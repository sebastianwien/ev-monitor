<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { Chart as ChartJS, CategoryScale, LinearScale, PointElement, LineElement, Title, Tooltip, Legend, Filler } from 'chart.js'
import { Line } from 'vue-chartjs'
import { ArrowRightIcon } from '@heroicons/vue/24/outline'
import {
  getSeoAudit, getSeoOpportunities, getSeoOverview,
  type SeoMarket, type SeoOpportunities, type SeoOverview, type SeoPeriod, type SeoTotals,
} from '../../../api/seoService'
import { FINDINGS } from './seoHelp'
import { date, dec, num, pct, pos } from './seoFormat'
import type { SeoNavigation } from './seoNavigation'

ChartJS.register(CategoryScale, LinearScale, PointElement, LineElement, Title, Tooltip, Legend, Filler)

const props = defineProps<{ days: SeoPeriod; market: SeoMarket }>()
const emit = defineEmits<{ go: [target: SeoNavigation] }>()

const overview = ref<SeoOverview | null>(null)
const opportunities = ref<SeoOpportunities | null>(null)
const techErrors = ref<number | null>(null)
const loading = ref(false)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [o, opp] = await Promise.all([getSeoOverview(props.days, props.market), getSeoOpportunities(props.days, props.market)])
    overview.value = o
    opportunities.value = opp
  } catch {
    error.value = 'Der Überblick konnte nicht geladen werden.'
  } finally {
    loading.value = false
  }
  try {
    const audit = await getSeoAudit()
    techErrors.value = audit.day ? audit.summary.withErrors : null
  } catch {
    techErrors.value = null
  }
}

watch(() => [props.days, props.market], load)
onMounted(load)

interface Tile { label: string; value: string; delta: string; up: boolean | null; note?: string }

function relDelta(cur: number, prev: number, comparable: boolean): { text: string; up: boolean | null } {
  if (!comparable) return { text: 'kein vollständiger Vorzeitraum', up: null }
  if (!prev) return { text: cur ? 'vorher 0' : 'unverändert', up: cur ? true : null }
  const d = ((cur - prev) / prev) * 100
  if (Math.abs(d) < 0.5) return { text: 'unverändert', up: null }
  return { text: `${d > 0 ? '+' : ''}${d.toFixed(0)} % zum Vorzeitraum`, up: d > 0 }
}

const perThousand = (t: SeoTotals) => (t.impressions ? (t.registrations / t.impressions) * 1000 : 0)
const siteWide = computed(() => props.market !== 'ALL')

const groups = computed<{ title: string; hint: string; tiles: Tile[] }[]>(() => {
  const o = overview.value
  if (!o) return []
  const c = o.current
  const p = o.previous
  const ok = o.previousComplete
  const d = (cur: number, prev: number) => relDelta(cur, prev, ok)
  const ctrDiff = (c.ctr - p.ctr) * 100
  const posDiff = p.avgPosition && c.avgPosition ? p.avgPosition - c.avgPosition : 0
  const ptDiff = perThousand(c) - perThousand(p)
  const siteNote = siteWide.value ? 'ganze Website, alle Märkte' : undefined
  const imp = d(c.impressions, p.impressions)
  const clk = d(c.clicks, p.clicks)
  const vis = d(c.visitors, p.visitors)
  const goo = d(c.googleEntries, p.googleEntries)
  const reg = d(c.registrations, p.registrations)
  const org = d(c.organicRegistrations, p.organicRegistrations)
  const notComparable = 'kein vollständiger Vorzeitraum'
  return [
    {
      title: 'Bei Google', hint: 'Google Search Console, 2 bis 3 Tage Verzug',
      tiles: [
        { label: 'Impressionen', value: num(c.impressions), delta: imp.text, up: imp.up },
        { label: 'Klicks', value: num(c.clicks), delta: clk.text, up: clk.up },
        { label: 'Klickrate (CTR)', value: pct(c.ctr), delta: !ok ? notComparable : `${ctrDiff > 0 ? '+' : ''}${dec(ctrDiff)} Prozentpunkte`, up: !ok || Math.abs(ctrDiff) < 0.05 ? null : ctrDiff > 0 },
        { label: 'Ø Position', value: pos(c.avgPosition), delta: !ok ? notComparable : posDiff === 0 ? 'unverändert' : posDiff > 0 ? `${dec(posDiff)} Plätze besser` : `${dec(-posDiff)} Plätze schlechter`, up: !ok || posDiff === 0 ? null : posDiff > 0 },
      ],
    },
    {
      title: 'Auf der Website', hint: 'Plausible, ohne Cookies gezählt',
      tiles: [
        { label: 'Besucher', value: num(c.visitors), delta: vis.text, up: vis.up, note: siteNote },
        { label: 'Google-Einstiege', value: num(c.googleEntries), delta: goo.text, up: goo.up, note: siteNote },
      ],
    },
    {
      title: 'Ergebnis', hint: 'Neue Konten aus unserer Datenbank',
      tiles: [
        { label: 'Registrierungen', value: num(c.registrations), delta: reg.text, up: reg.up, note: siteWide.value ? 'nur Konten mit bekannter erster Seite' : undefined },
        { label: 'davon über die Google-Suche', value: num(c.organicRegistrations), delta: org.text, up: org.up },
        { label: 'Registrierungen je 1 000 Impressionen', value: dec(perThousand(c)), delta: !ok ? notComparable : Math.abs(ptDiff) < 0.05 ? 'unverändert' : `${ptDiff > 0 ? '+' : ''}${dec(ptDiff)} zum Vorzeitraum`, up: !ok || Math.abs(ptDiff) < 0.05 ? null : ptDiff > 0 },
      ],
    },
  ]
})

