<script setup lang="ts">
import { ref, defineAsyncComponent } from 'vue'
import { useI18n } from 'vue-i18n'
import { ChevronLeftIcon, TrashIcon } from '@heroicons/vue/24/outline'
import api from '../../../api/axios'
import { useLocaleFormat } from '../../../composables/useLocaleFormat'
import { useLogUndo } from '../../../composables/useLogUndo'
import { tripFormFrom, newTripFormAt, tripFormError, tripPayload, type TripFormValues } from '../../../utils/tripForm'
import DayTripForm from './DayTripForm.vue'
import { hasTripMap } from '../../../utils/tripMap'
// Leaflet erst laden, wenn eine Fahrt mit Ort offen ist
const ActivityLocationMap = defineAsyncComponent(() => import('../../dashboard/ActivityLocationMap.vue'))
import type { DayTrip } from '../../../composables/storyDay'

/**
 * Zweite Ebene des Tagesblatts für eine Fahrt: bearbeiten und löschen (mit Rückgängig im Toast)
 * oder nachtragen. Nachtragen endet zur übergebenen Zeit (Tag plus Uhrzeit von jetzt).
 */
const props = defineProps<{ trip: DayTrip | null; carId: string; newEnd: Date }>()
const emit = defineEmits<{ back: []; saved: [action: 'edit_trip' | 'add_trip']; deleted: [] }>()
const { t } = useI18n()
const { distanceUnitLabel } = useLocaleFormat()
const { deleteTripWithUndo } = useLogUndo()

// Record wie in useLogList: DayTripForm schreibt leere Felder als ''
const form = ref<Record<string, any>>(props.trip ? tripFormFrom(props.trip) : newTripFormAt(props.newEnd))
const saving = ref(false)
const error = ref<string | null>(null)
const confirmingDelete = ref(false)

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
  <div class="flex min-h-0 flex-1 flex-col" data-testid="day-trip-panel">
    <div class="flex items-center gap-2 border-b-2 border-gray-100 px-4 py-3 dark:border-gray-800">
      <button type="button" data-testid="day-detail-back" :aria-label="t('common.back')"
        class="-ml-2 grid h-11 w-11 shrink-0 place-items-center rounded-full text-gray-700 hover:bg-gray-100 dark:text-gray-200 dark:hover:bg-gray-800"
        @click="emit('back')">
        <ChevronLeftIcon class="h-5 w-5" />
      </button>
      <h2 class="flex-1 text-[18px] font-bold tracking-tight">{{ trip ? t('storyday.trip') : t('storyday.add_trip') }}</h2>
    </div>
    <div class="min-h-0 flex-1 overflow-y-auto p-4">
      <!-- Karte oben: auf dem Handy randlos über dem Formular, ab 1024 px oben in der rechten Spalte -->
      <div v-if="trip && hasTripMap(trip)" class="relative -mx-4 -mt-4 mb-4 h-52 overflow-hidden border-b-2 border-gray-100 lg:mx-0 lg:mt-0 lg:h-72 lg:rounded-2xl lg:border-2 lg:border-gray-900 dark:border-gray-800"
        data-testid="day-trip-map">
        <ActivityLocationMap variant="panel" :label="t('dashboard.trip_map_alt')"
          :start-geohash="trip.locationStartGeohash" :end-geohash="trip.locationEndGeohash"
          :route-polyline="trip.routePolyline" :route-kind="trip.routeKind" :trace-polyline="trip.tracePolyline" />
      </div>
      <DayTripForm v-model="form" :mode="trip ? 'edit' : 'add'" :error="error" :saving="saving" :distance-unit="distanceUnitLabel()"
        @save="save" @cancel="emit('back')" />
      <div v-if="trip" class="mt-6 border-t-2 border-gray-100 pt-4 dark:border-gray-800">
        <div v-if="confirmingDelete" class="flex items-center gap-2">
          <span class="flex-1 text-sm text-gray-700 dark:text-gray-200">{{ t('storyday.delete_trip_confirm') }}</span>
          <button type="button" class="min-h-11 px-3 text-sm font-medium text-gray-500 dark:text-gray-400" @click="confirmingDelete = false">{{ t('common.cancel') }}</button>
          <button type="button" data-testid="day-trip-delete-confirm" :disabled="saving"
            class="btn-3d min-h-11 rounded-xl bg-red-600 px-4 text-sm font-semibold text-white disabled:opacity-40" @click="remove">
            {{ t('dashboard.action_delete') }}
          </button>
        </div>
        <button v-else type="button" data-testid="day-trip-delete"
          class="inline-flex min-h-11 items-center gap-2 rounded-xl px-2 text-sm font-semibold text-gray-600 hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-white/10"
          @click="confirmingDelete = true">
          <TrashIcon class="h-5 w-5" aria-hidden="true" />{{ t('storyday.delete_trip') }}
        </button>
      </div>
    </div>
  </div>
</template>
