<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import StoryFeedItem from './StoryFeedItem.vue'
import PlacesTeaser from './places/PlacesTeaser.vue'
import type { StoryFeedItem as Item, TileId } from '../../composables/storyModel'
import type { PlaceRow, PlacesTeaser as Teaser } from '../../composables/storyPlaces'

/** "Diesen Monat": die Feed-Zeilen und darunter der Teaser zur Karte. Steht auf dem Handy unter "Zuletzt", ab lg rechts neben dem Hero. */
withDefaults(defineProps<{
  feed: Item[]
  upsellTo: string
  /** Modell ohne Variante für den Modellvergleich. */
  modelName: string
  carId: string | null
  carName: string
  sheets: TileId[]
  /** Ladeorte des Story-Monats für den Karten-Teaser, ohne Orte die schlichte Zeile. */
  placesTeaser?: Teaser | null
  placesRows?: PlaceRow[]
  monthName?: string
  /** Kartenbild im Teaser, nur in der rechten Spalte (ab lg). */
  mapPreview?: boolean
}>(), { placesTeaser: null, placesRows: () => [], monthName: '', mapPreview: false })
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
    <PlacesTeaser :teaser="placesTeaser" :rows="placesRows" :month-name="monthName" :preview="mapPreview" @open="emit('map')" />
  </section>
</template>
