<script setup lang="ts">
import { ref, onMounted, watch, computed } from 'vue'
import { Line } from 'vue-chartjs'
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend,
  Filler
} from 'chart.js'
import api from '../api/axios'
import CarSelector from '../components/CarSelector.vue'

// Register Chart.js components
ChartJS.register(
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend,
  Filler
)

interface StatisticsData {
  totalDistanceKm: number
  averageConsumptionKwhPer100km: number
  bestConsumptionKwhPer100km: number
  worstConsumptionKwhPer100km: number
  totalDrives: number
  wltpRangeKm: number | null
  wltpConsumptionKwhPer100km: number | null
  wltpDifferencePercent: number | null
  consumptionOverTime: Array<{
    timestamp: string
    consumptionKwhPer100km: number
    outsideTempC: number
  }>
}

const selectedCarId = ref<string | null>(null)
const stats = ref<StatisticsData | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)

const fetchStatistics = async () => {
  if (!selectedCarId.value) {
    stats.value = null
    return
  }

  try {
    loading.value = true
    error.value = null
    const response = await api.get(`/logs/statistics?carId=${selectedCarId.value}`)
    stats.value = response.data
  } catch (err: any) {
    error.value = err.response?.data?.message || 'Failed to load statistics'
    console.error('Failed to fetch statistics:', err)
  } finally {
    loading.value = false
  }
}

watch(selectedCarId, () => {
  fetchStatistics()
})

// Chart data
const chartData = computed(() => {
  if (!stats.value || stats.value.consumptionOverTime.length === 0) {
    return null
  }

  const labels = stats.value.consumptionOverTime.map(d => {
    const date = new Date(d.timestamp)
    return date.toLocaleDateString('de-DE', { month: 'short', day: 'numeric' })
  })

  const consumptionData = stats.value.consumptionOverTime.map(d => d.consumptionKwhPer100km)
  const wltpLine = stats.value.wltpConsumptionKwhPer100km
    ? Array(consumptionData.length).fill(stats.value.wltpConsumptionKwhPer100km)
    : null

  return {
    labels,
    datasets: [
      {
        label: 'Tatsächlicher Verbrauch',
        data: consumptionData,
        borderColor: '#4f46e5',
        backgroundColor: 'rgba(79, 70, 229, 0.1)',
        tension: 0.3,
        fill: true,
        pointRadius: 4,
        pointHoverRadius: 6
      },
      ...(wltpLine ? [{
        label: 'WLTP Verbrauch',
        data: wltpLine,
        borderColor: '#10b981',
        borderDash: [5, 5],
        borderWidth: 2,
        fill: false,
        pointRadius: 0
      }] : [])
    ]
  }
})

const chartOptions = {
  responsive: true,
  maintainAspectRatio: false,
  plugins: {
    legend: {
      display: true,
      position: 'top' as const
    },
    tooltip: {
      callbacks: {
        label: (context: any) => {
          return `${context.dataset.label}: ${context.parsed.y.toFixed(1)} kWh/100km`
        }
      }
    }
  },
  scales: {
    y: {
      beginAtZero: false,
      title: {
        display: true,
        text: 'Verbrauch (kWh/100km)'
      }
    }
  }
}

// WLTP comparison color
const wltpComparisonColor = computed(() => {
  if (!stats.value?.wltpDifferencePercent) return 'text-gray-500'
  if (stats.value.wltpDifferencePercent <= -5) return 'text-green-600' // Significantly better
  if (stats.value.wltpDifferencePercent <= 5) return 'text-yellow-600' // Within tolerance
  return 'text-red-600' // Worse
})

const wltpComparisonLabel = computed(() => {
  if (!stats.value?.wltpDifferencePercent) return 'Keine WLTP-Daten'
  const diff = Math.abs(stats.value.wltpDifferencePercent)
  if (stats.value.wltpDifferencePercent < 0) {
    return `${diff.toFixed(1)}% besser als WLTP 🎉`
  } else if (stats.value.wltpDifferencePercent > 0) {
    return `${diff.toFixed(1)}% schlechter als WLTP`
  }
  return 'Genau WLTP'
})

