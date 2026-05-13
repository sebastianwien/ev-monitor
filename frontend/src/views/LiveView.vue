<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { useHead } from '@unhead/vue'
import { BoltIcon, ArrowPathIcon } from '@heroicons/vue/24/outline'
import { useAuthStore } from '../stores/auth'
import { useCarStore } from '../stores/car'
import { useChargingLive } from '../composables/useChargingLive'
import CarSelectDropdown from '../components/car/CarSelectDropdown.vue'
import type { Car } from '../api/carService'
import { carDisplayName } from '../utils/enumLabel'

const { t } = useI18n()
const router = useRouter()
const authStore = useAuthStore()
const carStore = useCarStore()

const cars = ref<Car[]>([])
const loading = ref(true)
const selectedCarId = ref<string | null>(null)

// Reactive clock for computed values that depend on current time
const now = ref(Date.now())
let clockInterval: ReturnType<typeof setInterval>
onMounted(() => { clockInterval = setInterval(() => { now.value = Date.now() }, 1000) })
onUnmounted(() => clearInterval(clockInterval))

useHead(computed(() => ({
  title: `${t('live.title')} | EV Monitor`,
})))

const activeCars = computed(() =>
  Array.isArray(cars.value) ? cars.value.filter(c => c.status === 'ACTIVE') : []
)

const canViewLive = computed(() => authStore.canViewLiveTrips)

onMounted(async () => {
  try {
    cars.value = await carStore.getCars()
    if (activeCars.value.length > 0) {
      selectedCarId.value = activeCars.value[0].id
    }
  } finally {
    loading.value = false
  }
})

const { data, powerHistory, loading: liveLoading, refresh } = useChargingLive(
  computed(() => canViewLive.value ? selectedCarId.value : null)
)

// Currently selected car (for header display)
const selectedCar = computed<Car | undefined>(() =>
  activeCars.value.find(c => c.id === selectedCarId.value)
)

// --- Curve geometry ---
// SVG is rendered at viewBox 0 0 600 200. We compute the polyline / area path
// from powerHistory, normalising kW to pixel space with 15% headroom.
const CURVE_W = 600
const CURVE_H = 200

const curveMaxKw = computed(() => {
  const buf = powerHistory.value
  const max = buf.length > 0 ? Math.max(...buf.map(p => p.kw)) : 0
  return Math.max(80, max) * 1.15
})

interface CurvePoint { x: number; y: number }
const curvePoints = computed<CurvePoint[]>(() => {
  const buf = powerHistory.value
  if (buf.length === 0) return []
  if (buf.length === 1) {
    const y = CURVE_H - (buf[0].kw / curveMaxKw.value) * CURVE_H
    return [{ x: 0, y }, { x: CURVE_W, y }]
  }
  const max = curveMaxKw.value
  return buf.map((p, i) => ({
    x: (i / (buf.length - 1)) * CURVE_W,
    y: CURVE_H - (p.kw / max) * CURVE_H,
  }))
})

const curveStrokePath = computed(() => {
  const pts = curvePoints.value
  if (pts.length === 0) return ''
  return pts.map((p, i) => `${i === 0 ? 'M' : 'L'} ${p.x.toFixed(1)} ${p.y.toFixed(1)}`).join(' ')
})

const curveFillPath = computed(() => {
  const pts = curvePoints.value
  if (pts.length === 0) return ''
  const lastX = pts[pts.length - 1].x.toFixed(1)
  return `${curveStrokePath.value} L ${lastX} ${CURVE_H} L 0 ${CURVE_H} Z`
})

const curveLastPoint = computed<CurvePoint | null>(() => {
  const pts = curvePoints.value
  return pts.length > 0 ? pts[pts.length - 1] : null
})

