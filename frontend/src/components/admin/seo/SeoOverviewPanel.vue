<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { Chart as ChartJS, CategoryScale, LinearScale, PointElement, LineElement, Title, Tooltip, Legend, Filler } from 'chart.js'
import { Line } from 'vue-chartjs'
import { ArrowRightIcon } from '@heroicons/vue/24/outline'
import {
  getSeoAudit, getSeoCampaigns, getSeoOpportunities, getSeoOverview, getSeoPages,
  type SeoBrandSplit, type SeoCampaignRow, type SeoMarket, type SeoOpportunities, type SeoOverview, type SeoPageRow, type SeoPeriod, type SeoTask, type SeoTotals,
} from '../../../api/seoService'
import SeoChannelTable from './SeoChannelTable.vue'
import { FINDINGS } from './seoHelp'
import { movers } from './seoAnalysis'
import { date, dec, num, pct, pos } from './seoFormat'
import type { SeoNavigation } from './seoNavigation'

ChartJS.register(CategoryScale, LinearScale, PointElement, LineElement, Title, Tooltip, Legend, Filler)

const props = defineProps<{ days: SeoPeriod; market: SeoMarket }>()
const emit = defineEmits<{ go: [target: SeoNavigation] }>()

const overview = ref<SeoOverview | null>(null)
const opportunities = ref<SeoOpportunities | null>(null)
const pages = ref<SeoPageRow[]>([])
const campaigns = ref<SeoCampaignRow[]>([])
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
    error.value = 'Die Übersicht konnte nicht geladen werden.'
  } finally {
    loading.value = false
  }
  // Ergänzende Bausteine: scheitern sie, fehlt nur ihr Abschnitt
  const [p, c, a] = await Promise.allSettled([getSeoPages(props.days, 'ALL', props.market), getSeoCampaigns(props.days), getSeoAudit()])
  pages.value = p.status === 'fulfilled' ? p.value : []
  campaigns.value = c.status === 'fulfilled' ? c.value : []
  techErrors.value = a.status === 'fulfilled' && a.value.day ? a.value.summary.withErrors : null
}

watch(() => [props.days, props.market], load)
onMounted(load)

interface Tile { label: string; value: string; delta: string; up: boolean | null; note?: string }

const notComparable = 'kein vollständiger Vorzeitraum'

function relDelta(cur: number, prev: number, comparable: boolean): { text: string; up: boolean | null } {
  if (!comparable) return { text: notComparable, up: null }
  if (!prev) return { text: cur ? 'vorher 0' : 'unverändert', up: cur ? true : null }
  const d = ((cur - prev) / prev) * 100
  if (Math.abs(d) < 0.5) return { text: 'unverändert', up: null }
  return { text: `${d > 0 ? '+' : ''}${d.toFixed(0)} % zum Vorzeitraum`, up: d > 0 }
}

/** Differenz in Prozentpunkten zweier Anteile (0-1). */
function pointDelta(cur: number, prev: number, comparable: boolean): { text: string; up: boolean | null } {
  if (!comparable) return { text: notComparable, up: null }
  const d = (cur - prev) * 100
  if (Math.abs(d) < 0.05) return { text: 'unverändert', up: null }
  return { text: `${d > 0 ? '+' : ''}${dec(d)} Prozentpunkte`, up: d > 0 }
}

const organicCr = (organic: number, sessions: number) => (sessions ? organic / sessions : 0)
/** Sitzungen aller Suchmaschinen; älteres Backend liefert nur Google. */
const search = (t: SeoTotals) => t.searchEntries ?? t.googleEntries

