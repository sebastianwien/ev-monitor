<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { BoltIcon, ClockIcon, CheckCircleIcon, ExclamationTriangleIcon, XMarkIcon, PencilIcon, CheckIcon, MapPinIcon } from '@heroicons/vue/24/outline'
import goeService, { type GoeConnection } from '@/api/goeService'
import { useWallboxStore } from '@/stores/wallbox'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import { useCarStore } from '@/stores/car'
import type { Car } from '@/api/carService'
import { carLabel } from '@/composables/useCarLabel'
import ToggleSwitch from '../shared/ToggleSwitch.vue'
import { CI_BTN_PRIMARY, CI_BTN_SECONDARY, CI_ICON_BTN, CI_INPUT, CI_CARD, CI_NOTE } from '@/utils/ciClasses'

const { t } = useI18n()
const { formatCostPerKwh } = useLocaleFormat()
const wallboxStore = useWallboxStore()
const carStore = useCarStore()

const props = defineProps<{ connectionId: string; mockConnection?: GoeConnection }>()
const emit = defineEmits<{ disconnect: [id: string] }>()

const actionError = ref<string | null>(null)

const conn = ref<GoeConnection | null>(props.mockConnection ?? null)
const loading = ref(props.mockConnection == null)

const POLL_CHARGING_MS  = 2 * 60 * 1000
const POLL_IDLE_MS      = 4 * 60 * 1000

let pollTimer: ReturnType<typeof setTimeout> | null = null

// ── State config ────────────────────────────────────────────────────────────

const STATE = {
  1: { key: 'state_key_state_ready',   color: 'gray',  pulse: false },
  2: { key: 'state_key_state_charging', color: 'green', pulse: true  },
  3: { key: 'state_key_state_waiting',  color: 'amber', pulse: false },
  4: { key: 'state_key_state_done',     color: 'blue',  pulse: false },
  5: { key: 'state_key_state_error',    color: 'red',   pulse: false },
} as const

const stateConfig = computed(() => {
  const s = conn.value?.carState ?? 1
  return STATE[s as keyof typeof STATE] ?? { key: 'state_unknown', color: 'gray', pulse: false }
})

const isCharging = computed(() => conn.value?.carState === 2)

// ── Colors ───────────────────────────────────────────────────────────────────

// App-CI: Grün nur, solange geladen wird oder fertig ist (Ergebnis), alles andere grau, Rot nirgends
const colorMap = {
  gray:  { ring: 'border-gray-300 dark:border-gray-600', icon: 'text-gray-500 dark:text-gray-400', bg: 'bg-gray-100 dark:bg-gray-800/60', badge: 'bg-white text-gray-700 dark:bg-gray-900 dark:text-gray-300' },
  green: { ring: 'border-green-600 dark:border-green-400', icon: 'text-green-700 dark:text-green-400', bg: 'bg-green-50 dark:bg-green-900/20', badge: 'bg-white text-green-800 dark:bg-gray-900 dark:text-green-300' },
  amber: { ring: 'border-gray-300 dark:border-gray-600', icon: 'text-gray-500 dark:text-gray-400', bg: 'bg-gray-100 dark:bg-gray-800/60', badge: 'bg-white text-gray-700 dark:bg-gray-900 dark:text-gray-300' },
  blue:  { ring: 'border-gray-300 dark:border-gray-600', icon: 'text-green-700 dark:text-green-400', bg: 'bg-gray-100 dark:bg-gray-800/60', badge: 'bg-white text-gray-900 dark:bg-gray-900 dark:text-gray-100' },
  red:   { ring: 'border-gray-900 dark:border-gray-100', icon: 'text-gray-900 dark:text-gray-100', bg: 'bg-gray-100 dark:bg-gray-800/60', badge: 'bg-white font-bold text-gray-900 dark:bg-gray-900 dark:text-gray-100' },
}
// Bearbeiten-Links der Zeilen (Tarif, Standort, Auto)
const EDIT_LINK = 'inline-flex min-h-11 shrink-0 items-center gap-1 text-[13px] font-semibold text-gray-700 underline-offset-2 hover:text-gray-900 hover:underline dark:text-gray-300 dark:hover:text-gray-100'

const colors = computed(() => colorMap[stateConfig.value.color])

// ── Polling ──────────────────────────────────────────────────────────────────

