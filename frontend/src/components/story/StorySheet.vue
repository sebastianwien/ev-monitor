<script setup lang="ts">
import BottomSheet from '../shared/BottomSheet.vue'
import SheetCloseButton from './SheetCloseButton.vue'
import StoryTileDetail, { useTileTitle, type StorySheetContext } from './StoryTileDetail.vue'
import type { StoryTile } from '../../composables/storyModel'

/** Detail zu einer Kachel als Sheet (unter lg): Titel, Inhalt je Kachel-Id, Schließen. */
const props = defineProps<{
  tile: StoryTile
  context: StorySheetContext
}>()
const emit = defineEmits<{ close: [] }>()

const title = useTileTitle(props)
</script>

<template>
  <BottomSheet :label="title" testid="story-sheet" panel-class="sm:max-w-lg" edge @close="emit('close')">
    <template #default="{ close }">
      <div class="px-4 pt-3 pb-4 max-h-[85vh] overflow-y-auto">
        <div class="w-10 h-1 rounded-full bg-gray-300 dark:bg-gray-600 mx-auto mb-3" />
        <StoryTileDetail :tile="tile" :context="context" transition-name />
        <SheetCloseButton class="mt-5" @click="close" />
      </div>
    </template>
  </BottomSheet>
</template>
