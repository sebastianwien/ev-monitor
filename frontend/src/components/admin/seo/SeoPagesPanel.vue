<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import {
  getSeoAudit, getSeoPageQueries, getSeoPages,
  type SeoBreakdown, type SeoMarket, type SeoPageFilter, type SeoPageRow, type SeoPeriod,
} from '../../../api/seoService'
import SeoDataTable from './SeoDataTable.vue'
import SeoFindingBadge from './SeoFindingBadge.vue'
import SeoFindingNote from './SeoFindingNote.vue'
import SeoBreakdownList from './SeoBreakdownList.vue'
import { FINDINGS } from './seoHelp'
import { GLOSSARY } from './seoGlossary'
import { num, pct, pos } from './seoFormat'
import type { SeoColumn } from './seoTable'
import { auditCountsByPath } from './seoAudit'

const props = defineProps<{ days: SeoPeriod; market: SeoMarket }>()
const filter = defineModel<SeoPageFilter>('filter', { required: true })
const emit = defineEmits<{ tech: [] }>()

const FILTERS: SeoPageFilter[] = ['ALL', 'UNDERRATED', 'LEAK', 'MONEY', 'DEAD']

const rows = ref<SeoPageRow[]>([])
const loading = ref(false)
const error = ref('')
const audit = ref<Map<string, { errors: number; warnings: number }> | null>(null)
const queries = ref<Record<string, SeoBreakdown[]>>({})

async function load() {
  loading.value = true
  error.value = ''
  queries.value = {}
  try {
    rows.value = await getSeoPages(props.days, filter.value, props.market)
  } catch {
    error.value = 'Die Seiten konnten nicht geladen werden.'
  } finally {
    loading.value = false
  }
  if (!audit.value) {
    try {
      audit.value = auditCountsByPath((await getSeoAudit()).rows)
    } catch {
      // Spalte "Technik" bleibt leer
    }
  }
}

async function loadQueries(row: SeoPageRow) {
  if (queries.value[row.path]) return
  try {
    queries.value = { ...queries.value, [row.path]: await getSeoPageQueries(props.days, row.path) }
  } catch {
    queries.value = { ...queries.value, [row.path]: [] }
  }
}

watch(() => [props.days, props.market, filter.value], load)
onMounted(load)

const g = (k: string) => `${GLOSSARY[k].term}: ${GLOSSARY[k].text}`
const columns = computed<SeoColumn<SeoPageRow>[]>(() => [
  { key: 'path', label: 'Seite', value: r => r.path },
  { key: 'impressions', label: 'Impressionen', hint: g('impressions'), numeric: true, value: r => r.impressions, format: r => num(r.impressions) },
  { key: 'clicks', label: 'Klicks', hint: g('clicks'), numeric: true, value: r => r.clicks, format: r => num(r.clicks) },
  { key: 'ctr', label: 'Klickrate', hint: g('ctr'), numeric: true, value: r => (r.impressions ? r.ctr : null), format: r => (r.impressions ? pct(r.ctr) : '-'), csv: r => (r.impressions ? r.ctr * 100 : null) },
  { key: 'position', label: 'Position', hint: g('position'), numeric: true, value: r => r.avgPosition || null, format: r => pos(r.avgPosition) },
  { key: 'entries', label: 'Einstiege', hint: g('entries'), numeric: true, value: r => r.entries, format: r => (r.entries ? num(r.entries) : '-') },
  { key: 'bounce', label: 'Absprung', hint: g('bounce'), numeric: true, value: r => (r.entries ? r.bounceRate : null), format: r => (r.entries ? pct(r.bounceRate, 0) : '-'), csv: r => (r.entries ? r.bounceRate * 100 : null) },
  { key: 'cta', label: 'CTA-Rate', hint: g('cta'), numeric: true, value: r => (r.entries ? r.ctaRate : null), format: r => (r.entries ? pct(r.ctaRate) : '-'), csv: r => (r.entries ? r.ctaRate * 100 : null) },
  { key: 'registrations', label: 'Registr.', hint: g('registrations'), numeric: true, value: r => r.registrations, format: r => (r.registrations ? num(r.registrations) : '-') },
  { key: 'finding', label: 'Befund', value: r => (r.finding ? FINDINGS[r.finding]?.label ?? r.finding : null) },
  { key: 'tech', label: 'Technik', hint: 'Fehler und Hinweise aus der nächtlichen Prüfung, Details im Unter-Tab Technik', value: r => techText(r.path) },
])

function techText(path: string): string | null {
  const c = audit.value?.get(path)
  if (!c) return null
  if (!c.errors && !c.warnings) return 'ok'
  return [c.errors ? `${c.errors} Fehler` : '', c.warnings ? `${c.warnings} ${c.warnings === 1 ? 'Hinweis' : 'Hinweise'}` : ''].filter(Boolean).join(', ')
}

const filterLabel = (f: SeoPageFilter) => (f === 'ALL' ? 'Alle' : FINDINGS[f].label)
const btn = (active: boolean) => [
  'px-3 py-1.5 rounded-sm text-xs font-medium transition focus:outline-none focus:ring-2 focus:ring-indigo-500',
  active ? 'bg-indigo-600 text-white' : 'bg-gray-800 text-gray-300 hover:bg-gray-700',
]
</script>

<template>
  <div class="space-y-3" :class="{ 'opacity-60': loading }">
    <div class="flex gap-1 flex-wrap" role="group" aria-label="Befund">
      <button v-for="f in FILTERS" :key="f" type="button" :class="btn(filter === f)" :aria-pressed="filter === f" @click="filter = f">{{ filterLabel(f) }}</button>
    </div>
    <SeoFindingNote :finding="filter === 'ALL' ? null : filter" />
    <p v-if="error" class="text-red-400 text-sm">{{ error }}</p>
    <SeoDataTable v-else :columns="columns" :rows="rows" :row-key="r => r.path" :search-keys="['path']" search-placeholder="Seite suchen, z. B. Tesla"
                  csv-name="seo-seiten" expandable empty="Keine Seiten im Zeitraum." @expand="loadQueries">
      <template #cell-path="{ row }">
        <a :href="row.path" target="_blank" rel="noopener" class="font-mono hover:underline" @click.stop>{{ row.path }}</a>
      </template>
      <template #cell-registrations="{ row }">
        <span :class="row.registrations ? 'text-emerald-300' : 'text-gray-500'">{{ row.registrations ? num(row.registrations) : '-' }}</span>
      </template>
      <template #cell-finding="{ row }">
        <SeoFindingBadge v-if="row.finding" :finding="row.finding" />
      </template>
      <template #cell-tech="{ row }">
        <button v-if="audit?.get(row.path)" type="button" class="text-xs hover:underline focus:outline-none focus:ring-2 focus:ring-indigo-500 rounded-sm" @click.stop="emit('tech')">
          <span :class="audit.get(row.path)!.errors ? 'text-red-300' : audit.get(row.path)!.warnings ? 'text-amber-300' : 'text-emerald-400'">{{ techText(row.path) }}</span>
        </button>
        <span v-else class="text-gray-600">-</span>
      </template>
      <template #expand="{ row }">
        <SeoBreakdownList :rows="queries[row.path]" key-label="Suchbegriff"
                          intro="Für diese Suchbegriffe hat Google die Seite angezeigt. Die häufigsten gehören in Titel und Beschreibung." />
      </template>
    </SeoDataTable>
  </div>
</template>
