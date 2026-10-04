<script setup lang="ts">
import { ChevronRightIcon } from '@heroicons/vue/24/outline'

/**
 * Eine Zeile der Mobile-Liste "Zuletzt": Vorschaubild links, Text daneben - der Text liegt
 * nie auf der Karte, deshalb braucht er weder Schleier noch Schattenkontur.
 * Die ganze Zeile ist der Button (Klick/aria-label kommen per Attribut-Fallthrough).
 */
defineProps<{
  tone: 'charge' | 'trip'
  label: string
  when: string
  /** Gefuelltes SoC-Segment in Prozent, oder null ohne SoC-Daten. */
  soc: { from: number; to: number } | null
}>()
</script>

<template>
  <button
    type="button"
    class="group flex w-full items-center gap-3 px-4 py-3 text-left min-h-[44px] active:bg-gray-50 dark:active:bg-gray-700/40 focus-visible:outline-2 focus-visible:outline-offset-[-2px] focus-visible:outline-indigo-500"
  >
    <div class="relative isolate w-20 h-20 shrink-0 overflow-hidden rounded-lg bg-gray-100 dark:bg-gray-700">
      <slot name="thumb" />
    </div>
    <div class="min-w-0 flex-1">
      <div class="flex items-baseline gap-2">
        <span class="flex items-center gap-1.5 text-[13px] font-semibold text-gray-700 dark:text-gray-200">
          <span class="w-2 h-2 rounded-full" :class="tone === 'charge' ? 'bg-amber-500' : 'bg-indigo-500'" aria-hidden="true"></span>
          {{ label }}
        </span>
        <span class="ml-auto text-xs text-gray-500 dark:text-gray-400 whitespace-nowrap">{{ when }}</span>
      </div>
      <div class="mt-0.5 flex items-baseline gap-1.5 flex-wrap tabular-nums text-gray-900 dark:text-gray-100">
        <slot name="value" />
      </div>
      <div class="mt-0.5 flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-gray-500 dark:text-gray-400">
        <slot name="meta" />
      </div>
      <div v-if="soc" class="relative mt-1.5 h-1 rounded-full bg-gray-200 dark:bg-gray-700" role="presentation">
        <div
          class="absolute inset-y-0 rounded-full"
          :class="tone === 'charge' ? 'bg-amber-500' : 'bg-indigo-500'"
          :style="{ left: soc.from + '%', width: `max(3px, ${soc.to - soc.from}%)` }"
        ></div>
      </div>
    </div>
    <ChevronRightIcon class="w-4 h-4 shrink-0 text-gray-400" aria-hidden="true" />
  </button>
</template>
