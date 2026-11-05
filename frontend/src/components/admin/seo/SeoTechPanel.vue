<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getSeoAudit, getSeoBots, getSeoPages, type SeoAuditRow, type SeoAuditView, type SeoBotSummary } from '../../../api/seoService'
import SeoDataTable from './SeoDataTable.vue'
import SeoIndexActions from './SeoIndexActions.vue'
import { CHECK_LABELS, checksPresent, filterAuditRows, type SeoAuditFilter } from './seoAudit'
import { caseVariants, type VariantGroup } from './seoAnalysis'
import { GLOSSARY } from './seoGlossary'
import { date, dateTime, dec, num } from './seoFormat'
import type { SeoColumn } from './seoTable'

/** Site Audit: nächtlicher Crawl der Sitemap wie Googlebot, dazu Duplicate URLs aus der Search Console. */
const view = ref<SeoAuditView | null>(null)
const variants = ref<VariantGroup[]>([])
const bots = ref<SeoBotSummary[]>([])
const BOT_DAYS = 28
const loading = ref(false)
const error = ref('')
const filter = ref<SeoAuditFilter>('ALL')

async function load() {
  loading.value = true
  error.value = ''
  try {
    view.value = await getSeoAudit()
  } catch {
    error.value = 'Der Site Audit konnte nicht geladen werden.'
  } finally {
    loading.value = false
  }
  const [v, b] = await Promise.allSettled([getSeoPages(90, 'ALL', 'DE'), getSeoBots(BOT_DAYS)])
  variants.value = v.status === 'fulfilled' ? caseVariants(v.value) : []
  bots.value = b.status === 'fulfilled' ? b.value : []
}

onMounted(load)

const rows = computed(() => filterAuditRows(view.value?.rows ?? [], filter.value))
const chips = computed(() => checksPresent(view.value?.rows ?? []))
const hasVitals = computed(() => (view.value?.rows ?? []).some(r => r.vitals))

const tiles = computed(() => {
  const s = view.value?.summary
  if (!s) return []
  return [
    { label: 'Seiten gecrawlt', value: s.checked, bad: false, filter: 'ALL' as SeoAuditFilter },
    { label: 'Seiten mit Fehler', value: s.withErrors, bad: s.withErrors > 0, filter: 'ERRORS' as SeoAuditFilter },
    { label: 'Nicht im Google-Index', value: s.notIndexed, bad: s.notIndexed > 0, filter: 'INDEX' as SeoAuditFilter },
    { label: 'Prerendering-Fehler', value: s.prerenderFailed, bad: s.prerenderFailed > 0, filter: 'PRERENDER' as SeoAuditFilter },
    { label: 'Seiten mit Broken Links', value: s.brokenLinkPages, bad: s.brokenLinkPages > 0, filter: 'BROKEN_LINK' as SeoAuditFilter },
    { label: 'Orphan Pages', value: s.orphans, bad: false, filter: 'ORPHAN' as SeoAuditFilter },
  ]
})

const vitalsTitle = (r: SeoAuditRow) => (r.vitals?.source === 'FIELD' ? 'Felddaten echter Chrome-Nutzer der letzten 28 Tage' : 'Labordaten (Lighthouse), für die Seite gibt es keine Felddaten')
const columns = computed<SeoColumn<SeoAuditRow>[]>(() => {
  const base: SeoColumn<SeoAuditRow>[] = [
    { key: 'path', label: 'URL', value: r => r.path },
    { key: 'status', label: 'HTTP-Status', hint: 'Antwort an Googlebot, 200 ist richtig, 0 = keine Antwort', source: 'Crawl', numeric: true, value: r => r.httpStatus },
    { key: 'score', label: 'Score', hint: '100 minus 20 je Fehler minus 5 je Hinweis', numeric: true, value: r => r.score },
    { key: 'errors', label: 'Fehler', numeric: true, value: r => r.errors, format: r => (r.errors ? String(r.errors) : '-') },
    { key: 'warnings', label: 'Hinweise', numeric: true, value: r => r.warnings, format: r => (r.warnings ? String(r.warnings) : '-') },
  ]
  if (hasVitals.value) {
    base.push(
      { key: 'lcp', label: 'LCP', hint: GLOSSARY.cwv.text, numeric: true, value: r => r.vitals?.lcpMs ?? null, format: r => (r.vitals?.lcpMs == null ? '' : `${dec(r.vitals.lcpMs / 1000)} s`) },
      { key: 'cls', label: 'CLS', hint: GLOSSARY.cwv.text, numeric: true, value: r => r.vitals?.cls ?? null, format: r => (r.vitals?.cls == null ? '' : dec(r.vitals.cls, 2)) },
      { key: 'inp', label: 'INP', hint: GLOSSARY.cwv.text, numeric: true, value: r => r.vitals?.inpMs ?? null, format: r => (r.vitals?.inpMs == null ? '' : `${r.vitals.inpMs} ms`) },
    )
  }
  base.push({ key: 'findings', label: 'Befunde', sortable: false, value: r => r.findings.map(f => `${CHECK_LABELS[f.check].label}: ${f.message}`).join(' | ') })
  return base
})

