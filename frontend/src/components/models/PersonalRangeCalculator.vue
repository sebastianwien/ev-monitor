<template>
  <section class="mt-6 pt-5 border-t border-gray-100 dark:border-gray-700" aria-labelledby="pr-title">
    <div class="text-center mb-4">
      <h2 id="pr-title" class="text-xl font-bold text-gray-800 dark:text-gray-200 flex items-center justify-center gap-1.5">
        <UserCircleIcon class="h-6 w-6 text-green-600 dark:text-green-400" />
        {{ t('personal_range.title') }}
      </h2>
      <p class="text-sm text-gray-500 dark:text-gray-400 mt-1">{{ t('personal_range.hint', { model: modelName }) }}</p>
    </div>

    <form class="space-y-4" novalidate @submit.prevent="onCalculate" @input="markStarted">
      <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <div>
          <label for="pr-commute" class="block text-sm font-semibold text-gray-700 dark:text-gray-300 mb-1">
            {{ t('personal_range.commute_label') }}
          </label>
          <div class="flex items-center gap-2">
            <input id="pr-commute" v-model.number="commuteLocal" type="number" inputmode="numeric" min="0" :max="toLocal(MAX_COMMUTE_KM)"
                   class="w-full rounded-lg border-2 border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 px-3 py-2.5 text-base tabular-nums text-gray-900 dark:text-gray-100 focus:border-green-600 focus:outline-none" />
            <span class="text-sm font-semibold text-gray-600 dark:text-gray-300">{{ distanceUnitLabel() }}</span>
          </div>
        </div>

        <div>
          <label for="pr-long-trip" class="block text-sm font-semibold text-gray-700 dark:text-gray-300 mb-1">
            {{ t('personal_range.long_trip_label') }}
          </label>
          <div class="flex items-center gap-2">
            <input id="pr-long-trip" v-model.number="longTripLocal" type="number" inputmode="numeric" :min="toLocal(MIN_LONG_TRIP_KM)" :max="toLocal(MAX_LONG_TRIP_KM)"
                   class="w-full rounded-lg border-2 border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 px-3 py-2.5 text-base tabular-nums text-gray-900 dark:text-gray-100 focus:border-green-600 focus:outline-none" />
            <span class="text-sm font-semibold text-gray-600 dark:text-gray-300">{{ distanceUnitLabel() }}</span>
          </div>
        </div>
      </div>

      <div>
        <label for="pr-highway" class="flex justify-between text-sm font-semibold text-gray-700 dark:text-gray-300 mb-1">
          <span>{{ t('personal_range.highway_label') }}</span>
          <span class="tabular-nums">{{ highwaySharePercent }} %</span>
        </label>
        <input id="pr-highway" v-model.number="highwaySharePercent" type="range" min="0" max="100" step="10"
               :aria-valuetext="`${highwaySharePercent} %`" :style="{ '--pct': highwaySharePercent + '%' }" class="ev-slider w-full" />
      </div>

      <fieldset>
        <legend class="text-sm font-semibold text-gray-700 dark:text-gray-300 mb-1">{{ t('personal_range.home_label') }}</legend>
        <div class="grid grid-cols-2 gap-2">
          <button v-for="option in HOME_OPTIONS" :key="String(option)" type="button"
                  :aria-pressed="homeCharging === option" @click="homeCharging = option; markStarted()"
                  class="py-2 rounded-lg text-sm font-semibold border-2 transition-colors"
                  :class="homeCharging === option
                    ? 'bg-green-600 border-gray-800 dark:border-gray-200 text-white'
                    : 'bg-white dark:bg-gray-800 border-gray-200 dark:border-gray-600 text-gray-700 dark:text-gray-200'">
            {{ option ? t('personal_range.yes') : t('personal_range.no') }}
          </button>
        </div>
      </fieldset>

      <button type="submit" data-testid="personal-range-calculate" :disabled="busy"
              class="btn-3d w-full rounded-lg border-2 border-gray-800 dark:border-gray-200 bg-green-600 py-3 font-bold text-white disabled:opacity-60">
        {{ busy ? t('personal_range.calculating') : t('personal_range.calculate') }}
      </button>
    </form>

    <InlineGate v-if="gateOpen" class="mt-4" :lead="gateLead" :detail="t('personal_range.gate_why')" @authenticated="onAuthenticated" />

    <p v-if="error" role="alert" class="mt-3 text-sm font-semibold text-red-700 dark:text-red-400">{{ error }}</p>

    <div v-if="result" data-testid="personal-range-result" class="mt-5" aria-live="polite">
      <h3 class="text-base font-bold text-gray-800 dark:text-gray-200 mb-3">{{ t('personal_range.result_title', { model: modelName }) }}</h3>
      <div class="grid grid-cols-2 gap-2">
        <div v-for="tile in tiles" :key="tile.label" class="rounded-xl border-2 border-gray-800 dark:border-gray-200 bg-gray-50 dark:bg-gray-800/40 p-3">
          <div class="text-xs font-semibold text-gray-500 dark:text-gray-400">{{ tile.label }}</div>
          <div class="text-2xl font-extrabold tabular-nums text-gray-900 dark:text-gray-100 leading-tight">{{ tile.value }}</div>
          <div class="text-xs text-gray-600 dark:text-gray-300 mt-0.5">{{ tile.detail }}</div>
        </div>
      </div>

      <p class="mt-3 inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-semibold"
         :class="result.dataLevel === 'MEASURED' ? 'bg-green-100 text-green-800 dark:bg-green-900/40 dark:text-green-300' : 'bg-amber-100 text-amber-800 dark:bg-amber-900/40 dark:text-amber-300'">
        {{ t(`personal_range.data_level_${result.dataLevel}`) }}
      </p>

      <h4 class="mt-4 text-sm font-bold text-gray-700 dark:text-gray-300">{{ t('personal_range.assumptions_title') }}</h4>
      <ul class="mt-1 space-y-1">
        <li v-for="a in result.assumptions" :key="a.key" data-testid="assumption" class="flex items-start gap-1.5 text-sm text-gray-700 dark:text-gray-300">
          <CheckCircleIcon v-if="a.measured" class="h-4 w-4 shrink-0 mt-0.5 text-green-600 dark:text-green-400" />
          <QuestionMarkCircleIcon v-else class="h-4 w-4 shrink-0 mt-0.5 text-amber-600 dark:text-amber-400" />
          <span>{{ assumptionText(a) }} <span class="text-gray-500 dark:text-gray-400">({{ sourceText(a) }})</span></span>
        </li>
      </ul>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { CheckCircleIcon, QuestionMarkCircleIcon, UserCircleIcon } from '@heroicons/vue/24/outline'
