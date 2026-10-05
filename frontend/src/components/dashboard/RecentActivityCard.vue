<script setup lang="ts">
import { computed, defineAsyncComponent } from 'vue'
import { useI18n } from 'vue-i18n'
import { BoltIcon, MapPinIcon, ExclamationTriangleIcon } from '@heroicons/vue/24/outline'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import { normalizeCharge, relativeTimeParts, tripTimestamp, tripSpeedKeyAndArgs } from '../../utils/recentActivity'
import { tripConsumption } from '../../utils/tripCalculations'
import { tripLine, isRoutedLine } from '../../utils/tripMap'
import RecentActivityRow from './RecentActivityRow.vue'
import TripClimateMarkers from '../TripClimateMarkers.vue'
// Async: Leaflet stays out of the dashboard's initial chunk (same reasoning as the
// heatmap) - the map only loads when a trip actually carries a location.
const ActivityLocationMap = defineAsyncComponent(() => import('./ActivityLocationMap.vue'))
import WattBadge from '../shared/WattBadge.vue'
import { useCoinStore } from '../../stores/coins'
import { wattPossibleForLog } from '../../utils/wattPreview'
const coinStore = useCoinStore()
coinStore.ensureCatalog()

const props = defineProps<{
  /** Raw charge / Ladegruppe feed entry (mergedLogFeed), or null. */
  charge: any | null
  /** Raw trip feed entry (mergedLogFeed), or null. */
  trip: any | null
  /** SoH-adjusted capacity, needed for SoC-based trip consumption fallback. */
  effectiveBatteryCapacityKwh: number | null
  /** Source badge helper from the log list (icon + label + classes per dataSource). */
  sourceInfo: (ds?: string) => { label: string; icon: unknown; classes: string } | null
}>()

/**
 * Beide Kacheln bearbeiten den Eintrag direkt - frueher fuehrten sie nur in den
 * Log-Feed, wo man denselben Eintrag erst wiederfinden musste. Das Formular oeffnet
 * der Dashboard-Container, die Kachel bleibt praesentational.
 */
const emit = defineEmits<{ 'edit-charge': []; 'edit-trip': []; 'amend-charge': [] }>()

const { t, locale } = useI18n()
const { formatConsumption, formatDistance, formatCurrency, formatCostPerKwh, formatDecimal } =
  useLocaleFormat()

const intlLocale = computed(() => (locale.value === 'en' ? 'en-GB' : locale.value))

/** Localized "vor 3 Stunden" / "gestern" via native Intl. */
function relativeTime(iso: string | null | undefined): string {
  if (!iso) return ''
  const ms = new Date(iso).getTime()
  const parts = relativeTimeParts(ms, Date.now())
  if (!parts) return ''
  return new Intl.RelativeTimeFormat(intlLocale.value, { numeric: 'auto' }).format(parts.value, parts.unit)
}

// -- Charge --
const ch = computed(() => normalizeCharge(props.charge))
const chargeSource = computed(() => (ch.value ? props.sourceInfo(ch.value.dataSource ?? undefined) : null))
/**
 * Automatisch erfasste Ladung (Tesla/Smartcar/Wallbox) ohne vollstaendigen Preis - der amber
 * Chip bietet den schnellen Nachtrag an. Bei einer Ladegruppe zaehlt sie als preislos, sobald
 * EIN Teilvorgang keinen Preis hat; der Nachtrag startet dann bei diesem Teilvorgang.
 * Manuelle Logs sind ausgenommen - sie haben ohnehin einen Preis.
 */
const chargePriceless = computed(() => {
  if (!ch.value || chargeSource.value == null) return false
  if (props.charge?._isLadegruppe) return (props.charge._pricelessSubs?.length ?? 0) > 0
  return ch.value.costEur == null && !!props.charge?.id
})
/** Watt-Potenzial des Nachtrags: bei einer Ladegruppe die Summe ihrer preislosen Teilvorgaenge. */
const pricelessWatt = computed<number>(() => {
  const subs = props.charge?._pricelessSubs as any[] | undefined
  if (subs?.length) return subs.reduce((sum, sub) => sum + wattPossibleForLog(coinStore.catalog, sub), 0)
  return ch.value ? wattPossibleForLog(coinStore.catalog, ch.value) : 0
})
const chargeTypeLabel = computed(() => {
  const type = ch.value?.chargingType
  if (type === 'AC') return t('dashboard.charging_type_ac')
  if (type === 'DC') return t('dashboard.charging_type_dc')
  return null
})
/**
 * Brutto ab Netz, sobald es zusaetzlich zur Netto-Menge gemessen wurde. Die grosse
 * Zahl bleibt netto (wie im Log-Feed), der ct/kWh-Wert rechnet gegen brutto - der
 * Chip macht diese beiden Bezugsgroessen sichtbar statt sie zu vermischen.
 */
