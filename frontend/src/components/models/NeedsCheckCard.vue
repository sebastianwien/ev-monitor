<template>
  <section class="grid gap-3.5 rounded-2xl border border-gray-200 bg-white p-4 dark:border-gray-800 dark:bg-gray-900" :aria-labelledby="titleId">
    <h2 :id="titleId" class="text-[17px] font-bold tracking-tight text-gray-900 dark:text-gray-100">{{ t('models_ranking.needs.title') }}</h2>

    <div class="grid grid-cols-2 gap-3">
      <label class="grid gap-1">
        <span class="text-[12.5px] text-gray-500 dark:text-gray-400">{{ t('models_ranking.needs.daily', { unit: distanceUnitLabel() }) }}</span>
        <input
          :value="dailyLocal"
          type="text"
          inputmode="numeric"
          pattern="[0-9]*"
          autocomplete="off"
          data-testid="needs-daily"
          class="h-11 w-full rounded-xl border border-gray-200 bg-white px-3 text-base tabular-nums text-gray-900 focus:border-green-600 focus:outline-none focus:ring-2 focus:ring-green-600/30 dark:border-gray-700 dark:bg-gray-950 dark:text-gray-100"
          @input="onDistance('dailyKm', $event)"
        />
      </label>
      <label class="grid gap-1">
        <span class="text-[12.5px] text-gray-500 dark:text-gray-400">{{ t('models_ranking.needs.longest', { unit: distanceUnitLabel() }) }}</span>
        <input
          :value="longestLocal"
          type="text"
          inputmode="numeric"
          pattern="[0-9]*"
          autocomplete="off"
          data-testid="needs-longest"
          class="h-11 w-full rounded-xl border border-gray-200 bg-white px-3 text-base tabular-nums text-gray-900 focus:border-green-600 focus:outline-none focus:ring-2 focus:ring-green-600/30 dark:border-gray-700 dark:bg-gray-950 dark:text-gray-100"
          @input="onDistance('longestTripKm', $event)"
        />
      </label>
    </div>

    <div class="grid gap-1">
      <span :id="homeId" class="text-[12.5px] text-gray-500 dark:text-gray-400">{{ t('models_ranking.needs.home') }}</span>
      <div class="grid grid-cols-2 gap-px overflow-hidden rounded-xl border border-gray-200 bg-gray-200 dark:border-gray-700 dark:bg-gray-700" role="group" :aria-labelledby="homeId">
        <button
          v-for="opt in homeOptions"
          :key="String(opt.value)"
          type="button"
          class="min-h-11 text-sm font-semibold"
          :class="modelValue.homeCharging === opt.value
            ? 'bg-gray-900 text-white dark:bg-gray-100 dark:text-gray-900'
            : 'bg-white text-gray-800 hover:bg-gray-50 dark:bg-gray-900 dark:text-gray-200 dark:hover:bg-gray-800'"
          :aria-pressed="modelValue.homeCharging === opt.value"
          @click="emit('update:modelValue', { ...modelValue, homeCharging: opt.value })"
        >{{ opt.label }}</button>
      </div>
    </div>

    <p class="text-[15px] leading-snug text-gray-900 dark:text-gray-100" aria-live="polite" data-testid="needs-summary">{{ summaryText }}</p>
    <p class="text-[12px] text-gray-500 dark:text-gray-400">{{ t('models_ranking.needs.assumptions') }}</p>
  </section>
</template>

<script setup lang="ts">
import { computed, useId } from 'vue'
import { useI18n } from 'vue-i18n'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import { odometerLocalToKm } from '../../utils/unitConversions'
import { NEEDS_KM_MAX } from '../../composables/useModelRanking'
import type { NeedsInput, NeedsSummary } from '../../utils/needsCheck'

const props = defineProps<{
  modelValue: NeedsInput
  summary: NeedsSummary
}>()
const emit = defineEmits<{ 'update:modelValue': [value: NeedsInput] }>()

const { t } = useI18n()
const { formatNumber, distanceUnitLabel, convertDistance, isImperial } = useLocaleFormat()

const titleId = useId()
const homeId = useId()

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
