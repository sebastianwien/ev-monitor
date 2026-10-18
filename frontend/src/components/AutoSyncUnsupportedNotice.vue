<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { ArrowUpTrayIcon, PlusIcon } from '@heroicons/vue/24/outline'

// /upgrade-Variante fuer Garagen nur aus Marken ohne AutoSync-Weg (Zeekr, Leapmotor, Lucid,
// Rivian): kein Verkauf, weil das Abo ihre Daten nicht anfassen wuerde. Auch kein Verweis aufs
// Supporter-Pack - dessen Auswertungen brauchen Telemetrie, die es fuer diese Marken nicht gibt.
const { t } = useI18n()
defineProps<{ carName: string }>()
</script>

<template>
  <div class="py-6 md:py-12 px-4">
    <div class="max-w-xl mx-auto text-center rounded-sm border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 shadow-sm dark:shadow-none p-5 md:p-8">
      <p class="text-[13px] font-semibold uppercase tracking-wider text-gray-600 dark:text-gray-400 mb-2">{{ t('autosync_page.unsupported_eyebrow') }}</p>
      <h1 class="text-2xl md:text-3xl font-bold text-gray-900 dark:text-gray-100 mb-3" style="text-wrap:balance">{{ t('autosync_page.unsupported_title', { car: carName }) }}</h1>
      <p class="text-base md:text-[17px] leading-relaxed text-gray-700 dark:text-gray-300">{{ t('autosync_page.unsupported_body') }}</p>

      <router-link
        to="/erfassen"
        class="w-full inline-flex items-center justify-center gap-2 mt-5 bg-green-600 hover:bg-green-700 dark:bg-green-500 dark:hover:bg-green-400 text-white dark:text-gray-900 font-semibold py-3.5 rounded-sm text-base shadow-[0_4px_0_0_#166534] dark:shadow-[0_4px_0_0_#064e3b] active:translate-y-1 active:shadow-none transition"
      >
        <PlusIcon class="w-5 h-5" />
        {{ t('autosync_page.unsupported_log_cta') }}
      </router-link>

      <router-link
        to="/imports"
        class="w-full inline-flex items-center justify-center gap-2 mt-3 py-3 rounded-sm text-sm font-medium text-gray-700 dark:text-gray-300 border border-gray-200 dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors"
      >
        <ArrowUpTrayIcon class="w-5 h-5" />
        {{ t('autosync_page.unsupported_import_cta') }}
      </router-link>
    </div>
  </div>
</template>
