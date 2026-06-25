<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import {
  BoltIcon,
  BanknotesIcon,
  InformationCircleIcon,
  DocumentCheckIcon,
  ArrowRightIcon,
} from '@heroicons/vue/24/outline'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import type { TopModelPreview } from '../../api/publicModelService'

defineProps<{
  model: TopModelPreview
  /** Fully built, locale-aware detail URL (crawlable <a href>). */
  to: string
}>()

const { t } = useI18n()
const { formatConsumption, formatCostPerKwh, formatCostPerDistance, formatNumber, consumptionUnitLabel } = useLocaleFormat()

// Unit-less consumption: the unit (kWh/100km) is appended once per value as a
// faint suffix, so the numeric ranges stay short and never wrap.
function formatWltpRangeBare(min: number, max: number | null): string {
  if (!max || Math.abs(max - min) < 0.05) return formatConsumption(min, { showUnit: false })
  return `${formatConsumption(min, { showUnit: false })} - ${formatConsumption(max, { showUnit: false })}`
}
function formatRealConsumptionBare(avg: number | null, min: number | null, max: number | null): string {
  if (min !== null && max !== null) return formatWltpRangeBare(min, max)
  if (avg !== null) return formatConsumption(avg, { showUnit: false })
  return '-'
}
</script>

<template>
  <a
    :href="to"
    class="btn-3d cta-card no-press flex flex-col bg-white dark:bg-gray-800 border-2 border-gray-900 dark:border-gray-100 rounded-lg p-4"
  >
    <!-- Title -->
    <h3 class="mb-3 text-center text-xl font-extrabold leading-tight text-gray-900 dark:text-gray-100">{{ model.modelDisplayName }}</h3>

    <!-- Cost hero -->
    <div
      v-if="model.avgCostPerKwh && model.avgConsumptionKwhPer100km"
      class="mb-3 rounded-lg border-2 border-gray-900 dark:border-gray-100 bg-blue-50 dark:bg-blue-950/40 px-3 py-2 shadow-[3px_3px_0_0_#2563eb]"
    >
      <div class="flex items-center gap-1.5 text-[11px] font-bold uppercase tracking-wide text-gray-500 dark:text-gray-400">
        <BanknotesIcon class="h-3.5 w-3.5 flex-none" />
        {{ t('landing.hero.cost_headline') }}
      </div>
      <div class="mt-0.5 text-2xl font-black leading-none text-blue-600 dark:text-blue-400 sl-num">
        ~{{ formatCostPerDistance(model.avgCostPerKwh * model.avgConsumptionKwhPer100km) }}
      </div>
      <div class="mt-1 flex items-center gap-1 text-xs text-gray-500 dark:text-gray-400">
        <span class="sl-num">{{ t('landing.hero.cost_basis', { price: formatCostPerKwh(model.avgCostPerKwh) }) }}</span>
        <span class="relative group cursor-help inline-flex items-center">
          <InformationCircleIcon class="h-3.5 w-3.5 flex-none" />
          <span class="absolute bottom-full left-0 mb-1.5 px-2.5 py-2 bg-gray-800 text-white text-xs rounded-sm w-60 hidden group-hover:block z-20 pointer-events-none leading-snug shadow-[4px_4px_0_rgba(0,0,0,0.30)] dark:shadow-[4px_4px_0_rgba(255,255,255,0.30)]">
            {{ t('landing.hero.cost_tooltip') }}
          </span>
        </span>
      </div>
    </div>

    <!-- Consumption: real vs factory -->
    <div>
      <div class="text-center text-xs font-bold uppercase tracking-wide text-gray-600 dark:text-gray-300">{{ t('landing.hero.consumption_label') }}</div>
      <div class="mt-1 space-y-0.5 text-sm">
        <div v-if="model.avgConsumptionKwhPer100km || model.minRealConsumptionKwhPer100km" class="flex items-center gap-1.5">
          <BoltIcon class="h-3 w-3 flex-none text-green-600" />
          <span class="text-xs font-semibold text-gray-600 dark:text-gray-400">{{ t('landing.hero.real_short') }}</span>
          <span class="ml-auto whitespace-nowrap font-extrabold text-gray-900 dark:text-gray-100 sl-num">{{ formatRealConsumptionBare(model.avgConsumptionKwhPer100km, model.minRealConsumptionKwhPer100km, model.maxRealConsumptionKwhPer100km) }}<span class="ml-0.5 text-[11px] font-medium text-gray-400">{{ consumptionUnitLabel() }}</span></span>
        </div>
        <div v-if="model.minWltpConsumptionKwhPer100km" class="flex items-center gap-1.5">
          <DocumentCheckIcon class="h-3 w-3 flex-none text-gray-400" />
          <span class="text-xs font-semibold text-gray-500 dark:text-gray-400">{{ t('landing.hero.factory_short') }}</span>
          <span class="ml-auto whitespace-nowrap font-bold text-gray-500 dark:text-gray-400 sl-num">{{ formatWltpRangeBare(model.minWltpConsumptionKwhPer100km, model.maxWltpConsumptionKwhPer100km) }}<span class="ml-0.5 text-[11px] font-medium text-gray-400">{{ consumptionUnitLabel() }}</span></span>
        </div>
      </div>
    </div>

    <!-- Footer: charge count + CTA -->
    <div class="mt-auto flex items-center justify-between gap-2 border-t-2 border-dashed border-gray-200 dark:border-gray-700 pt-2.5">
      <span class="whitespace-nowrap text-xs text-gray-400 sl-num"><span class="font-semibold text-gray-500 dark:text-gray-400">{{ formatNumber(model.logCount) }}</span> {{ t('landing.hero.charging_sessions') }}</span>
      <span class="flex items-center gap-1 whitespace-nowrap text-[13px] font-bold text-green-600 dark:text-green-400">
        {{ t('landing.models_section.view_details') }}
        <ArrowRightIcon class="h-4 w-4 flex-none" />
      </span>
    </div>
  </a>
</template>
