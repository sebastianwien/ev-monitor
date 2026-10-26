<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { BoltIcon, ChevronRightIcon, ChevronDownIcon, ArrowLeftIcon, ArrowTopRightOnSquareIcon, CheckCircleIcon, ExclamationCircleIcon } from '@heroicons/vue/24/outline'
import LazyCollapse from '../shared/LazyCollapse.vue'
import { CI_BTN_PRIMARY, CI_INPUT, CI_LABEL, CI_CARD, CI_NOTE } from '../../utils/ciClasses'
import vwEudaSyncService, {
  eudaBrandOf,
  eudaErrorCode,
  type VwEudaBrand,
  type VwEudaConnectionStatus,
  type VwEudaEntitlement,
} from '../../api/vwEudaSyncService'
import type { Car } from '../../api/carService'
import smartcarService, { connectionsOf } from '../../api/smartcarService'
import CarSelectDropdown from '../car/CarSelectDropdown.vue'
import VwEudaExplainer from './VwEudaExplainer.vue'
import VwEudaSyncActivity from './VwEudaSyncActivity.vue'

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
const LOGIN_SOURCE_URL = 'https://github.com/sebastianwien/ev-monitor/blob/main/backend/src/main/java/com/evmonitor/application/vweuda/VwEudaLoginClient.java'

