import { describe, it, expect } from 'vitest'
import { costMetrics, type CostMetricFormat } from '../costMetrics'

const f: CostMetricFormat = { isEurCountry: true, subunit: 'ct', symbol: '€', eurToLocal: e => e, formatNumber: n => String(n), formatDecimal: (n, d) => n.toFixed(d) }
const preview = { kwhPer100km: 16.24, eurPer100km: 4.5, distanceKm: 200, plausible: true, estimated: false }

describe('costMetrics', () => {
  it('Verbrauch aus Brutto steht ohne Tilde, Kosten je 100 km immer exakt', () => {
    const m = costMetrics(0.3, preview, f)
    expect(m.map(x => x.value)).toEqual(['30', '4.50', '16.2'])
  })
  it('aus Netto hochgerechneter Verbrauch bekommt "~", die Kosten nicht', () => {
    const m = costMetrics(null, { ...preview, estimated: true }, f)
    expect(m.map(x => x.value)).toEqual(['4.50', '~16.2'])
  })
})
