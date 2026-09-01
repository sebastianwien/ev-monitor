<script setup lang="ts">
import { computed, defineAsyncComponent } from 'vue'
import { useI18n } from 'vue-i18n'
import { BoltIcon, MapPinIcon, PencilSquareIcon, ExclamationTriangleIcon } from '@heroicons/vue/24/outline'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import { normalizeCharge, relativeTimeParts, tripTimestamp, tripSpeedKeyAndArgs } from '../../utils/recentActivity'
import { tripConsumption } from '../../utils/tripCalculations'
import TripClimateMarkers from '../TripClimateMarkers.vue'
// Async: Leaflet stays out of the dashboard's initial chunk (same reasoning as the
// heatmap) - the map only loads when a trip actually carries a location.
const ActivityLocationMap = defineAsyncComponent(() => import('./ActivityLocationMap.vue'))

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
const { formatConsumption, consumptionUnitLabel, formatDistance, formatCurrency, formatCostPerKwh, formatDecimal } =
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
 * Automatisch erfasste Einzel-Ladung (Tesla/Smartcar/Wallbox) ohne Preis - der amber Chip
 * bietet den schnellen Nachtrag an. Ladegruppen und manuelle Logs sind ausgenommen (kein
 * Einzel-Log-Patch bzw. haben ohnehin einen Preis).
 */
const chargePriceless = computed(() =>
  !!ch.value && !props.charge?._isLadegruppe && chargeSource.value != null
  && ch.value.costEur == null && !!props.charge?.id)
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
  if (ch.value?.costPerKwh != null) out.push(formatCostPerKwh(ch.value.costPerKwh))
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

// -- Mobile: kompakte Inline-Metriken (ein dichter Fließtext statt Balken) --
const chargeSocText = computed(() =>
  chargeSoc.value ? `${Math.round(chargeSoc.value.before)}→${Math.round(chargeSoc.value.after)}%` : null,
)
const tripSocText = computed(() =>
  tripSoc.value ? `${Math.round(tripSoc.value.start)}→${Math.round(tripSoc.value.end)}%` : null,
)
// Mobile zeigt in der schmalen Kachel nur das Wesentliche (Zeit steht separat davor);
// Leistung/Kosten/Temp/Route bleiben Desktop + Detailseite vorbehalten.
const chargeInline = computed<string[]>(() => {
  const out: string[] = []
  if (ch.value?.costEur != null) out.push(formatCurrency(ch.value.costEur))
  if (chargeSocText.value) out.push(chargeSocText.value)
  return out
})
const tripInline = computed<string[]>(() => {
  const out: string[] = []
  if (tripConsumptionResult.value)
    out.push(`${tripConsumptionResult.value.estimated ? '~' : ''}${formatConsumption(tripConsumptionResult.value.kwhPer100km)}`)
  if (tripSocText.value) out.push(tripSocText.value)
  return out
})
</script>

