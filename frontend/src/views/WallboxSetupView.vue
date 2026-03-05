<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useAuthStore } from '../stores/auth'
import { carService, type Car } from '../api/carService'
import { wallboxService, type WallboxConnection } from '../api/wallboxService'
import {
  BoltIcon,
  PlusIcon,
  TrashIcon,
  CheckCircleIcon,
  ExclamationTriangleIcon,
  ClipboardDocumentIcon
} from '@heroicons/vue/24/outline'

const authStore = useAuthStore()
const userId = computed(() => authStore.user?.userId || '')

const connections = ref<WallboxConnection[]>([])
const cars = ref<Car[]>([])
const loading = ref(true)
const saving = ref(false)
const error = ref<string | null>(null)
const success = ref<string | null>(null)

// Form
const showForm = ref(false)
const formChargePointId = ref('')
const formCarId = ref('')
const formDisplayName = ref('')

// OCPP WebSocket URL (points to Core Nginx which proxies to Wallbox Service)
const ocppUrl = computed(() => {
  const base = import.meta.env.VITE_API_BASE_URL?.replace('/api', '') || window.location.origin
  return `${base.replace('https://', 'wss://').replace('http://', 'ws://')}/ocpp/ws/${formChargePointId.value || '<deine-chargepoint-id>'}`
})

const load = async () => {
  loading.value = true
  error.value = null
  try {
    const [conns, carList] = await Promise.all([
      wallboxService.getConnections(userId.value),
      carService.getCars()
    ])
    connections.value = conns
    cars.value = carList
  } catch (e: any) {
    error.value = 'Fehler beim Laden der Wallbox-Verbindungen.'
  } finally {
    loading.value = false
  }
}

const save = async () => {
  if (!formChargePointId.value.trim()) {
    error.value = 'ChargePoint-ID darf nicht leer sein.'
    return
  }
  saving.value = true
  error.value = null
  success.value = null
  try {
    const conn = await wallboxService.registerConnection({
      userId: userId.value,
      ocppChargePointId: formChargePointId.value.trim(),
      carId: formCarId.value || null,
      displayName: formDisplayName.value.trim() || null
    })
    connections.value.push(conn)
    showForm.value = false
    formChargePointId.value = ''
    formCarId.value = ''
    formDisplayName.value = ''
    success.value = 'Wallbox erfolgreich verbunden! Jetzt die OCPP-URL in deiner Wallbox-App eintragen.'
  } catch (e: any) {
    if (e?.response?.status === 409) {
      error.value = 'Diese ChargePoint-ID ist bereits registriert.'
    } else {
      error.value = 'Verbindung konnte nicht gespeichert werden.'
    }
  } finally {
    saving.value = false
  }
}

const remove = async (id: string) => {
  try {
    await wallboxService.deleteConnection(id, userId.value)
    connections.value = connections.value.filter(c => c.id !== id)
    success.value = 'Wallbox-Verbindung entfernt.'
  } catch {
    error.value = 'Verbindung konnte nicht gelöscht werden.'
  }
}

const carLabel = (carId: string | null) => {
  if (!carId) return null
  const car = cars.value.find(c => c.id === carId)
  return car ? `${car.brand} ${car.model} (${car.year})` : carId
}

const copyUrl = (url: string) => {
  navigator.clipboard.writeText(url)
  success.value = 'URL kopiert!'
}

onMounted(load)
</script>

