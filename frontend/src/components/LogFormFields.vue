<script setup lang="ts">
import { ref } from 'vue'
import { GlobeAltIcon, BoltIcon, TruckIcon, Battery0Icon, ClockIcon } from '@heroicons/vue/24/outline'

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
    locationErrorMessage.value = 'Geolokalisierung wird von deinem Browser nicht unterstützt'
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
      locationErrorMessage.value = 'Standortzugriff verweigert. Du kannst unten manuell nach einem Standort suchen.'
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
    'mt-1 block w-full rounded-md shadow-sm sm:text-sm p-2 border bg-white',
    props.fieldErrors?.has(field)
      ? 'border-red-400 focus:border-red-500 focus:ring-red-500'
      : 'border-gray-300 focus:border-indigo-500 focus:ring-indigo-500',
  ].join(' ')

// expose clearLocation so LogForm can call it on reset
defineExpose({ clearLocation, locationEnabled, locationStatus })
</script>

<template>
  <!-- Pflichtfelder-Gruppe: grauer Hintergrund nur im Create-Mode -->
  <div :class="locationMode !== 'edit' ? 'bg-gray-100 md:rounded-xl p-3 space-y-3 -mx-4 md:mx-0' : 'space-y-3'">

  <!-- Row 1: kWh + Kosten -->
  <div class="grid grid-cols-2 gap-3">
    <div>
      <label class="block text-sm font-medium text-gray-700">Energie (kWh)</label>
      <input v-model="form.kwhCharged" type="number" step="0.1" placeholder="z.B. 42.5"
        :class="inputClass('kwh')" />
    </div>
    <div>
      <label class="block text-sm font-medium text-gray-700">Kosten (€)</label>
      <input v-model="form.costEur" type="number" step="0.01" placeholder="z.B. 12.50"
        :class="inputClass('cost')" />
    </div>
  </div>

  <!-- Row 2: Tachostand + SoC nach (+ SoC vorher wenn Edit) -->
  <div class="grid grid-cols-2 gap-3">
    <div>
      <label class="block text-sm font-medium text-gray-700">Tachostand (km)</label>
      <input v-model="form.odometerKm" type="number" step="1"
        :placeholder="odometerPlaceholder ?? 'Tachostand (km)'"
        :class="inputClass('odometer')" />
    </div>
    <div>
      <label class="block text-sm font-medium text-gray-700">Akku nach Laden (%)</label>
      <input v-model="form.socAfterChargePercent" type="number" min="0" max="100" step="1"
        :class="inputClass('soc')" />
    </div>
    <div v-if="showSocBefore">
      <label class="block text-sm font-medium text-gray-700">Akku vor Laden (%)</label>
      <input v-model="form.socBeforeChargePercent" type="number" min="0" max="100" placeholder="optional"
        :class="inputClass('socBefore')" />
    </div>
    <!-- Ladeart im Edit-Mode: 2. Spalte neben SoC vorher -->
    <div v-if="showSocBefore">
      <label class="block text-sm font-medium text-gray-700">Ladeart</label>
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
    <div class="relative flex w-full rounded-full border border-gray-200 bg-gray-100 p-0.5">
      <!-- sliding white pill -->
      <div class="absolute top-0.5 bottom-0.5 rounded-full bg-white shadow-sm transition-transform duration-200 ease-in-out pointer-events-none" style="width: calc(33.333% - 2px)"
        :style="{ transform: `translateX(${['CITY','COMBINED','HIGHWAY'].indexOf(form.routeType) * 100}%)` }" />
      <button type="button" @click="form.routeType = 'CITY'"
        :class="['relative z-10 flex-1 px-1 py-1.5 rounded-full text-xs font-medium transition-colors duration-200', form.routeType === 'CITY' ? 'text-indigo-700' : 'text-gray-500 hover:text-gray-700']">
        Stadt
      </button>
      <button type="button" @click="form.routeType = 'COMBINED'"
        :class="['relative z-10 flex-1 px-1 py-1.5 rounded-full text-xs font-medium transition-colors duration-200', form.routeType === 'COMBINED' ? 'text-indigo-700' : 'text-gray-500 hover:text-gray-700']">
        Mix
      </button>
      <button type="button" @click="form.routeType = 'HIGHWAY'"
        :class="['relative z-10 flex-1 px-1 py-1.5 rounded-full text-xs font-medium transition-colors duration-200', form.routeType === 'HIGHWAY' ? 'text-indigo-700' : 'text-gray-500 hover:text-gray-700']">
        AB
      </button>
    </div>
    <!-- Reifenart -->
    <div class="relative flex w-full rounded-full border border-gray-200 bg-gray-100 p-0.5">
      <!-- sliding white pill -->
      <div class="absolute top-0.5 bottom-0.5 rounded-full bg-white shadow-sm transition-transform duration-200 ease-in-out pointer-events-none" style="width: calc(33.333% - 2px)"
        :style="{ transform: `translateX(${['SUMMER','ALL_YEAR','WINTER'].indexOf(form.tireType) * 100}%)` }" />
      <button type="button" @click="form.tireType = 'SUMMER'"
        :class="['relative z-10 flex-1 px-1 py-1.5 rounded-full text-xs font-medium transition-colors duration-200', form.tireType === 'SUMMER' ? 'text-indigo-700' : 'text-gray-500 hover:text-gray-700']">
        Som.
      </button>
      <button type="button" @click="form.tireType = 'ALL_YEAR'"
        :class="['relative z-10 flex-1 px-1 py-1.5 rounded-full text-xs font-medium transition-colors duration-200', form.tireType === 'ALL_YEAR' ? 'text-indigo-700' : 'text-gray-500 hover:text-gray-700']">
        Ganz.
      </button>
      <button type="button" @click="form.tireType = 'WINTER'"
        :class="['relative z-10 flex-1 px-1 py-1.5 rounded-full text-xs font-medium transition-colors duration-200', form.tireType === 'WINTER' ? 'text-indigo-700' : 'text-gray-500 hover:text-gray-700']">
        Win.
      </button>
    </div>
  </div>

  <!-- Dauer + Ladeleistung -->
  <div class="grid grid-cols-2 gap-3">
    <div>
      <label class="block text-sm font-medium text-gray-600">Dauer (min)</label>
      <input v-model="form.chargeDurationMinutes" type="number"
        class="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm p-2 border" />
    </div>
    <div>
      <label class="block text-sm font-medium text-gray-600"><span class="sm:hidden">Max. Leistung (kW)</span><span class="hidden sm:inline">Max. Ladeleistung (kW)</span></label>
      <input v-model="form.maxChargingPowerKw" type="number" step="0.1"
        class="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm p-2 border" />
    </div>
  </div>

  <!-- Datum/Uhrzeit -->
  <div>
    <label class="block text-sm font-medium text-gray-600">Ladezeitpunkt</label>
    <input
      v-model="form.loggedAt"
      type="datetime-local"
      :max="getCurrentDateTimeLocal()"
      class="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm p-2 border" />
    <p class="text-xs text-gray-400 mt-1">Leer lassen für aktuelle Zeit</p>
  </div>
</template>
