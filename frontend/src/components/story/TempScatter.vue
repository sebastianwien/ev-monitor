<script setup lang="ts">
import { ref, computed } from 'vue'
import type { TemperatureEffect } from '../../composables/storyInsights'
import { COLD_MAX_C, MILD_MIN_C } from '../../composables/storyInsights'
import { prefersReducedEffects } from '../../composables/prefersReducedEffects'

/**
 * Verbrauch je Fahrt gegen Aussentemperatur als SVG-Scatter, Median-Linie je 5-Grad-Bin.
 * Kalt- und Mild-Zone sind leicht hinterlegt. Punkte poppen gestaffelt auf, Tipp zeigt die Fahrt.
 */
const props = defineProps<{
  effect: TemperatureEffect
  unitLabel: string
}>()

const W = 320
const H = 170
const L = 34
const R = 8
const T = 10
const B = 24

const reduceMotion = prefersReducedEffects()

const xDomain = computed(() => {
  const temps = props.effect.points.map(p => p.temp)
  return { min: Math.floor(Math.min(...temps)) - 2, max: Math.ceil(Math.max(...temps)) + 2 }
})
const yDomain = computed(() => {
  const v = props.effect.points.map(p => p.kwh100)
  return { min: Math.max(0, Math.floor(Math.min(...v) * 0.9)), max: Math.ceil(Math.max(...v) * 1.1) }
})

const x = (temp: number) => L + ((temp - xDomain.value.min) / (xDomain.value.max - xDomain.value.min)) * (W - L - R)
const y = (v: number) => T + (1 - (v - yDomain.value.min) / (yDomain.value.max - yDomain.value.min)) * (H - T - B)

const medianLine = computed(() => props.effect.bins
  .map(b => `${x(b.tempFrom + 2.5).toFixed(1)},${y(b.kwh100).toFixed(1)}`)
  .join(' '))

const coldW = computed(() => Math.max(0, x(Math.min(COLD_MAX_C, xDomain.value.max)) - L))
const mildX = computed(() => x(Math.max(MILD_MIN_C, xDomain.value.min)))

const selected = ref<number | null>(null)
const selectedPoint = computed(() => selected.value == null ? null : props.effect.points[selected.value])

const xTicks = computed(() => {
  const { min, max } = xDomain.value
  const ticks = [min, max]
  if (min < 0 && max > 0) ticks.splice(1, 0, 0)
  return ticks
})
</script>

<template>
  <div>
    <svg :viewBox="`0 0 ${W} ${H}`" class="block w-full h-auto" role="img">
      <rect :x="L" :y="T" :width="coldW" :height="H - T - B" class="fill-sky-500/10" />
      <rect v-if="mildX < W - R" :x="mildX" :y="T" :width="W - R - mildX" :height="H - T - B" class="fill-amber-500/10" />

      <line :x1="L" :x2="W - R" :y1="H - B" :y2="H - B" class="stroke-gray-300 dark:stroke-gray-600" stroke-width="1" />
      <line :x1="L" :x2="L" :y1="T" :y2="H - B" class="stroke-gray-300 dark:stroke-gray-600" stroke-width="1" />

      <g class="fill-gray-500 dark:fill-gray-400" font-size="9">
        <text v-for="(tk, i) in xTicks" :key="tk" :x="x(tk)" :y="H - B + 12" :text-anchor="i === 0 ? 'start' : i === xTicks.length - 1 ? 'end' : 'middle'">{{ tk }} °C</text>
        <text :x="L - 4" :y="y(yDomain.max) + 3" text-anchor="end">{{ yDomain.max }}</text>
        <text :x="L - 4" :y="y(yDomain.min) + 3" text-anchor="end">{{ yDomain.min }}</text>
      </g>

      <polyline v-if="effect.bins.length > 1" :points="medianLine" fill="none" stroke-width="2" stroke-linejoin="round" class="stroke-gray-800 dark:stroke-gray-100" opacity="0.8" />

      <circle
        v-for="(p, i) in effect.points" :key="i"
        :cx="x(p.temp)" :cy="y(p.kwh100)" :r="selected === i ? 5.5 : 3.5"
        class="story-pop cursor-pointer fill-story-accent dark:fill-story-accent-dark"
        :class="{ 'no-motion': reduceMotion }"
        :style="{ animationDelay: `${Math.min(i, 40) * 25}ms` }"
        opacity="0.8"
        @click="selected = selected === i ? null : i" />
    </svg>
    <p class="text-xs text-gray-600 dark:text-gray-300 mt-1 min-h-[1.25rem] tabular-nums">
      <template v-if="selectedPoint">{{ selectedPoint.temp }} °C: {{ selectedPoint.kwh100.toFixed(1) }} {{ unitLabel }}</template>
    </p>
  </div>
</template>

<style scoped>
.story-pop {
  transform-box: fill-box;
  transform-origin: center;
  animation: story-pop 420ms cubic-bezier(0.2, 0.9, 0.3, 1.4) both;
}
.story-pop.no-motion { animation: none; }
@keyframes story-pop {
  from { transform: scale(0); opacity: 0; }
  to { transform: scale(1); opacity: 0.8; }
}
</style>
