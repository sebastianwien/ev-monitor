<script setup lang="ts">
import { ref, computed, defineAsyncComponent, onBeforeUnmount, useId, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ChevronLeftIcon, TrashIcon, XMarkIcon } from '@heroicons/vue/24/outline'
import api from '../../../api/axios'
import { useLocaleFormat } from '../../../composables/useLocaleFormat'
import { useLogUndo } from '../../../composables/useLogUndo'
import { tripFormFrom, newTripFormAt, tripFormError, tripPayload, type TripFormValues } from '../../../utils/tripForm'
import DayTripForm from './DayTripForm.vue'
import TripMeasuredFacts from './TripMeasuredFacts.vue'
import DataFeedbackButtons from '../../shared/DataFeedbackButtons.vue'
import DataFeedbackPanel from '../../shared/DataFeedbackPanel.vue'
import { hasTripMap, isRoutedLine, tripLine } from '../../../utils/tripMap'
import MapCredit from '../../shared/MapCredit.vue'
// Leaflet erst laden, wenn eine Fahrt mit Ort offen ist
const ActivityLocationMap = defineAsyncComponent(() => import('../../dashboard/ActivityLocationMap.vue'))
import type { DayTrip } from '../../../composables/storyDay'

/**
 * Zweite Ebene des Tagesblatts für eine Fahrt: bearbeiten und löschen (mit Rückgängig im Toast)
 * oder nachtragen. Nachtragen endet zur übergebenen Zeit (Tag plus Uhrzeit von jetzt).
 */
const props = withDefaults(defineProps<{
  trip: DayTrip | null; carId: string; newEnd: Date
  /** Oben links Pfeil zurück (zweite Ebene, Spalte am Desktop), oben rechts X, oder nichts: als eigenes Sheet auf dem
   *  Handy schließen Griff, Wischen, Tipp daneben und Escape, oben steht nur der Papierkorb. */
  dismiss?: 'back' | 'close' | 'none'
}>(), { dismiss: 'back' })
/** `changed`: eine gemessene Fahrt hat einen Tipp sofort gespeichert, die Ansicht bleibt offen */
const emit = defineEmits<{ back: []; saved: [action: 'edit_trip' | 'add_trip']; deleted: []; changed: [] }>()
const { t } = useI18n()
const { distanceUnitLabel } = useLocaleFormat()
const { deleteTripWithUndo } = useLogUndo()

// Record wie in useLogList: DayTripForm schreibt leere Felder als ''
const form = ref<Record<string, any>>(props.trip ? tripFormFrom(props.trip) : newTripFormAt(props.newEnd))
const saving = ref(false)
const error = ref<string | null>(null)
// Löschen sitzt oben neben Zurück bzw. X: der erste Tipp macht aus dem Papierkorb eine rote Pille "Löschen",
// erst der zweite löscht (Rückgängig im Toast). Ohne zweiten Tipp springt sie nach 3 s zurück.
const confirmingDelete = ref(false)
let disarm: ReturnType<typeof setTimeout> | null = null
function armDelete() {
  confirmingDelete.value = true
  if (disarm) clearTimeout(disarm)
  disarm = setTimeout(() => { confirmingDelete.value = false }, 3000)
}
onBeforeUnmount(() => { if (disarm) clearTimeout(disarm) })
// Speichern steht in der Fußleiste außerhalb des Formulars; eindeutig, falls beim Wechsel kurz zwei Panels im DOM sind
const formId = useId()
// Erkannte Fahrten (nicht von Hand angelegt) fragen überall "Korrekt erkannt?"
const rateable = computed(() => !!props.trip && props.trip.dataSource !== 'USER_CREATED')
// Wie bei der Ladung: mit Karte schweben Zurück und X als Kreise darauf, der Titel bleibt für Screenreader
const withMap = computed(() => !!props.trip && hasTripMap(props.trip))
const routed = computed(() => !!props.trip && isRoutedLine(tripLine(props.trip.tracePolyline, props.trip.routePolyline, props.trip.routeKind)))
const iconButton = computed(() => ['pointer-events-auto grid h-11 w-11 shrink-0 place-items-center rounded-full text-gray-700 dark:text-gray-200',
  withMap.value ? 'border-2 border-edge bg-white/95 hover:bg-white dark:border-gray-600 dark:bg-gray-800/95 dark:hover:bg-gray-800' : 'hover:bg-gray-100 dark:hover:bg-gray-800'])

