<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import ChoiceChip from '../shared/ChoiceChip.vue'
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
  <div role="group" :aria-label="t('story.month_aria')" class="flex shrink-0 gap-1.5">
    <ChoiceChip
      v-for="m in months" :key="m.offset"
      :active="m.offset === offset"
      size="sm"
      :data-testid="`story-month-${m.offset}`"
      @click="emit('select', m.offset)">
      {{ labelOf(m) }}
    </ChoiceChip>
  </div>
</template>
