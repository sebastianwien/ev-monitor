<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import {
  getSeoAudit, getSeoPageQueries, getSeoPages,
  type SeoBreakdown, type SeoMarket, type SeoModelFilter, type SeoModelLevel, type SeoPageFilter, type SeoPageRow, type SeoPageType, type SeoPeriod,
} from '../../../api/seoService'
import SeoDataTable from './SeoDataTable.vue'
import SeoFindingBadge from './SeoFindingBadge.vue'
import SeoFindingNote from './SeoFindingNote.vue'
import SeoBreakdownList from './SeoBreakdownList.vue'
import SeoModelsPanel from './SeoModelsPanel.vue'
import { FINDINGS } from './seoHelp'
import { GLOSSARY } from './seoGlossary'
import { PAGE_TYPE_LABELS } from './seoAnalysis'
import { num, pct, pos } from './seoFormat'
import type { SeoColumn } from './seoTable'
import { auditCountsByPath } from './seoAudit'

/**
 * Landingpages: Search Console vor dem Klick, Plausible und Sign-ups nach dem Klick, je Seite. Zweite Ansicht
 * "Modellseiten: Nachfrage und Datenbasis" stellt Suchnachfrage je Modell neben unsere Fahrzeugdaten.
 */
const props = defineProps<{ days: SeoPeriod; market: SeoMarket; search: string }>()
const filter = defineModel<SeoPageFilter>('filter', { required: true })
const modelFilter = defineModel<SeoModelFilter>('modelFilter', { required: true })
const modelLevel = defineModel<SeoModelLevel>('modelLevel', { required: true })
const view = defineModel<'pages' | 'models'>('view', { required: true })
const emit = defineEmits<{ audit: [] }>()

const FILTERS: SeoPageFilter[] = ['ALL', 'UNDERRATED', 'LEAK', 'MONEY', 'DEAD']

const rows = ref<SeoPageRow[]>([])
const pageType = ref<SeoPageType | 'ALL'>('ALL')
const loading = ref(false)
const error = ref('')
const audit = ref<Map<string, { errors: number; warnings: number }> | null>(null)
const queries = ref<Record<string, SeoBreakdown[]>>({})

