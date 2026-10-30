<!--
  Wetter-Icons im Strich von Heroicons Outline (1,5). Regen, Schnee und Wind nach Tabler Icons
  "cloud-rain", "cloud-snow", "wind" (MIT, https://tabler.io/icons), Sonne nach Heroicons "sun".
  Farbe je Zustand: das Icon sagt den Typ, wie die Typ-Icons der Ortswahl.
-->
<script setup lang="ts">
import type { WeatherKind } from '../../utils/destinationForecast'

defineProps<{ kind: WeatherKind }>()

const CLOUD = 'M7 18a4.6 4.4 0 0 1 0 -9a5 4.5 0 0 1 11 2h1a3.5 3.5 0 0 1 0 7z'
/** Kleinere Wolke unten rechts, die Sonne schaut oben links hervor. */
const CLOUD_LOW = 'M9 20a3.6 3.4 0 0 1 0 -7a4 3.6 0 0 1 8.6 1.6h.9a2.7 2.7 0 0 1 0 5.4z'
/** Wolke etwas höher, darunter Platz für Tropfen. */
const CLOUD_HIGH = 'M9 16a3.6 3.4 0 0 1 0 -7a4 3.6 0 0 1 8.6 1.6h.9a2.7 2.7 0 0 1 0 5.4z'
const SMALL_SUN = 'M7 4.5a2.5 2.5 0 1 0 0 5a2.5 2.5 0 0 0 0 -5M7 1.5v.75M2 7h.75M3.5 3.5l.55.55M10.5 3.5l-.55.55'

const COLOR: Record<WeatherKind, string> = {
  sun: 'text-amber-500 dark:text-amber-400',
  partly: 'text-amber-500 dark:text-amber-400',
  cloudy: 'text-gray-500 dark:text-gray-400',
  rain: 'text-sky-600 dark:text-sky-400',
  showers: 'text-sky-600 dark:text-sky-400',
  snow: 'text-cyan-500 dark:text-cyan-300',
  wind: 'text-teal-600 dark:text-teal-400',
}
</script>

<template>
  <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" :class="COLOR[kind]" :data-weather="kind">
    <template v-if="kind === 'sun'">
      <circle cx="12" cy="12" r="3.75" />
      <path d="M12 3v2.25m6.364.386-1.591 1.591M21 12h-2.25m-.386 6.364-1.591-1.591M12 18.75V21m-4.773-4.227-1.591 1.591M5.25 12H3m4.227-4.773L5.636 5.636" />
    </template>
    <template v-else-if="kind === 'partly'">
      <path :d="SMALL_SUN" />
      <path :d="CLOUD_LOW" class="text-gray-500 dark:text-gray-400" stroke="currentColor" />
    </template>
    <path v-else-if="kind === 'cloudy'" :d="CLOUD" />
    <template v-else-if="kind === 'rain'">
      <path d="M7 18a4.6 4.4 0 0 1 0 -9a5 4.5 0 0 1 11 2h1a3.5 3.5 0 0 1 0 7" />
      <path d="M11 13v2m0 3v2m4 -5v2m0 3v2" />
    </template>
    <template v-else-if="kind === 'showers'">
      <path :d="SMALL_SUN" class="text-amber-500 dark:text-amber-400" stroke="currentColor" />
      <path :d="CLOUD_HIGH" />
      <path d="M10.5 18.5v2m3.5 -2v2m3.5 -2v2" />
    </template>
    <template v-else-if="kind === 'snow'">
      <path d="M7 18a4.6 4.4 0 0 1 0 -9a5 4.5 0 0 1 11 2h1a3.5 3.5 0 0 1 0 7" />
      <path d="M11 15v.01m0 3v.01m0 3v.01m4 -4v.01m0 3v.01" stroke-width="2.25" />
    </template>
    <template v-else>
      <path d="M5 8h8.5a2.5 2.5 0 1 0 -2.34 -3.24" />
      <path d="M3 12h15.5a2.5 2.5 0 1 1 -2.34 3.24" />
      <path d="M4 16h5.5a2.5 2.5 0 1 1 -2.34 3.24" />
    </template>
  </svg>
</template>
