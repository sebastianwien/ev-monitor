import { groupTripsByDay, type TripDay } from './tripCalculations'

/**
 * Der Log-Feed nach Zeitraum: Tag, Woche oder Monat.
 *
 * Die Aufloesung bestimmt, was eine Zeile ist - nicht, welche Fahrten sichtbar sind. Darunter
 * liegt immer der Tag als zweite Ebene, darunter die einzelnen Fahrten. Fuer die Bilanz wird
 * nichts geschaetzt: Kilometer und Kilowattstunden werden aufsummiert, alles Weitere folgt
 * daraus. Wie viel eine einzelne Fahrt verbraucht hat, entscheidet der Aufrufer ueber
 * {@link PeriodMeasure} - diese Datei kennt keine eigene Verbrauchsformel.
 */
export type PeriodResolution = 'day' | 'week' | 'month'

/**
 * Kuerzeste Strecke, ueber die ein Verbrauch noch etwas aussagt.
 *
 * Der SoC kommt in Schritten von rund 0,1 % herein; auf hundert Metern wird daraus jeder
 * beliebige Wert - 470 kWh/100km sind kein Messergebnis, sondern Rundung.
 */
export const MIN_MEASURED_KM = 0.5

/** Fahrten ohne Zeitstempel gehoeren in einen eigenen Abschnitt statt ins Nichts. */
const UNKNOWN_KEY = 'unknown'

export interface PeriodTotals {
  km: number
  tripCount: number
  chargeCount: number
  chargedKwh: number
  /** Summe der Fahrten mit bekanntem Verbrauch; null, wenn keine einzige einen Wert hat. */
  consumedKwh: number | null
  kwhPer100km: number | null
  costPer100km: number | null
  /** Fahrten, die zur Strecke zaehlen, aber nicht zur Verbrauchsbilanz. */
  unmeasuredTrips: number
  /**
   * True, wenn die Strecke aus dem Odometer-Delta der Ladungen stammt (keine Fahrten) - also
   * eine Schaetzung ist. Ohne Trips ist nicht bestimmbar, wann welcher Abschnitt gefahren
   * wurde; gezaehlt wird nur der Kern zwischen zwei In-Period-Ladungen. Die UI markiert das.
   */
  kmIsOdometerEstimate: boolean
}

/** Ein Kalendertag im Balkenstreifen - der Kalenderblick ohne Raster. */
export interface PeriodDayBar {
  dateKey: string
  km: number
  charged: boolean
}

/**
 * Ein Kalendertag innerhalb eines Zeitraums - die zweite Ebene des Feeds.
 *
 * Ladungen haengen am Tag und nicht an einer Fahrt, denn sie stehen zwischen den Fahrten.
 * Ein Tag, an dem nur geladen wurde, bekommt trotzdem seinen Abschnitt - sonst verschwaende
 * die Ladung, obwohl sie Geld gekostet hat.
 */
export interface PeriodDay<T = any, C = any> extends TripDay<T> {
  charges: C[]
  /** Fahrten und Ladungen des Tages chronologisch gemischt, neueste zuerst. */
  events: PeriodDayEvent<T, C>[]
}

/**
 * Ein Ereignis im Tag - Fahrt oder Ladung. `tripIdx` zeigt in `day.trips`, weil
 * Pausenrechnung und Menueposition weiter ueber die reine Fahrtenliste laufen.
 */
export type PeriodDayEvent<T = any, C = any> =
  | { kind: 'trip'; trip: T; tripIdx: number }
  | { kind: 'charge'; charge: C }

export interface PeriodGroup<T = any, C = any> {
  /** Stabil ueber Rendervorgaenge hinweg, dient als Schluessel fuer Liste und Klappzustand. */
  id: string
  level: PeriodResolution
  periodKey: string
  trips: T[]
  charges: C[]
  days: PeriodDay<T, C>[]
  totals: PeriodTotals
  /** Ein Balken je Kalendertag des Zeitraums; null beim Tag und ohne Zeitstempel. */
  bars: PeriodDayBar[] | null
}

export interface PeriodTripInput {
  tripStartedAt?: string | null
  distanceKm?: number | null
}

export interface PeriodChargeInput {
  loggedAt?: string | null
  /** Odometer-Delta zur vorigen Ladung - die einzige Streckenquelle fuer reine Lade-User. */
  distanceSinceLastChargeKm?: number | null
}

