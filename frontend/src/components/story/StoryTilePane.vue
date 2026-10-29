<script setup lang="ts">
import { ref, watch, nextTick, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ChevronLeftIcon, XMarkIcon } from '@heroicons/vue/24/outline'
import StoryTileDetail, { type StorySheetContext } from './StoryTileDetail.vue'
import type { StoryTile } from '../../composables/storyModel'

/**
 * Detail einer Kachel ab lg in der rechten Spalte statt als Sheet: nicht modal, die Seite bleibt
 * sichtbar und bedienbar. Zurück, × oder Escape schließen; Pfeil links und rechts blättern zur
 * vorigen und nächsten Kachel. Tasten gehören einem offenen Dialog oder Eingabefeld, nicht der Spalte.
 */
const props = defineProps<{
  tile: StoryTile
  context: StorySheetContext
  /** Rückweg zur Spalte, z. B. "Diesen Monat". */
  backLabel: string
}>()
const emit = defineEmits<{ close: []; step: [dir: 1 | -1] }>()
const { t } = useI18n()

// Fokus auf den Titel, ohne die Seite zu verschieben: Screenreader lesen das neue Detail vor.
const root = ref<HTMLElement | null>(null)
const focusTitle = () => nextTick(() => root.value?.querySelector<HTMLElement>('h3')?.focus({ preventScroll: true }))
watch(() => props.tile.id, focusTitle)
onMounted(focusTitle)

function onKey(e: KeyboardEvent) {
  if (e.defaultPrevented || e.altKey || e.ctrlKey || e.metaKey || e.shiftKey) return
  if (document.querySelector('[aria-modal="true"]')) return
  const el = e.target as HTMLElement | null
  if (el?.closest?.('input, textarea, select, [contenteditable="true"]')) return
  if (e.key === 'Escape') emit('close')
  else if (e.key === 'ArrowRight') emit('step', 1)
  else if (e.key === 'ArrowLeft') emit('step', -1)
  else return
  e.preventDefault()
}
onMounted(() => document.addEventListener('keydown', onKey))
onUnmounted(() => document.removeEventListener('keydown', onKey))

const quiet = 'inline-flex min-h-11 items-center rounded-lg text-sm font-semibold text-gray-700 hover:text-gray-900 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-green-600 dark:text-gray-200 dark:hover:text-white'
</script>

<template>
  <section ref="root" class="grid content-start gap-2" data-testid="story-pane">
    <div class="flex items-center justify-between gap-3">
      <button type="button" :class="quiet" class="-ml-1 gap-1 pr-2" data-testid="story-pane-back" @click="emit('close')">
        <ChevronLeftIcon class="h-4 w-4" aria-hidden="true" />{{ backLabel }}
      </button>
      <button type="button" :class="quiet" class="-mr-2 h-11 w-11 justify-center" :aria-label="t('story.sheet_close')" data-testid="story-pane-close" @click="emit('close')">
        <XMarkIcon class="h-5 w-5" aria-hidden="true" />
      </button>
    </div>
    <div class="rounded-2xl bg-white p-4 edge-card dark:bg-gray-700/50">
      <StoryTileDetail :tile="tile" :context="context" />
    </div>
  </section>
</template>
