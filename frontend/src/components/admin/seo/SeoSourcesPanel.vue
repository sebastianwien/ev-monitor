<script setup lang="ts">
import { computed, ref } from 'vue'
import { ArrowTopRightOnSquareIcon } from '@heroicons/vue/24/outline'
import {
  getSeoAudit, getSeoCompetition, triggerSeoAudit, triggerSeoCompetition, triggerSeoSync,
  type SeoAuditView, type SeoCompetitionView, type SeoStatus, type SeoSyncRun,
} from '../../../api/seoService'
import { competitionErrorHint, usd } from './seoCompetition'
import { date, dateTime, num } from './seoFormat'

/**
 * Datenquellen und Abrufe: Datenstand je Quelle, Links zu den Original-Tools, manuelle Abrufe und Einrichtung.
 * Eingeklappt am Ende des Tabs, weil Marketing damit im Alltag nichts zu tun hat. Lädt Crawl- und SERP-Stand erst beim Öffnen.
 */
const props = defineProps<{ status: SeoStatus | null }>()
const emit = defineEmits<{ synced: [] }>()

const GSC_URL = 'https://search.google.com/search-console/performance/search-analytics?resource_id=sc-domain%3Aev-monitor.net'
const PLAUSIBLE_URL = 'https://plausible.io/ev-monitor.net'
const RUN_LABELS: Record<string, string> = { RUNNING: 'läuft', SUCCESS: 'erfolgreich', PARTIAL: 'teilweise', FAILED: 'fehlgeschlagen' }

const audit = ref<SeoAuditView | null>(null)
const competition = ref<SeoCompetitionView | null>(null)
const message = ref('')
let loaded = false

async function onToggle(e: Event) {
  if (!(e.target as HTMLDetailsElement).open || loaded) return
  loaded = true
  const [a, c] = await Promise.allSettled([getSeoAudit(), getSeoCompetition(28)])
  if (a.status === 'fulfilled') audit.value = a.value
  if (c.status === 'fulfilled') competition.value = c.value
}

const run = (source: string) => computed<SeoSyncRun | null>(() => props.status?.runs.find(r => r.source === source) ?? null)
const gscRun = run('GSC')
const plausibleRun = run('PLAUSIBLE')
const runText = (r: SeoSyncRun | null) => (r ? `Letzter Abruf ${dateTime(r.startedAt)}, ${RUN_LABELS[r.status] ?? r.status}, ${num(r.rowCount)} Zeilen.` : 'Noch kein Abruf.')

async function start(kind: 'sync' | 'audit' | 'serp') {
  message.value = ''
  try {
    if (kind === 'sync') {
      message.value = (await triggerSeoSync(30))
        ? 'Search Console und Plausible: die letzten 30 Tage werden neu abgerufen, das dauert ein bis zwei Minuten.'
        : 'Es läuft schon ein Abruf, oder keine Quelle ist verbunden.'
      emit('synced')
    } else if (kind === 'audit') {
      message.value = (await triggerSeoAudit())
        ? 'Crawl läuft, etwa 3 Minuten. Danach die Seite neu laden.'
        : 'Es läuft schon ein Crawl, oder er ist nicht eingerichtet.'
    } else {
      message.value = (await triggerSeoCompetition())
        ? 'SERP-Abruf läuft. Er holt nur, was fällig ist, und dauert ein bis zwei Minuten.'
        : 'Es läuft schon ein Abruf, oder der Zugang ist nicht eingerichtet.'
    }
  } catch {
    message.value = 'Der Abruf konnte nicht gestartet werden.'
  }
}

const btn = 'px-3 py-1.5 rounded-sm text-xs font-medium bg-gray-800 text-gray-300 hover:bg-gray-700 border border-gray-700 focus:outline-none focus:ring-2 focus:ring-indigo-500 disabled:opacity-50'
const link = 'inline-flex items-center gap-1 text-xs text-indigo-300 hover:text-indigo-200 hover:underline'
</script>

