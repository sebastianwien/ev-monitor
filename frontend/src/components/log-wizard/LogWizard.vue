<script setup lang="ts">
import { computed, defineAsyncComponent, nextTick, onMounted, ref, watch } from 'vue'
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
import { useAuthStore } from '../../stores/auth'
import { isVoiceSupported } from '../../composables/useVoiceRecorder'
import { useHaptic } from '../../composables/useHaptic'
import { useCpoOptions } from '../../composables/useCpoOptions'
import { useNearbyStations, type StationMatch } from '../../composables/useNearbyStations'
import { useCostInput } from '../../composables/useCostInput'
import { useDelayedCall } from '../../composables/useDelayedCall'
import { queryLocationPermission, getCurrentPosition, LOCATION_ENABLED_KEY, type LocationPermission } from '../../composables/useLocationPermission'
import { EUR_ZONE_COUNTRIES } from '../../config/unitSystems'
import { EUR_EXCHANGE_RATES } from '../../config/exchangeRates'
import { analytics } from '../../services/analytics'
import { applyTariffToLocationIfRequested } from '../../utils/applyTariffToLocation'
import { emptyLogForm, canProceed, missingRequired, applyPlace, buildLogPayload, LAST_STEP, type WizardStep, type WizardState, type PlaceChoice,
  applyVoiceDraft, voiceCost, voiceFlags, canJumpToReview, type VoiceDraft, type VoiceCost, type VoiceFlag, type VoiceUsage } from './wizardLogic'
import WizardShell from './WizardShell.vue'
import StepPlace from './StepPlace.vue'
import StepNumbers, { type NumbersContext } from './StepNumbers.vue'
import { consumptionPreview, type PreviousLogRef } from '../../utils/consumptionPreview'
import { costMetrics } from './costMetrics'
import geohashLib from 'ngeohash'
import type { CardChoice, CommunityPrice } from './CardStrip.vue'
import { providerPriceForType } from '../../utils/chargingProviderPricing'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import StepReview from './StepReview.vue'
import { prefetchMinimapTiles, MINIMAP_MAX_PX } from './minimapTiles'
import { nextField } from './keyboardNav'
import { useIsMobile } from '../../composables/useIsMobile'
const VoiceCapture = defineAsyncComponent(() => import('./VoiceCapture.vue'))

const emit = defineEmits<{ success: []; cancel: [] }>()
const { t } = useI18n()
const { haptic } = useHaptic()
const { formatNumber, formatDecimal } = useLocaleFormat()
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
/** Ungenauigkeit der letzten Ortung in Metern; null bei einer gesuchten Adresse */
const locationAccuracy = ref<number | null>(null)
const nearby = useNearbyStations()
const cpo = useCpoOptions(computed(() => countryStore.country))
const providers = ref<ChargingProvider[]>([])

const requestLocation = async () => {
  locationStatus.value = 'loading'
  try {
    const pos = await getCurrentPosition()
    form.value.latitude = pos.latitude
    form.value.longitude = pos.longitude
    locationAccuracy.value = pos.accuracy ?? null
    permission.value = 'granted'
    localStorage.setItem(LOCATION_ENABLED_KEY, 'true')
    // Erst nach den Säulen auf 'success': so klappt die Standort-Card in einem Zug zu, während die Liste erscheint
    pickedAddress.value = null
    await nearby.load(pos.latitude, pos.longitude)
    locationStatus.value = 'success'
  } catch (e: any) {
    locationStatus.value = 'error'
    if (e?.denied) permission.value = 'denied'
  }
}

/** Gewählte Adresse: steht als Untertitel in "Hier privat geladen" */
const pickedAddress = ref<string | null>(null)
const onPlacePicked = async (p: { latitude: number; longitude: number; name: string }) => {
  pickedAddress.value = p.name
  form.value.latitude = p.latitude
  form.value.longitude = p.longitude
  locationAccuracy.value = null
  locationStatus.value = 'success'
  await nearby.load(p.latitude, p.longitude)
  // Keine Säule an der Adresse: privat ist vorgewählt, ohne Weiterspringen - der Nutzer sieht die Wahl und tippt "Weiter"
  if (state.value.place === null && nearby.stations.value.length === 0) choosePlace({ kind: 'home' }, { advance: false })
}

