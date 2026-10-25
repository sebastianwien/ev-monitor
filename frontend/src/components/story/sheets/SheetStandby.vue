<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useLocaleFormat } from '../../../composables/useLocaleFormat'
import type { StandbyGap } from '../../../composables/storyInsights'

/**
 * Die größten Verluste im Stand (Abschnitt im Strom-Sheet): wann, wie lange, wie viele kWh und Euro, als liegende Balken in Amber wie der Verlust
 * im Strom-Balken. Euro zum Preis der letzten Ladung davor (sonst Monatsschnitt), deshalb mit Tilde.
 */
const props = defineProps<{ gaps: StandbyGap[] }>()
const { t, locale } = useI18n()
const { formatDecimal, formatCurrency } = useLocaleFormat()

const max = computed(() => Math.max(0, ...props.gaps.map(g => g.kwh)))
const when = (g: StandbyGap) => {
  const f = (d: string) => new Date(d).toLocaleDateString(locale.value, { day: 'numeric', month: 'numeric' })
  return f(g.from) === f(g.to) ? f(g.from) : `${f(g.from)} - ${f(g.to)}`
}
const hours = (g: StandbyGap) => Math.max(1, Math.round((new Date(g.to).getTime() - new Date(g.from).getTime()) / 3_600_000))
const duration = (g: StandbyGap) => hours(g) >= 24 ? t('story.dur_days_n', { n: Math.round(hours(g) / 24) }, Math.round(hours(g) / 24)) : t('story.dur_hours', { n: hours(g) }, hours(g))
</script>

<template>
  <div data-testid="sheet-standby">
    <ul class="mt-3 grid gap-3">
      <li v-for="(g, i) in gaps" :key="i" class="grid gap-1">
        <div class="flex justify-between gap-3 text-sm tabular-nums">
          <span class="min-w-0 text-gray-600 dark:text-gray-300">{{ when(g) }} · {{ duration(g) }}</span>
          <b class="shrink-0 whitespace-nowrap" :class="i === 0 ? 'text-amber-700 dark:text-amber-400' : 'text-gray-900 dark:text-gray-100'">{{ formatDecimal(g.kwh, 1) }} kWh<template v-if="g.eur != null"> · ~{{ formatCurrency(g.eur) }}</template></b>
        </div>
        <span class="block h-2.5 rounded-[3px] bg-gray-100 dark:bg-gray-800"><i class="block h-full rounded-[3px]" :class="i === 0 ? 'bg-amber-500 dark:bg-amber-400' : 'bg-amber-200 dark:bg-amber-400/40'" :style="{ width: `${(g.kwh / max) * 100}%` }" /></span>
      </li>
    </ul>
  </div>
</template>
