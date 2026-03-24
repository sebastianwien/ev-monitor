<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { BoltIcon, ClockIcon, CheckCircleIcon, ExclamationTriangleIcon, XMarkIcon, PencilIcon, CheckIcon, MapPinIcon } from '@heroicons/vue/24/outline'
import goeService, { type GoeConnection } from '@/api/goeService'
import { useWallboxStore } from '@/stores/wallbox'

const { t } = useI18n()
const wallboxStore = useWallboxStore()

const props = defineProps<{ connectionId: string; mockConnection?: GoeConnection }>()
const emit = defineEmits<{ disconnect: [id: string] }>()

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
const hasError   = computed(() => conn.value?.carState === 5 || !!conn.value?.lastPollError)

// ── Colors ───────────────────────────────────────────────────────────────────

const colorMap = {
  gray:  { ring: 'border-gray-200',  icon: 'text-gray-400',  bg: 'bg-gray-50 dark:bg-gray-700',                      badge: 'bg-gray-100 text-gray-600'                        },
  green: { ring: 'border-green-400', icon: 'text-green-500', bg: 'bg-green-50 dark:bg-green-900/30',                  badge: 'bg-green-100 text-green-700 dark:text-green-300'  },
  amber: { ring: 'border-amber-400', icon: 'text-amber-500', bg: 'bg-amber-50 dark:bg-amber-900/30',                  badge: 'bg-amber-100 text-amber-700 dark:text-amber-300'  },
  blue:  { ring: 'border-blue-400',  icon: 'text-blue-500',  bg: 'bg-blue-50 dark:bg-blue-900/30',                    badge: 'bg-blue-100 text-blue-700 dark:text-blue-300'     },
  red:   { ring: 'border-red-400',   icon: 'text-red-500',   bg: 'bg-red-50 dark:bg-red-900/30',                      badge: 'bg-red-100 text-red-700 dark:text-red-300'        },
}

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
  // 5-char geohash = ~5km precision, matches privacy policy
  const lat = parseFloat(result.lat)
  const lon = parseFloat(result.lon)
  // Manual base32 geohash encoding (5 chars)
  selectedGeohash.value = encodeGeohash(lat, lon, 5)
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
  try {
    const updated = await goeService.updateGeohash(conn.value.id, selectedGeohash.value)
    conn.value = { ...conn.value, geohash: updated.geohash }
    editingLocation.value = false
  } finally {
    savingLocation.value = false
  }
}

// ── Merge sessions toggle ─────────────────────────────────────────────────────

const savingMergeSessions = ref(false)

