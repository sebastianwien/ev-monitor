<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import {
  getSeoCompetition,
  type SeoCompetitionView, type SeoCompetitorRow, type SeoIdeaRow, type SeoPeriod, type SeoSerpQueryRow,
} from '../../../api/seoService'
import SeoDataTable from './SeoDataTable.vue'
import { GLOSSARY } from './seoGlossary'
import { date, num, pct } from './seoFormat'
import { safeHref, usd, visibilityShare } from './seoCompetition'
import type { SeoColumn } from './seoTable'

/** SERP & Wettbewerb aus DataForSEO: Top 10 je Keyword, Keyword-Ideen, Domains. Abruf und Einrichtung unter "Datenquellen". */
const props = defineProps<{ days: SeoPeriod }>()

const view = ref<SeoCompetitionView | null>(null)
const loading = ref(false)
const error = ref('')
const onlyNew = ref(true)

async function load() {
  loading.value = true
  error.value = ''
  try {
    view.value = await getSeoCompetition(props.days)
  } catch {
    error.value = 'Die SERP-Daten konnten nicht geladen werden.'
  } finally {
    loading.value = false
  }
}

watch(() => props.days, load)
onMounted(load)

const ideas = computed(() => (view.value?.ideas ?? []).filter(i => !onlyNew.value || !i.covered))
const hasData = computed(() => !!view.value && (view.value.queries.length > 0 || view.value.ideas.length > 0))
const lastFailed = computed(() => view.value?.lastRun?.status === 'FAILED' || view.value?.lastRun?.status === 'PARTIAL')

const competitorColumns: SeoColumn<SeoCompetitorRow>[] = [
  { key: 'domain', label: 'Domain', value: r => r.domain },
  { key: 'aheadOfUs', label: 'Vor uns', hint: 'Bei so vielen unserer Keywords rankt die Domain vor uns oder wir sind gar nicht in den Top 10', numeric: true, value: r => r.aheadOfUs },
  { key: 'queries', label: 'Keywords in Top 10', hint: 'Bei so vielen unserer beobachteten Keywords', numeric: true, value: r => r.queries },
  { key: 'avgRank', label: 'Ø Ranking', numeric: true, value: r => r.avgRank, format: r => r.avgRank.toFixed(1).replace('.', ',') },
  { key: 'bestRank', label: 'Bestes Ranking', numeric: true, value: r => r.bestRank },
]

const queryColumns = computed<SeoColumn<SeoSerpQueryRow>[]>(() => [
  { key: 'query', label: 'Keyword', value: r => r.query },
  { key: 'impressions', label: 'Impressionen', hint: `${GLOSSARY.impressions.text} Im gewählten Zeitraum, deutsche Website.`, source: 'GSC', numeric: true, value: r => r.impressions, format: r => num(r.impressions) },
  { key: 'ourRank', label: 'Unser Ranking', hint: 'Platz unserer besten Seite unter den organischen Treffern, "-" = nicht in den Top 10', source: 'DataForSEO', numeric: true, value: r => r.ourRank, format: r => (r.ourRank == null ? '-' : String(r.ourRank)) },
  { key: 'leader', label: 'Platz 1', source: 'DataForSEO', value: r => r.top.find(t => t.rank === 1)?.domain ?? null },
  { key: 'volume', label: 'Suchvolumen', hint: GLOSSARY.volume.text, source: 'DataForSEO', numeric: true, value: r => r.volume, format: r => (r.volume == null ? '-' : num(r.volume)) },
  { key: 'share', label: 'Impression Share', hint: GLOSSARY.share.text, numeric: true, value: r => visibilityShare(r.impressions, props.days, r.volume), format: r => { const s = visibilityShare(r.impressions, props.days, r.volume); return s == null ? '-' : pct(s, 0) }, csv: r => { const s = visibilityShare(r.impressions, props.days, r.volume); return s == null ? null : s * 100 } },
  { key: 'cpc', label: 'CPC', hint: GLOSSARY.cpc.text, source: 'DataForSEO', numeric: true, value: r => r.cpc, format: r => usd(r.cpc) },
])

const ideaColumns: SeoColumn<SeoIdeaRow>[] = [
  { key: 'keyword', label: 'Keyword-Idee', value: r => r.keyword },
  { key: 'volume', label: 'Suchvolumen', hint: GLOSSARY.volume.text, source: 'DataForSEO', numeric: true, value: r => r.volume, format: r => (r.volume == null ? '-' : num(r.volume)) },
  { key: 'cpc', label: 'CPC', hint: GLOSSARY.cpc.text, source: 'DataForSEO', numeric: true, value: r => r.cpc, format: r => usd(r.cpc) },
  { key: 'seed', label: 'Verwandt mit', value: r => r.seed },
  { key: 'covered', label: 'Wir ranken', hint: 'Ja = in den letzten 90 Tagen erschien schon eine unserer deutschen Seiten für das Keyword', value: r => (r.covered ? 'ja' : 'nein') },
]
</script>

