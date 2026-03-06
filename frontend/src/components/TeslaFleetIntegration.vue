<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowTopRightOnSquareIcon, ArrowPathIcon, XMarkIcon, CheckCircleIcon, ExclamationTriangleIcon } from '@heroicons/vue/24/outline'
import teslaFleetService, { type TeslaConnectionStatus, type TeslaFleetSyncResult } from '@/api/teslaFleetService'
import { carService, type Car } from '@/api/carService'

const route = useRoute()

const status = ref<TeslaConnectionStatus>({ connected: false, vehicleName: null, lastSyncAt: null, autoImportEnabled: false, geocodingInProgress: false })
const isLoading = ref(false)
const syncResult = ref<TeslaFleetSyncResult | null>(null)
const error = ref<string | null>(null)
const success = ref<string | null>(null)
const fleetApiConfigured = ref(true)
const cars = ref<Car[]>([])
const selectedCarId = ref<string>('')
let geocodingPollInterval: ReturnType<typeof setInterval> | null = null

onMounted(async () => {
  await Promise.all([loadStatus(), loadCars()])
  if (status.value.geocodingInProgress) {
    startGeocodingPoll()
  }
  if (route.query['tesla-connected']) {
    success.value = 'Tesla erfolgreich verbunden! Du kannst jetzt deine Ladehistorie importieren.'
    await loadStatus()
  }
  if (route.query['tesla-error']) {
    error.value = String(route.query['tesla-error'])
  }
})

async function loadStatus() {
  try { status.value = await teslaFleetService.getStatus() } catch { /* ignore */ }
}

function startGeocodingPoll() {
  if (geocodingPollInterval) return
  geocodingPollInterval = setInterval(async () => {
    await loadStatus()
    if (!status.value.geocodingInProgress) {
      stopGeocodingPoll()
    }
  }, 3000)
}

function stopGeocodingPoll() {
  if (geocodingPollInterval) {
    clearInterval(geocodingPollInterval)
    geocodingPollInterval = null
  }
}

onUnmounted(() => stopGeocodingPoll())

async function loadCars() {
  try {
    cars.value = await carService.getCars()
    if (cars.value.length > 0) selectedCarId.value = cars.value[0].id
  } catch { /* ignore */ }
}

async function handleConnect() {
  if (!selectedCarId.value) { error.value = 'Bitte zuerst ein Fahrzeug auswählen.'; return }
  isLoading.value = true
  error.value = null
  try {
    const authStart = await teslaFleetService.getAuthStartUrl(selectedCarId.value)
    if (!authStart.fleetApiConfigured) {
      error.value = 'Tesla Fleet API ist noch nicht konfiguriert. Bitte wende dich an den Support.'
      fleetApiConfigured.value = false
      return
    }
    if (authStart.authUrl) window.location.href = authStart.authUrl
  } catch (e: any) {
    error.value = e.response?.data?.message || 'Verbindung fehlgeschlagen'
  } finally {
    isLoading.value = false
  }
}

async function handleSyncHistory() {
  isLoading.value = true
  error.value = null
  syncResult.value = null
  try {
    syncResult.value = await teslaFleetService.syncHistory()
    await loadStatus()
    if (status.value.geocodingInProgress) {
      startGeocodingPoll()
    }
  } catch (e: any) {
    error.value = e.response?.data?.message || 'Synchronisierung fehlgeschlagen'
  } finally {
    isLoading.value = false
  }
}

async function handleDisconnect() {
  if (!confirm('Tesla-Verbindung wirklich trennen?')) return
  try {
    await teslaFleetService.disconnect()
    status.value = { connected: false, vehicleName: null, lastSyncAt: null, autoImportEnabled: false, geocodingInProgress: false }
    syncResult.value = null; success.value = null
  } catch { error.value = 'Trennen fehlgeschlagen' }
}

