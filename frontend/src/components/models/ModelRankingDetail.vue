<template>
  <div class="grid gap-5 border-t border-gray-200 px-4 pb-5 pt-4 md:px-7 dark:border-gray-800">
    <div class="grid gap-5" :class="answers.length ? 'lg:grid-cols-2 lg:gap-8' : ''">
      <!-- 1. The reader's answers, signed in only: guests have the one gate in chapter 1 -->
      <section v-if="answers.length" class="grid content-start gap-2.5" :aria-label="t('models_ranking.detail.for_you')" data-testid="for-you">
        <h3 :class="HEAD">{{ t('models_ranking.detail.for_you') }}</h3>
        <dl class="grid gap-2 sm:grid-cols-3">
          <div v-for="a in answers" :key="a.key" class="grid content-start gap-0.5 rounded-xl border border-gray-200 bg-white px-3.5 py-3 dark:border-gray-700 dark:bg-gray-900" data-testid="for-you-answer">
            <dt class="text-[13px] text-gray-600 dark:text-gray-400">{{ a.label }}</dt>
            <dd class="text-[20px] font-extrabold leading-tight tracking-tight tabular-nums text-gray-900 dark:text-gray-100">{{ a.value }}</dd>
            <dd v-for="line in a.sub" :key="line" class="text-[12.5px] leading-snug text-gray-500 dark:text-gray-400">{{ line }}</dd>
          </div>
        </dl>
        <!-- For the expert: what the closed row leaves out, one quiet line under the tiles -->
        <p v-if="expertLine" :class="EXPERT" data-testid="detail-expert">{{ expertLine }}</p>
      </section>

      <!-- 2. What the drivers measure, free for everyone, as one paragraph -->
      <section class="grid content-start gap-2.5" :aria-label="t('models_ranking.detail.measured')">
        <h3 :class="HEAD">{{ t('models_ranking.detail.measured') }}</h3>
        <p v-if="item.singleDriver" class="flex items-start gap-1.5 rounded-xl bg-amber-50 px-3 py-2 text-[13px] text-amber-900 dark:bg-amber-900/30 dark:text-amber-200" data-testid="single-driver-note">
          <InformationCircleIcon class="mt-px h-4 w-4 flex-none" aria-hidden="true" />{{ t('models_ranking.row.single_driver') }}
        </p>
        <p class="text-[13.5px] leading-relaxed text-gray-700 dark:text-gray-300" data-testid="detail-measured">{{ measured }}</p>
        <!-- Without tiles the expert line follows the paragraph -->
        <p v-if="expertLine && !answers.length" :class="EXPERT" data-testid="detail-expert">{{ expertLine }}</p>
      </section>
    </div>

    <!-- 3. One way on: the model page. The phone compares from here. -->
    <div class="grid gap-2.5 sm:flex sm:flex-wrap sm:items-center sm:gap-3">
      <a :href="href" class="btn-3d cta-shadow-sm inline-flex min-h-11 items-center justify-center gap-2 rounded-xl border-2 border-gray-900 bg-gray-900 px-4 font-bold text-white hover:bg-gray-800 dark:border-gray-200 dark:bg-gray-100 dark:text-gray-900" @click="analytics.track('ranking_model_page')">
        {{ t('models_ranking.detail.model_page', { name: m.modelDisplayName }) }}
        <ArrowRightIcon class="h-5 w-5" aria-hidden="true" />
      </a>
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
      <p class="text-[12.5px] leading-snug text-gray-500 sm:flex-1 dark:text-gray-400">{{ t('models_ranking.detail.model_page_more') }}</p>
    </div>
    <!-- The row's photo is CC licensed: the credit links to the source -->
    <p v-if="credit" class="-mt-3 text-[11px] text-gray-500 dark:text-gray-400">
      <a :href="credit.sourceUrl" target="_blank" rel="noopener" class="hover:underline">{{ t('models_ranking.detail.photo_credit', { author: credit.author, license: credit.license }) }}</a>
    </p>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowRightIcon, CheckIcon, InformationCircleIcon, PlusIcon } from '@heroicons/vue/24/outline'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import { useRankedModelText } from '../../composables/useRankedModelText'
import { officialModelImageAttribution } from '../../config/modelImages'
import { chargeRhythmText, type RhythmFormat } from '../../utils/chargeRhythm'
import { analytics } from '../../services/analytics'
import type { RankedModel } from '../../composables/useModelRanking'
import type { FuelKind } from '../../utils/costMix'

const props = defineProps<{
  item: RankedModel
  /** Link to the model detail page */
  href: string
  longestTripKm: number | null
  fuel: FuelKind
  /** Guest: no answers here, the one gate sits in chapter 1 */
  locked?: boolean
  inCompare?: boolean
  canAddCompare?: boolean
}>()

const emit = defineEmits<{ compare: [] }>()

const HEAD = 'text-[12px] font-semibold uppercase tracking-[0.08em] text-gray-500 dark:text-gray-400'
const EXPERT = 'text-[12.5px] leading-snug text-gray-500 dark:text-gray-400'

const { t } = useI18n()
const { formatNumber, formatDecimal, formatConsumption, formatCostPerKwh, formatCurrency, distanceUnitLabel } = useLocaleFormat()
const { m, fmtKm, fmtKmUnit, duration, tripParams, fuelLabel, rangeValue, signedPct } =
  useRankedModelText(() => props.item, () => props.longestTripKm, () => props.fuel)

const credit = computed(() => officialModelImageAttribution(m.value.model))
const cap = (s: string) => s.charAt(0).toLocaleUpperCase() + s.slice(1)

