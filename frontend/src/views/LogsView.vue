<script setup lang="ts">
import { ref, computed, reactive, onMounted, onUnmounted, onActivated, onDeactivated, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  TruckIcon,
  MapIcon,
  BoltIcon,
  PencilSquareIcon,
  ClockIcon,
  Battery0Icon,
  SunIcon,
  ChevronLeftIcon,
  ChevronRightIcon,
  ChevronDownIcon,
  ChevronUpIcon,
  TrashIcon,
  ExclamationTriangleIcon,
  InformationCircleIcon,
  XMarkIcon,
  ArrowsRightLeftIcon,
  PlusIcon,
  HandThumbUpIcon,
  HandThumbDownIcon,
  EllipsisVerticalIcon,
} from '@heroicons/vue/24/outline'
import { tempBadgeClass } from '../utils/temperatureColor'
import { consumptionTextClass } from '../utils/consumptionColor'
import { isShortTrip } from '../utils/shortTrip'
import {
  tripConsumption as tripConsumptionPure,
  tripGroupConsumedKwh as tripGroupConsumedKwhPure,
  tripGroupSocBoundaries as tripGroupSocBoundariesPure,
  tripGroupCostPer100km as tripGroupCostPer100kmPure,
  buildTripCostPerKwhMap,
} from '../utils/tripCalculations'
import ConsumptionInfoBox from '../components/dashboard/ConsumptionInfoBox.vue'
import EditLogModal from '../components/dashboard/EditLogModal.vue'
import TripForm from '../components/dashboard/TripForm.vue'
import { costBadgeClass } from '../utils/costColor'
import LicensePlate from '../components/car/LicensePlate.vue'
import RewardSystemUpdateBanner from '../components/shared/RewardSystemUpdateBanner.vue'
import { useAuthStore } from '../stores/auth'
import ImplausibleLogsModal from '../components/dashboard/ImplausibleLogsModal.vue'
import { useLocaleFormat } from '../composables/useLocaleFormat'
import { useDashboardStats } from '../composables/useDashboardStats'
import { useLogList } from '../composables/useLogList'
import { useStickyCarHeader } from '../composables/useStickyCarHeader'
import { useBulkBarOffset } from '../composables/useBulkBarOffset'
import { useWallboxStore } from '../stores/wallbox'
import { carDisplayName } from '../utils/enumLabel'
import { isVwGroupBrand } from '../api/vwGroupService'
import { subscriptionService, type SubscriptionTier } from '../api/subscriptionService'

const { t } = useI18n()
const { formatConsumption, formatDistance, distanceUnitLabel, formatCurrency, formatCostPerKwh } = useLocaleFormat()

// -- Dashboard Stats --
const {
  selectedCarId, stats, loading, isInitialLoad,
  cars, carImageUrls,
  implausibleBannerDismissed, teslaStatus, smartcarStatus, vwGroupStatus, implausibleCount,
  dismissImplausibleBanner, fetchImplausibleCount,
  fetchCarAndWltp, fetchStatistics, initCars,
} = useDashboardStats()

// -- Log List --
const logsSection = ref<HTMLElement | null>(null)
const {
  logs, logsPage, logsLoading, hasMoreLogs, editingLog,
  expandedGroups, toggleLadegruppe, hasAnyLogs, showOdometer, showCostAbsolute,
  openTooltipLogId, reassignModalEntry, reassignSelectedCarId, reassignSaving,
  reassignError, reassignSuccessMessage, otherCars, openReassignModal, saveReassign,
  fetchLogs, fetchLogsAndScroll, refreshLogsAndGroups, deleteLog,
  formatLogDate, formatTripTimeRange, toggleOdometerDisplay, sourceInfo, mergedLogFeed,
  editingTripId, addingTripAfterId, tripForm, tripSaving, tripError,
  startEditTrip, cancelTripEdit, saveTripEdit, startAddTrip, saveNewTrip, deleteTripEntry,
  mergeTripEntry,
  submitTripFeedback,
} = useLogList(selectedCarId, cars, logsSection)

const deletingTripId = ref<string | null>(null)
let _deleteTimer: ReturnType<typeof setTimeout> | null = null
onUnmounted(() => { if (_deleteTimer) clearTimeout(_deleteTimer) })

async function handleDeleteTrip(id: string) {
  if (!confirm(t('dashboard.trip_delete_confirm'))) return
  deletingTripId.value = id
  _deleteTimer = setTimeout(async () => {
    _deleteTimer = null
    try {
      await deleteTripEntry(id)
    } catch {
      deletingTripId.value = null
      alert(t('dashboard.err_load'))
      return
    }
    deletingTripId.value = null
  }, 300)
}

// -- Trip group collapse --
const tripCollapseKey = (carId: string | number | null) => `logfeed_collapsed_groups_${carId}`

function loadCollapsedGroups(carId: string | number | null): Set<string> {
  if (!carId) return new Set<string>()
  try {
    const saved = localStorage.getItem(tripCollapseKey(carId))
    if (!saved) return new Set<string>()
    const parsed = JSON.parse(saved)
    if (!Array.isArray(parsed)) return new Set<string>()
    return new Set<string>(parsed.filter((x: unknown) => typeof x === 'string'))
  } catch {
    return new Set<string>()
  }
}

const collapsedTripGroups = ref<Set<string>>(loadCollapsedGroups(selectedCarId.value))

watch(selectedCarId, (newId) => {
  collapsedTripGroups.value = loadCollapsedGroups(newId)
})

const page1GroupIds = computed<Set<string>>(() => {
  if (logsPage.value !== 0) return new Set<string>()
  return new Set(['tg_top', ...logs.value.map((l: any) => `tg_${l.id}`)])
})

function toggleTripGroup(groupId: string) {
  const next = new Set(collapsedTripGroups.value)
  if (next.has(groupId)) next.delete(groupId)
  else next.add(groupId)
  collapsedTripGroups.value = next
  if (page1GroupIds.value.has(groupId)) {
    try {
      const toSave = [...next].filter(id => page1GroupIds.value.has(id))
      localStorage.setItem(tripCollapseKey(selectedCarId.value), JSON.stringify(toSave))
    } catch {}
  }
}

// -- Trip merge --
const mergePreviewForTripId = ref<string | null>(null)
const tripMerging = ref(false)
const tripMergeError = ref<string | null>(null)

const previousTripMap = computed<Record<string, any>>(() => {
  const feed = mergedLogFeed.value
  const result: Record<string, any> = {}
  for (let i = 0; i < feed.length; i++) {
    if (!feed[i]._isTrip) continue
    for (let j = i + 1; j < feed.length; j++) {
      if (feed[j]._isTrip) { result[feed[i].id] = feed[j]; break }
    }
  }
  return result
})

const groupedFeed = computed<any[]>(() => {
  const feed = mergedLogFeed.value
  const result: any[] = []
  let i = 0
  while (i < feed.length) {
    const entry = feed[i]
    if (entry._isTrip && entry._tripGroupIndex === 0) {
      const groupId = entry._tripGroupId
      const group: any = {
        kind: 'tripGroup',
        id: groupId,
        groupId,
        phantomDrain: entry._phantomDrain,
        totalKm: entry._tripGroupTotalKm,
        dateRange: entry._tripGroupDateRange,
        groupSize: entry._tripGroupSize,
        trips: [] as any[],
      }
      while (i < feed.length && feed[i]._isTrip && feed[i]._tripGroupId === groupId) {
        group.trips.push(feed[i])
        i++
      }
      // Drain between last trip and subsequent charge entry
      const nextEntry = i < feed.length ? feed[i] : null
      const drainAfterGroup = (nextEntry && !nextEntry._isTrip) ? nextEntry._phantomDrain : null
      const totalPhantomKwh =
        group.trips.reduce((s: number, t: any) => s + (t._phantomDrain?.kwh ?? 0), 0)
        + (drainAfterGroup?.kwh ?? 0)
      group.totalPhantomKwh = totalPhantomKwh > 0.05 ? Math.round(totalPhantomKwh * 100) / 100 : null
      result.push(group)
    } else {
      result.push({ kind: 'entry', id: entry.id, entry })
      i++
    }
  }
  return result
})

function cancelTripEditFull() {
  cancelTripEdit()
  mergePreviewForTripId.value = null
}

async function doMergeTrip(survivingId: string, previousId: string) {
  tripMerging.value = true
  tripMergeError.value = null
  try {
    await mergeTripEntry(survivingId, previousId)
    cancelTripEditFull()
  } catch {
    tripMergeError.value = t('dashboard.err_load')
  } finally {
    tripMerging.value = false
  }
}

// saveReassign needs fetchStatistics, so wrap it
const doSaveReassign = () => saveReassign(fetchStatistics)

// -- Trip feedback --
const FEEDBACK_TAGS = ['distance_wrong', 'time_wrong', 'duplicate', 'other'] as const
const openMenuLogId     = ref<string | null>(null)
const openMenuTripId    = ref<string | null>(null)
const openMenuTopUpId   = ref<string | null>(null)
const expandedLogs      = ref(new Set<string>())

// ESC closes any open action menu - keeps keyboard parity with the click-outside dimmer.
function onMenuKeyEsc(event: KeyboardEvent) {
  if (event.key !== 'Escape') return
  if (openMenuLogId.value || openMenuTripId.value || openMenuTopUpId.value) {
    openMenuLogId.value = null
    openMenuTripId.value = null
    openMenuTopUpId.value = null
  }
}
onActivated(() => window.addEventListener('keydown', onMenuKeyEsc))
onDeactivated(() => window.removeEventListener('keydown', onMenuKeyEsc))

function toggleLogExpanded(id: string) {
  if (expandedLogs.value.has(id)) expandedLogs.value.delete(id)
  else expandedLogs.value.add(id)
}

function chargingEfficiency(kwhCharged: number | null, kwhAtVehicle: number | null): number | null {
  if (!kwhCharged || !kwhAtVehicle || kwhCharged <= 0) return null
  return Math.round((kwhAtVehicle / kwhCharged) * 1000) / 10
}

const feedbackOpenId    = ref<string | null>(null)
const feedbackTags      = ref<string[]>([])
const feedbackComment   = ref('')
const feedbackPending   = reactive<Record<string, string | null>>({})
const feedbackTimers    = new Map<string, ReturnType<typeof setTimeout>>()

// Returns the effective rating for a trip, taking optimistic pending state into account
const effectiveRating = (tripId: string, serverFeedback: string | null | undefined): string | null => {
  if (tripId in feedbackPending) return feedbackPending[tripId]
  if (!serverFeedback) return null
  if (serverFeedback.startsWith('positive')) return 'positive'
  if (serverFeedback.startsWith('negative')) return 'negative'
  return null
}

const toggleFeedbackTag = (tag: string) => {
  const idx = feedbackTags.value.indexOf(tag)
  if (idx === -1) feedbackTags.value.push(tag)
  else feedbackTags.value.splice(idx, 1)
}

const openFeedbackPanel = (tripId: string) => {
  if (feedbackOpenId.value === tripId) {
    feedbackOpenId.value = null
    return
  }
  feedbackOpenId.value = tripId
  feedbackTags.value   = []
  feedbackComment.value = ''
}

// Debounced toggle for 👍/👎 direct buttons (600ms)
const toggleRating = (tripId: string, rating: 'positive' | 'negative', serverFeedback: string | null | undefined) => {
  const current = effectiveRating(tripId, serverFeedback)
  const next = current === rating ? null : rating

  // Optimistic update
  feedbackPending[tripId] = next

  // Close negative panel if switching away
  if (next !== 'negative' && feedbackOpenId.value === tripId) feedbackOpenId.value = null
  // Open panel for negative
  if (next === 'negative') openFeedbackPanel(tripId)

  // Debounce: reset timer on each click
  if (feedbackTimers.has(tripId)) clearTimeout(feedbackTimers.get(tripId)!)
  const timer = setTimeout(async () => {
    await submitTripFeedback(tripId, next ?? '')
    delete feedbackPending[tripId]
    feedbackTimers.delete(tripId)
  }, 600)
  feedbackTimers.set(tripId, timer)
}

// Panel submit (no debounce - deliberate action)
const sendNegativeFeedback = async (tripId: string) => {
  const parts: string[] = ['negative']
  const tags = feedbackTags.value.join(',')
  if (tags) parts.push(tags)
  if (feedbackComment.value.trim()) parts.push(feedbackComment.value.trim())
  await submitTripFeedback(tripId, parts.join(' | '))
  feedbackOpenId.value = null
  feedbackTags.value   = []
  feedbackComment.value = ''
}

// -- Implausible logs modal --
const showImplausibleModal = ref(false)
const implausibleModalDirty = ref(false)

// -- Range calculator --

const selectedCar = computed(() =>
  cars.value.find(c => c.id === selectedCarId.value) ?? cars.value[0] ?? null
)

const wallboxStore = useWallboxStore()

const isSmartcarCharging = (car: any) =>
  smartcarStatus.value?.connected === true &&
  smartcarStatus.value?.vehicleState === 'CHARGING' &&
  (smartcarStatus.value?.carId === car.id ||
    (smartcarStatus.value?.carId === null && cars.value.length === 1))

// Wallbox kennt keine carId → Glow nur bei Single-Car sicher zuordenbar
const isWallboxCharging = () =>
  wallboxStore.isCharging && cars.value.length === 1

const isVwGroupCharging = (car: any) =>
  isVwGroupBrand(car.brand) &&
  vwGroupStatus.value?.connected === true &&
  vwGroupStatus.value?.vehicleState === 'charging'

const isVehicleCharging = (car: any) => isSmartcarCharging(car) || isVwGroupCharging(car) || isWallboxCharging()

const anyVehicleCharging = computed(() => cars.value.some(car => isVehicleCharging(car)))

// -- Lifecycle --
watch(selectedCarId, async (newId) => {
  if (newId) {
    await fetchCarAndWltp(newId)
    await Promise.all([fetchStatistics(), fetchLogs(0), fetchImplausibleCount()])
  } else {
    stats.value = null
    implausibleCount.value = 0
  }
})

const authStore = useAuthStore()
const isAdmin   = computed(() => authStore.isAdmin)

// All trips visible in the current feed - used to seed the cpk-map. We pull
// them out of mergedLogFeed (which still flags each trip via `_isTrip`); the
// later groupedFeed computed clusters them but keeps the same trip references.
const allVisibleTrips = computed<any[]>(() => {
  const feed = (mergedLogFeed?.value ?? []) as any[]
  return feed.filter(entry => entry?._isTrip === true)
})

// Memoized: rebuilt only when logs / trips / fallback change. O(N + M log M)
// instead of the naive O(N × M) of inline template lookups.
const tripCpkMap = computed(() => buildTripCostPerKwhMap(
  allVisibleTrips.value,
  (logs.value ?? []) as any,
  stats.value?.avgCostPerKwh ?? null,
))

function tripConsumption(entry: any): { kwhPer100km: number; estimated: boolean } | null {
  return tripConsumptionPure(entry, selectedCar.value?.effectiveBatteryCapacityKwh ?? null)
}

function tripCostPerKwh(trip: any): number | null {
  return tripCpkMap.value.get(trip?.id) ?? (stats.value?.avgCostPerKwh ?? null)
}

function tripCostPer100km(trip: any): number | null {
  const c = tripConsumption(trip)
  if (!c) return null
  const cpk = tripCostPerKwh(trip)
  if (cpk == null) return null
  return c.kwhPer100km * cpk
}

function tripGroupCostPer100km(group: any): number | null {
  if (!group?.trips?.length) return null
  return tripGroupCostPer100kmPure(group.trips, tripCpkMap.value, selectedCar.value?.effectiveBatteryCapacityKwh ?? null)
}

function tripGroupConsumedKwh(group: any): number | null {
  if (!group?.trips?.length) return null
  return tripGroupConsumedKwhPure(group.trips, selectedCar.value?.effectiveBatteryCapacityKwh ?? null)
}

function tripGroupSocBoundaries(group: any): { start: number; end: number } | null {
  if (!group?.trips?.length) return null
  return tripGroupSocBoundariesPure(group.trips)
}

onMounted(() => initCars())

// AutoSync Live discoverability banner: shown to Tesla-users that don't have
// Live yet, and haven't dismissed the hint. Position: below ConsumptionInfoBox.
const LS_LIVE_BANNER_DISMISSED = 'autosync_live_banner_dismissed'
const subscriptionTier = ref<SubscriptionTier | null>(null)
const liveBannerDismissed = ref(localStorage.getItem(LS_LIVE_BANNER_DISMISSED) === 'true')
const showLiveBanner = computed(() =>
  subscriptionTier.value != null
  && subscriptionTier.value !== 'AUTOSYNC_LIVE'
  && selectedCar.value?.brand === 'TESLA'
  && !liveBannerDismissed.value
)
function dismissLiveBanner() {
  liveBannerDismissed.value = true
  localStorage.setItem(LS_LIVE_BANNER_DISMISSED, 'true')
}
onMounted(async () => {
  try {
    const status = await subscriptionService.getStatus()
    subscriptionTier.value = status.tier ?? 'NONE'
  } catch {
    subscriptionTier.value = null
  }
})

