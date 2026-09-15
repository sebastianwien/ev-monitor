<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { TruckIcon, BoltIcon } from '@heroicons/vue/24/outline'
import api from '../../api/axios'
import type { LogFormData } from '../log-form/logFormData'
import type { ChargingProvider } from '../../composables/useChargingProviders'
import CarSelector from '../car/CarSelector.vue'
import { useCarStore } from '../../stores/car'
import { useCountryStore } from '../../stores/country'
import { useCoinStore } from '../../stores/coins'
import { useLogsRefreshStore } from '../../stores/logsRefresh'
import { useHaptic } from '../../composables/useHaptic'
import { useCpoOptions } from '../../composables/useCpoOptions'
import { useNearbyStations } from '../../composables/useNearbyStations'
import { useRecentSites } from '../../composables/useRecentSites'
import { useCostInput } from '../../composables/useCostInput'
import { queryLocationPermission, getCurrentPosition, LOCATION_ENABLED_KEY, type LocationPermission } from '../../composables/useLocationPermission'
import { EUR_ZONE_COUNTRIES } from '../../config/unitSystems'
import { EUR_EXCHANGE_RATES } from '../../config/exchangeRates'
import { analytics } from '../../services/analytics'
import { applyTariffToLocationIfRequested } from '../../utils/applyTariffToLocation'
import { emptyLogForm, canProceed, applyPlace, buildLogPayload, LAST_STEP, type WizardStep, type WizardState, type PlaceChoice } from './wizardLogic'
import WizardShell from './WizardShell.vue'
import StepPlace from './StepPlace.vue'
import StepEnergy from './StepEnergy.vue'
import StepVehicle from './StepVehicle.vue'
import StepCost from './StepCost.vue'
import StepReview from './StepReview.vue'

const emit = defineEmits<{ success: []; cancel: [] }>()
const { t } = useI18n()
const { haptic } = useHaptic()
const carStore = useCarStore()
const countryStore = useCountryStore()
const coinStore = useCoinStore()
const logsRefreshStore = useLogsRefreshStore()

// ── Auto ──────────────────────────────────────────────────────────────────────
const cars = ref<any[]>([])
const hasCars = ref<boolean | null>(null)
const selectedCarId = ref<string | null>(null)
const selectedCar = computed(() => cars.value.find(c => c.id === selectedCarId.value) ?? null)

// ── Formular ──────────────────────────────────────────────────────────────────
const form = ref<LogFormData>(emptyLogForm())
const state = ref<WizardState>({ place: null })
const step = ref<WizardStep>(1)
const ocrUsed = ref(false)
const saving = ref(false)
const error = ref<string | null>(null)

const cost = useCostInput(form, {
  isEurCountry: computed(() => EUR_ZONE_COUNTRIES.includes(countryStore.country)),
  exchangeRate: computed(() => EUR_EXCHANGE_RATES[countryStore.unitSystem.currency]),
  localCurrency: computed(() => countryStore.unitSystem.currency),
})

// ── Letzte Logs: Tacho-Referenz, Reifen/Strecke, zuletzt genutzte Anbieter ────
const logs = ref<any[]>([])
const lastOdometerKm = computed<number | null>(() => {
  const withOdo = logs.value.filter(l => l.odometerKm != null)
  return withOdo.length ? withOdo[0].odometerKm : null
})
const recentCpos = computed<string[]>(() =>
  [...new Set(logs.value.map(l => l.cpoName).filter((c): c is string => !!c))].slice(0, 3))

const fetchLogs = async () => {
  if (!selectedCarId.value) { logs.value = []; return }
  try {
    const res = await api.get(`/logs?carId=${selectedCarId.value}&limit=10`)
    logs.value = res.data.sort((a: any, b: any) => new Date(b.loggedAt).getTime() - new Date(a.loggedAt).getTime())
    const lastTire = logs.value.find(l => l.tireType); if (lastTire) form.value.tireType = lastTire.tireType
    const lastRoute = logs.value.find(l => l.routeType); if (lastRoute) form.value.routeType = lastRoute.routeType
  } catch { logs.value = [] }
}
watch(selectedCarId, fetchLogs)

