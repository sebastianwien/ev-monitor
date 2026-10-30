import { describe, it, expect } from 'vitest'
import {
  detectLongTrip, detectFirstFrost, detectMonthChange, detectBattery,
  detectRankClosing, detectSilence, detectGap, detectMilestone, detectExpensive, expensiveCandidate,
  momentScore, pickCards, HOUR, DAY, type MomentSeen, type Moment,
} from './storyMoments'
import type { ChargingBenchmark } from '../api/chargingBenchmarkService'

// Samstag, 24.10.2026, 12:00 Ortszeit
const NOW = new Date(2026, 9, 24, 12, 0).getTime()
const iso = (ms: number) => new Date(ms).toISOString()
const NONE: MomentSeen = { shown: {}, dismissed: {} }

describe('detectLongTrip', () => {
  const trip = (id: string, endH: number, km: number) =>
    ({ id, tripStartedAt: iso(NOW - (endH + 2) * HOUR), tripEndedAt: iso(NOW - endH * HOUR), distanceKm: km })

  it('summiert Fahrten der letzten 24 h über 250 km und zählt Ladestopps dazwischen', () => {
    const trips = [trip('a', 20, 180), trip('b', 15, 160)]
    const logs = [
      { id: 'x', loggedAt: iso(NOW - 17 * HOUR), costEur: 18 },
      { id: 'y', loggedAt: iso(NOW - 2 * DAY), costEur: 5 },
    ]
    const m = detectLongTrip({ trips, logs, now: NOW })
    expect(m).toMatchObject({ kind: 'long_trip', km: 340, stops: 1, costEur: 18, at: NOW - 15 * HOUR })
  })

  it('nennt keine Kosten, wenn einem Stopp der Preis fehlt', () => {
    const logs = [{ id: 'x', loggedAt: iso(NOW - 17 * HOUR), costEur: 18 }, { id: 'z', loggedAt: iso(NOW - 16 * HOUR), costEur: null }]
    expect(detectLongTrip({ trips: [trip('a', 20, 180), trip('b', 15, 160)], logs, now: NOW })).toMatchObject({ stops: 2, costEur: null })
  })

  it('merkt sich das Ziel der zuletzt beendeten Fahrt für die Wetterprognose', () => {
    const trips = [{ ...trip('a', 20, 180), locationEndGeohash: 'u0yjjd6j' }, { ...trip('b', 15, 160), locationEndGeohash: 'u1hcvz8x' }]
    expect(detectLongTrip({ trips, logs: [], now: NOW })).toMatchObject({ endGeohash: 'u1hcvz8x' })
  })

  it('bleibt unter 250 km still', () => {
    expect(detectLongTrip({ trips: [trip('a', 3, 120), trip('b', 30, 300)], logs: [], now: NOW })).toBeNull()
  })
})

describe('detectFirstFrost', () => {
  const range = { winter: { km: 254 }, summer: { km: 318 } }
  const cold = { id: 'c', tripStartedAt: iso(NOW - 26 * HOUR), tripEndedAt: iso(NOW - 25 * HOUR), distanceKm: 12, outsideTempCelsius: 3 }

  it('meldet die erste kalte Fahrt mit Winter- und Sommerreichweite, einmal je Saison', () => {
    const m = detectFirstFrost({ trips: [cold, { ...cold, id: 'd', outsideTempCelsius: 9 }], now: NOW, range })
    expect(m).toMatchObject({ kind: 'first_frost', key: 'first_frost:2026', tempC: 3, winterKm: 254, summerKm: 318 })
  })

  it('ordnet Januar der Saison des Vorjahres zu', () => {
    const jan = new Date(2027, 0, 10, 12).getTime()
    const t = { ...cold, tripEndedAt: iso(jan - HOUR) }
    expect(detectFirstFrost({ trips: [t], now: jan, range })?.key).toBe('first_frost:2026')
  })

  it('braucht Saisonwerte und eine Fahrt unter 5 °C in den letzten 3 Tagen', () => {
    expect(detectFirstFrost({ trips: [cold], now: NOW, range: { winter: null, summer: { km: 318 } } })).toBeNull()
    expect(detectFirstFrost({ trips: [{ ...cold, outsideTempCelsius: 5 }], now: NOW, range })).toBeNull()
    expect(detectFirstFrost({ trips: [{ ...cold, tripEndedAt: iso(NOW - 4 * DAY) }], now: NOW, range })).toBeNull()
  })
})