async function fetchStatus() {
  try {
    await wallboxStore.refresh()
    const found = wallboxStore.connections.find(c => c.id === props.connectionId)
    if (found) conn.value = found
  } finally {
    loading.value = false
  }
  schedulePoll()
}

function schedulePoll() {
  if (pollTimer) clearTimeout(pollTimer)
  if (document.hidden) return
  pollTimer = setTimeout(fetchStatus, isCharging.value ? POLL_CHARGING_MS : POLL_IDLE_MS)
}

function onVisibilityChange() {
  if (document.hidden) {
    if (pollTimer) clearTimeout(pollTimer)
  } else {
    fetchStatus()
  }
}

onMounted(() => {
  carStore.getCars().then(data => { cars.value = data }).catch(() => {})
  if (!props.mockConnection) {
    fetchStatus()
    document.addEventListener('visibilitychange', onVisibilityChange)
  }
})

onUnmounted(() => {
  if (pollTimer) clearTimeout(pollTimer)
  if (!props.mockConnection) {
    document.removeEventListener('visibilitychange', onVisibilityChange)
  }
})

// ── Actions ──────────────────────────────────────────────────────────────────

async function handleDisconnect() {
  if (!conn.value) return
  if (!confirm(t('goe.confirm_disconnect_named', { name: conn.value.displayName || conn.value.serial }))) return
  emit('disconnect', conn.value.id)
}

// ── Location editing ──────────────────────────────────────────────────────────

const editingLocation = ref(false)
const locationQuery = ref('')
const locationResults = ref<{ display_name: string; lat: string; lon: string }[]>([])
const locationSearchTimer = ref<ReturnType<typeof setTimeout> | null>(null)
const savingLocation = ref(false)
const selectedGeohash = ref<string | null>(null)
const selectedLocationName = ref<string | null>(null)

function startEditLocation() {
  selectedGeohash.value = conn.value?.geohash ?? null
  selectedLocationName.value = null
  locationQuery.value = ''
  locationResults.value = []
  editingLocation.value = true
}

function selectLocation(result: { display_name: string; lat: string; lon: string }) {
  // 6-char geohash = ~600m precision, matches privacy policy
  const lat = parseFloat(result.lat)
  const lon = parseFloat(result.lon)
  // Manual base32 geohash encoding (6 chars)
  selectedGeohash.value = encodeGeohash(lat, lon, 6)
  selectedLocationName.value = result.display_name
  locationQuery.value = result.display_name
  locationResults.value = []
}

