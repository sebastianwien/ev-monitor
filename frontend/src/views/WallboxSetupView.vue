<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '../stores/auth'
import type { Car } from '../api/carService'
import { useCarStore } from '../stores/car'
import { wallboxService, type WallboxConnection } from '../api/wallboxService'
import {
  BoltIcon,
  PlusIcon,
  TrashIcon,
  CheckCircleIcon,
  ExclamationTriangleIcon,
  ClipboardDocumentIcon,
  MapPinIcon,
  PencilIcon,
  CheckIcon,
  XMarkIcon,
  EyeIcon,
  EyeSlashIcon,
  KeyIcon
} from '@heroicons/vue/24/outline'
import { useLocaleFormat } from '../composables/useLocaleFormat'

const { t } = useI18n()
const { formatCostPerKwh } = useLocaleFormat()
const authStore = useAuthStore()
const carStore = useCarStore()
const userId = computed(() => authStore.user?.userId || '')

const connections = ref<WallboxConnection[]>([])
const cars = ref<Car[]>([])
const loading = ref(true)
const saving = ref(false)
const error = ref<string | null>(null)
const success = ref<string | null>(null)

// ── Add form ───────────────────────────────────────────────────────────────────
const showForm = ref(false)
const formChargePointId = ref('')
const formCarId = ref('')
const formDisplayName = ref('')

// OCPP WebSocket URL
const ocppUrl = computed(() => {
  const base = import.meta.env.VITE_API_BASE_URL?.replace('/api', '') || window.location.origin
  return `${base.replace('https://', 'wss://').replace('http://', 'ws://')}/ocpp/ws/${formChargePointId.value || '<deine-chargepoint-id>'}`
})

// ── Location search (shared helper) ───────────────────────────────────────────
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

async function searchLocation(query: string): Promise<{ display_name: string; lat: string; lon: string }[]> {
  if (query.length < 3) return []
  try {
    const res = await fetch(
      `https://nominatim.openstreetmap.org/search?q=${encodeURIComponent(query)}&format=json&limit=5`
    )
    return await res.json()
  } catch { return [] }
}

// ── Per-connection inline edit state ──────────────────────────────────────────
interface EditState {
  editingLocation: boolean
  locationQuery: string
  locationResults: { display_name: string; lat: string; lon: string }[]
  locationTimer: ReturnType<typeof setTimeout> | null
  pendingGeohash: string | null
  pendingLocationName: string | null
  editingTariff: boolean
  tariffInput: string
  passwordVisible: boolean
  saving: boolean
}

const editStates = ref<Record<string, EditState>>({})

function getEditState(id: string): EditState {
  if (!editStates.value[id]) {
    editStates.value[id] = {
      editingLocation: false,
      locationQuery: '',
      locationResults: [],
      locationTimer: null,
      pendingGeohash: null,
      pendingLocationName: null,
      editingTariff: false,
      tariffInput: '',
      passwordVisible: false,
      saving: false
    }
  }
  return editStates.value[id]
}

function startEditLocation(conn: WallboxConnection) {
  const s = getEditState(conn.id)
  s.editingLocation = true
  s.locationQuery = ''
  s.pendingGeohash = conn.geohash
  s.pendingLocationName = null
}

function onLocationQueryChange(id: string) {
  const s = getEditState(id)
  if (s.locationTimer) clearTimeout(s.locationTimer)
  s.pendingGeohash = null
  s.pendingLocationName = null
  if (s.locationQuery.length < 3) { s.locationResults = []; return }
  s.locationTimer = setTimeout(async () => {
    s.locationResults = await searchLocation(s.locationQuery)
  }, 400)
}

function selectLocation(id: string, result: { display_name: string; lat: string; lon: string }) {
  const s = getEditState(id)
  s.pendingGeohash = encodeGeohash(parseFloat(result.lat), parseFloat(result.lon), 5)
  s.pendingLocationName = result.display_name
  s.locationQuery = result.display_name
  s.locationResults = []
}

function clearLocation(id: string) {
  const s = getEditState(id)
  s.pendingGeohash = null
  s.pendingLocationName = null
  s.locationQuery = ''
  s.locationResults = []
}

