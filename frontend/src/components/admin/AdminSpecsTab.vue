<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
    adminVehicleSpecService, apiErrorText,
    type ChangeEntry, type MissingVehicleRequestRow, type ModelGroup, type SaveResult, type SpecOverview, type SpecRow, type WorkList,
} from '../../api/adminVehicleSpecService'
import AdminSpecRowForm from './AdminSpecRowForm.vue'
import AdminSpecReassignSheet from './AdminSpecReassignSheet.vue'

/**
 * Admin-Tab "Fahrzeug-Specs": die Tabelle vehicle_specification ist Daten, dieser Tab ist der einzige Schreibweg.
 * Arbeitslisten sind serverseitig je Zeile berechnete Flags; sie sollen bis null abgearbeitet werden können.
 */
type Filter = WorkList | 'ALL'

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
const form = ref<{ row: SpecRow | null; model: string | null; brand: string | null } | null>(null)
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

const visibleModels = computed<ModelGroup[]>(() => {
    if (!data.value) return []
    if (filter.value === 'ALL') return data.value.models
    const f = filter.value
    return data.value.models
        .map(m => ({ ...m, rows: m.rows.filter(r => r.workLists.includes(f)) }))
        .filter(m => m.rows.length > 0)
})

function openCreate(model: ModelGroup | null, brand?: string, modelName?: string) {
    form.value = { row: null, model: model?.carModel ?? modelName ?? null, brand: model?.carBrand ?? brand ?? null }
    changesFor.value = null
}

