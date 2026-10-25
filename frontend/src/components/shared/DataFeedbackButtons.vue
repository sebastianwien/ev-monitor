<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { HandThumbUpIcon, HandThumbDownIcon } from '@heroicons/vue/24/outline'
import { useDataFeedback, type FeedbackSubjectType } from '../../composables/useDataFeedback'

// Daumen hoch/runter für "Korrekt erkannt?". Das Problem-Panel ist DataFeedbackPanel,
// getrennt, damit es unter der Aktionsleiste die volle Breite bekommt.
// compact: für die zweite Zeile der Desktop-Ladezeile, damit sie nicht höher wird.
const props = withDefaults(defineProps<{ subjectType: FeedbackSubjectType; subjectId: string; compact?: boolean }>(), { compact: false })
const { t } = useI18n()
const { ratingOf, toggle } = useDataFeedback()
const pad = props.compact ? 'p-1' : 'p-2'
const icon = props.compact ? 'w-3.5 h-3.5' : 'w-4 h-4'
</script>

<template>
  <button type="button" @click="toggle(props.subjectType, props.subjectId, 'POSITIVE')"
    :aria-label="t(`dashboard.data_feedback_positive_${props.subjectType.toLowerCase()}`)"
    :title="t(`dashboard.data_feedback_positive_${props.subjectType.toLowerCase()}`)"
    :aria-pressed="ratingOf(props.subjectType, props.subjectId) === 'POSITIVE'"
    :class="[pad, 'rounded-full transition hover:bg-gray-100 dark:hover:bg-white/10',
      ratingOf(props.subjectType, props.subjectId) === 'POSITIVE' ? 'text-green-700 dark:text-green-400' : 'text-gray-400 hover:text-gray-700 dark:hover:text-gray-200']">
    <HandThumbUpIcon :class="icon" aria-hidden="true" />
  </button>
  <button type="button" @click="toggle(props.subjectType, props.subjectId, 'NEGATIVE')"
    :aria-label="t('dashboard.data_feedback_negative')" :title="t('dashboard.data_feedback_negative')"
    :aria-pressed="ratingOf(props.subjectType, props.subjectId) === 'NEGATIVE'"
    :class="[pad, 'rounded-full transition hover:bg-gray-100 dark:hover:bg-white/10',
      ratingOf(props.subjectType, props.subjectId) === 'NEGATIVE' ? 'text-amber-700 dark:text-amber-400' : 'text-gray-400 hover:text-gray-700 dark:hover:text-gray-200']">
    <HandThumbDownIcon :class="icon" aria-hidden="true" />
  </button>
</template>