/** Woher die Werte kommen, die diese Datei nur noch addiert. */
export interface PeriodMeasure<T, C> {
  /** Verbrauch einer Fahrt in kWh, oder null wenn unbekannt. */
  kwhOf: (trip: T) => number | null
  /** Kosten einer Fahrt in der Anzeigewaehrung, oder null wenn unbekannt. */
  costOf: (trip: T) => number | null
  /** Geladene Energie eines Ladeeintrags in kWh, oder null wenn unbekannt. */
  chargeKwhOf: (charge: C) => number | null
  /**
   * Wie viele einzelne Ladevorgaenge ein Eintrag repraesentiert - eine Ladegruppe buendelt
   * mehrere Teilladungen und zaehlt entsprechend hoch. Fehlt der Accessor, zaehlt jeder
   * Eintrag als einer.
   */
  chargeCountOf?: (charge: C) => number
}

/**
 * Zeitraum-Schluessel eines Zeitstempels - {@code YYYY-MM-DD} fuer Tag und Woche (Montag),
 * {@code YYYY-MM} fuer den Monat.
 *
 * Tag und Monat entstehen durch Abschneiden: die API liefert lokale Zeitstempel, ein Umweg
 * ueber {@link Date} wuerde sie in UTC verschieben und Fahrten kurz nach Mitternacht dem
 * Vortag zuschlagen. Nur die Woche braucht Kalenderrechnung, und die laeuft auf Mittag in
 * UTC, damit keine Zeitumstellung dazwischenfunkt.
 */
export function periodKeyOf(
  iso: string | null | undefined,
  resolution: PeriodResolution,
): string | null {
  if (!iso || !/^\d{4}-\d{2}-\d{2}/.test(iso)) return null
  const dateKey = iso.slice(0, 10)
  if (resolution === 'day') return dateKey
  if (resolution === 'month') return iso.slice(0, 7)

  const date = new Date(`${dateKey}T12:00:00Z`)
  if (Number.isNaN(date.getTime())) return null
  date.setUTCDate(date.getUTCDate() - ((date.getUTCDay() + 6) % 7))
  return date.toISOString().slice(0, 10)
}

/** Alle Kalendertage eines Zeitraums, aufsteigend - die Achse des Balkenstreifens. */
function calendarDays(periodKey: string, resolution: PeriodResolution): string[] {
  if (resolution === 'day' || periodKey === UNKNOWN_KEY) return []
  const first = resolution === 'week' ? periodKey : `${periodKey}-01`
  const cursor = new Date(`${first}T12:00:00Z`)
  if (Number.isNaN(cursor.getTime())) return []

  const days: string[] = []
  const month = cursor.getUTCMonth()
  const length = resolution === 'week' ? 7 : Infinity
  while (days.length < length && (resolution === 'week' || cursor.getUTCMonth() === month)) {
    days.push(cursor.toISOString().slice(0, 10))
    cursor.setUTCDate(cursor.getUTCDate() + 1)
  }
  return days
}

function barsFor<T extends PeriodTripInput, C extends PeriodChargeInput>(
  periodKey: string,
  resolution: PeriodResolution,
  trips: T[],
  charges: C[],
): PeriodDayBar[] | null {
  const axis = calendarDays(periodKey, resolution)
  if (!axis.length) return null

  const kmByDay = new Map<string, number>()
  for (const trip of trips) {
    const day = trip.tripStartedAt?.slice(0, 10)
    if (day) kmByDay.set(day, (kmByDay.get(day) ?? 0) + (trip.distanceKm ?? 0))
  }
  // Ohne Fahrt am Tag zaehlt der Odometer-Sprung der Ladungen als gefahrene Strecke - aber
  // nur der Kern: das Grenzsegment der aeltesten Ladung wird weggelassen (siehe chargeKernKm),
  // damit die Balkensumme der Header-Strecke entspricht.
  const boundary = oldestCharge(charges)
  const chargeKmByDay = new Map<string, number>()
  const chargedDays = new Set<string>()
  for (const charge of charges) {
    const day = charge.loggedAt?.slice(0, 10)
    if (!day) continue
    chargedDays.add(day)
    const km = charge === boundary ? 0 : (charge.distanceSinceLastChargeKm ?? 0)
    chargeKmByDay.set(day, (chargeKmByDay.get(day) ?? 0) + km)
  }
  return axis.map((dateKey) => ({
    dateKey,
    km: kmByDay.get(dateKey) ?? chargeKmByDay.get(dateKey) ?? 0,
    charged: chargedDays.has(dateKey),
  }))
}

