<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import StoryFeedItem from './StoryFeedItem.vue'
import PlacesTeaser from './places/PlacesTeaser.vue'
import type { StoryFeedItem as Item, TileId } from '../../composables/storyModel'
import type { PlaceRow, PlacesTeaser as Teaser } from '../../composables/storyPlaces'

/**
 * "Diesen Monat" bzw. "Im September": Überschrift mit Linien links und rechts, darunter höchstens drei
 * Insight-Zeilen mit Vergleichsbasis und der Teaser zur Karte. Steht auf dem Handy unter "Zuletzt",
 * ab lg rechts neben dem Hero.
 */
withDefaults(defineProps<{
  feed: Item[]
  /** Modell ohne Variante für den Modellvergleich. */
  modelName: string
  sheets: TileId[]
  /** Ladeorte des Story-Monats für den Karten-Teaser, ohne Orte die schlichte Zeile. */
  placesTeaser?: Teaser | null
  placesRows?: PlaceRow[]
  monthName?: string
  /** Laufender Monat: "Diesen Monat", sonst der Name des gewählten Monats. */
  runningMonth?: boolean
  /** Kartenbild im Teaser, nur in der rechten Spalte (ab lg). */
  mapPreview?: boolean
}>(), { placesTeaser: null, placesRows: () => [], monthName: '', runningMonth: true, mapPreview: false })
const emit = defineEmits<{ open: [id: TileId]; map: []; charge: [logId: string, section?: 'cost']; priceless: [] }>()
const { t } = useI18n()
</script>

<template>
  <section class="grid content-start gap-3" aria-labelledby="story-feed-title" data-testid="story-feed">
    <div class="flex items-center gap-3">
      <span class="h-0.5 flex-1 bg-gray-900 dark:bg-gray-100" aria-hidden="true" />
      <h2 id="story-feed-title" class="text-[18px] font-bold tracking-tight text-gray-900 md:text-[20px] dark:text-gray-100">{{ runningMonth ? t('story.feed_title') : t('story.feed_title_past', { month: monthName }) }}</h2>
      <span class="h-0.5 flex-1 bg-gray-900 dark:bg-gray-100" aria-hidden="true" />
    </div>
    <StoryFeedItem
      v-for="item in feed"
      :key="item.id"
      :item="item"
      :model-name="modelName"
      :month-name="monthName"
      :sheets="sheets"
      @open="emit('open', $event)" @charge="(id, section) => emit('charge', id, section)" @priceless="emit('priceless')" />
    <PlacesTeaser :teaser="placesTeaser" :rows="placesRows" :month-name="monthName" :preview="mapPreview" @open="emit('map')" />
  </section>
</template>
