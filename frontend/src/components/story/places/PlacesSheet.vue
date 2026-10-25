<script setup lang="ts">
import { ref, computed, watch, onMounted, defineAsyncComponent } from 'vue'
import { useI18n } from 'vue-i18n'
import { XMarkIcon, ChevronLeftIcon, MapIcon, LockClosedIcon } from '@heroicons/vue/24/outline'
import api from '../../../api/axios'
import { chargingBenchmarkService, type ChargingBenchmark } from '../../../api/chargingBenchmarkService'
import BottomSheet from '../../shared/BottomSheet.vue'
import ChoiceChip from '../../shared/ChoiceChip.vue'
import StoryPriceDetail from '../StoryPriceDetail.vue'
import PlacesList, { type PlaceRow } from './PlacesList.vue'
import type { MapPin, MapView } from './PlacesMap.vue'
import { PLACE_FILL, PIN_EDGE } from './placeStyle'
import {
  placeClass, benchmarkKey, benchmarkPairs, sortPlaces, legendClasses, leadParts, periodWindow, tripWindowParams,
  drawableTripLines, tapPlace, type ChargingPlace, type PlacePeriod, type PlacesTrip, type PlaceSelection,
} from '../../../composables/storyPlaces'

// Leaflet nur laden, wenn die Karte steht (ab 3 Orten).
const PlacesMap = defineAsyncComponent(() => import('./PlacesMap.vue'))

/**
 * Sheet "Wo du lädst": wo ich lade, was ich dort zahle und wo ich mehr zahle als andere.
 * Handy: Karte oben (40 %), Liste darunter. Ab lg Karte links, Liste rechts. Unter 3 Orten nur die Liste.
 * Der Preisvergleich eines Orts ist eine Unteransicht im selben Sheet (kein Sheet auf dem Sheet).
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

const places = ref<ChargingPlace[]>([])
const trips = ref<PlacesTrip[]>([])
const benchmarks = ref<Record<string, ChargingBenchmark | null>>({})
const loading = ref(true)
const error = ref(false)
const showTrips = ref(false)
const selection = ref<PlaceSelection<MapView | null>>({ active: null, saved: null })
const pricesFor = ref<string | null>(null)

const benchmarkOf = (p: ChargingPlace) => {
  const key = benchmarkKey(p)
  return key ? benchmarks.value[key] ?? null : null
}
const rows = computed<PlaceRow[]>(() => sortPlaces(places.value).map(p => {
  const benchmark = benchmarkOf(p)
  return { place: p, cls: placeClass(p, benchmark), benchmark }
}))
const classes = computed(() => rows.value.map(r => r.cls))
const showMap = computed(() => rows.value.length >= 3)
const pins = computed<MapPin[]>(() => rows.value.map(r => ({ geohash: r.place.geohash, cls: r.cls, kwh: r.place.kwh })))
const allLines = computed(() => drawableTripLines(trips.value))
const lines = computed(() => showTrips.value ? allLines.value : [])
/** Ohne Analytics trägt nur die neueste Fahrt ihre Linie, die übrigen kommen ohne. */
const tripsLocked = computed(() => showTrips.value && !props.canViewLiveAnalytics && trips.value.length > allLines.value.length)
const legend = computed(() => legendClasses(classes.value))

const periods = computed<Array<{ id: PlacePeriod; label: string }>>(() => [
  { id: 'month', label: props.monthName },
  { id: '12m', label: t('story.places_period_12m') },
  { id: 'all', label: t('story.places_period_all') },
])

const lead = computed(() => {
  if (loading.value) return ''
  const parts = leadParts(classes.value)
  if (parts.key === 'empty') return t(`story.places_empty_${period.value}`, { month: props.monthName })
  const when = t(`story.places_when_${period.value}`, { month: props.monthName })
  const count = t('story.places_count', { n: parts.places }, parts.places)
  return parts.key === 'high'
    ? t('story.places_lead_high', { places: count, when, high: parts.high }, parts.high)
    : t('story.places_lead_plain', { places: count, when })
})

