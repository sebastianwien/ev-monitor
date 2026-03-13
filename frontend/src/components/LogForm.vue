<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import api from '../api/axios'
import CarSelector from './CarSelector.vue'
import OcrPhotoCapture from './OcrPhotoCapture.vue'
import { CameraIcon, PencilSquareIcon, TrashIcon, BoltIcon, TruckIcon, ClockIcon, Battery0Icon, SunIcon, GlobeAltIcon } from '@heroicons/vue/24/outline'
import { useCoinStore } from '../stores/coins'
import { analytics } from '../services/analytics'
import { carService } from '../api/carService'
import { tempBadgeClass } from '../utils/temperatureColor'
import ConsumptionInfoBox from './ConsumptionInfoBox.vue'
import EditLogModal from './EditLogModal.vue'

const coinStore = useCoinStore()

const emit = defineEmits<{
  success: []
}>()

const showOcrCapture = ref(window.innerWidth < 768)
const ocrUsed = ref(false)

// Toast notification
const showToast = ref(false)
const toastMessage = ref('')

const showCoinToast = (coins: number) => {
  toastMessage.value = `+${coins} Watt erhalten!`
  showToast.value = true
  setTimeout(() => { showToast.value = false }, 4000)
}

const selectedCarId = ref<string | null>(null)
const kwhCharged = ref<number>(0)
const costEur = ref<number>(0)
const chargeDurationMinutes = ref<number | null>(null)
const odometerKm = ref<number | null>(null) // Required: odometer reading
const socAfterChargePercent = ref<number | null>(null) // Required: battery level after charging (0-100%)
const maxChargingPowerKw = ref<number | null>(null) // Optional: max charging power
const loggedAt = ref<string | null>(null) // Optional: when the charge happened
const odometerWarning = ref<string | null>(null) // Warning if odometer is lower than last log
const chargingType = ref<'AC' | 'DC'>('AC') // AC or DC charging, default AC

// Location tracking
const latitude = ref<number | null>(null)
const longitude = ref<number | null>(null)
const locationEnabled = ref(localStorage.getItem('ev_location_enabled') === 'true')
const locationStatus = ref<'idle' | 'loading' | 'success' | 'error' | 'manual'>('idle')
const locationSearchQuery = ref('')
const locationSuggestions = ref<any[]>([])
const showLocationSuggestions = ref(false)
const locationErrorMessage = ref<string | null>(null)

const toggleLocation = () => {
  if (locationEnabled.value) {
    locationEnabled.value = false
    localStorage.setItem('ev_location_enabled', 'false')
    clearLocation()
  } else {
    locationEnabled.value = true
    localStorage.setItem('ev_location_enabled', 'true')
    requestCurrentLocation()
  }
}

// Helper to format current datetime for datetime-local input
const getCurrentDateTimeLocal = () => {
  const now = new Date()
  const year = now.getFullYear()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  const hours = String(now.getHours()).padStart(2, '0')
  const minutes = String(now.getMinutes()).padStart(2, '0')
  return `${year}-${month}-${day}T${hours}:${minutes}`
}

const logs = ref<any[]>([])
const error = ref<string | null>(null)

// Get last odometer reading for validation
const getLastOdometerReading = (): number | null => {
  if (logs.value.length === 0) return null

  const logsWithOdometer = logs.value
    .filter(log => log.odometerKm !== null && log.odometerKm !== undefined)
    .sort((a, b) => new Date(b.loggedAt).getTime() - new Date(a.loggedAt).getTime())

  return logsWithOdometer.length > 0 ? logsWithOdometer[0].odometerKm : null
}

const getLastOdometerPlaceholder = (): string => {
  if (logs.value.length === 0) return 'Tachostand (km)'

  const logsWithOdometer = logs.value
    .filter(log => log.odometerKm !== null && log.odometerKm !== undefined)
    .sort((a, b) => new Date(b.loggedAt).getTime() - new Date(a.loggedAt).getTime())

  if (logsWithOdometer.length === 0) return 'Tachostand (km)'

  const last = logsWithOdometer[0]
  const date = new Date(last.loggedAt).toLocaleDateString('de-DE')
  return `zuletzt: ${last.odometerKm.toLocaleString('de-DE')} km vom ${date}`
}

