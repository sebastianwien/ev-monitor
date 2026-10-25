<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ChevronDownIcon, InformationCircleIcon } from '@heroicons/vue/24/outline'
import PeriodDayBars from './PeriodDayBars.vue'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import { useCommunityComparison } from '../../composables/useCommunityComparison'
import { periodLabel, isoWeekNumber } from '../../utils/tripTimeFormat'
import type { PeriodGroup } from '../../utils/tripPeriods'

/** Community-Schnitte der Modellgruppe, gegen die Verbrauch und Kosten eingefaerbt werden. */
export interface CommunityBenchmark {
  consumptionKwhPer100km: number | null
  costPer100km: number | null
}

/**
 * Kopfzeile eines Zeitraums im Log-Feed - Tag, Woche oder Monat.
 *
 * Oben Name und Bilanz-Chips in einer Zeile, darunter mittig das Tagesraster
 * ({@link PeriodDayBars}). Mittig statt neben den Chips, damit das Raster in jedem
 * Kopf an derselben Stelle steht - rechts von unterschiedlich breiten Chip-Zeilen
 * wuerde es von Gruppe zu Gruppe springen.
 */
const props = defineProps<{
  group: PeriodGroup
  expanded: boolean
  /** Schmale Ansicht: alles untereinander, Raster unter den Chips. */
  compact?: boolean
  community?: CommunityBenchmark | null
  /** Standverlust-Summe des Zeitraums in kWh - bereits gegated, null blendet den Chip aus. */
  phantomKwh?: number | null
  /**
   * Nur einen Teil rendern: 'title' ist die Zeile, die beim Scrollen kleben bleibt (Name,
   * Zaehlung, Chevron), 'body' die Bilanz-Chips samt Tagesraster, die mitscrollen. Ein ganzer
   * klebender Kopf belegte auf dem Handy bis zu 240 px - ein Drittel des Bildschirms.
   */
  part?: 'title' | 'body'
}>()

const showTitle = computed(() => props.part !== 'body')
const showBody = computed(() => props.part !== 'title')
const paddingClass = computed(() =>
  props.part === 'title' ? 'pt-3 pb-1' : props.part === 'body' ? 'pb-3' : 'py-3')

const { t, locale } = useI18n()
const { formatDistance, formatConsumption, formatCurrency, formatDecimal } = useLocaleFormat()
const { comparisonLevel, comparisonDeltaPercent, comparisonTooltip } = useCommunityComparison()

const consumptionLevel = computed(() =>
  comparisonLevel(props.group.totals.kwhPer100km, props.community?.consumptionKwhPer100km))
const consumptionDelta = computed(() =>
  comparisonDeltaPercent(props.group.totals.kwhPer100km, props.community?.consumptionKwhPer100km))
const consumptionTooltip = computed(() =>
  props.community?.consumptionKwhPer100km == null
    ? null
    : comparisonTooltip(props.group.totals.kwhPer100km, props.community.consumptionKwhPer100km,
        formatConsumption(props.community.consumptionKwhPer100km)))

const costLevel = computed(() =>
  comparisonLevel(props.group.totals.costPer100km, props.community?.costPer100km))
const costDelta = computed(() =>
  comparisonDeltaPercent(props.group.totals.costPer100km, props.community?.costPer100km))
const costTooltip = computed(() =>
  props.community?.costPer100km == null
    ? null
    : comparisonTooltip(props.group.totals.costPer100km, props.community.costPer100km,
        `${formatCurrency(props.community.costPer100km)}/100km`))

const label = computed(() => {
  const text = periodLabel(props.group.periodKey, props.group.level, locale.value, new Date())
  return text || t('logs.period.no_trips')
})

const weekNumber = computed(() =>
  props.group.level === 'week' ? isoWeekNumber(props.group.periodKey) : null,
)

const isMonth = computed(() => props.group.level === 'month')

const hasBars = computed(() => (props.group.bars?.length ?? 0) > 0)

