<script setup lang="ts">
import { ref, onMounted, watch, computed, defineAsyncComponent } from 'vue'
import { useI18n } from 'vue-i18n'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import api from '../../api/axios'
import CarSelector from '../car/CarSelector.vue'
const OcrPhotoCapture = defineAsyncComponent(() => import('./OcrPhotoCapture.vue'))
import LogFormFields, { type LogFormData } from './LogFormFields.vue'
import { CameraIcon, PencilSquareIcon, TrashIcon, BoltIcon, TruckIcon, ClockIcon, Battery0Icon, SunIcon, XMarkIcon } from '@heroicons/vue/24/outline'
import { useCoinStore } from '../../stores/coins'
import { useLogsRefreshStore } from '../../stores/logsRefresh'
import { useHaptic } from '../../composables/useHaptic'
import { analytics } from '../../services/analytics'
import { useCarStore } from '../../stores/car'
import { tempBadgeClass } from '../../utils/temperatureColor'
import { consumptionTextClass } from '../../utils/consumptionColor'
import { costBadgeClass } from '../../utils/costColor'
import { isShortTrip } from '../../utils/shortTrip'
import { datetimeLocalToUtcIso } from '../../utils/datetime'
import ConsumptionInfoBox from '../dashboard/ConsumptionInfoBox.vue'
import EditLogModal from '../dashboard/EditLogModal.vue'

const { t, locale } = useI18n()
const { haptic } = useHaptic()
const { formatDistance, formatConsumption, formatCurrency, formatCostPerKwh } = useLocaleFormat()

// ── Format helpers (kept lean - mirror LogsView card design) ──────────────────
function formatLogDate(loggedAt: string): string {
  const d = new Date(loggedAt)
  const isCurrentYear = d.getFullYear() === new Date().getFullYear()
  const loc = locale.value === 'en' ? 'en-GB' : 'de-DE'
  const date = d.toLocaleDateString(loc, { day: 'numeric', month: 'numeric', ...(isCurrentYear ? {} : { year: 'numeric' }) })
  const time = d.toLocaleTimeString(loc, { hour: '2-digit', minute: '2-digit' })
  return `${date}, ${time}`
}

function chargingEfficiency(kwhCharged: number | null, kwhAtVehicle: number | null): number | null {
  if (!kwhCharged || !kwhAtVehicle || kwhCharged <= 0) return null
  return Math.round((kwhAtVehicle / kwhCharged) * 1000) / 10
}

// Toggle all log cards between absolute cost and cost-per-kWh display.
const showCostAbsolute = ref(false)
const coinStore = useCoinStore()
const carStore = useCarStore()
const logsRefreshStore = useLogsRefreshStore()

const emit = defineEmits<{ success: []; cancel: [] }>()

// ── OCR ───────────────────────────────────────────────────────────────────────
const showOcrCapture = ref(window.innerWidth < 768)
const ocrUsed = ref(false)

// ── Toast ─────────────────────────────────────────────────────────────────────
const showToast = ref(false)
const toastMessage = ref('')
const showCoinToast = (coins: number) => {
  toastMessage.value = t('logform.coin_toast', { n: coins })
  showToast.value = true
  setTimeout(() => { showToast.value = false }, 4000)
}

// ── Car ───────────────────────────────────────────────────────────────────────
const selectedCarId = ref<string | null>(null)
const hasCars = ref<boolean | null>(null)
const carCount = ref(0)

// ── Form state ────────────────────────────────────────────────────────────────
const formData = ref<LogFormData>({
  kwhCharged: null,
  costEur: null,
  costExchangeRate: null,
  costCurrency: null,
  odometerKm: null,
  socAfterChargePercent: null,
  socBeforeChargePercent: null,
  kwhAtVehicle: null,
  chargeDurationMinutes: null,
  maxChargingPowerKw: null,
  loggedAt: null,
  chargingType: 'AC',
  routeType: 'COMBINED',
  tireType: 'SUMMER',
  latitude: null,
  longitude: null,
  isPublicCharging: false,
  cpoName: null,
  chargingProviderId: null,
})

