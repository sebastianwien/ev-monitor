<template>
  <div class="flex min-h-0 flex-1 flex-col bg-white dark:bg-gray-900">
    <!-- Header: in einer Unteransicht fuehrt der Pfeil zurueck zur Uebersicht -->
    <div class="flex items-center gap-1 px-4 py-2 border-b-2 border-gray-100 dark:border-gray-800 shrink-0">
      <button v-if="section || dismiss === 'back'" type="button" :aria-label="t('common.back')" data-testid="edit-back"
        @click="section ? section = null : emit('cancel')"
        class="-ml-2 grid h-11 w-11 shrink-0 place-items-center rounded-full text-gray-600 hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-white/10">
        <ChevronLeftIcon class="w-5 h-5" />
      </button>
      <h2 class="flex-1 min-w-0 truncate text-[17px] font-bold text-gray-900 dark:text-gray-50">
        {{ section ? t(sectionTitles[section]) : t('dashboard.edit_title') }}
      </h2>
      <button v-if="dismiss === 'close'" type="button" :aria-label="t('common.cancel')" @click="emit('cancel')" class="-mr-2 grid h-11 w-11 shrink-0 place-items-center rounded-full text-gray-600 hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-white/10">
        <XMarkIcon class="w-5 h-5" />
      </button>
    </div>

    <div class="flex-1 overflow-y-auto p-4 space-y-4">
      <!-- Uebersicht: was da ist, was fehlt, Optionales -->
      <template v-if="!section">
        <!-- Erst die Ladung sehen: der Ort als Kartenkopf wie auf der Prüfseite des Wizards, ein Tipp darauf bearbeitet ihn -->
        <PlaceHeader :context="placeContext" :height="PLACE_MAP_PX" clickable class="-mt-4" data-testid="summary-place" @click="section = 'place'" />
        <!-- Ergänzungen des Rahmens zur Ladung, z. B. Ladekurve und "Korrekt erkannt?" im Logbuch -->
        <slot name="extra" />
        <!-- Sprache: nur Handy, Knopf in der Fußleiste neben Speichern; danach Vorher/Nachher mit Rückgängig hier oben -->
        <VoiceCapture v-if="showVoice && !confirmingDelete" entry="edit" variant="footer" :host="footerId" :car-id="log.carId" @draft="onVoiceDraft" />
        <div v-if="voiceChanges" class="edge-box rounded-xl bg-white dark:bg-gray-700/50 p-3 space-y-2" data-testid="edit-voice-changes" aria-live="polite">
          <p v-if="voiceNothing" class="text-sm text-gray-700 dark:text-gray-200">{{ t('voicelog.edit_nothing') }}</p>
          <template v-if="voiceChanges.length">
            <div class="flex items-center justify-between gap-2">
              <p class="text-[14px] font-semibold text-gray-900 dark:text-gray-50">{{ t('voicelog.edit_changed') }}</p>
              <button type="button" data-testid="edit-voice-undo" @click="undoVoice" class="min-h-11 rounded-full px-3 text-[14px] font-semibold text-gray-900 underline underline-offset-2 hover:bg-gray-100 dark:text-gray-100 dark:hover:bg-white/10">{{ t('voicelog.edit_undo') }}</button>
            </div>
            <ul class="space-y-1 text-sm">
              <li v-for="c in voiceChanges" :key="c.key" :data-testid="`edit-voice-change-${c.key}`" class="flex flex-wrap gap-x-2 text-gray-700 dark:text-gray-200">
                <span class="text-gray-500 dark:text-gray-400">{{ changeLabel(c.key) }}</span>
                <span class="tabular-nums"><s class="text-gray-400">{{ changeValue(c.key, c.from) }}</s> → <b class="font-semibold">{{ changeValue(c.key, c.to) }}</b></span>
              </li>
            </ul>
          </template>
        </div>
        <LogSummary v-model="formData" :place-label="placeLabel" :missing="missingAtOpen" show-time-tile hide-place @edit="s => section = s" />

        <div v-if="missingAtOpen.length" class="rounded-xl bg-amber-50 dark:bg-amber-400/10 p-3 space-y-4" data-testid="edit-missing">
          <p class="text-[14px] font-semibold text-amber-900 dark:text-amber-200">{{ t('logwizard.still_open') }}</p>
          <div v-if="missingAtOpen.includes('energy')">
            <label for="wizard-kwh" class="text-[12px] font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400">{{ t('logfields.energy') }}</label>
            <BigInput id="wizard-kwh" v-model="formData.kwhCharged" unit="kWh" :label="t('logfields.energy')" :placeholder="t('logfields.kwh_placeholder')" step="0.1" :min="0" />
          </div>
          <div v-if="missingAtOpen.includes('odometer')">
            <label for="wizard-odometer" class="text-[12px] font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400">{{ t('logfields.odometer') }}</label>
            <BigInput id="wizard-odometer" v-model="odometerLocal" :unit="usesMiles ? t('logfields.unit_miles') : t('logfields.unit_km')" step="1" :min="0" inputmode="numeric" />
          </div>
          <div v-if="missingAtOpen.includes('soc')">
            <label for="wizard-soc" class="text-[12px] font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400">{{ t('logfields.soc_after') }}</label>
            <BigInput id="wizard-soc" v-model="formData.socAfterChargePercent" unit="%" placeholder="80" step="1" :min="0" :max="100" inputmode="numeric" />
          </div>
          <div v-if="missingAtOpen.includes('cost')">
            <label for="wizard-cost" class="text-[12px] font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400">{{ t('logfields.cost_eur') }}</label>
            <BigInput id="wizard-cost" v-model="cost.costLocalTotal.value" :unit="currencySymbol" :placeholder="t('logfields.cost_eur_placeholder')" step="0.01" :min="0" />
          </div>
        </div>
      </template>

      <!-- Unteransichten: dieselben Schritte wie beim Anlegen -->
      <template v-else-if="section === 'place'">
      <StepPlace :place="place" :selected-cpo="formData.cpoName" :selected-site="formData.chargingSite"
        :stations="nearby.stations.value" :stations-loading="nearby.loading.value" permission="unavailable" :location-status="locationStatus"
        :radius-meters="nearby.radius.value" :can-expand="nearby.canExpand.value" :next-radius="nearby.nextRadius.value" :exhausted="nearby.exhausted.value"
        :latitude="formData.latitude" :longitude="formData.longitude" :stored-place="!!log.geohash"
        :address-label="pickedAddress" @choose="choosePlace" @place-picked="onPlacePicked"
        @expand-radius="nearby.expand()">
      </StepPlace>
      <p class="text-[13px] text-gray-500 dark:text-gray-400" data-testid="edit-place-precision">{{ t('logwizard.place_precision_note') }}</p>
      </template>
      <StepEnergy v-else-if="section === 'energy'" v-model="formData" @ocr="onOcr" />
      <StepVehicle v-else-if="section === 'vehicle'" v-model="formData" :last-odometer-km="null" :effective-capacity-kwh="null" />
      <StepCost v-else-if="section === 'cost'" v-model="formData" v-model:providers="providers" :cost="cost" />
      <div v-else-if="section === 'time'">
        <label for="wizard-time" class="mb-1 block text-[12px] font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400">{{ t('logfields.timestamp') }}</label>
        <input id="wizard-time" v-model="formData.loggedAt" type="datetime-local"
          class="min-h-12 w-full min-w-0 rounded-xl border-2 border-edge bg-white px-3 text-[17px] font-semibold tabular-nums outline-none focus:border-green-600 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-100" />
      </div>

      <!-- Standort aendern: nur im Ort-Editor, ohne Live-Position (es gibt nur den Geohash) -->
      <div v-if="section === 'place'" class="pt-2">
        <p v-if="formData.latitude == null && log.geohash" class="text-xs text-gray-500 dark:text-gray-400 mt-1">{{ t('logfields.current_location', { geohash: log.geohash }) }}</p>
      </div>

      <p v-if="errorMsg" role="alert" class="rounded-xl bg-amber-50 px-3 py-2 text-[14px] text-amber-900 dark:bg-amber-400/10 dark:text-amber-200">{{ errorMsg }}</p>
    </div>

    <!-- Footer: in der Unteransicht "Fertig" (zurueck zur Uebersicht), sonst Speichern. Die Ziele fuer die Spracheingabe
         (Sprechblase darueber, Mikrofon links neben Speichern) bleiben immer im DOM, sonst verliert das Teleport sein Ziel. -->
    <div class="px-4 py-3 border-t-2 border-gray-100 dark:border-gray-800 shrink-0">
      <div :id="`${footerId}-footer-note`" />
      <div class="flex items-center gap-2">
      <button v-if="section" type="button" data-testid="edit-done" @click="section = null" v-haptic
        class="flex-1 btn-3d min-h-12 rounded-xl border-2 border-edge bg-green-600 px-3 text-[15px] font-semibold text-white hover:bg-green-700">
        {{ t('logwizard.done') }}
      </button>
      <!-- Loeschen: zweistufig im Sheet statt window.confirm (in der App unzuverlaessig) -->
      <template v-else-if="confirmingDelete">
        <span class="flex-1 text-[14px] font-semibold text-gray-900 dark:text-gray-100">{{ t('logform.delete_confirm') }}</span>
        <button type="button" data-testid="edit-delete-cancel" @click="confirmingDelete = false" v-haptic
          class="min-h-12 rounded-full px-3 text-[15px] font-semibold text-gray-600 hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-white/10">
          {{ t('common.cancel') }}
        </button>
        <button type="button" data-testid="edit-delete-confirm" @click="deleteLog" v-haptic :disabled="loading"
          class="btn-3d min-h-12 rounded-xl border-2 border-edge bg-red-600 px-4 text-[15px] font-semibold text-white hover:bg-red-700 disabled:opacity-40 flex items-center gap-2">
          <span v-if="loading" class="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
          {{ t('dashboard.action_delete') }}
        </button>
      </template>
      <template v-else>
        <button type="button" data-testid="edit-delete" :aria-label="t('logform.delete_title')" @click="confirmingDelete = true" v-haptic
          class="-ml-1 grid h-12 w-12 shrink-0 place-items-center rounded-full text-gray-600 hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-white/10">
          <TrashIcon class="w-5 h-5" />
        </button>
        <!-- Abbrechen erst ab 640 px: auf dem Handy fuehrt der Pfeil oder das X oben zurueck, Speichern bekommt die Breite -->
        <button type="button" @click="emit('cancel')" v-haptic class="hidden sm:inline-flex sm:items-center min-h-12 rounded-full px-3 text-[15px] font-semibold text-gray-600 hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-white/10">
          {{ t('common.cancel') }}
        </button>
      </template>
      <div :id="`${footerId}-footer-lead`" class="contents" />
      <button v-if="!section && !confirmingDelete" type="button" data-testid="edit-save" @click="save" v-haptic :disabled="loading || !isFormValid"
        class="flex-1 btn-3d min-h-12 rounded-xl border-2 border-edge bg-green-600 px-3 text-[15px] font-semibold text-white hover:bg-green-700 disabled:opacity-40 disabled:cursor-not-allowed flex items-center justify-center gap-2">
        <span v-if="loading" class="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
        {{ t('logfields.save') }}
      </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, nextTick, watch, defineAsyncComponent, useId } from 'vue'
