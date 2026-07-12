<script setup lang="ts">
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

/**
 * Erklaert, was Tesla Fleet-Telemetry liefert. Wird an zwei Stellen gezeigt: in der
 * Tesla-Sektion von /imports und im Modal direkt nach dem Anlegen eines Teslas.
 */
withDefaults(defineProps<{
  /** Eigene Karte mit Rahmen - im Modal aus, dort rahmt das Modal selbst. */
  card?: boolean
}>(), { card: true })
</script>

<template>
  <div :class="card ? 'border-2 border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-900 rounded-sm shadow-[2px_2px_0_0_#d1d5db] dark:shadow-[2px_2px_0_0_#374151] p-4 md:p-5' : ''">
    <h3 class="text-base md:text-lg font-bold text-gray-900 dark:text-white tracking-tight mb-3">{{ t('tesla.sync_info_title') }}</h3>
    <ul class="space-y-2 mb-3">
      <li v-for="i in 5" :key="i" class="flex items-start gap-2.5 text-sm text-gray-700 dark:text-gray-300">
        <span class="shrink-0 w-5 h-5 bg-red-600 text-white rounded-sm flex items-center justify-center text-[10px] font-extrabold mt-0.5">→</span>
        <span class="font-medium">{{ t(`tesla.sync_item${i}`) }}</span>
      </li>
    </ul>
    <p class="text-xs text-gray-500 dark:text-gray-400 font-medium leading-relaxed">{{ t('tesla.sync_note') }}</p>
  </div>
</template>
