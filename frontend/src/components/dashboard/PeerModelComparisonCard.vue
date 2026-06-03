<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ChevronDownIcon, ChevronLeftIcon, ChevronRightIcon, UserIcon } from '@heroicons/vue/24/outline'
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
  carBrandModel: string
}>()

const comparisons = ref<PeerModelComparisonItem[]>([])
const loading = ref(false)
const error = ref<string | null>(null)
const currentCategoryIndex = ref(0)
const slideDirection = ref('slide-next')
const hoveredSpecId = ref<string | null>(null)

const categories = [
  { id: 'consumption', label: t('dashboard.peer_consumption'), unit: 'kWh/100km', shortUnit: 'kWh/100km', color: 'blue', sortAsc: true },
  { id: 'costPerKwh', label: t('dashboard.peer_cost_label'), unit: '€/kWh', shortUnit: '€/kWh', color: 'purple', sortAsc: true },
  { id: 'costPer100km', label: t('dashboard.peer_cost_per_distance'), unit: '€/100km', shortUnit: '€/100km', color: 'orange', sortAsc: true },
  { id: 'range', label: t('dashboard.peer_range'), unit: 'km (Nettokapazität)', shortUnit: 'km', color: 'green', sortAsc: false },
]

const currentCategory = computed(() => categories[currentCategoryIndex.value])

const visibleComparisons = computed(() =>
  comparisons.value
    .filter(item => {
      if (item.isUserVehicleSpec) return true
      return getPeerMetricValue(item, currentCategory.value.id) !== null
    })
    .sort((a, b) => {
      const aVal = a.isUserVehicleSpec ? getUserMetricValue(a, currentCategory.value.id) : getPeerMetricValue(a, currentCategory.value.id)
      const bVal = b.isUserVehicleSpec ? getUserMetricValue(b, currentCategory.value.id) : getPeerMetricValue(b, currentCategory.value.id)
      if (aVal === null) return 1
      if (bVal === null) return -1
      return currentCategory.value.sortAsc ? aVal - bVal : bVal - aVal
    })
)

function nextCategory() {
  slideDirection.value = 'slide-next'
  currentCategoryIndex.value = (currentCategoryIndex.value + 1) % categories.length
}

