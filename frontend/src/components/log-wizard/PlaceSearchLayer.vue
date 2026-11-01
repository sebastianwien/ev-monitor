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
import Collapse from '../shared/Collapse.vue'
import ListTransition from '../shared/ListTransition.vue'

/**
 * Suchebene für "Woanders geladen?": tippen genügt. Der beste Adresstreffer gilt sofort - darunter "Privat geladen"
 * an dieser Adresse und die Säulen dort, ohne erst einen Vorschlag antippen zu müssen. Ein anderer Treffer oder eine
 * zuletzt gewählte Adresse wird per Tap zur Adresse. Säulen aus dem Register nach Namen stehen obenan.
 * Mit `origin` (Lage des Knopfs "Woanders geladen?") gleitet die Suchleiste von dort nach oben und beim Schließen zurück.
 */
export interface OriginRect { top: number; left: number }
const props = defineProps<{ near: LatLon | null; origin?: OriginRect | null }>()
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
// Beim Zuklappen des Adressblocks zeigt er weiter die letzte Adresse, statt leer zusammenzufallen
const lastActive = ref<PickedPlace | null>(null)
watch(active, (a) => { if (a) lastActive.value = a })
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
const bar = ref<HTMLElement | null>(null)
const backdrop = ref<HTMLElement | null>(null)
const content = ref<HTMLElement | null>(null)

// Gleiten wie Collapse: hin ease-out, zurück ease-in, 300 ms. Ohne Herkunft, ohne Web Animations oder bei
// "Bewegung reduzieren" steht die Ebene einfach da.
const EASE_OUT = 'cubic-bezier(0, 0, 0.2, 1)'
const EASE_IN = 'cubic-bezier(0.4, 0, 1, 1)'
const MOVE_MS = 300
const FADE_MS = 150
const reducedMotion = () => typeof window.matchMedia === 'function' && window.matchMedia('(prefers-reduced-motion: reduce)').matches
const canMove = () => !!props.origin && !!bar.value && typeof bar.value.animate === 'function' && !reducedMotion()
/** Versatz der Suchleiste von ihrem Platz oben zum Knopf */
const offset = () => {
  const target = bar.value!.getBoundingClientRect()
  return `translate(${props.origin!.left - target.left}px, ${props.origin!.top - target.top}px)`
}
const fade = (el: HTMLElement | null, from: number, to: number, ms: number, easing: string) =>
  el?.animate([{ opacity: from }, { opacity: to }], { duration: ms, easing, fill: 'forwards' })

// Der Tap auf "Woanders geladen?" hat den Fokus schon auf ein Hilfsfeld gelegt (iOS öffnet die Tastatur nur im Tap); hier übernimmt das Suchfeld
onMounted(async () => {
  await nextTick()
  inputEl.value?.focus({ preventScroll: true })
  if (!canMove()) return
  // Weiß deckt schnell, damit der Wizard darunter beim Öffnen der Tastatur nicht sichtbar springt
  fade(backdrop.value, 0, 1, FADE_MS, EASE_OUT)
  fade(content.value, 0, 1, MOVE_MS, EASE_OUT)
  bar.value!.animate([{ transform: offset() }, { transform: 'none' }], { duration: MOVE_MS, easing: EASE_OUT })
})

