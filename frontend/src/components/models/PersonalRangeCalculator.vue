<template>
  <section ref="root" id="ergebnis" aria-labelledby="pr-title" data-testid="personal-range"
           class="grid min-w-0 scroll-mt-28 content-start gap-5 px-4 py-6 transition-colors duration-300 md:rounded-3xl md:p-7 md:edge-box"
           :class="state === 'locked'
             ? 'bg-white dark:bg-gray-900'
             : '-my-2 bg-green-50 md:my-0 dark:bg-gray-900'">
    <!-- Green (gate, result) on the phone: the surface separates it from its white neighbours, so it takes up the grey gaps -->

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

      <div class="relative overflow-hidden rounded-2xl border border-gray-200 max-md:edge-card dark:border-gray-800" data-testid="personal-range-placeholder">
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
          : carCount >= MIN_MEASURED_CARS ? t('personal_range.locked_basis', { logs: formatNumber(logCount), cars: formatNumber(carCount), model: modelName })
          : t('personal_range.locked_basis_thin', { logs: formatNumber(logCount), cars: formatNumber(carCount) }) }}
        <!-- what the free unlock asks for, right above the button -->
        <template v-if="!authStore.token">{{ t('personal_range.locked_free') }}</template>
      </p>
      <button ref="action" type="button" data-testid="personal-range-calculate" :disabled="busy"
              class="inline-flex min-h-12 w-full items-center justify-center gap-2 rounded-xl bg-green-600 px-4 text-[16px] font-bold text-white transition-colors hover:bg-green-700 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-green-600 disabled:opacity-60 btn-3d cta-shadow-sm border-2 border-gray-900 dark:border-transparent"
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
      <div ref="action"><InlineGate plain :consent="t('personal_range.gate_privacy')" :placement="`modell:${brand}/${model}`" @authenticated="onAuthenticated" /></div>
    </template>

    <!-- Result: four answers; wallbox and price live in ProfileTune beside the sliders -->
    <template v-else-if="result">
      <div class="flex items-center justify-center gap-3 text-center">
        <span class="grid h-12 w-12 flex-none place-items-center rounded-full bg-green-600 text-white"><LockOpenIcon class="h-6 w-6" aria-hidden="true" /></span>
        <h2 id="pr-title" tabindex="-1" class="text-[19px] font-bold leading-tight tracking-tight text-gray-900 focus:outline-none dark:text-gray-100">{{ t('personal_range.answers_title', { model: modelName }) }}</h2>
      </div>

      <!-- The set profile as a white strip above the answers: one line, "Angaben ändern" on the right
           unfolds the five sliders in their compact form (the ranking's strip) inside the same strip -->
      <div v-if="profileSummary" class="rounded-2xl bg-white shadow-[0_1px_2px_rgba(0,0,0,0.05)] max-md:edge-card dark:bg-gray-800" data-testid="personal-range-profile-strip">
        <div class="flex items-start justify-between gap-3 px-4 py-3 md:items-center md:px-5">
          <p class="min-w-0 text-[13.5px] leading-snug text-gray-700 md:text-[14px] dark:text-gray-300" data-testid="personal-range-profile-summary">{{ profileSummary }}</p>
          <button type="button" data-testid="personal-range-profile-toggle" :aria-expanded="profileOpen" aria-controls="pr-profile"
                  class="-my-2 inline-flex min-h-11 flex-none items-center gap-1 text-[14px] font-semibold text-green-700 underline-offset-4 hover:underline dark:text-green-400"
                  @click="emit('update:profileOpen', !profileOpen)">
            {{ t('personal_range.profile_change') }}
            <ChevronDownIcon class="h-4 w-4 transition-transform" :class="profileOpen ? 'rotate-180' : ''" aria-hidden="true" />
          </button>
        </div>
        <!-- folds both ways: the row grows from 0fr to 1fr and back, the inner box stays min-h-0 so it clips;
             the content stays mounted (inert while closed), else there is nothing to shrink -->
        <div id="pr-profile" class="grid transition-[grid-template-rows] duration-300 ease-out motion-reduce:transition-none" :class="profileOpen ? 'grid-rows-[1fr]' : 'grid-rows-[0fr]'" :inert="profileOpen ? undefined : true">
          <div class="min-h-0 overflow-hidden">
            <div class="border-t border-gray-100 px-4 pb-5 pt-4 md:px-5 dark:border-gray-800"><slot name="profile" /></div>
          </div>
        </div>
      </div>

      <p class="sr-only" role="status">{{ busy ? t('personal_range.calculating') : '' }}</p>
      <!-- One answer per row, as a sentence; the figure is the loud part (AnswerGrid, shared with the model list) -->
      <AnswerGrid ref="answers" v-if="answer" :busy="busy" data-testid="personal-range-result">
        <AnswerCell :icon="CloudIcon" tone="sky" data-testid="answer-winter">
            <i18n-t keypath="personal_range.winter_answer" tag="p" class="text-[17px] font-medium leading-snug text-gray-900 dark:text-gray-100">
              <template #winter><b class="whitespace-nowrap text-[24px] font-extrabold tracking-tight tabular-nums">{{ answer.winter }}</b></template>
              <template #summer><br><b class="font-bold">{{ answer.summer }}</b></template>
            </i18n-t>
            <!-- The window both figures rest on, as the gauge in the hero draws it -->
            <p class="flex items-center gap-1 text-[12.5px] font-semibold text-gray-500 dark:text-gray-400">
              <Battery100Icon class="h-4 w-4" aria-hidden="true" /><span>100 %</span>
              <ArrowRightIcon class="h-3 w-3" aria-hidden="true" />
              <Battery0Icon class="h-4 w-4" aria-hidden="true" /><span>10 %</span>
            </p>
        </AnswerCell>
        <AnswerCell :icon="BoltIcon" tone="green" data-testid="answer-rhythm">
            <i18n-t v-for="row in answer.rhythm" :key="row.key" :keypath="row.key" tag="p" class="text-[17px] font-medium leading-snug text-gray-900 dark:text-gray-100">
              <template #rhythm><b class="font-extrabold">{{ row.value }}</b></template>
            </i18n-t>
            <p class="text-[12.5px] text-gray-500 dark:text-gray-400">{{ answer.rhythmBasis }}</p>
        </AnswerCell>
        <AnswerCell :icon="MapIcon" tone="amber" dense data-testid="answer-stops">
            <i18n-t keypath="personal_range.stops_answer" tag="p" class="text-[17px] font-medium leading-snug text-gray-900 dark:text-gray-100">
              <template #distance><span class="whitespace-nowrap">{{ answer.distance }}</span></template>
              <template #times><b class="whitespace-nowrap text-[20px] font-extrabold tracking-tight">{{ answer.times }}</b></template>
            </i18n-t>
            <p v-if="answer.driveTime" class="flex flex-wrap gap-x-4 gap-y-1 text-[13px] text-gray-600 dark:text-gray-400">
              <span class="inline-flex items-center gap-1.5"><ClockIcon class="h-4 w-4 text-gray-400" aria-hidden="true" />{{ answer.driveTime }}</span>
              <span v-if="answer.chargeTime" class="inline-flex items-center gap-1.5"><BoltIcon class="h-4 w-4 text-gray-400" aria-hidden="true" />{{ answer.chargeTime }}</span>
            </p>
            <i18n-t v-if="answer.tripCost" keypath="personal_range.trip_cost" tag="p" class="text-[15px] font-medium text-gray-900 dark:text-gray-100">
              <template #cost><b class="whitespace-nowrap text-[20px] font-extrabold tracking-tight tabular-nums">{{ answer.tripCost }}</b></template>
            </i18n-t>
        </AnswerCell>
        <AnswerCell :icon="BanknotesIcon" tone="gray" data-testid="answer-cost">
            <i18n-t keypath="personal_range.cost_answer" tag="p" class="text-[17px] font-medium leading-snug text-gray-900 dark:text-gray-100">
              <template #cost><b class="whitespace-nowrap text-[24px] font-extrabold tracking-tight tabular-nums">{{ answer.cost }}</b></template>
            </i18n-t>
            <p class="text-[12.5px] text-gray-500 dark:text-gray-400">{{ answer.costBasis }}</p>
            <template v-if="answer.petrol">
              <i18n-t :keypath="answer.petrol.saving ? 'personal_range.petrol_compare' : 'personal_range.petrol_compare_more'" tag="p"
                      class="mt-1.5 text-[15px] font-medium leading-snug text-gray-900 dark:text-gray-100">
                <template #petrol><b class="whitespace-nowrap font-bold">{{ answer.petrol.cost }}</b></template>
                <template #saving><b class="whitespace-nowrap font-extrabold text-green-700 dark:text-green-400">{{ answer.petrol.saving }}</b></template>
              </i18n-t>
              <p class="text-[12px] leading-snug text-gray-500 dark:text-gray-400">{{ answer.petrol.basis }}</p>
            </template>
        </AnswerCell>
        <!-- The two assumptions to play with, a tile of their own across both columns -->
        <template #tune>
          <ProfileTune :model-name="modelName" :inputs="inputs" :community-price="communityPrice" @home="v => emit('home', v)" @price="v => emit('price', v)" />
        </template>
      </AnswerGrid>


      <!-- What the result rests on: a light, centred link that opens the list in a card -->
      <div class="grid gap-1">
        <button type="button" data-testid="personal-range-assumptions-toggle" :aria-expanded="assumptionsOpen" aria-controls="pr-assumptions"
                class="mx-auto flex min-h-11 w-fit items-center gap-1.5 text-[14px] font-semibold text-green-800 hover:underline dark:text-green-300"
                @click="assumptionsOpen = !assumptionsOpen">
          {{ t('personal_range.assumptions_title') }}
          <ChevronDownIcon class="h-4 w-4 transition-transform" :class="assumptionsOpen ? 'rotate-180' : ''" aria-hidden="true" />
        </button>
        <div id="pr-assumptions" class="grid transition-[grid-template-rows] duration-300 ease-out motion-reduce:transition-none" :class="assumptionsOpen ? 'grid-rows-[1fr]' : 'grid-rows-[0fr]'" :inert="assumptionsOpen ? undefined : true">
          <div class="min-h-0 overflow-hidden">
            <ul class="space-y-1.5 rounded-2xl bg-white px-4 py-3.5 shadow-[0_1px_2px_rgba(0,0,0,0.05)] max-md:edge-card dark:bg-gray-800">
              <li v-for="a in result.assumptions" :key="a.key" data-testid="assumption" class="flex items-start gap-1.5 text-sm text-gray-700 dark:text-gray-300">
                <CheckCircleIcon v-if="a.measured" class="mt-0.5 h-4 w-4 shrink-0 text-green-600 dark:text-green-400" aria-hidden="true" />
                <!-- a fixed setting, not a missing measurement: no warning colour -->
                <InformationCircleIcon v-else-if="a.key === 'TRAVEL_SPEED'" class="mt-0.5 h-4 w-4 shrink-0 text-gray-400 dark:text-gray-500" aria-hidden="true" />
                <QuestionMarkCircleIcon v-else class="mt-0.5 h-4 w-4 shrink-0 text-amber-600 dark:text-amber-400" aria-hidden="true" />
                <span>{{ assumptionText(a) }} <span class="text-gray-500 dark:text-gray-400">({{ sourceText(a) }})</span></span>
              </li>
            </ul>
          </div>
        </div>
      </div>
    </template>

    <p v-if="error" role="alert" class="text-sm font-semibold text-red-700 dark:text-red-400">{{ error }}</p>

    <!-- Desktop: the four figures follow the reader down to the sliders once the answers are out of view -->
    <Teleport to="body">
      <Transition name="bar">
        <div v-if="answer && followBar" class="fixed inset-x-0 z-30 hidden justify-center px-4 md:flex" :style="{ top: 'var(--content-top, 0px)' }" data-testid="personal-range-follow">
          <button type="button" :aria-label="t('personal_range.back_to_result')"
                  class="mt-2 flex max-w-5xl items-center gap-6 rounded-2xl border border-green-600/20 bg-white/95 px-5 py-2.5 text-[15px] font-semibold text-gray-900 shadow-lg shadow-gray-900/10 backdrop-blur hover:bg-white dark:border-green-500/20 dark:bg-gray-900/95 dark:text-gray-100 dark:hover:bg-gray-900"
                  :class="busy ? 'opacity-60' : ''" @click="scrollToElement(root!)">
            <span class="inline-flex items-center gap-1.5"><CloudIcon class="h-5 w-5 text-sky-700 dark:text-sky-300" aria-hidden="true" /><b class="tabular-nums">{{ answer.winter }}</b></span>
            <span class="inline-flex items-center gap-1.5"><BoltIcon class="h-5 w-5 text-green-700 dark:text-green-300" aria-hidden="true" /><b>{{ answer.rhythm[0]?.value }}</b></span>
            <span class="inline-flex items-center gap-1.5"><MapIcon class="h-5 w-5 text-amber-700 dark:text-amber-300" aria-hidden="true" /><b>{{ answer.times }}</b></span>
            <span class="inline-flex items-center gap-1.5"><BanknotesIcon class="h-5 w-5 text-gray-600 dark:text-gray-300" aria-hidden="true" /><b class="tabular-nums">{{ answer.cost }}</b></span>
          </button>
        </div>
      </Transition>
    </Teleport>

    <!-- Phone: a slim bar while the box's own button is out of view, until the answers are open -->
    <UnlockBar :label="authStore.token ? t('personal_range.show') : t('personal_range.bar_cta')" :signed-in="!!authStore.token"
               :active="!result" :box="root" :action="action" :entry="entry" :nudged="openCount < startOpen" @press="fromBar" />
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowRightIcon, BanknotesIcon, Battery0Icon, Battery100Icon, BoltIcon, CheckCircleIcon, ChevronDownIcon, ClockIcon, CloudIcon, InformationCircleIcon, LockClosedIcon, LockOpenIcon, MapIcon, QuestionMarkCircleIcon } from '@heroicons/vue/24/outline'
import InlineGate from '../auth/InlineGate.vue'
import UnlockBar from './UnlockBar.vue'
import ProfileTune from './ProfileTune.vue'
import AnswerGrid from './AnswerGrid.vue'
import AnswerCell from './AnswerCell.vue'
import { useAuthStore } from '../../stores/auth'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import { analytics } from '../../services/analytics'
import { chargeRhythmText, type RhythmFormat } from '../../utils/chargeRhythm'
import { scrollToElement } from '../../utils/smoothScroll'
import { fastStop, MIN_MEASURED_CARS } from '../../utils/modelStory'
import {
  personalRangeService,
  type PersonalRangeAssumption,
  type PersonalRangeInputs,
  type PersonalRangeResult,
} from '../../api/personalRangeService'

