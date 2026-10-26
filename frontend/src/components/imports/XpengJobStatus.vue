<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { CheckCircleIcon, ExclamationCircleIcon, ArrowRightIcon } from '@heroicons/vue/24/outline'
import { CI_BTN_PRIMARY, CI_LABEL, CI_CARD, CI_NOTE } from '../../utils/ciClasses'
import { RouterLink } from 'vue-router'
import type { XpengJobDto } from '../../api/xpengService'

const { t } = useI18n()

defineProps<{ job: XpengJobDto }>()

function statusLabel(status: string): string {
  return t(`xpeng.status_${status.toLowerCase()}`)
}

function statusBadgeClass(status: string): string {
  // App-CI: Grün nur für fertig (Ergebnis), Fehler fett statt rot, laufend grau
  const base = 'inline-block rounded-full px-2.5 py-0.5 text-[12px] font-semibold'
  if (status === 'DONE') return `${base} bg-green-50 text-green-800 dark:bg-green-900/20 dark:text-green-300`
  if (status === 'FAILED') return `${base} bg-gray-100 font-bold text-gray-900 dark:bg-gray-800 dark:text-gray-100`
  return `${base} bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-300`
}

function formatRange(start: string | null, end: string | null): string {
  if (!start || !end) return ''
  const s = new Date(start).toLocaleDateString()
  const e = new Date(end).toLocaleDateString()
  return `${s} - ${e}`
}
</script>

<template>
  <section :class="CI_CARD">
    <p :class="[CI_LABEL, 'mb-2']">
      {{ t('xpeng.import_in_progress') }}
    </p>

    <div class="flex items-center gap-3 mb-4">
      <span :class="statusBadgeClass(job.status)">{{ statusLabel(job.status) }}</span>
      <span v-if="job.dataRangeStart" class="text-[13px] tabular-nums text-gray-500 dark:text-gray-400">
        {{ formatRange(job.dataRangeStart, job.dataRangeEnd) }}
      </span>
    </div>

    <!-- DONE: stats + Dashboard-Link -->
    <div v-if="job.status === 'DONE'">
      <p class="mb-2 flex items-center gap-2 text-base font-bold text-gray-900 dark:text-gray-100">
        <CheckCircleIcon class="h-5 w-5 text-green-700 dark:text-green-400" aria-hidden="true" />
        {{ t('xpeng.done_title') }}
      </p>
      <ul class="mb-4 ml-7 space-y-0.5 text-sm tabular-nums text-gray-600 dark:text-gray-400">
        <li>{{ t('xpeng.summary_trips', job.importedTrips, { named: { count: job.importedTrips } }) }}</li>
        <li>{{ t('xpeng.summary_sessions', job.importedSessions, { named: { count: job.importedSessions } }) }}</li>
        <li v-if="job.skippedDuplicates > 0">{{ t('xpeng.summary_skipped', job.skippedDuplicates, { named: { count: job.skippedDuplicates } }) }}</li>
      </ul>
      <div v-if="job.importedTrips > 0 || job.importedSessions > 0" class="ml-7">
        <RouterLink to="/" :class="CI_BTN_PRIMARY">
          {{ t('xpeng.view_dashboard') }}<ArrowRightIcon class="h-4 w-4" aria-hidden="true" />
        </RouterLink>
      </div>
    </div>

    <!-- FAILED: error message -->
    <div v-else-if="job.status === 'FAILED'" role="alert"
         :class="[CI_NOTE, 'flex items-start gap-2 py-3']">
      <ExclamationCircleIcon class="mt-0.5 h-5 w-5 shrink-0 text-gray-600 dark:text-gray-300" aria-hidden="true" />
      <div class="text-sm font-semibold text-gray-900 dark:text-gray-100">{{ job.errorMessage }}</div>
    </div>
  </section>
</template>
