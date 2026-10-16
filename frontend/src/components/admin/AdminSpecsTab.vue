<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ChevronDownIcon, ChevronLeftIcon, ChevronRightIcon, ChevronUpIcon } from '@heroicons/vue/24/outline'
import {
    adminVehicleSpecService, apiErrorText,
    type ChangeEntry, type MissingVehicleRequestRow, type ModelGroup, type SaveResult, type SpecCommand, type SpecOverview, type SpecRow, type WorkList,
} from '../../api/adminVehicleSpecService'
import AdminSpecRowForm from './AdminSpecRowForm.vue'
import AdminSpecRowInline, { SPEC_GRID } from './AdminSpecRowInline.vue'
import AdminSpecReassignSheet from './AdminSpecReassignSheet.vue'

/**
 * Admin-Tab "Fahrzeug-Specs": die Tabelle vehicle_specification ist Daten, dieser Tab ist der einzige Schreibweg.
 * Arbeitslisten sind serverseitig je Zeile berechnete Flags; sie sollen bis null abgearbeitet werden können.
 * Ab lg wird eine Zeile an Ort und Stelle bearbeitet, ein Chip mit Zielfeld startet den Lückenmodus (Feld für Feld durch die Liste).
 * Unter lg bleibt das Formular; "Neue Zeile" öffnet immer das Formular.
 */
type Filter = WorkList | 'ALL'

/** Chip -> Feld, das der Lückenmodus Zeile für Zeile füllt. Ohne Zielfeld filtert der Chip nur. */
const GAP_FIELDS: Partial<Record<WorkList, keyof SpecCommand>> = {
    NO_NAME: 'variantName', NO_TRIM: 'trimLevel', NO_PERIOD: 'availableFrom', NO_SOURCE: 'sourceUrl', NO_NET: 'netBatteryCapacityKwh',
}

const WORK_LISTS: { key: WorkList; label: string }[] = [
    { key: 'NO_NAME', label: 'Ohne Name' },
    { key: 'NO_TRIM', label: 'Ohne Trim' },
    { key: 'NO_PERIOD', label: 'Ohne Zeitraum' },
    { key: 'NO_SOURCE', label: 'Ohne Quelle' },
    { key: 'NO_NET', label: 'Ohne Netto' },
    { key: 'USER_ROW', label: 'Nutzer-Zeile' },
    { key: 'ROW_WITHOUT_ENUM_MODEL', label: 'Ohne Enum-Modell' },
]

const data = ref<SpecOverview | null>(null)
const loading = ref(false)
const error = ref('')
const notice = ref('')
const filter = ref<Filter>('ALL')
const showModelsWithoutRow = ref(false)
const form = ref<{ row: SpecRow | null; model: string | null; brand: string | null; template: SpecRow | null } | null>(null)
/** Zeile, die gerade inline offen ist; `field` gesetzt = Lückenmodus, nur dieses Feld aktiv. */
const inline = ref<{ id: string; field: keyof SpecCommand | null } | null>(null)
const gapMode = ref<{ label: string; field: keyof SpecCommand; ids: string[]; index: number; done: number } | null>(null)
const reassign = ref<ModelGroup | null>(null)
const changesFor = ref<{ row: SpecRow; entries: ChangeEntry[] } | null>(null)
/** Meldungen "Fehlt etwas?" der Nutzer: Zeile anlegen, dann erledigen (schickt die Rückmeldung). */
const requests = ref<MissingVehicleRequestRow[]>([])
const showRequests = ref(true)

async function load() {
    loading.value = true
    error.value = ''
    try {
        data.value = await adminVehicleSpecService.overview()
        requests.value = (await adminVehicleSpecService.listRequests()) ?? []
    } catch (err) {
        error.value = apiErrorText(err, 'Specs konnten nicht geladen werden.')
    } finally {
        loading.value = false
    }
}
onMounted(load)

