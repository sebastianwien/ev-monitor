<script setup lang="ts">
import { ref, computed, reactive, onMounted, onUnmounted, watch, defineAsyncComponent } from 'vue'
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
  BoltIcon,
  PencilSquareIcon,
  ArrowDownTrayIcon,
  ClockIcon,
  Battery0Icon,
  SunIcon,
  ChevronLeftIcon,
  ChevronRightIcon,
  ChevronDownIcon,
  ChevronUpIcon,
  ListBulletIcon,
  TrashIcon,
  ExclamationTriangleIcon,
  InformationCircleIcon,
  XMarkIcon,
  CalendarIcon,
  ArrowsRightLeftIcon,
  PlusIcon,
  HandThumbUpIcon,
  HandThumbDownIcon,
  UsersIcon,
} from '@heroicons/vue/24/outline'
import { useRouter } from 'vue-router'
import { tempBadgeClass } from '../utils/temperatureColor'
import { consumptionTextClass } from '../utils/consumptionColor'
import ConsumptionInfoBox from '../components/dashboard/ConsumptionInfoBox.vue'
import EditLogModal from '../components/dashboard/EditLogModal.vue'
import { costBadgeClass } from '../utils/costColor'
import LicensePlate from '../components/car/LicensePlate.vue'
const ChargingHeatMap = defineAsyncComponent(() => import('../components/dashboard/ChargingHeatMap.vue'))
import RewardSystemUpdateBanner from '../components/shared/RewardSystemUpdateBanner.vue'
import { useCountryStore } from '../stores/country'
import { useAuthStore } from '../stores/auth'
import { analytics } from '../services/analytics'
import SupportPopover from '../components/settings/SupportPopover.vue'
import ImplausibleLogsModal from '../components/dashboard/ImplausibleLogsModal.vue'
import PeerBenchmarkCard from '../components/dashboard/PeerBenchmarkCard.vue'
import RangeCard from '../components/dashboard/RangeCard.vue'
import DashboardEmptyState from '../components/dashboard/DashboardEmptyState.vue'
import { useLocaleFormat } from '../composables/useLocaleFormat'
import { useDashboardStats } from '../composables/useDashboardStats'
import { useDashboardCharts } from '../composables/useDashboardCharts'
import { useLogList } from '../composables/useLogList'
import { useWallboxStore } from '../stores/wallbox'
import { enumToLabel } from '../utils/enumLabel'

ChartJS.register(CategoryScale, LinearScale, BarElement, PointElement, LineElement, Title, Tooltip, Legend, Filler, ChartDataLabels)

const { t } = useI18n()
const router = useRouter()
const { formatConsumption, consumptionUnitLabel, formatDistance, distanceUnitLabel, formatCurrency, formatCostPerKwh, formatCostPerDistance, currencySymbol } = useLocaleFormat()

