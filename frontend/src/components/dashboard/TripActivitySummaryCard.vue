<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'
import { MapPinIcon, MoonIcon, InformationCircleIcon } from '@heroicons/vue/24/outline'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import type { TripMonthSummary } from '../../utils/tripMonthSummary'

/**
 * Ersetzt den "keine Daten"-Empty-State, wenn im Zeitraum Fahrten, aber keine Ladung vorliegen.
 * Zeigt Antriebsverbrauch (NICHT nachgeladen) und - nur fuer Analytics-Berechtigte - den
 * Standby-/Phantomverlust; sonst einen Teaser ohne Zahl (weiches Gate, siehe feature-teasing).
 */
const props = defineProps<{
  summary: TripMonthSummary
  monthLabel: string
  canViewAnalytics: boolean
  upsellTarget: string
}>()

const { t, locale } = useI18n()
const { formatDistance, formatConsumption } = useLocaleFormat()

const localeTag = computed(() => (locale.value === 'en' ? 'en-GB' : locale.value))
const formatKwh = (v: number) =>
  `${v.toLocaleString(localeTag.value, { maximumFractionDigits: 2 })} kWh`

/**
 * Voller Kalendermonat als Aktivitaetsstreifen: eine schmale Spalte je Tag, damit Fahrtage im
 * Kontext der fahrtfreien Tage lesbar werden - statt weniger fetter Balken ohne Bezug.
 * Bei einem mehrmonatigen Fenster (selten im no-charge-Fall) fallen wir auf die zusammenhaengende
 * Tagesspanne zurueck; ist auch die zu breit, zeigen wir nur die Fahrtage.
 */
interface DayCell {
  dateKey: string
  km: number
  trips: number
  active: boolean
}

const dayCells = computed<DayCell[]>(() => {
  const per = new Map(props.summary.perDay.map(d => [d.dateKey, d]))
  const keys = props.summary.perDay.map(d => d.dateKey).sort()
  if (keys.length === 0) return []

  const first = keys[0]
  const last = keys[keys.length - 1]
  const [fy, fm] = first.split('-').map(Number)
  const [ly, lm] = last.split('-').map(Number)
  const cell = (dateKey: string): DayCell => {
    const hit = per.get(dateKey)
    return { dateKey, km: hit?.km ?? 0, trips: hit?.trips ?? 0, active: !!hit }
  }

  if (fy === ly && fm === lm) {
    const daysInMonth = new Date(Date.UTC(fy, fm, 0)).getUTCDate()
    const cells: DayCell[] = []
    for (let day = 1; day <= daysInMonth; day++) {
      cells.push(cell(`${fy}-${String(fm).padStart(2, '0')}-${String(day).padStart(2, '0')}`))
    }
    return cells
  }

  const startMs = Date.parse(`${first}T00:00:00Z`)
  const endMs = Date.parse(`${last}T00:00:00Z`)
  const span = Math.round((endMs - startMs) / 86_400_000) + 1
  if (span > 0 && span <= 92) {
    const cells: DayCell[] = []
    for (let i = 0; i < span; i++) {
      cells.push(cell(new Date(startMs + i * 86_400_000).toISOString().slice(0, 10)))
    }
    return cells
  }
  return props.summary.perDay.map(d => ({ dateKey: d.dateKey, km: d.km, trips: d.trips, active: true }))
})

const barMax = computed(() => Math.max(1, ...dayCells.value.filter(c => c.active).map(c => c.km)))
const barHeight = (c: DayCell) => `${Math.max(14, Math.round((c.km / barMax.value) * 100))}%`

const activeIdx = ref<number | null>(null)
const activeCell = computed(() => (activeIdx.value == null ? null : dayCells.value[activeIdx.value] ?? null))
const tooltipLeft = computed(() => {
  const n = dayCells.value.length
  if (activeIdx.value == null || n === 0) return '50%'
  const raw = ((activeIdx.value + 0.5) / n) * 100
  return `${Math.min(90, Math.max(10, raw))}%`
})

