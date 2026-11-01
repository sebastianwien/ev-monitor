<script setup lang="ts">
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { storeToRefs } from 'pinia'
import { ChatBubbleLeftEllipsisIcon } from '@heroicons/vue/24/outline'
import { useThemeStore } from '../../stores/theme'
import { previewFeedbackMailto } from '../../utils/previewFeedback'

defineProps<{ aboveCaptureBar?: boolean }>()

const FEEDBACK_MAIL = 'support@ev-monitor.net'

const { t } = useI18n()
const route = useRoute()
const { isDark } = storeToRefs(useThemeStore())

// Erst beim Tipp gebaut: Breite (Drehen, Fenstergröße) und Seite zeigen den Moment des Feedbacks
function buildHref(): string {
  return previewFeedbackMailto({
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
  })
}

function onClick(e: MouseEvent) {
  (e.currentTarget as HTMLAnchorElement).href = buildHref()
}
</script>

<template>
  <a :href="`mailto:${FEEDBACK_MAIL}`"
     @click="onClick"
     :aria-label="t('previewFeedback.button')"
          :class="aboveCaptureBar ? 'bottom-[calc(8.5rem+env(safe-area-inset-bottom))]' : 'bottom-[calc(4.5rem+env(safe-area-inset-bottom))]'"
     class="fixed right-4 z-40 md:bottom-6 inline-flex items-center gap-2 rounded-full bg-green-600 px-4 py-2.5 text-sm font-medium text-white shadow-lg hover:bg-green-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-green-500 focus-visible:ring-offset-2 dark:focus-visible:ring-offset-gray-900">
    <ChatBubbleLeftEllipsisIcon class="h-5 w-5" aria-hidden="true" />
    {{ t('previewFeedback.button') }}
  </a>
</template>
