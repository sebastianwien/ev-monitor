<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getSeoStatus, triggerSeoSync, type SeoBrandFilter, type SeoMarket, type SeoModelFilter, type SeoModelLevel, type SeoPageFilter, type SeoPeriod, type SeoQueryFilter, type SeoStatus } from '../../api/seoService'
import SeoHelpPanel from './seo/SeoHelpPanel.vue'
import SeoOverviewPanel from './seo/SeoOverviewPanel.vue'
import SeoPagesPanel from './seo/SeoPagesPanel.vue'
import SeoQueriesPanel from './seo/SeoQueriesPanel.vue'
import SeoCampaignsPanel from './seo/SeoCampaignsPanel.vue'
import SeoModelsPanel from './seo/SeoModelsPanel.vue'
import SeoTechPanel from './seo/SeoTechPanel.vue'
import { date, dateTime, num } from './seo/seoFormat'
import type { SeoNavigation, SeoSub } from './seo/seoNavigation'

/**
 * Admin-Tab "SEO & Traffic": Hülle mit Datenstand, Zeitraum, Markt und Unter-Tabs. Jede Ansicht lädt ihre Daten
 * selbst; Befundfilter liegen hier, damit der Überblick direkt in eine gefilterte Liste springen kann.
 */
const SUBS: { key: SeoSub; label: string }[] = [
  { key: 'overview', label: 'Überblick' },
  { key: 'pages', label: 'Seiten' },
  { key: 'queries', label: 'Suchbegriffe' },
  { key: 'campaigns', label: 'Kampagnen' },
  { key: 'models', label: 'Modellseiten' },
  { key: 'tech', label: 'Technik' },
]
const PERIODS: SeoPeriod[] = [7, 28, 90]
const MARKETS: { key: SeoMarket; label: string }[] = [
  { key: 'DE', label: 'Deutschland' },
  { key: 'INTL', label: 'International' },
  { key: 'ALL', label: 'Alle Märkte' },
]
const MARKET_SUBS: SeoSub[] = ['overview', 'pages', 'queries']
const PERIOD_SUBS: SeoSub[] = ['overview', 'pages', 'queries', 'campaigns', 'models']

const sub = ref<SeoSub>('overview')
const days = ref<SeoPeriod>(28)
const market = ref<SeoMarket>('DE')
const pageFilter = ref<SeoPageFilter>('ALL')
const queryFilter = ref<SeoQueryFilter>('ALL')
const brandFilter = ref<SeoBrandFilter>('ALL')
const modelFilter = ref<SeoModelFilter>('ALL')
const modelLevel = ref<SeoModelLevel>('MODEL')

const status = ref<SeoStatus | null>(null)
const statusError = ref('')
const syncMessage = ref('')

async function loadStatus() {
  try {
    status.value = await getSeoStatus()
  } catch {
    statusError.value = 'Der Datenstand konnte nicht geladen werden.'
  }
}

async function startSync() {
  syncMessage.value = ''
  try {
    const started = await triggerSeoSync(30)
    syncMessage.value = started
      ? 'Die letzten 30 Tage werden im Hintergrund neu abgeholt. Das dauert ein bis zwei Minuten.'
      : 'Es läuft gerade schon ein Abruf, oder keine Datenquelle ist verbunden.'
    await loadStatus()
  } catch {
    syncMessage.value = 'Der Abruf konnte nicht gestartet werden.'
  }
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
  }
  sub.value = target.sub
}

onMounted(loadStatus)

