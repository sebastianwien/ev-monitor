<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter, RouterLink } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ArrowTopRightOnSquareIcon, ArrowPathIcon, XMarkIcon, CheckCircleIcon, ExclamationTriangleIcon, ChevronDownIcon } from '@heroicons/vue/24/outline'
import teslaFleetService, { type TeslaConnectionStatus, type TeslaFleetSyncResult, type TeslaPairingStatus } from '@/api/teslaFleetService'
import type { Car } from '@/api/carService'
import { useCarStore } from '@/stores/car'
import { useAuthStore } from '@/stores/auth'
import CarSelectDropdown from '../car/CarSelectDropdown.vue'

// Embedded mode = rendered inside the AutoSync car-tile picker. Suppresses the
// standalone-tab header, OAuth-flavoured "Was synchronisiert wird" box, the
// Kaffee disclaimer; renders a leaner Tesla-AutoSync hero instead.
// forcedCarId locks the connect-flow to one specific car (the tile's car) so the
// internal dropdown is bypassed - in tile-context the car is already chosen.
const props = defineProps<{ embedded?: boolean; forcedCarId?: string }>()
const emit = defineEmits<{
  (e: 'connected-label', label: string | null): void
}>()

const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const callbackErrorCode = ref<string | null>(null)

const status = ref<TeslaConnectionStatus>({ connected: false, vehicleName: null, carId: null, lastSyncAt: null, autoImportEnabled: false, geocodingInProgress: false, vehicleState: null, suspendAfterIdleMinutes: 15 })
const suspendMinutesInput = ref(15)
const settingsSaved = ref(false)
const isLoading = ref(false)
const syncResult = ref<TeslaFleetSyncResult | null>(null)
const error = ref<string | null>(null)
const success = ref<string | null>(null)
const confirmDisconnect = ref(false)
const lastImportedIds = ref<string[]>([])
const showDeleteAllConfirm = ref(false)
const deleteAllLoading = ref(false)
const deleteAllError = ref<string | null>(null)
const fleetApiConfigured = ref(true)
const authStore = useAuthStore()
const carStore = useCarStore()
const pairingStatus = ref<TeslaPairingStatus | null>(null)
const pairingStatusLoaded = ref(false)
const isTelemetryActive = computed(() => pairingStatus.value?.dataSource === 'TELEMETRY')
const isFullProfile = computed(() => pairingStatus.value?.telemetryProfile === 'FULL')

// Role-aware copy for the pairing block: Beta-Tester help with Trip-Detection,
// Founders are grandfathered, Premium users pay via AutoSync.
const pairingRoleBadge = computed(() => {
  if (authStore.isAdmin) return 'Beta · Admin'
  if (authStore.isBetaTester) return 'Beta'
  if (authStore.isTeslaFounder) return t('tesla.pairing_founder_badge')
  return ''
})
const pairingLoading = ref(false)
const pairingError = ref<string | null>(null)
const cars = ref<Car[]>([])
const carsLoaded = ref(false)
const selectedCarId = ref<string>('')
let geocodingPollInterval: ReturnType<typeof setInterval> | null = null

onMounted(async () => {
  await Promise.all([loadStatus(), loadCars()])
  if (status.value.geocodingInProgress) {
    startGeocodingPoll()
  }
  await loadPairingStatus()
  if (route.query['tesla-connected']) {
    success.value = t('tesla.success_connected')
    await loadStatus()
  }
  if (route.query['tesla-error']) {
    callbackErrorCode.value = String(route.query['tesla-error'])
    router.replace({ query: { ...route.query, 'tesla-error': undefined } })
  }
})

async function loadStatus() {
  try {
    status.value = await teslaFleetService.getStatus()
    suspendMinutesInput.value = status.value.suspendAfterIdleMinutes ?? 15
  } catch { /* ignore */ }
}

watch(() => status.value.connected, () => {
  emit('connected-label', status.value.connected ? (status.value.vehicleName || 'Tesla') : null)
}, { immediate: true })

function startGeocodingPoll() {
  if (geocodingPollInterval) return
  geocodingPollInterval = setInterval(async () => {
    await loadStatus()
    if (!status.value.geocodingInProgress) {
      stopGeocodingPoll()
    }
  }, 3000)
}

function stopGeocodingPoll() {
  if (geocodingPollInterval) {
    clearInterval(geocodingPollInterval)
    geocodingPollInterval = null
  }
}

onUnmounted(() => stopGeocodingPoll())

