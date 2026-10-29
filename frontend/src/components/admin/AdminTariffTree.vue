<script setup lang="ts">
import { computed, ref } from 'vue'
import { ArrowTopRightOnSquareIcon, ChevronRightIcon } from '@heroicons/vue/24/outline'
import type { CrawlProposal, CrawlSource, DriverDeviation, TariffRow } from '../../api/adminChargingTariffService'
import AdminTariffProposal from './AdminTariffProposal.vue'

/**
 * Tarife im Admin-Tab "Ladekarten", ein aufklappbarer Knoten je Anbieter: Quelle mit letztem Lauf,
 * gepflegte Tarife, darunter die Vorschläge des Crawlers an der Zeile, die sie ändern würden,
 * mit dem Satz von der Seite. Anbieter mit offenen Vorschlägen stehen oben und sind aufgeklappt.
 */
const props = defineProps<{
  tariffs: TariffRow[]
  cpos: string[]
  proposals: CrawlProposal[]
  sources: CrawlSource[]
  hints: DriverDeviation[]
  busyId: string | null
}>()
const emit = defineEmits<{
  verify: [row: TariffRow]
  edit: [row: TariffRow]
  end: [row: TariffRow]
  decide: [proposal: CrawlProposal, how: 'apply' | 'reject']
  toggleSource: [source: CrawlSource]
}>()

interface ProviderNode {
  name: string
  tariffs: TariffRow[]
  proposals: CrawlProposal[]
  sources: CrawlSource[]
  hints: DriverDeviation[]
}

const nodes = computed<ProviderNode[]>(() => {
  const names = new Set<string>([...props.tariffs, ...props.proposals, ...props.sources, ...props.hints].map(x => x.empName))
  return [...names].map(name => ({
    name,
    tariffs: props.tariffs.filter(t => t.empName === name),
    proposals: props.proposals.filter(p => p.empName === name),
    sources: props.sources.filter(s => s.empName === name),
    hints: props.hints.filter(h => h.empName === name),
  })).sort((a, b) => b.proposals.length - a.proposals.length || a.name.localeCompare(b.name, 'de'))
})

const mode = ref<'auto' | 'all' | 'none'>('auto')
const overrides = ref(new Map<string, boolean>())
function isOpen(n: ProviderNode): boolean {
  const own = overrides.value.get(n.name)
  if (own !== undefined) return own
  if (mode.value === 'all') return true
  if (mode.value === 'none') return false
  return n.proposals.length > 0
}
function toggle(n: ProviderNode) {
  const next = new Map(overrides.value)
  next.set(n.name, !isOpen(n))
  overrides.value = next
}
function setAll(open: boolean) {
  mode.value = open ? 'all' : 'none'
  overrides.value = new Map()
}

const plural = (n: number, one: string, many: string) => `${n} ${n === 1 ? one : many}`
function summary(n: ProviderNode): string {
  const count = (k: CrawlProposal['kind']) => n.proposals.filter(p => p.kind === k).length
  const parts: string[] = []
  if (count('CHANGE')) parts.push(plural(count('CHANGE'), 'Änderung', 'Änderungen'))
  if (count('NEW')) parts.push(`${count('NEW')} neu`)
  if (count('MISSING')) parts.push(plural(count('MISSING'), 'fehlt', 'fehlen'))
  if (count('MAPPING')) parts.push(plural(count('MAPPING'), 'Preisstufe', 'Preisstufen'))
  if (count('WATCH_CHANGED')) parts.push('Preisbilder geändert')
  if (n.hints.length) parts.push(plural(n.hints.length, 'Hinweis', 'Hinweise'))
  return parts.length ? parts.join(' · ') : 'nichts offen'
}

const STATUS: Record<string, string> = {
  OK: 'gelesen, stimmt',
  UNCHANGED: 'unverändert',
  CHANGED: 'geändert',
  HTTP_ERROR: 'nicht erreichbar',
  ROBOTS: 'robots.txt sperrt',
  UNREADABLE: 'keine Preise gefunden',
  EXTRACT_ERROR: 'Sprachmodell-Fehler',
}
function statusText(s: CrawlSource): string {
  if (!s.lastStatus) return 'noch nicht gelesen'
  const base = STATUS[s.lastStatus] ?? s.lastStatus
  const detail = s.lastError ? `: ${s.lastError}` : ''
  const streak = s.consecutiveFailures > 1 ? `, ${s.consecutiveFailures} Läufe in Folge` : ''
  return `${base}${detail}${streak}`
}
const failing = (s: CrawlSource) => s.consecutiveFailures > 0
function sourceState(n: ProviderNode): string {
  const s = n.sources.find(failing) ?? n.sources[0]
  if (!s) return 'ohne Quelle'
  if (!s.lastFetchedAt) return 'noch nicht gelesen'
  return failing(s) ? statusText(s) : `gelesen ${dateTime(s.lastFetchedAt)}`
}

