<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { BoltIcon, XCircleIcon } from '@heroicons/vue/24/outline'
import { useCarStore } from '../../stores/car'
import { purchasesAvailable } from '../../utils/iapPolicy'
import CarSelectDropdown from '../car/CarSelectDropdown.vue'
import SmartcarFaq from '../SmartcarFaq.vue'
import smartcarService, { type SmartcarConnectionStatus } from '../../api/smartcarService'
import type { Car } from '../../api/carService'
import { AUTOSYNC_BRANDS } from '../../config/smartcarBrands'

const { t } = useI18n()

// embedded = rendered inside the AutoSync car-tile picker. Suppresses the
// premium teaser (parent picker handles it), the brand-list and "How it works" FAQ
// (also at picker level), keeping the tile focused on this car's connect/status.
// forcedCarId locks the connect to the tile's car (skips the internal dropdown).
const props = defineProps<{
    premiumEnabled?: boolean
    isPremium?: boolean
    embedded?: boolean
    forcedCarId?: string
}>()

const brands = AUTOSYNC_BRANDS
const carStore = useCarStore()

const status = ref<SmartcarConnectionStatus | null>(null)
const loading = ref(true)
const connecting = ref(false)
const disconnecting = ref(false)
const error = ref<string | null>(null)
const cars = ref<Car[]>([])
const selectedCarId = ref<string | null>(null)

onMounted(async () => {
  if (!props.premiumEnabled && !props.isPremium) return
  try {
    const [s, c] = await Promise.all([
      smartcarService.getStatus(),
      carStore.getCars(),
    ])
    status.value = s
    cars.value = (c ?? []).filter((car: Car) => car.status === 'ACTIVE')
    if (props.forcedCarId) {
      // Tile-context: car is fixed by the surrounding picker.
      selectedCarId.value = props.forcedCarId
    } else if (cars.value.length === 1) {
      selectedCarId.value = cars.value[0].id
    }
    // Handle redirect params after OAuth callback
    const params = new URLSearchParams(window.location.search)
    if (params.get('smartcar-connected')) {
      await smartcarService.getStatus().then(s => status.value = s)
      window.history.replaceState({}, '', window.location.pathname)
    }
    if (params.get('smartcar-error')) {
      const code = params.get('smartcar-error')!
      if (code === 'VIN_ALREADY_LINKED') {
        error.value = t('imports.smartcar_error_vin_linked_title') + ' - ' + t('imports.smartcar_error_vin_linked_body')
      } else if (code === 'NO_VEHICLES_FOUND') {
        error.value = t('imports.smartcar_error_no_vehicles_body')
      } else {
        error.value = t('imports.smartcar_error_unknown_body')
      }
      window.history.replaceState({}, '', window.location.pathname)
    }
  } catch (e: any) {
    error.value = e.message
  } finally {
    loading.value = false
  }
})

const connect = async () => {
  if (!selectedCarId.value) return
  connecting.value = true
  error.value = null
  try {
    const { authUrl, available } = await smartcarService.getAuthStartUrl(selectedCarId.value)
    if (!available || !authUrl) {
      error.value = t('imports.smartcar_not_configured')
      return
    }
    window.location.href = authUrl
  } catch (e: any) {
    error.value = e.response?.data?.message || e.message
  } finally {
    connecting.value = false
  }
}

const disconnect = async () => {
  if (!confirm(t('imports.smartcar_confirm_disconnect'))) return
  disconnecting.value = true
  try {
    await smartcarService.disconnect()
    status.value = { connected: false, vehicleName: null, carId: null, vin: null, vehicleState: null, lastCheckedAt: null, lastSoc: null, sessionActive: false, sessionStartedAt: null, sessionEnergyAdded: null }
  } catch (e: any) {
    error.value = e.message
  } finally {
    disconnecting.value = false
  }
}

const stateLabel = (state: string | null) => {
  if (state === 'CHARGING') return t('imports.smartcar_state_charging')
  if (state === 'NOT_CHARGING') return t('imports.smartcar_state_idle')
  if (state === 'FULLY_CHARGED') return t('imports.smartcar_state_full')
  return t('imports.smartcar_state_unknown')
}
</script>

