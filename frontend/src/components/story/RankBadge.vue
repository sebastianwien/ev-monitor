<script setup lang="ts">
import { computed } from 'vue'

/** Platz im Community-Ranking: Gold schimmert (vorhandene gold-shimmer-Keyframes), Silber, Bronze, sonst neutral. */
const props = defineProps<{ rank: number }>()

const cls = computed(() => {
  if (props.rank === 1) return 'story-rank-gold text-amber-900'
  if (props.rank === 2) return 'bg-gradient-to-br from-gray-200 to-gray-400 text-gray-800'
  if (props.rank === 3) return 'bg-gradient-to-br from-amber-600 to-amber-800 text-amber-50'
  return 'bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-200'
})
</script>

<template>
  <span class="inline-grid place-items-center w-11 h-11 rounded-full font-extrabold text-base tabular-nums shadow-sm" :class="cls" data-testid="story-rank">
    {{ rank }}
  </span>
</template>

<style scoped>
.story-rank-gold {
  background: linear-gradient(90deg, #d97706, #fbbf24, #fde68a, #fbbf24, #d97706);
  background-size: 200% auto;
  animation: gold-shimmer 6s linear infinite;
}
@media (prefers-reduced-motion: reduce) {
  .story-rank-gold { animation: none; }
}
</style>
