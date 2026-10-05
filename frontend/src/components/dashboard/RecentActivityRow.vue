<script setup lang="ts">
import { ChevronRightIcon } from '@heroicons/vue/24/outline'

/**
 * Eine Zeile der Liste "Zuletzt": randloses Vorschaubild links (wie das Autofoto der
 * Fahrzeugkarte darueber), Text daneben - der Text liegt
 * nie auf der Karte, deshalb braucht er weder Schleier noch Schattenkontur.
 * Die ganze Zeile ist der Button (Klick/aria-label kommen per Attribut-Fallthrough).
 */
defineProps<{
  tone: 'charge' | 'trip'
  label: string
  when: string
  /** Gefuelltes SoC-Segment in Prozent plus Beschriftung in Fahrtrichtung, oder null ohne SoC-Daten. */
  soc: { from: number; to: number; label: string } | null
}>()
</script>

<template>
  <button
    type="button"
    class="group flex w-full items-stretch gap-3 md:gap-4 pr-3 md:pr-4 text-left active:bg-gray-50 dark:active:bg-gray-700/40 focus-visible:outline-2 focus-visible:outline-offset-[-2px] focus-visible:outline-indigo-500"
  >
    <!-- Feste Mindesthoehe: die Karte rechnet ihren Ausschnitt einmal beim Aufbau, eine
         spaeter wachsende Zeile soll den Ausschnitt nicht verschieben. -->
    <div class="relative isolate w-24 md:w-28 min-h-24 md:min-h-28 shrink-0 overflow-hidden bg-gray-100 dark:bg-gray-700">
      <slot name="thumb" />
    </div>
    <div class="min-w-0 flex-1 self-center py-3">
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
      <div v-if="soc" class="mt-1.5 flex items-center gap-2 max-w-sm">
        <div class="relative flex-1 h-1 rounded-full bg-gray-200 dark:bg-gray-700" role="presentation">
          <div
          class="absolute inset-y-0 rounded-full"
          :class="tone === 'charge' ? 'bg-amber-500' : 'bg-indigo-500'"
          :style="{ left: soc.from + '%', width: `max(3px, ${soc.to - soc.from}%)` }"
          ></div>
        </div>
        <span class="text-[11px] text-gray-500 dark:text-gray-400 tabular-nums whitespace-nowrap">{{ soc.label }}</span>
      </div>
    </div>
    <ChevronRightIcon class="w-4 h-4 shrink-0 self-center text-gray-400" aria-hidden="true" />
  </button>
</template>