const btn = (active: boolean) => [
  'px-3 py-1.5 rounded-sm text-xs font-medium transition focus:outline-none focus:ring-2 focus:ring-indigo-500',
  active ? 'bg-indigo-600 text-white' : 'bg-gray-800 text-gray-300 hover:bg-gray-700',
]
const checksOf = (r: SeoAuditRow) => [...new Set(r.findings.map(f => f.check))]
const isError = (r: SeoAuditRow, c: string) => r.findings.some(f => f.check === c && f.severity === 'ERROR')
</script>

<template>
  <div class="space-y-4" :class="{ 'opacity-60': loading }">
    <p class="text-xs text-gray-400">
      <template v-if="view?.day">Gecrawlt am {{ date(view.day) }} (Lauf {{ dateTime(view.lastRunAt) }}): alle URLs aus der Sitemap so, wie Googlebot sie bekommt.</template>
      <template v-else-if="view && !view.enabled">Der nächtliche Crawl ist nicht eingerichtet, siehe "Datenquellen und Abrufe".</template>
      <template v-else-if="view">Der erste Crawl läuft heute Nacht um 5 Uhr.</template>
    </p>
    <p v-if="error" class="text-red-400 text-sm">{{ error }}</p>
    <p v-else-if="loading && !view" class="text-gray-400 text-sm">Lädt...</p>

    <template v-else-if="view?.day">
      <div class="grid grid-cols-2 md:grid-cols-6 gap-3">
        <button v-for="t in tiles" :key="t.label" type="button" class="text-left bg-gray-900 rounded-sm px-4 py-3 border border-gray-800 hover:border-gray-600 focus:outline-none focus:ring-2 focus:ring-indigo-500"
                @click="filter = t.filter">
          <span :class="['block text-xl font-bold tabular-nums', t.bad ? 'text-red-300' : 'text-indigo-300']">{{ t.value }}</span>
          <span class="block text-xs text-gray-300 mt-1">{{ t.label }}</span>
        </button>
      </div>

      <div class="flex gap-1 flex-wrap" role="group" aria-label="Prüfung">
        <button type="button" :class="btn(filter === 'ALL')" :aria-pressed="filter === 'ALL'" @click="filter = 'ALL'">Alle</button>
        <button type="button" :class="btn(filter === 'ERRORS')" :aria-pressed="filter === 'ERRORS'" @click="filter = 'ERRORS'">Nur Fehler</button>
        <button v-for="c in chips" :key="c" type="button" :class="btn(filter === c)" :aria-pressed="filter === c" :title="CHECK_LABELS[c].hint" @click="filter = c">
          {{ CHECK_LABELS[c].label }}
        </button>
      </div>
      <p v-if="filter !== 'ALL' && filter !== 'ERRORS'" class="border-l-2 border-indigo-500 bg-gray-900 px-4 py-3 text-sm text-gray-200">
        {{ CHECK_LABELS[filter].hint }}
      </p>

      <SeoDataTable :columns="columns" :rows="rows" :row-key="r => r.path" :search-keys="['path']" search-placeholder="URL suchen"
                    csv-name="seo-site-audit" expandable :can-expand="r => r.httpStatus === 200 || r.findings.length > 0" :initial-sort="{ key: 'score', dir: 'asc' }"
                    empty="Keine Seite mit diesem Befund.">
        <template #cell-path="{ row }">
          <a :href="row.path" target="_blank" rel="noopener" class="font-mono hover:underline" @click.stop>{{ row.path }}</a>
        </template>
        <template #cell-status="{ row }">
          <span :class="row.httpStatus === 200 ? 'text-gray-300' : 'text-red-300'">{{ row.httpStatus }}</span>
        </template>
        <template #cell-errors="{ row }">
          <span :class="row.errors ? 'text-red-300' : 'text-gray-500'">{{ row.errors || '-' }}</span>
        </template>
        <template #cell-warnings="{ row }">
          <span :class="row.warnings ? 'text-amber-300' : 'text-gray-500'">{{ row.warnings || '-' }}</span>
        </template>
        <template #cell-lcp="{ row }">
          <span :title="row.vitals ? vitalsTitle(row) : ''">{{ row.vitals?.lcpMs == null ? '' : `${dec(row.vitals.lcpMs / 1000)} s` }}</span>
        </template>
        <template #cell-findings="{ row }">
          <span v-if="!row.findings.length" class="text-xs text-emerald-400">ok</span>
          <span v-for="c in checksOf(row)" :key="c"
                :class="['inline-block text-xs px-2 py-0.5 rounded-full mr-1 whitespace-nowrap', isError(row, c) ? 'bg-red-900/60 text-red-200' : 'bg-amber-900/60 text-amber-200']">
            {{ CHECK_LABELS[c].label }}
          </span>
        </template>
        <template #expand="{ row }">
          <SeoIndexActions v-if="row.httpStatus === 200" :path="row.path" :index="row.index" class="mb-2" />
          <ul class="space-y-1">
            <li v-for="(f, i) in row.findings" :key="i" class="text-xs flex gap-2">
              <span :class="f.severity === 'ERROR' ? 'text-red-300' : 'text-amber-300'" class="shrink-0 w-16">{{ f.severity === 'ERROR' ? 'Fehler' : 'Hinweis' }}</span>
              <span class="text-gray-400 shrink-0 w-32">{{ CHECK_LABELS[f.check].label }}</span>
              <span class="text-gray-200 break-all whitespace-normal">{{ f.message }}</span>
            </li>
          </ul>
        </template>
      </SeoDataTable>
    </template>

    <section v-if="bots.length" class="bg-gray-900 rounded-sm border border-gray-800 p-4" aria-labelledby="seo-bots">
      <h3 id="seo-bots" class="text-sm font-semibold text-gray-200">Bot-Zugriffe <span class="font-normal text-xs text-gray-500">letzte {{ BOT_DAYS }} Tage, Modellseiten und Startseite</span></h3>
      <p class="text-xs text-gray-400 mt-1 mb-3">Wie oft Such- und KI-Crawler die öffentlichen Seiten abrufen. Ein KI-Crawler, der eine Seite nie abruft, kann sie auch nicht zitieren.</p>
      <ul class="divide-y divide-gray-800">
        <li v-for="b in bots" :key="b.bot" class="py-2 text-xs">
          <div class="flex items-baseline gap-2">
            <span class="w-16 shrink-0 text-right text-base font-bold tabular-nums text-indigo-300">{{ num(b.hits) }}</span>
            <span class="text-gray-100">{{ b.label }}</span>
            <span v-if="b.ai" class="rounded-full bg-indigo-900/60 px-2 py-0.5 text-indigo-200">KI</span>
          </div>
          <ul class="mt-1 space-y-0.5 pl-[4.5rem]">
            <li v-for="p in b.topPaths" :key="p.path" class="flex gap-2">
              <a :href="p.path" target="_blank" rel="noopener" class="font-mono text-gray-300 hover:underline truncate">{{ p.path }}</a>
              <span class="text-gray-500 tabular-nums">{{ num(p.hits) }}</span>
            </li>
          </ul>
        </li>
      </ul>
    </section>

    <section v-if="variants.length" class="bg-gray-900 rounded-sm border border-gray-800 p-4" aria-labelledby="seo-duplicates">
      <h3 id="seo-duplicates" class="text-sm font-semibold text-gray-200">Duplicate URLs <span class="font-normal text-xs text-gray-500">Search Console, letzte 90 Tage</span></h3>
      <p class="text-xs text-gray-400 mt-1 mb-3">{{ GLOSSARY.duplicate.text }} Abhilfe: alte Schreibweisen per 301 auf die kanonische Adresse umleiten und intern nur noch diese verlinken.</p>
      <ul class="space-y-2">
        <li v-for="g in variants" :key="g.paths[0].path" class="text-xs">
          <span class="text-gray-400 tabular-nums">{{ num(g.impressions) }} Impressionen auf {{ g.paths.length }} Adressen:</span>
          <span v-for="p in g.paths" :key="p.path" class="block pl-4 font-mono text-gray-200">{{ p.path }} <span class="text-gray-500 font-sans">({{ num(p.impressions) }})</span></span>
        </li>
      </ul>
    </section>
  </div>
</template>