async function load() {
  if (view.value !== 'pages') return
  loading.value = true
  error.value = ''
  queries.value = {}
  try {
    rows.value = await getSeoPages(props.days, filter.value, props.market)
  } catch {
    error.value = 'Die Landingpages konnten nicht geladen werden.'
  } finally {
    loading.value = false
  }
  if (!audit.value) {
    try {
      audit.value = auditCountsByPath((await getSeoAudit()).rows)
    } catch {
      // Spalte "Site Audit" bleibt leer
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

watch(() => [props.days, props.market, filter.value, view.value], load)
onMounted(load)

const typeOf = (r: SeoPageRow): SeoPageType => r.pageType ?? 'OTHER'
const types = computed(() => (Object.keys(PAGE_TYPE_LABELS) as SeoPageType[]).filter(t => rows.value.some(r => typeOf(r) === t)))
const visibleRows = computed(() => (pageType.value === 'ALL' ? rows.value : rows.value.filter(r => typeOf(r) === pageType.value)))
const hasDelta = computed(() => rows.value.some(r => r.clicksPrevious != null))
const hasPotential = computed(() => rows.value.some(r => r.potentialClicks !== undefined))

const g = (k: string) => `${GLOSSARY[k].term}: ${GLOSSARY[k].text}`
const delta = (r: SeoPageRow) => (r.clicksPrevious == null ? null : r.clicks - r.clicksPrevious)
const deltaText = (r: SeoPageRow) => { const d = delta(r); return !d ? '-' : `${d > 0 ? '+' : ''}${num(d)}` }
const deltaClass = (r: SeoPageRow) => { const d = delta(r) ?? 0; return d > 0 ? 'text-emerald-300' : d < 0 ? 'text-red-300' : 'text-gray-500' }
const columns = computed<SeoColumn<SeoPageRow>[]>(() => [
  { key: 'path', label: 'Landingpage', value: r => r.path },
  { key: 'impressions', label: 'Impressionen', hint: g('impressions'), source: 'GSC', numeric: true, value: r => r.impressions, format: r => num(r.impressions) },
  { key: 'clicks', label: 'Klicks', hint: g('clicks'), source: 'GSC', numeric: true, value: r => r.clicks, format: r => num(r.clicks) },
  ...(hasDelta.value ? [{ key: 'delta', label: 'Δ Klicks', hint: g('clicksDelta'), numeric: true, value: delta,
    format: deltaText }] : []),
  { key: 'ctr', label: 'CTR', hint: g('ctr'), source: 'GSC', numeric: true, value: r => (r.impressions ? r.ctr : null), format: r => (r.impressions ? pct(r.ctr) : '-'), csv: r => (r.impressions ? r.ctr * 100 : null) },
  { key: 'position', label: 'Ø Position', hint: g('position'), source: 'GSC', numeric: true, value: r => r.avgPosition || null, format: r => pos(r.avgPosition) },
  ...(hasPotential.value ? [{ key: 'potential', label: 'Klick-Potenzial', hint: g('potential'), numeric: true, value: (r: SeoPageRow) => r.potentialClicks || null,
    format: (r: SeoPageRow) => (r.potentialClicks ? `+${num(r.potentialClicks)}` : '-') }] : []),
  { key: 'entries', label: 'Einstiege', hint: g('entries'), source: 'Plausible', numeric: true, value: r => r.entries, format: r => (r.entries ? num(r.entries) : '-') },
  { key: 'bounce', label: 'Bounce Rate', hint: g('bounce'), source: 'Plausible', numeric: true, value: r => (r.entries ? r.bounceRate : null), format: r => (r.entries ? pct(r.bounceRate, 0) : '-'), csv: r => (r.entries ? r.bounceRate * 100 : null) },
  { key: 'cta', label: 'Conversion Rate', hint: g('cta'), source: 'Plausible', numeric: true, value: r => (r.entries ? r.ctaRate : null), format: r => (r.entries ? pct(r.ctaRate) : '-'), csv: r => (r.entries ? r.ctaRate * 100 : null) },
  { key: 'registrations', label: 'Sign-ups', hint: g('registrations'), source: 'DB', numeric: true, value: r => r.registrations, format: r => (r.registrations ? num(r.registrations) : '-') },
  { key: 'finding', label: 'Befund', value: r => (r.finding ? FINDINGS[r.finding]?.label ?? r.finding : null) },
  { key: 'tech', label: 'Site Audit', hint: 'Fehler und Hinweise aus dem nächtlichen Crawl, Details unter Site Audit', source: 'Crawl', value: r => techText(r.path) },
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
  <div class="space-y-3">
    <div class="flex gap-1 flex-wrap" role="group" aria-label="Ansicht">
      <button type="button" :class="btn(view === 'pages')" :aria-pressed="view === 'pages'" @click="view = 'pages'">Alle Landingpages</button>
      <button type="button" :class="btn(view === 'models')" :aria-pressed="view === 'models'" @click="view = 'models'">Modellseiten: Nachfrage und Datenbasis</button>
    </div>

    <SeoModelsPanel v-if="view === 'models'" v-model:filter="modelFilter" v-model:level="modelLevel" :days="days" />

    <div v-else class="space-y-3" :class="{ 'opacity-60': loading }">
      <div class="flex gap-3 flex-wrap">
        <div v-if="types.length > 1" class="flex gap-1 flex-wrap" role="group" aria-label="Seitentyp" :title="GLOSSARY.pageType.text">
          <button type="button" :class="btn(pageType === 'ALL')" :aria-pressed="pageType === 'ALL'" @click="pageType = 'ALL'">Alle Seitentypen</button>
          <button v-for="t in types" :key="t" type="button" :class="btn(pageType === t)" :aria-pressed="pageType === t" @click="pageType = t">{{ PAGE_TYPE_LABELS[t] }}</button>
        </div>
        <div class="flex gap-1 flex-wrap" role="group" aria-label="Befund">
          <button v-for="f in FILTERS" :key="f" type="button" :class="btn(filter === f)" :aria-pressed="filter === f" @click="filter = f">{{ filterLabel(f) }}</button>
        </div>
      </div>
      <SeoFindingNote :finding="filter === 'ALL' ? null : filter" />
      <p v-if="error" class="text-red-400 text-sm">{{ error }}</p>
      <SeoDataTable v-else :columns="columns" :rows="visibleRows" :row-key="r => r.path" :search-keys="['path']" search-placeholder="Landingpage suchen, z. B. Tesla"
                    :initial-search="search" csv-name="seo-landingpages" expandable empty="Keine Landingpages im Zeitraum." @expand="loadQueries">
        <template #cell-path="{ row }">
          <a :href="row.path" target="_blank" rel="noopener" class="font-mono hover:underline" @click.stop>{{ row.path }}</a>
        </template>
        <template #cell-delta="{ row }">
          <span :class="deltaClass(row)">{{ deltaText(row) }}</span>
        </template>
        <template #cell-registrations="{ row }">
          <span :class="row.registrations ? 'text-emerald-300' : 'text-gray-500'">{{ row.registrations ? num(row.registrations) : '-' }}</span>
        </template>
        <template #cell-finding="{ row }">
          <SeoFindingBadge v-if="row.finding" :finding="row.finding" />
        </template>
        <template #cell-tech="{ row }">
          <button v-if="audit?.get(row.path)" type="button" class="text-xs hover:underline focus:outline-none focus:ring-2 focus:ring-indigo-500 rounded-sm" @click.stop="emit('audit')">
            <span :class="audit.get(row.path)!.errors ? 'text-red-300' : audit.get(row.path)!.warnings ? 'text-amber-300' : 'text-emerald-400'">{{ techText(row.path) }}</span>
          </button>
          <span v-else class="text-gray-600">-</span>
        </template>
        <template #expand="{ row }">
          <SeoBreakdownList :rows="queries[row.path]" key-label="Keyword"
                            intro="Für diese Keywords hat Google die Seite angezeigt. Die häufigsten gehören in Title und Meta Description." />
        </template>
      </SeoDataTable>
    </div>
  </div>
</template>
