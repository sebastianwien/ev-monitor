<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import { cheapestCard } from '../../composables/storyPlaces'
import type { BenchmarkCard, CardSavings, ChargingBenchmark } from '../../api/chargingBenchmarkService'
import StoryCardSavings from './StoryCardSavings.vue'

/**
 * "Was hätte ich hier mit einer anderen Karte gezahlt?", ohne Rahmen: steht im eigenen Sheet und als
 * Unteransicht der Ladekarte. Nur gepflegte Tarife, die heute gelten; was andere Fahrer zahlen, steht hier nie.
 *
 * Mit Ersparnis (eine bestimmte Ladung) stehen die Karten mit ihrem Euro-Betrag oben und die Liste entfällt.
 * Ohne Ersparnis die Liste der Tarife; zahlst du höchstens den günstigsten, sagt ein Satz das vorab.
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
const cheapest = computed(() => cheapestCard(props.benchmark))
/** Auf den halben Cent: 0,439 gezahlt gegen 0,44 Tarif ist derselbe Preis. */
const alreadyCheap = computed(() => !hasSavings.value && props.pricePerKwh != null && cheapest.value != null
  && props.pricePerKwh <= cheapest.value.pricePerKwh + 0.005)
const range = computed(() => {
  const prices = props.benchmark.cards.map(c => c.pricePerKwh)
  const min = Math.min(...prices)
  const max = Math.max(...prices)
  const ct = (v: number) => formatCostPerKwh(v, { subunitDecimals: 0 })
  return min === max
    ? t('story.sheet_prices_cheap_one', { cpo: props.benchmark.cpoName, price: ct(min) })
    : t('story.sheet_prices_cheap', { cpo: props.benchmark.cpoName, min: ct(min), max: ct(max) })
})

const typeLabel = computed(() => t(props.benchmark.chargingType === 'DC' ? 'story.sheet_prices_dc' : 'story.sheet_prices_ac'))

function cardName(c: BenchmarkCard): string {
  if (c.direct) return t('story.sheet_prices_direct', { cpo: c.name })
  return c.variant ? `${c.name} ${c.variant}` : c.name
}
function cardDetail(c: BenchmarkCard): string {
  const fee = c.direct
    ? t('story.sheet_prices_card_direct')
    : c.monthlyFeeEur > 0
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
    <p v-if="alreadyCheap" data-testid="price-cheap" class="mt-1 text-sm text-gray-700 dark:text-gray-200">{{ range }}</p>

    <StoryCardSavings :savings="savings ?? null" />

    <template v-if="!hasSavings && benchmark.cards.length">
      <h4 class="mt-5 text-xs uppercase tracking-wider text-gray-500 dark:text-gray-400">{{ t('story.sheet_prices_cards', { cpo: benchmark.cpoName, type: typeLabel }) }}</h4>
      <ul class="mt-1 divide-y divide-gray-200 dark:divide-gray-700">
        <li v-for="c in benchmark.cards" :key="cardName(c)" data-testid="price-card" class="flex items-baseline justify-between gap-3 py-2.5">
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
