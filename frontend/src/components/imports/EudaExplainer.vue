<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { ArrowTopRightOnSquareIcon } from '@heroicons/vue/24/outline'

/**
 * Erklaert, was der EU Data Act fuer ein VW-Group-Fahrzeug bedeutet und wie die
 * Einrichtung laeuft. Steht in der AutoSync-Karte auf /imports und im Modal direkt nach
 * dem Anlegen eines VW-Group-Fahrzeugs - derselbe Text, damit beide Wege dasselbe
 * versprechen. Den Trial-Satz traegt das Formular darunter, nicht der Explainer.
 */
const { t } = useI18n()

/** Das Portal, in dem VW die Datenanfrage fuehrt - dort sieht der Nutzer, was ev-monitor angelegt hat. */
const PORTAL_URL = 'https://eu-data-act.drivesomethinggreater.com'
</script>

<template>
  <div class="space-y-4" data-testid="euda-explainer">
    <div>
      <h4 class="text-sm md:text-base font-bold text-gray-900 dark:text-white tracking-tight mb-1">{{ t('eu_data_act_sync.explainer_title') }}</h4>
      <p class="text-sm text-gray-700 dark:text-gray-300 leading-relaxed">{{ t('eu_data_act_sync.explainer_intro') }}</p>
    </div>

    <ul class="space-y-2">
      <li v-for="i in 4" :key="i" class="flex items-start gap-2.5 text-sm text-gray-700 dark:text-gray-300">
        <span class="shrink-0 w-5 h-5 bg-indigo-600 text-white rounded-sm flex items-center justify-center text-[10px] font-extrabold mt-0.5" aria-hidden="true">→</span>
        <span class="font-medium">{{ t(`eu_data_act_sync.explainer_item${i}`) }}</span>
      </li>
    </ul>

    <div>
      <h4 class="text-sm font-bold text-gray-900 dark:text-white tracking-tight mb-1.5">{{ t('eu_data_act_sync.explainer_steps_title') }}</h4>
      <ol class="space-y-1.5">
        <li v-for="i in 3" :key="i" class="flex items-start gap-2.5 text-sm text-gray-700 dark:text-gray-300">
          <span class="shrink-0 w-5 h-5 border-2 border-gray-900 dark:border-gray-200 text-gray-900 dark:text-gray-200 rounded-sm flex items-center justify-center text-[10px] font-extrabold mt-0.5" aria-hidden="true">{{ i }}</span>
          <span>{{ t(`eu_data_act_sync.explainer_step${i}`) }}</span>
        </li>
      </ol>
    </div>

    <p class="flex flex-wrap gap-x-4 gap-y-1 text-xs font-medium">
      <a :href="PORTAL_URL" target="_blank" rel="noopener noreferrer"
         class="inline-flex items-center gap-1 text-indigo-700 dark:text-indigo-300 underline">
        {{ t('eu_data_act_sync.explainer_link_portal') }}
        <ArrowTopRightOnSquareIcon class="h-3.5 w-3.5" aria-hidden="true" />
      </a>
      <router-link to="/datenschutz" class="text-indigo-700 dark:text-indigo-300 underline">
        {{ t('eu_data_act_sync.explainer_link_privacy') }}
      </router-link>
    </p>
  </div>
</template>
