import { describe, it, expect } from 'vitest'
import { comparisonLevel, comparisonDeltaPercent, comparisonChipClass } from '../communityComparison'

describe('comparisonLevel', () => {
  // Bei Verbrauch und Kosten ist niedriger immer besser.
  it('wertet deutlich niedrigere Werte als besser, deutlich hoehere als schlechter', () => {
    expect(comparisonLevel(15, 20)).toBe('better')
    expect(comparisonLevel(22, 20)).toBe('worse')
  })

  it('wertet Werte innerhalb von ±5 % als vergleichbar', () => {
    expect(comparisonLevel(19.5, 20)).toBe('similar')
    expect(comparisonLevel(20.9, 20)).toBe('similar')
    expect(comparisonLevel(20, 20)).toBe('similar')
  })

  it('liegt die Grenze exakt bei 5 %', () => {
    expect(comparisonLevel(19, 20)).toBe('similar')   // -5 % noch vergleichbar
    expect(comparisonLevel(21, 20)).toBe('similar')   // +5 % noch vergleichbar
    expect(comparisonLevel(18.99, 20)).toBe('better')
    expect(comparisonLevel(21.01, 20)).toBe('worse')
  })

  it('ist null ohne eigenen Wert oder ohne Community-Schnitt', () => {
    expect(comparisonLevel(null, 20)).toBeNull()
    expect(comparisonLevel(15, null)).toBeNull()
    expect(comparisonLevel(15, 0)).toBeNull()
  })

  it('wertet mehr als 30 % ueber dem Schnitt als deutlich schlechter', () => {
    expect(comparisonLevel(26, 20)).toBe('worse')        // exakt +30 % bleibt amber
    expect(comparisonLevel(26.01, 20)).toBe('much_worse')
    expect(comparisonLevel(25.99, 20)).toBe('worse')
    expect(comparisonLevel(40, 20)).toBe('much_worse')
  })
})

describe('comparisonDeltaPercent', () => {
  it('nennt die Abweichung vom Schnitt in ganzen Prozent', () => {
    expect(comparisonDeltaPercent(15, 20)).toBe(-25)
    expect(comparisonDeltaPercent(22, 20)).toBe(10)
  })

  it('ist null ohne Vergleichsbasis', () => {
    expect(comparisonDeltaPercent(15, null)).toBeNull()
    expect(comparisonDeltaPercent(null, 20)).toBeNull()
    expect(comparisonDeltaPercent(15, 0)).toBeNull()
  })
})

describe('comparisonChipClass', () => {
  it('liefert fuer jede Stufe eine eigene Farbwelt und neutral als Fallback', () => {
    const better = comparisonChipClass('better')
    const similar = comparisonChipClass('similar')
    const worse = comparisonChipClass('worse')
    const muchWorse = comparisonChipClass('much_worse')
    const neutral = comparisonChipClass(null)

    expect(new Set([better, similar, worse, muchWorse, neutral]).size).toBe(5)
    expect(better).toContain('emerald')
    expect(worse).toContain('amber')
    expect(muchWorse).toContain('rose')
  })
})