const chargeGross = computed(() => {
  const gross = ch.value?.kwhGross
  const net = ch.value?.kwh
  if (gross == null || net == null || gross <= net) return null
  return `${formatDecimal(gross, 1)} kWh ${t('dashboard.ac_gross_label_brutto')}`
})
/** Dot-separated metric texts; only present ones, so no orphan separators. */
const chargeMetrics = computed<string[]>(() => {
  const out: string[] = []
  if (chargeGross.value) out.push(chargeGross.value)
  // Nur zeigen, wenn der Preis die ganze Ladung/Ladegruppe abdeckt - eine Rate aus einem
  // Bruchteil der Energie waere ein falscher Vergleichswert.
  if (ch.value?.costPerKwh != null && ch.value.isFullyPriced) out.push(formatCostPerKwh(ch.value.costPerKwh))
  if (ch.value?.maxPowerKw != null) out.push(`${Math.round(ch.value.maxPowerKw)} kW`)
  return out
})
/** SoC gain segment [before → after] as clamped percentages for the bar. */
const chargeSoc = computed(() => {
  const before = ch.value?.socBefore
  const after = ch.value?.socAfter
  if (before == null || after == null || after <= before) return null
  return { before: clamp(before), after: clamp(after) }
})

// -- Trip --
const tripConsumptionResult = computed(() =>
  props.trip ? tripConsumption(props.trip, props.effectiveBatteryCapacityKwh) : null,
)
const tripRouteLabel = computed(() => {
  switch (props.trip?.routeType) {
    case 'CITY': return t('dashboard.trip_route_city')
    case 'HIGHWAY': return t('dashboard.trip_route_highway')
    case 'COMBINED': return t('dashboard.trip_route_combined')
    default: return null
  }
})
const tripSpeed = computed(() => {
  const speed = tripSpeedKeyAndArgs(props.trip?.avgSpeedKmh, props.trip?.maxSpeedKmh)
  return speed ? t(speed.key, speed.args) : null
})
/** Dot-separated metric texts; only present ones, so no orphan separators. */
const tripMetrics = computed<string[]>(() => {
  const out: string[] = []
  if (tripSpeed.value) out.push(tripSpeed.value)
  if (props.trip?.outsideTempCelsius != null) out.push(`${Math.round(props.trip.outsideTempCelsius)} °C`)
  return out
})
/** SoC consumption segment [end → start] for the bar (start > end while driving). */
const tripSoc = computed(() => {
  const start = props.trip?.socStart
  const end = props.trip?.socEnd
  if (start == null || end == null || start <= end) return null
  return { start: clamp(start), end: clamp(end) }
})

function clamp(v: number): number {
  return Math.max(0, Math.min(100, v))
}


const showTrip = computed(() => !!props.trip)
/** Nur rund die Haelfte der Ladevorgaenge traegt einen Ort - ohne bleibt die Kachel schlicht. */
const hasChargeLocation = computed(() => !!ch.value?.geohash)
/** Backend fills the location data for the most recent trip only - older ones stay blank. */
const hasTripLocation = computed(
  () =>
    !!props.trip?.locationStartGeohash ||
    !!props.trip?.locationEndGeohash ||
    !!props.trip?.tracePolyline,
)

const chargeMeta = computed<string[]>(() => [...(chargeTypeLabel.value ? [chargeTypeLabel.value] : []), ...chargeMetrics.value])
const tripMeta = computed<string[]>(() => [...(tripRouteLabel.value ? [tripRouteLabel.value] : []), ...tripMetrics.value])

/** Die Thumbnails tragen keine eigene Attribution - sie steht gesammelt unter der Liste. */
const mapCredit = computed<string | null>(() => {
  if (!hasChargeLocation.value && !hasTripLocation.value) return null
  const routed = hasTripLocation.value && isRoutedLine(tripLine(props.trip?.tracePolyline, props.trip?.routePolyline, props.trip?.routeKind))
  return routed ? '© OpenStreetMap · Route © openrouteservice by HeiGIT' : '© OpenStreetMap'
})
</script>

