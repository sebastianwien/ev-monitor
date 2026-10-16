<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ArrowDownTrayIcon, ArrowUturnLeftIcon, ChevronDownIcon, ChevronUpIcon } from '@heroicons/vue/24/outline'
import { adminVehicleSpecService, apiErrorText, type ModelGroup, type SpecCommand, type SpecRow } from '../../api/adminVehicleSpecService'

/**
 * Abgleich je Modell gegen eine lokale Datei `public/spec-vorschlaege.json` (gitignored, nur auf dem Entwicklungsrechner).
 * Oben unsere Zeilen, darunter alle Einträge der Datei für dieses Modell, beide nach Start sortiert.
 * Zahlen je Eintrag liefert auf Klick ein lokaler Helfer (`_helper` in der Datei). Woher Datei und Helfer kommen, weiß dieser Code nicht.
 * Ohne Datei zeigt der Tab nur einen Hinweis.
 */
interface Entry { label: string; battery: number | null; availableFrom: string | null; availableTo: string | null; sourceUrl: string }
type Values = Pick<SpecCommand, 'batteryCapacityKwh' | 'netBatteryCapacityKwh' | 'officialRangeKm' | 'officialConsumptionKwhPer100km'>
type Used = { kind: 'übernommen'; targetId: string; before: SpecRow } | { kind: 'angelegt'; createdId: string }

const PAGE = 10
const entriesByModel = ref<Record<string, Entry[]> | null>(null)
const helper = ref('')
const models = ref<ModelGroup[]>([])
const values = ref<Record<string, Values | 'loading' | 'error'>>({})
const targetId = ref<Record<string, string>>({})
const used = ref(new Map<string, Used>())
const done = ref(new Set<string>())
const touched = ref(new Set<string>())
const onlyOpen = ref(true)
const filter = ref('')
const shown = ref(PAGE)
const busy = ref<string | null>(null)
const error = ref('')
const loading = ref(true)

onMounted(async () => {
    try {
        const res = await fetch('/spec-vorschlaege.json', { cache: 'no-store' })
        if (!res.ok) return
        const file = await res.json() as { _helper?: string; _models?: Record<string, Entry[]> }
        helper.value = typeof file._helper === 'string' ? file._helper : ''
        entriesByModel.value = file._models ?? {}
        models.value = (await adminVehicleSpecService.overview()).models
        await loadCached()
    } catch (err) {
        error.value = apiErrorText(err, 'Laden fehlgeschlagen.')
    } finally {
        loading.value = false
    }
})

const loaded = (e: Entry): Values | null => { const v = values.value[e.sourceUrl]; return v && typeof v === 'object' ? v : null }

