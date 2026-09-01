<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowTopRightOnSquareIcon, CheckCircleIcon, ExclamationCircleIcon, XMarkIcon } from '@heroicons/vue/24/outline'
import TeslaTelemetryExplainer from '../imports/TeslaTelemetryExplainer.vue'
import TeslaPairingStep from '../imports/TeslaPairingStep.vue'
import teslaFleetService from '../../api/teslaFleetService'
import { useTeslaPairing } from '../../composables/useTeslaPairing'
import type { Car } from '../../api/carService'

/**
 * Tesla-Einrichtung direkt nach dem Anlegen des Fahrzeugs in /cars - und nach der
 * Rueckkehr aus dem Tesla-OAuth, denn Tesla leitet auf /cars?tesla-connected=true.
 *
 * Zwei Schritte, beide im Modal:
 * 1. `connect` - Tesla-Account per OAuth verbinden (verlaesst die Seite).
 * 2. `pair` - Virtual Key in der Tesla-App pairen, danach schaltet sich Telemetry
 *    automatisch scharf (`useTeslaPairing`).
 */
const props = defineProps<{
  car: Car
  /** Frisch aus dem OAuth zurueck: Schritt 1 ist erledigt, direkt pairen. */
  connected?: boolean
  /** Fehlercode aus dem OAuth-Callback (`VIN_ALREADY_LINKED` | `UNKNOWN`). */
  callbackError?: string | null
}>()

const emit = defineEmits<{ (e: 'close'): void }>()

const { t } = useI18n()

const connecting = ref(false)
const connectError = ref<string | null>(null)
// Der Tesla-Account kann schon verbunden sein (zweiter Tesla in der Garage) - dann
// steht wie im /imports-Tab kein Connect-Button, sondern direkt das Pairing.
const accountConnected = ref(props.connected === true)

const {
  pairingStatus, pairingLoading, pairingError, isTelemetryActive, isFullProfile,
  loadPairingStatus, enableTelemetry,
} = useTeslaPairing(() => accountConnected.value)

onMounted(async () => {
  if (!accountConnected.value) {
    const status = await teslaFleetService.getStatus().catch(() => null)
    accountConnected.value = status?.connected === true
  }
  if (accountConnected.value) await loadPairingStatus()
})

const callbackErrorText = computed(() => {
  if (!props.callbackError) return null
  return props.callbackError === 'VIN_ALREADY_LINKED'
    ? t('tesla.callback_error_vin_linked_body')
    : t('tesla.callback_error_unknown_body')
})

/** OAuth-Redirect zu Tesla, fest auf dieses Auto - zurueck kommt der User auf /cars. */
const connect = async () => {
  connecting.value = true
  connectError.value = null
  try {
    const result = await teslaFleetService.startReconnect(props.car.id)
    if (result === 'not_configured') {
      connectError.value = t('tesla.err_fleet_api')
    }
  } catch (e: any) {
    connectError.value = e.response?.data?.message || t('tesla.err_connect')
  } finally {
    connecting.value = false
  }
}
</script>

