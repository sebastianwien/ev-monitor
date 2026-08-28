import { ref, watch, computed, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import api from '../api/axios'
import { useCarStore } from '../stores/car'
import { useAuthStore } from '../stores/auth'
import { carService } from '../api/carService'
import { vehicleSpecificationService, type VehicleSpecification } from '../api/vehicleSpecificationService'
import { useTeslaStatus } from './useTeslaStatus'
import { useSmartcarStatus } from './useSmartcarStatus'
import { useVwGroupStatus } from './useVwGroupStatus'
import { isVwGroupBrand } from '../api/vwGroupService'

export interface ChargeDataPoint {
  timestamp: string
  costEur: number
  kwhCharged: number
  distanceKm: number | null
  consumptionKwhPer100km: number | null
}

export interface PeerBenchmark {
  userLifetimeConsumptionKwhPer100km: number | null
  peerAvgConsumptionKwhPer100km: number | null
  userLifetimeCostPerKwh: number | null
  peerAvgCostPerKwh: number | null
  uniquePeerUsers: number
  peerTripCount: number
  peerLogCount: number
  matchType: 'SPEC' | 'MODEL'
}

export interface ChargingTypeSplit {
  acKwh: number
  dcKwh: number
  unknownKwh: number
}

export interface LocationSplit {
  publicKwh: number
  privateKwh: number
}

export interface ChargingEfficiencySplit {
  gridKwh: number
  vehicleKwh: number
  coveredLogCount: number
  totalLogCount: number
}

export interface StatisticsData {
  totalKwhCharged: number
  energyCostEur: number
  fixedCostEur: number
  fixedIncomeEur: number
  totalCostEur: number
  avgCostPerKwh: number
  cheapestChargeEur: number
  mostExpensiveChargeEur: number
  avgChargeDurationMinutes: number
  totalCharges: number
  totalDistanceKm: number | null
  avgConsumptionKwhPer100km: number | null
  estimatedConsumptionCount: number
  summerConsumptionKwhPer100km: number | null
  winterConsumptionKwhPer100km: number | null
  chargesOverTime: ChargeDataPoint[]
  chargingTypeSplit: ChargingTypeSplit | null
  locationSplit: LocationSplit | null
  peerBenchmark: PeerBenchmark | null
  chargingEfficiencySplit: ChargingEfficiencySplit | null
}

export interface CarInfo {
  id: string
  brand: string
  model: string
  customNetCapacityKwh: number | null
}

export function calcCostPer100km(
  avgCostPerKwh: number | null,
  avgConsumptionKwhPer100km: number | null,
): number | null {
  if (avgCostPerKwh == null || avgConsumptionKwhPer100km == null) return null
  return avgCostPerKwh * avgConsumptionKwhPer100km
}

/**
 * Vollkosten pro 100 km: Energiekosten plus die Fixkosten des Zeitraums, umgelegt auf die
 * gefahrene Strecke. Die Energiekosten kommen bewusst aus calcCostPer100km (avgCostPerKwh x
 * Verbrauch) - Kosten durch Distanz zu teilen faellt bei Ladungen ohne Tachostand auseinander.
 * Fixkosten gelten dagegen fuer den Zeitraum als Ganzes, deshalb ist die Zeitraum-Distanz hier
 * die richtige Bezugsgroesse.
 */
export function calcFullCostPer100km(
  energyCostPer100km: number | null,
  fixedCostEur: number | null,
  fixedIncomeEur: number | null,
  distanceKm: number | null,
): number | null {
  if (energyCostPer100km == null) return null
  if (distanceKm == null || distanceKm <= 0) return null
  const fixedNet = (fixedCostEur ?? 0) - (fixedIncomeEur ?? 0)
  return energyCostPer100km + (fixedNet / distanceKm) * 100
}

const LS_TIME_RANGE = 'dashboard_time_range'
const LS_GROUP_BY = 'dashboard_group_by'
const LS_CUSTOM_START = 'dashboard_custom_start'
const LS_CUSTOM_END = 'dashboard_custom_end'
const LS_IMPLAUSIBLE_BANNER_DISMISSED = 'implausible_banner_dismissed'
const LS_COST_MODE = 'dashboard_cost_mode'

export type CostMode = 'energy' | 'full'

export function useDashboardStats() {
  const { t, locale } = useI18n()
  const carStore = useCarStore()
  const authStore = useAuthStore()

  const selectedCarId = ref<string | null>(null)
  const stats = ref<StatisticsData | null>(null)
  const lastMonthStats = ref<StatisticsData | null>(null)
  const thisMonthStats = ref<StatisticsData | null>(null)
  const insightStats = ref<StatisticsData | null>(null)
  const carInfo = ref<CarInfo | null>(null)
  const wltp = ref<VehicleSpecification | null>(null)
  const loading = ref(true)
  const chartsReady = ref(false)
  const isInitialLoad = ref(true)
  const error = ref<string | null>(null)
  const cars = ref<any[]>([])
  const carImageUrls = ref<Record<string, string>>({})

  const selectedTimeRange = ref<string>(localStorage.getItem(LS_TIME_RANGE) ?? 'LAST_3_MONTHS')
  const selectedGroupBy = ref<string>(localStorage.getItem(LS_GROUP_BY) ?? 'DAY')
  const customStartDate = ref<string>(localStorage.getItem(LS_CUSTOM_START) ?? '')
  const customEndDate = ref<string>(localStorage.getItem(LS_CUSTOM_END) ?? '')

  const importBannerDismissed = ref(localStorage.getItem('import_banner_dismissed') === 'true')
  const implausibleBannerDismissed = ref(localStorage.getItem(LS_IMPLAUSIBLE_BANNER_DISMISSED) === 'true')

  const { teslaStatus, start: startTeslaPolling } = useTeslaStatus()
  const { smartcarStatus, start: startSmartcarPolling } = useSmartcarStatus()
  const { vwGroupStatus, start: startVwGroupPolling } = useVwGroupStatus()

  // Implausible logs
  const implausibleCount = ref(0)

  const hasDistanceData = computed(() =>
    stats.value?.chargesOverTime?.some(d => d.distanceKm != null && d.distanceKm > 0) ?? false
  )

  const avgCostPer100km = computed(() =>
    calcCostPer100km(
      stats.value?.avgCostPerKwh ?? null,
      stats.value?.avgConsumptionKwhPer100km ?? null,
    )
  )

  /** Fixkosten oder Einnahmen im Zeitraum vorhanden - erst dann lohnt der Vollkosten-Umschalter. */
  const hasFixedCostData = computed(() =>
    !!stats.value?.fixedCostEur || !!stats.value?.fixedIncomeEur
  )

  const costMode = ref<CostMode>(
    localStorage.getItem(LS_COST_MODE) === 'full' ? 'full' : 'energy'
  )

  function setCostMode(mode: CostMode) {
    costMode.value = mode
    localStorage.setItem(LS_COST_MODE, mode)
  }

  function toggleCostMode() {
    setCostMode(costMode.value === 'full' ? 'energy' : 'full')
  }

  const fullCostPer100km = computed(() =>
    calcFullCostPer100km(
      avgCostPer100km.value,
      stats.value?.fixedCostEur ?? null,
      stats.value?.fixedIncomeEur ?? null,
      stats.value?.totalDistanceKm ?? null,
    )
  )

  /** Der im Widget angezeigte Wert - faellt auf die Energiekosten zurueck, wenn keine Fixkosten da sind. */
  const displayedCostPer100km = computed(() =>
    costMode.value === 'full' && fullCostPer100km.value != null
      ? fullCostPer100km.value
      : avgCostPer100km.value
  )

  const timeRangeOptions = computed(() => {
    const now = new Date()
    const mo = (offsetMonths: number) =>
      new Date(now.getFullYear(), now.getMonth() + offsetMonths)
    const fmt = (date: Date) =>
      date.toLocaleDateString(locale.value, { month: 'short' }).replace('.', '')
    const fmtShort = (date: Date) =>
      fmt(date) + '\'' + String(date.getFullYear()).slice(2)
    const thisYear = String(now.getFullYear())
    // range labels: "Mär-Mai", "Nov-Mai", "Mai'25-Mai'26"
    const range3  = `${fmt(mo(-2))}-${fmt(mo(0))}`
    const range6  = `${fmt(mo(-5))}-${fmt(mo(0))}`
    const range12 = `${fmtShort(mo(-11))}-${fmtShort(mo(0))}`
    return [
      { value: 'THIS_MONTH',    label: t('dashboard.time_this_month'), shortLabel: fmt(mo(0)) },
      { value: 'LAST_MONTH',    label: t('dashboard.time_last_month'), shortLabel: fmt(mo(-1)) },
      { value: 'LAST_3_MONTHS',  label: t('dashboard.time_last_3m'),   shortLabel: range3 },
      { value: 'LAST_6_MONTHS',  label: t('dashboard.time_last_6m'),   shortLabel: range6 },
      { value: 'LAST_12_MONTHS', label: t('dashboard.time_last_12m'),  shortLabel: range12 },
      { value: 'THIS_YEAR',     label: t('dashboard.time_this_year'),  shortLabel: thisYear },
      { value: 'ALL_TIME',      label: t('dashboard.time_all'),        shortLabel: t('dashboard.time_all') },
      { value: 'CUSTOM',        label: t('dashboard.time_custom'),     shortLabel: '' },
    ]
  })

  const groupByOptions = computed(() => [
    { value: 'DAY', label: t('dashboard.group_day') },
    { value: 'WEEK', label: t('dashboard.group_week') },
    { value: 'MONTH', label: t('dashboard.group_month') }
  ])

  const dismissImportBanner = (e: Event) => {
    e.preventDefault()
    importBannerDismissed.value = true
    localStorage.setItem('import_banner_dismissed', 'true')
  }

  const dismissImplausibleBanner = () => {
    implausibleBannerDismissed.value = true
    localStorage.setItem(LS_IMPLAUSIBLE_BANNER_DISMISSED, 'true')
  }

  const fetchImplausibleCount = async () => {
    if (!selectedCarId.value) { implausibleCount.value = 0; return }
    try {
      const res = await api.get(`/logs/implausible?carId=${selectedCarId.value}`)
      implausibleCount.value = res.data.filter((l: any) => l.includeInStatistics).length
    } catch {
      implausibleCount.value = 0
    }
  }

  const fetchCarAndWltp = async (carId: string) => {
    try {
      const car = cars.value.find((c: any) => c.id === carId)
      if (!car) return
      carInfo.value = {
        id: car.id,
        brand: car.brand,
        model: car.model,
        customNetCapacityKwh: car.customNetCapacityKwh
      }
      wltp.value = car.vehicleSpecificationId
        ? await vehicleSpecificationService.lookupById(car.vehicleSpecificationId)
        : car.customNetCapacityKwh != null
          ? await vehicleSpecificationService.lookup(car.brand, car.model, car.customNetCapacityKwh)
          : null
    } catch {
      wltp.value = null
    }
  }

  const fetchStatistics = async () => {
    if (!selectedCarId.value) {
      stats.value = null
      loading.value = false
      chartsReady.value = false
      return
    }
    try {
      loading.value = true
      if (isInitialLoad.value) {
        chartsReady.value = false
      }
      error.value = null
      const params = new URLSearchParams({
        carId: selectedCarId.value,
        groupBy: selectedGroupBy.value
      })
      if (selectedTimeRange.value === 'CUSTOM') {
        if (!customStartDate.value || !customEndDate.value) {
          loading.value = false
          chartsReady.value = true
          return
        }
        params.set('startDate', customStartDate.value)
        params.set('endDate', customEndDate.value)
      } else {
        params.set('timeRange', selectedTimeRange.value)
      }
      const [response, lastMonthResponse, thisMonthResponse] = await Promise.all([
        api.get(`/logs/statistics?${params}`),
        api.get(`/logs/statistics?carId=${selectedCarId.value}&timeRange=LAST_MONTH&groupBy=MONTH`).catch(() => null),
        selectedTimeRange.value !== 'THIS_MONTH'
          ? api.get(`/logs/statistics?carId=${selectedCarId.value}&timeRange=THIS_MONTH&groupBy=MONTH`).catch(() => null)
          : Promise.resolve(null),
      ])
      stats.value = response.data
      lastMonthStats.value = lastMonthResponse?.data ?? null
      thisMonthStats.value = selectedTimeRange.value === 'THIS_MONTH' ? stats.value : (thisMonthResponse?.data ?? null)
      insightStats.value = thisMonthStats.value
    } catch (err: any) {
      error.value = err.response?.data?.message || t('dashboard.err_load')
    } finally {
      loading.value = false
      if (isInitialLoad.value) {
        setTimeout(() => {
          chartsReady.value = true
          isInitialLoad.value = false
        }, 300)
      } else {
        chartsReady.value = true
      }
    }
  }

  const revokeCarImageUrls = () => {
    Object.values(carImageUrls.value).forEach(url => URL.revokeObjectURL(url))
    carImageUrls.value = {}
  }

  const initCars = async () => {
    try {
      const carList = await carStore.getCars()
      cars.value = carList
      const primary = carList.find((c: any) => c.isPrimary) ?? carList[0]
      if (primary) {
        selectedCarId.value = primary.id
      } else {
        loading.value = false
      }
      revokeCarImageUrls()
      for (const car of carList.filter((c: any) => c.imageUrl)) {
        carService.getCarImageBlobUrl(car.id)
          .then(url => { carImageUrls.value = { ...carImageUrls.value, [car.id]: url } })
          .catch(() => {})
      }
      const hasTesla = carList.some((c: any) => c.brand?.toLowerCase() === 'tesla')
      startTeslaPolling(hasTesla)
      startSmartcarPolling(carList.length > 0 && authStore.isPremium)
      const activeCar = carList.find((c: any) => c.active) ?? carList[0]
      const vwBrand = activeCar && isVwGroupBrand(activeCar.brand)
        ? activeCar.brand?.toLowerCase() ?? null : null
      if (authStore.isPremium && authStore.isBetaTester) startVwGroupPolling(vwBrand)
    } catch { /* non-critical */ }
  }

  onUnmounted(() => {
    revokeCarImageUrls()
  })

  // Watchers
  watch([selectedTimeRange, selectedGroupBy], () => {
    localStorage.setItem(LS_TIME_RANGE, selectedTimeRange.value)
    localStorage.setItem(LS_GROUP_BY, selectedGroupBy.value)
    if (selectedCarId.value) fetchStatistics()
  })

  watch([customStartDate, customEndDate], () => {
    localStorage.setItem(LS_CUSTOM_START, customStartDate.value)
    localStorage.setItem(LS_CUSTOM_END, customEndDate.value)
    if (selectedCarId.value && selectedTimeRange.value === 'CUSTOM') fetchStatistics()
  })

  return {
    selectedCarId,
    stats,
    lastMonthStats,
    thisMonthStats,
    insightStats,
    carInfo,
    wltp,
    loading,
    chartsReady,
    isInitialLoad,
    error,
    cars,
    carImageUrls,
    selectedTimeRange,
    selectedGroupBy,
    customStartDate,
    customEndDate,
    importBannerDismissed,
    implausibleBannerDismissed,
    teslaStatus,
    smartcarStatus,
    vwGroupStatus,
    implausibleCount,
    hasDistanceData,
    avgCostPer100km,
    fullCostPer100km,
    displayedCostPer100km,
    hasFixedCostData,
    costMode,
    toggleCostMode,
    timeRangeOptions,
    groupByOptions,
    dismissImportBanner,
    dismissImplausibleBanner,
    fetchImplausibleCount,
    fetchCarAndWltp,
    fetchStatistics,
    initCars,
  }
}