/** Zurück und Escape: die Leiste gleitet auf den Knopf zurück, dann schließt die Ebene */
let closing = false
const close = async () => {
  if (closing) return
  closing = true
  if (!canMove()) { emit('close'); return }
  inputEl.value?.blur()
  fade(content.value, 1, 0, FADE_MS, EASE_IN)
  fade(backdrop.value, 1, 0, MOVE_MS, EASE_IN)
  const move = bar.value!.animate([{ transform: 'none' }, { transform: offset() }], { duration: MOVE_MS, easing: EASE_IN, fill: 'forwards' })
  try { await move.finished } catch { /* abgebrochen: trotzdem schließen */ }
  emit('close')
}
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
      class="fixed inset-x-0 z-[1100] flex flex-col" @keydown.escape.prevent="close">
      <div ref="backdrop" class="absolute inset-0 bg-white dark:bg-gray-900" aria-hidden="true" />
      <header class="relative px-2 pb-3 pt-[calc(env(safe-area-inset-top)+0.75rem)] md:mx-auto md:w-full md:max-w-xl">
        <div ref="bar" data-testid="place-layer-bar" class="flex items-center gap-1">
          <button type="button" data-testid="place-layer-back" :aria-label="t('common.back')" @click="close"
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
        </div>
      </header>

      <!-- Abschnitte klappen per Collapse auf und zu, Einträge darin blenden per ListTransition ein und aus -->
      <main ref="content" class="relative min-h-0 flex-1 overflow-y-auto border-t-2 border-gray-100 px-4 py-3 dark:border-gray-800 md:mx-auto md:w-full md:max-w-xl" aria-live="polite">
        <Collapse :open="showRecent">
          <section data-testid="place-layer-recent" class="pb-4">
            <h2 :class="heading">{{ t('logwizard.layer_recent') }}</h2>
            <ListTransition>
              <button v-for="(p, i) in recent.list.value" :key="p.name" :data-index="i" type="button" :class="row" @click="manual = p">
                <ClockIcon class="h-5 w-5 flex-shrink-0 text-gray-500" aria-hidden="true" /><span class="min-w-0 truncate">{{ p.name }}</span>
              </button>
            </ListTransition>
          </section>
        </Collapse>

        <Collapse :open="stations.matches.value.length > 0">
          <section data-testid="place-layer-matches" class="space-y-3 pb-4">
            <h2 :class="heading">{{ t('logwizard.layer_matches') }}</h2>
            <ListTransition class="space-y-3">
              <StationTile v-for="(s, i) in stations.matches.value" :key="s.name + s.geohash" :data-index="i" :station="s" @click="takeMatch(s)" />
            </ListTransition>
          </section>
        </Collapse>

        <Collapse :open="!!active">
          <div v-if="lastActive" class="space-y-4 pb-4">
            <button type="button" data-testid="place-layer-private" :disabled="stale" :class="[cardClass(false), 'w-full flex items-center gap-3 p-3 disabled:opacity-50']" @click="takePrivate(lastActive)">
              <span :class="placeIconBox('home', false)"><HomeIcon class="h-5 w-5" /></span>
              <span class="min-w-0 flex-1">
                <b class="block text-[15px] font-semibold text-gray-900 dark:text-gray-50">{{ t('logwizard.place_private') }}</b>
                <!-- Neue Adresse: der Text wechselt weich, die Kachel bleibt stehen -->
                <Transition mode="out-in" enter-active-class="transition-opacity duration-150 ease-out motion-reduce:transition-none" enter-from-class="opacity-0"
                  leave-active-class="transition-opacity duration-150 ease-in motion-reduce:transition-none" leave-to-class="opacity-0">
                  <small :key="lastActive.name" class="block truncate text-xs text-gray-500 dark:text-gray-400">{{ lastActive.name }}</small>
                </Transition>
              </span>
            </button>
            <Collapse :open="nearby.loading.value || nearby.stations.value.length > 0">
              <section data-testid="place-layer-stations" class="space-y-3">
                <h2 :class="heading">{{ t('logwizard.layer_stations_at', { a: lastActive.name }) }}</h2>
                <div v-if="nearby.loading.value && !nearby.stations.value.length" role="status" class="flex justify-center py-2">
                  <ArrowPathIcon class="h-5 w-5 animate-spin text-gray-500" :aria-label="t('common.loading')" />
                </div>
                <!-- Neue Adresse: die alten Säulen bleiben gedimmt stehen, bis die neuen da sind, dann blendet der Unterschied über -->
                <ListTransition data-testid="place-layer-station-list"
                  :class="['space-y-3 transition-opacity duration-150', nearby.loading.value && nearby.stations.value.length ? 'opacity-50' : '']">
                  <StationTile v-for="(s, i) in nearby.stations.value" :key="s.name" :data-index="i" :station="s" :disabled="stale || nearby.loading.value"
                    class="disabled:opacity-50" @click="takeStationThere(s)" />
                </ListTransition>
              </section>
            </Collapse>
          </div>
        </Collapse>

        <Collapse :open="others.length > 0">
          <section data-testid="place-layer-other" class="pb-4">
            <h2 :class="heading">{{ t('logwizard.layer_other') }}</h2>
            <ListTransition>
              <button v-for="(p, i) in others" :key="`${p.name}|${p.latitude}|${p.longitude}`" :data-index="i" type="button" :class="row" @click="manual = p">
                <MapPinIcon class="h-5 w-5 flex-shrink-0 text-gray-500" aria-hidden="true" /><span class="min-w-0 truncate">{{ p.name }}</span>
              </button>
            </ListTransition>
          </section>
        </Collapse>

        <Collapse :open="nothing">
          <p data-testid="place-layer-none" class="text-sm text-gray-600 dark:text-gray-300">{{ t('logwizard.address_not_found') }}</p>
        </Collapse>
      </main>
    </div>
  </Teleport>
</template>
