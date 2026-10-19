<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useDataFeedback, FEEDBACK_TAGS, type FeedbackSubjectType } from '../../composables/useDataFeedback'

// Erscheint nach Daumen runter: was nicht stimmte, optional mit Kommentar.
// @click.stop, sonst klappt jeder Tipp ins Panel die Karte um.
const props = defineProps<{ subjectType: FeedbackSubjectType; subjectId: string }>()
const { t } = useI18n()
const { isPanelOpen, toggleTag, sendNegative, draftTags, draftComment } = useDataFeedback()
</script>

<template>
  <div v-if="isPanelOpen(props.subjectType, props.subjectId)" @click.stop
       class="pt-1 space-y-2 border-t border-gray-100 dark:border-gray-600">
    <div class="flex flex-wrap gap-1">
      <button v-for="tag in FEEDBACK_TAGS[props.subjectType]" :key="tag" type="button" @click="toggleTag(tag)"
        :aria-pressed="draftTags.includes(tag)"
        :class="['px-2 py-0.5 text-xs rounded-full border transition',
          draftTags.includes(tag)
            ? 'bg-red-100 border-red-300 text-red-700 dark:bg-red-900/30 dark:border-red-700 dark:text-red-400'
            : 'bg-white dark:bg-gray-800 border-gray-200 dark:border-gray-600 text-gray-500 dark:text-gray-400 hover:border-red-300']">
        {{ t('dashboard.data_feedback_tag_' + tag.toLowerCase()) }}
      </button>
    </div>
    <textarea v-model="draftComment" rows="2" maxlength="1000" :placeholder="t('dashboard.data_feedback_comment')"
      :aria-label="t('dashboard.data_feedback_comment')"
      class="w-full px-2 py-1.5 text-xs border border-gray-200 dark:border-gray-600 rounded-sm bg-white dark:bg-gray-800 text-gray-700 dark:text-gray-300 placeholder-gray-400 focus:outline-none focus:ring-1 focus:ring-red-400 resize-none" />
    <div class="flex justify-end">
      <button type="button" @click="sendNegative(props.subjectType, props.subjectId)"
        class="px-3 py-1 text-xs font-medium bg-red-500 hover:bg-red-600 text-white rounded-sm transition">
        {{ t('dashboard.data_feedback_send') }}
      </button>
    </div>
  </div>
</template>