async function loadPairingStatus() {
  // Gate auf canActivateTelemetry statt nur Admin/Beta - sonst würden TESLA_FOUNDER
  // und Premium-USER ihren Telemetry-Status nie sehen.
  if (!authStore.canActivateTelemetry || !status.value.connected) {
    pairingStatusLoaded.value = true
    return
  }
  pairingError.value = null
  try {
    pairingStatus.value = await teslaFleetService.getPairingStatus()
  } catch (e: any) {
    if (e.response?.status === 404) {
      pairingError.value = t('tesla.pairing_err_not_connected')
    } else {
      pairingError.value = e.response?.data?.message || t('tesla.pairing_err_status')
    }
  } finally {
    pairingStatusLoaded.value = true
  }
}

async function handleEnableTelemetry() {
  pairingLoading.value = true
  pairingError.value = null
  try {
    await teslaFleetService.enableTelemetry()
    await loadPairingStatus()
  } catch (e: any) {
    pairingError.value = e.response?.data?.message || t('tesla.pairing_err_enable')
  } finally {
    pairingLoading.value = false
  }
}

async function handleDisableTelemetry() {
  pairingLoading.value = true
  pairingError.value = null
  try {
    await teslaFleetService.disableTelemetry()
    await loadPairingStatus()
  } catch (e: any) {
    pairingError.value = e.response?.data?.message || t('tesla.pairing_err_disable')
  } finally {
    pairingLoading.value = false
  }
}

async function loadCars() {
  try {
    cars.value = await carStore.getCars()
    if (props.forcedCarId) {
      // Tile-context: the car is fixed by the surrounding picker.
      selectedCarId.value = props.forcedCarId
    } else if (cars.value.length > 0) {
      selectedCarId.value = cars.value[0].id
    }
  } catch { /* ignore */ } finally {
    carsLoaded.value = true
  }
}

async function handleConnect() {
  if (!selectedCarId.value) { error.value = t('tesla.err_no_car'); return }
  isLoading.value = true
  error.value = null
  try {
    const authStart = await teslaFleetService.getAuthStartUrl(selectedCarId.value)
    if (!authStart.fleetApiConfigured) {
      error.value = t('tesla.err_fleet_api')
      fleetApiConfigured.value = false
      return
    }
    if (authStart.authUrl) window.location.href = authStart.authUrl
  } catch (e: any) {
    error.value = e.response?.data?.message || t('tesla.err_connect')
  } finally {
    isLoading.value = false
  }
}

async function handleSyncHistory() {
  isLoading.value = true
  error.value = null
  syncResult.value = null
  try {
    syncResult.value = await teslaFleetService.syncHistory()
    if (syncResult.value.importedLogIds?.length > 0) {
      lastImportedIds.value = syncResult.value.importedLogIds
    }
    await loadStatus()
    if (status.value.geocodingInProgress) {
      startGeocodingPoll()
    }
  } catch (e: any) {
    error.value = e.response?.data?.message || t('tesla.err_sync')
  } finally {
    isLoading.value = false
  }
}

async function handleUpdateSettings() {
  try {
    await teslaFleetService.updateSettings(suspendMinutesInput.value)
    status.value.suspendAfterIdleMinutes = suspendMinutesInput.value
    settingsSaved.value = true
    setTimeout(() => { settingsSaved.value = false }, 2000)
  } catch { error.value = t('tesla.err_settings') }
}

async function handleDisconnect() {
  if (!confirmDisconnect.value) { confirmDisconnect.value = true; return }
  confirmDisconnect.value = false
  try {
    await teslaFleetService.disconnect()
    status.value = { connected: false, vehicleName: null, carId: null, lastSyncAt: null, autoImportEnabled: false, geocodingInProgress: false, vehicleState: null, suspendAfterIdleMinutes: 15 }
    syncResult.value = null; success.value = null
  } catch { error.value = t('tesla.err_disconnect') }
}

async function handleUndoLastImport() {
  if (lastImportedIds.value.length === 0) return
  isLoading.value = true
  error.value = null
  try {
    await teslaFleetService.deleteByIds(lastImportedIds.value)
    lastImportedIds.value = []
    syncResult.value = null
    success.value = t('tesla.success_undo')
  } catch { error.value = t('tesla.err_undo') } finally {
    isLoading.value = false
  }
}

async function handleDeleteAllImports() {
  deleteAllError.value = null
  deleteAllLoading.value = true
  try {
    await teslaFleetService.deleteAllImports()
    showDeleteAllConfirm.value = false
    lastImportedIds.value = []
    syncResult.value = null
    success.value = t('tesla.success_delete_all')
  } catch (e: any) {
    deleteAllError.value = e.response?.data?.error || t('tesla.err_delete_all')
  } finally {
    deleteAllLoading.value = false
  }
}



