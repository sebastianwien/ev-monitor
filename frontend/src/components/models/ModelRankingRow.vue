<template>
  <li class="border-b border-gray-200 dark:border-gray-800">
    <div class="mr-grid relative px-4 pb-3.5 pt-3 transition-colors hover:bg-white/70 lg:px-5 lg:py-2.5 lg:hover:bg-gray-50 dark:hover:bg-gray-900/60 lg:dark:hover:bg-gray-800/60">
      <!-- Whole row toggles the details; content sits below it, only the compare box is clickable on top. -->
      <button
        type="button"
        class="absolute inset-0 h-full w-full cursor-pointer focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-green-600"
        :aria-expanded="expanded"
        :aria-controls="detailId"
        @click="emit('toggle')"
      >
        <span class="sr-only">{{ t(expanded ? 'models_ranking.row.details_close' : 'models_ranking.row.details_open', { name: m.modelDisplayName }) }}</span>
      </button>

      <span class="mr-rk pointer-events-none text-right text-[13px] font-semibold tabular-nums" :class="inCompare ? 'text-green-600 dark:text-green-400' : 'text-gray-500 dark:text-gray-400'">{{ item.rank }}</span>

      <span class="mr-th pointer-events-none grid h-9 w-[52px] place-items-center overflow-hidden rounded-lg bg-gray-200 lg:h-[34px] lg:w-12 dark:bg-gray-800" :class="inCompare ? 'ring-2 ring-green-600 dark:ring-green-400' : ''">
        <img v-if="imageUrl" :src="imageUrl" alt="" loading="lazy" class="h-full w-full object-cover" />
        <span v-else class="text-[13px] font-bold text-gray-500 dark:text-gray-400">{{ m.brandDisplayName.slice(0, 2) }}</span>
      </span>

      <span class="mr-who pointer-events-none grid min-w-0">
        <span class="truncate font-semibold tracking-tight text-gray-900 dark:text-gray-100">
          <template v-if="nameRepeatsBrand">{{ modelName }}</template>
          <template v-else>{{ m.brandDisplayName }} <span class="font-normal text-gray-500 dark:text-gray-400">{{ modelName }}</span></template>
        </span>
        <span class="flex min-w-0 items-center gap-1 text-[12.5px] text-gray-500 dark:text-gray-400">
          <span class="truncate">
            {{ categoryLabel }}
            <span class="lg:hidden"> · {{ costLabel }}</span>
          </span>
        </span>
        <span class="flex min-w-0 items-center gap-1 text-[12.5px] text-gray-500 lg:hidden dark:text-gray-400">
          <span class="truncate">{{ dataBasis }}</span>
          <InformationCircleIcon v-if="item.singleDriver" class="pointer-events-auto h-4 w-4 flex-none text-orange-600 dark:text-orange-400" role="img" :aria-label="t('models_ranking.row.single_driver')" :title="t('models_ranking.row.single_driver')" />
        </span>
      </span>

      <!-- Needs hint: full width under the name block, two lines on the phone; on the desktop one line
           across the row that also carries the data basis (the name block is too narrow for it there) -->
      <span class="mr-hint pointer-events-none line-clamp-3 text-[12.5px] leading-snug text-gray-700 lg:line-clamp-none lg:flex lg:min-w-0 lg:flex-wrap lg:items-center lg:gap-x-1 dark:text-gray-300">
        <span data-testid="needs-hint">{{ needsHint }}</span>
        <span v-if="savingsLabel" class="lg:hidden"> · <span :class="savingsClass" data-testid="savings">{{ savingsLabel }}</span></span>
        <span class="hidden whitespace-nowrap text-gray-500 lg:inline dark:text-gray-400"> · {{ dataBasis }}</span>
        <InformationCircleIcon v-if="item.singleDriver" class="pointer-events-auto hidden h-4 w-4 flex-none text-orange-600 lg:block dark:text-orange-400" role="img" :aria-label="t('models_ranking.row.single_driver')" :title="t('models_ranking.row.single_driver')" />
      </span>

      <span class="mr-val pointer-events-none grid text-right lg:hidden">
        <b class="text-[19px] font-semibold leading-tight tracking-tight tabular-nums" :class="mainValue.cls">{{ mainValue.value }}</b>
        <small class="text-[11px] text-gray-500 dark:text-gray-400">{{ mainValue.unit }}</small>
      </span>

      <ConsumptionLadder
        class="mr-ld pointer-events-none"
        :axis="axis"
        :real="m.avgConsumptionKwhPer100km"
        :wltp="item.wltpKwhPer100km"
        :band-min="m.minRealConsumptionKwhPer100km"
        :band-max="m.maxRealConsumptionKwhPer100km"
      />

      <span class="mr-c1 pointer-events-none hidden text-right text-sm tabular-nums lg:block" :class="colClass('efficient')">{{ formatConsumption(m.avgConsumptionKwhPer100km, { showUnit: false }) }}</span>
      <span class="mr-c2 pointer-events-none hidden text-right text-sm tabular-nums xl:block" :class="colClass('wltp')">{{ formatConsumption(item.wltpKwhPer100km, { showUnit: false }) }}</span>
      <span class="mr-c3 pointer-events-none hidden text-right text-sm tabular-nums lg:block" :class="colClass('cost')">{{ costNumber }}</span>
      <span class="mr-c6 pointer-events-none hidden text-right text-sm tabular-nums lg:block" :class="savingsNumber ? savingsClass : 'text-gray-500 dark:text-gray-400'" data-testid="savings-column">{{ savingsNumber ?? '–' }}</span>
      <span class="mr-c4 pointer-events-none hidden whitespace-nowrap text-right text-sm tabular-nums xl:block" :class="colClass('range')">{{ rangeCell }}</span>

      <button
        type="button"
        class="mr-cb relative hidden h-[22px] w-[22px] place-items-center rounded-md border-[1.5px] disabled:cursor-not-allowed disabled:opacity-40 lg:grid"
        :class="inCompare ? 'border-green-600 bg-green-600 text-white dark:border-green-400 dark:bg-green-400 dark:text-gray-950' : 'border-gray-300 hover:border-green-600 dark:border-gray-600'"
        :aria-pressed="inCompare"
        :aria-label="t(inCompare ? 'models_ranking.row.compare_remove' : 'models_ranking.row.compare_add', { name: m.modelDisplayName })"
        :title="!inCompare && !canAddCompare ? t('models_ranking.row.compare_full') : undefined"
        :disabled="!inCompare && !canAddCompare"
        @click="emit('compare')"
      >
        <CheckIcon v-if="inCompare" class="h-3.5 w-3.5" stroke-width="3" />
      </button>
    </div>

    <div v-if="expanded" :id="detailId" class="grid gap-3.5 px-4 pb-[18px] pt-1 lg:grid-cols-2 lg:items-start lg:pb-5 lg:pl-[124px] lg:pr-5">
      <p class="text-[13.5px] text-gray-700 lg:col-span-2 dark:text-gray-300">{{ needsHint }}</p>
      <dl class="grid grid-cols-2 gap-2 lg:col-span-2 lg:grid-cols-3">
        <div v-for="kv in facts" :key="kv.label" class="grid gap-0.5 rounded-xl border border-gray-200 bg-white px-3 py-2.5 dark:border-gray-700 dark:bg-gray-900">
          <dt class="flex items-center gap-1.5 text-xs text-gray-500 dark:text-gray-400">
            <component :is="kv.icon" class="h-4 w-4 flex-none" aria-hidden="true" />{{ kv.label }}
          </dt>
          <dd class="text-[17px] font-semibold leading-snug tracking-tight tabular-nums text-gray-900 dark:text-gray-100">{{ kv.value }}</dd>
          <dd class="text-xs text-gray-500 dark:text-gray-400">{{ kv.hint }}</dd>
        </div>
      </dl>

      <div class="grid gap-1.5" role="group" :aria-label="t('models_ranking.detail.seasons', { unit: consumptionUnitLabel() })">
        <div v-for="bar in seasonBars" :key="bar.label" class="grid grid-cols-[64px_minmax(0,1fr)_54px] items-center gap-2 text-[12.5px]">
          <span class="text-gray-600 dark:text-gray-300">{{ bar.label }}</span>
          <span class="h-2 overflow-hidden rounded bg-gray-200 dark:bg-gray-800">
            <span class="block h-full rounded" :class="bar.wltp ? 'bg-gray-400 dark:bg-gray-500' : 'bg-green-600 dark:bg-green-400'" :style="{ width: `${bar.width}%` }"></span>
          </span>
          <b class="text-right font-semibold tabular-nums text-gray-900 dark:text-gray-100">{{ bar.text }}</b>
        </div>
        <p v-if="!hasSeasons" class="text-[12.5px] text-gray-500 dark:text-gray-400">{{ t('models_ranking.detail.season_missing') }}</p>
      </div>

      <div class="flex flex-wrap gap-2 lg:justify-end">
        <button
          type="button"
          class="inline-flex min-h-11 flex-[1_1_160px] items-center justify-center gap-2 whitespace-nowrap rounded-xl border px-4 font-semibold disabled:cursor-not-allowed disabled:opacity-50"
          :class="inCompare ? 'border-green-600 text-green-700 dark:border-green-400 dark:text-green-400' : 'border-gray-300 bg-white text-gray-900 dark:border-gray-700 dark:bg-gray-900 dark:text-gray-100'"
          :aria-pressed="inCompare"
          :disabled="!inCompare && !canAddCompare"
          :title="!inCompare && !canAddCompare ? t('models_ranking.row.compare_full') : undefined"
          @click="emit('compare')"
        >
          <CheckIcon v-if="inCompare" class="h-5 w-5" aria-hidden="true" />
          <PlusIcon v-else class="h-5 w-5" aria-hidden="true" />
          {{ t(inCompare ? 'models_ranking.detail.compare_remove' : 'models_ranking.detail.compare_add') }}
        </button>
        <a :href="href" class="inline-flex min-h-11 flex-[1_1_160px] items-center justify-center gap-2 whitespace-nowrap rounded-xl bg-green-600 px-4 font-semibold text-white hover:bg-green-700">
          {{ t('models_ranking.detail.model_page', { name: m.modelDisplayName }) }}
          <ArrowRightIcon class="h-5 w-5" aria-hidden="true" />
        </a>
      </div>
    </div>
  </li>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowRightIcon, BanknotesIcon, BoltIcon, ChartBarIcon, CheckIcon, DocumentTextIcon, InformationCircleIcon, MapIcon, PlusIcon, TagIcon } from '@heroicons/vue/24/outline'
