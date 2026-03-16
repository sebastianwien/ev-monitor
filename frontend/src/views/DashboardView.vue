<script setup lang="ts">
import { ref, onMounted, watch, computed, nextTick } from 'vue'
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
  CloudIcon,
  XMarkIcon,
  HomeIcon,
} from '@heroicons/vue/24/outline'
import type { Component } from 'vue'
import { useRouter } from 'vue-router'
import api from '../api/axios'
import { tempBadgeClass } from '../utils/temperatureColor'
import { consumptionBadgeClass } from '../utils/consumptionColor'
import ConsumptionInfoBox from '../components/ConsumptionInfoBox.vue'
import EditLogModal from '../components/EditLogModal.vue'
import { costBadgeClass } from '../utils/costColor'
import { carService } from '../api/carService'
import { useCarStore } from '../stores/car'
import LicensePlate from '../components/LicensePlate.vue'
import { useTeslaStatus } from '../composables/useTeslaStatus'
import ChargingHeatMap from '../components/ChargingHeatMap.vue'
import { vehicleSpecificationService, type VehicleSpecification } from '../api/vehicleSpecificationService'
import RewardSystemUpdateBanner from '../components/RewardSystemUpdateBanner.vue'

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
  estimatedConsumptionCount: number
  summerConsumptionKwhPer100km: number | null
  winterConsumptionKwhPer100km: number | null
  chargesOverTime: ChargeDataPoint[]
}

interface CarInfo {
  id: string
  brand: string
  model: string
  batteryCapacityKwh: number
}

const router = useRouter()
const carStore = useCarStore()
const selectedCarId = ref<string | null>(null)
const importBannerDismissed = ref(localStorage.getItem('import_banner_dismissed') === 'true')

const dismissImportBanner = (e: Event) => {
  e.preventDefault()
  importBannerDismissed.value = true
  localStorage.setItem('import_banner_dismissed', 'true')
}
const stats = ref<StatisticsData | null>(null)
const carInfo = ref<CarInfo | null>(null)
const wltp = ref<VehicleSpecification | null>(null)
const loading = ref(true) // Initial true to prevent flicker on mount
const chartsReady = ref(false) // Charts render after fade-in
const isInitialLoad = ref(true) // Track if this is the first load
const error = ref<string | null>(null)
const cars = ref<any[]>([]) // Track available cars for empty state
const carImageUrls = ref<Record<string, string>>({})

const selectedTimeRange = ref<string>('LAST_3_MONTHS')
const selectedGroupBy = ref<string>('DAY')

const showOdometer = ref(false)
const showCostAbsolute = ref(false)
const { teslaStatus, start: startTeslaPolling } = useTeslaStatus()

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
  { value: 'ALL_TIME', label: 'Alle' }
]

const groupByOptions = [
  { value: 'DAY', label: 'Täglich' },
  { value: 'WEEK', label: 'Wöchentlich' },
  { value: 'MONTH', label: 'Monatlich' }
]

// Fetch car details + WLTP when car changes (uses already-loaded cars.value — no extra API call)
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
    await Promise.all([fetchStatistics(), fetchLogs(0)])
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

const rangeWindows = [
  { label: '100 → 0 %', socMax: 100, socMin: 0,  recommended: false },
  { label: '90 → 10 %',  socMax: 90,  socMin: 10, recommended: true  },
  { label: '80 → 20 %',  socMax: 80,  socMin: 20, recommended: false },
]

const calcRange = (batteryKwh: number, socMax: number, socMin: number, consumptionKwhPer100km: number): string => {
  const usableKwh = batteryKwh * (socMax - socMin) / 100
  const km = Math.floor(usableKwh / consumptionKwhPer100km * 100 / 10) * 10
  return `~${km}`
}

