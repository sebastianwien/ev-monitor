<template>
  <section class="grid gap-4" :aria-labelledby="titleId">
    <!-- Single root (no comment before it, a root comment makes $el a comment node): the list
         header scrolls to $el. Four labelled fields, below them three figures the rows follow. -->
    <h2 :id="titleId" class="text-[15px] font-semibold tracking-tight text-gray-900 dark:text-gray-100">{{ t('models_ranking.needs.title') }}</h2>

    <!-- Four questions; inputs line up at the bottom even when a question wraps differently -->
    <div class="grid grid-cols-2 items-end gap-x-3 gap-y-3 sm:grid-cols-4">
      <label class="grid gap-1.5">
        <span class="text-[13px] leading-snug text-gray-700 dark:text-gray-300">{{ t('models_ranking.needs.q_daily') }}</span>
        <span class="flex items-center gap-2">
          <input ref="dailyInput" :value="dailyText" v-bind="numeric" data-testid="needs-daily" @input="onDistance('dailyKm', $event)" @blur="flush" @keydown.enter="flush" />
          <span class="text-sm text-gray-500 dark:text-gray-400">{{ distanceUnitLabel() }}</span>
        </span>
      </label>
      <label class="grid gap-1.5">
        <span class="text-[13px] leading-snug text-gray-700 dark:text-gray-300">{{ t('models_ranking.needs.q_longest') }}</span>
        <span class="flex items-center gap-2">
          <input :value="longestText" v-bind="numeric" data-testid="needs-longest" @input="onDistance('longestTripKm', $event)" @blur="flush" @keydown.enter="flush" />
          <span class="text-sm text-gray-500 dark:text-gray-400">{{ distanceUnitLabel() }}</span>
        </span>
      </label>
      <div class="grid gap-1.5">
        <span :id="homeId" class="text-[13px] leading-snug text-gray-700 dark:text-gray-300">{{ t('models_ranking.needs.q_home') }}</span>
        <div class="grid h-11 grid-cols-2 gap-px overflow-hidden rounded-xl border border-gray-300 bg-gray-300 dark:border-gray-600 dark:bg-gray-600" role="group" :aria-labelledby="homeId">
          <button
            v-for="opt in homeOptions"
            :key="String(opt.value)"
            type="button"
            class="text-sm font-semibold"
            :class="modelValue.homeCharging === opt.value
              ? 'bg-gray-900 text-white dark:bg-gray-100 dark:text-gray-900'
              : 'bg-white text-gray-800 hover:bg-gray-50 dark:bg-gray-900 dark:text-gray-200 dark:hover:bg-gray-800'"
            :aria-pressed="modelValue.homeCharging === opt.value"
            @click="emit('update:modelValue', { ...modelValue, homeCharging: opt.value })"
          >{{ opt.label }}</button>
        </div>
      </div>
      <div class="grid gap-1.5">
        <span :id="stopsId" class="text-[13px] leading-snug text-gray-700 dark:text-gray-300">{{ t('models_ranking.needs.q_stops') }}</span>
        <div class="grid h-11 grid-cols-4 gap-px overflow-hidden rounded-xl border border-gray-300 bg-gray-300 dark:border-gray-600 dark:bg-gray-600" role="group" :aria-labelledby="stopsId">
          <button
            v-for="n in stopOptions"
            :key="n"
            type="button"
            class="text-sm font-semibold"
            :class="modelValue.maxStops === n
              ? 'bg-gray-900 text-white dark:bg-gray-100 dark:text-gray-900'
              : 'bg-white text-gray-800 hover:bg-gray-50 dark:bg-gray-900 dark:text-gray-200 dark:hover:bg-gray-800'"
            :aria-pressed="modelValue.maxStops === n"
            :data-testid="`needs-stops-${n}`"
            @click="emit('update:modelValue', { ...modelValue, maxStops: n })"
          >{{ formatNumber(n) }}</button>
        </div>
      </div>
    </div>

    <!-- The answer in one line; the button narrows the list to the models it counts -->
    <p v-if="summary.total === 0" class="text-[14px] text-gray-700 dark:text-gray-300" data-testid="needs-summary">{{ t('models_ranking.needs.summary_empty') }}</p>
    <p v-else class="flex flex-wrap items-center gap-x-3 gap-y-1.5 text-[14px] leading-snug text-gray-700 dark:text-gray-300" aria-live="polite" data-testid="needs-summary">
      <span>
        <span class="text-[22px] font-semibold leading-tight tracking-tight tabular-nums text-gray-900 dark:text-gray-100">{{ t('models_ranking.needs.stat_of', { n: formatNumber(within), total: formatNumber(summary.total) }) }}</span>
        {{ t('models_ranking.needs.stat_trip', { longest: longestLocal, unit: distanceUnitLabel(), count: formatNumber(modelValue.maxStops) }, modelValue.maxStops) }}
      </span>
      <button
        type="button"
        class="inline-flex min-h-9 items-center gap-1.5 rounded-lg border px-3 text-[13px] font-semibold"
        :class="tripOnly
          ? 'border-green-700 bg-green-700 text-white dark:border-green-400 dark:bg-green-400 dark:text-gray-950'
          : 'border-gray-300 bg-white text-green-700 hover:bg-gray-50 dark:border-gray-600 dark:bg-gray-900 dark:text-green-400 dark:hover:bg-gray-800'"
        :aria-pressed="tripOnly"
        data-testid="needs-show-models"
        @click="emit('show')"
      >
        <FunnelIcon class="h-4 w-4" aria-hidden="true" />
        {{ t(tripOnly ? 'models_ranking.needs.show_all' : 'models_ranking.needs.show_only') }}
      </button>
    </p>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, useId, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { FunnelIcon } from '@heroicons/vue/24/outline'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import { useDebouncedCommit } from '../../composables/useDebouncedCommit'
