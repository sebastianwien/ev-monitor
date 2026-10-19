<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { Chart as ChartJS, CategoryScale, LinearScale, PointElement, LineElement, Title, Tooltip, Legend, Filler } from 'chart.js'
import { Line } from 'vue-chartjs'
import {
  getSeoCampaigns, getSeoOverview, getSeoPages, getSeoQueries, getSeoStatus, triggerSeoSync,
  type SeoBrandFilter, type SeoCampaignRow, type SeoOverview, type SeoPageFilter, type SeoPageRow, type SeoPeriod,
  type SeoTotals, type SeoQueryFilter, type SeoQueryRow, type SeoStatus,
} from '../../api/seoService'

ChartJS.register(CategoryScale, LinearScale, PointElement, LineElement, Title, Tooltip, Legend, Filler)

type Sub = 'overview' | 'pages' | 'queries' | 'campaigns'

const PERIODS: SeoPeriod[] = [7, 28, 90]
const SUBS: { key: Sub; label: string }[] = [
  { key: 'overview', label: 'Überblick' },
  { key: 'pages', label: 'Seiten' },
  { key: 'queries', label: 'Suchbegriffe' },
  { key: 'campaigns', label: 'Kampagnen' },
]
const PAGE_FILTERS: { key: SeoPageFilter; label: string }[] = [
  { key: 'ALL', label: 'Alle' },
  { key: 'UNDERRATED', label: 'Unterschätzt' },
  { key: 'LEAK', label: 'Leck' },
  { key: 'MONEY', label: 'Geldseiten' },
  { key: 'DEAD', label: 'Tot' },
]
const BRAND_FILTERS: { key: SeoBrandFilter; label: string }[] = [
  { key: 'ALL', label: 'Alle' },
  { key: 'NON_BRAND', label: 'Non-Brand' },
  { key: 'BRAND', label: 'Brand' },
]
const QUERY_FILTERS: { key: SeoQueryFilter; label: string }[] = [
  { key: 'ALL', label: 'Alle' },
  { key: 'STRIKING_DISTANCE', label: 'Striking Distance' },
  { key: 'CANNIBALIZED', label: 'Kannibalisierung' },
  { key: 'ADWORDS', label: 'AdWords-Kandidaten' },
]
const FINDING_LABELS: Record<string, { label: string; cls: string; hint: string }> = {
  UNDERRATED: { label: 'Unterschätzt', cls: 'bg-amber-900/60 text-amber-200', hint: 'Position 4-15, viele Impressionen, CTR unter Median: Title und Description prüfen' },
  DEAD: { label: 'Tot', cls: 'bg-red-900/60 text-red-200', hint: 'Vorher Impressionen, im Zeitraum keine: Prerender und Canonical prüfen' },
  LEAK: { label: 'Leck', cls: 'bg-orange-900/60 text-orange-200', hint: 'Viele Klicks, hoher Absprung, CTA-Rate unter Median: Inhalt und UX der Seite prüfen' },
  MONEY: { label: 'Geldseite', cls: 'bg-emerald-900/60 text-emerald-200', hint: 'Meiste Registrierungen je Klick: Vorbild und schützen' },
  STRIKING_DISTANCE: { label: 'Striking Distance', cls: 'bg-amber-900/60 text-amber-200', hint: 'Position 5-20 mit vielen Impressionen: Inhalt ausbauen' },
  CANNIBALIZED: { label: 'Kannibalisierung', cls: 'bg-red-900/60 text-red-200', hint: 'Mehrere eigene Seiten für denselben Begriff: Canonical und interne Links prüfen' },
}

const days = ref<SeoPeriod>(28)
const sub = ref<Sub>('overview')
const pageFilter = ref<SeoPageFilter>('ALL')
const brandFilter = ref<SeoBrandFilter>('ALL')
const queryFilter = ref<SeoQueryFilter>('ALL')

const status = ref<SeoStatus | null>(null)
const overview = ref<SeoOverview | null>(null)
const pages = ref<SeoPageRow[]>([])
const queries = ref<SeoQueryRow[]>([])
const campaigns = ref<SeoCampaignRow[]>([])
const loading = ref(false)
const error = ref('')
const syncMessage = ref('')

