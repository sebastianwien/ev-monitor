<script setup lang="ts">
import { computed } from 'vue'
import { comparisonChipClass, type ComparisonLevel } from '../../utils/communityComparison'

/**
 * Kennzahl-Chip im Feed, optional gegen einen Schnitt eingefaerbt (gruen = besser,
 * neutral = vergleichbar, amber = teurer/hoeher, rot = deutlich hoeher). Bei einer
 * Abweichung steht das Delta klein im Chip - so unterscheiden sich +6 % und +120 % auf
 * einen Blick, auch auf Mobile ohne Hover. Die Erlaeuterung (Basis und Schnittwert)
 * liegt im Tooltip, der per Hover und Tastaturfokus oeffnet.
 */
const props = defineProps<{
  level?: ComparisonLevel | null
  tooltip?: string | null
  /** Abweichung vom Schnitt in Prozent, negativ = darunter. Nur ausserhalb des ±5-%-Bands gezeigt. */
  deltaPercent?: number | null
}>()

const deltaText = computed(() => {
  if (props.deltaPercent == null || !props.level || props.level === 'similar') return null
  return `${props.deltaPercent > 0 ? '+' : '−'}${Math.abs(props.deltaPercent)}%`
})
</script>

<template>
  <span class="relative inline-flex group" :tabindex="tooltip ? 0 : undefined">
    <span :class="['inline-flex items-baseline gap-1 px-2 py-0.5 border rounded-full text-xs font-medium whitespace-nowrap',
                   comparisonChipClass(props.level ?? null), tooltip ? 'cursor-help' : '']">
      <slot />
      <span v-if="deltaText" class="text-[10px] font-normal opacity-75">{{ deltaText }}</span>
    </span>
    <span v-if="tooltip" role="tooltip"
      class="pointer-events-none absolute bottom-full left-1/2 -translate-x-1/2 mb-1.5 z-50 hidden group-hover:block group-focus-visible:block
             w-max max-w-[240px] px-2.5 py-1.5 rounded bg-gray-900 dark:bg-gray-100 text-white dark:text-gray-900 text-[11px] leading-snug font-normal whitespace-normal text-center shadow-lg">
      {{ tooltip }}
    </span>
  </span>
</template>
