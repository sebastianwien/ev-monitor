import { describe, it, expect } from 'vitest'
import { chargeHeadline, chargeHeadlineText, addedRangeKm } from '../powerCurveHeadline'

const MIN = 60_000

describe('chargeHeadline', () => {
  it('nennt Dauer und Ladestand, wenn beides vorliegt', () => {
    expect(chargeHeadline({ durationMs: 16 * MIN, socBefore: 8, socAfter: 66, kwh: 41.7 }))
      .toEqual({ key: 'soc', duration: '16 Min', from: 8, to: 66 })
  })

  it('rundet den Ladestand auf ganze Prozent', () => {
    expect(chargeHeadline({ durationMs: 16 * MIN, socBefore: 7.6, socAfter: 66.4, kwh: null }))
      .toMatchObject({ from: 8, to: 66 })
  })

  it('weicht auf die Energie aus, wenn der Ladestand fehlt', () => {
    expect(chargeHeadline({ durationMs: 16 * MIN, socBefore: null, socAfter: 66, kwh: 41.7 }))
      .toEqual({ key: 'energy', duration: '16 Min', kwh: 41.7 })
  })

  it('nimmt keinen Ladestand, der nicht steigt', () => {
    expect(chargeHeadline({ durationMs: 16 * MIN, socBefore: 66, socAfter: 66, kwh: null }))
      .toEqual({ key: 'duration', duration: '16 Min' })
  })

  it('liefert nichts ohne Dauer', () => {
    expect(chargeHeadline({ durationMs: null, socBefore: 8, socAfter: 66, kwh: 41.7 })).toBeNull()
    expect(chargeHeadline({ durationMs: 0, socBefore: 8, socAfter: 66, kwh: 41.7 })).toBeNull()
  })
})

describe('addedRangeKm', () => {
  it('rechnet die geladene Energie über den Verbrauch in km um', () => {
    expect(addedRangeKm(41.7, 18)).toBe(232)
  })

  it('liefert nichts ohne Energie oder ohne gültigen Verbrauch', () => {
    expect(addedRangeKm(null, 18)).toBeNull()
    expect(addedRangeKm(0, 18)).toBeNull()
    expect(addedRangeKm(41.7, null)).toBeNull()
    expect(addedRangeKm(41.7, 0)).toBeNull()
    expect(addedRangeKm(41.7, -5)).toBeNull()
  })
})

describe('chargeHeadlineText', () => {
  const t = (key: string, params: Record<string, unknown>) => `${key}|${JSON.stringify(params)}`
  const kwh = (v: number) => `${v} kWh`

  it('wählt den Text zum Fall und reicht die Werte durch', () => {
    expect(chargeHeadlineText({ key: 'soc', duration: '16 Min', from: 8, to: 66 }, t, kwh))
      .toBe('dashboard.power_curve_headline_soc|{"duration":"16 Min","from":8,"to":66}')
    expect(chargeHeadlineText({ key: 'duration', duration: '16 Min' }, t, kwh))
      .toBe('dashboard.power_curve_headline_duration|{"duration":"16 Min"}')
  })

  it('formatiert die Energie über den übergebenen Formatierer', () => {
    expect(chargeHeadlineText({ key: 'energy', duration: '16 Min', kwh: 41.7 }, t, kwh))
      .toBe('dashboard.power_curve_headline_energy|{"duration":"16 Min","kwh":"41.7 kWh"}')
  })

  it('liefert nichts ohne Kopfsatz', () => {
    expect(chargeHeadlineText(null, t, kwh)).toBeNull()
  })
})
