<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { XMarkIcon, ArrowUturnLeftIcon, TrashIcon } from '@heroicons/vue/24/outline'
import BottomSheet from '../shared/BottomSheet.vue'
import type { DeletedLog } from '../../composables/useDeletedLogs'
import { sourceInfo } from '../../utils/logSource'

defineProps<{
  logs: DeletedLog[]
  busyId: string | null
  failedIds: Set<string>
  formatDate: (iso: string) => string
}>()
const emit = defineEmits<{ close: []; restore: [id: string]; purge: [id: string] }>()

const { t, n } = useI18n()
const sheet = ref<InstanceType<typeof BottomSheet> | null>(null)
const confirmingPurgeId = ref<string | null>(null)

function kwh(log: DeletedLog) {
  const v = log.kwhAtVehicle ?? log.kwhCharged
  return v == null ? '-' : `${n(v, { maximumFractionDigits: 2 })} kWh`
}

function source(log: DeletedLog) {
  return sourceInfo(log.dataSource)?.label ?? (log.dataSource === 'API_UPLOAD' ? 'API' : null)
}

function confirmPurge(id: string) {
  confirmingPurgeId.value = null
  emit('purge', id)
}

defineExpose({ requestClose: () => sheet.value?.requestClose() })
</script>

<template>
  <BottomSheet ref="sheet" :label="t('logs.trash.title')" testid="deleted-logs-sheet" @close="emit('close')">
    <div class="flex items-center justify-between gap-3 px-4 pt-4 pb-2">
      <h2 class="text-lg font-bold text-gray-900 dark:text-gray-100">{{ t('logs.trash.title') }}</h2>
      <button type="button" :aria-label="t('common.close')" @click="sheet?.requestClose()"
        class="min-h-11 min-w-11 flex items-center justify-center rounded-sm text-gray-500 hover:bg-gray-100 dark:hover:bg-gray-700">
        <XMarkIcon class="w-5 h-5" />
      </button>
    </div>
    <p class="px-4 pb-3 text-sm text-gray-600 dark:text-gray-300">{{ t('logs.trash.intro') }}</p>

    <ul class="flex-1 overflow-y-auto px-4 pb-4 space-y-2">
      <li v-if="logs.length === 0" class="py-6 text-center text-sm text-gray-500 dark:text-gray-400">
        {{ t('logs.trash.empty') }}
      </li>
      <li v-for="log in logs" :key="log.id" data-testid="deleted-log-row"
        class="border-2 border-gray-200 dark:border-gray-700 rounded-sm p-3">
        <div class="flex items-baseline justify-between gap-2">
          <span class="font-medium text-gray-900 dark:text-gray-100">{{ formatDate(log.loggedAt) }}</span>
          <span class="text-sm tabular-nums text-gray-700 dark:text-gray-300">{{ kwh(log) }}</span>
        </div>
        <div v-if="source(log)" class="text-xs text-gray-500 dark:text-gray-400 mt-0.5">{{ source(log) }}</div>

        <p v-if="failedIds.has(log.id)" role="alert" class="mt-2 text-sm text-red-700 dark:text-red-300">
          {{ t('logs.trash.failed') }}
        </p>

        <div v-if="confirmingPurgeId === log.id" class="mt-2 rounded-sm bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-700 p-2">
          <p class="text-sm text-gray-800 dark:text-gray-200">{{ t('logs.trash.purge_confirm') }}</p>
          <div class="mt-2 flex gap-2">
            <button type="button" data-testid="deleted-log-purge-confirm" :disabled="busyId === log.id" @click="confirmPurge(log.id)"
              class="min-h-11 px-3 rounded-sm bg-red-600 text-white text-sm font-bold hover:bg-red-700 disabled:opacity-50">
              {{ t('logs.trash.purge_yes') }}
            </button>
            <button type="button" @click="confirmingPurgeId = null"
              class="min-h-11 px-3 rounded-sm text-sm font-medium text-gray-700 dark:text-gray-200 hover:bg-gray-100 dark:hover:bg-gray-700">
              {{ t('logs.trash.cancel') }}
            </button>
          </div>
        </div>
        <div v-else class="mt-2 flex flex-wrap gap-2">
          <button type="button" data-testid="deleted-log-restore" :disabled="busyId === log.id" @click="emit('restore', log.id)"
            class="min-h-11 inline-flex items-center gap-1.5 px-3 rounded-sm border-2 border-gray-300 dark:border-gray-600 text-sm font-bold text-gray-800 dark:text-gray-100 hover:bg-gray-50 dark:hover:bg-gray-700 disabled:opacity-50">
            <ArrowUturnLeftIcon class="w-4 h-4" aria-hidden="true" />
            {{ t('logs.trash.restore') }}
          </button>
          <button type="button" data-testid="deleted-log-purge" :disabled="busyId === log.id" @click="confirmingPurgeId = log.id"
            class="min-h-11 inline-flex items-center gap-1.5 px-3 rounded-sm text-sm font-medium text-red-700 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20 disabled:opacity-50">
            <TrashIcon class="w-4 h-4" aria-hidden="true" />
            {{ t('logs.trash.purge') }}
          </button>
        </div>
      </li>
    </ul>
  </BottomSheet>
</template>
