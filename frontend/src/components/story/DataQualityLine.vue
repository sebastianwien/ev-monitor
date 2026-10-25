<script setup lang="ts">
import { computed, ref, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { visibleGap, type DayCoverage } from '../../composables/storyModel'
import type { DataQuality } from '../../composables/storyInsights'
import { useLocaleFormat } from '../../composables/useLocaleFormat'

/**
 * Die Qualitätszeile unter dem Hero: ein Feld je Tag des Monats (Daten grün, die genannte
 * Lücke amber, sonst grau) und ein Satz dazu. Eine Lücke nennt der Satz nur, wenn der Monat vorbei
 * ist oder sie 7 Tage überschreitet. Tage nach heute sind keine Lücke. Überfahren, Antippen oder
 * Pfeiltasten zeigen den einzelnen Tag (Ladungen, kWh); die Achse nennt jeden siebten Tag.
 */
const props = defineProps<{ days: DayCoverage[]; quality: DataQuality | null }>()
const { t } = useI18n()
const { formatDecimal } = useLocaleFormat()

const withData = computed(() => props.days.filter(d => d.hasData).length)
/** Im laufenden Monat nur die Tage bis heute. */
const elapsed = computed(() => props.days.filter(d => !d.future).length)
const gap = computed(() => visibleGap(props.days))
const measured = computed(() => props.quality ? Math.max(0, props.quality.total - props.quality.estimated) : 0)
const qualityText = computed(() => {
  const q = props.quality
  if (!q) return ''
  const parts = [t('story.quality_line', { measured: measured.value, total: q.total })]
  if (q.estimated > 0) parts.push(t('story.quality_estimated', { n: q.estimated }))
  return parts.join(', ')
})
const caption = computed(() => {
  const base = t('story.days_caption', { total: elapsed.value, withData: withData.value })
  return props.quality && props.quality.total > 0 ? `${base} ${qualityText.value}` : base
})
/** Amber nur für die Tage der Lücke, die der Satz nennt; andere leere Tage grau, Tage nach heute heller. */
const cellClass = (d: DayCoverage) => {
  if (d.hasData) return 'bg-green-700 dark:bg-green-400'
  if (d.future) return 'bg-gray-100 dark:bg-gray-800'
  if (gap.value && d.day >= gap.value.from && d.day <= gap.value.to) return 'bg-amber-400'
  return 'bg-gray-200 dark:bg-gray-700'
}

// Tagesanzeige: aktiver Tag aus Zeiger oder Tastatur
const active = ref<number | null>(null)
const bar = ref<HTMLElement | null>(null)
let touchTimer: ReturnType<typeof setTimeout> | undefined
onUnmounted(() => clearTimeout(touchTimer))

function onPointer(e: PointerEvent) {
  const el = bar.value
  if (!el || !props.days.length) return
  const r = el.getBoundingClientRect()
  if (r.width <= 0) return
  active.value = Math.min(props.days.length - 1, Math.max(0, Math.floor(((e.clientX - r.left) / r.width) * props.days.length)))
  clearTimeout(touchTimer)
  // Auf dem Handy gibt es kein Verlassen mit der Maus: die Anzeige verschwindet nach kurzer Zeit.
  if (e.pointerType === 'touch') touchTimer = setTimeout(() => { active.value = null }, 2500)
}
function onLeave(e: PointerEvent) {
  if (e.pointerType !== 'touch') active.value = null
}
function onKey(e: KeyboardEvent) {
  const n = props.days.length
  if (!n) return
  const cur = active.value ?? -1
  const next = { ArrowRight: Math.min(n - 1, cur + 1), ArrowLeft: Math.max(0, cur - 1), Home: 0, End: elapsed.value > 0 ? elapsed.value - 1 : n - 1 }[e.key]
  if (next === undefined) return
  e.preventDefault()
  active.value = next
}

const tip = computed(() => {
  const d = active.value != null ? props.days[active.value] : null
  if (!d) return ''
  if (d.future) return t('story.day_tip_future', { day: d.day })
  if (d.charges > 0) return t('story.day_tip_charges', { day: d.day, n: d.charges, kwh: `${formatDecimal(d.kwh, 1)} kWh` }, d.charges)
  return t('story.day_tip_empty', { day: d.day })
})
/** Mitte des Felds in Prozent; die Anzeige am Rand bündig, damit sie nicht aus der Box ragt. */
const pos = (index: number) => `${((index + 0.5) / props.days.length) * 100}%`
const tipAlign = computed(() => {
  const i = active.value ?? 0
  return i < 3 ? 'translate-x-[-6px]' : i > props.days.length - 4 ? '-translate-x-full translate-x-[6px]' : '-translate-x-1/2'
})
const ticks = computed(() => [1, 8, 15, 22, 29].filter(d => d <= props.days.length))
</script>

<template>
  <div v-if="days.length" class="grid gap-1" data-testid="story-quality">
    <div class="relative">
      <div
        ref="bar"
        tabindex="0"
        role="group"
        data-testid="story-days"
        :aria-label="t('story.days_aria')"
        class="-my-1.5 flex cursor-default gap-[3px] rounded py-1.5 focus:outline-none focus-visible:ring-2 focus-visible:ring-story-accent"
        @pointerdown="onPointer" @pointermove="onPointer" @pointerleave="onLeave"
        @keydown="onKey" @blur="active = null">
        <i v-for="(d, i) in days" :key="d.day" data-role="day" class="h-2.5 flex-1 rounded-[2px] transition-transform"
           :class="[cellClass(d), active === i ? 'scale-y-[1.6]' : '']" />
      </div>
      <span v-if="tip" data-testid="story-day-tip" role="status"
            class="pointer-events-none absolute bottom-full mb-2 whitespace-nowrap rounded-md bg-gray-900 px-2 py-1 text-[12px] font-semibold text-white tabular-nums shadow-sm dark:bg-gray-100 dark:text-gray-900"
            :class="tipAlign" :style="{ left: pos(active ?? 0) }">{{ tip }}</span>
      <div class="relative mt-1 h-3.5 text-[10.5px] text-gray-400 tabular-nums dark:text-gray-500" aria-hidden="true">
        <span v-for="d in ticks" :key="d" data-role="tick" class="absolute -translate-x-1/2" :style="{ left: pos(d - 1) }">{{ d }}.</span>
      </div>
    </div>
    <p class="text-[12.5px] text-gray-500 tabular-nums dark:text-gray-400">{{ caption }}<template v-if="quality && quality.total > 0"><template v-if="quality.priceless > 0">, <span class="text-amber-700 dark:text-amber-400">{{ t('story.quality_priceless', { n: quality.priceless }) }}<template v-if="quality.pricelessOpen > 0">{{ ' ' }}<router-link to="/logs" class="font-semibold underline underline-offset-2">{{ t('story.quality_fix') }}</router-link></template></span></template>.</template><span v-if="gap" class="text-amber-700 dark:text-amber-400">{{ ' ' + t('story.days_gap', gap) }}</span></p>
  </div>
</template>
