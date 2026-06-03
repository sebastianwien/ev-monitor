<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ChevronDownIcon, InformationCircleIcon } from '@heroicons/vue/24/outline'
import { useSlideTransition } from '../../composables/useSlideTransition'

const { onEnter, onAfterEnter, onLeave, onAfterLeave } = useSlideTransition()

const LS_KEY = 'peer_model_comparison_collapsed'
const collapsed = ref(localStorage.getItem(LS_KEY) === 'true')
function toggleCollapsed() {
  collapsed.value = !collapsed.value
  localStorage.setItem(LS_KEY, String(collapsed.value))
}

const { t } = useI18n()

// Mock data — später vom Backend
interface ModelComparison {
  modelName: string
  isOwnSpec: boolean
  consumption: {
    ownValue: number | null
    peerAvg: number | null
  }
  range: {
    ownValue: number | null
    peerAvg: number | null
  }
  costPerKwh: {
    ownValue: number | null
    peerAvg: number | null
  }
  costPer100km: {
    ownValue: number | null
    peerAvg: number | null
  }
  peerCount: number
}

const mockData: ModelComparison[] = [
  {
    modelName: 'Model 3 2024 (LR RWD)',
    isOwnSpec: true,
    consumption: { ownValue: 19.2, peerAvg: 22.1 },
    range: { ownValue: 256, peerAvg: 261 },
    costPerKwh: { ownValue: 0.29, peerAvg: 0.28 },
    costPer100km: { ownValue: 5.57, peerAvg: 6.18 },
    peerCount: 3,
  },
  {
    modelName: 'Model 3 2025 (LR RWD)',
    isOwnSpec: false,
    consumption: { ownValue: null, peerAvg: 20.5 },
    range: { ownValue: null, peerAvg: 258 },
    costPerKwh: { ownValue: null, peerAvg: 0.27 },
    costPer100km: { ownValue: null, peerAvg: 5.54 },
    peerCount: 8,
  },
  {
    modelName: 'Model 3 2023 (LR RWD)',
    isOwnSpec: false,
    consumption: { ownValue: null, peerAvg: 21.2 },
    range: { ownValue: null, peerAvg: 248 },
    costPerKwh: { ownValue: null, peerAvg: 0.29 },
    costPer100km: { ownValue: null, peerAvg: 6.14 },
    peerCount: 5,
  },
  {
    modelName: 'Model 3 2024 (Long Range)',
    isOwnSpec: false,
    consumption: { ownValue: null, peerAvg: 21.8 },
    range: { ownValue: null, peerAvg: 244 },
    costPerKwh: { ownValue: null, peerAvg: 0.28 },
    costPer100km: { ownValue: null, peerAvg: 6.10 },
    peerCount: 6,
  },
  {
    modelName: 'Model 3 2025 (Refresh LR)',
    isOwnSpec: false,
    consumption: { ownValue: null, peerAvg: 22.5 },
    range: { ownValue: null, peerAvg: 240 },
    costPerKwh: { ownValue: null, peerAvg: 0.26 },
    costPer100km: { ownValue: null, peerAvg: 5.85 },
    peerCount: 4,
  },
]

const props = defineProps<{
  carDisplayName: string
}>()

// Bar chart helper
interface BarConfig {
  label: string
  unit: string
  models: ModelComparison[]
}

const barConfigs: BarConfig[] = [
  {
    label: t('dashboard.peer_consumption'),
    unit: 'kWh/100km',
    models: mockData,
  },
  {
    label: t('dashboard.peer_range'),
    unit: 'km',
    models: mockData,
  },
  {
    label: t('dashboard.peer_cost_label'),
    unit: '€/kWh',
    models: mockData,
  },
  {
    label: t('dashboard.peer_cost_per_distance'),
    unit: '€/100km',
    models: mockData,
  },
]

// Calculate bar width percentage — normalized to the max value in each category
function getMetricValue(model: ModelComparison, category: string): number | null {
  if (category === 'consumption') return model.consumption.peerAvg
  if (category === 'range') return model.range.peerAvg
  if (category === 'costPerKwh') return model.costPerKwh.peerAvg
  if (category === 'costPer100km') return model.costPer100km.peerAvg
  return null
}

function getOwnMetricValue(model: ModelComparison, category: string): number | null {
  if (category === 'consumption') return model.consumption.ownValue
  if (category === 'range') return model.range.ownValue
  if (category === 'costPerKwh') return model.costPerKwh.ownValue
  if (category === 'costPer100km') return model.costPer100km.ownValue
  return null
}

function getMaxValue(category: string): number {
  let max = 0
  mockData.forEach(m => {
    const val = getMetricValue(m, category)
    if (val !== null && val > max) max = val
  })
  return max || 1
}

