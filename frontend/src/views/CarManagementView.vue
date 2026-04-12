<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ChartBarIcon, TruckIcon, ArrowDownTrayIcon, ClipboardDocumentIcon, CheckIcon } from '@heroicons/vue/24/outline'
import LicensePlate from '../components/car/LicensePlate.vue'
import { useLocaleFormat } from '../composables/useLocaleFormat'
import { useCarForm } from '../composables/useCarForm'
import { useCarImages } from '../composables/useCarImages'
import { useSohHistory } from '../composables/useSohHistory'
import { useWltpLookup } from '../composables/useWltpLookup'

const { t } = useI18n()
const { consumptionUnitLabel, distanceUnitLabel } = useLocaleFormat()

// -- Car Form --
const {
  cars, availableModels, loading, error, showForm, editingCar,
  showToast, toastMessage, teslaStatus,
  selectedBrand, selectedModel, year, licensePlate, trim,
  selectedCapacity, customCapacity, useCustomCapacity,
  powerKw, batteryDegradationPercent, hasHeatPump, isBusinessCar,
  sohHistory, showSohAddForm, sohEditingEntry, sohPercent, sohDate,
  sortedBrands, isSonstige, selectedModelCapacities, finalCapacity, powerPs,
  fetchCars, fetchBrands, resetForm,
  openAddForm, openEditForm, submitForm, deleteCar, setActiveCar, getModelLabel,
} = useCarForm()

// -- Car Images --
const {
  imageBlobUrls, imageUploading, imagePublicForUpload,
  revokeAllBlobUrls, loadCarImages, initVisibility,
  handleVisibilityChange, handleImageUpload, handleDeleteImage,
} = useCarImages(cars, error, showToast, toastMessage)

// -- SoH History --
const {
  openSohAddForm, openSohEditForm, cancelSohForm, submitSohForm, deleteSohEntry,
} = useSohHistory(editingCar, cars, error, sohHistory, showSohAddForm, sohEditingEntry, sohPercent, sohDate)

// -- WLTP Lookup --
const {
  wltpData, showWltpQuestion, showWltpForm,
  wltpRangeKm, wltpConsumptionKwhPer100km,
  closeWltpQuestion, openWltpForm, closeWltpForm, submitWltpData,
} = useWltpLookup(selectedBrand, selectedModel, finalCapacity, editingCar, error, showToast, toastMessage)

// -- Orchestration --
const doFetchCars = async () => {
  await fetchCars(loadCarImages, revokeAllBlobUrls)
  initVisibility(cars.value)
}

const doSubmitForm = () => submitForm(doFetchCars)
const doDeleteCar = (id: string) => deleteCar(id, doFetchCars)

onMounted(async () => {
  await fetchBrands()
  await fetchCars(loadCarImages, revokeAllBlobUrls)
  initVisibility(cars.value)
})

const copiedCarId = ref<string | null>(null)
const copyCarId = async (id: string) => {
  try {
    await navigator.clipboard.writeText(id)
    copiedCarId.value = id
    setTimeout(() => { copiedCarId.value = null }, 2000)
  } catch { /* ignore */ }
}
</script>

