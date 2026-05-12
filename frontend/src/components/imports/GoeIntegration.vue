<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ExclamationTriangleIcon, InformationCircleIcon, ChevronRightIcon, MapPinIcon, CheckIcon } from '@heroicons/vue/24/outline'
import goeService from '@/api/goeService'
import type { Car } from '@/api/carService'
import { useCarStore } from '@/stores/car'
import GoeStatusCard from './GoeStatusCard.vue'
import { useAuthStore } from '@/stores/auth'
import { useWallboxStore } from '@/stores/wallbox'

const { t } = useI18n()
const authStore = useAuthStore()
const wallboxStore = useWallboxStore()
const carStore = useCarStore()

const cars = ref<Car[]>([])
const loading = ref(false)
const error = ref<string | null>(null)
const showForm = ref(false)

const form = ref({ serial: '', apiKey: '', carId: '', displayName: '' })

// ── Location (manual address search) ─────────────────────────────────────────
const locationQuery = ref('')
const locationResults = ref<{ display_name: string; lat: string; lon: string }[]>([])
const locationSearchTimer = ref<ReturnType<typeof setTimeout> | null>(null)
const selectedGeohash = ref<string | null>(null)
const selectedLocationName = ref<string | null>(null)

function encodeGeohash(lat: number, lon: number, precision: number): string {
  const BASE32 = '0123456789bcdefghjkmnpqrstuvwxyz'
  let idx = 0, bit = 0, evenBit = true, geohash = ''
  let minLat = -90, maxLat = 90, minLon = -180, maxLon = 180
  while (geohash.length < precision) {
    if (evenBit) {
      const mid = (minLon + maxLon) / 2
      if (lon >= mid) { idx = idx * 2 + 1; minLon = mid } else { idx = idx * 2; maxLon = mid }
    } else {
      const mid = (minLat + maxLat) / 2
      if (lat >= mid) { idx = idx * 2 + 1; minLat = mid } else { idx = idx * 2; maxLat = mid }
    }
    evenBit = !evenBit
    if (++bit === 5) { geohash += BASE32[idx]; bit = 0; idx = 0 }
  }
  return geohash
}

function onLocationQueryChange() {
  if (locationSearchTimer.value) clearTimeout(locationSearchTimer.value)
  selectedGeohash.value = null
  selectedLocationName.value = null
  if (locationQuery.value.length < 3) { locationResults.value = []; return }
  locationSearchTimer.value = setTimeout(async () => {
    try {
      const res = await fetch(
        `https://nominatim.openstreetmap.org/search?q=${encodeURIComponent(locationQuery.value)}&format=json&limit=5`
      )
      locationResults.value = await res.json()
    } catch { locationResults.value = [] }
  }, 400)
}

function selectLocation(result: { display_name: string; lat: string; lon: string }) {
  selectedGeohash.value = encodeGeohash(parseFloat(result.lat), parseFloat(result.lon), 5)
  selectedLocationName.value = result.display_name
  locationQuery.value = result.display_name
  locationResults.value = []
}

const infoExpanded = ref(!wallboxStore.hasConnections)
watch(() => wallboxStore.hasConnections, (hasConnections) => {
  if (hasConnections) infoExpanded.value = false
})

onMounted(async () => {
  await loadCars()
})

async function loadCars() {
  try {
    cars.value = await carStore.getCars()
    if (cars.value.length > 0 && !form.value.carId) form.value.carId = cars.value[0].id
  } catch { /* ignore */ }
}


async function handleConnect() {
  if (!form.value.serial || !form.value.apiKey || !form.value.carId) {
    error.value = t('goe.err_required')
    return
  }
  loading.value = true
  error.value = null
  try {
    await goeService.connect(form.value.serial, form.value.apiKey, form.value.carId, form.value.displayName, selectedGeohash.value)
    form.value = { serial: '', apiKey: '', carId: cars.value[0]?.id ?? '', displayName: '' }
    locationQuery.value = ''
    locationResults.value = []
    selectedGeohash.value = null
    selectedLocationName.value = null
    showForm.value = false
    await wallboxStore.refresh()
  } catch (e: any) {
    error.value = e.response?.data?.message || t('goe.err_connect')
  } finally {
    loading.value = false
  }
}