const groups = computed<{ title: string; hint: string; tiles: Tile[] }[]>(() => {
  const o = overview.value
  if (!o) return []
  const c = o.current
  const p = o.previous
  const ok = o.previousComplete
  const d = (cur: number, prev: number) => relDelta(cur, prev, ok)
  const posDiff = p.avgPosition && c.avgPosition ? p.avgPosition - c.avgPosition : 0
  const siteNote = 'ganze Website, alle Märkte'
  const tile = (label: string, value: string, delta: { text: string; up: boolean | null }, note?: string): Tile =>
    ({ label, value, delta: delta.text, up: delta.up, note })
  const result = [
    {
      title: 'Search Performance', hint: 'Google Search Console, 2 bis 3 Tage Verzug',
      tiles: [
        tile('Impressionen', num(c.impressions), d(c.impressions, p.impressions)),
        tile('Klicks', num(c.clicks), d(c.clicks, p.clicks)),
        tile('CTR', pct(c.ctr), pointDelta(c.ctr, p.ctr, ok)),
        tile('Ø Position', pos(c.avgPosition), !ok ? { text: notComparable, up: null }
          : posDiff === 0 ? { text: 'unverändert', up: null }
            : { text: posDiff > 0 ? `${dec(posDiff)} Plätze besser` : `${dec(-posDiff)} Plätze schlechter`, up: posDiff > 0 }),
      ],
    },
  ]
  const b: SeoBrandSplit | undefined = o.brand
  const bp: SeoBrandSplit | undefined = o.brandPrevious
  if (b && bp) {
    const share = b.brandClicks + b.nonBrandClicks ? b.nonBrandClicks / (b.brandClicks + b.nonBrandClicks) : 0
    const sharePrev = bp.brandClicks + bp.nonBrandClicks ? bp.nonBrandClicks / (bp.brandClicks + bp.nonBrandClicks) : 0
    result.push({
      title: 'Brand / Non-Brand', hint: 'Search Console, alle Märkte, ohne anonymisierte Queries',
      tiles: [
        tile('Non-Brand-Klicks', num(b.nonBrandClicks), d(b.nonBrandClicks, bp.nonBrandClicks)),
        tile('Brand-Klicks', num(b.brandClicks), d(b.brandClicks, bp.brandClicks)),
        tile('Non-Brand-Anteil an Klicks', pct(share, 0), pointDelta(share, sharePrev, ok)),
      ],
    })
  }
  result.push(
    {
      title: 'Traffic', hint: 'Plausible, ohne Cookies gezählt',
      tiles: [
        tile('Besucher', num(c.visitors), d(c.visitors, p.visitors), siteNote),
        tile('Organische Sitzungen (Google)', num(c.googleEntries), d(c.googleEntries, p.googleEntries), siteNote),
        tile('Organische Sitzungen (alle Suchmaschinen)', num(search(c)), d(search(c), search(p)), siteNote),
      ],
    },
    {
      title: 'Conversions', hint: 'Sign-ups aus unserer Datenbank',
      tiles: [
        tile('Sign-ups', num(c.registrations), d(c.registrations, p.registrations), 'nur Konten mit bekannter erster Seite'),
        tile('davon Organic Search', num(c.organicRegistrations), d(c.organicRegistrations, p.organicRegistrations), 'nur Konten mit bekannter erster Seite'),
        tile('Organic Conversion Rate', pct(organicCr(c.organicRegistrations, search(c))),
          pointDelta(organicCr(c.organicRegistrations, search(c)), organicCr(p.organicRegistrations, search(p)), ok)),
      ],
    },
  )
  return result
})

const tasks = computed<SeoTask[]>(() => opportunities.value?.tasks ?? [])

function openTask(t: SeoTask) {
  emit('go', t.type === 'STRIKING_DISTANCE'
    ? { sub: 'keywords', queryFilter: 'STRIKING_DISTANCE', search: t.key }
    : { sub: 'landingpages', pageFilter: 'UNDERRATED', search: t.key })
}

interface Finding { key: string; count: number; label: string; text: string; target: SeoNavigation }

const findings = computed<Finding[]>(() => {
  const o = opportunities.value
  if (!o) return []
  const f = (key: string, count: number, target: SeoNavigation): Finding =>
    ({ key, count, label: FINDINGS[key].label, text: FINDINGS[key].meaning, target })
  const list: Finding[] = [
    { key: 'tech', count: techErrors.value ?? 0, label: 'Seiten mit technischem Fehler', text: 'Google kann diese Seiten nicht einwandfrei crawlen oder indexieren. Zuerst beheben.', target: { sub: 'audit' } },
    f('LEAK', o.leak, { sub: 'landingpages', pageFilter: 'LEAK' }),
    f('UNDERRATED', o.underrated, { sub: 'landingpages', pageFilter: 'UNDERRATED' }),
    f('STRIKING_DISTANCE', o.striking, { sub: 'keywords', queryFilter: 'STRIKING_DISTANCE' }),
    f('CANNIBALIZED', o.cannibalized, { sub: 'keywords', queryFilter: 'CANNIBALIZED' }),
    f('ADWORDS', o.adwords, { sub: 'keywords', queryFilter: 'ADWORDS' }),
    f('CONTENT_GAP', o.contentGap, { sub: 'landingpages', modelFilter: 'CONTENT_GAP' }),
    f('INVISIBLE', o.invisible, { sub: 'landingpages', modelFilter: 'INVISIBLE' }),
    f('DEAD', o.dead, { sub: 'landingpages', pageFilter: 'DEAD' }),
    f('MONEY', o.money, { sub: 'landingpages', pageFilter: 'MONEY' }),
  ]
  return list.filter(a => a.count > 0)
})