function prevCategory() {
  slideDirection.value = 'slide-prev'
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
      return item.peerMetrics.avgCostPerKwh != null && item.peerMetrics.avgConsumptionKwhPer100km != null
        ? item.peerMetrics.avgCostPerKwh * item.peerMetrics.avgConsumptionKwhPer100km
        : null
    case 'range':
      return item.netBatteryCapacityKwh != null && item.peerMetrics.avgConsumptionKwhPer100km != null && item.peerMetrics.avgConsumptionKwhPer100km > 0
        ? (item.netBatteryCapacityKwh * 0.8 / item.peerMetrics.avgConsumptionKwhPer100km) * 100
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
      return item.userMetrics.costPerKwh != null && item.userMetrics.consumptionKwhPer100km != null
        ? item.userMetrics.costPerKwh * item.userMetrics.consumptionKwhPer100km
        : null
    case 'range':
      return item.netBatteryCapacityKwh != null && item.userMetrics.consumptionKwhPer100km != null && item.userMetrics.consumptionKwhPer100km > 0
        ? (item.netBatteryCapacityKwh * 0.8 / item.userMetrics.consumptionKwhPer100km) * 100
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

function getItemValue(item: PeerModelComparisonItem, categoryId: string): number | null {
  return item.isUserVehicleSpec
    ? getUserMetricValue(item, categoryId)
    : getPeerMetricValue(item, categoryId)
}

function getRelativePercent(item: PeerModelComparisonItem): string | null {
  if (!hoveredSpecId.value) return null
  const hoveredItem = visibleComparisons.value.find(i => i.vehicleSpecificationId === hoveredSpecId.value)
  if (!hoveredItem) return null
  const refVal = getItemValue(hoveredItem, currentCategory.value.id)
  const ownVal = getItemValue(item, currentCategory.value.id)
  if (refVal === null || ownVal === null || refVal === 0) return null
  if (item.vehicleSpecificationId === hoveredSpecId.value) return 'Referenz'
  const diff = Math.round((ownVal - refVal) / refVal * 100)
  return diff > 0 ? `+${diff}%` : `${diff}%`
}

function formatValue(value: number | null): string {
  if (value === null) return '—'
  return (Math.trunc(value * 100) / 100).toString()
}

function stripKwh(name: string): string {
  return name.replace(/\s*\(\d+(?:\.\d+)?kWh\)/g, '')
}

function getDisplayBarWidth(item: PeerModelComparisonItem): number {
  const catId = currentCategory.value.id
  if (item.isUserVehicleSpec && getUserMetricValue(item, catId) !== null) {
    return getBarWidth(
      Math.max(getUserMetricValue(item, catId)!, getPeerMetricValue(item, catId) || 0),
      getMaxValue(catId)
    )
  }
  return getBarWidth(getPeerMetricValue(item, catId), getMaxValue(catId))
}
</script>

<template>
  <div class="bg-white dark:bg-gray-700 rounded-sm border-2 border-gray-300 dark:border-gray-600 overflow-hidden shadow-[2px_2px_0_0_#d1d5db] dark:shadow-[2px_2px_0_0_#4b5563] flex flex-col">

    <!-- Header + Category Navigation (Combined) -->
    <div class="border-b border-gray-100 dark:border-gray-600">
      <!-- Mobile: Carousel-Navigation + Collapse -->
      <div class="sm:hidden flex items-center px-2 py-2.5 gap-1">
        <button @click="prevCategory" class="p-1.5 hover:bg-gray-100 dark:hover:bg-gray-600 rounded shrink-0">
          <ChevronLeftIcon class="w-4 h-4 text-gray-600 dark:text-gray-300" />
        </button>
        <button @click="toggleCollapsed" class="flex-1 flex flex-col items-center text-center min-w-0">
          <p class="text-xs font-semibold text-gray-800 dark:text-gray-200">Dein {{ carBrandModel }} im Vergleich</p>
          <p class="text-xs text-gray-500 dark:text-gray-400 mt-0.5">{{ currentCategory.label }} · {{ currentCategory.unit }} · {{ currentCategoryIndex + 1 }}/{{ categories.length }}</p>
        </button>
        <button @click="nextCategory" class="p-1.5 hover:bg-gray-100 dark:hover:bg-gray-600 rounded shrink-0">
          <ChevronRightIcon class="w-4 h-4 text-gray-600 dark:text-gray-300" />
        </button>
        <button @click="toggleCollapsed" class="p-1.5 shrink-0">
          <ChevronDownIcon
            class="w-4 h-4 text-gray-400 transition-transform duration-200"
            :class="{ 'rotate-180': !collapsed }" />
        </button>
      </div>

      <!-- sm+ -->
      <div class="hidden sm:flex items-center justify-between px-4 py-2.5">
        <button @click="prevCategory" class="p-1.5 hover:bg-gray-100 dark:hover:bg-gray-600 rounded">
          <ChevronLeftIcon class="w-4 h-4 text-gray-600 dark:text-gray-300" />
        </button>

        <div class="flex-1 text-center">
          <p class="text-sm font-semibold text-gray-800 dark:text-gray-200">Dein {{ carBrandModel }} im Vergleich</p>
          <p class="text-xs text-gray-400 dark:text-gray-500">{{ visibleComparisons.length }} Varianten · gesamter Zeitraum</p>
        </div>

        <div class="flex items-center gap-3">
          <div class="text-center">
            <p class="text-sm font-semibold text-gray-800 dark:text-gray-200">{{ currentCategory.label }}</p>
            <p class="text-xs text-gray-400 dark:text-gray-500">{{ currentCategory.unit }}</p>
          </div>
          <p class="text-xs text-gray-400 dark:text-gray-500 tabular-nums min-w-max">{{ currentCategoryIndex + 1 }}/{{ categories.length }}</p>
          <button @click="nextCategory" class="p-1.5 hover:bg-gray-100 dark:hover:bg-gray-600 rounded">
            <ChevronRightIcon class="w-4 h-4 text-gray-600 dark:text-gray-300" />
          </button>
        </div>
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
    <div v-else-if="visibleComparisons.length === 0" class="p-4 text-center text-xs text-gray-500 dark:text-gray-400">
      <p>Keine Vergleichsdaten verfügbar</p>
    </div>

    <!-- Category Carousel -->
    <div v-else-if="comparisons.length > 0" class="flex flex-col">

      <!-- Horizontal Bars Chart -->
      <Transition :name="slideDirection" mode="out-in">
      <div :key="currentCategoryIndex" class="px-3 py-4 sm:px-5 space-y-3">
        <div v-for="item in visibleComparisons" :key="item.vehicleSpecificationId"
          class="flex items-center"
          @mouseenter="hoveredSpecId = item.vehicleSpecificationId"
          @mouseleave="hoveredSpecId = null">
          <!-- Horizontal Bar Track -->
          <div class="flex-1 h-7 bg-gray-100 dark:bg-gray-600 rounded relative flex items-center">

            <!-- Balken-Bereich: reduziert so dass Zahlen rechts immer Platz haben -->
            <div class="h-full relative w-[calc(100%-2.5rem)] sm:w-[calc(100%-5rem)]">
              <!-- Split-Bar für user spec -->
              <div v-if="item.isUserVehicleSpec && getUserMetricValue(item, currentCategory.id) !== null"
                class="h-full rounded absolute inset-y-0 left-0 transition-opacity duration-150"
                :class="{ 'opacity-50': hoveredSpecId && item.vehicleSpecificationId !== hoveredSpecId }"
                :style="{
                  width: getBarWidth(Math.max(getUserMetricValue(item, currentCategory.id)!, getPeerMetricValue(item, currentCategory.id) || 0), getMaxValue(currentCategory.id)) + '%',
                  background: `linear-gradient(90deg, rgb(59, 130, 246) 0%, rgb(59, 130, 246) ${getUserMetricValue(item, currentCategory.id)! / Math.max((getUserMetricValue(item, currentCategory.id) || 0), (getPeerMetricValue(item, currentCategory.id) || 0)) * 100}%, rgb(156, 163, 175) ${getUserMetricValue(item, currentCategory.id)! / Math.max((getUserMetricValue(item, currentCategory.id) || 0), (getPeerMetricValue(item, currentCategory.id) || 0)) * 100}%, rgb(156, 163, 175) 100%)`
                }">
              </div>

              <!-- Regular bar für peers -->
              <div v-else
                class="h-full rounded absolute inset-y-0 left-0 bg-gray-400 dark:bg-gray-500 transition-opacity duration-150"
                :class="{ 'opacity-50': hoveredSpecId && item.vehicleSpecificationId !== hoveredSpecId }"
                :style="{ width: getBarWidth(getPeerMetricValue(item, currentCategory.id), getMaxValue(currentCategory.id)) + '%' }">
              </div>

              <!-- Prozent-Badge (nicht gedimmt) -->
              <span v-if="hoveredSpecId"
                class="hidden sm:flex items-center absolute top-1/2 -translate-y-1/2 -translate-x-full text-xs font-bold tabular-nums text-gray-900 dark:text-white z-10"
                :style="{ left: `calc(${getBarWidth(item.isUserVehicleSpec && getUserMetricValue(item, currentCategory.id) !== null ? Math.max(getUserMetricValue(item, currentCategory.id)!, getPeerMetricValue(item, currentCategory.id) || 0) : (getPeerMetricValue(item, currentCategory.id) ?? 0), getMaxValue(currentCategory.id))}% - 0.5rem)` }">
                {{ getRelativePercent(item) }}
              </span>

              <!-- Model name + icon (absolute overlay, begrenzt auf Balkenbreite) -->
              <div class="absolute inset-y-0 left-0 flex items-center px-2 pointer-events-none overflow-hidden"
                :style="{ width: getDisplayBarWidth(item) + '%' }">
                <p class="text-xs font-medium text-gray-700 dark:text-gray-300 truncate min-w-0">{{ stripKwh(item.displayName) }}</p>
                <div class="flex items-center gap-0.5 flex-shrink-0 ml-1">
                  <UserIcon class="w-3 h-3 text-gray-600 dark:text-gray-400" />
                  <span class="text-xs font-medium text-gray-700 dark:text-gray-300">{{ item.peerMetrics.uniqueCars }}</span>
                </div>
              </div>
            </div>

            <!-- Mobile: Wert am rechten Track-Rand -->
            <span class="sm:hidden absolute top-1/2 -translate-y-1/2 right-1 text-xs font-semibold text-gray-700 dark:text-gray-300 tabular-nums">
              {{ item.isUserVehicleSpec
                ? formatValue(getUserMetricValue(item, currentCategory.id))
                : formatValue(getPeerMetricValue(item, currentCategory.id)) }}
            </span>
            <!-- Desktop: Wert + Einheit direkt nach Balkenende -->
            <span class="hidden sm:block absolute top-1/2 -translate-y-1/2 pl-1.5 text-xs font-semibold text-gray-700 dark:text-gray-300 tabular-nums"
              :style="{ left: `calc(${getDisplayBarWidth(item) / 100} * (100% - 5rem))` }">
              {{ item.isUserVehicleSpec
                ? formatValue(getUserMetricValue(item, currentCategory.id))
                : formatValue(getPeerMetricValue(item, currentCategory.id)) }}
              <span class="font-normal text-gray-400 dark:text-gray-500">{{ currentCategory.shortUnit }}</span>
            </span>
          </div>

        </div>
      </div>
      </Transition>
    </div>

    </div>
    </Transition>


  </div>
</template>

<style scoped>
.slide-next-enter-active,
.slide-next-leave-active,
.slide-prev-enter-active,
.slide-prev-leave-active {
  transition: transform 240ms cubic-bezier(0.4, 0, 0.2, 1), opacity 240ms ease;
}

.slide-next-enter-from { transform: translateX(40px); opacity: 0; }
.slide-next-leave-to  { transform: translateX(-40px); opacity: 0; }

.slide-prev-enter-from { transform: translateX(-40px); opacity: 0; }
.slide-prev-leave-to  { transform: translateX(40px); opacity: 0; }

.fade-enter-active, .fade-leave-active { transition: opacity 120ms ease; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>
