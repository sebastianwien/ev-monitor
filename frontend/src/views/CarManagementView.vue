<script setup lang="ts">
import { carDisplayName } from '../utils/enumLabel'
import { computed, onActivated, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import {
  TruckIcon, ArrowDownTrayIcon, ChartBarIcon, BoltIcon, LockClosedIcon, ChevronRightIcon,
} from '@heroicons/vue/24/outline'
import CarFormFields, { type CarFormState } from '../components/car/CarFormFields.vue'
import CarFormSheet from '../components/car/CarFormSheet.vue'
import BatterySohModal from '../components/car/BatterySohModal.vue'
import ConsumptionInfoBox from '../components/dashboard/ConsumptionInfoBox.vue'
import CarCostCard from '../components/car/CarCostCard.vue'
import CarShareBox from '../components/car/CarShareBox.vue'
import CarShareSheet from '../components/car/CarShareSheet.vue'
import CarHero from '../components/car/CarHero.vue'
import CarSwitch from '../components/car/CarSwitch.vue'
import CarManageSheet from '../components/car/CarManageSheet.vue'
import { useCarShare } from '../composables/useCarShare'
import { readLocal, writeLocal } from '../utils/safeStorage'
import TeslaTelemetryPrompt from '../components/car/TeslaTelemetryPrompt.vue'
import CarSetupTeaser from '../components/car/CarSetupTeaser.vue'
import AutoSyncPrompt from '../components/car/AutoSyncPrompt.vue'
import RatingDeleteSheet from '../components/rating/RatingDeleteSheet.vue'
import MissingVehicleDialog from '../components/car/MissingVehicleDialog.vue'
import CarRatingsCard from '../components/rating/CarRatingsCard.vue'
import { ratingService } from '../api/ratingService'
import { useRatingStore } from '../stores/rating'
import type { Car } from '../api/carService'
import teslaFleetService from '../api/teslaFleetService'
import { analytics } from '../services/analytics'
import { isTeslaCar } from '../composables/useImportGating'
import { useCarForm } from '../composables/useCarForm'
import { useCarImages } from '../composables/useCarImages'
import { useCarSetupTeaser } from '../composables/useCarSetupTeaser'
import { useCarOdometer, useCarAvgConsumption } from '../composables/useCarFacts'
import { useLogsRefreshStore } from '../stores/logsRefresh'
import { useCarStore } from '../stores/car'

const { t } = useI18n()


// -- Car Form --
const {
  cars, availableModels, loading, error, showForm, editingCar,
  showToast, toastMessage, teslaStatus,
  selectedBrand, selectedModel, year, month, licensePlate, trim,
  selectedCapacity, selectedSpecId, selectedTrimLevel, useCustomCapacity, customCapacity,
  powerKw, batteryDegradationPercent, hasHeatPump, isBusinessCar,
  sortedBrands, isSonstige, selectedModelCapacities, finalCapacity, powerPs,
  isGroupedByTrim, trimGroups, visibleOptionsForTrim,
  selectTrimGroup,
  capacityWasCorrected,
  fetchCars, refreshCars, fetchBrands, resetForm,
  openAddForm, openEditForm, submitForm, deleteCar, setActiveCar, getModelLabel, toastUndo,
} = useCarForm()

// Formularzustand fuer CarFormFields: reactive() packt die Refs aus, v-model dort schreibt
// auf dieselben Refs - ein Formular fuer Anlegen und Bearbeiten, ein Zustand.
const formState: CarFormState = reactive({
  editingCar, selectedBrand, selectedModel, year, month, licensePlate, trim,
  selectedCapacity, selectedSpecId, selectedTrimLevel, customCapacity, useCustomCapacity,
  powerKw, batteryDegradationPercent, hasHeatPump, isBusinessCar, capacityWasCorrected,
  sortedBrands, availableModels, isSonstige, selectedModelCapacities, finalCapacity, powerPs,
  isGroupedByTrim, trimGroups, visibleOptionsForTrim, selectTrimGroup, getModelLabel,
})

// -- Car Images --
const {
  imageBlobUrls, imageUploading, imagePublicForUpload,
  revokeAllBlobUrls, loadCarImages, initVisibility,
  handleVisibilityChange, handleImageUpload, handleDeleteImage,
} = useCarImages(cars, error, showToast, toastMessage)

// Fahrzeug, dessen Batteriegesundheit-Detailansicht offen ist (null = geschlossen).
const sohModalCar = ref<Car | null>(null)

// -- Gewaehltes Fahrzeug: die Seite zeigt eines, der Switcher wechselt --
const LS_SELECTED_CAR = 'cars.selected'
const selectedCarId = ref<string | null>(readLocal(LS_SELECTED_CAR))
const selectedCar = computed<Car | null>(() => {
  if (cars.value.length === 0) return null
  return cars.value.find(c => c.id === selectedCarId.value)
    ?? cars.value.find(c => c.isPrimary)
    ?? cars.value[0]
})
function selectCar(id: string) {
  selectedCarId.value = id
  writeLocal(LS_SELECTED_CAR, id)
}
const menuOpen = ref(false)

// Hero-Chips Kilometerstand und Ø Verbrauch gesamt; neu laden, wenn hier ein Log gespeichert wurde.
const selectedCarIdResolved = computed(() => selectedCar.value?.id ?? null)
const logsRefresh = useLogsRefreshStore()
const logsVersion = () => logsRefresh.version
const heroOdometerKm = useCarOdometer(selectedCarIdResolved, [logsVersion])
const heroAvgConsumption = useCarAvgConsumption(selectedCarIdResolved, [logsVersion])

// Teilen-Status fuer den Hero-Chip; die Teilen-Box haelt ihren eigenen Zustand.
const heroShare = useCarShare()
const isShared = computed(() => heroShare.share.value !== null)
watch(() => selectedCar.value?.id, id => {
  heroShare.share.value = null
  if (id) heroShare.load(id)
}, { immediate: true })
function onShareChanged() {
  if (selectedCar.value) heroShare.load(selectedCar.value.id)
}
// Teilen-Sheet aus dem Hero-Chip. Nach dem Schliessen Chip und Teilen-Box neu laden,
// beide halten ihren eigenen Zustand.
const shareSheetOpen = ref(false)
const shareBoxRev = ref(0)
function closeShareSheet() {
  shareSheetOpen.value = false
  shareBoxRev.value++
  onShareChanged()
}
// Meldeweg "Fehlt etwas?": Dialog aus beiden Links im Formular (Marke/Modell, Akku).
const missingDialogOpen = ref(false)

// -- Setup-Teaser ueber der Auto-Card --
const { teaserFor, dismiss: dismissTeaser, loadTelemetryStatus } = useCarSetupTeaser(cars, teslaStatus)

// -- Orchestration --
const doFetchCars = async () => {
  await fetchCars(loadCarImages, revokeAllBlobUrls)
  initVisibility(cars.value)
}

const submitting = ref(false)
// Frisch angelegter XPeng: AutoSync (EU Data Act) direkt anbieten, statt den User
// erst in /imports danach suchen zu lassen.
// Dasselbe fuer Tesla: Fleet-Telemetry (gratis) direkt einrichten.
const teslaPromptCar = ref<Car | null>(null)
const teslaJustConnected = ref(false)
const teslaCallbackError = ref<string | null>(null)
// Frisch angelegte Smartcar-Marke ohne Abo: AutoSync direkt anbieten (dieselbe Regel wie der
// Teaser in der Card). Der EU-Data-Act-Weg bleibt unter /imports, wird hier aber nicht mehr beworben.
const autoSyncPromptCar = ref<Car | null>(null)
const autoSyncPromptCarName = computed(() => carDisplayName(autoSyncPromptCar.value))
const doSubmitForm = async () => {
  submitting.value = true
  try {
    const created = await submitForm(doFetchCars)
    if (created) selectCar(created.id)
    if (isTeslaCar(created)) teslaPromptCar.value = created
    else if (created && teaserFor(created) === 'AUTOSYNC') autoSyncPromptCar.value = created
  } finally { submitting.value = false }
}
// -- Fahrzeugbewertung: Zeile je Auto und Abschlussbewertung im Lösch-Flow --
const ratingStore = useRatingStore()
const ratingFor = (carId: string) => ratingStore.forCar(carId)
const deleteSheet = ref<{ carId: string; model: string; mode: 'rate' | 'text'; deleteWithCar: boolean } | null>(null)
const deleteSheetBusy = ref(false)

const doDeleteCar = async (id: string) => {
  const rating = await ratingStore.getEligibility().then(e => e.cars.find(c => c.carId === id) ?? null).catch(() => null)
  if (rating?.eligible && !rating.rated) {
    deleteSheet.value = { carId: id, model: rating.modelName, mode: 'rate', deleteWithCar: false }
    return
  }
  if (rating?.rated) {
    const review = (await ratingService.getMyRating(id).catch(() => null))?.review
    if (review) {
      deleteSheet.value = { carId: id, model: rating.modelName, mode: 'text', deleteWithCar: review.deleteWithCar }
      return
    }
  }
  await deleteCar(id, doFetchCars)
}

const rateBeforeDelete = () => {
  if (!deleteSheet.value) return
  router.push(`/cars/${deleteSheet.value.carId}/bewerten?src=loeschen&danach=loeschen`)
}

const deleteFromSheet = async () => {
  if (!deleteSheet.value) return
  const id = deleteSheet.value.carId
  deleteSheet.value = null
  await deleteCar(id, doFetchCars, true)
  ratingStore.invalidate()
}

const toggleDeleteWithCar = async (value: boolean) => {
  if (!deleteSheet.value) return
  deleteSheetBusy.value = true
  try {
    const review = await ratingService.updateReviewSettings(deleteSheet.value.carId, value)
    if (deleteSheet.value) deleteSheet.value.deleteWithCar = review.deleteWithCar
  } catch {
    error.value = t('rating.save_error')
  } finally {
    deleteSheetBusy.value = false
  }
}

/** Rückkehr aus der Abschlussbewertung: das Löschen fortsetzen (mit der normalen Rückfrage). */
const resumeDeleteAfterRating = async () => {
  const id = route.query.loeschen
  if (typeof id !== 'string') return
  router.replace({ query: { ...route.query, loeschen: undefined } })
  if (cars.value.some(c => c.id === id)) await deleteCar(id, doFetchCars)
}

const closeTeslaPrompt = () => {
  teslaPromptCar.value = null
  teslaJustConnected.value = false
  teslaCallbackError.value = null
  // Wurde die Telemetry im Modal eingerichtet, soll der Teaser sofort verschwinden -
  // der gepollte teslaStatus waere dafuer zu traege.
  loadTelemetryStatus(true)
}

/** Teaser-CTA: dasselbe Einrichtungs-Modal wie direkt nach dem Anlegen eines Teslas. */
const openTeslaSetup = (car: Car) => {
  teslaPromptCar.value = car
  teslaJustConnected.value = !!teslaStatus.value?.connected
}

const route = useRoute()
const router = useRouter()

/**
 * Tesla schickt den User nach dem OAuth auf /cars zurueck (siehe TeslaFleetController).
 * Wir fangen das Ergebnis hier ab und setzen die Einrichtung im Modal fort - der
 * Virtual Key fehlt an dieser Stelle naemlich noch.
 */
const resumeTeslaSetupFromCallback = async () => {
  const connected = route.query['tesla-connected']
  const callbackError = route.query['tesla-error']
  if (!connected && !callbackError) return

  const status = await teslaFleetService.getStatus().catch(() => null)
  teslaPromptCar.value =
    cars.value.find(c => c.id === status?.carId) ?? cars.value.find(isTeslaCar) ?? null
  if (!teslaPromptCar.value) return

  if (connected) {
    teslaJustConnected.value = true
    analytics.trackConnectorActivated('tesla')
  }
  if (callbackError) teslaCallbackError.value = String(callbackError)

  // Query aufraeumen, damit ein Reload das Modal nicht erneut oeffnet.
  router.replace({ query: { ...route.query, 'tesla-connected': undefined, 'tesla-error': undefined } })
}

onMounted(async () => {
  // Marken braucht nur das Formular - parallel laden, nicht vor den Autos
  void fetchBrands()
  await fetchCars(loadCarImages, revokeAllBlobUrls)
  initVisibility(cars.value)
  await loadTelemetryStatus()
  await resumeTeslaSetupFromCallback()
  await ratingStore.getEligibility(true).catch(() => null)
  await resumeDeleteAfterRating()
})

// Die Seite lebt in KeepAlive (wie Dashboard und Logs): beim Zurueckkommen steht der Inhalt sofort
// da, der frische Stand kommt still nach. onActivated feuert auch direkt nach dem ersten Mount -
// dort laedt onMounted.
const carStore = useCarStore()
let firstActivation = true
onActivated(async () => {
  if (firstActivation) { firstActivation = false; return }
  // Store zurueckgesetzt (Logout, Auto geaendert): voll neu laden, damit nie ein alter Stand
  // - etwa die Autos eines vorherigen Nutzers auf demselben Geraet - kurz sichtbar ist.
  if (!carStore.carsLoaded) {
    await fetchCars(loadCarImages, revokeAllBlobUrls)
    initVisibility(cars.value)
    return
  }
  const fresh = await refreshCars()
  initVisibility(fresh)
  await loadCarImages(fresh.filter(c => !imageBlobUrls.value[c.id]))
})

const emptyStateFeatures = [
  { icon: ChartBarIcon, titleKey: 'cars.feat_models_title', descKey: 'cars.feat_models_desc' },
  { icon: BoltIcon, titleKey: 'cars.feat_wltp_title', descKey: 'cars.feat_wltp_desc' },
  { icon: LockClosedIcon, titleKey: 'cars.feat_privacy_title', descKey: 'cars.feat_privacy_desc' },
]

const copiedCarId = ref<string | null>(null)
const copyCarId = async (id: string) => {
  try {
    await navigator.clipboard.writeText(id)
    copiedCarId.value = id
    setTimeout(() => { copiedCarId.value = null }, 2000)
  } catch { /* ignore */ }
}

</script>

<template>
  <div class="mx-auto max-w-6xl pb-6 md:px-6 md:pb-12">
    <Transition name="fade" mode="out-in">
      <div v-if="!loading">
        <!-- Der aktive Tab benennt die Seite, die Ueberschrift bleibt fuer Screenreader. -->
        <h1 class="sr-only">{{ t('cars.title') }}</h1>

      <!-- Eine Box wie in der Uebersicht: mobil edge-to-edge, ab md mit Kante. Das Foto liegt randlos oben. -->
      <section class="overflow-hidden bg-white pb-6 md:rounded-3xl md:edge-box dark:bg-gray-900" data-testid="cars-box">

      <p v-if="error" class="mx-4 mt-4 rounded-xl bg-white px-4 py-3 text-sm text-gray-900 edge-card md:mx-6 dark:bg-gray-700/50 dark:text-gray-100" role="alert">
        {{ error }}
      </p>

      <!-- Ohne Auto: Leerzustand und Anlege-Formular -->
      <div v-if="cars.length === 0" class="px-4 pt-4 md:px-6 md:pt-6">

      <!-- Anlegen ohne Autos: inline, damit der Leerzustand und das Formular zusammen stehen -->
      <div v-if="showForm && !editingCar" class="mb-8 rounded-xl bg-white p-4 edge-card md:p-6 dark:bg-gray-700/50">
        <h2 class="text-xl font-bold mb-4 text-gray-900 dark:text-gray-100">{{ t('cars.add_title') }}</h2>
        <CarFormFields :form="formState" :submitting="submitting" @submit="doSubmitForm" @cancel="resetForm" @missing="missingDialogOpen = true" />
      </div>

      <!-- Empty State: No Cars -->
      <div v-if="cars.length === 0 && !showForm" class="text-center py-16 px-4">
        <TruckIcon class="h-24 w-24 mx-auto text-gray-300 dark:text-gray-600 mb-6" />
        <h3 class="text-2xl font-bold text-gray-900 dark:text-gray-100 mb-3">{{ t('cars.empty_title') }}</h3>
        <p class="text-gray-600 dark:text-gray-400 mb-8 max-w-md mx-auto">
          {{ t('cars.empty_desc') }}
        </p>
        <button
          @click="openAddForm"
          class="btn-3d inline-flex min-h-12 items-center gap-2 rounded-xl border-2 border-edge bg-green-600 px-8 text-[15px] font-semibold text-white hover:bg-green-700">
          <TruckIcon class="h-5 w-5" />
          {{ t('cars.add_first_btn') }}
        </button>
        <div class="mt-8 grid grid-cols-1 sm:grid-cols-3 gap-4 max-w-2xl mx-auto text-left">
          <div v-for="feat in emptyStateFeatures" :key="feat.titleKey"
            class="rounded-xl bg-white p-4 edge-card dark:bg-gray-700/50">
            <component :is="feat.icon" class="h-6 w-6 mb-2 text-gray-500 dark:text-gray-400" aria-hidden="true" />
            <p class="text-sm font-semibold text-gray-900 dark:text-gray-100 mb-1">{{ t(feat.titleKey) }}</p>
            <p class="text-xs text-gray-600 dark:text-gray-400">{{ t(feat.descKey) }}</p>
          </div>
        </div>
      </div>

    </div>

      <!-- Fahrzeugseite: ein Auto, von oben nach unten Wechsel, Hero, Karten -->
      <template v-if="selectedCar">
        <!-- Kopfzeile wie in Uebersicht und Logs, nur bei mehreren Autos: den Namen traegt sonst das Foto -->
        <header v-if="cars.length > 1" class="px-4 py-2 md:px-6 md:py-3">
          <CarSwitch :model-value="selectedCar.id" :cars="cars" :car-image-urls="imageBlobUrls" @update:model-value="selectCar" />
        </header>

        <CarHero
          :car="selectedCar"
          :model-label="carDisplayName(selectedCar)"
          :image-url="imageBlobUrls[selectedCar.id] ?? null"
          :uploading="!!imageUploading[selectedCar.id]"
          :tesla-status="teslaStatus"
          :show-active="selectedCar.isPrimary && cars.length > 1"
          :shared="isShared"
          :rated="!!ratingFor(selectedCar.id)?.rated"
          :odometer-km="heroOdometerKm"
          :avg-consumption-kwh-per100km="heroAvgConsumption"
          @menu="menuOpen = true"
          @edit="openEditForm(selectedCar)"
          @soh="sohModalCar = selectedCar"
          @rating="router.push(`/cars/${selectedCar.id}/bewerten?src=cars_hero`)"
          @share="shareSheetOpen = true"
          @upload="handleImageUpload(selectedCar.id, $event)" />

        <div class="px-4 md:px-6 pt-4 space-y-4">
          <!-- Setup-Teaser: dieses Auto koennte seine Daten automatisch liefern, tut es aber nicht -->
          <CarSetupTeaser
            v-if="teaserFor(selectedCar)"
            :kind="teaserFor(selectedCar)!"
            @setup="openTeslaSetup(selectedCar)"
            @dismiss="dismissTeaser(selectedCar, teaserFor(selectedCar)!)"
          />

          <div class="md:grid md:grid-cols-[minmax(0,1.3fr)_minmax(0,1fr)] md:gap-5 space-y-4 md:space-y-0">
            <div class="space-y-4">
              <CarCostCard :key="selectedCar.id" :car-id="selectedCar.id" :registration-date="selectedCar.registrationDate ?? null" :show-history-link="selectedCar.isPrimary || cars.length === 1" />
            </div>
            <div class="space-y-4">
              <CarRatingsCard v-if="ratingFor(selectedCar.id)" :key="`rating-${selectedCar.id}`" :car-id="selectedCar.id" :eligibility="ratingFor(selectedCar.id)!" />
              <CarShareBox :key="`${selectedCar.id}-${shareBoxRev}`" :car-id="selectedCar.id" :title="carDisplayName(selectedCar)" compact @changed="onShareChanged" />
            </div>
          </div>

          <button type="button" @click="openAddForm" v-haptic
            class="btn-3d inline-flex min-h-11 items-center gap-2 rounded-xl border-2 border-edge bg-white px-4 text-sm font-semibold text-gray-900 hover:bg-gray-50 dark:bg-white/10 dark:text-gray-100 dark:hover:bg-white/15">
            {{ t('cars.add_btn') }}
          </button>
        </div>

        <CarManageSheet
          v-if="menuOpen"
          :car="selectedCar"
          :model-label="carDisplayName(selectedCar)"
          :image-url="imageBlobUrls[selectedCar.id] ?? null"
          :image-public="imagePublicForUpload[selectedCar.id] ?? false"
          :uploading="!!imageUploading[selectedCar.id]"
          :can-activate="!selectedCar.isPrimary && cars.length > 1"
          :copied="copiedCarId === selectedCar.id"
          @close="menuOpen = false"
          @edit="menuOpen = false; openEditForm(selectedCar)"
          @upload="handleImageUpload(selectedCar.id, $event)"
          @visibility="handleVisibilityChange(selectedCar.id, $event)"
          @delete-photo="handleDeleteImage(selectedCar.id)"
          @activate="menuOpen = false; setActiveCar(selectedCar.id)"
          @copy-id="copyCarId(selectedCar.id)"
          @add="menuOpen = false; openAddForm()"
          @delete="menuOpen = false; doDeleteCar(selectedCar.id)" />
      </template>

      <!-- Import und Erklaerung der Zahlen: fuer alle Autos, darum unter dem Auto -->
      <div class="space-y-3 px-4 pt-4 md:px-6">
      <!-- Import Hub Hint -->
      <router-link
        to="/imports"
        class="group flex min-h-14 items-center gap-3 rounded-xl bg-white px-4 py-3 edge-card transition-colors hover:bg-gray-50 dark:bg-gray-700/50 dark:hover:bg-gray-700"
      >
        <ArrowDownTrayIcon class="h-5 w-5 text-gray-500 dark:text-gray-400 shrink-0" />
        <div>
          <span class="text-sm font-semibold text-gray-900 dark:text-gray-100">{{ t('cars.import_title') }}</span>
          <span class="text-sm text-gray-500 dark:text-gray-400 ml-1">- Tesla, Sprit-Monitor, go-eCharger Cloud, OCPP Wallbox</span>
        </div>
        <ChevronRightIcon class="ml-auto h-5 w-5 shrink-0 text-gray-400 transition-transform group-hover:translate-x-0.5 dark:text-gray-500" aria-hidden="true" />
      </router-link>
      <ConsumptionInfoBox :initial-expanded="true" />
    </div>
      </section>

      <!-- Anlegen mit vorhandenen Autos: als Sheet -->
      <CarFormSheet v-if="showForm && !editingCar && cars.length > 0" :form="formState" :submitting="submitting" :title="t('cars.add_title')"
        @submit="doSubmitForm" @cancel="resetForm" @missing="missingDialogOpen = true" />

    <MissingVehicleDialog v-if="missingDialogOpen" :brand="selectedBrand || null" :model="selectedModel || null" @close="missingDialogOpen = false" />
    <RatingDeleteSheet
      v-if="deleteSheet"
      :model="deleteSheet.model"
      :mode="deleteSheet.mode"
      :delete-with-car="deleteSheet.deleteWithCar"
      :busy="deleteSheetBusy"
      @rate="rateBeforeDelete"
      @delete="deleteFromSheet"
      @cancel="deleteSheet = null"
      @update:delete-with-car="toggleDeleteWithCar"
    />

    <!-- Bearbeiten als Sheet (Teleport nach body liegt im BottomSheet, wegen des SwipeTabPagers) -->
    <CarFormSheet v-if="editingCar" :form="formState" :submitting="submitting" :title="t('cars.edit_title')"
      @submit="doSubmitForm" @cancel="resetForm" @missing="missingDialogOpen = true" />

      </div>
    </Transition>

    <!-- Tesla-Telemetry direkt nach dem Anlegen anbieten - und nach der OAuth-Rueckkehr fortsetzen -->
    <TeslaTelemetryPrompt
      v-if="teslaPromptCar"
      :car="teslaPromptCar"
      :connected="teslaJustConnected"
      :callback-error="teslaCallbackError"
      @close="closeTeslaPrompt"
    />

    <!-- Smartcar-Marke angelegt: AutoSync anbieten, Kauf auf /upgrade -->
    <AutoSyncPrompt v-if="autoSyncPromptCar" :car="autoSyncPromptCar" :car-name="autoSyncPromptCarName" @close="autoSyncPromptCar = null" />

    <!-- Toast Notification (outside Transition) - Teleport wie das Modal, sonst richtet
         sich der Toast am Pager-Track aus und liegt auf Mobile ausserhalb des Bildes. -->
    <Teleport to="body">
    <div v-if="showToast" class="fixed bottom-6 right-6 z-50 animate-slide-in">
      <div class="bg-green-600 text-white px-6 py-4 rounded-xl edge-card max-w-md">
        <div class="flex items-start">
          <svg class="w-6 h-6 mr-3 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"></path>
          </svg>
          <div class="min-w-0">
            <p class="text-sm font-medium">{{ toastMessage }}</p>
            <!-- Undo zum Soft-Delete: Tap-Ziel gross genug fuer Mobile. -->
            <button
              v-if="toastUndo"
              type="button"
              class="mt-2 -ml-1 px-3 py-2 text-sm font-bold underline underline-offset-2 rounded-sm hover:bg-white/15 focus:outline-none focus:ring-2 focus:ring-white"
              @click="toastUndo?.()"
            >
              {{ $t('cars.undo_delete') }}
            </button>
          </div>
        </div>
      </div>
    </div>
    </Teleport>

    <BatterySohModal
      v-if="sohModalCar"
      :car="sohModalCar"
      @changed="doFetchCars()"
      @close="sohModalCar = null"
    />

    <CarShareSheet
      v-if="shareSheetOpen && selectedCar"
      :car-id="selectedCar.id"
      :title="carDisplayName(selectedCar)"
      source="car_management"
      @close="closeShareSheet"
    />
  </div>
</template>

<style scoped>
.fade-enter-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from {
  opacity: 0;
}

.fade-enter-to {
  opacity: 1;
}
</style>

<style scoped>
@keyframes slide-in {
  from {
    transform: translateX(100%);
    opacity: 0;
  }
  to {
    transform: translateX(0);
    opacity: 1;
  }
}

.animate-slide-in {
  animation: slide-in 0.3s ease-out;
}
</style>