<template>
  <!-- Teleport nach body: die Fahrzeug-View liegt auf Mobile im SwipeTabPager, dessen Track
       ein translateX() traegt - ein transformierter Vorfahre wird zum Containing Block fuer
       position:fixed und wuerde das Overlay aus dem Viewport schieben. -->
  <Teleport to="body">
  <div
    class="fixed inset-0 bg-black bg-opacity-50 flex items-end md:items-center justify-center z-50 md:p-4"
    role="dialog"
    aria-modal="true"
    :aria-label="t('tesla.prompt_title')"
    @click.self="emit('close')"
  >
    <div class="bg-white dark:bg-gray-800 rounded-t-sm md:rounded-sm md:shadow-[6px_6px_0_rgba(0,0,0,0.40)] md:dark:shadow-[6px_6px_0_rgba(255,255,255,0.40)] w-full max-w-2xl max-h-[92vh] md:max-h-[90vh] overflow-y-auto">
      <div class="flex items-start justify-between gap-4 px-5 md:px-6 pt-5 md:pt-6 pb-4 border-b border-gray-100 dark:border-gray-700">
        <div class="min-w-0">
          <p class="text-red-600 dark:text-red-500 text-[10px] font-bold uppercase tracking-[0.14em] mb-0.5">
            {{ accountConnected ? t('tesla.prompt_eyebrow_connected') : t('tesla.prompt_eyebrow') }}
          </p>
          <h2 class="text-lg md:text-xl font-bold text-gray-900 dark:text-white tracking-tight">{{ t('tesla.prompt_title') }}</h2>
        </div>
        <button
          type="button"
          :aria-label="t('common.close')"
          class="shrink-0 text-gray-400 hover:text-gray-600 dark:hover:text-gray-200 transition-colors"
          @click="emit('close')"
        >
          <XMarkIcon class="w-6 h-6" />
        </button>
      </div>

      <!-- Telemetry laeuft - fertig. -->
      <div v-if="isTelemetryActive" class="p-5 md:p-6 space-y-5">
        <div class="flex items-start gap-3 rounded-sm border-2 border-green-600 bg-green-50 dark:bg-green-950/30 p-4 shadow-[2px_2px_0_0_#16a34a]">
          <CheckCircleIcon class="w-6 h-6 shrink-0 text-green-700 dark:text-green-500" />
          <div>
            <p class="font-bold text-gray-900 dark:text-white text-sm mb-1">{{ t('tesla.prompt_success_title') }}</p>
            <p class="text-sm text-gray-700 dark:text-gray-300 leading-relaxed">
              {{ isFullProfile ? t('tesla.telemetry_live_desc_full') : t('tesla.telemetry_live_desc_charging_only') }}
            </p>
          </div>
        </div>
        <button
          type="button"
          v-haptic
          class="btn-3d w-full bg-gray-950 dark:bg-gray-700 text-white font-bold uppercase tracking-wide text-sm px-5 py-2.5 rounded-sm"
          @click="emit('close')"
        >
          {{ t('common.close') }}
        </button>
      </div>

      <div v-else class="p-5 md:p-6 space-y-5">
        <TeslaTelemetryExplainer :card="false" />

        <div
          v-if="callbackErrorText || connectError"
          class="rounded-sm border-2 border-red-300 dark:border-red-700 bg-red-50 dark:bg-red-950/40 p-3 text-sm text-red-900 dark:text-red-200 flex gap-2"
        >
          <ExclamationCircleIcon class="w-5 h-5 flex-shrink-0" />
          <span>{{ callbackErrorText || connectError }}</span>
        </div>

        <div class="border-t-2 border-gray-200 dark:border-gray-700 pt-5">
          <!-- Schritt 2: Virtual Key pairen (nach der OAuth-Rueckkehr). -->
          <TeslaPairingStep
            v-if="accountConnected"
            :status="pairingStatus"
            :loading="pairingLoading"
            :error="pairingError"
            @enable="enableTelemetry"
            @refresh="loadPairingStatus"
          />

          <!-- Schritt 1: Tesla-Account verbinden. -->
          <button
            v-else
            type="button"
            v-haptic
            :disabled="connecting"
            class="w-full inline-flex items-center justify-center gap-2 bg-red-600 hover:bg-red-500 disabled:bg-gray-300 dark:disabled:bg-gray-700 disabled:text-gray-500 disabled:cursor-not-allowed text-white font-bold uppercase tracking-wider text-xs px-5 py-3 rounded-sm border-2 border-red-600 disabled:border-gray-300 dark:disabled:border-gray-700 shadow-[2px_2px_0_0_#030712] disabled:shadow-none active:translate-x-[3px] active:translate-y-[3px] active:shadow-none transition-[transform,box-shadow] duration-75"
            @click="connect"
          >
            <ArrowTopRightOnSquareIcon class="h-4 w-4" />
            {{ connecting ? t('tesla.connect_btn_loading') : t('tesla.connect_btn') }}
          </button>
        </div>

        <div class="text-center">
          <button
            type="button"
            class="text-sm text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-200 underline"
            @click="emit('close')"
          >
            {{ t('tesla.prompt_later') }}
          </button>
          <p class="text-xs text-gray-400 dark:text-gray-500 mt-1">{{ t('tesla.prompt_later_hint') }}</p>
        </div>
      </div>
    </div>
  </div>
  </Teleport>
</template>
