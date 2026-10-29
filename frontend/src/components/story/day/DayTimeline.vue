<script setup lang="ts">
import { computed, defineAsyncComponent, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { BoltIcon, MapIcon, ChevronRightIcon, LinkIcon } from '@heroicons/vue/24/outline'
import { useLocaleFormat } from '../../../composables/useLocaleFormat'
import {
  chargeKwh, chargePlace, missingOf, canMerge, showStanding, gapMinutes, type DayEvent,
} from '../../../composables/storyDay'
import { hasTripMap, isRoutedLine, mapCredit, tripLine } from '../../../utils/tripMap'

const ActivityLocationMap = defineAsyncComponent(() => import('../../dashboard/ActivityLocationMap.vue'))

/**
 * Ablauf eines Tages: chronologische Liste mit senkrechter Leiste durch die Symbole. Zeilen mindestens 56 px,
 * hier wird auf dem Handy getippt. Erste Zeile Startzeit | km bzw. Ort, Unterzeile Dauer und Akku; Symbol, Text und
 * Minikarte auf einer Achse. Halt- und Standzeile gleich hoch (44 px), damit die Abstände zwischen Fahrten gleich sind.
 * Zwischen zwei Fahrten mit kurzem Halt: Zusammenführen, zweistufig bestätigt.
 */
const props = defineProps<{ events: DayEvent[]; selectedId: string | null; merging: boolean }>()
const emit = defineEmits<{ select: [event: DayEvent]; merge: [earlier: DayEvent, later: DayEvent] }>()
const { t } = useI18n()
const { formatDecimal, formatDistance, formatCurrency } = useLocaleFormat()

const hm = (min: number) => min >= 1440 ? '24:00' : `${String(Math.floor(min / 60)).padStart(2, '0')}:${String(min % 60).padStart(2, '0')}`
function dur(min: number) {
  const h = Math.floor(min / 60), m = min % 60
  return h && m ? t('storyday.dur_hm', { h, m }) : h ? t('storyday.dur_h', { h }) : t('storyday.dur_m', { m })
}
function placeName(e: DayEvent & { kind: 'charge' }) {
  const p = chargePlace(e.log)
  return p.kind === 'operator' ? p.name : t(`storyday.place_${p.kind}`)
}
/** Rechts unter dem Preis: Ladeart und Energie */
function chargeSide(e: DayEvent & { kind: 'charge' }) {
  const k = chargeKwh(e.log)
  const type = e.log.chargingType && e.log.chargingType !== 'UNKNOWN' ? e.log.chargingType : null
  return [type, k != null ? `${formatDecimal(k, 1)} kWh` : null].filter(Boolean).join(' · ')
}
/** Die Minikarten tragen keine eigene Attribution - sie steht einmal unter der Liste. */
const credit = computed(() => {
  const mapped = props.events.flatMap(e => e.kind === 'trip' && hasTripMap(e.trip) ? [e.trip] : [])
  return mapped.length ? mapCredit(mapped.some(tr => isRoutedLine(tripLine(tr.tracePolyline, tr.routePolyline, tr.routeKind)))) : null
})
/** Änderung des Akkus als "+72 %" oder "−2 %" */
const delta = (from: number, to: number) => { const d = Math.round(to - from); return d > 0 ? `+${d} %` : `−${-d} %` }

/** Zusammenführen: erst fragen, dann tun. Schlüssel = id der früheren Fahrt. */
const confirming = ref<string | null>(null)
</script>

<template>
  <div>
  <ul class="relative -mx-2" data-testid="day-timeline">
    <span class="pointer-events-none absolute bottom-6 left-[25px] top-6 w-0.5 bg-gray-200 dark:bg-gray-800" aria-hidden="true" />
    <template v-for="(e, i) in events" :key="e.id">
      <!-- Zwischen zwei Einträgen: Halt mit Zusammenführen oder längere Standzeit -->
      <li v-if="i && canMerge(events[i - 1], e)" class="pl-14 text-[12.5px] text-gray-500 dark:text-gray-400" data-testid="day-merge-row">
        <div v-if="confirming !== events[i - 1].id" class="flex min-h-11 flex-wrap items-center gap-x-2">
          <span class="tabular-nums">{{ gapMinutes(events[i - 1], e) <= 0 ? t('storyday.direct') : t('storyday.halt', { d: dur(gapMinutes(events[i - 1], e)) }) }}</span>
          <button type="button" data-testid="day-merge" class="inline-flex min-h-11 items-center gap-1 rounded-full px-2 font-semibold text-gray-900 underline decoration-gray-300 underline-offset-4 hover:decoration-gray-900 dark:text-gray-100 dark:decoration-gray-600"
            @click="confirming = events[i - 1].id">
            <LinkIcon class="h-4 w-4" aria-hidden="true" />{{ t('storyday.merge') }}
          </button>
        </div>
        <div v-else class="my-1 mr-2 grid gap-2 rounded-xl border-2 border-edge p-3 dark:border-gray-700" role="group" :aria-label="t('storyday.merge')">
          <p class="text-[13.5px] text-gray-800 dark:text-gray-200">{{ t('storyday.merge_confirm') }}</p>
          <div class="flex gap-2">
            <button type="button" class="min-h-11 flex-1 rounded-xl px-3 text-[14px] font-semibold text-gray-600 dark:text-gray-300" @click="confirming = null">{{ t('common.cancel') }}</button>
            <button type="button" data-testid="day-merge-confirm" :disabled="merging"
              class="btn-3d cta-shadow-sm min-h-11 flex-1 rounded-xl border-2 border-gray-900 bg-gray-900 dark:border-edge px-3 text-[14px] font-semibold text-white disabled:opacity-40 dark:bg-gray-100 dark:text-gray-900"
              @click="emit('merge', events[i - 1], e)">{{ t('storyday.merge_do') }}</button>
          </div>
        </div>
      </li>
      <li v-else-if="i && showStanding(events[i - 1], e)" class="flex min-h-11 items-center pl-14 text-[12px] tabular-nums text-gray-500 dark:text-gray-400">
        {{ t('storyday.standing', { d: dur(gapMinutes(events[i - 1], e)) }) }}
      </li>

      <li>
        <button type="button" :data-testid="`day-row-${e.kind}`" :aria-current="e.id === selectedId"
          class="relative flex min-h-14 w-full items-center gap-3 rounded-2xl px-2 py-2 text-left transition-colors hover:bg-gray-50 focus-visible:outline-2 focus-visible:outline-green-600 dark:hover:bg-white/5"
          :class="e.id === selectedId ? 'bg-green-50 dark:bg-green-400/10' : ''"
          @click="emit('select', e)">
          <span v-if="e.kind === 'trip'" class="z-10 grid h-9 w-9 shrink-0 place-items-center rounded-full border-2 border-edge bg-white text-sky-600 dark:border-gray-200 dark:bg-gray-900 dark:text-sky-400">
            <MapIcon class="h-[18px] w-[18px]" aria-hidden="true" />
          </span>
          <span v-else class="z-10 grid h-9 w-9 shrink-0 place-items-center rounded-full border-2 border-edge bg-green-600 text-white dark:border-transparent dark:bg-green-500 dark:text-gray-950">
            <BoltIcon class="h-[18px] w-[18px]" aria-hidden="true" />
          </span>
          <span class="min-w-0 flex-1">
            <span class="flex items-baseline gap-2 text-[15px] font-semibold leading-snug">
              <span class="shrink-0 tabular-nums text-gray-500 dark:text-gray-400">{{ hm(e.from) }}</span>
              <span class="h-3.5 w-px shrink-0 self-center bg-gray-300 dark:bg-gray-600" aria-hidden="true" />
              <span class="truncate">
                <template v-if="e.kind === 'trip' && e.trip.distanceKm != null"><span class="sr-only">{{ t('storyday.trip') }} </span><span class="tabular-nums">{{ formatDistance(e.trip.distanceKm) }}</span></template>
                <template v-else-if="e.kind === 'trip'">{{ t('storyday.trip') }}</template>
                <template v-else>{{ placeName(e) }}</template>
              </span>
            </span>
            <span class="flex flex-wrap items-center gap-x-1.5 text-[13px] tabular-nums text-gray-500 dark:text-gray-400">
              <span class="whitespace-nowrap">{{ dur(e.to - e.from) }}</span>
              <template v-if="e.socFrom != null && e.socTo != null">·
                <span class="whitespace-nowrap text-gray-600 dark:text-gray-300" data-testid="day-soc"><span class="sr-only">{{ t('storyday.soc') }} </span>{{ t('storyday.soc_range', { from: Math.round(e.socFrom), to: Math.round(e.socTo) }) }}</span>
                <span v-if="Math.round(e.socTo - e.socFrom) !== 0" class="whitespace-nowrap rounded-full px-1.5 text-[12px] font-semibold"
                  :class="e.socTo > e.socFrom ? 'bg-green-50 text-green-800 dark:bg-green-400/10 dark:text-green-300' : 'bg-gray-100 text-gray-700 dark:bg-white/10 dark:text-gray-300'">{{ delta(e.socFrom, e.socTo) }}</span>
              </template>
              <template v-else-if="e.socTo != null">·
                <span class="whitespace-nowrap text-gray-600 dark:text-gray-300">{{ t('storyday.soc_to', { to: Math.round(e.socTo) }) }}</span>
              </template>
            </span>
            <span v-if="e.kind === 'charge' && missingOf(e.log).length" class="mt-1 flex gap-1">
              <span v-for="m in missingOf(e.log)" :key="m" data-testid="day-missing"
                class="rounded-full bg-amber-100 px-2 py-0.5 text-[11.5px] font-semibold text-amber-800 dark:bg-amber-400/15 dark:text-amber-300">{{ t(`storyday.missing_${m}`) }}</span>
            </span>
          </span>
          <!-- isolate: Leaflets Ebenen dürfen nicht über die Kopfzeile des Sheets ragen -->
          <span v-if="e.kind === 'trip' && hasTripMap(e.trip)" data-testid="day-trip-thumb" class="relative isolate block h-[52px] w-[72px] shrink-0 overflow-hidden rounded-lg bg-gray-100 dark:bg-white/5">
            <ActivityLocationMap variant="thumb" :start-geohash="e.trip.locationStartGeohash" :end-geohash="e.trip.locationEndGeohash"
              :route-polyline="e.trip.routePolyline" :route-kind="e.trip.routeKind" :trace-polyline="e.trip.tracePolyline" />
          </span>
          <span v-else-if="e.kind === 'charge'" class="shrink-0 text-right tabular-nums">
            <span class="block text-[15px] font-bold leading-snug">{{ e.log.costEur != null ? formatCurrency(e.log.costEur) : '' }}</span>
            <span class="block whitespace-nowrap text-[12px] text-gray-500 dark:text-gray-400">{{ chargeSide(e) }}</span>
          </span>
          <ChevronRightIcon class="-ml-1 h-4 w-4 shrink-0 self-center text-gray-400" aria-hidden="true" />
        </button>
      </li>
    </template>
  </ul>
  <p v-if="credit" data-testid="day-map-credit" class="mt-1 text-right text-[10px] text-gray-400">{{ credit }}</p>
  </div>
</template>
