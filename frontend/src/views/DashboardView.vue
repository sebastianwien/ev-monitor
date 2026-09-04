<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch, defineAsyncComponent } from 'vue'
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
import { useRouter, useRoute } from 'vue-router'
import LicensePlate from '../components/car/LicensePlate.vue'
const ChargingHeatMap = defineAsyncComponent(() => import('../components/dashboard/ChargingHeatMap.vue'))
import RewardSystemUpdateBanner from '../components/shared/RewardSystemUpdateBanner.vue'
import { useCountryStore } from '../stores/country'
import { useAuthStore } from '../stores/auth'
import { analytics } from '../services/analytics'
import ImplausibleLogsModal from '../components/dashboard/ImplausibleLogsModal.vue'
import PeerBenchmarkCard from '../components/dashboard/PeerBenchmarkCard.vue'
import PeerModelComparisonCard from '../components/dashboard/PeerModelComparisonCard.vue'
import WltpComparisonCard from '../components/dashboard/WltpComparisonCard.vue'
import RangeCard from '../components/dashboard/RangeCard.vue'
import CostBreakdownCard from '../components/dashboard/CostBreakdownCard.vue'
import CostModeToggle from '../components/dashboard/CostModeToggle.vue'
import LiveChargingCard from '../components/dashboard/LiveChargingCard.vue'
import DashboardEmptyState from '../components/dashboard/DashboardEmptyState.vue'
import DashboardInsights from '../components/dashboard/DashboardInsights.vue'
import DashboardInsightsTeaser from '../components/dashboard/DashboardInsightsTeaser.vue'
import ChargingTypeSplitCard from '../components/dashboard/ChargingTypeSplitCard.vue'
import ChargingSavingsCard from '../components/dashboard/ChargingSavingsCard.vue'
import ChargingSavingsCardTeaser from '../components/dashboard/ChargingSavingsCardTeaser.vue'
import HomeInvestmentModal from '../components/dashboard/HomeInvestmentModal.vue'
import chargingSavingsService from '../api/chargingSavingsService'
import dashboardPreferencesService from '../api/dashboardPreferencesService'
import { useAnalyticsUpsellTarget } from '../composables/useUpsellTarget'
import type { ChargingSavings } from '../components/dashboard/chargingSavings'
import ChargingEfficiencyCard from '../components/dashboard/ChargingEfficiencyCard.vue'
import CO2Card from '../components/dashboard/CO2Card.vue'
import SmartInsightsCard from '../components/dashboard/SmartInsightsCard.vue'
import CarCardDetails from '../components/dashboard/CarCardDetails.vue'
import BatterySohModal from '../components/car/BatterySohModal.vue'
import type { Car } from '../api/carService'
import CostHistoryCard from '../components/dashboard/CostHistoryCard.vue'
import RecentActivityCard from '../components/dashboard/RecentActivityCard.vue'
import EditTripModal from '../components/dashboard/EditTripModal.vue'
import { latestChargeEntry, latestTripEntry } from '../utils/recentActivity'
import { useLocaleFormat } from '../composables/useLocaleFormat'
import { useCarContext } from '../composables/useCarContext'
import TripActivitySummaryCard from '../components/dashboard/TripActivitySummaryCard.vue'
import { summarizeTripMonth, resolveTripWindow, tripsInWindow } from '../utils/tripMonthSummary'
import { useDashboardCharts } from '../composables/useDashboardCharts'
import { useVehicleCharging } from '../composables/useVehicleCharging'
import { carDisplayName, enumToLabel } from '../utils/enumLabel'
import { isVwGroupBrand } from '../api/vwGroupService'

ChartJS.register(CategoryScale, LinearScale, BarElement, PointElement, LineElement, Title, Tooltip, Legend, Filler, ChartDataLabels)

const { t, locale } = useI18n()
const router = useRouter()
const route = useRoute()
const { formatConsumption, consumptionUnitLabel, formatDistance, distanceUnitLabel, formatCurrency, formatCostPerKwh, formatCostPerDistance, currencySymbol } = useLocaleFormat()

// -- Geteilter Auto-Context (State + Polling liegen im CarContextLayout) --
const {
  selectedCarId, stats, lastMonthStats, insightStats, carInfo, wltp, loading, chartsReady, isInitialLoad, error,
  cars, carImageUrls, selectedTimeRange, selectedGroupBy, customStartDate, customEndDate,
  importBannerDismissed, teslaStatus, smartcarStatus, vwGroupStatus, hasDistanceData, avgCostPer100km,
  fixedCostPerMonth, displayedCostPer100km, canShowFixedModes, effectiveCostMode, toggleCostMode,
  timeRangeOptions, groupByOptions, dismissImportBanner, fetchImplausibleCount, fetchStatistics,
  hasAnyLogs, mergedLogFeed, trips, currentOdometerKm, sourceInfo, initCars,
  editingLog, priceAmendingLog, startEditTrip, cancelTripEdit, saveTripEdit, tripForm, tripSaving, tripError,
} = useCarContext()

// Car whose battery-health detail sheet is open (null = closed).
const sohModalCar = ref<Car | null>(null)

/**
 * Der Kachel-Titel benennt die Kostenbasis, weil der Umschalter selbst nur ein Icon ist.
 * Kurzformen sind Absicht - die Titelzeile hat auf Desktop nur rund 135px.
 */
