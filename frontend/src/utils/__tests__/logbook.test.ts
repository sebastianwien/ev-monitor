import { describe, it, expect } from 'vitest'
import { monthsOf, tripDetailKeys, chargeDetailKeys, chargeMonthsOf, type LogbookItem } from '../logbook'
import type { DayLog, DayTrip } from '../../composables/storyDay'

// Ortszeit wie im Tagesblatt: Tagesgrenzen im Browser, nicht in UTC
const at = (d: string, h: number, m = 0) => {
  const [y, mo, day] = d.split('-').map(Number)
  return new Date(y, mo - 1, day, h, m).toISOString()
}
const trip = (id: string, d: string, h: number, km = 10, over: Partial<DayTrip> = {}): DayTrip => ({
  id, tripStartedAt: at(d, h), tripEndedAt: at(d, h, 30), distanceKm: km, socStart: 80, socEnd: 76, ...over,
})
const log = (id: string, d: string, h: number, over: Partial<DayLog> = {}): DayLog => ({
  id, loggedAt: at(d, h), chargeDurationMinutes: 60, socBeforeChargePercent: 30, socAfterChargePercent: 80,
  kwhCharged: 30, kwhAtVehicle: null, costEur: 9, isPublicCharging: false, cpoName: null, chargingType: 'AC', ...over,
})
const kinds = (items: LogbookItem[]) => items.map(i => i.kind === 'day' ? i.key : `idle ${i.from}..${i.to}`)

