<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { adminTariffCrawlService, type CrawlOverview, type CrawlProposal, type CrawlSource, type DriverDeviation } from '../../api/adminChargingTariffService'
import { apiErrorText } from '../../api/adminVehicleSpecService'

/**
 * Tarif-Crawler im Admin-Tab "Ladekarten": welche Seiten er liest, wie der letzte Lauf ausging,
 * und die Vorschläge, die ein Admin entscheiden muss. Mit {@code autoApply} übernimmt der Crawler
 * Preisänderungen ohne Zweifel selbst, sobald ein zweiter Lauf sie bestätigt; sonst landet jede hier.
 */
const emit = defineEmits<{ changed: [] }>()

const data = ref<CrawlOverview | null>(null)
const hints = ref<DriverDeviation[]>([])
const message = ref('')
const error = ref('')
const busy = ref<string | null>(null)
const adding = ref<{ empName: string; url: string; mode: 'EXTRACT' | 'WATCH' } | null>(null)

async function load() {
  try {
    const [overview, deviations] = await Promise.all([
      adminTariffCrawlService.overview(),
      adminTariffCrawlService.driverCheck().catch(() => []),
    ])
    data.value = overview
    hints.value = deviations
  } catch (e) {
    error.value = apiErrorText(e, 'Crawler-Daten konnten nicht geladen werden.')
  }
}
onMounted(load)

const STATUS: Record<string, string> = {
  OK: 'gelesen, stimmt',
  UNCHANGED: 'unverändert',
  CHANGED: 'geändert',
  HTTP_ERROR: 'nicht erreichbar',
  ROBOTS: 'robots.txt sperrt',
  UNREADABLE: 'keine Preise gefunden',
  EXTRACT_ERROR: 'Sprachmodell-Fehler',
}
const failing = (s: CrawlSource) => s.consecutiveFailures > 0
const de = (iso: string | null) => iso
  ? new Date(iso).toLocaleString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' })
  : 'noch nie'
function statusText(s: CrawlSource): string {
  if (!s.lastStatus) return 'noch nicht gelesen'
  const base = STATUS[s.lastStatus] ?? s.lastStatus
  const detail = s.lastError ? `: ${s.lastError}` : ''
  const streak = s.consecutiveFailures > 1 ? `, ${s.consecutiveFailures} Läufe in Folge` : ''
  return `${base}${detail}${streak}`
}

async function decide(p: CrawlProposal, how: 'apply' | 'reject') {
  busy.value = p.id
  error.value = ''
  message.value = ''
  try {
    if (how === 'apply') {
      const applied = await adminTariffCrawlService.apply(p.id)
      if (!applied) message.value = 'Der Tarif wurde inzwischen anders gepflegt, der Vorschlag ist überholt.'
      emit('changed')
    } else {
      await adminTariffCrawlService.reject(p.id)
    }
    await load()
  } catch (e) {
    error.value = apiErrorText(e, 'Das hat nicht geklappt.')
  } finally {
    busy.value = null
  }
}

async function runNow() {
  error.value = ''
  try {
    message.value = (await adminTariffCrawlService.run())
      ? 'Prüfung läuft im Hintergrund. In ein bis zwei Minuten neu laden.'
      : 'Es läuft schon eine Prüfung.'
  } catch (e) {
    error.value = apiErrorText(e, 'Start hat nicht geklappt.')
  }
}

async function toggle(s: CrawlSource) {
  try {
    await adminTariffCrawlService.updateSource(s.id, { empName: s.empName, url: s.url, mode: s.mode, enabled: !s.enabled })
    await load()
  } catch (e) {
    error.value = apiErrorText(e, 'Ändern hat nicht geklappt.')
  }
}

async function addSource() {
  if (!adding.value) return
  try {
    await adminTariffCrawlService.createSource({ ...adding.value, enabled: true })
    adding.value = null
    await load()
  } catch (e) {
    error.value = apiErrorText(e, 'Quelle konnte nicht angelegt werden.')
  }
}

