<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { CheckIcon } from '@heroicons/vue/24/outline'
import { CI_CARD } from '@/utils/ciClasses'

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
  <div :class="card ? CI_CARD : ''">
    <h3 class="mb-3 text-[18px] font-bold tracking-tight text-gray-900 dark:text-gray-100">{{ t('tesla.sync_info_title') }}</h3>
    <ul class="mb-3 grid gap-2">
      <li v-for="i in 5" :key="i" class="flex items-start gap-2.5 text-[15px] text-gray-700 dark:text-gray-300">
        <CheckIcon class="mt-0.5 h-5 w-5 shrink-0 text-gray-400" aria-hidden="true" />{{ t(`tesla.sync_item${i}`) }}
      </li>
    </ul>
    <p class="text-[13px] leading-relaxed text-gray-500 dark:text-gray-400">{{ t('tesla.sync_note') }}</p>
  </div>
</template>
