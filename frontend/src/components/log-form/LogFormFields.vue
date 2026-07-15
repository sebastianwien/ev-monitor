<script setup lang="ts">
import { ref, watch, computed, onMounted, onBeforeUnmount } from 'vue'
import { useI18n } from 'vue-i18n'
import { GlobeAltIcon, CalendarDaysIcon, ClockIcon, MoonIcon, CreditCardIcon } from '@heroicons/vue/24/outline'
import { VueDatePicker } from '@vuepic/vue-datepicker'
import '@vuepic/vue-datepicker/dist/main.css'
import api from '../../api/axios'
import { useInlineChargingCard, CUSTOM_PROVIDER } from '../../composables/useInlineChargingCard'
import { KNOWN_EMPS } from '../../composables/useChargingProviders'
import { cardContainerStyle } from '../../composables/useChargingCardDesign'
import ChargingCardTile from '../shared/ChargingCardTile.vue'
import { useCountryStore } from '../../stores/country'
import { EUR_EXCHANGE_RATES } from '../../config/exchangeRates'
import { EUR_ZONE_COUNTRIES } from '../../config/unitSystems'
import { odometerKmToLocal, odometerLocalToKm } from '../../utils/unitConversions'
import { tariffLocationParams } from '../../utils/tariffLocation'
import { shouldRefetchPriceOnToggle } from './costSuggestion'

export interface LogFormData {
  kwhCharged: number | null
  costEur: number | null
  costExchangeRate: number | null
  costCurrency: string | null
  odometerKm: number | null
  socAfterChargePercent: number | null
  socBeforeChargePercent: number | null
  kwhAtVehicle: number | null
  chargeDurationMinutes: number | null
  maxChargingPowerKw: number | null
  loggedAt: string | null
  chargingType: 'AC' | 'DC'
  routeType: 'CITY' | 'COMBINED' | 'HIGHWAY'
  tireType: 'SUMMER' | 'ALL_YEAR' | 'WINTER'
  latitude: number | null
  longitude: number | null
  isPublicCharging: boolean
  cpoName: string | null
  chargingProviderId: string | null
  /**
   * Geohash of an already-stored log (edit mode). lat/lon are never persisted, so when editing
   * an imported charge this is the only location the client has.
   */
  geohash?: string | null
  /** Opt-in: after saving, price all cost-less logs at this location with the selected card. */
  applyTariffToLocation?: boolean
}

interface UserProvider {
  id: string
  providerName: string
  label: string | null
  acPricePerKwh: number | null
  dcPricePerKwh: number | null
}

const props = defineProps<{
  fieldErrors?: Set<string>
  odometerPlaceholder?: string
  // create mode: GPS toggle sichtbar; edit mode: kein GPS (Nominatim-Suche im Container)
  locationMode?: 'create' | 'edit'
  hideDatetime?: boolean
}>()

const form = defineModel<LogFormData>({ required: true })

const { t, locale } = useI18n()
const countryStore = useCountryStore()

const numberLocale = computed(() => locale.value === 'en' ? 'en-GB' : 'de-DE')
const formatLocalPerKwh = (v: number) =>
  `${v.toLocaleString(numberLocale.value, { minimumFractionDigits: 2, maximumFractionDigits: 2 })} ${localSymbol.value}/kWh`
const formatLocalAmount = (v: number) =>
  `${v.toLocaleString(numberLocale.value, { minimumFractionDigits: 2, maximumFractionDigits: 2 })} ${localSymbol.value}`

const isEurCountry = computed(() => EUR_ZONE_COUNTRIES.includes(countryStore.country))
const localCurrency = computed(() => countryStore.unitSystem.currency)
const localSymbol = computed(() => countryStore.unitSystem.currencySymbol)
const localSubunit = computed(() => countryStore.unitSystem.currencySubunit)
const exchangeRate = computed(() => EUR_EXCHANGE_RATES[localCurrency.value])

const usesMiles = computed(() => countryStore.unitSystem.distanceUnit === 'miles')
const distanceUnitLabel = computed(() => t(`logfields.unit_${countryStore.unitSystem.distanceUnit}`))

/** User-facing odometer value in local distance unit (km or miles) */
const odometerLocal = computed({
  get(): number | null {
    if (form.value.odometerKm == null) return null
    return odometerKmToLocal(form.value.odometerKm, usesMiles.value)
  },
  set(val: number | null) {
    form.value.odometerKm = val == null ? null : odometerLocalToKm(val, usesMiles.value)
  },
})

/** Convert local currency amount to EUR */
const localToEur = (local: number) => local / exchangeRate.value
/** Convert EUR to local currency amount */
const eurToLocal = (eur: number) => eur * exchangeRate.value

// ── Location ─────────────────────────────────────────────────────────────────
const locationEnabled = ref(
  props.locationMode === 'create'
    ? localStorage.getItem('ev_location_enabled') === 'true'
    : false
)
const locationStatus = ref<'idle' | 'loading' | 'success' | 'error' | 'manual'>('idle')
const locationErrorMessage = ref<string | null>(null)

const fetchPriceSuggestion = async (lat: number, lon: number, isPublic: boolean) => {
  if (costLocalTotal.value != null || costLocalPerKwh.value != null) return
  try {
    const res = await api.get('/logs/price-suggestion', { params: { lat, lon, isPublic } })
    if (res.status === 200 && res.data?.costPerKwh != null) {
      costMode.value = 'per_kwh'
      // Price suggestion comes in EUR - convert to local
      const eurPrice = Number(res.data.costPerKwh)
      costLocalPerKwh.value = isEurCountry.value ? eurPrice : Math.round(eurToLocal(eurPrice) * 1000) / 1000
      // Auto-select tariff if suggestion includes one
      if (res.data.chargingProviderId && !form.value.chargingProviderId) {
        form.value.chargingProviderId = res.data.chargingProviderId
      }
    }
  } catch {
    // kein Vorschlag verfügbar - kein Problem
  }
}

