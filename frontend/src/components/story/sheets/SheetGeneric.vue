<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import Spark from '../Spark.vue'
import { useLocaleFormat } from '../../../composables/useLocaleFormat'
import type { StoryTile } from '../../../composables/storyModel'
import type { StatisticsData } from '../../../composables/useDashboardStats'

/** Kosten: groesseres Spark-Chart. Die Kosten-Balken sind Stromkosten in Nutzerwaehrung; Fixkosten des Monats stehen immer als Satz darunter, auch wenn es keine gibt. */
const props = defineProps<{
  tile: StoryTile
  stats: StatisticsData | null
  monthLabels: string[]
}>()

const { t } = useI18n()
const { formatCurrency } = useLocaleFormat()

const fixedText = computed(() => {
  const s = props.stats
  if (props.tile.id !== 'cost' || !s) return ''
  if (s.fixedCostEur <= 0 && s.fixedIncomeEur <= 0) return t('story.sheet_cost_fixed_none')
  const p = { fixed: formatCurrency(s.fixedCostEur, { decimals: 0 }), income: formatCurrency(s.fixedIncomeEur, { decimals: 0 }), total: formatCurrency(s.totalCostEur, { decimals: 0 }) }
  return t(s.fixedIncomeEur > 0 ? 'story.sheet_cost_fixed_income' : 'story.sheet_cost_fixed', p)
})

const labels = computed(() => props.tile.sparkVariant === 'line' ? props.monthLabels : [])
const variant = computed(() => props.tile.sparkVariant === 'line' ? 'bars' : props.tile.sparkVariant)
</script>

<template>
  <div>
    <div class="mt-4 h-28">
      <Spark :values="tile.spark" :variant="variant" :tone="tile.sentiment" class="!h-28" />
    </div>
    <div v-if="labels.length" class="flex justify-between text-[11px] text-gray-500 dark:text-gray-400 mt-1 tabular-nums">
      <span v-for="(l, i) in labels" :key="i">{{ l }}</span>
    </div>
    <div v-if="tile.sparkVariant === 'line'" class="flex justify-between text-[11px] text-gray-700 dark:text-gray-200 mt-0.5 tabular-nums">
      <span v-for="(v, i) in tile.spark" :key="i">{{ formatCurrency(v, { decimals: 0 }) }}</span>
    </div>
    <p v-if="fixedText" data-testid="story-cost-fixed" class="text-sm text-gray-600 dark:text-gray-300 mt-3 text-pretty">{{ fixedText }}</p>
  </div>
</template>