const allRows = computed(() => data.value?.models.flatMap(m => m.rows) ?? [])
const counts = computed(() => Object.fromEntries(
    WORK_LISTS.map(w => [w.key, allRows.value.filter(r => r.workLists.includes(w.key)).length])) as Record<WorkList, number>)

/** Sortierung per Spaltenkopf, je Modell; ohne Klick die Reihenfolge vom Server. Leere Werte immer unten. */
type SortKey = 'name' | 'trim' | 'brutto' | 'range' | 'from' | 'source'
const COLUMNS: { key: SortKey; label: string }[] = [
    { key: 'name', label: 'Variante' }, { key: 'trim', label: 'Trim' }, { key: 'brutto', label: 'Akku brutto / netto' },
    { key: 'range', label: 'Normwert' }, { key: 'from', label: 'Zeitraum' }, { key: 'source', label: 'Quelle' },
]
const sort = ref<{ key: SortKey; dir: 1 | -1 } | null>(null)
function sortBy(key: SortKey) {
    page.value = 0
    sort.value = sort.value?.key === key ? { key, dir: sort.value.dir === 1 ? -1 : 1 } : { key, dir: 1 }
}
const ariaSort = (key: SortKey) => sort.value?.key !== key ? 'none' : sort.value.dir === 1 ? 'ascending' : 'descending'
const sortValue = (r: SpecRow, k: SortKey) => ({
    name: r.variantName, trim: r.trimLevel, brutto: r.batteryCapacityKwh, range: r.officialRangeKm, from: r.availableFrom, source: r.sourceUrl,
})[k]
function sorted(rows: SpecRow[]): SpecRow[] {
    const s = sort.value
    if (!s) return rows
    return [...rows].sort((x, y) => {
        const a = sortValue(x, s.key), b = sortValue(y, s.key)
        const ea = a == null || a === '', eb = b == null || b === ''
        if (ea || eb) return ea === eb ? 0 : ea ? 1 : -1
        return (typeof a === 'number' && typeof b === 'number' ? a - b : String(a).localeCompare(String(b), 'de', { numeric: true })) * s.dir
    })
}

const filteredModels = computed<ModelGroup[]>(() => {
    if (!data.value) return []
    const f = filter.value
    return data.value.models
        .map(m => ({ ...m, rows: sorted(f === 'ALL' ? m.rows : m.rows.filter(r => r.workLists.includes(f))) }))
        .filter(m => f === 'ALL' || m.rows.length > 0)
})

/** Seiten zu PAGE_SIZE Spec-Zeilen; ein Modell kann über zwei Seiten laufen und steht dann auf beiden. */
const PAGE_SIZE = 25
const page = ref(0)
const filteredIds = computed(() => filteredModels.value.flatMap(m => m.rows.map(r => r.id)))
const pageCount = computed(() => Math.max(1, Math.ceil(filteredIds.value.length / PAGE_SIZE)))
const pageInfo = computed(() => {
    const total = filteredIds.value.length
    return total ? `Zeilen ${page.value * PAGE_SIZE + 1}-${Math.min(total, (page.value + 1) * PAGE_SIZE)} von ${total}` : ''
})
watch(pageCount, n => { if (page.value >= n) page.value = n - 1 })
const visibleModels = computed<ModelGroup[]>(() => {
    const onPage = new Set(filteredIds.value.slice(page.value * PAGE_SIZE, (page.value + 1) * PAGE_SIZE))
    return filteredModels.value
        .map(m => ({ ...m, rows: m.rows.filter(r => onPage.has(r.id)) }))
        .filter(m => m.rows.length > 0)
})
function goToPage(p: number) {
    page.value = Math.min(Math.max(0, p), pageCount.value - 1)
    closeInline()
}

const isDesktop = () => typeof window.matchMedia === 'function' && window.matchMedia('(min-width: 1024px)').matches

