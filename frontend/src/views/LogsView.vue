<script setup lang="ts">
import { ref, computed, reactive, onMounted, onUnmounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  TruckIcon,
  MapIcon,
  BoltIcon,
  HeartIcon,
  PencilSquareIcon,
  ChevronDownIcon,
  ChevronRightIcon,
  ChevronDoubleDownIcon,
  ChevronDoubleUpIcon,
  TrashIcon,
  ExclamationTriangleIcon,
  InformationCircleIcon,
  XMarkIcon,
  ArrowPathIcon,
  ArrowsRightLeftIcon,
  PlusIcon,
  ChartBarSquareIcon,
  LockClosedIcon,
  CheckIcon,
  LinkIcon,
  CurrencyEuroIcon,
} from '@heroicons/vue/24/outline'
import WattBadge from '../components/shared/WattBadge.vue'
import { useCoinStore } from '../stores/coins'
import { wattPossibleForLog, wattPossiblePerLogMax } from '../utils/wattPreview'
import { purchasesAvailable } from '../utils/iapPolicy'
import { isShortTrip } from '../utils/shortTrip'
import { phantomEurFor, totalPhantomKwh } from '../utils/phantomDrain'
import {
  tripConsumption as tripConsumptionPure,
  tripGroupConsumedKwh as tripGroupConsumedKwhPure,
  tripGroupSocBoundaries as tripGroupSocBoundariesPure,
  tripGroupCostPer100km as tripGroupCostPer100kmPure,
  buildTripCostPerKwhMap,
  isRestBreak,
  groupTripsByDay,
  pauseBeforeTripMinutes,
} from '../utils/tripCalculations'
import api from '../api/axios'
import { distributeProportionally } from '../utils/distributeProportionally'
import { rescaleCostForKwhChange } from '../utils/costRescale'
import PowerCurveModal from '../components/charging/PowerCurveModal.vue'
import { mergeSocSeries, type CurvePoint, type SocPoint } from '../components/charging/powerCurveSeries'
import { formatSocRange } from '../utils/socRange'
import { hasTripMap } from '../utils/tripMap'
import { formatPauseDuration, timeColumn, tripDayLabel } from '../utils/tripTimeFormat'
import { buildPeriodGroups } from '../utils/tripPeriods'
import { collapseAnchorScrollY, isGroupScrolledPast } from '../utils/stickyGroup'
import { normalizeCharge } from '../utils/recentActivity'
import { pricelessDeepLink } from '../utils/pricelessDeepLink'
import { feedHints } from '../utils/feedHints'
import PeriodGroupHeader from '../components/dashboard/PeriodGroupHeader.vue'
import FeedLegend from '../components/dashboard/FeedLegend.vue'
import FeedHintRow from '../components/dashboard/FeedHintRow.vue'
import FeedEventRow from '../components/dashboard/FeedEventRow.vue'
import FeedActionMenu from '../components/dashboard/FeedActionMenu.vue'
import FeedMenuItem from '../components/dashboard/FeedMenuItem.vue'
import ChoiceChip from '../components/shared/ChoiceChip.vue'
import Collapse from '../components/shared/Collapse.vue'
import BottomSheet from '../components/shared/BottomSheet.vue'
import PeriodChargeLine from '../components/dashboard/PeriodChargeLine.vue'
import ComparisonChip from '../components/dashboard/ComparisonChip.vue'
import { FEED_GRID_COLS } from '../components/dashboard/feedGridCols'
import type { CommunityBenchmark } from '../components/dashboard/PeriodGroupHeader.vue'
import { getModelStatsByEnum, type PublicModelStats } from '../api/publicModelService'
import { useCommunityComparison } from '../composables/useCommunityComparison'
import { communityStatsUsable } from '../utils/communityComparison'
import ConsumptionInfoBox from '../components/dashboard/ConsumptionInfoBox.vue'
import DayTripForm from '../components/story/day/DayTripForm.vue'
import TripClimateMarkers from '../components/TripClimateMarkers.vue'
import TripMapPanel from '../components/dashboard/TripMapPanel.vue'
import LicensePlate from '../components/car/LicensePlate.vue'
import RewardSystemUpdateBanner from '../components/shared/RewardSystemUpdateBanner.vue'
import { useAuthStore } from '../stores/auth'
import ImplausibleLogsModal from '../components/dashboard/ImplausibleLogsModal.vue'
import DeletedLogsSheet from '../components/dashboard/DeletedLogsSheet.vue'
import { useDeletedLogs } from '../composables/useDeletedLogs'
import PricelessLogsModal from '../components/dashboard/PricelessLogsModal.vue'
import MergeLogModal from '../components/dashboard/MergeLogModal.vue'
import CarCardDetails from '../components/dashboard/CarCardDetails.vue'
import { FEED_RESOLUTIONS, formatOlderWindow } from '../composables/useFeedWindow'
import { useRoute, useRouter } from 'vue-router'
import { useLocaleFormat } from '../composables/useLocaleFormat'
import { useCarContext } from '../composables/useCarContext'
import { useVehicleCharging } from '../composables/useVehicleCharging'
import { useHaptic } from '../composables/useHaptic'
import { useAnalyticsUpsellTarget } from '../composables/useUpsellTarget'
import { hasFreeDataSource, isAutoSyncUnsupported } from '../composables/useCarAutoSyncProvider'
import { useCountryStore } from '../stores/country'
import { getPricing } from '../config/pricingConfig'
import { carDisplayName } from '../utils/enumLabel'
import { subscriptionService, type SubscriptionTier } from '../api/subscriptionService'
import {
  computeRealCostHint,
  isNettoOnlyCostLog,
  costBasisKwh,
  type RealCostHint,
  type CostHintLog,
} from '../composables/useChargingEfficiency'
import { useDataFeedback, isRateableLogSource } from '../composables/useDataFeedback'
import DataFeedbackButtons from '../components/shared/DataFeedbackButtons.vue'
import DataFeedbackPanel from '../components/shared/DataFeedbackPanel.vue'
import DataFeedbackMenuItems from '../components/shared/DataFeedbackMenuItems.vue'

const { t, locale } = useI18n()
const coinStore = useCoinStore()
coinStore.ensureCatalog()
/** Obergrenze am Banner: N Ladungen mal das Maximum pro Ladung - Betraege kommen aus dem Backend-Katalog. */
const { formatConsumption, formatDistance, distanceUnitLabel, formatCurrency, formatCostPerKwh, formatDecimal, consumptionUnitLabel } = useLocaleFormat()
const { haptic } = useHaptic()
const route = useRoute()
const router = useRouter()

// -- Geteilter Auto-Context (State + Polling liegen im CarContextLayout) --
const {
  selectedCarId, stats, loading, isInitialLoad,
  cars, carImageUrls, wltp,
  implausibleBannerDismissed, teslaStatus, smartcarStatus, implausibleCount,
  dismissImplausibleBanner, fetchImplausibleCount, fetchStatistics,
  pricelessCount, pricelessBannerDismissed, dismissPricelessBanner, fetchPricelessCount,
  setLogsSection, currentOdometerKm,
  logs, logsLoading, editingLog, priceAmendingLog,
  feedResolution, feedNextOlder, loadOlderFeed,
  expandedGroups, toggleLadegruppe, hasAnyLogs, showOdometer,
  openTooltipLogId, reassignModalEntry, reassignSelectedCarId, reassignSaving,
  reassignError, reassignSuccessMessage, deleteError, otherCars, openReassignModal, saveReassign,
  mergeModalEntry, mergeSaving, mergeError, openMergeModal, mergeCandidates, saveMerge,
  refreshLogsAndGroups, deleteLog,
  formatLogDate, formatTripTimeRange, tripTimeParts, toggleOdometerDisplay, sourceInfo, mergedLogFeed,
  editingTripId, addingTripAfterId, tripForm, tripSaving, tripError,
  startEditTrip, cancelTripEdit, saveTripEdit, startAddTrip, saveNewTrip, deleteTripEntry,
  mergeTripEntry,
} = useCarContext()
const wattForAllPriceless = computed(() => wattPossiblePerLogMax(coinStore.catalog) * pricelessCount.value)

// -- Papierkorb: Liste erst laden, wenn der Nutzer das Sheet öffnet --
const deletedLogs = useDeletedLogs(selectedCarId)
const showDeletedSheet = ref(false)
function openDeletedSheet() {
  showDeletedSheet.value = true
  deletedLogs.load()
}

const deletingTripId = ref<string | null>(null)
let _deleteTimer: ReturnType<typeof setTimeout> | null = null
onUnmounted(() => { if (_deleteTimer) clearTimeout(_deleteTimer) })

const grossEditState = ref<Record<string, string>>({})
const grossSaving = ref<Set<string>>(new Set())
const grossError = ref<Record<string, string>>({})

function startGrossEdit(entry: any, event: Event) {
  event.stopPropagation()
  const { [entry.id]: _, ...restErrors } = grossError.value
  grossError.value = restErrors
  grossEditState.value = {
    ...grossEditState.value,
    [entry.id]: entry._totalMissingKwh != null ? String(entry._totalMissingKwh) : '',
  }
}

function cancelGrossEdit(entryId: string, event?: Event) {
  event?.stopPropagation()
  const { [entryId]: _e, ...restErrors } = grossError.value
  grossError.value = restErrors
  const { [entryId]: _s, ...rest } = grossEditState.value
  grossEditState.value = rest
}

// For all fields except kwhCharged/kwhAtVehicle, the backend applies null = keep existing.
// The kWh pair is the only exception: sending one field with the other null actively clears the other.
// This function always sends both kWh values so that quick-edit patches never accidentally clear one.
// Fields omitted entirely (loggedAt, latitude, longitude, etc.) are safe to leave out - null keeps existing.
function patchLog(id: string, existing: any, update: Record<string, unknown>): Promise<any> {
  return api.patch(`/logs/${id}`, {
    kwhCharged:   existing.kwhCharged,
    kwhAtVehicle: existing.kwhAtVehicle,
    ...update,
  })
}

async function applyGrossTotal(entry: any, event?: Event) {
  event?.stopPropagation()
  const total = parseFloat(grossEditState.value[entry.id])
  if (isNaN(total) || total <= 0) return
  const weightField: 'kwhAtVehicle' | 'kwhCharged' = entry._fieldForWeights
  const setField: 'kwhCharged' | 'kwhAtVehicle' = entry._fieldToSet
  const weights = entry._topUps.map((t: any) => parseFloat(t[weightField]) || 0)

  grossSaving.value = new Set([...grossSaving.value, entry.id])
  const { [entry.id]: _e, ...restErrors } = grossError.value
  grossError.value = restErrors
  try {
    const values = distributeProportionally(total, weights)
    await Promise.all(entry._topUps.map((topUp: any, i: number) =>
      patchLog(topUp.id, topUp, {
        [setField]: values[i],
        costEur: rescaleCostForKwhChange(topUp, setField, values[i]),
      })
    ))
    const { [entry.id]: _s, ...rest } = grossEditState.value
    grossEditState.value = rest
  } catch {
    grossError.value = { ...grossError.value, [entry.id]: t('dashboard.ac_gross_error') }
  } finally {
    const ns = new Set(grossSaving.value)
    ns.delete(entry.id)
    grossSaving.value = ns
    await refreshLogsAndGroups()
  }
}

const logEditState = ref<Record<string, string>>({})
const logSaving = ref<Set<string>>(new Set())
const logError = ref<Record<string, string>>({})

function logFieldToSet(log: any): 'kwhCharged' | 'kwhAtVehicle' {
  return (log.kwhAtVehicle != null || log.kwhCharged == null) ? 'kwhCharged' : 'kwhAtVehicle'
}

function startLogEdit(log: any, event: Event) {
  event.stopPropagation()
  const { [log.id]: _e, ...restErrors } = logError.value
  logError.value = restErrors
  const current = logFieldToSet(log) === 'kwhCharged' ? log.kwhCharged : log.kwhAtVehicle
  logEditState.value = { ...logEditState.value, [log.id]: current != null ? String(current) : '' }
}

function cancelLogEdit(logId: string, event?: Event) {
  event?.stopPropagation()
  const { [logId]: _e, ...re } = logError.value
  logError.value = re
  const { [logId]: _s, ...rs } = logEditState.value
  logEditState.value = rs
}

async function applyLogValue(log: any, event?: Event) {
  event?.stopPropagation()
  const value = parseFloat(logEditState.value[log.id])
  if (isNaN(value) || value <= 0) return
  const field = logFieldToSet(log)
  logSaving.value = new Set([...logSaving.value, log.id])
  const { [log.id]: _e, ...re } = logError.value
  logError.value = re
  try {
    await patchLog(log.id, log, { [field]: value, costEur: rescaleCostForKwhChange(log, field, value) })
    const { [log.id]: _s, ...rs } = logEditState.value
    logEditState.value = rs
    await refreshLogsAndGroups()
  } catch {
    logError.value = { ...logError.value, [log.id]: t('dashboard.ac_gross_error') }
  } finally {
    const ns = new Set(logSaving.value)
    ns.delete(log.id)
    logSaving.value = ns
  }
}

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

// Zeitraum-Gruppen (Tag/Woche/Monat, ids "resolution:key") starten zugeklappt - ein Monat
// rendert sonst hunderte Zeilen auf einmal. Aufklappen gilt nur fuer die Sitzung.
const expandedPeriodGroups = ref<Set<string>>(new Set())
const isPeriodGroupId = (groupId: string) => groupId.includes(':')

function isGroupCollapsed(groupId: string): boolean {
  return isPeriodGroupId(groupId)
    ? !expandedPeriodGroups.value.has(groupId)
    : collapsedTripGroups.value.has(groupId)
}

watch(selectedCarId, (newId) => {
  collapsedTripGroups.value = loadCollapsedGroups(newId)
  expandedPeriodGroups.value = new Set()
})

const loadedGroupIds = computed<Set<string>>(() =>
  new Set(['tg_top', ...logs.value.map((l: any) => `tg_${l.id}`)]))

