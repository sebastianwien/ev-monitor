<template>
  <section ref="root" id="ergebnis" aria-labelledby="pr-title" data-testid="personal-range"
           class="grid min-w-0 scroll-mt-28 content-start gap-5 border-y px-4 py-6 transition-colors duration-300 md:rounded-3xl md:border md:p-7 md:shadow-[0_1px_2px_rgba(0,0,0,0.04)]"
           :class="state === 'locked'
             ? 'border-gray-200 bg-white dark:border-gray-800 dark:bg-gray-900'
             : 'border-green-600/40 bg-green-50 dark:border-green-500/40 dark:bg-green-950/40'">

    <!-- Locked: the four questions, placeholders behind a lock. No figure of the result exists in
         the page until the signed-in request returns, so there is nothing to unblur in the console. -->
    <template v-if="state === 'locked'">
      <div class="grid gap-3">
        <p class="text-[11.5px] font-semibold uppercase tracking-[0.08em] text-green-700 dark:text-green-400">{{ t('personal_range.eyebrow') }}</p>
        <h2 id="pr-title" tabindex="-1" class="text-[21px] focus:outline-none font-bold tracking-tight text-gray-900 md:text-[24px] dark:text-gray-100">{{ t('personal_range.locked_title') }}</h2>
        <ol class="grid gap-2" data-testid="personal-range-questions">
          <li v-for="(q, i) in questions" :key="q" class="flex items-start gap-2.5 text-[15px] font-semibold leading-snug text-gray-900 dark:text-gray-100">
            <span class="mt-px grid h-5 w-5 flex-none place-items-center rounded-full bg-gray-900 text-[11px] font-bold text-white dark:bg-gray-100 dark:text-gray-900" aria-hidden="true">{{ i + 1 }}</span>
            <span>{{ q }}</span>
          </li>
        </ol>
      </div>

      <div class="relative overflow-hidden rounded-2xl border border-gray-200 dark:border-gray-800" data-testid="personal-range-placeholder">
        <dl class="grid select-none grid-cols-2 gap-px bg-gray-200 blur-[5px] dark:bg-gray-800" aria-hidden="true">
          <div v-for="label in shortLabels" :key="label" class="grid h-[76px] content-start gap-1 bg-white px-3 py-3 dark:bg-gray-900">
            <dt class="text-[12px] text-gray-500 dark:text-gray-400">{{ label }}</dt>
            <dd class="text-[22px] font-bold text-gray-300 dark:text-gray-600">···</dd>
          </div>
        </dl>
        <div class="absolute inset-0 grid place-content-center justify-items-center gap-2 bg-white/75 px-4 text-center dark:bg-gray-900/75">
          <span class="grid h-12 w-12 place-items-center rounded-full bg-gray-900 text-white dark:bg-gray-100 dark:text-gray-900">
            <LockClosedIcon class="h-6 w-6" aria-hidden="true" />
          </span>
          <p class="text-[16px] font-bold text-gray-900 dark:text-gray-100" data-testid="personal-range-open" aria-live="polite">
            {{ openCount > 0 ? t('personal_range.locked_open', { n: openCount }, openCount) : t('personal_range.locked_ready') }}
          </p>
          <p v-if="openCount > 0" class="text-[13px] text-gray-600 dark:text-gray-400">{{ t('personal_range.locked_open_hint') }}</p>
        </div>
      </div>

      <p class="text-[13px] leading-snug text-gray-600 dark:text-gray-400">
        {{ logCount === 0 ? t('personal_range.locked_basis_none')
          : carCount >= MIN_MEASURED_CARS ? t('personal_range.locked_basis', { logs: formatNumber(logCount) })
          : t('personal_range.locked_basis_thin', { logs: formatNumber(logCount), cars: formatNumber(carCount) }) }}
        {{ inputs.homeCharging ? t('personal_range.assume_home') : t('personal_range.assume_public') }}
      </p>
      <button type="button" data-testid="personal-range-calculate" :disabled="busy"
              class="inline-flex min-h-12 w-full items-center justify-center gap-2 rounded-xl bg-green-600 px-4 text-[16px] font-bold text-white transition-colors hover:bg-green-700 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-green-600 disabled:opacity-60"
              @click="request">
        <LockOpenIcon class="h-5 w-5" aria-hidden="true" />
        {{ busy ? t('personal_range.calculating') : ctaLabel }}
      </button>
    </template>

    <!-- Gate: the same box turns green, the lock opens, sign-in right here -->
    <template v-else-if="state === 'gate'">
      <div class="flex items-center gap-3">
        <span class="grid h-12 w-12 flex-none place-items-center rounded-full bg-green-600 text-white"><LockOpenIcon class="h-6 w-6" aria-hidden="true" /></span>
        <h2 id="pr-title" tabindex="-1" class="text-[19px] font-bold leading-tight tracking-tight text-gray-900 focus:outline-none dark:text-gray-100">{{ t('personal_range.gate_title') }}</h2>
      </div>
      <p class="text-[14px] leading-snug text-gray-700 dark:text-gray-300">{{ t('personal_range.gate_short') }}</p>
      <InlineGate plain :consent="t('personal_range.gate_privacy')" :placement="`modell:${brand}/${model}`" @authenticated="onAuthenticated" />
    </template>

    <!-- Result: four answers, then the two assumptions to play with -->
    <template v-else-if="result">
      <div class="flex items-center gap-3">
        <span class="grid h-12 w-12 flex-none place-items-center rounded-full bg-green-600 text-white"><LockOpenIcon class="h-6 w-6" aria-hidden="true" /></span>
        <h2 id="pr-title" tabindex="-1" class="text-[19px] font-bold leading-tight tracking-tight text-gray-900 focus:outline-none dark:text-gray-100">{{ t('personal_range.answers_title', { model: modelName }) }}</h2>
      </div>

      <p class="sr-only" role="status">{{ busy ? t('personal_range.calculating') : '' }}</p>
      <!-- One answer per row, as a sentence; the figure is the loud part -->
      <ul v-if="answer" class="divide-y divide-green-600/15 overflow-hidden rounded-2xl bg-white shadow-[0_1px_2px_rgba(0,0,0,0.05)] transition-opacity dark:divide-green-500/15 dark:bg-gray-900" :class="busy ? 'opacity-60' : ''" :aria-busy="busy" data-testid="personal-range-result">
        <li class="grid grid-cols-[36px_minmax(0,1fr)] gap-3 px-4 py-4" data-testid="answer-winter">
          <span class="grid h-9 w-9 place-items-center rounded-full bg-sky-50 text-sky-700 dark:bg-sky-900/40 dark:text-sky-300"><CloudIcon class="h-5 w-5" aria-hidden="true" /></span>
          <div class="grid gap-1">
            <i18n-t keypath="personal_range.winter_answer" tag="p" class="text-[17px] font-medium leading-snug text-gray-900 dark:text-gray-100">
              <template #winter><b class="whitespace-nowrap text-[24px] font-extrabold tracking-tight tabular-nums">{{ answer.winter }}</b></template>
              <template #summer><b class="font-bold">{{ answer.summer }}</b></template>
            </i18n-t>
            <!-- The window both figures rest on, as the gauge in the hero draws it -->
            <p class="flex items-center gap-1 text-[12.5px] font-semibold text-gray-500 dark:text-gray-400">
              <Battery100Icon class="h-4 w-4" aria-hidden="true" /><span>100 %</span>
              <ArrowRightIcon class="h-3 w-3" aria-hidden="true" />
              <Battery0Icon class="h-4 w-4" aria-hidden="true" /><span>10 %</span>
            </p>
          </div>
        </li>
        <li class="grid grid-cols-[36px_minmax(0,1fr)] gap-3 px-4 py-4" data-testid="answer-rhythm">
          <span class="grid h-9 w-9 place-items-center rounded-full bg-green-50 text-green-700 dark:bg-green-900/40 dark:text-green-300"><BoltIcon class="h-5 w-5" aria-hidden="true" /></span>
          <div class="grid gap-1">
            <i18n-t v-for="row in answer.rhythm" :key="row.key" :keypath="row.key" tag="p" class="text-[17px] font-medium leading-snug text-gray-900 dark:text-gray-100">
              <template #rhythm><b class="font-extrabold">{{ row.value }}</b></template>
            </i18n-t>
            <p class="text-[12.5px] text-gray-500 dark:text-gray-400">{{ answer.rhythmBasis }}</p>
          </div>
        </li>
        <li class="grid grid-cols-[36px_minmax(0,1fr)] gap-3 px-4 py-4" data-testid="answer-stops">
          <span class="grid h-9 w-9 place-items-center rounded-full bg-amber-50 text-amber-700 dark:bg-amber-900/40 dark:text-amber-300"><MapIcon class="h-5 w-5" aria-hidden="true" /></span>
          <div class="grid gap-1.5">
            <i18n-t keypath="personal_range.stops_answer" tag="p" class="text-[17px] font-medium leading-snug text-gray-900 dark:text-gray-100">
              <template #distance><span class="whitespace-nowrap">{{ answer.distance }}</span></template>
              <template #times><b class="text-[20px] font-extrabold tracking-tight">{{ answer.times }}</b></template>
            </i18n-t>
            <p class="flex flex-wrap gap-x-4 gap-y-1 text-[13px] text-gray-600 dark:text-gray-400">
              <span class="inline-flex items-center gap-1.5"><ClockIcon class="h-4 w-4 text-gray-400" aria-hidden="true" />{{ answer.driveTime }}</span>
              <span v-if="answer.chargeTime" class="inline-flex items-center gap-1.5"><BoltIcon class="h-4 w-4 text-gray-400" aria-hidden="true" />{{ answer.chargeTime }}</span>
            </p>
            <p v-if="answer.powerEstimated" class="text-[12.5px] text-amber-700 dark:text-amber-400">{{ t('personal_range.power_estimated') }}</p>
          </div>
        </li>
        <li class="grid grid-cols-[36px_minmax(0,1fr)] gap-3 px-4 py-4" data-testid="answer-cost">
          <span class="grid h-9 w-9 place-items-center rounded-full bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-300"><BanknotesIcon class="h-5 w-5" aria-hidden="true" /></span>
          <div class="grid gap-1">
            <i18n-t keypath="personal_range.cost_answer" tag="p" class="text-[17px] font-medium leading-snug text-gray-900 dark:text-gray-100">
              <template #cost><b class="whitespace-nowrap text-[24px] font-extrabold tracking-tight tabular-nums">{{ answer.cost }}</b></template>
            </i18n-t>
            <p class="text-[12.5px] text-gray-500 dark:text-gray-400">{{ answer.costBasis }}</p>
          </div>
        </li>
      </ul>

      <p class="inline-flex items-center gap-1.5 justify-self-start rounded-full px-2.5 py-1 text-xs font-semibold"
         :class="result.dataLevel === 'MEASURED' ? 'bg-green-100 text-green-800 dark:bg-green-900/40 dark:text-green-300' : 'bg-amber-100 text-amber-800 dark:bg-amber-900/40 dark:text-amber-300'">
        {{ t(`personal_range.data_level_${result.dataLevel}`) }}
      </p>

      <div class="grid gap-4 border-t border-green-600/25 pt-4 dark:border-green-500/25" data-testid="personal-range-tune">
        <p class="text-[11.5px] font-semibold uppercase tracking-[0.08em] text-green-700 dark:text-green-400">{{ t('personal_range.tune_title') }}</p>
        <div class="grid gap-2">
          <p id="pr-wallbox-q" class="text-[15px] font-bold text-gray-900 dark:text-gray-100">{{ t('personal_range.wallbox_question') }}</p>
          <div class="flex gap-2" role="group" aria-labelledby="pr-wallbox-q">
            <button v-for="opt in WALLBOX" :key="String(opt)" type="button" :aria-pressed="inputs.homeCharging === opt"
                    class="inline-flex min-h-11 min-w-[84px] items-center justify-center rounded-full border-2 px-4 font-semibold transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-green-600"
                    :class="inputs.homeCharging === opt
                      ? 'border-gray-900 bg-gray-900 text-white dark:border-gray-100 dark:bg-gray-100 dark:text-gray-900'
                      : 'border-gray-300 bg-white text-gray-800 hover:border-gray-400 dark:border-gray-700 dark:bg-gray-900 dark:text-gray-200'"
                    :data-testid="`wallbox-${opt ? 'yes' : 'no'}`"
                    @click="setHome(opt)">
              {{ opt ? t('personal_range.wallbox_yes') : t('personal_range.wallbox_no') }}
            </button>
          </div>
          <p class="text-[12.5px] leading-snug text-gray-600 dark:text-gray-400">{{ t('personal_range.wallbox_why') }}</p>
        </div>
        <ProfileSlider v-if="shownPrice != null" id="pr-price" test-key="price" :label="t('personal_range.price_label')"
                       :min="PRICE.min" :max="PRICE.max" :step="PRICE.step" :start="shownPrice" :value="shownPrice"
                       :format="v => formatCostPerKwh(v)" @commit="v => emit('price', Math.round(v * 100) / 100)" />
        <p class="-mt-2 text-[12.5px] leading-snug text-gray-600 dark:text-gray-400">
          <template v-if="inputs.pricePerKwh == null">{{ t('personal_range.price_hint_community') }}</template>
          <button v-else type="button" class="min-h-11 font-semibold text-green-700 underline dark:text-green-400" @click="emit('price', null)">{{ t('personal_range.price_reset') }}</button>
        </p>
      </div>

      <details class="group border-t border-green-600/25 pt-3 dark:border-green-500/25">
        <summary class="flex min-h-11 cursor-pointer list-none items-center justify-between text-sm font-bold text-gray-700 dark:text-gray-300 [&::-webkit-details-marker]:hidden">
          {{ t('personal_range.assumptions_title') }}
          <ChevronDownIcon class="h-4 w-4 transition-transform group-open:rotate-180" aria-hidden="true" />
        </summary>
        <ul class="mt-1 space-y-1">
          <li v-for="a in result.assumptions" :key="a.key" data-testid="assumption" class="flex items-start gap-1.5 text-sm text-gray-700 dark:text-gray-300">
            <CheckCircleIcon v-if="a.measured" class="mt-0.5 h-4 w-4 shrink-0 text-green-600 dark:text-green-400" aria-hidden="true" />
            <!-- a fixed setting, not a missing measurement: no warning colour -->
            <InformationCircleIcon v-else-if="a.key === 'TRAVEL_SPEED'" class="mt-0.5 h-4 w-4 shrink-0 text-gray-400 dark:text-gray-500" aria-hidden="true" />
            <QuestionMarkCircleIcon v-else class="mt-0.5 h-4 w-4 shrink-0 text-amber-600 dark:text-amber-400" aria-hidden="true" />
            <span>{{ assumptionText(a) }} <span class="text-gray-500 dark:text-gray-400">({{ sourceText(a) }})</span></span>
          </li>
        </ul>
      </details>
    </template>

    <p v-if="error" role="alert" class="text-sm font-semibold text-red-700 dark:text-red-400">{{ error }}</p>

    <!-- Phone: a slim bar once the box is out of view, until the answers are open -->
    <Teleport to="body">
      <div v-if="showBar" class="fixed inset-x-0 z-40 px-3 md:hidden" :class="authStore.token ? 'bottom-[calc(env(safe-area-inset-bottom)+68px)]' : 'bottom-[calc(env(safe-area-inset-bottom)+12px)]'" data-testid="unlock-bar">
        <button type="button" class="flex min-h-12 w-full items-center justify-center gap-2 rounded-2xl bg-gray-900 px-4 text-[15px] font-bold text-white shadow-lg shadow-gray-900/25 dark:bg-gray-100 dark:text-gray-900"
                @click="fromBar">
          <LockOpenIcon class="h-5 w-5 text-green-400 dark:text-green-600" aria-hidden="true" />
          {{ authStore.token ? t('personal_range.show') : t('personal_range.bar_cta') }}
        </button>
      </div>
    </Teleport>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowRightIcon, BanknotesIcon, Battery0Icon, Battery100Icon, BoltIcon, CheckCircleIcon, ChevronDownIcon, ClockIcon, CloudIcon, InformationCircleIcon, LockClosedIcon, LockOpenIcon, MapIcon, QuestionMarkCircleIcon } from '@heroicons/vue/24/outline'