const requestCurrentLocation = () => {
  if (!navigator.geolocation) {
    locationStatus.value = 'error'
    locationErrorMessage.value = t('logfields.location_not_supported')
    return
  }
  locationStatus.value = 'loading'
  locationErrorMessage.value = null
  navigator.geolocation.getCurrentPosition(
    (pos) => {
      form.value.latitude = pos.coords.latitude
      form.value.longitude = pos.coords.longitude
      locationStatus.value = 'success'
      fetchPriceSuggestion(pos.coords.latitude, pos.coords.longitude, form.value.isPublicCharging)
    },
    (err) => {
      console.error('Geolocation error:', err)
      locationStatus.value = 'error'
      locationErrorMessage.value = t('logfields.location_denied')
    }
  )
}

const clearLocation = () => {
  form.value.latitude = null
  form.value.longitude = null
  locationStatus.value = 'idle'
  locationErrorMessage.value = null
}

const toggleLocation = () => {
  if (locationEnabled.value) {
    locationEnabled.value = false
    localStorage.setItem('ev_location_enabled', 'false')
    clearLocation()
  } else {
    locationEnabled.value = true
    localStorage.setItem('ev_location_enabled', 'true')
    requestCurrentLocation()
  }
}

// ── Helpers ───────────────────────────────────────────────────────────────────
// Pure datetime formatter (kept in sync with __tests__/LogFormFields.test.ts).
const padTwo = (n: number) => String(n).padStart(2, '0')
const formatDateTimeLocal = (d: Date): string =>
  `${d.getFullYear()}-${padTwo(d.getMonth() + 1)}-${padTwo(d.getDate())}T${padTwo(d.getHours())}:${padTwo(d.getMinutes())}`

const getCurrentDateTimeLocal = (): string => formatDateTimeLocal(new Date())

const chipValueNow = (): string => formatDateTimeLocal(new Date())
const chipValue1hAgo = (): string => {
  const d = new Date()
  d.setHours(d.getHours() - 1)
  return formatDateTimeLocal(d)
}
const chipValueYesterdayEvening = (): string => {
  const d = new Date()
  d.setDate(d.getDate() - 1)
  d.setHours(20, 0, 0, 0)
  return formatDateTimeLocal(d)
}

// ── VueDatePicker bridge (Desktop) ────────────────────────────────────────────
// VueDatePicker arbeitet mit Date-Objekten, das Form-Model mit
// datetime-local-Strings. Wir bruecken bidirektional und respektieren das
// "keine Zukunft"-Constraint.
const datePickerValue = computed<Date | null>({
  get(): Date | null {
    if (!form.value.loggedAt) return null
    const parsed = new Date(form.value.loggedAt)
    return isNaN(parsed.getTime()) ? null : parsed
  },
  set(val: Date | null) {
    form.value.loggedAt = val ? formatDateTimeLocal(val) : null
  },
})

const datePickerMaxDate = computed(() => new Date())

// Dark-Mode-Erkennung anhand der .dark-Klasse am <html>-Element.
const isDark = ref(false)
let darkObserver: MutationObserver | null = null
const updateDarkFlag = () => {
  isDark.value = typeof document !== 'undefined' && document.documentElement.classList.contains('dark')
}
onMounted(() => {
  updateDarkFlag()
  if (typeof MutationObserver === 'undefined' || typeof document === 'undefined') return
  darkObserver = new MutationObserver(updateDarkFlag)
  darkObserver.observe(document.documentElement, { attributes: true, attributeFilter: ['class'] })
})
onBeforeUnmount(() => {
  darkObserver?.disconnect()
  darkObserver = null
})

const setLoggedAt = (value: string) => {
  form.value.loggedAt = value
}

const inputClass = (field: string) =>
  [
    'mt-1 block w-full rounded-sm shadow-sm sm:text-sm p-2 border bg-white dark:bg-gray-700 dark:text-gray-100',
    props.fieldErrors?.has(field)
      ? 'border-red-400 focus:border-red-500 focus:ring-red-500'
      : 'border-gray-300 dark:border-gray-600 focus:border-indigo-500 focus:ring-indigo-500',
  ].join(' ')

// ── kWh Mode ──────────────────────────────────────────────────────────────────
const kwhMode = ref<'charger' | 'vehicle'>('charger')

// ── SoC Mode ──────────────────────────────────────────────────────────────────
const socMode = ref<'after' | 'before'>('after')

const socInputValue = computed({
  get(): number | null {
    return socMode.value === 'after' ? form.value.socAfterChargePercent : form.value.socBeforeChargePercent
  },
  set(val: number | null) {
    if (socMode.value === 'after') form.value.socAfterChargePercent = val
    else form.value.socBeforeChargePercent = val
  },
})

watch([() => form.value.socAfterChargePercent, () => form.value.socBeforeChargePercent], ([after, before]) => {
  if (after === null && before === null) socMode.value = 'after'
})

const kwhInputValue = computed({
  get(): number | null {
    return kwhMode.value === 'charger' ? form.value.kwhCharged : form.value.kwhAtVehicle
  },
  set(val: number | null) {
    if (kwhMode.value === 'charger') {
      form.value.kwhCharged = val
    } else {
      form.value.kwhAtVehicle = val
    }
  },
})

const toggleKwhMode = (mode: 'charger' | 'vehicle') => {
  kwhMode.value = mode
}