function onTripFormEnter(el: Element, done: () => void) {
  const h = el as HTMLElement
  const cs = getComputedStyle(h)
  const targetHeight = h.scrollHeight
  const targetMarginTop = cs.marginTop
  const targetPaddingTop = cs.paddingTop
  const targetPaddingBottom = cs.paddingBottom
  h.style.overflow = 'hidden'
  h.style.height = '0'
  h.style.marginTop = '0'
  h.style.paddingTop = '0'
  h.style.paddingBottom = '0'
  h.style.opacity = '0'
  requestAnimationFrame(() => {
    h.style.transition = 'height 0.28s ease, margin-top 0.28s ease, padding-top 0.28s ease, padding-bottom 0.28s ease, opacity 0.22s ease'
    h.style.height = targetHeight + 'px'
    h.style.marginTop = targetMarginTop
    h.style.paddingTop = targetPaddingTop
    h.style.paddingBottom = targetPaddingBottom
    h.style.opacity = '1'
    setTimeout(done, 280)
  })
}
function onTripFormAfterEnter(el: Element) {
  const h = el as HTMLElement
  h.style.cssText = ''
}
function onTripFormLeave(el: Element, done: () => void) {
  const h = el as HTMLElement
  const cs = getComputedStyle(h)
  h.style.overflow = 'hidden'
  h.style.height = h.scrollHeight + 'px'
  h.style.marginTop = cs.marginTop
  h.style.paddingTop = cs.paddingTop
  h.style.paddingBottom = cs.paddingBottom
  h.style.opacity = '1'
  requestAnimationFrame(() => {
    h.style.transition = 'height 0.28s ease, margin-top 0.28s ease, padding-top 0.28s ease, padding-bottom 0.28s ease, opacity 0.22s ease'
    h.style.height = '0'
    h.style.marginTop = '0'
    h.style.paddingTop = '0'
    h.style.paddingBottom = '0'
    h.style.opacity = '0'
    setTimeout(done, 280)
  })
}

// -- Sticky car header detection --
const stickyCarBar = ref<HTMLElement | null>(null)
const { isCarHeaderSticky } = useStickyCarHeader(stickyCarBar)

// -- Bulk expand/collapse for trips + charges (mobile sticky bar) --
const expandedLogsStorageKey  = (carId: string | number | null) => `logfeed_expanded_logs_${carId}`
const expandedGroupsStorageKey = (carId: string | number | null) => `logfeed_expanded_groups_${carId}`

function loadIdSet(key: string): Set<string> {
  try {
    const raw = localStorage.getItem(key)
    if (!raw) return new Set<string>()
    const parsed = JSON.parse(raw)
    if (!Array.isArray(parsed)) return new Set<string>()
    return new Set<string>(parsed.filter((x: unknown) => typeof x === 'string'))
  } catch { return new Set<string>() }
}
function saveIdSet(key: string, set: Set<string>) {
  try { localStorage.setItem(key, JSON.stringify([...set])) } catch {}
}

// Hydrate expandedLogs / expandedGroups from localStorage on car switch.
watch(selectedCarId, (newId) => {
  if (!newId) return
  expandedLogs.value = loadIdSet(expandedLogsStorageKey(newId))
  expandedGroups.value = loadIdSet(expandedGroupsStorageKey(newId))
}, { immediate: true })

// Persist on any change (handles single-item toggles too).
watch(expandedLogs, (v) => {
  if (selectedCarId.value) saveIdSet(expandedLogsStorageKey(selectedCarId.value), v)
}, { deep: true })
watch(expandedGroups, (v) => {
  if (selectedCarId.value) saveIdSet(expandedGroupsStorageKey(selectedCarId.value), v)
}, { deep: true })

const visibleTripGroups = computed(() => groupedFeed.value.filter(i => i.kind === 'tripGroup'))
const visibleChargeEntries = computed(() => groupedFeed.value.filter(i => i.kind === 'entry'))
const totalTripCount = computed(() => visibleTripGroups.value.reduce((s, g) => s + (g.groupSize ?? 0), 0))
const chargeCount = computed(() => visibleChargeEntries.value.length)

const allTripsExpanded = computed(() =>
  visibleTripGroups.value.length > 0
  && visibleTripGroups.value.every(g => !collapsedTripGroups.value.has(g.groupId)),
)
const allChargesExpanded = computed(() =>
  visibleChargeEntries.value.length > 0
  && visibleChargeEntries.value.every(item =>
    item.entry._isLadegruppe ? expandedGroups.value.has(item.entry.id) : expandedLogs.value.has(item.entry.id),
  ),
)

function toggleAllTrips() {
  const next = new Set(collapsedTripGroups.value)
  if (allTripsExpanded.value) {
    visibleTripGroups.value.forEach(g => next.add(g.groupId))
  } else {
    visibleTripGroups.value.forEach(g => next.delete(g.groupId))
  }
  collapsedTripGroups.value = next
  if (selectedCarId.value) {
    try {
      const toSave = [...next].filter(id => page1GroupIds.value.has(id))
      localStorage.setItem(tripCollapseKey(selectedCarId.value), JSON.stringify(toSave))
    } catch {}
  }
}

// -- Coordinate FAB position with the sticky bulk-bar (mobile only) --
const bulkBar = ref<HTMLElement | null>(null)
const bulkBarVisible = computed(() =>
  hasAnyLogs.value && (totalTripCount.value > 0 || chargeCount.value > 0),
)
useBulkBarOffset(bulkBar, bulkBarVisible)

function toggleAllCharges() {
  const expanded = allChargesExpanded.value
  const newLogs   = new Set(expandedLogs.value)
  const newGroups = new Set(expandedGroups.value)
  visibleChargeEntries.value.forEach(item => {
    if (item.entry._isLadegruppe) {
      if (expanded) newGroups.delete(item.entry.id)
      else newGroups.add(item.entry.id)
    } else {
      if (expanded) newLogs.delete(item.entry.id)
      else newLogs.add(item.entry.id)
    }
  })
  expandedLogs.value   = newLogs
  expandedGroups.value = newGroups
}
</script>

