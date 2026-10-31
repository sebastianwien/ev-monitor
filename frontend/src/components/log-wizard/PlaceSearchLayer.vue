<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { getActivePinia } from 'pinia'
import { ArrowPathIcon, ChevronLeftIcon, ClockIcon, HomeIcon, MapPinIcon, XMarkIcon } from '@heroicons/vue/24/outline'
import { useLocationSearch, shortAddress, type LatLon, type PickedPlace, type PlaceSuggestion } from '../../composables/useLocationSearch'
import { useStationSearch } from '../../composables/useStationSearch'
import { useNearbyStations, type StationMatch } from '../../composables/useNearbyStations'
import { useRecentAddresses } from '../../composables/useRecentAddresses'
import { useVisualViewportBox } from '../../composables/useVisualViewportBox'
import { useAuthStore } from '../../stores/auth'
import { cardClass, placeIconBox } from './chipClass'
import type { PlaceChoice } from './wizardLogic'
import StationTile from './StationTile.vue'

/**
 * Suchebene für "Woanders geladen?": tippen genügt. Der beste Adresstreffer gilt sofort - darunter "Privat geladen"
 * an dieser Adresse und die Säulen dort, ohne erst einen Vorschlag antippen zu müssen. Ein anderer Treffer oder eine
 * zuletzt gewählte Adresse wird per Tap zur Adresse. Säulen aus dem Register nach Namen stehen obenan.
 */
const props = defineProps<{ near: LatLon | null }>()
const emit = defineEmits<{ close: []; choose: [choice: PlaceChoice]; picked: [place: PickedPlace] }>()
const { t } = useI18n()

const stations = useStationSearch()
const address = useLocationSearch(() => props.near)
const nearby = useNearbyStations()
const recent = useRecentAddresses(getActivePinia() ? useAuthStore().user?.userId : null)

const query = ref('')
/** Per Tap gewählte Adresse (anderer Treffer, letzte Adresse); sonst gilt der beste Treffer */
const manual = ref<PickedPlace | null>(null)
watch(query, (q) => { stations.query.value = q; address.autocomplete(q); manual.value = null })

const toPlace = (s: PlaceSuggestion): PickedPlace => ({ latitude: parseFloat(s.lat), longitude: parseFloat(s.lon), name: shortAddress(s) })
const hits = computed(() => address.suggestions.value.map(toPlace))
const active = computed<PickedPlace | null>(() => manual.value ?? hits.value[0] ?? null)
const others = computed(() => hits.value.filter(p => p.name !== active.value?.name))
// Säulen an der Adresse: je neuer Position eine Abfrage beim eigenen Backend (Geohash-Zelle, wächst bis zum ersten Treffer)
watch(() => active.value && `${active.value.latitude},${active.value.longitude}`, (key) => {
  if (key && active.value) nearby.load(active.value.latitude, active.value.longitude)
})

const showRecent = computed(() => query.value.trim() === '' && !manual.value && recent.list.value.length > 0)
const busy = computed(() => stations.loading.value || address.loading.value)
const nothing = computed(() => address.noResults.value && stations.searched.value && stations.matches.value.length === 0 && !manual.value)

/** Such-Taste: sofort, ohne die Tipp-Pause abzuwarten */
const onEnter = () => { if (query.value.trim().length >= 3) address.search(query.value.trim()) }
// Solange die Antwort auf die letzte Eingabe aussteht, gehört der Treffer zur vorigen: gesperrt statt falscher Ort
const stale = computed(() => !manual.value && address.pending.value)
const takePrivate = (p: PickedPlace) => {
  if (stale.value) return
  recent.remember(p); emit('picked', p); emit('choose', { kind: 'home' })
}
const takeStationThere = (s: StationMatch) => {
  if (stale.value) return
  if (active.value) { recent.remember(active.value); emit('picked', active.value) }
  emit('choose', { kind: 'station', station: s, viaSearch: true })
}
const takeMatch = (s: StationMatch) => emit('choose', { kind: 'station', station: s, viaSearch: true })

const inputEl = ref<HTMLInputElement | null>(null)
// Der Tap auf "Woanders geladen?" hat den Fokus schon auf ein Hilfsfeld gelegt (iOS öffnet die Tastatur nur im Tap); hier übernimmt das Suchfeld
onMounted(async () => { await nextTick(); inputEl.value?.focus({ preventScroll: true }) })
// Kein Nachzügler nach dem Schließen: offene Tipp-Pause verwerfen
onUnmounted(() => { address.reset(); stations.reset() })
const clear = () => { query.value = ''; inputEl.value?.focus() }

// iOS verkleinert bei offener Tastatur nur den sichtbaren Ausschnitt: die Ebene folgt ihm, sonst wandert das Feld aus dem Bild
const box = useVisualViewportBox()
const frame = computed(() => box.value ? { top: `${box.value.top}px`, height: `${box.value.height}px` } : { top: '0', bottom: '0' })
const heading = 'text-[12px] font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400'
const row = 'w-full min-h-11 flex items-center gap-2 px-1 py-2 text-left text-[15px] text-gray-800 dark:text-gray-100 rounded-lg hover:bg-gray-50 dark:hover:bg-white/10'
</script>

