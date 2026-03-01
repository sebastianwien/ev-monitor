<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import api from '../api/axios'
import CarSelector from './CarSelector.vue'

const emit = defineEmits<{
  success: []
}>()

const selectedCarId = ref<string | null>(null)
const kwhCharged = ref<number>(0)
const costEur = ref<number>(0)
const chargeDurationMinutes = ref<number>(0)
const odometerKm = ref<number | null>(null) // Optional: odometer reading
const maxChargingPowerKw = ref<number | null>(null) // Optional: max charging power
const loggedAt = ref<string | null>(null) // Optional: when the charge happened
const odometerWarning = ref<string | null>(null) // Warning if odometer is lower than last log

// Location tracking
const latitude = ref<number | null>(null)
const longitude = ref<number | null>(null)
const locationStatus = ref<'idle' | 'loading' | 'success' | 'error' | 'manual'>('idle')
const locationSearchQuery = ref('')
const locationSuggestions = ref<any[]>([])
const showLocationSuggestions = ref(false)
const locationErrorMessage = ref<string | null>(null)

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

  // Filter logs with odometer data and sort by loggedAt descending
  const logsWithOdometer = logs.value
    .filter(log => log.odometerKm !== null && log.odometerKm !== undefined)
    .sort((a, b) => new Date(b.loggedAt).getTime() - new Date(a.loggedAt).getTime())

  return logsWithOdometer.length > 0 ? logsWithOdometer[0].odometerKm : null
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

// Select a location from suggestions
const selectLocation = (suggestion: any) => {
  latitude.value = parseFloat(suggestion.lat)
  longitude.value = parseFloat(suggestion.lon)
  locationSearchQuery.value = suggestion.display_name
  locationStatus.value = 'manual'
  showLocationSuggestions.value = false
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
    const res = await api.get(`/logs?carId=${selectedCarId.value}`)
    logs.value = res.data
  } catch (err) {
    console.error('Failed to fetch logs:', err)
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
      chargeDurationMinutes: chargeDurationMinutes.value
    }

    // Add location if provided (lat/lon will be converted to geohash on backend)
    if (latitude.value !== null && longitude.value !== null) {
      payload.latitude = latitude.value
      payload.longitude = longitude.value
    }

    // Add odometer if provided
    if (odometerKm.value !== null) {
      payload.odometerKm = odometerKm.value
    }

    // Add max charging power if provided
    if (maxChargingPowerKw.value !== null) {
      payload.maxChargingPowerKw = Math.round(maxChargingPowerKw.value * 100) / 100
    }

    // Add loggedAt if provided (convert from datetime-local format to ISO string)
    if (loggedAt.value) {
      payload.loggedAt = new Date(loggedAt.value).toISOString()
    }

    await api.post('/logs', payload)

    // Reset form (keep car selected)
    kwhCharged.value = 0
    costEur.value = 0
    chargeDurationMinutes.value = 0
    odometerKm.value = null
    maxChargingPowerKw.value = null
    loggedAt.value = null
    odometerWarning.value = null
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

onMounted(() => {
  fetchLogs()
})
</script>

