import { ref, computed, watch } from 'vue'
import { resolveTripWindow, type TripWindow } from '../utils/tripMonthSummary'
import type { PeriodResolution } from '../utils/tripPeriods'

/**
 * Zeitfenster des Log-Feeds. Der Feed laedt keine Seiten mehr, sondern ein Fenster; Einzeln
 * laedt auf Wunsch monatsweise weiter zurueck. Nutzer mit hunderten Fahrten rendern so nur,
 * was sie gerade ansehen.
 *
 * Ein Zeitraum fuer alle Darstellungen: der Wechsel zwischen Tag, Woche und Monat aendert nur
 * die Gruppierung, nicht, was man sieht. Aufloesung und Zeitraum halten ueber Sitzungen hinweg.
 */
export type FeedResolution = 'cycle' | PeriodResolution
export const FEED_RESOLUTIONS: FeedResolution[] = ['day', 'week', 'month', 'cycle']
export const FEED_RANGE_DEFAULT = 'THIS_MONTH'
/** "Alle" gibt es im Feed bewusst nicht - das ist genau das Fenster, das den Browser stocken laesst. */
export const FEED_TIME_RANGES = ['THIS_MONTH', 'LAST_MONTH', 'LAST_3_MONTHS', 'LAST_6_MONTHS', 'LAST_12_MONTHS', 'THIS_YEAR', 'CUSTOM']

export const FEED_RESOLUTION_KEY = 'logfeed_resolution'
export const FEED_TIME_RANGE_KEY = 'logfeed_time_range'
const FEED_CUSTOM_START_KEY = 'logfeed_custom_start'
const FEED_CUSTOM_END_KEY = 'logfeed_custom_end'

const DAY_MS = 86_400_000

/** Montag 00:00 der Woche, in der `ms` liegt - in UTC gerechnet wie die Monatsgrenzen (Wandzeit). */
function mondayOf(ms: number): number {
  const d = new Date(ms)
  return Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), d.getUTCDate() - ((d.getUTCDay() + 6) % 7))
}

/**
 * Weitet das Fenster auf ganze Wochen bzw. Monate der Darstellung. Eine Gruppe am Rand zeigte
 * sonst nur ihre Tage im Fenster - mit zu kleinen Summen unter einem Kopf, der die ganze Woche
 * nennt. Ein offenes Ende ("bis jetzt") bleibt offen.
 */
function widenToWholePeriods(w: TripWindow, resolution: FeedResolution, openEnd: boolean): TripWindow {
  if (resolution === 'week') {
    return { startMs: mondayOf(w.startMs), endMs: openEnd ? w.endMs : mondayOf(w.endMs) + 7 * DAY_MS - 1 }
  }
  if (resolution === 'month') {
    const start = new Date(w.startMs)
    const end = new Date(w.endMs)
    return {
      startMs: Date.UTC(start.getUTCFullYear(), start.getUTCMonth(), 1),
      endMs: openEnd ? w.endMs : Date.UTC(end.getUTCFullYear(), end.getUTCMonth() + 1, 1) - 1,
    }
  }
  return w
}

function readStored(key: string): string | null {
  try { return localStorage.getItem(key) } catch { return null }
}
function writeStored(key: string, value: string) {
  try { localStorage.setItem(key, value) } catch { /* Safari private mode, quota */ }
}

/**
 * Frueher lag hier ein Zeitraum pro Darstellung als JSON-Map. Davon gilt der Wert der zuletzt
 * genutzten Darstellung - das ist der, den der Nutzer zuletzt gesehen hat.
 */
function readStoredRange(resolution: FeedResolution): string | null {
  const raw = readStored(FEED_TIME_RANGE_KEY)
  if (raw == null || FEED_TIME_RANGES.includes(raw)) return raw
  try {
    const legacy = JSON.parse(raw)?.[resolution]
    return FEED_TIME_RANGES.includes(legacy) ? legacy : null
  } catch {
    return null
  }
}

export function useFeedWindow(now: () => Date = () => new Date()) {
  const storedResolution = readStored(FEED_RESOLUTION_KEY) as FeedResolution | null
  const resolution = ref<FeedResolution>(
    storedResolution && FEED_RESOLUTIONS.includes(storedResolution) ? storedResolution : 'month')
  watch(resolution, (value) => writeStored(FEED_RESOLUTION_KEY, value))

  const storedRange = readStoredRange(resolution.value)
  const timeRange = ref<string>(storedRange ?? FEED_RANGE_DEFAULT)
  // immediate schreibt eine alte Map sofort als einzelnen Wert zurueck.
  watch(timeRange, (value) => writeStored(FEED_TIME_RANGE_KEY, value), { immediate: storedRange != null })

  const customStartDate = ref<string>(readStored(FEED_CUSTOM_START_KEY) ?? '')
  const customEndDate = ref<string>(readStored(FEED_CUSTOM_END_KEY) ?? '')
  watch(customStartDate, (v) => writeStored(FEED_CUSTOM_START_KEY, v))
  watch(customEndDate, (v) => writeStored(FEED_CUSTOM_END_KEY, v))

  // "Aeltere laden": verlaengert das Fenster monatsweise nach hinten, bis der Nutzer den
  // Zeitraum wechselt - dann faengt es wieder beim gewaehlten Fenster an. Ein Wechsel der
  // Darstellung behaelt Zeitraum und Verlaengerung; Woche und Monat runden nur die Raender.
  const olderMonths = ref(0)
  watch([timeRange, customStartDate, customEndDate], () => { olderMonths.value = 0 }, { flush: 'sync' })
  const loadOlder = () => { olderMonths.value += 1 }

  const baseWindow = computed<TripWindow>(() => {
    const current = now()
    return resolveTripWindow(timeRange.value, customStartDate.value || null, customEndDate.value || null, current)
      ?? resolveTripWindow(FEED_RANGE_DEFAULT, null, null, current)!
  })

  const shiftMonths = (ms: number, months: number) => {
    const d = new Date(ms)
    return Date.UTC(d.getUTCFullYear(), d.getUTCMonth() - months, 1)
  }

  const monthWindow = computed<TripWindow>(() => ({
    startMs: olderMonths.value > 0 ? shiftMonths(baseWindow.value.startMs, olderMonths.value) : baseWindow.value.startMs,
    endMs: baseWindow.value.endMs,
  }))

  /** Der Monat, den der naechste "Aeltere laden"-Klick dazuholt. */
  const nextOlderMonth = computed(() => new Date(shiftMonths(monthWindow.value.startMs, 1)))

  /**
   * Zeitraeume, die "bis jetzt" reichen, schicken kein `to`: Ladungen tragen ihre Zeit als
   * lokale Wandzeit ohne Zone, ein UTC-"jetzt" wuerde die Ladung von heute frueh abschneiden.
   */
  const openEnded = computed(() => !['LAST_MONTH', 'CUSTOM'].includes(timeRange.value))

  const window = computed(() => widenToWholePeriods(monthWindow.value, resolution.value, openEnded.value))
  const queryParams = computed(() =>
    `&from=${new Date(window.value.startMs).toISOString()}`
    + (openEnded.value ? '' : `&to=${new Date(window.value.endMs).toISOString()}`))

  return { resolution, timeRange, customStartDate, customEndDate, window, queryParams, loadOlder, nextOlderMonth }
}
