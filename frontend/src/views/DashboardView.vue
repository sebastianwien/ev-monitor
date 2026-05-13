<script setup lang="ts">
import { ref, computed, onMounted, watch, defineAsyncComponent } from 'vue'
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
import RangeCard from '../components/dashboard/RangeCard.vue'
import DashboardEmptyState from '../components/dashboard/DashboardEmptyState.vue'
import DashboardInsights from '../components/dashboard/DashboardInsights.vue'
import { useLocaleFormat } from '../composables/useLocaleFormat'
import { useDashboardStats } from '../composables/useDashboardStats'
import { useDashboardCharts } from '../composables/useDashboardCharts'
import { useLogList } from '../composables/useLogList'
import { useStickyCarHeader } from '../composables/useStickyCarHeader'
import { useBulkBarOffset } from '../composables/useBulkBarOffset'
import { useWallboxStore } from '../stores/wallbox'
import { carDisplayName } from '../utils/enumLabel'
import { isVwGroupBrand } from '../api/vwGroupService'

ChartJS.register(CategoryScale, LinearScale, BarElement, PointElement, LineElement, Title, Tooltip, Legend, Filler, ChartDataLabels)

const { t } = useI18n()
const router = useRouter()
const { formatConsumption, consumptionUnitLabel, formatDistance, distanceUnitLabel, formatCurrency, formatCostPerKwh, formatCostPerDistance, currencySymbol } = useLocaleFormat()

// -- Dashboard Stats --
const {
  selectedCarId, stats, carInfo, wltp, loading, chartsReady, isInitialLoad, error,
  cars, carImageUrls, selectedTimeRange, selectedGroupBy, customStartDate, customEndDate,
  importBannerDismissed, teslaStatus, smartcarStatus, vwGroupStatus, implausibleCount, hasDistanceData, avgCostPer100km,
  timeRangeOptions, groupByOptions, dismissImportBanner, fetchImplausibleCount,
  fetchCarAndWltp, fetchStatistics, initCars,
} = useDashboardStats()

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
  hasAnyLogs, mergedLogFeed, fetchLogs,
} = useLogList(selectedCarId, cars, logsSection)


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

const anyVehicleCharging = computed(() => cars.value.some(car => isVehicleCharging(car)))

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

// -- Mobile sticky filter bar: lift FAB so they don't collide --
const filterBar = ref<HTMLElement | null>(null)
const filterBarVisible = computed(() => !!selectedCarId.value && hasAnyLogs.value)
useBulkBarOffset(filterBar, filterBarVisible)

