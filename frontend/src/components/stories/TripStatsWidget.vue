<template>
  <div class="rounded-xl border border-emerald-200 dark:border-emerald-900 bg-emerald-50/60 dark:bg-emerald-950/40 p-4">
    <div class="flex items-center gap-2 mb-3">
      <MapPinIcon class="w-5 h-5 text-emerald-600 dark:text-emerald-400 shrink-0" aria-hidden="true" />
      <span class="font-semibold text-gray-900 dark:text-gray-100 truncate">
        {{ block.label || t('stories.widget.trip') }}
      </span>
      <span v-if="tripDate" class="ml-auto text-xs text-gray-500 dark:text-gray-400 shrink-0">{{ tripDate }}</span>
    </div>
    <dl class="grid grid-cols-2 sm:grid-cols-3 gap-3">
      <div v-if="stats.distanceKm != null">
        <dt class="text-xs text-gray-500 dark:text-gray-400">{{ t('stories.widget.distance') }}</dt>
        <dd class="text-base font-semibold text-gray-900 dark:text-gray-100">{{ formatNumber(stats.distanceKm) }} km</dd>
      </div>
      <div v-if="stats.durationMinutes != null">
        <dt class="text-xs text-gray-500 dark:text-gray-400">{{ t('stories.widget.duration') }}</dt>
        <dd class="text-base font-semibold text-gray-900 dark:text-gray-100">{{ formatDuration(stats.durationMinutes) }}</dd>
      </div>
      <div v-if="stats.consumedKwh != null">
        <dt class="text-xs text-gray-500 dark:text-gray-400">{{ t('stories.widget.consumed') }}</dt>
        <dd class="text-base font-semibold text-gray-900 dark:text-gray-100">{{ formatNumber(stats.consumedKwh) }} kWh</dd>
      </div>
      <div v-if="consumptionPer100 != null">
        <dt class="text-xs text-gray-500 dark:text-gray-400">{{ t('stories.widget.consumption') }}</dt>
        <dd class="text-base font-semibold text-gray-900 dark:text-gray-100">{{ formatNumber(consumptionPer100) }} kWh/100km</dd>
      </div>
      <div v-if="stats.socStart != null && stats.socEnd != null">
        <dt class="text-xs text-gray-500 dark:text-gray-400">{{ t('stories.widget.soc') }}</dt>
        <dd class="text-base font-semibold text-gray-900 dark:text-gray-100">{{ Math.round(stats.socStart) }} % → {{ Math.round(stats.socEnd) }} %</dd>
      </div>
      <div v-if="stats.avgSpeedKmh != null">
        <dt class="text-xs text-gray-500 dark:text-gray-400">{{ t('stories.widget.avg_speed') }}</dt>
        <dd class="text-base font-semibold text-gray-900 dark:text-gray-100">{{ formatNumber(stats.avgSpeedKmh) }} km/h</dd>
      </div>
      <div v-if="stats.outsideTempCelsius != null">
        <dt class="text-xs text-gray-500 dark:text-gray-400">{{ t('stories.widget.temperature') }}</dt>
        <dd class="text-base font-semibold text-gray-900 dark:text-gray-100">{{ formatNumber(stats.outsideTempCelsius) }} °C</dd>
      </div>
    </dl>
    <div class="mt-3 flex items-center gap-1 text-xs text-emerald-700 dark:text-emerald-400">
      <CheckBadgeIcon class="w-4 h-4 shrink-0" aria-hidden="true" />
      {{ t('stories.widget.verified') }}
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { MapPinIcon, CheckBadgeIcon } from '@heroicons/vue/24/outline'
import type { StoryBlock } from '../../api/storyService'

const props = defineProps<{ block: StoryBlock }>()
const { t, locale } = useI18n()

const stats = computed(() => props.block.stats ?? {})

const consumptionPer100 = computed(() => {
  const s = stats.value
  if (s.consumedKwh == null || s.distanceKm == null || s.distanceKm <= 0) return null
  return (s.consumedKwh / s.distanceKm) * 100
})

const tripDate = computed(() => {
  if (!stats.value.startedAt) return null
  return new Date(stats.value.startedAt).toLocaleDateString(locale.value, {
    day: '2-digit', month: '2-digit', year: 'numeric',
  })
})

function formatNumber(value: number): string {
  return value.toLocaleString(locale.value, { maximumFractionDigits: 1 })
}

function formatDuration(minutes: number): string {
  const h = Math.floor(minutes / 60)
  const m = Math.round(minutes % 60)
  return h > 0 ? `${h} h ${m} min` : `${m} min`
}
</script>
