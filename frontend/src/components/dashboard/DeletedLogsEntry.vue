<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { TrashIcon, ChevronRightIcon } from '@heroicons/vue/24/outline'
import DeletedLogsSheet from './DeletedLogsSheet.vue'
import { useDeletedLogs } from '../../composables/useDeletedLogs'
import { useCarContext } from '../../composables/useCarContext'

/** Papierkorb unter Log-Feed und Logbuch: unaufdringlicher Einstieg, die Liste lädt erst beim Öffnen. */
const { t } = useI18n()
const { selectedCarId, formatLogDate } = useCarContext()
const deletedLogs = useDeletedLogs(selectedCarId)
const showDeletedSheet = ref(false)
function openDeletedSheet() {
  showDeletedSheet.value = true
  deletedLogs.load()
}
</script>

<template>
  <div>
  <button type="button" data-testid="deleted-logs-row"
    @click="openDeletedSheet"
    class="group flex min-h-14 w-full items-center gap-3 rounded-2xl px-3 py-2.5 text-left transition-colors hover:bg-gray-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-story-accent dark:hover:bg-white/5">
    <span class="grid h-8 w-8 shrink-0 place-items-center rounded-full bg-gray-100 text-gray-600 dark:bg-white/10 dark:text-gray-300">
      <TrashIcon class="h-[18px] w-[18px]" aria-hidden="true" />
    </span>
    <span class="min-w-0 flex-1">
      <span class="block text-[14px] font-semibold text-gray-900 dark:text-gray-100">{{ t('logs.trash.row') }}</span>
      <span class="block text-[12.5px] text-gray-500 dark:text-gray-400">{{ t('logs.trash.hint') }}</span>
    </span>
    <ChevronRightIcon class="h-4 w-4 shrink-0 text-gray-400 transition-transform group-hover:translate-x-0.5" aria-hidden="true" />
  </button>
  <DeletedLogsSheet
    v-if="showDeletedSheet"
    :logs="deletedLogs.logs.value"
    :busy-id="deletedLogs.busyId.value"
    :loading="deletedLogs.loading.value"
    :failed="deletedLogs.failed.value"
    :format-date="formatLogDate"
    @restore="deletedLogs.restore"
    @purge="deletedLogs.purge"
    @close="showDeletedSheet = false"
  />
  </div>
</template>