<template>
<div>
  <div class="md:max-w-6xl md:mx-auto md:p-6">
    <RewardSystemUpdateBanner class="mb-4" />
    <Transition name="fade" mode="out-in">
      <div v-if="!loading || !isInitialLoad">
        <div class="bg-gray-100 dark:bg-gray-800 md:rounded-xl md:shadow-lg p-2 md:p-6">
          <!-- Car card selector (all breakpoints) -->
          <div
            v-if="cars.length > 0"
            ref="stickyCarBar"
            :class="[
              cars.length > 1
                ? 'sticky top-16 z-10 bg-white dark:bg-gray-800 -mx-4 px-4 md:-mx-6 md:px-6 py-1.5 md:py-3 mb-3 border-b border-gray-100 dark:border-gray-700 shadow-sm'
                : 'sticky top-16 z-10 bg-white dark:bg-gray-800 -mx-4 px-4 py-1.5 mb-3 border-b border-gray-100 dark:border-gray-700 shadow-sm md:static md:bg-transparent md:p-0 md:mb-6 md:rounded-xl md:w-fit md:border-0 md:shadow-none',
              cars.length === 1 && anyVehicleCharging ? 'vehicle-charging-glow' : '',
              isCarHeaderSticky ? 'car-header-compact' : ''
            ]"
          >
            <div class="flex gap-2 items-stretch">
              <router-link
                to="/dashboard"
                :aria-label="t('dashboard.title')"
                class="flex-shrink-0 self-stretch flex items-center gap-1 px-2 rounded-lg border border-gray-200 dark:border-gray-600 bg-white dark:bg-gray-700 text-gray-600 dark:text-gray-300 text-xs font-medium hover:border-indigo-300 active:translate-y-0.5 transition">
                <ChevronLeftIcon class="w-4 h-4 flex-shrink-0" />
                <span class="hidden sm:inline">{{ t('dashboard.title') }}</span>
              </router-link>
              <div class="flex gap-3 overflow-x-auto car-scroll-hide flex-1 lg:flex-wrap lg:overflow-x-visible">
              <button
                v-for="car in cars"
                :key="car.id"
                @click="selectedCarId = car.id"
                :class="[
                  cars.length === 1
                    ? 'flex items-center md:items-stretch rounded-xl border-2 text-left transition w-full md:w-auto overflow-hidden'
                    : 'flex items-center md:items-stretch rounded-xl border-2 text-left transition flex-shrink-0 min-w-[180px] max-w-[240px] lg:flex-shrink lg:min-w-0 lg:max-w-none overflow-hidden',
                  selectedCarId === car.id
                    ? isVehicleCharging(car)
                      ? 'border-transparent bg-green-50 dark:bg-green-900/20 shadow-[0_4px_0_0_#16a34a] dark:shadow-[0_4px_0_0_#14532d] translate-y-[2px]'
                      : 'border-indigo-500 bg-indigo-50 dark:bg-indigo-900/30 shadow-[0_4px_0_0_#4338ca] translate-y-[2px]'
                    : isVehicleCharging(car)
                      ? 'border-transparent bg-white dark:bg-gray-700 shadow-[0_4px_0_0_#16a34a] dark:shadow-[0_4px_0_0_#14532d] active:shadow-none active:translate-y-1'
                      : 'border-gray-200 dark:border-gray-600 bg-white dark:bg-gray-700 shadow-[0_4px_0_0_#d1d5db] dark:shadow-[0_4px_0_0_#111827] hover:border-indigo-300 active:shadow-none active:translate-y-1',
                  cars.length > 1 && isVehicleCharging(car) ? 'ring-2 ring-green-400 dark:ring-green-500' : '',
                ]" style="transition: transform 0.075s ease, box-shadow 0.075s ease;">
                <div class="flex-shrink-0 h-12 aspect-[4/3] md:w-24 md:h-auto md:aspect-auto md:self-stretch bg-gray-100 dark:bg-gray-600 flex items-center justify-center overflow-hidden compact-shrink-thumb">
                  <img
                    v-if="carImageUrls[car.id]"
                    :src="carImageUrls[car.id]"
                    :alt="car.model"
                    class="w-full h-full object-cover" />
                  <TruckIcon v-else class="w-6 h-6 md:w-8 md:h-8 text-gray-400" />
                </div>
                <div class="min-w-0 flex-1 px-3 py-1.5 md:px-4 md:py-3 compact-shrink-pad">
                  <!-- Mobile compact (alle Autos): eine Zeile -->
                  <div class="flex items-center gap-1.5 flex-wrap md:hidden compact-nowrap">
                    <span class="font-semibold text-sm text-gray-800 dark:text-gray-200 whitespace-nowrap">{{ carDisplayName(car.brand, car.model) }}</span>
                    <span v-if="car.trim" class="text-xs text-gray-500 dark:text-gray-400 compact-hide">{{ car.trim }}</span>
                    <template v-if="!isCarHeaderSticky">
                      <template v-if="car.brand?.toLowerCase() === 'tesla' && teslaStatus?.connected && (teslaStatus.carId === car.id || teslaStatus.carId === null)">
                        <span v-if="teslaStatus.vehicleState === 'charging'"
                          class="inline-flex items-center gap-1 px-1.5 py-0.5 bg-green-100 text-green-700 text-xs rounded-full font-medium border border-green-200">
                          <span class="w-1.5 h-1.5 rounded-full bg-green-500 animate-pulse"></span>{{ t('dashboard.tesla_charging') }}
                        </span>
                        <span v-else-if="teslaStatus.vehicleState === 'online'"
                          class="inline-flex items-center gap-1 px-1.5 py-0.5 bg-blue-50 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400 text-xs rounded-full font-medium border border-blue-200 dark:border-blue-700">
                          <span class="w-1.5 h-1.5 rounded-full bg-blue-400"></span>{{ t('dashboard.tesla_online') }}
                        </span>
                        <span v-else-if="teslaStatus.vehicleState === 'asleep'"
                          class="inline-flex items-center gap-1 px-1.5 py-0.5 bg-gray-100 text-gray-500 text-xs rounded-full font-medium border border-gray-300">
                          <span class="w-1.5 h-1.5 rounded-full bg-gray-400"></span>{{ t('dashboard.tesla_sleeping') }}
                        </span>
                      </template>
                      <span v-if="isSmartcarCharging(car)"
                        class="inline-flex items-center gap-1 px-1.5 py-0.5 bg-green-100 dark:bg-green-900/40 text-green-700 dark:text-green-400 text-xs rounded-full font-medium border border-green-200 dark:border-green-700">
                        <span class="w-1.5 h-1.5 rounded-full bg-green-500 dark:bg-green-400 animate-pulse"></span>{{ t('dashboard.smartcar_charging') }}
                      </span>
                      <template v-if="isVwGroupBrand(car.brand) && vwGroupStatus?.connected">
                        <span v-if="vwGroupStatus.vehicleState === 'charging'"
                          class="inline-flex items-center gap-1 px-1.5 py-0.5 bg-green-100 dark:bg-green-900/40 text-green-700 dark:text-green-400 text-xs rounded-full font-medium border border-green-200 dark:border-green-700">
                          <span class="w-1.5 h-1.5 rounded-full bg-green-500 dark:bg-green-400 animate-pulse"></span>
                          {{ t('dashboard.vwgroup_charging') }}
                          <span v-if="vwGroupStatus.lastSoc != null" class="opacity-75">· {{ vwGroupStatus.lastSoc }}%</span>
                        </span>
                        <span v-else
                          class="inline-flex items-center gap-1 px-1.5 py-0.5 bg-blue-50 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400 text-xs rounded-full font-medium border border-blue-200 dark:border-blue-700">
                          <span class="w-1.5 h-1.5 rounded-full bg-blue-400"></span>
                          {{ vwGroupStatus.lastSoc != null ? vwGroupStatus.lastSoc + '%' : t('dashboard.vwgroup_connected') }}
                        </span>
                      </template>
                      <span v-if="isWallboxCharging()"
                        class="inline-flex items-center gap-1 px-1.5 py-0.5 bg-green-100 dark:bg-green-900/40 text-green-700 dark:text-green-400 text-xs rounded-full font-medium border border-green-200 dark:border-green-700">
                        <span class="w-1.5 h-1.5 rounded-full bg-green-500 dark:bg-green-400 animate-pulse"></span>{{ t('dashboard.wallbox_charging') }}
                      </span>
                    </template>
                  </div>
                  <!-- Desktop: zweizeiliges Layout -->
                  <div :class="cars.length === 1 ? 'hidden lg:block' : 'hidden md:block'">
                    <div class="flex items-center gap-2 flex-wrap">
                      <span class="font-semibold text-gray-800 dark:text-gray-200">{{ carDisplayName(car.brand, car.model) }}</span>
                      <span v-if="car.trim" class="text-sm text-gray-500 dark:text-gray-400">{{ car.trim }}</span>
                      <span v-if="car.isPrimary && cars.length > 1"
                        class="px-1.5 py-0.5 bg-green-100 dark:bg-green-900/40 text-green-700 dark:text-green-400 text-xs rounded-full border border-green-200 dark:border-green-700 font-medium">
                        {{ t('dashboard.active') }}
                      </span>
                      <template v-if="car.brand?.toLowerCase() === 'tesla' && teslaStatus?.connected && (teslaStatus.carId === car.id || teslaStatus.carId === null)">
                        <span v-if="teslaStatus.vehicleState === 'charging'"
                          class="inline-flex items-center gap-1 px-1.5 py-0.5 bg-green-100 dark:bg-green-900/40 text-green-700 dark:text-green-400 text-xs rounded-full font-medium border border-green-200 dark:border-green-700">
                          <span class="w-1.5 h-1.5 rounded-full bg-green-500 dark:bg-green-400 animate-pulse"></span>{{ t('dashboard.tesla_charging') }}
                        </span>
                        <span v-else-if="teslaStatus.vehicleState === 'online'"
                          class="inline-flex items-center gap-1 px-1.5 py-0.5 bg-blue-50 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400 text-xs rounded-full font-medium border border-blue-200 dark:border-blue-700">
                          <span class="w-1.5 h-1.5 rounded-full bg-blue-400"></span>{{ t('dashboard.tesla_online') }}
                        </span>
                        <span v-else-if="teslaStatus.vehicleState === 'asleep'"
                          class="inline-flex items-center gap-1 px-1.5 py-0.5 bg-gray-100 dark:bg-gray-700 text-gray-500 dark:text-gray-400 text-xs rounded-full font-medium border border-gray-200 dark:border-gray-600">
                          <span class="w-1.5 h-1.5 rounded-full bg-gray-400 dark:bg-gray-500"></span>{{ t('dashboard.tesla_sleeping') }}
                        </span>
                      </template>
                      <span v-if="isSmartcarCharging(car)"
                        class="inline-flex items-center gap-1 px-1.5 py-0.5 bg-green-100 dark:bg-green-900/40 text-green-700 dark:text-green-400 text-xs rounded-full font-medium border border-green-200 dark:border-green-700">
                        <span class="w-1.5 h-1.5 rounded-full bg-green-500 dark:bg-green-400 animate-pulse"></span>{{ t('dashboard.smartcar_charging') }}
                      </span>
                      <template v-if="isVwGroupBrand(car.brand) && vwGroupStatus?.connected">
                        <span v-if="vwGroupStatus.vehicleState === 'charging'"
                          class="inline-flex items-center gap-1 px-1.5 py-0.5 bg-green-100 dark:bg-green-900/40 text-green-700 dark:text-green-400 text-xs rounded-full font-medium border border-green-200 dark:border-green-700">
                          <span class="w-1.5 h-1.5 rounded-full bg-green-500 dark:bg-green-400 animate-pulse"></span>
                          {{ t('dashboard.vwgroup_charging') }}
                          <span v-if="vwGroupStatus.lastSoc != null" class="opacity-75">· {{ vwGroupStatus.lastSoc }}%</span>
                        </span>
                        <span v-else
                          class="inline-flex items-center gap-1 px-1.5 py-0.5 bg-blue-50 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400 text-xs rounded-full font-medium border border-blue-200 dark:border-blue-700">
                          <span class="w-1.5 h-1.5 rounded-full bg-blue-400"></span>
                          {{ vwGroupStatus.lastSoc != null ? vwGroupStatus.lastSoc + '%' : t('dashboard.vwgroup_connected') }}
                        </span>
                      </template>
                      <span v-if="isWallboxCharging()"
                        class="inline-flex items-center gap-1 px-1.5 py-0.5 bg-green-100 dark:bg-green-900/40 text-green-700 dark:text-green-400 text-xs rounded-full font-medium border border-green-200 dark:border-green-700">
                        <span class="w-1.5 h-1.5 rounded-full bg-green-500 dark:bg-green-400 animate-pulse"></span>{{ t('dashboard.wallbox_charging') }}
                      </span>
                    </div>
                    <div class="mt-1.5 flex justify-center">
                      <LicensePlate v-if="car.licensePlate" :plate="car.licensePlate" />
                    </div>
                  </div>
                </div>
              </button>
              </div>
            </div>
          </div>

        <!-- Log List -->
        <div ref="logsSection" class="pt-3 scroll-mt-4"
          :style="{ paddingBottom: `calc(var(--bulk-bar-offset, 0px) + 1.5rem)` }">
          <!-- AutoSync Live discoverability hint (Tesla-users without Live, dismissible) -->
          <div v-if="showLiveBanner"
            class="w-full flex items-center justify-between gap-2 px-3 py-2 mb-4 rounded-md border-l-2 border-indigo-400 bg-indigo-500/15">
            <div class="flex items-center gap-2 min-w-0">
              <span class="text-[10px] px-1.5 py-0.5 rounded bg-indigo-500 text-white font-semibold tracking-wide uppercase flex-shrink-0">{{ t('dashboard.live_banner_new_chip') }}</span>
              <svg class="w-3.5 h-3.5 text-indigo-500 dark:text-indigo-300 flex-shrink-0" fill="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                <path d="M13 10V3L4 14h7v7l9-11h-7z"/>
              </svg>
              <p class="text-xs text-gray-700 dark:text-gray-200 leading-snug">
                {{ t('dashboard.live_banner_text_prefix') }}
                <span class="font-medium text-indigo-700 dark:text-indigo-300">AutoSync Live</span>
                {{ t('dashboard.live_banner_text_suffix') }}
                <span class="text-gray-500 dark:text-gray-400">{{ t('dashboard.live_banner_price') }}</span>
                <router-link to="/upgrade" class="ml-1 underline text-indigo-700 dark:text-indigo-300 hover:text-indigo-500">
                  {{ t('dashboard.live_banner_cta') }}
                </router-link>
              </p>
            </div>
            <button type="button" @click="dismissLiveBanner"
              class="flex-shrink-0 p-0.5 rounded hover:bg-indigo-500/20 dark:hover:bg-indigo-500/30 transition focus:outline-none focus-visible:ring-2 focus-visible:ring-indigo-400"
              :aria-label="t('dashboard.live_banner_dismiss')">
              <XMarkIcon class="w-3.5 h-3.5 text-gray-500 dark:text-gray-400" aria-hidden="true" />
            </button>
          </div>

          <!-- Implausible logs banner (position 2: under ConsumptionInfoBox) -->
          <div v-if="implausibleCount > 0 && !implausibleBannerDismissed"
            class="w-full mb-4 flex items-center gap-3 px-4 py-3 rounded-lg bg-amber-200 dark:bg-amber-500/20 border border-amber-300 dark:border-amber-600/50">
            <button
              @click="showImplausibleModal = true"
              class="flex-1 flex items-center justify-between gap-3 text-left">
              <div class="flex items-center gap-2">
                <ExclamationTriangleIcon class="h-4 w-4 text-amber-600 dark:text-amber-400 shrink-0" />
                <span class="text-sm font-medium text-amber-800 dark:text-amber-300">
                  {{ t('dashboard.implausible_banner', { n: implausibleCount, noun: implausibleCount === 1 ? t('dashboard.implausible_entry') : t('dashboard.implausible_entries') }) }}
                </span>
              </div>
              <span class="text-xs text-amber-700 dark:text-amber-400 font-medium shrink-0">{{ t('dashboard.implausible_check') }}</span>
            </button>
            <button
              @click="dismissImplausibleBanner"
              class="shrink-0 p-1 rounded hover:bg-amber-300/50 dark:hover:bg-amber-600/30 transition-colors"
              :title="t('dashboard.implausible_dismiss')">
              <XMarkIcon class="h-4 w-4 text-amber-700 dark:text-amber-400" />
            </button>
          </div>

          <div v-if="!logsLoading && logsPage > 0" class="text-sm text-gray-400 mb-2 text-right">{{ t('dashboard.logs_page', { n: logsPage + 1 }) }}</div>

          <Transition enter-active-class="transition duration-200 ease-out" enter-from-class="opacity-0 -translate-y-1" enter-to-class="opacity-100 translate-y-0" leave-active-class="transition duration-150 ease-in" leave-from-class="opacity-100" leave-to-class="opacity-0">
            <div v-if="reassignSuccessMessage" class="mb-2 px-3 py-2 rounded-lg bg-green-50 dark:bg-green-900/30 border border-green-200 dark:border-green-700 text-sm text-green-700 dark:text-green-300 flex items-center gap-2">
              <ArrowsRightLeftIcon class="w-4 h-4 flex-shrink-0" />
              {{ reassignSuccessMessage }}
            </div>
          </Transition>

          <div class="space-y-2">
            <div v-if="logsLoading && !hasAnyLogs" class="py-8 text-center text-gray-400 text-sm">{{ t('dashboard.loading') }}</div>
            <template v-else-if="!hasAnyLogs">
              <p class="py-8 text-center text-gray-400 text-sm">{{ t('dashboard.no_logs_empty') }}</p>
            </template>
            <template v-else>
              <div v-if="openMenuLogId" class="fixed inset-0 z-40" @click="openMenuLogId = null" />
              <div v-if="openMenuTripId" class="fixed inset-0 z-40" @click="openMenuTripId = null" />
              <div v-if="openMenuTopUpId" class="fixed inset-0 z-40" @click="openMenuTopUpId = null" />
              <template v-for="item in groupedFeed" :key="item.id">

              <!-- ===== TRIP GROUP CONTAINER ===== -->
              <template v-if="item.kind === 'tripGroup'">
                <div class="gridfeed:hidden rounded-xl overflow-hidden border border-gray-300 dark:border-gray-600 border-l-4 border-r-4 border-l-emerald-400 dark:border-l-emerald-500 border-r-emerald-400 dark:border-r-emerald-500">

                  <!-- Group header -->
                  <div @click="toggleTripGroup(item.groupId)"
                       class="flex flex-col px-3 py-2.5 bg-white dark:bg-gray-700 cursor-pointer select-none hover:bg-gray-50 dark:hover:bg-gray-600/60 transition-colors border-b border-gray-100 dark:border-gray-600">
                    <!-- Header row -->
                    <div class="flex items-center gap-2">
                      <div class="flex-1 flex items-center justify-center gap-1.5 text-xs font-semibold text-emerald-600 dark:text-emerald-400">
                        <MapIcon class="w-3.5 h-3.5 shrink-0" />
                        {{ t('dashboard.trip_group_count', { count: item.groupSize }, item.groupSize) }}
                        <span v-if="item.totalKm" class="font-normal text-gray-500 dark:text-gray-400 whitespace-nowrap">&middot; {{ formatDistance(item.totalKm) }}</span>
                        <span v-if="item.dateRange" class="font-normal text-gray-500 dark:text-gray-400 whitespace-nowrap">&middot; {{ item.dateRange }}</span>
                        <span v-if="item.totalPhantomKwh && authStore.canViewLiveTrips" class="font-normal text-amber-500 dark:text-amber-500 inline-flex items-center gap-0.5 whitespace-nowrap shrink-0">&middot; <BoltIcon class="w-2.5 h-2.5" />{{ item.totalPhantomKwh.toFixed(1) }} kWh<span class="hidden sm:inline">&nbsp;{{ t('dashboard.phantom_drain_word') }}</span></span>
                      </div>
                      <ChevronUpIcon v-if="!collapsedTripGroups.has(item.groupId)" class="w-4 h-4 text-emerald-500 shrink-0" />
                      <ChevronDownIcon v-else class="w-4 h-4 text-emerald-500 shrink-0" />
                    </div>
                  </div>

                  <!-- Trips (expanded, animated) -->
                  <Transition :css="false" @enter="onTripFormEnter" @after-enter="onTripFormAfterEnter" @leave="onTripFormLeave">
                  <div v-if="!collapsedTripGroups.has(item.groupId)">
                    <template v-for="(trip, tripIdx) in item.trips" :key="trip.id">

                      <!-- Day-boundary separator between trips -->
                      <div v-if="tripIdx !== 0 && new Date(item.trips[(tripIdx as number) - 1].tripEndedAt).toDateString() !== new Date(trip.tripStartedAt).toDateString()"
                           class="border-t-2 border-gray-200 dark:border-gray-500" />

                      <!-- Phantom drain separator between trips -->
                      <div v-if="trip._phantomDrain && authStore.canViewLiveTrips && tripIdx !== 0"
                           class="flex items-center justify-center gap-1 py-1 border-t border-gray-600/50">
                        <BoltIcon class="w-2.5 h-2.5 text-amber-600 dark:text-amber-700" />
                        <span class="text-[11px] text-amber-600 dark:text-amber-700">
                          {{ trip._phantomDrain.kwh.toFixed(2) }} kWh
                          <template v-if="selectedCar?.effectiveBatteryCapacityKwh">({{ (trip._phantomDrain.kwh / selectedCar.effectiveBatteryCapacityKwh * 100).toFixed(1) }}%)</template>
                          {{ t('dashboard.phantom_drain_word') }}
                        </span>
                      </div>

                      <!-- Add-trip form triggered from this trip -->
                      <Transition :css="false" @enter="onTripFormEnter" @after-enter="onTripFormAfterEnter" @leave="onTripFormLeave">
                      <div v-if="addingTripAfterId === trip.id"
                           class="px-3 py-3 bg-white dark:bg-gray-700 border-t border-gray-100 dark:border-gray-600">
                        <TripForm v-model="tripForm" mode="add"
                          :error="tripError" :saving="tripSaving" :distance-unit="distanceUnitLabel()"
                          @save="saveNewTrip()" @cancel="cancelTripEdit()" />
                      </div>
                      </Transition>

                      <!-- Trip display mode -->
                      <Transition :css="false" @enter="onTripFormEnter" @after-enter="onTripFormAfterEnter" @leave="onTripFormLeave">
                      <div v-if="editingTripId !== trip.id && deletingTripId !== trip.id"
                           class="px-3 py-3 bg-white dark:bg-gray-700 border-t border-gray-100 dark:border-gray-600 space-y-2">
                    <!-- Single row: distance, consumption, time, SoC, badges, temp, actions, menu -->
                    <div class="flex items-center justify-between gap-2">
                      <div class="flex items-center flex-wrap gap-x-2 gap-y-1 min-w-0">
                        <MapIcon :class="['w-4 h-4 flex-shrink-0',
                          isAdmin && trip.dataSource === 'TESLA_LIVE'    ? 'text-red-500 dark:text-red-400' :
                          isAdmin && trip.dataSource === 'SMARTCAR_LIVE' ? 'text-blue-500 dark:text-blue-400' :
                          'text-emerald-600 dark:text-emerald-400']" />
                        <span v-if="trip.distanceKm != null" class="font-semibold text-emerald-700 dark:text-emerald-400 whitespace-nowrap">{{ formatDistance(trip.distanceKm, { round: false }) }}</span>
                        <span v-if="tripConsumption(trip)" class="inline-flex items-center text-[13px] text-gray-600 dark:text-gray-300 whitespace-nowrap">
                          {{ tripConsumption(trip)!.estimated ? '~' : '' }}{{ formatConsumption(tripConsumption(trip)!.kwhPer100km) }}
                        </span>
                        <span class="inline-flex items-center gap-1 whitespace-nowrap text-[13px] text-gray-500 dark:text-gray-400">
                          <ClockIcon class="w-3 h-3" />{{ formatTripTimeRange(trip.tripStartedAt, trip.tripEndedAt) }}
                        </span>
                        <span v-if="trip.socStart != null && trip.socEnd != null" class="inline-flex items-center gap-1 whitespace-nowrap text-[13px] text-gray-500 dark:text-gray-400">
                          <Battery0Icon class="w-3 h-3" />{{ trip.socStart }}% → {{ trip.socEnd }}%
                        </span>
                        <span v-if="trip.routeType"
                          class="inline-flex items-center px-2 py-0.5 bg-gray-50 dark:bg-gray-600 border border-gray-200 dark:border-gray-500 rounded-full text-xs text-gray-500 dark:text-gray-400 whitespace-nowrap">
                          {{ t('dashboard.trip_route_' + trip.routeType.toLowerCase()) }}
                        </span>
                        <span v-if="trip.dataSource === 'USER_CREATED'"
                          class="inline-flex items-center px-2 py-0.5 bg-gray-50 dark:bg-gray-600 border border-gray-200 dark:border-gray-500 rounded-full text-xs text-gray-400 whitespace-nowrap">
                          {{ t('dashboard.trip_manual') }}
                        </span>
                      </div>
                      <div class="flex items-center gap-1.5 flex-shrink-0">
                        <span v-if="trip.outsideTempCelsius != null"
                          :class="['inline-flex items-center gap-0.5 px-2 py-0.5 border rounded-full text-xs whitespace-nowrap', tempBadgeClass(trip.outsideTempCelsius)]">
                          <SunIcon class="w-3 h-3" />{{ trip.outsideTempCelsius }}°C
                        </span>
                        <div class="flex items-center gap-1 flex-shrink-0">
                          <template v-if="trip.dataSource !== 'USER_CREATED'">
                            <button @click="toggleRating(trip.id, 'positive', trip.feedback)"
                              class="p-2 md:p-1 rounded transition"
                              :class="effectiveRating(trip.id, trip.feedback) === 'positive' ? 'text-emerald-500' : 'text-gray-400 hover:text-emerald-500 hover:bg-emerald-50 dark:hover:bg-emerald-900/30'"
                              :title="t('dashboard.trip_feedback_positive')">
                              <HandThumbUpIcon class="w-3.5 h-3.5" />
                            </button>
                            <button @click="toggleRating(trip.id, 'negative', trip.feedback)"
                              class="p-2 md:p-1 rounded transition"
                              :class="effectiveRating(trip.id, trip.feedback) === 'negative' ? 'text-red-500' : 'text-gray-400 hover:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20'"
                              :title="t('dashboard.trip_feedback_negative')">
                              <HandThumbDownIcon class="w-3.5 h-3.5" />
                            </button>
                          </template>
                        </div>
                        <div class="relative flex-shrink-0">
                          <button @click.stop="openMenuTripId = openMenuTripId === trip.id ? null : trip.id"
                            class="p-1 rounded text-gray-400 hover:text-gray-600 dark:hover:text-gray-200 hover:bg-gray-100 dark:hover:bg-gray-600 transition">
                            <EllipsisVerticalIcon class="w-5 h-5" />
                          </button>
                          <div v-if="openMenuTripId === trip.id"
                            :class="['absolute right-0 w-44 bg-white dark:bg-gray-700 rounded-lg shadow-lg border border-gray-200 dark:border-gray-600 z-50 py-1 overflow-hidden',
                                     tripIdx === 0 ? 'top-full mt-1' : 'bottom-full mb-1']">
                            <button @click.stop="startAddTrip(trip.id, trip.tripStartedAt); openMenuTripId = null"
                              class="w-full flex items-center gap-2.5 px-3 py-2 text-sm text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-600 transition">
                              <PlusIcon class="w-4 h-4 flex-shrink-0" />{{ t('dashboard.action_add_trip') }}
                            </button>
                            <button @click.stop="startEditTrip(trip); openMenuTripId = null"
                              class="w-full flex items-center gap-2.5 px-3 py-2 text-sm text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-600 transition">
                              <PencilSquareIcon class="w-4 h-4 flex-shrink-0" />{{ t('dashboard.trip_edit') }}
                            </button>
                            <div class="border-t border-gray-100 dark:border-gray-600 mt-1 pt-1">
                              <button @click.stop="handleDeleteTrip(trip.id); openMenuTripId = null"
                                class="w-full flex items-center gap-2.5 px-3 py-2 text-sm text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20 transition">
                                <TrashIcon class="w-4 h-4 flex-shrink-0" />{{ t('dashboard.action_delete') }}
                              </button>
                            </div>
                          </div>
                        </div>
                      </div>
                    </div>
                    <!-- Feedback panel -->
                    <div v-if="feedbackOpenId === trip.id" class="pt-1 space-y-2 border-t border-gray-100 dark:border-gray-600">
                      <div class="flex flex-wrap gap-1">
                        <button v-for="tag in FEEDBACK_TAGS" :key="tag" @click="toggleFeedbackTag(tag)"
                          :class="['px-2 py-0.5 text-xs rounded-full border transition',
                            feedbackTags.includes(tag)
                              ? 'bg-red-100 border-red-300 text-red-700 dark:bg-red-900/30 dark:border-red-700 dark:text-red-400'
                              : 'bg-white dark:bg-gray-800 border-gray-200 dark:border-gray-600 text-gray-500 dark:text-gray-400 hover:border-red-300']">
                          {{ t('dashboard.trip_feedback_tag_' + tag) }}
                        </button>
                      </div>
                      <textarea v-model="feedbackComment" rows="2" :placeholder="t('dashboard.trip_feedback_comment')"
                        class="w-full px-2 py-1.5 text-xs border border-gray-200 dark:border-gray-600 rounded-md bg-white dark:bg-gray-800 text-gray-700 dark:text-gray-300 placeholder-gray-400 focus:outline-none focus:ring-1 focus:ring-red-400 resize-none" />
                      <div class="flex justify-end">
                        <button @click="sendNegativeFeedback(trip.id)"
                          class="px-3 py-1 text-xs font-medium bg-red-500 hover:bg-red-600 text-white rounded-md transition">
                          {{ t('dashboard.trip_feedback_send') }}
                        </button>
                      </div>
                    </div>
                  </div>
                  </Transition>

                  <!-- Trip inline edit mode -->
                  <Transition :css="false" @enter="onTripFormEnter" @after-enter="onTripFormAfterEnter" @leave="onTripFormLeave">
                  <div v-if="editingTripId === trip.id"
                       class="px-3 py-3 bg-white dark:bg-gray-700 border-t border-gray-100 dark:border-gray-600">
                  <TripForm v-model="tripForm" mode="edit"
                    :error="tripError" :saving="tripSaving" :distance-unit="distanceUnitLabel()"
                    @save="saveTripEdit(trip.id)" @cancel="cancelTripEditFull()">
                    <template #extra>
                  <!-- Feedback + merge trigger -->
                  <div v-if="trip.dataSource !== 'USER_CREATED' || (previousTripMap[trip.id] && mergePreviewForTripId !== trip.id)"
                       class="flex flex-col sm:flex-row items-center sm:justify-between gap-1 pt-1 border-t border-emerald-100 dark:border-emerald-800">
                    <div v-if="trip.dataSource !== 'USER_CREATED'" class="flex items-center gap-2">
                      <span class="text-xs text-gray-400 dark:text-gray-500">{{ t('dashboard.trip_feedback_label') }}</span>
                      <button @click="tripForm.feedback = 'positive'"
                        :class="['p-1 rounded transition', tripForm.feedback?.startsWith('positive') ? 'text-emerald-500 bg-emerald-50 dark:bg-emerald-900/30' : 'text-gray-400 hover:text-emerald-500 hover:bg-emerald-50 dark:hover:bg-emerald-900/30']">
                        <HandThumbUpIcon class="w-4 h-4" />
                      </button>
                      <button @click="tripForm.feedback = 'negative'"
                        :class="['p-1 rounded transition', tripForm.feedback?.startsWith('negative') ? 'text-red-500 bg-red-50 dark:bg-red-900/20' : 'text-gray-400 hover:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20']">
                        <HandThumbDownIcon class="w-4 h-4" />
                      </button>
                      <input v-if="tripForm.feedback?.startsWith('negative')" v-model="tripForm.feedback"
                        type="text" maxlength="200" :placeholder="t('dashboard.trip_feedback_comment')"
                        class="flex-1 px-2 py-1 text-xs border border-gray-200 dark:border-gray-600 rounded-md bg-white dark:bg-gray-800 text-gray-700 dark:text-gray-300 placeholder-gray-400 focus:outline-none focus:ring-1 focus:ring-red-400" />
                    </div>
                    <button v-if="previousTripMap[trip.id] && mergePreviewForTripId !== trip.id"
                      @click="mergePreviewForTripId = trip.id"
                      class="flex items-center gap-1 text-xs text-gray-400 dark:text-gray-500 hover:text-orange-500 dark:hover:text-orange-400 transition sm:ml-auto">
                      <ArrowsRightLeftIcon class="w-3.5 h-3.5" />
                      {{ t('dashboard.trip_merge_button') }}
                    </button>
                  </div>
                  <!-- Merge preview -->
                  <template v-if="previousTripMap[trip.id]">
                    <div v-if="mergePreviewForTripId === trip.id" class="pt-2 border-t border-orange-200 dark:border-orange-800 space-y-2">
                      <p class="text-xs font-medium text-orange-700 dark:text-orange-400">{{ t('dashboard.trip_merge_title') }}</p>
                      <div class="text-xs text-gray-500 dark:text-gray-400 bg-gray-50 dark:bg-gray-800 rounded px-2 py-1.5 space-y-0.5">
                        <div class="flex justify-between gap-2">
                          <span class="shrink-0">{{ t('dashboard.trip_merge_previous') }}</span>
                          <span class="text-right">{{ formatTripTimeRange(previousTripMap[trip.id].tripStartedAt, previousTripMap[trip.id].tripEndedAt) }}, {{ previousTripMap[trip.id].distanceKm ? formatDistance(previousTripMap[trip.id].distanceKm, { round: false }) : '?' }}</span>
                        </div>
                        <div class="flex justify-between gap-2">
                          <span class="shrink-0">{{ t('dashboard.trip_merge_this') }}</span>
                          <span class="text-right">{{ formatTripTimeRange(trip.tripStartedAt, trip.tripEndedAt) }}, {{ trip.distanceKm ? formatDistance(trip.distanceKm, { round: false }) : '?' }}</span>
                        </div>
                        <div class="flex justify-between gap-2 font-medium text-orange-700 dark:text-orange-400 pt-0.5 border-t border-gray-200 dark:border-gray-700">
                          <span class="shrink-0">{{ t('dashboard.trip_merge_result') }}</span>
                          <span class="text-right">{{ formatTripTimeRange(previousTripMap[trip.id].tripStartedAt, trip.tripEndedAt) }}, ~{{ formatDistance((previousTripMap[trip.id].distanceKm || 0) + (trip.distanceKm || 0), { round: false }) }}</span>
                        </div>
                      </div>
                      <p v-if="tripMergeError" class="text-xs text-red-500">{{ tripMergeError }}</p>
                      <div class="flex gap-2">
                        <button @click="doMergeTrip(trip.id, previousTripMap[trip.id].id)" :disabled="tripMerging"
                          class="px-3 py-1 text-xs font-medium bg-orange-500 hover:bg-orange-600 text-white rounded-md disabled:opacity-50 transition">
                          {{ t('dashboard.trip_merge_confirm') }}
                        </button>
                        <button @click="mergePreviewForTripId = null"
                          class="px-3 py-1 text-xs font-medium bg-white dark:bg-gray-700 border border-gray-200 dark:border-gray-600 text-gray-600 dark:text-gray-300 rounded-md hover:bg-gray-50 dark:hover:bg-gray-600 transition">
                          {{ t('dashboard.trip_cancel') }}
                        </button>
                      </div>
                    </div>
                  </template>
                    </template>
                  </TripForm>
                  </div>
                  </Transition>

                    </template><!-- end v-for trips -->
                  </div><!-- end expanded trips -->
                  </Transition>
                </div><!-- end trip group container (mobile) -->

                <!-- DESKTOP TRIP GROUP -->
                <div class="hidden gridfeed:block rounded-lg border border-emerald-200 dark:border-emerald-800/50 border-l-4 border-l-emerald-400 dark:border-l-emerald-500">
                  <!-- Header as grid row -->
                  <button type="button" @click="toggleTripGroup(item.groupId)"
                    :aria-expanded="!collapsedTripGroups.has(item.groupId)"
                    :aria-label="t('dashboard.trip_group_count', { count: item.groupSize }, item.groupSize)"
                    class="w-full grid grid-cols-[52px_90px_minmax(110px,1fr)_125px_80px_56px_88px_108px_40px] gap-2.5 items-center px-3 py-2 bg-emerald-50/60 dark:bg-emerald-900/15 hover:bg-emerald-50 dark:hover:bg-emerald-900/25 transition text-left focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-400 focus-visible:ring-inset">
                    <div class="flex items-center gap-1.5">
                      <MapIcon class="w-4 h-4 text-emerald-600 dark:text-emerald-400 flex-shrink-0" />
                      <span class="text-[10px] px-1.5 py-0.5 rounded bg-emerald-100 dark:bg-emerald-900/40 text-emerald-700 dark:text-emerald-300 font-medium">{{ item.groupSize }}×</span>
                    </div>
                    <div class="font-semibold text-rose-500 dark:text-rose-300 whitespace-nowrap text-sm">
                      <template v-if="tripGroupConsumedKwh(item) != null">−{{ tripGroupConsumedKwh(item)!.toFixed(2) }} kWh</template>
                      <span v-else class="text-gray-400 dark:text-gray-600">-</span>
                    </div>
                    <div class="text-sm text-gray-500 dark:text-gray-400 whitespace-nowrap truncate flex items-center gap-2">
                      <span v-if="item.dateRange">{{ item.dateRange }}</span>
                      <span v-if="item.totalPhantomKwh && authStore.canViewLiveTrips"
                        class="inline-flex items-center gap-0.5 text-amber-600 dark:text-amber-500 text-xs whitespace-nowrap"
                        :title="t('dashboard.phantom_drain_word')">
                        <BoltIcon class="w-3 h-3" />{{ item.totalPhantomKwh.toFixed(1) }} kWh
                      </span>
                    </div>
                    <div class="text-gray-400 dark:text-gray-600 text-sm">-</div>
                    <div class="text-xs text-gray-600 dark:text-gray-300 whitespace-nowrap">
                      <template v-if="tripGroupSocBoundaries(item)">{{ tripGroupSocBoundaries(item)!.start }}→{{ tripGroupSocBoundaries(item)!.end }}%</template>
                      <span v-else class="text-gray-400 dark:text-gray-600">-</span>
                    </div>
                    <div class="text-gray-400 dark:text-gray-600 text-sm">-</div>
                    <div>
                      <span v-if="item.totalKm" class="inline-flex items-center gap-1 px-2 py-0.5 bg-gray-50 dark:bg-gray-800 border border-gray-200 dark:border-gray-600 rounded-full text-xs text-gray-600 dark:text-gray-300 whitespace-nowrap">
                        +{{ formatDistance(item.totalKm) }}
                      </span>
                      <span v-else class="text-gray-400 dark:text-gray-600 text-sm">-</span>
                    </div>
                    <div class="flex justify-end">
                      <span v-if="tripGroupCostPer100km(item) != null"
                        class="inline-flex items-center px-2 py-0.5 border rounded-full text-xs font-medium whitespace-nowrap bg-emerald-50 dark:bg-emerald-900/20 border-emerald-200 dark:border-emerald-700/50 text-emerald-700 dark:text-emerald-300">
                        {{ formatCurrency(tripGroupCostPer100km(item)!) }}/100km
                      </span>
                      <span v-else class="text-gray-400 dark:text-gray-600 text-sm">-</span>
                    </div>
                    <div class="flex justify-end">
                      <ChevronUpIcon v-if="!collapsedTripGroups.has(item.groupId)" class="w-4 h-4 text-emerald-500 flex-shrink-0" />
                      <ChevronDownIcon v-else class="w-4 h-4 text-emerald-500 flex-shrink-0" />
                    </div>
                  </button>

                  <!-- Sub-trip rows in grid -->
                  <Transition :css="false" @enter="onTripFormEnter" @after-enter="onTripFormAfterEnter" @leave="onTripFormLeave">
                  <div v-if="!collapsedTripGroups.has(item.groupId)" class="bg-emerald-50/30 dark:bg-emerald-950/20">
                    <template v-for="(trip, tripIdx) in item.trips" :key="trip.id + '__d'">
                      <!-- Phantom drain separator between trips (AutoSync Live feature) -->
                      <div v-if="trip._phantomDrain && authStore.canViewLiveTrips && tripIdx !== 0"
                        class="flex items-center justify-center gap-1 py-1 border-t border-amber-300/30 dark:border-amber-700/30 bg-amber-50/30 dark:bg-amber-900/10">
                        <BoltIcon class="w-3 h-3 text-amber-600 dark:text-amber-500" />
                        <span class="text-[11px] text-amber-700 dark:text-amber-500">
                          {{ trip._phantomDrain.kwh.toFixed(2) }} kWh
                          <template v-if="selectedCar?.effectiveBatteryCapacityKwh">({{ (trip._phantomDrain.kwh / selectedCar.effectiveBatteryCapacityKwh * 100).toFixed(1) }}%)</template>
                          {{ t('dashboard.phantom_drain_word') }}
                        </span>
                      </div>
                      <!-- Add-trip form (full width inside container) -->
                      <Transition :css="false" @enter="onTripFormEnter" @after-enter="onTripFormAfterEnter" @leave="onTripFormLeave">
                      <div v-if="addingTripAfterId === trip.id"
                           class="px-3 py-3 border-t border-emerald-200/60 dark:border-emerald-800/40 bg-emerald-50/40 dark:bg-emerald-950/30">
                        <TripForm v-model="tripForm" mode="add"
                          :error="tripError" :saving="tripSaving" :distance-unit="distanceUnitLabel()"
                          @save="saveNewTrip()" @cancel="cancelTripEdit()" />
                      </div>
                      </Transition>
                      <!-- Display row -->
                      <div v-if="editingTripId !== trip.id && deletingTripId !== trip.id"
                        class="grid grid-cols-[52px_90px_minmax(110px,1fr)_125px_80px_56px_88px_108px_40px] gap-2.5 items-center px-3 py-1.5 border-t border-emerald-200/40 dark:border-emerald-800/30 hover:bg-emerald-50/60 dark:hover:bg-emerald-900/20 transition">
                        <div class="flex items-center gap-1.5 pl-4 text-gray-500 text-xs">└</div>
                        <div class="text-sm font-medium text-rose-500 dark:text-rose-300 whitespace-nowrap">
                          <template v-if="tripConsumption(trip) && trip.distanceKm">−{{ (tripConsumption(trip)!.kwhPer100km * trip.distanceKm / 100).toFixed(2) }} kWh</template>
                          <span v-else class="text-gray-400 dark:text-gray-600">-</span>
                        </div>
                        <div class="text-xs text-gray-500 dark:text-gray-400 whitespace-nowrap truncate">
                          <ClockIcon class="w-3 h-3 inline-block mr-0.5 -mt-0.5" />{{ formatTripTimeRange(trip.tripStartedAt, trip.tripEndedAt) }}
                        </div>
                        <div class="text-xs whitespace-nowrap">
                          <span v-if="tripConsumption(trip)" class="text-gray-600 dark:text-gray-300">
                            {{ tripConsumption(trip)!.estimated ? '~' : '' }}{{ formatConsumption(tripConsumption(trip)!.kwhPer100km) }}
                          </span>
                          <span v-else class="text-gray-400 dark:text-gray-600">-</span>
                        </div>
                        <div class="text-xs text-gray-600 dark:text-gray-300 whitespace-nowrap">
                          <template v-if="trip.socStart != null && trip.socEnd != null">{{ trip.socStart }}→{{ trip.socEnd }}%</template>
                          <span v-else class="text-gray-400 dark:text-gray-600">-</span>
                        </div>
                        <div class="text-gray-400 dark:text-gray-600 text-xs">-</div>
                        <div>
                          <span v-if="trip.distanceKm != null" class="inline-flex items-center gap-1 px-2 py-0.5 bg-gray-50 dark:bg-gray-800 border border-gray-200 dark:border-gray-600 rounded-full text-xs text-gray-600 dark:text-gray-300 whitespace-nowrap">
                            +{{ formatDistance(trip.distanceKm, { round: false }) }}
                          </span>
                          <span v-else class="text-gray-400 dark:text-gray-600 text-xs">-</span>
                        </div>
                        <div class="flex justify-end text-xs whitespace-nowrap">
                          <span v-if="tripCostPer100km(trip) != null" class="text-emerald-600 dark:text-emerald-300/80">
                            {{ formatCurrency(tripCostPer100km(trip)!) }}/100km
                          </span>
                          <span v-else class="text-gray-400 dark:text-gray-600">-</span>
                        </div>
                        <div class="flex justify-end relative">
                          <button type="button"
                            @click.stop="openMenuTripId = openMenuTripId === trip.id + '__d' ? null : trip.id + '__d'"
                            class="p-1 rounded text-gray-400 hover:text-gray-600 dark:hover:text-gray-200 hover:bg-gray-100 dark:hover:bg-gray-600 transition focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-400"
                            :aria-label="t('dashboard.action_menu_label') || 'Aktionen'"
                            aria-haspopup="menu"
                            :aria-expanded="openMenuTripId === trip.id + '__d'">
                            <EllipsisVerticalIcon class="w-4 h-4" aria-hidden="true" />
                          </button>
                          <div v-if="openMenuTripId === trip.id + '__d'"
                            role="menu"
                            :class="['absolute right-0 w-44 bg-white dark:bg-gray-700 rounded-lg shadow-lg border border-gray-200 dark:border-gray-600 z-50 py-1',
                                     tripIdx === 0 ? 'top-full mt-1' : 'bottom-full mb-1']">
                            <button role="menuitem" type="button" @click.stop="startAddTrip(trip.id, trip.tripStartedAt); openMenuTripId = null"
                              class="w-full flex items-center gap-2.5 px-3 py-2 text-sm text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-600 transition focus:outline-none focus-visible:bg-gray-100 dark:focus-visible:bg-gray-600">
                              <PlusIcon class="w-4 h-4 flex-shrink-0" aria-hidden="true" />{{ t('dashboard.trip_add') }}
                            </button>
                            <button role="menuitem" type="button" @click.stop="startEditTrip(trip); openMenuTripId = null"
                              class="w-full flex items-center gap-2.5 px-3 py-2 text-sm text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-600 transition focus:outline-none focus-visible:bg-gray-100 dark:focus-visible:bg-gray-600">
                              <PencilSquareIcon class="w-4 h-4 flex-shrink-0" aria-hidden="true" />{{ t('dashboard.action_edit') }}
                            </button>
                            <div class="border-t border-gray-100 dark:border-gray-600 mt-1 pt-1">
                              <button role="menuitem" type="button" @click.stop="handleDeleteTrip(trip.id); openMenuTripId = null"
                                class="w-full flex items-center gap-2.5 px-3 py-2 text-sm text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20 transition focus:outline-none focus-visible:bg-red-50 dark:focus-visible:bg-red-900/30">
                                <TrashIcon class="w-4 h-4 flex-shrink-0" aria-hidden="true" />{{ t('dashboard.action_delete') }}
                              </button>
                            </div>
                          </div>
                        </div>
                      </div>
                      <!-- Inline edit form -->
                      <Transition :css="false" @enter="onTripFormEnter" @after-enter="onTripFormAfterEnter" @leave="onTripFormLeave">
                      <div v-if="editingTripId === trip.id"
                           class="px-3 py-3 border-t border-emerald-200/60 dark:border-emerald-800/40 bg-emerald-50/40 dark:bg-emerald-950/30">
                        <TripForm v-model="tripForm" mode="edit"
                          :error="tripError" :saving="tripSaving" :distance-unit="distanceUnitLabel()"
                          @save="saveTripEdit(trip.id)" @cancel="cancelTripEditFull()" />
                      </div>
                      </Transition>
                    </template>
                  </div>
                  </Transition>
                </div>

              </template><!-- end tripGroup -->

              <!-- ===== REGULAR ENTRY (charge log / inaccessible trip) ===== -->
              <template v-else>
              <!-- Phantom drain -->
              <div v-if="item.entry._phantomDrain && authStore.canViewLiveTrips" class="flex items-center gap-2 px-4 mt-0.5 mb-2">
                <div class="flex-1 h-px bg-gray-200 dark:bg-gray-600" />
                <span class="inline-flex items-center gap-1 px-2 py-0.5 rounded-full border border-amber-300 dark:border-amber-600 bg-amber-50 dark:bg-amber-900/20 text-xs text-amber-600 dark:text-amber-400 whitespace-nowrap">
                  <BoltIcon class="w-3 h-3" />
                  {{ item.entry._phantomDrain.kwh.toFixed(2) }} kWh
                  <template v-if="selectedCar?.effectiveBatteryCapacityKwh">
                    ({{ (item.entry._phantomDrain.kwh / selectedCar.effectiveBatteryCapacityKwh * 100).toFixed(1) }}%)
                  </template>
                  {{ t('dashboard.phantom_drain_word') }}
                </span>
                <div class="flex-1 h-px bg-gray-200 dark:bg-gray-600" />
              </div>
              <!-- Add-trip form triggered from a charge entry -->
              <Transition :css="false" @enter="onTripFormEnter" @after-enter="onTripFormAfterEnter" @leave="onTripFormLeave">
              <div v-if="addingTripAfterId === item.entry.id" class="ml-2 mr-2 mt-1 p-3 rounded-lg shadow-sm ring-1 ring-black/5 dark:ring-white/10 border-l-4 border-l-emerald-400 dark:border-l-emerald-500 border-r-4 border-r-emerald-400 dark:border-r-emerald-500 bg-white dark:bg-gray-700 space-y-3">
                <div class="flex items-center justify-between gap-2">
                  <span class="text-sm font-medium text-emerald-800 dark:text-emerald-300 flex items-center gap-1.5">
                    <PlusIcon class="w-4 h-4" />{{ t('dashboard.trip_add') }}
                  </span>
                  <div class="flex gap-1">
                    <button @click="saveNewTrip()" :disabled="tripSaving"
                      class="px-3 py-1 text-xs font-medium bg-emerald-600 hover:bg-emerald-700 text-white rounded-md disabled:opacity-50 transition">
                      {{ t('dashboard.trip_save') }}
                    </button>
                    <button @click="cancelTripEdit()"
                      class="px-3 py-1 text-xs font-medium bg-white dark:bg-gray-700 border border-gray-200 dark:border-gray-600 text-gray-600 dark:text-gray-300 rounded-md hover:bg-gray-50 dark:hover:bg-gray-600 transition">
                      {{ t('dashboard.trip_cancel') }}
                    </button>
                  </div>
                </div>
                <p v-if="tripError" class="text-xs text-red-500 -mb-1">{{ tripError }}</p>
                <div class="grid grid-cols-1 sm:grid-cols-2 gap-2">
                  <div>
                    <label class="block text-xs text-gray-500 dark:text-gray-400 mb-1">{{ t('dashboard.trip_started_at') }}</label>
                    <input v-model="tripForm.tripStartedAt" type="datetime-local"
                      class="w-full px-2 py-1.5 text-sm border border-gray-200 dark:border-gray-600 rounded-md bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-1 focus:ring-emerald-400" />
                  </div>
                  <div>
                    <label class="block text-xs text-gray-500 dark:text-gray-400 mb-1">{{ t('dashboard.trip_ended_at') }}</label>
                    <input v-model="tripForm.tripEndedAt" type="datetime-local"
                      class="w-full px-2 py-1.5 text-sm border border-gray-200 dark:border-gray-600 rounded-md bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-1 focus:ring-emerald-400" />
                  </div>
                </div>
                <div class="grid grid-cols-1 sm:grid-cols-2 gap-2">
                  <div>
                    <label class="block text-xs text-gray-500 dark:text-gray-400 mb-1">{{ t('dashboard.trip_distance', { unit: distanceUnitLabel() }) }}</label>
                    <input v-model="tripForm.distanceKm" type="number" min="0" max="9999" step="0.1"
                      class="w-full px-2 py-1.5 text-sm border border-gray-200 dark:border-gray-600 rounded-md bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-1 focus:ring-emerald-400" />
                  </div>
                  <div>
                    <label class="block text-xs text-gray-500 dark:text-gray-400 mb-1">{{ t('dashboard.trip_route_type') }}</label>
                    <div class="flex gap-1">
                      <button v-for="rt in ['CITY','COMBINED','HIGHWAY']" :key="rt"
                        @click="tripForm.routeType = rt"
                        :class="['flex-1 px-1 py-1.5 text-xs rounded-md border transition',
                                 tripForm.routeType === rt
                                   ? 'bg-emerald-600 border-emerald-600 text-white'
                                   : 'bg-white dark:bg-gray-800 border-gray-200 dark:border-gray-600 text-gray-600 dark:text-gray-400 hover:border-emerald-400']">
                        {{ t('dashboard.trip_route_' + rt.toLowerCase()) }}
                      </button>
                    </div>
                  </div>
                </div>
              </div>
              </Transition>

              <!-- CHARGE ENTRY (DESKTOP GRID, normal logs only) -->
              <div v-if="!item.entry._isLadegruppe"
                class="hidden gridfeed:block relative bg-white dark:bg-gray-700 border border-gray-300 dark:border-gray-600 shadow-sm rounded-lg">
                <div class="grid grid-cols-[52px_90px_minmax(110px,1fr)_125px_80px_56px_88px_108px_40px] gap-2.5 items-center px-3 py-2.5">
                  <!-- 1. Type cell: Bolt + AC/DC badge -->
                  <div class="flex items-center gap-1.5">
                    <BoltIcon class="w-4 h-4 text-green-500 dark:text-green-400 flex-shrink-0" />
                    <span v-if="item.entry.chargingType === 'AC'"
                      class="text-[10px] px-1.5 py-0.5 rounded bg-amber-100 dark:bg-amber-900/40 text-amber-700 dark:text-amber-300 font-medium">AC</span>
                    <span v-else-if="item.entry.chargingType === 'DC'"
                      class="text-[10px] px-1.5 py-0.5 rounded bg-blue-100 dark:bg-blue-900/40 text-blue-700 dark:text-blue-300 font-medium">DC</span>
                  </div>
                  <!-- 2. Energy -->
                  <div class="font-semibold text-indigo-700 dark:text-indigo-300 whitespace-nowrap">
                    +{{ item.entry.kwhAtVehicle ?? item.entry.kwhCharged ?? '-' }} kWh
                  </div>
                  <!-- 3. Date -->
                  <div class="text-sm text-gray-500 dark:text-gray-400 whitespace-nowrap truncate">
                    {{ formatLogDate(item.entry.loggedAt) }}
                  </div>
                  <!-- 4. Consumption (or short-trip hint) -->
                  <div class="text-sm whitespace-nowrap">
                    <button
                      v-if="item.entry.consumptionKwhPer100km == null && isShortTrip(item.entry)"
                      type="button"
                      class="inline-flex items-center gap-1 text-xs text-gray-400 dark:text-gray-500 cursor-pointer focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-400 focus-visible:ring-offset-1 dark:focus-visible:ring-offset-gray-700 rounded"
                      :aria-expanded="openTooltipLogId === item.entry.id"
                      @click.stop="openTooltipLogId = openTooltipLogId === item.entry.id ? null : item.entry.id">
                      <InformationCircleIcon class="w-3 h-3 flex-shrink-0" aria-hidden="true" />
                      {{ t('dashboard.short_trip_hint') }}
                    </button>
                    <span v-else-if="item.entry.consumptionKwhPer100km != null"
                      :class="['inline-flex items-center gap-1 text-xs font-medium',
                               item.entry.consumptionImplausible
                                 ? 'text-red-600 dark:text-red-400'
                                 : item.entry.consumptionIsEstimated
                                   ? 'text-gray-500 dark:text-gray-400'
                                   : consumptionTextClass(item.entry.consumptionKwhPer100km, stats?.avgConsumptionKwhPer100km ?? null)]"
                      :title="item.entry.consumptionIsEstimated
                        ? 'Schätzwert: berechnet aus geladener Energie ÷ Distanz, da kein SoC-Wert vorhanden.'
                        : item.entry.consumptionQuality === 'SOC_DELTA'
                          ? 'Näherungswert: berechnet aus SoC-Differenz ohne direkte kWh-Messung.'
                          : undefined">
                      <button
                        v-if="item.entry.consumptionImplausible"
                        type="button"
                        class="flex-shrink-0 focus:outline-none focus-visible:ring-2 focus-visible:ring-amber-400 focus-visible:ring-offset-1 dark:focus-visible:ring-offset-gray-700 rounded"
                        :aria-label="t('dashboard.implausible_tooltip_title')"
                        :aria-expanded="openTooltipLogId === item.entry.id"
                        @click.stop="openTooltipLogId = openTooltipLogId === item.entry.id ? null : item.entry.id">
                        <ExclamationTriangleIcon class="w-3 h-3" aria-hidden="true" />
                      </button>
                      <InformationCircleIcon
                        v-if="item.entry.consumptionQuality === 'SOC_DELTA'"
                        class="w-3 h-3 flex-shrink-0 text-gray-400 dark:text-gray-500"
                        aria-hidden="true" />
                      {{ item.entry.consumptionIsEstimated ? '~' : '' }}{{ formatConsumption(item.entry.consumptionKwhPer100km) }}
                    </span>
                    <span v-else class="text-gray-400 dark:text-gray-600">-</span>
                  </div>
                  <!-- 5. SoC X→Y% -->
                  <div class="text-sm text-gray-600 dark:text-gray-300 whitespace-nowrap">
                    <template v-if="item.entry.socBeforeChargePercent != null && item.entry.socAfterChargePercent != null">
                      {{ item.entry.socBeforeChargePercent }}→{{ item.entry.socAfterChargePercent }}%
                    </template>
                    <template v-else-if="item.entry.socAfterChargePercent != null">
                      {{ item.entry.socAfterChargePercent }}%
                    </template>
                    <span v-else class="text-gray-400 dark:text-gray-600">-</span>
                  </div>
                  <!-- 6. Power -->
                  <div class="text-sm text-gray-600 dark:text-gray-300 whitespace-nowrap">
                    <template v-if="item.entry.maxChargingPowerKw">{{ item.entry.maxChargingPowerKw }} kW</template>
                    <span v-else class="text-gray-400 dark:text-gray-600">-</span>
                  </div>
                  <!-- 7. Distance chip -->
                  <div>
                    <component :is="item.entry.distanceSinceLastChargeKm != null && item.entry.odometerKm ? 'button' : 'span'"
                      v-if="item.entry.distanceSinceLastChargeKm != null || item.entry.odometerKm"
                      type="button"
                      class="inline-flex items-center gap-1 px-2 py-0.5 bg-gray-50 dark:bg-gray-800 border border-gray-200 dark:border-gray-600 rounded-full text-xs text-gray-600 dark:text-gray-300 whitespace-nowrap focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-400"
                      :class="item.entry.distanceSinceLastChargeKm != null && item.entry.odometerKm ? 'cursor-pointer shadow-[0_4px_0_0_#d1d5db] dark:shadow-[0_4px_0_0_#111827] active:shadow-none active:translate-y-1 transition-all duration-75' : ''"
                      @click.stop="toggleOdometerDisplay(item.entry.distanceSinceLastChargeKm, item.entry.odometerKm)"
                      @mousedown.stop>
                      <template v-if="item.entry.distanceSinceLastChargeKm != null && !showOdometer">+{{ formatDistance(item.entry.distanceSinceLastChargeKm) }}</template>
                      <template v-else>{{ item.entry.odometerKm != null ? formatDistance(item.entry.odometerKm) : '' }}</template>
                    </component>
                    <span v-else class="text-gray-400 dark:text-gray-600 text-sm">-</span>
                  </div>
                  <!-- 8. Price chip -->
                  <div class="flex justify-end">
                    <button v-if="item.entry.costEur != null && (item.entry.kwhCharged ?? item.entry.kwhAtVehicle)"
                      type="button"
                      :class="['inline-flex items-center px-2 py-0.5 border text-xs rounded-full font-medium whitespace-nowrap cursor-pointer transition-all duration-75 focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-400',
                               showCostAbsolute
                                 ? 'bg-gray-50 dark:bg-gray-700 border-gray-200 dark:border-gray-600 text-gray-600 dark:text-gray-300 shadow-[0_4px_0_0_#d1d5db] dark:shadow-[0_4px_0_0_#111827] hover:shadow-[0_2px_0_0_#d1d5db] dark:hover:shadow-[0_2px_0_0_#111827] hover:translate-y-0.5 active:shadow-none active:translate-y-1'
                                 : [(costBadgeClass(item.entry.costEur, item.entry.kwhCharged ?? item.entry.kwhAtVehicle) ?? 'bg-green-50 border-green-200 text-green-700'), 'shadow-[0_4px_0_0_#d1d5db] dark:shadow-[0_4px_0_0_#111827] hover:shadow-[0_2px_0_0_#d1d5db] dark:hover:shadow-[0_2px_0_0_#111827] hover:translate-y-0.5 active:shadow-none active:translate-y-1'].join(' ')]"
                      @click.stop="showCostAbsolute = !showCostAbsolute">
                      <template v-if="showCostAbsolute">{{ formatCurrency(item.entry.costEur) }}</template>
                      <template v-else>{{ formatCostPerKwh(item.entry.costEur / (item.entry.kwhCharged ?? item.entry.kwhAtVehicle)) }}</template>
                    </button>
                    <span v-else class="text-gray-400 dark:text-gray-600 text-sm">-</span>
                  </div>
                  <!-- 9. Actions menu -->
                  <div class="flex justify-end relative">
                    <button type="button"
                      @click.stop="openMenuLogId = openMenuLogId === item.entry.id + '__d' ? null : item.entry.id + '__d'"
                      class="p-1 rounded text-gray-400 hover:text-gray-600 dark:hover:text-gray-200 hover:bg-gray-100 dark:hover:bg-gray-600 transition focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-400"
                      :aria-label="t('dashboard.action_menu_label') || 'Aktionen'"
                      aria-haspopup="menu"
                      :aria-expanded="openMenuLogId === item.entry.id + '__d'">
                      <EllipsisVerticalIcon class="w-5 h-5" aria-hidden="true" />
                    </button>
                    <div v-if="openMenuLogId === item.entry.id + '__d'"
                      role="menu"
                      class="absolute right-0 top-full mt-1 w-44 bg-white dark:bg-gray-700 rounded-lg shadow-lg border border-gray-200 dark:border-gray-600 z-50 py-1 overflow-hidden">
                      <button role="menuitem" type="button" @click.stop="editingLog = item.entry; openMenuLogId = null"
                        class="w-full flex items-center gap-2.5 px-3 py-2 text-sm text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-600 transition focus:outline-none focus-visible:bg-gray-100 dark:focus-visible:bg-gray-600">
                        <PencilSquareIcon class="w-4 h-4 flex-shrink-0" aria-hidden="true" />{{ t('dashboard.action_edit') }}
                      </button>
                      <button role="menuitem" type="button" @click.stop="startAddTrip(item.entry.id); openMenuLogId = null"
                        class="w-full flex items-center gap-2.5 px-3 py-2 text-sm text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-600 transition focus:outline-none focus-visible:bg-gray-100 dark:focus-visible:bg-gray-600">
                        <MapIcon class="w-4 h-4 flex-shrink-0" aria-hidden="true" />{{ t('dashboard.action_add_trip') }}
                      </button>
                      <button v-if="otherCars.length > 0" role="menuitem" type="button" @click.stop="openReassignModal(item.entry); openMenuLogId = null"
                        class="w-full flex items-center gap-2.5 px-3 py-2 text-sm text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-600 transition focus:outline-none focus-visible:bg-gray-100 dark:focus-visible:bg-gray-600">
                        <ArrowsRightLeftIcon class="w-4 h-4 flex-shrink-0" aria-hidden="true" />{{ t('dashboard.action_reassign') }}
                      </button>
                      <div class="border-t border-gray-100 dark:border-gray-600 mt-1 pt-1">
                        <button role="menuitem" type="button" @click.stop="deleteLog(item.entry.id); openMenuLogId = null"
                          class="w-full flex items-center gap-2.5 px-3 py-2 text-sm text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20 transition focus:outline-none focus-visible:bg-red-50 dark:focus-visible:bg-red-900/30">
                          <TrashIcon class="w-4 h-4 flex-shrink-0" aria-hidden="true" />{{ t('dashboard.action_delete') }}
                        </button>
                      </div>
                    </div>
                  </div>
                </div>
                <!-- Tooltip panels (span full width below the row) -->
                <div
                  v-if="item.entry.consumptionImplausible && openTooltipLogId === item.entry.id"
                  class="px-3 pb-2.5">
                  <div class="p-2.5 rounded-lg bg-amber-50 dark:bg-amber-900/20 border border-amber-200 dark:border-amber-700 text-xs text-amber-800 dark:text-amber-300 space-y-1">
                    <p class="font-medium">{{ t('dashboard.implausible_tooltip_title') }}</p>
                    <p>{{ t('dashboard.implausible_tooltip_desc', { value: formatConsumption(item.entry.consumptionKwhPer100km) }) }}</p>
                    <ul class="list-disc list-inside space-y-0.5 mt-1">
                      <li>{{ t('dashboard.implausible_tooltip_cause1') }}</li>
                      <li>{{ t('dashboard.implausible_tooltip_cause2') }}</li>
                    </ul>
                  </div>
                </div>
                <div
                  v-if="item.entry.consumptionKwhPer100km == null && isShortTrip(item.entry) && openTooltipLogId === item.entry.id"
                  class="px-3 pb-2.5">
                  <div class="p-2.5 rounded-lg bg-gray-50 dark:bg-gray-800 border border-gray-200 dark:border-gray-700 text-xs text-gray-600 dark:text-gray-300 space-y-1">
                    <p class="font-medium">{{ t('dashboard.short_trip_tooltip_title') }}</p>
                    <p>{{ t('dashboard.short_trip_tooltip_desc') }}</p>
                  </div>
                </div>
                <!-- Brutto/Efficiency line (Tesla AT_VEHICLE logs) -->
                <div v-if="item.entry.kwhCharged != null && item.entry.kwhAtVehicle != null"
                  class="px-3 pb-2 -mt-1 flex items-center gap-3 text-[11px] text-gray-500 dark:text-gray-400">
                  <span>{{ item.entry.kwhCharged }} kWh brutto</span>
                  <span :class="['font-medium', chargingEfficiency(item.entry.kwhCharged, item.entry.kwhAtVehicle)! >= 90 ? 'text-green-600 dark:text-green-400' : 'text-amber-600 dark:text-amber-400']">
                    {{ chargingEfficiency(item.entry.kwhCharged, item.entry.kwhAtVehicle) }}% Effizienz
                  </span>
                  <span v-if="sourceInfo(item.entry.dataSource)"
                    :class="['inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-medium',
                             sourceInfo(item.entry.dataSource)!.classes]">
                    {{ sourceInfo(item.entry.dataSource)!.label }}
                  </span>
                </div>
              </div>

              <!-- CHARGE ENTRY (DESKTOP GRID, Ladegruppe) -->
              <div v-if="item.entry._isLadegruppe"
                class="hidden gridfeed:block rounded-lg border border-blue-200 dark:border-blue-800/60 border-l-4 border-l-blue-400 dark:border-l-blue-500">
                <button type="button" @click="toggleLadegruppe(item.entry.id)"
                  :aria-expanded="expandedGroups.has(item.entry.id)"
                  class="w-full grid grid-cols-[52px_90px_minmax(110px,1fr)_125px_80px_56px_88px_108px_40px] gap-2.5 items-center px-3 py-2 bg-blue-50/40 dark:bg-blue-900/15 hover:bg-blue-50 dark:hover:bg-blue-900/25 transition text-left focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-400 focus-visible:ring-inset">
                  <div class="flex items-center gap-1.5">
                    <BoltIcon class="w-4 h-4 text-blue-600 dark:text-blue-400 flex-shrink-0" />
                    <span class="text-[10px] px-1.5 py-0.5 rounded bg-blue-100 dark:bg-blue-900/40 text-blue-700 dark:text-blue-300 font-medium">{{ item.entry._topUps?.length ?? 0 }}×</span>
                  </div>
                  <div class="font-semibold text-blue-700 dark:text-blue-300 whitespace-nowrap text-sm">
                    +{{ item.entry._totalKwh }} kWh
                  </div>
                  <div class="text-sm text-gray-500 dark:text-gray-400 whitespace-nowrap truncate">
                    {{ item.entry._dateRangeLabel }}
                  </div>
                  <div class="text-xs whitespace-nowrap">
                    <span v-if="item.entry._totalConsumption != null"
                      :class="['font-medium', consumptionTextClass(item.entry._totalConsumption, stats?.avgConsumptionKwhPer100km ?? null)]">
                      {{ formatConsumption(item.entry._totalConsumption) }}
                    </span>
                    <span v-else class="text-gray-400 dark:text-gray-600">-</span>
                  </div>
                  <div class="text-xs text-gray-600 dark:text-gray-300 whitespace-nowrap">
                    <template v-if="item.entry._maxSoc != null">{{ item.entry._maxSoc }}%</template>
                    <span v-else class="text-gray-400 dark:text-gray-600">-</span>
                  </div>
                  <div class="text-xs text-gray-600 dark:text-gray-300 whitespace-nowrap">
                    <template v-if="item.entry._maxPower">{{ item.entry._maxPower }} kW</template>
                    <span v-else class="text-gray-400 dark:text-gray-600">-</span>
                  </div>
                  <div>
                    <component :is="item.entry.distanceSinceLastChargeKm != null && item.entry.odometerKm ? 'button' : 'span'"
                      v-if="item.entry.distanceSinceLastChargeKm != null || item.entry.odometerKm"
                      type="button"
                      class="inline-flex items-center gap-1 px-2 py-0.5 bg-gray-50 dark:bg-gray-800 border border-gray-200 dark:border-gray-600 rounded-full text-xs text-gray-600 dark:text-gray-300 whitespace-nowrap focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-400"
                      :class="item.entry.distanceSinceLastChargeKm != null && item.entry.odometerKm ? 'cursor-pointer shadow-[0_4px_0_0_#d1d5db] dark:shadow-[0_4px_0_0_#111827] active:shadow-none active:translate-y-1 transition-all duration-75' : ''"
                      @click.stop="toggleOdometerDisplay(item.entry.distanceSinceLastChargeKm, item.entry.odometerKm)"
                      @mousedown.stop>
                      <template v-if="item.entry.distanceSinceLastChargeKm != null && !showOdometer">+{{ formatDistance(item.entry.distanceSinceLastChargeKm) }}</template>
                      <template v-else>{{ item.entry.odometerKm != null ? formatDistance(item.entry.odometerKm) : '' }}</template>
                    </component>
                    <span v-else class="text-gray-400 dark:text-gray-600 text-xs">-</span>
                  </div>
                  <div class="flex justify-end">
                    <button v-if="item.entry._totalCostEur != null && item.entry._totalKwh"
                      type="button"
                      :class="['inline-flex items-center px-2 py-0.5 border text-xs rounded-full font-medium whitespace-nowrap cursor-pointer transition-all duration-75 focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-400',
                               showCostAbsolute
                                 ? 'bg-gray-50 dark:bg-gray-700 border-gray-200 dark:border-gray-600 text-gray-600 dark:text-gray-300 shadow-[0_4px_0_0_#d1d5db] dark:shadow-[0_4px_0_0_#111827]'
                                 : [(costBadgeClass(item.entry._totalCostEur, item.entry._totalKwh) ?? 'bg-green-50 border-green-200 text-green-700'), 'shadow-[0_4px_0_0_#d1d5db] dark:shadow-[0_4px_0_0_#111827]'].join(' ')]"
                      @click.stop="showCostAbsolute = !showCostAbsolute">
                      <template v-if="showCostAbsolute">{{ formatCurrency(item.entry._totalCostEur) }}</template>
                      <template v-else>{{ formatCostPerKwh(item.entry._totalCostEur / item.entry._costKwh) }}</template>
                    </button>
                    <span v-else class="text-gray-400 dark:text-gray-600 text-xs">-</span>
                  </div>
                  <div class="flex justify-end">
                    <ChevronUpIcon v-if="expandedGroups.has(item.entry.id)" class="w-4 h-4 text-blue-400 dark:text-blue-500 flex-shrink-0" />
                    <ChevronDownIcon v-else class="w-4 h-4 text-blue-400 dark:text-blue-500 flex-shrink-0" />
                  </div>
                </button>

                <!-- Top-Up rows -->
                <Transition name="slide-down">
                  <div v-if="expandedGroups.has(item.entry.id)" class="bg-blue-50/30 dark:bg-blue-950/20">
                    <div v-for="topUp in item.entry._topUps" :key="topUp.id + '__d'"
                      class="grid grid-cols-[52px_90px_minmax(110px,1fr)_125px_80px_56px_88px_108px_40px] gap-2.5 items-center px-3 py-1.5 border-t border-blue-200/40 dark:border-blue-800/30 hover:bg-blue-50/60 dark:hover:bg-blue-900/20 transition">
                      <div class="flex items-center gap-1.5 pl-4 text-gray-500 text-xs">└</div>
                      <div class="text-sm font-medium text-blue-700 dark:text-blue-300 whitespace-nowrap">+{{ topUp.kwhAtVehicle ?? topUp.kwhCharged ?? '-' }} kWh</div>
                      <div class="text-xs text-gray-500 dark:text-gray-400 whitespace-nowrap truncate">
                        <template v-if="item.entry._spansMultipleDays">{{ formatLogDate(topUp.loggedAt) }}</template>
                        <template v-else><ClockIcon class="w-3 h-3 inline-block mr-0.5 -mt-0.5" />{{ new Date(topUp.loggedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) }}</template>
                      </div>
                      <div class="text-gray-400 dark:text-gray-600 text-xs">-</div>
                      <div class="text-xs text-gray-600 dark:text-gray-300 whitespace-nowrap">
                        <template v-if="topUp.socAfterChargePercent != null">{{ topUp.socAfterChargePercent }}%</template>
                        <span v-else class="text-gray-400 dark:text-gray-600">-</span>
                      </div>
                      <div class="text-xs text-gray-600 dark:text-gray-300 whitespace-nowrap">
                        <template v-if="topUp.maxChargingPowerKw">{{ topUp.maxChargingPowerKw }} kW</template>
                        <span v-else class="text-gray-400 dark:text-gray-600">-</span>
                      </div>
                      <div class="text-xs text-gray-500 dark:text-gray-400">
                        <template v-if="topUp.chargeDurationMinutes">{{ topUp.chargeDurationMinutes }}min</template>
                        <span v-else class="text-gray-400 dark:text-gray-600">-</span>
                      </div>
                      <div class="text-gray-400 dark:text-gray-600 text-xs">-</div>
                      <div class="flex justify-end relative">
                        <button type="button"
                          @click.stop="openMenuTopUpId = openMenuTopUpId === topUp.id + '__d' ? null : topUp.id + '__d'"
                          class="p-1 rounded text-gray-400 hover:text-gray-600 dark:hover:text-gray-200 hover:bg-gray-100 dark:hover:bg-gray-600 transition focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-400"
                          :aria-label="t('dashboard.action_menu_label') || 'Aktionen'"
                          aria-haspopup="menu"
                          :aria-expanded="openMenuTopUpId === topUp.id + '__d'">
                          <EllipsisVerticalIcon class="w-4 h-4" aria-hidden="true" />
                        </button>
                        <div v-if="openMenuTopUpId === topUp.id + '__d'"
                          role="menu"
                          class="absolute right-0 top-full mt-1 w-40 bg-white dark:bg-gray-700 rounded-lg shadow-lg border border-gray-200 dark:border-gray-600 z-50 py-1 overflow-hidden">
                          <button role="menuitem" type="button" @click.stop="editingLog = topUp; openMenuTopUpId = null"
                            class="w-full flex items-center gap-2.5 px-3 py-2 text-sm text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-600 transition focus:outline-none focus-visible:bg-gray-100 dark:focus-visible:bg-gray-600">
                            <PencilSquareIcon class="w-4 h-4 flex-shrink-0" aria-hidden="true" />{{ t('dashboard.action_edit') }}
                          </button>
                          <div class="border-t border-gray-100 dark:border-gray-600 mt-1 pt-1">
                            <button role="menuitem" type="button" @click.stop="deleteLog(topUp.id); openMenuTopUpId = null"
                              class="w-full flex items-center gap-2.5 px-3 py-2 text-sm text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20 transition focus:outline-none focus-visible:bg-red-50 dark:focus-visible:bg-red-900/30">
                              <TrashIcon class="w-4 h-4 flex-shrink-0" aria-hidden="true" />{{ t('dashboard.action_delete') }}
                            </button>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>
                </Transition>
              </div>

              <!-- CHARGE ENTRY -->
              <div>
              <div
                :class="['relative p-3 border rounded-lg space-y-2 gridfeed:hidden',
                         item.entry._isLadegruppe
                           ? 'bg-white dark:bg-gray-700 border-blue-200 dark:border-blue-800 cursor-pointer'
                           : 'bg-white dark:bg-gray-700 border-gray-300 dark:border-gray-600 shadow-sm']"
                v-bind="item.entry._isLadegruppe ? { role: 'button', tabindex: '0', 'aria-expanded': expandedGroups.has(item.entry.id) } : {}"
                @click="item.entry._isLadegruppe ? toggleLadegruppe(item.entry.id) : null"
                @keydown.enter.space.prevent="item.entry._isLadegruppe ? toggleLadegruppe(item.entry.id) : null">

                <!-- LADEGRUPPE HEADER -->
                <template v-if="item.entry._isLadegruppe">
                  <div class="flex items-center justify-between gap-2">
                    <div class="flex items-center gap-2 min-w-0 overflow-hidden">
                      <BoltIcon class="w-4 h-4 text-green-500 dark:text-green-400 flex-shrink-0" />
                      <span class="font-semibold text-indigo-700 dark:text-indigo-300 whitespace-nowrap">{{ item.entry._totalKwh }} kWh</span>
                      <span class="text-xs text-gray-500 whitespace-nowrap truncate">{{ item.entry._dateRangeLabel }}</span>
                    </div>
                    <div class="flex items-center gap-1.5 flex-shrink-0">
                      <span v-if="item.entry._totalCostEur != null && item.entry._totalKwh"
                        :class="['inline-flex items-center px-2 py-0.5 border text-xs rounded-full font-medium whitespace-nowrap cursor-pointer transition-all duration-75',
                                 showCostAbsolute
                                   ? 'bg-gray-50 dark:bg-gray-700 border-gray-200 dark:border-gray-600 text-gray-600 dark:text-gray-300 shadow-[0_4px_0_0_#d1d5db] dark:shadow-[0_4px_0_0_#111827] hover:shadow-[0_2px_0_0_#d1d5db] dark:hover:shadow-[0_2px_0_0_#111827] hover:translate-y-0.5 active:shadow-none active:translate-y-1'
                                   : [(costBadgeClass(item.entry._totalCostEur, item.entry._totalKwh) ?? 'bg-green-50 border-green-200 text-green-700'), 'shadow-[0_4px_0_0_#d1d5db] dark:shadow-[0_4px_0_0_#111827] hover:shadow-[0_2px_0_0_#d1d5db] dark:hover:shadow-[0_2px_0_0_#111827] hover:translate-y-0.5 active:shadow-none active:translate-y-1'].join(' ')]"
                        @click.stop="showCostAbsolute = !showCostAbsolute"
                        @mousedown.stop>
                        <template v-if="showCostAbsolute">{{ formatCurrency(item.entry._totalCostEur) }}</template>
                        <template v-else>{{ formatCostPerKwh(item.entry._totalCostEur / item.entry._costKwh) }}</template>
                      </span>
                      <ChevronDownIcon v-if="!expandedGroups.has(item.entry.id)" class="w-4 h-4 text-blue-400 dark:text-blue-500 flex-shrink-0" />
                      <ChevronUpIcon v-else class="w-4 h-4 text-blue-400 dark:text-blue-500 flex-shrink-0" />
                    </div>
                  </div>
                  <!-- Expanded: stats + chips -->
                  <Transition :css="false" @enter="onTripFormEnter" @after-enter="onTripFormAfterEnter" @leave="onTripFormLeave">
                  <div v-if="expandedGroups.has(item.entry.id)" class="space-y-2">
                    <!-- Zeile 2: consumption, maxSoc, maxPower -->
                    <div v-if="item.entry._totalConsumption != null || item.entry._maxSoc != null || item.entry._maxPower"
                      class="flex flex-wrap gap-x-3 gap-y-0.5 items-center">
                      <span v-if="item.entry._totalConsumption != null"
                        :class="['inline-flex items-center gap-1 text-xs font-medium whitespace-nowrap',
                                 consumptionTextClass(item.entry._totalConsumption, stats?.avgConsumptionKwhPer100km ?? null)]">
                        {{ formatConsumption(item.entry._totalConsumption) }}
                      </span>
                      <span v-if="item.entry._maxSoc != null" class="inline-flex items-center gap-1 text-xs text-gray-400 dark:text-gray-500 whitespace-nowrap">
                        <Battery0Icon class="w-3 h-3" />{{ item.entry._maxSoc }}%
                      </span>
                      <span v-if="item.entry._maxPower" class="inline-flex items-center gap-1 text-xs text-gray-400 dark:text-gray-500 whitespace-nowrap">
                        <BoltIcon class="w-3 h-3" />{{ item.entry._maxPower }} kW
                      </span>
                    </div>
                    <!-- Zeile 3: source + distance -->
                    <div class="flex flex-wrap gap-1.5">
                      <span v-if="sourceInfo(item.entry._commonDataSource)"
                        :class="['inline-flex items-center px-1.5 py-0.5 rounded text-xs font-medium whitespace-nowrap',
                                 sourceInfo(item.entry._commonDataSource)!.classes]">
                        {{ sourceInfo(item.entry._commonDataSource)!.label }}
                      </span>
                      <span
                        v-if="item.entry.distanceSinceLastChargeKm != null || item.entry.odometerKm"
                        class="inline-flex items-center gap-1 px-2 py-0.5 bg-gray-50 dark:bg-gray-700 border border-gray-200 dark:border-gray-600 rounded-full text-xs text-gray-600 dark:text-gray-300 whitespace-nowrap"
                        :class="item.entry.distanceSinceLastChargeKm != null && item.entry.odometerKm ? 'cursor-pointer shadow-[0_4px_0_0_#d1d5db] dark:shadow-[0_4px_0_0_#111827] active:shadow-none active:translate-y-1 transition-all duration-75' : ''"
                        @click.stop="toggleOdometerDisplay(item.entry.distanceSinceLastChargeKm, item.entry.odometerKm)"
                        @mousedown.stop
                      >
                        <template v-if="item.entry.distanceSinceLastChargeKm != null && !showOdometer">+{{ formatDistance(item.entry.distanceSinceLastChargeKm) }}</template>
                        <template v-else>{{ item.entry.odometerKm != null ? formatDistance(item.entry.odometerKm) : '' }}</template>
                      </span>
                    </div>
                  </div>
                  </Transition>
                </template>

                <!-- NORMAL LOG HEADER -->
                <template v-else>
                <button type="button" class="w-full flex items-center justify-between gap-2 text-left select-none"
                  :aria-expanded="expandedLogs.has(item.entry.id)"
                  @click="toggleLogExpanded(item.entry.id)">
                  <div class="flex items-center gap-2 min-w-0 overflow-hidden">
                    <BoltIcon class="w-4 h-4 text-green-500 dark:text-green-400 flex-shrink-0" />
                    <span class="font-semibold text-indigo-700 dark:text-indigo-300 whitespace-nowrap">{{ item.entry.kwhAtVehicle ?? item.entry.kwhCharged ?? '-' }} kWh</span>
                    <span class="text-xs text-gray-500 whitespace-nowrap truncate">{{ formatLogDate(item.entry.loggedAt) }}</span>
                  </div>
                  <div class="flex items-center gap-1.5 flex-shrink-0">
                    <span v-if="item.entry.costEur != null && (item.entry.kwhCharged ?? item.entry.kwhAtVehicle)"
                      :class="['inline-flex items-center px-2 py-0.5 border text-xs rounded-full font-medium whitespace-nowrap cursor-pointer transition-all duration-75',
                               showCostAbsolute
                                 ? 'bg-gray-50 dark:bg-gray-700 border-gray-200 dark:border-gray-600 text-gray-600 dark:text-gray-300 shadow-[0_4px_0_0_#d1d5db] dark:shadow-[0_4px_0_0_#111827] hover:shadow-[0_2px_0_0_#d1d5db] dark:hover:shadow-[0_2px_0_0_#111827] hover:translate-y-0.5 active:shadow-none active:translate-y-1'
                                 : [(costBadgeClass(item.entry.costEur, item.entry.kwhCharged ?? item.entry.kwhAtVehicle) ?? 'bg-green-50 border-green-200 text-green-700'), 'shadow-[0_4px_0_0_#d1d5db] dark:shadow-[0_4px_0_0_#111827] hover:shadow-[0_2px_0_0_#d1d5db] dark:hover:shadow-[0_2px_0_0_#111827] hover:translate-y-0.5 active:shadow-none active:translate-y-1'].join(' ')]"
                      @click.stop="showCostAbsolute = !showCostAbsolute">
                      <template v-if="showCostAbsolute">{{ formatCurrency(item.entry.costEur) }}</template>
                      <template v-else>{{ formatCostPerKwh(item.entry.costEur / (item.entry.kwhCharged ?? item.entry.kwhAtVehicle)) }}</template>
                    </span>
                    <ChevronDownIcon v-if="!expandedLogs.has(item.entry.id)" class="w-4 h-4 text-gray-400 flex-shrink-0" />
                    <ChevronUpIcon v-else class="w-4 h-4 text-gray-400 flex-shrink-0" />
                  </div>
                </button>
                </template>
                <!-- Expanded content (normal log only) -->
                <Transition :css="false" @enter="onTripFormEnter" @after-enter="onTripFormAfterEnter" @leave="onTripFormLeave">
                <div v-if="!item.entry._isLadegruppe && expandedLogs.has(item.entry.id)" class="space-y-2">
                  <!-- Brutto + Ladeeffizienz -->
                  <div v-if="item.entry.kwhCharged != null && item.entry.kwhAtVehicle != null"
                    class="flex flex-wrap gap-x-4 gap-y-0.5 items-center text-xs text-gray-500 dark:text-gray-400">
                    <span class="whitespace-nowrap">{{ item.entry.kwhCharged }} kWh brutto</span>
                    <span
                      :class="['font-medium whitespace-nowrap', chargingEfficiency(item.entry.kwhCharged, item.entry.kwhAtVehicle)! >= 90 ? 'text-green-600 dark:text-green-400' : 'text-amber-600 dark:text-amber-400']">
                      {{ chargingEfficiency(item.entry.kwhCharged, item.entry.kwhAtVehicle) }}% Effizienz
                    </span>
                  </div>
                  <!-- Plain text stats - Zeile 2 -->
                  <div v-if="item.entry.consumptionKwhPer100km != null || isShortTrip(item.entry) || item.entry.chargeDurationMinutes || item.entry.socAfterChargePercent != null || (item.entry.costEur != null && !item.entry.kwhCharged && !item.entry.kwhAtVehicle)"
                    class="flex flex-wrap gap-x-3 gap-y-0.5 items-center">
                    <button
                      v-if="item.entry.consumptionKwhPer100km == null && isShortTrip(item.entry)"
                      type="button"
                      class="inline-flex items-center gap-1 text-xs text-gray-400 dark:text-gray-500 whitespace-nowrap cursor-pointer focus:outline-none"
                      @click.stop="openTooltipLogId = openTooltipLogId === item.entry.id ? null : item.entry.id">
                      <InformationCircleIcon class="w-3 h-3 flex-shrink-0" />
                      {{ t('dashboard.short_trip_hint') }}
                    </button>
                    <span v-if="item.entry.consumptionKwhPer100km != null"
                      :class="['inline-flex items-center gap-1 text-xs font-medium whitespace-nowrap',
                               item.entry.consumptionImplausible
                                 ? 'text-red-600 dark:text-red-400'
                                 : item.entry.consumptionIsEstimated
                                   ? 'text-gray-500 dark:text-gray-400'
                                   : consumptionTextClass(item.entry.consumptionKwhPer100km, stats?.avgConsumptionKwhPer100km ?? null)]"
                      :title="item.entry.consumptionIsEstimated
                        ? 'Schätzwert: berechnet aus geladener Energie ÷ Distanz, da kein SoC-Wert vorhanden.'
                        : item.entry.consumptionQuality === 'SOC_DELTA'
                          ? 'Näherungswert: berechnet aus SoC-Differenz ohne direkte kWh-Messung.'
                          : undefined">
                      <button
                        v-if="item.entry.consumptionImplausible"
                        class="flex-shrink-0 focus:outline-none"
                        @click.stop="openTooltipLogId = openTooltipLogId === item.entry.id ? null : item.entry.id">
                        <ExclamationTriangleIcon class="w-3 h-3" />
                      </button>
                      <InformationCircleIcon
                        v-if="item.entry.consumptionQuality === 'SOC_DELTA'"
                        class="w-3 h-3 flex-shrink-0 text-gray-400 dark:text-gray-500" />
                      {{ item.entry.consumptionIsEstimated ? '~' : '' }}{{ formatConsumption(item.entry.consumptionKwhPer100km) }}
                    </span>
                    <span v-if="item.entry.costEur != null && !item.entry.kwhCharged && !item.entry.kwhAtVehicle" class="inline-flex items-center gap-1 text-xs text-gray-500 dark:text-gray-400 whitespace-nowrap">
                      {{ formatCurrency(item.entry.costEur) }}
                    </span>
                    <span v-if="item.entry.chargeDurationMinutes" class="inline-flex items-center gap-1 text-xs text-gray-500 dark:text-gray-400 whitespace-nowrap">
                      <ClockIcon class="w-3 h-3" />{{ item.entry.chargeDurationMinutes }}min
                    </span>
                    <span v-if="item.entry.socAfterChargePercent != null" class="inline-flex items-center gap-1 text-xs text-gray-500 dark:text-gray-400 whitespace-nowrap">
                      <Battery0Icon class="w-3 h-3" />{{ item.entry.socAfterChargePercent }}%
                    </span>
                    <span v-if="item.entry.maxChargingPowerKw" class="inline-flex items-center gap-1 text-xs text-gray-500 dark:text-gray-400 whitespace-nowrap">
                      <BoltIcon class="w-3 h-3" />{{ item.entry.maxChargingPowerKw }} kW
                    </span>
                  </div>
                  <!-- Chips + Aktions-Menü - Zeile 3 -->
                  <div class="flex flex-wrap gap-1.5 items-center">
                    <span v-if="sourceInfo(item.entry.dataSource)"
                      :class="['inline-flex items-center px-1.5 py-0.5 rounded text-xs font-medium whitespace-nowrap',
                               sourceInfo(item.entry.dataSource)!.classes]">
                      {{ sourceInfo(item.entry.dataSource)!.label }}
                    </span>
                    <span v-if="item.entry.temperatureCelsius != null"
                      :class="['inline-flex items-center gap-0.5 px-2 py-0.5 border rounded-full text-xs whitespace-nowrap', tempBadgeClass(item.entry.temperatureCelsius)]">
                      <SunIcon class="w-3 h-3" />{{ item.entry.temperatureCelsius.toFixed(1) }}°C
                    </span>
                    <span
                      v-if="item.entry.distanceSinceLastChargeKm != null || item.entry.odometerKm"
                      class="inline-flex items-center gap-1 px-2 py-0.5 bg-gray-50 dark:bg-gray-700 border border-gray-200 dark:border-gray-600 rounded-full text-xs text-gray-600 dark:text-gray-300 whitespace-nowrap"
                      :class="item.entry.distanceSinceLastChargeKm != null && item.entry.odometerKm
                        ? 'cursor-pointer shadow-[0_4px_0_0_#d1d5db] dark:shadow-[0_4px_0_0_#111827] active:shadow-none active:translate-y-1 transition-all duration-75'
                        : ''"
                      @click.stop="toggleOdometerDisplay(item.entry.distanceSinceLastChargeKm, item.entry.odometerKm)"
                    >
                      <template v-if="item.entry.distanceSinceLastChargeKm != null && !showOdometer">+{{ formatDistance(item.entry.distanceSinceLastChargeKm) }}</template>
                      <template v-else>{{ item.entry.odometerKm != null ? formatDistance(item.entry.odometerKm) : '' }}</template>
                    </span>
                    <!-- Aktions-Menü -->
                    <div class="relative ml-auto">
                      <button @click.stop="openMenuLogId = openMenuLogId === item.entry.id ? null : item.entry.id"
                        class="p-1 rounded text-gray-400 hover:text-gray-600 dark:hover:text-gray-200 hover:bg-gray-100 dark:hover:bg-gray-600 transition">
                        <EllipsisVerticalIcon class="w-5 h-5" />
                      </button>
                      <div v-if="openMenuLogId === item.entry.id"
                        class="absolute right-0 bottom-full mb-1 w-44 bg-white dark:bg-gray-700 rounded-lg shadow-lg border border-gray-200 dark:border-gray-600 z-50 py-1 overflow-hidden">
                        <button @click.stop="editingLog = item.entry; openMenuLogId = null"
                          class="w-full flex items-center gap-2.5 px-3 py-2 text-sm text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-600 transition">
                          <PencilSquareIcon class="w-4 h-4 flex-shrink-0" />{{ t('dashboard.action_edit') }}
                        </button>
                        <button @click.stop="startAddTrip(item.entry.id); openMenuLogId = null"
                          class="w-full flex items-center gap-2.5 px-3 py-2 text-sm text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-600 transition">
                          <MapIcon class="w-4 h-4 flex-shrink-0" />{{ t('dashboard.action_add_trip') }}
                        </button>
                        <button v-if="otherCars.length > 0" @click.stop="openReassignModal(item.entry); openMenuLogId = null"
                          class="w-full flex items-center gap-2.5 px-3 py-2 text-sm text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-600 transition">
                          <ArrowsRightLeftIcon class="w-4 h-4 flex-shrink-0" />{{ t('dashboard.action_reassign') }}
                        </button>
                        <div class="border-t border-gray-100 dark:border-gray-600 mt-1 pt-1">
                          <button @click.stop="deleteLog(item.entry.id); openMenuLogId = null"
                            class="w-full flex items-center gap-2.5 px-3 py-2 text-sm text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20 transition">
                            <TrashIcon class="w-4 h-4 flex-shrink-0" />{{ t('dashboard.action_delete') }}
                          </button>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
                </Transition>
                <!-- Implausibility tooltip panel (normal log only) -->
                <div
                  v-if="!item.entry._isLadegruppe && item.entry.consumptionImplausible && openTooltipLogId === item.entry.id"
                  class="mt-1 p-2.5 rounded-lg bg-amber-50 dark:bg-amber-900/20 border border-amber-200 dark:border-amber-700 text-xs text-amber-800 dark:text-amber-300 space-y-1">
                  <p class="font-medium">{{ t('dashboard.implausible_tooltip_title') }}</p>
                  <p>{{ t('dashboard.implausible_tooltip_desc', { value: formatConsumption(item.entry.consumptionKwhPer100km) }) }}</p>
                  <ul class="list-disc list-inside space-y-0.5 mt-1">
                    <li>{{ t('dashboard.implausible_tooltip_cause1') }}</li>
                    <li>{{ t('dashboard.implausible_tooltip_cause2') }}</li>
                  </ul>
                </div>
                <!-- Short trip tooltip panel -->
                <div
                  v-if="!item.entry._isLadegruppe && item.entry.consumptionKwhPer100km == null && isShortTrip(item.entry) && openTooltipLogId === item.entry.id"
                  class="mt-1 p-2.5 rounded-lg bg-gray-50 dark:bg-gray-800 border border-gray-200 dark:border-gray-700 text-xs text-gray-600 dark:text-gray-300 space-y-1">
                  <p class="font-medium">{{ t('dashboard.short_trip_tooltip_title') }}</p>
                  <p>{{ t('dashboard.short_trip_tooltip_desc') }}</p>
                </div>
              </div>
              <!-- Ladegruppe Sub-Eintraege (collapsible, mobile only) -->
              <template v-if="item.entry._isLadegruppe">
                <Transition name="slide-down">
                  <div v-if="expandedGroups.has(item.entry.id)" class="mt-1 -space-y-px gridfeed:hidden">
                    <div v-for="(topUp, idx) in item.entry._topUps" :key="topUp.id"
                      :class="['ml-4 mr-4 flex flex-col gap-1.5 px-2.5 py-1.5 bg-gray-50 dark:bg-gray-700 border border-blue-200 dark:border-[#1e3a5f]',
                               idx === item.entry._topUps.length - 1 ? 'rounded-b-lg' : '']">
                      <!-- Einzeiler: alles in einer Zeile, bricht auf Mobile sauber um -->
                      <div class="flex items-center gap-x-2">
                        <span class="text-gray-400 text-xs leading-none flex-shrink-0">└</span>
                        <span class="text-xs text-gray-500 dark:text-gray-400 whitespace-nowrap">{{ t('dashboard.top_up') }}</span>
                        <BoltIcon class="w-3.5 h-3.5 text-gray-500 dark:text-gray-400 flex-shrink-0" />
                        <span class="text-xs font-semibold text-gray-600 dark:text-gray-300 whitespace-nowrap">{{ topUp.kwhCharged }} kWh</span>
                        <span class="text-xs text-gray-500 whitespace-nowrap">
                          <template v-if="item.entry._spansMultipleDays">{{ formatLogDate(topUp.loggedAt) }}</template>
                          <template v-else>{{ new Date(topUp.loggedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) }}</template>
                        </span>
                        <span v-if="topUp.chargeDurationMinutes" class="min-[436px]:inline-flex hidden items-center gap-1 text-xs text-gray-500 dark:text-gray-400 whitespace-nowrap">
                          <ClockIcon class="w-3 h-3" />{{ topUp.chargeDurationMinutes }}min
                        </span>
                        <span v-if="topUp.socAfterChargePercent != null" class="min-[436px]:inline-flex hidden items-center gap-1 text-xs text-gray-500 dark:text-gray-400 whitespace-nowrap">
                          <Battery0Icon class="w-3 h-3" />{{ topUp.socAfterChargePercent }}%
                        </span>
                        <div class="relative ml-auto flex-shrink-0">
                          <button @click.stop="openMenuTopUpId = openMenuTopUpId === topUp.id ? null : topUp.id"
                            class="p-1 rounded text-gray-400 hover:text-gray-600 dark:hover:text-gray-200 hover:bg-gray-100 dark:hover:bg-gray-600 transition">
                            <EllipsisVerticalIcon class="w-4 h-4" />
                          </button>
                          <div v-if="openMenuTopUpId === topUp.id"
                            class="absolute right-0 bottom-full mb-1 w-40 bg-white dark:bg-gray-700 rounded-lg shadow-lg border border-gray-200 dark:border-gray-600 z-50 py-1 overflow-hidden">
                            <button @click.stop="editingLog = topUp; openMenuTopUpId = null"
                              class="w-full flex items-center gap-2.5 px-3 py-2 text-sm text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-600 transition">
                              <PencilSquareIcon class="w-4 h-4 flex-shrink-0" />{{ t('dashboard.action_edit') }}
                            </button>
                            <div class="border-t border-gray-100 dark:border-gray-600 mt-1 pt-1">
                              <button @click.stop="deleteLog(topUp.id); openMenuTopUpId = null"
                                class="w-full flex items-center gap-2.5 px-3 py-2 text-sm text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20 transition">
                                <TrashIcon class="w-4 h-4 flex-shrink-0" />{{ t('dashboard.action_delete') }}
                              </button>
                            </div>
                          </div>
                        </div>
                      </div>
                      <div class="max-[436px]:flex min-[436px]:hidden items-center gap-2">
                        <span v-if="topUp.chargeDurationMinutes" class="inline-flex items-center gap-1 text-xs text-gray-400 dark:text-gray-500 whitespace-nowrap">
                          <ClockIcon class="w-3 h-3" />{{ topUp.chargeDurationMinutes }}min
                        </span>
                        <span v-if="topUp.socAfterChargePercent != null" class="inline-flex items-center gap-1 text-xs text-gray-400 dark:text-gray-500 whitespace-nowrap">
                          <Battery0Icon class="w-3 h-3" />{{ topUp.socAfterChargePercent }}%
                        </span>
                      </div>
                    </div>
                  </div>
                </Transition>
              </template>
              </div><!-- end charge -->
              </template><!-- end v-else regular entry -->

              </template><!-- end v-for groupedFeed -->
            </template>
          </div>
          <!-- Pagination -->
          <div class="flex items-center justify-between mt-4">
            <button
              @click="fetchLogsAndScroll(logsPage - 1)"
              :disabled="logsPage === 0"
              class="flex items-center gap-1 px-3 py-2 text-sm rounded-lg border border-gray-200 dark:border-gray-600 dark:text-gray-300 disabled:opacity-40 disabled:cursor-not-allowed hover:bg-gray-50 dark:hover:bg-gray-700 transition">
              <ChevronLeftIcon class="w-4 h-4" />{{ t('dashboard.prev') }}
            </button>
            <button
              @click="fetchLogsAndScroll(logsPage + 1)"
              :disabled="!hasMoreLogs"
              class="flex items-center gap-1 px-3 py-2 text-sm rounded-lg border border-gray-200 dark:border-gray-600 dark:text-gray-300 disabled:opacity-40 disabled:cursor-not-allowed hover:bg-gray-50 dark:hover:bg-gray-700 transition">
              {{ t('dashboard.next') }}<ChevronRightIcon class="w-4 h-4" />
            </button>
          </div>

          <!-- Consumption info accordion (positioned below the list as user reference material) -->
          <ConsumptionInfoBox :min-trips="5" class="mt-6" />
        </div>

        </div>
      </div>
    </Transition>
  </div>

  <EditLogModal
    v-if="editingLog"
    :log="editingLog"
    @close="editingLog = null"
    @saved="() => { editingLog = null; refreshLogsAndGroups() }"
  />

  <ImplausibleLogsModal
    :car-id="selectedCarId"
    :open="showImplausibleModal"
    @close="() => { showImplausibleModal = false; if (implausibleModalDirty) { fetchStatistics(); implausibleModalDirty = false } }"
    @updated="() => { fetchImplausibleCount(); implausibleModalDirty = true }"
  />

  <!-- Fahrzeug-Zuordnung Modal -->
  <Teleport to="body">
    <Transition enter-active-class="transition duration-200 ease-out" enter-from-class="opacity-0" enter-to-class="opacity-100" leave-active-class="transition duration-150 ease-in" leave-from-class="opacity-100" leave-to-class="opacity-0">
      <div v-if="reassignModalEntry" class="fixed inset-0 z-50 flex items-end sm:items-center justify-center p-0 sm:p-4" @click.self="reassignModalEntry = null">
        <div class="absolute inset-0 bg-black/40" @click="reassignModalEntry = null" />
        <div class="relative w-full sm:max-w-sm bg-white dark:bg-gray-800 rounded-t-2xl sm:rounded-2xl shadow-2xl p-6 space-y-5">
          <div>
            <h3 class="text-base font-semibold text-gray-900 dark:text-gray-100">{{ t('dashboard.reassign_car') }}</h3>
            <p class="text-sm text-gray-500 dark:text-gray-400 mt-1">{{ t('dashboard.reassign_car_hint') }}</p>
          </div>

          <div class="space-y-2">
            <button
              v-for="car in otherCars"
              :key="car.id"
              @click="reassignSelectedCarId = car.id; reassignError = null"
              :class="['w-full flex items-center gap-3 p-3 rounded-xl border-2 transition text-left',
                       reassignSelectedCarId === car.id
                         ? 'border-indigo-500 bg-indigo-50 dark:bg-indigo-900/30'
                         : 'border-gray-200 dark:border-gray-600 hover:border-gray-300 dark:hover:border-gray-500']">
              <TruckIcon class="w-5 h-5 flex-shrink-0" :class="reassignSelectedCarId === car.id ? 'text-indigo-600' : 'text-gray-400'" />
              <span class="font-medium text-gray-800 dark:text-gray-200">{{ carDisplayName(car.brand, car.model) }}</span>
              <div v-if="reassignSelectedCarId === car.id" class="ml-auto w-4 h-4 rounded-full bg-indigo-500 flex-shrink-0" />
            </button>
          </div>

          <p v-if="reassignError" class="text-sm text-red-600 dark:text-red-400">{{ reassignError }}</p>

          <div class="flex gap-3 pt-1">
            <button @click="reassignModalEntry = null"
              class="flex-1 px-4 py-2.5 rounded-xl border border-gray-200 dark:border-gray-600 text-sm font-medium text-gray-600 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 transition">
              {{ t('common.cancel') }}
            </button>
            <button @click="doSaveReassign"
              :disabled="!reassignSelectedCarId || reassignSaving"
              class="flex-1 px-4 py-2.5 rounded-xl text-sm font-medium text-white transition disabled:opacity-40"
              :class="reassignSelectedCarId ? 'bg-indigo-600 hover:bg-indigo-700' : 'bg-gray-300 dark:bg-gray-600'">
              {{ reassignSaving ? t('common.saving') : t('common.save') }}
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>

  <!-- Mobile sticky bottom bar: bulk expand/collapse with toggle switches -->
  <Teleport to="body">
    <Transition
      enter-active-class="transition duration-200 ease-out"
      enter-from-class="opacity-0 translate-y-2"
      enter-to-class="opacity-100 translate-y-0"
      leave-active-class="transition duration-150 ease-in"
      leave-from-class="opacity-100 translate-y-0"
      leave-to-class="opacity-0 translate-y-2">
      <div v-if="hasAnyLogs && (totalTripCount > 0 || chargeCount > 0)"
        ref="bulkBar"
        class="md:hidden fixed bottom-0 left-0 right-0 z-30 bg-gray-900/95 dark:bg-gray-800/95 backdrop-blur border-t border-white/10 flex items-center justify-around gap-3 px-4 py-2.5"
        style="padding-bottom: calc(env(safe-area-inset-bottom, 0px) + 10px);">
        <label v-if="totalTripCount > 0" class="flex items-center gap-2 cursor-pointer select-none">
          <span class="text-xs font-medium text-white whitespace-nowrap">
            {{ t('dashboard.bulk_trips_label') }}
          </span>
          <button
            type="button"
            role="switch"
            :aria-checked="allTripsExpanded"
            @click="toggleAllTrips"
            :class="['relative inline-flex h-5 w-9 flex-shrink-0 items-center rounded-full transition-colors duration-150 focus:outline-none focus-visible:ring-2 focus-visible:ring-indigo-400',
                     allTripsExpanded ? 'bg-indigo-500' : 'bg-gray-600 dark:bg-gray-500']">
            <span :class="['inline-block h-3.5 w-3.5 transform rounded-full bg-white transition-transform duration-150 shadow',
                           allTripsExpanded ? 'translate-x-[18px]' : 'translate-x-[3px]']" />
          </button>
        </label>
        <label v-if="chargeCount > 0" class="flex items-center gap-2 cursor-pointer select-none">
          <span class="text-xs font-medium text-white whitespace-nowrap">
            {{ t('dashboard.bulk_charges_label') }}
          </span>
          <button
            type="button"
            role="switch"
            :aria-checked="allChargesExpanded"
            @click="toggleAllCharges"
            :class="['relative inline-flex h-5 w-9 flex-shrink-0 items-center rounded-full transition-colors duration-150 focus:outline-none focus-visible:ring-2 focus-visible:ring-indigo-400',
                     allChargesExpanded ? 'bg-indigo-500' : 'bg-gray-600 dark:bg-gray-500']">
            <span :class="['inline-block h-3.5 w-3.5 transform rounded-full bg-white transition-transform duration-150 shadow',
                           allChargesExpanded ? 'translate-x-[18px]' : 'translate-x-[3px]']" />
          </button>
        </label>
      </div>
    </Transition>
  </Teleport>
