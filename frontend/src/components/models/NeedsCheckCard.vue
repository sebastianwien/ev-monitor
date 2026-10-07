<template>
  <section class="grid gap-3" :aria-labelledby="titleId">
    <!-- Single root (no comment before it, a root comment makes $el a comment node): the list
         header scrolls to $el. One sentence with the three inputs inside, right above the rows. -->
    <h2 :id="titleId" class="text-[15px] font-semibold tracking-tight text-gray-900 dark:text-gray-100">{{ t('models_ranking.needs.title') }}</h2>

    <p class="flex flex-wrap items-center gap-x-1.5 gap-y-2 text-[15px] leading-snug text-gray-900 dark:text-gray-100">
      <label class="contents">
        <span>{{ t('models_ranking.needs.sentence_daily') }}</span>
        <input
          ref="dailyInput"
          :value="dailyLocal"
          v-bind="numeric"
          data-testid="needs-daily"
          @input="onDistance('dailyKm', $event)"
        />
        <span>{{ t('models_ranking.needs.sentence_daily_unit', { unit: distanceUnitLabel() }) }}</span>
      </label>
      <label class="contents">
        <span>{{ t('models_ranking.needs.sentence_longest') }}</span>
        <input
          :value="longestLocal"
          v-bind="numeric"
          data-testid="needs-longest"
          @input="onDistance('longestTripKm', $event)"
        />
        <span>{{ t('models_ranking.needs.sentence_longest_unit', { unit: distanceUnitLabel() }) }}</span>
      </label>
      <span :id="homeId">{{ t('models_ranking.needs.sentence_home') }}</span>
      <span class="inline-grid grid-cols-2 gap-px overflow-hidden rounded-lg border border-gray-300 bg-gray-300 dark:border-gray-600 dark:bg-gray-600" role="group" :aria-labelledby="homeId">
        <button
          v-for="opt in homeOptions"
          :key="String(opt.value)"
          type="button"
          class="min-h-10 px-3.5 text-sm font-semibold"
          :class="modelValue.homeCharging === opt.value
            ? 'bg-gray-900 text-white dark:bg-gray-100 dark:text-gray-900'
            : 'bg-white text-gray-800 hover:bg-gray-50 dark:bg-gray-900 dark:text-gray-200 dark:hover:bg-gray-800'"
          :aria-pressed="modelValue.homeCharging === opt.value"
          @click="emit('update:modelValue', { ...modelValue, homeCharging: opt.value })"
        >{{ opt.label }}</button>
      </span>
    </p>

    <p class="flex flex-wrap items-baseline gap-x-3 gap-y-1 text-[14px] leading-snug text-gray-700 dark:text-gray-300">
      <span aria-live="polite" data-testid="needs-summary">{{ summaryText }}</span>
      <button
        v-if="summary.tripOk > 0"
        type="button"
        class="inline-flex min-h-8 items-center gap-1 font-semibold text-green-700 underline-offset-4 hover:underline dark:text-green-400"
        data-testid="needs-show-models"
        @click="emit('show')"
      >
        {{ t('models_ranking.needs.show_models', { count: formatNumber(summary.tripOk) }, summary.tripOk) }}
        <ArrowDownIcon class="h-3.5 w-3.5" aria-hidden="true" />
      </button>
    </p>
    <p class="text-[12px] text-gray-500 dark:text-gray-400">{{ t('models_ranking.needs.assumptions') }}</p>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, useId } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowDownIcon } from '@heroicons/vue/24/outline'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import { odometerLocalToKm } from '../../utils/unitConversions'
import { NEEDS_KM_MAX } from '../../composables/useModelRanking'
import type { NeedsInput, NeedsSummary } from '../../utils/needsCheck'

const props = defineProps<{
  modelValue: NeedsInput
  summary: NeedsSummary
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
  class: 'h-10 w-[72px] rounded-lg border border-gray-300 bg-white px-2.5 text-center text-base font-semibold tabular-nums text-gray-900 focus:border-green-600 focus:outline-none focus:ring-2 focus:ring-green-600/30 dark:border-gray-600 dark:bg-gray-950 dark:text-gray-100',
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

const homeOptions = computed(() => [
  { value: true, label: t('models_ranking.needs.home_yes') },
  { value: false, label: t('models_ranking.needs.home_no') },
])

const summaryText = computed(() => {
  const s = props.summary
  if (s.total === 0) return t('models_ranking.needs.summary_empty')
  return t(props.modelValue.homeCharging ? 'models_ranking.needs.summary_home' : 'models_ranking.needs.summary_public', {
    daily: dailyLocal.value,
    longest: longestLocal.value,
    unit: distanceUnitLabel(),
    weekly: formatNumber(s.weeklyOk),
    total: formatNumber(s.total),
    trip: formatNumber(s.tripOk),
  })
})
</script>
