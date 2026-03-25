<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { GlobeAltIcon } from '@heroicons/vue/24/outline'

export interface LogFormData {
  kwhCharged: number | null
  costEur: number | null
  odometerKm: number | null
  socAfterChargePercent: number | null
  socBeforeChargePercent: number | null
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

// ── Location ─────────────────────────────────────────────────────────────────
const locationEnabled = ref(
  props.locationMode === 'create'
    ? localStorage.getItem('ev_location_enabled') === 'true'
    : false
)
const locationStatus = ref<'idle' | 'loading' | 'success' | 'error' | 'manual'>('idle')
const locationErrorMessage = ref<string | null>(null)

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

// ── Cost Mode ─────────────────────────────────────────────────────────────────
const costMode = ref<'eur' | 'eur_kwh'>('eur')
const priceEurPerKwh = ref<number | null>(null)

const calculatedEur = computed(() => {
  const kwh = form.value.kwhCharged
  const price = priceEurPerKwh.value
  if (kwh != null && price != null) return Math.round(kwh * price * 100) / 100
  return null
})

watch([() => form.value.kwhCharged, priceEurPerKwh], () => {
  if (costMode.value === 'eur_kwh') {
    form.value.costEur = calculatedEur.value
  }
})

const toggleCostMode = (mode: 'eur' | 'eur_kwh') => {
  if (costMode.value === mode) return
  if (mode === 'eur_kwh') {
    const kwh = form.value.kwhCharged
    const eur = form.value.costEur
    priceEurPerKwh.value = (kwh && eur) ? Math.round((eur / kwh) * 100) / 100 : null
  } else {
    form.value.costEur = calculatedEur.value
  }
  costMode.value = mode
}

// ── CPO Dropdown ──────────────────────────────────────────────────────────────
const CPO_LIST = [
  'IONITY',
  'Tesla Supercharger',
  'EnBW',
  'Aral Pulse',
  'Shell Recharge',
  'Fastned',
  'Allego',
  'Mer',
  'E.ON Drive',
  'TotalEnergies',
  'REWE',
  'Lidl',
  'Kaufland',
  'Avia',
  'OMV',
  'Q8',
  'Smatrics',
  'Wien Energie',
  'Verbund',
  'ÖAMTC',
  'EWZ',
  'ewb',
  'ChargePoint',
  'Clever',
  'Greenway',
  'Stadtwerke',
] as const

const cpoSelect = ref<string>(
  form.value.cpoName
    ? (CPO_LIST as readonly string[]).includes(form.value.cpoName) ? form.value.cpoName : 'OTHER'
    : ''
)

watch(() => form.value.chargingType, (type) => {
  if (type === 'DC') form.value.isPublicCharging = true
})

watch(cpoSelect, (val) => {
  if (val !== 'OTHER') {
    form.value.cpoName = val || null
  } else {
    form.value.cpoName = null
  }
})

defineExpose({ clearLocation, locationEnabled, locationStatus, getCurrentDateTimeLocal })
</script>

<template>
  <!-- Pflichtfelder-Gruppe -->
  <div :class="locationMode !== 'edit' ? 'bg-gray-100 dark:bg-gray-800 md:rounded-xl p-3 space-y-3 -mx-4 md:mx-0' : 'space-y-3'">

  <!-- Row 1: kWh + Kosten -->
  <div class="grid grid-cols-2 gap-3 items-end">
    <div>
      <label class="block text-sm font-medium text-gray-700 dark:text-gray-300">{{ t('logfields.energy') }}</label>
      <input v-model="form.kwhCharged" type="number" step="0.1" :placeholder="t('logfields.kwh_placeholder')"
        :class="inputClass('kwh')" />
    </div>
    <div>
      <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">{{ t('logfields.cost_eur') }}</label>
      <div class="relative">
        <input v-if="costMode === 'eur'" v-model="form.costEur" type="number" step="0.01" :placeholder="t('logfields.cost_eur_placeholder')"
          :class="[inputClass('cost'), 'pr-14']" />
        <input v-else v-model="priceEurPerKwh" type="number" step="0.01"
          :placeholder="t('logfields.cost_per_kwh_placeholder')"
          :class="[inputClass('cost'), 'pr-14']" />
        <div class="absolute right-1.5 top-1/2 -translate-y-1/2 flex rounded-full border border-gray-300 dark:border-gray-500 bg-gray-200 dark:bg-gray-600 p-0.5 text-xs">
          <div class="absolute top-0.5 bottom-0.5 rounded-full pill-slider transition-transform duration-200 ease-in-out pointer-events-none" style="width: calc(50% - 2px)"
            :style="{ transform: `translateX(${costMode === 'eur_kwh' ? '100%' : '0%'})` }" />
          <button type="button" @click="toggleCostMode('eur')"
            :class="['relative z-10 px-1.5 py-0.5 rounded-full font-medium transition-colors duration-200', costMode === 'eur' ? 'text-indigo-700 dark:text-white' : 'text-gray-500 dark:text-gray-400']">
            €
          </button>
          <button type="button" @click="toggleCostMode('eur_kwh')"
            :class="['relative z-10 px-1.5 py-0.5 rounded-full font-medium transition-colors duration-200', costMode === 'eur_kwh' ? 'text-indigo-700 dark:text-white' : 'text-gray-500 dark:text-gray-400']">
            ct
          </button>
        </div>
      </div>
      <p v-if="costMode === 'eur_kwh' && calculatedEur !== null" class="mt-1 text-xs text-gray-400 dark:text-gray-500">
        = {{ calculatedEur.toFixed(2) }} €
      </p>
    </div>
  </div>

  <!-- Row 2: Tachostand + SoC nach -->
  <div class="grid grid-cols-2 gap-3">
    <div>
      <label class="block text-sm font-medium text-gray-700 dark:text-gray-300">{{ t('logfields.odometer') }}</label>
      <input v-model="form.odometerKm" type="number" step="1"
        :placeholder="odometerPlaceholder ?? t('logfields.odometer')"
        :class="inputClass('odometer')" />
    </div>
    <div>
      <label class="block text-sm font-medium text-gray-700 dark:text-gray-300">{{ t('logfields.soc_after') }}</label>
      <input v-model="form.socAfterChargePercent" type="number" min="0" max="100" step="1"
        :class="inputClass('soc')" />
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

  <!-- CPO Dropdown (innerhalb der Pflichtfelder-Gruppe, direkt über Submit) -->
  <div v-if="form.isPublicCharging" class="space-y-1.5">
    <select v-model="cpoSelect" :class="inputClass('cpoName')">
      <option value="">{{ t('logfields.cpo_select_placeholder') }}</option>
      <option v-for="cpo in CPO_LIST" :key="cpo" :value="cpo">{{ cpo }}</option>
      <option value="OTHER">{{ t('logfields.cpo_other') }}</option>
    </select>
    <input
      v-if="cpoSelect === 'OTHER'"
      v-model="form.cpoName"
      type="text"
      :placeholder="t('logfields.cpo_name_placeholder')"
      maxlength="100"
      :class="inputClass('cpoName')"
    />
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

  <!-- Dauer + Ladeleistung + Akku vor Laden -->
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
    <div>
      <label class="block text-sm font-medium text-gray-600 dark:text-gray-400">{{ t('logfields.soc_before') }}</label>
      <input v-model="form.socBeforeChargePercent" type="number" min="0" max="100" :placeholder="t('logfields.optional')"
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