// Formular-Reset erkennen: erst wenn kWh UND Kosten gleichzeitig null werden (programmatischer Reset,
// nicht einfaches Löschen eines einzelnen Feldes durch den User).
watch(
  [() => form.value.kwhCharged, () => form.value.kwhAtVehicle, () => form.value.costEur],
  ([kwh, kwhV, cost]) => {
    if (kwh === null && kwhV === null && cost === null) kwhMode.value = 'charger'
  }
)

// ── Cost Mode ─────────────────────────────────────────────────────────────────
const costMode = ref<'total' | 'per_kwh'>('total')

// User-facing values in LOCAL currency
const costLocalTotal = ref<number | null>(null)
const costLocalPerKwh = ref<number | null>(null)

// Effective kWh for cost display: prefer kwhCharged (grid-side billing), fall back to kwhAtVehicle
const effectiveKwhForDisplay = computed<number | null>(() =>
  form.value.kwhCharged ?? form.value.kwhAtVehicle
)

// Sync local → EUR whenever local values change
const syncCostToEur = () => {
  let eurValue: number | null = null
  if (costMode.value === 'total' && costLocalTotal.value != null) {
    eurValue = isEurCountry.value ? costLocalTotal.value : localToEur(costLocalTotal.value)
  } else if (costMode.value === 'per_kwh' && costLocalPerKwh.value != null && effectiveKwhForDisplay.value) {
    const localTotal = costLocalPerKwh.value * effectiveKwhForDisplay.value
    eurValue = isEurCountry.value ? localTotal : localToEur(localTotal)
  }
  form.value.costEur = eurValue != null ? Math.round(eurValue * 100) / 100 : null

  // Set currency metadata for non-EUR
  if (!isEurCountry.value && eurValue != null) {
    form.value.costExchangeRate = exchangeRate.value
    form.value.costCurrency = localCurrency.value
  } else {
    form.value.costExchangeRate = null
    form.value.costCurrency = null
  }
}

const calculatedLocalTotal = computed(() => {
  const kwh = effectiveKwhForDisplay.value
  const price = costLocalPerKwh.value
  if (kwh != null && price != null) return Math.round(kwh * price * 100) / 100
  return null
})

const calculatedLocalPerKwh = computed(() => {
  const kwh = effectiveKwhForDisplay.value
  const total = costLocalTotal.value
  if (kwh != null && kwh > 0 && total != null) return Math.round(total / kwh * 1000) / 1000
  return null
})

watch([costLocalTotal, costLocalPerKwh, effectiveKwhForDisplay], syncCostToEur)

const toggleCostMode = (mode: 'total' | 'per_kwh') => {
  if (costMode.value === mode) return
  if (mode === 'per_kwh') {
    const kwh = effectiveKwhForDisplay.value
    const total = costLocalTotal.value
    if (kwh && total) {
      costLocalPerKwh.value = Math.round((total / kwh) * 1000) / 1000
    }
    // else: bestehenden costLocalPerKwh behalten (z.B. Preisvorschlag)
  } else {
    const calc = calculatedLocalTotal.value
    if (calc != null) {
      costLocalTotal.value = calc
    }
    // else: bestehenden costLocalTotal behalten
  }
  costMode.value = mode
}

// Initialize local cost from EUR (for edit mode or price suggestion)
const initLocalCostFromEur = () => {
  if (form.value.costEur == null) return
  // Use stored exchange rate if available (exact roundtrip), otherwise current rate
  const rate = (form.value as any).costExchangeRate ?? exchangeRate.value
  const localAmount = isEurCountry.value ? form.value.costEur : form.value.costEur * rate
  if (costMode.value === 'total') {
    costLocalTotal.value = Math.round(localAmount * 100) / 100
  } else {
    const kwh = effectiveKwhForDisplay.value
    costLocalPerKwh.value = kwh ? Math.round((localAmount / kwh) * 1000) / 1000 : null
  }
}

// Watch for external costEur changes (price suggestion, edit mode load)
let skipExternalSync = false
watch(() => form.value.costEur, (newVal) => {
  if (skipExternalSync) { skipExternalSync = false; return }
  // Only react to external changes (not our own syncCostToEur)
  if (newVal != null && costLocalTotal.value == null && costLocalPerKwh.value == null) {
    initLocalCostFromEur()
  }
}, { immediate: true })

// ── Tarif-Chips ───────────────────────────────────────────────────────────────
const userProviders = ref<UserProvider[]>([])

// Ohne Ladekarte bleibt die oeffentliche Ladung unzuordenbar - und der User tippt seine
// Kosten weiter von Hand ein. Deshalb hier, im Moment der oeffentlichen Ladung, anbieten
// die Karte anzulegen. Preis wird in derselben Einheit getippt, in der die Chips ihn zeigen.
const inlineCard = useInlineChargingCard(
  (typed: number) => isEurCountry.value ? typed / 100 : typed)

const showCardPrompt = computed(() =>
  userProviders.value.length === 0 && form.value.isPublicCharging === true)

const saveInlineCard = async () => {
  const created = await inlineCard.save()
  if (!created) return
  userProviders.value = [created]
  form.value.chargingProviderId = created.id
}

