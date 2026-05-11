<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  EnvelopeIcon,
  ClockIcon,
  ClipboardDocumentIcon,
  CheckIcon,
  SparklesIcon,
} from '@heroicons/vue/24/outline'

const { t } = useI18n()

const props = defineProps<{
  vin: string
}>()

const recipient = 'data-privacy@xiaopeng.com'

const subject = computed(() =>
  `Request for Telematics Data - VIN ${props.vin} (EU Data Act)`,
)
const body = computed(() => t('xpeng.mail_body', { vin: props.vin }))

const mailtoUrl = computed(() =>
  `mailto:${recipient}?subject=${encodeURIComponent(subject.value)}&body=${encodeURIComponent(body.value)}`,
)

const copyText = computed(() =>
  `To: ${recipient}\nSubject: ${subject.value}\n\n${body.value}`,
)

const copied = ref(false)
async function copyAll() {
  try {
    await navigator.clipboard.writeText(copyText.value)
    copied.value = true
    setTimeout(() => { copied.value = false }, 2000)
  } catch (e) {
    console.error('Copy failed', e)
  }
}
</script>

<template>
  <section class="space-y-4 border border-gray-200 dark:border-gray-700 rounded-lg p-4">
    <h3 class="text-base font-semibold flex items-center gap-2">
      <EnvelopeIcon class="w-5 h-5 text-blue-600 dark:text-blue-400" />
      {{ t('xpeng.step_request_title') }}
    </h3>

    <p class="text-sm text-gray-600 dark:text-gray-400">{{ t('xpeng.step_request_body') }}</p>

    <div class="flex flex-col sm:flex-row gap-2">
      <a :href="mailtoUrl" target="_blank" rel="noopener"
         class="btn-3d inline-flex items-center justify-center gap-2 bg-gray-900 hover:bg-gray-800 dark:bg-gray-700 dark:hover:bg-gray-600 text-white px-5 py-2.5 rounded-lg font-medium text-sm transition-colors flex-1">
        <EnvelopeIcon class="w-4 h-4" />
        {{ t('xpeng.btn_open_mail') }}
      </a>
      <button @click="copyAll"
              class="inline-flex items-center justify-center gap-2 border border-gray-300 dark:border-gray-600 text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-700/50 px-5 py-2.5 rounded-lg font-medium text-sm transition-colors flex-1">
        <component :is="copied ? CheckIcon : ClipboardDocumentIcon" class="w-4 h-4" />
        {{ copied ? t('common.copied') : t('xpeng.btn_copy_mail') }}
      </button>
    </div>

    <p class="text-xs text-gray-500 dark:text-gray-400 flex items-center gap-1">
      <ClockIcon class="w-4 h-4" />
      {{ t('xpeng.response_time_hint') }}
    </p>

    <!-- Phase-2 ankündigung -->
    <div class="rounded-md bg-amber-50 dark:bg-amber-900/20 p-3 border border-amber-200 dark:border-amber-800 flex gap-2">
      <SparklesIcon class="w-5 h-5 text-amber-600 dark:text-amber-400 flex-shrink-0" />
      <div class="text-xs text-gray-700 dark:text-gray-300">
        <p class="font-medium">{{ t('xpeng.phase2_promo_title') }}</p>
        <p class="text-gray-600 dark:text-gray-400 mt-0.5">{{ t('xpeng.phase2_promo_body') }}</p>
      </div>
    </div>
  </section>
</template>
