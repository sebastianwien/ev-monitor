import { describe, it, expect } from 'vitest'
import {
  detectJustCharged, detectLongTrip, detectFirstFrost, detectMonthChange, detectBattery,
  detectRank, detectSilence, detectLive, monthMoment, momentScore, pickMoment,
  momentFeedItems, HOUR, DAY, type MomentSeen, type Moment,
} from './storyMoments'

// Samstag, 24.10.2026, 12:00 Ortszeit
const NOW = new Date(2026, 9, 24, 12, 0).getTime()
const iso = (ms: number) => new Date(ms).toISOString()
const NONE: MomentSeen = { shown: {}, dismissed: {} }

describe('detectJustCharged', () => {
  const log = { id: 'l1', loggedAt: iso(NOW - HOUR), kwhAtVehicle: 28.5, kwhCharged: 30, costEur: 9.5, socBeforeChargePercent: 35, socAfterChargePercent: 80, dataSource: 'SMARTCAR_LIVE' }

  it('erkennt eine Ladung unter 2 Stunden mit kWh, SoC und Reichweite', () => {
    const m = detectJustCharged({ log, now: NOW, consumptionKwh100: 13.6 })
    expect(m).toMatchObject({ kind: 'just_charged', key: 'just_charged:l1', kwh: 28.5, socFrom: 35, socTo: 80, rangeKm: 210, costEur: 9.5, actionable: false })
  })

  it('ist ohne Preis eine Handlung', () => {
    expect(detectJustCharged({ log: { ...log, costEur: null }, now: NOW, consumptionKwh100: 13.6 })?.actionable).toBe(true)
  })

  it('ignoriert Ladungen älter als 2 Stunden', () => {
    expect(detectJustCharged({ log: { ...log, loggedAt: iso(NOW - 3 * HOUR) }, now: NOW, consumptionKwh100: null })).toBeNull()
  })

  it('zählt bei manueller Erfassung den Zeitpunkt des Eintragens, wenn die Ladung von heute ist', () => {
    const manual = { ...log, loggedAt: iso(NOW - 6 * HOUR), createdAt: iso(NOW - 10 * 60_000), dataSource: 'USER_LOGGED' }
    expect(detectJustCharged({ log: manual, now: NOW, consumptionKwh100: null })?.at).toBe(NOW - 10 * 60_000)
  })

  it('nachgetragene alte Ladung ist kein "gerade geladen"', () => {
    const old = { ...log, loggedAt: iso(NOW - 5 * DAY), createdAt: iso(NOW - 60_000), dataSource: 'USER_LOGGED' }
    expect(detectJustCharged({ log: old, now: NOW, consumptionKwh100: null })).toBeNull()
  })

  it('lässt SoC und Reichweite weg, wenn sie fehlen oder unplausibel sind', () => {
    const m = detectJustCharged({ log: { ...log, socBeforeChargePercent: 80, socAfterChargePercent: 35 }, now: NOW, consumptionKwh100: null })
    expect(m).toMatchObject({ socFrom: null, socTo: null, rangeKm: null })
  })
})

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

  it('lebt vom 1. bis 5. mit Vormonat und Benzinvergleich', () => {
    const now = new Date(2026, 9, 3, 9).getTime()
    const m = detectMonthChange({ now, previous, petrolEurPerLitre: 2 })
    expect(m).toMatchObject({ kind: 'month_change', key: 'month_change:2026-9', km: 279, eur: 33, petrolEur: 279 / 100 * 7.5 * 2 })
    expect(m?.at).toBe(new Date(2026, 9, 1).getTime())
  })

  it('vergleicht nicht mit Benzin, wenn dem Vormonat Preise fehlen', () => {
    const now = new Date(2026, 9, 3, 9).getTime()
    expect(detectMonthChange({ now, previous: { ...previous, priceless: 2 }, petrolEurPerLitre: 2 })).toMatchObject({ petrolEur: null })
  })

  it('schweigt ab dem 6. und ohne Ladungen im Vormonat', () => {
    expect(detectMonthChange({ now: NOW, previous, petrolEurPerLitre: 2 })).toBeNull()
    expect(detectMonthChange({ now: new Date(2026, 9, 2).getTime(), previous: { ...previous, charges: 0 }, petrolEurPerLitre: 2 })).toBeNull()
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

describe('detectRank und detectSilence', () => {
  it('Rang nur auf den Plätzen 1 bis 3', () => {
    expect(detectRank({ rank: { category: 'MONTHLY_KWH', rank: 2, value: 1, unit: 'kWh' }, now: NOW })).toMatchObject({ kind: 'rank', key: 'rank:MONTHLY_KWH:2:2026-10', rank: 2 })
    expect(detectRank({ rank: { category: 'MONTHLY_KWH', rank: 4, value: 1, unit: 'kWh' }, now: NOW })).toBeNull()
  })

  it('Stille nach 14 Tagen ohne Ladung, nur bei manuell erfassten Ladungen', () => {
    const log = { id: 'l', loggedAt: iso(NOW - 16 * DAY), kwhAtVehicle: 28.5, dataSource: 'USER_LOGGED' }
    expect(detectSilence({ log, now: NOW, autoSync: true })).toMatchObject({ kind: 'silence', key: 'silence:l', days: 16, kwh: 28.5, autoSync: true, dismissible: true, actionable: true })
    expect(detectSilence({ log: { ...log, dataSource: 'SMARTCAR_LIVE' }, now: NOW, autoSync: false })).toBeNull()
    expect(detectSilence({ log: { ...log, loggedAt: iso(NOW - 10 * DAY) }, now: NOW, autoSync: false })).toBeNull()
  })
})

describe('momentScore und pickMoment', () => {
  const charged = (at: number, actionable = false): Moment => ({
    kind: 'just_charged', key: 'just_charged:l1', at, lifetimeMs: 2 * HOUR, actionable, dismissible: false,
    logId: 'l1', kwh: 20, socFrom: null, socTo: null, rangeKm: null, costEur: 5,
  })
  const silence: Moment = { kind: 'silence', key: 'silence:l', at: NOW - DAY, lifetimeMs: Infinity, actionable: true, dismissible: true, days: 15, kwh: 20, autoSync: false }

  it('verfällt linear über die Lebensdauer, Handlung gibt einen Bonus', () => {
    const fresh = momentScore(charged(NOW), NOW, NONE)
    const half = momentScore(charged(NOW - HOUR), NOW, NONE)
    expect(half).toBeCloseTo(fresh / 2, 5)
    expect(momentScore(charged(NOW - HOUR, true), NOW, NONE)).toBeGreaterThan(half)
  })

  it('Monat ist immer da und gewinnt, wenn nichts anderes vorliegt', () => {
    expect(pickMoment([], NOW, NONE).hero.kind).toBe('month')
    expect(pickMoment([charged(NOW - 3 * HOUR)], NOW, NONE).hero.kind).toBe('month')
  })

  it('Live schlägt alles, Rest ohne Monat und Live, absteigend', () => {
    const r = pickMoment([monthMoment(), charged(NOW), silence, detectLive(true)!], NOW, NONE)
    expect(r.hero.kind).toBe('live')
    expect(r.rest.map(s => s.moment.kind)).toEqual(['just_charged', 'silence'])
  })

  it('ein schon gesehener Moment fällt nach der Schonfrist unter den Monat', () => {
    const seen: MomentSeen = { shown: { 'just_charged:l1': NOW - HOUR }, dismissed: {} }
    expect(pickMoment([charged(NOW - 5 * 60_000)], NOW, seen).hero.kind).toBe('month')
    const justNow: MomentSeen = { shown: { 'just_charged:l1': NOW - 60_000 }, dismissed: {} }
    expect(pickMoment([charged(NOW - 5 * 60_000)], NOW, justNow).hero.kind).toBe('just_charged')
  })

  it('wegklickbare Momente kennen keinen Gesehen-Abzug, aber das Wegklicken bis zu seinem Ablauf', () => {
    const shown: MomentSeen = { shown: { 'silence:l': NOW - 5 * DAY }, dismissed: {} }
    expect(pickMoment([silence], NOW, shown).hero.kind).toBe('silence')
    const dismissed: MomentSeen = { shown: {}, dismissed: { 'silence:l': NOW + DAY } }
    expect(pickMoment([silence], NOW, dismissed).hero.kind).toBe('month')
    const expired: MomentSeen = { shown: {}, dismissed: { 'silence:l': NOW - 1 } }
    expect(pickMoment([silence], NOW, expired).hero.kind).toBe('silence')
  })
})

describe('momentFeedItems', () => {
  it('macht aus dem Rest Feed-Einträge, ohne Rang (den nennt der Feed selbst), Relevanz höchstens 100', () => {
    const rank = detectRank({ rank: { category: 'MONTHLY_KWH', rank: 1, value: 1, unit: 'kWh' }, now: NOW })!
    const frost = { kind: 'first_frost', key: 'first_frost:2026', at: NOW, lifetimeMs: DAY, actionable: false, dismissible: false, tempC: 3, winterKm: 254, summerKm: 318 } as Moment
    const items = momentFeedItems([{ moment: frost, score: 120 }, { moment: rank, score: 55 }])
    expect(items).toEqual([expect.objectContaining({ id: 'moment', question: 2, relevance: 100, moment: frost })])
  })
})
