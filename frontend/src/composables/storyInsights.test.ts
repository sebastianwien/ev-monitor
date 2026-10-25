import { describe, it, expect } from 'vitest'
import {
  temperatureEffect, whatIfModels, dataQuality, countPricelessInMonth, bestRank, buildLatest, sohIsEstimated,
  COLD_MAX_C, MILD_MIN_C, MIN_TRIPS_PER_SIDE,
} from './storyInsights'
import type { PeerModelComparisonItem } from '../api/peerModelComparisonService'

function trip(temp: number | null, kwh: number, km = 50) {
  return { id: `${temp}-${kwh}`, tripStartedAt: null, tripEndedAt: null, distanceKm: km, socStart: null, socEnd: null, estimatedConsumedKwh: kwh, outsideTempCelsius: temp }
}

describe('temperatureEffect', () => {
  it('liefert null unter drei auswertbaren Fahrten', () => {
    expect(temperatureEffect([trip(2, 10), trip(20, 7)], null)).toBeNull()
    expect(temperatureEffect([trip(null, 10), trip(null, 7), trip(null, 8)], null)).toBeNull()
  })

  it('bildet Punkte, 5-Grad-Bins mit streckengewichtetem Mittel und das Kalt-gegen-Mild-Delta', () => {
    const cold = [trip(-3, 12), trip(1, 11), trip(4, 11.5)]
    const mild = [trip(16, 8), trip(18, 8.2), trip(22, 7.8)]
    const e = temperatureEffect([...cold, ...mild], null)!
    expect(e.points).toHaveLength(6)
    expect(e.points[0]).toEqual({ temp: -3, kwh100: 24, km: 50 })
    expect(e.bins.map(b => b.tempFrom)).toEqual([-5, 0, 15, 20])
    expect(e.bins.find(b => b.tempFrom === 0)!.kwh100).toBeCloseTo(22.5)
    // gleiche Strecken: kalt (24+22+23)/3 = 23, mild 16 -> +43,75 %
    expect(e.coldKwh100).toBeCloseTo(23)
    expect(e.mildKwh100).toBeCloseTo(16)
    expect(e.deltaPercent).toBeCloseTo(43.75)
  })

  it('hat kein Delta, wenn eine Seite zu wenige Fahrten hat', () => {
    const e = temperatureEffect([trip(1, 12), trip(2, 11), trip(18, 8), trip(19, 8), trip(20, 8)], null)!
    expect(e.deltaPercent).toBeNull()
    expect(e.coldKwh100).toBeNull()
    expect(MIN_TRIPS_PER_SIDE).toBe(3)
    expect(COLD_MAX_C).toBeLessThan(MILD_MIN_C)
  })

  it('behaelt Kurzstrecken, gewichtet aber nach Strecke wie das Backend (Summe kWh durch Summe km)', () => {
    // 0,4 km mit 0,2 kWh sind 50 kWh/100 km als Punkt, im Bin zaehlen sie nur mit 0,4 km
    const short = trip(14, 0.2, 0.4)
    const e = temperatureEffect([short, trip(12, 9), trip(13, 9), trip(16, 8)], null)!
    expect(e.points.map(p => p.temp)).toEqual([12, 13, 14, 16])
    expect(e.points.find(p => p.temp === 14)!.kwh100).toBeCloseTo(50)
    // Bin 10 bis 15: (9 + 9 + 0,2) kWh auf 100,4 km, ein einfaches Mittel laege bei 22,7
    expect(e.bins.find(b => b.tempFrom === 10)!.kwh100).toBeCloseTo(18.13, 1)
    expect(e.bins.find(b => b.tempFrom === 10)!.count).toBe(3)
  })

  it('gewichtet auch das Kalt-gegen-Mild-Delta nach Strecke', () => {
    // kalt: 200 km mit 40 kWh (20) und zwei 1-km-Fahrten mit 1 kWh (100) -> 42/202 = 20,8
    const cold = [trip(1, 40, 200), trip(2, 1, 1), trip(3, 1, 1)]
    const mild = [trip(18, 16, 100), trip(19, 16, 100), trip(20, 16, 100)]
    const e = temperatureEffect([...cold, ...mild], null)!
    expect(e.coldKwh100).toBeCloseTo(20.79, 1)
    expect(e.mildKwh100).toBeCloseTo(16)
    expect(e.deltaPercent).toBeCloseTo(29.95, 0)
  })

  it('nutzt SoC-Delta mit Kapazitaet, wenn keine kWh gemeldet sind', () => {
    const t = { ...trip(10, 0), estimatedConsumedKwh: null, socStart: 80, socEnd: 60 }
    const e = temperatureEffect([t, trip(12, 9), trip(14, 9)], 50)!
    // 20 % von 50 kWh = 10 kWh auf 50 km = 20 kWh/100 km
    expect(e.points.find(p => p.temp === 10)!.kwh100).toBeCloseTo(20)
  })
})

