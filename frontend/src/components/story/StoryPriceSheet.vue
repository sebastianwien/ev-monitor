<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import BottomSheet from '../shared/BottomSheet.vue'
import SheetCloseButton from './SheetCloseButton.vue'
import StoryPriceDetail from './StoryPriceDetail.vue'
import { chargingBenchmarkService, type CardSavings, type ChargingBenchmark } from '../../api/chargingBenchmarkService'

/**
 * Was andere Fahrer bei einem Betreiber zahlen, zum Moment "zu teuer geladen". Inhalt in StoryPriceDetail.
 * Mit {@code logId} holt das Sheet selbst, was gepflegte Ladekarten bei dieser Ladung gespart hätten;
 * ohne Ergebnis oder bei Fehler fehlt nur dieser Abschnitt.
 */
const props = defineProps<{
  benchmark: ChargingBenchmark
  /** Was der Nutzer bei der auslösenden Ladung gezahlt hat. */
  pricePerKwh: number | null
  /** Die auslösende Ladung; ohne sie keine Ersparnis-Zeilen. */
  logId?: string | null
}>()
const emit = defineEmits<{ close: [] }>()

const { t } = useI18n()
const title = computed(() => t('story.sheet_prices_title', { cpo: props.benchmark.cpoName }))

const savings = ref<CardSavings | null>(null)
onMounted(async () => {
  if (!props.logId) return
  savings.value = await chargingBenchmarkService.savings(props.logId).catch(() => null)
})
</script>

<template>
  <BottomSheet :label="title" testid="story-price-sheet" panel-class="sm:max-w-lg" edge @close="emit('close')">
    <template #default="{ close }">
      <div class="px-4 pt-3 pb-4 max-h-[85vh] overflow-y-auto">
        <div class="w-10 h-1 rounded-full bg-gray-300 dark:bg-gray-600 mx-auto mb-3" />
        <h3 class="text-xl font-extrabold text-gray-900 dark:text-gray-50">{{ title }}</h3>
        <StoryPriceDetail :benchmark="benchmark" :price-per-kwh="pricePerKwh" :savings="savings" />
        <SheetCloseButton class="mt-5" @click="close" />
      </div>
    </template>
  </BottomSheet>
</template>
