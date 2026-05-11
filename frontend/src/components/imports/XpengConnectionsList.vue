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

function statusColor(status: string): string {
  if (status === 'DONE') return 'text-green-600 dark:text-green-400'
  if (status === 'FAILED') return 'text-red-600 dark:text-red-400'
  return 'text-blue-600 dark:text-blue-400'
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
  <div class="space-y-4">
    <!-- Active connections -->
    <section v-if="connections.length > 0" class="border border-gray-200 dark:border-gray-700 rounded-lg p-4">
      <h3 class="text-base font-semibold mb-3">{{ t('xpeng.connections_title') }}</h3>
      <div class="space-y-2">
        <div v-for="c in connections" :key="c.id"
             class="flex items-center justify-between text-sm border-b border-gray-100 dark:border-gray-800 last:border-0 pb-2">
          <div>
            <div class="font-mono text-xs">{{ c.vinMasked }}</div>
            <div class="text-xs text-gray-500 dark:text-gray-400">
              {{ t('xpeng.imports_count', c.totalImportsCount, { named: { count: c.totalImportsCount } }) }}
              <span v-if="c.lastSuccessfulImportAt"> - {{ t('xpeng.last_import') }}: {{ formatDate(c.lastSuccessfulImportAt) }}</span>
            </div>
          </div>
          <button @click="revoke(c.id)"
                  class="text-red-600 dark:text-red-400 hover:text-red-700 dark:hover:text-red-300 p-1"
                  :title="t('xpeng.btn_revoke')">
            <TrashIcon class="w-4 h-4" />
          </button>
        </div>
      </div>
    </section>

    <!-- Job history -->
    <section v-if="jobs.length > 0" class="border border-gray-200 dark:border-gray-700 rounded-lg p-4">
      <h3 class="text-base font-semibold mb-3">{{ t('xpeng.history_title') }}</h3>
      <div class="text-xs space-y-2 max-h-60 overflow-y-auto">
        <div v-for="j in jobs" :key="j.id"
             class="border-b border-gray-100 dark:border-gray-800 last:border-0 pb-2">
          <div class="flex justify-between">
            <span>{{ formatDate(j.createdAt) }}</span>
            <span :class="statusColor(j.status)">{{ statusLabel(j.status) }}</span>
          </div>
          <div v-if="j.status === 'DONE'" class="text-gray-500 dark:text-gray-400">
            {{ j.importedTrips }} / {{ j.importedSessions }}
          </div>
          <div v-if="j.errorMessage" class="text-red-600 dark:text-red-400">{{ j.errorMessage }}</div>
        </div>
      </div>
    </section>
  </div>
</template>
