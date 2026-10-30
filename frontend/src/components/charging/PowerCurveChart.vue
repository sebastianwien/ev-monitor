<script setup lang="ts">
import { computed, ref } from 'vue'
import { nearestIndexByX, formatDuration, peakIndex } from './powerCurveScrub'
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
  /** Wenn gesetzt: feste Ablese-Zeile ueber der Kurve statt schwebendem
   *  Tooltip. Ohne Zeiger zeigt sie die Spitze, mit diesem Text als Praefix
   *  ("Spitze"). Ein Tooltip verschwindet auf dem Handy unter dem Finger. */
  readout?: string
  /** Energie aus dem Log. Skaliert die km-Linie, damit ihr Endwert zur Zahl
   *  daneben passt - die Kurve kann Luecken haben, das Log ist massgeblich. */
  rangeTotalKwh?: number | null
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
  readout: '',
  rangeTotalKwh: null,
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
  const cum = cumulativeKwh(buf)
  const curveTotal = cum[cum.length - 1]
  const scale = props.rangeTotalKwh && props.rangeTotalKwh > 0 && curveTotal > 0
    ? props.rangeTotalKwh / curveTotal
    : 1
  const km = cum.map(kwh => (kwh * scale * 100) / cons)
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
  // Die Beschriftung sitzt ueber ihrer Linie; ganz oben kollidierte sie mit
  // der Spitze, die bei DC-Ladungen fast immer links oben liegt.
  for (let kw = step; kw < max * 0.88; kw += step) {
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
    return soc === null ? '' : `${series.derived ? '~' : ''}${Math.round(soc)} %`
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

interface ScrubInfo {
  x: number; y: number; kw: string; label: string; km: number | null; rangeY: number | null
  soc: string | null
  /** Ohne Zeiger: Ablese-Zeile zeigt die Spitze. */
  resting: boolean
}

const scrubPoint = computed<ScrubInfo | null>(() => {
  const resting = scrubIndex.value === null
  const i = scrubIndex.value ?? (props.readout ? peakIndex(props.points) : null)
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
    resting,
    soc: soc === null ? null : `${series?.derived ? '~' : ''}${Math.round(soc)} %`,
    x: pts[i].x,
    y: pts[i].y,
    kw: src.kw.toLocaleString(undefined, { maximumFractionDigits: src.kw < 10 ? 1 : 0 }),
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

/** Punktposition im HTML-Overlay - die viewBox ist 600x200. */
function dotStyle(x: number, y: number) {
  return {
    left: `${(x / CURVE_W) * 100}%`,
    top: `${(y / CURVE_H) * 100}%`,
    transform: 'translate(-50%, -50%)',
  }
}
</script>

<template>
  <div>
    <!-- Feste Ablese-Zeile: steht ueber der Kurve, wo der Finger sie nicht verdeckt -->
    <p
      v-if="readout && scrubPoint"
      data-testid="power-curve-readout"
      class="mb-3 min-h-6 text-[15px] leading-6 tabular-nums text-gray-900 dark:text-gray-100"
      aria-live="polite"
    >
      <span v-if="scrubPoint.resting" class="mr-1 text-gray-500 dark:text-gray-400">{{ readout }}</span>
      <span class="font-bold">{{ scrubPoint.kw }} kW</span>
      <template v-if="scrubPoint.soc !== null"> · {{ scrubPoint.soc }}</template>
      <template v-if="scrubPoint.km !== null">
        · <span class="inline-block w-3 h-0.5 align-middle rounded-full bg-story-accent dark:bg-story-accent-dark" aria-hidden="true" /> +{{ scrubPoint.km }} km
      </template>
      <span class="text-gray-500 dark:text-gray-400"> · {{ scrubPoint.label }}</span>
    </p>
    <!-- Bezugsrahmen der Overlays: genau die SVG-Flaeche. Die Achsenzeilen
         darunter duerfen nicht mit hineinzaehlen - die Punkte und die
         kW-Beschriftung sitzen prozentual zur viewBox-Hoehe und rutschten sonst
         nach unten, je tiefer der Wert liegt. `block` am SVG, damit kein
         Baseline-Spalt die Rahmenhoehe verfaelscht. -->
    <div class="relative">
      <svg
        ref="svgRef"
        viewBox="0 0 600 200"
        :style="svgHeightStyle"
        class="block w-full h-[var(--pc-h-mobile)] md:h-[var(--pc-h-desktop)] touch-pan-y focus:outline-none focus-visible:ring-2 focus-visible:ring-gray-900 dark:focus-visible:ring-gray-100 rounded"
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
        <g stroke="currentColor" class="text-gray-200 dark:text-gray-700">
          <line v-for="(tick, i) in curveYTicks" :key="`g-${i}`" x1="0" :y1="tick.y" x2="600" :y2="tick.y" vector-effect="non-scaling-stroke" />
        </g>
        <!-- CI: flache Flaeche und Linie in gray-900, kein Verlauf, kein Glow -->
        <path v-if="curveFillPath" :d="curveFillPath" class="fill-gray-100 dark:fill-white/10" />
        <path v-if="curveStrokePath" :d="curveStrokePath" fill="none" stroke="currentColor" class="text-gray-900 dark:text-gray-100" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" vector-effect="non-scaling-stroke" />
        <!-- Nachgeladene Reichweite: eigene Y-Skala, gruen wie das Ergebnis im Kopf -->
        <path v-if="rangeStrokePath" :d="rangeStrokePath" fill="none" stroke="currentColor" class="text-story-accent dark:text-story-accent-dark" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" vector-effect="non-scaling-stroke" />
        <!-- Nur die Linie bleibt im SVG. Punkte liegen als HTML darueber, weil
             preserveAspectRatio="none" die Achsen unterschiedlich streckt und
             einen <circle> damit zur Ellipse zieht - dasselbe Problem, das
             Beschriftungen schon ausserhalb des SVG loesen. -->
        <line v-if="scrubPoint && !scrubPoint.resting" :x1="scrubPoint.x" y1="0" :x2="scrubPoint.x" y2="200" stroke="currentColor" class="text-gray-900 dark:text-gray-100" stroke-width="1" stroke-dasharray="3 3" vector-effect="non-scaling-stroke" />
        <line v-if="showLiveMarker && curveLastPoint" :x1="curveLastPoint.x" :y1="curveLastPoint.y" :x2="curveLastPoint.x" y2="200" stroke="currentColor" class="text-story-accent dark:text-story-accent-dark" stroke-width="1" stroke-dasharray="2 3" opacity="0.6" vector-effect="non-scaling-stroke" />
      </svg>
      <!-- Ohne feste Ablese-Zeile (Live-Karte): Momentanwert am Zeiger. HTML
           statt SVG-Text, sonst wuerde preserveAspectRatio="none" die Schrift verzerren. -->
      <div
        v-if="!readout && scrubPoint"
        data-testid="power-curve-tooltip"
        class="pointer-events-none absolute top-1 z-10 flex items-baseline gap-1.5 whitespace-nowrap rounded-md border-2 border-edge bg-white dark:bg-gray-900 px-2 py-1 text-[12px] leading-none tabular-nums text-gray-900 dark:text-gray-100"
        :style="scrubTooltipStyle"
      >
        <span class="font-bold">{{ scrubPoint.kw }} kW</span>
        <span v-if="scrubPoint.km !== null" class="inline-flex items-center gap-1">
          <span class="inline-block w-2.5 h-0.5 rounded-full bg-story-accent dark:bg-story-accent-dark" aria-hidden="true" />+{{ scrubPoint.km }} km
        </span>
        <span v-if="scrubPoint.soc !== null">{{ scrubPoint.soc }}</span>
        <span class="text-gray-500 dark:text-gray-400">{{ scrubPoint.label }}</span>
      </div>
      <span v-if="!readout" class="sr-only" aria-live="polite">{{ scrubPoint ? `${scrubPoint.kw} kW, ${scrubPoint.soc !== null ? `${scrubPoint.soc}, ` : ''}${scrubPoint.label}` : '' }}</span>
      <!-- Y-Achse als HTML-Overlay (vermeidet preserveAspectRatio="none"-Stretching von SVG-Text) -->
      <div data-testid="power-curve-overlay" class="pointer-events-none absolute inset-0">
        <span
          v-for="(tick, i) in curveYTicks"
          :key="`yt-${i}`"
          class="absolute left-0 -translate-y-full pb-0.5 text-[11px] text-gray-500 dark:text-gray-400 tabular-nums leading-none"
          :style="`top: ${(tick.y / 200) * 100}%;`"
        >{{ tick.kw }} kW</span>
        <!-- Endwert der km-Linie, rechtsbuendig ueber ihrem Ende -->
        <span
          v-if="rangeLastPoint"
          data-testid="power-curve-range-end"
          class="absolute right-0 -translate-y-full pb-1 text-[13px] font-bold text-story-accent dark:text-story-accent-dark tabular-nums whitespace-nowrap leading-none"
          :style="`top: ${(rangeLastPoint.y / 200) * 100}%;`"
        >+{{ Math.round(rangeLastPoint.km) }} km</span>
        <template v-if="scrubPoint">
          <span
            v-if="scrubPoint.rangeY !== null && !scrubPoint.resting"
            class="absolute w-2.5 h-2.5 rounded-full bg-story-accent dark:bg-story-accent-dark ring-2 ring-white dark:ring-gray-900"
            :style="dotStyle(scrubPoint.x, scrubPoint.rangeY)"
          />
          <span
            class="absolute w-3 h-3 rounded-full bg-gray-900 dark:bg-gray-100 ring-2 ring-white dark:ring-gray-900"
            :style="dotStyle(scrubPoint.x, scrubPoint.y)"
          />
        </template>
        <template v-if="showLiveMarker && curveLastPoint">
          <span class="absolute w-4 h-4 rounded-full bg-story-accent/25 dark:bg-story-accent-dark/25" :style="dotStyle(curveLastPoint.x, curveLastPoint.y)" />
          <span class="absolute w-2 h-2 rounded-full bg-story-accent dark:bg-story-accent-dark marker-pulse" :style="dotStyle(curveLastPoint.x, curveLastPoint.y)" />
        </template>
      </div>
    </div>
    <div class="flex justify-between text-[11px] text-gray-700 dark:text-gray-300 tabular-nums mt-1">
      <span
        v-for="(lab, i) in curveXLabels"
        :key="`x-${i}`"
        :class="lab.isNow ? 'font-semibold' : ''"
      >{{ lab.text }}</span>
    </div>
    <!-- Zweite Achsenzeile: Ladestand an denselben Stuetzstellen -->
    <div
      v-if="socXLabels"
      data-testid="power-curve-soc-axis"
      class="flex justify-between text-[11px] text-gray-500 dark:text-gray-400 tabular-nums mt-0.5"
      :aria-label="socAxisLabel"
    >
      <span v-for="(lab, i) in socXLabels" :key="`soc-${i}`">{{ lab }}</span>
    </div>
  </div>
</template>

<style scoped>
@keyframes marker-pulse {
  0%, 100% { transform: translate(-50%, -50%) scale(1); opacity: 1; }
  50% { transform: translate(-50%, -50%) scale(1.5); opacity: 0.85; }
}
.marker-pulse { animation: marker-pulse 1.6s ease-in-out infinite; }
</style>