import ConsumptionLadder from './ConsumptionLadder.vue'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import { officialModelImageUrl } from '../../config/modelImages'
import { convertCostPerDistance } from '../../utils/unitConversions'
import type { LadderAxis } from '../../utils/ladderScale'
import type { RankedModel, RankingSort } from '../../composables/useModelRanking'
import type { FuelKind, MainValue } from '../../utils/costMix'

const props = defineProps<{
  item: RankedModel
  axis: LadderAxis
  sort: RankingSort
  expanded: boolean
  inCompare: boolean
  canAddCompare: boolean
  /** Link to the model detail page */
  href: string
  /** Electricity price in EUR/kWh the costs are based on */
  price: number
  /** Big number under "Sparsamste": consumption or cost per 100 km */
  mainValue: MainValue
  /** Daily distance in km the yearly savings are based on */
  dailyKm: number
  fuel: FuelKind
}>()

const emit = defineEmits<{ toggle: []; compare: [] }>()

const { t, te } = useI18n()
const {
  formatNumber, formatDecimal, formatConsumption, consumptionUnitLabel, convertConsumption,
  formatDistance, convertDistance, distanceUnitLabel, formatCostPerDistance, formatCostPerKwh, formatCurrency, unitSystem,
} = useLocaleFormat()

const m = computed(() => props.item.model)
const detailId = computed(() => `mr-detail-${props.item.key.replace(/[^A-Za-z0-9_-]/g, '-')}`)
const imageUrl = computed(() => officialModelImageUrl(m.value.model))
const modelName = computed(() => m.value.modelUrlSlug.replace(/_/g, ' '))
// "Renault 5", "Polestar 2": the model name already carries the brand, show it once
const nameRepeatsBrand = computed(() => modelName.value.toLowerCase().startsWith(m.value.brandDisplayName.toLowerCase()))
const categoryLabel = computed(() => {
  const key = `models_list.filters.categories.${m.value.category}`
  return te(key) ? t(key) : m.value.categoryDisplayName
})

