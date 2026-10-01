<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
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
import { useChargingSuggestion } from '../../composables/useChargingSuggestion'
import { useCostInput } from '../../composables/useCostInput'
import { useDelayedCall } from '../../composables/useDelayedCall'
import { queryLocationPermission, getCurrentPosition, LOCATION_ENABLED_KEY, type LocationPermission } from '../../composables/useLocationPermission'
import { EUR_ZONE_COUNTRIES } from '../../config/unitSystems'
import { EUR_EXCHANGE_RATES } from '../../config/exchangeRates'
import { analytics } from '../../services/analytics'
import { applyTariffToLocationIfRequested } from '../../utils/applyTariffToLocation'
import { emptyLogForm, canProceed, applyPlace, applySuggestion, buildLogPayload, LAST_STEP, type WizardStep, type WizardState, type PlaceChoice } from './wizardLogic'
import WizardShell from './WizardShell.vue'
import StepPlace from './StepPlace.vue'
import StepNumbers, { type NumbersContext } from './StepNumbers.vue'
import type { PreviousLogRef } from '../../utils/consumptionPreview'
import geohashLib from 'ngeohash'
import type { CardChoice, CommunityPrice } from './CardStrip.vue'
import { providerPriceForType } from '../../utils/chargingProviderPricing'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import StepReview from './StepReview.vue'

const emit = defineEmits<{ success: []; cancel: [] }>()
const { t } = useI18n()
const { haptic } = useHaptic()
const { formatNumber } = useLocaleFormat()
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
const suggestion = useChargingSuggestion()
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
    // während die Liste erscheint, statt in zwei Sprüngen. Der Treffer läuft parallel.
    await Promise.all([nearby.load(pos.latitude, pos.longitude), suggestion.load(pos.latitude, pos.longitude)])
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
  await Promise.all([nearby.load(p.latitude, p.longitude), suggestion.load(p.latitude, p.longitude)])
}

/** Die Trefferkarte: Ort und Ladekarte wie beim letzten Mal, dann direkt weiter. */
const acceptSuggestion = () => {
  if (!suggestion.suggestion.value) return
  const choice = applySuggestion(form.value, suggestion.suggestion.value)
  state.value.place = choice.kind
  cost.reset(); cardKey.value = null; openCard.value = null
  const id = form.value.chargingProviderId
  const p = id ? providers.value.find(x => x.id === id) : null
  if (p) applyCard({ kind: 'provider', provider: p, eurPerKwh: providerPriceForType(p, form.value.chargingType) })
  haptic(10)
  advance.schedule()
}
const suggestionProviderLabel = computed(() => {
  const id = suggestion.suggestion.value?.lastProviderId
  const p = id ? providers.value.find(x => x.id === id) : null
  return p ? (p.label || p.providerName) : null
})

/** Kurze Pause vor dem Weiterspringen: die gewaehlte Kachel soll als ausgewaehlt sichtbar werden. */
const PLACE_ADVANCE_MS = 350
const advance = useDelayedCall(() => next(), PLACE_ADVANCE_MS)
/**
 * Nur Zuhause springt von selbst weiter. An einer Säule klappt der Ladekarten-Streifen auf:
 * der Tipp auf eine Karte springt weiter, die vorgewählte bestätigt der Nutzer mit "Weiter".
 */
const autoAdvances = (choice: PlaceChoice) => choice.kind === 'home'
const choosePlace = (choice: PlaceChoice) => {
  state.value.place = choice.kind
  siteCoords.value = choice.kind === 'station' && choice.station.latitude != null && choice.station.longitude != null
    ? { lat: choice.station.latitude, lon: choice.station.longitude } : null
  applyPlace(form.value, choice)
  resetCard()
  if (autoAdvances(choice)) advance.schedule(); else { advance.cancel(); preselectCard() }
}

