<template>
  <div class="grid gap-5 px-4 pb-5 pt-3 lg:px-5">
    <!-- 1. The reader's answers first, as on the model page. Guests: the same questions with a lock, no stand-in figures, one gate. -->
    <section v-if="answers.length" :aria-label="t('models_ranking.detail.for_you')" data-testid="for-you">
      <h3 class="mb-2 text-[13px] font-semibold text-gray-500 dark:text-gray-400">{{ t('models_ranking.detail.for_you') }}</h3>
      <ul class="divide-y divide-green-600/15 overflow-hidden rounded-2xl bg-green-600/15 shadow-[0_1px_2px_rgba(0,0,0,0.05)] max-md:edge-card md:grid md:gap-px md:divide-y-0 dark:divide-green-500/15 dark:bg-green-500/15" :class="COLS[answers.length]">
        <li v-for="a in answers" :key="a.key" class="grid grid-cols-[36px_minmax(0,1fr)] gap-3 bg-white px-4 py-3.5 md:grid-cols-1 md:gap-2 dark:bg-gray-900" :data-testid="a.locked ? 'for-you-locked' : 'for-you-answer'">
          <span class="grid h-9 w-9 place-items-center rounded-full" :class="a.tone"><component :is="a.icon" class="h-5 w-5" aria-hidden="true" /></span>
          <div class="grid min-w-0 content-start gap-0.5" :class="a.locked && 'self-center md:self-start'">
            <p v-if="a.locked" class="flex items-center gap-1.5 text-[15px] font-medium text-gray-900 dark:text-gray-100">
              {{ a.label }}
              <LockClosedIcon class="h-4 w-4 flex-none text-gray-500 dark:text-gray-400" aria-hidden="true" />
              <span class="sr-only">{{ t('models_ranking.row.locked_sr') }}</span>
            </p>
            <template v-else>
              <p class="text-[13px] text-gray-600 dark:text-gray-400">{{ a.label }}</p>
              <p class="text-[22px] font-extrabold leading-tight tracking-tight tabular-nums text-gray-900 dark:text-gray-100">{{ a.value }}</p>
              <p v-for="line in a.sub" :key="line" class="text-[13px] leading-snug text-gray-600 dark:text-gray-400">{{ line }}</p>
            </template>
          </div>
        </li>
        <li v-if="locked" class="grid gap-2.5 bg-white px-4 py-4 md:col-span-full dark:bg-gray-900">
          <p class="text-[15px] font-medium leading-snug text-gray-900 dark:text-gray-100">{{ gateText }}</p>
          <button type="button" class="btn-3d cta-shadow-sm inline-flex min-h-11 items-center justify-center rounded-xl border-2 border-gray-900 bg-green-600 px-4 text-[15px] font-bold text-white hover:bg-green-700 max-md:w-full md:justify-self-start dark:border-gray-200" data-testid="row-unlock" @click="emit('unlock')">{{ t('models_ranking.detail.locked_cta') }}</button>
          <p class="text-[12.5px] text-gray-500 dark:text-gray-400">{{ t('models_ranking.detail.gate_mail') }}</p>
        </li>
      </ul>
    </section>

    <!-- 2. The data sheet: model data, free for everyone, in four compact groups -->
    <section :aria-label="t('models_ranking.detail.measured')">
      <h3 class="mb-2 text-[13px] font-semibold text-gray-500 dark:text-gray-400">{{ t('models_ranking.detail.measured') }}</h3>
      <p v-if="item.singleDriver" class="mb-2 flex items-start gap-1.5 rounded-xl bg-amber-50 px-3 py-2 text-[13px] text-amber-900 dark:bg-amber-900/30 dark:text-amber-200" data-testid="single-driver-note">
        <InformationCircleIcon class="mt-px h-4 w-4 flex-none" aria-hidden="true" />{{ t('models_ranking.row.single_driver') }}
      </p>
      <div class="grid gap-2.5 md:grid-cols-2 md:items-start">
        <div v-for="g in groups" :key="g.key" class="rounded-2xl bg-white px-4 py-2.5 shadow-[0_1px_2px_rgba(0,0,0,0.05)] dark:bg-gray-900">
          <h4 class="pt-1 text-[12px] font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400">{{ g.title }}</h4>
          <dl class="divide-y divide-gray-100 dark:divide-gray-800">
            <div v-for="r in g.rows" :key="r.label" class="flex items-baseline justify-between gap-3 py-2" :data-testid="r.testid">
              <dt class="min-w-0">
                <span class="block text-[14px] text-gray-800 dark:text-gray-200">{{ r.label }}</span>
                <span v-for="h in r.hints" :key="h" class="block text-[12.5px] leading-snug text-gray-500 dark:text-gray-400">{{ h }}</span>
                <button v-if="r.key === 'cost'" type="button" class="pointer-events-auto mt-0.5 text-[12.5px] font-semibold text-green-700 underline underline-offset-2 hover:text-green-800 dark:text-green-400" data-testid="detail-assumptions" @click="emit('assumptions')">{{ t('models_ranking.detail.assumptions_link') }}</button>
              </dt>
              <dd class="whitespace-nowrap text-right text-[16px] font-bold tabular-nums text-gray-900 dark:text-gray-100">{{ r.value }}</dd>
            </div>
          </dl>
          <!-- Consumption: the season bars under the two figures -->
          <div v-if="g.key === 'consumption'" class="grid gap-1.5 border-t border-gray-100 py-2.5 dark:border-gray-800" role="group" :aria-label="t('models_ranking.detail.seasons', { unit: consumptionUnitLabel() })">
            <div v-for="bar in seasonBars" :key="bar.label" class="grid grid-cols-[64px_minmax(0,1fr)_54px] items-center gap-2 text-[12.5px]">
              <span class="text-gray-600 dark:text-gray-300">{{ bar.label }}</span>
              <span class="h-2 overflow-hidden rounded bg-gray-200 dark:bg-gray-800">
                <span class="block h-full rounded" :class="bar.wltp ? 'bg-gray-400 dark:bg-gray-500' : 'bg-green-600 dark:bg-green-400'" :style="{ width: `${bar.width}%` }"></span>
              </span>
              <b class="text-right font-semibold tabular-nums text-gray-900 dark:text-gray-100">{{ bar.text }}</b>
            </div>
            <p v-if="!hasSeasons" class="text-[12.5px] text-gray-500 dark:text-gray-400">{{ t('models_ranking.detail.season_missing') }}</p>
          </div>
          <!-- Range: the stop ladder names its start, the motorway from a full battery -->
          <div v-if="g.key === 'range' && item.ladder" class="border-t border-gray-100 py-2.5 dark:border-gray-800" data-testid="detail-ladder">
            <p class="text-[14px] text-gray-800 dark:text-gray-200">{{ t('models_ranking.detail.ladder') }}</p>
            <p class="text-[12.5px] leading-snug text-gray-500 dark:text-gray-400">{{ t('models_ranking.detail.ladder_hint') }}</p>
            <ol class="mt-2 grid grid-cols-3 gap-1.5">
              <li v-for="s in ladder" :key="s.label" class="rounded-lg bg-gray-50 px-2 py-1.5 dark:bg-gray-800">
                <span class="block text-[12px] text-gray-500 dark:text-gray-400">{{ s.label }}</span>
                <b class="block whitespace-nowrap text-[15px] font-bold tabular-nums text-gray-900 dark:text-gray-100">{{ s.value }}</b>
              </li>
            </ol>
          </div>
        </div>
      </div>
    </section>

    <!-- 3. One way on: the model page, and what only it has. The phone compares from here. -->
    <button
      type="button"
      class="inline-flex min-h-11 items-center justify-center gap-1.5 rounded-xl border-2 px-4 font-semibold disabled:cursor-not-allowed disabled:opacity-40 lg:hidden"
      :class="inCompare ? 'border-green-600 bg-green-600 text-white dark:border-green-400 dark:bg-green-400 dark:text-gray-950' : 'border-gray-900 bg-white text-gray-900 dark:border-gray-200 dark:bg-gray-900 dark:text-gray-100'"
      :aria-pressed="inCompare"
      :disabled="!inCompare && !canAddCompare"
      data-testid="compare-open"
      @click="emit('compare')"
    >
      <CheckIcon v-if="inCompare" class="h-4 w-4" stroke-width="2.5" aria-hidden="true" />
      <PlusIcon v-else class="h-4 w-4" stroke-width="2.5" aria-hidden="true" />
      {{ t(inCompare ? 'models_ranking.detail.compare_remove' : 'models_ranking.detail.compare_add') }}
    </button>
    <div class="grid gap-2 md:flex md:items-center md:justify-between md:gap-4">
      <p class="text-[13px] leading-snug text-gray-600 dark:text-gray-400">{{ t('models_ranking.detail.model_page_more') }}</p>
      <a :href="href" class="btn-3d cta-shadow-sm inline-flex min-h-11 flex-none items-center justify-center gap-2 whitespace-nowrap rounded-xl border-2 border-gray-900 bg-green-600 px-4 font-bold text-white hover:bg-green-700 dark:border-gray-200" @click="analytics.track('ranking_model_page')">
        {{ t('models_ranking.detail.model_page', { name: m.modelDisplayName }) }}
        <ArrowRightIcon class="h-5 w-5" aria-hidden="true" />
      </a>
    </div>
    <!-- The row's photo is CC licensed: the credit links to the source -->
    <p v-if="credit" class="-mt-3 text-[11px] text-gray-500 dark:text-gray-400">
      <a :href="credit.sourceUrl" target="_blank" rel="noopener" class="hover:underline">{{ t('models_ranking.detail.photo_credit', { author: credit.author, license: credit.license }) }}</a>
    </p>
  </div>
