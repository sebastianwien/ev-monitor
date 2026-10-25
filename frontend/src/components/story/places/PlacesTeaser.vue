<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { MapIcon, ChevronRightIcon } from '@heroicons/vue/24/outline'
import { useLocaleFormat } from '../../../composables/useLocaleFormat'
import { placeName, type PlaceRow, type PlacesTeaser } from '../../../composables/storyPlaces'
import StaticPlacesMap from './StaticPlacesMap.vue'

/**
 * Zeile zur Ladekarte unter dem Feed. Mit Orten im Story-Monat nennt sie, was die Karte zeigt (teurer oder günstiger
 * als andere, sonst Orte und Geld), damit niemand die alte kWh-Karte dahinter erwartet. Mit `preview` (ab lg, rechte
 * Spalte) steht darüber ein Kartenbild; ohne lädt sie keine Kachel.
 */
const props = defineProps<{
  teaser: PlacesTeaser | null
  rows: PlaceRow[]
  monthName: string
  preview: boolean
}>()
const emit = defineEmits<{ open: [] }>()

const { t } = useI18n()
const { formatCurrency, formatCostPerKwh } = useLocaleFormat()

const sentence = computed(() => {
  const x = props.teaser
  if (!x) return null
  if (x.key === 'plain') {
    return x.eur > 0
      ? t('story.places_teaser_plain', { places: x.places, month: props.monthName, eur: formatCurrency(x.eur, { decimals: x.eur < 10 ? 2 : 0 }) }, x.places)
      : t('story.places_teaser_plain_noeur', { places: x.places, month: props.monthName }, x.places)
  }
  return x.places === 1 ? t(`story.places_teaser_${x.key}_single`) : t(`story.places_teaser_${x.key}`, { count: x.count, places: x.places })
})

const detail = computed(() => {
  const f = props.teaser?.focus
  if (!f || f.place.pricePerKwh == null) return null
  return t('story.places_teaser_detail', {
    name: placeName(f.place) ?? t('story.places_kind_public'),
    price: formatCostPerKwh(f.place.pricePerKwh, { subunitDecimals: 0 }),
    median: formatCostPerKwh(f.benchmark.medianPricePerKwh, { subunitDecimals: 0 }),
  })
})
</script>

<template>
  <button
    type="button"
    data-testid="story-map-open"
    class="mt-3 w-full overflow-hidden rounded-xl bg-white text-left edge-card transition-transform active:translate-x-0.5 active:translate-y-0.5 active:shadow-none dark:bg-gray-800"
    @click="emit('open')">
    <StaticPlacesMap v-if="preview && rows.length" :rows="rows" class="h-[180px] border-b-2 border-gray-900 dark:border-gray-700" />
    <span class="flex min-h-11 items-center gap-2.5 px-3 py-2">
      <MapIcon class="h-5 w-5 shrink-0 text-green-700 dark:text-green-400" aria-hidden="true" />
      <span class="min-w-0 flex-1">
        <span class="block text-[14px] font-semibold text-gray-900 dark:text-gray-50">{{ t('story.map_title') }}</span>
        <span v-if="sentence" data-testid="places-teaser-sentence" class="block text-[13.5px] leading-snug text-gray-700 dark:text-gray-200">{{ sentence }}</span>
        <span v-if="detail" data-testid="places-teaser-detail" class="block text-[12.5px] tabular-nums text-gray-500 dark:text-gray-400">{{ detail }}</span>
      </span>
      <span v-if="!sentence" class="text-[12px] text-gray-500 dark:text-gray-400">{{ t('story.map_label') }}</span>
      <ChevronRightIcon class="h-4 w-4 shrink-0 text-gray-400" aria-hidden="true" />
    </span>
  </button>
</template>
