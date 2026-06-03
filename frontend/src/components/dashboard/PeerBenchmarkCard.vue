<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ChevronDownIcon } from '@heroicons/vue/24/outline'
import { useSlideTransition } from '../../composables/useSlideTransition'

const { onEnter, onAfterEnter, onLeave, onAfterLeave } = useSlideTransition()

const LS_KEY = 'peer_benchmark_collapsed'
const collapsed = ref(localStorage.getItem(LS_KEY) === 'true')
function toggleCollapsed() {
  collapsed.value = !collapsed.value
  localStorage.setItem(LS_KEY, String(collapsed.value))
}
import type { PeerBenchmark } from '../../composables/useDashboardStats'

const props = defineProps<{
  benchmark: PeerBenchmark
  effectiveBatteryKwh: number | null
  carModel: string
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
  if (!props.effectiveBatteryKwh || !props.benchmark.userLifetimeConsumptionKwhPer100km) return null
  return calcRange(props.effectiveBatteryKwh, props.benchmark.userLifetimeConsumptionKwhPer100km)
})

const peerRange = computed(() => {
  if (!props.effectiveBatteryKwh || !props.benchmark.peerAvgConsumptionKwhPer100km) return null
  return calcRange(props.effectiveBatteryKwh, props.benchmark.peerAvgConsumptionKwhPer100km)
})

const consumptionDelta = computed<DeltaBadge | null>(() => {
  const u = props.benchmark.userLifetimeConsumptionKwhPer100km
  const p = props.benchmark.peerAvgConsumptionKwhPer100km
  if (!u || !p) return null
  const pct = ((u - p) / p) * 100
  const rounded = Math.round(Math.abs(pct))
  if (rounded === 0) return { label: '= 0%', isGood: true }
  if (pct < 0) return { label: `↓ ${rounded}%`, isGood: true }
  return { label: `↑ ${rounded}%`, isGood: false }
})

const rangeDelta = computed<DeltaBadge | null>(() => {
  if (userRange.value === null || peerRange.value === null) return null
  const km = userRange.value - peerRange.value
  const abs = Math.abs(km)
  if (abs < 2) return { label: '= 0 km', isGood: true }
  if (km > 0) return { label: `↑ +${abs} km`, isGood: true }
  return { label: `↓ ${abs} km`, isGood: false }
})

const costDelta = computed<DeltaBadge | null>(() => {
  const u = props.benchmark.userLifetimeCostPerKwh
  const p = props.benchmark.peerAvgCostPerKwh
  if (!u || !p) return null
  const pct = ((u - p) / p) * 100
  const rounded = Math.round(Math.abs(pct))
  if (rounded === 0) return { label: '= 0%', isGood: true }
  if (pct < 0) return { label: `↓ ${rounded}%`, isGood: true }
  return { label: `↑ ${rounded}%`, isGood: false }
})

const showCost = computed(() =>
  props.benchmark.sameCountryPeerUsers >= 3 &&
  props.benchmark.userLifetimeCostPerKwh !== null &&
  props.benchmark.peerAvgCostPerKwh !== null
)

// €/100km = €/kWh × kWh/100km — nur wenn Kostendaten verfügbar
const userCostPer100km = computed(() => {
  const cost = props.benchmark.userLifetimeCostPerKwh
  const cons = props.benchmark.userLifetimeConsumptionKwhPer100km
  if (!cost || !cons) return null
  return cost * cons
})

const peerCostPer100km = computed(() => {
  const cost = props.benchmark.peerAvgCostPerKwh
  const cons = props.benchmark.peerAvgConsumptionKwhPer100km
  if (!cost || !cons) return null
  return cost * cons
})

const costPer100kmDelta = computed<DeltaBadge | null>(() => {
  const u = userCostPer100km.value
  const p = peerCostPer100km.value
  if (!u || !p) return null
  const pct = ((u - p) / p) * 100
  const rounded = Math.round(Math.abs(pct))
  if (rounded === 0) return { label: '= 0%', isGood: true }
  if (pct < 0) return { label: `↓ ${rounded}%`, isGood: true }
  return { label: `↑ ${rounded}%`, isGood: false }
})

function formatConsumption(val: number | null | undefined): string {
  if (val == null) return '–'
  return val.toFixed(1)
}

function formatCost(val: number | null | undefined): string {
  if (val == null) return '–'
  return val.toFixed(2).replace('.', ',') + ' €'
}

function formatCostPer100km(val: number | null | undefined): string {
  if (val == null) return '–'
  return val.toFixed(2).replace('.', ',') + ' €'
}
</script>

