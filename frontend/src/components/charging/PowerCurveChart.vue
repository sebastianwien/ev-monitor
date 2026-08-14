<script setup lang="ts">
import { computed, ref, useId } from 'vue'
import { nearestIndexByX, formatDuration } from './powerCurveScrub'
import { cumulativeKwh, buildSocSeries, socAtTs, yTickStepKw } from './powerCurveSeries'

interface PowerPoint { ts: number; kw: number; soc?: number | null }

const props = withDefaults(defineProps<{
  points: PowerPoint[]
  height?: number          // SVG-Hoehe in px (mobile)
  heightDesktop?: number   // optionale Desktop-Hoehe (md+), default = height
  showLiveMarker?: boolean // pulsierender Endpunkt + vertikale Dotted-Linie (Live-Modus)
  yStepKw?: number         // Y-Tick-Schrittweite in kW, 0 = automatisch nach Spitzenleistung
  nowLabel?: string        // wenn gesetzt: Praefix fuer die rechteste X-Achsen-Beschriftung (z.B. "jetzt")
  ariaLabel?: string       // a11y - SVG-Beschreibung fuer Screenreader
  /** X-Achsen-Modus: 'time' zeigt Uhrzeit (HH:MM), 'duration' zeigt verstrichene
   *  Dauer ab Session-Start (z.B. "0", "5 Min", "10 Min", ...). Default 'time'. */
  xAxisMode?: 'time' | 'duration'
  /** Wenn gesetzt: Overlay-Linie "nachgeladene Reichweite (km)" wird gerendert.
   *  km = (kumulierte kWh) * 100 / consumptionKwhPer100km. Endwert als
   *  Inline-Label statt zweiter Y-Achse (mobile-freundlich). */
  consumptionKwhPer100km?: number | null
  /** SoC-Grenzen aus dem Log. Nur noetig, damit der Verlauf fuer Kurven ohne
   *  gemessenen SoC rekonstruiert werden kann - siehe buildSocSeries. */
  socBeforePercent?: number | null
  socAfterPercent?: number | null
  /** a11y - Beschriftung der SoC-Achsenzeile. Die Uebersetzung liegt beim
   *  Aufrufer, damit die Chart-Komponente i18n-frei bleibt. */
  socAxisLabel?: string
  /** Zeigt den Zeiger ohne Interaktion auf diesem Punkt-Index. Fuer den
   *  Teaser, der die Abtast-Funktion vorfuehren soll, ohne bedienbar zu sein.
   *  Echte Interaktion ueberschreibt ihn. */
  previewScrubIndex?: number | null
}>(), {
  height: 234,
  heightDesktop: 0,
  showLiveMarker: false,
  yStepKw: 0,
  nowLabel: '',
  ariaLabel: 'Ladekurve',
  consumptionKwhPer100km: null,
  xAxisMode: 'time',
  socBeforePercent: null,
  socAfterPercent: null,
  socAxisLabel: 'SoC',
  previewScrubIndex: null,
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
  const km = cumulativeKwh(buf).map(kwh => (kwh * 100) / cons)
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
  const step = props.yStepKw > 0 ? props.yStepKw : yTickStepKw(max)
  const ticks: { kw: number; y: number }[] = []
  for (let kw = step; kw < max; kw += step) {
    ticks.push({ kw, y: CURVE_H - (kw / max) * CURVE_H })
  }
  return ticks
})

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

// Zweite X-Achse: Ladestand an denselben Stuetzstellen wie die Zeit-Labels.
// Bewusst keine dritte Linie im Chart - der SoC-Verlauf haette im abgeleiteten
// Modus exakt die Form der km-Linie und wuerde nur Redundanz stiften. Als
// Achsenbeschriftung beantwortet er dagegen die Frage, die die Zeitachse offen
// laesst: bei welchem Ladestand die Leistung eingebrochen ist.
const socSeries = computed(() =>
  buildSocSeries(props.points, props.socBeforePercent, props.socAfterPercent))

const socXLabels = computed<string[] | null>(() => {
  const series = socSeries.value
  const buf = props.points
  if (!series || buf.length < 2) return null
  const first = buf[0].ts
  const span = buf[buf.length - 1].ts - first
  return Array.from({ length: 5 }, (_, i) => {
    const soc = socAtTs(buf, series.values, first + (span * i) / 4)
    return soc === null ? '' : `${Math.round(soc)} %`
  })
})

