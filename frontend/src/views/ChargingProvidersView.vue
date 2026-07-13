<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { CreditCardIcon } from '@heroicons/vue/24/outline'
import ChargingProvidersPanel from '../components/settings/ChargingProvidersPanel.vue'

/**
 * Ladekarten-Verwaltung als zweiter Tab neben "Meine Fahrzeuge". Beide gehoeren zum
 * Fuhrpark des Users - die Karte zahlt, was das Auto laedt. Den Tab-Switch stellt
 * das CarsLayout, das beide Ansichten traegt.
 */
const { t } = useI18n()
</script>

<template>
  <div class="md:max-w-6xl md:mx-auto md:px-6 md:pb-6">
    <div class="bg-white dark:bg-gray-800 md:rounded-sm md:shadow-[4px_4px_0_rgba(0,0,0,0.30)] dark:md:shadow-[4px_4px_0_rgba(255,255,255,0.30)] p-4 md:p-6">
      <!-- Ab Desktop benennt der aktive Tab die Seite - die Ueberschrift wuerde ihn nur
           wiederholen und bleibt darum nur fuer Screenreader stehen. -->
      <h1 class="mb-2 flex items-center gap-2 text-3xl font-bold text-gray-800 dark:text-gray-200 md:sr-only">
        <CreditCardIcon class="h-7 w-7" />
        {{ t('settings.tariff_title') }}
      </h1>
      <p class="mb-6 text-sm text-gray-500 dark:text-gray-400">{{ t('cards.intro') }}</p>

      <ChargingProvidersPanel />
    </div>
  </div>
</template>