<template>
  <Teleport to="body">
    <!-- Über jedem Modal: der Wizard läuft auf dem Desktop im Erfassen-Modal (z-[1000]) -->
    <div role="dialog" aria-modal="true" :aria-label="t('logwizard.place_search')" data-testid="place-layer" :style="frame"
      class="fixed inset-x-0 z-[1100] flex flex-col bg-white dark:bg-gray-900" @keydown.escape.prevent="emit('close')">
      <header class="flex items-center gap-1 border-b-2 border-gray-100 px-2 pb-3 pt-[calc(env(safe-area-inset-top)+0.75rem)] dark:border-gray-800 md:mx-auto md:w-full md:max-w-xl">
        <button type="button" data-testid="place-layer-back" :aria-label="t('common.back')" @click="emit('close')"
          class="grid h-11 w-11 flex-shrink-0 place-items-center rounded-full text-gray-700 hover:bg-gray-100 dark:text-gray-200 dark:hover:bg-white/10">
          <ChevronLeftIcon class="h-6 w-6" />
        </button>
        <div class="relative min-w-0 flex-1">
          <input id="wizard-place-layer-search" ref="inputEl" v-model="query" type="text" autocomplete="off" enterkeyhint="search"
            :aria-label="t('logwizard.place_search')" :placeholder="t('logwizard.place_search_placeholder')" @keydown.enter.prevent="onEnter"
            class="min-h-12 w-full rounded-xl border-2 border-edge bg-white px-3 pr-11 text-[16px] outline-none placeholder:text-gray-400 focus:border-green-600 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-100 dark:placeholder:text-gray-500" />
          <ArrowPathIcon v-if="busy" class="absolute right-3.5 top-1/2 h-4 w-4 -translate-y-1/2 animate-spin text-gray-500" :aria-label="t('common.loading')" />
          <button v-else-if="query" type="button" :aria-label="t('logwizard.search_clear')" @click="clear"
            class="absolute inset-y-0 right-0 grid w-11 place-items-center text-gray-500 hover:text-gray-900 dark:text-gray-400 dark:hover:text-gray-100">
            <XMarkIcon class="h-5 w-5" aria-hidden="true" />
          </button>
        </div>
      </header>

      <main class="min-h-0 flex-1 space-y-4 overflow-y-auto px-4 py-3 md:mx-auto md:w-full md:max-w-xl" aria-live="polite">
        <section v-if="showRecent" data-testid="place-layer-recent">
          <h2 :class="heading">{{ t('logwizard.layer_recent') }}</h2>
          <button v-for="p in recent.list.value" :key="p.name" type="button" :class="row" @click="manual = p">
            <ClockIcon class="h-5 w-5 flex-shrink-0 text-gray-500" aria-hidden="true" /><span class="min-w-0 truncate">{{ p.name }}</span>
          </button>
        </section>

        <section v-if="stations.matches.value.length" data-testid="place-layer-matches" class="space-y-3">
          <h2 :class="heading">{{ t('logwizard.layer_matches') }}</h2>
          <StationTile v-for="s in stations.matches.value" :key="s.name + s.geohash" :station="s" @click="takeMatch(s)" />
        </section>

        <template v-if="active">
          <button type="button" data-testid="place-layer-private" :disabled="stale" :class="[cardClass(false), 'w-full flex items-center gap-3 p-3 disabled:opacity-50']" @click="takePrivate(active)">
            <span :class="placeIconBox('home', false)"><HomeIcon class="h-5 w-5" /></span>
            <span class="min-w-0 flex-1">
              <b class="block text-[15px] font-semibold text-gray-900 dark:text-gray-50">{{ t('logwizard.place_private') }}</b>
              <small class="block truncate text-xs text-gray-500 dark:text-gray-400">{{ active.name }}</small>
            </span>
          </button>
          <section v-if="nearby.loading.value || nearby.stations.value.length" data-testid="place-layer-stations" class="space-y-3">
            <h2 :class="heading">{{ t('logwizard.layer_stations_at', { a: active.name }) }}</h2>
            <div v-if="nearby.loading.value" role="status" class="flex justify-center py-2">
              <ArrowPathIcon class="h-5 w-5 animate-spin text-gray-500" :aria-label="t('common.loading')" />
            </div>
            <template v-else>
              <StationTile v-for="s in nearby.stations.value" :key="s.name" :station="s" :disabled="stale" class="disabled:opacity-50" @click="takeStationThere(s)" />
            </template>
          </section>
        </template>

        <section v-if="others.length" data-testid="place-layer-other">
          <h2 :class="heading">{{ t('logwizard.layer_other') }}</h2>
          <button v-for="p in others" :key="`${p.name}|${p.latitude}|${p.longitude}`" type="button" :class="row" @click="manual = p">
            <MapPinIcon class="h-5 w-5 flex-shrink-0 text-gray-500" aria-hidden="true" /><span class="min-w-0 truncate">{{ p.name }}</span>
          </button>
        </section>

        <p v-if="nothing" data-testid="place-layer-none" class="text-sm text-gray-600 dark:text-gray-300">{{ t('logwizard.address_not_found') }}</p>
      </main>
    </div>
  </Teleport>
</template>
