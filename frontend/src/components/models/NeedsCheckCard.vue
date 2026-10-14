<template>
  <section class="grid gap-4" :aria-labelledby="titleId">
    <!-- Single root (no comment before it, a root comment makes $el a comment node): the list
         header scrolls to $el. The five sliders of the model page, same wording and steps, the
         answers shared with it; below them one line per answer. Home charging comes after the gate. -->
    <h2 :id="titleId" class="text-[21px] font-bold tracking-tight text-gray-900 md:text-[24px] dark:text-gray-100">{{ t('models_ranking.needs.title') }}</h2>

    <p v-if="summary.total === 0" class="text-[14px] text-gray-700 dark:text-gray-300" data-testid="needs-summary">{{ t('models_ranking.needs.summary_empty') }}</p>

    <!-- Phone: answers already given fold the sliders into one line, "Ändern" opens them again -->
    <p v-if="collapsed" class="flex flex-wrap items-baseline gap-x-2 gap-y-1 text-[15px] leading-snug text-gray-900 md:hidden dark:text-gray-100" data-testid="needs-summary-line">
      <span class="font-semibold">{{ summaryLine }}</span>
      <button type="button" class="min-h-9 font-semibold text-green-700 underline-offset-4 hover:underline dark:text-green-400" data-testid="needs-expand" @click="collapsed = false">{{ t('models_ranking.needs.basis_change') }}</button>
    </p>
    <div class="grid items-end gap-x-6 gap-y-2 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5 xl:gap-x-5" :class="collapsed ? 'max-md:hidden' : ''">
      <ProfileSlider v-for="q in sliders" :key="q.key" :id="`${idBase}-${q.key}`" :test-key="`needs-${q.key}`" :label="q.label"
                     :min="q.min" :max="q.max" :step="q.step" :start="q.start" :value="q.value" :format="q.format" :detail="q.detail" compact
                     @commit="v => answer(q.key, v)" />
    </div>
    <button v-if="!collapsed && anyAnswer" type="button" class="-mt-2 inline-flex min-h-9 items-center gap-1 justify-self-start text-[14px] font-semibold text-green-700 md:hidden dark:text-green-400" data-testid="needs-collapse" @click="collapsed = true">
      <ChevronUpIcon class="h-4 w-4" aria-hidden="true" />{{ t('models_ranking.needs.collapse') }}
    </button>

    <div class="grid gap-3 border-t border-gray-200 pt-3.5 text-[14px] leading-snug dark:border-gray-800" aria-live="polite">
      <p v-if="!hasTrip && !hasCommute" class="text-gray-600 dark:text-gray-400" data-testid="needs-prompt">{{ t('models_ranking.needs.slider_prompt') }}</p>

      <!-- The trip: how many models make it with how many stops is free, which ones is not. Signed in
           a chip filters the list to its models (the active one shows all again); a guest's chip
           opens the gate, and once through the list filters to that chip. -->
      <div v-if="hasTrip && summary.total > 0" class="grid gap-2" role="group" :aria-labelledby="tripTitleId" data-testid="needs-trip-answer">
        <span :id="tripTitleId" class="font-semibold text-gray-900 dark:text-gray-100" data-testid="needs-trip-title">{{ t('models_ranking.needs.trip_title', { longest: longestShown, unit: distanceUnitLabel() }) }}</span>
        <div class="[scrollbar-width:none] [&::-webkit-scrollbar]:hidden -mx-4 flex items-center gap-2 overflow-x-auto px-4 py-0.5 sm:mx-0 sm:flex-wrap sm:overflow-visible sm:px-0">
        <button
          v-for="tile in stopTiles"
          :key="tile.stops"
          type="button"
          class="inline-flex min-h-9 flex-none items-center gap-1.5 whitespace-nowrap rounded-full px-3.5 text-[13.5px] font-medium disabled:cursor-not-allowed disabled:opacity-40"
          :class="tile.active ? 'bg-gray-900 text-white dark:bg-gray-100 dark:text-gray-900' : 'bg-gray-200/70 text-gray-800 hover:bg-gray-200 dark:bg-gray-800 dark:text-gray-200 dark:hover:bg-gray-700'"
          :aria-pressed="tile.active"
          :disabled="tile.count === 0"
          :data-testid="`needs-stops-${tile.stops}`"
          @click="pickStops(tile)"
        >
          {{ t('models_ranking.needs.trip_tile', { count: formatNumber(tile.stops) }, tile.stops) }}
          <b class="font-extrabold tabular-nums">{{ formatNumber(tile.count) }}</b>
        </button>
        <button v-if="tripOnly" type="button" class="min-h-9 flex-none whitespace-nowrap px-1 font-semibold text-green-700 underline-offset-4 hover:underline dark:text-green-400" data-testid="needs-show-all" @click="emit('trip', null)">{{ t('models_ranking.needs.show_all') }}</button>
        </div>
      </div>

      <!-- Guests: the questions the gate answers for every model, each with a blurred stand-in and the
           lock, as the model page lists its answers. Signed in, the wallbox question follows the commute. -->
      <div v-if="locked && hasAnswers" class="grid gap-2.5" data-testid="needs-locked">
        <p class="font-semibold text-gray-900 dark:text-gray-100">{{ t('models_ranking.needs.promise_title') }}</p>
        <div class="grid gap-3 md:grid-cols-[minmax(0,1fr)_auto] md:items-center md:gap-5">
          <ul class="grid grid-cols-3 gap-2">
            <li v-for="q in promises" :key="q.key" class="grid content-between gap-2 rounded-2xl bg-gray-100/80 p-3 dark:bg-gray-800/60">
              <span class="text-[12.5px] leading-snug text-gray-600 dark:text-gray-400">{{ q.text }}</span>
              <span class="flex items-center gap-1.5">
                <span class="select-none whitespace-nowrap text-[16px] font-extrabold leading-none tracking-tight text-gray-900 blur-[4px] md:text-[18px] dark:text-gray-100" aria-hidden="true">{{ q.dummy }}</span>
                <LockClosedIcon class="h-4 w-4 flex-none text-gray-500 dark:text-gray-400" aria-hidden="true" />
                <span class="sr-only">{{ t('models_ranking.row.locked_sr') }}</span>
              </span>
            </li>
          </ul>
          <button v-if="!gateOpen" type="button" class="btn-3d cta-shadow-sm inline-flex min-h-11 items-center justify-center justify-self-start rounded-xl border-2 border-gray-900 bg-green-600 px-4 text-[15px] font-bold text-white hover:bg-green-700 max-md:w-full dark:border-gray-200" data-testid="needs-unlock" @click="openGate">
            <LockOpenIcon class="mr-1.5 h-5 w-5" aria-hidden="true" />{{ t('models_ranking.needs.locked_cta') }}
          </button>
        </div>
      </div>
      <div v-if="!locked && hasCommute" class="flex flex-wrap items-center gap-x-3 gap-y-2" data-testid="needs-commute-answer">
        <span :id="homeId" class="font-semibold text-gray-900 dark:text-gray-100">{{ t('personal_range.wallbox_question') }}</span>
        <div :class="segmented" class="w-[150px] grid-cols-2" role="group" :aria-labelledby="homeId" data-testid="needs-home">
          <button
            v-for="opt in homeOptions"
            :key="String(opt.value)"
            type="button"
            class="text-sm font-semibold"
            :class="segmentClass(modelValue.homeCharging === opt.value)"
            :aria-pressed="modelValue.homeCharging === opt.value"
            @click="emit('update:modelValue', { ...modelValue, homeCharging: opt.value })"
          >{{ opt.label }}</button>
        </div>
        <span class="text-gray-600 dark:text-gray-400">{{ t('models_ranking.needs.commute_open') }}</span>
      </div>

      <!-- Guests: one gate for the whole list, the rows point back here -->
      <InlineGate v-if="locked && gateOpen" :lead="gateLead"
                  :detail="t('models_ranking.needs.gate_detail')" placement="modelle:rangliste" data-testid="needs-gate" @authenticated="onAuthenticated" />
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, useId } from 'vue'
import { useI18n } from 'vue-i18n'
import { ChevronUpIcon, LockClosedIcon, LockOpenIcon } from '@heroicons/vue/24/outline'
import InlineGate from '../auth/InlineGate.vue'
import ProfileSlider from './ProfileSlider.vue'
import { analytics } from '../../services/analytics'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import { NEEDS_FIELD, PROFILE_KEYS, PROFILE_SLIDERS, highwayPercent, type ProfileKey } from '../../composables/useDrivingProfile'
import { MAX_STOPS, type NeedsInput, type NeedsSummary } from '../../utils/needsCheck'

