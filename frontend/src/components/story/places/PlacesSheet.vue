<script setup lang="ts">
import { ref, computed, watch, onMounted, defineAsyncComponent } from 'vue'
import { useI18n } from 'vue-i18n'
import { XMarkIcon, LockClosedIcon } from '@heroicons/vue/24/outline'
import api from '../../../api/axios'
import { useChargingPlaces } from '../../../composables/useChargingPlaces'
import BottomSheet from '../../shared/BottomSheet.vue'
import ChoiceChip from '../../shared/ChoiceChip.vue'
import ToggleSwitch from '../../shared/ToggleSwitch.vue'
import PlacesList from './PlacesList.vue'
import type { MapPin, MapView } from './PlacesMap.vue'
import { PLACE_FILL, PIN_EDGE } from './placeStyle'
import {
  legendClasses, leadParts, periodWindow, tripWindowParams, drawableTripLines, tapPlace,
  type PlacePeriod, type PlacesTrip, type PlaceSelection,
} from '../../../composables/storyPlaces'

// Leaflet nur laden, wenn die Karte steht (ab einem Ort).
const PlacesMap = defineAsyncComponent(() => import('./PlacesMap.vue'))

/**
 * Sheet "Wo du lädst": wo ich lade, was ich dort zahle und wo ich mehr zahle als andere.
 * Handy: Karte oben (40 %), Liste darunter. Ab lg Karte links, Liste rechts. Ohne Orte keine Karte.
 * Der Preisvergleich eines Orts klappt in seiner Zeile auf. "Betreiber eintragen" lässt das Sheet offen,
 * der Bearbeiten-Dialog legt sich darüber (View).
 */
const props = defineProps<{
  carId: string
  /** Monat der Story, nicht der laufende. */
  month: { year: number; month: number }
  monthName: string
  canViewLiveAnalytics: boolean
  upsellTo: string
}>()
const emit = defineEmits<{ close: []; operator: [logId: string] }>()
const period = defineModel<PlacePeriod>('period', { default: '12m' })

const { t } = useI18n()

const { rows, loading, error, load: loadPlaces } = useChargingPlaces(() => props.carId)
const trips = ref<PlacesTrip[]>([])
const showTrips = ref(false)
const selection = ref<PlaceSelection<MapView | null>>({ active: null, saved: null })

const classes = computed(() => rows.value.map(r => r.cls))
// Ab einem Ort: der Teaser auf der Seite zeigt schon bei einem Ort eine Karte, das Sheet darf dann keine verlieren.
const showMap = computed(() => rows.value.length >= 1)
const pins = computed<MapPin[]>(() => rows.value.map(r => ({ geohash: r.place.geohash, cls: r.cls, kwh: r.place.kwh })))
const allLines = computed(() => drawableTripLines(trips.value))
const lines = computed(() => showTrips.value ? allLines.value : [])
/** Fahrten gibt es nur auf der Karte, ohne Karte kein Schalter. */
const showTripsToggle = computed(() => showMap.value && allLines.value.length > 0)
const tripsLabel = computed(() => t('story.places_trips', { n: allLines.value.length }, allLines.value.length))
/** Ohne Analytics trägt nur die neueste Fahrt ihre Linie, die übrigen kommen ohne. */
const tripsLocked = computed(() => showTrips.value && !props.canViewLiveAnalytics && trips.value.length > allLines.value.length)
const legend = computed(() => legendClasses(classes.value))

const periods = computed<Array<{ id: PlacePeriod; label: string }>>(() => [
  { id: 'month', label: props.monthName },
  { id: '12m', label: t('story.places_period_12m') },
  { id: 'all', label: t('story.places_period_all') },
])

const lead = computed(() => {
  if (loading.value || !loaded.value) return ''
  const parts = leadParts(classes.value)
  if (parts.key === 'empty') return t(`story.places_empty_${period.value}`, { month: props.monthName })
  const when = t(`story.places_when_${period.value}`, { month: props.monthName })
  const count = t('story.places_count', { n: parts.places }, parts.places)
  return parts.key === 'high'
    ? t('story.places_lead_high', { places: count, when, high: parts.high }, parts.high)
    : t('story.places_lead_plain', { places: count, when })
})

/** Erst nach dem ersten Laden gibt es einen Kopfsatz, sonst stünde kurz "keine Ladung mit Ort" da. */
const loaded = ref(false)
let tripToken = 0
async function load() {
  selection.value = { active: null, saved: null }
  const range = periodWindow(period.value, props.month, new Date())
  const mine = ++tripToken
  const tripsReq = api.get<PlacesTrip[]>('/trips', { params: { carId: props.carId, ...tripWindowParams(range) } })
    .then(res => Array.isArray(res.data) ? res.data : [], () => [])
  const placesReq = loadPlaces(range).then(() => { loaded.value = true })
  const nextTrips = await tripsReq
  if (mine === tripToken) trips.value = nextTrips
  await placesReq
}

watch(period, load)
onMounted(load)
/** Nach "Betreiber eintragen" (Dialog im View) neu laden. */
defineExpose({ reload: load })

const mapRef = ref<{ currentView: () => MapView | null; focus: (gh: string) => void; restore: (v: MapView) => void } | null>(null)
const listEl = ref<HTMLElement | null>(null)

function onTap(gh: string, fromMap: boolean) {
  const step = tapPlace(selection.value, gh, mapRef.value?.currentView() ?? null)
  selection.value = step.state
  if (step.restore) mapRef.value?.restore(step.restore)
  if (!step.focus) return
  mapRef.value?.focus(step.focus)
  if (fromMap) {
    const reduced = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
    listEl.value?.querySelector(`[data-place="${gh}"]`)?.scrollIntoView({ block: 'nearest', behavior: reduced ? 'auto' : 'smooth' })
  }
}

