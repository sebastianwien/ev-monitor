import { describe, it, expect } from 'vitest'
import { wattPreview, type WattCatalog } from '../wattPreview'

const catalog: WattCatalog = {
  PRICE_ADDED: { amount: 3, oneTime: false, claimed: false },
  CARD_LINKED: { amount: 2, oneTime: false, claimed: false },
  CPO_ADDED: { amount: 3, oneTime: false, claimed: false },
  CARD_CREATED: { amount: 5, oneTime: true, claimed: false },
}

describe('wattPreview', () => {
  it('zaehlt nur, was die Ladung vorher nicht hatte', () => {
    expect(wattPreview(catalog, { addsPrice: true, addsCard: true, addsCpo: true, batchCount: 0 })).toBe(8)
    expect(wattPreview(catalog, { addsPrice: false, addsCard: false, addsCpo: false, batchCount: 0 })).toBe(0)
  })

  it('Batch zahlt pro Ladung, gedeckelt auf 20', () => {
    expect(wattPreview(catalog, { addsPrice: true, addsCard: false, addsCpo: false, batchCount: 4 })).toBe(3 + 12)
    expect(wattPreview(catalog, { addsPrice: false, addsCard: false, addsCpo: false, batchCount: 25 })).toBe(60)
  })

  it('einmaliger Kartenbonus nur, solange er nicht beansprucht ist', () => {
    expect(wattPreview(catalog, { addsPrice: false, addsCard: false, addsCpo: false, batchCount: 0, createsCard: true })).toBe(5)
    const claimed = { ...catalog, CARD_CREATED: { amount: 5, oneTime: true, claimed: true } }
    expect(wattPreview(claimed, { addsPrice: false, addsCard: false, addsCpo: false, batchCount: 0, createsCard: true })).toBe(0)
  })

  it('ohne Katalog (noch nicht geladen) ist die Vorschau 0', () => {
    expect(wattPreview(null, { addsPrice: true, addsCard: true, addsCpo: true, batchCount: 3 })).toBe(0)
  })
})

import { wattPossibleForLog } from '../wattPreview'

describe('wattPossibleForLog', () => {
  it('oeffentliche Ladung: Preis + Karte + Betreiber', () => {
    expect(wattPossibleForLog(catalog, { costEur: null, chargingProviderId: null, cpoName: null, isPublicCharging: true })).toBe(8)
  })
  it('private Ladung: kein Betreiber moeglich', () => {
    expect(wattPossibleForLog(catalog, { costEur: null, chargingProviderId: null, cpoName: null, isPublicCharging: false })).toBe(5)
  })
  it('was schon da ist, zaehlt nicht mehr', () => {
    expect(wattPossibleForLog(catalog, { costEur: null, chargingProviderId: 'c', cpoName: 'Aldi', isPublicCharging: true })).toBe(3)
  })
})
