/**
 * Wann eine Fahrt stattfand, als Ueberschrift im Log-Feed.
 *
 * Der Tag steht vorn, weil er es ist, wonach man in einer langen Liste sucht; die Spanne
 * folgt. Faehrt jemand ueber Mitternacht, traegt auch das Ende sein Datum - sonst stuende
 * dort eine Fahrt, die scheinbar rueckwaerts laeuft.
 */
const LOCALE_MAP: Record<string, string> = { en: 'en-GB', nb: 'nb-NO', sv: 'sv-SE' }

const TIME_OPTS: Intl.DateTimeFormatOptions = { hour: '2-digit', minute: '2-digit' }
const DATE_OPTS: Intl.DateTimeFormatOptions = { day: 'numeric', month: 'numeric' }

/**
 * Tag und Spanne getrennt, damit die Darstellung beide unterschiedlich gewichten kann.
 *
 * Ueberschreitet die Fahrt Mitternacht, bleibt das zweite Datum Teil der Spanne: es gehoert
 * zur Ankunft, nicht zur Ueberschrift, und nur der Beginn ordnet die Fahrt in die Liste ein.
 */
export function tripDateTimeParts(
  startIso: string | null | undefined,
  endIso: string | null | undefined,
  locale: string,
): { date: string; time: string } {
  if (!endIso) return { date: '', time: '' }
  const loc = LOCALE_MAP[locale] ?? 'de-DE'
  const end = new Date(endIso)
  const endDate = end.toLocaleDateString(loc, DATE_OPTS)
  const endTime = end.toLocaleTimeString(loc, TIME_OPTS)

  if (!startIso) return { date: endDate, time: endTime }

  const start = new Date(startIso)
  const startTime = start.toLocaleTimeString(loc, TIME_OPTS)
  return {
    date: start.toLocaleDateString(loc, DATE_OPTS),
    time: start.toDateString() === end.toDateString()
      ? `${startTime} - ${endTime}`
      : `${startTime} - ${endDate}, ${endTime}`,
  }
}

/** Beide Teile in einer Zeile - fuer Stellen ohne eigene Gewichtung, etwa den Merge-Dialog. */
export function formatTripDateTimeRange(
  startIso: string | null | undefined,
  endIso: string | null | undefined,
  locale: string,
): string {
  const { date, time } = tripDateTimeParts(startIso, endIso, locale)
  return date ? `${date}, ${time}` : ''
}

/**
 * Zeitspanne einer Ladung: Beginn aus loggedAt, Ende aus Beginn plus Dauer. Ohne Dauer
 * bleibt nur der Beginn - eine Nullspanne waere gelogen. Laeuft die Ladung ueber
 * Mitternacht, traegt das Ende sein Datum, genau wie bei den Fahrten.
 */
export function chargeTimeRange(
  loggedAtIso: string | null | undefined,
  durationMinutes: number | null | undefined,
  locale: string,
): string {
  if (!loggedAtIso) return ''
  const start = new Date(loggedAtIso)
  if (Number.isNaN(start.getTime())) return ''
  if (durationMinutes == null || durationMinutes <= 0) {
    return start.toLocaleTimeString(LOCALE_MAP[locale] ?? 'de-DE', TIME_OPTS)
  }
  const end = new Date(start.getTime() + Math.round(durationMinutes) * 60_000)
  return tripDateTimeParts(loggedAtIso, end.toISOString(), locale).time
}

/** Ab hier interessiert die Minute nicht mehr - eine Nacht ist eine Nacht. */
const COARSE_ABOVE_HOURS = 6

/**
 * Dauer einer Standzeit, so grob wie noetig: Minuten unter einer Stunde, danach Stunden und
 * Minuten, und ab einem halben Tag nur noch volle Stunden.
 *
 * Sprachneutral - "h" und "min" lesen sich in allen vier Sprachen gleich, das umgebende Wort
 * kommt aus der Uebersetzung.
 */
export function formatPauseDuration(minutes: number | null | undefined): string {
  if (minutes == null || minutes <= 0) return ''
  if (minutes < 60) return `${Math.round(minutes)} min`

  const hours = Math.floor(minutes / 60)
  const rest = Math.round(minutes % 60)
  if (hours >= COARSE_ABOVE_HOURS || rest === 0) return `${hours} h`
  return `${hours} h ${rest} min`
}