function toggleTripGroup(groupId: string) {
  haptic()
  if (isPeriodGroupId(groupId)) {
    const next = new Set(expandedPeriodGroups.value)
    if (next.has(groupId)) {
      keepCollapsingHeaderInPlace(groupId)
      next.delete(groupId)
    } else next.add(groupId)
    expandedPeriodGroups.value = next
    return
  }
  const next = new Set(collapsedTripGroups.value)
  if (next.has(groupId)) next.delete(groupId)
  else next.add(groupId)
  collapsedTripGroups.value = next
  if (loadedGroupIds.value.has(groupId)) {
    try {
      const toSave = [...next].filter(id => loadedGroupIds.value.has(id))
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

/** Der gewachsene Feed: eine Gruppe je Ladezyklus, weil dazwischen die Bilanz gilt. */
const cycleFeed = computed<any[]>(() => {
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
        // Zweite Ebene: der Ladezyklus traegt die Bilanz, der Tag darin die Orientierung.
        days: [] as any[],
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
      const drainAfterGroup = (nextEntry && !nextEntry._isTrip) ? [nextEntry] : []
      group.totalPhantomKwh = totalPhantomKwh([...group.trips, ...drainAfterGroup])
      // Ladungen sind im Zyklus-Feed eigene Eintraege, der Tag traegt hier nur Fahrten.
      group.days = groupTripsByDay(group.trips).map((day: any) => ({
        ...day,
        events: day.trips.map((trip: any, tripIdx: number) => ({ kind: 'trip', trip, tripIdx })),
      }))
      result.push(group)
    } else {
      result.push({ kind: 'entry', id: entry.id, entry })
      i++
    }
  }
  return result
})

// Aufloesung + Zeitfenster des Feeds leben im CarContext (useFeedWindow); hier nur die Chips.
// Jede Darstellung hat ihr festes Fenster, "Aeltere laden" nennt das naechste Stueck.
const RESOLUTIONS = FEED_RESOLUTIONS
const nextOlderLabel = computed(() => formatOlderWindow(feedNextOlder.value, feedResolution.value, locale.value))

/**
 * Verbrauch und Kosten einer einzelnen Fahrt - dieselben Werte, die auch in ihrer Zeile
 * stehen. Die Zeitraum-Bilanz addiert sie nur; die Formel bleibt hier, damit es weiterhin
 * genau eine gibt.
 */
const periodMeasure = computed(() => ({
  kwhOf: (trip: any) => {
    const consumption = tripConsumption(trip)
    if (!consumption || trip.distanceKm == null) return null
    return (consumption.kwhPer100km * trip.distanceKm) / 100
  },
  costOf: (trip: any) => {
    const per100km = tripCostPer100km(trip)
    if (per100km == null || trip.distanceKm == null) return null
    return (per100km * trip.distanceKm) / 100
  },
  // Ueber normalizeCharge, das den Single-vs-Ladegruppe-Branch kennt: bei einer Ladegruppe
  // muss das Gruppen-Total (_totalKwh) zaehlen, nicht der erste Teilvorgang - sonst
  // verschwindet gemergte Energie aus der Wochen-/Monatssumme.
  chargeKwhOf: (entry: any) => normalizeCharge(entry)?.kwh ?? null,
  // Einzelne Ladevorgaenge zaehlen: eine Ladegruppe buendelt mehrere Teilladungen (_topUps),
  // die Periodenzahl soll sie einzeln fuehren - konsistent zur Statistik-Kachel.
  chargeCountOf: (entry: any) => entry._topUps?.length || 1,
}))

/** Derselbe Feed, nur nach Tag, Woche oder Monat geschnitten statt nach Ladezyklus. */
const periodFeed = computed<any[]>(() => {
  const resolution = feedResolution.value
  if (resolution === 'cycle') return []

  const feed = mergedLogFeed.value
  return buildPeriodGroups(
    feed.filter((entry: any) => entry._isTrip),
    feed.filter((entry: any) => !entry._isTrip),
    resolution,
    periodMeasure.value,
  ).map((group) => ({
    kind: 'tripGroup',
    id: group.id,
    groupId: group.id,
    period: group,
    days: group.days,
    trips: group.trips,
    totalKm: group.totals.km,
    groupSize: group.totals.tripCount,
    // Standverluste des Zeitraums - Drains haengen an Fahrten wie an Ladungen.
    totalPhantomKwh: totalPhantomKwh([...group.trips, ...group.charges]),
  }))
})

const groupedFeed = computed<any[]>(() =>
  feedResolution.value === 'cycle' ? cycleFeed.value : periodFeed.value)

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
const reassignSheet = ref<InstanceType<typeof BottomSheet> | null>(null)
const doSaveMerge = ({ sourceLogId, preferSource }: { sourceLogId: string; preferSource: boolean }) =>
  saveMerge(sourceLogId, preferSource, fetchStatistics)

const expandedLogs      = ref(new Set<string>())

// -- Aufklappbare Fahrtzeile: die Zeile selbst ist der Schalter fuer die Karte.
// Keys je Template-Instanz getrennt (id vs. id + '__d'), sonst mounten Mobile- und
// Desktop-Ansicht dieselbe Leaflet-Karte doppelt.
const expandedTripMaps = ref(new Set<string>())
function toggleTripMap(key: string) {
  const next = new Set(expandedTripMaps.value)
  if (next.has(key)) next.delete(key)
  else next.add(key)
  expandedTripMaps.value = next
}

// ESC und Tippen daneben schliessen ein offenes Aktionsmenue - das regelt FeedActionMenu selbst.

function toggleLogExpanded(id: string) {
  haptic()
  if (expandedLogs.value.has(id)) expandedLogs.value.delete(id)
  else expandedLogs.value.add(id)
}

// Power-Curve: lazy-loaded pro Log-ID, einmal geladen wird's gecached.
// LRU mit Hard-Cap auf POWER_CURVE_CACHE_MAX: bei einem Power-User der hunderte
// Logs aufklappt waere die Map sonst unbounded (~600 Byte pro Eintrag).
const POWER_CURVE_CACHE_MAX = 50
// Die Kurve wird im Overlay gezeigt, nicht inline: in der Feed-Zeile blieb ihr
// nur eine gestauchte Resthoehe. Es ist immer hoechstens eine offen.
const powerCurveEntry = ref<any | null>(null)
/** Was zu einer Ladung aufgezeichnet wurde: Leistungskurve (Tesla) oder
 *  Ladeverlauf (Smartcar). Nie beides - siehe PowerCurveResponse im Backend. */
interface CurveData { points: CurvePoint[]; socPoints: SocPoint[] }
const powerCurveCache = ref(new Map<string, CurveData>())
const powerCurveLoading = ref(new Set<string>())

function cachePut(logId: string, points: CurveData) {
  // LRU-Verhalten via JS-Map-Insertion-Order: bei Hit erst delete dann set
  // schiebt den Eintrag ans Ende; oldest fliegt raus wenn Cap erreicht.
  if (powerCurveCache.value.has(logId)) powerCurveCache.value.delete(logId)
  powerCurveCache.value.set(logId, points)
  while (powerCurveCache.value.size > POWER_CURVE_CACHE_MAX) {
    const oldest = powerCurveCache.value.keys().next().value
    if (oldest === undefined) break
    powerCurveCache.value.delete(oldest)
  }
  powerCurveCache.value = new Map(powerCurveCache.value)
}

/** Anzahl der Teilladungen einer Ladegruppe, die eine Kurve aufgezeichnet haben. */
function curveTopUpCount(entry: any): number {
  return (entry?._topUps ?? []).filter((t: any) => t?.hasPowerCurve || t?.hasSocCurve).length
}

/**
 * Watt-Potenzial fuer den Sammel-Nachtrag einer Ladegruppe: Summe ueber alle Teilladungen,
 * denen der Preis fehlt - der Header-Chip verspricht damit dasselbe, was der Nutzer nach
 * dem Nachtragen aller Teilladungen tatsaechlich bekommt.
 */
function groupPricelessWatt(entry: any): number {
  return (entry?._pricelessSubs ?? []).reduce(
    (sum: number, sub: any) => sum + wattPossibleForLog(coinStore.catalog, sub), 0)
}

/** Nachtrag aus der Gruppe heraus: startet bei der aeltesten Teilladung ohne Preis. */
function openGroupPriceAmend(entry: any) {
  const target = (entry?._pricelessSubs ?? [])[0]
  if (target) priceAmendingLog.value = target
}

/** Erste Teilladung mit Kurve - liefert dem Teaser echte Eckdaten statt leerer Kacheln. */
function firstCurveTopUp(entry: any): any {
  return (entry?._topUps ?? []).find((t: any) => t?.hasPowerCurve || t?.hasSocCurve) ?? entry
}

async function openPowerCurve(entry: any) {
  const logId = entry.id
  powerCurveEntry.value = entry
  // Ohne Freischaltung zeigt das Overlay den Teaser. Der Abruf wuerde am
  // Server-Gate scheitern - gar nicht erst fragen. Der Ladeverlauf hat ein
  // eigenes, weiteres Gate.
  if (!authStore.canViewLiveAnalytics && !(entry?.hasSocCurve && authStore.canViewSocCurve)) return
  if (powerCurveCache.value.has(logId)) {
    // Recency-Touch fuer LRU: re-insert um den Eintrag ans Map-Ende zu schieben
    const cached = powerCurveCache.value.get(logId)!
    cachePut(logId, cached)
    return
  }
  powerCurveLoading.value.add(logId)
  powerCurveLoading.value = new Set(powerCurveLoading.value)
  try {
    // Teilladungen mitladen: der Connector trennt einen Ladelauf am
    // Zaehler-Reset, der Feed fuehrt die Logs ueber den identischen
    // Kilometerstand aber wieder zusammen. Der Ladeverlauf folgt derselben
    // Gruppierung und wird dadurch wieder durchgehend. Die Leistungskurve
    // bleibt beim Elternlog - Tesla kennt diesen Split nicht.
    const fetchCurve = (id: string) =>
      api.get<{ points: CurvePoint[]; socPoints: SocPoint[] }>(`/logs/${id}/power-curve`)
        .then(r => r.data)
        .catch(() => ({ points: [] as CurvePoint[], socPoints: [] as SocPoint[] }))

    const parent = await fetchCurve(logId)
    // Teilladungen nur nachladen, wenn es um einen Ladeverlauf geht: der
    // Connector trennt einen Lauf am Zaehler-Reset, der Feed fuehrt die Logs
    // ueber den identischen Kilometerstand aber wieder zusammen. Bei einer
    // Leistungskurve gibt es diesen Split nicht - dort waeren die zusaetzlichen
    // Requests reine Last.
    const topUpIds = parent.points?.length
      ? []
      : ((entry?._topUps ?? []) as any[]).map(t => t?.id).filter(Boolean)
    const topUps = topUpIds.length ? await Promise.all(topUpIds.map(fetchCurve)) : []

    cachePut(logId, {
      points: parent.points ?? [],
      socPoints: mergeSocSeries([parent, ...topUps].map(r => r?.socPoints)),
    })
  } catch {
    cachePut(logId, { points: [], socPoints: [] })
  } finally {
    powerCurveLoading.value.delete(logId)
    powerCurveLoading.value = new Set(powerCurveLoading.value)
  }
}

// Referenzlinie der Ladekurve: Verbrauch des Logs, sonst der Fahrzeug-Schnitt.
function powerCurveConsumption(entry: any): number | null {
  if (entry?.consumptionKwhPer100km != null) return Number(entry.consumptionKwhPer100km)
  return stats.value?.avgConsumptionKwhPer100km != null ? Number(stats.value.avgConsumptionKwhPer100km) : null
}

// formatLogDate enthaelt bereits die Uhrzeit - kein zweites Mal anhaengen.
const powerCurveSubtitle = computed(() => {
  const at = powerCurveEntry.value?.loggedAt
  return at ? formatLogDate(at) : ''
})

function chargingEfficiency(kwhCharged: number | null, kwhAtVehicle: number | null): number | null {
  if (!kwhCharged || !kwhAtVehicle || kwhCharged <= 0) return null
  return Math.round((kwhAtVehicle / kwhCharged) * 1000) / 10
}

// A log without kwhCharged carries only the netto-side cost (= tariff x kwhAtVehicle).
// We hint the realistic grid-side cost in the expanded card - estimated from the user's
// own measured efficiency at this tariff when available, else from the AC/DC pauschale.
// realCostHintMap memoizes the per-log result so the template can read it 4-6x per row
// without re-filtering+sorting the full logs array each time.
type HintInputLog = CostHintLog & { id: string; loggedAt: string }
const realCostHintMap = computed<Map<string, RealCostHint>>(() => {
  const result = new Map<string, RealCostHint>()
  const all = (logs.value ?? []) as HintInputLog[]
  for (const log of all) {
    if (!log?.id) continue
    const hint = computeRealCostHint(log, all)
    if (hint) result.set(log.id, hint)
  }
  return result
})
function realCostHintFor(logId: string | undefined): RealCostHint | null {
  if (!logId) return null
  return realCostHintMap.value.get(logId) ?? null
}

// Separate state from openTooltipLogId so the implausible/short-trip tooltips
// don't clobber the real-cost tooltip (and vice versa).
const openRealCostTooltipId = ref<string | null>(null)
function toggleRealCostTooltip(logId: string) {
  openRealCostTooltipId.value = openRealCostTooltipId.value === logId ? null : logId
}

// -- "Korrekt erkannt?" für Fahrten und Ladevorgänge: Zustand und Speichern in useDataFeedback --
const dataFeedback = useDataFeedback()
onMounted(() => { dataFeedback.load() })

// -- Implausible logs modal --
const showImplausibleModal = ref(false)
const showPricelessModal = ref(false)
const implausibleModalDirty = ref(false)

// Einstieg aus der Monatsrückblick-Mail (/logs?car=<id>&nachtragen=preis). Wartet, bis die Autos
// geladen sind, und nimmt die Parameter danach aus der URL, damit Neuladen das Modal nicht erneut öffnet.
let pricelessLinkHandled = false
watch(cars, (list) => {
  if (pricelessLinkHandled || !list.length) return
  pricelessLinkHandled = true
  const link = pricelessDeepLink(route.query, list)
  if (!link) return
  if (link.carId) selectedCarId.value = link.carId
  showPricelessModal.value = true
  router.replace({ query: { ...route.query, car: undefined, nachtragen: undefined } })
}, { immediate: true })

// -- Range calculator --

const selectedCar = computed(() =>
  cars.value.find(c => c.id === selectedCarId.value) ?? cars.value[0] ?? null
)

// -- Community-Vergleich: Schnitt der Modellgruppe fuer Chip-Faerbung und Tooltips --
const communityModelStats = ref<PublicModelStats | null>(null)
watch(() => selectedCar.value?.model, async (model) => {
  communityModelStats.value = null
  if (!model) return
  try {
    const stats = await getModelStatsByEnum(model)
    // Unter 5 Fahrern ist der Schnitt ein Einzelwert - dann lieber keine Einordnung (App-CI).
    if (communityStatsUsable(stats)) communityModelStats.value = stats
  } catch { /* Ohne Community-Daten bleiben die Chips neutral. */ }
}, { immediate: true })

const { comparisonLevel: communityLevel, comparisonDeltaPercent, comparisonTooltip } = useCommunityComparison()

/**
 * Eigener Schnitt als Vergleichsbasis der Zeilen-Chips: er enthaelt das eigene Ladeprofil
 * und bestraft nicht den Kontext einer Langstreckenwoche. Die Gruppenkoepfe vergleichen
 * weiter gegen die Community - auf Aggregatebene mitteln sich Kontexte.
 */
const personalCostBenchmark = computed(() => {
  const s = stats.value
  const cpk = s?.avgCostPerKwh != null ? Number(s.avgCostPerKwh) : null
  const cons = s?.avgConsumptionKwhPer100km != null ? Number(s.avgConsumptionKwhPer100km) : null
  if (!cpk) return null
  return { costPerKwh: cpk, costPer100km: cons ? cpk * cons : null }
})

/** Tooltip fuer einen Kosten/100km-Wert gegen den eigenen Schnitt, oder null. */
function costPer100kmTooltip(value: number | null): string | null {
  const avg = personalCostBenchmark.value?.costPer100km
  if (avg == null) return null
  return comparisonTooltip(value, avg, `${formatCurrency(avg)}/100km`, 'self')
}

const communityBenchmark = computed<CommunityBenchmark | null>(() => {
  const s = communityModelStats.value
  if (!s) return null
  return {
    consumptionKwhPer100km: s.avgConsumptionKwhPer100km,
    costPer100km: s.avgCostPerKwh != null && s.avgConsumptionKwhPer100km != null
      ? s.avgCostPerKwh * s.avgConsumptionKwhPer100km
      : null,
  }
})

// -- Ladekarten: einmal geladen, dann je Ladung im Feed als Name aufgeloest --
const chargingProviderNames = ref<Map<string, string>>(new Map())
onMounted(async () => {
  try {
    const res = await api.get<{ id: string; providerName: string; label: string | null }[]>('/users/me/charging-providers')
    chargingProviderNames.value = new Map(res.data.map((p) => [p.id, p.label || p.providerName]))
  } catch { /* Ohne Kartennamen zeigt die Ladezeile nur den Ort. */ }
})

function chargeCardName(entry: any): string | null {
  if (!entry.chargingProviderId) return null
  return chargingProviderNames.value.get(entry.chargingProviderId) ?? null
}

const { isSmartcarCharging, isWallboxCharging } =
  useVehicleCharging(cars, smartcarStatus)

// Daten-Reload bei Auto-Wechsel liegt zentral im CarContextLayout.
const authStore = useAuthStore()
const isAdmin   = computed(() => authStore.isAdmin)

// Ziel aller "schalt die Auswertungen frei"-CTAs (Standverluste, Ladekurven). Tesla-Fahrer
// brauchen nur das Supporter-Pack, alle anderen erst eine Datenquelle - siehe Composable.
const upsellTarget = useAnalyticsUpsellTarget()

// Locked-state: for users without the analytics entitlement, unlock only the most-recent
// phantom marker inline (in its native style, with an upgrade link); all other markers stay
// gated. The id matches either a trip or a standalone entry in the feed.
const teaserPhantomId = computed<string | null>(() => {
  if (authStore.canViewLiveAnalytics) return null
  const first = (mergedLogFeed.value ?? []).find((e: any) => (e?._phantomDrain?.kwh ?? 0) > 0.05)
  return first ? first.id : null
})

// Die EINZIGEN Sichtbarkeits-Regeln fuer Standverluste - Gate nirgendwo im Template
// duplizieren. Einzelner Drain: Supporter-Pack oder der eine Teaser. Summen: nur Supporter.
function showsDrain(entry: any): boolean {
  return !!entry?._phantomDrain && (authStore.canViewLiveAnalytics || entry.id === teaserPhantomId.value)
}
function visiblePhantomTotal(item: any): number | null {
  return authStore.canViewLiveAnalytics ? (item?.totalPhantomKwh ?? null) : null
}

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

// Cost of an idle (phantom) drain, valued at the preceding charge's price (falling
// back to the user's average). Null when no real price is known - then we hide it.
function phantomDrainEur(drain: any): number | null {
  return phantomEurFor(drain, stats.value?.avgCostPerKwh ?? null)
}

/** Standzeit eines Drains in Minuten, fuer die Dauer im Separator - auch ueber Tagesgrenzen. */
function drainPauseMinutes(trip: any): number | null {
  const ms = trip?._phantomDrain?.pauseMs
  return ms != null && ms > 0 ? Math.round(ms / 60000) : null
}

/** Standverlust als "0,06 kWh" im Zahlenformat der Sprache. */
function drainText(drain: any): string {
  return `${formatDecimal(Number(drain?.kwh ?? 0), 2)}\u00a0kWh`
}
/** Anteil des Standverlusts am Akku, " (0,1 %)" - gegen die SoH-adjustierte Kapazitaet. */
function drainShare(drain: any): string {
  const cap = selectedCar.value?.effectiveBatteryCapacityKwh
  return cap ? ` (${formatDecimal(Number(drain?.kwh ?? 0) / cap * 100, 1)} %)` : ''
}

/** Titel einer Fahrt wie im Story-Tagesblatt: "Fahrt 1,9 km". */
function tripTitle(trip: any): string {
  return [t('storyday.trip'), trip.distanceKm != null ? formatDistance(trip.distanceKm, { round: false }) : ''].filter(Boolean).join(' ')
}

/** Unterzeile einer Fahrt: Akku vorher und nachher, Aussentemperatur - nur was gemessen ist. */
function tripSubline(trip: any): string {
  const parts: string[] = []
  // Geschuetzte Leerzeichen: Zahl und Einheit brechen in der schmalen Spalte nicht auseinander.
  if (trip.socStart != null && trip.socEnd != null) parts.push(`${Math.round(Number(trip.socStart))} →\u00a0${Math.round(Number(trip.socEnd))}\u00a0%`)
  if (trip.outsideTempCelsius != null) parts.push(`${formatDecimal(Number(trip.outsideTempCelsius), 0)}\u00a0°C`)
  return parts.join(' · ')
}

/** Weitere Angaben einer Fahrt fuer den aufgeklappten Bereich: Tempo und Streckentyp. */
function tripFacts(trip: any): string {
  const parts: string[] = []
  if (trip.maxSpeedKmh != null) {
    parts.push(t('dashboard.trip_speed_summary', { avg: Math.round(Number(trip.avgSpeedKmh)), max: Math.round(Number(trip.maxSpeedKmh)) }))
  }
  if (trip.routeType) parts.push(t('dashboard.trip_route_' + String(trip.routeType).toLowerCase()))
  return parts.join(' · ')
}

/** Nur fuer Admins: Fahrt-Symbol nach Datenquelle eingefaerbt (Fehlersuche). */
function adminTripTone(trip: any): string {
  if (!isAdmin.value) return ''
  if (trip.dataSource === 'TESLA_LIVE') return 'text-red-500 dark:text-red-400'
  if (trip.dataSource === 'SMARTCAR_LIVE') return 'text-blue-500 dark:text-blue-400'
  return ''
}

/** Zeitspalte einer Ladung in der Ansicht Einzeln: ohne Tagesband steht das Datum oben, die Uhrzeit darunter. */
function logTimeColumn(entry: any): { from: string; to: string } {
  if (!entry?.loggedAt) return { from: '', to: '' }
  const d = new Date(entry.loggedAt)
  if (Number.isNaN(d.getTime())) return { from: '', to: '' }
  const loc = ({ en: 'en-GB', nb: 'nb-NO', sv: 'sv-SE' } as Record<string, string>)[locale.value] ?? 'de-DE'
  return {
    from: d.toLocaleDateString(loc, { day: '2-digit', month: '2-digit' }),
    to: d.toLocaleTimeString(loc, { hour: '2-digit', minute: '2-digit' }),
  }
}

/** Ladeort wie im Zeitraum-Feed: Betreiber, sonst oeffentlich/zu Hause, sonst neutral (V166 dreiwertig). */
function chargePlace(entry: any): string {
  if (entry?.cpoName) return entry.cpoName
  if (entry?.isPublicCharging === true) return t('logs.period.charge_public')
  if (entry?.isPublicCharging === false) return t('logs.period.charge_home')
  return t('logs.period.charge_unknown')
}

/** Geladene Energie einer Ladung: laut Fahrzeug, sonst laut Saeule. */
function logKwh(entry: any): number | null {
  const v = entry?.kwhAtVehicle ?? entry?.kwhCharged
  return v == null ? null : Number(v)
}
/** Bezugsmenge fuer ct/kWh: laut Saeule, sonst laut Fahrzeug (wie bisher in der Preis-Pille). */
function logKwhBasis(entry: any): number | null {
  const v = entry?.kwhCharged ?? entry?.kwhAtVehicle
  return v ? Number(v) : null
}
function logCurveOpen(entry: any): boolean {
  return !!((entry?.hasPowerCurve && authStore.canViewLiveAnalytics) || (entry?.hasSocCurve && authStore.canViewSocCurve))
}
function logHasCurve(entry: any): boolean {
  return logCurveOpen(entry) || (!!(entry?.hasPowerCurve || entry?.hasSocCurve) && purchasesAvailable())
}

/** Verbrauch neutral; amber nur, wenn deutlich ueber dem eigenen Schnitt oder unplausibel (App-CI, nie rot). */
function consumptionValueTone(value: number | null | undefined): string {
  const avg = stats.value?.avgConsumptionKwhPer100km
  if (value == null || !avg) return 'text-gray-900 dark:text-gray-100'
  return value > Number(avg) * 1.15 ? 'text-amber-700 dark:text-amber-400' : 'text-gray-900 dark:text-gray-100'
}
function consumptionToneClass(entry: any): string {
  if (entry?.consumptionImplausible) return 'text-amber-700 dark:text-amber-400'
  if (entry?.consumptionIsEstimated) return 'text-gray-600 dark:text-gray-300'
  return consumptionValueTone(entry?.consumptionKwhPer100km)
}

/** Kleine Eingabe und Knoepfe fuer den Brutto/Netto-Nachtrag direkt in der Zeile. */
const QUICK_INPUT = 'w-24 min-h-9 rounded-lg border-2 border-gray-300 bg-white px-2 text-[15px] text-gray-900 tabular-nums outline-none focus:border-gray-900 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100 dark:focus:border-gray-300'
const QUICK_BTN = 'grid h-9 w-9 place-items-center rounded-full text-gray-600 hover:bg-gray-100 disabled:opacity-40 dark:text-gray-300 dark:hover:bg-white/10'
/** "Preis fehlt": Amber mit Handlung (App-CI). */
const PRICE_CHIP = 'inline-flex min-h-8 items-center gap-1 whitespace-nowrap rounded-full bg-amber-100 px-2.5 text-[12px] font-semibold text-amber-800 transition-colors hover:bg-amber-200 dark:bg-amber-400/15 dark:text-amber-300 dark:hover:bg-amber-400/25'
const CURVE_BTN = 'relative -my-1 grid h-10 w-10 shrink-0 place-items-center rounded-full text-gray-600 transition-colors hover:bg-gray-100 focus:outline-none focus-visible:ring-2 focus-visible:ring-story-accent dark:text-gray-300 dark:hover:bg-white/10'
/** Sekundaerer Knopf mit Text in der Kante der Chips. */
const FEED_TEXT_BTN = 'btn-3d cta-shadow-sm inline-flex min-h-10 items-center gap-1.5 rounded-full border-2 border-edge bg-white px-3.5 text-[14px] font-semibold text-gray-800 transition-colors hover:bg-gray-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-green-600 dark:bg-white/10 dark:text-gray-200 dark:hover:bg-white/15'
/** Erklaerkaesten unter einer Zeile: Amber fuer "pruefen", sonst grau. */
const NOTE_WARN = 'grid gap-1 rounded-xl bg-amber-50 p-3 text-[13px] leading-snug text-amber-900 dark:bg-amber-400/10 dark:text-amber-200'
const NOTE_INFO = 'grid gap-1 rounded-xl bg-gray-50 p-3 text-[13px] leading-snug text-gray-700 dark:bg-white/5 dark:text-gray-300'

/** Symbolkreise der Desktop-Zeilen, kleiner Bruder des Kreises in FeedEventRow. */
const ROW_ICON_TRIP = 'grid h-7 w-7 shrink-0 place-items-center rounded-full border-2 border-gray-900 bg-white dark:border-gray-200 dark:bg-gray-900'
const ROW_ICON_CHARGE = 'grid h-7 w-7 shrink-0 place-items-center rounded-full border-2 border-gray-900 bg-green-600 text-white dark:border-transparent dark:bg-green-500 dark:text-gray-950'
/** Leere Rasterzelle: leiser Bindestrich, damit die Spalten lesbar bleiben. */
const EMPTY_CELL = 'text-gray-300 dark:text-gray-600'

/** Runder Symbol-Knopf der Feed-Aktionen, in der Kante der Chips (App-CI). */
const FEED_ICON_BTN = 'btn-3d cta-shadow-sm grid h-10 w-10 shrink-0 place-items-center rounded-full border-2 border-edge bg-white text-gray-800 transition-colors hover:bg-gray-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-green-600 dark:bg-white/10 dark:text-gray-200 dark:hover:bg-white/15'

function tripCostPer100km(trip: any): number | null {
  const c = tripConsumption(trip)
  if (!c) return null
  const cpk = tripCostPerKwh(trip)
  if (cpk == null) return null
  return c.kwhPer100km * cpk
}

/**
 * €/100km einer Fahrt nur fuers Anzeigen: unter 1 km ist die Hochrechnung Rauschen
 * (0,1 km Rangieren wird zu 27 €/100km). Die Zeitraum-Bilanz nutzt weiter
 * {@link tripCostPer100km}, dort gehen die realen Kosten ein, nicht die Hochrechnung.
 */
const MIN_KM_FOR_PER100KM = 1
function tripCostPer100kmDisplay(trip: any): number | null {
  if (!(trip.distanceKm >= MIN_KM_FOR_PER100KM)) return null
  return tripCostPer100km(trip)
}

function tripGroupCostPer100km(group: any): number | null {
  if (!group?.trips?.length) return null
  return tripGroupCostPer100kmPure(group.trips, tripCpkMap.value, selectedCar.value?.effectiveBatteryCapacityKwh ?? null)
}

/** Beschriftung eines Tagesbandes - "Heute", "Gestern" oder Wochentag mit Datum. */
function dayLabel(dateKey: string): string {
  return tripDayLabel(dateKey, locale.value, new Date())
}

/**
 * Das Datumsband klebt unter allem, was oben bereits fest steht: der Desktop-Nav
 * (--top-nav-h, auf Mobile 0) und - nur bei mehreren Autos - dem ebenfalls stickyen
 * Auto-Selektor. Ohne diesen Offset verschwindet das Band beim Scrollen dahinter.
 */
const carSelectorRef = ref<HTMLElement | null>(null)
const stickyCarSelectorHeight = ref(0)
let carSelectorObserver: ResizeObserver | null = null

function measureCarSelector() {
  // Nur der sticky Zustand (mehrere Autos) belegt dauerhaft Platz; sonst scrollt er weg.
  stickyCarSelectorHeight.value =
    cars.value.length > 1 ? (carSelectorRef.value?.offsetHeight ?? 0) : 0
}

watch(carSelectorRef, (el) => {
  carSelectorObserver?.disconnect()
  carSelectorObserver = el ? new ResizeObserver(measureCarSelector) : null
  if (el && carSelectorObserver) carSelectorObserver.observe(el)
  measureCarSelector()
})
watch(() => cars.value.length, measureCarSelector)
onUnmounted(() => carSelectorObserver?.disconnect())

// --content-top kommt aus App.vue und kennt Nav, Notch, Demo-Banner und den
// Ticker in beiden Zustaenden. Der Auto-Selektor ist LogsView-eigen.
const stickyHeaderStyle = computed(() => ({
  top: `calc(var(--content-top, var(--top-nav-h)) + ${stickyCarSelectorHeight.value}px)`,
  transition: 'top 0.3s ease',
}))

// Gruppenkoepfe sind sticky; die Tagesbaender stapeln sich darunter. Dafuer braucht jedes
// Band die gemessene Hoehe seines Kopfes - die variiert mit Chips-Umbruch und Tagesraster.
const periodHeaderHeights = reactive<Record<string, number>>({})
const periodHeaderEls = new Map<string, HTMLElement>()
const periodHeaderObserver = new ResizeObserver((entries) => {
  for (const entry of entries) {
    const el = entry.target as HTMLElement
    const groupId = el.dataset.groupId
    // Mobil- und Desktop-Template rendern denselben Kopf; das per Breakpoint versteckte
    // Exemplar misst 0 und darf den Wert des sichtbaren nicht ueberschreiben.
    if (groupId && el.offsetHeight > 0) periodHeaderHeights[groupId] = el.offsetHeight
  }
})
function setPeriodHeaderRef(groupId: string, variant: string, el: unknown) {
  const key = `${groupId}:${variant}`
  const prev = periodHeaderEls.get(key)
  if (prev && prev !== el) {
    periodHeaderObserver.unobserve(prev)
    periodHeaderEls.delete(key)
  }
  if (el instanceof HTMLElement && prev !== el) {
    el.dataset.groupId = groupId
    periodHeaderEls.set(key, el)
    periodHeaderObserver.observe(el)
  }
}
onUnmounted(() => periodHeaderObserver.disconnect())

/** Der sichtbare Kopf einer Zeitraum-Gruppe - Mobil- und Desktop-Template rendern je einen. */
function visiblePeriodHeader(groupId: string): HTMLElement | null {
  for (const variant of ['m', 'd']) {
    const el = periodHeaderEls.get(`${groupId}:${variant}`)
    if (el && el.offsetHeight > 0) return el
  }
  return null
}

const stickyTopPx = (el: HTMLElement) => parseFloat(getComputedStyle(el).top) || 0
/** Wo der Kopf ohne Kleben stuende: Gruppenanfang innerhalb des Rahmens. */
const headerRestTop = (group: HTMLElement) => group.getBoundingClientRect().top + group.clientTop

/** Klebt der Kopf, bleibt er beim Zuklappen stehen, statt dass die Gruppe unter dem Finger wegschrumpft. */
function keepCollapsingHeaderInPlace(groupId: string) {
  const header = visiblePeriodHeader(groupId)
  const group = header?.parentElement
  if (!header || !group) return
  const target = collapseAnchorScrollY(window.scrollY, headerRestTop(group), stickyTopPx(header))
  if (target != null) window.scrollTo({ top: target, behavior: 'instant' })
}

// Welcher Zeitraum-Kopf gerade klebt. Er deckt dann die Luecke unter der Ticker-Lasche ab
// (.period-head-stuck) - sonst scrollen links und rechts der Lasche Fahrten sichtbar durch.
// Nur Farbe, kein Layout: der Kopf darf beim Ankleben nicht springen.
const stuckPeriodGroupId = ref<string | null>(null)
let stuckFrame = 0
function updateStuckPeriodHeader() {
  stuckFrame = 0
  let stuck: string | null = null
  if (viewActive.value) {
    let stickyTop: number | null = null
    for (const el of periodHeaderEls.values()) {
      const group = el.parentElement
      if (!group || el.offsetHeight === 0) continue
      stickyTop ??= stickyTopPx(el)
      if (isGroupScrolledPast(headerRestTop(group), stickyTop) && group.getBoundingClientRect().bottom > stickyTop) {
        stuck = el.dataset.groupId ?? null
        break
      }
    }
  }
  stuckPeriodGroupId.value = stuck
}
function scheduleStuckUpdate() {
  if (!stuckFrame) stuckFrame = requestAnimationFrame(updateStuckPeriodHeader)
}
watch([groupedFeed, expandedPeriodGroups], scheduleStuckUpdate, { flush: 'post' })
onMounted(() => {
  window.addEventListener('scroll', scheduleStuckUpdate, { passive: true })
  window.addEventListener('resize', scheduleStuckUpdate, { passive: true })
})
onUnmounted(() => {
  window.removeEventListener('scroll', scheduleStuckUpdate)
  window.removeEventListener('resize', scheduleStuckUpdate)
  if (stuckFrame) cancelAnimationFrame(stuckFrame)
})

function dayBandStyle(groupId: string) {
  return {
    top: `calc(var(--content-top, var(--top-nav-h)) + ${stickyCarSelectorHeight.value + (periodHeaderHeights[groupId] ?? 0)}px)`,
    transition: 'top 0.3s ease',
  }
}

function tripGroupConsumedKwh(group: any): number | null {
  if (!group?.trips?.length) return null
  return tripGroupConsumedKwhPure(group.trips, selectedCar.value?.effectiveBatteryCapacityKwh ?? null)
}

function tripGroupSocBoundaries(group: any): { start: number; end: number } | null {
  if (!group?.trips?.length) return null
  return tripGroupSocBoundariesPure(group.trips)
}


const subscriptionTier = ref<SubscriptionTier | null>(null)

// Ob das ausgewaehlte Auto bereits kostenlos Daten liefert (Tesla/XPeng) - dreht die
// AutoSync- vs. Supporter-Weiche fuer alle drei Banner unten. Kein Auto ausgewaehlt
// zaehlt als "keine freie Quelle", wie zuvor bei den einzelnen Brand-Checks.
const selectedCarHasFreeSource = computed(() =>
  selectedCar.value != null && hasFreeDataSource(selectedCar.value)
)

// Marke ohne AutoSync-Weg (Zeekr, Leapmotor, Lucid, Rivian): ein Abo wuerde ihre Daten nicht
// anfassen, also auch keine AutoSync-Banner. Kein Auto ausgewaehlt zaehlt als "unbekannt".
const selectedCarAutoSyncUnsupported = computed(() =>
  selectedCar.value != null && isAutoSyncUnsupported(selectedCar.value)
)

// AutoSync Live discoverability banner: temporarily disabled (flip flag to re-enable).
// Shown to users without a free data source and without Live, dismissible. Position:
// below ConsumptionInfoBox.
const LIVE_BANNER_ENABLED = false
const LS_LIVE_BANNER_DISMISSED = 'autosync_live_banner_dismissed'
const liveBannerDismissed = ref(localStorage.getItem(LS_LIVE_BANNER_DISMISSED) === 'true')
const showLiveBanner = computed(() =>
  LIVE_BANNER_ENABLED
  && purchasesAvailable()
  && subscriptionTier.value != null
  && subscriptionTier.value !== 'AUTOSYNC_LIVE'
  && !selectedCarHasFreeSource.value
  && !selectedCarAutoSyncUnsupported.value
  && !liveBannerDismissed.value
)
function dismissLiveBanner() {
  liveBannerDismissed.value = true
  localStorage.setItem(LS_LIVE_BANNER_DISMISSED, 'true')
}

// AutoSync discoverability banner: shown to users without a free data source and
// without any subscription (tier NONE), dismissible. Position: below ConsumptionInfoBox.
const countryStore = useCountryStore()
const autoSyncPrice = computed(() => getPricing(countryStore.country).monthly)
const LS_AUTOSYNC_BANNER_DISMISSED = 'autosync_banner_dismissed'
const autoSyncBannerDismissed = ref(localStorage.getItem(LS_AUTOSYNC_BANNER_DISMISSED) === 'true')
const showAutoSyncBanner = computed(() =>
  purchasesAvailable()
  && subscriptionTier.value === 'NONE'
  && !selectedCarHasFreeSource.value
  && !selectedCarAutoSyncUnsupported.value
  && !autoSyncBannerDismissed.value
)
function dismissAutoSyncBanner() {
  autoSyncBannerDismissed.value = true
  localStorage.setItem(LS_AUTOSYNC_BANNER_DISMISSED, 'true')
}

// Supporter-Banner: das Gegenstueck zum AutoSync-Banner darueber, gleicher Slot,
// gleiches Dismiss-Verhalten. Tesla-/XPeng-Fahrer bekommen die Datenerfassung gratis,
// AutoSync hat ihnen also nichts zu verkaufen - die Auswertungsebene schon. Ohne diesen
// Banner erfahren sie vom Pack nur, wenn sie zufaellig auf ein gesperrtes Widget klicken.
const supporterPrice = computed(() => getPricing(countryStore.country).supporterMonthly)
const LS_SUPPORTER_BANNER_DISMISSED = 'supporter_banner_dismissed'
const supporterBannerDismissed = ref(localStorage.getItem(LS_SUPPORTER_BANNER_DISMISSED) === 'true')
const showSupporterBanner = computed(() =>
  purchasesAvailable()
  && subscriptionTier.value === 'NONE'
  && selectedCarHasFreeSource.value
  && !supporterBannerDismissed.value
)
function dismissSupporterBanner() {
  supporterBannerDismissed.value = true
  localStorage.setItem(LS_SUPPORTER_BANNER_DISMISSED, 'true')
}

const LS_COST_TIP_DISMISSED = 'logfeed_cost_reuse_tip_dismissed'
const costTipDismissed = ref(localStorage.getItem(LS_COST_TIP_DISMISSED) === 'true')
const showCostTip = computed(() => !costTipDismissed.value)
function dismissCostTip() {
  costTipDismissed.value = true
  localStorage.setItem(LS_COST_TIP_DISMISSED, 'true')
}

// Eine Hinweis-Karte statt vier Bannern, Reihenfolge und Deckel in utils/feedHints.
const visibleHints = computed(() => feedHints({
  implausible: implausibleCount.value > 0 && !implausibleBannerDismissed.value,
  priceless: pricelessCount.value > 0 && !pricelessBannerDismissed.value,
  live: showLiveBanner.value,
  autosync: showAutoSyncBanner.value,
  supporter: showSupporterBanner.value,
  costTip: showCostTip.value,
}))

onMounted(async () => {
  try {
    const status = await subscriptionService.getStatus()
    subscriptionTier.value = status.tier ?? 'NONE'
  } catch {
    subscriptionTier.value = null
  }
})

/**
 * Dauer der Auf-/Zuklapp-Animation, an die Inhaltshoehe gekoppelt. Eine feste Dauer liest
 * sich bei kleinen Formularen fluessig, wirkt bei meterlangen Wochen-Gruppen aber wie ein
 * Sprung - dieselbe Zeit fuer die zwanzigfache Strecke. Gedeckelt, damit grosse Gruppen
 * nicht traege werden.
 */
function expandDurationMs(heightPx: number): number {
  return Math.round(Math.min(550, Math.max(280, heightPx / 4)))
}
const EXPAND_EASE = 'cubic-bezier(0.22, 1, 0.36, 1)'

function onTripFormEnter(el: Element, done: () => void) {
  const h = el as HTMLElement
  const cs = getComputedStyle(h)
  const targetHeight = h.scrollHeight
  const targetMarginTop = cs.marginTop
  const targetPaddingTop = cs.paddingTop
  const targetPaddingBottom = cs.paddingBottom
  const ms = expandDurationMs(targetHeight)
  h.style.overflow = 'hidden'
  h.style.height = '0'
  h.style.marginTop = '0'
  h.style.paddingTop = '0'
  h.style.paddingBottom = '0'
  h.style.opacity = '0'
  requestAnimationFrame(() => {
    h.style.transition = `height ${ms}ms ${EXPAND_EASE}, margin-top ${ms}ms ${EXPAND_EASE}, padding-top ${ms}ms ${EXPAND_EASE}, padding-bottom ${ms}ms ${EXPAND_EASE}, opacity ${Math.round(ms * 0.8)}ms ease`
    h.style.height = targetHeight + 'px'
    h.style.marginTop = targetMarginTop
    h.style.paddingTop = targetPaddingTop
    h.style.paddingBottom = targetPaddingBottom
    h.style.opacity = '1'
    setTimeout(done, ms)
  })
}
function onTripFormAfterEnter(el: Element) {
  const h = el as HTMLElement
  h.style.cssText = ''
}
function onTripFormLeave(el: Element, done: () => void) {
  const h = el as HTMLElement
  const cs = getComputedStyle(h)
  const ms = expandDurationMs(h.scrollHeight)
  h.style.overflow = 'hidden'
  h.style.height = h.scrollHeight + 'px'
  h.style.marginTop = cs.marginTop
  h.style.paddingTop = cs.paddingTop
  h.style.paddingBottom = cs.paddingBottom
  h.style.opacity = '1'
  requestAnimationFrame(() => {
    h.style.transition = `height ${ms}ms ${EXPAND_EASE}, margin-top ${ms}ms ${EXPAND_EASE}, padding-top ${ms}ms ${EXPAND_EASE}, padding-bottom ${ms}ms ${EXPAND_EASE}, opacity ${Math.round(ms * 0.8)}ms ease`
    h.style.height = '0'
    h.style.marginTop = '0'
    h.style.paddingTop = '0'
    h.style.paddingBottom = '0'
    h.style.opacity = '0'
    setTimeout(done, ms)
  })
}

// -- Klappzustand von Fahrtgruppen und Ladekarten, pro Auto gemerkt --
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
// Standverlust-Zeile der Feed-Legende nur, wenn der Chip auch sichtbar ist (Premium-gegated).
const feedShowPhantom = computed(() => visibleTripGroups.value.some(g => visiblePhantomTotal(g) != null))
// Offener Zustand des Erklaerkastens - Trigger steht in der Aufloesungs-Zeile, Panel darunter.
const feedLegendOpen = ref(false)
try {
  feedLegendOpen.value = localStorage.getItem('feedLegendOpen') === '1'
} catch { /* localStorage kann blockiert sein - Default zu genuegt. */ }
function toggleFeedLegend() {
  feedLegendOpen.value = !feedLegendOpen.value
  try {
    localStorage.setItem('feedLegendOpen', feedLegendOpen.value ? '1' : '0')
  } catch { /* nicht persistierbar - fuer diese Sitzung reicht der Ref. */ }
}


const allTripsExpanded = computed(() =>
  visibleTripGroups.value.length > 0
  && visibleTripGroups.value.every(g => !isGroupCollapsed(g.groupId)),
)
const allChargesExpanded = computed(() =>
  visibleChargeEntries.value.length > 0
  && visibleChargeEntries.value.every(item =>
    item.entry._isLadegruppe ? expandedGroups.value.has(item.entry.id) : expandedLogs.value.has(item.entry.id),
  ),
)

function setAllTripsExpanded(expandAll: boolean) {
  const nextCollapsed = new Set(collapsedTripGroups.value)
  const nextExpanded = new Set(expandedPeriodGroups.value)
  for (const g of visibleTripGroups.value) {
    if (isPeriodGroupId(g.groupId)) {
      if (expandAll) nextExpanded.add(g.groupId)
      else nextExpanded.delete(g.groupId)
    } else {
      if (expandAll) nextCollapsed.delete(g.groupId)
      else nextCollapsed.add(g.groupId)
    }
  }
  collapsedTripGroups.value = nextCollapsed
  expandedPeriodGroups.value = nextExpanded
  if (selectedCarId.value) {
    try {
      const toSave = [...nextCollapsed].filter(id => loadedGroupIds.value.has(id))
      localStorage.setItem(tripCollapseKey(selectedCarId.value), JSON.stringify(toSave))
    } catch {}
  }
}

// Beide Bodies sind im Pager dauerhaft gerendert -> aktiv = die Route zeigt sie.
const viewActive = computed(() => route.name === 'logs')

function setAllChargesExpanded(expand: boolean) {
  const newLogs   = new Set(expandedLogs.value)
  const newGroups = new Set(expandedGroups.value)
  visibleChargeEntries.value.forEach(item => {
    if (item.entry._isLadegruppe) {
      if (expand) newGroups.add(item.entry.id)
      else newGroups.delete(item.entry.id)
    } else {
      if (expand) newLogs.add(item.entry.id)
      else newLogs.delete(item.entry.id)
    }
  })
  expandedLogs.value   = newLogs
  expandedGroups.value = newGroups
}

// "Alle auf/zu" steht in der Steuerzeile und klappt Fahrtgruppen und Ladekarten gemeinsam.
// Vorher eine fixe Leiste am unteren Rand, die seit der Bottom-Nav dahinter verschwunden war.
const allFeedExpanded = computed(() =>
  (visibleTripGroups.value.length > 0 || visibleChargeEntries.value.length > 0)
  && (visibleTripGroups.value.length === 0 || allTripsExpanded.value)
  && (visibleChargeEntries.value.length === 0 || allChargesExpanded.value))

function toggleAllFeed() {
  const expand = !allFeedExpanded.value
  setAllTripsExpanded(expand)
  setAllChargesExpanded(expand)
}

</script>

<template>
<div>
  <div class="md:max-w-6xl md:mx-auto md:px-6 md:pb-6">
    <RewardSystemUpdateBanner class="mb-4" />
    <Transition name="fade" mode="out-in">
      <div v-if="loading && isInitialLoad" key="skeleton-view">
        <div class="bg-white dark:bg-gray-900 md:rounded-3xl md:edge-box px-3 pt-1 pb-6 md:p-6 animate-pulse">
          <div class="mb-6 h-16 rounded-2xl bg-gray-200 dark:bg-gray-700" />
          <div class="space-y-2">
            <div v-for="n in 6" :key="n" class="relative rounded-2xl border-2 border-gray-200 bg-white p-4 dark:border-gray-700 dark:bg-gray-800">
              <div class="flex items-center justify-between gap-2">
                <div class="flex items-center gap-2">
                  <div class="w-4 h-4 rounded bg-gray-200 dark:bg-gray-600" />
                  <div class="w-16 h-4 rounded bg-gray-200 dark:bg-gray-600" />
                  <div class="w-24 h-3 rounded bg-gray-200 dark:bg-gray-600" />
                </div>
                <div class="w-16 h-5 rounded-full bg-gray-200 dark:bg-gray-600" />
              </div>
              <div class="flex gap-2 mt-1.5">
                <div class="w-20 h-3 rounded bg-gray-200 dark:bg-gray-600" />
                <div class="w-16 h-3 rounded bg-gray-200 dark:bg-gray-600" />
                <div class="w-12 h-3 rounded bg-gray-200 dark:bg-gray-600" />
              </div>
            </div>
          </div>
        </div>
      </div>
      <div v-else key="content-view">
        <div class="bg-white dark:bg-gray-900 md:rounded-3xl md:edge-box px-3 pt-1 pb-6 md:p-6">
          <!-- Mobile Auto-Card + Tab-Switch liegen im CarContextLayout (geteilter Header).
               Auf Desktop benennt der aktive Tab die Seite - eine sichtbare Ueberschrift
               wuerde ihn nur wiederholen. Fuer Screenreader bleibt sie. -->
          <h1 class="sr-only">{{ t('logs.title') }}</h1>
          <!-- Desktop-Auto-Selektor (>=768px) -->
          <div
            ref="carSelectorRef"
            v-if="cars.length > 0"
            class="hidden md:block"
            :class="cars.length > 1
              ? 'sticky top-16 z-10 bg-white dark:bg-gray-900 md:-mx-6 md:px-6 md:py-3 mb-3 border-b border-gray-200 dark:border-gray-800'
              : 'mb-6 md:w-fit md:mx-auto'"
          >
            <!-- Car cards -->
            <div class="flex gap-3 overflow-x-auto car-scroll-hide flex-1 pb-1 lg:flex-wrap lg:overflow-x-visible">
              <button
                v-for="car in cars"
                :key="car.id"
                @click="selectedCarId = car.id"
                :class="[
                  cars.length === 1
                    ? 'flex items-start md:items-stretch rounded-2xl text-left transition w-full md:w-auto overflow-hidden'
                    : 'flex items-center md:items-stretch rounded-2xl text-left transition flex-shrink-0 min-w-[180px] max-w-[240px] lg:flex-shrink lg:min-w-0 lg:max-w-none overflow-hidden',
                  // App-CI: das gewaehlte Auto traegt die Kante; ob es laedt, sagt das gruene Abzeichen.
                  selectedCarId === car.id
                    ? 'edge-card bg-white dark:bg-gray-800'
                    : 'border-2 border-gray-200 bg-white hover:border-gray-400 dark:border-gray-700 dark:bg-gray-800/60 dark:hover:border-gray-500',
                ]" style="transition: transform 0.075s ease, box-shadow 0.075s ease;">
                <div
                  :class="[
                    'flex-shrink-0 bg-gray-100 dark:bg-gray-700 flex items-center justify-center overflow-hidden',
                    cars.length === 1
                      ? 'w-24 self-stretch'
                      : 'md:w-24 md:h-auto md:self-stretch'
                  ]">
                  <img
                    v-if="carImageUrls[car.id]"
                    :src="carImageUrls[car.id]"
                    :alt="car.model"
                    class="w-full h-full object-cover" />
                  <TruckIcon v-else class="w-6 h-6 md:w-8 md:h-8 text-gray-400" />
                </div>
                <div class="min-w-0 flex-1 px-3 py-1.5 md:px-4 md:py-3 flex flex-col justify-center">
                  <!-- Desktop: zweizeiliges Layout. Ab md sichtbar - die Kachel passt mit
                       vollem Inhalt auch in den schmalsten Desktop-Viewport; vorher fiel sie
                       unterhalb von lg auf das blosse Bild zusammen. -->
                  <div class="hidden md:block">
                    <div class="flex items-center gap-2 flex-wrap">
                      <span class="font-semibold text-gray-800 dark:text-gray-200">{{ carDisplayName(car) }}</span>
                      <span v-if="car.trim" class="text-sm text-gray-500 dark:text-gray-400">{{ car.trim }}</span>
                      <span v-if="car.isPrimary && cars.length > 1"
                        class="px-1.5 py-0.5 bg-gray-100 dark:bg-white/10 text-gray-700 dark:text-gray-300 text-xs rounded-full font-medium">
                        {{ t('dashboard.active') }}
                      </span>
                      <template v-if="car.brand?.toLowerCase() === 'tesla' && teslaStatus?.connected && (teslaStatus.carId === car.id || teslaStatus.carId === null)">
                        <span v-if="teslaStatus.vehicleState === 'charging'"
                          class="inline-flex items-center gap-1 px-1.5 py-0.5 bg-green-50 dark:bg-green-400/10 text-green-800 dark:text-green-300 text-xs rounded-full font-medium">
                          <span class="w-1.5 h-1.5 rounded-full bg-green-500 dark:bg-green-400 animate-pulse"></span>{{ t('dashboard.tesla_charging') }}
                        </span>
                        <span v-else-if="teslaStatus.vehicleState === 'online'"
                          class="inline-flex items-center gap-1 px-1.5 py-0.5 bg-gray-100 dark:bg-white/10 text-gray-700 dark:text-gray-300 text-xs rounded-full font-medium">
                          <span class="w-1.5 h-1.5 rounded-full bg-gray-900 dark:bg-gray-100"></span>{{ t('dashboard.tesla_online') }}
                        </span>
                        <span v-else-if="teslaStatus.vehicleState === 'asleep'"
                          class="inline-flex items-center gap-1 px-1.5 py-0.5 bg-gray-100 dark:bg-white/10 text-gray-500 dark:text-gray-400 text-xs rounded-full font-medium">
                          <span class="w-1.5 h-1.5 rounded-full bg-gray-400 dark:bg-gray-500"></span>{{ t('dashboard.tesla_sleeping') }}
                        </span>
                      </template>
                      <span v-if="isSmartcarCharging(car)"
                        class="inline-flex items-center gap-1 px-1.5 py-0.5 bg-green-50 dark:bg-green-400/10 text-green-800 dark:text-green-300 text-xs rounded-full font-medium">
                        <span class="w-1.5 h-1.5 rounded-full bg-green-500 dark:bg-green-400 animate-pulse"></span>{{ t('dashboard.smartcar_charging') }}
                      </span>
                      <span v-if="isWallboxCharging()"
                        class="inline-flex items-center gap-1 px-1.5 py-0.5 bg-green-50 dark:bg-green-400/10 text-green-800 dark:text-green-300 text-xs rounded-full font-medium">
                        <span class="w-1.5 h-1.5 rounded-full bg-green-500 dark:bg-green-400 animate-pulse"></span>{{ t('dashboard.wallbox_charging') }}
                      </span>
                    </div>
                    <div class="mt-1.5 flex justify-center">
                      <LicensePlate v-if="car.licensePlate" :plate="car.licensePlate" />
                    </div>
                  </div>
                </div>
                <!-- Desktop horizontal extension (single-car only, ab md) -->
                <div
                  v-if="cars.length === 1 && car.id === selectedCarId"
                  class="hidden md:flex flex-shrink-0 self-stretch items-center border-l border-gray-200 dark:border-gray-700 pl-3 pr-3 py-2"
                >
                  <CarCardDetails :car="car" :wltp="wltp" :current-odometer-km="currentOdometerKm" orientation="horizontal" />
                </div>
              </button>
              </div>
          </div>

        <!-- Log List -->
        <div :ref="setLogsSection" class="pt-3 pb-6 scroll-mt-4">
          <!-- Hinweise als eine Karte statt eines Bannerstapels: erst Aufgaben, dann hoechstens
               ein Angebot, zuletzt der Tipp (utils/feedHints). So beginnt der Feed auf dem Handy
               im ersten Bildschirm. -->
          <ul v-if="visibleHints.length" data-testid="feed-hints"
            class="mb-4 divide-y divide-gray-200 rounded-2xl bg-white edge-card dark:divide-gray-700/70 dark:bg-gray-800">
            <template v-for="hint in visibleHints" :key="hint">
              <FeedHintRow v-if="hint === 'implausible'" data-testid="implausible-banner" task :icon="ExclamationTriangleIcon"
                :dismiss-label="t('dashboard.implausible_dismiss')" @open="showImplausibleModal = true" @dismiss="dismissImplausibleBanner">
                {{ t('dashboard.implausible_banner', { n: implausibleCount, noun: implausibleCount === 1 ? t('dashboard.implausible_entry') : t('dashboard.implausible_entries') }) }}
                <template #sub>{{ t('logs.hints.check') }}</template>
              </FeedHintRow>
              <FeedHintRow v-else-if="hint === 'priceless'" data-testid="priceless-banner" task :icon="CurrencyEuroIcon"
                :dismiss-label="t('priceless.dismiss')" @open="showPricelessModal = true" @dismiss="dismissPricelessBanner">
                {{ t('priceless.banner', pricelessCount) }}
                <template #sub>{{ t('priceless.banner_cta') }} <WattBadge :amount="wattForAllPriceless" up-to /></template>
              </FeedHintRow>
              <FeedHintRow v-else-if="hint === 'live'" :icon="BoltIcon" to="/upgrade"
                :dismiss-label="t('dashboard.live_banner_dismiss')" @dismiss="dismissLiveBanner">
                <i18n-t keypath="logs.hints.live" tag="span"><template #product><b class="font-semibold">AutoSync Live</b></template></i18n-t>
                <template #sub>{{ t('logs.hints.more') }}</template>
              </FeedHintRow>
              <FeedHintRow v-else-if="hint === 'autosync'" data-testid="autosync-banner" :icon="ArrowPathIcon" to="/upgrade"
                :dismiss-label="t('dashboard.autosync_banner_dismiss')" @dismiss="dismissAutoSyncBanner">
                <i18n-t keypath="logs.hints.autosync" tag="span"><template #product><b class="font-semibold">AutoSync</b></template></i18n-t>
                <template #sub>{{ t('logs.hints.per_month', { price: autoSyncPrice }) }} &middot; {{ t('logs.hints.more') }}</template>
              </FeedHintRow>
              <FeedHintRow v-else-if="hint === 'supporter'" data-testid="supporter-banner" :icon="HeartIcon" to="/supporter"
                :dismiss-label="t('dashboard.supporter_banner_dismiss')" @dismiss="dismissSupporterBanner">
                <i18n-t keypath="logs.hints.supporter" tag="span"><template #pack><b class="font-semibold">{{ t('dashboard.supporter_banner_pack') }}</b></template></i18n-t>
                <template #sub>{{ t('logs.hints.per_month', { price: supporterPrice }) }} &middot; {{ t('logs.hints.more') }}</template>
              </FeedHintRow>
              <FeedHintRow v-else-if="hint === 'cost_tip'" plain :icon="InformationCircleIcon"
                :dismiss-label="t('dashboard.cost_reuse_tip_dismiss')" @dismiss="dismissCostTip">
                <span class="text-[13.5px] text-gray-600 dark:text-gray-300">
                  <i18n-t keypath="dashboard.cost_reuse_tip_text" tag="span">
                    <template #settings>
                      <router-link to="/settings" class="font-semibold text-gray-900 underline underline-offset-2 dark:text-gray-100">{{ t('dashboard.cost_reuse_tip_settings') }}</router-link>
                    </template>
                  </i18n-t>
                </span>
              </FeedHintRow>
            </template>
          </ul>

          <Transition enter-active-class="transition duration-200 ease-out" enter-from-class="opacity-0 -translate-y-1" enter-to-class="opacity-100 translate-y-0" leave-active-class="transition duration-150 ease-in" leave-from-class="opacity-100" leave-to-class="opacity-0">
            <div v-if="reassignSuccessMessage" role="status" class="mb-3 flex items-center gap-2 rounded-xl bg-green-50 px-3 py-2.5 text-[14px] font-semibold text-green-800 dark:bg-green-400/10 dark:text-green-300">
              <ArrowsRightLeftIcon class="h-4 w-4 flex-shrink-0" aria-hidden="true" />
              {{ reassignSuccessMessage }}
            </div>
          </Transition>
          <div v-if="deleteError" role="alert" class="mb-3 flex items-center justify-between gap-2 rounded-xl bg-amber-50 px-3 py-2.5 text-[14px] text-amber-900 dark:bg-amber-400/10 dark:text-amber-200">
            {{ deleteError }}
            <button type="button" :aria-label="t('common.close')" @click="deleteError = null" class="grid h-9 w-9 shrink-0 place-items-center rounded-full hover:bg-amber-100 dark:hover:bg-amber-400/20"><XMarkIcon class="h-4 w-4" aria-hidden="true" /></button>
          </div>

          <!-- Aufloesung des Feeds: Ladezyklus wie bisher, oder nach Kalenderzeitraum, jede mit
               festem Fenster. Voll ausgeschriebene Segmente statt eines Menues, zentriert wie die
               Bereichs-Chips - eine Auswahl, die man sieht, muss man nicht suchen. -->
          <div v-if="hasAnyLogs" class="mb-4 flex flex-wrap items-center justify-center gap-x-2 gap-y-2.5">
            <div role="group" :aria-label="t('logs.resolution.label')" class="flex flex-wrap justify-center gap-1.5">
              <ChoiceChip v-for="option in RESOLUTIONS" :key="option" :active="feedResolution === option"
                @click="feedResolution = option">
                {{ t('logs.resolution.' + option) }}
              </ChoiceChip>
            </div>
            <!-- Alles auf- oder zuklappen; die Doppel-Chevrons wiederholen die Chevrons der Koepfe. -->
            <button v-if="totalTripCount > 0 || chargeCount > 0" type="button" data-testid="logfeed-toggle-all"
                    @click="toggleAllFeed"
                    :aria-label="allFeedExpanded ? t('logs.collapse_all') : t('logs.expand_all')"
                    :title="allFeedExpanded ? t('logs.collapse_all') : t('logs.expand_all')"
                    class="btn-3d cta-shadow-sm grid h-10 w-10 shrink-0 place-items-center rounded-full border-2 border-edge bg-white text-gray-800 transition-colors hover:bg-gray-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-green-600 dark:bg-white/10 dark:text-gray-200 dark:hover:bg-white/15">
              <ChevronDoubleUpIcon v-if="allFeedExpanded" class="h-4 w-4" aria-hidden="true" />
              <ChevronDoubleDownIcon v-else class="h-4 w-4" aria-hidden="true" />
            </button>
            <FeedLegend mode="trigger" :has-trips="totalTripCount > 0"
                        :has-charges="chargeCount > 0" :show-phantom="feedShowPhantom"
                        :open="feedLegendOpen" @toggle="toggleFeedLegend" />
          </div>

          <div :class="['space-y-2', { 'opacity-50 pointer-events-none transition-opacity duration-150': logsLoading && hasAnyLogs }]">
            <template v-if="logsLoading && !hasAnyLogs">
              <div v-for="n in 5" :key="n" class="relative rounded-2xl border-2 border-gray-200 bg-white p-4 dark:border-gray-700 dark:bg-gray-800 animate-pulse">
                <div class="flex items-center justify-between gap-2">
                  <div class="flex items-center gap-2">
                    <div class="w-4 h-4 rounded bg-gray-200 dark:bg-gray-600" />
                    <div class="w-16 h-4 rounded bg-gray-200 dark:bg-gray-600" />
                    <div class="w-24 h-3 rounded bg-gray-200 dark:bg-gray-600" />
                  </div>
                  <div class="w-16 h-5 rounded-full bg-gray-200 dark:bg-gray-600" />
                </div>
                <div class="flex gap-2 mt-1.5">
                  <div class="w-20 h-3 rounded bg-gray-200 dark:bg-gray-600" />
                  <div class="w-16 h-3 rounded bg-gray-200 dark:bg-gray-600" />
                  <div class="w-12 h-3 rounded bg-gray-200 dark:bg-gray-600" />
                </div>
              </div>
            </template>
            <!-- Leer: sagen, was hier steht, und die Handlung anbieten (App-CI) -->
            <template v-else-if="!hasAnyLogs">
              <div class="grid justify-items-center gap-3 rounded-2xl bg-gray-50 px-6 py-10 text-center dark:bg-white/5" data-testid="logfeed-empty">
                <p class="max-w-xs text-[15px] font-semibold text-gray-900 dark:text-gray-100">{{ t('logs.empty_title') }}</p>
                <router-link to="/erfassen" class="btn-3d inline-flex min-h-11 items-center gap-1.5 rounded-full border-2 border-edge bg-green-600 px-5 text-[15px] font-semibold text-white hover:bg-green-700">
                  <PlusIcon class="h-4 w-4" aria-hidden="true" />{{ t('logs.empty_cta') }}
                </router-link>
              </div>
            </template>
            <template v-else-if="!logsLoading && mergedLogFeed.length === 0">
              <p class="rounded-2xl bg-gray-50 px-6 py-8 text-center text-[14px] text-gray-600 dark:bg-white/5 dark:text-gray-300">{{ t('logs.empty_window') }}</p>
            </template>
            <template v-else>

              <Collapse :open="feedLegendOpen">
                <FeedLegend mode="panel" :has-trips="totalTripCount > 0"
                            :has-charges="chargeCount > 0" :show-phantom="feedShowPhantom" />
              </Collapse>

              <template v-for="item in groupedFeed" :key="item.id">

              <!-- ===== TRIP GROUP CONTAINER ===== -->
              <template v-if="item.kind === 'tripGroup'">
                <div class="gridfeed:hidden rounded-2xl bg-white edge-card dark:bg-gray-800">

                  <!-- Kopf des Zeitraums - Tag, Woche oder Monat. Nur die Titelzeile klebt; Chips
                       und Tagesraster scrollen mit, sonst belegte der Kopf ein Drittel des Bildschirms. -->
                  <template v-if="item.period">
                    <button type="button" @click="toggleTripGroup(item.groupId)"
                            :aria-expanded="!isGroupCollapsed(item.groupId)"
                            :ref="(el) => setPeriodHeaderRef(item.groupId, 'm', el)" :style="stickyHeaderStyle"
                            :class="['w-full text-left sticky z-[3] rounded-t-2xl bg-white dark:bg-gray-800 transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-story-accent',
                                     { 'period-head-stuck': stuckPeriodGroupId === item.groupId }]">
                      <PeriodGroupHeader part="title" :group="item.period" :expanded="!isGroupCollapsed(item.groupId)" compact />
                    </button>
                    <div @click="toggleTripGroup(item.groupId)"
                         :class="['cursor-pointer', isGroupCollapsed(item.groupId) ? '' : 'border-b border-gray-200 dark:border-gray-700']">
                      <PeriodGroupHeader part="body" :group="item.period" :expanded="!isGroupCollapsed(item.groupId)" compact
                                         :community="communityBenchmark" :phantom-kwh="visiblePhantomTotal(item)" />
                    </div>
                  </template>

                  <!-- Kopf eines Ladezyklus (Ansicht Einzeln): die Fahrten zwischen zwei Ladungen. -->
                  <button v-else type="button" @click="toggleTripGroup(item.groupId)"
                       :aria-expanded="!isGroupCollapsed(item.groupId)"
                       class="flex w-full select-none flex-col gap-1 px-4 py-3 text-left transition-colors hover:bg-gray-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-story-accent dark:hover:bg-white/5"
                       :class="isGroupCollapsed(item.groupId) ? 'rounded-2xl' : 'rounded-t-2xl border-b border-gray-200 dark:border-gray-700'">
                    <span class="flex items-center gap-2">
                      <span class="flex min-w-0 flex-1 flex-wrap items-baseline gap-x-2">
                        <span class="text-[15px] font-bold text-gray-900 dark:text-gray-50">{{ t('dashboard.trip_group_count', { count: item.groupSize }, item.groupSize) }}</span>
                        <span class="text-[12.5px] text-gray-500 tabular-nums dark:text-gray-400">
                          <template v-if="item.totalKm">{{ formatDistance(item.totalKm) }}</template><template v-if="item.totalKm && item.dateRange"> &middot; </template><template v-if="item.dateRange">{{ item.dateRange }}</template>
                        </span>
                      </span>
                      <ChevronDownIcon class="h-4 w-4 shrink-0 text-gray-400 transition-transform duration-300 ease-out"
                        :class="isGroupCollapsed(item.groupId) ? '' : 'rotate-180'" aria-hidden="true" />
                    </span>
                    <!-- Die Bilanz des Ladezyklus - was zwischen zwei Ladungen verbraucht wurde. -->
                    <span v-if="tripGroupConsumedKwh(item) != null || tripGroupSocBoundaries(item) || tripGroupCostPer100km(item) != null || visiblePhantomTotal(item)"
                         class="flex flex-wrap items-center gap-x-3 gap-y-0.5 text-[12.5px] text-gray-600 tabular-nums dark:text-gray-300">
                      <span v-if="tripGroupConsumedKwh(item) != null" class="inline-flex items-center gap-1.5 whitespace-nowrap">
                        <i class="h-2 w-2 rounded-[2px] bg-sky-500 dark:bg-sky-400" aria-hidden="true" />{{ formatDecimal(tripGroupConsumedKwh(item)!, 1) }} kWh {{ t('logs.legend.slim_driven') }}
                      </span>
                      <span v-if="tripGroupSocBoundaries(item)" class="whitespace-nowrap">
                        {{ Math.round(tripGroupSocBoundaries(item)!.start) }} &rarr; {{ Math.round(tripGroupSocBoundaries(item)!.end) }} %
                      </span>
                      <span v-if="tripGroupCostPer100km(item) != null" class="whitespace-nowrap">
                        {{ formatCurrency(tripGroupCostPer100km(item)!) }}/100km
                      </span>
                      <span v-if="visiblePhantomTotal(item)" class="inline-flex items-center gap-1.5 whitespace-nowrap">
                        <i class="h-2 w-2 rounded-[2px] bg-amber-400" aria-hidden="true" />{{ formatDecimal(item.totalPhantomKwh, 1) }} kWh {{ t('logs.legend.slim_phantom') }}
                      </span>
                    </span>
                  </button>

                  <!-- Trips (expanded, animated) -->
                  <Transition :css="false" @enter="onTripFormEnter" @after-enter="onTripFormAfterEnter" @leave="onTripFormLeave">
                  <div v-if="!isGroupCollapsed(item.groupId)">
                    <template v-for="day in item.days" :key="day.dateKey">
                    <!-- Datumsband: bleibt beim Scrollen stehen, damit der Tag waehrend des
                         Lesens sichtbar bleibt. Nur exakt gezaehlte Werte, keine Schaetzung. -->
                    <div v-if="item.days.length > 1" :style="dayBandStyle(item.groupId)" class="sticky z-[2] flex items-center justify-between gap-2 px-4 pb-1.5 pt-3
                                bg-white dark:bg-gray-800 border-t border-gray-200 dark:border-gray-700">
                      <span class="text-[13px] font-bold text-gray-900 dark:text-gray-100">{{ dayLabel(day.dateKey) }}</span>
                      <span class="text-[12px] text-gray-500 dark:text-gray-400 tabular-nums">
                        {{ t('dashboard.trip_group_count', { count: day.tripCount }, day.tripCount) }}<template v-if="day.km"> &middot; {{ formatDistance(day.km) }}</template>
                      </span>
                    </div>
                    <!-- Ereignisse des Tages: Fahrten und Ladungen chronologisch gemischt, neueste zuerst. -->
                    <template v-for="ev in day.events" :key="ev.kind === 'charge' ? 'pc_' + ev.charge.id : ev.trip.id">
                    <PeriodChargeLine v-if="ev.kind === 'charge'" :entry="ev.charge"
                                      :card-name="chargeCardName(ev.charge)" @edit="editingLog = $event"
                                      @power-curve="openPowerCurve" />
                    <template v-else>
                    <template v-for="{ trip, tripIdx } in [ev]" :key="trip.id">

                      <!-- Add-trip form triggered from this trip -->
                      <Transition :css="false" @enter="onTripFormEnter" @after-enter="onTripFormAfterEnter" @leave="onTripFormLeave">
                      <div v-if="addingTripAfterId === trip.id"
                           class="px-4 py-3 bg-gray-50 dark:bg-gray-900/40 border-t border-gray-100 dark:border-gray-700/70">
                        <DayTripForm v-model="tripForm" mode="add" :title="t('dashboard.trip_add')"
                          :error="tripError" :saving="tripSaving" :distance-unit="distanceUnitLabel()"
                          @save="saveNewTrip()" @cancel="cancelTripEdit()" />
                      </div>
                      </Transition>

                      <!-- Fahrt als Zeile wie im Story-Tagesblatt: Tippen klappt Karte und Aktionen auf. -->
                      <Transition :css="false" @enter="onTripFormEnter" @after-enter="onTripFormAfterEnter" @leave="onTripFormLeave">
                      <div v-if="editingTripId !== trip.id && deletingTripId !== trip.id" class="border-t border-gray-100 dark:border-gray-700/70">
                        <FeedEventRow kind="trip" v-bind="timeColumn(trip.tripStartedAt, trip.tripEndedAt, locale)" interactive
                          :expanded="expandedTripMaps.has(trip.id)" :controls="'trip-details-' + trip.id"
                          :icon-tone="adminTripTone(trip)" @activate="toggleTripMap(trip.id)">
                          <template #title>
                            <span>{{ tripTitle(trip) }}</span>
                            <span v-if="trip.dataSource === 'USER_CREATED'"
                              class="rounded-full bg-gray-100 px-1.5 text-[11.5px] font-semibold text-gray-600 dark:bg-white/10 dark:text-gray-300">{{ t('dashboard.trip_manual') }}</span>
                          </template>
                          <template v-if="tripSubline(trip)">{{ tripSubline(trip) }}</template>
                          <template #extra>
                            <TripClimateMarkers :climate="trip.climate" align="start" class="mt-1" />
                          </template>
                          <template v-if="tripConsumption(trip)" #right>
                            <b class="block text-[15px] font-bold text-gray-900 dark:text-gray-50">{{ tripConsumption(trip)!.estimated ? '~' : '' }}{{ formatConsumption(tripConsumption(trip)!.kwhPer100km, { showUnit: false }) }}</b>
                            <span class="block text-[10.5px] font-semibold text-gray-500 dark:text-gray-400">{{ consumptionUnitLabel() }}</span>
                          </template>
                        </FeedEventRow>
                        <Collapse :open="expandedTripMaps.has(trip.id)">
                          <div :id="'trip-details-' + trip.id" class="px-3 pb-3 sm:px-4">
                            <p v-if="tripFacts(trip)" class="pl-[96px] sm:pl-[104px] text-[13px] text-gray-600 tabular-nums dark:text-gray-300">{{ tripFacts(trip) }}</p>
                            <TripMapPanel v-if="hasTripMap(trip)" :trip="trip" :panel-id="'trip-map-' + trip.id" />
                            <!-- Aktionen der Fahrt: Bewerten links, Nachtragen, Bearbeiten, Loeschen rechts. -->
                            <div class="flex items-center gap-2 pt-3">
                              <DataFeedbackButtons v-if="trip.dataSource !== 'USER_CREATED'" subject-type="TRIP" :subject-id="trip.id" />
                              <span class="flex-1" />
                              <button type="button" @click="startAddTrip(trip.id, trip.tripStartedAt)"
                                :aria-label="t('dashboard.action_add_trip')" :title="t('dashboard.action_add_trip')" :class="FEED_ICON_BTN">
                                <PlusIcon class="h-4 w-4" aria-hidden="true" />
                              </button>
                              <button type="button" @click="startEditTrip(trip)"
                                :aria-label="t('dashboard.trip_edit')" :title="t('dashboard.trip_edit')" :class="FEED_ICON_BTN">
                                <PencilSquareIcon class="h-4 w-4" aria-hidden="true" />
                              </button>
                              <button type="button" @click="handleDeleteTrip(trip.id)"
                                :aria-label="t('dashboard.action_delete')" :title="t('dashboard.action_delete')" :class="FEED_ICON_BTN">
                                <TrashIcon class="h-4 w-4" aria-hidden="true" />
                              </button>
                            </div>
                          </div>
                        </Collapse>
                        <DataFeedbackPanel subject-type="TRIP" :subject-id="trip.id" class="mx-4 mb-3" />
                      </div>
                      </Transition>

                  <!-- Trip inline edit mode -->
                  <Transition :css="false" @enter="onTripFormEnter" @after-enter="onTripFormAfterEnter" @leave="onTripFormLeave">
                  <div v-if="editingTripId === trip.id"
                       class="px-4 py-3 bg-gray-50 dark:bg-gray-900/40 border-t border-gray-100 dark:border-gray-700/70">
                  <DayTripForm v-model="tripForm" mode="edit" :title="t('dashboard.trip_edit')"
                    :error="tripError" :saving="tripSaving" :distance-unit="distanceUnitLabel()"
                    @save="saveTripEdit(trip.id)" @cancel="cancelTripEditFull()">
                    <template #extra>
                  <!-- Bewerten und Zusammenfuehren. type="button": das Formular darf nicht absenden. -->
                  <div v-if="trip.dataSource !== 'USER_CREATED' || (previousTripMap[trip.id] && mergePreviewForTripId !== trip.id)"
                       class="flex flex-wrap items-center justify-between gap-2 border-t border-gray-200 pt-3 dark:border-gray-700">
                    <div v-if="trip.dataSource !== 'USER_CREATED'" class="flex items-center gap-1">
                      <span class="text-[13px] text-gray-500 dark:text-gray-400">{{ t('dashboard.data_feedback_label') }}</span>
                      <DataFeedbackButtons subject-type="TRIP" :subject-id="trip.id" />
                    </div>
                    <button v-if="previousTripMap[trip.id] && mergePreviewForTripId !== trip.id" type="button"
                      @click="mergePreviewForTripId = trip.id"
                      class="ml-auto inline-flex min-h-10 items-center gap-1.5 rounded-full px-2 text-[13px] font-semibold text-gray-900 underline decoration-gray-300 underline-offset-4 hover:decoration-gray-900 dark:text-gray-100 dark:decoration-gray-600">
                      <ArrowsRightLeftIcon class="h-4 w-4" aria-hidden="true" />
                      {{ t('dashboard.trip_merge_button') }}
                    </button>
                  </div>
                  <DataFeedbackPanel v-if="trip.dataSource !== 'USER_CREATED'" subject-type="TRIP" :subject-id="trip.id" />
                  <!-- Vorschau des Zusammenfuehrens mit der Fahrt davor -->
                  <template v-if="previousTripMap[trip.id]">
                    <div v-if="mergePreviewForTripId === trip.id" class="grid gap-2 rounded-xl border-2 border-gray-900 p-3 dark:border-gray-700">
                      <p class="text-[14px] font-semibold text-gray-900 dark:text-gray-100">{{ t('dashboard.trip_merge_title') }}</p>
                      <dl class="grid gap-1 text-[13px] tabular-nums text-gray-600 dark:text-gray-300">
                        <div class="flex justify-between gap-2">
                          <dt class="shrink-0">{{ t('dashboard.trip_merge_previous') }}</dt>
                          <dd class="text-right">{{ formatTripTimeRange(previousTripMap[trip.id].tripStartedAt, previousTripMap[trip.id].tripEndedAt) }}, {{ previousTripMap[trip.id].distanceKm ? formatDistance(previousTripMap[trip.id].distanceKm, { round: false }) : '?' }}</dd>
                        </div>
                        <div class="flex justify-between gap-2">
                          <dt class="shrink-0">{{ t('dashboard.trip_merge_this') }}</dt>
                          <dd class="text-right">{{ formatTripTimeRange(trip.tripStartedAt, trip.tripEndedAt) }}, {{ trip.distanceKm ? formatDistance(trip.distanceKm, { round: false }) : '?' }}</dd>
                        </div>
                        <div class="flex justify-between gap-2 border-t border-gray-200 pt-1 font-semibold text-gray-900 dark:border-gray-700 dark:text-gray-100">
                          <dt class="shrink-0">{{ t('dashboard.trip_merge_result') }}</dt>
                          <dd class="text-right">{{ formatTripTimeRange(previousTripMap[trip.id].tripStartedAt, trip.tripEndedAt) }}, ~{{ formatDistance((previousTripMap[trip.id].distanceKm || 0) + (trip.distanceKm || 0), { round: false }) }}</dd>
                        </div>
                      </dl>
                      <p v-if="tripMergeError" role="alert" class="rounded-xl bg-amber-50 px-3 py-2 text-[13px] text-amber-900 dark:bg-amber-400/10 dark:text-amber-200">{{ tripMergeError }}</p>
                      <div class="flex gap-2">
                        <button type="button" @click="mergePreviewForTripId = null"
                          class="min-h-11 flex-1 rounded-xl px-3 text-[14px] font-semibold text-gray-600 dark:text-gray-300">
                          {{ t('dashboard.trip_cancel') }}
                        </button>
                        <button type="button" @click="doMergeTrip(trip.id, previousTripMap[trip.id].id)" :disabled="tripMerging"
                          class="btn-3d min-h-11 flex-1 rounded-xl border-2 border-edge bg-gray-900 px-3 text-[14px] font-semibold text-white disabled:opacity-40 dark:bg-gray-100 dark:text-gray-900">
                          {{ t('dashboard.trip_merge_confirm') }}
                        </button>
                      </div>
                    </div>
                  </template>
                    </template>
                  </DayTripForm>
                  </div>
                  </Transition>


                      <!-- Was zwischen dieser und der naechstfrueheren Fahrt liegt, steht
                           unter ihr - dort, wo die Liste (neueste zuerst) die Luecke zeigt.
                           Erst die Ruhe, dann was sie gekostet hat; ohne ausgewiesenen
                           Standverlust bleibt die Dauer allein stehen. -->
                      <div v-if="isRestBreak(day.trips, tripIdx as number) && !showsDrain(trip)"
                           class="pb-2 pl-[66px] pr-3 text-[12px] text-gray-400 tabular-nums sm:pl-[72px] sm:pr-4 dark:text-gray-500">
                        {{ t('dashboard.trip_pause', { duration: formatPauseDuration(pauseBeforeTripMinutes(day.trips, tripIdx as number)) }) }}
                      </div>

                      <!-- Standzeit mit Verlust zwischen zwei Fahrten: Amber wie der Standverlust im Kopf. -->
                      <div v-if="showsDrain(trip)"
                           class="pb-2 pl-[66px] pr-3 text-[12px] tabular-nums text-gray-500 sm:pl-[72px] sm:pr-4 dark:text-gray-400">
                        <span>
                          <i class="mr-1.5 inline-block h-2 w-2 rounded-[2px] bg-amber-400 align-middle" aria-hidden="true" />
                          <template v-if="drainPauseMinutes(trip)">{{ formatPauseDuration(drainPauseMinutes(trip)) }} &middot; </template>
                          <b class="font-semibold text-gray-700 dark:text-gray-200">{{ drainText(trip._phantomDrain) }}</b><span class="hidden sm:inline">{{ drainShare(trip._phantomDrain) }}</span>
                          {{ t('dashboard.phantom_drain_word') }}
                          <template v-if="phantomDrainEur(trip._phantomDrain) != null"> &middot; <span class="whitespace-nowrap">≈ {{ formatCurrency(phantomDrainEur(trip._phantomDrain)!) }}</span></template>
                        </span>
                        <router-link v-if="!authStore.canViewLiveAnalytics && purchasesAvailable()" :to="upsellTarget" class="ml-1.5 font-semibold text-gray-900 underline underline-offset-2 dark:text-gray-100">{{ t('dashboard.phantom_teaser_unlock') }}</router-link>
                      </div>

                    </template>
                    </template><!-- end v-else trip -->
                    </template><!-- end v-for events -->
                    </template><!-- end v-for days -->
                  </div><!-- end expanded trips -->
                  </Transition>
                </div><!-- end trip group container (mobile) -->

                <!-- DESKTOP TRIP GROUP: dasselbe Spaltenraster fuer alle Zeilen, neutral wie die Story (App-CI). -->
                <div class="hidden gridfeed:block rounded-2xl bg-white edge-card dark:bg-gray-800">
                  <!-- Kopf des Zeitraums - Tag, Woche oder Monat. Deckend, weil unter dem sticky Kopf Zeilen durchscrollen. -->
                  <button v-if="item.period" type="button" @click="toggleTripGroup(item.groupId)"
                    :aria-expanded="!isGroupCollapsed(item.groupId)"
                    :ref="(el) => setPeriodHeaderRef(item.groupId, 'd', el)" :style="stickyHeaderStyle"
                    :class="[{ 'period-head-stuck': stuckPeriodGroupId === item.groupId },
                             isGroupCollapsed(item.groupId) ? 'rounded-2xl' : 'rounded-t-2xl border-b border-gray-200 dark:border-gray-700']"
                    class="w-full text-left sticky z-[3] bg-white dark:bg-gray-800 hover:bg-gray-50 dark:hover:bg-gray-700/60 transition focus:outline-none focus-visible:ring-2 focus-visible:ring-story-accent focus-visible:ring-inset">
                    <PeriodGroupHeader :group="item.period" :expanded="!isGroupCollapsed(item.groupId)"
                                       :community="communityBenchmark" :phantom-kwh="visiblePhantomTotal(item)" />
                  </button>

                  <!-- Kopf eines Ladezyklus (Einzeln) als Rasterzeile -->
                  <button v-else type="button" @click="toggleTripGroup(item.groupId)"
                    :aria-expanded="!isGroupCollapsed(item.groupId)"
                    :aria-label="t('dashboard.trip_group_count', { count: item.groupSize }, item.groupSize)"
                    :class="[FEED_GRID_COLS, 'w-full items-center px-4 py-2.5 text-left text-[13px] tabular-nums transition hover:bg-gray-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-story-accent dark:hover:bg-white/5',
                             isGroupCollapsed(item.groupId) ? 'rounded-2xl' : 'rounded-t-2xl border-b border-gray-200 dark:border-gray-700']">
                    <div class="flex items-center">
                      <span :class="ROW_ICON_TRIP"><MapIcon class="h-3.5 w-3.5" aria-hidden="true" /></span>
                    </div>
                    <div class="whitespace-nowrap font-semibold text-gray-900 dark:text-gray-100">
                      <template v-if="tripGroupConsumedKwh(item) != null">&minus;{{ formatDecimal(tripGroupConsumedKwh(item)!, 2) }} kWh</template>
                      <span v-else :class="EMPTY_CELL">-</span>
                    </div>
                    <div class="truncate whitespace-nowrap text-gray-700 dark:text-gray-300">
                      {{ t('dashboard.trip_group_count', { count: item.groupSize }, item.groupSize) }}<template v-if="item.dateRange"> &middot; {{ item.dateRange }}</template>
                    </div>
                    <div class="whitespace-nowrap">
                      <span v-if="item.totalPhantomKwh && authStore.canViewLiveAnalytics" class="inline-flex items-center gap-1 text-gray-600 dark:text-gray-300"
                        :title="t('dashboard.phantom_drain_word')">
                        <i class="h-2 w-2 rounded-[2px] bg-amber-400" aria-hidden="true" />{{ formatDecimal(item.totalPhantomKwh, 1) }} kWh
                      </span>
                      <span v-else :class="EMPTY_CELL">-</span>
                    </div>
                    <div class="whitespace-nowrap text-gray-600 dark:text-gray-300">
                      <template v-if="tripGroupSocBoundaries(item)">{{ Math.round(tripGroupSocBoundaries(item)!.start) }} → {{ Math.round(tripGroupSocBoundaries(item)!.end) }} %</template>
                      <span v-else :class="EMPTY_CELL">-</span>
                    </div>
                    <div class="text-center text-[11px] font-semibold uppercase tracking-[0.06em] text-gray-400 dark:text-gray-500">{{ t('dashboard.trip_speed_header') }}</div>
                    <div class="whitespace-nowrap font-semibold text-gray-900 dark:text-gray-100">
                      <template v-if="item.totalKm">+{{ formatDistance(item.totalKm) }}</template>
                      <span v-else :class="EMPTY_CELL">-</span>
                    </div>
                    <div :class="EMPTY_CELL" class="text-center">-</div>
                    <div class="flex justify-end whitespace-nowrap font-semibold text-gray-900 dark:text-gray-100">
                      <template v-if="tripGroupCostPer100km(item) != null">{{ formatCurrency(tripGroupCostPer100km(item)!) }}/100km</template>
                      <span v-else :class="EMPTY_CELL">-</span>
                    </div>
                    <div class="flex justify-end">
                      <ChevronDownIcon class="h-4 w-4 text-gray-400 transition-transform duration-300 ease-out"
                        :class="isGroupCollapsed(item.groupId) ? '' : 'rotate-180'" aria-hidden="true" />
                    </div>
                  </button>

                  <!-- Fahrten und Ladungen im Raster -->
                  <Transition :css="false" @enter="onTripFormEnter" @after-enter="onTripFormAfterEnter" @leave="onTripFormLeave">
                  <div v-if="!isGroupCollapsed(item.groupId)">
                    <template v-for="day in item.days" :key="day.dateKey + '__d'">
                    <!-- Datumsband, siehe Mobile-Ansicht. -->
                    <div v-if="item.days.length > 1" :style="dayBandStyle(item.groupId)" class="sticky z-[2] flex items-center justify-between gap-3 px-4 pb-1.5 pt-3
                                bg-white dark:bg-gray-800 border-t border-gray-200 dark:border-gray-700">
                      <span class="text-[13px] font-bold text-gray-900 dark:text-gray-100">{{ dayLabel(day.dateKey) }}</span>
                      <span class="text-[12px] text-gray-500 dark:text-gray-400 tabular-nums">
                        {{ t('dashboard.trip_group_count', { count: day.tripCount }, day.tripCount) }}<template v-if="day.km"> &middot; {{ formatDistance(day.km) }}</template>
                      </span>
                    </div>
                    <!-- Ereignisse des Tages chronologisch gemischt, siehe Mobile-Ansicht. -->
                    <template v-for="ev in day.events" :key="ev.kind === 'charge' ? 'pcd_' + ev.charge.id : ev.trip.id + '__d'">
                    <PeriodChargeLine v-if="ev.kind === 'charge'" :entry="ev.charge"
                                      layout="row" :own-avg-cost-per-kwh="personalCostBenchmark?.costPerKwh"
                                      :card-name="chargeCardName(ev.charge)" @edit="editingLog = $event"
                                      @power-curve="openPowerCurve" />
                    <template v-else>
                    <template v-for="{ trip, tripIdx } in [ev]" :key="trip.id + '__d'">
                      <Transition :css="false" @enter="onTripFormEnter" @after-enter="onTripFormAfterEnter" @leave="onTripFormLeave">
                      <div v-if="addingTripAfterId === trip.id" class="border-t border-gray-100 bg-gray-50 px-4 py-4 dark:border-gray-700/70 dark:bg-gray-900/40">
                        <DayTripForm v-model="tripForm" mode="add" :title="t('dashboard.trip_add')"
                          :error="tripError" :saving="tripSaving" :distance-unit="distanceUnitLabel()"
                          @save="saveNewTrip()" @cancel="cancelTripEdit()" />
                      </div>
                      </Transition>
                      <!-- Zeile der Fahrt - Klick klappt die Karte darunter auf. -->
                      <div v-if="editingTripId !== trip.id && deletingTripId !== trip.id"
                        :class="[FEED_GRID_COLS, 'items-center border-t border-gray-100 px-4 py-2 text-[13px] tabular-nums transition dark:border-gray-700/70',
                                 hasTripMap(trip) ? 'cursor-pointer hover:bg-gray-50 dark:hover:bg-white/5' : '']"
                        :role="hasTripMap(trip) ? 'button' : undefined"
                        :tabindex="hasTripMap(trip) ? 0 : undefined"
                        :aria-expanded="hasTripMap(trip) ? expandedTripMaps.has(trip.id + '__d') : undefined"
                        :aria-controls="hasTripMap(trip) ? 'trip-map-' + trip.id + '__d' : undefined"
                        @click="hasTripMap(trip) && toggleTripMap(trip.id + '__d')"
                        @keydown.enter.self.prevent="hasTripMap(trip) && toggleTripMap(trip.id + '__d')"
                        @keydown.space.self.prevent="hasTripMap(trip) && toggleTripMap(trip.id + '__d')">
                        <div class="flex items-center">
                          <span :class="[ROW_ICON_TRIP, adminTripTone(trip)]"><MapIcon class="h-3.5 w-3.5" aria-hidden="true" /></span>
                        </div>
                        <div class="truncate whitespace-nowrap font-semibold text-gray-900 dark:text-gray-100">
                          {{ tripTimeParts(trip.tripStartedAt, trip.tripEndedAt).time }}
                        </div>
                        <div class="whitespace-nowrap text-gray-700 dark:text-gray-300">
                          <template v-if="tripConsumption(trip) && trip.distanceKm">&minus;{{ formatDecimal(tripConsumption(trip)!.kwhPer100km * trip.distanceKm / 100, 2) }} kWh</template>
                          <span v-else :class="EMPTY_CELL">-</span>
                        </div>
                        <div class="whitespace-nowrap font-semibold text-gray-900 dark:text-gray-100">
                          <template v-if="tripConsumption(trip)">{{ tripConsumption(trip)!.estimated ? '~' : '' }}{{ formatConsumption(tripConsumption(trip)!.kwhPer100km) }}</template>
                          <span v-else :class="EMPTY_CELL">-</span>
                        </div>
                        <div class="whitespace-nowrap text-gray-600 dark:text-gray-300">
                          <template v-if="trip.socStart != null && trip.socEnd != null">{{ Math.round(Number(trip.socStart)) }} → {{ Math.round(Number(trip.socEnd)) }} %</template>
                          <span v-else :class="EMPTY_CELL">-</span>
                        </div>
                        <div class="whitespace-nowrap text-center text-gray-600 dark:text-gray-300">
                          <template v-if="trip.maxSpeedKmh != null">{{ t('dashboard.trip_speed_compact', { avg: Math.round(Number(trip.avgSpeedKmh)), max: Math.round(Number(trip.maxSpeedKmh)) }) }}</template>
                          <span v-else :class="EMPTY_CELL">-</span>
                        </div>
                        <div class="whitespace-nowrap text-gray-700 dark:text-gray-300">
                          <template v-if="trip.distanceKm != null">+{{ formatDistance(trip.distanceKm, { round: false }) }}</template>
                          <span v-else :class="EMPTY_CELL">-</span>
                        </div>
                        <div class="whitespace-nowrap text-gray-600 dark:text-gray-300">
                          <template v-if="trip.outsideTempCelsius != null">{{ formatDecimal(Number(trip.outsideTempCelsius), 0) }} °C</template>
                          <span v-else :class="EMPTY_CELL">-</span>
                        </div>
                        <div class="flex justify-end whitespace-nowrap">
                          <ComparisonChip v-if="tripCostPer100kmDisplay(trip) != null"
                            :level="communityLevel(tripCostPer100kmDisplay(trip), personalCostBenchmark?.costPer100km)"
                            :delta-percent="comparisonDeltaPercent(tripCostPer100kmDisplay(trip), personalCostBenchmark?.costPer100km)"
                            :tooltip="costPer100kmTooltip(tripCostPer100kmDisplay(trip))">
                            {{ formatCurrency(tripCostPer100kmDisplay(trip)!) }}/100km
                          </ComparisonChip>
                          <span v-else :class="EMPTY_CELL">-</span>
                        </div>
                        <div class="flex items-center justify-end">
                          <ChevronDownIcon v-if="hasTripMap(trip)"
                            class="h-4 w-4 text-gray-400 transition-transform duration-300 ease-out"
                            :class="expandedTripMaps.has(trip.id + '__d') ? 'rotate-180' : ''" aria-hidden="true" />
                          <FeedActionMenu :placement="tripIdx === 0 ? 'down' : 'up'" v-slot="{ close }">
                            <FeedMenuItem :icon="PlusIcon" @click="startAddTrip(trip.id, trip.tripStartedAt); close()">{{ t('dashboard.trip_add') }}</FeedMenuItem>
                            <FeedMenuItem :icon="PencilSquareIcon" @click="startEditTrip(trip); close()">{{ t('dashboard.action_edit') }}</FeedMenuItem>
                            <DataFeedbackMenuItems v-if="trip.dataSource !== 'USER_CREATED'" subject-type="TRIP" :subject-id="trip.id" @done="close()" />
                            <FeedMenuItem :icon="TrashIcon" separated @click="handleDeleteTrip(trip.id); close()">{{ t('dashboard.action_delete') }}</FeedMenuItem>
                          </FeedActionMenu>
                        </div>
                      </div>
                      <TripClimateMarkers :climate="trip.climate" align="start" class="pb-2 pl-[72px] pr-4" />
                      <DataFeedbackPanel v-if="trip.dataSource !== 'USER_CREATED'" subject-type="TRIP" :subject-id="trip.id" class="mx-4 mb-3" />
                      <Transition :css="false" @enter="onTripFormEnter" @after-enter="onTripFormAfterEnter" @leave="onTripFormLeave">
                        <TripMapPanel v-if="expandedTripMaps.has(trip.id + '__d')" :trip="trip"
                                      :panel-id="'trip-map-' + trip.id + '__d'" class="px-4 pb-3" />
                      </Transition>
                      <Transition :css="false" @enter="onTripFormEnter" @after-enter="onTripFormAfterEnter" @leave="onTripFormLeave">
                      <div v-if="editingTripId === trip.id" class="border-t border-gray-100 bg-gray-50 px-4 py-4 dark:border-gray-700/70 dark:bg-gray-900/40">
                        <DayTripForm v-model="tripForm" mode="edit" :title="t('dashboard.trip_edit')"
                          :error="tripError" :saving="tripSaving" :distance-unit="distanceUnitLabel()"
                          @save="saveTripEdit(trip.id)" @cancel="cancelTripEditFull()" />
                      </div>
                      </Transition>
                      <!-- Luecke zur naechstfrueheren Fahrt - siehe Mobile-Ansicht. -->
                      <div v-if="isRestBreak(day.trips, tripIdx as number) && !showsDrain(trip)"
                           class="pb-2 pl-[72px] pr-4 text-[12px] text-gray-400 tabular-nums dark:text-gray-500">
                        {{ t('dashboard.trip_pause', { duration: formatPauseDuration(pauseBeforeTripMinutes(day.trips, tripIdx as number)) }) }}
                      </div>
                      <!-- Standverlust zwischen zwei Fahrten - siehe Mobile-Ansicht. -->
                      <div v-if="showsDrain(trip)" class="pb-2 pl-[72px] pr-4 text-[12px] tabular-nums text-gray-500 dark:text-gray-400">
                        <i class="mr-1.5 inline-block h-2 w-2 rounded-[2px] bg-amber-400 align-middle" aria-hidden="true" />
                        <template v-if="drainPauseMinutes(trip)">{{ formatPauseDuration(drainPauseMinutes(trip)) }} &middot; </template>
                        <b class="font-semibold text-gray-700 dark:text-gray-200">{{ drainText(trip._phantomDrain) }}</b>{{ drainShare(trip._phantomDrain) }}
                        {{ t('dashboard.phantom_drain_word') }}
                        <template v-if="phantomDrainEur(trip._phantomDrain) != null"> &middot; ≈ {{ formatCurrency(phantomDrainEur(trip._phantomDrain)!) }}</template>
                        <router-link v-if="!authStore.canViewLiveAnalytics && purchasesAvailable()" :to="upsellTarget" class="ml-1.5 font-semibold text-gray-900 underline underline-offset-2 dark:text-gray-100">{{ t('dashboard.phantom_teaser_unlock') }}</router-link>
                      </div>
                    </template>
                    </template><!-- end v-else trip (desktop) -->
                    </template><!-- end v-for events (desktop) -->
                    </template><!-- end v-for days (desktop) -->
                  </div>
                  </Transition>
                </div>

              </template><!-- end tripGroup -->

              <!-- ===== REGULAR ENTRY (charge log / inaccessible trip) ===== -->
              <template v-else>
              <!-- Standverlust vor dieser Ladung: eine leise Zeile zwischen den Karten, Amber wie im Kopf. -->
              <div v-if="showsDrain(item.entry)" class="px-4 py-1 text-[12px] tabular-nums text-gray-500 dark:text-gray-400">
                <i class="mr-1.5 inline-block h-2 w-2 rounded-[2px] bg-amber-400 align-middle" aria-hidden="true" />
                <b class="font-semibold text-gray-700 dark:text-gray-200">{{ drainText(item.entry._phantomDrain) }}</b>{{ drainShare(item.entry._phantomDrain) }}
                {{ t('dashboard.phantom_drain_word') }}
                <template v-if="phantomDrainEur(item.entry._phantomDrain) != null"> &middot; <span class="whitespace-nowrap">≈ {{ formatCurrency(phantomDrainEur(item.entry._phantomDrain)!) }}</span></template>
                <router-link v-if="!authStore.canViewLiveAnalytics && purchasesAvailable()" :to="upsellTarget" class="ml-1.5 font-semibold text-gray-900 underline underline-offset-2 dark:text-gray-100">{{ t('dashboard.phantom_teaser_unlock') }}</router-link>
              </div>
              <!-- Add-trip form triggered from a charge entry -->
              <Transition :css="false" @enter="onTripFormEnter" @after-enter="onTripFormAfterEnter" @leave="onTripFormLeave">
              <div v-if="addingTripAfterId === item.entry.id" class="mb-2 rounded-2xl bg-gray-50 p-4 dark:bg-gray-900/40">
                <DayTripForm v-model="tripForm" mode="add" :title="t('dashboard.trip_add')"
                  :error="tripError" :saving="tripSaving" :distance-unit="distanceUnitLabel()"
                  @save="saveNewTrip()" @cancel="cancelTripEdit()" />
              </div>
              </Transition>

              <!-- CHARGE ENTRY (DESKTOP GRID, normal logs only): Rasterzeile im Spaltenraster der Fahrten -->
              <div v-if="!item.entry._isLadegruppe" class="hidden gridfeed:block rounded-2xl bg-white edge-card dark:bg-gray-800">
                <div :class="[FEED_GRID_COLS, 'items-center px-4 py-2.5 text-[13px] tabular-nums']">
                  <div class="flex items-center gap-1.5">
                    <span :class="ROW_ICON_CHARGE"><BoltIcon class="h-3.5 w-3.5" aria-hidden="true" /></span>
                    <span v-if="item.entry.chargingType === 'AC' || item.entry.chargingType === 'DC'" class="text-[11.5px] font-semibold text-gray-500 dark:text-gray-400">{{ item.entry.chargingType }}</span>
                  </div>
                  <div class="whitespace-nowrap font-semibold text-gray-900 dark:text-gray-100">
                    <template v-if="logKwh(item.entry) != null">+{{ formatDecimal(logKwh(item.entry)!, 1) }} kWh</template>
                    <span v-else :class="EMPTY_CELL">-</span>
                  </div>
                  <!-- Wann und wo -->
                  <div class="flex min-w-0 items-center gap-2 whitespace-nowrap">
                    <span class="text-gray-700 dark:text-gray-300">{{ formatLogDate(item.entry.loggedAt) }}</span>
                    <span class="min-w-0 truncate text-gray-500 dark:text-gray-400">{{ chargePlace(item.entry) }}</span>
                  </div>
                  <!-- Verbrauch, oder warum keiner steht -->
                  <div class="whitespace-nowrap">
                    <button v-if="item.entry.consumptionKwhPer100km == null && item.entry.kwhCountedInNextConsumption" type="button"
                      class="inline-flex items-center gap-1 text-[12px] text-gray-500 underline decoration-gray-300 underline-offset-4 focus:outline-none focus-visible:ring-2 focus-visible:ring-story-accent dark:text-gray-400 dark:decoration-gray-600"
                      :aria-expanded="openTooltipLogId === item.entry.id"
                      @click.stop="openTooltipLogId = openTooltipLogId === item.entry.id ? null : item.entry.id">
                      <InformationCircleIcon class="h-3.5 w-3.5 shrink-0" aria-hidden="true" />{{ t('dashboard.kwh_in_next_hint') }}
                    </button>
                    <button v-else-if="item.entry.consumptionKwhPer100km == null && isShortTrip(item.entry)" type="button"
                      class="inline-flex items-center gap-1 text-[12px] text-gray-500 underline decoration-gray-300 underline-offset-4 focus:outline-none focus-visible:ring-2 focus-visible:ring-story-accent dark:text-gray-400 dark:decoration-gray-600"
                      :aria-expanded="openTooltipLogId === item.entry.id"
                      @click.stop="openTooltipLogId = openTooltipLogId === item.entry.id ? null : item.entry.id">
                      <InformationCircleIcon class="h-3.5 w-3.5 shrink-0" aria-hidden="true" />{{ t('dashboard.short_trip_hint') }}
                    </button>
                    <span v-else-if="item.entry.consumptionKwhPer100km != null" class="inline-flex items-center gap-1 font-semibold"
                      :class="consumptionToneClass(item.entry)"
                      :title="item.entry.consumptionIsEstimated ? t('logs.consumption_estimated_title') : item.entry.consumptionQuality === 'SOC_DELTA' ? t('logs.consumption_soc_delta_title') : undefined">
                      <button v-if="item.entry.consumptionImplausible" type="button"
                        class="shrink-0 rounded focus:outline-none focus-visible:ring-2 focus-visible:ring-amber-400"
                        :aria-label="t('dashboard.implausible_tooltip_title')" :aria-expanded="openTooltipLogId === item.entry.id"
                        @click.stop="openTooltipLogId = openTooltipLogId === item.entry.id ? null : item.entry.id">
                        <ExclamationTriangleIcon class="h-3.5 w-3.5" aria-hidden="true" />
                      </button>
                      {{ item.entry.consumptionIsEstimated ? '~' : '' }}{{ formatConsumption(item.entry.consumptionKwhPer100km) }}
                    </span>
                    <span v-else :class="EMPTY_CELL">-</span>
                  </div>
                  <div class="whitespace-nowrap text-gray-600 dark:text-gray-300">
                    <template v-if="formatSocRange(item.entry.socBeforeChargePercent, item.entry.socAfterChargePercent)">{{ formatSocRange(item.entry.socBeforeChargePercent, item.entry.socAfterChargePercent) }}</template>
                    <span v-else :class="EMPTY_CELL">-</span>
                  </div>
                  <div class="whitespace-nowrap text-gray-600 dark:text-gray-300">
                    <template v-if="item.entry.maxChargingPowerKw">max {{ formatDecimal(Number(item.entry.maxChargingPowerKw), 0) }} kW</template>
                    <span v-else :class="EMPTY_CELL">-</span>
                  </div>
                  <div class="whitespace-nowrap">
                    <button v-if="item.entry.distanceSinceLastChargeKm != null || item.entry.odometerKm" type="button"
                      class="text-gray-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-story-accent dark:text-gray-300"
                      :class="item.entry.distanceSinceLastChargeKm != null && item.entry.odometerKm ? 'underline decoration-dotted decoration-gray-400 underline-offset-4' : 'cursor-default'"
                      :title="item.entry.distanceSinceLastChargeKm != null && item.entry.odometerKm ? t('logs.toggle_odometer') : undefined"
                      @click.stop="toggleOdometerDisplay(item.entry.distanceSinceLastChargeKm, item.entry.odometerKm)" @mousedown.stop>
                      <template v-if="item.entry.distanceSinceLastChargeKm != null && !showOdometer">+{{ formatDistance(item.entry.distanceSinceLastChargeKm) }}</template>
                      <template v-else>{{ item.entry.odometerKm != null ? formatDistance(item.entry.odometerKm) : '' }}</template>
                    </button>
                    <span v-else :class="EMPTY_CELL">-</span>
                  </div>
                  <div class="whitespace-nowrap text-gray-600 dark:text-gray-300">
                    <template v-if="item.entry.temperatureCelsius != null">{{ formatDecimal(Number(item.entry.temperatureCelsius), 0) }} °C</template>
                    <span v-else :class="EMPTY_CELL">-</span>
                  </div>
                  <!-- Preis: Betrag fett, ct/kWh grau; ohne Preis die Handlung in Amber -->
                  <div class="flex items-center justify-end gap-1.5 whitespace-nowrap">
                    <button v-if="realCostHintFor(item.entry.id)" type="button"
                      class="grid h-7 w-7 place-items-center rounded-full text-gray-500 hover:bg-gray-100 hover:text-gray-900 focus:outline-none focus-visible:ring-2 focus-visible:ring-story-accent dark:text-gray-400 dark:hover:bg-white/10"
                      :aria-expanded="openRealCostTooltipId === item.entry.id" :aria-label="t('dashboard.real_cost_tooltip_title')"
                      @click.stop="toggleRealCostTooltip(item.entry.id)">
                      <InformationCircleIcon class="h-4 w-4" aria-hidden="true" />
                    </button>
                    <span v-if="item.entry.costEur != null && logKwhBasis(item.entry)" class="text-right"
                      :title="isNettoOnlyCostLog(item.entry) ? t('dashboard.cost_pill_netto_title') : undefined">
                      <b class="font-semibold text-gray-900 dark:text-gray-100">{{ formatCurrency(item.entry.costEur) }}</b>
                      <span class="ml-1 text-[12px] text-gray-500 dark:text-gray-400">{{ formatCostPerKwh(item.entry.costEur / logKwhBasis(item.entry)!) }}</span>
                    </span>
                    <button v-else-if="sourceInfo(item.entry.dataSource) && item.entry.id" type="button" data-testid="charge-price-chip"
                      :aria-label="t('priceamend.chip_aria')" :class="PRICE_CHIP" @click.stop="priceAmendingLog = item.entry">
                      {{ t('priceamend.chip') }}
                      <WattBadge :amount="wattPossibleForLog(coinStore.catalog, item.entry)" up-to />
                    </button>
                    <span v-else :class="EMPTY_CELL">-</span>
                  </div>
                  <div class="flex justify-end">
                    <FeedActionMenu v-slot="{ close }">
                      <FeedMenuItem :icon="PencilSquareIcon" @click="editingLog = item.entry; close()">{{ t('dashboard.action_edit') }}</FeedMenuItem>
                      <FeedMenuItem :icon="MapIcon" @click="startAddTrip(item.entry.id); close()">{{ t('dashboard.action_add_trip') }}</FeedMenuItem>
                      <FeedMenuItem :icon="LinkIcon" @click="openMergeModal(item.entry); close()">{{ t('dashboard.action_merge') }}</FeedMenuItem>
                      <FeedMenuItem v-if="otherCars.length > 0" :icon="ArrowsRightLeftIcon" @click="openReassignModal(item.entry); close()">{{ t('dashboard.action_reassign') }}</FeedMenuItem>
                      <FeedMenuItem :icon="TrashIcon" separated @click="deleteLog(item.entry.id); close()">{{ t('dashboard.action_delete') }}</FeedMenuItem>
                    </FeedActionMenu>
                  </div>
                </div>
                <!-- Zweite Zeile: Brutto/Netto-Nachtrag, Quelle und "Korrekt erkannt?" -->
                <div v-if="item.entry.kwhCharged != null || item.entry.kwhAtVehicle != null || logHasCurve(item.entry)"
                  class="-mt-1 flex items-center gap-3 pb-2 pl-[72px] pr-4 text-[12px] text-gray-500 tabular-nums dark:text-gray-400">
                  <template v-if="logEditState[item.entry.id] !== undefined">
                    <input type="number" step="0.01" min="0"
                      v-model="logEditState[item.entry.id]"
                      :aria-label="t(logFieldToSet(item.entry) === 'kwhCharged' ? 'dashboard.ac_gross_label_brutto' : 'dashboard.ac_gross_label_netto')"
                      :class="QUICK_INPUT"
                      @keydown.enter.prevent="applyLogValue(item.entry)"
                      @keydown.escape.prevent="cancelLogEdit(item.entry.id)" />
                    <span>kWh {{ t(logFieldToSet(item.entry) === 'kwhCharged' ? 'dashboard.ac_gross_label_brutto' : 'dashboard.ac_gross_label_netto') }}</span>
                    <button type="button" :class="QUICK_BTN" :disabled="logSaving.has(item.entry.id)" :aria-label="t('common.save')" @click="applyLogValue(item.entry, $event)">
                      <ArrowPathIcon v-if="logSaving.has(item.entry.id)" class="h-4 w-4 animate-spin" aria-hidden="true" />
                      <CheckIcon v-else class="h-4 w-4" aria-hidden="true" />
                    </button>
                    <button type="button" :class="QUICK_BTN" :aria-label="t('common.cancel')" @click="cancelLogEdit(item.entry.id)">
                      <XMarkIcon class="h-4 w-4" aria-hidden="true" />
                    </button>
                    <span v-if="logError[item.entry.id]" role="alert" class="text-amber-700 dark:text-amber-400">{{ logError[item.entry.id] }}</span>
                  </template>
                  <button v-else-if="item.entry.kwhCharged != null && item.entry.kwhAtVehicle != null" type="button"
                    class="inline-flex items-center gap-1.5 hover:text-gray-900 dark:hover:text-gray-100" @click="startLogEdit(item.entry, $event)">
                    <span>{{ formatDecimal(Number(item.entry.kwhCharged), 1) }} kWh {{ t('dashboard.ac_gross_label_brutto') }}</span>
                    <span>&middot; {{ formatDecimal(Number(chargingEfficiency(item.entry.kwhCharged, item.entry.kwhAtVehicle)), 0) }} % {{ t('dashboard.ac_gross_efficiency') }}</span>
                  </button>
                  <button v-else type="button" class="inline-flex items-center gap-1 hover:text-gray-900 dark:hover:text-gray-100" @click="startLogEdit(item.entry, $event)">
                    <PlusIcon class="h-3.5 w-3.5" aria-hidden="true" />
                    {{ t(logFieldToSet(item.entry) === 'kwhCharged' ? 'dashboard.ac_gross_add_brutto' : 'dashboard.ac_gross_add_netto') }}
                  </button>
                  <span v-if="sourceInfo(item.entry.dataSource)">&middot; {{ sourceInfo(item.entry.dataSource)!.label }}</span>
                  <button v-if="logHasCurve(item.entry)" type="button" @click.stop="openPowerCurve(item.entry)" aria-haspopup="dialog"
                    :aria-label="logCurveOpen(item.entry) ? t('dashboard.show_power_curve') : t('dashboard.power_curve_locked')"
                    :title="logCurveOpen(item.entry) ? undefined : t('dashboard.power_curve_locked')"
                    class="inline-flex shrink-0 items-center gap-1 rounded font-semibold text-gray-700 hover:text-gray-900 focus:outline-none focus-visible:ring-2 focus-visible:ring-story-accent dark:text-gray-200 dark:hover:text-gray-50">
                    <ChartBarSquareIcon class="h-4 w-4" aria-hidden="true" />{{ t('dashboard.power_curve_label') }}
                    <LockClosedIcon v-if="!logCurveOpen(item.entry)" class="h-3 w-3" aria-hidden="true" />
                  </button>
                  <div v-if="isRateableLogSource(item.entry.dataSource)" class="ml-auto flex items-center gap-0.5" @click.stop>
                    <span class="mr-1">{{ t('dashboard.data_feedback_label') }}</span>
                    <DataFeedbackButtons subject-type="LOG" :subject-id="item.entry.id" compact />
                  </div>
                </div>
                <!-- Erklaerungen zu Hinweisen der Zeile -->
                <div v-if="openTooltipLogId === item.entry.id && (item.entry.consumptionImplausible || (item.entry.consumptionKwhPer100km == null && (isShortTrip(item.entry) || item.entry.kwhCountedInNextConsumption)))"
                  class="px-4 pb-3">
                  <div v-if="item.entry.consumptionImplausible" :class="NOTE_WARN">
                    <p class="font-semibold">{{ t('dashboard.implausible_tooltip_title') }}</p>
                    <p>{{ t('dashboard.implausible_tooltip_desc', { value: formatConsumption(item.entry.consumptionKwhPer100km) }) }}</p>
                    <ul class="mt-1 list-inside list-disc space-y-0.5">
                      <li>{{ t('dashboard.implausible_tooltip_cause1') }}</li>
                      <li>{{ t('dashboard.implausible_tooltip_cause2') }}</li>
                    </ul>
                  </div>
                  <div v-else-if="isShortTrip(item.entry)" :class="NOTE_INFO">
                    <p class="font-semibold">{{ t('dashboard.short_trip_tooltip_title') }}</p>
                    <p>{{ t('dashboard.short_trip_tooltip_desc') }}</p>
                  </div>
                  <div v-else :class="NOTE_INFO">
                    <p class="font-semibold">{{ t('dashboard.kwh_in_next_tooltip_title') }}</p>
                    <p>{{ t('dashboard.kwh_in_next_tooltip_desc') }}</p>
                  </div>
                </div>
                <div v-if="openRealCostTooltipId === item.entry.id && realCostHintFor(item.entry.id)" class="px-4 pb-3">
                  <div :class="NOTE_INFO">
                    <p class="font-semibold">{{ t('dashboard.real_cost_tooltip_title') }}</p>
                    <p>{{ t('dashboard.real_cost_label') }} ≈ {{ formatCurrency(realCostHintFor(item.entry.id)!.bruttoCostEur) }} &middot; {{ formatCostPerKwh(realCostHintFor(item.entry.id)!.bruttoRatePerNettoKwhEur) }}</p>
                    <p>{{ realCostHintFor(item.entry.id)!.source === 'measured-median'
                          ? t('dashboard.real_cost_tooltip_measured', { count: realCostHintFor(item.entry.id)!.sampleSize, pct: realCostHintFor(item.entry.id)!.efficiencyPercent }, realCostHintFor(item.entry.id)!.sampleSize)
                          : t('dashboard.real_cost_tooltip_pauschale', { pct: realCostHintFor(item.entry.id)!.efficiencyPercent }) }}</p>
                  </div>
                </div>
                <DataFeedbackPanel v-if="isRateableLogSource(item.entry.dataSource)" subject-type="LOG" :subject-id="item.entry.id" class="mx-4 mb-3" />
              </div>

              <!-- CHARGE ENTRY (DESKTOP GRID, Ladegruppe) -->
              <div v-if="item.entry._isLadegruppe" class="hidden gridfeed:block rounded-2xl bg-white edge-card dark:bg-gray-800">
                <div role="button" tabindex="0" @click="toggleLadegruppe(item.entry.id)"
                  @keydown.enter.self.prevent="toggleLadegruppe(item.entry.id)" @keydown.space.self.prevent="toggleLadegruppe(item.entry.id)"
                  :aria-expanded="expandedGroups.has(item.entry.id)"
                  :class="[FEED_GRID_COLS, 'w-full cursor-pointer items-center px-4 py-2.5 text-left text-[13px] tabular-nums transition hover:bg-gray-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-story-accent dark:hover:bg-white/5',
                           expandedGroups.has(item.entry.id) ? 'rounded-t-2xl' : 'rounded-2xl']">
                  <div class="flex items-center gap-1.5">
                    <span :class="ROW_ICON_CHARGE"><BoltIcon class="h-3.5 w-3.5" aria-hidden="true" /></span>
                    <span class="text-[12px] font-semibold text-gray-500 dark:text-gray-400">{{ item.entry._topUps?.length ?? 0 }}&times;</span>
                  </div>
                  <div class="whitespace-nowrap font-semibold text-gray-900 dark:text-gray-100">+{{ formatDecimal(Number(item.entry._totalKwh), 1) }} kWh</div>
                  <div class="flex min-w-0 items-center gap-2 whitespace-nowrap">
                    <span class="text-gray-700 dark:text-gray-300">{{ item.entry._dateRangeLabel }}</span>
                    <span class="min-w-0 truncate text-gray-500 dark:text-gray-400">{{ chargePlace(item.entry) }}</span>
                  </div>
                  <div class="whitespace-nowrap">
                    <span v-if="item.entry._totalConsumption != null" class="font-semibold" :class="consumptionValueTone(item.entry._totalConsumption)">{{ formatConsumption(item.entry._totalConsumption) }}</span>
                    <span v-else :class="EMPTY_CELL">-</span>
                  </div>
                  <div class="whitespace-nowrap text-gray-600 dark:text-gray-300">
                    <template v-if="item.entry._maxSoc != null">{{ t('logs.group_max_soc', { soc: item.entry._maxSoc }) }}</template>
                    <span v-else :class="EMPTY_CELL">-</span>
                  </div>
                  <div class="whitespace-nowrap text-gray-600 dark:text-gray-300">
                    <template v-if="item.entry._maxPower">max {{ formatDecimal(Number(item.entry._maxPower), 0) }} kW</template>
                    <span v-else :class="EMPTY_CELL">-</span>
                  </div>
                  <div class="whitespace-nowrap">
                    <button v-if="item.entry.distanceSinceLastChargeKm != null || item.entry.odometerKm" type="button"
                      class="text-gray-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-story-accent dark:text-gray-300"
                      :class="item.entry.distanceSinceLastChargeKm != null && item.entry.odometerKm ? 'underline decoration-dotted decoration-gray-400 underline-offset-4' : 'cursor-default'"
                      @click.stop="toggleOdometerDisplay(item.entry.distanceSinceLastChargeKm, item.entry.odometerKm)" @mousedown.stop>
                      <template v-if="item.entry.distanceSinceLastChargeKm != null && !showOdometer">+{{ formatDistance(item.entry.distanceSinceLastChargeKm) }}</template>
                      <template v-else>{{ item.entry.odometerKm != null ? formatDistance(item.entry.odometerKm) : '' }}</template>
                    </button>
                    <span v-else :class="EMPTY_CELL">-</span>
                  </div>
                  <div :class="EMPTY_CELL">-</div>
                  <div class="flex justify-end whitespace-nowrap">
                    <span v-if="item.entry._totalCostEur != null && item.entry._costBasisKwh && item.entry._isFullyPriced"
                      :title="item.entry._costIsNettoOnly ? t('dashboard.cost_pill_netto_title') : undefined">
                      <b class="font-semibold text-gray-900 dark:text-gray-100">{{ formatCurrency(item.entry._totalCostEur) }}</b>
                      <span class="ml-1 text-[12px] text-gray-500 dark:text-gray-400">{{ formatCostPerKwh(item.entry._totalCostEur / item.entry._costBasisKwh) }}</span>
                    </span>
                    <!-- Teilweise bepreist: eine Rate aus einem Bruchteil der Energie waere irrefuehrend. -->
                    <button v-else-if="item.entry._pricelessSubs?.length" type="button" data-testid="group-price-chip"
                      :aria-label="t('priceamend.chip_group_aria')" :class="PRICE_CHIP" @click.stop="openGroupPriceAmend(item.entry)">
                      {{ t('priceamend.chip_group') }}
                      <WattBadge :amount="groupPricelessWatt(item.entry)" up-to />
                    </button>
                    <span v-else :class="EMPTY_CELL">-</span>
                  </div>
                  <div class="flex items-center justify-end">
                    <ChevronDownIcon class="h-4 w-4 text-gray-400 transition-transform duration-300 ease-out"
                      :class="expandedGroups.has(item.entry.id) ? 'rotate-180' : ''" aria-hidden="true" />
                    <FeedActionMenu v-if="otherCars.length > 0" v-slot="{ close }">
                      <FeedMenuItem :icon="ArrowsRightLeftIcon" @click="openReassignModal(item.entry); close()">{{ t('dashboard.action_reassign') }}</FeedMenuItem>
                    </FeedActionMenu>
                  </div>
                </div>
                <!-- Fehlender Brutto- oder Nettowert der Gruppe -->
                <div v-if="item.entry._canEditMissing"
                  class="-mt-1 flex items-center gap-2 pb-2 pl-[72px] pr-4 text-[12px] text-gray-500 tabular-nums dark:text-gray-400"
                  @click.stop @mousedown.stop>
                  <template v-if="grossEditState[item.entry.id] !== undefined">
                    <input type="number" step="0.01" min="0"
                      v-model="grossEditState[item.entry.id]" :aria-label="t(item.entry._missingLabel)" :class="QUICK_INPUT"
                      @click.stop @mousedown.stop @keydown.stop
                      @keydown.enter.stop.prevent="applyGrossTotal(item.entry)"
                      @keydown.escape.stop.prevent="cancelGrossEdit(item.entry.id)" />
                    <span>kWh {{ t(item.entry._missingLabel) }}</span>
                    <button type="button" :class="QUICK_BTN" :disabled="grossSaving.has(item.entry.id)" :aria-label="t('common.save')" @click.stop="applyGrossTotal(item.entry, $event)">
                      <ArrowPathIcon v-if="grossSaving.has(item.entry.id)" class="h-4 w-4 animate-spin" aria-hidden="true" />
                      <CheckIcon v-else class="h-4 w-4" aria-hidden="true" />
                    </button>
                    <button type="button" :class="QUICK_BTN" :aria-label="t('common.cancel')" @click.stop="cancelGrossEdit(item.entry.id)">
                      <XMarkIcon class="h-4 w-4" aria-hidden="true" />
                    </button>
                    <span v-if="grossError[item.entry.id]" role="alert" class="text-amber-700 dark:text-amber-400">{{ grossError[item.entry.id] }}</span>
                  </template>
                  <button v-else-if="item.entry._efficiency != null" type="button" class="inline-flex items-center gap-1.5 hover:text-gray-900 dark:hover:text-gray-100" @click.stop="startGrossEdit(item.entry, $event)">
                    <span>{{ formatDecimal(Number(item.entry._bruttoSum), 1) }} kWh {{ t('dashboard.ac_gross_label_brutto') }}</span>
                    <span>&middot; {{ item.entry._efficiency }} % {{ t('dashboard.ac_gross_efficiency') }}</span>
                    <PencilSquareIcon class="h-3.5 w-3.5" aria-hidden="true" />
                  </button>
                  <button v-else-if="item.entry._totalMissingKwh != null" type="button" class="inline-flex items-center gap-1.5 hover:text-gray-900 dark:hover:text-gray-100" @click.stop="startGrossEdit(item.entry, $event)">
                    <span>{{ formatDecimal(Number(item.entry._totalMissingKwh), 1) }} kWh {{ t(item.entry._missingLabel) }}</span>
                    <PencilSquareIcon class="h-3.5 w-3.5" aria-hidden="true" />
                  </button>
                  <button v-else type="button" class="inline-flex items-center gap-1 hover:text-gray-900 dark:hover:text-gray-100" @click.stop="startGrossEdit(item.entry, $event)">
                    <PlusIcon class="h-3.5 w-3.5" aria-hidden="true" />{{ t(item.entry._addMissingLabel) }}
                  </button>
                </div>

                <!-- Teilladungen im Raster -->
                <Collapse :open="expandedGroups.has(item.entry.id)">
                  <div class="border-t border-gray-100 dark:border-gray-700/70">
                    <div v-for="topUp in item.entry._topUps" :key="topUp.id + '__d'"
                      :class="[FEED_GRID_COLS, 'items-center border-t border-gray-100 px-4 py-1.5 text-[13px] tabular-nums first:border-t-0 dark:border-gray-700/70']">
                      <div class="pl-9 text-[12px] text-gray-400 dark:text-gray-500" aria-hidden="true">&#9492;</div>
                      <div class="whitespace-nowrap">
                        <div class="font-semibold text-gray-800 dark:text-gray-100">+{{ formatDecimal(Number(topUp.kwhAtVehicle ?? topUp.kwhCharged ?? 0), 1) }} kWh</div>
                        <div v-if="topUp.kwhCharged != null && topUp.kwhAtVehicle != null" class="text-[11.5px] text-gray-500 dark:text-gray-400">
                          {{ formatDecimal(Number(topUp.kwhCharged), 1) }} kWh {{ t('dashboard.ac_gross_label_brutto') }} &middot; {{ formatDecimal(Number(chargingEfficiency(topUp.kwhCharged, topUp.kwhAtVehicle)), 0) }} %
                        </div>
                      </div>
                      <div class="flex min-w-0 items-center gap-2 whitespace-nowrap text-gray-600 dark:text-gray-300">
                        <span class="truncate">
                          <template v-if="item.entry._spansMultipleDays">{{ formatLogDate(topUp.loggedAt) }}</template>
                          <template v-else>{{ new Date(topUp.loggedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) }}</template>
                        </span>
                        <button v-if="(topUp.hasPowerCurve && authStore.canViewLiveAnalytics) || (topUp.hasSocCurve && authStore.canViewSocCurve)" type="button"
                          @click.stop="openPowerCurve(topUp)" :aria-label="t('dashboard.show_power_curve')" aria-haspopup="dialog"
                          class="inline-flex items-center gap-1 rounded font-semibold text-gray-700 hover:text-gray-900 focus:outline-none focus-visible:ring-2 focus-visible:ring-story-accent dark:text-gray-200">
                          <ChartBarSquareIcon class="h-4 w-4" aria-hidden="true" />{{ t('dashboard.power_curve_label') }}
                        </button>
                      </div>
                      <div :class="EMPTY_CELL">-</div>
                      <div class="whitespace-nowrap text-gray-600 dark:text-gray-300">
                        <template v-if="topUp.socAfterChargePercent != null">{{ formatSocRange(topUp.socBeforeChargePercent, topUp.socAfterChargePercent) }}</template>
                        <span v-else :class="EMPTY_CELL">-</span>
                      </div>
                      <div class="whitespace-nowrap text-gray-600 dark:text-gray-300">
                        <template v-if="topUp.maxChargingPowerKw">max {{ formatDecimal(Number(topUp.maxChargingPowerKw), 0) }} kW</template>
                        <span v-else :class="EMPTY_CELL">-</span>
                      </div>
                      <div class="whitespace-nowrap text-gray-600 dark:text-gray-300">
                        <template v-if="topUp.chargeDurationMinutes">{{ formatPauseDuration(topUp.chargeDurationMinutes) }}</template>
                        <span v-else :class="EMPTY_CELL">-</span>
                      </div>
                      <div class="whitespace-nowrap text-gray-600 dark:text-gray-300">
                        <template v-if="topUp.temperatureCelsius != null">{{ formatDecimal(Number(topUp.temperatureCelsius), 0) }} °C</template>
                        <span v-else :class="EMPTY_CELL">-</span>
                      </div>
                      <!-- Jede Teilladung zeigt ihren eigenen Preis-Status. -->
                      <div class="flex justify-end whitespace-nowrap">
                        <span v-if="topUp.costEur != null && costBasisKwh(topUp)">
                          <b class="font-semibold text-gray-900 dark:text-gray-100">{{ formatCurrency(topUp.costEur) }}</b>
                          <span class="ml-1 text-[12px] text-gray-500 dark:text-gray-400">{{ formatCostPerKwh(topUp.costEur / costBasisKwh(topUp)!) }}</span>
                        </span>
                        <button v-else-if="costBasisKwh(topUp)" type="button" data-testid="topup-price-chip"
                          :aria-label="t('priceamend.chip_aria')" :class="PRICE_CHIP" @click.stop="priceAmendingLog = topUp">
                          {{ t('priceamend.chip') }}
                          <WattBadge :amount="wattPossibleForLog(coinStore.catalog, topUp)" up-to />
                        </button>
                        <span v-else :class="EMPTY_CELL">-</span>
                      </div>
                      <div class="flex justify-end">
                        <FeedActionMenu placement="up" v-slot="{ close }">
                          <FeedMenuItem :icon="PencilSquareIcon" @click="editingLog = topUp; close()">{{ t('dashboard.action_edit') }}</FeedMenuItem>
                          <FeedMenuItem :icon="LinkIcon" @click="openMergeModal(topUp); close()">{{ t('dashboard.action_merge') }}</FeedMenuItem>
                          <FeedMenuItem v-if="otherCars.length > 0" :icon="ArrowsRightLeftIcon" @click="openReassignModal(topUp); close()">{{ t('dashboard.action_reassign') }}</FeedMenuItem>
                          <FeedMenuItem :icon="TrashIcon" separated @click="deleteLog(topUp.id); close()">{{ t('dashboard.action_delete') }}</FeedMenuItem>
                        </FeedActionMenu>
                      </div>
                    </div>
                    <!-- Ein Hinweis pro Gruppe statt eines Schlosses je Teilladung -->
                    <button v-if="!authStore.canViewLiveAnalytics && purchasesAvailable() && curveTopUpCount(item.entry) > 0" type="button" aria-haspopup="dialog"
                      class="flex w-full items-center gap-1.5 border-t border-gray-100 px-4 py-2 pl-[72px] text-left text-[13px] font-semibold text-gray-700 hover:bg-gray-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-story-accent dark:border-gray-700/70 dark:text-gray-200 dark:hover:bg-white/5"
                      @click.stop="openPowerCurve(firstCurveTopUp(item.entry))">
                      <ChartBarSquareIcon class="h-4 w-4 shrink-0" aria-hidden="true" />
                      <span class="underline decoration-gray-300 underline-offset-4">{{ curveTopUpCount(item.entry) === 1 ? t('dashboard.power_curve_group_locked_one') : t('dashboard.power_curve_group_locked', { count: curveTopUpCount(item.entry) }) }}</span>
                      <LockClosedIcon class="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                    </button>
                  </div>
                </Collapse>
              </div>

              <!-- CHARGE ENTRY (Handy): Zeile wie im Story-Tagesblatt; Antippen klappt Details und Aktionen auf. -->
              <div>
              <div v-if="!item.entry._isLadegruppe" class="gridfeed:hidden rounded-2xl bg-white edge-card dark:bg-gray-800">
                <FeedEventRow kind="charge" v-bind="logTimeColumn(item.entry)" interactive data-testid="charge-row"
                  :expanded="expandedLogs.has(item.entry.id)" @activate="toggleLogExpanded(item.entry.id)">
                  <template #title><span class="min-w-0 line-clamp-2 break-words">{{ chargePlace(item.entry) }}</span></template>
                  <span class="block">
                    <b v-if="logKwh(item.entry) != null" class="font-semibold text-gray-700 dark:text-gray-200">+{{ formatDecimal(logKwh(item.entry)!, 1) }} kWh</b>
                    <template v-if="item.entry.chargingType === 'AC' || item.entry.chargingType === 'DC'"> &middot; {{ item.entry.chargingType }}</template>
                    <template v-if="formatSocRange(item.entry.socBeforeChargePercent, item.entry.socAfterChargePercent)"> &middot; {{ formatSocRange(item.entry.socBeforeChargePercent, item.entry.socAfterChargePercent) }}</template>
                  </span>
                  <template #right>
                    <template v-if="item.entry.costEur != null && logKwhBasis(item.entry)">
                      <b class="block text-[15px] font-bold text-gray-900 dark:text-gray-50"
                        :title="isNettoOnlyCostLog(item.entry) ? t('dashboard.cost_pill_netto_title') : undefined">{{ formatCurrency(item.entry.costEur) }}</b>
                      <span class="block text-[12px] font-semibold text-gray-500 dark:text-gray-400">{{ formatCostPerKwh(item.entry.costEur / logKwhBasis(item.entry)!) }}</span>
                    </template>
                    <b v-else-if="item.entry.costEur != null" class="block text-[15px] font-bold text-gray-900 dark:text-gray-50">{{ formatCurrency(item.entry.costEur) }}</b>
                    <button v-else-if="sourceInfo(item.entry.dataSource) && item.entry.id" type="button" data-testid="charge-price-chip"
                      :aria-label="t('priceamend.chip_aria')" :class="PRICE_CHIP" @click.stop="priceAmendingLog = item.entry">
                      {{ t('priceamend.chip') }}
                      <WattBadge :amount="wattPossibleForLog(coinStore.catalog, item.entry)" up-to />
                    </button>
                  </template>
                </FeedEventRow>
                <!-- Brutto/Netto, Ladekurve und "Korrekt erkannt?": auch zugeklappt sichtbar, volle Breite unter der Zeile -->
                    <!-- Brutto/Netto und "Korrekt erkannt?", auch zugeklappt sichtbar -->
                <div class="-mt-1.5 flex flex-wrap items-center gap-x-3 gap-y-0.5 pb-2 pl-[108px] pr-3 sm:pl-[120px] sm:pr-4 text-[12.5px] text-gray-500 dark:text-gray-400" @click.stop @mousedown.stop>
                      <template v-if="logEditState[item.entry.id] !== undefined">
                        <input type="number" step="0.01" min="0" inputmode="decimal"
                          v-model="logEditState[item.entry.id]"
                          :aria-label="t(logFieldToSet(item.entry) === 'kwhCharged' ? 'dashboard.ac_gross_label_brutto' : 'dashboard.ac_gross_label_netto')"
                          :class="QUICK_INPUT"
                          @click.stop @mousedown.stop @keydown.stop
                          @keydown.enter.stop.prevent="applyLogValue(item.entry)"
                          @keydown.escape.stop.prevent="cancelLogEdit(item.entry.id)" />
                        <span class="whitespace-nowrap">kWh {{ t(logFieldToSet(item.entry) === 'kwhCharged' ? 'dashboard.ac_gross_label_brutto' : 'dashboard.ac_gross_label_netto') }}</span>
                        <button type="button" :class="QUICK_BTN" :disabled="logSaving.has(item.entry.id)" :aria-label="t('common.save')" @click.stop="applyLogValue(item.entry, $event)">
                          <ArrowPathIcon v-if="logSaving.has(item.entry.id)" class="h-4 w-4 animate-spin" aria-hidden="true" />
                          <CheckIcon v-else class="h-4 w-4" aria-hidden="true" />
                        </button>
                        <button type="button" :class="QUICK_BTN" :aria-label="t('common.cancel')" @click.stop="cancelLogEdit(item.entry.id)">
                          <XMarkIcon class="h-4 w-4" aria-hidden="true" />
                        </button>
                        <span v-if="logError[item.entry.id]" role="alert" class="text-amber-700 dark:text-amber-400">{{ logError[item.entry.id] }}</span>
                      </template>
                      <button v-else-if="item.entry.kwhCharged != null && item.entry.kwhAtVehicle != null" type="button"
                        class="inline-flex min-h-8 flex-wrap items-center gap-x-1.5 hover:text-gray-900 dark:hover:text-gray-100" @click.stop="startLogEdit(item.entry, $event)">
                        <span class="whitespace-nowrap">{{ formatDecimal(Number(item.entry.kwhCharged), 1) }} kWh {{ t('dashboard.ac_gross_label_brutto') }}</span>
                        <span class="whitespace-nowrap">&middot; {{ formatDecimal(Number(chargingEfficiency(item.entry.kwhCharged, item.entry.kwhAtVehicle)), 0) }} % {{ t('dashboard.ac_gross_efficiency') }}</span>
                      </button>
                      <button v-else type="button" class="inline-flex min-h-8 items-center gap-1 whitespace-nowrap hover:text-gray-900 dark:hover:text-gray-100" @click.stop="startLogEdit(item.entry, $event)">
                        <PlusIcon class="h-3.5 w-3.5" aria-hidden="true" />
                        {{ t(logFieldToSet(item.entry) === 'kwhCharged' ? 'dashboard.ac_gross_add_brutto' : 'dashboard.ac_gross_add_netto') }}
                      </button>
                      <button v-if="logHasCurve(item.entry)" type="button" @click.stop="openPowerCurve(item.entry)" aria-haspopup="dialog"
                        :aria-label="logCurveOpen(item.entry) ? t('dashboard.show_power_curve') : t('dashboard.power_curve_locked')"
                        class="inline-flex min-h-8 items-center gap-1 whitespace-nowrap font-semibold text-gray-700 hover:text-gray-900 dark:text-gray-200 dark:hover:text-gray-50">
                        <ChartBarSquareIcon class="h-4 w-4" aria-hidden="true" />{{ t('dashboard.power_curve_label') }}
                        <LockClosedIcon v-if="!logCurveOpen(item.entry)" class="h-3.5 w-3.5" aria-hidden="true" />
                      </button>
                      <div v-if="isRateableLogSource(item.entry.dataSource)" class="-my-1 ml-auto flex items-center">
                        <DataFeedbackButtons subject-type="LOG" :subject-id="item.entry.id" />
                      </div>
                    </div>
                <DataFeedbackPanel v-if="isRateableLogSource(item.entry.dataSource)" subject-type="LOG" :subject-id="item.entry.id" class="mx-4 mb-3" />
                <Collapse :open="expandedLogs.has(item.entry.id)">
                  <div class="grid gap-2 px-3 pb-3 sm:px-4">
                    <!-- Was zur Ladung gemessen ist; Hinweise oeffnen ihre Erklaerung darunter. -->
                    <div class="flex flex-wrap items-center gap-x-3 gap-y-1 pl-[96px] sm:pl-[104px] text-[13px] text-gray-600 tabular-nums dark:text-gray-300">
                      <button v-if="item.entry.consumptionKwhPer100km == null && item.entry.kwhCountedInNextConsumption" type="button"
                        class="inline-flex items-center gap-1 underline decoration-gray-300 underline-offset-4 dark:decoration-gray-600"
                        :aria-expanded="openTooltipLogId === item.entry.id"
                        @click.stop="openTooltipLogId = openTooltipLogId === item.entry.id ? null : item.entry.id">
                        <InformationCircleIcon class="h-3.5 w-3.5 shrink-0" aria-hidden="true" />{{ t('dashboard.kwh_in_next_hint') }}
                      </button>
                      <button v-else-if="item.entry.consumptionKwhPer100km == null && isShortTrip(item.entry)" type="button"
                        class="inline-flex items-center gap-1 underline decoration-gray-300 underline-offset-4 dark:decoration-gray-600"
                        :aria-expanded="openTooltipLogId === item.entry.id"
                        @click.stop="openTooltipLogId = openTooltipLogId === item.entry.id ? null : item.entry.id">
                        <InformationCircleIcon class="h-3.5 w-3.5 shrink-0" aria-hidden="true" />{{ t('dashboard.short_trip_hint') }}
                      </button>
                      <span v-if="item.entry.consumptionKwhPer100km != null" class="inline-flex items-center gap-1 whitespace-nowrap"
                        :class="consumptionToneClass(item.entry)"
                        :title="item.entry.consumptionIsEstimated ? t('logs.consumption_estimated_title') : item.entry.consumptionQuality === 'SOC_DELTA' ? t('logs.consumption_soc_delta_title') : undefined">
                        <button v-if="item.entry.consumptionImplausible" type="button" class="shrink-0"
                          :aria-label="t('dashboard.implausible_tooltip_title')" :aria-expanded="openTooltipLogId === item.entry.id"
                          @click.stop="openTooltipLogId = openTooltipLogId === item.entry.id ? null : item.entry.id">
                          <ExclamationTriangleIcon class="h-4 w-4" aria-hidden="true" />
                        </button>
                        <b class="font-semibold">{{ item.entry.consumptionIsEstimated ? '~' : '' }}{{ formatConsumption(item.entry.consumptionKwhPer100km) }}</b>
                      </span>
                      <span v-if="item.entry.chargeDurationMinutes" class="whitespace-nowrap">{{ formatPauseDuration(item.entry.chargeDurationMinutes) }}</span>
                      <span v-if="item.entry.maxChargingPowerKw" class="whitespace-nowrap">max {{ formatDecimal(Number(item.entry.maxChargingPowerKw), 0) }} kW</span>
                      <span v-if="item.entry.temperatureCelsius != null" class="whitespace-nowrap">{{ formatDecimal(Number(item.entry.temperatureCelsius), 0) }} °C</span>
                      <button v-if="item.entry.distanceSinceLastChargeKm != null || item.entry.odometerKm" type="button"
                        class="whitespace-nowrap"
                        :class="item.entry.distanceSinceLastChargeKm != null && item.entry.odometerKm ? 'underline decoration-dotted decoration-gray-400 underline-offset-4' : 'cursor-default'"
                        @click.stop="toggleOdometerDisplay(item.entry.distanceSinceLastChargeKm, item.entry.odometerKm)">
                        <template v-if="item.entry.distanceSinceLastChargeKm != null && !showOdometer">+{{ formatDistance(item.entry.distanceSinceLastChargeKm) }}</template>
                        <template v-else>{{ item.entry.odometerKm != null ? formatDistance(item.entry.odometerKm) : '' }}</template>
                      </button>
                      <span v-if="sourceInfo(item.entry.dataSource)" class="whitespace-nowrap text-gray-500 dark:text-gray-400">{{ sourceInfo(item.entry.dataSource)!.label }}</span>
                      <button v-if="realCostHintFor(item.entry.id)" type="button"
                        class="inline-flex items-center gap-1 whitespace-nowrap underline decoration-gray-300 underline-offset-4 dark:decoration-gray-600"
                        :aria-expanded="openRealCostTooltipId === item.entry.id" :aria-label="t('dashboard.real_cost_tooltip_title')"
                        @click.stop="toggleRealCostTooltip(item.entry.id)">
                        {{ t('dashboard.real_cost_label') }} ≈ {{ formatCurrency(realCostHintFor(item.entry.id)!.bruttoCostEur) }}
                        <InformationCircleIcon class="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                      </button>
                    </div>
                    <!-- Aktionen: Bearbeiten direkt, der Rest im Menue. -->
                    <div class="flex items-center justify-end gap-2 pt-1">
                      <button type="button" data-testid="charge-edit" :class="FEED_TEXT_BTN" @click.stop="editingLog = item.entry">
                        <PencilSquareIcon class="h-4 w-4" aria-hidden="true" />{{ t('dashboard.action_edit') }}
                      </button>
                      <FeedActionMenu placement="up" v-slot="{ close }">
                        <FeedMenuItem :icon="MapIcon" @click="startAddTrip(item.entry.id); close()">{{ t('dashboard.action_add_trip') }}</FeedMenuItem>
                        <FeedMenuItem :icon="LinkIcon" @click="openMergeModal(item.entry); close()">{{ t('dashboard.action_merge') }}</FeedMenuItem>
                        <FeedMenuItem v-if="otherCars.length > 0" :icon="ArrowsRightLeftIcon" @click="openReassignModal(item.entry); close()">{{ t('dashboard.action_reassign') }}</FeedMenuItem>
                        <FeedMenuItem :icon="TrashIcon" separated @click="deleteLog(item.entry.id); close()">{{ t('dashboard.action_delete') }}</FeedMenuItem>
                      </FeedActionMenu>
                    </div>
                  </div>
                </Collapse>
                <!-- Erklaerungen zu Hinweisen der Ladung -->
                <div v-if="openTooltipLogId === item.entry.id && (item.entry.consumptionImplausible || (item.entry.consumptionKwhPer100km == null && (isShortTrip(item.entry) || item.entry.kwhCountedInNextConsumption)))"
                  class="mx-4 mb-3">
                  <div v-if="item.entry.consumptionImplausible" :class="NOTE_WARN">
                    <p class="font-semibold">{{ t('dashboard.implausible_tooltip_title') }}</p>
                    <p>{{ t('dashboard.implausible_tooltip_desc', { value: formatConsumption(item.entry.consumptionKwhPer100km) }) }}</p>
                    <ul class="mt-1 list-inside list-disc space-y-0.5">
                      <li>{{ t('dashboard.implausible_tooltip_cause1') }}</li>
                      <li>{{ t('dashboard.implausible_tooltip_cause2') }}</li>
                    </ul>
                  </div>
                  <div v-else-if="isShortTrip(item.entry)" :class="NOTE_INFO">
                    <p class="font-semibold">{{ t('dashboard.short_trip_tooltip_title') }}</p>
                    <p>{{ t('dashboard.short_trip_tooltip_desc') }}</p>
                  </div>
                  <div v-else :class="NOTE_INFO">
                    <p class="font-semibold">{{ t('dashboard.kwh_in_next_tooltip_title') }}</p>
                    <p>{{ t('dashboard.kwh_in_next_tooltip_desc') }}</p>
                  </div>
                </div>
                <div v-if="openRealCostTooltipId === item.entry.id && realCostHintFor(item.entry.id)" class="mx-4 mb-3" :class="NOTE_INFO">
                  <p class="font-semibold">{{ t('dashboard.real_cost_tooltip_title') }}</p>
                  <p>{{ t('dashboard.real_cost_label') }} ≈ {{ formatCurrency(realCostHintFor(item.entry.id)!.bruttoCostEur) }} &middot; {{ formatCostPerKwh(realCostHintFor(item.entry.id)!.bruttoRatePerNettoKwhEur) }}</p>
                  <p>{{ realCostHintFor(item.entry.id)!.source === 'measured-median'
                        ? t('dashboard.real_cost_tooltip_measured', { count: realCostHintFor(item.entry.id)!.sampleSize, pct: realCostHintFor(item.entry.id)!.efficiencyPercent }, realCostHintFor(item.entry.id)!.sampleSize)
                        : t('dashboard.real_cost_tooltip_pauschale', { pct: realCostHintFor(item.entry.id)!.efficiencyPercent }) }}</p>
                </div>
              </div>
              <!-- LADEGRUPPE (Handy): mehrere Teilladungen hintereinander, Antippen klappt sie auf. -->
              <div v-else class="gridfeed:hidden rounded-2xl bg-white edge-card dark:bg-gray-800">
                <FeedEventRow kind="charge" v-bind="logTimeColumn(item.entry)" interactive
                  :expanded="expandedGroups.has(item.entry.id)" @activate="toggleLadegruppe(item.entry.id)">
                  <template #title>
                    <span class="text-[13px] font-semibold text-gray-500 dark:text-gray-400">{{ item.entry._topUps?.length ?? 0 }}&times;</span>
                    <span class="min-w-0 line-clamp-2 break-words">{{ chargePlace(item.entry) }}</span>
                  </template>
                  <span class="block">
                    <b class="font-semibold text-gray-700 dark:text-gray-200">+{{ formatDecimal(Number(item.entry._totalKwh), 1) }} kWh</b>
                    <template v-if="item.entry._dateRangeLabel"> &middot; {{ item.entry._dateRangeLabel }}</template>
                  </span>
                  <template #right>
                    <template v-if="item.entry._totalCostEur != null && item.entry._costBasisKwh && item.entry._isFullyPriced">
                      <b class="block text-[15px] font-bold text-gray-900 dark:text-gray-50"
                        :title="item.entry._costIsNettoOnly ? t('dashboard.cost_pill_netto_title') : undefined">{{ formatCurrency(item.entry._totalCostEur) }}</b>
                      <span class="block text-[12px] font-semibold text-gray-500 dark:text-gray-400">{{ formatCostPerKwh(item.entry._totalCostEur / item.entry._costBasisKwh) }}</span>
                    </template>
                    <!-- Teilweise bepreist: eine Rate aus einem Bruchteil der Energie waere irrefuehrend. -->
                    <button v-else-if="item.entry._pricelessSubs?.length" type="button" data-testid="group-price-chip"
                      :aria-label="t('priceamend.chip_group_aria')" :class="PRICE_CHIP" @click.stop="openGroupPriceAmend(item.entry)">
                      {{ t('priceamend.chip_group') }}
                      <WattBadge :amount="groupPricelessWatt(item.entry)" up-to />
                    </button>
                  </template>
                  <template v-if="otherCars.length > 0" #actions>
                    <FeedActionMenu v-slot="{ close }">
                      <FeedMenuItem :icon="ArrowsRightLeftIcon" @click="openReassignModal(item.entry); close()">{{ t('dashboard.action_reassign') }}</FeedMenuItem>
                    </FeedActionMenu>
                  </template>
                </FeedEventRow>
                    <!-- Fehlender Brutto- oder Nettowert der ganzen Gruppe -->
                <div v-if="item.entry._canEditMissing" class="-mt-1.5 flex flex-wrap items-center gap-x-2 gap-y-0.5 pb-2 pl-[108px] pr-3 sm:pl-[120px] sm:pr-4 text-[12.5px] text-gray-500 dark:text-gray-400" @click.stop @mousedown.stop>
                      <template v-if="grossEditState[item.entry.id] !== undefined">
                        <input type="number" step="0.01" min="0" inputmode="decimal"
                          v-model="grossEditState[item.entry.id]"
                          :aria-label="t(item.entry._missingLabel)" :class="QUICK_INPUT"
                          @click.stop @mousedown.stop @keydown.stop
                          @keydown.enter.stop.prevent="applyGrossTotal(item.entry)"
                          @keydown.escape.stop.prevent="cancelGrossEdit(item.entry.id)" />
                        <span class="whitespace-nowrap">kWh {{ t(item.entry._missingLabel) }}</span>
                        <button type="button" :class="QUICK_BTN" :disabled="grossSaving.has(item.entry.id)" :aria-label="t('common.save')" @click.stop="applyGrossTotal(item.entry, $event)">
                          <ArrowPathIcon v-if="grossSaving.has(item.entry.id)" class="h-4 w-4 animate-spin" aria-hidden="true" />
                          <CheckIcon v-else class="h-4 w-4" aria-hidden="true" />
                        </button>
                        <button type="button" :class="QUICK_BTN" :aria-label="t('common.cancel')" @click.stop="cancelGrossEdit(item.entry.id)">
                          <XMarkIcon class="h-4 w-4" aria-hidden="true" />
                        </button>
                        <span v-if="grossError[item.entry.id]" role="alert" class="text-amber-700 dark:text-amber-400">{{ grossError[item.entry.id] }}</span>
                      </template>
                      <button v-else-if="item.entry._efficiency != null" type="button"
                        class="inline-flex min-h-8 flex-wrap items-center gap-x-1.5 hover:text-gray-900 dark:hover:text-gray-100" @click.stop="startGrossEdit(item.entry, $event)">
                        <span class="whitespace-nowrap">{{ formatDecimal(Number(item.entry._bruttoSum), 1) }} kWh {{ t('dashboard.ac_gross_label_brutto') }}</span>
                        <span class="whitespace-nowrap">&middot; {{ item.entry._efficiency }} % {{ t('dashboard.ac_gross_efficiency') }}</span>
                        <PencilSquareIcon class="h-3.5 w-3.5" aria-hidden="true" />
                      </button>
                      <button v-else-if="item.entry._totalMissingKwh != null" type="button"
                        class="inline-flex min-h-8 items-center gap-1.5 hover:text-gray-900 dark:hover:text-gray-100" @click.stop="startGrossEdit(item.entry, $event)">
                        <span class="whitespace-nowrap">{{ formatDecimal(Number(item.entry._totalMissingKwh), 1) }} kWh {{ t(item.entry._missingLabel) }}</span>
                        <PencilSquareIcon class="h-3.5 w-3.5" aria-hidden="true" />
                      </button>
                      <button v-else type="button" class="inline-flex min-h-8 items-center gap-1 hover:text-gray-900 dark:hover:text-gray-100" @click.stop="startGrossEdit(item.entry, $event)">
                        <PlusIcon class="h-3.5 w-3.5" aria-hidden="true" />{{ t(item.entry._addMissingLabel) }}
                      </button>
                    </div>
                <Collapse :open="expandedGroups.has(item.entry.id)">
                  <div class="px-3 pb-3 sm:px-4">
                    <p v-if="item.entry._totalConsumption != null || item.entry._maxSoc != null || item.entry._maxPower || sourceInfo(item.entry._commonDataSource) || item.entry.distanceSinceLastChargeKm != null || item.entry.odometerKm"
                      class="flex flex-wrap items-center gap-x-3 gap-y-1 pb-2 pl-[96px] sm:pl-[104px] text-[13px] text-gray-600 tabular-nums dark:text-gray-300">
                      <b v-if="item.entry._totalConsumption != null" class="font-semibold"
                        :class="consumptionValueTone(item.entry._totalConsumption)">{{ formatConsumption(item.entry._totalConsumption) }}</b>
                      <span v-if="item.entry._maxSoc != null" class="whitespace-nowrap">{{ t('logs.group_max_soc', { soc: item.entry._maxSoc }) }}</span>
                      <span v-if="item.entry._maxPower" class="whitespace-nowrap">max {{ formatDecimal(Number(item.entry._maxPower), 0) }} kW</span>
                      <button v-if="item.entry.distanceSinceLastChargeKm != null || item.entry.odometerKm" type="button" class="whitespace-nowrap"
                        :class="item.entry.distanceSinceLastChargeKm != null && item.entry.odometerKm ? 'underline decoration-dotted decoration-gray-400 underline-offset-4' : 'cursor-default'"
                        @click.stop="toggleOdometerDisplay(item.entry.distanceSinceLastChargeKm, item.entry.odometerKm)">
                        <template v-if="item.entry.distanceSinceLastChargeKm != null && !showOdometer">+{{ formatDistance(item.entry.distanceSinceLastChargeKm) }}</template>
                        <template v-else>{{ item.entry.odometerKm != null ? formatDistance(item.entry.odometerKm) : '' }}</template>
                      </button>
                      <span v-if="sourceInfo(item.entry._commonDataSource)" class="whitespace-nowrap text-gray-500 dark:text-gray-400">{{ sourceInfo(item.entry._commonDataSource)!.label }}</span>
                    </p>
                    <!-- Teilladungen: Uhrzeit, kWh, Akku, Preis, jede mit eigenem Menue -->
                    <ul class="divide-y divide-gray-100 border-t border-gray-100 dark:divide-gray-700/70 dark:border-gray-700/70">
                      <li v-for="topUp in item.entry._topUps" :key="topUp.id" class="flex items-center gap-2 py-1.5 pl-[96px] sm:pl-[104px] text-[13px] tabular-nums">
                        <span class="min-w-0 flex-1 text-gray-600 dark:text-gray-300">
                          <span class="block">
                            <template v-if="item.entry._spansMultipleDays">{{ formatLogDate(topUp.loggedAt) }}</template>
                            <template v-else>{{ new Date(topUp.loggedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) }}</template>
                            &middot; <b class="font-semibold text-gray-800 dark:text-gray-100">+{{ formatDecimal(Number(topUp.kwhAtVehicle ?? topUp.kwhCharged ?? 0), 1) }} kWh</b>
                          </span>
                          <span class="block text-[12px] text-gray-500 dark:text-gray-400">
                            <template v-if="formatSocRange(topUp.socBeforeChargePercent, topUp.socAfterChargePercent)">{{ formatSocRange(topUp.socBeforeChargePercent, topUp.socAfterChargePercent) }}</template>
                            <template v-if="topUp.chargeDurationMinutes"> &middot; {{ formatPauseDuration(topUp.chargeDurationMinutes) }}</template>
                            <template v-if="topUp.kwhCharged != null && topUp.kwhAtVehicle != null"> &middot; {{ formatDecimal(Number(chargingEfficiency(topUp.kwhCharged, topUp.kwhAtVehicle)), 0) }} % {{ t('dashboard.ac_gross_efficiency') }}</template>
                          </span>
                        </span>
                        <button v-if="(topUp.hasPowerCurve && authStore.canViewLiveAnalytics) || (topUp.hasSocCurve && authStore.canViewSocCurve)" type="button"
                          @click.stop="openPowerCurve(topUp)" :aria-label="t('dashboard.show_power_curve')" aria-haspopup="dialog" :class="CURVE_BTN">
                          <ChartBarSquareIcon class="h-5 w-5" aria-hidden="true" />
                        </button>
                        <span v-if="topUp.costEur != null && costBasisKwh(topUp)" class="shrink-0 text-right">
                          <b class="block font-semibold text-gray-900 dark:text-gray-100">{{ formatCurrency(topUp.costEur) }}</b>
                          <span class="block text-[11.5px] text-gray-500 dark:text-gray-400">{{ formatCostPerKwh(topUp.costEur / costBasisKwh(topUp)!) }}</span>
                        </span>
                        <button v-else-if="costBasisKwh(topUp)" type="button" data-testid="topup-price-chip"
                          :aria-label="t('priceamend.chip_aria')" :class="PRICE_CHIP" @click.stop="priceAmendingLog = topUp">
                          <span class="max-[436px]:hidden">{{ t('priceamend.chip') }}</span>
                          <WattBadge :amount="wattPossibleForLog(coinStore.catalog, topUp)" up-to />
                        </button>
                        <FeedActionMenu placement="up" v-slot="{ close }">
                          <FeedMenuItem :icon="PencilSquareIcon" @click="editingLog = topUp; close()">{{ t('dashboard.action_edit') }}</FeedMenuItem>
                          <FeedMenuItem :icon="LinkIcon" @click="openMergeModal(topUp); close()">{{ t('dashboard.action_merge') }}</FeedMenuItem>
                          <FeedMenuItem v-if="otherCars.length > 0" :icon="ArrowsRightLeftIcon" @click="openReassignModal(topUp); close()">{{ t('dashboard.action_reassign') }}</FeedMenuItem>
                          <FeedMenuItem :icon="TrashIcon" separated @click="deleteLog(topUp.id); close()">{{ t('dashboard.action_delete') }}</FeedMenuItem>
                        </FeedActionMenu>
                      </li>
                    </ul>
                    <!-- Ein Hinweis pro Gruppe statt eines Schlosses je Teilladung -->
                    <button v-if="!authStore.canViewLiveAnalytics && purchasesAvailable() && curveTopUpCount(item.entry) > 0" type="button" aria-haspopup="dialog"
                      class="mt-1 flex min-h-10 w-full items-center gap-1.5 pl-[96px] sm:pl-[104px] text-left text-[13px] font-semibold text-gray-700 dark:text-gray-200"
                      @click.stop="openPowerCurve(firstCurveTopUp(item.entry))">
                      <ChartBarSquareIcon class="h-4 w-4 shrink-0" aria-hidden="true" />
                      <span class="underline decoration-gray-300 underline-offset-4">{{ curveTopUpCount(item.entry) === 1 ? t('dashboard.power_curve_group_locked_one') : t('dashboard.power_curve_group_locked', { count: curveTopUpCount(item.entry) }) }}</span>
                      <LockClosedIcon class="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                    </button>
                  </div>
                </Collapse>
              </div>
              </div><!-- end charge -->
              </template><!-- end v-else regular entry -->

              </template><!-- end v-for groupedFeed -->
            </template>
          </div>
          <!-- Ein Stueck weiter zurueck (so gross wie das Fenster der Darstellung), statt eines festen Seitenendes. -->
          <div v-if="hasAnyLogs" class="mt-5 flex justify-center">
            <button type="button" data-testid="logfeed-load-older" :disabled="logsLoading" @click="loadOlderFeed()" :class="[FEED_TEXT_BTN, 'disabled:opacity-50']">
              <ChevronDownIcon class="h-4 w-4" aria-hidden="true" />
              {{ t('logs.load_older', { range: nextOlderLabel }) }}
            </button>
          </div>

          <!-- Papierkorb: unaufdringlicher Einstieg, die Liste laedt erst beim Oeffnen -->
          <button type="button" data-testid="deleted-logs-row"
            @click="openDeletedSheet"
            class="group mt-6 flex min-h-14 w-full items-center gap-3 rounded-2xl px-3 py-2.5 text-left transition-colors hover:bg-gray-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-story-accent dark:hover:bg-white/5">
            <span class="grid h-8 w-8 shrink-0 place-items-center rounded-full bg-gray-100 text-gray-600 dark:bg-white/10 dark:text-gray-300">
              <TrashIcon class="h-[18px] w-[18px]" aria-hidden="true" />
            </span>
            <span class="min-w-0 flex-1">
              <span class="block text-[14px] font-semibold text-gray-900 dark:text-gray-100">{{ t('logs.trash.row') }}</span>
              <span class="block text-[12.5px] text-gray-500 dark:text-gray-400">{{ t('logs.trash.hint') }}</span>
            </span>
            <ChevronRightIcon class="h-4 w-4 shrink-0 text-gray-400 transition-transform group-hover:translate-x-0.5" aria-hidden="true" />
          </button>

          <!-- Erklaerung der Verbrauchsberechnung als Nachschlagestoff unter der Liste -->
          <ConsumptionInfoBox :min-trips="5" class="mt-2" />
        </div>

        </div>
      </div>
    </Transition>
  </div>

  <!-- Der EditLogModal liegt im CarContextLayout (geteilt mit dem Dashboard) - hier
       wird nur noch `editingLog` gesetzt. -->

  <DeletedLogsSheet
    v-if="showDeletedSheet"
    :logs="deletedLogs.logs.value"
    :busy-id="deletedLogs.busyId.value"
    :loading="deletedLogs.loading.value"
    :failed="deletedLogs.failed.value"
    :format-date="formatLogDate"
    @restore="deletedLogs.restore"
    @purge="deletedLogs.purge"
    @close="showDeletedSheet = false"
  />

  <ImplausibleLogsModal
    :car-id="selectedCarId"
    :open="showImplausibleModal"
    @close="() => { showImplausibleModal = false; if (implausibleModalDirty) { fetchStatistics(); implausibleModalDirty = false } }"
    @updated="() => { fetchImplausibleCount(); implausibleModalDirty = true }"
  />

  <PricelessLogsModal
    :car-id="selectedCarId"
    :open="showPricelessModal"
    @close="showPricelessModal = false"
    @updated="() => { fetchPricelessCount(); refreshLogsAndGroups() }"
  />

  <!-- Fahrzeug-Zuordnung: Sheet mit Kante wie die Story-Sheets (App-CI) -->
  <BottomSheet v-if="reassignModalEntry" ref="reassignSheet" edge :label="t('dashboard.reassign_car')" panel-class="sm:max-w-sm"
    testid="reassign-sheet" @close="reassignModalEntry = null">
    <div class="grid gap-5 p-5">
      <div>
        <h3 class="text-[17px] font-bold text-gray-900 dark:text-gray-50">{{ t('dashboard.reassign_car') }}</h3>
        <p class="mt-1 text-[14px] text-gray-600 dark:text-gray-300">
          <template v-if="reassignModalEntry?._isLadegruppe">{{ t('dashboard.reassign_car_hint_group', { count: reassignModalEntry._topUps?.length ?? 0 }) }}</template>
          <template v-else>{{ t('dashboard.reassign_car_hint') }}</template>
        </p>
      </div>
      <div role="radiogroup" :aria-label="t('dashboard.reassign_car')" class="grid gap-2">
        <button v-for="car in otherCars" :key="car.id" type="button" role="radio" :aria-checked="reassignSelectedCarId === car.id"
          @click="reassignSelectedCarId = car.id; reassignError = null"
          class="flex min-h-12 w-full items-center gap-3 rounded-xl border-2 px-3 text-left transition-colors"
          :class="reassignSelectedCarId === car.id ? 'border-gray-900 bg-gray-50 dark:border-gray-100 dark:bg-white/10' : 'border-gray-200 hover:border-gray-400 dark:border-gray-700 dark:hover:border-gray-500'">
          <TruckIcon class="h-5 w-5 shrink-0 text-gray-500 dark:text-gray-400" aria-hidden="true" />
          <span class="font-semibold text-gray-900 dark:text-gray-100">{{ carDisplayName(car) }}</span>
          <CheckIcon v-if="reassignSelectedCarId === car.id" class="ml-auto h-5 w-5 shrink-0 text-gray-900 dark:text-gray-100" aria-hidden="true" />
        </button>
      </div>
      <p v-if="reassignError" role="alert" class="rounded-xl bg-amber-50 px-3 py-2 text-[14px] text-amber-900 dark:bg-amber-400/10 dark:text-amber-200">{{ reassignError }}</p>
      <div class="flex gap-2">
        <button type="button" class="min-h-12 flex-1 rounded-xl px-3 text-[15px] font-semibold text-gray-600 dark:text-gray-300" @click="reassignSheet?.requestClose()">
          {{ t('common.cancel') }}
        </button>
        <button type="button" :disabled="!reassignSelectedCarId || reassignSaving" @click="doSaveReassign"
          class="btn-3d min-h-12 flex-1 rounded-xl border-2 border-edge bg-green-600 px-3 text-[15px] font-semibold text-white disabled:opacity-40">
          {{ reassignSaving ? t('common.saving') : t('common.save') }}
        </button>
      </div>
    </div>
  </BottomSheet>

  <MergeLogModal
    v-if="mergeModalEntry"
    :entry="mergeModalEntry"
    :candidates="mergeCandidates"
    :saving="mergeSaving"
    :error="mergeError"
    @merge="doSaveMerge"
    @close="mergeModalEntry = null"
  />

  <PowerCurveModal
    v-if="powerCurveEntry"
    :loading="powerCurveLoading.has(powerCurveEntry.id)"
    :points="powerCurveCache.get(powerCurveEntry.id)?.points ?? []"
    :soc-points="powerCurveCache.get(powerCurveEntry.id)?.socPoints ?? []"
    :consumption-kwh-per100km="powerCurveConsumption(powerCurveEntry)"
    :subtitle="powerCurveSubtitle"
    :soc-before-charge-percent="powerCurveEntry.socBeforeChargePercent"
    :soc-after-charge-percent="powerCurveEntry.socAfterChargePercent"
    :kwh-charged="powerCurveEntry.kwhAtVehicle ?? powerCurveEntry.kwhCharged"
    :charge-duration-minutes="powerCurveEntry.chargeDurationMinutes"
    :locked="!authStore.canViewLiveAnalytics"
    :upsell-target="upsellTarget"
    :log-id="powerCurveEntry.id"
    @close="powerCurveEntry = null"
  />

</div>
</template>

<style scoped>
/* Klebender Zeitraum-Kopf: der Schatten deckt die Luecke unter der Ticker-Lasche ab und zieht
   unten eine Haarlinie gegen die durchscrollenden Fahrten. Nur Farbe, kein Layout. */
.period-head-stuck {
  border-radius: 0;
  box-shadow: 0 calc(-1 * var(--ticker-lasche-h, 0px)) 0 0 #fff, 0 1px 0 0 #e5e7eb;
}
.dark .period-head-stuck {
  box-shadow: 0 calc(-1 * var(--ticker-lasche-h, 0px)) 0 0 #1f2937, 0 1px 0 0 #374151;
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
</style>
