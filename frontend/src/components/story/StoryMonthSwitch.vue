<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ChevronLeftIcon, ChevronRightIcon } from '@heroicons/vue/24/outline'
import { monthAt, type StoryMonth } from '../../composables/useDashboardStory'
import { monthGrid } from '../../composables/storyMonthGrid'
import type { PanelAnchor } from '../../utils/panelAnchor'
import AnchoredPanel from '../shared/AnchoredPanel.vue'
import StoryMonthCapsule from './StoryMonthCapsule.vue'

/**
 * Drei Monate als Segment-Schalter, sichtbar 40 px, Trefferfläche 44 px. Bewusst keine Chips: die sehen aus wie
 * die Bereichs-Navigation, der Monat filtert aber nur die Seite. Das Wischen auf dem Hero steuert dieselbe Auswahl.
 * Die Fläche des gewählten Monats gleitet wie der Unterstrich der Navbar.
 * Gibt es ältere Ladungen, liegt links außerhalb des Rands ein Kalender. Ein Klick dehnt den Rand auf ihn aus, dann
 * klappt aus der Kapsel das Monatsraster auf (AnchoredPanel wie die Autoauswahl; seine Kopfzeile ist eine
 * deckungsgleiche Kopie der aufgeklappten Kapsel). Zu läuft es umgekehrt. Ein älterer Monat steht im Kalender-Segment,
 * der Rand bleibt dann aufgeklappt.
 */
const props = defineProps<{
  months: StoryMonth[]
  offset: number
  labelOf: (m: StoryMonth) => string
  /** Frühester wählbarer Monat (erste Ladung), -2: nur die drei des Schalters. */
  minOffset: number
  /** Monate mit Ladungen als 'Jahr-Monat'. */
  dataMonths: Set<string>
}>()
const emit = defineEmits<{ select: [offset: number] }>()
const { t, locale } = useI18n()
const capsule = ref<InstanceType<typeof StoryMonthCapsule> | null>(null)

const hasOlder = computed(() => props.minOffset < Math.min(...props.months.map(m => m.offset)))
const olderMonth = computed(() => props.months.some(m => m.offset === props.offset) ? null : monthAt(props.offset))
/** Kurz, im laufenden Jahr ohne Jahreszahl: "Mär", sonst "Mär 25". */
const olderLabel = computed(() => {
  const m = olderMonth.value
  if (!m) return null
  return m.year === new Date().getFullYear() ? props.labelOf(m) : `${props.labelOf(m)} ${String(m.year).slice(2)}`
})
const olderAria = computed(() => olderMonth.value
  ? `${t('story.month_older')}: ${new Date(olderMonth.value.year, olderMonth.value.month - 1, 1).toLocaleDateString(locale.value, { month: 'long', year: 'numeric' })}`
  : t('story.month_older'))

// Monatsraster: Jahre absteigend, je zwölf Monate; der gewählte trägt die Fläche wie im Schalter
const grid = computed(() => monthGrid(new Date(), props.dataMonths, props.minOffset))
/** Gezeigtes Jahr im Raster, ‹ und › schalten; beim Öffnen das Jahr des gewählten Monats. */
const year = ref(new Date().getFullYear())
const shownYear = computed(() => grid.value.find(y => y.year === year.value) ?? grid.value[0])
const canPrev = computed(() => year.value > grid.value[grid.value.length - 1].year)
const canNext = computed(() => year.value < grid.value[0].year)
const open = ref(false)
/** Rand über dem Kalender: während das Raster offen ist und solange ein älterer Monat gewählt ist. */
const widened = ref(false)
const expanded = computed(() => widened.value || !!olderMonth.value)
const body = ref<HTMLElement | null>(null)
const anchor = ref<PanelAnchor>({ top: 0, left: 0, width: 0, shift: 0 })
const COLS = 4
/** Lage des Rands der zugeklappten Kapsel: links über dem Kalender eingerückt (32 px plus 10 px), 40 px hoch. */
const COLLAPSED_LEFT = 42
const CAPSULE_H = 40

function cells(): HTMLButtonElement[] {
  return [...(body.value?.querySelectorAll<HTMLButtonElement>('[data-month-cell]') ?? [])]
}

function focusCell() {
  const all = cells()
  const target = all.find(c => c.getAttribute('aria-current') === 'true' && !c.disabled) ?? all.find(c => !c.disabled)
  target?.focus({ preventScroll: true })
}

function toggle() {
  if (open.value) { hide(); return }
  // Panel genau auf der Kapsel: sein Rand ist ihr Rand und wächst zugleich nach links und nach unten
  const r = capsule.value!.el!.getBoundingClientRect()
  anchor.value = { top: r.top, left: r.left, width: r.width, shift: 0 }
  year.value = monthAt(props.offset).year
  widened.value = true
  open.value = true
  nextTick(focusCell)
}

function hide(returnFocus = true) {
  if (!open.value) return
  open.value = false
  widened.value = false
  if (returnFocus) capsule.value?.calendarButton?.focus()
}
// Ein Monat aus dem Schalter, während das Raster zu ist: der Rand bleibt, wie er ist
watch(() => props.offset, () => { if (!open.value) widened.value = false })

function stepYear(dir: -1 | 1) {
  year.value += dir
  nextTick(focusCell)
}

/**
 * Der Rand des Panels startet in der Form der zugeklappten Kapsel (links eingerückt, nur so hoch wie sie) und wächst
 * in einem Zug zur vollen Form, das Raster darunter deckt sich im selben Takt auf. Zu als Umkehrung mit ease-in.
 * Ist ein älterer Monat gewählt, steht der Rand schon links, dann wächst er nur nach unten.
 */
