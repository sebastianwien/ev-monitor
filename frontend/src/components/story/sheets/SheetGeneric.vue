<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import Spark from '../Spark.vue'
import MonthBars from '../MonthBars.vue'
import { useLocaleFormat } from '../../../composables/useLocaleFormat'
import type { StoryTile } from '../../../composables/storyModel'

/** Kacheln ohne eigenes Sheet: Monatsbalken mit Beträgen (MonthBars), sonst großes Spark-Chart. */
const props = defineProps<{
  tile: StoryTile
  monthLabels: string[]
  runningMonth: boolean
}>()

const { t } = useI18n()
const { formatCurrency } = useLocaleFormat()

const partialLabel = computed(() => props.runningMonth ? t('story.sheet_partial', { month: props.monthLabels[props.monthLabels.length - 1] ?? '' }) : '')
</script>

<template>
  <div>
    <MonthBars
      v-if="tile.sparkVariant === 'line'"
      class="mt-4"
      :values="tile.spark" :labels="monthLabels" :format="(v: number) => formatCurrency(v, { decimals: 0 })"
      :partial-label="partialLabel" :tone="tile.sentiment" />
    <div v-else class="mt-4 h-28">
      <Spark :values="tile.spark" :variant="tile.sparkVariant" :tone="tile.sentiment" class="h-full [&_svg]:h-full" />
    </div>
  </div>
</template>