describe('monthsOf', () => {
  it('Monate und Tage neueste zuerst, im Tag die Ereignisse zeitlich', () => {
    const months = monthsOf([trip('a', '2026-10-02', 8), trip('b', '2026-10-02', 17), trip('c', '2026-09-30', 9)], [])
    expect(months.map(m => m.key)).toEqual(['2026-10', '2026-09'])
    expect(kinds(months[0].items)).toEqual(['2026-10-02', 'idle 2026-10-01..2026-10-01'])
    const day = months[0].items[0]
    expect(day.kind === 'day' && day.events.map(e => e.id)).toEqual(['a', 'b'])
  })

  it('Tage ohne Eintrag werden eine Zeile, am Monatswechsel geteilt', () => {
    const months = monthsOf([trip('a', '2026-10-03', 8), trip('c', '2026-09-27', 9)], [])
    expect(kinds(months[0].items)).toEqual(['2026-10-03', 'idle 2026-10-01..2026-10-02'])
    expect(kinds(months[1].items)).toEqual(['idle 2026-09-28..2026-09-30', '2026-09-27'])
  })

  it('über dem jüngsten Eintrag keine Zeile ohne Eintrag', () => {
    const months = monthsOf([trip('a', '2026-10-01', 8)], [])
    expect(kinds(months[0].items)).toEqual(['2026-10-01'])
  })

  it('ein Tag nur mit Ladung ist ein Blatt, keine Lücke', () => {
    const months = monthsOf([trip('a', '2026-10-03', 8)], [log('l', '2026-10-02', 20)])
    expect(kinds(months[0].items)).toEqual(['2026-10-03', '2026-10-02'])
  })

  it('Standzeit über Nacht steht im Blatt des neueren Tages', () => {
    const drains = new Map([['a', { kwh: 0.3, eur: 0.09 }]])
    const months = monthsOf([trip('a', '2026-10-03', 8), trip('b', '2026-10-02', 18)], [], { drains })
    const newer = months[0].items[0]
    // 18:30 bis 08:00
    expect(newer.kind === 'day' && newer.standing.a).toEqual({ minutes: 810, kwh: 0.3, eur: 0.09 })
  })

  it('ohne Standverlust keine Standzeit', () => {
    const months = monthsOf([trip('a', '2026-10-05', 8), trip('b', '2026-10-05', 12), trip('c', '2026-10-02', 18)], [])
    expect(months[0].items[0].kind === 'day' && months[0].items[0].standing).toEqual({})
    expect(months[0].items[1]).toMatchObject({ kind: 'idle', standing: null })
  })

  it('liegt eine Lücke dazwischen, trägt sie die Standzeit samt Standverlust', () => {
    const drains = new Map([['a', { kwh: 1.5, eur: 0.45 }]])
    const months = monthsOf([trip('a', '2026-10-05', 8), trip('b', '2026-10-02', 18)], [], { drains })
    const [newer, idle] = months[0].items
    expect(newer.kind === 'day' && newer.standing.a).toBeUndefined()
    expect(idle).toMatchObject({ kind: 'idle', from: '2026-10-03', to: '2026-10-04', standing: { minutes: 3690, kwh: 1.5, eur: 0.45 } })
  })

  it('Standzeit im Tag nur mit Standverlust aus dem Feed, auch kurze', () => {
    const drains = new Map([['b', { kwh: 0.06, eur: 0.02 }], ['c', { kwh: 0.24, eur: null }]])
    const months = monthsOf([trip('a', '2026-10-02', 8), trip('b', '2026-10-02', 9), trip('c', '2026-10-02', 12)], [], { drains })
    const day = months[0].items[0]
    // a endet 8:30, b beginnt 9:00 (30 min); b endet 9:30, c beginnt 12:00
    expect(day.kind === 'day' && day.standing).toEqual({ b: { minutes: 30, kwh: 0.06, eur: 0.02 }, c: { minutes: 150, kwh: 0.24, eur: null } })
  })

  it('überlappen sich zwei Einträge, keine Standzeit, auch mit Standverlust', () => {
    // Ladung 8:00 bis 9:00, die Fahrt beginnt schon 8:30
    const drains = new Map([['a', { kwh: 0.2, eur: 0.06 }]])
    const months = monthsOf([trip('a', '2026-10-02', 8, 10, { tripStartedAt: at('2026-10-02', 8, 30), tripEndedAt: at('2026-10-02', 9, 0) })],
      [log('l', '2026-10-02', 8)], { drains })
    expect(months[0].items[0].kind === 'day' && months[0].items[0].standing).toEqual({})
  })

  it('je Tag € pro 100 km über die übergebene Funktion', () => {
    const months = monthsOf([trip('a', '2026-10-02', 8), trip('b', '2026-10-01', 8)], [], { costPer100km: trips => trips.length * 4 })
    expect(months[0].items.map(i => i.kind === 'day' && i.costPer100km)).toEqual([4, 4])
  })

  it('Summen des Monats: Fahrten, km, Ladungen, Kosten; € pro 100 km über die übergebene Funktion', () => {
    const months = monthsOf([trip('a', '2026-10-02', 8, 12.5), trip('b', '2026-10-01', 8, 7.5)], [log('l', '2026-10-01', 20, { costEur: 12.4 })],
      { costPer100km: trips => trips.length * 3 })
    expect(months[0].totals).toEqual({ trips: 2, km: 20, charges: 1, eur: 12.4, costPer100km: 6 })
  })

  // go-e ohne km-Stand am selben Tag: eine Ladegruppe wie in der Liste
  const goe = (id: string, d: string, h: number) => ({ ...log(id, d, h, { chargeDurationMinutes: 30 }), dataSource: 'WALLBOX_GOE' })

  it('Teilladungen eines Tages: im Ablauf ein Eintrag, im Diagramm und in den Summen jede', () => {
    const months = monthsOf([trip('t', '2026-10-05', 18)], [goe('a', '2026-10-05', 9), goe('b', '2026-10-05', 11), goe('c', '2026-10-05', 13)])
    const day = months[0].items[0]
    expect(day.kind === 'day' && day.rows.map(e => e.id)).toEqual(['a', 't'])
    expect(day.kind === 'day' && day.events).toHaveLength(4)
    expect(months[0].totals.charges).toBe(3)
  })

  it('Standzeit vor dem Bündel: der Standverlust hängt an der ältesten Teilladung', () => {
    const drains = new Map([['a', { kwh: 0.4, eur: 0.12 }]])
    const months = monthsOf([trip('t', '2026-10-05', 7)], [goe('a', '2026-10-05', 9), goe('b', '2026-10-05', 11)], { drains })
    const day = months[0].items[0]
    expect(day.kind === 'day' && day.rows.map(e => e.id)).toEqual(['t', 'a'])
    // 07:30 bis 09:00
    expect(day.kind === 'day' && day.standing.a).toEqual({ minutes: 90, kwh: 0.4, eur: 0.12 })
  })

  it('ohne Einträge kein Monat', () => {
    expect(monthsOf([], [])).toEqual([])
  })
})

