import { ref, watch, computed, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import api from '../api/axios'
import { useCarStore } from '../stores/car'
import { useAuthStore } from '../stores/auth'
import { carService } from '../api/carService'
import { vehicleSpecificationService, type VehicleSpecification } from '../api/vehicleSpecificationService'
import { useTeslaStatus } from './useTeslaStatus'
import { useSmartcarStatus } from './useSmartcarStatus'

export interface ChargeDataPoint {
  timestamp: string
  costEur: number
  kwhCharged: number
  distanceKm: number | null
  consumptionKwhPer100km: number | null
}

export interface StatisticsData {
  totalKwhCharged: number
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
}

export interface CarInfo {
  id: string
  brand: string
  model: string
  batteryCapacityKwh: number
}

const LS_TIME_RANGE = 'dashboard_time_range'
const LS_GROUP_BY = 'dashboard_group_by'
const LS_CUSTOM_START = 'dashboard_custom_start'
const LS_CUSTOM_END = 'dashboard_custom_end'

export function useDashboardStats() {
  const { t } = useI18n()
  const carStore = useCarStore()
  const authStore = useAuthStore()

  const selectedCarId = ref<string | null>(null)
  const stats = ref<StatisticsData | null>(null)
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

  const { teslaStatus, start: startTeslaPolling } = useTeslaStatus()
  const { smartcarStatus, start: startSmartcarPolling } = useSmartcarStatus()

  // Implausible logs
  const implausibleCount = ref(0)

  const hasDistanceData = computed(() =>
    stats.value?.chargesOverTime?.some(d => d.distanceKm != null) ?? false
  )

  const timeRangeOptions = computed(() => [
    { value: 'THIS_MONTH', label: t('dashboard.time_this_month') },
    { value: 'LAST_MONTH', label: t('dashboard.time_last_month') },
    { value: 'LAST_3_MONTHS', label: t('dashboard.time_last_3m') },
    { value: 'LAST_6_MONTHS', label: t('dashboard.time_last_6m') },
    { value: 'LAST_12_MONTHS', label: t('dashboard.time_last_12m') },
    { value: 'THIS_YEAR', label: t('dashboard.time_this_year') },
    { value: 'ALL_TIME', label: t('dashboard.time_all') },
    { value: 'CUSTOM', label: t('dashboard.time_custom') }
  ])

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
        batteryCapacityKwh: car.batteryCapacityKwh
      }
      wltp.value = await vehicleSpecificationService.lookup(car.brand, car.model, car.batteryCapacityKwh)
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
      const response = await api.get(`/logs/statistics?${params}`)
      stats.value = response.data
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
    teslaStatus,
    smartcarStatus,
    implausibleCount,
    hasDistanceData,
    timeRangeOptions,
    groupByOptions,
    dismissImportBanner,
    fetchImplausibleCount,
    fetchCarAndWltp,
    fetchStatistics,
    initCars,
  }
}
