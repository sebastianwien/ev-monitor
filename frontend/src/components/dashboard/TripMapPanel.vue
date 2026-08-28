<script setup lang="ts">
/**
 * Karte einer Fahrt im Log-Feed - kontrolliert vom Aufrufer: die Fahrtzeile selbst ist der
 * Schalter (Klick klappt auf), hier lebt nur noch das Panel. So verschwindet die einsame
 * "Karte"-Zeile unter jeder Fahrt.
 *
 * Die Karte haengt an einem Tipp und nicht am Scrollen: eine Leaflet-Instanz pro sichtbarer
 * Fahrt waere auf einer langen Liste teuer, waehrend geoeffnet immer nur die Fahrten sind,
 * die jemanden gerade interessieren. Erst beim Aufklappen gemountet, beim Zuklappen wieder
 * abgeraeumt.
 *
 * Ob es ueberhaupt etwas zu zeigen gibt, entscheidet der Server: aeltere Fahrten kommen
 * ohne Ortsangaben an, solange der Nutzer die Analytics-Freischaltung nicht hat.
 */
import { defineAsyncComponent } from 'vue'
import { useI18n } from 'vue-i18n'
import { hasTripMap, type TripMapSource } from '../../utils/tripMap'

// Async wie im Dashboard: Leaflet bleibt aus dem Einstiegs-Bundle des Log-Feeds.
const ActivityLocationMap = defineAsyncComponent(() => import('./ActivityLocationMap.vue'))

const props = defineProps<{
  /** Trip feed entry - gelesen werden nur die Ortsfelder. */
  trip: (TripMapSource & { id?: string }) | null
  /** Ziel des aria-controls der Fahrtzeile - eindeutig je Template-Instanz. */
  panelId: string
}>()

const { t } = useI18n()
</script>

<template>
  <!-- Aeusserer Wrapper faengt Fallthrough-Klassen der Aufrufer (Padding) ab, damit sie
       nicht im umrandeten Karten-Container landen. -->
  <!-- Offen/zu entscheidet der Aufrufer per v-if - so greift dessen Hoehen-Transition. -->
  <div v-if="hasTripMap(props.trip)">
    <!-- @click.stop: Zoomen und Ziehen in der Karte darf die Zeile nicht wieder zuklappen. -->
    <div
      :id="panelId"
      class="relative mt-2 h-40 md:h-48 overflow-hidden rounded-sm border border-gray-200 dark:border-gray-600"
      @click.stop
    >
      <ActivityLocationMap
        variant="panel"
        :label="t('dashboard.trip_map_alt')"
        :start-geohash="props.trip?.locationStartGeohash"
        :end-geohash="props.trip?.locationEndGeohash"
        :route-polyline="props.trip?.routePolyline"
        :route-kind="props.trip?.routeKind"
        :trace-polyline="props.trip?.tracePolyline"
      />
    </div>
  </div>
</template>
