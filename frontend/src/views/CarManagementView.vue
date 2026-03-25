<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { carService, type Car, type CarRequest, type BrandInfo, type ModelInfo, type CarCreateResponse } from '../api/carService'
import { useCarStore } from '../stores/car'
import { vehicleSpecificationService, type VehicleSpecification } from '../api/vehicleSpecificationService'
import { ChartBarIcon, TruckIcon, ArrowDownTrayIcon } from '@heroicons/vue/24/outline'
import { useCoinStore } from '../stores/coins'
import LicensePlate from '../components/LicensePlate.vue'
import { analytics } from '../services/analytics'
import { useTeslaStatus } from '../composables/useTeslaStatus'

const { t } = useI18n()
const coinStore = useCoinStore()
const carStore = useCarStore()

const { teslaStatus, start: startTeslaPolling } = useTeslaStatus()

const cars = ref<Car[]>([])
const brands = ref<BrandInfo[]>([])
const availableModels = ref<ModelInfo[]>([])
const loading = ref(true) // Initial true to prevent flicker on mount
const error = ref<string | null>(null)
const showForm = ref(false)
const editingCar = ref<Car | null>(null)

// Form fields
const selectedBrand = ref('')
const selectedModel = ref('')
const year = ref<number>(new Date().getFullYear())
const licensePlate = ref('')
const trim = ref('')
const selectedCapacity = ref<number | null>(null)
const customCapacity = ref<number | null>(null)
const useCustomCapacity = ref(false)
const powerKw = ref<number | null>(null)
const batteryDegradationPercent = ref<number | null>(null)
const showAdvancedSettings = ref(false)

// WLTP data
const wltpData = ref<VehicleSpecification | null>(null)
const showWltpQuestion = ref(false)
const showWltpForm = ref(false)
const wltpRangeKm = ref<number | null>(null)
const wltpConsumptionKwhPer100km = ref<number | null>(null)
const showToast = ref(false)
const toastMessage = ref('')

// Image state
const imageBlobUrls = ref<Record<string, string>>({})
const imageUploading = ref<Record<string, boolean>>({})
const imagePublicForUpload = ref<Record<string, boolean>>({})
const visibilityTimers: Record<string, ReturnType<typeof setTimeout>> = {}

const sortedBrands = computed(() => {
  return [...brands.value].sort((a, b) => a.label.localeCompare(b.label))
})

const isSonstige = computed(() => selectedBrand.value === 'SONSTIGE')

const selectedModelCapacities = computed(() => {
  if (!selectedModel.value) return []
  const model = availableModels.value.find(m => m.value === selectedModel.value)
  return model?.capacities || []
})

const finalCapacity = computed(() => {
  if (useCustomCapacity.value) return customCapacity.value
  return selectedCapacity.value
})

// kW to PS conversion (1 kW = 1.35962 PS)
const powerPs = computed(() => {
  if (!powerKw.value) return null
  return Math.round(powerKw.value * 1.35962)
})

const revokeAllBlobUrls = () => {
  Object.values(imageBlobUrls.value).forEach(url => URL.revokeObjectURL(url))
  imageBlobUrls.value = {}
}

const loadCarImages = async (carList: Car[]) => {
  const carsWithImages = carList.filter(c => c.imageUrl)
  for (const car of carsWithImages) {
    try {
      const blobUrl = await carService.getCarImageBlobUrl(car.id)
      imageBlobUrls.value = { ...imageBlobUrls.value, [car.id]: blobUrl }
    } catch {
      // Image might not be accessible (e.g. private) — ignore silently
    }
  }
}

const fetchCars = async () => {
  try {
    loading.value = true
    error.value = null
    revokeAllBlobUrls()
    cars.value = await carStore.getCars(true) ?? []
    // Init checkbox state from server (shows current visibility per car)
    const visibility: Record<string, boolean> = {}
    cars.value.forEach(c => { visibility[c.id] = c.imagePublic })
    imagePublicForUpload.value = visibility
    await loadCarImages(cars.value)
    await startTeslaPolling(cars.value.some((c: any) => c.brand?.toLowerCase() === 'tesla'))
    // Small delay to ensure fade-in transition is visible
    await new Promise(resolve => setTimeout(resolve, 150))
  } catch (err: any) {
    error.value = err.response?.data?.message || t('cars.error_load')
    console.error('Failed to fetch cars:', err)
  } finally {
    loading.value = false
  }
}

