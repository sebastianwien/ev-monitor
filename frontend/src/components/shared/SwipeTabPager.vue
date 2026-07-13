<script setup lang="ts">
import { computed, ref, watch, onUnmounted, toRef } from 'vue'
import { useRouter } from 'vue-router'
import { useTabPager } from '../../composables/useTabPager'
import { useIsMobile } from '../../composables/useIsMobile'

/**
 * Horizontaler Pager fuer zwei verwandte Ansichten (Dashboard/Log-Feed, Fahrzeuge/Ladekarten).
 *
 * Mobile: beide Bodies liegen dauerhaft in einer horizontalen Schiene (Track). Der aktive
 * Tab bestimmt den Track-Offset; ein Swipe zieht den Track mit dem Finger, sodass der
 * Nachbar-Tab schon waehrend der Geste sichtbar wird (echter Pager). Beim Loslassen ueber
 * der Schwelle wird navigiert - die Route folgt der Geste, nicht umgekehrt.
 *
 * Desktop: nur der aktive Body wird gemountet - sonst rendern beide Views parallel
 * (doppeltes Polling, duplizierte Header/Aktionen im DOM), obwohl es dort keinen Swipe
 * gibt. Der Tab-Klick blendet den Body mit einem kurzen Versatz aus der Tab-Richtung ein
 * - dieselben Klassen wie beim Wechsel ueber die Paar-Grenze (Routen-Transition, App.vue).
 */
const props = defineProps<{
  /** Routen-Pfade der beiden Tabs, in Anzeige-Reihenfolge. */
  tabs: readonly string[]
  activeIndex: number
}>()

const router = useRouter()
const isMobile = useIsMobile()

const activeIndex = toRef(props, 'activeIndex')
const pagerEl = ref<HTMLElement | null>(null)
const { dragX, dragging } = useTabPager(
  pagerEl,
  activeIndex,
  toRef(props, 'tabs'),
  (to) => router.push(to),
)

// Track ist 200% breit (zwei Panes je 100% Viewport). Ein Pane-Wechsel = 50% des
// Tracks. dragX (px) kommt vom Finger obendrauf.
const trackStyle = computed(() => ({
  transform: `translateX(calc(-${activeIndex.value * 50}% + ${dragX.value}px))`,
  transition: dragging.value ? 'none' : 'transform 0.28s cubic-bezier(0.32, 0.72, 0, 1)',
}))

// Die Tabs sind unterschiedlich hoch. Im Ruhezustand klappt der inaktive Pane
// zusammen, damit unter dem kuerzeren Tab keine Luecke entsteht - nur waehrend
// Drag (Peek) und dem Snap-Slide sind beide voll ausgeklappt. Zusammengeklappt ist
// er zusaetzlich `invisible` + aria-hidden: der Nachbar-Tab liegt zwar im DOM, ist
// aber weder fuer Screenreader noch fuer den Tab-Fokus erreichbar.
const transitioning = ref(false)
let transTimer: ReturnType<typeof setTimeout> | undefined
// Richtung des Desktop-Uebergangs: der neue Body stupst aus der Richtung seines Tabs
// herein (Klassen in App.vue, geteilt mit der Routen-Transition).
const slideName = ref<'nudge-left' | 'nudge-right'>('nudge-left')
watch(activeIndex, (to, from) => {
  slideName.value = to > from ? 'nudge-left' : 'nudge-right'
  transitioning.value = true
  clearTimeout(transTimer)
  transTimer = setTimeout(() => { transitioning.value = false }, 320)
})
onUnmounted(() => clearTimeout(transTimer))
const bothExpanded = computed(() => dragging.value || transitioning.value)
const paneCollapsed = (i: number) => !bothExpanded.value && i !== activeIndex.value
</script>

<template>
  <!-- Desktop: nur der aktive Body, eingeblendet mit Versatz aus der Tab-Richtung. -->
  <div v-if="!isMobile" class="overflow-x-clip">
    <Transition :name="slideName" mode="out-in">
      <div :key="activeIndex">
        <slot v-if="activeIndex === 0" name="left" />
        <slot v-else name="right" />
      </div>
    </Transition>
  </div>

  <!-- Mobile: Pager-Viewport (klippt horizontal) + Track mit beiden Bodies. -->
  <div v-else ref="pagerEl" class="overflow-x-clip">
    <div class="flex w-[200%] items-start" :style="trackStyle">
      <div
        class="w-1/2 shrink-0"
        :class="{ 'max-h-0 overflow-hidden invisible': paneCollapsed(0) }"
        :aria-hidden="paneCollapsed(0) || undefined">
        <slot name="left" />
      </div>
      <div
        class="w-1/2 shrink-0"
        :class="{ 'max-h-0 overflow-hidden invisible': paneCollapsed(1) }"
        :aria-hidden="paneCollapsed(1) || undefined">
        <slot name="right" />
      </div>
    </div>
  </div>
</template>