const BRANDS: { key: VwEudaBrand; label: string }[] = [
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
const connections = ref<VwEudaConnectionStatus[]>([])
/** Fahrzeug-ID, die aktuell ueber Smartcar haengt - nur dann ist der Dubletten-Hinweis relevant. */
const smartcarCarIds = ref<string[]>([])
/** decide: Entscheidung ohne Formular. connect: Marke, E-Mail, Passwort. */
const step = ref<'decide' | 'connect'>('decide')
const detailsOpen = ref(false)
/** Direkt nach einem erfolgreichen Verbinden: Erfolgssatz ueber der Status-Karte. */
const justConnected = ref(false)
// Ohne Antwort vom Core gilt "nicht berechtigt" - das Backend ist ohnehin die Sicherheitsgrenze,
// hier geht es nur darum, keinem ein Formular zu zeigen, das dann mit 403 endet.
const entitlement = ref<VwEudaEntitlement>({ entitled: false, viaTrial: false, trialEndsAt: null })

const selectedCarId = ref(props.cars.length === 1 ? props.cars[0].id : '')
function brandOfCar(carId: string): VwEudaBrand {
  const car = props.cars.find(c => c.id === carId)
  return (car && eudaBrandOf(car.brand)) || 'volkswagen'
}
const brand = ref<VwEudaBrand>(brandOfCar(selectedCarId.value))
watch(selectedCarId, id => { brand.value = brandOfCar(id) })
const email = ref('')
const password = ref('')

const connection = computed(() => connections.value.find(c => c.carId === selectedCarId.value) ?? null)
const viaSmartcar = computed(() => !!selectedCarId.value && smartcarCarIds.value.includes(selectedCarId.value))

async function load() {
  loading.value = true
  try {
    const [status, ent, smartcar] = await Promise.all([
      vwEudaSyncService.getStatus().catch(() => [] as VwEudaConnectionStatus[]),
      vwEudaSyncService.getEntitlement().catch(() => entitlement.value),
      smartcarService.getStatus().catch(() => null),
    ])
    connections.value = status
    entitlement.value = ent
    smartcarCarIds.value = connectionsOf(smartcar).map(c => c.carId).filter((id): id is string => !!id)
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
    const status = await vwEudaSyncService.connect(selectedCarId.value, brand.value, email.value, password.value)
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

const disconnect = () => run(() => vwEudaSyncService.disconnect(selectedCarId.value))
const requestHistory = () => run(() => vwEudaSyncService.requestHistory(selectedCarId.value))
const reactivateSmartcar = () => run(() => vwEudaSyncService.reactivateSmartcar(selectedCarId.value))

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
  <div class="grid gap-3" :class="embedded ? '' : CI_CARD">
    <div v-if="!embedded" class="flex items-start gap-3">
      <BoltIcon class="h-6 w-6 shrink-0 text-gray-500 dark:text-gray-400" aria-hidden="true" />
      <div class="min-w-0">
        <h3 class="text-[18px] font-bold tracking-tight text-gray-900 dark:text-gray-100">{{ t('eu_data_act_sync.title') }}</h3>
        <p v-if="!connection" class="text-sm text-gray-600 dark:text-gray-400">{{ t('eu_data_act_sync.desc') }}</p>
      </div>
      <span class="ml-auto shrink-0 rounded-full bg-gray-100 px-2 py-0.5 text-[11.5px] font-bold uppercase tracking-wide text-gray-600 dark:bg-gray-800 dark:text-gray-300">Beta</span>
    </div>

      <CarSelectDropdown v-if="cars.length > 1" v-model="selectedCarId" :cars="cars" />

      <div v-if="loading" class="text-sm text-gray-500 dark:text-gray-400">…</div>

      <!-- Verbunden: das Panel (Lagebild, Fakten, Handlung, Details) gehört VwEudaSyncActivity -->
      <div v-else-if="connection" class="grid gap-3" data-testid="euda-connected">
        <p v-if="justConnected" role="status" :class="[CI_NOTE, 'flex items-start gap-2 py-3 font-semibold text-gray-900 dark:text-gray-100']" data-testid="euda-success">
          <CheckCircleIcon class="h-5 w-5 shrink-0 text-green-700 dark:text-green-400" aria-hidden="true" />{{ t('eu_data_act_sync.connected_success') }}
        </p>
        <VwEudaSyncActivity
          :connection="connection"
          :version="activityVersion"
          :trial-ends-at="entitlement.viaTrial ? trialEndsAt : undefined"
          :history-pending="historyPending"
          :busy="busy"
          @request-history="onRequestHistory"
          @reactivate-smartcar="reactivateSmartcar"
          @disconnect="disconnect"
        />
      </div>

      <!-- Nicht verbunden, nicht berechtigt: Teaser statt Formular -->
      <div v-else-if="!entitlement.entitled" class="grid gap-3" data-testid="euda-teaser">
        <p class="text-sm text-gray-600 dark:text-gray-400">{{ t('eu_data_act_sync.teaser') }}</p>
        <p v-if="entitlement.trialEndsAt" class="text-[13px] text-gray-500 dark:text-gray-400">
          {{ t('eu_data_act_sync.trial_ended', { date: trialEndsAt }) }}
        </p>
        <router-link to="/upgrade" data-testid="euda-upgrade" :class="[CI_BTN_PRIMARY, 'justify-self-start']">
          {{ t('eu_data_act_sync.teaser_cta') }}
          <ChevronRightIcon class="h-4 w-4" aria-hidden="true" />
        </router-link>
      </div>

      <!-- Schritt 1: Entscheiden - drei Saetze, Trial, Details auf Wunsch -->
      <div v-else-if="step === 'decide'" class="grid gap-4" data-testid="euda-step-decide">
        <p class="text-[15px] leading-relaxed text-gray-800 dark:text-gray-200">{{ t('eu_data_act_sync.decide_pitch') }}</p>
        <p v-if="entitlement.viaTrial" class="text-sm text-gray-600 dark:text-gray-400" data-testid="euda-trial-hint">
          {{ t('eu_data_act_sync.trial_hint', { date: trialEndsAt }) }}
        </p>
        <button type="button" data-testid="euda-start" :class="[CI_BTN_PRIMARY, 'w-full sm:w-auto sm:justify-self-start']" @click="step = 'connect'">
          {{ t('eu_data_act_sync.btn_start') }}
        </button>
        <div>
          <button
            type="button"
            data-testid="euda-details-toggle"
            :aria-expanded="detailsOpen"
            aria-controls="euda-details"
            @click="detailsOpen = !detailsOpen"
            class="inline-flex min-h-11 items-center gap-1 text-sm font-semibold text-gray-900 underline underline-offset-2 dark:text-gray-100"
          >
            {{ t('eu_data_act_sync.details_toggle') }}
            <ChevronDownIcon class="h-4 w-4 transition-transform duration-300 motion-reduce:transition-none"
              :class="detailsOpen ? 'rotate-180 ease-out' : 'ease-in'" aria-hidden="true" />
          </button>
          <LazyCollapse id="euda-details" :open="detailsOpen">
            <VwEudaExplainer class="mt-2 border-t border-gray-200 pt-3 dark:border-white/10" />
          </LazyCollapse>
        </div>
      </div>

      <!-- Schritt 2: Anmelden -->
      <form v-else class="grid gap-3" data-testid="euda-step-connect" @submit.prevent="connect">
        <button
          type="button"
          data-testid="euda-back"
          @click="step = 'decide'"
          class="inline-flex min-h-11 items-center gap-1 justify-self-start text-sm font-semibold text-gray-600 hover:text-gray-900 dark:text-gray-400 dark:hover:text-gray-100"
        >
          <ArrowLeftIcon class="h-4 w-4" aria-hidden="true" />
          {{ t('common.back') }}
        </button>
        <p class="text-sm text-gray-700 dark:text-gray-300">{{ t('eu_data_act_sync.connect_intro') }}</p>
        <div class="grid gap-1.5">
          <span :class="CI_LABEL">{{ t('eu_data_act_sync.label_brand') }}</span>
          <!-- Antwort-Chips (ChoiceChip-Optik), als Radiogruppe ausgezeichnet -->
          <div class="flex flex-wrap gap-2" role="radiogroup" :aria-label="t('eu_data_act_sync.label_brand')">
            <button
              v-for="b in BRANDS"
              :key="b.key"
              type="button"
              role="radio"
              :aria-checked="brand === b.key"
              @click="brand = b.key"
              class="btn-3d cta-shadow-sm inline-flex min-h-11 items-center rounded-full border-2 px-4 text-sm font-semibold transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-green-600"
              :class="brand === b.key
                ? 'active border-gray-900 bg-gray-900 text-white dark:border-gray-100 dark:bg-gray-100 dark:text-gray-900'
                : 'border-edge bg-white text-gray-800 hover:bg-gray-50 dark:bg-white/10 dark:text-gray-200 dark:hover:bg-white/15'"
            >{{ b.label }}</button>
          </div>
        </div>
        <div class="grid gap-1.5">
          <label for="euda-email" :class="CI_LABEL">{{ t('eu_data_act_sync.label_email') }}</label>
          <input id="euda-email" v-model="email" type="email" autocomplete="username" required :class="CI_INPUT" />
        </div>
        <div class="grid gap-1.5">
          <label for="euda-password" :class="CI_LABEL">{{ t('eu_data_act_sync.label_password') }}</label>
          <input id="euda-password" v-model="password" type="password" autocomplete="current-password" required :class="CI_INPUT" />
        </div>
        <ul class="list-disc space-y-1 pl-4 text-[13px] text-gray-600 dark:text-gray-400">
          <li>{{ t('eu_data_act_sync.privacy_password') }}</li>
          <li>{{ t('eu_data_act_sync.privacy_request') }}</li>
          <li v-if="viaSmartcar" data-testid="euda-smartcar-note">{{ t('eu_data_act_sync.privacy_smartcar') }}</li>
          <li>
            {{ t('eu_data_act_sync.privacy_open_source') }}
            <a :href="LOGIN_SOURCE_URL" target="_blank" rel="noopener noreferrer" data-testid="euda-open-source"
               class="inline-flex items-center gap-1 font-semibold text-gray-900 underline dark:text-gray-100">
              {{ t('eu_data_act_sync.privacy_open_source_link') }}
              <ArrowTopRightOnSquareIcon class="h-3 w-3" aria-hidden="true" />
            </a>
          </li>
        </ul>
        <button
          type="submit"
          data-testid="euda-connect"
          :disabled="connecting || !selectedCarId || !email || !password"
          :class="[CI_BTN_PRIMARY, 'w-full sm:w-auto sm:justify-self-start']"
        >
          {{ connecting ? t('eu_data_act_sync.connecting') : t('eu_data_act_sync.btn_connect') }}
        </button>
      </form>

      <p v-if="error" role="alert" :class="[CI_NOTE, 'flex items-start gap-2 py-3 font-semibold text-gray-900 dark:text-gray-100']">
        <ExclamationCircleIcon class="h-5 w-5 shrink-0 text-gray-700 dark:text-gray-300" aria-hidden="true" />{{ error }}
      </p>
  </div>
</template>