/**
 * The result box of the model page, three states in one place: locked (the four questions and a
 * lock, no figure), gate (sign-in right in the box) and result (four answers, recalculated live
 * on every change of profile, wallbox and price). The driving profile comes in as `inputs`;
 * wallbox and price are set in ProfileTune, a tile of the answers, and go back to the page
 * through `home` and `price`. Inputs survive the gate (and a Google redirect) in sessionStorage; a stored
 * profile is handed back through `profile`. From md up a slim bar with the four figures follows
 * the reader while `follow` (the sliders) is in view and the answers are not.
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
  /** a petrol car of the same vehicle class (its litres, the class name), German daily fuel price; null outside Germany */
  petrol?: { pricePerLiter: number; litersPer100km: number; category: string | null } | null
  /** fast charging without a subscription, EUR/kWh; prices the charging stops of the holiday trip */
  fastPricePerKwh?: number | null
  /** the heading the reader reaches first on the way to the answers; once it stands above the bar, the bar comes */
  entry?: HTMLElement | null
  /** the sliders; while they are in view and the answers are not, the figures follow (md up) */
  follow?: HTMLElement | null
  /** the strip above the answers unfolded: shows the `profile` slot (the compact sliders) */
  profileOpen?: boolean
  /** the set profile in one line, the strip above the answers */
  profileSummary?: string | null
}>()
const emit = defineEmits<{
  profile: [inputs: PersonalRangeInputs]
  incomplete: []
  home: [value: boolean]
  price: [value: number | null]
  /** answers open or closed: the page lays the box out differently */
  open: [value: boolean]
  'update:profileOpen': [value: boolean]
}>()

