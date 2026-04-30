<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { BoltIcon, CheckCircleIcon, XCircleIcon, ArrowTopRightOnSquareIcon, ArrowPathIcon, ExclamationTriangleIcon } from '@heroicons/vue/24/outline'
import { useCarStore } from '../../stores/car'
import vwGroupService, { type VwGroupConnectionStatus, isCredentialBrand } from '../../api/vwGroupService'
import type { Car } from '../../api/carService'

const { t } = useI18n()

const props = defineProps<{ premiumEnabled?: boolean; isPremium?: boolean; isBetaTester?: boolean }>()

const carStore = useCarStore()
const cars = ref<Car[]>([])
const status = ref<VwGroupConnectionStatus | null>(null)
const loading = ref(true)
const error = ref<string | null>(null)
// Distinguishes auth errors (wrong credentials, 2FA) from technical errors (network,
// upstream) so the user knows whether to retry or check their credentials.
const errorKind = ref<'auth' | 'technical' | null>(null)

// Device Code auth state (Skoda only)
const activeBrand = ref<string | null>(null)
const userCode = ref<string | null>(null)
const verificationUri = ref<string | null>(null)
const authExpiresAt = ref<number | null>(null)
const authPending = ref(false)
const connecting = ref(false)
const disconnecting = ref(false)

// Credential auth state (VW, SEAT, CUPRA)
const credEmail = ref('')
const credPassword = ref('')
const credSubmitting = ref(false)

let pollTimer: ReturnType<typeof setTimeout> | null = null
let countdownTimer: ReturnType<typeof setInterval> | null = null
const secondsLeft = ref(0)

const activeCar = computed(() =>
  cars.value.find(c => (c as any).active) ?? cars.value[0] ?? null
)

const brandKey = computed(() => activeCar.value?.brand?.toLowerCase() ?? null)

const displayBrand = computed(() => {
  const b = activeCar.value?.brand?.toLowerCase()
  if (b === 'vw') return 'Volkswagen'
  if (b === 'skoda') return 'Škoda'
  if (b === 'audi') return 'Audi'
  if (b === 'seat') return 'SEAT'
  if (b === 'cupra') return 'CUPRA'
  return activeCar.value?.brand ?? 'VW Group'
})

onMounted(async () => {
  if (!props.premiumEnabled && !props.isPremium && !props.isBetaTester) return
  try {
    const [c] = await Promise.all([carStore.getCars()])
    cars.value = ((c ?? []) as Car[]).filter((car: Car) => (car as any).status === 'ACTIVE')
    if (brandKey.value) {
      status.value = await vwGroupService.getStatus(brandKey.value).catch(() => null)
    }
  } catch (e: any) {
    error.value = e.message
  } finally {
    loading.value = false
  }
})

onUnmounted(stopPolling)

const connect = async () => {
  if (!brandKey.value || !activeCar.value) return
  connecting.value = true
  error.value = null
  try {
    const result = await vwGroupService.startAuth(brandKey.value, activeCar.value.id)
    activeBrand.value = brandKey.value
    userCode.value = result.userCode
    verificationUri.value = result.verificationUri
    authExpiresAt.value = Date.now() + result.expiresIn * 1000
    secondsLeft.value = result.expiresIn
    authPending.value = true
    startCountdown()
    startPolling()
  } catch (e: any) {
    error.value = e.response?.data?.message || e.message
  } finally {
    connecting.value = false
  }
}

const startPolling = () => {
  if (pollTimer) clearTimeout(pollTimer)
  const poll = async () => {
    if (!activeBrand.value) return
    try {
      const res = await vwGroupService.pollAuthStatus(activeBrand.value)
      if (res.status === 'connected') {
        status.value = await vwGroupService.getStatus(activeBrand.value)
        authPending.value = false
        stopPolling()
        return
      }
      if (res.status === 'expired') {
        authPending.value = false
        error.value = t('imports.vwgroup_code_expired')
        stopPolling()
        return
      }
    } catch { /* ignore */ }
    pollTimer = setTimeout(poll, 3000)
  }
  pollTimer = setTimeout(poll, 3000)
}

const startCountdown = () => {
  if (countdownTimer) clearInterval(countdownTimer)
  countdownTimer = setInterval(() => {
    secondsLeft.value = Math.max(0, Math.round(((authExpiresAt.value ?? 0) - Date.now()) / 1000))
    if (secondsLeft.value <= 0) {
      clearInterval(countdownTimer!)
      if (authPending.value) {
        authPending.value = false
        error.value = t('imports.vwgroup_code_expired')
      }
    }
  }, 1000)
}

