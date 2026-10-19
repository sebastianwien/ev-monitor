<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { HandThumbUpIcon, HandThumbDownIcon } from '@heroicons/vue/24/outline'
import { useDataFeedback, type FeedbackSubjectType } from '../../composables/useDataFeedback'

// Daumen hoch/runter für "Korrekt erkannt?". Das Problem-Panel ist DataFeedbackPanel,
// getrennt, damit es unter der Aktionsleiste die volle Breite bekommt.
const props = defineProps<{ subjectType: FeedbackSubjectType; subjectId: string }>()
const { t } = useI18n()
const { ratingOf, toggle } = useDataFeedback()
</script>

<template>
  <button type="button" @click="toggle(props.subjectType, props.subjectId, 'POSITIVE')"
    :aria-label="t(`dashboard.data_feedback_positive_${props.subjectType.toLowerCase()}`)"
    :title="t(`dashboard.data_feedback_positive_${props.subjectType.toLowerCase()}`)"
    :aria-pressed="ratingOf(props.subjectType, props.subjectId) === 'POSITIVE'"
    :class="['p-2 rounded transition hover:bg-gray-100 dark:hover:bg-gray-600',
      ratingOf(props.subjectType, props.subjectId) === 'POSITIVE' ? 'text-emerald-600 dark:text-emerald-400' : 'text-gray-400 hover:text-gray-600 dark:hover:text-gray-200']">
    <HandThumbUpIcon class="w-4 h-4" />
  </button>
  <button type="button" @click="toggle(props.subjectType, props.subjectId, 'NEGATIVE')"
    :aria-label="t('dashboard.data_feedback_negative')" :title="t('dashboard.data_feedback_negative')"
    :aria-pressed="ratingOf(props.subjectType, props.subjectId) === 'NEGATIVE'"
    :class="['p-2 rounded transition hover:bg-gray-100 dark:hover:bg-gray-600',
      ratingOf(props.subjectType, props.subjectId) === 'NEGATIVE' ? 'text-red-600 dark:text-red-400' : 'text-gray-400 hover:text-gray-600 dark:hover:text-gray-200']">
    <HandThumbDownIcon class="w-4 h-4" />
  </button>
</template>