const moved = computed(() => movers(pages.value.map(p => ({ key: p.path, clicks: p.clicks, clicksPrevious: p.clicksPrevious })), 5))
const showMovers = computed(() => !!overview.value?.previousComplete && (moved.value.winners.length > 0 || moved.value.losers.length > 0))

const chartData = computed(() => {
  const days = overview.value?.days ?? []
  return {
    labels: days.map(d => new Date(d.day).toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit' })),
    datasets: [
      { label: 'Impressionen', yAxisID: 'y', data: days.map(d => d.impressions), borderColor: 'rgb(129, 140, 248)', backgroundColor: 'rgba(129, 140, 248, 0.15)', fill: true, tension: 0.3, pointRadius: 2 },
      { label: 'Klicks', yAxisID: 'y1', data: days.map(d => d.clicks), borderColor: 'rgb(52, 211, 153)', backgroundColor: 'rgba(52, 211, 153, 0.1)', fill: false, tension: 0.3, pointRadius: 2 },
    ],
  }
})

const axisTitle = (text: string) => ({ display: true, text, color: '#9ca3af' })
const chartOptions = {
  responsive: true,
  maintainAspectRatio: false,
  interaction: { mode: 'index' as const, intersect: false },
  plugins: { legend: { labels: { color: '#9ca3af', boxWidth: 12 } } },
  scales: {
    x: { ticks: { color: '#9ca3af', maxTicksLimit: 12 }, grid: { color: 'rgba(55, 65, 81, 0.5)' } },
    y: { beginAtZero: true, position: 'left' as const, title: axisTitle('Impressionen'), ticks: { color: '#9ca3af' }, grid: { color: 'rgba(55, 65, 81, 0.5)' } },
    y1: { beginAtZero: true, position: 'right' as const, title: axisTitle('Klicks'), ticks: { color: '#9ca3af' }, grid: { drawOnChartArea: false } },
  },
}
const sign = (n: number) => `${n > 0 ? '+' : ''}${num(n)}`
</script>

<template>
  <div class="space-y-6" :class="{ 'opacity-60': loading }">
    <p v-if="error" class="text-red-400 text-sm">{{ error }}</p>
    <p v-else-if="loading && !overview" class="text-gray-400 text-sm">Lädt...</p>
    <template v-if="overview">
      <p v-if="!overview.previousComplete" class="text-xs text-amber-300">
        Für den Zeitraum davor fehlen Daten, deshalb gibt es keine Veränderung in Prozent. Wähle einen kürzeren Zeitraum für einen Vergleich.
      </p>

      <section v-if="tasks.length" class="edge-card rounded-xl bg-gray-700/50 p-4" aria-labelledby="seo-tasks">
        <h3 id="seo-tasks" class="text-sm font-semibold text-gray-100">Top-Aufgaben</h3>
        <p class="text-xs text-gray-400 mt-0.5 mb-3">Sortiert nach Klick-Potenzial: geschätzte zusätzliche Klicks in {{ overview.periodDays }} Tagen. Klick führt zur Zeile.</p>
        <ul class="divide-y divide-gray-800">
          <li v-for="t in tasks" :key="t.type + t.key">
            <button type="button" class="w-full flex items-center gap-3 py-2 text-left hover:bg-gray-800/40 focus:outline-none focus:ring-2 focus:ring-indigo-500 rounded-sm px-2"
                    @click="openTask(t)">
              <span class="w-20 shrink-0 text-right">
                <span class="block text-lg font-bold tabular-nums text-emerald-300">+{{ num(t.potentialClicks) }}</span>
                <span class="block text-xs text-gray-500">Klicks</span>
              </span>
              <span class="min-w-0">
                <span class="block text-sm font-medium text-gray-100">
                  {{ FINDINGS[t.type].label }}: <span :class="t.type === 'UNDERRATED' ? 'font-mono' : ''">{{ t.key }}</span>
                </span>
                <span class="block text-xs text-gray-400">
                  <template v-if="t.type === 'STRIKING_DISTANCE'">Landingpage <span class="font-mono">{{ t.path }}</span> auf das Keyword ausbauen, Ziel Seite 1.</template>
                  <template v-else>Title und Meta Description umschreiben, die CTR liegt weit unter dem Üblichen für die Position.</template>
                </span>
              </span>
              <ArrowRightIcon class="h-4 w-4 text-gray-500 ml-auto shrink-0" aria-hidden="true" />
            </button>
          </li>
        </ul>
      </section>

      <section v-for="g in groups" :key="g.title" :aria-label="g.title">
        <h3 class="text-sm font-semibold text-gray-200">{{ g.title }} <span class="font-normal text-xs text-gray-500">{{ g.hint }}</span></h3>
        <div class="grid grid-cols-2 md:grid-cols-4 gap-3 mt-2">
          <div v-for="t in g.tiles" :key="t.label" class="edge-card rounded-xl bg-gray-700/50 px-4 py-3">
            <div class="text-xl font-bold tabular-nums text-indigo-300">{{ t.value }}</div>
            <div class="text-xs text-gray-300 mt-1">{{ t.label }}</div>
            <div :class="['text-xs', t.up === null ? 'text-gray-500' : t.up ? 'text-emerald-400' : 'text-red-400']">{{ t.delta }}</div>
            <div v-if="t.note" class="text-xs text-gray-500">{{ t.note }}</div>
          </div>
        </div>
      </section>

      <section v-if="showMovers" aria-labelledby="seo-movers">
        <h3 id="seo-movers" class="text-sm font-semibold text-gray-200">Gewinner und Verlierer <span class="font-normal text-xs text-gray-500">Landingpages nach Klicks zum Vorzeitraum</span></h3>
        <div class="grid md:grid-cols-2 gap-3 mt-2">
          <div v-for="list in [{ title: 'Gewinner', rows: moved.winners, cls: 'text-emerald-300' }, { title: 'Verlierer', rows: moved.losers, cls: 'text-red-300' }]" :key="list.title"
               class="edge-card rounded-xl bg-gray-700/50 p-3">
            <h4 class="text-xs font-semibold uppercase tracking-wide text-gray-400 mb-1">{{ list.title }}</h4>
            <p v-if="!list.rows.length" class="text-xs text-gray-500">Keine Landingpage.</p>
            <ul class="space-y-1">
              <li v-for="m in list.rows" :key="m.key" class="flex items-baseline gap-2 text-xs">
                <a :href="m.key" target="_blank" rel="noopener" class="font-mono text-gray-200 hover:underline truncate">{{ m.key }}</a>
                <span :class="['ml-auto tabular-nums shrink-0 font-semibold', list.cls]">{{ sign(m.delta) }}</span>
                <span class="tabular-nums shrink-0 text-gray-500">{{ num(m.clicks) }} statt {{ num(m.clicksPrevious) }}</span>
              </li>
            </ul>
          </div>
        </div>
      </section>

      <section v-if="campaigns.length" aria-labelledby="seo-channels">
        <h3 id="seo-channels" class="text-sm font-semibold text-gray-200 mb-2">Channels <span class="font-normal text-xs text-gray-500">Welcher Kanal bringt Sign-ups? Details unter Akquisition.</span></h3>
        <SeoChannelTable :rows="campaigns" />
      </section>

      <details v-if="findings.length" class="edge-card rounded-xl bg-gray-700/50 p-4">
        <summary class="cursor-pointer text-sm font-semibold text-gray-200">Alle Befunde</summary>
        <ul class="divide-y divide-gray-800 mt-2">
          <li v-for="a in findings" :key="a.key">
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
      </details>

      <section class="edge-card rounded-xl bg-gray-700/50 p-4">
        <h3 class="text-sm font-semibold text-gray-200 mb-1">Verlauf je Tag</h3>
        <p class="text-xs text-gray-500 mb-2">{{ date(overview.from) }} bis {{ date(overview.to) }}, Impressionen und Klicks aus der Search Console.</p>
        <div class="h-64"><Line :data="chartData" :options="chartOptions" /></div>
      </section>
    </template>
  </div>
</template>
