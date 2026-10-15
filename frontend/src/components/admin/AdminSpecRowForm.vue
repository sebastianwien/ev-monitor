<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { carService, type BrandInfo, type ModelInfo } from '../../api/carService'
import { adminVehicleSpecService, apiErrorText, type SaveResult, type SpecCommand, type SpecRow } from '../../api/adminVehicleSpecService'

/**
 * Anlegen oder Bearbeiten einer Spec-Zeile. Marke und Modell nur aus dem Enum (Dropdown), nie Freitext.
 * Die Regeln prüft der Server; jeder Fehler kommt als Feldtext zurück und wird hier angezeigt.
 */
const props = defineProps<{ row: SpecRow | null; presetModel: string | null; presetBrand: string | null }>()
const emit = defineEmits<{ saved: [result: SaveResult]; cancel: [] }>()

const brands = ref<BrandInfo[]>([])
const models = ref<ModelInfo[]>([])
const brand = ref(props.row?.carBrand ?? props.presetBrand ?? '')
const busy = ref(false)
const error = ref('')

const form = ref<SpecCommand>({
    model: props.row?.carModel ?? props.presetModel ?? '',
    batteryCapacityKwh: props.row?.batteryCapacityKwh ?? null,
    netBatteryCapacityKwh: props.row?.netBatteryCapacityKwh ?? null,
    officialRangeKm: props.row?.officialRangeKm ?? null,
    officialConsumptionKwhPer100km: props.row?.officialConsumptionKwhPer100km ?? null,
    variantName: props.row?.variantName ?? '',
    trimLevel: props.row?.trimLevel ?? '',
    availableFrom: props.row?.availableFrom ?? null,
    availableTo: props.row?.availableTo ?? null,
    sourceUrl: props.row?.sourceUrl ?? '',
    ratingSource: props.row?.ratingSource ?? 'WLTP',
})

async function loadModels() {
    models.value = brand.value ? await carService.getModelsForBrand(brand.value) : []
    if (!models.value.some(m => m.value === form.value.model)) form.value.model = ''
}

onMounted(async () => {
    try {
        brands.value = (await carService.getBrands()).filter(b => b.value !== 'SONSTIGE')
        await loadModels()
    } catch {
        error.value = 'Marken konnten nicht geladen werden.'
    }
})
watch(brand, loadModels)

async function submit() {
    busy.value = true
    error.value = ''
    try {
        const result = props.row
            ? await adminVehicleSpecService.update(props.row.id, form.value)
            : await adminVehicleSpecService.create(form.value)
        emit('saved', result)
    } catch (err) {
        error.value = apiErrorText(err, 'Speichern fehlgeschlagen.')
    } finally {
        busy.value = false
    }
}
</script>

<template>
  <form class="bg-gray-800 rounded-sm p-4 space-y-3" @submit.prevent="submit">
    <h3 class="font-semibold">{{ row ? 'Zeile bearbeiten' : 'Neue Zeile' }}</h3>

    <div class="grid gap-3 sm:grid-cols-2">
      <label class="block text-sm">
        <span class="text-gray-400">Marke</span>
        <select v-model="brand" class="mt-1 w-full bg-gray-900 rounded-sm px-2 py-1.5" :disabled="!!row">
          <option value="">Bitte wählen</option>
          <option v-for="b in brands" :key="b.value" :value="b.value">{{ b.label }}</option>
        </select>
      </label>
      <label class="block text-sm">
        <span class="text-gray-400">Modell</span>
        <select v-model="form.model" class="mt-1 w-full bg-gray-900 rounded-sm px-2 py-1.5" :disabled="!!row">
          <option value="">Bitte wählen</option>
          <option v-for="m in models" :key="m.value" :value="m.value">{{ m.label }}</option>
        </select>
      </label>
      <label class="block text-sm">
        <span class="text-gray-400">Name der Variante</span>
        <input v-model="form.variantName" maxlength="100" class="mt-1 w-full bg-gray-900 rounded-sm px-2 py-1.5" placeholder="z. B. Long Range AWD (2024-)" />
      </label>
      <label class="block text-sm">
        <span class="text-gray-400">Trim (Pflicht bei neuen Zeilen)</span>
        <input v-model="form.trimLevel" maxlength="40" class="mt-1 w-full bg-gray-900 rounded-sm px-2 py-1.5" placeholder="z. B. Long Range, Pro, GTX" />
      </label>
      <label class="block text-sm">
        <span class="text-gray-400">Brutto kWh</span>
        <input v-model.number="form.batteryCapacityKwh" type="number" step="0.1" min="1" class="mt-1 w-full bg-gray-900 rounded-sm px-2 py-1.5" />
      </label>
      <label class="block text-sm">
        <span class="text-gray-400">Netto kWh</span>
        <input v-model.number="form.netBatteryCapacityKwh" type="number" step="0.1" min="1" class="mt-1 w-full bg-gray-900 rounded-sm px-2 py-1.5" />
      </label>
      <label class="block text-sm">
        <span class="text-gray-400">{{ form.ratingSource }} Reichweite km</span>
        <input v-model.number="form.officialRangeKm" type="number" step="1" min="1" class="mt-1 w-full bg-gray-900 rounded-sm px-2 py-1.5" />
      </label>
      <label class="block text-sm">
        <span class="text-gray-400">{{ form.ratingSource }} Verbrauch kWh/100 km</span>
        <input v-model.number="form.officialConsumptionKwhPer100km" type="number" step="0.1" min="1" class="mt-1 w-full bg-gray-900 rounded-sm px-2 py-1.5" />
      </label>
      <label class="block text-sm">
        <span class="text-gray-400">Verfügbar ab (Pflicht bei neuen Zeilen)</span>
        <input v-model="form.availableFrom" type="date" class="mt-1 w-full bg-gray-900 rounded-sm px-2 py-1.5" />
      </label>
      <label class="block text-sm">
        <span class="text-gray-400">Verfügbar bis (leer = läuft noch)</span>
        <input v-model="form.availableTo" type="date" class="mt-1 w-full bg-gray-900 rounded-sm px-2 py-1.5" />
      </label>
      <label class="block text-sm sm:col-span-2">
        <span class="text-gray-400">Quelle (https, z. B. ev-database.org)</span>
        <input v-model="form.sourceUrl" type="url" maxlength="500" class="mt-1 w-full bg-gray-900 rounded-sm px-2 py-1.5" placeholder="https://ev-database.org/car/..." />
      </label>
      <label class="block text-sm">
        <span class="text-gray-400">Norm</span>
        <select v-model="form.ratingSource" class="mt-1 w-full bg-gray-900 rounded-sm px-2 py-1.5">
          <option value="WLTP">WLTP</option>
          <option value="EPA">EPA</option>
        </select>
      </label>
    </div>

    <p v-if="error" class="text-sm text-red-400" role="alert">{{ error }}</p>

    <div class="flex gap-2">
      <button type="submit" class="px-3 py-1.5 bg-indigo-600 hover:bg-indigo-500 rounded-sm text-sm disabled:opacity-40" :disabled="busy">Speichern</button>
      <button type="button" class="px-3 py-1.5 bg-gray-700 rounded-sm text-sm" @click="emit('cancel')">Abbrechen</button>
    </div>
  </form>
</template>