// ── Location search ────────────────────────────────────────────────────────────
const locationSearchQuery = ref('')
const locationSuggestions = ref<any[]>([])
const showLocationSuggestions = ref(false)
const locationDisplayName = ref('')

let locationSearchTimer: ReturnType<typeof setTimeout> | null = null
watch(locationSearchQuery, (q) => {
  if (locationSearchTimer) clearTimeout(locationSearchTimer)
  if (!q || q.length < 3) { locationSuggestions.value = []; return }
  locationSearchTimer = setTimeout(async () => {
    try {
      const res = await fetch(`https://nominatim.openstreetmap.org/search?q=${encodeURIComponent(q)}&format=json&limit=5`)
      locationSuggestions.value = await res.json()
      showLocationSuggestions.value = locationSuggestions.value.length > 0
    } catch { /* ignore */ }
  }, 300)
})

function selectLocation(s: any) {
  formData.value.latitude = parseFloat(s.lat)
  formData.value.longitude = parseFloat(s.lon)
  locationDisplayName.value = s.display_name
  locationSearchQuery.value = s.display_name
  showLocationSuggestions.value = false
}

const isFormValid = computed(() => {
  const f = formData.value
  const hasValue = (v: any) => v !== null && v !== undefined && v !== ''
  const hasEnergy = (hasValue(f.kwhCharged) && Number(f.kwhCharged) > 0)
                 || (hasValue(f.kwhAtVehicle) && Number(f.kwhAtVehicle) > 0)
  return !!selectedCarId.value && hasEnergy && hasValue(f.costEur)
})

const error = ref<string | null>(null)
const fieldErrors = ref<Set<string>>(new Set())
const shakeKey = ref(0)
const odometerPlaceholderOverride = ref<string | null>(null)

// ── Logs ──────────────────────────────────────────────────────────────────────
const logs = ref<any[]>([])
const editingLog = ref<any | null>(null)
const pendingDeleteId = ref<string | null>(null)

const getLastOdometerReading = (beforeDate?: string): number | null => {
  let filtered = logs.value.filter(l => l.odometerKm != null)
  if (beforeDate) {
    const cutoff = new Date(beforeDate).getTime()
    filtered = filtered.filter(l => new Date(l.loggedAt).getTime() < cutoff)
  }
  const sorted = filtered.sort((a, b) => new Date(b.loggedAt).getTime() - new Date(a.loggedAt).getTime())
  return sorted.length > 0 ? sorted[0].odometerKm : null
}

const getLastOdometerPlaceholder = (): string => {
  const refDate = formData.value.loggedAt || undefined
  const sorted = logs.value
    .filter(l => l.odometerKm != null)
    .filter(l => !refDate || new Date(l.loggedAt).getTime() < new Date(refDate).getTime())
    .sort((a, b) => new Date(b.loggedAt).getTime() - new Date(a.loggedAt).getTime())
  if (sorted.length === 0) return t('logfields.odometer')
  return t('logform.odometer_last', { km: formatDistance(sorted[0].odometerKm) })
}

const fetchLogs = async () => {
  if (!selectedCarId.value) { logs.value = []; return }
  try {
    const res = await api.get(`/logs?carId=${selectedCarId.value}&limit=5`)
    logs.value = res.data.sort((a: any, b: any) =>
      new Date(b.loggedAt).getTime() - new Date(a.loggedAt).getTime()
    )
    if (logs.value.length > 0) {
      const lastWithTireType = logs.value.find(l => l.tireType)
      if (lastWithTireType) formData.value.tireType = lastWithTireType.tireType
      const lastWithRouteType = logs.value.find(l => l.routeType)
      if (lastWithRouteType) formData.value.routeType = lastWithRouteType.routeType
    }
  } catch (err) {
    console.error('Failed to fetch logs:', err)
  }
}