describe('detectMonthChange', () => {
  const previous = { year: 2026, month: 9, distanceKm: 279, energyCostEur: 33, charges: 6, priceless: 0 }

  it('im Folgemonat an jedem Tag, mit Vormonat und Benzinvergleich, bis erledigt', () => {
    const now = new Date(2026, 9, 3, 9).getTime()
    const m = detectMonthChange({ now, previous, petrolEurPerLitre: 2, petrolLitersPer100km: 7.5 })
    expect(m).toMatchObject({ kind: 'month_change', key: 'month_change:2026-9', km: 279, eur: 33, petrolEur: 279 / 100 * 7.5 * 2, dismissible: true, lifetimeMs: Infinity })
    expect(m?.at).toBe(new Date(2026, 9, 1).getTime())
    expect(detectMonthChange({ now: NOW, previous, petrolEurPerLitre: 2, petrolLitersPer100km: 7.5 })).toMatchObject({ key: 'month_change:2026-9' })
  })

  it('vergleicht nicht mit Benzin, wenn dem Vormonat Preise fehlen', () => {
    const now = new Date(2026, 9, 3, 9).getTime()
    expect(detectMonthChange({ now, previous: { ...previous, priceless: 2 }, petrolEurPerLitre: 2, petrolLitersPer100km: 7.5 })).toMatchObject({ petrolEur: null })
  })

  it('schweigt ohne Ladungen im Vormonat', () => {
    expect(detectMonthChange({ now: new Date(2026, 9, 2).getTime(), previous: { ...previous, charges: 0 }, petrolEurPerLitre: 2, petrolLitersPer100km: 7.5 })).toBeNull()
  })
})

describe('detectBattery', () => {
  const entry = (id: string, date: string, soh: number, source = 'CHARGE_LOG') => ({ id, recordedAt: date, sohPercent: soh, source })

  it('meldet einen neuen Eintrag der letzten 3 Tage mit dem Wert von vor mindestens 90 Tagen', () => {
    const m = detectBattery({ history: [entry('n', '2026-10-23', 92), entry('o', '2026-04-20', 93.4), entry('p', '2026-09-01', 92.5)], now: NOW })
    expect(m).toMatchObject({ kind: 'battery', key: 'battery:n', sohPercent: 92, estimated: true, previousSohPercent: 93.4, previousAt: new Date(2026, 3, 20).getTime() })
  })

  it('manuelle Angabe ist keine Schätzung, ohne alten Wert kein Vergleich', () => {
    expect(detectBattery({ history: [entry('n', '2026-10-23', 92, 'MANUAL')], now: NOW })).toMatchObject({ estimated: false, previousSohPercent: null })
  })

  it('schweigt bei älteren Einträgen', () => {
    expect(detectBattery({ history: [entry('n', '2026-10-20', 92)], now: NOW })).toBeNull()
  })
})

