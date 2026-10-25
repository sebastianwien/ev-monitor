<script setup lang="ts">
import { computed } from 'vue'
import type { ComparisonLevel } from '../../utils/communityComparison'

/**
 * Kennzahl im Feed-Raster mit Einordnung gegen einen Schnitt. App-CI: die Zahl bleibt
 * neutral und fett, die Abweichung steht klein daneben - gruen, wenn besser, amber, wenn
 * hoeher (nie rot). So unterscheiden sich +6 % und +120 % auf einen Blick, auch ohne Hover.
 * Die Erlaeuterung (Basis und Schnittwert) liegt im Tooltip, der per Hover und Fokus oeffnet.
 */
const props = defineProps<{
  level?: ComparisonLevel | null
  tooltip?: string | null
  /** Abweichung vom Schnitt in Prozent, negativ = darunter. Nur ausserhalb des ±5-%-Bands gezeigt. */
  deltaPercent?: number | null
}>()

const deltaText = computed(() => {
  if (props.deltaPercent == null || !props.level || props.level === 'similar') return null
  return `${props.deltaPercent > 0 ? '+' : '−'}${Math.abs(props.deltaPercent)} %`
})
const deltaClass = computed(() =>
  props.level === 'better' ? 'text-green-700 dark:text-green-400' : 'text-amber-700 dark:text-amber-400')
</script>

<template>
  <span class="relative inline-flex group" :tabindex="tooltip ? 0 : undefined">
    <span :class="['inline-flex items-baseline gap-1 whitespace-nowrap text-[13px] font-semibold tabular-nums text-gray-900 dark:text-gray-100', tooltip ? 'cursor-help' : '']">
      <slot />
      <span v-if="deltaText" class="text-[11.5px] font-semibold" :class="deltaClass">{{ deltaText }}</span>
    </span>
    <span v-if="tooltip" role="tooltip"
      class="pointer-events-none absolute bottom-full left-1/2 -translate-x-1/2 mb-1.5 z-50 hidden group-hover:block group-focus-visible:block
             w-max max-w-[240px] px-2.5 py-1.5 rounded-md bg-gray-900 dark:bg-gray-100 text-white dark:text-gray-900 text-[12px] leading-snug font-semibold whitespace-normal text-center">
      {{ tooltip }}
    </span>
  </span>
</template>
