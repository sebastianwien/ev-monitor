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
  totalKwhCharged: number
  totalCostEur: number
  avgCostPerKwh: number
  cheapestChargeEur: number
  mostExpensiveChargeEur: number
  avgChargeDurationMinutes: number
  totalCharges: number
  chargesOverTime: Array<{
    timestamp: string
    costEur: number
    kwhCharged: number
  }>
}

const selectedCarId = ref<string | null>(null)
const stats = ref<StatisticsData | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)

// Filter state
const selectedTimeRange = ref<string>('LAST_3_MONTHS')
const selectedGroupBy = ref<string>('MONTH')

const timeRangeOptions = [
  { value: 'THIS_MONTH', label: 'This Month' },
  { value: 'LAST_MONTH', label: 'Last Month' },
  { value: 'LAST_3_MONTHS', label: 'Last 3 Months' },
  { value: 'LAST_6_MONTHS', label: 'Last 6 Months' },
  { value: 'LAST_12_MONTHS', label: 'Last Year' },
  { value: 'THIS_YEAR', label: 'This Year' },
  { value: 'ALL_TIME', label: 'All Time' }
]

const groupByOptions = [
  { value: 'DAY', label: 'Daily' },
  { value: 'WEEK', label: 'Weekly' },
  { value: 'MONTH', label: 'Monthly' }
]

const fetchStatistics = async () => {
  if (!selectedCarId.value) {
    stats.value = null
    return
  }

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
    error.value = err.response?.data?.message || 'Failed to load statistics'
    console.error('Failed to fetch statistics:', err)
  } finally {
    loading.value = false
  }
}

watch(selectedCarId, () => {
  fetchStatistics()
})

watch([selectedTimeRange, selectedGroupBy], () => {
  if (selectedCarId.value) {
    fetchStatistics()
  }
})

// Chart data
const chartData = computed(() => {
  if (!stats.value || stats.value.chargesOverTime.length === 0) {
    return null
  }

  const labels = stats.value.chargesOverTime.map(d => {
    const date = new Date(d.timestamp)

    // Format based on groupBy
    if (selectedGroupBy.value === 'DAY') {
      return date.toLocaleDateString('de-DE', { month: 'short', day: 'numeric' })
    } else if (selectedGroupBy.value === 'WEEK') {
      return `KW ${Math.ceil(date.getDate() / 7)} ${date.toLocaleDateString('de-DE', { month: 'short' })}`
    } else {
      return date.toLocaleDateString('de-DE', { month: 'short', year: 'numeric' })
    }
  })

  const costPerKwhData = stats.value.chargesOverTime.map(d => d.costEur / d.kwhCharged)
  const avgCostLine = Array(costPerKwhData.length).fill(stats.value.avgCostPerKwh)

  return {
    labels,
    datasets: [
      {
        label: 'Kosten pro kWh',
        data: costPerKwhData,
        borderColor: '#4f46e5',
        backgroundColor: 'rgba(79, 70, 229, 0.1)',
        tension: 0.3,
        fill: true,
        pointRadius: 4,
        pointHoverRadius: 6
      },
      {
        label: 'Durchschnitt (Zeitraum)',
        data: avgCostLine,
        borderColor: '#10b981',
        borderDash: [5, 5],
        borderWidth: 2,
        fill: false,
        pointRadius: 0
      }
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
          return `${context.dataset.label}: €${context.parsed.y.toFixed(2)}/kWh`
        }
      }
    }
  },
  scales: {
    y: {
      beginAtZero: false,
      title: {
        display: true,
        text: 'Kosten (€/kWh)'
      }
    }
  }
}

