<script setup lang="ts">
import { ref, onMounted, watch, computed } from 'vue'
import api from '../api/axios'
import CarSelector from './CarSelector.vue'
import OcrPhotoCapture from './OcrPhotoCapture.vue'
import LogFormFields, { type LogFormData } from './LogFormFields.vue'
import { CameraIcon, PencilSquareIcon, TrashIcon, BoltIcon, TruckIcon, ClockIcon, Battery0Icon, SunIcon } from '@heroicons/vue/24/outline'
import { useCoinStore } from '../stores/coins'
import { useHaptic } from '../composables/useHaptic'
import { analytics } from '../services/analytics'
import { useCarStore } from '../stores/car'
import { tempBadgeClass } from '../utils/temperatureColor'
import ConsumptionInfoBox from './ConsumptionInfoBox.vue'
import EditLogModal from './EditLogModal.vue'

const { haptic } = useHaptic()
const coinStore = useCoinStore()
const carStore = useCarStore()

const emit = defineEmits<{ success: [] }>()

// ── OCR ───────────────────────────────────────────────────────────────────────
const showOcrCapture = ref(window.innerWidth < 768)
const ocrUsed = ref(false)

// ── Toast ─────────────────────────────────────────────────────────────────────
const showToast = ref(false)
const toastMessage = ref('')
const showCoinToast = (coins: number) => {
  toastMessage.value = `+${coins} Watt erhalten!`
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
  odometerKm: null,
  socAfterChargePercent: null,
  socBeforeChargePercent: null,
  chargeDurationMinutes: null,
  maxChargingPowerKw: null,
  loggedAt: null,
  chargingType: 'AC',
  routeType: 'COMBINED',
  tireType: 'SUMMER',
  latitude: null,
  longitude: null,
})