describe('whatIfModels', () => {
  const item = (name: string, cons: number | null, cost: number | null, isUser = false): PeerModelComparisonItem => ({
    vehicleSpecificationId: name,
    displayName: name,
    isUserVehicleSpec: isUser,
    netBatteryCapacityKwh: 60,
    userMetrics: null,
    peerMetrics: { avgConsumptionKwhPer100km: cons, avgCostPerKwh: cost, uniqueCars: 40 },
  })

  it('sortiert nach Euro je 100 km und rechnet das Delta zum Nutzer', () => {
    const list = whatIfModels([item('Teuer', 20, 0.40), item('Meins', 18, 0.25, true), item('Billig', 15, 0.20)], 4.5)
    expect(list.map(m => m.name)).toEqual(['Billig', 'Meins', 'Teuer'])
    expect(list[0].costPer100km).toBeCloseTo(3)
    expect(list[0].deltaPercent).toBeCloseTo(-33.33, 1)
    expect(list[1].isUser).toBe(true)
    expect(list[0].cars).toBe(40)
  })

  it('laesst Modelle ohne vollstaendige Werte weg und gibt ohne Nutzerwert nichts zurueck', () => {
    expect(whatIfModels([item('A', null, 0.3), item('B', 16, null)], 4)).toEqual([])
    expect(whatIfModels([item('A', 16, 0.3)], null)).toEqual([])
  })

  it('Modellname ohne Klammerwert der Akkugröße', () => {
    const list = whatIfModels([item('Model 3 Premium RWD Highland (82.00kWh)', 15, 0.2)], 4.5)
    expect(list[0].name).toBe('Model 3 Premium RWD Highland')
  })
})

describe('dataQuality', () => {
  const base = { totalCharges: 20, estimatedConsumptionCount: 0 }

  it('ist ok ohne Schaetzungen und ohne preislose Ladungen', () => {
    const q = dataQuality(base, 0)
    expect(q.severity).toBe('ok')
    expect(q.estimatedShare).toBe(0)
  })

  it('warnt ab 25 Prozent Schaetzung oder einer preislosen Ladung', () => {
    expect(dataQuality({ ...base, estimatedConsumptionCount: 5 }, 0).severity).toBe('warn')
    expect(dataQuality(base, 1).severity).toBe('warn')
  })

  it('ist schlecht ab 50 Prozent Schaetzung oder einem Viertel ohne Preis', () => {
    expect(dataQuality({ ...base, estimatedConsumptionCount: 10 }, 0).severity).toBe('bad')
    expect(dataQuality(base, 5).severity).toBe('bad')
  })

  it('teilt nicht durch null', () => {
    expect(dataQuality({ totalCharges: 0, estimatedConsumptionCount: 0 }, 0).estimatedShare).toBe(0)
  })

  it('trennt offene Lücken von Preisen, die der Nutzer nicht mehr weiß', () => {
    expect(dataQuality(base, 3, 1)).toMatchObject({ priceless: 3, pricelessOpen: 1, severity: 'warn' })
    expect(dataQuality(base, 2).pricelessOpen).toBe(2)
  })
})

