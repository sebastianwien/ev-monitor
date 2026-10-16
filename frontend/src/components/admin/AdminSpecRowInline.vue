<script lang="ts">
/** Feste Spalten ab lg, in Tab-Kopfzeile und Zeile identisch: minmax(0, …) und feste Aktionsspalte, damit Inhalt die Spuren nicht verschiebt. */
export const SPEC_GRID = 'lg:grid lg:grid-cols-[minmax(0,1.8fr)_minmax(0,1fr)_minmax(0,1.1fr)_minmax(0,1.8fr)_minmax(0,1.4fr)_minmax(0,1.2fr)_14rem] lg:items-center lg:gap-x-3'
</script>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { adminVehicleSpecService, apiErrorText, type ChangeEntry, type SaveResult, type SpecCommand, type SpecRow, type WorkList } from '../../api/adminVehicleSpecService'
import AdminSpecRowCars from './AdminSpecRowCars.vue'

/**
 * Eine Spec-Zeile an Ort und Stelle: Anzeige und Bearbeitung in derselben Spaltengeometrie, die Nachbarzeilen bleiben sichtbar.
 * Mit `focusField` (Lückenmodus) ist nur dieses Feld aktiv; Enter speichert, ein leeres Feld wird per `next` übersprungen.
 * Der Server prüft die Regeln, sein Fehlertext steht unter der Zeile, die Zeile bleibt offen.
 */
const props = defineProps<{ row: SpecRow; editing: boolean; focusField: keyof SpecCommand | null; canEdit: boolean; changes: ChangeEntry[] | null }>()
const emit = defineEmits<{ edit: []; saved: [result: SaveResult]; cancel: []; next: []; changes: []; remove: []; carsChanged: [] }>()

const FLAG_LABELS: Record<WorkList, string> = {
    NO_NAME: 'Ohne Name', NO_TRIM: 'Ohne Trim', NO_PERIOD: 'Ohne Zeitraum', NO_SOURCE: 'Ohne Quelle', NO_NET: 'Ohne Netto',
    USER_ROW: 'Nutzer-Zeile', ROW_WITHOUT_ENUM_MODEL: 'Ohne Enum-Modell',
}

const draft = ref<SpecCommand>(toCommand(props.row))
const busy = ref(false)
const error = ref('')
const warning = ref('')
const rootEl = ref<HTMLElement | null>(null)
/** Autos der Zeile sind von Anfang an aufgeklappt; Zeilen ohne Autos zeigen und laden nichts. */
const showCars = ref(true)

function toCommand(r: SpecRow): SpecCommand {
    return {
        model: r.carModel,
        batteryCapacityKwh: r.batteryCapacityKwh,
        netBatteryCapacityKwh: r.netBatteryCapacityKwh,
        officialRangeKm: r.officialRangeKm,
        officialConsumptionKwhPer100km: r.officialConsumptionKwhPer100km,
        variantName: r.variantName ?? '',
        trimLevel: r.trimLevel ?? '',
        availableFrom: r.availableFrom,
        availableTo: r.availableTo,
        sourceUrl: r.sourceUrl ?? '',
        ratingSource: r.ratingSource,
    }
}

async function focusTarget() {
    await nextTick()
    const target = props.focusField
        ? rootEl.value?.querySelector<HTMLInputElement>(`[data-field="${props.focusField}"]`)
        : rootEl.value?.querySelector<HTMLInputElement>('input')
    target?.focus()
    target?.select?.()
}

watch(() => [props.editing, props.focusField, props.row.id], () => {
    draft.value = toCommand(props.row)
    error.value = ''
    if (props.editing) focusTarget()
}, { immediate: true })

/** Im Lückenmodus ist nur das Zielfeld ein Input, der Rest bleibt Anzeige. */
const active = (field: keyof SpecCommand | 'period' | 'battery' | 'norm') => {
    if (!props.editing) return false
    if (!props.focusField) return true
    if (field === 'period') return props.focusField === 'availableFrom' || props.focusField === 'availableTo'
    if (field === 'battery') return props.focusField === 'batteryCapacityKwh' || props.focusField === 'netBatteryCapacityKwh'
    if (field === 'norm') return props.focusField === 'officialRangeKm' || props.focusField === 'officialConsumptionKwhPer100km'
    return props.focusField === field
}

