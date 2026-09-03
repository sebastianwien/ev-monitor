import { describe, it, expect } from 'vitest'
import { periodKeyOf, buildPeriodGroups, MIN_MEASURED_KM } from '../tripPeriods'
import { normalizeCharge } from '../recentActivity'

/** Fahrten kommen absteigend aus dem Feed - neueste zuerst. */
const trips = [
  { id: 'mi', tripStartedAt: '2026-08-26T07:37:00', distanceKm: 10, kwh: 1.7, cost: 0.85 },
  { id: 'di', tripStartedAt: '2026-08-25T19:19:00', distanceKm: 20, kwh: 3.3, cost: 1.65 },
  { id: 'so', tripStartedAt: '2026-08-23T09:00:00', distanceKm: 30, kwh: 6.0, cost: 3.00 },
  { id: 'sa', tripStartedAt: '2026-08-22T09:00:00', distanceKm: 40, kwh: 8.0, cost: 4.00 },
  { id: 'jul', tripStartedAt: '2026-07-30T09:00:00', distanceKm: 100, kwh: 15, cost: 7.50 },
]
const charges = [
  { id: 'c1', loggedAt: '2026-08-25T20:00:00', kwh: 12 },
  { id: 'c2', loggedAt: '2026-07-30T20:00:00', kwh: 30 },
]
const measure = {
  kwhOf: (t: any) => t.kwh,
  costOf: (t: any) => t.cost,
  chargeKwhOf: (c: any) => c.kwh,
}
const build = (res: any) => buildPeriodGroups(trips, charges, res, measure)

describe('periodKeyOf', () => {
  it('schneidet Tag und Monat aus dem Zeitstempel, ohne Zeitzonenumweg', () => {
    expect(periodKeyOf('2026-08-26T07:37:00', 'day')).toBe('2026-08-26')
    expect(periodKeyOf('2026-08-26T07:37:00', 'month')).toBe('2026-08')
  })

  it('legt die Woche auf ihren Montag', () => {
    expect(periodKeyOf('2026-08-26T07:37:00', 'week')).toBe('2026-08-24') // Mittwoch
    expect(periodKeyOf('2026-08-24T00:01:00', 'week')).toBe('2026-08-24') // Montag selbst
  })

  it('rechnet den Sonntag zur ablaufenden Woche, nicht zur naechsten', () => {
    expect(periodKeyOf('2026-08-23T09:00:00', 'week')).toBe('2026-08-17')
  })

  it('ist null ohne verwertbaren Zeitstempel', () => {
    expect(periodKeyOf(null, 'day')).toBeNull()
    expect(periodKeyOf('kaputt', 'week')).toBeNull()
  })
})

