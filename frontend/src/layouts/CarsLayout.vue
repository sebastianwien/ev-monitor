<script setup lang="ts">
import { computed, defineAsyncComponent } from 'vue'
import { useRoute } from 'vue-router'
import CarManagementView from '../views/CarManagementView.vue'

const ChargingProvidersView = defineAsyncComponent(() => import('../views/ChargingProvidersView.vue'))

/**
 * Gemeinsame Route-Komponente fuer /cars und /charging-providers. Mobil sind es zwei eigene
 * Seiten ohne Umschalter: Fahrzeuge und Ladekarten stehen im Mehr-Menue, das Foto des Autos
 * schliesst direkt an den Kopf an. Desktop erreicht beide ueber die Bereichs-Leiste im
 * WorkspaceFrame. Das innere KeepAlive haelt beim Wechsel Scrollstand und Daten beider Seiten.
 */
const route = useRoute()
const view = computed(() => route.path === '/charging-providers' ? ChargingProvidersView : CarManagementView)
</script>

<template>
  <!-- Ein Element als Wurzel: liegt das innere KeepAlive direkt an der Wurzel dieses (selbst gecachten) Layouts,
       verliert Vue beim Zurückholen den Ankerknoten (insertBefore-Fehler, Navigation bleibt leer) -->
  <div>
    <KeepAlive>
      <component :is="view" />
    </KeepAlive>
  </div>
</template>
