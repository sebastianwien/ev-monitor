<script setup lang="ts">
import { computed, ref } from 'vue'
import { ArrowTopRightOnSquareIcon, PaperAirplaneIcon } from '@heroicons/vue/24/outline'
import { submitSeoIndexNow, type SeoIndexInfo } from '../../../api/seoService'
import { gscInspectUrl, indexState } from './seoAudit'
import { date, dateTime } from './seoFormat'

/**
 * Index-Stand einer Seite plus die zwei Wege, sie in den Index zu bringen: Search Console öffnen (dort
 * "Indexierung beantragen") und IndexNow-Meldung an Bing, Yandex und Seznam.
 */
const props = defineProps<{ path: string; index: SeoIndexInfo | null | undefined }>()

const state = computed(() => indexState(props.index))
const sending = ref(false)
const result = ref('')
const sentAt = ref<string | null>(null)
const lastSent = computed(() => sentAt.value ?? props.index?.indexnowAt ?? null)

const MESSAGES = {
  SUBMITTED: 'An Bing und Co. gemeldet.',
  DISABLED: 'IndexNow ist nicht eingerichtet (INDEXNOW_KEY fehlt).',
  REJECTED: 'IndexNow hat die Meldung abgelehnt, später noch einmal versuchen.',
  NOT_ALLOWED: 'Nur indexierbare Seiten aus der Sitemap lassen sich melden.',
} as const

async function submit() {
  sending.value = true
  result.value = ''
  try {
    const r = await submitSeoIndexNow(props.path)
    result.value = MESSAGES[r]
    if (r === 'SUBMITTED') sentAt.value = new Date().toISOString()
  } catch {
    result.value = 'Die Meldung konnte nicht gesendet werden.'
  } finally {
    sending.value = false
  }
}

const action = 'inline-flex items-center gap-1 rounded-sm bg-gray-800 px-2 py-1 text-xs text-gray-200 hover:bg-gray-700 focus:outline-none focus:ring-2 focus:ring-indigo-500 disabled:opacity-50'
</script>

<template>
  <div class="flex flex-wrap items-center gap-2 text-xs">
    <span v-if="state === 'INDEXED'" class="rounded-full bg-emerald-900/60 px-2 py-0.5 text-emerald-200">Im Google-Index</span>
    <span v-else-if="state === 'NOT_INDEXED'" class="rounded-full bg-red-900/60 px-2 py-0.5 text-red-200" :title="index?.coverageState ?? ''">
      Nicht im Google-Index<template v-if="index?.coverageState">: {{ index.coverageState }}</template>
    </span>
    <span v-else class="rounded-full bg-gray-800 px-2 py-0.5 text-gray-400">Index nicht geprüft</span>
    <span v-if="index?.lastCrawlAt" class="text-gray-500">Google-Crawl {{ date(index.lastCrawlAt) }}</span>

    <a :href="gscInspectUrl(path)" target="_blank" rel="noopener" :class="action" @click.stop>
      In Search Console prüfen <ArrowTopRightOnSquareIcon class="h-3.5 w-3.5" aria-hidden="true" />
    </a>
    <button type="button" :class="action" :disabled="sending" @click.stop="submit">
      <PaperAirplaneIcon class="h-3.5 w-3.5" aria-hidden="true" /> {{ sending ? 'Meldet...' : 'Bei Bing melden' }}
    </button>
    <span v-if="result" class="text-gray-300" role="status">{{ result }}</span>
    <span v-else-if="lastSent" class="text-gray-500">IndexNow {{ dateTime(lastSent) }}</span>
  </div>
</template>