describe('buildPeriodGroups', () => {
  it('bildet je Aufloesung eine Ebene und behaelt die Reihenfolge des Feeds', () => {
    expect(build('day').map((g) => g.periodKey))
      .toEqual(['2026-08-26', '2026-08-25', '2026-08-23', '2026-08-22', '2026-07-30'])
    expect(build('week').map((g) => g.periodKey)).toEqual(['2026-08-24', '2026-08-17', '2026-07-27'])
    expect(build('month').map((g) => g.periodKey)).toEqual(['2026-08', '2026-07'])
  })

  it('zerlegt jede Gruppe zusaetzlich in Tage', () => {
    const [augustWeek] = build('week')
    expect(augustWeek.days.map((d) => d.dateKey)).toEqual(['2026-08-26', '2026-08-25'])
    expect(augustWeek.days[0].km).toBe(10)
  })

  it('summiert Kilometer und Verbrauch der Gruppe', () => {
    const [august] = build('month')
    expect(august.totals.km).toBe(100)
    expect(august.totals.consumedKwh).toBeCloseTo(19, 5)
    expect(august.totals.tripCount).toBe(4)
    expect(august.totals.kwhPer100km).toBeCloseTo(19, 5)
    expect(august.totals.costPer100km).toBeCloseTo(9.5, 5)
  })

  it('ordnet Ladungen ihrem Zeitraum zu', () => {
    const [august, juli] = build('month')
    expect(august.totals.chargeCount).toBe(1)
    expect(august.totals.chargedKwh).toBe(12)
    expect(juli.charges.map((c: any) => c.id)).toEqual(['c2'])
  })

  it('rechnet Verbrauch nur ueber Fahrten mit bekanntem Wert', () => {
    // Eine Fahrt ohne kWh darf die Bilanz nicht verwaessern - ihre Kilometer zaehlen
    // zur Strecke, aber nicht zur Bezugsgroesse des Verbrauchs.
    const withGap = [...trips, { id: 'gap', tripStartedAt: '2026-08-21T09:00:00', distanceKm: 900, kwh: null, cost: null }]
    const [august] = buildPeriodGroups(withGap, [], 'month', measure)

    expect(august.totals.km).toBe(1000)
    expect(august.totals.kwhPer100km).toBeCloseTo(19, 5)
    expect(august.totals.unmeasuredTrips).toBe(1)
  })

  it('nennt keinen Verbrauch, wenn die Bezugsstrecke zu kurz ist', () => {
    // 100 m SoC-Sprung ergeben jeden beliebigen Wert - lieber gar keinen zeigen.
    const micro = [{ id: 'x', tripStartedAt: '2026-08-26T07:00:00', distanceKm: 0.1, kwh: 0.47, cost: 0.2 }]
    const [day] = buildPeriodGroups(micro, [], 'day', measure)

    expect(MIN_MEASURED_KM).toBeGreaterThan(0.1)
    expect(day.totals.km).toBe(0.1)
    expect(day.totals.kwhPer100km).toBeNull()
    expect(day.totals.costPer100km).toBeNull()
  })

  it('gibt Woche und Monat einen Balken je Kalendertag, Tagen keinen', () => {
    const [augustWeek] = build('week')
    expect(augustWeek.bars).toHaveLength(7)
    expect(augustWeek.bars![0]).toEqual({ dateKey: '2026-08-24', km: 0, charged: false })
    expect(augustWeek.bars![1]).toEqual({ dateKey: '2026-08-25', km: 20, charged: true })

    expect(build('month')[1].bars).toHaveLength(31) // Juli
    expect(build('day')[0].bars).toBeNull()
  })

  it('laesst Fahrten ohne Zeitstempel nicht verschwinden', () => {
    const broken = [{ id: 'b', tripStartedAt: null, distanceKm: 5, kwh: 1, cost: 0.5 }]
    const groups = buildPeriodGroups(broken, [], 'week', measure)

    expect(groups).toHaveLength(1)
    expect(groups[0].periodKey).toBe('unknown')
    expect(groups[0].bars).toBeNull()
  })

  it('haengt Ladungen an den Tag, an dem sie passiert sind', () => {
    const [augustWeek] = build('week')
    const dienstag = augustWeek.days.find((d: any) => d.dateKey === '2026-08-25')

    expect(dienstag!.charges.map((c: any) => c.id)).toEqual(['c1'])
    expect(augustWeek.days[0].charges).toEqual([])
  })

  it('mischt Fahrten und Ladungen eines Tages chronologisch, neueste zuerst', () => {
    // Eine Mittagsladung gehoert zwischen die Fahrten, nicht als Block darueber.
    const dayTrips = [
      { id: 'nachmittag', tripStartedAt: '2026-08-25T16:00:00', distanceKm: 10, kwh: 2, cost: 1 },
      { id: 'morgen', tripStartedAt: '2026-08-25T08:00:00', distanceKm: 10, kwh: 2, cost: 1 },
    ]
    const mittag = [{ id: 'mittag', loggedAt: '2026-08-25T12:00:00', kwh: 20 }]
    const [tag] = buildPeriodGroups(dayTrips, mittag, 'day', measure)
    const [day] = tag.days

    expect(day.events.map((e: any) => (e.kind === 'trip' ? e.trip.id : e.charge.id)))
      .toEqual(['nachmittag', 'mittag', 'morgen'])
    // tripIdx zeigt weiter in day.trips, damit Pausenrechnung und Menueposition stimmen
    expect((day.events[0] as any).tripIdx).toBe(0)
    expect((day.events[2] as any).tripIdx).toBe(1)
  })

  it('gibt einem reinen Ladetag einen eigenen Abschnitt', () => {
    // Sonst faellt eine Ladung an einem Tag ohne Fahrt aus dem Feed heraus.
    const [woche] = buildPeriodGroups(
      [trips[0]],
      [{ id: 'solo', loggedAt: '2026-08-24T18:00:00', kwh: 40 }],
      'week',
      measure,
    )

    expect(woche.days.map((d: any) => d.dateKey)).toEqual(['2026-08-26', '2026-08-24'])
    expect(woche.days[1].tripCount).toBe(0)
    expect(woche.days[1].charges.map((c: any) => c.id)).toEqual(['solo'])
  })

  it('gibt einem Zeitraum ohne einzige Fahrt trotzdem eine Gruppe', () => {
    // Wer nur laedt und keine Fahrten aufzeichnet, saehe sonst einen leeren Feed.
    const groups = buildPeriodGroups([], charges, 'month', measure)

    expect(groups.map((g) => g.periodKey)).toEqual(['2026-08', '2026-07'])
    expect(groups[0].totals.tripCount).toBe(0)
    expect(groups[0].totals.chargedKwh).toBe(12)
    expect(groups[0].days[0].charges.map((c: any) => c.id)).toEqual(['c1'])
  })

  it('zaehlt bei reinen Ladetagen die gefahrenen km aus dem Odometer-Delta der Ladungen', () => {
    // Import-User (XPeng, Spritmonitor) zeichnen keine Fahrten auf - ihre Strecke steckt
    // im Odometer-Sprung zwischen den Ladungen, nicht in einer Trip-Liste.
    const chargeOnly = [
      { id: 'a', loggedAt: '2026-08-27T18:00:00', kwh: 20, distanceSinceLastChargeKm: 137 },
      { id: 'b', loggedAt: '2026-08-27T12:00:00', kwh: 20, distanceSinceLastChargeKm: 123 },
      { id: 'c', loggedAt: '2026-08-26T11:00:00', kwh: 20, distanceSinceLastChargeKm: null },
    ]
    const [august] = buildPeriodGroups([], chargeOnly, 'month', measure)

    expect(august.totals.km).toBe(260)
    expect(august.totals.tripCount).toBe(0)
  })

  it('faellt fuer die Strecke nur auf Ladungen zurueck, wenn es keine Fahrten gibt', () => {
    // Fahrten sind die genauere Quelle - ihr Odometer-Delta darf nicht zusaetzlich zaehlen.
    const withOdo = charges.map((c) => ({ ...c, distanceSinceLastChargeKm: 500 }))
    const [august] = buildPeriodGroups(trips, withOdo, 'month', measure)

    expect(august.totals.km).toBe(100)
  })

  it('faerbt Tagesbalken einer reinen Ladewoche aus dem Kern, nicht dem Grenzsegment', () => {
    // Balkensumme muss der Header-Strecke entsprechen: das Delta der aeltesten Ladung (Grenze)
    // faellt weg, spaetere Ladungen zeigen ihr Delta an ihrem Tag. Der Grenztag bleibt als
    // "geladen" markiert, traegt aber 0 km.
    const chargeOnly = [
      { id: 'b', loggedAt: '2026-08-26T18:00:00', kwh: 20, distanceSinceLastChargeKm: 55 },
      { id: 'a', loggedAt: '2026-08-24T18:00:00', kwh: 20, distanceSinceLastChargeKm: 100 },
    ]
    const [woche] = buildPeriodGroups([], chargeOnly, 'week', measure)

    expect(woche.bars!.find((b) => b.dateKey === '2026-08-26')!.km).toBe(55)
    expect(woche.bars!.find((b) => b.dateKey === '2026-08-24')!.km).toBe(0)
    expect(woche.bars!.find((b) => b.dateKey === '2026-08-24')!.charged).toBe(true)
    expect(woche.totals.km).toBe(55)
  })

  it('ordnet Zeitraeume nach Datum, egal woher sie kamen', () => {
    // Eine Ladung eroeffnet einen Monat, der zwischen zwei Fahrtmonaten liegt.
    const groups = buildPeriodGroups(
      [trips[0], trips[4]],
      [{ id: 'mitte', loggedAt: '2026-08-10T12:00:00', kwh: 20 }],
      'week',
      measure,
    )

    expect(groups.map((g) => g.periodKey)).toEqual(['2026-08-24', '2026-08-10', '2026-07-27'])
  })

  it('kommt mit einem leeren Feed klar', () => {
    expect(buildPeriodGroups([], [], 'month', measure)).toEqual([])
  })

  it('summiert bei einer Ladegruppe das Gruppen-Total, nicht den ersten Teilvorgang', () => {
    // Regression (Prod-Fall DoctorSnuggles, Sept 2026): Zwei Ladungen mit gleichem Odometer
    // mergen im Feed zu einer Ladegruppe. Der Chart las frueher entry.kwhCharged (= nur der
    // erste Teilvorgang, 4,05) statt _totalKwh (53,91) und verschluckte die 49,86-kWh-Ladung
    // -> Monatssumme 41,1 statt 91,0. Der Charge-Measure muss deshalb ueber normalizeCharge
    // laufen, das den Single-vs-Ladegruppe-Branch kennt.
    const groupAwareMeasure = {
      kwhOf: () => null,
      costOf: () => null,
      chargeKwhOf: (c: any) => normalizeCharge(c)?.kwh ?? null,
      chargeCountOf: (c: any) => c._topUps?.length || 1,
    }
    const feed = [
      { id: 's3', loggedAt: '2026-09-03T01:35:00', kwhCharged: 37.1, kwhAtVehicle: null, distanceSinceLastChargeKm: 182 },
      {
        id: 'g1',
        loggedAt: '2026-09-01T18:37:00',
        _isLadegruppe: true,
        _totalKwh: 53.91,
        _totalKwhGross: 53.91,
        _topUps: [{ id: 'l1' }, { id: 'l2' }],
        // Top-Level = nur der erste Teilvorgang (Spread aus allSubs[0]) - die Falle.
        kwhCharged: 4.05,
        kwhAtVehicle: null,
        // Odometer-Delta zur letzten August-Ladung - das unauflösbare Grenzsegment.
        distanceSinceLastChargeKm: 251,
      },
    ]
    const [sept] = buildPeriodGroups([], feed, 'month', groupAwareMeasure)

    expect(sept.totals.chargedKwh).toBeCloseTo(91.01, 2)
    // Einzelne Ladevorgaenge zaehlen: Ladegruppe (2 Teilladungen) + Einzelladung = 3, wie die Kachel.
    expect(sept.totals.chargeCount).toBe(3)
    // Gegenprobe: der naive Accessor (kwhAtVehicle ?? kwhCharged) wuerde nur 41,15 liefern.
    const naive = feed.reduce((s, c: any) => s + (c.kwhAtVehicle ?? c.kwhCharged ?? 0), 0)
    expect(naive).toBeCloseTo(41.15, 2)
  })

  it('zaehlt als Ladestrecke nur den Kern zwischen In-Period-Ladungen, nicht das Grenzsegment', () => {
    // Ehrlicher Boden: Strecke zwischen zwei Ladungen, die BEIDE im Zeitraum liegen, ist
    // eindeutig im Zeitraum gefahren (Kern). Das Delta der aeltesten Ladung reicht zurueck
    // zu einer Ladung VOR dem Zeitraum - ohne Trips nicht auf Perioden aufteilbar, also
    // weggelassen statt geraten. DoctorSnuggles Sept: 251 (Grenze) + 182 (Kern) -> nur 182.
    const feed = [
      { id: 's3', loggedAt: '2026-09-03T01:35:00', kwh: 37.1, distanceSinceLastChargeKm: 182 },
      { id: 's1', loggedAt: '2026-09-01T18:37:00', kwh: 4.05, distanceSinceLastChargeKm: 251 },
    ]
    const [sept] = buildPeriodGroups([], feed, 'month', measure)

    expect(sept.totals.km).toBe(182)
    expect(sept.totals.kmIsOdometerEstimate).toBe(true)
  })

  it('markiert Trip-basierte Strecke nicht als Schaetzung', () => {
    const [aug] = buildPeriodGroups(trips, [], 'month', measure)
    expect(aug.totals.kmIsOdometerEstimate).toBe(false)
  })
})