<template>
  <div class="bg-white dark:bg-gray-700 rounded-sm border-2 border-gray-300 dark:border-gray-600 overflow-hidden shadow-[2px_2px_0_0_#d1d5db] dark:shadow-[2px_2px_0_0_#4b5563] flex flex-col">

    <!-- Header: mobile gestackt + einklappbar, sm+ absolut zentriert -->
    <div class="border-b border-gray-100 dark:border-gray-600">
      <!-- Mobile: klickbarer Header -->
      <button @click="toggleCollapsed"
        class="sm:hidden w-full px-4 py-3 flex items-center justify-between">
        <div class="w-6 shrink-0"></div>
        <div class="flex-1 flex flex-col items-center text-center min-w-0">
          <p class="text-sm font-semibold text-gray-800 dark:text-gray-200 truncate">{{ t('dashboard.peer_compact_title') }}</p>
          <p class="text-xs text-gray-400 dark:text-gray-500 mt-0.5">{{ benchmark.uniquePeerUsers }} {{ carModel }} {{ t('dashboard.peer_drivers') }}</p>
        </div>
        <ChevronDownIcon
          class="w-4 h-4 text-gray-400 shrink-0 ml-2 transition-transform duration-200"
          :class="{ 'rotate-180': !collapsed }" />
      </button>
      <!-- sm+ -->
      <div class="hidden sm:flex flex-col items-center justify-center px-4 py-3">
        <p class="text-sm font-semibold text-gray-800 dark:text-gray-200 text-center">{{ t('dashboard.peer_compact_title') }}</p>
        <p class="text-xs text-gray-400 dark:text-gray-500 mt-0.5">{{ benchmark.uniquePeerUsers }} {{ carModel }} {{ t('dashboard.peer_drivers') }}</p>
      </div>
    </div>

    <!-- Content: auf Mobile einklappbar, auf Desktop immer sichtbar -->
    <Transition @enter="onEnter" @after-enter="onAfterEnter" @leave="onLeave" @after-leave="onAfterLeave">
    <div v-show="!collapsed" class="sm:!block">

    <!-- 4er Kachel: Verbrauch | Reichweite / Kosten | €/100km -->
    <div class="grid grid-cols-2">

      <!-- Verbrauch (oben links) -->
      <div class="p-4 text-center border-r border-gray-100 dark:border-gray-600">
        <p class="text-xs font-medium text-gray-500 dark:text-gray-400 mb-1.5">{{ t('dashboard.peer_consumption') }}</p>
        <div class="flex items-center justify-center gap-1.5 text-sm flex-wrap">
          <span class="font-bold text-gray-900 dark:text-gray-100">{{ formatConsumption(benchmark.userLifetimeConsumptionKwhPer100km) }} kWh</span>
          <span class="text-xs text-gray-400 dark:text-gray-500">vs</span>
          <span class="text-gray-400 dark:text-gray-500">Ø {{ formatConsumption(benchmark.peerAvgConsumptionKwhPer100km) }} kWh</span>
          <span v-if="consumptionDelta" :class="['font-semibold', consumptionDelta.isGood ? 'text-green-600 dark:text-green-400' : 'text-red-500 dark:text-red-400']">· {{ consumptionDelta.label }}</span>
        </div>
      </div>

      <!-- Reichweite (oben rechts) -->
      <div class="p-4 text-center">
        <p class="text-xs font-medium text-gray-500 dark:text-gray-400 mb-1.5">{{ t('dashboard.peer_range') }}</p>
        <div class="flex items-center justify-center gap-1.5 text-sm flex-wrap">
          <span class="font-bold text-gray-900 dark:text-gray-100">{{ userRange ?? '–' }} km</span>
          <span class="text-xs text-gray-400 dark:text-gray-500">vs</span>
          <span class="text-gray-400 dark:text-gray-500">Ø {{ peerRange ?? '–' }} km</span>
          <span v-if="rangeDelta" :class="['font-semibold', rangeDelta.isGood ? 'text-green-600 dark:text-green-400' : 'text-red-500 dark:text-red-400']">· {{ rangeDelta.label }}</span>
        </div>
      </div>

      <!-- Kosten €/kWh (unten links) — nur wenn Daten da -->
      <div v-if="showCost" class="p-4 text-center border-t border-r border-gray-100 dark:border-gray-600">
        <p class="text-xs font-medium text-gray-500 dark:text-gray-400 mb-1.5">
          {{ t('dashboard.peer_cost_label') }}
        </p>
        <div class="flex items-center justify-center gap-1.5 text-sm flex-wrap">
          <span class="font-bold text-gray-900 dark:text-gray-100">{{ formatCost(benchmark.userLifetimeCostPerKwh) }}/kWh</span>
          <span class="text-xs text-gray-400 dark:text-gray-500">vs</span>
          <span class="text-gray-400 dark:text-gray-500">Ø {{ formatCost(benchmark.peerAvgCostPerKwh) }}/kWh</span>
          <span v-if="costDelta" :class="['font-semibold', costDelta.isGood ? 'text-green-600 dark:text-green-400' : 'text-red-500 dark:text-red-400']">· {{ costDelta.label }}</span>
        </div>
      </div>

      <!-- €/100km (unten rechts) -->
      <div v-if="showCost && userCostPer100km !== null && peerCostPer100km !== null"
        class="p-4 text-center border-t border-gray-100 dark:border-gray-600">
        <p class="text-xs font-medium text-gray-500 dark:text-gray-400 mb-1.5">{{ t('dashboard.peer_cost_per_distance') }}</p>
        <div class="flex items-center justify-center gap-1.5 text-sm flex-wrap">
          <span class="font-bold text-gray-900 dark:text-gray-100">{{ formatCostPer100km(userCostPer100km) }}/100km</span>
          <span class="text-xs text-gray-400 dark:text-gray-500">vs</span>
          <span class="text-gray-400 dark:text-gray-500">Ø {{ formatCostPer100km(peerCostPer100km) }}/100km</span>
          <span v-if="costPer100kmDelta" :class="['font-semibold', costPer100kmDelta.isGood ? 'text-green-600 dark:text-green-400' : 'text-red-500 dark:text-red-400']">· {{ costPer100kmDelta.label }}</span>
        </div>
      </div>
    </div>


    </div>
    </Transition>
  </div>
</template>

