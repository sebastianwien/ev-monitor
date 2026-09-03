import { describe, it, expect } from 'vitest'
import { amortisationTimeline } from '../amortisationTimeline'

/**
 * Die Schiene spannt vom ersten Jahr mit belegtem Heimladen bis zur vollen Amortisation.
 * Das Enddatum ist eine Fortschreibung, keine Zusage - es wird deshalb bewusst grob
 * angegeben (Anfang/Mitte/Ende eines Jahres), nicht auf den Tag.
 */
describe('amortisationTimeline', () => {
  const now = new Date('2026-09-02T12:00:00Z')

  it('spannt vom ersten Ladevorgang bis zur Amortisation', () => {
    const t = amortisationTimeline({ startYear: 2022, yearsRemaining: 4.97, now })!

    expect(t.startYear).toBe(2022)
    expect(t.endYear).toBe(2031)
    expect(t.totalYears).toBeCloseTo(9.64, 1)
  })

  it('setzt den Fortschritt auf den Anteil der bisherigen Nutzung', () => {
    const t = amortisationTimeline({ startYear: 2022, yearsRemaining: 4.97, now })!

    expect(t.progressPct).toBeCloseTo(48.4, 0)
  })

  /** Ein Strich je Jahresgrenze innerhalb der Laufzeit. */
  it('setzt einen Strich je Jahr', () => {
    const t = amortisationTimeline({ startYear: 2022, yearsRemaining: 4.97, now })!

    expect(t.tickPercents.length).toBe(9)
    t.tickPercents.forEach(p => {
      expect(p).toBeGreaterThan(0)
      expect(p).toBeLessThan(100)
    })
  })

  it('gibt das Enddatum grob an, nicht auf den Tag', () => {
    // Basis ist der 02.09.2026
    expect(amortisationTimeline({ startYear: 2025, yearsRemaining: 0.2, now })!.endPart).toBe('late')   // Nov 2026
    expect(amortisationTimeline({ startYear: 2025, yearsRemaining: 0.6, now })!.endPart).toBe('early')  // Apr 2027
    expect(amortisationTimeline({ startYear: 2025, yearsRemaining: 1.0, now })!.endPart).toBe('late')   // Sep 2027
  })

  /** Bereits abbezahlt: die Schiene ist voll, es gibt kein Enddatum mehr. */
  it('meldet vollstaendige Amortisation', () => {
    const t = amortisationTimeline({ startYear: 2020, yearsRemaining: 0, now })!

    expect(t.progressPct).toBe(100)
    expect(t.endYear).toBeNull()
  })

  /** Ohne Restlaufzeit (keine Investition hinterlegt) gibt es keine Schiene. */
  it('liefert nichts ohne Restlaufzeit', () => {
    expect(amortisationTimeline({ startYear: 2022, yearsRemaining: null, now })).toBeNull()
  })

  /** Sehr lange Restlaufzeiten duerfen die Strichdichte nicht sprengen. */
  it('duennt die Striche bei langen Laufzeiten aus', () => {
    const t = amortisationTimeline({ startYear: 2024, yearsRemaining: 30, now })!

    expect(t.tickPercents.length).toBeLessThanOrEqual(12)
  })
})
