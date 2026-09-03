import { describe, it, expect } from 'vitest'
import { providerPriceForType, providerHasNoPrice } from '../chargingProviderPricing'

describe('providerPriceForType', () => {
  const card = { acPricePerKwh: 0.30, dcPricePerKwh: 0.50 }

  it('waehlt den DC-Preis bei DC-Ladung', () => {
    expect(providerPriceForType(card, 'DC')).toBe(0.50)
  })

  it('waehlt den AC-Preis bei AC-Ladung', () => {
    expect(providerPriceForType(card, 'AC')).toBe(0.30)
  })

  it('gibt null, wenn genau dieser Ladetyp keinen Preis hat', () => {
    expect(providerPriceForType({ acPricePerKwh: 0.30, dcPricePerKwh: null }, 'DC')).toBeNull()
    expect(providerPriceForType({ acPricePerKwh: null, dcPricePerKwh: 0.50 }, 'AC')).toBeNull()
  })

  it('behandelt einen 0-Preis als gueltigen Preis, nicht als fehlend', () => {
    expect(providerPriceForType({ acPricePerKwh: 0, dcPricePerKwh: null }, 'AC')).toBe(0)
  })
})

describe('providerHasNoPrice', () => {
  it('true nur wenn beide Preise fehlen', () => {
    expect(providerHasNoPrice({ acPricePerKwh: null, dcPricePerKwh: null })).toBe(true)
  })

  it('false sobald ein Preis vorhanden ist - auch 0', () => {
    expect(providerHasNoPrice({ acPricePerKwh: 0.30, dcPricePerKwh: null })).toBe(false)
    expect(providerHasNoPrice({ acPricePerKwh: null, dcPricePerKwh: 0.50 })).toBe(false)
    expect(providerHasNoPrice({ acPricePerKwh: 0, dcPricePerKwh: null })).toBe(false)
  })
})
