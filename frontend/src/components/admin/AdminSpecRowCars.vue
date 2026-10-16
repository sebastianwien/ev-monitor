<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { adminVehicleSpecService, apiErrorText, type Candidate, type CarForReassign } from '../../api/adminVehicleSpecService'

/**
 * Autos einer Spec-Zeile, aufgeklappt unter der Zeile. Umhängen je Auto auf eine andere Zeile desselben Modells,
 * nie als Bulk: das Netto der neuen Zeile ändert Verbrauch, Phantom Drain und SoH des Autos.
 */
const props = defineProps<{ specId: string }>()
const emit = defineEmits<{ changed: [] }>()

const cars = ref<CarForReassign[]>([])
const target = ref<Record<string, string>>({})
const loading = ref(true)
const error = ref('')
const busyCar = ref<string | null>(null)

onMounted(async () => {
    try {
        cars.value = await adminVehicleSpecService.carsOfSpec(props.specId)
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

const period = (c: Candidate) => `${c.availableFrom?.slice(0, 7) ?? '?'} bis ${c.availableTo?.slice(0, 7) ?? 'offen'}`
const label = (c: Candidate) => `${c.variantName ?? 'ohne Name'} · ${c.netKwh ?? '?'} kWh · ${period(c)}`
const cell = 'py-1 pr-3 whitespace-nowrap'
</script>

<template>
  <div class="mt-2 pt-2 border-t border-gray-800 text-xs space-y-1">
    <p v-if="error" class="text-red-400" role="alert">{{ error }}</p>
    <p v-if="loading" class="text-gray-500">Lädt …</p>
    <p v-else-if="cars.length === 0" class="text-gray-500">Keine aktiven Autos an dieser Zeile.</p>
    <table v-else class="text-left">
      <thead class="text-gray-500">
        <tr><th :class="cell" class="font-normal">Baujahr</th><th :class="cell" class="font-normal">Trim</th><th :class="cell" class="font-normal">eigenes Netto</th>
          <th :class="cell" class="font-normal">Ladevorgänge</th><th :class="cell" class="font-normal">umhängen auf</th><th></th></tr>
      </thead>
      <tbody>
        <tr v-for="car in cars" :key="car.carId" data-car class="border-t border-gray-800 text-gray-300">
          <td :class="cell">{{ car.year ?? '?' }}</td>
          <td :class="cell">{{ car.trim ?? '–' }}</td>
          <td :class="cell">{{ car.currentNetKwh == null ? '–' : `${car.currentNetKwh} kWh` }}</td>
          <td :class="cell">{{ car.logCount }}</td>
          <template v-if="car.candidates.length">
            <td :class="cell">
              <select v-model="target[car.carId]" aria-label="Ziel-Zeile" class="bg-gray-800 border border-gray-600 rounded-sm px-2 py-1 text-xs text-gray-100">
                <option :value="undefined" disabled>Zeile wählen</option>
                <option v-for="c in car.candidates" :key="c.specId" :value="c.specId">{{ label(c) }}</option>
              </select>
            </td>
            <td class="py-1">
              <button type="button" class="px-2 py-1 bg-indigo-600 hover:bg-indigo-500 rounded-sm text-white disabled:opacity-40"
                      :disabled="!target[car.carId] || busyCar === car.carId" @click="assign(car)">Umhängen</button>
            </td>
          </template>
          <td v-else colspan="2" :class="cell" class="text-gray-500">keine andere Zeile dieses Modells</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
