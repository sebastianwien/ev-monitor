<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'
import { ChevronRightIcon } from '@heroicons/vue/24/outline'
import MonthBars from '../MonthBars.vue'
import ChoiceChip from '../../shared/ChoiceChip.vue'
import { useLocaleFormat } from '../../../composables/useLocaleFormat'
import { costStack, type MonthCosts } from '../../../composables/storyEnergy'
import { analytics } from '../../../services/analytics'
import type { StoryTile } from '../../../composables/storyModel'
import type { StatisticsData } from '../../../composables/useDashboardStats'

/**
 * Kosten je Monat. Standard sind die Stromkosten (wie Kachel und Hero). Wer Fixkosten erfasst hat,
 * schaltet in Monaten mit Fixkosten auf "Mit Fixkosten": Strom unten, Fixkosten grau darauf, die Summe darüber. Die Wahl
 * merkt sich das Gerät. Die Fixkosten je Monat kommen erst beim Umschalten.
 */
const props = defineProps<{
  tile: StoryTile
  /** Gewählter Monat, Basis des Satzes unter den Balken. */
  stats: StatisticsData | null
  monthLabels: string[]
  monthName: string
  runningMonth: boolean
  /** Fixkosten oder Einnahmen irgendwo in den sechs Monaten: die Zeile unten führt dann zu allen Kosten. */
  hasFixedCosts: boolean
  loadMonthlyCosts: () => Promise<MonthCosts[] | null>
}>()

const STORAGE_KEY = 'story_cost_fixed'
function readChoice(): boolean {
  try { return localStorage.getItem(STORAGE_KEY) === 'true' } catch { return false }
}

const { t } = useI18n()
const { formatCurrency } = useLocaleFormat()
const eur = (v: number) => formatCurrency(v, { decimals: 0 })

/** Umschalten nur, wenn der gewählte Monat Fixkosten oder Einnahmen hat; sonst wären beide Ansichten gleich. */
const monthHasFixed = computed(() => (props.stats?.fixedCostEur ?? 0) > 0 || (props.stats?.fixedIncomeEur ?? 0) > 0)
const withFixed = ref(readChoice())
const showFixed = computed(() => monthHasFixed.value && withFixed.value)
const months = ref<MonthCosts[] | null>(null)

function choose(fixed: boolean) {
  if (withFixed.value === fixed) return
  withFixed.value = fixed
  try { localStorage.setItem(STORAGE_KEY, String(fixed)) } catch { /* nur Komfort */ }
  if (fixed) analytics.track('story_cost_fixed_on')
}

watch(showFixed, async (on) => {
  if (!on || months.value) return
  months.value = await props.loadMonthlyCosts()
}, { immediate: true })

const stack = computed(() => showFixed.value && months.value ? costStack(months.value) : null)

const split = computed(() => {
  const s = props.stats
  if (!showFixed.value || !s) return null
  return {
    line: t('story.sheet_cost_split', { month: props.monthName, energy: eur(s.energyCostEur), fixed: eur(s.fixedCostEur) }),
    income: s.fixedIncomeEur > 0 ? t('story.sheet_cost_income', { income: eur(s.fixedIncomeEur) }) : null,
  }
})

const partialLabel = computed(() => props.runningMonth ? t('story.sheet_partial', { month: props.monthLabels[props.monthLabels.length - 1] ?? '' }) : '')
</script>

<template>
  <div>
    <div v-if="monthHasFixed" class="mt-3 flex gap-2" role="group" :aria-label="t('story.sheet_cost_title')">
      <ChoiceChip size="sm" :active="!withFixed" @click="choose(false)">{{ t('story.sheet_cost_mode_energy') }}</ChoiceChip>
      <ChoiceChip size="sm" :active="withFixed" @click="choose(true)">{{ t('story.sheet_cost_mode_fixed') }}</ChoiceChip>
    </div>
    <MonthBars
      v-if="stack"
      class="mt-4"
      :values="stack.energy" :stack="stack.fixed" :labels="monthLabels" :format="eur"
      :stack-labels="{ base: t('story.layers_energy'), stack: t('story.layers_fixed') }"
      :partial-label="partialLabel" tone="good" />
    <MonthBars
      v-else
      class="mt-4"
      :values="tile.spark" :labels="monthLabels" :format="eur"
      :partial-label="partialLabel" :tone="tile.sentiment" />
    <template v-if="split">
      <p data-testid="story-cost-split" class="mt-4 text-sm text-gray-600 tabular-nums text-pretty dark:text-gray-300">{{ split.line }}</p>
      <p v-if="split.income" data-testid="story-cost-income" class="mt-1 text-sm text-gray-600 tabular-nums text-pretty dark:text-gray-300">{{ split.income }}</p>
    </template>
    <RouterLink
      to="/cars"
      data-testid="story-cost-cars"
      class="mt-4 flex min-h-11 items-center justify-between gap-3 border-t border-gray-200 pt-1 text-[15px] font-semibold text-gray-900 dark:border-gray-700 dark:text-gray-50">
      {{ hasFixedCosts ? t('story.sheet_cost_all') : t('story.sheet_cost_add_fixed') }}
      <ChevronRightIcon class="h-5 w-5 shrink-0 text-gray-500 dark:text-gray-400" aria-hidden="true" />
    </RouterLink>
  </div>
</template>
