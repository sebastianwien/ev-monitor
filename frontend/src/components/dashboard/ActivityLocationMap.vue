<script setup lang="ts">
/**
 * Decorative map behind the recent-activity tiles.
 *
 * Two shapes, one component: a trip passes a start and an end geohash and gets the two
 * areas plus the line between them; a charge passes only its location and gets a single
 * area. In both cases the geohash is coarse (~600 m private, ~150 m public), so what is
 * drawn is a neighbourhood, never an address and never a recorded route.
 *
 * Purely presentational: no interaction, no pointer events (the surrounding tile stays
 * clickable), aria-hidden for screen readers.
 */
import { ref, watch, onMounted, onActivated, onDeactivated, onUnmounted, nextTick } from 'vue'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { tripMapView } from '../../utils/tripMap'
import { decodePolyline } from '../../utils/polyline'

const props = defineProps<{
  startGeohash: string | null | undefined
  endGeohash: string | null | undefined
  /**
   * Vom Backend berechnete Strassenverbindung (encodierte Polyline). Ohne sie bleibt es
   * bei der gestrichelten Luftlinie. Beides ist NICHT die gefahrene Strecke.
   */
  routePolyline?: string | null
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

  const route = decodePolyline(props.routePolyline)

  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    // CC-BY-SA 4.0 verlangt die Nennung von openrouteservice, sobald deren Ergebnis
    // gezeigt wird - deshalb haengt der Zusatz an der Route, nicht an der Karte.
    attribution: route.length
      ? '&copy; OpenStreetMap | Route &copy; openrouteservice'
      : '&copy; OpenStreetMap',
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
  // Zwei Linien uebereinander: eine helle Kontur traegt den Strich ueber jeden
  // Kartenuntergrund, ohne dass er kraeftiger werden muss als die Daten darueber.
  // Berechnete Strasse, wenn das Backend eine hat - sonst die gestrichelte Luftlinie.
  // Gestrichelt bleibt sie, weil sie zwei Naeherungen verbindet; die Route ist
  // durchgezogen, sie folgt echten Strassen (aber nicht zwingend den gefahrenen).
  if (route.length >= 2) {
    L.polyline(route, { color: '#ffffff', weight: 5, opacity: 0.5, interactive: false }).addTo(map)
    L.polyline(route, { color: ACCENT, weight: 2.5, opacity: 1, interactive: false }).addTo(map)
  } else if (points.length === 2) {
    L.polyline(points, {
        color: '#ffffff',
        weight: 5,
        opacity: 0.5,
      dashArray: '6 6',
      interactive: false,
    }).addTo(map)
    L.polyline(points, {
      color: ACCENT,
      weight: 2.5,
      opacity: 1,
      dashArray: '6 6',
      interactive: false,
    }).addTo(map)
  }

  // Start und Ziel als feste Punkte: die Flaechen allein verschwinden auf Kachelgroesse.
  points.forEach((point, i) => {
    L.circleMarker(point, {
      radius: 4,
      color: '#ffffff',
      weight: 2,
      opacity: 0.9,
      fillColor: i === 0 ? ACCENT : ACCENT_END,
      fillOpacity: 1,
      interactive: false,
    }).addTo(map!)
  })

  const bounds = L.latLngBounds(route.length >= 2 ? route : points).pad(route.length >= 2 ? 0.15 : 0.35)
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

watch(() => [props.startGeohash, props.endGeohash, props.routePolyline], render, { flush: 'post' })
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
  <div class="activity-map absolute inset-0 overflow-hidden pointer-events-none" aria-hidden="true">
    <!-- Die Opacity liegt bewusst eine Ebene ueber dem Leaflet-Container: ein dynamisches
         :class auf dem Container selbst schreibt beim Umschalten die komplette class-Liste
         neu und loescht dabei Leaflets eigene Klassen (leaflet-container & Co.). Ohne
         .leaflet-container greift Tailwinds Preflight `img { max-width: 100% }`, und die
         Kacheln kollabieren auf Breite 0 - die Karte bleibt leer. -->
    <div
      class="absolute inset-0 transition-opacity duration-500"
      :class="visible ? 'opacity-100' : 'opacity-0'"
    >
      <div ref="container" class="absolute inset-0"></div>
    </div>
    <!-- Lesbarkeits-Schleier: die Karte bleibt vollflaechig sichtbar, der Text braucht aber
         einen ruhigen Grund. Links (Titel, grosse Zahl) deckender als rechts, wo nur die
         Relativzeit steht. -->
    <div class="absolute inset-0 bg-gradient-to-r from-white/80 via-white/70 to-white/60
                dark:from-gray-900/70 dark:via-gray-900/55 dark:to-gray-900/45"></div>
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

<style>
/*
 * Ungescoped, weil Leaflet die Kacheln selbst erzeugt - sie tragen kein data-v-Attribut.
 * Der Klassenname .activity-map grenzt die Regeln auf diese Karte ein, die Heatmap
 * bleibt unberuehrt.
 */
.activity-map .leaflet-tile {
  /* Leaflet setzt plus-lighter, um Naht-Artefakte beim Ein-/Ausblenden zu vermeiden. Das
     addiert die Kacheln auf den Untergrund und macht sie auf der dunklen Kachel grell -
     hier wird nicht animiert (fadeAnimation: false), normales Blending reicht. */
  mix-blend-mode: normal;
  /* Entfaerbt und aufgehellt: die Karte soll als Textur lesbar sein, nicht als Bild mit
     eigenen Farben und Ortsnamen gegen die Kennzahlen antreten. */
  /* Der Weichzeichner trifft nur die Kacheln - Route und Punkte liegen in Leaflets
     overlay-pane und bleiben scharf. Damit verschwindet die feine Strassenzeichnung
     hinter der Schrift, die Karte bleibt aber als Form und Farbe erkennbar. */
  filter: grayscale(0.8) saturate(0.4) brightness(1.25) contrast(0.75) blur(0.5px);
}
.dark .activity-map .leaflet-tile {
  filter: grayscale(0.6) saturate(0.55) brightness(0.45) contrast(1) blur(0.5px);
}
</style>