describe('tripDetailKeys', () => {
  it('Tempo, Streckentyp und Besonderheiten als Textbausteine, in dieser Reihenfolge', () => {
    expect(tripDetailKeys({ avgSpeedKmh: 31.6, maxSpeedKmh: 71.2, routeType: 'CITY', loadExtras: ['TRAILER', 'FULL_LOAD'] })).toEqual([
      { key: 'dashboard.trip_speed_summary', params: { avg: 32, max: 71 } },
      { key: 'dashboard.trip_route_city' },
      { key: 'storyday.form_extra_trailer' },
      { key: 'storyday.form_extra_full_load' },
    ])
  })
  it('Tempo wie in der Story: nur Ø, nur max oder nichts, nie "0 km/h" für einen fehlenden Wert', () => {
    expect(tripDetailKeys({ avgSpeedKmh: 30, maxSpeedKmh: null })).toEqual([{ key: 'dashboard.trip_speed_avg_only', params: { avg: 30 } }])
    expect(tripDetailKeys({ avgSpeedKmh: null, maxSpeedKmh: 71 })).toEqual([{ key: 'dashboard.trip_speed_max_only', params: { max: 71 } }])
    expect(tripDetailKeys({})).toEqual([])
  })
})

describe('chargeDetailKeys', () => {
  it('höchste Ladeleistung und Effizienz, gerundet', () => {
    expect(chargeDetailKeys({ maxChargingPowerKw: 131.6, kwhCharged: 42, kwhAtVehicle: 38.5 })).toEqual([
      { key: 'logbook.max_power', params: { kw: 132 } },
      { key: 'logbook.efficiency', params: { pct: 92 } },
    ])
  })
  it('ohne Werte leer', () => {
    expect(chargeDetailKeys({ maxChargingPowerKw: null, kwhCharged: 42, kwhAtVehicle: null })).toEqual([])
  })
})

