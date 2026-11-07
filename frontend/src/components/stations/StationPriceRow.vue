<script setup lang="ts">
/** Ein Katalogtarif an der Säule: Karte, Gebühren als Satz, rechts der Preis je kWh. */
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import BoldNumbers from '../story/BoldNumbers.vue'
import type { StationTariff } from '../../api/chargingStationService'
import { feeLine, formatEur } from '../../utils/stationMap'
import { cardLabel } from './stationText'

const props = defineProps<{ tariff: StationTariff }>()
const { t, locale } = useI18n()
const eur = (n: number) => formatEur(n, locale.value)

const fees = computed(() => {
  const f = feeLine(props.tariff)
  if (f.kind === 'none') return t('stations.no_fees')
  const parts: string[] = []
  if (f.sessionFeeEur != null) parts.push(t('stations.session_fee', { fee: eur(f.sessionFeeEur) }))
  if (f.blocking) {
    const b = f.blocking
    let line = b.afterMin ? t('stations.blocking_fee_after', { min: b.afterMin, fee: eur(b.perMin) }) : t('stations.blocking_fee', { fee: eur(b.perMin) })
    if (b.capEur) line += `, ${t('stations.blocking_cap', { cap: eur(b.capEur) })}`
    parts.push(line)
  }
  return parts.join(' · ')
})
const monthly = computed(() => props.tariff.monthlyFeeEur && props.tariff.monthlyFeeEur > 0
  ? t('stations.monthly_fee', { fee: eur(props.tariff.monthlyFeeEur) }) : null)
const name = computed(() => cardLabel(props.tariff.name, props.tariff.variant))
</script>

<template>
  <li data-testid="station-price-row" class="flex items-start justify-between gap-3 border-b border-gray-200 px-4 py-3 dark:border-white/10">
    <div class="min-w-0">
      <div class="font-bold text-gray-900 dark:text-gray-100">{{ name }}</div>
      <span v-if="tariff.direct" class="mt-1 inline-block rounded-full border border-gray-400 px-2 text-[12px] font-semibold text-gray-700 dark:border-gray-500 dark:text-gray-300">{{ t('stations.own_station') }}</span>
      <div class="mt-1 text-[13px] text-gray-600 tabular-nums dark:text-gray-400"><BoldNumbers :text="fees" units /></div>
      <div v-if="monthly" class="text-[13px] text-gray-600 tabular-nums dark:text-gray-400"><BoldNumbers :text="monthly" units /></div>
    </div>
    <span class="ci-money shrink-0 text-[15px] font-bold tabular-nums">{{ eur(tariff.pricePerKwh) }}</span>
  </li>
</template>
