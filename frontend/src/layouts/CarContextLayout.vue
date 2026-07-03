<script setup lang="ts">
import { computed, defineAsyncComponent } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { provideCarContext } from '../composables/useCarContext'
import MobileCarSelector from '../components/shared/MobileCarSelector.vue'
import DashboardView from '../views/DashboardView.vue'

// Log-Feed ist gross -> lazy. Dashboard ist der Default-Tab -> eager.
const LogsView = defineAsyncComponent(() => import('../views/LogsView.vue'))

/**
 * Geteiltes Layout fuer /dashboard und /logs. Beide Routen zeigen auf diese
 * Komponente -> Vue Router reused die Instanz beim Wechsel, der Header (Auto-Card
 * + Tab-Switch) bleibt stehen und nur der Body-Teil wechselt. Der gemeinsame
 * State (Auto-Auswahl, Polling, Statistiken, Logs) lebt hier einmal.
 */
const { t } = useI18n()
const route = useRoute()

const {
  selectedCarId, cars, carImageUrls, wltp, currentOdometerKm,
  teslaStatus, smartcarStatus, vwGroupStatus,
} = provideCarContext()

const isLogs = computed(() => route.name === 'logs')
const activeBody = computed(() => (isLogs.value ? LogsView : DashboardView))

const tabClass = (active: boolean) =>
  `flex-1 text-center text-sm font-semibold py-1.5 rounded-sm transition ${
    active ? 'bg-white dark:bg-gray-700 text-indigo-600 dark:text-indigo-300 shadow-sm'
           : 'text-gray-500 dark:text-gray-400'
  }`
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
      <div class="flex gap-1 p-1 mb-2 rounded-md bg-gray-100 dark:bg-gray-800">
        <router-link to="/dashboard" :class="tabClass(!isLogs)">{{ t('nav.tab_overview') }}</router-link>
        <router-link to="/logs" :class="tabClass(isLogs)">{{ t('logs.title') }}</router-link>
      </div>
    </div>

    <KeepAlive :include="['DashboardView', 'LogsView']">
      <component :is="activeBody" />
    </KeepAlive>
  </div>
</template>
