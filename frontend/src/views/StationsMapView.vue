<script setup lang="ts">
/**
 * Säulenkarte: Ladesäulen in der Umgebung oder an einer gesuchten Adresse, mit Katalogpreisen.
 *
 * Öffnet sofort an der zuletzt gezeigten Mitte (auf ein Kartenfeld gerundet, nur im Browser), fragt
 * dann den Standort und gleitet dorthin. Nach jedem Verschieben lädt sie die Felder des Ausschnitts
 * nach (entprellt), solange es höchstens zwölf sind; an den Server gehen nur Felder, nie eine Position.
 * Darunter ein nicht modales Sheet: Liste nach Entfernung zur Kartenmitte, eine Säule als zweite Ebene.
 */
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowPathIcon, MagnifyingGlassIcon, MapPinIcon, XMarkIcon } from '@heroicons/vue/24/outline'
import StationsMap from '../components/stations/StationsMap.vue'
import StationsSheet from '../components/stations/StationsSheet.vue'
import StationRow from '../components/stations/StationRow.vue'
import StationDetail from '../components/stations/StationDetail.vue'
import SegmentToggle from '../components/log-wizard/SegmentToggle.vue'
import Collapse from '../components/shared/Collapse.vue'
import { useStationsData } from '../composables/useStationsData'
import { readLastMapCenter, rememberMapCenter } from '../composables/useLastMapCenter'
import { useLocationSearch, type PlaceSuggestion } from '../composables/useLocationSearch'
import { placeLabel } from '../components/stations/stationText'
import { analytics } from '../services/analytics'
import {
  cellsForBounds, distanceMeters, inBounds, isAppleDevice, neighborRing, sortByDistance, stationsNotice, CELL_PRECISION,
  type Bounds, type ChargeType, type LatLon,
} from '../utils/stationMap'
import type { Snap } from '../utils/snapSheet'
import geohash from 'ngeohash'

/** Mitte Deutschlands, wenn es weder gemerkte Mitte noch Standort gibt */
const GERMANY: LatLon = { lat: 51.16, lon: 10.45 }
/** Straßenzoom: rund ein Kilometer um die Mitte; die breitere Desktop-Karte eine Stufe näher, damit zwölf Felder reichen */
const STREET_ZOOM = typeof window !== 'undefined' && window.matchMedia?.('(min-width: 768px)').matches ? 16 : 15
const MOVE_DEBOUNCE_MS = 300
/** Griff, Kopfzeile und zwei Zeilen */
const PEEK_PX = 190
/** Liegt der Standort näher an der Kartenmitte, bleibt die Karte stehen: kein Gleiten und Nachladen für ein paar Meter */
const STAY_METERS = 250
/** In der Nähe, wenn im Ausschnitt nichts steht: so viele der nächsten */
const NEARBY_MAX = 20
const TYPE_KEY = 'stations.type'

const { t } = useI18n()
const data = useStationsData()
const apple = isAppleDevice()
const remembered = readLastMapCenter()
const initialCenter = remembered ?? GERMANY
const initialZoom = remembered ? STREET_ZOOM : 6

const readType = (): ChargeType => { try { return localStorage.getItem(TYPE_KEY) === 'AC' ? 'AC' : 'DC' } catch { return 'DC' } }
const type = ref<ChargeType>(readType())
watch(type, v => {
  try { localStorage.setItem(TYPE_KEY, v) } catch { /* Speicher gesperrt: Wahl gilt nur jetzt */ }
  analytics.track('stations_type_switched', { type: v })
})
const typeOptions = computed(() => [
  { value: 'AC' as ChargeType, label: t('stations.type_ac'), testid: 'stations-type-ac' },
  { value: 'DC' as ChargeType, label: t('stations.type_dc'), testid: 'stations-type-dc' },
])

const mapRef = ref<InstanceType<typeof StationsMap> | null>(null)
const view = ref<{ bounds: Bounds; center: LatLon; zoom: number } | null>(null)
/** Felder des Ausschnitts; null, wenn es mehr als zwölf wären */
const viewCells = ref<string[] | null>(null)