/** Zahl und Einheit getrennt setzen ("21,2" fett, "kWh/100km" klein) - am ersten Leerzeichen. */
function splitUnit(text: string): { n: string; unit: string } {
  const at = text.indexOf(' ')
  return at > 0 ? { n: text.slice(0, at), unit: text.slice(at + 1) } : { n: text, unit: '' }
}

/** Abweichung vom Schnitt als kleiner Text neben der Zahl, nur ausserhalb des ±5-%-Bands. */
function deltaText(delta: number | null, level: string | null): string | null {
  if (delta == null || !level || level === 'similar') return null
  return `${delta > 0 ? '+' : '−'}${Math.abs(delta)} %`
}
/** Besser als der Schnitt gruen (Ergebnis), schlechter amber - nie rot (App-CI). */
function deltaClass(level: string | null): string {
  return level === 'better' ? 'text-green-700 dark:text-green-400' : 'text-amber-700 dark:text-amber-400'
}

const distance = computed(() => {
  const t = props.group.totals
  if (t.kmIsOdometerEstimate && !(t.km > 0)) return null
  const parts = splitUnit(formatDistance(t.km))
  return { ...parts, n: (t.kmIsOdometerEstimate ? '~' : '') + parts.n }
})
const consumption = computed(() =>
  props.group.totals.kwhPer100km == null ? null : splitUnit(formatConsumption(props.group.totals.kwhPer100km)))
const cost = computed(() => {
  if (props.group.totals.costPer100km == null) return null
  const parts = splitUnit(formatCurrency(props.group.totals.costPer100km))
  return { n: parts.n, unit: `${parts.unit}/100km` }
})
const consumptionDeltaText = computed(() => deltaText(consumptionDelta.value, consumptionLevel.value))
const costDeltaText = computed(() => deltaText(costDelta.value, costLevel.value))
const hasEnergyLine = computed(() =>
  props.group.totals.chargeCount > 0 || props.group.totals.chargedKwh > 0
  || props.group.totals.consumedKwh != null || !!props.phantomKwh)
</script>