function frameMotion(opening: boolean) {
  return (el: Element, done: () => void) => {
    const frame = el.querySelector<HTMLElement>('[data-month-panel-frame]')
    const grid = el.querySelector<HTMLElement>('[data-month-panel-body]')
    const still = !frame || !grid || typeof frame.animate !== 'function' || window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
    if (still) { done(); return }
    const small = { left: `${olderMonth.value ? 0 : COLLAPSED_LEFT}px`, height: `${CAPSULE_H}px` }
    const full = { left: '0px', height: `${(el as HTMLElement).offsetHeight}px` }
    const hidden = { clipPath: 'inset(0 0 100% 0)' }
    const shown = { clipPath: 'inset(0 0 0 0)' }
    const timing = { duration: 300, easing: opening ? 'ease-out' : 'ease-in' }
    const run = frame.animate(opening ? [small, full] : [full, small], timing)
    grid.animate(opening ? [hidden, shown] : [shown, hidden], timing)
    run.onfinish = () => done()
  }
}
const motion = { enter: frameMotion(true), leave: frameMotion(false) }

function choose(offset: number) {
  if (offset !== props.offset) emit('select', offset)
  hide()
}

/** Pfeile wandern durchs Raster (links/rechts ein Monat, hoch/runter eine Zeile), gesperrte Monate werden übersprungen. */
function onKey(e: KeyboardEvent) {
  const step = { ArrowLeft: -1, ArrowRight: 1, ArrowUp: -COLS, ArrowDown: COLS }[e.key]
  if (!step) return
  e.preventDefault()
  const all = cells()
  let i = all.indexOf(document.activeElement as HTMLButtonElement) + step
  while (i >= 0 && i < all.length && all[i].disabled) i += step
  all[i]?.focus({ preventScroll: true })
}

function monthName(year: number, month: number): string {
  return props.labelOf({ year, month, offset: 0 })
}
</script>

<template>
  <StoryMonthCapsule ref="capsule" :months="months" :offset="offset" :label-of="labelOf" :calendar="hasOlder" :expanded="expanded"
    :older-label="olderLabel" :older-aria="olderAria" :calendar-open="open" @select="emit('select', $event)" @calendar="toggle" />

  <AnchoredPanel v-if="hasOlder" :open="open" :anchor="anchor" testid="story-month-grid" role="dialog" :label="t('story.month_aria')"
    panel-class="" :motion="motion" @close="hide">
    <div @keydown="onKey">
      <!-- Ein Rand: der der Kapsel, gewachsen nach links über den Kalender und nach unten über das Raster -->
      <span data-month-panel-frame aria-hidden="true" class="absolute inset-x-0 top-0 h-full rounded-[20px] border-2 border-edge bg-white dark:bg-gray-900" />
      <StoryMonthCapsule :months="months" :offset="offset" :label-of="labelOf" :calendar="true" :expanded="true"
        :older-label="olderLabel" :older-aria="olderAria" :calendar-open="true" :testids="false" :framed="false"
        @select="choose" @calendar="hide()" />
      <div ref="body" data-month-panel-body class="relative px-2 pb-2">
        <div class="flex items-center justify-between border-t border-gray-200 pt-1 dark:border-gray-700">
          <button type="button" data-testid="story-month-year-prev" :disabled="!canPrev" :aria-label="t('story.year_prev')"
            class="grid h-11 w-11 place-items-center rounded-full text-gray-600 transition-colors hover:bg-black/5 disabled:cursor-default disabled:text-gray-300 disabled:hover:bg-transparent dark:text-gray-300 dark:hover:bg-white/10 dark:disabled:text-gray-600"
            @click="stepYear(-1)">
            <ChevronLeftIcon class="h-4 w-4" aria-hidden="true" />
          </button>
          <span data-testid="story-month-year" class="text-[15px] font-bold text-gray-900 tabular-nums dark:text-gray-50" aria-live="polite">{{ shownYear.year }}</span>
          <button type="button" data-testid="story-month-year-next" :disabled="!canNext" :aria-label="t('story.year_next')"
            class="grid h-11 w-11 place-items-center rounded-full text-gray-600 transition-colors hover:bg-black/5 disabled:cursor-default disabled:text-gray-300 disabled:hover:bg-transparent dark:text-gray-300 dark:hover:bg-white/10 dark:disabled:text-gray-600"
            @click="stepYear(1)">
            <ChevronRightIcon class="h-4 w-4" aria-hidden="true" />
          </button>
        </div>
        <div class="grid grid-cols-4 gap-1">
          <button v-for="c in shownYear.months" :key="c.month" type="button" data-month-cell
            :data-testid="`story-month-pick-${c.offset}`"
            :disabled="!c.enabled"
            :aria-current="c.offset === offset ? 'true' : undefined"
            class="min-h-11 rounded-full text-[13.5px] font-semibold transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-green-600"
            :class="c.offset === offset
              ? 'bg-gray-900 text-white dark:bg-gray-100 dark:text-gray-900'
              : c.enabled
                ? 'text-gray-800 hover:bg-gray-100 dark:text-gray-100 dark:hover:bg-white/10'
                : 'cursor-default text-gray-300 dark:text-gray-600'"
            @click="choose(c.offset)">
            {{ monthName(c.year, c.month) }}
          </button>
        </div>
      </div>
    </div>
  </AnchoredPanel>
</template>