<template>
  <div v-if="ch" class="grid grid-cols-2 gap-2 mb-2.5 md:gap-2.5 md:mb-3">
    <!-- Letzter Ladevorgang -->
    <button
      type="button"
      data-testid="recent-charge-tile"
      :aria-label="t('dashboard.recent_charge_edit')"
      @click="emit('edit-charge')"
      class="group relative isolate block w-full cursor-pointer text-left bg-white dark:bg-gray-800 border-2 border-gray-300 dark:border-gray-600 rounded-sm shadow-[2px_2px_0_0_#d1d5db] dark:shadow-[2px_2px_0_0_#374151] hover:shadow-[3px_3px_0_0_#9ca3af] dark:hover:shadow-[3px_3px_0_0_#4b5563] transition-shadow px-3 py-2 md:px-3.5 md:py-2.5"
      :class="{ 'col-span-2': !showTrip, 'map-legible': hasChargeLocation }"
    >
      <!-- Ladeort als Hintergrund - ein Punkt, keine Strecke. -->
      <ActivityLocationMap
        v-if="hasChargeLocation"
        :start-geohash="ch.geohash"
        :end-geohash="null"
        class="-z-10"
      />
      <div class="flex items-center justify-between mb-1 md:mb-1.5">
        <div class="flex items-center gap-1.5 text-xs font-semibold text-gray-500 dark:text-gray-400">
          <BoltIcon class="w-4 h-4 text-amber-500 dark:text-amber-400" aria-hidden="true" />
          {{ t('dashboard.recent_charge_title') }}
          <span
            v-if="chargePriceless"
            role="button"
            tabindex="0"
            data-testid="charge-price-chip"
            :aria-label="t('priceamend.chip_aria')"
            @click.stop="emit('amend-charge')"
            @keydown.enter.stop.prevent="emit('amend-charge')"
            @keydown.space.stop.prevent="emit('amend-charge')"
            class="inline-flex items-center gap-1 px-1.5 py-0.5 rounded-sm text-[10px] font-medium bg-amber-100 text-amber-700 dark:bg-amber-900/40 dark:text-amber-300 hover:bg-amber-200 dark:hover:bg-amber-900/60 cursor-pointer transition-colors">
            <ExclamationTriangleIcon class="w-3 h-3" aria-hidden="true" />
            {{ t('priceamend.chip') }}
          </span>
        </div>
        <!-- Relativzeit: Desktop immer im Header | Mobile nur bei voller Breite (dann ist Platz) -->
        <div class="items-center gap-0.5 text-xs text-gray-400 dark:text-gray-400" :class="showTrip ? 'hidden md:flex' : 'flex'">
          <span>{{ relativeTime(ch.loggedAt) }}</span>
          <PencilSquareIcon class="w-3.5 h-3.5 opacity-0 group-hover:opacity-60 transition-opacity" aria-hidden="true" />
        </div>
      </div>

      <div class="flex items-baseline gap-2" :class="showTrip ? 'justify-between' : 'justify-center md:justify-between'">
        <div class="flex items-baseline gap-x-2.5 gap-y-0 flex-wrap min-w-0">
          <div class="flex items-baseline gap-1">
            <span class="text-lg md:text-xl font-bold text-gray-900 dark:text-gray-100 tabular-nums leading-none">{{ ch.kwh != null ? formatDecimal(ch.kwh, 1) : '–' }}</span>
            <span class="text-xs text-gray-400 dark:text-gray-400 font-medium">kWh</span>
            <!-- Nur wenn ein Brutto-Wert danebensteht, muss die grosse Zahl sich abgrenzen -->
            <span v-if="chargeGross" class="hidden md:inline text-[10px] text-gray-400 dark:text-gray-400 font-medium">{{ t('dashboard.ac_gross_label_netto') }}</span>
          </div>
          <!-- Volle Breite (keine Fahrt): Ladedaten inline neben kWh, Zeit steht im Header -->
          <div v-if="!showTrip" class="md:hidden flex items-baseline gap-x-2.5 text-[11px] text-gray-500 dark:text-gray-400">
            <span v-for="m in chargeInline" :key="'wci' + m" class="tabular-nums">{{ m }}</span>
            <span v-if="chargeSource" :class="['inline-flex items-center gap-1 px-1 py-0.5 rounded-sm text-[10px] font-medium', chargeSource.classes]">
              <component :is="chargeSource.icon" class="w-3 h-3" aria-hidden="true" />
              {{ chargeSource.label }}
            </span>
          </div>
        </div>
        <span v-if="ch.costEur != null" class="hidden md:inline-block text-sm md:text-base font-semibold text-gray-800 dark:text-gray-200 tabular-nums">{{ formatCurrency(ch.costEur) }}</span>
        <span v-if="showTrip" class="md:hidden text-xs text-gray-400 dark:text-gray-400 whitespace-nowrap">{{ relativeTime(ch.loggedAt) }}</span>
      </div>

      <!-- Desktop: SoC-Balken (Labels flankieren das Segment) + Chips -->
      <div class="hidden md:block">
        <div v-if="chargeSoc" class="mt-2">
          <div class="relative h-3.5 text-[11px] tabular-nums">
            <span class="absolute pr-1 text-gray-400 dark:text-gray-400" :style="{ right: (100 - Math.min(Math.max(chargeSoc.before, 6), 100)) + '%' }">{{ Math.round(chargeSoc.before) }}%</span>
            <span class="absolute pl-1 font-semibold text-gray-700 dark:text-gray-200" :style="{ left: Math.min(chargeSoc.after, 94) + '%' }">{{ Math.round(chargeSoc.after) }}%</span>
          </div>
          <div class="relative h-1.5 rounded-full bg-gray-200 dark:bg-gray-700 overflow-hidden" role="presentation">
            <div class="absolute inset-y-0 left-0 bg-gray-300 dark:bg-gray-600" :style="{ width: chargeSoc.before + '%' }"></div>
            <div class="absolute inset-y-0 bg-emerald-500 dark:bg-emerald-400" :style="{ left: chargeSoc.before + '%', width: (chargeSoc.after - chargeSoc.before) + '%' }"></div>
          </div>
        </div>
        <div class="mt-2 flex flex-wrap items-center gap-x-2 gap-y-1 text-[11px] text-gray-600 dark:text-gray-300">
          <template v-for="(m, i) in chargeMetrics" :key="'cm' + i">
            <span v-if="i > 0" class="text-gray-300 dark:text-gray-600" aria-hidden="true">&middot;</span>
            <span class="tabular-nums">{{ m }}</span>
          </template>
          <span v-if="chargeTypeLabel" class="inline-flex items-center px-1.5 py-0.5 rounded-sm text-[10px] font-semibold bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-300">{{ chargeTypeLabel }}</span>
          <span v-if="chargeSource" :class="['inline-flex items-center gap-1 px-1.5 py-0.5 rounded-sm text-[10px] font-medium', chargeSource.classes]">
            <component :is="chargeSource.icon" class="w-3 h-3" aria-hidden="true" />
            {{ chargeSource.label }}
          </span>
        </div>
      </div>

      <!-- Mobile (schmal, neben Fahrt): gedämpfte Meta-Zeile unter der kWh-Zeile -->
      <div v-if="showTrip" class="md:hidden mt-0.5 flex flex-wrap items-center gap-x-2.5 gap-y-0.5 text-[11px] text-gray-500 dark:text-gray-400">
        <span v-for="m in chargeInline" :key="'ci' + m" class="tabular-nums">{{ m }}</span>
        <span v-if="chargeSource" :class="['inline-flex items-center gap-1 px-1 py-0.5 rounded-sm text-[10px] font-medium', chargeSource.classes]">
          <component :is="chargeSource.icon" class="w-3 h-3" aria-hidden="true" />
          {{ chargeSource.label }}
        </span>
      </div>
    </button>

    <!-- Letzte Fahrt (nur wenn Trip vorhanden - Premium/AutoSync) -->
    <button
      v-if="showTrip"
      type="button"
      data-testid="recent-trip-tile"
      :class="{ 'map-legible': hasTripLocation }"
      :aria-label="t('dashboard.recent_trip_edit')"
      @click="emit('edit-trip')"
      class="group relative isolate block w-full cursor-pointer text-left bg-white dark:bg-gray-800 border-2 border-gray-300 dark:border-gray-600 rounded-sm shadow-[2px_2px_0_0_#d1d5db] dark:shadow-[2px_2px_0_0_#374151] hover:shadow-[3px_3px_0_0_#9ca3af] dark:hover:shadow-[3px_3px_0_0_#4b5563] transition-shadow px-3 py-2 md:px-3.5 md:py-2.5"
    >
      <!-- Grobe Start-/Zielgegend als Hintergrund. Nur die neueste Fahrt liefert
           Geohashes, alle anderen Kacheln bleiben ohne Karte. -->
      <ActivityLocationMap
        v-if="hasTripLocation"
        :start-geohash="trip.locationStartGeohash"
        :end-geohash="trip.locationEndGeohash"
        :route-polyline="trip.routePolyline"
        :route-kind="trip.routeKind"
        :trace-polyline="trip.tracePolyline"
        class="-z-10"
      />
      <div class="flex items-center justify-between mb-1 md:mb-1.5">
        <div class="flex items-center gap-1.5 text-xs font-semibold text-gray-500 dark:text-gray-400">
          <MapPinIcon class="w-4 h-4 text-indigo-500 dark:text-indigo-400" aria-hidden="true" />
          {{ t('dashboard.recent_trip_title') }}
        </div>
        <!-- Relativzeit: Desktop im Header rechts | Mobile in der Meta-Zeile (Platz) -->
        <div class="hidden md:flex items-center gap-0.5 text-xs text-gray-400 dark:text-gray-400">
          <span>{{ relativeTime(tripTimestamp(trip)) }}</span>
          <PencilSquareIcon class="w-3.5 h-3.5 opacity-0 group-hover:opacity-60 transition-opacity" aria-hidden="true" />
        </div>
      </div>

      <div class="flex items-baseline justify-between gap-2">
        <span class="text-lg md:text-xl font-bold text-gray-900 dark:text-gray-100 tabular-nums leading-none whitespace-nowrap">{{ trip.distanceKm != null ? formatDistance(trip.distanceKm) : '–' }}</span>
        <span v-if="tripConsumptionResult" class="hidden md:flex items-baseline gap-1">
          <span class="text-sm md:text-base font-semibold text-gray-800 dark:text-gray-200 tabular-nums">{{ tripConsumptionResult.estimated ? '~' : '' }}{{ formatConsumption(tripConsumptionResult.kwhPer100km, { showUnit: false }) }}</span>
          <span class="text-xs text-gray-400 dark:text-gray-400 font-medium">{{ consumptionUnitLabel() }}</span>
        </span>
        <span class="md:hidden text-xs text-gray-400 dark:text-gray-400 whitespace-nowrap">{{ relativeTime(tripTimestamp(trip)) }}</span>
      </div>

      <!-- Desktop: SoC-Balken (Labels flankieren das Segment) + Chips -->
      <div class="hidden md:block">
        <div v-if="tripSoc" class="mt-2">
          <div class="relative h-3.5 text-[11px] tabular-nums">
            <span class="absolute pr-1 text-gray-400 dark:text-gray-400" :style="{ right: (100 - Math.min(Math.max(tripSoc.end, 6), 100)) + '%' }">{{ Math.round(tripSoc.end) }}%</span>
            <span class="absolute pl-1 font-semibold text-gray-700 dark:text-gray-200" :style="{ left: Math.min(tripSoc.start, 94) + '%' }">{{ Math.round(tripSoc.start) }}%</span>
          </div>
          <div class="relative h-1.5 rounded-full bg-gray-200 dark:bg-gray-700 overflow-hidden" role="presentation">
            <div class="absolute inset-y-0 left-0 bg-gray-300 dark:bg-gray-600" :style="{ width: tripSoc.end + '%' }"></div>
            <div class="absolute inset-y-0 bg-indigo-500 dark:bg-indigo-400" :style="{ left: tripSoc.end + '%', width: (tripSoc.start - tripSoc.end) + '%' }"></div>
          </div>
        </div>
        <!-- Verbraucher stehen in derselben Zeile wie Tempo und Temperatur, nicht darunter:
             eine eigene Zeile laesst die Kachel wachsen, sobald eine Fahrt Klimadaten hat,
             und verschiebt damit die Reihe neben dem Ladevorgang. min-h haelt die Hoehe
             auch dann, wenn eine Fahrt gar keine Metriken liefert. -->
        <div class="mt-2 min-h-[1.125rem] flex flex-wrap items-center gap-x-2 gap-y-1 text-[11px] text-gray-600 dark:text-gray-300">
          <template v-for="(m, i) in tripMetrics" :key="'tm' + i">
            <span v-if="i > 0" class="text-gray-300 dark:text-gray-600" aria-hidden="true">&middot;</span>
            <span class="tabular-nums">{{ m }}</span>
          </template>
          <span v-if="tripRouteLabel" class="inline-flex items-center px-1.5 py-0.5 rounded-sm text-[10px] font-semibold bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-300">{{ tripRouteLabel }}</span>
          <span v-if="trip.climate && tripMetrics.length" class="text-gray-300 dark:text-gray-600" aria-hidden="true">&middot;</span>
          <TripClimateMarkers v-if="trip.climate" :climate="trip.climate" class="!justify-start !text-[11px]" />
        </div>
      </div>

      <!-- Mobile: gedämpfte Meta-Zeile statt Balken - nur Abstand trennt (sauberer Umbruch) -->
      <div class="md:hidden mt-0.5 flex flex-wrap items-center gap-x-2.5 gap-y-0.5 text-[11px] text-gray-500 dark:text-gray-400">
        <span v-for="m in tripInline" :key="'ti' + m" class="tabular-nums">{{ m }}</span>
      </div>
    </button>
  </div>