/** Kurze Pause vor dem Weiterspringen: die gewaehlte Kachel soll als ausgewaehlt sichtbar werden. */
const PLACE_ADVANCE_MS = 350
const advance = useDelayedCall(() => next(), PLACE_ADVANCE_MS)
/**
 * Privat springt von selbst weiter. An einer Säule klappt der Ladekarten-Streifen auf: der Tipp auf eine
 * Karte springt weiter, die vorgewählte bestätigt der Nutzer mit "Weiter".
 */
const autoAdvances = (choice: PlaceChoice) => choice.kind === 'home'
/** Ortswahl im Wizard-Zustand: Art, dazu Position und Adresse der Säule für Minimap und Kopfzeile. */
const setPlaceContext = (choice: PlaceChoice) => {
  state.value.place = choice.kind
  siteCoords.value = choice.kind === 'station' && choice.station.latitude != null && choice.station.longitude != null
    ? { lat: choice.station.latitude, lon: choice.station.longitude } : null
  siteAddress.value = choice.kind === 'station' ? choice.station.address ?? null : null
}
const choosePlace = (choice: PlaceChoice, opts: { advance?: boolean } = {}) => {
  setPlaceContext(choice)
  applyPlace(form.value, choice)
  resetCard()
  if (autoAdvances(choice) && opts.advance !== false) advance.schedule(); else { advance.cancel(); preselectCard() }
}

// ── Ladekarte (Schritt 1, unter der gewählten Säule) ──────────────────────────
/** Provider-ID, 'community' oder 'free' - was im Streifen markiert ist */
const cardKey = ref<string | null>(null)
const community = ref<CommunityPrice | null>(null)
/** "+ neue Karte" oder Karte ohne Tarif: der Editor öffnet in Schritt 2 */
const openCard = ref<'new' | 'price' | null>(null)
const resetCard = () => { cardKey.value = null; openCard.value = null; form.value.chargingProviderId = null; cost.reset(); applySpokenCost() }
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
  // Gesagter Betrag geht vor den Kartenpreis; ohne Tarif muss dann auch kein Preis nachgetragen werden
  if (applySpokenCost() && openCard.value === 'price') openCard.value = null
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
  const mine = ++communitySeq
  community.value = null
  if (!form.value.isPublicCharging) return
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
  if (state.value.place === 'home') return t('logwizard.place_private')
  return form.value.cpoName ?? t('logwizard.place_public')
})

// ── Navigation ────────────────────────────────────────────────────────────────
const proceedAllowed = computed(() => canProceed(step.value, form.value, state.value))
const questions: Record<WizardStep, string> = { 1: 'logwizard.q_place', 2: 'logwizard.q_numbers', 3: 'logwizard.q_review' }
/** Hinweis nach "Weiter" mit Lücke: was noch fehlt. Verschwindet, sobald der Schritt wechselt oder vollständig ist. */
const blockedHint = ref('')
const hint = computed(() => blockedHint.value)
watch([step, proceedAllowed], () => { blockedHint.value = '' })
/** Kopf von Schritt 2: gewählter Ort mit Adresse (aus der Umkreisliste) und Karte, Position für die Minimap. */
const numbersContext = computed<NumbersContext>(() => {
  const card = providers.value.find(x => x.id === form.value.chargingProviderId)
  const station = form.value.chargingSite ? nearby.stations.value.find(s => s.name === form.value.chargingSite!.name) : null
  return {
    title: form.value.chargingSite?.name ?? placeLabel.value, address: siteAddress.value ?? station?.address ?? null,
    card: card ? (card.label || card.providerName) : null,
    ...siteCenter(),
  }
})
/** Exakte Position der gewählten Säule aus dem Register - nur für die Minimap, geht nie ins Log. */
const siteCoords = ref<{ lat: number; lon: number } | null>(null)
/** Aus der Textsuche gewählte Säule - überlebt den Wechsel zwischen den Schritten */
const searchedStation = ref<StationMatch | null>(null)
/** Adresse der gewählten Säule (auch aus der Textsuche, die nicht in der Umkreisliste steht) */
const siteAddress = ref<string | null>(null)
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
/** Richtwert kWh und Euro je 100 km seit der letzten Ladung - Schritt 2 zeigt ihn in der Kostenbox, Schritt 3 in der Kachel */
const preview = computed(() => consumptionPreview({
  kwhCharged: form.value.kwhCharged, kwhAtVehicle: form.value.kwhAtVehicle, chargingType: form.value.chargingType,
  odometerKm: form.value.odometerKm, socAfter: form.value.socAfterChargePercent, capacityKwh: selectedCar.value?.effectiveBatteryCapacityKwh,
  costEur: form.value.costEur, previous: previousLog.value,
}))
const summaryMetrics = computed(() => {
  const kwh = form.value.kwhCharged ?? form.value.kwhAtVehicle
  const perKwh = form.value.costEur != null && kwh ? cost.eurToLocal(form.value.costEur / kwh) : null
  return costMetrics(perKwh, preview.value, {
    isEurCountry: EUR_ZONE_COUNTRIES.includes(countryStore.country), subunit: countryStore.unitSystem.currencySubunit || countryStore.unitSystem.currencySymbol,
    symbol: countryStore.unitSystem.currencySymbol, eurToLocal: cost.eurToLocal, formatNumber, formatDecimal,
  })
})
/** Richtung des letzten Schrittwechsels: vorwärts schiebt von rechts rein, zurück von links */
const dir = ref<'forward' | 'back'>('forward')
const goto = (s: WizardStep) => { error.value = null; dir.value = s > step.value ? 'forward' : 'back'; step.value = s; window.scrollTo({ top: 0 }) } // Desktop: Seite; mobil setzt WizardShell ihren Scroller zurueck
const back = () => { if (step.value > 1) goto((step.value - 1) as WizardStep) }
const next = () => { if (step.value < LAST_STEP) goto((step.value + 1) as WizardStep); else submit() }