const formatLabel = (timestamp: string) => {
  const date = new Date(timestamp)
  const currentYear = new Date().getFullYear()
  const year = date.getFullYear()
  const yearSuffix = year !== currentYear ? ` '${String(year).slice(-2)}` : ''

  if (selectedGroupBy.value === 'DAY')
    return date.toLocaleDateString('de-DE', { month: 'short', day: 'numeric' }) + yearSuffix
  if (selectedGroupBy.value === 'WEEK')
    return `KW ${Math.ceil(date.getDate() / 7)} ${date.toLocaleDateString('de-DE', { month: 'short' })}${yearSuffix}`
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

const enumToLabel = (value: string | undefined | null): string =>
  (value ?? '').replace(/_/g, ' ').toLowerCase()
    .split(' ')
    .map((w: string) => w.charAt(0).toUpperCase() + w.slice(1))
    .join(' ')

onMounted(async () => {
  // Pre-fetch cars so the desktop card selector is populated before CarSelector emits
  try {
    const carList = await carStore.getCars()
    cars.value = carList
    // Auto-select: primary car, fallback to first
    const primary = carList.find((c: any) => c.isPrimary) ?? carList[0]
    if (primary) selectedCarId.value = primary.id
    // Load images in background — non-critical
    for (const car of carList.filter((c: any) => c.imageUrl)) {
      carService.getCarImageBlobUrl(car.id)
        .then(url => { carImageUrls.value = { ...carImageUrls.value, [car.id]: url } })
        .catch(() => {})
    }
    startTeslaPolling(carList.some((c: any) => c.brand?.toLowerCase() === 'tesla'))
  } catch { /* non-critical */ }
  // fetchStatistics() is NOT called here — setting selectedCarId above already triggers the watch,
  // which calls fetchCarAndWltp + fetchStatistics + fetchLogs in sequence.
})

// ── Log List with Pagination ─────────────────────────────────────────────────
const PAGE_SIZE = 20
const logs = ref<any[]>([])
const logsPage = ref(0)
const logsLoading = ref(false)
const hasMoreLogs = ref(false)
const logsSection = ref<HTMLElement | null>(null)
const editingLog = ref<any | null>(null)

// Session Groups (Überschussladen)
const sessionGroups = ref<any[]>([])
const expandedGroups = ref<Set<string>>(new Set())
const subSessionsCache = ref<Record<string, any[]>>({})

const fetchGroups = async () => {
  if (!selectedCarId.value) return
  try {
    const res = await api.get(`/logs/groups?carId=${selectedCarId.value}`)
    sessionGroups.value = res.data
  } catch {
    // Kein Session-Grouping-Feature aktiv oder Netzwerkfehler — ignorieren
    sessionGroups.value = []
  }
}

const toggleGroupExpand = async (groupId: string) => {
  if (expandedGroups.value.has(groupId)) {
    expandedGroups.value.delete(groupId)
    return
  }
  expandedGroups.value.add(groupId)
  if (!subSessionsCache.value[groupId]) {
    try {
      const res = await api.get(`/logs/group/${groupId}`)
      subSessionsCache.value[groupId] = res.data
    } catch {
      subSessionsCache.value[groupId] = []
    }
  }
}

/// Schneller Exists-Check ohne den teuren Merge+Sort zu triggern
const hasAnyLogs = computed(() => logs.value.length > 0 || sessionGroups.value.length > 0)

// Merged + sorted feed: normale Logs und Gruppen nach Datum zusammenführen
const mergedLogFeed = computed(() => {
  const groupsForPage = sessionGroups.value.map((g: any) => ({ ...g, _isGroup: true }))
  const logsWithFlag = logs.value.map((l: any) => ({ ...l, _isGroup: false }))
  const sorted = [...logsWithFlag, ...groupsForPage].sort((a, b) => {
    const dateA = new Date(a._isGroup ? a.sessionStart : a.loggedAt).getTime()
    const dateB = new Date(b._isGroup ? b.sessionStart : b.loggedAt).getTime()
    return dateB - dateA  // Neueste zuerst
  })

  // Nachladen-Erkennung: konsekutive Eintraege mit gleichem Kilometerstand
  // sorted ist newest-first. Aeltester Eintrag im Run = Parent, alle neueren = Top-Ups.
  const topUpChildren = new Map<number, any[]>() // parent-index → [top-up entries]
  const skipIndices = new Set<number>()

  let i = 0
  while (i < sorted.length) {
    if (sorted[i]._isGroup || sorted[i].odometerKm == null) {
      i++
      continue
    }
    // Konsekutiven Run mit gleichem odometerKm finden
    let j = i + 1
    while (j < sorted.length &&
           !sorted[j]._isGroup &&
           sorted[j].odometerKm != null &&
           sorted[j].odometerKm === sorted[i].odometerKm) {
      j++
    }
    if (j > i + 1) {
      // Run von i bis j-1: sorted[j-1] ist der aelteste = Parent
      const parentIdx = j - 1
      topUpChildren.set(parentIdx, [])
      for (let k = i; k < j - 1; k++) {
        topUpChildren.get(parentIdx)!.push({ ...sorted[k], _isTopUp: true })
        skipIndices.add(k)
      }
      i = j
    } else {
      i++
    }
  }

  const result: any[] = []
  for (let i = 0; i < sorted.length; i++) {
    if (skipIndices.has(i)) continue
    result.push({ ...sorted[i], _isTopUp: false, _topUps: topUpChildren.get(i) ?? [] })
  }
  return result
})

const fetchLogs = async (page = 0) => {
  if (!selectedCarId.value) return
  logsLoading.value = true
  try {
    const res = await api.get(`/logs?carId=${selectedCarId.value}&limit=${PAGE_SIZE}&page=${page}`)
    logs.value = res.data
    logsPage.value = page
    hasMoreLogs.value = res.data.length === PAGE_SIZE
    if (page === 0) await fetchGroups()
  } catch {
    // Network error — keep existing log list, don't crash
  } finally {
    logsLoading.value = false
  }
}

const scrollToLogs = async () => {
  await fetchLogs(0)
  await nextTick()
  logsSection.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

watch(selectedCarId, () => {
  logs.value = []
  sessionGroups.value = []
  expandedGroups.value = new Set()
  subSessionsCache.value = {}
  logsPage.value = 0
  hasMoreLogs.value = false
})

const formatLogDate = (loggedAt: string) => {
  const d = new Date(loggedAt)
  const isCurrentYear = d.getFullYear() === new Date().getFullYear()
  const date = d.toLocaleDateString('de-DE', { day: 'numeric', month: 'numeric', ...(isCurrentYear ? {} : { year: 'numeric' }) })
  const time = d.toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' })
  return `${date}, ${time} Uhr`
}

const toggleOdometerDisplay = (distanceKm: number | null, odometerKm: number | null) => {
  if (distanceKm == null || odometerKm == null) return
  showOdometer.value = !showOdometer.value
}

function sourceInfo(ds?: string): { label: string; icon: Component; classes: string } | null {
  switch (ds) {
    case 'TESLA_FLEET_IMPORT':  return { label: 'Supercharger',    icon: BoltIcon,          classes: 'bg-red-50 text-red-700' }
    case 'TESLA_LIVE':          return { label: 'Tesla Live',       icon: BoltIcon,          classes: 'bg-red-50 text-red-700' }
    case 'TESLA_IMPORT':        return { label: 'Tesla Import',     icon: ArrowDownTrayIcon, classes: 'bg-purple-50 text-purple-700' }
    case 'TESLA_MANUAL_IMPORT': return { label: 'TeslaMate Import', icon: ArrowDownTrayIcon, classes: 'bg-purple-50 text-purple-700' }
    case 'SPRITMONITOR_IMPORT': return { label: 'SpritMonitor',     icon: ArrowDownTrayIcon, classes: 'bg-purple-50 text-purple-700' }
    case 'WALLBOX_OCPP':
    case 'WALLBOX_GOE':         return { label: 'Wallbox',          icon: HomeIcon,          classes: 'bg-blue-50 text-blue-700' }
    default:                    return null
  }
}

const deleteLog = async (id: string) => {
  if (!confirm('Ladevorgang wirklich löschen?')) return
  try {
    await api.delete(`/logs/${id}`)
    await fetchLogs(logsPage.value)
  } catch {
    // Network error — ignore, log list stays unchanged
  }
}
</script>

<template>
  <div class="md:max-w-6xl md:mx-auto md:p-6">
    <RewardSystemUpdateBanner class="mb-4" />
    <Transition name="fade" mode="out-in">
      <div v-if="!loading">
        <div class="bg-white md:rounded-xl md:shadow-lg p-4 md:p-6">
          <div class="flex items-center gap-3 mb-6">
            <ChartBarIcon class="h-8 w-8 text-gray-700" />
            <h1 class="text-3xl font-bold text-gray-800">Dashboard</h1>
            <button v-if="stats && stats.totalCharges > 0"
              @click="scrollToLogs"
              class="ml-auto hidden md:flex items-center gap-2 px-4 py-2 rounded-lg bg-indigo-600 text-white text-sm font-medium shadow-sm hover:bg-indigo-700 active:scale-95 transition">
              <ListBulletIcon class="w-4 h-4" />
              Deine Ladevorgänge
              <ChevronRightIcon class="w-3.5 h-3.5 opacity-75" />
            </button>
          </div>

          <!-- Import Hint Banner -->
          <div v-if="!importBannerDismissed" class="relative mb-6">
            <router-link
              to="/imports"
              class="flex items-center gap-3 bg-green-50 border border-green-200 rounded-lg px-4 py-3 hover:bg-green-100 transition group"
            >
              <ArrowDownTrayIcon class="h-5 w-5 text-green-600 shrink-0" />
              <div class="flex-1 min-w-0">
                <span class="text-sm font-medium text-green-800">Ladevorgänge importieren</span>
                <span class="text-sm text-green-700 ml-1">— Sprit-Monitor, go-eCharger Cloud, OCPP Wallbox</span>
              </div>
              <span class="text-green-600 text-sm group-hover:translate-x-0.5 transition-transform">→</span>
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
          <div v-if="cars.length > 0" class="mb-6">
            <p class="text-sm font-medium text-gray-700 mb-2">Fahrzeug</p>
            <div class="flex gap-3 overflow-x-auto pb-1 lg:flex-wrap lg:overflow-x-visible">
              <button
                v-for="car in cars"
                :key="car.id"
                @click="selectedCarId = car.id"
                :class="[
                  cars.length === 1
                    ? 'flex items-center gap-3 px-4 py-3 rounded-xl border-2 text-left transition w-full md:w-auto'
                    : 'flex items-center gap-3 px-4 py-3 rounded-xl border-2 text-left transition flex-shrink-0 min-w-[200px] max-w-[280px] lg:flex-shrink lg:min-w-0 lg:max-w-none',
                  selectedCarId === car.id
                    ? 'border-indigo-500 bg-indigo-50 shadow-sm'
                    : 'border-gray-200 bg-white hover:border-indigo-300 hover:bg-gray-50'
                ]">
                <div class="flex-shrink-0 w-16 h-16 rounded-xl bg-gray-100 flex items-center justify-center overflow-hidden">
                  <img
                    v-if="carImageUrls[car.id]"
                    :src="carImageUrls[car.id]"
                    :alt="car.model"
                    class="w-full h-full object-cover" />
                  <TruckIcon v-else class="w-8 h-8 text-gray-400" />
                </div>
                <div class="min-w-0 flex-1">
                  <!-- Mobile single-car: alles in einer Zeile -->
                  <div v-if="cars.length === 1" class="flex items-center gap-2 flex-wrap lg:hidden">
                    <span class="font-semibold text-gray-800">{{ enumToLabel(car.brand) }} {{ enumToLabel(car.model) }}</span>
                    <span v-if="car.trim" class="text-sm text-gray-500">{{ car.trim }}</span>
                    <LicensePlate v-if="car.licensePlate" :plate="car.licensePlate" />
                    <span v-if="car.isPrimary"
                      class="px-1.5 py-0.5 bg-green-100 text-green-700 text-xs rounded-full border border-green-200 font-medium">
                      Aktiv
                    </span>
                    <template v-if="car.brand?.toLowerCase() === 'tesla' && teslaStatus?.connected && (teslaStatus.carId === car.id || teslaStatus.carId === null)">
                      <span v-if="teslaStatus.vehicleState === 'charging'"
                        class="inline-flex items-center gap-1 px-1.5 py-0.5 bg-green-100 text-green-700 text-xs rounded-full font-medium border border-green-200">
                        <span class="w-1.5 h-1.5 rounded-full bg-green-500 animate-pulse"></span>Lädt
                      </span>
                      <span v-else-if="teslaStatus.vehicleState === 'online'"
                        class="inline-flex items-center gap-1 px-1.5 py-0.5 bg-blue-50 text-blue-600 text-xs rounded-full font-medium border border-blue-200">
                        <span class="w-1.5 h-1.5 rounded-full bg-blue-400"></span>Online
                      </span>
                      <span v-else-if="teslaStatus.vehicleState === 'asleep'"
                        class="inline-flex items-center gap-1 px-1.5 py-0.5 bg-gray-100 text-gray-500 text-xs rounded-full font-medium border border-gray-200">
                        <span class="w-1.5 h-1.5 rounded-full bg-gray-400"></span>Schläft
                      </span>
                    </template>
                  </div>
                  <!-- Desktop oder mehrere Autos: zweizeiliges Layout -->
                  <div :class="cars.length === 1 ? 'hidden lg:block' : ''">
                    <div class="flex items-center gap-2 flex-wrap">
                      <span class="font-semibold text-gray-800">{{ enumToLabel(car.brand) }} {{ enumToLabel(car.model) }}</span>
                      <span v-if="car.trim" class="text-sm text-gray-500">{{ car.trim }}</span>
                      <span v-if="car.isPrimary"
                        class="px-1.5 py-0.5 bg-green-100 text-green-700 text-xs rounded-full border border-green-200 font-medium">
                        Aktiv
                      </span>
                      <template v-if="car.brand?.toLowerCase() === 'tesla' && teslaStatus?.connected && (teslaStatus.carId === car.id || teslaStatus.carId === null)">
                        <span v-if="teslaStatus.vehicleState === 'charging'"
                          class="inline-flex items-center gap-1 px-1.5 py-0.5 bg-green-100 text-green-700 text-xs rounded-full font-medium border border-green-200">
                          <span class="w-1.5 h-1.5 rounded-full bg-green-500 animate-pulse"></span>Lädt
                        </span>
                        <span v-else-if="teslaStatus.vehicleState === 'online'"
                          class="inline-flex items-center gap-1 px-1.5 py-0.5 bg-blue-50 text-blue-600 text-xs rounded-full font-medium border border-blue-200">
                          <span class="w-1.5 h-1.5 rounded-full bg-blue-400"></span>Online
                        </span>
                        <span v-else-if="teslaStatus.vehicleState === 'asleep'"
                          class="inline-flex items-center gap-1 px-1.5 py-0.5 bg-gray-100 text-gray-500 text-xs rounded-full font-medium border border-gray-200">
                          <span class="w-1.5 h-1.5 rounded-full bg-gray-400"></span>Schläft
                        </span>
                      </template>
                    </div>
                    <div class="mt-1.5 flex justify-center">
                      <LicensePlate v-if="car.licensePlate" :plate="car.licensePlate" />
                    </div>
                  </div>
                </div>
              </button>
            </div>
          </div>

          <!-- Echte Reichweite -->
          <div v-if="carInfo?.batteryCapacityKwh && (stats?.summerConsumptionKwhPer100km || stats?.winterConsumptionKwhPer100km)"
            class="mb-6 bg-gray-50 rounded-lg border border-gray-200 p-4">
            <h3 class="text-sm font-semibold text-gray-700 mb-3">Deine echte Reichweite</h3>
            <div class="overflow-x-auto -mx-4 px-4">
            <table class="w-full text-sm">
              <thead>
                <tr class="text-xs text-gray-500">
                  <th class="text-left pb-2 font-medium">Ladefenster</th>
                  <th v-if="stats?.summerConsumptionKwhPer100km" class="text-right pb-2 font-medium text-amber-600 whitespace-nowrap pl-4">
                    <span class="inline-flex items-center justify-end gap-1">
                      <SunIcon class="w-4 h-4" />
                      <span class="hidden sm:inline">Sommer</span>
                      <span class="font-normal">({{ stats.summerConsumptionKwhPer100km.toFixed(1) }}<span class="hidden sm:inline"> kWh/100km</span><span class="sm:hidden"> kWh</span>)</span>
                    </span>
                  </th>
                  <th v-if="stats?.winterConsumptionKwhPer100km" class="text-right pb-2 font-medium text-blue-600 whitespace-nowrap pl-4">
                    <span class="inline-flex items-center justify-end gap-1">
                      <CloudIcon class="w-4 h-4" />
                      <span class="hidden sm:inline">Winter</span>
                      <span class="font-normal">({{ stats.winterConsumptionKwhPer100km.toFixed(1) }}<span class="hidden sm:inline"> kWh/100km</span><span class="sm:hidden"> kWh</span>)</span>
                    </span>
                  </th>
                </tr>
              </thead>
              <tbody class="divide-y divide-gray-100">
                <tr v-for="window in rangeWindows" :key="window.label">
                  <td class="py-2 pr-4 whitespace-nowrap font-medium text-gray-800">{{ window.label }}</td>
                  <td v-if="stats?.summerConsumptionKwhPer100km" class="py-2 text-right font-bold text-amber-700">
                    {{ calcRange(carInfo.batteryCapacityKwh, window.socMax, window.socMin, stats.summerConsumptionKwhPer100km) }} km
                  </td>
                  <td v-if="stats?.winterConsumptionKwhPer100km" class="py-2 text-right font-bold text-blue-700">
                    {{ calcRange(carInfo.batteryCapacityKwh, window.socMax, window.socMin, stats.winterConsumptionKwhPer100km) }} km
                  </td>
                </tr>
              </tbody>
            </table>
            </div>
          </div>

          <!-- Filters (show if there are logs in any time range) -->
          <div v-if="selectedCarId && hasAnyLogs" class="mb-6 p-4 bg-gray-50 rounded-lg border border-gray-200">
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

          <!-- Empty State: No Logs in time range (but logs exist) -->
          <div v-else-if="stats && stats.totalCharges === 0 && hasAnyLogs" class="py-12 flex items-center justify-center">
            <div class="text-center max-w-md px-4">
              <h2 class="text-lg font-semibold text-gray-700 mb-2">Keine Daten im gewählten Zeitraum</h2>
              <p class="text-gray-500 text-sm">Wähle einen anderen Zeitraum oder scrolle nach unten um alle Ladevorgänge zu sehen.</p>
            </div>
          </div>

          <!-- Empty State: Truly no logs at all -->
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

          <div v-else-if="stats" class="space-y-0">

        <!-- Mobile: Deine Ladevorgänge CTA — above stats cards, below filters -->
        <button v-if="stats && stats.totalCharges > 0"
          @click="scrollToLogs"
          class="md:hidden w-full flex items-center justify-center gap-2 px-4 py-3 mb-4 rounded-xl bg-indigo-600 text-white text-sm font-semibold shadow-sm hover:bg-indigo-700 active:scale-95 transition">
          <ListBulletIcon class="w-4 h-4" />
          Deine Ladevorgänge
          <ChevronRightIcon class="w-3.5 h-3.5 opacity-75" />
        </button>

        <!-- Key Metrics -->
        <div class="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-4 pb-6 mb-0">
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
            <p v-if="stats.estimatedConsumptionCount > 0" class="text-xs text-red-500 mt-2 italic">
              {{ stats.estimatedConsumptionCount }} Ladung{{ stats.estimatedConsumptionCount > 1 ? 'en' : '' }} geschätzt (ohne SoC)
            </p>
          </div>
        </div>

        <!-- Chart 1: Charging & Costs -->
        <div class="border-t border-gray-100 pt-6">
          <div class="md:bg-gray-50 py-4 md:p-6 -mx-4 md:mx-0 md:rounded-lg md:border md:border-gray-200">
            <div v-if="!chartsReady && isInitialLoad" class="h-64 sm:h-72 bg-gray-100 animate-pulse rounded mx-4 md:mx-0"></div>
            <template v-else>
              <div class="flex flex-col sm:flex-row sm:items-center justify-center gap-4 sm:gap-6 mb-4 px-4 md:px-0">
                <h2 class="text-xl font-semibold text-gray-800 text-center">Laden & Kosten</h2>
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
              <div v-else class="text-center py-10 text-gray-400 text-sm px-4 md:px-0">
                Kein Datensatz ausgewählt oder nicht genügend Daten.
              </div>
              <div class="flex flex-wrap gap-x-6 gap-y-1 mt-3 text-xs text-gray-400 px-4 md:px-0">
                <span>Linke Achse: €/kWh</span>
                <span>Rechte Achse: kWh</span>
              </div>
            </template>
          </div>
        </div>

        <!-- Chart 2: Range & Efficiency (only if distance data exists) -->
        <div v-if="hasDistanceData" class="border-t border-gray-100 pt-6">
          <div class="md:bg-gray-50 py-4 md:p-6 -mx-4 md:mx-0 md:rounded-lg md:border md:border-gray-200">
            <div v-if="!chartsReady && isInitialLoad" class="h-64 sm:h-72 bg-gray-100 animate-pulse rounded mx-4 md:mx-0"></div>
            <template v-else>
              <div class="flex flex-col sm:flex-row sm:items-center justify-center gap-4 sm:gap-6 mb-4 px-4 md:px-0">
                <h2 class="text-xl font-semibold text-gray-800 text-center">Reichweite & Effizienz</h2>
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
              <div v-else class="text-center py-10 text-gray-400 text-sm px-4 md:px-0">
                Kein Datensatz ausgewählt oder nicht genügend Daten.
              </div>
              <div class="flex flex-wrap gap-x-6 gap-y-1 mt-3 text-xs text-gray-400 px-4 md:px-0">
                <span>Linke Achse: kWh/100km</span>
                <span>Rechte Achse: km</span>
              </div>
            </template>
          </div>
        </div>

        <!-- WLTP Delta Bar Chart -->
        <div v-if="wltp && hasDistanceData && wltpChartData" class="border-t border-gray-100 pt-6">
          <div class="md:bg-gray-50 py-4 md:p-6 -mx-4 md:mx-0 md:rounded-lg md:border md:border-gray-200">
          <div v-if="!chartsReady && isInitialLoad" :style="{ height: wltpChartHeight }" class="bg-gray-100 animate-pulse rounded mx-4 md:mx-0"></div>
          <template v-else>
          <div class="mb-4 text-center px-4 md:px-0">
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
        </div>

        <!-- WLTP missing hint -->
        <div v-else-if="!wltp && hasDistanceData" class="border-t border-gray-100 pt-6">
          <div class="bg-amber-50 border border-amber-200 md:rounded-lg p-3 md:p-4 text-sm text-amber-700">
            Für dieses Fahrzeug sind noch keine WLTP-Daten hinterlegt.
            Du kannst sie in der <router-link to="/cars" class="font-semibold underline">Fahrzeugverwaltung</router-link> ergänzen und dabei 50 Watt verdienen!
          </div>
        </div>

        <!-- Charging Heat Map -->
        <div class="border-t border-gray-100 pt-6">
          <div class="md:bg-gray-50 py-4 md:p-6 -mx-4 md:mx-0 md:rounded-lg md:border md:border-gray-200 mb-4 md:mb-0">
            <div v-if="!chartsReady && isInitialLoad" class="h-96 bg-gray-100 animate-pulse rounded mx-4 md:mx-0"></div>
            <template v-else>
              <div class="mb-4 px-4 md:px-0">
                <h2 class="text-xl font-semibold text-gray-800">Lade-Standorte</h2>
                <p class="text-sm text-gray-500 mt-1">
                  Geografische Übersicht deiner Ladevorgänge · Farbcodiert nach geladener Energie (kWh)
                </p>
              </div>
              <ChargingHeatMap :car-id="selectedCarId" :time-range="selectedTimeRange" />
            </template>
          </div>
        </div>

        <!-- Log List -->
        <div ref="logsSection" class="border-t border-gray-100 pt-3 scroll-mt-4 pb-6">
          <div class="flex items-center justify-between mb-3">
            <h2 class="text-xl font-semibold text-gray-800">Deine Ladevorgänge</h2>
            <span v-if="!logsLoading" class="text-sm text-gray-400">Seite {{ logsPage + 1 }}</span>
          </div>

          <!-- Consumption info accordion -->
          <ConsumptionInfoBox :min-trips="5" class="mb-4" />
          <div class="space-y-2">
            <div v-if="logsLoading && !hasAnyLogs" class="py-8 text-center text-gray-400 text-sm">Lade...</div>
            <template v-else-if="!hasAnyLogs">
              <p class="py-8 text-center text-gray-400 text-sm">Keine Ladevorgänge vorhanden.</p>
            </template>
            <template v-else>
              <!-- Session Group (Überschussladen) -->
              <div v-for="entry in mergedLogFeed" :key="entry.id">
              <div v-if="entry._isGroup"
                class="p-3 border border-blue-200 bg-blue-50 rounded-lg space-y-2">
                <!-- Group Header -->
                <div class="flex items-center justify-between gap-2">
                  <div class="flex items-center gap-2 min-w-0">
                    <SunIcon class="w-4 h-4 text-blue-600 flex-shrink-0" />
                    <span class="font-semibold text-blue-700 whitespace-nowrap">{{ entry.totalKwhCharged }} kWh</span>
                    <span class="text-xs text-gray-400 whitespace-nowrap">
                      {{ new Date(entry.sessionStart).toLocaleDateString('de-DE') }}
                      {{ new Date(entry.sessionStart).toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' }) }}
                      –
                      {{ new Date(entry.sessionEnd).toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' }) }}
                    </span>
                  </div>
                  <button @click="toggleGroupExpand(entry.id)"
                    class="flex items-center gap-1 px-2 py-0.5 rounded-full bg-blue-100 text-blue-700 text-xs font-medium hover:bg-blue-200 transition flex-shrink-0">
                    <HomeIcon class="w-3 h-3" />
                    {{ entry.sessionCount }} Ladevorgänge
                    <ChevronDownIcon v-if="!expandedGroups.has(entry.id)" class="w-3 h-3" />
                    <ChevronUpIcon v-else class="w-3 h-3" />
                  </button>
                </div>
                <!-- Group Badges -->
                <div class="flex flex-wrap gap-1.5">
                  <span v-if="entry.costEur != null && entry.totalKwhCharged"
                    class="inline-flex items-center px-2 py-0.5 border border-blue-200 bg-white text-xs rounded-full text-blue-700 whitespace-nowrap">
                    €{{ (entry.costEur / entry.totalKwhCharged).toFixed(2) }}/kWh
                  </span>
                  <span v-if="entry.costEur != null"
                    class="inline-flex items-center gap-1 px-2 py-0.5 bg-white border border-blue-200 rounded-full text-xs text-blue-700 whitespace-nowrap">
                    €{{ entry.costEur }}
                  </span>
                  <span v-if="entry.totalDurationMinutes"
                    class="inline-flex items-center gap-1 px-2 py-0.5 bg-white border border-blue-200 rounded-full text-xs text-blue-700 whitespace-nowrap">
                    <ClockIcon class="w-3 h-3" />{{ entry.totalDurationMinutes }}min
                  </span>
                  <span class="inline-flex items-center gap-0.5 px-1.5 py-0.5 rounded text-xs font-medium bg-blue-100 text-blue-700 whitespace-nowrap">
                    <HomeIcon class="w-3 h-3" />Überschussladen
                  </span>
                </div>
                <!-- Sub-Sessions expandiert -->
                <div v-if="expandedGroups.has(entry.id)" class="mt-2 space-y-1 pl-3 border-l-2 border-blue-200">
                  <div v-if="!subSessionsCache[entry.id]" class="text-xs text-gray-400">Lade...</div>
                  <div v-else-if="subSessionsCache[entry.id].length === 0" class="text-xs text-gray-400">Keine Sub-Sessions gefunden.</div>
                  <div v-else v-for="sub in subSessionsCache[entry.id]" :key="sub.id"
                    class="text-xs text-gray-600 flex items-center gap-2 py-1">
                    <BoltIcon class="w-3 h-3 text-blue-400 flex-shrink-0" />
                    <span class="font-medium">{{ sub.kwhCharged }} kWh</span>
                    <span class="text-gray-400">
                      {{ new Date(sub.loggedAt).toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' }) }}
                    </span>
                    <span v-if="sub.chargeDurationMinutes" class="text-gray-400">{{ sub.chargeDurationMinutes }}min</span>
                    <span v-if="sub.costEur != null" class="text-gray-400">€{{ sub.costEur }}</span>
                  </div>
                </div>
              </div>
              <!-- Normal Log -->
              <div v-else>
              <div
                :class="['p-3 border rounded-lg space-y-2',
                         entry.consumptionImplausible
                           ? 'bg-red-50 border-red-300 border-l-4 border-l-red-400'
                           : 'bg-white border-gray-200']">
                <!-- Header -->
                <div class="flex items-center justify-between gap-2">
                  <div class="flex items-center gap-2 min-w-0">
                    <BoltIcon class="w-4 h-4 text-indigo-600 flex-shrink-0" />
                    <span class="font-semibold text-indigo-700 whitespace-nowrap">{{ entry.kwhCharged }} kWh</span>
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
                    <button @click="editingLog = entry"
                      class="p-1 rounded text-gray-300 hover:text-blue-500 hover:bg-blue-50 transition flex-shrink-0"
                      title="Ladevorgang bearbeiten">
                      <PencilSquareIcon class="w-3.5 h-3.5" />
                    </button>
                    <button @click="deleteLog(entry.id)"
                      class="p-1 rounded text-gray-300 hover:text-red-500 hover:bg-red-50 transition flex-shrink-0">
                      <TrashIcon class="w-3.5 h-3.5" />
                    </button>
                  </div>
                </div>
                <!-- Source Badge: mobile only (desktop: inline im Header) -->
                <div v-if="sourceInfo(entry.dataSource)" class="md:hidden">
                  <span :class="['inline-flex items-center gap-0.5 px-1.5 py-0.5 rounded text-xs font-medium',
                                 sourceInfo(entry.dataSource)!.classes]">
                    <component :is="sourceInfo(entry.dataSource)!.icon" class="w-3 h-3" />
                    {{ sourceInfo(entry.dataSource)!.label }}
                  </span>
                </div>
                <!-- Badges -->
                <div class="flex flex-wrap gap-1.5">
                  <span v-if="entry.costEur != null && entry.kwhCharged"
                    :class="['inline-flex items-center px-2 py-0.5 border text-xs rounded-full font-medium whitespace-nowrap cursor-pointer transition-all duration-75',
                             showCostAbsolute
                               ? 'bg-gray-50 border-gray-200 text-gray-600 shadow-[0_2px_0_0_#d1d5db] hover:shadow-[0_1px_0_0_#d1d5db] hover:translate-y-px active:shadow-none active:translate-y-0.5'
                               : [(costBadgeClass(entry.costEur, entry.kwhCharged) ?? 'bg-green-50 border-green-200 text-green-700'), 'shadow-[0_2px_0_0_#d1d5db] hover:shadow-[0_1px_0_0_#d1d5db] hover:translate-y-px active:shadow-none active:translate-y-0.5'].join(' ')]"
                    @click="showCostAbsolute = !showCostAbsolute">
                    <template v-if="showCostAbsolute">€{{ entry.costEur }}</template>
                    <template v-else>€{{ (entry.costEur / entry.kwhCharged).toFixed(2) }}/kWh</template>
                  </span>
                  <span v-if="entry.consumptionKwhPer100km != null"
                    :class="['inline-flex items-center gap-1 px-2 py-0.5 border rounded-full text-xs font-medium whitespace-nowrap',
                             entry.consumptionImplausible
                               ? 'bg-red-100 border-red-400 text-red-700'
                               : entry.consumptionIsEstimated
                                 ? 'bg-gray-50 border-gray-300 text-gray-500'
                                 : consumptionBadgeClass(entry.consumptionKwhPer100km, stats?.avgConsumptionKwhPer100km ?? null)]"
                    :title="entry.consumptionIsEstimated ? 'Schätzwert: berechnet aus geladener Energie ÷ Distanz, da kein SoC-Wert vorhanden.' : undefined">
                    <ExclamationTriangleIcon v-if="entry.consumptionImplausible" class="w-3 h-3 flex-shrink-0" />
                    {{ entry.consumptionIsEstimated ? '~' : '' }}{{ entry.consumptionKwhPer100km }} kWh/100km
                  </span>
                  <span v-if="entry.costEur != null && !entry.kwhCharged" class="inline-flex items-center gap-1 px-2 py-0.5 bg-gray-50 border border-gray-200 rounded-full text-xs text-gray-600 whitespace-nowrap">
                    €{{ entry.costEur }}
                  </span>
                  <span v-if="entry.chargeDurationMinutes" class="inline-flex items-center gap-1 px-2 py-0.5 bg-gray-50 border border-gray-200 rounded-full text-xs text-gray-600 whitespace-nowrap">
                    <ClockIcon class="w-3 h-3" />{{ entry.chargeDurationMinutes }}min
                  </span>
                  <span
                    v-if="entry.distanceSinceLastChargeKm != null || entry.odometerKm"
                    class="inline-flex items-center gap-1 px-2 py-0.5 bg-gray-50 border border-gray-200 rounded-full text-xs text-gray-600 whitespace-nowrap"
                    :class="entry.distanceSinceLastChargeKm != null && entry.odometerKm
                      ? 'cursor-pointer shadow-[0_2px_0_0_#d1d5db] hover:shadow-[0_1px_0_0_#d1d5db] hover:translate-y-px active:shadow-none active:translate-y-0.5 transition-all duration-75'
                      : ''"
                    @click="toggleOdometerDisplay(entry.distanceSinceLastChargeKm, entry.odometerKm)"
                  >
                    <template v-if="entry.distanceSinceLastChargeKm != null && !showOdometer">+{{ entry.distanceSinceLastChargeKm.toLocaleString('de-DE') }} km</template>
                    <template v-else>{{ entry.odometerKm?.toLocaleString('de-DE') }} km</template>
                  </span>
                  <span v-if="entry.socAfterChargePercent != null" class="inline-flex items-center gap-1 px-2 py-0.5 bg-gray-50 border border-gray-200 rounded-full text-xs text-gray-600 whitespace-nowrap">
                    <Battery0Icon class="w-3 h-3" />{{ entry.socAfterChargePercent }}%
                  </span>
                  <span v-if="entry.maxChargingPowerKw" class="inline-flex items-center gap-1 px-2 py-0.5 bg-gray-50 border border-gray-200 rounded-full text-xs text-gray-600 whitespace-nowrap">
                    <BoltIcon class="w-3 h-3" />{{ entry.maxChargingPowerKw }} kW
                  </span>
                </div>
                <!-- Implausibility warning -->
                <div v-if="entry.consumptionImplausible" class="flex items-start gap-1.5 text-xs text-red-700">
                  <ExclamationTriangleIcon class="w-3.5 h-3.5 flex-shrink-0 mt-0.5" />
                  <span>Verbrauch unplausibel - wahrscheinlich fehlt ein Ladevorgang zwischen diesem und dem vorherigen Eintrag.</span>
                </div>
              </div>
              <!-- Nachladen Sub-Eintraege -->
              <template v-if="entry._topUps && entry._topUps.length > 0">
                <div v-for="topUp in entry._topUps" :key="topUp.id"
                  class="ml-4 mt-1 flex flex-col gap-1.5 px-2.5 py-1.5 bg-gray-50 border border-gray-200 rounded-lg">
                  <!-- Zeile 1: Label + kWh + Zeit + Buttons -->
                  <div class="flex items-center gap-2">
                    <span class="text-gray-300 text-xs leading-none">└</span>
                    <span class="text-xs text-gray-400 whitespace-nowrap">Nachladen</span>
                    <BoltIcon class="w-3.5 h-3.5 text-gray-400 flex-shrink-0" />
                    <span class="text-xs font-semibold text-gray-600 whitespace-nowrap">{{ topUp.kwhCharged }} kWh</span>
                    <span class="text-xs text-gray-400 whitespace-nowrap">
                      <template v-if="new Date(topUp.loggedAt).toDateString() !== new Date(entry.loggedAt).toDateString()">{{ formatLogDate(topUp.loggedAt) }}</template>
                      <template v-else>{{ new Date(topUp.loggedAt).toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' }) }} Uhr</template>
                    </span>
                    <div class="ml-auto flex items-center gap-1 flex-shrink-0">
                      <button @click="editingLog = topUp" class="p-1 rounded text-gray-300 hover:text-blue-500 hover:bg-blue-50 transition" title="Ladevorgang bearbeiten">
                        <PencilSquareIcon class="w-3.5 h-3.5" />
                      </button>
                      <button @click="deleteLog(topUp.id)" class="p-1 rounded text-gray-300 hover:text-red-500 hover:bg-red-50 transition">
                        <TrashIcon class="w-3.5 h-3.5" />
                      </button>
                    </div>
                  </div>
                  <!-- Zeile 2: Badges -->
                  <div v-if="topUp.costEur != null || topUp.chargeDurationMinutes || topUp.socAfterChargePercent != null" class="flex flex-wrap items-center gap-1.5">
                  <span v-if="topUp.costEur != null && topUp.kwhCharged"
                    :class="['inline-flex items-center px-1.5 py-0.5 border rounded-full text-xs font-medium whitespace-nowrap cursor-pointer transition-all duration-75',
                             showCostAbsolute
                               ? 'bg-gray-50 border-gray-200 text-gray-600 shadow-[0_2px_0_0_#d1d5db] hover:shadow-[0_1px_0_0_#d1d5db] hover:translate-y-px active:shadow-none active:translate-y-0.5'
                               : 'bg-green-50 border-green-200 text-green-700 shadow-[0_2px_0_0_#d1d5db] hover:shadow-[0_1px_0_0_#d1d5db] hover:translate-y-px active:shadow-none active:translate-y-0.5']"
                    @click="showCostAbsolute = !showCostAbsolute">
                    <template v-if="showCostAbsolute">€{{ topUp.costEur }}</template>
                    <template v-else>€{{ (topUp.costEur / topUp.kwhCharged).toFixed(2) }}/kWh</template>
                  </span>
                  <span v-else-if="topUp.costEur != null" class="text-xs text-gray-500 whitespace-nowrap">· €{{ topUp.costEur }}</span>
                  <span v-if="topUp.chargeDurationMinutes" class="inline-flex items-center gap-0.5 text-xs text-gray-500 whitespace-nowrap">· <ClockIcon class="w-3 h-3" />{{ topUp.chargeDurationMinutes }}min</span>
                  <span v-if="topUp.socAfterChargePercent != null" class="inline-flex items-center gap-0.5 text-xs text-gray-500 whitespace-nowrap">· <Battery0Icon class="w-3 h-3" />{{ topUp.socAfterChargePercent }}%</span>
                  </div>
                </div>
              </template>
              </div><!-- end normal log wrapper -->
              </div><!-- end v-for entry in mergedLogFeed -->
            </template>
          </div>
          <!-- Pagination -->
          <div class="flex items-center justify-between mt-4">
            <button
              @click="fetchLogs(logsPage - 1)"
              :disabled="logsPage === 0"
              class="flex items-center gap-1 px-3 py-2 text-sm rounded-lg border border-gray-200 disabled:opacity-40 disabled:cursor-not-allowed hover:bg-gray-50 transition">
              <ChevronLeftIcon class="w-4 h-4" />Zurück
            </button>
            <button
              @click="fetchLogs(logsPage + 1)"
              :disabled="!hasMoreLogs"
              class="flex items-center gap-1 px-3 py-2 text-sm rounded-lg border border-gray-200 disabled:opacity-40 disabled:cursor-not-allowed hover:bg-gray-50 transition">
              Weiter<ChevronRightIcon class="w-4 h-4" />
            </button>
          </div>
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

  <EditLogModal
    v-if="editingLog"
    :log="editingLog"
    @close="editingLog = null"
    @saved="() => { editingLog = null; fetchLogs(logsPage) }"
  />
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