const dayLong = (dateKey: string) =>
  new Date(`${dateKey}T00:00:00`).toLocaleDateString(localeTag.value, { weekday: 'short', day: 'numeric', month: 'short' })
const axisLabel = (dateKey: string) =>
  new Date(`${dateKey}T00:00:00`).toLocaleDateString(localeTag.value, { day: 'numeric', month: 'short' })
const tripsLabel = (n: number) => t('dashboard.trip_summary_day_trips', n)
const cellAria = (c: DayCell) => `${dayLong(c.dateKey)}: ${formatDistance(c.km)}, ${tripsLabel(c.trips)}`

const showStandbyValue = computed(() => props.canViewAnalytics && props.summary.standbyKwh != null)
const showStandbyTeaser = computed(() => !props.canViewAnalytics && props.summary.standbyKwh != null)
const hasTempRange = computed(() => props.summary.tempMin != null && props.summary.tempMax != null)
</script>

<template>
  <div class="bg-white dark:bg-gray-800 border-2 border-gray-300 dark:border-gray-600 rounded-sm overflow-hidden shadow-[2px_2px_0_0_#d1d5db] dark:shadow-[2px_2px_0_0_#374151]">
    <!-- Header + einleitender Kontext (warum keine Kosten-/Verbrauchsauswertung) -->
    <div class="px-4 pt-3 pb-2 text-center">
      <div class="flex items-center justify-center gap-2">
        <MapPinIcon class="h-5 w-5 text-indigo-600 dark:text-indigo-400 shrink-0" aria-hidden="true" />
        <h2 class="text-sm font-bold text-gray-800 dark:text-gray-100 tracking-tight">
          {{ t('dashboard.trip_summary_title', { month: monthLabel }) }}
        </h2>
      </div>
      <p class="text-xs text-gray-500 dark:text-gray-400 mt-1.5 leading-relaxed max-w-md mx-auto">
        {{ t('dashboard.trip_summary_intro', { month: monthLabel }) }}
      </p>
    </div>

    <!-- Hero: distance -->
    <div class="px-4 pt-2">
      <p class="text-[11px] font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400">
        {{ t('dashboard.trip_summary_driven') }}
      </p>
      <p class="text-[44px] leading-none font-semibold tracking-tight text-gray-900 dark:text-gray-50 tabular-nums">
        {{ formatDistance(summary.totalDistanceKm, { showUnit: false }) }}<span class="text-lg text-gray-500 dark:text-gray-400 ml-1">km</span>
      </p>
      <p class="text-xs text-gray-500 dark:text-gray-400 mt-1.5">
        {{ t('dashboard.trip_summary_active_days', { trips: summary.tripCount, days: summary.activeDays }) }}
      </p>
    </div>

    <!-- Aktivitaetsstreifen: voller Monat, Fahrtage hervorgehoben, mit Hover/Focus/Tap-Detail -->
    <div v-if="dayCells.length" class="px-4 pt-4 pb-3">
      <div class="relative">
        <!-- Tooltip -->
        <div
          v-if="activeCell"
          class="pointer-events-none absolute bottom-full mb-1.5 -translate-x-1/2 z-10 whitespace-nowrap rounded-sm bg-gray-900 dark:bg-gray-700 px-2 py-1 text-[11px] font-medium text-white shadow-md"
          :style="{ left: tooltipLeft }"
        >
          {{ dayLong(activeCell.dateKey) }} · {{ formatDistance(activeCell.km) }} · {{ tripsLabel(activeCell.trips) }}
        </div>

        <div
          class="flex items-end gap-px h-16"
          role="group"
          :aria-label="t('dashboard.trip_summary_bars_alt')"
        >
          <template v-for="(c, i) in dayCells" :key="c.dateKey">
            <button
              v-if="c.active"
              type="button"
              class="flex-1 min-w-[2px] rounded-t-[2px] bg-indigo-500 dark:bg-indigo-400 hover:bg-indigo-400 dark:hover:bg-indigo-300 focus:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-1 dark:focus-visible:ring-offset-gray-800 transition-colors"
              :style="{ height: barHeight(c) }"
              :aria-label="cellAria(c)"
              @mouseenter="activeIdx = i"
              @mouseleave="activeIdx = null"
              @focus="activeIdx = i"
              @blur="activeIdx = null"
              @click="activeIdx = activeIdx === i ? null : i"
            ></button>
            <div
              v-else
              class="flex-1 min-w-[2px] h-[3px] rounded-full bg-gray-200 dark:bg-gray-700"
              aria-hidden="true"
            ></div>
          </template>
        </div>

        <!-- Achse: Anfang/Ende des Streifens -->
        <div class="flex justify-between mt-1 text-[10px] text-gray-400 dark:text-gray-500 tabular-nums">
          <span>{{ axisLabel(dayCells[0].dateKey) }}</span>
          <span>{{ axisLabel(dayCells[dayCells.length - 1].dateKey) }}</span>
        </div>
      </div>

      <div v-if="hasTempRange" class="flex items-center gap-2 mt-3 text-[11px] text-gray-500 dark:text-gray-400">
        <span class="tabular-nums">{{ Math.round(summary.tempMin!) }}°</span>
        <span class="flex-1 h-1.5 rounded-full bg-gradient-to-r from-sky-400 to-amber-400"></span>
        <span class="tabular-nums">{{ Math.round(summary.tempMax!) }}°</span>
      </div>
    </div>

    <!-- Mini metrics -->
    <div class="flex border-t border-gray-200 dark:border-gray-700">
      <div v-if="summary.drivetrainKwh != null" class="flex-1 px-4 py-2.5">
        <p class="text-[10.5px] font-semibold text-gray-500 dark:text-gray-400 mb-0.5">{{ t('dashboard.trip_summary_drivetrain') }}</p>
        <p class="text-base font-semibold text-gray-900 dark:text-gray-50 tabular-nums">{{ formatKwh(summary.drivetrainKwh) }}</p>
      </div>
      <div v-if="summary.consumptionKwhPer100km != null" class="flex-1 px-4 py-2.5 border-l border-gray-200 dark:border-gray-700">
        <p class="text-[10.5px] font-semibold text-gray-500 dark:text-gray-400 mb-0.5">{{ t('dashboard.trip_summary_consumption') }}</p>
        <p class="text-base font-semibold text-gray-900 dark:text-gray-50 tabular-nums">{{ formatConsumption(summary.consumptionKwhPer100km) }}</p>
      </div>
      <div v-if="showStandbyValue" class="flex-1 px-4 py-2.5 border-l border-gray-200 dark:border-gray-700">
        <p class="text-[10.5px] font-semibold text-gray-500 dark:text-gray-400 mb-0.5">{{ t('dashboard.trip_summary_standby') }}</p>
        <p class="text-base font-semibold text-amber-600 dark:text-amber-400 tabular-nums">{{ formatKwh(summary.standbyKwh!) }}</p>
      </div>
    </div>

    <!-- Standby teaser (non-entitled): concept, no number -->
    <RouterLink
      v-if="showStandbyTeaser"
      :to="upsellTarget"
      class="flex items-center gap-2.5 mx-4 my-3 px-3 py-2.5 rounded-sm bg-amber-50 dark:bg-amber-900/20 border border-amber-200 dark:border-amber-800/50 hover:bg-amber-100 dark:hover:bg-amber-900/30 transition"
    >
      <MoonIcon class="h-4 w-4 text-amber-600 dark:text-amber-400 shrink-0" aria-hidden="true" />
      <span class="text-xs text-gray-700 dark:text-gray-200">{{ t('dashboard.trip_summary_standby_teaser') }}</span>
    </RouterLink>

    <!-- Footnote: the honest drivetrain-vs-charged distinction -->
    <div class="flex gap-1.5 items-start px-4 py-2.5 bg-gray-50 dark:bg-gray-900/40 text-[11.5px] text-gray-500 dark:text-gray-400">
      <InformationCircleIcon class="h-3.5 w-3.5 mt-0.5 shrink-0" aria-hidden="true" />
      <span>{{ t('dashboard.trip_summary_footnote') }}</span>
    </div>
  </div>
</template>
