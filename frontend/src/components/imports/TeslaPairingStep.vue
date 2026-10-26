<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowTopRightOnSquareIcon, ArrowPathIcon, ExclamationTriangleIcon, CheckCircleIcon, ChevronDownIcon } from '@heroicons/vue/24/outline'
import Collapse from '../shared/Collapse.vue'
import { CI_BTN_PRIMARY, CI_BTN_SECONDARY, CI_LABEL, CI_CARD } from '@/utils/ciClasses'
import type { TeslaPairingStatus } from '@/api/teslaFleetService'
import { useAuthStore } from '@/stores/auth'
import { isTeslaVehicleMissing } from '@/utils/teslaVehicleMissing'

/**
 * Schritt 2 der Tesla-Einrichtung: Virtual Key in der Tesla-App pairen, danach
 * Telemetry aktivieren. Rein darstellend - den Zustand haelt `useTeslaPairing`
 * beim Aufrufer (Tesla-Sektion in /imports, Modal in /cars).
 */
const props = defineProps<{
  status: TeslaPairingStatus | null
  loading: boolean
  error: string | null
}>()

defineEmits<{
  (e: 'enable'): void
  (e: 'refresh'): void
  (e: 'reconnect'): void
}>()

const { t } = useI18n()

// Ohne VIN kann Tesla weder Pairing-Status liefern noch Telemetry annehmen - dann hilft nur
// ein erneuter OAuth-Durchlauf, nicht der Virtual-Key-Hinweis.
const vehicleMissing = computed(() => isTeslaVehicleMissing(props.status))
const authStore = useAuthStore()
const unreliableOpen = ref(false)

// Rollenabhaengige Kennzeichnung: Beta-Tester helfen bei der Trip-Erkennung,
// Founder behalten Live-Sync dauerhaft gratis.
const roleBadge = computed(() => {
  if (authStore.isAdmin) return 'Beta · Admin'
  if (authStore.isBetaTester) return 'Beta'
  if (authStore.isTeslaFounder) return t('tesla.pairing_founder_badge')
  return ''
})
</script>

