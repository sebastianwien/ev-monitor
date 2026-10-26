<script setup lang="ts">
/**
 * Polestar Data Portal, Stufe 2 "Testmodus": Nutzer gibt im Portal die Fahrzeugdaten für die
 * ev-monitor-ID frei und trägt hier die VIN ein. Wir schreiben Ladestand und Kilometerstand still
 * mit, eine Auswertung gibt es noch nicht. Zustände: Anleitung + VIN-Feld, verbunden (Status-Karte),
 * noch nicht freigegeben (Retry), Warteliste (Limit erreicht).
 */
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  ClipboardDocumentIcon, CheckIcon, ArrowTopRightOnSquareIcon, ExclamationTriangleIcon,
  ClockIcon, CheckCircleIcon, InformationCircleIcon, BellAlertIcon,
} from '@heroicons/vue/24/outline'
import { CI_BTN_PRIMARY, CI_BTN_SECONDARY, CI_INPUT, CI_LABEL, CI_CARD, CI_NOTE } from '../../utils/ciClasses'
import polestarService, { POLESTAR_SHARE_ACCOUNT, POLESTAR_PORTAL_URL, type PolestarConnection, type PolestarCapacity } from '@/api/polestarService'
import type { Car } from '@/api/carService'
import CarSelectDropdown from '@/components/car/CarSelectDropdown.vue'
import { carDisplayName } from '@/utils/enumLabel'
import { analytics } from '@/services/analytics'
import { useWaitlist } from '@/composables/useWaitlist'

const props = defineProps<{ cars: Car[] }>()
const { t, locale } = useI18n()

const connections = ref<PolestarConnection[]>([])
// Kostendeckel: ohne freien Platz zeigen wir nur die Warteliste, damit niemand die Portal-Freigabe umsonst macht.
const capacity = ref<PolestarCapacity | null>(null)
const slotsFull = computed(() => capacity.value !== null && capacity.value.slotsFree <= 0)
// Echte Warteliste: Opt-in je Nutzer, der Admin schaltet am Monatswechsel von Hand frei.
const waitlist = useWaitlist('POLESTAR_DATA_PORTAL')
const loading = ref(true)
const submitting = ref(false)
const selectedCarId = ref('')
const vin = ref('')
const outcome = ref<'NOT_SHARED' | 'WAITLIST' | null>(null)
const error = ref<string | null>(null)
const copied = ref(false)

const VIN_PATTERN = /^[A-HJ-NPR-Z0-9]{17}$/
const normalizedVin = computed(() => vin.value.trim().toUpperCase())
const vinValid = computed(() => VIN_PATTERN.test(normalizedVin.value))
const vinTouched = ref(false)

const activeConnection = computed(() =>
  connections.value.find(c => c.carId === selectedCarId.value && c.status === 'ACTIVE') ?? null,
)
const selectedCar = computed(() => props.cars.find(c => c.id === selectedCarId.value) ?? null)
const carLabel = (c: Car) => `${carDisplayName(c)} ${c.year ?? ''}`.trim()

onMounted(async () => {
  selectedCarId.value = props.cars.find(c => c.isPrimary)?.id ?? props.cars[0]?.id ?? ''
  await reload()
})

async function reload() {
  loading.value = true
  try {
    const [conns, cap] = await Promise.all([polestarService.getConnections(), polestarService.getCapacity(), waitlist.load()])
    connections.value = conns
    capacity.value = cap
  } catch {
    connections.value = []
  } finally {
    loading.value = false
  }
}

async function copyAccount() {
  try {
    await navigator.clipboard.writeText(POLESTAR_SHARE_ACCOUNT)
    copied.value = true
    setTimeout(() => { copied.value = false }, 2000)
  } catch { /* Clipboard nicht verfügbar: Text bleibt markierbar */ }
}

async function connect() {
  vinTouched.value = true
  if (!vinValid.value || !selectedCarId.value) return
  submitting.value = true
  error.value = null
  outcome.value = null
  try {
    const result = await polestarService.connect(selectedCarId.value, normalizedVin.value)
    if (result.result === 'ACTIVE') {
      analytics.trackPolestarConnect('success')
      vin.value = ''
      vinTouched.value = false
      await reload()
    } else if (result.result === 'WAITLIST') {
      analytics.trackPolestarConnect('waitlist')
      outcome.value = 'WAITLIST'
    } else {
      analytics.trackPolestarConnect('not_shared')
      outcome.value = 'NOT_SHARED'
    }
  } catch (e: any) {
    analytics.trackPolestarConnect('error')
    const code = e?.response?.data?.error
    if (code === 'VIN_TAKEN') error.value = t('polestar.err_vin_taken')
    else if (code === 'INVALID_VIN') error.value = t('polestar.err_vin_invalid')
    else if (e?.response?.status === 502) error.value = t('polestar.err_portal_unavailable')
    else error.value = t('polestar.err_generic')
  } finally {
    submitting.value = false
  }
}

