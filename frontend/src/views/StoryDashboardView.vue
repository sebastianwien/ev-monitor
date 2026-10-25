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
import { analytics } from '../services/analytics'
import { petrolCostEur, type TileId } from '../composables/storyModel'
import { DAY } from '../composables/storyMoments'
import { useMomentSeen } from '../composables/useMomentSeen'
import { useMomentView } from '../composables/useMomentView'
import { autoSyncProviderFor } from '../composables/useCarAutoSyncProvider'
import { connectionsOf } from '../api/smartcarService'
import { shareOrCopy, type ShareOutcome } from '../utils/shareOrCopy'
import { carDisplayName } from '../utils/enumLabel'
import { WORKSPACE_TABS } from '../config/tabs'
import PublicNav from '../components/shared/PublicNav.vue'
import ChoiceChip from '../components/shared/ChoiceChip.vue'
import ModelBox from '../components/models/ModelBox.vue'
import StoryHero from '../components/story/StoryHero.vue'
import StoryMomentHero from '../components/story/StoryMomentHero.vue'
import StoryLatestCards from '../components/story/StoryLatestCards.vue'
import StoryLiveHero from '../components/story/StoryLiveHero.vue'
import StoryTileRow from '../components/story/StoryTileRow.vue'
import StoryFeedItem from '../components/story/StoryFeedItem.vue'
import StoryMonthSwitch from '../components/story/StoryMonthSwitch.vue'
import LicensePlate from '../components/car/LicensePlate.vue'

const StorySheet = defineAsyncComponent(() => import('../components/story/StorySheet.vue'))
const StoryPriceSheet = defineAsyncComponent(() => import('../components/story/StoryPriceSheet.vue'))
const ChargingHeatMap = defineAsyncComponent(() => import('../components/dashboard/ChargingHeatMap.vue'))
const BottomSheet = defineAsyncComponent(() => import('../components/shared/BottomSheet.vue'))

const { t, locale } = useI18n()
const router = useRouter()
const authStore = useAuthStore()
const { haptic } = useHaptic()
const upsellTo = useAnalyticsUpsellTarget()
const { set: setOptIn, alwaysMonth } = useStoryOptIn()

// Das Template hat genau ein Wurzelelement, ohne Kommentar davor: die Routen-Transition in App.vue
// (mode out-in) kann ein Fragment nicht ausblenden, der Leave endet nie und die router-view bleibt leer.
// Aufbau wie die Modellseiten: eigene gruene Nav (Route-Meta publicNav), opake Flaeche ueber dem
// Wallpaper der App, Boxen mobil randlos, ab md mit Kante.
const ctx = provideCarContext()
const { selectedCarId, cars, wltp, currentOdometerKm, smartcarStatus } = ctx

const selectedCar = computed(() => cars.value.find((c: any) => c.id === selectedCarId.value) ?? cars.value[0] ?? null)
const sohPercent = computed(() => selectedCar.value?.batteryDegradationPercent != null ? 100 - selectedCar.value.batteryDegradationPercent : null)
const effectiveCapacity = computed<number | null>(() => selectedCar.value?.effectiveBatteryCapacityKwh ?? null)

// Live-Laden: Polling nur, wenn das Gate offen ist (gleiche Regel wie die LiveChargingCard).
const liveGate = computed(() => authStore.canViewLiveCharging(selectedCar.value?.brand ?? null))
const liveCarId = computed(() => liveGate.value ? selectedCarId.value : null)
const { data: live } = useChargingLive(liveCarId)
const liveActive = computed(() => !!live.value?.isActive && live.value?.chargingType === 'DC')
watch(liveActive, (v) => { if (v) analytics.track('story_live_shown') })

// AutoSync-Satz im Stille-Moment: Marke hat einen Weg, dieses Auto ist nicht verbunden.
const autoSyncOffer = computed(() => !!selectedCar.value
  && autoSyncProviderFor(selectedCar.value) !== 'NONE'
  && !connectionsOf(smartcarStatus.value).some(c => c.carId === selectedCar.value.id))

const storyCtx = (): StoryCarContext => ({
  wltpConsumption: wltp.value?.officialConsumptionKwhPer100km ?? null,
  sohPercent: sohPercent.value,
  odometerKm: currentOdometerKm.value,
  effectiveBatteryCapacityKwh: effectiveCapacity.value,
  energySplitLocked: !authStore.canViewEnergySplit,
  canViewLiveAnalytics: authStore.canViewLiveAnalytics,
  liveActive: liveActive.value,
  autoSync: autoSyncOffer.value,
  alwaysMonth: alwaysMonth.value,
})
const story = useDashboardStory(selectedCarId, storyCtx)
const { month, months, offset, tiles, feed, days, monthStats, hasCharges, loading, error, layers, quality } = story


