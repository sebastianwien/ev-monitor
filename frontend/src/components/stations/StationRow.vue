<script setup lang="ts">
/** Eine Säule in der Liste: Betreiber, darunter Entfernung, Stecker und Ladepunkte, rechts der günstigste Preis. */
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ChevronRightIcon } from '@heroicons/vue/24/outline'
import type { MapStation } from '../../api/chargingStationService'
import { formatDistance, formatEur, type ChargeType } from '../../utils/stationMap'
import { cardLabel, stationMeta } from './stationText'

const props = defineProps<{ station: MapStation; type: ChargeType; distanceMeters: number }>()
const emit = defineEmits<{ select: [] }>()
const { t, locale } = useI18n()

const price = computed(() => props.type === 'DC' ? props.station.priceDc : props.station.priceAc)
const meta = computed(() => [formatDistance(props.distanceMeters, locale.value), ...stationMeta(props.station, props.type, t)].join(' · '))
</script>

<template>
  <button type="button" data-testid="station-row"
    class="flex w-full min-h-[68px] items-center gap-3 border-b border-gray-200 px-4 py-2.5 text-left text-gray-900 transition-colors hover:bg-black/5 focus-visible:outline focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-indigo-600 dark:border-white/10 dark:text-gray-100 dark:hover:bg-white/5"
    @click="emit('select')">
    <span class="min-w-0 flex-1">
      <span class="block truncate text-base font-bold">{{ station.name }}</span>
      <span class="block truncate text-[13px] text-gray-600 tabular-nums dark:text-gray-400">{{ meta }}</span>
    </span>
    <span v-if="price" class="shrink-0 text-right">
      <span class="ci-money text-[15px] font-bold tabular-nums">{{ formatEur(price.pricePerKwh, locale) }}</span>
      <span class="mt-0.5 block max-w-[8rem] truncate text-[12px] text-gray-600 dark:text-gray-400">{{ cardLabel(price.cardName, price.variant) }}</span>
    </span>
    <ChevronRightIcon class="h-4 w-4 shrink-0 text-gray-400" aria-hidden="true" />
  </button>
</template>
