<script setup lang="ts">
import { ref, onMounted, watch, computed } from 'vue'
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
  CameraIcon,
  PencilSquareIcon,
  ArrowDownTrayIcon
} from '@heroicons/vue/24/outline'
import { useRouter } from 'vue-router'
import api from '../api/axios'
import CarSelector from '../components/CarSelector.vue'
import ChargingHeatMap from '../components/ChargingHeatMap.vue'
import { vehicleSpecificationService, type VehicleSpecification } from '../api/vehicleSpecificationService'

ChartJS.register(CategoryScale, LinearScale, BarElement, PointElement, LineElement, Title, Tooltip, Legend, Filler, ChartDataLabels)

interface ChargeDataPoint {
  timestamp: string
  costEur: number
  kwhCharged: number
  distanceKm: number | null
  consumptionKwhPer100km: number | null
}

interface StatisticsData {
  totalKwhCharged: number
  totalCostEur: number
  avgCostPerKwh: number
  cheapestChargeEur: number
  mostExpensiveChargeEur: number
  avgChargeDurationMinutes: number
  totalCharges: number
  totalDistanceKm: number | null
  avgConsumptionKwhPer100km: number | null
  chargesOverTime: ChargeDataPoint[]
}

interface CarInfo {
  id: string
  brand: string
  model: string
  batteryCapacityKwh: number
}

const router = useRouter()
const selectedCarId = ref<string | null>(null)
const stats = ref<StatisticsData | null>(null)
const carInfo = ref<CarInfo | null>(null)
const wltp = ref<VehicleSpecification | null>(null)
const loading = ref(true) // Initial true to prevent flicker on mount
const chartsReady = ref(false) // Charts render after fade-in
const isInitialLoad = ref(true) // Track if this is the first load
const error = ref<string | null>(null)
const cars = ref<any[]>([]) // Track available cars for empty state

const selectedTimeRange = ref<string>('LAST_3_MONTHS')
const selectedGroupBy = ref<string>('DAY')

// Dataset toggles - Chart 1 (Charging & Costs)
const showCostPerKwh = ref(true)
const showKwh = ref(true)

// Dataset toggles - Chart 2 (Range & Efficiency)
const showDistance = ref(true)
const showConsumption = ref(true)

const timeRangeOptions = [
  { value: 'THIS_MONTH', label: 'Dieser Monat' },
  { value: 'LAST_MONTH', label: 'Letzter Monat' },
  { value: 'LAST_3_MONTHS', label: 'Letzte 3 Monate' },
  { value: 'LAST_6_MONTHS', label: 'Letzte 6 Monate' },
  { value: 'LAST_12_MONTHS', label: 'Letztes Jahr' },
  { value: 'THIS_YEAR', label: 'Dieses Jahr' },
  { value: 'ALL_TIME', label: 'Gesamt' }
]

const groupByOptions = [
  { value: 'DAY', label: 'Täglich' },
  { value: 'WEEK', label: 'Wöchentlich' },
  { value: 'MONTH', label: 'Monatlich' }
]

