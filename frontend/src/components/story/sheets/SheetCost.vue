<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'
import { ChevronRightIcon, HomeIcon } from '@heroicons/vue/24/outline'
import MonthBars from '../MonthBars.vue'
import StoryUpsellRow from '../StoryUpsellRow.vue'
import ChoiceChip from '../../shared/ChoiceChip.vue'
import Collapse from '../../shared/Collapse.vue'
import { useLocaleFormat } from '../../../composables/useLocaleFormat'
import { costStack, type MonthCosts } from '../../../composables/storyEnergy'
import { analytics } from '../../../services/analytics'
import type { StoryTile } from '../../../composables/storyModel'
import type { StatisticsData } from '../../../composables/useDashboardStats'

/**
 * Kosten je Monat. Standard sind die Stromkosten (wie Kachel und Hero). Wer Fixkosten erfasst hat,
 * schaltet in Monaten mit Fixkosten auf "Mit Fixkosten": Strom unten, Fixkosten grau darauf, die Summe darüber. Die Wahl
 * merkt sich das Gerät. Beide Ansichten sind dieselbe Grafik: die Fixkosten wachsen auf die
 * Strombalken, Satz und Legende klappen auf. Die Fixkosten je Monat kommen erst beim Umschalten.
 */
const props = withDefaults(defineProps<{
  tile: StoryTile
  /** Gewählter Monat, Basis des Satzes unter den Balken. */
  stats: StatisticsData | null
  monthLabels: string[]
  monthName: string
  runningMonth: boolean
  /** Fixkosten oder Einnahmen irgendwo in den sechs Monaten: die Zeile unten führt dann zu allen Kosten. */
  hasFixedCosts: boolean
  loadMonthlyCosts: () => Promise<MonthCosts[] | null>
  /** Heimlade-Ersparnis: gesperrt nach dem Probemonat, sonst das Ende des Probemonats (null ohne). */
  savings?: { locked: boolean; trialEndsAt: string | null; eur: number | null }
}>(), { savings: () => ({ locked: false, trialEndsAt: null, eur: null }) })

const STORAGE_KEY = 'story_cost_fixed'
function readChoice(): boolean {
  try { return localStorage.getItem(STORAGE_KEY) === 'true' } catch { return false }
}

const { t, locale } = useI18n()
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

/** Fixkosten sichtbar, sobald sie geladen sind; bis dahin bleibt die Strom-Ansicht stehen. */
const stacked = computed(() => showFixed.value && months.value != null)
/** Graues Segment je Balken, in der Strom-Ansicht 0: so gleitet die Grafik zwischen beiden. */
const fixedStack = computed(() => {
  const fixed = stacked.value ? costStack(months.value!).fixed : []
  return props.tile.spark.map((_, i) => fixed[i] ?? 0)
})

const split = computed(() => {
  const s = props.stats
  if (!monthHasFixed.value || !s) return null
  return {
    line: t('story.sheet_cost_split', { month: props.monthName, energy: eur(s.energyCostEur), fixed: eur(s.fixedCostEur) }),
    income: s.fixedIncomeEur > 0 ? t('story.sheet_cost_income', { income: eur(s.fixedIncomeEur) }) : null,
  }
})

const trialUntil = computed(() => {
  const end = props.savings.trialEndsAt
  return end ? new Date(`${end.slice(0, 10)}T00:00:00`).toLocaleDateString(locale.value, { day: 'numeric', month: 'numeric' }) : null
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
      class="mt-4"
      :values="tile.spark" :stack="fixedStack" :labels="monthLabels" :format="eur"
      :stack-labels="{ base: t('story.layers_energy'), stack: t('story.layers_fixed') }"
      :partial-label="partialLabel" :tone="tile.sentiment" />
    <Collapse v-if="split" :open="stacked">
      <p data-testid="story-cost-split" class="mt-4 text-sm text-gray-600 tabular-nums text-pretty dark:text-gray-300">{{ split.line }}</p>
      <p v-if="split.income" data-testid="story-cost-income" class="mt-1 text-sm text-gray-600 tabular-nums text-pretty dark:text-gray-300">{{ split.income }}</p>
    </Collapse>
    <StoryUpsellRow v-if="savings.locked" data-testid="story-upsell-cost" class="mt-4"
      :title="t('story.upsell_cost_title')" :text="t('story.upsell_cost_text')" from="story_cost" />
    <!-- Freigeschaltet (auch im Probemonat): die Ersparnis selbst, mit Gratis-Test-Plakette -->
    <div v-else-if="savings.eur != null || trialUntil" data-testid="story-savings-cost" class="mt-4 flex items-start gap-3 border-t border-gray-200 pt-3 dark:border-gray-700">
      <HomeIcon class="mt-0.5 h-5 w-5 shrink-0 text-green-700 dark:text-green-400" aria-hidden="true" />
      <div class="min-w-0 flex-1">
        <div class="text-sm font-semibold text-gray-900 dark:text-gray-100">
          {{ t('story.upsell_cost_title') }}
          <span v-if="trialUntil" data-testid="story-trial-cost"
            class="ml-1.5 whitespace-nowrap rounded-full bg-green-50 px-2 py-0.5 align-[1px] text-[11.5px] font-semibold text-green-700 dark:bg-green-950/40 dark:text-green-400">{{ t('story.trial_until', { date: trialUntil }) }}</span>
        </div>
        <div class="text-[13px] text-gray-500 dark:text-gray-400">{{ t('story.savings_sub') }}</div>
      </div>
      <div v-if="savings.eur != null" class="text-right text-sm font-semibold tabular-nums text-green-700 dark:text-green-400">{{ eur(savings.eur) }}</div>
    </div>
    <RouterLink
      to="/cars"
      data-testid="story-cost-cars"
      class="mt-4 flex min-h-11 items-center justify-between gap-3 border-t border-gray-200 pt-1 text-[15px] font-semibold text-gray-900 dark:border-gray-700 dark:text-gray-50">
      {{ hasFixedCosts ? t('story.sheet_cost_all') : t('story.sheet_cost_add_fixed') }}
      <ChevronRightIcon class="h-5 w-5 shrink-0 text-gray-500 dark:text-gray-400" aria-hidden="true" />
    </RouterLink>
  </div>
</template>