/**
 * Bilanz eines Zeitraums.
 *
 * Die Strecke zaehlt jede Fahrt, der Verbrauch nur die, deren kWh bekannt sind - sonst
 * druecken Fahrten ohne Wert das Ergebnis nach unten und niemand sieht warum. Bezugsgroesse
 * von kWh/100km ist deshalb die gemessene Strecke, nicht die gefahrene.
 */
function totalsOf<T extends PeriodTripInput, C extends PeriodChargeInput>(
  trips: T[],
  charges: C[],
  measure: PeriodMeasure<T, C>,
): PeriodTotals {
  let km = 0
  let consumedKwh = 0
  let measuredKm = 0
  let cost = 0
  let costKm = 0
  let unmeasuredTrips = 0
  let anyKwh = false

  for (const trip of trips) {
    const tripKm = trip.distanceKm ?? 0
    km += tripKm
    const kwh = measure.kwhOf(trip)
    if (kwh == null || trip.distanceKm == null) {
      unmeasuredTrips++
    } else {
      anyKwh = true
      consumedKwh += kwh
      measuredKm += tripKm
    }
    const tripCost = measure.costOf(trip)
    if (tripCost != null && trip.distanceKm != null) {
      cost += tripCost
      costKm += tripKm
    }
  }

  const chargedKwh = charges.reduce((sum, charge) => sum + (measure.chargeKwhOf(charge) ?? 0), 0)
  // Reine Lade-User (Import ohne Fahrten) haben ihre Strecke nur im Odometer-Delta der
  // Ladungen - Fahrten sind die genauere Quelle, ihr Delta darf nicht zusaetzlich zaehlen.
  const chargeKm = chargeKernKm(charges)

  return {
    km: trips.length > 0 ? km : chargeKm,
    tripCount: trips.length,
    chargeCount: charges.reduce((sum, charge) => sum + (measure.chargeCountOf?.(charge) ?? 1), 0),
    chargedKwh,
    consumedKwh: anyKwh ? consumedKwh : null,
    kwhPer100km: measuredKm >= MIN_MEASURED_KM ? (consumedKwh / measuredKm) * 100 : null,
    costPer100km: costKm >= MIN_MEASURED_KM ? (cost / costKm) * 100 : null,
    unmeasuredTrips,
    kmIsOdometerEstimate: trips.length === 0 && charges.length > 0,
  }
}

/** Die aelteste Ladung (kleinster Zeitstempel) - ihr Odometer-Delta ist das Grenzsegment. */
function oldestCharge<C extends PeriodChargeInput>(charges: C[]): C | null {
  let oldest: C | null = null
  for (const charge of charges) {
    if (!charge.loggedAt) continue
    if (oldest == null || charge.loggedAt < oldest.loggedAt!) oldest = charge
  }
  return oldest
}

/**
 * Ladestrecke eines Zeitraums = Kern zwischen In-Period-Ladungen. Das Delta der aeltesten
 * Ladung reicht zur Ladung VOR dem Zeitraum zurueck (Grenzsegment) und ist ohne Trips nicht
 * auf Perioden aufteilbar - es wird weggelassen, nicht geraten. Uebrig bleibt odo(letzte) -
 * odo(erste) der In-Period-Ladungen.
 */
function chargeKernKm<C extends PeriodChargeInput>(charges: C[]): number {
  const boundary = oldestCharge(charges)
  return charges.reduce(
    (sum, charge) => (charge === boundary ? sum : sum + (charge.distanceSinceLastChargeKm ?? 0)),
    0,
  )
}

/**
 * Fasst Fahrten und Ladungen zu Zeitraeumen zusammen.
 *
 * Ein Zeitraum entsteht aus Fahrten wie aus Ladungen. Wer nur laedt und keine Fahrten
 * aufzeichnet - etwa ohne angebundenes Fahrzeug - bekommt sonst einen leeren Feed, obwohl
 * Daten da sind. Am Ende stehen die Zeitraeume wieder absteigend nach Datum.
 */