</div>
</template>

<style scoped>
@keyframes vehicle-charging-glow {
  0%, 100% {
    box-shadow: 0 0 14px 4px rgba(74, 222, 128, 0.4), 0 0 32px 10px rgba(74, 222, 128, 0.15);
  }
  50% {
    box-shadow: 0 0 24px 10px rgba(34, 197, 94, 0.6), 0 0 56px 20px rgba(34, 197, 94, 0.25);
  }
}

@keyframes vehicle-charging-glow-dark {
  0%, 100% {
    box-shadow: 0 0 18px 6px rgba(74, 222, 128, 0.55), 0 0 40px 14px rgba(74, 222, 128, 0.2);
  }
  50% {
    box-shadow: 0 0 32px 14px rgba(134, 239, 172, 0.75), 0 0 64px 24px rgba(74, 222, 128, 0.35);
  }
}

.vehicle-charging-glow {
  animation: vehicle-charging-glow 1.8s ease-in-out infinite;
}

:global(.dark) .vehicle-charging-glow {
  animation: vehicle-charging-glow-dark 1.8s ease-in-out infinite;
}

.fade-enter-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from {
  opacity: 0;
}

.fade-enter-to {
  opacity: 1;
}

.slide-down-enter-active,
.slide-down-leave-active {
  transition: max-height 0.25s ease, opacity 0.2s ease;
  overflow: hidden;
  max-height: 600px;
}