function getBarWidth(value: number | null, maxValue: number): number {
  if (value === null) return 0
  return (value / maxValue) * 100
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
          <p class="text-xs text-gray-400 dark:text-gray-500 mt-0.5">5 Modellvarianten</p>
        </div>
        <ChevronDownIcon
          class="w-4 h-4 text-gray-400 shrink-0 ml-2 transition-transform duration-200"
          :class="{ 'rotate-180': !collapsed }" />
      </button>
      <!-- sm+ -->
      <div class="hidden sm:flex flex-col items-center px-4 py-3 gap-0.5">
        <p class="text-sm font-semibold text-gray-800 dark:text-gray-200 text-center">{{ carDisplayName }} Modell-Vergleich</p>
        <p class="text-xs text-gray-400 dark:text-gray-500">5 Modellvarianten</p>
      </div>
    </div>

    <!-- Content -->
    <Transition @enter="onEnter" @after-enter="onAfterEnter" @leave="onLeave" @after-leave="onAfterLeave">
    <div v-show="!collapsed" class="sm:!block">

    <!-- 4 Categories, jede mit Bar Chart -->
    <div class="grid grid-cols-1 md:grid-cols-2 gap-4 p-4">

      <!-- Verbrauch -->
      <div class="flex flex-col gap-3">
        <div class="flex items-center justify-between">
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">Verbrauch</p>
          <p class="text-xs text-gray-400 dark:text-gray-500">kWh/100km</p>
        </div>
        <div class="space-y-2">
          <div v-for="model in mockData" :key="model.modelName" class="flex items-center gap-2">
            <!-- Model name + peer count -->
            <div class="w-32 text-xs truncate">
              <p class="font-medium text-gray-700 dark:text-gray-300 truncate">{{ model.modelName }}</p>
              <p class="text-xs text-gray-400 dark:text-gray-500">{{ model.peerCount }} Fahrer</p>
            </div>
            <!-- Bar -->
            <div class="flex-1 flex items-center gap-1">
              <div v-if="model.isOwnSpec && model.consumption.ownValue !== null" class="relative h-5 bg-gradient-to-r from-blue-500 to-blue-400 rounded"
                :style="{ width: getBarWidth(model.consumption.ownValue, getMaxValue('consumption')) + '%' }">
                <!-- Split-Balken: blau (ownValue) + grau (peerAvg) -->
                <div class="absolute inset-0 flex" :style="{
                  width: '100%',
                  background: `linear-gradient(90deg, rgb(59, 130, 246) 0%, rgb(59, 130, 246) ${(model.consumption.ownValue / (model.consumption.ownValue + (model.consumption.peerAvg || 0)) * 100)}%, rgb(156, 163, 175) ${(model.consumption.ownValue / (model.consumption.ownValue + (model.consumption.peerAvg || 0)) * 100)}%, rgb(156, 163, 175) 100%)`
                }}"></div>
              </div>
              <div v-else class="h-5 bg-gray-300 dark:bg-gray-600 rounded"
                :style="{ width: getBarWidth(model.consumption.peerAvg, getMaxValue('consumption')) + '%' }"></div>
              <!-- Value -->
              <span class="text-xs font-semibold text-gray-700 dark:text-gray-300 whitespace-nowrap">
                {{ formatValue(model.isOwnSpec ? model.consumption.ownValue : model.consumption.peerAvg, 1) }}
              </span>
            </div>
          </div>
        </div>
      </div>

      <!-- Reichweite -->
      <div class="flex flex-col gap-3">
        <div class="flex items-center justify-between">
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">Reichweite (90→10%)</p>
          <p class="text-xs text-gray-400 dark:text-gray-500">km</p>
        </div>
        <div class="space-y-2">
          <div v-for="model in mockData" :key="model.modelName" class="flex items-center gap-2">
            <div class="w-32 text-xs truncate">
              <p class="font-medium text-gray-700 dark:text-gray-300 truncate">{{ model.modelName }}</p>
              <p class="text-xs text-gray-400 dark:text-gray-500">{{ model.peerCount }} Fahrer</p>
            </div>
            <div class="flex-1 flex items-center gap-1">
              <div v-if="model.isOwnSpec && model.range.ownValue !== null" class="relative h-5 bg-gradient-to-r from-green-500 to-green-400 rounded"
                :style="{ width: getBarWidth(model.range.ownValue, getMaxValue('range')) + '%' }">
                <div class="absolute inset-0 flex" :style="{
                  width: '100%',
                  background: `linear-gradient(90deg, rgb(34, 197, 94) 0%, rgb(34, 197, 94) ${(model.range.ownValue / (model.range.ownValue + (model.range.peerAvg || 0)) * 100)}%, rgb(156, 163, 175) ${(model.range.ownValue / (model.range.ownValue + (model.range.peerAvg || 0)) * 100)}%, rgb(156, 163, 175) 100%)`
                }}"></div>
              </div>
              <div v-else class="h-5 bg-gray-300 dark:bg-gray-600 rounded"
                :style="{ width: getBarWidth(model.range.peerAvg, getMaxValue('range')) + '%' }"></div>
              <span class="text-xs font-semibold text-gray-700 dark:text-gray-300 whitespace-nowrap">
                {{ formatValue(model.isOwnSpec ? model.range.ownValue : model.range.peerAvg, 0) }}
              </span>
            </div>
          </div>
        </div>
      </div>

      <!-- Stromkosten -->
      <div class="flex flex-col gap-3">
        <div class="flex items-center justify-between">
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">Stromkosten</p>
          <p class="text-xs text-gray-400 dark:text-gray-500">€/kWh</p>
        </div>
        <div class="space-y-2">
          <div v-for="model in mockData" :key="model.modelName" class="flex items-center gap-2">
            <div class="w-32 text-xs truncate">
              <p class="font-medium text-gray-700 dark:text-gray-300 truncate">{{ model.modelName }}</p>
              <p class="text-xs text-gray-400 dark:text-gray-500">{{ model.peerCount }} Fahrer</p>
            </div>
            <div class="flex-1 flex items-center gap-1">
              <div v-if="model.isOwnSpec && model.costPerKwh.ownValue !== null" class="relative h-5 bg-gradient-to-r from-purple-500 to-purple-400 rounded"
                :style="{ width: getBarWidth(model.costPerKwh.ownValue, getMaxValue('costPerKwh')) + '%' }">
                <div class="absolute inset-0 flex" :style="{
                  width: '100%',
                  background: `linear-gradient(90deg, rgb(168, 85, 247) 0%, rgb(168, 85, 247) ${(model.costPerKwh.ownValue / (model.costPerKwh.ownValue + (model.costPerKwh.peerAvg || 0)) * 100)}%, rgb(156, 163, 175) ${(model.costPerKwh.ownValue / (model.costPerKwh.ownValue + (model.costPerKwh.peerAvg || 0)) * 100)}%, rgb(156, 163, 175) 100%)`
                }}"></div>
              </div>
              <div v-else class="h-5 bg-gray-300 dark:bg-gray-600 rounded"
                :style="{ width: getBarWidth(model.costPerKwh.peerAvg, getMaxValue('costPerKwh')) + '%' }"></div>
              <span class="text-xs font-semibold text-gray-700 dark:text-gray-300 whitespace-nowrap">
                {{ formatValue(model.isOwnSpec ? model.costPerKwh.ownValue : model.costPerKwh.peerAvg, 2) }}
              </span>
            </div>
          </div>
        </div>
      </div>

      <!-- Fahrtkosten -->
      <div class="flex flex-col gap-3">
        <div class="flex items-center justify-between">
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">Fahrtkosten</p>
          <p class="text-xs text-gray-400 dark:text-gray-500">€/100km</p>
        </div>
        <div class="space-y-2">
          <div v-for="model in mockData" :key="model.modelName" class="flex items-center gap-2">
            <div class="w-32 text-xs truncate">
              <p class="font-medium text-gray-700 dark:text-gray-300 truncate">{{ model.modelName }}</p>
              <p class="text-xs text-gray-400 dark:text-gray-500">{{ model.peerCount }} Fahrer</p>
            </div>
            <div class="flex-1 flex items-center gap-1">
              <div v-if="model.isOwnSpec && model.costPer100km.ownValue !== null" class="relative h-5 bg-gradient-to-r from-orange-500 to-orange-400 rounded"
                :style="{ width: getBarWidth(model.costPer100km.ownValue, getMaxValue('costPer100km')) + '%' }">
                <div class="absolute inset-0 flex" :style="{
                  width: '100%',
                  background: `linear-gradient(90deg, rgb(249, 115, 22) 0%, rgb(249, 115, 22) ${(model.costPer100km.ownValue / (model.costPer100km.ownValue + (model.costPer100km.peerAvg || 0)) * 100)}%, rgb(156, 163, 175) ${(model.costPer100km.ownValue / (model.costPer100km.ownValue + (model.costPer100km.peerAvg || 0)) * 100)}%, rgb(156, 163, 175) 100%)`
                }}"></div>
              </div>
              <div v-else class="h-5 bg-gray-300 dark:bg-gray-600 rounded"
                :style="{ width: getBarWidth(model.costPer100km.peerAvg, getMaxValue('costPer100km')) + '%' }"></div>
              <span class="text-xs font-semibold text-gray-700 dark:text-gray-300 whitespace-nowrap">
                {{ formatValue(model.isOwnSpec ? model.costPer100km.ownValue : model.costPer100km.peerAvg, 2) }}
              </span>
            </div>
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
