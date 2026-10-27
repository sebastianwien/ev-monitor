<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import type { CardSavingOffer, CardSavings } from '../../api/chargingBenchmarkService'

/**
 * "Mit Ladekarte an IONITY-Säulen": gepflegte Karten, die bei dieser Ladung mindestens einen Euro
 * gespart hätten, nach Ersparnis sortiert, Grundgebühr anteilig eingerechnet, mit Prüfdatum. Keine
 * Links und keine Provision: der Abschnitt soll vergleichen, nicht werben. Ohne Angebot nichts.
 */
const props = defineProps<{ savings: CardSavings | null }>()

const { t, locale } = useI18n()
const { formatCostPerKwh, formatCurrency } = useLocaleFormat()

const offers = computed<CardSavingOffer[]>(() => props.savings?.offers ?? [])
const name = (o: CardSavingOffer) => o.variant ? `${o.name} ${o.variant}` : o.name
const fee = (o: CardSavingOffer) => o.monthlyFeeEur > 0
  ? t('story.sheet_prices_card_fee', { fee: formatCurrency(o.monthlyFeeEur) })
  : t('story.sheet_prices_card_no_fee')
/** Abgerundet: eine Schätzung, die eher zu wenig verspricht. */
const saving = (o: CardSavingOffer) => formatCurrency(-Math.floor(o.chargeSavingsEur), { decimals: 0 })
/** Der älteste Stand der gezeigten Karten, damit das Datum für alle Zeilen stimmt. */
const checked = computed(() => {
  const dates = offers.value.map(o => o.verifiedOn).filter((d): d is string => d != null).sort()
  return dates.length
    ? new Date(`${dates[0]}T00:00:00`).toLocaleDateString(locale.value, { day: '2-digit', month: '2-digit', year: 'numeric' })
    : null
})
</script>

<template>
  <section v-if="savings && offers.length" data-testid="card-savings">
    <h4 class="mt-5 text-xs uppercase tracking-wider text-gray-500 dark:text-gray-400">{{ t('story.sheet_prices_with_card', { cpo: savings.cpoName }) }}</h4>
    <ul class="mt-1 divide-y divide-gray-200 dark:divide-gray-700">
      <li v-for="o in offers" :key="name(o)" data-testid="card-saving" class="flex items-baseline gap-3 py-2.5">
        <span class="min-w-[4.5rem] shrink-0 text-base font-extrabold tabular-nums text-gray-900 dark:text-gray-50">{{ formatCostPerKwh(o.pricePerKwh, { subunitDecimals: 0 }) }}</span>
        <span class="min-w-0 flex-1">
          <span class="block text-sm text-gray-900 dark:text-gray-50">{{ name(o) }}</span>
          <span class="block text-xs text-gray-500 dark:text-gray-400">{{ fee(o) }}</span>
        </span>
        <span class="shrink-0 text-sm font-bold tabular-nums text-green-700 dark:text-green-400">{{ saving(o) }}</span>
      </li>
    </ul>
    <p v-if="checked" class="mt-1.5 text-xs text-gray-500 dark:text-gray-400">{{ t('story.sheet_prices_saving_note', { date: checked }) }}</p>
  </section>
</template>
