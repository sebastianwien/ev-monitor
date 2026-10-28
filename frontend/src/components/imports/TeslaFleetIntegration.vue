<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import {
  ArrowTopRightOnSquareIcon, ArrowPathIcon, ArrowUturnLeftIcon, ExclamationTriangleIcon, ExclamationCircleIcon, CheckCircleIcon, ChevronDownIcon,
} from '@heroicons/vue/24/outline'
import BottomSheet from '../shared/BottomSheet.vue'
import Collapse from '../shared/Collapse.vue'
import { CI_BTN_PRIMARY, CI_BTN_SECONDARY, CI_BTN_DANGER, CI_ICON_BTN, CI_LABEL, CI_CARD, CI_NOTE } from '@/utils/ciClasses'
import teslaFleetService, { type TeslaConnectionStatus, type TeslaFleetSyncResult, type TeslaLatestImport } from '@/api/teslaFleetService'
import type { Car } from '@/api/carService'
import { useCarStore } from '@/stores/car'
import { useTeslaPairing } from '@/composables/useTeslaPairing'
import { analytics } from '@/services/analytics'
import CarSelectDropdown from '../car/CarSelectDropdown.vue'
import TeslaTelemetryExplainer from './TeslaTelemetryExplainer.vue'
import TeslaPairingStep from './TeslaPairingStep.vue'

const emit = defineEmits<{
  (e: 'connected-label', label: string | null): void
}>()

const route = useRoute()
const router = useRouter()
const { t, locale } = useI18n()
const callbackErrorCode = ref<string | null>(null)

const status = ref<TeslaConnectionStatus>({ connected: false, vehicleName: null, carId: null, lastSyncAt: null, autoImportEnabled: false, geocodingInProgress: false, vehicleState: null })
const isLoading = ref(false)
const syncResult = ref<TeslaFleetSyncResult | null>(null)
const error = ref<string | null>(null)
const success = ref<string | null>(null)
const confirmDisconnect = ref(false)
const syncInfoOpen = ref(false)
const lastImportedIds = ref<string[]>([])
const showDeleteAllConfirm = ref(false)
const deleteAllLoading = ref(false)
const deleteAllError = ref<string | null>(null)
const fleetApiConfigured = ref(true)
const carStore = useCarStore()

const {
  pairingStatus, pairingStatusLoaded, pairingLoading, pairingError,
  isTelemetryActive, isFullProfile,
  loadPairingStatus, enableTelemetry, disableTelemetry,
} = useTeslaPairing(() => status.value.connected)

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
  if (status.value.connected) loadLatestImport()
  if (route.query['tesla-connected']) {
    success.value = t('tesla.success_connected')
    await loadStatus()
    analytics.trackConnectorActivated('tesla')
  }
  if (route.query['tesla-error']) {
    callbackErrorCode.value = String(route.query['tesla-error'])
    router.replace({ query: { ...route.query, 'tesla-error': undefined } })
  }
})

const latestImport = ref<TeslaLatestImport | null>(null)

async function loadLatestImport() {
  try {
    latestImport.value = await teslaFleetService.getLatestImport()
  } catch { /* rein informativ */ }
}

