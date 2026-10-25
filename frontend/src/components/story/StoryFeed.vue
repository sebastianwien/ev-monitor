<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { MapIcon, ChevronRightIcon } from '@heroicons/vue/24/outline'
import StoryFeedItem from './StoryFeedItem.vue'
import type { StoryFeedItem as Item, TileId } from '../../composables/storyModel'

/** "Diesen Monat": die Feed-Zeilen und darunter die Zeile zur Karte. Steht auf dem Handy unter "Zuletzt", ab lg rechts neben dem Hero. */
defineProps<{
  feed: Item[]
  upsellTo: string
  /** Modell ohne Variante für den Modellvergleich. */
  modelName: string
  carId: string | null
  carName: string
  sheets: TileId[]
}>()
const emit = defineEmits<{ open: [id: TileId]; map: [] }>()
const { t } = useI18n()
</script>

<template>
  <section class="grid content-start" aria-labelledby="story-feed-title" data-testid="story-feed">
    <h2 id="story-feed-title" class="pb-1 text-[18px] font-bold tracking-tight text-gray-900 md:text-[20px] dark:text-gray-100">{{ t('story.feed_title') }}</h2>
    <StoryFeedItem
      v-for="item in feed"
      :key="item.moment?.key ?? item.id"
      :item="item"
      :upsell-to="upsellTo"
      :model-name="modelName"
      :car-id="carId"
      :car-name="carName"
      :sheets="sheets"
      @open="emit('open', $event)" />
    <button
      type="button"
      data-testid="story-map-open"
      class="mt-3 flex h-12 w-full items-center gap-2.5 rounded-xl bg-white px-3 text-left edge-card transition-transform active:translate-x-0.5 active:translate-y-0.5 active:shadow-none dark:bg-gray-800"
      @click="emit('map')">
      <MapIcon class="h-5 w-5 shrink-0 text-green-700 dark:text-green-400" aria-hidden="true" />
      <span class="flex-1 text-[14px] font-semibold text-gray-900 dark:text-gray-50">{{ t('story.map_title') }}</span>
      <span class="text-[12px] text-gray-500 dark:text-gray-400">{{ t('story.map_label') }}</span>
      <ChevronRightIcon class="h-4 w-4 text-gray-400" aria-hidden="true" />
    </button>
  </section>
</template>
