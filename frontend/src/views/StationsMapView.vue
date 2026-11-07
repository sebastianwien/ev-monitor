<script setup lang="ts">
/**
 * Säulenkarte: Ladesäulen in der Umgebung oder an einer gesuchten Adresse, mit Katalogpreisen.
 *
 * Öffnet sofort an der zuletzt gezeigten Mitte (auf ein Kartenfeld gerundet, nur im Browser).
 * Nach jedem Verschieben lädt sie die Felder des Ausschnitts nach (entprellt), solange es höchstens
 * zwölf sind; die Karte schickt dabei nur Felder, nie eine Position.
 */
import { computed, onMounted, onUnmounted, ref } from 'vue'
import StationsMap from '../components/stations/StationsMap.vue'
import { useStationsData } from '../composables/useStationsData'
import { readLastMapCenter, rememberMapCenter } from '../composables/useLastMapCenter'
import { cellsForBounds, type Bounds, type ChargeType, type LatLon } from '../utils/stationMap'

/** Mitte Deutschlands, wenn es weder gemerkte Mitte noch Standort gibt */
const GERMANY: LatLon = { lat: 51.16, lon: 10.45 }
/** Straßenzoom: rund ein Kilometer um die Mitte auf dem Handy */
const STREET_ZOOM = 15
const MOVE_DEBOUNCE_MS = 300

const data = useStationsData()
const remembered = readLastMapCenter()
const initialCenter = remembered ?? GERMANY
const initialZoom = remembered ? STREET_ZOOM : 6

const type = ref<ChargeType>('DC')
const view = ref<{ bounds: Bounds; center: LatLon; zoom: number } | null>(null)
/** Ausschnitt braucht mehr als zwölf Felder */
const tooWide = ref(false)

const typeStations = computed(() => data.stations.value.filter(s => (type.value === 'DC' ? s.maxDcKw : s.maxAcKw) != null))

let moveTimer: ReturnType<typeof setTimeout> | null = null
function onMoved(v: { bounds: Bounds; center: LatLon; zoom: number }) {
  view.value = v
  if (moveTimer) clearTimeout(moveTimer)
  moveTimer = setTimeout(() => load(v), MOVE_DEBOUNCE_MS)
}
function load(v: { bounds: Bounds; center: LatLon }) {
  const cells = cellsForBounds(v.bounds)
  tooWide.value = cells == null
  if (!cells) return
  rememberMapCenter(v.center.lat, v.center.lon)
  data.request(cells)
}

// Die Karte füllt den Platz zwischen Kopf und Bottom-Nav; der Kopf ist je Design verschieden hoch, also gemessen
const root = ref<HTMLElement | null>(null)
const top = ref(0)
const measure = () => { top.value = Math.max(0, (root.value?.getBoundingClientRect().top ?? 0) + window.scrollY) }
onMounted(() => {
  measure()
  window.addEventListener('resize', measure)
})
onUnmounted(() => {
  window.removeEventListener('resize', measure)
  if (moveTimer) clearTimeout(moveTimer)
  data.dispose()
})
</script>

<template>
  <div ref="root" class="stations-fill relative overflow-hidden md:mx-auto md:max-w-4xl md:rounded-2xl md:edge-card"
    :style="{ '--stations-top': `${top}px` }" data-testid="stations-view">
    <StationsMap :stations="typeStations" :type="type" :selected-id="null" :me="null"
      :initial-center="initialCenter" :initial-zoom="initialZoom" @moved="onMoved" />
  </div>
</template>

<style scoped>
.stations-fill { height: calc(100dvh - var(--stations-top) - var(--bottom-nav-h, 0px)); }
@media (min-width: 768px) {
  .stations-fill { height: calc(100dvh - var(--stations-top) - 2.5rem); }
}
</style>
