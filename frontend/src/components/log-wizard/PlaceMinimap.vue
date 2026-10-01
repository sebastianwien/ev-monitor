<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'

/** Stummes Kartenbild der gewählten Säule: nicht bedienbar, nur Orientierung. Kein Marker-Bild (Vite), ein Kreis reicht. */
const props = defineProps<{ lat: number; lon: number }>()
const container = ref<HTMLElement | null>(null)
let map: L.Map | null = null
let dot: L.CircleMarker | null = null

onMounted(() => {
  if (!container.value) return
  map = L.map(container.value, {
    zoomControl: false, attributionControl: true, dragging: false, scrollWheelZoom: false, doubleClickZoom: false,
    boxZoom: false, keyboard: false, touchZoom: false, fadeAnimation: false,
  }).setView([props.lat, props.lon], 17)
  map.attributionControl.setPrefix(false)
  // Ruhige CARTO-Basiskarte statt bunter OSM-Kacheln, passend zum Farbschema der App (Tailwind-Klasse "dark").
  const dark = document.documentElement.classList.contains('dark')
  L.tileLayer(`https://{s}.basemaps.cartocdn.com/${dark ? 'dark_all' : 'light_all'}/{z}/{x}/{y}{r}.png`, {
    attribution: '&copy; OpenStreetMap &copy; CARTO', subdomains: 'abcd', maxZoom: 20,
  }).addTo(map)
  dot = L.circleMarker([props.lat, props.lon], { radius: 7, color: '#ffffff', weight: 2, fillColor: '#4f46e5', fillOpacity: 1 }).addTo(map)
})
watch(() => [props.lat, props.lon], ([lat, lon]) => { map?.setView([lat, lon]); dot?.setLatLng([lat, lon]) })
onUnmounted(() => { map?.remove(); map = null })
</script>

<template>
  <div ref="container" class="h-full w-full [&_.leaflet-control-attribution]:text-[9px] [&_.leaflet-control-attribution]:bg-white/60  dark:[&_.leaflet-control-attribution]:bg-gray-900/60 dark:[&_.leaflet-control-attribution]:text-gray-400" aria-hidden="true" />
</template>
