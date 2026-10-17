<template>
  <!-- Phone: the label over the values, one column per model. Desktop (md): the label in its own column. -->
  <div class="grid">
    <dl v-for="row in rows" :key="row.key" class="grid items-start gap-x-4 gap-y-2 border-t border-gray-100 py-3.5 dark:border-gray-800" :class="GRID[names.length]" data-testid="compare-line">
      <dt class="col-span-full text-center text-[12.5px] font-semibold uppercase tracking-[0.06em] text-gray-500 md:col-span-1 md:text-left md:text-[13.5px] md:font-normal md:normal-case md:tracking-normal dark:text-gray-400">{{ row.label }}</dt>
      <dd v-for="(cell, i) in row.cells" :key="i" class="grid min-w-0 content-start justify-items-center gap-1 text-center" data-testid="compare-cell">
        <span v-if="cell.bar" class="relative block h-2 w-full max-w-[200px] rounded bg-gray-200 dark:bg-gray-700" role="img" :aria-label="cell.bar.label">
          <span v-if="cell.bar.real != null" class="absolute inset-y-0 left-0 rounded" :class="cell.bar.tone" :style="{ width: `${cell.bar.real}%` }"></span>
          <span v-if="cell.bar.wltp != null" class="absolute -inset-y-1 w-0.5 rounded bg-gray-900 dark:bg-gray-100" :style="{ left: `calc(${cell.bar.wltp}% - 1px)` }"></span>
        </span>
        <span class="sr-only">{{ names[i] }}: </span>
        <span class="text-[15px] font-semibold leading-snug tabular-nums text-gray-900 dark:text-gray-100">
          <span v-if="cell.better" class="mr-1.5 inline-block h-2 w-2 rounded-full bg-green-600 align-middle dark:bg-green-400" aria-hidden="true" data-testid="compare-better"></span><span v-if="cell.better" class="sr-only">{{ t('compare.legend_better') }}: </span>{{ cell.value }}
        </span>
        <span v-for="line in cell.sub" :key="line" class="text-[12.5px] leading-snug text-gray-500 dark:text-gray-400">{{ line }}</span>
      </dd>
    </dl>
  </div>
</template>

<script lang="ts">
/** One value of a compared model: bold value, fine print, optional consumption bar, "better" mark */
export interface CompareCell {
  value: string
  sub: string[]
  better: boolean
  bar?: { real: number | null; wltp: number | null; tone: string; label: string }
}
export interface CompareRow { key: string; label: string; cells: CompareCell[] }

/** Row grid per number of models: phone one column per model, desktop a label column in front */
export const GRID: Record<number, string> = {
  2: 'grid-cols-2 md:grid-cols-[11rem_repeat(2,minmax(0,1fr))]',
  3: 'grid-cols-3 md:grid-cols-[11rem_repeat(3,minmax(0,1fr))]',
}
/** Just the model columns (the phone's floating names) */
export const COLS: Record<number, string> = { 2: 'grid-cols-2', 3: 'grid-cols-3' }
</script>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'

defineProps<{
  rows: CompareRow[]
  /** short model names, for screen readers in each cell */
  names: string[]
}>()

const { t } = useI18n()
</script>