import InlineGate from '../auth/InlineGate.vue'
import ProfileSlider from './ProfileSlider.vue'
import { useAuthStore } from '../../stores/auth'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import { analytics } from '../../services/analytics'
import { chargeRhythmText, type RhythmFormat } from '../../utils/chargeRhythm'
import {
  personalRangeService,
  type PersonalRangeAssumption,
  type PersonalRangeInputs,
  type PersonalRangeResult,
} from '../../api/personalRangeService'

/**
 * The result box of the model page, three states in one place: locked (the four questions and a
 * lock, no figure), gate (sign-in right in the box) and result (four answers plus wallbox and
 * price to play with, recalculated live). The driving profile comes in as `inputs`; wallbox and
 * price go back to the page through `home` and `price`. Inputs survive the gate (and a Google
 * redirect) in sessionStorage; a stored profile is handed back through `profile`.
 */
const props = defineProps<{
  brand: string
  model: string
  modelName: string
  logCount: number
  carCount: number
  inputs: PersonalRangeInputs
  /** unanswered sliders; the result rests on answers only, never on start values */
  openCount: number
}>()
const emit = defineEmits<{
  profile: [inputs: PersonalRangeInputs]
  incomplete: []
  home: [value: boolean]
  price: [value: number | null]
}>()

const { t } = useI18n()
const authStore = useAuthStore()
const { formatNumber, formatDistance, formatCurrency, formatConsumption, formatCostPerKwh, formatDecimal } = useLocaleFormat()