<template>
  <div class="grid gap-3">
    <div class="flex items-center justify-center gap-2">
      <p :class="CI_LABEL">{{ t('tesla.pairing_title') }}</p>
      <span v-if="roleBadge" class="rounded-full bg-gray-100 px-2 py-0.5 text-[11.5px] font-bold uppercase tracking-wide text-gray-600 dark:bg-gray-800 dark:text-gray-300">{{ roleBadge }}</span>
    </div>

    <!-- Ohne Fahrzeug fehlt etwas: Amber mit Handlung -->
    <div v-if="vehicleMissing" class="grid gap-3 rounded-2xl bg-amber-50 p-4 dark:bg-amber-900/20">
      <div class="flex items-start gap-2">
        <ExclamationTriangleIcon class="mt-0.5 h-5 w-5 shrink-0 text-amber-600 dark:text-amber-400" aria-hidden="true" />
        <div class="grid gap-1">
          <p class="text-sm font-semibold text-gray-900 dark:text-gray-100">{{ t('tesla.pairing_no_vehicle_title') }}</p>
          <p class="text-[13px] leading-relaxed text-gray-700 dark:text-gray-300">{{ t('tesla.pairing_no_vehicle_hint') }}</p>
        </div>
      </div>
      <button type="button" :class="[CI_BTN_PRIMARY, 'w-full']" :disabled="loading" @click="$emit('reconnect')">
        <ArrowTopRightOnSquareIcon class="h-5 w-5" aria-hidden="true" />
        {{ t('tesla.pairing_no_vehicle_btn') }}
      </button>
    </div>

    <div v-else-if="status" :class="[CI_CARD, 'grid gap-2 text-sm']">
      <div class="flex items-center justify-between gap-3">
        <span :class="CI_LABEL">{{ t('tesla.pairing_key_label') }}</span>
        <span v-if="status.keyPaired" class="inline-flex items-center gap-1 text-[13px] font-semibold text-green-700 dark:text-green-400">
          <CheckCircleIcon class="h-4 w-4" aria-hidden="true" />{{ t('tesla.pairing_key_ok') }}
        </span>
        <span v-else class="inline-flex items-center gap-1 text-[13px] font-semibold text-amber-700 dark:text-amber-400">
          <ExclamationTriangleIcon class="h-4 w-4" aria-hidden="true" />{{ t('tesla.pairing_key_missing') }}
        </span>
      </div>
      <div v-if="!status.keyPaired">
        <button type="button" :aria-expanded="unreliableOpen"
          class="inline-flex min-h-11 items-center gap-1.5 text-[13px] font-semibold text-gray-600 dark:text-gray-400"
          @click="unreliableOpen = !unreliableOpen">
          {{ t('tesla.pairing_key_unreliable_summary') }}
          <ChevronDownIcon class="h-4 w-4 transition-transform duration-300 motion-reduce:transition-none"
            :class="unreliableOpen ? 'rotate-180 ease-out' : 'ease-in'" aria-hidden="true" />
        </button>
        <Collapse :open="unreliableOpen">
          <p class="pb-1 text-[13px] leading-relaxed text-gray-600 dark:text-gray-400">{{ t('tesla.pairing_key_unreliable_hint') }}</p>
        </Collapse>
      </div>
      <div class="flex items-center justify-between gap-3">
        <span :class="CI_LABEL">{{ t('tesla.pairing_config_label') }}</span>
        <span class="inline-flex items-center gap-1 text-[13px] font-semibold"
          :class="status.telemetryConfigPushed ? 'text-green-700 dark:text-green-400' : 'text-gray-600 dark:text-gray-400'">
          <CheckCircleIcon v-if="status.telemetryConfigPushed" class="h-4 w-4" aria-hidden="true" />
          {{ status.telemetryConfigPushed ? t('tesla.pairing_config_ok') : t('tesla.pairing_config_missing') }}
        </span>
      </div>
    </div>
    <div v-else-if="loading" class="flex items-center justify-center py-2">
      <ArrowPathIcon class="h-5 w-5 animate-spin text-gray-400" aria-hidden="true" />
    </div>

    <!-- Eine grüne Handlung: erst den Schlüssel in der App, danach Telemetry einschalten -->
    <a v-if="!vehicleMissing && !status?.keyPaired" href="https://tesla.com/_ak/ev-monitor.net" target="_blank" rel="noopener"
      :class="[CI_BTN_PRIMARY, 'w-full']">
      <ArrowTopRightOnSquareIcon class="h-5 w-5" aria-hidden="true" />
      {{ t('tesla.pairing_open_app_btn') }}
    </a>

    <button v-if="status && !vehicleMissing" type="button" :class="[status.keyPaired ? CI_BTN_PRIMARY : CI_BTN_SECONDARY, 'w-full']"
      :disabled="loading" @click="$emit('enable')">
      <ArrowPathIcon class="h-5 w-5" :class="{ 'animate-spin': loading }" aria-hidden="true" />
      {{ loading ? t('tesla.pairing_enable_btn_loading') : t('tesla.pairing_enable_btn') }}
    </button>

    <button type="button" class="min-h-11 w-full text-center text-sm font-semibold text-gray-600 transition hover:text-gray-900 disabled:opacity-50 dark:text-gray-400 dark:hover:text-gray-100"
      :disabled="loading" @click="$emit('refresh')">
      {{ t('tesla.pairing_refresh') }}
    </button>

    <p v-if="error" role="alert" class="flex items-start justify-center gap-2 text-[13px] font-semibold text-gray-900 dark:text-gray-100">
      <ExclamationTriangleIcon class="h-5 w-5 shrink-0 text-gray-600 dark:text-gray-300" aria-hidden="true" />{{ error }}
    </p>
  </div>
</template>