const props = defineProps<{
  modelValue: NeedsInput
  summary: NeedsSummary
  /** The list is filtered to the models that make the trip within `modelValue.maxStops` */
  tripOnly: boolean
  /** Guest: the per-model figures are behind the mail gate this card hosts */
  locked?: boolean
}>()
/** trip: filter the list to the models that make the trip within that many stops, null shows all */
const emit = defineEmits<{ 'update:modelValue': [value: NeedsInput]; trip: [maxStops: number | null]; unlocked: [] }>()

const { t } = useI18n()
const { formatNumber, formatDistance, distanceUnitLabel, convertDistance } = useLocaleFormat()

const titleId = useId()
const idBase = useId()
const tripTitleId = useId()
const homeId = useId()

/** The model page's sliders: same keys, steps, labels and formats; an unanswered one reads "noch offen" */
const FORMAT: Record<ProfileKey, (v: number) => string> = {
  annualKm: km => formatDistance(km),
  highwayKm: km => formatDistance(km),
  commute: km => formatDistance(km),
  commuteDays: n => t('model.page.days_value', { n }, n),
  longTrip: km => formatDistance(km),
}
const sliders = computed(() => PROFILE_KEYS.map(key => ({
  key,
  ...PROFILE_SLIDERS[key],
  // motorway km cannot exceed the yearly mileage once that is answered
  ...(key === 'highwayKm' && props.modelValue.annualKm != null ? { max: props.modelValue.annualKm } : {}),
  label: t(`model.page.q_${key}`),
  value: props.modelValue[NEEDS_FIELD[key]],
  format: FORMAT[key],
  detail: key === 'highwayKm' && props.modelValue.annualKm != null
    ? (km: number) => t('model.story.slider_highway_pct', { pct: formatNumber(highwayPercent(km, props.modelValue.annualKm!)) })
    : undefined,
})))
function answer(key: ProfileKey, value: number) {
  emit('update:modelValue', { ...props.modelValue, [NEEDS_FIELD[key]]: value })
}