describe('detectRankClosing und detectSilence', () => {
  it('Abschluss des Vormonats: alle Treppchenplätze in einer Karte, nach Platz sortiert, bis erledigt', () => {
    const ranks = [
      { category: 'MONTHLY_KWH', ownEntry: { rank: 3, value: 310, unit: 'kWh' } },
      null,
      { category: 'MONTHLY_DISTANCE', ownEntry: { rank: 1, value: 1234, unit: 'km' } },
      { category: 'MONTHLY_CHARGES', ownEntry: { rank: 4, value: 12, unit: '' } },
      { category: 'MONTHLY_NIGHT_OWL', ownEntry: null },
    ]
    const m = detectRankClosing({ ranks, now: NOW })!
    expect(m).toMatchObject({ kind: 'rank', key: 'rank:2026-9', year: 2026, month: 9, dismissible: true, lifetimeMs: Infinity })
    expect(m.kind === 'rank' && m.places).toEqual([
      { category: 'MONTHLY_DISTANCE', rank: 1, value: 1234, unit: 'km' },
      { category: 'MONTHLY_KWH', rank: 3, value: 310, unit: 'kWh' },
    ])
    expect(m.at).toBe(new Date(2026, 9, 1).getTime())
    expect(detectRankClosing({ ranks: [{ category: 'MONTHLY_KWH', ownEntry: { rank: 4, value: 1, unit: 'kWh' } }], now: NOW })).toBeNull()
    expect(detectRankClosing({ ranks: [], now: NOW })).toBeNull()
  })

  it('Stille nach 14 Tagen ohne Ladung, nur bei manuell erfassten Ladungen', () => {
    const log = { id: 'l', loggedAt: iso(NOW - 16 * DAY), kwhAtVehicle: 28.5, dataSource: 'USER_LOGGED' }
    expect(detectSilence({ log, now: NOW, autoSync: true })).toMatchObject({ kind: 'silence', key: 'silence:l', days: 16, kwh: 28.5, autoSync: true, dismissible: true, actionable: true })
    expect(detectSilence({ log: { ...log, dataSource: 'SMARTCAR_LIVE' }, now: NOW, autoSync: false })).toBeNull()
    expect(detectSilence({ log: { ...log, loggedAt: iso(NOW - 10 * DAY) }, now: NOW, autoSync: false })).toBeNull()
  })
})

describe('detectGap', () => {
  const gap = (id: string, hoursAgo: number, extra: object = {}) =>
    ({ id, loggedAt: iso(NOW - hoursAgo * HOUR), kwhAtVehicle: 28.5, costEur: null, priceUnknown: false, ...extra })

  it('fragt nach der einen Ladung ohne Preis, mit kWh', () => {
    const m = detectGap({ logs: [gap('a', 30)], now: NOW })
    expect(m).toMatchObject({ kind: 'gap', key: 'gap:a', logId: 'a', count: 1, kwh: 28.5, actionable: true, at: NOW - 30 * HOUR })
  })

  it('zählt mehrere Lücken, Schlüssel und Zeitpunkt von der neuesten, Datum ab der ältesten', () => {
    const m = detectGap({ logs: [gap('old', 10 * 24), gap('new', 5)], now: NOW })
    expect(m).toMatchObject({ key: 'gap:new', count: 2, at: NOW - 5 * HOUR, since: NOW - 10 * DAY })
  })

  it('übergeht "Weiß ich nicht mehr", Ladungen mit Preis und die gerade geladene', () => {
    const logs = [gap('unknown', 30, { priceUnknown: true }), gap('priced', 30, { costEur: 9 }), gap('fresh', 1), gap('skip', 30)]
    expect(detectGap({ logs, now: NOW, skipLogId: 'skip' })).toBeNull()
  })

  it('ignoriert Lücken älter als 31 Tage', () => {
    expect(detectGap({ logs: [gap('a', 32 * 24)], now: NOW })).toBeNull()
  })
})

