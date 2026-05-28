<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, onActivated, onDeactivated, watch, defineAsyncComponent } from 'vue'
import { useI18n } from 'vue-i18n'
import { Line, Bar } from 'vue-chartjs'
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  BarElement,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend,
  Filler
} from 'chart.js'
import ChartDataLabels from 'chartjs-plugin-datalabels'
import {
  ChartBarIcon,
  TruckIcon,
  ArrowDownTrayIcon,
  ChevronRightIcon,
  ChevronDownIcon,
  ChevronUpIcon,
  ListBulletIcon,
  InformationCircleIcon,
  XMarkIcon,
  CalendarIcon,
  UsersIcon,
} from '@heroicons/vue/24/outline'
import { useRouter } from 'vue-router'
import LicensePlate from '../components/car/LicensePlate.vue'
const ChargingHeatMap = defineAsyncComponent(() => import('../components/dashboard/ChargingHeatMap.vue'))
import RewardSystemUpdateBanner from '../components/shared/RewardSystemUpdateBanner.vue'
import { useCountryStore } from '../stores/country'
import { useAuthStore } from '../stores/auth'
import { analytics } from '../services/analytics'
import ImplausibleLogsModal from '../components/dashboard/ImplausibleLogsModal.vue'
import PeerBenchmarkCard from '../components/dashboard/PeerBenchmarkCard.vue'
import WltpComparisonCard from '../components/dashboard/WltpComparisonCard.vue'
import RangeCard from '../components/dashboard/RangeCard.vue'
import LiveChargingCard from '../components/dashboard/LiveChargingCard.vue'
import DashboardEmptyState from '../components/dashboard/DashboardEmptyState.vue'
import DashboardInsights from '../components/dashboard/DashboardInsights.vue'
import ChargingTypeSplitCard from '../components/dashboard/ChargingTypeSplitCard.vue'
import ChargingEfficiencyCard from '../components/dashboard/ChargingEfficiencyCard.vue'
import CO2Card from '../components/dashboard/CO2Card.vue'
import SmartInsightsCard from '../components/dashboard/SmartInsightsCard.vue'
import CarCardDetails from '../components/dashboard/CarCardDetails.vue'
import { useLocaleFormat } from '../composables/useLocaleFormat'
import { useDashboardStats } from '../composables/useDashboardStats'
import { useDashboardCharts } from '../composables/useDashboardCharts'
import { useLogList } from '../composables/useLogList'
import { useStickyCarHeader } from '../composables/useStickyCarHeader'
import { useWallboxStore } from '../stores/wallbox'
import { carDisplayName } from '../utils/enumLabel'
import { isVwGroupBrand } from '../api/vwGroupService'

ChartJS.register(CategoryScale, LinearScale, BarElement, PointElement, LineElement, Title, Tooltip, Legend, Filler, ChartDataLabels)

const { t } = useI18n()
const router = useRouter()
const { formatConsumption, consumptionUnitLabel, formatDistance, distanceUnitLabel, formatCurrency, formatCostPerKwh, formatCostPerDistance, currencySymbol } = useLocaleFormat()

// -- Dashboard Stats --
const {
  selectedCarId, stats, lastMonthStats, carInfo, wltp, loading, chartsReady, isInitialLoad, error,
  cars, carImageUrls, selectedTimeRange, selectedGroupBy, customStartDate, customEndDate,
  importBannerDismissed, teslaStatus, smartcarStatus, vwGroupStatus, implausibleCount, hasDistanceData, avgCostPer100km,
  timeRangeOptions, groupByOptions, dismissImportBanner, fetchImplausibleCount,
  fetchCarAndWltp, fetchStatistics, initCars,
} = useDashboardStats()

// CUSTOM-Toggle: merkt sich den vorherigen Zeitraum, damit Klick auf das aktive
// CUSTOM-Button zur letzten Auswahl zurückspringt statt nur aufzuklappen.
const previousTimeRange = ref<string>(
  selectedTimeRange.value !== 'CUSTOM' ? selectedTimeRange.value : 'LAST_3_MONTHS'
)
function setTimeRange(value: string) {
  if (value === 'CUSTOM' && selectedTimeRange.value === 'CUSTOM') {
    selectedTimeRange.value = previousTimeRange.value
    showFilterDropdown.value = false
    return
  }
  if (value !== 'CUSTOM') {
    previousTimeRange.value = value
    showFilterDropdown.value = false
  }
  selectedTimeRange.value = value
}

// -- Charts --
const {
  showCostPerKwh, showKwh, showDistance, showConsumption,
  customCompareValue, customCompareInput, showCompareInput,
  isCustomCompare, saveCustomCompare, resetToWltp,
  chargingChartData, chargingChartOptions, efficiencyChartData, efficiencyChartOptions,
  wltpChartData, wltpChartOptions, wltpChartHeight, wltpChartScrollable,
} = useDashboardCharts(stats, wltp, hasDistanceData, selectedGroupBy)

// -- Log List (used by DashboardInsights via mergedLogFeed) --
const logsSection = ref<HTMLElement | null>(null)
const {
  hasAnyLogs, mergedLogFeed, fetchLogs, logs,
} = useLogList(selectedCarId, cars, logsSection)

const currentOdometerKm = computed<number | null>(() => {
  let max: number | null = null
  for (const l of logs.value) {
    const o = l.odometerKm
    if (typeof o === 'number' && (max == null || o > max)) max = o
  }
  return max
})


// -- Implausible logs modal --
const showImplausibleModal = ref(false)
const implausibleModalDirty = ref(false)

// -- Range calculator --

const selectedCar = computed(() =>
  cars.value.find(c => c.id === selectedCarId.value) ?? cars.value[0] ?? null
)

const wallboxStore = useWallboxStore()

const isSmartcarCharging = (car: any) =>
  smartcarStatus.value?.connected === true &&
  smartcarStatus.value?.vehicleState === 'CHARGING' &&
  (smartcarStatus.value?.carId === car.id ||
    (smartcarStatus.value?.carId === null && cars.value.length === 1))

// Wallbox kennt keine carId → Glow nur bei Single-Car sicher zuordenbar
const isWallboxCharging = () =>
  wallboxStore.isCharging && cars.value.length === 1

const isVwGroupCharging = (car: any) =>
  isVwGroupBrand(car.brand) &&
  vwGroupStatus.value?.connected === true &&
  vwGroupStatus.value?.vehicleState === 'charging'

const isVehicleCharging = (car: any) => isSmartcarCharging(car) || isVwGroupCharging(car) || isWallboxCharging()

// -- Lifecycle --
watch(selectedCarId, async (newId) => {
  if (newId) {
    await fetchCarAndWltp(newId)
    await Promise.all([fetchStatistics(), fetchLogs(0), fetchImplausibleCount()])
  } else {
    stats.value = null
    carInfo.value = null
    wltp.value = null
    implausibleCount.value = 0
  }
})


// -- THG Banner (DE only) --
const countryStore = useCountryStore()
const authStore    = useAuthStore()
const isGerman     = computed(() => countryStore.country === 'DE')
const thgDismissedAt = ref<number | null>(
  Number(localStorage.getItem('thg_banner_dismissed_at')) || null
)
const showThgBanner = computed(() => {
  if (!thgDismissedAt.value) return true
  return (Date.now() - thgDismissedAt.value) / 86_400_000 >= 90
})
function dismissThgBanner() {
  const now = Date.now()
  thgDismissedAt.value = now
  localStorage.setItem('thg_banner_dismissed_at', String(now))
}
function handleThgCardClick() {
  analytics.trackAffiliateBannerClicked('thg')
  window.open('https://Geld-fuer-eAuto.de/ref/evmonitor', '_blank', 'noopener,noreferrer')
}

// -- Stat tile tooltips --
const openMetricTooltip = ref<'distance' | 'consumption' | 'costPer100km' | null>(null)

// -- Peer Benchmark empty-state collapse (default collapsed to save space) --
const LS_PEER_PLACEHOLDER = 'peer_placeholder_collapsed'
const peerPlaceholderCollapsed = ref(localStorage.getItem(LS_PEER_PLACEHOLDER) !== 'false')
function togglePeerPlaceholder() {
  peerPlaceholderCollapsed.value = !peerPlaceholderCollapsed.value
  localStorage.setItem(LS_PEER_PLACEHOLDER, String(peerPlaceholderCollapsed.value))
}

// -- Car card expanded details on mobile (single-car mode) --
const LS_CAR_CARD_EXPANDED = 'ev_dashboard_car_card_expanded'
const carCardExpanded = ref(localStorage.getItem(LS_CAR_CARD_EXPANDED) === 'true')
function toggleCarCardExpanded() {
  carCardExpanded.value = !carCardExpanded.value
  localStorage.setItem(LS_CAR_CARD_EXPANDED, String(carCardExpanded.value))
}
// True if there is at least one extra detail worth showing
const hasCarCardDetails = computed(() => {
  const c = selectedCar.value
  if (!c) return false
  return !!(
    (c.effectiveBatteryCapacityKwh ?? c.customNetCapacityKwh) ||
    wltp.value?.officialRangeKm ||
    c.powerKw ||
    c.year ||
    c.hasHeatPump ||
    c.isBusinessCar ||
    currentOdometerKm.value != null
  )
})

const viewActive = ref(true)
const showFilterDropdown = ref(false)
const filterDropdownDesktop = ref<HTMLElement | null>(null)
const filterDropdownMobile = ref<HTMLElement | null>(null)
const filterBarVisible = computed(() => viewActive.value && !!selectedCarId.value && hasAnyLogs.value)

function onClickOutsideFilter(e: MouseEvent) {
  const t = e.target as Node
  if (!filterDropdownDesktop.value?.contains(t) && !filterDropdownMobile.value?.contains(t)) {
    showFilterDropdown.value = false
  }
}