/** "Heute" und "Gestern" pro Sprache - kurz genug fuer das Datumsband auf schmalen Geraeten. */
const RELATIVE_DAYS: Record<string, [string, string]> = {
  de: ['Heute', 'Gestern'],
  en: ['Today', 'Yesterday'],
  nb: ['I dag', 'I går'],
  sv: ['Idag', 'Igår'],
}

/**
 * Beschriftung eines Tages im Log-Feed: "Heute", "Gestern" oder Wochentag mit Datum.
 *
 * Der Wochentag steht vorn, weil Menschen ihren Alltag ueber Wochentage erinnern und nicht
 * ueber Kalenderzahlen - "Do 20.8." findet man schneller wieder als "20.8.".
 *
 * @param dateKey Tag als {@code YYYY-MM-DD}; alles andere ergibt eine leere Beschriftung
 * @param today   Bezugstag, explizit uebergeben, damit die Funktion testbar bleibt
 */
export function tripDayLabel(dateKey: string, locale: string, today: Date): string {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(dateKey)) return ''
  const loc = LOCALE_MAP[locale] ?? 'de-DE'
  const [heute, gestern] = RELATIVE_DAYS[locale] ?? RELATIVE_DAYS.de

  const day = new Date(`${dateKey}T12:00:00`)
  const reference = new Date(today.getFullYear(), today.getMonth(), today.getDate(), 12)
  const daysApart = Math.round((reference.getTime() - day.getTime()) / 86_400_000)
  if (daysApart === 0) return heute
  if (daysApart === 1) return gestern

  const weekday = day.toLocaleDateString(loc, { weekday: 'short' }).replace(/\.$/, '')
  return `${weekday} ${day.toLocaleDateString(loc, DATE_OPTS)}`
}

/**
 * Kalenderwoche zu ihrem Montag, nach ISO 8601.
 *
 * Massgeblich ist der Donnerstag derselben Woche: er liegt immer in dem Jahr, zu dem die
 * Woche zaehlt. Deshalb gehoert die Woche ab Mo 29.12.2025 bereits zu 2026.
 *
 * @param mondayKey Montag als {@code YYYY-MM-DD}; alles andere ergibt null
 */
export function isoWeekNumber(mondayKey: string): number | null {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(mondayKey)) return null
  const thursday = new Date(`${mondayKey}T12:00:00Z`)
  if (Number.isNaN(thursday.getTime())) return null
  thursday.setUTCDate(thursday.getUTCDate() + 3)

  const firstThursday = new Date(Date.UTC(thursday.getUTCFullYear(), 0, 4, 12))
  firstThursday.setUTCDate(firstThursday.getUTCDate() - ((firstThursday.getUTCDay() + 6) % 7) + 3)
  return 1 + Math.round((thursday.getTime() - firstThursday.getTime()) / (7 * 86_400_000))
}

/**
 * Beschriftung eines Zeitraums im Log-Feed.
 *
 * Der Tag erbt die Sprache des Datumsbands samt "Heute" und "Gestern", die Woche zeigt ihre
 * Spanne und der Monat seinen Namen - Menschen erinnern "August" und "letzte Woche", nicht
 * "2026-08".
 *
 * @param periodKey {@code YYYY-MM-DD} fuer Tag und Woche (deren Montag), {@code YYYY-MM} fuer
 *                  den Monat; alles andere ergibt eine leere Beschriftung
 */
export function periodLabel(
  periodKey: string,
  level: 'day' | 'week' | 'month',
  locale: string,
  today: Date,
): string {
  const loc = LOCALE_MAP[locale] ?? 'de-DE'
  if (level === 'day') return tripDayLabel(periodKey, locale, today)

  if (level === 'month') {
    if (!/^\d{4}-\d{2}$/.test(periodKey)) return ''
    return new Date(`${periodKey}-01T12:00:00`).toLocaleDateString(loc, {
      month: 'long',
      year: 'numeric',
    })
  }

  if (!/^\d{4}-\d{2}-\d{2}$/.test(periodKey)) return ''
  const monday = new Date(`${periodKey}T12:00:00`)
  const sunday = new Date(monday.getTime() + 6 * 86_400_000)
  return `${monday.toLocaleDateString(loc, DATE_OPTS)} - ${sunday.toLocaleDateString(loc, DATE_OPTS)}`
}
