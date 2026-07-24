<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { BoltIcon, MapPinIcon, PencilSquareIcon } from '@heroicons/vue/24/outline'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import { normalizeCharge, relativeTimeParts } from '../../utils/recentActivity'
import { tripConsumption } from '../../utils/tripCalculations'
import TripClimateMarkers from '../TripClimateMarkers.vue'

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
const emit = defineEmits<{ 'edit-charge': []; 'edit-trip': [] }>()

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
const chargeTypeLabel = computed(() => {
  const type = ch.value?.chargingType
  if (type === 'AC') return t('dashboard.charging_type_ac')
  if (type === 'DC') return t('dashboard.charging_type_dc')
  return null
})
/** Dot-separated metric texts; only present ones, so no orphan separators. */
const chargeMetrics = computed<string[]>(() => {
  const out: string[] = []
  if (ch.value?.costKwh != null) out.push(formatCostPerKwh(ch.value.costKwh))
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
  const avg = props.trip?.avgSpeedKmh
  const max = props.trip?.maxSpeedKmh
  if (avg == null && max == null) return null
  return t('dashboard.trip_speed_summary', { avg: Math.round(avg ?? 0), max: Math.round(max ?? 0) })
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
      class="group block w-full text-left bg-white dark:bg-gray-800 border-2 border-gray-300 dark:border-gray-600 rounded-sm shadow-[2px_2px_0_0_#d1d5db] dark:shadow-[2px_2px_0_0_#374151] hover:shadow-[3px_3px_0_0_#9ca3af] dark:hover:shadow-[3px_3px_0_0_#4b5563] transition-shadow px-3 py-2 md:px-3.5 md:py-2.5"
      :class="{ 'col-span-2': !showTrip }"
    >
      <div class="flex items-center justify-between mb-1 md:mb-1.5">
        <div class="flex items-center gap-1.5 text-xs font-semibold text-gray-500 dark:text-gray-400">
          <BoltIcon class="w-4 h-4 text-amber-500 dark:text-amber-400" aria-hidden="true" />
          {{ t('dashboard.recent_charge_title') }}
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
      :aria-label="t('dashboard.recent_trip_edit')"
      @click="emit('edit-trip')"
      class="group block w-full text-left bg-white dark:bg-gray-800 border-2 border-gray-300 dark:border-gray-600 rounded-sm shadow-[2px_2px_0_0_#d1d5db] dark:shadow-[2px_2px_0_0_#374151] hover:shadow-[3px_3px_0_0_#9ca3af] dark:hover:shadow-[3px_3px_0_0_#4b5563] transition-shadow px-3 py-2 md:px-3.5 md:py-2.5"
    >
      <div class="flex items-center justify-between mb-1 md:mb-1.5">
        <div class="flex items-center gap-1.5 text-xs font-semibold text-gray-500 dark:text-gray-400">
          <MapPinIcon class="w-4 h-4 text-indigo-500 dark:text-indigo-400" aria-hidden="true" />
          {{ t('dashboard.recent_trip_title') }}
        </div>
        <!-- Relativzeit: Desktop im Header rechts | Mobile in der Meta-Zeile (Platz) -->
        <div class="hidden md:flex items-center gap-0.5 text-xs text-gray-400 dark:text-gray-400">
          <span>{{ relativeTime(trip.tripStartedAt) }}</span>
          <PencilSquareIcon class="w-3.5 h-3.5 opacity-0 group-hover:opacity-60 transition-opacity" aria-hidden="true" />
        </div>
      </div>

      <div class="flex items-baseline justify-between gap-2">
        <span class="text-lg md:text-xl font-bold text-gray-900 dark:text-gray-100 tabular-nums leading-none whitespace-nowrap">{{ trip.distanceKm != null ? formatDistance(trip.distanceKm) : '–' }}</span>
        <span v-if="tripConsumptionResult" class="hidden md:flex items-baseline gap-1">
          <span class="text-sm md:text-base font-semibold text-gray-800 dark:text-gray-200 tabular-nums">{{ tripConsumptionResult.estimated ? '~' : '' }}{{ formatConsumption(tripConsumptionResult.kwhPer100km, { showUnit: false }) }}</span>
          <span class="text-xs text-gray-400 dark:text-gray-400 font-medium">{{ consumptionUnitLabel() }}</span>
        </span>
        <span class="md:hidden text-xs text-gray-400 dark:text-gray-400 whitespace-nowrap">{{ relativeTime(trip.tripStartedAt) }}</span>
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
        <div class="mt-2 flex flex-wrap items-center gap-x-2 gap-y-1 text-[11px] text-gray-600 dark:text-gray-300">
          <template v-for="(m, i) in tripMetrics" :key="'tm' + i">
            <span v-if="i > 0" class="text-gray-300 dark:text-gray-600" aria-hidden="true">&middot;</span>
            <span class="tabular-nums">{{ m }}</span>
          </template>
          <span v-if="tripRouteLabel" class="inline-flex items-center px-1.5 py-0.5 rounded-sm text-[10px] font-semibold bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-300">{{ tripRouteLabel }}</span>
        </div>
        <TripClimateMarkers v-if="trip.climate" :climate="trip.climate" class="mt-2 !justify-start" />
      </div>

      <!-- Mobile: gedämpfte Meta-Zeile statt Balken - nur Abstand trennt (sauberer Umbruch) -->
      <div class="md:hidden mt-0.5 flex flex-wrap items-center gap-x-2.5 gap-y-0.5 text-[11px] text-gray-500 dark:text-gray-400">
        <span v-for="m in tripInline" :key="'ti' + m" class="tabular-nums">{{ m }}</span>
      </div>
    </button>
  </div>
</template>