const costLabel = computed(() => props.item.costPer100kmEur != null ? formatCostPerDistance(props.item.costPer100kmEur) : '–')

// Data basis: "from 412 charging sessions by 9 drivers". Counts missing on an older backend → sessions only.
const driverCount = computed(() => m.value.contributorCount ?? null)
const driversLabel = computed(() => driverCount.value != null
  ? t('models_ranking.row.drivers_plural', { count: formatNumber(driverCount.value) }, driverCount.value)
  : '')
const dataBasis = computed(() => {
  const logs = t('models_ranking.row.logs_plural', { count: formatNumber(m.value.logCount) }, m.value.logCount)
  return driverCount.value != null
    ? t('models_ranking.row.data_basis', { logs, drivers: driversLabel.value })
    : t('models_ranking.row.logs', { count: formatNumber(m.value.logCount) })
})

// Needs check per row, from the winter range (fallback: typical range, flagged as such)
const needsHint = computed(() => {
  const n = props.item.needs
  if (!n.assessable) return t('models_ranking.row.needs_unrated')
  const span = (min: number, max: number) => t('models_ranking.row.span', { min: formatNumber(min), max: formatNumber(max) })
  const parts: string[] = []
  if (n.interval) {
    const daily = n.interval.single && n.interval.min === 1
    const days = n.interval.single ? formatNumber(n.interval.min) : span(n.interval.min, n.interval.max)
    const key = n.winter
      ? (daily ? 'needs_interval_daily' : 'needs_interval')
      : (daily ? 'needs_interval_typical_daily' : 'needs_interval_typical')
    parts.push(t(`models_ranking.row.${key}`, { days }))
  } else if (n.stopsPerWeek) {
    parts.push(n.stopsPerWeek.single
      ? t('models_ranking.row.needs_stops', { count: formatNumber(n.stopsPerWeek.min) }, n.stopsPerWeek.min)
      : t('models_ranking.row.needs_stops_span', { min: formatNumber(n.stopsPerWeek.min), max: formatNumber(n.stopsPerWeek.max) }))
  }
  const trip = n.tripStops
  if (trip.single) {
    parts.push(trip.min === 0
      ? t('models_ranking.row.needs_trip_none')
      : t('models_ranking.row.needs_trip', { count: formatNumber(trip.min) }, trip.min))
  } else {
    parts.push(t('models_ranking.row.needs_trip_span', { min: formatNumber(trip.min), max: formatNumber(trip.max) }))
  }
  return parts.join(', ')
})
const costNumber = computed(() => props.item.costPer100kmEur != null
  ? formatDecimal(convertCostPerDistance(props.item.costPer100kmEur, unitSystem.value), 2)
  : '–')