// Y-axis gridlines & labels - feste 25-kW-Schritte (25, 50, 75, 100, ...).
// Ticks innerhalb der sichtbaren Hoehe; oberer Rand der Curve wird nicht beschriftet.
const curveYTicks = computed(() => {
  const max = curveMaxKw.value
  const STEP = 25
  const ticks: { kw: number; y: number }[] = []
  for (let kw = STEP; kw < max; kw += STEP) {
    ticks.push({ kw, y: CURVE_H - (kw / max) * CURVE_H })
  }
  return ticks
})

// X-axis labels: 5 evenly spaced timestamps from first→last history point.
const curveXLabels = computed<{ label: string; isNow: boolean }[]>(() => {
  const buf = powerHistory.value
  if (buf.length < 2) {
    const nowLabel = new Date(now.value).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' })
    return [
      { label: '', isNow: false },
      { label: '', isNow: false },
      { label: '', isNow: false },
      { label: '', isNow: false },
      { label: `${t('live.now_short')} ${nowLabel}`, isNow: true },
    ]
  }
  const first = buf[0].ts
  const last = buf[buf.length - 1].ts
  const span = last - first
  const labels: { label: string; isNow: boolean }[] = []
  for (let i = 0; i < 5; i++) {
    const ts = first + (span * i / 4)
    const hhmm = new Date(ts).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' })
    const isLast = i === 4
    labels.push({
      label: isLast ? `${t('live.now_short')} ${hhmm}` : hhmm,
      isNow: isLast,
    })
  }
  return labels
})

// Window for curve title: minutes covered by current history buffer
const curveWindowMinutes = computed(() => {
  const buf = powerHistory.value
  if (buf.length < 2) return 0
  return Math.round((buf[buf.length - 1].ts - buf[0].ts) / 60000)
})

// SoC percentage clamped 0..100 for the wave bar
const socPct = computed(() => Math.max(0, Math.min(100, data.value?.socPercent ?? 0)))

// Duration since session started - reactive via now.value
const sessionDuration = computed(() => {
  if (!data.value?.sessionStartedAt) return null
  const start = new Date(data.value.sessionStartedAt).getTime()
  const diffMs = Math.max(0, now.value - start)
  const totalSec = Math.floor(diffMs / 1000)
  const h = Math.floor(totalSec / 3600)
  const m = Math.floor((totalSec % 3600) / 60)
  if (h > 0) return `${h}h ${m}min`
  return `${m} ${t('live.minutes_short')}`
})

// Seconds since last update - reactive via now.value
const secondsSinceUpdate = computed(() => {
  if (!data.value?.lastUpdatedAt) return null
  const updated = new Date(data.value.lastUpdatedAt).getTime()
  return Math.floor((now.value - updated) / 1000)
})

// Stale data indicators
const dataIsStale = computed(() => (secondsSinceUpdate.value ?? 0) > 60)
const dataIsVeryStale = computed(() => (secondsSinceUpdate.value ?? 0) > 120)

// Effective SoC start: socAtSessionStart if available, else 0
const socStart = computed(() => data.value?.socAtSessionStart ?? 0)
// Effective target: chargeLimitSoc if available, else 80
const socTarget = computed(() => data.value?.chargeLimitSoc ?? 80)
// Session start time formatted as HH:MM
const sessionStartTime = computed(() => {
  if (!data.value?.sessionStartedAt) return null
  return new Date(data.value.sessionStartedAt).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' })
})

function formatNumber(val: number | null, decimals = 1): string {
  if (val === null || val === undefined) return '-'
  return val.toLocaleString(undefined, { minimumFractionDigits: decimals, maximumFractionDigits: decimals })
}
</script>