async function disconnect() {
  const conn = activeConnection.value
  if (!conn?.id || !confirm(t('polestar.confirm_disconnect'))) return
  try {
    await polestarService.disconnect(conn.id)
    await reload()
  } catch {
    error.value = t('polestar.err_generic')
  }
}

/** Zeiten kommen vom Connector in UTC ohne Zonenangabe. */
const fmt = (iso: string | null) =>
  iso ? new Intl.DateTimeFormat(locale.value, { dateStyle: 'short', timeStyle: 'short' }).format(new Date(/[Z+]/.test(iso.slice(10)) ? iso : iso + 'Z')) : null
</script>

<template>
  <div class="grid gap-5" data-testid="polestar-integration">
    <!-- Einleitung: was, warum, wozu -->
    <div class="grid gap-2 text-[15px] leading-relaxed text-gray-700 dark:text-gray-300" data-testid="polestar-intro">
      <p>{{ t('polestar.intro_what') }}</p>
      <p>{{ t('polestar.intro_why') }}</p>
      <p class="font-semibold text-gray-900 dark:text-gray-100">{{ t('polestar.intro_ask') }}</p>
    </div>

    <!-- Fahrzeugwahl nur bei mehreren Polestar -->
    <div v-if="cars.length > 1" data-testid="polestar-car">
      <label :class="[CI_LABEL, 'mb-1.5 block']">{{ t('polestar.car_label') }}</label>
      <CarSelectDropdown v-model="selectedCarId" :cars="cars" />
    </div>

    <p v-if="loading" class="text-sm text-gray-500 dark:text-gray-400" data-testid="polestar-loading">{{ t('common.loading') }}</p>

    <!-- Verbunden: Status-Karte -->
    <div v-else-if="activeConnection" data-testid="polestar-status"
         :class="[CI_CARD, 'grid gap-3']">
      <div class="flex items-start gap-3">
        <CheckCircleIcon class="h-6 w-6 shrink-0 text-green-700 dark:text-green-400" aria-hidden="true" />
        <div class="min-w-0 flex-1">
          <p class="font-bold text-gray-900 dark:text-gray-100">{{ t('polestar.status_connected') }}</p>
          <p class="mt-0.5 text-[13px] text-gray-600 dark:text-gray-300">{{ selectedCar ? carLabel(selectedCar) : '' }} · VIN …{{ activeConnection.vin?.slice(-6) }}</p>
        </div>
      </div>
      <dl class="grid grid-cols-2 gap-x-4 gap-y-2 text-sm">
        <dt class="text-gray-500 dark:text-gray-400">{{ t('polestar.status_since') }}</dt>
        <dd class="text-right font-semibold tabular-nums text-gray-900 dark:text-gray-100">{{ fmt(activeConnection.connectedAt) ?? '-' }}</dd>
        <dt class="text-gray-500 dark:text-gray-400">{{ t('polestar.status_last_data') }}</dt>
        <dd class="text-right font-semibold tabular-nums text-gray-900 dark:text-gray-100" data-testid="polestar-last-data">
          {{ fmt(activeConnection.lastEventAt) ?? t('polestar.status_waiting') }}
        </dd>
      </dl>
      <p class="text-[13px] leading-relaxed text-gray-600 dark:text-gray-300">{{ t('polestar.testmode_short') }}</p>
      <button type="button" @click="disconnect" data-testid="polestar-disconnect"
              :class="[CI_BTN_SECONDARY, 'justify-self-start']">
        {{ t('polestar.disconnect') }}
      </button>
    </div>

    <!-- Alle Plätze belegt: nur Warteliste, keine Anleitung -->
    <div v-else-if="slotsFull" data-testid="polestar-capped"
         :class="[CI_NOTE, 'flex gap-3']">
      <ClockIcon class="h-5 w-5 shrink-0 text-gray-500 dark:text-gray-400" aria-hidden="true" />
      <div class="text-sm leading-relaxed text-gray-800 dark:text-gray-200">
        <p class="font-semibold text-gray-900 dark:text-gray-100">{{ t('polestar.waitlist_title') }}</p>
        <template v-if="waitlist.onWaitlist.value">
          <p class="mt-0.5 text-[13px]">{{ t('polestar.waitlist_joined') }}</p>
          <button type="button" :disabled="waitlist.busy.value" data-testid="polestar-waitlist-leave"
              class="mt-1 inline-flex min-h-11 items-center text-[13px] font-semibold text-gray-600 underline hover:text-gray-900 disabled:opacity-50 dark:text-gray-400 dark:hover:text-gray-100"
              @click="waitlist.leave">{{ t('polestar.waitlist_leave') }}</button>
        </template>
        <template v-else>
          <p class="mt-0.5 text-[13px]">{{ t('polestar.capped_desc') }}</p>
          <button type="button" :disabled="waitlist.busy.value" data-testid="polestar-waitlist-join"
              :class="[CI_BTN_SECONDARY, 'mt-2']"
              @click="waitlist.join">
          <BellAlertIcon class="h-5 w-5" aria-hidden="true" />
          <span>{{ waitlist.busy.value ? t('polestar.waitlist_joining') : t('polestar.waitlist_cta') }}</span>
          </button>
          <p v-if="waitlist.error.value" class="mt-2 text-[13px] font-semibold text-gray-900 dark:text-gray-100" role="alert">{{ waitlist.error.value }}</p>
        </template>
      </div>
    </div>

    <!-- Nicht verbunden: Anleitung + VIN -->
    <template v-else>
      <!-- Schritt 1 -->
      <section data-testid="polestar-step1">
        <h3 class="mb-2 flex items-center text-[15px] font-bold text-gray-900 dark:text-gray-100">
          <span class="mr-2 inline-grid h-6 w-6 shrink-0 place-items-center rounded-full bg-gray-900 text-xs font-extrabold tabular-nums text-white dark:bg-gray-100 dark:text-gray-900" aria-hidden="true">1</span>{{ t('polestar.step1_title') }}
        </h3>
        <ol class="list-decimal space-y-1.5 pl-5 text-sm leading-relaxed text-gray-700 dark:text-gray-300">
          <li>
            {{ t('polestar.step1_a') }}
            <a :href="POLESTAR_PORTAL_URL" target="_blank" rel="noopener" class="inline-flex items-center gap-1 font-semibold text-gray-900 underline hover:no-underline dark:text-gray-100">
              data-portal.polestar.com <ArrowTopRightOnSquareIcon class="h-3.5 w-3.5" aria-hidden="true" />
            </a>
          </li>
          <li>{{ t('polestar.step1_b') }}</li>
          <li>
            {{ t('polestar.step1_c') }}
            <span class="mt-1 inline-flex select-all items-center gap-1 rounded-xl bg-gray-100 py-0.5 pl-3 pr-0.5 font-mono text-[13px] text-gray-900 dark:bg-gray-800 dark:text-gray-100">
              {{ POLESTAR_SHARE_ACCOUNT }}
              <button type="button" @click="copyAccount" :aria-label="t('polestar.copy')" data-testid="polestar-copy"
                      class="grid h-11 w-11 place-items-center rounded-full hover:bg-black/10 dark:hover:bg-white/10">
                <CheckIcon v-if="copied" class="h-4 w-4 text-green-700 dark:text-green-400" aria-hidden="true" />
                <ClipboardDocumentIcon v-else class="h-4 w-4" aria-hidden="true" />
              </button>
            </span>
          </li>
          <li>{{ t('polestar.step1_d') }}</li>
          <li>{{ t('polestar.step1_e') }}</li>
        </ol>
      </section>

      <!-- Schritt 2 -->
      <section data-testid="polestar-step2">
        <h3 class="mb-2 flex items-center text-[15px] font-bold text-gray-900 dark:text-gray-100">
          <span class="mr-2 inline-grid h-6 w-6 shrink-0 place-items-center rounded-full bg-gray-900 text-xs font-extrabold tabular-nums text-white dark:bg-gray-100 dark:text-gray-900" aria-hidden="true">2</span>{{ t('polestar.step2_title') }}
        </h3>
        <form class="grid gap-3" @submit.prevent="connect">
          <div>
            <label for="polestar-vin" :class="[CI_LABEL, 'mb-1.5 block']">{{ t('polestar.vin_label') }}</label>
            <input id="polestar-vin" v-model="vin" type="text" inputmode="text" autocomplete="off" autocapitalize="characters"
                   maxlength="17" spellcheck="false" :placeholder="t('polestar.vin_placeholder')" data-testid="polestar-vin"
                   :aria-invalid="vinTouched && !vinValid ? 'true' : undefined" @blur="vinTouched = true"
                   :class="[CI_INPUT, 'font-mono uppercase tracking-wider', vinTouched && !vinValid ? 'border-amber-500 dark:border-amber-400' : '']" />
            <p v-if="vinTouched && !vinValid" class="mt-1 text-[13px] font-semibold text-amber-700 dark:text-amber-400" data-testid="polestar-vin-error">{{ t('polestar.vin_invalid_hint') }}</p>
            <p v-else class="mt-1 text-[13px] text-gray-500 dark:text-gray-400">{{ t('polestar.vin_hint') }}</p>
          </div>

          <button type="submit" :disabled="submitting" data-testid="polestar-connect"
                  :class="[CI_BTN_PRIMARY, 'w-full sm:w-auto sm:justify-self-start']">
            {{ submitting ? t('polestar.checking') : t('polestar.check_connection') }}
          </button>
        </form>

        <!-- Ergebnis: noch nicht freigegeben -->
        <div v-if="outcome === 'NOT_SHARED'" data-testid="polestar-not-shared"
             class="mt-3 flex gap-3 rounded-2xl bg-amber-50 p-4 dark:bg-amber-900/20">
          <ExclamationTriangleIcon class="h-5 w-5 text-amber-600 dark:text-amber-400 shrink-0" aria-hidden="true" />
          <div class="text-sm leading-relaxed text-gray-800 dark:text-gray-200">
            <p class="font-semibold text-gray-900 dark:text-gray-100">{{ t('polestar.not_shared_title') }}</p>
            <p class="mt-0.5 text-[13px]">{{ t('polestar.not_shared_desc') }}</p>
          </div>
        </div>

        <!-- Ergebnis: Warteliste -->
        <div v-else-if="outcome === 'WAITLIST'" data-testid="polestar-waitlist"
             :class="[CI_NOTE, 'mt-3 flex gap-3']">
          <ClockIcon class="h-5 w-5 shrink-0 text-gray-500 dark:text-gray-400" aria-hidden="true" />
          <div class="text-sm leading-relaxed text-gray-800 dark:text-gray-200">
            <p class="font-semibold text-gray-900 dark:text-gray-100">{{ t('polestar.waitlist_title') }}</p>
            <template v-if="waitlist.onWaitlist.value">
              <p class="mt-0.5 text-[13px]">{{ t('polestar.waitlist_joined') }}</p>
              <button type="button" :disabled="waitlist.busy.value" data-testid="polestar-waitlist-leave"
                        class="mt-1 inline-flex min-h-11 items-center text-[13px] font-semibold text-gray-600 underline hover:text-gray-900 disabled:opacity-50 dark:text-gray-400 dark:hover:text-gray-100"
                        @click="waitlist.leave">{{ t('polestar.waitlist_leave') }}</button>
            </template>
            <template v-else>
              <p class="mt-0.5 text-[13px]">{{ t('polestar.waitlist_desc') }}</p>
              <button type="button" :disabled="waitlist.busy.value" data-testid="polestar-waitlist-join"
                        :class="[CI_BTN_SECONDARY, 'mt-2']"
                        @click="waitlist.join">
                <BellAlertIcon class="h-5 w-5" aria-hidden="true" />
                <span>{{ waitlist.busy.value ? t('polestar.waitlist_joining') : t('polestar.waitlist_cta') }}</span>
              </button>
              <p v-if="waitlist.error.value" class="mt-2 text-[13px] font-semibold text-gray-900 dark:text-gray-100" role="alert">{{ waitlist.error.value }}</p>
            </template>
          </div>
        </div>

        <p v-if="error" class="mt-3 text-sm font-semibold text-gray-900 dark:text-gray-100" role="alert" data-testid="polestar-error">{{ error }}</p>
      </section>
    </template>

    <!-- Testmodus-Satz (DSGVO): immer sichtbar -->
    <div :class="[CI_NOTE, 'flex gap-3']" data-testid="polestar-testmode">
      <InformationCircleIcon class="h-5 w-5 shrink-0 text-gray-500 dark:text-gray-400" aria-hidden="true" />
      <p class="text-[13px] leading-relaxed">{{ t('polestar.testmode') }}</p>
    </div>
  </div>
</template>
