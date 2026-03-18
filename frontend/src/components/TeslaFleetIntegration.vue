<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowTopRightOnSquareIcon, ArrowPathIcon, XMarkIcon, CheckCircleIcon, ExclamationTriangleIcon } from '@heroicons/vue/24/outline'
import teslaFleetService, { type TeslaConnectionStatus, type TeslaFleetSyncResult } from '@/api/teslaFleetService'
import type { Car } from '@/api/carService'
import { useCarStore } from '@/stores/car'
import CarSelectDropdown from './CarSelectDropdown.vue'

const route = useRoute()

const status = ref<TeslaConnectionStatus>({ connected: false, vehicleName: null, carId: null, lastSyncAt: null, autoImportEnabled: false, geocodingInProgress: false, vehicleState: null, suspendAfterIdleMinutes: 15 })
const suspendMinutesInput = ref(15)
const settingsSaved = ref(false)
const isLoading = ref(false)
const syncResult = ref<TeslaFleetSyncResult | null>(null)
const error = ref<string | null>(null)
const success = ref<string | null>(null)
const confirmDisconnect = ref(false)
const lastImportedIds = ref<string[]>([])
const showDeleteAllConfirm = ref(false)
const deleteAllLoading = ref(false)
const deleteAllError = ref<string | null>(null)
const fleetApiConfigured = ref(true)
const carStore = useCarStore()
const cars = ref<Car[]>([])
const carsLoaded = ref(false)
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
  try {
    status.value = await teslaFleetService.getStatus()
    suspendMinutesInput.value = status.value.suspendAfterIdleMinutes ?? 15
  } catch { /* ignore */ }
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
    cars.value = await carStore.getCars()
    if (cars.value.length > 0) selectedCarId.value = cars.value[0].id
  } catch { /* ignore */ } finally {
    carsLoaded.value = true
  }
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
    if (syncResult.value.importedLogIds?.length > 0) {
      lastImportedIds.value = syncResult.value.importedLogIds
    }
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

async function handleUpdateSettings() {
  try {
    await teslaFleetService.updateSettings(suspendMinutesInput.value)
    status.value.suspendAfterIdleMinutes = suspendMinutesInput.value
    settingsSaved.value = true
    setTimeout(() => { settingsSaved.value = false }, 2000)
  } catch { error.value = 'Einstellungen konnten nicht gespeichert werden' }
}

async function handleDisconnect() {
  if (!confirmDisconnect.value) { confirmDisconnect.value = true; return }
  confirmDisconnect.value = false
  try {
    await teslaFleetService.disconnect()
    status.value = { connected: false, vehicleName: null, carId: null, lastSyncAt: null, autoImportEnabled: false, geocodingInProgress: false, vehicleState: null, suspendAfterIdleMinutes: 15 }
    syncResult.value = null; success.value = null
  } catch { error.value = 'Trennen fehlgeschlagen' }
}

async function handleUndoLastImport() {
  if (lastImportedIds.value.length === 0) return
  isLoading.value = true
  error.value = null
  try {
    await teslaFleetService.deleteByIds(lastImportedIds.value)
    lastImportedIds.value = []
    syncResult.value = null
    success.value = 'Letzter Import wurde rückgängig gemacht.'
  } catch { error.value = 'Rückgängig machen fehlgeschlagen' } finally {
    isLoading.value = false
  }
}

async function handleDeleteAllImports() {
  deleteAllError.value = null
  deleteAllLoading.value = true
  try {
    await teslaFleetService.deleteAllImports()
    showDeleteAllConfirm.value = false
    lastImportedIds.value = []
    syncResult.value = null
    success.value = 'Alle Tesla-Importe wurden gelöscht.'
  } catch (e: any) {
    deleteAllError.value = e.response?.data?.error || 'Löschen fehlgeschlagen'
  } finally {
    deleteAllLoading.value = false
  }
}


function formatDate(d: string) {
  return new Date(d).toLocaleString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' })
}
</script>

