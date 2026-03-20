<script setup lang="ts">
import { ref, onMounted, watch, nextTick } from 'vue'
import L from 'leaflet'
import 'leaflet.markercluster'
import geohash from 'ngeohash'
import 'leaflet/dist/leaflet.css'
import 'leaflet.markercluster/dist/MarkerCluster.css'
import 'leaflet.markercluster/dist/MarkerCluster.Default.css'
import { MapIcon, MapPinIcon } from '@heroicons/vue/24/outline'
import api from '../api/axios'

// Import leaflet.heat with proper typing
import 'leaflet.heat'
declare module 'leaflet' {
  function heatLayer(latlngs: Array<[number, number, number]>, options?: any): L.Layer
}

// Fix Leaflet default icon paths (Vite bundler issue)
import icon from 'leaflet/dist/images/marker-icon.png'
import iconShadow from 'leaflet/dist/images/marker-shadow.png'
const DefaultIcon = L.icon({
  iconUrl: icon,
  shadowUrl: iconShadow,
  iconSize: [25, 41],
  iconAnchor: [12, 41]
})
L.Marker.prototype.options.icon = DefaultIcon

interface Props {
  carId: string | null
  timeRange?: string
}

const props = defineProps<Props>()

const mapContainer = ref<HTMLDivElement | null>(null)
let map: L.Map | null = null
let heatLayer: L.HeatLayer | null = null
let markerClusterGroup: L.MarkerClusterGroup | null = null

const loading = ref(false)
const error = ref<string | null>(null)
const chargeCount = ref(0)
const viewMode = ref<'heatmap' | 'markers' | 'both'>('both')

interface EvLog {
  id: string
  kwhCharged: number
  costEur: number
  geohash: string | null
  loggedAt: string
  chargeDurationMinutes: number
}

interface SessionGroup {
  id: string
  totalKwhCharged: number
  costEur: number | null
  geohash: string | null
  sessionStart: string
  totalDurationMinutes: number | null
}

// Fetch logs + groups for the selected car and combine geohash data
const fetchLogs = async () => {
  if (!props.carId) return []

  try {
    loading.value = true
    error.value = null
    const [logsResponse, groupsResponse] = await Promise.all([
      api.get(`/logs?carId=${props.carId}`),
      api.get(`/logs/groups?carId=${props.carId}`).catch(() => ({ data: [] }))
    ])

    const logs: EvLog[] = logsResponse.data
    const groups: SessionGroup[] = groupsResponse.data

    // Convert groups to EvLog shape so they appear on the heatmap too
    const groupsAsLogs: EvLog[] = groups
      .filter((g: SessionGroup) => g.geohash)
      .map((g: SessionGroup) => ({
        id: g.id,
        kwhCharged: g.totalKwhCharged,
        costEur: g.costEur ?? 0,
        geohash: g.geohash,
        loggedAt: g.sessionStart,
        chargeDurationMinutes: g.totalDurationMinutes ?? 0
      }))

    return [...logs, ...groupsAsLogs]
  } catch (err: any) {
    error.value = err.response?.data?.message || 'Fehler beim Laden der Logs'
    return []
  } finally {
    loading.value = false
  }
}

// Initialize map
const initMap = () => {
  if (!mapContainer.value || map) return

  map = L.map(mapContainer.value, {
    preferCanvas: true  // Better performance for many markers
  }).setView([51.1657, 10.4515], 6) // Germany center

  // Add OpenStreetMap tiles
  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
    maxZoom: 19
  }).addTo(map)

  // Initialize cluster group
  markerClusterGroup = L.markerClusterGroup({
    maxClusterRadius: 50,
    spiderfyOnMaxZoom: true,
    showCoverageOnHover: false,
    zoomToBoundsOnClick: true,
    iconCreateFunction: (cluster) => {
      const count = cluster.getChildCount()
      let size = 'small'
      if (count > 10) size = 'medium'
      if (count > 25) size = 'large'

      return L.divIcon({
        html: `<div><span>${count}</span></div>`,
        className: `marker-cluster marker-cluster-${size}`,
        iconSize: L.point(40, 40)
      })
    }
  })
}

