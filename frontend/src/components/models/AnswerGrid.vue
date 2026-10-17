<template>
  <!-- The answers one per row on the phone, two by two from md up; the gap draws the lines. The tune slot
       (wallbox and price) is the last row across both columns. -->
  <ul ref="el" class="divide-y divide-green-600/15 overflow-hidden rounded-2xl bg-green-600/15 shadow-[0_1px_2px_rgba(0,0,0,0.05)] transition-opacity max-md:edge-card md:grid md:grid-cols-2 md:gap-px md:divide-y-0 dark:divide-gray-900 dark:bg-gray-900"
      :class="busy ? 'opacity-60' : ''" :aria-busy="busy">
    <slot />
    <li v-if="$slots.tune" class="bg-white px-4 py-4 md:col-span-2 md:px-5 dark:bg-gray-800"><slot name="tune" /></li>
  </ul>
</template>

<script setup lang="ts">
import { ref } from 'vue'

defineProps<{ busy?: boolean }>()

const el = ref<HTMLElement | null>(null)
/** The list element, for a page that watches where the answers are */
defineExpose({ el })
</script>