// ── Ladekarte (Schritt 1, unter der gewählten Säule) ──────────────────────────
/** Provider-ID, 'community' oder 'free' - was im Streifen markiert ist */
const cardKey = ref<string | null>(null)
const community = ref<CommunityPrice | null>(null)
/** "+ neue Karte" oder Karte ohne Tarif: der Editor öffnet in Schritt 2 */
const openCard = ref<'new' | 'price' | null>(null)
const resetCard = () => { cardKey.value = null; openCard.value = null; form.value.chargingProviderId = null; cost.reset() }
const priceLabel = (eur: number) => `${formatNumber(Math.round(cost.eurToLocal(eur) * 100) / 100)} ${countryStore.unitSystem.currencySymbol}/kWh`
const cardStrip = computed(() => form.value.isPublicCharging
  ? { providers: providers.value, chargingType: form.value.chargingType, community: community.value, selected: cardKey.value, priceLabel }
  : null)

const applyCard = (c: CardChoice) => {
  openCard.value = null
  switch (c.kind) {
    case 'provider':
      form.value.chargingProviderId = c.provider.id; cardKey.value = c.provider.id
      if (c.eurPerKwh != null) cost.setPerKwhEur(c.eurPerKwh); else { cost.reset(); openCard.value = 'price' }
      break
    case 'community':
      form.value.chargingProviderId = c.price.providerId; cardKey.value = 'community'; cost.setPerKwhEur(c.price.eurPerKwh)
      break
    case 'free':
      form.value.chargingProviderId = null; cardKey.value = 'free'; cost.reset(); cost.costLocalTotal.value = 0
      break
    case 'new':
      form.value.chargingProviderId = null; cardKey.value = null; cost.reset(); openCard.value = 'new'
      break
  }
}
const chooseCard = (c: CardChoice) => { applyCard(c); haptic(10); advance.schedule() }

/** Die Karte, deren Name zum Betreiber passt - nur bei genau einem Treffer. */
const cardMatchingCpo = () => {
  const cpo = form.value.cpoName?.trim().toLowerCase()
  if (!cpo) return null
  const hits = providers.value.filter(p => !p.isPrivate && providerPriceForType(p, form.value.chargingType) != null)
    .filter(p => { const n = (p.label || p.providerName).toLowerCase(); return n.includes(cpo) || cpo.includes(n) })
  return hits.length === 1 ? hits[0] : null
}
/**
 * Vorauswahl ohne Weiterspringen: die Karte vom letzten Mal an diesem Ort (ohne Karte der
 * letzte Preis als "Zuletzt hier"), sonst die Karte, deren Name zum Betreiber passt. Der Preisvorschlag kommt vom Backend und braucht die
 * Position; kommt er nach einer Nutzerwahl an, bleibt die Nutzerwahl.
 */
let communitySeq = 0
const preselectCard = async () => {
  if (!form.value.isPublicCharging) return
  const mine = ++communitySeq
  community.value = null
  if (form.value.latitude != null && form.value.longitude != null) {
    try {
      const res = await api.get('/logs/price-suggestion', {
        params: { lat: form.value.latitude, lon: form.value.longitude, isPublic: true, chargingType: form.value.chargingType },
      })
      if (mine !== communitySeq) return
      if (res.data?.costPerKwh != null) community.value = { eurPerKwh: Number(res.data.costPerKwh), providerId: res.data.chargingProviderId ?? null }
    } catch { /* kein Vorschlag - kein Problem */ }
  }
  if (cardKey.value != null) return
  if (community.value) {
    const last = providers.value.find(p => p.id === community.value!.providerId && !p.isPrivate)
    if (last) applyCard({ kind: 'provider', provider: last, eurPerKwh: providerPriceForType(last, form.value.chargingType) ?? community.value.eurPerKwh })
    else applyCard({ kind: 'community', price: community.value })
    return
  }
  const match = cardMatchingCpo()
  if (match) applyCard({ kind: 'provider', provider: match, eurPerKwh: providerPriceForType(match, form.value.chargingType) })
}