<template>
  <div class="md:max-w-2xl md:mx-auto md:p-6">
    <!-- Page loading skeleton -->
    <div v-if="loading" class="bg-white dark:bg-gray-800 md:rounded-xl md:shadow-lg p-4 md:p-6">
      <div class="animate-pulse space-y-4">
        <div class="h-8 bg-gray-200 dark:bg-gray-700 rounded w-1/3"></div>
        <div class="h-4 bg-gray-200 dark:bg-gray-700 rounded w-1/2"></div>
        <div class="h-40 bg-gray-200 dark:bg-gray-700 rounded"></div>
      </div>
    </div>

    <div v-else class="bg-white dark:bg-gray-800 md:rounded-xl md:shadow-lg p-4 md:p-6">
      <!-- Header -->
      <div class="mb-5">
        <div class="flex items-center gap-3 mb-1.5">
          <BoltIcon class="h-7 w-7 text-emerald-500" />
          <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">{{ t('live.title') }}</h1>
        </div>
        <p class="text-gray-600 dark:text-gray-400 text-sm">{{ t('live.subtitle') }}</p>
      </div>

      <!-- Upgrade teaser - shown for non-entitled users -->
      <div
        v-if="!canViewLive"
        class="border-2 border-gray-900 dark:border-white bg-amber-50 dark:bg-amber-950/30 rounded-sm shadow-[4px_4px_0_0_#030712] dark:shadow-[4px_4px_0_0_#ffffff] p-5"
      >
        <div class="flex flex-col items-center md:flex-row md:items-start gap-4">
          <div class="shrink-0 rounded-sm border-2 border-gray-900 dark:border-white bg-amber-500 p-2.5 w-12 h-12 flex items-center justify-center">
            <BoltIcon class="h-6 w-6 text-gray-950" />
          </div>
          <div class="flex-1 min-w-0 text-center md:text-left">
            <p class="text-amber-600 dark:text-amber-400 text-[11px] font-bold uppercase tracking-[0.14em] mb-1">
              AutoSync Live
            </p>
            <p class="font-bold text-gray-900 dark:text-white text-lg mb-1 tracking-tight">
              {{ t('live.upgrade_title') }}
            </p>
            <p id="live-upgrade-desc" class="text-sm text-gray-600 dark:text-gray-300 mb-4 font-medium leading-relaxed">
              {{ t('live.upgrade_desc') }}
            </p>
            <button
              @click="router.push('/upgrade')"
              aria-describedby="live-upgrade-desc"
              class="inline-flex items-center gap-2 bg-amber-500 hover:bg-amber-400 text-gray-950 font-bold uppercase tracking-wider text-xs px-5 py-3 rounded-sm border-2 border-gray-900 shadow-[3px_3px_0_0_#030712] active:translate-x-[3px] active:translate-y-[3px] active:shadow-none transition-[transform,box-shadow] duration-75"
            >
              <BoltIcon class="h-4 w-4" />
              {{ t('live.upgrade_cta') }}
            </button>
          </div>
        </div>
      </div>

      <!-- Content for entitled users -->
      <template v-else>
        <!-- Car selector (only if multiple cars) -->
        <div v-if="activeCars.length > 1" class="mb-4">
          <CarSelectDropdown :cars="activeCars" v-model="selectedCarId" />
        </div>

        <!-- No cars state -->
        <div v-if="activeCars.length === 0" class="text-center py-10 text-gray-500 dark:text-gray-400 text-sm">
          {{ t('cars.no_cars') }}
        </div>

        <!-- Live data spinner (first load) -->
        <div v-else-if="liveLoading && !data" class="flex items-center justify-center py-12">
          <ArrowPathIcon class="h-8 w-8 animate-spin text-gray-400" />
        </div>

        <!-- No active session -->
        <div
          v-else-if="!data?.isActive"
          class="border-2 border-gray-200 dark:border-gray-700 rounded-sm p-6 text-center"
        >
          <div class="mb-3 flex items-center justify-center">
            <span class="w-3 h-3 rounded-full bg-gray-300 dark:bg-gray-600"></span>
          </div>
          <p class="font-bold text-gray-900 dark:text-white text-base mb-1">{{ t('live.no_session') }}</p>
          <p class="text-sm text-gray-500 dark:text-gray-400">{{ t('live.no_session_hint') }}</p>
        </div>

        <!-- Active charging session card - Live Stream design (Design 3) -->
        <section
          v-else
          class="bg-white dark:bg-gray-800/80 border border-gray-200 dark:border-gray-700 rounded-md shadow-lg overflow-hidden"
        >
          <!-- Header: car identity left, charging-type + LIVE pill right -->
          <header class="flex items-center justify-between px-4 sm:px-6 py-4 border-b border-gray-200 dark:border-gray-700/70">
            <div class="flex items-center gap-3 min-w-0">
              <div class="w-9 h-9 shrink-0 rounded-md bg-emerald-500/15 border border-emerald-500/30 flex items-center justify-center">
                <BoltIcon class="w-5 h-5 text-emerald-500 dark:text-emerald-400" />
              </div>
              <div class="min-w-0">
                <h2 class="text-sm font-semibold text-gray-900 dark:text-white leading-tight truncate">
                  {{ selectedCar ? carDisplayName(selectedCar.brand, selectedCar.model) : 'Tesla Model 3' }}
                </h2>
                <p v-if="selectedCar?.licensePlate" class="text-xs text-gray-500 dark:text-gray-400 leading-tight mt-0.5 tracking-wide truncate">
                  {{ selectedCar.licensePlate }}
                </p>
              </div>
            </div>

            <div class="flex items-center gap-2 sm:gap-3 shrink-0">
              <div
                v-if="data.chargingType"
                class="hidden sm:flex items-center gap-1.5 px-2.5 py-1 rounded-md border"
                :class="data.chargingType === 'DC'
                  ? 'bg-amber-500/10 border-amber-500/30 text-amber-700 dark:text-amber-300'
                  : 'bg-sky-500/10 border-sky-500/30 text-sky-700 dark:text-sky-300'"
              >
                <BoltIcon class="w-3 h-3" />
                <span class="text-[10px] font-semibold tracking-[0.12em] uppercase">
                  {{ data.chargingType === 'DC' ? t('live.dc') : t('live.ac') }}
                </span>
              </div>
              <div class="flex items-center gap-2 px-2.5 py-1.5 rounded-md bg-emerald-500/10 border border-emerald-500/30">
                <span class="relative flex h-2 w-2">
                  <span class="absolute inline-flex h-full w-full rounded-full bg-emerald-400 pulse-ring"></span>
                  <span class="relative inline-flex rounded-full h-2 w-2 bg-emerald-500 dark:bg-emerald-400 pulse-dot"></span>
                </span>
                <span class="text-[11px] font-semibold tracking-wider text-emerald-700 dark:text-emerald-300 uppercase">{{ t('live.live') }}</span>
              </div>
            </div>
          </header>

          <!-- Stale warning -->
          <div v-if="dataIsStale" class="border-l-4 border-amber-500 bg-amber-50 dark:bg-amber-950/30 px-3 py-2 text-xs font-medium text-amber-700 dark:text-amber-300">
            {{ t('live.stale_warning') }}
          </div>

          <!-- Curve area -->
          <div class="relative dot-pattern px-4 sm:px-6 pt-5 pb-3" :class="{ 'opacity-50': dataIsVeryStale }">
            <!-- Top label row -->
            <div class="flex items-end justify-between mb-2 px-1">
              <p class="text-[10px] font-semibold tracking-[0.18em] uppercase text-gray-500">
                {{ t('live.power_curve') }}
                <span v-if="curveWindowMinutes > 0"> · {{ t('live.last_minutes', { count: curveWindowMinutes }) }}</span>
              </p>
              <div class="flex items-baseline gap-1">
                <span class="text-3xl font-bold text-gray-900 dark:text-white tabular-nums leading-none">
                  <template v-if="data.powerKw != null">{{ formatNumber(data.powerKw) }}</template>
                  <span v-else class="opacity-40">-</span>
                </span>
                <span class="text-sm font-medium text-gray-500 dark:text-gray-400">kW</span>
              </div>
            </div>

            <!-- SVG curve -->
            <div class="relative">
              <svg
                viewBox="0 0 600 200"
                class="w-full h-[180px]"
                preserveAspectRatio="none"
                role="img"
                :aria-label="t('live.power_curve')"
              >
                <defs>
                  <linearGradient id="curveFill" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stop-color="#10b981" stop-opacity="0.45" />
                    <stop offset="60%" stop-color="#10b981" stop-opacity="0.12" />
                    <stop offset="100%" stop-color="#10b981" stop-opacity="0" />
                  </linearGradient>
                  <linearGradient id="curveStroke" x1="0" y1="0" x2="1" y2="0">
                    <stop offset="0%" stop-color="#059669" />
                    <stop offset="100%" stop-color="#34d399" />
                  </linearGradient>
                  <filter id="glow" x="-20%" y="-20%" width="140%" height="140%">
                    <feGaussianBlur stdDeviation="3" result="blur" />
                    <feMerge>
                      <feMergeNode in="blur" />
                      <feMergeNode in="SourceGraphic" />
                    </feMerge>
                  </filter>
                </defs>

                <!-- Horizontal grid -->
                <g stroke="currentColor" class="text-gray-300 dark:text-slate-700" stroke-width="1" stroke-dasharray="2 4" opacity="0.6">
                  <line v-for="(tick, i) in curveYTicks" :key="`g-${i}`" x1="0" :y1="tick.y" x2="600" :y2="tick.y" />
                </g>

                <!-- Y-axis labels -->
                <g fill="currentColor" class="text-gray-500 dark:text-slate-500" font-size="9" font-family="system-ui">
                  <text v-for="(tick, i) in curveYTicks" :key="`t-${i}`" x="6" :y="tick.y + 4">{{ tick.kw }} kW</text>
                </g>

                <!-- Fill under curve -->
                <path
                  v-if="curveFillPath"
                  :d="curveFillPath"
                  fill="url(#curveFill)"
                />

                <!-- Curve stroke -->
                <path
                  v-if="curveStrokePath"
                  :d="curveStrokePath"
                  fill="none"
                  stroke="url(#curveStroke)"
                  stroke-width="2.5"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  filter="url(#glow)"
                />

                <!-- Current value marker -->
                <template v-if="curveLastPoint">
                  <line
                    :x1="curveLastPoint.x" :y1="curveLastPoint.y" :x2="curveLastPoint.x" y2="200"
                    stroke="#10b981" stroke-width="1" stroke-dasharray="2 3" opacity="0.5"
                  />
                  <circle :cx="curveLastPoint.x" :cy="curveLastPoint.y" r="8" fill="#10b981" opacity="0.25" />
                  <circle :cx="curveLastPoint.x" :cy="curveLastPoint.y" r="4" fill="#10b981" class="marker-pulse" />
                  <circle :cx="curveLastPoint.x" :cy="curveLastPoint.y" r="2" fill="#ffffff" />
                </template>
              </svg>

              <!-- Loading state overlay -->
              <div
                v-if="curvePoints.length === 0"
                class="absolute inset-0 flex items-center justify-center text-xs text-gray-500 dark:text-gray-400"
              >
                {{ t('live.loading_data') }}
              </div>

              <!-- X axis labels -->
              <div class="flex justify-between text-[10px] text-gray-500 dark:text-gray-500 px-1 tabular-nums mt-1">
                <span
                  v-for="(lab, i) in curveXLabels"
                  :key="`x-${i}`"
                  :class="lab.isNow ? 'text-emerald-600 dark:text-emerald-400 font-semibold' : 'text-gray-500 dark:text-gray-600'"
                >{{ lab.label }}</span>
              </div>
            </div>
          </div>

          <!-- SoC bar with wave-flow -->
          <div class="px-4 sm:px-6 py-4 border-t border-gray-200 dark:border-gray-700/70" :class="{ 'opacity-50': dataIsVeryStale }">
            <div class="flex items-end justify-between mb-2 gap-2">
              <div class="flex items-baseline gap-2 flex-wrap min-w-0">
                <span class="text-[10px] font-semibold tracking-[0.18em] uppercase text-gray-500">{{ t('live.soc') }}</span>
                <span class="text-xl font-bold text-gray-900 dark:text-white tabular-nums">{{ Math.round(socPct) }}%</span>
                <span v-if="socStart > 0" class="text-[11px] text-gray-500 tabular-nums whitespace-nowrap">← {{ Math.round(socStart) }}% {{ t('live.start_label') }}</span>
              </div>
              <span class="text-[11px] text-amber-600 dark:text-amber-400 font-semibold tabular-nums whitespace-nowrap">{{ t('live.target_label') }} {{ socTarget }}%</span>
            </div>

            <div class="relative h-3 bg-gray-200 dark:bg-gray-700/60 rounded-full overflow-hidden">
              <div class="absolute inset-y-0 left-0 wave-flow rounded-full" :style="`width: ${socPct}%;`"></div>
              <!-- Target marker -->
              <div class="absolute top-0 bottom-0 w-px bg-amber-400/70" :style="`left: ${socTarget}%;`"></div>
              <div
                class="absolute -top-0.5 w-2 h-2 rounded-full bg-amber-400 shadow-[0_0_8px_rgba(251,191,36,0.6)]"
                :style="`left: calc(${socTarget}% - 4px);`"
              ></div>
            </div>
          </div>

          <!-- Metric strip: 2x2 on mobile, 4-col on sm+ -->
          <div
            class="grid grid-cols-2 sm:grid-cols-4 border-t border-gray-200 dark:border-gray-700/70 divide-x divide-y sm:divide-y-0 divide-gray-200 dark:divide-gray-700/70"
            :class="{ 'opacity-50': dataIsVeryStale }"
          >
            <!-- Bis Ziel% -->
            <div class="px-4 py-4">
              <div class="flex items-center gap-1.5 mb-1">
                <svg class="w-3 h-3 text-amber-500 dark:text-amber-400" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
                <p class="text-[10px] font-semibold tracking-[0.12em] uppercase text-gray-500">{{ t('live.until_target', { pct: socTarget }) }}</p>
              </div>
              <p class="text-lg font-bold text-gray-900 dark:text-white tabular-nums">
                <template v-if="data.timeToFullMinutes != null">{{ data.timeToFullMinutes }}<span class="text-xs text-gray-500 dark:text-gray-400 ml-1 font-medium">{{ t('live.minutes_short') }}</span></template>
                <template v-else>-</template>
              </p>
            </div>

            <!-- Reichweite -->
            <div class="px-4 py-4">
              <div class="flex items-center gap-1.5 mb-1">
                <svg class="w-3 h-3 text-sky-500 dark:text-sky-400" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6" />
                </svg>
                <p class="text-[10px] font-semibold tracking-[0.12em] uppercase text-gray-500">{{ t('live.range') }}</p>
              </div>
              <p class="text-lg font-bold text-gray-900 dark:text-white tabular-nums">
                <template v-if="data.estRangeKm != null">{{ formatNumber(data.estRangeKm, 0) }}<span class="text-xs text-gray-500 dark:text-gray-400 ml-1 font-medium">km</span></template>
                <template v-else>-</template>
              </p>
            </div>

            <!-- Strom -->
            <div class="px-4 py-4">
              <div class="flex items-center gap-1.5 mb-1">
                <svg class="w-3 h-3 text-emerald-500 dark:text-emerald-400" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M13 10V3L4 14h7v7l9-11h-7z" />
                </svg>
                <p class="text-[10px] font-semibold tracking-[0.12em] uppercase text-gray-500">{{ t('live.amps') }}</p>
              </div>
              <p class="text-lg font-bold text-gray-900 dark:text-white tabular-nums">
                <template v-if="data.chargeAmps != null">{{ formatNumber(data.chargeAmps, 0) }}<span class="text-xs text-gray-500 dark:text-gray-400 ml-1 font-medium">A</span></template>
                <template v-else>-</template>
              </p>
            </div>

            <!-- Dauer -->
            <div class="px-4 py-4">
              <div class="flex items-center gap-1.5 mb-1">
                <svg class="w-3 h-3 text-indigo-500 dark:text-indigo-400" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
                </svg>
                <p class="text-[10px] font-semibold tracking-[0.12em] uppercase text-gray-500">{{ t('live.duration') }}</p>
              </div>
              <p class="text-lg font-bold text-gray-900 dark:text-white tabular-nums">
                {{ sessionDuration ?? '-' }}
              </p>
            </div>
          </div>

          <!-- Footer -->
          <footer class="flex items-center justify-between gap-3 px-4 sm:px-6 py-3 bg-gray-50 dark:bg-gray-900/40 border-t border-gray-200 dark:border-gray-700/70">
            <p v-if="sessionStartTime" class="text-[11px] text-gray-500 dark:text-gray-500 tabular-nums min-w-0 truncate">
              {{ t('live.start_label') }} {{ sessionStartTime }}
            </p>
            <span v-else></span>
            <div class="flex items-center gap-3 shrink-0">
              <div class="flex items-center gap-2">
                <span class="w-1.5 h-1.5 rounded-full bg-emerald-500 dark:bg-emerald-400 pulse-dot shrink-0"></span>
                <p class="text-[11px] text-gray-500 dark:text-gray-400 tabular-nums">
                  {{ secondsSinceUpdate != null ? t('live.seconds_ago', { n: secondsSinceUpdate }) : '-' }}
                </p>
              </div>
              <button
                @click="refresh()"
                :aria-label="t('live.refresh')"
                class="p-1.5 rounded-md border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
              >
                <ArrowPathIcon class="h-4 w-4 text-gray-500 dark:text-gray-400" />
              </button>
            </div>
          </footer>
        </section>
      </template>
    </div>
  </div>