// ── Tastatur ──────────────────────────────────────────────────────────────────
/** Feld-IDs der Pflichtangaben in Schritt 2 - ihr aria-label ist zugleich der Name im Hinweis. */
const REQUIRED_FIELD_IDS = { energy: 'wizard-kwh', odometer: 'wizard-odometer', soc: 'wizard-soc', cost: 'wizard-cost' } as const
/** "Weiter" trotz Lücke: Hinweis nennt, was fehlt, der Fokus springt zum ersten offenen Feld (bzw. zur ersten Kachel). */
const onBlocked = () => {
  if (step.value === 1) {
    blockedHint.value = t('logwizard.missing_place')
    stepEl.value?.querySelector<HTMLElement>('button')?.focus()
    return
  }
  const fields = missingRequired(form.value).map(f => document.getElementById(REQUIRED_FIELD_IDS[f]))
  const names = fields.map(el => el?.getAttribute('aria-label')).filter(Boolean)
  if (names.length) blockedHint.value = t('logwizard.missing_fields', { fields: names.join(', ') })
  fields.find(Boolean)?.focus()
}
/** Enter in einem Zahlenfeld: ins nächste Feld, im letzten Feld wie "Weiter". */
const onEnter = (e: KeyboardEvent) => {
  const target = e.target as HTMLElement
  if (!(target instanceof HTMLInputElement) || target.type !== 'number' || !stepEl.value) return
  e.preventDefault()
  const following = nextField(stepEl.value, target)
  // Markieren wie beim Tab: ein vorbelegter Wert (z. B. geschätzter SoC) wird überschrieben, nicht ergänzt
  if (following) { following.focus(); following.select() }
  else if (proceedAllowed.value) next()
  else onBlocked()
}
/**
 * Nach jedem Schrittwechsel steht der Fokus im neuen Schritt, nicht auf dem Seitenkörper. Mit Maus und
 * Tastatur direkt auf dem ersten Bedienelement (Schritt 3: Speichern); auf Touch nur auf dem Schritt
 * selbst, sonst springt die Bildschirmtastatur auf.
 */