describe('expensiveCandidate und detectExpensive', () => {
  const pub = (id: string, hoursAgo: number, extra: object = {}) => ({
    id, loggedAt: iso(NOW - hoursAgo * HOUR), kwhCharged: 40, kwhAtVehicle: 38, costEur: 31.6,
    isPublicCharging: true, cpoName: 'IONITY', chargingType: 'DC', ...extra,
  })
  const bench = (extra: Partial<ChargingBenchmark> = {}): ChargingBenchmark => ({
    cpoName: 'IONITY', chargingType: 'DC', drivers: 18,
    medianPricePerKwh: 0.52, lowerQuartilePricePerKwh: 0.39, upperQuartilePricePerKwh: 0.6,
    cards: [
      { name: 'IONITY', variant: 'Motion', pricePerKwh: 0.49, monthlyFeeEur: 5.99, source: 'CURATED', drivers: null, verifiedOn: '2026-03-25' },
      { name: 'Maingau', variant: 'Standard', pricePerKwh: 0.62, monthlyFeeEur: 0, source: 'CURATED', drivers: null, verifiedOn: '2026-03-25' },
    ],
    ...extra,
  })

  it('nimmt die neueste öffentliche Ladung der letzten 3 Tage mit Betreiber, Ladeart und Preis', () => {
    const logs = [pub('home', 1, { isPublicCharging: false }), pub('nocpo', 2, { cpoName: null }), pub('noprice', 3, { costEur: null }),
      pub('unknown', 4, { chargingType: 'UNKNOWN' }), pub('hit', 20), pub('older', 30)]
    expect(expensiveCandidate(logs, NOW)).toMatchObject({ log: { id: 'hit' }, pricePerKwh: expect.closeTo(0.79, 6) })
  })

  it('rechnet den Preis wie die App je Ladung: an der Säule, sonst am Fahrzeug', () => {
    expect(expensiveCandidate([pub('a', 5, { kwhCharged: null, kwhAtVehicle: 40, costEur: 20 })], NOW)?.pricePerKwh).toBe(0.5)
  })

  it('übergeht Ladungen älter als 3 Tage', () => {
    expect(expensiveCandidate([pub('a', 3 * 24 + 1)], NOW)).toBeNull()
  })

  it('meldet mehr als 20 % über dem, was andere Fahrer dort zahlen, mit der günstigsten Karte', () => {
    const m = detectExpensive({ logs: [pub('a', 20)], benchmark: bench(), now: NOW })
    expect(m).toMatchObject({
      kind: 'expensive', key: 'expensive:a', logId: 'a', cpoName: 'IONITY', at: NOW - 20 * HOUR, lifetimeMs: 3 * DAY,
      pricePerKwh: expect.closeTo(0.79, 6), medianPricePerKwh: 0.52, cardFromPricePerKwh: 0.49,
    })
  })

  it('schweigt bis 20 % über dem Median', () => {
    const at = (cost: number) => detectExpensive({ logs: [pub('a', 20, { costEur: cost })], benchmark: bench(), now: NOW })
    expect(at(40 * 0.62)).toBeNull()
    expect(at(40 * 0.63)).not.toBeNull()
  })

  it('nennt keine Karte, die nicht günstiger ist als der gezahlte Preis', () => {
    const m = detectExpensive({ logs: [pub('a', 20, { costEur: 40 * 0.66 })], benchmark: bench({ cards: [bench().cards[1], { ...bench().cards[0], pricePerKwh: 0.7 }] }), now: NOW })
    expect(m).toMatchObject({ cardFromPricePerKwh: 0.62 })
    const none = detectExpensive({ logs: [pub('a', 20, { costEur: 40 * 0.66 })], benchmark: bench({ cards: [{ ...bench().cards[0], pricePerKwh: 0.7 }] }), now: NOW })
    expect(none).toMatchObject({ cardFromPricePerKwh: null })
  })

  it('ohne Vergleich oder mit anderer Ladeart kein Moment', () => {
    expect(detectExpensive({ logs: [pub('a', 20)], benchmark: null, now: NOW })).toBeNull()
    expect(detectExpensive({ logs: [pub('a', 20)], benchmark: bench({ chargingType: 'AC' }), now: NOW })).toBeNull()
  })
})

