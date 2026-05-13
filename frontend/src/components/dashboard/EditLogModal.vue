<template>
  <div class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50">
    <div class="bg-white dark:bg-gray-800 border-2 border-gray-900 dark:border-white rounded-sm shadow-[4px_4px_0_0_#030712] dark:shadow-[4px_4px_0_0_#ffffff] w-full max-w-3xl flex flex-col max-h-[90vh]">
      <!-- Header -->
      <div class="flex items-center justify-between p-5 border-b-2 border-gray-300 dark:border-gray-700">
        <h2 class="text-lg font-bold tracking-tight text-gray-900 dark:text-gray-100">{{ t('dashboard.edit_title') }}</h2>
        <button @click="$emit('close')"
          class="p-2 border-2 border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-800 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-sm shadow-[2px_2px_0_0_#9ca3af] dark:shadow-[2px_2px_0_0_#4b5563] active:translate-x-[2px] active:translate-y-[2px] active:shadow-none transition-[transform,box-shadow] duration-75">
          <XMarkIcon class="w-4 h-4 text-gray-600 dark:text-gray-300" />
        </button>
      </div>

      <div class="overflow-y-auto p-5 space-y-4">
        <LogFormFields
          v-model="formData"
          location-mode="edit"
          :field-errors="fieldErrors"
        />

        <!-- Standort aktualisieren -->
        <div class="space-y-1">
          <label class="block text-[11px] font-bold uppercase tracking-wider text-gray-600 dark:text-gray-300">{{ t('logfields.update_location') }}</label>
          <div class="relative">
            <input
              v-model="locationSearchQuery"
              type="text"
              :placeholder="t('logfields.location_search_placeholder')"
              class="w-full border-2 border-gray-300 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100 rounded-sm px-3 py-2 text-sm font-medium focus:outline-none focus:border-green-500 transition-colors"
              @focus="showSuggestions = suggestions.length > 0"
            />
            <ul v-if="showSuggestions && suggestions.length > 0"
              class="absolute z-10 mt-1 w-full bg-white dark:bg-gray-800 border-2 border-gray-900 dark:border-white rounded-sm shadow-[4px_4px_0_0_#030712] dark:shadow-[4px_4px_0_0_#ffffff] max-h-48 overflow-y-auto">
              <li v-for="s in suggestions" :key="s.place_id"
                class="px-3 py-2 text-sm font-medium hover:bg-gray-50 dark:hover:bg-gray-700 cursor-pointer"
                @mousedown.prevent="selectLocation(s)">
                {{ s.display_name }}
              </li>
            </ul>
          </div>
          <p v-if="newLocationName" class="text-xs font-medium text-green-700 dark:text-green-400 mt-1">{{ t('logfields.new_location') }} {{ newLocationName }}</p>
          <p v-else-if="log.geohash" class="text-xs font-medium text-gray-500 dark:text-gray-500 mt-1">{{ t('logfields.current_location', { geohash: log.geohash }) }}</p>
        </div>

        <p v-if="errorMsg" class="text-sm font-medium border-l-2 border-red-500 bg-red-50 dark:bg-red-950/30 text-red-700 dark:text-red-300 px-4 py-3 rounded-r-sm">{{ errorMsg }}</p>
      </div>

      <!-- Footer -->
      <div class="flex justify-end gap-3 p-5 border-t-2 border-gray-300 dark:border-gray-700 shrink-0">
        <button @click="$emit('close')" v-haptic
          class="inline-flex items-center justify-center px-4 py-2.5 bg-white dark:bg-gray-800 hover:bg-gray-50 dark:hover:bg-gray-700 text-gray-700 dark:text-gray-300 font-bold uppercase tracking-wider text-[11px] rounded-sm border-2 border-gray-300 dark:border-gray-700 shadow-[2px_2px_0_0_#9ca3af] dark:shadow-[2px_2px_0_0_#4b5563] active:translate-x-[2px] active:translate-y-[2px] active:shadow-none transition-[transform,box-shadow] duration-75">
          {{ t('cars.cancel') }}
        </button>
        <button @click="save" v-haptic
          :disabled="loading || !isFormValid"
          class="inline-flex items-center justify-center gap-2 bg-green-600 hover:bg-green-500 disabled:bg-gray-300 dark:disabled:bg-gray-700 disabled:text-gray-500 disabled:cursor-not-allowed text-white font-bold uppercase tracking-wider text-[11px] px-5 py-2.5 rounded-sm border-2 border-green-600 disabled:border-gray-300 dark:disabled:border-gray-700 shadow-[3px_3px_0_0_#030712] disabled:shadow-none active:translate-x-[3px] active:translate-y-[3px] active:shadow-none transition-[transform,box-shadow] duration-75">
          <span v-if="loading" class="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
          {{ t('logfields.save') }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { XMarkIcon } from '@heroicons/vue/24/outline'
import api from '../../api/axios'
import LogFormFields, { type LogFormData } from '../log-form/LogFormFields.vue'
import { useI18n } from 'vue-i18n'
import { datetimeLocalToUtcIso } from '../../utils/datetime'

export interface EvLogResponse {
  id: string
  carId: string
  kwhCharged: number | null
  costEur: number | null
  costExchangeRate: number | null
  costCurrency: string | null
  chargeDurationMinutes: number | null
  geohash: string | null
  odometerKm: number | null
  maxChargingPowerKw: number | null
  socAfterChargePercent: number | null
  socBeforeChargePercent: number | null
  kwhAtVehicle: number | null
  loggedAt: string
  routeType: 'CITY' | 'COMBINED' | 'HIGHWAY' | null
  tireType: 'SUMMER' | 'ALL_YEAR' | 'WINTER' | null
  chargingType: 'AC' | 'DC' | 'UNKNOWN' | null
  isPublicCharging: boolean
  cpoName: string | null
  chargingProviderId: string | null
}

const props = defineProps<{ log: EvLogResponse }>()
const emit = defineEmits<{ close: []; saved: [log: EvLogResponse] }>()
const { t } = useI18n()

const toDatetimeLocal = (iso: string): string => {
  const d = new Date(iso)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

const formData = ref<LogFormData>({
  kwhCharged: props.log.kwhCharged,
  costEur: props.log.costEur ?? null,
  costExchangeRate: (props.log as any).costExchangeRate ?? null,
  costCurrency: (props.log as any).costCurrency ?? null,
  odometerKm: props.log.odometerKm ?? null,
  socAfterChargePercent: props.log.socAfterChargePercent ?? null,
  socBeforeChargePercent: props.log.socBeforeChargePercent ?? null,
  kwhAtVehicle: props.log.kwhAtVehicle ?? null,
  chargeDurationMinutes: props.log.chargeDurationMinutes ?? null,
  maxChargingPowerKw: props.log.maxChargingPowerKw ?? null,
  loggedAt: toDatetimeLocal(props.log.loggedAt),
  chargingType: (props.log.chargingType === 'DC' ? 'DC' : 'AC'),
  routeType: props.log.routeType ?? 'COMBINED',
  tireType: props.log.tireType ?? 'SUMMER',
  latitude: null,
  longitude: null,
  isPublicCharging: props.log.isPublicCharging ?? false,
  cpoName: props.log.cpoName ?? null,
  chargingProviderId: props.log.chargingProviderId ?? null,
})

const loading = ref(false)
const errorMsg = ref('')
const fieldErrors = ref<Set<string>>(new Set())

const isFormValid = computed(() => {
  const f = formData.value
  const hasValue = (v: any) => v !== null && v !== undefined && v !== ''
  const hasEnergy = (hasValue(f.kwhCharged) && Number(f.kwhCharged) > 0)
                 || (hasValue(f.kwhAtVehicle) && Number(f.kwhAtVehicle) > 0)
  return hasEnergy && hasValue(f.costEur)
})

// Location search
const locationSearchQuery = ref('')
const suggestions = ref<any[]>([])
const showSuggestions = ref(false)
const newLocationName = ref('')

let searchTimer: any = null
watch(locationSearchQuery, (q) => {
  clearTimeout(searchTimer)
  if (!q || q.length < 3) { suggestions.value = []; return }
  searchTimer = setTimeout(async () => {
    try {
      const res = await fetch(`https://nominatim.openstreetmap.org/search?q=${encodeURIComponent(q)}&format=json&limit=5`)
      suggestions.value = await res.json()
      showSuggestions.value = suggestions.value.length > 0
    } catch { /* ignore */ }
  }, 300)
})

function selectLocation(s: any) {
  formData.value.latitude = parseFloat(s.lat)
  formData.value.longitude = parseFloat(s.lon)
  newLocationName.value = s.display_name
  locationSearchQuery.value = s.display_name
  showSuggestions.value = false
}

async function save() {
  errorMsg.value = ''
  const f = formData.value

  // Normalize empty strings (from cleared number inputs) to null
  const n = (v: any): number | null => (v === '' || v === null || v === undefined) ? null : Number(v)

  const kwh = n(f.kwhCharged)
  const kwhV = n(f.kwhAtVehicle)
  const cost = n(f.costEur)
  const odometer = n(f.odometerKm)
  const soc = n(f.socAfterChargePercent)

  // Frontend validation (same rules as LogForm)
  fieldErrors.value = new Set()
  const errors: string[] = []
  if ((!kwh || kwh <= 0) && (!kwhV || kwhV <= 0)) { fieldErrors.value.add('kwh'); errors.push(t('logform.field_kwh')) }
  if (cost === null) { fieldErrors.value.add('cost'); errors.push(t('logform.field_cost')) }
  if (errors.length > 0) {
    errorMsg.value = t('logform.error_required', { fields: errors.join(', ') })
    return
  }

  loading.value = true
  try {
    const payload: Record<string, any> = {
      costEur: Math.round((cost ?? 0) * 100) / 100,
      kwhCharged: kwh != null && kwh > 0 ? Math.round(kwh * 100) / 100 : null,
      kwhAtVehicle: kwhV != null && kwhV > 0 ? Math.round(kwhV * 100) / 100 : null,
      chargeDurationMinutes: n(f.chargeDurationMinutes),
      odometerKm: odometer,
      maxChargingPowerKw: n(f.maxChargingPowerKw) !== null ? Math.round(n(f.maxChargingPowerKw)! * 100) / 100 : null,
      socAfterChargePercent: soc,
      socBeforeChargePercent: n(f.socBeforeChargePercent),
      loggedAt: f.loggedAt ? datetimeLocalToUtcIso(f.loggedAt) : null,
      chargingType: f.chargingType,
      routeType: f.routeType,
      tireType: f.tireType,
      costExchangeRate: f.costExchangeRate,
      costCurrency: f.costCurrency,
      chargingProviderId: f.chargingProviderId ?? null,
      isPublicCharging: f.isPublicCharging,
      cpoName: f.isPublicCharging && f.cpoName ? f.cpoName : null,
    }
    if (f.latitude !== null && f.longitude !== null) {
      payload.latitude = f.latitude
      payload.longitude = f.longitude
    }

    const res = await api.patch(`/logs/${props.log.id}`, payload)
    emit('saved', res.data)
    emit('close')
  } catch (e: any) {
    errorMsg.value = e?.response?.data?.message ?? 'Speichern fehlgeschlagen'
  } finally {
    loading.value = false
  }
}
</script>

