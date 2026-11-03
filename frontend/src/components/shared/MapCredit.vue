<script setup lang="ts">
/**
 * Kartennennung unter dem Inhalt statt auf der Karte (Fahrt und Ladung bearbeiten): OSM mit Link auf die
 * Lizenzseite, wie die OSM-Guidelines es für eigenständige Karten verlangen; openrouteservice (CC-BY-SA) nur,
 * wenn die gezeigte Linie vom Router stammt.
 */
defineProps<{ routed?: boolean }>()
const link = 'underline decoration-gray-300 underline-offset-2 hover:text-gray-600 dark:decoration-gray-600 dark:hover:text-gray-300'
</script>

<template>
  <p data-testid="map-credit" class="text-center text-[10px] text-gray-400 dark:text-gray-500">
    © <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener" :class="link">OpenStreetMap</a>
    <template v-if="routed"> · Route © <a href="https://openrouteservice.org/" target="_blank" rel="noopener" :class="link">openrouteservice by HeiGIT</a></template>
  </p>
</template>
