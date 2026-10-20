<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import {
  getSeoCompetition, triggerSeoCompetition,
  type SeoCompetitionView, type SeoCompetitorRow, type SeoIdeaRow, type SeoPeriod, type SeoSerpQueryRow,
} from '../../../api/seoService'
import SeoDataTable from './SeoDataTable.vue'
import { GLOSSARY } from './seoGlossary'
import { date, dateTime, num, pct } from './seoFormat'
import { competitionErrorHint, safeHref, usd, visibilityShare } from './seoCompetition'
import type { SeoColumn } from './seoTable'

const props = defineProps<{ days: SeoPeriod }>()

const view = ref<SeoCompetitionView | null>(null)
const loading = ref(false)
const error = ref('')
const runMessage = ref('')
const onlyNew = ref(true)

async function load() {
  loading.value = true
  error.value = ''
  try {
    view.value = await getSeoCompetition(props.days)
  } catch {
    error.value = 'Die Wettbewerbsdaten konnten nicht geladen werden.'
  } finally {
    loading.value = false
  }
}

async function startRun() {
  runMessage.value = ''
  try {
    runMessage.value = (await triggerSeoCompetition())
      ? 'Abruf läuft. Er holt nur, was fällig ist, und dauert ein bis zwei Minuten. Danach die Seite neu laden.'
      : 'Es läuft gerade schon ein Abruf, oder der Zugang ist nicht eingerichtet.'
  } catch {
    runMessage.value = 'Der Abruf konnte nicht gestartet werden.'
  }
}

watch(() => props.days, load)
onMounted(load)

const budgetShare = computed(() => (view.value && view.value.monthlyBudgetUsd > 0 ? view.value.costThisMonthUsd / view.value.monthlyBudgetUsd : 0))
const ideas = computed(() => (view.value?.ideas ?? []).filter(i => !onlyNew.value || !i.covered))
const hasData = computed(() => !!view.value && (view.value.queries.length > 0 || view.value.ideas.length > 0))

const competitorColumns: SeoColumn<SeoCompetitorRow>[] = [
  { key: 'domain', label: 'Website', value: r => r.domain },
  { key: 'aheadOfUs', label: 'Vor uns', hint: 'Bei so vielen unserer Begriffe steht die Website vor uns oder wir sind gar nicht in den Top 10', numeric: true, value: r => r.aheadOfUs },
  { key: 'queries', label: 'In den Top 10', hint: 'Bei so vielen unserer beobachteten Begriffe', numeric: true, value: r => r.queries },
  { key: 'avgRank', label: 'Ø Platz', numeric: true, value: r => r.avgRank, format: r => r.avgRank.toFixed(1).replace('.', ',') },
  { key: 'bestRank', label: 'Bester Platz', numeric: true, value: r => r.bestRank },
]

const queryColumns = computed<SeoColumn<SeoSerpQueryRow>[]>(() => [
  { key: 'query', label: 'Suchbegriff', value: r => r.query },
  { key: 'impressions', label: 'Impressionen', hint: `${GLOSSARY.impressions.text} Im gewählten Zeitraum, deutsche Website.`, numeric: true, value: r => r.impressions, format: r => num(r.impressions) },
  { key: 'ourRank', label: 'Unser Platz', hint: 'Platz unserer besten Seite unter den organischen Treffern, "-" = nicht in den Top 10', numeric: true, value: r => r.ourRank, format: r => (r.ourRank == null ? '-' : String(r.ourRank)) },
  { key: 'leader', label: 'Platz 1', value: r => r.top.find(t => t.rank === 1)?.domain ?? null },
  { key: 'volume', label: 'Suchvolumen', hint: GLOSSARY.volume.text, numeric: true, value: r => r.volume, format: r => (r.volume == null ? '-' : num(r.volume)) },
  { key: 'share', label: 'Sichtbarkeit', hint: GLOSSARY.share.text, numeric: true, value: r => visibilityShare(r.impressions, props.days, r.volume), format: r => { const s = visibilityShare(r.impressions, props.days, r.volume); return s == null ? '-' : pct(s, 0) }, csv: r => { const s = visibilityShare(r.impressions, props.days, r.volume); return s == null ? null : s * 100 } },
  { key: 'cpc', label: 'CPC', hint: GLOSSARY.cpc.text, numeric: true, value: r => r.cpc, format: r => usd(r.cpc) },
])

const ideaColumns: SeoColumn<SeoIdeaRow>[] = [
  { key: 'keyword', label: 'Begriffsidee', value: r => r.keyword },
  { key: 'volume', label: 'Suchvolumen', hint: GLOSSARY.volume.text, numeric: true, value: r => r.volume, format: r => (r.volume == null ? '-' : num(r.volume)) },
  { key: 'cpc', label: 'CPC', hint: GLOSSARY.cpc.text, numeric: true, value: r => r.cpc, format: r => usd(r.cpc) },
  { key: 'seed', label: 'Verwandt mit', value: r => r.seed },
  { key: 'covered', label: 'Bei uns', hint: 'Ja = in den letzten 90 Tagen erschien schon eine unserer deutschen Seiten für den Begriff', value: r => (r.covered ? 'ja' : 'nein') },
]
</script>