/** Below this many cars the box does not advertise measured data (same as the backend highway threshold). */
const MIN_MEASURED_CARS = 5
/** Pending inputs plus "calculate after login" flag; read once on mount. */
const STORAGE_KEY = 'personalRange:inputs'
const RECALC_MS = 400
const WALLBOX = [true, false] as const
/** Within the backend bounds (0,05 to 1,50 €), narrower so 35 ct can be set with a thumb, in EUR */
const PRICE = { min: 0.1, max: 1, step: 0.01 }

const gateOpen = ref(false)
const busy = ref(false)
const error = ref('')
const result = ref<PersonalRangeResult | null>(null)
/** The inputs the shown result was calculated with; the words around the figures follow them, not the sliders */
const resultInputs = ref<PersonalRangeInputs>(props.inputs)
const state = computed(() => result.value ? 'result' : gateOpen.value ? 'gate' : 'locked')
const ctaLabel = computed(() => authStore.token ? t('personal_range.show') : t('personal_range.unlock'))

const questions = computed(() => [
  t('personal_range.question_winter'),
  t('personal_range.question_rhythm'),
  t('personal_range.question_stops', { distance: formatDistance(props.inputs.longTripKm) }),
  t('personal_range.question_cost'),
])
const shortLabels = computed(() => ['winter', 'rhythm', 'stops', 'cost'].map(k => t(`personal_range.short_${k}`)))