// Render charging locations on map
const renderCharges = async () => {
  // If map doesn't exist yet, try to initialize it now
  if (!map) {
    await nextTick()
    initMap()
    if (!map) return
  }

  // Clear existing layers
  if (heatLayer) {
    map.removeLayer(heatLayer)
    heatLayer = null
  }
  if (markerClusterGroup) {
    markerClusterGroup.clearLayers()
    map.removeLayer(markerClusterGroup)
  }

  const logs = await fetchLogs()
  const logsWithGeohash = logs.filter(log => log.geohash)

  console.log('📊 ChargingHeatMap Debug:', {
    totalLogs: logs.length,
    logsWithGeohash: logsWithGeohash.length,
    viewMode: viewMode.value
  })

  chargeCount.value = logsWithGeohash.length

  if (logsWithGeohash.length === 0) {
    console.warn('⚠️ Keine Logs mit Geohash gefunden')
    return
  }

  // Prepare data structures
  const bounds: [number, number][] = []
  const heatPoints: [number, number, number][] = []
  const markers: L.Layer[] = []

  logsWithGeohash.forEach(log => {
    if (!log.geohash) return

    try {
      const { latitude, longitude } = geohash.decode(log.geohash)
      bounds.push([latitude, longitude])

      // Heatmap intensity based on kWh (normalized 0-1)
      const intensity = Math.min(log.kwhCharged / 100, 1)
      heatPoints.push([latitude, longitude, intensity])

      // Create marker with color coding
      const kwhNormalized = Math.min(log.kwhCharged / 80, 1)
      const color = `hsl(${120 - kwhNormalized * 120}, 85%, 50%)` // Green → Red

      const marker = L.circleMarker([latitude, longitude], {
        radius: 9,
        fillColor: color,
        color: '#fff',
        weight: 2,
        opacity: 1,
        fillOpacity: 0.8
      })

      // Enhanced popup with better formatting
      const date = new Date(log.loggedAt).toLocaleDateString('de-DE', {
        day: '2-digit',
        month: 'short',
        year: 'numeric'
      })
      const time = new Date(log.loggedAt).toLocaleTimeString('de-DE', {
        hour: '2-digit',
        minute: '2-digit'
      })
      const kwh = log.kwhCharged ?? 0
      const cost = log.costEur ?? 0
      const costPerKwh = kwh > 0 ? (cost / kwh).toFixed(2) : '0.00'

      marker.bindPopup(`
        <div style="font-family: system-ui; font-size: 13px; min-width: 180px;">
          <div style="font-weight: 700; font-size: 15px; margin-bottom: 6px; color: #1f2937;">
            ⚡ ${kwh.toFixed(1)} kWh
          </div>
          <div style="display: flex; flex-direction: column; gap: 4px; color: #4b5563;">
            <div>💰 €${cost.toFixed(2)} <span style="color: #9ca3af;">(€${costPerKwh}/kWh)</span></div>
            <div>⏱️ ${log.chargeDurationMinutes} Minuten</div>
            <div>📅 ${date} · ${time}</div>
          </div>
        </div>
      `, {
        maxWidth: 250,
        className: 'custom-popup'
      })

      markers.push(marker)
    } catch (err) {
      console.warn('Failed to decode geohash:', log.geohash, err)
    }
  })

  // Add layers based on view mode
  if (viewMode.value === 'heatmap' || viewMode.value === 'both') {
    console.log('🔥 Creating heatmap with', heatPoints.length, 'points')
    console.log('Sample heatPoint:', heatPoints[0])

    try {
      // @ts-ignore - leaflet.heat types
      heatLayer = L.heatLayer(heatPoints, {
        radius: 35,           // Größerer Radius (war 25)
        blur: 25,             // Mehr Blur für weichere Übergänge (war 20)
        maxZoom: 13,          // Niedrigerer maxZoom = sichtbarer bei zoom out (war 17)
        max: 0.5,             // Niedrigerer max = intensivere Farben (war 1.0)
        minOpacity: 0.4,      // Minimum Opacity für bessere Sichtbarkeit
        gradient: {
          0.0: '#00ff00',     // Grün
          0.3: '#ffff00',     // Gelb
          0.5: '#ff9900',     // Orange
          0.7: '#ff4400',     // Rot-Orange
          1.0: '#ff0000'      // Rot
        }
      })

      if (!heatLayer) {
        console.error('❌ L.heatLayer returned null/undefined!')
      } else {
        console.log('✅ Heatmap layer created, adding to map')
        map.addLayer(heatLayer)
        console.log('✅ Heatmap layer added to map')
      }
    } catch (err) {
      console.error('❌ Error creating heatmap:', err)
    }
  }

  if (viewMode.value === 'markers' || viewMode.value === 'both') {
    if (markerClusterGroup) {
      markers.forEach(marker => markerClusterGroup!.addLayer(marker))
      map.addLayer(markerClusterGroup)
    }
  }

  // Fit map to show all markers
  if (bounds.length > 0) {
    map.fitBounds(bounds, { padding: [50, 50] })
  }
}

