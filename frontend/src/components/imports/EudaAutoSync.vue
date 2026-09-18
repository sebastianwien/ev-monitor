<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { BoltIcon, CheckCircleIcon, ExclamationTriangleIcon, LockClosedIcon, ArrowPathIcon } from '@heroicons/vue/24/outline'
import euDataActSyncService, {
  eudaErrorCode,
  type EudaBrand,
  type EudaConnectionStatus,
} from '../../api/euDataActSyncService'
import type { Car } from '../../api/carService'
import CarSelectDropdown from '../car/CarSelectDropdown.vue'

/**
 * VW EU-Data-Act-AutoSync: Nutzer meldet sich einmal mit seiner Marken-ID an, danach holt
 * ev-monitor die Ladevorgaenge alle 15 Minuten aus dem Portal. Das Passwort wird nur fuer die
 * Anmeldung uebertragen und nicht gespeichert - das sagt die Karte auch so.
 */
const props = defineProps<{
  cars: Car[]
  isPremium: boolean
}>()

const { t, d } = useI18n()

const BRANDS: { key: EudaBrand; label: string }[] = [
  { key: 'volkswagen', label: 'Volkswagen' },
  { key: 'skoda', label: 'Škoda' },
  { key: 'audi', label: 'Audi' },
  { key: 'seat', label: 'SEAT' },
  { key: 'cupra', label: 'CUPRA' },
]

const loading = ref(true)
const connecting = ref(false)
const busy = ref(false)
const error = ref<string | null>(null)
const connections = ref<EudaConnectionStatus[]>([])

const selectedCarId = ref(props.cars.length === 1 ? props.cars[0].id : '')
const BRAND_BY_CAR: Record<string, EudaBrand> = { VW: 'volkswagen', VOLKSWAGEN: 'volkswagen', SKODA: 'skoda', AUDI: 'audi', SEAT: 'seat', CUPRA: 'cupra' }
function brandOfCar(carId: string): EudaBrand {
  const car = props.cars.find(c => c.id === carId)
  return (car && BRAND_BY_CAR[car.brand]) || 'volkswagen'
}
const brand = ref<EudaBrand>(brandOfCar(selectedCarId.value))
watch(selectedCarId, id => { brand.value = brandOfCar(id) })
const email = ref('')
const password = ref('')

const connection = computed(() => connections.value.find(c => c.carId === selectedCarId.value) ?? null)

async function load() {
  loading.value = true
  try {
    connections.value = await euDataActSyncService.getStatus()
    if (!selectedCarId.value && connections.value.length > 0) {
      selectedCarId.value = connections.value[0].carId
    }
  } catch {
    // Status ist optional - die Karte bleibt im "nicht verbunden"-Zustand nutzbar
  } finally {
    loading.value = false
  }
}

function describeError(err: unknown): string {
  const code = eudaErrorCode(err)
  const known: Record<string, string> = {
    INVALID_CREDENTIALS: t('eu_data_act_sync.err_invalid_credentials'),
    PORTAL_INTERACTION_REQUIRED: t('eu_data_act_sync.err_interaction_required'),
    PORTAL_UNAVAILABLE: t('eu_data_act_sync.err_portal_unavailable'),
    CAPACITY_REACHED: t('eu_data_act_sync.err_capacity'),
    RATE_LIMITED: t('eu_data_act_sync.err_rate_limited'),
  }
  return (code && known[code]) || t('eu_data_act_sync.err_generic')
}

async function connect() {
  if (!selectedCarId.value || !email.value || !password.value) return
  connecting.value = true
  error.value = null
  try {
    const status = await euDataActSyncService.connect(selectedCarId.value, brand.value, email.value, password.value)
    connections.value = [...connections.value.filter(c => c.carId !== status.carId), status]
  } catch (err) {
    error.value = describeError(err)
  } finally {
    // Das Passwort verlaesst den Browser genau einmal - danach ist es auch hier weg
    password.value = ''
    connecting.value = false
  }
}

async function run(action: () => Promise<void>) {
  busy.value = true
  error.value = null
  try {
    await action()
    await load()
  } catch (err) {
    error.value = describeError(err)
  } finally {
    busy.value = false
  }
}

const disconnect = () => run(() => euDataActSyncService.disconnect(selectedCarId.value))
const requestHistory = () => run(() => euDataActSyncService.requestHistory(selectedCarId.value))
const reactivateSmartcar = () => run(() => euDataActSyncService.reactivateSmartcar(selectedCarId.value))

