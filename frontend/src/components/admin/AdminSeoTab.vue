<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getSeoStatus, type SeoBrandFilter, type SeoMarket, type SeoModelFilter, type SeoModelLevel, type SeoPageFilter, type SeoPeriod, type SeoQueryFilter, type SeoStatus } from '../../api/seoService'
import SeoHelpPanel from './seo/SeoHelpPanel.vue'
import SeoOverviewPanel from './seo/SeoOverviewPanel.vue'
import SeoPagesPanel from './seo/SeoPagesPanel.vue'
import SeoQueriesPanel from './seo/SeoQueriesPanel.vue'
import SeoCampaignsPanel from './seo/SeoCampaignsPanel.vue'
import SeoTechPanel from './seo/SeoTechPanel.vue'
import SeoCompetitionPanel from './seo/SeoCompetitionPanel.vue'
import SeoSourcesPanel from './seo/SeoSourcesPanel.vue'
import { date } from './seo/seoFormat'
import type { SeoNavigation, SeoSub } from './seo/seoNavigation'

/**
 * Admin-Tab "SEO & Traffic": Hülle mit Datenstand, Zeitraum und Ansichten nach SEO-Aufgabe (nicht nach Datenquelle).
 * Jede Ansicht lädt ihre Daten selbst; Befundfilter liegen hier, damit die Übersicht in eine gefilterte Liste springt.
 * Ausgewertet wird nur die deutsche Website, die Sprachvarianten sind seit Oktober 2026 nicht mehr im Index.
 */
const SUBS: { key: SeoSub; label: string }[] = [
  { key: 'overview', label: 'Übersicht' },
  { key: 'keywords', label: 'Keywords' },
  { key: 'landingpages', label: 'Landingpages' },
  { key: 'acquisition', label: 'Akquisition' },
  { key: 'audit', label: 'Site Audit' },
  { key: 'serp', label: 'SERP & Wettbewerb' },
]
const PERIODS: SeoPeriod[] = [7, 28, 90]
const PERIOD_SUBS: SeoSub[] = ['overview', 'keywords', 'landingpages', 'acquisition', 'serp']
const MARKET: SeoMarket = 'DE'

const sub = ref<SeoSub>('overview')
const days = ref<SeoPeriod>(28)
const pageFilter = ref<SeoPageFilter>('ALL')
const queryFilter = ref<SeoQueryFilter>('ALL')
const brandFilter = ref<SeoBrandFilter>('ALL')
const modelFilter = ref<SeoModelFilter>('ALL')
const modelLevel = ref<SeoModelLevel>('MODEL')
const landingView = ref<'pages' | 'models'>('pages')
/** Suchbegriff aus einem Sprung; navKey erzwingt ein frisches Panel, damit die Suche greift. */
const search = ref('')
const navKey = ref(0)

const status = ref<SeoStatus | null>(null)
const statusError = ref('')

async function loadStatus() {
  try {
    status.value = await getSeoStatus()
  } catch {
    statusError.value = 'Der Datenstand konnte nicht geladen werden.'
  }
}

function select(next: SeoSub) {
  search.value = ''
  sub.value = next
}

function go(target: SeoNavigation) {
  if (target.pageFilter) pageFilter.value = target.pageFilter
  if (target.queryFilter) {
    queryFilter.value = target.queryFilter
    brandFilter.value = 'ALL'
  }
  if (target.modelFilter) {
    modelFilter.value = target.modelFilter
    modelLevel.value = 'MODEL'
    landingView.value = 'models'
  } else if (target.sub === 'landingpages') {
    landingView.value = 'pages'
  }
  search.value = target.search ?? ''
  navKey.value++
  sub.value = target.sub
}

onMounted(loadStatus)

const failed = computed(() => status.value?.runs.filter(r => r.status === 'FAILED' && (r.source === 'GSC' || r.source === 'PLAUSIBLE')) ?? [])

const btn = (active: boolean) => [
  'px-3 py-1.5 rounded-sm text-xs font-medium transition focus:outline-none focus:ring-2 focus:ring-indigo-500',
  active ? 'bg-indigo-600 text-white' : 'bg-gray-800 text-gray-300 hover:bg-gray-700',
]
</script>

<template>
  <div class="space-y-4">
    <div class="flex items-start justify-between gap-3 flex-wrap">
      <div>
        <h2 class="text-lg font-semibold text-white">SEO &amp; Traffic</h2>
        <p v-if="status" class="text-xs text-gray-400 mt-1">
          Deutsche Website. Datenstand: Search Console bis {{ date(status.dataUntil) }}<template v-if="status.plausibleDataUntil">, Plausible bis {{ date(status.plausibleDataUntil) }}</template>.
        </p>
        <p v-if="failed.length" class="text-xs text-red-300 mt-1">
          Der letzte Abruf von {{ failed.map(r => r.source === 'GSC' ? 'Search Console' : 'Plausible').join(' und ') }} ist fehlgeschlagen, die Zahlen sind womöglich nicht aktuell. Details unter "Datenquellen und Abrufe".
        </p>
        <p v-if="statusError" class="text-xs text-red-300 mt-1">{{ statusError }}</p>
      </div>
      <div v-if="PERIOD_SUBS.includes(sub)" class="flex gap-1" role="group" aria-label="Zeitraum">
        <button v-for="p in PERIODS" :key="p" type="button" :class="btn(days === p)" :aria-pressed="days === p" @click="days = p">{{ p }} Tage</button>
      </div>
    </div>

    <nav class="flex gap-1 flex-wrap" aria-label="Ansichten">
      <button v-for="s in SUBS" :key="s.key" type="button" :class="btn(sub === s.key)" :aria-current="sub === s.key ? 'page' : undefined" @click="select(s.key)">{{ s.label }}</button>
    </nav>

    <SeoHelpPanel :topic="sub" />

    <SeoOverviewPanel v-if="sub === 'overview'" :days="days" :market="MARKET" @go="go" />
    <SeoQueriesPanel v-else-if="sub === 'keywords'" :key="`k${navKey}`" v-model:filter="queryFilter" v-model:brand="brandFilter" :days="days" :market="MARKET" :search="search" />
    <SeoPagesPanel v-else-if="sub === 'landingpages'" :key="`l${navKey}`" v-model:filter="pageFilter" v-model:model-filter="modelFilter" v-model:model-level="modelLevel"
                   v-model:view="landingView" :days="days" :market="MARKET" :search="search" @audit="select('audit')" />
    <SeoCampaignsPanel v-else-if="sub === 'acquisition'" :days="days" />
    <SeoTechPanel v-else-if="sub === 'audit'" />
    <SeoCompetitionPanel v-else-if="sub === 'serp'" :days="days" />

    <SeoSourcesPanel :status="status" @synced="loadStatus" />
  </div>
</template>