// --- Scrubbing: Zeiger ueber der Kurve zeigt den Momentanwert ---------------
// Der Zustand ist ein Punkt-Index (nicht eine Pixelposition), damit er bei
// nachlaufenden Live-Daten am selben Messpunkt haftet.
const svgRef = ref<SVGSVGElement | null>(null)
const scrubIndex = ref<number | null>(null)
// Auf Touch-Geraeten gibt es kein Hover: erst das Aufsetzen des Fingers scrubbt,
// sonst wuerde schon das Vorbeiscrollen einen Wert einblenden.
const touchScrubbing = ref(false)

function scrubToClientX(clientX: number) {
  const el = svgRef.value
  if (!el || curvePoints.value.length === 0) return
  const rect = el.getBoundingClientRect()
  if (rect.width <= 0) return
  // preserveAspectRatio="none" streckt linear, das Verhaeltnis uebertraegt sich 1:1.
  const ratio = Math.min(1, Math.max(0, (clientX - rect.left) / rect.width))
  scrubIndex.value = nearestIndexByX(curvePoints.value.map(p => p.x), ratio * CURVE_W)
}

function onPointerMove(e: PointerEvent) {
  if (e.pointerType === 'touch' && !touchScrubbing.value) return
  scrubToClientX(e.clientX)
}

function onPointerDown(e: PointerEvent) {
  if (e.pointerType === 'touch') touchScrubbing.value = true
  // Finger darf den Chart verlassen, ohne dass das Scrubbing abreisst.
  svgRef.value?.setPointerCapture?.(e.pointerId)
  scrubToClientX(e.clientX)
}

function onPointerUp(e: PointerEvent) {
  // Maus behaelt den Wert nach einem Klick - der Zeiger steht ja noch drueber.
  if (e.pointerType !== 'mouse') endScrub()
}

function endScrub() {
  touchScrubbing.value = false
  scrubIndex.value = null
}

function onKeydown(e: KeyboardEvent) {
  const count = curvePoints.value.length
  if (count === 0) return
  if (e.key === 'ArrowRight' || e.key === 'ArrowLeft') {
    e.preventDefault()
    const step = e.key === 'ArrowRight' ? 1 : -1
    const current = scrubIndex.value
    scrubIndex.value = current === null
      ? (step === 1 ? 0 : count - 1)
      : Math.min(count - 1, Math.max(0, current + step))
  } else if (e.key === 'Escape' && scrubIndex.value !== null) {
    // Nur stoppen wenn Escape hier auch wirklich etwas bewirkt - sonst soll ein
    // umschliessender Dialog es weiterhin zum Schliessen bekommen.
    e.stopPropagation()
    endScrub()
  }
}

interface ScrubInfo { x: number; y: number; kw: string; label: string; km: number | null; rangeY: number | null; soc: number | null }

const scrubPoint = computed<ScrubInfo | null>(() => {
  const i = scrubIndex.value ?? props.previewScrubIndex ?? null
  const pts = curvePoints.value
  if (i === null || i < 0 || i >= pts.length) return null
  // Bei einem einzelnen Messpunkt spannt curvePoints eine Linie ueber zwei
  // Stuetzpunkte auf - die Quelldaten haben dann nur Index 0.
  const src = props.points[Math.min(i, props.points.length - 1)]
  if (!src) return null
  const range = rangePoints.value[i] ?? null
  const series = socSeries.value
  const soc = series ? series.values[Math.min(i, series.values.length - 1)] ?? null : null
  return {
    soc: soc === null ? null : Math.round(soc),
    x: pts[i].x,
    y: pts[i].y,
    kw: src.kw.toLocaleString(undefined, { maximumFractionDigits: 1 }),
    label: props.xAxisMode === 'duration'
      ? formatDuration(src.ts - props.points[0].ts)
      : new Date(src.ts).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' }),
    km: range ? Math.round(range.km) : null,
    rangeY: range ? range.y : null,
  }
})