onMounted(() => {
  fetchStatistics()
})
</script>

<template>
  <div class="max-w-6xl mx-auto p-6">
    <div class="bg-white rounded-xl shadow-lg p-6">
      <h1 class="text-3xl font-bold text-gray-800 mb-6">📊 Statistics & Analytics</h1>

      <div class="mb-6">
        <CarSelector v-model="selectedCarId" />
      </div>

      <div v-if="error" class="mb-4 p-4 bg-red-50 border border-red-200 text-red-700 rounded-md">
        {{ error }}
      </div>

      <div v-if="loading" class="text-center py-12 text-gray-500">
        <div class="inline-block animate-spin rounded-full h-12 w-12 border-b-2 border-indigo-600 mb-4"></div>
        <p>Loading statistics...</p>
      </div>

      <div v-else-if="!selectedCarId" class="text-center py-12 text-gray-500">
        <p>Please select a vehicle to see statistics.</p>
      </div>

      <div v-else-if="stats && stats.totalDrives === 0" class="text-center py-12 text-gray-500">
        <p>No drives logged yet for this vehicle. Start logging to see statistics!</p>
      </div>

      <div v-else-if="stats" class="space-y-6">
        <!-- Key Metrics Cards -->
        <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
          <!-- Total Distance -->
          <div class="bg-gradient-to-br from-blue-50 to-blue-100 p-6 rounded-lg border border-blue-200 shadow-sm">
            <div class="flex items-center justify-between">
              <div>
                <p class="text-sm text-blue-600 font-medium mb-1">Total Distance</p>
                <p class="text-3xl font-bold text-blue-900">{{ stats.totalDistanceKm.toFixed(0) }}</p>
                <p class="text-sm text-blue-600 mt-1">km</p>
              </div>
              <div class="text-4xl">🚗</div>
            </div>
            <p class="text-xs text-blue-500 mt-3">{{ stats.totalDrives }} drives logged</p>
          </div>

          <!-- Average Consumption -->
          <div class="bg-gradient-to-br from-purple-50 to-purple-100 p-6 rounded-lg border border-purple-200 shadow-sm">
            <div class="flex items-center justify-between">
              <div>
                <p class="text-sm text-purple-600 font-medium mb-1">Avg Consumption</p>
                <p class="text-3xl font-bold text-purple-900">{{ stats.averageConsumptionKwhPer100km.toFixed(1) }}</p>
                <p class="text-sm text-purple-600 mt-1">kWh/100km</p>
              </div>
              <div class="text-4xl">⚡</div>
            </div>
            <p class="text-xs text-purple-500 mt-3">
              Best: {{ stats.bestConsumptionKwhPer100km.toFixed(1) }} |
              Worst: {{ stats.worstConsumptionKwhPer100km.toFixed(1) }}
            </p>
          </div>

          <!-- WLTP Comparison -->
          <div class="bg-gradient-to-br from-green-50 to-green-100 p-6 rounded-lg border border-green-200 shadow-sm">
            <div class="flex items-center justify-between">
              <div>
                <p class="text-sm text-green-600 font-medium mb-1">vs. WLTP</p>
                <p :class="['text-2xl font-bold', wltpComparisonColor]">
                  {{ wltpComparisonLabel }}
                </p>
                <p v-if="stats.wltpConsumptionKwhPer100km" class="text-sm text-green-600 mt-1">
                  WLTP: {{ stats.wltpConsumptionKwhPer100km.toFixed(1) }} kWh/100km
                </p>
              </div>
              <div class="text-4xl">🎯</div>
            </div>
          </div>
        </div>

        <!-- Consumption Over Time Chart -->
        <div class="bg-gray-50 p-6 rounded-lg border border-gray-200">
          <h2 class="text-xl font-semibold text-gray-800 mb-4">📈 Consumption Over Time</h2>
          <div v-if="chartData" class="h-80">
            <Line :data="chartData" :options="chartOptions" />
          </div>
          <div v-else class="text-center py-12 text-gray-500">
            <p>Not enough data to display chart.</p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
