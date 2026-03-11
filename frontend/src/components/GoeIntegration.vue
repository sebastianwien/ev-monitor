<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { PlusIcon, ExclamationTriangleIcon, InformationCircleIcon, ChevronRightIcon } from '@heroicons/vue/24/outline'
import goeService from '@/api/goeService'
import { carService, type Car } from '@/api/carService'
import GoeStatusCard from './GoeStatusCard.vue'
import { useAuthStore } from '@/stores/auth'
import { useWallboxStore } from '@/stores/wallbox'

const authStore = useAuthStore()
const wallboxStore = useWallboxStore()

const cars = ref<Car[]>([])
const loading = ref(false)
const error = ref<string | null>(null)
const showForm = ref(false)

const form = ref({ serial: '', apiKey: '', carId: '', displayName: '' })

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
