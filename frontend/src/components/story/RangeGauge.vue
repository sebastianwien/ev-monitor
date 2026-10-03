<script setup lang="ts">
import { computed } from 'vue'
import type { RangeEstimate } from '../../composables/storyEnergy'
import { prefersReducedEffects } from '../../composables/prefersReducedEffects'

/**
 * Akku-Silhouette mit Fuellstand des empfohlenen SoC-Fensters. Der Fuellstand faehrt
 * beim Erscheinen von links hoch, ohne reduced motion.
 */
const props = defineProps<{
  range: RangeEstimate
  /** Beschriftung des Fensters, z. B. "90 → 10 %". */
  label: string
}>()

const reduceMotion = prefersReducedEffects()

const rec = computed(() => props.range.windows.find(w => w.recommended) ?? props.range.windows[0])
const share = computed(() => (rec.value.socMax - rec.value.socMin) / 100)

const BODY_X = 2
const BODY_W = 180
const INNER_PAD = 4
</script>

<template>
  <svg viewBox="0 0 200 44" class="block w-full h-11" role="img">
    <rect :x="BODY_X" y="6" :width="BODY_W" height="32" rx="6" fill="none" stroke-width="2" class="stroke-gray-400 dark:stroke-gray-500" />
    <rect :x="BODY_X + BODY_W + 2" y="15" width="8" height="14" rx="2" class="fill-gray-400 dark:fill-gray-500" />
    <rect
      :x="BODY_X + INNER_PAD" :y="6 + INNER_PAD"
      :width="(BODY_W - INNER_PAD * 2) * share" :height="32 - INNER_PAD * 2" rx="3"
      class="fill-story-accent dark:fill-story-accent-dark story-range-fill"
      :class="{ 'no-motion': reduceMotion }"
      :style="{ transformOrigin: `${BODY_X + INNER_PAD}px center` }" />
    <text :x="BODY_X + INNER_PAD + ((BODY_W - INNER_PAD * 2) * share) / 2" y="22" text-anchor="middle" dominant-baseline="central" font-size="10" font-weight="700" class="fill-white dark:fill-gray-900">{{ label }}</text>
  </svg>
</template>

<style scoped>
.story-range-fill {
  transform-box: view-box;
  animation: story-range-fill 700ms cubic-bezier(0.2, 0.7, 0.2, 1) both;
}
.story-range-fill.no-motion { animation: none; }
@keyframes story-range-fill {
  from { transform: scaleX(0.08); }
  to { transform: scaleX(1); }
}
</style>