const panelClass = computed(() => showMap.value ? 'h-[88dvh] sm:max-w-2xl lg:h-[85vh] lg:max-w-6xl' : 'sm:max-w-2xl')
</script>

<template>
  <BottomSheet :label="t('story.map_title')" testid="places-sheet" :panel-class="panelClass" edge @close="emit('close')">
    <template #default="{ close }">
      <div class="flex min-h-0 flex-1 flex-col bg-white dark:bg-gray-900">
        <div class="flex min-h-0 flex-1 flex-col">
          <div class="flex items-start justify-between gap-3 px-4 pt-4 lg:px-6 lg:pt-5">
            <div class="grid min-w-0 gap-1">
              <h2 class="text-[22px] font-bold tracking-tight text-gray-900 lg:text-[26px] dark:text-gray-50">{{ t('story.map_title') }}</h2>
              <p data-testid="places-lead" class="min-h-[1.4em] text-[15px] font-semibold text-gray-600 dark:text-gray-300" aria-live="polite">{{ lead }}</p>
            </div>
            <button type="button" :aria-label="t('story.sheet_close')"
              class="btn-3d cta-shadow-sm grid h-11 w-11 shrink-0 place-items-center rounded-full border-2 border-edge bg-white text-gray-900 dark:bg-gray-800 dark:text-gray-100"
              @click="close">
              <XMarkIcon class="h-5 w-5" />
            </button>
          </div>

          <!-- Zeitraum ist eine Auswahl, Fahrten ein eigener Schalter: anders geformt, sonst wirkt er wie ein vierter Zeitraum.
               Handy eigene Zeile, ab sm rechts daneben. -->
          <div class="flex flex-wrap items-center gap-x-2 gap-y-1 px-4 pt-3 lg:px-6">
            <div role="group" :aria-label="t('story.places_period_aria')" class="flex gap-1.5">
              <ChoiceChip v-for="p in periods" :key="p.id" size="sm" :active="period === p.id" :data-testid="`places-period-${p.id}`" @click="period = p.id">
                {{ p.label }}
              </ChoiceChip>
            </div>
            <div v-if="showTripsToggle" data-testid="places-trips" class="flex w-full items-center justify-between gap-3 sm:ml-auto sm:w-auto sm:justify-start">
              <span class="text-[14px] font-semibold text-gray-800 dark:text-gray-200" aria-hidden="true">{{ tripsLabel }}</span>
              <ToggleSwitch v-model="showTrips" :label="tripsLabel" />
            </div>
          </div>

          <div v-if="tripsLocked" data-testid="places-trips-locked" class="mx-4 mt-2 flex min-h-11 items-center justify-between gap-3 lg:mx-6">
            <span class="flex items-center gap-1.5 text-sm text-gray-800 dark:text-gray-200">
              <LockClosedIcon class="h-4 w-4 shrink-0" aria-hidden="true" />{{ t('story.places_trips_locked') }}
            </span>
            <router-link :to="upsellTo"
              class="inline-flex min-h-[44px] shrink-0 items-center rounded-full bg-story-accent px-4 py-2.5 text-sm font-semibold text-white dark:bg-story-accent-dark dark:text-gray-900">
              {{ t('story.places_trips_cta') }}
            </router-link>
          </div>

          <div class="mt-3 grid min-h-0 flex-1" :class="showMap ? 'grid-rows-[40%_1fr] lg:grid-cols-[1.4fr_1fr] lg:grid-rows-1' : 'grid-rows-1'">
            <div v-if="showMap" class="relative min-h-0 border-y-2 border-edge lg:border-b-0 lg:border-r-2 dark:border-gray-800">
              <PlacesMap ref="mapRef" :pins="pins" :lines="lines" :active="selection.active" @tap="gh => onTap(gh, true)" />
              <div aria-hidden="true" data-testid="places-legend"
                class="pointer-events-none absolute bottom-2 left-2 z-[500] flex flex-wrap gap-x-3 gap-y-1 rounded-lg bg-white/90 px-2.5 py-1.5 text-[11.5px] font-semibold text-gray-700 dark:bg-gray-900/90 dark:text-gray-300">
                <span v-for="c in legend" :key="c" class="inline-flex items-center gap-1">
                  <i class="inline-block h-2.5 w-2.5" :class="[PIN_EDGE, PLACE_FILL[c]]" />{{ t(`story.places_legend_${c}`) }}
                </span>
                <span v-if="lines.length" class="inline-flex items-center gap-1">
                  <i class="inline-block h-0.5 w-4 bg-green-700 dark:bg-green-400" />{{ t('story.places_legend_trips') }}
                </span>
              </div>
            </div>
            <div ref="listEl" class="min-h-0 overflow-y-auto pb-[env(safe-area-inset-bottom)]"
              :class="showMap ? 'lg:border-t-2 lg:border-edge dark:lg:border-gray-800' : 'border-t-2 border-edge dark:border-gray-800'">
              <div v-if="loading || !loaded" class="grid gap-3 p-4 lg:px-6" aria-busy="true">
                <div v-for="i in 3" :key="i" class="h-12 animate-pulse rounded-lg bg-gray-200 dark:bg-gray-800" />
              </div>
              <p v-else-if="error" class="p-4 text-sm text-gray-700 lg:px-6 dark:text-gray-200">
                {{ t('story.error') }}
                <button type="button" class="ml-2 min-h-[44px] font-semibold underline" @click="load">{{ t('story.retry') }}</button>
              </p>
              <PlacesList v-else :rows="rows" :active="selection.active"
                @tap="gh => onTap(gh, false)" @operator="logId => emit('operator', logId)" />
            </div>
          </div>
        </div>
      </div>
    </template>
  </BottomSheet>
</template>
