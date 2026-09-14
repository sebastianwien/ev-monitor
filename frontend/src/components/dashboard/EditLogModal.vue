<template>
  <BottomSheet
    ref="sheet"
    :label="t('dashboard.edit_title')"
    testid="edit-log-modal"
    panel-class="sm:max-w-xl"
    @close="onClosed">
    <template #default="{ close }">
      <!-- Header: in einer Unteransicht fuehrt der Pfeil zurueck zur Uebersicht -->
      <div class="flex items-center gap-2 p-4 border-b border-gray-100 dark:border-gray-700 shrink-0">
        <button v-if="section" type="button" :aria-label="t('common.back')" @click="section = null"
          class="w-8 h-8 -ml-1 flex items-center justify-center rounded-sm text-gray-500 hover:bg-gray-100 dark:hover:bg-gray-700">
          <ChevronLeftIcon class="w-5 h-5" />
        </button>
        <h2 class="flex-1 text-lg font-semibold text-gray-900 dark:text-gray-100">
          {{ section ? t(sectionTitles[section]) : t('dashboard.edit_title') }}
        </h2>
        <button type="button" :aria-label="t('common.cancel')" @click="close" class="text-gray-400 dark:text-gray-500 hover:text-gray-600 dark:hover:text-gray-300">
          <XMarkIcon class="w-5 h-5" />
        </button>
      </div>

      <div class="flex-1 overflow-y-auto p-4 space-y-4">
        <!-- Uebersicht: was da ist, was fehlt, Optionales -->
        <template v-if="!section">
          <LogSummary v-model="formData" :place-label="placeLabel" :missing="missingAtOpen" show-time-tile @edit="s => section = s" />

          <div v-if="missingAtOpen.length" class="rounded-sm border border-amber-300 dark:border-amber-700 p-3 space-y-4" data-testid="edit-missing">
            <p class="text-sm font-semibold text-gray-800 dark:text-gray-100">{{ t('logwizard.still_open') }}</p>
            <div v-if="missingAtOpen.includes('energy')">
              <label for="wizard-kwh" class="text-[11px] uppercase tracking-wide text-gray-400">{{ t('logfields.energy') }}</label>
              <BigInput id="wizard-kwh" v-model="formData.kwhCharged" unit="kWh" :label="t('logfields.energy')" :placeholder="t('logfields.kwh_placeholder')" step="0.1" :min="0" />
            </div>
            <div v-if="missingAtOpen.includes('odometer')">
              <label for="wizard-odometer" class="text-[11px] uppercase tracking-wide text-gray-400">{{ t('logfields.odometer') }}</label>
              <BigInput id="wizard-odometer" v-model="odometerLocal" :unit="usesMiles ? t('logfields.unit_miles') : t('logfields.unit_km')" step="1" :min="0" inputmode="numeric" />
            </div>
            <div v-if="missingAtOpen.includes('soc')">
              <label for="wizard-soc" class="text-[11px] uppercase tracking-wide text-gray-400">{{ t('logfields.soc_after') }}</label>
              <BigInput id="wizard-soc" v-model="formData.socAfterChargePercent" unit="%" placeholder="80" step="1" :min="0" :max="100" inputmode="numeric" />
            </div>
            <div v-if="missingAtOpen.includes('cost')">
              <label for="wizard-cost" class="text-[11px] uppercase tracking-wide text-gray-400">{{ t('logfields.cost_eur') }}</label>
              <BigInput id="wizard-cost" v-model="cost.costLocalTotal.value" :unit="currencySymbol" :placeholder="t('logfields.cost_eur_placeholder')" step="0.01" :min="0" />
            </div>
          </div>
        </template>

        <!-- Unteransichten: dieselben Schritte wie beim Anlegen -->
        <StepPlace v-else-if="section === 'place'" :place="place" :selected-cpo="formData.cpoName" :selected-site="formData.chargingSite"
          :stations="[]" :stations-loading="false" permission="unavailable" location-status="idle"
          :recent-cpos="[]" :recent-sites="recentSites.sites.value" :all-cpos="cpo.allCpos.value" @choose="choosePlace">
        </StepPlace>
        <StepEnergy v-else-if="section === 'energy'" v-model="formData" @ocr="onOcr" />
        <StepVehicle v-else-if="section === 'vehicle'" v-model="formData" :last-odometer-km="null" :effective-capacity-kwh="null" />
        <StepCost v-else-if="section === 'cost'" v-model="formData" v-model:providers="providers" :cost="cost" />
        <div v-else-if="section === 'time'">
          <label for="wizard-time" class="block text-xs text-gray-500 dark:text-gray-400 mb-1">{{ t('logfields.timestamp') }}</label>
          <input id="wizard-time" v-model="formData.loggedAt" type="datetime-local"
            class="w-full rounded-sm border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 p-2 text-sm" />
        </div>

        <!-- Standort aendern: nur im Ort-Editor, ohne Live-Position (es gibt nur den Geohash) -->
        <div v-if="section === 'place'" class="pt-2">
          <PlaceSearch :label="t('logfields.update_location')" :placeholder="t('logfields.location_search_placeholder')" @picked="onPlacePicked" />
          <p v-if="formData.latitude == null && log.geohash" class="text-xs text-gray-400 dark:text-gray-500 mt-1">{{ t('logfields.current_location', { geohash: log.geohash }) }}</p>
        </div>

        <p v-if="errorMsg" class="text-sm text-red-600 bg-red-50 dark:bg-red-900/30 rounded-sm p-3">{{ errorMsg }}</p>
      </div>

      <!-- Footer: in der Unteransicht "Fertig" (zurueck zur Uebersicht), sonst Speichern -->
      <div class="flex items-center gap-3 p-4 border-t border-gray-100 dark:border-gray-700 shrink-0">
        <button v-if="section" type="button" data-testid="edit-done" @click="section = null" v-haptic
          class="flex-1 bg-indigo-600 text-white p-3 rounded-sm btn-3d font-semibold hover:bg-indigo-700">
          {{ t('logwizard.done') }}
        </button>
        <template v-else>
          <button type="button" @click="close" v-haptic class="px-3 py-3 text-sm font-medium text-gray-500 dark:text-gray-400">
            {{ t('common.cancel') }}
          </button>
          <button type="button" @click="save" v-haptic :disabled="loading || !isFormValid"
            class="flex-1 bg-indigo-600 text-white p-3 rounded-sm btn-3d font-semibold hover:bg-indigo-700 disabled:opacity-40 disabled:cursor-not-allowed flex items-center justify-center gap-2">
            <span v-if="loading" class="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
            {{ t('logfields.save') }}
          </button>
        </template>
      </div>
    </template>
  </BottomSheet>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { XMarkIcon, ChevronLeftIcon } from '@heroicons/vue/24/outline'
