<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { adminVehicleSpecService, apiErrorText, type SpecCommand, type SpecRow } from '../../api/adminVehicleSpecService'

/**
 * Vorschläge für Spec-Zeilen aus einer lokalen Datei `public/spec-vorschlaege.json` (gitignored, nur auf dem
 * Entwicklungsrechner vorhanden). Woher die Datei kommt, weiß dieser Code nicht. Ohne Datei zeigt der Tab nur einen Hinweis.
 * Zahlenwerte je Kandidat liefert auf Klick ein lokaler Helfer (`_helper` in der Datei), ebenfalls nur lokal.
 */
interface Suggestion { label: string; availableFrom: string | null; availableTo: string | null; sourceUrl: string | null }
type Values = Pick<SpecCommand, 'batteryCapacityKwh' | 'netBatteryCapacityKwh' | 'officialRangeKm' | 'officialConsumptionKwhPer100km'>

const suggestions = ref<Record<string, Suggestion[]> | null>(null)
const helper = ref('')
const rows = ref<SpecRow[]>([])
const values = ref<Record<string, Values | 'loading' | 'error'>>({})
const applied = ref(new Set<string>())
const created = ref(0)
/** Gruppen, die Sebastian mit "Fertig" geschlossen hat, und Kandidaten, die schon benutzt wurden (Zeilen-ID plus Index). */
const done = ref(new Set<string>())
const used = ref(new Map<string, 'übernommen' | 'angelegt'>())
const onlyOpen = ref(true)
const busy = ref<string | null>(null)
const error = ref('')
const loading = ref(true)

onMounted(async () => {
    try {
        const res = await fetch('/spec-vorschlaege.json', { cache: 'no-store' })
        if (!res.ok) return
        const { _helper, ...rest } = await res.json() as Record<string, Suggestion[]> & { _helper?: string }
        helper.value = typeof _helper === 'string' ? _helper : ''
        suggestions.value = rest
        const overview = await adminVehicleSpecService.overview()
        rows.value = overview.models.flatMap(m => m.rows)
    } catch (err) {
        error.value = apiErrorText(err, 'Laden fehlgeschlagen.')
    } finally {
        loading.value = false
    }
})

/** Eine Gruppe bleibt offen, bis "Fertig" geklickt wird, auch nach Übernehmen, damit weitere Kandidaten noch als neue Zeile angelegt werden können. */
const visible = computed(() => rows.value.filter(r => suggestions.value?.[r.id]?.length
    && !done.value.has(r.id)
    && (!onlyOpen.value || !r.availableFrom || !r.sourceUrl || applied.value.has(r.id))))
const usedKey = (r: SpecRow, i: number) => `${r.id}#${i}`
/** Welche unserer Zeilen "Übernehmen" beschreibt: je Gruppe wählbar, Standard ist die Zeile der Gruppe. */
const targetId = ref<Record<string, string>>({})
const target = (r: SpecRow) => rows.value.find(x => x.id === targetId.value[r.id]) ?? r
/** Unsere anderen Zeilen desselben Modells (gleicher Trim, sonst alle), nach Zeitraum: Vorgänger und Nachfolger zum Einordnen der Kandidaten. */
const ours = (r: SpecRow) => rows.value
    .filter(x => x.carModel === r.carModel && (!r.trimLevel || !x.trimLevel || x.trimLevel.toLowerCase() === r.trimLevel.toLowerCase()))
    .sort((a, b) => (a.availableFrom ?? '9999').localeCompare(b.availableFrom ?? '9999'))

function toCommand(r: SpecRow): SpecCommand {
    return {
        model: r.carModel, batteryCapacityKwh: r.batteryCapacityKwh, netBatteryCapacityKwh: r.netBatteryCapacityKwh,
        officialRangeKm: r.officialRangeKm, officialConsumptionKwhPer100km: r.officialConsumptionKwhPer100km,
        variantName: r.variantName ?? '', trimLevel: r.trimLevel ?? '', availableFrom: r.availableFrom, availableTo: r.availableTo,
        sourceUrl: r.sourceUrl ?? '', ratingSource: r.ratingSource,
    }
}

const loaded = (s: Suggestion): Values | null => { const v = s.sourceUrl ? values.value[s.sourceUrl] : null; return v && typeof v === 'object' ? v : null }

/** Zahlen aller Kandidaten einer Zeile vom lokalen Helfer holen, nacheinander, nur auf Klick. */
async function loadValues(r: SpecRow) {
    for (const s of suggestions.value?.[r.id] ?? []) {
        if (!s.sourceUrl || loaded(s)) continue
        values.value = { ...values.value, [s.sourceUrl]: 'loading' }
        try {
            const res = await fetch(`${helper.value}/spec?url=${encodeURIComponent(s.sourceUrl)}`)
            if (!res.ok) throw new Error(String(res.status))
            values.value = { ...values.value, [s.sourceUrl]: await res.json() as Values }
        } catch {
            values.value = { ...values.value, [s.sourceUrl]: 'error' }
        }
    }
}

