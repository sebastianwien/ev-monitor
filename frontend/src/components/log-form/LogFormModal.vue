<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { BoltIcon, ChevronUpIcon, XMarkIcon } from '@heroicons/vue/24/outline'
import BottomSheet from '../shared/BottomSheet.vue'
import LogWizard from '../log-wizard/LogWizard.vue'
import { useIsMobile } from '../../composables/useIsMobile'
import { cardRelease } from '../../composables/useCardSwipe'

/**
 * Erfassen als Sheet: mobil bis knapp unter den oberen Rand, nach unten ziehen schließt wie bei
 * den anderen Sheets. Mit `minimizable` und schon gemachten Eingaben minimiert der Zug stattdessen
 * zur Leiste über der Bottom-Nav (v-model:minimized); Tipp darauf macht weiter, das X verwirft.
 * Ab sm ein zentrierter Dialog.
 */
const props = defineProps<{ carId?: string | null; initialLoggedAt?: string | null; minimizable?: boolean; minimized?: boolean }>()
const emit = defineEmits<{
  close: []
  saved: []
  'update:minimized': [value: boolean]
}>()
const { t } = useI18n()

const sheet = ref<InstanceType<typeof BottomSheet> | null>(null)
const wizard = ref<InstanceType<typeof LogWizard> | null>(null)
// Gleiche Grenze wie das Ziehen im BottomSheet: nur wo das Panel unten anliegt
const phone = useIsMobile('(max-width: 639px)')
const canMinimize = computed(() => !!props.minimizable && phone.value && !!wizard.value?.hasInput)

const close = () => sheet.value?.requestClose()

// Höhe auf dem Handy: so hoch, wie Schritt 2 (die Zahlen) ohne Scrollen braucht, und das schon ab Schritt 1,
// damit sich zwischen den Schritten nichts verschiebt. Die Zeilen brechen je nach Breite und Sprache anders um:
// gemessen und je Breite im Gerät gemerkt. Ohne Messung 625 px (Schritt 2 auf schmalen Handys).
// Während das Sheet offen ist, wächst es nur, ein kleinerer Messwert gilt erst beim nächsten Öffnen.
const FALLBACK_PX = 625
const heightKey = () => `wizard-step2-h:${window.innerWidth}`
function storedHeight(): number | null {
  try { const v = Number(localStorage.getItem(heightKey())); return v > 0 ? v : null } catch { return null }
}
const body = ref<HTMLElement | null>(null)
const panel = () => body.value?.closest<HTMLElement>('[data-testid="log-form-sheet"]') ?? null
let appliedPx = 0
let visitMax = 0
function applyHeight(px: number) {
  appliedPx = px
  panel()?.style.setProperty('--wizard-h', `${px}px`)
}
function measureStepTwo() {
  const p = panel()
  if (!p || !phone.value) return
  const fixed = p.style.height
  p.style.setProperty('height', 'auto', 'important')
  // Höchster Wert dieses Besuchs: Teile von Schritt 2 laden nach, Zwischenstände beim Ein- und Ausblenden sind kleiner
  visitMax = Math.max(visitMax, p.offsetHeight)
  p.style.height = fixed
  try { localStorage.setItem(heightKey(), String(visitMax)) } catch { /* nur Komfort */ }
  if (visitMax > appliedPx) applyHeight(visitMax)
}
// Gemessen wird, wenn Schritt 2 fertig eingeblendet ist, und erneut, wenn er wächst (Hinweise)
let observer: ResizeObserver | null = null
function onStepShown(step: number, el: Element) {
  observer?.disconnect(); observer = null
  if (step !== 2) return
  visitMax = 0
  measureStepTwo()
  if (typeof ResizeObserver === 'undefined') return
  observer = new ResizeObserver(measureStepTwo)
  observer.observe(el)
}
// Das Panel rendert BottomSheet erst nach seinem eigenen Mount
onMounted(() => nextTick(() => applyHeight(storedHeight() ?? FALLBACK_PX)))
onUnmounted(() => observer?.disconnect())
const expand = () => emit('update:minimized', false)

