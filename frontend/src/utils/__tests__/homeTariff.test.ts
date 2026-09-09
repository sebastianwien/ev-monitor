import { describe, it, expect } from 'vitest'
import { activeHomeTariff, homeTariffConflict, type HomeTariffCard } from '../homeTariff'

const card = (over: Partial<HomeTariffCard> = {}): HomeTariffCard => ({
  id: crypto.randomUUID(),
  providerName: 'Zuhause',
  label: null,
  acPricePerKwh: 0.25,
  activeFrom: '2026-01-01',
  activeUntil: null,
  isPrivate: true,
  ...over,
})

describe('activeHomeTariff', () => {
  it('findet die eine als privat markierte Karte', () => {
    const home = card()
    expect(activeHomeTariff([card({ isPrivate: false }), home], '2026-09-09')?.id).toBe(home.id)
  })

  it('ignoriert oeffentliche Ladekarten komplett', () => {
    expect(activeHomeTariff([card({ isPrivate: false })], '2026-09-09')).toBeNull()
  })

  it('liefert nichts bei zwei gleichzeitig gueltigen Heimtarifen - das waere mehrdeutig', () => {
    expect(activeHomeTariff([card(), card()], '2026-09-09')).toBeNull()
  })

  it('beachtet den Gueltigkeitszeitraum', () => {
    expect(activeHomeTariff([card({ activeFrom: '2026-10-01' })], '2026-09-09')).toBeNull()
    expect(activeHomeTariff([card({ activeUntil: '2026-08-31' })], '2026-09-09')).toBeNull()
    expect(activeHomeTariff([card({ activeUntil: '2026-09-09' })], '2026-09-09')).not.toBeNull()
  })

  it('kommt mit fehlender Kartenliste klar', () => {
    expect(activeHomeTariff(null)).toBeNull()
    expect(activeHomeTariff([])).toBeNull()
  })
})

describe('homeTariffConflict', () => {
  const form = (activeFrom: string, isPrivate = true) => ({ isPrivate, activeFrom })

  it('meldet nichts, wenn die Karte kein Heimtarif ist', () => {
    expect(homeTariffConflict([card()], form('2026-09-09', false))).toBeNull()
  })

  it('meldet nichts, wenn es noch keinen Heimtarif gibt', () => {
    expect(homeTariffConflict([card({ isPrivate: false })], form('2026-09-09'))).toBeNull()
  })

  it('kuendigt an, dass der bisherige Heimtarif am Tag davor endet', () => {
    const prev = card({ activeFrom: '2026-01-01' })
    const c = homeTariffConflict([prev], form('2026-09-09'))
    expect(c).toEqual({ type: 'ends', card: prev, endsOn: '2026-09-08' })
  })

  it('warnt vor einer Ueberschneidung, wenn der neue Tarif frueher beginnt', () => {
    const existing = card({ activeFrom: '2026-01-01' })
    expect(homeTariffConflict([existing], form('2025-06-01'))?.type).toBe('overlap')
  })

  it('warnt auch beim selben Starttag', () => {
    expect(homeTariffConflict([card({ activeFrom: '2026-09-09' })], form('2026-09-09'))?.type).toBe('overlap')
  })

  it('ignoriert einen bereits beendeten Heimtarif', () => {
    expect(homeTariffConflict([card({ activeFrom: '2025-01-01', activeUntil: '2026-06-30' })], form('2026-09-09'))).toBeNull()
  })

  it('die bearbeitete Karte kollidiert nicht mit sich selbst', () => {
    const self = card({ activeFrom: '2026-01-01' })
    expect(homeTariffConflict([self], form('2026-09-09'), self.id)).toBeNull()
  })
})
