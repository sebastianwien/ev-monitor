<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { BoltIcon, XCircleIcon, CheckIcon, CheckCircleIcon, ExclamationCircleIcon } from '@heroicons/vue/24/outline'
import { CI_BTN_PRIMARY, CI_BTN_SECONDARY, CI_LABEL, CI_CARD, CI_NOTE } from '../../utils/ciClasses'
import { useCarStore } from '../../stores/car'
import { purchasesAvailable } from '../../utils/iapPolicy'
import CarSelectDropdown from '../car/CarSelectDropdown.vue'
import SmartcarFaq from '../SmartcarFaq.vue'
import smartcarService, { connectionForCar, DISCONNECTED_STATUS, type SmartcarCarConnection, type SmartcarConnectionStatus } from '../../api/smartcarService'
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

const emit = defineEmits<{ (e: 'connected' | 'disconnected', carId: string): void }>()

const brands = AUTOSYNC_BRANDS
const carStore = useCarStore()

const rawStatus = ref<SmartcarConnectionStatus | null>(null)
// Im Kachel-Modus zählt nur die Verbindung dieses Autos (AutoSync-Slots: eine je Fahrzeug).
const status = computed<SmartcarCarConnection | SmartcarConnectionStatus | null>(() =>
  props.forcedCarId && rawStatus.value ? connectionForCar(rawStatus.value, props.forcedCarId) : rawStatus.value)
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
    rawStatus.value = s
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
      await smartcarService.getStatus().then(s => rawStatus.value = s)
      window.history.replaceState({}, '', window.location.pathname)
    }
    if (params.get('smartcar-error')) {
      const code = params.get('smartcar-error')!
      if (code === 'VIN_ALREADY_LINKED') {
        error.value = t('imports.smartcar_error_vin_linked_title') + ' - ' + t('imports.smartcar_error_vin_linked_body')
      } else if (code === 'AUTOSYNC_SLOTS_EXHAUSTED') {
        error.value = t('imports.smartcar_error_slots_exhausted')
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
    error.value = e.response?.status === 409 && String(e.response?.data?.message ?? '').includes('AUTOSYNC_SLOTS_EXHAUSTED')
      ? t('imports.smartcar_error_slots_exhausted')
      : (e.response?.data?.message || e.message)
  } finally {
    connecting.value = false
  }
}

