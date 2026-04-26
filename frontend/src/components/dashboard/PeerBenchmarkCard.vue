<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { UsersIcon, ExclamationTriangleIcon, ChevronDownIcon } from '@heroicons/vue/24/outline'
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
  carDisplayName: string
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

function formatConsumption(val: number | null): string {
  if (val === null) return '–'
  return val.toFixed(1)
}

function formatCost(val: number | null): string {
  if (val === null) return '–'
  return val.toFixed(2).replace('.', ',') + ' €'
}

function formatCostPer100km(val: number | null): string {
  if (val === null) return '–'
  return val.toFixed(2).replace('.', ',') + ' €'
}
</script>

<template>
  <div class="bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 overflow-hidden shadow-sm flex flex-col">

    <!-- Header: mobile gestackt + einklappbar, sm+ absolut zentriert -->
    <div class="border-b border-gray-100 dark:border-gray-700">
      <!-- Mobile: klickbarer Header -->
      <button @click="toggleCollapsed"
        class="sm:hidden w-full px-4 py-3 flex items-center justify-between">
        <div class="w-6 shrink-0"></div>
        <div class="flex-1 flex flex-col items-center text-center min-w-0">
          <p class="text-sm font-semibold text-gray-800 dark:text-gray-200 truncate">{{ carDisplayName }} {{ t('dashboard.peer_benchmark_title') }}</p>
          <p class="text-xs text-gray-400 dark:text-gray-500 mt-0.5 flex items-center gap-1">
            <UsersIcon class="w-3.5 h-3.5 shrink-0" />
            {{ benchmark.uniquePeerUsers }} {{ t('dashboard.peer_drivers') }} · {{ benchmark.peerTripCount }} {{ t('dashboard.peer_trips') }}
          </p>
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
          <UsersIcon class="w-3.5 h-3.5" />
          <span>{{ benchmark.uniquePeerUsers }} {{ t('dashboard.peer_drivers') }} · {{ benchmark.peerTripCount }} {{ t('dashboard.peer_trips') }}</span>
        </div>
      </div>
    </div>

    <!-- Content: auf Mobile einklappbar, auf Desktop immer sichtbar -->
    <Transition @enter="onEnter" @after-enter="onAfterEnter" @leave="onLeave" @after-leave="onAfterLeave">
    <div v-show="!collapsed" class="sm:!block">

    <!-- 4er Kachel: Verbrauch | Reichweite / Kosten | €/100km -->
    <div class="grid grid-cols-2">

      <!-- Verbrauch (oben links) -->
      <div class="p-4 text-center border-r border-gray-100 dark:border-gray-700">
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
      <div v-if="showCost" class="p-4 text-center border-t border-r border-gray-100 dark:border-gray-700">
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
        class="p-4 text-center border-t border-gray-100 dark:border-gray-700">
        <p class="text-xs font-medium text-gray-500 dark:text-gray-400 mb-1.5">{{ t('dashboard.peer_cost_per_distance') }}</p>
        <div class="flex items-center justify-center gap-1.5 text-sm flex-wrap">
          <span class="font-bold text-gray-900 dark:text-gray-100">{{ formatCostPer100km(userCostPer100km) }}/100km</span>
          <span class="text-xs text-gray-400 dark:text-gray-500">vs</span>
          <span class="text-gray-400 dark:text-gray-500">Ø {{ formatCostPer100km(peerCostPer100km) }}/100km</span>
          <span v-if="costPer100kmDelta" :class="['font-semibold', costPer100kmDelta.isGood ? 'text-green-600 dark:text-green-400' : 'text-red-500 dark:text-red-400']">· {{ costPer100kmDelta.label }}</span>
        </div>
      </div>
    </div>

    <!-- Footer: nur bei insufficient data -->
    <div v-if="!benchmark.sufficientData"
      class="px-4 py-2.5 border-t border-gray-100 dark:border-gray-700 flex items-center gap-2 mt-auto">
      <ExclamationTriangleIcon class="w-3.5 h-3.5 text-amber-500 shrink-0" />
      <p class="text-xs text-gray-400 dark:text-gray-500">
        {{ t('dashboard.peer_insufficient_data', { n: benchmark.uniquePeerUsers }) }}
      </p>
    </div>

    </div>
    </Transition>
  </div>
</template>