const typeStations = computed(() => data.stations.value.filter(s => (type.value === 'DC' ? s.maxDcKw : s.maxAcKw) != null))
const center = computed(() => view.value?.center ?? initialCenter)
const inView = computed(() => view.value ? sortByDistance(typeStations.value.filter(s => inBounds(s, view.value!.bounds)), center.value) : [])
/** Steht im Ausschnitt nichts, die nächsten aus den mitgeladenen Nachbarfeldern */
const nearby = computed(() => inView.value.length ? [] : sortByDistance(typeStations.value, center.value).slice(0, NEARBY_MAX))
const listed = computed(() => inView.value.length ? inView.value : nearby.value)
const distanceTo = (s: { latitude: number; longitude: number }) => distanceMeters(center.value, { lat: s.latitude, lon: s.longitude })

// --- Nachladen ---
let moveTimer: ReturnType<typeof setTimeout> | null = null
let firstView = true
function onMoved(v: { bounds: Bounds; center: LatLon; zoom: number }) {
  view.value = v
  if (moveTimer) clearTimeout(moveTimer)
  // Der erste Ausschnitt lädt sofort: gemerkte Felder liegen im Cache und sollen ohne Warten stehen
  if (firstView) { firstView = false; load(v); return }
  moveTimer = setTimeout(() => load(v), MOVE_DEBOUNCE_MS)
}
function load(v: { bounds: Bounds; center: LatLon }) {
  viewCells.value = cellsForBounds(v.bounds)
  if (!viewCells.value) return
  rememberMapCenter(v.center.lat, v.center.lon)
  data.request(viewCells.value)
}
const viewLoaded = computed(() => !!viewCells.value && viewCells.value.every(c => data.isLoaded(c)))
/** Auf dem Land: steht im Ausschnitt nichts, einmal die Nachbarfelder dazu, damit die Karte selten leer bleibt */
const expanded = new Set<string>()
const ringLoaded = ref(false)
watch([viewLoaded, () => data.loading.value, inView], () => {
  const cells = viewCells.value
  if (!cells || !viewLoaded.value || data.loading.value) return
  const ring = neighborRing(cells)
  ringLoaded.value = ring.every(c => data.isLoaded(c))
  if (inView.value.length || ringLoaded.value) return
  const key = cells.join(',')
  if (expanded.has(key)) return
  expanded.add(key)
  data.request(ring)
})

const centerCell = computed(() => geohash.encode(center.value.lat, center.value.lon, CELL_PRECISION))
const outsideGermany = computed(() => data.cellInfo(centerCell.value)?.inGermany === false)

// --- Standort ---
const me = ref<LatLon | null>(null)
const located = ref<'pending' | 'ok' | 'denied'>('pending')
/** Hat der Nutzer die Karte selbst bewegt, gesucht oder eine Säule gewählt, holt ein später Standort sie nicht zurück */
let userTookOver = false
const takeOver = () => { userTookOver = true }
function locate(fly: boolean) {
  if (typeof navigator === 'undefined' || !navigator.geolocation) { located.value = 'denied'; return }
  navigator.geolocation.getCurrentPosition(
    p => {
      me.value = { lat: p.coords.latitude, lon: p.coords.longitude }
      located.value = 'ok'
      const near = view.value && view.value.zoom >= STREET_ZOOM && distanceMeters(view.value.center, me.value) < STAY_METERS
      if (fly && !near && !userTookOver) mapRef.value?.flyTo(me.value, Math.max(STREET_ZOOM, view.value?.zoom ?? 0))
    },
    () => { located.value = 'denied' },
    { enableHighAccuracy: false, timeout: 10_000, maximumAge: 60_000 },
  )
}
function toMyLocation() {
  // Ausdrücklich gewünscht: auch nach eigenem Verschieben dorthin
  userTookOver = false
  if (me.value) mapRef.value?.flyTo(me.value, STREET_ZOOM)
  else locate(true)
}

// --- Suche ---
const search = useLocationSearch(() => me.value ? { lat: me.value.lat, lon: me.value.lon } : null)
const searchInput = ref<HTMLInputElement | null>(null)
const onSearchInput = (e: Event) => search.autocomplete((e.target as HTMLInputElement).value)
function pickPlace(s: PlaceSuggestion) {
  userTookOver = true
  const picked = search.select(s)
  searchInput.value?.blur()
  selectedId.value = null
  mapRef.value?.flyTo({ lat: picked.latitude, lon: picked.longitude }, STREET_ZOOM)
  analytics.track('stations_search_used')
}
function clearSearch() {
  search.query.value = ''
  search.reset()
  searchInput.value?.focus()
}
/** Photon liefert denselben Ort mehrfach (Straße in Abschnitten): je Beschriftung einmal */
const suggestions = computed(() => {
  const seen = new Set<string>()
  return search.suggestions.value
    .map(s => ({ s, label: placeLabel(s.display_name, s.address?.country) }))
    .filter(x => !seen.has(x.label) && !!seen.add(x.label))
})
const showSuggestions = computed(() => suggestions.value.length > 0 || search.noResults.value)