interface Action { key: string; count: number; label: string; text: string; target: SeoNavigation }

const actions = computed<Action[]>(() => {
  const o = opportunities.value
  if (!o) return []
  const list: Action[] = [
    { key: 'tech', count: techErrors.value ?? 0, label: 'Seiten mit technischem Fehler', text: 'Google bekommt diese Seiten nicht einwandfrei. Zuerst beheben.', target: { sub: 'tech' } },
    { key: 'LEAK', count: o.leak, label: FINDINGS.LEAK.label, text: FINDINGS.LEAK.meaning, target: { sub: 'pages', pageFilter: 'LEAK' } },
    { key: 'UNDERRATED', count: o.underrated, label: FINDINGS.UNDERRATED.label, text: FINDINGS.UNDERRATED.meaning, target: { sub: 'pages', pageFilter: 'UNDERRATED' } },
    { key: 'STRIKING_DISTANCE', count: o.striking, label: FINDINGS.STRIKING_DISTANCE.label, text: FINDINGS.STRIKING_DISTANCE.meaning, target: { sub: 'queries', queryFilter: 'STRIKING_DISTANCE' } },
    { key: 'CANNIBALIZED', count: o.cannibalized, label: FINDINGS.CANNIBALIZED.label, text: FINDINGS.CANNIBALIZED.meaning, target: { sub: 'queries', queryFilter: 'CANNIBALIZED' } },
    { key: 'ADWORDS', count: o.adwords, label: FINDINGS.ADWORDS.label, text: FINDINGS.ADWORDS.meaning, target: { sub: 'queries', queryFilter: 'ADWORDS' } },
    { key: 'CONTENT_GAP', count: o.contentGap, label: FINDINGS.CONTENT_GAP.label, text: FINDINGS.CONTENT_GAP.meaning, target: { sub: 'models', modelFilter: 'CONTENT_GAP' } },
    { key: 'INVISIBLE', count: o.invisible, label: FINDINGS.INVISIBLE.label, text: FINDINGS.INVISIBLE.meaning, target: { sub: 'models', modelFilter: 'INVISIBLE' } },
    { key: 'DEAD', count: o.dead, label: FINDINGS.DEAD.label, text: FINDINGS.DEAD.meaning, target: { sub: 'pages', pageFilter: 'DEAD' } },
    { key: 'MONEY', count: o.money, label: FINDINGS.MONEY.label, text: FINDINGS.MONEY.meaning, target: { sub: 'pages', pageFilter: 'MONEY' } },
  ]
  return list.filter(a => a.count > 0)
})

