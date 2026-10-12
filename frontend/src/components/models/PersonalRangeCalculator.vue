<template>
  <section class="grid gap-4 pt-9" aria-labelledby="pr-title">
    <div class="grid gap-1.5">
      <p class="text-[11.5px] font-semibold uppercase tracking-[0.08em] text-green-700 dark:text-green-400">{{ t('personal_range.eyebrow') }}</p>
      <h2 id="pr-title" class="max-w-[22ch] text-balance text-[clamp(24px,6vw,36px)] font-bold leading-[1.08] tracking-tight text-gray-900 dark:text-gray-100">
        {{ result ? t('personal_range.result_title', { model: modelName }) : t('personal_range.precise_title') }}
      </h2>
      <p v-if="!result" class="max-w-[58ch] text-gray-600 dark:text-gray-400" data-testid="personal-range-intro">
        {{ logCount > 0
          ? t('personal_range.precise_hint', { logs: formatNumber(logCount), cars: formatNumber(carCount) })
          : t('personal_range.precise_hint_none') }}
      </p>
      <!-- Why the mail: three short reasons, before anyone is asked for anything -->
      <ul v-if="!result" class="mt-1 grid gap-1.5">
        <li v-for="key in REASONS" :key="key" class="flex items-start gap-2 text-[15px] text-gray-700 dark:text-gray-300" data-testid="personal-range-reason">
          <CheckIcon class="mt-0.5 h-5 w-5 shrink-0 text-green-600 dark:text-green-400" aria-hidden="true" />
          <span>{{ t(`personal_range.${key}`) }}</span>
        </li>
      </ul>
    </div>

    <!-- Before the gate: four tiles with placeholders only. No figure of the result exists in the
         page until the signed-in request returns, so there is nothing to unblur in the console. -->
    <dl v-if="!result" class="grid grid-cols-2 gap-px overflow-hidden rounded-2xl border border-gray-200 bg-gray-200 md:grid-cols-4 dark:border-gray-800 dark:bg-gray-800" data-testid="personal-range-placeholder">
      <div v-for="tile in placeholderLabels" :key="tile.label" class="flex flex-col-reverse gap-1 bg-white px-3.5 py-3 dark:bg-gray-900">
        <dt class="text-[12.5px] text-gray-500 dark:text-gray-400">{{ tile.label }}<span v-if="tile.detail" class="block">{{ tile.detail }}</span></dt>
        <dd class="select-none text-[22px] font-semibold leading-tight tracking-tight text-gray-400 blur-[6px] dark:text-gray-500" aria-hidden="true">···</dd>
      </div>
    </dl>

    <button v-if="!result && !gateOpen" type="button" data-testid="personal-range-calculate" :disabled="busy"
            class="inline-flex min-h-11 w-full items-center justify-center rounded-xl bg-green-600 px-4 font-semibold text-white hover:bg-green-700 disabled:opacity-60 md:w-auto md:justify-self-start"
            @click="request">
      {{ busy ? t('personal_range.calculating') : t('model.story.band_cta') }}
    </button>
    <p v-if="!result && !gateOpen && openCount > 0" class="-mt-2 text-[13px] text-gray-500 dark:text-gray-400" data-testid="personal-range-open">
      {{ t('personal_range.open_questions', { n: openCount }, openCount) }}
    </p>

    <InlineGate v-if="gateOpen" :lead="gateLead" :detail="t('personal_range.gate_why')" :placement="`modell:${brand}/${model}`" @authenticated="onAuthenticated" />

    <p v-if="error" role="alert" class="text-sm font-semibold text-red-700 dark:text-red-400">{{ error }}</p>

    <div v-if="result" data-testid="personal-range-result" class="grid gap-4" aria-live="polite">
      <dl class="grid grid-cols-2 gap-px overflow-hidden rounded-2xl border border-gray-200 bg-gray-200 md:grid-cols-4 dark:border-gray-800 dark:bg-gray-800">
        <div v-for="tile in tiles" :key="tile.label" class="flex flex-col-reverse gap-1 bg-white px-3.5 py-3 dark:bg-gray-900">
          <dt class="text-[12.5px] text-gray-500 dark:text-gray-400">{{ tile.label }}<span class="block">{{ tile.detail }}</span></dt>
          <dd class="text-[22px] font-semibold leading-tight tracking-tight tabular-nums text-gray-900 dark:text-gray-100">{{ tile.value }}</dd>
        </div>
      </dl>

      <p class="inline-flex items-center gap-1.5 justify-self-start rounded-full px-2.5 py-1 text-xs font-semibold"
         :class="result.dataLevel === 'MEASURED' ? 'bg-green-100 text-green-800 dark:bg-green-900/40 dark:text-green-300' : 'bg-amber-100 text-amber-800 dark:bg-amber-900/40 dark:text-amber-300'">
        {{ t(`personal_range.data_level_${result.dataLevel}`) }}
      </p>

      <div>
        <h3 class="text-sm font-bold text-gray-700 dark:text-gray-300">{{ t('personal_range.assumptions_title') }}</h3>
        <ul class="mt-1 space-y-1">
          <li v-for="a in result.assumptions" :key="a.key" data-testid="assumption" class="flex items-start gap-1.5 text-sm text-gray-700 dark:text-gray-300">
            <CheckCircleIcon v-if="a.measured" class="mt-0.5 h-4 w-4 shrink-0 text-green-600 dark:text-green-400" />
            <QuestionMarkCircleIcon v-else class="mt-0.5 h-4 w-4 shrink-0 text-amber-600 dark:text-amber-400" />
            <span>{{ assumptionText(a) }} <span class="text-gray-500 dark:text-gray-400">({{ sourceText(a) }})</span></span>
          </li>
        </ul>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { CheckCircleIcon, CheckIcon, QuestionMarkCircleIcon } from '@heroicons/vue/24/outline'
