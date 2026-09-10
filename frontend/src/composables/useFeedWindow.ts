import { ref, computed, watch } from 'vue'
import { resolveTripWindow, type TripWindow } from '../utils/tripMonthSummary'
import type { PeriodResolution } from '../utils/tripPeriods'

/**
 * Zeitfenster des Log-Feeds. Der Feed laedt keine Seiten mehr, sondern ein Fenster - und das
 * haengt an der Darstellung: Tag und Woche zeigen den laufenden Monat, Monat drei Monate,
 * Einzeln den laufenden Monat und laedt auf Wunsch monatsweise weiter zurueck. Nutzer mit
 * hunderten Fahrten rendern so nur, was sie gerade ansehen.
 *
 * Aufloesung und Zeitraum (pro Aufloesung) halten ueber Sitzungen hinweg.
 */
export type FeedResolution = 'cycle' | PeriodResolution
export const FEED_RESOLUTIONS: FeedResolution[] = ['day', 'week', 'month', 'cycle']
export const FEED_RANGE_DEFAULTS: Record<FeedResolution, string> = {
  day: 'THIS_MONTH', week: 'THIS_MONTH', month: 'LAST_3_MONTHS', cycle: 'THIS_MONTH',
}
/** "Alle" gibt es im Feed bewusst nicht - das ist genau das Fenster, das den Browser stocken laesst. */
export const FEED_TIME_RANGES = ['THIS_MONTH', 'LAST_MONTH', 'LAST_3_MONTHS', 'LAST_6_MONTHS', 'LAST_12_MONTHS', 'THIS_YEAR', 'CUSTOM']

export const FEED_RESOLUTION_KEY = 'logfeed_resolution'
export const FEED_TIME_RANGE_KEY = 'logfeed_time_range'
const FEED_CUSTOM_START_KEY = 'logfeed_custom_start'
const FEED_CUSTOM_END_KEY = 'logfeed_custom_end'

function readStored(key: string): string | null {
  try { return localStorage.getItem(key) } catch { return null }
}
function writeStored(key: string, value: string) {
  try { localStorage.setItem(key, value) } catch { /* Safari private mode, quota */ }
}

function readStoredRanges(): Partial<Record<FeedResolution, string>> {
  try {
    const parsed = JSON.parse(readStored(FEED_TIME_RANGE_KEY) ?? '{}')
    const valid: Partial<Record<FeedResolution, string>> = {}
    for (const r of FEED_RESOLUTIONS) {
      if (FEED_TIME_RANGES.includes(parsed?.[r])) valid[r] = parsed[r]
    }
    return valid
  } catch {
    return {}
  }
}

export function useFeedWindow(now: () => Date = () => new Date()) {
  const storedResolution = readStored(FEED_RESOLUTION_KEY) as FeedResolution | null
  const resolution = ref<FeedResolution>(
    storedResolution && FEED_RESOLUTIONS.includes(storedResolution) ? storedResolution : 'month')
  watch(resolution, (value) => writeStored(FEED_RESOLUTION_KEY, value))

  const ranges = ref<Partial<Record<FeedResolution, string>>>(readStoredRanges())
  watch(ranges, (value) => writeStored(FEED_TIME_RANGE_KEY, JSON.stringify(value)), { deep: true })

  const timeRange = computed<string>({
    get: () => ranges.value[resolution.value] ?? FEED_RANGE_DEFAULTS[resolution.value],
    set: (value) => { ranges.value = { ...ranges.value, [resolution.value]: value } },
  })

  const customStartDate = ref<string>(readStored(FEED_CUSTOM_START_KEY) ?? '')
  const customEndDate = ref<string>(readStored(FEED_CUSTOM_END_KEY) ?? '')
  watch(customStartDate, (v) => writeStored(FEED_CUSTOM_START_KEY, v))
  watch(customEndDate, (v) => writeStored(FEED_CUSTOM_END_KEY, v))

  // "Aeltere laden": verlaengert das Fenster monatsweise nach hinten, bis der Nutzer
  // Zeitraum oder Darstellung wechselt - dann faengt es wieder beim gewaehlten Fenster an.
  const olderMonths = ref(0)
  watch([resolution, timeRange, customStartDate, customEndDate], () => { olderMonths.value = 0 }, { flush: 'sync' })
  const loadOlder = () => { olderMonths.value += 1 }

  const baseWindow = computed<TripWindow>(() => {
    const current = now()
    return resolveTripWindow(timeRange.value, customStartDate.value || null, customEndDate.value || null, current)
      ?? resolveTripWindow(FEED_RANGE_DEFAULTS[resolution.value], null, null, current)!
  })

  const shiftMonths = (ms: number, months: number) => {
    const d = new Date(ms)
    return Date.UTC(d.getUTCFullYear(), d.getUTCMonth() - months, 1)
  }

  const window = computed<TripWindow>(() => ({
    startMs: olderMonths.value > 0 ? shiftMonths(baseWindow.value.startMs, olderMonths.value) : baseWindow.value.startMs,
    endMs: baseWindow.value.endMs,
  }))

  /** Der Monat, den der naechste "Aeltere laden"-Klick dazuholt. */
  const nextOlderMonth = computed(() => new Date(shiftMonths(window.value.startMs, 1)))

  /**
   * Zeitraeume, die "bis jetzt" reichen, schicken kein `to`: Ladungen tragen ihre Zeit als
   * lokale Wandzeit ohne Zone, ein UTC-"jetzt" wuerde die Ladung von heute frueh abschneiden.
   */
  const openEnded = computed(() => !['LAST_MONTH', 'CUSTOM'].includes(timeRange.value))
  const queryParams = computed(() =>
    `&from=${new Date(window.value.startMs).toISOString()}`
    + (openEnded.value ? '' : `&to=${new Date(window.value.endMs).toISOString()}`))

  return { resolution, timeRange, customStartDate, customEndDate, window, queryParams, loadOlder, nextOlderMonth }
}