const { t } = useI18n()
const authStore = useAuthStore()
const { formatNumber, formatDistance, formatCurrency, formatConsumption, formatCostPerKwh, formatCostPerDistance, formatDecimal } = useLocaleFormat()

/** Pending inputs plus "calculate after login" flag; read once on mount. */
const STORAGE_KEY = 'personalRange:inputs'
const RECALC_MS = 400

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
  return {
    winter: formatDistance(r.winterRangeKm),
    summer: t('personal_range.winter_summer', { range: formatDistance(r.summerRangeKm) }),
    // one statement at the winter figure: it holds the whole year
    rhythm: [{ key: 'personal_range.rhythm_one', value: rhythm(r.chargesPerWeekWinter) }],
    rhythmBasis: t('personal_range.rhythm_basis', { km: formatDistance(inputs.commuteKmPerDay * days) }),
    distance: formatDistance(inputs.longTripKm),
    times: t('personal_range.stops_times', { n: r.longTripStops }, r.longTripStops),
    // no time without a reliable charging power (own measurement or datasheet): the stops stand alone
    driveTime: r.longTripTotalMinutes != null ? t('personal_range.drive_time', { duration: duration(r.longTripTotalMinutes) }) : null,
    chargeTime: r.longTripStops > 0 && r.longTripChargingMinutes != null
      ? t('personal_range.charge_time', { duration: duration(r.longTripChargingMinutes) }) : null,
    tripCost: tripChargingCost(r.longTripStops, inputs.netBatteryKwh ?? null),
    cost: formatCurrency(r.monthlyCostEur, { decimals: 0 }),
    // the reader's own cost per 100 km, from the month and the yearly mileage it rests on
    costBasis: inputs.annualKm
      ? t('personal_range.cost_basis_per100', { per100: formatCostPerDistance(r.monthlyCostEur * 12 / inputs.annualKm * 100), annual: formatDistance(inputs.annualKm) })
      : t('personal_range.cost_basis_charges'),
    petrol: petrolCompare(r.monthlyCostEur, inputs.annualKm ?? null),
  }
})

