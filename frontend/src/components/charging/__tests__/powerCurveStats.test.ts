import { describe, it, expect } from 'vitest'
import { computeCurveStats } from '../powerCurveStats'

const MIN = 60_000

describe('computeCurveStats', () => {
  it('gibt null zurueck wenn keine Punkte vorliegen', () => {
    expect(computeCurveStats([])).toBeNull()
  })

  it('behandelt einen einzelnen Messpunkt als Momentaufnahme ohne Dauer', () => {
    const stats = computeCurveStats([{ ts: 0, kw: 120 }])
    expect(stats).toEqual({ peakKw: 120, avgKw: 120, durationMs: 0, energyKwh: 0 })
  })

  it('ermittelt Peak und Dauer ueber die gesamte Punktreihe', () => {
    const stats = computeCurveStats([
      { ts: 0, kw: 50 },
      { ts: 10 * MIN, kw: 250 },
      { ts: 30 * MIN, kw: 90 },
    ])!
    expect(stats.peakKw).toBe(250)
    expect(stats.durationMs).toBe(30 * MIN)
  })

  it('integriert die Energie per Trapezregel ueber ungleichmaessige dt', () => {
    // 0-10min: Trapez (50+250)/2 = 150 kW * (1/6) h = 25 kWh
    // 10-30min: Trapez (250+90)/2 = 170 kW * (1/3) h = 56,667 kWh
    const stats = computeCurveStats([
      { ts: 0, kw: 50 },
      { ts: 10 * MIN, kw: 250 },
      { ts: 30 * MIN, kw: 90 },
    ])!
    expect(stats.energyKwh).toBeCloseTo(81.667, 3)
  })

  it('mittelt zeitgewichtet, nicht ueber die Sample-Anzahl', () => {
    // Tesla streamt on-change: drei Samples in der ersten Minute, eines nach
    // einer Stunde. Der arithmetische Mittelwert waere 77,5 kW und damit falsch.
    const stats = computeCurveStats([
      { ts: 0, kw: 100 },
      { ts: 20_000, kw: 100 },
      { ts: 40_000, kw: 100 },
      { ts: 60 * MIN, kw: 10 },
    ])!
    expect(stats.avgKw).toBeLessThan(60)
    expect(stats.avgKw).toBeCloseTo(stats.energyKwh / (stats.durationMs / 3_600_000), 6)
  })

  it('faellt bei einer Punktreihe ohne Zeitspanne nicht auf Division durch null herein', () => {
    const stats = computeCurveStats([
      { ts: 5_000, kw: 40 },
      { ts: 5_000, kw: 80 },
    ])!
    expect(stats.durationMs).toBe(0)
    expect(stats.energyKwh).toBe(0)
    expect(stats.avgKw).toBe(80)
    expect(Number.isFinite(stats.avgKw)).toBe(true)
  })
})
