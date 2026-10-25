<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { XMarkIcon, ArrowUturnLeftIcon, TrashIcon, ArrowPathIcon } from '@heroicons/vue/24/outline'
import BottomSheet from '../shared/BottomSheet.vue'
import type { DeletedLog } from '../../composables/useDeletedLogs'
import { sourceInfo } from '../../utils/logSource'

defineProps<{
  logs: DeletedLog[]
  busyId: string | null
  loading: boolean
  failed: Map<string, 'restore' | 'purge'>
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
  <BottomSheet ref="sheet" edge :label="t('logs.trash.title')" testid="deleted-logs-sheet" @close="emit('close')">
    <div class="flex items-center justify-between gap-3 px-4 pt-4 pb-2">
      <h2 class="text-lg font-bold text-gray-900 dark:text-gray-100">{{ t('logs.trash.title') }}</h2>
      <button type="button" :aria-label="t('common.close')" @click="sheet?.requestClose()"
        class="btn-3d cta-shadow-sm grid h-10 w-10 place-items-center rounded-full border-2 border-edge bg-white text-gray-800 dark:bg-white/10 dark:text-gray-200">
        <XMarkIcon class="h-5 w-5" aria-hidden="true" />
      </button>
    </div>
    <p class="px-4 pb-3 text-sm text-gray-600 dark:text-gray-300">{{ t('logs.trash.intro') }}</p>

    <ul class="flex-1 overflow-y-auto px-4 pb-4 space-y-2">
      <li v-if="loading" class="py-6 flex justify-center" role="status" :aria-label="t('logs.trash.loading')">
        <ArrowPathIcon class="w-5 h-5 animate-spin text-gray-400" aria-hidden="true" />
      </li>
      <li v-else-if="logs.length === 0" class="py-6 text-center text-sm text-gray-500 dark:text-gray-400">
        {{ t('logs.trash.empty') }}
      </li>
      <li v-for="log in logs" :key="log.id" data-testid="deleted-log-row"
        class="rounded-2xl border-2 border-gray-200 p-3 dark:border-gray-700">
        <div class="flex items-baseline justify-between gap-2">
          <span class="font-medium text-gray-900 dark:text-gray-100">{{ formatDate(log.loggedAt) }}</span>
          <span class="text-sm tabular-nums text-gray-700 dark:text-gray-300">{{ kwh(log) }}</span>
        </div>
        <div v-if="source(log)" class="text-xs text-gray-500 dark:text-gray-400 mt-0.5">{{ source(log) }}</div>

        <p v-if="failed.has(log.id)" role="alert" class="mt-2 rounded-xl bg-amber-50 px-3 py-2 text-sm text-amber-900 dark:bg-amber-400/10 dark:text-amber-200">
          {{ failed.get(log.id) === 'purge' ? t('logs.trash.purge_failed') : t('logs.trash.failed') }}
        </p>

        <div v-if="confirmingPurgeId === log.id" class="mt-2 rounded-xl border-2 border-gray-900 p-3 dark:border-gray-700">
          <p class="text-sm text-gray-800 dark:text-gray-200">{{ t('logs.trash.purge_confirm') }}</p>
          <div class="mt-2 flex gap-2">
            <button type="button" data-testid="deleted-log-purge-confirm" :disabled="busyId === log.id" @click="confirmPurge(log.id)"
              class="btn-3d min-h-11 rounded-xl border-2 border-edge bg-red-600 px-4 text-sm font-semibold text-white hover:bg-red-700 disabled:opacity-50">
              {{ t('logs.trash.purge_yes') }}
            </button>
            <button type="button" @click="confirmingPurgeId = null"
              class="min-h-11 rounded-xl px-3 text-sm font-semibold text-gray-600 hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-white/10">
              {{ t('logs.trash.cancel') }}
            </button>
          </div>
        </div>
        <div v-else class="mt-2 flex flex-wrap gap-2">
          <button type="button" data-testid="deleted-log-restore" :disabled="busyId === log.id" @click="emit('restore', log.id)"
            class="btn-3d cta-shadow-sm inline-flex min-h-11 items-center gap-1.5 rounded-full border-2 border-edge bg-white px-4 text-sm font-semibold text-gray-800 hover:bg-gray-50 disabled:opacity-50 dark:bg-white/10 dark:text-gray-100">
            <ArrowUturnLeftIcon class="w-4 h-4" aria-hidden="true" />
            {{ t('logs.trash.restore') }}
          </button>
          <button type="button" data-testid="deleted-log-purge" :disabled="busyId === log.id" @click="confirmingPurgeId = log.id"
            class="inline-flex min-h-11 items-center gap-1.5 rounded-full px-3 text-sm font-semibold text-gray-600 hover:bg-gray-100 disabled:opacity-50 dark:text-gray-300 dark:hover:bg-white/10">
            <TrashIcon class="w-4 h-4" aria-hidden="true" />
            {{ t('logs.trash.purge') }}
          </button>
        </div>
      </li>
    </ul>
  </BottomSheet>
</template>
