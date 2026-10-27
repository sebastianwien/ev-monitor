<script setup lang="ts">
import { CHIP_ROW, chipClass, cardClass } from './chipClass'
import { computed, nextTick, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowPathIcon, HomeIcon, BoltIcon, MapPinIcon, MagnifyingGlassIcon, CheckCircleIcon, Cog6ToothIcon } from '@heroicons/vue/24/outline'
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
import { stationSub as stationSubBase } from './stationSub'

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
// Nach erfolgreicher Ortung klappt die Suche zu; "Woanders" holt sie zurück
const searchReopened = ref(false)
const reopenSearch = async () => {
  searchReopened.value = true
  await nextTick()
  document.getElementById('wizard-place-search')?.focus()
}
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
const showLocationBlock = computed(() => props.locationStatus !== 'success' || searchReopened.value || listEmpty.value)
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

/**
 * Icon-Box der Kacheln: Farbe sagt den Ortstyp (grün privat, gelb Säule), bewusst bunt (Ausnahme in
 * docs/design/ci.md). "Ausgewählt" sagt die eingedrückte Kachel mit Rand gray-900.
 */
const iconBox = (kind: 'home' | 'station', on: boolean) => [
  'w-9 h-9 rounded-lg grid place-items-center flex-shrink-0 transition-colors',
  kind === 'home'
    ? (on ? 'bg-emerald-500 text-white' : 'bg-emerald-50 text-emerald-600 dark:bg-emerald-900/40 dark:text-emerald-300')
    : (on ? 'bg-amber-500 text-white' : 'bg-amber-50 text-amber-600 dark:bg-amber-900/40 dark:text-amber-300'),
]
const tileClass = (on: boolean) => [cardClass(on), 'w-full flex items-center gap-3 p-3']
const stationSub = (s: Pick<NearbyStation, 'chargePoints' | 'maxAcKw' | 'maxDcKw'>) => stationSubBase(s, t)
</script>