</template>

<style scoped>
@keyframes pulse-dot {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.4; transform: scale(0.85); }
}
@keyframes pulse-ring {
  0% { transform: scale(1); opacity: 0.6; }
  100% { transform: scale(2.4); opacity: 0; }
}
@keyframes marker-pulse {
  0%, 100% { r: 4; opacity: 1; }
  50% { r: 6; opacity: 0.85; }
}
@keyframes wave-flow {
  0% { background-position: 0% 50%; }
  100% { background-position: 200% 50%; }
}

.pulse-dot { animation: pulse-dot 1.5s ease-in-out infinite; }
.pulse-ring { animation: pulse-ring 1.8s cubic-bezier(0.215, 0.61, 0.355, 1) infinite; }
.marker-pulse {
  animation: marker-pulse 1.6s ease-in-out infinite;
  transform-origin: center;
  transform-box: fill-box;
}
.wave-flow {
  background: linear-gradient(90deg,
    #059669 0%,
    #10b981 30%,
    #34d399 50%,
    #10b981 70%,
    #059669 100%);
  background-size: 200% 100%;
  animation: wave-flow 3s linear infinite;
}

/* Subtle dot pattern behind the curve area */
.dot-pattern {
  background-image: radial-gradient(circle, rgba(148, 163, 184, 0.15) 1px, transparent 1px);
  background-size: 16px 16px;
}
:global(.dark) .dot-pattern,
.dark .dot-pattern {
  background-image: radial-gradient(circle, rgba(148, 163, 184, 0.08) 1px, transparent 1px);
}
</style>