</template>

<script setup lang="ts">
import { computed, type Component } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowRightIcon, BanknotesIcon, BoltIcon, CheckIcon, InformationCircleIcon, LockClosedIcon, MapIcon, PlusIcon } from '@heroicons/vue/24/outline'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import { useRankedModelText } from '../../composables/useRankedModelText'
import { officialModelImageAttribution } from '../../config/modelImages'
import { convertCostPerDistance } from '../../utils/unitConversions'
import { chargeRhythmText, type RhythmFormat } from '../../utils/chargeRhythm'
import { analytics } from '../../services/analytics'
import type { RankedModel } from '../../composables/useModelRanking'
import type { FuelKind } from '../../utils/costMix'

const props = defineProps<{
  item: RankedModel
  /** Link to the model detail page */
  href: string
  /** Electricity price in EUR/kWh the costs are based on: the reader's mix of home and public */
  price: number
  longestTripKm: number | null
  fuel: FuelKind
  /** Guest: the answers wait behind the mail gate */
  locked?: boolean
  inCompare?: boolean
  canAddCompare?: boolean
}>()

const emit = defineEmits<{ unlock: []; assumptions: []; compare: [] }>()

const { t } = useI18n()
const { formatNumber, formatDecimal, formatConsumption, consumptionUnitLabel, convertConsumption, distanceUnitLabel, formatCostPerKwh, formatCurrency, unitSystem } = useLocaleFormat()
const {
  m, fmtKmUnit, duration, tripParams, fuelLabel, costLabel, typicalSpan, hasBatterySpan, rangeValue,
  driversLabel, signedPct, displayWltpDeviation,
} = useRankedModelText(() => props.item, () => props.longestTripKm, () => props.fuel)