/** Any slider answered, here or on the model page */
const anyAnswer = computed(() => PROFILE_KEYS.some(k => props.modelValue[NEEDS_FIELD[k]] != null))
/** Phone only (md:hidden / max-md:hidden): a reader who arrives with answers sees them as one line */
const collapsed = ref(anyAnswer.value)
const summaryLine = computed(() => {
  const v = props.modelValue
  const km = (x: number) => formatDistance(x)
  const parts: string[] = []
  if (v.annualKm != null) parts.push(t('models_ranking.needs.sum_annual', { km: km(v.annualKm) }))
  if (v.highwayKm != null) parts.push(t('models_ranking.needs.sum_highway', { km: km(v.highwayKm) }))
  if (v.dailyKm != null) parts.push(v.commuteDays != null
    ? t('models_ranking.needs.sum_commute', { km: km(v.dailyKm), days: t('model.page.days_value', { n: v.commuteDays }, v.commuteDays) })
    : t('models_ranking.needs.sum_commute_km', { km: km(v.dailyKm) }))
  if (v.longestTripKm != null) parts.push(t('models_ranking.needs.sum_trip', { km: km(v.longestTripKm) }))
  return parts.join(' · ')
})

/** "Ändern" in the sticky list header brings the reader back here: the first open slider, else the first */
async function focusFirst() {
  collapsed.value = false
  await nextTick()
  const key = PROFILE_KEYS.find(k => props.modelValue[NEEDS_FIELD[k]] == null) ?? PROFILE_KEYS[0]
  document.getElementById(`${idBase}-${key}-input`)?.focus()
}
/** Mail gate for the figures per row; a row's unlock button opens it from afar via openGate() */
const gateOpen = ref(false)
/**
 * The stops of the chip a guest tapped: the gate names its models, after it the list filters to them.
 * The count is read live, so figures arriving after the tap (a changed trip) correct the gate.
 */
