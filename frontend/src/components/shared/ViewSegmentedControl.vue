<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useHaptic } from '../../composables/useHaptic'

/**
 * Tab-Umschalter zwischen verwandten Ansichten derselben Sache (Dashboard/Log-Feed im
 * CarContextLayout, Fahrzeuge/Ladekarten unter /cars, alle vier in der Workspace-Leiste).
 *
 * Als Knopfleiste: jeder Tab ist ein 3D-Knopf, der aktive ist eingedrueckt (btn-3d.active
 * aus dem Design-System - Schatten schrumpft, der Knopf sitzt tiefer). Der Zustand kommt
 * aus der Route.
 */
const props = defineProps<{
  tabs: { to: string; label: string }[]
}>()

const route = useRoute()
const { haptic } = useHaptic()

const activeIndex = computed(() => {
  const index = props.tabs.findIndex(tab => tab.to === route.path)
  return index === -1 ? 0 : index
})

// Der Rahmen traegt die Knopffarbe: die Kontur kommt vom Schlagschatten, ein zweiter
// grauer Rand daneben laese sich als doppelter Schatten. Er bleibt nur, damit aktiv und
// inaktiv dieselben Masse haben - und faerbt sich beim Hovern indigo.
const tabClass = (active: boolean) =>
  `btn-3d tab-btn flex-1 text-center text-sm font-semibold py-2 px-3 rounded-sm border-2 whitespace-nowrap ${
    active
      ? 'active is-active bg-indigo-600 border-indigo-600 text-white'
      : 'bg-white dark:bg-gray-700 border-white dark:border-gray-700 text-gray-600 dark:text-gray-300 hover:border-indigo-400 dark:hover:border-indigo-500'
  }`
</script>

<template>
  <!-- Keine Grundplatte: sie laege als zweite graue Flaeche direkt ueber der grauen
       Inhaltskarte und wirkte wie ein Doppelrahmen. Dass die Knoepfe zusammengehoeren,
       sagen gleiche Groesse und der enge, gleichmaessige Abstand. -->
  <div class="flex gap-2 select-none" role="tablist">
    <router-link
      v-for="(tab, index) in tabs"
      :key="tab.to"
      :to="tab.to"
      role="tab"
      :aria-selected="index === activeIndex"
      :class="tabClass(index === activeIndex)"
      @click="haptic()">
      {{ tab.label }}
    </router-link>
  </div>
</template>

<style scoped>
/* Derselbe Schlagschatten wie die Inhaltskarte darunter - die Leiste liegt auf deren
   Ebene, nicht im Inhalt. Der hellgraue 2px-Schatten der Buttons *im* Inhalt waere hier
   die falsche Referenz und wirkte neben der Karte blass. */
.tab-btn {
  --btn-shadow-color: rgba(0, 0, 0, 0.30);
  box-shadow: 4px 4px 0 0 var(--btn-shadow-color);
  transition: transform 0.08s ease, box-shadow 0.08s ease, background-color 0.15s ease, border-color 0.15s ease;
}
:global(.dark) .tab-btn {
  --btn-shadow-color: rgba(255, 255, 255, 0.30);
}

/* Eingedrueckt: der Schatten verschwindet, der Knopf sitzt an dessen Stelle. */
.tab-btn:active,
.tab-btn.is-active {
  box-shadow: 0 0 0 0 var(--btn-shadow-color);
  transform: translate(4px, 4px);
}
</style>