let token = 0
async function load() {
  const mine = ++token
  loading.value = true
  error.value = false
  selection.value = { active: null, saved: null }
  const range = periodWindow(period.value, props.month, new Date())
  const placeParams = { carId: props.carId, ...(range.from ? { from: range.from } : {}), ...(range.to ? { to: range.to } : {}) }
  const [placesRes, tripsRes] = await Promise.allSettled([
    api.get<ChargingPlace[]>('/logs/places', { params: placeParams }),
    api.get<PlacesTrip[]>('/trips', { params: { carId: props.carId, ...tripWindowParams(range) } }),
  ])
  if (mine !== token) return
  if (placesRes.status === 'rejected') {
    error.value = true
    loading.value = false
    return
  }
  places.value = Array.isArray(placesRes.value.data) ? placesRes.value.data : []
  trips.value = tripsRes.status === 'fulfilled' && Array.isArray(tripsRes.value.data) ? tripsRes.value.data : []
  loading.value = false
  await loadBenchmarks()
}

/** Ein Aufruf je Betreiber und Ladeart, parallel, über Zeiträume hinweg gemerkt. 204 (unter 5 Fahrern) = null. */
async function loadBenchmarks() {
  const missing = benchmarkPairs(places.value).filter(p => !(`${p.cpo}|${p.type}` in benchmarks.value))
  if (!missing.length) return
  const results = await Promise.allSettled(missing.map(p => chargingBenchmarkService.get(p.cpo, p.type)))
  const next = { ...benchmarks.value }
  results.forEach((r, i) => { next[`${missing[i].cpo}|${missing[i].type}`] = r.status === 'fulfilled' ? r.value : null })
  benchmarks.value = next
}

watch(period, load)
onMounted(load)

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

const priceRow = computed(() => rows.value.find(r => r.place.geohash === pricesFor.value && r.benchmark) ?? null)

// "Betreiber eintragen" öffnet die Ladung im eigenen Sheet: erst dieses schließen, dann melden.
const sheet = ref<InstanceType<typeof BottomSheet> | null>(null)
let operatorLogId: string | null = null
function onOperator(logId: string) {
  operatorLogId = logId
  sheet.value?.requestClose()
}
function onClosed() {
  if (operatorLogId) emit('operator', operatorLogId)
  else emit('close')
}

const panelClass = computed(() => showMap.value ? 'h-[88dvh] sm:max-w-2xl lg:h-[85vh] lg:max-w-6xl' : 'sm:max-w-2xl')
</script>