// -- Dashboard Stats --
const {
  selectedCarId, stats, carInfo, wltp, loading, chartsReady, isInitialLoad, error,
  cars, carImageUrls, selectedTimeRange, selectedGroupBy, customStartDate, customEndDate,
  importBannerDismissed, implausibleBannerDismissed, teslaStatus, smartcarStatus, implausibleCount, hasDistanceData,
  timeRangeOptions, groupByOptions, dismissImportBanner, dismissImplausibleBanner, fetchImplausibleCount,
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

// -- Log List --
const logsSection = ref<HTMLElement | null>(null)
const {
  logsPage, logsLoading, hasMoreLogs, editingLog,
  expandedGroups, toggleLadegruppe, hasAnyLogs, showOdometer, showCostAbsolute,
  openTooltipLogId, reassignModalEntry, reassignSelectedCarId, reassignSaving,
  reassignError, reassignSuccessMessage, otherCars, openReassignModal, saveReassign,
  fetchLogs, scrollToLogs, fetchLogsAndScroll, refreshLogsAndGroups, deleteLog,
  formatLogDate, formatTripTimeRange, toggleOdometerDisplay, sourceInfo, mergedLogFeed,
  editingTripId, addingTripAfterId, tripForm, tripSaving, tripError,
  startEditTrip, cancelTripEdit, saveTripEdit, startAddTrip, saveNewTrip, deleteTripEntry,
  mergeTripEntry,
  submitTripFeedback,
} = useLogList(selectedCarId, cars, logsSection)

const deletingTripId = ref<string | null>(null)
let _deleteTimer: ReturnType<typeof setTimeout> | null = null
onUnmounted(() => { if (_deleteTimer) clearTimeout(_deleteTimer) })

async function handleDeleteTrip(id: string) {
  if (!confirm(t('dashboard.trip_delete_confirm'))) return
  deletingTripId.value = id
  _deleteTimer = setTimeout(async () => {
    _deleteTimer = null
    try {
      await deleteTripEntry(id)
    } catch {
      deletingTripId.value = null
      alert(t('dashboard.err_load'))
      return
    }
    deletingTripId.value = null
  }, 300)
}

// -- Trip group collapse --
const collapsedTripGroups = ref<Set<string>>(new Set())
function toggleTripGroup(groupId: string) {
  const next = new Set(collapsedTripGroups.value)
  if (next.has(groupId)) next.delete(groupId)
  else next.add(groupId)
  collapsedTripGroups.value = next
}

// -- Trip merge --
const mergePreviewForTripId = ref<string | null>(null)
const tripMerging = ref(false)
const tripMergeError = ref<string | null>(null)

const previousTripMap = computed<Record<string, any>>(() => {
  const feed = mergedLogFeed.value
  const result: Record<string, any> = {}
  for (let i = 0; i < feed.length; i++) {
    if (!feed[i]._isTrip) continue
    for (let j = i + 1; j < feed.length; j++) {
      if (feed[j]._isTrip) { result[feed[i].id] = feed[j]; break }
    }
  }
  return result
})

function cancelTripEditFull() {
  cancelTripEdit()
  mergePreviewForTripId.value = null
}

async function doMergeTrip(survivingId: string, previousId: string) {
  tripMerging.value = true
  tripMergeError.value = null
  try {
    await mergeTripEntry(survivingId, previousId)
    cancelTripEditFull()
  } catch {
    tripMergeError.value = t('dashboard.err_load')
  } finally {
    tripMerging.value = false
  }
}

// saveReassign needs fetchStatistics, so wrap it
const doSaveReassign = () => saveReassign(fetchStatistics)

// -- Trip feedback --
const FEEDBACK_TAGS = ['distance_wrong', 'time_wrong', 'duplicate', 'other'] as const
const feedbackOpenId    = ref<string | null>(null)
const feedbackTags      = ref<string[]>([])
const feedbackComment   = ref('')
const feedbackPending   = reactive<Record<string, string | null>>({})
const feedbackTimers    = new Map<string, ReturnType<typeof setTimeout>>()

// Returns the effective rating for a trip, taking optimistic pending state into account
const effectiveRating = (tripId: string, serverFeedback: string | null | undefined): string | null => {
  if (tripId in feedbackPending) return feedbackPending[tripId]
  if (!serverFeedback) return null
  if (serverFeedback.startsWith('positive')) return 'positive'
  if (serverFeedback.startsWith('negative')) return 'negative'
  return null
}

const toggleFeedbackTag = (tag: string) => {
  const idx = feedbackTags.value.indexOf(tag)
  if (idx === -1) feedbackTags.value.push(tag)
  else feedbackTags.value.splice(idx, 1)
}

const openFeedbackPanel = (tripId: string) => {
  if (feedbackOpenId.value === tripId) {
    feedbackOpenId.value = null
    return
  }
  feedbackOpenId.value = tripId
  feedbackTags.value   = []
  feedbackComment.value = ''
}

// Debounced toggle for 👍/👎 direct buttons (600ms)
const toggleRating = (tripId: string, rating: 'positive' | 'negative', serverFeedback: string | null | undefined) => {
  const current = effectiveRating(tripId, serverFeedback)
  const next = current === rating ? null : rating

  // Optimistic update
  feedbackPending[tripId] = next

  // Close negative panel if switching away
  if (next !== 'negative' && feedbackOpenId.value === tripId) feedbackOpenId.value = null
  // Open panel for negative
  if (next === 'negative') openFeedbackPanel(tripId)

  // Debounce: reset timer on each click
  if (feedbackTimers.has(tripId)) clearTimeout(feedbackTimers.get(tripId)!)
  const timer = setTimeout(async () => {
    await submitTripFeedback(tripId, next ?? '')
    delete feedbackPending[tripId]
    feedbackTimers.delete(tripId)
  }, 600)
  feedbackTimers.set(tripId, timer)
}

// Panel submit (no debounce - deliberate action)
const sendNegativeFeedback = async (tripId: string) => {
  const parts: string[] = ['negative']
  const tags = feedbackTags.value.join(',')
  if (tags) parts.push(tags)
  if (feedbackComment.value.trim()) parts.push(feedbackComment.value.trim())
  await submitTripFeedback(tripId, parts.join(' | '))
  feedbackOpenId.value = null
  feedbackTags.value   = []
  feedbackComment.value = ''
}

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

const isVehicleCharging = (car: any) => isSmartcarCharging(car) || isWallboxCharging()

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

// -- THG Banner --
const countryStore = useCountryStore()
const authStore    = useAuthStore()
const isPremium    = computed(() => authStore.isPremium)
const isAdmin      = computed(() => authStore.isAdmin)
const isBetaTester = computed(() => authStore.isBetaTester)
const canAccessTrips = computed(() => isPremium.value || isBetaTester.value || isAdmin.value)
const isGerman = computed(() => countryStore.country === 'DE')
const thgDismissedAt = ref<number | null>(
  Number(localStorage.getItem('thg_banner_dismissed_at')) || null
)
const showThgBanner = computed(() => {
  if (!thgDismissedAt.value) return true
  return (Date.now() - thgDismissedAt.value) / 86_400_000 >= 90
})
function displayKwh(kwhCharged: number | null, kwhAtVehicle: number | null): string {
  if (kwhCharged != null && kwhAtVehicle != null) return `${kwhCharged}/${kwhAtVehicle}`
  return String(kwhCharged ?? kwhAtVehicle ?? '')
}

function tripConsumption(entry: any): { kwhPer100km: number; estimated: boolean } | null {
  if (!entry.distanceKm || entry.distanceKm <= 0) return null
  if (entry.estimatedConsumedKwh != null) {
    return { kwhPer100km: entry.estimatedConsumedKwh / entry.distanceKm * 100, estimated: false }
  }
  const cap = selectedCar.value?.effectiveBatteryCapacityKwh
  if (entry.socStart != null && entry.socEnd != null && entry.socStart > entry.socEnd && cap) {
    const isDecimalSoc = entry.socStart % 1 !== 0 || entry.socEnd % 1 !== 0
    return { kwhPer100km: (entry.socStart - entry.socEnd) * cap / entry.distanceKm, estimated: !isDecimalSoc }
  }
  return null
}

function dismissThgBanner() {
  const now = Date.now()
  thgDismissedAt.value = now
  localStorage.setItem('thg_banner_dismissed_at', String(now))
}
function handleThgCardClick() {
  analytics.trackAffiliateBannerClicked('thg')
  window.open('https://Geld-fuer-eAuto.de/ref/evmonitor', '_blank', 'noopener,noreferrer')
}

onMounted(() => initCars())

function onTripFormEnter(el: Element, done: () => void) {
  const h = el as HTMLElement
  const cs = getComputedStyle(h)
  const targetHeight = h.scrollHeight
  const targetMarginTop = cs.marginTop
  const targetPaddingTop = cs.paddingTop
  const targetPaddingBottom = cs.paddingBottom
  h.style.overflow = 'hidden'
  h.style.height = '0'
  h.style.marginTop = '0'
  h.style.paddingTop = '0'
  h.style.paddingBottom = '0'
  h.style.opacity = '0'
  requestAnimationFrame(() => {
    h.style.transition = 'height 0.28s ease, margin-top 0.28s ease, padding-top 0.28s ease, padding-bottom 0.28s ease, opacity 0.22s ease'
    h.style.height = targetHeight + 'px'
    h.style.marginTop = targetMarginTop
    h.style.paddingTop = targetPaddingTop
    h.style.paddingBottom = targetPaddingBottom
    h.style.opacity = '1'
    setTimeout(done, 280)
  })
}
function onTripFormAfterEnter(el: Element) {
  const h = el as HTMLElement
  h.style.cssText = ''
}
function onTripFormLeave(el: Element, done: () => void) {
  const h = el as HTMLElement
  const cs = getComputedStyle(h)
  h.style.overflow = 'hidden'
  h.style.height = h.scrollHeight + 'px'
  h.style.marginTop = cs.marginTop
  h.style.paddingTop = cs.paddingTop
  h.style.paddingBottom = cs.paddingBottom
  h.style.opacity = '1'
  requestAnimationFrame(() => {
    h.style.transition = 'height 0.28s ease, margin-top 0.28s ease, padding-top 0.28s ease, padding-bottom 0.28s ease, opacity 0.22s ease'
    h.style.height = '0'
    h.style.marginTop = '0'
    h.style.paddingTop = '0'
    h.style.paddingBottom = '0'
    h.style.opacity = '0'
    setTimeout(done, 280)
  })
}
</script>

<template>
  <div class="md:max-w-6xl md:mx-auto md:p-6">
    <RewardSystemUpdateBanner class="mb-4" />
    <Transition name="fade" mode="out-in">
      <div v-if="!loading || !isInitialLoad">
        <div class="bg-gray-100 dark:bg-gray-800 md:rounded-xl md:shadow-lg p-4 md:p-6">
          <div class="flex flex-wrap items-center gap-3 mb-6">
            <ChartBarIcon class="h-8 w-8 text-gray-700 dark:text-gray-300" />
            <h1 class="text-3xl font-bold text-gray-800 dark:text-gray-200">Dashboard</h1>
            <div class="w-full flex items-center gap-2 sm:w-auto sm:ml-auto">
              <router-link
                to="/cars"
                class="flex items-center gap-2 px-4 py-2 rounded-lg border border-gray-300 dark:border-gray-600 text-gray-700 dark:text-gray-300 text-sm font-medium shadow-[0_4px_0_0_#d1d5db] dark:shadow-[0_4px_0_0_#111827] hover:shadow-[0_2px_0_0_#d1d5db] dark:hover:shadow-[0_2px_0_0_#111827] hover:translate-y-0.5 active:shadow-none active:translate-y-1 transition-all duration-75">
                <TruckIcon class="w-4 h-4" />
                {{ t('dashboard.vehicles_btn') }}
              </router-link>
              <button v-if="stats && stats.totalCharges > 0"
                @click="scrollToLogs"
                class="flex items-center gap-2 px-4 py-2 rounded-lg bg-indigo-600 text-white text-sm font-medium shadow-[0_4px_0_0_#3730a3] hover:shadow-[0_2px_0_0_#3730a3] hover:translate-y-0.5 active:shadow-none active:translate-y-1 transition-all duration-75">
                <ListBulletIcon class="w-4 h-4" />
                <span class="min-[381px]:hidden">{{ t('dashboard.logs_title_short') }}</span>
                <span class="hidden min-[381px]:inline">{{ t('dashboard.logs_btn') }}</span>
                <ChevronRightIcon class="w-3.5 h-3.5 opacity-75" />
              </button>
            </div>
          </div>

          <!-- Import Hint Banner -->
          <div v-if="!importBannerDismissed" class="relative mb-6">
            <router-link
              to="/imports"
              class="flex items-center gap-3 bg-green-50 dark:bg-green-900/30 border border-green-200 dark:border-green-700 rounded-lg px-4 py-3 hover:bg-green-100 dark:hover:bg-green-900/40 transition group"
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
              class="absolute -top-2 -right-2 h-5 w-5 rounded-full bg-green-200 hover:bg-green-300 text-green-700 flex items-center justify-center transition"
              title="Hinweis ausblenden"
            >
              <XMarkIcon class="h-3 w-3" />
            </button>
          </div>

          <!-- Car card selector (all breakpoints) -->
          <div
            v-if="cars.length > 0"
            :class="[
              cars.length > 1
                ? 'sticky top-16 z-10 bg-white dark:bg-gray-800 -mx-4 px-4 md:-mx-6 md:px-6 py-3 mb-3 border-b border-gray-100 dark:border-gray-700 shadow-sm'
                : 'mb-6 rounded-xl md:w-fit',
              cars.length === 1 && anyVehicleCharging ? 'vehicle-charging-glow' : ''
            ]"
          >
            <div class="flex gap-3 overflow-x-auto pb-1 lg:flex-wrap lg:overflow-x-visible">
              <button
                v-for="car in cars"
                :key="car.id"
                @click="selectedCarId = car.id"
                :class="[
                  cars.length === 1
                    ? 'flex items-stretch rounded-xl border-2 text-left transition w-full md:w-auto overflow-hidden'
                    : 'flex items-stretch rounded-xl border-2 text-left transition flex-shrink-0 min-w-[200px] max-w-[280px] lg:flex-shrink lg:min-w-0 lg:max-w-none overflow-hidden',
                  selectedCarId === car.id
                    ? isVehicleCharging(car)
                      ? 'border-transparent bg-green-50 dark:bg-green-900/20 shadow-[0_4px_0_0_#16a34a] dark:shadow-[0_4px_0_0_#14532d] translate-y-[2px]'
                      : 'border-indigo-500 bg-indigo-50 dark:bg-indigo-900/30 shadow-[0_4px_0_0_#4338ca] translate-y-[2px]'
                    : isVehicleCharging(car)
                      ? 'border-transparent bg-white dark:bg-gray-700 shadow-[0_4px_0_0_#16a34a] dark:shadow-[0_4px_0_0_#14532d] active:shadow-none active:translate-y-1'
                      : 'border-gray-200 dark:border-gray-600 bg-white dark:bg-gray-700 shadow-[0_4px_0_0_#d1d5db] dark:shadow-[0_4px_0_0_#111827] hover:border-indigo-300 active:shadow-none active:translate-y-1',
                  cars.length > 1 && isVehicleCharging(car) ? 'ring-2 ring-green-400 dark:ring-green-500' : '',
                ]" style="transition: transform 0.075s ease, box-shadow 0.075s ease;">
                <div class="flex-shrink-0 w-24 self-stretch bg-gray-100 dark:bg-gray-600 flex items-center justify-center overflow-hidden">
                  <img
                    v-if="carImageUrls[car.id]"
                    :src="carImageUrls[car.id]"
                    :alt="car.model"
                    class="w-full h-full object-cover" />
                  <TruckIcon v-else class="w-8 h-8 text-gray-400" />
                </div>
                <div class="min-w-0 flex-1 px-4 py-3">
                  <!-- Mobile single-car: alles in einer Zeile -->
                  <div v-if="cars.length === 1" class="flex items-center gap-2 flex-wrap lg:hidden">
                    <span class="font-semibold text-gray-800 dark:text-gray-200">{{ enumToLabel(car.brand) }} {{ enumToLabel(car.model) }}</span>
                    <span v-if="car.trim" class="text-sm text-gray-500 dark:text-gray-400">{{ car.trim }}</span>
                    <LicensePlate v-if="car.licensePlate" :plate="car.licensePlate" />
                    <span v-if="car.isPrimary && cars.length > 1"
                      class="px-1.5 py-0.5 bg-green-100 dark:bg-green-900/40 text-green-700 dark:text-green-400 text-xs rounded-full border border-green-200 dark:border-green-700 font-medium">
                      {{ t('dashboard.active') }}
                    </span>
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
                        class="inline-flex items-center gap-1 px-1.5 py-0.5 bg-gray-100 text-gray-500 text-xs rounded-full font-medium border border-gray-200">
                        <span class="w-1.5 h-1.5 rounded-full bg-gray-400"></span>{{ t('dashboard.tesla_sleeping') }}
                      </span>
                    </template>
                    <span v-if="isSmartcarCharging(car)"
                      class="inline-flex items-center gap-1 px-1.5 py-0.5 bg-green-100 dark:bg-green-900/40 text-green-700 dark:text-green-400 text-xs rounded-full font-medium border border-green-200 dark:border-green-700">
                      <span class="w-1.5 h-1.5 rounded-full bg-green-500 dark:bg-green-400 animate-pulse"></span>{{ t('dashboard.smartcar_charging') }}
                    </span>
                    <span v-if="isWallboxCharging()"
                      class="inline-flex items-center gap-1 px-1.5 py-0.5 bg-green-100 dark:bg-green-900/40 text-green-700 dark:text-green-400 text-xs rounded-full font-medium border border-green-200 dark:border-green-700">
                      <span class="w-1.5 h-1.5 rounded-full bg-green-500 dark:bg-green-400 animate-pulse"></span>{{ t('dashboard.wallbox_charging') }}
                    </span>
                  </div>
                  <!-- Desktop oder mehrere Autos: zweizeiliges Layout -->
                  <div :class="cars.length === 1 ? 'hidden lg:block' : ''">
                    <div class="flex items-center gap-2 flex-wrap">
                      <span class="font-semibold text-gray-800 dark:text-gray-200">{{ enumToLabel(car.brand) }} {{ enumToLabel(car.model) }}</span>
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
            v-if="stats?.peerBenchmark && stats.peerBenchmark.peerAvgConsumptionKwhPer100km !== null"
            class="flex-1 min-w-0"
            :benchmark="stats.peerBenchmark"
            :effective-battery-kwh="selectedCar?.effectiveBatteryCapacityKwh ?? null"
            :car-display-name="selectedCar ? [enumToLabel(selectedCar.brand), enumToLabel(selectedCar.model), selectedCar.trim].filter(Boolean).join(' ') : ''"
          />

          <!-- Peer Benchmark Placeholder -->
          <div
            v-else-if="stats && carInfo?.batteryCapacityKwh && stats?.avgConsumptionKwhPer100km"
            class="flex-1 min-w-0 bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 shadow-sm flex flex-col items-center justify-center gap-3 px-6 py-8 text-center"
          >
            <UsersIcon class="w-8 h-8 text-gray-300 dark:text-gray-600" />
            <div>
              <p class="text-sm font-semibold text-gray-400 dark:text-gray-500">{{ t('dashboard.peer_no_data_title') }}</p>
              <p class="text-xs text-gray-400 dark:text-gray-500 mt-1 max-w-xs">{{ t('dashboard.peer_no_data_body') }}</p>
            </div>
          </div>

          </div><!-- Ende Reichweite + Peer Wrapper -->

          <!-- Filters (show if there are logs in any time range) -->
          <div v-if="selectedCarId && hasAnyLogs" class="mb-6 p-4 bg-white dark:bg-gray-700 rounded-lg border border-gray-200 dark:border-gray-600 shadow-sm">
            <!-- Mobile: two selects side by side -->
            <div class="md:hidden">
              <div class="flex gap-3">
                <div class="flex-1">
                  <label class="block text-xs font-medium text-gray-500 dark:text-gray-400 mb-1">{{ t('dashboard.time_range_label') }}</label>
                  <div class="relative">
                    <select v-model="selectedTimeRange"
                      class="block w-full appearance-none px-3 pr-8 py-2 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-md text-sm focus:ring-indigo-500 focus:border-indigo-500">
                      <option v-for="option in timeRangeOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
                    </select>
                    <ChevronDownIcon class="pointer-events-none absolute right-2.5 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400 dark:text-gray-500" />
                  </div>
                </div>
                <div class="flex-1">
                  <label class="block text-xs font-medium text-gray-500 dark:text-gray-400 mb-1">{{ t('dashboard.group_by_label') }}</label>
                  <div class="relative">
                    <select v-model="selectedGroupBy"
                      class="block w-full appearance-none px-3 pr-8 py-2 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-md text-sm focus:ring-indigo-500 focus:border-indigo-500">
                      <option v-for="opt in groupByOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
                    </select>
                    <ChevronDownIcon class="pointer-events-none absolute right-2.5 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400 dark:text-gray-500" />
                  </div>
                </div>
              </div>
              <!-- Mobile: custom date inputs -->
              <div v-if="selectedTimeRange === 'CUSTOM'" class="flex gap-3 mt-3">
                <div class="flex-1">
                  <label class="block text-xs font-medium text-gray-500 dark:text-gray-400 mb-1">{{ t('dashboard.time_custom_from') }}</label>
                  <div class="relative">
                    <input type="date" v-model="customStartDate" :max="customEndDate || undefined"
                      class="block w-full px-3 pr-9 py-2 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-md text-sm focus:ring-indigo-500 focus:border-indigo-500 [&::-webkit-calendar-picker-indicator]:opacity-0 [&::-webkit-calendar-picker-indicator]:absolute [&::-webkit-calendar-picker-indicator]:right-0 [&::-webkit-calendar-picker-indicator]:w-9 [&::-webkit-calendar-picker-indicator]:h-full [&::-webkit-calendar-picker-indicator]:cursor-pointer" />
                    <CalendarIcon class="pointer-events-none absolute right-2.5 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400 dark:text-gray-500" />
                  </div>
                </div>
                <div class="flex-1">
                  <label class="block text-xs font-medium text-gray-500 dark:text-gray-400 mb-1">{{ t('dashboard.time_custom_to') }}</label>
                  <div class="relative">
                    <input type="date" v-model="customEndDate" :min="customStartDate || undefined"
                      class="block w-full px-3 pr-9 py-2 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-md text-sm focus:ring-indigo-500 focus:border-indigo-500 [&::-webkit-calendar-picker-indicator]:opacity-0 [&::-webkit-calendar-picker-indicator]:absolute [&::-webkit-calendar-picker-indicator]:right-0 [&::-webkit-calendar-picker-indicator]:w-9 [&::-webkit-calendar-picker-indicator]:h-full [&::-webkit-calendar-picker-indicator]:cursor-pointer" />
                    <CalendarIcon class="pointer-events-none absolute right-2.5 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400 dark:text-gray-500" />
                  </div>
                </div>
              </div>
            </div>
            <!-- Desktop: buttons + select -->
            <div class="hidden md:block">
              <div class="flex gap-4 items-start md:items-center justify-between">
                <div class="flex-1">
                  <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">{{ t('dashboard.time_range_label') }}</label>
                  <div class="flex flex-wrap gap-2">
                    <button
                      v-for="option in timeRangeOptions"
                      :key="option.value"
                      @click="selectedTimeRange = option.value"
                      :class="[
                        'px-3 py-1.5 rounded-md text-sm font-medium transition-colors',
                        selectedTimeRange === option.value
                          ? 'bg-indigo-600 text-white translate-y-[2px] shadow-[0_2px_0_0_#3730a3] active:shadow-none active:translate-y-1 transition-all duration-75 cursor-pointer'
                          : 'bg-white dark:bg-gray-600 text-gray-700 dark:text-gray-200 border border-gray-300 dark:border-gray-500 shadow-[0_4px_0_0_#d1d5db] dark:shadow-[0_4px_0_0_#111827] active:shadow-none active:translate-y-1 transition-all duration-75 cursor-pointer'
                      ]">
                      {{ option.label }}
                    </button>
                  </div>
                </div>
                <div class="w-full md:w-auto">
                  <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">{{ t('dashboard.group_by_label') }}</label>
                  <div class="relative">
                    <select v-model="selectedGroupBy"
                      class="block w-full md:w-auto appearance-none px-4 pr-10 py-2 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-md shadow-sm focus:ring-indigo-500 focus:border-indigo-500">
                      <option v-for="opt in groupByOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
                    </select>
                    <ChevronDownIcon class="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400 dark:text-gray-500" />
                  </div>
                </div>
              </div>
              <!-- Desktop: custom date inputs -->
              <div v-if="selectedTimeRange === 'CUSTOM'" class="flex gap-3 mt-3">
                <div>
                  <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">{{ t('dashboard.time_custom_from') }}</label>
                  <div class="relative">
                    <input type="date" v-model="customStartDate" :max="customEndDate || undefined"
                      class="px-3 pr-9 py-2 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-md text-sm focus:ring-indigo-500 focus:border-indigo-500 [&::-webkit-calendar-picker-indicator]:opacity-0 [&::-webkit-calendar-picker-indicator]:absolute [&::-webkit-calendar-picker-indicator]:right-0 [&::-webkit-calendar-picker-indicator]:w-9 [&::-webkit-calendar-picker-indicator]:h-full [&::-webkit-calendar-picker-indicator]:cursor-pointer" />
                    <CalendarIcon class="pointer-events-none absolute right-2.5 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400 dark:text-gray-500" />
                  </div>
                </div>
                <div>
                  <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">{{ t('dashboard.time_custom_to') }}</label>
                  <div class="relative">
                    <input type="date" v-model="customEndDate" :min="customStartDate || undefined"
                      class="px-3 pr-9 py-2 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-md text-sm focus:ring-indigo-500 focus:border-indigo-500 [&::-webkit-calendar-picker-indicator]:opacity-0 [&::-webkit-calendar-picker-indicator]:absolute [&::-webkit-calendar-picker-indicator]:right-0 [&::-webkit-calendar-picker-indicator]:w-9 [&::-webkit-calendar-picker-indicator]:h-full [&::-webkit-calendar-picker-indicator]:cursor-pointer" />
                    <CalendarIcon class="pointer-events-none absolute right-2.5 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400 dark:text-gray-500" />
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div v-if="error" class="mb-4 p-4 bg-red-50 dark:bg-red-900/30 border border-red-200 dark:border-red-700 text-red-700 dark:text-red-300 rounded-md">{{ error }}</div>

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
                class="px-6 py-3 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 font-medium shadow-lg hover:shadow-xl transition flex items-center gap-2 mx-auto">
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

        <!-- Key Metrics -->
        <div :class="['grid grid-cols-2 md:grid-cols-3 gap-4 pb-6 mb-0', showThgBanner && isGerman ? 'lg:grid-cols-6' : 'lg:grid-cols-5']">
          <div class="bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 overflow-hidden shadow-sm">
            <div class="h-1 bg-amber-500"></div>
            <div class="p-4">
              <p class="text-xs text-gray-500 dark:text-gray-400 font-medium mb-1">{{ t('dashboard.metric_total_energy') }}</p>
              <p class="text-2xl font-bold text-gray-900 dark:text-gray-100">{{ stats.totalKwhCharged?.toFixed(1) ?? '–' }} kWh</p>
              <p class="text-sm font-medium text-gray-400 dark:text-gray-500 mt-0.5">{{ stats.totalCharges }} {{ t('dashboard.metric_charges') }}</p>
            </div>
          </div>
          <div class="bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 overflow-hidden shadow-sm">
            <div class="h-1 bg-indigo-500"></div>
            <div class="p-4">
              <p class="text-xs text-gray-500 dark:text-gray-400 font-medium mb-1">{{ t('dashboard.metric_total_cost') }}</p>
              <p class="text-2xl font-bold text-gray-900 dark:text-gray-100">{{ stats.totalCostEur != null ? formatCurrency(stats.totalCostEur) : '–' }}</p>
              <p class="text-sm font-medium text-gray-400 dark:text-gray-500 mt-0.5">Ø {{ stats.avgCostPerKwh != null ? formatCostPerKwh(stats.avgCostPerKwh) : '–' }}</p>
            </div>
          </div>
          <div v-if="stats.totalDistanceKm != null"
            class="bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 overflow-hidden shadow-sm">
            <div class="h-1 bg-green-500"></div>
            <div class="p-4">
              <p class="text-xs text-gray-500 dark:text-gray-400 font-medium mb-1">{{ t('dashboard.metric_total_distance') }}</p>
              <p class="text-2xl font-bold text-gray-900 dark:text-gray-100">{{ formatDistance(stats.totalDistanceKm) }}<span class="hidden sm:inline-block font-normal text-gray-400 dark:text-gray-500 text-lg ml-1">{{ t('dashboard.metric_driven') }}</span></p>
            </div>
          </div>
          <div v-if="stats.avgConsumptionKwhPer100km != null"
            class="bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 overflow-hidden shadow-sm">
            <div class="h-1 bg-red-500"></div>
            <div class="p-4">
              <p class="text-xs text-gray-500 dark:text-gray-400 font-medium mb-1">{{ t('dashboard.metric_avg_consumption') }}</p>
              <p class="text-2xl font-bold text-gray-900 dark:text-gray-100">{{ formatConsumption(stats.avgConsumptionKwhPer100km, { showUnit: false }) }}</p>
              <p class="text-sm font-medium text-gray-400 dark:text-gray-500 mt-0.5">{{ consumptionUnitLabel() }}</p>
              <router-link to="/consumption-methodology" class="mt-2 inline-flex items-center gap-1 text-xs font-medium text-indigo-600 dark:text-indigo-400 underline underline-offset-2 hover:text-indigo-800 dark:hover:text-indigo-300 transition-colors">
                {{ t('dashboard.metric_consumption_methodology_link') }}
                <ChevronRightIcon class="w-3 h-3 flex-shrink-0" />
              </router-link>
              <p v-if="stats.estimatedConsumptionCount > 0" class="text-xs text-red-500 mt-2 italic">
                {{ t('dashboard.metric_estimated', { n: stats.estimatedConsumptionCount }) }}
              </p>
            </div>
          </div>
          <div v-if="stats.totalDistanceKm != null && stats.totalCostEur != null && stats.totalDistanceKm > 0"
            class="bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 overflow-hidden shadow-sm">
            <div class="h-1 bg-pink-500"></div>
            <div class="p-4">
              <p class="text-xs text-gray-500 dark:text-gray-400 font-medium mb-1">{{ t('dashboard.metric_avg_cost') }}</p>
              <p class="text-2xl font-bold text-gray-900 dark:text-gray-100">{{ formatCostPerDistance(stats.totalCostEur / stats.totalDistanceKm * 100) }}</p>
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
            class="relative bg-green-50 dark:bg-green-900/20 rounded-lg border border-green-200 dark:border-green-700 overflow-hidden shadow-[0_4px_0_0_#bbf7d0] dark:shadow-[0_4px_0_0_#14532d] hover:shadow-[0_2px_0_0_#bbf7d0] dark:hover:shadow-[0_2px_0_0_#14532d] hover:translate-y-0.5 active:shadow-none active:translate-y-1 transition-all duration-75 group cursor-pointer"
          >
            <div class="h-1 bg-green-500"></div>
            <div class="p-4 pr-8">
              <p class="text-xs text-gray-700 dark:text-gray-200 font-medium mb-1">THG-Prämie schon beantragt?</p>
              <p class="text-xs text-gray-500 dark:text-gray-400 leading-snug mt-1">Falls nicht, kannst du das hier tun und gleichzeitig den Betrieb der Seite unterstützen.</p>
            </div>
            <button
              @click.stop="dismissThgBanner"
              class="absolute top-4 right-2 h-5 w-5 rounded-full bg-gray-200 dark:bg-gray-600 hover:bg-gray-300 dark:hover:bg-gray-500 text-gray-500 dark:text-gray-300 flex items-center justify-center transition"
              title="Hinweis ausblenden"
            >
              <XMarkIcon class="h-3 w-3" />
            </button>
            <span class="absolute bottom-1 right-3 text-[10px] text-gray-300 dark:text-gray-600">Affiliate-Link</span>
          </div>
        </div>

        <!-- Log List -->
        <div ref="logsSection" class="border-t border-gray-100 dark:border-gray-700 pt-3 scroll-mt-4 pb-6">
          <div class="flex items-center justify-between mb-3">
            <h2 class="text-xl font-semibold text-gray-800 dark:text-gray-200">{{ t('dashboard.logs_title') }}</h2>
          </div>

          <!-- Consumption info accordion -->
          <ConsumptionInfoBox :min-trips="5" class="mb-4" />

          <!-- Implausible logs banner (position 2: under ConsumptionInfoBox) -->
          <div v-if="implausibleCount > 0 && !implausibleBannerDismissed"
            class="w-full mb-4 flex items-center gap-3 px-4 py-3 rounded-lg bg-amber-200 dark:bg-amber-500/20 border border-amber-300 dark:border-amber-600/50">
            <button
              @click="showImplausibleModal = true"
              class="flex-1 flex items-center justify-between gap-3 text-left">
              <div class="flex items-center gap-2">
                <ExclamationTriangleIcon class="h-4 w-4 text-amber-600 dark:text-amber-400 shrink-0" />
                <span class="text-sm font-medium text-amber-800 dark:text-amber-300">
                  {{ t('dashboard.implausible_banner', { n: implausibleCount, noun: implausibleCount === 1 ? t('dashboard.implausible_entry') : t('dashboard.implausible_entries') }) }}
                </span>
              </div>
              <span class="text-xs text-amber-700 dark:text-amber-400 font-medium shrink-0">{{ t('dashboard.implausible_check') }}</span>
            </button>
            <button
              @click="dismissImplausibleBanner"
              class="shrink-0 p-1 rounded hover:bg-amber-300/50 dark:hover:bg-amber-600/30 transition-colors"
              :title="t('dashboard.implausible_dismiss')">
              <XMarkIcon class="h-4 w-4 text-amber-700 dark:text-amber-400" />
            </button>
          </div>

          <div v-if="!logsLoading && logsPage > 0" class="text-sm text-gray-400 mb-2 text-right">{{ t('dashboard.logs_page', { n: logsPage + 1 }) }}</div>

          <Transition enter-active-class="transition duration-200 ease-out" enter-from-class="opacity-0 -translate-y-1" enter-to-class="opacity-100 translate-y-0" leave-active-class="transition duration-150 ease-in" leave-from-class="opacity-100" leave-to-class="opacity-0">
            <div v-if="reassignSuccessMessage" class="mb-2 px-3 py-2 rounded-lg bg-green-50 dark:bg-green-900/30 border border-green-200 dark:border-green-700 text-sm text-green-700 dark:text-green-300 flex items-center gap-2">
              <ArrowsRightLeftIcon class="w-4 h-4 flex-shrink-0" />
              {{ reassignSuccessMessage }}
            </div>
          </Transition>

          <div class="space-y-2">
            <div v-if="logsLoading && !hasAnyLogs" class="py-8 text-center text-gray-400 text-sm">{{ t('dashboard.loading') }}</div>
            <template v-else-if="!hasAnyLogs">
              <p class="py-8 text-center text-gray-400 text-sm">{{ t('dashboard.no_logs_empty') }}</p>
            </template>
            <template v-else>
              <template v-for="entry in mergedLogFeed" :key="entry.id">

              <!-- Phantom drain indicator: energy lost while parked before this entry -->
              <div v-if="entry._phantomDrain && isAdmin && !(entry._isTrip && collapsedTripGroups.has(entry._tripGroupId ?? ''))" class="flex items-center gap-2 px-4 mt-0.5 mb-2">
                <div class="flex-1 h-px bg-gray-200 dark:bg-gray-600" />
                <span class="inline-flex items-center gap-1 px-2 py-0.5 rounded-full border border-amber-300 dark:border-amber-600 bg-amber-50 dark:bg-amber-900/20 text-xs text-amber-600 dark:text-amber-400 whitespace-nowrap">
                  <BoltIcon class="w-3 h-3" />
                  {{ entry._phantomDrain.kwh.toFixed(2) }} kWh
                  <template v-if="selectedCar?.effectiveBatteryCapacityKwh">
                    ({{ (entry._phantomDrain.kwh / selectedCar.effectiveBatteryCapacityKwh * 100).toFixed(1) }}%)
                  </template>
                  Standverlust
                </span>
                <div class="flex-1 h-px bg-gray-200 dark:bg-gray-600" />
              </div>

              <!-- Inline add-trip form - appears above the triggering entry -->
              <Transition :css="false" @enter="onTripFormEnter" @after-enter="onTripFormAfterEnter" @leave="onTripFormLeave">
              <div v-if="canAccessTrips && addingTripAfterId === entry.id" class="ml-2 mr-2 mt-1 p-3 rounded-lg shadow-sm ring-1 ring-black/5 dark:ring-white/10 border-l-4 border-l-emerald-400 dark:border-l-emerald-500 border-r-4 border-r-emerald-400 dark:border-r-emerald-500 bg-white dark:bg-gray-700 space-y-3">
                <div class="flex items-center justify-between gap-2">
                  <span class="text-sm font-medium text-emerald-800 dark:text-emerald-300 flex items-center gap-1.5">
                    <PlusIcon class="w-4 h-4" />{{ t('dashboard.trip_add') }}
                  </span>
                  <div class="flex gap-1">
                    <button @click="saveNewTrip()" :disabled="tripSaving"
                      class="px-3 py-1 text-xs font-medium bg-emerald-600 hover:bg-emerald-700 text-white rounded-md disabled:opacity-50 transition">
                      {{ t('dashboard.trip_save') }}
                    </button>
                    <button @click="cancelTripEdit()"
                      class="px-3 py-1 text-xs font-medium bg-white dark:bg-gray-700 border border-gray-200 dark:border-gray-600 text-gray-600 dark:text-gray-300 rounded-md hover:bg-gray-50 dark:hover:bg-gray-600 transition">
                      {{ t('dashboard.trip_cancel') }}
                    </button>
                  </div>
                </div>
                <p v-if="tripError" class="text-xs text-red-500 -mb-1">{{ tripError }}</p>
                <div class="grid grid-cols-1 sm:grid-cols-2 gap-2">
                  <div>
                    <label class="block text-xs text-gray-500 dark:text-gray-400 mb-1">{{ t('dashboard.trip_started_at') }}</label>
                    <input v-model="tripForm.tripStartedAt" type="datetime-local"
                      class="w-full px-2 py-1.5 text-sm border border-gray-200 dark:border-gray-600 rounded-md bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-1 focus:ring-emerald-400" />
                  </div>
                  <div>
                    <label class="block text-xs text-gray-500 dark:text-gray-400 mb-1">{{ t('dashboard.trip_ended_at') }}</label>
                    <input v-model="tripForm.tripEndedAt" type="datetime-local"
                      class="w-full px-2 py-1.5 text-sm border border-gray-200 dark:border-gray-600 rounded-md bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-1 focus:ring-emerald-400" />
                  </div>
                </div>
                <div class="grid grid-cols-1 sm:grid-cols-2 gap-2">
                  <div>
                    <label class="block text-xs text-gray-500 dark:text-gray-400 mb-1">{{ t('dashboard.trip_distance', { unit: distanceUnitLabel() }) }}</label>
                    <input v-model="tripForm.distanceKm" type="number" min="0" max="9999" step="0.1"
                      class="w-full px-2 py-1.5 text-sm border border-gray-200 dark:border-gray-600 rounded-md bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-1 focus:ring-emerald-400" />
                  </div>
                  <div>
                    <label class="block text-xs text-gray-500 dark:text-gray-400 mb-1">{{ t('dashboard.trip_route_type') }}</label>
                    <div class="flex gap-1">
                      <button v-for="rt in ['CITY','COMBINED','HIGHWAY']" :key="rt"
                        @click="tripForm.routeType = rt"
                        :class="['flex-1 px-1 py-1.5 text-xs rounded-md border transition',
                                 tripForm.routeType === rt
                                   ? 'bg-emerald-600 border-emerald-600 text-white'
                                   : 'bg-white dark:bg-gray-800 border-gray-200 dark:border-gray-600 text-gray-600 dark:text-gray-400 hover:border-emerald-400']">
                        {{ t('dashboard.trip_route_' + rt.toLowerCase()) }}
                      </button>
                    </div>
                  </div>
                </div>
              </div>
              </Transition>

              <template v-if="entry._isTrip && canAccessTrips">
                <!-- Trip group collapse header -->
                <div v-if="entry._tripGroupSize > 1 && entry._tripGroupIndex === 0"
                     @click="toggleTripGroup(entry._tripGroupId)"
                     class="mx-2 mb-1 flex items-center justify-between px-3 py-1.5 rounded-md bg-emerald-50 dark:bg-emerald-900/20 border border-emerald-200 dark:border-emerald-800 cursor-pointer select-none hover:bg-emerald-100 dark:hover:bg-emerald-900/30 transition-colors">
                  <div class="w-4 shrink-0"></div>
                  <span class="flex-1 text-xs font-medium text-emerald-700 dark:text-emerald-400 flex items-center justify-center gap-1.5">
                    <TruckIcon class="w-3.5 h-3.5" />
                    {{ t('dashboard.trip_group_count', { count: entry._tripGroupSize }) }}
                    <span v-if="entry._tripGroupTotalKm" class="font-normal text-emerald-600 dark:text-emerald-500">&middot; {{ formatDistance(entry._tripGroupTotalKm) }}</span>
                    <span v-if="entry._tripGroupDateRange" class="font-normal text-emerald-600 dark:text-emerald-500">&middot; {{ entry._tripGroupDateRange }}</span>
                  </span>
                  <ChevronUpIcon v-if="!collapsedTripGroups.has(entry._tripGroupId)" class="w-4 h-4 text-emerald-500 shrink-0" />
                  <ChevronDownIcon v-else class="w-4 h-4 text-emerald-500 shrink-0" />
                </div>
                <!-- Trip display mode -->
                <Transition :css="false" @enter="onTripFormEnter" @after-enter="onTripFormAfterEnter" @leave="onTripFormLeave">
                  <div v-if="editingTripId !== entry.id && deletingTripId !== entry.id && !collapsedTripGroups.has(entry._tripGroupId ?? '')" class="ml-2 mr-2 p-3 rounded-lg shadow-sm ring-1 ring-black/5 dark:ring-white/10 border-l-4 border-l-emerald-400 dark:border-l-emerald-500 border-r-4 border-r-emerald-400 dark:border-r-emerald-500 bg-white dark:bg-gray-700 space-y-2">
                    <!-- Zeile 1: Distanz, Verbrauch, Temperatur (rechts), Aktionen -->
                    <div class="flex items-center justify-between gap-2">
                      <div class="flex items-center gap-2 min-w-0 overflow-hidden">
                        <TruckIcon :class="['w-4 h-4 flex-shrink-0',
                          isAdmin && entry.dataSource === 'TESLA_LIVE'     ? 'text-red-500 dark:text-red-400' :
                          isAdmin && entry.dataSource === 'SMARTCAR_LIVE'  ? 'text-blue-500 dark:text-blue-400' :
                          'text-emerald-600 dark:text-emerald-400']" />
                        <span v-if="entry.distanceKm != null" class="font-semibold text-emerald-700 dark:text-emerald-400 whitespace-nowrap flex-shrink-0">{{ formatDistance(entry.distanceKm) }}</span>
                        <span v-if="tripConsumption(entry)"
                          class="inline-flex items-center text-[13px] text-gray-600 dark:text-gray-300 whitespace-nowrap flex-shrink-0">
                          {{ tripConsumption(entry)!.estimated ? '~' : '' }}{{ formatConsumption(tripConsumption(entry)!.kwhPer100km) }}
                        </span>
                      </div>
                      <div class="flex items-center gap-1.5 flex-shrink-0">
                        <span v-if="entry.outsideTempCelsius != null"
                          :class="['inline-flex items-center gap-0.5 px-2 py-0.5 border rounded-full text-xs whitespace-nowrap',
                                   tempBadgeClass(entry.outsideTempCelsius)]">
                          <SunIcon class="w-3 h-3" />{{ entry.outsideTempCelsius }}°C
                        </span>
                      <div class="flex items-center gap-1 flex-shrink-0">
                        <template v-if="entry.dataSource !== 'USER_CREATED'">
                          <button @click="toggleRating(entry.id, 'positive', entry.feedback)"
                            class="p-2 md:p-1 rounded transition"
                            :class="effectiveRating(entry.id, entry.feedback) === 'positive' ? 'text-emerald-500' : 'text-gray-400 hover:text-emerald-500 hover:bg-emerald-50 dark:hover:bg-emerald-900/30'"
                            :title="t('dashboard.trip_feedback_positive')">
                            <HandThumbUpIcon class="w-3.5 h-3.5" />
                          </button>
                          <button @click="toggleRating(entry.id, 'negative', entry.feedback)"
                            class="p-2 md:p-1 rounded transition"
                            :class="effectiveRating(entry.id, entry.feedback) === 'negative' ? 'text-red-500' : 'text-gray-400 hover:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20'"
                            :title="t('dashboard.trip_feedback_negative')">
                            <HandThumbDownIcon class="w-3.5 h-3.5" />
                          </button>
                        </template>
                        <button @click="startAddTrip(entry.id, entry.tripStartedAt)"
                          class="p-2 md:p-1 rounded text-gray-400 hover:text-emerald-600 dark:hover:text-emerald-400 hover:bg-emerald-50 dark:hover:bg-emerald-900/30 transition"
                          :title="t('dashboard.trip_add')">
                          <PlusIcon class="w-3.5 h-3.5" />
                        </button>
                        <button @click="startEditTrip(entry)"
                          class="p-2 md:p-1 rounded text-gray-400 hover:text-emerald-600 dark:hover:text-emerald-400 hover:bg-emerald-50 dark:hover:bg-emerald-900/30 transition"
                          :title="t('dashboard.trip_edit')">
                          <PencilSquareIcon class="w-3.5 h-3.5" />
                        </button>
                        <button @click="handleDeleteTrip(entry.id)"
                          class="p-2 md:p-1 rounded text-gray-400 hover:text-red-500 hover:bg-red-50 dark:hover:bg-red-900/20 transition">
                          <TrashIcon class="w-3.5 h-3.5" />
                        </button>
                      </div>
                      </div><!-- end right: temp + buttons -->
                    </div>
                    <!-- Zeile 2: Zeitraum + SoC-Änderung -->
                    <div class="flex items-center gap-2 text-[13px] text-gray-500 dark:text-gray-400">
                      <span class="inline-flex items-center gap-1 whitespace-nowrap">
                        <ClockIcon class="w-3 h-3" />{{ formatTripTimeRange(entry.tripStartedAt, entry.tripEndedAt) }}
                      </span>
                      <span v-if="entry.socStart != null && entry.socEnd != null"
                        class="inline-flex items-center gap-1 whitespace-nowrap">
                        <Battery0Icon class="w-3 h-3" />{{ entry.socStart }}% → {{ entry.socEnd }}%
                      </span>
                    </div>
                    <div class="flex flex-wrap gap-1.5">
                      <span v-if="entry.routeType"
                        class="inline-flex items-center px-2 py-0.5 bg-gray-50 dark:bg-gray-600 border border-gray-200 dark:border-gray-500 rounded-full text-xs text-gray-500 dark:text-gray-400 whitespace-nowrap">
                        {{ t('dashboard.trip_route_' + entry.routeType.toLowerCase()) }}
                      </span>
                      <span v-if="entry.dataSource === 'USER_CREATED'"
                        class="inline-flex items-center px-2 py-0.5 bg-gray-50 dark:bg-gray-600 border border-gray-200 dark:border-gray-500 rounded-full text-xs text-gray-400 whitespace-nowrap">
                        {{ t('dashboard.trip_manual') }}
                      </span>
                    </div>
                    <!-- Inline feedback panel -->
                    <div v-if="feedbackOpenId === entry.id" class="pt-1 space-y-2 border-t border-gray-100 dark:border-gray-600">
                      <div class="flex flex-wrap gap-1">
                        <button v-for="tag in FEEDBACK_TAGS" :key="tag"
                          @click="toggleFeedbackTag(tag)"
                          :class="['px-2 py-0.5 text-xs rounded-full border transition',
                            feedbackTags.includes(tag)
                              ? 'bg-red-100 border-red-300 text-red-700 dark:bg-red-900/30 dark:border-red-700 dark:text-red-400'
                              : 'bg-white dark:bg-gray-800 border-gray-200 dark:border-gray-600 text-gray-500 dark:text-gray-400 hover:border-red-300']">
                          {{ t('dashboard.trip_feedback_tag_' + tag) }}
                        </button>
                      </div>
                      <textarea v-model="feedbackComment" rows="2"
                        :placeholder="t('dashboard.trip_feedback_comment')"
                        class="w-full px-2 py-1.5 text-xs border border-gray-200 dark:border-gray-600 rounded-md bg-white dark:bg-gray-800 text-gray-700 dark:text-gray-300 placeholder-gray-400 focus:outline-none focus:ring-1 focus:ring-red-400 resize-none" />
                      <div class="flex justify-end">
                        <button @click="sendNegativeFeedback(entry.id)"
                          class="px-3 py-1 text-xs font-medium bg-red-500 hover:bg-red-600 text-white rounded-md transition">
                          {{ t('dashboard.trip_feedback_send') }}
                        </button>
                      </div>
                    </div>
                  </div>
                </Transition>
                <!-- Trip inline edit mode -->
                <Transition :css="false" @enter="onTripFormEnter" @after-enter="onTripFormAfterEnter" @leave="onTripFormLeave">
                <div v-if="editingTripId === entry.id && !collapsedTripGroups.has(entry._tripGroupId ?? '')" class="ml-2 mr-2 p-3 rounded-lg shadow-sm ring-1 ring-black/5 dark:ring-white/10 border-l-4 border-l-emerald-500 dark:border-l-emerald-400 bg-white dark:bg-gray-700 space-y-3">
                  <div class="flex items-center justify-between gap-2">
                    <span class="text-sm font-medium text-emerald-800 dark:text-emerald-300">{{ t('dashboard.trip_edit') }}</span>
                    <div class="flex gap-1">
                      <button @click="saveTripEdit(entry.id)" :disabled="tripSaving"
                        class="px-3 py-1 text-xs font-medium bg-emerald-600 hover:bg-emerald-700 text-white rounded-md disabled:opacity-50 transition">
                        {{ t('dashboard.trip_save') }}
                      </button>
                      <button @click="cancelTripEditFull()"
                        class="px-3 py-1 text-xs font-medium bg-white dark:bg-gray-700 border border-gray-200 dark:border-gray-600 text-gray-600 dark:text-gray-300 rounded-md hover:bg-gray-50 dark:hover:bg-gray-600 transition">
                        {{ t('dashboard.trip_cancel') }}
                      </button>
                    </div>
                  </div>
                  <p v-if="tripError" class="text-xs text-red-500 -mb-1">{{ tripError }}</p>
                  <div class="grid grid-cols-1 sm:grid-cols-2 gap-2">
                    <div>
                      <label class="block text-xs text-gray-500 dark:text-gray-400 mb-1">{{ t('dashboard.trip_started_at') }}</label>
                      <input v-model="tripForm.tripStartedAt" type="datetime-local"
                        class="w-full px-2 py-1.5 text-sm border border-gray-200 dark:border-gray-600 rounded-md bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-1 focus:ring-emerald-400" />
                    </div>
                    <div>
                      <label class="block text-xs text-gray-500 dark:text-gray-400 mb-1">{{ t('dashboard.trip_ended_at') }}</label>
                      <input v-model="tripForm.tripEndedAt" type="datetime-local"
                        class="w-full px-2 py-1.5 text-sm border border-gray-200 dark:border-gray-600 rounded-md bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-1 focus:ring-emerald-400" />
                    </div>
                  </div>
                  <div class="grid grid-cols-1 sm:grid-cols-2 gap-2">
                    <div>
                      <label class="block text-xs text-gray-500 dark:text-gray-400 mb-1">{{ t('dashboard.trip_distance', { unit: distanceUnitLabel() }) }}</label>
                      <input v-model="tripForm.distanceKm" type="number" min="0" max="9999" step="0.1"
                        class="w-full px-2 py-1.5 text-sm border border-gray-200 dark:border-gray-600 rounded-md bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-1 focus:ring-emerald-400" />
                    </div>
                    <div>
                      <label class="block text-xs text-gray-500 dark:text-gray-400 mb-1">{{ t('dashboard.trip_route_type') }}</label>
                      <div class="flex gap-1">
                        <button v-for="rt in ['CITY','COMBINED','HIGHWAY']" :key="rt"
                          @click="tripForm.routeType = rt"
                          :class="['flex-1 px-1 py-1.5 text-xs rounded-md border transition',
                                   tripForm.routeType === rt
                                     ? 'bg-emerald-600 border-emerald-600 text-white'
                                     : 'bg-white dark:bg-gray-800 border-gray-200 dark:border-gray-600 text-gray-600 dark:text-gray-400 hover:border-emerald-400']">
                          {{ t('dashboard.trip_route_' + rt.toLowerCase()) }}
                        </button>
                      </div>
                    </div>
                  </div>
                  <div class="grid grid-cols-1 sm:grid-cols-2 gap-2">
                    <div>
                      <label class="block text-xs text-gray-500 dark:text-gray-400 mb-1">{{ t('dashboard.trip_soc_start') }}</label>
                      <input v-model="tripForm.socStart" type="number" min="0" max="100"
                        class="w-full px-2 py-1.5 text-sm border border-gray-200 dark:border-gray-600 rounded-md bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-1 focus:ring-emerald-400" />
                    </div>
                    <div>
                      <label class="block text-xs text-gray-500 dark:text-gray-400 mb-1">{{ t('dashboard.trip_soc_end') }}</label>
                      <input v-model="tripForm.socEnd" type="number" min="0" max="100"
                        class="w-full px-2 py-1.5 text-sm border border-gray-200 dark:border-gray-600 rounded-md bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-1 focus:ring-emerald-400" />
                    </div>
                  </div>
                  <!-- Feedback + merge trigger: same row on sm+ -->
                  <div v-if="entry.dataSource !== 'USER_CREATED' || (previousTripMap[entry.id] && mergePreviewForTripId !== entry.id)"
                       class="flex flex-col sm:flex-row items-center sm:justify-between gap-1 pt-1 border-t border-emerald-100 dark:border-emerald-800">
                    <div v-if="entry.dataSource !== 'USER_CREATED'" class="flex items-center gap-2">
                      <span class="text-xs text-gray-400 dark:text-gray-500">{{ t('dashboard.trip_feedback_label') }}</span>
                      <button @click="tripForm.feedback = 'positive'"
                        :class="['p-1 rounded transition', tripForm.feedback?.startsWith('positive')
                          ? 'text-emerald-500 bg-emerald-50 dark:bg-emerald-900/30'
                          : 'text-gray-400 hover:text-emerald-500 hover:bg-emerald-50 dark:hover:bg-emerald-900/30']">
                        <HandThumbUpIcon class="w-4 h-4" />
                      </button>
                      <button @click="tripForm.feedback = 'negative'"
                        :class="['p-1 rounded transition', tripForm.feedback?.startsWith('negative')
                          ? 'text-red-500 bg-red-50 dark:bg-red-900/20'
                          : 'text-gray-400 hover:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20']">
                        <HandThumbDownIcon class="w-4 h-4" />
                      </button>
                      <input v-if="tripForm.feedback?.startsWith('negative')"
                        v-model="tripForm.feedback"
                        type="text" maxlength="200"
                        :placeholder="t('dashboard.trip_feedback_comment')"
                        class="flex-1 px-2 py-1 text-xs border border-gray-200 dark:border-gray-600 rounded-md bg-white dark:bg-gray-800 text-gray-700 dark:text-gray-300 placeholder-gray-400 focus:outline-none focus:ring-1 focus:ring-red-400" />
                    </div>
                    <button v-if="previousTripMap[entry.id] && mergePreviewForTripId !== entry.id"
                      @click="mergePreviewForTripId = entry.id"
                      class="flex items-center gap-1 text-xs text-gray-400 dark:text-gray-500 hover:text-orange-500 dark:hover:text-orange-400 transition sm:ml-auto">
                      <ArrowsRightLeftIcon class="w-3.5 h-3.5" />
                      {{ t('dashboard.trip_merge_button') }}
                    </button>
                  </div>
                  <!-- Merge with previous trip - preview -->
                  <template v-if="previousTripMap[entry.id]">
                    <div v-if="mergePreviewForTripId === entry.id"
                         class="pt-2 border-t border-orange-200 dark:border-orange-800 space-y-2">
                      <p class="text-xs font-medium text-orange-700 dark:text-orange-400">{{ t('dashboard.trip_merge_title') }}</p>
                      <div class="text-xs text-gray-500 dark:text-gray-400 bg-gray-50 dark:bg-gray-800 rounded px-2 py-1.5 space-y-0.5">
                        <div class="flex justify-between gap-2">
                          <span class="shrink-0">{{ t('dashboard.trip_merge_previous') }}</span>
                          <span class="text-right">{{ formatTripTimeRange(previousTripMap[entry.id].tripStartedAt, previousTripMap[entry.id].tripEndedAt) }}, {{ previousTripMap[entry.id].distanceKm ? formatDistance(previousTripMap[entry.id].distanceKm) : '?' }}</span>
                        </div>
                        <div class="flex justify-between gap-2">
                          <span class="shrink-0">{{ t('dashboard.trip_merge_this') }}</span>
                          <span class="text-right">{{ formatTripTimeRange(entry.tripStartedAt, entry.tripEndedAt) }}, {{ entry.distanceKm ? formatDistance(entry.distanceKm) : '?' }}</span>
                        </div>
                        <div class="flex justify-between gap-2 font-medium text-orange-700 dark:text-orange-400 pt-0.5 border-t border-gray-200 dark:border-gray-700">
                          <span class="shrink-0">{{ t('dashboard.trip_merge_result') }}</span>
                          <span class="text-right">{{ formatTripTimeRange(previousTripMap[entry.id].tripStartedAt, entry.tripEndedAt) }}, ~{{ formatDistance((previousTripMap[entry.id].distanceKm || 0) + (entry.distanceKm || 0)) }}</span>
                        </div>
                      </div>
                      <p v-if="tripMergeError" class="text-xs text-red-500">{{ tripMergeError }}</p>
                      <div class="flex gap-2">
                        <button @click="doMergeTrip(entry.id, previousTripMap[entry.id].id)"
                          :disabled="tripMerging"
                          class="px-3 py-1 text-xs font-medium bg-orange-500 hover:bg-orange-600 text-white rounded-md disabled:opacity-50 transition">
                          {{ t('dashboard.trip_merge_confirm') }}
                        </button>
                        <button @click="mergePreviewForTripId = null"
                          class="px-3 py-1 text-xs font-medium bg-white dark:bg-gray-700 border border-gray-200 dark:border-gray-600 text-gray-600 dark:text-gray-300 rounded-md hover:bg-gray-50 dark:hover:bg-gray-600 transition">
                          {{ t('dashboard.trip_cancel') }}
                        </button>
                      </div>
                    </div>
                  </template>
                </div>
                </Transition>
              </template>

              <!-- CHARGE ENTRY -->
              <div v-else :class="entry._isLadegruppe ? 'pb-[5px]' : ''">
              <div
                :class="['p-3 border rounded-lg space-y-2',
                         entry._isLadegruppe
                           ? 'bg-white dark:bg-gray-700 border-blue-200 dark:border-blue-800 cursor-pointer shadow-[0_5px_0_0_#bfdbfe] dark:shadow-[0_5px_0_0_#1e3a5f] hover:shadow-[0_2px_0_0_#bfdbfe] dark:hover:shadow-[0_2px_0_0_#1e3a5f] hover:translate-y-[3px] active:shadow-none active:translate-y-[5px] transition-all duration-75'
                           : 'bg-white dark:bg-gray-700 border-gray-200 dark:border-gray-600 shadow-sm']"
                @click="entry._isLadegruppe ? toggleLadegruppe(entry.id) : null">

                <!-- LADEGRUPPE HEADER -->
                <template v-if="entry._isLadegruppe">
                  <div class="flex items-center justify-between gap-2">
                    <div class="flex items-center gap-2 min-w-0">
                      <BoltIcon class="w-4 h-4 text-indigo-600 dark:text-indigo-300 flex-shrink-0" />
                      <span class="font-semibold text-indigo-700 dark:text-indigo-300 whitespace-nowrap">{{ entry._totalKwh }} kWh</span>
                      <span class="text-xs text-gray-400 whitespace-nowrap">{{ entry._dateRangeLabel }}</span>
                      <span class="inline-flex items-center gap-0.5 px-1.5 py-0.5 rounded text-xs font-medium bg-blue-50 dark:bg-blue-900/30 text-blue-700 dark:text-blue-400 border border-blue-200 dark:border-blue-700">
                        {{ t('dashboard.charge_group') }}
                      </span>
