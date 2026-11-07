<script setup lang="ts">
/**
 * Leaflet-Karte der Säulenkarte: ein Pin je Standort mit dem günstigsten Preis der gewählten
 * Ladeart, weit herausgezoomt zu Gruppen zusammengefasst, dazu der eigene Standort als Punkt.
 * Pins sind Knöpfe (Tastatur: Tab, Enter) mit "<Betreiber>, <Preis> je kWh".
 *
 * Marker werden beim Nachladen wiederverwendet: neue kommen dazu, weggefallene gehen, an den
 * übrigen ändert sich nur, was sich ändert (Preis beim Wechsel der Ladeart, Auswahl).
 */
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import 'leaflet.markercluster'
import 'leaflet.markercluster/dist/MarkerCluster.css'
import type { MapStation } from '../../api/chargingStationService'
import { formatEur, type Bounds, type ChargeType, type LatLon } from '../../utils/stationMap'

const props = defineProps<{
  /** Schon nach Ladeart gefiltert */
  stations: MapStation[]
  type: ChargeType
  selectedId: string | null
  /** Eigener Standort, nur auf dem Gerät */
  me: LatLon | null
  initialCenter: LatLon
  initialZoom: number
}>()
const emit = defineEmits<{
  moved: [view: { bounds: Bounds; center: LatLon; zoom: number }]
  select: [id: string]
}>()

const { t, locale } = useI18n()
const container = ref<HTMLDivElement | null>(null)
let map: L.Map | null = null
let cluster: L.MarkerClusterGroup | null = null
let meMarker: L.Marker | null = null
let resizeObserver: ResizeObserver | null = null
/** Marker je Säulen-ID und der Text, mit dem er gezeichnet ist */
const markers = new Map<string, { marker: L.Marker; html: string }>()

const MAX_ZOOM = 18
/** Ab Straßenzoom steht jeder Standort für sich */
const UNCLUSTER_ZOOM = 15
const OSM = '&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener">OpenStreetMap</a>'
// Heroicons 24 Outline "bolt": Pin ohne Preis zeigt nur, dass hier eine Säule steht
const BOLT = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16" aria-hidden="true"><path stroke-linecap="round" stroke-linejoin="round" d="m3.75 13.5 10.5-11.25L12 10.5h8.25L9.75 21.75 12 13.5H3.75Z"/></svg>'

const reducedMotion = () => typeof window !== 'undefined' && !!window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
const priceOf = (s: MapStation) => (props.type === 'DC' ? s.priceDc : s.priceAc)?.pricePerKwh ?? null

function pinHtml(s: MapStation): string {
  const p = priceOf(s)
  const label = p == null ? BOLT : formatEur(p, locale.value)
  return `<span class="st-pin${p == null ? ' st-pin-plain' : ''}">${label}</span>`
}

function ariaLabel(s: MapStation): string {
  const p = priceOf(s)
  return p == null ? s.name : t('stations.pin_aria', { name: s.name, price: formatEur(p, locale.value) })
}

function icon(html: string): L.DivIcon {
  // Anker unten Mitte: die Spitze der Kapsel zeigt auf den Standort
  return L.divIcon({ html, className: 'st-pin-wrap', iconSize: undefined, iconAnchor: [0, 0] })
}

/** Leaflet baut das Element bei jedem setIcon neu: Beschriftung und Auswahl danach wieder setzen. */
function decorate(id: string) {
  const entry = markers.get(id)
  const s = props.stations.find(x => x.id === id)
  const el = entry?.marker.getElement()
  if (!el || !s) return
  el.setAttribute('aria-label', ariaLabel(s))
  el.setAttribute('data-station-id', id)
  el.querySelector('.st-pin')?.classList.toggle('is-selected', props.selectedId === id)
  el.querySelector('.st-pin')?.classList.toggle('ci-selected', props.selectedId === id)
}

function sync() {
  if (!map || !cluster) return
  const next = new Map(props.stations.map(s => [s.id, s]))
  const gone: L.Marker[] = []
  for (const [id, entry] of markers) {
    if (!next.has(id)) { gone.push(entry.marker); markers.delete(id) }
  }
  if (gone.length) cluster.removeLayers(gone)
  const added: L.Marker[] = []
  for (const s of props.stations) {
    const html = pinHtml(s)
    const entry = markers.get(s.id)
    if (!entry) {
      const marker = L.marker([s.latitude, s.longitude], { icon: icon(html), keyboard: true, riseOnHover: true })
      marker.on('click', () => emit('select', s.id))
      marker.on('keydown', (e: L.LeafletKeyboardEvent) => {
        if (e.originalEvent.key === 'Enter' || e.originalEvent.key === ' ') { e.originalEvent.preventDefault(); emit('select', s.id) }
      })
      marker.on('add', () => decorate(s.id))
      markers.set(s.id, { marker, html })
      added.push(marker)
    } else if (entry.html !== html) {
      entry.marker.setIcon(icon(html))
      entry.html = html
      decorate(s.id)
    }
  }
  if (added.length) cluster.addLayers(added)
}

function markSelected() {
  for (const [id, entry] of markers) {
    entry.marker.setZIndexOffset(props.selectedId === id ? 1000 : 0)
    decorate(id)
  }
}

function drawMe() {
  if (!map) return
  if (!props.me) { meMarker?.remove(); meMarker = null; return }
  const ll: L.LatLngTuple = [props.me.lat, props.me.lon]
  if (meMarker) { meMarker.setLatLng(ll); return }
  meMarker = L.marker(ll, {
    icon: L.divIcon({ html: '<span class="st-me"></span>', className: 'st-pin-wrap', iconSize: [20, 20], iconAnchor: [10, 10] }),
    interactive: false, keyboard: false, zIndexOffset: -100,
  }).addTo(map)
  meMarker.getElement()?.setAttribute('aria-label', t('stations.me_aria'))
}