function closeCallbackError() {
  callbackErrorCode.value = null
}

async function retryConnect() {
  closeCallbackError()
  await handleConnect()
}
</script>

<template>
  <div class="space-y-4">

    <!-- Premium-AutoSync hero (embedded mode only). Replaces the standalone header,
         the OAuth-flavoured info-box, and the Kaffee-Disclaimer with one cohesive intro. -->
    <div v-if="props.embedded && !status.connected" class="rounded-xl bg-gradient-to-br from-gray-50 to-white dark:from-slate-800/60 dark:to-slate-900/40 border border-gray-200/70 dark:border-slate-700/60 p-5">
      <div class="flex items-start gap-3">
        <div class="shrink-0 bg-gray-900 rounded-lg p-2.5">
          <svg class="h-5 w-5 text-white" viewBox="0 0 24 24" fill="currentColor">
            <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 14H9V8h2v8zm4 0h-2V8h2v8z"/>
          </svg>
        </div>
        <div class="flex-1 min-w-0">
          <h3 class="font-bold text-base text-gray-900 dark:text-gray-100 leading-tight">{{ t('imports.autosync_tesla_section_title') }}</h3>
          <p class="text-sm text-gray-600 dark:text-gray-400 mt-1">{{ t('imports.autosync_tesla_section_desc') }}</p>
        </div>
      </div>
      <ul class="mt-4 space-y-2">
        <li class="flex items-start gap-2 text-sm text-gray-700 dark:text-gray-300">
          <CheckCircleIcon class="h-4 w-4 text-green-600 dark:text-green-400 mt-0.5 shrink-0" />
          <span>{{ t('imports.autosync_tesla_feat1') }}</span>
        </li>
        <li class="flex items-start gap-2 text-sm text-gray-700 dark:text-gray-300">
          <CheckCircleIcon class="h-4 w-4 text-green-600 dark:text-green-400 mt-0.5 shrink-0" />
          <span>{{ t('imports.autosync_tesla_feat2') }}</span>
        </li>
        <li class="flex items-start gap-2 text-sm text-gray-700 dark:text-gray-300">
          <CheckCircleIcon class="h-4 w-4 text-green-600 dark:text-green-400 mt-0.5 shrink-0" />
          <span>{{ t('imports.autosync_tesla_feat3') }}</span>
        </li>
      </ul>
    </div>

    <div v-if="success" class="flex items-start gap-2 bg-green-50 dark:bg-green-900/30 border border-green-200 dark:border-green-700 rounded-lg p-3">
      <CheckCircleIcon class="h-4 w-4 text-green-600 dark:text-green-400 mt-0.5 shrink-0" />
      <p class="text-sm text-green-800 dark:text-green-200">{{ success }}</p>
    </div>
    <div v-if="error" class="flex items-start gap-2 bg-red-50 dark:bg-red-900/30 border border-red-200 dark:border-red-700 rounded-lg p-3">
      <ExclamationTriangleIcon class="h-4 w-4 text-red-500 dark:text-red-400 mt-0.5 shrink-0" />
      <p class="text-sm text-red-800 dark:text-red-200">{{ error }}</p>
    </div>

    <template v-if="!status.connected">
      <!-- Standalone-Tab info-box. Hidden in embedded mode (the AutoSync hero already
           explains what the user gets - this OAuth/polling-flavoured list would conflict). -->
      <div v-if="!props.embedded" class="bg-blue-50 dark:bg-blue-900/30 border border-blue-200 dark:border-blue-700 rounded-lg p-3 text-sm text-blue-800 dark:text-blue-200 space-y-1">
        <p class="font-medium">{{ t('tesla.sync_info_title') }}</p>
        <ul class="list-disc list-inside space-y-0.5 text-blue-700 dark:text-blue-300">
          <li>{{ t('tesla.sync_item1') }}</li>
          <li>{{ t('tesla.sync_item2') }}</li>
          <li>{{ t('tesla.sync_item3') }}</li>
          <li>{{ t('tesla.sync_item4') }}</li>
          <li>{{ t('tesla.sync_item5') }}</li>
        </ul>
        <p class="text-xs text-blue-600 dark:text-blue-400 mt-2">{{ t('tesla.sync_note') }}</p>
      </div>
      <p v-if="!props.embedded" class="text-sm text-gray-600 dark:text-gray-400">{{ t('tesla.connect_desc') }}</p>
      <div v-if="cars.length > 1 && !props.forcedCarId">
        <label class="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">{{ t('tesla.select_car_label') }}</label>
        <CarSelectDropdown :cars="cars" v-model="selectedCarId" />
      </div>
      <p v-if="carsLoaded && cars.length === 0" class="text-xs text-amber-700 dark:text-amber-300 bg-amber-50 dark:bg-amber-900/30 border border-amber-200 dark:border-amber-700 rounded-lg p-2">
        {{ t('tesla.no_car_hint') }}
      </p>
      <button
        @click="handleConnect"
        :disabled="isLoading || !fleetApiConfigured || cars.length === 0"
        :class="[
          'btn-3d w-full flex items-center justify-center gap-2 px-4 py-2.5 rounded-lg text-sm font-semibold transition disabled:opacity-50 text-white',
          props.embedded
            ? 'bg-green-600 hover:bg-green-500 active:translate-y-1 active:shadow-none shadow-[0_4px_0_0_#166534]'
            : 'bg-gray-900 dark:bg-gray-600 hover:bg-gray-800 dark:hover:bg-gray-500',
        ]"
      >
        <ArrowTopRightOnSquareIcon class="h-4 w-4" />
        {{ isLoading ? t('tesla.connect_btn_loading') : t('tesla.connect_btn') }}
      </button>
      <p v-if="!props.embedded" class="text-xs text-gray-500 dark:text-gray-400 text-center">
        Die Tesla Fleet API ist gebührenpflichtig - über einen Kaffee würde ich mich freuen ☕
      </p>
    </template>

    <template v-else>
      <!-- Loading-Skeleton solange pairingStatus noch nicht da ist - verhindert Flicker zwischen
           Polling-Banner und Telemetry-Card auf langsamen Verbindungen. -->
      <div v-if="!pairingStatusLoaded" class="bg-gray-50 dark:bg-slate-900/80 border border-gray-200 dark:border-slate-800 rounded-xl p-4 pl-5 animate-pulse">
        <div class="h-4 bg-gray-200 dark:bg-slate-800 rounded w-2/3"></div>
        <div class="h-3 bg-gray-200 dark:bg-slate-800 rounded w-1/3 mt-2"></div>
        <div class="h-3 bg-gray-200 dark:bg-slate-800 rounded w-full mt-3"></div>
      </div>


      <!-- ── TELEMETRY ACTIVE MODE: Status-Card mit linker Akzent-Leiste ─── -->
      <template v-if="pairingStatusLoaded && isTelemetryActive">
        <div class="relative bg-gray-50 dark:bg-slate-900/80 border border-gray-200 dark:border-slate-800 rounded-xl p-4 pl-5 overflow-hidden">
          <span class="absolute left-0 top-0 bottom-0 w-1 bg-emerald-500"></span>
          <div class="flex items-start gap-3">
            <CheckCircleIcon class="h-5 w-5 text-emerald-500 dark:text-emerald-400 shrink-0 mt-0.5" />
            <div class="flex-1 min-w-0">
              <p class="text-sm font-semibold text-gray-900 dark:text-white">
                {{ t('tesla.telemetry_live_title') }} · {{ status.vehicleName || 'Tesla' }}
              </p>
              <p v-if="pairingStatus?.vin" class="text-[11px] text-gray-500 dark:text-slate-500 mt-0.5 font-mono truncate">{{ pairingStatus.vin }}</p>
              <p class="text-xs text-gray-700 dark:text-slate-300 mt-2">
                {{ isFullProfile ? t('tesla.telemetry_live_desc_full') : t('tesla.telemetry_live_desc_charging_only') }}
              </p>
            </div>
            <div class="flex items-center gap-1 shrink-0">
              <button
                @click="loadPairingStatus"
                :disabled="pairingLoading"
                :title="t('tesla.pairing_refresh')"
                :aria-label="t('tesla.pairing_refresh')"
                class="p-1.5 rounded-md text-gray-500 dark:text-slate-400 hover:bg-gray-200 dark:hover:bg-slate-800 transition disabled:opacity-50"
              >
                <ArrowPathIcon class="h-4 w-4" :class="{ 'animate-spin': pairingLoading }" />
              </button>
              <button
                @click="handleDisconnect"
                :title="confirmDisconnect ? t('tesla.confirm_disconnect') : t('tesla.disconnect_btn')"
                :aria-label="t('tesla.disconnect_btn')"
                class="p-1.5 rounded-md transition"
                :class="confirmDisconnect ? 'text-red-700 bg-red-100 dark:bg-red-900/40' : 'text-gray-500 dark:text-slate-400 hover:bg-gray-200 dark:hover:bg-slate-800'"
              >
                <XMarkIcon class="h-4 w-4" />
              </button>
            </div>
          </div>

          <div class="mt-3 pt-3 border-t border-gray-200 dark:border-slate-800 flex justify-end">
            <button
              @click="handleDisableTelemetry"
              :disabled="pairingLoading"
              class="text-xs text-gray-500 dark:text-slate-400 hover:text-gray-800 dark:hover:text-slate-200 underline-offset-2 hover:underline transition disabled:opacity-50"
            >
              {{ pairingLoading ? t('tesla.pairing_disable_btn_loading') : t('tesla.pairing_disable_btn') }}
            </button>
          </div>

          <div v-if="pairingError" class="mt-3 flex items-start gap-2 bg-red-50 dark:bg-red-900/30 border border-red-200 dark:border-red-700 rounded-lg p-2">
            <ExclamationTriangleIcon class="h-4 w-4 text-red-500 dark:text-red-400 mt-0.5 shrink-0" />
            <p class="text-xs text-red-800 dark:text-red-200">{{ pairingError }}</p>
          </div>
        </div>

        <!-- ── DANGER ZONE: gleiche Card mit roter Akzent-Leiste ────────── -->
        <div class="relative bg-gray-50 dark:bg-slate-900/80 border border-gray-200 dark:border-slate-800 rounded-xl p-4 pl-5 mt-4 overflow-hidden">
          <span class="absolute left-0 top-0 bottom-0 w-1 bg-red-500"></span>
          <div class="flex items-start gap-3">
            <ExclamationTriangleIcon class="h-5 w-5 text-red-500 dark:text-red-400 shrink-0 mt-0.5" />
            <div class="flex-1 min-w-0">
              <p class="text-sm font-semibold text-gray-900 dark:text-white">{{ t('tesla.danger_zone_title') }}</p>
              <p class="text-xs text-gray-600 dark:text-slate-400 mt-1">{{ t('tesla.danger_zone_desc') }}</p>
            </div>
          </div>
          <div class="mt-3 flex justify-end">
            <button
              @click="showDeleteAllConfirm = true"
              class="bg-red-600 hover:bg-red-700 text-white text-sm font-medium px-4 py-2 rounded-lg transition"
            >
              {{ t('tesla.delete_all_btn') }}
            </button>
          </div>
        </div>
      </template>

      <!-- ── POLLING MODE ────────────────────────────────────────────────── -->
      <template v-else-if="pairingStatusLoaded">
        <!-- Fleet Telemetry setup - primary CTA -->
        <div v-if="authStore.canActivateTelemetry" class="space-y-2">
          <p class="text-xs font-semibold text-gray-900 dark:text-white text-center">
            {{ t('tesla.pairing_title') }}
            <span v-if="pairingRoleBadge" class="ml-1 text-[10px] uppercase bg-amber-500/15 text-amber-700 dark:text-amber-300 px-1.5 py-0.5 rounded">{{ pairingRoleBadge }}</span>
          </p>
          <div v-if="pairingStatus" class="bg-white dark:bg-slate-950/60 border border-gray-200 dark:border-slate-800 rounded-lg p-2 text-xs space-y-1">
            <div class="flex items-center justify-between">
              <span class="text-gray-600 dark:text-slate-400">{{ t('tesla.pairing_key_label') }}</span>
              <span :class="pairingStatus.keyPaired ? 'text-emerald-600 dark:text-emerald-400' : 'text-amber-600 dark:text-amber-400'" class="font-medium">
                {{ pairingStatus.keyPaired ? t('tesla.pairing_key_ok') : t('tesla.pairing_key_missing') }}
              </span>
            </div>
            <details v-if="!pairingStatus.keyPaired" class="pt-0.5">
              <summary class="text-xs text-gray-500 dark:text-slate-500 cursor-pointer">Hinweis zum Status-Flag</summary>
              <p class="text-xs text-gray-500 dark:text-slate-500 mt-1">{{ t('tesla.pairing_key_unreliable_hint') }}</p>
            </details>
            <div class="flex items-center justify-between">
              <span class="text-gray-600 dark:text-slate-400">{{ t('tesla.pairing_config_label') }}</span>
              <span :class="pairingStatus.telemetryConfigPushed ? 'text-emerald-600 dark:text-emerald-400' : 'text-gray-500 dark:text-slate-500'" class="font-medium">
                {{ pairingStatus.telemetryConfigPushed ? t('tesla.pairing_config_ok') : t('tesla.pairing_config_missing') }}
              </span>
            </div>
          </div>
          <div v-else-if="pairingLoading" class="flex items-center justify-center py-2">
            <ArrowPathIcon class="h-4 w-4 text-amber-500 animate-spin" />
          </div>

          <a
            v-if="!pairingStatus?.keyPaired"
            href="https://tesla.com/_ak/ev-monitor.net"
            target="_blank"
            rel="noopener"
            class="btn-3d w-full flex items-center justify-center gap-2 text-sm px-4 py-2 rounded-lg bg-amber-500 hover:bg-amber-400 active:translate-y-1 active:shadow-none shadow-[0_4px_0_0_#92400e] text-white font-medium transition"
          >
            <ArrowTopRightOnSquareIcon class="h-4 w-4" />
            {{ t('tesla.pairing_open_app_btn') }}
          </a>

          <button
            v-if="pairingStatus"
            @click="handleEnableTelemetry"
            :disabled="pairingLoading"
            class="btn-3d w-full flex items-center justify-center gap-2 bg-gray-900 dark:bg-slate-700 hover:bg-gray-800 dark:hover:bg-slate-600 text-white px-4 py-2 rounded-lg text-sm font-medium transition disabled:opacity-50"
          >
            <ArrowPathIcon class="h-4 w-4" :class="{ 'animate-spin': pairingLoading }" />
            {{ pairingLoading ? t('tesla.pairing_enable_btn_loading') : t('tesla.pairing_enable_btn') }}
          </button>

          <button
            @click="loadPairingStatus"
            :disabled="pairingLoading"
            class="w-full text-xs text-gray-500 dark:text-slate-400 hover:text-gray-700 dark:hover:text-slate-200 text-center transition disabled:opacity-50"
          >
            {{ t('tesla.pairing_refresh') }}
          </button>

          <div v-if="pairingError" class="flex items-center justify-center gap-2">
            <ExclamationTriangleIcon class="h-4 w-4 text-red-500 dark:text-red-400 shrink-0" />
            <p class="text-xs text-red-700 dark:text-red-300">{{ pairingError }}</p>
          </div>
        </div>

        <!-- AutoSync upsell - for plain users without premium / privileged role.
             Solid slate card with purple accent edge. -->
        <div v-if="!authStore.canActivateTelemetry" class="relative bg-gray-50 dark:bg-slate-900/80 border border-gray-200 dark:border-slate-800 rounded-xl p-4 pl-5 overflow-hidden space-y-2">
          <span class="absolute left-0 top-0 bottom-0 w-1 bg-purple-500"></span>
          <p class="text-xs font-semibold text-gray-900 dark:text-white">{{ t('tesla.upsell_title') }}</p>
          <p class="text-xs text-gray-600 dark:text-slate-400">{{ t('tesla.upsell_desc') }}</p>
          <RouterLink to="/premium" class="btn-3d w-full flex items-center justify-center gap-2 bg-purple-600 hover:bg-purple-500 active:translate-y-1 active:shadow-none shadow-[0_4px_0_0_#581c87] text-white px-4 py-2 rounded-lg text-sm font-medium transition">
            {{ t('tesla.upsell_cta') }}
          </RouterLink>
        </div>

        <!-- Legacy polling controls: Info-Box, History-Sync, Sleep-Window-Slider,
             Undo, Delete-All. Im AutoSync-Tile (embedded) komplett ausgeblendet,
             weil Telemetry der einzige Weg ist und Polling-UI nur verwirrt. -->
        <template v-if="!props.embedded">
          <!-- Collapsible info box -->
          <details class="group border border-gray-200 dark:border-slate-700/60 rounded-xl overflow-hidden bg-white dark:bg-slate-800/50">
            <summary class="flex items-center justify-between px-4 py-3 cursor-pointer text-sm font-medium text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-slate-700/40 list-none select-none">
              {{ t('tesla.sync_info_title') }}
              <ChevronDownIcon class="h-4 w-4 text-slate-400 transition-transform group-open:rotate-180 shrink-0" />
            </summary>
            <div class="px-4 pb-3 pt-1 border-t border-gray-100 dark:border-slate-700/40">
              <ul class="list-disc list-inside space-y-0.5 text-sm text-gray-600 dark:text-slate-400">
                <li>{{ t('tesla.sync_item1') }}</li>
                <li>{{ t('tesla.sync_item2') }}</li>
                <li>{{ t('tesla.sync_item3') }}</li>
                <li>{{ t('tesla.sync_item4') }}</li>
                <li>{{ t('tesla.sync_item5') }}</li>
              </ul>
              <p class="text-xs text-slate-500 dark:text-slate-500 mt-2">{{ t('tesla.sync_note') }}</p>
            </div>
          </details>

          <div v-if="syncResult" class="bg-gray-50 dark:bg-slate-900/60 rounded-lg p-3 text-sm">
            <p class="font-medium text-gray-800 dark:text-gray-200">{{ syncResult.message }}</p>
            <p v-if="syncResult.logsSkipped > 0" class="text-slate-500 dark:text-slate-400 text-xs mt-1">{{ t('tesla.skipped', { n: syncResult.logsSkipped }) }}</p>
          </div>
          <div v-if="status.geocodingInProgress" class="flex items-center gap-2 bg-blue-50 dark:bg-blue-900/30 border border-blue-200 dark:border-blue-700 rounded-lg p-3">
            <ArrowPathIcon class="h-4 w-4 text-blue-500 dark:text-blue-400 animate-spin shrink-0" />
            <p class="text-sm text-blue-800 dark:text-blue-200">{{ t('tesla.geocoding') }}</p>
          </div>

          <div class="flex gap-2">
            <button
              @click="handleSyncHistory"
              :disabled="isLoading"
              class="btn-3d flex-1 flex items-center justify-center gap-2 bg-gray-900 dark:bg-slate-700 text-white px-4 py-2.5 rounded-lg text-sm font-medium hover:bg-gray-800 dark:hover:bg-slate-600 transition disabled:opacity-50"
            >
              <ArrowPathIcon class="h-4 w-4" :class="{ 'animate-spin': isLoading }" />
              {{ isLoading ? t('tesla.sync_btn_loading') : t('tesla.sync_btn') }}
            </button>
            <button
              @click="handleDisconnect"
              class="shrink-0 px-4 py-2.5 rounded-lg border border-red-300 dark:border-red-700/60 text-red-600 dark:text-red-400 text-sm font-medium transition"
              :class="confirmDisconnect ? 'bg-red-50 dark:bg-red-900/20' : 'hover:bg-red-50 dark:hover:bg-red-900/20'"
            >
              {{ confirmDisconnect ? t('tesla.confirm_disconnect') : t('tesla.disconnect_btn') }}
            </button>
          </div>
          <p class="text-xs text-slate-500 dark:text-slate-500 text-center">{{ t('tesla.auto_import_hint') }}</p>

          <!-- Sleep-Window Setting -->
          <div class="border-t border-gray-100 dark:border-slate-700/40 pt-4 space-y-2">
            <p class="text-xs font-medium text-gray-700 dark:text-slate-300">{{ t('tesla.realtime_title') }}</p>
            <p class="text-xs text-slate-500 dark:text-slate-400" v-html="t('tesla.realtime_desc', { minutes: status.suspendAfterIdleMinutes })" />
            <div class="flex items-center gap-2">
              <input v-model.number="suspendMinutesInput" type="range" min="5" max="60" step="5" class="flex-1 accent-gray-900 dark:accent-slate-400" />
              <span class="text-sm font-medium text-gray-700 dark:text-slate-300 w-16 text-right">{{ suspendMinutesInput }} Min</span>
              <button
                @click="handleUpdateSettings"
                :disabled="suspendMinutesInput === status.suspendAfterIdleMinutes"
                class="text-xs px-2.5 py-1 rounded-lg bg-gray-900 dark:bg-slate-600 text-white disabled:opacity-40 transition"
              >
                {{ settingsSaved ? t('tesla.settings_saved') : t('tesla.settings_save') }}
              </button>
            </div>
            <p class="text-xs text-amber-700 dark:text-amber-300 bg-amber-50 dark:bg-amber-900/30 border border-amber-200 dark:border-amber-700 rounded-lg p-2" v-html="t('tesla.sleep_warning')" />
          </div>

          <!-- Undo last import -->
          <div v-if="lastImportedIds.length > 0" class="border-t border-gray-100 dark:border-slate-700/40 pt-4">
            <button
              @click="handleUndoLastImport"
              :disabled="isLoading"
              class="w-full flex items-center justify-center gap-2 text-sm px-4 py-2 rounded-lg border border-amber-300 dark:border-amber-700 text-amber-700 dark:text-amber-300 hover:bg-amber-50 dark:hover:bg-amber-900/20 transition disabled:opacity-50"
            >
              <ArrowPathIcon class="h-4 w-4" />
              {{ t('tesla.undo_btn', { n: lastImportedIds.length }) }}
            </button>
          </div>

          <!-- Delete all -->
          <div class="border-t border-gray-100 dark:border-slate-700/40 pt-4">
            <button @click="showDeleteAllConfirm = true" class="w-full text-sm px-4 py-2 rounded-lg border border-red-300 dark:border-red-700/60 text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20 transition">
              {{ t('tesla.delete_all_btn') }}
            </button>
          </div>
        </template>
      </template>
    </template>
  </div>

  <!-- Tesla callback error modal -->
  <div
    v-if="callbackErrorCode"
    role="dialog"
    aria-modal="true"
    aria-labelledby="callback-error-title"
    class="fixed inset-0 flex items-center justify-center z-50 p-4"
    style="backdrop-filter: blur(8px); background-color: rgba(0, 0, 0, 0.3);"
    @click.self="closeCallbackError"
  >
    <div class="bg-white dark:bg-gray-800 rounded-xl shadow-2xl max-w-md w-full p-6 space-y-4" @click.stop>
      <div class="flex flex-col items-center gap-2 text-center">
        <ExclamationTriangleIcon class="w-8 h-8 text-amber-500" />
        <h3 id="callback-error-title" class="text-xl font-bold text-gray-900 dark:text-white">
          {{ callbackErrorCode === 'VIN_ALREADY_LINKED' ? t('tesla.callback_error_vin_linked_title') : t('tesla.callback_error_unknown_title') }}
        </h3>
      </div>
      <p class="text-gray-700 dark:text-gray-300 text-sm text-center">
        {{ callbackErrorCode === 'VIN_ALREADY_LINKED' ? t('tesla.callback_error_vin_linked_body') : t('tesla.callback_error_unknown_body') }}
      </p>
      <div class="flex gap-3">
        <button
          @click="closeCallbackError"
          class="flex-1 px-4 py-3 bg-gray-200 dark:bg-gray-700 text-gray-800 dark:text-gray-200 font-semibold rounded-lg hover:bg-gray-300 dark:hover:bg-gray-600 transition"
        >
          {{ t('tesla.callback_error_close') }}
        </button>
        <button
          v-if="callbackErrorCode !== 'VIN_ALREADY_LINKED'"
          @click="retryConnect"
          class="flex-1 px-4 py-3 bg-gray-900 dark:bg-white text-white dark:text-gray-900 font-semibold rounded-lg hover:opacity-90 transition"
        >
          {{ t('tesla.callback_error_retry') }}
        </button>
      </div>
    </div>
  </div>

  <!-- Delete all confirmation modal -->
  <div
    v-if="showDeleteAllConfirm"
    role="dialog"
    aria-modal="true"
    aria-labelledby="delete-all-title"
    class="fixed inset-0 flex items-center justify-center z-50 p-4"
    style="backdrop-filter: blur(8px); background-color: rgba(0, 0, 0, 0.3);"
    @click.self="showDeleteAllConfirm = false"
  >
    <div class="bg-white dark:bg-gray-800 rounded-xl shadow-2xl max-w-md w-full p-6 space-y-4" @click.stop>
      <div class="flex flex-col items-center gap-2 text-center">
        <ExclamationTriangleIcon class="w-8 h-8 text-red-600" />
        <h3 id="delete-all-title" class="text-xl font-bold text-red-600">{{ t('tesla.delete_modal_title') }}</h3>
      </div>
      <p class="text-gray-700 dark:text-gray-300 text-sm" v-html="t('tesla.delete_modal_desc')" />
      <div v-if="deleteAllError" class="p-3 bg-red-100 dark:bg-red-900/30 text-red-800 dark:text-red-200 rounded-lg border border-red-300 dark:border-red-700 text-sm">
        <ExclamationTriangleIcon class="w-4 h-4 inline-block mr-1" />
        {{ deleteAllError }}
      </div>
      <div class="flex gap-3">
        <button
          @click="showDeleteAllConfirm = false"
          :disabled="deleteAllLoading"
          class="flex-1 px-4 py-3 bg-gray-200 dark:bg-gray-700 text-gray-800 dark:text-gray-200 font-semibold rounded-lg hover:bg-gray-300 dark:hover:bg-gray-600 transition disabled:opacity-50"
        >
          {{ t('tesla.delete_modal_cancel') }}
        </button>
        <button
          @click="handleDeleteAllImports"
          :disabled="deleteAllLoading"
          class="flex-1 px-4 py-3 bg-red-600 text-white font-semibold rounded-lg hover:bg-red-700 transition disabled:opacity-50 flex items-center justify-center gap-2"
        >
          <svg v-if="deleteAllLoading" class="animate-spin h-5 w-5 text-white flex-shrink-0" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
            <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
            <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
          </svg>
          {{ deleteAllLoading ? t('tesla.delete_modal_btn_loading') : t('tesla.delete_modal_btn') }}
        </button>
      </div>
    </div>
  </div>
</template>
