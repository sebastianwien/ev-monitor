<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { TemperatureEffect } from '../../composables/storyInsights'
import { scatterYScale } from '../../composables/storyInsights'
import { prefersReducedEffects } from '../../composables/prefersReducedEffects'
import { useLocaleFormat } from '../../composables/useLocaleFormat'

/**
 * Verbrauch je Fahrt gegen Aussentemperatur als SVG-Scatter, Linie = streckengewichtetes Mittel je 5-Grad-Bin.
 * y-Achse ab 0 mit Gitter und Herstellerlinie; einzelne Ausreisser (Kurzfahrten) stehen als Pfeil am oberen Rand.
 * Punkte poppen gestaffelt auf, Tipp zeigt die Fahrt.
 */
const props = withDefaults(defineProps<{
  effect: TemperatureEffect
  unitLabel: string
  /** Herstellerwert als gestrichelte Linie. */
  reference?: number | null
}>(), { reference: null })

const { t } = useI18n()
const { formatDecimal } = useLocaleFormat()

const W = 320
const H = 190
const L = 26
const R = 6
const T = 10
const B = 22

const reduceMotion = prefersReducedEffects()

const xDomain = computed(() => {
  const temps = props.effect.points.map(p => p.temp)
  return { min: Math.floor(Math.min(...temps)) - 2, max: Math.ceil(Math.max(...temps)) + 2 }
})
const yScale = computed(() => scatterYScale(props.effect.points.map(p => p.kwh100), props.reference))

const x = (temp: number) => L + ((temp - xDomain.value.min) / (xDomain.value.max - xDomain.value.min)) * (W - L - R)
const y = (v: number) => T + (1 - Math.min(v, yScale.value.max) / yScale.value.max) * (H - T - B)

const medianLine = computed(() => props.effect.bins
  .map(b => `${x(b.tempFrom + 2.5).toFixed(1)},${y(b.kwh100).toFixed(1)}`)
  .join(' '))

const selected = ref<number | null>(null)
const selectedPoint = computed(() => selected.value == null ? null : props.effect.points[selected.value])

/** Striche alle 5 °C innerhalb des Bereichs, Rand-Werte nur, wenn sonst weniger als zwei. */
const xTicks = computed(() => {
  const { min, max } = xDomain.value
  const ticks: number[] = []
  for (let v = Math.ceil(min / 5) * 5; v <= max; v += 5) ticks.push(v)
  return ticks.length >= 2 ? ticks : [min, max]
})
</script>

<template>
  <div>
    <p class="text-[12px] font-semibold text-gray-600 dark:text-gray-300">{{ unitLabel }}</p>
    <svg :viewBox="`0 0 ${W} ${H}`" class="mt-1 block h-auto w-full" role="img" :aria-label="t('story.sheet_wltp_temp_title')">
      <g class="stroke-gray-200 dark:stroke-gray-700" stroke-width="1">
        <line v-for="tk in yScale.ticks" :key="tk" :x1="L" :x2="W - R" :y1="y(tk)" :y2="y(tk)" />
      </g>
      <line :x1="L" :x2="W - R" :y1="H - B" :y2="H - B" class="stroke-gray-400 dark:stroke-gray-500" stroke-width="1" />

      <g class="fill-gray-600 dark:fill-gray-300" font-size="11">
        <text v-for="tk in yScale.ticks" :key="tk" :x="L - 5" :y="y(tk) + 4" text-anchor="end">{{ tk }}</text>
        <text v-for="(tk, i) in xTicks" :key="tk" :x="x(tk)" :y="H - B + 15" :text-anchor="i === 0 && x(tk) < L + 12 ? 'start' : 'middle'">{{ tk }} °C</text>
      </g>

      <template v-if="reference != null && reference <= yScale.max">
        <line data-role="reference" :x1="L" :x2="W - R" :y1="y(reference)" :y2="y(reference)" stroke-width="1.5" stroke-dasharray="5 4" class="stroke-gray-900 dark:stroke-gray-100" opacity="0.7" />
        <text :x="L + 4" :y="y(reference) - 4" text-anchor="start" font-size="11" font-weight="600" class="fill-gray-700 dark:fill-gray-200">{{ t('story.sheet_ref_short') }}</text>
      </template>

      <polyline v-if="effect.bins.length > 1" :points="medianLine" fill="none" stroke-width="2.5" stroke-linejoin="round" class="stroke-gray-800 dark:stroke-gray-100" opacity="0.85" />

      <template v-for="(p, i) in effect.points" :key="i">
        <path
          v-if="p.kwh100 > yScale.max"
          :d="`M${x(p.temp).toFixed(1)} ${T - 4} l-4 7 h8 z`"
          class="cursor-pointer fill-story-accent dark:fill-story-accent-dark"
          @click="selected = selected === i ? null : i" />
        <circle
          v-else
          :cx="x(p.temp)" :cy="y(p.kwh100)" :r="selected === i ? 5.5 : 3.5"
          class="story-pop cursor-pointer fill-story-accent dark:fill-story-accent-dark"
          :class="{ 'no-motion': reduceMotion }"
          :style="{ animationDelay: `${Math.min(i, 40) * 25}ms` }"
          opacity="0.8"
          @click="selected = selected === i ? null : i" />
      </template>
    </svg>
    <p class="mt-1 min-h-[1.25rem] text-[13px] text-gray-600 tabular-nums dark:text-gray-300">
      <template v-if="selectedPoint">{{ formatDecimal(selectedPoint.temp, 0) }} °C: {{ formatDecimal(selectedPoint.kwh100, 1) }} {{ unitLabel }}</template>
      <template v-else-if="yScale.above">{{ t('story.sheet_temp_above', { n: yScale.above, max: formatDecimal(yScale.aboveMax ?? 0, 0) }, yScale.above) }}</template>
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
