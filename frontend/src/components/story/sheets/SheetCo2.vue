<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useLocaleFormat } from '../../../composables/useLocaleFormat'
import type { StoryTile } from '../../../composables/storyModel'

/** CO₂ gegen einen Benziner der eigenen Fahrzeugklasse: gesparte kg, Liter, Summe seit Start und drei Balken in g/km. */
const props = defineProps<{ tile: StoryTile }>()
const { t, locale } = useI18n()
const { formatDecimal } = useLocaleFormat()

const c = computed(() => props.tile.co2!)
const rows = computed(() => [
  { key: 'petrol', label: t('story.co2_petrol'), g: c.value.petrolGPerKm, cls: 'bg-gray-400 dark:bg-gray-500' },
  { key: 'grid', label: t('story.co2_yours'), g: c.value.gridGPerKm, cls: 'bg-green-700 dark:bg-green-400' },
  { key: 'green', label: t('story.co2_green'), g: c.value.greenGPerKm, cls: 'bg-green-500 dark:bg-green-300' },
])
const since = computed(() => c.value.lifetime
  ? new Date(c.value.lifetime.since).toLocaleDateString(locale.value, { month: 'long', year: 'numeric' })
  : null)
const basis = computed(() => {
  const liters = formatDecimal(c.value.petrolLitersPer100km, 1)
  return c.value.category
    ? t('story.sheet_co2_basis_class', { category: t(`models_list.filters.categories.${c.value.category}`), liters })
    : t('story.sheet_co2_basis', { liters })
})
</script>

<template>
  <div data-testid="sheet-co2">
    <div class="mt-1 grid gap-1 text-sm text-gray-600 dark:text-gray-300">
      <p>{{ t(tile.value < 0 ? 'story.sheet_co2_text_more' : 'story.sheet_co2_text', { kg: formatDecimal(Math.abs(tile.value), 0) }) }}</p>
      <i18n-t keypath="story.co2_liters" tag="p" scope="global">
        <template #liters><b class="tabular-nums text-gray-900 dark:text-gray-100">{{ formatDecimal(c.petrolLiters, 0) }} l</b></template>
      </i18n-t>
      <i18n-t v-if="c.lifetime" keypath="story.co2_since" tag="p" scope="global">
        <template #date>{{ since }}</template>
        <template #kg><b class="tabular-nums text-gray-900 dark:text-gray-100">{{ formatDecimal(c.lifetime.kg, 0) }} kg</b></template>
      </i18n-t>
    </div>
    <div class="mt-4 grid gap-2.5">
      <div v-for="r in rows" :key="r.key" class="grid gap-1">
        <div class="flex justify-between text-sm tabular-nums">
          <span class="text-gray-600 dark:text-gray-300">{{ r.label }}</span>
          <b class="text-gray-900 dark:text-gray-100">{{ formatDecimal(r.g, 0) }} g/km</b>
        </div>
        <span class="block h-3 rounded-[3px] bg-gray-100 dark:bg-gray-800"><i class="block h-full rounded-[3px]" :class="r.cls" :style="{ width: `${Math.max(1, (r.g / rows[0].g) * 100)}%` }" /></span>
      </div>
    </div>
    <p class="mt-3 text-xs text-gray-500 dark:text-gray-400">{{ basis }}</p>
  </div>
</template>