const credit = computed(() => officialModelImageAttribution(m.value.model))
const COLS: Record<number, string> = { 1: 'md:grid-cols-1', 2: 'md:grid-cols-2', 3: 'md:grid-cols-3' }
const cap = (s: string) => s.charAt(0).toLocaleUpperCase() + s.slice(1)

// ── 1. The reader's answers ────────────────────────────────────────────────
interface Answer { key: string; icon: Component; tone: string; label: string; value?: string; sub?: string[]; locked?: boolean }
const rhythmFormat: RhythmFormat = { t: (k, p, c) => t(k, p ?? {}, c as number), formatDecimal }
const answers = computed<Answer[]>(() => {
  const n = props.item.needs
  if (!n) return []
  const out: Answer[] = []
  const lock = props.locked
  if (n.commute) {
    const c = n.commute
    // one statement at the winter figure: it holds the whole year
    const charge = chargeRhythmText(c.winter, c.homeCharging, rhythmFormat)
    out.push({
      key: 'charge', icon: BoltIcon, tone: 'bg-green-50 text-green-700 dark:bg-green-900/40 dark:text-green-300',
      label: t(c.homeCharging ? 'models_ranking.detail.charge_label' : 'models_ranking.detail.charge_label_public'),
      value: cap(charge),
      sub: [],
      locked: lock,
    })
  }
  if (n.trip && tripParams.value) {
    out.push({
      key: 'trip', icon: MapIcon, tone: 'bg-amber-50 text-amber-700 dark:bg-amber-900/40 dark:text-amber-300',
      label: t('models_ranking.detail.trip_label', tripParams.value),
      value: cap(n.trip.stops === 0 ? t('models_ranking.row.stops_none') : t('models_ranking.row.stops_word', { count: formatNumber(n.trip.stops) }, n.trip.stops)),
      sub: n.trip.totalMinutes != null ? [t('personal_range.drive_time', { duration: duration(n.trip.totalMinutes) })] : [],
      locked: lock,
    })
  }
  const cost = props.item.monthlyCostEur
  if (cost != null && props.item.monthlyKm != null) {
    const year = props.item.savingsPerYearEur
    out.push({
      key: 'cost', icon: BanknotesIcon, tone: 'bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-300',
      label: t('models_ranking.detail.monthly'),
      value: formatCurrency(Math.round(cost), { decimals: 0 }),
      sub: [
        t('models_ranking.detail.monthly_hint', { km: fmtKmUnit(Math.round(props.item.monthlyKm / 10) * 10) }),
        ...(year != null ? [t(year >= 0 ? 'models_ranking.detail.year_cheaper' : 'models_ranking.detail.year_dearer', {
          amount: formatCurrency(Math.abs(year), { decimals: 0 }), fuel: fuelLabel.value,
        })] : []),
      ],
      locked: lock,
    })
  }
  return out
})
/** "Deine Antworten für den VW ID.3: wie oft du laden musst, wie viele Stopps auf 400 km und was es im Monat kostet." */
const gateText = computed(() => {
  const parts = answers.value.map(a => a.key === 'trip' && tripParams.value
    ? t('models_ranking.detail.gate_trip', tripParams.value)
    : t(`models_ranking.detail.gate_${a.key}`))
  const list = parts.length > 1 ? `${parts.slice(0, -1).join(', ')}${t('models_ranking.detail.gate_and')}${parts[parts.length - 1]}` : parts[0]
  return t('models_ranking.detail.gate_lead', { model: m.value.modelDisplayName, parts: list })
})

