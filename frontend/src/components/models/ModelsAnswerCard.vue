<template>
  <DrivingProfileCard ref="card" id="passt-zu-dir" class="scroll-mt-24" :title="t('ranking_card.title')"
                      :sub="locked ? t('ranking_card.sub', { n: formatNumber(total) }) : t('ranking_card.sub_unlocked', { n: formatNumber(total) })"
                      :answered="answered" :unlocked="!locked" :summary="summaryLine" v-model:open="questionsOpen" data-testid="ranking-card">
    <template #questions>
      <ProfileQuestions :sliders="sliders" id-prefix="needs" test-prefix="needs-" compact
                        :priorities="priorities" :priority="needs.priority" @answer="answer" @update:priority="setPriority" />
    </template>

    <template #answers>
      <p class="sr-only" aria-live="polite">{{ liveText }}</p>
      <AnswerGrid v-if="answers" data-testid="ranking-answers">
        <AnswerCell :icon="CloudIcon" tone="sky" data-testid="ranking-answer-range">
          <i18n-t keypath="ranking_card.winter" tag="p" class="text-[17px] font-medium leading-snug text-gray-900 dark:text-gray-100">
            <template #winter><b class="whitespace-nowrap text-[22px] font-extrabold tracking-tight tabular-nums">{{ kmSpan(answers.winterKm) }}</b></template>
            <template #summer><b class="whitespace-nowrap font-bold">{{ kmSpan(answers.summerKm) }}</b></template>
          </i18n-t>
          <p v-if="commuteLine" class="text-[12.5px] text-gray-500 dark:text-gray-400">{{ commuteLine }}</p>
        </AnswerCell>
        <AnswerCell v-if="chargeText" :icon="BoltIcon" tone="green" data-testid="ranking-answer-charge">
          <i18n-t keypath="ranking_card.charge" tag="p" class="text-[17px] font-medium leading-snug text-gray-900 dark:text-gray-100">
            <template #every><b class="whitespace-nowrap text-[22px] font-extrabold tracking-tight">{{ chargeText }}</b></template>
          </i18n-t>
          <p class="text-[12.5px] text-gray-500 dark:text-gray-400">{{ chargeBasis }}</p>
        </AnswerCell>
        <AnswerCell v-if="answers.stops && tripKm" :icon="MapIcon" tone="amber" data-testid="ranking-answer-stops">
          <p v-if="answers.stops.hi === 0" class="text-[17px] font-medium leading-snug text-gray-900 dark:text-gray-100">{{ t('ranking_card.stops_none', { km: tripKm }) }}</p>
          <template v-else>
            <i18n-t keypath="ranking_card.stops" tag="p" class="text-[17px] font-medium leading-snug text-gray-900 dark:text-gray-100">
              <template #km><span class="whitespace-nowrap">{{ tripKm }}</span></template>
              <template #times><b class="whitespace-nowrap text-[22px] font-extrabold tracking-tight">{{ stopsTimes }}</b></template>
            </i18n-t>
            <p class="text-[12.5px] text-gray-500 dark:text-gray-400">{{ fewestLine }}</p>
          </template>
        </AnswerCell>
        <AnswerCell v-if="showCost" :icon="BanknotesIcon" tone="gray" data-testid="ranking-answer-cost">
          <i18n-t keypath="ranking_card.cost" tag="p" class="text-[17px] font-medium leading-snug text-gray-900 dark:text-gray-100">
            <template #cost><b class="whitespace-nowrap text-[22px] font-extrabold tracking-tight tabular-nums">{{ euroSpan(answers.costEur) }}</b></template>
          </i18n-t>
          <p class="text-[12.5px] text-gray-500 dark:text-gray-400">{{ costBasis }}</p>
          <i18n-t v-if="answers.petrolEur != null" :keypath="answers.savingEur ? 'ranking_card.petrol' : 'ranking_card.petrol_more'" tag="p"
                  class="mt-1.5 text-[15px] font-medium leading-snug text-gray-900 dark:text-gray-100">
            <template #petrol><b class="whitespace-nowrap font-bold">{{ euro(answers.petrolEur) }}</b></template>
            <template #saving><b v-if="answers.savingEur" class="whitespace-nowrap font-extrabold text-green-700 dark:text-green-400">{{ euroSpan(answers.savingEur) }}</b></template>
          </i18n-t>
        </AnswerCell>
        <template v-if="!locked" #tune>
          <ProfileTune :model-name="TRACK_NAME" :inputs="tuneInputs" :community-price="price" @home="setHome" @price="v => emit('price', v)" />
        </template>
      </AnswerGrid>
    </template>

    <template #footer>
      <!-- Guests: one gate across the full width, it names how many models fit and promises which -->
      <div v-if="locked" ref="gateBox" class="grid gap-3 rounded-2xl border-2 border-green-600 bg-white px-4 py-4 max-md:edge-card md:px-5 dark:border-green-500 dark:bg-gray-900" data-testid="ranking-gate">
        <div class="grid gap-3 md:grid-cols-[minmax(0,1fr)_auto] md:items-center md:gap-6">
          <div class="flex items-start gap-3">
            <span class="grid h-10 w-10 flex-none place-items-center rounded-full bg-gray-900 text-white dark:bg-gray-100 dark:text-gray-900"><LockClosedIcon class="h-5 w-5" aria-hidden="true" /></span>
            <div class="grid gap-1">
              <h3 class="text-[17px] font-bold tracking-tight text-gray-900 dark:text-gray-100">{{ t('ranking_card.gate_title') }}</h3>
              <p class="text-[14px] leading-snug text-gray-700 dark:text-gray-300" data-testid="ranking-gate-lead">{{ gateLead }}</p>
            </div>
          </div>
          <button v-if="!gateOpen" ref="gateButton" type="button" data-testid="ranking-gate-open"
                  class="btn-3d cta-shadow-sm inline-flex min-h-12 items-center justify-center gap-2 rounded-xl border-2 border-gray-900 bg-green-600 px-5 text-[16px] font-bold text-white hover:bg-green-700 max-md:w-full dark:border-gray-200"
                  @click="openGate">
            <LockOpenIcon class="h-5 w-5" aria-hidden="true" />{{ t('ranking_card.gate_cta') }}
          </button>
        </div>
        <InlineGate v-if="gateOpen" plain :consent="t('ranking_card.gate_consent')" placement="modelle:top5" @authenticated="onAuthenticated" />
        <p v-else class="text-[12.5px] leading-snug text-gray-500 dark:text-gray-400">{{ t('ranking_card.gate_consent') }}</p>
      </div>
      <ModelTopFive v-else-if="topFive.length" :rows="topFive" :priority="needs.priority" :trip-label="t('ranking_card.col_trip', { km: tripKm || formatDistance(PROFILE_SLIDERS.longTrip.start) })"
                    :sub="topSub" @open="rank => analytics.track('ranking_top5_click', { rank })" />

      <!-- What the result rests on, for the reader who wants to know -->
      <div v-if="answers" class="grid gap-1">
        <button type="button" :aria-expanded="basisOpen" :aria-controls="basisId" data-testid="ranking-basis-toggle"
                class="mx-auto flex min-h-11 w-fit items-center gap-1.5 text-[14px] font-semibold text-green-800 hover:underline dark:text-green-300"
                @click="basisOpen = !basisOpen">
          {{ t('ranking_card.basis_title') }}
          <ChevronDownIcon class="h-4 w-4 transition-transform" :class="basisOpen ? 'rotate-180' : ''" aria-hidden="true" />
        </button>
        <ul v-show="basisOpen" :id="basisId" class="grid gap-1.5 rounded-2xl bg-white px-4 py-3.5 text-[13.5px] text-gray-700 shadow-[0_1px_2px_rgba(0,0,0,0.05)] max-md:edge-card dark:bg-gray-900 dark:text-gray-300">
          <li>{{ t('ranking_card.basis_spans') }}</li>
          <li>{{ t('ranking_card.basis_price', { price: formatCostPerKwh(price), where: t(home ? 'ranking_card.basis_where_home' : 'ranking_card.basis_where_road') }) }}</li>
          <li v-if="petrolPricePerLiter">{{ t('ranking_card.basis_petrol', { liters: formatDecimal(PETROL_LITERS_PER_100KM, 1), price: formatCurrency(petrolPricePerLiter) }) }}</li>
        </ul>
      </div>

      <!-- Phone: a slim bar with the gate's button while the gate is out of view -->
      <UnlockBar v-if="locked" :label="t('ranking_card.gate_cta')" :signed-in="false" :active="!gateOpen" :box="cardEl" :action="gateBox"
                 :nudged="anyAnswer" @press="fromBar" />
    </template>
  </DrivingProfileCard>
