<script setup lang="ts">
import { computed, defineAsyncComponent } from 'vue'
import { useI18n } from 'vue-i18n'
import { provideCarContext } from '../composables/useCarContext'
import { useStickyTabIndex } from '../composables/useStickyTabIndex'
import MobileCarSelector from '../components/shared/MobileCarSelector.vue'
import ViewSegmentedControl from '../components/shared/ViewSegmentedControl.vue'
import SwipeTabPager from '../components/shared/SwipeTabPager.vue'
import { CONTEXT_TABS } from '../config/tabs'
import DashboardView from '../views/DashboardView.vue'

const LogsView = defineAsyncComponent(() => import('../views/LogsView.vue'))

/**
 * Geteiltes Layout fuer /dashboard und /logs. Beide Routen zeigen auf diese
 * Komponente -> Vue Router reused die Instanz, der Header (Auto-Card + Tab-Switch)
 * bleibt stehen. Der gemeinsame State lebt hier einmal (provideCarContext).
 * Den horizontalen Wechsel zwischen beiden Bodies macht der SwipeTabPager.
 */
const { t } = useI18n()

const TAB_PATHS: readonly string[] = CONTEXT_TABS.map(tab => tab.to)
const TABS = computed(() => CONTEXT_TABS.map(tab => ({ to: tab.to, label: t(tab.labelKey) })))

const {
  selectedCarId, cars, carImageUrls, wltp, currentOdometerKm,
  teslaStatus, smartcarStatus, vwGroupStatus,
} = provideCarContext()

const activeIndex = useStickyTabIndex(TAB_PATHS)
</script>

<template>
  <div>
    <!-- Geteilter Mobile-Header: Auto-Card + Tab-Switch (persistent) -->
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
      <ViewSegmentedControl class="mb-2" :tabs="TABS" />
    </div>

    <!-- Die Desktop-Leiste mit allen vier Zielen liegt in App.vue - sie bleibt beim
         Tab-Wechsel stehen, waehrend der Inhalt darunter durchwischt. -->
    <SwipeTabPager :tabs="TAB_PATHS" :active-index="activeIndex">
      <template #left><DashboardView /></template>
      <template #right><LogsView /></template>
    </SwipeTabPager>
  </div>
</template>