// Request current location via Geolocation API
const requestCurrentLocation = () => {
  if (!navigator.geolocation) {
    locationStatus.value = 'error'
    locationErrorMessage.value = 'Geolokalisierung wird von deinem Browser nicht unterstützt'
    return
  }

  locationStatus.value = 'loading'
  locationErrorMessage.value = null

  navigator.geolocation.getCurrentPosition(
    (position) => {
      latitude.value = position.coords.latitude
      longitude.value = position.coords.longitude
      locationStatus.value = 'success'
    },
    (err) => {
      console.error('Geolocation error:', err)
      locationStatus.value = 'error'
      locationErrorMessage.value = 'Standortzugriff verweigert. Du kannst unten manuell nach einem Standort suchen.'
    }
  )
}

// Search location via OpenStreetMap Nominatim
const searchLocation = async (query: string) => {
  if (!query || query.length < 3) {
    locationSuggestions.value = []
    return
  }

  try {
    const response = await fetch(
      `https://nominatim.openstreetmap.org/search?q=${encodeURIComponent(query)}&format=json&limit=5`
    )
    const data = await response.json()
    locationSuggestions.value = data
    showLocationSuggestions.value = true
  } catch (err) {
    console.error('Failed to search location:', err)
  }
}

// Clear location
const clearLocation = () => {
  latitude.value = null
  longitude.value = null
  locationStatus.value = 'idle'
  locationSearchQuery.value = ''
  locationSuggestions.value = []
  showLocationSuggestions.value = false
  locationErrorMessage.value = null
}

// Watch search query for debounced search
let searchTimeout: any = null
watch(locationSearchQuery, (newQuery) => {
  clearTimeout(searchTimeout)
  searchTimeout = setTimeout(() => {
    searchLocation(newQuery)
  }, 300)
})

const fetchLogs = async () => {
  if (!selectedCarId.value) {
    logs.value = []
    return
  }

  try {
    const res = await api.get(`/logs?carId=${selectedCarId.value}&limit=5`)
    logs.value = res.data.sort((a: any, b: any) =>
      new Date(b.loggedAt).getTime() - new Date(a.loggedAt).getTime()
    )
  } catch (err) {
    console.error('Failed to fetch logs:', err)
  }
}

const editingLog = ref<any | null>(null)

const deleteLog = async (logId: string) => {
  if (!confirm('Ladevorgang wirklich löschen?')) return

  try {
    await api.delete(`/logs/${logId}`)
    await fetchLogs()
  } catch (err) {
    console.error('Failed to delete log:', err)
    error.value = 'Löschen fehlgeschlagen. Bitte versuche es erneut.'
  }
}

