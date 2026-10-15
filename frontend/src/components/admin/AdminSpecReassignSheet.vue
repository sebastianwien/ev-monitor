<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { adminVehicleSpecService, apiErrorText, type CarForReassign } from '../../api/adminVehicleSpecService'

/**
 * Autos eines Modells ohne Spec-Zeile, je Auto der Diff zu jeder Kandidaten-Zeile.
 * Umhängen ist eine Aktion je Auto, nie ein Bulk-Update: das Netto ändert Verbrauch, Phantom Drain und SoH.
 */
const props = defineProps<{ model: string; displayName: string }>()
const emit = defineEmits<{ close: []; changed: [] }>()

const cars = ref<CarForReassign[]>([])
const loading = ref(true)
const error = ref('')
const busyCar = ref<string | null>(null)

async function load() {
    loading.value = true
    error.value = ''
    try {
        cars.value = await adminVehicleSpecService.reassignCandidates(props.model)
    } catch (err) {
        error.value = apiErrorText(err, 'Autos konnten nicht geladen werden.')
    } finally {
        loading.value = false
    }
}
onMounted(load)

async function assign(carId: string, specId: string) {
    busyCar.value = carId
    error.value = ''
    try {
        await adminVehicleSpecService.assignCar(carId, specId)
        cars.value = cars.value.filter(c => c.carId !== carId)
        emit('changed')
    } catch (err) {
        error.value = apiErrorText(err, 'Umhängen fehlgeschlagen.')
    } finally {
        busyCar.value = null
    }
}

const signed = (n: number | null) => n == null ? '?' : (n > 0 ? '+' : '') + n.toLocaleString('de-DE', { maximumFractionDigits: 1 })
const diffClass = (d: number | null) => d == null ? 'text-gray-400' : Math.abs(d) === 0 ? 'text-green-400' : Math.abs(d) <= 1 ? 'text-amber-300' : 'text-red-400'
</script>

<template>
  <section class="bg-gray-800 rounded-sm p-4 space-y-3">
    <header class="flex items-center justify-between gap-2">
      <h3 class="font-semibold">Autos umhängen: {{ displayName }}</h3>
      <button type="button" class="px-3 py-1 bg-gray-700 rounded-sm text-sm" @click="emit('close')">Schließen</button>
    </header>
    <p class="text-xs text-gray-400">
      Nach dem Umhängen gilt das Netto der Zeile statt der eigenen Angabe. Bei Abweichung über 1 kWh verschieben sich Verbrauch, Phantom Drain und SoH des Autos.
    </p>
    <p v-if="error" class="text-sm text-red-400" role="alert">{{ error }}</p>
    <p v-if="loading" class="text-sm text-gray-500">Lädt …</p>
    <p v-else-if="cars.length === 0" class="text-sm text-gray-500">Alle Autos dieses Modells hängen an einer Zeile.</p>

    <ul class="space-y-3">
      <li v-for="car in cars" :key="car.carId" class="bg-gray-900 rounded-sm p-3 space-y-2">
        <p class="text-sm">
          <span class="font-medium">{{ car.year ?? '?' }} {{ car.trim ?? '' }}</span>
          <span class="text-gray-400"> · eigene Angabe {{ car.currentNetKwh ?? '?' }} kWh · {{ car.logCount }} Ladevorgänge</span>
        </p>
        <div class="flex flex-wrap gap-2">
          <button v-for="c in car.candidates" :key="c.specId" type="button"
                  class="px-3 py-1.5 bg-gray-800 hover:bg-gray-700 rounded-sm text-sm text-left disabled:opacity-40"
                  :disabled="busyCar === car.carId" @click="assign(car.carId, c.specId)">
            Umhängen auf {{ c.variantName ?? c.trimLevel ?? 'ohne Name' }}
            <span class="text-gray-400">({{ c.netKwh ?? '?' }} kWh, {{ c.availableFrom?.slice(0, 4) ?? '?' }}-{{ c.availableTo?.slice(0, 4) ?? '' }})</span>
            <span :class="diffClass(c.diffKwh)"> {{ signed(c.netKwh != null && car.currentNetKwh != null ? c.netKwh - car.currentNetKwh : null) }} kWh</span>
          </button>
        </div>
      </li>
    </ul>
  </section>
</template>