describe('countPricelessInMonth', () => {
  it('zaehlt nur Logs im Monat', () => {
    const logs = [{ loggedAt: '2026-09-30T23:30:00Z' }, { loggedAt: '2026-10-01T06:00:00Z' }, { loggedAt: '2026-10-31T22:00:00Z' }, { loggedAt: '2026-11-01T00:00:00Z' }]
    expect(countPricelessInMonth(logs, 2026, 10)).toBe(2)
  })
})

describe('bestRank', () => {
  it('nimmt den besten eigenen Rang ueber alle Kategorien', () => {
    const r = bestRank([
      { category: 'MONTHLY_KWH', ownEntry: { rank: 7, value: 300, unit: 'kWh' } },
      null,
      { category: 'MONTHLY_NIGHT_OWL', ownEntry: { rank: 2, value: 9, unit: '' } },
      { category: 'MONTHLY_DISTANCE', ownEntry: null },
    ])
    expect(r).toEqual({ category: 'MONTHLY_NIGHT_OWL', rank: 2, value: 9, unit: '' })
  })

  it('liefert null ohne eigenen Eintrag', () => {
    expect(bestRank([null, { category: 'MONTHLY_KWH', ownEntry: null }])).toBeNull()
  })
})

describe('buildLatest', () => {
  const log = { id: 'l1', loggedAt: '2026-10-01T18:00:00Z', kwhCharged: 34.2, kwhAtVehicle: 31.9, costEur: 12.4 }
  const trip = { id: 't1', tripStartedAt: '2026-10-02T07:00:00Z', tripEndedAt: '2026-10-02T07:40:00Z', distanceKm: 23.4 }

  it('nimmt Fahrzeug-kWh vor Saeulen-kWh und das Fahrtende als Zeitpunkt', () => {
    const l = buildLatest(log, trip)!
    expect(l.charge).toEqual({ id: 'l1', at: '2026-10-01T18:00:00Z', kwh: 31.9, costEur: 12.4 })
    expect(l.trip).toEqual({ id: 't1', at: '2026-10-02T07:40:00Z', km: 23.4 })
  })

  it('faellt auf Saeulen-kWh zurueck, Preis darf fehlen', () => {
    const l = buildLatest({ ...log, kwhAtVehicle: null, costEur: null }, null)!
    expect(l.charge).toEqual({ id: 'l1', at: '2026-10-01T18:00:00Z', kwh: 34.2, costEur: null })
    expect(l.trip).toBeNull()
  })

  it('nimmt Ladeort, Ladeart und Streckentyp mit, wenn bekannt', () => {
    const l = buildLatest({ ...log, cpoName: 'Tesla', chargingType: 'AC' }, { ...trip, routeType: 'CITY' })!
    expect(l.charge).toMatchObject({ cpoName: 'Tesla', chargingType: 'AC' })
    expect(l.trip).toMatchObject({ routeType: 'CITY' })
  })

  it('ist null ohne beides', () => {
    expect(buildLatest(null, null)).toBeNull()
    expect(buildLatest(undefined, undefined)).toBeNull()
  })
})

describe('sohIsEstimated: Tilde am Akkuwert', () => {
  const e = (recordedAt: string, source: string) => ({ recordedAt, source })

  it('entscheidet nach dem jüngsten Eintrag, egal in welcher Reihenfolge', () => {
    expect(sohIsEstimated([e('2026-10-01', 'MANUAL'), e('2026-09-01', 'CHARGE_LOG')])).toBe(false)
    expect(sohIsEstimated([e('2026-09-01', 'MANUAL'), e('2026-10-01', 'CHARGE_LOG')])).toBe(true)
  })

  it('ohne Verlauf keine Tilde: der Wert kommt aus der eigenen Angabe am Auto', () => {
    expect(sohIsEstimated([])).toBe(false)
  })
})