const submitLog = async () => {
  if (!selectedCarId.value) {
    error.value = 'Bitte wähle ein Fahrzeug aus'
    return
  }

// Odometer validation: Show warning if lower than last reading
  odometerWarning.value = null
  if (odometerKm.value !== null) {
    const lastOdometer = getLastOdometerReading()
    if (lastOdometer !== null && odometerKm.value < lastOdometer) {
      odometerWarning.value = `⚠️ Hinweis: Dein Tachostand (${odometerKm.value} km) ist niedriger als der letzte erfasste Wert (${lastOdometer} km). Bist du sicher?`
      // Don't block submission, just show warning
    }
  }

  try {
    error.value = null
    const payload: any = {
      carId: selectedCarId.value,
      kwhCharged: Math.round(kwhCharged.value * 100) / 100,
      costEur: Math.round(costEur.value * 100) / 100,
      odometerKm: odometerKm.value,
      socAfterChargePercent: socAfterChargePercent.value
    }

    // Add charging duration if provided (optional)
    if (chargeDurationMinutes.value !== null && chargeDurationMinutes.value > 0) {
      payload.chargeDurationMinutes = chargeDurationMinutes.value
    }

    // Add location if provided (lat/lon will be converted to geohash on backend)
    if (latitude.value !== null && longitude.value !== null) {
      payload.latitude = latitude.value
      payload.longitude = longitude.value
    }

    // Add max charging power if provided
    if (maxChargingPowerKw.value !== null) {
      payload.maxChargingPowerKw = Math.round(maxChargingPowerKw.value * 100) / 100
    }

    // Add loggedAt if provided — send as-is (no timezone conversion, backend stores LocalDateTime)
    if (loggedAt.value) {
      payload.loggedAt = loggedAt.value + ':00'
    }

    // Add ocrUsed flag if OCR was used to fill in data
    if (ocrUsed.value) {
      payload.ocrUsed = true
    }

    // Add chargingType if selected
    if (chargingType.value) {
      payload.chargingType = chargingType.value
    }

    const isFirstLog = logs.value.length === 0
    const res = await api.post('/logs', payload)

    // Show coin toast and refresh balance
    showCoinToast(res.data.coinsAwarded)
    coinStore.refresh()
    analytics.trackLogCreated(ocrUsed.value ? 'ocr' : 'manual', isFirstLog)

    // Reset form (keep car selected)
    kwhCharged.value = 0
    costEur.value = 0
    chargeDurationMinutes.value = null
    odometerKm.value = null
    socAfterChargePercent.value = null
    maxChargingPowerKw.value = null
    loggedAt.value = null
    odometerWarning.value = null
    ocrUsed.value = false
    chargingType.value = 'AC'
    locationEnabled.value = false
    clearLocation()

    await fetchLogs()

    // Emit success event (for modal close)
    emit('success')
  } catch (err: any) {
    error.value = err.response?.data?.message || 'Ladevorgang konnte nicht gespeichert werden'
    console.error('Failed to submit log:', err)
  }
}

// Watch for car selection changes and refetch logs
watch(selectedCarId, () => {
  fetchLogs()
})

const hasCars = ref<boolean | null>(null) // null = loading
const carCount = ref(0)

onMounted(async () => {
  try {
    const cars = await carService.getCars()
    hasCars.value = cars.length > 0
    carCount.value = cars.length
    if (cars.length === 1) {
      selectedCarId.value = cars[0].id
    }
  } catch {
    hasCars.value = false
  }
  if (locationEnabled.value) {
    requestCurrentLocation()
  }
  fetchLogs()
})

// Handle OCR data extraction
const handleOcrData = (ocrResult: any) => {
  if (ocrResult.kwh !== null) {
    kwhCharged.value = ocrResult.kwh
  }
  if (ocrResult.cost !== null) {
    costEur.value = ocrResult.cost
  }
  if (ocrResult.durationMinutes !== null) {
    chargeDurationMinutes.value = ocrResult.durationMinutes
  }

  // Mark that OCR was used to fill in data (awards +2 bonus coins)
  ocrUsed.value = true

  // Switch to manual mode so user can see & edit the extracted data
  showOcrCapture.value = false
}
</script>

