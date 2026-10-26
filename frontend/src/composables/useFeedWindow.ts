import { ref, computed, watch } from 'vue'
import type { TripWindow } from '../utils/tripMonthSummary'
import type { PeriodResolution } from '../utils/tripPeriods'

/**
 * Zeitfenster des Log-Feeds. Der Feed laedt keine Seiten, sondern ein festes Fenster je
 * Darstellung: Einzeln den laufenden Monat (wie die Uebersicht), Monat drei Monate, Woche acht
 * Wochen, Tag zwei Wochen. "Aeltere laden" holt je ein weiteres Stueck derselben Groesse. Nutzer
 * mit hunderten Fahrten rendern so nur, was sie gerade ansehen.
 *
 * Die Raender liegen auf ganzen Tagen, Wochen (ab Montag) und Monaten - eine Gruppe am Rand
 * zeigte sonst zu kleine Summen unter einem Kopf, der den ganzen Zeitraum nennt.
 */
export type FeedResolution = 'cycle' | PeriodResolution
export const FEED_RESOLUTIONS: FeedResolution[] = ['day', 'week', 'month', 'cycle']
export const FEED_RESOLUTION_KEY = 'logfeed_resolution'

const DAY_MS = 86_400_000

/**
 * Fenster als Wandzeit in UTC-Notation: Ladungen tragen ihre Zeit als lokale Wandzeit ohne Zone.
 * Daher zaehlt das lokale Datum von "jetzt", nicht das UTC-Datum.
 */
const today = (d: Date) => Date.UTC(d.getFullYear(), d.getMonth(), d.getDate())
const monthStart = (d: Date, back: number) => Date.UTC(d.getFullYear(), d.getMonth() - back, 1)
function mondayOf(ms: number): number {
  return ms - ((new Date(ms).getUTCDay() + 6) % 7) * DAY_MS
}

/** Beginn des Fensters, `steps` Stuecke weiter zurueck; Stueck 0 ist das Standardfenster. */
function windowStart(resolution: FeedResolution, now: Date, steps: number): number {
  switch (resolution) {
    case 'cycle': return monthStart(now, steps)
    case 'month': return monthStart(now, 2 + 3 * steps)
    case 'week': return mondayOf(today(now)) - (7 + 8 * steps) * 7 * DAY_MS
    case 'day': return today(now) - (13 + 14 * steps) * DAY_MS
  }
}

/** Das naechste Stueck fuer "Aeltere laden", lesbar: Monat(e) oder Tage, je nach Darstellung. */
export function formatOlderWindow(w: TripWindow, resolution: FeedResolution, locale: string): string {
  const options: Intl.DateTimeFormatOptions = resolution === 'cycle' || resolution === 'month'
    ? { month: 'short', year: 'numeric', timeZone: 'UTC' }
    : { day: 'numeric', month: 'short', timeZone: 'UTC' }
  const format = new Intl.DateTimeFormat(locale, options)
  if (resolution === 'cycle') return format.format(w.startMs)
  // Bindestrich statt des Halbgeviert- oder Geviertstrichs aus Intl
  return format.formatRange(w.startMs, w.endMs).replace(/\s*[\u2013\u2014]\s*/g, '-')
}

function readStored(key: string): string | null {
  try { return localStorage.getItem(key) } catch { return null }
}
function writeStored(key: string, value: string) {
  try { localStorage.setItem(key, value) } catch { /* Safari private mode, quota */ }
}

export function useFeedWindow(now: () => Date = () => new Date()) {
  const storedResolution = readStored(FEED_RESOLUTION_KEY) as FeedResolution | null
  const resolution = ref<FeedResolution>(
    storedResolution && FEED_RESOLUTIONS.includes(storedResolution) ? storedResolution : 'month')
  watch(resolution, (value) => writeStored(FEED_RESOLUTION_KEY, value))

  // Stuecke, die "Aeltere laden" dazugeholt hat; jede Darstellung beginnt wieder bei ihrem Fenster.
  const olderSteps = ref(0)
  watch(resolution, () => { olderSteps.value = 0 }, { flush: 'sync' })
  const loadOlder = () => { olderSteps.value += 1 }

  const window = computed<TripWindow>(() => {
    const current = now()
    return { startMs: windowStart(resolution.value, current, olderSteps.value), endMs: current.getTime() }
  })

  /** Das Stueck, das der naechste "Aeltere laden"-Klick dazuholt. */
  const nextOlder = computed<TripWindow>(() => ({
    startMs: windowStart(resolution.value, now(), olderSteps.value + 1),
    endMs: window.value.startMs - 1,
  }))

  /** Nur `from`: ein UTC-"jetzt" als `to` wuerde die Ladung von heute frueh abschneiden. */
  const queryParams = computed(() => `&from=${new Date(window.value.startMs).toISOString()}`)

  return { resolution, window, queryParams, loadOlder, nextOlder }
}