<template>
  <div class="space-y-6" :class="{ 'opacity-60': loading }">
    <p v-if="view?.serpWeek || view?.metricsMonth" class="text-xs text-gray-400">
      SERP Top 10 aus der Woche ab {{ date(view?.serpWeek) }}, Suchvolumen aus {{ view?.metricsMonth ? new Date(view.metricsMonth).toLocaleDateString('de-DE', { month: 'long', year: 'numeric' }) : '-' }}.
      Google Deutschland, Handy.
    </p>
    <p v-if="lastFailed" class="text-xs text-red-300">Der letzte SERP-Abruf ist fehlgeschlagen, die Daten sind womöglich nicht aktuell. Details unter "Datenquellen und Abrufe".</p>
    <p v-if="error" class="text-red-400 text-sm">{{ error }}</p>
    <p v-else-if="loading && !view" class="text-gray-400 text-sm">Lädt...</p>

    <p v-else-if="view && !view.enabled" class="text-sm text-gray-300">
      Für SERP-Daten, Suchvolumen und CPC ist DataForSEO nicht angebunden. Wie das geht, steht unter "Datenquellen und Abrufe".
    </p>

    <p v-else-if="view && !hasData" class="text-sm text-gray-400">Die ersten SERP-Daten kommen in der nächsten Nacht.</p>

    <template v-else-if="view">
      <section aria-labelledby="seo-serp">
        <h3 id="seo-serp" class="text-sm font-semibold text-gray-200">SERP je Keyword</h3>
        <p class="text-xs text-gray-500 mt-0.5 mb-2">Klick auf eine Zeile zeigt die organischen Top 10. Hohes Suchvolumen bei niedrigem Impression Share ist die größte Chance.</p>
        <SeoDataTable :columns="queryColumns" :rows="view.queries" :row-key="r => r.query" :search-keys="['query', 'leader']"
                      search-placeholder="Keyword oder Domain suchen" csv-name="seo-serp" expandable empty="Noch keine SERP abgerufen.">
          <template #cell-ourRank="{ row }">
            <span :class="row.ourRank == null ? 'text-red-300' : row.ourRank <= 3 ? 'text-emerald-300' : 'text-gray-300'">{{ row.ourRank ?? '-' }}</span>
          </template>
          <template #expand="{ row }">
            <ol class="text-xs space-y-1 max-w-3xl">
              <li v-for="t in row.top" :key="t.rank" class="flex gap-2" :class="t.own ? 'text-emerald-300' : 'text-gray-300'">
                <span class="w-6 text-right tabular-nums text-gray-500">{{ t.rank }}.</span>
                <span class="w-48 shrink-0 truncate">{{ t.domain }}</span>
                <a :href="safeHref(t.url)" target="_blank" rel="noopener noreferrer" class="hover:underline truncate">{{ t.title || t.url }}</a>
              </li>
            </ol>
          </template>
        </SeoDataTable>
      </section>

      <section aria-labelledby="seo-ideas">
        <div class="flex items-end justify-between gap-3 flex-wrap">
          <div>
            <h3 id="seo-ideas" class="text-sm font-semibold text-gray-200">Keyword-Ideen</h3>
            <p class="text-xs text-gray-500 mt-0.5">Verwandte Suchen zu unseren wichtigsten Keywords, mit Suchvolumen. Kandidaten für neue Inhalte oder Abschnitte.</p>
          </div>
          <label class="inline-flex items-center gap-2 text-xs text-gray-300">
            <input v-model="onlyNew" type="checkbox" class="rounded-sm bg-gray-900 border-gray-600 text-indigo-600 focus:ring-indigo-500" />
            Nur Keywords, für die wir noch nicht ranken
          </label>
        </div>
        <div class="mt-2">
          <SeoDataTable :columns="ideaColumns" :rows="ideas" :row-key="r => r.keyword" :search-keys="['keyword', 'seed']"
                        search-placeholder="Idee suchen" csv-name="seo-keyword-ideen" empty="Keine Keyword-Ideen." />
        </div>
      </section>

      <details class="bg-gray-900 rounded-sm border border-gray-800 p-4">
        <summary class="cursor-pointer text-sm font-semibold text-gray-200">Domains in den Top 10</summary>
        <p class="text-xs text-gray-500 mt-2 mb-2">Alle fremden Domains bei unseren beobachteten Keywords. Oben die, die uns am häufigsten überholen. Plattformen wie YouTube oder Wikipedia sind kaum angreifbar, aussagekräftiger ist die SERP je Keyword oben.</p>
        <SeoDataTable :columns="competitorColumns" :rows="view.competitors" :row-key="r => r.domain" :search-keys="['domain']"
                      search-placeholder="Domain suchen" csv-name="seo-domains" empty="Keine fremden Domains in den Top 10." />
      </details>
    </template>
  </div>
</template>