<template>
  <div class="space-y-6" :class="{ 'opacity-60': loading }">
    <div class="flex items-start justify-between gap-3 flex-wrap">
      <div class="text-xs text-gray-400 space-y-1">
        <p v-if="view?.serpWeek || view?.metricsMonth">
          Top 10 aus der Woche ab {{ date(view?.serpWeek) }}, Suchvolumen aus {{ view?.metricsMonth ? new Date(view.metricsMonth).toLocaleDateString('de-DE', { month: 'long', year: 'numeric' }) : '-' }}.
          Google Deutschland, Handy.
        </p>
        <p v-if="view?.enabled">
          Kosten diesen Monat: {{ usd(view.costThisMonthUsd) }} von {{ usd(view.monthlyBudgetUsd) }} Budget.
          <span v-if="budgetShare >= 0.9" class="text-amber-300">Das Budget ist fast aufgebraucht, weitere Abrufe folgen nächsten Monat.</span>
        </p>
      </div>
      <button v-if="view?.enabled" type="button" class="px-3 py-1.5 rounded-sm text-xs font-medium bg-gray-800 text-gray-300 hover:bg-gray-700 border border-gray-700 focus:outline-none focus:ring-2 focus:ring-indigo-500"
              @click="startRun">
        Fällige Daten jetzt abrufen
      </button>
    </div>
    <p v-if="runMessage" class="text-xs text-gray-300" role="status">{{ runMessage }}</p>
    <div v-if="view?.lastRun && (view.lastRun.status === 'FAILED' || view.lastRun.status === 'PARTIAL')"
         class="border-l-2 px-4 py-3 text-sm bg-gray-900" :class="view.lastRun.status === 'FAILED' ? 'border-red-500' : 'border-amber-500'">
      <p class="text-gray-200">
        Letzter Abruf am {{ dateTime(view.lastRun.startedAt) }} {{ view.lastRun.status === 'FAILED' ? 'ist fehlgeschlagen' : 'war unvollständig' }}.
        <template v-if="competitionErrorHint(view.lastRun.message)">{{ competitionErrorHint(view.lastRun.message) }}</template>
      </p>
      <p v-if="view.lastRun.message" class="text-xs text-gray-500 mt-1 break-all">Meldung: {{ view.lastRun.message }}</p>
    </div>
    <p v-if="error" class="text-red-400 text-sm">{{ error }}</p>
    <p v-else-if="loading && !view" class="text-gray-400 text-sm">Lädt...</p>

    <section v-else-if="view && !view.enabled" class="bg-gray-900 rounded-sm border border-gray-800 p-4 text-sm text-gray-300 space-y-2">
      <h3 class="font-semibold text-gray-100">Wettbewerbsdaten sind noch nicht eingerichtet</h3>
      <p>Die Daten kommen von DataForSEO, einem Anbieter, der Google-Suchergebnisse und Google-Ads-Kennzahlen gegen eine kleine Gebühr je Abruf liefert. Für unsere Menge sind das wenige Euro im Monat, begrenzt durch ein Monatsbudget.</p>
      <ol class="list-decimal pl-5 space-y-1">
        <li>Konto auf dataforseo.com anlegen und Guthaben aufladen.</li>
        <li>Unter "API Access" Login und API-Passwort kopieren.</li>
        <li>Admin: als DATAFORSEO_LOGIN und DATAFORSEO_PASSWORD in die Server-Umgebung eintragen, optional DATAFORSEO_MONTHLY_BUDGET_USD (Standard 5).</li>
      </ol>
      <p>Danach füllt sich diese Ansicht in der nächsten Nacht von selbst.</p>
    </section>

    <p v-else-if="view && !hasData" class="text-sm text-gray-400">Die ersten Wettbewerbsdaten kommen in der nächsten Nacht. Du kannst sie auch jetzt abrufen.</p>

    <template v-else-if="view">
      <section aria-labelledby="seo-competitors">
        <h3 id="seo-competitors" class="text-sm font-semibold text-gray-200">Wer steht vor uns?</h3>
        <p class="text-xs text-gray-500 mt-0.5 mb-2">Websites, die bei unseren wichtigsten Suchbegriffen in den Top 10 stehen. Oben die, die uns am häufigsten überholen.</p>
        <SeoDataTable :columns="competitorColumns" :rows="view.competitors" :row-key="r => r.domain" :search-keys="['domain']"
                      search-placeholder="Website suchen" csv-name="seo-wettbewerber" empty="Keine fremden Websites in den Top 10." />
      </section>

      <section aria-labelledby="seo-serp">
        <h3 id="seo-serp" class="text-sm font-semibold text-gray-200">Unsere Plätze je Suchbegriff</h3>
        <p class="text-xs text-gray-500 mt-0.5 mb-2">Klick auf eine Zeile zeigt die zehn ersten Treffer. Hohes Suchvolumen bei niedriger Sichtbarkeit ist die größte Chance.</p>
        <SeoDataTable :columns="queryColumns" :rows="view.queries" :row-key="r => r.query" :search-keys="['query', 'leader']"
                      search-placeholder="Begriff oder Website suchen" csv-name="seo-plaetze" expandable empty="Noch keine Suchergebnisse abgerufen.">
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
            <h3 id="seo-ideas" class="text-sm font-semibold text-gray-200">Begriffsideen</h3>
            <p class="text-xs text-gray-500 mt-0.5">Verwandte Suchen zu unseren wichtigsten Begriffen, mit Suchvolumen. Kandidaten für neue Inhalte oder Abschnitte.</p>
          </div>
          <label class="inline-flex items-center gap-2 text-xs text-gray-300">
            <input v-model="onlyNew" type="checkbox" class="rounded-sm bg-gray-900 border-gray-600 text-indigo-600 focus:ring-indigo-500" />
            Nur Begriffe, für die wir noch nicht erscheinen
          </label>
        </div>
        <div class="mt-2">
          <SeoDataTable :columns="ideaColumns" :rows="ideas" :row-key="r => r.keyword" :search-keys="['keyword', 'seed']"
                        search-placeholder="Idee suchen" csv-name="seo-begriffsideen" empty="Keine Begriffsideen." />
        </div>
      </section>
    </template>
  </div>
</template>