async function toggleMergeSessions() {
  if (!conn.value) return
  savingMergeSessions.value = true
  try {
    const updated = await goeService.updateMergeSessions(conn.value.id, !conn.value.mergeSessions)
    conn.value = { ...conn.value, mergeSessions: updated.mergeSessions }
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
  try {
    const updated = await goeService.updateTariff(conn.value.id, n)
    conn.value = { ...conn.value, tariffCentsPerKwh: updated.tariffCentsPerKwh }
    editingTariff.value = false
  } finally {
    savingTariff.value = false
  }
}
</script>

<template>
  <!-- Skeleton -->
  <div v-if="loading" class="rounded-2xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 p-5 animate-pulse">
    <div class="h-4 bg-gray-100 dark:bg-gray-700 rounded w-1/2 mb-4" />
    <div class="h-20 bg-gray-100 dark:bg-gray-700 rounded mb-4" />
    <div class="h-3 bg-gray-100 dark:bg-gray-700 rounded w-1/3" />
  </div>

  <div v-else-if="conn"
    :class="['rounded-2xl border bg-white dark:bg-gray-800 overflow-hidden shadow-sm transition-all duration-300',
             hasError ? 'border-red-200 dark:border-red-700' : 'border-gray-200 dark:border-gray-700']"
  >
    <!-- Header -->
    <div class="flex items-center justify-between px-5 pt-4 pb-3">
      <div class="flex items-center gap-2 min-w-0">
        <span class="text-sm font-semibold text-gray-900 dark:text-gray-100 truncate">
          {{ conn.displayName || t('goe.default_name') }}
        </span>
        <!-- Online dot -->
        <span :class="['inline-block w-2 h-2 rounded-full flex-shrink-0',
                       conn.active && !conn.lastPollError ? 'bg-green-400' : 'bg-gray-300']"
              :title="conn.active && !conn.lastPollError ? t('goe.title_connected') : t('goe.title_disconnected')" />
      </div>
      <button @click="handleDisconnect"
        class="text-gray-300 hover:text-red-400 transition p-1 rounded-md hover:bg-red-50 flex-shrink-0"
        :title="t('goe.title_disconnect')">
        <XMarkIcon class="h-4 w-4" />
      </button>
    </div>

    <!-- State Visualisation -->
    <div :class="['mx-4 rounded-xl py-6 flex flex-col items-center gap-3 transition-colors duration-500', colors.bg]">
      <!-- Icon with optional pulse ring -->
      <div class="relative flex items-center justify-center">
        <!-- Outer pulse ring (charging only) -->
        <span v-if="stateConfig.pulse"
          :class="['absolute inset-0 rounded-full border-2 animate-ping opacity-40', colors.ring]" />
        <!-- Inner ring -->
        <span :class="['flex items-center justify-center w-16 h-16 rounded-full border-2 bg-white dark:bg-gray-800', colors.ring]">
          <BoltIcon          v-if="conn.carState === 2" :class="['h-8 w-8', colors.icon]" />
          <ClockIcon         v-else-if="conn.carState === 3" :class="['h-8 w-8', colors.icon]" />
          <CheckCircleIcon   v-else-if="conn.carState === 4" :class="['h-8 w-8', colors.icon]" />
          <ExclamationTriangleIcon v-else-if="conn.carState === 5" :class="['h-8 w-8', colors.icon]" />
          <!-- State 1: Bereit — custom plug icon -->
          <svg v-else :class="['h-8 w-8', colors.icon]" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round"
              d="M3.75 13.5l10.5-11.25L12 10.5h8.25L9.75 21.75 12 13.5H3.75z" />
          </svg>
        </span>
      </div>

      <!-- State label -->
      <span :class="['text-sm font-semibold px-3 py-1 rounded-full', colors.badge]">
        {{ t('goe.' + stateConfig.key) }}
      </span>
    </div>

    <!-- Error details -->
    <div v-if="conn.lastPollError"
      class="mx-4 mt-3 flex items-start gap-2 bg-red-50 dark:bg-red-900/30 border border-red-200 dark:border-red-700 rounded-lg px-3 py-2">
      <ExclamationTriangleIcon class="h-4 w-4 text-red-500 dark:text-red-400 mt-0.5 shrink-0" />
      <p class="text-xs text-red-700 dark:text-red-300 leading-snug">
        <span v-if="conn.lastPollError.includes('cloud api not enabled')" v-html="t('goe.cloud_api_error')" />
        <span v-else>{{ conn.lastPollError }}</span>
      </p>
    </div>

    <!-- Tariff -->
    <div class="px-5 py-3 border-t border-gray-100 dark:border-gray-700">
      <div v-if="!editingTariff" class="flex items-center justify-between">
        <span class="text-xs text-gray-500 dark:text-gray-400">
          {{ t('goe.tariff_label') }}
          <span class="font-medium text-gray-700 dark:text-gray-300">
            {{ conn.tariffCentsPerKwh > 0 ? `${Number(conn.tariffCentsPerKwh).toLocaleString(undefined, { maximumFractionDigits: 4 })} ct/kWh` : t('goe.tariff_unset') }}
          </span>
        </span>
        <button @click="startEditTariff"
          class="flex items-center gap-1 text-xs text-green-600 hover:text-green-700 font-medium">
          <PencilIcon class="h-3.5 w-3.5" />
          {{ t('goe.tariff_edit') }}
        </button>
      </div>
      <div v-else class="flex items-center gap-2">
        <input
          v-model="tariffInput"
          type="number"
          min="0"
          max="9999"
          step="0.0001"
          :placeholder="t('goe.tariff_placeholder')"
          class="flex-1 border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 rounded-lg px-2 py-1 text-sm"
          @keyup.enter="saveTariff"
          @keyup.escape="editingTariff = false"
        />
        <span class="text-xs text-gray-500 dark:text-gray-400 shrink-0">ct/kWh</span>
        <button @click="saveTariff" :disabled="savingTariff"
          class="p-1 text-green-600 hover:text-green-700 disabled:opacity-50">
          <CheckIcon class="h-4 w-4" />
        </button>
        <button @click="editingTariff = false"
          class="p-1 text-gray-400 hover:text-gray-600">
          <XMarkIcon class="h-4 w-4" />
        </button>
      </div>
    </div>

    <!-- Location -->
    <div class="px-5 py-3 border-t border-gray-100 dark:border-gray-700">
      <div v-if="!editingLocation" class="flex items-center justify-between">
        <span class="text-xs text-gray-500 dark:text-gray-400 flex items-center gap-1">
          <MapPinIcon class="h-3.5 w-3.5" />
          {{ t('goe.location_status_label') }}
          <span :class="conn.geohash ? 'font-medium text-gray-700 dark:text-gray-300' : 'text-amber-600 font-medium'">
            {{ conn.geohash ? t('goe.location_status_set') : t('goe.location_status_missing') }}
          </span>
        </span>
        <button @click="startEditLocation"
          class="flex items-center gap-1 text-xs text-green-600 hover:text-green-700 font-medium">
          <PencilIcon class="h-3.5 w-3.5" />
          {{ conn.geohash ? t('goe.location_change') : t('goe.location_set_btn') }}
        </button>
      </div>
      <div v-else class="space-y-2">
        <div class="relative">
          <input
            v-model="locationQuery"
            type="text"
            :placeholder="t('goe.location_placeholder')"
            class="w-full border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 rounded-lg px-3 py-1.5 text-sm pr-8"
            @input="onLocationQueryChange"
            @keyup.escape="editingLocation = false"
          />
          <MapPinIcon class="absolute right-2.5 top-2 h-4 w-4 text-gray-400 pointer-events-none" />
        </div>
        <ul v-if="locationResults.length" class="border border-gray-200 dark:border-gray-600 bg-white dark:bg-gray-700 rounded-lg overflow-hidden text-xs">
          <li v-for="r in locationResults" :key="r.display_name"
            class="px-3 py-2 hover:bg-gray-50 dark:hover:bg-gray-600 text-gray-900 dark:text-gray-100 cursor-pointer truncate border-b border-gray-100 dark:border-gray-600 last:border-0"
            @click="selectLocation(r)">
            {{ r.display_name }}
          </li>
        </ul>
        <p v-if="selectedLocationName" class="text-xs text-green-700 flex items-center gap-1">
          <CheckIcon class="h-3.5 w-3.5" />
          {{ selectedLocationName }}
        </p>
        <div class="flex gap-2">
          <button @click="saveLocation" :disabled="savingLocation || !selectedGeohash"
            class="flex-1 bg-green-600 text-white py-1.5 rounded-lg text-xs font-medium hover:bg-green-700 disabled:opacity-40 transition">
            {{ savingLocation ? t('goe.location_save_loading') : t('goe.location_save') }}
          </button>
          <button @click="editingLocation = false"
            class="px-3 py-1.5 border border-gray-300 dark:border-gray-600 rounded-lg text-xs text-gray-600 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 transition">
            {{ t('goe.location_cancel') }}
          </button>
        </div>
      </div>
    </div>

    <!-- Merge Sessions -->
    <div class="px-5 py-3 border-t border-gray-100 dark:border-gray-700">
      <div class="flex items-center justify-between">
        <div>
          <p class="text-xs font-medium text-gray-700 dark:text-gray-300">{{ t('goe.merge_sessions_title') }}</p>
          <p class="text-xs text-gray-400 dark:text-gray-500 mt-0.5">{{ t('goe.merge_sessions_desc') }}</p>
        </div>
        <button
          @click="toggleMergeSessions"
          :disabled="savingMergeSessions"
          :class="['relative inline-flex h-5 w-9 items-center rounded-full transition-colors disabled:opacity-50',
                   conn.mergeSessions ? 'bg-indigo-600' : 'bg-gray-200']">
          <span :class="['inline-block h-3 w-3 transform rounded-full bg-white transition-transform',
                         conn.mergeSessions ? 'translate-x-5' : 'translate-x-1']" />
        </button>
      </div>
    </div>

    <!-- Footer -->
    <div class="flex items-center justify-between px-5 py-2 border-t border-gray-100 dark:border-gray-700">
      <span class="text-xs text-gray-400 dark:text-gray-500">Serial: {{ conn.serial }}</span>
      <span class="text-xs text-gray-400 dark:text-gray-500">
        {{ t('goe.updated_every', { n: isCharging ? 2 : 4 }) }}
      </span>
    </div>
  </div>
</template>
