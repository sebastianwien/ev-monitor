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
  <section
    class="rounded-sm border-2 border-amber-500 dark:border-amber-500 bg-white dark:bg-gray-800 p-5 md:p-6 shadow-[2px_2px_0_0_#f59e0b] dark:shadow-[2px_2px_0_0_#f59e0b]">
    <!-- Step badge + eyebrow -->
    <p class="text-amber-600 dark:text-amber-500 text-[11px] font-bold uppercase tracking-[0.14em] mb-2 flex items-center gap-2">
      <span class="inline-flex w-5 h-5 bg-amber-500 text-gray-950 rounded-sm items-center justify-center text-[11px] font-extrabold">1</span>
      {{ t('xpeng.eyebrow_step1') }}
    </p>

    <h3 class="text-lg md:text-xl font-bold text-gray-900 dark:text-white tracking-tight mb-3">
      {{ t('xpeng.step_request_title') }}
    </h3>

    <p class="text-sm text-gray-600 dark:text-gray-400 leading-relaxed mb-5">
      {{ t('xpeng.step_request_body') }}
    </p>

    <div class="flex flex-col sm:flex-row gap-3">
      <a :href="mailtoUrl" target="_blank" rel="noopener"
         class="inline-flex items-center justify-center gap-2 bg-amber-500 hover:bg-amber-400 text-gray-950 font-bold uppercase tracking-wider text-xs px-5 py-3 rounded-sm border-2 border-amber-500 shadow-[2px_2px_0_0_#030712] active:translate-x-[3px] active:translate-y-[3px] active:shadow-none transition-[transform,box-shadow] duration-75">
        <EnvelopeIcon class="w-4 h-4" />
        {{ t('xpeng.btn_open_mail') }}
      </a>
      <button @click="copyAll"
              class="inline-flex items-center justify-center gap-2 bg-transparent border-2 border-gray-400 dark:border-gray-600 text-gray-800 dark:text-gray-100 hover:bg-gray-50 dark:hover:bg-gray-700/40 font-bold uppercase tracking-wider text-xs px-5 py-3 rounded-sm shadow-[2px_2px_0_0_#9ca3af] dark:shadow-[2px_2px_0_0_#4b5563] active:translate-x-[3px] active:translate-y-[3px] active:shadow-none transition-[transform,box-shadow] duration-75">
        <component :is="copied ? CheckIcon : ClipboardDocumentIcon" class="w-4 h-4" />
        {{ copied ? t('common.copied') : t('xpeng.btn_copy_mail') }}
      </button>
    </div>

    <p class="text-xs text-gray-500 dark:text-gray-400 flex items-center gap-1.5 mt-4">
      <ClockIcon class="w-3.5 h-3.5" />
      {{ t('xpeng.response_time_hint') }}
    </p>

    <!-- Phase-2 callout: amber border-left + tinted bg -->
    <div class="mt-5 border-l-2 border-amber-500 bg-amber-50 dark:bg-amber-950/40 px-4 py-3 rounded-r-sm">
      <p class="text-amber-700 dark:text-amber-400 text-xs font-bold uppercase tracking-wider mb-1 flex items-center gap-1.5">
        <SparklesIcon class="w-3.5 h-3.5" />
        {{ t('xpeng.phase2_promo_title') }}
      </p>
      <p class="text-[13px] text-amber-900/80 dark:text-amber-200/80 leading-relaxed">
        {{ t('xpeng.phase2_promo_body') }}
      </p>
    </div>
  </section>
</template>