import type { PickedPlace } from '../../composables/useLocationSearch'
import { XMarkIcon, ChevronLeftIcon, TrashIcon } from '@heroicons/vue/24/outline'
import { useI18n } from 'vue-i18n'
import api from '../../api/axios'
import { useLogUndo } from '../../composables/useLogUndo'
import type { LogFormData } from '../log-form/logFormData'
import type { ChargingProvider } from '../../composables/useChargingProviders'
import { applyTariffToLocationIfRequested } from '../../utils/applyTariffToLocation'
import { useCountryStore } from '../../stores/country'
import { useCostInput } from '../../composables/useCostInput'
import { EUR_ZONE_COUNTRIES } from '../../config/unitSystems'
import { odometerKmToLocal, odometerLocalToKm } from '../../utils/unitConversions'
import { EUR_EXCHANGE_RATES } from '../../config/exchangeRates'
import { buildLogUpdatePayload, missingRequired, applyPlace, type PlaceChoice, type PlaceKind, type RequiredField } from '../log-wizard/wizardLogic'
import LogSummary, { type SummarySection } from '../log-wizard/LogSummary.vue'
import PlaceHeader, { type NumbersContext } from '../log-wizard/PlaceHeader.vue'
import { privateArea } from '../log-wizard/minimapTiles'
import BigInput from '../log-wizard/BigInput.vue'
import StepPlace from '../log-wizard/StepPlace.vue'
import { useNearbyStations } from '../../composables/useNearbyStations'
import StepEnergy from '../log-wizard/StepEnergy.vue'
import StepVehicle from '../log-wizard/StepVehicle.vue'
import StepCost from '../log-wizard/StepCost.vue'
import { logSaveErrorMessage } from '../../utils/logSaveError'
import { useVoiceQuota } from '../log-wizard/useVoiceQuota'
import { isVoiceSupported } from '../../composables/useVoiceRecorder'
import { useIsMobile } from '../../composables/useIsMobile'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import { analytics } from '../../services/analytics'
import { applyVoiceDraft, voiceCost, readSuggestion, type VoiceDraft } from '../log-wizard/wizardLogic'
import { spokenSnapshot, correctedFields, type SpokenSnapshot } from '../log-wizard/voiceAnalytics'
import { resolveSpokenAddress, placeFromAddress } from '../log-wizard/voiceAddress'
import geohashLib from 'ngeohash'
import { formChanges, undoVoiceChanges, type FieldChange, type EditDiffKey } from '../log-wizard/voiceEdit'
const VoiceCapture = defineAsyncComponent(() => import('../log-wizard/VoiceCapture.vue'))

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