// ── Ort: Standort, Registerstandorte, Anbieterliste ───────────────────────────
const permission = ref<LocationPermission>('unknown')
const locationStatus = ref<'idle' | 'loading' | 'success' | 'error'>('idle')
const nearby = useNearbyStations()
const recentSites = useRecentSites()
const cpo = useCpoOptions(computed(() => countryStore.country))
const providers = ref<ChargingProvider[]>([])

const requestLocation = async () => {
  locationStatus.value = 'loading'
  try {
    const pos = await getCurrentPosition()
    form.value.latitude = pos.latitude
    form.value.longitude = pos.longitude
    permission.value = 'granted'
    localStorage.setItem(LOCATION_ENABLED_KEY, 'true')
    // Erst nach den Säulen auf 'success': so klappt die Standort-Card in einem Zug zu,
    // während die Liste erscheint, statt in zwei Sprüngen
    await nearby.load(pos.latitude, pos.longitude)
    locationStatus.value = 'success'
  } catch (e: any) {
    locationStatus.value = 'error'
    if (e?.denied) permission.value = 'denied'
  }
}

const onPlacePicked = async (p: { latitude: number; longitude: number }) => {
  form.value.latitude = p.latitude
  form.value.longitude = p.longitude
  locationStatus.value = 'success'
  await nearby.load(p.latitude, p.longitude)
}

/** Kurze Pause vor dem Weiterspringen: die gewaehlte Kachel soll als ausgewaehlt sichtbar werden. */
const PLACE_ADVANCE_MS = 350
let advanceTimer: number | undefined
const choosePlace = (choice: PlaceChoice) => {
  state.value.place = choice.kind
  applyPlace(form.value, choice)
  window.clearTimeout(advanceTimer)
  if (choice.kind !== 'other') advanceTimer = window.setTimeout(next, PLACE_ADVANCE_MS)
}
onUnmounted(() => window.clearTimeout(advanceTimer))

const placeLabel = computed(() => {
  if (state.value.place === 'home') return t('logwizard.place_home')
  return form.value.cpoName ?? t('logwizard.place_other')
})

// ── Navigation ────────────────────────────────────────────────────────────────
const proceedAllowed = computed(() => canProceed(step.value, form.value, state.value))
const questions: Record<WizardStep, string> = {
  1: 'logwizard.q_place', 2: 'logwizard.q_energy', 3: 'logwizard.q_vehicle', 4: 'logwizard.q_cost', 5: 'logwizard.q_review',
}
const hint = computed(() => {
  if (step.value === 1 && permission.value === 'granted' && nearby.stations.value.length) return t('logwizard.hint_nearby')
  if (step.value === 2) return placeLabel.value
  if (step.value === 4) return `${placeLabel.value} · ${form.value.chargingType}`
  if (step.value === 3) return t('logwizard.hint_vehicle')
  return ''
})
const goto = (s: WizardStep) => { error.value = null; step.value = s; window.scrollTo({ top: 0 }) }
const back = () => { if (step.value > 1) goto((step.value - 1) as WizardStep) }
const next = () => { if (step.value < LAST_STEP) goto((step.value + 1) as WizardStep); else submit() }

// ── OCR ───────────────────────────────────────────────────────────────────────
const onOcr = (r: any) => {
  if (r.kwh != null) { form.value.kwhCharged = r.kwh; form.value.kwhAtVehicle = null }
  if (r.cost != null) { cost.costMode.value = 'total'; cost.costLocalTotal.value = r.cost }
  if (r.durationMinutes != null) form.value.chargeDurationMinutes = r.durationMinutes
  if (r.maxChargingPowerKw != null) form.value.maxChargingPowerKw = r.maxChargingPowerKw
  ocrUsed.value = true
}

