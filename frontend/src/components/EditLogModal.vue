<template>
  <div class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50">
    <div class="bg-white dark:bg-gray-800 rounded-2xl shadow-xl w-full max-w-3xl flex flex-col max-h-[90vh]">
      <!-- Header -->
      <div class="flex items-center justify-between p-5 border-b border-gray-100 dark:border-gray-700">
        <h2 class="text-lg font-semibold text-gray-900 dark:text-gray-100">{{ t('dashboard.edit_title') }}</h2>
        <button @click="$emit('close')" class="text-gray-400 dark:text-gray-500 hover:text-gray-600 dark:hover:text-gray-300 transition-colors">
          <XMarkIcon class="w-5 h-5" />
        </button>
      </div>

      <div class="overflow-y-auto p-5 space-y-4">
        <LogFormFields
          v-model="formData"
          location-mode="edit"
          :show-soc-before="true"
          :field-errors="fieldErrors"
        />

        <!-- Standort aktualisieren -->
        <div class="space-y-1">
          <label class="block text-sm font-medium text-gray-700 dark:text-gray-300">{{ t('logfields.update_location') }}</label>
          <div class="relative">
            <input
              v-model="locationSearchQuery"
              type="text"
              :placeholder="t('logfields.location_search_placeholder')"
              class="w-full border border-gray-200 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-green-500"
              @focus="showSuggestions = suggestions.length > 0"
            />
            <ul v-if="showSuggestions && suggestions.length > 0"
              class="absolute z-10 mt-1 w-full bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl shadow-lg max-h-48 overflow-y-auto">
              <li v-for="s in suggestions" :key="s.place_id"
                class="px-3 py-2 text-sm hover:bg-gray-50 dark:hover:bg-gray-700 cursor-pointer"
                @mousedown.prevent="selectLocation(s)">
                {{ s.display_name }}
              </li>
            </ul>
          </div>
          <p v-if="newLocationName" class="text-xs text-green-600 mt-1">{{ t('logfields.new_location') }} {{ newLocationName }}</p>
          <p v-else-if="log.geohash" class="text-xs text-gray-400 dark:text-gray-500 mt-1">{{ t('logfields.current_location', { geohash: log.geohash }) }}</p>
        </div>

        <p v-if="errorMsg" class="text-sm text-red-600 bg-red-50 rounded-xl p-3">{{ errorMsg }}</p>
      </div>

      <!-- Footer -->
      <div class="flex justify-end gap-3 p-5 border-t border-gray-100 dark:border-gray-700 shrink-0">
        <button @click="$emit('close')" v-haptic
          class="btn-3d px-4 py-2 text-sm font-medium text-gray-700 dark:text-gray-300 bg-white dark:bg-gray-700 border border-gray-200 dark:border-gray-600 rounded-xl hover:bg-gray-50 dark:hover:bg-gray-600 transition-colors">
          {{ t('cars.cancel') }}
        </button>
        <button @click="save" v-haptic
          :disabled="loading || !isFormValid"
          class="btn-3d px-5 py-2 text-sm font-medium text-white bg-green-600 rounded-xl hover:bg-green-700 disabled:opacity-40 disabled:cursor-not-allowed transition-colors flex items-center gap-2">
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
import api from '../api/axios'
import LogFormFields, { type LogFormData } from './LogFormFields.vue'
import { useI18n } from 'vue-i18n'

export interface EvLogResponse {
  id: string
  carId: string
  kwhCharged: number
  costEur: number | null
  chargeDurationMinutes: number | null
  geohash: string | null
  odometerKm: number | null
  maxChargingPowerKw: number | null
  socAfterChargePercent: number | null
  socBeforeChargePercent: number | null
  loggedAt: string
  routeType: 'CITY' | 'COMBINED' | 'HIGHWAY' | null
  tireType: 'SUMMER' | 'ALL_YEAR' | 'WINTER' | null
  chargingType: 'AC' | 'DC' | 'UNKNOWN' | null
  isPublicCharging: boolean
  cpoName: string | null
}

