<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { HomeIcon, ChevronRightIcon, ChevronDownIcon } from '@heroicons/vue/24/outline'
import Collapse from '../../shared/Collapse.vue'
import StoryPriceDetail from '../StoryPriceDetail.vue'
import { useLocaleFormat } from '../../../composables/useLocaleFormat'
import { placeName, needsOperator, type ChargingPlace, type PlaceRow } from '../../../composables/storyPlaces'
import { PLACE_FILL, PIN_EDGE } from './placeStyle'

/**
 * Die Liste der Ladeorte ist die vollständige Fassung der Karte (die Karte ist aria-hidden).
 * Zeile antippen markiert den Ort. Der Amber-Chip "teurer" klappt den Preisvergleich in der Zeile auf,
 * "Betreiber eintragen" meldet die neueste Ladung des Orts.
 */
defineProps<{
  rows: PlaceRow[]
  active: string | null
}>()
const emit = defineEmits<{ tap: [geohash: string]; operator: [logId: string] }>()
/** Ort mit aufgeklapptem Preisvergleich, höchstens einer. */
const expanded = ref<string | null>(null)
const toggleExpanded = (gh: string) => { expanded.value = expanded.value === gh ? null : gh }

const { t, locale } = useI18n()
const { formatCurrency, formatCostPerKwh, formatDecimal } = useLocaleFormat()

function name(p: ChargingPlace): string {
  return placeName(p) ?? t(`story.places_kind_${p.kind.toLowerCase()}`)
}
function sub(p: ChargingPlace): string {
  const last = new Date(p.lastChargedAt).toLocaleDateString(locale.value, { day: 'numeric', month: 'numeric' })
  return [
    t('story.places_charges', { n: p.charges }, p.charges),
    `${formatDecimal(p.kwh, 0)} kWh`,
    p.pricePerKwh != null ? formatCostPerKwh(p.pricePerKwh, { subunitDecimals: 0 }) : t('story.places_no_price'),
    t('story.places_last', { date: last }),
  ].join(' · ')
}
/** Zweite Zeile mit Chip: teurer, günstiger oder Betreiber fehlt. */
const hasChip = (r: PlaceRow) => (r.cls === 'high' || r.cls === 'low') && !!r.benchmark || needsOperator(r.place)
const eur = (v: number) => v > 0 ? formatCurrency(v, { decimals: v < 10 ? 2 : 0 }) : ''

const CHIP = 'relative mt-1 inline-flex items-center gap-1 rounded-sm px-1.5 py-0.5 text-[12px] font-semibold before:absolute before:-inset-y-3 before:inset-x-0 before:content-[\'\']'
const AMBER = 'bg-amber-100 text-amber-800 dark:bg-amber-900/40 dark:text-amber-300'
</script>

<template>
  <ul class="divide-y divide-gray-200 dark:divide-gray-800" :aria-label="t('story.places_list_aria')">
    <li
      v-for="r in rows" :key="r.place.geohash"
      :data-place="r.place.geohash"
      data-testid="places-row"
      class="transition-colors"
      :class="active === r.place.geohash ? 'bg-green-50 dark:bg-green-400/10' : ''">
      <button
        type="button"
        data-testid="places-row-button"
        :aria-pressed="active === r.place.geohash"
        class="flex min-h-14 w-full items-center gap-3 px-4 pt-3 text-left focus:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-green-600 lg:px-6"
        :class="hasChip(r) ? 'pb-1' : 'pb-3'"
        @click="emit('tap', r.place.geohash)">
        <span v-if="r.cls === 'home'" class="grid h-8 w-8 shrink-0 place-items-center rounded-full" :class="PLACE_FILL.home" aria-hidden="true">
          <HomeIcon class="h-5 w-5" />
        </span>
        <span v-else class="mx-2 h-4 w-4 shrink-0" :class="[PIN_EDGE, PLACE_FILL[r.cls]]" aria-hidden="true" />
        <span class="min-w-0 flex-1">
          <span data-testid="places-name" class="block truncate text-[15px] font-semibold text-gray-900 dark:text-gray-50">{{ name(r.place) }}</span>
          <span data-testid="places-sub" class="block text-[13px] leading-snug tabular-nums text-gray-500 dark:text-gray-400">{{ sub(r.place) }}</span>
        </span>
        <span data-testid="places-eur" class="shrink-0 text-[15px] font-bold tabular-nums text-gray-900 dark:text-gray-50">{{ eur(r.place.costEur) }}</span>
      </button>
      <div v-if="hasChip(r)" class="pb-3 pl-[60px] pr-4 lg:pl-[68px] lg:pr-6">
        <button
          v-if="r.cls === 'high' && r.benchmark"
          type="button"
          data-testid="places-chip-high"
          :class="[CHIP, AMBER]"
          :aria-expanded="expanded === r.place.geohash"
          :aria-controls="`places-prices-${r.place.geohash}`"
          @click="toggleExpanded(r.place.geohash)">
          {{ t('story.places_high_chip', { price: formatCostPerKwh(r.benchmark.medianPricePerKwh, { subunitDecimals: 0 }), n: r.benchmark.drivers }) }}
          <ChevronDownIcon class="h-3 w-3 transition-transform motion-reduce:transition-none" :class="expanded === r.place.geohash ? 'rotate-180' : ''" aria-hidden="true" />
        </button>
        <span
          v-else-if="r.cls === 'low' && r.benchmark"
          data-testid="places-chip-low"
          class="mt-1 inline-block rounded-sm bg-green-100 px-1.5 py-0.5 text-[12px] font-semibold text-green-800 dark:bg-green-900/40 dark:text-green-300">
          {{ t('story.places_low_chip', { n: r.benchmark.drivers }) }}
        </span>
        <button
          v-else-if="needsOperator(r.place)"
          type="button"
          data-testid="places-chip-operator"
          :class="[CHIP, AMBER]"
          @click="emit('operator', r.place.lastLogId)">
          {{ t('story.places_operator_chip') }}
          <ChevronRightIcon class="h-3 w-3" aria-hidden="true" />
        </button>
      </div>
      <Collapse v-if="r.cls === 'high' && r.benchmark" :open="expanded === r.place.geohash">
        <div :id="`places-prices-${r.place.geohash}`" data-testid="places-prices" class="pb-4 pl-[60px] pr-4 lg:pl-[68px] lg:pr-6">
          <StoryPriceDetail :benchmark="r.benchmark" :price-per-kwh="r.place.pricePerKwh" />
        </div>
      </Collapse>
    </li>
  </ul>
</template>
