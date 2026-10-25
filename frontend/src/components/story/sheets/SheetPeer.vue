<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import Spark from '../Spark.vue'
import { useLocaleFormat } from '../../../composables/useLocaleFormat'
import { MIN_PEERS, type StoryTile } from '../../../composables/storyModel'
import type { StatisticsData } from '../../../composables/useDashboardStats'
import type { ModelPeers } from '../../../composables/storyInsights'

/** Peer-Vergleich des Monats, darunter du gegen alle Fahrer deines Modells und die Varianten (alle Zeiträume). */
const props = defineProps<{
  tile: StoryTile
  stats: StatisticsData | null
  modelPeers: ModelPeers | null
  /** Modell ohne Variante, z. B. "Model 3". */
  modelName: string
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

const model = computed(() => props.modelPeers && props.modelPeers.cars >= MIN_PEERS ? props.modelPeers : null)
const rows = computed(() => {
  const m = model.value
  if (!m) return []
  return [
    { key: 'price', label: t('story.sheet_peer_row_price'), mine: formatCostPerKwh(m.mine.costPerKwh), theirs: formatCostPerKwh(m.theirs.costPerKwh) },
    { key: 'consumption', label: t('story.sheet_peer_row_consumption'), mine: formatConsumption(m.mine.consumption), theirs: formatConsumption(m.theirs.consumption) },
    { key: 'cost', label: t('story.sheet_peer_row_cost'), mine: formatCostPerDistance(m.mine.costPer100km), theirs: formatCostPerDistance(m.theirs.costPer100km) },
  ]
})

/** Varianten nur ab MIN_PEERS Autos, darunter wäre der Wert ein einzelner Fahrer. */
const variants = computed(() => {
  const m = props.modelPeers
  if (!m) return []
  return m.variants.filter(v => v.cars >= MIN_PEERS).slice(0, 8)
    .map(v => ({ ...v, deltaPercent: ((v.costPer100km - m.mine.costPer100km) / m.mine.costPer100km) * 100 }))
})
const maxCost = computed(() => Math.max(...variants.value.map(v => v.costPer100km), 0.01))
</script>

<template>
  <div>
    <p class="text-sm text-gray-600 dark:text-gray-300 mt-1">{{ text }}</p>
    <div class="mt-4 h-20">
      <Spark :values="tile.spark" variant="range" :tone="tile.sentiment" class="!h-20" />
    </div>

    <template v-if="model">
      <h4 class="mt-5 font-extrabold text-gray-900 dark:text-gray-50">{{ t('story.sheet_peer_model_title', { model: modelName }) }}</h4>
      <p class="text-xs text-gray-500 dark:text-gray-400">{{ t('story.sheet_peer_model_sub', { n: model.cars }) }}</p>
      <table class="mt-2 w-full text-sm tabular-nums" data-testid="story-peer-model">
        <thead>
          <tr class="text-left text-[12px] uppercase tracking-wide text-gray-500 dark:text-gray-400">
            <th class="py-1 font-semibold"><span class="sr-only">{{ t('story.sheet_peer_model_title', { model: modelName }) }}</span></th>
            <th class="py-1 text-right font-semibold">{{ t('story.sheet_peer_you') }}</th>
            <th class="py-1 text-right font-semibold">{{ t('story.sheet_peer_others') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in rows" :key="r.key" class="border-t border-gray-200 dark:border-gray-700">
            <th scope="row" class="py-1.5 text-left font-normal text-gray-700 dark:text-gray-300">{{ r.label }}</th>
            <td class="py-1.5 text-right font-bold text-gray-900 dark:text-gray-50">{{ r.mine }}</td>
            <td class="py-1.5 text-right text-gray-700 dark:text-gray-300">{{ r.theirs }}</td>
          </tr>
        </tbody>
      </table>
    </template>

    <template v-if="variants.length > 1">
      <h4 class="mt-5 font-extrabold text-gray-900 dark:text-gray-50">{{ t('story.sheet_peer_variants_title') }}</h4>
      <p class="text-xs text-gray-500 dark:text-gray-400">{{ t('story.sheet_peer_variants_sub') }}</p>
      <ul class="mt-2 space-y-2" data-testid="story-variants">
        <li v-for="(m, i) in variants" :key="m.name" class="story-whatif-row" :style="{ '--i': i }">
          <div class="flex items-baseline justify-between gap-3 text-sm">
            <span class="truncate" :class="m.isUser ? 'font-extrabold text-gray-900 dark:text-gray-50' : 'text-gray-700 dark:text-gray-300'">
              {{ m.name }}<span v-if="m.isUser" class="ml-1.5 text-[11px] font-semibold text-gray-500 dark:text-gray-400 uppercase">{{ t('story.sheet_peer_your_variant') }}</span>
            </span>
            <span class="tabular-nums shrink-0 text-gray-700 dark:text-gray-300">
              {{ formatCostPerDistance(m.costPer100km) }}
              <span class="text-xs ml-1 text-gray-500 dark:text-gray-400">{{ m.deltaPercent > 0 ? '+' : '' }}{{ formatDecimal(m.deltaPercent, 0) }} %</span>
            </span>
          </div>
          <div class="h-1.5 mt-1 rounded-full bg-gray-100 dark:bg-gray-700 overflow-hidden">
            <div class="h-full rounded-full story-whatif-bar bg-gray-400 dark:bg-gray-500" :style="{ width: `${(m.costPer100km / maxCost) * 100}%` }" />
          </div>
          <p class="text-[10px] text-gray-500 dark:text-gray-400 mt-0.5">{{ t('story.sheet_peer_cars', { n: m.cars }) }}</p>
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
