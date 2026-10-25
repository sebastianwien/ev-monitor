import { describe, it, expect } from 'vitest'
import {
  dayKeyOf, dayRange, shiftDay, eventsOfDay, daySummary, chargeKwh, missingOf, chargePlace,
  gapMinutes, canMerge, showStanding, ribbonSegments, routeThumb, dayAtNow,
  type DayLog, type DayTrip, type DayEvent,
} from './storyDay'

// Ortszeit wie die Tagesleiste: Zeiten in Ortszeit bauen, damit der Test in jeder Zeitzone gilt
const at = (d: number, h: number, m = 0) => new Date(2026, 7, d, h, m).toISOString()

function trip(over: Partial<DayTrip> = {}): DayTrip {
  return {
    id: 't1', tripStartedAt: at(16, 9), tripEndedAt: at(16, 10), distanceKm: 42,
    socStart: 80, socEnd: 70, routePolyline: null, tracePolyline: null, ...over,
  }
}
function log(over: Partial<DayLog> = {}): DayLog {
  return {
    id: 'l1', loggedAt: at(16, 12), chargeDurationMinutes: 30, socBeforeChargePercent: 30,
    socAfterChargePercent: 80, kwhCharged: 33, kwhAtVehicle: null, costEur: 16.5, priceUnknown: false,
    isPublicCharging: true, cpoName: 'Tesla', chargingType: 'DC', ...over,
  }
}

describe('Tagesgrenzen', () => {
  it('dayKeyOf nimmt die Ortszeit', () => {
    expect(dayKeyOf(at(16, 0, 5))).toBe('2026-08-16')
    expect(dayKeyOf(at(16, 23, 55))).toBe('2026-08-16')
  })
  it('dayRange deckt den Ortstag ab', () => {
    const r = dayRange('2026-08-16')
    expect(new Date(r.from).getTime()).toBe(new Date(2026, 7, 16).getTime())
    expect(new Date(r.to).getTime()).toBe(new Date(2026, 7, 17).getTime() - 1)
  })
  it('shiftDay geht über Monatsgrenzen', () => {
    expect(shiftDay('2026-08-31', 1)).toBe('2026-09-01')
    expect(shiftDay('2026-09-01', -1)).toBe('2026-08-31')
  })
})

describe('eventsOfDay', () => {
  it('sortiert Fahrten und Ladungen nach Beginn, in Minuten des Tages', () => {
    const ev = eventsOfDay('2026-08-16', [trip({ id: 'a', tripStartedAt: at(16, 14), tripEndedAt: at(16, 15) }), trip()], [log()])
    expect(ev.map(e => e.id)).toEqual(['t1', 'l1', 'a'])
    expect(ev[0]).toMatchObject({ kind: 'trip', from: 540, to: 600, socFrom: 80, socTo: 70 })
    expect(ev[1]).toMatchObject({ kind: 'charge', from: 720, to: 750, socFrom: 30, socTo: 80 })
  })
  it('Fahrt über Mitternacht gehört zum Starttag und endet bei 24 Uhr', () => {
    const t = trip({ tripStartedAt: at(16, 23, 30), tripEndedAt: at(17, 0, 40) })
    expect(eventsOfDay('2026-08-16', [t], [])[0]).toMatchObject({ from: 1410, to: 1440 })
    expect(eventsOfDay('2026-08-17', [t], [])).toEqual([])
  })
  it('Ladung ohne Dauer bekommt eine Minute, lange Ladung endet bei 24 Uhr', () => {
    const ev = eventsOfDay('2026-08-16', [], [
      log({ id: 'x', chargeDurationMinutes: null }),
      log({ id: 'y', loggedAt: at(16, 23), chargeDurationMinutes: 300 }),
    ])
    expect(ev[0]).toMatchObject({ from: 720, to: 721 })
    expect(ev[1]).toMatchObject({ from: 1380, to: 1440 })
  })
})

describe('daySummary', () => {
  it('zählt Fahrten, km, Ladungen, kWh und Euro', () => {
    const ev = eventsOfDay('2026-08-16', [trip(), trip({ id: 't2', distanceKm: null })], [
      log(), log({ id: 'l2', kwhCharged: null, kwhAtVehicle: 10, costEur: null }),
    ])
    expect(daySummary(ev)).toEqual({ trips: 2, km: 42, charges: 2, kwh: 43, eur: 16.5 })
  })
})

