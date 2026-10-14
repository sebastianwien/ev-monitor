<template>
  <section class="grid gap-4" :aria-labelledby="titleId">
    <!-- Single root (no comment before it, a root comment makes $el a comment node): the list
         header scrolls to $el. The five sliders of the model page, same wording and steps, the
         answers shared with it; below them one line per answer. Home charging comes after the gate. -->
    <h2 :id="titleId" class="text-[21px] font-bold tracking-tight text-gray-900 md:text-[24px] dark:text-gray-100">{{ t('models_ranking.needs.title') }}</h2>

    <p v-if="summary.total === 0" class="text-[14px] text-gray-700 dark:text-gray-300" data-testid="needs-summary">{{ t('models_ranking.needs.summary_empty') }}</p>

    <div class="grid items-end gap-x-6 gap-y-2 sm:grid-cols-2 lg:grid-cols-3">
      <ProfileSlider v-for="q in sliders" :key="q.key" :id="`${idBase}-${q.key}`" :test-key="`needs-${q.key}`" :label="q.label"
                     :min="q.min" :max="q.max" :step="q.step" :start="q.start" :value="q.value" :format="q.format" :detail="q.detail"
                     @commit="v => answer(q.key, v)" />
    </div>

    <div class="grid gap-3 border-t border-gray-200 pt-3.5 text-[14px] leading-snug dark:border-gray-800" aria-live="polite">
      <p v-if="!hasTrip && !hasCommute" class="text-gray-600 dark:text-gray-400" data-testid="needs-prompt">{{ t('models_ranking.needs.slider_prompt') }}</p>

      <!-- The trip: how many models make it with how many stops is free, which ones is not. Signed in
           a chip filters the list to its models (the active one shows all again); a guest's chip
           opens the gate, and once through the list filters to that chip. -->
      <div v-if="hasTrip && summary.total > 0" class="flex flex-wrap items-center gap-2" role="group" :aria-labelledby="tripTitleId" data-testid="needs-trip-answer">
        <span :id="tripTitleId" class="basis-full font-semibold text-gray-900 dark:text-gray-100" data-testid="needs-trip-title">{{ t('models_ranking.needs.trip_title', { longest: longestShown, unit: distanceUnitLabel() }) }}</span>
        <button
          v-for="tile in stopTiles"
          :key="tile.stops"
          type="button"
          class="btn-3d cta-shadow-sm inline-flex min-h-9 items-center gap-1.5 whitespace-nowrap rounded-full border-2 border-gray-900 px-3 text-[13px] font-semibold disabled:cursor-not-allowed disabled:opacity-45 dark:border-gray-200"
          :class="tile.active ? 'bg-gray-900 text-white dark:bg-gray-100 dark:text-gray-900' : 'bg-white text-gray-800 dark:bg-gray-900 dark:text-gray-200'"
          :aria-pressed="tile.active"
          :disabled="tile.count === 0"
          :data-testid="`needs-stops-${tile.stops}`"
          @click="pickStops(tile)"
        >
          {{ t('models_ranking.needs.trip_tile', { count: formatNumber(tile.stops) }, tile.stops) }}
          <b class="font-extrabold tabular-nums">{{ formatNumber(tile.count) }}</b>
        </button>
        <button v-if="tripOnly" type="button" class="min-h-9 px-1 font-semibold text-green-700 underline-offset-4 hover:underline dark:text-green-400" data-testid="needs-show-all" @click="emit('trip', null)">{{ t('models_ranking.needs.show_all') }}</button>
      </div>

      <!-- The commute: guests see where the figure will stand, signed in the wallbox question
           follows, as on the model page; until it is answered the figures count home charging -->
      <div v-if="hasCommute && locked" class="flex flex-wrap items-center gap-x-3 gap-y-2" data-testid="needs-commute-answer">
        <span class="flex min-w-0 items-center gap-1.5 text-gray-900 dark:text-gray-100" data-testid="needs-locked">
          <span class="flex-none font-semibold">{{ t('models_ranking.row.commute_label') }}</span>
          <span class="min-w-0 select-none truncate blur-[3px]" aria-hidden="true">{{ t('models_ranking.row.locked_dummy') }}</span>
          <LockClosedIcon class="h-4 w-4 flex-none text-gray-500 dark:text-gray-400" aria-hidden="true" />
          <span class="sr-only">{{ t('models_ranking.row.locked_sr') }}</span>
        </span>
        <button v-if="!gateOpen" type="button" class="btn-3d cta-shadow-sm inline-flex min-h-10 items-center rounded-xl border-2 border-gray-900 bg-green-600 px-3.5 text-[14px] font-bold text-white hover:bg-green-700 dark:border-gray-200" data-testid="needs-unlock" @click="openGate">
          <LockOpenIcon class="mr-1.5 h-5 w-5" aria-hidden="true" />{{ t('models_ranking.needs.locked_cta') }}
        </button>
      </div>
      <div v-else-if="hasCommute" class="flex flex-wrap items-center gap-x-3 gap-y-2" data-testid="needs-commute-answer">
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
import { computed, ref, useId } from 'vue'
import { useI18n } from 'vue-i18n'
import { LockClosedIcon, LockOpenIcon } from '@heroicons/vue/24/outline'
import InlineGate from '../auth/InlineGate.vue'
import ProfileSlider from './ProfileSlider.vue'
import { analytics } from '../../services/analytics'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import { NEEDS_FIELD, PROFILE_KEYS, PROFILE_SLIDERS, type ProfileKey } from '../../composables/useDrivingProfile'
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
  highwayShare: share => `${formatNumber(share)} %`,
  commute: km => formatDistance(km),
  commuteDays: n => t('model.page.days_value', { n }, n),
  longTrip: km => formatDistance(km),
}
const highwayKm = (share: number) => formatDistance(Math.round((props.modelValue.annualKm ?? PROFILE_SLIDERS.annualKm.start) * share / 100 / 100) * 100)
const sliders = computed(() => PROFILE_KEYS.map(key => ({
  key,
  ...PROFILE_SLIDERS[key],
  label: t(`model.page.q_${key}`),
  value: props.modelValue[NEEDS_FIELD[key]],
  format: FORMAT[key],
  detail: key === 'highwayShare' ? (share: number) => t('model.story.slider_highway_km', { km: highwayKm(share) }) : undefined,
})))
function answer(key: ProfileKey, value: number) {
  emit('update:modelValue', { ...props.modelValue, [NEEDS_FIELD[key]]: value })
}