onMounted(() => {
  initCars()
  document.addEventListener('click', onClickOutsideFilter)
})
onUnmounted(() => { document.removeEventListener('click', onClickOutsideFilter) })
onActivated(() => {
  viewActive.value = true
  document.addEventListener('click', onClickOutsideFilter)
})
onDeactivated(() => {
  viewActive.value = false
  document.removeEventListener('click', onClickOutsideFilter)
})

// -- Sticky car header compact mode --
const stickyCarBar = ref<HTMLElement | null>(null)
const { isCarHeaderSticky } = useStickyCarHeader(stickyCarBar)


</script>

<template>
<div>
  <div class="md:max-w-6xl md:mx-auto md:p-6">
    <RewardSystemUpdateBanner class="mb-4" />
    <Transition name="fade" mode="out-in">
      <div v-if="!loading || !isInitialLoad">
        <div class="bg-gray-100 dark:bg-gray-800 md:rounded-sm md:shadow-[4px_4px_0_rgba(0,0,0,0.30)] dark:md:shadow-[4px_4px_0_rgba(255,255,255,0.30)] p-4 md:p-6 pb-6">
          <div class="flex flex-wrap items-center gap-3 mb-6">
            <ChartBarIcon class="h-8 w-8 text-gray-700 dark:text-gray-300" />
            <h1 class="text-3xl font-bold text-gray-800 dark:text-gray-200">Dashboard</h1>
            <!-- Mobile: Logs & Trips -->
            <div v-if="stats && stats.totalCharges > 0" class="sm:hidden ml-auto">
              <router-link
                to="/logs"
                class="flex items-center gap-1.5 px-3 py-1.5 rounded-sm bg-indigo-600 text-white text-sm font-medium shadow-[0_4px_0_0_#3730a3] hover:shadow-[0_2px_0_0_#3730a3] hover:translate-y-0.5 active:shadow-none active:translate-y-1 transition-all duration-75">
                Logs & Trips
                <ChevronRightIcon class="w-3 h-3 opacity-75" />
              </router-link>
            </div>
            <!-- Desktop: Filter + Fahrzeuge + Logs -->
            <div class="hidden sm:flex items-center gap-2 ml-auto">
              <div v-if="filterBarVisible" class="relative" ref="filterDropdownDesktop">
                  <button
                    @click.stop="showFilterDropdown = !showFilterDropdown"
                    class="btn-3d flex items-center gap-2 px-4 py-2 rounded-sm border border-gray-300 dark:border-gray-600 text-gray-700 dark:text-gray-300 text-sm font-medium">
                    <CalendarIcon class="w-4 h-4" />
                    <span>{{ timeRangeOptions.find(o => o.value === selectedTimeRange)?.shortLabel ?? selectedTimeRange }}</span>
                    <span class="text-gray-300 dark:text-gray-500">·</span>
                    <span>{{ groupByOptions.find(o => o.value === selectedGroupBy)?.label }}</span>
                    <ChevronDownIcon class="w-3.5 h-3.5 opacity-60 transition-transform" :class="{ 'rotate-180': showFilterDropdown }" />
                  </button>
                  <Transition name="dropdown">
                    <div v-if="showFilterDropdown"
                      class="absolute right-0 top-full mt-1.5 z-40 bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-sm shadow-[2px_2px_0_0_#d1d5db] dark:shadow-[2px_2px_0_0_#374151] p-3 w-72"
                      @click.stop>
                      <p class="text-[10px] font-semibold uppercase tracking-wide text-gray-400 dark:text-gray-500 mb-2">{{ t('dashboard.time_range_label') }}</p>
                      <div class="flex flex-wrap gap-1.5 mb-3">
                        <button v-for="option in timeRangeOptions" :key="option.value" @click="setTimeRange(option.value)"
                          :class="['inline-flex items-center gap-1 px-2.5 py-1 text-xs font-medium rounded-sm transition', selectedTimeRange === option.value ? 'bg-indigo-600 text-white' : 'bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-300 hover:bg-gray-200 dark:hover:bg-gray-600']">
                          <CalendarIcon v-if="option.value === 'CUSTOM'" class="w-3 h-3" />
                          {{ option.shortLabel }}
                        </button>
                      </div>
                      <div v-if="selectedTimeRange === 'CUSTOM'" class="flex items-center gap-2 mb-3">
                        <div class="flex-1 relative">
                          <input type="date" v-model="customStartDate" :max="customEndDate || undefined" :aria-label="t('dashboard.time_custom_from')"
                            class="block w-full px-2 pr-7 py-1.5 text-xs border border-gray-300 dark:border-gray-600 rounded-sm bg-white dark:bg-gray-700 text-gray-800 dark:text-gray-200 focus:outline-none focus:ring-1 focus:ring-indigo-500 [&::-webkit-calendar-picker-indicator]:opacity-0 [&::-webkit-calendar-picker-indicator]:absolute [&::-webkit-calendar-picker-indicator]:right-0 [&::-webkit-calendar-picker-indicator]:w-8 [&::-webkit-calendar-picker-indicator]:h-full [&::-webkit-calendar-picker-indicator]:cursor-pointer" />
                          <CalendarIcon class="pointer-events-none absolute right-2 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-gray-400" />
                        </div>
                        <span class="text-gray-400 text-xs shrink-0">→</span>
                        <div class="flex-1 relative">
                          <input type="date" v-model="customEndDate" :min="customStartDate || undefined" :aria-label="t('dashboard.time_custom_to')"
                            class="block w-full px-2 pr-7 py-1.5 text-xs border border-gray-300 dark:border-gray-600 rounded-sm bg-white dark:bg-gray-700 text-gray-800 dark:text-gray-200 focus:outline-none focus:ring-1 focus:ring-indigo-500 [&::-webkit-calendar-picker-indicator]:opacity-0 [&::-webkit-calendar-picker-indicator]:absolute [&::-webkit-calendar-picker-indicator]:right-0 [&::-webkit-calendar-picker-indicator]:w-8 [&::-webkit-calendar-picker-indicator]:h-full [&::-webkit-calendar-picker-indicator]:cursor-pointer" />
                          <CalendarIcon class="pointer-events-none absolute right-2 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-gray-400" />
                        </div>
                      </div>
                      <div class="pt-2 border-t border-gray-100 dark:border-gray-700">
                        <div class="flex items-center gap-1.5 mb-1.5">
                          <ListBulletIcon class="h-3.5 w-3.5 text-gray-400 shrink-0" />
                          <span class="text-xs text-gray-500 dark:text-gray-400">{{ t('dashboard.group_by_label') }}</span>
                        </div>
                        <div class="flex flex-wrap gap-1.5">
                          <button v-for="opt in groupByOptions" :key="opt.value" @click="selectedGroupBy = opt.value; showFilterDropdown = false"
                            :class="['px-2.5 py-1 text-xs font-medium rounded-sm transition', selectedGroupBy === opt.value ? 'bg-indigo-600 text-white' : 'bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-300 hover:bg-gray-200 dark:hover:bg-gray-600']">
                            {{ opt.label }}
                          </button>
                        </div>
                      </div>
                    </div>
                  </Transition>
                </div>
              <router-link
                to="/cars"
                class="btn-3d flex items-center gap-2 px-4 py-2 rounded-sm border border-gray-300 dark:border-gray-600 text-gray-700 dark:text-gray-300 text-sm font-medium">
                <TruckIcon class="w-4 h-4" />
                {{ t('dashboard.vehicles_btn') }}
              </router-link>
              <router-link v-if="stats && stats.totalCharges > 0"
                to="/logs"
                class="btn-3d flex items-center gap-2 px-4 py-2 rounded-sm bg-indigo-600 text-white text-sm font-medium" style="--btn-shadow-color: #3730a3">
                <ListBulletIcon class="w-4 h-4" />
                {{ t('dashboard.logs_btn') }}
                <ChevronRightIcon class="w-3.5 h-3.5 opacity-75" />
              </router-link>
            </div>
          </div>

          <!-- Import Hint Banner -->
          <div v-if="!importBannerDismissed" class="relative mb-6">
            <router-link
              to="/imports"
              class="flex items-center gap-3 bg-green-50 dark:bg-green-900/30 border border-green-200 dark:border-green-700 rounded-sm px-4 py-3 hover:bg-green-100 dark:hover:bg-green-900/40 transition group"
            >
              <ArrowDownTrayIcon class="h-5 w-5 text-green-600 dark:text-green-400 shrink-0" />
              <div class="flex-1 min-w-0">
                <span class="text-sm font-medium text-green-800 dark:text-green-200">{{ t('dashboard.import_banner') }}</span>
                <span class="text-sm text-green-700 dark:text-green-300 ml-1">{{ t('dashboard.import_banner_sources') }}</span>
              </div>
              <span class="text-green-600 dark:text-green-400 text-sm group-hover:translate-x-0.5 transition-transform">→</span>
            </router-link>
            <button
              @click="dismissImportBanner"
              class="absolute -top-2 -right-2 h-5 w-5 rounded-sm bg-green-200 hover:bg-green-300 text-green-700 flex items-center justify-center transition"
              title="Hinweis ausblenden"
            >
              <XMarkIcon class="h-3 w-3" />
            </button>
          </div>

          <!-- Car card selector (all breakpoints) -->
          <div
            v-if="cars.length > 0"
            ref="stickyCarBar"
            :class="[
              cars.length > 1
                ? 'sticky top-16 z-10 bg-white dark:bg-gray-800 -mx-4 px-4 md:-mx-6 md:px-6 py-1.5 md:py-3 mb-3 border-b border-gray-100 dark:border-gray-700 shadow-sm'
                : 'mb-6 md:w-fit md:mx-auto',
              isCarHeaderSticky ? 'car-header-compact' : ''
            ]"
          >
            <div class="flex gap-3 overflow-x-auto car-scroll-hide pb-1 lg:flex-wrap lg:overflow-x-visible">
              <button
                v-for="car in cars"
                :key="car.id"
                @click="selectedCarId = car.id"
                :class="[
                  cars.length === 1
                    ? 'flex items-center md:items-stretch rounded-sm border-2 text-left transition w-full md:w-auto overflow-hidden'
                    : 'flex items-center md:items-stretch rounded-sm border-2 text-left transition flex-shrink-0 min-w-[180px] max-w-[240px] lg:flex-shrink lg:min-w-0 lg:max-w-none overflow-hidden',
                  selectedCarId === car.id
                    ? isVehicleCharging(car)
                      ? 'border-2 border-green-500 bg-green-50 dark:bg-green-900/20 shadow-[2px_2px_0_0_#16a34a] dark:shadow-[2px_2px_0_0_#14532d]'
                      : 'border-2 border-indigo-500 bg-indigo-50 dark:bg-indigo-900/30 shadow-[2px_2px_0_0_#4338ca] dark:shadow-[2px_2px_0_0_#312e81]'
                    : isVehicleCharging(car)
                      ? 'border-2 border-green-300 dark:border-green-700 bg-white dark:bg-gray-700 shadow-[2px_2px_0_0_#d1d5db] dark:shadow-[2px_2px_0_0_#374151]'
                      : 'border-2 border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-700 shadow-[2px_2px_0_0_#d1d5db] dark:shadow-[2px_2px_0_0_#374151] hover:border-indigo-300',
                  cars.length > 1 && isVehicleCharging(car) ? 'ring-2 ring-green-400 dark:ring-green-500' : '',
                ]" style="transition: transform 0.075s ease, box-shadow 0.075s ease;">
                <div class="flex-shrink-0 h-12 aspect-[4/3] md:w-20 md:h-auto md:aspect-auto md:self-stretch bg-gray-100 dark:bg-gray-600 flex items-center justify-center overflow-hidden compact-shrink-thumb">
                  <img
                    v-if="carImageUrls[car.id]"
                    :src="carImageUrls[car.id]"
                    :alt="car.model"
                    class="w-full h-full object-cover" />
                  <TruckIcon v-else class="w-6 h-6 md:w-8 md:h-8 text-gray-400" />
                </div>
                <div class="min-w-0 flex-1 px-3 py-1.5 md:px-3 md:py-2 compact-shrink-pad">
                  <!-- Mobile compact (alle Autos): eine Zeile -->
                  <div class="flex items-center gap-1.5 flex-wrap md:hidden compact-nowrap">
                    <span class="font-semibold text-sm text-gray-800 dark:text-gray-200 whitespace-nowrap">{{ carDisplayName(car.brand, car.model) }}</span>
                    <span v-if="car.trim" class="text-xs text-gray-500 dark:text-gray-400 compact-hide">{{ car.trim }}</span>
                    <template v-if="!isCarHeaderSticky">
                      <template v-if="car.brand?.toLowerCase() === 'tesla' && teslaStatus?.connected && (teslaStatus.carId === car.id || teslaStatus.carId === null)">
                        <span v-if="teslaStatus.vehicleState === 'charging'"
                          class="inline-flex items-center gap-1 px-1.5 py-0.5 bg-green-100 text-green-700 text-xs rounded-full font-medium border border-green-200">
                          <span class="w-1.5 h-1.5 rounded-full bg-green-500 animate-pulse"></span>{{ t('dashboard.tesla_charging') }}
                        </span>
                        <span v-else-if="teslaStatus.vehicleState === 'online'"
                          class="inline-flex items-center gap-1 px-1.5 py-0.5 bg-blue-50 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400 text-xs rounded-full font-medium border border-blue-200 dark:border-blue-700">
                          <span class="w-1.5 h-1.5 rounded-full bg-blue-400"></span>{{ t('dashboard.tesla_online') }}
                        </span>
                        <span v-else-if="teslaStatus.vehicleState === 'asleep'"
                          class="inline-flex items-center gap-1 px-1.5 py-0.5 bg-gray-100 text-gray-500 text-xs rounded-full font-medium border border-gray-300">
                          <span class="w-1.5 h-1.5 rounded-full bg-gray-400"></span>{{ t('dashboard.tesla_sleeping') }}
                        </span>
                      </template>
                      <span v-if="isSmartcarCharging(car)"
                        class="inline-flex items-center gap-1 px-1.5 py-0.5 bg-green-100 dark:bg-green-900/40 text-green-700 dark:text-green-400 text-xs rounded-full font-medium border border-green-200 dark:border-green-700">
                        <span class="w-1.5 h-1.5 rounded-full bg-green-500 dark:bg-green-400 animate-pulse"></span>{{ t('dashboard.smartcar_charging') }}
                      </span>
                      <template v-if="isVwGroupBrand(car.brand) && vwGroupStatus?.connected">
                        <span v-if="vwGroupStatus.vehicleState === 'charging'"
                          class="inline-flex items-center gap-1 px-1.5 py-0.5 bg-green-100 dark:bg-green-900/40 text-green-700 dark:text-green-400 text-xs rounded-full font-medium border border-green-200 dark:border-green-700">
                          <span class="w-1.5 h-1.5 rounded-full bg-green-500 dark:bg-green-400 animate-pulse"></span>
                          {{ t('dashboard.vwgroup_charging') }}
                          <span v-if="vwGroupStatus.lastSoc != null" class="opacity-75">· {{ vwGroupStatus.lastSoc }}%</span>
                        </span>
                        <span v-else
                          class="inline-flex items-center gap-1 px-1.5 py-0.5 bg-blue-50 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400 text-xs rounded-full font-medium border border-blue-200 dark:border-blue-700">
                          <span class="w-1.5 h-1.5 rounded-full bg-blue-400"></span>
                          {{ vwGroupStatus.lastSoc != null ? vwGroupStatus.lastSoc + '%' : t('dashboard.vwgroup_connected') }}
                        </span>
                      </template>
                      <span v-if="isWallboxCharging()"
                        class="inline-flex items-center gap-1 px-1.5 py-0.5 bg-green-100 dark:bg-green-900/40 text-green-700 dark:text-green-400 text-xs rounded-full font-medium border border-green-200 dark:border-green-700">
                        <span class="w-1.5 h-1.5 rounded-full bg-green-500 dark:bg-green-400 animate-pulse"></span>{{ t('dashboard.wallbox_charging') }}
                      </span>
                    </template>
                  </div>
                  <!-- Desktop: zweizeiliges Layout -->
                  <div :class="cars.length === 1 ? 'hidden lg:block' : 'hidden md:block'">
                    <div class="flex items-center gap-2 flex-wrap">
                      <span class="font-semibold text-gray-800 dark:text-gray-200">{{ carDisplayName(car.brand, car.model) }}</span>
                      <span v-if="car.trim" class="text-sm text-gray-500 dark:text-gray-400">{{ car.trim }}</span>
                      <span v-if="car.isPrimary && cars.length > 1"
                        class="px-1.5 py-0.5 bg-green-100 dark:bg-green-900/40 text-green-700 dark:text-green-400 text-xs rounded-full border border-green-200 dark:border-green-700 font-medium">
                        {{ t('dashboard.active') }}
                      </span>
                      <template v-if="car.brand?.toLowerCase() === 'tesla' && teslaStatus?.connected && (teslaStatus.carId === car.id || teslaStatus.carId === null)">
                        <span v-if="teslaStatus.vehicleState === 'charging'"
                          class="inline-flex items-center gap-1 px-1.5 py-0.5 bg-green-100 dark:bg-green-900/40 text-green-700 dark:text-green-400 text-xs rounded-full font-medium border border-green-200 dark:border-green-700">
                          <span class="w-1.5 h-1.5 rounded-full bg-green-500 dark:bg-green-400 animate-pulse"></span>{{ t('dashboard.tesla_charging') }}
                        </span>
                        <span v-else-if="teslaStatus.vehicleState === 'online'"
                          class="inline-flex items-center gap-1 px-1.5 py-0.5 bg-blue-50 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400 text-xs rounded-full font-medium border border-blue-200 dark:border-blue-700">
                          <span class="w-1.5 h-1.5 rounded-full bg-blue-400"></span>{{ t('dashboard.tesla_online') }}
                        </span>
                        <span v-else-if="teslaStatus.vehicleState === 'asleep'"
                          class="inline-flex items-center gap-1 px-1.5 py-0.5 bg-gray-100 dark:bg-gray-700 text-gray-500 dark:text-gray-400 text-xs rounded-full font-medium border border-gray-200 dark:border-gray-600">
                          <span class="w-1.5 h-1.5 rounded-full bg-gray-400 dark:bg-gray-500"></span>{{ t('dashboard.tesla_sleeping') }}
                        </span>
                      </template>
                      <span v-if="isSmartcarCharging(car)"
                        class="inline-flex items-center gap-1 px-1.5 py-0.5 bg-green-100 dark:bg-green-900/40 text-green-700 dark:text-green-400 text-xs rounded-full font-medium border border-green-200 dark:border-green-700">
                        <span class="w-1.5 h-1.5 rounded-full bg-green-500 dark:bg-green-400 animate-pulse"></span>{{ t('dashboard.smartcar_charging') }}
                      </span>
                      <template v-if="isVwGroupBrand(car.brand) && vwGroupStatus?.connected">
                        <span v-if="vwGroupStatus.vehicleState === 'charging'"
                          class="inline-flex items-center gap-1 px-1.5 py-0.5 bg-green-100 dark:bg-green-900/40 text-green-700 dark:text-green-400 text-xs rounded-full font-medium border border-green-200 dark:border-green-700">
                          <span class="w-1.5 h-1.5 rounded-full bg-green-500 dark:bg-green-400 animate-pulse"></span>
                          {{ t('dashboard.vwgroup_charging') }}
                          <span v-if="vwGroupStatus.lastSoc != null" class="opacity-75">· {{ vwGroupStatus.lastSoc }}%</span>
                        </span>
                        <span v-else
                          class="inline-flex items-center gap-1 px-1.5 py-0.5 bg-blue-50 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400 text-xs rounded-full font-medium border border-blue-200 dark:border-blue-700">
                          <span class="w-1.5 h-1.5 rounded-full bg-blue-400"></span>
                          {{ vwGroupStatus.lastSoc != null ? vwGroupStatus.lastSoc + '%' : t('dashboard.vwgroup_connected') }}
                        </span>
                      </template>
                      <span v-if="isWallboxCharging()"
                        class="inline-flex items-center gap-1 px-1.5 py-0.5 bg-green-100 dark:bg-green-900/40 text-green-700 dark:text-green-400 text-xs rounded-full font-medium border border-green-200 dark:border-green-700">
                        <span class="w-1.5 h-1.5 rounded-full bg-green-500 dark:bg-green-400 animate-pulse"></span>{{ t('dashboard.wallbox_charging') }}
                      </span>
                    </div>
                    <div class="mt-1.5 flex justify-center">
                      <LicensePlate v-if="car.licensePlate" :plate="car.licensePlate" />
                    </div>
                  </div>
                </div>
                <!-- Desktop horizontal extension (single-car only, lg+): chips column -->
                <div
                  v-if="cars.length === 1 && car.id === selectedCarId"
                  class="hidden lg:flex flex-shrink-0 self-stretch items-center border-l border-gray-200 dark:border-gray-600 pl-3 pr-3 py-2"
                >
                  <CarCardDetails :car="car" :wltp="wltp" :current-odometer-km="currentOdometerKm" orientation="horizontal" />
                </div>
              </button>
            </div>
            <!-- Mobile/tablet: Mehr Details + Filter in einer Zeile (<lg) -->
            <div v-if="(cars.length === 1 && selectedCar && hasCarCardDetails) || filterBarVisible"
              class="lg:hidden mt-1.5 relative flex items-center justify-between" ref="filterDropdownMobile">
              <!-- Mehr Details toggle (links) -->
              <button
                v-if="cars.length === 1 && selectedCar && hasCarCardDetails"
                type="button"
                @click="toggleCarCardExpanded"
                :aria-expanded="carCardExpanded"
                aria-controls="car-card-details-panel"
                class="inline-flex items-center gap-1 text-xs text-gray-500 dark:text-gray-400 hover:text-indigo-600 dark:hover:text-indigo-400 px-2 py-1 rounded-sm transition"
              >
                <component :is="carCardExpanded ? ChevronUpIcon : ChevronDownIcon" class="w-4 h-4" />
                <span>{{ carCardExpanded ? t('dashboard.car_card_show_less') : t('dashboard.car_card_show_more') }}</span>
              </button>
              <div v-else class="flex-1" />
              <!-- Filter trigger (rechts, nur Mobile) -->
              <div v-if="filterBarVisible" class="sm:hidden relative">
                <button
                  @click.stop="showFilterDropdown = !showFilterDropdown"
                  class="flex items-center gap-1.5 px-2.5 py-1 rounded-sm border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-700 text-gray-600 dark:text-gray-300 text-xs font-medium hover:bg-gray-50 dark:hover:bg-gray-600 transition">
                  <CalendarIcon class="w-3 h-3 opacity-60" />
                  <span>{{ timeRangeOptions.find(o => o.value === selectedTimeRange)?.shortLabel ?? selectedTimeRange }}</span>
                  <span class="text-gray-300 dark:text-gray-500">·</span>
                  <span>{{ groupByOptions.find(o => o.value === selectedGroupBy)?.label }}</span>
                  <ChevronDownIcon class="w-3 h-3 opacity-50 transition-transform" :class="{ 'rotate-180': showFilterDropdown }" />
                </button>
                <Transition name="dropdown">
                  <div v-if="showFilterDropdown"
                    class="absolute right-0 top-full mt-1.5 z-40 bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-sm shadow-[2px_2px_0_0_#d1d5db] dark:shadow-[2px_2px_0_0_#374151] p-3 w-72"
                    @click.stop>
                    <p class="text-[10px] font-semibold uppercase tracking-wide text-gray-400 dark:text-gray-500 mb-2">{{ t('dashboard.time_range_label') }}</p>
                    <div class="flex flex-wrap gap-1.5 mb-3">
                      <button v-for="option in timeRangeOptions" :key="option.value" @click="setTimeRange(option.value)"
                        :class="['inline-flex items-center gap-1 px-2.5 py-1 text-xs font-medium rounded-sm transition', selectedTimeRange === option.value ? 'bg-indigo-600 text-white' : 'bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-300 hover:bg-gray-200 dark:hover:bg-gray-600']">
                        <CalendarIcon v-if="option.value === 'CUSTOM'" class="w-3 h-3" />
                        {{ option.shortLabel }}
                      </button>
                    </div>
                    <div v-if="selectedTimeRange === 'CUSTOM'" class="flex items-center gap-2 mb-3">
                      <div class="flex-1 relative">
                        <input type="date" v-model="customStartDate" :max="customEndDate || undefined" :aria-label="t('dashboard.time_custom_from')"
                          class="block w-full px-2 pr-7 py-1.5 text-xs border border-gray-300 dark:border-gray-600 rounded-sm bg-white dark:bg-gray-700 text-gray-800 dark:text-gray-200 focus:outline-none focus:ring-1 focus:ring-indigo-500 [&::-webkit-calendar-picker-indicator]:opacity-0 [&::-webkit-calendar-picker-indicator]:absolute [&::-webkit-calendar-picker-indicator]:right-0 [&::-webkit-calendar-picker-indicator]:w-8 [&::-webkit-calendar-picker-indicator]:h-full [&::-webkit-calendar-picker-indicator]:cursor-pointer" />
                        <CalendarIcon class="pointer-events-none absolute right-2 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-gray-400" />
                      </div>
                      <span class="text-gray-400 text-xs shrink-0">→</span>
                      <div class="flex-1 relative">
                        <input type="date" v-model="customEndDate" :min="customStartDate || undefined" :aria-label="t('dashboard.time_custom_to')"
                          class="block w-full px-2 pr-7 py-1.5 text-xs border border-gray-300 dark:border-gray-600 rounded-sm bg-white dark:bg-gray-700 text-gray-800 dark:text-gray-200 focus:outline-none focus:ring-1 focus:ring-indigo-500 [&::-webkit-calendar-picker-indicator]:opacity-0 [&::-webkit-calendar-picker-indicator]:absolute [&::-webkit-calendar-picker-indicator]:right-0 [&::-webkit-calendar-picker-indicator]:w-8 [&::-webkit-calendar-picker-indicator]:h-full [&::-webkit-calendar-picker-indicator]:cursor-pointer" />
                        <CalendarIcon class="pointer-events-none absolute right-2 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-gray-400" />
                      </div>
                    </div>
                    <div class="pt-2 border-t border-gray-100 dark:border-gray-700">
                      <div class="flex items-center gap-1.5 mb-1.5">
                        <ListBulletIcon class="h-3.5 w-3.5 text-gray-400 shrink-0" />
                        <span class="text-xs text-gray-500 dark:text-gray-400">{{ t('dashboard.group_by_label') }}</span>
                      </div>
                      <div class="flex flex-wrap gap-1.5">
                        <button v-for="opt in groupByOptions" :key="opt.value" @click="selectedGroupBy = opt.value; showFilterDropdown = false"
                          :class="['px-2.5 py-1 text-xs font-medium rounded-sm transition', selectedGroupBy === opt.value ? 'bg-indigo-600 text-white' : 'bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-300 hover:bg-gray-200 dark:hover:bg-gray-600']">
                          {{ opt.label }}
                        </button>
                      </div>
                    </div>
                  </div>
                </Transition>
              </div>
            </div>
            <!-- Mehr Details expanded panel -->
            <div v-if="cars.length === 1 && selectedCar && hasCarCardDetails" class="lg:hidden">
              <div
                v-show="carCardExpanded"
                id="car-card-details-panel"
                class="mt-2 px-3 py-3 bg-white dark:bg-gray-700 border border-gray-200 dark:border-gray-600 rounded-sm"
              >
                <CarCardDetails :car="selectedCar" :wltp="wltp" :current-odometer-km="currentOdometerKm" orientation="stacked" />
              </div>
            </div>
          </div>

          <!-- Live-Ladevorgang: blendet sich automatisch ein wenn aktive Session und User AS Live -->
          <LiveChargingCard
            v-if="selectedCarId && authStore.canViewLiveCharging"
            :car-id="selectedCarId"
            :car-display-name="selectedCar ? [carDisplayName(selectedCar.brand, selectedCar.model), selectedCar.trim].filter(Boolean).join(' ') : ''"
            :license-plate="selectedCar?.licensePlate ?? null"
            :avg-consumption-kwh-per100km="stats?.avgConsumptionKwhPer100km != null ? Number(stats.avgConsumptionKwhPer100km) : null"
            class="mb-6"
          />

          <div v-if="error" class="mb-4 p-4 bg-red-50 dark:bg-red-900/30 border border-red-200 dark:border-red-700 text-red-700 dark:text-red-300 rounded-sm">{{ error }}</div>

          <!-- Empty State: No Cars -->
          <div v-if="cars.length === 0" class="min-h-[60vh] flex items-center justify-center">
            <div class="text-center max-w-md px-4">
              <TruckIcon class="h-24 w-24 mx-auto text-gray-300 mb-6" />
              <h2 class="text-2xl font-bold text-gray-800 dark:text-gray-200 mb-3">
                {{ t('dashboard.no_car_title') }}
              </h2>
              <p class="text-gray-600 dark:text-gray-400 mb-8">
                {{ t('dashboard.no_car_desc') }}
              </p>
              <button
                @click="router.push('/cars')"
                class="px-6 py-3 bg-indigo-600 text-white rounded-sm hover:bg-indigo-700 font-medium shadow-[4px_4px_0_rgba(0,0,0,0.30)] dark:shadow-[4px_4px_0_rgba(255,255,255,0.30)] hover:shadow-[5px_5px_0_rgba(0,0,0,0.35)] dark:hover:shadow-[5px_5px_0_rgba(255,255,255,0.35)] transition flex items-center gap-2 mx-auto">
                <TruckIcon class="h-5 w-5" />
                {{ t('dashboard.no_car_btn') }}
              </button>
            </div>
          </div>

          <!-- Empty State: No Logs in time range (but logs exist) -->
          <div v-else-if="stats && stats.totalCharges === 0 && hasAnyLogs" class="py-12 flex items-center justify-center">
            <div class="text-center max-w-md px-4">
              <h2 class="text-lg font-semibold text-gray-700 dark:text-gray-300 mb-2">{{ t('dashboard.no_logs_period_title') }}</h2>
              <p class="text-gray-500 dark:text-gray-400 text-sm">{{ t('dashboard.no_logs_period_desc') }}</p>
            </div>
          </div>

          <!-- Empty State: Truly no logs at all -->
          <div v-else-if="stats && stats.totalCharges === 0">
            <DashboardEmptyState v-if="selectedCar" :car="selectedCar" />
          </div>

          <div v-else-if="stats" class="space-y-0">

        <!-- Smart Insights (unter Auto-Karte, über KPI-Kacheln) -->
        <SmartInsightsCard
          :stats="stats"
          :last-month-stats="lastMonthStats"
          class="mb-5"
        />

        <!-- Key Metrics: Mobile Data Strip -->
        <div class="md:hidden mb-6">
          <div class="border border-gray-200 dark:border-gray-700 rounded-lg overflow-hidden bg-white dark:bg-gray-800">
            <!-- Gesamtenergie -->
            <div class="relative flex items-center px-4 min-h-[3.75rem] border-b border-gray-100 dark:border-gray-700"
