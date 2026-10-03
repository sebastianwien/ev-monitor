<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import Spark from '../Spark.vue'
import TempScatter from '../TempScatter.vue'
import { useLocaleFormat } from '../../../composables/useLocaleFormat'
import type { StoryTile } from '../../../composables/storyModel'
import type { TemperatureEffect } from '../../../composables/storyInsights'

/** WLTP-Vergleich mit Verlauf plus Verbrauch gegen Aussentemperatur aus den Fahrten. */
const props = defineProps<{
  tile: StoryTile
  monthLabels: string[]
  temperature: TemperatureEffect | null
}>()

const { t } = useI18n()
const { formatConsumption, formatDecimal, consumptionUnitLabel } = useLocaleFormat()

const text = computed(() => t('story.sheet_wltp_text', {
  wltp: formatConsumption(props.tile.reference ?? 0),
  mine: formatConsumption(props.tile.value),
}))
</script>

<template>
  <div>
    <p class="text-sm text-gray-600 dark:text-gray-300 mt-1">{{ text }}</p>
    <div class="mt-4 h-24">
      <Spark :values="tile.spark" variant="bars" :reference="tile.reference" :tone="tile.sentiment" class="!h-24" />
    </div>
    <div class="flex justify-between text-[11px] text-gray-500 dark:text-gray-400 mt-1 tabular-nums">
      <span v-for="(l, i) in monthLabels" :key="i">{{ l }}</span>
    </div>
    <div class="flex justify-between text-[11px] text-gray-700 dark:text-gray-200 mt-0.5 tabular-nums">
      <span v-for="(v, i) in tile.spark" :key="i">{{ formatDecimal(v, 0) }}</span>
    </div>

    <h4 class="mt-5 font-extrabold text-gray-900 dark:text-gray-50">{{ t('story.sheet_wltp_temp_title') }}</h4>
    <p class="text-xs text-gray-500 dark:text-gray-400">{{ t('story.sheet_wltp_temp_sub', { unit: consumptionUnitLabel() }) }}</p>
    <div v-if="temperature" class="mt-2" data-testid="story-temp-scatter">
      <TempScatter :effect="temperature" :unit-label="consumptionUnitLabel()" />
      <p v-if="temperature.deltaPercent != null" class="text-sm text-gray-800 dark:text-gray-200 mt-1">
        {{ t('story.feed_temp', { pct: formatDecimal(Math.abs(temperature.deltaPercent), 0), cold: 5, mild: 15 }) }}
      </p>
    </div>
    <p v-else class="mt-2 text-xs text-gray-500 dark:text-gray-400">{{ t('story.sheet_wltp_temp_none') }}</p>
  </div>
</template>
