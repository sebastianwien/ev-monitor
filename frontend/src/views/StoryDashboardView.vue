<script setup lang="ts">
import { ref, computed, defineAsyncComponent, onMounted, onUnmounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ArrowUturnLeftIcon, ChevronRightIcon } from '@heroicons/vue/24/outline'
import { provideCarContext } from '../composables/useCarContext'
import { useDashboardStory, type StoryMonth, type StoryCarContext } from '../composables/useDashboardStory'
import { useStoryOptIn } from '../composables/useStoryOptIn'
import { useChargingLive } from '../composables/useChargingLive'
import { registerSoftReload } from '../composables/usePullToRefresh'
import { useAuthStore } from '../stores/auth'
import { useCountryStore } from '../stores/country'
import { useAnalyticsUpsellTarget } from '../composables/useUpsellTarget'
import { useVehicleCharging } from '../composables/useVehicleCharging'
import { useHaptic } from '../composables/useHaptic'
import { useIsMobile } from '../composables/useIsMobile'
import { analytics } from '../services/analytics'
import { petrolCostEur, type TileId } from '../composables/storyModel'
import { DAY } from '../composables/storyMoments'
import { useMomentSeen } from '../composables/useMomentSeen'
import { useMomentView } from '../composables/useMomentView'
import { autoSyncProviderFor } from '../composables/useCarAutoSyncProvider'
import { connectionsOf } from '../api/smartcarService'
import { shareOrCopy, type ShareOutcome } from '../utils/shareOrCopy'
import { carDisplayName } from '../utils/enumLabel'
import { WORKSPACE_TABS, WORKSPACE_LINKS } from '../config/tabs'
import PublicNav from '../components/shared/PublicNav.vue'
import LeaderboardTicker from '../components/shared/LeaderboardTicker.vue'
import ChoiceChip from '../components/shared/ChoiceChip.vue'
import StoryHero from '../components/story/StoryHero.vue'
import StoryMomentHero from '../components/story/StoryMomentHero.vue'
import StoryLatestCards from '../components/story/StoryLatestCards.vue'
import StoryLiveHero from '../components/story/StoryLiveHero.vue'
import StoryTileRow from '../components/story/StoryTileRow.vue'
import StoryFeed from '../components/story/StoryFeed.vue'
import DataQualityLine from '../components/story/DataQualityLine.vue'
import StoryMonthSwitch from '../components/story/StoryMonthSwitch.vue'
import LicensePlate from '../components/car/LicensePlate.vue'
import api from '../api/axios'
import type { PlacePeriod } from '../composables/storyPlaces'
import type { EvLogResponse } from '../components/dashboard/EditLogModal.vue'
import { dayKeyOf } from '../composables/storyDay'
import { useLogsRefreshStore } from '../stores/logsRefresh'

const StorySheet = defineAsyncComponent(() => import('../components/story/StorySheet.vue'))
const StoryPriceSheet = defineAsyncComponent(() => import('../components/story/StoryPriceSheet.vue'))
const PlacesSheet = defineAsyncComponent(() => import('../components/story/places/PlacesSheet.vue'))
const EditLogModal = defineAsyncComponent(() => import('../components/dashboard/EditLogModal.vue'))
const StoryDaySheet = defineAsyncComponent(() => import('../components/story/day/StoryDaySheet.vue'))
const LogFormModal = defineAsyncComponent(() => import('../components/log-form/LogFormModal.vue'))