<template>
  <BottomSheet ref="sheet" :label="t('story.map_title')" testid="places-sheet" :panel-class="panelClass" edge @close="onClosed">
    <template #default="{ close }">
      <div class="flex min-h-0 flex-1 flex-col bg-white dark:bg-gray-900">
        <!-- Unteransicht: Preise eines Orts, mit Pfeil zurück zur Karte -->
        <template v-if="priceRow">
          <div class="flex items-center gap-2 px-4 pt-4 lg:px-6 lg:pt-5">
            <button type="button" data-testid="places-back" :aria-label="t('common.back')"
              class="-ml-2 grid h-11 w-11 shrink-0 place-items-center rounded-full text-gray-700 hover:bg-gray-100 dark:text-gray-200 dark:hover:bg-gray-800"
              @click="pricesFor = null">
              <ChevronLeftIcon class="h-5 w-5" />
            </button>
            <h2 class="text-[20px] font-bold tracking-tight text-gray-900 dark:text-gray-50">
              {{ t('story.sheet_prices_title', { cpo: priceRow.benchmark!.cpoName }) }}
            </h2>
          </div>
          <div class="min-h-0 flex-1 overflow-y-auto px-4 pb-6 lg:px-6">
            <StoryPriceDetail :benchmark="priceRow.benchmark!" :price-per-kwh="priceRow.place.pricePerKwh" />
          </div>
        </template>

        <div v-show="!priceRow" class="flex min-h-0 flex-1 flex-col">
          <div class="flex items-start justify-between gap-3 px-4 pt-4 lg:px-6 lg:pt-5">
            <div class="grid min-w-0 gap-1">
              <h2 class="text-[22px] font-bold tracking-tight text-gray-900 lg:text-[26px] dark:text-gray-50">{{ t('story.map_title') }}</h2>
              <p data-testid="places-lead" class="min-h-[1.4em] text-[15px] font-semibold text-gray-600 dark:text-gray-300" aria-live="polite">{{ lead }}</p>
            </div>
            <button type="button" :aria-label="t('story.sheet_close')"
              class="btn-3d cta-shadow-sm grid h-11 w-11 shrink-0 place-items-center rounded-full border-2 border-gray-900 bg-white text-gray-900 dark:border-transparent dark:bg-gray-800 dark:text-gray-100 dark:shadow-none!"
              @click="close">
              <XMarkIcon class="h-5 w-5" />
            </button>
          </div>

          <div class="flex flex-wrap items-center gap-2 px-4 pt-3 lg:px-6">
            <div role="group" :aria-label="t('story.places_period_aria')" class="flex gap-1.5">
              <ChoiceChip v-for="p in periods" :key="p.id" size="sm" :active="period === p.id" :data-testid="`places-period-${p.id}`" @click="period = p.id">
                {{ p.label }}
              </ChoiceChip>
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
            <div v-if="showMap" class="relative min-h-0 border-y-2 border-gray-900 lg:border-b-0 lg:border-r-2 dark:border-gray-800">
              <PlacesMap ref="mapRef" :pins="pins" :lines="lines" :active="selection.active" @tap="gh => onTap(gh, true)" />
              <!-- Auf der Karte, nicht in der Chip-Reihe: nur hier wirkt er, und auf 390 px passt die Reihe sonst nicht -->
              <span v-if="allLines.length" class="absolute right-2 top-2 z-[500] rounded-full bg-white dark:bg-gray-900">
                <ChoiceChip size="sm" :active="showTrips" data-testid="places-trips" @click="showTrips = !showTrips">
                  <MapIcon class="h-4 w-4" aria-hidden="true" />{{ t('story.places_trips', { n: allLines.length }) }}
                </ChoiceChip>
              </span>
              <div aria-hidden="true" data-testid="places-legend"
                class="pointer-events-none absolute bottom-2 left-2 z-[500] flex flex-wrap gap-x-3 gap-y-1 rounded-lg bg-white/90 px-2.5 py-1.5 text-[11.5px] font-semibold text-gray-700 dark:bg-gray-900/90 dark:text-gray-300">
                <span v-for="c in legend" :key="c" class="inline-flex items-center gap-1">
                  <i class="inline-block h-2.5 w-2.5" :class="[PIN_EDGE, PLACE_FILL[c]]" />{{ t(`story.places_legend_${c}`) }}
                </span>
                <span v-if="lines.length" class="inline-flex items-center gap-1">
                  <i class="inline-block h-0.5 w-4 bg-gray-500 dark:bg-gray-400" />{{ t('story.places_legend_trips') }}
                </span>
              </div>
            </div>
            <div ref="listEl" class="min-h-0 overflow-y-auto pb-[env(safe-area-inset-bottom)]"
              :class="showMap ? 'lg:border-t-2 lg:border-gray-900 dark:lg:border-gray-800' : 'border-t-2 border-gray-900 dark:border-gray-800'">
              <div v-if="loading" class="grid gap-3 p-4 lg:px-6" aria-busy="true">
                <div v-for="i in 3" :key="i" class="h-12 animate-pulse rounded-lg bg-gray-200 dark:bg-gray-800" />
              </div>
              <p v-else-if="error" class="p-4 text-sm text-gray-700 lg:px-6 dark:text-gray-200">
                {{ t('story.error') }}
                <button type="button" class="ml-2 min-h-[44px] font-semibold underline" @click="load">{{ t('story.retry') }}</button>
              </p>
              <PlacesList v-else :rows="rows" :active="selection.active"
                @tap="gh => onTap(gh, false)" @prices="gh => pricesFor = gh" @operator="onOperator" />
            </div>
          </div>
        </div>
      </div>
    </template>
  </BottomSheet>
</template>