async function loadStatus() {
  try {
    status.value = await getSeoStatus()
  } catch {
    error.value = 'SEO-Daten konnten nicht geladen werden.'
  }
}

async function loadSub() {
  loading.value = true
  error.value = ''
  try {
    if (sub.value === 'overview') overview.value = await getSeoOverview(days.value)
    else if (sub.value === 'pages') pages.value = await getSeoPages(days.value, pageFilter.value)
    else if (sub.value === 'campaigns') campaigns.value = await getSeoCampaigns(days.value)
    else queries.value = await getSeoQueries(days.value, brandFilter.value, queryFilter.value)
  } catch {
    error.value = 'SEO-Daten konnten nicht geladen werden.'
  } finally {
    loading.value = false
  }
}

async function startSync() {
  syncMessage.value = ''
  try {
    const started = await triggerSeoSync(30)
    syncMessage.value = started
      ? 'Sync gestartet, die letzten 30 Tage werden im Hintergrund geholt.'
      : 'Kein Sync gestartet: Zugang fehlt oder ein Lauf ist noch aktiv.'
    await loadStatus()
  } catch {
    syncMessage.value = 'Sync konnte nicht gestartet werden.'
  }
}

watch([days, sub, pageFilter, brandFilter, queryFilter], loadSub)
onMounted(async () => {
  await loadStatus()
  await loadSub()
})

