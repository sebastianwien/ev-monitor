<script setup lang="ts">
import { BoltIcon, ChevronDownIcon, ChevronRightIcon, MapIcon } from '@heroicons/vue/24/outline'

/**
 * Eine Fahrt oder Ladung im Log-Feed, in der Anatomie des Story-Tagesblatts (DayTimeline):
 * Zeitspalte (Beginn, Ende), Symbolkreis (Fahrt weiß mit Kante, Ladung grün), Titel mit
 * grauer Unterzeile, rechts die Zahl der Zeile. Antippbar, wenn `interactive`: mit
 * `expanded` klappt die Zeile auf (Chevron dreht sich), ohne oeffnet sie ein Ziel (Pfeil).
 */
withDefaults(defineProps<{
  kind: 'trip' | 'charge'
  from: string
  to?: string
  interactive?: boolean
  /** Aufklappbare Zeile: Zustand fuer Chevron und aria-expanded; undefined = Zeile oeffnet ein Ziel. */
  expanded?: boolean
  /** Nur fuer Admins: Farbe des Fahrt-Symbols nach Datenquelle. */
  iconTone?: string
  controls?: string
}>(), { to: '', interactive: false, expanded: undefined, iconTone: '', controls: undefined })
const emit = defineEmits<{ activate: [] }>()
</script>

<template>
  <div
    :role="interactive ? 'button' : undefined"
    :tabindex="interactive ? 0 : undefined"
    :aria-expanded="interactive && expanded !== undefined ? expanded : undefined"
    :aria-controls="controls"
    class="flex items-start gap-3 px-4 py-3 tabular-nums"
    :class="interactive ? 'cursor-pointer transition-colors hover:bg-gray-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-story-accent dark:hover:bg-white/5' : ''"
    @click="interactive && emit('activate')"
    @keydown.enter.self.prevent="interactive && emit('activate')"
    @keydown.space.self.prevent="interactive && emit('activate')">
    <span class="w-11 shrink-0 pt-0.5 text-[13px] font-semibold leading-tight text-gray-900 dark:text-gray-100">
      {{ from }}<span v-if="to" class="block font-normal text-gray-400 dark:text-gray-500">{{ to }}</span>
    </span>
    <span v-if="kind === 'trip'"
      class="grid h-9 w-9 shrink-0 place-items-center rounded-full border-2 border-gray-900 bg-white dark:border-gray-200 dark:bg-gray-900"
      :class="iconTone">
      <MapIcon class="h-[18px] w-[18px]" aria-hidden="true" />
    </span>
    <span v-else
      class="grid h-9 w-9 shrink-0 place-items-center rounded-full border-2 border-gray-900 bg-green-600 text-white dark:border-transparent dark:bg-green-500 dark:text-gray-950">
      <BoltIcon class="h-[18px] w-[18px]" aria-hidden="true" />
    </span>
    <span class="min-w-0 flex-1">
      <span class="flex flex-wrap items-baseline gap-x-2 text-[15px] font-semibold leading-snug text-gray-900 dark:text-gray-100"><slot name="title" /></span>
      <span v-if="$slots.default" class="mt-0.5 block text-[13px] leading-snug text-gray-500 dark:text-gray-400"><slot /></span>
      <slot name="extra" />
    </span>
    <span v-if="$slots.right" class="shrink-0 pt-0.5 text-right"><slot name="right" /></span>
    <span v-if="$slots.actions" class="flex shrink-0 items-center" @click.stop><slot name="actions" /></span>
    <template v-if="interactive">
      <ChevronDownIcon v-if="expanded !== undefined" class="mt-1 h-4 w-4 shrink-0 text-gray-400 transition-transform duration-300 ease-out"
        :class="expanded ? 'rotate-180' : ''" aria-hidden="true" />
      <ChevronRightIcon v-else class="mt-1 h-4 w-4 shrink-0 text-gray-400" aria-hidden="true" />
    </template>
  </div>
</template>