/** The same month's driving in a comparable petrol car, and what the EV saves on it */
function petrolCompare(evMonthly: number, annualKm: number | null) {
  const p = props.petrol
  if (!p || !annualKm) return null
  const monthly = annualKm / 12 * p.litersPer100km / 100 * p.pricePerLiter
  const saving = Math.round(monthly) - Math.round(evMonthly)
  return {
    cost: formatCurrency(monthly, { decimals: 0 }),
    saving: saving > 0 ? formatCurrency(saving, { decimals: 0 }) : null,
    basis: t(p.category ? 'personal_range.petrol_basis' : 'personal_range.petrol_basis_plain',
      { category: p.category ?? '', liters: formatDecimal(p.litersPer100km, 1), price: formatCurrency(p.pricePerLiter, { decimals: 2 }) }),
  }
}

/** The charging stops of the holiday trip, each 10 to 80 % at the price without a subscription */
function tripChargingCost(stops: number, netKwh: number | null): string | null {
  const stop = stops > 0 && props.fastPricePerKwh ? fastStop(netKwh, props.fastPricePerKwh) : null
  return stop ? formatCurrency(stops * stop.eur, { decimals: 0 }) : null
}

/** The community price the shown result rests on; the price slider starts there */
const communityPrice = computed(() => result.value?.assumptions.find(a => a.key === 'PRICE')?.value ?? null)
watch(result, r => emit('open', !!r))