import InlineGate from '../auth/InlineGate.vue'
import { useAuthStore } from '../../stores/auth'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import { analytics } from '../../services/analytics'
import { KM_PER_MILE } from '../../utils/unitConversions'
import {
  personalRangeService,
  type PersonalRangeAssumption,
  type PersonalRangeInputs,
  type PersonalRangeResult,
} from '../../api/personalRangeService'

/**
 * Personal range result for one model: four inputs, result behind the gate for guests.
 * Inputs survive the gate (and a Google redirect) in sessionStorage; logged-in users get
 * their stored profile pre-filled.
 */
const props = defineProps<{ brand: string; model: string; modelName: string; logCount: number; carCount: number }>()

const { t } = useI18n()
const authStore = useAuthStore()
const { formatNumber, formatDistance, distanceUnitLabel, isImperial, formatCurrency, formatConsumption, formatCostPerKwh, formatDecimal } = useLocaleFormat()

/** Below this many cars the gate does not advertise measured data (same as the backend highway threshold). */
const MIN_MEASURED_CARS = 5
const MAX_COMMUTE_KM = 500
const MIN_LONG_TRIP_KM = 50
const MAX_LONG_TRIP_KM = 2000
const HOME_OPTIONS = [true, false] as const
/** Pending inputs plus "calculate after login" flag; read once on mount. */
const STORAGE_KEY = 'personalRange:inputs'

const commuteLocal = ref(toLocal(40))
const longTripLocal = ref(toLocal(500))
const highwaySharePercent = ref(30)
const homeCharging = ref(true)
const gateOpen = ref(false)
const gateLead = computed(() => props.carCount >= MIN_MEASURED_CARS
  ? t('personal_range.gate_lead_measured', { model: props.modelName, logs: formatNumber(props.logCount), cars: formatNumber(props.carCount) })
  : t('personal_range.gate_lead_thin', { model: props.modelName }))