onMounted(() => initCars())

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
        <div class="bg-white dark:bg-gray-800 md:rounded-xl md:shadow-lg p-4 md:p-6"
          :style="{ paddingBottom: `calc(var(--bulk-bar-offset, 0px) + 1.5rem)` }">
          <div class="flex flex-wrap items-center gap-3 mb-6">
            <div class="shrink-0 rounded-sm border-2 border-gray-900 dark:border-white bg-indigo-600 p-2 w-10 h-10 flex items-center justify-center">
              <ChartBarIcon class="h-5 w-5 text-white" />
            </div>
            <h1 class="text-2xl md:text-3xl font-bold tracking-tight text-gray-900 dark:text-gray-100">Dashboard</h1>
            <div class="w-full flex items-center gap-2 sm:w-auto sm:ml-auto">
              <router-link
                to="/cars"
                class="inline-flex items-center justify-center gap-1.5 bg-white dark:bg-gray-800 hover:bg-gray-50 dark:hover:bg-gray-700 text-gray-700 dark:text-gray-300 font-bold uppercase tracking-wider text-[11px] px-4 py-2.5 rounded-sm border-2 border-gray-300 dark:border-gray-700 shadow-[3px_3px_0_0_#9ca3af] dark:shadow-[3px_3px_0_0_#4b5563] active:translate-x-[3px] active:translate-y-[3px] active:shadow-none transition-[transform,box-shadow] duration-75">
                <TruckIcon class="w-4 h-4" />
                {{ t('dashboard.vehicles_btn') }}
              </router-link>
              <router-link v-if="stats && stats.totalCharges > 0"
                to="/logs"
                class="inline-flex items-center justify-center gap-1.5 bg-indigo-600 hover:bg-indigo-500 text-white font-bold uppercase tracking-wider text-[11px] px-4 py-2.5 rounded-sm border-2 border-indigo-600 shadow-[3px_3px_0_0_#030712] dark:shadow-[3px_3px_0_0_#ffffff] active:translate-x-[3px] active:translate-y-[3px] active:shadow-none transition-[transform,box-shadow] duration-75">
                <ListBulletIcon class="w-4 h-4" />
                <span class="min-[381px]:hidden">{{ t('dashboard.logs_title_short') }}</span>
                <span class="hidden min-[381px]:inline">{{ t('dashboard.logs_btn') }}</span>
                <ChevronRightIcon class="w-3.5 h-3.5 opacity-75" />
              </router-link>
            </div>
          </div>

          <!-- Import Hint Banner -->
          <div v-if="!importBannerDismissed" class="relative mb-6">
            <router-link
              to="/imports"
              class="flex items-center gap-3 border-l-2 border-green-500 bg-green-50 dark:bg-green-950/30 px-4 py-3 rounded-r-sm hover:bg-green-100 dark:hover:bg-green-950/50 transition group"
            >
              <ArrowDownTrayIcon class="h-5 w-5 text-green-700 dark:text-green-400 shrink-0" />
              <div class="flex-1 min-w-0">
                <span class="text-sm font-bold text-green-800 dark:text-green-200">{{ t('dashboard.import_banner') }}</span>
                <span class="text-sm font-medium text-green-700 dark:text-green-300 ml-1">{{ t('dashboard.import_banner_sources') }}</span>
              </div>
              <span class="text-green-700 dark:text-green-400 text-sm font-bold group-hover:translate-x-0.5 transition-transform">→</span>
            </router-link>
            <button
              @click="dismissImportBanner"
              class="absolute -top-2 -right-2 h-5 w-5 rounded-sm border-2 border-gray-900 dark:border-white bg-white dark:bg-gray-800 hover:bg-gray-100 dark:hover:bg-gray-700 text-gray-700 dark:text-gray-300 flex items-center justify-center transition"
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
                : 'sticky top-16 z-10 bg-white dark:bg-gray-800 -mx-4 px-4 py-1.5 mb-3 border-b border-gray-100 dark:border-gray-700 shadow-sm md:static md:bg-transparent md:p-0 md:mb-6 md:rounded-xl md:w-fit md:border-0 md:shadow-none',
              cars.length === 1 && anyVehicleCharging ? 'vehicle-charging-glow' : '',
              isCarHeaderSticky ? 'car-header-compact' : ''
            ]"
          >
            <div class="flex gap-3 overflow-x-auto car-scroll-hide lg:flex-wrap lg:overflow-x-visible">
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
                      ? 'border-emerald-500 bg-emerald-50 dark:bg-emerald-950/30 shadow-[4px_4px_0_0_#16a34a] translate-x-[2px] translate-y-[2px]'
                      : 'border-indigo-600 bg-indigo-50 dark:bg-indigo-950/30 shadow-[4px_4px_0_0_#4338ca] translate-x-[2px] translate-y-[2px]'
                    : isVehicleCharging(car)
                      ? 'border-emerald-500 bg-white dark:bg-gray-800 shadow-[4px_4px_0_0_#16a34a] active:translate-x-[2px] active:translate-y-[2px] active:shadow-none'
                      : 'border-gray-900 dark:border-white bg-white dark:bg-gray-800 shadow-[4px_4px_0_0_#030712] dark:shadow-[4px_4px_0_0_#ffffff] active:translate-x-[2px] active:translate-y-[2px] active:shadow-none',
                  cars.length > 1 && isVehicleCharging(car) ? 'ring-2 ring-emerald-400 dark:ring-emerald-500' : '',
                ]" style="transition: transform 0.075s ease, box-shadow 0.075s ease;">
                <div class="flex-shrink-0 h-12 aspect-[4/3] md:w-24 md:h-auto md:aspect-auto md:self-stretch bg-gray-100 dark:bg-gray-600 flex items-center justify-center overflow-hidden compact-shrink-thumb">
                  <img
                    v-if="carImageUrls[car.id]"
                    :src="carImageUrls[car.id]"
                    :alt="car.model"
                    class="w-full h-full object-cover" />
                  <TruckIcon v-else class="w-6 h-6 md:w-8 md:h-8 text-gray-400" />
                </div>
                <div class="min-w-0 flex-1 px-3 py-1.5 md:px-4 md:py-3 compact-shrink-pad">
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
              </button>
            </div>
          </div>

          <!-- Filters (desktop only - mobile uses sticky bottom bar below) -->
          <div v-if="selectedCarId && hasAnyLogs" class="hidden md:block mb-6 overflow-hidden rounded-sm border-2 border-gray-900 dark:border-white shadow-[4px_4px_0_0_#030712] dark:shadow-[4px_4px_0_0_#ffffff]">
            <!-- Tab strip -->
            <div class="flex overflow-x-auto scrollbar-hide bg-white dark:bg-gray-900 border-b-2 border-gray-900 dark:border-white">
              <button
                v-for="option in timeRangeOptions"
                :key="option.value"
                @click="selectedTimeRange = option.value"
                :class="[
                  'flex items-center gap-1.5 px-4 h-11 text-[11px] font-bold uppercase tracking-wider whitespace-nowrap border-r-2 border-gray-300 dark:border-gray-700 border-l-[4px] flex-shrink-0 transition-colors duration-100 cursor-pointer',
                  selectedTimeRange === option.value
                    ? option.value === 'CUSTOM'
                      ? 'border-l-sky-500 text-sky-700 dark:text-sky-300 bg-sky-50 dark:bg-sky-950/30'
                      : 'border-l-indigo-600 text-indigo-700 dark:text-indigo-300 bg-indigo-50 dark:bg-indigo-950/30'
                    : 'border-l-transparent text-gray-600 dark:text-gray-400 hover:text-gray-900 dark:hover:text-gray-100 hover:bg-gray-50 dark:hover:bg-gray-800'
                ]">
                <CalendarIcon v-if="option.value === 'CUSTOM'" class="h-3.5 w-3.5 flex-shrink-0" />
                {{ option.label }}
              </button>
              <!-- Gruppierung at the right end -->
              <div class="ml-auto flex items-center gap-2 px-4 border-l-2 border-gray-300 dark:border-gray-700 flex-shrink-0">
                <ListBulletIcon class="h-3.5 w-3.5 text-gray-500 dark:text-gray-400 flex-shrink-0" />
                <div class="relative">
                  <select v-model="selectedGroupBy"
                    class="appearance-none bg-transparent text-[11px] font-bold uppercase tracking-wider text-gray-600 dark:text-gray-400 pr-5 cursor-pointer focus:outline-none focus:text-gray-900 dark:focus:text-gray-100">
                    <option v-for="opt in groupByOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
                  </select>
                  <ChevronDownIcon class="pointer-events-none absolute right-0 top-1/2 -translate-y-1/2 h-3 w-3 text-gray-500 dark:text-gray-400" />
                </div>
              </div>
            </div>
            <!-- Custom date picker (only visible when CUSTOM is selected) -->
            <Transition
              enter-active-class="transition duration-150 ease-out"
              enter-from-class="opacity-0 -translate-y-1"
              enter-to-class="opacity-100 translate-y-0"
              leave-active-class="transition duration-100 ease-in"
              leave-from-class="opacity-100 translate-y-0"
              leave-to-class="opacity-0 -translate-y-1">
              <div v-if="selectedTimeRange === 'CUSTOM'"
                class="flex items-center gap-3 px-4 py-2.5 bg-gray-50 dark:bg-gray-900 border-t-2 border-gray-300 dark:border-gray-700">
                <span class="text-[10px] font-bold uppercase tracking-wider text-gray-500 dark:text-gray-400">{{ t('dashboard.time_custom_from') }}</span>
                <div class="relative">
                  <input type="date" v-model="customStartDate" :max="customEndDate || undefined"
                    class="px-3 pr-9 py-1.5 text-xs font-medium border-2 border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 text-gray-700 dark:text-gray-100 rounded-sm focus:outline-none focus:border-indigo-500 [&::-webkit-calendar-picker-indicator]:opacity-0 [&::-webkit-calendar-picker-indicator]:absolute [&::-webkit-calendar-picker-indicator]:right-0 [&::-webkit-calendar-picker-indicator]:w-9 [&::-webkit-calendar-picker-indicator]:h-full [&::-webkit-calendar-picker-indicator]:cursor-pointer" />
                  <CalendarIcon class="pointer-events-none absolute right-2.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-gray-500 dark:text-gray-400" />
                </div>
                <span class="text-gray-400 dark:text-gray-600 font-bold">→</span>
                <span class="text-[10px] font-bold uppercase tracking-wider text-gray-500 dark:text-gray-400">{{ t('dashboard.time_custom_to') }}</span>
                <div class="relative">
                  <input type="date" v-model="customEndDate" :min="customStartDate || undefined"
                    class="px-3 pr-9 py-1.5 text-xs font-medium border-2 border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 text-gray-700 dark:text-gray-100 rounded-sm focus:outline-none focus:border-indigo-500 [&::-webkit-calendar-picker-indicator]:opacity-0 [&::-webkit-calendar-picker-indicator]:absolute [&::-webkit-calendar-picker-indicator]:right-0 [&::-webkit-calendar-picker-indicator]:w-9 [&::-webkit-calendar-picker-indicator]:h-full [&::-webkit-calendar-picker-indicator]:cursor-pointer" />
                  <CalendarIcon class="pointer-events-none absolute right-2.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-gray-500 dark:text-gray-400" />
                </div>
              </div>
            </Transition>
          </div>

          <!-- Echte Reichweite + Peer Benchmark: mobile gestackt, desktop nebeneinander -->
          <div class="mb-6 flex flex-col md:flex-row md:items-stretch gap-4">

          <!-- Echte Reichweite -->
          <RangeCard
            v-if="carInfo?.batteryCapacityKwh && stats?.avgConsumptionKwhPer100km"
            class="md:w-80 shrink-0"
            :battery-capacity-kwh="carInfo.batteryCapacityKwh"
            :summer-consumption="stats.summerConsumptionKwhPer100km ?? null"
            :winter-consumption="stats.winterConsumptionKwhPer100km ?? null"
            :avg-consumption="stats.avgConsumptionKwhPer100km"
          />

          <!-- Peer Benchmark -->
          <PeerBenchmarkCard
            v-if="stats?.peerBenchmark && stats.peerBenchmark.peerAvgConsumptionKwhPer100km != null"
            class="flex-1 min-w-0"
            :benchmark="stats.peerBenchmark"
            :effective-battery-kwh="selectedCar?.effectiveBatteryCapacityKwh ?? null"
            :car-display-name="selectedCar ? [carDisplayName(selectedCar.brand, selectedCar.model), selectedCar.trim].filter(Boolean).join(' ') : ''"
          />

          <!-- Peer Benchmark Placeholder (collapsible: takes one line when empty) -->
          <div
            v-else-if="stats && carInfo?.batteryCapacityKwh && stats?.avgConsumptionKwhPer100km"
            class="flex-1 min-w-0 bg-white dark:bg-gray-800 border-2 border-gray-300 dark:border-gray-700 rounded-sm shadow-[3px_3px_0_0_#9ca3af] dark:shadow-[3px_3px_0_0_#4b5563] overflow-hidden"
          >
            <button
              type="button"
              @click="togglePeerPlaceholder"
              class="w-full px-4 py-2.5 flex items-center gap-2 text-left"
              :aria-expanded="!peerPlaceholderCollapsed">
              <UsersIcon class="w-4 h-4 text-gray-500 dark:text-gray-400 flex-shrink-0" />
              <span class="flex-1 text-[11px] font-bold uppercase tracking-wider text-gray-600 dark:text-gray-400 truncate">
                {{ t('dashboard.peer_no_data_title') }}
              </span>
              <ChevronDownIcon
                class="w-4 h-4 text-gray-400 flex-shrink-0 transition-transform duration-200"
                :class="{ 'rotate-180': !peerPlaceholderCollapsed }" />
            </button>
            <Transition name="slide-down">
              <div v-if="!peerPlaceholderCollapsed" class="px-4 pb-4 -mt-1 text-center">
                <p class="text-xs font-medium text-gray-500 dark:text-gray-500 max-w-xs mx-auto">{{ t('dashboard.peer_no_data_body') }}</p>
              </div>
            </Transition>
          </div>

          </div><!-- Ende Reichweite + Peer Wrapper -->

          <div v-if="error" class="mb-4 text-sm font-medium border-l-2 border-red-500 bg-red-50 dark:bg-red-950/30 text-red-700 dark:text-red-300 px-4 py-3 rounded-r-sm">{{ error }}</div>

          <!-- Empty State: No Cars -->
          <div v-if="cars.length === 0" class="min-h-[60vh] flex items-center justify-center">
            <div class="text-center max-w-md px-4">
              <div class="inline-flex items-center justify-center rounded-sm border-2 border-gray-900 dark:border-white bg-indigo-600 w-16 h-16 mb-6 shadow-[4px_4px_0_0_#030712] dark:shadow-[4px_4px_0_0_#ffffff]">
                <TruckIcon class="h-9 w-9 text-white" />
              </div>
              <h2 class="text-2xl font-bold tracking-tight text-gray-900 dark:text-gray-100 mb-3">
                {{ t('dashboard.no_car_title') }}
              </h2>
              <p class="text-sm font-medium text-gray-700 dark:text-gray-300 mb-8">
                {{ t('dashboard.no_car_desc') }}
              </p>
              <button
                @click="router.push('/cars')"
                class="inline-flex items-center justify-center gap-2 mx-auto bg-amber-500 hover:bg-amber-400 text-gray-950 font-bold uppercase tracking-wider text-xs px-6 py-3 rounded-sm border-2 border-amber-500 shadow-[4px_4px_0_0_#030712] dark:shadow-[4px_4px_0_0_#ffffff] active:translate-x-[3px] active:translate-y-[3px] active:shadow-none transition-[transform,box-shadow] duration-75">
                <TruckIcon class="h-5 w-5" />
                {{ t('dashboard.no_car_btn') }}
              </button>
            </div>
          </div>

          <!-- Empty State: No Logs in time range (but logs exist) -->
          <div v-else-if="stats && stats.totalCharges === 0 && hasAnyLogs" class="py-12 flex items-center justify-center">
            <div class="text-center max-w-md px-4">
              <h2 class="text-base font-bold uppercase tracking-wider text-gray-700 dark:text-gray-300 mb-2">{{ t('dashboard.no_logs_period_title') }}</h2>
              <p class="text-sm font-medium text-gray-500 dark:text-gray-400">{{ t('dashboard.no_logs_period_desc') }}</p>
            </div>
          </div>

          <!-- Empty State: Truly no logs at all -->
          <div v-else-if="stats && stats.totalCharges === 0">
            <DashboardEmptyState v-if="selectedCar" :car="selectedCar" />
          </div>

          <div v-else-if="stats" class="space-y-0">

        <!-- Key Metrics -->
        <div :class="['grid grid-cols-2 md:grid-cols-3 gap-4 pb-6 mb-0', showThgBanner && isGerman ? 'lg:grid-cols-6' : 'lg:grid-cols-5']">
          <div class="bg-white dark:bg-gray-800 border-2 border-gray-900 dark:border-white rounded-sm shadow-[4px_4px_0_0_#030712] dark:shadow-[4px_4px_0_0_#ffffff] overflow-hidden">
            <div class="h-1.5 bg-amber-500 border-b-2 border-gray-900 dark:border-white"></div>
            <div class="p-4">
              <p class="text-[10px] font-bold uppercase tracking-wider text-gray-500 dark:text-gray-400 mb-1">{{ t('dashboard.metric_total_energy') }}</p>
              <p class="text-2xl font-bold tabular-nums text-gray-900 dark:text-gray-100">{{ stats.totalKwhCharged?.toFixed(1) ?? '–' }} kWh</p>
              <p class="text-[11px] font-bold uppercase tracking-wider text-gray-500 dark:text-gray-500 mt-0.5">{{ stats.totalCharges }} {{ t('dashboard.metric_charges') }}</p>
            </div>
          </div>
          <div class="bg-white dark:bg-gray-800 border-2 border-gray-900 dark:border-white rounded-sm shadow-[4px_4px_0_0_#030712] dark:shadow-[4px_4px_0_0_#ffffff] overflow-hidden">
            <div class="h-1.5 bg-indigo-600 border-b-2 border-gray-900 dark:border-white"></div>
            <div class="p-4">
              <p class="text-[10px] font-bold uppercase tracking-wider text-gray-500 dark:text-gray-400 mb-1">{{ t('dashboard.metric_total_cost') }}</p>
              <p class="text-2xl font-bold tabular-nums text-gray-900 dark:text-gray-100">{{ stats.totalCostEur != null ? formatCurrency(stats.totalCostEur) : '–' }}</p>
              <p class="text-[11px] font-bold uppercase tracking-wider text-gray-500 dark:text-gray-500 mt-0.5">Ø {{ stats.avgCostPerKwh != null ? formatCostPerKwh(stats.avgCostPerKwh) : '–' }}</p>
            </div>
          </div>
          <div v-if="stats.totalDistanceKm != null"
            class="bg-white dark:bg-gray-800 border-2 border-gray-900 dark:border-white rounded-sm shadow-[4px_4px_0_0_#030712] dark:shadow-[4px_4px_0_0_#ffffff] overflow-hidden">
            <div class="h-1.5 bg-green-500 border-b-2 border-gray-900 dark:border-white"></div>
            <div class="p-4">
              <div class="flex items-center gap-1 mb-1">
                <p class="text-[10px] font-bold uppercase tracking-wider text-gray-500 dark:text-gray-400">{{ t('dashboard.metric_total_distance') }}</p>
                <button
                  type="button"
                  class="flex-shrink-0 text-gray-400 hover:text-gray-600 dark:text-gray-500 dark:hover:text-gray-300 focus:outline-none focus:ring-2 focus:ring-green-500 rounded-sm"
                  :aria-label="t('dashboard.metric_info_aria')"
                  @click.stop="openMetricTooltip = openMetricTooltip === 'distance' ? null : 'distance'">
                  <InformationCircleIcon class="w-3.5 h-3.5" />
                </button>
              </div>
              <p class="text-2xl font-bold tabular-nums text-gray-900 dark:text-gray-100">{{ formatDistance(stats.totalDistanceKm) }}<span class="hidden sm:inline-block font-medium text-gray-500 dark:text-gray-500 text-base ml-1">{{ t('dashboard.metric_driven') }}</span></p>
              <div v-if="openMetricTooltip === 'distance'"
                class="mt-2 p-2.5 rounded-sm bg-gray-50 dark:bg-gray-900 border-2 border-gray-300 dark:border-gray-700 text-xs font-medium text-gray-700 dark:text-gray-300 leading-relaxed space-y-1.5">
                <p>{{ t('dashboard.metric_total_distance_tooltip') }}</p>
                <p class="italic text-gray-500 dark:text-gray-400">{{ t('dashboard.metric_complete_definition') }}</p>
                <router-link to="/consumption-methodology" class="inline-flex items-center gap-1 text-[11px] font-bold uppercase tracking-wider text-indigo-600 dark:text-indigo-400 underline underline-offset-2 hover:text-indigo-800 dark:hover:text-indigo-300 transition-colors">
                  {{ t('dashboard.metric_consumption_methodology_link') }}
                  <ChevronRightIcon class="w-3 h-3 flex-shrink-0" />
                </router-link>
              </div>
            </div>
          </div>
          <div v-if="stats.avgConsumptionKwhPer100km != null"
            class="bg-white dark:bg-gray-800 border-2 border-gray-900 dark:border-white rounded-sm shadow-[4px_4px_0_0_#030712] dark:shadow-[4px_4px_0_0_#ffffff] overflow-hidden">
            <div class="h-1.5 bg-red-500 border-b-2 border-gray-900 dark:border-white"></div>
            <div class="p-4">
              <div class="flex items-center gap-1 mb-1">
                <p class="text-[10px] font-bold uppercase tracking-wider text-gray-500 dark:text-gray-400">{{ t('dashboard.metric_avg_consumption') }}</p>
                <button
                  type="button"
                  class="flex-shrink-0 text-gray-400 hover:text-gray-600 dark:text-gray-500 dark:hover:text-gray-300 focus:outline-none focus:ring-2 focus:ring-red-500 rounded-sm"
                  :aria-label="t('dashboard.metric_info_aria')"
                  @click.stop="openMetricTooltip = openMetricTooltip === 'consumption' ? null : 'consumption'">
                  <InformationCircleIcon class="w-3.5 h-3.5" />
                </button>
              </div>
              <p class="text-2xl font-bold tabular-nums text-gray-900 dark:text-gray-100">{{ formatConsumption(stats.avgConsumptionKwhPer100km, { showUnit: false }) }}</p>
              <p class="text-[11px] font-bold uppercase tracking-wider text-gray-500 dark:text-gray-500 mt-0.5">{{ consumptionUnitLabel() }}</p>
              <p v-if="stats.estimatedConsumptionCount > 0" class="text-[10px] font-bold uppercase tracking-wider text-red-600 dark:text-red-400 mt-2">
                {{ t('dashboard.metric_estimated', { n: stats.estimatedConsumptionCount }) }}
              </p>
              <div v-if="openMetricTooltip === 'consumption'"
                class="mt-2 p-2.5 rounded-sm bg-gray-50 dark:bg-gray-900 border-2 border-gray-300 dark:border-gray-700 text-xs font-medium text-gray-700 dark:text-gray-300 leading-relaxed space-y-1.5">
                <p>{{ t('dashboard.metric_avg_consumption_tooltip') }}</p>
                <p class="italic text-gray-500 dark:text-gray-400">{{ t('dashboard.metric_complete_definition') }}</p>
                <router-link to="/consumption-methodology" class="inline-flex items-center gap-1 text-[11px] font-bold uppercase tracking-wider text-indigo-600 dark:text-indigo-400 underline underline-offset-2 hover:text-indigo-800 dark:hover:text-indigo-300 transition-colors">
                  {{ t('dashboard.metric_consumption_methodology_link') }}
                  <ChevronRightIcon class="w-3 h-3 flex-shrink-0" />
                </router-link>
              </div>
            </div>
          </div>
          <div v-if="avgCostPer100km != null"
            class="bg-white dark:bg-gray-800 border-2 border-gray-900 dark:border-white rounded-sm shadow-[4px_4px_0_0_#030712] dark:shadow-[4px_4px_0_0_#ffffff] overflow-hidden">
            <div class="h-1.5 bg-pink-500 border-b-2 border-gray-900 dark:border-white"></div>
            <div class="p-4">
              <div class="flex items-center gap-1 mb-1">
                <p class="text-[10px] font-bold uppercase tracking-wider text-gray-500 dark:text-gray-400">{{ t('dashboard.metric_avg_cost') }}</p>
                <button
                  type="button"
                  class="flex-shrink-0 text-gray-400 hover:text-gray-600 dark:text-gray-500 dark:hover:text-gray-300 focus:outline-none focus:ring-2 focus:ring-pink-500 rounded-sm"
                  :aria-label="t('dashboard.metric_info_aria')"
                  @click.stop="openMetricTooltip = openMetricTooltip === 'costPer100km' ? null : 'costPer100km'">
                  <InformationCircleIcon class="w-3.5 h-3.5" />
                </button>
              </div>
              <p class="text-2xl font-bold tabular-nums text-gray-900 dark:text-gray-100">{{ formatCostPerDistance(avgCostPer100km) }}</p>
              <div v-if="openMetricTooltip === 'costPer100km'"
                class="mt-2 p-2.5 rounded-sm bg-gray-50 dark:bg-gray-900 border-2 border-gray-300 dark:border-gray-700 text-xs font-medium text-gray-700 dark:text-gray-300 leading-relaxed space-y-1.5">
                <p>{{ t('dashboard.metric_avg_cost_tooltip') }}</p>
                <p class="italic text-gray-500 dark:text-gray-400">{{ t('dashboard.metric_complete_definition') }}</p>
                <router-link to="/consumption-methodology" class="inline-flex items-center gap-1 text-[11px] font-bold uppercase tracking-wider text-indigo-600 dark:text-indigo-400 underline underline-offset-2 hover:text-indigo-800 dark:hover:text-indigo-300 transition-colors">
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
            class="relative bg-green-50 dark:bg-green-950/30 border-2 border-green-500 rounded-sm shadow-[4px_4px_0_0_#16a34a] active:translate-x-[3px] active:translate-y-[3px] active:shadow-none transition-[transform,box-shadow] duration-75 overflow-hidden group cursor-pointer"
          >
            <div class="h-1.5 bg-green-500 border-b-2 border-green-500"></div>
            <div class="p-4 pr-8">
              <p class="text-[11px] font-bold uppercase tracking-[0.14em] text-green-700 dark:text-green-400 mb-1">THG-Prämie</p>
              <p class="text-sm font-bold text-gray-900 dark:text-gray-100 mb-1">Schon beantragt?</p>
              <p class="text-xs font-medium text-gray-600 dark:text-gray-400 leading-snug mt-1">Falls nicht, kannst du das hier tun und gleichzeitig den Betrieb der Seite unterstützen.</p>
            </div>
            <button
              @click.stop="dismissThgBanner"
              class="absolute top-3 right-2 h-5 w-5 rounded-sm border-2 border-gray-900 dark:border-white bg-white dark:bg-gray-800 hover:bg-gray-100 dark:hover:bg-gray-700 text-gray-700 dark:text-gray-300 flex items-center justify-center transition"
              title="Hinweis ausblenden"
            >
              <XMarkIcon class="h-3 w-3" />
            </button>
            <span class="absolute bottom-1 right-3 text-[9px] font-bold uppercase tracking-wider text-gray-400 dark:text-gray-600">Affiliate-Link</span>
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


        <!-- Chart 1: Charging & Costs -->
        <div class="pt-6">
          <div class="bg-white dark:bg-gray-800 md:border-2 md:border-gray-900 md:dark:border-white md:rounded-sm md:shadow-[4px_4px_0_0_#030712] md:dark:shadow-[4px_4px_0_0_#ffffff] py-4 md:p-6 -mx-4 md:mx-0">
            <div v-if="!chartsReady && isInitialLoad" class="h-64 sm:h-72 bg-gray-100 dark:bg-gray-700 animate-pulse rounded-sm mx-4 md:mx-0"></div>
            <template v-else>
              <div class="flex flex-col sm:flex-row sm:items-center justify-center gap-4 sm:gap-6 mb-4 px-4 md:px-0">
                <h2 class="text-base md:text-lg font-bold uppercase tracking-wider text-gray-900 dark:text-gray-100 text-center">{{ t('dashboard.chart_charging_costs') }}</h2>
                <div class="flex flex-wrap gap-2 sm:gap-4 text-xs sm:text-sm justify-center">
                  <label class="flex items-center gap-1 sm:gap-2 cursor-pointer">
                    <input type="checkbox" v-model="showCostPerKwh"
                      class="w-3 h-3 sm:w-4 sm:h-4 rounded-sm accent-indigo-600 cursor-pointer" />
                    <span class="font-bold uppercase tracking-wider text-[11px] text-gray-700 dark:text-gray-300">
                      <span class="inline-block w-2 sm:w-3 h-0.5 bg-indigo-600 mr-1 align-middle"></span>
                      {{ currencySymbol }}/kWh
                    </span>
                  </label>
                  <label class="flex items-center gap-1 sm:gap-2 cursor-pointer">
                    <input type="checkbox" v-model="showKwh"
                      class="w-3 h-3 sm:w-4 sm:h-4 rounded-sm accent-amber-500 cursor-pointer" />
                    <span class="font-bold uppercase tracking-wider text-[11px] text-gray-700 dark:text-gray-300">
                      <span class="inline-block w-2 sm:w-3 h-0.5 bg-amber-500 mr-1 align-middle"></span>
                      kWh
                    </span>
                  </label>
                </div>
              </div>
              <div v-if="chargingChartData && chargingChartData.datasets.length > 0" class="h-64 sm:h-72">
                <Line :data="chargingChartData" :options="chargingChartOptions" />
              </div>
              <div v-else class="text-center py-10 text-sm font-medium text-gray-500 dark:text-gray-400 px-4 md:px-0">
                {{ t('dashboard.chart_no_data') }}
              </div>
              <div class="flex flex-wrap gap-x-6 gap-y-1 mt-3 text-[10px] font-bold uppercase tracking-wider text-gray-500 dark:text-gray-500 px-4 md:px-0">
                <span>{{ t('dashboard.chart_left_axis') }}: {{ currencySymbol }}/kWh</span>
                <span>{{ t('dashboard.chart_right_axis') }}: kWh</span>
              </div>
            </template>
          </div>
        </div>

        <!-- Chart 2: Range & Efficiency (only if distance data exists) -->
        <div v-if="hasDistanceData" class="pt-6">
          <div class="bg-white dark:bg-gray-800 md:border-2 md:border-gray-900 md:dark:border-white md:rounded-sm md:shadow-[4px_4px_0_0_#030712] md:dark:shadow-[4px_4px_0_0_#ffffff] py-4 md:p-6 -mx-4 md:mx-0">
            <div v-if="!chartsReady && isInitialLoad" class="h-64 sm:h-72 bg-gray-100 dark:bg-gray-700 animate-pulse rounded-sm mx-4 md:mx-0"></div>
            <template v-else>
              <div class="flex flex-col sm:flex-row sm:items-center justify-center gap-4 sm:gap-6 mb-4 px-4 md:px-0">
                <h2 class="text-base md:text-lg font-bold uppercase tracking-wider text-gray-900 dark:text-gray-100 text-center">{{ t('dashboard.chart_range_efficiency') }}</h2>
                <div class="flex flex-wrap gap-2 sm:gap-4 text-xs sm:text-sm justify-center">
                  <label class="flex items-center gap-1 sm:gap-2 cursor-pointer">
                    <input type="checkbox" v-model="showConsumption"
                      class="w-3 h-3 sm:w-4 sm:h-4 rounded-sm accent-red-500 cursor-pointer" />
                    <span class="font-bold uppercase tracking-wider text-[11px] text-gray-700 dark:text-gray-300">
                      <span class="inline-block w-2 sm:w-3 h-0.5 bg-red-500 mr-1 align-middle"></span>
                      {{ consumptionUnitLabel() }}
                    </span>
                  </label>
                  <label class="flex items-center gap-1 sm:gap-2 cursor-pointer">
                    <input type="checkbox" v-model="showDistance"
                      class="w-3 h-3 sm:w-4 sm:h-4 rounded-sm accent-emerald-500 cursor-pointer" />
                    <span class="font-bold uppercase tracking-wider text-[11px] text-gray-700 dark:text-gray-300">
                      <span class="inline-block w-2 sm:w-3 h-0.5 bg-emerald-500 mr-1 align-middle"></span>
                      {{ distanceUnitLabel() }}
                    </span>
                  </label>
                </div>
              </div>
              <div v-if="efficiencyChartData && efficiencyChartData.datasets.length > 0" class="h-64 sm:h-72">
                <Line :data="efficiencyChartData" :options="efficiencyChartOptions" />
              </div>
              <div v-else class="text-center py-10 text-sm font-medium text-gray-500 dark:text-gray-400 px-4 md:px-0">
                {{ t('dashboard.chart_no_data') }}
              </div>
              <div class="flex flex-wrap gap-x-6 gap-y-1 mt-3 text-[10px] font-bold uppercase tracking-wider text-gray-500 dark:text-gray-500 px-4 md:px-0">
                <span>{{ t('dashboard.chart_left_axis') }}: {{ consumptionUnitLabel() }}</span>
                <span>{{ t('dashboard.chart_right_axis') }}: {{ distanceUnitLabel() }}</span>
              </div>
            </template>
          </div>
        </div>

        <!-- WLTP Delta Bar Chart -->
        <div v-if="wltp && hasDistanceData && wltpChartData" class="pt-6">
          <div class="bg-white dark:bg-gray-800 md:border-2 md:border-gray-900 md:dark:border-white md:rounded-sm md:shadow-[4px_4px_0_0_#030712] md:dark:shadow-[4px_4px_0_0_#ffffff] py-4 md:p-6 -mx-4 md:mx-0">
          <div v-if="!chartsReady && isInitialLoad" :style="{ height: wltpChartHeight }" class="bg-gray-100 dark:bg-gray-700 animate-pulse rounded-sm mx-4 md:mx-0"></div>
          <template v-else>
          <div class="mb-4 text-center px-4 md:px-0">
            <h2 class="text-base md:text-lg font-bold uppercase tracking-wider text-gray-900 dark:text-gray-100">
              <template v-if="isCustomCompare">
                {{ t('dashboard.chart_consumption_vs_custom_prefix') }} <strong>{{ customCompareValue != null ? formatConsumption(customCompareValue) : '–' }}</strong>
              </template>
              <template v-else>{{ t('dashboard.chart_consumption_vs_wltp') }}</template>
            </h2>
            <p class="text-xs sm:text-sm font-medium text-gray-600 dark:text-gray-400 mt-1">
              WLTP: <strong class="tabular-nums">{{ wltp.officialConsumptionKwhPer100km != null ? formatConsumption(wltp.officialConsumptionKwhPer100km) : '–' }}</strong>
              ({{ wltp.officialRangeKm != null ? formatDistance(wltp.officialRangeKm) : '–' }}, {{ wltp.wltpType }})
              <span class="hidden sm:inline">
                · <span class="text-emerald-600 dark:text-emerald-400 font-bold">{{ t('dashboard.chart_green_better') }}</span>
                · <span class="text-red-600 dark:text-red-400 font-bold">{{ t('dashboard.chart_red_worse') }}</span>
              </span>
            </p>
            <!-- Custom compare controls -->
            <div class="mt-2 flex items-center justify-center gap-3 flex-wrap">
              <button
                @click="showCompareInput = !showCompareInput"
                class="text-[11px] font-bold uppercase tracking-wider text-blue-600 dark:text-blue-400 underline underline-offset-2 hover:text-blue-700 dark:hover:text-blue-300"
              >
                {{ isCustomCompare ? t('dashboard.chart_compare_edit') : t('dashboard.chart_compare_customize') }}
              </button>
              <button
                v-if="isCustomCompare"
                @click="resetToWltp"
                class="text-[11px] font-bold uppercase tracking-wider text-gray-500 dark:text-gray-500 underline underline-offset-2 hover:text-gray-700 dark:hover:text-gray-300"
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
                class="w-24 px-2 py-1 text-sm font-medium border-2 border-gray-300 dark:border-gray-600 rounded-sm bg-white dark:bg-gray-900 text-gray-800 dark:text-gray-200 focus:outline-none focus:border-blue-500 transition-colors"
              />
              <span class="text-[10px] font-bold uppercase tracking-wider text-gray-500 dark:text-gray-400">{{ consumptionUnitLabel() }}</span>
              <button
                @click="saveCustomCompare"
                class="inline-flex items-center justify-center bg-emerald-600 hover:bg-emerald-500 text-white font-bold uppercase tracking-wider text-[10px] px-3 py-1.5 rounded-sm border-2 border-emerald-600 shadow-[2px_2px_0_0_#030712] dark:shadow-[2px_2px_0_0_#ffffff] active:translate-x-[2px] active:translate-y-[2px] active:shadow-none transition-[transform,box-shadow] duration-75"
              >
                {{ t('dashboard.chart_compare_save') }}
              </button>
            </div>
            <p class="text-[10px] font-bold uppercase tracking-wider text-gray-500 dark:text-gray-500 mt-1">{{ t('model.wltp_measurement_note') }}</p>
          </div>
            <div :class="wltpChartScrollable ? 'overflow-y-auto' : ''" :style="{ height: wltpChartHeight }">
              <Bar :data="wltpChartData" :options="wltpChartOptions" />
            </div>
          </template>
          </div>
        </div>

        <!-- WLTP missing hint -->
        <div v-else-if="!wltp && hasDistanceData" class="pt-6">
          <div class="border-l-2 border-amber-500 bg-amber-50 dark:bg-amber-950/30 px-4 py-3 rounded-r-sm text-sm font-medium text-amber-700 dark:text-amber-300">
            {{ t('dashboard.wltp_missing') }}
            <router-link to="/cars" class="font-bold underline">{{ t('dashboard.wltp_missing_link') }}</router-link>
            {{ t('dashboard.wltp_missing_suffix') }}
          </div>
        </div>

        <!-- Charging Heat Map -->
        <div class="pt-6">
          <div class="bg-white dark:bg-gray-800 md:border-2 md:border-gray-900 md:dark:border-white md:rounded-sm md:shadow-[4px_4px_0_0_#030712] md:dark:shadow-[4px_4px_0_0_#ffffff] py-4 md:p-6 -mx-4 md:mx-0 mb-4 md:mb-0">
            <div v-if="!chartsReady && isInitialLoad" class="h-96 bg-gray-100 dark:bg-gray-700 animate-pulse rounded-sm mx-4 md:mx-0"></div>
            <template v-else>
              <div class="mb-4 px-4 md:px-0">
                <h2 class="text-base md:text-lg font-bold uppercase tracking-wider text-gray-900 dark:text-gray-100">{{ t('dashboard.map_title') }}</h2>
                <p class="text-xs font-medium text-gray-600 dark:text-gray-400 mt-1">
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

  <!-- Mobile sticky bottom filter bar (mirrors LogsView's bulk toggle bar) -->
  <Teleport to="body">
    <Transition
      enter-active-class="transition duration-200 ease-out"
      enter-from-class="opacity-0 translate-y-2"
      enter-to-class="opacity-100 translate-y-0"
      leave-active-class="transition duration-150 ease-in"
      leave-from-class="opacity-100 translate-y-0"
      leave-to-class="opacity-0 translate-y-2">
      <div v-if="filterBarVisible"
        ref="filterBar"
        class="md:hidden fixed bottom-0 left-0 right-0 z-30 bg-gray-900 dark:bg-gray-800 border-t border-white/10 px-3 py-2.5"
        style="padding-bottom: calc(env(safe-area-inset-bottom, 0px) + 10px);">
        <div class="flex gap-2">
          <div class="flex-1 relative">
            <select v-model="selectedTimeRange"
              :aria-label="t('dashboard.time_range_label')"
              class="block w-full appearance-none px-3 pr-8 py-2 bg-gray-800 dark:bg-gray-700 border border-white/10 text-white text-xs font-medium rounded-full focus:outline-none focus-visible:ring-2 focus-visible:ring-indigo-400">
              <option v-for="option in timeRangeOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
            </select>
            <ChevronDownIcon class="pointer-events-none absolute right-2.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-white/70" />
          </div>
          <div class="flex-1 relative">
            <select v-model="selectedGroupBy"
              :aria-label="t('dashboard.group_by_label')"
              class="block w-full appearance-none px-3 pr-8 py-2 bg-gray-800 dark:bg-gray-700 border border-white/10 text-white text-xs font-medium rounded-full focus:outline-none focus-visible:ring-2 focus-visible:ring-indigo-400">
              <option v-for="opt in groupByOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
            </select>
            <ChevronDownIcon class="pointer-events-none absolute right-2.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-white/70" />
          </div>
        </div>
        <div v-if="selectedTimeRange === 'CUSTOM'" class="flex gap-2 mt-2">
          <div class="flex-1 relative">
            <input type="date" v-model="customStartDate" :max="customEndDate || undefined"
              :aria-label="t('dashboard.time_custom_from')"
              class="block w-full px-3 pr-8 py-2 bg-gray-800 dark:bg-gray-700 border border-white/10 text-white text-xs rounded-full focus:outline-none focus-visible:ring-2 focus-visible:ring-indigo-400 [&::-webkit-calendar-picker-indicator]:opacity-0 [&::-webkit-calendar-picker-indicator]:absolute [&::-webkit-calendar-picker-indicator]:right-0 [&::-webkit-calendar-picker-indicator]:w-8 [&::-webkit-calendar-picker-indicator]:h-full [&::-webkit-calendar-picker-indicator]:cursor-pointer" />
            <CalendarIcon class="pointer-events-none absolute right-2.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-white/70" />
          </div>
          <div class="flex-1 relative">
            <input type="date" v-model="customEndDate" :min="customStartDate || undefined"
              :aria-label="t('dashboard.time_custom_to')"
              class="block w-full px-3 pr-8 py-2 bg-gray-800 dark:bg-gray-700 border border-white/10 text-white text-xs rounded-full focus:outline-none focus-visible:ring-2 focus-visible:ring-indigo-400 [&::-webkit-calendar-picker-indicator]:opacity-0 [&::-webkit-calendar-picker-indicator]:absolute [&::-webkit-calendar-picker-indicator]:right-0 [&::-webkit-calendar-picker-indicator]:w-8 [&::-webkit-calendar-picker-indicator]:h-full [&::-webkit-calendar-picker-indicator]:cursor-pointer" />
            <CalendarIcon class="pointer-events-none absolute right-2.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-white/70" />
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</div>
</template>

<style scoped>
@keyframes vehicle-charging-glow {
  0%, 100% {
    box-shadow: 0 0 14px 4px rgba(74, 222, 128, 0.4), 0 0 32px 10px rgba(74, 222, 128, 0.15);
  }
  50% {
    box-shadow: 0 0 24px 10px rgba(34, 197, 94, 0.6), 0 0 56px 20px rgba(34, 197, 94, 0.25);
  }
}

@keyframes vehicle-charging-glow-dark {
  0%, 100% {
    box-shadow: 0 0 18px 6px rgba(74, 222, 128, 0.55), 0 0 40px 14px rgba(74, 222, 128, 0.2);
  }
  50% {
    box-shadow: 0 0 32px 14px rgba(134, 239, 172, 0.75), 0 0 64px 24px rgba(74, 222, 128, 0.35);
  }
}

.vehicle-charging-glow {
  animation: vehicle-charging-glow 1.8s ease-in-out infinite;
}

:global(.dark) .vehicle-charging-glow {
  animation: vehicle-charging-glow-dark 1.8s ease-in-out infinite;
}

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