const gateStops = ref<number | null>(null)
function openGate() {
  if (gateOpen.value) return
  gateOpen.value = true
  analytics.track('ranking_gate_shown')
}
function onAuthenticated() {
  gateOpen.value = false
  emit('unlocked')
  if (gateStops.value != null) emit('trip', gateStops.value)
  gateStops.value = null
}
function pickStops(tile: { stops: number; count: number; active: boolean }) {
  if (!props.locked) return emit('trip', tile.active ? null : tile.stops)
  gateStops.value = tile.stops
  openGate()
}
const gateLead = computed(() => {
  const n = formatNumber(props.summary.total)
  if (gateStops.value != null) {
    const count = props.summary.tripOkWithin[gateStops.value] ?? 0
    return t('models_ranking.needs.gate_lead_stops', { n: formatNumber(count), longest: longestShown.value, unit: distanceUnitLabel() }, count)
  }
  return hasTrip.value
    ? t('models_ranking.needs.gate_lead', { n, longest: longestShown.value, unit: distanceUnitLabel() })
    : t('models_ranking.needs.gate_lead_commute', { n })
})
defineExpose({ focusFirst, openGate })

/** The trip length in the copy, in the market's unit with thousands separators */
const longestShown = computed(() => props.modelValue.longestTripKm == null ? '' : formatNumber(Math.round(convertDistance(props.modelValue.longestTripKm))))

/** The longest trip alone gives the stop chips; the commute needs km and days (home charging counts until answered) */
const hasTrip = computed(() => props.modelValue.longestTripKm != null)
const hasCommute = computed(() => props.modelValue.dailyKm != null && props.modelValue.commuteDays != null)
/** Any slider the list answers with: commute, trip, or the yearly mileage the monthly cost rests on */
const hasAnswers = computed(() => hasTrip.value || hasCommute.value || props.modelValue.annualKm != null)

/** What the gate answers for every model: how often to charge, stops on the trip, the month's cost */
const promises = computed(() => [
  { key: 'charge', text: t('models_ranking.needs.promise_charge'), dummy: t('models_ranking.needs.promise_charge_dummy') },
  {
    key: 'stops',
    text: hasTrip.value
      ? t('models_ranking.needs.promise_stops', { longest: longestShown.value, unit: distanceUnitLabel() })
      : t('models_ranking.needs.promise_stops_open'),
    dummy: t('models_ranking.needs.promise_stops_dummy'),
  },
  { key: 'cost', text: t('models_ranking.needs.promise_cost'), dummy: t('models_ranking.needs.promise_cost_dummy') },
])

/** At most five chips: from one before the first count above zero (the "none yet" context) up to the
    first that every model reaches, more stops add nothing */
const MAX_CHIPS = 5
const stopTiles = computed(() => {
  const within = (stops: number) => props.summary.tripOkWithin[stops] ?? 0
  let first = 0
  while (first < MAX_STOPS && within(first) === 0) first++
  const tiles: { stops: number; count: number; active: boolean }[] = []
  for (let stops = Math.max(0, first - 1); stops <= MAX_STOPS && tiles.length < MAX_CHIPS; stops++) {
    const count = within(stops)
    tiles.push({ stops, count, active: props.tripOnly && props.modelValue.maxStops === stops })
    if (count >= props.summary.total) break
  }
  return tiles
})

const segmented = 'edge-card grid h-9 gap-0.5 overflow-hidden rounded-xl bg-gray-900 dark:bg-gray-200'
const segmentClass = (active: boolean) => active
  ? 'bg-gray-900 text-white dark:bg-gray-100 dark:text-gray-900'
  : 'bg-white text-gray-800 hover:bg-gray-50 dark:bg-gray-900 dark:text-gray-200 dark:hover:bg-gray-800'
const homeOptions = computed(() => [
  { value: true, label: t('personal_range.wallbox_yes') },
  { value: false, label: t('personal_range.wallbox_no') },
])
</script>