<template>
  <div class="md:max-w-2xl md:mx-auto p-4 md:p-6 bg-white md:rounded-xl md:shadow-lg md:mt-8">
    <h1 class="text-3xl font-bold text-gray-800 mb-6 text-center">Ladevorgang erfassen</h1>

    <!-- No cars yet: prompt to add one first -->
    <div v-if="hasCars === false" class="text-center py-10 space-y-4">
      <TruckIcon class="h-14 w-14 mx-auto text-gray-300" />
      <p class="text-gray-600 font-medium">Noch kein Fahrzeug hinterlegt</p>
      <p class="text-sm text-gray-400">Lege zuerst ein Fahrzeug an, bevor du einen Ladevorgang erfasst.</p>
      <router-link
        to="/cars"
        class="inline-flex items-center gap-2 bg-indigo-600 text-white px-5 py-2.5 rounded-lg text-sm font-medium hover:bg-indigo-700 transition"
      >
        <TruckIcon class="h-4 w-4" />
        Fahrzeug anlegen
      </router-link>
    </div>

    <template v-else-if="hasCars === true">
    <!-- Car Selector: only shown when user has multiple cars -->
    <div v-if="carCount > 1" class="mb-6">
      <CarSelector v-model="selectedCarId" />
    </div>

    <!-- Mode Toggle: Photo OCR vs Manual Entry -->
    <div class="flex justify-center mb-4">
    <div class="inline-flex rounded-full border border-gray-200 bg-gray-100 p-0.5">
      <button
        type="button"
        @click="showOcrCapture = true"
        :class="[
          'flex items-center gap-1.5 px-3 py-1.5 rounded-full text-sm font-medium transition',
          showOcrCapture ? 'bg-white text-indigo-700 shadow-sm' : 'text-gray-500 hover:text-gray-700'
        ]">
        <CameraIcon class="h-4 w-4" />
        Foto
      </button>
      <button
        type="button"
        @click="showOcrCapture = false"
        :class="[
          'flex items-center gap-1.5 px-3 py-1.5 rounded-full text-sm font-medium transition',
          !showOcrCapture ? 'bg-white text-indigo-700 shadow-sm' : 'text-gray-500 hover:text-gray-700'
        ]">
        <PencilSquareIcon class="h-4 w-4" />
        Manuell
      </button>
    </div>
    </div>


    <!-- OCR Photo Capture Mode -->
    <OcrPhotoCapture
      v-if="showOcrCapture"
      @dataExtracted="handleOcrData"
      @cancel="showOcrCapture = false"
    />

    <!-- Manual Entry Mode -->
    <div v-if="!showOcrCapture">
      <div v-if="error" class="mb-4 p-4 bg-red-50 border border-red-200 text-red-700 rounded-md">
        {{ error }}
      </div>

      <div v-if="odometerWarning" class="mb-4 p-4 bg-yellow-50 border border-yellow-200 text-yellow-800 rounded-md">
        {{ odometerWarning }}
      </div>

      <form @submit.prevent="submitLog" class="space-y-4">

      <!-- Pflichtfelder-Gruppe -->
      <div class="bg-gray-100 md:rounded-xl p-3 space-y-3 -mx-4 md:mx-0">

      <!-- Row 1: kWh + Kosten -->
      <div class="grid grid-cols-2 gap-3">
        <div>
          <label class="block text-sm font-medium text-gray-700">Energie (kWh)</label>
          <input v-model="kwhCharged" type="number" step="0.1" required class="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm p-2 border bg-white" />
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700">Kosten (€)</label>
          <input v-model="costEur" type="number" step="0.01" required class="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm p-2 border bg-white" />
        </div>
      </div>

      <!-- Row 2: Tachostand + Batteriestand -->
      <div class="grid grid-cols-2 gap-3">
        <div>
          <label class="block text-sm font-medium text-gray-700">Tachostand (km)</label>
          <input v-model="odometerKm" type="number" step="1" required :placeholder="getLastOdometerPlaceholder()" class="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm p-2 border bg-white" />
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700">Akku nach Laden (%)</label>
          <input v-model="socAfterChargePercent" type="number" min="0" max="100" step="1" required class="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm p-2 border bg-white" />
        </div>
      </div>

      <!-- Row 3: Toggles nebeneinander, Standort zuerst -->
      <div class="flex items-center justify-center gap-8">
        <!-- Location toggle with globe indicator -->
        <div class="flex items-center gap-1.5">
          <GlobeAltIcon
            :class="[
              'h-5 w-5 transition-colors duration-300',
              locationStatus === 'loading' ? 'text-gray-400 animate-pulse' :
              locationStatus === 'success' || locationStatus === 'manual' ? 'text-green-500' :
              locationStatus === 'error' ? 'text-red-500' :
              'text-gray-300'
            ]"
          />
          <button
            type="button"
            @click="toggleLocation"
            :class="[
              'relative inline-flex h-8 w-14 flex-shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none',
              locationEnabled ? 'bg-green-500' : 'bg-gray-300'
            ]">
            <span
              :class="[
                'pointer-events-none inline-flex h-7 w-7 transform items-center justify-center rounded-full bg-white shadow text-sm transition duration-200 ease-in-out',
                locationEnabled ? 'translate-x-6' : 'translate-x-0'
              ]">
              📍
            </span>
          </button>
        </div>

        <!-- AC/DC toggle -->
        <button
          type="button"
          @click="chargingType = chargingType === 'AC' ? 'DC' : 'AC'"
          :class="[
            'relative inline-flex h-8 w-16 flex-shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none',
            chargingType === 'DC' ? 'bg-orange-500' : 'bg-blue-500'
          ]">
          <span
            :class="[
              'pointer-events-none inline-flex h-7 w-9 transform items-center justify-center rounded-full bg-white shadow text-xs font-bold transition duration-200 ease-in-out',
              chargingType === 'DC' ? 'translate-x-6' : 'translate-x-0'
            ]"
            :style="{ color: chargingType === 'DC' ? '#f97316' : '#3b82f6' }">
            {{ chargingType }}
          </span>
        </button>
      </div>
      </div><!-- end Pflichtfelder-Gruppe -->

      <!-- Optionale Felder -->
      <div>
        <p class="text-xs text-gray-400 text-center mb-2">Optional</p>
        <div class="space-y-3">
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-sm font-medium text-gray-600">Dauer (min)</label>
              <input v-model="chargeDurationMinutes" type="number" class="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm p-2 border" />
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-600">Max. Ladeleistung (kW)</label>
              <input v-model="maxChargingPowerKw" type="number" step="0.1" class="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm p-2 border" />
            </div>
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-600">Ladezeitpunkt</label>
            <input
              v-model="loggedAt"
              type="datetime-local"
              :max="getCurrentDateTimeLocal()"
              :placeholder="getCurrentDateTimeLocal()"
              class="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm p-2 border" />
            <p class="text-xs text-gray-400 mt-1">Leer lassen für aktuelle Zeit</p>
          </div>
        </div>
      </div>

        <button type="submit" class="w-full bg-indigo-600 text-white p-3 rounded-md shadow hover:bg-indigo-700 transition">⚡ Ladevorgang speichern</button>
        <p class="text-xs text-gray-400 text-center mt-2">
          📍 Der Standort hilft uns, die Außentemperatur beim Laden zu ermitteln — anonymisiert auf ~5km.
        </p>
        <ConsumptionInfoBox :min-trips="5" class="mt-4" />
      </form>

      <!-- Watt Toast -->
      <div v-if="showToast" class="fixed bottom-6 right-6 z-50 animate-slide-in">
        <div class="bg-green-600 text-white px-5 py-3 rounded-lg shadow-2xl flex items-center gap-2">
          <BoltIcon class="h-5 w-5 flex-shrink-0" />
          <span class="font-medium text-sm">{{ toastMessage }}</span>
        </div>
      </div>

      <div class="mt-10">
      <h2 class="text-xl font-semibold mb-4 text-gray-800">Letzte 5 Ladevorgänge</h2>

      <div v-if="!selectedCarId" class="text-gray-500 text-center">Bitte wähle ein Fahrzeug aus um Ladevorgänge anzuzeigen.</div>
      <div v-else-if="logs.length === 0" class="text-gray-500 text-center">Noch keine Ladevorgänge für dieses Fahrzeug erfasst.</div>
      <ul v-else class="space-y-3">
        <li v-for="log in logs" :key="log.id" class="p-3 bg-gray-50 border border-gray-200 rounded-lg shadow-sm hover:shadow transition space-y-2">
          <!-- Header row: kWh + date | temp + price + delete -->
          <div class="flex items-center justify-between gap-2">
            <div class="flex items-center gap-2 min-w-0">
              <BoltIcon class="w-4 h-4 text-indigo-600 flex-shrink-0" />
              <span class="font-semibold text-indigo-700 whitespace-nowrap">{{ log.kwhCharged }} kWh</span>
              <span class="text-xs text-gray-400 whitespace-nowrap">{{ new Date(log.loggedAt).toLocaleDateString('de-DE') }}</span>
            </div>
            <div class="flex items-center gap-1.5 flex-shrink-0">
              <span v-if="log.temperatureCelsius != null"
                :class="['inline-flex items-center gap-0.5 px-2 py-0.5 border rounded-full text-xs whitespace-nowrap', tempBadgeClass(log.temperatureCelsius)]">
                <SunIcon class="w-3 h-3" />{{ log.temperatureCelsius.toFixed(1) }}°C
              </span>
              <span class="hidden min-[475px]:inline-block px-2 py-0.5 bg-indigo-50 border border-indigo-200 text-xs rounded-full text-indigo-700 font-medium whitespace-nowrap">
                €{{ (log.costEur / log.kwhCharged).toFixed(2) }}/kWh
              </span>
              <button
                type="button"
                @click="editingLog = log"
                class="p-1 text-gray-400 hover:text-blue-500 hover:bg-blue-50 rounded transition"
                title="Ladevorgang bearbeiten">
                <PencilSquareIcon class="w-4 h-4" />
              </button>
              <button
                type="button"
                @click="deleteLog(log.id)"
                class="p-1 text-gray-400 hover:text-red-500 hover:bg-red-50 rounded transition"
                title="Ladevorgang löschen">
                <TrashIcon class="w-4 h-4" />
              </button>
            </div>
          </div>
          <!-- Badge row: metadata as pills -->
          <div class="flex flex-wrap gap-1.5">
            <span class="inline-flex items-center gap-1 px-2 py-0.5 bg-white border border-gray-200 rounded-full text-xs text-gray-600 whitespace-nowrap">
              €{{ log.costEur }}
            </span>
            <span class="inline-flex min-[475px]:hidden items-center gap-1 px-2 py-0.5 bg-indigo-50 border border-indigo-200 rounded-full text-xs text-indigo-700 font-medium whitespace-nowrap">
              €{{ (log.costEur / log.kwhCharged).toFixed(2) }}/kWh
            </span>
            <span v-if="log.chargeDurationMinutes" class="hidden min-[475px]:inline-flex items-center gap-1 px-2 py-0.5 bg-white border border-gray-200 rounded-full text-xs text-gray-600 whitespace-nowrap">
              <ClockIcon class="w-3 h-3" />{{ log.chargeDurationMinutes }}min
            </span>
            <span v-if="log.odometerKm" class="hidden min-[475px]:inline-flex items-center gap-1 px-2 py-0.5 bg-white border border-gray-200 rounded-full text-xs text-gray-600 whitespace-nowrap">
              <TruckIcon class="w-3 h-3" />{{ log.odometerKm.toLocaleString('de-DE') }} km
            </span>
            <span v-if="log.socAfterChargePercent !== null" class="inline-flex items-center gap-1 px-2 py-0.5 bg-white border border-gray-200 rounded-full text-xs text-gray-600 whitespace-nowrap">
              <Battery0Icon class="w-3 h-3" />{{ log.socAfterChargePercent }}%
            </span>
            <span v-if="log.maxChargingPowerKw" class="inline-flex items-center gap-1 px-2 py-0.5 bg-white border border-gray-200 rounded-full text-xs text-gray-600 whitespace-nowrap">
              <BoltIcon class="w-3 h-3" />{{ log.maxChargingPowerKw }} kW
            </span>
            <span
              v-if="log.chargingType && log.chargingType !== 'UNKNOWN'"
              :class="[
                'inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium whitespace-nowrap',
                log.chargingType === 'DC'
                  ? 'bg-orange-50 border border-orange-200 text-orange-700'
                  : 'bg-blue-50 border border-blue-200 text-blue-700'
              ]">
              {{ log.chargingType }}
            </span>
          </div>
        </li>
      </ul>
      </div>
    </div>
    </template> <!-- end v-else-if="hasCars" -->
  </div>

  <EditLogModal
    v-if="editingLog"
    :log="editingLog"
    @close="editingLog = null"
    @saved="() => { editingLog = null; fetchLogs() }"
  />
</template>

<style scoped>
@keyframes slide-in {
  from { transform: translateX(100%); opacity: 0; }
  to { transform: translateX(0); opacity: 1; }
}
.animate-slide-in { animation: slide-in 0.3s ease-out; }
</style>
