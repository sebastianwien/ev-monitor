<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { BoltIcon, MapIcon, ChevronRightIcon } from '@heroicons/vue/24/outline'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import { relativeTimeParts } from '../../utils/recentActivity'
import type { StoryLatest } from '../../composables/storyInsights'

/**
 * "Zuletzt": letzte Ladung und letzte Fahrt als zwei tippbare Karten. Beantwortet "ist meine
 * Ladung angekommen" und führt, wenn ihr der Preis fehlt, direkt zum Nachtragen.
 */
const props = defineProps<{ latest: StoryLatest; carId: string | null; now?: number }>()
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
    title: c.kwh != null ? t('story.latest_charged', { kwh: `${formatDecimal(c.kwh, 1)} kWh` }) : t('story.latest_charged_plain'),
    sub: [when(c.at), c.costEur != null ? formatCurrency(c.costEur) : null].filter(Boolean).join(' · '),
    missingPrice,
    // Einstieg wie aus der Monatsrückblick-Mail: LogsView öffnet die Ladungen ohne Preis.
    to: missingPrice && props.carId ? `/logs?car=${encodeURIComponent(props.carId)}&nachtragen=preis` : '/logs',
  }
})
const trip = computed(() => {
  const tr = props.latest.trip
  if (!tr) return null
  return {
    title: tr.km != null ? t('story.latest_drove', { km: formatDistance(tr.km, { round: true }) }) : t('story.latest_drove_plain'),
    sub: when(tr.at),
  }
})

const card = 'flex min-h-[64px] items-center gap-3 rounded-2xl bg-white px-4 py-3 edge-card transition-transform active:translate-x-0.5 active:translate-y-0.5 active:shadow-none focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-green-600 dark:bg-gray-800'
</script>

<template>
  <div class="grid gap-2.5 md:grid-cols-2 md:gap-4" data-testid="story-latest">
    <router-link v-if="charge" :to="charge.to" :class="card" data-testid="story-latest-charge">
      <BoltIcon class="h-6 w-6 shrink-0 text-story-accent dark:text-story-accent-dark" aria-hidden="true" />
      <span class="min-w-0 flex-1">
        <span class="block font-bold text-gray-900 tabular-nums dark:text-gray-50">{{ charge.title }}</span>
        <span class="block text-[13px] text-gray-500 tabular-nums dark:text-gray-400">{{ charge.sub }}</span>
      </span>
      <span v-if="charge.missingPrice" class="shrink-0 rounded-full bg-gray-900 px-3 py-1.5 text-[13px] font-semibold text-white dark:bg-gray-100 dark:text-gray-900">{{ t('story.latest_add_price') }}</span>
      <ChevronRightIcon v-else class="h-5 w-5 shrink-0 text-gray-400" aria-hidden="true" />
    </router-link>
    <router-link v-if="trip" to="/logs" :class="card" data-testid="story-latest-trip">
      <MapIcon class="h-6 w-6 shrink-0 text-story-accent dark:text-story-accent-dark" aria-hidden="true" />
      <span class="min-w-0 flex-1">
        <span class="block font-bold text-gray-900 tabular-nums dark:text-gray-50">{{ trip.title }}</span>
        <span class="block text-[13px] text-gray-500 tabular-nums dark:text-gray-400">{{ trip.sub }}</span>
      </span>
      <ChevronRightIcon class="h-5 w-5 shrink-0 text-gray-400" aria-hidden="true" />
    </router-link>
  </div>
</template>