/** "Ändern" in the sticky list header brings the reader back here: the first open slider, else the first */
function focusFirst() {
  const key = PROFILE_KEYS.find(k => props.modelValue[NEEDS_FIELD[k]] == null) ?? PROFILE_KEYS[0]
  document.getElementById(`${idBase}-${key}-input`)?.focus()
}
/** Mail gate for the figures per row; a row's unlock button opens it from afar via openGate() */
const gateOpen = ref(false)
/** The stop chip a guest tapped: the gate names its models, after it the list filters to them */
const gateStops = ref<{ stops: number; count: number } | null>(null)
function openGate() {
  if (gateOpen.value) return
  gateOpen.value = true
  analytics.track('ranking_gate_shown')
}
function onAuthenticated() {
  gateOpen.value = false
  emit('unlocked')
  if (gateStops.value) emit('trip', gateStops.value.stops)
  gateStops.value = null
}
function pickStops(tile: { stops: number; count: number; active: boolean }) {
  if (!props.locked) return emit('trip', tile.active ? null : tile.stops)
  gateStops.value = { stops: tile.stops, count: tile.count }
  openGate()
}
const gateLead = computed(() => {
  const n = formatNumber(props.summary.total)
  if (gateStops.value) {
    const count = gateStops.value.count
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

/** One chip per stop count, up to the first that every model reaches: more stops add nothing */
const stopTiles = computed(() => {
  const tiles: { stops: number; count: number; active: boolean }[] = []
  for (let stops = 0; stops <= MAX_STOPS; stops++) {
    const count = props.summary.tripOkWithin[stops] ?? 0
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
