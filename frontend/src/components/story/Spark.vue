<script setup lang="ts">
import { computed } from 'vue'
import type { SparkVariant } from '../../composables/storyModel'

/**
 * Mini-Chart als inline SVG, ohne Chart-Bibliothek.
 *
 * line:  Verlauf mit Flaeche, letzter Punkt markiert, optionale Referenzlinie.
 * bars:  ein Balken je Wert, der hoechste fuellt die Hoehe.
 * range: zwei Werte auf einer Skala (z. B. Peer-Schnitt gegen eigenen Wert).
 */
const props = withDefaults(defineProps<{
  values: number[]
  variant: SparkVariant
  reference?: number | null
  /** Farbklasse des Akzents (Tailwind text-*), die SVG-Elemente nutzen currentColor. */
  tone?: 'good' | 'warn' | 'bad' | 'neutral'
  labels?: string[]
  /** Letzter Wert als Text rechts neben der Linie, auf Höhe des Endpunkts (nur line). */
  endLabel?: string
}>(), { reference: null, tone: 'neutral', labels: () => [], endLabel: '' })

const W = 200
const H = 44
const PAD = 4

const toneClass = computed(() => ({
  good: 'text-green-600 dark:text-green-400',
  warn: 'text-amber-600 dark:text-amber-400',
  bad: 'text-rose-600 dark:text-rose-400',
  neutral: 'text-gray-500 dark:text-gray-400',
}[props.tone]))

const domain = computed(() => {
  const all = [...props.values, ...(props.reference != null ? [props.reference] : [])]
  const min = Math.min(...all)
  const max = Math.max(...all)
  return { min: min === max ? min - 1 : min, max: min === max ? max + 1 : max }
})

function y(v: number): number {
  const { min, max } = domain.value
  return H - PAD - ((v - min) / (max - min)) * (H - 2 * PAD)
}

const linePoints = computed(() => {
  const n = props.values.length
  return props.values.map((v, i) => ({ x: n === 1 ? W : (i / (n - 1)) * W, y: y(v) }))
})

const linePath = computed(() =>
  linePoints.value.map((p, i) => `${i === 0 ? 'M' : 'L'}${p.x.toFixed(1)} ${p.y.toFixed(1)}`).join(' '))

/** Höhe des Endpunkts in Prozent der Grafik, damit der Endwert in HTML daneben steht. */
const endTop = computed(() => `${(((linePoints.value[linePoints.value.length - 1]?.y ?? H / 2) / H) * 100).toFixed(1)}%`)

const areaPath = computed(() => `${linePath.value} L${W} ${H} L0 ${H} Z`)

const bars = computed(() => {
  const n = props.values.length
  const max = Math.max(...props.values, 0)
  const gap = 6
  const bw = (W - gap * (n - 1)) / n
  return props.values.map((v, i) => {
    const h = max > 0 ? Math.max(2, (v / max) * (H - 2 * PAD)) : 2
    return { x: i * (bw + gap), y: H - PAD - h, w: bw, h }
  })
})

const range = computed(() => {
  if (props.values.length < 2) return null
  const [ref, mine] = props.values
  const lo = Math.min(ref, mine) * 0.9
  const hi = Math.max(ref, mine) * 1.1
  const x = (v: number) => ((v - lo) / (hi - lo)) * W
  return { refX: x(ref), mineX: x(mine) }
})
</script>

<template>
  <div v-if="values.length > 0" class="relative" :class="toneClass">
  <svg
    :viewBox="`0 0 ${W} ${H}`"
    class="block w-full h-11"
    :class="endLabel ? 'pr-11' : ''"
    preserveAspectRatio="none"
    aria-hidden="true">
    <template v-if="variant === 'line'">
      <path :d="areaPath" fill="currentColor" opacity="0.12" />
      <line
        v-if="reference != null"
        data-role="reference"
        x1="0" :y1="y(reference)" :x2="W" :y2="y(reference)"
        stroke="currentColor" stroke-dasharray="3 3" opacity="0.5" />
      <path data-role="line" :d="linePath" fill="none" stroke="currentColor" stroke-width="2" vector-effect="non-scaling-stroke" />
      <circle :cx="linePoints[linePoints.length - 1].x" :cy="linePoints[linePoints.length - 1].y" r="3" fill="currentColor" />
    </template>

    <template v-else-if="variant === 'bars'">
      <rect
        v-for="(b, i) in bars" :key="i" data-role="bar"
        :x="b.x" :y="b.y" :width="b.w" :height="b.h" rx="2"
        fill="currentColor" :opacity="i === bars.length - 1 ? 0.35 : 1" />
    </template>

    <template v-else-if="variant === 'range' && range">
      <line x1="0" :y1="H / 2" :x2="W" :y2="H / 2" stroke="currentColor" opacity="0.2" stroke-width="6" stroke-linecap="round" />
      <line :x1="range.refX" :y1="H / 2 - 9" :x2="range.refX" :y2="H / 2 + 9" stroke="currentColor" opacity="0.5" stroke-width="2" />
      <circle :cx="range.mineX" :cy="H / 2" r="6" fill="currentColor" />
    </template>
  </svg>
  <span
    v-if="endLabel && variant === 'line'"
    data-role="end-label"
    class="absolute right-0 -translate-y-1/2 text-[11px] font-bold text-gray-900 tabular-nums dark:text-gray-100"
    :style="{ top: endTop }">{{ endLabel }}</span>
  </div>
</template>