const { isVehicleCharging } = useVehicleCharging(cars, smartcarStatus)
const charging = computed(() => selectedCar.value ? isVehicleCharging(selectedCar.value) : false)

const monthLabel = (m: StoryMonth) => new Date(m.year, m.month - 1, 1).toLocaleDateString(locale.value, { month: 'long' })
const monthLabelShort = (m: StoryMonth) => new Date(m.year, m.month - 1, 1).toLocaleDateString(locale.value, { month: 'short' })
const sixMonthLabels = computed(() => Array.from({ length: 6 }, (_, i) =>
  new Date(month.value.year, month.value.month - 1 - (5 - i), 1).toLocaleDateString(locale.value, { month: 'short' })))

// Hero: Live vor Moment vor Monat. "Zum Monat" zeigt fuer diesen Besuch den Monat statt des Moments.
const moment = story.moment
const showMonth = ref(false)
watch(selectedCarId, () => { showMonth.value = false })
const heroMode = computed<'live' | 'moment' | 'month'>(() => {
  if (moment.value.kind === 'live') return live.value ? 'live' : 'month'
  return moment.value.kind === 'month' || showMonth.value ? 'month' : 'moment'
})
const priceInHero = computed(() => heroMode.value === 'moment' && moment.value.kind === 'just_charged'
  && moment.value.costEur == null && moment.value.logId === story.latest.value?.charge?.id)
const carName = computed(() => selectedCar.value ? carDisplayName(selectedCar.value) : '')
const heroView = useMomentView({
  get moment() { return moment.value },
  get carId() { return selectedCarId.value },
  get carName() { return carName.value },
})

const { markShown, dismiss } = useMomentSeen()
watch(() => heroMode.value === 'moment' ? moment.value.key : null, (key) => {
  if (!key) return
  markShown(key)
  analytics.track('story_moment_shown', { moment: moment.value.kind })
}, { immediate: true })

const shareStatus = ref<ShareOutcome | null>(null)
async function shareMoment() {
  const v = heroView.value
  shareStatus.value = await shareOrCopy({
    title: v.eyebrow,
    text: [v.sentence, v.sub].filter(Boolean).join(' '),
    url: window.location.origin,
  })
  if (shareStatus.value !== 'shared') setTimeout(() => { shareStatus.value = null }, 3000)
}

function onMomentAct(id: string) {
  haptic()
  analytics.track('story_moment_tapped', { moment: moment.value.kind, action: id })
  if (id === 'share') shareMoment()
  else if (id === 'winter') openSheet('range')
  else if (id === 'prices') openPrices.value = true
  else if (id === 'price_unknown' && moment.value.kind === 'gap') story.markPriceUnknown(moment.value.logId)
  else if (id === 'battery') {
    // Ohne Akku-Kachel (kein SoH am Auto) fuehrt der Verlauf zur Fahrzeugseite.
    if (tiles.value.some(x => x.id === 'battery')) openSheet('battery')
    else router.push('/cars')
  }
}
function onMomentDismiss() {
  analytics.track('story_moment_dismissed', { moment: moment.value.kind })
  dismiss(moment.value.key, Date.now(), 30 * DAY)
}

const petrolEur = computed(() => petrolCostEur(monthStats.value?.totalDistanceKm ?? null, story.petrol.value.eurPerLitre))

