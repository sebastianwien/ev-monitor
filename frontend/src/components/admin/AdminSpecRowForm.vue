<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { carService, type BrandInfo, type ModelInfo } from '../../api/carService'
import { adminVehicleSpecService, apiErrorText, type SaveResult, type SpecCommand, type SpecRow } from '../../api/adminVehicleSpecService'

/**
 * Inspektor zum Anlegen einer Spec-Zeile (unter lg auch zum Bearbeiten). Marke und Modell nur aus dem Enum, nie Freitext.
 * `template` ist die neueste Zeile des Modells: Trim, Akku und Normwerte werden übernommen, Name, Zeitraum und Quelle bleiben leer.
 * Die Regeln prüft der Server; jeder Fehler kommt als Feldtext zurück und wird hier angezeigt.
 */
const props = withDefaults(defineProps<{ row: SpecRow | null; presetModel: string | null; presetBrand: string | null; template?: SpecRow | null }>(), { template: null })
const base = props.row ?? props.template
const emit = defineEmits<{ saved: [result: SaveResult]; cancel: [] }>()

const brands = ref<BrandInfo[]>([])
const models = ref<ModelInfo[]>([])
const brand = ref(props.row?.carBrand ?? props.presetBrand ?? '')
const busy = ref(false)
const error = ref('')

const form = ref<SpecCommand>({
    model: props.row?.carModel ?? props.presetModel ?? '',
    batteryCapacityKwh: base?.batteryCapacityKwh ?? null,
    netBatteryCapacityKwh: base?.netBatteryCapacityKwh ?? null,
    officialRangeKm: base?.officialRangeKm ?? null,
    officialConsumptionKwhPer100km: base?.officialConsumptionKwhPer100km ?? null,
    variantName: props.row?.variantName ?? '',
    trimLevel: base?.trimLevel ?? '',
    availableFrom: props.row?.availableFrom ?? null,
    availableTo: props.row?.availableTo ?? null,
    sourceUrl: props.row?.sourceUrl ?? '',
    ratingSource: base?.ratingSource ?? 'WLTP',
    dcMaxPowerKw: base?.dcMaxPowerKw ?? null,
    dcAvgPower1080Kw: base?.dcAvgPower1080Kw ?? null,
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
    return modelLabel.toLowerCase().startsWith(brandLabel.toLowerCase()) ? modelLabel : `${brandLabel} ${modelLabel}`
})