// ── 1. The reader's answers ────────────────────────────────────────────────
interface Answer { key: string; label: string; value: string; sub: string[] }
const rhythmFormat: RhythmFormat = { t: (k, p, c) => t(k, p ?? {}, c as number), formatDecimal }
const answers = computed<Answer[]>(() => {
  const n = props.item.needs
  if (!n || props.locked) return []
  const out: Answer[] = []
  if (n.commute) {
    const c = n.commute
    // one statement at the winter figure: it holds the whole year
    out.push({
      key: 'charge',
      label: t(c.homeCharging ? 'models_ranking.detail.charge_label' : 'models_ranking.detail.charge_label_public'),
      value: cap(chargeRhythmText(c.winter, c.homeCharging, rhythmFormat)),
      sub: [],
    })
  }
  if (n.trip && tripParams.value) {
    out.push({
      key: 'trip',
      label: t('models_ranking.detail.trip_label', tripParams.value),
      value: cap(n.trip.stops === 0 ? t('models_ranking.row.stops_none') : t('models_ranking.row.stops_word', { count: formatNumber(n.trip.stops) }, n.trip.stops)),
      sub: n.trip.totalMinutes != null ? [t('personal_range.drive_time', { duration: duration(n.trip.totalMinutes) })] : [],
    })
  }
  const cost = props.item.monthlyCostEur
  if (cost != null && props.item.monthlyKm != null) {
    const year = props.item.savingsPerYearEur
    out.push({
      key: 'cost',
      label: t('models_ranking.detail.monthly'),
      value: formatCurrency(Math.round(cost), { decimals: 0 }),
      sub: [
        t('models_ranking.detail.monthly_hint', { km: fmtKmUnit(Math.round(props.item.monthlyKm / 10) * 10) }),
        ...(year != null ? [t(year >= 0 ? 'models_ranking.detail.year_cheaper' : 'models_ranking.detail.year_dearer', {
          amount: formatCurrency(Math.abs(year), { decimals: 0 }), fuel: fuelLabel.value,
        })] : []),
      ],
    })
  }
  return out
})

// ── 2. What the drivers measure ────────────────────────────────────────────
/** "Verbrauch 20,0 kWh/100km (Sommer 19,0, Winter 22,0), WLTP 14,6 kWh/100km · Reichweite 343 km, im Winter 200 bis 307 km · Ø Ladepreis ... · 6.627 Ladevorgänge von 43 Autos" */
const measured = computed(() => {
  const x = m.value
  const summer = x.summerConsumptionKwhPer100km
  const winter = x.winterConsumptionKwhPer100km
  const seasons = summer != null && winter != null
    ? ` ${t('models_ranking.detail.measured_seasons', { summer: formatConsumption(summer, { showUnit: false }), winter: formatConsumption(winter, { showUnit: false }) })}`
    : ''
  const consumption = [
    t('models_ranking.detail.measured_consumption', { value: formatConsumption(x.avgConsumptionKwhPer100km) }) + seasons,
    ...(props.item.wltpKwhPer100km != null ? [t('models_ranking.detail.measured_wltp', { value: formatConsumption(props.item.wltpKwhPer100km) })] : []),
  ].join(', ')
  const parts = [consumption]
  const unit = distanceUnitLabel()
  if (rangeValue.value !== '–') {
    const lo = x.winterRangeMinKm
    const hi = x.winterRangeMaxKm
    const winterRange = lo != null && hi != null
      ? `, ${t('models_ranking.detail.measured_winter_range', {
          value: `${fmtKm(lo) === fmtKm(hi) ? fmtKm(hi) : t('models_ranking.row.span', { min: fmtKm(lo), max: fmtKm(hi) })} ${unit}`,
        })}`
      : ''
    parts.push(t('models_ranking.detail.measured_range', { value: `${rangeValue.value} ${unit}` }) + winterRange)
  }
  if (x.avgCostPerKwh != null) parts.push(t('models_ranking.detail.measured_price', { price: formatCostPerKwh(x.avgCostPerKwh) }))
  const logs = t('models_ranking.row.logs_plural', { count: formatNumber(x.logCount) }, x.logCount)
  parts.push(x.carCount != null
    ? t('models_ranking.detail.measured_data', { logs, cars: t('models_ranking.detail.cars_plural', { count: formatNumber(x.carCount) }, x.carCount) })
    : logs)
  return parts.join(' · ')
})

/** "Autobahn 2 Stopps bis 790 km · Winter +10 % Verbrauch · 23 % über WLTP · Akku 75 und 82 kWh"; null without any of them */
const expertLine = computed(() => {
  const x = m.value
  const parts: string[] = []
  if (props.item.ladder) parts.push(t('models_ranking.detail.kenner_ladder', { value: fmtKmUnit(props.item.ladder.twoStopsKm) }))
  if (props.item.winterSurchargePct != null) parts.push(t('models_ranking.detail.kenner_winter', { pct: signedPct(props.item.winterSurchargePct) }))
  // consumption against WLTP, in kWh space: "über" means more energy than the rating
  const gap = props.item.wltpDeviationPct
  if (gap != null && Math.round(gap) !== 0) {
    parts.push(t(gap > 0 ? 'models_ranking.detail.kenner_wltp_above' : 'models_ranking.detail.kenner_wltp_below', { pct: `${formatDecimal(Math.abs(gap), 0)} %` }))
  }
  const lo = x.minNetCapacityKwh
  const hi = x.maxNetCapacityKwh
  if (lo != null && hi != null) {
    parts.push(Math.round(lo) === Math.round(hi)
      ? t('models_ranking.detail.kenner_battery', { value: formatDecimal(hi, 0) })
      : t('models_ranking.detail.kenner_battery_span', { min: formatDecimal(lo, 0), max: formatDecimal(hi, 0) }))
  }
  return parts.length ? parts.join(' · ') : null
})
</script>
