import { describe, it, expect } from 'vitest'
import { costEurFromCard, localTotalToEur, buildAmendPayload, isAmendValid } from '../priceAmend'

const card = { id: 'c1', acPricePerKwh: 0.30, dcPricePerKwh: 0.50 }

describe('costEurFromCard', () => {
  it('nimmt den AC-Preis fuer AC-Ladung', () => {
    expect(costEurFromCard(card, 'AC', 10)).toBe(3.0)
  })

  it('nimmt den DC-Preis fuer DC-Ladung', () => {
    expect(costEurFromCard(card, 'DC', 10)).toBe(5.0)
  })

  it('faellt bei unbekanntem Ladetyp auf AC zurueck', () => {
    expect(costEurFromCard(card, 'UNKNOWN', 10)).toBe(3.0)
    expect(costEurFromCard(card, null, 10)).toBe(3.0)
  })

  it('rundet auf zwei Nachkommastellen', () => {
    expect(costEurFromCard(card, 'AC', 7.77)).toBe(2.33) // 0.30 * 7.77 = 2.331
  })

  it('gibt null ohne Energie', () => {
    expect(costEurFromCard(card, 'AC', null)).toBeNull()
    expect(costEurFromCard(card, 'AC', 0)).toBeNull()
  })

  it('gibt null, wenn die Karte fuer den Ladetyp keinen Preis hat', () => {
    expect(costEurFromCard({ id: 'x', acPricePerKwh: null, dcPricePerKwh: 0.5 }, 'AC', 10)).toBeNull()
  })
})

describe('localTotalToEur', () => {
  it('rechnet EUR-Zone unveraendert (rate 1)', () => {
    expect(localTotalToEur(12.5, 1)).toBe(12.5)
  })

  it('rechnet Landeswaehrung in EUR (NOK rate 11.5)', () => {
    expect(localTotalToEur(115, 11.5)).toBe(10.0)
  })

  it('gibt null bei fehlender Eingabe oder ungueltiger Rate', () => {
    expect(localTotalToEur(null, 1)).toBeNull()
    expect(localTotalToEur(NaN, 1)).toBeNull()
    expect(localTotalToEur(-1, 1)).toBeNull()
    expect(localTotalToEur(10, 0)).toBeNull()
  })
})

describe('buildAmendPayload', () => {
  const base = {
    costEur: null, chargingProviderId: null, cpoName: null,
    isPublicCharging: null, latitude: null, longitude: null,
    costCurrency: null, costExchangeRate: null,
  }

  it('laesst kWh/Odometer/Zeit bewusst weg - nur gesetzte Nachtrag-Felder', () => {
    const p = buildAmendPayload({ ...base, costEur: 5.5 })
    expect(p).toEqual({ costEur: 5.5 })
    expect(p).not.toHaveProperty('kwhCharged')
    expect(p).not.toHaveProperty('loggedAt')
  })

  it('nimmt Karte, Preis, Waehrung und oeffentlich mit', () => {
    const p = buildAmendPayload({
      ...base, costEur: 5.5, chargingProviderId: 'c1', costCurrency: 'NOK', costExchangeRate: 11.5, isPublicCharging: true,
    })
    expect(p).toEqual({ costEur: 5.5, chargingProviderId: 'c1', costCurrency: 'NOK', costExchangeRate: 11.5, isPublicCharging: true })
  })

  it('sendet lat/lon nur gemeinsam', () => {
    expect(buildAmendPayload({ ...base, latitude: 52.5, longitude: null })).toEqual({})
    expect(buildAmendPayload({ ...base, latitude: 52.5, longitude: 13.4 })).toEqual({ latitude: 52.5, longitude: 13.4 })
  })

  it('laesst leeren CPO-Namen weg', () => {
    expect(buildAmendPayload({ ...base, cpoName: '' })).toEqual({})
    expect(buildAmendPayload({ ...base, cpoName: 'IONITY' })).toEqual({ cpoName: 'IONITY' })
  })
})

describe('isAmendValid', () => {
  it('gilt mit Preis', () => expect(isAmendValid(5, null)).toBe(true))
  it('gilt mit Ladekarte', () => expect(isAmendValid(null, 'c1')).toBe(true))
  it('ist ohne beides ungueltig', () => expect(isAmendValid(null, null)).toBe(false))
})