const price = (v: number) => `${v.toLocaleString('de-DE', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} €`

const field = 'bg-gray-900 border border-gray-700 rounded-sm px-2 py-1.5 text-sm text-gray-100 focus:outline-none focus:border-indigo-500'
</script>

<template>
  <section class="mb-8">
    <div class="flex flex-wrap items-baseline justify-between gap-3 mb-3">
      <div>
        <h3 class="text-base font-semibold text-white">Automatische Prüfung</h3>
        <p class="text-sm text-gray-400 max-w-2xl">
          Täglich 05:40 liest der Crawler fällige Seiten. Unveränderte Preise bekommen den Stand von heute.
          <template v-if="data?.autoApply">Eindeutige Preisänderungen übernimmt er nach einer zweiten Bestätigung selbst. Unten steht nur, was eine Entscheidung braucht.</template>
          <template v-else>Jede Preisänderung steht unten mit Zitat und bisherigem Wert zur Entscheidung.</template>
        </p>
      </div>
      <button type="button" class="px-4 py-2 rounded-sm border border-gray-700 text-gray-200 text-sm hover:bg-gray-800" @click="runNow">Jetzt alle Seiten prüfen</button>
    </div>

    <div v-if="data && !data.extractorAvailable" class="mb-3 p-3 bg-amber-900/30 border border-amber-700 text-amber-200 text-sm rounded-sm">
      Kein Sprachmodell konfiguriert (ANTHROPIC_API_KEY oder MISTRAL_API_KEY). Nur Seiten mit Preisbildern werden auf Änderungen geprüft.
    </div>
    <div v-if="data && !data.enabled" class="mb-3 p-3 bg-amber-900/30 border border-amber-700 text-amber-200 text-sm rounded-sm">
      Die nächtliche Prüfung ist abgeschaltet (TARIFF_CRAWL_ENABLED). Der Knopf funktioniert trotzdem.
    </div>
    <div v-if="message" class="mb-3 p-3 bg-gray-800 border border-gray-700 text-gray-200 text-sm rounded-sm">{{ message }}</div>
    <div v-if="error" class="mb-3 p-3 bg-red-900/40 border border-red-700 text-red-300 text-sm rounded-sm">{{ error }}</div>

    <template v-if="data">
      <h4 class="text-xs uppercase tracking-wider text-gray-400 mb-2">Zu entscheiden ({{ data.proposals.length }})</h4>
      <p v-if="!data.proposals.length" class="text-sm text-gray-500 mb-4">Nichts offen.</p>
      <ul v-else class="mb-6 divide-y divide-gray-800 border border-gray-800 rounded-sm">
        <li v-for="p in data.proposals" :key="p.id" data-testid="crawl-proposal" class="p-3 flex flex-wrap gap-3 items-start justify-between">
          <div class="min-w-0 max-w-3xl">
            <p class="text-sm text-gray-100">{{ p.description }}</p>
            <p v-if="p.current" class="text-xs text-gray-400 mt-1">
              Bisher {{ p.current.pricePerKwh.toLocaleString('de-DE', { minimumFractionDigits: 2 }) }} €/kWh,
              Grundgebühr {{ p.current.monthlyFeeEur.toLocaleString('de-DE', { minimumFractionDigits: 2 }) }} €
            </p>
            <p v-if="p.evidence" class="text-xs text-gray-400 mt-1 italic">Seite: „{{ p.evidence }}“</p>
            <p class="text-xs text-gray-500 mt-1">{{ p.seenCount }}× gesehen, zuletzt {{ de(p.lastSeenAt) }}</p>
          </div>
          <div class="flex gap-2 shrink-0">
            <button type="button" :disabled="busy === p.id" class="px-3 py-1.5 rounded-sm bg-indigo-600 text-white text-sm hover:bg-indigo-500 disabled:opacity-50" @click="decide(p, 'apply')">Übernehmen</button>
            <button type="button" :disabled="busy === p.id" class="px-3 py-1.5 rounded-sm border border-gray-700 text-gray-300 text-sm hover:bg-gray-800 disabled:opacity-50" @click="decide(p, 'reject')">Verwerfen</button>
          </div>
        </li>
      </ul>

      <template v-if="hints.length">
        <h4 class="text-xs uppercase tracking-wider text-gray-400 mb-2">Fahrer zahlen anders ({{ hints.length }})</h4>
        <p class="text-xs text-gray-500 mb-2">Mehr als 10 % Abstand zur nächsten gepflegten Variante. Nur ein Hinweis: Fahrer tragen die Variante nicht ein.</p>
        <ul class="mb-6 divide-y divide-gray-800 border border-gray-800 rounded-sm">
          <li v-for="d in hints" :key="`${d.empName}|${d.operator}|${d.chargingType}`" data-testid="driver-hint" class="p-3 text-sm text-gray-200">
            {{ d.empName }} {{ d.nearestVariant ?? '' }} an {{ d.operator }} {{ d.chargingType }}: gepflegt {{ price(d.curatedPricePerKwh) }},
            Fahrer zahlen {{ price(d.driverMedianPricePerKwh) }} ({{ d.drivers }} Fahrer, {{ d.charges }} Ladungen)
          </li>
        </ul>
      </template>

      <h4 class="text-xs uppercase tracking-wider text-gray-400 mb-2">Quellen</h4>
      <div class="overflow-x-auto border border-gray-800 rounded-sm">
        <table class="min-w-full text-sm">
          <tbody class="divide-y divide-gray-800">
            <tr v-for="s in data.sources" :key="s.id" data-testid="crawl-source" :class="s.enabled ? 'text-gray-200' : 'text-gray-500'">
              <td class="px-3 py-2 font-medium whitespace-nowrap">{{ s.empName }}</td>
              <td class="px-3 py-2"><a :href="s.url" target="_blank" rel="noopener noreferrer" class="text-indigo-400 hover:underline break-all">{{ s.url }}</a></td>
              <td class="px-3 py-2 whitespace-nowrap">{{ s.mode === 'WATCH' ? 'nur Bilder' : 'Text' }}</td>
              <td class="px-3 py-2" :class="failing(s) ? 'text-red-300' : ''">{{ statusText(s) }}</td>
              <td class="px-3 py-2 whitespace-nowrap text-gray-400">{{ de(s.lastFetchedAt) }}</td>
              <td class="px-3 py-2 whitespace-nowrap">
                <label class="flex items-center gap-2 text-gray-300">
                  <input type="checkbox" :checked="s.enabled" class="accent-indigo-500" @click.prevent="toggle(s)" /> aktiv
                </label>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <form v-if="adding" class="mt-3 flex flex-wrap gap-2 items-end" @submit.prevent="addSource">
        <input v-model="adding.empName" name="crawlEmp" placeholder="Anbieter" :class="field" required />
        <input v-model="adding.url" name="crawlUrl" type="url" placeholder="https://" :class="[field, 'min-w-[24rem]']" required />
        <select v-model="adding.mode" name="crawlMode" :class="field">
          <option value="EXTRACT">Text lesen</option>
          <option value="WATCH">Nur auf Änderung achten</option>
        </select>
        <button type="submit" class="px-3 py-1.5 rounded-sm bg-indigo-600 text-white text-sm hover:bg-indigo-500">Anlegen</button>
        <button type="button" class="px-3 py-1.5 rounded-sm border border-gray-700 text-gray-300 text-sm hover:bg-gray-800" @click="adding = null">Abbrechen</button>
      </form>
      <button v-else type="button" class="mt-3 text-sm text-indigo-400 hover:underline" @click="adding = { empName: '', url: '', mode: 'EXTRACT' }">Quelle hinzufügen</button>
    </template>
  </section>
</template>
