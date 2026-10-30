<template>
  <div>
    <!-- Feste Ablese-Zeile wie bei der Ladekurve: ueber der Kurve, nicht unter dem Finger -->
    <p
      v-if="readout && scrubPoint"
      data-testid="soc-curve-readout"
      class="mb-3 min-h-6 text-[15px] leading-6 tabular-nums text-gray-900 dark:text-gray-100"
      aria-live="polite"
    >
      <span v-if="scrubPoint.resting" class="mr-1 text-gray-500 dark:text-gray-400">{{ readout }}</span>
      <span class="font-bold">{{ scrubPoint.soc }} %</span>
      <span class="text-gray-500 dark:text-gray-400"> · {{ scrubPoint.label }}</span>
    </p>
    <div class="relative">
      <svg
        ref="svgRef"
        viewBox="0 0 600 200"
        :style="svgHeightStyle"
        class="block w-full h-[var(--sc-h-mobile)] md:h-[var(--sc-h-desktop)] touch-pan-y focus:outline-none focus-visible:ring-2 focus-visible:ring-gray-900 dark:focus-visible:ring-gray-100 rounded"
        preserveAspectRatio="none"
        role="img"
        :aria-label="ariaLabel"
        tabindex="0"
        data-no-swipe
        data-testid="soc-curve-chart"
        @pointermove="onPointerMove"
        @pointerdown="onPointerDown"
        @pointerup="onPointerUp"
        @pointercancel="endScrub"
        @pointerleave="endScrub"
        @blur="endScrub"
        @keydown="onKeydown"
      >
        <g stroke="currentColor" class="text-gray-200 dark:text-gray-700">
          <line v-for="tick in yTicks" :key="`g-${tick.soc}`" x1="0" :y1="tick.y" x2="600" :y2="tick.y" vector-effect="non-scaling-stroke" />
        </g>
        <path v-if="fillPath" :d="fillPath" class="fill-gray-100 dark:fill-white/10" />
        <!-- Stufenlinie statt geglaetteter Kurve: der Ladestand kommt in
             1-%-Schritten alle vier bis fuenf Minuten. Eine weiche Linie wuerde
             eine Aufloesung vortaeuschen, die die Daten nicht haben. -->
        <path v-if="strokePath" :d="strokePath" fill="none" stroke="currentColor" class="text-gray-900 dark:text-gray-100" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" vector-effect="non-scaling-stroke" />
        <!-- Punkt liegt als HTML darueber: preserveAspectRatio="none" streckt die
             Achsen unterschiedlich und zieht einen <circle> zur Ellipse. -->
        <line v-if="scrubPoint && !scrubPoint.resting" :x1="scrubPoint.x" y1="0" :x2="scrubPoint.x" y2="200" stroke="currentColor" class="text-gray-900 dark:text-gray-100" stroke-width="1" stroke-dasharray="3 3" vector-effect="non-scaling-stroke" />
      </svg>

      <div
        v-if="!readout && scrubPoint"
        data-testid="soc-curve-tooltip"
        class="pointer-events-none absolute top-1 z-10 flex items-baseline gap-1.5 whitespace-nowrap rounded-md border-2 border-edge bg-white dark:bg-gray-900 px-2 py-1 text-[12px] leading-none tabular-nums text-gray-900 dark:text-gray-100"
        :style="tooltipStyle"
      >
        <span class="font-bold">{{ scrubPoint.soc }} %</span>
        <span class="text-gray-500 dark:text-gray-400">{{ scrubPoint.label }}</span>
      </div>
      <span v-if="!readout" class="sr-only" aria-live="polite">{{ scrubPoint ? `${scrubPoint.soc} %, ${scrubPoint.label}` : '' }}</span>

      <div class="pointer-events-none absolute inset-0">
        <span
          v-if="scrubPoint"
          class="absolute w-3 h-3 rounded-full bg-gray-900 dark:bg-gray-100 ring-2 ring-white dark:ring-gray-900"
          :style="dotStyle(scrubPoint.x, scrubPoint.y)"
        />
        <span
          v-for="tick in yTicks"
          :key="`yt-${tick.soc}`"
          class="absolute left-0 -translate-y-full pb-0.5 text-[11px] text-gray-500 dark:text-gray-400 tabular-nums leading-none"
          :style="`top: ${(tick.y / 200) * 100}%;`"
        >{{ tick.soc }} %</span>
      </div>
    </div>

    <div class="flex justify-between text-[11px] text-gray-700 dark:text-gray-300 tabular-nums mt-1">
      <span v-for="(label, i) in xLabels" :key="`x-${i}`">{{ label }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { nearestIndexByX, formatDuration } from './powerCurveScrub'
import type { SocPoint } from './powerCurveSeries'

/**
 * Ladeverlauf: gemessener Ladestand ueber die Zeit.
 *
 * Bewusst eine eigene Komponente und kein Modus des PowerCurveChart. Die beiden
 * zeigen Verschiedenes - dort gemessene Leistung, hier gemessener Ladestand.
 * Unterscheidbar bleiben sie ueber die Prozent-Achse und die Stufenlinie.
 */
const props = withDefaults(defineProps<{
  points: SocPoint[]
  height?: number
  heightDesktop?: number
  ariaLabel?: string
  /** Wenn gesetzt: feste Ablese-Zeile ueber der Kurve statt Tooltip. Ohne
   *  Zeiger zeigt sie den Endstand, mit diesem Text als Praefix ("Ende"). */
  readout?: string
}>(), {
  height: 234,
  heightDesktop: 0,
  ariaLabel: 'Ladeverlauf',
  readout: '',
})

const CURVE_W = 600
const CURVE_H = 200

const svgHeightStyle = computed(() => ({
  '--sc-h-mobile': `${props.height}px`,
  '--sc-h-desktop': `${props.heightDesktop || props.height}px`,
}))

interface Plotted { x: number; y: number }

// Feste 0-100-Skala statt Autoskalierung: der Ladestand hat einen bekannten
// Wertebereich, und eine mitwachsende Achse liesse jede Ladung gleich steil
// aussehen - egal ob sie 10 oder 60 Prozentpunkte gebracht hat.
const plotted = computed<Plotted[]>(() => {
  const buf = props.points
  if (buf.length === 0) return []
  const first = buf[0].ts
  const span = buf[buf.length - 1].ts - first
  return buf.map((p, i) => ({
    x: span > 0 ? ((p.ts - first) / span) * CURVE_W : (buf.length === 1 ? 0 : (i / (buf.length - 1)) * CURVE_W),
    y: CURVE_H - (Math.min(100, Math.max(0, p.soc)) / 100) * CURVE_H,
  }))
})

/** Treppe: waagerecht bis zum naechsten Messpunkt, dann senkrecht auf den neuen Wert. */
const strokePath = computed(() => {
  const pts = plotted.value
  if (pts.length === 0) return ''
  if (pts.length === 1) return `M 0 ${pts[0].y.toFixed(1)} L ${CURVE_W} ${pts[0].y.toFixed(1)}`
  let d = `M ${pts[0].x.toFixed(1)} ${pts[0].y.toFixed(1)}`
  for (let i = 1; i < pts.length; i++) {
    d += ` L ${pts[i].x.toFixed(1)} ${pts[i - 1].y.toFixed(1)} L ${pts[i].x.toFixed(1)} ${pts[i].y.toFixed(1)}`
  }
  return d
})

const fillPath = computed(() => {
  const pts = plotted.value
  if (pts.length === 0) return ''
  const lastX = (pts.length === 1 ? CURVE_W : pts[pts.length - 1].x).toFixed(1)
  return `${strokePath.value} L ${lastX} ${CURVE_H} L ${pts[0].x.toFixed(1)} ${CURVE_H} Z`
})

const yTicks = computed(() =>
  [25, 50, 75].map(soc => ({ soc, y: CURVE_H - (soc / 100) * CURVE_H })))

const xLabels = computed<string[]>(() => {
  const buf = props.points
  if (buf.length < 2) return ['', '', '', '', '']
  const span = buf[buf.length - 1].ts - buf[0].ts
  return Array.from({ length: 5 }, (_, i) => (i === 0 ? '0' : formatDuration((span * i) / 4)))
})

// --- Abtasten (identische Gesten wie bei der Ladekurve) --------------------
const svgRef = ref<SVGSVGElement | null>(null)
const scrubIndex = ref<number | null>(null)
const touchScrubbing = ref(false)

function scrubToClientX(clientX: number) {
  const el = svgRef.value
  if (!el || plotted.value.length === 0) return
  const rect = el.getBoundingClientRect()
  if (rect.width <= 0) return
  const ratio = Math.min(1, Math.max(0, (clientX - rect.left) / rect.width))
  scrubIndex.value = nearestIndexByX(plotted.value.map(p => p.x), ratio * CURVE_W)
}

function onPointerMove(e: PointerEvent) {
  if (e.pointerType === 'touch' && !touchScrubbing.value) return
  scrubToClientX(e.clientX)
}

function onPointerDown(e: PointerEvent) {
  if (e.pointerType === 'touch') touchScrubbing.value = true
  svgRef.value?.setPointerCapture?.(e.pointerId)
  scrubToClientX(e.clientX)
}

function onPointerUp(e: PointerEvent) {
  if (e.pointerType !== 'mouse') endScrub()
}

function endScrub() {
  touchScrubbing.value = false
  scrubIndex.value = null
}

function onKeydown(e: KeyboardEvent) {
  const count = plotted.value.length
  if (count === 0) return
  if (e.key === 'ArrowRight' || e.key === 'ArrowLeft') {
    e.preventDefault()
    const step = e.key === 'ArrowRight' ? 1 : -1
    const current = scrubIndex.value
    scrubIndex.value = current === null
      ? (step === 1 ? 0 : count - 1)
      : Math.min(count - 1, Math.max(0, current + step))
  } else if (e.key === 'Escape' && scrubIndex.value !== null) {
    // Nur stoppen wenn Escape hier etwas bewirkt - sonst soll ein umschliessender
    // Dialog es weiterhin zum Schliessen bekommen.
    e.stopPropagation()
    endScrub()
  }
}

const scrubPoint = computed(() => {
  const resting = scrubIndex.value === null
  const pts = plotted.value
  const i = scrubIndex.value ?? (props.readout ? pts.length - 1 : null)
  if (i === null || i < 0 || i >= pts.length) return null
  const src = props.points[Math.min(i, props.points.length - 1)]
  if (!src) return null
  return {
    resting,
    x: pts[i].x,
    y: pts[i].y,
    soc: Math.round(src.soc),
    label: formatDuration(src.ts - props.points[0].ts),
  }
})

const tooltipStyle = computed(() => {
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