const num = (v: number) => Number(v ?? 0).toLocaleString('de-DE')
const pct = (v: number, digits = 1) => `${(Number(v ?? 0) * 100).toFixed(digits).replace('.', ',')} %`
const pos = (v: number) => (v ? Number(v).toFixed(1).replace('.', ',') : '-')
const date = (iso: string | null | undefined) => (iso ? new Date(iso).toLocaleDateString('de-DE') : '-')
const dateTime = (iso: string | null | undefined) =>
  iso ? new Date(iso).toLocaleString('de-DE', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' }) : '-'

const lastRun = computed(() => status.value?.runs.find(r => r.source === 'GSC') ?? null)
const lastPlausibleRun = computed(() => status.value?.runs.find(r => r.source === 'PLAUSIBLE') ?? null)
const RUN_LABELS: Record<string, string> = { RUNNING: 'läuft', SUCCESS: 'ok', PARTIAL: 'teilweise', FAILED: 'fehlgeschlagen' }

interface Tile { label: string; value: string; delta: string; up: boolean | null }

function relDelta(cur: number, prev: number): { text: string; up: boolean | null } {
  if (!prev) return { text: 'kein Vorzeitraum', up: null }
  const d = ((cur - prev) / prev) * 100
  const sign = d > 0 ? '+' : ''
  return { text: `${sign}${d.toFixed(0)} % zum Vorzeitraum`, up: d === 0 ? null : d > 0 }
}

const tiles = computed<Tile[]>(() => {
  const o = overview.value
  if (!o) return []
  const c = o.current
  const p = o.previous
  const imp = relDelta(c.impressions, p.impressions)
  const clk = relDelta(c.clicks, p.clicks)
  const vis = relDelta(c.visitors, p.visitors)
  const goo = relDelta(c.googleEntries, p.googleEntries)
  const reg = relDelta(c.registrations, p.registrations)
  const org = relDelta(c.organicRegistrations, p.organicRegistrations)
  const perThousand = (t: SeoTotals) => (t.impressions ? (t.registrations / t.impressions) * 1000 : 0)
  const ptDiff = perThousand(c) - perThousand(p)
  const ctrDiff = (c.ctr - p.ctr) * 100
  const posDiff = p.avgPosition && c.avgPosition ? p.avgPosition - c.avgPosition : 0
  return [
    { label: 'Impressionen', value: num(c.impressions), delta: imp.text, up: imp.up },
    { label: 'Klicks', value: num(c.clicks), delta: clk.text, up: clk.up },
    { label: 'CTR', value: pct(c.ctr), delta: p.impressions ? `${ctrDiff > 0 ? '+' : ''}${ctrDiff.toFixed(1).replace('.', ',')} Pkt.` : 'kein Vorzeitraum', up: ctrDiff === 0 ? null : ctrDiff > 0 },
    { label: 'Ø Position', value: pos(c.avgPosition), delta: posDiff === 0 ? 'unverändert' : posDiff > 0 ? `besser um ${posDiff.toFixed(1).replace('.', ',')}` : `schlechter um ${(-posDiff).toFixed(1).replace('.', ',')}`, up: posDiff === 0 ? null : posDiff > 0 },
    { label: 'Besucher', value: num(c.visitors), delta: vis.text, up: vis.up },
    { label: 'Google-Einstiege', value: num(c.googleEntries), delta: goo.text, up: goo.up },
    { label: 'Registrierungen', value: num(c.registrations), delta: reg.text, up: reg.up },
    { label: 'davon Google organisch', value: num(c.organicRegistrations), delta: org.text, up: org.up },
    { label: 'Registrierungen je 1 000 Impr.', value: perThousand(c).toFixed(1).replace('.', ','), delta: p.impressions ? `${ptDiff > 0 ? '+' : ''}${ptDiff.toFixed(1).replace('.', ',')} zum Vorzeitraum` : 'kein Vorzeitraum', up: ptDiff === 0 ? null : ptDiff > 0 },
  ]
})

const chartData = computed(() => ({
  labels: overview.value?.days.map(d => d.day.slice(5)) ?? [],
  datasets: [
    { label: 'Impressionen', yAxisID: 'y', data: overview.value?.days.map(d => d.impressions) ?? [], borderColor: 'rgb(129, 140, 248)', backgroundColor: 'rgba(129, 140, 248, 0.15)', fill: true, tension: 0.3, pointRadius: 2 },
    { label: 'Klicks', yAxisID: 'y1', data: overview.value?.days.map(d => d.clicks) ?? [], borderColor: 'rgb(52, 211, 153)', backgroundColor: 'rgba(52, 211, 153, 0.1)', fill: false, tension: 0.3, pointRadius: 2 },
    { label: 'Google-Einstiege', yAxisID: 'y1', data: overview.value?.days.map(d => d.googleEntries) ?? [], borderColor: 'rgb(251, 191, 36)', backgroundColor: 'rgba(251, 191, 36, 0.1)', fill: false, tension: 0.3, pointRadius: 2 },
    { label: 'Besucher', yAxisID: 'y1', data: overview.value?.days.map(d => d.visitors) ?? [], borderColor: 'rgb(192, 132, 252)', backgroundColor: 'rgba(192, 132, 252, 0.1)', fill: false, tension: 0.3, pointRadius: 2, borderDash: [4, 3] },
  ],
}))

const chartOptions = {
  responsive: true,
  maintainAspectRatio: false,
  plugins: { legend: { labels: { color: '#9ca3af', boxWidth: 12 } } },
  scales: {
    x: { ticks: { color: '#9ca3af', maxTicksLimit: 12 }, grid: { color: 'rgba(55, 65, 81, 0.5)' } },
    y: { beginAtZero: true, position: 'left' as const, ticks: { color: '#9ca3af' }, grid: { color: 'rgba(55, 65, 81, 0.5)' } },
    y1: { beginAtZero: true, position: 'right' as const, ticks: { color: '#9ca3af' }, grid: { drawOnChartArea: false } },
  },
}

const btn = (active: boolean) => [
  'px-3 py-1.5 rounded-sm text-xs font-medium transition',
  active ? 'bg-indigo-600 text-white' : 'bg-gray-800 text-gray-300 hover:bg-gray-700',
]
</script>

<template>
  <div>
    <div class="flex items-start justify-between mb-4 gap-3 flex-wrap">
      <div>
        <h2 class="text-lg font-semibold text-white">SEO &amp; Traffic</h2>
        <p v-if="status" class="text-xs text-gray-400 mt-1">
          Search Console bis {{ date(status.dataUntil) }}
          <template v-if="lastRun">
            (Sync {{ dateTime(lastRun.startedAt) }}, {{ RUN_LABELS[lastRun.status] ?? lastRun.status }}, {{ num(lastRun.rowCount) }} Zeilen)
          </template>
          <template v-if="status.plausibleEnabled">
            · Plausible bis {{ date(status.plausibleDataUntil) }}
            <template v-if="lastPlausibleRun">
              (Sync {{ dateTime(lastPlausibleRun.startedAt) }}, {{ RUN_LABELS[lastPlausibleRun.status] ?? lastPlausibleRun.status }}, {{ num(lastPlausibleRun.rowCount) }} Zeilen)
            </template>
          </template>
          <template v-else>· Plausible aus (PLAUSIBLE_API_KEY fehlt)</template>
        </p>
      </div>
      <div class="flex gap-3 items-center flex-wrap">
        <div class="flex gap-1">
          <button v-for="p in PERIODS" :key="p" type="button" :class="btn(days === p)" @click="days = p">{{ p }} Tage</button>
        </div>
        <button type="button" class="px-3 py-1.5 rounded-sm text-xs font-medium bg-gray-800 text-gray-300 hover:bg-gray-700 border border-gray-700"
                :disabled="status !== null && !status.enabled && !status.plausibleEnabled" @click="startSync">
          Jetzt synchronisieren
        </button>
      </div>
    </div>
    <p v-if="syncMessage" class="text-xs text-gray-300 mb-3">{{ syncMessage }}</p>

    <div class="flex gap-1 mb-4">
      <button v-for="s in SUBS" :key="s.key" type="button" :class="btn(sub === s.key)" @click="sub = s.key">{{ s.label }}</button>
    </div>

    <p v-if="error" class="text-red-400 text-sm mb-4">{{ error }}</p>
    <p v-else-if="status && !status.enabled" class="text-gray-500 text-sm py-8 text-center">
      Keine Daten - GSC_SERVICE_ACCOUNT_JSON gesetzt?
    </p>
    <p v-else-if="loading && !overview && !pages.length && !queries.length && !campaigns.length" class="text-gray-400 text-sm">Lädt...</p>

    <div v-else class="space-y-6" :class="{ 'opacity-60': loading }">
      <template v-if="sub === 'overview' && overview">
        <div class="grid grid-cols-2 md:grid-cols-4 gap-3">
          <div v-for="t in tiles" :key="t.label" class="bg-gray-900 rounded-sm px-4 py-3 border border-gray-800">
            <div class="text-xl font-bold tabular-nums text-indigo-300">{{ t.value }}</div>
            <div class="text-xs text-gray-300 mt-1">{{ t.label }}</div>
            <div :class="['text-xs', t.up === null ? 'text-gray-500' : t.up ? 'text-emerald-400' : 'text-red-400']">{{ t.delta }}</div>
          </div>
        </div>
        <section class="bg-gray-900 rounded-sm p-4 border border-gray-800">
          <h3 class="text-sm font-semibold text-gray-200 mb-1">Impressionen, Klicks, Google-Einstiege und Besucher je Tag</h3>
          <p class="text-xs text-gray-500 mb-2">{{ date(overview.from) }} bis {{ date(overview.to) }}. Klaffen Klicks (Search Console) und Google-Einstiege (Plausible) auseinander, stimmt Tracking oder Prerender nicht.</p>
          <div class="h-64"><Line :data="chartData" :options="chartOptions" /></div>
        </section>
      </template>

      <template v-else-if="sub === 'pages'">
        <div class="flex gap-1">
          <button v-for="f in PAGE_FILTERS" :key="f.key" type="button" :class="btn(pageFilter === f.key)" @click="pageFilter = f.key">{{ f.label }}</button>
        </div>
        <p v-if="!pages.length" class="text-sm text-gray-500">Keine Seiten im Zeitraum.</p>
        <div v-else class="overflow-x-auto bg-gray-900 rounded-sm border border-gray-800">
          <table class="w-full text-sm">
            <thead>
              <tr class="text-xs text-gray-400 border-b border-gray-800">
                <th class="text-left px-3 py-2 font-medium">Pfad</th>
                <th class="text-right px-3 py-2 font-medium">Impr.</th>
                <th class="text-right px-3 py-2 font-medium">Klicks</th>
                <th class="text-right px-3 py-2 font-medium">CTR</th>
                <th class="text-right px-3 py-2 font-medium">Pos.</th>
                <th class="text-right px-3 py-2 font-medium" title="Besuche, die auf dieser Seite begonnen haben (Plausible)">Einstiege</th>
                <th class="text-right px-3 py-2 font-medium" title="Anteil der Einstiege ohne weitere Seite">Absprung</th>
                <th class="text-right px-3 py-2 font-medium" title="Einstiege mit Registrierungs-Klick, Code-Login oder Registrierung">CTA</th>
                <th class="text-right px-3 py-2 font-medium" title="Konten, deren erste besuchte Seite dieser Pfad war">Reg.</th>
                <th class="text-left px-3 py-2 font-medium">Befund</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="r in pages" :key="r.path" class="border-b border-gray-800/60">
                <td class="px-3 py-1.5 font-mono text-gray-200 whitespace-nowrap">{{ r.path }}</td>
                <td class="px-3 py-1.5 text-right tabular-nums text-gray-300">{{ num(r.impressions) }}</td>
                <td class="px-3 py-1.5 text-right tabular-nums text-gray-300">{{ num(r.clicks) }}</td>
                <td class="px-3 py-1.5 text-right tabular-nums text-gray-300">{{ r.impressions ? pct(r.ctr) : '-' }}</td>
                <td class="px-3 py-1.5 text-right tabular-nums text-gray-300">{{ pos(r.avgPosition) }}</td>
                <td class="px-3 py-1.5 text-right tabular-nums text-gray-300">{{ r.entries ? num(r.entries) : '-' }}</td>
                <td class="px-3 py-1.5 text-right tabular-nums text-gray-300">{{ r.entries ? pct(r.bounceRate, 0) : '-' }}</td>
                <td class="px-3 py-1.5 text-right tabular-nums text-gray-300">{{ r.entries ? pct(r.ctaRate) : '-' }}</td>
                <td class="px-3 py-1.5 text-right tabular-nums" :class="r.registrations ? 'text-emerald-300' : 'text-gray-500'">{{ r.registrations || '-' }}</td>
                <td class="px-3 py-1.5">
                  <span v-if="r.finding" :class="['inline-block text-xs px-2 py-0.5 rounded-full', FINDING_LABELS[r.finding].cls]" :title="FINDING_LABELS[r.finding].hint">
                    {{ FINDING_LABELS[r.finding].label }}
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </template>

      <template v-else-if="sub === 'queries'">
        <div class="flex gap-3 flex-wrap">
          <div class="flex gap-1">
            <button v-for="f in BRAND_FILTERS" :key="f.key" type="button" :class="btn(brandFilter === f.key)" @click="brandFilter = f.key">{{ f.label }}</button>
          </div>
          <div class="flex gap-1">
            <button v-for="f in QUERY_FILTERS" :key="f.key" type="button" :class="btn(queryFilter === f.key)" @click="queryFilter = f.key">{{ f.label }}</button>
          </div>
        </div>
        <p v-if="!queries.length" class="text-sm text-gray-500">Keine Suchbegriffe im Zeitraum.</p>
        <div v-else class="overflow-x-auto bg-gray-900 rounded-sm border border-gray-800">
          <table class="w-full text-sm">
            <thead>
              <tr class="text-xs text-gray-400 border-b border-gray-800">
                <th class="text-left px-3 py-2 font-medium">Suchbegriff</th>
                <th class="text-right px-3 py-2 font-medium">Impr.</th>
                <th class="text-right px-3 py-2 font-medium">Klicks</th>
                <th class="text-right px-3 py-2 font-medium">CTR</th>
                <th class="text-right px-3 py-2 font-medium">Pos.</th>
                <th class="text-left px-3 py-2 font-medium">Beste Seite</th>
                <th class="text-right px-3 py-2 font-medium">Seiten</th>
                <th class="text-left px-3 py-2 font-medium">Befund</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="r in queries" :key="r.query" class="border-b border-gray-800/60">
                <td class="px-3 py-1.5 text-gray-200">{{ r.query }}<span v-if="r.brand" class="ml-2 text-xs text-gray-500">Brand</span><span v-if="r.adwordsCandidate" class="ml-2 text-xs text-sky-300" title="Non-Brand, Position über 10, Zielseite konvertiert organisch gut: bezahlter Klick lohnt">AdWords</span></td>
                <td class="px-3 py-1.5 text-right tabular-nums text-gray-300">{{ num(r.impressions) }}</td>
                <td class="px-3 py-1.5 text-right tabular-nums text-gray-300">{{ num(r.clicks) }}</td>
                <td class="px-3 py-1.5 text-right tabular-nums text-gray-300">{{ r.impressions ? pct(r.ctr) : '-' }}</td>
                <td class="px-3 py-1.5 text-right tabular-nums text-gray-300">{{ pos(r.avgPosition) }}</td>
                <td class="px-3 py-1.5 font-mono text-gray-400 whitespace-nowrap">{{ r.topPath ?? '-' }}</td>
                <td class="px-3 py-1.5 text-right tabular-nums text-gray-300">{{ r.pageCount }}</td>
                <td class="px-3 py-1.5">
                  <span v-if="r.finding" :class="['inline-block text-xs px-2 py-0.5 rounded-full', FINDING_LABELS[r.finding].cls]" :title="FINDING_LABELS[r.finding].hint">
                    {{ FINDING_LABELS[r.finding].label }}
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </template>

      <template v-else-if="sub === 'campaigns'">
        <p class="text-xs text-gray-500">Quelle, Medium und Kampagne aus Plausible. Registrierungen und Trials aus der Datenbank nur, wenn der Link UTM-Parameter trug oder die Quelle Google organisch ist; sonst "-".</p>
        <p v-if="!campaigns.length" class="text-sm text-gray-500">Keine Quellen im Zeitraum.</p>
        <div v-else class="overflow-x-auto bg-gray-900 rounded-sm border border-gray-800">
          <table class="w-full text-sm">
            <thead>
              <tr class="text-xs text-gray-400 border-b border-gray-800">
                <th class="text-left px-3 py-2 font-medium">Quelle</th>
                <th class="text-left px-3 py-2 font-medium">Medium</th>
                <th class="text-left px-3 py-2 font-medium">Kampagne</th>
                <th class="text-right px-3 py-2 font-medium">Besucher</th>
                <th class="text-right px-3 py-2 font-medium" title="Besucher mit Registrierungs-Klick, Code-Login oder Registrierung">CTA</th>
                <th class="text-right px-3 py-2 font-medium" title="Konten aus der Datenbank">Reg.</th>
                <th class="text-right px-3 py-2 font-medium" title="davon Trial oder Abo">Trials</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="r in campaigns" :key="`${r.source}|${r.utmMedium}|${r.utmCampaign}`" class="border-b border-gray-800/60">
                <td class="px-3 py-1.5 text-gray-200">{{ r.source }}</td>
                <td class="px-3 py-1.5 text-gray-400">{{ r.utmMedium || '-' }}</td>
                <td class="px-3 py-1.5 text-gray-400">{{ r.utmCampaign || '-' }}</td>
                <td class="px-3 py-1.5 text-right tabular-nums text-gray-300">{{ num(r.visitors) }}</td>
                <td class="px-3 py-1.5 text-right tabular-nums text-gray-300">{{ r.visitors ? pct(r.ctaRate) : '-' }}</td>
                <td class="px-3 py-1.5 text-right tabular-nums" :class="r.attributed && r.registrations ? 'text-emerald-300' : 'text-gray-500'">{{ r.attributed ? r.registrations : '-' }}</td>
                <td class="px-3 py-1.5 text-right tabular-nums text-gray-300">{{ r.attributed ? r.trials : '-' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </template>
    </div>
  </div>
</template>
