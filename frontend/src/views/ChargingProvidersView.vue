<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import ChargingProvidersPanel from '../components/settings/ChargingProvidersPanel.vue'

/**
 * Ladekarten-Verwaltung als zweiter Tab neben "Meine Fahrzeuge". Beide gehoeren zum
 * Fuhrpark des Users - die Karte zahlt, was das Auto laedt. Den Tab-Switch stellt
 * das CarsLayout, das beide Ansichten traegt.
 */
const { t } = useI18n()
</script>

<template>
  <div class="mx-auto max-w-6xl pb-6 md:px-6 md:pb-12">
    <!-- Eine Box wie in Uebersicht und /cars: mobil edge-to-edge, ab md mit Kante -->
    <section class="bg-white px-4 py-4 md:rounded-3xl md:p-6 md:edge-box dark:bg-gray-900" data-testid="cards-box">
      <!-- Der aktive Tab benennt die Seite (Chips mobil wie Desktop), die Ueberschrift bleibt fuer Screenreader. -->
      <h1 class="sr-only">{{ t('settings.tariff_title') }}</h1>
      <p class="mb-4 text-sm text-gray-600 dark:text-gray-400">{{ t('cards.intro') }}</p>

      <ChargingProvidersPanel />
    </section>
  </div>
</template>