// Helper: format duration
const formatDuration = (minutes: number) => {
  const hours = Math.floor(minutes / 60)
  const mins = minutes % 60
  return hours > 0 ? `${hours}h ${mins}min` : `${mins}min`
}

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

      <!-- Filters Section -->
      <div v-if="selectedCarId" class="mb-6 p-4 bg-gray-50 rounded-lg border border-gray-200">
        <div class="flex flex-col md:flex-row gap-4 items-start md:items-center justify-between">
          <!-- Time Range Filter -->
          <div class="flex-1">
            <label class="block text-sm font-medium text-gray-700 mb-2">Time Range</label>
            <div class="flex flex-wrap gap-2">
              <button
                v-for="option in timeRangeOptions"
                :key="option.value"
                @click="selectedTimeRange = option.value"
                :class="[
                  'px-4 py-2 rounded-md text-sm font-medium transition-colors',
                  selectedTimeRange === option.value
                    ? 'bg-indigo-600 text-white shadow-md'
                    : 'bg-white text-gray-700 border border-gray-300 hover:bg-gray-100'
                ]">
                {{ option.label }}
              </button>
            </div>
          </div>

          <!-- Group By Filter -->
          <div class="w-full md:w-auto">
            <label class="block text-sm font-medium text-gray-700 mb-2">Group by</label>
            <select
              v-model="selectedGroupBy"
              class="block w-full md:w-auto px-4 py-2 border border-gray-300 rounded-md shadow-sm focus:ring-indigo-500 focus:border-indigo-500">
              <option v-for="option in groupByOptions" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select>
          </div>
        </div>
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

      <div v-else-if="stats && stats.totalCharges === 0" class="text-center py-12 text-gray-500">
        <p>No charges logged yet for this vehicle. Start logging to see statistics!</p>
      </div>

      <div v-else-if="stats" class="space-y-6">
        <!-- Key Metrics Cards -->
        <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
          <!-- Total Energy Charged -->
          <div class="bg-gradient-to-br from-blue-50 to-blue-100 p-6 rounded-lg border border-blue-200 shadow-sm">
            <div class="flex items-center justify-between">
              <div>
                <p class="text-sm text-blue-600 font-medium mb-1">Total Energy</p>
                <p class="text-3xl font-bold text-blue-900">{{ stats.totalKwhCharged.toFixed(1) }}</p>
                <p class="text-sm text-blue-600 mt-1">kWh</p>
              </div>
              <div class="text-4xl">⚡</div>
            </div>
            <p class="text-xs text-blue-500 mt-3">{{ stats.totalCharges }} charges logged</p>
          </div>

          <!-- Total Cost -->
          <div class="bg-gradient-to-br from-purple-50 to-purple-100 p-6 rounded-lg border border-purple-200 shadow-sm">
            <div class="flex items-center justify-between">
              <div>
                <p class="text-sm text-purple-600 font-medium mb-1">Total Cost</p>
                <p class="text-3xl font-bold text-purple-900">€{{ stats.totalCostEur.toFixed(2) }}</p>
                <p class="text-sm text-purple-600 mt-1">Average: €{{ stats.avgCostPerKwh.toFixed(2) }}/kWh</p>
              </div>
              <div class="text-4xl">💶</div>
            </div>
            <p class="text-xs text-purple-500 mt-3">
              Best: €{{ stats.cheapestChargeEur.toFixed(2) }} |
              Worst: €{{ stats.mostExpensiveChargeEur.toFixed(2) }}
            </p>
          </div>

          <!-- Average Duration -->
          <div class="bg-gradient-to-br from-green-50 to-green-100 p-6 rounded-lg border border-green-200 shadow-sm">
            <div class="flex items-center justify-between">
              <div>
                <p class="text-sm text-green-600 font-medium mb-1">Avg Duration</p>
                <p class="text-3xl font-bold text-green-900">{{ formatDuration(stats.avgChargeDurationMinutes) }}</p>
                <p class="text-sm text-green-600 mt-1">per charge</p>
              </div>
              <div class="text-4xl">⏱️</div>
            </div>
          </div>
        </div>

        <!-- Cost Over Time Chart -->
        <div class="bg-gray-50 p-6 rounded-lg border border-gray-200">
          <h2 class="text-xl font-semibold text-gray-800 mb-4">📈 Cost per kWh Over Time</h2>
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
