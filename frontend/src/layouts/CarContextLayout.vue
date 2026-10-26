<script setup lang="ts">
import { computed, defineAsyncComponent, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { provideCarContext } from '../composables/useCarContext'
import { useStickyTabIndex } from '../composables/useStickyTabIndex'
import MobileCarSelector from '../components/shared/MobileCarSelector.vue'
import SwipeTabPager from '../components/shared/SwipeTabPager.vue'
import EditLogModal from '../components/dashboard/EditLogModal.vue'
import PriceAmendModal from '../components/dashboard/PriceAmendModal.vue'
import WattToast from '../components/shared/WattToast.vue'
import CarShareSheet from '../components/car/CarShareSheet.vue'
import { useCarShareSheet } from '../composables/useCarShareSheet'
import { carDisplayName } from '../utils/enumLabel'
import type { Car } from '../api/carService'
import { useCoinStore } from '../stores/coins'
import { contextTabPaths } from '../config/tabs'
import { useStoryOptIn } from '../composables/useStoryOptIn'
import DashboardView from '../views/DashboardView.vue'

const LogsView = defineAsyncComponent(() => import('../views/LogsView.vue'))
const StoryDashboardView = defineAsyncComponent(() => import('../views/StoryDashboardView.vue'))

/**
 * Geteiltes Layout fuer /dashboard bzw. /story und /logs. Alle Routen zeigen auf diese
 * Komponente -> Vue Router reused die Instanz, der Header bleibt stehen. Der gemeinsame
 * State lebt hier einmal (provideCarContext).
 * Den horizontalen Wechsel zwischen beiden Bodies macht der SwipeTabPager.
 *
 * Neues Design (Story-Opt-in, einzige Weiche): links die Uebersicht, Kopf und Bereiche bringt das
 * Geruest WorkspaceFrame aus App.vue. Altes Design: Chrome aus App.vue, links das Dashboard.
 *
 * Mobile hat hier bewusst keine eigene Tab-Leiste: die Bottom-Nav (Start/Logs) fuehrt
 * schon zwischen genau diesen beiden Zielen, ein zweiter Umschalter im Header waere
 * dieselbe Navigation doppelt. Desktop bekommt die Leiste aus App.vue.
 */
const { enabled: storyDesign } = useStoryOptIn()
const tabPaths = computed(() => contextTabPaths(storyDesign.value))

const {
  selectedCarId, cars, carImageUrls, wltp, currentOdometerKm,
  teslaStatus, smartcarStatus,
  editingLog, priceAmendingLog, refreshLogsAndGroups, fetchPricelessCount,
} = provideCarContext()

const activeIndex = useStickyTabIndex(tabPaths)
const wattToast = ref<InstanceType<typeof WattToast> | null>(null)
const coinStore = useCoinStore()
const { t } = useI18n()

const { sheet: shareSheet, open: openShareSheet, close: closeShareSheet } = useCarShareSheet()
function shareFromHeader(car: Car) {
  openShareSheet(car.id, [carDisplayName(car), car.trim].filter(Boolean).join(' '), 'car_header')
}
</script>

<template>
  <div>
    <!-- Mobile Auto-Card nur ueber dem alten Dashboard. Uebersicht und Logs nennen das Auto in
         ihrer Kopfzeile (CarSwitch), dort sitzt auch der Wechsel. -->
    <div class="md:hidden px-3">
      <MobileCarSelector
        v-if="cars.length > 0 && !storyDesign && activeIndex === 0"
        v-model="selectedCarId"
        :cars="cars"
        :car-image-urls="carImageUrls"
        :wltp="wltp"
        :current-odometer-km="currentOdometerKm"
        :tesla-status="teslaStatus"
        :smartcar-status="smartcarStatus"
        :show-inline-details="true"
        @share="shareFromHeader"
      />
    </div>

    <!-- Ein Teilen-Sheet fuer alle Einstiege (Header, Vergleichskarte). -->
    <CarShareSheet
      v-if="shareSheet"
      :car-id="shareSheet.carId"
      :title="shareSheet.title"
      :source="shareSheet.source"
      @close="closeShareSheet"
    />

    <!-- Die Desktop-Leiste mit allen vier Zielen liegt in App.vue - sie bleibt beim
         Tab-Wechsel stehen, waehrend der Inhalt darunter durchwischt. -->
    <SwipeTabPager :tabs="tabPaths" :active-index="activeIndex">
      <template #left><StoryDashboardView v-if="storyDesign" /><DashboardView v-else /></template>
      <template #right><LogsView /></template>
    </SwipeTabPager>

    <!-- Der Log-Editor gehoert dem geteilten Context, nicht einem der beiden Bodies:
         geoeffnet wird er aus dem Log-Feed (Zeilen-Menue) wie aus dem Dashboard
         (Kachel "Letzter Ladevorgang"), gerendert genau einmal. -->
    <EditLogModal
      v-if="editingLog"
      :log="editingLog"
      @close="editingLog = null"
      @saved="() => { editingLog = null; refreshLogsAndGroups() }"
      @deleted="() => { editingLog = null; refreshLogsAndGroups() }"
    />

    <!-- Schlanker Preis-Nachtrag: aus dem Warn-Chip an einer preislosen Ladung. -->
    <PriceAmendModal
      v-if="priceAmendingLog"
      :log="priceAmendingLog"
      @close="priceAmendingLog = null"
      @saved="(_log, coins, batch) => { priceAmendingLog = null; refreshLogsAndGroups(); fetchPricelessCount(); wattToast?.show(coins, batch > 0 ? t('priceamend.batch_done', batch) : ''); if (coins) coinStore.refresh() }"
    />
    <WattToast ref="wattToast" />
  </div>
</template>
