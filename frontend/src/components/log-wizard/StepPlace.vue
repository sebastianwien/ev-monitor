<script setup lang="ts">
import { CHIP_ROW, chipClass } from './chipClass'
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowPathIcon, HomeIcon, BoltIcon, MapPinIcon, MagnifyingGlassIcon, CheckCircleIcon, Cog6ToothIcon } from '@heroicons/vue/24/outline'
import { knownIcon } from './knownIcon'
import { RADIUS_STEPS, type NearbyStation, type StationMatch } from '../../composables/useNearbyStations'
import { isLocationInaccurate, formatDistance } from './placeDistance'
import type { KnownPlace } from '../../composables/useKnownPlaces'
import { knownPlaceTitle, placeKindOf, stationIsKnown } from './knownPlace'
import PlaceMinimap from './PlaceMinimap.vue'
import type { ChargingSiteRef } from '../log-form/logFormData'
import { settingsPlatform, openAppSettings, type LocationPermission } from '../../composables/useLocationPermission'
import type { PlaceChoice, PlaceKind } from './wizardLogic'
import CardStrip, { type CardChoice, type CommunityPrice } from './CardStrip.vue'
import type { ChargingProvider } from '../../composables/useChargingProviders'
import type { PickedPlace } from '../../composables/useLocationSearch'
import PlaceSearch from './PlaceSearch.vue'
import Collapse from './Collapse.vue'
import { stationSub as stationSubBase } from './stationSub'

const props = defineProps<{
  place: PlaceKind | null
  selectedCpo: string | null
  selectedSite: ChargingSiteRef | null
  /** Orte, an denen der Nutzer schon geladen hat: mit Position nur die in seiner Zelle ("hier"), sonst die häufigsten */
  knownPlaces: KnownPlace[]
  /** Die eigene Zelle wird gerade abgefragt - der Platzhalter sagt das, statt die Liste springen zu lassen */
  knownLoading?: boolean
  /** Getippte Adresse, als Untertitel der Zeile "Hier hast du schon geladen" */
  addressLabel?: string | null
  stations: NearbyStation[]
  stationsLoading: boolean
  permission: LocationPermission
  locationStatus: 'idle' | 'loading' | 'success' | 'error'
  recentCpos: string[]
  allCpos: string[]
  /** Umkreis der aktuellen Liste in Metern, und ob "Umkreis erweitern" noch etwas bringt */
  radiusMeters?: number
  canExpand?: boolean
  /** Nächste Stufe für "Nicht dabei?" */
  nextRadius?: number | null
  /** Der Umkreis ist bis zur größten Stufe abgesucht, ohne Treffer */
  exhausted?: boolean
  /** Ungenauigkeit der Ortung in Metern; null bei gesuchter Adresse */
  locationAccuracy?: number | null
  /** Position für die Entfernung zu den letzten Ladeorten; wird nirgends hingeschickt */
  latitude?: number | null
  longitude?: number | null
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

const query = ref('')
const platform = settingsPlatform()
// Nach erfolgreicher Ortung klappt die Suche zu; "Anderer Ort" holt sie zurück
const searchReopened = ref(false)
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
const showOther = computed(() => props.place === 'other')
// Kachelreihe "Zuletzt genutzt" nur ohne Position: mit Position sagt die Zeile "hier" alles, der Rest wäre Rauschen
const tiles = computed(() => props.latitude == null ? props.knownPlaces.filter(p => !p.here) : [])
const title = (p: KnownPlace) => knownPlaceTitle(p, t)
const lastUsed = (p: KnownPlace) => new Date(p.lastUsedAt).toLocaleDateString(locale.value === 'en' ? 'en-GB' : locale.value, { day: 'numeric', month: 'short' })
const usage = (p: KnownPlace) => t('logwizard.known_usage', { n: p.usageCount, d: lastUsed(p) }, p.usageCount)
/** Die Orte in der eigenen Zelle (höchstens drei, Säule zuerst) - als grüner Block über der Liste */
const herePlaces = computed(() => props.knownPlaces.filter(p => p.here))
// Ob dieser bekannte Ort die aktuelle Wahl ist: Säule über Zelle und Name, Anbieter über den Namen, privat über Zuhause
const isKnown = (p: KnownPlace) => {
  switch (placeKindOf(p)) {
    case 'site': return atStation.value && sameSite(p.site!.name, p.site!.geohash)
    case 'other': return props.place === 'other' && props.selectedCpo === p.cpoName
    default: return props.place === 'home'
  }
}
// Der Block "Hier hast du schon geladen" bleibt, bis der Nutzer etwas anderes wählt
const showHere = computed(() => herePlaces.value.length > 0 && (props.place === null || herePlaces.value.some(isKnown)))
// Eine Säule, die schon oben als "hier" steht, erscheint in der Registerliste nicht noch einmal
const listedStations = computed(() => props.stations.filter(s => !stationIsKnown(s, herePlaces.value)))
// Getippte Adresse ohne Säule und ohne bekannten Ort: Privat oder Öffentlich, ein Tap
const addressPicked = ref(false)
const showChips = computed(() => addressPicked.value && !props.knownLoading && herePlaces.value.length === 0
  && !props.stationsLoading && props.stations.length === 0)
const onPicked = (p: PickedPlace) => { addressPicked.value = true; emit('placePicked', p) }
const filteredCpos = computed(() => {
  const q = query.value.trim().toLowerCase()
  const list = props.allCpos.filter(c => !props.recentCpos.includes(c))
  return (q ? list.filter(c => c.toLowerCase().includes(q)) : list).slice(0, 8)
})

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
  addressPicked.value = false
  emit('choose', c)
}
const showSearchedStation = computed(() => {
  const s = searchedStation.value
  return !!s && atStation.value && sameSite(s.name, s.geohash)
    && !props.stations.some(n => n.name === s.name && n.geohash === s.geohash)
})
const isOtherCpo = (c: string) => props.place === 'other' && props.selectedCpo === c