<template>
  <div class="flex flex-col gap-2 px-4 select-none" :class="paddingClass">
    <div v-if="showTitle" class="flex items-center gap-2">
      <div data-testid="period-title" class="flex min-w-0 flex-1 flex-wrap items-baseline gap-x-2">
        <span class="whitespace-nowrap text-[17px] font-bold tracking-tight text-gray-900 md:text-lg dark:text-gray-50">
          {{ label }}
        </span>
        <span class="truncate text-[12.5px] text-gray-500 tabular-nums dark:text-gray-400">
          <template v-if="weekNumber">{{ t('logs.period.week_number', { week: weekNumber }) }} &middot; </template>
          {{ t('dashboard.trip_group_count', { count: group.totals.tripCount }, group.totals.tripCount) }}
          <template v-if="group.totals.chargeCount">
            &middot; {{ t('logs.period.charges', { count: group.totals.chargeCount }, group.totals.chargeCount) }}
          </template>
        </span>
      </div>
      <ChevronDownIcon class="h-4 w-4 shrink-0 text-gray-400 transition-transform duration-300 ease-out"
        :class="expanded ? 'rotate-180' : ''" aria-hidden="true" />
    </div>

    <!-- Bilanz als Kennzahlen statt Pillen: Zahl fett, Einheit klein, Vergleich als kleiner Text.
         Nur Summen und was direkt daraus folgt, nichts Geschaetztes ausser der ~Strecke ohne Fahrten. -->
    <div v-if="showBody" data-testid="period-chips" class="grid gap-1 tabular-nums">
      <div class="flex flex-wrap items-baseline gap-x-4 gap-y-1">
        <span v-if="distance" class="inline-flex items-baseline gap-1 whitespace-nowrap"
          :class="group.totals.kmIsOdometerEstimate ? 'cursor-help' : ''"
          :title="group.totals.kmIsOdometerEstimate ? t('logs.period.km_estimate_hint') : undefined">
          <b class="text-base font-bold text-gray-900 dark:text-gray-50">{{ distance.n }}</b>
          <span class="text-[12.5px] font-semibold text-gray-500 dark:text-gray-400">{{ distance.unit }}</span>
        </span>
        <span v-if="consumption" class="inline-flex items-baseline gap-1 whitespace-nowrap" :title="consumptionTooltip ?? undefined">
          <b class="text-base font-bold text-gray-900 dark:text-gray-50">{{ consumption.n }}</b>
          <span class="text-[12.5px] font-semibold text-gray-500 dark:text-gray-400">{{ consumption.unit }}</span>
          <span v-if="consumptionDeltaText" class="text-[12px] font-semibold" :class="deltaClass(consumptionLevel)">{{ consumptionDeltaText }}</span>
        </span>
        <span v-if="cost" class="inline-flex items-baseline gap-1 whitespace-nowrap" :title="costTooltip ?? undefined">
          <b class="text-base font-bold text-gray-900 dark:text-gray-50">{{ cost.n }}</b>
          <span class="text-[12.5px] font-semibold text-gray-500 dark:text-gray-400">{{ cost.unit }}</span>
          <span v-if="costDeltaText" class="text-[12px] font-semibold" :class="deltaClass(costLevel)">{{ costDeltaText }}</span>
        </span>
        <span v-if="group.totals.unmeasuredTrips" class="inline-flex cursor-help self-center text-gray-400 dark:text-gray-500"
          :title="t('logs.period.unmeasured', { count: group.totals.unmeasuredTrips }, group.totals.unmeasuredTrips)"
          :aria-label="t('logs.period.unmeasured', { count: group.totals.unmeasuredTrips }, group.totals.unmeasuredTrips)">
          <InformationCircleIcon class="h-4 w-4" />
        </span>
      </div>
      <!-- Energie des Zeitraums: Farbpunkte wie im Tagesraster (geladen gruen, gefahren blau), Stand amber. -->
      <div v-if="hasEnergyLine" class="flex flex-wrap items-center gap-x-3 gap-y-0.5 text-[12.5px] text-gray-600 dark:text-gray-300">
        <span v-if="group.totals.chargeCount > 0 || group.totals.chargedKwh > 0" class="inline-flex items-center gap-1.5 whitespace-nowrap"
          :title="t('logs.period.charges', { count: group.totals.chargeCount }, group.totals.chargeCount)">
          <i class="h-2 w-2 rounded-[2px] bg-green-700 dark:bg-green-400" aria-hidden="true" />
          <template v-if="group.totals.chargedKwh > 0">{{ formatDecimal(group.totals.chargedKwh) }} kWh</template>
          <template v-else>{{ group.totals.chargeCount }}&times;</template>
          {{ t('logs.legend.slim_charged') }}
        </span>
        <span v-if="group.totals.consumedKwh != null" class="inline-flex items-center gap-1.5 whitespace-nowrap">
          <i class="h-2 w-2 rounded-[2px] bg-sky-500 dark:bg-sky-400" aria-hidden="true" />
          {{ formatDecimal(group.totals.consumedKwh) }} kWh {{ t('logs.legend.slim_driven') }}
        </span>
        <span v-if="phantomKwh" class="inline-flex items-center gap-1.5 whitespace-nowrap" :title="t('dashboard.phantom_drain_word')">
          <i class="h-2 w-2 rounded-[2px] bg-amber-400" aria-hidden="true" />
          {{ formatDecimal(phantomKwh) }} kWh {{ t('logs.legend.slim_phantom') }}
        </span>
      </div>
    </div>

    <div v-if="showBody && hasBars" data-testid="period-bars" class="pt-1 md:max-w-xl">
      <PeriodDayBars :bars="group.bars" :month="isMonth" />
    </div>
  </div>
</template>
