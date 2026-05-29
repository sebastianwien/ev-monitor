<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useSmartInsights, type InsightSentiment, type ChartBar } from '../../composables/useSmartInsights'
import type { StatisticsData } from '../../composables/useDashboardStats'

const props = defineProps<{
  stats: StatisticsData | null
  lastMonthStats: StatisticsData | null
}>()

const { t } = useI18n()

const { insights } = useSmartInsights(
  () => props.stats,
  () => props.lastMonthStats,
)

const visible = computed(() => insights.value.length > 0)

function borderClass(s: InsightSentiment): string {
  return s === 'positive'
    ? 'border-green-300 dark:border-green-700/60'
    : s === 'warning'
    ? 'border-amber-300 dark:border-amber-600/60'
    : 'border-blue-200 dark:border-blue-700/60'
}

function accentClass(s: InsightSentiment): string {
  return s === 'positive' ? 'bg-green-500' : s === 'warning' ? 'bg-amber-400' : 'bg-blue-400'
}

function deltaClass(s: InsightSentiment): string {
  return s === 'positive' ? 'text-green-500' : s === 'warning' ? 'text-amber-400' : 'text-blue-400'
}

function barWidthStyle(bars: ChartBar[], bar: ChartBar): string {
  const max = Math.max(...bars.map(b => b.projectedValue ?? b.value))
  if (max === 0) return '4px'
  const effective = bar.projectedValue ?? bar.value
  return `${Math.max((effective / max) * 100, 4)}%`
}

function solidColor(s: InsightSentiment): string {
  return s === 'positive' ? '#22c55e' : s === 'warning' ? '#fbbf24' : '#60a5fa'
}

function solidPortionWidth(bar: ChartBar): string {
  if (!bar.projectedValue || bar.projectedValue === 0) return '100%'
  return `${Math.min((bar.value / bar.projectedValue) * 100, 100)}%`
}

function stripePartStyle(sentiment: InsightSentiment): Record<string, string> {
  const c = solidColor(sentiment)
  return { background: `repeating-linear-gradient(45deg, ${c} 0px, ${c} 1.5px, transparent 1.5px, transparent 4px)`, opacity: '0.6' }
}

function barStyle(bar: ChartBar, sentiment: InsightSentiment): Record<string, string> {
  if (bar.style === 'dashed') {
    const c = solidColor(sentiment)
    return { background: `repeating-linear-gradient(90deg, ${c} 0px, ${c} 5px, transparent 5px, transparent 10px)` }
  }
  return {}
}

function barClass(bar: ChartBar, sentiment: InsightSentiment): string {
  if (bar.style === 'dashed') return 'opacity-70'
  if (bar.muted) return 'bg-gray-300 dark:bg-gray-600'
  return sentiment === 'positive' ? 'bg-green-500' : sentiment === 'warning' ? 'bg-amber-400' : 'bg-blue-400'
}
</script>

<template>
  <div v-if="visible">
    <div class="mb-2 px-4 md:px-0 flex items-center gap-3">
      <div class="flex-1 h-px bg-gray-200 dark:bg-gray-700" />
      <h2 class="text-sm font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wide">
        {{ t('insights.page_title') }}
      </h2>
      <div class="flex-1 h-px bg-gray-200 dark:bg-gray-700" />
    </div>

    <!-- Mobile: horizontal scroll | Desktop: grid -->
    <div class="flex gap-3 overflow-x-auto pb-1 -mx-4 px-4 [&::-webkit-scrollbar]:hidden [scrollbar-width:none] md:grid md:grid-cols-3 md:overflow-visible md:mx-0 md:px-0 md:gap-4">
      <div
        v-for="insight in insights"
        :key="insight.id"
        :class="['flex-shrink-0 w-[85vw] min-w-[260px] md:w-auto md:min-w-0 bg-white dark:bg-gray-800 border-2 rounded-sm overflow-hidden flex flex-col', borderClass(insight.sentiment)]"
      >
        <!-- Top: accent bar + headline + delta, body full-width below -->
        <div class="px-3 pt-3 pb-2.5">
          <div class="flex items-start gap-2.5">
            <div :class="['w-1 self-stretch rounded-full flex-shrink-0', accentClass(insight.sentiment)]" />
            <p class="flex-1 min-w-0 text-sm font-semibold text-gray-800 dark:text-gray-200 leading-snug">{{ insight.headline }}</p>
            <div v-if="insight.delta" class="flex-shrink-0 text-right tabular-nums ml-1">
              <div :class="['text-sm font-bold leading-snug', deltaClass(insight.sentiment)]">{{ insight.delta }}</div>
              <div v-if="insight.deltaSecondary" :class="['text-xs leading-snug', deltaClass(insight.sentiment)]">{{ insight.deltaSecondary }}</div>
              <div v-if="insight.deltaTertiary" class="text-xs leading-snug text-gray-400 dark:text-gray-500">{{ insight.deltaTertiary }}</div>
            </div>
          </div>
          <p class="text-sm text-gray-600 dark:text-gray-300 mt-1 leading-snug pl-3.5">{{ insight.body }}</p>
        </div>

        <!-- Horizontal bar chart -->
        <div v-if="insight.chartBars?.length" class="px-3 pb-3 border-t border-gray-100 dark:border-gray-700/50 pt-2.5 space-y-1.5">
          <div
            v-for="(bar, i) in insight.chartBars"
            :key="i"
            class="flex items-center gap-2"
          >
            <span class="text-xs font-medium text-gray-400 dark:text-gray-500 w-8 flex-shrink-0 text-right">{{ bar.label }}</span>
            <div class="flex-1 h-2 bg-gray-100 dark:bg-gray-700/60 rounded-full overflow-hidden">
              <!-- Split bar: solid (actual) + striped (projection) -->
              <div
                v-if="bar.projectedValue"
                :style="{ width: barWidthStyle(insight.chartBars!, bar) }"
                class="h-full flex rounded-full overflow-hidden"
              >
                <div :style="{ width: solidPortionWidth(bar) }" :class="['h-full', barClass(bar, insight.sentiment)]" />
                <div :style="stripePartStyle(insight.sentiment)" class="h-full flex-1" />
              </div>
              <!-- Regular bar -->
              <div
                v-else
                :style="{ width: barWidthStyle(insight.chartBars!, bar), ...barStyle(bar, insight.sentiment) }"
                :class="['h-full rounded-full transition-all duration-500', barClass(bar, insight.sentiment)]"
              />
            </div>
            <span class="text-xs tabular-nums text-gray-400 dark:text-gray-500 w-16 flex-shrink-0 text-right">{{ bar.formattedValue }}</span>
          </div>
        </div>
      </div>
    </div>
    <div class="mt-2 h-px bg-gray-200 dark:bg-gray-700 mx-4 md:mx-0" />
  </div>
</template>