<span v-if="sourceInfo(entry._commonDataSource)"
                        :class="['inline-flex items-center gap-0.5 px-1.5 py-0.5 rounded text-xs font-medium whitespace-nowrap',
                                 sourceInfo(entry._commonDataSource)!.classes]">
                        <component :is="sourceInfo(entry._commonDataSource)!.icon" class="w-3 h-3" />
                        {{ sourceInfo(entry._commonDataSource)!.label }}
                      </span>
                    </div>
                    <div class="flex items-center gap-1.5 flex-shrink-0">
                      <button class="p-1 rounded text-blue-400 dark:text-blue-500">
                        <ChevronDownIcon v-if="!expandedGroups.has(entry.id)" class="w-4 h-4" />
                        <ChevronUpIcon v-else class="w-4 h-4" />
                      </button>
                    </div>
                  </div>
                  <!-- Ladegruppe badges -->
                  <div class="flex flex-wrap gap-1.5">
                    <span v-if="entry._totalCostEur != null && entry._totalKwh"
                      :class="['inline-flex items-center px-2 py-0.5 border text-xs rounded-full font-medium whitespace-nowrap cursor-pointer transition-all duration-75',
                               showCostAbsolute
                                 ? 'bg-gray-50 dark:bg-gray-700 border-gray-200 dark:border-gray-600 text-gray-600 dark:text-gray-300 shadow-[0_4px_0_0_#d1d5db] dark:shadow-[0_4px_0_0_#111827] hover:shadow-[0_2px_0_0_#d1d5db] dark:hover:shadow-[0_2px_0_0_#111827] hover:translate-y-0.5 active:shadow-none active:translate-y-1'
                                 : [(costBadgeClass(entry._totalCostEur, entry._totalKwh) ?? 'bg-green-50 border-green-200 text-green-700'), 'shadow-[0_4px_0_0_#d1d5db] dark:shadow-[0_4px_0_0_#111827] hover:shadow-[0_2px_0_0_#d1d5db] dark:hover:shadow-[0_2px_0_0_#111827] hover:translate-y-0.5 active:shadow-none active:translate-y-1'].join(' ')]"
                      @click.stop="showCostAbsolute = !showCostAbsolute"
                      @mousedown.stop>
                      <template v-if="showCostAbsolute">{{ formatCurrency(entry._totalCostEur) }}</template>
                      <template v-else>{{ formatCostPerKwh(entry._totalCostEur / entry._costKwh) }}</template>
                    </span>
                    <span
                      v-if="entry.distanceSinceLastChargeKm != null || entry.odometerKm"
                      class="inline-flex items-center gap-1 px-2 py-0.5 bg-gray-50 dark:bg-gray-700 border border-gray-200 dark:border-gray-600 rounded-full text-xs text-gray-600 dark:text-gray-300 whitespace-nowrap"
                      :class="entry.distanceSinceLastChargeKm != null && entry.odometerKm ? 'cursor-pointer shadow-[0_4px_0_0_#d1d5db] dark:shadow-[0_4px_0_0_#111827] active:shadow-none active:translate-y-1 transition-all duration-75 cursor-pointer' : ''"
                      @click.stop="toggleOdometerDisplay(entry.distanceSinceLastChargeKm, entry.odometerKm)"
                      @mousedown.stop
                    >
                      <template v-if="entry.distanceSinceLastChargeKm != null && !showOdometer">+{{ formatDistance(entry.distanceSinceLastChargeKm) }}</template>
                      <template v-else>{{ entry.odometerKm != null ? formatDistance(entry.odometerKm) : '' }}</template>
                    </span>
                    <span v-if="entry._totalConsumption != null"
                      :class="['inline-flex items-center gap-1 text-xs font-medium whitespace-nowrap',
                               consumptionTextClass(entry._totalConsumption, stats?.avgConsumptionKwhPer100km ?? null)]">
                      {{ formatConsumption(entry._totalConsumption) }}
                    </span>
                    <span v-if="entry._maxSoc != null" class="inline-flex items-center gap-1 text-xs text-gray-400 dark:text-gray-500 whitespace-nowrap">
                      <Battery0Icon class="w-3 h-3" />{{ entry._maxSoc }}%
                    </span>
                    <span v-if="entry._maxPower" class="inline-flex items-center gap-1 text-xs text-gray-400 dark:text-gray-500 whitespace-nowrap">
                      <BoltIcon class="w-3 h-3" />{{ entry._maxPower }} kW
                    </span>
                  </div>
                </template>

                <!-- NORMAL LOG HEADER -->
                <template v-else>
                <div class="flex items-center justify-between gap-2">
                  <div class="flex items-center gap-2 min-w-0">
                    <BoltIcon class="w-4 h-4 text-indigo-600 flex-shrink-0" />
                    <span class="font-semibold text-indigo-700 dark:text-indigo-300 whitespace-nowrap">{{ displayKwh(entry.kwhCharged, entry.kwhAtVehicle) }} kWh</span>
                    <span class="text-xs text-gray-400 whitespace-nowrap">{{ formatLogDate(entry.loggedAt) }}</span>
                    <span v-if="sourceInfo(entry.dataSource)"
                      :class="['hidden md:inline-flex items-center gap-0.5 px-1.5 py-0.5 rounded text-xs font-medium whitespace-nowrap',
                               sourceInfo(entry.dataSource)!.classes]">
                      <component :is="sourceInfo(entry.dataSource)!.icon" class="w-3 h-3" />
                      {{ sourceInfo(entry.dataSource)!.label }}
                    </span>
                  </div>
                  <div class="flex items-center gap-1.5 flex-shrink-0">
                    <span v-if="entry.temperatureCelsius != null"
                      :class="['inline-flex items-center gap-0.5 px-2 py-0.5 border rounded-full text-xs whitespace-nowrap', tempBadgeClass(entry.temperatureCelsius)]">
                      <SunIcon class="w-3 h-3" />{{ entry.temperatureCelsius.toFixed(1) }}°C
                    </span>
                    <button v-if="otherCars.length > 0" @click.stop="openReassignModal(entry)"
                      class="p-1 rounded text-gray-300 hover:text-indigo-500 hover:bg-indigo-50 dark:hover:bg-indigo-900/30 transition"
                      :title="t('dashboard.reassign_car')">
                      <ArrowsRightLeftIcon class="w-3.5 h-3.5" />
                    </button>
                    <button @click="editingLog = entry"
                      :class="['p-1 rounded text-gray-300 hover:text-blue-500 hover:bg-blue-50 transition',
                               entry.temperatureCelsius != null ? 'hidden md:block' : '']"
                      :title="t('dashboard.edit_title')">
                      <PencilSquareIcon class="w-3.5 h-3.5" />
                    </button>
                    <button @click="deleteLog(entry.id)"
                      :class="['p-1 rounded text-gray-400 hover:text-red-500 hover:bg-red-50 transition',
                               entry.temperatureCelsius != null ? 'hidden md:block' : '']">
                      <TrashIcon class="w-3.5 h-3.5" />
                    </button>
                    <button v-if="canAccessTrips" @click.stop="startAddTrip(entry.id)"
                      class="p-1 rounded text-gray-400 hover:text-emerald-600 dark:hover:text-emerald-400 hover:bg-emerald-50 dark:hover:bg-emerald-900/30 transition"
                      :title="t('dashboard.trip_add')">
                      <TruckIcon class="w-3.5 h-3.5" />
                    </button>
                  </div>
                </div>
                <!-- Source Badge + Edit/Delete (mobile only) -->
                <div v-if="sourceInfo(entry.dataSource) || entry.temperatureCelsius != null"
                  class="md:hidden flex items-center gap-2">
                  <span v-if="sourceInfo(entry.dataSource)"
                    :class="['inline-flex items-center gap-0.5 px-1.5 py-0.5 rounded text-xs font-medium',
                             sourceInfo(entry.dataSource)!.classes]">
                    <component :is="sourceInfo(entry.dataSource)!.icon" class="w-3 h-3" />
                    {{ sourceInfo(entry.dataSource)!.label }}
                  </span>
                  <div v-if="entry.temperatureCelsius != null" class="flex items-center gap-1 ml-auto">
                    <button @click="editingLog = entry"
                      class="p-1 rounded text-gray-300 hover:text-blue-500 hover:bg-blue-50 transition"
                      :title="t('dashboard.edit_title')">
                      <PencilSquareIcon class="w-3.5 h-3.5" />
                    </button>
                    <button @click="deleteLog(entry.id)"
                      class="p-1 rounded text-gray-400 hover:text-red-500 hover:bg-red-50 transition">
                      <TrashIcon class="w-3.5 h-3.5" />
                    </button>
                  </div>
                </div>
                </template>
                <!-- Badges (normal log only) -->
                <div v-if="!entry._isLadegruppe" class="flex flex-wrap gap-1.5">
                  <span v-if="entry.costEur != null && (entry.kwhCharged ?? entry.kwhAtVehicle)"
                    :class="['inline-flex items-center px-2 py-0.5 border text-xs rounded-full font-medium whitespace-nowrap cursor-pointer transition-all duration-75',
                             showCostAbsolute
                               ? 'bg-gray-50 dark:bg-gray-700 border-gray-200 dark:border-gray-600 text-gray-600 dark:text-gray-300 shadow-[0_4px_0_0_#d1d5db] dark:shadow-[0_4px_0_0_#111827] hover:shadow-[0_2px_0_0_#d1d5db] dark:hover:shadow-[0_2px_0_0_#111827] hover:translate-y-0.5 active:shadow-none active:translate-y-1'
                               : [(costBadgeClass(entry.costEur, entry.kwhCharged ?? entry.kwhAtVehicle) ?? 'bg-green-50 border-green-200 text-green-700'), 'shadow-[0_4px_0_0_#d1d5db] dark:shadow-[0_4px_0_0_#111827] hover:shadow-[0_2px_0_0_#d1d5db] dark:hover:shadow-[0_2px_0_0_#111827] hover:translate-y-0.5 active:shadow-none active:translate-y-1'].join(' ')]"
                    @click="showCostAbsolute = !showCostAbsolute">
                    <template v-if="showCostAbsolute">{{ formatCurrency(entry.costEur) }}</template>
                    <template v-else>{{ formatCostPerKwh(entry.costEur / (entry.kwhCharged ?? entry.kwhAtVehicle)) }}</template>
                  </span>
                  <span
                    v-if="entry.distanceSinceLastChargeKm != null || entry.odometerKm"
                    class="inline-flex items-center gap-1 px-2 py-0.5 bg-gray-50 dark:bg-gray-700 border border-gray-200 dark:border-gray-600 rounded-full text-xs text-gray-600 dark:text-gray-300 whitespace-nowrap"
                    :class="entry.distanceSinceLastChargeKm != null && entry.odometerKm
                      ? 'cursor-pointer shadow-[0_4px_0_0_#d1d5db] dark:shadow-[0_4px_0_0_#111827] active:shadow-none active:translate-y-1 transition-all duration-75 cursor-pointer'
                      : ''"
                    @click="toggleOdometerDisplay(entry.distanceSinceLastChargeKm, entry.odometerKm)"
                  >
                    <template v-if="entry.distanceSinceLastChargeKm != null && !showOdometer">+{{ formatDistance(entry.distanceSinceLastChargeKm) }}</template>
                    <template v-else>{{ entry.odometerKm != null ? formatDistance(entry.odometerKm) : '' }}</template>
                  </span>
                  <span v-if="entry.consumptionKwhPer100km != null"
                    :class="['inline-flex items-center gap-1 text-xs font-medium whitespace-nowrap',
                             entry.consumptionImplausible
                               ? 'text-red-600 dark:text-red-400'
                               : entry.consumptionIsEstimated
                                 ? 'text-gray-400 dark:text-gray-500'
                                 : consumptionTextClass(entry.consumptionKwhPer100km, stats?.avgConsumptionKwhPer100km ?? null)]"
                    :title="entry.consumptionIsEstimated
                      ? 'Schätzwert: berechnet aus geladener Energie ÷ Distanz, da kein SoC-Wert vorhanden.'
                      : entry.consumptionQuality === 'SOC_DELTA'
                        ? 'Näherungswert: berechnet aus SoC-Differenz ohne direkte kWh-Messung.'
                        : undefined">
                    <button
                      v-if="entry.consumptionImplausible"
                      class="flex-shrink-0 focus:outline-none"
                      @click.stop="openTooltipLogId = openTooltipLogId === entry.id ? null : entry.id">
                      <ExclamationTriangleIcon class="w-3 h-3" />
                    </button>
                    <InformationCircleIcon
                      v-if="entry.consumptionQuality === 'SOC_DELTA'"
                      class="w-3 h-3 flex-shrink-0 text-gray-400 dark:text-gray-500" />
                    {{ entry.consumptionIsEstimated ? '~' : '' }}{{ formatConsumption(entry.consumptionKwhPer100km) }}
                  </span>
                  <span v-if="entry.costEur != null && !entry.kwhCharged" class="inline-flex items-center gap-1 text-xs text-gray-500 dark:text-gray-400 whitespace-nowrap">
                    {{ formatCurrency(entry.costEur) }}
                  </span>
                  <span v-if="entry.chargeDurationMinutes" class="inline-flex items-center gap-1 text-xs text-gray-400 dark:text-gray-500 whitespace-nowrap">
                    <ClockIcon class="w-3 h-3" />{{ entry.chargeDurationMinutes }}min
                  </span>
                  <span v-if="entry.socAfterChargePercent != null" class="inline-flex items-center gap-1 text-xs text-gray-400 dark:text-gray-500 whitespace-nowrap">
                    <Battery0Icon class="w-3 h-3" />{{ entry.socAfterChargePercent }}%
                  </span>
                  <span v-if="entry.maxChargingPowerKw" class="inline-flex items-center gap-1 text-xs text-gray-400 dark:text-gray-500 whitespace-nowrap">
                    <BoltIcon class="w-3 h-3" />{{ entry.maxChargingPowerKw }} kW
                  </span>
                </div>
                <!-- Implausibility tooltip panel (normal log only) -->
                <div
                  v-if="!entry._isLadegruppe && entry.consumptionImplausible && openTooltipLogId === entry.id"
                  class="mt-1 p-2.5 rounded-lg bg-amber-50 dark:bg-amber-900/20 border border-amber-200 dark:border-amber-700 text-xs text-amber-800 dark:text-amber-300 space-y-1">
                  <p class="font-medium">{{ t('dashboard.implausible_tooltip_title') }}</p>
                  <p>{{ t('dashboard.implausible_tooltip_desc', { value: formatConsumption(entry.consumptionKwhPer100km) }) }}</p>
                  <ul class="list-disc list-inside space-y-0.5 mt-1">
                    <li>{{ t('dashboard.implausible_tooltip_cause1') }}</li>
                    <li>{{ t('dashboard.implausible_tooltip_cause2') }}</li>
                  </ul>
                </div>
              </div>
              <!-- Ladegruppe Sub-Eintraege (collapsible) -->
              <template v-if="entry._isLadegruppe">
                <Transition name="slide-down">
                  <div v-if="expandedGroups.has(entry.id)" class="mt-1 -space-y-px">
                    <div v-for="(topUp, idx) in entry._topUps" :key="topUp.id"
                      :class="['ml-4 mr-4 flex flex-col gap-1.5 px-2.5 py-1.5 bg-gray-50 dark:bg-gray-700 border border-blue-200 dark:border-[#1e3a5f]',
                               idx === entry._topUps.length - 1 ? 'rounded-b-lg' : '']">
                      <!-- Einzeiler: alles in einer Zeile, bricht auf Mobile sauber um -->
                      <div class="flex items-center gap-x-2">
                        <span class="text-gray-300 text-xs leading-none flex-shrink-0">└</span>
                        <span class="text-xs text-gray-400 dark:text-gray-500 whitespace-nowrap">{{ t('dashboard.top_up') }}</span>
                        <BoltIcon class="w-3.5 h-3.5 text-gray-400 flex-shrink-0" />
                        <span class="text-xs font-semibold text-gray-600 dark:text-gray-300 whitespace-nowrap">{{ topUp.kwhCharged }} kWh</span>
                        <span class="text-xs text-gray-400 whitespace-nowrap">
                          <template v-if="entry._spansMultipleDays">{{ formatLogDate(topUp.loggedAt) }}</template>
                          <template v-else>{{ new Date(topUp.loggedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) }}</template>
                        </span>
                        <span v-if="topUp.chargeDurationMinutes" class="min-[436px]:inline-flex hidden items-center gap-1 text-xs text-gray-400 dark:text-gray-500 whitespace-nowrap">
                          <ClockIcon class="w-3 h-3" />{{ topUp.chargeDurationMinutes }}min
                        </span>
                        <span v-if="topUp.socAfterChargePercent != null" class="min-[436px]:inline-flex hidden items-center gap-1 text-xs text-gray-400 dark:text-gray-500 whitespace-nowrap">
                          <Battery0Icon class="w-3 h-3" />{{ topUp.socAfterChargePercent }}%
                        </span>
                        <div class="ml-auto flex items-center gap-1 flex-shrink-0">
                          <button @click="editingLog = topUp" class="p-1 rounded text-gray-300 hover:text-blue-500 hover:bg-blue-50 transition" :title="t('dashboard.edit_title')">
                            <PencilSquareIcon class="w-3.5 h-3.5" />
                          </button>
                          <button @click="deleteLog(topUp.id)" class="p-1 rounded text-gray-400 hover:text-red-500 hover:bg-red-50 transition">
                            <TrashIcon class="w-3.5 h-3.5" />
                          </button>
                        </div>
                      </div>
                      <div class="max-[436px]:flex min-[436px]:hidden items-center gap-2">
                        <span v-if="topUp.chargeDurationMinutes" class="inline-flex items-center gap-1 text-xs text-gray-400 dark:text-gray-500 whitespace-nowrap">
                          <ClockIcon class="w-3 h-3" />{{ topUp.chargeDurationMinutes }}min
                        </span>
                        <span v-if="topUp.socAfterChargePercent != null" class="inline-flex items-center gap-1 text-xs text-gray-400 dark:text-gray-500 whitespace-nowrap">
                          <Battery0Icon class="w-3 h-3" />{{ topUp.socAfterChargePercent }}%
                        </span>
                      </div>
                    </div>
                  </div>
                </Transition>
              </template>
              </div><!-- end charge v-else -->

              </template><!-- end v-for mergedLogFeed -->
            </template>
          </div>
          <!-- Pagination -->
          <div class="flex items-center justify-between mt-4">
            <button
              @click="fetchLogsAndScroll(logsPage - 1)"
              :disabled="logsPage === 0"
              class="flex items-center gap-1 px-3 py-2 text-sm rounded-lg border border-gray-200 dark:border-gray-600 dark:text-gray-300 disabled:opacity-40 disabled:cursor-not-allowed hover:bg-gray-50 dark:hover:bg-gray-700 transition">
              <ChevronLeftIcon class="w-4 h-4" />{{ t('dashboard.prev') }}
            </button>
            <button
              @click="fetchLogsAndScroll(logsPage + 1)"
              :disabled="!hasMoreLogs"
              class="flex items-center gap-1 px-3 py-2 text-sm rounded-lg border border-gray-200 dark:border-gray-600 dark:text-gray-300 disabled:opacity-40 disabled:cursor-not-allowed hover:bg-gray-50 dark:hover:bg-gray-700 transition">
              {{ t('dashboard.next') }}<ChevronRightIcon class="w-4 h-4" />
            </button>
          </div>
        </div>

        <!-- Chart 1: Charging & Costs -->
        <div class="border-t border-gray-100 dark:border-gray-700 pt-6">
          <div class="md:bg-gray-50 md:dark:bg-gray-700 py-4 md:p-6 -mx-4 md:mx-0 md:rounded-lg md:border md:border-gray-200 md:dark:border-gray-600">
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
              <div class="flex flex-wrap gap-x-6 gap-y-1 mt-3 text-xs text-gray-400 px-4 md:px-0">
                <span>{{ t('dashboard.chart_left_axis') }}: {{ currencySymbol }}/kWh</span>
                <span>{{ t('dashboard.chart_right_axis') }}: kWh</span>
              </div>
            </template>
          </div>
        </div>

        <!-- Chart 2: Range & Efficiency (only if distance data exists) -->
        <div v-if="hasDistanceData" class="border-t border-gray-100 dark:border-gray-700 pt-6">
          <div class="md:bg-gray-50 md:dark:bg-gray-700 py-4 md:p-6 -mx-4 md:mx-0 md:rounded-lg md:border md:border-gray-200 md:dark:border-gray-600">
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
              <div class="flex flex-wrap gap-x-6 gap-y-1 mt-3 text-xs text-gray-400 px-4 md:px-0">
                <span>{{ t('dashboard.chart_left_axis') }}: {{ consumptionUnitLabel() }}</span>
                <span>{{ t('dashboard.chart_right_axis') }}: {{ distanceUnitLabel() }}</span>
              </div>
            </template>
          </div>
        </div>

        <!-- WLTP Delta Bar Chart -->
        <div v-if="wltp && hasDistanceData && wltpChartData" class="border-t border-gray-100 dark:border-gray-700 pt-6">
          <div class="md:bg-gray-50 md:dark:bg-gray-700 py-4 md:p-6 -mx-4 md:mx-0 md:rounded-lg md:border md:border-gray-200 md:dark:border-gray-600">
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
          <div class="bg-amber-50 dark:bg-amber-900/30 border border-amber-200 dark:border-amber-700 md:rounded-lg p-3 md:p-4 text-sm text-amber-700 dark:text-amber-300">
            {{ t('dashboard.wltp_missing') }}
            <router-link to="/cars" class="font-semibold underline">{{ t('dashboard.wltp_missing_link') }}</router-link>
            {{ t('dashboard.wltp_missing_suffix') }}
          </div>
        </div>

        <!-- Charging Heat Map -->
        <div class="border-t border-gray-100 dark:border-gray-700 pt-6">
          <div class="md:bg-gray-50 md:dark:bg-gray-700 py-4 md:p-6 -mx-4 md:mx-0 md:rounded-lg md:border md:border-gray-200 md:dark:border-gray-600 mb-4 md:mb-0">
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

        <!-- Support -->
        <div class="px-4 md:px-0 py-6 text-center">
          <p class="text-sm text-gray-400 flex items-center justify-center gap-1">
            {{ t('dashboard.free_text') }}
            <SupportPopover variant="footer" />
          </p>
        </div>

        </div>
      </div>
      </div>
    </Transition>
  </div>

  <EditLogModal
    v-if="editingLog"
    :log="editingLog"
    @close="editingLog = null"
    @saved="() => { editingLog = null; refreshLogsAndGroups() }"
  />

  <ImplausibleLogsModal
    :car-id="selectedCarId"
    :open="showImplausibleModal"
    @close="() => { showImplausibleModal = false; if (implausibleModalDirty) { fetchStatistics(); implausibleModalDirty = false } }"
    @updated="() => { fetchImplausibleCount(); implausibleModalDirty = true }"
  />

  <!-- Fahrzeug-Zuordnung Modal -->
  <Teleport to="body">
    <Transition enter-active-class="transition duration-200 ease-out" enter-from-class="opacity-0" enter-to-class="opacity-100" leave-active-class="transition duration-150 ease-in" leave-from-class="opacity-100" leave-to-class="opacity-0">
      <div v-if="reassignModalEntry" class="fixed inset-0 z-50 flex items-end sm:items-center justify-center p-0 sm:p-4" @click.self="reassignModalEntry = null">
        <div class="absolute inset-0 bg-black/40" @click="reassignModalEntry = null" />
        <div class="relative w-full sm:max-w-sm bg-white dark:bg-gray-800 rounded-t-2xl sm:rounded-2xl shadow-2xl p-6 space-y-5">
          <div>
            <h3 class="text-base font-semibold text-gray-900 dark:text-gray-100">{{ t('dashboard.reassign_car') }}</h3>
            <p class="text-sm text-gray-500 dark:text-gray-400 mt-1">{{ t('dashboard.reassign_car_hint') }}</p>
          </div>

          <div class="space-y-2">
            <button
              v-for="car in otherCars"
              :key="car.id"
              @click="reassignSelectedCarId = car.id; reassignError = null"
              :class="['w-full flex items-center gap-3 p-3 rounded-xl border-2 transition text-left',
                       reassignSelectedCarId === car.id
                         ? 'border-indigo-500 bg-indigo-50 dark:bg-indigo-900/30'
                         : 'border-gray-200 dark:border-gray-600 hover:border-gray-300 dark:hover:border-gray-500']">
              <TruckIcon class="w-5 h-5 flex-shrink-0" :class="reassignSelectedCarId === car.id ? 'text-indigo-600' : 'text-gray-400'" />
              <span class="font-medium text-gray-800 dark:text-gray-200">{{ enumToLabel(car.brand) }} {{ enumToLabel(car.model) }}</span>
              <div v-if="reassignSelectedCarId === car.id" class="ml-auto w-4 h-4 rounded-full bg-indigo-500 flex-shrink-0" />
            </button>
          </div>

          <p v-if="reassignError" class="text-sm text-red-600 dark:text-red-400">{{ reassignError }}</p>

          <div class="flex gap-3 pt-1">
            <button @click="reassignModalEntry = null"
              class="flex-1 px-4 py-2.5 rounded-xl border border-gray-200 dark:border-gray-600 text-sm font-medium text-gray-600 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 transition">
              {{ t('common.cancel') }}
            </button>
            <button @click="doSaveReassign"
              :disabled="!reassignSelectedCarId || reassignSaving"
              class="flex-1 px-4 py-2.5 rounded-xl text-sm font-medium text-white transition disabled:opacity-40"
              :class="reassignSelectedCarId ? 'bg-indigo-600 hover:bg-indigo-700' : 'bg-gray-300 dark:bg-gray-600'">
              {{ reassignSaving ? t('common.saving') : t('common.save') }}
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
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
</style>
