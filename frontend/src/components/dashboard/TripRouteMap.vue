<script setup lang="ts">
/**
 * Decorative map behind the "letzte Fahrt" tile.
 *
 * We have no recorded route - only a coarse start and end geohash (~600 m). The map
 * therefore shows two blurred areas and the straight line between them, never a road.
 * It is purely presentational: no interaction, no pointer events (the surrounding tile
 * stays clickable), aria-hidden for screen readers.
 */
import { ref, watch, onMounted, onActivated, onDeactivated, onUnmounted, nextTick } from 'vue'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { tripMapView } from '../../utils/tripMap'

const props = defineProps<{
  startGeohash: string | null | undefined
  endGeohash: string | null | undefined
}>()

const container = ref<HTMLDivElement | null>(null)
const visible = ref(false)
let map: L.Map | null = null
let resizeObserver: ResizeObserver | null = null

const ACCENT = '#6366f1'      // indigo-500, the trip accent used across the tile
const ACCENT_END = '#4f46e5'  // indigo-600

// Leaflet throws when it draws into a zero-sized container (hidden tab, pre-layout),
// so we wait for a real size before initialising - same pattern as ChargingHeatMap.
const hasSize = () => !!container.value && container.value.offsetWidth > 0 && container.value.offsetHeight > 0

function observeSize() {
  if (resizeObserver || !container.value || typeof ResizeObserver === 'undefined') return
  resizeObserver = new ResizeObserver(() => {
    if (!hasSize()) return
    resizeObserver?.disconnect()
    resizeObserver = null
    render()
  })
  resizeObserver.observe(container.value)
}

async function render() {
  await nextTick()
  teardown()

  const view = tripMapView(props.startGeohash, props.endGeohash)
  if (!view) return
  if (!container.value) return
  if (!hasSize()) {
    observeSize()
    return
  }

  map = L.map(container.value, {
    zoomControl: false,
    attributionControl: true,
    dragging: false,
    scrollWheelZoom: false,
    doubleClickZoom: false,
    boxZoom: false,
    keyboard: false,
    touchZoom: false,
    fadeAnimation: false,
    zoomSnap: 0.25,
  })
  map.attributionControl.setPrefix(false)

  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    attribution: '&copy; OpenStreetMap',
    maxZoom: 17,
    crossOrigin: true,
  }).addTo(map)

  const points: L.LatLngExpression[] = []
  const addArea = (point: { lat: number; lon: number }, color: string) => {
    const latLng: L.LatLngExpression = [point.lat, point.lon]
    points.push(latLng)
    L.circle(latLng, {
      radius: view.cellRadiusMeters,
      color,
      weight: 1,
      opacity: 0.55,
      fillColor: color,
      fillOpacity: 0.18,
      interactive: false,
    }).addTo(map!)
  }

  if (view.start) addArea(view.start, ACCENT)
  if (view.end) addArea(view.end, ACCENT_END)

  // Dashed, because it is a beeline between two approximations - not the driven route.
  if (points.length === 2) {
    L.polyline(points, {
      color: ACCENT,
      weight: 2,
      opacity: 0.8,
      dashArray: '5 5',
      interactive: false,
    }).addTo(map)
  }

  const bounds = L.latLngBounds(points).pad(0.35)
  map.fitBounds(bounds, { animate: false })
  // A single known point (or a round trip) would otherwise zoom to street level and
  // expose more precision than the geohash actually carries.
  if (points.length < 2 && map.getZoom() > 13) map.setZoom(13)

  visible.value = true
}

function teardown() {
  resizeObserver?.disconnect()
  resizeObserver = null
  map?.remove()
  map = null
  visible.value = false
}

watch(() => [props.startGeohash, props.endGeohash], render, { flush: 'post' })
onMounted(render)

// DashboardView is kept alive by <KeepAlive>: on deactivate the DOM detaches (zero
// size) while Leaflet's resize listener stays bound and would redraw into nothing.
let activatedOnce = false
onActivated(() => {
  if (!activatedOnce) {
    activatedOnce = true
    return
  }
  render()
})
onDeactivated(teardown)
onUnmounted(teardown)
</script>

<template>
  <div class="absolute inset-0 overflow-hidden pointer-events-none" aria-hidden="true">
    <div
      ref="container"
      class="absolute inset-0 transition-opacity duration-500"
      :class="visible ? 'opacity-40 dark:opacity-25' : 'opacity-0'"
    ></div>
    <!-- Readability floor: the text sits on the tile background, not on raw map tiles. -->
    <div class="absolute inset-0 bg-gradient-to-r from-white via-white/85 to-white/45
                dark:from-gray-800 dark:via-gray-800/85 dark:to-gray-800/45"></div>
  </div>
</template>

<style scoped>
:deep(.leaflet-container) {
  background: transparent;
}
/* Attribution stays (OSM requires credit) but must not compete with the tile content. */
:deep(.leaflet-control-attribution) {
  font-size: 8px;
  background: transparent;
  color: rgb(107 114 128);
  padding: 0 2px;
}
</style>