function openCreate(model: ModelGroup | null, brand?: string, modelName?: string) {
    form.value = { row: null, model: model?.carModel ?? modelName ?? null, brand: model?.carBrand ?? brand ?? null, template: model?.rows[0] ?? null }
    changesFor.value = null
    closeInline()
}

function openEdit(row: SpecRow) {
    changesFor.value = null
    if (isDesktop()) {
        form.value = null
        gapMode.value = null
        inline.value = { id: row.id, field: null }
        return
    }
    form.value = { row, model: row.carModel, brand: row.carBrand, template: null }
}

function closeInline() {
    inline.value = null
    gapMode.value = null
}

function selectFilter(f: Filter) {
    filter.value = f
    page.value = 0
    closeInline()
    const field = f === 'ALL' ? undefined : GAP_FIELDS[f]
    if (!field || !isDesktop()) return
    const ids = filteredModels.value.flatMap(m => m.inEnum ? m.rows.map(r => r.id) : [])
    if (!ids.length) return
    gapMode.value = { label: WORK_LISTS.find(w => w.key === f)?.label ?? f, field, ids, index: -1, done: 0 }
    gapOpen(0)
}

function gapOpen(index: number) {
    const gap = gapMode.value
    if (!gap) return
    if (index >= gap.ids.length) {
        notice.value = `${gap.label}: ${gap.done} von ${gap.ids.length} erledigt, Liste durch.`
        closeInline()
        return
    }
    gap.index = index
    page.value = Math.floor(filteredIds.value.indexOf(gap.ids[index]) / PAGE_SIZE)
    inline.value = { id: gap.ids[index], field: gap.field }
}

function gapNext() {
    if (gapMode.value) gapOpen(gapMode.value.index + 1)
}

async function onInlineSaved(result: SaveResult) {
    if (gapMode.value) {
        gapMode.value.done++
        gapNext()
    } else {
        inline.value = null
        notice.value = result.warnings.length ? 'Gespeichert mit Hinweis: ' + result.warnings.join(' | ') : 'Gespeichert.'
    }
    await load()
}

async function onSaved(result: SaveResult) {
    form.value = null
    notice.value = result.warnings.length ? 'Gespeichert mit Hinweis: ' + result.warnings.join(' | ') : 'Gespeichert.'
    await load()
}

async function remove(row: SpecRow) {
    const reason = window.prompt('Begründung für das Löschen (Quelle oder Grund):')
    if (!reason || !reason.trim()) return
    error.value = ''
    try {
        await adminVehicleSpecService.remove(row.id, reason.trim())
        notice.value = 'Zeile gelöscht.'
        await load()
    } catch (err) {
        error.value = apiErrorText(err, 'Löschen fehlgeschlagen.')
    }
}

async function finishRequest(r: MissingVehicleRequestRow) {
    const hint = r.contactAvailable ? 'Der Nutzer bekommt jetzt die Mail "ist jetzt da, leg dein Auto an".' : 'Ohne Kontakt-Mail, der Nutzer wird nicht benachrichtigt.'
    if (!window.confirm(`Meldung "${r.freeText}" als erledigt markieren? ${hint}`)) return
    error.value = ''
    try {
        await adminVehicleSpecService.markRequestDone(r.id)
        notice.value = r.contactAvailable ? 'Erledigt, Nutzer benachrichtigt.' : 'Erledigt.'
        await load()
    } catch (err) {
        error.value = apiErrorText(err, 'Erledigen fehlgeschlagen.')
    }
}

/** Protokoll klappt unter der Zeile auf; zweiter Klick schließt es. */
async function showChanges(row: SpecRow) {
    form.value = null
    if (changesFor.value?.row.id === row.id) { changesFor.value = null; return }
    try {
        changesFor.value = { row, entries: await adminVehicleSpecService.changes(row.id) }
    } catch (err) {
        error.value = apiErrorText(err, 'Protokoll konnte nicht geladen werden.')
    }
}

