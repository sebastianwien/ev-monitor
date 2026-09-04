import { describe, it, expect } from 'vitest'
import { isEnergySplitTrialActive, energySplitTrialEnd, isWithinLaunchTrial } from '../featureTrial'

// Launch des Energie-Split-Trials: 2026-09-04, 30 Tage.
const d = (iso: string) => new Date(`${iso}T12:00:00`)

describe('isEnergySplitTrialActive', () => {
  it('ist offen fuer Bestandsuser ab Launch fuer einen Monat', () => {
    const reg = '2026-05-01' // vor Launch -> verankert am Launch 2026-09-04
    expect(isEnergySplitTrialActive(reg, d('2026-09-04'))).toBe(true)
    expect(isEnergySplitTrialActive(reg, d('2026-10-04'))).toBe(true)  // letzter Tag
    expect(isEnergySplitTrialActive(reg, d('2026-10-05'))).toBe(false) // danach
  })

  it('verankert Neuregistrierte an ihrem Registrierungsdatum', () => {
    const reg = '2026-09-20' // nach Launch -> Fenster ab 2026-09-20
    expect(isEnergySplitTrialActive(reg, d('2026-10-15'))).toBe(true)
    expect(isEnergySplitTrialActive(reg, d('2026-10-20'))).toBe(true)  // 30 Tage
    expect(isEnergySplitTrialActive(reg, d('2026-10-21'))).toBe(false)
  })

  it('ist ohne Registrierungsdatum aus (alte Tokens)', () => {
    expect(isEnergySplitTrialActive(undefined, d('2026-09-10'))).toBe(false)
    expect(isEnergySplitTrialActive(null, d('2026-09-10'))).toBe(false)
  })

  it('nennt das Trial-Ende (letzter Tag)', () => {
    expect(energySplitTrialEnd('2026-09-04')?.getFullYear()).toBe(2026)
    expect(energySplitTrialEnd('2026-09-04')?.getMonth()).toBe(9)   // Oktober (0-basiert)
    expect(energySplitTrialEnd('2026-09-04')?.getDate()).toBe(4)
    expect(energySplitTrialEnd(null)).toBeNull()
  })

  it('ist generisch parametrisierbar', () => {
    expect(isWithinLaunchTrial('2026-01-01', '2026-01-01', 10, d('2026-01-11'))).toBe(true)
    expect(isWithinLaunchTrial('2026-01-01', '2026-01-01', 10, d('2026-01-12'))).toBe(false)
  })
})
