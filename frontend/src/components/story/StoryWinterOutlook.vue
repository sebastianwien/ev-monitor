<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import BoldNumbers from './BoldNumbers.vue'
import WeatherIcon from '../icons/WeatherIcon.vue'
import { keepUnits } from '../../utils/modelStory'
import { useLocaleFormat, RANGE_STEP } from '../../composables/useLocaleFormat'
import type { OutlookDay } from '../../composables/storyMoments'

/**
 * Winter-Ausblick im leeren laufenden Monat: Reichweite mit einer Akkuladung als zwei beschriftete Balken
 * (Sommer als Bezug), darunter das Wetter der nächsten Tage am Heimladeort als Tagesleiste.
 * Im Rückblick-Kopf unter den Zahlen, ohne Rückblick unter der Leerzeile.
 */
const props = defineProps<{
  eyebrow: string
  range: { winterKm: number; summerKm: number } | null
  week: { days: OutlookDay[]; deltaC: number | null } | null
}>()
const { t, locale } = useI18n()
const { formatDistance, formatDecimal, distanceUnitLabel } = useLocaleFormat()

const caps = 'text-[11.5px] font-semibold uppercase tracking-[0.06em] text-gray-500 dark:text-gray-400'

const bars = computed(() => {
  const r = props.range
  if (!r) return []
  const max = Math.max(r.summerKm, r.winterKm)
  return [
    { id: 'summer', label: t('story.range_season_summer'), km: r.summerKm, tone: 'bg-gray-300 dark:bg-gray-500' },
    { id: 'winter', label: t('story.range_season_winter'), km: r.winterKm, tone: 'bg-gray-900 dark:bg-gray-100' },
  ].map(b => ({ ...b, n: formatDistance(b.km, { showUnit: false, step: RANGE_STEP }), pct: max > 0 ? (b.km / max) * 100 : 0 }))
})
const rangeSentence = computed(() => props.range
  ? t('story.outlook_winter', { winter: formatDistance(props.range.winterKm, { step: RANGE_STEP }), summer: formatDistance(props.range.summerKm, { step: RANGE_STEP }) })
  : '')

const weekday = computed(() => new Intl.DateTimeFormat(locale.value, { weekday: 'short' }))
const days = computed(() => (props.week?.days ?? []).map(d => {
  // Mittag in Ortszeit: der Wochentag kippt nicht an einer Zeitzonengrenze
  const label = weekday.value.format(new Date(`${d.date}T12:00:00`))
  const max = formatDecimal(d.maxC, 0)
  const min = formatDecimal(d.minC, 0)
  return { ...d, label, max, min, spoken: `${label}: ${t(`story.weather_${d.kind}`)}, ${t('story.moment_trip_day', { min, max })}` }
}))
const trend = computed(() => {
  const delta = props.week?.deltaC
  return delta == null ? null : t(delta < 0 ? 'story.outlook_week_colder' : 'story.outlook_week_warmer', { delta: Math.abs(delta) })
})
</script>

<template>
  <div data-testid="story-winter-outlook" class="grid gap-4">
    <p class="text-center text-[16px] font-semibold uppercase tracking-[0.06em] text-green-700 dark:text-green-400">{{ eyebrow }}</p>

    <section v-if="range" data-testid="outlook-range" class="grid gap-2">
      <h3 :class="caps">{{ t('story.outlook_range_title') }}</h3>
      <p class="sr-only">{{ rangeSentence }}</p>
      <!-- Balken gleicher Dicke, Zahl und Einheit daneben (CI: beschriftete Balken) -->
      <div class="grid grid-cols-[auto_minmax(0,1fr)_auto] items-center gap-x-3 gap-y-2" aria-hidden="true">
        <template v-for="b in bars" :key="b.id">
          <span class="text-[13px] text-gray-600 dark:text-gray-300">{{ b.label }}</span>
          <span class="h-2.5 rounded-full" :class="b.tone" :style="{ width: `${b.pct}%` }" :data-bar="b.id" />
          <span class="whitespace-nowrap text-right tabular-nums"><b class="text-[17px] font-extrabold text-gray-900 dark:text-gray-50">~{{ b.n }}</b>{{ ' ' }}<span class="text-[13px] font-semibold text-gray-500 dark:text-gray-400">{{ distanceUnitLabel() }}</span></span>
        </template>
      </div>
    </section>

    <section v-if="days.length" data-testid="outlook-week" class="grid gap-2">
      <div class="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-0.5">
        <h3 :class="caps">{{ t('story.outlook_week_title', { days: days.length }, days.length) }}</h3>
        <p v-if="trend" data-testid="outlook-trend" class="text-[13px] text-gray-600 tabular-nums dark:text-gray-300"><BoldNumbers :text="keepUnits(trend)" /></p>
      </div>
      <ol class="grid gap-1" :style="{ gridTemplateColumns: `repeat(${days.length}, minmax(0, 1fr))` }">
        <li v-for="d in days" :key="d.date" data-testid="outlook-day" class="relative text-center tabular-nums">
          <span class="sr-only">{{ d.spoken }}</span>
          <span class="grid justify-items-center gap-1" aria-hidden="true">
            <span class="text-[12px] font-semibold text-gray-600 dark:text-gray-300">{{ d.label }}</span>
            <WeatherIcon :kind="d.kind" class="h-7 w-7" />
            <span class="text-[15px] font-bold leading-none text-gray-900 dark:text-gray-50">{{ d.max }}°</span>
            <span class="text-[13px] leading-none text-gray-500 dark:text-gray-400">{{ d.min }}°</span>
          </span>
        </li>
      </ol>
    </section>
  </div>
</template>