import { odometerLocalToKm } from '../../utils/unitConversions'
import { NEEDS_KM_MAX } from '../../composables/useModelRanking'
import { MIN_STOPS, MAX_STOPS, type NeedsInput, type NeedsSummary } from '../../utils/needsCheck'

const props = defineProps<{
  modelValue: NeedsInput
  summary: NeedsSummary
  /** The list is filtered to the models that make the trip without a stop */
  tripOnly: boolean
}>()
const emit = defineEmits<{ 'update:modelValue': [value: NeedsInput]; show: [] }>()

const { t } = useI18n()
const { formatNumber, distanceUnitLabel, convertDistance, isImperial } = useLocaleFormat()

const titleId = useId()
const homeId = useId()
const stopsId = useId()
const dailyInput = ref<HTMLInputElement | null>(null)
/** "Ändern" in the sticky list header brings the reader back here */
function focusFirst() {
  dailyInput.value?.focus()
  dailyInput.value?.select()
}
defineExpose({ focusFirst })

const numeric = {
  type: 'text',
  inputmode: 'numeric',
  pattern: '[0-9]*',
  autocomplete: 'off',
  class: 'h-11 w-full min-w-0 rounded-xl border border-gray-300 bg-white px-3 text-base font-semibold tabular-nums text-gray-900 focus:border-green-600 focus:outline-none focus:ring-2 focus:ring-green-600/30 dark:border-gray-600 dark:bg-gray-950 dark:text-gray-100',
} as const

// Inputs show the market's distance unit; the model keeps kilometres. The committed values
// feed the figures below; the fields keep their own text while the reader is still typing.
const toLocal = (km: number) => String(Math.round(convertDistance(km)))
const dailyLocal = computed(() => toLocal(props.modelValue.dailyKm))
const longestLocal = computed(() => toLocal(props.modelValue.longestTripKm))
const dailyText = ref(dailyLocal.value)
const longestText = ref(longestLocal.value)
watch(dailyLocal, v => { dailyText.value = v })
watch(longestLocal, v => { longestText.value = v })

/** Typing "120" re-ranks the list once, not three times: commit after a short pause, or on blur/enter */
const COMMIT_DELAY_MS = 300
let draft: Partial<NeedsInput> = {}
const { set: scheduleCommit, flush } = useDebouncedCommit<Partial<NeedsInput>>(patch => {
  draft = {}
  emit('update:modelValue', { ...props.modelValue, ...patch })
  if (patch.dailyKm !== undefined) dailyText.value = toLocal(patch.dailyKm)
  if (patch.longestTripKm !== undefined) longestText.value = toLocal(patch.longestTripKm)
}, COMMIT_DELAY_MS)

function onDistance(field: 'dailyKm' | 'longestTripKm', event: Event) {
  const raw = (event.target as HTMLInputElement).value.replace(/[^\d]/g, '')
  ;(field === 'dailyKm' ? dailyText : longestText).value = raw
  const local = raw === '' ? 0 : Math.min(Number(raw), NEEDS_KM_MAX)
  const km = Math.round(odometerLocalToKm(local, isImperial.value))
  draft = { ...draft, [field]: Math.min(km, NEEDS_KM_MAX) }
  scheduleCommit({ ...draft })
}

const stopOptions = Array.from({ length: MAX_STOPS - MIN_STOPS + 1 }, (_, i) => MIN_STOPS + i)
/** Models that make the trip with at most the chosen number of stops */
const within = computed(() => props.summary.tripOkWithin[props.modelValue.maxStops] ?? 0)

const homeOptions = computed(() => [
  { value: true, label: t('models_ranking.needs.home_yes') },
  { value: false, label: t('models_ranking.needs.home_no') },
])
</script>
