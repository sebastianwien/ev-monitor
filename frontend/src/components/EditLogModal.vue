<template>
  <div class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50">
    <div class="bg-white rounded-2xl shadow-xl w-full max-w-lg flex flex-col max-h-[90vh]">
      <!-- Header -->
      <div class="flex items-center justify-between p-5 border-b border-gray-100">
        <h2 class="text-lg font-semibold text-gray-900">Ladevorgang bearbeiten</h2>
        <button @click="$emit('close')" class="text-gray-400 hover:text-gray-600 transition-colors">
          <XMarkIcon class="w-5 h-5" />
        </button>
      </div>

      <div class="overflow-y-auto p-5 space-y-4">
        <!-- kWh + Kosten -->
        <div class="grid grid-cols-2 gap-3">
          <div class="space-y-1">
            <label class="block text-sm font-medium text-gray-700">
              <BoltIcon class="w-4 h-4 inline mr-1 text-yellow-500" />Geladen (kWh)
            </label>
            <input v-model.number="form.kwhCharged" type="number" step="0.01" min="0.01"
              class="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-green-500" />
          </div>
          <div class="space-y-1">
            <label class="block text-sm font-medium text-gray-700">Kosten (€)</label>
            <input v-model.number="form.costEur" type="number" step="0.01" min="0"
              class="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-green-500" />
          </div>
        </div>

        <!-- Datum -->
        <div class="space-y-1">
          <label class="block text-sm font-medium text-gray-700">Datum & Uhrzeit</label>
          <input v-model="form.loggedAt" type="datetime-local"
            class="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-green-500" />
        </div>

        <!-- Tachostand -->
        <div class="space-y-1">
          <label class="block text-sm font-medium text-gray-700">
            <TruckIcon class="w-4 h-4 inline mr-1 text-gray-400" />Tachostand (km)
          </label>
          <input v-model.number="form.odometerKm" type="number" step="1" min="0" placeholder="optional"
            class="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-green-500" />
        </div>

        <!-- SoC vorher + nachher -->
        <div class="grid grid-cols-2 gap-3">
          <div class="space-y-1">
            <label class="block text-sm font-medium text-gray-700">
              <Battery0Icon class="w-4 h-4 inline mr-1 text-gray-400" />SoC vorher (%)
            </label>
            <input v-model.number="form.socBeforeChargePercent" type="number" min="0" max="100" placeholder="optional"
              class="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-green-500" />
          </div>
          <div class="space-y-1">
            <label class="block text-sm font-medium text-gray-700">
              <Battery0Icon class="w-4 h-4 inline mr-1 text-green-500" />SoC nachher (%)
            </label>
            <input v-model.number="form.socAfterChargePercent" type="number" min="0" max="100" placeholder="optional"
              class="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-green-500" />
          </div>
        </div>

        <!-- Dauer + Ladeleistung -->
        <div class="grid grid-cols-2 gap-3">
          <div class="space-y-1">
            <label class="block text-sm font-medium text-gray-700">
              <ClockIcon class="w-4 h-4 inline mr-1 text-gray-400" />Dauer (min)
            </label>
            <input v-model.number="form.chargeDurationMinutes" type="number" step="1" min="0" placeholder="optional"
              class="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-green-500" />
          </div>
          <div class="space-y-1">
            <label class="block text-sm font-medium text-gray-700">Max. Ladeleistung (kW)</label>
            <input v-model.number="form.maxChargingPowerKw" type="number" step="0.1" min="0" placeholder="optional"
              class="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-green-500" />
          </div>
        </div>

        <!-- Standort -->
        <div class="space-y-1">
          <label class="block text-sm font-medium text-gray-700">Standort aktualisieren</label>
          <div class="relative">
            <input
              v-model="locationSearchQuery"
              type="text"
              placeholder="Ort suchen (ersetzt bestehenden Standort)…"
              class="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-green-500"
              @focus="showSuggestions = true"
            />
            <ul v-if="showSuggestions && suggestions.length > 0"
              class="absolute z-10 mt-1 w-full bg-white border border-gray-200 rounded-xl shadow-lg max-h-48 overflow-y-auto">
              <li v-for="s in suggestions" :key="s.place_id"
                class="px-3 py-2 text-sm hover:bg-gray-50 cursor-pointer"
                @mousedown.prevent="selectLocation(s)">
                {{ s.display_name }}
              </li>
            </ul>
          </div>
          <p v-if="newLocationName" class="text-xs text-green-600 mt-1">
            Neuer Standort: {{ newLocationName }}
          </p>
          <p v-else-if="log.geohash" class="text-xs text-gray-400 mt-1">
            Aktueller Standort gespeichert (Geohash: {{ log.geohash }})
          </p>
        </div>

        <p v-if="errorMsg" class="text-sm text-red-600 bg-red-50 rounded-xl p-3">{{ errorMsg }}</p>
      </div>

      <!-- Footer -->
      <div class="flex justify-end gap-3 p-5 border-t border-gray-100 shrink-0">
        <button
          @click="$emit('close')"
          class="px-4 py-2 text-sm font-medium text-gray-700 bg-white border border-gray-200 rounded-xl hover:bg-gray-50 transition-colors"
        >Abbrechen</button>
        <button
          @click="save"
          :disabled="loading || !form.kwhCharged"
          class="px-5 py-2 text-sm font-medium text-white bg-green-600 rounded-xl hover:bg-green-700 disabled:opacity-40 disabled:cursor-not-allowed transition-colors flex items-center gap-2"
        >
          <span v-if="loading" class="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
          Speichern
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { XMarkIcon, BoltIcon, TruckIcon, Battery0Icon, ClockIcon } from '@heroicons/vue/24/outline'
import api from '../api/axios'

