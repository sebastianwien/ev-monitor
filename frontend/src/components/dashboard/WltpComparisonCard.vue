<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ChevronDownIcon } from '@heroicons/vue/24/outline'
import { useSlideTransition } from '../../composables/useSlideTransition'

const { onEnter, onAfterEnter, onLeave, onAfterLeave } = useSlideTransition()

const LS_KEY = 'wltp_comparison_collapsed'
const collapsed = ref(localStorage.getItem(LS_KEY) === 'true')
function toggleCollapsed() {
  collapsed.value = !collapsed.value
  localStorage.setItem(LS_KEY, String(collapsed.value))
}

const props = defineProps<{
  officialRangeKm: number
  officialConsumptionKwhPer100km: number
  userAvgConsumptionKwhPer100km: number
  effectiveBatteryKwh: number | null
  carDisplayName: string
  ratingSource: string
}>()

const { t } = useI18n()

const SOC_MAX = 90
const SOC_MIN = 10

interface DeltaBadge { label: string; isGood: boolean }

function calcRange(batteryKwh: number, consumptionKwhPer100km: number): number {
  const usableKwh = batteryKwh * (SOC_MAX - SOC_MIN) / 100
  return Math.round(usableKwh / consumptionKwhPer100km * 100)
}

const userRange = computed(() => {
  if (!props.effectiveBatteryKwh) return null
  return calcRange(props.effectiveBatteryKwh, props.userAvgConsumptionKwhPer100km)
})

const wltpRange = computed(() => {
  if (!props.effectiveBatteryKwh) return null
  return calcRange(props.effectiveBatteryKwh, props.officialConsumptionKwhPer100km)
})

const consumptionDelta = computed<DeltaBadge | null>(() => {
  const u = props.userAvgConsumptionKwhPer100km
  const w = props.officialConsumptionKwhPer100km
  if (!u || !w) return null
  const pct = ((u - w) / w) * 100
  const rounded = Math.round(Math.abs(pct))
  if (rounded === 0) return { label: '= 0%', isGood: true }
  if (pct < 0) return { label: `↓ ${rounded}%`, isGood: true }
  return { label: `↑ ${rounded}%`, isGood: false }
})

const rangeDelta = computed<DeltaBadge | null>(() => {
  if (userRange.value === null || wltpRange.value === null) return null
  const km = userRange.value - wltpRange.value
  const abs = Math.abs(km)
  if (abs < 2) return { label: '= 0 km', isGood: true }
  if (km > 0) return { label: `↑ +${abs} km`, isGood: true }
  return { label: `↓ ${abs} km`, isGood: false }
})

function formatConsumption(val: number | null | undefined): string {
  if (val == null) return '-'
  return val.toFixed(1)
}
</script>

<template>
  <div class="bg-white dark:bg-gray-800 rounded-sm border-2 border-gray-300 dark:border-gray-700 overflow-hidden shadow-[2px_2px_0_0_#d1d5db] dark:shadow-[2px_2px_0_0_#374151] flex flex-col">

    <!-- Header -->
    <div class="border-b border-gray-100 dark:border-gray-700">
      <!-- Mobile: klickbarer Header -->
      <button @click="toggleCollapsed"
        class="sm:hidden w-full px-4 py-3 flex items-center justify-between">
        <div class="w-6 shrink-0"></div>
        <div class="flex-1 flex flex-col items-center text-center min-w-0">
          <p class="text-sm font-semibold text-gray-800 dark:text-gray-200 truncate">{{ carDisplayName }} {{ t('dashboard.peer_benchmark_title') }}</p>
          <p class="text-xs text-gray-400 dark:text-gray-500 mt-0.5">{{ t('dashboard.wltp_comparison_subtitle') }}</p>
        </div>
        <ChevronDownIcon
          class="w-4 h-4 text-gray-400 shrink-0 ml-2 transition-transform duration-200"
          :class="{ 'rotate-180': !collapsed }" />
      </button>
      <!-- sm+ -->
      <div class="hidden sm:flex relative items-center px-4 py-3">
        <p class="absolute inset-0 flex items-center justify-center text-sm font-semibold text-gray-800 dark:text-gray-200 pointer-events-none">
          {{ carDisplayName }} {{ t('dashboard.peer_benchmark_title') }}
        </p>
        <div class="ml-auto flex items-center gap-1 text-xs text-gray-400 dark:text-gray-500 shrink-0 relative">
          <span>{{ t('dashboard.wltp_comparison_subtitle') }}</span>
        </div>
      </div>
    </div>

    <!-- Content: auf Mobile einklappbar, auf Desktop immer sichtbar -->
    <Transition @enter="onEnter" @after-enter="onAfterEnter" @leave="onLeave" @after-leave="onAfterLeave">
    <div v-show="!collapsed" class="sm:!block">

    <div class="grid grid-cols-2">

      <!-- Verbrauch -->
      <div class="p-4 text-center border-r border-gray-100 dark:border-gray-700">
        <p class="text-xs font-medium text-gray-500 dark:text-gray-400 mb-1.5">{{ t('dashboard.peer_consumption') }}</p>
        <div class="flex items-center justify-center gap-1.5 text-sm flex-wrap">
          <span class="font-bold text-gray-900 dark:text-gray-100">{{ formatConsumption(userAvgConsumptionKwhPer100km) }} kWh</span>
          <span class="text-xs text-gray-400 dark:text-gray-500">vs</span>
          <span class="text-gray-400 dark:text-gray-500">{{ ratingSource }} {{ formatConsumption(officialConsumptionKwhPer100km) }} kWh</span>
          <span v-if="consumptionDelta" :class="['font-semibold', consumptionDelta.isGood ? 'text-green-600 dark:text-green-400' : 'text-red-500 dark:text-red-400']">· {{ consumptionDelta.label }}</span>
        </div>
      </div>

      <!-- Reichweite -->
      <div class="p-4 text-center">
        <p class="text-xs font-medium text-gray-500 dark:text-gray-400 mb-1.5">{{ t('dashboard.peer_range') }}</p>
        <div class="flex items-center justify-center gap-1.5 text-sm flex-wrap">
          <span class="font-bold text-gray-900 dark:text-gray-100">{{ userRange ?? '-' }} km</span>
          <span class="text-xs text-gray-400 dark:text-gray-500">vs</span>
          <span class="text-gray-400 dark:text-gray-500">{{ ratingSource }} {{ wltpRange ?? '-' }} km</span>
          <span v-if="rangeDelta" :class="['font-semibold', rangeDelta.isGood ? 'text-green-600 dark:text-green-400' : 'text-red-500 dark:text-red-400']">· {{ rangeDelta.label }}</span>
        </div>
      </div>

    </div>

    <!-- Footer: Erklärung zur Berechnungsmethode -->
    <div class="px-4 py-2.5 border-t border-gray-100 dark:border-gray-700 flex items-center gap-2 mt-auto">
      <p class="text-xs text-gray-400 dark:text-gray-500">{{ t('dashboard.wltp_comparison_note') }}</p>
    </div>

    </div>
    </Transition>
  </div>
</template>
