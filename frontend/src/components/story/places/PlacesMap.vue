<script setup lang="ts">
/**
 * Karte der Ladeorte: ein Punkt je Ort (Größe nach kWh, Füllung nach Preisklasse), wahlweise die
 * Fahrtlinien darunter. Die Liste daneben sagt dasselbe vollständig, deshalb ist die Karte
 * aria-hidden und nichts darin fokussierbar (Marker ohne Tastatur, Links mit tabindex -1).
 *
 * Die Orte sind Geohash-Zellen (öffentlich rund 150 m, privat rund 600 m). Weiter als maxZoom
 * lässt die Karte nicht hinein, sonst wirkt der Punkt in der Zellmitte wie eine Adresse.
 */
import { ref, watch, onMounted, onUnmounted } from 'vue'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import geohash from 'ngeohash'
import { pinSize, focusZoom, type PlaceClass } from '../../../composables/storyPlaces'
import type { TripLine } from '../../../utils/tripMap'
import { PLACE_FILL, PIN_EDGE } from './placeStyle'

export interface MapPin { geohash: string; cls: PlaceClass; kwh: number }
export interface MapView { center: [number, number]; zoom: number }

const props = defineProps<{
  pins: MapPin[]
  /** Schon gefiltert: leer, solange die Fahrten aus sind. */
  lines: TripLine[]
  active: string | null
}>()
const emit = defineEmits<{ tap: [geohash: string] }>()

const container = ref<HTMLDivElement | null>(null)
let map: L.Map | null = null
let markers: L.LayerGroup | null = null
let tripLayer: L.LayerGroup | null = null
let resizeObserver: ResizeObserver | null = null
const markerOf = new Map<string, L.Marker>()

const MAX_ZOOM = 16
const OSM = '&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener" tabindex="-1">OpenStreetMap</a>'
// CC-BY-SA verlangt die Nennung, sobald eine vom Router gelegte Linie zu sehen ist (wie ActivityLocationMap).
const ORS = 'Route &copy; <a href="https://openrouteservice.org/" target="_blank" rel="noopener" tabindex="-1">openrouteservice by HeiGIT</a>'
// Heroicons 24 Outline "home", als String, weil Leaflet die Punkte selbst baut.
const HOUSE = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="60%" height="60%" aria-hidden="true"><path stroke-linecap="round" stroke-linejoin="round" d="m2.25 12 8.954-8.955a1.126 1.126 0 0 1 1.591 0L21.75 12M4.5 9.75v10.125c0 .621.504 1.125 1.125 1.125H9.75v-4.875c0-.621.504-1.125 1.125-1.125h2.25c.621 0 1.125.504 1.125 1.125V21h4.125c.621 0 1.125-.504 1.125-1.125V9.75"/></svg>'

const reducedMotion = () => typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
const latLngOf = (gh: string): L.LatLngTuple => {
  const { latitude, longitude } = geohash.decode(gh)
  return [latitude, longitude]
}
const hasSize = () => !!container.value && container.value.offsetWidth > 0 && container.value.offsetHeight > 0

function iconFor(pin: MapPin): L.DivIcon {
  const s = pinSize(pin.kwh)
  const on = props.active === pin.geohash ? ' is-active' : ''
  const html = `<span class="places-pin ${PIN_EDGE} ${PLACE_FILL[pin.cls]}${on}" style="width:${s}px;height:${s}px">${pin.cls === 'home' ? HOUSE : ''}</span>`
  return L.divIcon({ html, className: 'places-pin-wrap', iconSize: [s, s], iconAnchor: [s / 2, s / 2] })
}

/** Der markierte über allen, dann teure, dann Zuhause (sonst verdecken es Orte in derselben Gegend). */
const Z_INDEX: Partial<Record<PlaceClass, number>> = { high: 500, home: 400 }
const zIndexOf = (pin: MapPin) => props.active === pin.geohash ? 1000 : Z_INDEX[pin.cls] ?? 0

function drawPins(fit: boolean) {
  if (!map || !markers) return
  markers.clearLayers()
  markerOf.clear()
  for (const pin of props.pins) {
    const marker = L.marker(latLngOf(pin.geohash), { icon: iconFor(pin), keyboard: false, zIndexOffset: zIndexOf(pin) })
      .on('click', () => emit('tap', pin.geohash))
    marker.addTo(markers)
    markerOf.set(pin.geohash, marker)
  }
  if (fit && props.pins.length) {
    map.fitBounds(L.latLngBounds(props.pins.map(p => latLngOf(p.geohash))).pad(0.25), { maxZoom: 12, animate: false })
  }
}

let orsShown = false
function drawLines() {
  if (!map || !tripLayer) return
  tripLayer.clearLayers()
  for (const line of props.lines) {
    L.polyline(line.points, { className: 'places-trip', weight: 2.5, opacity: 0.55, interactive: false }).addTo(tripLayer)
  }
  const routed = props.lines.some(l => l.source === 'matched')
  if (routed !== orsShown) {
    if (routed) map.attributionControl.addAttribution(ORS)
    else map.attributionControl.removeAttribution(ORS)
    orsShown = routed
  }
}

