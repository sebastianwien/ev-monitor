<script setup lang="ts">
import { ref, computed, defineAsyncComponent, onMounted, onUnmounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ArrowUturnLeftIcon, MapIcon, ChevronRightIcon } from '@heroicons/vue/24/outline'
import { provideCarContext } from '../composables/useCarContext'
import { useDashboardStory, type StoryMonth, type StoryCarContext } from '../composables/useDashboardStory'
import { useStoryOptIn } from '../composables/useStoryOptIn'
import { useChargingLive } from '../composables/useChargingLive'
import { registerSoftReload } from '../composables/usePullToRefresh'
import { useAuthStore } from '../stores/auth'
import { useAnalyticsUpsellTarget } from '../composables/useUpsellTarget'
import { useVehicleCharging } from '../composables/useVehicleCharging'
import { useHaptic } from '../composables/useHaptic'
import { useLocaleFormat } from '../composables/useLocaleFormat'
import { analytics } from '../services/analytics'
import { petrolCostEur, type TileId } from '../composables/storyModel'
import { calcCostPer100km } from '../composables/useDashboardStats'
import { carDisplayName } from '../utils/enumLabel'
import StoryHero from '../components/story/StoryHero.vue'
import StoryLiveHero from '../components/story/StoryLiveHero.vue'
import StoryTileRow from '../components/story/StoryTileRow.vue'
import StoryFeedItem from '../components/story/StoryFeedItem.vue'
import StoryMonthSwitch from '../components/story/StoryMonthSwitch.vue'
import LicensePlate from '../components/car/LicensePlate.vue'

const StorySheet = defineAsyncComponent(() => import('../components/story/StorySheet.vue'))
const ChargingHeatMap = defineAsyncComponent(() => import('../components/dashboard/ChargingHeatMap.vue'))
const BottomSheet = defineAsyncComponent(() => import('../components/shared/BottomSheet.vue'))

const { t, locale } = useI18n()
const { formatCostPerDistance } = useLocaleFormat()
const router = useRouter()
const authStore = useAuthStore()
const { haptic } = useHaptic()
const upsellTo = useAnalyticsUpsellTarget()
const { set: setOptIn } = useStoryOptIn()

// Das Template hat genau ein Wurzelelement, ohne Kommentar davor: die Routen-Transition in App.vue
// (mode out-in) kann ein Fragment nicht ausblenden, der Leave endet nie und die router-view bleibt leer.
// Die Flaeche ist opak, weil die App hinter angemeldete Seiten ein Wallpaper legt.
const ctx = provideCarContext()
const { selectedCarId, cars, wltp, currentOdometerKm, smartcarStatus } = ctx

const selectedCar = computed(() => cars.value.find((c: any) => c.id === selectedCarId.value) ?? cars.value[0] ?? null)
const sohPercent = computed(() => selectedCar.value?.batteryDegradationPercent != null ? 100 - selectedCar.value.batteryDegradationPercent : null)
const effectiveCapacity = computed<number | null>(() => selectedCar.value?.effectiveBatteryCapacityKwh ?? null)

const storyCtx = (): StoryCarContext => ({
  wltpConsumption: wltp.value?.officialConsumptionKwhPer100km ?? null,
  sohPercent: sohPercent.value,
  odometerKm: currentOdometerKm.value,
  effectiveBatteryCapacityKwh: effectiveCapacity.value,
  energySplitLocked: !authStore.canViewEnergySplit,
  canViewLiveAnalytics: authStore.canViewLiveAnalytics,
})
const story = useDashboardStory(selectedCarId, storyCtx)
const { month, months, offset, tiles, feed, days, monthStats, hasCharges, loading, error, layers, quality } = story

