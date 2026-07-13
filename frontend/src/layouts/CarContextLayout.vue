<script setup lang="ts">
import { computed, defineAsyncComponent } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { provideCarContext } from '../composables/useCarContext'
import MobileCarSelector from '../components/shared/MobileCarSelector.vue'
import ViewSegmentedControl from '../components/shared/ViewSegmentedControl.vue'
import SwipeTabPager from '../components/shared/SwipeTabPager.vue'
import DashboardView from '../views/DashboardView.vue'

const LogsView = defineAsyncComponent(() => import('../views/LogsView.vue'))

/**
 * Geteiltes Layout fuer /dashboard und /logs. Beide Routen zeigen auf diese
 * Komponente -> Vue Router reused die Instanz, der Header (Auto-Card + Tab-Switch)
 * bleibt stehen. Der gemeinsame State lebt hier einmal (provideCarContext).
 * Den horizontalen Wechsel zwischen beiden Bodies macht der SwipeTabPager.
 */
const route = useRoute()
const { t } = useI18n()

const TAB_PATHS = ['/dashboard', '/logs'] as const
const TABS = computed(() => [
  { to: '/dashboard', label: t('nav.tab_overview') },
  { to: '/logs', label: t('logs.title') },
])

const {
  selectedCarId, cars, carImageUrls, wltp, currentOdometerKm,
  teslaStatus, smartcarStatus, vwGroupStatus,
} = provideCarContext()

const activeIndex = computed(() => (route.name === 'logs' ? 1 : 0))
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

    <SwipeTabPager :tabs="TAB_PATHS" :active-index="activeIndex">
      <template #left><DashboardView /></template>
      <template #right><LogsView /></template>
    </SwipeTabPager>
  </div>
</template>
