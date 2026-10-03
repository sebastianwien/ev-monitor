<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import StoryTile from './StoryTile.vue'
import type { StoryTile as Tile, TileId } from '../../composables/storyModel'

/**
 * Die Kachelreihe: horizontal wischbar mit Scroll-Snap auf Mobile, Raster ab Desktop.
 * Darunter ein Punkte-Indikator, der die Position zeigt und per Tipp springt.
 */
const props = defineProps<{ tiles: Tile[] }>()
const emit = defineEmits<{ open: [id: TileId] }>()

const { t } = useI18n()
const scroller = ref<HTMLElement | null>(null)
const active = ref(0)

// scrollsnapchange liefert das eingerastete Element direkt; aeltere Browser rechnen aus scrollLeft.
const hasSnapEvent = typeof window !== 'undefined' && 'onscrollsnapchange' in window

function onScroll() {
  if (hasSnapEvent) return
  const el = scroller.value
  if (!el || props.tiles.length === 0) return
  const step = el.scrollWidth / props.tiles.length
  active.value = Math.min(props.tiles.length - 1, Math.max(0, Math.round(el.scrollLeft / step)))
}

function onSnapChange(e: Event) {
  const target = (e as Event & { snapTargetInline?: Element | null }).snapTargetInline
  const el = scroller.value
  if (!target || !el) return
  const i = Array.prototype.indexOf.call(el.children, target)
  if (i >= 0) active.value = i
}

function jumpTo(i: number) {
  const el = scroller.value?.children[i] as HTMLElement | undefined
  el?.scrollIntoView({ inline: 'start', block: 'nearest', behavior: 'smooth' })
}

const showDots = computed(() => props.tiles.length > 1)
</script>

<template>
  <div>
    <div
      ref="scroller"
      class="flex gap-2.5 overflow-x-auto snap-x snap-mandatory -mx-4 px-4 pt-4 pb-2 scrollbar-hide md:grid md:grid-cols-2 md:overflow-visible md:mx-0 md:px-0 md:pb-4 md:[&>:last-child:nth-child(odd)]:col-span-2"
      data-testid="story-tiles"
      @scroll.passive="onScroll"
      @scrollsnapchange="onSnapChange">
      <StoryTile v-for="tile in tiles" :key="tile.id" :tile="tile" @open="emit('open', $event)" />
    </div>
    <div v-if="showDots" class="flex justify-center gap-1.5 pb-3 md:hidden" role="tablist">
      <button
        v-for="(tile, i) in tiles" :key="tile.id"
        type="button"
        role="tab"
        :aria-selected="i === active"
        :aria-label="t('story.tiles_aria', { n: i + 1, total: tiles.length })"
        class="min-w-[44px] min-h-[44px] -my-4 grid place-items-center"
        @click="jumpTo(i)">
        <span class="block h-1.5 rounded-full transition-all duration-300" :class="i === active ? 'w-5 bg-story-accent dark:bg-story-accent-dark' : 'w-1.5 bg-gray-300 dark:bg-gray-600'" />
      </button>
    </div>
  </div>
</template>
