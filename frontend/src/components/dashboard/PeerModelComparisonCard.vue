<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ChevronDownIcon, InformationCircleIcon } from '@heroicons/vue/24/outline'
import { useSlideTransition } from '../../composables/useSlideTransition'
import { peerModelComparisonService, type PeerModelComparisonItem } from '../../api/peerModelComparisonService'

const { onEnter, onAfterEnter, onLeave, onAfterLeave } = useSlideTransition()

const LS_KEY = 'peer_model_comparison_collapsed'
const collapsed = ref(localStorage.getItem(LS_KEY) === 'true')
function toggleCollapsed() {
  collapsed.value = !collapsed.value
  localStorage.setItem(LS_KEY, String(collapsed.value))
}

const { t } = useI18n()

const props = defineProps<{
  carId: string
  carDisplayName: string
}>()

const comparisons = ref<PeerModelComparisonItem[]>([])
const loading = ref(false)
const error = ref<string | null>(null)

onMounted(async () => {
  loading.value = true
  error.value = null
  try {
    const response = await peerModelComparisonService.getPeerModelComparison(props.carId)
    comparisons.value = response.modelComparisons
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to load peer data'
  } finally {
    loading.value = false
  }
})

// Get metric values from API response
function getPeerMetricValue(item: PeerModelComparisonItem, category: string): number | null {
  switch (category) {
    case 'consumption':
      return item.peerMetrics.avgConsumptionKwhPer100km
    case 'costPerKwh':
      return item.peerMetrics.avgCostPerKwh
    case 'costPer100km':
      return item.userMetrics?.costPerKwh && item.peerMetrics.avgCostPerKwh
        ? item.peerMetrics.avgCostPerKwh * (item.peerMetrics.avgConsumptionKwhPer100km || 0)
        : null
    default:
      return null
  }
}

function getUserMetricValue(item: PeerModelComparisonItem, category: string): number | null {
  if (!item.userMetrics) return null
  switch (category) {
    case 'consumption':
      return item.userMetrics.consumptionKwhPer100km
    case 'costPerKwh':
      return item.userMetrics.costPerKwh
    case 'costPer100km':
      return item.userMetrics.costPerKwh && item.userMetrics.consumptionKwhPer100km
        ? item.userMetrics.costPerKwh * item.userMetrics.consumptionKwhPer100km
        : null
    default:
      return null
  }
}

function getMaxValue(category: string): number {
  let max = 0
  comparisons.value.forEach(item => {
    const peerVal = getPeerMetricValue(item, category)
    const userVal = getUserMetricValue(item, category)
    const val = Math.max(peerVal || 0, userVal || 0)
    if (val > max) max = val
  })
  return max || 1
}

function getBarWidth(value: number | null, maxValue: number): number {
  if (value === null) return 0
  return Math.min((value / maxValue) * 100, 100)
}

function formatValue(value: number | null, decimals: number = 1): string {
  if (value === null) return '—'
  return value.toFixed(decimals)
}
</script>