<template>
  <div class="md:max-w-4xl md:mx-auto md:p-6">
    <Transition name="fade" mode="out-in">
      <div v-if="!loading">
        <div class="bg-white dark:bg-gray-800 md:rounded-xl md:shadow-lg p-4 md:p-6">
      <div class="flex justify-between items-center mb-6">
        <h1 class="text-3xl font-bold text-gray-800 dark:text-gray-200">{{ t('cars.title') }}</h1>
        <button
          v-if="!showForm"
          @click="openAddForm"
          v-haptic
          class="btn-3d bg-indigo-600 text-white px-4 py-2 rounded-md hover:bg-indigo-700 transition">
          {{ t('cars.add_btn') }}
        </button>
      </div>

      <div v-if="error" class="mb-4 p-4 bg-red-50 dark:bg-red-900/30 border border-red-200 dark:border-red-700 text-red-700 dark:text-red-300 rounded-md">
        {{ error }}
      </div>

      <!-- Add form (inline) -->
      <div v-if="showForm && !editingCar" class="mb-8 p-6 bg-gray-50 dark:bg-gray-700 rounded-lg border border-gray-200 dark:border-gray-600">
        <h2 class="text-xl font-semibold mb-4 text-gray-800 dark:text-gray-200">{{ t('cars.add_title') }}</h2>

        <form @submit.prevent="doSubmitForm" class="space-y-4">
          <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">{{ t('cars.label_brand') }}</label>
              <select v-model="selectedBrand" required
                class="w-full rounded-md border-gray-300 dark:border-gray-600 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 p-2 border dark:bg-gray-700 dark:text-gray-100">
                <option value="">{{ t('cars.select_brand') }}</option>
                <option v-for="brand in sortedBrands" :key="brand.value" :value="brand.value">
                  {{ brand.label }}
                </option>
              </select>
            </div>

            <div v-if="!isSonstige">
              <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">{{ t('cars.label_model') }}</label>
              <select v-model="selectedModel" required :disabled="!selectedBrand"
                class="w-full rounded-md border-gray-300 dark:border-gray-600 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 p-2 border disabled:bg-gray-100 dark:disabled:bg-gray-600 dark:bg-gray-700 dark:text-gray-100">
                <option value="">{{ selectedBrand ? t('cars.select_model') : t('cars.select_brand_first') }}</option>
                <option v-for="m in availableModels" :key="m.value" :value="m.value">
                  {{ m.label }}
                </option>
              </select>
            </div>

            <div>
              <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">{{ isSonstige ? t('cars.label_model') : t('cars.label_trim') }}</label>
              <input
                v-model="trim"
                type="text"
                :placeholder="isSonstige ? t('cars.trim_placeholder_sonstige') : t('cars.trim_placeholder')"
                class="w-full rounded-md border-gray-300 dark:border-gray-600 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 p-2 border dark:bg-gray-700 dark:text-gray-100" />
              <p v-if="!isSonstige" class="text-xs text-gray-500 dark:text-gray-400 mt-1">{{ t('cars.hint_trim') }}</p>
            </div>

            <!-- Capacity Selection -->
            <div v-if="selectedModel" class="md:col-span-2">
              <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">{{ t('cars.label_capacity') }}</label>

              <div v-if="!useCustomCapacity" class="space-y-2">
                <div class="flex gap-2 flex-wrap">
                  <button
                    v-for="capacity in selectedModelCapacities"
                    :key="capacity.kWh"
                    type="button"
                    @click="selectedCapacity = capacity.kWh"
                    :class="[
                      'px-4 py-2 rounded-md text-sm font-medium transition',
                      selectedCapacity === capacity.kWh
                        ? 'bg-indigo-600 text-white shadow-md'
                        : 'bg-indigo-100 text-indigo-700 hover:bg-indigo-200'
                    ]">
                    {{ capacity.variantName ? `${capacity.variantName} · ${capacity.kWh} kWh` : `${capacity.kWh} kWh` }}
                  </button>
                </div>
                <button
                  type="button"
                  @click="useCustomCapacity = true; selectedCapacity = null"
                  class="text-sm text-indigo-600 hover:text-indigo-700 underline">
                  {{ t('cars.custom_capacity') }}
                </button>
              </div>

              <div v-else class="space-y-2">
                <input
                  v-model.number="customCapacity"
                  type="number"
                  step="0.1"
                  min="0"
                  required
                  :placeholder="t('cars.capacity_custom_placeholder')"
                  class="w-full rounded-md border-gray-300 dark:border-gray-600 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 p-2 border dark:bg-gray-700 dark:text-gray-100"
                />
                <button
                  type="button"
                  @click="useCustomCapacity = false; customCapacity = null"
                  class="text-sm text-indigo-600 hover:text-indigo-700 underline">
                  {{ t('cars.preset_capacity') }}
                </button>
              </div>
            </div>

            <!-- WLTP Info (if available) -->
            <div v-if="wltpData && !editingCar" class="md:col-span-2">
              <div class="p-4 bg-blue-50 dark:bg-blue-900/30 border border-blue-200 dark:border-blue-700 rounded-lg">
                <div class="flex items-start">
                  <svg class="w-5 h-5 text-blue-600 mt-0.5 mr-3 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path>
                  </svg>
                  <div>
                    <p class="text-sm font-medium text-blue-900 dark:text-blue-300">{{ t('cars.wltp_available') }}</p>
                    <p class="text-sm text-blue-700 dark:text-blue-400 mt-1">
                      {{ t('cars.wltp_range') }}: <span class="font-semibold">{{ wltpData.wltpRangeKm }} {{ distanceUnitLabel() }}</span>
                      | {{ t('cars.wltp_consumption') }}: <span class="font-semibold">{{ wltpData.wltpConsumptionKwhPer100km }} {{ consumptionUnitLabel() }}</span>
                    </p>
                  </div>
                </div>
              </div>
            </div>

            <div>
              <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">{{ t('cars.label_year') }}</label>
              <input v-model="year" type="number" required min="2000" :max="new Date().getFullYear() + 1"
                class="w-full rounded-md border-gray-300 dark:border-gray-600 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 p-2 border dark:bg-gray-700 dark:text-gray-100" />
            </div>

            <div>
              <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">{{ t('cars.label_plate') }}</label>
              <input v-model="licensePlate" type="text" :placeholder="t('cars.plate_placeholder')"
                class="w-full rounded-md border-gray-300 dark:border-gray-600 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 p-2 border dark:bg-gray-700 dark:text-gray-100" />
            </div>

            <div>
              <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">{{ t('cars.label_power') }}</label>
              <input
                v-model.number="powerKw"
                type="number"
                step="0.1"
                min="0"
                :placeholder="t('cars.power_placeholder')"
                class="w-full rounded-md border-gray-300 dark:border-gray-600 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 p-2 border dark:bg-gray-700 dark:text-gray-100" />
              <p v-if="powerPs" class="text-xs text-gray-500 dark:text-gray-400 mt-1">≈ {{ powerPs }} PS</p>
              <p v-else class="text-xs text-gray-500 dark:text-gray-400 mt-1">{{ t('cars.hint_power') }}</p>
            </div>
          </div>

          <!-- Zusätzliche Einstellungen -->
          <div class="mt-4 border-t dark:border-gray-600 pt-4">
            <div class="space-y-4">
              <!-- Wärmepumpe -->
              <label class="flex items-start gap-3 cursor-pointer">
                <div class="relative mt-0.5">
                  <input type="checkbox" v-model="hasHeatPump" class="sr-only peer" />
                  <div class="w-10 h-5 bg-gray-200 dark:bg-gray-600 rounded-full peer-checked:bg-blue-500 transition-colors"></div>
                  <div class="absolute top-0.5 left-0.5 w-4 h-4 bg-white rounded-full shadow transition-transform peer-checked:translate-x-5"></div>
                </div>
                <div>
                  <span class="text-sm font-medium text-gray-700 dark:text-gray-300">{{ t('cars.label_heat_pump') }}</span>
                  <p class="text-xs text-gray-400 dark:text-gray-500">{{ t('cars.hint_heat_pump') }}</p>
                </div>
              </label>

              <!-- Dienstwagen -->
              <label class="flex items-start gap-3 cursor-pointer">
                <div class="relative mt-0.5">
                  <input type="checkbox" v-model="isBusinessCar" class="sr-only peer" />
                  <div class="w-10 h-5 bg-gray-200 dark:bg-gray-600 rounded-full peer-checked:bg-violet-500 transition-colors"></div>
                  <div class="absolute top-0.5 left-0.5 w-4 h-4 bg-white rounded-full shadow transition-transform peer-checked:translate-x-5"></div>
                </div>
                <div>
                  <span class="text-sm font-medium text-gray-700 dark:text-gray-300">{{ t('cars.business_car_label') }}</span>
                  <p class="text-xs text-gray-400 dark:text-gray-500">{{ t('cars.business_car_hint') }}</p>
                </div>
              </label>

              <!-- Batteriedegradation (Legacy-Feld, nur sichtbar wenn noch kein SoH-Verlauf) -->
              <div v-if="!editingCar">
                <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">{{ t('cars.label_degradation') }}</label>
                <div class="flex items-center gap-2">
                  <input v-model.number="batteryDegradationPercent" type="number" step="0.1" min="0" max="50"
                    :placeholder="t('cars.degradation_placeholder')"
                    class="w-32 rounded-md border-gray-300 dark:border-gray-600 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 p-2 border dark:bg-gray-700 dark:text-gray-100" />
                  <span class="text-sm text-gray-500 dark:text-gray-400">%</span>
                  <button v-if="batteryDegradationPercent != null" type="button"
                    @click="batteryDegradationPercent = null"
                    class="text-xs text-red-500 hover:text-red-700 ml-2">{{ t('cars.reset') }}</button>
                </div>
                <p v-if="batteryDegradationPercent && finalCapacity" class="text-xs text-amber-600 mt-1">
                  {{ t('cars.effective_capacity', { eff: (finalCapacity * (1 - batteryDegradationPercent / 100)).toFixed(2), nom: finalCapacity }) }}
                </p>
                <p class="text-xs text-gray-400 dark:text-gray-500 mt-1">{{ t('cars.hint_degradation') }}</p>
              </div>

              <!-- SoH-Verlauf (nur im Edit-Modus) -->
              <div v-if="editingCar">
                <div class="flex items-center justify-between mb-2">
                  <label class="text-sm font-medium text-gray-700 dark:text-gray-300">{{ t('cars.soh_section_title') }}</label>
                  <button v-if="!showSohAddForm" type="button" @click="openSohAddForm"
                    class="text-xs text-indigo-600 hover:text-indigo-700 font-medium">
                    + {{ t('cars.soh_add_btn') }}
                  </button>
                </div>
                <p class="text-xs text-gray-400 dark:text-gray-500 mb-2">{{ t('cars.soh_hint') }}</p>

                <!-- SoH Eingabeformular -->
                <div v-if="showSohAddForm" class="p-3 bg-gray-50 dark:bg-gray-600 rounded-lg space-y-2 mb-3">
                  <div class="grid grid-cols-2 gap-2">
                    <div>
                      <label class="block text-xs font-medium text-gray-600 dark:text-gray-300 mb-1">{{ t('cars.soh_label_percent') }}</label>
                      <div class="relative">
                        <input v-model.number="sohPercent" type="number" step="0.1" min="50" max="100"
                          placeholder="z.B. 92"
                          class="w-full rounded-md border-gray-300 dark:border-gray-500 p-2 border text-sm dark:bg-gray-700 dark:text-gray-100 pr-6" />
                        <span class="absolute right-2 top-2 text-xs text-gray-400">%</span>
                      </div>
                    </div>
                    <div>
                      <label class="block text-xs font-medium text-gray-600 dark:text-gray-300 mb-1">{{ t('cars.soh_label_date') }}</label>
                      <input v-model="sohDate" type="date"
                        class="w-full rounded-md border-gray-300 dark:border-gray-500 p-2 border text-sm dark:bg-gray-700 dark:text-gray-100" />
                    </div>
                  </div>
                  <div v-if="sohPercent && finalCapacity" class="text-xs text-amber-600">
                    Effektive Kapazität: {{ (finalCapacity * sohPercent / 100).toFixed(1) }} kWh
                  </div>
                  <div class="flex gap-2">
                    <button type="button" @click="submitSohForm"
                      class="text-xs bg-indigo-600 text-white px-3 py-1.5 rounded-md hover:bg-indigo-700 transition">
                      {{ t('cars.soh_save_btn') }}
                    </button>
                    <button type="button" @click="cancelSohForm"
                      class="text-xs bg-gray-200 dark:bg-gray-500 text-gray-700 dark:text-gray-200 px-3 py-1.5 rounded-md hover:bg-gray-300 transition">
                      {{ t('cars.cancel') }}
                    </button>
                  </div>
                </div>

                <!-- SoH Verlaufsliste -->
                <div v-if="sohHistory.length === 0 && !showSohAddForm" class="text-xs text-gray-400 dark:text-gray-500 italic">
                  {{ t('cars.soh_history_empty') }}
                </div>
                <div v-else class="space-y-1">
                  <div v-for="entry in sohHistory" :key="entry.id"
                    class="flex items-center justify-between py-1.5 px-2 rounded bg-gray-50 dark:bg-gray-600 text-sm">
                    <div class="flex items-center gap-3">
                      <span class="font-semibold text-gray-800 dark:text-gray-100">{{ entry.sohPercent }}%</span>
                      <span class="text-gray-500 dark:text-gray-400 text-xs">{{ entry.recordedAt }}</span>
                    </div>
                    <div class="flex gap-2">
                      <button type="button" @click="openSohEditForm(entry)"
                        class="text-xs text-indigo-500 hover:text-indigo-700">{{ t('cars.soh_correct_btn') }}</button>
                      <button type="button" @click="deleteSohEntry(entry)"
                        class="text-xs text-red-400 hover:text-red-600">{{ t('cars.soh_delete_btn') }}</button>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div class="flex gap-3 pt-2">
            <button type="submit"
              v-haptic
              class="btn-3d bg-indigo-600 text-white px-6 py-2 rounded-md hover:bg-indigo-700 transition">
              {{ editingCar ? t('cars.update_btn') : t('cars.add_submit_btn') }}
            </button>
            <button type="button" @click="resetForm"
              v-haptic
              class="btn-3d bg-gray-200 dark:bg-gray-600 text-gray-700 dark:text-gray-200 px-6 py-2 rounded-md hover:bg-gray-300 dark:hover:bg-gray-500 transition">
              {{ t('cars.cancel') }}
            </button>
          </div>
        </form>
      </div>

      <!-- Empty State: No Cars -->
      <div v-if="cars.length === 0 && !showForm" class="text-center py-16 px-4">
        <TruckIcon class="h-24 w-24 mx-auto text-gray-300 mb-6" />
        <h3 class="text-2xl font-bold text-gray-800 dark:text-gray-200 mb-3">{{ t('cars.empty_title') }}</h3>
        <p class="text-gray-600 dark:text-gray-400 mb-8 max-w-md mx-auto">
          {{ t('cars.empty_desc') }}
        </p>
        <button
          @click="openAddForm"
          class="inline-flex items-center gap-2 px-8 py-4 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 font-medium shadow-lg hover:shadow-xl transition">
          <TruckIcon class="h-5 w-5" />
          {{ t('cars.add_first_btn') }}
        </button>
        <div class="mt-8 grid grid-cols-1 sm:grid-cols-3 gap-4 max-w-2xl mx-auto text-left">
          <div class="bg-blue-50 dark:bg-blue-900/30 border border-blue-200 dark:border-blue-700 rounded-lg p-4">
            <div class="text-blue-600 mb-2">📊</div>
            <p class="text-sm font-semibold text-gray-800 dark:text-gray-200 mb-1">{{ t('cars.feat_models_title') }}</p>
            <p class="text-xs text-gray-600 dark:text-gray-400">{{ t('cars.feat_models_desc') }}</p>
          </div>
          <div class="bg-green-50 dark:bg-green-900/30 border border-green-200 dark:border-green-700 rounded-lg p-4">
            <div class="text-green-600 mb-2">⚡</div>
            <p class="text-sm font-semibold text-gray-800 dark:text-gray-200 mb-1">{{ t('cars.feat_wltp_title') }}</p>
            <p class="text-xs text-gray-600 dark:text-gray-400">{{ t('cars.feat_wltp_desc') }}</p>
          </div>
          <div class="bg-purple-50 dark:bg-purple-900/20 border border-purple-200 dark:border-purple-800 rounded-lg p-4">
            <div class="text-purple-600 mb-2">🔒</div>
            <p class="text-sm font-semibold text-gray-800 dark:text-gray-200 mb-1">{{ t('cars.feat_privacy_title') }}</p>
            <p class="text-xs text-gray-600 dark:text-gray-400">{{ t('cars.feat_privacy_desc') }}</p>
          </div>
        </div>
      </div>

      <div v-else class="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div v-for="car in cars" :key="car.id"
          class="bg-gray-50 dark:bg-gray-700 border border-gray-200 dark:border-gray-600 rounded-lg shadow-sm hover:shadow-md transition overflow-hidden">

          <!-- Car Image -->
          <div class="relative bg-gray-100 dark:bg-gray-600 h-40 flex items-center justify-center">
            <img
              v-if="imageBlobUrls[car.id]"
              :src="imageBlobUrls[car.id]"
              :alt="getModelLabel(car.model)"
              class="w-full h-full object-cover" />
            <div v-else class="flex flex-col items-center text-gray-400 dark:text-gray-500">
              <svg class="w-12 h-12 mb-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5"
                  d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
              </svg>
              <span class="text-xs">{{ t('cars.no_photo') }}</span>
            </div>

            <!-- Delete image button -->
            <button v-if="imageBlobUrls[car.id]"
              @click="handleDeleteImage(car.id)"
              class="absolute top-2 right-2 bg-white dark:bg-gray-800 bg-opacity-80 dark:bg-opacity-80 rounded-full p-1 hover:bg-opacity-100 text-red-600 hover:text-red-700 transition"
              :title="t('cars.delete_photo')">
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>

            <!-- Public badge -->
            <span v-if="car.imageUrl && car.imagePublic"
              class="absolute top-2 left-2 bg-green-600 text-white text-xs px-2 py-0.5 rounded-full">
              {{ t('cars.public') }}
            </span>
            <span v-else-if="car.imageUrl && !car.imagePublic"
              class="absolute top-2 left-2 bg-gray-600 text-white text-xs px-2 py-0.5 rounded-full">
              {{ t('cars.private') }}
            </span>
          </div>

          <!-- Photo controls row -->
          <div class="px-4 py-2 flex items-center gap-3 border-b border-gray-100 dark:border-gray-600">
            <label class="flex items-center gap-1.5 cursor-pointer">
              <input type="checkbox"
                :checked="imagePublicForUpload[car.id] ?? false"
                @change="handleVisibilityChange(car.id, ($event.target as HTMLInputElement).checked)"
                class="rounded border-gray-300 text-indigo-600 focus:ring-indigo-500" />
              <span class="text-xs text-gray-600 dark:text-gray-400">{{ t('cars.share_photo') }}</span>
            </label>
            <label class="ml-auto cursor-pointer">
              <span :class="[
                'text-xs px-3 py-1.5 rounded-md font-medium transition',
                imageUploading[car.id]
                  ? 'bg-gray-100 dark:bg-gray-600 text-gray-400 cursor-not-allowed'
                  : 'bg-indigo-100 dark:bg-indigo-900/40 text-indigo-700 dark:text-indigo-300 hover:bg-indigo-200'
              ]">
                {{ imageUploading[car.id] ? t('cars.uploading') : (car.imageUrl ? t('cars.change_photo') : t('cars.upload_photo')) }}
              </span>
              <input type="file" accept="image/jpeg,image/png" class="hidden"
                :disabled="imageUploading[car.id]"
                @change="handleImageUpload(car.id, $event)" />
            </label>
          </div>

          <!-- Card body -->
          <div class="px-4 pt-3 pb-4 space-y-3">

            <!-- Title row -->
            <div class="flex justify-between items-start gap-2">
              <div class="min-w-0">
                <div class="flex items-center gap-2 flex-wrap">
                  <h3 class="text-lg font-bold text-indigo-700 dark:text-indigo-300 leading-tight">
                    {{ getModelLabel(car.model) }}
                    <span v-if="car.trim" class="text-sm font-normal text-indigo-500 dark:text-indigo-400">{{ car.trim }}</span>
                  </h3>
                  <span v-if="car.isPrimary"
                    class="px-2 py-0.5 bg-green-100 text-green-700 text-xs rounded-full font-medium border border-green-200 shrink-0">
                    {{ t('cars.active') }}
                  </span>
                  <!-- Tesla state badge -->
                  <template v-if="car.brand?.toLowerCase() === 'tesla' && teslaStatus?.connected && (teslaStatus.carId === car.id || teslaStatus.carId === null)">
                    <span v-if="teslaStatus.vehicleState === 'charging'"
                      class="inline-flex items-center gap-1 px-2 py-0.5 bg-green-100 text-green-700 text-xs rounded-full font-medium border border-green-200 shrink-0">
                      <span class="w-1.5 h-1.5 rounded-full bg-green-500 animate-pulse"></span>
                      {{ t('cars.tesla_charging') }}
                    </span>
                    <span v-else-if="teslaStatus.vehicleState === 'online'"
                      class="inline-flex items-center gap-1 px-2 py-0.5 bg-blue-50 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400 text-xs rounded-full font-medium border border-blue-200 dark:border-blue-700 shrink-0">
                      <span class="w-1.5 h-1.5 rounded-full bg-blue-400"></span>
                      {{ t('cars.tesla_online') }}
                    </span>
                    <span v-else-if="teslaStatus.vehicleState === 'asleep'"
                      class="inline-flex items-center gap-1 px-2 py-0.5 bg-gray-100 dark:bg-gray-600 text-gray-500 dark:text-gray-300 text-xs rounded-full font-medium border border-gray-200 dark:border-gray-500 shrink-0">
                      <span class="w-1.5 h-1.5 rounded-full bg-gray-400"></span>
                      {{ t('cars.tesla_sleeping') }}
                    </span>
                  </template>
                </div>
                <p class="text-sm text-gray-500 dark:text-gray-400 mt-0.5">{{ car.year }}</p>
              </div>
              <LicensePlate v-if="car.licensePlate" :plate="car.licensePlate" class="shrink-0" />
            </div>

            <!-- Fahrzeug-ID -->
            <div class="flex items-center gap-2 cursor-pointer group" @click.stop="copyCarId(car.id)" :title="t('cars.api_id_copy')">
              <div class="flex-1 min-w-0">
                <p class="text-[10px] text-gray-400 dark:text-gray-500 uppercase tracking-wide">{{ t('cars.api_id_label') }}</p>
                <p class="text-xs text-gray-500 dark:text-gray-300 font-mono bg-gray-100 dark:bg-gray-600 rounded px-1.5 py-0.5 mt-0.5 select-all group-hover:bg-gray-200 dark:group-hover:bg-gray-500 transition-colors">{{ car.id }}</p>
              </div>
              <div class="shrink-0 p-1.5 text-gray-400 group-hover:text-indigo-500 dark:group-hover:text-indigo-400 transition-colors">
                <CheckIcon v-if="copiedCarId === car.id" class="h-4 w-4 text-green-500" />
                <ClipboardDocumentIcon v-else class="h-4 w-4" />
              </div>
            </div>

            <!-- Specs row -->
            <div class="grid grid-cols-2 gap-x-4 gap-y-1 text-sm">
              <div class="text-gray-600 dark:text-gray-400">
                <span class="text-gray-400 dark:text-gray-500 text-xs uppercase tracking-wide">{{ t('cars.battery') }}</span>
                <div class="font-medium text-gray-800 dark:text-gray-200">
                  {{ car.batteryCapacityKwh }} kWh
                  <span v-if="car.batteryDegradationPercent" class="text-amber-500 text-xs font-normal ml-1">
                    - effektiv {{ car.effectiveBatteryCapacityKwh }} kWh
                  </span>
                </div>
              </div>
              <div v-if="car.powerKw" class="text-gray-600 dark:text-gray-400">
                <span class="text-gray-400 dark:text-gray-500 text-xs uppercase tracking-wide">{{ t('cars.power') }}</span>
                <div class="font-medium text-gray-800 dark:text-gray-200">
                  {{ car.powerKw }} kW <span class="text-gray-500 text-xs">({{ Math.round(car.powerKw * 1.35962) }} PS)</span>
                </div>
              </div>
            </div>

            <!-- Badges row: SoH, Wärmepumpe, Dienstwagen -->
            <div class="flex flex-wrap gap-1.5">
              <span v-if="car.batteryDegradationPercent"
                class="inline-flex items-center gap-1 px-2 py-0.5 bg-amber-50 dark:bg-amber-900/20 text-amber-700 dark:text-amber-400 text-xs rounded-full border border-amber-200 dark:border-amber-700">
                {{ t('cars.soh_badge', { pct: 100 - car.batteryDegradationPercent }) }}
              </span>
              <span v-if="car.hasHeatPump"
                class="inline-flex items-center gap-1 px-2 py-0.5 bg-blue-50 dark:bg-blue-900/20 text-blue-600 dark:text-blue-400 text-xs rounded-full border border-blue-200 dark:border-blue-700">
                {{ t('cars.heat_pump_badge') }}
              </span>
              <span v-if="car.isBusinessCar"
                class="inline-flex items-center gap-1 px-2 py-0.5 bg-violet-50 dark:bg-violet-900/20 text-violet-700 dark:text-violet-400 text-xs rounded-full border border-violet-200 dark:border-violet-700">
                {{ t('cars.business_car_label') }}
              </span>
            </div>

            <!-- Actions -->
            <div class="flex gap-2 pt-1">
              <button v-if="!car.isPrimary" @click="setActiveCar(car.id)"
                v-haptic
                class="btn-3d flex-1 bg-green-100 dark:bg-green-700 text-green-800 dark:text-white px-3 py-2 rounded-md text-sm font-medium hover:bg-green-200 dark:hover:bg-green-600 transition">
                {{ t('cars.set_active_btn') }}
              </button>
              <button @click="openEditForm(car)"
                v-haptic
                class="btn-3d flex-1 bg-indigo-100 dark:bg-indigo-700 text-indigo-800 dark:text-white px-3 py-2 rounded-md text-sm font-medium hover:bg-indigo-200 dark:hover:bg-indigo-600 transition">
                {{ t('cars.edit_btn') }}
              </button>
              <button @click="doDeleteCar(car.id)"
                v-haptic
                class="btn-3d flex-1 bg-red-100 dark:bg-red-700 text-red-800 dark:text-white px-3 py-2 rounded-md text-sm font-medium hover:bg-red-200 dark:hover:bg-red-600 transition">
                {{ t('cars.delete_btn') }}
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Edit Car Modal -->
    <div v-if="editingCar" class="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 p-4" @click.self="resetForm">
      <div class="bg-white dark:bg-gray-800 rounded-xl shadow-2xl w-full max-w-2xl max-h-[90vh] overflow-y-auto">
        <div class="flex items-center justify-between px-6 pt-6 pb-4 border-b border-gray-100 dark:border-gray-700">
          <h2 class="text-xl font-semibold text-gray-800 dark:text-gray-200">{{ t('cars.edit_title') }}</h2>
          <button @click="resetForm" class="text-gray-400 hover:text-gray-600 dark:hover:text-gray-200 transition">
            <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        <form @submit.prevent="doSubmitForm" class="p-6 space-y-4">
          <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">{{ t('cars.label_brand') }}</label>
              <select v-model="selectedBrand" required
                class="w-full rounded-md border-gray-300 dark:border-gray-600 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 p-2 border dark:bg-gray-700 dark:text-gray-100">
                <option value="">{{ t('cars.select_brand') }}</option>
                <option v-for="brand in sortedBrands" :key="brand.value" :value="brand.value">
                  {{ brand.label }}
                </option>
              </select>
            </div>

            <div v-if="!isSonstige">
              <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">{{ t('cars.label_model') }}</label>
              <select v-model="selectedModel" required :disabled="!selectedBrand"
                class="w-full rounded-md border-gray-300 dark:border-gray-600 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 p-2 border disabled:bg-gray-100 dark:disabled:bg-gray-600 dark:bg-gray-700 dark:text-gray-100">
                <option value="">{{ selectedBrand ? t('cars.select_model') : t('cars.select_brand_first') }}</option>
                <option v-for="m in availableModels" :key="m.value" :value="m.value">
                  {{ m.label }}
                </option>
              </select>
            </div>

            <div>
              <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">{{ isSonstige ? t('cars.label_model') : t('cars.label_trim') }}</label>
              <input v-model="trim" type="text"
                :placeholder="isSonstige ? t('cars.trim_placeholder_sonstige') : t('cars.trim_placeholder')"
                class="w-full rounded-md border-gray-300 dark:border-gray-600 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 p-2 border dark:bg-gray-700 dark:text-gray-100" />
            </div>

            <div v-if="selectedModel" class="md:col-span-2">
              <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">{{ t('cars.label_capacity') }}</label>
              <div v-if="!useCustomCapacity" class="space-y-2">
                <div class="flex gap-2 flex-wrap">
                  <button v-for="capacity in selectedModelCapacities" :key="capacity.kWh" type="button"
                    @click="selectedCapacity = capacity.kWh"
                    :class="['px-4 py-2 rounded-md text-sm font-medium transition',
                      selectedCapacity === capacity.kWh ? 'bg-indigo-600 text-white shadow-md' : 'bg-indigo-100 text-indigo-700 hover:bg-indigo-200']">
                    {{ capacity.variantName ? `${capacity.variantName} · ${capacity.kWh} kWh` : `${capacity.kWh} kWh` }}
                  </button>
                </div>
                <button type="button" @click="useCustomCapacity = true; selectedCapacity = null"
                  class="text-sm text-indigo-600 hover:text-indigo-700 underline">
                  {{ t('cars.custom_capacity') }}
                </button>
              </div>
              <div v-else class="space-y-2">
                <input v-model.number="customCapacity" type="number" step="0.1" min="0" required
                  :placeholder="t('cars.capacity_custom_placeholder')"
                  class="w-full rounded-md border-gray-300 dark:border-gray-600 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 p-2 border dark:bg-gray-700 dark:text-gray-100" />
                <button type="button" @click="useCustomCapacity = false; customCapacity = null"
                  class="text-sm text-indigo-600 hover:text-indigo-700 underline">
                  {{ t('cars.preset_capacity') }}
                </button>
              </div>
            </div>

            <div>
              <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">{{ t('cars.label_year') }}</label>
              <input v-model="year" type="number" required min="2000" :max="new Date().getFullYear() + 1"
                class="w-full rounded-md border-gray-300 dark:border-gray-600 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 p-2 border dark:bg-gray-700 dark:text-gray-100" />
            </div>

            <div>
              <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">{{ t('cars.label_plate') }}</label>
              <input v-model="licensePlate" type="text" :placeholder="t('cars.plate_placeholder')"
                class="w-full rounded-md border-gray-300 dark:border-gray-600 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 p-2 border dark:bg-gray-700 dark:text-gray-100" />
            </div>

            <div>
              <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">{{ t('cars.label_power') }}</label>
              <input v-model.number="powerKw" type="number" step="0.1" min="0" :placeholder="t('cars.power_placeholder')"
                class="w-full rounded-md border-gray-300 dark:border-gray-600 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 p-2 border dark:bg-gray-700 dark:text-gray-100" />
              <p v-if="powerPs" class="text-xs text-gray-500 dark:text-gray-400 mt-1">≈ {{ powerPs }} PS</p>
              <p v-else class="text-xs text-gray-500 dark:text-gray-400 mt-1">{{ t('cars.hint_power') }}</p>
            </div>
          </div>

          <!-- Zusätzliche Einstellungen -->
          <div class="mt-4 border-t dark:border-gray-600 pt-4">
            <div class="space-y-4">
              <!-- Wärmepumpe -->
              <label class="flex items-start gap-3 cursor-pointer">
                <div class="relative mt-0.5">
                  <input type="checkbox" v-model="hasHeatPump" class="sr-only peer" />
                  <div class="w-10 h-5 bg-gray-200 dark:bg-gray-600 rounded-full peer-checked:bg-blue-500 transition-colors"></div>
                  <div class="absolute top-0.5 left-0.5 w-4 h-4 bg-white rounded-full shadow transition-transform peer-checked:translate-x-5"></div>
                </div>
                <div>
                  <span class="text-sm font-medium text-gray-700 dark:text-gray-300">{{ t('cars.label_heat_pump') }}</span>
                  <p class="text-xs text-gray-400 dark:text-gray-500">{{ t('cars.hint_heat_pump') }}</p>
                </div>
              </label>

              <!-- Dienstwagen -->
              <label class="flex items-start gap-3 cursor-pointer">
                <div class="relative mt-0.5">
                  <input type="checkbox" v-model="isBusinessCar" class="sr-only peer" />
                  <div class="w-10 h-5 bg-gray-200 dark:bg-gray-600 rounded-full peer-checked:bg-violet-500 transition-colors"></div>
                  <div class="absolute top-0.5 left-0.5 w-4 h-4 bg-white rounded-full shadow transition-transform peer-checked:translate-x-5"></div>
                </div>
                <div>
                  <span class="text-sm font-medium text-gray-700 dark:text-gray-300">{{ t('cars.business_car_label') }}</span>
                  <p class="text-xs text-gray-400 dark:text-gray-500">{{ t('cars.business_car_hint') }}</p>
                </div>
              </label>

              <!-- Batteriedegradation (Legacy-Feld, nur sichtbar wenn noch kein SoH-Verlauf) -->
              <div v-if="!editingCar">
                <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">{{ t('cars.label_degradation') }}</label>
                <div class="flex items-center gap-2">
                  <input v-model.number="batteryDegradationPercent" type="number" step="0.1" min="0" max="50"
                    :placeholder="t('cars.degradation_placeholder')"
                    class="w-32 rounded-md border-gray-300 dark:border-gray-600 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 p-2 border dark:bg-gray-700 dark:text-gray-100" />
                  <span class="text-sm text-gray-500 dark:text-gray-400">%</span>
                  <button v-if="batteryDegradationPercent != null" type="button"
                    @click="batteryDegradationPercent = null"
                    class="text-xs text-red-500 hover:text-red-700 ml-2">{{ t('cars.reset') }}</button>
                </div>
                <p v-if="batteryDegradationPercent && finalCapacity" class="text-xs text-amber-600 mt-1">
                  {{ t('cars.effective_capacity', { eff: (finalCapacity * (1 - batteryDegradationPercent / 100)).toFixed(2), nom: finalCapacity }) }}
                </p>
                <p class="text-xs text-gray-400 dark:text-gray-500 mt-1">{{ t('cars.hint_degradation') }}</p>
              </div>

              <!-- SoH-Verlauf (nur im Edit-Modus) -->
              <div v-if="editingCar">
                <div class="flex items-center justify-between mb-2">
                  <label class="text-sm font-medium text-gray-700 dark:text-gray-300">{{ t('cars.soh_section_title') }}</label>
                  <button v-if="!showSohAddForm" type="button" @click="openSohAddForm"
                    class="text-xs text-indigo-600 hover:text-indigo-700 font-medium">
                    + {{ t('cars.soh_add_btn') }}
                  </button>
                </div>
                <p class="text-xs text-gray-400 dark:text-gray-500 mb-2">{{ t('cars.soh_hint') }}</p>

                <!-- SoH Eingabeformular -->
                <div v-if="showSohAddForm" class="p-3 bg-gray-50 dark:bg-gray-600 rounded-lg space-y-2 mb-3">
                  <div class="grid grid-cols-2 gap-2">
                    <div>
                      <label class="block text-xs font-medium text-gray-600 dark:text-gray-300 mb-1">{{ t('cars.soh_label_percent') }}</label>
                      <div class="relative">
                        <input v-model.number="sohPercent" type="number" step="0.1" min="50" max="100"
                          placeholder="z.B. 92"
                          class="w-full rounded-md border-gray-300 dark:border-gray-500 p-2 border text-sm dark:bg-gray-700 dark:text-gray-100 pr-6" />
                        <span class="absolute right-2 top-2 text-xs text-gray-400">%</span>
                      </div>
                    </div>
                    <div>
                      <label class="block text-xs font-medium text-gray-600 dark:text-gray-300 mb-1">{{ t('cars.soh_label_date') }}</label>
                      <input v-model="sohDate" type="date"
                        class="w-full rounded-md border-gray-300 dark:border-gray-500 p-2 border text-sm dark:bg-gray-700 dark:text-gray-100" />
                    </div>
                  </div>
                  <div v-if="sohPercent && finalCapacity" class="text-xs text-amber-600">
                    Effektive Kapazität: {{ (finalCapacity * sohPercent / 100).toFixed(1) }} kWh
                  </div>
                  <div class="flex gap-2">
                    <button type="button" @click="submitSohForm"
                      class="text-xs bg-indigo-600 text-white px-3 py-1.5 rounded-md hover:bg-indigo-700 transition">
                      {{ t('cars.soh_save_btn') }}
                    </button>
                    <button type="button" @click="cancelSohForm"
                      class="text-xs bg-gray-200 dark:bg-gray-500 text-gray-700 dark:text-gray-200 px-3 py-1.5 rounded-md hover:bg-gray-300 transition">
                      {{ t('cars.cancel') }}
                    </button>
                  </div>
                </div>

                <!-- SoH Verlaufsliste -->
                <div v-if="sohHistory.length === 0 && !showSohAddForm" class="text-xs text-gray-400 dark:text-gray-500 italic">
                  {{ t('cars.soh_history_empty') }}
                </div>
                <div v-else class="space-y-1">
                  <div v-for="entry in sohHistory" :key="entry.id"
                    class="flex items-center justify-between py-1.5 px-2 rounded bg-gray-50 dark:bg-gray-600 text-sm">
                    <div class="flex items-center gap-3">
                      <span class="font-semibold text-gray-800 dark:text-gray-100">{{ entry.sohPercent }}%</span>
                      <span class="text-gray-500 dark:text-gray-400 text-xs">{{ entry.recordedAt }}</span>
                    </div>
                    <div class="flex gap-2">
                      <button type="button" @click="openSohEditForm(entry)"
                        class="text-xs text-indigo-500 hover:text-indigo-700">{{ t('cars.soh_correct_btn') }}</button>
                      <button type="button" @click="deleteSohEntry(entry)"
                        class="text-xs text-red-400 hover:text-red-600">{{ t('cars.soh_delete_btn') }}</button>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div class="flex gap-3 pt-2">
            <button type="submit"
              v-haptic
              class="btn-3d bg-indigo-600 text-white px-6 py-2 rounded-md hover:bg-indigo-700 transition">
              {{ t('cars.update_btn') }}
            </button>
            <button type="button" @click="resetForm"
              v-haptic
              class="btn-3d bg-gray-200 dark:bg-gray-600 text-gray-700 dark:text-gray-200 px-6 py-2 rounded-md hover:bg-gray-300 dark:hover:bg-gray-500 transition">
              {{ t('cars.cancel') }}
            </button>
          </div>
        </form>
      </div>
    </div>

    <!-- WLTP Question Overlay -->
    <div v-if="showWltpQuestion" class="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 p-4">
      <div class="bg-white dark:bg-gray-800 rounded-xl shadow-2xl max-w-md w-full p-6">
        <h3 class="text-xl font-bold text-gray-800 dark:text-gray-200 mb-4">🎯 {{ t('cars.wltp_question_title') }}</h3>
        <p class="text-gray-600 dark:text-gray-400 mb-6">
          {{ t('cars.wltp_question_desc') }}
        </p>
        <div class="flex gap-3">
          <button
            @click="openWltpForm"
            class="flex-1 bg-green-600 text-white px-6 py-3 rounded-lg font-semibold hover:bg-green-700 transition shadow-md">
            {{ t('cars.wltp_yes') }}
          </button>
          <button
            @click="closeWltpQuestion"
            class="flex-1 bg-red-100 text-red-700 px-6 py-3 rounded-lg font-semibold hover:bg-red-200 transition">
            {{ t('cars.wltp_no') }}
          </button>
        </div>
      </div>
    </div>

    <!-- WLTP Form Overlay -->
    <div v-if="showWltpForm" class="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 p-4">
      <div class="bg-white dark:bg-gray-800 rounded-xl shadow-2xl max-w-lg w-full p-6">
        <div class="flex items-center gap-2 mb-4">
          <ChartBarIcon class="h-6 w-6 text-gray-700 dark:text-gray-300" />
          <h3 class="text-xl font-bold text-gray-800 dark:text-gray-200">{{ t('cars.wltp_form_title') }}</h3>
        </div>
        <p class="text-sm text-gray-600 dark:text-gray-400 mb-4">
          {{ t('cars.wltp_form_for') }}: <span class="font-semibold">{{ selectedBrand }} {{ getModelLabel(selectedModel) }}</span>
          ({{ finalCapacity }} kWh)
        </p>

        <form @submit.prevent="submitWltpData" class="space-y-4">
          <div>
            <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
              {{ t('cars.wltp_range_label') }}
            </label>
            <div class="relative">
              <input
                v-model.number="wltpRangeKm"
                type="number"
                step="0.1"
                min="0"
                max="2000"
                required
                :placeholder="t('cars.wltp_range_placeholder')"
                class="w-full rounded-md border-gray-300 dark:border-gray-600 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 p-3 border pr-12 dark:bg-gray-700 dark:text-gray-100" />
              <span class="absolute right-3 top-3 text-gray-500 dark:text-gray-400 text-sm">{{ distanceUnitLabel() }}</span>
            </div>
          </div>

          <div>
            <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
              {{ t('cars.wltp_consumption_label') }}
            </label>
            <div class="relative">
              <input
                v-model.number="wltpConsumptionKwhPer100km"
                type="number"
                step="0.1"
                min="0"
                max="100"
                required
                :placeholder="t('cars.wltp_consumption_placeholder')"
                class="w-full rounded-md border-gray-300 dark:border-gray-600 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 p-3 border pr-24 dark:bg-gray-700 dark:text-gray-100" />
              <span class="absolute right-3 top-3 text-gray-500 dark:text-gray-400 text-sm">{{ consumptionUnitLabel() }}</span>
            </div>
          </div>

          <div class="flex gap-3 pt-2">
            <button
              type="submit"
              class="flex-1 bg-indigo-600 text-white px-6 py-3 rounded-lg font-semibold hover:bg-indigo-700 transition shadow-md">
              {{ t('cars.wltp_save_btn') }}
            </button>
            <button
              type="button"
              @click="closeWltpForm"
              class="flex-1 bg-gray-200 dark:bg-gray-600 text-gray-700 dark:text-gray-200 px-6 py-3 rounded-lg font-semibold hover:bg-gray-300 dark:hover:bg-gray-500 transition">
              {{ t('cars.cancel') }}
            </button>
          </div>
        </form>
      </div>
    </div>

    <!-- Tesla Fleet Integration (only shown when user has a Tesla) -->
    <div class="md:max-w-4xl md:mx-auto px-4 md:px-0 pb-4">
      <!-- Import Hub Hint -->
      <router-link
        to="/imports"
        class="flex items-center gap-3 bg-gray-50 dark:bg-gray-700 border border-gray-200 dark:border-gray-600 rounded-xl px-4 py-3 hover:bg-gray-100 dark:hover:bg-gray-600 transition group"
      >
        <ArrowDownTrayIcon class="h-5 w-5 text-gray-500 dark:text-gray-400 shrink-0" />
        <div>
          <span class="text-sm font-medium text-gray-800 dark:text-gray-200">{{ t('cars.import_title') }}</span>
          <span class="text-sm text-gray-500 dark:text-gray-400 ml-1">— Tesla, Sprit-Monitor, go-eCharger Cloud, OCPP Wallbox</span>
        </div>
        <span class="text-gray-400 dark:text-gray-500 ml-auto group-hover:translate-x-0.5 transition-transform">→</span>
      </router-link>
    </div>
      </div>
    </Transition>

    <!-- Toast Notification (outside Transition) -->
    <div v-if="showToast" class="fixed bottom-6 right-6 z-50 animate-slide-in">
      <div class="bg-green-600 text-white px-6 py-4 rounded-lg shadow-2xl max-w-md">
        <div class="flex items-start">
          <svg class="w-6 h-6 mr-3 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"></path>
          </svg>
          <p class="text-sm font-medium">{{ toastMessage }}</p>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.fade-enter-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from {
  opacity: 0;
}

.fade-enter-to {
  opacity: 1;
}
</style>

<style scoped>
@keyframes slide-in {
  from {
    transform: translateX(100%);
    opacity: 0;
  }
  to {
    transform: translateX(0);
    opacity: 1;
  }
}

.animate-slide-in {
  animation: slide-in 0.3s ease-out;
}
</style>