const chartData = computed(() => {
  const days = overview.value?.days ?? []
  const sets = [
    { label: 'Impressionen', yAxisID: 'y', data: days.map(d => d.impressions), borderColor: 'rgb(129, 140, 248)', backgroundColor: 'rgba(129, 140, 248, 0.15)', fill: true, tension: 0.3, pointRadius: 2 },
    { label: 'Klicks', yAxisID: 'y1', data: days.map(d => d.clicks), borderColor: 'rgb(52, 211, 153)', backgroundColor: 'rgba(52, 211, 153, 0.1)', fill: false, tension: 0.3, pointRadius: 2 },
  ]
  if (!siteWide.value) {
    sets.push(
      { label: 'Google-Einstiege', yAxisID: 'y1', data: days.map(d => d.googleEntries), borderColor: 'rgb(251, 191, 36)', backgroundColor: 'rgba(251, 191, 36, 0.1)', fill: false, tension: 0.3, pointRadius: 2 },
      { label: 'Besucher', yAxisID: 'y1', data: days.map(d => d.visitors), borderColor: 'rgb(192, 132, 252)', backgroundColor: 'rgba(192, 132, 252, 0.1)', fill: false, tension: 0.3, pointRadius: 2 },
    )
  }
  return { labels: days.map(d => new Date(d.day).toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit' })), datasets: sets }
})

const axisTitle = (text: string) => ({ display: true, text, color: '#9ca3af' })
const chartOptions = computed(() => ({
  responsive: true,
  maintainAspectRatio: false,
  interaction: { mode: 'index' as const, intersect: false },
  plugins: { legend: { labels: { color: '#9ca3af', boxWidth: 12 } } },
  scales: {
    x: { ticks: { color: '#9ca3af', maxTicksLimit: 12 }, grid: { color: 'rgba(55, 65, 81, 0.5)' } },
    y: { beginAtZero: true, position: 'left' as const, title: axisTitle('Impressionen'), ticks: { color: '#9ca3af' }, grid: { color: 'rgba(55, 65, 81, 0.5)' } },
    y1: { beginAtZero: true, position: 'right' as const, title: axisTitle(siteWide.value ? 'Klicks' : 'Klicks, Einstiege, Besucher'), ticks: { color: '#9ca3af' }, grid: { drawOnChartArea: false } },
  },
}))
</script>

<template>
  <div class="space-y-6" :class="{ 'opacity-60': loading }">
    <p v-if="error" class="text-red-400 text-sm">{{ error }}</p>
    <p v-else-if="loading && !overview" class="text-gray-400 text-sm">Lädt...</p>
    <template v-if="overview">
      <p v-if="!overview.previousComplete" class="text-xs text-amber-300">
        Für den Zeitraum davor fehlen Daten, deshalb gibt es keine Veränderung in Prozent. Wähle einen kürzeren Zeitraum für einen Vergleich.
      </p>

      <section v-if="actions.length" class="bg-gray-900 rounded-sm border border-gray-800 p-4" aria-labelledby="seo-actions">
        <h3 id="seo-actions" class="text-sm font-semibold text-gray-100">Was du jetzt tun kannst</h3>
        <p class="text-xs text-gray-400 mt-0.5 mb-3">Sortiert nach Dringlichkeit. Klick führt zur passenden Liste mit Erklärung.</p>
        <ul class="divide-y divide-gray-800">
          <li v-for="a in actions" :key="a.key">
            <button type="button" class="w-full flex items-center gap-3 py-2 text-left hover:bg-gray-800/40 focus:outline-none focus:ring-2 focus:ring-indigo-500 rounded-sm px-2"
                    @click="emit('go', a.target)">
              <span class="text-lg font-bold tabular-nums text-indigo-300 w-12 text-right shrink-0">{{ num(a.count) }}</span>
              <span class="min-w-0">
                <span class="block text-sm font-medium text-gray-100">{{ a.label }}</span>
                <span class="block text-xs text-gray-400">{{ a.text }}</span>
              </span>
              <ArrowRightIcon class="h-4 w-4 text-gray-500 ml-auto shrink-0" aria-hidden="true" />
            </button>
          </li>
        </ul>
      </section>

      <section v-for="g in groups" :key="g.title" :aria-label="g.title">
        <h3 class="text-sm font-semibold text-gray-200">{{ g.title }} <span class="font-normal text-xs text-gray-500">{{ g.hint }}</span></h3>
        <div class="grid grid-cols-2 md:grid-cols-4 gap-3 mt-2">
          <div v-for="t in g.tiles" :key="t.label" class="bg-gray-900 rounded-sm px-4 py-3 border border-gray-800">
            <div class="text-xl font-bold tabular-nums text-indigo-300">{{ t.value }}</div>
            <div class="text-xs text-gray-300 mt-1">{{ t.label }}</div>
            <div :class="['text-xs', t.up === null ? 'text-gray-500' : t.up ? 'text-emerald-400' : 'text-red-400']">{{ t.delta }}</div>
            <div v-if="t.note" class="text-xs text-gray-500">{{ t.note }}</div>
          </div>
        </div>
      </section>

      <section class="bg-gray-900 rounded-sm p-4 border border-gray-800">
        <h3 class="text-sm font-semibold text-gray-200 mb-1">Verlauf je Tag</h3>
        <p class="text-xs text-gray-500 mb-2">
          {{ date(overview.from) }} bis {{ date(overview.to) }}.
          <template v-if="siteWide">Besucher und Google-Einstiege gibt es nur für die ganze Website, deshalb fehlen sie hier. Wähle "Alle Märkte", um Klicks und Einstiege zu vergleichen.</template>
          <template v-else>Klicks (Google) und Google-Einstiege (Plausible) sollten fast gleich laufen. Gehen sie auseinander, prüfe den Unter-Tab Technik.</template>
        </p>
        <div class="h-64"><Line :data="chartData" :options="chartOptions" /></div>
      </section>
    </template>
  </div>
</template>
