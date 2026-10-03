<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { StoryMonth } from '../../composables/useDashboardStory'

/** Drei Monats-Chips. Das Wischen auf dem Hero steuert dieselbe Auswahl (siehe View). */
defineProps<{
  months: StoryMonth[]
  offset: number
  labelOf: (m: StoryMonth) => string
}>()
const emit = defineEmits<{ select: [offset: number] }>()
const { t } = useI18n()
</script>

<template>
  <div role="tablist" :aria-label="t('story.month_aria')" class="flex gap-1 overflow-x-auto scrollbar-hide">
    <button
      v-for="m in months" :key="m.offset"
      type="button" role="tab"
      :aria-selected="m.offset === offset"
      :data-testid="`story-month-${m.offset}`"
      class="min-h-[44px] px-2.5 text-[13px] font-semibold border-b-2 transition-colors whitespace-nowrap"
      :class="m.offset === offset
        ? 'text-gray-900 dark:text-gray-50 border-story-accent dark:border-story-accent-dark'
        : 'text-gray-500 dark:text-gray-400 border-transparent'"
      @click="emit('select', m.offset)">
      {{ labelOf(m) }}
    </button>
  </div>
</template>
