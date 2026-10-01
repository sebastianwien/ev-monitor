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
  }).setView([props.lat, props.lon], 16)
  map.attributionControl.setPrefix(false)
  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', { attribution: '&copy; OpenStreetMap', maxZoom: 19 }).addTo(map)
  dot = L.circleMarker([props.lat, props.lon], { radius: 7, color: '#ffffff', weight: 2, fillColor: '#4f46e5', fillOpacity: 1 }).addTo(map)
})
watch(() => [props.lat, props.lon], ([lat, lon]) => { map?.setView([lat, lon]); dot?.setLatLng([lat, lon]) })
onUnmounted(() => { map?.remove(); map = null })
</script>

<template>
  <div ref="container" class="h-full w-full [&_.leaflet-control-attribution]:text-[9px] [&_.leaflet-control-attribution]:bg-white/60 dark:[&_.leaflet-tile]:brightness-75 dark:[&_.leaflet-tile]:contrast-125" aria-hidden="true" />
</template>
