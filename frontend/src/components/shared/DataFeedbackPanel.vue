<script setup lang="ts">
import ChoiceChip from './ChoiceChip.vue'
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
       class="grid gap-2.5 rounded-2xl bg-gray-50 p-3 dark:bg-white/5">
    <div class="flex flex-wrap gap-1.5">
      <ChoiceChip v-for="tag in FEEDBACK_TAGS[props.subjectType]" :key="tag" size="sm" :active="draftTags.includes(tag)" @click="toggleTag(tag)">
        {{ t('dashboard.data_feedback_tag_' + tag.toLowerCase()) }}
      </ChoiceChip>
    </div>
    <textarea v-model="draftComment" rows="2" maxlength="1000" :placeholder="t('dashboard.data_feedback_comment')"
      :aria-label="t('dashboard.data_feedback_comment')"
      class="w-full resize-none rounded-xl border-2 border-gray-300 bg-white px-3 py-2 text-[15px] text-gray-900 placeholder-gray-400 outline-none focus:border-gray-900 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100 dark:focus:border-gray-300" />
    <div class="flex justify-end">
      <button type="button" @click="sendNegative(props.subjectType, props.subjectId)"
        class="btn-3d min-h-10 rounded-full border-2 border-gray-900 bg-gray-900 dark:border-edge px-4 text-[14px] font-semibold text-white dark:bg-gray-100 dark:text-gray-900">
        {{ t('dashboard.data_feedback_send') }}
      </button>
    </div>
  </div>
</template>
