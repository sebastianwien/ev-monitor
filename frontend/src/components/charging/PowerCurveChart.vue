<script setup lang="ts">
import { computed, useId } from 'vue'

interface PowerPoint { ts: number; kw: number }

const props = withDefaults(defineProps<{
  points: PowerPoint[]
  height?: number          // SVG-Hoehe in px (mobile)
  heightDesktop?: number   // optionale Desktop-Hoehe (md+), default = height
  showLiveMarker?: boolean // pulsierender Endpunkt + vertikale Dotted-Linie (Live-Modus)
  yStepKw?: number         // Y-Tick-Schrittweite in kW
  nowLabel?: string        // wenn gesetzt: Praefix fuer die rechteste X-Achsen-Beschriftung (z.B. "jetzt")
  ariaLabel?: string       // a11y - SVG-Beschreibung fuer Screenreader
  /** X-Achsen-Modus: 'time' zeigt Uhrzeit (HH:MM), 'duration' zeigt verstrichene
   *  Dauer ab Session-Start (z.B. "0", "5 Min", "10 Min", ...). Default 'time'. */
  xAxisMode?: 'time' | 'duration'
  /** Wenn gesetzt: Overlay-Linie "nachgeladene Reichweite (km)" wird gerendert.
   *  km = (kumulierte kWh) * 100 / consumptionKwhPer100km. Endwert als
   *  Inline-Label statt zweiter Y-Achse (mobile-freundlich). */
  consumptionKwhPer100km?: number | null
}>(), {
  height: 234,
  heightDesktop: 0,
  showLiveMarker: false,
  yStepKw: 25,
  nowLabel: '',
  ariaLabel: 'Ladekurve',
  consumptionKwhPer100km: null,
  xAxisMode: 'time',
})

const svgHeightStyle = computed(() => ({
  '--pc-h-mobile': `${props.height}px`,
  '--pc-h-desktop': `${props.heightDesktop || props.height}px`,
}))

const CURVE_W = 600
const CURVE_H = 200

const curveMaxKw = computed(() => {
  const buf = props.points
  const max = buf.length > 0 ? Math.max(...buf.map(p => p.kw)) : 0
  // 5% Headroom: bei Peak 250kW endet die Y-Achse bei ~263kW, kein
  // verschenkter Platz oberhalb der Spitze.
  return Math.max(80, max) * 1.05
})

interface CurvePoint { x: number; y: number }

// Zeit-Spanne der aktuellen Punktreihe. Wird sowohl fuer die kW-Kurve als auch
// fuer die km-Reichweiten-Linie als x-Achse genutzt - damit Tesla's on-change-
// Streaming mit ungleichmaessigen dt's korrekt visualisiert wird, statt die
// Punkte index-basiert gleichmaessig zu verteilen (sonst sieht eine 20-min-Pause
// genauso breit aus wie eine 5-sek-Luecke).
function pointX(ts: number, first: number, span: number): number {
  return span > 0 ? ((ts - first) / span) * CURVE_W : 0
}