<template>
  <div class="bg-white dark:bg-gray-700 rounded-sm border-2 border-gray-300 dark:border-gray-600 overflow-hidden shadow-[2px_2px_0_0_#d1d5db] dark:shadow-[2px_2px_0_0_#4b5563] flex flex-col">

    <!-- Header -->
    <div class="border-b border-gray-100 dark:border-gray-600">
      <!-- Mobile: klickbarer Header -->
      <button @click="toggleCollapsed"
        class="sm:hidden w-full px-4 py-3 flex items-center justify-between">
        <div class="w-6 shrink-0"></div>
        <div class="flex-1 flex flex-col items-center text-center min-w-0">
          <p class="text-sm font-semibold text-gray-800 dark:text-gray-200 truncate">{{ carDisplayName }} Modell-Vergleich</p>
          <p class="text-xs text-gray-400 dark:text-gray-500 mt-0.5">{{ comparisons.length }} Modellvariante{{ comparisons.length !== 1 ? 'n' : '' }}</p>
        </div>
        <ChevronDownIcon
          class="w-4 h-4 text-gray-400 shrink-0 ml-2 transition-transform duration-200"
          :class="{ 'rotate-180': !collapsed }" />
      </button>
      <!-- sm+ -->
      <div class="hidden sm:flex flex-col items-center px-4 py-3 gap-0.5">
        <p class="text-sm font-semibold text-gray-800 dark:text-gray-200 text-center">{{ carDisplayName }} Modell-Vergleich</p>
        <p class="text-xs text-gray-400 dark:text-gray-500">{{ comparisons.length }} Modellvariante{{ comparisons.length !== 1 ? 'n' : '' }}</p>
      </div>
    </div>

    <!-- Content -->
    <Transition @enter="onEnter" @after-enter="onAfterEnter" @leave="onLeave" @after-leave="onAfterLeave">
    <div v-show="!collapsed" class="sm:!block">

    <!-- Loading state -->
    <div v-if="loading" class="p-4 text-center text-xs text-gray-500 dark:text-gray-400">
      <p>{{ t('common.loading') }}</p>
    </div>

    <!-- Error state -->
    <div v-else-if="error" class="p-4 text-center text-xs text-red-500 dark:text-red-400">
      <p>{{ error }}</p>
    </div>

    <!-- Empty state -->
    <div v-else-if="comparisons.length === 0" class="p-4 text-center text-xs text-gray-500 dark:text-gray-400">
      <p>Keine Vergleichsdaten verfügbar</p>
    </div>

    <!-- 3 Categories, jede mit Bar Chart (Verbrauch, Stromkosten, Fahrtkosten) -->
    <div v-else class="grid grid-cols-1 md:grid-cols-2 gap-4 p-4">

      <!-- Verbrauch (kWh/100km) -->
      <div class="flex flex-col gap-3">
        <div class="flex items-center justify-between">
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ t('dashboard.peer_consumption') }}</p>
          <p class="text-xs text-gray-400 dark:text-gray-500">kWh/100km</p>
        </div>
        <div class="space-y-2">
          <div v-for="item in comparisons" :key="item.vehicleSpecificationId" class="flex items-center gap-2">
            <div class="w-32 text-xs truncate">
              <p class="font-medium text-gray-700 dark:text-gray-300 truncate">{{ item.displayName }}</p>
              <p class="text-xs text-gray-400 dark:text-gray-500">{{ item.peerMetrics.uniqueCars }} {{ item.peerMetrics.uniqueCars === 1 ? 'Fahrer' : 'Fahrer' }}</p>
            </div>
            <div class="flex-1 flex items-center gap-1">
              <div v-if="item.isUserVehicleSpec && getUserMetricValue(item, 'consumption') !== null"
                class="relative h-5 rounded"
                :style="{
                  width: getBarWidth(getUserMetricValue(item, 'consumption')!, getMaxValue('consumption')) + '%',
                  background: `linear-gradient(90deg, rgb(59, 130, 246) 0%, rgb(59, 130, 246) ${getUserMetricValue(item, 'consumption')! / ((getUserMetricValue(item, 'consumption') || 0) + (getPeerMetricValue(item, 'consumption') || 0)) * 100}%, rgb(156, 163, 175) ${getUserMetricValue(item, 'consumption')! / ((getUserMetricValue(item, 'consumption') || 0) + (getPeerMetricValue(item, 'consumption') || 0)) * 100}%, rgb(156, 163, 175) 100%)`
                }">
              </div>
              <div v-else
                class="h-5 bg-gray-300 dark:bg-gray-600 rounded"
                :style="{ width: getBarWidth(getPeerMetricValue(item, 'consumption'), getMaxValue('consumption')) + '%' }">
              </div>
            </div>
            <span class="text-xs font-semibold text-gray-700 dark:text-gray-300 whitespace-nowrap">
              {{ formatValue(getPeerMetricValue(item, 'consumption'), 1) }}
            </span>
          </div>
        </div>
      </div>

      <!-- Stromkosten (€/kWh) -->
      <div class="flex flex-col gap-3">
        <div class="flex items-center justify-between">
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ t('dashboard.peer_cost_label') }}</p>
          <p class="text-xs text-gray-400 dark:text-gray-500">€/kWh</p>
        </div>
        <div class="space-y-2">
          <div v-for="item in comparisons" :key="item.vehicleSpecificationId" class="flex items-center gap-2">
            <div class="w-32 text-xs truncate">
              <p class="font-medium text-gray-700 dark:text-gray-300 truncate">{{ item.displayName }}</p>
              <p class="text-xs text-gray-400 dark:text-gray-500">{{ item.peerMetrics.uniqueCars }} Fahrer</p>
            </div>
            <div class="flex-1 flex items-center gap-1">
              <div v-if="item.isUserVehicleSpec && getUserMetricValue(item, 'costPerKwh') !== null"
                class="relative h-5 rounded"
                :style="{
                  width: getBarWidth(getUserMetricValue(item, 'costPerKwh')!, getMaxValue('costPerKwh')) + '%',
                  background: `linear-gradient(90deg, rgb(168, 85, 247) 0%, rgb(168, 85, 247) ${getUserMetricValue(item, 'costPerKwh')! / ((getUserMetricValue(item, 'costPerKwh') || 0) + (getPeerMetricValue(item, 'costPerKwh') || 0)) * 100}%, rgb(156, 163, 175) ${getUserMetricValue(item, 'costPerKwh')! / ((getUserMetricValue(item, 'costPerKwh') || 0) + (getPeerMetricValue(item, 'costPerKwh') || 0)) * 100}%, rgb(156, 163, 175) 100%)`
                }">
              </div>
              <div v-else
                class="h-5 bg-gray-300 dark:bg-gray-600 rounded"
                :style="{ width: getBarWidth(getPeerMetricValue(item, 'costPerKwh'), getMaxValue('costPerKwh')) + '%' }">
              </div>
            </div>
            <span class="text-xs font-semibold text-gray-700 dark:text-gray-300 whitespace-nowrap">
              {{ formatValue(getPeerMetricValue(item, 'costPerKwh'), 2) }}
            </span>
          </div>
        </div>
      </div>

      <!-- Fahrtkosten (€/100km) -->
      <div class="flex flex-col gap-3">
        <div class="flex items-center justify-between">
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ t('dashboard.peer_cost_per_distance') }}</p>
          <p class="text-xs text-gray-400 dark:text-gray-500">€/100km</p>
        </div>
        <div class="space-y-2">
          <div v-for="item in comparisons" :key="item.vehicleSpecificationId" class="flex items-center gap-2">
            <div class="w-32 text-xs truncate">
              <p class="font-medium text-gray-700 dark:text-gray-300 truncate">{{ item.displayName }}</p>
              <p class="text-xs text-gray-400 dark:text-gray-500">{{ item.peerMetrics.uniqueCars }} Fahrer</p>
            </div>
            <div class="flex-1 flex items-center gap-1">
              <div v-if="item.isUserVehicleSpec && getUserMetricValue(item, 'costPer100km') !== null"
                class="relative h-5 rounded"
                :style="{
                  width: getBarWidth(getUserMetricValue(item, 'costPer100km')!, getMaxValue('costPer100km')) + '%',
                  background: `linear-gradient(90deg, rgb(249, 115, 22) 0%, rgb(249, 115, 22) ${getUserMetricValue(item, 'costPer100km')! / ((getUserMetricValue(item, 'costPer100km') || 0) + (getPeerMetricValue(item, 'costPer100km') || 0)) * 100}%, rgb(156, 163, 175) ${getUserMetricValue(item, 'costPer100km')! / ((getUserMetricValue(item, 'costPer100km') || 0) + (getPeerMetricValue(item, 'costPer100km') || 0)) * 100}%, rgb(156, 163, 175) 100%)`
                }">
              </div>
              <div v-else
                class="h-5 bg-gray-300 dark:bg-gray-600 rounded"
                :style="{ width: getBarWidth(getPeerMetricValue(item, 'costPer100km'), getMaxValue('costPer100km')) + '%' }">
              </div>
            </div>
            <span class="text-xs font-semibold text-gray-700 dark:text-gray-300 whitespace-nowrap">
              {{ formatValue(getPeerMetricValue(item, 'costPer100km'), 2) }}
            </span>
          </div>
        </div>
      </div>

    </div>

    <!-- Footer: Erklärung -->
    <div class="px-4 py-2.5 border-t border-gray-100 dark:border-gray-600 flex items-center gap-2 mt-auto">
      <InformationCircleIcon class="w-3.5 h-3.5 text-blue-400 shrink-0" />
      <p class="text-xs text-gray-400 dark:text-gray-500">
        Blau/Farbig: deine Performance · Grau: Durchschnitt anderer Fahrer
      </p>
    </div>

    </div>
    </Transition>
  </div>
</template>
