<script setup lang="ts">
import { ref, watch, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { GlobeAltIcon } from '@heroicons/vue/24/outline'
import api from '../../api/axios'
import { useCountryStore } from '../../stores/country'
import { EUR_EXCHANGE_RATES } from '../../config/exchangeRates'
import { EUR_ZONE_COUNTRIES } from '../../config/unitSystems'
import { odometerKmToLocal, odometerLocalToKm } from '../../utils/unitConversions'

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

const { t } = useI18n()
const countryStore = useCountryStore()

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
      costLocalPerKwh.value = isEurCountry.value ? eurPrice : Math.round(eurToLocal(eurPrice) * 100) / 100
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
const getCurrentDateTimeLocal = () => {
  const now = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}T${pad(now.getHours())}:${pad(now.getMinutes())}`
}

const inputClass = (field: string) =>
  [
    'mt-1 block w-full rounded-md shadow-sm sm:text-sm p-2 border bg-white dark:bg-gray-700 dark:text-gray-100',
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
  if (kwh != null && kwh > 0 && total != null) return Math.round(total / kwh * 100) / 100
  return null
})

watch([costLocalTotal, costLocalPerKwh, effectiveKwhForDisplay], syncCostToEur)

const toggleCostMode = (mode: 'total' | 'per_kwh') => {
  if (costMode.value === mode) return
  if (mode === 'per_kwh') {
    const kwh = effectiveKwhForDisplay.value
    const total = costLocalTotal.value
    if (kwh && total) {
      costLocalPerKwh.value = Math.round((total / kwh) * 100) / 100
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
    costLocalPerKwh.value = kwh ? Math.round((localAmount / kwh) * 100) / 100 : null
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
  costLocalPerKwh.value = isEurCountry.value ? price : Math.round(eurToLocal(price) * 100) / 100
})

watch(() => form.value.isPublicCharging, (isPublic) => {
  if (form.value.latitude != null && form.value.longitude != null) {
    costLocalPerKwh.value = null
    costLocalTotal.value = null
    costMode.value = 'total'
    fetchPriceSuggestion(form.value.latitude, form.value.longitude, isPublic)
  }
})

defineExpose({ clearLocation, locationEnabled, locationStatus, getCurrentDateTimeLocal })

type CardDesign = 'stripe' | 'circles' | 'solid' | 'pastel'
const CARD_DESIGNS: CardDesign[] = ['stripe', 'circles', 'solid', 'pastel']

function hashId(id: string): number {
  let h = 0
  for (let i = 0; i < id.length; i++) h = (h * 31 + id.charCodeAt(i)) >>> 0
  return h
}

function cardDesign(id: string): CardDesign {
  return CARD_DESIGNS[hashId(id) % CARD_DESIGNS.length]
}

// Rich solid colors with a subtle top-shine gradient
const SOLID_COLORS = [
  { bg: 'linear-gradient(180deg, #4f46e5 0%, #3730a3 100%)', shadow: '#312e81' }, // indigo
  { bg: 'linear-gradient(180deg, #0f766e 0%, #0d5e57 100%)', shadow: '#134e4a' }, // teal
  { bg: 'linear-gradient(180deg, #be123c 0%, #9f1239 100%)', shadow: '#881337' }, // rose
  { bg: 'linear-gradient(180deg, #b45309 0%, #92400e 100%)', shadow: '#78350f' }, // amber
  { bg: 'linear-gradient(180deg, #1d4ed8 0%, #1e40af 100%)', shadow: '#1e3a8a' }, // blue
  { bg: 'linear-gradient(180deg, #7e22ce 0%, #6b21a8 100%)', shadow: '#581c87' }, // purple
]

function solidColor(id: string) {
  return SOLID_COLORS[(hashId(id) >>> 3) % SOLID_COLORS.length]
}

function cardContainerStyle(id: string): Record<string, string> {
  const d = cardDesign(id)
  if (d === 'stripe')  return { background: '#f1f5f9', '--btn-shadow-color': '#94a3b8' }
  if (d === 'circles') return { background: '#1e1b4b', '--btn-shadow-color': '#0c0a2e' }
  if (d === 'solid')   return { background: solidColor(id).bg, '--btn-shadow-color': solidColor(id).shadow }
  /* pastel */         return { background: 'linear-gradient(135deg, #fde68a 0%, #fbcfe8 100%)', '--btn-shadow-color': '#d97706' }
}

function cardChipStyle(id: string): Record<string, string> {
  const d = cardDesign(id)
  if (d === 'stripe')  return { background: 'rgba(251,191,36,0.9)', border: '1px solid rgba(180,83,9,0.3)' }
  if (d === 'solid' || d === 'circles') return { background: 'rgba(253,224,71,0.75)', border: '1px solid rgba(253,224,71,0.4)' }
  /* pastel */         return { background: 'rgba(180,83,9,0.35)', border: '1px solid rgba(180,83,9,0.2)' }
}

function cardTextColor(id: string): string {
  const d = cardDesign(id)
  if (d === 'stripe')  return '#1f2937'
  if (d === 'solid' || d === 'circles') return 'rgba(255,255,255,0.95)'
  /* pastel */         return '#78350f'
}

function cardSubTextColor(id: string): string {
  const d = cardDesign(id)
  if (d === 'stripe')  return '#6b7280'
  if (d === 'solid' || d === 'circles') return 'rgba(255,255,255,0.55)'
  /* pastel */         return 'rgba(120,53,15,0.6)'
}
</script>

<template>
  <!-- Pflichtfelder-Gruppe -->
  <div :class="locationMode !== 'edit' ? 'bg-gray-100 dark:bg-gray-800 md:rounded-xl p-3 space-y-3 -mx-4 md:mx-0' : 'space-y-3'">

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
          = {{ calculatedLocalPerKwh.toFixed(2) }} {{ (localSubunit || localSymbol) + '/kWh' }}
        </span>
        <span v-if="costMode === 'per_kwh' && calculatedLocalTotal !== null"
          class="text-xs text-gray-400 dark:text-gray-500 font-normal whitespace-nowrap">
          = {{ calculatedLocalTotal.toFixed(2) }} {{ localSymbol }}
        </span>
      </label>
      <div class="relative">
        <input v-if="costMode === 'total'" v-model="costLocalTotal" type="number" step="0.01" :placeholder="t('logfields.cost_eur_placeholder')"
          :class="[inputClass('cost'), 'pr-24']" />
        <input v-else v-model="costLocalPerKwh" type="number" step="0.01"
          :placeholder="t('logfields.cost_per_kwh_placeholder')"
          :class="[inputClass('cost'), 'pr-24']" />
        <div class="absolute right-1.5 top-1/2 -translate-y-1/2 flex rounded-full border border-gray-300 dark:border-gray-500 bg-gray-200 dark:bg-gray-600 p-0.5 text-xs">
          <button type="button" @click="toggleCostMode('total')"
            :class="['px-2.5 py-0.5 rounded-full font-medium transition-all duration-200 min-w-[2rem] text-center', costMode === 'total' ? 'bg-white dark:bg-gray-500 text-indigo-700 dark:text-white shadow-sm' : 'text-gray-500 dark:text-gray-400']">
            {{ localSymbol }}
          </button>
          <button type="button" @click="toggleCostMode('per_kwh')"
            :class="['px-1.5 py-0.5 rounded-full font-medium transition-all duration-200', costMode === 'per_kwh' ? 'bg-white dark:bg-gray-500 text-indigo-700 dark:text-white shadow-sm' : 'text-gray-500 dark:text-gray-400']">
            {{ (localSubunit || localSymbol) + '/kWh' }}
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

  <!-- Tarif-Chips (wenn User Tarife hinterlegt hat) -->
  <div v-if="userProviders.length > 0">
    <div class="flex gap-2.5 overflow-x-auto pb-2 -mx-1 px-1 justify-center">
      <button
        v-for="p in userProviders"
        :key="p.id"
        type="button"
        @click="form.chargingProviderId = form.chargingProviderId === p.id ? null : p.id"
        :class="[
          'btn-3d flex-shrink-0 w-28 h-[4.5rem] rounded-xl overflow-hidden relative select-none',
          form.chargingProviderId === p.id ? 'active opacity-100' : 'opacity-65 hover:opacity-85'
        ]"
        :style="cardContainerStyle(p.id)">

        <!-- Stripe: diagonaler Farbkeil rechts -->
        <div v-if="cardDesign(p.id) === 'stripe'"
          class="absolute inset-y-0 right-0 w-14 skew-x-[-8deg] translate-x-4 pointer-events-none"
          style="background: linear-gradient(160deg, #059669 0%, #0891b2 100%);" />

        <!-- Circles: überlappende Halbkreise unten rechts -->
        <template v-else-if="cardDesign(p.id) === 'circles'">
          <div class="absolute -bottom-4 right-3 w-14 h-14 rounded-full opacity-60 pointer-events-none" style="background: #dc2626;" />
          <div class="absolute -bottom-4 right-8 w-14 h-14 rounded-full opacity-60 pointer-events-none" style="background: #ea580c;" />
        </template>

        <!-- Content (above decorations) -->
        <div class="relative z-10 h-full p-2.5 flex flex-col justify-between">
          <div class="w-5 h-3.5 rounded-[3px]" :style="cardChipStyle(p.id)" />
          <div>
            <div class="text-[11px] font-bold leading-tight truncate"
              :style="{ color: cardTextColor(p.id) }">
              {{ p.label || p.providerName }}
            </div>
            <div v-if="p.acPricePerKwh != null" class="text-[10px] leading-tight mt-0.5"
              :style="{ color: cardSubTextColor(p.id) }">
              {{ (isEurCountry ? p.acPricePerKwh * 100 : p.acPricePerKwh).toFixed(1) }} {{ (localSubunit || localSymbol) }}/kWh
            </div>
          </div>
        </div>
      </button>
    </div>

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
        class="mt-1 block w-full rounded-md border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm p-2 border" />
    </div>
    <div>
      <label class="block text-sm font-medium text-gray-600 dark:text-gray-400"><span class="sm:hidden">{{ t('logfields.max_power') }}</span><span class="hidden sm:inline">{{ t('logfields.max_power_full') }}</span></label>
      <input v-model="form.maxChargingPowerKw" type="number" step="0.1"
        class="mt-1 block w-full rounded-md border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm p-2 border" />
    </div>
  </div>

  <!-- Datum/Uhrzeit -->
  <div v-if="!hideDatetime">
    <label class="block text-sm font-medium text-gray-600 dark:text-gray-400">{{ t('logfields.timestamp') }}</label>
    <input
      v-model="form.loggedAt"
      type="datetime-local"
      :max="getCurrentDateTimeLocal()"
      class="mt-1 block w-full rounded-md border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm p-2 border" />
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