async function saveLocation(conn: WallboxConnection) {
  const s = getEditState(conn.id)
  s.saving = true
  try {
    const updated = await wallboxService.updateSettings(conn.id, userId.value, {
      geohash: s.pendingGeohash,
      tariffCentsPerKwh: conn.tariffCentsPerKwh
    })
    const idx = connections.value.findIndex(c => c.id === conn.id)
    if (idx !== -1) connections.value[idx] = updated
    s.editingLocation = false
  } catch {
    error.value = t('wallbox.err_location')
  } finally {
    s.saving = false
  }
}

function startEditTariff(conn: WallboxConnection) {
  const s = getEditState(conn.id)
  s.tariffInput = conn.tariffCentsPerKwh > 0 ? String(conn.tariffCentsPerKwh) : ''
  s.editingTariff = true
}

async function saveTariff(conn: WallboxConnection) {
  const s = getEditState(conn.id)
  const n = s.tariffInput === '' ? 0 : parseFloat(s.tariffInput)
  if (isNaN(n) || n < 0 || n > 9999) return
  s.saving = true
  try {
    const updated = await wallboxService.updateSettings(conn.id, userId.value, {
      geohash: conn.geohash,
      tariffCentsPerKwh: n
    })
    const idx = connections.value.findIndex(c => c.id === conn.id)
    if (idx !== -1) connections.value[idx] = updated
    s.editingTariff = false
  } catch {
    error.value = t('wallbox.err_tariff')
  } finally {
    s.saving = false
  }
}

// ── Load / Save / Delete ──────────────────────────────────────────────────────
const load = async () => {
  loading.value = true
  error.value = null
  try {
    const [conns, carList] = await Promise.all([
      wallboxService.getConnections(userId.value),
      carStore.getCars()
    ])
    connections.value = conns
    cars.value = carList
  } catch {
    error.value = t('wallbox.err_load')
  } finally {
    loading.value = false
  }
}

const save = async () => {
  if (!formChargePointId.value.trim()) {
    error.value = t('wallbox.err_empty_id')
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
    success.value = t('wallbox.success_registered')
  } catch (e: any) {
    if (e?.response?.status === 409) {
      error.value = t('wallbox.err_duplicate')
    } else {
      error.value = t('wallbox.err_save')
    }
  } finally {
    saving.value = false
  }
}

const remove = async (id: string) => {
  try {
    await wallboxService.deleteConnection(id, userId.value)
    connections.value = connections.value.filter(c => c.id !== id)
    success.value = t('wallbox.success_removed')
  } catch {
    error.value = t('wallbox.err_delete')
  }
}

const enumToLabel = (value: string | null | undefined): string => {
  if (!value) return ''
  return value.replace(/_/g, ' ').toLowerCase()
    .split(' ')
    .map(w => w.charAt(0).toUpperCase() + w.slice(1))
    .join(' ')
}

const carLabel = (carId: string | null) => {
  if (!carId) return null
  const car = cars.value.find(c => c.id === carId)
  if (!car) return carId
  const name = `${enumToLabel(car.brand)} ${enumToLabel(car.model)}`
  return car.licensePlate ? `${name} · ${car.licensePlate}` : name
}

const copyUrl = (url: string) => {
  navigator.clipboard.writeText(url)
  success.value = t('wallbox.success_url_copied')
}

onMounted(load)
</script>

