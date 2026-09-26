<script setup lang="ts">
import { computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowPathIcon, BoltIcon, MapPinIcon } from '@heroicons/vue/24/outline'
import { useLocationSearch, type PickedPlace } from '../../composables/useLocationSearch'
import { useStationSearch } from '../../composables/useStationSearch'
import type { StationMatch } from '../../composables/useNearbyStations'
import type { PlaceChoice } from './wizardLogic'
import { stationSub } from './stationSub'

defineProps<{ label: string }>()
const emit = defineEmits<{ choose: [choice: PlaceChoice]; picked: [place: PickedPlace] }>()
const { t } = useI18n()

// Beim Tippen nur das Register (eigenes Backend); Nominatim erst auf Tap, nie als Autocomplete
const stations = useStationSearch()
const address = useLocationSearch()

const query = computed({
  get: () => stations.query.value,
  set: (v: string) => { stations.query.value = v; address.reset() },
})
const trimmed = computed(() => query.value.trim())
const showAddressRow = computed(() => trimmed.value.length >= 3 && !address.loading.value
  && !address.suggestions.value.length && !address.noResults.value && trimmed.value !== address.selectedName.value)
const open = computed(() => stations.matches.value.length > 0 || showAddressRow.value
  || address.loading.value || address.suggestions.value.length > 0 || address.noResults.value)
const busy = computed(() => stations.loading.value || address.loading.value)

watch(() => address.selectedName.value, (name) => { if (name) stations.reset() })

const chooseStation = (s: StationMatch) => {
  stations.query.value = s.name
  stations.reset()
  emit('choose', { kind: 'station', station: s })
}
const pickAddress = (s: Parameters<typeof address.select>[0]) => {
  const picked = address.select(s)
  stations.query.value = picked.name
  emit('picked', picked)
}
const rowClass = 'w-full flex items-start gap-2 px-3 py-2 text-left text-sm hover:bg-gray-50 dark:hover:bg-gray-700 cursor-pointer'
</script>

<template>
  <div class="space-y-1">
    <label for="wizard-place-search" class="block text-xs text-gray-500 dark:text-gray-400">{{ label }}</label>
    <div class="relative">
      <input id="wizard-place-search" v-model="query" type="text" :placeholder="t('logwizard.place_search_placeholder')" autocomplete="off"
        class="w-full rounded-sm border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 px-3 py-2 pr-9 text-sm" />
      <ArrowPathIcon v-if="busy" class="absolute right-3 top-2.5 h-4 w-4 animate-spin text-gray-400" :aria-label="t('common.loading')" />
      <ul v-if="open" role="listbox"
        class="absolute z-10 mt-1 w-full bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-sm shadow-[4px_4px_0_rgba(0,0,0,0.30)] max-h-64 overflow-y-auto divide-y divide-gray-100 dark:divide-gray-700">
        <!-- Säulen aus dem Register -->
        <li v-for="s in stations.matches.value" :key="s.name + s.geohash" role="option" data-testid="place-search-station"
          :class="rowClass" @mousedown.prevent="chooseStation(s)">
          <BoltIcon class="h-4 w-4 mt-0.5 text-indigo-600 flex-shrink-0" />
          <span class="min-w-0">
            <b class="block font-semibold text-gray-800 dark:text-gray-100 truncate">{{ s.name }}</b>
            <small class="block text-xs text-gray-500 dark:text-gray-400">{{ stationSub(s, t) }}</small>
            <small v-if="s.address" class="block text-xs text-gray-400 dark:text-gray-500 truncate">{{ s.address }}</small>
          </span>
        </li>
        <!-- Adresse: erst der Tap fragt Nominatim -->
        <li v-if="showAddressRow" role="option" data-testid="place-search-address" :class="rowClass"
          @mousedown.prevent="address.search(trimmed)">
          <MapPinIcon class="h-4 w-4 mt-0.5 text-gray-500 flex-shrink-0" />
          <span class="min-w-0 text-gray-700 dark:text-gray-200">{{ t('logwizard.search_as_address', { q: trimmed }) }}</span>
        </li>
        <li v-for="s in address.suggestions.value" :key="s.place_id" role="option" data-testid="place-search-suggestion"
          :class="rowClass" @mousedown.prevent="pickAddress(s)">
          <MapPinIcon class="h-4 w-4 mt-0.5 text-gray-500 flex-shrink-0" />
          <span class="min-w-0 text-gray-700 dark:text-gray-200">{{ s.display_name }}</span>
        </li>
        <li v-if="address.noResults.value" data-testid="place-search-no-address" class="px-3 py-2 text-xs text-gray-500 dark:text-gray-400">
          {{ t('logwizard.address_not_found') }}
        </li>
      </ul>
    </div>
    <p v-if="address.selectedName.value" class="text-xs text-green-600">{{ t('logfields.new_location') }} {{ address.selectedName.value }}</p>
  </div>
</template>