onMounted(async () => {
  // Nur auf 'vehicle' wechseln wenn kwhAtVehicle gesetzt ist aber kwhCharged nicht
  // Sind beide gesetzt, hat kwhCharged Prio (charger ist der Default)
  if (form.value.kwhAtVehicle != null && form.value.kwhAtVehicle > 0
      && (form.value.kwhCharged == null || form.value.kwhCharged <= 0)) {
    kwhMode.value = 'vehicle'
  }

  try {
    const res = await api.get<UserProvider[]>('/users/me/charging-providers')
    userProviders.value = res.data
    // Auto-select wenn nur ein Tarif vorhanden und noch keiner gewählt
    if (res.data.length === 1 && !form.value.chargingProviderId) {
      form.value.chargingProviderId = res.data[0].id
    }
  } catch {
    // nicht kritisch
  }

  // Wenn Location aus vorherigem Besuch aktiviert war: direkt GPS holen
  if (locationEnabled.value && props.locationMode === 'create') {
    requestCurrentLocation()
  }
})

watch(() => form.value.chargingType, (type) => {
  if (type === 'DC') form.value.isPublicCharging = true
})

watch(() => form.value.chargingProviderId, (providerId) => {
  if (!providerId) return
  if (costLocalTotal.value != null || costLocalPerKwh.value != null) return
  const provider = userProviders.value.find(p => p.id === providerId)
  if (!provider) return
  const price = form.value.chargingType === 'DC' ? provider.dcPricePerKwh : provider.acPricePerKwh
  if (price == null) return
  costMode.value = 'per_kwh'
  costLocalPerKwh.value = isEurCountry.value ? price : Math.round(eurToLocal(price) * 1000) / 1000
})

watch(() => form.value.isPublicCharging, (isPublic) => {
  // Einen bereits vorhandenen Preis - manuell eingegeben ODER aus einem
  // gespeicherten Log abgeleitet - nie ueberschreiben. Ein Vorschlag fuellt
  // nur leere Felder. Frueher loeschte der Toggle (auf Mobile, wo per GPS ein
  // Standort gesetzt ist) den gerade eingegebenen Preis, weil nur der
  // Edit-Modus geschuetzt war; AC->DC erzwingt oeffentlich und triggert dies.
  if (!shouldRefetchPriceOnToggle({
    hasLocation: form.value.latitude != null && form.value.longitude != null,
    costLocalTotal: costLocalTotal.value,
    costLocalPerKwh: costLocalPerKwh.value,
    costEur: form.value.costEur,
  })) return
  costMode.value = 'total'
  fetchPriceSuggestion(form.value.latitude!, form.value.longitude!, isPublic)
})

// --- Rueckwirkender Tarif: wie viele Ladungen an diesem Ort haben noch keinen Preis? ---
// Erst wenn Ort UND Ladekarte feststehen, ist die Frage ueberhaupt beantwortbar.
const pricelessCountAtLocation = ref(0)

const fetchPricelessCount = async () => {
  const location = tariffLocationParams(form.value)
  if (!location || !form.value.chargingProviderId) {
    pricelessCountAtLocation.value = 0
    return
  }
  try {
    const res = await api.get('/logs/priceless-count', { params: location })
    pricelessCountAtLocation.value = res.data.count ?? 0
  } catch {
    pricelessCountAtLocation.value = 0
  }
}

watch(
  () => [form.value.latitude, form.value.longitude, form.value.geohash,
         form.value.chargingProviderId, form.value.isPublicCharging],
  () => {
    // Abwaehlen der Karte setzt auch die Zustimmung zurueck - sonst liefe der
    // Folge-Call gegen eine Karte, die der User gar nicht mehr gewaehlt hat.
    if (!form.value.chargingProviderId) form.value.applyTariffToLocation = false
    fetchPricelessCount()
  },
  { immediate: true }
)

defineExpose({ clearLocation, locationEnabled, locationStatus, getCurrentDateTimeLocal })

/** Preiszeile auf der Kachel: AC-Preis in der lokalen Waehrung (Cent, wo es Cent gibt). */
function cardPriceLabel(p: UserProvider): string | null {
  if (p.acPricePerKwh == null) return null
  const value = isEurCountry.value ? p.acPricePerKwh * 100 : p.acPricePerKwh
  return `${value.toFixed(1)} ${localSubunit.value || localSymbol.value}/kWh`
}
</script>