// Die Leiste lässt sich hochziehen: das Sheet folgt dem Finger (oder der Maus), die Abdunklung blendet mit ein.
// Loslassen nach derselben Regel wie das Schließen der anderen Sheets (Weg oder schneller Wisch), sonst federt es
// zur Leiste zurück. Nach unten passiert nichts, Verwerfen bleibt beim X. Pointer- statt Touch-Events: so geht es
// auch mit der Maus; touch-none an der Leiste hält den nativen Scroll fern, damit kein pointercancel kommt.
let bar: { id: number; y: number; base: number; moved: boolean; last: { y: number; t: number }; prev: { y: number; t: number } } | null = null
/** Ein Zug ist kein Tipp: der Klick danach soll die Leiste nicht zusätzlich aufmachen */
let swallowClick = false
const backdrop = () => panel()?.parentElement?.querySelector<HTMLElement>('.sheet-backdrop') ?? null
/** Verschiebung des minimierten Panels nach unten (translateY), auch bei matrix3d */
function minimizedOffset(p: HTMLElement): number {
  const t = getComputedStyle(p).transform
  if (!t || t === 'none' || typeof DOMMatrixReadOnly === 'undefined') return 0
  return new DOMMatrixReadOnly(t).m42
}
function onBarDown(e: PointerEvent) {
  const p = panel()
  if (!p || bar || (e.pointerType === 'mouse' && e.button !== 0)) return
  bar = { id: e.pointerId, y: e.clientY, base: minimizedOffset(p), moved: false, last: { y: e.clientY, t: e.timeStamp }, prev: { y: e.clientY, t: e.timeStamp } }
  swallowClick = false
}
function onBarMove(e: PointerEvent) {
  const p = panel()
  if (!bar || e.pointerId !== bar.id || !p) return
  const up = Math.max(0, bar.y - e.clientY)
  if (!bar.moved) {
    if (Math.abs(bar.y - e.clientY) < 4) return
    bar.moved = true
    ;(e.currentTarget as Element).setPointerCapture?.(e.pointerId)
  }
  e.preventDefault()
  bar.prev = bar.last
  bar.last = { y: e.clientY, t: e.timeStamp }
  p.style.transition = 'none'
  p.style.transform = `translateY(${Math.max(0, bar.base - up)}px)`
  const b = backdrop()
  if (b && bar.base > 0) { b.style.transition = 'none'; b.style.opacity = String(Math.min(1, up / bar.base)) }
}
function onBarUp(e: PointerEvent) {
  if (!bar || e.pointerId !== bar.id) return
  const { moved } = bar
  const up = bar.y - bar.last.y
  const dt = bar.last.t - bar.prev.t
  const v = dt > 0 ? (bar.prev.y - bar.last.y) / dt : 0
  bar = null
  if (!moved) return
  swallowClick = true
  // Inline-Werte weg: der Übergang läuft von der Fingerposition zum Ziel (offen oder Leiste)
  const p = panel(); const b = backdrop()
  for (const el of [p, b]) el?.style.removeProperty('transition')
  p?.style.removeProperty('transform')
  b?.style.removeProperty('opacity')
  if (e.type !== 'pointercancel' && cardRelease({ axis: 'y', dx: 0, dy: up, v, width: 0 }) === 'collapse') expand()
}
function onBarClickCapture(e: MouseEvent) {
  if (!swallowClick) return
  swallowClick = false
  e.stopPropagation()
  e.preventDefault()
}
</script>

<template>
  <BottomSheet ref="sheet" edge :label="t('logform.title')" testid="log-form-sheet"
    panel-class="h-[min(calc(100dvh-env(safe-area-inset-top)-0.5rem-var(--bottom-nav-h,0px)),var(--wizard-h,39rem))] max-h-none! dark:bg-gray-900! sm:h-auto sm:max-h-[90vh]! sm:max-w-xl"
    :minimizable="canMinimize" :minimized="minimized"
    @minimize="emit('update:minimized', true)" @close="emit('close')">
    <!-- Ein Hintergrund wie der Wizard, auch unter Griff und Safe-Area -->
    <div ref="body" class="relative flex min-h-0 flex-1 flex-col bg-white dark:bg-gray-900">
      <LogWizard ref="wizard" embedded @step-shown="onStepShown" :car-id="carId" :initial-logged-at="initialLoggedAt"
        @success="emit('saved'); close()" @cancel="close" />
    </div>

    <!-- Minimiert: die Leiste deckt den Wizard-Kopf ab, die Eingaben darunter bleiben erhalten. Über allem: der Kopf der
         Prüfseite schwebt mit z-[600] über der Karte, Leaflet-Bedienelemente liegen bei 1000. -->
    <div v-if="minimized" data-testid="log-form-bar" class="absolute inset-x-0 top-0 z-[1100] h-16 touch-none select-none bg-white dark:bg-gray-900"
      @pointerdown="onBarDown" @pointermove="onBarMove" @pointerup="onBarUp" @pointercancel="onBarUp" @click.capture="onBarClickCapture" @dragstart.prevent>
      <div class="mx-auto mt-2 h-1.5 w-10 rounded-full bg-gray-200 dark:bg-gray-700" aria-hidden="true" />
      <div class="flex items-center gap-1 px-2">
        <button type="button" class="flex min-h-11 min-w-0 flex-1 items-center gap-2 rounded-xl px-2 text-left" @click="expand">
          <BoltIcon class="h-5 w-5 shrink-0 text-green-600" aria-hidden="true" />
          <span class="truncate text-[15px] font-semibold text-gray-900 dark:text-gray-50">{{ t('logform.title') }}</span>
          <ChevronUpIcon class="ml-auto h-5 w-5 shrink-0 text-gray-500 dark:text-gray-400" aria-hidden="true" />
        </button>
        <button type="button" :aria-label="t('common.cancel')" class="grid h-11 w-11 shrink-0 place-items-center rounded-full text-gray-600 hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-white/10" @click="close">
          <XMarkIcon class="h-5 w-5" />
        </button>
      </div>
    </div>
  </BottomSheet>
</template>