<template>
  <!-- Eine Zeile je Eintrag, Karte als randloses Vorschaubild neben dem Text statt Text
       auf der Karte. Ab md stehen die beiden Zeilen nebeneinander. -->
  <div v-if="ch" class="mb-2.5 md:mb-3">
    <div class="grid overflow-hidden divide-y md:divide-y-0 md:divide-x divide-gray-200 dark:divide-gray-700 bg-white dark:bg-gray-800 border-2 border-gray-300 dark:border-gray-600 rounded-sm shadow-[2px_2px_0_0_#d1d5db] dark:shadow-[2px_2px_0_0_#374151]" :class="{ 'md:grid-cols-2': showTrip }">
      <RecentActivityRow
        tone="charge"
        data-testid="recent-charge-tile"
        :label="t('dashboard.recent_charge_short')"
        :when="relativeTime(ch.loggedAt)"
        :soc="chargeSoc ? { from: chargeSoc.before, to: chargeSoc.after, label: `${Math.round(chargeSoc.before)} → ${Math.round(chargeSoc.after)} %` } : null"
        :aria-label="t('dashboard.recent_charge_edit')"
        @click="emit('edit-charge')"
      >
        <template #thumb>
          <ActivityLocationMap v-if="hasChargeLocation" variant="thumb" :start-geohash="ch.geohash" :end-geohash="null" />
          <BoltIcon v-else class="absolute inset-0 m-auto w-7 h-7 text-amber-500 dark:text-amber-400" aria-hidden="true" />
        </template>
        <template #value>
          <span class="text-xl font-bold leading-tight">{{ ch.kwh != null ? formatDecimal(ch.kwh, 1) : '–' }}</span>
          <span class="text-[13px] text-gray-500 dark:text-gray-400">kWh</span>
          <template v-if="ch.costEur != null && ch.isFullyPriced">
            <span class="text-gray-300 dark:text-gray-600" aria-hidden="true">&middot;</span>
            <span class="text-[15px] font-semibold">{{ formatCurrency(ch.costEur) }}</span>
          </template>
        </template>
        <template #meta>
          <span
            v-if="chargePriceless"
            role="button"
            tabindex="0"
            data-testid="charge-price-chip"
            :aria-label="t('priceamend.chip_aria')"
            @click.stop="emit('amend-charge')"
            @keydown.enter.stop.prevent="emit('amend-charge')"
            @keydown.space.stop.prevent="emit('amend-charge')"
            class="inline-flex items-center gap-1 px-2 py-1 rounded-sm text-[11px] font-medium bg-amber-100 text-amber-700 dark:bg-amber-900/40 dark:text-amber-300 cursor-pointer">
            <ExclamationTriangleIcon class="w-3.5 h-3.5" aria-hidden="true" />
            {{ t('priceamend.chip') }}
            <WattBadge :amount="pricelessWatt" up-to />
          </span>
          <span v-if="chargeSource" :class="['inline-flex items-center gap-1 px-1 py-0.5 rounded-sm text-[10px] font-medium', chargeSource.classes]">
            <component :is="chargeSource.icon" class="w-3 h-3" aria-hidden="true" />
            {{ chargeSource.label }}
          </span>
          <span v-for="m in chargeMeta" :key="'cmm' + m" class="tabular-nums whitespace-nowrap">{{ m }}</span>
        </template>
      </RecentActivityRow>

      <RecentActivityRow
        v-if="showTrip"
        tone="trip"
        data-testid="recent-trip-tile"
        :label="t('dashboard.recent_trip_short')"
        :when="relativeTime(tripTimestamp(trip))"
        :soc="tripSoc ? { from: tripSoc.end, to: tripSoc.start, label: `${Math.round(tripSoc.start)} → ${Math.round(tripSoc.end)} %` } : null"
        :aria-label="t('dashboard.recent_trip_edit')"
        @click="emit('edit-trip')"
      >
        <template #thumb>
          <ActivityLocationMap
            v-if="hasTripLocation"
            variant="thumb"
            :start-geohash="trip.locationStartGeohash"
            :end-geohash="trip.locationEndGeohash"
            :route-polyline="trip.routePolyline"
            :route-kind="trip.routeKind"
            :trace-polyline="trip.tracePolyline"
          />
          <MapPinIcon v-else class="absolute inset-0 m-auto w-7 h-7 text-indigo-500 dark:text-indigo-400" aria-hidden="true" />
        </template>
        <template #value>
          <span class="text-xl font-bold leading-tight whitespace-nowrap">{{ trip.distanceKm != null ? formatDistance(trip.distanceKm) : '–' }}</span>
          <template v-if="tripConsumptionResult">
            <span class="text-gray-300 dark:text-gray-600" aria-hidden="true">&middot;</span>
            <span class="text-[15px] font-semibold whitespace-nowrap">{{ tripConsumptionResult.estimated ? '~' : '' }}{{ formatConsumption(tripConsumptionResult.kwhPer100km) }}</span>
          </template>
        </template>
        <template #meta>
          <span v-for="m in tripMeta" :key="'tmm' + m" class="tabular-nums whitespace-nowrap">{{ m }}</span>
          <TripClimateMarkers v-if="trip.climate" :climate="trip.climate" class="!justify-start !text-xs" />
        </template>
      </RecentActivityRow>
    </div>
    <p v-if="mapCredit" class="mt-1 text-right text-[10px] text-gray-400 dark:text-gray-500">{{ mapCredit }}</p>
  </div>

</template>