// --- Auswahl ---
const snap = ref<Snap>('peek')
const sheetRef = ref<InstanceType<typeof StationsSheet> | null>(null)
const selectedId = ref<string | null>(null)
/** Richtung des Ebenenwechsels im Sheet, wie die Schritte im Erfassen-Wizard */
const levelMotion = ref<'step-forward' | 'step-back'>('step-forward')
/** Scrollstand der Liste, damit "Zurück" an derselben Stelle landet */
let listScroll = 0
const selected = computed(() => selectedId.value ? data.stations.value.find(s => s.id === selectedId.value) ?? null : null)
let snapBefore: Snap = 'peek'
function select(id: string, from: 'pin' | 'list') {
  userTookOver = true
  if (!selectedId.value) {
    snapBefore = snap.value
    listScroll = sheetRef.value?.scroller?.scrollTop ?? 0
  }
  levelMotion.value = 'step-forward'
  selectedId.value = id
  snap.value = 'full'
  const s = data.stations.value.find(x => x.id === id)
  analytics.track('station_selected', { from, priced: !!(type.value === 'DC' ? s?.priceDc : s?.priceAc) })
}
function back() {
  levelMotion.value = 'step-back'
  selectedId.value = null
  snap.value = snapBefore
}
/** Neue Ebene steht: Säule von oben, Liste an der gemerkten Stelle */
function onLevelEnter() {
  const el = sheetRef.value?.scroller
  if (el) el.scrollTop = selectedId.value ? 0 : listScroll
}
const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape' && selectedId.value && !e.defaultPrevented) { e.preventDefault(); back() } }

// --- Zustand im Sheet: genau ein Satz, als Handlung ---
const notice = computed(() => stationsNotice({
  tooWide: !viewCells.value, located: located.value, remembered: !!remembered, outsideGermany: outsideGermany.value,
  inView: inView.value.length, failed: data.failed.value, viewLoaded: viewLoaded.value, ringLoaded: ringLoaded.value,
  listed: listed.value.length,
}))
const NOTICE_TEXT = { zoom: 'stations.zoom_hint', search: 'stations.search_hint', outside: 'stations.outside', error: 'stations.error', empty: 'stations.empty' } as const
const SECONDARY_BTN = 'btn-3d min-h-11 shrink-0 rounded-full border-2 border-edge bg-white px-4 font-semibold text-gray-900 dark:bg-gray-900 dark:text-gray-100'
const countLabel = computed(() => inView.value.length
  ? t('stations.count', inView.value.length, { named: { n: inView.value.length } })
  : nearby.value.length ? t('stations.nearby', nearby.value.length, { named: { n: nearby.value.length } }) : t('stations.title'))

// --- Fläche: zwischen Kopf und Bottom-Nav, der Kopf ist je Design verschieden hoch ---
const root = ref<HTMLElement | null>(null)
const top = ref(0)
const measure = () => { top.value = Math.max(0, (root.value?.getBoundingClientRect().top ?? 0) + window.scrollY) }
// Ticker und Kopf verschieben den Inhalt nach dem ersten Bild noch (Polster mit Übergang): danach neu messen
const onTransitionEnd = (e: TransitionEvent) => { if (e.propertyName === 'padding-top' || e.propertyName === 'height') measure() }
let settleTimer: ReturnType<typeof setTimeout> | null = null

onMounted(() => {
  measure()
  requestAnimationFrame(() => requestAnimationFrame(measure))
  settleTimer = setTimeout(measure, 600)
  window.addEventListener('resize', measure)
  document.addEventListener('transitionend', onTransitionEnd)
  window.addEventListener('keydown', onKey)
  analytics.track('stations_opened', { start: remembered ? 'remembered' : 'default' })
  locate(true)
})
onUnmounted(() => {
  window.removeEventListener('resize', measure)
  document.removeEventListener('transitionend', onTransitionEnd)
  window.removeEventListener('keydown', onKey)
  if (settleTimer) clearTimeout(settleTimer)
  if (moveTimer) clearTimeout(moveTimer)
  search.reset()
  data.dispose()
})
</script>

