<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ExclamationTriangleIcon, InformationCircleIcon, ChevronDownIcon, MapPinIcon, CheckIcon } from '@heroicons/vue/24/outline'
import Collapse from '../shared/Collapse.vue'
import { CI_BTN_PRIMARY, CI_BTN_SECONDARY, CI_INPUT, CI_LABEL, CI_CARD, CI_NOTE } from '@/utils/ciClasses'
import goeService from '@/api/goeService'
import type { Car } from '@/api/carService'
import { carLabel } from '@/composables/useCarLabel'
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
// Heim-Strompreis in ct/kWh. Frei lassbar (String, damit "" != 0): ohne Angabe bleiben
// Ladungen preislos, mit Angabe ist schon die erste Session bepreist.
const tariffInput = ref('')

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
    const parsedTariff = tariffInput.value.trim() === '' ? null : Number(tariffInput.value)
    const tariff = parsedTariff != null && !Number.isNaN(parsedTariff) ? parsedTariff : null
    await goeService.connect(form.value.serial, form.value.apiKey, form.value.carId, form.value.displayName, selectedGeohash.value, tariff)
    form.value = { serial: '', apiKey: '', carId: cars.value[0]?.id ?? '', displayName: '' }
    tariffInput.value = ''
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

</script>

<template>
  <div class="grid gap-4">
    <p v-if="error" role="alert" :class="[CI_NOTE, 'flex items-start gap-2 py-3 font-semibold text-gray-900 dark:text-gray-100']">
      <ExclamationTriangleIcon class="h-5 w-5 shrink-0 text-gray-600 dark:text-gray-300" aria-hidden="true" />{{ error }}
    </p>

    <!-- So funktioniert's -->
    <div class="rounded-2xl bg-gray-100 dark:bg-gray-800/60">
      <button type="button" :aria-expanded="infoExpanded" @click="infoExpanded = !infoExpanded"
        class="flex min-h-11 w-full items-center justify-between gap-3 px-4 py-3 text-left text-sm font-semibold text-gray-900 dark:text-gray-100">
        <span class="flex items-center gap-2">
          <InformationCircleIcon class="h-5 w-5 shrink-0 text-gray-500 dark:text-gray-400" aria-hidden="true" />
          {{ t('goe.how_it_works') }}
        </span>
        <ChevronDownIcon class="h-4 w-4 shrink-0 text-gray-500 transition-transform duration-300 motion-reduce:transition-none"
          :class="infoExpanded ? 'rotate-180 ease-out' : 'ease-in'" aria-hidden="true" />
      </button>
      <Collapse :open="infoExpanded">
        <div class="grid gap-2 px-4 pb-4 text-sm leading-relaxed text-gray-700 dark:text-gray-300">
          <p v-html="t('goe.info_auto')" />
          <p v-html="t('goe.info_session')" />
          <p v-html="t('goe.info_tariff')" />
          <div class="mt-1 grid gap-1 border-t border-gray-200 pt-3 dark:border-white/10">
            <p :class="CI_LABEL">{{ t('goe.info_one_car_title') }}</p>
            <p v-html="t('goe.info_one_car')" />
          </div>
        </div>
      </Collapse>
    </div>

    <!-- Connected devices -->
    <div v-if="wallboxStore.hasConnections" class="grid gap-3">
      <p :class="CI_LABEL">{{ t('goe.connected_devices') }}</p>
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
      <div v-if="showForm" :class="[CI_CARD, 'grid gap-3']">
        <p class="text-[15px] font-bold text-gray-900 dark:text-gray-100">{{ t('goe.form_title') }}</p>
        <div class="grid gap-1.5">
          <label for="goe-serial" :class="CI_LABEL">{{ t('goe.serial_label') }}</label>
          <input id="goe-serial" v-model="form.serial" type="text" :placeholder="t('goe.serial_placeholder')" :class="CI_INPUT" />
        </div>
        <div class="grid gap-1.5">
          <label for="goe-apikey" :class="CI_LABEL">{{ t('goe.apikey_label') }}</label>
          <input id="goe-apikey" v-model="form.apiKey" type="password" :placeholder="t('goe.apikey_placeholder')" :class="CI_INPUT" />
        </div>
        <div v-if="cars.length > 0" class="grid gap-1.5">
          <label for="goe-car" :class="CI_LABEL">{{ t('goe.car_label') }}</label>
          <select id="goe-car" v-model="form.carId" :class="CI_INPUT">
            <option v-for="car in cars" :key="car.id" :value="car.id">
              {{ carLabel(car) }}
            </option>
          </select>
        </div>
        <div class="grid gap-1.5">
          <label for="goe-tariff" :class="CI_LABEL">{{ t('goe.connect_tariff_label') }}</label>
          <div class="relative">
            <input id="goe-tariff" v-model="tariffInput" type="number" inputmode="decimal" min="0" max="9999" step="0.01" :placeholder="t('goe.tariff_placeholder')" :class="[CI_INPUT, 'pr-10 tabular-nums']" />
            <span class="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-sm font-medium text-gray-400">ct</span>
          </div>
          <p class="text-[13px] leading-snug text-gray-500 dark:text-gray-400">{{ t('goe.connect_tariff_hint') }}</p>
        </div>
        <div class="grid gap-1.5">
          <label for="goe-name" :class="CI_LABEL">{{ t('goe.name_label') }}</label>
          <input id="goe-name" v-model="form.displayName" type="text" :placeholder="t('goe.name_placeholder')" :class="CI_INPUT" />
        </div>

        <!-- Location (optional) -->
        <div class="grid gap-1.5">
          <label for="goe-location" :class="CI_LABEL">{{ t('goe.location_label') }}</label>
          <div class="relative">
            <input
              id="goe-location"
              v-model="locationQuery"
              type="text"
              :placeholder="t('goe.location_placeholder')"
              :class="[CI_INPUT, 'pr-9']"
              @input="onLocationQueryChange"
            />
            <MapPinIcon class="pointer-events-none absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400" aria-hidden="true" />
          </div>
          <ul v-if="locationResults.length" class="overflow-hidden rounded-xl border border-gray-200 bg-white text-sm dark:border-gray-600 dark:bg-gray-900">
            <li v-for="r in locationResults" :key="r.display_name"
              class="cursor-pointer truncate border-b border-gray-200 px-3 py-2.5 last:border-0 hover:bg-gray-50 dark:border-white/10 dark:hover:bg-gray-800"
              @click="selectLocation(r)">
              {{ r.display_name }}
            </li>
          </ul>
          <p v-if="selectedLocationName" class="flex items-center gap-1 text-[13px] font-semibold text-gray-900 dark:text-gray-100">
            <CheckIcon class="h-4 w-4 shrink-0 text-green-700 dark:text-green-400" aria-hidden="true" />
            {{ selectedLocationName }}
          </p>
        </div>

        <div class="flex flex-col gap-2 sm:flex-row">
          <button type="button" :class="[CI_BTN_PRIMARY, 'flex-1']" :disabled="loading" @click="handleConnect">
            {{ loading ? t('goe.connect_btn_loading') : t('goe.connect_btn') }}
          </button>
          <button type="button" :class="CI_BTN_SECONDARY" @click="showForm = false">
            {{ t('goe.cancel_btn') }}
          </button>
        </div>
      </div>

      <button v-if="!showForm" type="button" :class="[CI_BTN_PRIMARY, 'w-full sm:w-auto sm:justify-self-start']" @click="showForm = true">
        {{ t('goe.add_btn') }}
      </button>
    </template>
  </div>
</template>