watch(selectedCarId, fetchLogs)

const deleteLog = async (logId: string) => {
  if (pendingDeleteId.value !== logId) { pendingDeleteId.value = logId; return }
  pendingDeleteId.value = null
  try {
    await api.delete(`/logs/${logId}`)
    await fetchLogs()
  } catch {
    error.value = t('logform.delete_failed')
  }
}

// ── Submit ────────────────────────────────────────────────────────────────────
const logFormFieldsRef = ref<InstanceType<typeof LogFormFields> | null>(null)

const submitLog = async () => {
  fieldErrors.value = new Set()
  shakeKey.value++

  if (!selectedCarId.value) { error.value = t('logform.error_no_car'); return }

  const errors: string[] = []
  const f = formData.value

  if ((!f.kwhCharged || f.kwhCharged <= 0) && (!f.kwhAtVehicle || f.kwhAtVehicle <= 0)) {
    fieldErrors.value.add('kwh'); errors.push(t('logform.field_kwh'))
  }
  if (f.costEur === null || f.costEur === undefined) { fieldErrors.value.add('cost'); errors.push(t('logform.field_cost')) }
  if (f.odometerKm && f.odometerKm > 0) {
    const last = getLastOdometerReading(f.loggedAt || undefined)
    if (last !== null && f.odometerKm < last) {
      fieldErrors.value.add('odometer')
      odometerPlaceholderOverride.value = t('logform.odometer_min', { min: formatDistance(last) })
      errors.push(t('logform.field_odometer'))
    }
  }

  if (errors.length > 0) {
    error.value = t('logform.error_required', { fields: errors.join(', ') })
    return
  }

  try {
    error.value = null
    fieldErrors.value = new Set()
    const payload: any = {
      carId: selectedCarId.value,
      costEur: Math.round((f.costEur ?? 0) * 100) / 100,
      odometerKm: f.odometerKm,
      socAfterChargePercent: f.socAfterChargePercent,
    }
    if (f.kwhCharged != null && f.kwhCharged > 0) payload.kwhCharged = Math.round(f.kwhCharged * 100) / 100
    if (f.kwhAtVehicle !== null && f.kwhAtVehicle > 0) payload.kwhAtVehicle = Math.round(f.kwhAtVehicle * 100) / 100
    if (f.socBeforeChargePercent !== null) payload.socBeforeChargePercent = f.socBeforeChargePercent
    if (f.chargeDurationMinutes) payload.chargeDurationMinutes = f.chargeDurationMinutes
    if (f.latitude !== null && f.longitude !== null) {
      payload.latitude = f.latitude
      payload.longitude = f.longitude
    }
    if (f.maxChargingPowerKw !== null) payload.maxChargingPowerKw = Math.round(f.maxChargingPowerKw * 100) / 100
    if (f.loggedAt) payload.loggedAt = datetimeLocalToUtcIso(f.loggedAt)
    if (ocrUsed.value) payload.ocrUsed = true
    payload.chargingType = f.chargingType
    payload.routeType = f.routeType
    payload.tireType = f.tireType
    payload.isPublicCharging = f.isPublicCharging
    if (f.isPublicCharging && f.cpoName) payload.cpoName = f.cpoName
    if (f.chargingProviderId) payload.chargingProviderId = f.chargingProviderId
    if (f.costExchangeRate != null) payload.costExchangeRate = f.costExchangeRate
    if (f.costCurrency != null) payload.costCurrency = f.costCurrency

    const isFirstLog = logs.value.length === 0
    const res = await api.post('/logs', payload)

    showCoinToast(res.data.coinsAwarded)
    coinStore.refresh()
    analytics.trackLogCreated(ocrUsed.value ? 'ocr' : 'manual', isFirstLog)

    // Opt-in: denselben Tarif rueckwirkend auf die preislosen Ladungen an diesem Ort legen.
    // Bewusst nach dem Log-POST und fehlertolerant - ein Fehlschlag hier darf den
    // gerade gespeicherten Ladevorgang nicht in Frage stellen.
    if (f.applyTariffToLocation && f.chargingProviderId && f.latitude != null && f.longitude != null) {
      try {
        await api.patch('/logs/apply-tariff-at-location', {
          lat: f.latitude,
          lon: f.longitude,
          isPublic: f.isPublicCharging,
          chargingProviderId: f.chargingProviderId,
        })
      } catch {
        // Preis-Uebernahme fehlgeschlagen - Log ist gespeichert, User kann es erneut versuchen.
      }
    }

    // Reset form (keep car + tireType + routeType)
    const savedTireType = f.tireType
    const savedRouteType = f.routeType
    formData.value = {
      kwhCharged: null, costEur: null, costExchangeRate: null, costCurrency: null, odometerKm: null,
      socAfterChargePercent: null, socBeforeChargePercent: null, kwhAtVehicle: null,
      chargeDurationMinutes: null, maxChargingPowerKw: null, loggedAt: null,
      chargingType: 'AC', routeType: savedRouteType,
      tireType: savedTireType,
      latitude: null, longitude: null,
      isPublicCharging: false, cpoName: null, chargingProviderId: null,
      applyTariffToLocation: false,
    }
    ocrUsed.value = false
    odometerPlaceholderOverride.value = null
    logFormFieldsRef.value?.clearLocation()
    locationSearchQuery.value = ''
    locationDisplayName.value = ''
    locationSuggestions.value = []

    await fetchLogs()
    logsRefreshStore.notifyLogSaved()
    emit('success')
  } catch (err: any) {
    error.value = err.response?.data?.message || t('logform.error_save')
  }
}