function enumToLabel(value: string): string {
  return value.replace(/_/g, ' ').toLowerCase()
    .split(' ')
    .map(word => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ')
}

function carLabel(car: Car): string {
  const name = `${enumToLabel(car.brand)} ${enumToLabel(car.model)}`
  return car.licensePlate ? `${name} · ${car.licensePlate}` : name
}

function formatDate(d: string) {
  return new Date(d).toLocaleString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' })
}
</script>

<template>
  <div class="bg-white border border-gray-200 rounded-xl p-6 space-y-4">
    <div class="flex items-center gap-3">
      <div class="bg-gray-900 rounded-lg p-2">
        <svg class="h-5 w-5 text-white" viewBox="0 0 24 24" fill="currentColor">
          <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 14H9V8h2v8zm4 0h-2V8h2v8z"/>
        </svg>
      </div>
      <div>
        <h3 class="font-semibold text-gray-900">Tesla Import</h3>
        <p class="text-xs text-gray-500">Via offizieller Tesla Fleet API</p>
      </div>
    </div>

    <div v-if="success" class="flex items-start gap-2 bg-green-50 border border-green-200 rounded-lg p-3">
      <CheckCircleIcon class="h-4 w-4 text-green-600 mt-0.5 shrink-0" />
      <p class="text-sm text-green-800">{{ success }}</p>
    </div>
    <div v-if="error" class="flex items-start gap-2 bg-red-50 border border-red-200 rounded-lg p-3">
      <ExclamationTriangleIcon class="h-4 w-4 text-red-500 mt-0.5 shrink-0" />
      <p class="text-sm text-red-800">{{ error }}</p>
    </div>

    <div class="bg-blue-50 border border-blue-200 rounded-lg p-3 text-sm text-blue-800 space-y-1">
      <p class="font-medium">Was importiert wird:</p>
      <ul class="list-disc list-inside space-y-0.5 text-blue-700">
        <li>Supercharger-Sessions (historisch + zukünftig)</li>
        <li>Energie in kWh, Dauer, Standort</li>
        <li>Kosten (falls Supercharger mit Abrechnung)</li>
        <li>Kilometerstand (ab jetzt, für zukünftige Sessions)</li>
      </ul>
      <p class="text-xs text-blue-600 mt-2">Bisherige Sessions enthalten keinen km-Stand — die Tesla API liefert diesen nur bei aktiven Ladevorgängen.</p>
    </div>

    <template v-if="!status.connected">
      <p class="text-sm text-gray-600">
        Verbinde deinen Tesla Account über das offizielle OAuth2-Verfahren.
        Wir importieren dann deine bisherigen Supercharger-Sessions und prüfen täglich auf neue.
      </p>
      <div v-if="cars.length > 0">
        <label class="block text-xs font-medium text-gray-600 mb-1">Welches Fahrzeug ist dein Tesla?</label>
        <select v-model="selectedCarId" class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-indigo-500 focus:border-transparent">
          <option v-for="car in cars" :key="car.id" :value="car.id">
            {{ carLabel(car) }}
          </option>
        </select>
      </div>
      <p v-else class="text-xs text-amber-700 bg-amber-50 border border-amber-200 rounded-lg p-2">
        Bitte zuerst ein Fahrzeug unter Fahrzeuge anlegen.
      </p>
      <button
        @click="handleConnect"
        :disabled="isLoading || !fleetApiConfigured || cars.length === 0"
        class="w-full flex items-center justify-center gap-2 bg-gray-900 text-white px-4 py-2.5 rounded-lg text-sm font-medium hover:bg-gray-800 transition disabled:opacity-50"
      >
        <ArrowTopRightOnSquareIcon class="h-4 w-4" />
        {{ isLoading ? 'Weiterleitung...' : 'Mit Tesla verbinden' }}
      </button>
    </template>

    <template v-else>
      <div class="bg-green-50 border border-green-200 rounded-lg p-3">
        <div class="flex items-center justify-between">
          <div>
            <p class="text-sm font-semibold text-green-800">Verbunden: {{ status.vehicleName || 'Tesla' }}</p>
            <p v-if="status.lastSyncAt" class="text-xs text-gray-500 mt-0.5">Letzter Sync: {{ formatDate(status.lastSyncAt) }}</p>
          </div>
          <button @click="handleDisconnect" class="text-xs text-red-500 hover:text-red-700">
            <XMarkIcon class="h-4 w-4" />
          </button>
        </div>
      </div>
      <div v-if="syncResult" class="bg-gray-50 rounded-lg p-3 text-sm">
        <p class="font-medium text-gray-800">{{ syncResult.message }}</p>
        <p v-if="syncResult.logsSkipped > 0" class="text-gray-500 text-xs mt-1">{{ syncResult.logsSkipped }} übersprungen</p>
      </div>
      <div v-if="status.geocodingInProgress" class="flex items-center gap-2 bg-blue-50 border border-blue-200 rounded-lg p-3">
        <ArrowPathIcon class="h-4 w-4 text-blue-500 animate-spin shrink-0" />
        <p class="text-sm text-blue-800">Ladestandorte werden aufgelöst…</p>
      </div>
      <button
        @click="handleSyncHistory"
        :disabled="isLoading"
        class="w-full flex items-center justify-center gap-2 bg-gray-900 text-white px-4 py-2.5 rounded-lg text-sm font-medium hover:bg-gray-800 transition disabled:opacity-50"
      >
        <ArrowPathIcon class="h-4 w-4" :class="{ 'animate-spin': isLoading }" />
        {{ isLoading ? 'Importiere...' : 'Ladehistorie jetzt importieren' }}
      </button>
      <p class="text-xs text-gray-400 text-center">Täglich automatischer Import aktiviert</p>
    </template>
  </div>
</template>