<template>
  <div class="space-y-3">
    <!-- Leerzustand: keine Fehlermeldung, sondern die Frage, wo geladen wurde. Darunter Suche, privat hier, ganz unten öffentlich ohne Säule. -->
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
    <button v-if="showSearchedStation && searchedStation" type="button" data-testid="place-searched-station" :class="tileClass(true)"
      @click="emit('choose', { kind: 'station', station: searchedStation, viaSearch: true })">
      <span :class="iconBox('station', true)"><BoltIcon class="h-5 w-5" /></span>
      <span class="flex-1 min-w-0">
        <b class="block text-[15px] font-semibold text-gray-900 dark:text-gray-50 truncate">{{ searchedStation.name }}</b>
        <small class="block text-xs text-gray-500 dark:text-gray-400">{{ stationSub(searchedStation) }}</small>
        <small v-if="searchedStation.address" class="block text-xs text-gray-500 dark:text-gray-400 truncate">{{ searchedStation.address }}</small>
      </span>
      <CheckCircleIcon class="h-5 w-5 text-gray-900 dark:text-gray-100" aria-hidden="true" />
    </button>

    <!-- Privat hier und "Woanders" nebeneinander: kurze, feste Zeilen. Nur mit Ort (GPS, gewählte Adresse oder im
         Bearbeiten-Dialog der gespeicherte) - ein Zuhause gibt es im Datenmodell nicht, privat heißt "nicht öffentlich". -->
    <div v-if="placeKnown" class="grid grid-cols-2 gap-3">
      <button type="button" data-testid="place-here" :class="[tileClass(place === 'home'), !showElsewhere && 'col-span-2']" @click="emit('choose', { kind: 'home' })">
        <span :class="iconBox('home', place === 'home')"><HomeIcon class="h-5 w-5" /></span>
        <span class="flex-1 min-w-0">
          <b class="block text-[15px] font-semibold text-gray-900 dark:text-gray-50">{{ hasPosition ? t('logwizard.place_here') : t('logwizard.place_private') }}</b>
          <small class="block text-xs text-gray-500 dark:text-gray-400 truncate" :title="addressLabel ?? undefined">{{ hasPosition ? (addressLabel ?? t('logwizard.place_here_sub')) : t('logwizard.place_private_sub') }}</small>
        </span>
        <CheckCircleIcon v-if="place === 'home'" class="h-5 w-5 flex-shrink-0 text-gray-900 dark:text-gray-100" aria-hidden="true" />
      </button>
      <button v-if="showElsewhere" type="button" data-testid="place-elsewhere" :class="tileClass(false)" @click="reopenSearch">
        <span class="w-9 h-9 rounded-lg bg-gray-100 dark:bg-white/10 grid place-items-center flex-shrink-0"><MagnifyingGlassIcon class="h-5 w-5" /></span>
        <span class="flex-1 min-w-0">
          <b class="block text-[15px] font-semibold text-gray-900 dark:text-gray-50">{{ t('logwizard.place_elsewhere') }}</b>
          <small class="block text-xs text-gray-500 dark:text-gray-400 truncate">{{ t('logwizard.place_elsewhere_sub') }}</small>
        </span>
      </button>
    </div>

    <!-- Spinner nur bei der Ortssuche; bei der Live-Ortung dreht er in der Standort-Card, beim Erweitern unter der Liste -->
    <div v-if="stationsLoading && locationStatus !== 'loading' && stations.length === 0" role="status" data-testid="stations-loading"
      class="flex flex-col items-center gap-2 py-4 text-sm text-gray-500 dark:text-gray-400">
      <ArrowPathIcon class="h-8 w-8 animate-spin text-gray-500 dark:text-gray-400" aria-hidden="true" />
      <span>{{ loadingText }}</span>
    </div>
    <Collapse :open="stations.length > 0">
    <div class="space-y-3">
      <p class="flex items-baseline text-[12px] font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400 pt-1">
        {{ radiusMeters && radiusMeters > 250 ? t('logwizard.nearby_title_radius', { r: radiusLabel }) : t('logwizard.nearby_title') }}
      </p>
      <template v-for="s in stations" :key="s.name">
      <!-- Streifen fährt oberhalb der Kachel aus: die getippte Kachel bleibt stehen (Liste ist unten verankert) -->
      <Collapse :open="!!cardStrip && isStation(s)">
        <CardStrip v-if="cardStrip" :providers="cardStrip.providers" :is-public="true" :charging-type="cardStrip.chargingType"
        :community="cardStrip.community" :selected="cardStrip.selected" :price-label="cardStrip.priceLabel" @choose="c => emit('chooseCard', c)" />
      </Collapse>
      <button type="button" :class="tileClass(isStation(s))"
        @click="emit('choose', { kind: 'station', station: s })">
        <span :class="iconBox('station', isStation(s))"><BoltIcon class="h-5 w-5" /></span>
        <span class="flex-1 min-w-0">
          <b class="block text-[15px] font-semibold text-gray-900 dark:text-gray-50 truncate">{{ s.name }}</b>
          <small class="block text-xs text-gray-500 dark:text-gray-400">{{ stationSub(s) }}</small>
          <small v-if="s.address" class="block text-xs text-gray-500 dark:text-gray-400 truncate">{{ s.address }}</small>
        </span>
        <span class="text-xs tabular-nums text-gray-500 dark:text-gray-400 whitespace-nowrap">{{ dist(s.distanceMeters) }}</span>
        <CheckCircleIcon v-if="isStation(s)" class="h-5 w-5 text-gray-900 dark:text-gray-100" aria-hidden="true" />
      </button>
      </template>
      <!-- "Nicht dabei?": die weite Suche ersetzt diese Liste, statt sie zu verlängern -->
      <!-- Beim Erweitern dreht der Spinner dort, wo der Knopf war: dort schaut der Nutzer hin -->
      <div v-if="stationsLoading" role="status" data-testid="stations-loading"
        class="min-h-11 flex items-center justify-center gap-2 text-sm text-gray-500 dark:text-gray-400">
        <ArrowPathIcon class="h-5 w-5 animate-spin text-gray-500 dark:text-gray-400" aria-hidden="true" />
        <span>{{ t('logwizard.nearby_searching_radius', { r: radiusLabel }) }}</span>
      </div>
      <button v-else-if="canExpand" type="button" data-testid="expand-radius" @click="emit('expandRadius')"
        class="w-full min-h-11 rounded-xl border-2 border-dashed border-edge text-[14px] font-semibold text-gray-800 dark:text-gray-100 hover:bg-gray-50 dark:hover:bg-white/10 transition-colors">
        {{ t('logwizard.expand_radius', { r: dist(nextRadius ?? RADIUS_STEPS[RADIUS_STEPS.length - 1]) }) }}
      </button>
    </div>
    </Collapse>

    <!-- Öffentlich ohne Säule aus dem Register (Ausland, neue Säule): ganz unten, damit niemand sie statt der Säule tippt.
         Erst wenn die ersten Säulen geladen sind - sonst träfe ein schneller Tap diese Zeile. Der Ort bleibt die Position. -->
    <template v-if="placeKnown && !(stationsLoading && stations.length === 0)">
    <Collapse :open="!!cardStrip && place === 'other'">
      <CardStrip v-if="cardStrip" :providers="cardStrip.providers" :is-public="true" :charging-type="cardStrip.chargingType"
        :community="cardStrip.community" :selected="cardStrip.selected" :price-label="cardStrip.priceLabel" @choose="c => emit('chooseCard', c)" />
    </Collapse>
    <button type="button" data-testid="place-public" :class="tileClass(place === 'other')" @click="emit('choose', { kind: 'other', cpoName: null })">
      <span :class="iconBox('station', place === 'other')"><BoltIcon class="h-5 w-5" /></span>
      <span class="flex-1 min-w-0">
        <b class="block text-[15px] font-semibold text-gray-900 dark:text-gray-50">{{ hasPosition ? t('logwizard.place_public_here') : t('logwizard.place_public') }}</b>
        <small class="block text-xs text-gray-500 dark:text-gray-400 truncate">{{ place === 'other' && selectedCpo ? selectedCpo : t('logwizard.place_public_sub') }}</small>
      </span>
      <CheckCircleIcon v-if="place === 'other'" class="h-5 w-5 text-gray-900 dark:text-gray-100" aria-hidden="true" />
    </button>
    </template>
  </div>
</template>