</template>

<script setup lang="ts">
import { computed, ref, useId, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { BanknotesIcon, BoltIcon, ChevronDownIcon, CloudIcon, LockClosedIcon, LockOpenIcon, MapIcon } from '@heroicons/vue/24/outline'
import DrivingProfileCard from './DrivingProfileCard.vue'
import ProfileQuestions from './ProfileQuestions.vue'
import AnswerGrid from './AnswerGrid.vue'
import AnswerCell from './AnswerCell.vue'
import ProfileTune from './ProfileTune.vue'
import ModelTopFive, { type TopFiveRow } from './ModelTopFive.vue'
import UnlockBar from './UnlockBar.vue'
import InlineGate from '../auth/InlineGate.vue'
import { analytics } from '../../services/analytics'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import { useProfileSliders, type ProfileValues } from '../../composables/useProfileSliders'
import { NEEDS_FIELD } from '../../composables/useDrivingProfile'
import { PROFILE_KEYS, PROFILE_SLIDERS, needsToInputs, type ProfileKey } from '../../utils/drivingProfile'
import { chargeRhythmText, type RhythmFormat } from '../../utils/chargeRhythm'
import {
  rankingAnswers, sortByPriority, availablePriorities, PETROL_LITERS_PER_100KM, type RankingPriority, type Span,
} from '../../utils/rankingAnswers'
import type { NeedsInput } from '../../utils/needsCheck'
import type { PersonalRangeRankingEntry } from '../../api/personalRangeService'
import type { TopModelPreview } from '../../api/publicModelService'

const props = defineProps<{
  modelValue: NeedsInput
  /** the server's figures per model for the current answers */
  entries: PersonalRangeRankingEntry[]
  /** the list's models, for names, class and links of the top 5 */
  models: TopModelPreview[]
  /** the one electricity price of answers, top 5 and table, EUR/kWh */
  price: number
  /** guest: the top 5 wait behind the mail gate */
  locked: boolean
  petrolPricePerLiter: number | null
  modelHref: (m: TopModelPreview) => string
}>()
const emit = defineEmits<{ 'update:modelValue': [value: NeedsInput]; price: [value: number | null]; unlocked: [] }>()

const { t } = useI18n()
const { formatNumber, formatDecimal, formatDistance, formatCurrency, formatCostPerKwh } = useLocaleFormat()
/** ProfileTune tracks the wallbox answer with a model name; the list tracks as itself */
const TRACK_NAME = 'modelle'
const basisId = useId()
const basisOpen = ref(false)

const needs = computed(() => props.modelValue)
const home = computed(() => needs.value.homeCharging !== false)
const total = computed(() => props.entries.length || props.models.length)

// ── Questions ────────────────────────────────────────────────────────────────
const { sliders, summary } = useProfileSliders(() =>
  Object.fromEntries(PROFILE_KEYS.map(k => [k, needs.value[NEEDS_FIELD[k]]])) as ProfileValues)
function answer(key: ProfileKey, value: number) {
  emit('update:modelValue', { ...needs.value, [NEEDS_FIELD[key]]: value })
}
const priorities = computed(() => availablePriorities(props.entries))
function setPriority(value: RankingPriority) {
  emit('update:modelValue', { ...needs.value, priority: value })
  analytics.track('ranking_priority', { value })
}
function setHome(value: boolean) {
  emit('update:modelValue', { ...needs.value, homeCharging: value })
}
const anyAnswer = computed(() => PROFILE_KEYS.some(k => needs.value[NEEDS_FIELD[k]] != null))
/**
 * With a profile the questions fold behind the profile line. Decided on arrival and on
 * unlocking, never while the reader moves a slider, so the sliders do not fold away under the thumb.
 */
const isPhone = () => typeof window !== 'undefined' && !!window.matchMedia?.('(max-width: 767px)').matches
/** Guests on the phone too: five open sliders would push the answers out of sight */
const foldsOnArrival = () => anyAnswer.value && (!props.locked || isPhone())
const answered = ref(foldsOnArrival())
watch(() => props.locked, () => { answered.value = foldsOnArrival() })
const questionsOpen = ref(false)
const summaryLine = computed(() => [summary.value, t('profile.priority_summary', { value: t(`profile.priority_${needs.value.priority}`) })]
  .filter(Boolean).join(' · '))

// ── Answers: spans over all models ───────────────────────────────────────────
const hasCommute = computed(() => needs.value.dailyKm != null && needs.value.commuteDays != null)
const answers = computed(() => rankingAnswers(props.entries, {
  dailyKm: needs.value.dailyKm,
  longestTripKm: needs.value.longestTripKm,
  petrolPricePerLiter: props.petrolPricePerLiter,
}))
const km = (v: number) => formatDistance(v)
const euro = (v: number) => formatCurrency(v, { decimals: 0 })
const span = (s: Span, f: (v: number) => string) => s.lo === s.hi ? f(s.lo) : t('ranking_card.span', { lo: f(s.lo), hi: f(s.hi) })
const kmSpan = (s: Span) => span(s, km)
const euroSpan = (s: Span) => span(s, euro)
const tripKm = computed(() => needs.value.longestTripKm != null ? km(needs.value.longestTripKm) : '')

const commuteLine = computed(() => {
  const a = answers.value
  if (!a || a.commuteOk == null || needs.value.dailyKm == null) return ''
  return a.commuteOk === a.total
    ? t('ranking_card.commute_all', { km: km(needs.value.dailyKm), n: formatNumber(a.total) })
    : t('ranking_card.commute_some', { km: km(needs.value.dailyKm), ok: formatNumber(a.commuteOk), n: formatNumber(a.total) })
})
/** "alle 6 bis 10 Tage", from three weeks on in weeks */
const chargeText = computed(() => {
  const s = answers.value?.chargeEveryDays
  if (!hasCommute.value || !s) return ''
  if (s.hi > 21) {
    const lo = Math.max(1, Math.round(s.lo / 7)), hi = Math.round(s.hi / 7)
    return t('ranking_card.every_weeks', { lo: formatNumber(lo), hi: formatNumber(hi) })
  }
  return s.lo === s.hi
    ? t('ranking_card.every_days_one', { n: formatNumber(s.lo) }, s.lo)
    : t('ranking_card.every_days', { lo: formatNumber(s.lo), hi: formatNumber(s.hi) })
})
const chargeBasis = computed(() => t(home.value ? 'ranking_card.charge_basis_home' : 'ranking_card.charge_basis_road',
  { km: km((needs.value.dailyKm ?? 0) * (needs.value.commuteDays ?? 0)) }))
const stopsTimes = computed(() => {
  const s = answers.value?.stops
  if (!s) return ''
  if (s.lo === s.hi) return t('ranking_card.times_one', { n: formatNumber(s.hi) }, s.hi)
  return s.lo === 0 ? t('ranking_card.times_upto', { n: formatNumber(s.hi) }) : t('ranking_card.times_span', { lo: formatNumber(s.lo), hi: formatNumber(s.hi) })
})
const stopsWord = (n: number) => n === 0 ? t('models_ranking.row.stops_none') : t('models_ranking.row.stops_word', { count: formatNumber(n) }, n)
const fewestLine = computed(() => {
  const a = answers.value
  return a?.fewestStops != null ? t('ranking_card.fewest', { n: formatNumber(a.fewestStopsCount), stops: stopsWord(a.fewestStops) }) : ''
})
const showCost = computed(() => needs.value.annualKm != null || hasCommute.value)
const costBasis = computed(() => t(home.value ? 'ranking_card.cost_basis_home' : 'ranking_card.cost_basis_road',
  { km: km(props.entries[0]?.monthlyKm ?? 0), price: formatCostPerKwh(props.price) }))
/** Screen readers hear the new spans once the reply is in, not on every slider step */
const liveText = computed(() => answers.value
  ? t('ranking_card.winter', { winter: kmSpan(answers.value.winterKm), summer: kmSpan(answers.value.summerKm) })
  : '')

// ── Wallbox and price after the gate ─────────────────────────────────────────
const tuneInputs = computed(() => ({ ...needsToInputs(needs.value), pricePerKwh: null }))

// ── Gate ─────────────────────────────────────────────────────────────────────
const card = ref<InstanceType<typeof DrivingProfileCard> | null>(null)
const cardEl = computed(() => card.value?.root ?? null)
const gateBox = ref<HTMLElement | null>(null)
const gateOpen = ref(false)
const gateLead = computed(() => {
  const a = answers.value
  if (a?.fewestStops != null && tripKm.value) {
    return t('ranking_card.gate_lead_stops', { n: formatNumber(a.fewestStopsCount), km: tripKm.value, stops: stopsWord(a.fewestStops) })
  }
  return t('ranking_card.gate_lead', { n: formatNumber(total.value) })
})
function openGate() {
  if (gateOpen.value) return
  gateOpen.value = true
  analytics.track('ranking_gate_shown')
}
function fromBar() {
  openGate()
  gateBox.value?.scrollIntoView({ behavior: 'smooth', block: 'center' })
}
function onAuthenticated() {
  gateOpen.value = false
  emit('unlocked')
}
/** A row's "Meine Zahlen": back here, gate open */
function showGate() {
  openGate()
  gateBox.value?.scrollIntoView({ behavior: 'smooth', block: 'center' })
}
/** The hero card and the list point here: questions open and in view */
function focusQuestions() {
  questionsOpen.value = true
  cardEl.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}
defineExpose({ showGate, focusQuestions, root: cardEl })

// ── Top 5 ────────────────────────────────────────────────────────────────────
const rhythmFormat: RhythmFormat = { t: (k, p, c) => t(k, p ?? {}, c as number), formatDecimal }
const byKey = computed(() => new Map(props.models.map(m => [`${m.brand}/${m.model}`, m])))
const topFive = computed<TopFiveRow[]>(() => sortByPriority(props.entries, needs.value.priority)
  .filter(e => byKey.value.has(`${e.brand}/${e.model}`))
  .slice(0, 5)
  .map((e, i) => {
    const m = byKey.value.get(`${e.brand}/${e.model}`)!
    const summer = chargeRhythmText(e.chargesPerWeekSummer, home.value, rhythmFormat)
    const winter = chargeRhythmText(e.chargesPerWeekWinter, home.value, rhythmFormat)
    const drivers = m.contributorCount != null ? t('ranking_card.drivers', { n: formatNumber(m.contributorCount) }, m.contributorCount) : null
    return {
      key: `${e.brand}/${e.model}`,
      rank: i + 1,
      name: displayName(m),
      meta: [m.categoryDisplayName, drivers].filter(Boolean).join(' · '),
      href: props.modelHref(m),
      trip: { main: stopsWord(e.longTripStops), sub: e.longTripTotalMinutes != null ? t('personal_range.drive_time', { duration: duration(e.longTripTotalMinutes) }) : null },
      charge: { main: summer, sub: winter !== summer ? t('ranking_card.charge_winter', { value: winter }) : null },
      month: { main: euro(e.monthlyCostEur), sub: null },
    }
  }))
const topSub = computed(() => t(`ranking_card.top_sub_${needs.value.priority}`, { km: tripKm.value || km(PROFILE_SLIDERS.longTrip.start) }))
/** "Polestar 3", "Volkswagen ID.3": the brand once, as in the list rows */
function displayName(m: TopModelPreview): string {
  const name = m.modelUrlSlug.replace(/_/g, ' ')
  return name.toLowerCase().startsWith(m.brandDisplayName.toLowerCase()) ? name : `${m.brandDisplayName} ${name}`
}
function duration(minutes: number): string {
  const h = Math.floor(minutes / 60), m = minutes % 60
  if (h === 0) return t('personal_range.duration_m', { m })
  return m === 0 ? t('personal_range.duration_h', { h }) : t('personal_range.duration_h_m', { h, m })
}
</script>