function emitMoved() {
  if (!map) return
  const b = map.getBounds()
  const c = map.getCenter()
  emit('moved', {
    bounds: { south: b.getSouth(), west: b.getWest(), north: b.getNorth(), east: b.getEast() },
    center: { lat: c.lat, lon: c.lng },
    zoom: map.getZoom(),
  })
}

function init() {
  if (map || !container.value || !container.value.offsetWidth || !container.value.offsetHeight) return
  map = L.map(container.value, {
    zoomControl: false, maxZoom: MAX_ZOOM, minZoom: 5, zoomSnap: 0.5,
    center: [props.initialCenter.lat, props.initialCenter.lon], zoom: props.initialZoom,
  })
  map.attributionControl.setPrefix(false)
  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    attribution: OSM, maxZoom: MAX_ZOOM, crossOrigin: true,
  }).addTo(map)
  cluster = L.markerClusterGroup({
    disableClusteringAtZoom: UNCLUSTER_ZOOM,
    maxClusterRadius: 48,
    showCoverageOnHover: false,
    spiderfyOnMaxZoom: false,
    iconCreateFunction: c => L.divIcon({
      html: `<span class="st-cluster">${c.getChildCount()}</span>`, className: 'st-pin-wrap', iconSize: [40, 40],
    }),
  }).addTo(map)
  map.on('moveend', emitMoved)
  sync()
  drawMe()
  emitMoved()
}

/** Hin zu einem Ort, gleitend außer bei "weniger Bewegung". */
function flyTo(center: LatLon, zoom?: number) {
  if (!map) return
  const z = zoom ?? map.getZoom()
  if (reducedMotion()) map.setView([center.lat, center.lon], z, { animate: false })
  else map.flyTo([center.lat, center.lon], z, { duration: 0.6 })
}

/** Der Pin soll über dem Sheet stehen: Karte so verschieben, dass er im freien oberen Teil liegt. */
function reveal(center: LatLon, coveredPx: number) {
  if (!map || !container.value) return
  const size = map.getSize()
  const target = map.latLngToContainerPoint([center.lat, center.lon])
  const freeMid = (size.y - coveredPx) / 2
  if (target.y > 0 && target.y < size.y - coveredPx - 24) return
  map.panBy([0, target.y - freeMid], { animate: !reducedMotion() })
}

defineExpose({ flyTo, reveal })

watch(() => [props.stations, props.type, locale.value], sync)
watch(() => props.selectedId, markSelected)
watch(() => props.me, drawMe)

onMounted(() => {
  init()
  if (typeof ResizeObserver === 'undefined' || !container.value) return
  resizeObserver = new ResizeObserver(() => { if (!map) init(); else map.invalidateSize() })
  resizeObserver.observe(container.value)
})
onUnmounted(() => {
  resizeObserver?.disconnect()
  resizeObserver = null
  map?.off()
  map?.remove()
  map = null
  cluster = null
  meMarker = null
  markers.clear()
})
</script>

<template>
  <div class="stations-map absolute inset-0" data-testid="stations-map">
    <div ref="container" class="absolute inset-0" />
  </div>
</template>

<style>
/* Ungescoped: Leaflet baut Pins und Gruppen selbst. .stations-map grenzt auf diese Karte ein. */
.stations-map .leaflet-container { background: #e5e7eb; font: inherit; }
.stations-map .st-pin-wrap { background: none; border: 0; width: auto !important; height: auto !important; }
.stations-map .st-pin-wrap:focus-visible { outline: none; }
.stations-map .st-pin {
  position: absolute; left: 0; bottom: 8px; transform: translateX(-50%);
  display: inline-flex; align-items: center; justify-content: center; min-height: 28px; min-width: 28px;
  padding: 3px 9px; border-radius: 9999px; border: 2px solid #fff;
  background: #111827; color: #fef3c7; font-weight: 700; font-size: 13px; line-height: 1;
  font-variant-numeric: tabular-nums; white-space: nowrap; cursor: pointer;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.35);
}
.stations-map .st-pin::after {
  content: ""; position: absolute; left: 50%; bottom: -8px; margin-left: -5px;
  border: 5px solid transparent; border-top-color: #111827;
}
.stations-map .st-pin-plain { padding: 3px; color: #fff; }
.stations-map .st-pin.is-selected { background: #4f46e5; color: #fff; z-index: 1; }
.stations-map .st-pin.is-selected::after { border-top-color: #4f46e5; }
.stations-map .st-pin-wrap:focus-visible .st-pin { outline: 3px solid #4f46e5; outline-offset: 2px; }
/* Tap-Ziel mindestens 44 px, auch wenn die Kapsel kleiner ist */
.stations-map .st-pin::before { content: ""; position: absolute; inset: -10px -6px; }
.stations-map .st-cluster {
  display: grid; place-items: center; width: 40px; height: 40px; border-radius: 9999px;
  background: #111827; color: #fff; border: 2px solid #fff; font-weight: 700; font-size: 14px;
  font-variant-numeric: tabular-nums; box-shadow: 0 2px 6px rgba(0, 0, 0, 0.35);
}
.stations-map .st-me {
  display: block; width: 18px; height: 18px; border-radius: 9999px; background: #111827;
  border: 3px solid #fff; box-shadow: 0 0 0 6px rgba(17, 24, 39, 0.15);
}
.stations-map .leaflet-control-attribution { font-size: 10px; }
</style>