const finePointer = useIsMobile('(hover: hover) and (pointer: fine)')
const stepEl = ref<HTMLElement | null>(null)
const focusStep = (el: HTMLElement) => {
  if (!finePointer.value) { el.focus({ preventScroll: true }); return }
  const target = step.value === LAST_STEP
    ? document.querySelector<HTMLElement>('[data-testid="wizard-next"]')
    : step.value === 2 ? el.querySelector<HTMLElement>('#wizard-kwh')
    // Schritt 1: die Privat-Zeile - nicht der erste Button, das ist oft "Standort freigeben", der nach der Ortung verschwindet
    : el.querySelector<HTMLElement>('[data-testid="place-here"]')
  ;(target ?? el).focus({ preventScroll: true })
  if (target instanceof HTMLInputElement) target.select()
}
const onStepEl = (el: unknown) => {
  const node = el instanceof HTMLElement ? el : null
  if (node && node !== stepEl.value) { stepEl.value = node; requestAnimationFrame(() => focusStep(node)) }
  else if (!node) stepEl.value = null
}

// ── OCR ───────────────────────────────────────────────────────────────────────
const onOcr = (r: any) => {
  if (r.kwh != null) { form.value.kwhCharged = r.kwh; form.value.kwhAtVehicle = null }
  if (r.cost != null) { cost.costMode.value = 'total'; cost.costLocalTotal.value = r.cost }
  if (r.durationMinutes != null) form.value.chargeDurationMinutes = r.durationMinutes
  if (r.maxChargingPowerKw != null) form.value.maxChargingPowerKw = r.maxChargingPowerKw
  ocrUsed.value = true
}

// ── Sprachlog ─────────────────────────────────────────────────────────────────
const authStore = useAuthStore()
const voiceSupported = isVoiceSupported()
/** Testbetrieb: Mikrofon nur für Admins (das Backend antwortet sonst 404) und nur mit gewähltem Auto. */
const showVoice = computed(() => authStore.isAdmin && voiceSupported && !!selectedCarId.value)
const voice = ref<{ transcript: string; flags: VoiceFlag[]; usage: VoiceUsage } | null>(null)
const voiceUsed = ref(false)
/** Gesagte Kosten bleiben stehen, auch wenn Ort oder Karte erst danach gewählt werden. */
const spokenCost = ref<VoiceCost | null>(null)
function applySpokenCost(): boolean {
  const c = spokenCost.value
  if (!c) return false
  if (c.mode === 'per_kwh') { cost.setPerKwhEur(c.eur); return true }
  cost.costMode.value = 'total'; cost.costLocalPerKwh.value = null
  cost.costLocalTotal.value = Math.round(cost.eurToLocal(c.eur) * 100) / 100
  return true
}
/**
 * Aufnahme ins Formular: Ort (sonst der Treffer aus Position und eigenen Logs, zur Prüfung markiert),
 * Ladekarte, Werte, Kosten. Danach direkt auf die Prüfseite, wenn nichts fehlt, sonst zum ersten offenen Schritt.
 */