const today = () => new Date().toISOString().slice(0, 10)
const isEnded = (r: TariffRow) => r.validUntil != null && r.validUntil < today()
const date = (iso: string) => new Date(`${iso.slice(0, 10)}T00:00:00`).toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' })
const dateTime = (iso: string | null) => iso
  ? new Date(iso).toLocaleString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' })
  : 'noch nie'
const ct = (eur: number) => `${(eur * 100).toLocaleString('de-DE', { minimumFractionDigits: 1, maximumFractionDigits: 1 })} ct`
const euro = (v: number) => `${v.toLocaleString('de-DE', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} €`
const validity = (r: TariffRow) => r.validUntil ? `${date(r.validFrom)} bis ${date(r.validUntil)}` : `ab ${date(r.validFrom)}`
const kind = (type: string, minPowerKw: number | null | undefined) => minPowerKw != null ? `${type} über ${minPowerKw} kW` : type

/** Ohne Betreiber und Tier gilt ein Tarif nur, wenn der Anbieter selbst ein Netz im Katalog hat (ADAC, Maingau nicht). */
function scope(r: TariffRow): string {
  if (r.cpoName) return r.cpoName
  if (r.priceTier) return r.appliesElsewhere ? `Tier ${r.priceTier} und alle anderen Betreiber` : `Tier ${r.priceTier}`
  if (r.appliesElsewhere) return 'alle anderen Betreiber'
  return props.cpos.includes(r.empName) ? 'eigenes Netz' : 'nirgends: kein eigenes Netz, Betreiber setzen'
}

const atRow = (n: ProviderNode, r: TariffRow) => n.proposals.filter(p => p.current?.id === r.id)
const shownIds = (n: ProviderNode) => new Set(n.tariffs.map(t => t.id))
/** Neu auf der Seite, Preisstufen, Bildseiten und Vorschläge zu Zeilen, die gerade nicht angezeigt werden. */
const loose = (n: ProviderNode) => n.proposals.filter(p => !p.current || !shownIds(n).has(p.current.id))

const EFFECT: Record<CrawlProposal['kind'], string> = {
  CHANGE: 'Übernehmen: bisherige Zeile endet gestern, neue Werte gelten ab heute.',
  MISSING: 'Übernehmen beendet diesen Tarif (letzter Tag gestern).',
  NEW: 'Übernehmen legt den Tarif ab heute an.',
  MAPPING: 'Übernehmen ordnet den Betreiber der neuen Preisstufe zu.',
  WATCH_CHANGED: 'Übernehmen heißt: Seite angesehen, Preise stimmen, Stand wird heute.',
}

const num = (v: unknown): number | null => typeof v === 'number' ? v : null
const FIELDS: { key: string; label: string; fee: boolean; show: (v: number) => string; current: (r: TariffRow) => number | null }[] = [
  { key: 'pricePerKwh', label: 'Preis', fee: false, show: v => `${euro(v)}/kWh`, current: r => r.pricePerKwh },
  { key: 'monthlyFeeEur', label: 'Grundgebühr', fee: true, show: euro, current: r => r.monthlyFeeEur },
  { key: 'sessionFeeEur', label: 'Startgebühr', fee: true, show: euro, current: r => r.sessionFeeEur },
  { key: 'blockingFeePerMin', label: 'Blockiergebühr', fee: true, show: v => `${euro(v)}/min`, current: r => r.blockingFeePerMin },
  { key: 'blockingFeeAfterMin', label: 'Blockiergebühr ab Minute', fee: false, show: v => String(v), current: r => r.blockingFeeAfterMin },
  { key: 'blockingFeeCapEur', label: 'Blockiergebühr höchstens', fee: false, show: euro, current: r => r.blockingFeeCapEur },
  { key: 'minPowerKw', label: 'Leistungsgrenze', fee: false, show: v => `über ${v} kW`, current: r => r.minPowerKw },
]
/** Was die Seite anders sagt als die Zeile. Nicht genannt = keine Änderung; keine Gebühr und 0 sind gleich. */
function differences(p: CrawlProposal): string[] {
  const r = p.current
  if (!r) return []
  const out: string[] = []
  for (const f of FIELDS) {
    const found = num(p.proposed[f.key])
    if (found == null) continue
    const was = f.current(r)
    const a = f.fee ? (was ?? 0) : was
    if (a != null && Math.abs(a - found) < 0.0005) continue
    out.push(`${f.label}: ${f.show(found)} statt ${was == null ? '-' : f.show(was)}`)
  }
  if (p.proposed.dynamic === true && !r.dynamicPricing) out.push('Preis ist dynamisch')
  return out
}