// Against the assumed combustion car (null without a fuel price or outside metric markets)
const fuelLabel = computed(() => t(`models_ranking.assumptions.${props.fuel}`))
const savingsParams = computed(() => {
  const s = props.item.savingsPer100kmEur
  if (s == null) return null
  return { amount: formatCurrency(Math.abs(convertCostPerDistance(s, unitSystem.value))), fuel: fuelLabel.value, unit: distanceUnitLabel() }
})
// Meta line: short form; the expanded detail carries the full sentence with "per 100 km"
const savingsLabel = computed(() => savingsParams.value
  ? t((props.item.savingsPer100kmEur ?? 0) >= 0 ? 'models_ranking.row.cheaper_short' : 'models_ranking.row.dearer_short', savingsParams.value)
  : null)
const savingsClass = computed(() => (props.item.savingsPer100kmEur ?? 0) >= 0
  ? 'text-green-700 dark:text-green-400'
  : 'text-orange-700 dark:text-orange-400')
// Desktop column "vs. petrol": signed number in the cost column's unit, minus = EV cheaper
const savingsNumber = computed(() => {
  const s = props.item.savingsPer100kmEur
  if (s == null) return null
  return `${s >= 0 ? '−' : '+'}${formatDecimal(Math.abs(convertCostPerDistance(s, unitSystem.value)), 2)}`
})

// Range column: typical span from the smallest to the largest battery, real range as fallback
const typicalSpan = computed(() => {
  const lo = m.value.typicalRangeMinKm
  const hi = m.value.typicalRangeMaxKm
  if (lo == null || hi == null) return null
  return { lo, hi }
})
const rangeCell = computed(() => {
  const span = typicalSpan.value
  const fmt = (km: number) => formatDistance(km, { showUnit: false })
  if (span) return span.lo === span.hi ? fmt(span.hi) : `${fmt(span.lo)}–${fmt(span.hi)}`
  return m.value.realRangeKm != null ? fmt(m.value.realRangeKm) : '–'
})

function signedPct(v: number, decimals = 0): string {
  return `${v > 0 ? '+' : ''}${formatDecimal(v, decimals)} %`
}

/** Deviation as the reader sees it: in mi/kWh more is better, so the sign flips. */
const displayWltpDeviation = computed(() => {
  const real = m.value.avgConsumptionKwhPer100km
  const wltp = props.item.wltpKwhPer100km
  if (real == null || wltp == null || real <= 0 || wltp <= 0) return null
  return unitSystem.value.consumptionInverse ? ((wltp - real) / real) * 100 : props.item.wltpDeviationPct
})
// Colour always in kWh space: above WLTP is worse (orange), below is better (green)
const deviationClass = computed(() => {
  const d = props.item.wltpDeviationPct
  if (d == null) return ''
  return d > 0 ? 'text-orange-700 dark:text-orange-400' : 'text-green-700 dark:text-green-400'
})

