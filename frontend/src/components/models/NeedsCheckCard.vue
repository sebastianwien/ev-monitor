<template>
  <section class="grid gap-4" :aria-labelledby="titleId">
    <!-- Single root (no comment before it, a root comment makes $el a comment node): the list
         header scrolls to $el. Four labelled fields, below them three figures the rows follow. -->
    <h2 :id="titleId" class="text-[15px] font-semibold tracking-tight text-gray-900 dark:text-gray-100">{{ t('models_ranking.needs.title') }}</h2>

    <div class="grid grid-cols-2 gap-x-3 gap-y-3 sm:grid-cols-4">
      <label class="grid gap-1">
        <span class="text-[12.5px] text-gray-500 dark:text-gray-400">{{ t('models_ranking.needs.daily', { unit: distanceUnitLabel() }) }}</span>
        <input ref="dailyInput" :value="dailyLocal" v-bind="numeric" data-testid="needs-daily" @input="onDistance('dailyKm', $event)" />
      </label>
      <label class="grid gap-1">
        <span class="text-[12.5px] text-gray-500 dark:text-gray-400">{{ t('models_ranking.needs.longest', { unit: distanceUnitLabel() }) }}</span>
        <input :value="longestLocal" v-bind="numeric" data-testid="needs-longest" @input="onDistance('longestTripKm', $event)" />
      </label>
      <div class="grid gap-1">
        <span :id="homeId" class="text-[12.5px] text-gray-500 dark:text-gray-400">{{ t('models_ranking.needs.home') }}</span>
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
      <label class="grid gap-1">
        <span class="text-[12.5px] text-gray-500 dark:text-gray-400">{{ t('models_ranking.needs.stop') }}</span>
        <input :value="String(modelValue.stopMinutes)" v-bind="numeric" data-testid="needs-stop" @change="onMinutes($event)" />
      </label>
    </div>

    <!-- The answer as three figures; the middle one filters the list to the models it counts -->
    <p v-if="summary.total === 0" class="text-[14px] text-gray-700 dark:text-gray-300" data-testid="needs-summary">{{ t('models_ranking.needs.summary_empty') }}</p>
    <dl v-else class="grid grid-cols-3 divide-x divide-gray-200 dark:divide-gray-700" aria-live="polite" data-testid="needs-summary">
      <div class="grid content-start gap-0.5 pr-3">
        <dd class="text-[22px] font-semibold leading-tight tracking-tight tabular-nums text-gray-900 dark:text-gray-100">{{ t('models_ranking.needs.stat_of', { n: formatNumber(summary.weeklyOk), total: formatNumber(summary.total) }) }}</dd>
        <dt class="text-[12.5px] leading-snug text-gray-600 dark:text-gray-400">{{ t(modelValue.homeCharging ? 'models_ranking.needs.stat_weekly_home' : 'models_ranking.needs.stat_weekly_public', { daily: dailyLocal, unit: distanceUnitLabel() }) }}</dt>
      </div>
      <div class="grid content-start gap-0.5 px-3">
        <dd>
          <button
            type="button"
            class="-mx-1 -my-0.5 inline-flex items-center gap-1 rounded-md px-1 py-0.5 text-[22px] font-semibold leading-tight tracking-tight tabular-nums hover:bg-gray-100 dark:hover:bg-gray-800"
            :class="tripOnly ? 'text-green-700 dark:text-green-400' : 'text-gray-900 dark:text-gray-100'"
            :aria-pressed="tripOnly"
            :aria-label="t('models_ranking.needs.show_models', { count: formatNumber(summary.tripOk) }, summary.tripOk)"
            data-testid="needs-show-models"
            @click="emit('show')"
          >
            {{ formatNumber(summary.tripOk) }}
            <FunnelIcon class="h-4 w-4 text-green-700 dark:text-green-400" aria-hidden="true" />
          </button>
        </dd>
        <dt class="text-[12.5px] leading-snug text-gray-600 dark:text-gray-400">{{ t('models_ranking.needs.stat_trip', { longest: longestLocal, unit: distanceUnitLabel() }) }}</dt>
      </div>
      <div class="grid content-start gap-0.5 pl-3">
        <dd class="text-[22px] font-semibold leading-tight tracking-tight tabular-nums text-gray-900 dark:text-gray-100">{{ formatNumber(summary.tripOneStopOk) }}</dd>
        <dt class="text-[12.5px] leading-snug text-gray-600 dark:text-gray-400">{{ t('models_ranking.needs.stat_one_stop', { minutes: formatNumber(modelValue.stopMinutes) }) }}</dt>
      </div>
    </dl>
    <p class="text-[12px] text-gray-500 dark:text-gray-400">{{ t('models_ranking.needs.assumptions') }}</p>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, useId } from 'vue'
import { useI18n } from 'vue-i18n'
import { FunnelIcon } from '@heroicons/vue/24/outline'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import { odometerLocalToKm } from '../../utils/unitConversions'
import { NEEDS_KM_MAX } from '../../composables/useModelRanking'
import { ONE_STOP_MINUTES, STOP_MINUTES_MIN, STOP_MINUTES_MAX, type NeedsInput, type NeedsSummary } from '../../utils/needsCheck'

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
  class: 'h-11 w-full rounded-xl border border-gray-300 bg-white px-3 text-base font-semibold tabular-nums text-gray-900 focus:border-green-600 focus:outline-none focus:ring-2 focus:ring-green-600/30 dark:border-gray-600 dark:bg-gray-950 dark:text-gray-100',
} as const

// Inputs show the market's distance unit; the model keeps kilometres.
const toLocal = (km: number) => String(Math.round(convertDistance(km)))
const dailyLocal = computed(() => toLocal(props.modelValue.dailyKm))
const longestLocal = computed(() => toLocal(props.modelValue.longestTripKm))

function onDistance(field: 'dailyKm' | 'longestTripKm', event: Event) {
  const raw = (event.target as HTMLInputElement).value.replace(/[^\d]/g, '')
  const local = raw === '' ? 0 : Math.min(Number(raw), NEEDS_KM_MAX)
  const km = Math.round(odometerLocalToKm(local, isImperial.value))
  emit('update:modelValue', { ...props.modelValue, [field]: Math.min(km, NEEDS_KM_MAX) })
}

// Stop length: typed on change (not on every keystroke, "2" on the way to "25" would be clamped), clamped to the sensible window
function onMinutes(event: Event) {
  const input = event.target as HTMLInputElement
  const raw = input.value.replace(/[^\d]/g, '')
  const minutes = raw === '' ? ONE_STOP_MINUTES : Math.min(STOP_MINUTES_MAX, Math.max(STOP_MINUTES_MIN, Number(raw)))
  input.value = String(minutes)
  emit('update:modelValue', { ...props.modelValue, stopMinutes: minutes })
}

const homeOptions = computed(() => [
  { value: true, label: t('models_ranking.needs.home_yes') },
  { value: false, label: t('models_ranking.needs.home_no') },
])
</script>
