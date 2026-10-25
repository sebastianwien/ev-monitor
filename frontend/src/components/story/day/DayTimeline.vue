<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { BoltIcon, MapIcon, ChevronRightIcon, LinkIcon } from '@heroicons/vue/24/outline'
import { useLocaleFormat } from '../../../composables/useLocaleFormat'
import { tripLine } from '../../../utils/tripMap'
import {
  chargeKwh, chargePlace, missingOf, canMerge, showStanding, gapMinutes, routeThumb, type DayEvent,
} from '../../../composables/storyDay'

/**
 * Ablauf eines Tages: chronologische Liste mit senkrechter Leiste. Zeilen mindestens 56 px, hier wird
 * auf dem Handy getippt. Zwischen zwei Fahrten mit kurzem Halt: Zusammenführen, zweistufig bestätigt.
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
const soc = (e: DayEvent) => e.socFrom != null && e.socTo != null
  ? t('storyday.soc_range', { from: Math.round(e.socFrom), to: Math.round(e.socTo) })
  : e.socTo != null ? t('storyday.soc_to', { to: Math.round(e.socTo) }) : ''
function placeName(e: DayEvent & { kind: 'charge' }) {
  const p = chargePlace(e.log)
  return p.kind === 'operator' ? p.name : t(`storyday.place_${p.kind}`)
}
function chargeSub(e: DayEvent & { kind: 'charge' }) {
  const k = chargeKwh(e.log)
  const type = e.log.chargingType && e.log.chargingType !== 'UNKNOWN' ? e.log.chargingType : null
  return [type, dur(e.to - e.from), k != null ? `${formatDecimal(k, 1)} kWh` : null].filter(Boolean).join(' · ')
}
const tripSub = (e: DayEvent) => dur(e.to - e.from)
function thumb(e: DayEvent & { kind: 'trip' }) {
  const line = tripLine(e.trip.tracePolyline, e.trip.routePolyline, e.trip.routeKind)
  return line ? routeThumb(line.points, 60, 44) : null
}
/** Änderung des Akkus als "+72 %" oder "−2 %" */
const delta = (from: number, to: number) => { const d = Math.round(to - from); return d > 0 ? `+${d} %` : `−${-d} %` }

/** Zusammenführen: erst fragen, dann tun. Schlüssel = id der früheren Fahrt. */
const confirming = ref<string | null>(null)
</script>