const fetchBrands = async () => {
  try {
    brands.value = await carStore.getBrands()
  } catch (err: any) {
    error.value = t('cars.error_brands')
    console.error('Failed to fetch brands:', err)
  }
}

const loadModelsForBrand = async (brand: string) => {
  if (!brand) {
    availableModels.value = []
    return
  }

  try {
    availableModels.value = await carStore.getModelsForBrand(brand)
  } catch (err: any) {
    error.value = t('cars.error_models')
    console.error('Failed to fetch models:', err)
  }
}

let suppressNextBrandWatch = false

watch(selectedBrand, (newBrand) => {
  if (suppressNextBrandWatch) {
    suppressNextBrandWatch = false
    return
  }
  if (newBrand) {
    loadModelsForBrand(newBrand)
  } else {
    availableModels.value = []
  }
  if (!editingCar.value) {
    selectedCapacity.value = null
    if (newBrand === 'SONSTIGE') {
      selectedModel.value = 'SONSTIGE_CUSTOM'
      useCustomCapacity.value = true
    } else {
      selectedModel.value = ''
      useCustomCapacity.value = false
    }
  }
})

watch(selectedModel, (newModel) => {
  if (!editingCar.value) {
    selectedCapacity.value = null
    if (newModel === 'SONSTIGE_CUSTOM') {
      useCustomCapacity.value = true
    } else {
      useCustomCapacity.value = false
      customCapacity.value = null
    }
  }
  // Reset WLTP data when model changes
  wltpData.value = null
})

// Watch for capacity changes to lookup WLTP data
watch([selectedBrand, selectedModel, finalCapacity], async ([brand, model, capacity]) => {
  wltpData.value = null
  showWltpQuestion.value = false

  if (!brand || !model || !capacity || editingCar.value) {
    return
  }

  await lookupWltpData()
})

const resetForm = () => {
  selectedBrand.value = ''
  selectedModel.value = ''
  year.value = new Date().getFullYear()
  licensePlate.value = ''
  trim.value = ''
  selectedCapacity.value = null
  customCapacity.value = null
  useCustomCapacity.value = false
  powerKw.value = null
  batteryDegradationPercent.value = null
  showAdvancedSettings.value = false
  editingCar.value = null
  showForm.value = false
  availableModels.value = []
}

const openAddForm = () => {
  resetForm()
  showForm.value = true
}

const openEditForm = async (car: Car) => {
  editingCar.value = car
  suppressNextBrandWatch = true
  selectedBrand.value = car.brand
  await loadModelsForBrand(car.brand)
  selectedModel.value = car.model

  const foundModel = availableModels.value.find(m => m.value === car.model)
  if (foundModel && foundModel.capacities.includes(car.batteryCapacityKwh)) {
    selectedCapacity.value = car.batteryCapacityKwh
    useCustomCapacity.value = false
  } else {
    customCapacity.value = car.batteryCapacityKwh
    useCustomCapacity.value = true
  }

  year.value = car.year
  licensePlate.value = car.licensePlate
  trim.value = car.trim || ''
  powerKw.value = car.powerKw
  batteryDegradationPercent.value = car.batteryDegradationPercent
  showAdvancedSettings.value = car.batteryDegradationPercent != null
  showForm.value = true
}

const submitForm = async () => {
  try {
    error.value = null

    if (!finalCapacity.value) {
      error.value = t('cars.error_capacity')
      return
    }

    const carData: CarRequest = {
      model: selectedModel.value,
      year: year.value,
      licensePlate: licensePlate.value,
      trim: trim.value || null,
      batteryCapacityKwh: finalCapacity.value,
      powerKw: powerKw.value,
      batteryDegradationPercent: batteryDegradationPercent.value
    }

    if (editingCar.value) {
      await carService.updateCar(editingCar.value.id, carData)
      resetForm()
      await fetchCars()
    } else {
      const result: CarCreateResponse = await carService.createCar(carData)
      resetForm()
      await fetchCars()
      coinStore.refresh()
      const isFirst = result.coinsAwarded === 20
      analytics.trackCarAdded(isFirst)
      toastMessage.value = isFirst
        ? t('cars.toast_first', { n: result.coinsAwarded })
        : t('cars.toast_coins', { n: result.coinsAwarded })
      showToast.value = true
      setTimeout(() => { showToast.value = false }, 5000)
    }
  } catch (err: any) {
    error.value = err.response?.data?.message || t('cars.error_save')
    console.error('Failed to save car:', err)
  }
}

