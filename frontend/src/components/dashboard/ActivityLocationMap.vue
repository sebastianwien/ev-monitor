<script setup lang="ts">
/**
 * Decorative map behind the recent-activity tiles.
 *
 * Two shapes, one component: a trip passes a start and an end geohash and gets the two
 * areas plus the line between them; a charge passes only its location and gets a single
 * area. Die Flaechen sind Geohash-Zellen, also immer eine Gegend und nie eine Adresse.
 *
 * Die Linie dazwischen gibt es in drei Formen: die aufgezeichnete Fahrspur des Fahrzeugs
 * (durchgezogen), die vom Router gerechnete Strassenverbindung und die blosse Luftlinie
 * (beide gestrichelt - sie sind Naeherungen, keine Aufzeichnung).
 *
 * Zwei Auftritte, eine Karte: als `backdrop` liegt sie entsaettigt und weichgezeichnet
 * hinter den Kennzahlen einer Kachel und traegt keinen eigenen Inhalt; als `panel` steht
 * sie fuer sich, in voller Farbe und mit eigenem Rahmen.
 *
 * Nie interaktiv: die Orte sind auf ~38 m genau, freies Hineinzoomen wuerde eine
 * Genauigkeit vorspiegeln, die die Daten nicht haben.
 */
import { ref, watch, onMounted, onActivated, onDeactivated, onUnmounted, nextTick } from 'vue'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { tripMapView, tripLine } from '../../utils/tripMap'

const props = defineProps<{
  startGeohash: string | null | undefined
  endGeohash: string | null | undefined
  /**
   * Vom Backend berechnete Strassenverbindung (encodierte Polyline) - ein Routenvorschlag
   * zwischen Start- und Zielgegend, NICHT die gefahrene Strecke.
   */
  routePolyline?: string | null
  /**
   * Die tatsaechlich gefahrene Linie aus den Location-Signalen des Fahrzeugs. Wenn vorhanden,
   * hat sie Vorrang vor jeder Berechnung - sie zeigt, wo das Auto wirklich war.
   */
  tracePolyline?: string | null
  /** Woher `routePolyline` stammt - 'MATCHED' heisst: aus der gefahrenen Spur gerechnet. */
  routeKind?: string | null
  /**
   * `backdrop` (Vorgabe): Hintergrund einer Kachel - gedaempft, ohne eigene Bedeutung.
   * `panel`: eigenstaendige Karte mit Rahmen, die den Weg der Fahrt zeigt.
   */
  variant?: 'backdrop' | 'panel'
  /** Textalternative im panel-Modus; als backdrop bleibt die Karte fuer Screenreader unsichtbar. */
  label?: string
}>()

const isPanel = () => props.variant === 'panel'

const container = ref<HTMLDivElement | null>(null)
const visible = ref(false)
let map: L.Map | null = null
let resizeObserver: ResizeObserver | null = null

const OSM_CREDIT_LINK =
  '&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener">OpenStreetMap</a>'

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

/**
 * Jeder Aufruf zieht ein Token. Waehrend des `nextTick` kann ein weiterer Aufruf dazwischen
 * kommen (Prop-Wechsel, Reaktivierung, ResizeObserver) - dann tritt der aeltere Lauf ab,
 * statt die frisch gebaute Karte des neueren wieder abzureissen.
 */
let renderToken = 0