const gscRun = computed(() => status.value?.runs.find(r => r.source === 'GSC') ?? null)
const plausibleRun = computed(() => status.value?.runs.find(r => r.source === 'PLAUSIBLE') ?? null)
const RUN_LABELS: Record<string, string> = { RUNNING: 'läuft', SUCCESS: 'erfolgreich', PARTIAL: 'teilweise', FAILED: 'fehlgeschlagen' }
const syncDetails = computed(() => {
  const parts: string[] = ['Daten werden jede Nacht um 4:30 Uhr abgeholt.']
  if (gscRun.value) parts.push(`Google: zuletzt ${dateTime(gscRun.value.startedAt)}, ${RUN_LABELS[gscRun.value.status] ?? gscRun.value.status}, ${num(gscRun.value.rowCount)} Zeilen.`)
  if (plausibleRun.value) parts.push(`Plausible: zuletzt ${dateTime(plausibleRun.value.startedAt)}, ${RUN_LABELS[plausibleRun.value.status] ?? plausibleRun.value.status}, ${num(plausibleRun.value.rowCount)} Zeilen.`)
  return parts.join(' ')
})
const failedRun = computed(() => [gscRun.value, plausibleRun.value].find(r => r?.status === 'FAILED') ?? null)

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
        <p v-if="status" class="text-xs text-gray-400 mt-1" :title="syncDetails">
          Datenstand:
          <template v-if="status.enabled || status.dataUntil">Google bis {{ date(status.dataUntil) }}</template>
          <template v-if="status.plausibleDataUntil">, Besucher bis {{ date(status.plausibleDataUntil) }}</template>.
          <span class="text-gray-500">Google liefert mit 2 bis 3 Tagen Verzug.</span>
        </p>
        <p v-if="status && !status.enabled" class="text-xs text-amber-300 mt-1">Google Search Console ist nicht verbunden, neue Google-Daten kommen nicht an (Admin: GSC_SERVICE_ACCOUNT_JSON).</p>
        <p v-if="status && !status.plausibleEnabled" class="text-xs text-amber-300 mt-1">Plausible ist nicht verbunden, neue Besucherzahlen kommen nicht an (Admin: PLAUSIBLE_API_KEY).</p>
        <p v-if="failedRun" class="text-xs text-red-300 mt-1">Der letzte Abruf ist fehlgeschlagen: {{ failedRun.error ?? 'ohne Fehlermeldung' }}</p>
        <p v-if="statusError" class="text-xs text-red-300 mt-1">{{ statusError }}</p>
      </div>
      <div class="flex gap-3 items-center flex-wrap">
        <div v-if="MARKET_SUBS.includes(sub)" class="flex gap-1" role="group" aria-label="Markt">
          <button v-for="m in MARKETS" :key="m.key" type="button" :class="btn(market === m.key)" :aria-pressed="market === m.key"
                  :title="m.key === 'DE' ? 'Deutsche Website: alle Seiten ohne /en, /gb, /no, /se, /us' : m.key === 'INTL' ? 'Sprachvarianten, seit Oktober 2026 nicht mehr im Google-Index' : 'Alle Seiten'"
                  @click="market = m.key">{{ m.label }}</button>
        </div>
        <div v-if="PERIOD_SUBS.includes(sub)" class="flex gap-1" role="group" aria-label="Zeitraum">
          <button v-for="p in PERIODS" :key="p" type="button" :class="btn(days === p)" :aria-pressed="days === p" @click="days = p">{{ p }} Tage</button>
        </div>
        <button type="button" class="px-3 py-1.5 rounded-sm text-xs font-medium bg-gray-800 text-gray-300 hover:bg-gray-700 border border-gray-700 focus:outline-none focus:ring-2 focus:ring-indigo-500"
                :title="syncDetails" :disabled="status !== null && !status.enabled && !status.plausibleEnabled" @click="startSync">
          Daten jetzt abholen
        </button>
      </div>
    </div>
    <p v-if="syncMessage" class="text-xs text-gray-300" role="status">{{ syncMessage }}</p>

    <nav class="flex gap-1 flex-wrap" aria-label="Ansichten">
      <button v-for="s in SUBS" :key="s.key" type="button" :class="btn(sub === s.key)" :aria-current="sub === s.key ? 'page' : undefined" @click="sub = s.key">{{ s.label }}</button>
    </nav>

    <SeoHelpPanel :topic="sub" />

    <SeoOverviewPanel v-if="sub === 'overview'" :days="days" :market="market" @go="go" />
    <SeoPagesPanel v-else-if="sub === 'pages'" v-model:filter="pageFilter" :days="days" :market="market" @tech="sub = 'tech'" />
    <SeoQueriesPanel v-else-if="sub === 'queries'" v-model:filter="queryFilter" v-model:brand="brandFilter" :days="days" :market="market" />
    <SeoCampaignsPanel v-else-if="sub === 'campaigns'" :days="days" />
    <SeoModelsPanel v-else-if="sub === 'models'" v-model:filter="modelFilter" v-model:level="modelLevel" :days="days" />
    <SeoTechPanel v-else-if="sub === 'tech'" />
  </div>
</template>
