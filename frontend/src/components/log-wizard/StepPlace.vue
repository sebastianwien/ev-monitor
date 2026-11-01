<script setup lang="ts">
import { CHIP_ROW, chipClass, cardClass, placeIconBox } from './chipClass'
import { computed, nextTick, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowPathIcon, HomeIcon, BoltIcon, MapPinIcon, MagnifyingGlassIcon, CheckCircleIcon, Cog6ToothIcon, ChevronDownIcon } from '@heroicons/vue/24/outline'
import StationTile from './StationTile.vue'
import PlaceSearchLayer, { type OriginRect } from './PlaceSearchLayer.vue'
import { RADIUS_STEPS, type NearbyStation, type StationMatch } from '../../composables/useNearbyStations'
import { isLocationInaccurate, formatDistance } from './placeDistance'
import type { ChargingSiteRef } from '../log-form/logFormData'
import { settingsPlatform, openAppSettings, type LocationPermission } from '../../composables/useLocationPermission'
import type { PlaceChoice, PlaceKind } from './wizardLogic'
import CardStrip, { type CardChoice, type CommunityPrice } from './CardStrip.vue'
import type { ChargingProvider } from '../../composables/useChargingProviders'
import type { PickedPlace } from '../../composables/useLocationSearch'
import PlaceSearch from './PlaceSearch.vue'
import Collapse from '../shared/Collapse.vue'

const props = defineProps<{
  place: PlaceKind | null
  selectedCpo: string | null
  selectedSite: ChargingSiteRef | null
  /** Getippte Adresse, als Untertitel der Zeile "Hier privat" */
  addressLabel?: string | null
  stations: NearbyStation[]
  stationsLoading: boolean
  permission: LocationPermission
  locationStatus: 'idle' | 'loading' | 'success' | 'error'
  /** Umkreis der aktuellen Liste in Metern, und ob "Umkreis erweitern" noch etwas bringt */
  radiusMeters?: number
  canExpand?: boolean
  /** Nächste Stufe für "Nicht dabei?" */
  nextRadius?: number | null
  /** Der Umkreis ist bis zur größten Stufe abgesucht, ohne Treffer */
  exhausted?: boolean
  /** Ungenauigkeit der Ortung in Metern; null bei gesuchter Adresse */
  locationAccuracy?: number | null
  /** Position des Logs (GPS oder gewählte Adresse); ohne sie wird privat ohne Ortsangabe gespeichert */
  latitude?: number | null
  longitude?: number | null
  /** Bearbeiten-Dialog: das Log hat schon einen Ort, der ohne neue Adresse erhalten bleibt */
  storedPlace?: boolean
  /** Ladekarten-Streifen über der gewählten Säule; fehlt im Bearbeiten-Dialog */
  cardStrip?: {
    providers: ChargingProvider[]
    chargingType: 'AC' | 'DC'
    community: CommunityPrice | null
    selected: string | null
    priceLabel: (eur: number) => string
  } | null
}>()
const emit = defineEmits<{ choose: [choice: PlaceChoice]; requestLocation: []; placePicked: [place: PickedPlace]; expandRadius: []; chooseCard: [choice: CardChoice] }>()
const { t, locale } = useI18n()