function stopPolling() {
  if (pollTimer) { clearTimeout(pollTimer); pollTimer = null }
  if (countdownTimer) { clearInterval(countdownTimer); countdownTimer = null }
}

const disconnect = async () => {
  if (!brandKey.value) return
  if (!confirm(t('imports.vwgroup_confirm_disconnect'))) return
  disconnecting.value = true
  try {
    await vwGroupService.disconnect(brandKey.value)
    status.value = null
  } catch (e: any) {
    error.value = e.message
  } finally {
    disconnecting.value = false
  }
}

const connectWithCredentials = async () => {
  if (!brandKey.value || !activeCar.value) return
  credSubmitting.value = true
  error.value = null
  errorKind.value = null
  try {
    await vwGroupService.startCredentialAuth(brandKey.value, activeCar.value.id, credEmail.value, credPassword.value)
    status.value = await vwGroupService.getStatus(brandKey.value)
    credPassword.value = ''
  } catch (e: any) {
    const reason = e.response?.data?.reason as string | undefined
    const serverMsg = e.response?.data?.error as string | undefined
    if (!e.response) {
      // No response = network / CORS / timeout
      errorKind.value = 'technical'
      error.value = t('imports.vwgroup_cred_error_network')
    } else if (reason === 'AUTH_FAILED' || reason === 'TWO_FACTOR_REQUIRED') {
      errorKind.value = 'auth'
      error.value = serverMsg || t('imports.vwgroup_cred_error_generic')
    } else {
      errorKind.value = 'technical'
      error.value = serverMsg || t('imports.vwgroup_cred_error_generic')
    }
  } finally {
    credSubmitting.value = false
  }
}

const cancelAuth = () => {
  authPending.value = false
  userCode.value = null
  verificationUri.value = null
  stopPolling()
}

const stateLabel = (state: string | null) => {
  if (state === 'charging') return t('imports.vwgroup_state_charging')
  if (state === 'not_charging') return t('imports.vwgroup_state_idle')
  return t('imports.vwgroup_state_unknown')
}

const stateColor = (state: string | null) => {
  if (state === 'charging') return 'text-green-600 dark:text-green-400'
  return 'text-gray-500 dark:text-gray-400'
}
</script>

