import { ref, computed, nextTick, watch, type Component, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import api from '../api/axios'
import { useAuthStore } from '../stores/auth'
import {
  BoltIcon,
  ArrowDownTrayIcon,
  HomeIcon,
} from '@heroicons/vue/24/outline'
import { enumToLabel } from '../utils/enumLabel'

const PAGE_SIZE = 20

// Backend stores LocalDateTime without timezone - treat as UTC for consistent comparison
function toEpochMs(isoString: string): number {
  const s = isoString.endsWith('Z') || /[+-]\d{2}:\d{2}$/.test(isoString) ? isoString : isoString + 'Z'
  return new Date(s).getTime()
}

// Converts a UTC/offset ISO string to a datetime-local input value in browser-local time
function toLocalDatetimeInput(isoString: string): string {
  const d = new Date(isoString)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

// Converts a datetime-local string ("2026-04-24T14:30") to OffsetDateTime by appending the browser's UTC offset
function toOffsetDateTime(localDt: string): string {
  const off = -new Date().getTimezoneOffset()
  const sign = off >= 0 ? '+' : '-'
  const abs = Math.abs(off)
  const h = String(Math.floor(abs / 60)).padStart(2, '0')
  const m = String(abs % 60).padStart(2, '0')
  return `${localDt}:00${sign}${h}:${m}`
}

export function useLogList(selectedCarId: Ref<string | null>, cars: Ref<any[]>, logsSection: Ref<HTMLElement | null>) {
  const { t, locale } = useI18n()
  const authStore = useAuthStore()

  const logs = ref<any[]>([])
  const trips = ref<any[]>([])
  const logsPage = ref(0)
  const logsLoading = ref(false)
  const hasMoreLogs = ref(false)
  const editingLog = ref<any | null>(null)

  // Ladegruppen expand/collapse
  const expandedGroups = ref<Set<string>>(new Set())

  const toggleLadegruppe = (id: string) => {
    if (expandedGroups.value.has(id)) {
      expandedGroups.value.delete(id)
    } else {
      expandedGroups.value.add(id)
    }
  }

  const hasAnyLogs = computed(() => logs.value.length > 0 || trips.value.length > 0)

  // Display toggles
  const showOdometer = ref(false)
  const showCostAbsolute = ref(false)
  const openTooltipLogId = ref<string | null>(null)

  // Fahrzeug-Zuordnung Modal
  const reassignModalEntry = ref<any | null>(null)
  const reassignSelectedCarId = ref<string | null>(null)
  const reassignSaving = ref(false)
  const reassignError = ref<string | null>(null)
  const reassignSuccessMessage = ref<string | null>(null)
  const otherCars = computed(() => cars.value.filter((c: any) => c.id !== selectedCarId.value))

  const openReassignModal = (entry: any) => {
    reassignModalEntry.value = entry
    reassignSelectedCarId.value = null
    reassignError.value = null
  }

  const saveReassign = async (fetchStatistics: () => Promise<void>) => {
    if (!reassignModalEntry.value || !reassignSelectedCarId.value) return
    const entry = reassignModalEntry.value
    const targetCar = cars.value.find((c: any) => c.id === reassignSelectedCarId.value)
    reassignSaving.value = true
    try {
      await api.patch(`/logs/${entry.id}/car`, { targetCarId: reassignSelectedCarId.value })
      logs.value = logs.value.filter((l: any) => l.id !== entry.id)
      const carLabel = targetCar ? `${enumToLabel(targetCar.brand)} ${enumToLabel(targetCar.model)}`.trim() : ''
      reassignSuccessMessage.value = t('dashboard.reassign_success', { car: carLabel })
      setTimeout(() => { reassignSuccessMessage.value = null }, 3000)
      reassignModalEntry.value = null
      fetchStatistics()
    } catch {
      reassignError.value = t('dashboard.err_load')
    } finally {
      reassignSaving.value = false
    }
  }

  // --- Trip editing state ---
  const editingTripId = ref<string | null>(null)
  const addingTripAfterId = ref<string | null>(null)  // ID of feed entry after which to insert
  const tripForm = ref<Record<string, any>>({})
  const tripSaving = ref(false)
  const tripError = ref<string | null>(null)

  const startEditTrip = (trip: any) => {
    editingTripId.value = trip.id
    addingTripAfterId.value = null
    tripForm.value = {
      tripStartedAt: trip.tripStartedAt ? toLocalDatetimeInput(trip.tripStartedAt) : '',
      tripEndedAt: trip.tripEndedAt ? toLocalDatetimeInput(trip.tripEndedAt) : '',
      distanceKm: trip.distanceKm ?? '',
      routeType: trip.routeType ?? '',
      socStart: trip.socStart ?? '',
      socEnd: trip.socEnd ?? '',
      feedback: trip.feedback ?? null,
    }
    tripError.value = null
  }

  const cancelTripEdit = () => {
    editingTripId.value = null
    addingTripAfterId.value = null
    tripForm.value = {}
    tripError.value = null
  }

  const saveTripEdit = async (tripId: string) => {
    if (!tripForm.value.tripStartedAt || !tripForm.value.tripEndedAt) {
      tripError.value = t('dashboard.trip_err_times_required')
      return
    }
    if (new Date(tripForm.value.tripEndedAt) <= new Date(tripForm.value.tripStartedAt)) {
      tripError.value = t('dashboard.trip_err_end_before_start')
      return
    }
    tripSaving.value = true
    tripError.value = null
    try {
      const payload: any = {
        tripStartedAt: toOffsetDateTime(tripForm.value.tripStartedAt),
        tripEndedAt:   toOffsetDateTime(tripForm.value.tripEndedAt),
      }
      if (tripForm.value.distanceKm !== '')  payload.distanceKm = Number(tripForm.value.distanceKm)
      if (tripForm.value.routeType)          payload.routeType  = tripForm.value.routeType
      if (tripForm.value.socStart !== '')    payload.socStart   = Number(tripForm.value.socStart)
      if (tripForm.value.socEnd !== '')      payload.socEnd     = Number(tripForm.value.socEnd)
      if (tripForm.value.feedback != null)   payload.feedback   = tripForm.value.feedback
      const res = await api.patch(`/trips/${tripId}`, payload)
      const idx = trips.value.findIndex((t: any) => t.id === tripId)
      if (idx !== -1) trips.value[idx] = res.data
      editingTripId.value = null
    } catch {
      tripError.value = t('dashboard.err_load')
    } finally {
      tripSaving.value = false
    }
  }

  const startAddTrip = (afterId: string | null, defaultEndAt?: string) => {
    addingTripAfterId.value = afterId
    editingTripId.value = null
    const pad = (n: number) => String(n).padStart(2, '0')
    const fmt = (d: Date) =>
      `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
    const end = defaultEndAt ? new Date(defaultEndAt) : new Date()
    const start = new Date(end.getTime() - 60 * 60 * 1000)
    tripForm.value = {
      tripStartedAt: fmt(start),
      tripEndedAt: fmt(end),
      distanceKm: '',
      routeType: 'COMBINED',
    }
    tripError.value = null
  }

  const saveNewTrip = async () => {
    if (!selectedCarId.value) return
    if (!tripForm.value.tripStartedAt || !tripForm.value.tripEndedAt) {
      tripError.value = t('dashboard.trip_err_times_required')
      return
    }
    if (new Date(tripForm.value.tripEndedAt) <= new Date(tripForm.value.tripStartedAt)) {
      tripError.value = t('dashboard.trip_err_end_before_start')
      return
    }
    tripSaving.value = true
    tripError.value = null
    try {
      const payload: any = {
        carId: selectedCarId.value,
        tripStartedAt: toOffsetDateTime(tripForm.value.tripStartedAt),
        tripEndedAt: toOffsetDateTime(tripForm.value.tripEndedAt),
      }
      if (tripForm.value.distanceKm !== '') payload.distanceKm = Number(tripForm.value.distanceKm)
      if (tripForm.value.routeType)         payload.routeType = tripForm.value.routeType
      await api.post('/trips', payload)
      await fetchTrips()
      addingTripAfterId.value = null
    } catch {
      tripError.value = t('dashboard.err_load')
    } finally {
      tripSaving.value = false
    }
  }

  const deleteTripEntry = async (tripId: string) => {
    await api.delete(`/trips/${tripId}`)
    trips.value = trips.value.filter((t: any) => t.id !== tripId)
  }

  const mergeTripEntry = async (survivingTripId: string, mergeWithTripId: string) => {
    const res = await api.post(`/trips/${survivingTripId}/merge`, { mergeWithTripId })
    const idx = trips.value.findIndex((t: any) => t.id === survivingTripId)
    if (idx !== -1) trips.value[idx] = res.data
    trips.value = trips.value.filter((t: any) => t.id !== mergeWithTripId)
  }

  const submitTripFeedback = async (tripId: string, feedback: string) => {
    const res = await api.patch(`/trips/${tripId}`, { feedback })
    const idx = trips.value.findIndex((t: any) => t.id === tripId)
    if (idx !== -1) trips.value[idx] = res.data
  }

  // --- Fetch functions ---

  const fetchTrips = async () => {
    if (!selectedCarId.value) return
    if (!authStore.isPremium && !authStore.isBetaTester) return
    try {
      const res = await api.get(`/trips?carId=${selectedCarId.value}`)
      trips.value = res.data
    } catch {
      // keep existing trips
    }
  }

  const fetchLogs = async (page = 0) => {
    if (!selectedCarId.value) return
    logsLoading.value = true
    try {
      const canAccessTrips = authStore.isPremium || authStore.isBetaTester
      const requests: Promise<any>[] = [
        api.get(`/logs?carId=${selectedCarId.value}&limit=${PAGE_SIZE}&page=${page}`),
        ...(canAccessTrips ? [api.get(`/trips?carId=${selectedCarId.value}`)] : []),
      ]
      const [logsRes, tripsRes] = await Promise.all(requests)
      logs.value = logsRes.data
      logsPage.value = page
      hasMoreLogs.value = logsRes.data.length === PAGE_SIZE
      if (canAccessTrips && tripsRes) {
        trips.value = tripsRes.data
      }
    } catch {
      // Network error - keep existing state
    } finally {
      logsLoading.value = false
    }
  }

  const scrollToLogs = async () => {
    await fetchLogs(0)
    await nextTick()
    logsSection.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }

  const fetchLogsAndScroll = async (page: number) => {
    await fetchLogs(page)
    await nextTick()
    logsSection.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }

  const refreshLogsAndGroups = () => {
    fetchLogs(logsPage.value)
  }

  const deleteLog = async (id: string) => {
    if (!confirm(t('dashboard.delete_confirm'))) return
    try {
      await api.delete(`/logs/${id}`)
      refreshLogsAndGroups()
    } catch {
      // Network error
    }
  }

  const formatLogDate = (loggedAt: string) => {
    const d = new Date(loggedAt)
    const isCurrentYear = d.getFullYear() === new Date().getFullYear()
    const loc = locale.value === 'en' ? 'en-GB' : 'de-DE'
    const date = d.toLocaleDateString(loc, { day: 'numeric', month: 'numeric', ...(isCurrentYear ? {} : { year: 'numeric' }) })
    const time = d.toLocaleTimeString(loc, { hour: '2-digit', minute: '2-digit' })
    return `${date}, ${time}`
  }

  const formatTripDate = (isoString: string) => {
    const d = new Date(isoString)
    const loc = locale.value === 'en' ? 'en-GB' : 'de-DE'
    return d.toLocaleDateString(loc, { day: 'numeric', month: 'numeric' })
      + ', '
      + d.toLocaleTimeString(loc, { hour: '2-digit', minute: '2-digit' })
  }

  const formatTripTimeRange = (startIso: string | null | undefined, endIso: string) => {
    const localeMap: Record<string, string> = { en: 'en-GB', nb: 'nb-NO', sv: 'sv-SE' }
    const loc = localeMap[locale.value] ?? 'de-DE'
    const end = new Date(endIso)
    const timeOpts: Intl.DateTimeFormatOptions = { hour: '2-digit', minute: '2-digit' }
    const dateOpts: Intl.DateTimeFormatOptions = { day: 'numeric', month: 'numeric' }
    const endTime = end.toLocaleTimeString(loc, timeOpts)
    const dateStr = end.toLocaleDateString(loc, dateOpts)
    if (!startIso) return `${dateStr}, ${endTime}`
    const start = new Date(startIso)
    const startTime = start.toLocaleTimeString(loc, timeOpts)
    const sameDay = start.toDateString() === end.toDateString()
    if (sameDay) return `${startTime} - ${endTime}, ${dateStr}`
    const startDate = start.toLocaleDateString(loc, dateOpts)
    return `${startDate} ${startTime} - ${dateStr} ${endTime}`
  }

  const toggleOdometerDisplay = (distanceKm: number | null, odometerKm: number | null) => {
    if (distanceKm == null || odometerKm == null) return
    showOdometer.value = !showOdometer.value
  }

  function sourceInfo(ds?: string): { label: string; icon: Component; classes: string } | null {
    switch (ds) {
      case 'TESLA_FLEET_IMPORT':  return { label: 'Supercharger',    icon: BoltIcon,          classes: 'bg-red-50 dark:bg-red-900/30 text-red-700 dark:text-red-400 border border-red-200 dark:border-red-700' }
      case 'TESLA_LIVE':          return { label: 'Tesla',            icon: BoltIcon,          classes: 'bg-red-50 dark:bg-red-900/30 text-red-700 dark:text-red-400 border border-red-200 dark:border-red-700' }
      case 'TESLA_IMPORT':        return { label: 'Tesla',            icon: ArrowDownTrayIcon, classes: 'bg-purple-50 dark:bg-purple-900/30 text-purple-700 dark:text-purple-400 border border-purple-200 dark:border-purple-700' }
      case 'TESLA_MANUAL_IMPORT': return { label: 'Tesla',            icon: ArrowDownTrayIcon, classes: 'bg-purple-50 dark:bg-purple-900/30 text-purple-700 dark:text-purple-400 border border-purple-200 dark:border-purple-700' }
      case 'SPRITMONITOR_IMPORT': return { label: 'SpritMonitor',     icon: ArrowDownTrayIcon, classes: 'bg-purple-50 dark:bg-purple-900/30 text-purple-700 dark:text-purple-400 border border-purple-200 dark:border-purple-700' }
      case 'WALLBOX_OCPP':
      case 'WALLBOX_GOE':         return { label: 'Wallbox',          icon: HomeIcon,          classes: 'bg-blue-50 dark:bg-blue-900/30 text-blue-700 dark:text-blue-400 border border-blue-200 dark:border-blue-700' }
      case 'SMARTCAR_LIVE':       return { label: 'AutoSync',         icon: BoltIcon,          classes: 'bg-teal-50 dark:bg-teal-900/30 text-teal-700 dark:text-teal-400 border border-teal-200 dark:border-teal-700' }
      default:                    return null
    }
  }

  // Merged + sorted log feed (charges + trips interleaved)
  const mergedLogFeed = computed(() => {
    const safeLogs = Array.isArray(logs.value) ? logs.value : []
    const fmtDate = (d: Date) => d.toLocaleDateString(locale.value === 'en' ? 'en-GB' : 'de-DE', { day: 'numeric', month: 'numeric' })

    const makeLadegruppe = (subs: any[], commonDataSource?: string): any => {
      const allSubs = [...subs].sort((a, b) => new Date(a.loggedAt).getTime() - new Date(b.loggedAt).getTime())
      const totalKwh = allSubs.reduce((s: number, l: any) => s + (l.kwhCharged ?? l.kwhAtVehicle ?? 0), 0)
      const subsWithCost = allSubs.filter((l: any) => l.costEur != null)
      const totalCostEur = subsWithCost.length > 0
        ? subsWithCost.reduce((s: number, l: any) => s + l.costEur, 0)
        : null
      const costKwh = subsWithCost.reduce((s: number, l: any) => s + (l.kwhCharged ?? l.kwhAtVehicle ?? 0), 0)
      const maxSoc = allSubs.reduce((m: number | null, l: any) =>
        l.socAfterChargePercent != null ? Math.max(m ?? 0, l.socAfterChargePercent) : m, null)
      const maxPower = allSubs.reduce((m: number | null, l: any) =>
        l.maxChargingPowerKw != null ? Math.max(m ?? 0, l.maxChargingPowerKw) : m, null)
      const dates = allSubs.map((l: any) => new Date(l.loggedAt).toDateString())
      const spansMultipleDays = new Set(dates).size > 1
      const firstDate = new Date(allSubs[0].loggedAt)
      const lastDate = new Date(allSubs[allSubs.length - 1].loggedAt)
      const dateRangeLabel = spansMultipleDays ? `${fmtDate(firstDate)} - ${fmtDate(lastDate)}` : fmtDate(firstDate)
      const newestConsumption = allSubs[0].consumptionKwhPer100km ?? null
      const ds = commonDataSource ?? (new Set(allSubs.map((l: any) => l.dataSource)).size === 1 ? allSubs[0].dataSource : null)
      return {
        ...allSubs[0],
        id: allSubs[0].id,
        _isTopUp: false,
        _isLadegruppe: true,
        _isTrip: false,
        _topUps: [...allSubs].reverse(),
        _totalKwh: Math.round(totalKwh * 100) / 100,
        _totalCostEur: totalCostEur !== null ? Math.round(totalCostEur * 100) / 100 : null,
        _costKwh: Math.round(costKwh * 100) / 100,
        _maxSoc: maxSoc,
        _maxPower: maxPower,
        _spansMultipleDays: spansMultipleDays,
        _dateRangeLabel: dateRangeLabel,
        _totalConsumption: newestConsumption,
        _commonDataSource: ds,
      }
    }

    // Step 1: WALLBOX_GOE/API_UPLOAD ohne Odometer - group by day
    const goeLogs = safeLogs.filter((l: any) =>
      (l.dataSource === 'WALLBOX_GOE' || l.dataSource === 'API_UPLOAD') && l.odometerKm == null
    )
    const goeByDay = new Map<string, any[]>()
    for (const log of goeLogs) {
      const day = (log.loggedAt as string).substring(0, 10)
      if (!goeByDay.has(day)) goeByDay.set(day, [])
      goeByDay.get(day)!.push(log)
    }
    const goeGroupedIds = new Set<string>()
    const goeDayGroupEntries: any[] = []
    for (const [, dayLogs] of goeByDay) {
      if (dayLogs.length < 2) continue
      for (const l of dayLogs) goeGroupedIds.add(l.id)
      goeDayGroupEntries.push(makeLadegruppe(dayLogs, dayLogs[0].dataSource))
    }

    // Step 2: Remaining logs - same-odometer grouping
    const remainingLogs = safeLogs
      .filter((l: any) => !goeGroupedIds.has(l.id))
      .filter((l: any) => l.includeInStatistics || !l.consumptionImplausible)
    const sorted = remainingLogs.sort((a: any, b: any) =>
      new Date(b.loggedAt).getTime() - new Date(a.loggedAt).getTime()
    )
    const topUpChildren = new Map<number, any[]>()
    const skipIndices = new Set<number>()
    let i = 0
    while (i < sorted.length) {
      if (sorted[i].odometerKm == null) { i++; continue }
      let j = i + 1
      while (j < sorted.length && sorted[j].odometerKm != null && sorted[j].odometerKm === sorted[i].odometerKm) { j++ }
      if (j > i + 1) {
        const parentIdx = j - 1
        topUpChildren.set(parentIdx, [])
        for (let k = i; k < j - 1; k++) {
          topUpChildren.get(parentIdx)!.push({ ...sorted[k], _isTopUp: true })
          skipIndices.add(k)
        }
        i = j
      } else { i++ }
    }
    const odometerGroupEntries: any[] = []
    for (let idx = 0; idx < sorted.length; idx++) {
      if (skipIndices.has(idx)) continue
      const topUps: any[] = topUpChildren.get(idx) ?? []
      if (topUps.length > 0) {
        odometerGroupEntries.push(makeLadegruppe([...topUps, sorted[idx]]))
      } else {
        odometerGroupEntries.push({ ...sorted[idx], _isTopUp: false, _isLadegruppe: false, _isTrip: false, _topUps: [] })
      }
    }

    // Step 3: Build charge entries (sorted by date)
    const chargeEntries = [...goeDayGroupEntries, ...odometerGroupEntries].sort((a: any, b: any) => {
      const dateA = toEpochMs(a._isLadegruppe ? a._topUps[0].loggedAt : a.loggedAt)
      const dateB = toEpochMs(b._isLadegruppe ? b._topUps[0].loggedAt : b.loggedAt)
      return dateB - dateA
    })

    // Step 4: Build trip entries
    const tripEntries = (Array.isArray(trips.value) ? trips.value : []).map((trip: any) => ({
      ...trip,
      _isTopUp: false,
      _isLadegruppe: false,
      _isTrip: true,
      _topUps: [],
    }))

    // Step 5: Merge charges + trips, sorted by timestamp descending
    const all = [...chargeEntries, ...tripEntries]
    const entryTs = (e: any): number =>
      e._isTrip
        ? toEpochMs(e.tripEndedAt)
        : toEpochMs(e._isLadegruppe ? e._topUps[0].loggedAt : e.loggedAt)

    all.sort((a: any, b: any) => entryTs(b) - entryTs(a))

    // Annotate each entry with the gap to the next entry (ms) for add-trip button visibility
    for (let i = 0; i < all.length; i++) {
      all[i]._gapToNextMs = i < all.length - 1 ? entryTs(all[i]) - entryTs(all[i + 1]) : null
    }

    // Annotate each entry with phantom drain for the parked gap BEFORE it.
    // all is sorted descending: all[i] = newer entry, all[i+1] = older entry.
    // Drain = energy lost while parked between end of all[i+1] and start of all[i].
    const selectedCar = cars.value.find((c: any) => c.id === selectedCarId.value)
    const capacityKwh: number | null = selectedCar?.effectiveBatteryCapacityKwh ?? null

    const exitSoc = (e: any): number | null => {
      if (e._isTrip) return e.socEnd != null ? Number(e.socEnd) : null
      const soc = e._isLadegruppe ? e._maxSoc : e.socAfterChargePercent
      return soc != null ? Number(soc) : null
    }
    const entrySoc = (e: any): number | null => {
      if (e._isTrip) return e.socStart != null ? Number(e.socStart) : null
      return e.socBeforeChargePercent != null ? Number(e.socBeforeChargePercent) : null
    }
    const exitOdometer = (e: any): number | null => {
      if (e._isTrip) return e.odometerEndKm != null ? Number(e.odometerEndKm) : null
      return e.odometerKm != null ? Number(e.odometerKm) : null
    }
    const entryOdometer = (e: any): number | null => {
      if (e._isTrip) return e.odometerStartKm != null ? Number(e.odometerStartKm) : null
      return e.odometerKm != null ? Number(e.odometerKm) : null
    }

    for (let i = 0; i < all.length; i++) {
      all[i]._phantomDrain = null
      if (i >= all.length - 1) continue

      const newer = all[i]
      const older = all[i + 1]

      // Tesla: direct kWh delta via EnergyRemaining (most precise)
      const olderEnergyEnd = older._isTrip && older.energyRemainingEndKwh != null
        ? Number(older.energyRemainingEndKwh) : null
      const newerEnergyStart = newer._isTrip && newer.energyRemainingStartKwh != null
        ? Number(newer.energyRemainingStartKwh) : null

      // Odometer confidence: car didn't move during the gap
      const odomOlder = exitOdometer(older)
      const odomNewer = entryOdometer(newer)
      const highConfidence = odomOlder != null && odomNewer != null && Math.abs(odomOlder - odomNewer) < 0.5

      let drainKwh: number | null = null

      if (olderEnergyEnd != null && newerEnergyStart != null) {
        drainKwh = olderEnergyEnd - newerEnergyStart
      } else {
        const socOlder = exitSoc(older)
        const socNewer = entrySoc(newer)
        if (socOlder != null && socNewer != null && capacityKwh != null) {
          drainKwh = (socOlder - socNewer) / 100 * capacityKwh
        }
      }

      if (drainKwh != null && drainKwh > 0.05 && highConfidence) {
        newer._phantomDrain = {
          kwh: Math.round(drainKwh * 100) / 100,
          durationMs: entryTs(newer) - entryTs(older),
        }
      }
    }

    // Annotate consecutive trip groups between charge entries
    let currentGroupAnchorId = 'tg_top'
    for (let i = 0; i < all.length; i++) {
      if (!all[i]._isTrip) {
        currentGroupAnchorId = `tg_${all[i].id}`
      } else {
        all[i]._tripGroupId = currentGroupAnchorId
      }
    }
    const groupCounts: Record<string, number> = {}
    for (const e of all) {
      if (e._isTrip) groupCounts[e._tripGroupId] = (groupCounts[e._tripGroupId] ?? 0) + 1
    }
    const groupIndexCounters: Record<string, number> = {}
    for (const e of all) {
      if (e._isTrip) {
        e._tripGroupIndex = groupIndexCounters[e._tripGroupId] ?? 0
        e._tripGroupSize = groupCounts[e._tripGroupId]
        groupIndexCounters[e._tripGroupId] = e._tripGroupIndex + 1
      }
    }

    // Compute per-group summary (total km + date range) for groups of 2+ trips
    const groupTripEntries: Record<string, any[]> = {}
    for (const e of all) {
      if (e._isTrip && e._tripGroupSize > 1) {
        if (!groupTripEntries[e._tripGroupId]) groupTripEntries[e._tripGroupId] = []
        groupTripEntries[e._tripGroupId].push(e)
      }
    }
    for (const entries of Object.values(groupTripEntries)) {
      const totalKm = entries.reduce((s: number, e: any) => s + (e.distanceKm ?? 0), 0)
      // entries sorted descending: first = newest, last = oldest
      const newestTs = entries[0].tripEndedAt ?? entries[0].tripStartedAt
      const oldestTs = entries[entries.length - 1].tripStartedAt ?? entries[entries.length - 1].tripEndedAt
      const dNewest = newestTs ? new Date(newestTs) : null
      const dOldest = oldestTs ? new Date(oldestTs) : null
      const sameDay = dNewest && dOldest && dNewest.toDateString() === dOldest.toDateString()
      const dateRange = dOldest && dNewest
        ? sameDay ? fmtDate(dOldest) : `${fmtDate(dOldest)} - ${fmtDate(dNewest)}`
        : null
      for (const e of entries) {
        e._tripGroupTotalKm = totalKm > 0 ? Math.round(totalKm * 10) / 10 : null
        e._tripGroupDateRange = dateRange
      }
    }

    return all
  })

  // Reset on car change
  watch(selectedCarId, () => {
    logs.value = []
    trips.value = []
    expandedGroups.value = new Set()
    logsPage.value = 0
    hasMoreLogs.value = false
    reassignModalEntry.value = null
    editingTripId.value = null
    addingTripAfterId.value = null
  })

  return {
    logs,
    trips,
    logsPage,
    logsLoading,
    hasMoreLogs,
    editingLog,
    expandedGroups,
    toggleLadegruppe,
    hasAnyLogs,
    showOdometer,
    showCostAbsolute,
    openTooltipLogId,
    // Reassign
    reassignModalEntry,
    reassignSelectedCarId,
    reassignSaving,
    reassignError,
    reassignSuccessMessage,
    otherCars,
    openReassignModal,
    saveReassign,
    // Fetching
    fetchLogs,
    fetchTrips,
    scrollToLogs,
    fetchLogsAndScroll,
    refreshLogsAndGroups,
    deleteLog,
    // Trip editing
    editingTripId,
    addingTripAfterId,
    tripForm,
    tripSaving,
    tripError,
    startEditTrip,
    cancelTripEdit,
    saveTripEdit,
    startAddTrip,
    saveNewTrip,
    deleteTripEntry,
    mergeTripEntry,
    submitTripFeedback,
    // Formatting
    formatLogDate,
    formatTripDate,
    formatTripTimeRange,
    toggleOdometerDisplay,
    sourceInfo,
    // Merged feed
    mergedLogFeed,
  }
}