// Desktop: die Ziele des eingeloggten Bereichs als Chips (mobil traegt die Bottom-Nav). Die Story ist das Dashboard.
const workspaceTabs = computed(() => WORKSPACE_TABS.map(tab => ({ to: tab.to, label: t(tab.labelKey), active: tab.to === '/dashboard' })))

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
const openPrices = ref(false)
/** Was der Nutzer bei der Ladung gezahlt hat, die den Moment "zu teuer geladen" ausloest. */
const expensivePrice = computed(() => moment.value.kind === 'expensive' ? moment.value.pricePerKwh : null)
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
  <div class="min-h-screen bg-gray-100 md:bg-gray-50 dark:bg-gray-950" data-testid="story-view">
    <PublicNav home />
    <main class="mx-auto grid max-w-5xl gap-2 py-2 md:gap-5 md:px-6 md:py-8">
      <nav class="hidden flex-wrap gap-2 md:flex" :aria-label="t('story.workspace_aria')">
        <ChoiceChip v-for="tab in workspaceTabs" :key="tab.to" :href="tab.to" :active="tab.active" :aria-current="tab.active ? 'page' : undefined">
          {{ tab.label }}
        </ChoiceChip>
      </nav>

      <ModelBox>
        <header class="flex flex-wrap items-center justify-between gap-3">
          <div class="flex min-w-0 items-center gap-2">
            <h1 class="truncate text-[15px] font-extrabold text-gray-900 dark:text-gray-50">
              {{ selectedCar ? carDisplayName(selectedCar) : t('story.title') }}
            </h1>
            <LicensePlate v-if="selectedCar?.licensePlate" :plate="selectedCar.licensePlate" size="sm" class="hidden sm:inline-flex" />
          </div>
          <StoryMonthSwitch :months="months" :offset="offset" :label-of="monthLabelShort" @select="selectMonth" />
        </header>

        <p v-if="charging && !liveActive" class="flex items-center gap-2 text-[13px] text-story-accent dark:text-story-accent-dark" data-testid="story-live">
          <span class="h-1.5 w-1.5 rounded-full bg-current motion-safe:animate-pulse" />{{ t('story.live_charging') }}
        </p>

        <div v-if="!noteSeen" class="flex items-start gap-3 rounded-md border border-dashed border-gray-300 px-3 py-2 text-xs text-gray-600 dark:border-gray-600 dark:text-gray-300">
          <span class="flex-1">{{ t('story.experiment_note') }}</span>
          <button type="button" class="-my-2 min-h-[44px] font-semibold underline" @click="dismissNote">OK</button>
        </div>

        <div v-if="loading && !monthStats" class="animate-pulse" aria-busy="true">
          <div class="h-9 w-72 max-w-full rounded bg-gray-200 dark:bg-gray-700" />
          <div class="mt-3 h-9 w-56 max-w-full rounded bg-gray-200 dark:bg-gray-700" />
          <div class="mt-6 flex gap-3">
            <div v-for="i in 3" :key="i" class="h-36 flex-1 rounded-2xl bg-gray-200 dark:bg-gray-700" />
          </div>
        </div>

        <div v-else-if="error" class="text-sm text-rose-700 dark:text-rose-300">
          {{ t('story.error') }}
          <button type="button" class="ml-2 min-h-[44px] font-semibold underline" @click="story.reload()">{{ t('story.retry') }}</button>
        </div>

        <div v-else-if="!hasCharges && heroMode === 'month'" class="py-4 text-center">
          <p class="text-lg font-bold text-gray-900 dark:text-gray-50">{{ t('story.empty_title', { month: monthLabel(month) }) }}</p>
          <p class="mt-1 text-sm text-gray-600 dark:text-gray-300">{{ t('story.empty_body') }}</p>
        </div>

        <template v-else>
          <div @pointerdown="onPointerDown" @pointerup="onPointerUp">
            <Transition name="story-hero" mode="out-in">
              <StoryLiveHero v-if="heroMode === 'live' && live" key="live" :live="live" />
              <div v-else-if="heroMode === 'moment'" :key="moment.key" class="grid gap-3">
                <StoryMomentHero :moment="moment" :car-id="selectedCarId" :car-name="carName" @act="onMomentAct" @dismiss="onMomentDismiss" />
                <p v-if="shareStatus === 'copied' || shareStatus === 'failed'" class="text-sm text-gray-600 dark:text-gray-300" role="status">
                  {{ shareStatus === 'copied' ? t('story.moment_copied') : t('story.moment_share_failed') }}
                </p>
                <button
                  v-if="hasCharges"
                  type="button"
                  data-testid="story-moment-show-month"
                  class="inline-flex min-h-[44px] items-center gap-1 justify-self-start text-sm font-semibold text-gray-600 underline underline-offset-2 dark:text-gray-300"
                  @click="showMonth = true">
                  {{ t('story.moment_show_month', { month: monthLabel(month) }) }}<ChevronRightIcon class="h-4 w-4" aria-hidden="true" />
                </button>
              </div>
              <StoryHero
                v-else
                key="month"
                :month-label="monthLabel(month)"
                :distance-km="monthStats?.totalDistanceKm ?? null"
                :energy-cost-eur="monthStats?.energyCostEur ?? null"
                :charges="monthStats?.totalCharges ?? 0"
                :petrol-eur="petrolEur"
                :petrol-price-per-litre="story.petrol.value.eurPerLitre"
                :petrol-price-live="story.petrol.value.live"
                :days="days"
                :layers="layers"
                :quality="quality"
                @layers="(open) => open && analytics.track('story_layers_opened')" />
            </Transition>
          </div>
          <StoryTileRow v-if="hasCharges" :tiles="tiles" @open="openSheet" />
        </template>
      </ModelBox>

      <ModelBox v-if="story.latest.value" aria-labelledby="story-latest-title">
        <h2 id="story-latest-title" class="text-[21px] font-bold tracking-tight text-gray-900 md:text-[24px] dark:text-gray-100">{{ t('story.latest_title') }}</h2>
        <StoryLatestCards :latest="story.latest.value" :car-id="selectedCarId" :price-in-hero="priceInHero" />
      </ModelBox>

      <ModelBox v-if="!loading && !error && (hasCharges || liveActive)" aria-labelledby="story-feed-title">
        <div class="grid gap-1">
          <h2 id="story-feed-title" class="text-[21px] font-bold tracking-tight text-gray-900 md:text-[24px] dark:text-gray-100">{{ t('story.feed_title') }}</h2>
          <p class="text-[13.5px] text-gray-500 dark:text-gray-400">{{ t('story.feed_sub') }}</p>
        </div>
        <div>
          <StoryFeedItem
            v-for="item in feed"
            :key="item.moment?.key ?? item.id"
            :item="item"
            :upsell-to="upsellTo"
            :what-if="story.whatIf.value"
            :car-id="selectedCarId"
            :car-name="carName" />
        </div>
        <button
          type="button"
          data-testid="story-map-open"
          class="flex min-h-[44px] w-full items-center gap-3 rounded-2xl bg-white px-4 py-3.5 text-left edge-card transition-transform active:translate-x-0.5 active:translate-y-0.5 active:shadow-none dark:bg-gray-800"
          @click="openMap = true">
          <MapIcon class="h-6 w-6 shrink-0 text-story-accent dark:text-story-accent-dark" />
          <span class="min-w-0 flex-1">
            <span class="block font-semibold text-gray-900 dark:text-gray-50">{{ t('story.map_title') }}</span>
            <span class="block text-xs text-gray-500 dark:text-gray-400">{{ t('story.map_sub') }}</span>
          </span>
          <ChevronRightIcon class="h-5 w-5 text-gray-400" />
        </button>
      </ModelBox>

      <div class="px-4 pb-4 md:px-0">
        <button type="button" class="inline-flex min-h-[44px] items-center gap-2 text-sm text-gray-500 underline underline-offset-2 dark:text-gray-400" data-testid="story-back" @click="backToClassic">
          <ArrowUturnLeftIcon class="h-4 w-4" />{{ t('story.back_to_dashboard') }}
        </button>
      </div>
    </main>

    <StorySheet v-if="sheetTile" :tile="sheetTile" :context="sheetContext" @close="openTile = null" />

    <StoryPriceSheet v-if="openPrices && story.benchmark.value" :benchmark="story.benchmark.value" :price-per-kwh="expensivePrice" @close="openPrices = false" />

    <BottomSheet v-if="openMap" :label="t('story.map_title')" panel-class="sm:max-w-2xl" @close="openMap = false">
      <template #default="{ close }">
        <div class="p-3">
          <div class="h-[60vh]">
            <ChargingHeatMap :car-id="selectedCarId" time-range="LAST_12_MONTHS" />
          </div>
          <button type="button" class="mt-3 min-h-[44px] w-full rounded-full border border-gray-300 py-3 text-sm font-semibold dark:border-gray-600" @click="close">{{ t('story.sheet_close') }}</button>
        </div>
      </template>
    </BottomSheet>
  </div>
</template>

<style scoped>
.story-hero-enter-active, .story-hero-leave-active { transition: opacity 260ms ease, transform 260ms ease; }
.story-hero-enter-from { opacity: 0; transform: translateY(8px); }
.story-hero-leave-to { opacity: 0; transform: translateY(-8px); }
@media (prefers-reduced-motion: reduce) {
  .story-hero-enter-active, .story-hero-leave-active { transition: none; }
}
</style>
