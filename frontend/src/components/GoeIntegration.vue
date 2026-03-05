<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { BoltIcon, PlusIcon, TrashIcon, CheckCircleIcon, ExclamationTriangleIcon, ArrowPathIcon } from '@heroicons/vue/24/outline'
import goeService, { type GoeConnectionStatus } from '@/api/goeService'
import { carService, type Car } from '@/api/carService'

const connections = ref<GoeConnectionStatus[]>([])
const cars = ref<Car[]>([])
const loading = ref(true)
const showForm = ref(false)
const saving = ref(false)
const error = ref<string | null>(null)
const success = ref<string | null>(null)

// Form
const formSerial = ref('')
const formApiKey = ref('')
const formCarId = ref('')
const formDisplayName = ref('')

const enumToLabel = (v: string) =>
  v.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, c => c.toUpperCase())

const carLabel = (car: Car) => {
  const name = `${enumToLabel(car.brand)} ${enumToLabel(car.model)}`
  return car.licensePlate ? `${name} · ${car.licensePlate}` : name
}

const carStateColor = (state: number) => {
  if (state === 2) return 'text-green-600 bg-green-50'
  if (state === 4) return 'text-blue-600 bg-blue-50'
  if (state === 5) return 'text-red-600 bg-red-50'
  return 'text-gray-600 bg-gray-50'
}

async function load() {
  loading.value = true
  try {
    const [conns, carList] = await Promise.all([
      goeService.getConnections(),
      carService.getCars(),
    ])
    connections.value = conns
    cars.value = carList
  } catch {
    error.value = 'Fehler beim Laden'
  } finally {
    loading.value = false
  }
}

async function save() {
  if (!formSerial.value.trim() || !formApiKey.value.trim() || !formCarId.value) {
    error.value = 'Seriennummer, API Key und Fahrzeug sind Pflichtfelder.'
    return
  }
  saving.value = true
  error.value = null
  success.value = null
  try {
    const conn = await goeService.connect({
      serial: formSerial.value.trim(),
      apiKey: formApiKey.value.trim(),
      carId: formCarId.value,
      displayName: formDisplayName.value.trim() || formSerial.value.trim(),
    })
    connections.value.push(conn)
    showForm.value = false
    formSerial.value = ''
    formApiKey.value = ''
    formCarId.value = ''
    formDisplayName.value = ''
    success.value = 'go-eCharger verbunden! Ladevorgänge werden ab jetzt automatisch importiert.'
  } catch (e: any) {
    error.value = e.response?.data || e.message || 'Verbindung fehlgeschlagen'
  } finally {
    saving.value = false
  }
}

async function remove(id: string) {
  if (!confirm('go-e Verbindung wirklich trennen?')) return
  try {
    await goeService.disconnect(id)
    connections.value = connections.value.filter(c => c.id !== id)
    success.value = 'Verbindung getrennt.'
  } catch {
    error.value = 'Trennen fehlgeschlagen'
  }
}

onMounted(load)
</script>