const costModeLabel = computed(() => {
  if (effectiveCostMode.value === 'fixed') return t('dashboard.metric_avg_cost_fixed')
  if (effectiveCostMode.value === 'total') return t('dashboard.metric_avg_cost_total')
  return t('dashboard.metric_avg_cost')
})

/**
 * Zweite Zeile der Kachel. Im Fixkosten-Modus waere ct/kWh eine Energie-Kennzahl unter einer
 * Fixkosten-Ueberschrift - dort stehen deshalb die Fixkosten pro Monat.
 */
const costModeSecondary = computed(() => {
  if (effectiveCostMode.value === 'fixed') {
    return fixedCostPerMonth.value != null
      ? t('dashboard.metric_cost_per_month', { value: formatCurrency(fixedCostPerMonth.value) })
      : null
  }
  return stats.value?.avgCostPerKwh != null ? formatCostPerKwh(stats.value.avgCostPerKwh) : null
})

// Newest charge / trip for the "letzte Aktivität"-Block. Uses the full merged
// feed (not the filtered stats), so it always reflects the absolute latest event.
const latestCharge = computed(() => latestChargeEntry(mergedLogFeed.value))
const latestTrip = computed(() => latestTripEntry(mergedLogFeed.value))

// Beide Kacheln bearbeiten ihren Eintrag direkt. Der Ladevorgang nutzt den geteilten
// EditLogModal (liegt im CarContextLayout), die Fahrt bekommt hier ein eigenes Overlay -
// im Log-Feed haengt dasselbe Formular inline an der Zeile, die es hier nicht gibt.
const tripModalId = ref<string | null>(null)
const tripSheet = ref<{ requestClose: () => void } | null>(null)

function openTripEdit() {
  if (!latestTrip.value) return
  startEditTrip(latestTrip.value)
  tripModalId.value = latestTrip.value.id
}

/** Kommt erst, wenn das Sheet ausgefahren ist - dann darf der State weg. */
function onTripSheetClosed() {
  cancelTripEdit()
  tripModalId.value = null
}

async function submitTripEdit() {
  const id = tripModalId.value
  if (!id) return
  await saveTripEdit(id)
  // saveTripEdit haelt den Fehler in tripError fest und laesst das Formular offen.
  if (!tripError.value) tripSheet.value?.requestClose()
}

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

// The analytics teaser only makes sense if the user has a data foundation, i.e. trips.
// Backend already hides trips from non-entitled non-Tesla users (canViewLiveTrips), so this
// naturally targets Tesla/imported/API-pushed feeds and spares confused free users who would
// buy in but get no insights for lack of trip data.
const feedHasTrips = computed(() => mergedLogFeed.value.some((e: any) => e._isTrip))

// -- Implausible logs modal --
const showImplausibleModal = ref(false)
const implausibleModalDirty = ref(false)

// -- Range calculator --

const selectedCar = computed(() =>
  cars.value.find(c => c.id === selectedCarId.value) ?? cars.value[0] ?? null
)

const { isVehicleCharging, isSmartcarCharging, isWallboxCharging } =
  useVehicleCharging(cars, smartcarStatus, vwGroupStatus)

// -- Lifecycle --
const LS_ACTIVATION_KEY = 'ev_activation_reached'