// ── Speichern ─────────────────────────────────────────────────────────────────
const toast = ref<string | null>(null)
const submit = async () => {
  if (!selectedCarId.value || saving.value) return
  haptic(20)
  saving.value = true
  error.value = null
  try {
    const isFirstLog = logs.value.length === 0
    const res = await api.post('/logs', buildLogPayload(form.value, selectedCarId.value, ocrUsed.value))
    toast.value = t('logform.coin_toast', { n: res.data.coinsAwarded })
    setTimeout(() => { toast.value = null }, 4000)
    coinStore.refresh()
    analytics.trackLogCreated(ocrUsed.value ? 'ocr' : 'manual', isFirstLog)
    await applyTariffToLocationIfRequested(form.value)
    logsRefreshStore.notifyLogSaved()
    emit('success')
  } catch (err: any) {
    error.value = err.response?.data?.message || t('logform.error_save')
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  try {
    cars.value = await carStore.getCars()
    hasCars.value = cars.value.length > 0
    if (cars.value.length === 1) selectedCarId.value = cars.value[0].id
  } catch { hasCars.value = false }
  cpo.loadAll()
  recentSites.load()
  api.get<ChargingProvider[]>('/users/me/charging-providers').then(r => { providers.value = r.data }).catch(() => {})
  permission.value = await queryLocationPermission()
  // Schon einmal erlaubt: kein Dialog mehr, direkt laden. Sonst wartet der Hinweis auf den Tap.
  if (permission.value === 'granted' || (permission.value === 'unknown' && localStorage.getItem(LOCATION_ENABLED_KEY) === 'true')) {
    requestLocation()
  }
})
</script>

<template>
  <div>
    <div v-if="hasCars === false" class="text-center py-10 px-4 space-y-4">
      <TruckIcon class="h-14 w-14 mx-auto text-gray-300" />
      <p class="text-gray-600 dark:text-gray-400 font-medium">{{ t('logform.no_car_title') }}</p>
      <p class="text-sm text-gray-400 dark:text-gray-500">{{ t('logform.no_car_desc') }}</p>
      <router-link to="/cars" class="inline-flex items-center gap-2 bg-indigo-600 text-white px-5 py-2.5 rounded-sm text-sm font-medium">
        <TruckIcon class="h-4 w-4" />{{ t('logform.no_car_btn') }}
      </router-link>
    </div>

    <WizardShell v-else-if="hasCars" :step="step" :question="t(questions[step])" :hint="hint"
      :can-proceed="proceedAllowed && !!selectedCarId" :saving="saving"
      :primary-label="step === LAST_STEP ? t('common.save') : t('common.next')"
      @back="back" @next="next" @cancel="emit('cancel')">
      <div v-if="cars.length > 1 && step === 1" class="mb-4"><CarSelector v-model="selectedCarId" /></div>

      <StepPlace v-if="step === 1" :place="state.place" :selected-cpo="form.cpoName" :selected-site="form.chargingSite"
        :recent-sites="recentSites.sites.value"
        :stations="nearby.stations.value" :stations-loading="nearby.loading.value"
        :permission="permission" :location-status="locationStatus"
        :recent-cpos="recentCpos" :all-cpos="cpo.allCpos.value"
        @choose="choosePlace" @request-location="requestLocation" @place-picked="onPlacePicked" />
      <StepEnergy v-else-if="step === 2" v-model="form" @ocr="onOcr" />
      <StepVehicle v-else-if="step === 3" v-model="form" :last-odometer-km="lastOdometerKm"
        :effective-capacity-kwh="selectedCar?.effectiveBatteryCapacityKwh" />
      <StepCost v-else-if="step === 4" v-model="form" v-model:providers="providers" :cost="cost" />
      <StepReview v-else v-model="form" :place-label="placeLabel" :error="error" @goto="goto" />
    </WizardShell>

    <div v-if="toast" class="fixed bottom-6 right-6 z-50 animate-slide-in">
      <div class="bg-green-600 text-white px-5 py-3 rounded-sm shadow-[6px_6px_0_rgba(0,0,0,0.40)] flex items-center gap-2">
        <BoltIcon class="h-5 w-5" />{{ toast }}
      </div>
    </div>
  </div>
</template>
