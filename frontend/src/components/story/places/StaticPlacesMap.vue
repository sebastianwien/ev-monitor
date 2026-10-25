<script setup lang="ts">
import { computed } from 'vue'
import geohash from 'ngeohash'
import { HomeIcon } from '@heroicons/vue/24/outline'
import { staticMapLayout } from '../../../utils/staticMap'
import { pinSize, type PlaceClass, type PlaceRow } from '../../../composables/storyPlaces'
import { PLACE_FILL, PIN_EDGE } from './placeStyle'

/**
 * Stummes Kartenbild der Ladeorte für den Teaser: echte OSM-Kacheln als Bilder, Punkte darüber, kein Leaflet.
 * Das Bild ist immer 480 x 180 gerechnet und mittig beschnitten; der Rand hält die Punkte im sichtbaren Teil.
 */
const props = defineProps<{ rows: PlaceRow[] }>()

const W = 480
const H = 180
/** Höchstens Zoom 12: eine Gegend, keine Straße. Die Orte sind Zellen, keine Adressen. */
const MAX_ZOOM = 12
const Z: Partial<Record<PlaceClass, number>> = { high: 3, home: 2 }

const layout = computed(() => staticMapLayout(props.rows.map(r => {
  const { latitude, longitude } = geohash.decode(r.place.geohash)
  return { lat: latitude, lon: longitude }
}), W, H, { maxZoom: MAX_ZOOM, padding: 36 }))

const pins = computed(() => {
  const l = layout.value
  if (!l) return []
  return props.rows
    .map((r, i) => ({ r, x: l.points[i].x, y: l.points[i].y, size: Math.round(pinSize(r.place.kwh) * 0.75) }))
    .sort((a, b) => (Z[a.r.cls] ?? 0) - (Z[b.r.cls] ?? 0))
})
</script>

<template>
  <div v-if="layout" data-testid="places-teaser-map" class="relative overflow-hidden bg-gray-100 dark:bg-gray-900" aria-hidden="true">
    <div class="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2" :style="{ width: `${W}px`, height: `${H}px` }">
      <img
        v-for="tile in layout.tiles" :key="tile.url" :src="tile.url" alt="" decoding="async" draggable="false"
        class="places-teaser-tile absolute h-[256px] w-[256px] max-w-none select-none"
        :style="{ left: `${tile.left}px`, top: `${tile.top}px` }" />
      <span
        v-for="p in pins" :key="p.r.place.geohash"
        data-testid="places-teaser-pin"
        class="absolute grid place-items-center shadow-[2px_2px_0_0_#111827] dark:shadow-none"
        :class="[PIN_EDGE, PLACE_FILL[p.r.cls]]"
        :style="{ left: `${p.x - p.size / 2}px`, top: `${p.y - p.size / 2}px`, width: `${p.size}px`, height: `${p.size}px` }">
        <HomeIcon v-if="p.r.cls === 'home'" class="h-3/5 w-3/5" />
      </span>
    </div>
    <span class="absolute bottom-0.5 right-1 rounded-sm bg-white/70 px-1 text-[9px] text-gray-600 dark:bg-gray-900/70 dark:text-gray-400">&copy; OpenStreetMap</span>
  </div>
</template>

<style>
/* Wie die große Karte: in Farbe, dunkel etwas gedimmt. */
.dark .places-teaser-tile { filter: brightness(0.82); }
</style>