>
              <div class="text-xs font-medium text-gray-500 dark:text-gray-400 flex-1 truncate">{{ t('dashboard.metric_total_energy') }}</div>
              <div class="flex flex-col items-end leading-tight">
                <div class="flex items-baseline gap-1">
                  <span class="text-xl font-bold text-gray-900 dark:text-gray-100">{{ stats.totalKwhCharged?.toFixed(1) ?? '–' }}</span>
                  <span class="text-[10px] text-gray-400 dark:text-gray-500 font-medium">kWh</span>
                </div>
                <div class="text-[10px] text-gray-400 dark:text-gray-500 mt-0.5">
                  {{ stats.totalCharges }} {{ t('dashboard.metric_charges') }}
                </div>
              </div>
            </div>
            <!-- Gesamtkosten -->
            <div class="relative flex items-center px-4 min-h-[3.75rem] border-b border-gray-100 dark:border-gray-700"
>
              <div class="text-xs font-medium text-gray-500 dark:text-gray-400 flex-1 truncate">{{ t('dashboard.metric_total_cost') }}</div>
              <div class="flex flex-col items-end leading-tight">
                <div class="text-xl font-bold text-gray-900 dark:text-gray-100">{{ stats.totalCostEur != null ? formatCurrency(stats.totalCostEur) : '–' }}</div>
                <div class="text-[10px] text-gray-400 dark:text-gray-500 mt-0.5">
                  Ø {{ stats.avgCostPerKwh != null ? formatCostPerKwh(stats.avgCostPerKwh) : '–' }}
                </div>
              </div>
            </div>
            <!-- Gesamtstrecke -->
            <template v-if="stats.totalDistanceKm != null">
              <button type="button"
                class="w-full relative flex items-center px-4 min-h-[3.75rem] border-b border-gray-100 dark:border-gray-700 text-left"
                @click.stop="openMetricTooltip = openMetricTooltip === 'distance' ? null : 'distance'">
                <div class="text-xs font-medium text-gray-500 dark:text-gray-400 flex-1 flex items-center gap-1.5 truncate">
                  {{ t('dashboard.metric_total_distance') }}
                  <InformationCircleIcon class="w-3 h-3 text-gray-400 dark:text-gray-500 flex-shrink-0" />
                </div>
                <div class="text-xl font-bold text-gray-900 dark:text-gray-100">{{ formatDistance(stats.totalDistanceKm) }}</div>
              </button>
              <div v-if="openMetricTooltip === 'distance'"
                class="px-4 py-3 bg-gray-50 dark:bg-gray-900/50 border-b border-gray-100 dark:border-gray-700 text-xs text-gray-700 dark:text-gray-300 leading-relaxed space-y-1.5">
                <p>{{ t('dashboard.metric_total_distance_tooltip') }}</p>
                <p class="italic text-gray-500 dark:text-gray-400">{{ t('dashboard.metric_complete_definition') }}</p>
                <router-link to="/consumption-methodology" class="inline-flex items-center gap-1 text-xs font-medium text-indigo-600 dark:text-indigo-400 underline underline-offset-2">
                  {{ t('dashboard.metric_consumption_methodology_link') }}
                  <ChevronRightIcon class="w-3 h-3" />
                </router-link>
              </div>
            </template>
            <!-- Ø Verbrauch -->
            <template v-if="stats.avgConsumptionKwhPer100km != null">
              <button type="button"
                class="w-full relative flex items-center px-4 min-h-[3.75rem] border-b border-gray-100 dark:border-gray-700 text-left"
                @click.stop="openMetricTooltip = openMetricTooltip === 'consumption' ? null : 'consumption'">
                <div class="text-xs font-medium text-gray-500 dark:text-gray-400 flex-1 flex items-center gap-1.5 truncate">
                  {{ t('dashboard.metric_avg_consumption') }}
                  <InformationCircleIcon class="w-3 h-3 text-gray-400 dark:text-gray-500 flex-shrink-0" />
                </div>
                <div class="flex items-baseline gap-1">
                  <span class="text-xl font-bold text-gray-900 dark:text-gray-100">{{ formatConsumption(stats.avgConsumptionKwhPer100km, { showUnit: false }) }}</span>
                  <span class="text-[10px] text-gray-400 dark:text-gray-500 font-medium">{{ consumptionUnitLabel() }}</span>
                </div>
              </button>
              <div v-if="openMetricTooltip === 'consumption'"
                class="px-4 py-3 bg-gray-50 dark:bg-gray-900/50 border-b border-gray-100 dark:border-gray-700 text-xs text-gray-700 dark:text-gray-300 leading-relaxed space-y-1.5">
                <p>{{ t('dashboard.metric_avg_consumption_tooltip') }}</p>
                <p class="italic text-gray-500 dark:text-gray-400">{{ t('dashboard.metric_complete_definition') }}</p>
                <router-link to="/consumption-methodology" class="inline-flex items-center gap-1 text-xs font-medium text-indigo-600 dark:text-indigo-400 underline underline-offset-2">
                  {{ t('dashboard.metric_consumption_methodology_link') }}
                  <ChevronRightIcon class="w-3 h-3" />
                </router-link>
              </div>
            </template>
            <!-- Ø Kosten -->
            <template v-if="avgCostPer100km != null">
              <button type="button"
                class="w-full relative flex items-center px-4 min-h-[3.75rem] text-left"
                @click.stop="openMetricTooltip = openMetricTooltip === 'costPer100km' ? null : 'costPer100km'">
                <div class="text-xs font-medium text-gray-500 dark:text-gray-400 flex-1 flex items-center gap-1.5 truncate">
                  {{ t('dashboard.metric_avg_cost') }}
                  <InformationCircleIcon class="w-3 h-3 text-gray-400 dark:text-gray-500 flex-shrink-0" />
                </div>
                <div class="text-xl font-bold text-gray-900 dark:text-gray-100">{{ formatCostPerDistance(avgCostPer100km) }}</div>
              </button>
              <div v-if="openMetricTooltip === 'costPer100km'"
                class="px-4 py-3 bg-gray-50 dark:bg-gray-900/50 border-t border-gray-100 dark:border-gray-700 text-xs text-gray-700 dark:text-gray-300 leading-relaxed space-y-1.5">
                <p>{{ t('dashboard.metric_avg_cost_tooltip') }}</p>
                <p class="italic text-gray-500 dark:text-gray-400">{{ t('dashboard.metric_complete_definition') }}</p>
                <router-link to="/consumption-methodology" class="inline-flex items-center gap-1 text-xs font-medium text-indigo-600 dark:text-indigo-400 underline underline-offset-2">
                  {{ t('dashboard.metric_consumption_methodology_link') }}
                  <ChevronRightIcon class="w-3 h-3" />
                </router-link>
              </div>
            </template>
          </div>
          <!-- THG Card Mobile -->
          <div v-if="showThgBanner && isGerman"
            role="link" tabindex="0"
            @click="handleThgCardClick"
            @keydown.enter="handleThgCardClick"
            @keydown.space.prevent="handleThgCardClick"
            class="relative mt-3 bg-green-50 dark:bg-green-900/20 border border-green-200 dark:border-green-700 rounded-lg overflow-hidden cursor-pointer"
            style="background: linear-gradient(90deg, rgba(34,197,94,0.12) 0%, rgba(34,197,94,0.04) 55%);">
            <div class="px-4 py-3 pr-8">
              <p class="text-xs text-gray-700 dark:text-gray-200 font-medium">THG-Prämie schon beantragt?</p>
              <p class="text-xs text-gray-500 dark:text-gray-400 leading-snug mt-1">Falls nicht, kannst du das hier tun und gleichzeitig den Betrieb der Seite unterstützen.</p>
            </div>
            <button @click.stop="dismissThgBanner"
              class="absolute top-2 right-2 h-5 w-5 rounded-sm bg-gray-200 dark:bg-gray-600 hover:bg-gray-300 dark:hover:bg-gray-500 text-gray-500 dark:text-gray-300 flex items-center justify-center"
              title="Hinweis ausblenden">
              <XMarkIcon class="h-3 w-3" />
            </button>
            <span class="absolute bottom-1 right-3 text-[10px] text-gray-300 dark:text-gray-600">Affiliate-Link</span>
          </div>
        </div>

        <!-- Key Metrics: Desktop Grid (mobile uses Data Strip below) -->
        <div :class="['hidden md:grid md:grid-cols-3 gap-4 pb-6 mb-0', showThgBanner && isGerman ? 'lg:grid-cols-6' : 'lg:grid-cols-5']">
          <div class="bg-white dark:bg-gray-700 rounded-sm border border-gray-200 dark:border-gray-700 overflow-hidden">
            <div class="p-3">
              <p class="text-xs text-gray-500 dark:text-gray-400 font-medium mb-1">{{ t('dashboard.metric_total_energy') }}</p>
              <p class="text-xl font-bold text-gray-900 dark:text-gray-100">{{ stats.totalKwhCharged?.toFixed(1) ?? '–' }} kWh</p>
              <p class="text-sm font-medium text-gray-400 dark:text-gray-500 mt-0.5">{{ stats.totalCharges }} {{ t('dashboard.metric_charges') }}</p>
            </div>
          </div>
          <div class="bg-white dark:bg-gray-700 rounded-sm border border-gray-200 dark:border-gray-700 overflow-hidden">
            <div class="p-3">
              <p class="text-xs text-gray-500 dark:text-gray-400 font-medium mb-1">{{ t('dashboard.metric_total_cost') }}</p>
              <p class="text-xl font-bold text-gray-900 dark:text-gray-100">{{ stats.totalCostEur != null ? formatCurrency(stats.totalCostEur) : '–' }}</p>
              <p class="text-sm font-medium text-gray-400 dark:text-gray-500 mt-0.5">Ø {{ stats.avgCostPerKwh != null ? formatCostPerKwh(stats.avgCostPerKwh) : '–' }}</p>
            </div>
          </div>
          <div v-if="stats.totalDistanceKm != null"
            class="bg-white dark:bg-gray-700 rounded-sm border border-gray-200 dark:border-gray-700 overflow-hidden">
            <div class="p-3">
              <div class="flex items-center gap-1 mb-1">
                <p class="text-xs text-gray-500 dark:text-gray-400 font-medium">{{ t('dashboard.metric_total_distance') }}</p>
                <button
                  type="button"
                  class="flex-shrink-0 text-gray-400 hover:text-gray-600 dark:text-gray-500 dark:hover:text-gray-300 focus:outline-none focus:ring-2 focus:ring-green-500 rounded"
                  :aria-label="t('dashboard.metric_info_aria')"
                  @click.stop="openMetricTooltip = openMetricTooltip === 'distance' ? null : 'distance'">
                  <InformationCircleIcon class="w-3.5 h-3.5" />
                </button>
              </div>
              <p class="text-xl font-bold text-gray-900 dark:text-gray-100">{{ formatDistance(stats.totalDistanceKm) }}<span class="hidden sm:inline-block font-normal text-gray-400 dark:text-gray-500 text-lg ml-1">{{ t('dashboard.metric_driven') }}</span></p>
              <div v-if="openMetricTooltip === 'distance'"
                class="mt-2 p-2.5 rounded-sm bg-gray-50 dark:bg-gray-900/50 border border-gray-200 dark:border-gray-700 text-xs text-gray-700 dark:text-gray-300 leading-relaxed space-y-1.5">
                <p>{{ t('dashboard.metric_total_distance_tooltip') }}</p>
                <p class="italic text-gray-500 dark:text-gray-400">{{ t('dashboard.metric_complete_definition') }}</p>
                <router-link to="/consumption-methodology" class="inline-flex items-center gap-1 text-xs font-medium text-indigo-600 dark:text-indigo-400 underline underline-offset-2 hover:text-indigo-800 dark:hover:text-indigo-300 transition-colors">
                  {{ t('dashboard.metric_consumption_methodology_link') }}
                  <ChevronRightIcon class="w-3 h-3 flex-shrink-0" />
                </router-link>
              </div>
            </div>
          </div>
          <div v-if="stats.avgConsumptionKwhPer100km != null"
            class="bg-white dark:bg-gray-700 rounded-sm border border-gray-200 dark:border-gray-700 overflow-hidden">
            <div class="p-3">
              <div class="flex items-center gap-1 mb-1">
                <p class="text-xs text-gray-500 dark:text-gray-400 font-medium">{{ t('dashboard.metric_avg_consumption') }}</p>
                <button
                  type="button"
                  class="flex-shrink-0 text-gray-400 hover:text-gray-600 dark:text-gray-500 dark:hover:text-gray-300 focus:outline-none focus:ring-2 focus:ring-red-500 rounded"
                  :aria-label="t('dashboard.metric_info_aria')"
                  @click.stop="openMetricTooltip = openMetricTooltip === 'consumption' ? null : 'consumption'">
                  <InformationCircleIcon class="w-3.5 h-3.5" />
                </button>
              </div>
              <p class="text-xl font-bold text-gray-900 dark:text-gray-100">{{ formatConsumption(stats.avgConsumptionKwhPer100km, { showUnit: false }) }}</p>
              <p class="text-sm font-medium text-gray-400 dark:text-gray-500 mt-0.5">{{ consumptionUnitLabel() }}</p>
              <div v-if="openMetricTooltip === 'consumption'"
                class="mt-2 p-2.5 rounded-sm bg-gray-50 dark:bg-gray-900/50 border border-gray-200 dark:border-gray-700 text-xs text-gray-700 dark:text-gray-300 leading-relaxed space-y-1.5">
                <p>{{ t('dashboard.metric_avg_consumption_tooltip') }}</p>
                <p class="italic text-gray-500 dark:text-gray-400">{{ t('dashboard.metric_complete_definition') }}</p>
                <router-link to="/consumption-methodology" class="inline-flex items-center gap-1 text-xs font-medium text-indigo-600 dark:text-indigo-400 underline underline-offset-2 hover:text-indigo-800 dark:hover:text-indigo-300 transition-colors">
                  {{ t('dashboard.metric_consumption_methodology_link') }}
                  <ChevronRightIcon class="w-3 h-3 flex-shrink-0" />
                </router-link>
              </div>
            </div>
          </div>
          <div v-if="avgCostPer100km != null"
            class="bg-white dark:bg-gray-700 rounded-sm border border-gray-200 dark:border-gray-700 overflow-hidden">
            <div class="p-3">
              <div class="flex items-center gap-1 mb-1">
                <p class="text-xs text-gray-500 dark:text-gray-400 font-medium">{{ t('dashboard.metric_avg_cost') }}</p>
                <button
                  type="button"
                  class="flex-shrink-0 text-gray-400 hover:text-gray-600 dark:text-gray-500 dark:hover:text-gray-300 focus:outline-none focus:ring-2 focus:ring-pink-500 rounded"
                  :aria-label="t('dashboard.metric_info_aria')"
                  @click.stop="openMetricTooltip = openMetricTooltip === 'costPer100km' ? null : 'costPer100km'">
                  <InformationCircleIcon class="w-3.5 h-3.5" />
                </button>
              </div>
              <p class="text-xl font-bold text-gray-900 dark:text-gray-100">{{ formatCostPerDistance(avgCostPer100km) }}</p>
              <div v-if="openMetricTooltip === 'costPer100km'"
                class="mt-2 p-2.5 rounded-sm bg-gray-50 dark:bg-gray-900/50 border border-gray-200 dark:border-gray-700 text-xs text-gray-700 dark:text-gray-300 leading-relaxed space-y-1.5">
                <p>{{ t('dashboard.metric_avg_cost_tooltip') }}</p>
                <p class="italic text-gray-500 dark:text-gray-400">{{ t('dashboard.metric_complete_definition') }}</p>
                <router-link to="/consumption-methodology" class="inline-flex items-center gap-1 text-xs font-medium text-indigo-600 dark:text-indigo-400 underline underline-offset-2 hover:text-indigo-800 dark:hover:text-indigo-300 transition-colors">
                  {{ t('dashboard.metric_consumption_methodology_link') }}
                  <ChevronRightIcon class="w-3 h-3 flex-shrink-0" />
                </router-link>
              </div>
            </div>
          </div>

          <!-- THG Card (alle Viewports, füllt leeren Grid-Slot) -->
          <div
            v-if="showThgBanner && isGerman"
            role="link"
            tabindex="0"
            @click="handleThgCardClick"
            @keydown.enter="handleThgCardClick"
            @keydown.space.prevent="handleThgCardClick"
            class="relative bg-green-50 dark:bg-green-900/20 rounded-sm border border-green-200 dark:border-green-700 overflow-hidden shadow-[0_4px_0_0_#bbf7d0] dark:shadow-[0_4px_0_0_#14532d] hover:shadow-[0_2px_0_0_#bbf7d0] dark:hover:shadow-[0_2px_0_0_#14532d] hover:translate-y-0.5 active:shadow-none active:translate-y-1 transition-all duration-75 group cursor-pointer"
          >
            <div class="h-1 bg-green-500"></div>
            <div class="p-4 pr-8">
              <p class="text-xs text-gray-700 dark:text-gray-200 font-medium mb-1">THG-Prämie schon beantragt?</p>
              <p class="text-xs text-gray-500 dark:text-gray-400 leading-snug mt-1">Falls nicht, kannst du das hier tun und gleichzeitig den Betrieb der Seite unterstützen.</p>
            </div>
            <button
              @click.stop="dismissThgBanner"
              class="absolute top-4 right-2 h-5 w-5 rounded-sm bg-gray-200 dark:bg-gray-600 hover:bg-gray-300 dark:hover:bg-gray-500 text-gray-500 dark:text-gray-300 flex items-center justify-center transition"
              title="Hinweis ausblenden"
            >
              <XMarkIcon class="h-3 w-3" />
            </button>
            <span class="absolute bottom-1 right-3 text-[10px] text-gray-300 dark:text-gray-600">Affiliate-Link</span>
          </div>
        </div>

        <!-- Insights: Energie-Split · Standverluste · Fahrten-Kalender (AutoSync Live only) -->
        <DashboardInsights
          v-if="authStore.canViewLiveTrips && mergedLogFeed.length > 0"
          :entries="mergedLogFeed"
          :selected-car="cars.find((c: any) => c.id === selectedCarId)"
          :selected-time-range="selectedTimeRange"
          :custom-start-date="customStartDate"
          :custom-end-date="customEndDate"
          class="mb-3"
        />

        <!-- Echte Reichweite + Peer Benchmark: mobile gestackt, desktop nebeneinander -->
        <div class="mb-0 grid grid-cols-1 md:grid-cols-3 gap-4">

        <!-- Echte Reichweite -->
        <RangeCard
          v-if="selectedCar?.effectiveBatteryCapacityKwh && stats?.avgConsumptionKwhPer100km"
          :battery-capacity-kwh="selectedCar.effectiveBatteryCapacityKwh"
          :summer-consumption="stats.summerConsumptionKwhPer100km ?? null"
          :winter-consumption="stats.winterConsumptionKwhPer100km ?? null"
          :avg-consumption="stats.avgConsumptionKwhPer100km"
        />

        <!-- Ladeverteilung: immer links vom Community-Vergleich -->
        <ChargingTypeSplitCard
          v-if="stats.chargingTypeSplit && stats.locationSplit"
          :charging-type-split="stats.chargingTypeSplit"
          :location-split="stats.locationSplit"
        />

        <!-- Peer Benchmark -->
        <PeerBenchmarkCard
          v-if="stats?.peerBenchmark && stats.peerBenchmark.peerAvgConsumptionKwhPer100km != null"
          :benchmark="stats.peerBenchmark"
          :effective-battery-kwh="selectedCar?.effectiveBatteryCapacityKwh ?? null"
          :car-display-name="selectedCar ? [carDisplayName(selectedCar.brand, selectedCar.model), selectedCar.trim].filter(Boolean).join(' ') : ''"
        />

        <!-- WLTP-Vergleich (Fallback wenn keine Peer-Daten aber WLTP vorhanden) -->
        <WltpComparisonCard
          v-else-if="wltp && stats?.avgConsumptionKwhPer100km && selectedCar?.effectiveBatteryCapacityKwh"
          :official-range-km="wltp.officialRangeKm"
          :official-consumption-kwh-per100km="wltp.officialConsumptionKwhPer100km"
          :user-avg-consumption-kwh-per100km="stats.avgConsumptionKwhPer100km"
          :effective-battery-kwh="selectedCar.effectiveBatteryCapacityKwh"
          :car-display-name="selectedCar ? [carDisplayName(selectedCar.brand, selectedCar.model), selectedCar.trim].filter(Boolean).join(' ') : ''"
          :rating-source="wltp.ratingSource"
        />

        <!-- Peer Benchmark Placeholder (nur wenn auch keine WLTP-Daten) -->
        <div
          v-else-if="stats && carInfo?.customNetCapacityKwh && stats?.avgConsumptionKwhPer100km"
          class="flex-1 min-w-0 bg-white dark:bg-gray-800 rounded-sm border-2 border-gray-300 dark:border-gray-700 overflow-hidden shadow-[2px_2px_0_0_#d1d5db] dark:shadow-[2px_2px_0_0_#374151]"
        >
          <button
            type="button"
            @click="togglePeerPlaceholder"
            class="w-full px-4 py-2.5 flex items-center gap-2 text-left"
            :aria-expanded="!peerPlaceholderCollapsed">
            <UsersIcon class="w-4 h-4 text-gray-400 dark:text-gray-500 flex-shrink-0" />
            <span class="flex-1 text-sm font-medium text-gray-500 dark:text-gray-400 truncate">
              {{ t('dashboard.peer_no_data_title') }}
            </span>
            <ChevronDownIcon
              class="w-4 h-4 text-gray-400 flex-shrink-0 transition-transform duration-200"
              :class="{ 'rotate-180': !peerPlaceholderCollapsed }" />
          </button>
          <Transition name="slide-down">
            <div v-if="!peerPlaceholderCollapsed" class="px-4 pb-4 -mt-1 text-center">
              <p class="text-xs text-gray-400 dark:text-gray-500 max-w-xs mx-auto">{{ t('dashboard.peer_no_data_body') }}</p>
            </div>
          </Transition>
        </div>

        </div><!-- Ende Reichweite + Peer Wrapper -->

        <!-- Ladeeffizienz + CO2-Bilanz -->
        <div
          v-if="stats && ((stats.chargingEfficiencySplit?.coveredLogCount ?? 0) > 0 || (stats.avgConsumptionKwhPer100km && stats.totalDistanceKm))"
          class="mb-6 mt-4 grid grid-cols-1 md:grid-cols-2 gap-4"
        >
          <ChargingEfficiencyCard
            v-if="stats?.chargingEfficiencySplit && stats.chargingEfficiencySplit.coveredLogCount > 0"
            :efficiency-split="stats.chargingEfficiencySplit"
          />
          <CO2Card
            v-if="stats?.avgConsumptionKwhPer100km && stats?.totalDistanceKm"
            :avg-consumption-kwh-per100km="stats.avgConsumptionKwhPer100km"
            :total-distance-km="stats.totalDistanceKm"
          />
        </div>


        <!-- Chart 1: Charging & Costs -->
        <div class="border-t border-gray-100 dark:border-gray-700 pt-6">
          <div class="md:bg-gray-50 md:dark:bg-gray-700 py-4 md:p-6 -mx-4 md:mx-0 md:rounded-sm md:border-2 md:border-gray-300 md:dark:border-gray-600 md:shadow-[2px_2px_0_0_#d1d5db] dark:md:shadow-[2px_2px_0_0_#374151]">
            <div v-if="!chartsReady && isInitialLoad" class="h-64 sm:h-72 bg-gray-100 dark:bg-gray-700 animate-pulse rounded mx-4 md:mx-0"></div>
            <template v-else>
              <div class="flex flex-col sm:flex-row sm:items-center justify-center gap-4 sm:gap-6 mb-4 px-4 md:px-0">
                <h2 class="text-xl font-semibold text-gray-800 dark:text-gray-200 text-center">{{ t('dashboard.chart_charging_costs') }}</h2>
                <div class="flex flex-wrap gap-2 sm:gap-4 text-xs sm:text-sm justify-center">
                  <label class="flex items-center gap-1 sm:gap-2 cursor-pointer">
                    <input type="checkbox" v-model="showCostPerKwh"
                      class="w-3 h-3 sm:w-4 sm:h-4 rounded accent-indigo-600 cursor-pointer" />
                    <span class="font-medium text-gray-700 dark:text-gray-300">
                      <span class="inline-block w-2 sm:w-3 h-0.5 bg-indigo-600 mr-1 align-middle"></span>
                      {{ currencySymbol }}/kWh
                    </span>
                  </label>
                  <label class="flex items-center gap-1 sm:gap-2 cursor-pointer">
                    <input type="checkbox" v-model="showKwh"
                      class="w-3 h-3 sm:w-4 sm:h-4 rounded accent-amber-500 cursor-pointer" />
                    <span class="font-medium text-gray-700 dark:text-gray-300">
                      <span class="inline-block w-2 sm:w-3 h-0.5 bg-amber-500 mr-1 align-middle"></span>
                      kWh
                    </span>
                  </label>
                </div>
              </div>
              <div v-if="chargingChartData && chargingChartData.datasets.length > 0" class="h-64 sm:h-72">
                <Line :data="chargingChartData" :options="chargingChartOptions" />
              </div>
              <div v-else class="text-center py-10 text-gray-400 text-sm px-4 md:px-0">
                {{ t('dashboard.chart_no_data') }}
              </div>
              <div class="hidden md:flex flex-wrap gap-x-6 gap-y-1 mt-3 text-xs text-gray-400 px-4 md:px-0">
                <span>{{ t('dashboard.chart_left_axis') }}: {{ currencySymbol }}/kWh</span>
                <span>{{ t('dashboard.chart_right_axis') }}: kWh</span>
              </div>
            </template>
          </div>
        </div>

        <!-- Chart 2: Range & Efficiency (only if distance data exists) -->
        <div v-if="hasDistanceData" class="border-t border-gray-100 dark:border-gray-700 pt-6">
          <div class="md:bg-gray-50 md:dark:bg-gray-700 py-4 md:p-6 -mx-4 md:mx-0 md:rounded-sm md:border-2 md:border-gray-300 md:dark:border-gray-600 md:shadow-[2px_2px_0_0_#d1d5db] dark:md:shadow-[2px_2px_0_0_#374151]">
            <div v-if="!chartsReady && isInitialLoad" class="h-64 sm:h-72 bg-gray-100 dark:bg-gray-700 animate-pulse rounded mx-4 md:mx-0"></div>
            <template v-else>
              <div class="flex flex-col sm:flex-row sm:items-center justify-center gap-4 sm:gap-6 mb-4 px-4 md:px-0">
                <h2 class="text-xl font-semibold text-gray-800 dark:text-gray-200 text-center">{{ t('dashboard.chart_range_efficiency') }}</h2>
                <div class="flex flex-wrap gap-2 sm:gap-4 text-xs sm:text-sm justify-center">
                  <label class="flex items-center gap-1 sm:gap-2 cursor-pointer">
                    <input type="checkbox" v-model="showConsumption"
                      class="w-3 h-3 sm:w-4 sm:h-4 rounded accent-red-500 cursor-pointer" />
                    <span class="font-medium text-gray-700 dark:text-gray-300">
                      <span class="inline-block w-2 sm:w-3 h-0.5 bg-red-500 mr-1 align-middle"></span>
                      {{ consumptionUnitLabel() }}
                    </span>
                  </label>
                  <label class="flex items-center gap-1 sm:gap-2 cursor-pointer">
                    <input type="checkbox" v-model="showDistance"
                      class="w-3 h-3 sm:w-4 sm:h-4 rounded accent-emerald-500 cursor-pointer" />
                    <span class="font-medium text-gray-700 dark:text-gray-300">
                      <span class="inline-block w-2 sm:w-3 h-0.5 bg-emerald-500 mr-1 align-middle"></span>
                      {{ distanceUnitLabel() }}
                    </span>
                  </label>
                </div>
              </div>
              <div v-if="efficiencyChartData && efficiencyChartData.datasets.length > 0" class="h-64 sm:h-72">
                <Line :data="efficiencyChartData" :options="efficiencyChartOptions" />
              </div>
              <div v-else class="text-center py-10 text-gray-400 text-sm px-4 md:px-0">
                {{ t('dashboard.chart_no_data') }}
              </div>
              <div class="hidden md:flex flex-wrap gap-x-6 gap-y-1 mt-3 text-xs text-gray-400 px-4 md:px-0">
                <span>{{ t('dashboard.chart_left_axis') }}: {{ consumptionUnitLabel() }}</span>
                <span>{{ t('dashboard.chart_right_axis') }}: {{ distanceUnitLabel() }}</span>
              </div>
            </template>
          </div>
        </div>

        <!-- WLTP Delta Bar Chart -->
        <div v-if="wltp && hasDistanceData && wltpChartData" class="border-t border-gray-100 dark:border-gray-700 pt-6">
          <div class="md:bg-gray-50 md:dark:bg-gray-700 py-4 md:p-6 -mx-4 md:mx-0 md:rounded-sm md:border-2 md:border-gray-300 md:dark:border-gray-600 md:shadow-[2px_2px_0_0_#d1d5db] dark:md:shadow-[2px_2px_0_0_#374151]">
          <div v-if="!chartsReady && isInitialLoad" :style="{ height: wltpChartHeight }" class="bg-gray-100 dark:bg-gray-700 animate-pulse rounded mx-4 md:mx-0"></div>
          <template v-else>
          <div class="mb-4 text-center px-4 md:px-0">
            <h2 class="text-xl font-semibold text-gray-800 dark:text-gray-200">
              <template v-if="isCustomCompare">
                {{ t('dashboard.chart_consumption_vs_custom_prefix') }} <strong>{{ customCompareValue != null ? formatConsumption(customCompareValue) : '–' }}</strong>
              </template>
              <template v-else>{{ t('dashboard.chart_consumption_vs_wltp') }}</template>
            </h2>
            <p class="text-xs sm:text-sm text-gray-500 dark:text-gray-400 mt-1">
              WLTP: <strong>{{ wltp.officialConsumptionKwhPer100km != null ? formatConsumption(wltp.officialConsumptionKwhPer100km) : '–' }}</strong>
              ({{ wltp.officialRangeKm != null ? formatDistance(wltp.officialRangeKm) : '–' }}, {{ wltp.wltpType }})
              <span class="hidden sm:inline">
                · <span class="text-emerald-600 font-medium">{{ t('dashboard.chart_green_better') }}</span>
                · <span class="text-red-600 font-medium">{{ t('dashboard.chart_red_worse') }}</span>
              </span>
            </p>
            <!-- Custom compare controls -->
            <div class="mt-2 flex items-center justify-center gap-3 flex-wrap">
              <button
                @click="showCompareInput = !showCompareInput"
                class="text-xs text-blue-600 dark:text-blue-400 underline underline-offset-2 hover:text-blue-700 dark:hover:text-blue-300"
              >
                {{ isCustomCompare ? t('dashboard.chart_compare_edit') : t('dashboard.chart_compare_customize') }}
              </button>
              <button
                v-if="isCustomCompare"
                @click="resetToWltp"
                class="text-xs text-gray-400 dark:text-gray-500 underline underline-offset-2 hover:text-gray-600 dark:hover:text-gray-300"
              >
                {{ t('dashboard.chart_compare_reset') }}
              </button>
            </div>
            <!-- Inline input form -->
            <div v-if="showCompareInput" class="mt-2 flex items-center justify-center gap-2 flex-wrap">
              <input
                v-model="customCompareInput"
                type="number"
                step="0.1"
                min="5"
                max="99"
                @keyup.enter="saveCustomCompare"
                :placeholder="t('dashboard.chart_compare_placeholder')"
                class="w-24 px-2 py-1 text-sm border border-gray-300 dark:border-gray-600 rounded bg-white dark:bg-gray-800 text-gray-800 dark:text-gray-200 focus:outline-none focus:ring-1 focus:ring-blue-500"
              />
              <span class="text-sm text-gray-500 dark:text-gray-400">{{ consumptionUnitLabel() }}</span>
              <button
                @click="saveCustomCompare"
                class="px-3 py-1 text-xs font-medium bg-emerald-600 text-white rounded hover:bg-emerald-700 active:bg-emerald-800"
              >
                {{ t('dashboard.chart_compare_save') }}
              </button>
            </div>
            <p class="text-xs text-gray-400 dark:text-gray-500 mt-1">{{ t('model.wltp_measurement_note') }}</p>
          </div>
            <div :class="wltpChartScrollable ? 'overflow-y-auto' : ''" :style="{ height: wltpChartHeight }">
              <Bar :data="wltpChartData" :options="wltpChartOptions" />
            </div>
          </template>
          </div>
        </div>

        <!-- WLTP missing hint -->
        <div v-else-if="!wltp && hasDistanceData" class="border-t border-gray-100 pt-6">
          <div class="bg-amber-50 dark:bg-amber-900/30 border border-amber-200 dark:border-amber-700 md:rounded-sm p-3 md:p-4 text-sm text-amber-700 dark:text-amber-300">
            {{ t('dashboard.wltp_missing') }}
            <router-link to="/cars" class="font-semibold underline">{{ t('dashboard.wltp_missing_link') }}</router-link>
            {{ t('dashboard.wltp_missing_suffix') }}
          </div>
        </div>

        <!-- Charging Heat Map -->
        <div class="border-t border-gray-100 dark:border-gray-700 pt-6">
          <div class="md:bg-gray-50 md:dark:bg-gray-700 py-4 md:p-6 -mx-4 md:mx-0 md:rounded-sm md:border-2 md:border-gray-300 md:dark:border-gray-600 md:shadow-[2px_2px_0_0_#d1d5db] dark:md:shadow-[2px_2px_0_0_#374151] mb-4 md:mb-0">
            <div v-if="!chartsReady && isInitialLoad" class="h-96 bg-gray-100 dark:bg-gray-700 animate-pulse rounded mx-4 md:mx-0"></div>
            <template v-else>
              <div class="mb-4 px-4 md:px-0">
                <h2 class="text-xl font-semibold text-gray-800 dark:text-gray-200">{{ t('dashboard.map_title') }}</h2>
                <p class="text-sm text-gray-500 dark:text-gray-400 mt-1">
                  {{ t('dashboard.map_subtitle') }}
                </p>
              </div>
              <ChargingHeatMap :car-id="selectedCarId" :time-range="selectedTimeRange" />
            </template>
          </div>
        </div>

        </div>
      </div>
      </div>
    </Transition>
  </div>


  <ImplausibleLogsModal
    :car-id="selectedCarId"
    :open="showImplausibleModal"
    @close="() => { showImplausibleModal = false; if (implausibleModalDirty) { fetchStatistics(); implausibleModalDirty = false } }"
    @updated="() => { fetchImplausibleCount(); implausibleModalDirty = true }"
  />