// Fetch car details + WLTP when car changes
const fetchCarAndWltp = async (carId: string) => {
  try {
    const carsResponse = await api.get('/cars')
    cars.value = carsResponse.data // Store for empty state check
    const car = carsResponse.data.find((c: any) => c.id === carId)
    if (!car) return

    carInfo.value = {
      id: car.id,
      brand: car.brand,
      model: car.model,
      batteryCapacityKwh: car.batteryCapacityKwh
    }

    wltp.value = await vehicleSpecificationService.lookup(
      car.brand,
      car.model,
      car.batteryCapacityKwh
    )
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
    // Only hide charts on initial load, keep them visible on filter changes
    if (isInitialLoad.value) {
      chartsReady.value = false
    }
    error.value = null
    const params = new URLSearchParams({
      carId: selectedCarId.value,
      timeRange: selectedTimeRange.value,
      groupBy: selectedGroupBy.value
    })
    const response = await api.get(`/logs/statistics?${params}`)
    stats.value = response.data
  } catch (err: any) {
    error.value = err.response?.data?.message || 'Fehler beim Laden der Statistiken'
  } finally {
    loading.value = false
    // Delay only on initial load, immediate on filter changes
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

watch(selectedCarId, async (newId) => {
  if (newId) {
    await fetchCarAndWltp(newId)
    await fetchStatistics()
  } else {
    stats.value = null
    carInfo.value = null
    wltp.value = null
  }
})

watch([selectedTimeRange, selectedGroupBy], () => {
  if (selectedCarId.value) fetchStatistics()
})

const hasDistanceData = computed(() =>
  stats.value?.chargesOverTime.some(d => d.distanceKm != null) ?? false
)

const formatLabel = (timestamp: string) => {
  const date = new Date(timestamp)
  if (selectedGroupBy.value === 'DAY')
    return date.toLocaleDateString('de-DE', { month: 'short', day: 'numeric' })
  if (selectedGroupBy.value === 'WEEK')
    return `KW ${Math.ceil(date.getDate() / 7)} ${date.toLocaleDateString('de-DE', { month: 'short' })}`
  return date.toLocaleDateString('de-DE', { month: 'short', year: 'numeric' })
}

// ── Chart 1: Charging & Costs ───────────────────────────────────────────────
const chargingChartData = computed(() => {
  if (!stats.value || stats.value.chargesOverTime.length === 0) return null

  const labels = stats.value.chargesOverTime.map(d => formatLabel(d.timestamp))
  const datasets: any[] = []

  if (showCostPerKwh.value) {
    datasets.push({
      label: 'Kosten (€/kWh)',
      data: stats.value.chargesOverTime.map(d =>
        d.kwhCharged > 0 ? +(d.costEur / d.kwhCharged).toFixed(3) : null
      ),
      borderColor: '#4f46e5',
      backgroundColor: 'rgba(79,70,229,0.08)',
      tension: 0.3, fill: false, pointRadius: 4, pointHoverRadius: 6, yAxisID: 'y'
    })
  }

  if (showKwh.value) {
    datasets.push({
      label: 'Geladen (kWh)',
      data: stats.value.chargesOverTime.map(d => d.kwhCharged),
      borderColor: '#f59e0b',
      backgroundColor: 'rgba(245,158,11,0.08)',
      tension: 0.3, fill: false, pointRadius: 4, pointHoverRadius: 6, yAxisID: 'y1'
    })
  }

  return { labels, datasets }
})

const chargingChartOptions = computed(() => ({
  responsive: true,
  maintainAspectRatio: false,
  interaction: { mode: 'index' as const, intersect: false },
  plugins: {
    legend: { display: false },
    datalabels: { display: false },
    tooltip: {
      callbacks: {
        label: (ctx: any) => {
          const lbl = ctx.dataset.label
          const v = ctx.parsed.y
          if (v == null) return `${lbl}: –`
          if (lbl.includes('€/kWh')) return `${lbl}: €${v.toFixed(2)}`
          if (lbl.includes('kWh')) return `${lbl}: ${v.toFixed(1)} kWh`
          return `${lbl}: ${v}`
        }
      }
    }
  },
  scales: {
    y: {
      type: 'linear' as const,
      position: 'left' as const,
      title: { display: true, text: '€/kWh' },
      beginAtZero: false,
      grid: { color: 'rgba(0,0,0,0.06)' }
    },
    y1: {
      type: 'linear' as const,
      position: 'right' as const,
      title: { display: true, text: 'kWh' },
      beginAtZero: true,
      grid: { drawOnChartArea: false }
    }
  }
}))

// ── Chart 2: Range & Efficiency ──────────────────────────────────────────────
const efficiencyChartData = computed(() => {
  if (!stats.value || stats.value.chargesOverTime.length === 0 || !hasDistanceData.value) return null

  const labels = stats.value.chargesOverTime.map(d => formatLabel(d.timestamp))
  const datasets: any[] = []

  if (showConsumption.value) {
    datasets.push({
      label: 'Verbrauch (kWh/100km)',
      data: stats.value.chargesOverTime.map(d => d.consumptionKwhPer100km),
      borderColor: '#ef4444',
      backgroundColor: 'rgba(239,68,68,0.08)',
      tension: 0.3, fill: false, pointRadius: 4, pointHoverRadius: 6, yAxisID: 'y'
    })
  }

  if (showDistance.value) {
    datasets.push({
      label: 'Strecke (km)',
      data: stats.value.chargesOverTime.map(d => d.distanceKm),
      borderColor: '#10b981',
      backgroundColor: 'rgba(16,185,129,0.08)',
      tension: 0.3, fill: false, pointRadius: 4, pointHoverRadius: 6, yAxisID: 'y1'
    })
  }

  return { labels, datasets }
})

const efficiencyChartOptions = computed(() => ({
  responsive: true,
  maintainAspectRatio: false,
  interaction: { mode: 'index' as const, intersect: false },
  plugins: {
    legend: { display: false },
    datalabels: { display: false },
    tooltip: {
      callbacks: {
        label: (ctx: any) => {
          const lbl = ctx.dataset.label
          const v = ctx.parsed.y
          if (v == null) return `${lbl}: –`
          if (lbl.includes('kWh/100km')) return `${lbl}: ${v.toFixed(1)}`
          if (lbl.includes('km')) return `${lbl}: ${Math.round(v).toLocaleString('de-DE')} km`
          return `${lbl}: ${v}`
        }
      }
    }
  },
  scales: {
    y: {
      type: 'linear' as const,
      position: 'left' as const,
      title: { display: true, text: 'kWh/100km' },
      beginAtZero: false,
      grid: { color: 'rgba(0,0,0,0.06)' }
    },
    y1: {
      type: 'linear' as const,
      position: 'right' as const,
      title: { display: true, text: 'km' },
      beginAtZero: true,
      grid: { drawOnChartArea: false }
    }
  }
}))

// ── WLTP delta bar chart ─────────────────────────────────────────────────────
const wltpChartData = computed(() => {
  if (!stats.value || !wltp.value || !hasDistanceData.value) return null

  const wltpVal = wltp.value.wltpConsumptionKwhPer100km
  const points = stats.value.chargesOverTime.filter(d => d.consumptionKwhPer100km != null)
  if (points.length === 0) return null

  const labels = points.map(d => formatLabel(d.timestamp))
  const deltas = points.map(d => +(d.consumptionKwhPer100km! - wltpVal).toFixed(2))

  return {
    labels,
    datasets: [{
      label: 'Δ Verbrauch vs. WLTP (kWh/100km)',
      data: deltas,
      backgroundColor: deltas.map(v => v > 0 ? 'rgba(239,68,68,0.75)' : 'rgba(16,185,129,0.75)'),
      borderColor: deltas.map(v => v > 0 ? '#dc2626' : '#059669'),
      borderWidth: 1,
      borderRadius: 3,
    }]
  }
})

const wltpChartOptions = computed(() => {
  const dataPoints = wltpChartData.value?.labels?.length || 0
  // Dynamic bar thickness: thinner as more data points
  let barPercentage = 0.8
  let categoryPercentage = 0.9

  if (dataPoints >= 20) {
    barPercentage = 0.6
    categoryPercentage = 0.8
  } else if (dataPoints >= 10) {
    barPercentage = 0.7
    categoryPercentage = 0.85
  }

  return {
    indexAxis: 'y' as const, // Horizontal bars (like demographic pyramid)
    responsive: true,
    maintainAspectRatio: false,
    datasets: {
      bar: { barPercentage, categoryPercentage }
    },
    plugins: {
      legend: { display: false },
      datalabels: {
        align: 'end' as const,
        anchor: 'end' as const,
        color: '#374151',
        font: { weight: 'bold' as const, size: 12 },
        formatter: (value: number) => {
          const wltpVal = wltp.value?.wltpConsumptionKwhPer100km || 0
          const percentDiff = (value / wltpVal) * 100
          return `${percentDiff > 0 ? '+' : ''}${percentDiff.toFixed(1)}%`
        }
      },
      tooltip: {
        callbacks: {
          label: (ctx: any) => {
            const v = ctx.parsed.x // x for horizontal bars
            const sign = v > 0 ? '+' : ''
            const wltpVal = wltp.value?.wltpConsumptionKwhPer100km || 0
            const percentDiff = ((v / wltpVal) * 100).toFixed(1)
            return [
              `${sign}${v.toFixed(2)} kWh/100km vs. WLTP`,
              `WLTP: ${wltpVal.toFixed(1)} kWh/100km`,
              `Abweichung: ${sign}${percentDiff}%`
            ]
          }
        }
      }
    },
    scales: {
      x: {
        title: { display: true, text: 'Δ kWh/100km (+ = mehr als WLTP)' },
        grid: { color: (ctx: any) => ctx.tick.value === 0 ? '#6b7280' : 'rgba(0,0,0,0.06)' },
        ticks: {
          callback: (v: any) => `${v > 0 ? '+' : ''}${v}`
        }
      },
      y: {
        grid: { display: false }
      }
    }
  }
})

const formatDuration = (minutes: number) => {
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  return h > 0 ? `${h}h ${m}min` : `${m}min`
}

// Dynamic chart height: 35px per bar, minimum 400px, max 1150px (30 bars) then scroll
const wltpChartHeight = computed(() => {
  const dataPoints = wltpChartData.value?.labels?.length || 0
  const dynamicHeight = Math.max(400, dataPoints * 35 + 100)
  const maxHeight = 1150 // ~30 bars
  return `${Math.min(dynamicHeight, maxHeight)}px`
})

const wltpChartScrollable = computed(() => {
  const dataPoints = wltpChartData.value?.labels?.length || 0
  return dataPoints >= 30
})

onMounted(fetchStatistics)
</script>

<template>
  <div class="md:max-w-6xl md:mx-auto md:p-6">
    <Transition name="fade" mode="out-in">
      <div v-if="!loading">
        <div class="bg-white md:rounded-xl md:shadow-lg p-4 md:p-6">
          <div class="flex items-center gap-3 mb-6">
            <ChartBarIcon class="h-8 w-8 text-gray-700" />
            <h1 class="text-3xl font-bold text-gray-800">Statistiken & Analysen</h1>
          </div>

          <!-- Import Hint Banner -->
          <router-link
            to="/imports"
            class="flex items-center gap-3 bg-green-50 border border-green-200 rounded-lg px-4 py-3 mb-6 hover:bg-green-100 transition group"
          >
            <ArrowDownTrayIcon class="h-5 w-5 text-green-600 shrink-0" />
            <div class="flex-1 min-w-0">
              <span class="text-sm font-medium text-green-800">Ladevorgänge importieren</span>
              <span class="text-sm text-green-700 ml-1">— Sprit-Monitor, go-eCharger Cloud, OCPP Wallbox</span>
            </div>
            <span class="text-green-600 text-sm group-hover:translate-x-0.5 transition-transform">→</span>
          </router-link>

          <div class="mb-6">
            <CarSelector v-model="selectedCarId" />
          </div>

          <!-- Filters -->
          <div v-if="selectedCarId" class="mb-6 p-4 bg-gray-50 rounded-lg border border-gray-200">
            <div class="flex flex-col md:flex-row gap-4 items-start md:items-center justify-between">
              <div class="flex-1">
                <label class="block text-sm font-medium text-gray-700 mb-2">Zeitraum</label>
                <div class="flex flex-wrap gap-2">
                  <button
                    v-for="option in timeRangeOptions"
                    :key="option.value"
                    @click="selectedTimeRange = option.value"
                    :class="[
                      'px-3 py-1.5 rounded-md text-sm font-medium transition-colors',
                      selectedTimeRange === option.value
                        ? 'bg-indigo-600 text-white shadow-md'
                        : 'bg-white text-gray-700 border border-gray-300 hover:bg-gray-100'
                    ]">
                    {{ option.label }}
                  </button>
                </div>
              </div>
              <div class="w-full md:w-auto">
                <label class="block text-sm font-medium text-gray-700 mb-2">Gruppierung</label>
                <select v-model="selectedGroupBy"
                  class="block w-full md:w-auto px-4 py-2 border border-gray-300 rounded-md shadow-sm focus:ring-indigo-500 focus:border-indigo-500">
                  <option v-for="opt in groupByOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
                </select>
              </div>
            </div>
          </div>

          <div v-if="error" class="mb-4 p-4 bg-red-50 border border-red-200 text-red-700 rounded-md">{{ error }}</div>

          <!-- Empty State: No Cars -->
          <div v-if="cars.length === 0" class="min-h-[60vh] flex items-center justify-center">
            <div class="text-center max-w-md px-4">
              <TruckIcon class="h-24 w-24 mx-auto text-gray-300 mb-6" />
              <h2 class="text-2xl font-bold text-gray-800 mb-3">
                Noch kein Fahrzeug hinzugefügt
              </h2>
              <p class="text-gray-600 mb-8">
                Füge dein erstes E-Auto hinzu um Ladevorgänge zu tracken und Statistiken zu sehen.
              </p>
              <button
                @click="router.push('/cars')"
                class="px-6 py-3 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 font-medium shadow-lg hover:shadow-xl transition flex items-center gap-2 mx-auto">
                <TruckIcon class="h-5 w-5" />
                Fahrzeug hinzufügen →
              </button>
            </div>
          </div>

          <!-- Empty State: No Logs -->
          <div v-else-if="stats && stats.totalCharges === 0" class="min-h-[60vh] flex items-center justify-center">
            <div class="text-center max-w-md px-4">
              <BoltIcon class="h-24 w-24 mx-auto text-green-500 mb-6" />
              <h2 class="text-2xl font-bold text-gray-800 mb-3">
                Noch keine Ladevorgänge erfasst
              </h2>
              <p class="text-gray-600 mb-8">
                Erfasse deinen ersten Ladevorgang um Statistiken, Charts und WLTP-Vergleiche zu sehen!
              </p>
              <div class="flex flex-col sm:flex-row gap-4 justify-center">
                <button
                  @click="router.push('/erfassen')"
                  class="px-6 py-3 bg-green-600 text-white rounded-lg hover:bg-green-700 flex items-center gap-2 justify-center shadow-lg hover:shadow-xl transition font-medium">
                  <CameraIcon class="h-5 w-5" />
                  Foto scannen
                </button>
                <button
                  @click="router.push('/erfassen')"
                  class="px-6 py-3 border-2 border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50 flex items-center gap-2 justify-center transition font-medium">
                  <PencilSquareIcon class="h-5 w-5" />
                  Manuell eingeben
                </button>
              </div>
            </div>
          </div>

          <div v-else-if="stats" class="space-y-6">

        <!-- Key Metrics -->
        <div class="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-4">
          <div class="bg-gradient-to-br from-blue-50 to-blue-100 p-4 rounded-lg border border-blue-200">
            <p class="text-xs text-blue-600 font-medium mb-1">Gesamtenergie</p>
            <p class="text-2xl font-bold text-blue-900">{{ stats.totalKwhCharged.toFixed(1) }}</p>
            <p class="text-xs text-blue-600 mt-0.5">kWh · {{ stats.totalCharges }} Ladungen</p>
          </div>
          <div class="bg-gradient-to-br from-purple-50 to-purple-100 p-4 rounded-lg border border-purple-200">
            <p class="text-xs text-purple-600 font-medium mb-1">Gesamtkosten</p>
            <p class="text-2xl font-bold text-purple-900">€{{ stats.totalCostEur.toFixed(2) }}</p>
            <p class="text-xs text-purple-600 mt-0.5">Ø €{{ stats.avgCostPerKwh.toFixed(2) }}/kWh</p>
          </div>
          <div class="bg-gradient-to-br from-green-50 to-green-100 p-4 rounded-lg border border-green-200">
            <p class="text-xs text-green-600 font-medium mb-1">Ø Ladedauer</p>
            <p class="text-2xl font-bold text-green-900">{{ formatDuration(stats.avgChargeDurationMinutes) }}</p>
            <p class="text-xs text-green-600 mt-0.5">pro Ladevorgang</p>
          </div>
          <div v-if="stats.totalDistanceKm != null"
            class="bg-gradient-to-br from-amber-50 to-amber-100 p-4 rounded-lg border border-amber-200">
            <p class="text-xs text-amber-600 font-medium mb-1">Gesamtstrecke</p>
            <p class="text-2xl font-bold text-amber-900">{{ Math.round(stats.totalDistanceKm).toLocaleString('de-DE') }}</p>
            <p class="text-xs text-amber-600 mt-0.5">km gefahren</p>
          </div>
          <div v-if="stats.avgConsumptionKwhPer100km != null"
            class="bg-gradient-to-br from-red-50 to-red-100 p-4 rounded-lg border border-red-200">
            <p class="text-xs text-red-600 font-medium mb-1">Ø Verbrauch</p>
            <p class="text-2xl font-bold text-red-900">{{ stats.avgConsumptionKwhPer100km.toFixed(1) }}</p>
            <p class="text-xs text-red-600 mt-0.5">
              kWh/100km
              <span v-if="wltp" class="font-medium">
                (WLTP: {{ wltp.wltpConsumptionKwhPer100km.toFixed(1) }})
              </span>
            </p>
          </div>
        </div>

        <!-- Dual Charts: Charging & Costs | Range & Efficiency -->
        <div class="grid grid-cols-1 gap-6">

          <!-- Chart 1: Charging & Costs -->
          <div class="md:bg-gray-50 p-4 md:p-6 md:rounded-lg md:border md:border-gray-200">
            <div v-if="!chartsReady && isInitialLoad" class="h-64 sm:h-72 bg-gray-100 animate-pulse rounded"></div>
            <template v-else>
              <div class="flex flex-col sm:flex-row sm:items-center justify-center gap-4 sm:gap-6 mb-4">
              <h2 class="text-xl font-semibold text-gray-800 text-center">Laden & Kosten</h2>

              <!-- Checkboxes Chart 1 -->
              <div class="flex flex-wrap gap-2 sm:gap-4 text-xs sm:text-sm justify-center">
                <label class="flex items-center gap-1 sm:gap-2 cursor-pointer">
                  <input type="checkbox" v-model="showCostPerKwh"
                    class="w-3 h-3 sm:w-4 sm:h-4 rounded accent-indigo-600 cursor-pointer" />
                  <span class="font-medium text-gray-700">
                    <span class="inline-block w-2 sm:w-3 h-0.5 bg-indigo-600 mr-1 align-middle"></span>
                    €/kWh
                  </span>
                </label>
                <label class="flex items-center gap-1 sm:gap-2 cursor-pointer">
                  <input type="checkbox" v-model="showKwh"
                    class="w-3 h-3 sm:w-4 sm:h-4 rounded accent-amber-500 cursor-pointer" />
                  <span class="font-medium text-gray-700">
                    <span class="inline-block w-2 sm:w-3 h-0.5 bg-amber-500 mr-1 align-middle"></span>
                    kWh
                  </span>
                </label>
              </div>
            </div>

            <div v-if="chargingChartData && chargingChartData.datasets.length > 0" class="h-64 sm:h-72">
              <Line :data="chargingChartData" :options="chargingChartOptions" />
            </div>
            <div v-else class="text-center py-10 text-gray-400 text-sm">
              Kein Datensatz ausgewählt oder nicht genügend Daten.
            </div>

              <div class="flex flex-wrap gap-x-6 gap-y-1 mt-3 text-xs text-gray-400">
                <span>Linke Achse: €/kWh</span>
                <span>Rechte Achse: kWh</span>
              </div>
            </template>
          </div>

          <!-- Chart 2: Range & Efficiency (only if distance data exists) -->
          <div v-if="hasDistanceData" class="md:bg-gray-50 p-4 md:p-6 md:rounded-lg md:border md:border-gray-200">
            <div v-if="!chartsReady && isInitialLoad" class="h-64 sm:h-72 bg-gray-100 animate-pulse rounded"></div>
            <template v-else>
            <div class="flex flex-col sm:flex-row sm:items-center justify-center gap-4 sm:gap-6 mb-4">
              <h2 class="text-xl font-semibold text-gray-800 text-center">Reichweite & Effizienz</h2>

              <!-- Checkboxes Chart 2 -->
              <div class="flex flex-wrap gap-2 sm:gap-4 text-xs sm:text-sm justify-center">
                <label class="flex items-center gap-1 sm:gap-2 cursor-pointer">
                  <input type="checkbox" v-model="showConsumption"
                    class="w-3 h-3 sm:w-4 sm:h-4 rounded accent-red-500 cursor-pointer" />
                  <span class="font-medium text-gray-700">
                    <span class="inline-block w-2 sm:w-3 h-0.5 bg-red-500 mr-1 align-middle"></span>
                    kWh/100km
                  </span>
                </label>
                <label class="flex items-center gap-1 sm:gap-2 cursor-pointer">
                  <input type="checkbox" v-model="showDistance"
                    class="w-3 h-3 sm:w-4 sm:h-4 rounded accent-emerald-500 cursor-pointer" />
                  <span class="font-medium text-gray-700">
                    <span class="inline-block w-2 sm:w-3 h-0.5 bg-emerald-500 mr-1 align-middle"></span>
                    km
                  </span>
                </label>
              </div>
            </div>

            <div v-if="efficiencyChartData && efficiencyChartData.datasets.length > 0" class="h-64 sm:h-72">
              <Line :data="efficiencyChartData" :options="efficiencyChartOptions" />
            </div>
            <div v-else class="text-center py-10 text-gray-400 text-sm">
              Kein Datensatz ausgewählt oder nicht genügend Daten.
            </div>

              <div class="flex flex-wrap gap-x-6 gap-y-1 mt-3 text-xs text-gray-400">
                <span>Linke Achse: kWh/100km</span>
                <span>Rechte Achse: km</span>
              </div>
            </template>
          </div>

        </div>

        <!-- WLTP Delta Bar Chart -->
        <div v-if="wltp && hasDistanceData && wltpChartData" class="md:bg-gray-50 p-4 md:p-6 md:rounded-lg md:border md:border-gray-200">
          <div v-if="!chartsReady && isInitialLoad" :style="{ height: wltpChartHeight }" class="bg-gray-100 animate-pulse rounded"></div>
          <template v-else>
          <div class="mb-4 text-center">
            <h2 class="text-xl font-semibold text-gray-800">Verbrauch vs. WLTP</h2>
            <p class="text-xs sm:text-sm text-gray-500 mt-1">
              WLTP: <strong>{{ wltp.wltpConsumptionKwhPer100km.toFixed(1) }} kWh/100km</strong>
              ({{ wltp.wltpRangeKm }} km, {{ wltp.wltpType }})
              <span class="hidden sm:inline">
                · <span class="text-emerald-600 font-medium">grün = effizienter</span>
                · <span class="text-red-600 font-medium">rot = mehr Verbrauch</span>
              </span>
            </p>
          </div>
            <div :class="wltpChartScrollable ? 'overflow-y-auto' : ''" :style="{ height: wltpChartHeight }">
              <Bar :data="wltpChartData" :options="wltpChartOptions" />
            </div>
          </template>
        </div>

        <!-- WLTP missing hint -->
        <div v-else-if="!wltp && hasDistanceData"
          class="bg-amber-50 border border-amber-200 md:rounded-lg p-3 md:p-4 text-sm text-amber-700">
          Für dieses Fahrzeug sind noch keine WLTP-Daten hinterlegt.
          Du kannst sie in der <router-link to="/cars" class="font-semibold underline">Fahrzeugverwaltung</router-link> ergänzen und dabei 50 Watt verdienen!
        </div>

        <!-- Charging Heat Map -->
        <div class="md:bg-gray-50 p-4 md:p-6 md:rounded-lg md:border md:border-gray-200 mb-20 md:mb-0">
          <div v-if="!chartsReady && isInitialLoad" class="h-96 bg-gray-100 animate-pulse rounded"></div>
          <template v-else>
            <div class="mb-4">
              <h2 class="text-xl font-semibold text-gray-800">Lade-Standorte</h2>
              <p class="text-sm text-gray-500 mt-1">
                Geografische Übersicht deiner Ladevorgänge · Farbcodiert nach geladener Energie (kWh)
              </p>
            </div>
            <ChargingHeatMap :car-id="selectedCarId" :time-range="selectedTimeRange" />
          </template>
        </div>

        <!-- Support -->
        <div class="px-4 md:px-0 py-6 text-center">
          <p class="text-sm text-gray-400">
            EV Monitor ist kostenlos.
            <a
              href="https://ko-fi.com/ev_monitor"
              target="_blank"
              rel="noopener noreferrer"
              class="text-gray-400 hover:text-amber-600 transition underline underline-offset-2"
            >
              Unterstütze die Entwicklung auf Ko-fi →
            </a>
          </p>
        </div>

        </div>
      </div>
      </div>
    </Transition>
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
</style>