<template>
  <details class="edge-card rounded-xl bg-gray-700/50 p-4" @toggle="onToggle">
    <summary class="cursor-pointer text-sm font-semibold text-gray-200">Datenquellen und Abrufe</summary>
    <p class="text-xs text-gray-400 mt-2">Alle Daten werden jede Nacht automatisch abgerufen. Die Originaldaten liegen in den verlinkten Tools.</p>
    <p v-if="message" class="text-xs text-gray-300 mt-2" role="status">{{ message }}</p>

    <div class="grid md:grid-cols-2 gap-4 mt-3 text-xs text-gray-300">
      <section class="space-y-1" aria-label="Google Search Console">
        <h4 class="font-semibold text-gray-100">Google Search Console (GSC)</h4>
        <p>Impressionen, Klicks, CTR, Position, Keywords. Datenstand {{ date(status?.dataUntil) }}, Google liefert mit 2 bis 3 Tagen Verzug. Abruf nachts um 4:30 Uhr.</p>
        <p>{{ runText(gscRun) }} <span v-if="gscRun?.error" class="text-red-300">{{ gscRun.error }}</span></p>
        <p v-if="status && !status.enabled" class="text-amber-300">Nicht verbunden. Admin: GSC_SERVICE_ACCOUNT_JSON setzen.</p>
        <a :href="GSC_URL" target="_blank" rel="noopener noreferrer" :class="link">In der Search Console öffnen <ArrowTopRightOnSquareIcon class="h-3.5 w-3.5" aria-hidden="true" /></a>
      </section>

      <section class="space-y-1" aria-label="Plausible">
        <h4 class="font-semibold text-gray-100">Plausible</h4>
        <p>Besucher, Einstiege, Bounce Rate, Conversion-Goals, Quellen und UTM. Datenstand {{ date(status?.plausibleDataUntil) }}.</p>
        <p>{{ runText(plausibleRun) }} <span v-if="plausibleRun?.error" class="text-red-300">{{ plausibleRun.error }}</span></p>
        <p v-if="status && !status.plausibleEnabled" class="text-amber-300">Nicht verbunden. Admin: PLAUSIBLE_API_KEY setzen.</p>
        <a :href="PLAUSIBLE_URL" target="_blank" rel="noopener noreferrer" :class="link">In Plausible öffnen <ArrowTopRightOnSquareIcon class="h-3.5 w-3.5" aria-hidden="true" /></a>
      </section>

      <section class="space-y-1" aria-label="Datenbank">
        <h4 class="font-semibold text-gray-100">Datenbank (DB)</h4>
        <p>Sign-ups und Trials aus unseren Konten, zugeordnet über die erste Seite und UTM-Parameter. Immer aktuell.</p>
        <button type="button" :class="btn" :disabled="status !== null && !status.enabled && !status.plausibleEnabled" @click="start('sync')">
          GSC und Plausible jetzt abrufen
        </button>
      </section>

      <section class="space-y-1" aria-label="Site Audit">
        <h4 class="font-semibold text-gray-100">Site Audit (Crawl)</h4>
        <p>Crawlt nachts um 5 Uhr alle Seiten der Sitemap wie Googlebot.
          <template v-if="audit?.day">Letzter Crawl {{ dateTime(audit.lastRunAt) }}.</template></p>
        <p v-if="audit && !audit.enabled" class="text-amber-300">Nicht eingerichtet. Admin: SEO_AUDIT_BASE_URL setzen.</p>
        <button type="button" :class="btn" :disabled="audit !== null && !audit.enabled" @click="start('audit')">Crawl jetzt starten</button>
      </section>

      <section class="space-y-1 md:col-span-2" aria-label="DataForSEO">
        <h4 class="font-semibold text-gray-100">DataForSEO</h4>
        <p>SERP Top 10, Suchvolumen, CPC und Keyword-Ideen. Kostenpflichtig je Abruf, begrenzt durch ein Monatsbudget.</p>
        <template v-if="competition?.enabled">
          <p>Kosten diesen Monat: {{ usd(competition.costThisMonthUsd) }} von {{ usd(competition.monthlyBudgetUsd) }} Budget.</p>
          <p v-if="competition.lastRun && (competition.lastRun.status === 'FAILED' || competition.lastRun.status === 'PARTIAL')" class="text-red-300">
            Letzter Abruf am {{ dateTime(competition.lastRun.startedAt) }} {{ competition.lastRun.status === 'FAILED' ? 'ist fehlgeschlagen' : 'war unvollständig' }}.
            {{ competitionErrorHint(competition.lastRun.message) }}
            <span class="block text-gray-500 break-all">Meldung: {{ competition.lastRun.message }}</span>
          </p>
          <button type="button" :class="btn" @click="start('serp')">Fällige SERP-Daten abrufen</button>
        </template>
        <ol v-else-if="competition" class="list-decimal pl-5 space-y-0.5">
          <li>Konto auf dataforseo.com anlegen und Guthaben aufladen.</li>
          <li>Unter "API Access" Login und API-Passwort kopieren.</li>
          <li>Admin: als DATAFORSEO_LOGIN und DATAFORSEO_PASSWORD in die Server-Umgebung eintragen, optional DATAFORSEO_MONTHLY_BUDGET_USD (Standard 5).</li>
        </ol>
      </section>
    </div>
  </details>
</template>
