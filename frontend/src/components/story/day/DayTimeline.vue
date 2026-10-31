<script setup lang="ts">
import { computed, defineAsyncComponent, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { BoltIcon, MapIcon, ChevronRightIcon, LinkIcon } from '@heroicons/vue/24/outline'
import { useLocaleFormat } from '../../../composables/useLocaleFormat'
import {
  chargeKwh, chargePlace, missingOf, canMerge, showStanding, gapMinutes, bundleTotals, type DayEvent,
} from '../../../composables/storyDay'
import { hasTripThumb, tripsCredit } from '../../../utils/tripMap'
import StandingText from './StandingText.vue'
import { chargePricePerKwh } from '../../../composables/useChargingEfficiency'
import { temperatureTone } from '../../../utils/tripClimate'
import TripClimateMarkers from '../../TripClimateMarkers.vue'

const ActivityLocationMap = defineAsyncComponent(() => import('../../dashboard/ActivityLocationMap.vue'))

/**
 * Ablauf eines Tages: chronologische Liste mit senkrechter Leiste durch die Symbole. Zeilen mindestens 56 px,
 * hier wird auf dem Handy getippt. Erste Zeile "17:24 · 20 min | 9 km" (bzw. Ort), Unterzeile Akku und Temperatur; Symbol, Text und
 * Minikarte auf einer Achse. Halt- und Standzeile gleich hoch (44 px), damit die Abstände zwischen Fahrten gleich sind.
 * Zwischen zwei Fahrten mit kurzem Halt: Zusammenführen, zweistufig bestätigt.
 *
 * Das Logbuch auf /logs gibt mehr mit (die Story nicht): die Standzeit vor jedem Eintrag samt Standverlust,
 * auch über Nacht vor dem ersten, die Reihenfolge neueste zuerst und je Fahrt eine Zeile mit Kosten (Slot `trip-extra`).
 * Ein Bündel (Lade-Ereignis mit `parts`) steht als eine Zeile: "09:12 | Zu Hause", darunter "6 Teilladungen · 50 → 73 %",
 * rechts die Summen. Die Zahl steht unten, weil oben auf dem Handy sonst der Ort abgeschnitten wird.
 */
export interface StandingRow { minutes: number; kwh: number | null; eur?: number | null; unlockTo?: string | null }
const props = defineProps<{
  events: DayEvent[]
  selectedId: string | null
  merging: boolean
  standing?: Record<string, StandingRow>
  newestFirst?: boolean
  /** Logbuch: der Kartenhinweis steht einmal am Ende der Seite statt unter jedem Tag. */
  noCredit?: boolean
}>()
const emit = defineEmits<{ select: [event: DayEvent]; merge: [earlier: DayEvent, later: DayEvent]; hover: [id: string | null] }>()
const { t } = useI18n()
const { formatDecimal, formatDistance, formatCurrency, formatCostPerKwh } = useLocaleFormat()

const hm = (min: number) => min >= 1440 ? '24:00' : `${String(Math.floor(min / 60)).padStart(2, '0')}:${String(min % 60).padStart(2, '0')}`
function dur(min: number) {
  const h = Math.floor(min / 60), m = min % 60
  return h && m ? t('storyday.dur_hm', { h, m }) : h ? t('storyday.dur_h', { h }) : t('storyday.dur_m', { m })
}
function placeName(e: DayEvent & { kind: 'charge' }) {
  const p = chargePlace(e.log)
  return p.kind === 'operator' ? p.name : t(`storyday.place_${p.kind}`)
}
/** Summen je Bündel, einmal je Liste */
const totals = computed(() => new Map(props.events.flatMap(e => e.kind === 'charge' && e.parts ? [[e.id, bundleTotals(e.parts)] as const] : [])))
/** Außentemperatur: Fahrt aus der Telemetrie, Ladung aus dem Ladevorgang */
const tempOf = (e: DayEvent): number | null =>
  (e.kind === 'trip' ? e.trip.outsideTempCelsius : e.log.temperatureCelsius) ?? null
const partsOf = (e: DayEvent) => e.kind === 'charge' ? e.parts?.length ?? 0 : 0
const costOf = (e: DayEvent & { kind: 'charge' }) => totals.value.get(e.id)?.costEur ?? (e.parts ? null : e.log.costEur)
/** Rechts unter dem Preis: Ladeart und Energie */
function chargeSide(e: DayEvent & { kind: 'charge' }) {
  const b = totals.value.get(e.id)
  const k = b ? b.kwh : chargeKwh(e.log)
  const raw = b ? b.chargingType : e.log.chargingType
  const type = raw && raw !== 'UNKNOWN' ? raw : null
  return [type, k != null ? `${formatDecimal(k, 1)} kWh` : null].filter(Boolean).join(' · ')
}
/** Preis je kWh einer Ladung, auf der Abrechnungsbasis wie in der alten Liste; beim Bündel nur, wenn alle Teile einen Preis haben */
function pricePerKwh(e: DayEvent & { kind: 'charge' }): string | null {
  const b = totals.value.get(e.id)
  const p = b ? b.pricePerKwh : chargePricePerKwh(e.log)
  return p != null ? formatCostPerKwh(p) : null
}
/** Was fehlt, als Chips; beim Bündel nur der Preis, mit der Zahl der Teile */
function missingChips(e: DayEvent & { kind: 'charge' }): string[] {
  const b = totals.value.get(e.id)
  if (b) return b.priceless.length ? [t('storyday.missing_price_n', { n: b.priceless.length })] : []
  return missingOf(e.log).map(m => t(`storyday.missing_${m}`))
}
/** Fahrt unter 1 km (Umparken, Rangieren): kompakte Zeile ohne Minikarte */
const isShort = (e: DayEvent) => e.kind === 'trip' && e.trip.distanceKm != null && e.trip.distanceKm < 1
/** Die Minikarten tragen keine eigene Attribution - sie steht einmal unter der Liste. */
const credit = computed(() => props.noCredit ? null : tripsCredit(props.events.flatMap(e => e.kind === 'trip' && !isShort(e) ? [e.trip] : [])))

/** Zusammenführen: erst fragen, dann tun. Schlüssel = id der früheren Fahrt. */
const confirming = ref<string | null>(null)

/** Zeilen der Liste: je Eintrag davor Halt oder Standzeit. */
interface Item { key: string; kind: 'merge' | 'standing' | 'event'; e: DayEvent; prev: DayEvent | null; s: StandingRow | null }
function standingBefore(prev: DayEvent | null, e: DayEvent): StandingRow | null {
  if (props.standing) return props.standing[e.id] ?? null
  return prev && showStanding(prev, e) ? { minutes: gapMinutes(prev, e), kwh: null } : null
}
/**
 * Senkrechte Linie durch die Symbole, je Zeile gezeichnet: sie beginnt in der Mitte des ersten Symbols und
 * endet in der des letzten (Symbolmitte 26 px von oben: 8 px Abstand plus halbes Symbol). Eine Halt- oder
 * Standzeile am Rand endet auf halber Höhe.
 */
const LINE = 'relative before:pointer-events-none before:absolute before:left-[25px] before:w-0.5 before:bg-gray-200 dark:before:bg-gray-800'
/** Standzeit: gestrichelt, das Auto steht und lädt nicht */
const LINE_IDLE = 'relative before:pointer-events-none before:absolute before:left-[25px] before:w-0 before:border-l-2 before:border-dashed before:border-gray-300 dark:before:border-gray-700'
function lineClass(i: number, isEvent: boolean, idle = false): string {
  const n = items.value.length
  const line = idle ? LINE_IDLE : LINE
  if (n < 2) return 'relative'
  if (i === 0) return `${line} before:bottom-0 ${isEvent ? 'before:top-[26px]' : 'before:top-1/2'}`
  if (i === n - 1) return `${line} before:top-0 ${isEvent ? 'before:h-[26px]' : 'before:h-1/2'}`
  return `${line} before:inset-y-0`
}
const items = computed<Item[]>(() => {
  const groups = props.events.map((e, i) => {
    const prev = i ? props.events[i - 1] : null
    const base = { e, prev, s: null }
    const s = prev && canMerge(prev, e) ? null : standingBefore(prev, e)
    const gap: Item[] = prev && canMerge(prev, e) ? [{ ...base, key: `m-${e.id}`, kind: 'merge' }]
      : s ? [{ ...base, key: `s-${e.id}`, kind: 'standing', s }] : []
    const row: Item = { ...base, key: e.id, kind: 'event' }
    return props.newestFirst ? [row, ...gap] : [...gap, row]
  })
  return (props.newestFirst ? groups.reverse() : groups).flat()
})
</script>

<template>
  <div>
  <ul class="relative -mx-2" data-testid="day-timeline">
    <template v-for="({ key, kind, e, prev, s }, idx) in items" :key="key">
      <!-- Zwischen zwei Einträgen: Halt mit Zusammenführen oder längere Standzeit -->
      <li v-if="kind === 'merge'" class="pl-14 text-[12.5px] text-gray-500 dark:text-gray-400" :class="lineClass(idx, false)" data-testid="day-merge-row">
        <!-- Wie die Standzeile: Dauer, Punkt, dann was es damit auf sich hat -->
        <div v-if="confirming !== prev!.id" class="flex min-h-11 flex-wrap items-center gap-x-1.5">
          <span class="tabular-nums">{{ gapMinutes(prev!, e) <= 0 ? t('storyday.direct') : dur(gapMinutes(prev!, e)) }}</span>·
          <button type="button" data-testid="day-merge" class="inline-flex min-h-11 items-center gap-1 rounded-full px-1 font-semibold text-gray-900 underline decoration-gray-300 underline-offset-4 hover:decoration-gray-900 dark:text-gray-100 dark:decoration-gray-600"
            @click="confirming = prev!.id">
            <LinkIcon class="h-4 w-4" aria-hidden="true" />{{ t('storyday.merge') }}
          </button>
        </div>
        <div v-else class="my-1 mr-2 grid gap-2 rounded-xl border-2 border-edge p-3 dark:border-gray-700" role="group" :aria-label="t('storyday.merge')">
          <p class="text-[13.5px] text-gray-800 dark:text-gray-200">{{ t('storyday.merge_confirm') }}</p>
          <div class="flex gap-2">
            <button type="button" class="min-h-11 flex-1 rounded-xl px-3 text-[14px] font-semibold text-gray-600 dark:text-gray-300" @click="confirming = null">{{ t('common.cancel') }}</button>
            <button type="button" data-testid="day-merge-confirm" :disabled="merging"
              class="btn-3d cta-shadow-sm min-h-11 flex-1 rounded-xl border-2 border-gray-900 bg-gray-900 dark:border-edge px-3 text-[14px] font-semibold text-white disabled:opacity-40 dark:bg-gray-100 dark:text-gray-900"
              @click="emit('merge', prev!, e)">{{ t('storyday.merge_do') }}</button>
          </div>
        </div>
      </li>
      <li v-else-if="kind === 'standing'" class="flex min-h-11 flex-wrap items-center gap-x-1.5 pl-14 text-[12px] tabular-nums text-gray-500 dark:text-gray-400" :class="lineClass(idx, false, true)" data-testid="day-standing">
        <StandingText v-bind="s!" />
      </li>

      <li v-else :class="lineClass(idx, true)">
        <button type="button" :data-testid="partsOf(e) ? 'day-row-bundle' : `day-row-${e.kind}`" :aria-current="e.id === selectedId"
          class="relative flex w-full items-start gap-3 rounded-2xl px-2 text-left transition-colors hover:bg-gray-50 focus-visible:outline-2 focus-visible:outline-green-600 dark:hover:bg-white/5"
          :class="[e.id === selectedId ? 'bg-green-50 dark:bg-green-400/10' : '', isShort(e) ? 'min-h-11 py-1' : 'min-h-14 py-2']" :data-compact="isShort(e) || undefined"
          @click="emit('select', e)" @mouseenter="emit('hover', e.id)" @mouseleave="emit('hover', null)">
          <!-- Kurzfahrt: kleinerer Kreis, mx-1 hält die Mitte auf der Zeitlinie -->
          <span v-if="e.kind === 'trip'" class="z-10 grid shrink-0 place-items-center rounded-full border-2 border-edge bg-white text-sky-600 dark:border-gray-200 dark:bg-gray-900 dark:text-sky-400"
            :class="isShort(e) ? 'mx-1 h-7 w-7' : 'h-9 w-9'">
            <MapIcon :class="isShort(e) ? 'h-4 w-4' : 'h-[18px] w-[18px]'" aria-hidden="true" />
          </span>
          <span v-else class="z-10 grid h-9 w-9 shrink-0 place-items-center rounded-full border-2 border-edge bg-green-600 text-white dark:border-transparent dark:bg-green-500 dark:text-gray-950">
            <BoltIcon class="h-[18px] w-[18px]" aria-hidden="true" />
          </span>
          <!-- Kurzfahrt: beide Zeilen nebeneinander, kleinere Schrift -->
          <span class="min-w-0 flex-1" :class="isShort(e) ? 'flex flex-wrap items-baseline gap-x-3 pt-1.5' : 'pt-2'">
            <span class="flex items-baseline gap-2 font-semibold leading-snug" :class="isShort(e) ? 'text-[13px]' : 'text-[15px]'">
              <span class="shrink-0 whitespace-nowrap tabular-nums text-gray-500 dark:text-gray-400">{{ hm(e.from) }}<template v-if="!partsOf(e)"> · {{ dur(e.to - e.from) }}</template></span>
              <span class="h-3.5 w-px shrink-0 self-center bg-gray-300 dark:bg-gray-600" :class="e.kind === 'charge' ? 'hidden md:block' : ''" aria-hidden="true" />
              <!-- Ladeort auf dem Handy in eigener Zeile (unten), damit er nicht abgeschnitten wird -->
              <span class="truncate" :class="e.kind === 'charge' ? 'hidden md:inline' : ''">
                <template v-if="e.kind === 'trip' && e.trip.distanceKm != null"><span class="sr-only">{{ t('storyday.trip') }} </span><span class="tabular-nums">{{ formatDistance(e.trip.distanceKm) }}</span></template>
                <template v-else-if="e.kind === 'trip'">{{ t('storyday.trip') }}</template>
                <template v-else>{{ placeName(e) }}</template>
              </span>
            </span>
            <span v-if="partsOf(e) || e.socTo != null || tempOf(e) != null || $slots['line2-end']"
              class="flex flex-wrap items-center gap-x-1.5 text-[13px] tabular-nums text-gray-500 dark:text-gray-400">
              <template v-if="partsOf(e)">
                <span class="whitespace-nowrap" data-testid="day-parts">{{ t('storyday.parts', { n: partsOf(e) }, partsOf(e)) }}</span><template v-if="e.socTo != null">·</template>
              </template>
              <template v-if="e.socFrom != null && e.socTo != null">
                <span class="whitespace-nowrap text-gray-600 dark:text-gray-300" data-testid="day-soc"><span class="sr-only">{{ t('storyday.soc') }} </span>{{ t('storyday.soc_range', { from: Math.round(e.socFrom), to: Math.round(e.socTo) }) }}</span>
              </template>
              <template v-else-if="e.socTo != null">
                <span class="whitespace-nowrap text-gray-600 dark:text-gray-300">{{ t('storyday.soc_to', { to: Math.round(e.socTo) }) }}</span>
              </template>
              <template v-if="tempOf(e) != null"><template v-if="e.socTo != null">·</template>
                <span class="whitespace-nowrap" :class="temperatureTone(tempOf(e)!)" data-testid="day-temp">{{ formatDecimal(tempOf(e)!, 0) }}&nbsp;°C</span>
              </template>
              <!-- Heizung, Klima, Akkuheizung als farbige Symbole, Last und Dauer im Tooltip -->
              <!-- Logbuch: weitere Angaben (ab 768 px in der Zeile, auf dem Handy teils in eigener Zeile) -->
              <slot name="line2-end" :event="e" />
              <TripClimateMarkers v-if="e.kind === 'trip'" :climate="e.trip.climate" align="start" compact />
            </span>
            <span v-if="e.kind === 'charge'" class="line-clamp-2 break-words text-[13px] font-semibold leading-snug text-gray-700 md:hidden dark:text-gray-200">{{ placeName(e) }}</span>
            <!-- Logbuch: Kosten, Verbrauch und Verbraucher einer Fahrt -->
            <slot v-if="e.kind === 'trip'" name="trip-extra" :trip="e.trip" />
            <span v-if="e.kind === 'charge' && missingChips(e).length" class="mt-1 flex gap-1">
              <span v-for="m in missingChips(e)" :key="m" data-testid="day-missing"
                class="rounded-full bg-amber-100 px-2 py-0.5 text-[11.5px] font-semibold text-amber-800 dark:bg-amber-400/15 dark:text-amber-300">{{ m }}</span>
            </span>
          </span>
          <!-- Logbuch ab 768 px: Kosten und Verbrauch rechts wie der Preis einer Ladung -->
          <span v-if="e.kind === 'trip' && $slots['trip-side']" class="hidden shrink-0 pt-2 text-right tabular-nums md:block">
            <slot name="trip-side" :trip="e.trip" />
          </span>
          <!-- isolate: Leaflets Ebenen dürfen nicht über die Kopfzeile des Sheets ragen -->
          <span v-if="e.kind === 'trip' && !isShort(e) && hasTripThumb(e.trip)" data-testid="day-trip-thumb" class="relative isolate block h-[52px] w-[72px] shrink-0 overflow-hidden rounded-lg bg-gray-100 md:h-14 md:w-24 dark:bg-white/5">
            <ActivityLocationMap variant="thumb" :start-geohash="e.trip.locationStartGeohash" :end-geohash="e.trip.locationEndGeohash"
              :route-polyline="e.trip.routePolyline" :route-kind="e.trip.routeKind" :trace-polyline="e.trip.tracePolyline" />
          </span>
          <span v-else-if="e.kind === 'charge'" class="shrink-0 pt-2 text-right tabular-nums">
            <span class="block text-[15px] font-bold leading-snug">{{ costOf(e) != null ? formatCurrency(costOf(e)!) : '' }}</span>
            <span class="block whitespace-nowrap text-[12px] text-gray-500 dark:text-gray-400">{{ chargeSide(e) }}</span>
            <span v-if="pricePerKwh(e)" class="block whitespace-nowrap text-[12px] text-gray-500 dark:text-gray-400" data-testid="day-price-kwh">{{ pricePerKwh(e) }}</span>
          </span>
          <ChevronRightIcon class="-ml-1 mt-2.5 h-4 w-4 shrink-0 text-gray-400" aria-hidden="true" />
        </button>
      </li>
    </template>
  </ul>
  <p v-if="credit" data-testid="day-map-credit" class="mt-1 text-right text-[10px] text-gray-400">{{ credit }}</p>
  </div>
</template>
