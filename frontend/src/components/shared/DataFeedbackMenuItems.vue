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
  <div class="mt-1 border-t border-gray-200 pt-1 dark:border-gray-700">
    <p class="px-3.5 pt-1.5 pb-0.5 text-[11.5px] font-semibold uppercase tracking-[0.06em] text-gray-500 dark:text-gray-400">{{ t('dashboard.data_feedback_label') }}</p>
    <button role="menuitemcheckbox" type="button" @click.stop="choose('POSITIVE')"
      :aria-checked="ratingOf(props.subjectType, props.subjectId) === 'POSITIVE'"
      :class="['flex min-h-11 w-full items-center gap-3 px-3.5 text-left text-[14px] transition hover:bg-gray-50 focus:outline-none focus-visible:bg-gray-100 dark:hover:bg-white/5 dark:focus-visible:bg-white/10',
        ratingOf(props.subjectType, props.subjectId) === 'POSITIVE' ? 'font-semibold text-green-700 dark:text-green-400' : 'font-medium text-gray-800 dark:text-gray-200']">
      <HandThumbUpIcon class="w-4 h-4 flex-shrink-0" aria-hidden="true" />{{ t('dashboard.data_feedback_menu_positive') }}
    </button>
    <button role="menuitemcheckbox" type="button" @click.stop="choose('NEGATIVE')"
      :aria-checked="ratingOf(props.subjectType, props.subjectId) === 'NEGATIVE'"
      :class="['flex min-h-11 w-full items-center gap-3 px-3.5 text-left text-[14px] transition hover:bg-gray-50 focus:outline-none focus-visible:bg-gray-100 dark:hover:bg-white/5 dark:focus-visible:bg-white/10',
        ratingOf(props.subjectType, props.subjectId) === 'NEGATIVE' ? 'font-semibold text-amber-700 dark:text-amber-400' : 'font-medium text-gray-800 dark:text-gray-200']">
      <HandThumbDownIcon class="w-4 h-4 flex-shrink-0" aria-hidden="true" />{{ t('dashboard.data_feedback_negative') }}
    </button>
  </div>
</template>
