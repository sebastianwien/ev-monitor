<script setup lang="ts">
import { useI18n } from 'vue-i18n'

// Transparenz zur Ablage pseudonymisierter Upload-Kopien (Datenschutzerklaerung Abschnitt 7).
// Bewusst nur deutsch gepflegt (wie die Datenschutzerklaerung) - daher fest aus dem de-Locale.
const { t } = useI18n()
const de = { locale: 'de' }
</script>

<template>
  <p class="text-xs text-gray-500 dark:text-gray-400" data-testid="upload-sample-notice">
    {{ t('imports.sample_notice', {}, de) }}
    <router-link to="/datenschutz" class="underline underline-offset-2 hover:text-gray-700 dark:hover:text-gray-200">
      {{ t('imports.sample_notice_link', {}, de) }}
    </router-link>
  </p>
</template>