const historyPending = ref(false)
async function onRequestHistory() {
  await requestHistory()
  if (!error.value) historyPending.value = true
}

onMounted(load)
</script>

<template>
  <div class="border-2 border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-900 rounded-sm shadow-[2px_2px_0_0_#d1d5db] dark:shadow-[2px_2px_0_0_#374151] p-4 md:p-5">
    <div class="flex items-start gap-3 mb-3">
      <BoltIcon class="h-6 w-6 shrink-0 text-indigo-500" aria-hidden="true" />
      <div class="min-w-0">
        <h3 class="font-bold text-gray-900 dark:text-gray-100 text-base md:text-lg">{{ t('eu_data_act_sync.title') }}</h3>
        <p class="text-sm text-gray-600 dark:text-gray-400">{{ t('eu_data_act_sync.desc') }}</p>
      </div>
      <span class="ml-auto shrink-0 text-[10px] font-bold uppercase tracking-wider px-2 py-0.5 rounded-sm bg-amber-100 text-amber-800 dark:bg-amber-900/40 dark:text-amber-300">Beta</span>
    </div>

    <!-- Free-Nutzer: Teaser ohne Blur -->
    <div v-if="!isPremium" class="flex items-start gap-3 rounded-sm border border-dashed border-gray-300 dark:border-gray-600 p-3">
      <LockClosedIcon class="h-5 w-5 shrink-0 text-gray-400" aria-hidden="true" />
      <p class="text-sm text-gray-600 dark:text-gray-400">{{ t('eu_data_act_sync.teaser') }}</p>
    </div>

    <template v-else>
      <CarSelectDropdown v-if="cars.length > 1" v-model="selectedCarId" :cars="cars" class="mb-3" />

      <div v-if="loading" class="text-sm text-gray-500 dark:text-gray-400">…</div>

      <!-- Verbunden -->
      <div v-else-if="connection" class="space-y-3">
        <div class="flex items-center gap-2">
          <CheckCircleIcon v-if="connection.status === 'ACTIVE'" class="h-5 w-5 text-emerald-500" aria-hidden="true" />
          <ExclamationTriangleIcon v-else class="h-5 w-5 text-red-500" aria-hidden="true" />
          <span class="text-sm font-medium text-gray-900 dark:text-gray-100">
            {{ connection.status === 'ACTIVE' ? t('eu_data_act_sync.status_active') : t('eu_data_act_sync.status_auth_failed') }}
          </span>
        </div>
        <dl class="grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-1 text-sm">
          <div class="flex justify-between sm:block">
            <dt class="text-gray-500 dark:text-gray-400">{{ t('eu_data_act_sync.label_account') }}</dt>
            <dd class="text-gray-900 dark:text-gray-100 truncate">{{ connection.email }}</dd>
          </div>
          <div class="flex justify-between sm:block">
            <dt class="text-gray-500 dark:text-gray-400">{{ t('eu_data_act_sync.label_last_sync') }}</dt>
            <dd class="text-gray-900 dark:text-gray-100">
              {{ connection.lastSuccessAt ? d(new Date(connection.lastSuccessAt), 'short') : t('eu_data_act_sync.waiting_first') }}
            </dd>
          </div>
        </dl>
        <p v-if="connection.status === 'AUTH_FAILED'" class="text-sm text-gray-600 dark:text-gray-400">
          {{ t('eu_data_act_sync.auth_failed_hint') }}
        </p>
        <p v-if="historyPending" class="text-sm text-gray-600 dark:text-gray-400">
          {{ t('eu_data_act_sync.history_pending') }}
        </p>
        <p v-else-if="connection.historyImportedAt" class="text-sm text-gray-600 dark:text-gray-400">
          {{ t('eu_data_act_sync.history_done', { date: d(new Date(connection.historyImportedAt), 'short') }) }}
        </p>

        <div class="flex flex-wrap gap-2">
          <button
            v-if="!connection.historyImportedAt && !historyPending"
            type="button"
            :disabled="busy"
            @click="onRequestHistory"
            data-testid="euda-history"
            class="inline-flex items-center gap-1.5 bg-gray-950 dark:bg-white text-white dark:text-gray-950 font-bold uppercase tracking-wider text-[11px] px-4 py-2.5 rounded-sm border-2 border-gray-950 dark:border-white disabled:opacity-60"
          >
            <ArrowPathIcon class="h-4 w-4" aria-hidden="true" />
            {{ t('eu_data_act_sync.btn_history') }}
          </button>
          <button
            type="button"
            :disabled="busy"
            @click="reactivateSmartcar"
            data-testid="euda-reactivate-smartcar"
            class="text-[11px] font-bold uppercase tracking-wider px-4 py-2.5 rounded-sm border-2 border-gray-300 dark:border-gray-600 text-gray-700 dark:text-gray-200 disabled:opacity-60"
          >
            {{ t('eu_data_act_sync.btn_reactivate_smartcar') }}
          </button>
          <button
            type="button"
            :disabled="busy"
            @click="disconnect"
            data-testid="euda-disconnect"
            class="text-[11px] font-bold uppercase tracking-wider px-4 py-2.5 rounded-sm border-2 border-red-300 dark:border-red-800 text-red-700 dark:text-red-300 disabled:opacity-60"
          >
            {{ t('eu_data_act_sync.btn_disconnect') }}
          </button>
        </div>
        <p class="text-xs text-gray-500 dark:text-gray-400">{{ t('eu_data_act_sync.history_hint') }}</p>
      </div>

      <!-- Nicht verbunden: Anmeldeformular -->
      <form v-else class="space-y-3" @submit.prevent="connect">
        <div>
          <span class="block text-xs font-medium text-gray-700 dark:text-gray-300 mb-1">{{ t('eu_data_act_sync.label_brand') }}</span>
          <div class="flex flex-wrap gap-2" role="radiogroup" :aria-label="t('eu_data_act_sync.label_brand')">
            <button
              v-for="b in BRANDS"
              :key="b.key"
              type="button"
              role="radio"
              :aria-checked="brand === b.key"
              @click="brand = b.key"
              class="px-3 py-2 rounded-sm border-2 text-sm font-medium"
              :class="brand === b.key
                ? 'border-indigo-600 bg-indigo-50 text-indigo-900 dark:bg-indigo-950/40 dark:text-indigo-200 dark:border-indigo-400'
                : 'border-gray-300 dark:border-gray-600 text-gray-700 dark:text-gray-200'"
            >{{ b.label }}</button>
          </div>
        </div>
        <div>
          <label for="euda-email" class="block text-xs font-medium text-gray-700 dark:text-gray-300 mb-1">{{ t('eu_data_act_sync.label_email') }}</label>
          <input id="euda-email" v-model="email" type="email" autocomplete="username" required
                 class="w-full rounded-sm border-2 border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 px-3 py-2 text-sm text-gray-900 dark:text-gray-100" />
        </div>
        <div>
          <label for="euda-password" class="block text-xs font-medium text-gray-700 dark:text-gray-300 mb-1">{{ t('eu_data_act_sync.label_password') }}</label>
          <input id="euda-password" v-model="password" type="password" autocomplete="current-password" required
                 class="w-full rounded-sm border-2 border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 px-3 py-2 text-sm text-gray-900 dark:text-gray-100" />
        </div>
        <ul class="text-xs text-gray-600 dark:text-gray-400 space-y-1 list-disc pl-4">
          <li>{{ t('eu_data_act_sync.privacy_password') }}</li>
          <li>{{ t('eu_data_act_sync.privacy_request') }}</li>
          <li>{{ t('eu_data_act_sync.privacy_smartcar') }}</li>
          <li>{{ t('eu_data_act_sync.privacy_revoke') }}</li>
        </ul>
        <button
          type="submit"
          data-testid="euda-connect"
          :disabled="connecting || !selectedCarId || !email || !password"
          class="w-full sm:w-auto bg-amber-500 hover:bg-amber-400 text-gray-950 font-bold uppercase tracking-wider text-xs md:text-sm px-5 py-3.5 rounded-sm border-2 border-amber-500 shadow-[2px_2px_0_0_#030712] active:translate-x-[4px] active:translate-y-[4px] disabled:opacity-60 disabled:cursor-not-allowed"
        >
          {{ connecting ? t('eu_data_act_sync.connecting') : t('eu_data_act_sync.btn_connect') }}
        </button>
      </form>

      <div v-if="error" class="mt-3 border-l-2 border-red-500 bg-red-50 dark:bg-red-950/40 px-4 py-3 rounded-r-sm text-sm text-red-800 dark:text-red-200" role="alert">
        {{ error }}
      </div>
    </template>
  </div>
</template>