<template>
  <div class="max-w-2xl mx-auto px-4 py-8">
    <!-- Header -->
    <div class="mb-8">
      <div class="flex items-center gap-3 mb-2">
        <BoltIcon class="h-7 w-7 text-green-600" />
        <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">{{ t('wallbox.title') }}</h1>
      </div>
      <p class="text-gray-600 dark:text-gray-400">{{ t('wallbox.subtitle') }}</p>
    </div>

    <!-- Beta Disclaimer -->
    <div class="flex items-start gap-3 bg-blue-50 dark:bg-blue-900/30 border border-blue-200 dark:border-blue-700 rounded-xl p-4 mb-8">
      <ExclamationTriangleIcon class="h-5 w-5 text-blue-500 dark:text-blue-400 mt-0.5 shrink-0" />
      <div class="text-sm text-blue-800 dark:text-blue-200">
        <p class="font-semibold mb-1">{{ t('wallbox.beta_title') }}</p>
        <p>{{ t('wallbox.beta_desc') }}</p>
      </div>
    </div>

    <!-- Alerts -->
    <div v-if="success" class="flex items-start gap-2 bg-green-50 dark:bg-green-900/30 border border-green-200 dark:border-green-700 rounded-lg p-4 mb-6">
      <CheckCircleIcon class="h-5 w-5 text-green-600 dark:text-green-400 mt-0.5 shrink-0" />
      <p class="text-sm text-green-800 dark:text-green-200">{{ success }}</p>
    </div>
    <div v-if="error" class="flex items-start gap-2 bg-red-50 dark:bg-red-900/30 border border-red-200 dark:border-red-700 rounded-lg p-4 mb-6">
      <ExclamationTriangleIcon class="h-5 w-5 text-red-500 dark:text-red-400 mt-0.5 shrink-0" />
      <p class="text-sm text-red-800 dark:text-red-200">{{ error }}</p>
    </div>

    <!-- Loading -->
    <div v-if="loading" class="text-center text-gray-500 dark:text-gray-400 py-12">{{ t('wallbox.loading') }}</div>

    <template v-else>
      <!-- Existing Connections -->
      <div v-if="connections.length > 0" class="mb-8 space-y-3">
        <h2 class="text-sm font-semibold text-gray-700 dark:text-gray-300 uppercase tracking-wide">{{ t('wallbox.connections_title') }}</h2>
        <div
          v-for="conn in connections"
          :key="conn.id"
          class="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl overflow-hidden"
        >
          <!-- Connection header -->
          <div class="p-4 flex items-start justify-between gap-4">
            <div class="min-w-0">
              <div class="flex items-center gap-2">
                <BoltIcon class="h-4 w-4 text-green-600 shrink-0" />
                <span class="font-medium text-gray-900 dark:text-gray-100 truncate">
                  {{ conn.displayName || conn.ocppChargePointId }}
                </span>
                <span class="text-xs bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-400 px-2 py-0.5 rounded-full">{{ t('wallbox.status_active') }}</span>
              </div>
              <p class="text-xs text-gray-500 dark:text-gray-400 mt-1 font-mono">{{ conn.ocppChargePointId }}</p>
              <p v-if="carLabel(conn.carId)" class="text-xs text-gray-500 dark:text-gray-400 mt-0.5">
                {{ carLabel(conn.carId) }}
              </p>
            </div>
            <button
              @click="remove(conn.id)"
              class="text-gray-400 dark:text-gray-500 hover:text-red-500 transition shrink-0"
              :title="t('wallbox.remove_title')"
            >
              <TrashIcon class="h-4 w-4" />
            </button>
          </div>

          <!-- Location row -->
          <div class="border-t border-gray-100 dark:border-gray-700 px-4 py-3">
            <div v-if="!getEditState(conn.id).editingLocation" class="flex items-center justify-between">
              <span class="text-xs text-gray-500 dark:text-gray-400 flex items-center gap-1">
                <MapPinIcon class="h-3.5 w-3.5" />
                {{ t('wallbox.location_label') }}
                <span class="font-medium text-gray-700 dark:text-gray-300">
                  {{ conn.geohash ? t('wallbox.location_set', { hash: conn.geohash }) : t('wallbox.location_unset') }}
                </span>
              </span>
              <button @click="startEditLocation(conn)"
                class="flex items-center gap-1 text-xs text-green-600 hover:text-green-700 font-medium">
                <PencilIcon class="h-3.5 w-3.5" />
                {{ t('wallbox.location_edit') }}
              </button>
            </div>
            <div v-else class="space-y-2">
              <div class="relative">
                <input
                  v-model="getEditState(conn.id).locationQuery"
                  @input="onLocationQueryChange(conn.id)"
                  type="text"
                  :placeholder="t('wallbox.location_placeholder')"
                  class="w-full border border-gray-300 dark:border-gray-600 rounded-lg px-3 py-1.5 text-sm bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-green-500"
                />
                <ul v-if="getEditState(conn.id).locationResults.length > 0"
                  class="absolute z-10 w-full bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg shadow-lg mt-1 max-h-48 overflow-auto">
                  <li v-for="result in getEditState(conn.id).locationResults" :key="result.lat + result.lon"
                    @click="selectLocation(conn.id, result)"
                    class="px-3 py-2 text-sm text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 cursor-pointer truncate">
                    {{ result.display_name }}
                  </li>
                </ul>
              </div>
              <p v-if="getEditState(conn.id).pendingGeohash" class="text-xs text-green-600">
                Geohash: {{ getEditState(conn.id).pendingGeohash }}
              </p>
              <div class="flex items-center gap-2">
                <button @click="saveLocation(conn)" :disabled="getEditState(conn.id).saving"
                  class="flex items-center gap-1 text-xs bg-green-600 text-white px-3 py-1.5 rounded-lg hover:bg-green-700 disabled:opacity-50">
                  <CheckIcon class="h-3.5 w-3.5" />
                  {{ t('wallbox.location_save') }}
                </button>
                <button @click="clearLocation(conn.id); getEditState(conn.id).editingLocation = false"
                  class="text-xs text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-200 px-2 py-1.5">
                  {{ t('wallbox.location_cancel') }}
                </button>
              </div>
            </div>
          </div>

          <!-- Tariff row -->
          <div class="border-t border-gray-100 dark:border-gray-700 px-4 py-3">
            <div v-if="!getEditState(conn.id).editingTariff" class="flex items-center justify-between">
              <span class="text-xs text-gray-500 dark:text-gray-400">
                {{ t('wallbox.tariff_label') }}
                <span class="font-medium text-gray-700 dark:text-gray-300">
                  {{ conn.tariffCentsPerKwh > 0
                    ? formatCostPerKwh(Number(conn.tariffCentsPerKwh) / 100)
                    : t('wallbox.tariff_unset') }}
                </span>
              </span>
              <button @click="startEditTariff(conn)"
                class="flex items-center gap-1 text-xs text-green-600 hover:text-green-700 font-medium">
                <PencilIcon class="h-3.5 w-3.5" />
                {{ t('wallbox.tariff_edit') }}
              </button>
            </div>
            <div v-else class="flex items-center gap-2">
              <input
                v-model="getEditState(conn.id).tariffInput"
                type="number"
                min="0"
                max="9999"
                step="0.0001"
                :placeholder="t('wallbox.tariff_placeholder')"
                class="flex-1 border border-gray-300 dark:border-gray-600 rounded-lg px-2 py-1 text-sm bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-green-500"
                @keyup.enter="saveTariff(conn)"
                @keyup.escape="getEditState(conn.id).editingTariff = false"
              />
              <span class="text-xs text-gray-500 dark:text-gray-400 shrink-0">ct/kWh</span>
              <button @click="saveTariff(conn)" :disabled="getEditState(conn.id).saving"
                class="p-1 text-green-600 hover:text-green-700 disabled:opacity-50">
                <CheckIcon class="h-4 w-4" />
              </button>
              <button @click="getEditState(conn.id).editingTariff = false"
                class="p-1 text-gray-400 dark:text-gray-500 hover:text-gray-600 dark:hover:text-gray-300">
                <XMarkIcon class="h-4 w-4" />
              </button>
            </div>
          </div>

          <!-- OCPP credentials row -->
          <div class="border-t border-gray-100 dark:border-gray-700 px-4 py-3 bg-gray-50 dark:bg-gray-700/50">
            <p class="text-xs font-semibold text-gray-600 dark:text-gray-300 flex items-center gap-1 mb-2">
              <KeyIcon class="h-3.5 w-3.5" />
              {{ t('wallbox.ocpp_credentials_title') }}
            </p>
            <div class="space-y-1.5">
              <div class="flex items-center gap-2">
                <span class="text-xs text-gray-500 dark:text-gray-400 w-16 shrink-0">{{ t('wallbox.username_label') }}</span>
                <code class="text-xs text-gray-800 dark:text-gray-100 bg-white dark:bg-gray-700 border border-gray-200 dark:border-gray-600 rounded px-2 py-0.5 flex-1 truncate">{{ conn.ocppChargePointId }}</code>
                <button @click="copyUrl(conn.ocppChargePointId)" class="text-gray-400 dark:text-gray-500 hover:text-green-600 shrink-0" :title="t('wallbox.copy_title')">
                  <ClipboardDocumentIcon class="h-3.5 w-3.5" />
                </button>
              </div>
              <div class="flex items-center gap-2">
                <span class="text-xs text-gray-500 dark:text-gray-400 w-16 shrink-0">{{ t('wallbox.password_label') }}</span>
                <code class="text-xs text-gray-800 dark:text-gray-100 bg-white dark:bg-gray-700 border border-gray-200 dark:border-gray-600 rounded px-2 py-0.5 flex-1 truncate">
                  {{ getEditState(conn.id).passwordVisible ? conn.ocppPassword : '••••••••••••••••' }}
                </code>
                <button @click="getEditState(conn.id).passwordVisible = !getEditState(conn.id).passwordVisible"
                  class="text-gray-400 dark:text-gray-500 hover:text-gray-600 dark:hover:text-gray-300 shrink-0" :title="t('wallbox.show_hide_title')">
                  <EyeSlashIcon v-if="getEditState(conn.id).passwordVisible" class="h-3.5 w-3.5" />
                  <EyeIcon v-else class="h-3.5 w-3.5" />
                </button>
                <button @click="copyUrl(conn.ocppPassword)" class="text-gray-400 dark:text-gray-500 hover:text-green-600 shrink-0" :title="t('wallbox.copy_title')">
                  <ClipboardDocumentIcon class="h-3.5 w-3.5" />
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Add New Connection -->
      <div v-if="!showForm" class="mb-8">
        <button
          @click="showForm = true; success = null; error = null"
          class="flex items-center gap-2 bg-green-600 text-white px-5 py-2.5 rounded-lg font-medium hover:bg-green-700 transition"
        >
          <PlusIcon class="h-5 w-5" />
          {{ t('wallbox.add_btn') }}
        </button>
      </div>

      <div v-else class="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl p-6 mb-8 space-y-5">
        <h2 class="font-semibold text-gray-900 dark:text-gray-100">{{ t('wallbox.new_wallbox_title') }}</h2>

        <!-- ChargePoint ID -->
        <div>
          <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
            {{ t('wallbox.chargepointid_label') }} <span class="text-red-500">*</span>
          </label>
          <input
            v-model="formChargePointId"
            type="text"
            :placeholder="t('wallbox.chargepointid_placeholder')"
            class="w-full border border-gray-300 dark:border-gray-600 rounded-lg px-3 py-2 text-sm bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-green-500"
          />
          <p class="text-xs text-gray-500 dark:text-gray-400 mt-1">
            {{ t('wallbox.chargepointid_hint') }}
          </p>
        </div>

        <!-- Display Name -->
        <div>
          <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">{{ t('wallbox.name_label') }}</label>
          <input
            v-model="formDisplayName"
            type="text"
            :placeholder="t('wallbox.name_placeholder')"
            class="w-full border border-gray-300 dark:border-gray-600 rounded-lg px-3 py-2 text-sm bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-green-500"
          />
        </div>

        <!-- Car Selection -->
        <div>
          <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">{{ t('wallbox.car_label') }}</label>
          <select
            v-model="formCarId"
            class="w-full border border-gray-300 dark:border-gray-600 rounded-lg px-3 py-2 text-sm bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-green-500"
          >
            <option value="">{{ t('wallbox.car_none') }}</option>
            <option v-for="car in cars" :key="car.id" :value="car.id">
              {{ carLabel(car.id) }}
            </option>
          </select>
        </div>

        <!-- OCPP URL Preview -->
        <div v-if="formChargePointId.trim()" class="bg-gray-50 dark:bg-gray-700/50 border border-gray-200 dark:border-gray-600 rounded-lg p-4">
          <p class="text-xs font-semibold text-gray-700 dark:text-gray-300 mb-2 uppercase tracking-wide">
            {{ t('wallbox.ocpp_url_label') }}
          </p>
          <div class="flex items-center gap-2">
            <code class="text-xs text-gray-800 dark:text-gray-100 break-all flex-1">{{ ocppUrl }}</code>
            <button
              @click="copyUrl(ocppUrl)"
              class="text-gray-400 hover:text-green-600 transition shrink-0"
              :title="t('wallbox.copy_title')"
            >
              <ClipboardDocumentIcon class="h-4 w-4" />
            </button>
          </div>
          <p class="text-xs text-gray-500 dark:text-gray-400 mt-2">
            {{ t('wallbox.ocpp_url_goe_hint') }}
          </p>
        </div>

        <!-- Buttons -->
        <div class="flex gap-3 pt-2">
          <button
            @click="save"
            :disabled="saving"
            class="bg-green-600 text-white px-5 py-2 rounded-lg text-sm font-medium hover:bg-green-700 transition disabled:opacity-50"
          >
            {{ saving ? t('wallbox.save_btn_loading') : t('wallbox.save_btn') }}
          </button>
          <button
            @click="showForm = false; error = null"
            class="text-gray-600 dark:text-gray-300 px-5 py-2 rounded-lg text-sm font-medium hover:bg-gray-100 dark:hover:bg-gray-700 transition"
          >
            {{ t('wallbox.cancel_btn') }}
          </button>
        </div>
      </div>
    </template>
  </div>
</template>
