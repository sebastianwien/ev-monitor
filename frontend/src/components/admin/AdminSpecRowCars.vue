<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { adminVehicleSpecService, apiErrorText, type Candidate, type CarForReassign, type SpecRow } from '../../api/adminVehicleSpecService'
import { SPEC_GRID, fitClass, wordParts, yearFits } from './specCompare'

/**
 * Autos einer Spec-Zeile, aufgeklappt unter der Zeile. Umhängen je Auto auf eine andere Zeile desselben Modells,
 * nie als Bulk: das Netto der neuen Zeile ändert Verbrauch, Phantom Drain und SoH des Autos.
 * Passt das Baujahr eindeutig zu einer Zeile, ist sie vorausgewählt; umgehängt wird trotzdem erst per Klick.
 * Die Autos stehen in den Spalten der Spec-Zeile, jede Zelle grün oder gelb gegen die Spec: so fällt ein falsch hängendes Auto ins Auge.
 */
const props = defineProps<{ row: SpecRow }>()
const emit = defineEmits<{ changed: [] }>()

const cars = ref<CarForReassign[]>([])
const target = ref<Record<string, string>>({})
const loading = ref(true)
const error = ref('')
const busyCar = ref<string | null>(null)

/** Zeile, deren Zeitraum das Baujahr enthält; bei mehreren nur die mit gleichem Netto. Nicht eindeutig = kein Vorschlag. */
function suggestion(car: CarForReassign): string | null {
    if (car.year == null) return null
    const y = car.year
    const fits = car.candidates.filter(c => c.availableFrom != null && Number(c.availableFrom.slice(0, 4)) <= y
        && (c.availableTo == null || Number(c.availableTo.slice(0, 4)) >= y))
    const pick = fits.length > 1 ? fits.filter(c => c.diffKwh === 0) : fits
    return pick.length === 1 ? pick[0].specId : null
}
const suggested = ref<Record<string, string>>({})

onMounted(async () => {
    try {
        cars.value = await adminVehicleSpecService.carsOfSpec(props.row.id)
        for (const car of cars.value) {
            const id = suggestion(car)
            if (id) suggested.value[car.carId] = id
            target.value[car.carId] = id ?? ''
        }
    } catch (err) {
        error.value = apiErrorText(err, 'Autos konnten nicht geladen werden.')
    } finally {
        loading.value = false
    }
})

async function assign(car: CarForReassign) {
    const specId = target.value[car.carId]
    if (!specId) return
    busyCar.value = car.carId
    error.value = ''
    try {
        await adminVehicleSpecService.assignCar(car.carId, specId)
        cars.value = cars.value.filter(c => c.carId !== car.carId)
        emit('changed')
    } catch (err) {
        error.value = apiErrorText(err, 'Umhängen fehlgeschlagen.')
    } finally {
        busyCar.value = null
    }
}

const period = (c: Candidate) => `${c.availableFrom?.slice(0, 4) ?? '?'}-${c.availableTo?.slice(0, 4) ?? ''}`
const label = (c: Candidate) => `${c.variantName ?? 'ohne Name'} · ${c.netKwh ?? '?'} kWh · ${period(c)}`
/** Wie viele Wörter vom Trim des Autos im Namen oder Trim der Zeile vorkommen (Groß/klein egal). */
function trimHits(car: CarForReassign, c: Candidate): number {
    const words = new Set(`${c.variantName ?? ''} ${c.trimLevel ?? ''}`.toLowerCase().split(/\s+/).filter(Boolean))
    return new Set((car.trim ?? '').toLowerCase().split(/\s+/).filter(w => words.has(w))).size
}
/** Dropdown-Reihenfolge: Zeilen mit Trim-Treffern zuerst (meiste Treffer oben), dann der Rest; Gruppen nur, wenn es beide gibt. */
function candidateGroups(car: CarForReassign): { label: string | null; items: Candidate[] }[] {
    const hits = new Map(car.candidates.map(c => [c.specId, trimHits(car, c)]))
    const matching = car.candidates.filter(c => hits.get(c.specId)! > 0).sort((x, y) => hits.get(y.specId)! - hits.get(x.specId)!)
    const rest = car.candidates.filter(c => hits.get(c.specId) === 0)
    return matching.length && rest.length
        ? [{ label: 'Trim passt', items: matching }, { label: 'andere Zeilen', items: rest }]
        : [{ label: null, items: [...matching, ...rest] }]
}
const netFits = (car: CarForReassign) => car.currentNetKwh == null || props.row.netBatteryCapacityKwh == null ? null
    : Number(car.currentNetKwh) === Number(props.row.netBatteryCapacityKwh)
</script>

<template>
  <div class="mt-2 pt-1 border-t border-gray-800 text-xs">
    <p v-if="error" class="text-red-400 py-1" role="alert">{{ error }}</p>
    <p v-if="loading" class="text-gray-500 py-1">Lädt …</p>
    <p v-else-if="cars.length === 0" class="text-gray-500 py-1">Keine aktiven Autos an dieser Zeile.</p>
    <ul v-else>
      <li v-for="car in cars" :key="car.carId" data-car class="flex flex-wrap gap-x-3 gap-y-1 py-1 border-t border-gray-800/60 first:border-t-0 text-gray-300" :class="SPEC_GRID">
        <span class="min-w-0">
          <select v-if="car.candidates.length" v-model="target[car.carId]" aria-label="umhängen auf" class="w-full bg-gray-800 border border-gray-700 rounded-sm px-2 py-1 text-xs text-gray-100">
            <option value="" disabled>umhängen auf …</option>
            <template v-for="g in candidateGroups(car)" :key="g.label ?? 'alle'">
              <optgroup v-if="g.label" :label="g.label">
                <option v-for="c in g.items" :key="c.specId" :value="c.specId">{{ label(c) }}{{ suggested[car.carId] === c.specId ? ' · passt zum Baujahr' : '' }}</option>
              </optgroup>
              <template v-else>
                <option v-for="c in g.items" :key="c.specId" :value="c.specId">{{ label(c) }}{{ suggested[car.carId] === c.specId ? ' · passt zum Baujahr' : '' }}</option>
              </template>
            </template>
          </select>
          <span v-else class="text-gray-500">keine andere Zeile dieses Modells</span>
        </span>
        <span data-trim class="truncate"><template v-if="car.trim"><template v-for="(p, pi) in wordParts(car.trim, row.trimLevel ?? '')" :key="pi">{{ pi > 0 ? ' ' : '' }}<span :class="p.cls">{{ p.text }}</span></template></template><template v-else>–</template></span>
        <span data-net :class="fitClass(netFits(car))">{{ car.currentNetKwh == null ? '–' : `${car.currentNetKwh} kWh` }}</span>
        <span></span>
        <span data-year :class="fitClass(yearFits(car.year, row.availableFrom, row.availableTo))">{{ car.year ?? '?' }}</span>
        <span class="text-gray-500">{{ car.logCount }} Ladevorgänge</span>
        <span class="lg:text-right">
          <button v-if="car.candidates.length" type="button" class="px-2 py-1 bg-indigo-600 hover:bg-indigo-500 rounded-sm text-white disabled:opacity-40"
                  :disabled="!target[car.carId] || busyCar === car.carId" @click="assign(car)">Umhängen</button>
        </span>
      </li>
    </ul>
  </div>
</template>