import { useI18n } from 'vue-i18n'
import BottomSheet from '../shared/BottomSheet.vue'
import api from '../../api/axios'
import type { LogFormData } from '../log-form/logFormData'
import type { ChargingProvider } from '../../composables/useChargingProviders'
import { applyTariffToLocationIfRequested } from '../../utils/applyTariffToLocation'
import { useCountryStore } from '../../stores/country'
import { useCpoOptions } from '../../composables/useCpoOptions'
import { useCostInput } from '../../composables/useCostInput'
import { EUR_ZONE_COUNTRIES } from '../../config/unitSystems'
import { odometerKmToLocal, odometerLocalToKm } from '../../utils/unitConversions'
import { EUR_EXCHANGE_RATES } from '../../config/exchangeRates'
import { buildLogUpdatePayload, missingRequired, applyPlace, type PlaceChoice, type PlaceKind, type RequiredField } from '../log-wizard/wizardLogic'
import LogSummary, { type SummarySection } from '../log-wizard/LogSummary.vue'
import BigInput from '../log-wizard/BigInput.vue'
import StepPlace from '../log-wizard/StepPlace.vue'
import { useRecentSites } from '../../composables/useRecentSites'
import StepEnergy from '../log-wizard/StepEnergy.vue'
import StepVehicle from '../log-wizard/StepVehicle.vue'
import StepCost from '../log-wizard/StepCost.vue'
import PlaceSearch from '../log-wizard/PlaceSearch.vue'

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
  dataSource?: string | null
}

const props = defineProps<{ log: EvLogResponse }>()
const emit = defineEmits<{ close: []; saved: [log: EvLogResponse] }>()
const { t } = useI18n()
const countryStore = useCountryStore()

// Das Sheet faehrt erst aus, dann meldet es sich - der Aufrufer entfernt uns daraufhin
// per v-if, was eine noch laufende Animation abschneiden wuerde. Ein erfolgreicher
// Speichervorgang parkt hier sein Ergebnis, bis das Sheet draussen ist.
const sheet = ref<InstanceType<typeof BottomSheet> | null>(null)
const savedLog = ref<EvLogResponse | null>(null)
function onClosed() {
  if (savedLog.value) emit('saved', savedLog.value)
  else emit('close')
}

