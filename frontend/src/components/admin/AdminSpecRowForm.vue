<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
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

/** Beim Bearbeiten sind Marke und Modell fest; dann als Text statt als gesperrte Dropdowns. */
const fixedVehicle = computed(() => {
    if (!props.row) return null
    const brandLabel = brands.value.find(b => b.value === props.row?.carBrand)?.label ?? props.row.carBrand
    const modelLabel = models.value.find(m => m.value === props.row?.carModel)?.label ?? props.row.carModel
    return `${brandLabel} ${modelLabel}`
})

const field = 'mt-1 w-full bg-gray-900 border border-gray-700 rounded-sm px-3 py-2 text-base text-gray-100 placeholder:text-gray-600 focus:outline-none focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500'
const label = 'block text-sm font-medium text-gray-300'
const legend = 'text-xs font-semibold uppercase tracking-wide text-indigo-300'
const unit = 'pointer-events-none absolute right-3 bottom-2.5 text-sm text-gray-500'

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
  <form class="bg-gray-800 rounded-sm p-4 sm:p-6 space-y-6" @submit.prevent="submit">
    <div class="flex items-baseline justify-between gap-3">
      <h3 class="text-lg font-semibold">{{ row ? 'Zeile bearbeiten' : 'Neue Zeile' }}</h3>
      <p v-if="!row" class="text-xs text-gray-500"><span class="text-indigo-300">*</span> Pflicht</p>
    </div>

    <fieldset class="space-y-3">
      <legend :class="legend">Fahrzeug</legend>
      <p v-if="fixedVehicle" class="text-base font-medium text-gray-100">{{ fixedVehicle }}</p>
      <div v-else class="grid gap-3 sm:grid-cols-2">
        <label :class="label">Marke <span class="text-indigo-300">*</span>
          <select v-model="brand" :class="field">
            <option value="">Bitte wählen</option>
            <option v-for="b in brands" :key="b.value" :value="b.value">{{ b.label }}</option>
          </select>
        </label>
        <label :class="label">Modell <span class="text-indigo-300">*</span>
          <select v-model="form.model" :class="field" :disabled="!brand">
            <option value="">Bitte wählen</option>
            <option v-for="m in models" :key="m.value" :value="m.value">{{ m.label }}</option>
          </select>
        </label>
      </div>
      <div class="grid gap-3 sm:grid-cols-2">
        <label :class="label">Trim <span v-if="!row" class="text-indigo-300">*</span>
          <input v-model="form.trimLevel" maxlength="40" :class="field" placeholder="Long Range, Pro, GTX" />
        </label>
        <label :class="label">Name der Variante
          <input v-model="form.variantName" maxlength="100" :class="field" placeholder="Long Range AWD (2024-)" />
        </label>
      </div>
    </fieldset>

    <fieldset class="space-y-3">
      <legend :class="legend">Akku</legend>
      <div class="grid grid-cols-2 gap-3 sm:max-w-sm">
        <label :class="label + ' relative'">Brutto
          <input v-model.number="form.batteryCapacityKwh" type="number" inputmode="decimal" step="0.1" min="1" :class="field + ' pr-12'" />
          <span :class="unit">kWh</span>
        </label>
        <label :class="label + ' relative'">Netto
          <input v-model.number="form.netBatteryCapacityKwh" type="number" inputmode="decimal" step="0.1" min="1" :class="field + ' pr-12'" />
          <span :class="unit">kWh</span>
        </label>
      </div>
    </fieldset>

    <fieldset class="space-y-3">
      <legend :class="legend">Normwerte</legend>
      <div class="grid grid-cols-2 gap-3 sm:grid-cols-3 sm:max-w-xl">
        <div class="col-span-2 sm:col-span-1">
          <span :class="label">Norm</span>
          <div class="mt-1 inline-flex rounded-sm border border-gray-700 overflow-hidden" role="radiogroup" aria-label="Norm">
            <button v-for="n in ['WLTP', 'EPA']" :key="n" type="button" role="radio" :aria-checked="form.ratingSource === n"
                    class="px-4 py-2 text-sm font-medium transition-colors"
                    :class="form.ratingSource === n ? 'bg-indigo-600 text-white' : 'bg-gray-900 text-gray-400 hover:text-gray-200'"
                    @click="form.ratingSource = n as SpecCommand['ratingSource']">{{ n }}</button>
          </div>
        </div>
        <label :class="label + ' relative'">Reichweite
          <input v-model.number="form.officialRangeKm" type="number" inputmode="numeric" step="1" min="1" :class="field + ' pr-10'" />
          <span :class="unit">km</span>
        </label>
        <label :class="label + ' relative'">Verbrauch
          <input v-model.number="form.officialConsumptionKwhPer100km" type="number" inputmode="decimal" step="0.1" min="1" :class="field + ' pr-20'" />
          <span :class="unit + ' text-xs'">kWh/100 km</span>
        </label>
      </div>
    </fieldset>

    <fieldset class="space-y-3">
      <legend :class="legend">Bauzeitraum</legend>
      <div class="grid grid-cols-2 gap-3 sm:max-w-sm">
        <label :class="label">Ab <span v-if="!row" class="text-indigo-300">*</span>
          <input v-model="form.availableFrom" type="date" :class="field" />
        </label>
        <label :class="label">Bis
          <input v-model="form.availableTo" type="date" :class="field" />
          <span class="mt-1 block text-xs font-normal text-gray-500">leer = läuft noch</span>
        </label>
      </div>
    </fieldset>

    <fieldset class="space-y-3">
      <legend :class="legend">Quelle</legend>
      <label :class="label">Link zum Datenblatt
        <input v-model="form.sourceUrl" type="url" maxlength="500" :class="field" placeholder="https://ev-database.org/car/..." />
      </label>
    </fieldset>

    <p v-if="error" class="text-sm text-red-400 border border-red-900 bg-red-950/60 rounded-sm px-3 py-2" role="alert">{{ error }}</p>

    <div class="flex gap-2 pt-2 border-t border-gray-700">
      <button type="submit" class="min-h-11 px-5 bg-indigo-600 hover:bg-indigo-500 rounded-sm text-sm font-medium disabled:opacity-40" :disabled="busy">Speichern</button>
      <button type="button" class="min-h-11 px-4 bg-gray-700 hover:bg-gray-600 rounded-sm text-sm" @click="emit('cancel')">Abbrechen</button>
    </div>
  </form>
</template>
