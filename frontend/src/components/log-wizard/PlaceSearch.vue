<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowPathIcon, BoltIcon, ClockIcon, MapPinIcon, XMarkIcon } from '@heroicons/vue/24/outline'
import { getActivePinia } from 'pinia'
import { useLocationSearch, shortAddress, type PickedPlace } from '../../composables/useLocationSearch'
import { listPlacement } from './listPlacement'
import { useStationSearch } from '../../composables/useStationSearch'
import { useRecentAddresses } from '../../composables/useRecentAddresses'
import { useAuthStore } from '../../stores/auth'
import type { StationMatch } from '../../composables/useNearbyStations'
import type { PlaceChoice } from './wizardLogic'
import { stationSub } from './stationSub'

defineProps<{ label: string }>()
const emit = defineEmits<{ choose: [choice: PlaceChoice]; picked: [place: PickedPlace] }>()
const { t } = useI18n()

// Beim Tippen nur das Register (eigenes Backend); Nominatim erst auf Tap, nie als Autocomplete
const stations = useStationSearch()
const address = useLocationSearch()
// Zuletzt gewählte Adressen: beim Fokus ins leere Feld, damit niemand ohne GPS die Heimadresse jedes Mal tippt
const recent = useRecentAddresses(getActivePinia() ? useAuthStore().user?.userId : null)
const focused = ref(false)

// Name der aus dem Register gewählten Säule: solange er im Feld steht, gibt es nichts mehr zu suchen
const chosenStation = ref<string | null>(null)
const query = computed({
  get: () => stations.query.value,
  set: (v: string) => { stations.query.value = v; address.reset(); chosenStation.value = null },
})
const trimmed = computed(() => query.value.trim())
const showAddressRow = computed(() => trimmed.value.length >= 3 && !address.loading.value
  && !address.suggestions.value.length && !address.noResults.value
  && trimmed.value !== address.selectedName.value && trimmed.value !== chosenStation.value)
const showRecent = computed(() => focused.value && trimmed.value === '' && recent.list.value.length > 0)
const open = computed(() => showRecent.value || stations.matches.value.length > 0 || showAddressRow.value
  || address.loading.value || address.suggestions.value.length > 0 || address.noResults.value)
const busy = computed(() => stations.loading.value || address.loading.value)

watch(() => address.selectedName.value, (name) => { if (name) stations.reset() })

/** Nach der Wahl Fokus weg vom Suchfeld: sonst schluckt Android den nächsten Tap fürs Schließen der Tastatur */
const blurSearch = () => { const el = document.activeElement; if (el instanceof HTMLElement) el.blur() }
const chooseStation = (s: StationMatch) => {
  blurSearch()
  stations.select(s.name)
  chosenStation.value = s.name.trim()
  emit('choose', { kind: 'station', station: s, viaSearch: true })
}
const pickAddress = (s: Parameters<typeof address.select>[0]) => {
  blurSearch()
  const picked = address.select(s)
  stations.select(picked.name)
  recent.remember(picked)
  emit('picked', picked)
}
const pickRecent = (p: PickedPlace) => {
  blurSearch()
  address.selectedName.value = p.name
  stations.select(p.name)
  recent.remember(p)
  emit('picked', p)
}
const inputEl = ref<HTMLInputElement | null>(null)
const clear = () => {
  query.value = ''
  address.selectedName.value = ''
  inputEl.value?.focus()
}

// Liste über dem Feld, wenn die Tastatur den Platz darunter verdeckt
const placement = ref({ above: false, maxHeight: 256 })
const place = () => {
  const el = inputEl.value
  if (!el || !open.value) return
  const vv = window.visualViewport
  const top = vv?.offsetTop ?? 0
  placement.value = listPlacement(el.getBoundingClientRect(), { top, bottom: top + (vv?.height ?? window.innerHeight) })
}
watch(open, o => { if (o) nextTick(place) })
onMounted(() => window.visualViewport?.addEventListener('resize', place))
onUnmounted(() => window.visualViewport?.removeEventListener('resize', place))

/** Such-Taste der Tastatur: fragt die Adresse direkt ab, statt erst "Als Adresse suchen" antippen zu lassen */
const onEnter = () => { if (showAddressRow.value) address.search(trimmed.value) }
const rowClass = 'w-full flex items-start gap-2 px-3 py-2 text-left text-sm hover:bg-gray-50 dark:hover:bg-gray-700 cursor-pointer'
</script>

<template>
  <div class="space-y-1">
    <label for="wizard-place-search" class="block text-xs text-gray-500 dark:text-gray-400">{{ label }}</label>
    <div class="relative">
      <input id="wizard-place-search" ref="inputEl" v-model="query" type="text" :placeholder="t('logwizard.place_search_placeholder')" autocomplete="off"
        enterkeyhint="search" @keydown.enter.prevent="onEnter" @focus="focused = true" @blur="focused = false"
        class="w-full rounded-sm border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 px-3 py-2 pr-9 text-sm" />
      <ArrowPathIcon v-if="busy" class="absolute right-3 top-2.5 h-4 w-4 animate-spin text-gray-400" :aria-label="t('common.loading')" />
      <button v-else-if="query" type="button" data-testid="place-search-clear" :aria-label="t('logwizard.search_clear')" @click="clear"
        class="absolute inset-y-0 right-0 w-11 grid place-items-center text-gray-400 hover:text-gray-600 dark:hover:text-gray-200">
        <XMarkIcon class="h-5 w-5" aria-hidden="true" />
      </button>
      <ul v-if="open" role="listbox" :style="{ maxHeight: `${placement.maxHeight}px` }"
        :class="placement.above ? 'bottom-full mb-1' : 'top-full mt-1'"
        class="absolute z-10 w-full bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-sm shadow-[4px_4px_0_rgba(0,0,0,0.30)] overflow-y-auto divide-y divide-gray-100 dark:divide-gray-700">
        <li v-for="p in showRecent ? recent.list.value : []" :key="p.name" role="option" data-testid="place-search-recent"
          v-haptic :class="rowClass" @mousedown.prevent="pickRecent(p)">
          <ClockIcon class="h-4 w-4 mt-0.5 text-gray-500 flex-shrink-0" />
          <span class="min-w-0 text-gray-700 dark:text-gray-200">{{ p.name }}</span>
        </li>
        <!-- Säulen aus dem Register -->
        <li v-for="s in stations.matches.value" :key="s.name + s.geohash" role="option" data-testid="place-search-station"
          v-haptic :class="rowClass" @mousedown.prevent="chooseStation(s)">
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
          v-haptic :class="rowClass" @mousedown.prevent="pickAddress(s)">
          <MapPinIcon class="h-4 w-4 mt-0.5 text-gray-500 flex-shrink-0" />
          <span class="min-w-0">
            <b class="block font-semibold text-gray-800 dark:text-gray-100 truncate">{{ shortAddress(s) }}</b>
            <small class="block text-xs text-gray-400 dark:text-gray-500 truncate">{{ s.display_name }}</small>
          </span>
        </li>
        <li v-if="address.noResults.value" data-testid="place-search-no-address" class="px-3 py-2 text-xs text-gray-500 dark:text-gray-400">
          {{ t('logwizard.address_not_found') }}
        </li>
      </ul>
    </div>
    <p v-if="address.selectedName.value" class="text-xs text-green-600">{{ t('logfields.new_location') }} {{ address.selectedName.value }}</p>
  </div>
</template>
