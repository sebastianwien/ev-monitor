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
  ClockIcon, CheckCircleIcon, InformationCircleIcon,
} from '@heroicons/vue/24/outline'
import polestarService, { POLESTAR_SHARE_ACCOUNT, POLESTAR_PORTAL_URL, type PolestarConnection, type PolestarCapacity } from '@/api/polestarService'
import type { Car } from '@/api/carService'
import CarSelectDropdown from '@/components/car/CarSelectDropdown.vue'
import { carDisplayName } from '@/utils/enumLabel'
import { analytics } from '@/services/analytics'

const props = defineProps<{ cars: Car[] }>()
const { t, locale } = useI18n()

const connections = ref<PolestarConnection[]>([])
// Kostendeckel: ohne freien Platz zeigen wir nur die Warteliste, damit niemand die Portal-Freigabe umsonst macht.
const capacity = ref<PolestarCapacity | null>(null)
const slotsFull = computed(() => capacity.value !== null && capacity.value.slotsFree <= 0)
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
const carLabel = (c: Car) => `${carDisplayName(c.brand, c.model)} ${c.year ?? ''}`.trim()

onMounted(async () => {
  selectedCarId.value = props.cars.find(c => c.isPrimary)?.id ?? props.cars[0]?.id ?? ''
  await reload()
})

