<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { HandThumbUpIcon, HandThumbDownIcon } from '@heroicons/vue/24/outline'
import { useDataFeedback, type FeedbackRating, type FeedbackSubjectType } from '../../composables/useDataFeedback'

// "Korrekt erkannt?" als Einträge im Aktionsmenü der Desktop-Zeile, wo neben dem Menü kein
// Platz für Daumen ist. Gleicher Zustand wie DataFeedbackButtons; das Panel kommt unter die Zeile.
const props = defineProps<{ subjectType: FeedbackSubjectType; subjectId: string }>()
const emit = defineEmits<{ done: [] }>()
const { t } = useI18n()
const { ratingOf, toggle } = useDataFeedback()

const choose = (rating: FeedbackRating) => {
  toggle(props.subjectType, props.subjectId, rating)
  emit('done')
}
</script>

<template>
  <div class="border-t border-gray-100 dark:border-gray-600 mt-1 pt-1">
    <p class="px-3 pt-1 pb-0.5 text-xs text-gray-400 dark:text-gray-500">{{ t('dashboard.data_feedback_label') }}</p>
    <button role="menuitemcheckbox" type="button" @click.stop="choose('POSITIVE')"
      :aria-checked="ratingOf(props.subjectType, props.subjectId) === 'POSITIVE'"
      :class="['w-full flex items-center gap-2.5 px-3 py-2 text-sm hover:bg-gray-50 dark:hover:bg-gray-600 transition focus:outline-none focus-visible:bg-gray-100 dark:focus-visible:bg-gray-600',
        ratingOf(props.subjectType, props.subjectId) === 'POSITIVE' ? 'text-emerald-600 dark:text-emerald-400 font-medium' : 'text-gray-700 dark:text-gray-200']">
      <HandThumbUpIcon class="w-4 h-4 flex-shrink-0" aria-hidden="true" />{{ t('dashboard.data_feedback_menu_positive') }}
    </button>
    <button role="menuitemcheckbox" type="button" @click.stop="choose('NEGATIVE')"
      :aria-checked="ratingOf(props.subjectType, props.subjectId) === 'NEGATIVE'"
      :class="['w-full flex items-center gap-2.5 px-3 py-2 text-sm hover:bg-gray-50 dark:hover:bg-gray-600 transition focus:outline-none focus-visible:bg-gray-100 dark:focus-visible:bg-gray-600',
        ratingOf(props.subjectType, props.subjectId) === 'NEGATIVE' ? 'text-red-600 dark:text-red-400 font-medium' : 'text-gray-700 dark:text-gray-200']">
      <HandThumbDownIcon class="w-4 h-4 flex-shrink-0" aria-hidden="true" />{{ t('dashboard.data_feedback_negative') }}
    </button>
  </div>
</template>