// Live-Laden: Polling nur, wenn das Gate offen ist (gleiche Regel wie die LiveChargingCard).
const liveGate = computed(() => authStore.canViewLiveCharging(selectedCar.value?.brand ?? null))
const liveCarId = computed(() => liveGate.value ? selectedCarId.value : null)
const { data: live } = useChargingLive(liveCarId)
const liveActive = computed(() => !!live.value?.isActive && live.value?.chargingType === 'DC')
watch(liveActive, (v) => { if (v) analytics.track('story_live_shown') })

const { isVehicleCharging } = useVehicleCharging(cars, smartcarStatus)
const charging = computed(() => selectedCar.value ? isVehicleCharging(selectedCar.value) : false)

const monthLabel = (m: StoryMonth) => new Date(m.year, m.month - 1, 1).toLocaleDateString(locale.value, { month: 'long' })
const monthLabelShort = (m: StoryMonth) => new Date(m.year, m.month - 1, 1).toLocaleDateString(locale.value, { month: 'short' })
const sixMonthLabels = computed(() => Array.from({ length: 6 }, (_, i) =>
  new Date(month.value.year, month.value.month - 1 - (5 - i), 1).toLocaleDateString(locale.value, { month: 'short' })))

const costPer100km = computed(() => calcCostPer100km(monthStats.value?.avgCostPerKwh ?? null, monthStats.value?.avgConsumptionKwhPer100km ?? null))
const petrolExtra = computed(() => {
  const petrol = petrolCostEur(monthStats.value?.totalDistanceKm ?? null, story.petrol.value.eurPerLitre)
  return petrol == null || !monthStats.value ? null : petrol - monthStats.value.energyCostEur
})
const peerTile = computed(() => tiles.value.find(x => x.id === 'peer'))

// Monatswechsel: Chips oder horizontaler Wisch auf dem Hero. View Transition, wo verfuegbar.
function selectMonth(o: number) {
  if (o === offset.value) return
  haptic()
  analytics.track('story_month_changed', { offset: o })
  const apply = () => story.setOffset(o)
  const d = document as Document & { startViewTransition?: (cb: () => void) => void }
  d.startViewTransition ? d.startViewTransition(apply) : apply()
}
let swipeX: number | null = null
function onPointerDown(e: PointerEvent) { swipeX = e.clientX }
function onPointerUp(e: PointerEvent) {
  if (swipeX == null) return
  const dx = e.clientX - swipeX
  swipeX = null
  if (Math.abs(dx) < 60) return
  const next = dx < 0 ? offset.value + 1 : offset.value - 1
  if (next <= 0 && next >= -2) selectMonth(next)
}

const openTile = ref<TileId | null>(null)
const openMap = ref(false)
function openSheet(id: TileId) {
  haptic()
  analytics.track('story_tile_opened', { tile: id })
  openTile.value = id
}
const sheetTile = computed(() => tiles.value.find(x => x.id === openTile.value) ?? null)
const sheetContext = computed(() => ({
  stats: monthStats.value,
  monthLabels: sixMonthLabels.value,
  whatIf: story.whatIf.value,
  temperature: story.temperature.value,
  sohHistory: story.sohHistory.value,
  effectiveCapacityKwh: effectiveCapacity.value,
}))

const noteSeen = ref(false)
try { noteSeen.value = localStorage.getItem('story_note_seen') === 'true' } catch { /* egal */ }
function dismissNote() {
  noteSeen.value = true
  try { localStorage.setItem('story_note_seen', 'true') } catch { /* egal */ }
}

function backToClassic() {
  setOptIn(false)
  router.push('/dashboard')
}

// Pull-to-refresh (native App) laedt hier die Daten neu statt der ganzen Seite.
const unregisterReload = registerSoftReload(() => story.reload())
onUnmounted(unregisterReload)
onMounted(() => analytics.track('story_viewed'))
</script>

