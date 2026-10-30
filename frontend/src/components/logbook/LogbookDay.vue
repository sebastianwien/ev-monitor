<script setup lang="ts">
import { computed, ref } from 'vue'
import { useIsMobile } from '../../composables/useIsMobile'
import { useI18n } from 'vue-i18n'
import DayRibbon from '../story/day/DayRibbon.vue'
import DayTimeline, { type StandingRow } from '../story/day/DayTimeline.vue'
import { tripDetailKeys, chargeDetailKeys, type Standing } from '../../utils/logbook'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import { daySummary, type DayEvent, type DayTrip } from '../../composables/storyDay'
import { tripDayLabel } from '../../utils/tripTimeFormat'

import type { TripFacts } from '../../utils/tripCalculations'

/**
 * Ein Tag im Logbuch: Kopf mit Tag links und Summe rechts (klebt beim Scrollen), darunter Akku-Diagramm und
 * Ablauf aus dem Tagesblatt der Story. Der Ablauf steht neueste zuerst, wie das Buch. Das Diagramm nur, wenn
 * sich der Akku bewegt hat; eine flache Linie sagt nichts. Teilladungen stehen im Ablauf als ein Bündel
 * (`rows`), im Diagramm und in der Summe jede für sich (`events`).
 */
const props = defineProps<{
  dayKey: string
  events: DayEvent[]
  /** Ablauf: wie `events`, Teilladungen als ein Bündel */
  rows: DayEvent[]
  standing: Record<string, Standing>
  unlockTo: string | null
  facts: Record<string, TripFacts>
  /** Namen der eigenen Ladekarten je id */
  cardNames: Map<string, string>
  /** € pro 100 km der Fahrten des Tages (utils/logbook, tripGroupCostPer100km) */
  costPer100km: number | null
  selectedId: string | null
  merging: boolean
  /** Ab 1024 px: das Akku-Diagramm ist antippbar wie im Tagesblatt. */
  wide: boolean
}>()
const emit = defineEmits<{ select: [event: DayEvent]; merge: [earlier: DayEvent, later: DayEvent] }>()
const { t, locale } = useI18n()
const { formatDistance, formatCurrency, formatDecimal, formatConsumption } = useLocaleFormat()

const showRibbon = computed(() => {
  const socs = props.events.flatMap(e => [e.socFrom, e.socTo]).filter((v): v is number => v != null).map(Math.round)
  return socs.length > 0 && Math.max(...socs) > Math.min(...socs)
})
const stickyTop = { top: 'var(--content-top, var(--top-nav-h))' }
/** Zeile unter dem Mauszeiger: ihre Balken im Diagramm treten hervor (Desktop), beim Bündel die aller Teile */
const hovered = ref<string | null>(null)
const hoveredIds = computed(() => {
  const row = props.rows.find(e => e.id === hovered.value)
  if (!row) return null
  return row.kind === 'charge' && row.parts ? row.parts.map(p => p.id) : [row.id]
})
/** Ab 768 px klebt das Diagramm im Tageskopf, darunter steht es im Blatt: immer nur eins im DOM */
const md = useIsMobile('(min-width: 768px)')
/** Ab 768 px hinter Akku und Temperatur: Tempo, Streckentyp, Besonderheiten */
const tripDetails = (trip: DayTrip) => tripDetailKeys(trip).map(k => t(k.key, k.params ?? {}))
/**
 * Ladekarte der Ladung, überall (auf dem Handy in eigener Zeile); der Preis je kWh steht rechts unter dem Betrag.
 * Beim Bündel nur, wenn alle Teile mit derselben Karte geladen wurden.
 */
function cardOf(e: DayEvent & { kind: 'charge' }): string | null {
  const ids = new Set((e.parts ?? [e.log]).map(l => l.chargingProviderId ?? null))
  const [id] = ids
  return ids.size === 1 && id ? props.cardNames.get(id) ?? null : null
}
/** Ladung, ab 768 px: höchste Ladeleistung und Effizienz (auf dem Handy im Detail); beim Bündel je Teil im Detail */
const chargeMore = (e: DayEvent & { kind: 'charge' }) => e.parts ? [] : chargeDetailKeys(e.log).map(k => t(k.key, k.params ?? {}))
const costPer100 = (id: string) => `${formatCurrency(props.facts[id].eurPer100km!)}/100km`
const consumption = (id: string) => `${props.facts[id].estimated ? '~' : ''}${formatConsumption(props.facts[id].kwhPer100km)}`
const label = computed(() => tripDayLabel(props.dayKey, locale.value, new Date()))
/** Am sichtbaren Standverlust der Link zum Freischalten, solange er nur Vorgeschmack ist */
const standingRows = computed<Record<string, StandingRow>>(() => Object.fromEntries(Object.entries(props.standing)
  .map(([id, s]) => [id, { ...s, unlockTo: s.kwh != null ? props.unlockTo : null }])))