async function handleDisconnect(id: string) {
  if (!confirm(t('goe.confirm_disconnect'))) return
  try {
    await goeService.disconnect(id)
    await wallboxStore.refresh()
  } catch { error.value = t('goe.err_disconnect') }
}

function enumToLabel(value: string | null | undefined): string {
  if (!value) return ''
  return value.replace(/_/g, ' ').toLowerCase()
    .split(' ')
    .map(word => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ')
}

function carDisplayName(brand: string | null | undefined, model: string | null | undefined): string {
  const b = enumToLabel(brand); const m = enumToLabel(model)
  return m.toLowerCase().startsWith(b.toLowerCase()) ? m : `${b} ${m}`.trim()
}

function carLabel(car: Car): string {
  const name = carDisplayName(car.brand, car.model)
  return car.licensePlate ? `${name} · ${car.licensePlate}` : name
}
</script>

<template>
  <div class="space-y-4">
    <div v-if="error" class="border-l-2 border-red-500 bg-red-50 dark:bg-red-950/40 px-4 py-3 rounded-r-sm flex items-start gap-2">
      <ExclamationTriangleIcon class="h-4 w-4 text-red-500 dark:text-red-400 mt-0.5 shrink-0" />
      <p class="text-sm text-red-900 dark:text-red-200 font-medium">{{ error }}</p>
    </div>

    <!-- Info Box -->
    <div class="border-2 border-gray-300 dark:border-gray-700 rounded-sm overflow-hidden bg-white dark:bg-gray-900">
      <button
        type="button"
        @click="infoExpanded = !infoExpanded"
        class="w-full flex items-center justify-between px-3 py-2.5 text-[11px] font-bold uppercase tracking-[0.14em] text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-800 transition text-left">
        <span class="flex items-center gap-1.5">
          <InformationCircleIcon class="w-4 h-4 text-yellow-500 flex-shrink-0" />
          {{ t('goe.how_it_works') }}
        </span>
        <ChevronRightIcon class="w-4 h-4 flex-shrink-0 transition-transform duration-200"
          :class="infoExpanded ? 'rotate-90' : ''" />
      </button>
      <div v-if="infoExpanded" class="px-4 pb-3 text-sm text-gray-600 dark:text-gray-300 space-y-2 border-t-2 border-gray-300 dark:border-gray-700 pt-3 font-medium leading-relaxed">
        <p v-html="t('goe.info_auto')" />
        <p v-html="t('goe.info_session')" />
        <p v-html="t('goe.info_tariff')" />
      </div>
    </div>

    <!-- Connected devices -->
    <div v-if="wallboxStore.hasConnections" class="space-y-3">
      <p class="text-[11px] font-bold uppercase tracking-[0.14em] text-gray-500 dark:text-gray-400">{{ t('goe.connected_devices') }}</p>
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
      <div v-if="showForm" class="border-2 border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-900 rounded-sm shadow-[4px_4px_0_0_#d1d5db] dark:shadow-[4px_4px_0_0_#374151] p-4 space-y-3">
        <p class="text-[11px] font-bold uppercase tracking-[0.14em] text-gray-700 dark:text-gray-300">{{ t('goe.form_title') }}</p>
        <div>
          <label class="block text-[11px] font-bold uppercase tracking-wider text-gray-500 dark:text-gray-400 mb-1.5">{{ t('goe.serial_label') }}</label>
          <input v-model="form.serial" type="text" :placeholder="t('goe.serial_placeholder')" class="w-full border-2 border-gray-300 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100 rounded-sm px-3 py-2.5 text-sm font-medium focus:outline-none focus:border-yellow-500 transition-colors" />
        </div>
        <div>
          <label class="block text-[11px] font-bold uppercase tracking-wider text-gray-500 dark:text-gray-400 mb-1.5">{{ t('goe.apikey_label') }}</label>
          <input v-model="form.apiKey" type="password" :placeholder="t('goe.apikey_placeholder')" class="w-full border-2 border-gray-300 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100 rounded-sm px-3 py-2.5 text-sm font-medium focus:outline-none focus:border-yellow-500 transition-colors" />
        </div>
        <div v-if="cars.length > 0">
          <label class="block text-[11px] font-bold uppercase tracking-wider text-gray-500 dark:text-gray-400 mb-1.5">{{ t('goe.car_label') }}</label>
          <select v-model="form.carId" class="w-full border-2 border-gray-300 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100 rounded-sm px-3 py-2.5 text-sm font-medium focus:outline-none focus:border-yellow-500 transition-colors">
            <option v-for="car in cars" :key="car.id" :value="car.id">
              {{ carLabel(car) }}
            </option>
          </select>
        </div>
        <div>
          <label class="block text-[11px] font-bold uppercase tracking-wider text-gray-500 dark:text-gray-400 mb-1.5">{{ t('goe.name_label') }}</label>
          <input v-model="form.displayName" type="text" :placeholder="t('goe.name_placeholder')" class="w-full border-2 border-gray-300 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100 rounded-sm px-3 py-2.5 text-sm font-medium focus:outline-none focus:border-yellow-500 transition-colors" />
        </div>

        <!-- Location (optional) -->
        <div class="space-y-1.5">
          <label class="block text-[11px] font-bold uppercase tracking-wider text-gray-500 dark:text-gray-400">{{ t('goe.location_label') }}</label>
          <div class="relative">
            <input
              v-model="locationQuery"
              type="text"
              :placeholder="t('goe.location_placeholder')"
              class="w-full border-2 border-gray-300 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100 rounded-sm px-3 py-2.5 pr-8 text-sm font-medium focus:outline-none focus:border-yellow-500 transition-colors"
              @input="onLocationQueryChange"
            />
            <MapPinIcon class="absolute right-2.5 top-3 h-4 w-4 text-gray-400 pointer-events-none" />
          </div>
          <ul v-if="locationResults.length" class="border-2 border-gray-300 dark:border-gray-700 rounded-sm overflow-hidden text-xs bg-white dark:bg-gray-900">
            <li v-for="r in locationResults" :key="r.display_name"
              class="px-3 py-2 hover:bg-gray-50 dark:hover:bg-gray-800 cursor-pointer truncate border-b border-gray-200 dark:border-gray-700 last:border-0 font-medium"
              @click="selectLocation(r)">
              {{ r.display_name }}
            </li>
          </ul>
          <p v-if="selectedLocationName" class="text-[11px] font-bold uppercase tracking-wider text-emerald-700 dark:text-emerald-400 flex items-center gap-1">
            <CheckIcon class="h-3.5 w-3.5 flex-shrink-0" />
            {{ selectedLocationName }}
          </p>
        </div>

        <div class="flex flex-col sm:flex-row gap-2">
          <button @click="handleConnect" :disabled="loading"
                  class="flex-1 inline-flex items-center justify-center bg-yellow-400 hover:bg-yellow-300 disabled:bg-gray-300 dark:disabled:bg-gray-700 disabled:text-gray-500 text-gray-950 font-bold uppercase tracking-wider text-xs px-5 py-3 rounded-sm border-2 border-yellow-400 disabled:border-gray-300 dark:disabled:border-gray-700 shadow-[3px_3px_0_0_#030712] disabled:shadow-none active:translate-x-[3px] active:translate-y-[3px] active:shadow-none transition-[transform,box-shadow] duration-75">
            {{ loading ? t('goe.connect_btn_loading') : t('goe.connect_btn') }}
          </button>
          <button @click="showForm = false"
                  class="inline-flex items-center justify-center bg-white dark:bg-gray-900 border-2 border-gray-300 dark:border-gray-700 text-gray-800 dark:text-gray-200 font-bold uppercase tracking-wider text-xs px-5 py-3 rounded-sm shadow-[3px_3px_0_0_#9ca3af] dark:shadow-[3px_3px_0_0_#4b5563] active:translate-x-[3px] active:translate-y-[3px] active:shadow-none transition-[transform,box-shadow] duration-75">
            {{ t('goe.cancel_btn') }}
          </button>
        </div>
      </div>

      <button v-if="!showForm" @click="showForm = true"
              class="w-full sm:w-auto flex sm:inline-flex items-center justify-center gap-2 bg-yellow-400 hover:bg-yellow-300 text-gray-950 font-bold uppercase tracking-wider text-xs px-5 py-3 rounded-sm border-2 border-yellow-400 shadow-[3px_3px_0_0_#030712] active:translate-x-[3px] active:translate-y-[3px] active:shadow-none transition-[transform,box-shadow] duration-75">
        {{ t('goe.add_btn') }}
      </button>
    </template>
  </div>
</template>