</template>

<style>
/*
 * Text auf der Karte: statt eines Hintergrunds bekommt die Schrift eine weiche Kontur in
 * Hintergrundfarbe. Das trennt sie vom Kartenbild, ohne die Kachel in Balken zu zerlegen -
 * und die Karte bleibt luekenlos sichtbar. Ungescoped, weil die Regel an alle Kindknoten
 * vererbt werden muss (auch an die von Unterkomponenten gerenderten Texte).
 */
.map-legible,
.map-legible * {
  /* Vier Stufen: zwei harte Kerne zeichnen die Kontur, zwei weiche heben den
     Kartenuntergrund ab. Weniger Stufen ergaben auf feiner Strassenzeichnung Luecken. */
  text-shadow:
    0 0 1px rgb(255 255 255),
    0 0 3px rgb(255 255 255),
    0 0 7px rgb(255 255 255 / 0.98),
    0 0 16px rgb(255 255 255 / 0.9);
}
.dark .map-legible,
.dark .map-legible * {
  text-shadow:
    0 0 3px rgb(17 24 39 / 0.95),
    0 0 8px rgb(17 24 39 / 0.8);
}
/*
 * Auf der Karte gilt maximaler Kontrast statt der sonst abgestuften Grautoene: die
 * Abstufung, die auf ruhigem Kachelgrund Hierarchie schafft, wird auf Kartenuntergrund
 * einfach unlesbar. Icons bleiben ausgenommen, sie tragen ihre eigene Bedeutungsfarbe.
 */
.map-legible,
.map-legible *:not(svg):not(svg *) {
  color: #000 !important;
}
.dark .map-legible,
.dark .map-legible *:not(svg):not(svg *) {
  color: #fff !important;
}

/* Icons sind SVG, die trifft kein Textschatten - sie brauchen das Pendant als Filter. */
.map-legible svg {
  filter: drop-shadow(0 0 2px rgb(255 255 255)) drop-shadow(0 0 5px rgb(255 255 255 / 0.9));
}
.dark .map-legible svg {
  filter: drop-shadow(0 0 2px rgb(17 24 39 / 0.95));
}
</style>