export function buildPeriodGroups<T extends PeriodTripInput, C extends PeriodChargeInput>(
  trips: T[],
  charges: C[],
  resolution: PeriodResolution,
  measure: PeriodMeasure<T, C>,
): PeriodGroup<T, C>[] {
  const groups: PeriodGroup<T, C>[] = []
  const byKey = new Map<string, PeriodGroup<T, C>>()

  function groupFor(periodKey: string): PeriodGroup<T, C> {
    let group = byKey.get(periodKey)
    if (!group) {
      group = {
        id: `${resolution}:${periodKey}`,
        level: resolution,
        periodKey,
        trips: [],
        charges: [],
        days: [],
        totals: totalsOf<T, C>([], [], measure),
        bars: null,
      }
      byKey.set(periodKey, group)
      groups.push(group)
    }
    return group
  }

  for (const trip of trips) {
    groupFor(periodKeyOf(trip.tripStartedAt, resolution) ?? UNKNOWN_KEY).trips.push(trip)
  }

  for (const charge of charges) {
    const periodKey = periodKeyOf(charge.loggedAt, resolution)
    if (periodKey) groupFor(periodKey).charges.push(charge)
  }

  // Fahrten kommen absteigend herein, Ladungen haben eigene Zeitraeume eroeffnet - erst
  // zusammen ergeben sie wieder eine durchgehende Reihe. 'unknown' hat kein Datum und faellt
  // ans Ende, statt sich zwischen die datierten Zeitraeume zu draengen.
  groups.sort((a, b) => {
    if (a.periodKey === UNKNOWN_KEY) return 1
    if (b.periodKey === UNKNOWN_KEY) return -1
    return b.periodKey.localeCompare(a.periodKey)
  })

  for (const group of groups) {
    group.days = daysOf(group.trips, group.charges)
    group.totals = totalsOf(group.trips, group.charges, measure)
    group.bars = barsFor(group.periodKey, resolution, group.trips, group.charges)
  }
  return groups
}

/**
 * Tage eines Zeitraums, absteigend - Fahrttage und reine Ladetage in einer Reihe.
 *
 * Beide Listen kommen bereits absteigend herein; zusammengefuehrt wird nach dem Datum, damit
 * eine Ladung nicht hinter den Fahrten eines aelteren Tages landet.
 */
function daysOf<T extends PeriodTripInput, C extends PeriodChargeInput>(
  trips: T[],
  charges: C[],
): PeriodDay<T, C>[] {
  const days: PeriodDay<T, C>[] = groupTripsByDay(trips).map((day) => ({ ...day, charges: [], events: [] }))
  const byKey = new Map(days.map((day) => [day.dateKey, day]))

  for (const charge of charges) {
    const dateKey = charge.loggedAt?.slice(0, 10)
    if (!dateKey) continue
    let day = byKey.get(dateKey)
    if (!day) {
      day = { dateKey, trips: [], tripCount: 0, km: 0, charges: [], events: [] }
      byKey.set(dateKey, day)
      days.push(day)
    }
    day.charges.push(charge)
  }
  for (const day of days) {
    day.events = mergeDayEvents(day.trips, day.charges)
    // Ladetag ohne Fahrt: gefahrene Strecke steckt im Odometer-Delta der Ladungen.
    if (day.tripCount === 0) {
      day.km = day.charges.reduce((sum, charge) => sum + (charge.distanceSinceLastChargeKm ?? 0), 0)
    }
  }
  return days.sort((a, b) => b.dateKey.localeCompare(a.dateKey))
}

/**
 * Fahrten und Ladungen eines Tages zu einer Reihe verschraenkt, neueste zuerst.
 *
 * Beide Listen kommen absteigend herein; verglichen wird Abfahrt gegen Ladezeitpunkt,
 * bei Gleichstand oder fehlender Abfahrt steht die Ladung vorn.
 */
function mergeDayEvents<T extends PeriodTripInput, C extends PeriodChargeInput>(
  trips: T[],
  charges: C[],
): PeriodDayEvent<T, C>[] {
  const events: PeriodDayEvent<T, C>[] = []
  let t = 0
  let c = 0
  while (t < trips.length || c < charges.length) {
    const trip = trips[t]
    const charge = charges[c]
    if (charge != null && (trip == null || (charge.loggedAt ?? '') >= (trip.tripStartedAt ?? ''))) {
      events.push({ kind: 'charge', charge })
      c++
    } else {
      events.push({ kind: 'trip', trip, tripIdx: t })
      t++
    }
  }
  return events
}
