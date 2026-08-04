<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import PowerCurveChart from './PowerCurveChart.vue'

defineProps<{
  loading: boolean
  points: { ts: number; kw: number }[]
  height?: number
  consumptionKwhPer100km?: number | null
}>()

const { t } = useI18n()
</script>

<template>
  <div v-if="loading" class="text-xs text-gray-500 dark:text-gray-400 text-center py-6">
    {{ t('live.loading_data') }}
  </div>
  <PowerCurveChart
    v-else-if="points.length > 0"
    :points="points"
    :height="height ?? 180"
    x-axis-mode="duration"
    :aria-label="t('dashboard.show_power_curve')"
    :consumption-kwh-per100km="consumptionKwhPer100km ?? null"
  />
  <div v-else class="text-xs text-gray-500 dark:text-gray-400 text-center py-6">
    {{ t('dashboard.no_power_curve') }}
  </div>
</template>