async function reload() {
  loading.value = true
  try {
    const [conns, cap] = await Promise.all([polestarService.getConnections(), polestarService.getCapacity()])
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
  <div class="space-y-5" data-testid="polestar-integration">
    <!-- Einleitung: was, warum, wozu -->
    <div class="space-y-2 text-sm text-gray-700 dark:text-gray-300 leading-relaxed" data-testid="polestar-intro">
      <p>{{ t('polestar.intro_what') }}</p>
      <p>{{ t('polestar.intro_why') }}</p>
      <p class="font-medium text-gray-900 dark:text-gray-100">{{ t('polestar.intro_ask') }}</p>
    </div>

    <!-- Fahrzeugwahl nur bei mehreren Polestar -->
    <div v-if="cars.length > 1" data-testid="polestar-car">
      <label class="block text-xs font-bold uppercase tracking-wider text-gray-600 dark:text-gray-400 mb-1">{{ t('polestar.car_label') }}</label>
      <CarSelectDropdown v-model="selectedCarId" :cars="cars" />
    </div>

    <p v-if="loading" class="text-sm text-gray-500 dark:text-gray-400" data-testid="polestar-loading">{{ t('common.loading') }}</p>

    <!-- Verbunden: Status-Karte -->
    <div v-else-if="activeConnection" data-testid="polestar-status"
         class="rounded-sm border-2 border-emerald-500 bg-emerald-50 dark:bg-emerald-950/30 p-4 space-y-3">
      <div class="flex items-start gap-3">
        <CheckCircleIcon class="h-6 w-6 text-emerald-600 dark:text-emerald-400 shrink-0" aria-hidden="true" />
        <div class="min-w-0 flex-1">
          <p class="font-bold text-gray-900 dark:text-gray-100">{{ t('polestar.status_connected') }}</p>
          <p class="text-xs text-gray-600 dark:text-gray-300 mt-0.5">{{ selectedCar ? carLabel(selectedCar) : '' }} · VIN …{{ activeConnection.vin?.slice(-6) }}</p>
        </div>
      </div>
      <dl class="grid grid-cols-2 gap-x-4 gap-y-2 text-sm">
        <dt class="text-gray-600 dark:text-gray-400">{{ t('polestar.status_since') }}</dt>
        <dd class="text-gray-900 dark:text-gray-100 font-medium text-right">{{ fmt(activeConnection.connectedAt) ?? '-' }}</dd>
        <dt class="text-gray-600 dark:text-gray-400">{{ t('polestar.status_last_data') }}</dt>
        <dd class="text-gray-900 dark:text-gray-100 font-medium text-right" data-testid="polestar-last-data">
          {{ fmt(activeConnection.lastEventAt) ?? t('polestar.status_waiting') }}
        </dd>
      </dl>
      <p class="text-xs text-gray-700 dark:text-gray-300 leading-relaxed">{{ t('polestar.testmode_short') }}</p>
      <button type="button" @click="disconnect" data-testid="polestar-disconnect"
              class="text-xs font-bold uppercase tracking-wider text-red-700 dark:text-red-400 underline hover:no-underline">
        {{ t('polestar.disconnect') }}
      </button>
    </div>

    <!-- Alle Plätze belegt: nur Warteliste, keine Anleitung -->
    <div v-else-if="slotsFull" data-testid="polestar-capped"
         class="border-l-2 border-gray-400 bg-gray-50 dark:bg-gray-800 px-4 py-3 rounded-r-sm flex gap-3">
      <ClockIcon class="h-5 w-5 text-gray-500 shrink-0" aria-hidden="true" />
      <div class="text-sm text-gray-800 dark:text-gray-200 leading-relaxed">
        <p class="font-bold">{{ t('polestar.waitlist_title') }}</p>
        <p class="text-xs mt-0.5">{{ t('polestar.capped_desc') }}</p>
      </div>
    </div>

    <!-- Nicht verbunden: Anleitung + VIN -->
    <template v-else>
      <!-- Schritt 1 -->
      <section data-testid="polestar-step1">
        <h3 class="text-sm font-bold text-gray-900 dark:text-gray-100 mb-2">
          <span class="inline-flex items-center justify-center w-6 h-6 rounded-full bg-gray-900 dark:bg-gray-100 text-white dark:text-gray-900 text-xs font-extrabold mr-2">1</span>{{ t('polestar.step1_title') }}
        </h3>
        <ol class="list-decimal pl-5 space-y-1.5 text-sm text-gray-700 dark:text-gray-300 leading-relaxed">
          <li>
            {{ t('polestar.step1_a') }}
            <a :href="POLESTAR_PORTAL_URL" target="_blank" rel="noopener" class="inline-flex items-center gap-1 font-bold underline hover:no-underline">
              data-portal.polestar.com <ArrowTopRightOnSquareIcon class="h-3.5 w-3.5" aria-hidden="true" />
            </a>
          </li>
          <li>{{ t('polestar.step1_b') }}</li>
          <li>
            {{ t('polestar.step1_c') }}
            <span class="inline-flex items-center gap-1 mt-1 rounded-sm border-2 border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 pl-2 pr-1 py-0.5 font-mono text-xs text-gray-900 dark:text-gray-100 select-all">
              {{ POLESTAR_SHARE_ACCOUNT }}
              <button type="button" @click="copyAccount" :aria-label="t('polestar.copy')" data-testid="polestar-copy"
                      class="p-1 rounded-sm hover:bg-gray-100 dark:hover:bg-gray-700">
                <CheckIcon v-if="copied" class="h-4 w-4 text-emerald-600" aria-hidden="true" />
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
        <h3 class="text-sm font-bold text-gray-900 dark:text-gray-100 mb-2">
          <span class="inline-flex items-center justify-center w-6 h-6 rounded-full bg-gray-900 dark:bg-gray-100 text-white dark:text-gray-900 text-xs font-extrabold mr-2">2</span>{{ t('polestar.step2_title') }}
        </h3>
        <form class="space-y-3" @submit.prevent="connect">
          <div>
            <label for="polestar-vin" class="block text-xs font-bold uppercase tracking-wider text-gray-600 dark:text-gray-400 mb-1">{{ t('polestar.vin_label') }}</label>
            <input id="polestar-vin" v-model="vin" type="text" inputmode="text" autocomplete="off" autocapitalize="characters"
                   maxlength="17" spellcheck="false" :placeholder="t('polestar.vin_placeholder')" data-testid="polestar-vin"
                   :aria-invalid="vinTouched && !vinValid ? 'true' : undefined" @blur="vinTouched = true"
                   class="w-full rounded-sm border-2 bg-white dark:bg-gray-800 px-3 py-2.5 font-mono text-sm uppercase tracking-wider text-gray-900 dark:text-gray-100"
                   :class="vinTouched && !vinValid ? 'border-red-500' : 'border-gray-300 dark:border-gray-600'" />
            <p v-if="vinTouched && !vinValid" class="mt-1 text-xs text-red-700 dark:text-red-400" data-testid="polestar-vin-error">{{ t('polestar.vin_invalid_hint') }}</p>
            <p v-else class="mt-1 text-xs text-gray-500 dark:text-gray-400">{{ t('polestar.vin_hint') }}</p>
          </div>

          <button type="submit" :disabled="submitting" data-testid="polestar-connect"
                  class="w-full sm:w-auto bg-amber-500 hover:bg-amber-400 disabled:opacity-60 text-gray-950 font-bold uppercase tracking-wider text-xs md:text-sm px-5 py-3.5 rounded-sm border-2 border-amber-500 shadow-[2px_2px_0_0_#030712] active:translate-x-[4px] active:translate-y-[4px]">
            {{ submitting ? t('polestar.checking') : t('polestar.check_connection') }}
          </button>
        </form>

        <!-- Ergebnis: noch nicht freigegeben -->
        <div v-if="outcome === 'NOT_SHARED'" data-testid="polestar-not-shared"
             class="mt-3 border-l-2 border-amber-500 bg-amber-50 dark:bg-amber-950/40 px-4 py-3 rounded-r-sm flex gap-3">
          <ExclamationTriangleIcon class="h-5 w-5 text-amber-600 dark:text-amber-400 shrink-0" aria-hidden="true" />
          <div class="text-sm text-gray-800 dark:text-gray-200 leading-relaxed">
            <p class="font-bold">{{ t('polestar.not_shared_title') }}</p>
            <p class="text-xs mt-0.5">{{ t('polestar.not_shared_desc') }}</p>
          </div>
        </div>

        <!-- Ergebnis: Warteliste -->
        <div v-else-if="outcome === 'WAITLIST'" data-testid="polestar-waitlist"
             class="mt-3 border-l-2 border-gray-400 bg-gray-50 dark:bg-gray-800 px-4 py-3 rounded-r-sm flex gap-3">
          <ClockIcon class="h-5 w-5 text-gray-500 shrink-0" aria-hidden="true" />
          <div class="text-sm text-gray-800 dark:text-gray-200 leading-relaxed">
            <p class="font-bold">{{ t('polestar.waitlist_title') }}</p>
            <p class="text-xs mt-0.5">{{ t('polestar.waitlist_desc') }}</p>
          </div>
        </div>

        <p v-if="error" class="mt-3 text-sm text-red-700 dark:text-red-400" role="alert" data-testid="polestar-error">{{ error }}</p>
      </section>
    </template>

    <!-- Testmodus-Satz (DSGVO): immer sichtbar -->
    <div class="border-l-2 border-blue-500 bg-blue-50 dark:bg-blue-950/30 px-4 py-3 rounded-r-sm flex gap-3" data-testid="polestar-testmode">
      <InformationCircleIcon class="h-5 w-5 text-blue-600 dark:text-blue-400 shrink-0" aria-hidden="true" />
      <p class="text-xs text-gray-700 dark:text-gray-200 leading-relaxed">{{ t('polestar.testmode') }}</p>
    </div>
  </div>
</template>
