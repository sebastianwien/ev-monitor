<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { BoltIcon, CheckCircleIcon, ExclamationTriangleIcon, ArrowPathIcon, LockClosedIcon, ChevronRightIcon, ChevronDownIcon, ArrowLeftIcon, ArrowTopRightOnSquareIcon } from '@heroicons/vue/24/outline'
import euDataActSyncService, {
  eudaBrandOf,
  eudaErrorCode,
  type EudaBrand,
  type EudaConnectionStatus,
  type EudaEntitlement,
} from '../../api/euDataActSyncService'
import type { Car } from '../../api/carService'
import smartcarService from '../../api/smartcarService'
import CarSelectDropdown from '../car/CarSelectDropdown.vue'
import EudaExplainer from './EudaExplainer.vue'
import EudaSyncActivity from './EudaSyncActivity.vue'

/**
 * VW EU-Data-Act-AutoSync: Nutzer meldet sich einmal mit seiner Marken-ID an, danach holt
 * ev-monitor die Ladevorgaenge alle 15 Minuten aus dem Portal. Das Passwort wird nur fuer die
 * Anmeldung uebertragen und nicht gespeichert - das sagt die Karte auch so.
 *
 * Zwei Schritte statt einer Textwand: erst die Entscheidung (drei Saetze, Trial, Details auf
 * Wunsch), dann das Anmeldeformular. Danach zeigt die Status-Karte, dass es geklappt hat.
 */
const props = withDefaults(defineProps<{
  cars: Car[]
  /** Im Modal nach dem Anlegen eingebettet: dort rahmt das Modal, die Karte selbst bleibt ohne Rahmen. */
  embedded?: boolean
}>(), { embedded: false })

const { t, locale } = useI18n()

/** Die eine Klasse im oeffentlichen Repo, die das Passwort sieht - damit die Aussage "nicht gespeichert" pruefbar ist. */
const LOGIN_SOURCE_URL = 'https://github.com/sebastianwien/ev-monitor/blob/main/backend/src/main/java/com/evmonitor/application/euda/EudaLoginClient.java'

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
/** Fahrzeug-ID, die aktuell ueber Smartcar haengt - nur dann ist der Dubletten-Hinweis relevant. */
const smartcarCarId = ref<string | null>(null)
/** decide: Entscheidung ohne Formular. connect: Marke, E-Mail, Passwort. */
const step = ref<'decide' | 'connect'>('decide')
const detailsOpen = ref(false)
/** Direkt nach einem erfolgreichen Verbinden: Erfolgssatz ueber der Status-Karte. */
const justConnected = ref(false)
// Ohne Antwort vom Core gilt "nicht berechtigt" - das Backend ist ohnehin die Sicherheitsgrenze,
// hier geht es nur darum, keinem ein Formular zu zeigen, das dann mit 403 endet.
const entitlement = ref<EudaEntitlement>({ entitled: false, viaTrial: false, trialEndsAt: null })

const selectedCarId = ref(props.cars.length === 1 ? props.cars[0].id : '')
function brandOfCar(carId: string): EudaBrand {
  const car = props.cars.find(c => c.id === carId)
  return (car && eudaBrandOf(car.brand)) || 'volkswagen'
}
const brand = ref<EudaBrand>(brandOfCar(selectedCarId.value))
watch(selectedCarId, id => { brand.value = brandOfCar(id) })
const email = ref('')
const password = ref('')

const connection = computed(() => connections.value.find(c => c.carId === selectedCarId.value) ?? null)
const viaSmartcar = computed(() => !!smartcarCarId.value && smartcarCarId.value === selectedCarId.value)

async function load() {
  loading.value = true
  try {
    const [status, ent, smartcar] = await Promise.all([
      euDataActSyncService.getStatus().catch(() => [] as EudaConnectionStatus[]),
      euDataActSyncService.getEntitlement().catch(() => entitlement.value),
      smartcarService.getStatus().catch(() => null),
    ])
    connections.value = status
    entitlement.value = ent
    smartcarCarId.value = smartcar?.connected ? smartcar.carId : null
    if (!selectedCarId.value && connections.value.length > 0) {
      selectedCarId.value = connections.value[0].carId
    }
  } finally {
    loading.value = false
  }
}

// Kein vue-i18n-Datumsformat konfiguriert - deshalb direkt ueber Intl mit der aktiven Sprache.
const formatDate = (iso: string, withTime = false) => new Date(iso).toLocaleDateString(locale.value, {
  day: '2-digit', month: '2-digit', year: 'numeric', ...(withTime ? { hour: '2-digit', minute: '2-digit' } : {}),
})
const trialEndsAt = computed(() => entitlement.value.trialEndsAt ? formatDate(entitlement.value.trialEndsAt) : '')

