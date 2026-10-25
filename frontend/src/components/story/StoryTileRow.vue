<script setup lang="ts">
import StoryTile from './StoryTile.vue'
import type { StoryTile as Tile, TileId } from '../../composables/storyModel'

/** Die Kacheln: auf dem Handy eine Rutsche mit Scroll-Snap (Rand oben und unten fuer den Fokusring), ab md ein Raster mit zwei Spalten. */
defineProps<{ tiles: Tile[] }>()
const emit = defineEmits<{ open: [id: TileId] }>()
</script>

<template>
  <div
    class="-mx-4 -my-1 flex snap-x snap-mandatory gap-2.5 overflow-x-auto px-4 py-1 scroll-px-4 scrollbar-hide md:m-0 md:grid md:grid-cols-2 md:gap-3 md:overflow-visible md:p-0"
    data-testid="story-tiles">
    <StoryTile v-for="tile in tiles" :key="tile.id" :tile="tile" @open="emit('open', $event)" />
  </div>
</template>
