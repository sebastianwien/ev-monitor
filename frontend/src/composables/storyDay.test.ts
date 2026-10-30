import { describe, it, expect } from 'vitest'
import { barOfEvent,
  dayKeyOf, dayRange, shiftDay, eventsOfDay, daySummary, chargeKwh, missingOf, chargePlace,
  gapMinutes, canMerge, showStanding, ribbonSegments, dayAtNow, batteryBars, bundleEvents, bundleTotals,
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
    expect(r.caption).toEqual({ first: 80, last: 80, min: 30, max: 80 })
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

describe('dayAtNow', () => {
  it('Datum des Tages, Uhrzeit von jetzt', () => {
    const d = dayAtNow('2026-08-16', new Date(2026, 9, 25, 19, 42, 31))
    expect(d.getTime()).toBe(new Date(2026, 7, 16, 19, 42).getTime())
  })
})

describe('batteryBars', () => {
  // 08:00-09:00 laden 20 -> 80, 12:00-12:30 fahren 80 -> 70
  const ev = eventsOfDay('2026-08-16', [trip({ tripStartedAt: at(16, 12), tripEndedAt: at(16, 12, 30), socStart: 80, socEnd: 70 })],
    [log({ loggedAt: at(16, 8), chargeDurationMinutes: 60, socBeforeChargePercent: 20, socAfterChargePercent: 80 })])
  const bars = batteryBars(ev)
  it('48 halbe Stunden', () => expect(bars).toHaveLength(48))
  it('vor dem ersten und nach dem letzten Messwert: kein Balken', () => {
    expect(bars[15].soc).toBeNull()      // 07:30-08:00
    expect(bars[25].soc).toBeNull()      // 12:30-13:00, endet nach dem letzten Messwert
  })
  it('Höhe = Akku am Ende des Abschnitts, linear zwischen Messwerten', () => {
    expect(bars[16].soc).toBeCloseTo(50) // 08:30: halb geladen
    expect(bars[17].soc).toBe(80)        // 09:00
    expect(bars[20].soc).toBe(80)        // 10:30: steht
    expect(bars[24].soc).toBe(70)        // 12:30: Fahrt zu Ende
  })
  it('Farbe: laden vor fahren vor stehen', () => {
    expect(bars[16].kind).toBe('charge')
    expect(bars[20].kind).toBe('idle')
    expect(bars[24].kind).toBe('trip')
  })
})

describe('barOfEvent', () => {
  // Balken je halbe Stunde: ein Eintrag 8:10 bis 9:00 berührt die Balken ab 8:00 und 8:30
  const e = { from: 490, to: 540 }
  it('Balken, die der Eintrag berührt', () => {
    expect([450, 480, 510, 540].map(from => barOfEvent({ from }, e))).toEqual([false, true, true, false])
  })
  it('kurzer Eintrag innerhalb eines Balkens', () => {
    expect(barOfEvent({ from: 480 }, { from: 495, to: 496 })).toBe(true)
  })
})

describe('Bündel (Teilladungen eines Tages)', () => {
  // Überschussladen zu Hause: je 40 min, der Akku steigt von Teil zu Teil
  const part = (id: string, h: number, over: Partial<DayLog> = {}) => log({
    id, loggedAt: at(16, h), chargeDurationMinutes: 40, isPublicCharging: false, cpoName: null, chargingType: 'AC',
    kwhCharged: 4, costEur: 1.2, socBeforeChargePercent: 50 + (h - 9) * 5, socAfterChargePercent: 54 + (h - 9) * 5, ...over,
  })
  const keys = (m: Record<string, string>) => new Map(Object.entries(m))

  it('Teile einer Ladegruppe werden ein Eintrag: Beginn der ersten, Ende der letzten, Akku von der ersten bis zur letzten', () => {
    const rows = bundleEvents(eventsOfDay('2026-08-16', [], [part('b', 11), part('a', 9), part('c', 13)]), keys({ a: 'g', b: 'g', c: 'g' }))
    expect(rows).toHaveLength(1)
    expect(rows[0]).toMatchObject({ kind: 'charge', id: 'a', from: 540, to: 820, socFrom: 50, socTo: 74 })
    expect(rows[0].kind === 'charge' && rows[0].parts?.map(p => p.id)).toEqual(['a', 'b', 'c'])
  })

  it('erst ab zwei Teilen: eine Ladung, deren Gruppe an einem anderen Tag weitergeht, bleibt, wie sie ist', () => {
    const rows = bundleEvents(eventsOfDay('2026-08-16', [], [part('a', 9), part('b', 11)]), keys({ a: 'g', x: 'g' }))
    expect(rows.map(r => r.id)).toEqual(['a', 'b'])
    expect(rows.every(r => r.kind === 'charge' && r.parts === undefined)).toBe(true)
  })

  it('eine Fahrt dazwischen trennt das Bündel, der Ablauf bleibt in seiner Reihenfolge', () => {
    const t = trip({ id: 't', tripStartedAt: at(16, 11), tripEndedAt: at(16, 12) })
    const ev = eventsOfDay('2026-08-16', [t], [part('a', 9), part('b', 10), part('c', 13), part('d', 14)])
    const rows = bundleEvents(ev, keys({ a: 'g', b: 'g', c: 'g', d: 'g' }))
    expect(rows.map(r => r.id)).toEqual(['a', 't', 'c'])
    expect(rows[2].kind === 'charge' && rows[2].parts?.map(p => p.id)).toEqual(['c', 'd'])
  })

  it('Summen der Teile: Betrag, Energie, Ladeart; Preis je kWh nur, wenn alle einen Preis haben', () => {
    const full = bundleTotals([part('a', 9), part('b', 11, { costEur: 2, kwhCharged: 5 })])
    expect(full).toMatchObject({ kwh: 9, chargingType: 'AC', priceless: [] })
    expect(full.costEur).toBeCloseTo(3.2)
    expect(full.pricePerKwh).toBeCloseTo(3.2 / 9)

    const open = bundleTotals([part('a', 9, { chargingType: 'DC' }), part('b', 11, { costEur: null }), part('c', 13, { costEur: null, priceUnknown: true })])
    expect(open.costEur).toBeCloseTo(1.2)
    expect(open.pricePerKwh).toBeNull()
    expect(open.chargingType).toBeNull()
    // "Preis unbekannt" fehlt nicht, wie bei einer einzelnen Ladung
    expect(open.priceless.map(p => p.id)).toEqual(['b'])
  })
})
