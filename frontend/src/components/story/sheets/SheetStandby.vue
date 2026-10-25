<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useLocaleFormat } from '../../../composables/useLocaleFormat'
import type { StandbyGap } from '../../../composables/storyInsights'

/** Die größten Verluste im Stand: wann, wie lange, wie viele kWh, als liegende Balken. */
const props = defineProps<{ gaps: StandbyGap[]; totalKwh: number }>()
const { t, locale } = useI18n()
const { formatDecimal } = useLocaleFormat()

const max = computed(() => Math.max(0, ...props.gaps.map(g => g.kwh)))
const when = (g: StandbyGap) => {
  const f = (d: string) => new Date(d).toLocaleDateString(locale.value, { weekday: 'short', day: 'numeric', month: 'numeric' })
  return f(g.from) === f(g.to) ? f(g.from) : `${f(g.from)} - ${f(g.to)}`
}
const hours = (g: StandbyGap) => Math.max(1, Math.round((new Date(g.to).getTime() - new Date(g.from).getTime()) / 3_600_000))
const duration = (g: StandbyGap) => hours(g) >= 24 ? t('story.dur_days', { n: Math.round(hours(g) / 24) }, Math.round(hours(g) / 24)) : t('story.dur_hours', { n: hours(g) }, hours(g))
</script>

<template>
  <div data-testid="sheet-standby">
    <p class="mt-1 text-sm text-gray-600 dark:text-gray-300">{{ t('story.sheet_standby_text', { kwh: formatDecimal(totalKwh, 1) }) }}</p>
    <ul class="mt-4 grid gap-3">
      <li v-for="(g, i) in gaps" :key="i" class="grid gap-1">
        <div class="flex justify-between gap-3 text-sm tabular-nums">
          <span class="text-gray-600 dark:text-gray-300">{{ when(g) }} · {{ duration(g) }}</span>
          <b class="text-gray-900 dark:text-gray-100">{{ formatDecimal(g.kwh, 1) }} kWh</b>
        </div>
        <span class="block h-2.5 rounded-[3px] bg-gray-100 dark:bg-gray-800"><i class="block h-full rounded-[3px]" :class="i === 0 ? 'bg-gray-900 dark:bg-gray-100' : 'bg-gray-400 dark:bg-gray-500'" :style="{ width: `${(g.kwh / max) * 100}%` }" /></span>
      </li>
    </ul>
  </div>
</template>
