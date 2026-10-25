<script setup lang="ts">
import { computed } from 'vue'

/**
 * Monatsbalken fuer die Kachel-Sheets: Werte und Monate als HTML-Text (lesbar gross, kein skaliertes SVG),
 * Balken ab 0. Optional eine gestrichelte Referenzlinie (Herstellerwert) und ein heller letzter Balken
 * fuer den laufenden Monat, beide in der Legende darunter benannt.
 */
const props = withDefaults(defineProps<{
  values: number[]
  labels: string[]
  format: (v: number) => string
  reference?: { value: number; label: string } | null
  /** Gesetzt = letzter Balken ist der laufende Monat, der Text steht in der Legende. */
  partialLabel?: string
  tone?: 'good' | 'warn' | 'bad' | 'neutral'
}>(), { reference: null, partialLabel: '', tone: 'neutral' })

const TONE = {
  good: 'bg-green-600 dark:bg-green-400',
  warn: 'bg-amber-600 dark:bg-amber-400',
  bad: 'bg-rose-600 dark:bg-rose-400',
  neutral: 'bg-gray-500 dark:bg-gray-400',
}

const max = computed(() => Math.max(...props.values, props.reference?.value ?? 0, 0))
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
              class="absolute inset-x-0 bottom-0 min-h-0.5 rounded-t-md"
              :class="[TONE[tone], partial(i) ? 'opacity-35' : '']"
              :style="{ height: pct(v) }" />
            <span
              data-role="value"
              class="absolute inset-x-0 z-20 mb-1 text-center text-[15px] font-bold leading-none text-gray-900 tabular-nums dark:text-gray-50"
              :style="{ bottom: pct(v) }">{{ format(v) }}</span>
          </div>
        </div>
      </div>
    </div>
    <div class="mt-1.5 grid gap-2 border-t border-gray-300 pt-1.5 dark:border-gray-600" :style="cols">
      <span v-for="(l, i) in labels" :key="i" data-role="label" class="text-center text-[13px] text-gray-600 dark:text-gray-300">{{ l }}</span>
    </div>
    <div v-if="reference" class="mt-2.5 flex flex-wrap gap-x-4 gap-y-1 text-[13px] text-gray-600 dark:text-gray-300">
      <span v-if="reference" class="inline-flex items-center gap-1.5"><i class="w-4 border-t-2 border-dashed border-gray-900/70 dark:border-gray-100/70" />{{ reference.label }}</span>
    </div>
  </div>
</template>
