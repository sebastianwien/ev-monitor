import { describe, it, expect } from 'vitest'
import { monthsOf, tripDetailKeys, chargeDetailKeys, type LogbookItem } from '../logbook'
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
