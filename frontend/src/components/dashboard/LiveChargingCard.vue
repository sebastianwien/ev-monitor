<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '../../stores/auth'
import { useChargingLive } from '../../composables/useChargingLive'
import { BoltIcon, ArrowPathIcon, ChevronUpIcon, ChevronDownIcon } from '@heroicons/vue/24/outline'

const props = defineProps<{
  carId: string | null
  carDisplayName: string
  licensePlate: string | null
}>()

const { t } = useI18n()
const authStore = useAuthStore()

// Reaktiver Wall-Clock-Ticker fuer Dauer / "vor X Sek." / Stale-Detection
const now = ref(Date.now())
let clockInterval: ReturnType<typeof setInterval>
onMounted(() => { clockInterval = setInterval(() => { now.value = Date.now() }, 1000) })
onUnmounted(() => clearInterval(clockInterval))

const carIdRef = computed(() => authStore.canViewLiveCharging ? props.carId : null)
const { data, powerHistory, refresh } = useChargingLive(carIdRef)

// Sichtbarkeit: nur wenn entitled + aktiver Ladevorgang
const visible = computed(() => authStore.canViewLiveCharging && !!data.value?.isActive)

// Collapse-State: persistiert pro (carId, sessionStartedAt) - jede neue Session
// startet expanded. localStorage-Key wechselt mit Session-Start.
const sessionKey = computed(() => {
  if (!props.carId || !data.value?.sessionStartedAt) return null
  return `live_card_collapsed_${props.carId}_${data.value.sessionStartedAt}`
})
const collapsed = ref(false)
watch(sessionKey, key => {
  collapsed.value = key ? localStorage.getItem(key) === '1' : false
}, { immediate: true })
function toggleCollapsed() {
  collapsed.value = !collapsed.value
  if (sessionKey.value) localStorage.setItem(sessionKey.value, collapsed.value ? '1' : '0')
}