/** Datenblatt for the battery; the user's own price; otherwise gemessen, geschätzt, or the measured share of a blend */
function sourceText(a: PersonalRangeAssumption): string {
  if (a.key === 'BATTERY') return a.measured ? t('personal_range.source_spec') : t('personal_range.source_spec_derived')
  // a fixed setting, and the power of a model without own sessions comes from its datasheet
  if (a.key === 'TRAVEL_SPEED') return t('personal_range.source_fixed')
  if (a.key === 'CHARGING_POWER' && !a.measured) return t('personal_range.source_spec')
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

// The phone bar: the box, its button and whether a slider moved since the page opened
const root = ref<HTMLElement | null>(null)
const action = ref<HTMLElement | null>(null)
const startOpen = props.openCount

// The desktop bar: answers scrolled out above, sliders in view. Measured on scroll, not observed:
// an IntersectionObserver reports no change when one scroll step carries the answers from below
// the viewport to above it.
const answers = ref<InstanceType<typeof AnswerGrid> | null>(null)
const followBar = ref(false)
/** the assumptions list under the answers, folded by default */
const assumptionsOpen = ref(false)
let measureQueued = false
function measureFollow() {
  measureQueued = false
  const a = answers.value?.el?.getBoundingClientRect()
  const f = props.follow?.getBoundingClientRect()
  followBar.value = !!a && !!f && a.bottom < 80 && f.bottom > 0 && f.top < window.innerHeight
}
function onScroll() {
  if (measureQueued) return
  measureQueued = true
  requestAnimationFrame(measureFollow)
}
watch([answers, () => props.follow], ([el]) => {
  window.removeEventListener('scroll', onScroll)
  followBar.value = false
  if (!el || !props.follow) return
  window.addEventListener('scroll', onScroll, { passive: true })
  onScroll()
}, { immediate: true })

function fromBar() {
  if (root.value) scrollToElement(root.value)
  if (state.value === 'locked') void request()
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
  // every slider answered before the mount (the ranking's needs check, a prefilled tile): no click needed either
  if (props.openCount === 0 && !result.value && !busy.value) await calculate()
})

onBeforeUnmount(() => {
  if (timer) clearTimeout(timer)
  window.removeEventListener('scroll', onScroll)
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

</script>