<template>
  <div ref="root" class="stations-fill relative overflow-hidden md:mx-auto md:max-w-4xl md:rounded-2xl md:edge-card"
    :style="{ '--stations-top': `${top}px` }" data-testid="stations-view">
    <h1 class="sr-only">{{ t('stations.title') }}</h1>
    <StationsMap ref="mapRef" :stations="typeStations" :type="type" :selected-id="selectedId" :me="me"
      :initial-center="initialCenter" :initial-zoom="initialZoom" :bottom-inset="PEEK_PX"
      @moved="onMoved" @select="id => select(id, 'pin')" @user-move="takeOver" />

    <!-- Oben schwebend: Suche mit "Mein Standort", darunter die Ladeart -->
    <!-- Bei ganz offenem Sheet blenden Suche und Ladeart aus: oben bleibt nur ein Streifen Karte -->
    <div class="pointer-events-none absolute inset-x-3 top-3 z-[600] flex flex-col gap-2 transition-opacity duration-200 motion-reduce:transition-none"
      :class="snap === 'full' ? 'opacity-0 [&_*]:!pointer-events-none' : 'opacity-100'" :inert="snap === 'full' || undefined">
      <!-- z-20: die Vorschläge liegen über dem Ladeart-Schalter, dessen Knöpfe z-10 tragen -->
      <div class="pointer-events-auto relative z-20">
        <label class="flex min-h-11 items-center gap-2 rounded-xl border-2 border-edge bg-white pl-3 pr-1 edge-shadow dark:bg-gray-900">
          <MagnifyingGlassIcon class="h-5 w-5 shrink-0 text-gray-500 dark:text-gray-400" aria-hidden="true" />
          <span class="sr-only">{{ t('stations.search_label') }}</span>
          <input ref="searchInput" v-model="search.query.value" type="search" enterkeyhint="search" autocomplete="off"
            data-testid="stations-search"
            class="min-w-0 flex-1 bg-transparent py-2 text-base text-gray-900 placeholder:text-gray-500 focus:outline-none dark:text-gray-100 dark:placeholder:text-gray-400"
            :placeholder="t('stations.search_placeholder')" @input="onSearchInput"
            @keydown.enter.prevent="search.query.value.trim().length >= 3 && search.search(search.query.value.trim())" />
          <button v-if="search.query.value" type="button"
            class="grid h-11 w-11 shrink-0 place-items-center rounded-full text-gray-600 hover:bg-black/10 dark:text-gray-300 dark:hover:bg-white/10"
            :aria-label="t('stations.clear_search')" @click="clearSearch">
            <XMarkIcon class="h-5 w-5" aria-hidden="true" />
          </button>
          <button type="button" data-testid="stations-locate"
            class="grid h-11 w-11 shrink-0 place-items-center rounded-full text-indigo-600 hover:bg-black/10 focus-visible:outline focus-visible:outline-2 focus-visible:outline-indigo-600 dark:text-indigo-400 dark:hover:bg-white/10"
            :aria-label="t('stations.locate')" @click="toMyLocation">
            <MapPinIcon class="h-6 w-6" aria-hidden="true" />
          </button>
        </label>
        <Transition name="suggest">
        <ul v-if="showSuggestions" data-testid="stations-suggestions"
          class="absolute inset-x-0 top-full mt-1 overflow-hidden rounded-xl bg-white edge-box dark:bg-gray-800">
          <li v-for="x in suggestions" :key="x.label">
            <button type="button" class="flex min-h-11 w-full items-center px-4 py-2 text-left text-[15px] text-gray-900 hover:bg-black/5 dark:text-gray-100 dark:hover:bg-white/5"
              @click="pickPlace(x.s)">{{ x.label }}</button>
          </li>
          <li v-if="search.noResults.value" class="px-4 py-3 text-[14px] text-gray-600 dark:text-gray-400">{{ t('stations.no_results') }}</li>
        </ul>
        </Transition>
      </div>
      <SegmentToggle v-model="type" :options="typeOptions" accent :aria-label="t('stations.type_aria')"
        class="pointer-events-auto w-full max-w-sm self-center edge-shadow" />
    </div>

    <StationsSheet ref="sheetRef" v-model:snap="snap" :peek="PEEK_PX" :label="t('stations.sheet_aria')">
      <template v-if="!selected" #header>
        <div class="flex items-center justify-between px-4 pb-1 text-[12px] font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400">
          <span class="flex items-center gap-1.5" data-testid="stations-count">
            {{ countLabel }}
            <ArrowPathIcon v-if="data.loading.value" class="h-3.5 w-3.5 animate-spin motion-reduce:animate-none" aria-hidden="true" />
            <span v-if="data.loading.value" class="sr-only" role="status">{{ t('stations.loading') }}</span>
          </span>
          <span>{{ t('stations.no_monthly_fee') }}</span>
        </div>
      </template>

      <Transition :name="levelMotion" mode="out-in" @enter="onLevelEnter">
      <StationDetail v-if="selected" :key="selected.id" :station="selected" :type="type" :distance-meters="distanceTo(selected)" :apple="apple" @back="back" />
      <div v-else key="list">
        <Collapse :open="!!notice">
        <div data-testid="stations-notice" class="flex items-center justify-between gap-3 border-b border-gray-200 px-4 py-3 text-[15px] text-gray-800 dark:border-white/10 dark:text-gray-200">
          <span v-if="notice">{{ t(NOTICE_TEXT[notice]) }}</span>
          <button v-if="notice === 'error'" type="button" data-testid="stations-retry" :class="SECONDARY_BTN"
            @click="data.retry()">{{ t('stations.retry') }}</button>
          <button v-else-if="notice === 'outside' && located !== 'denied'" type="button" :class="SECONDARY_BTN"
            @click="toMyLocation">{{ t('stations.to_location') }}</button>
        </div>
        </Collapse>
        <!-- Ladeart gewechselt: nur der gefilterte Inhalt bewegt sich (ci.md, Segment-Schalter) -->
        <Transition name="filter-swap" mode="out-in">
          <div :key="type">
            <StationRow v-for="s in listed" :key="s.id" :station="s" :type="type" :distance-meters="distanceTo(s)" @select="select(s.id, 'list')" />
          </div>
        </Transition>
      </div>
      </Transition>
    </StationsSheet>
  </div>
