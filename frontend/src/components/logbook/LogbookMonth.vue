<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import LogbookDay from './LogbookDay.vue'
import type { TripFacts } from '../../utils/tripCalculations'
import StandingText from '../story/day/StandingText.vue'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import type { DayEvent } from '../../composables/storyDay'
import type { LogbookMonth } from '../../utils/logbook'
import { tripDayLabel } from '../../utils/tripTimeFormat'

/**
 * Ein Monat als Kapitel: der Kopf trägt die Summen, darunter die Tage (deren Kopf klebt beim Scrollen).
 * Tage ohne Eintrag stehen als eine Zeile: links der Tag bzw. die Spanne, rechts "keine Fahrt",
 * darunter Standzeit und Standverlust.
 */
const props = defineProps<{
  month: LogbookMonth
  /** Link zum Freischalten am sichtbaren Standverlust, solange er nur Vorgeschmack ist. */
  unlockTo: string | null
  facts: Record<string, TripFacts>
  cardNames: Map<string, string>
  selectedId: string | null
  merging: boolean
  wide: boolean
}>()
const emit = defineEmits<{ select: [event: DayEvent]; merge: [earlier: DayEvent, later: DayEvent] }>()
const { t, locale } = useI18n()
const { formatDistance, formatCurrency } = useLocaleFormat()

const name = computed(() => {
  const [y, m] = props.month.key.split('-').map(Number)
  return new Date(y, m - 1, 1).toLocaleDateString(locale.value, { month: 'long', year: 'numeric' })
})
const line = computed(() => {
  const s = props.month.totals
  return [
    s.trips ? t('storyday.pill_trips', { n: s.trips }, s.trips) : null,
    s.trips ? formatDistance(s.km, { round: true }) : null,
    s.charges ? t('storyday.pill_charges', { n: s.charges }, s.charges) : null,
    s.eur ? formatCurrency(s.eur) : null,
  ].filter(Boolean).join(' · ')
})
const dayLabel = (key: string) => tripDayLabel(key, locale.value, new Date())
const idleLabel = (from: string, to: string) => from === to
  ? dayLabel(from)
  : t('logbook.idle_range', { from: dayLabel(from), to: dayLabel(to) })
</script>

<template>
  <section :aria-label="name" data-testid="logbook-month">
    <header class="border-b border-gray-200 px-4 py-2.5 dark:border-gray-700" data-testid="logbook-month-head">
      <div class="flex items-baseline justify-between gap-3">
        <h2 class="text-[19px] font-bold tracking-tight">{{ name }}</h2>
        <span v-if="month.totals.costPer100km != null" class="text-[13px] font-semibold tabular-nums">
          {{ t('logbook.per_100km', { cost: formatCurrency(month.totals.costPer100km) }) }}
        </span>
      </div>
      <p v-if="line" class="text-[13px] tabular-nums text-gray-600 dark:text-gray-300">{{ line }}</p>
    </header>

    <template v-for="item in month.items" :key="item.kind === 'day' ? item.key : `idle-${item.from}`">
      <LogbookDay v-if="item.kind === 'day'" :day-key="item.key" :events="item.events" :standing="item.standing" :unlock-to="unlockTo" :facts="facts" :card-names="cardNames" :cost-per100km="item.costPer100km"
        :selected-id="selectedId" :merging="merging" :wide="wide" @select="emit('select', $event)" @merge="(a, b) => emit('merge', a, b)" />
      <section v-else class="border-t border-gray-200 px-4 py-3 dark:border-gray-700" data-testid="logbook-idle">
        <div class="flex items-baseline justify-between gap-3">
          <h3 class="text-[15px] font-bold text-gray-500 dark:text-gray-400">{{ idleLabel(item.from, item.to) }}</h3>
          <span class="text-[12px] text-gray-500 dark:text-gray-400">{{ t('logbook.no_trip') }}</span>
        </div>
        <p v-if="item.standing" class="mt-1 flex flex-wrap items-center gap-x-1.5 text-[12px] tabular-nums text-gray-500 dark:text-gray-400">
          <StandingText v-bind="item.standing" :unlock-to="unlockTo" />
        </p>
      </section>
    </template>
  </section>
</template>