const disconnect = async () => {
  if (!confirm(t('imports.smartcar_confirm_disconnect'))) return
  disconnecting.value = true
  try {
    const carId = selectedCarId.value ?? status.value?.carId
    if (!carId) return
    await smartcarService.disconnect(carId)
    rawStatus.value = await smartcarService.getStatus().catch(() => DISCONNECTED_STATUS)
    emit('disconnected', carId)
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
  <div v-if="props.premiumEnabled && !props.isPremium && !props.embedded" class="grid gap-5">
    <div>
      <p :class="[CI_LABEL, 'mb-2 flex items-center gap-2']">
        <BoltIcon class="h-4 w-4" aria-hidden="true" />EV Monitor AutoSync
      </p>
      <h2 class="mb-1.5 text-[22px] font-bold tracking-tight text-gray-900 md:text-2xl dark:text-gray-100">
        {{ t('imports.smartcar_teaser_title') }}
      </h2>
      <p class="text-[15px] leading-relaxed text-gray-600 dark:text-gray-400">{{ t('imports.smartcar_teaser_desc') }}</p>
    </div>

    <ul class="grid gap-2.5">
      <li v-for="k in ['smartcar_feat2', 'smartcar_feat3', 'smartcar_feat4']" :key="k" class="flex items-start gap-2.5 text-[15px] text-gray-700 dark:text-gray-300">
        <CheckIcon class="mt-0.5 h-5 w-5 shrink-0 text-gray-400" aria-hidden="true" />{{ t(`imports.${k}`) }}
      </li>
    </ul>

    <div :class="CI_CARD">
      <p :class="[CI_LABEL, 'mb-3']">{{ t('imports.smartcar_brands_title') }}</p>
      <div class="flex flex-wrap gap-1.5">
        <span v-for="brand in brands" :key="brand"
          class="rounded-full bg-gray-100 px-2.5 py-1 text-[12px] font-semibold text-gray-700 dark:bg-gray-800 dark:text-gray-300">{{ brand }}</span>
      </div>
    </div>

    <SmartcarFaq />

    <!-- Upgrade CTA (in der nativen App ausgeblendet, Guideline 3.1.1) -->
    <div v-if="purchasesAvailable()" class="grid gap-2 text-center">
      <router-link to="/upgrade" :class="[CI_BTN_PRIMARY, 'w-full']">
        {{ t('imports.smartcar_upgrade_cta', { priceMonthly: t('upgrade.price_monthly') }) }}
      </router-link>
      <p class="text-[13px] font-semibold tabular-nums text-gray-500 dark:text-gray-400">{{ t('imports.smartcar_upgrade_price') }}</p>
      <p class="text-[13px] text-gray-500 dark:text-gray-400">
        {{ t('imports.smartcar_support_hint') }}
        <a href="mailto:support@ev-monitor.net" class="font-semibold underline hover:no-underline">support@ev-monitor.net</a>
      </p>
    </div>
  </div>

  <!-- ADMIN: full setup UI -->
  <div v-else class="grid" :class="props.embedded ? 'gap-4' : 'gap-5'">

    <p v-if="loading" role="status" :class="CI_LABEL">{{ t('imports.smartcar_loading') }}</p>

    <template v-else>
      <!-- How it works FAQ. Hidden in embedded (tile) mode - picker shows it at
           the parent level so each tile stays focused on the per-car action. -->
      <SmartcarFaq v-if="!props.embedded" />

      <div v-if="error" role="alert" :class="[CI_NOTE, 'flex items-start gap-2 py-3']">
        <ExclamationCircleIcon class="h-5 w-5 shrink-0 text-gray-700 dark:text-gray-300" aria-hidden="true" />
        <div class="grid gap-1">
          <p class="font-semibold text-gray-900 dark:text-gray-100">{{ error }}</p>
          <p class="text-[13px] text-gray-600 dark:text-gray-400">
            {{ t('imports.smartcar_support_hint') }}
            <a href="mailto:support@ev-monitor.net" class="font-semibold underline hover:no-underline">support@ev-monitor.net</a>
          </p>
        </div>
      </div>

      <!-- Connected -->
      <div v-if="status?.connected" class="grid gap-4">
        <!-- Zustand des Autos: Grün nur, solange es lädt (Ergebnis), sonst grau -->
        <template v-if="props.embedded">
          <div class="grid gap-2">
            <div class="flex flex-wrap items-center gap-x-4 gap-y-1">
              <span v-if="status.vehicleState" class="inline-flex items-center gap-1.5 text-[13px] font-semibold"
                :class="status.vehicleState === 'CHARGING' ? 'text-green-700 dark:text-green-400' : 'text-gray-600 dark:text-gray-400'">
                <span class="h-2 w-2 rounded-full" :class="status.vehicleState === 'CHARGING' ? 'bg-green-600 dark:bg-green-400' : 'bg-gray-400'" aria-hidden="true" />
                {{ stateLabel(status.vehicleState) }}
              </span>
              <span v-if="status.sessionActive" class="inline-flex items-center gap-1.5 text-[13px] font-semibold text-green-700 dark:text-green-400">
                <span class="h-2 w-2 animate-pulse rounded-full bg-green-600 dark:bg-green-400" aria-hidden="true" />
                {{ t('imports.smartcar_session_active') }}
              </span>
            </div>
            <dl class="flex flex-wrap gap-x-4 gap-y-1 text-[13px] text-gray-500 dark:text-gray-400">
              <div v-if="status.vin" class="flex gap-1"><dt>VIN:</dt><dd class="font-mono font-semibold text-gray-900 dark:text-gray-100">{{ status.vin }}</dd></div>
              <div v-if="status.lastSoc != null" class="flex gap-1"><dt>SoC:</dt><dd class="font-semibold tabular-nums text-gray-900 dark:text-gray-100">{{ status.lastSoc }} %</dd></div>
              <div v-if="status.lastCheckedAt" class="flex gap-1"><dt>{{ t('imports.smartcar_last_update') }}:</dt><dd class="font-semibold tabular-nums text-gray-900 dark:text-gray-100">{{ new Date(status.lastCheckedAt).toLocaleString() }}</dd></div>
            </dl>
          </div>
        </template>

        <!-- Standalone mode: full card -->
        <div v-else :class="[CI_CARD, 'grid gap-3']">
          <div class="flex items-start gap-3">
            <CheckCircleIcon class="mt-0.5 h-6 w-6 shrink-0 text-green-700 dark:text-green-400" aria-hidden="true" />
            <div class="min-w-0 flex-1">
              <p :class="CI_LABEL">{{ t('imports.smartcar_connected_label') }}</p>
              <p class="text-base font-bold text-gray-900 dark:text-gray-100">{{ status.vehicleName }}</p>
              <p v-if="status.vin" class="mt-0.5 font-mono text-[12px] text-gray-500 dark:text-gray-400">{{ status.vin }}</p>
            </div>
          </div>
          <div class="flex flex-wrap items-center gap-x-4 gap-y-1">
            <span v-if="status.vehicleState" class="inline-flex items-center gap-1.5 text-[13px] font-semibold"
              :class="status.vehicleState === 'CHARGING' ? 'text-green-700 dark:text-green-400' : 'text-gray-600 dark:text-gray-400'">
              <span class="h-2 w-2 rounded-full" :class="status.vehicleState === 'CHARGING' ? 'bg-green-600 dark:bg-green-400' : 'bg-gray-400'" aria-hidden="true" />
              {{ stateLabel(status.vehicleState) }}
            </span>
            <span v-else class="text-[13px] text-gray-500 dark:text-gray-400">{{ t('imports.smartcar_waiting_data') }}</span>
            <span v-if="status.sessionActive" class="inline-flex items-center gap-1.5 text-[13px] font-semibold text-green-700 dark:text-green-400">
              <span class="h-2 w-2 animate-pulse rounded-full bg-green-600 dark:bg-green-400" aria-hidden="true" />
              {{ t('imports.smartcar_session_active') }}
            </span>
          </div>
          <dl v-if="status.lastCheckedAt" class="flex flex-wrap gap-x-4 gap-y-1 border-t border-gray-200 pt-3 text-[13px] text-gray-500 dark:border-white/10 dark:text-gray-400">
            <div class="flex gap-1"><dt>{{ t('imports.smartcar_last_update') }}:</dt><dd class="font-semibold tabular-nums text-gray-900 dark:text-gray-100">{{ new Date(status.lastCheckedAt).toLocaleString() }}</dd></div>
            <div v-if="status.lastSoc != null" class="flex gap-1"><dt>SoC:</dt><dd class="font-semibold tabular-nums text-gray-900 dark:text-gray-100">{{ status.lastSoc }} %</dd></div>
            <div v-if="status.sessionActive && status.sessionEnergyAdded != null" class="flex gap-1"><dt>{{ t('imports.smartcar_energy_added') }}:</dt><dd class="font-semibold tabular-nums text-gray-900 dark:text-gray-100">{{ status.sessionEnergyAdded }} kWh</dd></div>
            <div v-if="status.sessionActive && status.sessionStartedAt" class="flex gap-1"><dt>{{ t('imports.smartcar_session_since') }}:</dt><dd class="font-semibold tabular-nums text-gray-900 dark:text-gray-100">{{ new Date(status.sessionStartedAt).toLocaleTimeString() }}</dd></div>
          </dl>
        </div>

        <button type="button" :class="[CI_BTN_SECONDARY, 'justify-self-start']" :disabled="disconnecting" @click="disconnect">
          <XCircleIcon class="h-5 w-5" aria-hidden="true" />
          {{ disconnecting ? t('imports.smartcar_disconnecting') : t('imports.smartcar_disconnect_btn') }}
        </button>
      </div>

      <!-- Not connected -->
      <div v-else class="grid gap-4">
        <!-- Ohne Auto fehlt etwas: Amber mit Handlung -->
        <p v-if="cars.length === 0" class="rounded-2xl bg-amber-50 p-4 text-sm text-gray-800 dark:bg-amber-900/20 dark:text-gray-100">
          {{ t('imports.smartcar_no_cars') }}
          <router-link to="/cars" class="ml-1 font-semibold underline underline-offset-2 hover:no-underline">{{ t('imports.smartcar_add_car') }}</router-link>
        </p>
        <template v-else>
          <div v-if="cars.length > 1 && !props.forcedCarId" class="grid gap-1.5">
            <label :class="CI_LABEL">{{ t('imports.smartcar_select_car') }}</label>
            <CarSelectDropdown :cars="cars" v-model="selectedCarId" />
          </div>
          <button type="button" :class="[CI_BTN_PRIMARY, 'w-full']" :disabled="connecting || !selectedCarId" @click="connect">
            <BoltIcon class="h-5 w-5" aria-hidden="true" />
            {{ connecting ? t('imports.smartcar_connecting') : t('imports.smartcar_connect_btn') }}
          </button>
          <p class="text-[13px] text-gray-500 dark:text-gray-400">
            {{ t('imports.smartcar_support_hint') }}
            <a href="mailto:support@ev-monitor.net" class="font-semibold underline hover:no-underline">support@ev-monitor.net</a>
          </p>
        </template>
      </div>
    </template>
  </div>
</template>
