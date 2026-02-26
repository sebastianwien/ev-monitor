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
import api from '../api/axios'
import CarSelector from '../components/CarSelector.vue'
import { vehicleSpecificationService, type VehicleSpecification } from '../api/vehicleSpecificationService'

ChartJS.register(CategoryScale, LinearScale, BarElement, PointElement, LineElement, Title, Tooltip, Legend, Filler)

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

const selectedCarId = ref<string | null>(null)
const stats = ref<StatisticsData | null>(null)
const carInfo = ref<CarInfo | null>(null)
const wltp = ref<VehicleSpecification | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)

const selectedTimeRange = ref<string>('LAST_3_MONTHS')
const selectedGroupBy = ref<string>('MONTH')

// Dataset toggles
const showCostPerKwh = ref(true)
const showKwh = ref(true)
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
  if (!selectedCarId.value) { stats.value = null; return }
  try {
    loading.value = true
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

// ── Main line chart ──────────────────────────────────────────────────────────
const lineChartData = computed(() => {
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

  if (showDistance.value && hasDistanceData.value) {
    datasets.push({
      label: 'Strecke (km)',
      data: stats.value.chargesOverTime.map(d => d.distanceKm),
      borderColor: '#10b981',
      backgroundColor: 'rgba(16,185,129,0.08)',
      tension: 0.3, fill: false, pointRadius: 4, pointHoverRadius: 6, yAxisID: 'y1'
    })
  }

  if (showConsumption.value && hasDistanceData.value) {
    datasets.push({
      label: 'Verbrauch (kWh/100km)',
      data: stats.value.chargesOverTime.map(d => d.consumptionKwhPer100km),
      borderColor: '#ef4444',
      backgroundColor: 'rgba(239,68,68,0.08)',
      tension: 0.3, fill: false, pointRadius: 4, pointHoverRadius: 6, yAxisID: 'y'
    })
  }

  return { labels, datasets }
})

const lineChartOptions = computed(() => ({
  responsive: true,
  maintainAspectRatio: false,
  interaction: { mode: 'index' as const, intersect: false },
  plugins: {
    legend: { display: false },
    tooltip: {
      callbacks: {
        label: (ctx: any) => {
          const lbl = ctx.dataset.label
          const v = ctx.parsed.y
          if (v == null) return `${lbl}: –`
          if (lbl.includes('€/kWh')) return `${lbl}: €${v.toFixed(2)}`
          if (lbl.includes('kWh/100km')) return `${lbl}: ${v.toFixed(1)}`
          if (lbl.includes('kWh')) return `${lbl}: ${v.toFixed(1)} kWh`
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
      title: { display: true, text: '€/kWh  |  kWh/100km' },
      beginAtZero: false,
      grid: { color: 'rgba(0,0,0,0.06)' }
    },
    y1: {
      type: 'linear' as const,
      position: 'right' as const,
      title: { display: true, text: 'kWh  |  km' },
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

const wltpChartOptions = computed(() => ({
  responsive: true,
  maintainAspectRatio: false,
  plugins: {
    legend: { display: false },
    tooltip: {
      callbacks: {
        label: (ctx: any) => {
          const v = ctx.parsed.y
          const sign = v > 0 ? '+' : ''
          return `${sign}${v.toFixed(2)} kWh/100km vs. WLTP (${wltp.value?.wltpConsumptionKwhPer100km.toFixed(1)} kWh/100km)`
        }
      }
    }
  },
  scales: {
    y: {
      title: { display: true, text: 'Δ kWh/100km (+ = mehr als WLTP)' },
      grid: { color: (ctx: any) => ctx.tick.value === 0 ? '#6b7280' : 'rgba(0,0,0,0.06)' },
      ticks: {
        callback: (v: any) => `${v > 0 ? '+' : ''}${v}`
      }
    }
  }
}))

const formatDuration = (minutes: number) => {
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  return h > 0 ? `${h}h ${m}min` : `${m}min`
}

onMounted(fetchStatistics)
</script>

<template>
  <div class="max-w-6xl mx-auto p-6">
    <div class="bg-white rounded-xl shadow-lg p-6">
      <h1 class="text-3xl font-bold text-gray-800 mb-6">📊 Statistiken & Analysen</h1>

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

      <div v-if="loading" class="text-center py-12 text-gray-500">
        <div class="inline-block animate-spin rounded-full h-12 w-12 border-b-2 border-indigo-600 mb-4"></div>
        <p>Lade Statistiken...</p>
      </div>

      <div v-else-if="!selectedCarId" class="text-center py-12 text-gray-500">
        <p>Bitte wähle ein Fahrzeug aus um Statistiken anzuzeigen.</p>
      </div>

      <div v-else-if="stats && stats.totalCharges === 0" class="text-center py-12 text-gray-500">
        <p>Noch keine Ladevorgänge für dieses Fahrzeug erfasst.</p>
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

        <!-- Line Chart: Zeitverlauf -->
        <div class="bg-gray-50 p-6 rounded-lg border border-gray-200">
          <div class="flex flex-col sm:flex-row sm:items-start justify-between gap-4 mb-4">
            <h2 class="text-xl font-semibold text-gray-800">📈 Zeitverlauf</h2>

            <!-- Real checkboxes -->
            <div class="flex flex-wrap gap-4">
              <label class="flex items-center gap-2 cursor-pointer">
                <input type="checkbox" v-model="showCostPerKwh"
                  class="w-4 h-4 rounded accent-indigo-600 cursor-pointer" />
                <span class="text-sm font-medium text-gray-700">
                  <span class="inline-block w-3 h-0.5 bg-indigo-600 mr-1 align-middle"></span>
                  €/kWh
                </span>
              </label>
              <label class="flex items-center gap-2 cursor-pointer">
                <input type="checkbox" v-model="showKwh"
                  class="w-4 h-4 rounded accent-amber-500 cursor-pointer" />
                <span class="text-sm font-medium text-gray-700">
                  <span class="inline-block w-3 h-0.5 bg-amber-500 mr-1 align-middle"></span>
                  kWh geladen
                </span>
              </label>
              <template v-if="hasDistanceData">
                <label class="flex items-center gap-2 cursor-pointer">
                  <input type="checkbox" v-model="showDistance"
                    class="w-4 h-4 rounded accent-emerald-500 cursor-pointer" />
                  <span class="text-sm font-medium text-gray-700">
                    <span class="inline-block w-3 h-0.5 bg-emerald-500 mr-1 align-middle"></span>
                    Strecke km
                  </span>
                </label>
                <label class="flex items-center gap-2 cursor-pointer">
                  <input type="checkbox" v-model="showConsumption"
                    class="w-4 h-4 rounded accent-red-500 cursor-pointer" />
                  <span class="text-sm font-medium text-gray-700">
                    <span class="inline-block w-3 h-0.5 bg-red-500 mr-1 align-middle"></span>
                    kWh/100km
                  </span>
                </label>
              </template>
            </div>
          </div>

          <div v-if="lineChartData && lineChartData.datasets.length > 0" class="h-72">
            <Line :data="lineChartData" :options="lineChartOptions" />
          </div>
          <div v-else class="text-center py-10 text-gray-400 text-sm">
            Kein Datensatz ausgewählt oder nicht genügend Daten.
          </div>

          <div class="flex flex-wrap gap-x-6 gap-y-1 mt-3 text-xs text-gray-400">
            <span>Linke Achse: €/kWh · kWh/100km</span>
            <span>Rechte Achse: kWh · km</span>
          </div>
        </div>

        <!-- WLTP Delta Bar Chart -->
        <div v-if="wltp && hasDistanceData && wltpChartData" class="bg-gray-50 p-6 rounded-lg border border-gray-200">
          <div class="mb-4">
            <h2 class="text-xl font-semibold text-gray-800">⚡ Verbrauch vs. WLTP</h2>
            <p class="text-sm text-gray-500 mt-1">
              WLTP-Referenz: <strong>{{ wltp.wltpConsumptionKwhPer100km.toFixed(1) }} kWh/100km</strong>
              ({{ wltp.wltpRangeKm }} km Reichweite, {{ wltp.wltpType }}) ·
              <span class="text-emerald-600 font-medium">🟢 grün = effizienter als WLTP</span> ·
              <span class="text-red-600 font-medium">🔴 rot = mehr Verbrauch</span>
            </p>
          </div>
          <div class="h-64">
            <Bar :data="wltpChartData" :options="wltpChartOptions" />
          </div>
        </div>

        <!-- WLTP missing hint -->
        <div v-else-if="!wltp && hasDistanceData"
          class="bg-amber-50 border border-amber-200 rounded-lg p-4 text-sm text-amber-700">
          📊 Für dieses Fahrzeug sind noch keine WLTP-Daten hinterlegt.
          Du kannst sie in der <router-link to="/cars" class="font-semibold underline">Fahrzeugverwaltung</router-link> ergänzen und dabei 50 Coins verdienen!
        </div>

      </div>
    </div>
  </div>
</template>
