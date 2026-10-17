<template>
  <!-- The reader's best 5 as a table: model, then the column of what matters most (marked), then the
       other two. Phone: the name across the full width, the three values below it, the heads once on top. -->
  <div class="grid gap-3 rounded-2xl bg-white px-4 py-4 shadow-[0_1px_2px_rgba(0,0,0,0.05)] max-md:edge-card md:px-5 dark:bg-gray-800" data-testid="top-five">
    <div class="grid gap-1">
      <h3 class="text-[17px] font-bold tracking-tight text-gray-900 dark:text-gray-100">{{ t('ranking_card.top_title') }}</h3>
      <p class="text-[13px] leading-snug text-gray-600 dark:text-gray-400">{{ sub }}</p>
    </div>
    <div role="table" :aria-label="t('ranking_card.top_title')" class="grid">
      <div role="row" class="grid grid-cols-3 gap-x-2 border-b border-gray-100 pb-2 text-[12px] font-semibold text-gray-500 md:grid-cols-[minmax(0,1.6fr)_repeat(3,minmax(0,1fr))] dark:border-gray-800 dark:text-gray-400">
        <span role="columnheader" class="hidden md:block">{{ t('ranking_card.col_model') }}</span>
        <!-- marked columns: green heads on the phone, one green band from head to last row from md -->
        <span v-for="c in columns" :key="c.key" role="columnheader" class="px-1.5 py-0.5 md:px-2.5"
              :class="c.marked ? 'text-green-800 md:-mb-2 md:rounded-t-lg md:bg-green-50 md:pb-2.5 md:pt-1.5 dark:text-green-300 md:dark:bg-white/5' : ''">{{ c.label }}</span>
      </div>
      <div v-for="r in rows" :key="r.key" role="row" class="grid grid-cols-3 gap-x-2 gap-y-1.5 border-b border-gray-100 py-3 last:border-b-0 md:grid-cols-[minmax(0,1.6fr)_repeat(3,minmax(0,1fr))] md:items-center dark:border-gray-800" data-testid="top-five-row">
        <span role="rowheader" class="col-span-3 flex min-w-0 items-center gap-2.5 md:col-span-1">
          <span class="grid h-6 w-6 flex-none place-items-center rounded-full bg-gray-900 text-[12px] font-bold text-white dark:bg-gray-100 dark:text-gray-900">{{ r.rank }}</span>
          <span class="grid min-w-0">
            <RouterLink :to="r.href" class="truncate font-semibold text-gray-900 underline-offset-2 hover:underline dark:text-gray-100" @click="emit('open', r.rank)">{{ r.name }}</RouterLink>
            <span class="truncate text-[12px] text-gray-500 dark:text-gray-400">{{ r.meta }}</span>
          </span>
        </span>
        <span v-for="c in columns" :key="c.key" role="cell" class="grid content-start px-1.5 py-1 leading-tight md:self-stretch md:content-center md:px-2.5"
              :class="c.marked ? 'md:-my-3 md:bg-green-50 md:py-4 md:dark:bg-white/5' : ''" :data-marked="c.marked || undefined" :data-testid="`top-five-${c.key}`">
          <b class="text-[14.5px] font-bold tabular-nums text-gray-900 dark:text-gray-100">{{ r[c.key].main }}</b>
          <small v-if="r[c.key].sub" class="text-[11.5px] text-gray-500 dark:text-gray-400">{{ r[c.key].sub }}</small>
        </span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { RankingPriority } from '../../utils/rankingAnswers'

export interface TopFiveCell { main: string; sub: string | null }
export interface TopFiveRow {
  key: string
  rank: number
  name: string
  /** class and drivers: "Mittelklasse · 12 Fahrer" */
  meta: string
  href: string
  trip: TopFiveCell
  charge: TopFiveCell
  month: TopFiveCell
}

const props = defineProps<{
  rows: TopFiveRow[]
  priority: RankingPriority
  /** "Urlaubsfahrt 600 km" */
  tripLabel: string
  sub: string
}>()
const emit = defineEmits<{ open: [rank: number] }>()
const { t } = useI18n()

type Column = 'trip' | 'charge' | 'month'
/** Marked and first: the columns the order rests on. Far = stops, then winter range (which sets how often you charge). */
const MARKED: Record<RankingPriority, Column[]> = { range: ['trip', 'charge'], breaks: ['trip', 'charge'], cost: ['month'] }
const columns = computed(() => {
  const label: Record<Column, string> = { trip: props.tripLabel, charge: t('ranking_card.col_charge'), month: t('ranking_card.col_month') }
  const marked = MARKED[props.priority]
  return [...marked, ...(['trip', 'charge', 'month'] as Column[]).filter(c => !marked.includes(c))]
    .map(key => ({ key, label: label[key], marked: marked.includes(key) }))
})
</script>
