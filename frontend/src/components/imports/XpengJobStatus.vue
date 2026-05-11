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

function statusColor(status: string): string {
  if (status === 'DONE') return 'text-green-700 dark:text-green-300'
  if (status === 'FAILED') return 'text-red-700 dark:text-red-300'
  return 'text-blue-700 dark:text-blue-300'
}

function formatRange(start: string | null, end: string | null): string {
  if (!start || !end) return ''
  const s = new Date(start).toLocaleDateString()
  const e = new Date(end).toLocaleDateString()
  return `${s} - ${e}`
}
</script>

<template>
  <section class="border border-gray-200 dark:border-gray-700 rounded-lg p-4">
    <h3 class="text-base font-semibold mb-3">{{ t('xpeng.import_in_progress') }}</h3>

    <div class="text-sm space-y-2">
      <div class="flex items-center gap-2">
        <span class="text-gray-500 dark:text-gray-400">{{ t('xpeng.job_status') }}:</span>
        <span :class="statusColor(job.status)" class="font-medium">{{ statusLabel(job.status) }}</span>
      </div>

      <div v-if="job.dataRangeStart" class="text-xs text-gray-500 dark:text-gray-400">
        {{ t('xpeng.data_range') }}: {{ formatRange(job.dataRangeStart, job.dataRangeEnd) }}
      </div>

      <!-- DONE: stats + Link zu Trips/Logs -->
      <div v-if="job.status === 'DONE'" class="mt-3 space-y-2">
        <div class="flex items-start gap-2 text-green-800 dark:text-green-200">
          <CheckCircleIcon class="w-5 h-5 flex-shrink-0 mt-0.5" />
          <div class="text-sm">
            <p class="font-medium">{{ t('xpeng.done_title') }}</p>
            <ul class="mt-1 text-xs space-y-0.5 text-gray-600 dark:text-gray-400">
              <li>{{ t('xpeng.summary_trips', job.importedTrips, { named: { count: job.importedTrips } }) }}</li>
              <li>{{ t('xpeng.summary_sessions', job.importedSessions, { named: { count: job.importedSessions } }) }}</li>
              <li v-if="job.skippedDuplicates > 0">{{ t('xpeng.summary_skipped', job.skippedDuplicates, { named: { count: job.skippedDuplicates } }) }}</li>
            </ul>
          </div>
        </div>
        <div v-if="job.importedTrips > 0 || job.importedSessions > 0" class="pt-1">
          <RouterLink to="/"
                      class="text-sm text-blue-600 dark:text-blue-400 hover:underline">
            {{ t('xpeng.view_dashboard') }} →
          </RouterLink>
        </div>
      </div>

      <!-- FAILED: error message -->
      <div v-if="job.status === 'FAILED'" class="flex items-start gap-2 text-red-700 dark:text-red-300 mt-2">
        <ExclamationCircleIcon class="w-5 h-5 flex-shrink-0 mt-0.5" />
        <div class="text-sm">{{ job.errorMessage }}</div>
      </div>
    </div>
  </section>
</template>
