<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { ChargingEfficiencySplit } from '../../composables/useDashboardStats'

const props = defineProps<{
  efficiencySplit: ChargingEfficiencySplit
}>()

const { t } = useI18n()

const lossKwh = computed(() => props.efficiencySplit.gridKwh - props.efficiencySplit.vehicleKwh)

const lossPercent = computed(() => {
  if (props.efficiencySplit.gridKwh <= 0) return 0
  return Math.round((lossKwh.value / props.efficiencySplit.gridKwh) * 100)
})

const vehiclePercent = computed(() => 100 - lossPercent.value)

const isCoveragePartial = computed(
  () => props.efficiencySplit.coveredLogCount < props.efficiencySplit.totalLogCount
)

function fmt(kwh: number): string {
  return kwh.toFixed(1)
}
</script>

<template>
  <div class="bg-white dark:bg-gray-700 rounded-sm border-2 border-gray-300 dark:border-gray-600 overflow-hidden shadow-[2px_2px_0_0_#d1d5db] dark:shadow-[2px_2px_0_0_#4b5563] flex flex-col">

    <!-- Header -->
    <div class="px-4 py-3 border-b border-gray-100 dark:border-gray-600 text-sm font-semibold text-gray-800 dark:text-gray-200 text-center">
      {{ t('dashboard.efficiency_title') }}
    </div>

    <div class="flex-1 p-4 flex flex-col gap-4">

      <!-- Flow: Netz → Verlust → Batterie -->
      <div class="flex items-center justify-between gap-2">
        <div class="flex flex-col items-center flex-1">
          <span class="text-sm font-medium text-gray-500 dark:text-gray-400 mb-1">{{ t('dashboard.efficiency_grid') }}</span>
          <span class="text-base font-bold text-gray-800 dark:text-gray-100 tabular-nums">{{ fmt(efficiencySplit.gridKwh) }}</span>
          <span class="text-sm text-gray-400 dark:text-gray-500">kWh</span>
        </div>

        <div class="flex flex-col items-center px-3">
          <span class="text-2xl font-bold text-amber-500 dark:text-amber-400 tabular-nums leading-tight">-{{ fmt(lossKwh) }} kWh</span>
          <span class="text-base font-semibold text-amber-500 dark:text-amber-400">{{ lossPercent }}% {{ t('dashboard.efficiency_loss') }}</span>
        </div>

        <div class="flex flex-col items-center flex-1">
          <span class="text-sm font-medium text-gray-500 dark:text-gray-400 mb-1">{{ t('dashboard.efficiency_battery') }}</span>
          <span class="text-base font-bold text-indigo-600 dark:text-indigo-400 tabular-nums">{{ fmt(efficiencySplit.vehicleKwh) }}</span>
          <span class="text-sm text-gray-400 dark:text-gray-500">kWh</span>
        </div>
      </div>

      <!-- Stacked bar -->
      <div>
        <div class="h-3 w-full flex rounded-full overflow-hidden bg-amber-400">
          <div
            class="h-full bg-indigo-500 transition-all duration-500"
            :style="{ width: vehiclePercent + '%' }"
          />
        </div>
        <div class="flex justify-between mt-1">
          <span class="text-sm text-indigo-600 dark:text-indigo-400 font-medium">{{ vehiclePercent }}% {{ t('dashboard.efficiency_in_battery') }}</span>
          <span class="text-sm text-amber-500 dark:text-amber-400 font-medium">{{ lossPercent }}% {{ t('dashboard.efficiency_loss') }}</span>
        </div>
      </div>

      <!-- Hinweis bei partieller Abdeckung -->
      <p v-if="isCoveragePartial" class="text-xs text-gray-500 dark:text-gray-400 leading-snug">
        {{ t('dashboard.efficiency_partial_hint', { covered: efficiencySplit.coveredLogCount, total: efficiencySplit.totalLogCount }) }}
      </p>

    </div>
  </div>
</template>