/** Same words as the ranking (chargeRhythm): "alle 10 Tage", "täglich", stops without home charging */
const rhythmFormat: RhythmFormat = { t: (k, p, c) => t(k, p ?? {}, c as number), formatDecimal }
const rhythm = (perWeek: number) => chargeRhythmText(perWeek, resultInputs.value.homeCharging, rhythmFormat)

/** The four answers of the shown result, worded with the inputs of that same calculation */
const answer = computed(() => {
  const r = result.value
  if (!r) return null
  const inputs = resultInputs.value
  const days = inputs.commuteDaysPerWeek ?? 5
  const summerText = rhythm(r.chargesPerWeekSummer)
  const winterText = rhythm(r.chargesPerWeekWinter)
  return {
    winter: formatDistance(r.winterRangeKm),
    summer: t('personal_range.winter_summer', { range: formatDistance(r.summerRangeKm) }),
    // the same words for both seasons read as one line: "Sommer wie Winter alle 4 Tage"
    rhythm: summerText === winterText
      ? [{ key: 'personal_range.rhythm_same', value: summerText }]
      : [{ key: 'personal_range.rhythm_summer', value: summerText }, { key: 'personal_range.rhythm_winter', value: winterText }],
    rhythmBasis: t('personal_range.rhythm_basis', { km: formatDistance(inputs.commuteKmPerDay * days) }),
    distance: formatDistance(inputs.longTripKm),
    times: t('personal_range.stops_times', { n: r.longTripStops }, r.longTripStops),
    driveTime: t('personal_range.drive_time', { duration: duration(r.longTripTotalMinutes) }),
    chargeTime: r.longTripStops > 0 ? t('personal_range.charge_time', { duration: duration(r.longTripChargingMinutes) }) : null,
    powerEstimated: r.longTripStops > 0 && r.assumptions.some(a => a.key === 'CHARGING_POWER' && !a.measured),
    cost: formatCurrency(r.monthlyCostEur, { decimals: 0 }),
    costBasis: inputs.annualKm ? t('personal_range.cost_basis_annual', { annual: formatDistance(inputs.annualKm) }) : t('personal_range.cost_basis_charges'),
  }
})

