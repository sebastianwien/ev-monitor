<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { BoltIcon, ChevronDownIcon, ChevronUpIcon } from '@heroicons/vue/24/outline'
import ComparisonChip from './ComparisonChip.vue'
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
}>()

const { t, locale } = useI18n()
const { formatDistance, formatConsumption, formatCurrency } = useLocaleFormat()
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
</script>

<template>
  <div class="flex flex-col gap-1.5 px-3 py-2.5 select-none">
    <!-- Ab 1024px stehen Name, Kennzahlen und Chevron in einer Zeile; darunter bricht die
         Bilanz um, weil sie sonst die Namen abschneiden wuerde. -->
    <div class="flex items-start gap-2">
      <div class="min-w-0 flex-1 flex flex-col gap-1" :class="compact ? '' : 'lg:flex-row lg:items-baseline lg:gap-4'">
        <div class="flex items-baseline gap-2 min-w-0">
          <span class="text-base sm:text-lg font-bold tracking-tight text-gray-900 dark:text-gray-100 whitespace-nowrap">
            {{ label }}
          </span>
          <span class="text-xs text-gray-500 dark:text-gray-400 truncate">
            <template v-if="weekNumber">{{ t('logs.period.week_number', { week: weekNumber }) }} &middot; </template>
            {{ t('dashboard.trip_group_count', { count: group.totals.tripCount }, group.totals.tripCount) }}
            <template v-if="group.totals.chargeCount">
              &middot; {{ t('logs.period.charges', { count: group.totals.chargeCount }, group.totals.chargeCount) }}
            </template>
          </span>
        </div>

        <!-- Bilanz als Chips: nur Summen und was direkt daraus folgt, nichts Geschaetztes.
             Verbrauch und Kosten tragen die Community-Einordnung als Farbe, der Rest bleibt neutral. -->
        <div class="flex items-center gap-x-1.5 gap-y-1 flex-wrap tabular-nums"
             :class="compact ? '' : 'lg:flex-1 lg:gap-x-2'">
          <!-- Ohne Fahrten stammt die Strecke aus dem Odometer-Delta der Ladungen - eine
               Schaetzung (nur der Kern zwischen In-Period-Ladungen). "~" statt "+" plus
               Tooltip machen das ehrlich; bei 0 km (nichts Messbares) faellt der Chip weg. -->
          <span v-if="!group.totals.kmIsOdometerEstimate || group.totals.km > 0"
                class="inline-flex items-center px-2 py-0.5 border rounded-full text-xs font-medium whitespace-nowrap
                       bg-white dark:bg-gray-800 border-gray-300 dark:border-gray-600 text-emerald-700 dark:text-emerald-400"
                :class="group.totals.kmIsOdometerEstimate ? 'cursor-help' : ''"
                :title="group.totals.kmIsOdometerEstimate ? t('logs.period.km_estimate_hint') : undefined">
            {{ group.totals.kmIsOdometerEstimate ? '~' : '+' }}{{ formatDistance(group.totals.km) }}
          </span>
          <span v-if="group.totals.consumedKwh != null"
                class="inline-flex items-center px-2 py-0.5 border rounded-full text-xs font-medium whitespace-nowrap
                       bg-white dark:bg-gray-800 border-gray-300 dark:border-gray-600 text-rose-500 dark:text-rose-300">
            &minus;{{ group.totals.consumedKwh.toFixed(1) }} kWh
          </span>
          <ComparisonChip v-if="group.totals.kwhPer100km != null" :level="consumptionLevel"
                          :tooltip="consumptionTooltip" :delta-percent="consumptionDelta">
            {{ formatConsumption(group.totals.kwhPer100km) }}
          </ComparisonChip>
          <ComparisonChip v-if="group.totals.costPer100km != null" :level="costLevel"
                          :tooltip="costTooltip" :delta-percent="costDelta">
            {{ formatCurrency(group.totals.costPer100km) }}/100km
          </ComparisonChip>
          <!-- Ladungen tragen die Indigo-Identitaet der Ladezeilen: so verraet auch der
               eingeklappte Kopf auf einen Blick, dass der Zeitraum Ladevorgaenge enthaelt. -->
          <span v-if="group.totals.chargeCount > 0 || group.totals.chargedKwh > 0"
                class="inline-flex items-center gap-0.5 px-2 py-0.5 border rounded-full text-xs font-medium whitespace-nowrap
                       bg-indigo-100/80 dark:bg-indigo-900/40 border-indigo-300/70 dark:border-indigo-700/50 text-indigo-700 dark:text-indigo-300"
                :title="t('logs.period.charges', { count: group.totals.chargeCount }, group.totals.chargeCount)">
            <BoltIcon class="w-3 h-3" />
            <template v-if="group.totals.chargedKwh > 0">+{{ group.totals.chargedKwh.toFixed(1) }} kWh</template>
            <template v-else>{{ group.totals.chargeCount }}&times;</template>
          </span>
          <span v-if="phantomKwh"
                class="inline-flex items-center gap-0.5 px-2 py-0.5 border rounded-full text-xs font-medium whitespace-nowrap
                       bg-white dark:bg-gray-800 border-gray-300 dark:border-gray-600 text-amber-500 dark:text-amber-500"
                :title="t('dashboard.phantom_drain_word')">
            <BoltIcon class="w-3 h-3" />{{ phantomKwh.toFixed(1) }} kWh
          </span>
          <span v-if="group.totals.unmeasuredTrips" class="text-gray-400 dark:text-gray-500 cursor-help text-[13px]"
                :title="t('logs.period.unmeasured', { count: group.totals.unmeasuredTrips }, group.totals.unmeasuredTrips)">
            &#9432;
          </span>
        </div>
      </div>

      <ChevronUpIcon v-if="expanded" class="w-4 h-4 text-emerald-500 shrink-0 mt-1" />
      <ChevronDownIcon v-else class="w-4 h-4 text-emerald-500 shrink-0 mt-1" />
    </div>

    <!-- Tagesraster mittig unter der Bilanz - siehe Kopfkommentar. -->
    <div v-if="hasBars" class="mt-0.5 flex justify-center overflow-x-auto">
      <PeriodDayBars :bars="group.bars" :month="isMonth" class="shrink-0" />
    </div>
  </div>
</template>