const mainValue = computed(() => {
  const na = { value: '–', unit: t('models_ranking.row.no_value'), cls: '' }
  switch (props.sort) {
    case 'range':
      if (typicalSpan.value) {
        return { value: formatDistance(typicalSpan.value.hi, { showUnit: false }), unit: `${distanceUnitLabel()}, ${t('models_ranking.row.typical_range')}`, cls: '' }
      }
      return m.value.realRangeKm != null
        ? { value: formatDistance(m.value.realRangeKm, { showUnit: false }), unit: `${distanceUnitLabel()} ${t('models_ranking.row.real_range')}`, cls: '' }
        : na
    case 'wltp':
      return displayWltpDeviation.value != null
        ? { value: signedPct(displayWltpDeviation.value), unit: t('models_ranking.row.to_wltp'), cls: deviationClass.value }
        : na
    case 'winter':
      return props.item.winterSurchargePct != null
        ? { value: signedPct(props.item.winterSurchargePct), unit: t('models_ranking.row.in_winter'), cls: 'text-orange-700 dark:text-orange-400' }
        : { ...na, unit: t('models_ranking.row.no_season') }
    case 'data':
      return { value: formatNumber(m.value.logCount), unit: t('models_list.card.charging_sessions'), cls: '' }
    default:
      if (props.mainValue === 'cost') {
        return props.item.costPer100kmEur != null
          ? { value: costNumber.value, unit: `${unitSystem.value.currencySymbol} ${t('models_ranking.row.per_100', { unit: distanceUnitLabel() })}`, cls: '' }
          : na
      }
      return { value: formatConsumption(m.value.avgConsumptionKwhPer100km, { showUnit: false }), unit: consumptionUnitLabel(), cls: '' }
  }
})

function colClass(column: RankingSort | 'cost' | null): string {
  const active = column === props.sort || (column === 'cost' && props.sort === 'efficient' && props.mainValue === 'cost')
  return active
    ? 'font-bold text-gray-900 dark:text-gray-100'
    : 'text-gray-500 dark:text-gray-400'
}

const facts = computed(() => {
  const x = m.value
  const lo = x.minRealConsumptionKwhPer100km
  const hi = x.maxRealConsumptionKwhPer100km
  const [bandLo, bandHi] = lo != null && hi != null
    ? [convertConsumption(lo), convertConsumption(hi)].sort((a, b) => a - b)
    : [null, null]
  return [
    {
      icon: BoltIcon,
      label: t('models_ranking.detail.real'),
      value: formatConsumption(x.avgConsumptionKwhPer100km),
      hint: bandLo != null && bandHi != null
        ? t('models_ranking.detail.variant_range', { min: formatDecimal(bandLo, 1), max: formatDecimal(bandHi, 1) })
        : t('models_ranking.detail.variant_range_open'),
    },
    {
      icon: DocumentTextIcon,
      label: t('models_ranking.detail.wltp'),
      value: formatConsumption(props.item.wltpKwhPer100km),
      hint: displayWltpDeviation.value != null
        ? t('models_ranking.detail.wltp_gap', { gap: signedPct(displayWltpDeviation.value) })
        : t('models_ranking.row.no_value'),
    },
    {
      icon: BanknotesIcon,
      label: t('models_ranking.detail.cost', { unit: distanceUnitLabel() }),
      value: costLabel.value,
      hint: t('models_ranking.detail.cost_hint', { price: formatCostPerKwh(props.price) }),
    },
    ...(props.item.savingsPer100kmEur != null ? [{
      icon: BanknotesIcon,
      label: t('models_ranking.detail.savings', { fuel: fuelLabel.value, unit: distanceUnitLabel() }),
      value: savingsParams.value?.amount ?? '',
      hint: props.item.savingsPerYearEur != null
        ? t(props.item.savingsPerYearEur >= 0 ? 'models_ranking.detail.savings_year_cheaper' : 'models_ranking.detail.savings_year_dearer', {
            amount: formatCurrency(Math.abs(props.item.savingsPerYearEur), { decimals: 0 }),
            daily: formatNumber(Math.round(convertDistance(props.dailyKm))),
            unit: distanceUnitLabel(),
          })
        : '',
    }] : []),
    {
      icon: MapIcon,
      label: t('models_ranking.detail.range'),
      value: x.realRangeKm != null ? formatDistance(x.realRangeKm) : '–',
      hint: x.realRangeKm != null ? t('models_ranking.detail.range_hint') : t('models_ranking.detail.range_missing'),
    },
    {
      icon: TagIcon,
      label: t('models_ranking.detail.charge_price'),
      value: x.avgCostPerKwh != null ? formatCostPerKwh(x.avgCostPerKwh) : '–',
      hint: t('models_ranking.detail.charge_price_hint'),
    },
    {
      icon: ChartBarIcon,
      label: t('models_ranking.detail.data'),
      value: formatNumber(x.logCount),
      hint: props.item.singleDriver
        ? t('models_ranking.row.single_driver')
        : driverCount.value != null && x.carCount != null
          ? t('models_ranking.detail.data_hint', {
              drivers: driversLabel.value,
              cars: t('models_ranking.detail.cars_plural', { count: formatNumber(x.carCount) }, x.carCount),
            })
          : t('models_list.card.charging_sessions'),
    },
  ]
})