/**
 * Ladung bearbeiten ohne Rahmen: Kopf, Übersicht, Unteransichten, Spracheingabe, Fuß.
 * Der Rahmen (EditLogModal als Sheet, Tagesblatt als zweite Ebene) entscheidet, was nach
 * Speichern, Löschen und Abbrechen passiert.
 */
const props = withDefaults(defineProps<{
  log: EvLogResponse
  /** Direkt in einer Unteransicht öffnen, z. B. "place" für "Betreiber eintragen" auf der Ladekarte. */
  startSection?: SummarySection
  /** Kopf oben: Schließen-Kreuz (eigenes Sheet) oder Zurück-Pfeil (Ebene in einem anderen Sheet). */
  dismiss?: 'close' | 'back'
}>(), { dismiss: 'close' })
const emit = defineEmits<{ cancel: []; saved: [log: EvLogResponse]; deleted: [logId: string] }>()
const { t } = useI18n()
const countryStore = useCountryStore()

const confirmingDelete = ref(false)
/** Ziele der Spracheingabe in der Fußleiste, eindeutig je Panel */
const footerId = useId()
const { deleteWithUndo } = useLogUndo()
async function deleteLog() {
  errorMsg.value = ''
  loading.value = true
  try {
    await deleteWithUndo(props.log.id)
    emit('deleted', props.log.id)
  } catch {
    confirmingDelete.value = false
    errorMsg.value = t('logform.delete_failed')
  } finally {
    loading.value = false
  }
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
  routeType: props.log.routeType ?? null,
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
const section = ref<SummarySection | null>(props.startSection ?? null)
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

// Säulen im Umkreis einer neu gewählten Adresse - wie im Wizard
const nearby = useNearbyStations()
const locationStatus = ref<'idle' | 'success'>('idle')
const providers = ref<ChargingProvider[]>([])
onMounted(() => {
  api.get<ChargingProvider[]>('/users/me/charging-providers').then(r => { providers.value = r.data }).catch(() => {})
  if (props.startSection === 'place') loadStationsAtStoredPlace()
})

/**
 * Säulen rund um den gespeicherten Ort, sobald der Ort-Editor aufgeht - sonst müsste der Nutzer den Ort
 * erst suchen. Es gibt nur den Geohash, also seine Zellmitte; bei einer Heimladung ist das das 6er-Feld.
 */
function loadStationsAtStoredPlace() {
  if (!props.log.geohash || formData.value.latitude != null || nearby.stations.value.length || nearby.loading.value) return
  const { latitude, longitude } = geohashLib.decode(props.log.geohash)
  void nearby.load(latitude, longitude)
}
watch(section, s => { if (s === 'place') loadStationsAtStoredPlace() })

const place = computed<PlaceKind | null>(() => !formData.value.isPublicCharging ? 'home' : formData.value.chargingSite ? 'site' : 'other')
// Per Suche gewählte Adresse: muss nach "Fertig" in der Übersicht stehen, sonst wirkt sie verworfen
const pickedName = ref<string | null>(null)
// Die gewählte Adresse selbst: Untertitel von "Hier privat", auch wenn danach eine Säule gewählt wurde
const pickedAddress = ref<string | null>(null)
const placeLabel = computed(() => pickedName.value ?? (formData.value.isPublicCharging
  ? (formData.value.cpoName ?? t('logwizard.place_public'))
  : t('logwizard.place_private')))
const PLACE_MAP_PX = 160
/**
 * Kopf der Übersicht: Ort, Ladekarte und Minimap. Mittelpunkt ist ein neu gewählter Ort, sonst die gespeicherte
 * Zelle (öffentlich rund 150 m); privat die Zelle als Fläche, nie ein Punkt darin. Ohne beides nur die Textzeile.
 */
const placeContext = computed<NumbersContext>(() => {
  const card = providers.value.find(p => p.id === formData.value.chargingProviderId)
  const base = { title: placeLabel.value, address: null, card: card ? (card.label || card.providerName) : null }
  const cell = props.log.geohash ? geohashLib.decode(props.log.geohash) : null
  const lat = formData.value.latitude ?? cell?.latitude ?? null
  const lon = formData.value.longitude ?? cell?.longitude ?? null
  if (lat == null || lon == null) return { ...base, lat: null, lon: null }
  if (!formData.value.isPublicCharging) { const area = privateArea(lat, lon); return { ...base, lat: area.lat, lon: area.lon, area } }
  return { ...base, lat, lon }
})
/**
 * Ortswechsel beim Bearbeiten: die Ladeart der Ladung bleibt (das Register kennt nur die Stecker, nicht
 * diese Ladung; gemessene Werte stehen schon im Log). Karte und Preis kommen aus der Historie am neuen Ort.
 */
const applyChoice = (choice: PlaceChoice) => {
  if (choice.kind === 'station' || choice.kind === 'site') pickedName.value = null
  const chargingType = formData.value.chargingType
  applyPlace(formData.value, choice)
  formData.value.chargingType = chargingType
  dropHomePriceIfPublic()
}
/** Von privat auf öffentlich: der Heimtarif gilt an der Säule nicht. Kosten und Karte neu, solange sie noch die alten sind. */
function dropHomePriceIfPublic() {
  const f = formData.value
  if (props.log.isPublicCharging !== false || !f.isPublicCharging) return
  if (f.costEur === (props.log.costEur ?? null)) { cost.reset(); f.costEur = null }
  if (f.chargingProviderId === (props.log.chargingProviderId ?? null)) f.chargingProviderId = null
}
const choosePlace = (choice: PlaceChoice) => { applyChoice(choice); void fillFromHistory() }

/** Füllt nur Leeres: eine vorhandene Karte oder ein vorhandener Preis wird nie überschrieben. */
async function fillFromHistory() {
  const f = formData.value
  const geohash = f.chargingSite?.geohash ?? (f.latitude == null ? props.log.geohash : null)
  const where = geohash ? { geohash } : f.latitude != null && f.longitude != null ? { lat: f.latitude, lon: f.longitude } : null
  if (!where) return
  try {
    const res = await api.get('/logs/price-suggestion', { params: { ...where, isPublic: !!f.isPublicCharging, chargingType: f.chargingType } })
    const s = readSuggestion(res.data)
    const card = s.cardId ? providers.value.find(p => p.id === s.cardId && (!f.isPublicCharging || !p.isPrivate)) : null
    if (!f.chargingProviderId && card) f.chargingProviderId = card.id
    if (f.costEur == null && s.community) cost.setPerKwhEur(s.community.eurPerKwh)
  } catch { /* kein Vorschlag - kein Problem */ }
}

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

const onPlacePicked = async (p: PickedPlace) => {
  pickedName.value = p.name
  pickedAddress.value = p.name
  formData.value.latitude = p.latitude
  formData.value.longitude = p.longitude
  locationStatus.value = 'success'
  await nearby.load(p.latitude, p.longitude)
}

// ── Sprache ───────────────────────────────────────────────────────────────────
/** Handy und Tablet (unter 1024 px); am Desktop ist Tippen schneller. */
const isMobile = useIsMobile('(max-width: 1023px)')
/** Freigabe wie im Wizard über den Kontingent-Stand. */
const voiceQuota = useVoiceQuota()
if (isMobile.value && isVoiceSupported()) void voiceQuota.load()
const showVoice = computed(() => voiceQuota.available.value && isMobile.value && isVoiceSupported())
/** Alle Änderungen seit der ersten Aufnahme; null = noch keine Aufnahme */
const voiceChanges = ref<FieldChange[] | null>(null)
/** Die letzte Aufnahme hat nichts geändert */
const voiceNothing = ref(false)
let beforeVoice: { form: LogFormData; pickedName: string | null; pickedAddress: string | null } | null = null
/** Stand direkt nach der letzten Aufnahme: was danach abweicht, hat der Nutzer selbst geändert */
let afterVoice: LogFormData | null = null
const spoken = ref<SpokenSnapshot>({})

/** Nur Gesagtes überschreibt; Adresse wie die Adresssuche im Ort-Editor. Vorher-Stand bleibt fürs Rückgängig. */
async function onVoiceDraft(draft: VoiceDraft) {
  const before = cloneForm()
  if (!beforeVoice) beforeVoice = { form: before, pickedName: pickedName.value, pickedAddress: pickedAddress.value }
  const viaAddress = placeFromAddress(draft)
  const keptType = formData.value.chargingType
  const choice = applyVoiceDraft(formData.value, viaAddress ? { ...draft, place: null } : draft)
  if (!draft.fields.chargingType) formData.value.chargingType = keptType
  dropHomePriceIfPublic()
  if (choice && (choice.kind === 'station' || choice.kind === 'site')) pickedName.value = null
  if (viaAddress) {
    // Bekannte Zelle des Logs als Umgebung für die Suche (es gibt keine Live-Position)
    const cell = props.log.geohash ? geohashLib.decode(props.log.geohash) : null
    const r = await resolveSpokenAddress(draft, {
      near: cell ? { lat: cell.latitude, lon: cell.longitude } : null,
      loadStations: async (lat, lon) => { await onPlacePicked({ latitude: lat, longitude: lon, name: '' }); return nearby.stations.value },
    })
    if (r) {
      pickedName.value = r.hit.name
      pickedAddress.value = r.hit.name
      applyChoice(r.choice)
      if (draft.fields.chargingType) formData.value.chargingType = draft.fields.chargingType
    } else {
      errorMsg.value = t('voicelog.address_not_found', { q: draft.fields.spokenAddress })
    }
  }
  if (choice || viaAddress) await fillFromHistory()
  const said = voiceCost(draft.fields)
  if (said?.mode === 'per_kwh') cost.setPerKwhEur(said.eur)
  else if (said) { cost.costMode.value = 'total'; cost.costLocalPerKwh.value = null; cost.costLocalTotal.value = Math.round(cost.eurToLocal(said.eur) * 100) / 100 }
  await nextTick() // Kosten-Abgleich schreibt costEur im nächsten Tick
  spoken.value = { ...spoken.value, ...spokenSnapshot(formData.value, draft.fields) }
  afterVoice = cloneForm()
  voiceNothing.value = formChanges(before, formData.value).length === 0
  voiceChanges.value = formChanges(beforeVoice.form, formData.value)
}
/** Reiner Datenstand ohne Vue-Proxy (structuredClone scheitert an reaktiven Objekten) */
const cloneForm = (): LogFormData => JSON.parse(JSON.stringify(formData.value))

/** Nimmt nur zurück, was die Sprache gesetzt hat; spätere Handänderungen bleiben. */
function undoVoice() {
  if (!beforeVoice || !afterVoice) return
  const placeKept = JSON.stringify([formData.value.isPublicCharging, formData.value.cpoName, formData.value.latitude]) !== JSON.stringify([afterVoice.isPublicCharging, afterVoice.cpoName, afterVoice.latitude])
  formData.value = undoVoiceChanges(beforeVoice.form, afterVoice, cloneForm())
  if (!placeKept) { pickedName.value = beforeVoice.pickedName; pickedAddress.value = beforeVoice.pickedAddress }
  cost.initFromEur()
  beforeVoice = null
  afterVoice = null
  voiceChanges.value = null
  voiceNothing.value = false
  spoken.value = {}
}

const { formatNumber, formatDistance, formatCurrency } = useLocaleFormat()
const CHANGE_LABEL: Record<EditDiffKey, string> = {
  place: 'logwizard.place', kwhCharged: 'logfields.energy', kwhAtVehicle: 'logfields.kwh_at_vehicle', socBeforeChargePercent: 'logfields.soc_before',
  socAfterChargePercent: 'logfields.soc_after', odometerKm: 'logfields.odometer', costEur: 'logfields.cost_eur', loggedAt: 'logfields.timestamp',
  chargeDurationMinutes: 'logfields.duration', maxChargingPowerKw: 'logfields.max_power', chargingType: 'voicelog.chip_type',
  chargingProviderId: 'voicelog.edit_card',
  routeType: 'logfields.route_type_label', tireType: 'logfields.tire_type_label',
}
const ENUM_LABEL: Record<string, string> = {
  CITY: 'logfields.route_city', COMBINED: 'logfields.route_mix', HIGHWAY: 'logfields.route_highway',
  SUMMER: 'logfields.tire_summer', ALL_YEAR: 'logfields.tire_allyear', WINTER: 'logfields.tire_winter',
}
const changeLabel = (k: EditDiffKey) => t(CHANGE_LABEL[k])
function changeValue(k: EditDiffKey, v: unknown): string {
  if (k === 'place') {
    const p = v as { isPublic: boolean; name: string | null }
    return p.isPublic ? (p.name ?? t('logwizard.place_public')) : t('logwizard.place_private')
  }
  if (v == null || v === '') return '–'
  switch (k) {
    case 'kwhCharged': case 'kwhAtVehicle': return `${formatNumber(v as number)} kWh`
    case 'socBeforeChargePercent': case 'socAfterChargePercent': return `${v} %`
    case 'odometerKm': return formatDistance(v as number)
    case 'costEur': return formatCurrency(v as number)
    case 'chargeDurationMinutes': return `${v} min`
    case 'maxChargingPowerKw': return `${formatNumber(v as number)} kW`
    case 'loggedAt': return new Date(v as string).toLocaleString(undefined, { day: 'numeric', month: 'numeric', hour: '2-digit', minute: '2-digit' })
    case 'chargingProviderId': { const p = providers.value.find(x => x.id === v); return p ? (p.label || p.providerName) : '–' }
    case 'routeType': case 'tireType': return t(ENUM_LABEL[v as string] ?? String(v))
    default: return String(v)
  }
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
    if (beforeVoice) {
      const fields: string[] = correctedFields(spoken.value, formData.value)
      analytics.trackVoice('saved', { entry: 'edit', corrected: fields.length })
      fields.forEach(field => analytics.trackVoice('corrected', { entry: 'edit', field }))
    }
    emit('saved', res.data)
  } catch (e) {
    errorMsg.value = logSaveErrorMessage(e, t)
  } finally {
    loading.value = false
  }
}
</script>
