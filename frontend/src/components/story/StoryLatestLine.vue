<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import { relativeTimeParts } from '../../utils/recentActivity'
import type { StoryLatest } from '../../composables/storyInsights'

/**
 * "Zuletzt"-Zeile unter der Tagesleiste: letzte Ladung und letzte Fahrt des Autos als ein Satz.
 * Beantwortet "ist meine Ladung von gestern angekommen", nicht "was hat sich bewegt" - deshalb
 * hier als Meta-Zeile und nicht im Feed. Jeder Teil springt in die Liste, die den Eintrag oben zeigt.
 */
const props = defineProps<{ latest: StoryLatest; now?: number }>()
const { t, locale } = useI18n()
const { formatCurrency, formatDistance, formatDecimal } = useLocaleFormat()

function when(iso: string): string {
  const parts = relativeTimeParts(Date.parse(iso), props.now ?? Date.now())
  if (!parts) return ''
  const rtf = new Intl.RelativeTimeFormat(locale.value, { numeric: 'auto' })
  if (parts.unit === 'second' || parts.unit === 'minute') return t('story.latest_just_now')
  return rtf.format(parts.value, parts.unit)
}

const charge = computed(() => {
  const c = props.latest.charge
  if (!c) return ''
  const kwh = c.kwh != null ? `${formatDecimal(c.kwh, 1)} kWh` : ''
  const base = t('story.latest_charge', { when: when(c.at), kwh }).replace(/\s+,/g, ',').trim()
  return c.costEur != null ? t('story.latest_charge_cost', { base, cost: formatCurrency(c.costEur) }) : base
})
const trip = computed(() => {
  const tr = props.latest.trip
  if (!tr) return ''
  return t('story.latest_trip', { when: when(tr.at), km: tr.km != null ? formatDistance(tr.km, { round: true }) : '' }).replace(/,\s*$/, '')
})
</script>

<template>
  <p class="mt-2 text-[11px] text-gray-500 dark:text-gray-400 tabular-nums text-pretty" data-testid="story-latest">
    <span class="font-semibold">{{ t('story.latest_prefix') }}</span>
    <router-link v-if="charge" to="/logs" class="underline underline-offset-2 decoration-gray-300 dark:decoration-gray-600" data-testid="story-latest-charge">{{ charge }}</router-link>
    <span v-if="charge && trip" aria-hidden="true"> · </span>
    <router-link v-if="trip" to="/logs" class="underline underline-offset-2 decoration-gray-300 dark:decoration-gray-600" data-testid="story-latest-trip">{{ trip }}</router-link>
  </p>
</template>
