import { describe, it, expect } from 'vitest'
import { thgSeason, thgCopy, thgDismissActive, THG_SEASONS } from '../thgOffer'

const at = (iso: string) => new Date(`${iso}T12:00:00`)

describe('thgSeason', () => {
  it('liefert 2026 bis einschließlich zur Frist des Partners', () => {
    expect(thgSeason(at('2026-11-07'))?.year).toBe(2026)
  })

  it('wechselt am Starttag auf 2027 mit Betrag', () => {
    const s = thgSeason(at('2026-11-08'))
    expect(s?.year).toBe(2027)
    expect(s?.amountEur).toBe(230)
  })

  it('wechselt um Mitternacht Ortszeit, nicht nach UTC', () => {
    expect(thgSeason(new Date('2026-11-08T00:30:00'))?.year).toBe(2027)
    expect(thgSeason(new Date('2026-11-07T23:30:00'))?.year).toBe(2026)
  })

  it('liefert null nach der letzten bekannten Saison', () => {
    const last = THG_SEASONS[THG_SEASONS.length - 1]
    const after = new Date(`${last.until}T12:00:00`)
    after.setDate(after.getDate() + 1)
    expect(thgSeason(after)).toBeNull()
  })
})

describe('thgCopy', () => {
  it('nennt den Betrag, wenn er bekannt ist', () => {
    expect(thgCopy(thgSeason(at('2026-11-08'))!).title).toBe('230 € für dein E-Auto, einmal im Jahr.')
  })

  it('fragt ohne Betrag nach dem Antrag fürs Jahr', () => {
    expect(thgCopy(thgSeason(at('2026-11-05'))!).title).toBe('THG-Prämie 2026 schon beantragt?')
  })
})

describe('thgDismissActive', () => {
  const season = thgSeason(at('2026-11-20'))!

  it('ohne Wegklicken ist nichts ausgeblendet', () => {
    expect(thgDismissActive(null, at('2026-11-20'), season)).toBe(false)
  })

  it('blendet 90 Tage aus', () => {
    expect(thgDismissActive(at('2026-11-10').getTime(), at('2026-12-01'), season)).toBe(true)
    expect(thgDismissActive(at('2026-11-10').getTime(), at('2027-02-10'), season)).toBe(false)
  })

  it('eine neue Saison zeigt den Hinweis wieder, auch innerhalb von 90 Tagen', () => {
    expect(thgDismissActive(at('2026-10-20').getTime(), at('2026-11-08'), thgSeason(at('2026-11-08'))!)).toBe(false)
  })
})