/** gemessen, geschätzt, or the measured share of a blend */
function sourceText(a: PersonalRangeAssumption): string {
  if (a.measured) return t('personal_range.measured')
  if (a.measuredPercent && a.measuredPercent > 0) return t('personal_range.partly_measured', { percent: a.measuredPercent })
  return t('personal_range.estimated')
}
const busy = ref(false)
const error = ref('')
const result = ref<PersonalRangeResult | null>(null)
let started = false

function toLocal(km: number): number {
  return isImperial.value ? Math.round(km / KM_PER_MILE) : km
}

function toKm(local: number): number {
  return isImperial.value ? Math.round(local * KM_PER_MILE) : local
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, Number.isFinite(value) ? Math.round(value) : min))
}

const inputs = computed<PersonalRangeInputs>(() => ({
  commuteKmPerDay: clamp(toKm(commuteLocal.value), 0, MAX_COMMUTE_KM),
  highwaySharePercent: clamp(highwaySharePercent.value, 0, 100),
  homeCharging: homeCharging.value,
  longTripKm: clamp(toKm(longTripLocal.value), MIN_LONG_TRIP_KM, MAX_LONG_TRIP_KM),
}))

function applyInputs(saved: PersonalRangeInputs) {
  commuteLocal.value = toLocal(saved.commuteKmPerDay)
  longTripLocal.value = toLocal(saved.longTripKm)
  highwaySharePercent.value = saved.highwaySharePercent
  homeCharging.value = saved.homeCharging
}

function markStarted() {
  if (started) return
  started = true
  analytics.track('personal_range_started', { model: props.modelName })
}

function rememberPending() {
  try { sessionStorage.setItem(STORAGE_KEY, JSON.stringify(inputs.value)) } catch { /* storage blocked */ }
}

function takePending(): PersonalRangeInputs | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    sessionStorage.removeItem(STORAGE_KEY)
    return raw ? JSON.parse(raw) as PersonalRangeInputs : null
  } catch {
    return null
  }
}

async function onCalculate() {
  markStarted()
  if (!authStore.token) {
    gateOpen.value = true
    rememberPending()
    analytics.track('personal_range_gate_shown', { model: props.modelName })
    return
  }
  await calculate()
}

async function onAuthenticated() {
  gateOpen.value = false
  takePending()
  await calculate()
}

async function calculate() {
  busy.value = true
  error.value = ''
  try {
    result.value = await personalRangeService.calculate(props.brand, props.model, inputs.value)
    analytics.track('personal_range_result', { model: props.modelName, data_level: result.value.dataLevel })
  } catch {
    error.value = t('personal_range.error')
  } finally {
    busy.value = false
  }
}

onMounted(async () => {
  if (!authStore.token) return
  const pending = takePending()
  if (pending) {
    // Back from a login redirect: the visitor already asked for the result
    applyInputs(pending)
    await calculate()
    return
  }
  try {
    const profile = await personalRangeService.getProfile()
    if (profile) applyInputs(profile)
  } catch { /* defaults stay */ }
})

const tiles = computed(() => {
  const r = result.value
  if (!r) return []
  const longTrip = formatDistance(inputs.value.longTripKm)
  return [
    { label: t('personal_range.winter_range'), value: formatDistance(r.winterRangeKm),
      detail: t('personal_range.summer_range', { value: formatDistance(r.summerRangeKm) }) },
    { label: t('personal_range.stops', { distance: longTrip }), value: String(r.longTripStops),
      detail: r.longTripStops === 0 ? t('personal_range.no_stop') : t('personal_range.stops_minutes', { minutes: r.longTripChargingMinutes }) },
    { label: t('personal_range.charges_week'), value: compact(r.chargesPerWeek),
      detail: inputs.value.homeCharging ? t('personal_range.charges_home') : t('personal_range.charges_public') },
    { label: t('personal_range.cost_month'), value: formatCurrency(r.monthlyCostEur, { decimals: 0 }),
      detail: t('personal_range.cost_basis') },
  ]
})

/** Locale number without a trailing ",0". */
function compact(value: number): string {
  return formatDecimal(value, Number.isInteger(value) ? 0 : 1)
}

function assumptionText(a: PersonalRangeAssumption): string {
  const value = a.key.endsWith('_CONSUMPTION') ? formatConsumption(a.value)
    : a.key === 'BATTERY' ? `${compact(a.value)} kWh`
    : a.key === 'CHARGING_POWER' ? `${compact(a.value)} kW`
    : formatCostPerKwh(a.value)
  return t(`personal_range.assumption_${a.key}`, { value })
}
</script>