function focusFieldEmpty(): boolean {
    if (!props.focusField) return false
    const v = draft.value[props.focusField]
    return v == null || v === ''
}

async function save() {
    if (busy.value) return
    if (focusFieldEmpty()) { emit('next'); return }
    busy.value = true
    error.value = ''
    try {
        const result = await adminVehicleSpecService.update(props.row.id, draft.value)
        if (result.warnings.length) {
            warning.value = result.warnings.join(' | ')
            window.setTimeout(() => { warning.value = '' }, 5000)
        }
        emit('saved', result)
    } catch (err) {
        error.value = apiErrorText(err, 'Speichern fehlgeschlagen.')
    } finally {
        busy.value = false
    }
}

function onKeydown(e: KeyboardEvent) {
    if (e.key === 'Enter') { e.preventDefault(); save() }
    else if (e.key === 'Escape') { e.preventDefault(); emit('cancel') }
}

const ratio = computed(() => props.row.netBatteryCapacityKwh
    ? props.row.officialRangeKm * props.row.officialConsumptionKwhPer100km / 100 / props.row.netBatteryCapacityKwh
    : null)
const fmtRatio = computed(() => ratio.value == null ? '-' : ratio.value.toLocaleString('de-DE', { minimumFractionDigits: 2, maximumFractionDigits: 2 }))
const ratioClass = computed(() => ratio.value != null && ratio.value < 1.1 ? 'text-amber-300' : 'text-gray-400')
const period = computed(() => props.row.availableFrom
    ? `${props.row.availableFrom.slice(0, 4)}-${props.row.availableTo?.slice(0, 4) ?? ''}` : 'ohne Zeitraum')
const shortUrl = computed(() => props.row.sourceUrl?.replace(/^https?:\/\/(www\.)?/, '') ?? '')
const date = (iso: string) => new Date(iso).toLocaleString('de-DE', { dateStyle: 'short', timeStyle: 'short' })
const deletable = computed(() => props.row.allCars === 0 && props.row.ratings === 0)

/** Volle Bearbeitung braucht mehr Platz als die festen Spalten: dann freier Umbruch statt Grid. */
const wrap = computed(() => props.editing && !props.focusField)
const input = 'min-w-0 bg-gray-800 border border-gray-600 rounded-sm px-2 py-1 text-sm text-gray-100 placeholder:text-gray-600 focus:outline-none focus:border-indigo-400 focus:ring-1 focus:ring-indigo-400'
const btn = 'px-2 py-1 rounded-sm text-xs'
</script>