<template>
  <div class="space-y-4">
    <div class="flex items-center gap-3">
      <div class="bg-gray-900 rounded-lg p-2">
        <svg class="h-5 w-5 text-white" viewBox="0 0 24 24" fill="currentColor">
          <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 14H9V8h2v8zm4 0h-2V8h2v8z"/>
        </svg>
      </div>
      <div>
        <h3 class="font-semibold text-gray-900 dark:text-gray-100">Tesla Synchronisation einrichten</h3>
        <p class="text-xs text-gray-500 dark:text-gray-400">Via offizieller Tesla Fleet API</p>
      </div>
    </div>

    <div v-if="success" class="flex items-start gap-2 bg-green-50 dark:bg-green-900/30 border border-green-200 dark:border-green-700 rounded-lg p-3">
      <CheckCircleIcon class="h-4 w-4 text-green-600 dark:text-green-400 mt-0.5 shrink-0" />
      <p class="text-sm text-green-800 dark:text-green-200">{{ success }}</p>
    </div>
    <div v-if="error" class="flex items-start gap-2 bg-red-50 dark:bg-red-900/30 border border-red-200 dark:border-red-700 rounded-lg p-3">
      <ExclamationTriangleIcon class="h-4 w-4 text-red-500 dark:text-red-400 mt-0.5 shrink-0" />
      <p class="text-sm text-red-800 dark:text-red-200">{{ error }}</p>
    </div>

    <div class="bg-blue-50 dark:bg-blue-900/30 border border-blue-200 dark:border-blue-700 rounded-lg p-3 text-sm text-blue-800 dark:text-blue-200 space-y-1">
      <p class="font-medium">Was synchronisiert wird:</p>
      <ul class="list-disc list-inside space-y-0.5 text-blue-700 dark:text-blue-300">
        <li>Supercharger-Sessions (historische einmalig + zukünftige periodisch)</li>
        <li>Künftige Ladevorgänge (mit Duplikats-Erkennung für Datenqualität)</li>
        <li>Energie in kWh, Dauer, Standort</li>
        <li>Kosten (falls Supercharger mit Abrechnung)</li>
        <li>Kilometerstand (ab jetzt, für zukünftige Sessions)</li>
      </ul>
      <p class="text-xs text-blue-600 dark:text-blue-400 mt-2">Bisherige Sessions enthalten keinen km-Stand - die Tesla API liefert diesen nur bei aktiven Ladevorgängen.</p>
    </div>

    <template v-if="!status.connected">
      <p class="text-sm text-gray-600 dark:text-gray-400">
        Verbinde deinen Tesla Account über das offizielle OAuth2-Verfahren.
        Wir importieren dann deine bisherigen Supercharger-Sessions und prüfen täglich auf neue.
      </p>
      <div v-if="cars.length > 1">
        <label class="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">Welches Fahrzeug ist dein Tesla?</label>
        <CarSelectDropdown :cars="cars" v-model="selectedCarId" />
      </div>
      <p v-if="carsLoaded && cars.length === 0" class="text-xs text-amber-700 dark:text-amber-300 bg-amber-50 dark:bg-amber-900/30 border border-amber-200 dark:border-amber-700 rounded-lg p-2">
        Bitte zuerst ein Fahrzeug unter Fahrzeuge anlegen.
      </p>
      <button
        @click="handleConnect"
        :disabled="isLoading || !fleetApiConfigured || cars.length === 0"
        class="btn-3d w-full flex items-center justify-center gap-2 bg-gray-900 dark:bg-gray-600 text-white px-4 py-2.5 rounded-lg text-sm font-medium hover:bg-gray-800 dark:hover:bg-gray-500 transition disabled:opacity-50"
      >
        <ArrowTopRightOnSquareIcon class="h-4 w-4" />
        {{ isLoading ? 'Weiterleitung...' : 'Mit Tesla verbinden' }}
      </button>
    </template>

    <template v-else>
      <div class="bg-green-50 dark:bg-green-900/30 border border-green-200 dark:border-green-700 rounded-lg p-3">
        <div class="flex items-center justify-between">
          <div>
            <p class="text-sm font-semibold text-green-800 dark:text-green-200">Verbunden: {{ status.vehicleName || 'Tesla' }}</p>
            <p v-if="status.lastSyncAt" class="text-xs text-gray-500 dark:text-gray-400 mt-0.5">Letzter Sync: {{ formatDate(status.lastSyncAt) }}</p>
          </div>
          <button
            @click="handleDisconnect"
            class="text-xs px-2 py-1 rounded hover:bg-red-50 transition"
            :class="confirmDisconnect ? 'text-red-700 font-medium' : 'text-red-400'"
          >
            {{ confirmDisconnect ? 'Sicher?' : '' }}<XMarkIcon class="h-4 w-4 inline" />
          </button>
        </div>
      </div>
      <div v-if="syncResult" class="bg-gray-50 dark:bg-gray-900 rounded-lg p-3 text-sm">
        <p class="font-medium text-gray-800 dark:text-gray-200">{{ syncResult.message }}</p>
        <p v-if="syncResult.logsSkipped > 0" class="text-gray-500 dark:text-gray-400 text-xs mt-1">{{ syncResult.logsSkipped }} übersprungen</p>
      </div>
      <div v-if="status.geocodingInProgress" class="flex items-center gap-2 bg-blue-50 dark:bg-blue-900/30 border border-blue-200 dark:border-blue-700 rounded-lg p-3">
        <ArrowPathIcon class="h-4 w-4 text-blue-500 dark:text-blue-400 animate-spin shrink-0" />
        <p class="text-sm text-blue-800 dark:text-blue-200">Ladestandorte werden aufgelöst…</p>
      </div>
      <button
        @click="handleSyncHistory"
        :disabled="isLoading"
        class="btn-3d w-full flex items-center justify-center gap-2 bg-gray-900 dark:bg-gray-600 text-white px-4 py-2.5 rounded-lg text-sm font-medium hover:bg-gray-800 dark:hover:bg-gray-500 transition disabled:opacity-50"
      >
        <ArrowPathIcon class="h-4 w-4" :class="{ 'animate-spin': isLoading }" />
        {{ isLoading ? 'Importiere...' : 'Ladehistorie jetzt importieren' }}
      </button>
      <p class="text-xs text-gray-400 dark:text-gray-500 text-center">Täglich automatischer Import aktiviert</p>

      <!-- Sleep-Window Setting -->
      <div class="border-t border-gray-100 dark:border-gray-700 pt-4 space-y-2">
        <p class="text-xs font-medium text-gray-600 dark:text-gray-400">Echtzeit-Erkennung</p>
        <p class="text-xs text-gray-500 dark:text-gray-400">
          Nach einer Fahrt prüfen wir <span class="font-medium">{{ status.suspendAfterIdleMinutes }} Minuten</span> lang,
          ob du das Auto eingesteckt hast. Danach lassen wir den Tesla schlafen.
        </p>
        <div class="flex items-center gap-2">
          <input
            v-model.number="suspendMinutesInput"
            type="range"
            min="5"
            max="60"
            step="5"
            class="flex-1 accent-gray-900"
          />
          <span class="text-sm font-medium text-gray-700 dark:text-gray-300 w-16 text-right">{{ suspendMinutesInput }} Min</span>
          <button
            @click="handleUpdateSettings"
            :disabled="suspendMinutesInput === status.suspendAfterIdleMinutes"
            class="text-xs px-2.5 py-1 rounded-lg bg-gray-900 dark:bg-gray-600 text-white disabled:opacity-40 transition"
          >
            {{ settingsSaved ? 'Gespeichert' : 'Speichern' }}
          </button>
        </div>
        <p class="text-xs text-amber-700 dark:text-amber-300 bg-amber-50 dark:bg-amber-900/30 border border-amber-200 dark:border-amber-700 rounded-lg p-2">
          Längere Fenster erkennen Ladesessions zuverlässiger, verhindern aber auch dass der Tesla schläft.
          Kürzere Fenster schonen die Batterie, können aber Sessions verpassen.
          <span class="font-medium">15 Min</span> ist ein guter Kompromiss.
        </p>
      </div>

      <!-- Undo last import -->
      <div v-if="lastImportedIds.length > 0" class="border-t border-gray-100 dark:border-gray-700 pt-4">
        <button
          @click="handleUndoLastImport"
          :disabled="isLoading"
          class="w-full flex items-center justify-center gap-2 text-sm px-4 py-2 rounded-lg border border-amber-200 text-amber-700 hover:bg-amber-50 transition disabled:opacity-50"
        >
          <ArrowPathIcon class="h-4 w-4" />
          Letzten Import rückgängig machen ({{ lastImportedIds.length }} Ladevorgänge)
        </button>
      </div>

      <!-- Delete all imports -->
      <div class="border-t border-gray-100 dark:border-gray-700 pt-4">
        <button
          @click="showDeleteAllConfirm = true"
          class="w-full text-sm px-4 py-2 rounded-lg border border-red-200 text-red-600 hover:bg-red-50 transition"
        >
          Alle Tesla-Importe löschen
        </button>
      </div>
    </template>
  </div>

  <!-- Delete all confirmation modal -->
  <div
    v-if="showDeleteAllConfirm"
    class="fixed inset-0 flex items-center justify-center z-50 p-4"
    style="backdrop-filter: blur(8px); background-color: rgba(0, 0, 0, 0.3);"
    @click.self="showDeleteAllConfirm = false"
  >
    <div class="bg-white dark:bg-gray-800 rounded-xl shadow-2xl max-w-md w-full p-6 space-y-4" @click.stop>
      <div class="flex flex-col items-center gap-2 text-center">
        <ExclamationTriangleIcon class="w-8 h-8 text-red-600" />
        <h3 class="text-xl font-bold text-red-600">Alle Tesla-Importe löschen?</h3>
      </div>
      <p class="text-gray-700 dark:text-gray-300 text-sm">
        Du bist dabei, <strong>ALLE über Tesla Fleet API importierten Ladevorgänge</strong> zu löschen.
        Manuell angelegte Logs bleiben erhalten. Dieser Vorgang kann <strong>nicht rückgängig gemacht werden</strong>.
      </p>
      <div v-if="deleteAllError" class="p-3 bg-red-100 dark:bg-red-900/30 text-red-800 dark:text-red-200 rounded-lg border border-red-300 dark:border-red-700 text-sm">
        <ExclamationTriangleIcon class="w-4 h-4 inline-block mr-1" />
        {{ deleteAllError }}
      </div>
      <div class="flex gap-3">
        <button
          @click="showDeleteAllConfirm = false"
          :disabled="deleteAllLoading"
          class="flex-1 px-4 py-3 bg-gray-200 dark:bg-gray-700 text-gray-800 dark:text-gray-200 font-semibold rounded-lg hover:bg-gray-300 dark:hover:bg-gray-600 transition disabled:opacity-50"
        >
          Abbrechen
        </button>
        <button
          @click="handleDeleteAllImports"
          :disabled="deleteAllLoading"
          class="flex-1 px-4 py-3 bg-red-600 text-white font-semibold rounded-lg hover:bg-red-700 transition disabled:opacity-50 flex items-center justify-center gap-2"
        >
          <svg v-if="deleteAllLoading" class="animate-spin h-5 w-5 text-white flex-shrink-0" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
            <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
            <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
          </svg>
          {{ deleteAllLoading ? 'Wird gelöscht…' : 'Ja, alle löschen' }}
        </button>
      </div>
    </div>
  </div>
</template>
