<template>
  <li class="border-b border-gray-200 dark:border-gray-800">
    <div class="mr-grid relative pr-4 transition-colors hover:bg-white/70 lg:pr-5 lg:hover:bg-gray-50 dark:hover:bg-gray-900/60 lg:dark:hover:bg-gray-800/60">
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

      <!-- Photo flush with the row's left, top and bottom edge; the rank sits on it -->
      <span class="mr-th pointer-events-none relative self-stretch overflow-hidden bg-gray-200 dark:bg-gray-800" :class="inCompare ? 'ring-2 ring-inset ring-green-600 dark:ring-green-400' : ''">
        <img v-if="imageUrl" :src="imageUrl" alt="" loading="lazy" class="absolute inset-0 h-full w-full object-cover" />
        <span v-else class="absolute inset-0 grid place-items-center text-base font-bold text-gray-500 dark:text-gray-400">{{ m.brandDisplayName.slice(0, 2) }}</span>
        <span class="absolute left-1.5 top-1.5 rounded-md bg-gray-900/75 px-1.5 py-0.5 text-[11px] font-semibold tabular-nums text-white">{{ item.rank }}</span>
      </span>

      <span class="mr-who pointer-events-none grid min-w-0 pt-3 lg:pt-2.5">
        <span class="truncate font-semibold tracking-tight text-gray-900 dark:text-gray-100">
          <template v-if="nameRepeatsBrand">{{ modelName }}</template>
          <template v-else>{{ m.brandDisplayName }} <span class="font-normal text-gray-500 dark:text-gray-400">{{ modelName }}</span></template>
        </span>
      </span>

      <!-- Meta: class, cost (phone) and data basis. Own grid row so it spans the value column on the phone and wraps on the desktop. -->
      <span class="mr-meta pointer-events-none grid min-w-0 text-[12.5px] leading-snug text-gray-500 dark:text-gray-400">
        <span class="truncate lg:hidden">{{ categoryLabel }} · <span data-testid="savings">{{ costVsCombustion }}</span></span>
        <span class="truncate lg:whitespace-normal">
          <span class="hidden lg:inline">{{ categoryLabel }} · </span>{{ dataBasis }}
          <InformationCircleIcon v-if="item.singleDriver" class="pointer-events-auto inline h-4 w-4 align-text-bottom text-orange-600 dark:text-orange-400" role="img" :aria-label="t('models_ranking.row.single_driver')" :title="t('models_ranking.row.single_driver')" />
        </span>
      </span>

      <!-- The verdict for the reader's own numbers: own line, status dot, body colour -->
      <span class="mr-hint pointer-events-none flex min-w-0 items-start gap-1.5 pb-3 text-[13.5px] leading-snug text-gray-900 lg:pb-2.5 dark:text-gray-100">
        <i class="mt-[6px] h-2 w-2 flex-none rounded-full" :class="tripStatusClass" aria-hidden="true"></i>
        <span class="line-clamp-3 lg:line-clamp-none" data-testid="needs-hint">{{ needsHint }}</span>
      </span>

      <span class="mr-val pointer-events-none grid pt-3 text-right lg:hidden">
        <b class="text-[19px] font-semibold leading-tight tracking-tight tabular-nums" :class="mainValue.cls">{{ mainValue.value }}</b>
        <small class="text-[11px] text-gray-500 dark:text-gray-400">{{ mainValue.unit }}</small>
      </span>

      <ConsumptionBars
        class="mr-ld pointer-events-none pb-3.5 lg:pb-0"
        :axis="axis"
        :real="m.avgConsumptionKwhPer100km"
        :wltp="item.wltpKwhPer100km"
        :real-text="formatConsumption(m.avgConsumptionKwhPer100km, { showUnit: false })"
        :wltp-text="formatConsumption(item.wltpKwhPer100km, { showUnit: false })"
        :deviation-pct="item.wltpDeviationPct"
      />

      <!-- Desktop cost cell: electricity and the class's combustion car, both per 100 km -->
      <span class="mr-c3 pointer-events-none hidden content-center gap-0.5 whitespace-nowrap text-right leading-tight lg:grid" data-testid="cost-cell">
        <span class="text-[15px] tabular-nums" :class="colClass('cost')">
          <span class="mr-1 text-[11px] font-normal text-gray-500 dark:text-gray-400">{{ t('models_ranking.row.electricity') }}</span>{{ costNumber }} <span class="text-[11px] font-normal text-gray-500 dark:text-gray-400">{{ unitSystem.currencySymbol }}</span>
        </span>
        <span class="text-[11.5px] tabular-nums text-gray-500 dark:text-gray-400">{{ combustionCaption }}</span>
      </span>
      <!-- Desktop range cell (from 1280 px): typical span, below it the winter span -->
      <span class="mr-c4 pointer-events-none hidden content-center gap-0.5 whitespace-nowrap text-right leading-tight xl:grid" data-testid="range-cell">
        <span class="text-[15px] tabular-nums" :class="colClass('range')">{{ rangeValue }} <span class="text-[11px] font-normal text-gray-500 dark:text-gray-400">{{ distanceUnitLabel() }}</span></span>
        <span v-for="line in rangeLines" :key="line" class="text-[11.5px] tabular-nums text-gray-500 dark:text-gray-400">{{ line }}</span>
      </span>

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

    <div v-if="expanded" :id="detailId" class="grid gap-3.5 px-4 pb-[18px] pt-3 lg:grid-cols-2 lg:items-start lg:pb-5 lg:pl-[122px] lg:pr-5">
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
import { ArrowRightIcon, BanknotesIcon, BoltIcon, ChartBarIcon, CheckIcon, ClockIcon, DocumentTextIcon, InformationCircleIcon, MapIcon, PlusIcon, TagIcon } from '@heroicons/vue/24/outline'
import ConsumptionBars from './ConsumptionBars.vue'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import { officialModelImageUrl } from '../../config/modelImages'
import { convertCostPerDistance } from '../../utils/unitConversions'
import type { LadderAxis } from '../../utils/ladderScale'
import { stopsRangeKm, ONE_STOP_MINUTES, USABLE_BATTERY_SHARE } from '../../utils/needsCheck'
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
  /** Longest trip in km, named in the hint so the reader sees their own number */
  longestTripKm: number
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
  const tripParams = { trip: formatNumber(Math.round(convertDistance(props.longestTripKm))), unit: distanceUnitLabel() }
  if (trip.single) {
    parts.push(trip.min === 0
      ? t('models_ranking.row.needs_trip_none', tripParams)
      : t('models_ranking.row.needs_trip', { ...tripParams, count: formatNumber(trip.min) }, trip.min))
  } else if (trip.min === 0 && trip.max === 1) {
    parts.push(t('models_ranking.row.needs_trip_maybe', tripParams))
  } else {
    parts.push(t('models_ranking.row.needs_trip_span', { ...tripParams, min: formatNumber(trip.min), max: formatNumber(trip.max) }))
  }
  return parts.join(' · ')
})
// Dot in front of the hint: green when every battery makes the trip without a stop, orange otherwise
const tripStatusClass = computed(() => {
  const n = props.item.needs
  if (!n.assessable) return 'bg-gray-300 dark:bg-gray-600'
  return n.tripStops.max === 0 ? 'bg-green-600 dark:bg-green-400' : 'bg-orange-500 dark:bg-orange-400'
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
// Absolute cost of the assumed combustion car per 100 km (EV cost plus savings); the
// expanded detail carries the difference per 100 km and per year
const combustionCostEur = computed(() => {
  const ev = props.item.costPer100kmEur
  const s = props.item.savingsPer100kmEur
  return ev != null && s != null ? ev + s : null
})
// Phone meta line: "3,22 €/100km (Benziner 15,05)"
const costVsCombustion = computed(() => combustionCostEur.value != null
  ? t('models_ranking.row.cost_vs_combustion', {
      cost: costLabel.value,
      fuel: fuelLabel.value,
      combustion: formatDecimal(convertCostPerDistance(combustionCostEur.value, unitSystem.value), 2),
    })
  : costLabel.value)
// Desktop cost cell caption: "Benziner 14,62 €" (the litres per class sit in the detail and the sheet)
const litersLabel = computed(() => props.item.combustionLitersPer100km != null ? formatDecimal(props.item.combustionLitersPer100km, 1) : '')
const combustionCaption = computed(() => combustionCostEur.value != null
  ? t('models_ranking.row.combustion_short', {
      fuel: fuelLabel.value,
      amount: formatCurrency(convertCostPerDistance(combustionCostEur.value, unitSystem.value)),
    })
  : '')

// Range column: typical span from the smallest to the largest battery, real range as fallback
const typicalSpan = computed(() => {
  const lo = m.value.typicalRangeMinKm
  const hi = m.value.typicalRangeMaxKm
  if (lo == null || hi == null) return null
  return { lo, hi }
})
// Range with one and with two 20-minute fast-charge stops (largest battery); the stop adds
// the model's community DC power when known, else the flat 70 % (same rule as the hint)
const stopsRange = computed(() => {
  const base = typicalSpan.value?.hi ?? m.value.realRangeKm ?? null
  if (base == null) return null
  const one = stopsRangeKm(base, m.value.avgConsumptionKwhPer100km, m.value.fastChargePowerKw, 1)
  const two = stopsRangeKm(base, m.value.avgConsumptionKwhPer100km, m.value.fastChargePowerKw, 2)
  if (one == null || two == null) return null
  const dcBased = m.value.fastChargePowerKw != null && m.value.avgConsumptionKwhPer100km != null
  return { one, two, addedKm: one - Math.round(base * USABLE_BATTERY_SHARE), dcBased }
})
const fmtKm = (km: number) => formatDistance(km, { showUnit: false })
// Desktop range cell: the largest battery as the number, below it the smallest battery and one stop
const rangeValue = computed(() => {
  const span = typicalSpan.value
  if (span) return fmtKm(span.hi)
  return m.value.realRangeKm != null ? fmtKm(m.value.realRangeKm) : '–'
})
const rangeLines = computed(() => {
  const lines: string[] = []
  const span = typicalSpan.value
  if (span && span.lo !== span.hi) lines.push(t('models_ranking.row.small_battery', { value: fmtKm(span.lo) }))
  if (stopsRange.value) lines.push(t('models_ranking.row.stops_one', { value: fmtKm(stopsRange.value.one) }))
  return lines
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
    : 'font-medium text-gray-700 dark:text-gray-300'
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
      label: t('models_ranking.detail.savings', { fuel: fuelLabel.value, liters: litersLabel.value, unit: distanceUnitLabel() }),
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
      icon: ClockIcon,
      label: t('models_ranking.detail.stops'),
      value: stopsRange.value ? `${fmtKm(stopsRange.value.one)} / ${formatDistance(stopsRange.value.two)}` : '–',
      hint: stopsRange.value && stopsRange.value.dcBased && x.fastChargePowerKw != null
        ? t('models_ranking.detail.stops_hint', {
            minutes: formatNumber(ONE_STOP_MINUTES),
            added: fmtKm(stopsRange.value.addedKm),
            unit: distanceUnitLabel(),
            kw: formatNumber(Math.round(x.fastChargePowerKw)),
          })
        : t('models_ranking.detail.stops_flat'),
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
  grid-template-columns: 96px minmax(0, 1fr) auto;
  grid-template-areas: "th who val" "th meta meta" "th hint hint" ". ld ld";
  column-gap: 10px;
  row-gap: 4px;
  align-items: center;
}
.mr-grid.mr-ruler { grid-template-areas: ". ld ld"; row-gap: 0; }
.mr-th { grid-area: th; }
.mr-meta { grid-area: meta; }
.mr-who { grid-area: who; }
.mr-val { grid-area: val; }
.mr-hint { grid-area: hint; }
.mr-ld { grid-area: ld; }
.mr-c3 { grid-area: c3; }
.mr-c4 { grid-area: c4; }
.mr-cb { grid-area: cb; }
/* Desktop: name block wide enough for the meta line, the hint runs across the whole row.
   The bar pair (manufacturer vs. drivers) carries the consumption, then the cost cell and
   from 1280 px the range cell; below that the board next to the filter column is too
   narrow for it. 626 px of content at 1024 px. */
@media (min-width: 1024px) {
  .mr-grid {
    grid-template-columns: 112px minmax(140px, 1.6fr) minmax(180px, 1.3fr) 128px 26px;
    grid-template-areas: "th who ld c3 cb" "th meta ld c3 cb" "th hint hint c3 cb";
    column-gap: 8px;
    row-gap: 2px;
  }
  .mr-grid.mr-ruler {
    grid-template-areas: ". who ld c3 cb";
  }
}
@media (min-width: 1280px) {
  .mr-grid {
    grid-template-columns: 112px minmax(215px, 1.6fr) minmax(220px, 1.3fr) 128px 104px 26px;
    column-gap: 10px;
    grid-template-areas: "th who ld c3 c4 cb" "th meta ld c3 c4 cb" "th hint hint c3 c4 cb";
  }
  .mr-grid.mr-ruler {
    grid-template-areas: ". who ld c3 c4 cb";
  }
}
</style>
