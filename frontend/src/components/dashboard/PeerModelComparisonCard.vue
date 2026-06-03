<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ChevronDownIcon, ChevronLeftIcon, ChevronRightIcon, InformationCircleIcon } from '@heroicons/vue/24/outline'
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
const currentCategoryIndex = ref(0)

const categories = [
  { id: 'consumption', label: t('dashboard.peer_consumption'), unit: 'kWh/100km', color: 'blue' },
  { id: 'costPerKwh', label: t('dashboard.peer_cost_label'), unit: '€/kWh', color: 'purple' },
  { id: 'costPer100km', label: t('dashboard.peer_cost_per_distance'), unit: '€/100km', color: 'orange' },
]

const currentCategory = computed(() => categories[currentCategoryIndex.value])

function nextCategory() {
  currentCategoryIndex.value = (currentCategoryIndex.value + 1) % categories.length
}

function prevCategory() {
  currentCategoryIndex.value = (currentCategoryIndex.value - 1 + categories.length) % categories.length
}

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

    <!-- Category Carousel -->
    <div v-else class="flex flex-col">

      <!-- Category Header + Navigation -->
      <div class="px-4 py-3 flex items-center justify-between border-b border-gray-100 dark:border-gray-600">
        <button @click="prevCategory" class="p-2 hover:bg-gray-100 dark:hover:bg-gray-600 rounded">
          <ChevronLeftIcon class="w-4 h-4 text-gray-600 dark:text-gray-300" />
        </button>

        <div class="flex-1 text-center">
          <p class="text-sm font-semibold text-gray-800 dark:text-gray-200">{{ currentCategory.label }}</p>
          <p class="text-xs text-gray-400 dark:text-gray-500">{{ currentCategory.unit }}</p>
        </div>

        <div class="flex items-center gap-2">
          <p class="text-xs text-gray-400 dark:text-gray-500 tabular-nums">
            {{ currentCategoryIndex + 1 }}/{{ categories.length }}
          </p>
          <button @click="nextCategory" class="p-2 hover:bg-gray-100 dark:hover:bg-gray-600 rounded">
            <ChevronRightIcon class="w-4 h-4 text-gray-600 dark:text-gray-300" />
          </button>
        </div>
      </div>

      <!-- Bars -->
      <div class="p-4 space-y-2.5 max-h-96 overflow-y-auto">
        <div v-for="item in comparisons" :key="item.vehicleSpecificationId" class="flex items-center gap-2">
          <!-- Model name + peer count -->
          <div class="w-40 text-xs flex-shrink-0">
            <p class="font-medium text-gray-700 dark:text-gray-300 truncate">{{ item.displayName }}</p>
            <p class="text-xs text-gray-400 dark:text-gray-500">{{ item.peerMetrics.uniqueCars }} Fahrer</p>
          </div>

          <!-- Bar -->
          <div class="flex-1 flex items-center gap-2 min-w-0">
            <div class="flex-1 flex items-center gap-1">
              <!-- Split-bar für user spec -->
              <div v-if="item.isUserVehicleSpec && getUserMetricValue(item, currentCategory.id) !== null"
                class="relative h-6 rounded"
                :style="{
                  width: getBarWidth(getUserMetricValue(item, currentCategory.id)!, getMaxValue(currentCategory.id)) + '%',
                  background: `linear-gradient(90deg, rgb(59, 130, 246) 0%, rgb(59, 130, 246) ${getUserMetricValue(item, currentCategory.id)! / ((getUserMetricValue(item, currentCategory.id) || 0) + (getPeerMetricValue(item, currentCategory.id) || 0)) * 100}%, rgb(156, 163, 175) ${getUserMetricValue(item, currentCategory.id)! / ((getUserMetricValue(item, currentCategory.id) || 0) + (getPeerMetricValue(item, currentCategory.id) || 0)) * 100}%, rgb(156, 163, 175) 100%)`
                }">
              </div>
              <!-- Regular bar für peers -->
              <div v-else
                class="h-6 bg-gray-300 dark:bg-gray-600 rounded"
                :style="{ width: getBarWidth(getPeerMetricValue(item, currentCategory.id), getMaxValue(currentCategory.id)) + '%' }">
              </div>
            </div>

            <!-- Value -->
            <span class="text-xs font-semibold text-gray-700 dark:text-gray-300 whitespace-nowrap min-w-max">
              {{ formatValue(getPeerMetricValue(item, currentCategory.id), 2) }}
            </span>
          </div>
        </div>
      </div>
    </div>

    </div>
    </Transition>

    <!-- Footer: Erklärung -->
    <div class="px-4 py-2.5 border-t border-gray-100 dark:border-gray-600 flex items-center gap-2">
      <InformationCircleIcon class="w-3.5 h-3.5 text-blue-400 shrink-0" />
      <p class="text-xs text-gray-400 dark:text-gray-500">
        Farbig: deine Performance · Grau: Durchschnitt anderer Fahrer
      </p>
    </div>

  </div>
</template>