// Daten-Reload bei Auto-Wechsel liegt zentral im CarContextLayout. Hier nur noch
// die Dashboard-Analytics, ausgeloest wenn die geladenen Statistiken sich aendern.
watch(stats, () => {
  if (!selectedCarId.value) {
    if (cars.value.length === 0) analytics.trackEmptyDashboardViewed('no_car')
    return
  }
  if (!stats.value || stats.value.totalCharges === 0) {
    analytics.trackEmptyDashboardViewed(hasAnyLogs.value ? 'no_logs_in_period' : 'no_logs_ever')
  } else if (!localStorage.getItem(LS_ACTIVATION_KEY)) {
    localStorage.setItem(LS_ACTIVATION_KEY, '1')
    analytics.trackActivationReached()
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

// Beide Bodies sind im Pager dauerhaft gerendert -> aktiv = die Route zeigt sie.
const viewActive = computed(() => route.name === 'statistics')
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

// Heimlade-Ersparnis. Laedt unabhaengig von den uebrigen Statistiken: liefert der
// Endpoint nichts (kein Preis bekannt, Tarif ohne die Kachel), bleibt sie einfach aus,
// ohne das restliche Dashboard aufzuhalten.
const chargingSavings = ref<ChargingSavings | null>(null)
// Berechtigt: der Nutzer duerfte die Kachel sehen. Ob sie erscheint, haengt zusaetzlich
// an relevanten Zahlen (sonst 204, chargingSavings bleibt null) und daran, dass der Nutzer
// sie nicht selbst ausgeblendet hat.
const chargingSavingsEntitled = ref(false)
// Vom Nutzer ausgeblendet (serverseitig gehalten). Die Zahlen bleiben geladen, damit das
// Wiedereinblenden ohne neuen Request greift.
const chargingSavingsDismissed = ref(false)
// Zugang endgueltig weg (Probemonat vorbei, kein bezahlter Tarif): dann zeigt das
// Dashboard den Upsell-Teaser. Bewusst getrennt von "nicht berechtigt bei unklarem
// Zustand" (401/Fehler), damit der Teaser nicht bei Ladefehlern aufblitzt.
const chargingSavingsLocked = ref(false)
// Nutzer sieht die Kachel nur ueber den Probemonat - steuert den Retention-Hinweis.
const chargingSavingsViaTrial = ref(false)
const chargingSavingsTrialEndsAt = ref<string | null>(null)
// Wohin das "dauerhaft freischalten"-CTA fuehrt: Supporter-Pack fuer Tesla-Fahrer (Quelle
// vorhanden), sonst AutoSync. Dieselbe Regel wie bei den uebrigen Analytics-Upsells.
const savingsUpsellTarget = useAnalyticsUpsellTarget()
const showInvestmentPrompt = ref(false)

// Fahrten-Zusammenfassung fuer den Fall "Fahrten im Zeitraum, aber keine Ladung": ersetzt den
// generischen Empty-State. Fenster deckt sich mit dem der Ladestatistik.
const tripMonthSummary = computed(() => {
  if (!stats.value || stats.value.totalCharges !== 0) return null
  const window = resolveTripWindow(selectedTimeRange.value, customStartDate.value, customEndDate.value, new Date())
  const windowed = tripsInWindow((trips.value ?? []) as unknown[] as any[], window)
  return summarizeTripMonth(windowed, selectedCar.value?.effectiveBatteryCapacityKwh ?? null)
})

const tripMonthLabel = computed(() => {
  const window = resolveTripWindow(selectedTimeRange.value, customStartDate.value, customEndDate.value, new Date())
  const d = window ? new Date(window.startMs) : new Date()
  return d.toLocaleDateString(locale.value === 'en' ? 'en-GB' : locale.value, { month: 'long' })
})

async function loadChargingSavings() {
  try {
    const result = await chargingSavingsService.get()
    chargingSavings.value = result.savings
    chargingSavingsEntitled.value = result.entitled
    chargingSavingsLocked.value = result.locked
    chargingSavingsViaTrial.value = result.viaTrial
    chargingSavingsTrialEndsAt.value = result.trialEndsAt
    // Die Ausblenden-Preference ist ein eigenes Concern und haengt nicht an der
    // Berechtigung - der Savings-Endpoint liefert sie im 403-Fall (Teaser) gar nicht mit.
    // Nur laden, wenn ueberhaupt etwas erscheinen koennte (Kachel oder Teaser).
    if (result.savings || result.locked) {
      try {
        chargingSavingsDismissed.value = (await dashboardPreferencesService.get()).savingsCardDismissed
      } catch {
        chargingSavingsDismissed.value = false
      }
    } else {
      chargingSavingsDismissed.value = false
    }
  } catch {
    chargingSavings.value = null
    chargingSavingsEntitled.value = false
    chargingSavingsLocked.value = false
    chargingSavingsViaTrial.value = false
    chargingSavingsTrialEndsAt.value = null
    chargingSavingsDismissed.value = false
  }
}

async function saveInvestment(value: number | null) {
  await chargingSavingsService.saveInvestment(value)
  showInvestmentPrompt.value = false
  await loadChargingSavings()
}

// Ausblenden: sofort verstecken, dann persistieren. Die Undo-Zeile tritt kurz an die
// Stelle der Kachel und verschwindet von selbst - so ist die Aktion umkehrbar, ohne dass
// am Dashboard dauerhaft etwas stehen bleibt. Dauerhaft wieder einblendbar in den
// Einstellungen.
const savingsUndoVisible = ref(false)
let savingsUndoTimer: ReturnType<typeof setTimeout> | null = null

function clearSavingsUndoTimer() {
  if (savingsUndoTimer !== null) {
    clearTimeout(savingsUndoTimer)
    savingsUndoTimer = null
  }
}

async function dismissSavingsCard() {
  chargingSavingsDismissed.value = true
  savingsUndoVisible.value = true
  clearSavingsUndoTimer()
  savingsUndoTimer = setTimeout(() => { savingsUndoVisible.value = false }, 8000)
  try {
    await dashboardPreferencesService.setSavingsCardDismissed(true)
  } catch {
    // Fehlgeschlagen: den optimistischen Schritt zuruecknehmen, sonst waere die Kachel
    // weg, ohne dass es der Server weiss.
    chargingSavingsDismissed.value = false
    savingsUndoVisible.value = false
    clearSavingsUndoTimer()
  }
}

async function restoreSavingsCard() {
  chargingSavingsDismissed.value = false
  savingsUndoVisible.value = false
  clearSavingsUndoTimer()
  try {
    await dashboardPreferencesService.setSavingsCardDismissed(false)
  } catch {
    chargingSavingsDismissed.value = true
  }
}

onMounted(() => {
  document.addEventListener('click', onClickOutsideFilter)
  loadChargingSavings()
})
onUnmounted(() => {
  document.removeEventListener('click', onClickOutsideFilter)
  clearSavingsUndoTimer()
})


</script>

<template>
<div>
  <div class="md:max-w-6xl md:mx-auto md:px-6 md:pb-6">
    <RewardSystemUpdateBanner class="mb-4" />
    <Transition name="fade" mode="out-in">
      <div v-if="!loading || !isInitialLoad">
        <div class="bg-gray-100 dark:bg-gray-800 md:rounded-sm md:shadow-[4px_4px_0_rgba(0,0,0,0.30)] dark:md:shadow-[4px_4px_0_rgba(255,255,255,0.30)] p-4 md:p-6 pb-6">
          <!-- Auf Desktop benennt der aktive Tab die Seite - eine sichtbare Ueberschrift
               wuerde ihn nur wiederholen. Fuer Screenreader bleibt sie. Navigation laeuft
               ueber die Workspace-Leiste (Desktop) bzw. die Bottom-Nav (Mobile). -->
          <h1 class="sr-only">Dashboard</h1>

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

          <!-- Mobile Auto-Card + Tab-Switch liegen im CarContextLayout (geteilter Header). -->
          <!-- Desktop-Auto-Selektor (>=768px) -->
          <div
            v-if="cars.length > 0"
            class="hidden md:block"
            :class="cars.length > 1
              ? 'sticky top-16 z-10 bg-white dark:bg-gray-800 md:-mx-6 md:px-6 md:py-3 mb-3 border-b border-gray-100 dark:border-gray-700 shadow-sm'
              : 'mb-6 md:w-fit md:mx-auto'"
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
                <div class="flex-shrink-0 md:w-20 md:h-auto md:self-stretch bg-gray-100 dark:bg-gray-600 flex items-center justify-center overflow-hidden">
                  <img
                    v-if="carImageUrls[car.id]"
                    :src="carImageUrls[car.id]"
                    :alt="car.model"
                    class="w-full h-full object-cover" />
                  <TruckIcon v-else class="w-6 h-6 md:w-8 md:h-8 text-gray-400" />
                </div>
                <div class="min-w-0 flex-1 px-3 py-1.5 md:px-3 md:py-2">
                  <!-- Desktop: zweizeiliges Layout. Ab md sichtbar - die Kachel passt mit
                       vollem Inhalt auch in den schmalsten Desktop-Viewport; vorher fiel sie
                       unterhalb von lg auf das blosse Bild zusammen. -->
                  <div class="hidden md:block">
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
                <!-- Desktop horizontal extension (single-car only, ab md): chips column -->
                <div
                  v-if="cars.length === 1 && car.id === selectedCarId"
                  class="hidden md:flex flex-shrink-0 self-stretch items-center border-l border-gray-200 dark:border-gray-600 pl-3 pr-3 py-2"
                >
                  <CarCardDetails
                    :car="car"
                    :wltp="wltp"
                    :current-odometer-km="currentOdometerKm"
                    orientation="horizontal"
                    interactive-soh
                    @open-soh="sohModalCar = car"
                  />
                </div>
              </button>
            </div>
          </div>
          <!-- Letzte Aktivität: letzter Ladevorgang + letzte Fahrt. Bewusst ÜBER dem
               Zeitraum-Filter - es ist eine filter-unabhängige Momentaufnahme des
               jüngsten Ereignisses, nicht Teil der gefilterten Auswertung darunter. -->
          <RecentActivityCard
            :charge="latestCharge"
            :trip="latestTrip"
            :effective-battery-capacity-kwh="selectedCar?.effectiveBatteryCapacityKwh ?? null"
            :source-info="sourceInfo"
            @edit-charge="editingLog = latestCharge"
            @amend-charge="priceAmendingLog = latestCharge"
            @edit-trip="openTripEdit"
          />

          <!-- Mobile: Zeitraum-Filter (<lg): Sibling, damit es auf Mobile sichtbar
               bleibt (der Desktop-Selektor darueber ist hidden md:block). -->
            <div v-if="filterBarVisible"
              class="sm:hidden mt-1.5 mb-4 flex items-center gap-4" ref="filterDropdownMobile">
              <div class="flex-1 h-px bg-gray-200 dark:bg-gray-700"></div>
              <!-- Filter trigger (zentriert zwischen Trennstrichen, wie Desktop) -->
              <div class="relative">
                <button
                  @click.stop="showFilterDropdown = !showFilterDropdown"
                  class="flex items-center gap-1.5 px-2.5 py-1 rounded-sm border-2 border-gray-300 dark:border-gray-600 shadow-[2px_2px_0_0_#d1d5db] dark:shadow-[2px_2px_0_0_#374151] hover:shadow-[3px_3px_0_0_#9ca3af] dark:hover:shadow-[3px_3px_0_0_#4b5563] bg-white dark:bg-gray-700 text-gray-600 dark:text-gray-300 text-xs font-medium hover:bg-gray-50 dark:hover:bg-gray-600 cursor-pointer transition">
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
              <div class="flex-1 h-px bg-gray-200 dark:bg-gray-700"></div>
            </div>

          <!-- Live-Ladevorgang: blendet sich automatisch ein wenn aktive Session und User AS Live -->
          <LiveChargingCard
            v-if="selectedCarId && authStore.canViewLiveCharging(selectedCar?.brand ?? null)"
            :car-id="selectedCarId"
            :car-display-name="selectedCar ? [carDisplayName(selectedCar.brand, selectedCar.model), selectedCar.trim].filter(Boolean).join(' ') : ''"
            :license-plate="selectedCar?.licensePlate ?? null"
            :brand="selectedCar?.brand ?? null"
            :avg-consumption-kwh-per100km="stats?.avgConsumptionKwhPer100km != null ? Number(stats.avgConsumptionKwhPer100km) : null"
            class="mb-6"
          />

          <div v-if="error" class="mb-4 p-4 bg-red-50 dark:bg-red-900/30 border border-red-200 dark:border-red-700 text-red-700 dark:text-red-300 rounded-sm">{{ error }}</div>

        <!-- Desktop: Datumsfilter zentriert mit Trennstrich - immer sichtbar (auch bei leerem Zeitraum) -->
        <div v-if="filterBarVisible" class="hidden md:flex items-center gap-4 mb-4">
          <div class="flex-1 h-px bg-gray-200 dark:bg-gray-700"></div>
          <div class="relative" ref="filterDropdownDesktop">
            <button
              data-testid="dashboard-filter-toggle"
              @click.stop="showFilterDropdown = !showFilterDropdown"
              class="flex items-center gap-2 px-4 py-1.5 rounded-sm border-2 border-gray-300 dark:border-gray-600 shadow-[2px_2px_0_0_#d1d5db] dark:shadow-[2px_2px_0_0_#374151] hover:shadow-[3px_3px_0_0_#9ca3af] dark:hover:shadow-[3px_3px_0_0_#4b5563] bg-white dark:bg-gray-700 hover:bg-gray-50 dark:hover:bg-gray-600 text-gray-700 dark:text-gray-300 text-sm font-medium cursor-pointer transition">
              <CalendarIcon class="w-4 h-4 opacity-60" />
              <span>{{ timeRangeOptions.find(o => o.value === selectedTimeRange)?.shortLabel ?? selectedTimeRange }}</span>
              <span class="text-gray-300 dark:text-gray-500">·</span>
              <span>{{ groupByOptions.find(o => o.value === selectedGroupBy)?.label }}</span>
              <ChevronDownIcon class="w-3.5 h-3.5 opacity-50 transition-transform" :class="{ 'rotate-180': showFilterDropdown }" />
            </button>
            <Transition name="dropdown">
              <div v-if="showFilterDropdown"
                data-testid="dashboard-filter-dropdown"
                class="absolute left-1/2 -translate-x-1/2 top-full mt-1.5 z-40 bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-sm shadow-[2px_2px_0_0_#d1d5db] dark:shadow-[2px_2px_0_0_#374151] p-3 w-72"
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
          <div class="flex-1 h-px bg-gray-200 dark:bg-gray-700"></div>
        </div>

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

          <!-- Empty State: Trips in time range but no charge -> drove, didn't charge -->
          <div v-else-if="stats && stats.totalCharges === 0 && hasAnyLogs && tripMonthSummary" class="py-6">
            <TripActivitySummaryCard
              :summary="tripMonthSummary"
              :month-label="tripMonthLabel"
              :can-view-analytics="authStore.canViewLiveAnalytics"
              :upsell-target="savingsUpsellTarget"
            />
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
          :stats="insightStats"
          :last-month-stats="lastMonthStats"
          class="mb-5"
        />

        <!-- Key Metrics: Mobile Grid -->
        <div class="md:hidden mb-4">
          <div class="grid grid-cols-2 grid-rows-[auto_auto_auto] gap-px bg-gray-200 dark:bg-gray-700 rounded-sm overflow-hidden border-2 border-gray-300 dark:border-gray-600 shadow-[2px_2px_0_0_#d1d5db] dark:shadow-[2px_2px_0_0_#374151]">
            <!-- Gesamtkosten: spans 2 rows -->
            <div class="row-span-2 bg-white dark:bg-gray-800 px-4 py-3">
              <CostBreakdownCard
                layout="stacked"
                :energy-cost-eur="stats.energyCostEur"
                :fixed-cost-eur="stats.fixedCostEur"
                :fixed-income-eur="stats.fixedIncomeEur"
                :total-cost-eur="stats.totalCostEur"
              />
            </div>
            <!-- Ø Kosten: kompakt -->
            <div v-if="avgCostPer100km != null"
              class="w-full px-4 py-3 text-left"
              :class="openMetricTooltip === 'costPer100km' ? 'bg-gray-50 dark:bg-gray-900/50' : 'bg-white dark:bg-gray-800'">
              <div class="flex items-center gap-1 mb-1">
                <span class="text-xs font-medium text-gray-500 dark:text-gray-400 whitespace-nowrap">{{ costModeLabel }}</span>
                <button
                  type="button"
                  class="flex-shrink-0 text-gray-400 hover:text-gray-600 dark:text-gray-500 dark:hover:text-gray-300 focus:outline-none focus:ring-2 focus:ring-pink-500 rounded"
                  :aria-label="t('dashboard.metric_info_aria')"
                  @click.stop="openMetricTooltip = openMetricTooltip === 'costPer100km' ? null : 'costPer100km'">
                  <InformationCircleIcon class="w-3 h-3 flex-shrink-0" />
                </button>
                <CostModeToggle
                  v-if="canShowFixedModes"
                  :mode="effectiveCostMode"
                  class="ml-auto"
                  @toggle="toggleCostMode"
                />
              </div>
              <div class="text-base font-bold text-gray-900 dark:text-gray-100 leading-tight">{{ formatCostPerDistance(displayedCostPer100km!) }}</div>
              <div v-if="costModeSecondary" class="text-base font-bold text-gray-900 dark:text-gray-100 leading-tight">{{ costModeSecondary }}</div>
            </div>
            <!-- Gesamtstrecke: unterhalb Ø Kosten -->
            <button v-if="stats.totalDistanceKm != null"
              type="button"
              class="w-full px-4 py-3 text-left"
              :class="openMetricTooltip === 'distance' ? 'bg-gray-50 dark:bg-gray-900/50' : 'bg-white dark:bg-gray-800'"
              @click.stop="openMetricTooltip = openMetricTooltip === 'distance' ? null : 'distance'">
              <div class="flex items-center gap-1 text-xs font-medium text-gray-500 dark:text-gray-400 mb-1">
                {{ t('dashboard.metric_total_distance') }}
                <InformationCircleIcon class="w-3 h-3 flex-shrink-0" />
              </div>
              <div class="text-xl font-bold text-gray-900 dark:text-gray-100 leading-tight">{{ formatDistance(stats.totalDistanceKm) }}</div>
            </button>
            <!-- Gesamtenergie -->
            <div class="bg-white dark:bg-gray-800 px-4 py-3">
              <div class="text-xs font-medium text-gray-500 dark:text-gray-400 mb-1">{{ t('dashboard.metric_total_energy') }}</div>
              <div class="flex items-baseline gap-1 leading-tight">
                <span class="text-xl font-bold text-gray-900 dark:text-gray-100">{{ stats.totalKwhCharged?.toFixed(1) ?? '–' }}</span>
                <span class="text-[10px] text-gray-400 dark:text-gray-500 font-medium">kWh</span>
              </div>
              <div class="text-[10px] text-gray-400 dark:text-gray-500 mt-0.5">{{ stats.totalCharges }} {{ t('dashboard.metric_charges') }}</div>
            </div>
            <!-- Ø Verbrauch -->
            <button v-if="stats.avgConsumptionKwhPer100km != null"
              type="button"
              class="w-full px-4 py-3 text-left"
              :class="openMetricTooltip === 'consumption' ? 'bg-gray-50 dark:bg-gray-900/50' : 'bg-white dark:bg-gray-800'"
              @click.stop="openMetricTooltip = openMetricTooltip === 'consumption' ? null : 'consumption'">
              <div class="flex items-center gap-1 text-xs font-medium text-gray-500 dark:text-gray-400 mb-1">
                {{ t('dashboard.metric_avg_consumption') }}
                <InformationCircleIcon class="w-3 h-3 flex-shrink-0" />
              </div>
              <div class="flex items-baseline gap-1 leading-tight">
                <span class="text-xl font-bold text-gray-900 dark:text-gray-100">{{ formatConsumption(stats.avgConsumptionKwhPer100km, { showUnit: false }) }}</span>
                <span class="text-[10px] text-gray-400 dark:text-gray-500 font-medium">{{ consumptionUnitLabel() }}</span>
              </div>
              <div v-if="stats.chargingEfficiencySplit && stats.chargingEfficiencySplit.totalLogCount > 0" class="mt-1.5 flex gap-2 text-[10px] text-gray-400 dark:text-gray-500">
                <span>Brutto {{ Math.round((stats.chargingEfficiencySplit.totalLogCount - stats.chargingEfficiencySplit.coveredLogCount) / stats.chargingEfficiencySplit.totalLogCount * 100) }}%</span>
                <span>Netto {{ Math.round(stats.chargingEfficiencySplit.coveredLogCount / stats.chargingEfficiencySplit.totalLogCount * 100) }}%</span>
              </div>
            </button>
          </div>
          <!-- Tooltip panel below grid - shared for all three info metrics -->
          <div v-if="openMetricTooltip && ['distance','consumption','costPer100km'].includes(openMetricTooltip)"
            class="mt-1 px-4 py-3 rounded-lg bg-gray-50 dark:bg-gray-900/50 border border-gray-200 dark:border-gray-700 text-xs text-gray-700 dark:text-gray-300 leading-relaxed space-y-1.5">
            <template v-if="openMetricTooltip === 'distance'">
              <p>{{ t('dashboard.metric_total_distance_tooltip') }}</p>
            </template>
            <template v-else-if="openMetricTooltip === 'consumption'">
              <p>{{ t('dashboard.metric_avg_consumption_tooltip') }}</p>
            </template>
            <template v-else-if="openMetricTooltip === 'costPer100km'">
              <p>{{ t('dashboard.metric_avg_cost_tooltip') }}</p>
            </template>
            <p class="italic text-gray-500 dark:text-gray-400">{{ t('dashboard.metric_complete_definition') }}</p>
            <router-link to="/consumption-methodology" class="inline-flex items-center gap-1 text-xs font-medium text-indigo-600 dark:text-indigo-400 underline underline-offset-2">
              {{ t('dashboard.metric_consumption_methodology_link') }}
              <ChevronRightIcon class="w-3 h-3" />
            </router-link>
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
          <div class="bg-white dark:bg-gray-700 rounded-sm border-2 border-gray-300 dark:border-gray-600 shadow-[2px_2px_0_0_#d1d5db] dark:shadow-[2px_2px_0_0_#374151] overflow-hidden">
            <div class="p-3">
              <p class="text-xs text-gray-500 dark:text-gray-400 font-medium mb-1">{{ t('dashboard.metric_total_energy') }}</p>
              <p class="text-xl font-bold text-gray-900 dark:text-gray-100">{{ stats.totalKwhCharged?.toFixed(1) ?? '–' }} kWh</p>
              <p class="text-sm font-medium text-gray-400 dark:text-gray-500 mt-0.5">{{ stats.totalCharges }} {{ t('dashboard.metric_charges') }}</p>
            </div>
          </div>
          <div v-if="stats.avgConsumptionKwhPer100km != null"
            class="bg-white dark:bg-gray-700 rounded-sm border-2 border-gray-300 dark:border-gray-600 shadow-[2px_2px_0_0_#d1d5db] dark:shadow-[2px_2px_0_0_#374151] overflow-hidden">
            <div class="p-3">
              <div class="flex items-center gap-1 mb-1">
                <p class="text-xs text-gray-500 dark:text-gray-400 font-medium">{{ t('dashboard.metric_avg_consumption') }}</p>
                <button type="button"
                  class="flex-shrink-0 text-gray-400 hover:text-gray-600 dark:text-gray-500 dark:hover:text-gray-300 focus:outline-none focus:ring-2 focus:ring-red-500 rounded"
                  :aria-label="t('dashboard.metric_info_aria')"
                  @click.stop="openMetricTooltip = openMetricTooltip === 'consumption' ? null : 'consumption'">
                  <InformationCircleIcon class="w-3.5 h-3.5" />
                </button>
              </div>
              <p class="text-xl font-bold text-gray-900 dark:text-gray-100">{{ formatConsumption(stats.avgConsumptionKwhPer100km, { showUnit: false }) }}</p>
              <p class="text-sm font-medium text-gray-400 dark:text-gray-500 mt-0.5">{{ consumptionUnitLabel() }}</p>
              <div class="mt-2 space-y-0.5">
                <div v-if="stats.chargingEfficiencySplit && stats.chargingEfficiencySplit.totalLogCount > 0" class="flex gap-2 text-[10px] text-gray-400 dark:text-gray-500">
                  <span>Brutto {{ Math.round((stats.chargingEfficiencySplit.totalLogCount - stats.chargingEfficiencySplit.coveredLogCount) / stats.chargingEfficiencySplit.totalLogCount * 100) }}%</span>
                  <span>Netto {{ Math.round(stats.chargingEfficiencySplit.coveredLogCount / stats.chargingEfficiencySplit.totalLogCount * 100) }}%</span>
                </div>
              </div>
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
          <div class="bg-white dark:bg-gray-700 rounded-sm border-2 border-gray-300 dark:border-gray-600 shadow-[2px_2px_0_0_#d1d5db] dark:shadow-[2px_2px_0_0_#374151] overflow-hidden">
            <div class="p-3">
              <CostBreakdownCard
                layout="inline"
                :energy-cost-eur="stats.energyCostEur"
                :fixed-cost-eur="stats.fixedCostEur"
                :fixed-income-eur="stats.fixedIncomeEur"
                :total-cost-eur="stats.totalCostEur"
              />
            </div>
          </div>
          <div v-if="stats.totalDistanceKm != null"
            class="bg-white dark:bg-gray-700 rounded-sm border-2 border-gray-300 dark:border-gray-600 shadow-[2px_2px_0_0_#d1d5db] dark:shadow-[2px_2px_0_0_#374151] overflow-hidden">
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
          <div v-if="avgCostPer100km != null"
            class="bg-white dark:bg-gray-700 rounded-sm border-2 border-gray-300 dark:border-gray-600 shadow-[2px_2px_0_0_#d1d5db] dark:shadow-[2px_2px_0_0_#374151] overflow-hidden">
            <div class="p-3">
              <div class="flex items-center gap-1 mb-1">
                <p class="text-xs text-gray-500 dark:text-gray-400 font-medium whitespace-nowrap">{{ costModeLabel }}</p>
                <button
                  type="button"
                  class="flex-shrink-0 text-gray-400 hover:text-gray-600 dark:text-gray-500 dark:hover:text-gray-300 focus:outline-none focus:ring-2 focus:ring-pink-500 rounded"
                  :aria-label="t('dashboard.metric_info_aria')"
                  @click.stop="openMetricTooltip = openMetricTooltip === 'costPer100km' ? null : 'costPer100km'">
                  <InformationCircleIcon class="w-3.5 h-3.5" />
                </button>
                <CostModeToggle
                  v-if="canShowFixedModes"
                  :mode="effectiveCostMode"
                  class="ml-auto"
                  @toggle="toggleCostMode"
                />
              </div>
              <p class="text-xl font-bold text-gray-900 dark:text-gray-100">{{ formatCostPerDistance(displayedCostPer100km!) }}</p>
              <div v-if="costModeSecondary" class="mt-2 pt-2 border-t border-gray-100 dark:border-gray-700">
                <p class="text-xl font-bold text-gray-900 dark:text-gray-100">{{ costModeSecondary }}</p>
              </div>
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

        <!-- Kostenverlauf -->
        <CostHistoryCard :car-id="selectedCarId" class="mb-6" />

        <!-- Insights: Energie-Split · Standverluste · Fahrten-Kalender (AutoSync Live only) -->
        <DashboardInsights
          v-if="authStore.canViewEnergySplit && mergedLogFeed.length > 0"
          :entries="mergedLogFeed"
          :selected-car="cars.find((c: any) => c.id === selectedCarId)"
          :selected-time-range="selectedTimeRange"
          :custom-start-date="customStartDate"
          :custom-end-date="customEndDate"
          :avg-cost-per-kwh="stats.avgCostPerKwh"
          :trial="authStore.energySplitViaTrial"
          :trial-ends-at="authStore.energySplitTrialEndsAt"
          :upsell-target="savingsUpsellTarget"
          class="mb-3"
        />

        <!-- Locked-state teaser: same slot, shown to users without the analytics entitlement -->
        <DashboardInsightsTeaser
          v-if="!authStore.canViewEnergySplit && feedHasTrips"
          :entries="mergedLogFeed"
          class="mb-3"
        />

        <!-- Heimlade-Ersparnis: volle Breite, weil die Skala sie braucht - ihre Breite
             ist die Aussage. Ausserhalb des Rasters, damit dessen Zeilenhoehe nicht an
             dieser Kachel haengt und kein Loch entsteht, wenn der Tarif sie nicht enthaelt. -->
        <ChargingSavingsCard
          v-if="chargingSavingsEntitled && chargingSavings && !chargingSavingsDismissed"
          :savings="chargingSavings"
          :trial="chargingSavingsViaTrial"
          :trial-ends-at="chargingSavingsTrialEndsAt"
          :upsell-target="savingsUpsellTarget"
          class="mb-4"
          @edit-investment="showInvestmentPrompt = true"
          @dismiss="dismissSavingsCard"
        />
        <!-- Kurzlebige Undo-Zeile nach dem Ausblenden: tritt an die Stelle der Kachel und
             verschwindet von selbst. Dauerhaft wieder einblendbar in den Einstellungen. -->
        <div v-else-if="savingsUndoVisible"
             class="mb-4 flex items-center justify-between gap-3 rounded-sm border border-gray-200 dark:border-gray-600 bg-white dark:bg-gray-700 px-4 py-3 text-xs text-gray-500 dark:text-gray-400">
          <span class="min-w-0">{{ t('savings.dismissed_notice') }}</span>
          <button type="button" class="flex-none font-semibold text-emerald-700 dark:text-emerald-300 hover:underline"
                  @click="restoreSavingsCard">
            {{ t('savings.undo') }}
          </button>
        </div>
        <!-- Probemonat vorbei, kein bezahlter Tarif: der Teaser tritt an die Stelle der
             Kachel - Muster wie DashboardInsights/-Teaser. -->
        <ChargingSavingsCardTeaser v-else-if="chargingSavingsLocked && !chargingSavingsDismissed"
                                   class="mb-4" @dismiss="dismissSavingsCard" />
        <HomeInvestmentModal
          :open="showInvestmentPrompt"
          :current="chargingSavings?.investmentEur ?? null"
          @close="showInvestmentPrompt = false"
          @save="saveInvestment"
        />

        <!-- Echte Reichweite + Peer Benchmark: mobile gestackt, desktop nebeneinander.
             items-start, damit kurze Kacheln nicht auf die Zeilenhoehe gedehnt werden. -->
        <div class="mb-0 grid grid-cols-1 md:grid-cols-3 gap-4 items-start">

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

        <!-- Peer Benchmark (old) — können wir später löschen -->
        <PeerBenchmarkCard
          v-if="stats?.peerBenchmark && stats.peerBenchmark.peerAvgConsumptionKwhPer100km != null"
          :benchmark="stats.peerBenchmark"
          :effective-battery-kwh="selectedCar?.effectiveBatteryCapacityKwh ?? null"
          :car-model="enumToLabel(selectedCar?.model)"
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

        <!-- Peer Model Comparison: Volle Breite -->
        <PeerModelComparisonCard
          v-if="selectedCarId && stats?.peerBenchmark && stats.peerBenchmark.peerAvgConsumptionKwhPer100km != null"
          :car-id="selectedCarId"
          :car-display-name="selectedCar ? [carDisplayName(selectedCar.brand, selectedCar.model), selectedCar.trim].filter(Boolean).join(' ') : ''"
          :car-brand-model="selectedCar ? carDisplayName(selectedCar.brand, selectedCar.model) : ''"
          class="mb-4"
        />

        <!-- Chart 1: Charging & Costs -->
        <div class="border-t border-gray-200 dark:border-gray-600 pt-6 mt-2 -mx-4 px-4 bg-gray-50 dark:bg-gray-800/50">
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
        <div v-if="hasDistanceData" class="border-t border-gray-200 dark:border-gray-600 pt-6 mt-2 -mx-4 px-4 bg-gray-50 dark:bg-gray-800/50">
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
        <div v-if="wltp && hasDistanceData && wltpChartData" class="border-t border-gray-200 dark:border-gray-600 pt-6 mt-2 -mx-4 px-4 bg-gray-50 dark:bg-gray-800/50">
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
        <div class="border-t border-gray-200 dark:border-gray-600 pt-6 mt-2 -mx-4 px-4 bg-gray-50 dark:bg-gray-800/50">
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

  <EditTripModal
    v-if="tripModalId"
    ref="tripSheet"
    v-model="tripForm"
    :error="tripError"
    :saving="tripSaving"
    @save="submitTripEdit"
    @close="onTripSheetClosed"
  />

  <!-- Battery health detail sheet. `changed` reloads the cars so the chip on the card
       reflects a newly added or corrected value without a page reload. -->
  <BatterySohModal
    v-if="sohModalCar"
    :car="sohModalCar"
    @changed="initCars()"
    @close="sohModalCar = null"
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
</style>
