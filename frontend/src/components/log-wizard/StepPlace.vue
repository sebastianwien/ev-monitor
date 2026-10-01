<script setup lang="ts">
import { CHIP_ROW, chipClass } from './chipClass'
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowPathIcon, HomeIcon, BoltIcon, MapPinIcon, MagnifyingGlassIcon, CheckCircleIcon, Cog6ToothIcon } from '@heroicons/vue/24/outline'
import type { NearbyStation, StationMatch } from '../../composables/useNearbyStations'
import type { RecentSite } from '../../composables/useRecentSites'
import type { ChargingSiteRef } from '../log-form/logFormData'
import { settingsPlatform, openAppSettings, type LocationPermission } from '../../composables/useLocationPermission'
import type { ChargingSuggestion, PlaceChoice, PlaceKind } from './wizardLogic'
import SuggestionCard from './SuggestionCard.vue'
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
  recentSites: RecentSite[]
  stations: NearbyStation[]
  stationsLoading: boolean
  permission: LocationPermission
  locationStatus: 'idle' | 'loading' | 'success' | 'error'
  recentCpos: string[]
  allCpos: string[]
  /** Treffer aus Position x eigene Logs - steht als Karte über allem, bis der Nutzer ihn wegtippt */
  suggestion?: ChargingSuggestion | null
  suggestionProviderLabel?: string | null
  /** Umkreis der aktuellen Liste in Metern, und ob "Umkreis erweitern" noch etwas bringt */
  radiusMeters?: number
  canExpand?: boolean
  /** Ladekarten-Streifen über der gewählten Säule; fehlt im Bearbeiten-Dialog */
  cardStrip?: {
    providers: ChargingProvider[]
    chargingType: 'AC' | 'DC'
    community: CommunityPrice | null
    selected: string | null
    priceLabel: (eur: number) => string
  } | null
}>()
const emit = defineEmits<{ choose: [choice: PlaceChoice]; requestLocation: []; placePicked: [place: PickedPlace]; acceptSuggestion: []; expandRadius: []; chooseCard: [choice: CardChoice] }>()
const { t } = useI18n()

const query = ref('')
const platform = settingsPlatform()
// Nach erfolgreicher Ortung klappt die Suche zu; "Anderer Ort" holt sie zurück
const searchReopened = ref(false)
// "Anderer Ort" auf der Trefferkarte: Karte weg, Liste da - bis zur nächsten Ortung
const suggestionDismissed = ref(false)
const showSuggestion = computed(() => !!props.suggestion && !suggestionDismissed.value)
const radiusLabel = computed(() => props.radiusMeters && props.radiusMeters >= 1000
  ? `${(props.radiusMeters / 1000).toLocaleString(undefined, { maximumFractionDigits: 1 })} km` : `${props.radiusMeters ?? 250} m`)
