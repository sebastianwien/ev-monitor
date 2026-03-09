<script setup lang="ts">
import { ref } from 'vue'
import { InformationCircleIcon, ChevronRightIcon, ExclamationTriangleIcon } from '@heroicons/vue/24/outline'

const props = withDefaults(defineProps<{
  /** Minimum complete trips before switching from WLTP-bootstrap to statistical check */
  minTrips?: number
}>(), {
  minTrips: 5
})

const expanded = ref(false)
</script>

<template>
  <div class="border border-gray-200 rounded-lg overflow-hidden">
    <button
      type="button"
      @click="expanded = !expanded"
      class="w-full flex items-center justify-between px-3 py-2.5 text-sm text-gray-500 hover:bg-gray-50 transition text-left">
      <span class="flex items-center gap-1.5">
        <InformationCircleIcon class="w-4 h-4 text-indigo-400 flex-shrink-0" />
        Wie berechnen wir deinen Verbrauch?
      </span>
      <ChevronRightIcon class="w-4 h-4 flex-shrink-0 transition-transform duration-200"
        :class="expanded ? 'rotate-90' : ''" />
    </button>
    <div v-if="expanded" class="px-3 pb-3 text-sm text-gray-600 space-y-2 border-t border-gray-100 pt-2.5">
      <p>
        Der Verbrauch wird aus <strong>geladenen kWh</strong>, <strong>Ladezustand (SoC)</strong> und
        <strong>Kilometerstand</strong> zweier aufeinanderfolgender Ladevorgänge berechnet —
        alle drei Angaben müssen bei beiden Einträgen vorhanden sein.
        Fehlt eine davon, kann der Verbrauch für den nächsten Eintrag nicht berechnet werden.
      </p>
      <p>
        Ab <strong>{{ minTrips }} auswertbaren Fahrten</strong> vergleichen wir jeden Wert mit deinem
        persönlichen Durchschnitt. Davor wird der WLTP-Richtwert deines Fahrzeugs als Referenz genutzt.
        Ladevorgänge mit stark abweichendem Verbrauch werden mit
        <span class="inline-flex items-center gap-0.5 px-1.5 py-0.5 bg-red-100 border border-red-400 rounded-full text-xs text-red-700 font-medium">
          <ExclamationTriangleIcon class="w-3 h-3" />
        </span>
        markiert — meist ein Zeichen, dass zwischen zwei Einträgen ein Ladevorgang fehlt.
      </p>
    </div>
  </div>
</template>