async function render() {
  const token = ++renderToken
  await nextTick()
  if (token !== renderToken) return
  teardown()

  const view = tripMapView(props.startGeohash, props.endGeohash)
  const line = tripLine(props.tracePolyline, props.routePolyline, props.routeKind)
  // Beide gerechneten Formen stammen vom Router - seine Nennung haengt an ihnen, nicht an
  // der rohen Spur, die aus dem Fahrzeug kommt.
  const routed = line?.source === 'sketch' || line?.source === 'matched'
  if (!view && !line) return
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
    // CC-BY-SA 4.0 verlangt die Nennung von openrouteservice, sobald deren Ergebnis
    // gezeigt wird - deshalb haengt der Zusatz an der Route und nicht an der Karte.
    // Die gefahrene Spur stammt aus dem Fahrzeug, sie braucht ihn nicht.
    // Als eigenstaendige Karte verlangen die OSM-Guidelines einen Link auf die
    // Lizenzseite; als gedaempfter Kachelhintergrund, den niemand anklicken kann,
    // bleibt es beim Namen.
    attribution: routed
      ? (isPanel()
          ? `${OSM_CREDIT_LINK} | Route &copy; <a href="https://openrouteservice.org/" target="_blank" rel="noopener">openrouteservice by HeiGIT</a>`
          : '&copy; OpenStreetMap | Route &copy; openrouteservice by HeiGIT')
      : (isPanel() ? OSM_CREDIT_LINK : '&copy; OpenStreetMap'),
    maxZoom: 17,
    crossOrigin: true,
  }).addTo(map)

  const points: L.LatLngExpression[] = []
  const addArea = (point: { lat: number; lon: number }, color: string, radius: number) => {
    const latLng: L.LatLngExpression = [point.lat, point.lon]
    points.push(latLng)
    L.circle(latLng, {
      radius,
      color,
      weight: 1,
      opacity: 0.55,
      fillColor: color,
      fillOpacity: 0.18,
      interactive: false,
    }).addTo(map!)
  }

  if (view?.start) addArea(view.start, ACCENT, view.cellRadiusMeters)
  if (view?.end) addArea(view.end, ACCENT_END, view.cellRadiusMeters)

  // Gestrichelt heisst "genaeherter Verlauf": die Luftlinie verbindet nur zwei Punkte, und
  // die gerechnete Route folgt zwar Strassen, aber nicht zwingend den tatsaechlich
  // gefahrenen. Durchgezogen ist allein die aufgezeichnete Spur.
  // Zwei Linien uebereinander: eine helle Kontur traegt den Strich ueber jeden
  // Kartenuntergrund, ohne dass er kraeftiger werden muss als die Daten darueber.
  const drawn = line?.points ?? (points.length === 2 ? points : null)
  if (drawn) {
    // Gestrichelt bleibt allein die Skizze: sie verbindet zwei Naeherungen. Die gematchte
    // Linie folgt der gemessenen Fahrt und wird wie die rohe Spur durchgezogen.
    const dashArray = line && line.source !== 'sketch' ? undefined : '6 6'
    L.polyline(drawn, { color: '#ffffff', weight: 5, opacity: 0.5, dashArray, interactive: false }).addTo(map)
    L.polyline(drawn, { color: ACCENT, weight: 2.5, opacity: 1, dashArray, interactive: false }).addTo(map)
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

  // Start- und Zielflaeche gehoeren immer in den Ausschnitt: die Positionen kommen im
  // Minutentakt, der erste und letzte Stuetzpunkt der Spur liegen also ein Stueck hinter
  // bzw. vor den beiden Enden. Ohne sie im Rahmen wuerden die Marker abgeschnitten.
  // Eine Linie fuellt die Kachel selbst aus, zwei blosse Punkte brauchen Luft um sich herum.
  const bounds = L.latLngBounds([...(line?.points ?? []), ...points]).pad(line ? 0.15 : 0.35)
  map.fitBounds(bounds, { animate: false })
  // Praezisions-Deckel: die Orte sind auf ~38 m genau, die Karte darf nicht genauer wirken.
  // Er greift bei einem einzelnen Punkt ebenso wie bei einer kurzen Fahrt, die sonst
  // hausnummerngenau herangezoomt wuerde.
  if (map.getZoom() > 13) map.setZoom(13)

  visible.value = true
}

function teardown() {
  resizeObserver?.disconnect()
  resizeObserver = null
  map?.remove()
  map = null
  visible.value = false
}

watch(
  () => [props.startGeohash, props.endGeohash, props.routePolyline, props.tracePolyline, props.routeKind],
  render,
  { flush: 'post' },
)
onMounted(render)

// Die Ansicht haengt in einem <KeepAlive>: beim Wegnavigieren loest sich das DOM (Groesse
// null), waehrend Leaflets Resize-Listener gebunden bliebe und ins Leere zeichnen wuerde -
// deshalb raeumt onDeactivated die Karte ab und onActivated baut sie neu auf. Faellt das
// mit dem ersten Mount zusammen, faengt der Render-Token den doppelten Aufbau ab; ein
// Zaehler an dieser Stelle wuerde dagegen die erste echte Reaktivierung verschlucken -
// und Karten, die erst spaeter aufgeklappt werden, blieben danach leer.
onActivated(render)
onDeactivated(teardown)
onUnmounted(teardown)
</script>

<template>
  <div
    class="activity-map overflow-hidden pointer-events-none"
    :class="isPanel() ? 'panel absolute inset-0' : 'absolute inset-0'"
    :role="isPanel() ? 'img' : undefined"
    :aria-label="isPanel() ? label : undefined"
    :aria-hidden="isPanel() ? undefined : 'true'"
  >
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
         Relativzeit steht. Im panel-Modus steht kein Text darauf - dort waere er nur Nebel. -->
    <div v-if="!isPanel()" class="absolute inset-0 bg-gradient-to-r from-white/80 via-white/70 to-white/60
                dark:from-gray-900/70 dark:via-gray-900/55 dark:to-gray-900/45"></div>
  </div>
</template>

<style scoped>
:deep(.leaflet-container) {
  background: transparent;
}
/* Der Link in der Attribution braucht Klicks, obwohl der Container sie sonst durchreicht. */
.activity-map.panel :deep(.leaflet-control-attribution) {
  pointer-events: auto;
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
.activity-map:not(.panel) .leaflet-tile {
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
.dark .activity-map:not(.panel) .leaflet-tile {
  filter: grayscale(0.6) saturate(0.55) brightness(0.45) contrast(1) blur(0.5px);
}
</style>