<template>
  <div class="space-y-4">
    <!-- Alerts -->
    <div v-if="success" class="flex items-start gap-2 bg-green-50 border border-green-200 rounded-lg p-3">
      <CheckCircleIcon class="h-4 w-4 text-green-600 mt-0.5 shrink-0" />
      <p class="text-sm text-green-800">{{ success }}</p>
    </div>
    <div v-if="error" class="flex items-start gap-2 bg-red-50 border border-red-200 rounded-lg p-3">
      <ExclamationTriangleIcon class="h-4 w-4 text-red-500 mt-0.5 shrink-0" />
      <p class="text-sm text-red-800">{{ error }}</p>
    </div>

    <div v-if="loading" class="text-center text-gray-500 py-4 text-sm">Lade...</div>

    <template v-else>
      <!-- Existing connections -->
      <div v-if="connections.length > 0" class="space-y-2">
        <div
          v-for="conn in connections"
          :key="conn.id"
          class="flex items-center justify-between bg-gray-50 border border-gray-200 rounded-lg p-3"
        >
          <div class="min-w-0">
            <div class="flex items-center gap-2">
              <BoltIcon class="h-4 w-4 text-green-600 shrink-0" />
              <span class="font-medium text-sm text-gray-900 truncate">{{ conn.displayName }}</span>
              <span :class="['text-xs px-2 py-0.5 rounded-full font-medium', carStateColor(conn.carState)]">
                {{ conn.carStateLabel }}
              </span>
            </div>
            <p class="text-xs text-gray-400 mt-0.5 font-mono">{{ conn.serial }}</p>
            <p v-if="conn.lastPollError" class="text-xs text-red-500 mt-0.5 truncate max-w-xs">
              {{ conn.lastPollError }}
            </p>
          </div>
          <button @click="remove(conn.id)" class="text-gray-400 hover:text-red-500 transition shrink-0 ml-2">
            <TrashIcon class="h-4 w-4" />
          </button>
        </div>
      </div>

      <!-- Add button -->
      <div v-if="!showForm">
        <button
          @click="showForm = true; error = null; success = null"
          class="flex items-center gap-2 text-sm bg-green-600 text-white px-4 py-2 rounded-lg font-medium hover:bg-green-700 transition"
        >
          <PlusIcon class="h-4 w-4" />
          go-eCharger hinzufügen
        </button>
      </div>

      <!-- Add form -->
      <div v-else class="bg-gray-50 border border-gray-200 rounded-xl p-5 space-y-4">
        <h3 class="font-medium text-gray-900">Neuen go-eCharger verbinden</h3>

        <div class="bg-blue-50 border border-blue-100 rounded-lg p-3 text-xs text-blue-800 space-y-1">
          <p class="font-medium">So findest du deine Zugangsdaten:</p>
          <ol class="list-decimal list-inside space-y-0.5">
            <li>go-e App öffnen → Wallbox auswählen</li>
            <li>Internet → Erweiterte Einstellungen</li>
            <li>Cloud API aktivieren</li>
            <li>Seriennummer + API Key notieren</li>
          </ol>
        </div>

        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">
            Seriennummer <span class="text-red-500">*</span>
          </label>
          <input
            v-model="formSerial"
            type="text"
            placeholder="z.B. 123456"
            class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-green-500"
          />
          <p class="text-xs text-gray-500 mt-1">6-stellige Seriennummer aus der go-e App</p>
        </div>

        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">
            Cloud API Key <span class="text-red-500">*</span>
          </label>
          <input
            v-model="formApiKey"
            type="password"
            placeholder="Cloud API Key"
            class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-green-500"
          />
        </div>

        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">
            Fahrzeug <span class="text-red-500">*</span>
          </label>
          <select
            v-model="formCarId"
            class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-green-500"
          >
            <option value="">Fahrzeug wählen</option>
            <option v-for="car in cars" :key="car.id" :value="car.id">
              {{ carLabel(car) }}
            </option>
          </select>
        </div>

        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Name (optional)</label>
          <input
            v-model="formDisplayName"
            type="text"
            placeholder="z.B. Garage Zuhause"
            class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-green-500"
          />
        </div>

        <div class="flex gap-3 pt-1">
          <button
            @click="save"
            :disabled="saving"
            class="flex items-center gap-2 bg-green-600 text-white px-4 py-2 rounded-lg text-sm font-medium hover:bg-green-700 transition disabled:opacity-50"
          >
            <ArrowPathIcon v-if="saving" class="h-4 w-4 animate-spin" />
            {{ saving ? 'Verbinde & teste...' : 'Verbindung speichern' }}
          </button>
          <button
            @click="showForm = false; error = null"
            class="text-gray-600 px-4 py-2 rounded-lg text-sm font-medium hover:bg-gray-100 transition"
          >
            Abbrechen
          </button>
        </div>
      </div>
    </template>
  </div>
</template>