<template>
  <ul class="relative -mx-2" data-testid="day-timeline">
    <span class="pointer-events-none absolute bottom-6 left-[77px] top-6 w-0.5 bg-gray-200 dark:bg-gray-800" aria-hidden="true" />
    <template v-for="(e, i) in events" :key="e.id">
      <!-- Zwischen zwei Einträgen: Halt mit Zusammenführen oder längere Standzeit -->
      <li v-if="i && canMerge(events[i - 1], e)" class="pl-[90px] text-[12.5px] text-gray-500 dark:text-gray-400" data-testid="day-merge-row">
        <div v-if="confirming !== events[i - 1].id" class="flex min-h-11 flex-wrap items-center gap-x-2">
          <span class="tabular-nums">{{ gapMinutes(events[i - 1], e) <= 0 ? t('storyday.direct') : t('storyday.halt', { d: dur(gapMinutes(events[i - 1], e)) }) }}</span>
          <button type="button" data-testid="day-merge" class="inline-flex min-h-11 items-center gap-1 rounded-full px-2 font-semibold text-gray-900 underline decoration-gray-300 underline-offset-4 hover:decoration-gray-900 dark:text-gray-100 dark:decoration-gray-600"
            @click="confirming = events[i - 1].id">
            <LinkIcon class="h-4 w-4" aria-hidden="true" />{{ t('storyday.merge') }}
          </button>
        </div>
        <div v-else class="my-1 mr-2 grid gap-2 rounded-xl border-2 border-gray-900 p-3 dark:border-gray-700" role="group" :aria-label="t('storyday.merge')">
          <p class="text-[13.5px] text-gray-800 dark:text-gray-200">{{ t('storyday.merge_confirm') }}</p>
          <div class="flex gap-2">
            <button type="button" class="min-h-11 flex-1 rounded-xl px-3 text-[14px] font-semibold text-gray-600 dark:text-gray-300" @click="confirming = null">{{ t('common.cancel') }}</button>
            <button type="button" data-testid="day-merge-confirm" :disabled="merging"
              class="btn-3d min-h-11 flex-1 rounded-xl border-2 border-gray-900 bg-gray-900 px-3 text-[14px] font-semibold text-white disabled:opacity-40 dark:border-transparent dark:bg-gray-100 dark:text-gray-900"
              @click="emit('merge', events[i - 1], e)">{{ t('storyday.merge_do') }}</button>
          </div>
        </div>
      </li>
      <li v-else-if="i && showStanding(events[i - 1], e)" class="py-1 pl-[90px] text-[12px] tabular-nums text-gray-400 dark:text-gray-500">
        {{ t('storyday.standing', { d: dur(gapMinutes(events[i - 1], e)) }) }}
      </li>

      <li>
        <button type="button" :data-testid="`day-row-${e.kind}`" :aria-current="e.id === selectedId"
          class="relative flex min-h-14 w-full items-center gap-3 rounded-2xl px-2 py-2.5 text-left transition-colors hover:bg-gray-50 focus-visible:outline-2 focus-visible:outline-green-600 dark:hover:bg-white/5"
          :class="e.id === selectedId ? 'bg-green-50 dark:bg-green-400/10' : ''"
          @click="emit('select', e)">
          <span class="w-11 shrink-0 text-[13px] font-semibold leading-tight tabular-nums">
            {{ hm(e.from) }}<span class="block font-normal text-gray-400 dark:text-gray-500">{{ hm(e.to) }}</span>
          </span>
          <template v-if="e.kind === 'trip'">
            <span class="z-10 grid h-9 w-9 shrink-0 place-items-center rounded-full border-2 border-gray-900 bg-white dark:border-gray-200 dark:bg-gray-900">
              <MapIcon class="h-[18px] w-[18px]" aria-hidden="true" />
            </span>
            <span class="min-w-0 flex-1">
              <span class="block truncate text-[15px] font-semibold">{{ t('storyday.trip') }} <span class="tabular-nums">{{ e.trip.distanceKm != null ? formatDistance(e.trip.distanceKm) : '' }}</span></span>
              <span class="line-clamp-2 block text-[13px] tabular-nums text-gray-500 dark:text-gray-400">{{ tripSub(e) }}</span>
              <span v-if="e.socFrom != null && e.socTo != null" class="mt-1 flex items-center gap-2 text-[13px] tabular-nums" data-testid="day-soc">
                <span class="text-gray-600 dark:text-gray-300">{{ t('storyday.soc_range', { from: Math.round(e.socFrom), to: Math.round(e.socTo) }) }}</span>
                <span v-if="Math.round(e.socTo - e.socFrom) !== 0" class="rounded-full px-1.5 py-px text-[12px] font-semibold"
                  :class="e.socTo > e.socFrom ? 'bg-green-50 text-green-800 dark:bg-green-400/10 dark:text-green-300' : 'bg-gray-100 text-gray-700 dark:bg-white/10 dark:text-gray-300'">{{ delta(e.socFrom, e.socTo) }}</span>
              </span>
              <span v-else-if="e.socTo != null" class="mt-1 block text-[13px] tabular-nums text-gray-600 dark:text-gray-300">{{ soc(e) }}</span>
            </span>
            <svg v-if="thumb(e)" viewBox="0 0 60 44" class="h-11 w-[60px] shrink-0 rounded-lg bg-gray-100 dark:bg-white/5" aria-hidden="true">
              <path :d="thumb(e)!.d" class="fill-none stroke-gray-900 dark:stroke-gray-100" stroke-width="1.6" stroke-linejoin="round" stroke-linecap="round" />
              <circle :cx="thumb(e)!.end[0]" :cy="thumb(e)!.end[1]" r="2.6" class="fill-green-600 dark:fill-green-400" />
            </svg>
          </template>
          <template v-else>
            <span class="z-10 grid h-9 w-9 shrink-0 place-items-center rounded-full border-2 border-gray-900 bg-green-600 text-white dark:border-transparent dark:bg-green-500 dark:text-gray-950">
              <BoltIcon class="h-[18px] w-[18px]" aria-hidden="true" />
            </span>
            <span class="min-w-0 flex-1">
              <span class="block truncate text-[15px] font-semibold">{{ placeName(e) }}</span>
              <span class="line-clamp-2 block text-[13px] tabular-nums text-gray-500 dark:text-gray-400">{{ chargeSub(e) }}</span>
              <span v-if="e.socFrom != null && e.socTo != null" class="mt-1 flex items-center gap-2 text-[13px] tabular-nums" data-testid="day-soc">
                <span class="text-gray-600 dark:text-gray-300">{{ t('storyday.soc_range', { from: Math.round(e.socFrom), to: Math.round(e.socTo) }) }}</span>
                <span v-if="Math.round(e.socTo - e.socFrom) !== 0" class="rounded-full px-1.5 py-px text-[12px] font-semibold"
                  :class="e.socTo > e.socFrom ? 'bg-green-50 text-green-800 dark:bg-green-400/10 dark:text-green-300' : 'bg-gray-100 text-gray-700 dark:bg-white/10 dark:text-gray-300'">{{ delta(e.socFrom, e.socTo) }}</span>
              </span>
              <span v-else-if="e.socTo != null" class="mt-1 block text-[13px] tabular-nums text-gray-600 dark:text-gray-300">{{ soc(e) }}</span>
              <span v-if="missingOf(e.log).length" class="mt-1 flex gap-1">
                <span v-for="m in missingOf(e.log)" :key="m" data-testid="day-missing"
                  class="rounded-full bg-amber-100 px-2 py-0.5 text-[11.5px] font-semibold text-amber-800 dark:bg-amber-400/15 dark:text-amber-300">{{ t(`storyday.missing_${m}`) }}</span>
              </span>
            </span>
            <span class="shrink-0 text-[15px] font-bold tabular-nums">{{ e.log.costEur != null ? formatCurrency(e.log.costEur) : '' }}</span>
          </template>
          <ChevronRightIcon class="h-4 w-4 shrink-0 text-gray-400" aria-hidden="true" />
        </button>
      </li>
    </template>
  </ul>
</template>