/** Mit Fahrten: Fahrten, km, verbrauchte Energie, € pro 100 km wie die Zeilen. Nur Ladungen: Ladungen, geladene Energie, Betrag. */
const sum = computed(() => {
  const s = daySummary(props.events)
  if (!s.trips) {
    return [
      t('storyday.pill_charges', { n: s.charges }, s.charges),
      s.kwh ? `${formatDecimal(s.kwh, 1)} kWh` : null,
      s.eur ? formatCurrency(s.eur) : null,
    ].filter(Boolean).join(' · ')
  }
  const trips = props.events.flatMap(e => e.kind === 'trip' && props.facts[e.id] ? [props.facts[e.id]] : [])
  const kwh = trips.reduce((a, f) => a + (f.kwh ?? 0), 0)
  return [
    t('storyday.pill_trips', { n: s.trips }, s.trips),
    formatDistance(s.km),
    kwh ? `${formatDecimal(kwh, 1)} kWh` : null,
    props.costPer100km != null ? `${formatCurrency(props.costPer100km)}/100km` : null,
  ].filter(Boolean).join(' · ')
})
</script>

<template>
  <section class="border-t border-gray-200 px-4 pb-3 dark:border-gray-700" data-testid="logbook-day">
    <div class="sticky z-20 -mx-4 mb-1 border-b border-gray-200 bg-white/95 px-4 py-2.5 backdrop-blur dark:border-gray-700 dark:bg-gray-900/95"
      :style="stickyTop" data-testid="logbook-day-head">
      <div class="flex items-baseline justify-between gap-3">
        <h3 class="text-[15px] font-bold">{{ label }}</h3>
        <span class="text-right text-[12px] tabular-nums text-gray-500 dark:text-gray-400">{{ sum }}</span>
      </div>
      <!-- Desktop: das Diagramm klebt mit dem Tageskopf, Hover über einer Zeile hebt ihre Balken hervor -->
      <DayRibbon v-if="showRibbon && md" :highlight-ids="hoveredIds" :events="events" :selected-id="selectedId" :interactive="wide" @select="emit('select', $event)" />
    </div>
    <DayRibbon v-if="showRibbon && !md" :events="events" :selected-id="selectedId" :interactive="wide" @select="emit('select', $event)" />
    <div class="mt-2">
      <DayTimeline :events="rows" :selected-id="selectedId" :merging="merging" :standing="standingRows" newest-first no-credit
        @select="emit('select', $event)" @merge="(a, b) => emit('merge', a, b)" @hover="id => { hovered = id }">
        <template #line2-end="{ event }">
          <template v-if="event.kind === 'trip'">
            <!-- Der Punkt hängt am Wert: bricht die Zeile um, steht keiner allein am Ende -->
            <span v-for="text in tripDetails(event.trip)" :key="text" class="hidden whitespace-nowrap md:inline">· {{ text }}</span>
          </template>
          <template v-else>
            <!-- Ladekarte: auf dem Handy eigene Zeile, ab 768 px weiter in der Zeile -->
            <template v-if="cardOf(event)">
              <span class="basis-full md:hidden" aria-hidden="true" />
              <span class="whitespace-nowrap"><span class="hidden md:inline">· </span>{{ cardOf(event) }}</span>
            </template>
            <span v-for="text in chargeMore(event)" :key="text" class="hidden whitespace-nowrap md:inline">· {{ text }}</span>
          </template>
        </template>
        <!-- Handy: dritte Zeile; ab 768 px rechts neben der Karte -->
        <template #trip-extra="{ trip }">
          <!-- Handy: Euro zuerst; Verbrauch je 100 km steht im Detail -->
          <span v-if="facts[trip.id]?.eurPer100km != null"
            class="block whitespace-nowrap text-[13px] tabular-nums text-gray-500 md:hidden dark:text-gray-400" data-testid="logbook-trip-facts">{{ costPer100(trip.id) }}</span>
        </template>
        <template #trip-side="{ trip }">
          <span class="block whitespace-nowrap text-[15px] font-bold leading-snug">{{ facts[trip.id]?.eurPer100km != null ? costPer100(trip.id) : '' }}</span>
          <span v-if="facts[trip.id]?.kwhPer100km != null" class="block whitespace-nowrap text-[12px] text-gray-500 dark:text-gray-400">{{ consumption(trip.id) }}</span>
        </template>
      </DayTimeline>
    </div>
  </section>
</template>
