<template>
  <!-- One answer of the grid: a toned icon, then the sentence with its loud figure and the lines under it (slot) -->
  <li class="grid grid-cols-[36px_minmax(0,1fr)] gap-3 bg-white px-4 py-4 md:px-5 dark:bg-gray-900">
    <span class="grid h-9 w-9 place-items-center rounded-full" :class="TONE[tone]"><component :is="icon" class="h-5 w-5" aria-hidden="true" /></span>
    <div class="grid content-start" :class="dense ? 'gap-1.5' : 'gap-1'"><slot /></div>
  </li>
</template>

<script setup lang="ts">
import type { Component } from 'vue'

export type AnswerTone = 'sky' | 'green' | 'amber' | 'gray'

defineProps<{
  icon: Component
  tone: AnswerTone
  /** a little more air between the lines, for an answer with a row of icons under the sentence */
  dense?: boolean
}>()

/** The same tones as the follow bar and the model page hero: winter sky, charging green, trip amber, money grey */
const TONE: Record<AnswerTone, string> = {
  sky: 'bg-sky-50 text-sky-700 dark:bg-sky-900/40 dark:text-sky-300',
  green: 'bg-green-50 text-green-700 dark:bg-green-900/40 dark:text-green-300',
  amber: 'bg-amber-50 text-amber-700 dark:bg-amber-900/40 dark:text-amber-300',
  gray: 'bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-300',
}
</script>