const field = 'w-full bg-gray-900 border border-gray-700 rounded-sm px-3 py-2 text-base text-gray-100 placeholder:text-gray-600 focus:outline-none focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500'
/** Einspaltig: Label links fest breit, Wert rechts; auf schmalen Screens untereinander. */
const rowCls = 'grid gap-1 sm:grid-cols-[10rem_1fr] sm:items-center'
const label = 'text-sm font-medium text-gray-300'
const unit = 'pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-sm text-gray-500'

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
  <form class="bg-gray-800 rounded-sm p-4 sm:p-6 space-y-4 max-w-2xl" @submit.prevent="submit">
    <div class="flex items-baseline justify-between gap-3">
      <h3 class="text-lg font-semibold">{{ row ? 'Zeile bearbeiten' : 'Neue Zeile' }}</h3>
      <p v-if="!row" class="text-xs text-gray-500"><span class="text-indigo-300">*</span> Pflicht<template v-if="template"> · vorbelegt aus {{ template.variantName ?? template.trimLevel ?? 'der neuesten Zeile' }}</template></p>
    </div>

    <div class="space-y-3">
      <p v-if="fixedVehicle" :class="rowCls"><span :class="label">Fahrzeug</span><span class="text-base font-medium text-gray-100">{{ fixedVehicle }}</span></p>
      <template v-else>
        <label :class="rowCls"><span :class="label">Marke <span class="text-indigo-300">*</span></span>
          <select v-model="brand" :class="field">
            <option value="">Bitte wählen</option>
            <option v-for="b in brands" :key="b.value" :value="b.value">{{ b.label }}</option>
          </select>
        </label>
        <label :class="rowCls"><span :class="label">Modell <span class="text-indigo-300">*</span></span>
          <select v-model="form.model" :class="field" :disabled="!brand">
            <option value="">Bitte wählen</option>
            <option v-for="m in models" :key="m.value" :value="m.value">{{ m.label }}</option>
          </select>
        </label>
      </template>

      <label :class="rowCls"><span :class="label">Trim <span v-if="!row" class="text-indigo-300">*</span></span>
        <input v-model="form.trimLevel" maxlength="40" :class="field" placeholder="Long Range, Pro, GTX" />
      </label>
      <label :class="rowCls"><span :class="label">Name der Variante</span>
        <input v-model="form.variantName" maxlength="100" :class="field" placeholder="Long Range AWD (2024-)" />
      </label>

      <label :class="rowCls"><span :class="label">Brutto</span>
        <span class="relative block"><input v-model.number="form.batteryCapacityKwh" type="number" inputmode="decimal" step="0.1" min="1" :class="field + ' pr-12'" /><span :class="unit">kWh</span></span>
      </label>
      <label :class="rowCls"><span :class="label">Netto</span>
        <span class="relative block"><input v-model.number="form.netBatteryCapacityKwh" type="number" inputmode="decimal" step="0.1" min="1" :class="field + ' pr-12'" /><span :class="unit">kWh</span></span>
      </label>

      <div :class="rowCls"><span :class="label">Norm</span>
        <div class="inline-flex rounded-sm border border-gray-700 overflow-hidden" role="radiogroup" aria-label="Norm">
          <button v-for="n in ['WLTP', 'EPA']" :key="n" type="button" role="radio" :aria-checked="form.ratingSource === n"
                  class="px-4 py-2 text-sm font-medium transition-colors"
                  :class="form.ratingSource === n ? 'bg-indigo-600 text-white' : 'bg-gray-900 text-gray-400 hover:text-gray-200'"
                  @click="form.ratingSource = n as SpecCommand['ratingSource']">{{ n }}</button>
        </div>
      </div>
      <label :class="rowCls"><span :class="label">Reichweite</span>
        <span class="relative block"><input v-model.number="form.officialRangeKm" type="number" inputmode="numeric" step="1" min="1" :class="field + ' pr-10'" /><span :class="unit">km</span></span>
      </label>
      <label :class="rowCls"><span :class="label">Verbrauch</span>
        <span class="relative block"><input v-model.number="form.officialConsumptionKwhPer100km" type="number" inputmode="decimal" step="0.1" min="1" :class="field + ' pr-24'" /><span :class="unit + ' text-xs'">kWh/100 km</span></span>
      </label>

      <label :class="rowCls"><span :class="label">DC max</span>
        <span class="relative block"><input v-model.number="form.dcMaxPowerKw" type="number" inputmode="decimal" step="0.1" min="1" max="1000" :class="field + ' pr-10'" /><span :class="unit">kW</span></span>
      </label>
      <label :class="rowCls"><span :class="label">DC 10-80 %</span>
        <span class="relative block"><input v-model.number="form.dcAvgPower1080Kw" type="number" inputmode="decimal" step="0.1" min="1" max="1000" :class="field + ' pr-10'" /><span :class="unit">kW</span></span>
      </label>

      <label :class="rowCls"><span :class="label">Ab <span v-if="!row" class="text-indigo-300">*</span></span>
        <input v-model="form.availableFrom" type="date" :class="field + ' text-sm'" />
      </label>
      <label :class="rowCls"><span :class="label">Bis</span>
        <span class="block"><input v-model="form.availableTo" type="date" :class="field + ' text-sm'" /><span class="mt-1 block text-xs text-gray-500">leer = läuft noch</span></span>
      </label>

      <label :class="rowCls"><span :class="label">Quelle</span>
        <input v-model="form.sourceUrl" type="url" maxlength="500" :class="field" placeholder="https://ev-database.org/car/..." />
      </label>
    </div>

    <p v-if="error" class="text-sm text-red-400 border border-red-900 bg-red-950/60 rounded-sm px-3 py-2" role="alert">{{ error }}</p>

    <div class="flex gap-2 pt-2 border-t border-gray-700">
      <button type="submit" class="min-h-11 px-5 bg-indigo-600 hover:bg-indigo-500 rounded-sm text-sm font-medium disabled:opacity-40" :disabled="busy">Speichern</button>
      <button type="button" class="min-h-11 px-4 bg-gray-700 hover:bg-gray-600 rounded-sm text-sm" @click="emit('cancel')">Abbrechen</button>
    </div>
  </form>
</template>