<template>
  <div class="max-w-2xl mx-auto px-4 py-8">
    <!-- Header -->
    <div class="mb-8">
      <div class="flex items-center gap-3 mb-2">
        <BoltIcon class="h-7 w-7 text-green-600" />
        <h1 class="text-2xl font-bold text-gray-900">Wallbox verbinden</h1>
      </div>
      <p class="text-gray-600">
        Verbinde deine Heimwallbox über OCPP 1.6. Ladevorgänge werden automatisch importiert.
      </p>
    </div>

    <!-- Alerts -->
    <div v-if="success" class="flex items-start gap-2 bg-green-50 border border-green-200 rounded-lg p-4 mb-6">
      <CheckCircleIcon class="h-5 w-5 text-green-600 mt-0.5 shrink-0" />
      <p class="text-sm text-green-800">{{ success }}</p>
    </div>
    <div v-if="error" class="flex items-start gap-2 bg-red-50 border border-red-200 rounded-lg p-4 mb-6">
      <ExclamationTriangleIcon class="h-5 w-5 text-red-500 mt-0.5 shrink-0" />
      <p class="text-sm text-red-800">{{ error }}</p>
    </div>

    <!-- Loading -->
    <div v-if="loading" class="text-center text-gray-500 py-12">Lade...</div>

    <template v-else>
      <!-- Existing Connections -->
      <div v-if="connections.length > 0" class="mb-8 space-y-3">
        <h2 class="text-sm font-semibold text-gray-700 uppercase tracking-wide">Verbundene Wallboxen</h2>
        <div
          v-for="conn in connections"
          :key="conn.id"
          class="bg-white border border-gray-200 rounded-xl p-4 flex items-start justify-between gap-4"
        >
          <div class="min-w-0">
            <div class="flex items-center gap-2">
              <BoltIcon class="h-4 w-4 text-green-600 shrink-0" />
              <span class="font-medium text-gray-900 truncate">
                {{ conn.displayName || conn.ocppChargePointId }}
              </span>
              <span class="text-xs bg-green-100 text-green-700 px-2 py-0.5 rounded-full">Aktiv</span>
            </div>
            <p class="text-xs text-gray-500 mt-1 font-mono">{{ conn.ocppChargePointId }}</p>
            <p v-if="carLabel(conn.carId)" class="text-xs text-gray-500 mt-0.5">
              {{ carLabel(conn.carId) }}
            </p>
          </div>
          <button
            @click="remove(conn.id)"
            class="text-gray-400 hover:text-red-500 transition shrink-0"
            title="Verbindung entfernen"
          >
            <TrashIcon class="h-4 w-4" />
          </button>
        </div>
      </div>

      <!-- Add New Connection -->
      <div v-if="!showForm" class="mb-8">
        <button
          @click="showForm = true; success = null; error = null"
          class="flex items-center gap-2 bg-green-600 text-white px-5 py-2.5 rounded-lg font-medium hover:bg-green-700 transition"
        >
          <PlusIcon class="h-5 w-5" />
          Wallbox hinzufügen
        </button>
      </div>

      <div v-else class="bg-white border border-gray-200 rounded-xl p-6 mb-8 space-y-5">
        <h2 class="font-semibold text-gray-900">Neue Wallbox verbinden</h2>

        <!-- ChargePoint ID -->
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">
            ChargePoint-ID <span class="text-red-500">*</span>
          </label>
          <input
            v-model="formChargePointId"
            type="text"
            placeholder="z.B. ESP32_A1B2C3 oder go-e Seriennummer"
            class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-green-500"
          />
          <p class="text-xs text-gray-500 mt-1">
            Bei go-e: Seriennummer aus der App unter Einstellungen → Geräteinformationen
          </p>
        </div>

        <!-- Display Name -->
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Name (optional)</label>
          <input
            v-model="formDisplayName"
            type="text"
            placeholder="z.B. Garage Zuhause"
            class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-green-500"
          />
        </div>

        <!-- Car Selection -->
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Fahrzeug (optional)</label>
          <select
            v-model="formCarId"
            class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-green-500"
          >
            <option value="">Kein Fahrzeug zuordnen</option>
            <option v-for="car in cars" :key="car.id" :value="car.id">
              {{ car.brand }} {{ car.model }} ({{ car.year }})
            </option>
          </select>
        </div>

        <!-- OCPP URL Preview -->
        <div v-if="formChargePointId.trim()" class="bg-gray-50 border border-gray-200 rounded-lg p-4">
          <p class="text-xs font-semibold text-gray-700 mb-2 uppercase tracking-wide">
            OCPP-URL für deine Wallbox-App:
          </p>
          <div class="flex items-center gap-2">
            <code class="text-xs text-gray-800 break-all flex-1">{{ ocppUrl }}</code>
            <button
              @click="copyUrl(ocppUrl)"
              class="text-gray-400 hover:text-green-600 transition shrink-0"
              title="Kopieren"
            >
              <ClipboardDocumentIcon class="h-4 w-4" />
            </button>
          </div>
          <p class="text-xs text-gray-500 mt-2">
            go-e: App → Einstellungen → OCPP → Server URL eintragen
          </p>
        </div>

        <!-- Buttons -->
        <div class="flex gap-3 pt-2">
          <button
            @click="save"
            :disabled="saving"
            class="bg-green-600 text-white px-5 py-2 rounded-lg text-sm font-medium hover:bg-green-700 transition disabled:opacity-50"
          >
            {{ saving ? 'Speichern...' : 'Verbindung speichern' }}
          </button>
          <button
            @click="showForm = false; error = null"
            class="text-gray-600 px-5 py-2 rounded-lg text-sm font-medium hover:bg-gray-100 transition"
          >
            Abbrechen
          </button>
        </div>
      </div>

      <!-- Setup Instructions -->
      <div class="bg-amber-50 border border-amber-200 rounded-xl p-5">
        <h3 class="font-semibold text-amber-900 mb-3">Setup-Anleitung (go-e Charger)</h3>
        <ol class="text-sm text-amber-800 space-y-2 list-decimal list-inside">
          <li>go-e App öffnen → deine Wallbox auswählen</li>
          <li>Einstellungen → OCPP → OCPP aktivieren</li>
          <li>Server URL: <strong>die oben angezeigte OCPP-URL eintragen</strong></li>
          <li>ChargePoint-ID: deine Seriennummer (gleiche wie oben eingegeben)</li>
          <li>Speichern — ab jetzt werden Ladevorgänge automatisch importiert</li>
        </ol>
        <p class="text-xs text-amber-700 mt-3">
          Andere OCPP-fähige Wallboxen (Easee, KEBA, Wallbe, Heidelberg) funktionieren analog.
        </p>
      </div>
    </template>
  </div>
</template>