function candidateCommand(r: SpecRow, s: Suggestion): SpecCommand {
    return {
        ...toCommand(r), ...(loaded(s) ?? {}),
        availableFrom: s.availableFrom ?? r.availableFrom, availableTo: s.availableTo, sourceUrl: s.sourceUrl ?? r.sourceUrl ?? '',
    }
}

/** Bestehende Zeile auf den Kandidaten setzen: Zeitraum und Quelle immer, Zahlen nur, wenn geladen. */
async function apply(r: SpecRow, s: Suggestion, i: number) {
    const t = target(r)
    busy.value = r.id
    error.value = ''
    try {
        const result = await adminVehicleSpecService.update(t.id, candidateCommand(t, s))
        rows.value = rows.value.map(x => x.id === t.id ? result.row : x)
        applied.value = new Set([...applied.value, r.id, t.id])
        used.value = new Map([...used.value, [usedKey(r, i), 'übernommen']])
    } catch (err) {
        error.value = `${t.variantName ?? t.carModel}: ${apiErrorText(err, 'Speichern fehlgeschlagen.')}`
    } finally {
        busy.value = null
    }
}

/** Neue Variante: Zahlen vom Kandidaten (geladen) oder von der Ausgangszeile, Trim von der Ausgangszeile. */
async function createFrom(r: SpecRow, s: Suggestion, i: number) {
    busy.value = r.id
    error.value = ''
    try {
        const result = await adminVehicleSpecService.create({ ...candidateCommand(r, s), variantName: s.label.replace(/\s*\([\d.,]+ kWh\)$/, '') })
        rows.value = [...rows.value, result.row]
        created.value++
        used.value = new Map([...used.value, [usedKey(r, i), 'angelegt']])
    } catch (err) {
        error.value = `${s.label}: ${apiErrorText(err, 'Anlegen fehlgeschlagen.')}`
    } finally {
        busy.value = null
    }
}

