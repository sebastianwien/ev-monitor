<script setup lang="ts">
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { storeToRefs } from 'pinia'
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
     :class="aboveCaptureBar ? 'bottom-[calc(8.5rem+env(safe-area-inset-bottom))]' : 'bottom-[calc(4.5rem+env(safe-area-inset-bottom))]'"
     class="btn-3d fixed right-4 z-40 md:bottom-6 inline-flex items-center h-8 px-2.5 rounded-lg border-2 border-edge bg-green-600 text-xs before:absolute before:-inset-1.5 before:content-[''] font-semibold text-white hover:bg-green-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-green-500 focus-visible:ring-offset-2 dark:focus-visible:ring-offset-gray-900">
    {{ t('previewFeedback.button') }}
  </a>
</template>
