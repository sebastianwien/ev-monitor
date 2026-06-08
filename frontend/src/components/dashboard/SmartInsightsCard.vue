<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ChevronDownIcon } from '@heroicons/vue/24/outline'
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

// ── Fold state ────────────────────────────────────────────────────────────────
// Key scoped to year-month: each new month all insights reopen automatically.
// activePeriod is checked on every toggle so a tab open across a month boundary
// syncs to the new month on the next user interaction.
function lsKey(period: string) {
  return `ev-insights-collapsed:${period}`
}

function loadForPeriod(period: string): Set<string> {
  try {
    const raw = localStorage.getItem(lsKey(period))
    return raw ? new Set(JSON.parse(raw) as string[]) : new Set()
  } catch {
    return new Set()
  }
}

let activePeriod = new Date().toISOString().slice(0, 7)
const collapsed = ref<Set<string>>(loadForPeriod(activePeriod))

function isOpen(id: string): boolean {
  return !collapsed.value.has(id)
}

function toggle(id: string) {
  // haptic via global pointerdown on <button> in main.ts
  const period = new Date().toISOString().slice(0, 7)
  if (period !== activePeriod) {
    activePeriod = period
    collapsed.value = loadForPeriod(period)
  }
  const next = new Set(collapsed.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  collapsed.value = next
  try { localStorage.setItem(lsKey(period), JSON.stringify([...next])) } catch {}
}

// ── Sentiment helpers ─────────────────────────────────────────────────────────

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

    <!-- Mobile: vertical foldable stack | Desktop: 3-col grid always open -->
    <div class="flex flex-col gap-2 md:grid md:grid-cols-3 md:gap-4">
      <div
        v-for="insight in insights"
        :key="insight.id"
        :class="['bg-white dark:bg-gray-800 border-2 rounded-sm overflow-hidden shadow-[2px_2px_0_0_#d1d5db] dark:shadow-[2px_2px_0_0_#374151]', borderClass(insight.sentiment)]"
      >
        <!-- Header: tap to fold on mobile, static on desktop -->
        <button
          type="button"
          class="w-full px-3 pt-3 pb-2.5 text-left md:cursor-default"
          :aria-expanded="isOpen(insight.id)"
          :aria-controls="`insight-body-${insight.id}`"
          @click="toggle(insight.id)"
        >
          <div class="flex items-start gap-2.5">
            <div :class="['w-1 rounded-full flex-shrink-0 mt-0.5', accentClass(insight.sentiment)]" style="min-height:1.25rem;align-self:stretch" />
            <p :class="['flex-1 min-w-0 text-sm font-semibold text-gray-800 dark:text-gray-200 leading-snug', !isOpen(insight.id) ? 'line-clamp-1 md:line-clamp-none' : '']">{{ insight.headline }}</p>
            <div class="flex items-start gap-1.5 flex-shrink-0 ml-1">
              <div v-if="insight.delta" class="text-right tabular-nums">
                <div :class="['text-sm font-bold leading-snug', deltaClass(insight.sentiment)]">{{ insight.delta }}</div>
                <template v-if="isOpen(insight.id)">
                  <div v-if="insight.deltaSecondary" :class="['text-xs leading-snug', deltaClass(insight.sentiment)]">{{ insight.deltaSecondary }}</div>
                  <div v-if="insight.deltaTertiary" class="text-xs leading-snug text-gray-400 dark:text-gray-500">{{ insight.deltaTertiary }}</div>
                </template>
              </div>
              <!-- Chevron only on mobile -->
              <ChevronDownIcon
                :class="['w-4 h-4 flex-shrink-0 text-gray-400 dark:text-gray-500 transition-transform duration-200 mt-0.5 md:hidden', isOpen(insight.id) ? 'rotate-0' : 'rotate-180']"
              />
            </div>
          </div>
        </button>

        <!-- Collapsible body: grid trick for height animation on mobile, always open on desktop -->
        <div
          :id="`insight-body-${insight.id}`"
          class="grid transition-all duration-200 ease-in-out md:grid-rows-[1fr]"
          :class="isOpen(insight.id) ? 'grid-rows-[1fr]' : 'grid-rows-[0fr]'"
        >
          <div class="overflow-hidden">
            <p class="text-sm text-gray-600 dark:text-gray-300 mt-1 pb-2.5 leading-snug pl-3.5 pr-3">{{ insight.body }}</p>

            <!-- Bar chart -->
            <div v-if="insight.chartBars?.length" class="px-3 pb-3 border-t border-gray-100 dark:border-gray-700/50 pt-2.5 space-y-1.5">
              <div v-for="(bar, i) in insight.chartBars" :key="i" class="flex items-center gap-2">
                <span class="text-xs font-medium text-gray-400 dark:text-gray-500 w-8 flex-shrink-0 text-right">{{ bar.label }}</span>
                <div class="flex-1 h-2 bg-gray-100 dark:bg-gray-700/60 rounded-full overflow-hidden">
                  <div
                    v-if="bar.projectedValue"
                    :style="{ width: barWidthStyle(insight.chartBars!, bar) }"
                    class="h-full flex rounded-full overflow-hidden"
                  >
                    <div :style="{ width: solidPortionWidth(bar) }" :class="['h-full', barClass(bar, insight.sentiment)]" />
                    <div :style="stripePartStyle(insight.sentiment)" class="h-full flex-1" />
                  </div>
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
      </div>
    </div>
    <div class="mt-2 h-px bg-gray-200 dark:bg-gray-700 mx-4 md:mx-0" />
  </div>
</template>