const toDatetimeLocal = (iso: string): string => {
  const d = new Date(iso)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

const formData = ref<LogFormData>({
  kwhCharged: props.log.kwhCharged,
  costEur: props.log.costEur ?? null,
  costExchangeRate: props.log.costExchangeRate ?? null,
  costCurrency: props.log.costCurrency ?? null,
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
  // lat/lon werden nie gespeichert - beim Bearbeiten eines (importierten) Logs ist der
  // Geohash der einzige Ort, den wir haben. Er traegt die "Tarif auf alle hier"-Abfrage.
  geohash: props.log.geohash ?? null,
  isPublicCharging: props.log.isPublicCharging ?? false,
  cpoName: props.log.cpoName ?? null,
  chargingSite: null,
  chargingProviderId: props.log.chargingProviderId ?? null,
  applyTariffToLocation: false,
})

// Bearbeiten heisst meist "eins aendern": Uebersicht zuerst, Editor je Abschnitt darunter.
const section = ref<SummarySection | null>(null)
const sectionTitles: Record<SummarySection, string> = {
  place: 'logwizard.q_place', energy: 'logwizard.q_energy', vehicle: 'logwizard.q_vehicle', cost: 'logwizard.q_cost', time: 'logfields.timestamp',
}
// Einmal beim Oeffnen bestimmt: der Block "Noch offen" soll nicht unter den Fingern verschwinden.
const missingAtOpen = ref<RequiredField[]>(missingRequired(formData.value))

const cost = useCostInput(formData, {
  isEurCountry: computed(() => EUR_ZONE_COUNTRIES.includes(countryStore.country)),
  exchangeRate: computed(() => EUR_EXCHANGE_RATES[countryStore.unitSystem.currency]),
  localCurrency: computed(() => countryStore.unitSystem.currency),
})
cost.initFromEur()
const currencySymbol = computed(() => countryStore.unitSystem.currencySymbol)
const usesMiles = computed(() => countryStore.unitSystem.distanceUnit === 'miles')
const odometerLocal = computed({
  get: () => formData.value.odometerKm == null ? null : odometerKmToLocal(formData.value.odometerKm, usesMiles.value),
  set: (v) => { formData.value.odometerKm = v == null ? null : odometerLocalToKm(v, usesMiles.value) },
})

const cpo = useCpoOptions(computed(() => countryStore.country))
const recentSites = useRecentSites()
const providers = ref<ChargingProvider[]>([])
onMounted(() => {
  recentSites.load()
  cpo.loadAll().then(() => cpo.keepSelected(formData.value.cpoName))
  api.get<ChargingProvider[]>('/users/me/charging-providers').then(r => { providers.value = r.data }).catch(() => {})
})

const place = computed<PlaceKind | null>(() => !formData.value.isPublicCharging ? 'home' : formData.value.chargingSite ? 'site' : 'other')
const placeLabel = computed(() => formData.value.isPublicCharging
  ? (formData.value.cpoName ?? t('logwizard.place_other'))
  : t('logwizard.place_home'))
const choosePlace = (choice: PlaceChoice) => { applyPlace(formData.value, choice) }

const onOcr = (r: any) => {
  if (r.kwh != null) { formData.value.kwhCharged = r.kwh; formData.value.kwhAtVehicle = null }
  if (r.cost != null) { cost.costMode.value = 'total'; cost.costLocalTotal.value = r.cost }
  if (r.durationMinutes != null) formData.value.chargeDurationMinutes = r.durationMinutes
  if (r.maxChargingPowerKw != null) formData.value.maxChargingPowerKw = r.maxChargingPowerKw
}

const loading = ref(false)
const errorMsg = ref('')
const isFormValid = computed(() => {
  const f = formData.value
  const hasEnergy = (f.kwhCharged != null && f.kwhCharged > 0) || (f.kwhAtVehicle != null && f.kwhAtVehicle > 0)
  return hasEnergy && f.costEur != null
})

const onPlacePicked = (p: { latitude: number; longitude: number }) => {
  formData.value.latitude = p.latitude
  formData.value.longitude = p.longitude
}

async function save() {
  errorMsg.value = ''
  if (!isFormValid.value) {
    const fields = [!((formData.value.kwhCharged ?? 0) > 0 || (formData.value.kwhAtVehicle ?? 0) > 0) && t('logform.field_kwh'),
                    formData.value.costEur == null && t('logform.field_cost')].filter(Boolean)
    errorMsg.value = t('logform.error_required', { fields: fields.join(', ') })
    return
  }
  loading.value = true
  try {
    const res = await api.patch(`/logs/${props.log.id}`, buildLogUpdatePayload(formData.value))
    await applyTariffToLocationIfRequested(formData.value)
    savedLog.value = res.data
    sheet.value?.requestClose()
  } catch (e: any) {
    errorMsg.value = e?.response?.data?.message ?? t('logform.error_save')
  } finally {
    loading.value = false
  }
}
</script>
