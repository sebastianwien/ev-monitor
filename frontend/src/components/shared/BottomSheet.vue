<script setup lang="ts">
import { ref, onMounted } from 'vue'

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
withDefaults(defineProps<{
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
}>(), {
  panelClass: 'sm:max-w-lg',
})

const emit = defineEmits<{ close: [] }>()

const visible = ref(false)
onMounted(() => { visible.value = true })

function requestClose() {
  visible.value = false
}

defineExpose({ requestClose })
</script>

<template>
  <Teleport to="body">
    <Transition name="sheet" @after-leave="emit('close')">
      <div
        v-if="visible"
        class="fixed inset-0 z-50 flex items-end sm:items-center justify-center sm:p-4"
        role="dialog"
        aria-modal="true"
        :aria-label="label"
        @click.self="requestClose">
        <div class="absolute inset-0 bg-black/50" @click="requestClose" />
        <div
          :data-testid="testid"
          class="sheet-panel relative w-full max-h-[90vh] flex flex-col overflow-hidden bg-white dark:bg-gray-800 rounded-t-2xl sm:rounded-sm shadow-[0_-4px_20px_rgba(0,0,0,0.25)] sm:shadow-[5px_5px_0_rgba(0,0,0,0.35)] dark:sm:shadow-[5px_5px_0_rgba(255,255,255,0.35)] pb-[env(safe-area-inset-bottom)] sm:pb-0"
          :class="panelClass">
          <slot :close="requestClose" />
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
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
