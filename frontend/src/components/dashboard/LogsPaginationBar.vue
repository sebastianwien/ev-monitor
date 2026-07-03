<script setup lang="ts">
import { ChevronLeftIcon, ChevronRightIcon, ChevronUpDownIcon } from '@heroicons/vue/24/outline'
import { useI18n } from 'vue-i18n'
import type { PageSize } from '../../composables/useLogList'
import { PAGE_SIZE_OPTIONS } from '../../composables/useLogList'

const { t } = useI18n()

withDefaults(defineProps<{
  page: number
  hasMore: boolean
  pageSize: PageSize
  dateRange?: string
  /**
   * 'header' (ueber dem Feed): Datum als schlichtes Label zwischen Trennstrichen,
   * ohne Seitengroesse. 'footer' (default, unter dem Feed): mit Seitengroesse.
   */
  variant?: 'header' | 'footer'
}>(), { variant: 'footer' })

const emit = defineEmits<{
  prev: []
  next: []
  pageSizeChange: [size: PageSize]
}>()
</script>

<template>
  <div class="flex items-center justify-between gap-2">
    <button
      @click="emit('prev')"
      :disabled="page === 0"
      :aria-label="t('dashboard.prev')"
      class="flex items-center justify-center gap-1 px-3 sm:px-4 h-9 text-sm font-medium rounded-sm border-2 border-gray-300 dark:border-gray-600 shadow-[2px_2px_0_0_#d1d5db] dark:shadow-[2px_2px_0_0_#374151] bg-white dark:bg-gray-700 text-gray-700 dark:text-gray-300 disabled:opacity-40 disabled:cursor-not-allowed transition">
      <ChevronLeftIcon class="w-4 h-4" /><span class="hidden sm:inline">{{ t('dashboard.prev') }}</span>
    </button>
    <!-- Zeitraum: schlichtes Label (kein Button-Look). Im Header von Trennstrichen flankiert. -->
    <span v-if="dateRange" class="flex-1 flex items-center justify-center gap-3 min-w-0 px-1">
      <span v-if="variant === 'header'" class="flex-1 h-px bg-gray-200 dark:bg-gray-700" />
      <span class="text-sm font-medium text-gray-500 dark:text-gray-400 tabular-nums whitespace-nowrap select-none">{{ dateRange }}</span>
      <span v-if="variant === 'header'" class="flex-1 h-px bg-gray-200 dark:bg-gray-700" />
    </span>
    <span v-else class="flex-1" />
    <div class="flex items-center gap-2">
      <label v-if="variant === 'footer'" class="flex items-center gap-1.5 text-xs text-gray-600 dark:text-gray-400">
        <span class="hidden sm:inline">{{ t('dashboard.page_size_label') }}</span>
        <div class="relative">
          <select
            :value="pageSize"
            @change="emit('pageSizeChange', Number(($event.target as HTMLSelectElement).value) as PageSize)"
            :aria-label="t('dashboard.page_size_label')"
            class="appearance-none pl-2 pr-7 h-9 text-sm rounded-sm border-2 border-gray-300 dark:border-gray-600 shadow-[2px_2px_0_0_#d1d5db] dark:shadow-[2px_2px_0_0_#374151] bg-white dark:bg-gray-700 text-gray-700 dark:text-gray-300 cursor-pointer focus:outline-none focus:ring-2 focus:ring-indigo-500">
            <option v-for="size in PAGE_SIZE_OPTIONS" :key="size" :value="size">{{ size }}</option>
          </select>
          <ChevronUpDownIcon class="absolute right-1.5 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400 dark:text-gray-500 pointer-events-none" />
        </div>
      </label>
      <button
        @click="emit('next')"
        :disabled="!hasMore"
        :aria-label="t('dashboard.next')"
        class="flex items-center justify-center gap-1 px-3 sm:px-4 h-9 text-sm font-medium rounded-sm border-2 border-gray-300 dark:border-gray-600 shadow-[2px_2px_0_0_#d1d5db] dark:shadow-[2px_2px_0_0_#374151] bg-white dark:bg-gray-700 text-gray-700 dark:text-gray-300 disabled:opacity-40 disabled:cursor-not-allowed transition">
        <span class="hidden sm:inline">{{ t('dashboard.next') }}</span><ChevronRightIcon class="w-4 h-4" />
      </button>
    </div>
  </div>
</template>
