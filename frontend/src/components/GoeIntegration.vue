<script setup lang="ts">
import { ref, onMounted, watch, computed } from 'vue'
import { PlusIcon, ExclamationTriangleIcon, InformationCircleIcon, ChevronRightIcon, MapPinIcon, CheckCircleIcon } from '@heroicons/vue/24/outline'
import goeService from '@/api/goeService'
import { carService, type Car } from '@/api/carService'
import GoeStatusCard from './GoeStatusCard.vue'
import { useAuthStore } from '@/stores/auth'
import { useWallboxStore } from '@/stores/wallbox'
import * as geohash from 'ngeohash'

const authStore = useAuthStore()
const wallboxStore = useWallboxStore()

const cars = ref<Car[]>([])
const loading = ref(false)
const error = ref<string | null>(null)
const showForm = ref(false)

const form = ref({ serial: '', apiKey: '', carId: '', displayName: '' })
const latitude = ref<number | null>(null)
const longitude = ref<number | null>(null)
const locationStatus = ref<'idle' | 'loading' | 'success' | 'error'>('idle')
const locationError = ref<string | null>(null)

const geohashValue = computed(() => {
  if (latitude.value && longitude.value) {
    return geohash.encode(latitude.value, longitude.value, 5)
  }
  return null
})

const infoExpanded = ref(!wallboxStore.hasConnections)
watch(() => wallboxStore.hasConnections, (hasConnections) => {
  if (hasConnections) infoExpanded.value = false
})

onMounted(async () => {
  await loadCars()
})

async function loadCars() {
  try {
    cars.value = await carService.getCars()
    if (cars.value.length > 0 && !form.value.carId) form.value.carId = cars.value[0].id
  } catch { /* ignore */ }
}

function useCurrentLocation() {
  if (!navigator.geolocation) {
    locationError.value = 'Dein Browser unterstützt keine Standortabfrage.'
    locationStatus.value = 'error'
    return
  }

  locationStatus.value = 'loading'
  locationError.value = null

  navigator.geolocation.getCurrentPosition(
    (position) => {
      latitude.value = position.coords.latitude
      longitude.value = position.coords.longitude
      locationStatus.value = 'success'
    },
    (err) => {
      console.error('Geolocation error:', err)
      locationStatus.value = 'error'
      locationError.value = 'Standortzugriff verweigert. Bitte erlaube den Zugriff in deinem Browser.'
    }
  )
}

async function handleConnect() {
  if (!form.value.serial || !form.value.apiKey || !form.value.carId) {
    error.value = 'Serial, API Key und Fahrzeug sind erforderlich.'
    return
  }
  loading.value = true
  error.value = null
  try {
    await goeService.connect(form.value.serial, form.value.apiKey, form.value.carId, form.value.displayName, geohashValue.value)
    form.value = { serial: '', apiKey: '', carId: cars.value[0]?.id ?? '', displayName: '' }
    latitude.value = null
    longitude.value = null
    locationStatus.value = 'idle'
    showForm.value = false
    await wallboxStore.refresh()
  } catch (e: any) {
    error.value = e.response?.data?.message || 'Verbindung fehlgeschlagen'
  } finally {
    loading.value = false
  }
}

