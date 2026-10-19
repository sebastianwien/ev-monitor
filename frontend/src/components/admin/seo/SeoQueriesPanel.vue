<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { Chart as ChartJS, CategoryScale, LinearScale, PointElement, LineElement, Title, Tooltip, Legend, Filler } from 'chart.js'
import { Line } from 'vue-chartjs'
import {
  getSeoBrandTrend, getSeoKeywordMetrics, getSeoQueries, getSeoQueryPages,
  type SeoKeywordMetric, type SeoBrandFilter, type SeoBrandWeek, type SeoBreakdown, type SeoMarket, type SeoPeriod, type SeoQueryFilter, type SeoQueryRow,
} from '../../../api/seoService'
import SeoDataTable from './SeoDataTable.vue'
import SeoFindingBadge from './SeoFindingBadge.vue'
import SeoFindingNote from './SeoFindingNote.vue'
import SeoBreakdownList from './SeoBreakdownList.vue'
import { FINDINGS } from './seoHelp'
import { GLOSSARY } from './seoGlossary'
import { num, pct, pos } from './seoFormat'
import type { SeoColumn } from './seoTable'
import { usd, visibilityShare } from './seoCompetition'

ChartJS.register(CategoryScale, LinearScale, PointElement, LineElement, Title, Tooltip, Legend, Filler)

const props = defineProps<{ days: SeoPeriod; market: SeoMarket }>()
const filter = defineModel<SeoQueryFilter>('filter', { required: true })
const brand = defineModel<SeoBrandFilter>('brand', { required: true })

const FILTERS: SeoQueryFilter[] = ['ALL', 'STRIKING_DISTANCE', 'CANNIBALIZED', 'ADWORDS']
const BRANDS: { key: SeoBrandFilter; label: string }[] = [
  { key: 'ALL', label: 'Alle Begriffe' }, { key: 'NON_BRAND', label: 'Ohne Markennamen' }, { key: 'BRAND', label: 'Mit Markennamen' },
]

const rows = ref<SeoQueryRow[]>([])
const trend = ref<SeoBrandWeek[]>([])
const loading = ref(false)
const error = ref('')
const pages = ref<Record<string, SeoBreakdown[]>>({})
/** Suchvolumen und CPC aus dem Unter-Tab Wettbewerb; leer, solange es dort keine Daten gibt. */
const metrics = ref<Map<string, SeoKeywordMetric>>(new Map())

async function load() {
  loading.value = true
  error.value = ''
  pages.value = {}
  try {
    rows.value = await getSeoQueries(props.days, brand.value, filter.value, props.market)
  } catch {
    error.value = 'Die Suchbegriffe konnten nicht geladen werden.'
  } finally {
    loading.value = false
  }
}

async function loadMetrics() {
  try {
    metrics.value = new Map((await getSeoKeywordMetrics()).map(m => [m.query, m]))
  } catch {
    metrics.value = new Map()
  }
}

async function loadTrend() {
  try {
    trend.value = await getSeoBrandTrend(26)
  } catch {
    trend.value = []
  }
}

async function loadPages(row: SeoQueryRow) {
  if (pages.value[row.query]) return
  try {
    pages.value = { ...pages.value, [row.query]: await getSeoQueryPages(props.days, row.query) }
  } catch {
    pages.value = { ...pages.value, [row.query]: [] }
  }
}

watch(() => [props.days, props.market, filter.value, brand.value], load)
onMounted(() => {
  load()
  loadTrend()
  loadMetrics()
})

const g = (k: string) => `${GLOSSARY[k].term}: ${GLOSSARY[k].text}`
const columns = computed<SeoColumn<SeoQueryRow>[]>(() => [
  { key: 'query', label: 'Suchbegriff', value: r => r.query },
  { key: 'impressions', label: 'Impressionen', hint: g('impressions'), numeric: true, value: r => r.impressions, format: r => num(r.impressions) },
  { key: 'clicks', label: 'Klicks', hint: g('clicks'), numeric: true, value: r => r.clicks, format: r => num(r.clicks) },
  { key: 'ctr', label: 'Klickrate', hint: g('ctr'), numeric: true, value: r => r.ctr, format: r => pct(r.ctr), csv: r => r.ctr * 100 },
  { key: 'position', label: 'Position', hint: g('position'), numeric: true, value: r => r.avgPosition || null, format: r => pos(r.avgPosition) },
  ...(metrics.value.size ? volumeColumns() : []),
  { key: 'topPath', label: 'Beste Seite', hint: 'Unsere Seite mit den meisten Klicks für den Begriff', value: r => r.topPath },
  { key: 'pages', label: 'Seiten', hint: 'Wie viele unserer Seiten für den Begriff erschienen sind', numeric: true, value: r => r.pageCount },
  { key: 'finding', label: 'Befund', value: r => [r.finding ? FINDINGS[r.finding]?.label : '', r.adwordsCandidate ? FINDINGS.ADWORDS.label : ''].filter(Boolean).join(', ') || null },
])

