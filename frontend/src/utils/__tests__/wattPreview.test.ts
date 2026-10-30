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

import { wattForNewLog } from '../wattPreview'

describe('wattForNewLog', () => {
  const logCatalog: WattCatalog = {
    MANUAL_LOG_SUBSEQUENT: { amount: 5, oneTime: false, claimed: false },
    MANUAL_LOG_OCR: { amount: 7, oneTime: false, claimed: false },
  }
  it('neue Ladung bringt den Regelbetrag, mit Foto-Erkennung den OCR-Betrag', () => {
    expect(wattForNewLog(logCatalog, false)).toBe(5)
    expect(wattForNewLog(logCatalog, true)).toBe(7)
  })
  it('ohne Katalog keine Zahl', () => {
    expect(wattForNewLog(null, false)).toBe(0)
  })
  it('erste Ladung überhaupt: der Erst-Bonus, solange er nicht verbraucht ist', () => {
    const fresh: WattCatalog = { ...logCatalog,
      MANUAL_LOG_FIRST: { amount: 25, oneTime: false, claimed: false },
      MANUAL_LOG_FIRST_OCR: { amount: 27, oneTime: false, claimed: false } }
    expect(wattForNewLog(fresh, false)).toBe(25)
    expect(wattForNewLog(fresh, true)).toBe(27)
    const used = { ...fresh, MANUAL_LOG_FIRST: { amount: 25, oneTime: false, claimed: true } }
    expect(wattForNewLog(used, false)).toBe(5)
  })
})
