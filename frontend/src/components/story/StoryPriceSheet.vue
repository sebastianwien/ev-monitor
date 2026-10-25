<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import BottomSheet from '../shared/BottomSheet.vue'
import SheetCloseButton from './SheetCloseButton.vue'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import type { BenchmarkCard, ChargingBenchmark } from '../../api/chargingBenchmarkService'

/**
 * Was andere Fahrer bei einem Betreiber zahlen, zum Moment "zu teuer geladen". Hier stehen
 * die Kartennamen, im Hero nie. Jede Karte nennt ihre Quelle: von Fahrern dort bezahlt oder
 * gepflegter Tarif mit Stand.
 */
const props = defineProps<{
  benchmark: ChargingBenchmark
  /** Was der Nutzer bei der auslösenden Ladung gezahlt hat. */
  pricePerKwh: number | null
}>()
const emit = defineEmits<{ close: [] }>()

const { t, locale } = useI18n()
const { formatCostPerKwh, formatCurrency } = useLocaleFormat()

const title = computed(() => t('story.sheet_prices_title', { cpo: props.benchmark.cpoName }))
const typeLabel = computed(() => t(props.benchmark.chargingType === 'DC' ? 'story.sheet_prices_dc' : 'story.sheet_prices_ac'))

function cardName(c: BenchmarkCard): string {
  return c.variant ? `${c.name} ${c.variant}` : c.name
}
function cardDetail(c: BenchmarkCard): string {
  if (c.source === 'DRIVERS') return t('story.sheet_prices_card_drivers', { n: c.drivers ?? 0 }, c.drivers ?? 0)
  const fee = c.monthlyFeeEur != null && c.monthlyFeeEur > 0
    ? t('story.sheet_prices_card_fee', { fee: formatCurrency(c.monthlyFeeEur) })
    : t('story.sheet_prices_card_no_fee')
  if (!c.verifiedOn) return fee
  const date = new Date(`${c.verifiedOn}T00:00:00`).toLocaleDateString(locale.value, { day: '2-digit', month: '2-digit', year: 'numeric' })
  return `${fee} · ${t('story.sheet_prices_card_verified', { date })}`
}
</script>

<template>
  <BottomSheet :label="title" testid="story-price-sheet" panel-class="sm:max-w-lg" edge @close="emit('close')">
    <template #default="{ close }">
      <div class="px-4 pt-3 pb-4 max-h-[85vh] overflow-y-auto">
        <div class="w-10 h-1 rounded-full bg-gray-300 dark:bg-gray-600 mx-auto mb-3" />
        <h3 class="text-xl font-extrabold text-gray-900 dark:text-gray-50">{{ title }}</h3>

        <p v-if="pricePerKwh != null" class="mt-2 text-base font-semibold text-gray-900 dark:text-gray-50">
          {{ t('story.sheet_prices_you', { price: formatCostPerKwh(pricePerKwh) }) }}
        </p>
        <p class="mt-1 text-sm text-gray-700 dark:text-gray-200">
          {{ t('story.sheet_prices_others', {
            median: formatCostPerKwh(benchmark.medianPricePerKwh),
            low: formatCostPerKwh(benchmark.lowerQuartilePricePerKwh),
            high: formatCostPerKwh(benchmark.upperQuartilePricePerKwh),
          }) }}
        </p>
        <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">
          {{ t('story.sheet_prices_basis', { n: benchmark.drivers, type: typeLabel }) }}
        </p>

        <template v-if="benchmark.cards.length">
          <h4 class="mt-5 text-xs uppercase tracking-wider text-gray-500 dark:text-gray-400">{{ t('story.sheet_prices_cards', { cpo: benchmark.cpoName }) }}</h4>
          <ul class="mt-1 divide-y divide-gray-200 dark:divide-gray-700">
            <li v-for="c in benchmark.cards" :key="`${c.source}:${cardName(c)}`" data-testid="price-card" class="flex items-baseline justify-between gap-3 py-2.5">
              <span class="min-w-0">
                <span class="block font-semibold text-gray-900 dark:text-gray-50">{{ cardName(c) }}</span>
                <span class="block text-xs text-gray-500 dark:text-gray-400">{{ cardDetail(c) }}</span>
              </span>
              <span class="shrink-0 font-semibold tabular-nums text-gray-900 dark:text-gray-50">{{ formatCostPerKwh(c.pricePerKwh) }}</span>
            </li>
          </ul>
        </template>
        <SheetCloseButton class="mt-5" @click="close" />
      </div>
    </template>
  </BottomSheet>
</template>
