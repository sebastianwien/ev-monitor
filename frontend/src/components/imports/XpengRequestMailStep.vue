<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  EnvelopeIcon,
  ClockIcon,
  ClipboardDocumentIcon,
  CheckIcon,
  BoltIcon,
} from '@heroicons/vue/24/outline'
import type { XpengConnectionDto } from '../../api/xpengService'

const { t } = useI18n()

const props = defineProps<{
  vin: string
  connection?: XpengConnectionDto | null
}>()

const isAutoSyncActive = computed(() => props.connection?.autoSyncEnabled === true)

const nextRequestDate = computed(() => {
  if (!props.connection?.lastRequestSentAt) return null
  const last = new Date(props.connection.lastRequestSentAt)
  last.setDate(last.getDate() + 14)
  return last
})

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

function formatDate(iso: string | Date | null): string {
  if (!iso) return ''
  return new Date(iso).toLocaleDateString()
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

    <!-- AutoSync aktiv: Status-Card statt manuellem Mail-Step -->
    <template v-if="isAutoSyncActive">
      <div class="flex items-center gap-2 mb-3">
        <h3 class="text-lg md:text-xl font-bold text-gray-900 dark:text-white tracking-tight">
          {{ t('xpeng.autosync_status_title') }}
        </h3>
        <span class="inline-flex items-center gap-1 bg-green-100 dark:bg-green-950/60 text-green-700 dark:text-green-300 text-[10px] font-extrabold uppercase tracking-[0.1em] px-1.5 py-0.5 rounded-sm">
          <BoltIcon class="w-3 h-3" />
          {{ t('xpeng.autosync_active_badge') }}
        </span>
      </div>

      <p class="text-sm text-gray-600 dark:text-gray-400 leading-relaxed mb-4">
        {{ t('xpeng.autosync_status_body') }}
      </p>

      <dl class="text-sm space-y-1.5">
        <div v-if="connection?.lastRequestSentAt" class="flex gap-2">
          <dt class="text-gray-500 dark:text-gray-400 shrink-0">{{ t('xpeng.autosync_last_request', { date: formatDate(connection.lastRequestSentAt) }) }}</dt>
        </div>
        <div v-else class="text-gray-500 dark:text-gray-400 italic">
          {{ t('xpeng.autosync_no_request_yet') }}
        </div>
        <div v-if="nextRequestDate" class="text-gray-500 dark:text-gray-400">
          {{ t('xpeng.autosync_next_request', { date: formatDate(nextRequestDate) }) }}
        </div>
        <div v-if="connection?.xpengEmailMasked" class="flex gap-2">
          <dt class="text-gray-500 dark:text-gray-400 shrink-0">CC:</dt>
          <dd class="text-gray-800 dark:text-gray-200 font-mono">{{ connection.xpengEmailMasked }}</dd>
        </div>
      </dl>
    </template>

    <!-- Manueller Mail-Step (AutoSync inaktiv) -->
    <template v-else>
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
    </template>
  </section>
</template>
