<script setup lang="ts">
import { computed } from 'vue'
import Collapse from '../shared/Collapse.vue'

/**
 * Monatsbalken fuer die Kachel-Sheets: Werte und Monate als HTML-Text (lesbar gross, kein skaliertes SVG),
 * Balken ab 0. Optional eine gestrichelte Referenzlinie (Herstellerwert) und ein heller letzter Balken
 * fuer den laufenden Monat, beide in der Legende darunter benannt. Mit `stack` steht ein graues Segment
 * auf jedem Balken (Fixkosten auf Strom), der Wert darueber ist dann die Summe. Aendern sich Werte oder
 * Stapel, gleiten Balken und Zahlen in 300 ms an ihre neue Hoehe; die Legende der Segmente klappt auf,
 * sobald ein Segment da ist.
 */
const props = withDefaults(defineProps<{
  values: number[]
  labels: string[]
  format: (v: number) => string
  reference?: { value: number; label: string } | null
  /** Gesetzt = letzter Balken ist der laufende Monat, der Text steht in der Legende. */
  partialLabel?: string
  tone?: 'good' | 'warn' | 'bad' | 'neutral'
  /** Oberes Segment je Balken, gleich lang wie `values`. */
  stack?: number[] | null
  /** Legende fuer unteres und oberes Segment, nur mit `stack`. */
  stackLabels?: { base: string; stack: string } | null
}>(), { reference: null, partialLabel: '', tone: 'neutral', stack: null, stackLabels: null })

const TONE = {
  good: 'bg-green-600 dark:bg-green-400',
  warn: 'bg-amber-600 dark:bg-amber-400',
  bad: 'bg-rose-600 dark:bg-rose-400',
  neutral: 'bg-gray-500 dark:bg-gray-400',
}

const top = (i: number) => props.stack?.[i] ?? 0
const totals = computed(() => props.values.map((v, i) => v + top(i)))
const max = computed(() => Math.max(...totals.value, props.reference?.value ?? 0, 0))
const stacked = computed(() => !!props.stack?.some(v => v > 0))
/** Hoehe und Lage gleiten gemeinsam, damit Segment, Balken und Zahl zusammen bleiben. */
const MOVE = 'transition-[height,bottom] duration-300 ease-out motion-reduce:transition-none'
const pct = (v: number) => `${max.value > 0 ? Math.round((Math.max(0, v) / max.value) * 1000) / 10 : 0}%`
const cols = computed(() => ({ gridTemplateColumns: `repeat(${props.values.length}, minmax(0, 1fr))` }))
const partial = (i: number) => !!props.partialLabel && i === props.values.length - 1
</script>

<template>
  <div>
    <div class="pt-6">
      <div class="relative h-28">
        <div
          v-if="reference" data-role="reference"
          class="pointer-events-none absolute inset-x-0 z-10 border-t-2 border-dashed border-gray-900/70 dark:border-gray-100/70"
          :style="{ bottom: pct(reference.value) }" />
        <div class="grid h-full gap-2" :style="cols">
          <div v-for="(v, i) in values" :key="i" class="relative h-full">
            <div
              data-role="bar"
              :data-partial="partial(i) ? 'true' : null"
              :title="partial(i) ? partialLabel : undefined"
              class="absolute inset-x-0 bottom-0 min-h-0.5"
              :class="[MOVE, TONE[tone], partial(i) ? 'opacity-35' : '', top(i) > 0 ? '' : 'rounded-t-md']"
              :style="{ height: pct(v) }" />
            <div
              v-if="stack"
              data-role="stack"
              class="absolute inset-x-0 rounded-t-md bg-gray-300 dark:bg-gray-600"
              :class="[MOVE, partial(i) ? 'opacity-35' : '']"
              :style="{ bottom: pct(v), height: pct(top(i)) }" />
            <span
              data-role="value"
              :class="MOVE"
              class="absolute inset-x-0 z-20 mb-1 whitespace-nowrap text-center text-[15px] font-bold leading-none text-gray-900 tabular-nums dark:text-gray-50"
              :style="{ bottom: pct(totals[i]) }">{{ format(totals[i]) }}</span>
          </div>
        </div>
      </div>
    </div>
    <div class="mt-1.5 grid gap-2 border-t border-gray-300 pt-1.5 dark:border-gray-600" :style="cols">
      <span v-for="(l, i) in labels" :key="i" data-role="label" class="text-center text-[13px] text-gray-600 dark:text-gray-300">{{ l }}</span>
    </div>
    <Collapse v-if="stack && stackLabels" :open="stacked">
      <div class="mt-2.5 flex flex-wrap gap-x-4 gap-y-1 text-[13px] text-gray-600 dark:text-gray-300" data-role="stack-legend">
        <span class="inline-flex items-center gap-1.5"><i class="h-2.5 w-2.5 rounded-sm" :class="TONE[tone]" />{{ stackLabels.base }}</span>
        <span class="inline-flex items-center gap-1.5"><i class="h-2.5 w-2.5 rounded-sm bg-gray-300 dark:bg-gray-600" />{{ stackLabels.stack }}</span>
      </div>
    </Collapse>
    <div v-if="reference" class="mt-2.5 flex flex-wrap gap-x-4 gap-y-1 text-[13px] text-gray-600 dark:text-gray-300">
      <span class="inline-flex items-center gap-1.5"><i class="w-4 border-t-2 border-dashed border-gray-900/70 dark:border-gray-100/70" />{{ reference.label }}</span>
    </div>
  </div>
</template>
