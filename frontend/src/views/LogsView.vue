<script setup lang="ts">
import { ref, computed, reactive, onMounted, onUnmounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  TruckIcon,
  MapIcon,
  BoltIcon,
  HeartIcon,
  PencilSquareIcon,
  ClockIcon,
  Battery0Icon,
  SunIcon,
  ChevronDownIcon,
  ChevronUpIcon,
  TrashIcon,
  ExclamationTriangleIcon,
  InformationCircleIcon,
  XMarkIcon,
  ArrowPathIcon,
  ArrowsRightLeftIcon,
  PlusIcon,
  HandThumbUpIcon,
  HandThumbDownIcon,
  EllipsisVerticalIcon,
  ChartBarSquareIcon,
  LockClosedIcon,
  CheckIcon,
  LinkIcon,
} from '@heroicons/vue/24/outline'
import { tempBadgeClass } from '../utils/temperatureColor'
import { consumptionTextClass } from '../utils/consumptionColor'
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
import { formatPauseDuration, tripDayLabel } from '../utils/tripTimeFormat'
import { buildPeriodGroups, type PeriodResolution } from '../utils/tripPeriods'
import PeriodGroupHeader from '../components/dashboard/PeriodGroupHeader.vue'
import PeriodChargeLine from '../components/dashboard/PeriodChargeLine.vue'
import ChargeTypeBadge from '../components/dashboard/ChargeTypeBadge.vue'
import ComparisonChip from '../components/dashboard/ComparisonChip.vue'
import MetricCell from '../components/dashboard/MetricCell.vue'
import { FEED_GRID_COLS } from '../components/dashboard/feedGridCols'
import type { CommunityBenchmark } from '../components/dashboard/PeriodGroupHeader.vue'
import { getModelStatsByEnum, type PublicModelStats } from '../api/publicModelService'
import { useCommunityComparison } from '../composables/useCommunityComparison'
import ConsumptionInfoBox from '../components/dashboard/ConsumptionInfoBox.vue'
import TripForm from '../components/dashboard/TripForm.vue'
import TripClimateMarkers from '../components/TripClimateMarkers.vue'
import TripMapPanel from '../components/dashboard/TripMapPanel.vue'
import { costBadgeClass } from '../utils/costColor'
import LicensePlate from '../components/car/LicensePlate.vue'
import RewardSystemUpdateBanner from '../components/shared/RewardSystemUpdateBanner.vue'
import { useAuthStore } from '../stores/auth'
import ImplausibleLogsModal from '../components/dashboard/ImplausibleLogsModal.vue'
import MergeLogModal from '../components/dashboard/MergeLogModal.vue'
import CarCardDetails from '../components/dashboard/CarCardDetails.vue'
import LogsPaginationBar from '../components/dashboard/LogsPaginationBar.vue'
import { useRoute } from 'vue-router'
import { useLocaleFormat } from '../composables/useLocaleFormat'
import { useCarContext } from '../composables/useCarContext'
import { useVehicleCharging } from '../composables/useVehicleCharging'
import { useBulkBarOffset } from '../composables/useBulkBarOffset'
import { useHaptic } from '../composables/useHaptic'
import { useAnalyticsUpsellTarget } from '../composables/useUpsellTarget'
import { useCountryStore } from '../stores/country'
import { getPricing } from '../config/pricingConfig'
import { carDisplayName } from '../utils/enumLabel'
import { isVwGroupBrand } from '../api/vwGroupService'
import { subscriptionService, type SubscriptionTier } from '../api/subscriptionService'
import {
  computeRealCostHint,
  isNettoOnlyCostLog,
  type RealCostHint,
  type CostHintLog,
} from '../composables/useChargingEfficiency'

const { t, locale } = useI18n()
const { formatConsumption, formatDistance, distanceUnitLabel, formatCurrency, formatCostPerKwh } = useLocaleFormat()
const { haptic } = useHaptic()
const route = useRoute()

// -- Geteilter Auto-Context (State + Polling liegen im CarContextLayout) --
const {
  selectedCarId, stats, loading, isInitialLoad,
  cars, carImageUrls, wltp,
  implausibleBannerDismissed, teslaStatus, smartcarStatus, vwGroupStatus, implausibleCount,
  dismissImplausibleBanner, fetchImplausibleCount, fetchStatistics,
  setLogsSection, currentOdometerKm,
  logs, logsPage, logsLoading, hasMoreLogs, editingLog, pageSize, setPageSize,
  expandedGroups, toggleLadegruppe, hasAnyLogs, showOdometer, showCostAbsolute,
  openTooltipLogId, reassignModalEntry, reassignSelectedCarId, reassignSaving,
  reassignError, reassignSuccessMessage, otherCars, openReassignModal, saveReassign,
  mergeModalEntry, mergeSaving, mergeError, openMergeModal, mergeCandidates, saveMerge,
  fetchLogs, fetchLogsAndScroll, refreshLogsAndGroups, deleteLog,
  formatLogDate, formatTripTimeRange, tripTimeParts, toggleOdometerDisplay, sourceInfo, mergedLogFeed,
  editingTripId, addingTripAfterId, tripForm, tripSaving, tripError,
  startEditTrip, cancelTripEdit, saveTripEdit, startAddTrip, saveNewTrip, deleteTripEntry,
  mergeTripEntry,
  submitTripFeedback,
} = useCarContext()

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
// Fields omitted entirely (loggedAt, latitude, longitude, etc.) are safe to leave out — null keeps existing.
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

const page1GroupIds = computed<Set<string>>(() => {
  if (logsPage.value !== 0) return new Set<string>()
  return new Set(['tg_top', ...logs.value.map((l: any) => `tg_${l.id}`)])
})

