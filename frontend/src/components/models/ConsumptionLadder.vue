<template>
  <!-- Purely visual: one dot for the real consumption on the shared scale, the light band
       behind it spans the battery variants. The same values are shown as text in the row,
       so screen readers skip it. --mr-surface (set by the list) is the row background. -->
  <span class="relative block h-[18px]" aria-hidden="true">
    <span class="absolute inset-x-0 top-2 h-0.5 rounded-full bg-gray-200 dark:bg-gray-700"></span>
    <span
      v-if="band"
      class="absolute top-1 h-2.5 rounded-full bg-green-200 dark:bg-green-900"
      :style="{ left: `${band.left}%`, width: `${band.width}%` }"
    ></span>
    <span
      v-if="realPos !== null"
      class="absolute top-0.5 -ml-[7px] h-3.5 w-3.5 rounded-full bg-green-600 ring-[3px] ring-[color:var(--mr-surface)] transition-[left] duration-500 ease-out motion-reduce:transition-none dark:bg-green-400"
      :style="{ left: `${realPos}%` }"
    ></span>
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { ladderPosition, type LadderAxis } from '../../utils/ladderScale'

// All values in kWh/100km; the axis converts to the display unit.
const props = defineProps<{
  axis: LadderAxis
  real: number | null
  bandMin?: number | null
  bandMax?: number | null
}>()

const realPos = computed(() => ladderPosition(props.real, props.axis))

function span(a: number | null, b: number | null) {
  if (a === null || b === null) return null
  return { left: Math.min(a, b), width: Math.abs(a - b) }
}

const band = computed(() => span(ladderPosition(props.bandMin, props.axis), ladderPosition(props.bandMax, props.axis)))
</script>