</div>
</template>

<style scoped>
.fade-enter-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from {
  opacity: 0;
}

.fade-enter-to {
  opacity: 1;
}

.slide-down-enter-active,
.slide-down-leave-active {
  transition: max-height 0.25s ease, opacity 0.2s ease;
  overflow: hidden;
  max-height: 600px;
}

.slide-down-enter-from,
.slide-down-leave-to {
  max-height: 0;
  opacity: 0;
}

.dropdown-enter-active,
.dropdown-leave-active {
  transition: opacity 0.15s ease, transform 0.15s ease;
}
.dropdown-enter-from,
.dropdown-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

/* Hide horizontal scrollbar on the car selector strip (visible peek + touch
   scroll communicate scrollability; the native bar just clutters the chip). */
.car-scroll-hide { scrollbar-width: none; }
.car-scroll-hide::-webkit-scrollbar { display: none; }

/* Sticky car-header compact mode (mobile only). Mirrors LogsView rules. */
@media (max-width: 767px) {
  .car-header-compact .compact-hide { display: none !important; }
  .car-header-compact .compact-nowrap {
    flex-wrap: nowrap !important;
    overflow: hidden;
    padding-top: 0 !important;
    padding-bottom: 0 !important;
  }
  .car-header-compact .compact-nowrap > * { flex-shrink: 0; }
  .car-header-compact .compact-shrink-thumb {
    height: 1.75rem !important;
    width: auto !important;
    aspect-ratio: 4 / 3;
    align-self: center !important;
  }
  .car-header-compact .compact-shrink-pad {
    padding-top: 0.125rem !important;
    padding-bottom: 0.125rem !important;
  }
}
</style>