const deleteCar = async (id: string) => {
  if (!confirm(t('cars.confirm_delete'))) return

  try {
    error.value = null
    await carService.deleteCar(id)
    await fetchCars()
  } catch (err: any) {
    error.value = err.response?.data?.message || t('cars.error_delete')
    console.error('Failed to delete car:', err)
  }
}

const setActiveCar = async (id: string) => {
  try {
    error.value = null
    const updatedCar = await carService.setActiveCar(id)
    cars.value = cars.value.map(c => ({
      ...c,
      isPrimary: c.id === updatedCar.id
    }))
    carStore.invalidateCars()
  } catch (err: any) {
    error.value = err.response?.data?.message || t('cars.error_activate')
    console.error('Failed to set active car:', err)
  }
}

const getModelLabel = (modelValue: string | null | undefined): string => {
  if (!modelValue) return ''
  const model = availableModels.value.find(m => m.value === modelValue)
  if (model) return model.label
  return modelValue.replace(/_/g, ' ').toLowerCase()
    .split(' ')
    .map(word => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ')
}

// WLTP Lookup
const lookupWltpData = async () => {
  if (!selectedBrand.value || !selectedModel.value || !finalCapacity.value) {
    return
  }

  try {
    const data = await vehicleSpecificationService.lookup(
      selectedBrand.value,
      selectedModel.value,
      finalCapacity.value
    )

    if (data) {
      wltpData.value = data
    } else {
      // No WLTP data found - ask user if they want to contribute
      showWltpQuestion.value = true
    }
  } catch (err) {
    console.error('Failed to lookup WLTP data:', err)
  }
}

const closeWltpQuestion = () => {
  showWltpQuestion.value = false
}

const openWltpForm = () => {
  showWltpQuestion.value = false
  showWltpForm.value = true
  wltpRangeKm.value = null
  wltpConsumptionKwhPer100km.value = null
}

const closeWltpForm = () => {
  showWltpForm.value = false
  wltpRangeKm.value = null
  wltpConsumptionKwhPer100km.value = null
}

const submitWltpData = async () => {
  if (!wltpRangeKm.value || !wltpConsumptionKwhPer100km.value) {
    error.value = t('cars.error_wltp')
    return
  }

  try {
    error.value = null
    const response = await vehicleSpecificationService.create({
      carBrand: selectedBrand.value,
      carModel: selectedModel.value,
      batteryCapacityKwh: finalCapacity.value!,
      wltpRangeKm: wltpRangeKm.value,
      wltpConsumptionKwhPer100km: wltpConsumptionKwhPer100km.value
    })

    // Close form and show toast
    closeWltpForm()
    toastMessage.value = t('cars.toast_wltp', { n: response.coinsAwarded })
    showToast.value = true

    // Auto-hide toast after 5 seconds
    setTimeout(() => {
      showToast.value = false
    }, 5000)

    // Reload WLTP data
    wltpData.value = response.specification
  } catch (err: any) {
    error.value = err.response?.data?.message || t('cars.error_wltp_save')
    console.error('Failed to save WLTP data:', err)
  }
}

const handleVisibilityChange = (carId: string, isPublic: boolean) => {
  imagePublicForUpload.value = { ...imagePublicForUpload.value, [carId]: isPublic }

  const car = cars.value.find(c => c.id === carId)
  if (!car?.imageUrl) return // No image yet — just store preference for next upload

  // Debounce: cancel pending request, wait 500ms before sending
  clearTimeout(visibilityTimers[carId])
  visibilityTimers[carId] = setTimeout(async () => {
    try {
      const result = await carService.updateCarImageVisibility(carId, isPublic)
      cars.value = cars.value.map(c => c.id === carId ? result.car : c)
      carStore.invalidateCars()
      if (result.coinsAwarded > 0) {
        coinStore.refresh()
        toastMessage.value = t('cars.toast_image_public', { n: result.coinsAwarded })
        showToast.value = true
        setTimeout(() => { showToast.value = false }, 5000)
      }
    } catch (err: any) {
      error.value = err.response?.data?.message || t('cars.error_visibility')
    }
  }, 500)
}

const handleImageUpload = async (carId: string, event: Event) => {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return

  imageUploading.value = { ...imageUploading.value, [carId]: true }
  try {
    error.value = null
    const isPublic = imagePublicForUpload.value[carId] ?? false
    const result = await carService.uploadCarImage(carId, file, isPublic)
    // Update car in list
    cars.value = cars.value.map(c => c.id === carId ? result.car : c)
    carStore.invalidateCars()
    if (result.coinsAwarded > 0) {
      coinStore.refresh()
      toastMessage.value = t('cars.toast_upload', { n: result.coinsAwarded })
      showToast.value = true
      setTimeout(() => { showToast.value = false }, 5000)
    }
    // Revoke old blob and load new one
    if (imageBlobUrls.value[carId]) URL.revokeObjectURL(imageBlobUrls.value[carId])
    const blobUrl = await carService.getCarImageBlobUrl(carId)
    imageBlobUrls.value = { ...imageBlobUrls.value, [carId]: blobUrl }
  } catch (err: any) {
    error.value = err.response?.data?.message || t('cars.error_upload')
    console.error('Failed to upload image:', err)
  } finally {
    imageUploading.value = { ...imageUploading.value, [carId]: false }
    input.value = ''
  }
}

const handleDeleteImage = async (carId: string) => {
  if (!confirm(t('cars.confirm_delete_image'))) return
  try {
    error.value = null
    await carService.deleteCarImage(carId)
    if (imageBlobUrls.value[carId]) URL.revokeObjectURL(imageBlobUrls.value[carId])
    const { [carId]: _, ...rest } = imageBlobUrls.value
    imageBlobUrls.value = rest
    // Update car in list
    cars.value = cars.value.map(c => c.id === carId ? { ...c, imageUrl: null, imagePublic: false } : c)
    carStore.invalidateCars()
  } catch (err: any) {
    error.value = err.response?.data?.message || t('cars.error_delete_image')
    console.error('Failed to delete image:', err)
  }
}

onMounted(async () => {
  await fetchBrands()
  await fetchCars()
})

onUnmounted(() => {
  revokeAllBlobUrls()
  Object.values(visibilityTimers).forEach(clearTimeout)
})
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

        <form @submit.prevent="submitForm" class="space-y-4">
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
                    :key="capacity"
                    type="button"
                    @click="selectedCapacity = capacity"
                    :class="[
                      'px-4 py-2 rounded-md text-sm font-medium transition',
                      selectedCapacity === capacity
                        ? 'bg-indigo-600 text-white shadow-md'
                        : 'bg-indigo-100 text-indigo-700 hover:bg-indigo-200'
                    ]">
                    {{ capacity }} kWh
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
                      {{ t('cars.wltp_range') }}: <span class="font-semibold">{{ wltpData.wltpRangeKm }} km</span>
                      | {{ t('cars.wltp_consumption') }}: <span class="font-semibold">{{ wltpData.wltpConsumptionKwhPer100km }} kWh/100km</span>
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

          <!-- Erweiterte Einstellungen -->
          <div class="mt-4 border-t dark:border-gray-600 pt-4">
            <button type="button" @click="showAdvancedSettings = !showAdvancedSettings"
              class="flex items-center gap-2 text-sm text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-200">
              <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor"
                class="w-4 h-4 transition-transform" :class="{ 'rotate-180': showAdvancedSettings }">
                <path stroke-linecap="round" stroke-linejoin="round" d="m19.5 8.25-7.5 7.5-7.5-7.5" />
              </svg>
              {{ t('cars.advanced_settings') }}
            </button>
            <div v-if="showAdvancedSettings" class="mt-3 space-y-2">
              <label class="block text-sm font-medium text-gray-700 dark:text-gray-300">{{ t('cars.label_degradation') }}</label>
              <div class="flex items-center gap-2">
                <input v-model.number="batteryDegradationPercent" type="number" step="0.1" min="0" max="50"
                  :placeholder="t('cars.degradation_placeholder')"
                  class="w-32 rounded-md border-gray-300 dark:border-gray-600 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 p-2 border dark:bg-gray-700 dark:text-gray-100" />
                <span class="text-sm text-gray-500 dark:text-gray-400">%</span>
                <button v-if="batteryDegradationPercent != null" type="button"
                  @click="batteryDegradationPercent = null"
                  class="text-xs text-red-500 hover:text-red-700 ml-2">{{ t('cars.reset') }}</button>
              </div>
              <p v-if="batteryDegradationPercent && finalCapacity" class="text-xs text-amber-600">
                {{ t('cars.effective_capacity', { eff: (finalCapacity * (1 - batteryDegradationPercent / 100)).toFixed(2), nom: finalCapacity }) }}
              </p>
              <p class="text-xs text-gray-400 dark:text-gray-500">{{ t('cars.hint_degradation') }}</p>
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

          <!-- Upload controls -->
          <div class="px-5 pt-3 pb-1 flex items-center gap-3">
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
                  ? 'bg-gray-100 dark:bg-gray-600 text-gray-400 dark:text-gray-500 cursor-not-allowed'
                  : 'bg-indigo-100 dark:bg-indigo-900/40 text-indigo-700 dark:text-indigo-300 hover:bg-indigo-200 dark:hover:bg-indigo-900/60'
              ]">
                {{ imageUploading[car.id] ? t('cars.uploading') : (car.imageUrl ? t('cars.change_photo') : t('cars.upload_photo')) }}
              </span>
              <input
                type="file"
                accept="image/jpeg,image/png"
                class="hidden"
                :disabled="imageUploading[car.id]"
                @change="handleImageUpload(car.id, $event)" />
            </label>
          </div>

          <div class="px-5 pt-2 pb-5">
            <div class="flex justify-between items-start mb-3">
              <div>
                <div class="flex items-center gap-2 flex-wrap">
                  <h3 class="text-xl font-bold text-indigo-700 dark:text-indigo-300">
                    {{ getModelLabel(car.model) }}
                    <span v-if="car.trim" class="text-base font-normal text-indigo-600 dark:text-indigo-400">{{ car.trim }}</span>
                  </h3>
                  <span v-if="car.isPrimary"
                    class="px-2 py-0.5 bg-green-100 text-green-700 text-xs rounded-full font-medium border border-green-200">
                    {{ t('cars.active') }}
                  </span>
                  <!-- Tesla vehicle state badge -->
                  <template v-if="car.brand?.toLowerCase() === 'tesla' && teslaStatus?.connected && (teslaStatus.carId === car.id || teslaStatus.carId === null)">
                    <span v-if="teslaStatus.vehicleState === 'charging'"
                      class="inline-flex items-center gap-1 px-2 py-0.5 bg-green-100 text-green-700 text-xs rounded-full font-medium border border-green-200">
                      <span class="w-1.5 h-1.5 rounded-full bg-green-500 animate-pulse"></span>
                      {{ t('cars.tesla_charging') }}
                    </span>
                    <span v-else-if="teslaStatus.vehicleState === 'online'"
                      class="inline-flex items-center gap-1 px-2 py-0.5 bg-blue-50 text-blue-600 text-xs rounded-full font-medium border border-blue-200">
                      <span class="w-1.5 h-1.5 rounded-full bg-blue-400"></span>
                      {{ t('cars.tesla_online') }}
                    </span>
                    <span v-else-if="teslaStatus.vehicleState === 'asleep'"
                      class="inline-flex items-center gap-1 px-2 py-0.5 bg-gray-100 dark:bg-gray-600 text-gray-500 dark:text-gray-300 text-xs rounded-full font-medium border border-gray-200 dark:border-gray-500">
                      <span class="w-1.5 h-1.5 rounded-full bg-gray-400"></span>
                      {{ t('cars.tesla_sleeping') }}
                    </span>
                  </template>
                </div>
                <p class="text-sm text-gray-600 dark:text-gray-400">{{ car.year }}</p>
              </div>
              <LicensePlate v-if="car.licensePlate" :plate="car.licensePlate" />
            </div>

            <div class="mb-4 space-y-1">
              <p class="text-sm text-gray-600 dark:text-gray-400">
                <span class="font-semibold">{{ t('cars.battery') }}</span> {{ car.batteryCapacityKwh }} kWh
                <span v-if="car.batteryDegradationPercent" class="text-amber-600 text-xs ml-1">
                  {{ t('cars.degradation_info', { pct: car.batteryDegradationPercent, kwh: car.effectiveBatteryCapacityKwh }) }}
                </span>
              </p>
              <p v-if="car.powerKw" class="text-sm text-gray-600 dark:text-gray-400">
                <span class="font-semibold">{{ t('cars.power') }}</span> {{ car.powerKw }} kW ({{ Math.round(car.powerKw * 1.35962) }} PS)
              </p>
              <p class="text-xs text-gray-400 dark:text-gray-500 font-mono select-all" :title="t('cars.id_hint')">{{ car.id }}</p>
            </div>

            <div class="flex gap-2">
              <button v-if="!car.isPrimary" @click="setActiveCar(car.id)"
                v-haptic
                class="btn-3d flex-1 bg-green-100 dark:bg-green-700 text-green-800 dark:text-white px-3 py-2 rounded-md text-sm font-medium hover:bg-green-200 dark:hover:bg-green-600 transition shadow-[0_4px_0_0_#86efac] dark:shadow-[0_4px_0_0_#15803d] active:shadow-none active:translate-y-1" style="transition: transform 0.075s ease, box-shadow 0.075s ease;">
                {{ t('cars.set_active_btn') }}
              </button>
              <button @click="openEditForm(car)"
                v-haptic
                class="btn-3d flex-1 bg-indigo-100 dark:bg-indigo-700 text-indigo-800 dark:text-white px-3 py-2 rounded-md text-sm font-medium hover:bg-indigo-200 dark:hover:bg-indigo-600 transition shadow-[0_4px_0_0_#a5b4fc] dark:shadow-[0_4px_0_0_#3730a3] active:shadow-none active:translate-y-1" style="transition: transform 0.075s ease, box-shadow 0.075s ease;">
                {{ t('cars.edit_btn') }}
              </button>
              <button @click="deleteCar(car.id)"
                v-haptic
                class="btn-3d flex-1 bg-red-100 dark:bg-red-700 text-red-800 dark:text-white px-3 py-2 rounded-md text-sm font-medium hover:bg-red-200 dark:hover:bg-red-600 transition shadow-[0_4px_0_0_#fca5a5] dark:shadow-[0_4px_0_0_#b91c1c] active:shadow-none active:translate-y-1" style="transition: transform 0.075s ease, box-shadow 0.075s ease;">
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

        <form @submit.prevent="submitForm" class="p-6 space-y-4">
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
                  <button v-for="capacity in selectedModelCapacities" :key="capacity" type="button"
                    @click="selectedCapacity = capacity"
                    :class="['px-4 py-2 rounded-md text-sm font-medium transition',
                      selectedCapacity === capacity ? 'bg-indigo-600 text-white shadow-md' : 'bg-indigo-100 text-indigo-700 hover:bg-indigo-200']">
                    {{ capacity }} kWh
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

          <!-- Erweiterte Einstellungen -->
          <div class="mt-4 border-t dark:border-gray-600 pt-4">
            <button type="button" @click="showAdvancedSettings = !showAdvancedSettings"
              class="flex items-center gap-2 text-sm text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-200">
              <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor"
                class="w-4 h-4 transition-transform" :class="{ 'rotate-180': showAdvancedSettings }">
                <path stroke-linecap="round" stroke-linejoin="round" d="m19.5 8.25-7.5 7.5-7.5-7.5" />
              </svg>
              {{ t('cars.advanced_settings') }}
            </button>
            <div v-if="showAdvancedSettings" class="mt-3 space-y-2">
              <label class="block text-sm font-medium text-gray-700 dark:text-gray-300">{{ t('cars.label_degradation') }}</label>
              <div class="flex items-center gap-2">
                <input v-model.number="batteryDegradationPercent" type="number" step="0.1" min="0" max="50"
                  :placeholder="t('cars.degradation_placeholder')"
                  class="w-32 rounded-md border-gray-300 dark:border-gray-600 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 p-2 border dark:bg-gray-700 dark:text-gray-100" />
                <span class="text-sm text-gray-500 dark:text-gray-400">%</span>
                <button v-if="batteryDegradationPercent != null" type="button"
                  @click="batteryDegradationPercent = null"
                  class="text-xs text-red-500 hover:text-red-700 ml-2">{{ t('cars.reset') }}</button>
              </div>
              <p v-if="batteryDegradationPercent && finalCapacity" class="text-xs text-amber-600">
                {{ t('cars.effective_capacity', { eff: (finalCapacity * (1 - batteryDegradationPercent / 100)).toFixed(2), nom: finalCapacity }) }}
              </p>
              <p class="text-xs text-gray-400 dark:text-gray-500">{{ t('cars.hint_degradation') }}</p>
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
              <span class="absolute right-3 top-3 text-gray-500 dark:text-gray-400 text-sm">km</span>
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
              <span class="absolute right-3 top-3 text-gray-500 dark:text-gray-400 text-sm">kWh/100km</span>
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