const hasSeasons = computed(() => m.value.summerConsumptionKwhPer100km != null || m.value.winterConsumptionKwhPer100km != null)

const seasonBars = computed(() => {
  const rows = [
    { label: t('models_ranking.detail.summer'), kwh: m.value.summerConsumptionKwhPer100km ?? null, wltp: false },
    { label: t('models_ranking.detail.winter'), kwh: m.value.winterConsumptionKwhPer100km ?? null, wltp: false },
    { label: t('models_ranking.columns.wltp'), kwh: props.item.wltpKwhPer100km, wltp: true },
  ].filter((r): r is { label: string; kwh: number; wltp: boolean } => r.kwh != null && r.kwh > 0)
  const max = Math.max(...rows.map(r => convertConsumption(r.kwh))) * 1.08
  return rows.map(r => ({
    ...r,
    width: (convertConsumption(r.kwh) / max) * 100,
    text: formatConsumption(r.kwh, { showUnit: false }),
  }))
})
</script>

<style>
/* Shared row grid, also used by the axis ruler in PublicModelsListViewV2 (not scoped on purpose).
   Mobile: rank, thumb, name, value on top, the ladder below across the full width.
   Desktop: one line per model with ladder and value columns. */
.mr-grid {
  display: grid;
  grid-template-columns: 26px 52px minmax(0, 1fr) auto;
  grid-template-areas: "rk th who val" ". hint hint hint" ". ld ld ld";
  column-gap: 10px;
  row-gap: 6px;
  align-items: center;
}
.mr-grid.mr-ruler { grid-template-areas: "rk ld ld ld"; row-gap: 0; }
.mr-rk { grid-area: rk; }
.mr-th { grid-area: th; }
.mr-who { grid-area: who; }
.mr-val { grid-area: val; }
.mr-hint { grid-area: hint; }
.mr-ld { grid-area: ld; }
.mr-c1 { grid-area: c1; }
.mr-c2 { grid-area: c2; }
.mr-c3 { grid-area: c3; }
.mr-c4 { grid-area: c4; }
.mr-c6 { grid-area: c6; }
.mr-cb { grid-area: cb; }
/* Desktop: name block wide enough for the meta line, the hint runs across the whole row.
   The WLTP and range columns join at 1280 px; below that the board next to the filter
   column is too narrow for them (the ladder's ring still carries the WLTP value). */
@media (min-width: 1024px) {
  .mr-grid {
    grid-template-columns: 24px 48px minmax(200px, 1.6fr) minmax(100px, 1fr) 52px 60px 70px 26px;
    grid-template-areas: "rk th who ld c1 c3 c6 cb" ". . hint hint hint hint hint .";
    column-gap: 10px;
    row-gap: 4px;
  }
  .mr-grid.mr-ruler {
    grid-template-areas: "rk th who ld c1 c3 c6 cb";
  }
}
@media (min-width: 1280px) {
  .mr-grid {
    grid-template-columns: 24px 48px minmax(250px, 1.6fr) minmax(110px, 1fr) 52px 52px 60px 70px 68px 26px;
    grid-template-areas: "rk th who ld c1 c2 c3 c6 c4 cb" ". . hint hint hint hint hint hint hint .";
  }
  .mr-grid.mr-ruler {
    grid-template-areas: "rk th who ld c1 c2 c3 c6 c4 cb";
  }
}
</style>