<template>
  <div class="story min-h-dvh px-4 pb-10 bg-gray-50 dark:bg-gray-900 md:min-h-0 md:max-w-6xl md:mx-auto md:my-6 md:px-8 md:pb-12 md:rounded-2xl md:shadow-sm" style="timeline-scope: --story-hero" data-testid="story-view">
    <header class="sticky top-[env(safe-area-inset-top,0px)] z-10 bg-gray-50 dark:bg-gray-900 -mx-4 px-4 md:mx-0 md:px-0 pt-3 md:pt-5 pb-1 flex items-center justify-between gap-3">
      <div class="min-w-0 flex items-center gap-2">
        <span class="font-extrabold text-[15px] truncate">
          {{ selectedCar ? carDisplayName(selectedCar.brand, selectedCar.model) : t('story.title') }}
        </span>
        <LicensePlate v-if="selectedCar?.licensePlate" :plate="selectedCar.licensePlate" size="sm" class="hidden sm:inline-flex" />
        <!-- Kompakte Hero-Zahl: blendet per Scroll-Timeline ein, sobald der Hero aus dem Bild ist. Ohne Browser-Support bleibt sie unsichtbar. -->
        <span v-if="costPer100km != null && !liveActive" class="story-header-value shrink-0 text-sm font-extrabold tabular-nums text-story-accent dark:text-story-accent-dark md:hidden" aria-hidden="true">
          {{ formatCostPerDistance(costPer100km) }}
        </span>
      </div>
      <StoryMonthSwitch :months="months" :offset="offset" :label-of="monthLabelShort" @select="selectMonth" />
    </header>

    <p v-if="charging && !liveActive" class="flex items-center gap-2 text-[13px] text-story-accent dark:text-story-accent-dark pt-1" data-testid="story-live">
      <span class="w-1.5 h-1.5 rounded-full bg-current motion-safe:animate-pulse" />{{ t('story.live_charging') }}
    </p>

    <div v-if="!noteSeen" class="mt-2 flex items-start gap-3 text-xs text-gray-600 dark:text-gray-300 border border-dashed border-gray-300 dark:border-gray-600 rounded-md px-3 py-2">
      <span class="flex-1">{{ t('story.experiment_note') }}</span>
      <button type="button" class="font-semibold underline min-h-[44px] -my-2" @click="dismissNote">OK</button>
    </div>

    <div v-if="loading && !monthStats" class="animate-pulse mt-4" aria-busy="true">
      <div class="h-3 w-32 bg-gray-200 dark:bg-gray-700 rounded" />
      <div class="h-16 w-48 bg-gray-200 dark:bg-gray-700 rounded mt-3" />
      <div class="h-4 w-72 max-w-full bg-gray-200 dark:bg-gray-700 rounded mt-4" />
      <div class="flex gap-3 mt-6">
        <div v-for="i in 3" :key="i" class="h-36 flex-1 bg-gray-200 dark:bg-gray-700 rounded-xl" />
      </div>
    </div>

    <div v-else-if="error" class="mt-6 text-sm text-rose-700 dark:text-rose-300">
      {{ t('story.error') }}
      <button type="button" class="ml-2 underline font-semibold min-h-[44px]" @click="story.reload()">{{ t('story.retry') }}</button>
    </div>

    <div v-else-if="!hasCharges && !liveActive" class="mt-8 text-center">
      <p class="text-lg font-bold text-gray-900 dark:text-gray-50">{{ t('story.empty_title', { month: monthLabel(month) }) }}</p>
      <p class="text-sm text-gray-600 dark:text-gray-300 mt-1">{{ t('story.empty_body') }}</p>
    </div>

    <div v-else class="md:grid md:grid-cols-[5fr_6fr] md:gap-10 md:items-start">
      <div class="md:sticky md:top-16" @pointerdown="onPointerDown" @pointerup="onPointerUp">
        <Transition name="story-hero" mode="out-in">
          <StoryLiveHero v-if="liveActive && live" key="live" :live="live" />
          <StoryHero
            v-else
            key="month"
            :cost-per100km="costPer100km"
            :petrol-extra-eur="petrolExtra"
            :petrol-price-per-litre="story.petrol.value.eurPerLitre"
            :petrol-price-live="story.petrol.value.live"
            :peer-delta-percent="peerTile?.deltaPercent ?? null"
            :peer-count="peerTile?.peerCount ?? 0"
            :month-label="monthLabel(month)"
            :days="days"
            :layers="layers"
            :distance-km="monthStats?.totalDistanceKm ?? null"
            :quality="quality"
            :latest="story.latest.value"
            @layers="(open) => open && analytics.track('story_layers_opened')" />
        </Transition>

        <StoryTileRow :tiles="tiles" @open="openSheet" />
      </div>

      <section class="pt-1">
        <h2 class="text-lg font-extrabold text-gray-900 dark:text-gray-50 mt-3">{{ t('story.feed_title') }}</h2>
        <p class="text-[13px] text-gray-500 dark:text-gray-400">{{ t('story.feed_sub') }}</p>
        <StoryFeedItem v-for="item in feed" :key="item.id" :item="item" :upsell-to="upsellTo" :what-if="story.whatIf.value" />

        <button
          type="button"
          data-testid="story-map-open"
          class="mt-4 w-full flex items-center gap-3 rounded-xl bg-white dark:bg-gray-800 shadow-sm px-4 py-3.5 text-left min-h-[44px]"
          @click="openMap = true">
          <MapIcon class="w-6 h-6 shrink-0 text-story-accent dark:text-story-accent-dark" />
          <span class="flex-1 min-w-0">
            <span class="block font-semibold text-gray-900 dark:text-gray-50">{{ t('story.map_title') }}</span>
            <span class="block text-xs text-gray-500 dark:text-gray-400">{{ t('story.map_sub') }}</span>
          </span>
          <ChevronRightIcon class="w-5 h-5 text-gray-400" />
        </button>

        <button type="button" class="mt-8 inline-flex items-center gap-2 text-sm text-gray-500 dark:text-gray-400 underline underline-offset-2 min-h-[44px]" data-testid="story-back" @click="backToClassic">
          <ArrowUturnLeftIcon class="w-4 h-4" />{{ t('story.back_to_dashboard') }}
        </button>
      </section>
    </div>

    <StorySheet v-if="sheetTile" :tile="sheetTile" :context="sheetContext" @close="openTile = null" />

    <BottomSheet v-if="openMap" :label="t('story.map_title')" panel-class="sm:max-w-2xl" @close="openMap = false">
      <template #default="{ close }">
        <div class="p-3">
          <div class="h-[60vh]">
            <ChargingHeatMap :car-id="selectedCarId" time-range="LAST_12_MONTHS" />
          </div>
          <button type="button" class="mt-3 w-full rounded-full border border-gray-300 dark:border-gray-600 py-3 min-h-[44px] text-sm font-semibold" @click="close">{{ t('story.sheet_close') }}</button>
        </div>
      </template>
    </BottomSheet>
  </div>
</template>

<style scoped>
/* Kompakte Zahl in der Kopfzeile: unsichtbar, bis der Hero (view-timeline --story-hero) nach oben hinausscrollt. */
.story-header-value { opacity: 0; }
@supports (animation-timeline: view()) {
  .story-header-value {
    animation: story-header-value linear both;
    animation-timeline: --story-hero;
    animation-range: exit 40% exit 100%;
  }
}
@keyframes story-header-value {
  from { opacity: 0; transform: translateY(4px); }
  to { opacity: 1; transform: translateY(0); }
}
.story-hero-enter-active, .story-hero-leave-active { transition: opacity 260ms ease, transform 260ms ease; }
.story-hero-enter-from { opacity: 0; transform: translateY(8px); }
.story-hero-leave-to { opacity: 0; transform: translateY(-8px); }
@media (prefers-reduced-motion: reduce) {
  .story-hero-enter-active, .story-hero-leave-active { transition: none; }
}
</style>
