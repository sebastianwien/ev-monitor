<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useLocaleFormat } from '../../composables/useLocaleFormat'

const props = defineProps<{
  energyCostEur: number | null
  /** Fixkosten brutto, ohne Einnahmen. */
  fixedCostEur: number | null
  /** Einnahmen aus Einnahmen-Kategorien, positiv geliefert. */
  fixedIncomeEur: number | null
  totalCostEur: number | null
  /** stacked = Label ueber Wert (Mobile-Kachel), inline = Label links, Wert rechts (Desktop-Karte). */
  layout: 'stacked' | 'inline'
}>()

const { t } = useI18n()
const { formatCurrency } = useLocaleFormat()

const hasFixedCost = computed(() => !!props.fixedCostEur)
const hasIncome = computed(() => !!props.fixedIncomeEur)
/** Die Summenzeile lohnt nur, wenn sie sich von den Energiekosten unterscheidet. */
const showTotal = computed(() => hasFixedCost.value || hasIncome.value)

const isStacked = computed(() => props.layout === 'stacked')
const rowClass = computed(() => isStacked.value ? '' : 'flex items-center justify-between')
const labelClass = computed(() => isStacked.value
  ? 'text-[10px] text-gray-500 dark:text-gray-400'
  : 'text-xs text-gray-500 dark:text-gray-400 min-w-0 truncate')
const valueBase = computed(() => isStacked.value
  ? 'text-sm font-semibold tabular-nums whitespace-nowrap'
  : 'text-sm font-semibold whitespace-nowrap')
const valueClass = computed(() => `${valueBase.value} text-gray-800 dark:text-gray-200`)
const incomeValueClass = computed(() => `${valueBase.value} text-green-600 dark:text-green-400`)
</script>

<template>
  <div>
    <div class="text-xs font-medium text-gray-500 dark:text-gray-400 mb-2">
      {{ t('dashboard.metric_total_cost') }}
    </div>
    <div class="space-y-1">
      <div :class="rowClass">
        <span :class="labelClass">{{ t('fixed_costs.dashboard_energy') }}</span>
        <span :class="valueClass">{{ energyCostEur != null ? formatCurrency(energyCostEur) : '–' }}</span>
      </div>

      <div v-if="hasFixedCost" :class="rowClass">
        <span :class="labelClass">{{ t('fixed_costs.dashboard_fixed') }}</span>
        <span :class="valueClass">{{ formatCurrency(fixedCostEur!) }}</span>
      </div>

      <div v-if="hasIncome" :class="rowClass">
        <span :class="labelClass">{{ t('fixed_costs.dashboard_income') }}</span>
        <span :class="incomeValueClass">{{ formatCurrency(-fixedIncomeEur!) }}</span>
      </div>

      <div v-if="showTotal" :class="[rowClass, 'border-t border-gray-100 dark:border-gray-600 pt-1 mt-1']">
        <span
          class="font-medium text-gray-600 dark:text-gray-300"
          :class="isStacked ? 'text-[10px]' : 'text-xs'"
        >{{ t('fixed_costs.dashboard_total') }}</span>
        <span
          class="text-base font-bold text-gray-900 dark:text-gray-100 whitespace-nowrap"
          :class="isStacked ? 'tabular-nums' : ''"
        >{{ totalCostEur != null ? formatCurrency(totalCostEur) : '–' }}</span>
      </div>
    </div>
  </div>
</template>