const tileClass = (on: boolean) => [
  'btn-3d w-full flex items-center gap-3 text-left p-3 rounded-sm border-2 transition',
  on ? 'border-indigo-600 bg-indigo-50 dark:bg-indigo-900/60 ring-1 ring-indigo-600 hover:bg-indigo-100 dark:hover:bg-indigo-900/70'
     : 'border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 hover:border-indigo-300 hover:bg-gray-50 dark:hover:bg-gray-700',
]
const stationSub = (s: Pick<NearbyStation, 'chargePoints' | 'maxAcKw' | 'maxDcKw'>) => stationSubBase(s, t)
</script>

<template>
  <div class="space-y-3">
    <!-- Leerzustand: keine Fehlermeldung, sondern die Frage, wo geladen wurde. Darunter Suche, Zuhause, letzte Orte. -->
    <div v-if="noStationsFound" role="status" data-testid="wizard-no-stations"
      class="p-3 rounded-sm bg-gray-100 dark:bg-gray-700/60 text-sm text-gray-700 dark:text-gray-200 space-y-1">
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
      class="flex items-center gap-3 p-3 rounded-sm bg-gray-100 dark:bg-gray-700/60">
      <MapPinIcon class="h-5 w-5 text-indigo-600 flex-shrink-0" />
      <p class="flex-1 text-sm text-gray-700 dark:text-gray-200" aria-live="polite">{{ locationStatus === 'loading' ? loadingText : t('logwizard.location_offer') }}</p>
      <button type="button" data-testid="wizard-location" :disabled="locationStatus === 'loading'" @click="emit('requestLocation')"
        class="text-sm font-semibold text-indigo-600 dark:text-indigo-300 whitespace-nowrap hover:underline disabled:no-underline disabled:opacity-60">
        <ArrowPathIcon v-if="locationStatus === 'loading'" class="h-5 w-5 animate-spin" :aria-label="t('common.loading')" />
        <template v-else>{{ t('logwizard.location_cta') }}</template>
      </button>
    </div>
    <!-- Blockiert: Anleitung je Plattform, in der App der direkte Sprung in die Einstellungen -->
    <div v-else-if="permission === 'denied' || locationStatus === 'error'" data-testid="wizard-location-blocked"
      class="p-3 rounded-sm bg-gray-100 dark:bg-gray-700/60 text-sm text-gray-700 dark:text-gray-200 space-y-2">
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
      @choose="onSearchChoose" @picked="onPicked" />
    <!-- Datenschutz-Satz zur Ortserkennung, einmal neben "Standort nutzen" -->
    <p v-if="permission === 'prompt' || permission === 'unknown'" class="text-xs text-gray-500 dark:text-gray-400">{{ t('logwizard.known_place_hint') }}</p>
    </div>
    </Collapse>
    <!-- Die eigene Zelle wird geprüft: sofort sagen, was passiert, statt die Zeile später reinspringen zu lassen -->
    <div v-if="knownLoading && !herePlaces.length" role="status" data-testid="known-loading"
      class="flex items-center gap-3 p-3 rounded-sm border-2 border-dashed border-gray-300 dark:border-gray-600 text-sm text-gray-500 dark:text-gray-400">
      <ArrowPathIcon class="h-5 w-5 animate-spin text-green-600 flex-shrink-0" aria-hidden="true" />{{ t('logwizard.known_checking') }}
    </div>
    <!-- Hier hast du schon geladen: die eigenen Orte in dieser Zelle (höchstens drei), ein Tap wählt samt Anbieter und Karte vom letzten Mal -->
    <div v-if="showHere" data-testid="known-here" class="rounded-sm border-2 border-green-600 bg-green-50 dark:bg-green-900/30 overflow-hidden">
      <div class="flex items-center gap-3 p-3">
        <span class="w-9 h-9 rounded-sm bg-white dark:bg-gray-800 grid place-items-center flex-shrink-0 text-green-700 dark:text-green-300"><MapPinIcon class="h-5 w-5" /></span>
        <span class="flex-1 min-w-0">
          <b class="block text-sm font-semibold text-green-800 dark:text-green-200">{{ t('logwizard.known_here') }}</b>
          <small class="block text-xs text-gray-600 dark:text-gray-300 truncate">{{ addressLabel ?? t('logwizard.known_here_sub') }}</small>
        </span>
        <!-- Stummes Kartenbild der Position: Bestätigung "ja, hier", nicht bedienbar -->
        <span v-if="latitude != null && longitude != null" class="w-16 h-16 rounded-sm overflow-hidden flex-shrink-0 bg-gray-200 dark:bg-gray-700">
          <PlaceMinimap :lat="latitude" :lon="longitude" />
        </span>
      </div>
      <button v-for="p in herePlaces" :key="p.geohash + (p.site?.id ?? '')" type="button" :data-testid="`known-here-${p.geohash}`"
        class="w-full flex items-center gap-3 text-left px-3 py-2.5 min-h-11 border-t border-green-200 dark:border-green-800 bg-white/70 dark:bg-gray-900/30 hover:bg-white dark:hover:bg-gray-900/50 transition"
        @click="emit('choose', { kind: 'known', place: p })">
        <component :is="knownIcon(p)" class="h-5 w-5 text-gray-600 dark:text-gray-300 flex-shrink-0" />
        <span class="flex-1 min-w-0">
          <b class="block text-sm font-semibold text-gray-800 dark:text-gray-100 truncate">{{ title(p) }}</b>
          <small class="block text-xs text-gray-500 dark:text-gray-400">{{ usage(p) }}</small>
        </span>
        <CheckCircleIcon v-if="isKnown(p)" class="h-5 w-5 text-green-700 dark:text-green-300" />
      </button>
    </div>
    <!-- Getippte Adresse an unbekanntem Ort: Privat oder Öffentlich, dann Weiter -->
    <div v-if="showChips" data-testid="known-chips" :class="CHIP_ROW">
      <button type="button" :aria-pressed="place === 'home'" :class="chipClass(place === 'home')" @click="emit('choose', { kind: 'home' })">
        <HomeIcon class="h-4 w-4 inline mr-1 -mt-0.5" />{{ t('logwizard.chip_private') }}
      </button>
      <button type="button" :aria-pressed="place === 'other' && !selectedCpo" :class="chipClass(place === 'other' && !selectedCpo)"
        @click="emit('choose', { kind: 'other', cpoName: null })">
        <BoltIcon class="h-4 w-4 inline mr-1 -mt-0.5" />{{ t('logwizard.chip_public') }}
      </button>
    </div>
    <!-- Streifen fährt oberhalb der Kachel aus: die getippte Kachel bleibt stehen (Liste ist unten verankert) -->
    <Collapse :open="!!cardStrip && showSearchedStation">
      <CardStrip v-if="cardStrip" :providers="cardStrip.providers" :is-public="true" :charging-type="cardStrip.chargingType"
        :community="cardStrip.community" :selected="cardStrip.selected" :price-label="cardStrip.priceLabel" @choose="c => emit('chooseCard', c)" />
    </Collapse>
    <button v-if="showSearchedStation && searchedStation" type="button" data-testid="place-searched-station" :class="tileClass(true)"
      @click="emit('choose', { kind: 'station', station: searchedStation, viaSearch: true })">
      <span class="w-9 h-9 rounded-sm bg-gray-100 dark:bg-gray-700 grid place-items-center flex-shrink-0"><BoltIcon class="h-5 w-5" /></span>
      <span class="flex-1 min-w-0">
        <b class="block text-sm font-semibold text-gray-800 dark:text-gray-100 truncate">{{ searchedStation.name }}</b>
        <small class="block text-xs text-gray-500 dark:text-gray-400">{{ stationSub(searchedStation) }}</small>
        <small v-if="searchedStation.address" class="block text-xs text-gray-400 dark:text-gray-500 truncate">{{ searchedStation.address }}</small>
      </span>
      <CheckCircleIcon class="h-5 w-5 text-indigo-600" />
    </button>

    <button type="button" data-testid="place-home" :class="tileClass(place === 'home')" @click="emit('choose', { kind: 'home' })">
      <span class="w-9 h-9 rounded-sm bg-gray-100 dark:bg-gray-700 grid place-items-center flex-shrink-0"><HomeIcon class="h-5 w-5" /></span>
      <span class="flex-1 min-w-0">
        <b class="block text-sm font-semibold text-gray-800 dark:text-gray-100">{{ t('logwizard.place_home') }}</b>
        <small class="block text-xs text-gray-500 dark:text-gray-400">{{ t('logwizard.place_home_sub') }}</small>
      </span>
      <CheckCircleIcon v-if="place === 'home'" class="h-5 w-5 text-indigo-600" />
    </button>

    <!-- Spinner nur bei der Ortssuche; bei der Live-Ortung dreht er in der Standort-Card -->
    <div v-if="stationsLoading && locationStatus !== 'loading'" role="status" data-testid="stations-loading"
      class="flex flex-col items-center gap-2 py-4 text-sm text-gray-500 dark:text-gray-400">
      <ArrowPathIcon class="h-8 w-8 animate-spin text-indigo-600" aria-hidden="true" />
      <span>{{ loadingText }}</span>
    </div>
    <Collapse :open="stations.length > 0">
    <div class="space-y-3">
      <p class="flex items-baseline text-[11px] uppercase tracking-wide text-gray-400 dark:text-gray-500 pt-1">
        {{ radiusMeters && radiusMeters > 250 ? t('logwizard.nearby_title_radius', { r: radiusLabel }) : t('logwizard.nearby_title') }}
        <button v-if="!showLocationBlock" type="button" class="ml-auto normal-case tracking-normal text-xs font-semibold text-indigo-600 dark:text-indigo-300 hover:underline"
          @click="searchReopened = true">{{ t('logwizard.nearby_other_place') }}</button>
      </p>
      <template v-for="s in listedStations" :key="s.name">
      <!-- Streifen fährt oberhalb der Kachel aus: die getippte Kachel bleibt stehen (Liste ist unten verankert) -->
      <Collapse :open="!!cardStrip && isStation(s)">
        <CardStrip v-if="cardStrip" :providers="cardStrip.providers" :is-public="true" :charging-type="cardStrip.chargingType"
        :community="cardStrip.community" :selected="cardStrip.selected" :price-label="cardStrip.priceLabel" @choose="c => emit('chooseCard', c)" />
      </Collapse>
      <button type="button" :class="tileClass(isStation(s))"
        @click="emit('choose', { kind: 'station', station: s })">
        <span class="w-9 h-9 rounded-sm bg-gray-100 dark:bg-gray-700 grid place-items-center flex-shrink-0"><BoltIcon class="h-5 w-5" /></span>
        <span class="flex-1 min-w-0">
          <b class="block text-sm font-semibold text-gray-800 dark:text-gray-100 truncate">{{ s.name }}</b>
          <small class="block text-xs text-gray-500 dark:text-gray-400">{{ stationSub(s) }}</small>
          <small v-if="s.address" class="block text-xs text-gray-400 dark:text-gray-500 truncate">{{ s.address }}</small>
        </span>
        <span class="text-xs tabular-nums text-gray-400 whitespace-nowrap">{{ dist(s.distanceMeters) }}</span>
        <CheckCircleIcon v-if="isStation(s)" class="h-5 w-5 text-indigo-600" />
      </button>
      </template>
      <!-- "Nicht dabei?": die weite Suche ersetzt diese Liste, statt sie zu verlängern -->
      <button v-if="canExpand && !stationsLoading" type="button" data-testid="expand-radius" @click="emit('expandRadius')"
        class="btn-3d w-full min-h-11 rounded-sm border-2 border-dashed border-gray-300 dark:border-gray-600 text-sm font-semibold text-indigo-600 dark:text-indigo-300 hover:border-indigo-400 transition">
        {{ t('logwizard.expand_radius', { r: dist(nextRadius ?? RADIUS_STEPS[RADIUS_STEPS.length - 1]) }) }}
      </button>
    </div>
    </Collapse>

    <!-- Zuletzt genutzt: Säulen und Orte gemischt, sobald es welche gibt - sonst die Anbieter der letzten Logs -->
    <template v-if="tiles.length">
      <p class="text-[11px] uppercase tracking-wide text-gray-400 dark:text-gray-500 pt-1">{{ t('logwizard.recent_title') }}</p>
      <template v-for="p in tiles" :key="p.geohash + (p.site?.id ?? '')">
      <!-- Streifen fährt oberhalb der Kachel aus: die getippte Kachel bleibt stehen (Liste ist unten verankert) -->
      <Collapse :open="!!cardStrip && p.isPublic && isKnown(p)">
        <CardStrip v-if="cardStrip" :providers="cardStrip.providers" :is-public="true" :charging-type="cardStrip.chargingType"
        :community="cardStrip.community" :selected="cardStrip.selected" :price-label="cardStrip.priceLabel" @choose="c => emit('chooseCard', c)" />
      </Collapse>
      <button type="button" :class="tileClass(isKnown(p))" :data-testid="p.site ? `recent-site-${p.site.id}` : `known-place-${p.geohash}`"
        @click="emit('choose', { kind: 'known', place: p })">
        <span class="w-9 h-9 rounded-sm bg-gray-100 dark:bg-gray-700 grid place-items-center flex-shrink-0"><component :is="knownIcon(p)" class="h-5 w-5" /></span>
        <span class="flex-1 min-w-0">
          <b class="block text-sm font-semibold text-gray-800 dark:text-gray-100 truncate">{{ title(p) }}</b>
          <small v-if="p.site" class="block text-xs text-gray-500 dark:text-gray-400">{{ stationSub(p.site) }}</small>
          <small v-else class="block text-xs text-gray-500 dark:text-gray-400">{{ usage(p) }}</small>
          <small v-if="p.site?.address" class="block text-xs text-gray-400 dark:text-gray-500 truncate">{{ p.site.address }}</small>
        </span>
        <span v-if="p.site" class="text-xs tabular-nums text-gray-400 whitespace-nowrap">{{ t('logwizard.site_usage', { n: p.usageCount }, p.usageCount) }}</span>
        <CheckCircleIcon v-if="isKnown(p)" class="h-5 w-5 text-indigo-600" />
      </button>
      </template>
    </template>
    <template v-else-if="recentCpos.length">
      <p class="text-[11px] uppercase tracking-wide text-gray-400 dark:text-gray-500 pt-1">{{ t('logwizard.recent_title') }}</p>
      <button v-for="c in recentCpos" :key="c" type="button" :class="tileClass(isOtherCpo(c))"
        @click="emit('choose', { kind: 'other', cpoName: c })">
        <span class="w-9 h-9 rounded-sm bg-gray-100 dark:bg-gray-700 grid place-items-center flex-shrink-0"><MapPinIcon class="h-5 w-5" /></span>
        <b class="flex-1 text-sm font-semibold text-gray-800 dark:text-gray-100 truncate">{{ c }}</b>
        <CheckCircleIcon v-if="isOtherCpo(c)" class="h-5 w-5 text-indigo-600" />
      </button>
    </template>

    <button type="button" data-testid="place-other" :class="tileClass(showOther && !selectedCpo)"
      @click="emit('choose', { kind: 'other', cpoName: null })">
      <span class="w-9 h-9 rounded-sm bg-gray-100 dark:bg-gray-700 grid place-items-center flex-shrink-0"><MagnifyingGlassIcon class="h-5 w-5" /></span>
      <span class="flex-1 min-w-0">
        <b class="block text-sm font-semibold text-gray-800 dark:text-gray-100">{{ t('logwizard.place_other') }}</b>
        <small class="block text-xs text-gray-500 dark:text-gray-400">{{ noStationsFound ? t('logwizard.place_other_sub_missing') : t('logwizard.place_other_sub') }}</small>
      </span>
    </button>

    <div v-if="showOther" class="space-y-2 pl-1">
      <input v-model="query" type="search" :placeholder="t('logfields.cpo_select_placeholder')"
        class="w-full rounded-sm border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 p-2 text-sm" />
      <div :class="CHIP_ROW">
        <button v-for="c in filteredCpos" :key="c" type="button" :aria-pressed="isOtherCpo(c)"
          @click="emit('choose', { kind: 'other', cpoName: c })"
          :class="chipClass(isOtherCpo(c))">
          {{ c }}
        </button>
      </div>
      <!-- Streifen faehrt animiert auf, statt die Liste springen zu lassen -->
      <Collapse :open="!!cardStrip && !!selectedCpo">
        <CardStrip v-if="cardStrip" :providers="cardStrip.providers" :is-public="true" :charging-type="cardStrip.chargingType"
        :community="cardStrip.community" :selected="cardStrip.selected" :price-label="cardStrip.priceLabel" @choose="c => emit('chooseCard', c)" />
      </Collapse>
    </div>
  </div>
</template>