const showLocationBlock = computed(() => props.locationStatus !== 'success' || searchReopened.value)
const showOther = computed(() => props.place === 'other')
// Ort steht, aber das Register kennt dort keine Säule: ohne Kachel bliebe "Weiter" grau
const noStationsFound = computed(() =>
  props.locationStatus === 'success' && !props.stationsLoading && props.stations.length === 0 && !searchReopened.value)
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
  emit('choose', c)
}
const showSearchedStation = computed(() => {
  const s = searchedStation.value
  return !!s && atStation.value && sameSite(s.name, s.geohash)
    && !props.stations.some(n => n.name === s.name && n.geohash === s.geohash)
})
const isSite = (s: RecentSite) => props.place === 'site' && sameSite(s.name, s.geohash)
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
    <!-- Standort-Card und Ortssuche kollabieren gemeinsam, sobald die Position steht -->
    <Collapse :open="showLocationBlock">
    <div class="space-y-3">
    <!-- Standort: noch nie gefragt -> Hinweis-Card mit Button, Dialog erst beim Tap.
         Bereits erlaubt -> dieselbe Card zeigt waehrend der automatischen Ortung den Spinner. -->
    <div v-if="permission === 'prompt' || permission === 'unknown' || locationStatus === 'loading'"
      class="flex items-center gap-3 p-3 rounded-sm bg-gray-100 dark:bg-gray-700/60">
      <MapPinIcon class="h-5 w-5 text-indigo-600 flex-shrink-0" />
      <p class="flex-1 text-sm text-gray-700 dark:text-gray-200">{{ locationStatus === 'loading' ? t('logwizard.nearby_loading') : t('logwizard.location_offer') }}</p>
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
      @choose="onSearchChoose" @picked="p => emit('placePicked', p)" />
    </div>
    </Collapse>
    <SuggestionCard v-if="showSuggestion && suggestion" :suggestion="suggestion" :provider-label="suggestionProviderLabel ?? null"
      @accept="emit('acceptSuggestion')" @dismiss="suggestionDismissed = true" />
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
      <span>{{ t('logwizard.nearby_loading') }}</span>
    </div>
    <div v-if="noStationsFound" data-testid="wizard-no-stations"
      class="p-3 rounded-sm bg-gray-100 dark:bg-gray-700/60 text-sm text-gray-700 dark:text-gray-200 space-y-2">
      <p>{{ t('logwizard.no_stations_found') }}</p>
      <div :class="CHIP_ROW">
        <button v-if="canExpand" type="button" data-testid="expand-radius" :class="chipClass(false)" @click="emit('expandRadius')">
          <ArrowPathIcon class="h-4 w-4 inline mr-1 -mt-0.5" />{{ t('logwizard.expand_radius') }}
        </button>
        <button type="button" :class="chipClass(false)" @click="searchReopened = true">
          <MagnifyingGlassIcon class="h-4 w-4 inline mr-1 -mt-0.5" />{{ t('logwizard.nearby_other_place') }}
        </button>
      </div>
    </div>
    <Collapse :open="stations.length > 0">
    <div class="space-y-3">
      <p class="flex items-baseline text-[11px] uppercase tracking-wide text-gray-400 dark:text-gray-500 pt-1">
        {{ radiusMeters && radiusMeters > 250 ? t('logwizard.nearby_title_radius', { r: radiusLabel }) : t('logwizard.nearby_title') }}
        <button v-if="!showLocationBlock" type="button" class="ml-auto normal-case tracking-normal text-xs font-semibold text-indigo-600 dark:text-indigo-300 hover:underline"
          @click="searchReopened = true">{{ t('logwizard.nearby_other_place') }}</button>
      </p>
      <template v-for="s in stations" :key="s.name">
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
        <span class="text-xs tabular-nums text-gray-400 whitespace-nowrap">{{ s.distanceMeters >= 1000 ? `${(s.distanceMeters / 1000).toFixed(1)} km` : `${s.distanceMeters} m` }}</span>
        <CheckCircleIcon v-if="isStation(s)" class="h-5 w-5 text-indigo-600" />
      </button>
      </template>
      <!-- "Nicht dabei?": die weite Suche ersetzt diese Liste, statt sie zu verlängern -->
      <button v-if="canExpand && !stationsLoading" type="button" data-testid="expand-radius" @click="emit('expandRadius')"
        class="btn-3d w-full min-h-11 rounded-sm border-2 border-dashed border-gray-300 dark:border-gray-600 text-sm font-semibold text-indigo-600 dark:text-indigo-300 hover:border-indigo-400 transition">
        {{ t('logwizard.expand_radius') }}
      </button>
    </div>
    </Collapse>

    <!-- Zuletzt genutzt: echte Standorte, sobald es welche gibt - sonst die Anbieter der letzten Logs -->
    <template v-if="recentSites.length">
      <p class="text-[11px] uppercase tracking-wide text-gray-400 dark:text-gray-500 pt-1">{{ t('logwizard.recent_title') }}</p>
      <template v-for="s in recentSites" :key="s.id">
      <!-- Streifen fährt oberhalb der Kachel aus: die getippte Kachel bleibt stehen (Liste ist unten verankert) -->
      <Collapse :open="!!cardStrip && isSite(s)">
        <CardStrip v-if="cardStrip" :providers="cardStrip.providers" :is-public="true" :charging-type="cardStrip.chargingType"
        :community="cardStrip.community" :selected="cardStrip.selected" :price-label="cardStrip.priceLabel" @choose="c => emit('chooseCard', c)" />
      </Collapse>
      <button type="button" :class="tileClass(isSite(s))" :data-testid="`recent-site-${s.id}`"
        @click="emit('choose', { kind: 'site', site: s })">
        <span class="w-9 h-9 rounded-sm bg-gray-100 dark:bg-gray-700 grid place-items-center flex-shrink-0"><BoltIcon class="h-5 w-5" /></span>
        <span class="flex-1 min-w-0">
          <b class="block text-sm font-semibold text-gray-800 dark:text-gray-100 truncate">{{ s.name }}</b>
          <small class="block text-xs text-gray-500 dark:text-gray-400">{{ stationSub(s) }}</small>
          <small v-if="s.address" class="block text-xs text-gray-400 dark:text-gray-500 truncate">{{ s.address }}</small>
        </span>
        <span class="text-xs tabular-nums text-gray-400 whitespace-nowrap">{{ t('logwizard.site_usage', { n: s.usageCount }, s.usageCount) }}</span>
        <CheckCircleIcon v-if="isSite(s)" class="h-5 w-5 text-indigo-600" />
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
        <small class="block text-xs text-gray-500 dark:text-gray-400">{{ t('logwizard.place_other_sub') }}</small>
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
