<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { ArrowTopRightOnSquareIcon, CheckIcon } from '@heroicons/vue/24/outline'

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
  <div class="grid gap-4" data-testid="euda-explainer">
    <div>
      <h4 class="mb-1 text-[15px] font-bold tracking-tight text-gray-900 dark:text-gray-100">{{ t('eu_data_act_sync.explainer_title') }}</h4>
      <p class="text-sm leading-relaxed text-gray-700 dark:text-gray-300">{{ t('eu_data_act_sync.explainer_intro') }}</p>
    </div>

    <ul class="grid gap-2">
      <li v-for="i in 4" :key="i" class="flex items-start gap-2.5 text-sm text-gray-700 dark:text-gray-300">
        <CheckIcon class="mt-0.5 h-5 w-5 shrink-0 text-gray-400" aria-hidden="true" />{{ t(`eu_data_act_sync.explainer_item${i}`) }}
      </li>
    </ul>

    <div>
      <h4 class="mb-1.5 text-sm font-bold tracking-tight text-gray-900 dark:text-gray-100">{{ t('eu_data_act_sync.explainer_steps_title') }}</h4>
      <ol class="grid gap-1.5">
        <li v-for="i in 3" :key="i" class="flex items-start gap-2.5 text-sm text-gray-700 dark:text-gray-300">
          <span class="mt-0.5 grid h-5 w-5 shrink-0 place-items-center rounded-full bg-gray-100 text-[11px] font-bold tabular-nums text-gray-700 dark:bg-gray-800 dark:text-gray-200" aria-hidden="true">{{ i }}</span>
          <span>{{ t(`eu_data_act_sync.explainer_step${i}`) }}</span>
        </li>
      </ol>
    </div>

    <p class="flex flex-wrap gap-x-4 gap-y-1 text-[13px] font-semibold">
      <a :href="PORTAL_URL" target="_blank" rel="noopener noreferrer"
         class="inline-flex min-h-11 items-center gap-1 text-gray-900 underline underline-offset-2 dark:text-gray-100">
        {{ t('eu_data_act_sync.explainer_link_portal') }}
        <ArrowTopRightOnSquareIcon class="h-3.5 w-3.5" aria-hidden="true" />
      </a>
      <router-link to="/datenschutz" class="inline-flex min-h-11 items-center text-gray-900 underline underline-offset-2 dark:text-gray-100">
        {{ t('eu_data_act_sync.explainer_link_privacy') }}
      </router-link>
    </p>
  </div>
</template>
