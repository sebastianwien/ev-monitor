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
}

const props = defineProps<{
  fieldErrors?: Set<string>
  odometerPlaceholder?: string
  // create mode: GPS toggle + search; edit mode: search only (no GPS)
  locationMode?: 'create' | 'edit'
  showSocBefore?: boolean
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
    // € → €/kWh: Rückrechnung wenn kWh bekannt
    const kwh = form.value.kwhCharged
    const eur = form.value.costEur
    priceEurPerKwh.value = (kwh && eur) ? Math.round((eur / kwh) * 100) / 100 : null
  } else {
    // €/kWh → €: berechneten Wert übernehmen
    form.value.costEur = calculatedEur.value
  }
  costMode.value = mode
}

// expose clearLocation so LogForm can call it on reset
defineExpose({ clearLocation, locationEnabled, locationStatus })
</script>

<template>
  <!-- Pflichtfelder-Gruppe: grauer Hintergrund nur im Create-Mode -->
  <div :class="locationMode !== 'edit' ? 'bg-gray-100 dark:bg-gray-800 md:rounded-xl p-3 space-y-3 -mx-4 md:mx-0' : 'space-y-3'">

  <!-- Row 1: kWh + Kosten -->
  <div class="grid grid-cols-2 gap-3 items-end">
    <div>
      <label class="block text-sm font-medium text-gray-700 dark:text-gray-300">{{ t('logfields.energy') }}</label>
      <input v-model="form.kwhCharged" type="number" step="0.1" placeholder="z.B. 42.5"
        :class="inputClass('kwh')" />
    </div>
    <div>
      <div class="flex items-center justify-between mb-1">
        <label class="block text-sm font-medium text-gray-700 dark:text-gray-300">{{ costMode === 'eur' ? t('logfields.cost_eur') : t('logfields.cost_per_kwh') }}</label>
        <div class="relative flex rounded-full border border-gray-200 dark:border-gray-700 bg-gray-100 dark:bg-gray-700 p-0.5 text-xs">
          <div class="absolute top-0.5 bottom-0.5 rounded-full bg-white dark:bg-gray-600 shadow-sm transition-transform duration-200 ease-in-out pointer-events-none" style="width: calc(50% - 2px)"
            :style="{ transform: `translateX(${costMode === 'eur_kwh' ? '100%' : '0%'})` }" />
          <button type="button" @click="toggleCostMode('eur')"
            :class="['relative z-10 px-1.5 py-0.5 rounded-full font-medium transition-colors duration-200', costMode === 'eur' ? 'text-indigo-700' : 'text-gray-500 dark:text-gray-400']">
            €
          </button>
          <button type="button" @click="toggleCostMode('eur_kwh')"
            :class="['relative z-10 px-1.5 py-0.5 rounded-full font-medium transition-colors duration-200', costMode === 'eur_kwh' ? 'text-indigo-700' : 'text-gray-500 dark:text-gray-400']">
            ct
          </button>
        </div>
      </div>
      <input v-if="costMode === 'eur'" v-model="form.costEur" type="number" step="0.01" placeholder="z.B. 12.50"
        :class="inputClass('cost')" />
      <div v-else class="relative">
        <input v-model="priceEurPerKwh" type="number" step="0.01"
          :placeholder="calculatedEur === null ? 'z.B. 0.20' : ''"
          :class="[inputClass('cost'), 'pr-16']" />
        <span v-if="calculatedEur !== null" class="absolute right-2 top-1/2 -translate-y-1/2 text-xs text-gray-400 dark:text-gray-500 pointer-events-none">
          = {{ calculatedEur.toFixed(2) }} €
        </span>
      </div>
    </div>
  </div>

  <!-- Row 2: Tachostand + SoC nach (+ SoC vorher wenn Edit) -->
  <div class="grid grid-cols-2 gap-3">
    <div>
      <label class="block text-sm font-medium text-gray-700 dark:text-gray-300">{{ t('logfields.odometer') }}</label>
      <input v-model="form.odometerKm" type="number" step="1"
        :placeholder="odometerPlaceholder ?? 'Tachostand (km)'"
        :class="inputClass('odometer')" />
    </div>
    <div>
      <label class="block text-sm font-medium text-gray-700 dark:text-gray-300">{{ t('logfields.soc_after') }}</label>
      <input v-model="form.socAfterChargePercent" type="number" min="0" max="100" step="1"
        :class="inputClass('soc')" />
    </div>
    <div v-if="showSocBefore">
      <label class="block text-sm font-medium text-gray-700 dark:text-gray-300">{{ t('logfields.soc_before') }}</label>
      <input v-model="form.socBeforeChargePercent" type="number" min="0" max="100" placeholder="optional"
        :class="inputClass('socBefore')" />
    </div>
    <!-- Ladeart im Edit-Mode: 2. Spalte neben SoC vorher -->
    <div v-if="showSocBefore">
      <label class="block text-sm font-medium text-gray-700 dark:text-gray-300">{{ t('logfields.charge_type') }}</label>
      <div class="mt-1 flex items-center h-[34px]">
        <button
          type="button"
          @click="form.chargingType = form.chargingType === 'AC' ? 'DC' : 'AC'"
          :class="[
            'relative inline-flex h-8 w-16 flex-shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none',
            form.chargingType === 'DC' ? 'bg-orange-500' : 'bg-blue-500'
          ]">
          <span
            :class="[
              'pointer-events-none inline-flex h-7 w-9 transform items-center justify-center rounded-full bg-white shadow text-xs font-bold transition duration-200 ease-in-out',
              form.chargingType === 'DC' ? 'translate-x-6' : 'translate-x-0'
            ]"
            :style="{ color: form.chargingType === 'DC' ? '#f97316' : '#3b82f6' }">
            {{ form.chargingType }}
          </span>
        </button>
      </div>
    </div>
  </div>

  <!-- Row 3: Location toggle + AC/DC (create mode only) -->
  <div v-if="locationMode !== 'edit'" class="flex items-center justify-center gap-8">
    <div class="flex items-center gap-1.5">
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
          'pointer-events-none inline-flex h-7 w-7 transform items-center justify-center rounded-full bg-white shadow text-sm transition duration-200 ease-in-out',
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
          'pointer-events-none inline-flex h-7 w-9 transform items-center justify-center rounded-full bg-white shadow text-xs font-bold transition duration-200 ease-in-out',
          form.chargingType === 'DC' ? 'translate-x-6' : 'translate-x-0'
        ]"
        :style="{ color: form.chargingType === 'DC' ? '#f97316' : '#3b82f6' }">
        {{ form.chargingType }}
      </span>
    </button>
  </div>

  <!-- Location error message (create mode) -->
  <p v-if="locationMode === 'create' && locationErrorMessage" class="text-xs text-red-500">
    {{ locationErrorMessage }}
  </p>

  </div><!-- end Pflichtfelder-Gruppe -->

  <!-- Streckenart + Reifen -->
  <div class="grid grid-cols-2 gap-3">
    <!-- Streckenart -->
    <div class="relative flex w-full rounded-full border border-gray-200 dark:border-gray-700 bg-gray-100 dark:bg-gray-700 p-0.5">
      <!-- sliding white pill -->
      <div class="absolute top-0.5 bottom-0.5 rounded-full bg-white dark:bg-gray-600 shadow-sm transition-transform duration-200 ease-in-out pointer-events-none" style="width: calc(33.333% - 2px)"
        :style="{ transform: `translateX(${['CITY','COMBINED','HIGHWAY'].indexOf(form.routeType) * 100}%)` }" />
      <button type="button" @click="form.routeType = 'CITY'"
        :class="['relative z-10 flex-1 px-1 py-1.5 rounded-full text-xs font-medium transition-colors duration-200', form.routeType === 'CITY' ? 'text-indigo-700' : 'text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300']">
        {{ t('logfields.route_city') }}
      </button>
      <button type="button" @click="form.routeType = 'COMBINED'"
        :class="['relative z-10 flex-1 px-1 py-1.5 rounded-full text-xs font-medium transition-colors duration-200', form.routeType === 'COMBINED' ? 'text-indigo-700' : 'text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300']">
        {{ t('logfields.route_mix') }}
      </button>
      <button type="button" @click="form.routeType = 'HIGHWAY'"
        :class="['relative z-10 flex-1 px-1 py-1.5 rounded-full text-xs font-medium transition-colors duration-200', form.routeType === 'HIGHWAY' ? 'text-indigo-700' : 'text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300']">
        {{ t('logfields.route_highway') }}
      </button>
    </div>
    <!-- Reifenart -->
    <div class="relative flex w-full rounded-full border border-gray-200 dark:border-gray-700 bg-gray-100 dark:bg-gray-700 p-0.5">
      <!-- sliding white pill -->
      <div class="absolute top-0.5 bottom-0.5 rounded-full bg-white dark:bg-gray-600 shadow-sm transition-transform duration-200 ease-in-out pointer-events-none" style="width: calc(33.333% - 2px)"
        :style="{ transform: `translateX(${['SUMMER','ALL_YEAR','WINTER'].indexOf(form.tireType) * 100}%)` }" />
      <button type="button" @click="form.tireType = 'SUMMER'"
        :class="['relative z-10 flex-1 px-1 py-1.5 rounded-full text-xs font-medium transition-colors duration-200', form.tireType === 'SUMMER' ? 'text-indigo-700' : 'text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300']">
        {{ t('logfields.tire_summer') }}
      </button>
      <button type="button" @click="form.tireType = 'ALL_YEAR'"
        :class="['relative z-10 flex-1 px-1 py-1.5 rounded-full text-xs font-medium transition-colors duration-200', form.tireType === 'ALL_YEAR' ? 'text-indigo-700' : 'text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300']">
        {{ t('logfields.tire_allyear') }}
      </button>
      <button type="button" @click="form.tireType = 'WINTER'"
        :class="['relative z-10 flex-1 px-1 py-1.5 rounded-full text-xs font-medium transition-colors duration-200', form.tireType === 'WINTER' ? 'text-indigo-700' : 'text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300']">
        {{ t('logfields.tire_winter') }}
      </button>
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
  <div>
    <label class="block text-sm font-medium text-gray-600 dark:text-gray-400">{{ t('logfields.timestamp') }}</label>
    <input
      v-model="form.loggedAt"
      type="datetime-local"
      :max="getCurrentDateTimeLocal()"
      class="mt-1 block w-full rounded-md border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm p-2 border" />
    <p class="text-xs text-gray-400 dark:text-gray-500 mt-1">{{ t('logfields.timestamp_hint') }}</p>
  </div>
</template>