// Toggle view mode
const setViewMode = (mode: 'heatmap' | 'markers' | 'both') => {
  viewMode.value = mode
  renderCharges()
}

// Watch for car or time range changes
watch(() => [props.carId, props.timeRange], async () => {
  if (!props.carId) return

  // Wait for DOM to update (in case the container just appeared)
  await nextTick()
  await nextTick() // Double nextTick to be extra safe

  // Try to initialize map if it doesn't exist yet
  if (!map && mapContainer.value) {
    initMap()
  }

  if (props.carId && map) {
    await renderCharges()
  }
}, {
  immediate: false,
  flush: 'post' // Run after DOM updates
})

onMounted(async () => {
  await nextTick()
  initMap()
  if (props.carId) {
    await renderCharges()
  }
})
</script>

<template>
  <div class="relative isolate">
    <div v-if="loading" class="absolute inset-0 flex items-center justify-center bg-white bg-opacity-90 z-10 rounded-lg backdrop-blur-sm">
      <div class="text-center">
        <div class="inline-block animate-spin rounded-full h-10 w-10 border-b-2 border-indigo-600 mb-3"></div>
        <p class="text-sm text-gray-600 font-medium">Lade Karte...</p>
      </div>
    </div>

    <div v-if="error" class="p-4 bg-red-50 dark:bg-red-900/30 border border-red-200 dark:border-red-700 text-red-700 dark:text-red-300 rounded-md text-sm mb-4">
      {{ error }}
    </div>

    <!-- Empty State: No Car Selected -->
    <div v-if="!carId" class="absolute inset-0 flex items-center justify-center bg-gradient-to-br from-gray-50 to-gray-100 z-10 rounded-lg">
      <div class="p-6 md:p-10 text-center">
        <MapIcon class="h-20 w-20 mx-auto mb-4 text-gray-300" />
        <p class="text-lg font-semibold text-gray-700 mb-2">Kein Fahrzeug ausgewählt</p>
        <p class="text-sm text-gray-500">Wähle ein Fahrzeug aus um die Lade-Karte anzuzeigen.</p>
      </div>
    </div>

    <!-- Empty State: No Locations -->
    <div v-else-if="chargeCount === 0 && !loading" class="absolute inset-0 flex items-center justify-center bg-gradient-to-br from-blue-50 to-indigo-50 z-10 rounded-lg">
      <div class="p-6 md:p-10 text-center max-w-sm">
        <MapPinIcon class="h-20 w-20 mx-auto mb-4 text-indigo-300" />
        <p class="text-lg font-semibold text-gray-800 mb-2">Noch keine Standorte erfasst</p>
        <p class="text-sm text-gray-600 mb-4">
          Füge Standorte zu deinen Ladevorgängen hinzu, um sie hier auf der Karte zu sehen!
        </p>
        <div class="inline-flex items-center gap-2 px-4 py-2 bg-blue-100 border border-blue-200 rounded-lg text-xs text-blue-800">
          <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          <span>Standorte werden auf 5km anonymisiert (DSGVO)</span>
        </div>
      </div>
    </div>

    <!-- Map container (always rendered) -->
    <div>
      <!-- Controls (only shown when we have data) -->
      <div v-if="chargeCount > 0" class="mb-4 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 p-3 md:p-4 bg-gradient-to-r from-indigo-50 to-purple-50 dark:from-indigo-900/30 dark:to-purple-900/20 md:rounded-lg border border-indigo-100 dark:border-indigo-800">
        <div class="flex items-center gap-2">
          <MapPinIcon class="h-5 w-5 text-indigo-600 dark:text-indigo-400" />
          <span class="text-sm font-semibold text-gray-700 dark:text-gray-200">
            <strong class="text-indigo-600 dark:text-indigo-400">{{ chargeCount }}</strong> Ladevorgänge
          </span>
        </div>

        <!-- View Mode Toggle -->
        <div class="flex items-center gap-2">
          <span class="text-xs font-medium text-gray-600 dark:text-gray-400 mr-2">Ansicht:</span>
          <button
            @click="setViewMode('heatmap')"
            :class="[
              'px-3 py-1.5 rounded-md text-xs font-medium transition-all duration-200',
              viewMode === 'heatmap'
                ? 'bg-indigo-600 text-white shadow-md'
                : 'bg-white dark:bg-gray-700 text-gray-700 dark:text-gray-300 border border-gray-300 dark:border-gray-600 hover:bg-gray-50 dark:hover:bg-gray-600'
            ]">
            Heatmap
          </button>
          <button
            @click="setViewMode('markers')"
            :class="[
              'px-3 py-1.5 rounded-md text-xs font-medium transition-all duration-200',
              viewMode === 'markers'
                ? 'bg-indigo-600 text-white shadow-md'
                : 'bg-white dark:bg-gray-700 text-gray-700 dark:text-gray-300 border border-gray-300 dark:border-gray-600 hover:bg-gray-50 dark:hover:bg-gray-600'
            ]">
            Marker
          </button>
          <button
            @click="setViewMode('both')"
            :class="[
              'px-3 py-1.5 rounded-md text-xs font-medium transition-all duration-200',
              viewMode === 'both'
                ? 'bg-indigo-600 text-white shadow-md'
                : 'bg-white dark:bg-gray-700 text-gray-700 dark:text-gray-300 border border-gray-300 dark:border-gray-600 hover:bg-gray-50 dark:hover:bg-gray-600'
            ]">
            Beides
          </button>
        </div>
      </div>

      <!-- Legend (only shown when we have data) -->
      <div v-if="chargeCount > 0" class="mb-3 flex flex-wrap items-center gap-4 text-xs text-gray-600 dark:text-gray-400 px-2">
        <span class="font-semibold text-gray-700 dark:text-gray-300">Legende:</span>
        <span class="flex items-center gap-1.5">
          <span class="inline-block w-4 h-4 rounded-full bg-green-500 shadow-sm"></span>
          Wenig kWh
        </span>
        <span class="flex items-center gap-1.5">
          <span class="inline-block w-4 h-4 rounded-full bg-yellow-500 shadow-sm"></span>
          Mittel
        </span>
        <span class="flex items-center gap-1.5">
          <span class="inline-block w-4 h-4 rounded-full bg-red-500 shadow-sm"></span>
          Viel kWh
        </span>
        <span class="text-gray-400 ml-2">• Klick auf Marker für Details</span>
      </div>

      <div ref="mapContainer" class="w-full h-[400px] sm:h-[550px] md:rounded-lg border md:border-2 border-gray-300 md:shadow-lg transition-all duration-300 hover:shadow-xl"></div>
    </div>
  </div>
