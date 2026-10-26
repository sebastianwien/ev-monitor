<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { buildOptionLabels } from '../../utils/variantOptionLabels'
import { monthNames } from '../../utils/monthNames'
import type { Car, CapacityOption, ModelInfo } from '../../api/carService'
import type { TrimGroup } from '../../composables/useCarForm'

/**
 * Felder des Fahrzeugformulars, einmal fuer Anlegen und Bearbeiten. Der Zustand lebt
 * weiter in `useCarForm`; die View reicht ihn als `reactive({...})` aus den Refs herein,
 * damit v-model hier direkt auf dieselben Refs schreibt (ein Formular, ein Zustand).
 */
export interface CarFormState {
  editingCar: Car | null
  selectedBrand: string
  selectedModel: string
  year: number | null
  month: number | null
  licensePlate: string
  trim: string
  selectedCapacity: number | null
  selectedSpecId: string | null
  selectedTrimLevel: string | null
  customCapacity: number | null
  useCustomCapacity: boolean
  powerKw: number | null
  batteryDegradationPercent: number | null
  hasHeatPump: boolean
  isBusinessCar: boolean
  capacityWasCorrected: boolean
  sortedBrands: { value: string; label: string }[]
  availableModels: ModelInfo[]
  isSonstige: boolean
  selectedModelCapacities: CapacityOption[]
  finalCapacity: number | null
  powerPs: number | null
  isGroupedByTrim: boolean
  trimGroups: TrimGroup[]
  visibleOptionsForTrim: CapacityOption[]
  selectTrimGroup: (trimLevel: string) => void
  getModelLabel: (model: string) => string
}

const props = defineProps<{
  form: CarFormState
  submitting: boolean
}>()
const emit = defineEmits<{ submit: []; cancel: []; missing: [] }>()

const { t, locale } = useI18n()
const monthOptions = computed(() => monthNames(locale.value))

// Zeitraum als Label; bei gleichem Zeitraum zusaetzlich die Variante (sonst ununterscheidbar)
const trimOptionLabels = computed(() =>
  buildOptionLabels(props.form.visibleOptionsForTrim, props.form.getModelLabel(props.form.selectedModel), t('cars.since')))

const VARIANT_FILTER_THRESHOLD = 8
const variantFilter = ref('')
watch(() => props.form.selectedModel, () => { variantFilter.value = '' })

const filteredCapacities = computed(() => {
  const all = props.form.selectedModelCapacities
  if (all.length <= VARIANT_FILTER_THRESHOLD) return all
  const q = variantFilter.value.trim().toLowerCase()
  if (!q) return all
  return all.filter(c => (c.variantName?.toLowerCase().includes(q) ?? false) || String(c.kWh).includes(q))
})

function pick(option: CapacityOption) {
  props.form.selectedCapacity = option.kWh
  props.form.selectedSpecId = option.vehicleSpecificationId
}

const inputClass = 'w-full rounded-xl border-gray-300 dark:border-gray-600 focus:border-green-600 focus:ring-green-600 p-2 border dark:bg-gray-700 dark:text-gray-100'
const labelClass = 'block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1'
const chipOn = 'btn-3d cta-shadow-sm active border-2 border-gray-900 bg-gray-900 text-white dark:border-gray-100 dark:bg-gray-100 dark:text-gray-900'
const chipOff = 'btn-3d cta-shadow-sm border-2 border-edge bg-white text-gray-800 hover:bg-gray-50 dark:bg-white/10 dark:text-gray-200 dark:hover:bg-white/15'
</script>

