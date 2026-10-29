<script lang="ts">
/**
 * Solange ein Sheet offen ist, scrollt die Seite dahinter nicht (sonst nimmt ein Wisch auf
 * Abdunklung oder Kopf die Seite mit). Gezaehlt ueber alle Sheets, weil sie sich stapeln.
 */
let openSheets = 0
let pageOverflow = ''
function lockPage() {
  if (openSheets++ === 0) { pageOverflow = document.body.style.overflow; document.body.style.overflow = 'hidden' }
}
function unlockPage() {
  if (openSheets > 0 && --openSheets === 0) document.body.style.overflow = pageOverflow
}
</script>

<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted } from 'vue'
import { useSheetDrag } from '../../composables/useSheetDrag'

/**
 * Overlay, das auf Mobile von unten hereinfaehrt und beim Schliessen wieder nach unten
 * verschwindet (ab sm: zentriert, mit kurzem Versatz statt voller Schiebe-Strecke).
 *
 * Der Aufrufer haengt die Komponente wie gewohnt an ein `v-if` und hoert auf `close`.
 * Damit die Schliess-Animation nicht vom sofortigen Unmount abgeschnitten wird, meldet
 * das Sheet `close` erst, wenn sie durchgelaufen ist - bis dahin haelt es sich selbst
 * offen. Zum Schliessen aus dem Aufrufer heraus: `ref.requestClose()`.
 *
 * Teleport nach body: Dashboard und Log-Feed liegen im SwipeTabPager, dessen Track
 * dauerhaft ein translateX() traegt. Ein transformierter Vorfahre wird zum Containing
 * Block fuer position:fixed - das Overlay wuerde sich sonst am 200% breiten Track statt
 * am Viewport ausrichten und links aus dem Bild haengen.
 */
const props = withDefaults(defineProps<{
  /** Beschriftung des Dialogs fuer Screenreader. */
  label?: string
  /**
   * Zusaetzliche Klassen fuer das Panel (Breite, Padding). Das Panel ist eine Flex-Spalte
   * mit `overflow-hidden`: welcher Bereich scrollt, entscheidet der Inhalt (z. B. ein
   * `flex-1 overflow-y-auto` zwischen fixem Kopf und Fuss).
   */
  panelClass?: string
  /** data-testid am Panel, fuer E2E. */
  testid?: string
  /** CI-Kante (Outline plus harter Schatten, edge-box) statt weichem Schatten. */
  edge?: boolean
  /** Liegt über einem anderen Sheet: leichter abdunkeln, das untere bleibt erkennbar. */
  stacked?: boolean
  /** Klick neben das Panel schließt nicht (z. B. während ein Import läuft); requestClose bleibt möglich. */
  locked?: boolean
  /** Ziehen und Abdunklung melden `minimize` statt zu schließen (Erfassen mit Eingaben). */
  minimizable?: boolean
  /**
   * Minimiert: nur der Kopf des Panels schaut über der Bottom-Nav hervor, ohne Abdunklung; die
   * Seite dahinter bleibt bedienbar. Den Kopf (Leiste) liefert der Aufrufer im Slot.
   */
  minimized?: boolean
}>(), {
  panelClass: 'sm:max-w-lg',
  edge: false,
  stacked: false,
  locked: false,
  minimizable: false,
  minimized: false,
})

const emit = defineEmits<{ close: []; minimize: [] }>()

const visible = ref(false)
let locked = false
function setLock(on: boolean) {
  if (on === locked) return
  locked = on
  if (on) lockPage(); else unlockPage()
}
onMounted(() => { visible.value = true; setLock(!props.minimized) })
onUnmounted(() => setLock(false))
watch(() => props.minimized, m => setLock(!m))

function requestClose() {
  visible.value = false
}
function dismiss() {
  if (props.minimizable) emit('minimize')
  else requestClose()
}
function onBackdrop() {
  if (!props.locked) dismiss()
}