describe('chargeMonthsOf (Logbuch nur mit Ladungen)', () => {
  // Ladung mit km-Stand und dem, was das Backend je Ladung liefert (EvLogService). Je Ladung ein neuer km-Stand:
  // gleiche km-Stände hintereinander sind Nachladen und werden ein Bündel
  let odo = 1000
  const charge = (id: string, d: string, h: number, over: Record<string, unknown> = {}) => ({
    ...log(id, d, h), odometerKm: odo += 100, distanceSinceLastChargeKm: null, consumptionKwhPer100km: null, ...over,
  })

  it('neueste zuerst, dazwischen die Strecke der neueren Ladung mit Tagen, km, Verbrauch und € pro 100 km', () => {
    const months = chargeMonthsOf([
      charge('l1', '2026-10-21', 12),
      charge('l2', '2026-10-26', 18, { distanceSinceLastChargeKm: 300, consumptionKwhPer100km: 16.9, costEur: 18 }),
    ])
    expect(months).toHaveLength(1)
    expect(months[0].entries.map(e => e.kind)).toEqual(['charge', 'stretch', 'charge'])
    expect(months[0].entries[1]).toEqual({ kind: 'stretch', key: 's-l2', days: 5, km: 300, kwhPer100km: 16.9, eurPer100km: 6, fix: null })
  })

  it('ohne km-Stand eine Strecke zum Nachtragen, an der Ladung ohne km-Stand', () => {
    const months = chargeMonthsOf([charge('l1', '2026-10-21', 12, { odometerKm: null }), charge('l2', '2026-10-26', 18)])
    expect(months[0].entries[1]).toMatchObject({ kind: 'stretch', km: null, fix: 'l1' })
  })

  it('fehlt der älteren Ladung der km-Stand, misst das Backend weiter zurück: dann keine km, sondern Nachtragen an der älteren', () => {
    // l2 hat 300 km seit der letzten Ladung MIT km-Stand (l0), dazwischen l1 ohne km-Stand
    const months = chargeMonthsOf([
      charge('l0', '2026-10-20', 12),
      charge('l1', '2026-10-22', 12, { odometerKm: null }),
      charge('l2', '2026-10-23', 12, { distanceSinceLastChargeKm: 300, costEur: 5 }),
    ])
    expect(months[0].entries[1]).toMatchObject({ kind: 'stretch', key: 's-l2', km: null, eurPer100km: null, fix: 'l1' })
  })

  it('zwei Ladungen am selben Tag: Abstand 0, der Rhythmus nie unter einem Tag', () => {
    const months = chargeMonthsOf([
      charge('a', '2026-10-01', 8), charge('b', '2026-10-01', 15, { distanceSinceLastChargeKm: 40 }),
      charge('c', '2026-10-01', 20, { distanceSinceLastChargeKm: 30 }),
    ])
    expect(months[0].entries.filter(e => e.kind === 'stretch').map(e => e.kind === 'stretch' && e.days)).toEqual([0, 0])
    expect(months[0].rhythmDays).toBe(1)
  })

  it('Nachladen am selben km-Stand: ein Bündel ohne Strecke dazwischen; unter der ältesten geladenen Ladung keine', () => {
    const months = chargeMonthsOf([
      charge('l1', '2026-10-21', 12, { odometerKm: 900 }),
      charge('l2', '2026-10-21', 14, { odometerKm: 900, distanceSinceLastChargeKm: 0 }),
    ])
    expect(months[0].entries.map(e => e.kind)).toEqual(['charge'])
  })

  it('Teilladungen: eine Zeile mit den Teilen; die Strecke darunter kommt von der ältesten, € pro 100 km aus der Summe', () => {
    const months = chargeMonthsOf([
      charge('l0', '2026-10-20', 12, { odometerKm: 900 }),
      charge('a', '2026-10-25', 9, { odometerKm: 1200, distanceSinceLastChargeKm: 300, consumptionKwhPer100km: 17, costEur: 3 }),
      charge('b', '2026-10-25', 11, { odometerKm: 1200, distanceSinceLastChargeKm: 0, costEur: 3 }),
      charge('c', '2026-10-25', 13, { odometerKm: 1200, distanceSinceLastChargeKm: 0, costEur: 3 }),
    ])
    expect(months[0].entries.map(e => e.kind)).toEqual(['charge', 'stretch', 'charge'])
    const bundle = months[0].entries[0]
    expect(bundle.key).toBe('a')
    expect(bundle.kind === 'charge' && bundle.parts?.map(p => p.id)).toEqual(['a', 'b', 'c'])
    expect(months[0].entries[1]).toEqual({ kind: 'stretch', key: 's-a', days: 5, km: 300, kwhPer100km: 17, eurPer100km: 3, fix: null })
    // Summen und Zahl der Ladungen zählen jedes Teil
    expect(months[0].totals).toMatchObject({ charges: 4, eur: 18 })
  })

  it('Teilladungen, denen ein Preis fehlt: keine € pro 100 km', () => {
    const months = chargeMonthsOf([
      charge('l0', '2026-10-20', 12, { odometerKm: 900 }),
      charge('a', '2026-10-25', 9, { odometerKm: 1200, distanceSinceLastChargeKm: 300, costEur: 3 }),
      charge('b', '2026-10-25', 11, { odometerKm: 1200, distanceSinceLastChargeKm: 0, costEur: null }),
    ])
    expect(months[0].entries[1]).toMatchObject({ km: 300, eurPer100km: null })
  })

  it('Monate nach der Ladung, die Strecke bei der neueren; Summen je Monat', () => {
    const months = chargeMonthsOf([
      charge('l1', '2026-09-28', 12, { costEur: 10, kwhCharged: 30 }),
      charge('l2', '2026-10-03', 18, { distanceSinceLastChargeKm: 200, costEur: 12, kwhCharged: 34 }),
    ])
    expect(months.map(m => m.key)).toEqual(['2026-10', '2026-09'])
    expect(months[0].entries.map(e => e.kind)).toEqual(['charge', 'stretch'])
    expect(months[0].totals).toEqual({ charges: 1, kwh: 34, eur: 12 })
  })

  it('Laderhythmus: Median der Abstände in ganzen Tagen, erst ab zwei Abständen', () => {
    const three = chargeMonthsOf([charge('a', '2026-10-01', 12), charge('b', '2026-10-05', 12), charge('c', '2026-10-15', 12)])
    expect(three[0].rhythmDays).toBe(7)
    const two = chargeMonthsOf([charge('a', '2026-10-01', 12), charge('b', '2026-10-05', 12)])
    expect(two[0].rhythmDays).toBeNull()
  })
})