function openEdit(row: SpecRow) {
    form.value = { row, model: row.carModel, brand: row.carBrand }
    changesFor.value = null
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

async function showChanges(row: SpecRow) {
    form.value = null
    try {
        changesFor.value = { row, entries: await adminVehicleSpecService.changes(row.id) }
    } catch (err) {
        error.value = apiErrorText(err, 'Protokoll konnte nicht geladen werden.')
    }
}

const ratio = (r: SpecRow) => r.netBatteryCapacityKwh
    ? (r.officialRangeKm * r.officialConsumptionKwhPer100km / 100 / r.netBatteryCapacityKwh)
    : null
const fmtRatio = (r: SpecRow) => { const v = ratio(r); return v == null ? '-' : v.toLocaleString('de-DE', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) }
const ratioClass = (r: SpecRow) => { const v = ratio(r); return v != null && v < 1.1 ? 'text-amber-300' : 'text-gray-300' }
const period = (r: SpecRow) => r.availableFrom ? `${r.availableFrom.slice(0, 4)}-${r.availableTo?.slice(0, 4) ?? ''}` : 'ohne Zeitraum'
const date = (iso: string) => new Date(iso).toLocaleString('de-DE', { dateStyle: 'short', timeStyle: 'short' })
const flagLabel = (w: WorkList) => WORK_LISTS.find(x => x.key === w)?.label ?? w
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
      <button type="button" class="px-3 py-1.5 rounded-sm" :class="filter === 'ALL' ? 'bg-indigo-600 text-white' : 'bg-gray-800 hover:bg-gray-700'" @click="filter = 'ALL'">
        Alle ({{ allRows.length }})
      </button>
      <button v-for="w in WORK_LISTS" :key="w.key" type="button" class="px-3 py-1.5 rounded-sm"
              :class="filter === w.key ? 'bg-indigo-600 text-white' : counts[w.key] ? 'bg-gray-800 hover:bg-gray-700' : 'bg-gray-800/50 text-gray-500'"
              @click="filter = w.key">
        {{ w.label }} ({{ counts[w.key] }})
      </button>
      <button type="button" class="px-3 py-1.5 rounded-sm" :class="showModelsWithoutRow ? 'bg-indigo-600 text-white' : 'bg-gray-800 hover:bg-gray-700'"
              @click="showModelsWithoutRow = !showModelsWithoutRow">
        Modelle ohne Zeile ({{ data?.modelsWithoutRow.length ?? 0 }})
      </button>
      <button type="button" class="px-3 py-1.5 rounded-sm bg-gray-800 hover:bg-gray-700" @click="filter = 'ALL'" title="Je Modell unter dem Namen ausgewiesen">
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

    <AdminSpecRowForm v-if="form" :key="form.row?.id ?? 'new'" :row="form.row" :preset-model="form.model" :preset-brand="form.brand"
                      @saved="onSaved" @cancel="form = null" />

    <AdminSpecReassignSheet v-if="reassign" :key="reassign.carModel" :model="reassign.carModel" :display-name="reassign.displayName"
                            @close="reassign = null" @changed="load" />

    <section v-if="changesFor" class="bg-gray-800 rounded-sm p-4 space-y-2">
      <header class="flex items-center justify-between gap-2">
        <h3 class="font-semibold">Protokoll: {{ changesFor.row.variantName ?? changesFor.row.carModel }}</h3>
        <button type="button" class="px-3 py-1 bg-gray-700 rounded-sm text-sm" @click="changesFor = null">Schließen</button>
      </header>
      <p v-if="changesFor.entries.length === 0" class="text-sm text-gray-500">Noch keine Änderungen über den Tab.</p>
      <ul class="text-xs space-y-1">
        <li v-for="e in changesFor.entries" :key="e.id" class="text-gray-300">
          {{ date(e.changedAt) }} · {{ e.action }}<template v-if="e.field"> · {{ e.field }}</template>
          <template v-if="e.oldValue != null || e.newValue != null">: <span class="text-gray-500 break-all">{{ e.oldValue ?? '-' }}</span> → <span class="break-all">{{ e.newValue ?? '-' }}</span></template>
          <span v-if="e.warning" class="text-amber-300"> · {{ e.warning }}</span>
        </li>
      </ul>
    </section>

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

      <ul class="space-y-2">
        <li v-for="r in m.rows" :key="r.id" class="bg-gray-900 rounded-sm p-3 text-sm space-y-1">
          <div class="flex flex-wrap items-center gap-x-3 gap-y-1">
            <span class="font-medium">{{ r.variantName ?? 'ohne Name' }}</span>
            <span class="text-gray-400">{{ r.trimLevel ?? 'ohne Trim' }} · {{ period(r) }} · {{ r.ratingSource }}</span>
            <span v-for="w in r.workLists" :key="w" class="px-2 py-0.5 rounded-full text-xs bg-amber-900/50 text-amber-200">{{ flagLabel(w) }}</span>
          </div>
          <div class="flex flex-wrap gap-x-3 gap-y-1 text-xs text-gray-300">
            <span>{{ r.batteryCapacityKwh }} / {{ r.netBatteryCapacityKwh ?? '?' }} kWh</span>
            <span>{{ r.officialRangeKm }} km</span>
            <span>{{ r.officialConsumptionKwhPer100km }} kWh/100 km</span>
            <span :class="ratioClass(r)" title="WLTP-km × Verbrauch / 100 / Netto">Verhältnis {{ fmtRatio(r) }}</span>
            <span>{{ r.activeCars }} Autos<template v-if="r.allCars > r.activeCars"> (+{{ r.allCars - r.activeCars }} im Papierkorb)</template><template v-if="r.ratings"> · {{ r.ratings }} Bewertungen</template></span>
            <a v-if="r.sourceUrl" :href="r.sourceUrl" target="_blank" rel="noopener noreferrer" class="text-indigo-300 underline truncate max-w-[16rem]">{{ r.sourceUrl.replace(/^https:\/\//, '') }}</a>
          </div>
          <div class="flex flex-wrap gap-2 pt-1">
            <button v-if="m.inEnum" type="button" class="px-2 py-1 bg-gray-800 hover:bg-gray-700 rounded-sm text-xs" @click="openEdit(r)">Bearbeiten</button>
            <button type="button" class="px-2 py-1 bg-gray-800 hover:bg-gray-700 rounded-sm text-xs" @click="showChanges(r)">Protokoll</button>
            <button type="button" class="px-2 py-1 bg-gray-800 hover:bg-red-900 rounded-sm text-xs disabled:opacity-40"
                    :disabled="r.allCars > 0 || r.ratings > 0" :title="r.allCars > 0 || r.ratings > 0 ? 'Erst Autos umhängen' : ''" @click="remove(r)">Löschen</button>
          </div>
        </li>
      </ul>
    </section>

    <p v-if="data && visibleModels.length === 0 && !loading" class="text-sm text-gray-500">Diese Arbeitsliste ist leer.</p>
  </div>
</template>