const placeLabel = computed(() => {
  if (state.value.place === 'home') return t('logwizard.place_home')
  return form.value.cpoName ?? t('logwizard.place_other')
})

// ── Navigation ────────────────────────────────────────────────────────────────
const proceedAllowed = computed(() => canProceed(step.value, form.value, state.value))
const questions: Record<WizardStep, string> = { 1: 'logwizard.q_place', 2: 'logwizard.q_numbers', 3: 'logwizard.q_review' }
const hint = computed(() => {
  return ''
})
/** Kopf von Schritt 2: gewählter Ort mit Adresse (aus der Umkreisliste) und Karte, Position für die Minimap. */
const numbersContext = computed<NumbersContext>(() => {
  const card = providers.value.find(x => x.id === form.value.chargingProviderId)
  const station = form.value.chargingSite ? nearby.stations.value.find(s => s.name === form.value.chargingSite!.name) : null
  return {
    title: form.value.chargingSite?.name ?? placeLabel.value, address: station?.address ?? null,
    card: card ? (card.label || card.providerName) : null,
    ...siteCenter(),
  }
})
/** Exakte Position der gewählten Säule aus dem Register - nur für die Minimap, geht nie ins Log. */
const siteCoords = ref<{ lat: number; lon: number } | null>(null)
/**
 * Minimap-Mittelpunkt als Bestätigung "das ist die Säule": exakt aus dem Register, sonst die Zelle
 * des gespeicherten Standorts (~150 m). Nicht die Handy-Position, die liegt gern daneben. Ohne Säule
 * (freier Anbieter) die eigene Position, zuhause gar keine Karte.
 */
const siteCenter = (): { lat: number | null; lon: number | null } => {
  if (state.value.place === 'home') return { lat: null, lon: null }
  if (siteCoords.value) return siteCoords.value
  const cell = form.value.chargingSite?.geohash
  if (cell) { const c = geohashLib.decode(cell); return { lat: c.latitude, lon: c.longitude } }
  return { lat: form.value.latitude, lon: form.value.longitude }
}
/** Letzter Log mit Tacho - Referenz für den Richtwert kWh und Euro je 100 km. */
const previousLog = computed<PreviousLogRef | null>(() => {
  const l = logs.value.find(x => x.odometerKm != null)
  return l ? { odometerKm: l.odometerKm, socAfter: l.socAfterChargePercent ?? null } : null
})
const goto = (s: WizardStep) => { error.value = null; step.value = s; window.scrollTo({ top: 0 }) } // Desktop: Seite; mobil setzt WizardShell ihren Scroller zurueck
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
        :suggestion="suggestion.suggestion.value" :suggestion-provider-label="suggestionProviderLabel"
        :radius-meters="nearby.radius.value" :can-expand="nearby.canExpand.value"
        :card-strip="cardStrip" @choose-card="chooseCard"
        @choose="choosePlace" @request-location="requestLocation" @place-picked="onPlacePicked"
        @accept-suggestion="acceptSuggestion" @expand-radius="nearby.expand()" />
      <StepNumbers v-else-if="step === 2" v-model="form" v-model:providers="providers" :cost="cost"
        :last-odometer-km="lastOdometerKm" :effective-capacity-kwh="selectedCar?.effectiveBatteryCapacityKwh" :open-card="openCard" :context="numbersContext" :previous-log="previousLog" @ocr="onOcr" />
      <StepReview v-else v-model="form" :place-label="placeLabel" :error="error" @goto="goto" />
    </WizardShell>

    <div v-if="toast" class="fixed bottom-6 right-6 z-50 animate-slide-in">
      <div class="bg-green-600 text-white px-5 py-3 rounded-sm shadow-[6px_6px_0_rgba(0,0,0,0.40)] flex items-center gap-2">
        <BoltIcon class="h-5 w-5" />{{ toast }}
      </div>
    </div>
  </div>
</template>
