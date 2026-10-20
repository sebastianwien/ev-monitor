<script setup lang="ts" generic="T">
import { computed, ref } from 'vue'
import { ArrowDownIcon, ArrowDownTrayIcon, ArrowUpIcon, ChevronDownIcon, ChevronRightIcon, MagnifyingGlassIcon } from '@heroicons/vue/24/outline'
import { downloadCsv, searchRows, sortRows, toCsv, type SeoColumn, type SortDir } from './seoTable'

/**
 * Tabelle des SEO-Tabs: Sortieren per Klick auf den Spaltenkopf, Suche, Zeilenzahl, CSV-Export (Excel, deutsch),
 * optional aufklappbare Zeilen (Slot "expand"). Zellen lassen sich per Slot "cell-<key>" überschreiben.
 */
const props = withDefaults(defineProps<{
  columns: SeoColumn<T>[]
  rows: T[]
  rowKey: (row: T) => string
  /** Spalten-Keys, in denen die Suche sucht; leer = keine Suche. */
  searchKeys?: string[]
  searchPlaceholder?: string
  csvName: string
  expandable?: boolean
  canExpand?: (row: T) => boolean
  initialSort?: { key: string; dir: SortDir } | null
  empty?: string
}>(), {
  searchKeys: () => [],
  searchPlaceholder: 'Suchen',
  expandable: false,
  canExpand: () => true,
  initialSort: null,
  empty: 'Keine Zeilen.',
})

const emit = defineEmits<{ expand: [row: T] }>()

const term = ref('')
const sortKey = ref<string | null>(props.initialSort?.key ?? null)
const sortDir = ref<SortDir>(props.initialSort?.dir ?? 'desc')
const open = ref<Set<string>>(new Set())

const searchColumns = computed(() => props.columns.filter(c => props.searchKeys.includes(c.key)))
const visible = computed(() => {
  const found = searchRows(props.rows, term.value, searchColumns.value)
  const col = props.columns.find(c => c.key === sortKey.value)
  return col ? sortRows(found, col, sortDir.value) : found
})

function sortBy(key: string) {
  const col = props.columns.find(c => c.key === key)
  if (sortKey.value === key) sortDir.value = sortDir.value === 'desc' ? 'asc' : 'desc'
  else {
    sortKey.value = key
    sortDir.value = col?.numeric ? 'desc' : 'asc'
  }
}

function ariaSort(key: string): 'ascending' | 'descending' | 'none' {
  if (sortKey.value !== key) return 'none'
  return sortDir.value === 'asc' ? 'ascending' : 'descending'
}

function toggle(row: T) {
  if (!props.expandable || !props.canExpand(row)) return
  const key = props.rowKey(row)
  const next = new Set(open.value)
  if (next.has(key)) next.delete(key)
  else {
    next.add(key)
    emit('expand', row)
  }
  open.value = next
}

function exportCsv() {
  const stamp = new Date().toISOString().slice(0, 10)
  downloadCsv(`${props.csvName}-${stamp}.csv`, toCsv(props.columns, visible.value))
}

const display = (c: SeoColumn<T>, row: T) => (c.format ? c.format(row) : String(c.value(row) ?? '-'))
const colspan = computed(() => props.columns.length + (props.expandable ? 1 : 0))
</script>

<template>
  <div class="bg-gray-900 rounded-sm border border-gray-800">
    <div class="flex items-center gap-3 px-3 py-2 border-b border-gray-800 flex-wrap">
      <label v-if="searchColumns.length" class="relative flex-1 min-w-[12rem] max-w-sm">
        <span class="sr-only">{{ searchPlaceholder }}</span>
        <MagnifyingGlassIcon class="h-4 w-4 text-gray-500 absolute left-2 top-1/2 -translate-y-1/2" aria-hidden="true" />
        <input v-model="term" type="search" :placeholder="searchPlaceholder"
               class="w-full bg-gray-950 border border-gray-700 rounded-sm pl-7 pr-2 py-1.5 text-sm text-gray-200 placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-indigo-500" />
      </label>
      <span class="text-xs text-gray-400" aria-live="polite">{{ visible.length.toLocaleString('de-DE') }} {{ visible.length === 1 ? 'Zeile' : 'Zeilen' }}</span>
      <button type="button" class="ml-auto inline-flex items-center gap-1.5 px-2.5 py-1.5 rounded-sm text-xs font-medium bg-gray-800 text-gray-300 hover:bg-gray-700 focus:outline-none focus:ring-2 focus:ring-indigo-500"
              :disabled="!visible.length" @click="exportCsv">
        <ArrowDownTrayIcon class="h-4 w-4" aria-hidden="true" />
        CSV für Excel
      </button>
    </div>
    <p v-if="!visible.length" class="text-sm text-gray-500 px-3 py-6 text-center">{{ term.trim() ? 'Keine Zeile passt zur Suche.' : empty }}</p>
    <div v-else class="overflow-x-auto">
      <table class="w-full text-sm">
        <thead>
          <tr class="text-xs text-gray-400 border-b border-gray-800">
            <th v-if="expandable" class="w-8 px-2" scope="col"><span class="sr-only">Aufklappen</span></th>
            <th v-for="c in columns" :key="c.key" scope="col" :aria-sort="ariaSort(c.key)"
                :class="['px-3 py-2 font-medium whitespace-nowrap', c.numeric ? 'text-right' : 'text-left']">
              <button v-if="c.sortable !== false" type="button" :title="c.hint"
                      class="inline-flex items-center gap-1 hover:text-gray-200 focus:outline-none focus:ring-2 focus:ring-indigo-500 rounded-sm"
                      @click="sortBy(c.key)">
                <span :class="{ 'underline decoration-dotted underline-offset-2': c.hint }">{{ c.label }}</span>
                <ArrowUpIcon v-if="sortKey === c.key && sortDir === 'asc'" class="h-3 w-3" aria-hidden="true" />
                <ArrowDownIcon v-else-if="sortKey === c.key" class="h-3 w-3" aria-hidden="true" />
              </button>
              <span v-else :title="c.hint">{{ c.label }}</span>
            </th>
          </tr>
        </thead>
        <tbody>
          <template v-for="row in visible" :key="rowKey(row)">
            <tr :class="['border-b border-gray-800/60', expandable && canExpand(row) ? 'cursor-pointer hover:bg-gray-800/40' : '']"
                @click="toggle(row)">
              <td v-if="expandable" class="px-2 text-gray-500">
                <button v-if="canExpand(row)" type="button" class="p-1 rounded-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                        :aria-expanded="open.has(rowKey(row))" :aria-label="open.has(rowKey(row)) ? 'Zuklappen' : 'Aufklappen'"
                        @click.stop="toggle(row)">
                  <ChevronDownIcon v-if="open.has(rowKey(row))" class="h-4 w-4" aria-hidden="true" />
                  <ChevronRightIcon v-else class="h-4 w-4" aria-hidden="true" />
                </button>
              </td>
              <td v-for="c in columns" :key="c.key"
                  :class="['px-3 py-1.5 whitespace-nowrap', c.numeric ? 'text-right tabular-nums text-gray-300' : 'text-gray-200']">
                <slot :name="`cell-${c.key}`" :row="row">{{ display(c, row) }}</slot>
              </td>
            </tr>
            <tr v-if="expandable && open.has(rowKey(row))" class="border-b border-gray-800/60 bg-gray-950/50">
              <td :colspan="colspan" class="px-3 py-3">
                <slot name="expand" :row="row" />
              </td>
            </tr>
          </template>
        </tbody>
      </table>
    </div>
  </div>
</template>