describe('Ladung', () => {
  it('chargeKwh nimmt Säule vor Fahrzeug', () => {
    expect(chargeKwh(log())).toBe(33)
    expect(chargeKwh(log({ kwhCharged: null, kwhAtVehicle: 12 }))).toBe(12)
  })
  it('missingOf: Kosten 0 ist ein Wert, "Weiß ich nicht mehr" fragt nicht mehr', () => {
    expect(missingOf(log({ costEur: 0 }))).toEqual([])
    expect(missingOf(log({ costEur: null }))).toEqual(['price'])
    expect(missingOf(log({ costEur: null, priceUnknown: true }))).toEqual([])
    expect(missingOf(log({ costEur: null, kwhCharged: null }))).toEqual(['price', 'kwh'])
  })
  it('chargePlace: Betreiber bereinigt, sonst zu Hause, öffentlich oder unbekannt', () => {
    expect(chargePlace(log({ cpoName: 'Kaufland - Filiale 12' }))).toEqual({ kind: 'operator', name: 'Kaufland' })
    expect(chargePlace(log({ cpoName: 'Ionity, Germany 123' }))).toEqual({ kind: 'operator', name: 'Ionity' })
    expect(chargePlace(log({ cpoName: null, isPublicCharging: false }))).toEqual({ kind: 'home' })
    expect(chargePlace(log({ cpoName: '  ', isPublicCharging: true }))).toEqual({ kind: 'public' })
    expect(chargePlace(log({ cpoName: null, isPublicCharging: null }))).toEqual({ kind: 'unknown' })
  })
})

describe('Abstände und Zusammenführen', () => {
  const ev = (over: Partial<DayEvent>): DayEvent => ({ kind: 'trip', id: 'x', from: 0, to: 10, socFrom: null, socTo: null, ...over } as DayEvent)
  it('zwei Fahrten mit Halt bis 15 min lassen sich zusammenführen', () => {
    expect(canMerge(ev({ to: 100 }), ev({ from: 115 }))).toBe(true)
    expect(canMerge(ev({ to: 100 }), ev({ from: 116 }))).toBe(false)
    expect(canMerge(ev({ to: 100 }), ev({ from: 95 }))).toBe(true)
  })
  it('nicht über eine Ladung hinweg', () => {
    expect(canMerge(ev({ to: 100 }), ev({ kind: 'charge', from: 105 }))).toBe(false)
  })
  it('Standzeit ab 45 min', () => {
    expect(gapMinutes(ev({ to: 100 }), ev({ from: 145 }))).toBe(45)
    expect(showStanding(ev({ to: 100 }), ev({ from: 145 }))).toBe(true)
    expect(showStanding(ev({ to: 100 }), ev({ from: 144 }))).toBe(false)
  })
})

describe('ribbonSegments', () => {
  it('Linien je Eintrag, gestrichelt dazwischen, keine Linie davor und danach', () => {
    const ev = eventsOfDay('2026-08-16', [trip()], [log()])
    const r = ribbonSegments(ev)
    expect(r.lines).toEqual([
      { kind: 'trip', x1: 540, soc1: 80, x2: 600, soc2: 70 },
      { kind: 'idle', x1: 600, soc1: 70, x2: 720, soc2: 30 },
      { kind: 'charge', x1: 720, soc1: 30, x2: 750, soc2: 80 },
    ])
    expect(r.bands).toEqual([
      { kind: 'trip', id: 't1', x: 540, w: 60 },
      { kind: 'charge', id: 'l1', x: 720, w: 30 },
    ])
    expect(r.caption).toEqual({ first: 80, last: 80, min: 30 })
  })
  it('nur Endstand: Punkt statt Linie', () => {
    const r = ribbonSegments(eventsOfDay('2026-08-16', [trip({ socStart: null })], []))
    expect(r.lines).toEqual([])
    expect(r.dots).toEqual([{ kind: 'trip', x: 600, soc: 70 }])
  })
  it('ohne SoC keine Bildunterschrift', () => {
    const r = ribbonSegments(eventsOfDay('2026-08-16', [trip({ socStart: null, socEnd: null })], []))
    expect(r.caption).toBeNull()
  })
})

describe('routeThumb', () => {
  it('passt die Strecke ins Feld, Norden oben, Seitenverhältnis bleibt', () => {
    // [lat, lng]: 1 Grad nach Osten, dann 1 Grad nach Norden
    const r = routeThumb([[0, 0], [0, 1], [1, 1]], 60, 44, 4)!
    expect(r.d).toBe('M12,40L48,40L48,4')
    expect(r.end).toEqual([48, 4])
  })
  it('weniger als zwei Punkte: nichts', () => {
    expect(routeThumb([[0, 0]], 60, 44)).toBeNull()
  })
})

describe('dayAtNow', () => {
  it('Datum des Tages, Uhrzeit von jetzt', () => {
    const d = dayAtNow('2026-08-16', new Date(2026, 9, 25, 19, 42, 31))
    expect(d.getTime()).toBe(new Date(2026, 7, 16, 19, 42).getTime())
  })
})