const handleOcrData = (ocrResult: any) => {
  if (ocrResult.kwh !== null) {
    formData.value.kwhCharged = ocrResult.kwh
    formData.value.kwhAtVehicle = null  // OCR scannt Ladesäulenbon - immer Netz-Seite
  }
  if (ocrResult.cost !== null) formData.value.costEur = ocrResult.cost
  if (ocrResult.durationMinutes !== null) formData.value.chargeDurationMinutes = ocrResult.durationMinutes
  if (ocrResult.maxChargingPowerKw !== null) formData.value.maxChargingPowerKw = ocrResult.maxChargingPowerKw
  ocrUsed.value = true
  showOcrCapture.value = false
}

onMounted(async () => {
  try {
    const cars = await carStore.getCars()
    hasCars.value = cars.length > 0
    carCount.value = cars.length
    if (cars.length === 1) selectedCarId.value = cars[0].id
  } catch {
    hasCars.value = false
  }
  fetchLogs()
})
</script>

<template>
  <div class="p-4 md:p-6">
    <div class="flex items-center justify-between mb-4 md:mb-6">
      <div class="w-8" />
      <h1 class="text-xl md:text-3xl font-bold text-gray-800 dark:text-gray-200 text-center">{{ t('logform.title') }}</h1>
      <button type="button" @click="emit('cancel')"
        class="w-8 h-8 flex items-center justify-center rounded-sm text-gray-400 hover:text-gray-600 hover:bg-gray-100 dark:hover:text-gray-200 dark:hover:bg-gray-700 transition-colors">
        <XMarkIcon class="h-5 w-5" />
      </button>
    </div>

    <!-- No cars yet -->
    <div v-if="hasCars === false" class="text-center py-10 space-y-4">
      <TruckIcon class="h-14 w-14 mx-auto text-gray-300" />
      <p class="text-gray-600 dark:text-gray-400 font-medium">{{ t('logform.no_car_title') }}</p>
      <p class="text-sm text-gray-400 dark:text-gray-500">{{ t('logform.no_car_desc') }}</p>
      <router-link
        to="/cars"
        class="inline-flex items-center gap-2 bg-indigo-600 text-white px-5 py-2.5 rounded-sm text-sm font-medium hover:bg-indigo-700 transition"
      >
        <TruckIcon class="h-4 w-4" />
        {{ t('logform.no_car_btn') }}
      </router-link>
    </div>

    <template v-else-if="hasCars === true">
      <!-- Car Selector: only when multiple cars -->
      <div v-if="carCount > 1" class="mb-6">
        <CarSelector v-model="selectedCarId" />
      </div>

      <!-- Mode Toggle: Photo OCR vs Manual -->
      <div class="flex justify-center mb-4">
        <div class="relative grid grid-cols-2 rounded-full border border-gray-200 dark:border-gray-700 bg-gray-100 dark:bg-gray-700 p-0.5">
          <div class="absolute top-0.5 bottom-0.5 rounded-full pill-slider transition-transform duration-200 ease-in-out pointer-events-none" style="width: calc(50% - 2px)"
            :style="{ transform: `translateX(${showOcrCapture ? '0%' : '100%'})` }" />
          <button type="button" @click="showOcrCapture = true"
            :class="['relative z-10 flex items-center justify-center gap-1.5 px-3 py-1.5 rounded-full text-sm font-medium transition-colors duration-200', showOcrCapture ? 'text-indigo-700 dark:text-white' : 'text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300']">
            <CameraIcon class="h-4 w-4" />{{ t('logform.mode_photo') }}
          </button>
          <button type="button" @click="showOcrCapture = false"
            :class="['relative z-10 flex items-center justify-center gap-1.5 px-3 py-1.5 rounded-full text-sm font-medium transition-colors duration-200', !showOcrCapture ? 'text-indigo-700 dark:text-white' : 'text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300']">
            <PencilSquareIcon class="h-4 w-4" />{{ t('logform.mode_manual') }}
          </button>
        </div>
      </div>

      <!-- OCR -->
      <OcrPhotoCapture v-if="showOcrCapture" @dataExtracted="handleOcrData" @cancel="showOcrCapture = false" />

      <!-- Manual Entry -->
      <div v-if="!showOcrCapture">
        <form @submit.prevent="submitLog" novalidate class="space-y-4">
          <LogFormFields
            ref="logFormFieldsRef"
            v-model="formData"
            :field-errors="fieldErrors"
            :odometer-placeholder="odometerPlaceholderOverride ?? getLastOdometerPlaceholder()"
            location-mode="create"
            :hide-datetime="true"
          >
            <template #after-required>
              <button :key="shakeKey" type="submit" @click="haptic(20)"
                :disabled="!isFormValid"
                :class="['w-full bg-indigo-600 text-white p-3 rounded-sm btn-3d transition', !isFormValid ? 'opacity-40 cursor-not-allowed' : 'hover:bg-indigo-700', error ? 'ring-2 ring-red-400 ring-offset-2 animate-shake' : '']">
                {{ t('logform.save_btn') }}
              </button>
              <p v-if="error" class="text-red-500 dark:text-red-400 text-sm text-center mt-1">{{ error }}</p>
            </template>
          </LogFormFields>

          <!-- Ladezeitpunkt + Ladeort: auf Mobile gestapelt (Ort oben), auf Desktop nebeneinander -->
          <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div class="order-2 md:order-1">
              <label class="block text-sm font-medium text-gray-600 dark:text-gray-400">{{ t('logfields.timestamp') }}</label>
              <input
                v-model="formData.loggedAt"
                type="datetime-local"
                :max="logFormFieldsRef?.getCurrentDateTimeLocal()"
                class="mt-1 block w-full rounded-sm border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm p-2 border" />
              <p class="text-xs text-gray-400 dark:text-gray-500 mt-1">{{ t('logfields.timestamp_hint') }}</p>
            </div>
            <div class="order-1 md:order-2">
              <label class="block text-sm font-medium text-gray-600 dark:text-gray-400">{{ t('logfields.location_create_label') }}</label>
              <div class="relative">
                <input
                  v-model="locationSearchQuery"
                  type="text"
                  :placeholder="t('logfields.location_create_placeholder')"
                  class="mt-1 block w-full rounded-sm border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm p-2 border"
                  @focus="showLocationSuggestions = locationSuggestions.length > 0"
                />
                <ul v-if="showLocationSuggestions && locationSuggestions.length > 0"
                  class="absolute z-10 mt-1 w-full bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-sm shadow-[4px_4px_0_rgba(0,0,0,0.30)] dark:shadow-[4px_4px_0_rgba(255,255,255,0.30)] max-h-48 overflow-y-auto">
                  <li v-for="s in locationSuggestions" :key="s.place_id"
                    class="px-3 py-2 text-sm hover:bg-gray-50 dark:hover:bg-gray-700 cursor-pointer"
                    @mousedown.prevent="selectLocation(s)">
                    {{ s.display_name }}
                  </li>
                </ul>
              </div>
              <p v-if="locationDisplayName" class="text-xs text-green-600 mt-1">{{ t('logfields.new_location') }} {{ locationDisplayName }}</p>
              <p v-else class="text-xs text-gray-400 dark:text-gray-500 mt-1">{{ t('logform.location_hint') }}</p>
            </div>
          </div>
          <ConsumptionInfoBox :min-trips="5" class="mt-4" />
        </form>

        <!-- Watt Toast -->
        <div v-if="showToast" class="fixed bottom-6 right-6 z-50 animate-slide-in">
          <div class="bg-green-600 text-white px-5 py-3 rounded-sm shadow-[6px_6px_0_rgba(0,0,0,0.40)] dark:shadow-[6px_6px_0_rgba(255,255,255,0.40)] flex items-center gap-2">
            <BoltIcon class="h-5 w-5 flex-shrink-0" />
            <span class="font-medium text-sm">{{ toastMessage }}</span>
          </div>
        </div>

        <!-- Last 5 logs -->
        <div class="mt-10">
          <h2 class="text-xl font-semibold mb-4 text-gray-800 dark:text-gray-200">{{ t('logform.last_5_title') }}</h2>
          <div v-if="!selectedCarId" class="text-gray-500 dark:text-gray-400 text-center">{{ t('logform.no_car_selected') }}</div>
          <div v-else-if="logs.length === 0" class="text-gray-500 dark:text-gray-400 text-center">{{ t('logform.no_logs_yet') }}</div>
          <ul v-else class="space-y-2">
            <li v-for="log in logs" :key="log.id"
              class="p-3 bg-white dark:bg-gray-700 border-2 border-gray-300 dark:border-gray-600 rounded-sm shadow-[2px_2px_0_0_#d1d5db] dark:shadow-[2px_2px_0_0_#374151] space-y-2">
              <!-- Header row: Bolt + AC/DC, energy, date, cost-pill, edit, delete -->
              <div class="flex items-center justify-between gap-2">
                <div class="flex items-center gap-2 min-w-0 overflow-hidden flex-1">
                  <BoltIcon class="w-4 h-4 text-green-500 dark:text-green-400 flex-shrink-0" />
                  <span v-if="log.chargingType === 'AC'"
                    class="text-[10px] px-1.5 py-0.5 rounded bg-amber-100 dark:bg-amber-900/40 text-amber-700 dark:text-amber-300 font-medium flex-shrink-0">AC</span>
                  <span v-else-if="log.chargingType === 'DC'"
                    class="text-[10px] px-1.5 py-0.5 rounded bg-blue-100 dark:bg-blue-900/40 text-blue-700 dark:text-blue-300 font-medium flex-shrink-0">DC</span>
                  <span class="font-semibold text-indigo-700 dark:text-indigo-300 whitespace-nowrap">
                    {{ log.kwhAtVehicle ?? log.kwhCharged ?? '-' }} kWh
                  </span>
                  <span class="text-xs text-gray-500 dark:text-gray-400 whitespace-nowrap truncate">{{ formatLogDate(log.loggedAt) }}</span>
                </div>
                <div class="flex items-center gap-1.5 flex-shrink-0">
                  <span v-if="log.costEur != null && (log.kwhCharged ?? log.kwhAtVehicle)"
                    :class="['inline-flex items-center px-2 py-0.5 text-xs border rounded-full font-medium whitespace-nowrap cursor-pointer transition-all duration-75',
                             showCostAbsolute
                               ? 'bg-gray-50 dark:bg-gray-700 border-gray-200 dark:border-gray-600 text-gray-600 dark:text-gray-300 shadow-[0_4px_0_0_#d1d5db] dark:shadow-[0_4px_0_0_#111827] hover:shadow-[0_2px_0_0_#d1d5db] dark:hover:shadow-[0_2px_0_0_#111827] hover:translate-y-0.5 active:shadow-none active:translate-y-1'
                               : [(costBadgeClass(log.costEur, log.kwhCharged ?? log.kwhAtVehicle) ?? 'bg-green-50 border-green-200 text-green-700'), 'shadow-[0_4px_0_0_#d1d5db] dark:shadow-[0_4px_0_0_#111827] hover:shadow-[0_2px_0_0_#d1d5db] dark:hover:shadow-[0_2px_0_0_#111827] hover:translate-y-0.5 active:shadow-none active:translate-y-1'].join(' ')]"
                    @click.stop="showCostAbsolute = !showCostAbsolute">
                    <template v-if="showCostAbsolute">{{ formatCurrency(log.costEur) }}</template>
                    <template v-else>{{ formatCostPerKwh(log.costEur / (log.kwhCharged ?? log.kwhAtVehicle)) }}</template>
                  </span>
                  <button type="button" @click="editingLog = log"
                    class="p-1 text-gray-400 dark:text-gray-500 hover:text-blue-500 hover:bg-blue-50 dark:hover:bg-blue-900/30 rounded transition"
                    :title="t('logform.edit_title')"
                    :aria-label="t('logform.edit_title')">
                    <PencilSquareIcon class="w-4 h-4" />
                  </button>
                  <button type="button" @click="deleteLog(log.id)"
                    class="p-1 rounded transition"
                    :class="pendingDeleteId === log.id ? 'text-red-600 bg-red-50 dark:bg-red-900/30 ring-1 ring-red-300' : 'text-gray-400 dark:text-gray-500 hover:text-red-500 hover:bg-red-50 dark:hover:bg-red-900/30'"
                    :title="pendingDeleteId === log.id ? t('logform.confirm_delete') : t('logform.delete_title')"
                    :aria-label="pendingDeleteId === log.id ? t('logform.confirm_delete') : t('logform.delete_title')">
                    <TrashIcon class="w-4 h-4" />
                  </button>
                </div>
              </div>
              <!-- Stats row: consumption, duration, SoC, power -->
              <div v-if="log.consumptionKwhPer100km != null || isShortTrip(log) || log.chargeDurationMinutes || log.socAfterChargePercent != null || log.maxChargingPowerKw"
                class="flex flex-wrap gap-x-3 gap-y-0.5 items-center">
                <span v-if="log.consumptionKwhPer100km == null && isShortTrip(log)"
                  class="inline-flex items-center gap-1 text-xs text-gray-400 dark:text-gray-500 whitespace-nowrap">
                  {{ t('dashboard.short_trip_hint') }}
                </span>
                <span v-else-if="log.consumptionKwhPer100km != null"
                  :class="['inline-flex items-center gap-1 text-xs font-medium whitespace-nowrap',
                           log.consumptionImplausible
                             ? 'text-red-600 dark:text-red-400'
                             : log.consumptionIsEstimated
                               ? 'text-gray-500 dark:text-gray-400'
                               : consumptionTextClass(log.consumptionKwhPer100km, null)]">
                  {{ log.consumptionIsEstimated ? '~' : '' }}{{ formatConsumption(log.consumptionKwhPer100km) }}
                </span>
                <span v-if="log.chargeDurationMinutes"
                  class="inline-flex items-center gap-1 text-xs text-gray-500 dark:text-gray-400 whitespace-nowrap">
                  <ClockIcon class="w-3 h-3" />{{ log.chargeDurationMinutes }} min
                </span>
                <span v-if="log.socBeforeChargePercent != null && log.socAfterChargePercent != null"
                  class="inline-flex items-center gap-1 text-xs text-gray-500 dark:text-gray-400 whitespace-nowrap">
                  <Battery0Icon class="w-3 h-3" />{{ log.socBeforeChargePercent }} -> {{ log.socAfterChargePercent }}%
                </span>
                <span v-else-if="log.socAfterChargePercent != null"
                  class="inline-flex items-center gap-1 text-xs text-gray-500 dark:text-gray-400 whitespace-nowrap">
                  <Battery0Icon class="w-3 h-3" />{{ log.socAfterChargePercent }}%
                </span>
                <span v-if="log.maxChargingPowerKw"
                  class="inline-flex items-center gap-1 text-xs text-gray-500 dark:text-gray-400 whitespace-nowrap">
                  <BoltIcon class="w-3 h-3" />{{ log.maxChargingPowerKw }} kW
                </span>
              </div>
              <!-- Chips row: temperature, distance/odometer -->
              <div v-if="log.temperatureCelsius != null || log.odometerKm != null"
                class="flex flex-wrap gap-1.5 items-center">
                <span v-if="log.temperatureCelsius != null"
                  :class="['inline-flex items-center gap-0.5 px-2 py-0.5 border rounded text-xs whitespace-nowrap', tempBadgeClass(log.temperatureCelsius)]">
                  <SunIcon class="w-3 h-3" />{{ log.temperatureCelsius.toFixed(1) }}°C
                </span>
                <span v-if="log.odometerKm != null"
                  class="inline-flex items-center gap-1 px-2 py-0.5 bg-gray-50 dark:bg-gray-800 border border-gray-200 dark:border-gray-600 rounded-full text-xs text-gray-600 dark:text-gray-300 whitespace-nowrap">
                  <TruckIcon class="w-3 h-3" />{{ formatDistance(log.odometerKm) }}
                </span>
              </div>
              <!-- Brutto / Ladeeffizienz: nur wenn beide Werte gemessen -->
              <div v-if="log.kwhCharged != null && log.kwhAtVehicle != null"
                class="flex flex-wrap gap-x-4 gap-y-0.5 items-center text-xs text-gray-500 dark:text-gray-400">
                <span class="whitespace-nowrap">{{ log.kwhCharged }} kWh brutto</span>
                <span
                  :class="['font-medium whitespace-nowrap', (chargingEfficiency(log.kwhCharged, log.kwhAtVehicle) ?? 0) >= 90 ? 'text-green-600 dark:text-green-400' : 'text-amber-600 dark:text-amber-400']">
                  {{ chargingEfficiency(log.kwhCharged, log.kwhAtVehicle) }}% {{ t('logform.efficiency_label') }}
                </span>
              </div>
            </li>
          </ul>
        </div>
      </div>
    </template>
  </div>

  <EditLogModal
    v-if="editingLog"
    :log="editingLog"
    @close="editingLog = null"
    @saved="() => { editingLog = null; fetchLogs() }"
  />
</template>

<style scoped>
@keyframes shake {
  0%, 100% { transform: translateX(0); }
  20% { transform: translateX(-4px); }
  40% { transform: translateX(4px); }
  60% { transform: translateX(-3px); }
  80% { transform: translateX(3px); }
}
.animate-shake { animation: shake 0.35s ease-in-out; }

@keyframes slide-in {
  from { transform: translateX(100%); opacity: 0; }
  to { transform: translateX(0); opacity: 1; }
}
.animate-slide-in { animation: slide-in 0.3s ease-out; }

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
