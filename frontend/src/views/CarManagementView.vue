<script setup lang="ts">
import { ref, onMounted, watch, computed } from 'vue'
import { carService, type Car, type CarRequest, type BrandInfo, type ModelInfo } from '../api/carService'
import { vehicleSpecificationService, type VehicleSpecification } from '../api/vehicleSpecificationService'
import TeslaIntegration from '../components/TeslaIntegration.vue'
import { ChartBarIcon, TruckIcon } from '@heroicons/vue/24/outline'

const cars = ref<Car[]>([])
const brands = ref<BrandInfo[]>([])
const availableModels = ref<ModelInfo[]>([])
const loading = ref(false)
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

// WLTP data
const wltpData = ref<VehicleSpecification | null>(null)
const showWltpQuestion = ref(false)
const showWltpForm = ref(false)
const wltpRangeKm = ref<number | null>(null)
const wltpConsumptionKwhPer100km = ref<number | null>(null)
const showToast = ref(false)
const toastMessage = ref('')

const sortedBrands = computed(() => {
  return [...brands.value].sort((a, b) => a.label.localeCompare(b.label))
})

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

// Check if user owns a Tesla
const hasTesla = computed(() => {
  const teslaModels = ['MODEL_3', 'MODEL_Y', 'MODEL_S', 'MODEL_X', 'CYBERTRUCK', 'ROADSTER']
  return cars.value.some(car => teslaModels.includes(car.model))
})

const fetchCars = async () => {
  try {
    loading.value = true
    error.value = null
    cars.value = await carService.getCars()
  } catch (err: any) {
    error.value = err.response?.data?.message || 'Fehler beim Laden der Fahrzeuge'
    console.error('Failed to fetch cars:', err)
  } finally {
    loading.value = false
  }
}

const fetchBrands = async () => {
  try {
    brands.value = await carService.getBrands()
  } catch (err: any) {
    error.value = 'Fehler beim Laden der Marken'
    console.error('Failed to fetch brands:', err)
  }
}

const loadModelsForBrand = async (brand: string) => {
  if (!brand) {
    availableModels.value = []
    return
  }

  try {
    availableModels.value = await carService.getModelsForBrand(brand)
  } catch (err: any) {
    error.value = 'Fehler beim Laden der Modelle'
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
    selectedModel.value = ''
    selectedCapacity.value = null
  }
})

