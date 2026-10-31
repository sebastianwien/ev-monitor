<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { BoltIcon, CheckCircleIcon } from '@heroicons/vue/24/outline'
import type { StationMatch } from '../../composables/useNearbyStations'
import { cardClass, placeIconBox } from './chipClass'
import { formatDistance } from './placeDistance'
import { stationSub } from './stationSub'
import StationThumb from './StationThumb.vue'

/**
 * Kachel einer Ladesäule in der Ortswahl (Liste im Umkreis, gesuchte Säule, Suchebene): Kartenbild der genauen
 * Lage, wenn das Register sie kennt, sonst das Blitz-Symbol; Name, Ladepunkte, Adresse, Entfernung.
 */
const props = defineProps<{ station: StationMatch & { distanceMeters?: number }; selected?: boolean }>()
const { t, locale } = useI18n()
const coords = computed(() => {
  const { latitude: lat, longitude: lon } = props.station
  return lat != null && lon != null ? { lat, lon } : null
})
</script>

<template>
  <button type="button" :class="[cardClass(!!selected), 'w-full flex items-center gap-3 p-3']">
    <StationThumb v-if="coords" :lat="coords.lat" :lon="coords.lon" />
    <span v-else :class="placeIconBox('station', !!selected)"><BoltIcon class="h-5 w-5" /></span>
    <span class="flex-1 min-w-0">
      <b class="block text-[15px] font-semibold text-gray-900 dark:text-gray-50 truncate">{{ station.name }}</b>
      <small class="block text-xs text-gray-500 dark:text-gray-400">{{ stationSub(station, t) }}</small>
      <small v-if="station.address" class="block text-xs text-gray-500 dark:text-gray-400 truncate">{{ station.address }}</small>
    </span>
    <span v-if="station.distanceMeters != null" class="text-xs tabular-nums text-gray-500 dark:text-gray-400 whitespace-nowrap">
      {{ formatDistance(station.distanceMeters, locale) }}
    </span>
    <CheckCircleIcon v-if="selected" class="h-5 w-5 flex-shrink-0 text-gray-900 dark:text-gray-100" aria-hidden="true" />
  </button>
</template>