/** The reader's own price, else the community price the backend used (follows the wallbox answer) */
const shownPrice = computed(() => props.inputs.pricePerKwh
  ?? result.value?.assumptions.find(a => a.key === 'PRICE')?.value ?? null)

function setHome(value: boolean) {
  analytics.track('personal_range_wallbox', { model: props.modelName, home: value })
  emit('home', value)
}

/** Datenblatt for the battery; the user's own price; otherwise gemessen, geschätzt, or the measured share of a blend */
function sourceText(a: PersonalRangeAssumption): string {
  if (a.key === 'BATTERY') return a.measured ? t('personal_range.source_spec') : t('personal_range.source_spec_derived')
  // a fixed setting, and the power of a model without own sessions is the median of the measured ones
  if (a.key === 'TRAVEL_SPEED') return t('personal_range.source_fixed')
  if (a.key === 'CHARGING_POWER' && !a.measured) return t('personal_range.source_fleet_median')
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

/** "Freischalten": back to the open sliders first, then the gate for guests, the result for signed-in visitors. */
async function request() {
  analytics.track('personal_range_unlock_click', { model: props.modelName, open: props.openCount })
  if (props.openCount > 0) {
    emit('incomplete')
    return
  }
  if (!authStore.token) {
    gateOpen.value = true
    focusHeading()
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
/** Number of the newest request; an older answer arriving later is dropped */
let requestNo = 0

/** @param email true right after the gate: the result also goes out as a mail, as the gate promised */
async function calculate(email = false, inputs: PersonalRangeInputs = props.inputs) {
  const no = ++requestNo
  const first = result.value == null
  const sent = { ...inputs }
  busy.value = true
  error.value = ''
  try {
    const next = await personalRangeService.calculate(props.brand, props.model, sent, email)
    if (no !== requestNo) return
    result.value = next
    resultInputs.value = sent
    if (first) {
      analytics.track('personal_range_result', { model: props.modelName, data_level: next.dataLevel })
      focusHeading()
    }
  } catch {
    if (no === requestNo) error.value = t('personal_range.error')
  } finally {
    if (no === requestNo) busy.value = false
  }
}

/** The button the focus was on is gone with the state change: the focus moves to the new heading */
function focusHeading() {
  void nextTick(() => root.value?.querySelector<HTMLElement>('#pr-title')?.focus({ preventScroll: true }))
}

// Signed in and every slider answered (a stored profile, or the last slider just now): no gate, the answers right away.
// Not while the gate is open: its login sets the token first, onAuthenticated then calculates once, with the mail.
watch(() => [props.openCount, authStore.token] as const, ([open, token]) => {
  if (open === 0 && token && !result.value && !busy.value && !gateOpen.value) void calculate()
})

// Open answers follow every change of profile, wallbox and price, debounced
let timer: ReturnType<typeof setTimeout> | null = null
watch(() => ({ ...props.inputs }), () => {
  if (!result.value || !authStore.token) return
  if (timer) clearTimeout(timer)
  timer = setTimeout(() => { void calculate() }, RECALC_MS)
}, { deep: true })

// The phone bar shows while the box is out of view and the answers are still locked
const root = ref<HTMLElement | null>(null)
const boxVisible = ref(true)
/** The bar waits until the reader has seen the box or moved a slider, so it does not cover the data sheet on arrival */
const engaged = ref(false)
const startOpen = props.openCount
watch(() => props.openCount, n => { if (n < startOpen) engaged.value = true })
let observer: IntersectionObserver | null = null
const showBar = computed(() => !result.value && !boxVisible.value && engaged.value)

function fromBar() {
  root.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  if (state.value === 'locked') void request()
}

onMounted(async () => {
  if (root.value && typeof IntersectionObserver !== 'undefined') {
    observer = new IntersectionObserver(entries => {
      boxVisible.value = entries.some(e => e.isIntersecting)
      if (boxVisible.value) engaged.value = true
    })
    observer.observe(root.value)
  }
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
  // every slider answered before the mount (the ranking's needs check, a prefilled tile): no click needed either
  if (props.openCount === 0 && !result.value && !busy.value) await calculate()
})

onBeforeUnmount(() => {
  observer?.disconnect()
  if (timer) clearTimeout(timer)
})

/** "8 h 5 min", "45 min", "9 h" in the locale's unit words */
function duration(minutes: number): string {
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  if (h === 0) return t('personal_range.duration_m', { m })
  return m === 0 ? t('personal_range.duration_h', { h }) : t('personal_range.duration_h_m', { h, m })
}

/** Locale number without a trailing ",0". */
function compact(value: number): string {
  return formatDecimal(value, Number.isInteger(value) ? 0 : 1)
}

function assumptionText(a: PersonalRangeAssumption): string {
  const value = a.key.endsWith('_CONSUMPTION') ? formatConsumption(a.value)
    : a.key === 'BATTERY' ? `${compact(a.value)} kWh`
    : a.key === 'CHARGING_POWER' ? `${compact(a.value)} kW`
    : a.key === 'TRAVEL_SPEED' ? `${compact(a.value)} km/h`
    : formatCostPerKwh(a.value)
  return t(`personal_range.assumption_${a.key}`, { value })
}

defineExpose({ request })
</script>
