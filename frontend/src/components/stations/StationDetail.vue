<script setup lang="ts">
/**
 * Zweite Ebene im Sheet: eine Säule mit Registerdaten, Route und allen Katalogtarifen der gewählten
 * Ladeart, günstigste zuerst. Ohne Tarif (oder bei unbekanntem Betreiber) entfällt der Preisabschnitt,
 * die Säule bleibt mit Registerdaten.
 */
import { computed, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ChevronLeftIcon, MapPinIcon } from '@heroicons/vue/24/outline'
import { chargingStationService, type MapStation, type StationTariff } from '../../api/chargingStationService'
import { analytics } from '../../services/analytics'
import { formatDistance, latestVerified, routeUrl, type ChargeType } from '../../utils/stationMap'
import { formatDay, stationMeta } from './stationText'
import StationPriceRow from './StationPriceRow.vue'

const props = defineProps<{ station: MapStation; type: ChargeType; distanceMeters: number; apple: boolean }>()
const emit = defineEmits<{ back: [] }>()
const { t, locale } = useI18n()

const tariffs = ref<StationTariff[] | null>(null)
const pricesState = ref<'idle' | 'loading' | 'error'>('idle')
let controller: AbortController | null = null

async function loadPrices() {
  controller?.abort()
  tariffs.value = null
  if (!props.station.known) { pricesState.value = 'idle'; return }
  const mine = new AbortController()
  controller = mine
  pricesState.value = 'loading'
  const kw = props.type === 'DC' ? props.station.maxDcKw : props.station.maxAcKw
  try {
    const list = await chargingStationService.tariffs(props.station.name, props.type, kw, mine.signal)
    if (mine.signal.aborted) return
    tariffs.value = list && list.length ? list : null
    pricesState.value = 'idle'
  } catch {
    if (!mine.signal.aborted) pricesState.value = 'error'
  }
}
watch(() => [props.station.id, props.type], loadPrices, { immediate: true })
onUnmounted(() => controller?.abort())

const meta = computed(() => stationMeta(props.station, props.type, t).join(' · '))
const where = computed(() => [formatDistance(props.distanceMeters, locale.value),
  props.station.openingHours ? t('stations.opening_hours', { v: props.station.openingHours }) : null].filter(Boolean).join(' · '))
const route = computed(() => routeUrl(props.station.latitude, props.station.longitude, props.apple))
const verified = computed(() => {
  const iso = tariffs.value ? latestVerified(tariffs.value) : null
  return iso ? formatDay(iso, locale.value) : null
})

const onRoute = () => analytics.track('station_route_clicked', { known: props.station.known })
</script>

<template>
  <div data-testid="station-detail" class="pb-6">
    <div class="flex items-center gap-1 px-2">
      <button type="button" data-testid="station-back"
        class="grid h-11 w-11 shrink-0 place-items-center rounded-full text-gray-900 hover:bg-black/10 focus-visible:outline focus-visible:outline-2 focus-visible:outline-indigo-600 dark:text-gray-100 dark:hover:bg-white/10"
        :aria-label="t('stations.back')" @click="emit('back')">
        <ChevronLeftIcon class="h-6 w-6" aria-hidden="true" />
      </button>
      <h2 class="min-w-0 flex-1 truncate text-xl font-bold text-gray-900 dark:text-gray-100">{{ station.name }}</h2>
    </div>

    <div class="flex flex-col gap-3 px-4 pt-1">
      <div class="text-[13px] text-gray-600 tabular-nums dark:text-gray-400">
        <div v-if="station.address" class="text-[15px] text-gray-900 dark:text-gray-100">{{ station.address }}</div>
        <div class="mt-0.5">{{ where }}</div>
        <div v-if="meta">{{ meta }}</div>
        <div v-if="station.payment">{{ t('stations.payment', { v: station.payment }) }}</div>
      </div>

      <a :href="route" target="_blank" rel="noopener" data-testid="station-route"
        class="btn-3d flex min-h-12 items-center justify-center gap-2 rounded-xl border-2 border-edge bg-green-600 text-base font-bold text-white active:bg-green-700 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-gray-900 dark:focus-visible:outline-gray-100"
        @click="onRoute">
        <MapPinIcon class="h-5 w-5" aria-hidden="true" />
        {{ t('stations.route') }}
      </a>
    </div>

    <section v-if="tariffs || pricesState !== 'idle'" class="mt-4" :aria-label="t('stations.costs_title')">
      <div class="flex justify-between px-4 pb-1 text-[12px] font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400">
        <span>{{ t('stations.costs_title') }}</span>
        <span>{{ t('stations.per_kwh') }}</span>
      </div>
      <div v-if="pricesState === 'loading'" class="space-y-2 px-4 py-3" aria-hidden="true">
        <div class="h-4 w-2/3 rounded bg-gray-200 dark:bg-gray-700" />
        <div class="h-4 w-1/2 rounded bg-gray-200 dark:bg-gray-700" />
      </div>
      <div v-else-if="pricesState === 'error'" class="flex items-center justify-between gap-3 px-4 py-3 text-[14px] text-gray-700 dark:text-gray-300">
        <span>{{ t('stations.prices_error') }}</span>
        <button type="button" data-testid="station-prices-retry"
          class="btn-3d min-h-11 shrink-0 rounded-full border-2 border-edge bg-white px-4 font-semibold text-gray-900 dark:bg-gray-900 dark:text-gray-100"
          @click="loadPrices">{{ t('stations.retry') }}</button>
      </div>
      <template v-else-if="tariffs">
        <ul class="border-t border-gray-200 dark:border-white/10">
          <StationPriceRow v-for="tr in tariffs" :key="`${tr.name}-${tr.variant ?? ''}`" :tariff="tr" />
        </ul>
        <p class="px-4 pt-3 text-[13px] text-gray-600 dark:text-gray-400">
          {{ verified ? t('stations.footer', { date: verified }) : t('stations.footer_undated') }}
        </p>
      </template>
    </section>
  </div>
</template>
