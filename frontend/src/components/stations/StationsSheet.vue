<script setup lang="ts">
/**
 * Sheet über der Säulenkarte in drei Höhen (klein, halb, ganz). Anders als BottomSheet nicht modal:
 * keine Abdunklung, kein Fokusfang, die Karte darüber bleibt bedienbar, und es schließt nie. Daher
 * eine eigene Komponente statt BottomSheet mit fünf abgeschalteten Verhalten.
 *
 * Griff und Kopf ziehen die Höhe mit dem Finger; nach dem Loslassen läuft sie mit dessen Tempo zur
 * nächsten Höhe weiter (releaseMotion, wie die übrigen Sheets). Ein Tipp auf den Griff wechselt zur
 * nächsten Höhe. Die aktuelle Höhe steht als --sheet-h am Elternelement, damit die Karte ihre
 * Bedienelemente darüber hält.
 */
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { RELEASE_EASE, releaseMs, settleMs } from '../../composables/releaseMotion'
import { nextOnTap, snapAfterDrag, snapHeights, type Snap } from '../../utils/snapSheet'

const props = defineProps<{ label: string; peek: number }>()
const snap = defineModel<Snap>('snap', { required: true })
const { t } = useI18n()

const root = ref<HTMLElement | null>(null)
const containerH = ref(0)
const heights = computed(() => snapHeights(containerH.value || 600, props.peek))
/** Höhe während des Zugs; sonst null, dann gilt die Höhe der Stufe */
const dragH = ref<number | null>(null)
/** Tipp auf den Griff: dieselbe Dauer und Kurve wie die übrigen Sheets */
const TAP_MS = 300
const TAP_EASE = 'cubic-bezier(0.32, 0.72, 0, 1)'
const ms = ref(TAP_MS)
const ease = ref(TAP_EASE)
const height = computed(() => dragH.value ?? heights.value[snap.value])

const reducedMotion = () => !!window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
const style = computed(() => ({
  height: `${height.value}px`,
  transition: dragH.value != null || reducedMotion() ? 'none' : `height ${ms.value}ms ${ease.value}`,
}))

watch(height, h => root.value?.parentElement?.style.setProperty('--sheet-h', `${Math.round(h)}px`), { flush: 'post' })

let start: { y: number; h: number; id: number } | null = null
let last = { y: 0, t: 0 }
let prev = { y: 0, t: 0 }
let moved = false
/** Direkt nach einem Zug schickt der Browser noch einen Klick; der darf die Höhe nicht weiterschalten */
let swallowClick = false

/**
 * Bewegung und Loslassen am Fenster abhören: der Finger verlässt den Griff beim ersten Schritt,
 * danach landen die Ereignisse bei der Karte. Ohne Pointer-Capture bleibt ein Tipp ein Klick.
 */
function listen(on: boolean) {
  if (on) {
    window.addEventListener('pointermove', onMove)
    window.addEventListener('pointerup', onUp)
    window.addEventListener('pointercancel', onUp)
  } else {
    window.removeEventListener('pointermove', onMove)
    window.removeEventListener('pointerup', onUp)
    window.removeEventListener('pointercancel', onUp)
  }
}
function onDown(e: PointerEvent) {
  if (e.button !== 0) return
  start = { y: e.clientY, h: height.value, id: e.pointerId }
  last = prev = { y: e.clientY, t: e.timeStamp }
  moved = false
  listen(true)
}
function onMove(e: PointerEvent) {
  if (!start || e.pointerId !== start.id) return
  const dy = e.clientY - start.y
  if (!moved && Math.abs(dy) < 4) return
  moved = true
  e.preventDefault()
  prev = last
  last = { y: e.clientY, t: e.timeStamp }
  const h = heights.value
  dragH.value = Math.min(h.full, Math.max(h.peek * 0.85, start.h - dy))
}
function onUp(e: PointerEvent) {
  if (!start || e.pointerId !== start.id) return
  start = null
  listen(false)
  if (!moved) return
  const current = dragH.value ?? height.value
  const dt = last.t - prev.t
  // nach oben ist positiv: das Sheet wächst
  const v = dt > 0 ? -(last.y - prev.y) / dt : 0
  // Nur der Klick direkt aus diesem Zug zählt nicht; kommt er nicht am Griff an, gilt die nächste Taste wieder
  swallowClick = true
  setTimeout(() => { swallowClick = false }, 0)
  moved = false
  const target = snapAfterDrag(heights.value, current, v)
  const distance = heights.value[target] - current
  ms.value = Math.abs(v) > 0.05 ? releaseMs(distance, v) : settleMs(distance)
  ease.value = RELEASE_EASE
  snap.value = target
  dragH.value = null
}
/** Tipp auf den Griff (ohne Zug) oder Enter/Leertaste: nächste Höhe */
function onGrip() {
  if (swallowClick) { swallowClick = false; return }
  ms.value = TAP_MS
  ease.value = TAP_EASE
  snap.value = nextOnTap(snap.value)
}

let observer: ResizeObserver | null = null
onMounted(() => {
  const parent = root.value?.parentElement
  if (!parent) return
  containerH.value = parent.clientHeight
  root.value?.parentElement?.style.setProperty('--sheet-h', `${Math.round(height.value)}px`)
  if (typeof ResizeObserver === 'undefined') return
  observer = new ResizeObserver(() => { containerH.value = parent.clientHeight })
  observer.observe(parent)
})
onUnmounted(() => {
  observer?.disconnect()
  listen(false)
})

const scroller = ref<HTMLElement | null>(null)
defineExpose({ heights, scroller })
</script>

<template>
  <section ref="root" :aria-label="label" data-testid="stations-sheet" :data-snap="snap"
    class="absolute inset-x-0 bottom-0 z-[650] flex flex-col overflow-hidden rounded-t-2xl bg-white edge-box dark:bg-gray-800 md:rounded-b-2xl"
    :style="style">
    <div class="shrink-0 touch-none select-none" @pointerdown="onDown">
      <button type="button" data-testid="stations-sheet-grip"
        class="flex h-7 w-full items-center justify-center focus-visible:outline focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-indigo-600"
        :aria-label="snap === 'full' ? t('stations.shrink') : t('stations.grow')" @click="onGrip">
        <span class="h-1.5 w-10 rounded-full bg-gray-900 dark:bg-gray-100" aria-hidden="true" />
      </button>
      <slot name="header" />
    </div>
    <div ref="scroller" class="min-h-0 flex-1 overflow-y-auto overscroll-contain">
      <slot />
    </div>
  </section>
</template>