<template>
  <!-- TEASER -->
  <div v-if="props.premiumEnabled && !props.isPremium && !props.isBetaTester" class="p-6 space-y-5">
    <div>
      <h2 class="font-semibold text-gray-900 dark:text-gray-100 flex flex-wrap items-center gap-2">
        {{ t('imports.vwgroup_teaser_title') }}
        <span class="text-xs bg-amber-100 text-amber-800 dark:bg-amber-900/40 dark:text-amber-300 px-2 py-0.5 rounded-full font-medium">Premium</span>
      </h2>
      <p class="text-sm text-gray-600 dark:text-gray-400 mt-1">{{ t('imports.vwgroup_teaser_desc') }}</p>
    </div>
    <router-link
      to="/upgrade"
      class="btn-3d w-full flex items-center justify-center gap-2 bg-green-600 hover:bg-green-700 text-white px-5 py-3 rounded-xl font-semibold text-sm transition-colors"
    >
      {{ t('imports.smartcar_upgrade_cta', { priceMonthly: t('upgrade.price_monthly') }) }}
    </router-link>
  </div>

  <!-- SETUP -->
  <div v-else class="p-6 space-y-5">
    <div v-if="loading" class="text-sm text-gray-500 dark:text-gray-400">{{ t('imports.smartcar_loading') }}</div>

    <template v-else>
      <div
        v-if="error"
        :class="[
          'p-3 border rounded-lg text-sm flex items-start gap-2',
          errorKind === 'technical'
            ? 'bg-amber-50 dark:bg-amber-900/30 border-amber-200 dark:border-amber-700 text-amber-800 dark:text-amber-200'
            : 'bg-red-50 dark:bg-red-900/30 border-red-200 dark:border-red-700 text-red-700 dark:text-red-300'
        ]"
        role="alert"
      >
        <ExclamationTriangleIcon class="h-5 w-5 shrink-0 mt-0.5" />
        <div class="flex-1 min-w-0">
          <p v-if="errorKind" class="font-medium">
            {{ errorKind === 'auth' ? t('imports.vwgroup_cred_error_title_auth') : t('imports.vwgroup_cred_error_title_technical') }}
          </p>
          <p>{{ error }}</p>
        </div>
      </div>

      <!-- VERBUNDEN -->
      <div v-if="status?.connected" class="space-y-4">
        <div class="p-4 bg-gray-100 dark:bg-gray-700/60 border border-gray-200 dark:border-gray-600 rounded-lg flex items-start gap-3">
          <CheckCircleIcon class="h-5 w-5 text-green-500 dark:text-green-400 shrink-0 mt-0.5" />
          <div class="flex-1 min-w-0">
            <p class="font-medium text-gray-900 dark:text-gray-100 text-sm">
              {{ status.make }} {{ status.model }} <span v-if="status.year" class="text-gray-500">({{ status.year }})</span>
            </p>
            <p v-if="status.vin" class="text-xs text-gray-500 dark:text-gray-400 font-mono mt-0.5">{{ status.vin }}</p>
            <div class="flex items-center gap-3 mt-1.5">
              <span :class="['text-xs font-medium', stateColor(status.vehicleState)]">
                {{ stateLabel(status.vehicleState) }}
              </span>
              <span v-if="status.vehicleState === 'charging'" class="flex items-center gap-1 text-xs text-green-600 dark:text-green-400">
                <span class="inline-block w-1.5 h-1.5 rounded-full bg-green-500 animate-pulse" />
                {{ t('imports.vwgroup_session_active') }}
              </span>
            </div>
            <div v-if="status.lastCheckedAt || status.lastSoc != null" class="mt-2 pt-2 border-t border-gray-200 dark:border-gray-600 flex flex-wrap gap-x-4 gap-y-1">
              <span v-if="status.lastSoc != null" class="text-xs text-gray-500 dark:text-gray-400">
                SoC: <span class="font-medium text-gray-700 dark:text-gray-200">{{ status.lastSoc }}%</span>
              </span>
              <span v-if="status.lastRangeKm != null" class="text-xs text-gray-500 dark:text-gray-400">
                {{ t('imports.vwgroup_range') }}: <span class="font-medium text-gray-700 dark:text-gray-200">{{ status.lastRangeKm }} km</span>
              </span>
              <span v-if="status.lastCheckedAt" class="text-xs text-gray-500 dark:text-gray-400">
                {{ t('imports.smartcar_last_update') }}: {{ new Date(status.lastCheckedAt).toLocaleString() }}
              </span>
            </div>
          </div>
        </div>
        <button
          @click="disconnect"
          :disabled="disconnecting"
          class="btn-3d flex items-center gap-2 px-4 py-2 text-sm font-medium bg-red-100 dark:bg-red-700 text-red-800 dark:text-white rounded-lg hover:bg-red-200 dark:hover:bg-red-600 disabled:opacity-50 transition shadow-[0_4px_0_0_#fca5a5] dark:shadow-[0_4px_0_0_#b91c1c] active:shadow-none active:translate-y-1" style="transition: transform 0.075s ease, box-shadow 0.075s ease;"
        >
          <XCircleIcon class="h-4 w-4" />
          {{ disconnecting ? t('imports.smartcar_disconnecting') : t('imports.vwgroup_disconnect_btn') }}
        </button>
      </div>

      <!-- DEVICE CODE AUSSTEHEND -->
      <div v-else-if="authPending" class="space-y-4">
        <div class="p-4 bg-indigo-50 dark:bg-indigo-900/20 border border-indigo-200 dark:border-indigo-700 rounded-xl space-y-3">
          <p class="text-sm font-medium text-indigo-900 dark:text-indigo-100">{{ t('imports.vwgroup_auth_step1') }}</p>
          <a
            :href="verificationUri ?? '#'"
            target="_blank"
            class="flex items-center gap-2 text-sm font-semibold text-indigo-700 dark:text-indigo-300 underline underline-offset-2 hover:no-underline"
          >
            <ArrowTopRightOnSquareIcon class="h-4 w-4 shrink-0" />
            {{ verificationUri }}
          </a>
          <p class="text-sm text-indigo-800 dark:text-indigo-200">{{ t('imports.vwgroup_auth_step2') }}</p>
          <div class="flex items-center gap-3">
            <span class="font-mono text-2xl font-bold tracking-widest bg-white dark:bg-gray-800 border border-indigo-300 dark:border-indigo-600 px-4 py-2 rounded-lg text-indigo-900 dark:text-indigo-100 select-all">
              {{ userCode }}
            </span>
            <span class="text-xs text-indigo-500 dark:text-indigo-400">
              {{ t('imports.vwgroup_expires_in', { s: secondsLeft }) }}
            </span>
          </div>
          <div class="flex items-center gap-2 text-xs text-indigo-600 dark:text-indigo-400">
            <span class="inline-block w-1.5 h-1.5 rounded-full bg-indigo-400 animate-pulse" />
            {{ t('imports.vwgroup_waiting') }}
          </div>
        </div>
        <button @click="cancelAuth" class="text-xs text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 underline">
          {{ t('imports.vwgroup_cancel') }}
        </button>
      </div>

      <!-- NICHT VERBUNDEN -->
      <div v-else class="space-y-4">
        <div v-if="!activeCar" class="text-sm text-gray-500 dark:text-gray-400">
          {{ t('imports.smartcar_no_cars') }}
          <router-link to="/cars" class="text-indigo-600 hover:underline font-medium ml-1">{{ t('imports.smartcar_add_car') }}</router-link>
        </div>

        <!-- Credential flow: VW, SEAT, CUPRA -->
        <template v-else-if="isCredentialBrand(brandKey)">
          <p class="text-sm text-gray-600 dark:text-gray-400">{{ t('imports.vwgroup_cred_desc', { brand: displayBrand }) }}</p>
          <form @submit.prevent="connectWithCredentials" class="space-y-3">
            <div>
              <label for="vwgroup-cred-email" class="block text-xs font-medium text-gray-700 dark:text-gray-300 mb-1">
                {{ t('imports.vwgroup_cred_email') }}
              </label>
              <input
                id="vwgroup-cred-email"
                v-model="credEmail"
                type="email"
                autocomplete="email"
                required
                :disabled="credSubmitting"
                class="w-full px-3 py-2 text-sm border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-indigo-500 disabled:opacity-50"
              />
            </div>
            <div>
              <label for="vwgroup-cred-password" class="block text-xs font-medium text-gray-700 dark:text-gray-300 mb-1">
                {{ t('imports.vwgroup_cred_password') }}
              </label>
              <input
                id="vwgroup-cred-password"
                v-model="credPassword"
                type="password"
                autocomplete="current-password"
                required
                :disabled="credSubmitting"
                class="w-full px-3 py-2 text-sm border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-indigo-500 disabled:opacity-50"
              />
            </div>
            <button
              type="submit"
              :disabled="credSubmitting || !credEmail || !credPassword"
              class="btn-3d w-full flex items-center justify-center gap-2 bg-indigo-600 text-white px-5 py-2.5 rounded-lg font-medium text-sm hover:bg-indigo-700 disabled:opacity-40 disabled:cursor-not-allowed transition"
            >
              <ArrowPathIcon v-if="credSubmitting" class="h-4 w-4 animate-spin" />
              <BoltIcon v-else class="h-4 w-4" />
              {{ credSubmitting ? t('imports.vwgroup_cred_signing_in') : t('imports.vwgroup_cred_submit', { brand: displayBrand }) }}
            </button>
            <p v-if="credSubmitting" class="text-xs text-indigo-600 dark:text-indigo-400 text-center" role="status" aria-live="polite">
              {{ t('imports.vwgroup_cred_wait_hint') }}
            </p>
          </form>
          <p class="text-xs text-gray-400 dark:text-gray-500">{{ t('imports.vwgroup_cred_security_note') }}</p>
        </template>

        <!-- Device Code flow: Skoda -->
        <template v-else>
          <p class="text-sm text-gray-600 dark:text-gray-400">{{ t('imports.vwgroup_connect_desc', { brand: displayBrand }) }}</p>
          <button
            @click="connect"
            :disabled="connecting"
            class="btn-3d flex items-center gap-2 bg-indigo-600 text-white px-5 py-2.5 rounded-lg font-medium text-sm hover:bg-indigo-700 disabled:opacity-40 disabled:cursor-not-allowed transition"
          >
            <BoltIcon class="h-4 w-4" />
            {{ connecting ? t('imports.vwgroup_connecting') : t('imports.vwgroup_connect_btn', { brand: displayBrand }) }}
          </button>
          <p class="text-xs text-gray-400 dark:text-gray-500">
            {{ t('imports.smartcar_support_hint') }}
            <a href="mailto:support@ev-monitor.net" class="underline hover:no-underline">support@ev-monitor.net</a>
          </p>
        </template>
      </div>
    </template>
  </div>
</template>