const SCOPE: Record<string, string> = { OWN_NETWORK: 'eigenes Netz', ELSEWHERE: 'alle anderen Betreiber' }
function newLine(p: CrawlProposal): string {
  const v = p.proposed
  const where = v.scope === 'OPERATOR' ? String(v.operator ?? '') : v.scope === 'TIER' ? `Tier ${v.tier ?? ''}` : SCOPE[String(v.scope)] ?? ''
  const fee = num(v.monthlyFeeEur)
  const price = num(v.pricePerKwh)
  return [v.variant, where, kind(String(v.chargingType ?? ''), num(v.minPowerKw)), price == null ? null : ct(price),
    fee ? `Grundgebühr ${euro(fee)}` : null].filter(Boolean).join(' · ')
}

/** Link mit Textfragment: Chrome und Brave springen zur Stelle und heben sie hervor. Mehrzeiliges als Anfang und Ende. */
function evidenceLink(n: ProviderNode, p: CrawlProposal): string | null {
  const url = n.sources.find(s => s.id === p.sourceId)?.url ?? n.sources[0]?.url
  if (!url || !p.evidence) return null
  const enc = (s: string) => encodeURIComponent(s.trim()).replace(/-/g, '%2D')
  const parts = p.evidence.split(' · ')
  const fragment = parts.length > 1 ? `${enc(parts[0])},${enc(parts[parts.length - 1])}` : enc(p.evidence)
  return `${url.split('#')[0]}#:~:text=${fragment}`
}

const hintText = (d: DriverDeviation) => `${d.nearestVariant ?? ''} an ${d.operator} ${d.chargingType}: gepflegt ${euro(d.curatedPricePerKwh)}, `
  + `Fahrer zahlen ${euro(d.driverMedianPricePerKwh)} (${d.drivers} Fahrer, ${d.charges} Ladungen)`
function linesOf(p: CrawlProposal): string[] {
  if (p.kind === 'CHANGE') {
    const d = differences(p)
    return d.length ? d : [p.description]
  }
  if (p.kind === 'MISSING') return ['Steht laut letztem Lauf nicht mehr auf der Seite.']
  if (p.kind === 'NEW') return [newLine(p)]
  return [p.description]
}
const warningOf = (p: CrawlProposal) => p.kind === 'CHANGE' ? p.holdReason : null
</script>

