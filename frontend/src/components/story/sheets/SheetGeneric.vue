<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import Spark from '../Spark.vue'
import MonthBars from '../MonthBars.vue'
import { useLocaleFormat } from '../../../composables/useLocaleFormat'
import type { StoryTile } from '../../../composables/storyModel'
import type { StatisticsData } from '../../../composables/useDashboardStats'

/** Kosten: Monatsbalken mit Betraegen (MonthBars), sonst grosses Spark-Chart. Die Kosten-Balken sind Stromkosten in Nutzerwaehrung; Fixkosten des Monats stehen immer als Satz darunter, auch wenn es keine gibt. */
const props = defineProps<{
  tile: StoryTile
  stats: StatisticsData | null
  monthLabels: string[]
  runningMonth: boolean
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

const partialLabel = computed(() => props.runningMonth ? t('story.sheet_partial', { month: props.monthLabels[props.monthLabels.length - 1] ?? '' }) : '')
</script>

<template>
  <div>
    <MonthBars
      v-if="tile.sparkVariant === 'line'"
      class="mt-4"
      :values="tile.spark" :labels="monthLabels" :format="(v: number) => formatCurrency(v, { decimals: 0 })"
      :partial-label="partialLabel" :tone="tile.sentiment" />
    <div v-else class="mt-4 h-28">
      <Spark :values="tile.spark" :variant="tile.sparkVariant" :tone="tile.sentiment" class="h-full [&_svg]:h-full" />
    </div>
    <p v-if="fixedText" data-testid="story-cost-fixed" class="text-sm text-gray-600 dark:text-gray-300 mt-4 text-pretty">{{ fixedText }}</p>
  </div>
</template>