const props = defineProps<{ log: EvLogResponse }>()
const emit = defineEmits<{ close: []; saved: [log: EvLogResponse] }>()
const { t } = useI18n()

// Backend returns LocalDateTime without timezone (e.g. "2026-03-15T14:30:00")
// Slice directly to avoid timezone conversion via new Date()
const toDatetimeLocal = (iso: string) => iso.slice(0, 16)

const formData = ref<LogFormData>({
  kwhCharged: props.log.kwhCharged,
  costEur: props.log.costEur ?? null,
  odometerKm: props.log.odometerKm ?? null,
  socAfterChargePercent: props.log.socAfterChargePercent ?? null,
  socBeforeChargePercent: props.log.socBeforeChargePercent ?? null,
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
})

const loading = ref(false)
const errorMsg = ref('')
const fieldErrors = ref<Set<string>>(new Set())

const isFormValid = computed(() => {
  const f = formData.value
  const hasValue = (v: any) => v !== null && v !== undefined && v !== ''
  return (
    hasValue(f.kwhCharged) && Number(f.kwhCharged) > 0 &&
    hasValue(f.costEur) &&
    hasValue(f.odometerKm) && Number(f.odometerKm) > 0 &&
    hasValue(f.socAfterChargePercent) && Number(f.socAfterChargePercent) >= 0 && Number(f.socAfterChargePercent) <= 100
  )
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
  const cost = n(f.costEur)
  const odometer = n(f.odometerKm)
  const soc = n(f.socAfterChargePercent)

  // Frontend validation (same rules as LogForm)
  fieldErrors.value = new Set()
  const errors: string[] = []
  if (!kwh || kwh <= 0) { fieldErrors.value.add('kwh'); errors.push(t('logform.field_kwh')) }
  if (cost === null) { fieldErrors.value.add('cost'); errors.push(t('logform.field_cost')) }
  if (!odometer || odometer <= 0) { fieldErrors.value.add('odometer'); errors.push(t('logform.field_odometer')) }
  if (soc === null || soc < 0 || soc > 100) { fieldErrors.value.add('soc'); errors.push(t('logform.field_soc')) }
  if (errors.length > 0) {
    errorMsg.value = t('logform.error_required', { fields: errors.join(', ') })
    return
  }

  loading.value = true
  try {
    const payload: Record<string, any> = {
      kwhCharged: Math.round((kwh ?? 0) * 100) / 100,
      costEur: Math.round((cost ?? 0) * 100) / 100,
      chargeDurationMinutes: n(f.chargeDurationMinutes),
      odometerKm: odometer,
      maxChargingPowerKw: n(f.maxChargingPowerKw) !== null ? Math.round(n(f.maxChargingPowerKw)! * 100) / 100 : null,
      socAfterChargePercent: soc,
      socBeforeChargePercent: n(f.socBeforeChargePercent),
      loggedAt: f.loggedAt + ':00',
      chargingType: f.chargingType,
      routeType: f.routeType,
      tireType: f.tireType,
    }
    if (f.latitude !== null && f.longitude !== null) {
      payload.latitude = f.latitude
      payload.longitude = f.longitude
    }

    const res = await api.put(`/logs/${props.log.id}`, payload)
    emit('saved', res.data)
    emit('close')
  } catch (e: any) {
    errorMsg.value = e?.response?.data?.message ?? 'Speichern fehlgeschlagen'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.btn-3d {
  box-shadow: 0 4px 0 0 rgba(0,0,0,0.2);
  transform: translateY(0);
  transition: transform 0.08s ease, box-shadow 0.08s ease;
}
.btn-3d:active {
  box-shadow: 0 1px 0 0 rgba(0,0,0,0.2);
  transform: translateY(3px);
}
</style>