const platform = settingsPlatform()
// "Woanders geladen?" öffnet die Suchebene. iOS öffnet die Tastatur nur, wenn der Fokus noch im Tap gesetzt wird:
// das Hilfsfeld bekommt ihn sofort, das Suchfeld der Ebene übernimmt ihn, sobald sie steht.
const layerOpen = ref(false)
const focusProxy = ref<HTMLInputElement | null>(null)
/** Lage des Knopfs: von dort gleitet die Suchleiste nach oben und beim Schließen zurück */
const layerOrigin = ref<OriginRect | null>(null)
const openLayer = (e: MouseEvent) => {
  focusProxy.value?.focus({ preventScroll: true })
  const r = (e.currentTarget as HTMLElement).getBoundingClientRect()
  layerOrigin.value = { top: r.top, left: r.left }
  layerOpen.value = true
}
const onLayerChoose = (c: PlaceChoice) => { layerOpen.value = false; onSearchChoose(c) }
const near = computed(() => props.latitude != null && props.longitude != null ? { lat: props.latitude, lon: props.longitude } : null)
const dist = (m: number) => formatDistance(m, locale.value)
const radiusLabel = computed(() => dist(props.radiusMeters ?? RADIUS_STEPS[0]))
// Während der wachsende Umkreis läuft, sagt der Ladetext, welche Stufe leer war
const loadingText = computed(() => {
  const i = RADIUS_STEPS.indexOf((props.radiusMeters ?? RADIUS_STEPS[0]) as typeof RADIUS_STEPS[number])
  return i > 0 ? t('logwizard.nearby_expanding', { from: dist(RADIUS_STEPS[i - 1]), r: radiusLabel.value }) : t('logwizard.nearby_loading')
})
// Ort steht, aber keine Säule in der Liste: leer gesucht oder Abfrage gescheitert (z. B. Drossel)
const listEmpty = computed(() => props.locationStatus === 'success' && !props.stationsLoading && props.stations.length === 0)
// Nur wenn bis zur größten Stufe gesucht wurde, darf der Kasten "keine Säule im Umkreis" behaupten
const noStationsFound = computed(() => listEmpty.value && !!props.exhausted)
const inaccurate = computed(() => isLocationInaccurate(props.locationAccuracy))
// Ohne Säulen steht die Suche offen da: ein Tap ins Feld, kein "Anderer Ort" davor
const showLocationBlock = computed(() => props.locationStatus !== 'success' || listEmpty.value)
// Mit Position (GPS oder gewählte Adresse) heißen die Zeilen "hier privat/öffentlich geladen"
const hasPosition = computed(() => props.latitude != null)
// Privat und öffentlich gibt es nur mit Ort: ein Log ohne Geohash soll nicht entstehen
const placeKnown = computed(() => hasPosition.value || !!props.storedPlace)
// "Woanders" nur, solange die Suche zugeklappt ist - sonst steht sie schon da
const showElsewhere = computed(() => placeKnown.value && !showLocationBlock.value)
const sameSite = (name: string, geohash: string) =>
  props.selectedSite?.geohash === geohash && props.selectedSite?.name === name
// Im Bearbeiten-Dialog steht eine gewählte Säule als 'site' (der Dialog kennt nur den gespeicherten Standort)
const atStation = computed(() => props.place === 'station' || props.place === 'site')
const isStation = (s: NearbyStation) => atStation.value && sameSite(s.name, s.geohash)
// Drei Säulen, der Rest klappt auf: so bleibt die Privat-Zeile ganz unten auch auf kleinen Handys im Bild.
// Eine gewählte Säule weiter unten (Zurückblättern, Bearbeiten) zeigt die ganze Liste.
const VISIBLE_STATIONS = 3
const showAllStations = ref(false)
const visibleCount = computed(() =>
  showAllStations.value || props.stations.findIndex(isStation) >= VISIBLE_STATIONS ? props.stations.length : VISIBLE_STATIONS)
const hiddenStations = computed(() => Math.max(0, props.stations.length - visibleCount.value))
// Säulen, die nach dem ersten Bild kommen (erste Füllung nach dem Laden, größerer Umkreis), klappen einzeln und leicht
// versetzt auf - als Block fiele das neben der zuklappenden Standort-Karte nicht auf. Beim Zurückblättern stehen sie sofort da.
const STAGGER_MS = 60
const mountedOnce = ref(false)
onMounted(() => nextTick(() => { mountedOnce.value = true }))

// Aus der Textsuche gewählte Säule: als eigene Kachel zeigen, solange sie nicht ohnehin in der Umkreis-Liste steht.
// Lebt im Wizard (v-model), damit sie beim Zurückblättern aus Schritt 2 nicht verschwindet - Schritt 1 wird neu aufgebaut.
const searchedStation = defineModel<StationMatch | null>('searchedStation', { default: null })
const onSearchChoose = (c: PlaceChoice) => {
  searchedStation.value = c.kind === 'station' ? c.station : null
  emit('choose', c)
}
const showSearchedStation = computed(() => {
  const s = searchedStation.value
  return !!s && atStation.value && sameSite(s.name, s.geohash)
    && !props.stations.some(n => n.name === s.name && n.geohash === s.geohash)
})

const iconBox = placeIconBox
const tileClass = (on: boolean) => [cardClass(on), 'w-full flex items-center gap-3 p-3']
</script>