// Mobil liegt das Panel unten an: nach unten ziehen schließt wie bei den Karten des Stapels.
const drag = useSheetDrag({
  enabled: () => !props.locked && !props.minimized && !window.matchMedia?.('(min-width: 640px)').matches,
  close: () => {
    if (!props.minimizable) return requestClose()
    // Das Panel gleitet von der Fingerposition in die minimierte Lage, nicht ganz hinaus
    drag.release()
    emit('minimize')
  },
})

defineExpose({ requestClose })
</script>

<template>
  <Teleport to="body">
    <Transition name="sheet" @after-leave="emit('close')">
      <div
        v-if="visible"
        class="fixed inset-0 flex items-end sm:items-center justify-center sm:p-4"
        :class="minimized ? 'z-30 pointer-events-none' : 'z-50'"
        role="dialog"
        :aria-modal="minimized ? 'false' : 'true'"
        :aria-label="label"
        @click.self="onBackdrop">
        <div class="sheet-backdrop absolute inset-0 touch-none" :class="[stacked ? 'bg-black/25' : 'bg-black/50', minimized && 'opacity-0']" @click="onBackdrop" />
        <div
          :data-testid="testid"
          :data-minimized="minimized || undefined"
          class="sheet-panel pointer-events-auto relative w-full max-h-[90vh] flex flex-col overflow-hidden overscroll-contain bg-white dark:bg-gray-800 rounded-t-2xl pb-[env(safe-area-inset-bottom)] sm:pb-0"
          :style="drag.style.value"
          @touchstart="drag.onTouchStart" @touchmove="drag.onTouchMove" @touchend="drag.onTouchEnd" @touchcancel="drag.onTouchEnd"
          :class="[edge ? 'edge-box sm:rounded-2xl' : 'sm:rounded-sm shadow-[0_-4px_20px_rgba(0,0,0,0.25)] sm:shadow-[5px_5px_0_rgba(0,0,0,0.35)] dark:sm:shadow-[5px_5px_0_rgba(255,255,255,0.35)]', panelClass]">
          <slot :close="requestClose" />
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
/* Minimiert schaut nur die Leiste (--sheet-peek) über der Bottom-Nav hervor; der Rest liegt hinter der Nav */
.sheet-backdrop { transition: opacity 0.25s ease; }
.sheet-panel { transition: transform 0.3s cubic-bezier(0.32, 0.72, 0, 1); }
.sheet-panel[data-minimized] {
  transform: translateY(calc(100% - var(--sheet-peek, 4rem) - 3.5rem - env(safe-area-inset-bottom)));
}

/* Hintergrund blendet, das Panel faehrt - dieselbe Kurve wie das Mehr-Sheet. */
.sheet-enter-active,
.sheet-leave-active {
  transition: opacity 0.2s ease;
}
.sheet-enter-active .sheet-panel,
.sheet-leave-active .sheet-panel {
  transition: transform 0.25s cubic-bezier(0.32, 0.72, 0, 1);
}
.sheet-enter-from,
.sheet-leave-to {
  opacity: 0;
}
.sheet-enter-from .sheet-panel,
.sheet-leave-to .sheet-panel {
  transform: translateY(100%);
}

/* Ab sm haengt der Dialog in der Mitte - eine volle Bildschirmhoehe Schiebeweg waere
   dort unruhig, ein kurzer Versatz reicht als Richtungshinweis. */
@media (min-width: 640px) {
  .sheet-enter-from .sheet-panel,
  .sheet-leave-to .sheet-panel {
    transform: translateY(1.5rem);
  }
}

@media (prefers-reduced-motion: reduce) {
  .sheet-panel,
  .sheet-backdrop,
  .sheet-enter-active .sheet-panel,
  .sheet-leave-active .sheet-panel {
    transition: none;
  }
  .sheet-enter-from .sheet-panel,
  .sheet-leave-to .sheet-panel {
    transform: none;
  }
}
</style>
