<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import type { BenchmarkCard, CardSavings, ChargingBenchmark } from '../../api/chargingBenchmarkService'
import StoryCardSavings from './StoryCardSavings.vue'

/**
 * Was andere Fahrer bei einem Betreiber zahlen, ohne Rahmen: steht im eigenen Sheet (Moment "zu teuer
 * geladen") und als Unteransicht der Ladekarte. Hier stehen die Kartennamen, im Hero nie. Jede Karte
 * nennt ihre Quelle: von Fahrern dort bezahlt oder gepflegter Tarif mit Stand.
 *
 * Mit Ersparnis (eine bestimmte Ladung) stehen die gepflegten Karten mit ihrem Euro-Betrag oben;
 * die Liste darunter zeigt dann nur noch, was andere Fahrer hier bezahlt haben.
 */
const props = defineProps<{
  benchmark: ChargingBenchmark
  /** Was der Nutzer gezahlt hat. */
  pricePerKwh: number | null
  /** Ersparnis gepflegter Karten bei der Ladung, um die es geht; null ohne Ladung oder ohne Angebot. */
  savings?: CardSavings | null
}>()

const { t, locale } = useI18n()
const { formatCostPerKwh, formatCurrency } = useLocaleFormat()

const hasSavings = computed(() => (props.savings?.offers.length ?? 0) > 0)
const listed = computed(() => hasSavings.value ? props.benchmark.cards.filter(c => c.source === 'DRIVERS') : props.benchmark.cards)

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
  <div>
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

    <StoryCardSavings :savings="savings ?? null" />

    <template v-if="listed.length">
      <h4 class="mt-5 text-xs uppercase tracking-wider text-gray-500 dark:text-gray-400">{{ hasSavings ? t('story.sheet_prices_drivers_title') : t('story.sheet_prices_cards', { cpo: benchmark.cpoName }) }}</h4>
      <ul class="mt-1 divide-y divide-gray-200 dark:divide-gray-700">
        <li v-for="c in listed" :key="`${c.source}:${cardName(c)}`" data-testid="price-card" class="flex items-baseline justify-between gap-3 py-2.5">
          <span class="min-w-0">
            <span class="block font-semibold text-gray-900 dark:text-gray-50">{{ cardName(c) }}</span>
            <span class="block text-xs text-gray-500 dark:text-gray-400">{{ cardDetail(c) }}</span>
          </span>
          <span class="shrink-0 font-semibold tabular-nums text-gray-900 dark:text-gray-50">{{ formatCostPerKwh(c.pricePerKwh) }}</span>
        </li>
      </ul>
    </template>
  </div>
</template>