</template>

<style scoped>
/* Custom marker cluster styling */
:deep(.marker-cluster) {
  background-clip: padding-box;
  border-radius: 50%;
  font-weight: 700;
  text-align: center;
  display: flex;
  align-items: center;
  justify-content: center;
}

:deep(.marker-cluster div) {
  width: 30px;
  height: 30px;
  margin-left: 5px;
  margin-top: 5px;
  text-align: center;
  border-radius: 50%;
  font-size: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
}

:deep(.marker-cluster-small) {
  background-color: rgba(79, 70, 229, 0.6);
}

:deep(.marker-cluster-small div) {
  background-color: rgba(79, 70, 229, 0.8);
}

:deep(.marker-cluster-medium) {
  background-color: rgba(124, 58, 237, 0.6);
}

:deep(.marker-cluster-medium div) {
  background-color: rgba(124, 58, 237, 0.8);
}

:deep(.marker-cluster-large) {
  background-color: rgba(168, 85, 247, 0.6);
}

:deep(.marker-cluster-large div) {
  background-color: rgba(168, 85, 247, 0.8);
}

/* Enhanced popup styling */
:deep(.leaflet-popup-content-wrapper) {
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

:deep(.leaflet-popup-content) {
  margin: 12px;
}

:deep(.leaflet-popup-tip) {
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.1);
}
</style>