.slide-down-enter-from,
.slide-down-leave-to {
  max-height: 0;
  opacity: 0;
}

/* Hide horizontal scrollbar on the car selector strip (visible peek + touch
   scroll communicate scrollability; the native bar just clutters the chip). */
.car-scroll-hide { scrollbar-width: none; }
.car-scroll-hide::-webkit-scrollbar { display: none; }

/* Sticky car-header compact mode.
   When the header sticks below the app bar, swap to a slim one-line layout.
   Scoped to <md so the desktop layout (which uses its own two-line block)
   stays untouched even when the multi-car bar is sticky on desktop. */
@media (max-width: 767px) {
  .car-header-compact .compact-hide { display: none !important; }
  .car-header-compact .compact-nowrap {
    flex-wrap: nowrap !important;
    overflow: hidden;
    padding-top: 0 !important;
    padding-bottom: 0 !important;
  }
  .car-header-compact .compact-nowrap > * { flex-shrink: 0; }
  .car-header-compact .compact-shrink-thumb {
    height: 1.75rem !important;
    width: auto !important;
    aspect-ratio: 4 / 3;
    align-self: center !important;
  }
  .car-header-compact .compact-shrink-pad {
    padding-top: 0.125rem !important;
    padding-bottom: 0.125rem !important;
  }
}
</style>