<template>
  <div class="max-w-2xl mx-auto p-6 bg-white rounded-xl shadow-lg mt-8">
    <h1 class="text-3xl font-bold text-gray-800 mb-6 text-center">Ladevorgang erfassen</h1>

    <div v-if="error" class="mb-4 p-4 bg-red-50 border border-red-200 text-red-700 rounded-md">
      {{ error }}
    </div>

    <div v-if="odometerWarning" class="mb-4 p-4 bg-yellow-50 border border-yellow-200 text-yellow-800 rounded-md">
      {{ odometerWarning }}
    </div>

    <form @submit.prevent="submitLog" class="space-y-4">
      <CarSelector v-model="selectedCarId" />

      <div>
        <label class="block text-sm font-medium text-gray-700">Geladene Energie (kWh)</label>
        <input v-model="kwhCharged" type="number" step="0.1" required class="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm p-2 border" />
      </div>

      <div>
        <label class="block text-sm font-medium text-gray-700">Kosten (€)</label>
        <input v-model="costEur" type="number" step="0.01" required class="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm p-2 border" />
      </div>

      <div>
        <label class="block text-sm font-medium text-gray-700">Ladedauer (Minuten)</label>
        <input v-model="chargeDurationMinutes" type="number" required class="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm p-2 border" />
      </div>

      <div>
        <label class="block text-sm font-medium text-gray-700">Tachostand (km, optional)</label>
        <input v-model="odometerKm" type="number" step="1" class="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm p-2 border" />
        <p class="text-xs text-gray-500 mt-1">Hilft dir Verbrauch pro km zu tracken</p>
      </div>

      <div>
        <label class="block text-sm font-medium text-gray-700">Max. Ladeleistung (kW, optional)</label>
        <input v-model="maxChargingPowerKw" type="number" step="0.1" class="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm p-2 border" />
        <p class="text-xs text-gray-500 mt-1">Höchste erreichte Ladeleistung während des Ladevorgangs</p>
      </div>

      <div>
        <label class="block text-sm font-medium text-gray-700">Ladedatum & -zeit (optional)</label>
        <input
          v-model="loggedAt"
          type="datetime-local"
          :max="getCurrentDateTimeLocal()"
          :placeholder="getCurrentDateTimeLocal()"
          class="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm p-2 border" />
        <p class="text-xs text-gray-500 mt-1">Leer lassen für aktuelle Zeit</p>
      </div>

      <!-- Location Section -->
      <div class="border-t pt-4">
        <label class="block text-sm font-medium text-gray-700 mb-2">Standort (optional)</label>

        <!-- Idle State: Show "Use Current Location" button -->
        <div v-if="locationStatus === 'idle'" class="space-y-2">
          <button
            type="button"
            @click="requestCurrentLocation"
            class="w-full bg-green-100 text-green-700 p-3 rounded-md shadow hover:bg-green-200 transition font-medium">
            📍 Aktuellen Standort verwenden
          </button>
          <p class="text-xs text-gray-500 text-center">Oder manuell suchen</p>
          <input
            v-model="locationSearchQuery"
            @focus="locationStatus = 'error'"
            type="text"
            placeholder="Standort suchen (z.B. Berlin)"
            class="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm p-2 border" />
        </div>

        <!-- Loading State -->
        <div v-if="locationStatus === 'loading'" class="text-center py-4 text-gray-600">
          <div class="inline-block animate-spin rounded-full h-6 w-6 border-b-2 border-indigo-600"></div>
          <p class="mt-2 text-sm">Ermittle deinen Standort...</p>
        </div>

        <!-- Success State: Location acquired via GPS -->
        <div v-if="locationStatus === 'success'" class="space-y-2">
          <div class="p-3 bg-green-50 border border-green-200 rounded-md">
            <p class="text-sm text-green-800 font-medium">✅ Standort erfasst</p>
            <p class="text-xs text-green-600 mt-1">
              🔒 Wir anonymisieren deinen Standort auf einen Umkreis von 5km, bevor er unsere Datenbank berührt. Dein Schlafzimmer bleibt dein Geheimnis!
            </p>
          </div>
          <button
            type="button"
            @click="clearLocation"
            class="w-full text-sm text-indigo-600 hover:text-indigo-700 underline">
            Standort löschen
          </button>
        </div>

        <!-- Error State / Manual Search -->
        <div v-if="locationStatus === 'error'" class="space-y-2">
          <div v-if="locationErrorMessage" class="p-3 bg-yellow-50 border border-yellow-200 rounded-md">
            <p class="text-sm text-yellow-800">{{ locationErrorMessage }}</p>
          </div>
          <div class="relative">
            <input
              v-model="locationSearchQuery"
              type="text"
              placeholder="Standort suchen (z.B. Berlin, München)"
              class="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm p-2 border" />

            <!-- Suggestions Dropdown -->
            <div
              v-if="showLocationSuggestions && locationSuggestions.length > 0"
              class="absolute z-10 w-full mt-1 bg-white border border-gray-300 rounded-md shadow-lg max-h-48 overflow-y-auto">
              <button
                v-for="suggestion in locationSuggestions"
                :key="suggestion.place_id"
                type="button"
                @click="selectLocation(suggestion)"
                class="w-full text-left px-3 py-2 hover:bg-indigo-50 border-b last:border-b-0 text-sm">
                {{ suggestion.display_name }}
              </button>
            </div>
          </div>
          <p class="text-xs text-gray-500">
            🔒 Wir anonymisieren deinen Standort auf einen Umkreis von 5km, bevor er unsere Datenbank berührt.
          </p>
        </div>

        <!-- Manual State: Location selected from search -->
        <div v-if="locationStatus === 'manual'" class="space-y-2">
          <div class="p-3 bg-blue-50 border border-blue-200 rounded-md">
            <p class="text-sm text-blue-800 font-medium">📍 Standort: {{ locationSearchQuery }}</p>
            <p class="text-xs text-blue-600 mt-1">
              🔒 Wir anonymisieren deinen Standort auf einen Umkreis von 5km, bevor er unsere Datenbank berührt.
            </p>
          </div>
          <button
            type="button"
            @click="clearLocation"
            class="w-full text-sm text-indigo-600 hover:text-indigo-700 underline">
            Standort löschen
          </button>
        </div>
      </div>

      <button type="submit" class="w-full bg-indigo-600 text-white p-3 rounded-md shadow hover:bg-indigo-700 transition">⚡ Ladevorgang speichern</button>
    </form>

    <div class="mt-10">
      <h2 class="text-xl font-semibold mb-4 text-gray-800">Letzte Ladevorgänge</h2>

      <div v-if="!selectedCarId" class="text-gray-500 text-center">Bitte wähle ein Fahrzeug aus um Ladevorgänge anzuzeigen.</div>
      <div v-else-if="logs.length === 0" class="text-gray-500 text-center">Noch keine Ladevorgänge für dieses Fahrzeug erfasst.</div>
      <ul v-else class="space-y-3">
        <li v-for="log in logs" :key="log.id" class="p-4 bg-gray-50 border border-gray-200 rounded-lg flex justify-between items-center shadow-sm hover:shadow transition">
          <div class="space-y-1">
            <span class="block font-medium text-indigo-700">⚡ {{ log.kwhCharged }} kWh</span>
            <span class="block text-sm text-gray-500">€{{ log.costEur }} • {{ log.chargeDurationMinutes }}min</span>
          </div>
          <span class="px-3 py-1 bg-white border border-gray-300 text-xs rounded-full shadow-sm text-gray-600 font-medium">
            €{{ (log.costEur / log.kwhCharged).toFixed(2) }}/kWh
          </span>
        </li>
      </ul>
    </div>
  </div>
</template>