// ── 2. The data sheet ──────────────────────────────────────────────────────
interface Row { key?: string; label: string; value: string; hints: string[]; testid?: string }
const groups = computed(() => {
  const x = m.value
  const lo = x.minRealConsumptionKwhPer100km
  const hi = x.maxRealConsumptionKwhPer100km
  const band = lo != null && hi != null ? [convertConsumption(lo), convertConsumption(hi)].sort((a, b) => a - b) : null
  const consumption: Row[] = [
    {
      label: t('models_ranking.detail.real'),
      value: formatConsumption(x.avgConsumptionKwhPer100km),
      hints: [band ? t('models_ranking.detail.variant_range', { min: formatDecimal(band[0], 1), max: formatDecimal(band[1], 1) }) : t('models_ranking.detail.variant_range_open')],
    },
    {
      label: t('models_ranking.detail.wltp'),
      value: formatConsumption(props.item.wltpKwhPer100km),
      hints: [displayWltpDeviation.value != null ? t('models_ranking.detail.wltp_gap', { gap: signedPct(displayWltpDeviation.value) }) : t('models_ranking.row.no_value')],
    },
  ]
  // Range: the figure of the column (100 to 10 % at the drivers' mean); the best variant's own range only as an addition
  const rangeHints = typicalSpan.value
    ? [t(hasBatterySpan.value ? 'models_ranking.detail.range_hint_span' : 'models_ranking.detail.range_hint'),
       ...(x.realRangeKm != null ? [t('models_ranking.detail.range_variant', { value: fmtKmUnit(x.realRangeKm) })] : [])]
    : [t(x.realRangeKm != null ? 'models_ranking.detail.range_variant_only' : 'models_ranking.detail.range_missing')]
  const range: Row[] = [{
    label: t('models_ranking.detail.range'),
    value: rangeValue.value === '–' ? '–' : `${rangeValue.value} ${distanceUnitLabel()}`,
    hints: rangeHints, testid: 'detail-range',
  }]
  const savings = props.item.savingsPer100kmEur
  const cost: Row[] = [
    { key: 'cost', label: t('models_ranking.detail.cost', { unit: distanceUnitLabel() }), value: costLabel.value, hints: [t('models_ranking.detail.cost_hint', { price: formatCostPerKwh(props.price) })] },
    ...(savings != null ? [{
      label: t('models_ranking.detail.savings', { fuel: fuelLabel.value, liters: props.item.combustionLitersPer100km != null ? formatDecimal(props.item.combustionLitersPer100km, 1) : '', unit: distanceUnitLabel() }),
      value: t(savings >= 0 ? 'models_ranking.detail.savings_cheaper' : 'models_ranking.detail.savings_dearer', {
        amount: formatCurrency(Math.abs(convertCostPerDistance(savings, unitSystem.value))),
      }),
      hints: [],
    }] : []),
    { label: t('models_ranking.detail.charge_price'), value: x.avgCostPerKwh != null ? formatCostPerKwh(x.avgCostPerKwh) : '–', hints: [t('models_ranking.detail.charge_price_hint')] },
  ]
  const data: Row[] = [{
    label: t('models_list.card.charging_sessions'),
    value: formatNumber(x.logCount),
    hints: driversLabel.value && x.carCount != null
      ? [t('models_ranking.detail.data_hint', { drivers: driversLabel.value, cars: t('models_ranking.detail.cars_plural', { count: formatNumber(x.carCount) }, x.carCount) })]
      : [],
  }]
  return [
    { key: 'consumption', title: t('models_ranking.detail.group_consumption'), rows: consumption },
    { key: 'range', title: t('models_ranking.detail.group_range'), rows: range },
    { key: 'cost', title: t('models_ranking.detail.group_cost'), rows: cost },
    { key: 'data', title: t('models_ranking.detail.data'), rows: data },
  ]
})

// Largest battery, from a full battery on the motorway: without a stop, then one and two
const ladder = computed(() => {
  const l = props.item.ladder
  if (!l) return []
  return [
    { label: t('models_ranking.row.stops_none'), value: fmtKmUnit(l.zeroStopKm) },
    { label: t('models_ranking.row.ladder_stops', { count: formatNumber(1) }, 1), value: fmtKmUnit(l.oneStopKm) },
    { label: t('models_ranking.row.ladder_stops', { count: formatNumber(2) }, 2), value: fmtKmUnit(l.twoStopsKm) },
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
  return rows.map(r => ({ ...r, width: (convertConsumption(r.kwh) / max) * 100, text: formatConsumption(r.kwh, { showUnit: false }) }))
})
</script>