<template>
  <form @submit.prevent="emit('submit')" class="space-y-4">
    <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
      <div>
        <label :class="labelClass">{{ t('cars.label_brand') }}</label>
        <select v-model="form.selectedBrand" required :class="inputClass">
          <option value="">{{ t('cars.select_brand') }}</option>
          <option v-for="brand in form.sortedBrands" :key="brand.value" :value="brand.value">{{ brand.label }}</option>
        </select>
        <button type="button" @click="emit('missing')"
          class="mt-1 min-h-11 text-sm font-semibold text-gray-900 dark:text-gray-100 underline underline-offset-2">
          {{ t('cars.missing_brand_model_link') }}
        </button>
      </div>

      <div v-if="!form.isSonstige">
        <label :class="labelClass">{{ t('cars.label_model') }}</label>
        <select v-model="form.selectedModel" required :disabled="!form.selectedBrand"
          :class="[inputClass, 'disabled:bg-gray-100 dark:disabled:bg-gray-600']">
          <option value="">{{ form.selectedBrand ? t('cars.select_model') : t('cars.select_brand_first') }}</option>
          <option v-for="m in form.availableModels" :key="m.value" :value="m.value">{{ m.label }}</option>
        </select>
      </div>

      <div>
        <label :class="labelClass">{{ form.isSonstige ? t('cars.label_model') : t('cars.label_trim') }}</label>
        <input v-model="form.trim" type="text"
          :placeholder="form.isSonstige ? t('cars.trim_placeholder_sonstige') : t('cars.trim_placeholder')"
          :class="inputClass" />
        <p v-if="!form.isSonstige && !form.editingCar" class="text-xs text-gray-500 dark:text-gray-400 mt-1">{{ t('cars.hint_trim') }}</p>
      </div>

      <!-- Akku: immer eine Zeile der Variantenliste -->
      <div v-if="form.selectedModel" class="md:col-span-2">
        <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">{{ t('cars.label_capacity') }}</label>

        <div v-if="!form.useCustomCapacity" class="space-y-2">
          <template v-if="form.isGroupedByTrim">
            <div class="flex gap-2 flex-wrap">
              <button v-for="group in form.trimGroups" :key="group.trimLevel" type="button"
                @click="form.selectTrimGroup(group.trimLevel)"
                :class="['px-4 py-2 rounded-xl text-sm font-semibold transition-colors', form.selectedTrimLevel === group.trimLevel ? chipOn : chipOff]">
                {{ group.trimLevel }}
              </button>
            </div>
            <div v-if="form.visibleOptionsForTrim.length > 1" class="flex gap-2 flex-wrap pl-3 border-l-2 border-gray-200 dark:border-white/10">
              <button v-for="(option, index) in form.visibleOptionsForTrim" :key="option.vehicleSpecificationId ?? option.kWh" type="button"
                @click="pick(option)"
                :class="['px-3 py-1.5 rounded-xl text-sm font-semibold transition-colors',
                  form.selectedSpecId === option.vehicleSpecificationId ? chipOn : chipOff]">
                <span class="block text-left">{{ trimOptionLabels[index].primary }}</span>
                <span v-if="trimOptionLabels[index].secondary"
                  :class="['block text-left text-xs font-normal', form.selectedSpecId === option.vehicleSpecificationId ? 'opacity-80' : 'text-gray-500 dark:text-gray-400']">
                  {{ trimOptionLabels[index].secondary }}
                </span>
              </button>
            </div>
          </template>
          <template v-else>
            <input v-if="form.selectedModelCapacities.length > VARIANT_FILTER_THRESHOLD" v-model="variantFilter" type="text"
              :placeholder="t('cars.variant_filter_placeholder')" :class="[inputClass, 'text-sm']" />
            <div class="flex gap-2 flex-wrap">
              <button v-for="capacity in filteredCapacities" :key="capacity.vehicleSpecificationId ?? capacity.kWh" type="button"
                @click="pick(capacity)"
                :class="['px-4 py-2 rounded-xl text-sm font-semibold transition-colors',
                  (capacity.vehicleSpecificationId ? form.selectedSpecId === capacity.vehicleSpecificationId : form.selectedCapacity === capacity.kWh) ? chipOn : chipOff]">
                {{ capacity.variantName ? `${capacity.variantName} · ${capacity.kWh} kWh` : `${capacity.kWh} kWh` }}
              </button>
              <p v-if="filteredCapacities.length === 0" class="text-sm text-gray-500 dark:text-gray-400">{{ t('cars.variant_filter_no_results') }}</p>
            </div>
          </template>
          <button type="button" @click="emit('missing')"
            class="min-h-11 text-sm font-semibold text-gray-900 dark:text-gray-100 underline underline-offset-2">
            {{ t('cars.missing_battery_link') }}
          </button>
        </div>
        <p v-else class="text-sm text-gray-700 dark:text-gray-300">{{ t('cars.capacity_own_value', { kwh: form.customCapacity }) }}</p>
        <p v-if="form.capacityWasCorrected" class="mt-2 text-xs text-amber-700 dark:text-amber-400 bg-amber-50 dark:bg-amber-900/30 border border-amber-200 dark:border-amber-700 rounded px-3 py-2">
          {{ t('cars.capacity_corrected_hint', { old: form.editingCar?.customNetCapacityKwh, new: form.selectedCapacity }) }}
        </p>
      </div>

      <div class="grid grid-cols-2 gap-3">
        <div>
          <label :class="labelClass">{{ t('cars.label_month') }}</label>
          <select v-model="form.month" required :class="inputClass">
            <option :value="null" disabled>{{ t('cars.month_placeholder') }}</option>
            <option v-for="(name, i) in monthOptions" :key="i" :value="i + 1">{{ name }}</option>
          </select>
        </div>
        <div>
          <label :class="labelClass">{{ t('cars.label_year') }}</label>
          <input v-model="form.year" type="number" inputmode="numeric" required min="2000" :max="new Date().getFullYear() + 1" :class="inputClass" />
        </div>
      </div>

      <div>
        <label :class="labelClass">{{ t('cars.label_plate') }}</label>
        <input v-model="form.licensePlate" type="text" :placeholder="t('cars.plate_placeholder')" :class="inputClass" />
      </div>

      <div>
        <label :class="labelClass">{{ t('cars.label_power') }}</label>
        <input v-model.number="form.powerKw" type="number" step="0.1" min="0" :placeholder="t('cars.power_placeholder')" :class="inputClass" />
        <p v-if="form.powerPs" class="text-xs text-gray-500 dark:text-gray-400 mt-1">≈ {{ form.powerPs }} PS</p>
        <p v-else class="text-xs text-gray-500 dark:text-gray-400 mt-1">{{ t('cars.hint_power') }}</p>
      </div>
    </div>

    <!-- Zusätzliche Einstellungen -->
    <div class="mt-4 border-t dark:border-gray-600 pt-4 space-y-4">
      <label class="flex items-start gap-3 cursor-pointer">
        <div class="relative mt-0.5">
          <input type="checkbox" v-model="form.hasHeatPump" class="sr-only peer" />
          <div class="w-10 h-5 bg-gray-200 dark:bg-gray-600 rounded-full peer-checked:bg-blue-500 transition-colors"></div>
          <div class="absolute top-0.5 left-0.5 w-4 h-4 bg-white rounded-full shadow transition-transform peer-checked:translate-x-5"></div>
        </div>
        <div>
          <span class="text-sm font-medium text-gray-700 dark:text-gray-300">{{ t('cars.label_heat_pump') }}</span>
          <p class="text-xs text-gray-400 dark:text-gray-500">{{ t('cars.hint_heat_pump') }}</p>
        </div>
      </label>

      <label class="flex items-start gap-3 cursor-pointer">
        <div class="relative mt-0.5">
          <input type="checkbox" v-model="form.isBusinessCar" class="sr-only peer" />
          <div class="w-10 h-5 bg-gray-200 dark:bg-gray-600 rounded-full peer-checked:bg-violet-500 transition-colors"></div>
          <div class="absolute top-0.5 left-0.5 w-4 h-4 bg-white rounded-full shadow transition-transform peer-checked:translate-x-5"></div>
        </div>
        <div>
          <span class="text-sm font-medium text-gray-700 dark:text-gray-300">{{ t('cars.business_car_label') }}</span>
          <p class="text-xs text-gray-400 dark:text-gray-500">{{ t('cars.business_car_hint') }}</p>
        </div>
      </label>

      <!-- Batteriedegradation (Legacy-Feld, nur beim Anlegen; danach pflegt der SoH-Verlauf den Wert) -->
      <div v-if="!form.editingCar">
        <label :class="labelClass">{{ t('cars.label_degradation') }}</label>
        <div class="flex items-center gap-2">
          <input v-model.number="form.batteryDegradationPercent" type="number" step="0.1" min="0" max="50"
            :placeholder="t('cars.degradation_placeholder')" :class="[inputClass, 'w-32']" />
          <span class="text-sm text-gray-500 dark:text-gray-400">%</span>
          <button v-if="form.batteryDegradationPercent != null" type="button"
            @click="form.batteryDegradationPercent = null"
            class="text-xs text-red-500 hover:text-red-700 ml-2">{{ t('cars.reset') }}</button>
        </div>
        <p v-if="form.batteryDegradationPercent && form.finalCapacity" class="text-xs text-amber-600 mt-1">
          {{ t('cars.effective_capacity', { eff: (form.finalCapacity * (1 - form.batteryDegradationPercent / 100)).toFixed(2), nom: form.finalCapacity }) }}
        </p>
        <p class="text-xs text-gray-400 dark:text-gray-500 mt-1">{{ t('cars.hint_degradation') }}</p>
      </div>
    </div>

    <div class="flex gap-3 pt-2">
      <button type="submit" v-haptic :disabled="submitting"
        class="btn-3d rounded-xl border-2 border-edge bg-green-600 px-6 py-2 min-h-11 font-semibold text-white hover:bg-green-700 disabled:opacity-60 disabled:cursor-not-allowed">
        {{ submitting ? t('cars.saving') : (form.editingCar ? t('cars.update_btn') : t('cars.add_submit_btn')) }}
      </button>
      <button type="button" @click="emit('cancel')" v-haptic
        class="btn-3d rounded-xl border-2 border-edge bg-white px-6 py-2 min-h-11 font-semibold text-gray-900 hover:bg-gray-50 dark:bg-white/10 dark:text-gray-100 dark:hover:bg-white/15">
        {{ t('cars.cancel') }}
      </button>
    </div>
  </form>
</template>