const isFormValid = computed(() => {
  const f = formData.value
  const hasValue = (v: any) => v !== null && v !== undefined && v !== ''
  return (
    !!selectedCarId.value &&
    hasValue(f.kwhCharged) && Number(f.kwhCharged) > 0 &&
    hasValue(f.costEur) &&
    hasValue(f.odometerKm) && Number(f.odometerKm) > 0 &&
    hasValue(f.socAfterChargePercent) && Number(f.socAfterChargePercent) >= 0 && Number(f.socAfterChargePercent) <= 100
  )
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
  if (sorted.length === 0) return 'Tachostand (km)'
  return `zuletzt ${sorted[0].odometerKm.toLocaleString('de-DE')} km`
}

const fetchLogs = async () => {
  if (!selectedCarId.value) { logs.value = []; return }
  try {
    const res = await api.get(`/logs?carId=${selectedCarId.value}&limit=5`)
    logs.value = res.data.sort((a: any, b: any) =>
      new Date(b.loggedAt).getTime() - new Date(a.loggedAt).getTime()
    )
    if (logs.value.length > 0 && logs.value[0].tireType) {
      formData.value.tireType = logs.value[0].tireType
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
    error.value = 'Löschen fehlgeschlagen. Bitte versuche es erneut.'
  }
}

// ── Submit ────────────────────────────────────────────────────────────────────
const logFormFieldsRef = ref<InstanceType<typeof LogFormFields> | null>(null)

const submitLog = async () => {
  fieldErrors.value = new Set()
  shakeKey.value++

  if (!selectedCarId.value) { error.value = 'Bitte wähle ein Fahrzeug aus'; return }

  const errors: string[] = []
  const f = formData.value

  if (!f.kwhCharged || f.kwhCharged <= 0) { fieldErrors.value.add('kwh'); errors.push('Energie (kWh)') }
  if (f.costEur === null || f.costEur === undefined) { fieldErrors.value.add('cost'); errors.push('Kosten (€)') }
  if (!f.odometerKm || f.odometerKm <= 0) {
    fieldErrors.value.add('odometer'); errors.push('Tachostand')
  } else {
    const last = getLastOdometerReading(f.loggedAt || undefined)
    if (last !== null && f.odometerKm < last) {
      fieldErrors.value.add('odometer')
      odometerPlaceholderOverride.value = `sollte >= ${last.toLocaleString('de-DE')} km sein`
      errors.push(`Tachostand muss mindestens ${last.toLocaleString('de-DE')} km sein`)
    }
  }
  if (f.socAfterChargePercent === null || f.socAfterChargePercent < 0 || f.socAfterChargePercent > 100) {
    fieldErrors.value.add('soc'); errors.push('Akkustand nach Laden (0–100%)')
  }

  if (errors.length > 0) {
    error.value = `Bitte fülle alle Pflichtfelder korrekt aus: ${errors.join(', ')}`
    return
  }

  try {
    error.value = null
    fieldErrors.value = new Set()
    const payload: any = {
      carId: selectedCarId.value,
      kwhCharged: Math.round((f.kwhCharged ?? 0) * 100) / 100,
      costEur: Math.round((f.costEur ?? 0) * 100) / 100,
      odometerKm: f.odometerKm,
      socAfterChargePercent: f.socAfterChargePercent,
    }
    if (f.chargeDurationMinutes) payload.chargeDurationMinutes = f.chargeDurationMinutes
    if (f.latitude !== null && f.longitude !== null) {
      payload.latitude = f.latitude
      payload.longitude = f.longitude
    }
    if (f.maxChargingPowerKw !== null) payload.maxChargingPowerKw = Math.round(f.maxChargingPowerKw * 100) / 100
    if (f.loggedAt) payload.loggedAt = f.loggedAt + ':00'
    if (ocrUsed.value) payload.ocrUsed = true
    payload.chargingType = f.chargingType
    payload.routeType = f.routeType
    payload.tireType = f.tireType

    const isFirstLog = logs.value.length === 0
    const res = await api.post('/logs', payload)

    showCoinToast(res.data.coinsAwarded)
    coinStore.refresh()
    analytics.trackLogCreated(ocrUsed.value ? 'ocr' : 'manual', isFirstLog)

    // Reset form (keep car + tireType)
    const savedTireType = f.tireType
    formData.value = {
      kwhCharged: null, costEur: null, odometerKm: null,
      socAfterChargePercent: null, socBeforeChargePercent: null,
      chargeDurationMinutes: null, maxChargingPowerKw: null, loggedAt: null,
      chargingType: 'AC', routeType: 'COMBINED',
      tireType: savedTireType,
      latitude: null, longitude: null,
    }
    ocrUsed.value = false
    odometerPlaceholderOverride.value = null
    logFormFieldsRef.value?.clearLocation()

    await fetchLogs()
    emit('success')
  } catch (err: any) {
    error.value = err.response?.data?.message || 'Ladevorgang konnte nicht gespeichert werden'
  }
}

const handleOcrData = (ocrResult: any) => {
  if (ocrResult.kwh !== null) formData.value.kwhCharged = ocrResult.kwh
  if (ocrResult.cost !== null) formData.value.costEur = ocrResult.cost
  if (ocrResult.durationMinutes !== null) formData.value.chargeDurationMinutes = ocrResult.durationMinutes
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
  <div class="md:max-w-2xl md:mx-auto p-4 md:p-6 bg-white md:rounded-xl md:shadow-lg md:mt-8">
    <h1 class="text-xl md:text-3xl font-bold text-gray-800 mb-4 md:mb-6 text-center">Ladevorgang erfassen</h1>

    <!-- No cars yet -->
    <div v-if="hasCars === false" class="text-center py-10 space-y-4">
      <TruckIcon class="h-14 w-14 mx-auto text-gray-300" />
      <p class="text-gray-600 font-medium">Noch kein Fahrzeug hinterlegt</p>
      <p class="text-sm text-gray-400">Lege zuerst ein Fahrzeug an, bevor du einen Ladevorgang erfasst.</p>
      <router-link
        to="/cars"
        class="inline-flex items-center gap-2 bg-indigo-600 text-white px-5 py-2.5 rounded-lg text-sm font-medium hover:bg-indigo-700 transition"
      >
        <TruckIcon class="h-4 w-4" />
        Fahrzeug anlegen
      </router-link>
    </div>

    <template v-else-if="hasCars === true">
      <!-- Car Selector: only when multiple cars -->
      <div v-if="carCount > 1" class="mb-6">
        <CarSelector v-model="selectedCarId" />
      </div>

      <!-- Mode Toggle: Photo OCR vs Manual -->
      <div class="flex justify-center mb-4">
        <div class="inline-flex rounded-full border border-gray-200 bg-gray-100 p-0.5">
          <button type="button" @click="showOcrCapture = true"
            :class="['flex items-center gap-1.5 px-3 py-1.5 rounded-full text-sm font-medium transition', showOcrCapture ? 'bg-white text-indigo-700 shadow-sm' : 'text-gray-500 hover:text-gray-700']">
            <CameraIcon class="h-4 w-4" />Foto
          </button>
          <button type="button" @click="showOcrCapture = false"
            :class="['flex items-center gap-1.5 px-3 py-1.5 rounded-full text-sm font-medium transition', !showOcrCapture ? 'bg-white text-indigo-700 shadow-sm' : 'text-gray-500 hover:text-gray-700']">
            <PencilSquareIcon class="h-4 w-4" />Manuell
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
          />

          <button :key="shakeKey" type="submit" @click="haptic(20)"
            :disabled="!isFormValid"
            :class="['w-full bg-indigo-600 text-white p-3 rounded-md btn-3d transition', !isFormValid ? 'opacity-40 cursor-not-allowed' : 'hover:bg-indigo-700', error ? 'ring-2 ring-red-400 ring-offset-2 animate-shake' : '']">
            ⚡ Ladevorgang speichern
          </button>
          <p class="text-xs text-gray-400 text-center mt-2">
            📍 Der Standort hilft uns, die Außentemperatur beim Laden zu ermitteln — anonymisiert auf ~5km.
          </p>
          <ConsumptionInfoBox :min-trips="5" class="mt-4" />
        </form>

        <!-- Watt Toast -->
        <div v-if="showToast" class="fixed bottom-6 right-6 z-50 animate-slide-in">
          <div class="bg-green-600 text-white px-5 py-3 rounded-lg shadow-2xl flex items-center gap-2">
            <BoltIcon class="h-5 w-5 flex-shrink-0" />
            <span class="font-medium text-sm">{{ toastMessage }}</span>
          </div>
        </div>

        <!-- Last 5 logs -->
        <div class="mt-10">
          <h2 class="text-xl font-semibold mb-4 text-gray-800">Letzte 5 Ladevorgänge</h2>
          <div v-if="!selectedCarId" class="text-gray-500 text-center">Bitte wähle ein Fahrzeug aus um Ladevorgänge anzuzeigen.</div>
          <div v-else-if="logs.length === 0" class="text-gray-500 text-center">Noch keine Ladevorgänge für dieses Fahrzeug erfasst.</div>
          <ul v-else class="space-y-3">
            <li v-for="log in logs" :key="log.id" class="p-3 bg-gray-50 border border-gray-200 rounded-lg shadow-sm hover:shadow transition space-y-2">
              <div class="flex items-center justify-between gap-2">
                <div class="flex items-center gap-2 min-w-0">
                  <BoltIcon class="w-4 h-4 text-indigo-600 flex-shrink-0" />
                  <span class="font-semibold text-indigo-700 whitespace-nowrap">{{ log.kwhCharged }} kWh</span>
                  <span class="text-xs text-gray-400 whitespace-nowrap">{{ new Date(log.loggedAt).toLocaleDateString('de-DE') }}</span>
                </div>
                <div class="flex items-center gap-1.5 flex-shrink-0">
                  <span v-if="log.temperatureCelsius != null"
                    :class="['inline-flex items-center gap-0.5 px-2 py-0.5 border rounded-full text-xs whitespace-nowrap', tempBadgeClass(log.temperatureCelsius)]">
                    <SunIcon class="w-3 h-3" />{{ log.temperatureCelsius.toFixed(1) }}°C
                  </span>
                  <span class="hidden min-[475px]:inline-block px-2 py-0.5 bg-indigo-50 border border-indigo-200 text-xs rounded-full text-indigo-700 font-medium whitespace-nowrap">
                    €{{ (log.costEur / log.kwhCharged).toFixed(2) }}/kWh
                  </span>
                  <button type="button" @click="editingLog = log"
                    class="p-1 text-gray-400 hover:text-blue-500 hover:bg-blue-50 rounded transition" title="Ladevorgang bearbeiten">
                    <PencilSquareIcon class="w-4 h-4" />
                  </button>
                  <button type="button" @click="deleteLog(log.id)"
                    class="p-1 rounded transition"
                    :class="pendingDeleteId === log.id ? 'text-red-600 bg-red-50 ring-1 ring-red-300' : 'text-gray-400 hover:text-red-500 hover:bg-red-50'"
                    :title="pendingDeleteId === log.id ? 'Nochmal klicken zum Bestätigen' : 'Ladevorgang löschen'">
                    <TrashIcon class="w-4 h-4" />
                  </button>
                </div>
              </div>
              <div class="flex flex-wrap gap-1.5">
                <span class="inline-flex items-center gap-1 px-2 py-0.5 bg-white border border-gray-200 rounded-full text-xs text-gray-600 whitespace-nowrap">€{{ log.costEur }}</span>
                <span class="inline-flex min-[475px]:hidden items-center gap-1 px-2 py-0.5 bg-indigo-50 border border-indigo-200 rounded-full text-xs text-indigo-700 font-medium whitespace-nowrap">
                  €{{ (log.costEur / log.kwhCharged).toFixed(2) }}/kWh
                </span>
                <span v-if="log.chargeDurationMinutes" class="hidden min-[475px]:inline-flex items-center gap-1 px-2 py-0.5 bg-white border border-gray-200 rounded-full text-xs text-gray-600 whitespace-nowrap">
                  <ClockIcon class="w-3 h-3" />{{ log.chargeDurationMinutes }}min
                </span>
                <span v-if="log.odometerKm" class="hidden min-[475px]:inline-flex items-center gap-1 px-2 py-0.5 bg-white border border-gray-200 rounded-full text-xs text-gray-600 whitespace-nowrap">
                  <TruckIcon class="w-3 h-3" />{{ log.odometerKm.toLocaleString('de-DE') }} km
                </span>
                <span v-if="log.socAfterChargePercent !== null" class="inline-flex items-center gap-1 px-2 py-0.5 bg-white border border-gray-200 rounded-full text-xs text-gray-600 whitespace-nowrap">
                  <Battery0Icon class="w-3 h-3" />{{ log.socAfterChargePercent }}%
                </span>
                <span v-if="log.maxChargingPowerKw" class="inline-flex items-center gap-1 px-2 py-0.5 bg-white border border-gray-200 rounded-full text-xs text-gray-600 whitespace-nowrap">
                  <BoltIcon class="w-3 h-3" />{{ log.maxChargingPowerKw }} kW
                </span>
                <span v-if="log.chargingType && log.chargingType !== 'UNKNOWN'"
                  :class="['inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium whitespace-nowrap', log.chargingType === 'DC' ? 'bg-orange-50 border border-orange-200 text-orange-700' : 'bg-blue-50 border border-blue-200 text-blue-700']">
                  {{ log.chargingType }}
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