// Gemessene Fahrt: nur Streckentyp und Besonderheiten sind änderbar, jeder Tipp speichert sofort, ohne Rückmeldung.
// Lehnt der Server ab, springt die Wahl auf den gespeicherten Stand zurück. Tipps laufen nacheinander raus.
const autosave = computed(() => !!props.trip && form.value.measured === true)
const choice = () => ({ routeType: form.value.routeType ?? '', loadExtras: [...(form.value.loadExtras ?? [])] as string[] })
const same = (a: ReturnType<typeof choice>, b: ReturnType<typeof choice>) => JSON.stringify(a) === JSON.stringify(b)
let stored = choice()
let queue: Promise<void> = Promise.resolve()
watch(() => [form.value.routeType, [...(form.value.loadExtras ?? [])]], () => {
  if (!autosave.value) return
  const next = choice()
  if (same(next, stored)) return
  queue = queue.then(async () => {
    try {
      await api.patch(`/trips/${props.trip!.id}`, tripPayload({ ...form.value, ...next } as TripFormValues))
      stored = next
      emit('changed')
    } catch {
      if (same(choice(), next)) Object.assign(form.value, { routeType: stored.routeType, loadExtras: [...stored.loadExtras] })
    }
  })
})

async function save() {
  const values = form.value as TripFormValues
  const invalid = tripFormError(values)
  if (invalid) { error.value = t(invalid); return }
  saving.value = true
  error.value = null
  try {
    if (props.trip) await api.patch(`/trips/${props.trip.id}`, tripPayload(values))
    else await api.post('/trips', { carId: props.carId, ...tripPayload(values) })
    emit('saved', props.trip ? 'edit_trip' : 'add_trip')
  } catch {
    error.value = t('storyday.save_failed')
  } finally {
    saving.value = false
  }
}