<template>
  <div class="space-y-3">
    <!-- Leerzustand: keine Fehlermeldung, sondern die Frage, wo geladen wurde. Darunter Suche, öffentlich ohne Säule, ganz unten privat hier. -->
    <div v-if="noStationsFound" role="status" data-testid="wizard-no-stations"
      class="p-3 rounded-xl bg-gray-100 dark:bg-white/10 text-[14px] text-gray-800 dark:text-gray-100 space-y-1">
      <p v-if="inaccurate" class="font-semibold">{{ t('logwizard.location_inaccurate', { r: dist(locationAccuracy!) }) }}</p>
      <p v-else class="font-semibold">{{ t('logwizard.no_stations_in_radius', { r: radiusLabel }) }}</p>
      <div class="flex items-center gap-2">
        <p class="flex-1 text-gray-600 dark:text-gray-300">{{ t('logwizard.no_stations_hint') }}</p>
        <button v-if="inaccurate" type="button" data-testid="wizard-relocate" :class="chipClass(false)" @click="emit('requestLocation')">
          <ArrowPathIcon class="h-4 w-4 inline mr-1 -mt-0.5" />{{ t('logwizard.location_relocate') }}
        </button>
      </div>
    </div>
    <!-- Standort-Card und Ortssuche kollabieren gemeinsam, sobald die Position steht -->
    <Collapse :open="showLocationBlock">
    <div class="space-y-3">
    <!-- Standort: noch nie gefragt -> Hinweis-Card mit Button, Dialog erst beim Tap.
         Bereits erlaubt -> dieselbe Card zeigt waehrend der automatischen Ortung den Spinner. -->
    <div v-if="permission === 'prompt' || permission === 'unknown' || locationStatus === 'loading'"
      class="flex items-center gap-3 p-3 rounded-xl bg-gray-100 dark:bg-white/10">
      <MapPinIcon class="h-5 w-5 text-gray-900 dark:text-gray-100 flex-shrink-0" aria-hidden="true" />
      <p class="flex-1 text-[14px] text-gray-800 dark:text-gray-100" aria-live="polite">{{ locationStatus === 'loading' ? loadingText : t('logwizard.location_offer') }}</p>
      <button type="button" data-testid="wizard-location" :disabled="locationStatus === 'loading'" @click="emit('requestLocation')"
        class="btn-3d cta-shadow-sm min-h-11 rounded-full border-2 border-edge bg-white px-4 text-[14px] font-semibold text-gray-900 whitespace-nowrap hover:bg-gray-50 disabled:opacity-60 dark:bg-white/10 dark:text-gray-100 dark:hover:bg-white/15">
        <ArrowPathIcon v-if="locationStatus === 'loading'" class="h-5 w-5 animate-spin" :aria-label="t('common.loading')" />
        <template v-else>{{ t('logwizard.location_cta') }}</template>
      </button>
    </div>
    <!-- Blockiert: Anleitung je Plattform, in der App der direkte Sprung in die Einstellungen -->
    <div v-else-if="permission === 'denied' || locationStatus === 'error'" data-testid="wizard-location-blocked"
      class="p-3 rounded-xl bg-gray-100 dark:bg-white/10 text-[14px] text-gray-800 dark:text-gray-100 space-y-2">
      <p class="font-semibold">{{ t('logwizard.location_blocked') }}</p>
      <p v-if="platform !== 'native'" class="text-gray-600 dark:text-gray-300">{{ t(`logwizard.location_steps_${platform}`) }}</p>
      <div :class="CHIP_ROW">
        <button v-if="platform === 'native'" type="button" :class="chipClass(true)" @click="openAppSettings()">
          <Cog6ToothIcon class="h-4 w-4 inline mr-1 -mt-0.5" />{{ t('logwizard.location_open_settings') }}
        </button>
        <button type="button" :class="chipClass(platform !== 'native')" @click="emit('requestLocation')">
          <ArrowPathIcon class="h-4 w-4 inline mr-1 -mt-0.5" />{{ t('logwizard.location_retry') }}
        </button>
      </div>
    </div>

    <!-- Ohne Live-Position: Ort suchen - laedt danach ebenfalls die Saeulen im Umkreis -->
    <PlaceSearch :label="t('logwizard.place_search')"
      @choose="onSearchChoose" @picked="p => emit('placePicked', p)" />
    </div>
    </Collapse>
    <!-- Streifen fährt oberhalb der Kachel aus: die getippte Kachel bleibt stehen (Liste ist unten verankert) -->
    <Collapse :open="!!cardStrip && showSearchedStation">
      <CardStrip v-if="cardStrip" :providers="cardStrip.providers" :is-public="true" :charging-type="cardStrip.chargingType"
        :community="cardStrip.community" :selected="cardStrip.selected" :price-label="cardStrip.priceLabel" @choose="c => emit('chooseCard', c)" />
    </Collapse>
    <StationTile v-if="showSearchedStation && searchedStation" data-testid="place-searched-station" :station="searchedStation" selected
      @click="emit('choose', { kind: 'station', station: searchedStation, viaSearch: true })" />

    <!-- Spinner nur bei der Ortssuche; bei der Live-Ortung dreht er in der Standort-Card, beim Erweitern unter der Liste -->
    <div v-if="stationsLoading && locationStatus !== 'loading' && stations.length === 0" role="status" data-testid="stations-loading"
      class="flex flex-col items-center gap-2 py-4 text-sm text-gray-500 dark:text-gray-400">
      <ArrowPathIcon class="h-8 w-8 animate-spin text-gray-500 dark:text-gray-400" aria-hidden="true" />
      <span>{{ loadingText }}</span>
    </div>
    <Collapse :open="stations.length > 0">
    <div class="space-y-3">
      <h2 class="text-[15px] font-semibold text-gray-900 dark:text-gray-50">
        {{ radiusMeters && radiusMeters > 250 ? t('logwizard.nearby_title_radius', { r: radiusLabel }) : t('logwizard.nearby_title') }}
      </h2>
      <!-- Jede Säule in eigener Collapse: "N weitere Säulen" und ein größerer Umkreis klappen die neuen Einträge auf -->
      <template v-for="(s, i) in stations" :key="s.name">
      <Collapse :open="i < visibleCount" :appear="mountedOnce" :appear-delay="i * STAGGER_MS">
      <div class="space-y-3">
      <!-- Streifen fährt oberhalb der Kachel aus: die getippte Kachel bleibt stehen (Liste ist unten verankert) -->
      <Collapse :open="!!cardStrip && isStation(s)">
        <CardStrip v-if="cardStrip" :providers="cardStrip.providers" :is-public="true" :charging-type="cardStrip.chargingType"
        :community="cardStrip.community" :selected="cardStrip.selected" :price-label="cardStrip.priceLabel" @choose="c => emit('chooseCard', c)" />
      </Collapse>
      <StationTile :station="s" :selected="isStation(s)" @click="emit('choose', { kind: 'station', station: s })" />
      </div>
      </Collapse>
      </template>
      <!-- "Nicht dabei?": die weite Suche ersetzt diese Liste, statt sie zu verlängern -->
      <!-- Beim Erweitern dreht der Spinner dort, wo der Knopf war: dort schaut der Nutzer hin -->
      <div v-if="stationsLoading" role="status" data-testid="stations-loading"
        class="min-h-11 flex items-center justify-center gap-2 text-sm text-gray-500 dark:text-gray-400">
        <ArrowPathIcon class="h-5 w-5 animate-spin text-gray-500 dark:text-gray-400" aria-hidden="true" />
        <span>{{ t('logwizard.nearby_searching_radius', { r: radiusLabel }) }}</span>
      </div>
      <!-- Erst die ganze Liste, dann weiter suchen: zwei gestrichelte Knöpfe untereinander wären eine Frage zu viel -->
      <button v-else-if="hiddenStations > 0" type="button" data-testid="more-stations" @click="showAllStations = true"
        class="w-full min-h-11 rounded-xl border-2 border-dashed border-edge text-[14px] font-semibold text-gray-800 dark:text-gray-100 hover:bg-gray-50 dark:hover:bg-white/10 transition-colors inline-flex items-center justify-center gap-1.5">
        {{ t('logwizard.more_stations', { n: hiddenStations }, hiddenStations) }}<ChevronDownIcon class="h-4 w-4" aria-hidden="true" />
      </button>
      <button v-else-if="canExpand" type="button" data-testid="expand-radius" @click="emit('expandRadius')"
        class="w-full min-h-11 rounded-xl border-2 border-dashed border-edge text-[14px] font-semibold text-gray-800 dark:text-gray-100 hover:bg-gray-50 dark:hover:bg-white/10 transition-colors">
        {{ t('logwizard.expand_radius', { r: dist(nextRadius ?? RADIUS_STEPS[RADIUS_STEPS.length - 1]) }) }}
      </button>
    </div>
    </Collapse>

    <!-- Unten die Antworten "hier": öffentlich ohne Säule (Ausland, neue Säule) und privat in einer Zeile, direkt über "Weiter"
         in Daumenreichweite; sie bleibt stehen, wenn die Säulen darüber nachladen (Inhalt ist unten verankert). Öffentlich ist
         gesperrt, bis die ersten Säulen da sind - ein schneller Tap träfe sonst sie statt der Säule. Nur mit Ort (GPS, gewählte
         Adresse oder im Bearbeiten-Dialog der gespeicherte): ein Zuhause gibt es im Datenmodell nicht, privat heißt "nicht öffentlich". -->
    <template v-if="placeKnown">
    <Collapse :open="!!cardStrip && place === 'other'">
      <CardStrip v-if="cardStrip" :providers="cardStrip.providers" :is-public="true" :charging-type="cardStrip.chargingType"
        :community="cardStrip.community" :selected="cardStrip.selected" :price-label="cardStrip.priceLabel" @choose="c => emit('chooseCard', c)" />
    </Collapse>
    <div class="grid grid-cols-2 gap-3">
      <button type="button" data-testid="place-public" :disabled="stationsLoading && stations.length === 0"
        :class="[tileClass(place === 'other'), 'disabled:opacity-50']" @click="emit('choose', { kind: 'other', cpoName: null })">
        <span :class="iconBox('station', place === 'other')"><BoltIcon class="h-5 w-5" /></span>
        <span class="flex-1 min-w-0">
          <b class="block text-[15px] font-semibold text-gray-900 dark:text-gray-50 truncate">{{ hasPosition ? t('logwizard.place_public_here') : t('logwizard.place_public') }}</b>
          <small class="block text-xs text-gray-500 dark:text-gray-400 truncate">{{ place === 'other' && selectedCpo ? selectedCpo : t('logwizard.place_public_sub') }}</small>
        </span>
        <CheckCircleIcon v-if="place === 'other'" class="h-5 w-5 flex-shrink-0 text-gray-900 dark:text-gray-100" aria-hidden="true" />
      </button>
      <!-- Mit gewählter Adresse heißt es "Privat geladen": "hier" stimmte nicht, der Nutzer ist nicht dort -->
      <button type="button" data-testid="place-here" :class="tileClass(place === 'home')" @click="emit('choose', { kind: 'home' })">
        <span :class="iconBox('home', place === 'home')"><HomeIcon class="h-5 w-5" /></span>
        <span class="flex-1 min-w-0">
          <b class="block text-[15px] font-semibold text-gray-900 dark:text-gray-50 truncate">{{ hasPosition && !addressLabel ? t('logwizard.place_here') : t('logwizard.place_private') }}</b>
          <small class="block text-xs text-gray-500 dark:text-gray-400 truncate" :title="addressLabel ?? undefined">{{ hasPosition ? (addressLabel ?? t('logwizard.place_here_sub')) : t('logwizard.place_private_sub') }}</small>
        </span>
        <CheckCircleIcon v-if="place === 'home'" class="h-5 w-5 flex-shrink-0 text-gray-900 dark:text-gray-100" aria-hidden="true" />
      </button>
    </div>
    <!-- Gestrichelt: ein Einstieg, keine Antwort (App-CI). Nur solange die Suche nicht ohnehin offen dasteht. -->
    <button v-if="showElsewhere" type="button" data-testid="place-elsewhere" @click="openLayer"
      class="w-full min-h-11 rounded-xl border-2 border-dashed border-edge text-[14px] font-semibold text-gray-800 dark:text-gray-100 hover:bg-gray-50 dark:hover:bg-white/10 transition-colors inline-flex items-center justify-center gap-1.5">
      <MagnifyingGlassIcon class="h-4 w-4" aria-hidden="true" />{{ t('logwizard.place_elsewhere') }}
    </button>
    </template>
    <input ref="focusProxy" type="text" tabindex="-1" aria-hidden="true" class="pointer-events-none fixed left-0 top-0 h-px w-px text-[16px] opacity-0" />
    <PlaceSearchLayer v-if="layerOpen" :near="near" :origin="layerOrigin" @close="layerOpen = false" @choose="onLayerChoose" @picked="p => emit('placePicked', p)" />
  </div>
</template>