function formatImportTime(iso: string | null): string {
  if (!iso) return t('tesla.latest_import_none')
  const loc = ({ en: 'en-GB', nb: 'nb-NO', sv: 'sv-SE' } as Record<string, string>)[locale.value] ?? 'de-DE'
  return new Date(iso).toLocaleString(loc, { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' })
}

function refreshTelemetryCard() {
  loadPairingStatus()
  loadLatestImport()
}

async function loadStatus() {
  try {
    status.value = await teslaFleetService.getStatus()
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

async function loadCars() {
  try {
    cars.value = await carStore.getCars()
    if (cars.value.length > 0) {
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
    const result = await teslaFleetService.startReconnect(selectedCarId.value)
    if (result === 'not_configured') {
      error.value = t('tesla.err_fleet_api')
      fleetApiConfigured.value = false
    }
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

async function handleDisconnect() {
  if (!confirmDisconnect.value) { confirmDisconnect.value = true; return }
  confirmDisconnect.value = false
  try {
    await teslaFleetService.disconnect()
    status.value = { connected: false, vehicleName: null, carId: null, lastSyncAt: null, autoImportEnabled: false, geocodingInProgress: false, vehicleState: null }
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
  <div class="grid gap-4">

    <p v-if="success" role="status" :class="[CI_NOTE, 'flex items-start gap-2 py-3 font-semibold text-gray-900 dark:text-gray-100']">
      <CheckCircleIcon class="h-5 w-5 shrink-0 text-green-700 dark:text-green-400" aria-hidden="true" />{{ success }}
    </p>
    <p v-if="error" role="alert" :class="[CI_NOTE, 'flex items-start gap-2 py-3 font-semibold text-gray-900 dark:text-gray-100']">
      <ExclamationCircleIcon class="h-5 w-5 shrink-0 text-gray-700 dark:text-gray-300" aria-hidden="true" />{{ error }}
    </p>

    <template v-if="!status.connected">
      <TeslaTelemetryExplainer />
      <p class="text-[15px] leading-relaxed text-gray-700 dark:text-gray-300">{{ t('tesla.connect_desc') }}</p>
      <div v-if="cars.length > 1" class="grid gap-1.5">
        <label :class="CI_LABEL">{{ t('tesla.select_car_label') }}</label>
        <CarSelectDropdown :cars="cars" v-model="selectedCarId" />
      </div>
      <!-- Ohne Auto fehlt etwas: Amber -->
      <p v-if="carsLoaded && cars.length === 0" class="rounded-2xl bg-amber-50 p-4 text-sm text-gray-800 dark:bg-amber-900/20 dark:text-gray-100">
        {{ t('tesla.no_car_hint') }}
      </p>
      <button type="button" :class="[CI_BTN_PRIMARY, 'w-full sm:w-auto sm:justify-self-start']"
        :disabled="isLoading || !fleetApiConfigured || cars.length === 0" @click="handleConnect">
        <ArrowTopRightOnSquareIcon class="h-5 w-5" aria-hidden="true" />
        {{ isLoading ? t('tesla.connect_btn_loading') : t('tesla.connect_btn') }}
      </button>
    </template>

    <template v-else>
      <!-- Loading-Skeleton solange pairingStatus noch nicht da ist - verhindert Flicker zwischen
           Polling-Banner und Telemetry-Card auf langsamen Verbindungen. -->
      <div v-if="!pairingStatusLoaded" :class="[CI_CARD, 'animate-pulse']">
        <div class="h-4 w-2/3 rounded bg-gray-200 dark:bg-gray-600"></div>
        <div class="mt-2 h-3 w-1/3 rounded bg-gray-200 dark:bg-gray-600"></div>
        <div class="mt-3 h-3 w-full rounded bg-gray-200 dark:bg-gray-600"></div>
      </div>

      <!-- ── TELEMETRY ACTIVE MODE ──────────────────────────────────────── -->
      <template v-if="pairingStatusLoaded && isTelemetryActive">
        <div :class="[CI_CARD, 'grid gap-2']">
          <p :class="[CI_LABEL, 'flex items-center gap-2']">
            <span class="h-2.5 w-2.5 rounded-full bg-green-600 dark:bg-green-400" aria-hidden="true"></span>
            Telemetry aktiv
          </p>
          <p v-if="pairingStatus?.vin" class="truncate font-mono text-[12px] text-gray-500 dark:text-gray-400">{{ pairingStatus.vin }}</p>
          <p class="text-[15px] leading-relaxed text-gray-700 dark:text-gray-200">
            {{ isFullProfile ? t('tesla.telemetry_live_desc_full') : t('tesla.telemetry_live_desc_charging_only') }}
          </p>

          <dl v-if="latestImport" class="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1 text-[13px]">
            <dt class="text-gray-500 dark:text-gray-400">{{ t('tesla.latest_import_charge') }}</dt>
            <dd class="font-semibold tabular-nums text-gray-900 dark:text-gray-100">{{ formatImportTime(latestImport.lastChargeAt) }}</dd>
            <template v-if="isFullProfile">
              <dt class="text-gray-500 dark:text-gray-400">{{ t('tesla.latest_import_trip') }}</dt>
              <dd class="font-semibold tabular-nums text-gray-900 dark:text-gray-100">{{ formatImportTime(latestImport.lastTripAt) }}</dd>
            </template>
          </dl>

          <div class="mt-2 flex flex-wrap items-center justify-end gap-2 border-t border-gray-200 pt-3 dark:border-white/10">
            <button type="button" :class="CI_ICON_BTN" :disabled="pairingLoading"
              :title="t('tesla.pairing_refresh')" :aria-label="t('tesla.pairing_refresh')" @click="refreshTelemetryCard">
              <ArrowPathIcon class="h-5 w-5" :class="{ 'animate-spin': pairingLoading }" aria-hidden="true" />
            </button>
            <button type="button" :class="CI_BTN_SECONDARY" :disabled="pairingLoading" @click="disableTelemetry">
              {{ pairingLoading ? t('tesla.pairing_disable_btn_loading') : t('tesla.pairing_disable_btn') }}
            </button>
            <!-- Zweiter Klick bestätigt: erst dann rot -->
            <button type="button" :class="confirmDisconnect ? CI_BTN_DANGER : CI_BTN_SECONDARY" @click="handleDisconnect">
              {{ confirmDisconnect ? t('tesla.confirm_disconnect') : t('tesla.disconnect_btn') }}
            </button>
          </div>

          <p v-if="pairingError" role="alert" class="flex items-start gap-2 text-[13px] font-semibold text-gray-900 dark:text-gray-100">
            <ExclamationTriangleIcon class="h-5 w-5 shrink-0 text-gray-600 dark:text-gray-300" aria-hidden="true" />{{ pairingError }}
          </p>
        </div>

        <button type="button" class="inline-flex min-h-11 items-center justify-self-end text-sm font-semibold text-gray-600 underline underline-offset-2 hover:text-gray-900 dark:text-gray-400 dark:hover:text-gray-100"
          @click="showDeleteAllConfirm = true">
          {{ t('tesla.delete_all_link') }}
        </button>
      </template>

      <!-- ── POLLING MODE ────────────────────────────────────────────────── -->
      <template v-else-if="pairingStatusLoaded">
        <!-- Fleet Telemetry setup - primary CTA. Free for all Tesla owners. -->
        <TeslaPairingStep
          :status="pairingStatus"
          :loading="pairingLoading"
          :error="pairingError"
          @enable="enableTelemetry"
          @refresh="loadPairingStatus"
          @reconnect="handleConnect"
        />

        <!-- History-Sync controls: Info, "Ladehistorie importieren", Undo, Delete-All. -->
        <div class="rounded-2xl bg-gray-100 dark:bg-gray-800/60">
          <button type="button" :aria-expanded="syncInfoOpen"
            class="flex min-h-11 w-full items-center justify-between gap-3 px-4 py-3 text-left text-sm font-semibold text-gray-900 dark:text-gray-100"
            @click="syncInfoOpen = !syncInfoOpen">
            {{ t('tesla.sync_info_title') }}
            <ChevronDownIcon class="h-4 w-4 shrink-0 text-gray-500 transition-transform duration-300 motion-reduce:transition-none"
              :class="syncInfoOpen ? 'rotate-180 ease-out' : 'ease-in'" aria-hidden="true" />
          </button>
          <Collapse :open="syncInfoOpen">
            <div class="px-4 pb-4">
              <ul class="list-inside list-disc space-y-0.5 text-sm text-gray-700 dark:text-gray-300">
                <li>{{ t('tesla.sync_item1') }}</li>
                <li>{{ t('tesla.sync_item2') }}</li>
                <li>{{ t('tesla.sync_item3') }}</li>
                <li>{{ t('tesla.sync_item4') }}</li>
                <li>{{ t('tesla.sync_item5') }}</li>
                <li>{{ t('tesla.sync_item6') }}</li>
              </ul>
              <p class="mt-2 text-[13px] text-gray-500 dark:text-gray-400">{{ t('tesla.sync_note') }}</p>
            </div>
          </Collapse>
        </div>

        <div v-if="syncResult" role="status" :class="[CI_NOTE, 'py-3']">
          <p class="font-semibold text-gray-900 dark:text-gray-100">{{ syncResult.message }}</p>
          <p v-if="syncResult.logsSkipped > 0" class="mt-1 text-[13px] text-gray-500 dark:text-gray-400">{{ t('tesla.skipped', { n: syncResult.logsSkipped }) }}</p>
        </div>
        <p v-if="status.geocodingInProgress" role="status" :class="[CI_NOTE, 'flex items-center gap-2 py-3']">
          <ArrowPathIcon class="h-5 w-5 shrink-0 animate-spin text-gray-500 dark:text-gray-400" aria-hidden="true" />{{ t('tesla.geocoding') }}
        </p>

        <div class="flex flex-wrap gap-2">
          <button type="button" :class="[CI_BTN_SECONDARY, 'flex-1']" :disabled="isLoading" @click="handleSyncHistory">
            <ArrowPathIcon class="h-5 w-5" :class="{ 'animate-spin': isLoading }" aria-hidden="true" />
            {{ isLoading ? t('tesla.sync_btn_loading') : t('tesla.sync_btn') }}
          </button>
          <button type="button" :class="confirmDisconnect ? CI_BTN_DANGER : CI_BTN_SECONDARY" @click="handleDisconnect">
            {{ confirmDisconnect ? t('tesla.confirm_disconnect') : t('tesla.disconnect_btn') }}
          </button>
        </div>
        <p class="text-center text-[13px] text-gray-500 dark:text-gray-400">{{ t('tesla.auto_import_hint') }}</p>

        <!-- Undo last import / Delete all -->
        <div class="grid gap-2 border-t border-gray-200 pt-4 dark:border-white/10">
          <button v-if="lastImportedIds.length > 0" type="button" :class="[CI_BTN_SECONDARY, 'w-full']" :disabled="isLoading" @click="handleUndoLastImport">
            <ArrowUturnLeftIcon class="h-5 w-5" aria-hidden="true" />
            {{ t('tesla.undo_btn', { n: lastImportedIds.length }) }}
          </button>
          <button type="button" :class="[CI_BTN_SECONDARY, 'w-full']" @click="showDeleteAllConfirm = true">
            {{ t('tesla.delete_all_btn') }}
          </button>
        </div>
      </template>
    </template>
  </div>

  <!-- Tesla callback error -->
  <BottomSheet v-if="callbackErrorCode" edge
    :label="callbackErrorCode === 'VIN_ALREADY_LINKED' ? t('tesla.callback_error_vin_linked_title') : t('tesla.callback_error_unknown_title')"
    @close="closeCallbackError">
    <template #default="{ close }">
      <div class="grid gap-4 overflow-y-auto px-4 pb-4 pt-3 sm:px-6 sm:pt-6">
        <div class="mx-auto h-1 w-10 rounded-full bg-gray-300 sm:hidden dark:bg-gray-600" />
        <h3 class="flex items-start gap-2 text-xl font-extrabold text-gray-900 dark:text-gray-50">
          <ExclamationTriangleIcon class="mt-0.5 h-6 w-6 shrink-0 text-gray-500 dark:text-gray-400" aria-hidden="true" />
          {{ callbackErrorCode === 'VIN_ALREADY_LINKED' ? t('tesla.callback_error_vin_linked_title') : t('tesla.callback_error_unknown_title') }}
        </h3>
        <p class="text-sm text-gray-700 dark:text-gray-300">
          {{ callbackErrorCode === 'VIN_ALREADY_LINKED' ? t('tesla.callback_error_vin_linked_body') : t('tesla.callback_error_unknown_body') }}
        </p>
        <div class="grid gap-3" :class="callbackErrorCode !== 'VIN_ALREADY_LINKED' ? 'grid-cols-2' : ''">
          <button type="button" :class="CI_BTN_SECONDARY" @click="close">{{ t('tesla.callback_error_close') }}</button>
          <button v-if="callbackErrorCode !== 'VIN_ALREADY_LINKED'" type="button" :class="CI_BTN_PRIMARY" @click="retryConnect">
            {{ t('tesla.callback_error_retry') }}
          </button>
        </div>
      </div>
    </template>
  </BottomSheet>

  <!-- Alle Tesla-Importe löschen: einziger rote Knopf -->
  <BottomSheet v-if="showDeleteAllConfirm" edge :label="t('tesla.delete_modal_title')" @close="showDeleteAllConfirm = false">
    <template #default="{ close }">
      <div class="grid gap-4 overflow-y-auto px-4 pb-4 pt-3 sm:px-6 sm:pt-6">
        <div class="mx-auto h-1 w-10 rounded-full bg-gray-300 sm:hidden dark:bg-gray-600" />
        <h3 class="text-xl font-extrabold text-gray-900 dark:text-gray-50">{{ t('tesla.delete_modal_title') }}</h3>
        <p class="text-sm text-gray-700 dark:text-gray-300" v-html="t('tesla.delete_modal_desc')" />
        <p v-if="deleteAllError" role="alert" :class="[CI_NOTE, 'flex items-start gap-2 py-3 font-semibold text-gray-900 dark:text-gray-100']">
          <ExclamationCircleIcon class="h-5 w-5 shrink-0 text-gray-700 dark:text-gray-300" aria-hidden="true" />{{ deleteAllError }}
        </p>
        <div class="grid grid-cols-2 gap-3">
          <button type="button" :class="CI_BTN_SECONDARY" :disabled="deleteAllLoading" @click="close">{{ t('tesla.delete_modal_cancel') }}</button>
          <button type="button" :class="CI_BTN_DANGER" :disabled="deleteAllLoading" @click="handleDeleteAllImports">
            <ArrowPathIcon v-if="deleteAllLoading" class="h-5 w-5 animate-spin" aria-hidden="true" />
            {{ deleteAllLoading ? t('tesla.delete_modal_btn_loading') : t('tesla.delete_modal_btn') }}
          </button>
        </div>
      </div>
    </template>
  </BottomSheet>
</template>
