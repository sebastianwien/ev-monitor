import { describe, it, expect } from 'vitest'
import { meanPowerKw, shouldAskHighway } from '../routeTypeHint'

/** Vorladung 07:00 UTC, 60 min, SoC-Ende 90 %; aktuelle Ladung als UTC-ISO, 75 kWh */
const prev = { loggedAt: '2026-03-01T07:00:00Z', chargeDurationMinutes: 60, socAfterChargePercent: 90 }
const cap = 75
const at = (iso: string) => new Date(iso)

describe('routeTypeHint: Frage nach Autobahn ab 15 kW mittlerer Leistung', () => {
  it('60 % Hub in 2 h bei 75 kWh = 22,5 kW: fragen', () => {
    const kw = meanPowerKw(prev, { loggedAt: null, socBeforeChargePercent: 30 }, cap, at('2026-03-01T10:00:00Z'))
    expect(kw).toBeCloseTo(22.5, 1)
    expect(shouldAskHighway([prev], { loggedAt: null, socBeforeChargePercent: 30 }, cap, at('2026-03-01T10:00:00Z'))).toBe(true)
  })
  it('20 % Hub in 2 h = 7,5 kW: nicht fragen', () => {
    expect(shouldAskHighway([prev], { loggedAt: null, socBeforeChargePercent: 70 }, cap, at('2026-03-01T10:00:00Z'))).toBe(false)
  })
  it('Lücke unter 30 min oder über 12 h: nicht fragen', () => {
    expect(meanPowerKw(prev, { loggedAt: null, socBeforeChargePercent: 30 }, cap, at('2026-03-01T08:20:00Z'))).toBeNull()
    expect(meanPowerKw(prev, { loggedAt: null, socBeforeChargePercent: 30 }, cap, at('2026-03-01T21:00:00Z'))).toBeNull()
  })
  it('Ladedauer verschiebt das Ende: 90 min Lücke gezählt ab 08:00', () => {
    const kw = meanPowerKw(prev, { loggedAt: null, socBeforeChargePercent: 60 }, cap, at('2026-03-01T09:30:00Z'))
    expect(kw).toBeCloseTo(15, 1)
  })
  it('ohne SoC-Start, ohne SoC-Ende oder ohne Kapazität: nicht fragen', () => {
    const now = at('2026-03-01T10:00:00Z')
    expect(shouldAskHighway([prev], { loggedAt: null, socBeforeChargePercent: null }, cap, now)).toBe(false)
    expect(shouldAskHighway([{ ...prev, socAfterChargePercent: null }], { loggedAt: null, socBeforeChargePercent: 30 }, cap, now)).toBe(false)
    expect(shouldAskHighway([prev], { loggedAt: null, socBeforeChargePercent: 30 }, null, now)).toBe(false)
  })
  it('Vorladung ist die jüngste Ladung vor dem aktuellen Zeitpunkt, spätere Logs zählen nicht', () => {
    const later = { loggedAt: '2026-03-02T07:00:00Z', chargeDurationMinutes: 0, socAfterChargePercent: 100 }
    expect(shouldAskHighway([later, prev], { loggedAt: null, socBeforeChargePercent: 30 }, cap, at('2026-03-01T10:00:00Z'))).toBe(true)
  })
})
