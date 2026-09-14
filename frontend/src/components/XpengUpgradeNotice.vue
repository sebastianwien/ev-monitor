<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { ArrowTopRightOnSquareIcon, ArrowUpTrayIcon } from '@heroicons/vue/24/outline'
import XpengAutoSyncWaitlist from './imports/XpengAutoSyncWaitlist.vue'

// Eigenstaendige /upgrade-Variante fuer XPeng: XPeng hat keine Live-Schnittstelle,
// der einzige Weg ist der kostenlose EU-Data-Act-ZIP-Upload. Darum kein AutoSync-
// Verkauf, sondern ein ehrlicher, fokussierter Weg (Portal -> Import) plus der
// Verweis auf Supporter fuer die Auswertungen. Die Analytics-Previews leben bereits
// auf /supporter - hier bewusst nicht dupliziert.
const { t } = useI18n()
</script>

<template>
  <div class="py-6 md:py-12 px-4">
    <div class="max-w-xl mx-auto text-center rounded-sm border border-green-300 dark:border-green-700 bg-white dark:bg-gray-900 shadow-sm dark:shadow-none p-5 md:p-8">
      <p class="text-[13px] font-semibold uppercase tracking-wider text-green-700 dark:text-green-400 mb-2">{{ t('autosync_page.xpeng_eyebrow') }}</p>
      <h1 class="text-2xl md:text-3xl font-bold text-gray-900 dark:text-gray-100 mb-3" style="text-wrap:balance">{{ t('autosync_page.xpeng_title') }}</h1>
      <p class="text-base md:text-[17px] leading-relaxed text-gray-700 dark:text-gray-300">{{ t('autosync_page.xpeng_body') }}</p>

      <a
        href="https://www.xpeng.com/data-act"
        target="_blank"
        rel="noopener noreferrer"
        class="inline-flex items-center gap-1.5 mt-5 text-sm font-bold text-green-700 dark:text-green-400 hover:underline"
      >
        {{ t('xpeng.manual_prompt_portal_link') }}
        <ArrowTopRightOnSquareIcon class="w-4 h-4" />
      </a>

      <router-link
        to="/imports"
        class="w-full inline-flex items-center justify-center gap-2 mt-4 bg-green-600 hover:bg-green-700 dark:bg-green-500 dark:hover:bg-green-400 text-white dark:text-gray-900 font-semibold py-3.5 rounded-sm text-base shadow-[0_4px_0_0_#166534] dark:shadow-[0_4px_0_0_#064e3b] active:translate-y-1 active:shadow-none transition"
      >
        <ArrowUpTrayIcon class="w-5 h-5" />
        {{ t('autosync_page.xpeng_import_cta') }}
      </router-link>

      <div class="mt-6 pt-5 border-t border-green-200/70 dark:border-green-700/30">
        <XpengAutoSyncWaitlist bare />
      </div>

      <div class="mt-6 pt-5 border-t border-green-200/70 dark:border-green-700/30">
        <p class="text-[15px] leading-relaxed text-gray-600 dark:text-gray-400">{{ t('autosync_page.xpeng_analytics_hint') }}</p>
        <router-link to="/supporter" class="inline-block mt-1.5 text-[15px] font-semibold text-green-700 dark:text-green-400 hover:underline">{{ t('autosync_page.xpeng_supporter_cta') }} &rarr;</router-link>
      </div>
    </div>
  </div>
</template>