watch(selectedModel, () => {
  if (!editingCar.value) {
    selectedCapacity.value = null
    useCustomCapacity.value = false
    customCapacity.value = null
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
  showForm.value = true
}

const submitForm = async () => {
  try {
    error.value = null

    if (!finalCapacity.value) {
      error.value = 'Bitte wähle oder gib eine Batteriekapazität ein'
      return
    }

    const carData: CarRequest = {
      model: selectedModel.value,
      year: year.value,
      licensePlate: licensePlate.value,
      trim: trim.value || null,
      batteryCapacityKwh: finalCapacity.value,
      powerKw: powerKw.value
    }

    if (editingCar.value) {
      await carService.updateCar(editingCar.value.id, carData)
    } else {
      await carService.createCar(carData)
    }

    resetForm()
    await fetchCars()
  } catch (err: any) {
    error.value = err.response?.data?.message || 'Fehler beim Speichern des Fahrzeugs'
    console.error('Failed to save car:', err)
  }
}

const deleteCar = async (id: string) => {
  if (!confirm('Dieses Fahrzeug löschen? Diese Aktion kann nicht rückgängig gemacht werden.')) return

  try {
    error.value = null
    await carService.deleteCar(id)
    await fetchCars()
  } catch (err: any) {
    error.value = err.response?.data?.message || 'Fehler beim Löschen des Fahrzeugs'
    console.error('Failed to delete car:', err)
  }
}

const getModelLabel = (modelValue: string): string => {
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
    error.value = 'Bitte fülle alle WLTP-Felder aus'
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
    toastMessage.value = `🎉 Danke! ${response.coinsAwarded} Coins erhalten! Die Community profitiert von deinen Daten.`
    showToast.value = true

    // Auto-hide toast after 5 seconds
    setTimeout(() => {
      showToast.value = false
    }, 5000)

    // Reload WLTP data
    wltpData.value = response.specification
  } catch (err: any) {
    error.value = err.response?.data?.message || 'Fehler beim Speichern der WLTP-Daten'
    console.error('Failed to save WLTP data:', err)
  }
}

onMounted(async () => {
  await fetchBrands()
  await fetchCars()
})
</script>

<template>
  <div class="md:max-w-4xl md:mx-auto md:p-6">
    <div class="bg-white md:rounded-xl md:shadow-lg p-4 md:p-6">
      <div class="flex justify-between items-center mb-6">
        <h1 class="text-3xl font-bold text-gray-800">Meine Fahrzeuge</h1>
        <button
          v-if="!showForm"
          @click="openAddForm"
          class="bg-indigo-600 text-white px-4 py-2 rounded-md shadow hover:bg-indigo-700 transition">
          Fahrzeug hinzufügen
        </button>
      </div>

      <div v-if="error" class="mb-4 p-4 bg-red-50 border border-red-200 text-red-700 rounded-md">
        {{ error }}
      </div>

      <!-- Form -->
      <div v-if="showForm" class="mb-8 p-6 bg-gray-50 rounded-lg border border-gray-200">
        <h2 class="text-xl font-semibold mb-4 text-gray-800">
          {{ editingCar ? 'Fahrzeug bearbeiten' : 'Neues Fahrzeug hinzufügen' }}
        </h2>

        <form @submit.prevent="submitForm" class="space-y-4">
          <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Marke *</label>
              <select v-model="selectedBrand" required
                class="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 p-2 border">
                <option value="">Marke wählen...</option>
                <option v-for="brand in sortedBrands" :key="brand.value" :value="brand.value">
                  {{ brand.label }}
                </option>
              </select>
            </div>

            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Modell *</label>
              <select v-model="selectedModel" required :disabled="!selectedBrand"
                class="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 p-2 border disabled:bg-gray-100">
                <option value="">{{ selectedBrand ? 'Modell wählen...' : 'Erst Marke wählen...' }}</option>
                <option v-for="m in availableModels" :key="m.value" :value="m.value">
                  {{ m.label }}
                </option>
              </select>
            </div>

            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Ausstattungslinie (optional)</label>
              <input
                v-model="trim"
                type="text"
                placeholder="z.B. GTX, Pro Performance, Long Range"
                class="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 p-2 border" />
              <p class="text-xs text-gray-500 mt-1">Variante/Ausstattung</p>
            </div>

            <!-- Capacity Selection -->
            <div v-if="selectedModel" class="md:col-span-2">
              <label class="block text-sm font-medium text-gray-700 mb-2">Batteriekapazität (kWh) *</label>

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
                  Eigene Kapazität eingeben
                </button>
              </div>

              <div v-else class="space-y-2">
                <input
                  v-model.number="customCapacity"
                  type="number"
                  step="0.1"
                  min="0"
                  required
                  placeholder="Eigene Kapazität eingeben (z.B. 82,5)"
                  class="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 p-2 border"
                />
                <button
                  type="button"
                  @click="useCustomCapacity = false; customCapacity = null"
                  class="text-sm text-indigo-600 hover:text-indigo-700 underline">
                  Aus verfügbaren Kapazitäten wählen
                </button>
              </div>
            </div>

            <!-- WLTP Info (if available) -->
            <div v-if="wltpData && !editingCar" class="md:col-span-2">
              <div class="p-4 bg-blue-50 border border-blue-200 rounded-lg">
                <div class="flex items-start">
                  <svg class="w-5 h-5 text-blue-600 mt-0.5 mr-3 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path>
                  </svg>
                  <div>
                    <p class="text-sm font-medium text-blue-900">WLTP-Werte verfügbar</p>
                    <p class="text-sm text-blue-700 mt-1">
                      Reichweite: <span class="font-semibold">{{ wltpData.wltpRangeKm }} km</span>
                      | Verbrauch: <span class="font-semibold">{{ wltpData.wltpConsumptionKwhPer100km }} kWh/100km</span>
                    </p>
                  </div>
                </div>
              </div>
            </div>

            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Baujahr *</label>
              <input v-model="year" type="number" required min="2000" :max="new Date().getFullYear() + 1"
                class="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 p-2 border" />
            </div>

            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Kennzeichen (optional)</label>
              <input v-model="licensePlate" type="text" placeholder="z.B. M-EV 123"
                class="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 p-2 border" />
            </div>

            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Leistung (optional)</label>
              <input
                v-model.number="powerKw"
                type="number"
                step="0.1"
                min="0"
                placeholder="z.B. 150"
                class="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 p-2 border" />
              <p v-if="powerPs" class="text-xs text-gray-500 mt-1">≈ {{ powerPs }} PS</p>
              <p v-else class="text-xs text-gray-500 mt-1">Gib kW ein um PS-Umrechnung zu sehen</p>
            </div>
          </div>

          <div class="flex gap-3 pt-2">
            <button type="submit"
              class="bg-indigo-600 text-white px-6 py-2 rounded-md shadow hover:bg-indigo-700 transition">
              {{ editingCar ? 'Aktualisieren' : 'Hinzufügen' }}
            </button>
            <button type="button" @click="resetForm"
              class="bg-gray-200 text-gray-700 px-6 py-2 rounded-md shadow hover:bg-gray-300 transition">
              Abbrechen
            </button>
          </div>
        </form>
      </div>

      <!-- Cars List -->
      <div v-if="loading" class="text-center py-8 text-gray-500">Lade Fahrzeuge...</div>

      <!-- Empty State: No Cars -->
      <div v-else-if="cars.length === 0 && !showForm" class="text-center py-16 px-4">
        <TruckIcon class="h-24 w-24 mx-auto text-gray-300 mb-6" />
        <h3 class="text-2xl font-bold text-gray-800 mb-3">Noch keine Fahrzeuge</h3>
        <p class="text-gray-600 mb-8 max-w-md mx-auto">
          Füge dein erstes E-Auto hinzu um Ladevorgänge zu erfassen und Statistiken zu sehen!
        </p>
        <button
          @click="openAddForm"
          class="inline-flex items-center gap-2 px-8 py-4 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 font-medium shadow-lg hover:shadow-xl transition">
          <TruckIcon class="h-5 w-5" />
          Erstes Fahrzeug hinzufügen
        </button>
        <div class="mt-8 grid grid-cols-1 sm:grid-cols-3 gap-4 max-w-2xl mx-auto text-left">
          <div class="bg-blue-50 border border-blue-200 rounded-lg p-4">
            <div class="text-blue-600 mb-2">📊</div>
            <p class="text-sm font-semibold text-gray-800 mb-1">100+ Modelle</p>
            <p class="text-xs text-gray-600">68 Marken in unserer Datenbank</p>
          </div>
          <div class="bg-green-50 border border-green-200 rounded-lg p-4">
            <div class="text-green-600 mb-2">⚡</div>
            <p class="text-sm font-semibold text-gray-800 mb-1">WLTP-Daten</p>
            <p class="text-xs text-gray-600">Automatischer Verbrauchsvergleich</p>
          </div>
          <div class="bg-purple-50 border border-purple-200 rounded-lg p-4">
            <div class="text-purple-600 mb-2">🔒</div>
            <p class="text-sm font-semibold text-gray-800 mb-1">Privat</p>
            <p class="text-xs text-gray-600">Deine Daten bleiben bei dir</p>
          </div>
        </div>
      </div>

      <div v-else class="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div v-for="car in cars" :key="car.id"
          class="p-5 bg-gray-50 border border-gray-200 rounded-lg shadow-sm hover:shadow-md transition">
          <div class="flex justify-between items-start mb-3">
            <div>
              <h3 class="text-xl font-bold text-indigo-700">
                {{ getModelLabel(car.model) }}
                <span v-if="car.trim" class="text-base font-normal text-indigo-600">{{ car.trim }}</span>
              </h3>
              <p class="text-sm text-gray-600">{{ car.year }}</p>
            </div>
            <span class="px-3 py-1 bg-white border border-gray-300 text-xs rounded-full shadow-sm text-gray-600 font-medium">
              {{ car.licensePlate }}
            </span>
          </div>

          <div class="mb-4 space-y-1">
            <p class="text-sm text-gray-600">
              <span class="font-semibold">Batterie:</span> {{ car.batteryCapacityKwh }} kWh
            </p>
            <p v-if="car.powerKw" class="text-sm text-gray-600">
              <span class="font-semibold">Leistung:</span> {{ car.powerKw }} kW ({{ Math.round(car.powerKw * 1.35962) }} PS)
            </p>
          </div>

          <div class="flex gap-2">
            <button @click="openEditForm(car)"
              class="flex-1 bg-indigo-100 text-indigo-700 px-3 py-2 rounded-md text-sm font-medium hover:bg-indigo-200 transition">
              Bearbeiten
            </button>
            <button @click="deleteCar(car.id)"
              class="flex-1 bg-red-100 text-red-700 px-3 py-2 rounded-md text-sm font-medium hover:bg-red-200 transition">
              Löschen
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- WLTP Question Overlay -->
    <div v-if="showWltpQuestion" class="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 p-4">
      <div class="bg-white rounded-xl shadow-2xl max-w-md w-full p-6">
        <h3 class="text-xl font-bold text-gray-800 mb-4">🎯 WLTP-Werte fehlen!</h3>
        <p class="text-gray-600 mb-6">
          Wir haben noch keine WLTP-Werte für dieses Modell in unserer Datenbank.
          Möchtest du diese angeben und dadurch Punkte sammeln?
        </p>
        <div class="flex gap-3">
          <button
            @click="openWltpForm"
            class="flex-1 bg-green-600 text-white px-6 py-3 rounded-lg font-semibold hover:bg-green-700 transition shadow-md">
            Ja, Punkte sammeln! ✨
          </button>
          <button
            @click="closeWltpQuestion"
            class="flex-1 bg-red-100 text-red-700 px-6 py-3 rounded-lg font-semibold hover:bg-red-200 transition">
            Nein, danke
          </button>
        </div>
      </div>
    </div>

    <!-- WLTP Form Overlay -->
    <div v-if="showWltpForm" class="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 p-4">
      <div class="bg-white rounded-xl shadow-2xl max-w-lg w-full p-6">
        <div class="flex items-center gap-2 mb-4">
          <ChartBarIcon class="h-6 w-6 text-gray-700" />
          <h3 class="text-xl font-bold text-gray-800">WLTP-Werte eingeben</h3>
        </div>
        <p class="text-sm text-gray-600 mb-4">
          Für: <span class="font-semibold">{{ selectedBrand }} {{ getModelLabel(selectedModel) }}</span>
          ({{ finalCapacity }} kWh)
        </p>

        <form @submit.prevent="submitWltpData" class="space-y-4">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">
              Reichweite (WLTP Combined) *
            </label>
            <div class="relative">
              <input
                v-model.number="wltpRangeKm"
                type="number"
                step="0.1"
                min="0"
                max="2000"
                required
                placeholder="z.B. 450"
                class="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 p-3 border pr-12" />
              <span class="absolute right-3 top-3 text-gray-500 text-sm">km</span>
            </div>
          </div>

          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">
              Verbrauch (WLTP Combined) *
            </label>
            <div class="relative">
              <input
                v-model.number="wltpConsumptionKwhPer100km"
                type="number"
                step="0.1"
                min="0"
                max="100"
                required
                placeholder="z.B. 16.5"
                class="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 p-3 border pr-24" />
              <span class="absolute right-3 top-3 text-gray-500 text-sm">kWh/100km</span>
            </div>
          </div>

          <div class="flex gap-3 pt-2">
            <button
              type="submit"
              class="flex-1 bg-indigo-600 text-white px-6 py-3 rounded-lg font-semibold hover:bg-indigo-700 transition shadow-md">
              Speichern & Coins erhalten 🎉
            </button>
            <button
              type="button"
              @click="closeWltpForm"
              class="flex-1 bg-gray-200 text-gray-700 px-6 py-3 rounded-lg font-semibold hover:bg-gray-300 transition">
              Abbrechen
            </button>
          </div>
        </form>
      </div>
    </div>

    <!-- Tesla Integration (only shown if user owns a Tesla) -->
    <TeslaIntegration v-if="hasTesla" />

    <!-- Toast Notification -->
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
