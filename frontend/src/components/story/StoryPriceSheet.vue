<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import BottomSheet from '../shared/BottomSheet.vue'
import SheetCloseButton from './SheetCloseButton.vue'
import StoryPriceDetail from './StoryPriceDetail.vue'
import type { ChargingBenchmark } from '../../api/chargingBenchmarkService'

/** Was andere Fahrer bei einem Betreiber zahlen, zum Moment "zu teuer geladen". Inhalt in StoryPriceDetail. */
const props = defineProps<{
  benchmark: ChargingBenchmark
  /** Was der Nutzer bei der auslösenden Ladung gezahlt hat. */
  pricePerKwh: number | null
}>()
const emit = defineEmits<{ close: [] }>()

const { t } = useI18n()
const title = computed(() => t('story.sheet_prices_title', { cpo: props.benchmark.cpoName }))
</script>

<template>
  <BottomSheet :label="title" testid="story-price-sheet" panel-class="sm:max-w-lg" edge @close="emit('close')">
    <template #default="{ close }">
      <div class="px-4 pt-3 pb-4 max-h-[85vh] overflow-y-auto">
        <div class="w-10 h-1 rounded-full bg-gray-300 dark:bg-gray-600 mx-auto mb-3" />
        <h3 class="text-xl font-extrabold text-gray-900 dark:text-gray-50">{{ title }}</h3>
        <StoryPriceDetail :benchmark="benchmark" :price-per-kwh="pricePerKwh" />
        <SheetCloseButton class="mt-5" @click="close" />
      </div>
    </template>
  </BottomSheet>
</template>