// --- Curve geometry: viewBox 0 0 600 200, 15% Headroom auf max(80, peak) ---
const CURVE_W = 600
const CURVE_H = 200
const curveMaxKw = computed(() => {
  const buf = powerHistory.value
  const max = buf.length > 0 ? Math.max(...buf.map(p => p.kw)) : 0
  // Nur ~5% Headroom: bei Peak 250kW endet die Y-Achse bei ~263kW, kein
  // 275-kW-Tick mehr nach oben, Kurve nutzt die volle Hoehe.
  return Math.max(80, max) * 1.05
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
// Y-axis gridlines & labels - feste 25-kW-Schritte
const curveYTicks = computed(() => {
  const max = curveMaxKw.value
  const STEP = 25
  const ticks: { kw: number; y: number }[] = []
  for (let kw = STEP; kw < max; kw += STEP) ticks.push({ kw, y: CURVE_H - (kw / max) * CURVE_H })
  return ticks
})
// X-axis labels: 5 evenly spaced timestamps
const curveXLabels = computed<{ label: string; isNow: boolean }[]>(() => {
  const buf = powerHistory.value
  if (buf.length < 2) {
    const nowLabel = new Date(now.value).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' })
    return [{ label: '', isNow: false }, { label: '', isNow: false }, { label: '', isNow: false }, { label: '', isNow: false }, { label: `${t('live.now_short')} ${nowLabel}`, isNow: true }]
  }
  const first = buf[0].ts
  const last = buf[buf.length - 1].ts
  const span = last - first
  const labels: { label: string; isNow: boolean }[] = []
  for (let i = 0; i < 5; i++) {
    const ts = first + (span * i / 4)
    const hhmm = new Date(ts).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' })
    const isLast = i === 4
    labels.push({ label: isLast ? `${t('live.now_short')} ${hhmm}` : hhmm, isNow: isLast })
  }
  return labels
})
const curveWindowMinutes = computed(() => {
  const buf = powerHistory.value
  if (buf.length < 2) return 0
  return Math.round((buf[buf.length - 1].ts - buf[0].ts) / 60000)
})
const socPct = computed(() => Math.max(0, Math.min(100, data.value?.socPercent ?? 0)))
const socStart = computed(() => data.value?.socAtSessionStart ?? 0)
const socTarget = computed(() => data.value?.chargeLimitSoc ?? 80)
const sessionStartTime = computed(() => {
  if (!data.value?.sessionStartedAt) return null
  return new Date(data.value.sessionStartedAt).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' })
})
const sessionDuration = computed(() => {
  if (!data.value?.sessionStartedAt) return null
  const start = new Date(data.value.sessionStartedAt).getTime()
  const totalSec = Math.floor(Math.max(0, now.value - start) / 1000)
  const h = Math.floor(totalSec / 3600)
  const m = Math.floor((totalSec % 3600) / 60)
  if (h > 0) return `${h}h ${m}min`
  return `${m} ${t('live.minutes_short')}`
})
const secondsSinceUpdate = computed(() => {
  if (!data.value?.lastUpdatedAt) return null
  return Math.floor((now.value - new Date(data.value.lastUpdatedAt).getTime()) / 1000)
})
const dataIsStale = computed(() => (secondsSinceUpdate.value ?? 0) > 60)
const dataIsVeryStale = computed(() => (secondsSinceUpdate.value ?? 0) > 120)

function formatNumber(val: number | null, decimals = 1): string {
  if (val === null || val === undefined) return '-'
  return val.toLocaleString(undefined, { minimumFractionDigits: decimals, maximumFractionDigits: decimals })
}
</script>

<template>
  <section
    v-if="visible"
    class="bg-white dark:bg-gray-800/80 border border-gray-200 dark:border-gray-700 rounded-md shadow-lg overflow-hidden"
  >
    <!-- Collapsed strip -->
    <div
      v-if="collapsed"
      class="flex items-center gap-3 px-4 sm:px-6 py-3"
    >
      <span class="relative flex h-2 w-2 shrink-0">
        <span class="absolute inline-flex h-full w-full rounded-full bg-emerald-400 pulse-ring"></span>
        <span class="relative inline-flex rounded-full h-2 w-2 bg-emerald-500 dark:bg-emerald-400 pulse-dot"></span>
      </span>
      <span class="text-[11px] font-semibold tracking-wider text-emerald-700 dark:text-emerald-300 uppercase shrink-0">{{ t('live.live') }}</span>
      <span class="text-sm font-bold text-gray-900 dark:text-white tabular-nums shrink-0">
        <template v-if="data?.powerKw != null">{{ formatNumber(data.powerKw) }} kW</template>
        <template v-else>-</template>
      </span>
      <span v-if="data?.chargingType" class="text-[10px] font-semibold tracking-[0.12em] uppercase shrink-0"
            :class="data.chargingType === 'DC' ? 'text-amber-600 dark:text-amber-400' : 'text-sky-600 dark:text-sky-400'">
        {{ data.chargingType }}
      </span>
      <div class="flex-1 h-1.5 bg-gray-200 dark:bg-gray-700/60 rounded-full overflow-hidden min-w-[60px]">
        <div class="h-full wave-flow rounded-full" :style="`width: ${socPct}%;`"></div>
      </div>
      <span class="text-xs font-semibold text-gray-700 dark:text-gray-200 tabular-nums shrink-0">{{ Math.round(socPct) }}%</span>
      <span v-if="data?.timeToFullMinutes != null" class="hidden sm:inline text-[11px] text-gray-500 dark:text-gray-400 tabular-nums whitespace-nowrap shrink-0">
        {{ t('live.until_target', { pct: socTarget }) }}: {{ data.timeToFullMinutes }} {{ t('live.minutes_short') }}
      </span>
      <button
        @click="toggleCollapsed"
        :aria-label="t('live.expand_aria')"
        class="p-1 rounded-md hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors shrink-0"
      >
        <ChevronDownIcon class="h-4 w-4 text-gray-500 dark:text-gray-400" />
      </button>
    </div>

    <!-- Expanded full card -->
    <template v-else>
      <header class="flex items-center justify-between px-4 sm:px-6 py-4 border-b border-gray-200 dark:border-gray-700/70">
        <div class="flex items-center gap-3 min-w-0">
          <div class="w-9 h-9 shrink-0 rounded-md bg-emerald-500/15 border border-emerald-500/30 flex items-center justify-center">
            <BoltIcon class="w-5 h-5 text-emerald-500 dark:text-emerald-400" />
          </div>
          <div class="min-w-0">
            <h2 class="text-sm font-semibold text-gray-900 dark:text-white leading-tight truncate">
              {{ carDisplayName || 'Tesla Model 3' }}
            </h2>
            <p v-if="licensePlate" class="text-xs text-gray-500 dark:text-gray-400 leading-tight mt-0.5 tracking-wide truncate">
              {{ licensePlate }}
            </p>
          </div>
        </div>
        <div class="flex items-center gap-2 sm:gap-3 shrink-0">
          <div
            v-if="data?.chargingType"
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
          <button
            @click="toggleCollapsed"
            :aria-label="t('live.collapse_aria')"
            class="p-1 rounded-md hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
          >
            <ChevronUpIcon class="h-4 w-4 text-gray-500 dark:text-gray-400" />
          </button>
        </div>
      </header>

      <div v-if="dataIsStale" class="border-l-4 border-amber-500 bg-amber-50 dark:bg-amber-950/30 px-3 py-2 text-xs font-medium text-amber-700 dark:text-amber-300">
        {{ t('live.stale_warning') }}
      </div>

      <!-- Curve -->
      <div class="relative px-4 sm:px-6 pt-3 pb-3" :class="{ 'opacity-50': dataIsVeryStale }">
        <div class="relative">
          <svg viewBox="0 0 600 200" class="w-full h-[234px]" preserveAspectRatio="none" role="img" :aria-label="t('live.power_curve')">
            <defs>
              <linearGradient id="liveCurveFill" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stop-color="#10b981" stop-opacity="0.45" />
                <stop offset="60%" stop-color="#10b981" stop-opacity="0.12" />
                <stop offset="100%" stop-color="#10b981" stop-opacity="0" />
              </linearGradient>
              <linearGradient id="liveCurveStroke" x1="0" y1="0" x2="1" y2="0">
                <stop offset="0%" stop-color="#059669" />
                <stop offset="100%" stop-color="#34d399" />
              </linearGradient>
              <filter id="liveGlow" x="-20%" y="-20%" width="140%" height="140%">
                <feGaussianBlur stdDeviation="3" result="blur" />
                <feMerge><feMergeNode in="blur" /><feMergeNode in="SourceGraphic" /></feMerge>
              </filter>
            </defs>
            <g stroke="currentColor" class="text-gray-300 dark:text-slate-700" stroke-width="1" stroke-dasharray="2 4" opacity="0.6">
              <line v-for="(tick, i) in curveYTicks" :key="`g-${i}`" x1="0" :y1="tick.y" x2="600" :y2="tick.y" />
            </g>
            <path v-if="curveFillPath" :d="curveFillPath" fill="url(#liveCurveFill)" />
            <path v-if="curveStrokePath" :d="curveStrokePath" fill="none" stroke="url(#liveCurveStroke)" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" filter="url(#liveGlow)" />
            <template v-if="curveLastPoint">
              <line :x1="curveLastPoint.x" :y1="curveLastPoint.y" :x2="curveLastPoint.x" y2="200" stroke="#10b981" stroke-width="1" stroke-dasharray="2 3" opacity="0.5" />
              <circle :cx="curveLastPoint.x" :cy="curveLastPoint.y" r="8" fill="#10b981" opacity="0.25" />
              <circle :cx="curveLastPoint.x" :cy="curveLastPoint.y" r="4" fill="#10b981" class="marker-pulse" />
              <circle :cx="curveLastPoint.x" :cy="curveLastPoint.y" r="2" fill="#ffffff" />
            </template>
          </svg>
          <!-- Y-Achse als HTML-Overlay (aspect-preserving) -->
          <div class="pointer-events-none absolute inset-0">
            <span
              v-for="(tick, i) in curveYTicks"
              :key="`yt-${i}`"
              class="absolute left-1.5 text-[9px] text-gray-500 dark:text-slate-500 tabular-nums leading-none"
              :style="`top: calc(${(tick.y / 200) * 100}% - 0.3em);`"
            >{{ tick.kw }} kW</span>
          </div>
          <!-- kW-Anzeige als Overlay rechts oben (Platz sparen) -->
          <div class="pointer-events-none absolute top-1 right-2 text-right">
            <p class="text-[10px] font-semibold tracking-[0.12em] uppercase text-gray-500 mb-0.5">
              {{ t('live.power_curve') }}
              <span v-if="curveWindowMinutes > 0"> · {{ t('live.last_minutes', { count: curveWindowMinutes }) }}</span>
            </p>
            <div class="flex items-baseline gap-1 justify-end">
              <span class="text-3xl font-bold text-gray-900 dark:text-white tabular-nums leading-none">
                <template v-if="data?.powerKw != null">{{ formatNumber(data.powerKw) }}</template>
                <span v-else class="opacity-40">-</span>
              </span>
              <span class="text-sm font-medium text-gray-500 dark:text-gray-400">kW</span>
            </div>
          </div>
          <div v-if="curvePoints.length === 0" class="absolute inset-0 flex items-center justify-center text-xs text-gray-500 dark:text-gray-400">
            {{ t('live.loading_data') }}
          </div>
          <div class="flex justify-between text-[10px] text-gray-500 dark:text-gray-500 px-1 tabular-nums mt-1">
            <span v-for="(lab, i) in curveXLabels" :key="`x-${i}`"
                  :class="lab.isNow ? 'text-emerald-600 dark:text-emerald-400 font-semibold' : 'text-gray-500 dark:text-gray-600'">
              {{ lab.label }}
            </span>
          </div>
        </div>
      </div>

      <!-- SoC -->
      <div class="px-4 sm:px-6 py-4 border-t border-gray-200 dark:border-gray-700/70" :class="{ 'opacity-50': dataIsVeryStale }">
        <div class="flex items-end justify-between mb-2 gap-2">
          <div class="flex items-baseline gap-2 flex-wrap min-w-0">
            <span class="text-[10px] font-semibold tracking-[0.18em] uppercase text-gray-500">{{ t('live.soc') }}</span>
            <span class="text-xl font-bold text-gray-900 dark:text-white tabular-nums">{{ Math.round(socPct) }}%</span>
            <span v-if="socStart > 0" class="text-[11px] text-gray-500 tabular-nums whitespace-nowrap">← {{ Math.round(socStart) }}% {{ t('live.start_label') }}</span>
          </div>
          <span class="text-[11px] text-amber-600 dark:text-amber-400 font-semibold tabular-nums whitespace-nowrap">{{ t('live.target_label') }} {{ socTarget }}%</span>
        </div>
        <div class="relative">
          <div class="relative h-3 bg-gray-200 dark:bg-gray-700/60 rounded-full overflow-hidden">
            <div class="absolute inset-y-0 left-0 wave-flow rounded-full" :style="`width: ${socPct}%;`"></div>
            <div class="absolute top-0 bottom-0 w-px bg-amber-400/70" :style="`left: ${socTarget}%;`"></div>
          </div>
          <!-- Nadelkopf ragt aus dem Balken raus (ausserhalb des overflow-hidden) -->
          <div class="absolute -top-1 w-2.5 h-2.5 rounded-full bg-amber-400 shadow-[0_0_8px_rgba(251,191,36,0.6)] ring-2 ring-white dark:ring-gray-800"
               :style="`left: calc(${socTarget}% - 5px);`"></div>
        </div>
      </div>

      <!-- Metrics -->
      <div class="grid grid-cols-2 sm:grid-cols-4 border-t border-gray-200 dark:border-gray-700/70 divide-x divide-y sm:divide-y-0 divide-gray-200 dark:divide-gray-700/70"
           :class="{ 'opacity-50': dataIsVeryStale }">
        <div class="px-4 py-4">
          <div class="flex items-center gap-1.5 mb-1">
            <svg class="w-3 h-3 text-amber-500 dark:text-amber-400" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" /></svg>
            <p class="text-[10px] font-semibold tracking-[0.12em] uppercase text-gray-500">{{ t('live.until_target', { pct: socTarget }) }}</p>
          </div>
          <p class="text-lg font-bold text-gray-900 dark:text-white tabular-nums">
            <template v-if="data?.timeToFullMinutes != null">{{ data.timeToFullMinutes }}<span class="text-xs text-gray-500 dark:text-gray-400 ml-1 font-medium">{{ t('live.minutes_short') }}</span></template>
            <template v-else>-</template>
          </p>
        </div>
        <div class="px-4 py-4">
          <div class="flex items-center gap-1.5 mb-1">
            <svg class="w-3 h-3 text-sky-500 dark:text-sky-400" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6" /></svg>
            <p class="text-[10px] font-semibold tracking-[0.12em] uppercase text-gray-500">{{ t('live.range') }}</p>
          </div>
          <p class="text-lg font-bold text-gray-900 dark:text-white tabular-nums">
            <template v-if="data?.estRangeKm != null">{{ formatNumber(data.estRangeKm, 0) }}<span class="text-xs text-gray-500 dark:text-gray-400 ml-1 font-medium">km</span></template>
            <template v-else>-</template>
          </p>
        </div>
        <div class="px-4 py-4">
          <div class="flex items-center gap-1.5 mb-1">
            <svg class="w-3 h-3 text-emerald-500 dark:text-emerald-400" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" d="M13 10V3L4 14h7v7l9-11h-7z" /></svg>
            <p class="text-[10px] font-semibold tracking-[0.12em] uppercase text-gray-500">{{ t('live.amps') }}</p>
          </div>
          <p class="text-lg font-bold text-gray-900 dark:text-white tabular-nums">
            <template v-if="data?.chargeAmps != null">{{ formatNumber(data.chargeAmps, 0) }}<span class="text-xs text-gray-500 dark:text-gray-400 ml-1 font-medium">A</span></template>
            <template v-else>-</template>
          </p>
        </div>
        <div class="px-4 py-4">
          <div class="flex items-center gap-1.5 mb-1">
            <svg class="w-3 h-3 text-indigo-500 dark:text-indigo-400" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" /></svg>
            <p class="text-[10px] font-semibold tracking-[0.12em] uppercase text-gray-500">{{ t('live.duration') }}</p>
          </div>
          <p class="text-lg font-bold text-gray-900 dark:text-white tabular-nums">{{ sessionDuration ?? '-' }}</p>
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
          <button @click="refresh()" :aria-label="t('live.refresh')"
                  class="p-1.5 rounded-md border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors">
            <ArrowPathIcon class="h-4 w-4 text-gray-500 dark:text-gray-400" />
          </button>
        </div>
      </footer>
    </template>
  </section>
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
.marker-pulse { animation: marker-pulse 1.6s ease-in-out infinite; transform-origin: center; transform-box: fill-box; }
.wave-flow {
  background: linear-gradient(90deg, #059669 0%, #10b981 30%, #34d399 50%, #10b981 70%, #059669 100%);
  background-size: 200% 100%;
  animation: wave-flow 3s linear infinite;
}
</style>
