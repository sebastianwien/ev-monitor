<script setup lang="ts">
import { computed, defineAsyncComponent } from 'vue'
import { useRoute } from 'vue-router'
import { provideCarContext } from '../composables/useCarContext'
import MobileCarSelector from '../components/shared/MobileCarSelector.vue'
import ViewSegmentedControl from '../components/shared/ViewSegmentedControl.vue'
import DashboardView from '../views/DashboardView.vue'

// Log-Feed ist gross -> lazy. Dashboard ist der Default-Tab -> eager.
const LogsView = defineAsyncComponent(() => import('../views/LogsView.vue'))

/**
 * Geteiltes Layout fuer /dashboard und /logs. Beide Routen zeigen auf diese
 * Komponente -> Vue Router reused die Instanz beim Wechsel, der Header (Auto-Card
 * + Tab-Switch) bleibt stehen und nur der Body-Teil wechselt. Der gemeinsame
 * State (Auto-Auswahl, Polling, Statistiken, Logs) lebt hier einmal.
 */
const route = useRoute()

const {
  selectedCarId, cars, carImageUrls, wltp, currentOdometerKm,
  teslaStatus, smartcarStatus, vwGroupStatus,
} = provideCarContext()

const isLogs = computed(() => route.name === 'logs')
const activeBody = computed(() => (isLogs.value ? LogsView : DashboardView))
</script>

<template>
  <div>
    <!-- Geteilter Mobile-Header: Auto-Card + Tab-Switch (persistent, wechselt nicht) -->
    <div class="md:hidden px-2">
      <MobileCarSelector
        v-if="cars.length > 0"
        v-model="selectedCarId"
        :cars="cars"
        :car-image-urls="carImageUrls"
        :wltp="wltp"
        :current-odometer-km="currentOdometerKm"
        :tesla-status="teslaStatus"
        :smartcar-status="smartcarStatus"
        :vw-group-status="vwGroupStatus"
        :show-inline-details="true"
      />
      <ViewSegmentedControl class="mb-2" />
    </div>

    <!-- Nur der Body slidet (Header liegt darueber, bleibt stehen). Richtung aus
         router.beforeEach (meta.transition = slide-left/right fuer das Paar). -->
    <Transition :name="(route.meta.transition as string) || ''" mode="out-in">
      <KeepAlive :include="['DashboardView', 'LogsView']">
        <component :is="activeBody" />
      </KeepAlive>
    </Transition>
  </div>
</template>
