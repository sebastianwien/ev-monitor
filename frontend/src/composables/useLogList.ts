import { ref, computed, nextTick, watch, type Component, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import api from '../api/axios'
import {
  BoltIcon,
  ArrowDownTrayIcon,
  HomeIcon,
} from '@heroicons/vue/24/outline'

const PAGE_SIZE = 20

export function useLogList(selectedCarId: Ref<string | null>, cars: Ref<any[]>, logsSection: Ref<HTMLElement | null>) {
  const { t, locale } = useI18n()

  const logs = ref<any[]>([])
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

  const hasAnyLogs = computed(() => logs.value.length > 0)

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

  const fetchLogs = async (page = 0) => {
    if (!selectedCarId.value) return
    logsLoading.value = true
    try {
      const res = await api.get(`/logs?carId=${selectedCarId.value}&limit=${PAGE_SIZE}&page=${page}`)
      logs.value = res.data
      logsPage.value = page
      hasMoreLogs.value = res.data.length === PAGE_SIZE
    } catch {
      // Network error - keep existing log list
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

  // Merged + sorted log feed
  const mergedLogFeed = computed(() => {
    const safeLogs = Array.isArray(logs.value) ? logs.value : []
    const fmtDate = (d: Date) => d.toLocaleDateString(locale.value === 'en' ? 'en-GB' : 'de-DE', { day: 'numeric', month: 'numeric' })

    const makeLadegruppe = (subs: any[], commonDataSource?: string): any => {
      const allSubs = [...subs].sort((a, b) => new Date(a.loggedAt).getTime() - new Date(b.loggedAt).getTime())
      const totalKwh = allSubs.reduce((s: number, l: any) => s + (l.kwhCharged ?? 0), 0)
      const totalCostEur = allSubs.every((l: any) => l.costEur != null)
        ? allSubs.reduce((s: number, l: any) => s + (l.costEur ?? 0), 0)
        : null
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
        _topUps: allSubs,
        _totalKwh: Math.round(totalKwh * 100) / 100,
        _totalCostEur: totalCostEur !== null ? Math.round(totalCostEur * 100) / 100 : null,
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
        odometerGroupEntries.push({ ...sorted[idx], _isTopUp: false, _isLadegruppe: false, _topUps: [] })
      }
    }

    // Step 3: Merge and sort
    return [...goeDayGroupEntries, ...odometerGroupEntries].sort((a: any, b: any) => {
      const dateA = new Date(a._isLadegruppe ? a._topUps[a._topUps.length - 1].loggedAt : a.loggedAt).getTime()
      const dateB = new Date(b._isLadegruppe ? b._topUps[b._topUps.length - 1].loggedAt : b.loggedAt).getTime()
      return dateB - dateA
    })
  })

  // Reset on car change
  watch(selectedCarId, () => {
    logs.value = []
    expandedGroups.value = new Set()
    logsPage.value = 0
    hasMoreLogs.value = false
    reassignModalEntry.value = null
  })

  return {
    logs,
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
    scrollToLogs,
    fetchLogsAndScroll,
    refreshLogsAndGroups,
    deleteLog,
    // Formatting
    formatLogDate,
    toggleOdometerDisplay,
    sourceInfo,
    // Merged feed
    mergedLogFeed,
  }
}

function enumToLabel(value: string | undefined | null): string {
  return (value ?? '').replace(/_/g, ' ').toLowerCase()
    .split(' ')
    .map((w: string) => w.charAt(0).toUpperCase() + w.slice(1))
    .join(' ')
}