<template>
  <!-- Pflichtfelder-Gruppe -->
  <div :class="locationMode !== 'edit' ? 'bg-gray-100 dark:bg-gray-800 md:rounded-sm p-3 space-y-3 -mx-4 md:mx-0' : 'space-y-3'">

  <!-- Row 1: kWh + Kosten -->
  <div class="grid grid-cols-2 gap-3 items-end">
    <div>
      <label class="block text-sm font-medium text-gray-700 dark:text-gray-300">{{ t('logfields.energy') }}</label>
      <div class="relative">
        <input v-model="kwhInputValue" type="number" step="0.1" :placeholder="t('logfields.kwh_placeholder')"
          :class="[inputClass('kwh'), 'pr-24']" />
        <div class="absolute right-1.5 top-1/2 -translate-y-1/2 flex rounded-full border border-gray-300 dark:border-gray-500 bg-gray-200 dark:bg-gray-600 p-0.5 text-xs">
          <button type="button" data-testid="kwh-mode-charger" @click="toggleKwhMode('charger')"
            :class="['px-1.5 py-0.5 rounded-full font-medium transition-all duration-200', kwhMode === 'charger' ? 'bg-white dark:bg-gray-500 text-indigo-700 dark:text-white shadow-sm' : 'text-gray-500 dark:text-gray-400']">
            {{ t('logfields.kwh_mode_charger') }}
          </button>
          <button type="button" data-testid="kwh-mode-vehicle" @click="toggleKwhMode('vehicle')"
            :class="['px-1.5 py-0.5 rounded-full font-medium transition-all duration-200', kwhMode === 'vehicle' ? 'bg-white dark:bg-gray-500 text-indigo-700 dark:text-white shadow-sm' : 'text-gray-500 dark:text-gray-400']">
            {{ t('logfields.kwh_mode_vehicle') }}
          </button>
        </div>
      </div>
    </div>
    <div>
      <label class="flex items-baseline gap-1.5 text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
        {{ t('logfields.cost_eur') }}
        <span v-if="costMode === 'total' && calculatedLocalPerKwh !== null"
          class="text-xs text-gray-400 dark:text-gray-500 font-normal whitespace-nowrap">
          = {{ formatLocalPerKwh(calculatedLocalPerKwh) }}
        </span>
        <span v-if="costMode === 'per_kwh' && calculatedLocalTotal !== null"
          class="text-xs text-gray-400 dark:text-gray-500 font-normal whitespace-nowrap">
          = {{ formatLocalAmount(calculatedLocalTotal) }}
        </span>
      </label>
      <div class="relative">
        <input v-if="costMode === 'total'" v-model="costLocalTotal" type="number" step="0.01" :placeholder="t('logfields.cost_eur_placeholder')"
          :class="[inputClass('cost'), 'pr-24']" />
        <input v-else v-model="costLocalPerKwh" type="number" step="0.001"
          :placeholder="t('logfields.cost_per_kwh_placeholder')"
          :class="[inputClass('cost'), 'pr-24']" />
        <div class="absolute right-1.5 top-1/2 -translate-y-1/2 flex rounded-full border border-gray-300 dark:border-gray-500 bg-gray-200 dark:bg-gray-600 p-0.5 text-xs">
          <button type="button" @click="toggleCostMode('total')"
            :class="['px-2.5 py-0.5 rounded-full font-medium transition-all duration-200 min-w-[2rem] text-center', costMode === 'total' ? 'bg-white dark:bg-gray-500 text-indigo-700 dark:text-white shadow-sm' : 'text-gray-500 dark:text-gray-400']">
            {{ localSymbol }}
          </button>
          <button type="button" @click="toggleCostMode('per_kwh')"
            :class="['px-1.5 py-0.5 rounded-full font-medium transition-all duration-200', costMode === 'per_kwh' ? 'bg-white dark:bg-gray-500 text-indigo-700 dark:text-white shadow-sm' : 'text-gray-500 dark:text-gray-400']">
            {{ localSymbol + '/kWh' }}
          </button>
        </div>
      </div>
    </div>
  </div>
  <p v-if="kwhMode === 'charger'" class="sm:hidden text-xs text-gray-400 dark:text-gray-500 -mt-1">{{ t('logfields.kwh_hint_mobile') }}</p>
  <p v-if="kwhMode === 'charger'" class="hidden sm:block text-xs text-gray-400 dark:text-gray-500 -mt-1">{{ t('logfields.kwh_hint') }}</p>
  <p v-if="kwhMode === 'vehicle'" class="text-xs text-gray-400 dark:text-gray-500 -mt-1">{{ t('logfields.kwh_at_vehicle_hint') }}</p>

  <!-- Row 2: Tachostand + SoC nach -->
  <div class="grid grid-cols-2 gap-3">
    <div>
      <label class="block text-sm font-medium text-gray-700 dark:text-gray-300">{{ t('logfields.odometer') }} ({{ distanceUnitLabel }})</label>
      <input v-model="odometerLocal" type="number" step="1" min="0"
        :placeholder="odometerPlaceholder ?? t('logfields.odometer')"
        :class="inputClass('odometer')" />
    </div>
    <div>
      <label class="block text-sm font-medium text-gray-700 dark:text-gray-300">
        {{ socMode === 'after' ? t('logfields.soc_after') : t('logfields.soc_before') }}
      </label>
      <div class="relative">
        <input v-model="socInputValue" type="number" min="0" max="100" step="0.1"
          :class="[inputClass('soc'), 'pr-20']" />
        <div class="absolute right-1.5 top-1/2 -translate-y-1/2 flex rounded-full border border-gray-300 dark:border-gray-500 bg-gray-200 dark:bg-gray-600 p-0.5 text-xs">
          <button type="button" @click="socMode = 'after'"
            :class="['px-1.5 py-0.5 rounded-full font-medium transition-all duration-200', socMode === 'after' ? 'bg-white dark:bg-gray-500 text-indigo-700 dark:text-white shadow-sm' : 'text-gray-500 dark:text-gray-400']">
            {{ t('logfields.soc_mode_after') }}
          </button>
          <button type="button" @click="socMode = 'before'"
            :class="['px-1.5 py-0.5 rounded-full font-medium transition-all duration-200', socMode === 'before' ? 'bg-white dark:bg-gray-500 text-indigo-700 dark:text-white shadow-sm' : 'text-gray-500 dark:text-gray-400']">
            {{ t('logfields.soc_mode_before') }}
          </button>
        </div>
      </div>
    </div>
  </div>

  <!-- Toggle-Zeile: GPS (nur create) + AC/DC + Öff. -->
  <div class="flex items-center justify-around">
    <div v-if="locationMode !== 'edit'" class="flex items-center gap-1.5">
      <GlobeAltIcon
        :class="[
          'h-5 w-5 transition-colors duration-300',
          locationStatus === 'loading' ? 'text-gray-400 animate-pulse' :
          locationStatus === 'success' ? 'text-green-500' :
          locationStatus === 'error' ? 'text-red-500' :
          'text-gray-300'
        ]"
      />
      <button
        type="button"
        @click="toggleLocation"
        :class="[
          'relative inline-flex h-8 w-14 flex-shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none',
          locationEnabled ? 'bg-green-500' : 'bg-gray-300'
        ]">
        <span :class="[
          'toggle-knob pointer-events-none inline-flex h-7 w-7 transform items-center justify-center rounded-full text-sm transition duration-200 ease-in-out',
          locationEnabled ? 'translate-x-6' : 'translate-x-0'
        ]">📍</span>
      </button>
    </div>
    <button
      type="button"
      @click="form.chargingType = form.chargingType === 'AC' ? 'DC' : 'AC'"
      :class="[
        'relative inline-flex h-8 w-16 flex-shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none',
        form.chargingType === 'DC' ? 'bg-orange-500' : 'bg-blue-500'
      ]">
      <span
        :class="[
          'toggle-knob pointer-events-none inline-flex h-7 w-9 transform items-center justify-center rounded-full text-xs font-bold transition duration-200 ease-in-out',
          form.chargingType === 'DC' ? 'translate-x-6' : 'translate-x-0'
        ]"
        :style="{ color: form.chargingType === 'DC' ? '#f97316' : '#3b82f6' }">
        {{ form.chargingType }}
      </span>
    </button>
    <div class="flex items-center gap-1.5">
      <span class="text-[10px] leading-tight text-gray-400 dark:text-gray-500 font-medium text-right">{{ t('logfields.public_charging_short') }}<br>{{ t('logfields.public_charging_short2') }}</span>
      <button
        type="button"
        data-testid="public-charging-toggle"
        @click="form.isPublicCharging = !form.isPublicCharging"
        :class="[
          'relative inline-flex h-8 w-14 flex-shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none',
          form.isPublicCharging ? 'bg-green-500' : 'bg-gray-300 dark:bg-gray-600'
        ]">
        <span :class="[
          'toggle-knob pointer-events-none inline-block h-7 w-7 transform rounded-full ring-0 transition duration-200 ease-in-out',
          form.isPublicCharging ? 'translate-x-6' : 'translate-x-0'
        ]" />
      </button>
    </div>
  </div>

  <!-- Location error message -->
  <p v-if="locationErrorMessage" class="text-xs text-red-500">{{ locationErrorMessage }}</p>

  <!-- Keine Ladekarte hinterlegt: hier, an der oeffentlichen Ladung, ist der Moment sie anzulegen -->
  <div v-if="showCardPrompt"
    data-testid="charging-card-prompt"
    class="rounded-lg border border-dashed border-indigo-300 bg-indigo-50/60 p-3
           dark:border-indigo-700 dark:bg-indigo-950/30">

    <button
      v-if="!inlineCard.isOpen.value"
      type="button"
      data-testid="charging-card-prompt-open"
      @click="inlineCard.open()"
      class="flex w-full items-center gap-3 text-left">
      <CreditCardIcon class="h-6 w-6 flex-shrink-0 text-indigo-600 dark:text-indigo-400" aria-hidden="true" />
      <span class="flex-1">
        <span class="block text-xs font-semibold text-gray-800 dark:text-gray-100">
          {{ t('logfields.card_prompt_title') }}
        </span>
        <span class="block text-[11px] leading-snug text-gray-600 dark:text-gray-400">
          {{ t('logfields.card_prompt_hint') }}
        </span>
      </span>
      <span class="flex-shrink-0 rounded-full bg-indigo-600 px-3 py-1.5 text-xs font-semibold text-white">
        {{ t('logfields.card_prompt_cta') }}
      </span>
    </button>

    <div v-else class="space-y-2.5">
      <label class="block text-xs font-medium text-gray-600 dark:text-gray-300" for="inline-card-provider">
        {{ t('logfields.card_prompt_title') }}
      </label>

      <select
        id="inline-card-provider"
        v-model="inlineCard.draft.value.providerName"
        class="w-full rounded-md border border-gray-300 bg-white px-3 py-2 text-sm
               focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500
               dark:border-gray-600 dark:bg-gray-700 dark:text-white">
        <option value="">{{ t('logfields.card_select_placeholder') }}</option>
        <option v-for="emp in KNOWN_EMPS.filter(e => e !== CUSTOM_PROVIDER)" :key="emp" :value="emp">{{ emp }}</option>
        <option :value="CUSTOM_PROVIDER">{{ t('logfields.card_other_provider') }}</option>
      </select>

      <input
        v-if="inlineCard.isCustom.value"
        v-model="inlineCard.draft.value.customProviderName"
        type="text"
        maxlength="100"
        :placeholder="t('logfields.card_custom_name_placeholder')"
        class="w-full rounded-md border border-gray-300 bg-white px-3 py-2 text-sm
               focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500
               dark:border-gray-600 dark:bg-gray-700 dark:text-white" />

      <div class="grid grid-cols-2 gap-2">
        <label class="block">
          <span class="mb-1 block text-[11px] text-gray-500 dark:text-gray-400">
            {{ t('logfields.card_ac_price', { unit: localSubunit || localSymbol }) }}
          </span>
          <input
            v-model="inlineCard.draft.value.acPrice"
            type="number" inputmode="decimal" step="0.1" min="0"
            class="w-full rounded-md border border-gray-300 bg-white px-3 py-2 text-sm
                   focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500
                   dark:border-gray-600 dark:bg-gray-700 dark:text-white" />
        </label>
        <label class="block">
          <span class="mb-1 block text-[11px] text-gray-500 dark:text-gray-400">
            {{ t('logfields.card_dc_price', { unit: localSubunit || localSymbol }) }}
          </span>
          <input
            v-model="inlineCard.draft.value.dcPrice"
            type="number" inputmode="decimal" step="0.1" min="0"
            class="w-full rounded-md border border-gray-300 bg-white px-3 py-2 text-sm
                   focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500
                   dark:border-gray-600 dark:bg-gray-700 dark:text-white" />
        </label>
      </div>

      <p v-if="inlineCard.failed.value" class="text-xs text-red-500">{{ t('logfields.card_save_failed') }}</p>

      <div class="flex gap-2">
        <button
          type="button"
          @click="inlineCard.cancel()"
          class="flex-1 rounded-md border border-gray-300 px-3 py-2 text-xs font-medium text-gray-600
                 dark:border-gray-600 dark:text-gray-300">
          {{ t('common.cancel') }}
        </button>
        <button
          type="button"
          data-testid="charging-card-save"
          :disabled="!inlineCard.canSave.value"
          @click="saveInlineCard()"
          class="flex-1 rounded-md bg-indigo-600 px-3 py-2 text-xs font-semibold text-white
                 disabled:cursor-not-allowed disabled:opacity-50">
          {{ inlineCard.saving.value ? t('common.saving') : t('logfields.card_save') }}
        </button>
      </div>
    </div>
  </div>

  <!-- Tarif-Chips (wenn User Tarife hinterlegt hat) -->
  <div v-if="userProviders.length > 0">
    <div class="flex gap-2.5 overflow-x-auto pb-2 -mx-1 px-1 justify-center">
      <button
        v-for="p in userProviders"
        :key="p.id"
        type="button"
        @click="form.chargingProviderId = form.chargingProviderId === p.id ? null : p.id"
        :class="[
          'btn-3d flex-shrink-0 w-28 h-[4.5rem] rounded-sm',
          form.chargingProviderId === p.id ? 'active opacity-100' : 'opacity-65 hover:opacity-85'
        ]"
        :style="{ '--btn-shadow-color': cardContainerStyle(p.id)['--btn-shadow-color'] }">
        <ChargingCardTile
          class="w-full h-full"
          :id="p.id"
          :title="p.label || p.providerName"
          :subtitle="cardPriceLabel(p)" />
      </button>
    </div>

    <!-- Rueckwirkend: Tarif auf alle preislosen Ladungen an diesem Ort anwenden -->
    <label
      v-if="pricelessCountAtLocation > 0"
      class="mt-1 flex items-start gap-2.5 cursor-pointer text-left">
      <input
        type="checkbox"
        v-model="form.applyTariffToLocation"
        class="mt-0.5 h-4 w-4 flex-shrink-0 rounded border-gray-300 text-indigo-600
               focus:ring-2 focus:ring-indigo-500 dark:border-gray-600 dark:bg-gray-700" />
      <span class="text-xs leading-snug text-gray-600 dark:text-gray-300">
        {{ t('logfields.apply_tariff_to_location', pricelessCountAtLocation) }}
      </span>
    </label>

  </div>

  </div><!-- end Pflichtfelder-Gruppe -->

  <slot name="after-required" />

  <!-- Trennlinie optionale Felder -->
  <div class="flex items-center gap-3 text-xs text-gray-400 dark:text-gray-500">
    <div class="flex-1 h-px bg-gray-200 dark:bg-gray-700" />
    <span>{{ t('logfields.optional_section') }}</span>
    <div class="flex-1 h-px bg-gray-200 dark:bg-gray-700" />
  </div>

  <!-- Streckenart + Reifen -->
  <div class="grid grid-cols-2 gap-3">
    <div>
      <label class="block text-xs font-medium text-gray-500 dark:text-gray-400 mb-1 text-center">{{ t('logfields.route_type_label') }}</label>
      <div class="relative flex w-full rounded-full border border-gray-200 dark:border-gray-700 bg-gray-100 dark:bg-gray-700 p-0.5">
        <div class="absolute top-0.5 bottom-0.5 rounded-full pill-slider transition-transform duration-200 ease-in-out pointer-events-none" style="width: calc(33.333% - 2px)"
          :style="{ transform: `translateX(${['CITY','COMBINED','HIGHWAY'].indexOf(form.routeType) * 100}%)` }" />
        <button type="button" @click="form.routeType = 'CITY'"
          :class="['relative z-10 flex-1 px-1 py-1.5 rounded-full text-xs font-medium transition-colors duration-200', form.routeType === 'CITY' ? 'text-indigo-700 dark:text-white' : 'text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300']">
          {{ t('logfields.route_city') }}
        </button>
        <button type="button" @click="form.routeType = 'COMBINED'"
          :class="['relative z-10 flex-1 px-1 py-1.5 rounded-full text-xs font-medium transition-colors duration-200', form.routeType === 'COMBINED' ? 'text-indigo-700 dark:text-white' : 'text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300']">
          {{ t('logfields.route_mix') }}
        </button>
        <button type="button" @click="form.routeType = 'HIGHWAY'"
          :class="['relative z-10 flex-1 px-1 py-1.5 rounded-full text-xs font-medium transition-colors duration-200', form.routeType === 'HIGHWAY' ? 'text-indigo-700 dark:text-white' : 'text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300']">
          {{ t('logfields.route_highway') }}
        </button>
      </div>
    </div>
    <div>
      <label class="block text-xs font-medium text-gray-500 dark:text-gray-400 mb-1 text-center">{{ t('logfields.tire_type_label') }}</label>
      <div class="relative flex w-full rounded-full border border-gray-200 dark:border-gray-700 bg-gray-100 dark:bg-gray-700 p-0.5">
        <div class="absolute top-0.5 bottom-0.5 rounded-full pill-slider transition-transform duration-200 ease-in-out pointer-events-none" style="width: calc(33.333% - 2px)"
          :style="{ transform: `translateX(${['SUMMER','ALL_YEAR','WINTER'].indexOf(form.tireType) * 100}%)` }" />
        <button type="button" @click="form.tireType = 'SUMMER'"
          :class="['relative z-10 flex-1 px-1 py-1.5 rounded-full text-xs font-medium transition-colors duration-200', form.tireType === 'SUMMER' ? 'text-indigo-700 dark:text-white' : 'text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300']">
          {{ t('logfields.tire_summer') }}
        </button>
        <button type="button" @click="form.tireType = 'ALL_YEAR'"
          :class="['relative z-10 flex-1 px-1 py-1.5 rounded-full text-xs font-medium transition-colors duration-200', form.tireType === 'ALL_YEAR' ? 'text-indigo-700 dark:text-white' : 'text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300']">
          {{ t('logfields.tire_allyear') }}
        </button>
        <button type="button" @click="form.tireType = 'WINTER'"
          :class="['relative z-10 flex-1 px-1 py-1.5 rounded-full text-xs font-medium transition-colors duration-200', form.tireType === 'WINTER' ? 'text-indigo-700 dark:text-white' : 'text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300']">
          {{ t('logfields.tire_winter') }}
        </button>
      </div>
    </div>
  </div>

  <!-- Dauer + Ladeleistung -->
  <div class="grid grid-cols-2 gap-3">
    <div>
      <label class="block text-sm font-medium text-gray-600 dark:text-gray-400">{{ t('logfields.duration') }}</label>
      <input v-model="form.chargeDurationMinutes" type="number"
        class="mt-1 block w-full rounded-sm border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm p-2 border" />
    </div>
    <div>
      <label class="block text-sm font-medium text-gray-600 dark:text-gray-400"><span class="sm:hidden">{{ t('logfields.max_power') }}</span><span class="hidden sm:inline">{{ t('logfields.max_power_full') }}</span></label>
      <input v-model="form.maxChargingPowerKw" type="number" step="0.1"
        class="mt-1 block w-full rounded-sm border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm p-2 border" />
    </div>
  </div>

  <!-- Datum/Uhrzeit -->
  <div v-if="!hideDatetime">
    <label class="block text-sm font-medium text-gray-600 dark:text-gray-400">{{ t('logfields.timestamp') }}</label>

    <!-- Quick-Chips: Jetzt, Vor 1h, Gestern Abend -->
    <div class="mt-1 flex flex-wrap gap-2">
      <button
        type="button"
        @click="setLoggedAt(chipValueNow())"
        class="chip-3d inline-flex items-center gap-1 rounded-full bg-white dark:bg-gray-700 border border-gray-300 dark:border-gray-600 px-3 py-1 text-xs font-medium text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-600 focus:outline-none focus:ring-2 focus:ring-indigo-500"
        :aria-label="t('logfields.timestamp_chip_now')">
        <ClockIcon class="h-3.5 w-3.5" />
        {{ t('logfields.timestamp_chip_now') }}
      </button>
      <button
        type="button"
        @click="setLoggedAt(chipValue1hAgo())"
        class="chip-3d inline-flex items-center gap-1 rounded-full bg-white dark:bg-gray-700 border border-gray-300 dark:border-gray-600 px-3 py-1 text-xs font-medium text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-600 focus:outline-none focus:ring-2 focus:ring-indigo-500"
        :aria-label="t('logfields.timestamp_chip_1h_ago')">
        <CalendarDaysIcon class="h-3.5 w-3.5" />
        {{ t('logfields.timestamp_chip_1h_ago') }}
      </button>
      <button
        type="button"
        @click="setLoggedAt(chipValueYesterdayEvening())"
        class="chip-3d inline-flex items-center gap-1 rounded-full bg-white dark:bg-gray-700 border border-gray-300 dark:border-gray-600 px-3 py-1 text-xs font-medium text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-600 focus:outline-none focus:ring-2 focus:ring-indigo-500"
        :aria-label="t('logfields.timestamp_chip_yesterday_evening')">
        <MoonIcon class="h-3.5 w-3.5" />
        {{ t('logfields.timestamp_chip_yesterday_evening') }}
      </button>
    </div>

    <!-- Mobile: nativer datetime-local Picker (<768px) -->
    <input
      type="datetime-local"
      :value="form.loggedAt ?? ''"
      :max="getCurrentDateTimeLocal()"
      @change="(e) => { form.loggedAt = (e.target as HTMLInputElement).value || null }"
      class="mt-2 block w-full rounded-sm border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm p-2 border md:hidden" />

    <!-- Desktop: VueDatePicker (>=768px) -->
    <div class="hidden md:block mt-2">
      <VueDatePicker
        v-model="datePickerValue"
        :max-date="datePickerMaxDate"
        :dark="isDark"
        :enable-time-picker="true"
        time-picker-inline
        :minutes-increment="1"
        :is-24="true"
        text-input
        auto-apply
        :clearable="true"
        :format="'yyyy-MM-dd HH:mm'"
        :teleport="true"
        :placeholder="t('logfields.timestamp')" />
    </div>

    <p class="text-xs text-gray-400 dark:text-gray-500 mt-1">{{ t('logfields.timestamp_hint') }}</p>
  </div>
</template>

<style scoped>
.chip-3d {
  box-shadow: 0 3px 0 0 rgba(0, 0, 0, 0.15);
  transform: translateY(0);
  transition: transform 0.08s ease, box-shadow 0.08s ease;
}
.chip-3d:active {
  box-shadow: 0 1px 0 0 rgba(0, 0, 0, 0.10);
  transform: translateY(2px);
}
.chip-3d.chip-selected {
  box-shadow: 0 3px 0 0 rgba(55, 48, 163, 0.6);
}
.chip-3d.chip-selected:active {
  box-shadow: 0 1px 0 0 rgba(55, 48, 163, 0.4);
  transform: translateY(2px);
}
</style>

