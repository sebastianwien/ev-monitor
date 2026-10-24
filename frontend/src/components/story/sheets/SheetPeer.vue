<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import Spark from '../Spark.vue'
import { useLocaleFormat } from '../../../composables/useLocaleFormat'
import type { StoryTile } from '../../../composables/storyModel'
import type { StatisticsData } from '../../../composables/useDashboardStats'
import type { WhatIfModel } from '../../../composables/storyInsights'

/** Peer-Vergleich plus "Was waere wenn": Euro je 100 km anderer Modelle, Lifetime-Daten. */
const props = defineProps<{
  tile: StoryTile
  stats: StatisticsData | null
  whatIf: WhatIfModel[]
}>()

const { t } = useI18n()
const { formatConsumption, formatCostPerKwh, formatCostPerDistance, formatDecimal } = useLocaleFormat()

const text = computed(() => {
  const pb = props.stats?.peerBenchmark
  return t('story.sheet_peer_text', {
    peerAvg: formatConsumption(pb?.peerAvgConsumptionKwhPer100km ?? 0),
    mine: formatConsumption(props.tile.value),
    peerPrice: pb?.peerAvgCostPerKwh != null ? formatCostPerKwh(pb.peerAvgCostPerKwh) : '–',
    myPrice: pb?.userPeriodCostPerKwh != null ? formatCostPerKwh(pb.userPeriodCostPerKwh) : '–',
  })
})

const maxCost = computed(() => Math.max(...props.whatIf.map(m => m.costPer100km), 0.01))
const shown = computed(() => props.whatIf.slice(0, 8))
</script>

<template>
  <div>
    <p class="text-sm text-gray-600 dark:text-gray-300 mt-1">{{ text }}</p>
    <div class="mt-4 h-20">
      <Spark :values="tile.spark" variant="range" :tone="tile.sentiment" class="!h-20" />
    </div>

    <template v-if="shown.length > 1">
      <h4 class="mt-5 font-extrabold text-gray-900 dark:text-gray-50">{{ t('story.sheet_peer_whatif_title') }}</h4>
      <p class="text-xs text-gray-500 dark:text-gray-400">{{ t('story.sheet_peer_whatif_sub') }}</p>
      <ul class="mt-2 space-y-2" data-testid="story-whatif">
        <li v-for="(m, i) in shown" :key="m.name" class="story-whatif-row" :style="{ '--i': i }">
          <div class="flex items-baseline justify-between gap-3 text-sm">
            <span class="truncate" :class="m.isUser ? 'font-extrabold text-gray-900 dark:text-gray-50' : 'text-gray-700 dark:text-gray-300'">
              {{ m.name }}<span v-if="m.isUser" class="ml-1.5 text-[11px] font-semibold text-story-accent dark:text-story-accent-dark uppercase">{{ t('story.sheet_peer_you') }}</span>
            </span>
            <span class="tabular-nums shrink-0" :class="m.isUser ? 'font-extrabold text-gray-900 dark:text-gray-50' : 'text-gray-700 dark:text-gray-300'">
              {{ formatCostPerDistance(m.costPer100km) }}
              <span v-if="!m.isUser" class="text-xs ml-1" :class="m.deltaPercent < 0 ? 'text-green-700 dark:text-green-300' : 'text-gray-400'">
                {{ m.deltaPercent > 0 ? '+' : '' }}{{ formatDecimal(m.deltaPercent, 0) }} %
              </span>
            </span>
          </div>
          <div class="h-1.5 mt-1 rounded-full bg-gray-100 dark:bg-gray-700 overflow-hidden">
            <div class="h-full rounded-full story-whatif-bar" :class="m.isUser ? 'bg-story-accent dark:bg-story-accent-dark' : 'bg-gray-400 dark:bg-gray-500'" :style="{ width: `${(m.costPer100km / maxCost) * 100}%` }" />
          </div>
          <p class="text-[10px] text-gray-400 mt-0.5">{{ t('story.sheet_peer_cars', { n: m.cars }) }}</p>
        </li>
      </ul>
    </template>
  </div>
</template>

<style scoped>
.story-whatif-row {
  opacity: 1;
  transform: translateY(0);
  transition: opacity 300ms ease, transform 300ms cubic-bezier(0.2, 0.7, 0.2, 1);
  transition-delay: calc(var(--i) * 50ms);
}
@starting-style {
  .story-whatif-row { opacity: 0; transform: translateY(6px); }
}
.story-whatif-bar { transition: width 600ms cubic-bezier(0.2, 0.7, 0.2, 1); }
@media (prefers-reduced-motion: reduce) {
  .story-whatif-row, .story-whatif-bar { transition: none; }
}
</style>