function volumeColumns(): SeoColumn<SeoQueryRow>[] {
  const vol = (r: SeoQueryRow) => metrics.value.get(r.query)?.volume ?? null
  const share = (r: SeoQueryRow) => visibilityShare(r.impressions, props.days, vol(r))
  return [
    { key: 'volume', label: 'Suchvolumen', hint: g('volume'), numeric: true, value: vol, format: r => (vol(r) == null ? '-' : num(vol(r) as number)) },
    { key: 'share', label: 'Sichtbarkeit', hint: g('share'), numeric: true, value: share, format: r => (share(r) == null ? '-' : pct(share(r) as number, 0)), csv: r => (share(r) == null ? null : (share(r) as number) * 100) },
    { key: 'cpc', label: 'CPC', hint: g('cpc'), numeric: true, value: r => metrics.value.get(r.query)?.cpc ?? null, format: r => usd(metrics.value.get(r.query)?.cpc) },
  ]
}

const filterLabel = (f: SeoQueryFilter) => (f === 'ALL' ? 'Alle' : f === 'ADWORDS' ? FINDINGS.ADWORDS.label : FINDINGS[f].label)
const btn = (active: boolean) => [
  'px-3 py-1.5 rounded-sm text-xs font-medium transition focus:outline-none focus:ring-2 focus:ring-indigo-500',
  active ? 'bg-indigo-600 text-white' : 'bg-gray-800 text-gray-300 hover:bg-gray-700',
]

const trendData = computed(() => ({
  labels: trend.value.map(w => new Date(w.weekStart).toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit' })),
  datasets: [
    { label: 'Suchen mit Markennamen (Impressionen)', data: trend.value.map(w => w.brandImpressions), borderColor: 'rgb(52, 211, 153)', backgroundColor: 'rgba(52, 211, 153, 0.15)', fill: true, tension: 0.3, pointRadius: 2, yAxisID: 'y' },
    { label: 'davon Klicks', data: trend.value.map(w => w.brandClicks), borderColor: 'rgb(251, 191, 36)', backgroundColor: 'rgba(251, 191, 36, 0.1)', fill: false, tension: 0.3, pointRadius: 2, yAxisID: 'y' },
  ],
}))
const trendOptions = {
  responsive: true,
  maintainAspectRatio: false,
  interaction: { mode: 'index' as const, intersect: false },
  plugins: { legend: { labels: { color: '#9ca3af', boxWidth: 12 } } },
  scales: {
    x: { title: { display: true, text: 'Woche ab', color: '#9ca3af' }, ticks: { color: '#9ca3af' }, grid: { color: 'rgba(55, 65, 81, 0.5)' } },
    y: { beginAtZero: true, ticks: { color: '#9ca3af' }, grid: { color: 'rgba(55, 65, 81, 0.5)' } },
  },
}
</script>

<template>
  <div class="space-y-3" :class="{ 'opacity-60': loading }">
    <details class="bg-gray-900 rounded-sm border border-gray-800 p-4">
      <summary class="cursor-pointer text-sm font-semibold text-gray-200">Wird EV Monitor bekannter? Suchen mit unserem Namen je Woche</summary>
      <p class="text-xs text-gray-500 mt-2 mb-2">Letzte 26 Wochen, alle Märkte. Steigt die Kurve, suchen mehr Menschen gezielt nach uns, etwa nach einem Forum-Beitrag oder einer Erwähnung. Kampagnenstarts hier gegenlesen.</p>
      <div v-if="trend.length" class="h-56"><Line :data="trendData" :options="trendOptions" /></div>
      <p v-else class="text-xs text-gray-500">Noch keine Suchen mit unserem Namen im Datenbestand.</p>
    </details>

    <div class="flex gap-3 flex-wrap">
      <div class="flex gap-1" role="group" aria-label="Markenname">
        <button v-for="b in BRANDS" :key="b.key" type="button" :class="btn(brand === b.key)" :aria-pressed="brand === b.key" @click="brand = b.key">{{ b.label }}</button>
      </div>
      <div class="flex gap-1 flex-wrap" role="group" aria-label="Befund">
        <button v-for="f in FILTERS" :key="f" type="button" :class="btn(filter === f)" :aria-pressed="filter === f" @click="filter = f">{{ filterLabel(f) }}</button>
      </div>
    </div>
    <SeoFindingNote :finding="filter === 'ALL' ? null : filter === 'ADWORDS' ? 'ADWORDS' : filter" />
    <p v-if="error" class="text-red-400 text-sm">{{ error }}</p>
    <SeoDataTable v-else :columns="columns" :rows="rows" :row-key="r => r.query" :search-keys="['query', 'topPath']" search-placeholder="Begriff oder Seite suchen"
                  csv-name="seo-suchbegriffe" expandable empty="Keine Suchbegriffe im Zeitraum." @expand="loadPages">
      <template #cell-query="{ row }">
        <span class="whitespace-normal">{{ row.query }}</span>
        <span v-if="row.brand" class="ml-1 text-xs text-gray-500">Marke</span>
      </template>
      <template #cell-topPath="{ row }">
        <a v-if="row.topPath" :href="row.topPath" target="_blank" rel="noopener" class="font-mono text-gray-400 hover:underline" @click.stop>{{ row.topPath }}</a>
      </template>
      <template #cell-finding="{ row }">
        <SeoFindingBadge v-if="row.finding" :finding="row.finding" />
        <SeoFindingBadge v-if="row.adwordsCandidate" finding="ADWORDS" class="ml-1" />
      </template>
      <template #expand="{ row }">
        <SeoBreakdownList :rows="pages[row.query]" key-label="Seite" links
                          intro="Diese unserer Seiten hat Google für den Begriff angezeigt. Mehr als eine heißt: Sie machen sich Konkurrenz." />
      </template>
    </SeoDataTable>
  </div>
</template>