async function handleDisconnect(id: string) {
  if (!confirm('go-eCharger Verbindung wirklich trennen?')) return
  try {
    await goeService.disconnect(id)
    await wallboxStore.refresh()
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
</script>

<template>
  <div class="space-y-4">
    <div v-if="error" class="flex items-start gap-2 bg-red-50 border border-red-200 rounded-lg p-3">
      <ExclamationTriangleIcon class="h-4 w-4 text-red-500 mt-0.5 shrink-0" />
      <p class="text-sm text-red-800">{{ error }}</p>
    </div>

    <!-- Info Box -->
    <div class="border border-gray-200 rounded-lg overflow-hidden">
      <button
        type="button"
        @click="infoExpanded = !infoExpanded"
        class="w-full flex items-center justify-between px-3 py-2.5 text-sm text-gray-500 hover:bg-gray-50 transition text-left">
        <span class="flex items-center gap-1.5">
          <InformationCircleIcon class="w-4 h-4 text-indigo-400 flex-shrink-0" />
          Wie funktioniert der go-eCharger Import?
        </span>
        <ChevronRightIcon class="w-4 h-4 flex-shrink-0 transition-transform duration-200"
          :class="infoExpanded ? 'rotate-90' : ''" />
      </button>
      <div v-if="infoExpanded" class="px-3 pb-3 text-sm text-gray-600 space-y-2 border-t border-gray-100 pt-2.5">
        <p>
          Nach einmaliger Einrichtung läuft alles <strong>vollautomatisch</strong>: EV Monitor fragt
          alle paar Minuten bei der go-e Cloud an, ob gerade geladen wird.
        </p>
        <p>
          Sobald du das Kabel einsteckst, merkt sich das System Startzeitpunkt und Energiezählerstand.
          Wenn du fertig bist, wird daraus automatisch ein <strong>Ladeeintrag</strong> in deinem
          Dashboard angelegt — mit Datum, kWh und optional den Kosten.
        </p>
        <p>
          Den <strong>Strompreis</strong> kannst du direkt bei der Wallbox-Verbindung hinterlegen —
          dann rechnet EV Monitor die Kosten für jede Session automatisch aus.
        </p>
      </div>
    </div>

    <!-- Connected devices -->
    <div v-if="wallboxStore.hasConnections" class="space-y-3">
      <p class="text-xs font-medium text-gray-500 uppercase tracking-wide">Verbundene Geräte</p>
      <GoeStatusCard
        v-for="conn in wallboxStore.connections"
        :key="conn.id"
        :connection-id="conn.id"
        :mock-connection="conn.id === 'demo' ? conn : undefined"
        @disconnect="handleDisconnect"
      />
    </div>

    <!-- Add form (hidden for demo users) -->
    <template v-if="!authStore.isDemoAccount">
      <div v-if="showForm" class="border border-gray-200 rounded-lg p-4 space-y-3">
        <p class="text-sm font-medium text-gray-800">go-eCharger verbinden</p>
        <div>
          <label class="block text-xs text-gray-500 mb-1">Serial-Nummer</label>
          <input v-model="form.serial" type="text" placeholder="z.B. A0123456" class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm" />
        </div>
        <div>
          <label class="block text-xs text-gray-500 mb-1">Cloud API Key</label>
          <input v-model="form.apiKey" type="password" placeholder="aus der go-e App" class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm" />
        </div>
        <div v-if="cars.length > 0">
          <label class="block text-xs text-gray-500 mb-1">Fahrzeug</label>
          <select v-model="form.carId" class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm">
            <option v-for="car in cars" :key="car.id" :value="car.id">
              {{ carLabel(car) }}
            </option>
          </select>
        </div>
        <div>
          <label class="block text-xs text-gray-500 mb-1">Name (optional)</label>
          <input v-model="form.displayName" type="text" placeholder="z.B. Heimlader Garage" class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm" />
        </div>

        <!-- Location (optional) -->
        <div class="border border-indigo-200 rounded-lg p-3 bg-indigo-50/50">
          <div class="flex items-start gap-2 mb-2">
            <MapPinIcon class="w-4 h-4 text-indigo-600 mt-0.5 flex-shrink-0" />
            <div class="flex-1">
              <label class="block text-xs font-medium text-indigo-900 mb-1">Standort (optional)</label>
              <p class="text-xs text-indigo-700 mb-2">
                Mit Standort profitierst du von:
              </p>
              <ul class="text-xs text-indigo-700 space-y-1 mb-3">
                <li class="flex items-start gap-1.5">
                  <span class="text-green-600 mt-0.5">✓</span>
                  <span><strong>Temperatur-Daten:</strong> Wie saisonale Schwankungen deinen Verbrauch beeinflussen</span>
                </li>
                <li class="flex items-start gap-1.5">
                  <span class="text-green-600 mt-0.5">✓</span>
                  <span><strong>Heatmap:</strong> Ladevorgänge werden auf der Karte angezeigt</span>
                </li>
                <li class="flex items-start gap-1.5">
                  <span class="text-green-600 mt-0.5">✓</span>
                  <span><strong>Privacy:</strong> Wir speichern nur einen ~5km Bereich (Geohash), keine exakte Adresse</span>
                </li>
              </ul>

              <button
                v-if="locationStatus === 'idle'"
                type="button"
                @click="useCurrentLocation"
                class="flex items-center gap-2 px-3 py-1.5 bg-indigo-600 text-white text-xs font-medium rounded-md hover:bg-indigo-700 transition">
                <MapPinIcon class="w-3.5 h-3.5" />
                Aktuelle Position verwenden
              </button>

              <div v-if="locationStatus === 'loading'" class="flex items-center gap-2 text-xs text-indigo-700">
                <svg class="animate-spin h-3.5 w-3.5" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                  <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                  <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                </svg>
                Standort wird ermittelt...
              </div>

              <div v-if="locationStatus === 'success'" class="flex items-center gap-2 text-xs text-green-700">
                <CheckCircleIcon class="w-4 h-4" />
                Standort erfasst (Geohash: {{ geohashValue }})
              </div>

              <div v-if="locationStatus === 'error' && locationError" class="text-xs text-red-600 mt-1">
                {{ locationError }}
              </div>
            </div>
          </div>
        </div>

        <div class="flex gap-2">
          <button @click="handleConnect" :disabled="loading" class="flex-1 bg-green-600 text-white py-2 rounded-lg text-sm font-medium hover:bg-green-700 disabled:opacity-50 transition">
            {{ loading ? 'Verbinde...' : 'Verbinden' }}
          </button>
          <button @click="showForm = false" class="px-4 py-2 border border-gray-300 rounded-lg text-sm text-gray-600 hover:bg-gray-50 transition">
            Abbrechen
          </button>
        </div>
      </div>

      <button v-if="!showForm" @click="showForm = true" class="flex items-center gap-2 text-sm text-green-600 hover:text-green-700 font-medium">
        <PlusIcon class="h-4 w-4" />
        go-eCharger hinzufügen
      </button>
    </template>
  </div>
</template>