/** Ausschnitt auf Punkte einpassen, animiert außer bei "weniger Bewegung". */
function fitTo(points: L.LatLngExpression[], maxZoom: number) {
  if (!map || !points.length) return
  map.fitBounds(L.latLngBounds(points).pad(0.15), { maxZoom, animate: !reducedMotion() })
}

function markActive() {
  for (const pin of props.pins) {
    const marker = markerOf.get(pin.geohash)
    if (!marker) continue
    marker.setZIndexOffset(zIndexOf(pin))
    marker.getElement()?.querySelector('.places-pin')?.classList.toggle('is-active', props.active === pin.geohash)
  }
}

function init() {
  if (map || !container.value) return
  if (!hasSize()) return
  map = L.map(container.value, {
    zoomControl: false,
    keyboard: false,
    maxZoom: MAX_ZOOM,
    minZoom: 3,
    zoomSnap: 0.5,
  })
  map.attributionControl.setPrefix(false)
  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    attribution: OSM, maxZoom: MAX_ZOOM, className: 'places-tiles', crossOrigin: true,
  }).addTo(map)
  tripLayer = L.layerGroup().addTo(map)
  markers = L.layerGroup().addTo(map)
  drawPins(true)
  placesKey = props.pins.map(p => p.geohash).join(',')
  drawLines()
}

/** Ausschnitt jetzt, zum Merken vor dem ersten Antippen. */
function currentView(): MapView | null {
  if (!map) return null
  const c = map.getCenter()
  return { center: [c.lat, c.lng], zoom: map.getZoom() }
}
function moveTo(center: L.LatLngTuple, zoom: number) {
  if (!map) return
  if (reducedMotion()) map.setView(center, zoom, { animate: false })
  else map.flyTo(center, zoom, { duration: 0.6 })
}
/** Hin zum Ort, nah genug für die Straßen ringsum. Wer schon näher dran ist, bleibt so nah. */
function focus(gh: string) {
  if (!map) return
  moveTo(latLngOf(gh), Math.max(map.getZoom(), focusZoom(gh)))
}
function restore(view: MapView) {
  moveTo(view.center, view.zoom)
}
defineExpose({ currentView, focus, restore })

// Nur neu einpassen, wenn andere Orte kommen (Zeitraum). Kommen die Vergleiche nach, ändern sich nur Farben.
let placesKey = ''
watch(() => props.pins, (pins) => {
  const key = pins.map(p => p.geohash).join(',')
  drawPins(key !== placesKey)
  placesKey = key
})
// Einschalten passt auf die Fahrten ein (sie liegen oft in einer Gegend, aus der Ferne nur ein Strich),
// Ausschalten zurück auf die Orte.
watch(() => props.lines, (lines, before) => {
  drawLines()
  if (lines.length && !before?.length) fitTo(lines.flatMap(l => l.points), 13)
  else if (!lines.length && before?.length) fitTo(props.pins.map(p => latLngOf(p.geohash)), 12)
})
watch(() => props.active, markActive)

onMounted(() => {
  init()
  if (typeof ResizeObserver === 'undefined' || !container.value) return
  // Das Sheet fährt herein und wechselt ab lg das Raster: ohne Neuvermessung bleiben graue Kacheln.
  resizeObserver = new ResizeObserver(() => {
    if (!hasSize()) return
    if (!map) init()
    else map.invalidateSize()
  })
  resizeObserver.observe(container.value)
})
onUnmounted(() => {
  resizeObserver?.disconnect()
  resizeObserver = null
  map?.remove()
  map = null
  markers = null
  tripLayer = null
  markerOf.clear()
})
</script>

<template>
  <div class="places-map absolute inset-0" aria-hidden="true" data-testid="places-map">
    <div ref="container" class="absolute inset-0" />
  </div>
</template>

<style>
/* Ungescoped: Leaflet baut Kacheln und Punkte selbst. .places-map grenzt auf diese Karte ein. */
.places-map .leaflet-container { background: #f3f4f6; font: inherit; }
.dark .places-map .leaflet-container { background: #111827; }
.places-map .places-tiles { filter: grayscale(1) contrast(0.92) brightness(1.06); }
.dark .places-map .places-tiles { filter: grayscale(1) invert(1) brightness(0.82) contrast(0.9); }
.places-map .places-pin-wrap { background: none; border: 0; }
.places-map .places-pin { display: grid; place-items: center; box-shadow: 2px 2px 0 0 #111827; transition: transform 0.15s ease; }
.dark .places-map .places-pin { box-shadow: none; }
.places-map .places-pin.is-active { transform: scale(1.35); }
.places-map .places-trip { stroke: #15803d; }
.dark .places-map .places-trip { stroke: #4ade80; }
.places-map .leaflet-control-attribution { font-size: 10px; }
.dark .places-map .leaflet-control-attribution { background: rgba(17, 24, 39, 0.8); color: #9ca3af; }
.dark .places-map .leaflet-control-attribution a { color: #9ca3af; }
@media (prefers-reduced-motion: reduce) {
  .places-map .places-pin { transition: none; }
}
</style>
