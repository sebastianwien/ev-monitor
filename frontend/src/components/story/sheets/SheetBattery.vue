<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useLocaleFormat } from '../../../composables/useLocaleFormat'
import { sohAxisBounds } from '../../../composables/sohPresentation'
import type { StoryTile } from '../../../composables/storyModel'
import type { BatterySohEntry } from '../../../api/carService'

/**
 * Akku-Gesundheit mit SoH-Verlauf. Feste Achse 80 bis 100 (sohAxisBounds), damit
 * Messrauschen nicht wie ein Einbruch aussieht.
 */
const props = defineProps<{
  tile: StoryTile
  history: BatterySohEntry[]
}>()

const { t, locale } = useI18n()
const { formatDecimal, formatDistance } = useLocaleFormat()

const text = computed(() => t('story.sheet_battery_text', {
  soh: formatDecimal(props.tile.value, 0),
  km: props.tile.odometerKm ? formatDistance(props.tile.odometerKm) : '–',
}))

const W = 320
const H = 120
const L = 28
const R = 8
const T = 10
const B = 20

const sorted = computed(() => [...props.history]
  .filter(e => Number.isFinite(e.sohPercent))
  .sort((a, b) => a.recordedAt.localeCompare(b.recordedAt)))
const bounds = computed(() => sohAxisBounds(sorted.value.map(e => e.sohPercent)))

const tRange = computed(() => {
  const ts = sorted.value.map(e => new Date(e.recordedAt).getTime())
  const min = Math.min(...ts)
  const max = Math.max(...ts)
  return { min, max: max === min ? min + 1 : max }
})
const x = (iso: string) => L + ((new Date(iso).getTime() - tRange.value.min) / (tRange.value.max - tRange.value.min)) * (W - L - R)
const y = (v: number) => T + (1 - (v - bounds.value.min) / (bounds.value.max - bounds.value.min)) * (H - T - B)

const path = computed(() => sorted.value.map((e, i) => `${i ? 'L' : 'M'}${x(e.recordedAt).toFixed(1)},${y(e.sohPercent).toFixed(1)}`).join(' '))
const dateLabel = (iso: string) => new Date(iso).toLocaleDateString(locale.value, { month: 'short', year: '2-digit' })
</script>

<template>
  <div>
    <p class="text-sm text-gray-600 dark:text-gray-300 mt-1">{{ text }}</p>

    <template v-if="sorted.length >= 2">
      <h4 class="mt-4 text-xs uppercase tracking-wider text-gray-500 dark:text-gray-400">{{ t('story.sheet_battery_history') }}</h4>
      <svg :viewBox="`0 0 ${W} ${H}`" class="block w-full h-auto mt-1" role="img">
        <g class="fill-gray-500 dark:fill-gray-400" font-size="9">
          <text :x="L - 4" :y="y(bounds.max) + 3" text-anchor="end">{{ bounds.max }}</text>
          <text :x="L - 4" :y="y(bounds.min) + 3" text-anchor="end">{{ bounds.min }}</text>
          <text :x="L" :y="H - 6">{{ dateLabel(sorted[0].recordedAt) }}</text>
          <text :x="W - R" :y="H - 6" text-anchor="end">{{ dateLabel(sorted[sorted.length - 1].recordedAt) }}</text>
        </g>
        <line :x1="L" :x2="W - R" :y1="y(bounds.min)" :y2="y(bounds.min)" class="stroke-gray-200 dark:stroke-gray-700" />
        <line :x1="L" :x2="W - R" :y1="y(90)" :y2="y(90)" v-if="bounds.min < 90" class="stroke-gray-200 dark:stroke-gray-700" stroke-dasharray="3 3" />
        <path :d="path" fill="none" stroke-width="2.5" stroke-linejoin="round" stroke-linecap="round" class="stroke-story-accent dark:stroke-story-accent-dark story-draw" pathLength="1" />
        <circle
          v-for="e in sorted" :key="e.id"
          :cx="x(e.recordedAt)" :cy="y(e.sohPercent)" r="3"
          :class="e.source === 'MANUAL' ? 'fill-gray-500' : 'fill-story-accent dark:fill-story-accent-dark'" />
      </svg>
    </template>
    <p v-else class="mt-3 text-xs text-gray-500 dark:text-gray-400">{{ t('story.sheet_battery_no_history') }}</p>
  </div>
</template>

<style scoped>
.story-draw {
  stroke-dasharray: 1;
  stroke-dashoffset: 1;
  animation: story-draw 900ms cubic-bezier(0.2, 0.7, 0.2, 1) forwards;
}
@keyframes story-draw { to { stroke-dashoffset: 0; } }
@media (prefers-reduced-motion: reduce) {
  .story-draw { animation: none; stroke-dashoffset: 0; }
}
</style>