<template>
  <div>
    <div class="flex justify-end gap-3 mb-2 text-sm">
      <button type="button" class="text-indigo-400 hover:underline" @click="setAll(true)">Alle aufklappen</button>
      <button type="button" class="text-indigo-400 hover:underline" @click="setAll(false)">Alle zuklappen</button>
    </div>
    <ul class="border border-gray-800 rounded-sm divide-y divide-gray-800">
      <li v-for="n in nodes" :key="n.name" data-testid="tariff-node">
        <div class="flex items-center gap-3 px-3 py-2 hover:bg-gray-900/60">
          <button type="button" data-testid="node-toggle" class="flex-1 min-w-0 flex flex-wrap items-center gap-x-4 gap-y-1 text-left" :aria-expanded="isOpen(n)" @click="toggle(n)">
            <ChevronRightIcon class="w-4 h-4 text-gray-500 shrink-0 transition-transform" :class="isOpen(n) ? 'rotate-90' : ''" />
            <span data-testid="node-name" class="font-medium text-white">{{ n.name }}</span>
            <span class="text-sm text-gray-400">{{ plural(n.tariffs.length, 'Tarif', 'Tarife') }}</span>
            <span class="text-sm" :class="n.proposals.length ? 'text-amber-300' : 'text-gray-500'">{{ summary(n) }}</span>
            <span class="text-xs" :class="n.sources.some(failing) ? 'text-red-300' : 'text-gray-500'">{{ sourceState(n) }}</span>
          </button>
          <a v-if="n.sources[0]" :href="n.sources[0].url" target="_blank" rel="noopener noreferrer"
             class="inline-flex items-center gap-1 text-sm text-indigo-400 hover:underline shrink-0">Seite<ArrowTopRightOnSquareIcon class="w-3.5 h-3.5" /></a>
        </div>

        <div v-if="isOpen(n)" class="px-3 pb-4 space-y-3">
          <ul v-if="n.sources.length" class="text-xs text-gray-400 space-y-1">
            <li v-for="s in n.sources" :key="s.id" data-testid="crawl-source" class="flex flex-wrap items-center gap-x-3" :class="s.enabled ? '' : 'opacity-60'">
              <a :href="s.url" target="_blank" rel="noopener noreferrer" class="text-indigo-400 hover:underline break-all">{{ s.url }}</a>
              <span>{{ s.mode === 'WATCH' ? 'nur Bilder' : 'Text' }}</span>
              <span :class="failing(s) ? 'text-red-300' : ''">{{ statusText(s) }}, {{ dateTime(s.lastFetchedAt) }}</span>
              <label class="flex items-center gap-1 text-gray-300">
                <input type="checkbox" :checked="s.enabled" class="accent-indigo-500" @click.prevent="emit('toggleSource', s)" /> aktiv
              </label>
            </li>
          </ul>

          <div v-if="n.tariffs.length" class="overflow-x-auto border border-gray-800 rounded-sm">
            <table class="min-w-full text-sm">
              <thead class="bg-gray-900 text-gray-400 text-xs uppercase tracking-wider">
                <tr>
                  <th class="px-3 py-2 text-left">Variante</th>
                  <th class="px-3 py-2 text-left">Gilt bei</th>
                  <th class="px-3 py-2 text-left">Art</th>
                  <th class="px-3 py-2 text-right">Preis</th>
                  <th class="px-3 py-2 text-right">Grundgebühr</th>
                  <th class="px-3 py-2 text-left">Gültig</th>
                  <th class="px-3 py-2 text-left">Stand</th>
                  <th class="px-3 py-2 text-left">Quelle</th>
                  <th class="px-3 py-2" />
                </tr>
              </thead>
              <tbody class="divide-y divide-gray-800">
                <template v-for="r in n.tariffs" :key="r.id">
                  <tr data-testid="tariff-row" :class="isEnded(r) ? 'text-gray-500' : 'text-gray-200'">
                    <td class="px-3 py-2">{{ r.tariffVariant ?? '-' }}</td>
                    <td class="px-3 py-2">{{ scope(r) }}</td>
                    <td class="px-3 py-2 whitespace-nowrap">{{ kind(r.chargingType, r.minPowerKw) }}</td>
                    <td class="px-3 py-2 text-right tabular-nums whitespace-nowrap">{{ ct(r.pricePerKwh) }}</td>
                    <td class="px-3 py-2 text-right tabular-nums whitespace-nowrap">{{ r.monthlyFeeEur > 0 ? euro(r.monthlyFeeEur) : '-' }}</td>
                    <td class="px-3 py-2 whitespace-nowrap">{{ validity(r) }}</td>
                    <td class="px-3 py-2 whitespace-nowrap">{{ date(r.lastVerifiedAt) }}</td>
                    <td class="px-3 py-2">
                      <a v-if="r.sourceUrl" :href="r.sourceUrl" target="_blank" rel="noopener noreferrer" class="text-indigo-400 hover:underline">Link</a>
                      <span v-else>-</span>
                    </td>
                    <td class="px-3 py-2 whitespace-nowrap text-right">
                      <button v-if="!isEnded(r)" type="button" data-action="verify" class="px-2 py-1 text-emerald-400 hover:underline" @click="emit('verify', r)">Geprüft</button>
                      <button type="button" data-action="edit" class="px-2 py-1 text-indigo-400 hover:underline" @click="emit('edit', r)">Ändern</button>
                      <button v-if="!isEnded(r)" type="button" data-action="end" class="px-2 py-1 text-amber-400 hover:underline" @click="emit('end', r)">Beenden</button>
                    </td>
                  </tr>
                  <tr v-for="p in atRow(n, r)" :key="p.id" data-testid="crawl-proposal" :data-kind="p.kind" class="bg-amber-950/20">
                    <td colspan="9" class="px-3 py-3 border-l-2 border-amber-500">
                      <AdminTariffProposal :proposal="p" :lines="linesOf(p)" :warning="warningOf(p)" :effect="EFFECT[p.kind]"
                                           :link="evidenceLink(n, p)" :busy="busyId === p.id" @decide="how => emit('decide', p, how)" />
                    </td>
                  </tr>
                </template>
              </tbody>
            </table>
          </div>

          <div v-if="loose(n).length">
            <h4 class="text-xs uppercase tracking-wider text-gray-400 mb-2">Neu auf der Seite und weitere Vorschläge</h4>
            <ul class="divide-y divide-gray-800 border border-gray-800 rounded-sm">
              <li v-for="p in loose(n)" :key="p.id" data-testid="crawl-proposal" :data-kind="p.kind" class="p-3 bg-amber-950/20 border-l-2 border-amber-500">
                <AdminTariffProposal :proposal="p" :lines="linesOf(p)" :warning="warningOf(p)" :effect="EFFECT[p.kind]"
                                     :link="evidenceLink(n, p)" :busy="busyId === p.id" @decide="how => emit('decide', p, how)" />
              </li>
            </ul>
          </div>

          <ul v-if="n.hints.length" class="text-sm text-gray-200 space-y-1">
            <li v-for="d in n.hints" :key="`${d.operator}|${d.chargingType}`" data-testid="driver-hint">Fahrer zahlen anders: {{ hintText(d).trim() }}</li>
          </ul>
        </div>
      </li>
    </ul>
  </div>
</template>
