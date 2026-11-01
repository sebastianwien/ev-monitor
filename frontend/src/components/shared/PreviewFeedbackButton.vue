<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { storeToRefs } from 'pinia'
import { ChatBubbleLeftEllipsisIcon } from '@heroicons/vue/24/outline'
import { useThemeStore } from '../../stores/theme'
import { previewFeedbackMailto } from '../../utils/previewFeedback'

const FEEDBACK_MAIL = 'support@ev-monitor.net'

const { t } = useI18n()
const route = useRoute()
const { isDark } = storeToRefs(useThemeStore())

// Beim Tipp neu berechnet: Breite und Seite sollen den Moment des Feedbacks zeigen
const href = computed(() => previewFeedbackMailto({
  to: FEEDBACK_MAIL,
  path: route.fullPath,
  width: window.innerWidth,
  dark: isDark.value,
  userAgent: navigator.userAgent,
  texts: {
    subject: t('previewFeedback.subject'),
    prompt: t('previewFeedback.prompt'),
    screenshot: t('previewFeedback.screenshot'),
    keep: t('previewFeedback.keep'),
  },
}))
</script>

<template>
  <a :href="href"
     :aria-label="t('previewFeedback.button')"
     class="fixed right-4 z-40 bottom-[calc(4.5rem+env(safe-area-inset-bottom))] md:bottom-6 inline-flex items-center gap-2 rounded-full bg-green-600 px-4 py-2.5 text-sm font-medium text-white shadow-lg hover:bg-green-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-green-500 focus-visible:ring-offset-2 dark:focus-visible:ring-offset-gray-900">
    <ChatBubbleLeftEllipsisIcon class="h-5 w-5" aria-hidden="true" />
    {{ t('previewFeedback.button') }}
  </a>
</template>