const fmt = (v: number | null | undefined) => v == null ? '–' : v.toLocaleString('de-DE')
const diff = (a: number | null | undefined, b: number | null | undefined) => a != null && b != null && Math.abs(a - b) > 0.05 ? 'text-amber-300' : ''
const dateDiff = (a: string | null, b: string | null) => a && b && a !== b ? 'text-amber-300' : ''
const shortUrl = (u: string) => u.replace(/^https:\/\/(www\.)?/, '').replace(/^ev-database\.org\/car\//, '')
</script>

<template>
  <div class="space-y-4">
    <p v-if="loading" class="text-sm text-gray-400">Lade …</p>
    <p v-else-if="!suggestions" class="text-sm text-gray-400">Keine Vorschlagsdatei gefunden. Dieser Tab arbeitet nur mit einer lokalen Datei.</p>
    <template v-else>
      <div class="flex flex-wrap items-center gap-3 text-sm">
        <label class="flex items-center gap-2"><input v-model="onlyOpen" type="checkbox" /> nur Zeilen ohne Zeitraum oder Quelle</label>
        <span class="text-gray-400">{{ visible.length }} offen · {{ applied.size }} übernommen · {{ created }} angelegt</span>
      </div>
      <p v-if="error" class="text-sm text-red-400" role="alert">{{ error }}</p>

      <section v-for="r in visible" :key="r.id" class="bg-gray-800 rounded-sm p-3">
        <div class="flex items-center gap-3 mb-2 text-sm">
          <span class="font-medium">{{ r.carBrand }} {{ r.carModel }}</span>
          <span class="text-gray-400">Trim {{ r.trimLevel ?? '–' }} · {{ r.ratingSource }}</span>
          <button v-if="helper" type="button" class="ml-auto px-3 py-1 bg-gray-700 hover:bg-gray-600 rounded-sm text-xs"
                  :disabled="suggestions[r.id].every(s => loaded(s))" @click="loadValues(r)">Werte laden</button>
          <button type="button" class="px-3 py-1 bg-gray-700 hover:bg-gray-600 rounded-sm text-xs" :class="helper ? '' : 'ml-auto'"
                  title="Gruppe schließen, nichts wird geändert" @click="done = new Set([...done, r.id])">Fertig</button>
        </div>
        <table class="w-full text-sm tabular-nums">
          <thead class="text-xs text-gray-400">
            <tr class="text-left">
              <th class="py-1 pr-3 font-normal">Variante</th>
              <th class="py-1 pr-3 font-normal text-right">Brutto</th>
              <th class="py-1 pr-3 font-normal text-right">Netto</th>
              <th class="py-1 pr-3 font-normal text-right">Reichweite</th>
              <th class="py-1 pr-3 font-normal text-right">Verbrauch</th>
              <th class="py-1 pr-3 font-normal">Ab</th>
              <th class="py-1 pr-3 font-normal">Bis</th>
              <th class="py-1 pr-3 font-normal">Quelle</th>
              <th class="py-1 font-normal"></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="x in ours(r)" :key="x.id" class="cursor-pointer" :class="x.id === target(r).id ? 'bg-indigo-950/60 text-indigo-100' : 'text-gray-500 hover:text-gray-300'"
                :title="x.id === target(r).id ? 'Übernehmen schreibt in diese Zeile' : 'Klick: Übernehmen soll in diese Zeile schreiben'" @click="targetId = { ...targetId, [r.id]: x.id }">
              <td class="py-1.5 pr-3 pl-2 rounded-l-sm" :class="x.id === target(r).id ? 'font-medium' : ''">{{ x.variantName ?? 'ohne Name' }}</td>
              <td class="py-1.5 pr-3 text-right">{{ fmt(x.batteryCapacityKwh) }}</td>
              <td class="py-1.5 pr-3 text-right">{{ fmt(x.netBatteryCapacityKwh) }}</td>
              <td class="py-1.5 pr-3 text-right">{{ fmt(x.officialRangeKm) }}</td>
              <td class="py-1.5 pr-3 text-right">{{ fmt(x.officialConsumptionKwhPer100km) }}</td>
              <td class="py-1.5 pr-3">{{ x.availableFrom ?? '–' }}</td>
              <td class="py-1.5 pr-3">{{ x.availableTo ?? 'offen' }}</td>
              <td class="py-1.5 pr-3"><a v-if="x.sourceUrl" :href="x.sourceUrl" target="_blank" rel="noopener noreferrer" class="underline" :class="x.id === target(r).id ? 'text-indigo-300' : ''" @click.stop>{{ shortUrl(x.sourceUrl) }}</a><span v-else>–</span></td>
              <td class="py-1.5 pr-2 text-right text-xs rounded-r-sm whitespace-nowrap">
                <label class="inline-flex items-center gap-1 cursor-pointer" :class="x.id === target(r).id ? 'text-indigo-300' : ''">
                  <input type="radio" :name="'ziel-' + r.id" :checked="x.id === target(r).id" @change="targetId = { ...targetId, [r.id]: x.id }" /> Ziel
                </label>
              </td>
            </tr>
            <tr v-for="(s, i) in suggestions[r.id]" :key="i" class="border-t border-gray-700/60">
              <td class="py-1.5 pr-3 pl-2">{{ s.label.replace(/\s*\([\d.,]+ kWh\)$/, '') }}</td>
              <template v-if="loaded(s)">
                <td class="py-1.5 pr-3 text-right" :class="diff(loaded(s)!.batteryCapacityKwh, target(r).batteryCapacityKwh)">{{ fmt(loaded(s)!.batteryCapacityKwh) }}</td>
                <td class="py-1.5 pr-3 text-right" :class="diff(loaded(s)!.netBatteryCapacityKwh, target(r).netBatteryCapacityKwh)">{{ fmt(loaded(s)!.netBatteryCapacityKwh) }}</td>
                <td class="py-1.5 pr-3 text-right" :class="diff(loaded(s)!.officialRangeKm, target(r).officialRangeKm)">{{ fmt(loaded(s)!.officialRangeKm) }}</td>
                <td class="py-1.5 pr-3 text-right" :class="diff(loaded(s)!.officialConsumptionKwhPer100km, target(r).officialConsumptionKwhPer100km)">{{ fmt(loaded(s)!.officialConsumptionKwhPer100km) }}</td>
              </template>
              <td v-else colspan="4" class="py-1.5 pr-3 text-right text-xs text-gray-500">
                {{ s.sourceUrl && values[s.sourceUrl] === 'loading' ? 'lädt …' : s.sourceUrl && values[s.sourceUrl] === 'error' ? 'nicht ladbar' : helper ? 'Werte laden' : '' }}
              </td>
              <td class="py-1.5 pr-3" :class="dateDiff(s.availableFrom, target(r).availableFrom)">{{ s.availableFrom ?? '–' }}</td>
              <td class="py-1.5 pr-3" :class="dateDiff(s.availableTo, target(r).availableTo)">{{ s.availableTo ?? 'offen' }}</td>
              <td class="py-1.5 pr-3"><a v-if="s.sourceUrl" :href="s.sourceUrl" target="_blank" rel="noopener noreferrer" class="text-indigo-300 underline">{{ shortUrl(s.sourceUrl) }}</a></td>
              <td class="py-1.5 text-right whitespace-nowrap">
                <span v-if="used.get(usedKey(r, i))" class="text-xs text-green-300">{{ used.get(usedKey(r, i)) }}</span>
                <template v-else>
                  <button type="button" class="px-3 py-1 bg-indigo-600 hover:bg-indigo-500 rounded-sm text-white text-xs disabled:opacity-40"
                          :disabled="busy === r.id" @click="apply(r, s, i)">Übernehmen</button>
                  <button type="button" class="ml-1 px-3 py-1 bg-gray-700 hover:bg-gray-600 rounded-sm text-xs disabled:opacity-40" title="Neue Variante aus diesem Kandidaten, Trim von der bestehenden Zeile"
                          :disabled="busy === r.id" @click="createFrom(r, s, i)">Neue Zeile</button>
                </template>
              </td>
            </tr>
          </tbody>
        </table>
      </section>
      <p v-if="visible.length === 0" class="text-sm text-gray-500">Nichts mehr offen.</p>
    </template>
  </div>
</template>
