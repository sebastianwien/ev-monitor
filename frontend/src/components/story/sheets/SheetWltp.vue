<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import MonthBars from '../MonthBars.vue'
import TempScatter from '../TempScatter.vue'
import { useLocaleFormat } from '../../../composables/useLocaleFormat'
import type { StoryTile } from '../../../composables/storyModel'
import type { TemperatureEffect } from '../../../composables/storyInsights'

/**
 * WLTP-Vergleich: Monatswert gross, Abstand zum Herstellerwert als Satz, Monatsbalken mit Herstellerlinie,
 * darunter Verbrauch gegen Aussentemperatur aus den Fahrten. Ohne Fahrten mit Temperatur ein Satz zu den ueblichen Gruenden.
 */
const props = defineProps<{
  tile: StoryTile
  monthLabels: string[]
  monthName: string
  runningMonth: boolean
  temperature: TemperatureEffect | null
}>()

const { t } = useI18n()
const { formatConsumption, formatDecimal, consumptionUnitLabel } = useLocaleFormat()

const month = computed(() => props.monthName.charAt(0).toUpperCase() + props.monthName.slice(1))
const reference = computed(() => props.tile.reference ?? null)

const gap = computed(() => {
  if (reference.value == null || reference.value <= 0) return ''
  const pct = props.tile.deltaPercent ?? ((props.tile.value - reference.value) / reference.value) * 100
  return t(pct >= 0 ? 'story.sheet_wltp_more' : 'story.sheet_wltp_less', {
    month: month.value,
    pct: formatDecimal(Math.abs(pct), 0),
    wltp: formatDecimal(reference.value, 1),
  })
})

const bars = computed(() => ({
  reference: reference.value == null ? null : { value: reference.value, label: t('story.sheet_ref_label', { value: formatDecimal(reference.value, 1) }) },
  partialLabel: props.runningMonth ? t('story.sheet_partial', { month: props.monthLabels[props.monthLabels.length - 1] ?? '' }) : '',
}))

const gapTone = computed(() => ({
  good: 'text-green-700 dark:text-green-400',
  warn: 'text-amber-700 dark:text-amber-400',
  bad: 'text-rose-700 dark:text-rose-400',
  neutral: 'text-gray-700 dark:text-gray-200',
}[props.tile.sentiment]))
</script>

<template>
  <div>
    <p class="mt-3 flex items-baseline gap-1.5">
      <b class="text-[40px] font-extrabold leading-none tracking-tight text-gray-900 tabular-nums dark:text-gray-50">{{ formatConsumption(tile.value, { showUnit: false }) }}</b>
      <span class="text-[15px] font-semibold text-gray-500 dark:text-gray-400">{{ consumptionUnitLabel() }}</span>
    </p>
    <p v-if="gap" class="mt-1.5 text-pretty text-[15px] font-semibold tabular-nums" :class="gapTone" data-testid="story-wltp-gap">{{ gap }}</p>

    <MonthBars
      class="mt-4"
      :values="tile.spark" :labels="monthLabels" :format="(v: number) => formatDecimal(v, 0)"
      :reference="bars.reference" :partial-label="bars.partialLabel" :tone="tile.sentiment" />

    <section v-if="temperature" class="mt-7" data-testid="story-temp-scatter">
      <h4 class="text-[17px] font-extrabold text-gray-900 dark:text-gray-50">{{ t('story.sheet_wltp_temp_title') }}</h4>
      <p class="mt-0.5 text-pretty text-[13px] text-gray-600 dark:text-gray-300">{{ t('story.sheet_wltp_temp_sub') }}</p>
      <p v-if="temperature.deltaPercent != null" class="mt-2 text-pretty text-[15px] font-semibold text-gray-900 dark:text-gray-50">
        {{ t('story.feed_temp', { pct: formatDecimal(Math.abs(temperature.deltaPercent), 0), cold: 5, mild: 15 }) }}
      </p>
      <TempScatter class="mt-3" :effect="temperature" :unit-label="consumptionUnitLabel()" :reference="reference" />
    </section>
    <p v-else class="mt-5 text-pretty text-[14px] text-gray-700 dark:text-gray-200">{{ t('story.sheet_wltp_reasons') }}</p>
  </div>
</template>