export interface EvLogResponse {
  id: string
  carId: string
  kwhCharged: number
  costEur: number | null
  chargeDurationMinutes: number | null
  geohash: string | null
  odometerKm: number | null
  maxChargingPowerKw: number | null
  socAfterChargePercent: number | null
  socBeforeChargePercent: number | null
  loggedAt: string
}

const props = defineProps<{ log: EvLogResponse }>()
const emit = defineEmits<{ close: []; saved: [log: EvLogResponse] }>()

// Format loggedAt for datetime-local input (YYYY-MM-DDTHH:mm)
const toDatetimeLocal = (iso: string) => {
  const d = new Date(iso)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

const form = ref({
  kwhCharged: props.log.kwhCharged,
  costEur: props.log.costEur ?? null,
  chargeDurationMinutes: props.log.chargeDurationMinutes ?? null,
  odometerKm: props.log.odometerKm ?? null,
  maxChargingPowerKw: props.log.maxChargingPowerKw ?? null,
  socAfterChargePercent: props.log.socAfterChargePercent ?? null,
  socBeforeChargePercent: props.log.socBeforeChargePercent ?? null,
  loggedAt: toDatetimeLocal(props.log.loggedAt),
})

const loading = ref(false)
const errorMsg = ref('')

// Location search
const locationSearchQuery = ref('')
const suggestions = ref<any[]>([])
const showSuggestions = ref(false)
const newLocationName = ref('')
const newLatitude = ref<number | null>(null)
const newLongitude = ref<number | null>(null)

let searchTimer: any = null
watch(locationSearchQuery, (q) => {
  clearTimeout(searchTimer)
  if (!q || q.length < 3) { suggestions.value = []; return }
  searchTimer = setTimeout(async () => {
    try {
      const res = await fetch(`https://nominatim.openstreetmap.org/search?q=${encodeURIComponent(q)}&format=json&limit=5`)
      suggestions.value = await res.json()
    } catch { /* ignore */ }
  }, 300)
})

function selectLocation(s: any) {
  newLatitude.value = parseFloat(s.lat)
  newLongitude.value = parseFloat(s.lon)
  newLocationName.value = s.display_name
  locationSearchQuery.value = s.display_name
  showSuggestions.value = false
}

async function save() {
  loading.value = true
  errorMsg.value = ''
  try {
    const payload: Record<string, any> = {
      kwhCharged: form.value.kwhCharged,
      costEur: form.value.costEur,
      chargeDurationMinutes: form.value.chargeDurationMinutes || null,
      odometerKm: form.value.odometerKm || null,
      maxChargingPowerKw: form.value.maxChargingPowerKw || null,
      socAfterChargePercent: form.value.socAfterChargePercent || null,
      socBeforeChargePercent: form.value.socBeforeChargePercent || null,
      loggedAt: new Date(form.value.loggedAt).toISOString(),
    }
    if (newLatitude.value !== null && newLongitude.value !== null) {
      payload.latitude = newLatitude.value
      payload.longitude = newLongitude.value
    }

    const res = await api.put(`/logs/${props.log.id}`, payload)
    emit('saved', res.data)
    emit('close')
  } catch (e: any) {
    errorMsg.value = e?.response?.data?.message ?? 'Speichern fehlgeschlagen'
  } finally {
    loading.value = false
  }
}
</script>