// Nahe den Raendern nicht mehr zentrieren, sonst laeuft die Box aus dem Chart.
const scrubTooltipStyle = computed(() => {
  const p = scrubPoint.value
  if (!p) return {}
  const pct = (p.x / CURVE_W) * 100
  const shift = pct < 18 ? '0%' : pct > 82 ? '-100%' : '-50%'
  return { left: `${pct}%`, transform: `translateX(${shift})` }
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
    <svg
      ref="svgRef"
      viewBox="0 0 600 200"
      :style="svgHeightStyle"
      class="w-full h-[var(--pc-h-mobile)] md:h-[var(--pc-h-desktop)] touch-pan-y focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-400 rounded"
      preserveAspectRatio="none"
      role="img"
      :aria-label="ariaLabel"
      tabindex="0"
      data-no-swipe
      @pointermove="onPointerMove"
      @pointerdown="onPointerDown"
      @pointerup="onPointerUp"
      @pointercancel="endScrub"
      @pointerleave="endScrub"
      @blur="endScrub"
      @keydown="onKeydown"
    >
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
      <!-- Scrub-Marker: Fadenkreuz auf dem angepeilten Messpunkt -->
      <template v-if="scrubPoint">
        <line :x1="scrubPoint.x" y1="0" :x2="scrubPoint.x" y2="200" stroke="currentColor" class="text-gray-400 dark:text-slate-500" stroke-width="1" stroke-dasharray="3 3" />
        <circle v-if="scrubPoint.rangeY !== null" :cx="scrubPoint.x" :cy="scrubPoint.rangeY" r="3.5" fill="#0ea5e9" stroke="#ffffff" stroke-width="1.5" />
        <circle :cx="scrubPoint.x" :cy="scrubPoint.y" r="4.5" fill="#059669" stroke="#ffffff" stroke-width="2" />
      </template>
      <template v-if="showLiveMarker && curveLastPoint">
        <line :x1="curveLastPoint.x" :y1="curveLastPoint.y" :x2="curveLastPoint.x" y2="200" stroke="#10b981" stroke-width="1" stroke-dasharray="2 3" opacity="0.5" />
        <circle :cx="curveLastPoint.x" :cy="curveLastPoint.y" r="8" fill="#10b981" opacity="0.25" />
        <circle :cx="curveLastPoint.x" :cy="curveLastPoint.y" r="4" fill="#10b981" class="marker-pulse" />
        <circle :cx="curveLastPoint.x" :cy="curveLastPoint.y" r="2" fill="#ffffff" />
      </template>
    </svg>
    <!-- Momentanwert am Zeiger. HTML statt SVG-Text, sonst wuerde
         preserveAspectRatio="none" die Schrift verzerren. -->
    <div
      v-if="scrubPoint"
      data-testid="power-curve-tooltip"
      class="pointer-events-none absolute top-1 z-10 flex items-baseline gap-1.5 whitespace-nowrap rounded-md border border-emerald-200/70 dark:border-emerald-800/50 bg-white/95 dark:bg-gray-900/95 px-2 py-1 text-[11px] leading-none shadow-sm tabular-nums"
      :style="scrubTooltipStyle"
    >
      <span class="font-semibold text-emerald-700 dark:text-emerald-300">{{ scrubPoint.kw }} kW</span>
      <span v-if="scrubPoint.km !== null" class="font-medium text-sky-600 dark:text-sky-400">+{{ scrubPoint.km }} km</span>
      <span v-if="scrubPoint.soc !== null" class="font-medium text-gray-700 dark:text-gray-300">{{ scrubPoint.soc }} %</span>
      <span class="text-gray-500 dark:text-gray-400">{{ scrubPoint.label }}</span>
    </div>
    <span class="sr-only" aria-live="polite">{{ scrubPoint ? `${scrubPoint.kw} kW, ${scrubPoint.soc !== null ? `${scrubPoint.soc} %, ` : ''}${scrubPoint.label}` : '' }}</span>
    <!-- Y-Achse als HTML-Overlay (vermeidet preserveAspectRatio="none"-Stretching von SVG-Text) -->
    <div class="pointer-events-none absolute inset-0">
      <span
        v-for="(tick, i) in curveYTicks"
        :key="`yt-${i}`"
        class="absolute left-1.5 text-[9px] text-gray-500 dark:text-slate-400 tabular-nums leading-none rounded-sm bg-white/80 dark:bg-gray-900/80 px-1 py-px"
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
    <!-- Zweite Achsenzeile: Ladestand an denselben Stuetzstellen -->
    <div
      v-if="socXLabels"
      data-testid="power-curve-soc-axis"
      class="flex justify-between text-[10px] text-gray-400 dark:text-gray-500 px-1 tabular-nums mt-0.5"
      :aria-label="socAxisLabel"
    >
      <span v-for="(lab, i) in socXLabels" :key="`soc-${i}`">{{ lab }}</span>
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
