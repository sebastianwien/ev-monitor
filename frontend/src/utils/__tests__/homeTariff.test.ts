import { describe, it, expect } from 'vitest'
import { activeHomeTariff, type HomeTariffCard } from '../homeTariff'

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