</template>

<style scoped>
.stations-fill { height: calc(100dvh - var(--stations-top) - var(--bottom-nav-h, 0px)); }
@media (min-width: 768px) {
  .stations-fill { height: calc(100dvh - var(--stations-top) - 2.5rem); }
}
/* Ebenen im Sheet wie die Schritte im Erfassen-Wizard: rein 250 ms ease-out, raus 200 ms ease-in, 24 px seitlich */
.step-forward-enter-active, .step-back-enter-active { transition: opacity 250ms ease-out, translate 250ms ease-out; }
.step-forward-leave-active, .step-back-leave-active { transition: opacity 200ms ease-in, translate 200ms ease-in; }
.step-forward-enter-from { opacity: 0; translate: 24px 0; }
.step-forward-leave-to { opacity: 0; translate: -24px 0; }
.step-back-enter-from { opacity: 0; translate: -24px 0; }
.step-back-leave-to { opacity: 0; translate: 24px 0; }
/* Ladeart gewechselt: Liste kommt 14 px von unten mit Einblenden */
.filter-swap-enter-active { transition: opacity 220ms ease-out, translate 220ms ease-out; }
.filter-swap-leave-active { transition: opacity 120ms ease-in; }
.filter-swap-enter-from { opacity: 0; translate: 0 14px; }
.filter-swap-leave-to { opacity: 0; }
/* Vorschläge klappen unter dem Suchfeld auf */
.suggest-enter-active { transition: opacity 150ms ease-out, translate 150ms ease-out; }
.suggest-leave-active { transition: opacity 100ms ease-in; }
.suggest-enter-from { opacity: 0; translate: 0 -4px; }
.suggest-leave-to { opacity: 0; }
@media (prefers-reduced-motion: reduce) {
  .step-forward-enter-from, .step-forward-leave-to, .step-back-enter-from, .step-back-leave-to, .suggest-enter-from, .filter-swap-enter-from { translate: 0 0; }
}
/* Eigenes Leeren-Kreuz statt des nativen: sonst stehen zwei nebeneinander */
input[type='search']::-webkit-search-cancel-button { -webkit-appearance: none; appearance: none; }
</style>