describe('detectMilestone', () => {
  const pt = (daysAgo: number, charges: number, distanceKm = 0, consumption: number | null = null) =>
    ({ timestamp: iso(NOW - daysAgo * DAY), charges, distanceKm, consumptionKwhPer100km: consumption })

  it('feiert die 100. Ladung am Tag, an dem sie dazukam', () => {
    const m = detectMilestone({ series: [pt(40, 99), pt(1, 1)], now: NOW, petrolLitersPer100km: 7.5 })
    expect(m).toMatchObject({ kind: 'milestone', key: 'milestone:charges:100', metric: 'charges', value: 100, at: NOW - DAY, charges: 100 })
  })

  it('vergisst Meilensteine nach 3 Tagen und zählt keinen unter der ersten Schwelle', () => {
    expect(detectMilestone({ series: [pt(40, 99), pt(4, 1)], now: NOW, petrolLitersPer100km: 7.5 })).toBeNull()
    expect(detectMilestone({ series: [pt(1, 99)], now: NOW, petrolLitersPer100km: 7.5 })).toBeNull()
  })

  it('10.000 km, mit CO₂ aus Strecke und Verbrauch', () => {
    const m = detectMilestone({ series: [pt(30, 1, 9_900, 18), pt(1, 1, 300, 18)], now: NOW, petrolLitersPer100km: 7.5 })
    // 7,5 l x 2,37 kg = 177,75 g/km Benzin gegen 18 kWh/100 km x 380 g/kWh = 109,35 g/km weniger
    expect(m).toMatchObject({ metric: 'km', value: 10_000, km: 10_200 })
    expect(m!.kind === 'milestone' && m!.co2Kg).toBeCloseTo(1115.4, 1)
  })

  it('CO₂ mit dem Benziner der eigenen Fahrzeugklasse', () => {
    const m = detectMilestone({ series: [pt(30, 1, 9_900, 18), pt(1, 1, 300, 18)], now: NOW, petrolLitersPer100km: 10.5 })
    // 10,5 l x 2,37 kg = 248,85 g/km gegen 68,4 g/km
    expect(m!.kind === 'milestone' && m!.co2Kg).toBeCloseTo(1840.6, 1)
  })

  it('eine Tonne CO₂ weniger', () => {
    expect(detectMilestone({ series: [pt(10, 1, 9_000, 18), pt(1, 1, 200, 18)], now: NOW, petrolLitersPer100km: 7.5 }))
      .toMatchObject({ metric: 'co2', value: 1000 })
  })

  it('Jahrestag der ersten Ladung', () => {
    const first = { timestamp: new Date(2025, 9, 23, 8).toISOString(), charges: 1, distanceKm: 0, consumptionKwhPer100km: null }
    expect(detectMilestone({ series: [first, pt(5, 3)], now: NOW, petrolLitersPer100km: 7.5 }))
      .toMatchObject({ metric: 'years', value: 1, at: new Date(2026, 9, 23, 8).getTime(), charges: 4 })
  })

  it('mehrere zugleich: der jüngste gewinnt', () => {
    const m = detectMilestone({ series: [pt(30, 98, 9_900), pt(2, 2), pt(1, 1, 200)], now: NOW, petrolLitersPer100km: 7.5 })
    expect(m).toMatchObject({ metric: 'km', value: 10_000 })
  })
})

describe('momentScore und pickCards', () => {
  const frost = (at: number, actionable = false): Moment =>
    ({ kind: 'first_frost', key: 'first_frost:2026', at, lifetimeMs: 3 * DAY, actionable, dismissible: false, tempC: 3, winterKm: 254, summerKm: 318 })
  const silence: Moment = { kind: 'silence', key: 'silence:l', at: NOW - DAY, lifetimeMs: Infinity, actionable: true, dismissible: true, days: 15, kwh: 20, autoSync: false }

  it('verfällt linear über die Lebensdauer, Handlung gibt einen Bonus', () => {
    const fresh = momentScore(frost(NOW), NOW)
    const half = momentScore(frost(NOW - 1.5 * DAY), NOW)
    expect(half).toBeCloseTo(fresh / 2, 5)
    expect(momentScore(frost(NOW - 1.5 * DAY, true), NOW)).toBeGreaterThan(half)
  })

  it('Karten absteigend nach Punkten, abgelaufene fehlen', () => {
    expect(pickCards([], NOW, NONE)).toEqual([])
    expect(pickCards([frost(NOW - 4 * DAY)], NOW, NONE)).toEqual([])
    expect(pickCards([silence, frost(NOW)], NOW, NONE).map(s => s.moment.kind)).toEqual(['first_frost', 'silence'])
  })

  it('gesehen nimmt nichts weg, erledigt gilt bis zu seinem Ablauf', () => {
    const shown: MomentSeen = { shown: { 'silence:l': NOW - 5 * DAY, 'first_frost:2026': NOW - DAY }, dismissed: {} }
    expect(pickCards([silence, frost(NOW)], NOW, shown)).toHaveLength(2)
    const dismissed: MomentSeen = { shown: {}, dismissed: { 'silence:l': NOW + DAY } }
    expect(pickCards([silence], NOW, dismissed)).toEqual([])
    const expired: MomentSeen = { shown: {}, dismissed: { 'silence:l': NOW - 1 } }
    expect(pickCards([silence], NOW, expired).map(s => s.moment.kind)).toEqual(['silence'])
  })
})
