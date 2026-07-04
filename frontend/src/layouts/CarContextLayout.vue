<script setup lang="ts">
import { computed, ref, watch, onUnmounted, defineAsyncComponent } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { provideCarContext } from '../composables/useCarContext'
import { useTabPager } from '../composables/useTabPager'
import { useIsMobile } from '../composables/useIsMobile'
import MobileCarSelector from '../components/shared/MobileCarSelector.vue'
import ViewSegmentedControl from '../components/shared/ViewSegmentedControl.vue'
import DashboardView from '../views/DashboardView.vue'

const LogsView = defineAsyncComponent(() => import('../views/LogsView.vue'))

/**
 * Geteiltes Layout fuer /dashboard und /logs. Beide Routen zeigen auf diese
 * Komponente -> Vue Router reused die Instanz, der Header (Auto-Card + Tab-Switch)
 * bleibt stehen. Der gemeinsame State lebt hier einmal (provideCarContext).
 *
 * Beide Bodies liegen dauerhaft in einer horizontalen Schiene (Track). Der aktive
 * Tab bestimmt den Track-Offset; ein Swipe zieht den Track mit dem Finger, sodass
 * der Nachbar-Tab schon waehrend der Geste sichtbar wird (echter Pager).
 */
const route = useRoute()
const router = useRouter()

const {
  selectedCarId, cars, carImageUrls, wltp, currentOdometerKm,
  teslaStatus, smartcarStatus, vwGroupStatus,
} = provideCarContext()

const isLogs = computed(() => route.name === 'logs')
const activeIndex = computed(() => (isLogs.value ? 1 : 0))

// Der Swipe-Pager ist ein reines Mobile-Feature. Auf Desktop wird nur der aktive
// Body gemountet - sonst rendern beide Views (doppeltes Polling, duplizierte
// Header/Log-Aktionen im DOM) parallel.
const isMobile = useIsMobile()

const pagerEl = ref<HTMLElement | null>(null)
const { dragX, dragging } = useTabPager(pagerEl, isLogs, (to) => router.push(to))

// Track ist 200% breit (zwei Panes je 100% Viewport). Ein Pane-Wechsel = 50% des
// Tracks. dragX (px) kommt vom Finger obendrauf.
const trackStyle = computed(() => ({
  transform: `translateX(calc(-${activeIndex.value * 50}% + ${dragX.value}px))`,
  transition: dragging.value ? 'none' : 'transform 0.28s cubic-bezier(0.32, 0.72, 0, 1)',
}))

// Die Tabs sind unterschiedlich hoch. Im Ruhezustand klappt der inaktive Pane
// zusammen, damit unter dem kuerzeren Tab keine Luecke entsteht - nur waehrend
// Drag (Peek) und dem Snap-Slide sind beide voll ausgeklappt.
const transitioning = ref(false)
let transTimer: ReturnType<typeof setTimeout> | undefined
watch(activeIndex, () => {
  transitioning.value = true
  clearTimeout(transTimer)
  transTimer = setTimeout(() => { transitioning.value = false }, 320)
})
onUnmounted(() => clearTimeout(transTimer))
const bothExpanded = computed(() => dragging.value || transitioning.value)
const paneCollapsed = (i: number) => !bothExpanded.value && i !== activeIndex.value
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
      <ViewSegmentedControl class="mb-2" />
    </div>

    <!-- Desktop: nur der aktive Body (kein Pager, keine Doppel-Mounts). -->
    <template v-if="!isMobile">
      <DashboardView v-if="!isLogs" />
      <LogsView v-else />
    </template>

    <!-- Mobile: Pager-Viewport (klippt horizontal) + Track mit beiden Bodies. -->
    <div v-else ref="pagerEl" class="overflow-x-clip">
      <div class="flex w-[200%] items-start" :style="trackStyle">
        <div class="w-1/2 shrink-0" :class="{ 'max-h-0 overflow-hidden': paneCollapsed(0) }"><DashboardView /></div>
        <div class="w-1/2 shrink-0" :class="{ 'max-h-0 overflow-hidden': paneCollapsed(1) }"><LogsView /></div>
      </div>
    </div>
  </div>
</template>
