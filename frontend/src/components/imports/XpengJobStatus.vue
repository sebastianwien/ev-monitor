<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { CheckCircleIcon, ExclamationCircleIcon } from '@heroicons/vue/24/outline'
import { RouterLink } from 'vue-router'
import type { XpengJobDto } from '../../api/xpengService'

const { t } = useI18n()

defineProps<{ job: XpengJobDto }>()

function statusLabel(status: string): string {
  return t(`xpeng.status_${status.toLowerCase()}`)
}

function statusBadgeClass(status: string): string {
  const base = 'inline-block text-[10px] font-extrabold uppercase tracking-[0.1em] px-2 py-1 rounded-sm border-2'
  if (status === 'DONE') return `${base} bg-green-50 dark:bg-green-950/40 text-green-700 dark:text-green-300 border-green-500 dark:border-green-700`
  if (status === 'FAILED') return `${base} bg-red-50 dark:bg-red-950/40 text-red-700 dark:text-red-300 border-red-500 dark:border-red-700`
  return `${base} bg-amber-50 dark:bg-amber-950/40 text-amber-700 dark:text-amber-300 border-amber-500 dark:border-amber-700`
}

function formatRange(start: string | null, end: string | null): string {
  if (!start || !end) return ''
  const s = new Date(start).toLocaleDateString()
  const e = new Date(end).toLocaleDateString()
  return `${s} - ${e}`
}
</script>

<template>
  <section
    class="rounded-sm border-2 border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-800 p-5 md:p-6 shadow-[4px_4px_0_0_#d1d5db] dark:shadow-[4px_4px_0_0_#374151]">
    <p class="text-amber-600 dark:text-amber-500 text-[11px] font-bold uppercase tracking-[0.14em] mb-2">
      {{ t('xpeng.import_in_progress') }}
    </p>

    <div class="flex items-center gap-3 mb-4">
      <span :class="statusBadgeClass(job.status)">{{ statusLabel(job.status) }}</span>
      <span v-if="job.dataRangeStart" class="text-xs text-gray-500 dark:text-gray-400 font-mono">
        {{ formatRange(job.dataRangeStart, job.dataRangeEnd) }}
      </span>
    </div>

    <!-- DONE: stats + Dashboard-Link -->
    <div v-if="job.status === 'DONE'">
      <p class="text-gray-900 dark:text-white font-bold text-base mb-2 flex items-center gap-2">
        <CheckCircleIcon class="w-5 h-5 text-green-600 dark:text-green-400" />
        {{ t('xpeng.done_title') }}
      </p>
      <ul class="text-sm text-gray-600 dark:text-gray-400 space-y-0.5 mb-4 ml-7">
        <li>{{ t('xpeng.summary_trips', job.importedTrips, { named: { count: job.importedTrips } }) }}</li>
        <li>{{ t('xpeng.summary_sessions', job.importedSessions, { named: { count: job.importedSessions } }) }}</li>
        <li v-if="job.skippedDuplicates > 0">{{ t('xpeng.summary_skipped', job.skippedDuplicates, { named: { count: job.skippedDuplicates } }) }}</li>
      </ul>
      <div v-if="job.importedTrips > 0 || job.importedSessions > 0" class="ml-7">
        <RouterLink to="/"
                    class="inline-flex items-center gap-1.5 text-amber-700 dark:text-amber-400 hover:text-amber-600 dark:hover:text-amber-300 font-bold uppercase tracking-wider text-xs">
          {{ t('xpeng.view_dashboard') }} →
        </RouterLink>
      </div>
    </div>

    <!-- FAILED: error message -->
    <div v-else-if="job.status === 'FAILED'"
         class="border-l-2 border-red-500 bg-red-50 dark:bg-red-950/40 px-4 py-3 rounded-r-sm flex items-start gap-2">
      <ExclamationCircleIcon class="w-5 h-5 flex-shrink-0 text-red-600 dark:text-red-400 mt-0.5" />
      <div class="text-sm text-red-900 dark:text-red-200">{{ job.errorMessage }}</div>
    </div>
  </section>
</template>