import InlineGate from '../auth/InlineGate.vue'
import { useAuthStore } from '../../stores/auth'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import { analytics } from '../../services/analytics'
import {
  personalRangeService,
  type PersonalRangeAssumption,
  type PersonalRangeInputs,
  type PersonalRangeResult,
} from '../../api/personalRangeService'

/**
 * The exact result block at the end of the model story. The inputs come from the chapter chips
 * (props.inputs); this component owns the gate and the result. Guests see placeholder tiles and
 * the gate, signed-in visitors get the figures and the assumptions. Inputs survive the gate (and
 * a Google redirect) in sessionStorage; a stored profile is handed back to the page via `profile`
 * so the chips can follow it.
 */
const props = defineProps<{
  brand: string
  model: string
  modelName: string
  logCount: number
  carCount: number
  inputs: PersonalRangeInputs
  /** unanswered questions on the page; the result rests on answers only, never on start values */
  openCount?: number
}>()
const emit = defineEmits<{ profile: [inputs: PersonalRangeInputs]; incomplete: [] }>()
const openCount = computed(() => props.openCount ?? 0)

const { t } = useI18n()
const authStore = useAuthStore()
const { formatNumber, formatDistance, formatCurrency, formatConsumption, formatCostPerKwh, formatDecimal } = useLocaleFormat()

/** Below this many cars the gate does not advertise measured data (same as the backend highway threshold). */
const MIN_MEASURED_CARS = 5
const REASONS = ['reason_mail', 'reason_profile', 'reason_account'] as const
/** Pending inputs plus "calculate after login" flag; read once on mount. */
const STORAGE_KEY = 'personalRange:inputs'

const gateOpen = ref(false)
const busy = ref(false)
const error = ref('')
const result = ref<PersonalRangeResult | null>(null)

const gateLead = computed(() => props.carCount >= MIN_MEASURED_CARS
  ? t('personal_range.gate_lead_measured', { model: props.modelName, logs: formatNumber(props.logCount), cars: formatNumber(props.carCount) })
  : t('personal_range.gate_lead_thin', { model: props.modelName, cars: props.carCount }, props.carCount))

/** Same labels as the result tiles; summer and winter name the charges a week, the gate's selling point */
const placeholderLabels = computed(() => [
  { label: t('personal_range.summer_label'), detail: t('personal_range.charges_week_label') },
  { label: t('personal_range.winter_range'), detail: t('personal_range.charges_week_label') },
  { label: t('personal_range.stops', { distance: formatDistance(props.inputs.longTripKm) }), detail: null },
  { label: t('personal_range.cost_month'), detail: null },
])

/** Datenblatt for the battery; the user's own price; otherwise gemessen, geschätzt, or the measured share of a blend */
function sourceText(a: PersonalRangeAssumption): string {
  if (a.key === 'BATTERY') return a.measured ? t('personal_range.source_spec') : t('personal_range.source_spec_derived')
  if (a.key === 'OWN_PRICE') return t('personal_range.source_own')
  if (a.measured) return t('personal_range.measured')
  if (a.measuredPercent && a.measuredPercent > 0) return t('personal_range.partly_measured', { percent: a.measuredPercent })
  return t('personal_range.estimated')
}

function rememberPending() {
  try { sessionStorage.setItem(STORAGE_KEY, JSON.stringify(props.inputs)) } catch { /* storage blocked */ }
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

/** "Meine Zahlen": back to the open questions first, then the gate for guests, the result for signed-in visitors. */
async function request() {
  if (openCount.value > 0) {
    emit('incomplete')
    return
  }
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
  await calculate(true)
}

/** @param email true right after the gate: the result also goes out as a mail, as the gate promised */
async function calculate(email = false, inputs: PersonalRangeInputs = props.inputs) {
  busy.value = true
  error.value = ''
  try {
    result.value = await personalRangeService.calculate(props.brand, props.model, inputs, email)
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
    emit('profile', pending)
    await calculate(true, pending)
    return
  }
  try {
    const profile = await personalRangeService.getProfile()
    if (profile) emit('profile', profile)
  } catch { /* defaults stay */ }
})

const tiles = computed(() => {
  const r = result.value
  if (!r) return []
  const longTrip = formatDistance(props.inputs.longTripKm)
  return [
    { label: t('personal_range.summer_label'), value: formatDistance(r.summerRangeKm), detail: t('personal_range.charges_commute', { charges: compact(r.chargesPerWeekSummer) }) },
    { label: t('personal_range.winter_range'), value: formatDistance(r.winterRangeKm), detail: t('personal_range.charges_commute', { charges: compact(r.chargesPerWeekWinter) }) },
    { label: t('personal_range.stops', { distance: longTrip }), value: String(r.longTripStops),
      detail: r.longTripStops === 0 ? t('personal_range.no_stop') : t('personal_range.stops_minutes', { minutes: r.longTripChargingMinutes }) },
    { label: t('personal_range.cost_month'), value: formatCurrency(r.monthlyCostEur, { decimals: 0 }),
      detail: props.inputs.annualKm
        ? t('personal_range.cost_basis_annual', { annual: formatDistance(props.inputs.annualKm) })
        : t('personal_range.cost_basis_charges') },
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

defineExpose({ request })
</script>
