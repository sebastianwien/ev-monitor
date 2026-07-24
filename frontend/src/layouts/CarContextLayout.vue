<script setup lang="ts">
import { defineAsyncComponent } from 'vue'
import { provideCarContext } from '../composables/useCarContext'
import { useStickyTabIndex } from '../composables/useStickyTabIndex'
import MobileCarSelector from '../components/shared/MobileCarSelector.vue'
import SwipeTabPager from '../components/shared/SwipeTabPager.vue'
import { CONTEXT_TABS } from '../config/tabs'
import DashboardView from '../views/DashboardView.vue'

const LogsView = defineAsyncComponent(() => import('../views/LogsView.vue'))

/**
 * Geteiltes Layout fuer /dashboard und /logs. Beide Routen zeigen auf diese
 * Komponente -> Vue Router reused die Instanz, der Header (Auto-Card + Tab-Switch)
 * bleibt stehen. Der gemeinsame State lebt hier einmal (provideCarContext).
 * Den horizontalen Wechsel zwischen beiden Bodies macht der SwipeTabPager.
 *
 * Mobile hat hier bewusst keine eigene Tab-Leiste: die Bottom-Nav (Start/Logs) fuehrt
 * schon zwischen genau diesen beiden Zielen, ein zweiter Umschalter im Header waere
 * dieselbe Navigation doppelt. Desktop bekommt die Leiste aus App.vue.
 */
const TAB_PATHS: readonly string[] = CONTEXT_TABS.map(tab => tab.to)

const {
  selectedCarId, cars, carImageUrls, wltp, currentOdometerKm,
  teslaStatus, smartcarStatus, vwGroupStatus,
} = provideCarContext()

const activeIndex = useStickyTabIndex(TAB_PATHS)
</script>

<template>
  <div>
    <!-- Geteilter Mobile-Header: Auto-Card (persistent ueber beide Tabs) -->
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
    </div>

    <!-- Die Desktop-Leiste mit allen vier Zielen liegt in App.vue - sie bleibt beim
         Tab-Wechsel stehen, waehrend der Inhalt darunter durchwischt. -->
    <SwipeTabPager :tabs="TAB_PATHS" :active-index="activeIndex">
      <template #left><DashboardView /></template>
      <template #right><LogsView /></template>
    </SwipeTabPager>
  </div>
</template>