<template>
  <li ref="rootEl" :data-spec-row="row.id" class="bg-gray-900 rounded-sm p-3 text-sm space-y-1 transition-shadow duration-300" :class="editing ? 'ring-1 ring-indigo-500' : ''" @keydown="onKeydown">
    <div :key="editing ? (focusField ?? 'all') : 'view'" class="flex flex-wrap gap-x-3 gap-y-2 spec-in" :class="wrap ? '' : SPEC_GRID">
      <!-- Variante -->
      <input v-if="active('variantName')" v-model="draft.variantName" data-field="variantName" maxlength="100" :class="input + (wrap ? ' flex-1 min-w-[14rem]' : ' w-full')" placeholder="Name der Variante" aria-label="Name der Variante" />
      <span v-else class="font-medium truncate" :title="row.variantName ?? ''">{{ row.variantName ?? 'ohne Name' }}</span>

      <!-- Trim -->
      <input v-if="active('trimLevel')" v-model="draft.trimLevel" data-field="trimLevel" maxlength="40" :class="input + (wrap ? ' w-40' : ' w-full')" placeholder="Trim" aria-label="Trim" />
      <span v-else class="text-gray-300 truncate" :title="row.trimLevel ?? ''">{{ row.trimLevel ?? 'ohne Trim' }}</span>

      <!-- Akku -->
      <span v-if="active('battery')" class="flex items-center gap-1 min-w-0">
        <input v-model.number="draft.batteryCapacityKwh" data-field="batteryCapacityKwh" type="number" inputmode="decimal" step="0.1" min="1" :class="input + (wrap ? ' w-20' : ' flex-1')" aria-label="Brutto kWh" placeholder="Brutto" title="Brutto kWh" />
        <span class="text-gray-500">/</span>
        <input v-model.number="draft.netBatteryCapacityKwh" data-field="netBatteryCapacityKwh" type="number" inputmode="decimal" step="0.1" min="1" :class="input + (wrap ? ' w-20' : ' flex-1')" aria-label="Netto kWh" placeholder="Netto" title="Netto kWh" />
      </span>
      <span v-else class="text-gray-300 whitespace-nowrap">{{ row.batteryCapacityKwh }} / {{ row.netBatteryCapacityKwh ?? '?' }} kWh</span>

      <!-- Norm -->
      <span v-if="active('norm')" class="flex items-center gap-1 min-w-0">
        <input v-model.number="draft.officialRangeKm" data-field="officialRangeKm" type="number" inputmode="numeric" step="1" min="1" :class="input + (wrap ? ' w-20' : ' flex-1')" aria-label="Reichweite km" placeholder="km" title="Reichweite km" />
        <input v-model.number="draft.officialConsumptionKwhPer100km" data-field="officialConsumptionKwhPer100km" type="number" inputmode="decimal" step="0.1" min="1" :class="input + (wrap ? ' w-20' : ' flex-1')" aria-label="Verbrauch kWh/100 km" placeholder="kWh/100" title="Verbrauch kWh/100 km" />
        <span class="inline-flex rounded-sm border border-gray-600 overflow-hidden" role="radiogroup" aria-label="Norm">
          <button v-for="n in ['WLTP', 'EPA']" :key="n" type="button" role="radio" :aria-checked="draft.ratingSource === n"
                  class="px-1.5 py-1 text-xs" :class="draft.ratingSource === n ? 'bg-indigo-600 text-white' : 'bg-gray-800 text-gray-400'"
                  @click="draft.ratingSource = n as SpecCommand['ratingSource']">{{ n }}</button>
        </span>
      </span>
      <span v-else class="text-gray-300 whitespace-nowrap">{{ row.officialRangeKm }} km · {{ row.officialConsumptionKwhPer100km }} kWh/100 km · {{ row.ratingSource }}</span>

      <!-- Zeitraum -->
      <span v-if="active('period')" class="flex items-center gap-1 min-w-0">
        <input v-model="draft.availableFrom" data-field="availableFrom" type="date" :class="input + (wrap ? ' w-36' : ' flex-1 text-xs')" aria-label="Ab" title="Ab" />
        <input v-model="draft.availableTo" data-field="availableTo" type="date" :class="input + (wrap ? ' w-36' : ' flex-1 text-xs')" aria-label="Bis" title="Bis, leer = läuft noch" />
      </span>
      <span v-else class="text-gray-300 whitespace-nowrap">{{ period }}</span>

      <!-- Quelle -->
      <input v-if="active('sourceUrl')" v-model="draft.sourceUrl" data-field="sourceUrl" type="url" maxlength="500" :class="input + (wrap ? ' flex-1 min-w-[16rem]' : ' w-full')" placeholder="https://" aria-label="Quelle" />
      <a v-else-if="row.sourceUrl" :href="row.sourceUrl" target="_blank" rel="noopener noreferrer" class="text-indigo-300 underline truncate">{{ shortUrl }}</a>
      <span v-else class="text-gray-500">ohne Quelle</span>

      <!-- Aktionen -->
      <span class="flex gap-1 whitespace-nowrap lg:justify-end" :class="wrap ? 'ml-auto' : ''">
        <template v-if="editing">
          <button type="button" :class="btn + ' bg-indigo-600 hover:bg-indigo-500 text-white disabled:opacity-40'" :disabled="busy" @click="save">Speichern</button>
          <button type="button" :class="btn + ' bg-gray-700 hover:bg-gray-600'" @click="emit('cancel')">Abbrechen</button>
        </template>
        <template v-else>
          <button v-if="canEdit" type="button" :class="btn + ' bg-gray-800 hover:bg-gray-700'" @click="emit('edit')">Bearbeiten</button>
          <button type="button" :class="btn + ' bg-gray-800 hover:bg-gray-700'" @click="emit('changes')">Protokoll</button>
          <button type="button" :class="btn + ' bg-gray-800 hover:bg-red-900 disabled:opacity-40'" :disabled="!deletable" :title="deletable ? '' : 'Erst Autos umhängen'" @click="emit('remove')">Löschen</button>
        </template>
      </span>
    </div>

    <div class="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-gray-400">
      <span :class="ratioClass" title="WLTP-km × Verbrauch / 100 / Netto">Verhältnis {{ fmtRatio }}</span>
      <span>
        <button v-if="row.activeCars" type="button" class="underline decoration-dotted hover:text-gray-200" :aria-expanded="showCars" @click="showCars = !showCars">Autos ({{ row.activeCars }})</button>
        <template v-else>0 Autos</template>
        <template v-if="row.allCars > row.activeCars"> (+{{ row.allCars - row.activeCars }} im Papierkorb)</template><template v-if="row.ratings"> · {{ row.ratings }} Bewertungen</template>
      </span>
      <span v-for="w in row.workLists" :key="w" class="px-2 py-0.5 rounded-full bg-amber-900/50 text-amber-200">{{ FLAG_LABELS[w] }}</span>
    </div>

    <p v-if="error" class="text-xs text-red-400" role="alert">{{ error }}</p>
    <p v-else-if="warning" class="text-xs text-amber-300" role="status">{{ warning }}</p>

    <Transition name="spec-expand">
      <div v-if="showCars && row.activeCars > 0" class="spec-expand-outer">
        <div class="min-h-0 overflow-hidden">
          <!-- Key mit Anzahl: nach dem Umhängen laden Quell- und Zielzeile neu -->
          <AdminSpecRowCars :key="`${row.id}-${row.activeCars}`" :spec-id="row.id" @changed="emit('carsChanged')" />
        </div>
      </div>
    </Transition>

    <Transition name="spec-expand">
      <div v-if="changes" class="spec-expand-outer">
        <div class="min-h-0 overflow-hidden">
          <div class="mt-2 pt-2 border-t border-gray-800 text-xs space-y-1">
            <p class="text-gray-400 font-medium">Protokoll</p>
            <p v-if="changes.length === 0" class="text-gray-500">Noch keine Änderungen über den Tab.</p>
            <ul class="space-y-1">
              <li v-for="e in changes" :key="e.id" class="text-gray-300">
                {{ date(e.changedAt) }} · {{ e.action }}<template v-if="e.field"> · {{ e.field }}</template>
                <template v-if="e.oldValue != null || e.newValue != null">: <span class="text-gray-500 break-all">{{ e.oldValue ?? '-' }}</span> → <span class="break-all">{{ e.newValue ?? '-' }}</span></template>
                <span v-if="e.warning" class="text-amber-300"> · {{ e.warning }}</span>
              </li>
            </ul>
          </div>
        </div>
      </div>
    </Transition>
  </li>
</template>

<style scoped>
/* Bearbeiten: neue Zeilengeometrie blendet weich ein (CSS-Animation beim Mount, keine Vue-Transition nötig). */
.spec-in { animation: spec-in 200ms ease-out; }
@keyframes spec-in {
  from { opacity: 0; transform: translateY(-3px); }
  to { opacity: 1; transform: none; }
}
/* Protokoll: Höhe über grid-template-rows animieren, dadurch kein fester Höhenwert nötig. */
.spec-expand-outer { display: grid; grid-template-rows: 1fr; transition: grid-template-rows 250ms ease, opacity 250ms ease; }
.spec-expand-enter-from, .spec-expand-leave-to { grid-template-rows: 0fr; opacity: 0; }
@media (prefers-reduced-motion: reduce) {
  .spec-in { animation: none; }
  .spec-expand-outer { transition: none; }
}
</style>