/** Sortierung per Spaltenkopf, gilt für alle Modelle und für beide Hälften; leere Werte immer unten. */
type SortKey = 'name' | 'trim' | 'brutto' | 'netto' | 'range' | 'cons' | 'from' | 'to' | 'cars'
const sort = ref<{ key: SortKey; dir: 1 | -1 }>({ key: 'from', dir: 1 })
function sortBy(key: SortKey) {
    sort.value = sort.value.key === key ? { key, dir: sort.value.dir === 1 ? -1 : 1 } : { key, dir: 1 }
}
const ariaSort = (key: SortKey) => sort.value.key !== key ? 'none' : sort.value.dir === 1 ? 'ascending' : 'descending'
const COLUMNS: { key: SortKey; label: string; right?: boolean }[] = [
    { key: 'name', label: 'Variante' }, { key: 'trim', label: 'Trim' }, { key: 'brutto', label: 'Brutto', right: true },
    { key: 'netto', label: 'Netto', right: true }, { key: 'range', label: 'Reichweite', right: true }, { key: 'cons', label: 'Verbrauch', right: true },
    { key: 'from', label: 'Ab' }, { key: 'to', label: 'Bis' }, { key: 'cars', label: 'Autos', right: true },
]
function compare(a: string | number | null | undefined, b: string | number | null | undefined): number {
    const ea = a == null || a === '', eb = b == null || b === ''
    if (ea || eb) return ea === eb ? 0 : ea ? 1 : -1
    const c = typeof a === 'number' && typeof b === 'number' ? a - b : String(a).localeCompare(String(b), 'de', { numeric: true })
    return c * sort.value.dir
}
const ourValue = (r: SpecRow, k: SortKey) => ({
    name: r.variantName, trim: r.trimLevel, brutto: r.batteryCapacityKwh, netto: r.netBatteryCapacityKwh, range: r.officialRangeKm,
    cons: r.officialConsumptionKwhPer100km, from: r.availableFrom, to: r.availableTo ?? '9999-12-31', cars: r.activeCars,
})[k]
const entryValue = (e: Entry, k: SortKey) => {
    const v = loaded(e)
    return ({
        name: e.label, trim: null, brutto: v?.batteryCapacityKwh ?? e.battery, netto: v?.netBatteryCapacityKwh ?? e.battery, range: v?.officialRangeKm,
        cons: v?.officialConsumptionKwhPer100km, from: e.availableFrom, to: e.availableTo ?? '9999-12-31', cars: null,
    } as Record<SortKey, string | number | null | undefined>)[k]
}
const byOurs = (a: SpecRow, b: SpecRow) => compare(ourValue(a, sort.value.key), ourValue(b, sort.value.key)) || compare(a.availableFrom, b.availableFrom)
const byEntries = (a: Entry, b: Entry) => compare(entryValue(a, sort.value.key), entryValue(b, sort.value.key)) || compare(a.availableFrom, b.availableFrom)
const matches = (text: string | null | undefined) => !filter.value.trim() || (text ?? '').toLowerCase().includes(filter.value.trim().toLowerCase())

/** Einmal je Datenstand berechnet: je Modell unsere Zeilen und die Einträge, sortiert; der Filter wirkt nur auf den Modellnamen. */
const sections = computed(() => models.value
    .filter(m => (entriesByModel.value?.[m.carModel]?.length ?? 0) > 0 && !done.value.has(m.carModel))
    .filter(m => !onlyOpen.value || touched.value.has(m.carModel) || m.rows.some(r => !r.availableFrom || !r.sourceUrl))
    .filter(m => matches(m.displayName) || matches(m.carModel))
    .map(m => {
        const linked = new Map(m.rows.filter(r => r.sourceUrl).map(r => [r.sourceUrl as string, r]))
        return {
            m,
            ours: [...m.rows].sort(byOurs),
            entries: [...(entriesByModel.value?.[m.carModel] ?? [])].sort(byEntries),
            linked,
            target: m.rows.find(r => r.id === targetId.value[m.carModel]) ?? null,
        }
    }))
const page = computed(() => sections.value.slice(0, shown.value))

function toCommand(r: SpecRow): SpecCommand {
    return {
        model: r.carModel, batteryCapacityKwh: r.batteryCapacityKwh, netBatteryCapacityKwh: r.netBatteryCapacityKwh,
        officialRangeKm: r.officialRangeKm, officialConsumptionKwhPer100km: r.officialConsumptionKwhPer100km,
        variantName: r.variantName ?? '', trimLevel: r.trimLevel ?? '', availableFrom: r.availableFrom, availableTo: r.availableTo,
        sourceUrl: r.sourceUrl ?? '', ratingSource: r.ratingSource,
    }
}

const usedKey = (m: ModelGroup, e: Entry) => `${m.carModel}|${e.sourceUrl}`

