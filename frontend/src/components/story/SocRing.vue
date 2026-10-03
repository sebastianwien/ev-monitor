<script setup lang="ts">
import { computed } from 'vue'

/**
 * SoC-Ring als conic-gradient ueber die registrierte CSS-Variable --soc (index.css).
 * Weil --soc als <number> registriert ist, animiert der Browser den Wechsel zwischen
 * zwei Poll-Werten weich, ohne JavaScript.
 */
const props = defineProps<{
  soc: number
  socStart: number | null
  limit: number | null
}>()

const clamp = (v: number | null, fallback: number) => Math.max(0, Math.min(100, v ?? fallback))

const vars = computed(() => ({
  '--soc': String(clamp(props.soc, 0)),
  '--soc-start': String(clamp(props.socStart, 0)),
  '--soc-limit': String(clamp(props.limit, 100)),
}))
</script>

<template>
  <div
    role="progressbar"
    :aria-valuenow="Math.round(clamp(soc, 0))"
    aria-valuemin="0"
    aria-valuemax="100"
    class="story-soc-ring relative grid place-items-center w-36 h-36 md:w-44 md:h-44 rounded-full"
    :style="vars">
    <div class="absolute inset-[9px] rounded-full bg-gray-50 dark:bg-gray-900" />
    <div class="relative text-center">
      <slot />
    </div>
  </div>
</template>