<template>
  <!-- TEASER: Premium-Kauf möglich, aber User noch kein Abonnent.
       In embedded (tile) mode the picker handles the teaser at parent level. -->
  <div v-if="props.premiumEnabled && !props.isPremium && !props.embedded" class="space-y-5">
    <!-- Header -->
    <div>
      <p class="text-amber-600 dark:text-amber-500 text-[11px] font-bold uppercase tracking-[0.14em] mb-2 flex items-center gap-2">
        <span class="inline-flex w-5 h-5 bg-amber-500 text-gray-950 rounded-sm items-center justify-center text-[11px] font-extrabold">⚡</span>
        EV Monitor AutoSync
      </p>
      <h2 class="text-xl md:text-2xl font-bold text-gray-900 dark:text-white tracking-tight mb-1.5">
        {{ t('imports.smartcar_teaser_title') }}
      </h2>
      <p class="text-sm text-gray-500 dark:text-gray-400 leading-relaxed">{{ t('imports.smartcar_teaser_desc') }}</p>
    </div>

    <!-- Feature-Liste mit Pfeil-Bullets -->
    <ul class="space-y-2.5">
      <li class="flex items-start gap-2.5 text-sm text-gray-700 dark:text-gray-300">
        <span class="shrink-0 w-5 h-5 bg-amber-500 text-gray-950 rounded-sm flex items-center justify-center text-[10px] font-extrabold mt-0.5">→</span>
        <span class="font-medium">{{ t('imports.smartcar_feat2') }}</span>
      </li>
      <li class="flex items-start gap-2.5 text-sm text-gray-700 dark:text-gray-300">
        <span class="shrink-0 w-5 h-5 bg-amber-500 text-gray-950 rounded-sm flex items-center justify-center text-[10px] font-extrabold mt-0.5">→</span>
        <span class="font-medium">{{ t('imports.smartcar_feat3') }}</span>
      </li>
      <li class="flex items-start gap-2.5 text-sm text-gray-700 dark:text-gray-300">
        <span class="shrink-0 w-5 h-5 bg-amber-500 text-gray-950 rounded-sm flex items-center justify-center text-[10px] font-extrabold mt-0.5">→</span>
        <span class="font-medium">{{ t('imports.smartcar_feat4') }}</span>
      </li>
    </ul>

    <!-- Brand-List Block -->
    <div class="border-2 border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-900 rounded-sm shadow-[2px_2px_0_0_#d1d5db] dark:shadow-[2px_2px_0_0_#374151] p-4 md:p-5">
      <p class="text-[11px] font-bold uppercase tracking-[0.14em] text-gray-500 dark:text-gray-400 mb-3">{{ t('imports.smartcar_brands_title') }}</p>
      <div class="flex flex-wrap gap-1.5">
        <span
          v-for="brand in brands" :key="brand"
          class="text-xs font-bold uppercase tracking-wider bg-gray-100 dark:bg-gray-800 text-gray-700 dark:text-gray-300 border border-gray-300 dark:border-gray-600 px-2 py-1 rounded-sm"
        >{{ brand }}</span>
      </div>
    </div>

    <!-- FAQ Accordion -->
    <SmartcarFaq />

    <!-- Upgrade CTA (in der nativen App ausgeblendet, Guideline 3.1.1) -->
    <div v-if="purchasesAvailable()">
      <router-link
        to="/upgrade"
        class="w-full block text-center bg-amber-500 hover:bg-amber-400 text-gray-950 font-bold uppercase tracking-wider text-xs md:text-sm px-5 py-3.5 rounded-sm border-2 border-amber-500 shadow-[2px_2px_0_0_#030712] active:translate-x-[4px] active:translate-y-[4px] active:shadow-none transition-[transform,box-shadow] duration-75"
      >
        {{ t('imports.smartcar_upgrade_cta', { priceMonthly: t('upgrade.price_monthly') }) }}
      </router-link>
      <p class="text-[11px] font-bold uppercase tracking-wider text-gray-500 dark:text-gray-400 text-center mt-3">{{ t('imports.smartcar_upgrade_price') }}</p>
      <p class="text-xs text-gray-500 dark:text-gray-400 text-center mt-2">
        {{ t('imports.smartcar_support_hint') }}
        <a href="mailto:support@ev-monitor.net" class="underline hover:no-underline font-medium">support@ev-monitor.net</a>
      </p>
    </div>
  </div>

  <!-- ADMIN: full setup UI -->
  <div v-else :class="props.embedded ? 'space-y-4' : 'space-y-5'">

    <div v-if="loading" class="text-[11px] font-bold uppercase tracking-wider text-gray-500 dark:text-gray-400">{{ t('imports.smartcar_loading') }}</div>

    <template v-else>
      <!-- How it works FAQ. Hidden in embedded (tile) mode - picker shows it at
           the parent level so each tile stays focused on the per-car action. -->
      <SmartcarFaq v-if="!props.embedded" />

      <!-- Error - neo style border-l accent -->
      <div v-if="error" class="border-l-2 border-red-500 bg-red-50 dark:bg-red-950/40 px-4 py-3 rounded-r-sm space-y-1">
        <p class="text-[11px] font-bold uppercase tracking-wider text-red-700 dark:text-red-400">{{ t('imports.smartcar_error_unknown_body').substring(0, 0) || 'Fehler' }}</p>
        <p class="text-sm text-red-900 dark:text-red-200 font-medium">{{ error }}</p>
        <p class="text-xs text-red-700/80 dark:text-red-300/70">
          {{ t('imports.smartcar_support_hint') }}
          <a href="mailto:support@ev-monitor.net" class="font-bold underline hover:no-underline">support@ev-monitor.net</a>
        </p>
      </div>

      <!-- Connected -->
      <div v-if="status?.connected" class="space-y-4">
        <!-- Embedded (tile) mode: compact display -->
        <template v-if="props.embedded">
          <div class="space-y-2">
            <div class="flex flex-wrap items-center gap-2">
              <span
                v-if="status.vehicleState"
                class="text-[10px] font-bold uppercase tracking-wider px-2 py-1 rounded-sm"
                :class="status.vehicleState === 'CHARGING'
                  ? 'bg-emerald-500 text-white'
                  : status.vehicleState === 'FULLY_CHARGED'
                    ? 'bg-blue-600 text-white'
                    : 'bg-gray-300 dark:bg-gray-700 text-gray-700 dark:text-gray-300'"
              >{{ stateLabel(status.vehicleState) }}</span>
              <span v-if="status.sessionActive" class="text-[10px] font-bold uppercase tracking-wider bg-emerald-500 text-white px-2 py-1 rounded-sm flex items-center gap-1.5">
                <span class="inline-block w-1.5 h-1.5 rounded-full bg-white animate-pulse" />
                {{ t('imports.smartcar_session_active') }}
              </span>
            </div>
            <div class="flex flex-wrap gap-x-4 gap-y-1 text-xs font-mono text-gray-600 dark:text-gray-400">
              <span v-if="status.vin"><span class="text-gray-400 dark:text-gray-500">VIN:</span> <span class="text-gray-900 dark:text-white font-semibold">{{ status.vin }}</span></span>
              <span v-if="status.lastSoc != null"><span class="text-gray-400 dark:text-gray-500">SoC:</span> <span class="text-gray-900 dark:text-white font-semibold">{{ status.lastSoc }}%</span></span>
              <span v-if="status.lastCheckedAt"><span class="text-gray-400 dark:text-gray-500">{{ t('imports.smartcar_last_update') }}:</span> <span class="text-gray-900 dark:text-white font-semibold">{{ new Date(status.lastCheckedAt).toLocaleString() }}</span></span>
            </div>
          </div>
        </template>

        <!-- Standalone mode: full card -->
        <template v-else>
          <div class="border-2 border-emerald-500 dark:border-emerald-400 bg-white dark:bg-gray-900 rounded-sm shadow-[2px_2px_0_0_#10b981] p-4 md:p-5">
            <div class="flex items-start gap-3 mb-3">
              <span class="inline-flex w-7 h-7 bg-emerald-500 text-white rounded-sm items-center justify-center text-sm font-extrabold shrink-0 mt-0.5">✓</span>
              <div class="flex-1 min-w-0">
                <p class="text-[11px] font-bold uppercase tracking-[0.14em] text-emerald-700 dark:text-emerald-400 mb-0.5">{{ t('imports.smartcar_connected_label') || 'Verbunden' }}</p>
                <p class="text-base font-bold text-gray-900 dark:text-white">{{ status.vehicleName }}</p>
                <p v-if="status.vin" class="text-xs text-gray-500 dark:text-gray-400 font-mono mt-0.5">{{ status.vin }}</p>
              </div>
            </div>
            <div class="flex flex-wrap items-center gap-2 mb-3">
              <span
                v-if="status.vehicleState"
                class="text-[10px] font-bold uppercase tracking-wider px-2 py-1 rounded-sm"
                :class="status.vehicleState === 'CHARGING'
                  ? 'bg-emerald-500 text-white'
                  : status.vehicleState === 'FULLY_CHARGED'
                    ? 'bg-blue-600 text-white'
                    : 'bg-gray-300 dark:bg-gray-700 text-gray-700 dark:text-gray-300'"
              >{{ stateLabel(status.vehicleState) }}</span>
              <span v-else class="text-[10px] font-bold uppercase tracking-wider bg-gray-200 dark:bg-gray-800 text-gray-500 dark:text-gray-400 px-2 py-1 rounded-sm">{{ t('imports.smartcar_waiting_data') }}</span>
              <span v-if="status.sessionActive" class="text-[10px] font-bold uppercase tracking-wider bg-emerald-500 text-white px-2 py-1 rounded-sm flex items-center gap-1.5">
                <span class="inline-block w-1.5 h-1.5 rounded-full bg-white animate-pulse" />
                {{ t('imports.smartcar_session_active') }}
              </span>
            </div>
            <div v-if="status.lastCheckedAt" class="pt-3 border-t-2 border-dashed border-gray-200 dark:border-gray-700 flex flex-wrap gap-x-4 gap-y-1 text-xs font-mono">
              <span><span class="text-gray-400 dark:text-gray-500">{{ t('imports.smartcar_last_update') }}:</span> <span class="text-gray-900 dark:text-white font-semibold">{{ new Date(status.lastCheckedAt).toLocaleString() }}</span></span>
              <span v-if="status.lastSoc != null"><span class="text-gray-400 dark:text-gray-500">SoC:</span> <span class="text-gray-900 dark:text-white font-semibold">{{ status.lastSoc }}%</span></span>
              <span v-if="status.sessionActive && status.sessionEnergyAdded != null"><span class="text-gray-400 dark:text-gray-500">{{ t('imports.smartcar_energy_added') }}:</span> <span class="text-gray-900 dark:text-white font-semibold">{{ status.sessionEnergyAdded }} kWh</span></span>
              <span v-if="status.sessionActive && status.sessionStartedAt"><span class="text-gray-400 dark:text-gray-500">{{ t('imports.smartcar_session_since') }}:</span> <span class="text-gray-900 dark:text-white font-semibold">{{ new Date(status.sessionStartedAt).toLocaleTimeString() }}</span></span>
            </div>
          </div>
        </template>

        <button
          @click="disconnect"
          :disabled="disconnecting"
          class="inline-flex items-center gap-2 bg-gray-950 dark:bg-white text-white dark:text-gray-950 font-bold uppercase tracking-wider text-[11px] px-4 py-2.5 rounded-sm border-2 border-gray-950 dark:border-white shadow-[2px_2px_0_0_#dc2626] active:translate-x-[3px] active:translate-y-[3px] active:shadow-none transition-[transform,box-shadow] duration-75 disabled:opacity-50 disabled:cursor-not-allowed"
        >
          <XCircleIcon class="h-4 w-4" />
          {{ disconnecting ? t('imports.smartcar_disconnecting') : t('imports.smartcar_disconnect_btn') }}
        </button>
      </div>

      <!-- Not connected -->
      <div v-else class="space-y-4">
        <div v-if="cars.length === 0" class="border-l-2 border-amber-500 bg-amber-50 dark:bg-amber-950/30 px-4 py-3 rounded-r-sm">
          <p class="text-sm text-gray-700 dark:text-gray-200 font-medium">
            {{ t('imports.smartcar_no_cars') }}
            <router-link to="/cars" class="font-bold underline hover:no-underline ml-1">{{ t('imports.smartcar_add_car') }}</router-link>
          </p>
        </div>
        <template v-else>
          <div v-if="cars.length > 1 && !props.forcedCarId" class="space-y-1.5">
            <label class="block text-[11px] font-bold uppercase tracking-wider text-gray-500 dark:text-gray-400">{{ t('imports.smartcar_select_car') }}</label>
            <CarSelectDropdown :cars="cars" v-model="selectedCarId" />
          </div>
          <button
            @click="connect"
            :disabled="connecting || !selectedCarId"
            class="w-full inline-flex items-center justify-center gap-2 bg-amber-500 hover:bg-amber-400 disabled:bg-gray-300 dark:disabled:bg-gray-700 disabled:text-gray-500 disabled:cursor-not-allowed text-gray-950 font-bold uppercase tracking-wider text-xs px-5 py-3 rounded-sm border-2 border-amber-500 disabled:border-gray-300 dark:disabled:border-gray-700 shadow-[2px_2px_0_0_#030712] disabled:shadow-none active:translate-x-[3px] active:translate-y-[3px] active:shadow-none transition-[transform,box-shadow] duration-75"
          >
            <BoltIcon class="h-4 w-4" />
            {{ connecting ? t('imports.smartcar_connecting') : t('imports.smartcar_connect_btn') }}
          </button>
          <p class="text-xs text-gray-500 dark:text-gray-400">
            {{ t('imports.smartcar_support_hint') }}
            <a href="mailto:support@ev-monitor.net" class="font-bold underline hover:no-underline">support@ev-monitor.net</a>
          </p>
        </template>
      </div>
    </template>
  </div>
</template>