/** Werte, die der Helfer schon kennt (je ev-database-ID), übernehmen, damit ein Reload nichts verwirft. Ohne Helfer still nichts. */
async function loadCached() {
    if (!helper.value) return
    try {
        const res = await fetch(`${helper.value}/cached`)
        if (!res.ok) return
        const byId = await res.json() as Record<string, Values>
        const next = { ...values.value }
        for (const e of Object.values(entriesByModel.value ?? {}).flat()) {
            const id = e.sourceUrl.match(/\/car\/(\d+)\//)?.[1]
            if (id && byId[id]) next[e.sourceUrl] = byId[id]
        }
        values.value = next
    } catch { /* Helfer läuft nicht */ }
}

/** Zahlen eines Eintrags vom lokalen Helfer holen, nur auf Klick. */
async function loadOne(e: Entry) {
    values.value = { ...values.value, [e.sourceUrl]: 'loading' }
    try {
        const res = await fetch(`${helper.value}/spec?url=${encodeURIComponent(e.sourceUrl)}`)
        if (!res.ok) throw new Error(String(res.status))
        values.value = { ...values.value, [e.sourceUrl]: await res.json() as Values }
    } catch {
        values.value = { ...values.value, [e.sourceUrl]: 'error' }
    }
}

/** Zahlen aller noch fehlenden Einträge eines Modells holen, nacheinander. */
async function loadValues(m: ModelGroup) {
    for (const e of entriesByModel.value?.[m.carModel] ?? []) if (!loaded(e)) await loadOne(e)
}

function replaceRow(m: ModelGroup, row: SpecRow) {
    models.value = models.value.map(x => x.carModel !== m.carModel ? x
        : { ...x, rows: x.rows.some(r => r.id === row.id) ? x.rows.map(r => r.id === row.id ? row : r) : [...x.rows, row] })
}

/** Werte vom Eintrag über die Basis legen; leere Werte lassen die Basis stehen, nur ein leeres Bis heißt offen. */
function entryCommand(base: SpecRow, e: Entry): SpecCommand {
    const filled = Object.fromEntries(Object.entries(loaded(e) ?? {}).filter(([, v]) => v != null))
    return { ...toCommand(base), ...filled, availableFrom: e.availableFrom ?? base.availableFrom, availableTo: e.availableTo, sourceUrl: e.sourceUrl }
}

async function run(m: ModelGroup, failText: string, fn: () => Promise<void>) {
    busy.value = m.carModel
    error.value = ''
    try {
        await fn()
        touched.value = new Set([...touched.value, m.carModel])
    } catch (err) {
        error.value = `${m.displayName}: ${apiErrorText(err, failText)}`
    } finally {
        busy.value = null
    }
}

/** Zielzeile auf den Eintrag setzen: Zeitraum und Quelle immer, Zahlen nur, wenn geladen und nicht leer. */
const apply = (m: ModelGroup, t: SpecRow, e: Entry) => run(m, 'Speichern fehlgeschlagen.', async () => {
    const result = await adminVehicleSpecService.update(t.id, entryCommand(t, e))
    replaceRow(m, result.row)
    used.value = new Map([...used.value, [usedKey(m, e), { kind: 'übernommen', targetId: t.id, before: t }]])
})

/** Neue Zeile aus einem Eintrag mit geladenen Zahlen; Trim und Norm kommen vom Ziel. */
const createFrom = (m: ModelGroup, t: SpecRow, e: Entry) => run(m, 'Anlegen fehlgeschlagen.', async () => {
    const result = await adminVehicleSpecService.create({ ...entryCommand(t, e), variantName: e.label })
    replaceRow(m, result.row)
    used.value = new Map([...used.value, [usedKey(m, e), { kind: 'angelegt', createdId: result.row.id }]])
})

/** Nimmt Übernehmen (alter Stand der Zielzeile) oder Anlegen (neue Zeile löschen) zurück. */
const revert = (m: ModelGroup, e: Entry) => run(m, 'Zurücknehmen fehlgeschlagen.', async () => {
    const u = used.value.get(usedKey(m, e))
    if (!u) return
    if (u.kind === 'übernommen') {
        replaceRow(m, (await adminVehicleSpecService.update(u.targetId, toCommand(u.before))).row)
    } else {
        await adminVehicleSpecService.remove(u.createdId, 'Vorschlag zurückgenommen')
        models.value = models.value.map(x => x.carModel !== m.carModel ? x : { ...x, rows: x.rows.filter(r => r.id !== u.createdId) })
    }
    const next = new Map(used.value)
    next.delete(usedKey(m, e))
    used.value = next
})

const fmt = (v: number | null | undefined) => v == null ? '–' : v.toLocaleString('de-DE')
const diff = (a: number | null | undefined, b: number | null | undefined) => a != null && b != null && Math.abs(a - b) > 0.05 ? 'text-amber-300' : ''
/** Datum in Jahr, Monat, Tag zerlegt und von vorn gegen das Ziel gefärbt: grün bis zur ersten Abweichung, ab dort gelb, ohne Ziel neutral. */
function dateParts(a: string | null, b: string | null | undefined): { text: string; cls: string }[] {
    if (!a) return []
    const pa = a.split('-'), pb = b ? b.split('-') : null
    let matching = true
    return pa.flatMap((p, i) => {
        matching = matching && pb?.[i] === p
        return [
            ...(i > 0 ? [{ text: '-', cls: '' }] : []),
            { text: p, cls: !pb ? '' : matching ? 'text-green-300' : 'text-amber-300' },
        ]
    })
}
/** Name in Wörter zerlegt, jedes Wort gegen die Wörter im Namen des Ziels gefärbt (Groß/klein egal): drin grün, nicht drin gelb, ohne Ziel neutral. */
function nameParts(a: string, b: string | null | undefined): { text: string; cls: string }[] {
    const words = b == null ? null : new Set(b.toLowerCase().split(/\s+/))
    return a.split(/\s+/).filter(Boolean).map(w => ({ text: w, cls: !words ? '' : words.has(w.toLowerCase()) ? 'text-green-300' : 'text-amber-300' }))
}
const shortUrl = (u: string) => u.replace(/^https:\/\/(www\.)?/, '').replace(/^ev-database\.org\/car\//, '')
const cell = 'py-1.5 pr-3 whitespace-nowrap'
const date = 'py-1.5 pr-3 whitespace-nowrap'
const num = 'py-1.5 pr-3 text-right'
</script>

<template>
  <div class="space-y-4">
    <p v-if="loading" class="text-sm text-gray-400">Lade …</p>
    <p v-else-if="!entriesByModel" class="text-sm text-gray-400">Keine Vorschlagsdatei gefunden. Dieser Tab arbeitet nur mit einer lokalen Datei.</p>
    <template v-else>
      <div class="flex flex-wrap items-center gap-3 text-sm">
        <input v-model="filter" type="search" placeholder="Modell, z. B. ID.3" aria-label="Modell filtern"
               class="w-56 bg-gray-900 border border-gray-700 rounded-sm px-3 py-1.5 focus:outline-none focus:border-indigo-500" />
        <label class="flex items-center gap-2"><input v-model="onlyOpen" type="checkbox" /> nur Modelle mit Zeilen ohne Zeitraum oder Quelle</label>
        <span class="text-gray-400">{{ sections.length }} Modelle</span>
      </div>
      <p v-if="error" class="text-sm text-red-400" role="alert">{{ error }}</p>

      <section v-for="{ m, ours, entries, linked, target } in page" :key="m.carModel" class="bg-gray-800 rounded-sm p-3">
        <div class="flex items-center gap-3 mb-2 text-sm">
          <span class="font-medium">{{ m.carBrand }} {{ m.displayName }}</span>
          <span class="text-gray-400">{{ m.rows.length }} Zeilen bei uns · {{ entriesByModel[m.carModel].length }} Einträge</span>
          <span v-if="!target" class="text-xs text-amber-300">Ziel wählen, dann übernehmen</span>
          <button v-if="helper" type="button" class="ml-auto px-3 py-1 bg-gray-700 hover:bg-gray-600 rounded-sm text-xs"
                  :disabled="entries.every(e => loaded(e))" @click="loadValues(m)">Werte laden</button>
          <button type="button" class="px-3 py-1 bg-gray-700 hover:bg-gray-600 rounded-sm text-xs" :class="helper ? '' : 'ml-auto'"
                  title="Modell schließen, nichts wird geändert" @click="done = new Set([...done, m.carModel])">Fertig</button>
        </div>
        <table class="w-full text-sm tabular-nums">
          <thead class="text-xs text-gray-400">
            <tr class="text-left">
              <th v-for="(c, ci) in COLUMNS" :key="c.key" class="py-1 pr-3 font-normal" :class="[ci === 0 ? 'pl-2' : '', c.right ? 'text-right' : '']" :aria-sort="ariaSort(c.key)">
                <button type="button" class="inline-flex items-center gap-1 hover:text-gray-200" :class="sort.key === c.key ? 'text-gray-200' : ''" @click="sortBy(c.key)">
                  {{ c.label }}
                  <ChevronUpIcon v-if="sort.key === c.key && sort.dir === 1" class="w-3 h-3" aria-hidden="true" />
                  <ChevronDownIcon v-else-if="sort.key === c.key" class="w-3 h-3" aria-hidden="true" />
                </button>
              </th>
              <th class="py-1 pr-3 font-normal">Quelle</th>
              <th class="py-1 font-normal"></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="x in ours" :key="x.id" class="cursor-pointer"
                :class="x.id === target?.id ? 'bg-indigo-950/60 text-indigo-100' : 'text-gray-400 hover:text-gray-200'"
                :title="x.id === target?.id ? 'Übernehmen schreibt in diese Zeile' : 'Klick: Übernehmen soll in diese Zeile schreiben'"
                @click="targetId = { ...targetId, [m.carModel]: x.id }">
              <td :class="[cell, x.id === target?.id ? 'font-medium' : '']" class="pl-2 rounded-l-sm">{{ x.variantName ?? 'ohne Name' }}</td>
              <td :class="cell">{{ x.trimLevel || '–' }}</td>
              <td :class="num">{{ fmt(x.batteryCapacityKwh) }}</td>
              <td :class="num">{{ fmt(x.netBatteryCapacityKwh) }}</td>
              <td :class="num">{{ fmt(x.officialRangeKm) }}</td>
              <td :class="num">{{ fmt(x.officialConsumptionKwhPer100km) }}</td>
              <td :class="date">{{ x.availableFrom ?? '–' }}</td>
              <td :class="date">{{ x.availableTo ?? 'offen' }}</td>
              <td :class="num" data-cars :title="x.allCars > x.activeCars ? `+${x.allCars - x.activeCars} im Papierkorb` : ''">{{ x.activeCars }}</td>
              <td :class="cell"><a v-if="x.sourceUrl" :href="x.sourceUrl" target="_blank" rel="noopener noreferrer" class="underline" @click.stop>{{ shortUrl(x.sourceUrl) }}</a><span v-else>–</span></td>
              <td class="py-1.5 pr-2 text-right text-xs rounded-r-sm whitespace-nowrap">
                <label class="inline-flex items-center gap-1 cursor-pointer" @click.stop>
                  <input type="radio" :name="'ziel-' + m.carModel" :checked="x.id === target?.id" @change="targetId = { ...targetId, [m.carModel]: x.id }" /> Ziel
                </label>
              </td>
            </tr>
            <tr><td colspan="11" class="pt-3 pb-1 pl-2 text-xs text-gray-500">ev-database</td></tr>
            <tr v-for="e in entries" :key="e.sourceUrl" data-entry class="border-t border-gray-700/60">
              <td :class="cell" class="pl-2" data-name><template v-for="(p, pi) in nameParts(e.label, target ? target.variantName ?? '' : undefined)" :key="pi">{{ pi > 0 ? ' ' : '' }}<span :class="p.cls">{{ p.text }}</span></template></td>
              <td :class="cell"></td>
              <template v-if="loaded(e)">
                <td :class="[num, diff(loaded(e)!.batteryCapacityKwh, target?.batteryCapacityKwh)]">{{ fmt(loaded(e)!.batteryCapacityKwh) }}</td>
                <td :class="[num, diff(loaded(e)!.netBatteryCapacityKwh, target?.netBatteryCapacityKwh)]">{{ fmt(loaded(e)!.netBatteryCapacityKwh) }}</td>
                <td :class="[num, diff(loaded(e)!.officialRangeKm, target?.officialRangeKm)]">{{ fmt(loaded(e)!.officialRangeKm) }}</td>
                <td :class="[num, diff(loaded(e)!.officialConsumptionKwhPer100km, target?.officialConsumptionKwhPer100km)]">{{ fmt(loaded(e)!.officialConsumptionKwhPer100km) }}</td>
              </template>
              <td v-else colspan="4" :class="num" class="text-xs text-gray-500">
                <span class="inline-flex items-center gap-2">
                  {{ values[e.sourceUrl] === 'loading' ? 'lädt …' : values[e.sourceUrl] === 'error' ? 'nicht ladbar' : `${fmt(e.battery)} kWh` }}
                  <button v-if="helper" type="button" class="p-1 bg-gray-700 hover:bg-gray-600 rounded-sm text-gray-200 disabled:opacity-40"
                          :disabled="values[e.sourceUrl] === 'loading'" aria-label="Werte holen" title="Werte holen"
                          @click="loadOne(e)"><ArrowDownTrayIcon class="w-4 h-4" aria-hidden="true" /></button>
                </span>
              </td>
              <td :class="date" data-from>
                <template v-if="e.availableFrom"><span v-for="(p, pi) in dateParts(e.availableFrom, target ? target.availableFrom : undefined)" :key="pi" :class="p.cls">{{ p.text }}</span></template>
                <template v-else>–</template>
              </td>
              <td :class="date" data-to>
                <template v-if="e.availableTo"><span v-for="(p, pi) in dateParts(e.availableTo, target ? target.availableTo : undefined)" :key="pi" :class="p.cls">{{ p.text }}</span></template>
                <span v-else :class="target ? (target.availableTo ? 'text-amber-300' : 'text-green-300') : ''">offen</span>
              </td>
              <td :class="num"></td>
              <td :class="cell"><a :href="e.sourceUrl" target="_blank" rel="noopener noreferrer" class="text-indigo-300 underline">{{ shortUrl(e.sourceUrl) }}</a></td>
              <td class="py-1.5 text-right whitespace-nowrap">
                <span v-if="used.get(usedKey(m, e))" class="inline-flex items-center gap-2">
                  <span class="text-xs text-green-300">{{ used.get(usedKey(m, e))!.kind }}</span>
                  <button type="button" class="p-1.5 bg-gray-700 hover:bg-gray-600 rounded-sm disabled:opacity-40" :disabled="busy === m.carModel"
                          :aria-label="used.get(usedKey(m, e))!.kind === 'übernommen' ? 'Übernehmen zurücknehmen' : 'Angelegte Zeile löschen'"
                          :title="used.get(usedKey(m, e))!.kind === 'übernommen' ? 'Übernehmen zurücknehmen' : 'Angelegte Zeile löschen'"
                          @click="revert(m, e)"><ArrowUturnLeftIcon class="w-4 h-4" aria-hidden="true" /></button>
                </span>
                <template v-else>
                  <span v-if="linked.get(e.sourceUrl)" class="mr-2 text-xs text-green-300" :title="`Link steht bei: ${linked.get(e.sourceUrl)!.variantName}`">verknüpft</span>
                  <button type="button" class="px-3 py-1 bg-indigo-600 hover:bg-indigo-500 rounded-sm text-white text-xs disabled:opacity-40"
                          :disabled="!target || busy === m.carModel" :title="target ? `In „${target.variantName ?? 'ohne Name'}“ schreiben` : 'Erst oben ein Ziel wählen'"
                          @click="target && apply(m, target, e)">Übernehmen</button>
                  <button type="button" class="ml-1 px-3 py-1 bg-gray-700 hover:bg-gray-600 rounded-sm text-xs disabled:opacity-40"
                          :disabled="!target || !loaded(e) || busy === m.carModel"
                          :title="!target ? 'Erst oben ein Ziel wählen (Vorlage für Trim und Norm)' : !loaded(e) ? 'Erst Werte laden' : 'Neue Zeile mit den Zahlen dieses Eintrags, Trim vom Ziel'"
                          @click="target && createFrom(m, target, e)">Neue Zeile</button>
                </template>
              </td>
            </tr>
          </tbody>
        </table>
      </section>
      <button v-if="sections.length > shown" type="button" class="px-4 py-2 bg-gray-700 hover:bg-gray-600 rounded-sm text-sm" @click="shown += PAGE">
        Weitere {{ Math.min(PAGE, sections.length - shown) }} von {{ sections.length - shown }} Modellen anzeigen
      </button>
      <p v-if="sections.length === 0" class="text-sm text-gray-500">Nichts mehr offen.</p>
    </template>
  </div>
</template>