async function remove() {
  if (!props.trip) return
  saving.value = true
  try {
    await deleteTripWithUndo(props.trip.id)
    emit('deleted')
  } catch {
    confirmingDelete.value = false
    error.value = t('storyday.delete_failed')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="relative flex min-h-0 flex-1 flex-col bg-white dark:bg-gray-900" data-testid="day-trip-panel">
    <div :class="['flex shrink-0 items-center gap-2 px-4', withMap
      ? 'pointer-events-none absolute inset-x-0 top-0 z-[600] pb-2 pt-3'
      : 'border-b-2 border-gray-100 py-3 dark:border-gray-800']">
      <button v-if="dismiss === 'back'" type="button" data-testid="day-detail-back" :aria-label="t('common.back')"
        :class="[iconButton, '-ml-2']" @click="emit('back')">
        <ChevronLeftIcon class="h-5 w-5" />
      </button>
      <h2 :class="['flex-1 text-[18px] font-bold tracking-tight', withMap && 'sr-only']">{{ trip ? t('storyday.trip') : t('storyday.add_trip') }}</h2>
      <!-- Rechts gesammelt: mit Karte ist der Titel nur für Screenreader da und schiebt nichts nach rechts -->
      <div class="ml-auto flex items-center gap-2">
      <template v-if="trip">
        <button v-if="!confirmingDelete" type="button" data-testid="day-trip-delete" :aria-label="t('storyday.delete_trip')"
          :class="iconButton" @click="armDelete">
          <TrashIcon class="h-5 w-5 text-red-600 dark:text-red-400" aria-hidden="true" />
        </button>
        <button v-else type="button" data-testid="day-trip-delete-confirm" :disabled="saving" :aria-label="t('storyday.delete_trip_confirm')"
          class="pointer-events-auto btn-3d min-h-11 rounded-full border-2 border-edge bg-red-600 px-4 text-[15px] font-semibold text-white disabled:opacity-40" @click="remove">
          {{ t('dashboard.action_delete') }}
        </button>
      </template>
      <button v-if="dismiss === 'close'" type="button" data-testid="day-detail-close" :aria-label="t('common.close')"
        :class="[iconButton, '-mr-2']" @click="emit('back')">
        <XMarkIcon class="h-5 w-5" />
      </button>
      </div>
    </div>
    <div class="flex min-h-0 flex-1 flex-col overflow-y-auto p-4">
      <!-- Karte oben randlos wie der Ort bei der Ladung, unten Verlauf ins Formular, darauf die Fakten.
           Höchstens 224 px; fehlt Platz, schrumpft sie bis 144 px, bevor die Ansicht scrollen muss. -->
      <div v-if="withMap" class="relative -mx-4 -mt-4 mb-4 min-h-36 shrink basis-56 overflow-hidden md:mx-0 md:rounded-xl" data-testid="day-trip-map">
        <ActivityLocationMap variant="panel" no-credit :label="t('dashboard.trip_map_alt')"
          :start-geohash="trip!.locationStartGeohash" :end-geohash="trip!.locationEndGeohash"
          :route-polyline="trip!.routePolyline" :route-kind="trip!.routeKind" :trace-polyline="trip!.tracePolyline" />
        <div class="pointer-events-none absolute inset-x-0 bottom-0 z-[500] bg-gradient-to-t from-white via-white/70 to-transparent px-4 pt-14 dark:from-gray-900 dark:via-gray-900/70">
          <!-- Nur Text: der Verlauf lässt Tipps zur Karte durch, Knöpfe hier wären mit Karte nicht tippbar.
               Gemessene Fahrt: Tag und Uhrzeit stehen hier wie bei der Ladung, die Kennzahlen darunter im Formular. -->
          <div v-if="form.measured" :class="$slots.facts ? 'mb-1' : 'mb-3'">
            <TripMeasuredFacts :form="form" :distance-unit="distanceUnitLabel()" :stats="false" />
          </div>
          <slot name="facts" />
        </div>
      </div>
      <!-- Ergänzung des Rahmens über dem Formular, z. B. Tempo im Logbuch -->
      <slot v-else name="facts" />
      <DayTripForm :id="formId" v-model="form" :mode="trip ? 'edit' : 'add'" :error="error" :saving="saving" :distance-unit="distanceUnitLabel()"
        :actions="false" :measured-time="!withMap" @save="save">
        <!-- "Korrekt erkannt?" neben den Besonderheiten, die Rückfrage nach dem Daumen darunter -->
        <template #extras-end>
          <span v-if="rateable" role="group" :aria-label="t('dashboard.data_feedback_label')" data-testid="trip-feedback"
            class="ml-auto inline-flex items-center gap-0.5 self-center text-[13px] text-gray-500 dark:text-gray-400">
            <span class="mr-1">{{ t('dashboard.data_feedback_label') }}</span>
            <DataFeedbackButtons subject-type="TRIP" :subject-id="trip!.id" />
          </span>
        </template>
        <template #extra>
          <DataFeedbackPanel v-if="rateable" subject-type="TRIP" :subject-id="trip!.id" />
        </template>
      </DayTripForm>
      <MapCredit v-if="withMap" :routed="routed" class="mt-4" />
    </div>
    <!-- Fußleiste nur, wo gespeichert werden muss (von Hand angelegt oder nachgetragen): Speichern füllt,
         Abbrechen erst ab 640 px, auf dem Handy führt der Pfeil oben zurück. Gemessene Fahrten speichern jeden Tipp sofort. -->
    <footer v-if="!autosave" class="flex shrink-0 items-center gap-2 border-t-2 border-gray-100 px-4 pb-[max(0.75rem,env(safe-area-inset-bottom))] pt-3 dark:border-gray-800">
      <button type="button" class="hidden min-h-12 rounded-full px-3 text-[15px] font-semibold text-gray-600 hover:bg-gray-100 sm:inline-flex sm:items-center dark:text-gray-300 dark:hover:bg-white/10" @click="emit('back')">
        {{ t('common.cancel') }}
      </button>
      <button type="submit" :form="formId" data-testid="day-trip-save" :disabled="saving"
        class="btn-3d min-h-12 flex-1 rounded-xl border-2 border-edge bg-green-600 px-3 text-[15px] font-semibold text-white hover:bg-green-700 disabled:opacity-40">
        {{ t('logfields.save') }}
      </button>
    </footer>
  </div>
</template>