const { t, locale } = useI18n()
const router = useRouter()
const authStore = useAuthStore()
const countryStore = useCountryStore()
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
/** Modell ohne Variante für den Vergleich mit allen Fahrern des Modells. */
const modelName = computed<string>(() => selectedCar.value?.modelDisplayName ?? '')
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
async function share(title: string, text: string) {
  shareStatus.value = await shareOrCopy({ title, text, url: window.location.origin })
  if (shareStatus.value !== 'shared') setTimeout(() => { shareStatus.value = null }, 3000)
}
function shareMoment() {
  const v = heroView.value
  share(v.eyebrow, [v.sentence, v.sub].filter(Boolean).join(' '))
}
function shareMonth(text: string) {
  haptic()
  analytics.track('story_hero_shared')
  share(monthLabel(month.value), text)
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

// Unter lg steht der Feed zwischen "Zuletzt" und den Kacheln, ab lg rechts neben dem Hero. Einmal gerendert, nicht zweimal.
const narrow = useIsMobile('(max-width: 1023px)')
const sheetIds = computed(() => tiles.value.map(x => x.id))
const showFeed = computed(() => !loading.value && !error.value && (hasCharges.value || liveActive.value))

// Desktop: die Ziele des eingeloggten Bereichs als Chips (mobil traegt die Bottom-Nav). Die Story ist das Dashboard.
const workspaceTabs = computed(() => [...WORKSPACE_TABS, ...WORKSPACE_LINKS].map(tab => ({ to: tab.to, label: t(tab.labelKey), active: tab.to === '/dashboard' })))
// Community-Ticker wie im App-Kopf: nicht im Demo-Konto, nur DACH.
const showTicker = computed(() => !authStore.isDemoAccount && ['DE', 'AT', 'CH'].includes(countryStore.country))

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
/** Zeitraum der Ladekarte, bleibt beim Schließen und Wiederöffnen erhalten. */
const placesPeriod = ref<PlacePeriod>('12m')
const operatorLog = ref<EvLogResponse | null>(null)
const placesSheet = ref<{ reload: () => void } | null>(null)
/** "Betreiber eintragen": die Ladung öffnet im Ort, über der Karte und leichter abgedunkelt. */
async function openOperator(logId: string) {
  try {
    operatorLog.value = (await api.get<EvLogResponse>(`/logs/${logId}`)).data
  } catch { /* Ladung weg oder offline: die Karte bleibt einfach stehen */ }
}
function closeOperator(changed: boolean) {
  operatorLog.value = null
  if (!changed) return
  placesSheet.value?.reload()
  story.reload()
}
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
  monthName: monthLabel(month.value),
  runningMonth: month.value.offset === 0,
  modelPeers: story.modelPeers.value,
  modelName: modelName.value,
  temperature: story.temperature.value,
  sohHistory: story.sohHistory.value,
  effectiveCapacityKwh: effectiveCapacity.value,
  hasFixedCosts: story.hasFixedCosts.value,
  loadMonthlyCosts: story.loadMonthlyCosts,
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

// Tagesblatt: Tag im Zeitstrahl antippen. "Ladung nachtragen" schliesst das Blatt, oeffnet den
// Erfassen-Wizard mit dem Datum und kehrt danach zum selben Tag zurueck.
const openDay = ref<string | null>(null)
const todayKey = () => dayKeyOf(new Date())
function onOpenDay(d: number) {
  const pad = (n: number) => String(n).padStart(2, '0')
  openDay.value = `${month.value.year}-${pad(month.value.month)}-${pad(d)}`
}
const addCharge = ref<{ day: string; loggedAt: string } | null>(null)
function onAddCharge(loggedAt: string) {
  addCharge.value = { day: openDay.value!, loggedAt }
  openDay.value = null
}
function onAddChargeClosed() {
  openDay.value = addCharge.value?.day ?? null
  addCharge.value = null
}
// Rueckgaengig im Loesch-Toast (Ladung oder Fahrt) aendert die Zahlen des Monats
watch(() => useLogsRefreshStore().version, () => story.reload())

// Pull-to-refresh (native App) laedt hier die Daten neu statt der ganzen Seite.
const unregisterReload = registerSoftReload(() => story.reload())
onUnmounted(unregisterReload)
onMounted(() => analytics.track('story_viewed'))
</script>

<template>
  <div class="min-h-screen bg-gray-100 md:bg-gray-50 dark:bg-gray-950" data-testid="story-view">
    <PublicNav home>
      <template #below><LeaderboardTicker v-if="showTicker" docked /></template>
    </PublicNav>
    <nav class="mx-auto hidden max-w-6xl flex-wrap gap-2 px-6 pt-4 md:flex" :aria-label="t('story.workspace_aria')">
      <ChoiceChip v-for="tab in workspaceTabs" :key="tab.to" :href="tab.to" :active="tab.active" :aria-current="tab.active ? 'page' : undefined">
        {{ tab.label }}
      </ChoiceChip>
    </nav>
    <main class="mx-auto grid max-w-6xl grid-cols-1 gap-4 pb-6 md:px-6 md:pb-12 md:pt-4">
      <!-- Eine Box: ab lg zwei Spalten 7/5, links Hero bis Kacheln, rechts der Feed. -->
      <section class="grid grid-cols-1 gap-4 bg-white px-4 py-4 md:rounded-3xl md:p-6 md:edge-box lg:grid-cols-12 lg:gap-x-8 lg:gap-y-5 dark:bg-gray-900" data-testid="story-hero-box">
        <div class="grid min-w-0 grid-cols-1 content-start gap-4 lg:col-span-7">
          <header class="flex items-center justify-between gap-2">
            <div class="flex min-w-0 items-center gap-2">
              <p class="truncate text-[15px] font-bold text-gray-900 dark:text-gray-50">
                {{ selectedCar ? carDisplayName(selectedCar) : t('story.title') }}
              </p>
              <span v-if="selectedCar?.licensePlate" class="hidden sm:inline-flex"><LicensePlate :plate="selectedCar.licensePlate" size="sm" /></span>
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
            <div class="h-8 w-72 max-w-full rounded bg-gray-200 dark:bg-gray-700" />
            <div class="mt-2 h-4 w-56 max-w-full rounded bg-gray-200 dark:bg-gray-700" />
            <div class="mt-6 grid gap-2 md:grid-cols-2">
              <div v-for="i in 2" :key="i" class="h-12 rounded-xl bg-gray-200 dark:bg-gray-700" />
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
                  :layers="layers"
                  @share="shareMonth"
                  @layers="(open) => open && analytics.track('story_layers_opened')" />
              </Transition>
            </div>
            <p v-if="shareStatus === 'copied' || shareStatus === 'failed'" class="text-sm text-gray-600 dark:text-gray-300" role="status">
              {{ shareStatus === 'copied' ? t('story.moment_copied') : t('story.moment_share_failed') }}
            </p>
            <DataQualityLine v-if="hasCharges && heroMode !== 'live'" :days="days" :quality="quality" @open-day="onOpenDay" />
            <StoryLatestCards v-if="story.latest.value" :latest="story.latest.value" :car-id="selectedCarId" :price-in-hero="priceInHero" />
            <StoryFeed
              v-if="narrow && showFeed"
              :feed="feed" :upsell-to="upsellTo" :model-name="modelName" :car-id="selectedCarId" :car-name="carName" :sheets="sheetIds"
              @open="openSheet" @map="openMap = true" />
            <StoryTileRow v-if="hasCharges" :tiles="tiles" @open="openSheet" />
          </template>
        </div>
        <div v-if="!narrow && showFeed" class="min-w-0 lg:col-span-5 lg:pt-9">
          <StoryFeed
            :feed="feed" :upsell-to="upsellTo" :model-name="modelName" :car-id="selectedCarId" :car-name="carName" :sheets="sheetIds"
            @open="openSheet" @map="openMap = true" />
        </div>
      </section>

      <div class="px-4 md:px-0">
        <button type="button" class="inline-flex min-h-[44px] items-center gap-2 text-sm text-gray-500 underline underline-offset-2 dark:text-gray-400" data-testid="story-back" @click="backToClassic">
          <ArrowUturnLeftIcon class="h-4 w-4" />{{ t('story.back_to_dashboard') }}
        </button>
      </div>
    </main>

    <StorySheet v-if="sheetTile" :tile="sheetTile" :context="sheetContext" @close="openTile = null" />

    <StoryPriceSheet v-if="openPrices && story.benchmark.value" :benchmark="story.benchmark.value" :price-per-kwh="expensivePrice" @close="openPrices = false" />

    <PlacesSheet
      v-if="openMap && selectedCarId"
      ref="placesSheet"
      v-model:period="placesPeriod"
      :car-id="selectedCarId"
      :month="month"
      :month-name="monthLabel(month)"
      :can-view-live-analytics="authStore.canViewLiveAnalytics"
      :upsell-to="upsellTo"
      @close="openMap = false"
      @operator="openOperator" />
    <EditLogModal
      v-if="operatorLog"
      :log="operatorLog"
      start-section="place"
      stacked
      @close="closeOperator(false)"
      @saved="closeOperator(true)"
      @deleted="closeOperator(true)" />
    <StoryDaySheet
      v-if="openDay && selectedCarId"
      v-model:day="openDay"
      :car-id="selectedCarId"
      :today="todayKey()"
      @close="openDay = null"
      @changed="story.reload()"
      @add-charge="onAddCharge" />
    <LogFormModal
      v-if="addCharge"
      :car-id="selectedCarId"
      :initial-logged-at="addCharge.loggedAt"
      @saved="story.reload()"
      @close="onAddChargeClosed" />
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
