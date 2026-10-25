<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { BoltIcon, MapIcon, ChevronRightIcon } from '@heroicons/vue/24/outline'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import { relativeTimeParts } from '../../utils/recentActivity'
import type { StoryLatest } from '../../composables/storyInsights'
import { keepUnits } from '../../utils/modelStory'

/**
 * "Zuletzt": letzte Ladung und letzte Fahrt als je eine tippbare Zeile mit Chevron. Beantwortet
 * "ist meine Ladung angekommen" und führt, wenn ihr der Preis fehlt, direkt zum Nachtragen.
 */
const props = defineProps<{
  latest: StoryLatest
  carId: string | null
  now?: number
  /** Der Moment-Hero bietet das Nachtragen dieser Ladung schon an: hier kein zweiter Chip. */
  priceInHero?: boolean
}>()
const { t, locale } = useI18n()
const { formatCurrency, formatDistance, formatDecimal } = useLocaleFormat()

function when(iso: string): string {
  const parts = relativeTimeParts(Date.parse(iso), props.now ?? Date.now())
  if (!parts) return ''
  if (parts.unit === 'second' || parts.unit === 'minute') return t('story.latest_just_now')
  return new Intl.RelativeTimeFormat(locale.value, { numeric: 'auto' }).format(parts.value, parts.unit)
}

const charge = computed(() => {
  const c = props.latest.charge
  if (!c) return null
  const missingPrice = c.costEur == null
  return {
    title: c.kwh != null ? keepUnits(t('story.latest_charged', { kwh: `${formatDecimal(c.kwh, 1)} kWh` })) : t('story.latest_charged_plain'),
    sub: [when(c.at), [c.cpoName, c.chargingType].filter(Boolean).join(', ') || null, c.costEur != null ? formatCurrency(c.costEur) : null].filter(Boolean).join(' · '),
    missingPrice,
    // Einstieg wie aus der Monatsrückblick-Mail: LogsView öffnet die Ladungen ohne Preis.
    to: missingPrice && props.carId ? `/logs?car=${encodeURIComponent(props.carId)}&nachtragen=preis` : '/logs',
  }
})
const trip = computed(() => {
  const tr = props.latest.trip
  if (!tr) return null
  return {
    title: tr.km != null ? keepUnits(t('story.latest_drove', { km: formatDistance(tr.km, { round: true }) })) : t('story.latest_drove_plain'),
    sub: [when(tr.at), tr.routeType ? t(`model.route_type_${tr.routeType.toLowerCase()}`) : null].filter(Boolean).join(' · '),
  }
})

const chip = computed(() => !!charge.value?.missingPrice && !props.priceInHero)

const row = 'flex h-12 items-center gap-2.5 rounded-xl bg-white px-3 edge-card transition-transform active:translate-x-0.5 active:translate-y-0.5 active:shadow-none focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-green-600 dark:bg-gray-800'
</script>

<template>
  <div class="grid gap-2 md:grid-cols-2" data-testid="story-latest">
    <router-link v-if="charge" :to="charge.to" :class="row" data-testid="story-latest-charge">
      <BoltIcon class="h-5 w-5 shrink-0 text-green-700 dark:text-green-400" aria-hidden="true" />
      <span class="min-w-0 flex-1 leading-tight">
        <span class="block truncate text-[14px] font-semibold text-gray-900 tabular-nums dark:text-gray-50">{{ charge.title }}</span>
        <span v-if="charge.sub" class="block truncate text-[12px] text-gray-500 tabular-nums dark:text-gray-400" :class="chip ? 'md:hidden' : ''">{{ charge.sub }}</span>
        <!-- Ab md (zwei Zeilen nebeneinander) nimmt der Chip den Platz der Unterzeile, damit der Titel lesbar bleibt. -->
        <span v-if="chip" class="mt-0.5 hidden rounded-sm bg-amber-100 px-1.5 text-[12px] font-semibold text-amber-800 md:inline-block dark:bg-amber-900/40 dark:text-amber-300">{{ t('story.latest_add_price') }}</span>
      </span>
      <span v-if="chip" class="shrink-0 rounded-sm bg-amber-100 px-2 py-1 text-[12px] font-semibold text-amber-800 md:hidden dark:bg-amber-900/40 dark:text-amber-300">{{ t('story.latest_add_price') }}</span>
      <ChevronRightIcon class="h-4 w-4 shrink-0 text-gray-400" aria-hidden="true" />
    </router-link>
    <router-link v-if="trip" to="/logs" :class="row" data-testid="story-latest-trip">
      <MapIcon class="h-5 w-5 shrink-0 text-green-700 dark:text-green-400" aria-hidden="true" />
      <span class="min-w-0 flex-1 leading-tight">
        <span class="block truncate text-[14px] font-semibold text-gray-900 tabular-nums dark:text-gray-50">{{ trip.title }}</span>
        <span v-if="trip.sub" class="block truncate text-[12px] text-gray-500 tabular-nums dark:text-gray-400">{{ trip.sub }}</span>
      </span>
      <ChevronRightIcon class="h-4 w-4 shrink-0 text-gray-400" aria-hidden="true" />
    </router-link>
  </div>
</template>
