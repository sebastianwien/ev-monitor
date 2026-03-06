<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { PlusIcon, TrashIcon, ExclamationTriangleIcon } from '@heroicons/vue/24/outline'
import goeService, { type GoeConnection, CAR_STATE_LABELS } from '@/api/goeService'
import { carService, type Car } from '@/api/carService'

const connections = ref<GoeConnection[]>([])
const cars = ref<Car[]>([])
const loading = ref(false)
const error = ref<string | null>(null)
const showForm = ref(false)

const form = ref({ serial: '', apiKey: '', carId: '', displayName: '' })

onMounted(async () => {
  await Promise.all([loadConnections(), loadCars()])
})

async function loadConnections() {
  try { connections.value = await goeService.getConnections() } catch { /* ignore */ }
}

async function loadCars() {
  try {
    cars.value = await carService.getCars()
    if (cars.value.length > 0 && !form.value.carId) form.value.carId = cars.value[0].id
  } catch { /* ignore */ }
}

async function handleConnect() {
  if (!form.value.serial || !form.value.apiKey || !form.value.carId) {
    error.value = 'Serial, API Key und Fahrzeug sind erforderlich.'
    return
  }
  loading.value = true
  error.value = null
  try {
    await goeService.connect(form.value.serial, form.value.apiKey, form.value.carId, form.value.displayName)
    form.value = { serial: '', apiKey: '', carId: cars.value[0]?.id ?? '', displayName: '' }
    showForm.value = false
    await loadConnections()
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
    await loadConnections()
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

function carStateBadgeClass(state: number) {
  if (state === 2) return 'bg-green-100 text-green-700'
  if (state === 4) return 'bg-blue-100 text-blue-700'
  if (state === 5) return 'bg-red-100 text-red-700'
  return 'bg-gray-100 text-gray-600'
}
</script>

<template>
  <div class="space-y-4">
    <div v-if="error" class="flex items-start gap-2 bg-red-50 border border-red-200 rounded-lg p-3">
      <ExclamationTriangleIcon class="h-4 w-4 text-red-500 mt-0.5 shrink-0" />
      <p class="text-sm text-red-800">{{ error }}</p>
    </div>

    <!-- Connected devices -->
    <div v-if="connections.length > 0" class="space-y-2">
      <p class="text-xs font-medium text-gray-500 uppercase tracking-wide">Verbundene Geräte</p>
      <div v-for="conn in connections" :key="conn.id" class="flex items-center justify-between bg-gray-50 rounded-lg px-4 py-3 border border-gray-200">
        <div>
          <p class="text-sm font-medium text-gray-900">{{ conn.displayName || conn.serial }}</p>
          <p class="text-xs text-gray-500">Serial: {{ conn.serial }}</p>
        </div>
        <div class="flex items-center gap-3">
          <span :class="['text-xs px-2 py-0.5 rounded-full font-medium', carStateBadgeClass(conn.carState)]">
            {{ CAR_STATE_LABELS[conn.carState] ?? 'Unbekannt' }}
          </span>
          <button @click="handleDisconnect(conn.id)" class="text-red-400 hover:text-red-600 transition">
            <TrashIcon class="h-4 w-4" />
          </button>
        </div>
      </div>
    </div>

    <!-- Add form -->
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
  </div>
</template>