function describeError(err: unknown): string {
  const code = eudaErrorCode(err)
  const known: Record<string, string> = {
    INVALID_CREDENTIALS: t('eu_data_act_sync.err_invalid_credentials'),
    NOT_ENTITLED: t('eu_data_act_sync.err_not_entitled'),
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
    justConnected.value = true
    step.value = 'decide'
  } catch (err) {
    error.value = describeError(err)
  } finally {
    // Das Passwort verlaesst den Browser genau einmal - danach ist es auch hier weg
    password.value = ''
    connecting.value = false
  }
}

/** Zaehlt hoch, wenn sich die Verbindung geaendert hat - das Sync-Protokoll laedt dann neu. */
const activityVersion = ref(0)

async function run(action: () => Promise<void>) {
  busy.value = true
  error.value = null
  try {
    await action()
    await load()
    activityVersion.value++
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
  <!-- Die Marken-Vorauswahl trifft die Import-Seite; hier entscheidet nur noch die Berechtigung
       (Abo oder launch-verankertes Trial), welcher Zustand zu sehen ist. -->
  <div :class="embedded ? '' : 'border-2 border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-900 rounded-sm shadow-[2px_2px_0_0_#d1d5db] dark:shadow-[2px_2px_0_0_#374151] p-4 md:p-5'">
    <div v-if="!embedded" class="flex items-start gap-3 mb-3">
      <BoltIcon class="h-6 w-6 shrink-0 text-indigo-500" aria-hidden="true" />
      <div class="min-w-0">
        <h3 class="font-bold text-gray-900 dark:text-gray-100 text-base md:text-lg">{{ t('eu_data_act_sync.title') }}</h3>
        <p class="text-sm text-gray-600 dark:text-gray-400">{{ t('eu_data_act_sync.desc') }}</p>
      </div>
      <span class="ml-auto shrink-0 text-[10px] font-bold uppercase tracking-wider px-2 py-0.5 rounded-sm bg-amber-100 text-amber-800 dark:bg-amber-900/40 dark:text-amber-300">Beta</span>
    </div>

      <CarSelectDropdown v-if="cars.length > 1" v-model="selectedCarId" :cars="cars" class="mb-3" />

      <div v-if="loading" class="text-sm text-gray-500 dark:text-gray-400">…</div>

      <!-- Verbunden -->
      <div v-else-if="connection" class="space-y-3" data-testid="euda-connected">
        <p v-if="justConnected" class="text-sm font-medium text-emerald-800 dark:text-emerald-300 border-l-2 border-emerald-500 bg-emerald-50 dark:bg-emerald-950/40 px-3 py-2 rounded-r-sm" data-testid="euda-success">
          {{ t('eu_data_act_sync.connected_success') }}
        </p>
        <div class="flex items-center gap-2">
          <CheckCircleIcon v-if="connection.status === 'ACTIVE'" class="h-5 w-5 text-emerald-500" aria-hidden="true" />
          <LockClosedIcon v-else-if="connection.status === 'EXPIRED'" class="h-5 w-5 text-amber-500" aria-hidden="true" />
          <ExclamationTriangleIcon v-else class="h-5 w-5 text-red-500" aria-hidden="true" />
          <span class="text-sm font-medium text-gray-900 dark:text-gray-100">
            {{ t(`eu_data_act_sync.status_${connection.status.toLowerCase()}`) }}
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
              {{ connection.lastSuccessAt ? formatDate(connection.lastSuccessAt, true) : t('eu_data_act_sync.waiting_first') }}
            </dd>
          </div>
        </dl>
        <p v-if="connection.status === 'AUTH_FAILED'" class="text-sm text-gray-600 dark:text-gray-400">
          {{ t('eu_data_act_sync.auth_failed_hint') }}
        </p>
        <p v-if="connection.status === 'EXPIRED'" class="text-sm text-gray-600 dark:text-gray-400" data-testid="euda-expired-hint">
          {{ t('eu_data_act_sync.expired_hint') }}
        </p>
        <p v-else-if="connection.status === 'ACTIVE' && entitlement.viaTrial" class="text-sm text-amber-800 dark:text-amber-300" data-testid="euda-trial-hint">
          {{ t('eu_data_act_sync.trial_hint_connected', { date: trialEndsAt }) }}
        </p>
        <p v-if="historyPending" class="text-sm text-gray-600 dark:text-gray-400">
          {{ t('eu_data_act_sync.history_pending') }}
        </p>

        <!-- Sync-Protokoll: was der Hersteller liefert, was daraus wird, Beschwerde bei Bedarf.
             Historie und Fehler zeigt das Protokoll selbst. -->
        <EudaSyncActivity :car-id="connection.carId" :version="activityVersion" />

        <div class="flex flex-wrap gap-2">
          <router-link
            v-if="connection.status === 'EXPIRED'"
            to="/upgrade"
            data-testid="euda-upgrade"
            class="inline-flex items-center gap-1.5 bg-amber-500 hover:bg-amber-400 text-gray-950 font-bold uppercase tracking-wider text-[11px] px-4 py-2.5 rounded-sm border-2 border-amber-500"
          >
            {{ t('eu_data_act_sync.teaser_cta') }}
            <ChevronRightIcon class="h-3.5 w-3.5" aria-hidden="true" />
          </router-link>
          <button
            v-if="connection.status !== 'EXPIRED' && !connection.historyImportedAt && !historyPending"
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
            v-if="connection.status !== 'EXPIRED'"
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

      <!-- Nicht verbunden, nicht berechtigt: Teaser statt Formular -->
      <div v-else-if="!entitlement.entitled" class="space-y-3" data-testid="euda-teaser">
        <p class="text-sm text-gray-600 dark:text-gray-400">{{ t('eu_data_act_sync.teaser') }}</p>
        <p v-if="entitlement.trialEndsAt" class="text-xs text-gray-500 dark:text-gray-400">
          {{ t('eu_data_act_sync.trial_ended', { date: trialEndsAt }) }}
        </p>
        <router-link
          to="/upgrade"
          data-testid="euda-upgrade"
          class="inline-flex items-center gap-1.5 bg-amber-500 hover:bg-amber-400 text-gray-950 font-bold uppercase tracking-wider text-[11px] px-4 py-2.5 rounded-sm border-2 border-amber-500"
        >
          {{ t('eu_data_act_sync.teaser_cta') }}
          <ChevronRightIcon class="h-3.5 w-3.5" aria-hidden="true" />
        </router-link>
      </div>

      <!-- Schritt 1: Entscheiden - drei Saetze, Trial, Details auf Wunsch -->
      <div v-else-if="step === 'decide'" class="space-y-4" data-testid="euda-step-decide">
        <p class="text-sm text-gray-800 dark:text-gray-200 leading-relaxed">{{ t('eu_data_act_sync.decide_pitch') }}</p>
        <p v-if="entitlement.viaTrial" class="text-sm text-gray-600 dark:text-gray-400" data-testid="euda-trial-hint">
          {{ t('eu_data_act_sync.trial_hint', { date: trialEndsAt }) }}
        </p>
        <button
          type="button"
          data-testid="euda-start"
          @click="step = 'connect'"
          class="w-full sm:w-auto bg-amber-500 hover:bg-amber-400 text-gray-950 font-bold uppercase tracking-wider text-xs md:text-sm px-5 py-3.5 rounded-sm border-2 border-amber-500 shadow-[2px_2px_0_0_#030712] active:translate-x-[4px] active:translate-y-[4px]"
        >
          {{ t('eu_data_act_sync.btn_start') }}
        </button>
        <div>
          <button
            type="button"
            data-testid="euda-details-toggle"
            :aria-expanded="detailsOpen"
            aria-controls="euda-details"
            @click="detailsOpen = !detailsOpen"
            class="inline-flex items-center gap-1 text-sm font-medium text-indigo-700 dark:text-indigo-300 underline min-h-[44px]"
          >
            {{ t('eu_data_act_sync.details_toggle') }}
            <ChevronDownIcon class="h-4 w-4 transition-transform" :class="detailsOpen ? 'rotate-180' : ''" aria-hidden="true" />
          </button>
          <EudaExplainer v-if="detailsOpen" id="euda-details" class="mt-3 pt-3 border-t-2 border-gray-200 dark:border-gray-700" />
        </div>
      </div>

      <!-- Schritt 2: Anmelden -->
      <form v-else class="space-y-3" data-testid="euda-step-connect" @submit.prevent="connect">
        <button
          type="button"
          data-testid="euda-back"
          @click="step = 'decide'"
          class="inline-flex items-center gap-1 text-sm text-gray-500 dark:text-gray-400 hover:text-gray-800 dark:hover:text-gray-200 min-h-[44px]"
        >
          <ArrowLeftIcon class="h-4 w-4" aria-hidden="true" />
          {{ t('common.back') }}
        </button>
        <p class="text-sm text-gray-700 dark:text-gray-300">{{ t('eu_data_act_sync.connect_intro') }}</p>
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
          <li v-if="viaSmartcar" data-testid="euda-smartcar-note">{{ t('eu_data_act_sync.privacy_smartcar') }}</li>
          <li>
            {{ t('eu_data_act_sync.privacy_open_source') }}
            <a :href="LOGIN_SOURCE_URL" target="_blank" rel="noopener noreferrer" data-testid="euda-open-source"
               class="inline-flex items-center gap-1 text-indigo-700 dark:text-indigo-300 underline font-medium">
              {{ t('eu_data_act_sync.privacy_open_source_link') }}
              <ArrowTopRightOnSquareIcon class="h-3 w-3" aria-hidden="true" />
            </a>
          </li>
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
  </div>
</template>