const onVoiceDraft = async (draft: VoiceDraft) => {
  advance.cancel()
  spokenCost.value = voiceCost(draft.fields)
  const flags = voiceFlags(draft.fields.uncertain)
  if (draft.place) resetCard()
  const choice = applyVoiceDraft(form.value, draft)
  if (choice) setPlaceContext(choice)
  const id = form.value.chargingProviderId
  const card = id ? providers.value.find(x => x.id === id) : null
  if (card) applyCard({ kind: 'provider', provider: card, eurPerKwh: providerPriceForType(card, form.value.chargingType) })
  else if (choice && form.value.isPublicCharging) preselectCard()
  else applySpokenCost()
  voice.value = { transcript: draft.transcript, flags, usage: draft.usage }
  voiceUsed.value = true
  haptic(10)
  await nextTick() // der Kosten-Abgleich (Watcher) schreibt costEur erst im nächsten Tick
  const target: WizardStep = canJumpToReview(form.value, state.value) ? 3 : state.value.place ? 2 : 1
  if (target !== step.value) goto(target)
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
    analytics.trackLogCreated(voiceUsed.value ? 'voice' : ocrUsed.value ? 'ocr' : 'manual', isFirstLog)
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
  api.get<ChargingProvider[]>('/users/me/charging-providers').then(r => { providers.value = r.data }).catch(() => {})
  permission.value = await queryLocationPermission()
  // Schon einmal erlaubt: kein Dialog mehr, direkt laden. Sonst wartet der Hinweis auf den Tap.
  if (permission.value === 'granted' || (permission.value === 'unknown' && localStorage.getItem(LOCATION_ENABLED_KEY) === 'true')) {
    requestLocation()
  }
})
// Kacheln der Prüfseiten-Karte schon in Schritt 1 in den Cache holen: die Karte steht dann beim Betreten, statt kachelweise reinzuploppen
watch(() => [numbersContext.value.lat, numbersContext.value.lon] as const, ([lat, lon]) => {
  if (lat != null && lon != null) prefetchMinimapTiles(lat, lon, window.innerWidth, Math.min(MINIMAP_MAX_PX, window.innerHeight * 0.6))
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
      @back="back" @next="next" @blocked="onBlocked" @cancel="emit('cancel')">
      <div v-if="cars.length > 1 && step === 1" class="mb-4"><CarSelector v-model="selectedCarId" /></div>

      <!-- Schrittwechsel als horizontaler Slide, out-in: der alte Schritt ist weg, bevor der neue seine Karte misst.
           Gekeyter Wrapper statt Transition direkt auf den Komponenten: so hängt der Wechsel nicht an deren Wurzelelement. -->
      <Transition :name="`step-${dir}`" mode="out-in">
      <div :key="step" :ref="onStepEl" tabindex="-1" class="outline-none" @keydown.enter="onEnter">
      <VoiceCapture v-if="step === 1 && showVoice" class="mb-4" :car-id="selectedCarId!" :latitude="form.latitude" :longitude="form.longitude" @draft="onVoiceDraft" />
      <StepPlace v-if="step === 1" v-model:searched-station="searchedStation" :place="state.place" :selected-cpo="form.cpoName" :selected-site="form.chargingSite"
        :address-label="pickedAddress"
        :stations="nearby.stations.value" :stations-loading="nearby.loading.value"
        :permission="permission" :location-status="locationStatus"
        :recent-cpos="recentCpos" :all-cpos="cpo.allCpos.value"
        :radius-meters="nearby.radius.value" :can-expand="nearby.canExpand.value" :next-radius="nearby.nextRadius.value"
        :exhausted="nearby.exhausted.value" :location-accuracy="locationAccuracy" :latitude="form.latitude" :longitude="form.longitude"
        :card-strip="cardStrip" @choose-card="chooseCard"
        @choose="c => choosePlace(c)" @request-location="requestLocation" @place-picked="onPlacePicked"
        @expand-radius="nearby.expand()" />
      <StepNumbers v-else-if="step === 2" v-model="form" v-model:providers="providers" :cost="cost" :community-price="community"
        :last-odometer-km="lastOdometerKm" :effective-capacity-kwh="selectedCar?.effectiveBatteryCapacityKwh" :open-card="openCard" :context="numbersContext" :preview="preview" @ocr="onOcr" />
      <StepReview v-else v-model="form" :voice="voice" :place-label="placeLabel" :context="numbersContext" :cost-metrics="summaryMetrics" :error="error" @goto="goto" />
      </div>
      </Transition>
    </WizardShell>

    <div v-if="toast" class="fixed bottom-6 right-6 z-50 animate-slide-in">
      <div class="bg-green-600 text-white px-5 py-3 rounded-sm shadow-[6px_6px_0_rgba(0,0,0,0.40)] flex items-center gap-2">
        <BoltIcon class="h-5 w-5" />{{ toast }}
      </div>
    </div>
  </div>
</template>

<style scoped>
/* Vorwärts: neuer Schritt kommt von rechts, alter geht nach links. Zurück spiegelverkehrt. */
.step-forward-enter-active, .step-back-enter-active { transition: opacity 250ms ease-out, translate 250ms ease-out; }
.step-forward-leave-active, .step-back-leave-active { transition: opacity 200ms ease-in, translate 200ms ease-in; }
.step-forward-enter-from { opacity: 0; translate: 24px 0; }
.step-forward-leave-to { opacity: 0; translate: -24px 0; }
.step-back-enter-from { opacity: 0; translate: -24px 0; }
.step-back-leave-to { opacity: 0; translate: 24px 0; }
@media (prefers-reduced-motion: reduce) {
  .step-forward-enter-from, .step-forward-leave-to, .step-back-enter-from, .step-back-leave-to { translate: 0 0; }
}
</style>