function toggleTripGroup(groupId: string) {
  haptic()
  if (isPeriodGroupId(groupId)) {
    const next = new Set(expandedPeriodGroups.value)
    if (next.has(groupId)) next.delete(groupId)
    else next.add(groupId)
    expandedPeriodGroups.value = next
    return
  }
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

/**
 * Aufloesung des Feeds. 'cycle' ist der Ladezyklus wie bisher, alles andere schneidet
 * dieselben Fahrten nach Kalenderzeitraum. Die Wahl haelt ueber Sitzungen hinweg, weil
 * niemand sie bei jedem Besuch neu treffen will.
 */
type FeedResolution = 'cycle' | PeriodResolution
const RESOLUTION_KEY = 'logfeed_resolution'
const RESOLUTIONS: FeedResolution[] = ['day', 'week', 'month', 'cycle']

function loadResolution(): FeedResolution {
  try {
    const saved = localStorage.getItem(RESOLUTION_KEY) as FeedResolution | null
    return saved && RESOLUTIONS.includes(saved) ? saved : 'month'
  } catch {
    return 'month'
  }
}

const feedResolution = ref<FeedResolution>(loadResolution())

watch(feedResolution, (value) => {
  try { localStorage.setItem(RESOLUTION_KEY, value) } catch {}
})

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
  chargeKwhOf: (entry: any) => entry.kwhAtVehicle ?? entry.kwhCharged ?? null,
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
const doSaveMerge = ({ sourceLogId, preferSource }: { sourceLogId: string; preferSource: boolean }) =>
  saveMerge(sourceLogId, preferSource, fetchStatistics)

// -- Trip feedback --
const FEEDBACK_TAGS = ['distance_wrong', 'time_wrong', 'duplicate', 'other'] as const
const openMenuLogId     = ref<string | null>(null)
const openMenuTripId    = ref<string | null>(null)
const openMenuTopUpId   = ref<string | null>(null)
const openMenuGroupId   = ref<string | null>(null)
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

// ESC closes any open action menu - keeps keyboard parity with the click-outside dimmer.
function onMenuKeyEsc(event: KeyboardEvent) {
  if (event.key !== 'Escape') return
  if (openMenuLogId.value || openMenuTripId.value || openMenuTopUpId.value || openMenuGroupId.value) {
    openMenuLogId.value = null
    openMenuTripId.value = null
    openMenuTopUpId.value = null
    openMenuGroupId.value = null
  }
}
onMounted(() => window.addEventListener('keydown', onMenuKeyEsc))
onUnmounted(() => window.removeEventListener('keydown', onMenuKeyEsc))

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

// -- Community-Vergleich: Schnitt der Modellgruppe fuer Chip-Faerbung und Tooltips --
const communityModelStats = ref<PublicModelStats | null>(null)
watch(() => selectedCar.value?.model, async (model) => {
  communityModelStats.value = null
  if (!model) return
  try {
    const stats = await getModelStatsByEnum(model)
    // Unter 5 Logs ist der Schnitt Rauschen - dann lieber keine Einordnung.
    if ((stats?.logCount ?? 0) >= 5) communityModelStats.value = stats
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

const pageDateRange = computed<string | undefined>(() => {
  const feed = mergedLogFeed.value
  if (!feed.length) return undefined
  const dateOf = (e: any): string | null => e._isTrip ? e.tripStartedAt : e.loggedAt
  const first = dateOf(feed[0])
  const last = dateOf(feed[feed.length - 1])
  if (!first || !last) return undefined
  const fmt = (iso: string) => {
    const d = new Date(iso)
    const sameYear = d.getFullYear() === new Date().getFullYear()
    return d.toLocaleDateString('de-DE', { day: 'numeric', month: 'numeric', ...(!sameYear && { year: '2-digit' }) })
  }
  const a = fmt(first)
  const b = fmt(last)
  return a === b ? a : `${a} - ${b}`
})

const { isVehicleCharging, isSmartcarCharging, isWallboxCharging } =
  useVehicleCharging(cars, smartcarStatus, vwGroupStatus)

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

// AutoSync Live discoverability banner: temporarily disabled (flip flag to re-enable).
// Shown to non-Tesla users without Live, dismissible. Position: below ConsumptionInfoBox.
const LIVE_BANNER_ENABLED = false
const LS_LIVE_BANNER_DISMISSED = 'autosync_live_banner_dismissed'
const liveBannerDismissed = ref(localStorage.getItem(LS_LIVE_BANNER_DISMISSED) === 'true')
const showLiveBanner = computed(() =>
  LIVE_BANNER_ENABLED
  && purchasesAvailable()
  && subscriptionTier.value != null
  && subscriptionTier.value !== 'AUTOSYNC_LIVE'
  && selectedCar.value?.brand !== 'TESLA'
  && !liveBannerDismissed.value
)
function dismissLiveBanner() {
  liveBannerDismissed.value = true
  localStorage.setItem(LS_LIVE_BANNER_DISMISSED, 'true')
}

// AutoSync discoverability banner: shown to non-Tesla users without any subscription
// (tier NONE), dismissible. Position: below ConsumptionInfoBox.
const countryStore = useCountryStore()
const autoSyncPrice = computed(() => getPricing(countryStore.country).monthly)
const LS_AUTOSYNC_BANNER_DISMISSED = 'autosync_banner_dismissed'
const autoSyncBannerDismissed = ref(localStorage.getItem(LS_AUTOSYNC_BANNER_DISMISSED) === 'true')
const showAutoSyncBanner = computed(() =>
  purchasesAvailable()
  && subscriptionTier.value === 'NONE'
  && selectedCar.value?.brand !== 'TESLA'
  && !autoSyncBannerDismissed.value
)
function dismissAutoSyncBanner() {
  autoSyncBannerDismissed.value = true
  localStorage.setItem(LS_AUTOSYNC_BANNER_DISMISSED, 'true')
}

// Supporter-Banner: das Tesla-Gegenstueck zum AutoSync-Banner darueber, gleicher Slot,
// gleiches Dismiss-Verhalten. Tesla-Fahrer bekommen die Datenerfassung gratis, AutoSync
// hat ihnen also nichts zu verkaufen - die Auswertungsebene schon. Ohne diesen Banner
// erfahren sie vom Pack nur, wenn sie zufaellig auf ein gesperrtes Widget klicken.
const supporterPrice = computed(() => getPricing(countryStore.country).supporterMonthly)
const LS_SUPPORTER_BANNER_DISMISSED = 'supporter_banner_dismissed'
const supporterBannerDismissed = ref(localStorage.getItem(LS_SUPPORTER_BANNER_DISMISSED) === 'true')
const showSupporterBanner = computed(() =>
  purchasesAvailable()
  && subscriptionTier.value === 'NONE'
  && selectedCar.value?.brand === 'TESLA'
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
  && visibleTripGroups.value.every(g => !isGroupCollapsed(g.groupId)),
)
const allChargesExpanded = computed(() =>
  visibleChargeEntries.value.length > 0
  && visibleChargeEntries.value.every(item =>
    item.entry._isLadegruppe ? expandedGroups.value.has(item.entry.id) : expandedLogs.value.has(item.entry.id),
  ),
)

function toggleAllTrips() {
  const expandAll = !allTripsExpanded.value
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
      const toSave = [...nextCollapsed].filter(id => page1GroupIds.value.has(id))
      localStorage.setItem(tripCollapseKey(selectedCarId.value), JSON.stringify(toSave))
    } catch {}
  }
}

// -- Coordinate FAB position with the sticky bulk-bar (mobile only) --
// Beide Bodies sind im Pager dauerhaft gerendert -> aktiv = die Route zeigt sie.
// Verhindert, dass der Teleport-Footer (Bulk-Bar) im inaktiven Tab sichtbar bleibt.
const viewActive = computed(() => route.name === 'logs')
const bulkBar = ref<HTMLElement | null>(null)
const bulkBarVisible = computed(() =>
  viewActive.value && hasAnyLogs.value && (totalTripCount.value > 0 || chargeCount.value > 0),
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
  <div class="md:max-w-6xl md:mx-auto md:px-6 md:pb-6">
    <RewardSystemUpdateBanner class="mb-4" />
    <Transition name="fade" mode="out-in">
      <div v-if="loading && isInitialLoad" key="skeleton-view">
        <div class="bg-gray-100 dark:bg-gray-800 md:rounded-sm md:shadow-[4px_4px_0_rgba(0,0,0,0.30)] dark:md:shadow-[4px_4px_0_rgba(255,255,255,0.30)] p-2 md:p-6 pb-6 animate-pulse">
          <div class="h-16 rounded-sm bg-gray-200 dark:bg-gray-700 mb-6" />
          <div class="space-y-2">
            <div v-for="n in 6" :key="n" class="relative p-3 border-2 rounded-sm bg-white dark:bg-gray-700 border-gray-200 dark:border-gray-600 shadow-[2px_2px_0_0_#d1d5db] dark:shadow-[2px_2px_0_0_#374151]">
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
        <div class="bg-gray-100 dark:bg-gray-800 md:rounded-sm md:shadow-[4px_4px_0_rgba(0,0,0,0.30)] dark:md:shadow-[4px_4px_0_rgba(255,255,255,0.30)] p-2 md:p-6 pb-6">
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
              ? 'sticky top-16 z-10 bg-white dark:bg-gray-800 md:-mx-6 md:px-6 md:py-3 mb-3 border-b border-gray-100 dark:border-gray-700 shadow-sm'
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
                    ? 'flex items-start md:items-stretch rounded-sm border-2 text-left transition w-full md:w-auto overflow-hidden'
                    : 'flex items-center md:items-stretch rounded-sm border-2 text-left transition flex-shrink-0 min-w-[180px] max-w-[240px] lg:flex-shrink lg:min-w-0 lg:max-w-none overflow-hidden',
                  selectedCarId === car.id
                    ? isVehicleCharging(car)
                      ? 'border-2 border-green-500 bg-green-50 dark:bg-green-900/20 shadow-[2px_2px_0_0_#16a34a] dark:shadow-[2px_2px_0_0_#14532d]'
                      : 'border-2 border-indigo-500 bg-indigo-50 dark:bg-indigo-900/30 shadow-[2px_2px_0_0_#4338ca] dark:shadow-[2px_2px_0_0_#312e81]'
                    : isVehicleCharging(car)
                      ? 'border-2 border-green-300 dark:border-green-700 bg-white dark:bg-gray-700 shadow-[2px_2px_0_0_#d1d5db] dark:shadow-[2px_2px_0_0_#374151]'
                      : 'border-2 border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-700 shadow-[2px_2px_0_0_#d1d5db] dark:shadow-[2px_2px_0_0_#374151] hover:border-indigo-300',
                  cars.length > 1 && isVehicleCharging(car) ? 'ring-2 ring-green-400 dark:ring-green-500' : '',
                ]" style="transition: transform 0.075s ease, box-shadow 0.075s ease;">
                <div
                  :class="[
                    'flex-shrink-0 bg-gray-100 dark:bg-gray-600 flex items-center justify-center overflow-hidden',
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
                  <!-- Tablet (768-1023px): zusätzliche Auto-Daten + Kennzeichen (single-car, spiegelt Desktop) -->
                  <div
                    v-if="cars.length === 1 && car.id === selectedCarId"
                    class="lg:hidden mt-2 pt-2 border-t border-gray-200 dark:border-gray-600">
                    <CarCardDetails :car="car" :wltp="wltp" :current-odometer-km="currentOdometerKm" orientation="compact" />
                  </div>
                  <!-- Desktop: zweizeiliges Layout. Ab md sichtbar - die Kachel passt mit
                       vollem Inhalt auch in den schmalsten Desktop-Viewport; vorher fiel sie
                       unterhalb von lg auf das blosse Bild zusammen. -->
                  <div class="hidden md:block">
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
                <!-- Desktop horizontal extension (single-car only, ab md) -->
                <div
                  v-if="cars.length === 1 && car.id === selectedCarId"
                  class="hidden md:flex flex-shrink-0 self-stretch items-center border-l border-gray-200 dark:border-gray-600 pl-3 pr-3 py-2"
                >
                  <CarCardDetails :car="car" :wltp="wltp" :current-odometer-km="currentOdometerKm" orientation="horizontal" />
                </div>
              </button>
              </div>
          </div>

        <!-- Log List -->
        <div :ref="setLogsSection" class="pt-3 scroll-mt-4"
          :style="{ paddingBottom: `calc(var(--bulk-bar-offset, 0px) + 1.5rem)` }">
          <!-- AutoSync Live discoverability hint (Tesla-users without Live, dismissible) -->
          <div v-if="showLiveBanner"
            class="w-full flex items-center justify-between gap-2 px-3 py-2 mb-4 rounded-sm border-l-2 border-indigo-400 bg-indigo-500/15">
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

          <!-- AutoSync discoverability hint (free users without subscription, non-Tesla, dismissible) -->
          <div v-if="showAutoSyncBanner"
            class="w-full flex items-center justify-between gap-3 px-3 py-2.5 mb-4 rounded-sm border-2 border-indigo-300 dark:border-indigo-500/40 bg-indigo-50 dark:bg-indigo-900/20 shadow-[2px_2px_0_0_#c7d2fe] dark:shadow-[2px_2px_0_0_#312e81]">
            <div class="flex items-center gap-2 min-w-0">
              <span class="text-[10px] px-1.5 py-0.5 rounded bg-indigo-600 text-white font-semibold tracking-wide uppercase flex-shrink-0">{{ t('dashboard.autosync_banner_new_chip') }}</span>
              <ArrowPathIcon class="w-4 h-4 text-indigo-600 dark:text-indigo-300 flex-shrink-0" aria-hidden="true" />
              <p class="text-xs text-gray-700 dark:text-gray-200 leading-snug">
                {{ t('dashboard.autosync_banner_text_prefix') }}
                <span class="font-semibold text-indigo-700 dark:text-indigo-300">AutoSync</span>
                {{ t('dashboard.autosync_banner_text_suffix') }}
                <span class="font-semibold text-indigo-700 dark:text-indigo-300 whitespace-nowrap">{{ t('dashboard.autosync_banner_price', { price: autoSyncPrice }) }}</span>
              </p>
            </div>
            <div class="flex items-center gap-1.5 flex-shrink-0">
              <router-link to="/upgrade"
                class="inline-flex items-center gap-1 px-3 py-1.5 rounded-sm bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-semibold whitespace-nowrap transition focus:outline-none focus-visible:ring-2 focus-visible:ring-indigo-400">
                {{ t('dashboard.autosync_banner_cta') }}
              </router-link>
              <button type="button" @click="dismissAutoSyncBanner"
                class="p-0.5 rounded hover:bg-indigo-500/20 dark:hover:bg-indigo-500/30 transition focus:outline-none focus-visible:ring-2 focus-visible:ring-indigo-400"
                :aria-label="t('dashboard.autosync_banner_dismiss')">
                <XMarkIcon class="w-3.5 h-3.5 text-gray-500 dark:text-gray-400" aria-hidden="true" />
              </button>
            </div>
          </div>

          <!-- Supporter hint (free Tesla drivers - their data already flows, only the analysis is missing) -->
          <div v-if="showSupporterBanner"
            class="w-full flex items-center justify-between gap-3 px-3 py-2.5 mb-4 rounded-sm border-2 border-amber-300 dark:border-amber-500/40 bg-amber-50 dark:bg-amber-900/20 shadow-[2px_2px_0_0_#fde68a] dark:shadow-[2px_2px_0_0_#78350f]">
            <div class="flex items-center gap-2 min-w-0">
              <span class="text-[10px] px-1.5 py-0.5 rounded bg-amber-600 text-white font-semibold tracking-wide uppercase flex-shrink-0">{{ t('dashboard.supporter_banner_new_chip') }}</span>
              <HeartIcon class="w-4 h-4 text-amber-600 dark:text-amber-300 flex-shrink-0" aria-hidden="true" />
              <p class="text-xs text-gray-700 dark:text-gray-200 leading-snug">
                {{ t('dashboard.supporter_banner_text_prefix') }}
                <span class="font-semibold text-amber-700 dark:text-amber-300">{{ t('dashboard.supporter_banner_pack') }}</span>
                {{ t('dashboard.supporter_banner_text_suffix') }}
                <span class="font-semibold text-amber-700 dark:text-amber-300 whitespace-nowrap">{{ t('dashboard.supporter_banner_price', { price: supporterPrice }) }}</span>
              </p>
            </div>
            <div class="flex items-center gap-1.5 flex-shrink-0">
              <router-link to="/supporter"
                class="inline-flex items-center gap-1 px-3 py-1.5 rounded-sm bg-amber-600 hover:bg-amber-500 text-white text-xs font-semibold whitespace-nowrap transition focus:outline-none focus-visible:ring-2 focus-visible:ring-amber-400">
                {{ t('dashboard.supporter_banner_cta') }}
              </router-link>
              <button type="button" @click="dismissSupporterBanner"
                class="p-0.5 rounded hover:bg-amber-500/20 dark:hover:bg-amber-500/30 transition focus:outline-none focus-visible:ring-2 focus-visible:ring-amber-400"
                :aria-label="t('dashboard.supporter_banner_dismiss')">
                <XMarkIcon class="w-3.5 h-3.5 text-gray-500 dark:text-gray-400" aria-hidden="true" />
              </button>
            </div>
          </div>

          <!-- Cost-reuse tip (shown once, dismissible via localStorage) -->
          <div v-if="showCostTip"
            class="w-full flex items-center justify-between gap-2 px-3 py-2 mb-4 rounded-sm border-l-2 border-blue-400 bg-blue-500/10">
            <div class="flex items-start gap-2 min-w-0">
              <InformationCircleIcon class="w-3.5 h-3.5 text-blue-500 dark:text-blue-400 flex-shrink-0 mt-0.5" aria-hidden="true" />
              <p class="text-xs text-gray-700 dark:text-gray-300 leading-snug">
                <i18n-t keypath="dashboard.cost_reuse_tip_text" tag="span">
                  <template #settings>
                    <router-link to="/settings" class="underline text-blue-700 dark:text-blue-300 hover:text-blue-500">
                      {{ t('dashboard.cost_reuse_tip_settings') }}
                    </router-link>
                  </template>
                </i18n-t>
              </p>
            </div>
            <button type="button" @click="dismissCostTip"
              class="flex-shrink-0 p-0.5 rounded hover:bg-blue-500/20 transition focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-400"
              :aria-label="t('dashboard.cost_reuse_tip_dismiss')">
              <XMarkIcon class="w-3.5 h-3.5 text-gray-500 dark:text-gray-400" aria-hidden="true" />
            </button>
          </div>

          <!-- Implausible logs banner (position 2: under ConsumptionInfoBox) -->
          <div v-if="implausibleCount > 0 && !implausibleBannerDismissed"
            class="w-full mb-4 flex items-center gap-3 px-4 py-3 rounded-sm bg-amber-200 dark:bg-amber-500/20 border border-amber-300 dark:border-amber-600/50">
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

          <!-- Pagination top (Header-Variante: Zeitraum-Label, keine Seitengroesse) -->
          <LogsPaginationBar
            v-if="hasAnyLogs"
            variant="header"
            :page="logsPage"
            :has-more="hasMoreLogs"
            :page-size="pageSize"
            class="mb-4"
            :date-range="pageDateRange"
            @prev="fetchLogs(logsPage - 1)"
            @next="fetchLogs(logsPage + 1)"
            @page-size-change="setPageSize"
          />

          <Transition enter-active-class="transition duration-200 ease-out" enter-from-class="opacity-0 -translate-y-1" enter-to-class="opacity-100 translate-y-0" leave-active-class="transition duration-150 ease-in" leave-from-class="opacity-100" leave-to-class="opacity-0">
            <div v-if="reassignSuccessMessage" class="mb-2 px-3 py-2 rounded-sm bg-green-50 dark:bg-green-900/30 border border-green-200 dark:border-green-700 text-sm text-green-700 dark:text-green-300 flex items-center gap-2">
              <ArrowsRightLeftIcon class="w-4 h-4 flex-shrink-0" />
              {{ reassignSuccessMessage }}
            </div>
          </Transition>

          <!-- Aufloesung des Feeds: Ladezyklus wie bisher, oder nach Kalenderzeitraum.
               Voll ausgeschriebene Segmente statt eines Menues - die vier Optionen passen
               nebeneinander und eine Auswahl, die man sieht, muss man nicht suchen. -->
          <div v-if="hasAnyLogs" role="group" :aria-label="t('logs.resolution.label')"
               class="mb-3 inline-flex w-full sm:w-auto p-0.5 rounded-full bg-gray-100 dark:bg-gray-700/60 border border-gray-200 dark:border-gray-600">
            <button v-for="option in RESOLUTIONS" :key="option" type="button"
                    @click="feedResolution = option" :aria-pressed="feedResolution === option"
                    :class="['flex-1 sm:flex-none px-3.5 py-1.5 rounded-full text-xs font-medium whitespace-nowrap transition focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-400',
                      feedResolution === option
                        ? 'bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 shadow-sm'
                        : 'text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-200']">
              {{ t('logs.resolution.' + option) }}
            </button>
          </div>

          <div :class="['space-y-2', { 'opacity-50 pointer-events-none transition-opacity duration-150': logsLoading && hasAnyLogs }]">
            <template v-if="logsLoading && !hasAnyLogs">
              <div v-for="n in 5" :key="n" class="relative p-3 border-2 rounded-sm bg-white dark:bg-gray-700 border-gray-200 dark:border-gray-600 shadow-[2px_2px_0_0_#d1d5db] dark:shadow-[2px_2px_0_0_#374151] animate-pulse">
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
            <template v-else-if="!hasAnyLogs">
              <p class="py-8 text-center text-gray-400 text-sm">{{ t('dashboard.no_logs_empty') }}</p>
            </template>
            <template v-else>
              <div v-if="openMenuLogId" class="fixed inset-0 z-40" @click="openMenuLogId = null" />
              <div v-if="openMenuTripId" class="fixed inset-0 z-40" @click="openMenuTripId = null" />
              <div v-if="openMenuTopUpId" class="fixed inset-0 z-40" @click="openMenuTopUpId = null" />
              <div v-if="openMenuGroupId" class="fixed inset-0 z-40" @click="openMenuGroupId = null" />
              <!-- Backdrop nur fuer Desktop-Popover (mobile Tooltip ist Teil der Expanded-Card). -->
              <div v-if="openRealCostTooltipId?.endsWith('__d')" class="fixed inset-0 z-40" @click="openRealCostTooltipId = null" />

              <template v-for="item in groupedFeed" :key="item.id">

              <!-- ===== TRIP GROUP CONTAINER ===== -->
              <template v-if="item.kind === 'tripGroup'">
                <div class="gridfeed:hidden rounded-sm border-2 border-emerald-300 dark:border-emerald-800/60 border-l-4 border-r-4 border-l-emerald-400 dark:border-l-emerald-500 border-r-emerald-400 dark:border-r-emerald-500 shadow-[2px_2px_0_0_#d1d5db] dark:shadow-[2px_2px_0_0_#374151]">

                  <!-- Kopf des Zeitraums - Tag, Woche oder Monat -->
                  <button v-if="item.period" type="button" @click="toggleTripGroup(item.groupId)"
                          :aria-expanded="!isGroupCollapsed(item.groupId)"
                          :ref="(el) => setPeriodHeaderRef(item.groupId, 'm', el)" :style="stickyHeaderStyle"
                          class="w-full text-left sticky z-[3] bg-white dark:bg-gray-700 hover:bg-gray-50 dark:hover:bg-gray-600/60 transition-colors border-b border-gray-100 dark:border-gray-600">
                    <PeriodGroupHeader :group="item.period" :expanded="!isGroupCollapsed(item.groupId)" compact
                                       :community="communityBenchmark" :phantom-kwh="visiblePhantomTotal(item)" />
                  </button>

                  <!-- Group header -->
                  <div v-else @click="toggleTripGroup(item.groupId)"
                       class="flex flex-col px-3 py-2.5 bg-white dark:bg-gray-700 cursor-pointer select-none hover:bg-gray-50 dark:hover:bg-gray-600/60 transition-colors border-b border-gray-100 dark:border-gray-600">
                    <!-- Header row -->
                    <div class="flex items-center gap-2">
                      <div class="flex-1 flex items-center justify-center gap-1.5 text-xs font-semibold text-emerald-600 dark:text-emerald-400">
                        <MapIcon class="w-3.5 h-3.5 shrink-0" />
                        {{ t('dashboard.trip_group_count', { count: item.groupSize }, item.groupSize) }}
                        <span v-if="item.totalKm" class="font-normal text-gray-500 dark:text-gray-400 whitespace-nowrap">&middot; {{ formatDistance(item.totalKm) }}</span>
                        <span v-if="item.dateRange" class="font-normal text-gray-500 dark:text-gray-400 whitespace-nowrap">&middot; {{ item.dateRange }}</span>
                        <span v-if="visiblePhantomTotal(item)" class="font-normal text-amber-500 dark:text-amber-500 inline-flex items-center gap-0.5 whitespace-nowrap shrink-0">&middot; <BoltIcon class="w-2.5 h-2.5" />{{ item.totalPhantomKwh.toFixed(1) }} kWh<span class="hidden sm:inline">&nbsp;{{ t('dashboard.phantom_drain_word') }}</span></span>
                      </div>
                      <ChevronUpIcon v-if="!isGroupCollapsed(item.groupId)" class="w-4 h-4 text-emerald-500 shrink-0" />
                      <ChevronDownIcon v-else class="w-4 h-4 text-emerald-500 shrink-0" />
                    </div>
                    <!-- Die Bilanz des Ladezyklus - was zwischen zwei Ladungen verbraucht wurde.
                         Stand bisher nur in der breiten Ansicht und blieb damit fuer die
                         Mehrheit der Nutzer unsichtbar. -->
                    <div v-if="tripGroupConsumedKwh(item) != null || tripGroupSocBoundaries(item)"
                         class="mt-1 flex items-center justify-center gap-x-2.5 gap-y-0.5 flex-wrap text-[11px] tabular-nums">
                      <span v-if="tripGroupConsumedKwh(item) != null" class="font-semibold text-rose-500 dark:text-rose-300">
                        &minus;{{ tripGroupConsumedKwh(item)!.toFixed(2) }} kWh
                      </span>
                      <span v-if="tripGroupSocBoundaries(item)" class="text-gray-500 dark:text-gray-400">
                        {{ tripGroupSocBoundaries(item)!.start }}&nbsp;&rarr;&nbsp;{{ tripGroupSocBoundaries(item)!.end }}&nbsp;%
                      </span>
                      <span v-if="tripGroupCostPer100km(item) != null" class="text-emerald-600 dark:text-emerald-400">
                        {{ formatCurrency(tripGroupCostPer100km(item)!) }}/100km
                      </span>
                    </div>
                  </div>

                  <!-- Trips (expanded, animated) -->
                  <Transition :css="false" @enter="onTripFormEnter" @after-enter="onTripFormAfterEnter" @leave="onTripFormLeave">
                  <div v-if="!isGroupCollapsed(item.groupId)">
                    <template v-for="day in item.days" :key="day.dateKey">
                    <!-- Datumsband: bleibt beim Scrollen stehen, damit der Tag waehrend des
                         Lesens sichtbar bleibt. Nur exakt gezaehlte Werte, keine Schaetzung. -->
                    <div v-if="item.days.length > 1" :style="dayBandStyle(item.groupId)" class="sticky z-[2] flex items-center justify-between gap-2 px-3 py-2
                                bg-emerald-100 dark:bg-emerald-950 border-y border-emerald-300/70 dark:border-emerald-800/50">
                      <span class="text-[13px] font-bold text-gray-900 dark:text-gray-100">{{ dayLabel(day.dateKey) }}</span>
                      <span class="text-[11px] text-gray-500 dark:text-gray-400 tabular-nums">
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
                           class="px-3 py-3 bg-white dark:bg-gray-700 border-t border-gray-100 dark:border-gray-600">
                        <TripForm v-model="tripForm" mode="add"
                          :error="tripError" :saving="tripSaving" :distance-unit="distanceUnitLabel()"
                          @save="saveNewTrip()" @cancel="cancelTripEdit()" />
                      </div>
                      </Transition>

                      <!-- Trip display mode. Die Karte klappt per Klick auf die ganze Zeile auf -
                           .self bei den Tasten, damit Tippen im Feedback-Textfeld nicht toggelt. -->
                      <Transition :css="false" @enter="onTripFormEnter" @after-enter="onTripFormAfterEnter" @leave="onTripFormLeave">
                      <div v-if="editingTripId !== trip.id && deletingTripId !== trip.id"
                           class="px-3 py-3 bg-emerald-50/60 dark:bg-gray-700 space-y-2 border-t border-emerald-200/60 dark:border-gray-600 cursor-pointer"
                           role="button" tabindex="0"
                           :aria-expanded="expandedTripMaps.has(trip.id)"
                           :aria-controls="'trip-details-' + trip.id"
                           @click="toggleTripMap(trip.id)"
                           @keydown.enter.self.prevent="toggleTripMap(trip.id)"
                           @keydown.space.self.prevent="toggleTripMap(trip.id)">
                    <!-- Kopfzeile: wann + wie weit. Das Wann fuehrt, weil man eine Fahrt
                         ueber ihren Zeitpunkt wiederfindet, nicht ueber ihre Laenge. -->
                    <div class="flex items-center justify-between gap-2">
                      <span class="inline-flex items-center gap-2 min-w-0 flex-wrap">
                        <span class="inline-flex items-center gap-1.5 min-w-0">
                          <MapIcon :class="['w-4 h-4 flex-shrink-0 self-center',
                            isAdmin && trip.dataSource === 'TESLA_LIVE'    ? 'text-red-500 dark:text-red-400' :
                            isAdmin && trip.dataSource === 'SMARTCAR_LIVE' ? 'text-blue-500 dark:text-blue-400' :
                            'text-emerald-600 dark:text-emerald-400']" />
                          <span class="text-[15px] text-gray-900 dark:text-gray-100 whitespace-nowrap">
                            {{ tripTimeParts(trip.tripStartedAt, trip.tripEndedAt).time }}
                          </span>
                        </span>
                        <span v-if="trip.distanceKm != null" class="font-semibold text-emerald-700 dark:text-emerald-400 whitespace-nowrap">{{ formatDistance(trip.distanceKm, { round: false }) }}</span>
                        <span v-if="trip.dataSource === 'USER_CREATED'"
                          class="inline-flex items-center px-2 py-0.5 bg-gray-50 dark:bg-gray-600 border border-gray-200 dark:border-gray-500 rounded-full text-xs text-gray-400 whitespace-nowrap">
                          {{ t('dashboard.trip_manual') }}
                        </span>
                      </span>
                      <div class="flex items-center gap-1.5 flex-shrink-0">
                        <span v-if="trip.outsideTempCelsius != null"
                          :class="['inline-flex items-center gap-0.5 px-2 py-0.5 border rounded text-xs whitespace-nowrap', tempBadgeClass(trip.outsideTempCelsius)]">
                          <SunIcon class="w-3 h-3" />{{ trip.outsideTempCelsius }}°C
                        </span>
                        <!-- Einzige Affordance im Kopf: der Klick liegt auf der Zeile, die
                             Aktionen wohnen im ausgeklappten Bereich - zwei Touch-Ziele
                             direkt nebeneinander waren auf Mobile zu fehleranfaellig. -->
                        <ChevronDownIcon
                          :class="['w-4 h-4 text-gray-400 transition-transform duration-200', expandedTripMaps.has(trip.id) ? 'rotate-180' : '']"
                          aria-hidden="true" />
                      </div>
                    </div>
                    <!-- Metrics: 2-col grid, full width -->
                    <div class="grid grid-cols-2 gap-x-4 gap-y-1 text-[13px]">
                      <MetricCell v-if="tripConsumption(trip)" emphasized>
                        {{ tripConsumption(trip)!.estimated ? '~' : '' }}{{ formatConsumption(tripConsumption(trip)!.kwhPer100km) }}
                      </MetricCell>
                      <MetricCell v-if="trip.maxSpeedKmh != null">
                        {{ t('dashboard.trip_speed_summary', { avg: Math.round(Number(trip.avgSpeedKmh)), max: Math.round(Number(trip.maxSpeedKmh)) }) }}
                      </MetricCell>
                      <MetricCell v-if="trip.socStart != null && trip.socEnd != null">
                        {{ trip.socStart }}% → {{ trip.socEnd }}%
                      </MetricCell>
                      <MetricCell v-if="trip.routeType">
                        {{ t('dashboard.trip_route_' + trip.routeType.toLowerCase()) }}
                      </MetricCell>
                    </div>
                    <TripClimateMarkers :climate="trip.climate" />
                    <Transition :css="false" @enter="onTripFormEnter" @after-enter="onTripFormAfterEnter" @leave="onTripFormLeave">
                      <div v-if="expandedTripMaps.has(trip.id)" :id="'trip-details-' + trip.id">
                        <TripMapPanel v-if="hasTripMap(trip)" :trip="trip" :panel-id="'trip-map-' + trip.id" />
                        <!-- Aktionsleiste: ersetzt das Kebab-Menue aus dem Kopf. @click.stop,
                             damit ein Tipp auf eine Aktion die Karte nicht wieder zuklappt. -->
                        <div class="flex items-center gap-1 pt-2" @click.stop>
                          <template v-if="trip.dataSource !== 'USER_CREATED'">
                            <button type="button" @click="toggleRating(trip.id, 'positive', trip.feedback)"
                              :aria-label="t('dashboard.trip_feedback_positive')" :title="t('dashboard.trip_feedback_positive')"
                              :class="['p-2 rounded transition hover:bg-gray-100 dark:hover:bg-gray-600',
                                effectiveRating(trip.id, trip.feedback) === 'positive' ? 'text-emerald-600 dark:text-emerald-400' : 'text-gray-400 hover:text-gray-600 dark:hover:text-gray-200']">
                              <HandThumbUpIcon class="w-4 h-4" />
                            </button>
                            <button type="button" @click="toggleRating(trip.id, 'negative', trip.feedback)"
                              :aria-label="t('dashboard.trip_feedback_negative')" :title="t('dashboard.trip_feedback_negative')"
                              :class="['p-2 rounded transition hover:bg-gray-100 dark:hover:bg-gray-600',
                                effectiveRating(trip.id, trip.feedback) === 'negative' ? 'text-red-600 dark:text-red-400' : 'text-gray-400 hover:text-gray-600 dark:hover:text-gray-200']">
                              <HandThumbDownIcon class="w-4 h-4" />
                            </button>
                          </template>
                          <span class="flex-1" />
                          <button type="button" @click="startAddTrip(trip.id, trip.tripStartedAt)"
                            :aria-label="t('dashboard.action_add_trip')" :title="t('dashboard.action_add_trip')"
                            class="p-2 rounded text-gray-400 hover:text-gray-600 dark:hover:text-gray-200 hover:bg-gray-100 dark:hover:bg-gray-600 transition">
                            <PlusIcon class="w-4 h-4" />
                          </button>
                          <button type="button" @click="startEditTrip(trip)"
                            :aria-label="t('dashboard.trip_edit')" :title="t('dashboard.trip_edit')"
                            class="p-2 rounded text-gray-400 hover:text-gray-600 dark:hover:text-gray-200 hover:bg-gray-100 dark:hover:bg-gray-600 transition">
                            <PencilSquareIcon class="w-4 h-4" />
                          </button>
                          <button type="button" @click="handleDeleteTrip(trip.id)"
                            :aria-label="t('dashboard.action_delete')" :title="t('dashboard.action_delete')"
                            class="p-2 rounded text-red-500 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20 transition">
                            <TrashIcon class="w-4 h-4" />
                          </button>
                        </div>
                      </div>
                    </Transition>
                    <!-- Feedback panel - @click.stop, sonst klappt jeder Tipp ins Formular die Karte um. -->
                    <div v-if="feedbackOpenId === trip.id" @click.stop
                         class="pt-1 space-y-2 border-t border-gray-100 dark:border-gray-600">
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
                        class="w-full px-2 py-1.5 text-xs border border-gray-200 dark:border-gray-600 rounded-sm bg-white dark:bg-gray-800 text-gray-700 dark:text-gray-300 placeholder-gray-400 focus:outline-none focus:ring-1 focus:ring-red-400 resize-none" />
                      <div class="flex justify-end">
                        <button @click="sendNegativeFeedback(trip.id)"
                          class="px-3 py-1 text-xs font-medium bg-red-500 hover:bg-red-600 text-white rounded-sm transition">
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
                        class="flex-1 px-2 py-1 text-xs border border-gray-200 dark:border-gray-600 rounded-sm bg-white dark:bg-gray-800 text-gray-700 dark:text-gray-300 placeholder-gray-400 focus:outline-none focus:ring-1 focus:ring-red-400" />
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
                          class="px-3 py-1 text-xs font-medium bg-orange-500 hover:bg-orange-600 text-white rounded-sm disabled:opacity-50 transition">
                          {{ t('dashboard.trip_merge_confirm') }}
                        </button>
                        <button @click="mergePreviewForTripId = null"
                          class="px-3 py-1 text-xs font-medium bg-white dark:bg-gray-700 border border-gray-200 dark:border-gray-600 text-gray-600 dark:text-gray-300 rounded-sm hover:bg-gray-50 dark:hover:bg-gray-600 transition">
                          {{ t('dashboard.trip_cancel') }}
                        </button>
                      </div>
                    </div>
                  </template>
                    </template>
                  </TripForm>
                  </div>
                  </Transition>


                      <!-- Was zwischen dieser und der naechstfrueheren Fahrt liegt, steht
                           unter ihr - dort, wo die Liste (neueste zuerst) die Luecke zeigt.
                           Erst die Ruhe, dann was sie gekostet hat; ohne ausgewiesenen
                           Standverlust bleibt die Dauer allein stehen. -->
                      <div v-if="isRestBreak(day.trips, tripIdx as number) && !showsDrain(trip)"
                           class="flex items-center justify-center py-2">
                        <span class="text-[11px] text-gray-400 dark:text-gray-500">
                          {{ t('dashboard.trip_pause', { duration: formatPauseDuration(pauseBeforeTripMinutes(day.trips, tripIdx as number)) }) }}
                        </span>
                      </div>

                      <!-- Phantom drain separator between trips -->
                      <div v-if="showsDrain(trip)"
                           class="flex items-center justify-center gap-1 py-1.5 border-t border-gray-600/50">
                        <BoltIcon class="w-2.5 h-2.5 text-amber-600 dark:text-amber-700" />
                        <span class="text-[11px] text-amber-600 dark:text-amber-700">
                          <span v-if="drainPauseMinutes(trip)" class="text-gray-400 dark:text-gray-500">{{ formatPauseDuration(drainPauseMinutes(trip)) }} &middot; </span>
                          {{ trip._phantomDrain.kwh.toFixed(2) }} kWh
                          <template v-if="selectedCar?.effectiveBatteryCapacityKwh">({{ (trip._phantomDrain.kwh / selectedCar.effectiveBatteryCapacityKwh * 100).toFixed(1) }}%)</template>
                          {{ t('dashboard.phantom_drain_word') }}
                          <span v-if="phantomDrainEur(trip._phantomDrain) != null" class="opacity-80">· ≈ {{ formatCurrency(phantomDrainEur(trip._phantomDrain)!) }}</span>
                        </span>
                        <router-link v-if="!authStore.canViewLiveAnalytics && purchasesAvailable()" :to="upsellTarget" class="text-[11px] font-semibold text-amber-600 dark:text-amber-400 underline decoration-dotted hover:decoration-solid">{{ t('dashboard.phantom_teaser_unlock') }}</router-link>
                      </div>

                    </template>
                    </template><!-- end v-else trip -->
                    </template><!-- end v-for events -->
                    </template><!-- end v-for days -->
                  </div><!-- end expanded trips -->
                  </Transition>
                </div><!-- end trip group container (mobile) -->

                <!-- DESKTOP TRIP GROUP -->
                <div class="hidden gridfeed:block rounded-sm border-2 border-emerald-300 dark:border-emerald-800/50 border-l-4 border-l-emerald-400 dark:border-l-emerald-500 shadow-[2px_2px_0_0_#d1d5db] dark:shadow-[2px_2px_0_0_#374151]">
                  <!-- Kopf des Zeitraums - Tag, Woche oder Monat. Neutraler Hintergrund wie
                       auf Mobile; deckend, weil unter dem sticky Kopf Zeilen durchscrollen. -->
                  <button v-if="item.period" type="button" @click="toggleTripGroup(item.groupId)"
                    :aria-expanded="!isGroupCollapsed(item.groupId)"
                    :ref="(el) => setPeriodHeaderRef(item.groupId, 'd', el)" :style="stickyHeaderStyle"
                    class="w-full text-left sticky z-[3] bg-white dark:bg-gray-700 hover:bg-gray-50 dark:hover:bg-gray-600/60 border-b border-gray-100 dark:border-gray-600 transition focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-400 focus-visible:ring-inset">
                    <PeriodGroupHeader :group="item.period" :expanded="!isGroupCollapsed(item.groupId)"
                                       :community="communityBenchmark" :phantom-kwh="visiblePhantomTotal(item)" />
                  </button>

                  <!-- Header as grid row -->
                  <button v-else type="button" @click="toggleTripGroup(item.groupId)"
                    :aria-expanded="!isGroupCollapsed(item.groupId)"
                    :aria-label="t('dashboard.trip_group_count', { count: item.groupSize }, item.groupSize)"
                    :class="[FEED_GRID_COLS, 'w-full items-center px-3 py-2 bg-emerald-50/60 dark:bg-emerald-900/15 hover:bg-emerald-50 dark:hover:bg-emerald-900/25 transition text-left focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-400 focus-visible:ring-inset']">
                    <div class="flex items-center gap-1.5">
                      <MapIcon class="w-4 h-4 text-emerald-600 dark:text-emerald-400 flex-shrink-0" />
                      <span class="text-[10px] px-1.5 py-0.5 rounded bg-emerald-100 dark:bg-emerald-900/40 text-emerald-700 dark:text-emerald-300 font-medium">{{ item.groupSize }}×</span>
                    </div>
                    <div class="font-semibold text-rose-500 dark:text-rose-300 whitespace-nowrap text-sm">
                      <template v-if="tripGroupConsumedKwh(item) != null">−{{ tripGroupConsumedKwh(item)!.toFixed(2) }} kWh</template>
                      <span v-else class="text-gray-400 dark:text-gray-600">-</span>
                    </div>
                    <div class="text-sm text-slate-600 dark:text-gray-300 whitespace-nowrap truncate flex items-center gap-2">
                      <span v-if="item.dateRange">{{ item.dateRange }}</span>
                      <span v-if="item.totalPhantomKwh && authStore.canViewLiveAnalytics"
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
                    <div class="text-[10px] uppercase tracking-wider text-gray-400 dark:text-gray-500 text-center">{{ t('dashboard.trip_speed_header') }}</div>
                    <div>
                      <span v-if="item.totalKm" class="inline-flex items-center gap-1 px-2 py-0.5 bg-white dark:bg-gray-800 border border-gray-300 dark:border-gray-600 rounded-full text-xs text-slate-700 dark:text-gray-200 whitespace-nowrap">
                        +{{ formatDistance(item.totalKm) }}
                      </span>
                      <span v-else class="text-gray-400 dark:text-gray-600 text-sm">-</span>
                    </div>
                    <div class="text-gray-400 dark:text-gray-600 text-sm text-center">-</div>
                    <div class="flex justify-end">
                      <span v-if="tripGroupCostPer100km(item) != null"
                        class="inline-flex items-center px-2 py-0.5 border rounded-full text-xs font-medium whitespace-nowrap bg-emerald-50 dark:bg-emerald-900/20 border-emerald-200 dark:border-emerald-700/50 text-emerald-700 dark:text-emerald-300">
                        {{ formatCurrency(tripGroupCostPer100km(item)!) }}/100km
                      </span>
                      <span v-else class="text-gray-400 dark:text-gray-600 text-sm">-</span>
                    </div>
                    <div class="flex justify-end">
                      <ChevronUpIcon v-if="!isGroupCollapsed(item.groupId)" class="w-4 h-4 text-emerald-500 flex-shrink-0" />
                      <ChevronDownIcon v-else class="w-4 h-4 text-emerald-500 flex-shrink-0" />
                    </div>
                  </button>

                  <!-- Sub-trip rows in grid -->
                  <Transition :css="false" @enter="onTripFormEnter" @after-enter="onTripFormAfterEnter" @leave="onTripFormLeave">
                  <div v-if="!isGroupCollapsed(item.groupId)" class="bg-emerald-50/60 dark:bg-emerald-950/20">
                    <template v-for="day in item.days" :key="day.dateKey + '__d'">
                    <!-- Datumsband, siehe Mobile-Ansicht. -->
                    <div v-if="item.days.length > 1" :style="dayBandStyle(item.groupId)" class="sticky z-[2] flex items-center justify-between gap-3 px-3 py-2
                                bg-emerald-100 dark:bg-emerald-950 border-y border-emerald-300/70 dark:border-emerald-800/50">
                      <span class="text-[13px] font-bold text-gray-900 dark:text-gray-100">{{ dayLabel(day.dateKey) }}</span>
                      <span class="text-xs text-gray-500 dark:text-gray-400 tabular-nums">
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
                      <!-- Add-trip form (full width inside container) -->
                      <Transition :css="false" @enter="onTripFormEnter" @after-enter="onTripFormAfterEnter" @leave="onTripFormLeave">
                      <div v-if="addingTripAfterId === trip.id"
                           class="px-3 py-3 border-t border-emerald-200/60 dark:border-emerald-800/40 bg-emerald-50/40 dark:bg-emerald-950/30">
                        <TripForm v-model="tripForm" mode="add"
                          :error="tripError" :saving="tripSaving" :distance-unit="distanceUnitLabel()"
                          @save="saveNewTrip()" @cancel="cancelTripEdit()" />
                      </div>
                      </Transition>
                      <!-- Display row - Klick auf die Zeile klappt die Karte darunter auf. -->
                      <div v-if="editingTripId !== trip.id && deletingTripId !== trip.id"
                        :class="[FEED_GRID_COLS, 'items-center px-3 py-1.5 bg-emerald-50/60 dark:bg-transparent border-t border-emerald-300/50 dark:border-emerald-800/30 hover:bg-emerald-100/70 dark:hover:bg-emerald-900/20 transition',
                                 hasTripMap(trip) ? 'cursor-pointer' : '']"
                        :role="hasTripMap(trip) ? 'button' : undefined"
                        :tabindex="hasTripMap(trip) ? 0 : undefined"
                        :aria-expanded="hasTripMap(trip) ? expandedTripMaps.has(trip.id + '__d') : undefined"
                        :aria-controls="hasTripMap(trip) ? 'trip-map-' + trip.id + '__d' : undefined"
                        @click="hasTripMap(trip) && toggleTripMap(trip.id + '__d')"
                        @keydown.enter.self.prevent="hasTripMap(trip) && toggleTripMap(trip.id + '__d')"
                        @keydown.space.self.prevent="hasTripMap(trip) && toggleTripMap(trip.id + '__d')">
                        <div class="flex items-center gap-1.5 pl-4">
                          <MapIcon class="w-4 h-4 text-emerald-600 dark:text-emerald-400 flex-shrink-0" aria-hidden="true" />
                        </div>
                        <div class="text-[13px] text-gray-900 dark:text-gray-100 whitespace-nowrap truncate">
                          {{ tripTimeParts(trip.tripStartedAt, trip.tripEndedAt).time }}
                        </div>
                        <div class="text-sm font-medium text-rose-600 dark:text-rose-300 whitespace-nowrap">
                          <template v-if="tripConsumption(trip) && trip.distanceKm">−{{ (tripConsumption(trip)!.kwhPer100km * trip.distanceKm / 100).toFixed(2) }} kWh</template>
                          <span v-else class="text-gray-400 dark:text-gray-600">-</span>
                        </div>
                        <div class="text-xs whitespace-nowrap">
                          <span v-if="tripConsumption(trip)" class="text-slate-700 dark:text-gray-200">
                            {{ tripConsumption(trip)!.estimated ? '~' : '' }}{{ formatConsumption(tripConsumption(trip)!.kwhPer100km) }}
                          </span>
                          <span v-else class="text-gray-400 dark:text-gray-600">-</span>
                        </div>
                        <div class="text-xs text-slate-700 dark:text-gray-200 whitespace-nowrap">
                          <template v-if="trip.socStart != null && trip.socEnd != null">{{ trip.socStart }}→{{ trip.socEnd }}%</template>
                          <span v-else class="text-gray-400 dark:text-gray-600">-</span>
                        </div>
                        <div class="text-xs text-slate-700 dark:text-gray-200 whitespace-nowrap text-center">
                          <template v-if="trip.maxSpeedKmh != null">
                            {{ t('dashboard.trip_speed_compact', { avg: Math.round(Number(trip.avgSpeedKmh)), max: Math.round(Number(trip.maxSpeedKmh)) }) }}
                          </template>
                          <span v-else class="text-gray-400 dark:text-gray-600">-</span>
                        </div>
                        <div>
                          <span v-if="trip.distanceKm != null" class="inline-flex items-center gap-1 px-2 py-0.5 bg-white dark:bg-gray-800 border border-gray-300 dark:border-gray-600 rounded-full text-xs text-slate-700 dark:text-gray-200 whitespace-nowrap">
                            +{{ formatDistance(trip.distanceKm, { round: false }) }}
                          </span>
                          <span v-else class="text-gray-400 dark:text-gray-600 text-xs">-</span>
                        </div>
                        <div>
                          <span v-if="trip.outsideTempCelsius != null"
                            :class="['inline-flex items-center gap-0.5 px-2 py-0.5 border rounded text-xs whitespace-nowrap', tempBadgeClass(trip.outsideTempCelsius)]">
                            <SunIcon class="w-3 h-3" />{{ trip.outsideTempCelsius }}°C
                          </span>
                          <span v-else class="text-gray-400 dark:text-gray-600 text-xs">-</span>
                        </div>
                        <div class="flex justify-end whitespace-nowrap">
                          <ComparisonChip v-if="tripCostPer100kmDisplay(trip) != null"
                            :level="communityLevel(tripCostPer100kmDisplay(trip), personalCostBenchmark?.costPer100km)"
                            :delta-percent="comparisonDeltaPercent(tripCostPer100kmDisplay(trip), personalCostBenchmark?.costPer100km)"
                            :tooltip="costPer100kmTooltip(tripCostPer100kmDisplay(trip))">
                            {{ formatCurrency(tripCostPer100kmDisplay(trip)!) }}/100km
                          </ComparisonChip>
                          <span v-else class="text-gray-400 dark:text-gray-600 text-xs">-</span>
                        </div>
                        <div class="flex items-center justify-end gap-0.5 relative">
                          <!-- Affordance fuer die aufklappbare Karte - der Klick liegt auf der Zeile. -->
                          <ChevronDownIcon v-if="hasTripMap(trip)"
                            :class="['w-3.5 h-3.5 text-gray-400 transition-transform duration-200', expandedTripMaps.has(trip.id + '__d') ? 'rotate-180' : '']"
                            aria-hidden="true" />
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
                            :class="['absolute right-0 w-44 bg-white dark:bg-gray-700 rounded-sm shadow-[4px_4px_0_rgba(0,0,0,0.30)] dark:shadow-[4px_4px_0_rgba(255,255,255,0.30)] border border-gray-200 dark:border-gray-600 z-50 py-1',
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
                      <TripClimateMarkers :climate="trip.climate" class="px-3 pb-2.5" />
                      <Transition :css="false" @enter="onTripFormEnter" @after-enter="onTripFormAfterEnter" @leave="onTripFormLeave">
                        <TripMapPanel v-if="expandedTripMaps.has(trip.id + '__d')" :trip="trip"
                                      :panel-id="'trip-map-' + trip.id + '__d'" class="px-3 pb-2.5" />
                      </Transition>
                      <!-- Inline edit form -->
                      <Transition :css="false" @enter="onTripFormEnter" @after-enter="onTripFormAfterEnter" @leave="onTripFormLeave">
                      <div v-if="editingTripId === trip.id"
                           class="px-3 py-3 border-t border-emerald-200/60 dark:border-emerald-800/40 bg-emerald-50/40 dark:bg-emerald-950/30">
                        <TripForm v-model="tripForm" mode="edit"
                          :error="tripError" :saving="tripSaving" :distance-unit="distanceUnitLabel()"
                          @save="saveTripEdit(trip.id)" @cancel="cancelTripEditFull()" />
                      </div>
                      </Transition>
                      <!-- Luecke zur naechstfrueheren Fahrt - siehe Mobile-Ansicht. -->
                      <div v-if="isRestBreak(day.trips, tripIdx as number) && !showsDrain(trip)"
                           class="flex items-center justify-center py-2">
                        <span class="text-[11px] text-gray-400 dark:text-gray-500">
                          {{ t('dashboard.trip_pause', { duration: formatPauseDuration(pauseBeforeTripMinutes(day.trips, tripIdx as number)) }) }}
                        </span>
                      </div>

                      <!-- Phantom drain separator between trips (AutoSync Live feature) -->
                      <div v-if="showsDrain(trip)"
                        class="flex items-center justify-center gap-1 py-1.5 border-t border-emerald-300/50 dark:border-emerald-800/30">
                        <BoltIcon class="w-3 h-3 text-amber-700 dark:text-amber-500" />
                        <span class="text-[11px] font-medium text-amber-800 dark:text-amber-400">
                          <span v-if="drainPauseMinutes(trip)" class="font-normal text-amber-700/70 dark:text-amber-500/70">{{ formatPauseDuration(drainPauseMinutes(trip)) }} &middot; </span>
                          {{ trip._phantomDrain.kwh.toFixed(2) }} kWh
                          <template v-if="selectedCar?.effectiveBatteryCapacityKwh">({{ (trip._phantomDrain.kwh / selectedCar.effectiveBatteryCapacityKwh * 100).toFixed(1) }}%)</template>
                          {{ t('dashboard.phantom_drain_word') }}
                          <span v-if="phantomDrainEur(trip._phantomDrain) != null" class="opacity-80">· ≈ {{ formatCurrency(phantomDrainEur(trip._phantomDrain)!) }}</span>
                        </span>
                        <router-link v-if="!authStore.canViewLiveAnalytics && purchasesAvailable()" :to="upsellTarget" class="text-[11px] font-semibold text-amber-700 dark:text-amber-400 underline decoration-dotted hover:decoration-solid">{{ t('dashboard.phantom_teaser_unlock') }}</router-link>
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
              <!-- Phantom drain -->
              <div v-if="showsDrain(item.entry)" class="flex items-center gap-2 px-4 mt-0.5 mb-2">
                <div class="flex-1 h-px bg-gray-200 dark:bg-gray-600" />
                <span class="inline-flex items-center gap-1 px-2 py-0.5 rounded-full border border-amber-300 dark:border-amber-600 bg-amber-50 dark:bg-amber-900/20 text-xs text-amber-600 dark:text-amber-400 whitespace-nowrap">
                  <BoltIcon class="w-3 h-3" />
                  {{ item.entry._phantomDrain.kwh.toFixed(2) }} kWh
                  <template v-if="selectedCar?.effectiveBatteryCapacityKwh">
                    ({{ (item.entry._phantomDrain.kwh / selectedCar.effectiveBatteryCapacityKwh * 100).toFixed(1) }}%)
                  </template>
                  {{ t('dashboard.phantom_drain_word') }}
                  <span v-if="phantomDrainEur(item.entry._phantomDrain) != null" class="opacity-80">· ≈ {{ formatCurrency(phantomDrainEur(item.entry._phantomDrain)!) }}</span>
                </span>
                <router-link v-if="!authStore.canViewLiveAnalytics && purchasesAvailable()" :to="upsellTarget" class="text-xs font-semibold text-amber-600 dark:text-amber-400 underline decoration-dotted hover:decoration-solid whitespace-nowrap">{{ t('dashboard.phantom_teaser_unlock') }}</router-link>
                <div class="flex-1 h-px bg-gray-200 dark:bg-gray-600" />
              </div>
              <!-- Add-trip form triggered from a charge entry -->
              <Transition :css="false" @enter="onTripFormEnter" @after-enter="onTripFormAfterEnter" @leave="onTripFormLeave">
              <div v-if="addingTripAfterId === item.entry.id" class="ml-2 mr-2 mt-1 p-3 rounded-sm shadow-sm ring-1 ring-black/5 dark:ring-white/10 border-l-4 border-l-emerald-400 dark:border-l-emerald-500 border-r-4 border-r-emerald-400 dark:border-r-emerald-500 bg-white dark:bg-gray-700 space-y-3">
                <div class="flex items-center justify-between gap-2">
                  <span class="text-sm font-medium text-emerald-800 dark:text-emerald-300 flex items-center gap-1.5">
                    <PlusIcon class="w-4 h-4" />{{ t('dashboard.trip_add') }}
                  </span>
                  <div class="flex gap-1">
                    <button @click="saveNewTrip()" :disabled="tripSaving"
                      class="px-3 py-1 text-xs font-medium bg-emerald-600 hover:bg-emerald-700 text-white rounded-sm disabled:opacity-50 transition">
                      {{ t('dashboard.trip_save') }}
                    </button>
                    <button @click="cancelTripEdit()"
                      class="px-3 py-1 text-xs font-medium bg-white dark:bg-gray-700 border border-gray-200 dark:border-gray-600 text-gray-600 dark:text-gray-300 rounded-sm hover:bg-gray-50 dark:hover:bg-gray-600 transition">
                      {{ t('dashboard.trip_cancel') }}
                    </button>
                  </div>
                </div>
                <p v-if="tripError" class="text-xs text-red-500 -mb-1">{{ tripError }}</p>
                <div class="grid grid-cols-1 sm:grid-cols-2 gap-2">
                  <div>
                    <label class="block text-xs text-gray-500 dark:text-gray-400 mb-1">{{ t('dashboard.trip_started_at') }}</label>
                    <input v-model="tripForm.tripStartedAt" type="datetime-local"
                      class="w-full px-2 py-1.5 text-sm border border-gray-200 dark:border-gray-600 rounded-sm bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-1 focus:ring-emerald-400" />
                  </div>
                  <div>
                    <label class="block text-xs text-gray-500 dark:text-gray-400 mb-1">{{ t('dashboard.trip_ended_at') }}</label>
                    <input v-model="tripForm.tripEndedAt" type="datetime-local"
                      class="w-full px-2 py-1.5 text-sm border border-gray-200 dark:border-gray-600 rounded-sm bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-1 focus:ring-emerald-400" />
                  </div>
                </div>
                <div class="grid grid-cols-1 sm:grid-cols-2 gap-2">
                  <div>
                    <label class="block text-xs text-gray-500 dark:text-gray-400 mb-1">{{ t('dashboard.trip_distance', { unit: distanceUnitLabel() }) }}</label>
                    <input v-model="tripForm.distanceKm" type="number" min="0" max="9999" step="0.1"
                      class="w-full px-2 py-1.5 text-sm border border-gray-200 dark:border-gray-600 rounded-sm bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-1 focus:ring-emerald-400" />
                  </div>
                  <div>
                    <label class="block text-xs text-gray-500 dark:text-gray-400 mb-1">{{ t('dashboard.trip_route_type') }}</label>
                    <div class="flex gap-1">
                      <button v-for="rt in ['CITY','COMBINED','HIGHWAY']" :key="rt"
                        @click="tripForm.routeType = rt"
                        :class="['flex-1 px-1 py-1.5 text-xs rounded-sm border transition',
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
                class="hidden gridfeed:block relative bg-white dark:bg-gray-700 border-2 border-gray-300 dark:border-gray-600 rounded-sm shadow-[2px_2px_0_0_#d1d5db] dark:shadow-[2px_2px_0_0_#374151]">
                <div :class="[FEED_GRID_COLS, 'items-center px-3 py-2.5']">
                  <!-- 1. Type cell: Bolt + AC/DC badge -->
                  <div class="flex items-center gap-1.5">
                    <BoltIcon class="w-4 h-4 text-green-500 dark:text-green-400 flex-shrink-0" />
                    <ChargeTypeBadge :type="item.entry.chargingType" />
                  </div>
                  <!-- 2. Energy -->
                  <div class="font-semibold text-indigo-700 dark:text-indigo-300 whitespace-nowrap">
                    +{{ item.entry.kwhAtVehicle ?? item.entry.kwhCharged ?? '-' }} kWh
                  </div>
                  <!-- 3. Date + optionaler Ladekurve-Toggle -->
                  <div class="text-sm text-gray-500 dark:text-gray-400 whitespace-nowrap truncate flex items-center gap-1.5">
                    <span class="truncate">{{ formatLogDate(item.entry.loggedAt) }}</span>
                    <button
                      v-if="((item.entry.hasPowerCurve && authStore.canViewLiveAnalytics) || (item.entry.hasSocCurve && authStore.canViewSocCurve))"
                      type="button"
                      @click.stop="openPowerCurve(item.entry)"
                      :aria-label="t('dashboard.show_power_curve')"
                      aria-haspopup="dialog"
                      class="p-0.5 rounded text-emerald-600 dark:text-emerald-400 hover:bg-emerald-100/40 dark:hover:bg-emerald-900/30 transition focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-400 flex-shrink-0"
                    >
                      <ChartBarSquareIcon class="w-4 h-4" />
                    </button>
                    <!-- Gesperrt: dasselbe Kurven-Symbol mit Schloss-Marke. Ein blankes
                         Schloss sagt nicht, was dahinter liegt. -->
                    <button
                      v-else-if="(item.entry.hasPowerCurve || item.entry.hasSocCurve) && purchasesAvailable()"
                      type="button"
                      @click.stop="openPowerCurve(item.entry)"
                      :aria-label="t('dashboard.power_curve_locked')"
                      :title="t('dashboard.power_curve_locked')"
                      aria-haspopup="dialog"
                      class="relative p-0.5 rounded text-amber-500 dark:text-amber-400 hover:bg-amber-100/40 dark:hover:bg-amber-900/30 transition focus:outline-none focus-visible:ring-2 focus-visible:ring-amber-400 flex-shrink-0"
                    >
                      <ChartBarSquareIcon class="w-4 h-4" />
                      <LockClosedIcon class="absolute -bottom-0.5 -right-0.5 w-2.5 h-2.5" />
                    </button>
                  </div>
                  <!-- 4. Consumption (or short-trip / kwh-in-next hint) -->
                  <div class="text-sm whitespace-nowrap">
                    <button
                      v-if="item.entry.consumptionKwhPer100km == null && item.entry.kwhCountedInNextConsumption"
                      type="button"
                      class="inline-flex items-center gap-1 text-xs text-gray-400 dark:text-gray-500 cursor-pointer focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-400 focus-visible:ring-offset-1 dark:focus-visible:ring-offset-gray-700 rounded"
                      :aria-expanded="openTooltipLogId === item.entry.id"
                      @click.stop="openTooltipLogId = openTooltipLogId === item.entry.id ? null : item.entry.id">
                      <InformationCircleIcon class="w-3 h-3 flex-shrink-0" aria-hidden="true" />
                      {{ t('dashboard.kwh_in_next_hint') }}
                    </button>
                    <button
                      v-else-if="item.entry.consumptionKwhPer100km == null && isShortTrip(item.entry)"
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
                    <template v-if="formatSocRange(item.entry.socBeforeChargePercent, item.entry.socAfterChargePercent)">
                      {{ formatSocRange(item.entry.socBeforeChargePercent, item.entry.socAfterChargePercent) }}
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
                  <!-- 8. Temperature chip -->
                  <div>
                    <span v-if="item.entry.temperatureCelsius != null"
                      :class="['inline-flex items-center gap-0.5 px-2 py-0.5 border rounded text-xs whitespace-nowrap', tempBadgeClass(item.entry.temperatureCelsius)]">
                      <SunIcon class="w-3 h-3" />{{ item.entry.temperatureCelsius.toFixed(1) }}°C
                    </span>
                    <span v-else class="text-gray-400 dark:text-gray-600 text-sm">-</span>
                  </div>
                  <!-- 9. Price chip + Real-Cost-Info (Info-Icon LINKS des Preises, Popover-Overlay, Click-Außerhalb schließt) -->
                  <div class="flex justify-end items-center gap-1 relative">
                    <button v-if="realCostHintFor(item.entry.id)"
                      type="button"
                      class="p-0.5 rounded-full text-amber-600 dark:text-amber-400 hover:text-amber-700 dark:hover:text-amber-300 hover:bg-amber-50 dark:hover:bg-amber-900/30 focus:outline-none focus-visible:ring-2 focus-visible:ring-amber-400"
                      :aria-expanded="openRealCostTooltipId === item.entry.id + '__d'"
                      :aria-label="t('dashboard.real_cost_tooltip_title')"
                      @click.stop="openRealCostTooltipId = openRealCostTooltipId === item.entry.id + '__d' ? null : item.entry.id + '__d'">
                      <InformationCircleIcon class="w-4 h-4" aria-hidden="true" />
                    </button>
                    <button v-if="item.entry.costEur != null && (item.entry.kwhCharged ?? item.entry.kwhAtVehicle)"
                      type="button"
                      :class="['inline-flex items-center px-2 py-0.5 text-xs rounded-full font-medium whitespace-nowrap cursor-pointer transition-all duration-75 focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-400',
                               isNettoOnlyCostLog(item.entry) ? 'border border-dashed' : 'border',
                               showCostAbsolute
                                 ? 'bg-gray-50 dark:bg-gray-700 border-gray-200 dark:border-gray-600 text-gray-600 dark:text-gray-300 shadow-[0_4px_0_0_#d1d5db] dark:shadow-[0_4px_0_0_#111827] hover:shadow-[0_2px_0_0_#d1d5db] dark:hover:shadow-[0_2px_0_0_#111827] hover:translate-y-0.5 active:shadow-none active:translate-y-1'
                                 : [(costBadgeClass(item.entry.costEur, item.entry.kwhCharged ?? item.entry.kwhAtVehicle) ?? 'bg-green-50 border-green-200 text-green-700'), 'shadow-[0_4px_0_0_#d1d5db] dark:shadow-[0_4px_0_0_#111827] hover:shadow-[0_2px_0_0_#d1d5db] dark:hover:shadow-[0_2px_0_0_#111827] hover:translate-y-0.5 active:shadow-none active:translate-y-1'].join(' ')]"
                      @click.stop="showCostAbsolute = !showCostAbsolute">
                      <template v-if="showCostAbsolute">{{ formatCurrency(item.entry.costEur) }}</template>
                      <template v-else>{{ formatCostPerKwh(item.entry.costEur / (item.entry.kwhCharged ?? item.entry.kwhAtVehicle)) }}</template>
                    </button>
                    <span v-else class="text-gray-400 dark:text-gray-600 text-sm">-</span>
                    <div v-if="openRealCostTooltipId === item.entry.id + '__d' && realCostHintFor(item.entry.id)"
                      class="absolute right-0 top-full mt-1.5 w-72 p-3 rounded-sm bg-amber-50 dark:bg-gray-800 border border-amber-300 dark:border-amber-700 text-xs text-amber-900 dark:text-amber-200 space-y-1.5 leading-relaxed shadow-[4px_4px_0_rgba(0,0,0,0.30)] dark:shadow-[4px_4px_0_rgba(255,255,255,0.10)] z-[60]"
                      @click.stop>
                      <p class="font-semibold">{{ t('dashboard.real_cost_tooltip_title') }}</p>
                      <p>{{ t('dashboard.real_cost_label') }} ≈
                        <template v-if="showCostAbsolute">{{ formatCurrency(realCostHintFor(item.entry.id)!.bruttoCostEur) }}</template>
                        <template v-else>{{ formatCostPerKwh(realCostHintFor(item.entry.id)!.bruttoRatePerNettoKwhEur) }}</template>
                      </p>
                      <p>{{ realCostHintFor(item.entry.id)!.source === 'measured-median'
                            ? t('dashboard.real_cost_tooltip_measured', { count: realCostHintFor(item.entry.id)!.sampleSize, pct: realCostHintFor(item.entry.id)!.efficiencyPercent }, realCostHintFor(item.entry.id)!.sampleSize)
                            : t('dashboard.real_cost_tooltip_pauschale', { pct: realCostHintFor(item.entry.id)!.efficiencyPercent }) }}</p>
                    </div>
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
                      class="absolute right-0 top-full mt-1 w-44 bg-white dark:bg-gray-700 rounded-sm shadow-[4px_4px_0_rgba(0,0,0,0.30)] dark:shadow-[4px_4px_0_rgba(255,255,255,0.30)] border border-gray-200 dark:border-gray-600 z-50 py-1 overflow-hidden">
                      <button role="menuitem" type="button" @click.stop="editingLog = item.entry; openMenuLogId = null"
                        class="w-full flex items-center gap-2.5 px-3 py-2 text-sm text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-600 transition focus:outline-none focus-visible:bg-gray-100 dark:focus-visible:bg-gray-600">
                        <PencilSquareIcon class="w-4 h-4 flex-shrink-0" aria-hidden="true" />{{ t('dashboard.action_edit') }}
                      </button>
                      <button role="menuitem" type="button" @click.stop="startAddTrip(item.entry.id); openMenuLogId = null"
                        class="w-full flex items-center gap-2.5 px-3 py-2 text-sm text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-600 transition focus:outline-none focus-visible:bg-gray-100 dark:focus-visible:bg-gray-600">
                        <MapIcon class="w-4 h-4 flex-shrink-0" aria-hidden="true" />{{ t('dashboard.action_add_trip') }}
                      </button>
                      <button role="menuitem" type="button" @click.stop="openMergeModal(item.entry); openMenuLogId = null"
                        class="w-full flex items-center gap-2.5 px-3 py-2 text-sm text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-600 transition focus:outline-none focus-visible:bg-gray-100 dark:focus-visible:bg-gray-600">
                        <LinkIcon class="w-4 h-4 flex-shrink-0" aria-hidden="true" />{{ t('dashboard.action_merge') }}
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
                  <div class="p-2.5 rounded-sm bg-amber-50 dark:bg-amber-900/20 border border-amber-200 dark:border-amber-700 text-xs text-amber-800 dark:text-amber-300 space-y-1">
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
                  <div class="p-2.5 rounded-sm bg-gray-50 dark:bg-gray-800 border border-gray-200 dark:border-gray-700 text-xs text-gray-600 dark:text-gray-300 space-y-1">
                    <p class="font-medium">{{ t('dashboard.short_trip_tooltip_title') }}</p>
                    <p>{{ t('dashboard.short_trip_tooltip_desc') }}</p>
                  </div>
                </div>
                <div
                  v-if="item.entry.consumptionKwhPer100km == null && item.entry.kwhCountedInNextConsumption && openTooltipLogId === item.entry.id"
                  class="px-3 pb-2.5">
                  <div class="p-2.5 rounded-sm bg-gray-50 dark:bg-gray-800 border border-gray-200 dark:border-gray-700 text-xs text-gray-600 dark:text-gray-300 space-y-1">
                    <p class="font-medium">{{ t('dashboard.kwh_in_next_tooltip_title') }}</p>
                    <p>{{ t('dashboard.kwh_in_next_tooltip_desc') }}</p>
                  </div>
                </div>
                <!-- Brutto/Netto sub-line (quick-edit shortcut) -->
                <div v-if="item.entry.kwhCharged != null || item.entry.kwhAtVehicle != null"
                  class="px-3 pb-2 -mt-1 flex items-center gap-2 text-[11px] text-gray-500 dark:text-gray-400">
                  <template v-if="logEditState[item.entry.id] !== undefined">
                    <input type="number" step="0.01" min="0"
                      v-model="logEditState[item.entry.id]"
                      :aria-label="t(logFieldToSet(item.entry) === 'kwhCharged' ? 'dashboard.ac_gross_label_brutto' : 'dashboard.ac_gross_label_netto')"
                      class="w-20 text-xs border border-blue-300 dark:border-blue-700 rounded px-2 py-0.5 bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-1 focus:ring-blue-400"
                      @keydown.enter.prevent="applyLogValue(item.entry)"
                      @keydown.escape.prevent="cancelLogEdit(item.entry.id)"
                    />
                    <span>kWh {{ t(logFieldToSet(item.entry) === 'kwhCharged' ? 'dashboard.ac_gross_label_brutto' : 'dashboard.ac_gross_label_netto') }}</span>
                    <button type="button" class="text-blue-500 hover:text-blue-700 dark:hover:text-blue-300 disabled:opacity-40 transition" :disabled="logSaving.has(item.entry.id)" @click="applyLogValue(item.entry, $event)">
                      <svg v-if="logSaving.has(item.entry.id)" class="w-3.5 h-3.5 animate-spin" fill="none" viewBox="0 0 24 24">
                        <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"/>
                        <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"/>
                      </svg>
                      <CheckIcon v-else class="w-3.5 h-3.5" />
                    </button>
                    <button type="button" class="text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 transition" @click="cancelLogEdit(item.entry.id)">
                      <XMarkIcon class="w-3.5 h-3.5" />
                    </button>
                    <span v-if="logError[item.entry.id]" class="text-red-500 dark:text-red-400">{{ logError[item.entry.id] }}</span>
                  </template>
                  <template v-else-if="item.entry.kwhCharged != null && item.entry.kwhAtVehicle != null">
                    <button type="button" class="flex items-center gap-3 hover:text-blue-500 transition" @click="startLogEdit(item.entry, $event)">
                      <span>{{ item.entry.kwhCharged }} kWh {{ t('dashboard.ac_gross_label_brutto') }}</span>
                      <span :class="['font-medium', chargingEfficiency(item.entry.kwhCharged, item.entry.kwhAtVehicle)! >= 90 ? 'text-green-600 dark:text-green-400' : 'text-amber-600 dark:text-amber-400']">
                        {{ chargingEfficiency(item.entry.kwhCharged, item.entry.kwhAtVehicle) }}% {{ t('dashboard.ac_gross_efficiency') }}
                      </span>
                    </button>
                    <span v-if="sourceInfo(item.entry.dataSource)"
                      :class="['inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-medium', sourceInfo(item.entry.dataSource)!.classes]">
                      {{ sourceInfo(item.entry.dataSource)!.label }}
                    </span>
                  </template>
                  <template v-else>
                    <button type="button" class="flex items-center gap-1 hover:text-blue-500 transition" @click="startLogEdit(item.entry, $event)">
                      <PlusIcon class="w-3.5 h-3.5" />
                      {{ t(logFieldToSet(item.entry) === 'kwhCharged' ? 'dashboard.ac_gross_add_brutto' : 'dashboard.ac_gross_add_netto') }}
                    </button>
                    <span v-if="sourceInfo(item.entry.dataSource)"
                      :class="['inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-medium', sourceInfo(item.entry.dataSource)!.classes]">
                      {{ sourceInfo(item.entry.dataSource)!.label }}
                    </span>
                  </template>
                </div>
              </div>

              <!-- CHARGE ENTRY (DESKTOP GRID, Ladegruppe) -->
              <div v-if="item.entry._isLadegruppe"
                class="hidden gridfeed:block rounded-sm border-2 border-blue-200 dark:border-blue-800/60 border-l-4 border-l-blue-400 dark:border-l-blue-500 shadow-[2px_2px_0_0_#bfdbfe] dark:shadow-[2px_2px_0_0_#1e3a8a]">
                <button type="button" @click="toggleLadegruppe(item.entry.id)"
                  :aria-expanded="expandedGroups.has(item.entry.id)"
                  :class="[FEED_GRID_COLS, 'w-full items-center px-3 py-2 bg-blue-50/40 dark:bg-blue-900/15 hover:bg-blue-50 dark:hover:bg-blue-900/25 transition text-left focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-400 focus-visible:ring-inset']">
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
                  <div class="text-gray-400 dark:text-gray-600 text-xs text-center">-</div>
                  <div class="flex justify-end">
                    <button v-if="item.entry._totalCostEur != null && item.entry._costBasisKwh"
                      type="button"
                      :class="['inline-flex items-center px-2 py-0.5 text-xs rounded-full font-medium whitespace-nowrap cursor-pointer transition-all duration-75 focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-400',
                               item.entry._costIsNettoOnly ? 'border border-dashed' : 'border',
                               showCostAbsolute
                                 ? 'bg-gray-50 dark:bg-gray-700 border-gray-200 dark:border-gray-600 text-gray-600 dark:text-gray-300 shadow-[0_4px_0_0_#d1d5db] dark:shadow-[0_4px_0_0_#111827]'
                                 : [(costBadgeClass(item.entry._totalCostEur, item.entry._costBasisKwh) ?? 'bg-green-50 border-green-200 text-green-700'), 'shadow-[0_4px_0_0_#d1d5db] dark:shadow-[0_4px_0_0_#111827]'].join(' ')]"
                      @click.stop="showCostAbsolute = !showCostAbsolute">
                      <template v-if="showCostAbsolute">{{ formatCurrency(item.entry._totalCostEur) }}</template>
                      <template v-else>{{ formatCostPerKwh(item.entry._totalCostEur / item.entry._costBasisKwh) }}</template>
                    </button>
                    <span v-else class="text-gray-400 dark:text-gray-600 text-xs">-</span>
                  </div>
                  <div class="flex items-center justify-end gap-0.5 relative">
                    <button v-if="otherCars.length > 0" type="button"
                      @click.stop="openMenuGroupId = openMenuGroupId === item.entry.id + '__d' ? null : item.entry.id + '__d'"
                      @mousedown.stop
                      class="p-0.5 rounded text-gray-400 hover:text-gray-600 dark:hover:text-gray-200 hover:bg-gray-100 dark:hover:bg-gray-600 transition focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-400"
                      :aria-label="t('dashboard.action_menu_label') || 'Aktionen'"
                      aria-haspopup="menu"
                      :aria-expanded="openMenuGroupId === item.entry.id + '__d'">
                      <EllipsisVerticalIcon class="w-4 h-4" aria-hidden="true" />
                    </button>
                    <ChevronUpIcon v-if="expandedGroups.has(item.entry.id)" class="w-4 h-4 text-blue-400 dark:text-blue-500 flex-shrink-0" />
                    <ChevronDownIcon v-else class="w-4 h-4 text-blue-400 dark:text-blue-500 flex-shrink-0" />
                    <div v-if="openMenuGroupId === item.entry.id + '__d'"
                      role="menu"
                      class="absolute right-0 top-full mt-1 w-44 bg-white dark:bg-gray-700 rounded-sm shadow-[4px_4px_0_rgba(0,0,0,0.30)] dark:shadow-[4px_4px_0_rgba(255,255,255,0.30)] border border-gray-200 dark:border-gray-600 z-50 py-1 overflow-hidden">
                      <button role="menuitem" type="button"
                        @click.stop="openReassignModal(item.entry); openMenuGroupId = null"
                        @mousedown.stop
                        class="w-full flex items-center gap-2.5 px-3 py-2 text-sm text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-600 transition focus:outline-none focus-visible:bg-gray-100 dark:focus-visible:bg-gray-600">
                        <ArrowsRightLeftIcon class="w-4 h-4 flex-shrink-0" aria-hidden="true" />{{ t('dashboard.action_reassign') }}
                      </button>
                    </div>
                  </div>
                </button>

                <!-- Missing value sub-line (desktop) -->
                <div v-if="item.entry._canEditMissing"
                  class="px-3 py-1 border-t border-blue-100 dark:border-blue-800/30 bg-blue-50/20 dark:bg-blue-950/10 flex items-center gap-2"
                  @click.stop @mousedown.stop>
                  <template v-if="grossEditState[item.entry.id] !== undefined">
                    <input
                      type="number" step="0.01" min="0"
                      v-model="grossEditState[item.entry.id]"
                      :aria-label="t(item.entry._missingLabel)"
                      class="w-24 text-xs border border-blue-300 dark:border-blue-700 rounded px-2 py-0.5 bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-1 focus:ring-blue-400"
                      @click.stop @mousedown.stop @keydown.stop
                      @keydown.enter.stop.prevent="applyGrossTotal(item.entry)"
                      @keydown.escape.stop.prevent="cancelGrossEdit(item.entry.id)"
                    />
                    <span class="text-xs text-gray-500 dark:text-gray-400">kWh {{ t(item.entry._missingLabel) }}</span>
                    <button type="button"
                      class="text-blue-500 hover:text-blue-700 dark:hover:text-blue-300 disabled:opacity-40 transition"
                      :disabled="grossSaving.has(item.entry.id)"
                      @click.stop="applyGrossTotal(item.entry, $event)">
                      <svg v-if="grossSaving.has(item.entry.id)" class="w-3.5 h-3.5 animate-spin" fill="none" viewBox="0 0 24 24">
                        <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"/>
                        <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"/>
                      </svg>
                      <CheckIcon v-else class="w-3.5 h-3.5" />
                    </button>
                    <button type="button" class="text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 transition"
                      @click.stop="cancelGrossEdit(item.entry.id)">
                      <XMarkIcon class="w-3.5 h-3.5" />
                    </button>
                    <span v-if="grossError[item.entry.id]" class="text-xs text-red-500 dark:text-red-400">
                      {{ grossError[item.entry.id] }}
                    </span>
                  </template>
                  <template v-else-if="item.entry._efficiency != null">
                    <button type="button"
                      class="flex items-center gap-1.5 text-xs text-gray-500 dark:text-gray-400 hover:text-blue-500 transition"
                      @click.stop="startGrossEdit(item.entry, $event)">
                      <span class="tabular-nums font-medium">{{ item.entry._bruttoSum }} kWh {{ t('dashboard.ac_gross_label_brutto') }}</span>
                      <span :class="['font-medium', item.entry._efficiency >= 90 ? 'text-green-600 dark:text-green-400' : 'text-amber-600 dark:text-amber-400']">· {{ item.entry._efficiency }}% {{ t('dashboard.ac_gross_efficiency') }}</span>
                      <PencilSquareIcon class="w-3 h-3 text-gray-400" />
                    </button>
                  </template>
                  <template v-else-if="item.entry._totalMissingKwh != null">
                    <button type="button"
                      class="flex items-center gap-1.5 text-xs text-gray-500 dark:text-gray-400 hover:text-blue-500 transition"
                      @click.stop="startGrossEdit(item.entry, $event)">
                      <span class="tabular-nums font-medium">{{ item.entry._totalMissingKwh }} kWh {{ t(item.entry._missingLabel) }}</span>
                      <PencilSquareIcon class="w-3 h-3 text-gray-400" />
                    </button>
                  </template>
                  <template v-else>
                    <button type="button"
                      class="flex items-center gap-1 text-xs text-gray-400 dark:text-gray-500 hover:text-blue-500 transition"
                      @click.stop="startGrossEdit(item.entry, $event)">
                      <PlusIcon class="w-3.5 h-3.5" />
                      {{ t(item.entry._addMissingLabel) }}
                    </button>
                  </template>
                </div>

                <!-- Top-Up rows -->
                <Transition name="slide-down">
                  <div v-if="expandedGroups.has(item.entry.id)" class="bg-blue-50/30 dark:bg-blue-950/20">
                    <template v-for="topUp in item.entry._topUps" :key="topUp.id + '__d'">
                    <div
                      :class="[FEED_GRID_COLS, 'items-start px-3 py-1.5 border-t border-blue-200/40 dark:border-blue-800/30 hover:bg-blue-50/60 dark:hover:bg-blue-900/20 transition']">
                      <div class="flex items-center gap-1.5 pl-4 text-gray-500 text-xs pt-0.5">└</div>
                      <div class="whitespace-nowrap">
                        <div class="text-sm font-medium text-blue-700 dark:text-blue-300">+{{ topUp.kwhAtVehicle ?? topUp.kwhCharged ?? '-' }} kWh</div>
                        <div v-if="topUp.kwhCharged != null && topUp.kwhAtVehicle != null"
                          class="text-xs text-gray-400 dark:text-gray-500">
                          {{ topUp.kwhCharged }} kWh {{ t('dashboard.ac_gross_label_brutto') }} · <span :class="chargingEfficiency(topUp.kwhCharged, topUp.kwhAtVehicle)! >= 90 ? 'text-green-600 dark:text-green-400' : 'text-amber-600 dark:text-amber-400'">{{ chargingEfficiency(topUp.kwhCharged, topUp.kwhAtVehicle) }}%</span>
                        </div>
                      </div>
                      <div class="text-xs text-gray-500 dark:text-gray-400 whitespace-nowrap truncate pt-0.5 flex items-center gap-1.5">
                        <span class="truncate">
                          <template v-if="item.entry._spansMultipleDays">{{ formatLogDate(topUp.loggedAt) }}</template>
                          <template v-else><ClockIcon class="w-3 h-3 inline-block mr-0.5 -mt-0.5" />{{ new Date(topUp.loggedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) }}</template>
                        </span>
                        <button
                          v-if="((topUp.hasPowerCurve && authStore.canViewLiveAnalytics) || (topUp.hasSocCurve && authStore.canViewSocCurve))"
                          type="button"
                          @click.stop="openPowerCurve(topUp)"
                          :aria-label="t('dashboard.show_power_curve')"
                          aria-haspopup="dialog"
                          class="p-0.5 rounded text-emerald-600 dark:text-emerald-400 hover:bg-emerald-100/40 dark:hover:bg-emerald-900/30 transition focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-400 flex-shrink-0"
                        >
                          <ChartBarSquareIcon class="w-4 h-4" />
                        </button>
                      </div>
                      <div class="text-gray-400 dark:text-gray-600 text-xs pt-0.5">-</div>
                      <div class="text-xs text-gray-600 dark:text-gray-300 whitespace-nowrap pt-0.5">
                        <template v-if="topUp.socAfterChargePercent != null">{{ formatSocRange(topUp.socBeforeChargePercent, topUp.socAfterChargePercent) }}</template>
                        <span v-else class="text-gray-400 dark:text-gray-600">-</span>
                      </div>
                      <div class="text-xs text-gray-600 dark:text-gray-300 whitespace-nowrap pt-0.5">
                        <template v-if="topUp.maxChargingPowerKw">{{ topUp.maxChargingPowerKw }} kW</template>
                        <span v-else class="text-gray-400 dark:text-gray-600">-</span>
                      </div>
                      <div class="text-xs text-gray-500 dark:text-gray-400 pt-0.5">
                        <template v-if="topUp.chargeDurationMinutes">{{ topUp.chargeDurationMinutes }}min</template>
                        <span v-else class="text-gray-400 dark:text-gray-600">-</span>
                      </div>
                      <div class="pt-0.5">
                        <span v-if="topUp.temperatureCelsius != null"
                          :class="['inline-flex items-center gap-0.5 px-2 py-0.5 border rounded text-xs whitespace-nowrap', tempBadgeClass(topUp.temperatureCelsius)]">
                          <SunIcon class="w-3 h-3" />{{ topUp.temperatureCelsius.toFixed(1) }}°C
                        </span>
                        <span v-else class="text-gray-400 dark:text-gray-600 text-xs">-</span>
                      </div>
                      <div class="text-gray-400 dark:text-gray-600 text-xs pt-0.5">-</div>
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
                          class="absolute right-0 top-full mt-1 w-40 bg-white dark:bg-gray-700 rounded-sm shadow-[4px_4px_0_rgba(0,0,0,0.30)] dark:shadow-[4px_4px_0_rgba(255,255,255,0.30)] border border-gray-200 dark:border-gray-600 z-50 py-1 overflow-hidden">
                          <button role="menuitem" type="button" @click.stop="editingLog = topUp; openMenuTopUpId = null"
                            class="w-full flex items-center gap-2.5 px-3 py-2 text-sm text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-600 transition focus:outline-none focus-visible:bg-gray-100 dark:focus-visible:bg-gray-600">
                            <PencilSquareIcon class="w-4 h-4 flex-shrink-0" aria-hidden="true" />{{ t('dashboard.action_edit') }}
                          </button>
                          <button role="menuitem" type="button" @click.stop="openMergeModal(topUp); openMenuTopUpId = null"
                            class="w-full flex items-center gap-2.5 px-3 py-2 text-sm text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-600 transition focus:outline-none focus-visible:bg-gray-100 dark:focus-visible:bg-gray-600">
                            <LinkIcon class="w-4 h-4 flex-shrink-0" aria-hidden="true" />{{ t('dashboard.action_merge') }}
                          </button>
                          <button v-if="otherCars.length > 0" role="menuitem" type="button" @click.stop="openReassignModal(topUp); openMenuTopUpId = null"
                            class="w-full flex items-center gap-2.5 px-3 py-2 text-sm text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-600 transition focus:outline-none focus-visible:bg-gray-100 dark:focus-visible:bg-gray-600">
                            <ArrowsRightLeftIcon class="w-4 h-4 flex-shrink-0" aria-hidden="true" />{{ t('dashboard.action_reassign') }}
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
                    </template>
                    <!-- Ein Hinweis pro Gruppe statt eines Schlosses je Teilladung -->
                    <button
                      v-if="!authStore.canViewLiveAnalytics && purchasesAvailable() && curveTopUpCount(item.entry) > 0"
                      type="button"
                      aria-haspopup="dialog"
                      class="w-full flex items-center gap-1.5 px-3 py-2 text-xs text-amber-700 dark:text-amber-300 hover:bg-amber-50 dark:hover:bg-amber-900/20 transition focus:outline-none focus-visible:ring-2 focus-visible:ring-amber-400 focus-visible:ring-inset"
                      @click.stop="openPowerCurve(firstCurveTopUp(item.entry))"
                    >
                      <ChartBarSquareIcon class="w-4 h-4 flex-shrink-0" />
                      <span>{{ curveTopUpCount(item.entry) === 1 ? t('dashboard.power_curve_group_locked_one') : t('dashboard.power_curve_group_locked', { count: curveTopUpCount(item.entry) }) }}</span>
                      <LockClosedIcon class="w-3 h-3 flex-shrink-0 opacity-70" />
                    </button>
                  </div>
                </Transition>
              </div>

              <!-- CHARGE ENTRY -->
              <div>
              <div
                :class="['relative p-3 border-2 rounded-sm space-y-2 gridfeed:hidden',
                         item.entry._isLadegruppe
                           ? 'bg-white dark:bg-gray-700 border-blue-200 dark:border-blue-800 cursor-pointer shadow-[2px_2px_0_0_#bfdbfe] dark:shadow-[2px_2px_0_0_#1e3a8a]'
                           : 'bg-white dark:bg-gray-700 border-gray-300 dark:border-gray-600 shadow-[2px_2px_0_0_#d1d5db] dark:shadow-[2px_2px_0_0_#374151]']"
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
                      <span v-if="item.entry._totalCostEur != null && item.entry._costBasisKwh"
                        :class="['inline-flex items-center px-2 py-0.5 text-xs rounded-full font-medium whitespace-nowrap cursor-pointer transition-all duration-75',
                                 item.entry._costIsNettoOnly ? 'border border-dashed' : 'border',
                                 showCostAbsolute
                                   ? 'bg-gray-50 dark:bg-gray-700 border-gray-200 dark:border-gray-600 text-gray-600 dark:text-gray-300 shadow-[0_4px_0_0_#d1d5db] dark:shadow-[0_4px_0_0_#111827] hover:shadow-[0_2px_0_0_#d1d5db] dark:hover:shadow-[0_2px_0_0_#111827] hover:translate-y-0.5 active:shadow-none active:translate-y-1'
                                   : [(costBadgeClass(item.entry._totalCostEur, item.entry._costBasisKwh) ?? 'bg-green-50 border-green-200 text-green-700'), 'shadow-[0_4px_0_0_#d1d5db] dark:shadow-[0_4px_0_0_#111827] hover:shadow-[0_2px_0_0_#d1d5db] dark:hover:shadow-[0_2px_0_0_#111827] hover:translate-y-0.5 active:shadow-none active:translate-y-1'].join(' ')]"
                        @click.stop="showCostAbsolute = !showCostAbsolute"
                        @mousedown.stop>
                        <template v-if="showCostAbsolute">{{ formatCurrency(item.entry._totalCostEur) }}</template>
                        <template v-else>{{ formatCostPerKwh(item.entry._totalCostEur / item.entry._costBasisKwh) }}</template>
                      </span>
                      <div v-if="otherCars.length > 0" class="relative" @mousedown.stop>
                        <button type="button"
                          @click.stop="openMenuGroupId = openMenuGroupId === item.entry.id ? null : item.entry.id"
                          class="p-1 rounded text-gray-400 hover:text-gray-600 dark:hover:text-gray-200 hover:bg-gray-100 dark:hover:bg-gray-600 transition focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-400"
                          :aria-label="t('dashboard.action_menu_label') || 'Aktionen'"
                          aria-haspopup="menu"
                          :aria-expanded="openMenuGroupId === item.entry.id">
                          <EllipsisVerticalIcon class="w-4 h-4" />
                        </button>
                        <div v-if="openMenuGroupId === item.entry.id"
                          role="menu"
                          class="absolute right-0 bottom-full mb-1 w-44 bg-white dark:bg-gray-700 rounded-sm shadow-[4px_4px_0_rgba(0,0,0,0.30)] dark:shadow-[4px_4px_0_rgba(255,255,255,0.30)] border border-gray-200 dark:border-gray-600 z-50 py-1 overflow-hidden">
                          <button role="menuitem" type="button"
                            @click.stop="openReassignModal(item.entry); openMenuGroupId = null"
                            class="w-full flex items-center gap-2.5 px-3 py-2 text-sm text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-600 transition">
                            <ArrowsRightLeftIcon class="w-4 h-4 flex-shrink-0" />{{ t('dashboard.action_reassign') }}
                          </button>
                        </div>
                      </div>
                      <ChevronDownIcon v-if="!expandedGroups.has(item.entry.id)" class="w-4 h-4 text-blue-400 dark:text-blue-500 flex-shrink-0" />
                      <ChevronUpIcon v-else class="w-4 h-4 text-blue-400 dark:text-blue-500 flex-shrink-0" />
                    </div>
                  </div>
                  <!-- Missing value sub-line (mobile) -->
                  <div v-if="item.entry._canEditMissing"
                    class="flex items-center gap-2 mt-1.5 pt-1.5 border-t border-blue-100 dark:border-blue-800/30"
                    @click.stop @mousedown.stop>
                    <template v-if="grossEditState[item.entry.id] !== undefined">
                      <input
                        type="number" step="0.01" min="0"
                        v-model="grossEditState[item.entry.id]"
                        :aria-label="t(item.entry._missingLabel)"
                        class="w-24 text-xs border border-blue-300 dark:border-blue-700 rounded px-2 py-0.5 bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-1 focus:ring-blue-400"
                        @click.stop @mousedown.stop @keydown.stop
                        @keydown.enter.stop.prevent="applyGrossTotal(item.entry)"
                        @keydown.escape.stop.prevent="cancelGrossEdit(item.entry.id)"
                      />
                      <span class="text-xs text-gray-500 dark:text-gray-400 whitespace-nowrap">kWh {{ t(item.entry._missingLabel) }}</span>
                      <button type="button"
                        class="text-blue-500 hover:text-blue-700 dark:hover:text-blue-300 disabled:opacity-40 transition"
                        :disabled="grossSaving.has(item.entry.id)"
                        @click.stop="applyGrossTotal(item.entry, $event)">
                        <svg v-if="grossSaving.has(item.entry.id)" class="w-4 h-4 animate-spin" fill="none" viewBox="0 0 24 24">
                          <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"/>
                          <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"/>
                        </svg>
                        <CheckIcon v-else class="w-4 h-4" />
                      </button>
                      <button type="button" class="text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 transition"
                        @click.stop="cancelGrossEdit(item.entry.id)">
                        <XMarkIcon class="w-4 h-4" />
                      </button>
                      <span v-if="grossError[item.entry.id]" class="text-xs text-red-500 dark:text-red-400">
                        {{ grossError[item.entry.id] }}
                      </span>
                    </template>
                    <template v-else-if="item.entry._efficiency != null">
                      <button type="button"
                        class="flex flex-wrap items-center gap-1.5 text-xs text-gray-500 dark:text-gray-400 hover:text-blue-500 transition"
                        @click.stop="startGrossEdit(item.entry, $event)">
                        <span class="tabular-nums font-medium whitespace-nowrap">{{ item.entry._bruttoSum }} kWh {{ t('dashboard.ac_gross_label_brutto') }}</span>
                        <span :class="['font-medium whitespace-nowrap', item.entry._efficiency >= 90 ? 'text-green-600 dark:text-green-400' : 'text-amber-600 dark:text-amber-400']">· {{ item.entry._efficiency }}% {{ t('dashboard.ac_gross_efficiency') }}</span>
                        <PencilSquareIcon class="w-3.5 h-3.5 text-gray-400" />
                      </button>
                    </template>
                    <template v-else-if="item.entry._totalMissingKwh != null">
                      <button type="button"
                        class="flex items-center gap-1.5 text-xs text-gray-500 dark:text-gray-400 hover:text-blue-500 transition"
                        @click.stop="startGrossEdit(item.entry, $event)">
                        <span class="tabular-nums font-medium">{{ item.entry._totalMissingKwh }} kWh {{ t(item.entry._missingLabel) }}</span>
                        <PencilSquareIcon class="w-3.5 h-3.5 text-gray-400" />
                      </button>
                    </template>
                    <template v-else>
                      <button type="button"
                        class="flex items-center gap-1 text-xs text-gray-400 dark:text-gray-500 hover:text-blue-500 py-1 transition"
                        @click.stop="startGrossEdit(item.entry, $event)">
                        <PlusIcon class="w-3.5 h-3.5" />
                        {{ t(item.entry._addMissingLabel) }}
                      </button>
                    </template>
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
                    <!-- Kurven-Symbol ist zugleich der Ausloeser: auf Mobile erspart das
                         das Aufklappen der Karte. Tap-Flaeche via Padding auf ~28px. -->
                    <button
                      v-if="((item.entry.hasPowerCurve && authStore.canViewLiveAnalytics) || (item.entry.hasSocCurve && authStore.canViewSocCurve))"
                      type="button"
                      @click.stop="openPowerCurve(item.entry)"
                      :aria-label="t('dashboard.show_power_curve')"
                      aria-haspopup="dialog"
                      class="p-1 -m-1 rounded text-emerald-500 dark:text-emerald-400 hover:bg-emerald-100/40 dark:hover:bg-emerald-900/30 transition focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-400 flex-shrink-0"
                    >
                      <ChartBarSquareIcon class="w-4 h-4" />
                    </button>
                    <button
                      v-else-if="(item.entry.hasPowerCurve || item.entry.hasSocCurve) && purchasesAvailable()"
                      type="button"
                      @click.stop="openPowerCurve(item.entry)"
                      :aria-label="t('dashboard.power_curve_locked')"
                      aria-haspopup="dialog"
                      class="relative p-1 -m-1 rounded text-amber-500 dark:text-amber-400 hover:bg-amber-100/40 dark:hover:bg-amber-900/30 transition focus:outline-none focus-visible:ring-2 focus-visible:ring-amber-400 flex-shrink-0"
                    >
                      <ChartBarSquareIcon class="w-4 h-4" />
                      <LockClosedIcon class="absolute bottom-0 right-0 w-2.5 h-2.5" />
                    </button>
                    <ChartBarSquareIcon
                      v-else-if="item.entry.hasPowerCurve || item.entry.hasSocCurve"
                      class="w-3.5 h-3.5 text-emerald-500 dark:text-emerald-400 flex-shrink-0"
                      :aria-label="t('dashboard.show_power_curve')" />
                  </div>
                  <div class="flex items-center gap-1.5 flex-shrink-0">
                    <span v-if="item.entry.costEur != null && (item.entry.kwhCharged ?? item.entry.kwhAtVehicle)"
                      :class="['inline-flex items-center px-2 py-0.5 text-xs rounded-full font-medium whitespace-nowrap cursor-pointer transition-all duration-75',
                               isNettoOnlyCostLog(item.entry) ? 'border border-dashed' : 'border',
                               showCostAbsolute
                                 ? 'bg-gray-50 dark:bg-gray-700 border-gray-200 dark:border-gray-600 text-gray-600 dark:text-gray-300 shadow-[0_4px_0_0_#d1d5db] dark:shadow-[0_4px_0_0_#111827] hover:shadow-[0_2px_0_0_#d1d5db] dark:hover:shadow-[0_2px_0_0_#111827] hover:translate-y-0.5 active:shadow-none active:translate-y-1'
                                 : [(costBadgeClass(item.entry.costEur, item.entry.kwhCharged ?? item.entry.kwhAtVehicle) ?? 'bg-green-50 border-green-200 text-green-700'), 'shadow-[0_4px_0_0_#d1d5db] dark:shadow-[0_4px_0_0_#111827] hover:shadow-[0_2px_0_0_#d1d5db] dark:hover:shadow-[0_2px_0_0_#111827] hover:translate-y-0.5 active:shadow-none active:translate-y-1'].join(' ')]"
                      :title="isNettoOnlyCostLog(item.entry) ? t('dashboard.cost_pill_netto_title') : undefined"
                      @click.stop="showCostAbsolute = !showCostAbsolute">
                      <template v-if="showCostAbsolute">{{ formatCurrency(item.entry.costEur) }}</template>
                      <template v-else>{{ formatCostPerKwh(item.entry.costEur / (item.entry.kwhCharged ?? item.entry.kwhAtVehicle)) }}</template>
                    </span>
                    <ChevronDownIcon v-if="!expandedLogs.has(item.entry.id)" class="w-4 h-4 text-gray-400 flex-shrink-0" />
                    <ChevronUpIcon v-else class="w-4 h-4 text-gray-400 flex-shrink-0" />
                  </div>
                </button>
                </template>
                <!-- Brutto/Netto sub-line - always visible for normal logs -->
                <div v-if="!item.entry._isLadegruppe"
                  class="flex flex-wrap items-center gap-2 text-xs text-gray-500 dark:text-gray-400"
                  @click.stop @mousedown.stop>
                  <template v-if="logEditState[item.entry.id] !== undefined">
                    <input type="number" step="0.01" min="0"
                      v-model="logEditState[item.entry.id]"
                      :aria-label="t(logFieldToSet(item.entry) === 'kwhCharged' ? 'dashboard.ac_gross_label_brutto' : 'dashboard.ac_gross_label_netto')"
                      class="w-20 text-xs border border-blue-300 dark:border-blue-700 rounded px-2 py-0.5 bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-1 focus:ring-blue-400"
                      @click.stop @mousedown.stop @keydown.stop
                      @keydown.enter.stop.prevent="applyLogValue(item.entry)"
                      @keydown.escape.stop.prevent="cancelLogEdit(item.entry.id)"
                    />
                    <span class="whitespace-nowrap">kWh {{ t(logFieldToSet(item.entry) === 'kwhCharged' ? 'dashboard.ac_gross_label_brutto' : 'dashboard.ac_gross_label_netto') }}</span>
                    <button type="button" class="text-blue-500 hover:text-blue-700 dark:hover:text-blue-300 disabled:opacity-40 transition" :disabled="logSaving.has(item.entry.id)" @click.stop="applyLogValue(item.entry, $event)">
                      <svg v-if="logSaving.has(item.entry.id)" class="w-4 h-4 animate-spin" fill="none" viewBox="0 0 24 24">
                        <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"/>
                        <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"/>
                      </svg>
                      <CheckIcon v-else class="w-4 h-4" />
                    </button>
                    <button type="button" class="text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 transition" @click.stop="cancelLogEdit(item.entry.id)">
                      <XMarkIcon class="w-4 h-4" />
                    </button>
                    <span v-if="logError[item.entry.id]" class="text-red-500 dark:text-red-400">{{ logError[item.entry.id] }}</span>
                  </template>
                  <template v-else-if="item.entry.kwhCharged != null && item.entry.kwhAtVehicle != null">
                    <button type="button" class="flex flex-wrap items-center gap-x-3 gap-y-0.5 hover:text-blue-500 transition" @click.stop="startLogEdit(item.entry, $event)">
                      <span class="whitespace-nowrap">{{ item.entry.kwhCharged }} kWh {{ t('dashboard.ac_gross_label_brutto') }}</span>
                      <span :class="['font-medium whitespace-nowrap', chargingEfficiency(item.entry.kwhCharged, item.entry.kwhAtVehicle)! >= 90 ? 'text-green-600 dark:text-green-400' : 'text-amber-600 dark:text-amber-400']">
                        {{ chargingEfficiency(item.entry.kwhCharged, item.entry.kwhAtVehicle) }}% {{ t('dashboard.ac_gross_efficiency') }}
                      </span>
                    </button>
                  </template>
                  <template v-else>
                    <button type="button" class="flex items-center gap-1 py-1 hover:text-blue-500 transition" @click.stop="startLogEdit(item.entry, $event)">
                      <PlusIcon class="w-3.5 h-3.5" />
                      {{ t(logFieldToSet(item.entry) === 'kwhCharged' ? 'dashboard.ac_gross_add_brutto' : 'dashboard.ac_gross_add_netto') }}
                    </button>
                    <button v-if="realCostHintFor(item.entry.id)" type="button"
                      class="ml-auto inline-flex items-center gap-1 text-xs text-amber-700 dark:text-amber-300 hover:text-amber-800 dark:hover:text-amber-200 focus:outline-none focus-visible:ring-2 focus-visible:ring-amber-400 rounded-full px-1.5 py-0.5 whitespace-nowrap"
                      :aria-expanded="openRealCostTooltipId === item.entry.id"
                      :aria-label="t('dashboard.real_cost_tooltip_title')"
                      @click.stop="toggleRealCostTooltip(item.entry.id)">
                      <span class="font-medium">
                        {{ t('dashboard.real_cost_label') }} ≈
                        <template v-if="showCostAbsolute">{{ formatCurrency(realCostHintFor(item.entry.id)!.bruttoCostEur) }}</template>
                        <template v-else>{{ formatCostPerKwh(realCostHintFor(item.entry.id)!.bruttoRatePerNettoKwhEur) }}</template>
                      </span>
                      <InformationCircleIcon class="w-4 h-4 flex-shrink-0" aria-hidden="true" />
                    </button>
                  </template>
                </div>
                <!-- Expanded content (normal log only) -->
                <Transition :css="false" @enter="onTripFormEnter" @after-enter="onTripFormAfterEnter" @leave="onTripFormLeave">
                <div v-if="!item.entry._isLadegruppe && expandedLogs.has(item.entry.id)" class="space-y-2">
                  <!-- Plain text stats - Zeile 2 (Real-Cost-Hint rechtsbündig via ml-auto) -->
                  <div v-if="item.entry.consumptionKwhPer100km != null || isShortTrip(item.entry) || item.entry.kwhCountedInNextConsumption || item.entry.chargeDurationMinutes || item.entry.socAfterChargePercent != null || (item.entry.costEur != null && !item.entry.kwhCharged && !item.entry.kwhAtVehicle) || realCostHintFor(item.entry.id)"
                    class="flex flex-wrap gap-x-3 gap-y-0.5 items-center">
                    <button
                      v-if="item.entry.consumptionKwhPer100km == null && item.entry.kwhCountedInNextConsumption"
                      type="button"
                      class="inline-flex items-center gap-1 text-xs text-gray-400 dark:text-gray-500 whitespace-nowrap cursor-pointer focus:outline-none"
                      @click.stop="openTooltipLogId = openTooltipLogId === item.entry.id ? null : item.entry.id">
                      <InformationCircleIcon class="w-3 h-3 flex-shrink-0" />
                      {{ t('dashboard.kwh_in_next_hint') }}
                    </button>
                    <button
                      v-else-if="item.entry.consumptionKwhPer100km == null && isShortTrip(item.entry)"
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
                      <Battery0Icon class="w-3 h-3" />{{ formatSocRange(item.entry.socBeforeChargePercent, item.entry.socAfterChargePercent) }}
                    </span>
                    <span v-if="item.entry.maxChargingPowerKw" class="inline-flex items-center gap-1 text-xs text-gray-500 dark:text-gray-400 whitespace-nowrap">
                      <BoltIcon class="w-3 h-3" />{{ item.entry.maxChargingPowerKw }} kW
                    </span>
                    <!-- Real-Cost-Hint rechtsbündig, Info-Icon öffnet erklärendes Panel.
                         Einheit synchron zur Pill: € im Absolut-Modus, ct/kWh im pro-kWh-Modus. -->
                    <button v-if="realCostHintFor(item.entry.id) && item.entry.kwhCharged != null && item.entry.kwhAtVehicle != null" type="button"
                      class="ml-auto inline-flex items-center gap-1 text-xs text-amber-700 dark:text-amber-300 hover:text-amber-800 dark:hover:text-amber-200 focus:outline-none focus-visible:ring-2 focus-visible:ring-amber-400 rounded-full px-1.5 py-0.5 whitespace-nowrap"
                      :aria-expanded="openRealCostTooltipId === item.entry.id"
                      :aria-label="t('dashboard.real_cost_tooltip_title')"
                      @click.stop="toggleRealCostTooltip(item.entry.id)">
                      <span class="font-medium">
                        {{ t('dashboard.real_cost_label') }} ≈
                        <template v-if="showCostAbsolute">{{ formatCurrency(realCostHintFor(item.entry.id)!.bruttoCostEur) }}</template>
                        <template v-else>{{ formatCostPerKwh(realCostHintFor(item.entry.id)!.bruttoRatePerNettoKwhEur) }}</template>
                      </span>
                      <InformationCircleIcon class="w-4 h-4 flex-shrink-0" aria-hidden="true" />
                    </button>
                  </div>
                  <!-- Chips + Aktions-Menü - Zeile 3 -->
                  <div class="flex flex-wrap gap-1.5 items-center">
                    <span v-if="sourceInfo(item.entry.dataSource)"
                      :class="['inline-flex items-center px-1.5 py-0.5 rounded text-xs font-medium whitespace-nowrap',
                               sourceInfo(item.entry.dataSource)!.classes]">
                      {{ sourceInfo(item.entry.dataSource)!.label }}
                    </span>
                    <span v-if="item.entry.temperatureCelsius != null"
                      :class="['inline-flex items-center gap-0.5 px-2 py-0.5 border rounded text-xs whitespace-nowrap', tempBadgeClass(item.entry.temperatureCelsius)]">
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
                      <button type="button"
                        :aria-label="t('dashboard.action_menu_label') || 'Aktionen'"
                        :aria-expanded="openMenuLogId === item.entry.id"
                        @click.stop="openMenuLogId = openMenuLogId === item.entry.id ? null : item.entry.id"
                        class="p-1 rounded text-gray-400 hover:text-gray-600 dark:hover:text-gray-200 hover:bg-gray-100 dark:hover:bg-gray-600 transition">
                        <EllipsisVerticalIcon class="w-5 h-5" />
                      </button>
                      <div v-if="openMenuLogId === item.entry.id" role="menu"
                        class="absolute right-0 bottom-full mb-1 w-44 bg-white dark:bg-gray-700 rounded-sm shadow-[4px_4px_0_rgba(0,0,0,0.30)] dark:shadow-[4px_4px_0_rgba(255,255,255,0.30)] border border-gray-200 dark:border-gray-600 z-50 py-1 overflow-hidden">
                        <button role="menuitem" type="button" @click.stop="editingLog = item.entry; openMenuLogId = null"
                          class="w-full flex items-center gap-2.5 px-3 py-2 text-sm text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-600 transition">
                          <PencilSquareIcon class="w-4 h-4 flex-shrink-0" />{{ t('dashboard.action_edit') }}
                        </button>
                        <button @click.stop="startAddTrip(item.entry.id); openMenuLogId = null"
                          class="w-full flex items-center gap-2.5 px-3 py-2 text-sm text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-600 transition">
                          <MapIcon class="w-4 h-4 flex-shrink-0" />{{ t('dashboard.action_add_trip') }}
                        </button>
                        <button @click.stop="openMergeModal(item.entry); openMenuLogId = null"
                          class="w-full flex items-center gap-2.5 px-3 py-2 text-sm text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-600 transition">
                          <LinkIcon class="w-4 h-4 flex-shrink-0" />{{ t('dashboard.action_merge') }}
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
                  class="mt-1 p-2.5 rounded-sm bg-amber-50 dark:bg-amber-900/20 border border-amber-200 dark:border-amber-700 text-xs text-amber-800 dark:text-amber-300 space-y-1">
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
                  class="mt-1 p-2.5 rounded-sm bg-gray-50 dark:bg-gray-800 border border-gray-200 dark:border-gray-700 text-xs text-gray-600 dark:text-gray-300 space-y-1">
                  <p class="font-medium">{{ t('dashboard.short_trip_tooltip_title') }}</p>
                  <p>{{ t('dashboard.short_trip_tooltip_desc') }}</p>
                </div>
                <!-- kWh-in-next-window tooltip panel (Teilladung ohne km-Stand) -->
                <div
                  v-if="!item.entry._isLadegruppe && item.entry.consumptionKwhPer100km == null && item.entry.kwhCountedInNextConsumption && openTooltipLogId === item.entry.id"
                  class="mt-1 p-2.5 rounded-sm bg-gray-50 dark:bg-gray-800 border border-gray-200 dark:border-gray-700 text-xs text-gray-600 dark:text-gray-300 space-y-1">
                  <p class="font-medium">{{ t('dashboard.kwh_in_next_tooltip_title') }}</p>
                  <p>{{ t('dashboard.kwh_in_next_tooltip_desc') }}</p>
                </div>
                <!-- Real-cost tooltip panel: source-aware (measured-median vs pauschale) -->
                <div
                  v-if="!item.entry._isLadegruppe && openRealCostTooltipId === item.entry.id && realCostHintFor(item.entry.id)"
                  class="mt-1 p-3 rounded-sm bg-amber-50 dark:bg-amber-900/20 border border-amber-200 dark:border-amber-700 text-xs text-amber-900 dark:text-amber-200 space-y-1.5 leading-relaxed">
                  <p class="font-semibold">{{ t('dashboard.real_cost_tooltip_title') }}</p>
                  <p>{{ realCostHintFor(item.entry.id)!.source === 'measured-median'
                        ? t('dashboard.real_cost_tooltip_measured', { count: realCostHintFor(item.entry.id)!.sampleSize, pct: realCostHintFor(item.entry.id)!.efficiencyPercent }, realCostHintFor(item.entry.id)!.sampleSize)
                        : t('dashboard.real_cost_tooltip_pauschale', { pct: realCostHintFor(item.entry.id)!.efficiencyPercent }) }}</p>
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
                        <span class="text-xs font-semibold text-gray-600 dark:text-gray-300 whitespace-nowrap">{{ topUp.kwhAtVehicle ?? topUp.kwhCharged ?? '-' }} kWh</span>
                        <span class="text-xs text-gray-500 whitespace-nowrap">
                          <template v-if="item.entry._spansMultipleDays">{{ formatLogDate(topUp.loggedAt) }}</template>
                          <template v-else>{{ new Date(topUp.loggedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) }}</template>
                        </span>
                        <span v-if="topUp.chargeDurationMinutes" class="min-[436px]:inline-flex hidden items-center gap-1 text-xs text-gray-500 dark:text-gray-400 whitespace-nowrap">
                          <ClockIcon class="w-3 h-3" />{{ topUp.chargeDurationMinutes }}min
                        </span>
                        <span v-if="topUp.socAfterChargePercent != null" class="min-[436px]:inline-flex hidden items-center gap-1 text-xs text-gray-500 dark:text-gray-400 whitespace-nowrap">
                          <Battery0Icon class="w-3 h-3" />{{ formatSocRange(topUp.socBeforeChargePercent, topUp.socAfterChargePercent) }}
                        </span>
                        <button
                          v-if="((topUp.hasPowerCurve && authStore.canViewLiveAnalytics) || (topUp.hasSocCurve && authStore.canViewSocCurve))"
                          type="button"
                          @click.stop="openPowerCurve(topUp)"
                          :aria-label="t('dashboard.show_power_curve')"
                          aria-haspopup="dialog"
                          class="p-0.5 rounded text-emerald-600 dark:text-emerald-400 hover:bg-emerald-100/40 dark:hover:bg-emerald-900/30 transition focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-400 flex-shrink-0"
                        >
                          <ChartBarSquareIcon class="w-4 h-4" />
                        </button>
                        <div class="relative ml-auto flex-shrink-0">
                          <button @click.stop="openMenuTopUpId = openMenuTopUpId === topUp.id ? null : topUp.id"
                            class="p-1 rounded text-gray-400 hover:text-gray-600 dark:hover:text-gray-200 hover:bg-gray-100 dark:hover:bg-gray-600 transition">
                            <EllipsisVerticalIcon class="w-4 h-4" />
                          </button>
                          <div v-if="openMenuTopUpId === topUp.id"
                            class="absolute right-0 bottom-full mb-1 w-40 bg-white dark:bg-gray-700 rounded-sm shadow-[4px_4px_0_rgba(0,0,0,0.30)] dark:shadow-[4px_4px_0_rgba(255,255,255,0.30)] border border-gray-200 dark:border-gray-600 z-50 py-1 overflow-hidden">
                            <button @click.stop="editingLog = topUp; openMenuTopUpId = null"
                              class="w-full flex items-center gap-2.5 px-3 py-2 text-sm text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-600 transition">
                              <PencilSquareIcon class="w-4 h-4 flex-shrink-0" />{{ t('dashboard.action_edit') }}
                            </button>
                            <button @click.stop="openMergeModal(topUp); openMenuTopUpId = null"
                              class="w-full flex items-center gap-2.5 px-3 py-2 text-sm text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-600 transition">
                              <LinkIcon class="w-4 h-4 flex-shrink-0" />{{ t('dashboard.action_merge') }}
                            </button>
                            <button v-if="otherCars.length > 0" @click.stop="openReassignModal(topUp); openMenuTopUpId = null"
                              class="w-full flex items-center gap-2.5 px-3 py-2 text-sm text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-600 transition">
                              <ArrowsRightLeftIcon class="w-4 h-4 flex-shrink-0" />{{ t('dashboard.action_reassign') }}
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
                          <Battery0Icon class="w-3 h-3" />{{ formatSocRange(topUp.socBeforeChargePercent, topUp.socAfterChargePercent) }}
                        </span>
                      </div>
                      <div v-if="topUp.kwhCharged != null && topUp.kwhAtVehicle != null"
                        class="flex items-center gap-1.5 text-xs text-gray-400 dark:text-gray-500 pl-5">
                        <span class="tabular-nums">{{ topUp.kwhCharged }} kWh {{ t('dashboard.ac_gross_label_brutto') }}</span>
                        <span class="text-gray-300 dark:text-gray-600">·</span>
                        <span :class="['tabular-nums font-medium', chargingEfficiency(topUp.kwhCharged, topUp.kwhAtVehicle)! >= 90 ? 'text-green-600 dark:text-green-400' : 'text-amber-600 dark:text-amber-400']">
                          {{ chargingEfficiency(topUp.kwhCharged, topUp.kwhAtVehicle) }}% {{ t('dashboard.ac_gross_efficiency') }}
                        </span>
                      </div>
                    </div>
                    <!-- Ein Hinweis pro Gruppe statt eines Schlosses je Teilladung -->
                    <button
                      v-if="!authStore.canViewLiveAnalytics && purchasesAvailable() && curveTopUpCount(item.entry) > 0"
                      type="button"
                      aria-haspopup="dialog"
                      class="w-full flex items-center gap-1.5 pt-2 text-xs text-amber-700 dark:text-amber-300 focus:outline-none focus-visible:ring-2 focus-visible:ring-amber-400 rounded-sm"
                      @click.stop="openPowerCurve(firstCurveTopUp(item.entry))"
                    >
                      <ChartBarSquareIcon class="w-4 h-4 flex-shrink-0" />
                      <span>{{ curveTopUpCount(item.entry) === 1 ? t('dashboard.power_curve_group_locked_one') : t('dashboard.power_curve_group_locked', { count: curveTopUpCount(item.entry) }) }}</span>
                      <LockClosedIcon class="w-3 h-3 flex-shrink-0 opacity-70" />
                    </button>
                  </div>
                </Transition>
              </template>
              </div><!-- end charge -->
              </template><!-- end v-else regular entry -->

              </template><!-- end v-for groupedFeed -->
            </template>
          </div>
          <!-- Pagination bottom -->
          <LogsPaginationBar
            :page="logsPage"
            :has-more="hasMoreLogs"
            :page-size="pageSize"
            class="mt-4"
            :date-range="pageDateRange"
            @prev="fetchLogsAndScroll(logsPage - 1)"
            @next="fetchLogsAndScroll(logsPage + 1)"
            @page-size-change="setPageSize"
          />

          <!-- Consumption info accordion (positioned below the list as user reference material) -->
          <ConsumptionInfoBox :min-trips="5" class="mt-6" />
        </div>

        </div>
      </div>
    </Transition>
  </div>

  <!-- Der EditLogModal liegt im CarContextLayout (geteilt mit dem Dashboard) - hier
       wird nur noch `editingLog` gesetzt. -->

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
        <div class="relative w-full sm:max-w-sm bg-white dark:bg-gray-800 rounded-t-2xl sm:rounded-sm shadow-[6px_6px_0_rgba(0,0,0,0.40)] dark:shadow-[6px_6px_0_rgba(255,255,255,0.40)] p-6 space-y-5">
          <div>
            <h3 class="text-base font-semibold text-gray-900 dark:text-gray-100">{{ t('dashboard.reassign_car') }}</h3>
            <p class="text-sm text-gray-500 dark:text-gray-400 mt-1">
              <template v-if="reassignModalEntry?._isLadegruppe">{{ t('dashboard.reassign_car_hint_group', { count: reassignModalEntry._topUps?.length ?? 0 }) }}</template>
              <template v-else>{{ t('dashboard.reassign_car_hint') }}</template>
            </p>
          </div>

          <div class="space-y-2">
            <button
              v-for="car in otherCars"
              :key="car.id"
              @click="reassignSelectedCarId = car.id; reassignError = null"
              :class="['w-full flex items-center gap-3 p-3 rounded-sm border-2 transition text-left',
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
              class="flex-1 px-4 py-2.5 rounded-sm border border-gray-200 dark:border-gray-600 text-sm font-medium text-gray-600 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 transition">
              {{ t('common.cancel') }}
            </button>
            <button @click="doSaveReassign"
              :disabled="!reassignSelectedCarId || reassignSaving"
              class="flex-1 px-4 py-2.5 rounded-sm text-sm font-medium text-white transition disabled:opacity-40"
              :class="reassignSelectedCarId ? 'bg-indigo-600 hover:bg-indigo-700' : 'bg-gray-300 dark:bg-gray-600'">
              {{ reassignSaving ? t('common.saving') : t('common.save') }}
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>

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

  <!-- Mobile sticky bottom bar: bulk expand/collapse with toggle switches -->
  <Teleport to="body">
    <Transition
      enter-active-class="transition duration-200 ease-out"
      enter-from-class="opacity-0 translate-y-2"
      enter-to-class="opacity-100 translate-y-0"
      leave-active-class="transition duration-150 ease-in"
      leave-from-class="opacity-100 translate-y-0"
      leave-to-class="opacity-0 translate-y-2">
      <div v-if="bulkBarVisible"
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
