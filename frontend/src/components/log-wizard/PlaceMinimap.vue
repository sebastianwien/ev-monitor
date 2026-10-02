<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { MINIMAP_ZOOM } from './minimapTiles'

/** Stummes Kartenbild der gewählten Säule: nicht bedienbar, nur Orientierung. Kein Marker-Bild (Vite), ein Kreis reicht. */
const props = defineProps<{ lat: number; lon: number }>()
const container = ref<HTMLElement | null>(null)
/** Erst wenn alle Kacheln des Ausschnitts da sind, blendet die Karte ein - bis dahin ruhige graue Fläche statt Kachel-Geploppe */
const ready = ref(false)
let map: L.Map | null = null
let dot: L.CircleMarker | null = null

onMounted(() => {
  if (!container.value) return
  map = L.map(container.value, {
    zoomControl: false, attributionControl: true, dragging: false, scrollWheelZoom: false, doubleClickZoom: false,
    boxZoom: false, keyboard: false, touchZoom: false, fadeAnimation: false,
  }).setView([props.lat, props.lon], MINIMAP_ZOOM)
  map.attributionControl.setPrefix(false).setPosition('topright')
  // OSM-Kacheln, per CSS entsättigt (siehe Template): keine Schlüssel, keine fremden Konten. CARTO und
  // Stadia verlangen inzwischen API-Keys, die Kacheln tragen sonst ein Wasserzeichen.
  const tiles = L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', { attribution: '&copy; OpenStreetMap', maxZoom: 19 })
  tiles.once('load', () => { ready.value = true })
  tiles.addTo(map)
  dot = L.circleMarker([props.lat, props.lon], { radius: 7, color: '#ffffff', weight: 2, fillColor: '#4f46e5', fillOpacity: 1 }).addTo(map)
})
watch(() => [props.lat, props.lon], ([lat, lon]) => { map?.setView([lat, lon]); dot?.setLatLng([lat, lon]) })
onUnmounted(() => { map?.remove(); map = null })
</script>

<template>
  <!-- Graustufen hell, invertierte Graustufen dunkel: ruhiger Hintergrund, der Punkt bleibt die einzige Farbe.
       Die Einblendung sitzt auf dem Wrapper: am Leaflet-Container selbst darf Vue kein class binden, es überschriebe Leaflets eigene Klassen. -->
  <div aria-hidden="true" :class="['h-full w-full transition-opacity duration-300 motion-reduce:transition-none', ready ? 'opacity-100' : 'opacity-0']">
    <div ref="container"
      class="minimap h-full w-full [&_.leaflet-control-attribution]:text-[9px] [&_.leaflet-control-attribution]:bg-white/60 dark:[&_.leaflet-control-attribution]:bg-gray-900/60 dark:[&_.leaflet-control-attribution]:text-gray-400" />
  </div>
</template>

<style scoped>
:global(.dark) .minimap :deep(.leaflet-tile) { filter: grayscale(1) invert(1) contrast(0.8) brightness(0.75); }
</style>