const date = (iso: string) => new Date(iso).toLocaleString('de-DE', { dateStyle: 'short', timeStyle: 'short' })
</script>

<template>
  <div class="space-y-4 text-gray-200">
    <details class="bg-gray-800 rounded-sm p-3 text-sm">
      <summary class="cursor-pointer font-medium">Regeln</summary>
      <ol class="list-decimal pl-5 mt-2 space-y-1 text-gray-400">
        <li>Eine Zeile ist genau eine Variante mit WLTP-Werten vom Hersteller. Keine Spannen, keine Mischungen, keine Ausstattung.</li>
        <li>Jede Zeile braucht eine https-Quelle. WLTP ist der Anker, der ev-database-Realwert nur Korrekturquelle (Verhältnis unter 1,10 ist verdächtig).</li>
        <li>Löschen nur ohne Autos. Sonst Autos einzeln umhängen; das Netto der Zeile ersetzt die eigene Angabe des Fahrers.</li>
        <li>Arbeitslisten bis null abarbeiten. Modelle ohne Zeile bekommen ihre Akku-Auswahl noch aus dem Code.</li>
      </ol>
    </details>

    <div class="flex flex-wrap gap-2 items-center text-sm">
      <button type="button" class="px-3 py-1.5 rounded-sm" :class="filter === 'ALL' ? 'bg-indigo-600 text-white' : 'bg-gray-800 hover:bg-gray-700'" @click="selectFilter('ALL')">
        Alle ({{ allRows.length }})
      </button>
      <button v-for="w in WORK_LISTS" :key="w.key" type="button" class="px-3 py-1.5 rounded-sm"
              :class="filter === w.key ? 'bg-indigo-600 text-white' : counts[w.key] ? 'bg-gray-800 hover:bg-gray-700' : 'bg-gray-800/50 text-gray-500'"
              @click="selectFilter(w.key)">
        {{ w.label }} ({{ counts[w.key] }})
      </button>
      <button type="button" class="px-3 py-1.5 rounded-sm" :class="showModelsWithoutRow ? 'bg-indigo-600 text-white' : 'bg-gray-800 hover:bg-gray-700'"
              @click="showModelsWithoutRow = !showModelsWithoutRow">
        Modelle ohne Zeile ({{ data?.modelsWithoutRow.length ?? 0 }})
      </button>
      <button type="button" class="px-3 py-1.5 rounded-sm bg-gray-800 hover:bg-gray-700" @click="selectFilter('ALL')" title="Je Modell unter dem Namen ausgewiesen">
        Autos ohne Zeile ({{ data?.carsWithoutSpec ?? 0 }})
      </button>
      <button type="button" class="px-3 py-1.5 rounded-sm" :class="showRequests && requests.length ? 'bg-indigo-600 text-white' : requests.length ? 'bg-gray-800 hover:bg-gray-700' : 'bg-gray-800/50 text-gray-500'"
              @click="showRequests = !showRequests">
        Gemeldet ({{ requests.length }})
      </button>
      <button type="button" class="ml-auto px-3 py-1.5 bg-indigo-600 hover:bg-indigo-500 rounded-sm text-white" @click="openCreate(null)">Neue Zeile</button>
    </div>

    <p v-if="error" class="text-sm text-red-400" role="alert">{{ error }}</p>
    <p v-if="notice" class="text-sm text-green-400" role="status">{{ notice }} <button type="button" class="underline" @click="notice = ''">ok</button></p>
    <p v-if="loading" class="text-sm text-gray-500">Lädt …</p>

    <p v-if="gapMode" class="sticky top-0 z-10 bg-indigo-950/95 border border-indigo-800 rounded-sm px-3 py-2 text-sm text-indigo-100" role="status">
      {{ gapMode.label }}: {{ gapMode.done }} von {{ gapMode.ids.length }} erledigt · Enter speichert, leer überspringt · Esc beendet
    </p>

    <AdminSpecRowForm v-if="form" :key="form.row?.id ?? 'new'" :row="form.row" :preset-model="form.model" :preset-brand="form.brand" :template="form.template"
                      @saved="onSaved" @cancel="form = null" />

    <AdminSpecReassignSheet v-if="reassign" :key="reassign.carModel" :model="reassign.carModel" :display-name="reassign.displayName"
                            @close="reassign = null" @changed="load" />

    <section v-if="showRequests && requests.length" class="bg-gray-800 rounded-sm p-4 space-y-2" data-testid="reported-requests">
      <h3 class="font-semibold">Gemeldet</h3>
      <p class="text-xs text-gray-400">Nutzer vermissen eine Variante, ein Modell oder einen Hersteller. Zeile anlegen, dann erledigen: das schickt die Rückmeldung.</p>
      <ul class="grid gap-2 sm:grid-cols-2 text-sm">
        <li v-for="r in requests" :key="r.id" class="bg-gray-900 rounded-sm px-3 py-2 space-y-1.5">
          <p class="font-medium break-words">{{ r.freeText }}</p>
          <p class="text-xs text-gray-400">
            {{ r.brand ?? '-' }} · {{ r.model ?? '-' }}<template v-if="r.netKwh != null"> · {{ r.netKwh }} kWh</template><template v-if="r.buildYear"> · {{ r.buildYear }}</template>
            · {{ date(r.createdAt) }}<span v-if="!r.contactAvailable" class="text-amber-300"> · ohne Kontakt-Mail</span>
          </p>
          <div class="flex flex-wrap gap-2">
            <button type="button" class="px-2 py-1 bg-gray-800 hover:bg-gray-700 rounded-sm text-xs" @click="openCreate(null, r.brand ?? undefined, r.model ?? undefined)">Zeile anlegen</button>
            <button type="button" class="px-2 py-1 bg-green-700 hover:bg-green-600 rounded-sm text-xs text-white" @click="finishRequest(r)">Erledigt und Nutzer benachrichtigen</button>
          </div>
        </li>
      </ul>
    </section>

    <section v-if="showModelsWithoutRow && data" class="bg-gray-800 rounded-sm p-4 space-y-2">
      <h3 class="font-semibold">Modelle ohne Zeile</h3>
      <p class="text-xs text-gray-400">Sortiert nach Autos. Solange hier Autos stehen, bleibt die Akku-Auswahl aus dem Code.</p>
      <ul class="grid gap-2 sm:grid-cols-2 lg:grid-cols-3 text-sm">
        <li v-for="m in data.modelsWithoutRow" :key="m.carModel" class="flex items-center justify-between gap-2 bg-gray-900 rounded-sm px-3 py-2">
          <span>{{ m.displayName }} <span class="text-gray-500">{{ m.carBrand }} · {{ m.activeCars }} Autos · {{ m.logCount }} Logs</span></span>
          <button type="button" class="px-2 py-1 bg-gray-800 hover:bg-gray-700 rounded-sm text-xs" @click="openCreate(null, m.carBrand, m.carModel)">Zeile anlegen</button>
        </li>
      </ul>
    </section>

    <nav v-if="pageCount > 1" class="flex items-center gap-2 text-sm" aria-label="Seiten">
      <button type="button" class="inline-flex items-center gap-1 px-2 py-1 bg-gray-800 hover:bg-gray-700 rounded-sm disabled:opacity-40" :disabled="page === 0" @click="goToPage(page - 1)">
        <ChevronLeftIcon class="w-4 h-4" aria-hidden="true" />Zurück
      </button>
      <span class="text-gray-400">{{ pageInfo }} · Seite {{ page + 1 }} von {{ pageCount }}</span>
      <button type="button" class="inline-flex items-center gap-1 px-2 py-1 bg-gray-800 hover:bg-gray-700 rounded-sm disabled:opacity-40" :disabled="page >= pageCount - 1" @click="goToPage(page + 1)">
        Weiter<ChevronRightIcon class="w-4 h-4" aria-hidden="true" />
      </button>
    </nav>

    <section v-for="m in visibleModels" :key="m.carModel" class="bg-gray-800 rounded-sm p-3 sm:p-4 space-y-3">
      <header class="flex flex-wrap items-center gap-2">
        <h3 class="font-semibold">{{ m.displayName }} <span class="text-gray-500 font-normal text-sm">{{ m.carBrand }}</span></h3>
        <span class="text-xs text-gray-400">{{ m.logCount }} Logs · {{ m.activeCars }} Autos<template v-if="m.carsWithoutSpec"> · <span class="text-amber-300">{{ m.carsWithoutSpec }} ohne Zeile</span></template></span>
        <span v-if="!m.inEnum" class="text-xs text-red-400">nicht im Enum</span>
        <span class="ml-auto flex gap-2">
          <button v-if="m.carsWithoutSpec" type="button" class="px-2 py-1 bg-gray-700 hover:bg-gray-600 rounded-sm text-xs" @click="reassign = m">Autos umhängen</button>
          <button v-if="m.inEnum" type="button" class="px-2 py-1 bg-gray-700 hover:bg-gray-600 rounded-sm text-xs" @click="openCreate(m)">Neue Zeile</button>
        </span>
      </header>

      <div class="hidden px-3 text-xs text-gray-500" :class="SPEC_GRID">
        <span v-for="c in COLUMNS" :key="c.key" :aria-sort="ariaSort(c.key)">
          <button type="button" :data-sort="c.key" class="inline-flex items-center gap-1 hover:text-gray-200" :class="sort?.key === c.key ? 'text-gray-200' : ''" @click="sortBy(c.key)">
            {{ c.label }}
            <ChevronUpIcon v-if="sort?.key === c.key && sort.dir === 1" class="w-3 h-3" aria-hidden="true" />
            <ChevronDownIcon v-else-if="sort?.key === c.key" class="w-3 h-3" aria-hidden="true" />
          </button>
        </span><span></span>
      </div>
      <ul class="space-y-2">
        <AdminSpecRowInline v-for="r in m.rows" :key="r.id" :row="r" :can-edit="m.inEnum" :changes="changesFor?.row.id === r.id ? changesFor.entries : null"
                            :editing="inline?.id === r.id" :focus-field="inline?.id === r.id ? inline.field : null"
                            @edit="openEdit(r)" @saved="onInlineSaved" @cancel="closeInline" @next="gapNext" @changes="showChanges(r)" @remove="remove(r)" @cars-changed="load" />
      </ul>
    </section>

    <nav v-if="pageCount > 1" data-pager-bottom class="flex items-center gap-2 text-sm" aria-label="Seiten">
      <button type="button" class="inline-flex items-center gap-1 px-2 py-1 bg-gray-800 hover:bg-gray-700 rounded-sm disabled:opacity-40" :disabled="page === 0" @click="goToPage(page - 1)">
        <ChevronLeftIcon class="w-4 h-4" aria-hidden="true" />Zurück
      </button>
      <span class="text-gray-400">{{ pageInfo }} · Seite {{ page + 1 }} von {{ pageCount }}</span>
      <button type="button" class="inline-flex items-center gap-1 px-2 py-1 bg-gray-800 hover:bg-gray-700 rounded-sm disabled:opacity-40" :disabled="page >= pageCount - 1" @click="goToPage(page + 1)">
        Weiter<ChevronRightIcon class="w-4 h-4" aria-hidden="true" />
      </button>
    </nav>

    <p v-if="data && visibleModels.length === 0 && !loading" class="text-sm text-gray-500">Diese Arbeitsliste ist leer.</p>
  </div>
</template>
