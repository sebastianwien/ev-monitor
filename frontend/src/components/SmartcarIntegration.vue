<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { BoltIcon, CheckCircleIcon, XCircleIcon, SparklesIcon } from '@heroicons/vue/24/outline'
import { useAuthStore } from '../stores/auth'
import { useCarStore } from '../stores/car'
import CarSelectDropdown from './CarSelectDropdown.vue'
import smartcarService, { type SmartcarConnectionStatus } from '../api/smartcarService'
import type { Car } from '../api/carService'

const { t } = useI18n()
const authStore = useAuthStore()
const carStore = useCarStore()

const status = ref<SmartcarConnectionStatus | null>(null)
const loading = ref(true)
const connecting = ref(false)
const disconnecting = ref(false)
const error = ref<string | null>(null)
const cars = ref<Car[]>([])
const selectedCarId = ref<string | null>(null)

onMounted(async () => {
  if (!authStore.isAdmin) return
  try {
    const [s, c] = await Promise.all([
      smartcarService.getStatus(),
      carStore.getCars(),
    ])
    status.value = s
    cars.value = (c ?? []).filter((car: Car) => car.status === 'ACTIVE')
    if (cars.value.length === 1) selectedCarId.value = cars.value[0].id
    // Handle redirect params after OAuth callback
    const params = new URLSearchParams(window.location.search)
    if (params.get('smartcar-connected')) {
      await smartcarService.getStatus().then(s => status.value = s)
      window.history.replaceState({}, '', window.location.pathname)
    }
    if (params.get('smartcar-error')) {
      error.value = decodeURIComponent(params.get('smartcar-error')!)
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
    status.value = { connected: false, vehicleName: null, carId: null, vin: null, vehicleState: null, lastCheckedAt: null, sessionActive: false }
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

const stateColor = (state: string | null) => {
  if (state === 'CHARGING') return 'text-green-600 dark:text-green-400'
  if (state === 'FULLY_CHARGED') return 'text-blue-600 dark:text-blue-400'
  return 'text-gray-500 dark:text-gray-400'
}
</script>

<template>
  <!-- TEASER: non-admin users -->
  <div v-if="!authStore.isAdmin" class="p-6 space-y-5">
    <div class="flex items-start gap-4">
      <div class="bg-indigo-600 rounded-lg p-2 shrink-0">
        <SparklesIcon class="h-5 w-5 text-white" />
      </div>
      <div>
        <h2 class="font-semibold text-gray-900 dark:text-gray-100 flex flex-wrap items-center gap-2">
          {{ t('imports.smartcar_teaser_title') }}
<span class="text-xs bg-amber-100 text-amber-800 dark:bg-amber-900/40 dark:text-amber-300 px-2 py-0.5 rounded-full font-medium">
            Premium
          </span>
        </h2>
        <p class="text-sm text-gray-600 dark:text-gray-400 mt-1">{{ t('imports.smartcar_teaser_desc') }}</p>
      </div>
    </div>

    <ul class="text-sm text-gray-600 dark:text-gray-400 space-y-1.5 list-disc list-inside">
      <li>{{ t('imports.smartcar_feat1') }}</li>
      <li>{{ t('imports.smartcar_feat2') }}</li>
      <li>{{ t('imports.smartcar_feat3') }}</li>
      <li>{{ t('imports.smartcar_feat4') }}</li>
    </ul>

    <div class="p-4 bg-indigo-50 dark:bg-indigo-900/20 border border-indigo-200 dark:border-indigo-700 rounded-lg">
      <p class="text-sm font-medium text-indigo-800 dark:text-indigo-200 mb-2">{{ t('imports.smartcar_brands_title') }}</p>
      <p class="text-xs text-indigo-700 dark:text-indigo-300">
        BMW, Mercedes-EQ, VW ID, Audi e-tron, Porsche Taycan, Hyundai, Kia, Volvo, Polestar, Renault, Ford, Tesla, ...
      </p>
    </div>
  </div>

  <!-- ADMIN: full setup UI -->
  <div v-else class="p-6 space-y-5">
    <div class="flex items-start gap-4">
      <div class="bg-indigo-600 rounded-lg p-2 shrink-0">
        <BoltIcon class="h-5 w-5 text-white" />
      </div>
      <div>
        <h2 class="font-semibold text-gray-900 dark:text-gray-100">{{ t('imports.smartcar_title') }}</h2>
        <p class="text-sm text-gray-600 dark:text-gray-400 mt-1">{{ t('imports.smartcar_desc') }}</p>
      </div>
    </div>

    <div v-if="loading" class="text-sm text-gray-500 dark:text-gray-400">{{ t('imports.smartcar_loading') }}</div>

    <template v-else>
      <!-- Error -->
      <div v-if="error" class="p-3 bg-red-50 dark:bg-red-900/30 border border-red-200 dark:border-red-700 rounded-lg text-sm text-red-700 dark:text-red-300">
        {{ error }}
      </div>

      <!-- Connected -->
      <div v-if="status?.connected" class="space-y-4">
        <div class="p-4 bg-green-50 dark:bg-green-900/20 border border-green-200 dark:border-green-700 rounded-lg flex items-start gap-3">
          <CheckCircleIcon class="h-5 w-5 text-green-600 dark:text-green-400 shrink-0 mt-0.5" />
          <div class="flex-1 min-w-0">
            <p class="font-medium text-green-800 dark:text-green-200 text-sm">{{ status.vehicleName }}</p>
            <p v-if="status.vin" class="text-xs text-green-700 dark:text-green-300 font-mono mt-0.5">{{ status.vin }}</p>
            <div class="flex items-center gap-3 mt-1.5">
              <span :class="['text-xs font-medium', stateColor(status.vehicleState)]">
                {{ stateLabel(status.vehicleState) }}
              </span>
              <span v-if="status.sessionActive" class="flex items-center gap-1 text-xs text-green-600 dark:text-green-400">
                <span class="inline-block w-1.5 h-1.5 rounded-full bg-green-500 animate-pulse" />
                {{ t('imports.smartcar_session_active') }}
              </span>
            </div>
            <p v-if="status.lastCheckedAt" class="text-xs text-gray-500 dark:text-gray-400 mt-1">
              {{ t('imports.smartcar_last_update') }}: {{ new Date(status.lastCheckedAt).toLocaleString() }}
            </p>
          </div>
        </div>

        <button
          @click="disconnect"
          :disabled="disconnecting"
          class="flex items-center gap-2 px-4 py-2 text-sm font-medium text-red-600 dark:text-red-400 border border-red-300 dark:border-red-600 rounded-lg hover:bg-red-50 dark:hover:bg-red-900/20 disabled:opacity-50 transition"
        >
          <XCircleIcon class="h-4 w-4" />
          {{ disconnecting ? t('imports.smartcar_disconnecting') : t('imports.smartcar_disconnect_btn') }}
        </button>
      </div>

      <!-- Not connected -->
      <div v-else class="space-y-4">
        <div v-if="cars.length === 0" class="text-sm text-gray-500 dark:text-gray-400">
          {{ t('imports.smartcar_no_cars') }}
          <router-link to="/cars" class="text-indigo-600 hover:underline font-medium ml-1">{{ t('imports.smartcar_add_car') }}</router-link>
        </div>
        <template v-else>
          <div v-if="cars.length > 1" class="space-y-1.5">
            <label class="block text-sm font-medium text-gray-700 dark:text-gray-300">{{ t('imports.smartcar_select_car') }}</label>
            <CarSelectDropdown :cars="cars" v-model="selectedCarId" />
          </div>
          <button
            @click="connect"
            :disabled="connecting || !selectedCarId"
            class="btn-3d flex items-center gap-2 bg-indigo-600 text-white px-5 py-2.5 rounded-lg font-medium text-sm hover:bg-indigo-700 disabled:opacity-40 disabled:cursor-not-allowed transition"
          >
            <BoltIcon class="h-4 w-4" />
            {{ connecting ? t('imports.smartcar_connecting') : t('imports.smartcar_connect_btn') }}
          </button>
        </template>
      </div>
    </template>
  </div>
</template>
