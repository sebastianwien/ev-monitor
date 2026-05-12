<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { TrashIcon } from '@heroicons/vue/24/outline'
import xpengService, { type XpengConnectionDto, type XpengJobDto } from '../../api/xpengService'

const { t } = useI18n()

const props = defineProps<{
  connections: XpengConnectionDto[]
  jobs: XpengJobDto[]
}>()

const emit = defineEmits<{
  (e: 'refresh'): void
  (e: 'error', msg: string): void
}>()

function statusLabel(status: string): string {
  return t(`xpeng.status_${status.toLowerCase()}`)
}

function statusBadgeClass(status: string): string {
  const base = 'inline-block text-[10px] font-extrabold uppercase tracking-[0.1em] px-1.5 py-0.5 rounded-sm'
  if (status === 'DONE') return `${base} bg-green-100 dark:bg-green-950/60 text-green-700 dark:text-green-300`
  if (status === 'FAILED') return `${base} bg-red-100 dark:bg-red-950/60 text-red-700 dark:text-red-300`
  return `${base} bg-amber-100 dark:bg-amber-950/60 text-amber-700 dark:text-amber-300`
}

function formatDate(iso: string | null): string {
  if (!iso) return '-'
  return new Date(iso).toLocaleString()
}

async function revoke(connectionId: string) {
  if (!confirm(t('xpeng.confirm_revoke'))) return
  try {
    await xpengService.revoke(connectionId)
    emit('refresh')
  } catch (e: unknown) {
    const err = e as { response?: { data?: { error?: string } }; message?: string }
    emit('error', err.response?.data?.error ?? err.message ?? 'Unknown error')
  }
}
</script>

<template>
  <div class="space-y-5">
    <!-- Active connections -->
    <section v-if="connections.length > 0"
             class="rounded-sm border-2 border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-800 p-5 md:p-6 shadow-[4px_4px_0_0_#d1d5db] dark:shadow-[4px_4px_0_0_#374151]">
      <p class="text-amber-600 dark:text-amber-500 text-[11px] font-bold uppercase tracking-[0.14em] mb-3">
        {{ t('xpeng.connections_title') }}
      </p>
      <div class="divide-y divide-gray-200 dark:divide-gray-700">
        <div v-for="c in connections" :key="c.id"
             class="flex items-center justify-between py-3 first:pt-0 last:pb-0">
          <div class="min-w-0">
            <div class="font-mono text-sm font-semibold text-gray-900 dark:text-white">{{ c.vinMasked }}</div>
            <div class="text-xs text-gray-500 dark:text-gray-400 mt-0.5">
              {{ t('xpeng.imports_count', c.totalImportsCount, { named: { count: c.totalImportsCount } }) }}
              <span v-if="c.lastSuccessfulImportAt"> · {{ t('xpeng.last_import') }}: {{ formatDate(c.lastSuccessfulImportAt) }}</span>
            </div>
          </div>
          <button @click="revoke(c.id)"
                  class="inline-flex items-center justify-center w-8 h-8 border-2 border-red-300 dark:border-red-800 text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-950/40 rounded-sm shadow-[2px_2px_0_0_#fca5a5] dark:shadow-[2px_2px_0_0_#7f1d1d] active:translate-x-[2px] active:translate-y-[2px] active:shadow-none transition-[transform,box-shadow] duration-75"
                  :title="t('xpeng.btn_revoke')">
            <TrashIcon class="w-4 h-4" />
          </button>
        </div>
      </div>
    </section>

    <!-- Job history -->
    <section v-if="jobs.length > 0"
             class="rounded-sm border-2 border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-800 p-5 md:p-6 shadow-[4px_4px_0_0_#d1d5db] dark:shadow-[4px_4px_0_0_#374151]">
      <p class="text-amber-600 dark:text-amber-500 text-[11px] font-bold uppercase tracking-[0.14em] mb-3">
        {{ t('xpeng.history_title') }}
      </p>
      <div class="divide-y divide-gray-200 dark:divide-gray-700 max-h-72 overflow-y-auto">
        <div v-for="j in jobs" :key="j.id"
             class="py-3 first:pt-0 last:pb-0">
          <div class="flex items-center justify-between gap-3">
            <span class="font-mono text-xs text-gray-600 dark:text-gray-400">{{ formatDate(j.createdAt) }}</span>
            <span :class="statusBadgeClass(j.status)">{{ statusLabel(j.status) }}</span>
          </div>
          <div v-if="j.status === 'DONE'" class="text-sm text-gray-800 dark:text-gray-200 mt-1 font-mono">
            {{ j.importedTrips }} <span class="text-gray-400 dark:text-gray-500">·</span> {{ j.importedSessions }}
          </div>
          <div v-if="j.errorMessage" class="text-[12px] font-mono text-red-600 dark:text-red-400 mt-1">
            err: {{ j.errorMessage }}
          </div>
        </div>
      </div>
    </section>
  </div>
</template>
