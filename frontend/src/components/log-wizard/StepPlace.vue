<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowPathIcon, HomeIcon, BoltIcon, MapPinIcon, MagnifyingGlassIcon, CheckCircleIcon } from '@heroicons/vue/24/outline'
import type { NearbyStation } from '../../composables/useNearbyStations'
import type { LocationPermission } from '../../composables/useLocationPermission'
import type { PlaceChoice, PlaceKind } from './wizardLogic'
import type { PickedPlace } from '../../composables/useLocationSearch'
import PlaceSearch from './PlaceSearch.vue'

const props = defineProps<{
  place: PlaceKind | null
  selectedCpo: string | null
  stations: NearbyStation[]
  stationsLoading: boolean
  permission: LocationPermission
  locationStatus: 'idle' | 'loading' | 'success' | 'error'
  recentCpos: string[]
  allCpos: string[]
}>()
const emit = defineEmits<{ choose: [choice: PlaceChoice]; requestLocation: []; placePicked: [place: PickedPlace] }>()
const { t } = useI18n()

const query = ref('')
const showOther = computed(() => props.place === 'other')
const filteredCpos = computed(() => {
  const q = query.value.trim().toLowerCase()
  const list = props.allCpos.filter(c => !props.recentCpos.includes(c))
  return (q ? list.filter(c => c.toLowerCase().includes(q)) : list).slice(0, 8)
})

const isStation = (s: NearbyStation) => props.place === 'station' && props.selectedCpo === s.name
const isOtherCpo = (c: string) => props.place === 'other' && props.selectedCpo === c

const tileClass = (on: boolean) => [
  'btn-3d w-full flex items-center gap-3 text-left p-3 rounded-sm border-2 transition',
  on ? 'border-indigo-600 bg-indigo-50 dark:bg-indigo-900/30 hover:bg-indigo-100 dark:hover:bg-indigo-900/50'
     : 'border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 hover:border-indigo-300 hover:bg-gray-50 dark:hover:bg-gray-700',
]
const stationSub = (s: NearbyStation) => [
  s.chargePoints ? t('logwizard.charge_points', { n: s.chargePoints }, s.chargePoints) : null,
  s.maxPowerKw ? `${Math.round(s.maxPowerKw)} kW` : null,
  s.fastCharging ? 'DC' : 'AC',
].filter(Boolean).join(' · ')
</script>

<template>
  <div class="space-y-3">
    <!-- Standort: noch nie gefragt -> Hinweis-Card mit Button, Dialog erst beim Tap -->
    <div v-if="permission === 'prompt' || permission === 'unknown'"
      class="flex items-center gap-3 p-3 rounded-sm bg-gray-100 dark:bg-gray-700/60">
      <MapPinIcon class="h-5 w-5 text-indigo-600 flex-shrink-0" />
      <p class="flex-1 text-sm text-gray-700 dark:text-gray-200">{{ t('logwizard.location_offer') }}</p>
      <button type="button" data-testid="wizard-location" :disabled="locationStatus === 'loading'" @click="emit('requestLocation')"
        class="text-sm font-semibold text-indigo-600 dark:text-indigo-300 whitespace-nowrap hover:underline disabled:no-underline disabled:opacity-60">
        {{ locationStatus === 'loading' ? t('common.loading') : t('logwizard.location_cta') }}
      </button>
    </div>
    <p v-else-if="permission === 'denied' || locationStatus === 'error'" class="text-xs text-gray-500 dark:text-gray-400">
      {{ t('logwizard.location_blocked') }}
    </p>

    <!-- Ohne Live-Position: Ort suchen - laedt danach ebenfalls die Saeulen im Umkreis -->
    <PlaceSearch v-if="locationStatus !== 'success'" :label="t('logwizard.place_search')" :placeholder="t('logfields.location_create_placeholder')"
      @picked="p => emit('placePicked', p)" />

    <button type="button" data-testid="place-home" :class="tileClass(place === 'home')" @click="emit('choose', { kind: 'home' })">
      <span class="w-9 h-9 rounded-sm bg-gray-100 dark:bg-gray-700 grid place-items-center flex-shrink-0"><HomeIcon class="h-5 w-5" /></span>
      <span class="flex-1 min-w-0">
        <b class="block text-sm font-semibold text-gray-800 dark:text-gray-100">{{ t('logwizard.place_home') }}</b>
        <small class="block text-xs text-gray-500 dark:text-gray-400">{{ t('logwizard.place_home_sub') }}</small>
      </span>
      <CheckCircleIcon v-if="place === 'home'" class="h-5 w-5 text-indigo-600" />
    </button>

    <div v-if="stationsLoading" role="status" data-testid="stations-loading"
      class="flex flex-col items-center gap-2 py-4 text-sm text-gray-500 dark:text-gray-400">
      <ArrowPathIcon class="h-8 w-8 animate-spin text-indigo-600" aria-hidden="true" />
      <span>{{ t('logwizard.nearby_loading') }}</span>
    </div>
    <template v-if="stations.length">
      <p class="text-[11px] uppercase tracking-wide text-gray-400 dark:text-gray-500 pt-1">{{ t('logwizard.nearby_title') }}</p>
      <button v-for="s in stations" :key="s.name" type="button" :class="tileClass(isStation(s))"
        @click="emit('choose', { kind: 'station', station: s })">
        <span class="w-9 h-9 rounded-sm bg-gray-100 dark:bg-gray-700 grid place-items-center flex-shrink-0"><BoltIcon class="h-5 w-5" /></span>
        <span class="flex-1 min-w-0">
          <b class="block text-sm font-semibold text-gray-800 dark:text-gray-100 truncate">{{ s.name }}</b>
          <small class="block text-xs text-gray-500 dark:text-gray-400">{{ stationSub(s) }}</small>
        </span>
        <span class="text-xs tabular-nums text-gray-400 whitespace-nowrap">{{ s.distanceMeters }} m</span>
        <CheckCircleIcon v-if="isStation(s)" class="h-5 w-5 text-indigo-600" />
      </button>
    </template>

    <template v-if="recentCpos.length">
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
      <div class="flex flex-wrap gap-2">
        <button v-for="c in filteredCpos" :key="c" type="button" :aria-pressed="isOtherCpo(c)"
          @click="emit('choose', { kind: 'other', cpoName: c })"
          :class="['px-3 py-1.5 rounded-full text-sm border transition', isOtherCpo(c) ? 'bg-indigo-600 text-white border-indigo-600 hover:bg-indigo-700' : 'border-gray-300 dark:border-gray-600 text-gray-700 dark:text-gray-200 hover:border-indigo-400 hover:bg-indigo-50 dark:hover:bg-indigo-900/30']">
          {{ c }}
        </button>
      </div>
    </div>
  </div>
</template>