const curvePoints = computed<CurvePoint[]>(() => {
  const buf = props.points
  if (buf.length === 0) return []
  if (buf.length === 1) {
    const y = CURVE_H - (buf[0].kw / curveMaxKw.value) * CURVE_H
    return [{ x: 0, y }, { x: CURVE_W, y }]
  }
  const max = curveMaxKw.value
  const first = buf[0].ts
  const span = buf[buf.length - 1].ts - first
  return buf.map(p => ({
    x: pointX(p.ts, first, span),
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

// Kumulative nachgeladene Reichweite ueber die Session.
// Integration kW * dt -> kWh, dann / Verbrauch * 100 -> km. Eigene Y-Skala,
// damit die km-Linie unabhaengig von der kW-Achse skaliert.
interface RangePoint { x: number; y: number; km: number }
const rangePoints = computed<RangePoint[]>(() => {
  const cons = props.consumptionKwhPer100km
  const buf = props.points
  if (!cons || cons <= 0 || buf.length < 2) return []
  // Kumulierte kWh pro Punkt - Trapez-Regel ueber (kw, ts).
  const cumKwh: number[] = [0]
  for (let i = 1; i < buf.length; i++) {
    const dtH = (buf[i].ts - buf[i - 1].ts) / 3_600_000
    const avgKw = (buf[i].kw + buf[i - 1].kw) / 2
    cumKwh.push(cumKwh[i - 1] + avgKw * dtH)
  }
  const km = cumKwh.map(kwh => (kwh * 100) / cons)
  // 30 % Headroom oben, damit die km-Linie nicht in das kW-Overlay
  // rechts oben laeuft. Endpunkt liegt dann bei ~70 % Hoehe.
  const maxKm = (km[km.length - 1] || 1) / 0.70
  const first = buf[0].ts
  const span = buf[buf.length - 1].ts - first
  return buf.map((p, i) => ({
    x: pointX(p.ts, first, span),
    y: CURVE_H - (km[i] / maxKm) * CURVE_H,
    km: km[i],
  }))
})

const rangeStrokePath = computed(() => {
  const pts = rangePoints.value
  if (pts.length < 2) return ''
  return pts.map((p, i) => `${i === 0 ? 'M' : 'L'} ${p.x.toFixed(1)} ${p.y.toFixed(1)}`).join(' ')
})

const rangeLastPoint = computed<RangePoint | null>(() => {
  const pts = rangePoints.value
  return pts.length > 0 ? pts[pts.length - 1] : null
})

const curveYTicks = computed(() => {
  const max = curveMaxKw.value
  const ticks: { kw: number; y: number }[] = []
  for (let kw = props.yStepKw; kw < max; kw += props.yStepKw) {
    ticks.push({ kw, y: CURVE_H - (kw / max) * CURVE_H })
  }
  return ticks
})

function formatDuration(ms: number): string {
  const totalMin = Math.round(ms / 60000)
  if (totalMin < 60) return `${totalMin} Min`
  const h = Math.floor(totalMin / 60)
  const m = totalMin % 60
  return m === 0 ? `${h} h` : `${h}h ${m}`
}

const curveXLabels = computed<{ text: string; isNow: boolean }[]>(() => {
  const buf = props.points
  if (buf.length < 2) return [{ text: '', isNow: false }, { text: '', isNow: false }, { text: '', isNow: false }, { text: '', isNow: false }, { text: '', isNow: false }]
  const first = buf[0].ts
  const last = buf[buf.length - 1].ts
  const span = last - first
  const labels: { text: string; isNow: boolean }[] = []
  for (let i = 0; i < 5; i++) {
    const isLast = i === 4
    const isFirst = i === 0
    let text: string
    if (props.xAxisMode === 'duration') {
      // Dauer ab Session-Start. Start-Label leer/0, sonst "X Min"
      const elapsedMs = (span * i / 4)
      text = isFirst ? '0' : formatDuration(elapsedMs)
    } else {
      const ts = first + (span * i / 4)
      const hhmm = new Date(ts).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' })
      text = isLast && props.nowLabel ? `${props.nowLabel} ${hhmm}` : hhmm
    }
    labels.push({
      text,
      isNow: isLast && !!props.nowLabel && props.xAxisMode !== 'duration',
    })
  }
  return labels
})

// Unique id-Suffix damit mehrere Instanzen auf einer Seite keine Gradient-IDs
// teilen und sich gegenseitig "verlieren". Vue 3.5 useId() ist SSR-stabil
// (waehrend Math.random() Hydration-Mismatch ausloesen wuerde).
const uid = useId()
const fillId = `pc-fill-${uid}`
const strokeId = `pc-stroke-${uid}`
const glowId = `pc-glow-${uid}`
</script>

<template>
  <div class="relative">
    <svg viewBox="0 0 600 200" :style="svgHeightStyle" class="w-full h-[var(--pc-h-mobile)] md:h-[var(--pc-h-desktop)]" preserveAspectRatio="none" role="img" :aria-label="ariaLabel">
      <defs>
        <linearGradient :id="fillId" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stop-color="#10b981" stop-opacity="0.45" />
          <stop offset="60%" stop-color="#10b981" stop-opacity="0.12" />
          <stop offset="100%" stop-color="#10b981" stop-opacity="0" />
        </linearGradient>
        <linearGradient :id="strokeId" x1="0" y1="0" x2="1" y2="0">
          <stop offset="0%" stop-color="#059669" />
          <stop offset="100%" stop-color="#34d399" />
        </linearGradient>
        <filter :id="glowId" x="-20%" y="-20%" width="140%" height="140%">
          <feGaussianBlur stdDeviation="3" result="blur" />
          <feMerge><feMergeNode in="blur" /><feMergeNode in="SourceGraphic" /></feMerge>
        </filter>
      </defs>
      <g stroke="currentColor" class="text-gray-300 dark:text-slate-700" stroke-width="1" stroke-dasharray="2 4" opacity="0.6">
        <line v-for="(tick, i) in curveYTicks" :key="`g-${i}`" x1="0" :y1="tick.y" x2="600" :y2="tick.y" />
      </g>
      <path v-if="curveFillPath" :d="curveFillPath" :fill="`url(#${fillId})`" />
      <path v-if="curveStrokePath" :d="curveStrokePath" fill="none" :stroke="`url(#${strokeId})`" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" :filter="`url(#${glowId})`" />
      <!-- Reichweite-Overlay (km), eigene Y-Skala, blaue Linie zur Abgrenzung -->
      <path v-if="rangeStrokePath" :d="rangeStrokePath" fill="none" stroke="#0ea5e9" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" opacity="0.85" />
      <template v-if="showLiveMarker && curveLastPoint">
        <line :x1="curveLastPoint.x" :y1="curveLastPoint.y" :x2="curveLastPoint.x" y2="200" stroke="#10b981" stroke-width="1" stroke-dasharray="2 3" opacity="0.5" />
        <circle :cx="curveLastPoint.x" :cy="curveLastPoint.y" r="8" fill="#10b981" opacity="0.25" />
        <circle :cx="curveLastPoint.x" :cy="curveLastPoint.y" r="4" fill="#10b981" class="marker-pulse" />
        <circle :cx="curveLastPoint.x" :cy="curveLastPoint.y" r="2" fill="#ffffff" />
      </template>
    </svg>
    <!-- Y-Achse als HTML-Overlay (vermeidet preserveAspectRatio="none"-Stretching von SVG-Text) -->
    <div class="pointer-events-none absolute inset-0">
      <span
        v-for="(tick, i) in curveYTicks"
        :key="`yt-${i}`"
        class="absolute left-1.5 text-[9px] text-gray-500 dark:text-slate-500 tabular-nums leading-none"
        :style="`top: calc(${(tick.y / 200) * 100}% - 0.3em);`"
      >{{ tick.kw }} kW</span>
      <!-- Reichweite-Label am Endpunkt der km-Linie. Y aus rangeLastPoint,
           rechtsbuendig am rechten Rand. Bei 25% Headroom liegt y ~75%, also
           weit unter dem kW-Overlay rechts oben. -->
      <span
        v-if="rangeLastPoint"
        class="absolute text-[10px] font-semibold text-sky-600 dark:text-sky-400 tabular-nums whitespace-nowrap leading-none px-1.5 py-0.5 rounded bg-white/85 dark:bg-gray-900/85 border border-sky-200/60 dark:border-sky-800/40"
        :style="`top: calc(${(rangeLastPoint.y / 200) * 100}% - 1.6em); right: 4px;`"
      >+{{ Math.round(rangeLastPoint.km) }} km</span>
    </div>
    <div class="flex justify-between text-[10px] text-gray-500 dark:text-gray-500 px-1 tabular-nums mt-1">
      <span
        v-for="(lab, i) in curveXLabels"
        :key="`x-${i}`"
        :class="lab.isNow ? 'text-emerald-600 dark:text-emerald-400 font-semibold' : ''"
      >{{ lab.text }}</span>
    </div>
  </div>
</template>

<style scoped>
@keyframes marker-pulse {
  0%, 100% { r: 4; opacity: 1; }
  50% { r: 6; opacity: 0.85; }
}
.marker-pulse { animation: marker-pulse 1.6s ease-in-out infinite; transform-origin: center; transform-box: fill-box; }
</style>
