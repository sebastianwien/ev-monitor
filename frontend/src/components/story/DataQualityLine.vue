<script setup lang="ts">
import { computed, ref, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { visibleGap, type DayCoverage } from '../../composables/storyModel'
import type { DataQuality } from '../../composables/storyInsights'
import { useLocaleFormat } from '../../composables/useLocaleFormat'

/**
 * Die Qualitätszeile unter dem Hero: ein Feld je Tag des Monats (Ladung grün, die genannte
 * Lücke amber, sonst grau), darüber eine Säule je Fahrtag nach km, eine Legende und ein Satz. Eine Lücke nennt der Satz nur, wenn der Monat vorbei
 * ist oder sie 7 Tage überschreitet. Tage nach heute sind keine Lücke. Überfahren, Antippen oder
 * Pfeiltasten zeigen den einzelnen Tag (Ladungen, kWh); die Achse nennt jeden siebten Tag.
 */
const props = defineProps<{ days: DayCoverage[]; quality: DataQuality | null }>()
/** Klick, Tap oder Enter auf einen Tag bis heute: Tagesblatt öffnen */
const emit = defineEmits<{ 'open-day': [day: number] }>()
const { t } = useI18n()
const { formatDecimal, formatDistance } = useLocaleFormat()

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
  if (d.charges > 0) return 'bg-green-700 dark:bg-green-400'
  if (d.future) return 'bg-gray-100 dark:bg-gray-800'
  if (gap.value && d.day >= gap.value.from && d.day <= gap.value.to) return 'bg-amber-400'
  return 'bg-gray-200 dark:bg-gray-700'
}

// Tagesanzeige: aktiver Tag aus Zeiger oder Tastatur
const active = ref<number | null>(null)
const bar = ref<HTMLElement | null>(null)
let touchTimer: ReturnType<typeof setTimeout> | undefined
onUnmounted(() => clearTimeout(touchTimer))

function indexAt(clientX: number): number | null {
  const el = bar.value
  if (!el || !props.days.length) return null
  const r = el.getBoundingClientRect()
  if (r.width <= 0) return null
  return Math.min(props.days.length - 1, Math.max(0, Math.floor(((clientX - r.left) / r.width) * props.days.length)))
}
function onPointer(e: PointerEvent) {
  const i = indexAt(e.clientX)
  if (i == null) return
  active.value = i
  clearTimeout(touchTimer)
  // Auf dem Handy gibt es kein Verlassen mit der Maus: die Anzeige verschwindet nach kurzer Zeit.
  if (e.pointerType === 'touch') touchTimer = setTimeout(() => { active.value = null }, 2500)
}
function onLeave(e: PointerEvent) {
  if (e.pointerType !== 'touch') active.value = null
}
function open(i: number | null) {
  const d = i != null ? props.days[i] : null
  if (d && !d.future) emit('open-day', d.day)
}
const onClick = (e: MouseEvent) => open(indexAt(e.clientX))
function onKey(e: KeyboardEvent) {
  if (e.key === 'Enter') { open(active.value); return }
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
  if (d.future) return t('story.day_tip', { day: d.day, text: t('story.day_tip_future') })
  const parts = []
  if (d.charges > 0) parts.push(t('story.day_tip_charges', { n: d.charges, kwh: `${formatDecimal(d.kwh, 1)} kWh` }, d.charges))
  if (d.trips > 0) parts.push(t('story.day_tip_trips', { n: d.trips, km: formatDistance(d.tripKm, { round: true }) }, d.trips))
  return t('story.day_tip', { day: d.day, text: parts.length ? parts.join(' · ') : t('story.day_tip_empty') })
})
/** Höchste km eines Tages: Maßstab der Fahrt-Säulen. */
const maxKm = computed(() => Math.max(0, ...props.days.map(d => d.tripKm)))
const hasTrips = computed(() => props.days.some(d => d.trips > 0))
/** Säulenhöhe in Prozent; eine Fahrt ohne km bleibt als Strich sichtbar. */
const kmHeight = (d: DayCoverage) => maxKm.value > 0 ? `${Math.max(12, Math.round((d.tripKm / maxKm.value) * 100))}%` : '12%'
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
        class="-my-1.5 flex cursor-pointer gap-[3px] rounded py-1.5 focus:outline-none focus-visible:ring-2 focus-visible:ring-story-accent"
        @pointerdown="onPointer" @pointermove="onPointer" @pointerleave="onLeave"
        @click="onClick" @keydown="onKey" @blur="active = null">
        <span v-for="(d, i) in days" :key="d.day" class="flex flex-1 flex-col justify-end gap-[2px]" :class="hasTrips ? 'h-7' : ''">
          <span v-if="hasTrips" class="relative block flex-1">
            <i v-if="d.trips > 0" data-role="km" class="absolute inset-x-0 bottom-0 rounded-[2px]"
               :class="active === i ? 'bg-sky-700 dark:bg-sky-300' : 'bg-sky-500 dark:bg-sky-400'" :style="{ height: kmHeight(d) }" />
          </span>
          <i data-role="day" class="block h-2.5 rounded-[2px] transition-transform"
             :class="[cellClass(d), active === i ? 'scale-y-[1.4]' : '']" />
        </span>
      </div>
      <span v-if="tip" data-testid="story-day-tip" role="status"
            class="pointer-events-none absolute bottom-full mb-2 whitespace-nowrap rounded-md bg-gray-900 px-2 py-1 text-[12px] font-semibold text-white tabular-nums shadow-sm dark:bg-gray-100 dark:text-gray-900"
            :class="tipAlign" :style="{ left: pos(active ?? 0) }">{{ tip }}</span>
      <div class="relative mt-1 h-3.5 text-[10.5px] text-gray-400 tabular-nums dark:text-gray-500" aria-hidden="true">
        <span v-for="d in ticks" :key="d" data-role="tick" class="absolute -translate-x-1/2" :style="{ left: pos(d - 1) }">{{ d }}.</span>
      </div>
      <!-- Legende: nur, was im Monat vorkommt; Amber nur, wenn der Satz eine Lücke nennt -->
      <div data-testid="story-days-legend" class="mt-1 flex flex-wrap gap-x-3 gap-y-0.5 text-[11px] text-gray-500 dark:text-gray-400">
        <span class="inline-flex items-center gap-1"><i class="h-2 w-2.5 rounded-[2px] bg-green-700 dark:bg-green-400" />{{ t('story.days_legend_charged') }}</span>
        <span v-if="hasTrips" class="inline-flex items-center gap-1"><i class="h-2.5 w-1.5 rounded-[2px] bg-sky-500 dark:bg-sky-400" />{{ t('story.days_legend_drove') }}</span>
        <span v-if="gap" class="inline-flex items-center gap-1"><i class="h-2 w-2.5 rounded-[2px] bg-amber-400" />{{ t('story.days_legend_gap') }}</span>
      </div>
    </div>
    <p class="text-[12.5px] text-gray-500 tabular-nums dark:text-gray-400">{{ caption }}<template v-if="quality && quality.total > 0"><template v-if="quality.priceless > 0">, <span class="text-amber-700 dark:text-amber-400">{{ t('story.quality_priceless', { n: quality.priceless }) }}<template v-if="quality.pricelessOpen > 0">{{ ' ' }}<router-link to="/logs" class="font-semibold underline underline-offset-2">{{ t('story.quality_fix') }}</router-link></template></span></template>.</template><span v-if="gap" class="text-amber-700 dark:text-amber-400">{{ ' ' + t('story.days_gap', gap) }}</span></p>
  </div>
</template>