function encodeGeohash(lat: number, lon: number, precision: number): string {
  const BASE32 = '0123456789bcdefghjkmnpqrstuvwxyz'
  let idx = 0, bit = 0, evenBit = true
  let geohash = ''
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

async function saveLocation() {
  if (!conn.value) return
  savingLocation.value = true
  actionError.value = null
  try {
    const updated = await goeService.updateGeohash(conn.value.id, selectedGeohash.value)
    conn.value = { ...conn.value, geohash: updated.geohash }
    editingLocation.value = false
  } catch {
    actionError.value = t('goe.err_save')
  } finally {
    savingLocation.value = false
  }
}

// ── Merge sessions toggle ─────────────────────────────────────────────────────

const savingMergeSessions = ref(false)

async function toggleMergeSessions() {
  if (!conn.value) return
  savingMergeSessions.value = true
  actionError.value = null
  try {
    const updated = await goeService.updateMergeSessions(conn.value.id, !conn.value.mergeSessions)
    conn.value = { ...conn.value, mergeSessions: updated.mergeSessions }
  } catch {
    actionError.value = t('goe.err_save')
  } finally {
    savingMergeSessions.value = false
  }
}

// ── Tariff editing ────────────────────────────────────────────────────────────

const editingTariff = ref(false)
const tariffInput = ref<string>('')
const savingTariff = ref(false)

function startEditTariff() {
  tariffInput.value = (conn.value?.tariffCentsPerKwh ?? 0) > 0 ? String(conn.value!.tariffCentsPerKwh) : ''
  editingTariff.value = true
}

async function saveTariff() {
  if (!conn.value) return
  const n = tariffInput.value === '' ? 0 : parseFloat(tariffInput.value)
  if (isNaN(n) || n < 0 || n > 9999) return
  savingTariff.value = true
  actionError.value = null
  try {
    const updated = await goeService.updateTariff(conn.value.id, n)
    conn.value = { ...conn.value, tariffCentsPerKwh: updated.tariffCentsPerKwh }
    editingTariff.value = false
  } catch {
    actionError.value = t('goe.err_save')
  } finally {
    savingTariff.value = false
  }
}

// ── Car assignment ────────────────────────────────────────────────────────────

const cars = ref<Car[]>([])
const editingCar = ref(false)
const selectedCarId = ref<string | null>(null)
const savingCar = ref(false)

const currentCarLabel = computed(() => {
  if (!conn.value?.carId) return null
  const found = cars.value.find(c => c.id === conn.value!.carId)
  return found ? carLabel(found) : null
})

function startEditCar() {
  selectedCarId.value = conn.value?.carId ?? null
  editingCar.value = true
}

async function saveCar() {
  if (!conn.value || !selectedCarId.value) return
  savingCar.value = true
  actionError.value = null
  try {
    const updated = await goeService.updateCar(conn.value.id, selectedCarId.value)
    conn.value = { ...conn.value, carId: updated.carId }
    editingCar.value = false
  } catch {
    actionError.value = t('goe.err_save')
  } finally {
    savingCar.value = false
  }
}
</script>

<template>
  <!-- Skeleton -->
  <div v-if="loading" :class="[CI_CARD, 'animate-pulse']">
    <div class="mb-4 h-4 w-1/2 rounded bg-gray-200 dark:bg-gray-600" />
    <div class="mb-4 h-20 rounded bg-gray-200 dark:bg-gray-600" />
    <div class="h-3 w-1/3 rounded bg-gray-200 dark:bg-gray-600" />
  </div>

  <div v-else-if="conn" :class="[CI_CARD, 'overflow-hidden p-0']">
    <!-- Header -->
    <div class="flex items-center justify-between gap-2 px-4 pb-3 pt-3">
      <div class="flex min-w-0 items-center gap-2">
        <span class="truncate text-[15px] font-semibold text-gray-900 dark:text-gray-100">
          {{ conn.displayName || t('goe.default_name') }}
        </span>
        <!-- Online dot -->
        <span :class="['inline-block h-2 w-2 shrink-0 rounded-full',
                       conn.active && !conn.lastPollError ? 'bg-green-600 dark:bg-green-400' : 'bg-gray-300 dark:bg-gray-600']"
              :title="conn.active && !conn.lastPollError ? t('goe.title_connected') : t('goe.title_disconnected')" />
      </div>
      <button type="button" :class="CI_ICON_BTN" :title="t('goe.title_disconnect')" :aria-label="t('goe.title_disconnect')" @click="handleDisconnect">
        <XMarkIcon class="h-5 w-5" aria-hidden="true" />
      </button>
    </div>

    <!-- State Visualisation -->
    <div :class="['mx-4 flex flex-col items-center gap-3 rounded-2xl py-6 transition-colors duration-500', colors.bg]">
      <div class="relative flex items-center justify-center">
        <!-- Outer pulse ring (charging only) -->
        <span v-if="stateConfig.pulse"
          :class="['absolute inset-0 animate-ping rounded-full border-2 opacity-40', colors.ring]" />
        <span :class="['flex h-16 w-16 items-center justify-center rounded-full border-2 bg-white dark:bg-gray-900', colors.ring]">
          <BoltIcon          v-if="conn.carState === 2" :class="['h-8 w-8', colors.icon]" aria-hidden="true" />
          <ClockIcon         v-else-if="conn.carState === 3" :class="['h-8 w-8', colors.icon]" aria-hidden="true" />
          <CheckCircleIcon   v-else-if="conn.carState === 4" :class="['h-8 w-8', colors.icon]" aria-hidden="true" />
          <ExclamationTriangleIcon v-else-if="conn.carState === 5" :class="['h-8 w-8', colors.icon]" aria-hidden="true" />
          <!-- State 1: Bereit - custom plug icon -->
          <svg v-else :class="['h-8 w-8', colors.icon]" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" aria-hidden="true">
            <path stroke-linecap="round" stroke-linejoin="round"
              d="M3.75 13.5l10.5-11.25L12 10.5h8.25L9.75 21.75 12 13.5H3.75z" />
          </svg>
        </span>
      </div>

      <span :class="['rounded-full px-3 py-1 text-sm font-semibold', colors.badge]">
        {{ t('goe.' + stateConfig.key) }}
      </span>
    </div>

    <!-- Error details -->
    <p v-if="conn.lastPollError" role="alert" :class="[CI_NOTE, 'mx-4 mt-3 flex items-start gap-2 py-3 text-[13px] text-gray-900 dark:text-gray-100']">
      <ExclamationTriangleIcon class="mt-0.5 h-4 w-4 shrink-0 text-gray-600 dark:text-gray-300" aria-hidden="true" />
      <span v-if="conn.lastPollError.includes('cloud api not enabled')" v-html="t('goe.cloud_api_error')" />
      <span v-else class="font-semibold">{{ conn.lastPollError }}</span>
    </p>

    <!-- Action error -->
    <p v-if="actionError" role="alert" :class="[CI_NOTE, 'mx-4 mt-3 flex items-start gap-2 py-3 text-[13px] font-semibold text-gray-900 dark:text-gray-100']">
      <ExclamationTriangleIcon class="mt-0.5 h-4 w-4 shrink-0 text-gray-600 dark:text-gray-300" aria-hidden="true" />{{ actionError }}
    </p>

    <div class="mt-3 divide-y divide-gray-200 border-t border-gray-200 text-sm dark:divide-white/10 dark:border-white/10">
      <!-- Tariff -->
      <div class="px-4 py-2">
        <div v-if="!editingTariff" class="flex items-center justify-between gap-3">
          <span class="text-gray-500 dark:text-gray-400">
            {{ t('goe.tariff_label') }}
            <span class="font-semibold tabular-nums text-gray-900 dark:text-gray-100">
              {{ conn.tariffCentsPerKwh > 0 ? formatCostPerKwh(Number(conn.tariffCentsPerKwh) / 100) : t('goe.tariff_unset') }}
            </span>
          </span>
          <button type="button" :class="EDIT_LINK" @click="startEditTariff">
            <PencilIcon class="h-4 w-4" aria-hidden="true" />
            {{ t('goe.tariff_edit') }}
          </button>
        </div>
        <div v-else class="flex items-center gap-2 py-1">
          <input
            v-model="tariffInput"
            type="number"
            min="0"
            max="9999"
            step="0.0001"
            :placeholder="t('goe.tariff_placeholder')"
            :aria-label="t('goe.tariff_label')"
            :class="[CI_INPUT, 'flex-1 tabular-nums']"
            @keyup.enter="saveTariff"
            @keyup.escape="editingTariff = false"
          />
          <span class="shrink-0 text-[13px] text-gray-500 dark:text-gray-400">ct/kWh</span>
          <button type="button" :class="CI_ICON_BTN" :disabled="savingTariff" :aria-label="t('goe.location_save')" @click="saveTariff">
            <CheckIcon class="h-5 w-5" aria-hidden="true" />
          </button>
          <button type="button" :class="CI_ICON_BTN" :aria-label="t('goe.location_cancel')" @click="editingTariff = false">
            <XMarkIcon class="h-5 w-5" aria-hidden="true" />
          </button>
        </div>
      </div>

      <!-- Location -->
      <div class="px-4 py-2">
        <div v-if="!editingLocation" class="flex items-center justify-between gap-3">
          <span class="flex items-center gap-1 text-gray-500 dark:text-gray-400">
            <MapPinIcon class="h-4 w-4" aria-hidden="true" />
            {{ t('goe.location_status_label') }}
            <!-- Fehlt der Standort: Amber, die Handlung steht daneben -->
            <span :class="conn.geohash ? 'font-semibold text-gray-900 dark:text-gray-100' : 'font-semibold text-amber-700 dark:text-amber-400'">
              {{ conn.geohash ? t('goe.location_status_set') : t('goe.location_status_missing') }}
            </span>
          </span>
          <button type="button" :class="EDIT_LINK" @click="startEditLocation">
            <PencilIcon class="h-4 w-4" aria-hidden="true" />
            {{ conn.geohash ? t('goe.location_change') : t('goe.location_set_btn') }}
          </button>
        </div>
        <div v-else class="grid gap-2 py-1">
          <div class="relative">
            <input
              v-model="locationQuery"
              type="text"
              :placeholder="t('goe.location_placeholder')"
              :aria-label="t('goe.location_status_label')"
              :class="[CI_INPUT, 'pr-9']"
              @input="onLocationQueryChange"
              @keyup.escape="editingLocation = false"
            />
            <MapPinIcon class="pointer-events-none absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400" aria-hidden="true" />
          </div>
          <ul v-if="locationResults.length" class="overflow-hidden rounded-xl border border-gray-200 bg-white text-sm dark:border-gray-600 dark:bg-gray-900">
            <li v-for="r in locationResults" :key="r.display_name"
              class="cursor-pointer truncate border-b border-gray-200 px-3 py-2.5 text-gray-900 last:border-0 hover:bg-gray-50 dark:border-white/10 dark:text-gray-100 dark:hover:bg-gray-800"
              @click="selectLocation(r)">
              {{ r.display_name }}
            </li>
          </ul>
          <p v-if="selectedLocationName" class="flex items-center gap-1 text-[13px] font-semibold text-gray-900 dark:text-gray-100">
            <CheckIcon class="h-4 w-4 text-green-700 dark:text-green-400" aria-hidden="true" />
            {{ selectedLocationName }}
          </p>
          <div class="flex gap-2">
            <button type="button" :class="[CI_BTN_PRIMARY, 'flex-1']" :disabled="savingLocation || !selectedGeohash" @click="saveLocation">
              {{ savingLocation ? t('goe.location_save_loading') : t('goe.location_save') }}
            </button>
            <button type="button" :class="CI_BTN_SECONDARY" @click="editingLocation = false">
              {{ t('goe.location_cancel') }}
            </button>
          </div>
        </div>
      </div>

      <!-- Merge Sessions -->
      <div class="flex items-center justify-between gap-3 px-4 py-3">
        <div>
          <p class="font-semibold text-gray-900 dark:text-gray-100">{{ t('goe.merge_sessions_title') }}</p>
          <p class="mt-0.5 text-[13px] text-gray-500 dark:text-gray-400">{{ t('goe.merge_sessions_desc') }}</p>
        </div>
        <ToggleSwitch :model-value="conn.mergeSessions" :label="t('goe.merge_sessions_title')" :disabled="savingMergeSessions"
          :class="savingMergeSessions ? 'opacity-50' : ''" @update:model-value="toggleMergeSessions" />
      </div>

      <!-- Car assignment -->
      <div class="px-4 py-2">
        <div v-if="!editingCar" class="flex items-center justify-between gap-3">
          <span class="text-gray-500 dark:text-gray-400">
            {{ t('goe.car_status_label') }}
            <span :class="currentCarLabel ? 'font-semibold text-gray-900 dark:text-gray-100' : 'font-semibold text-amber-700 dark:text-amber-400'">
              {{ currentCarLabel ?? t('goe.car_not_set') }}
            </span>
          </span>
          <button type="button" :class="EDIT_LINK" @click="startEditCar">
            <PencilIcon class="h-4 w-4" aria-hidden="true" />
            {{ t('goe.car_change') }}
          </button>
        </div>
        <div v-else class="flex items-center gap-2 py-1">
          <select
            v-model="selectedCarId"
            :aria-label="t('goe.car_status_label')"
            :class="[CI_INPUT, 'flex-1']"
            @keydown.escape="editingCar = false"
          >
            <option v-for="car in cars" :key="car.id" :value="car.id">{{ carLabel(car) }}</option>
          </select>
          <button type="button" :class="CI_ICON_BTN" :disabled="savingCar || !selectedCarId" :aria-label="t('goe.location_save')" @click="saveCar">
            <CheckIcon class="h-5 w-5" aria-hidden="true" />
          </button>
          <button type="button" :class="CI_ICON_BTN" :aria-label="t('goe.location_cancel')" @click="editingCar = false">
            <XMarkIcon class="h-5 w-5" aria-hidden="true" />
          </button>
        </div>
      </div>

      <!-- Footer -->
      <div class="flex items-center justify-between gap-3 px-4 py-2 text-[12px] text-gray-500 dark:text-gray-400">
        <span>Serial: {{ conn.serial }}</span>
        <span>{{ t('goe.updated_every', { n: isCharging ? 2 : 4 }) }}</span>
      </div>
    </div>
  </div>
</template>
