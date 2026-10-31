<script setup lang="ts">
import { computed } from 'vue'
import { BoltIcon } from '@heroicons/vue/24/outline'
import { thumbTiles } from './minimapTiles'

/**
 * Kartenbild einer Säule in der Ortswahl: genaue Lage laut Register, damit der Nutzer die Säule wiedererkennt.
 * Reine Bilder statt Leaflet - fünf Karteninstanzen in einer Liste wären zu schwer. Rein zur Orientierung,
 * Name und Adresse stehen daneben als Text (aria-hidden). Der Blitz in der Ecke sagt den Ortstyp wie die Icon-Box.
 */
const SIZE = 56
const props = defineProps<{ lat: number; lon: number }>()
const tiles = computed(() => thumbTiles(props.lat, props.lon, SIZE))
const box = { width: `${SIZE}px`, height: `${SIZE}px` }
</script>

<template>
  <span data-testid="station-thumb" aria-hidden="true" :style="box"
    class="thumb relative block flex-shrink-0 overflow-hidden rounded-lg bg-gray-200 dark:bg-gray-700">
    <!-- lazy: Kacheln zugeklappter Säulen ("N weitere") lädt der Browser erst beim Aufklappen -->
    <img v-for="t in tiles" :key="t.url" :src="t.url" alt="" loading="lazy" decoding="async" draggable="false"
      class="absolute max-w-none select-none" :style="{ left: `${t.left}px`, top: `${t.top}px`, width: `${t.size}px`, height: `${t.size}px` }"
      @error="e => ((e.target as HTMLImageElement).style.visibility = 'hidden')" />
    <!-- Pin in der Textfarbe der App-CI wie auf der Minimap, mit weißem Ring für Kontrast zur Karte -->
    <span class="absolute left-1/2 top-1/2 h-3 w-3 -translate-x-1/2 -translate-y-1/2 rounded-full border-2 border-white bg-gray-900 dark:bg-gray-100 dark:border-gray-900" />
    <span class="absolute bottom-0.5 right-0.5 grid h-5 w-5 place-items-center rounded-md bg-amber-500 text-white">
      <BoltIcon class="h-3.5 w-3.5" />
    </span>
  </span>
</template>

<style scoped>
/* Wie die Minimap: hell die OSM-Kachel, dunkel invertierte Graustufen */
.dark .thumb img { filter: grayscale(1) invert(1) contrast(0.8) brightness(0.75); }
</style>
