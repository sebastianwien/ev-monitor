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

// ── Kontext + Nudge-Logik ────────────────────────────────────────────────────
import { amendKwh, pricePerKwhEur, costEurFromPricePerKwh, canApplyToLocation } from '../priceAmend'

describe('amendKwh', () => {
  it('nimmt kwhCharged, sonst kwhAtVehicle (Netto-only-Logs aus AutoSync)', () => {
    expect(amendKwh({ kwhCharged: 12.3, kwhAtVehicle: 11 })).toBe(12.3)
    expect(amendKwh({ kwhCharged: null, kwhAtVehicle: 11 })).toBe(11)
    expect(amendKwh({ kwhCharged: null, kwhAtVehicle: null })).toBeNull()
  })
})

describe('pricePerKwhEur', () => {
  it('rechnet den Gesamtbetrag auf EUR/kWh mit 4 Stellen zurueck', () => {
    expect(pricePerKwhEur(12.3, 25)).toBe(0.492)
    expect(pricePerKwhEur(4.15, 13.83)).toBe(0.3001)
  })

  it('gibt null ohne Betrag oder ohne Energie', () => {
    expect(pricePerKwhEur(null, 25)).toBeNull()
    expect(pricePerKwhEur(12.3, null)).toBeNull()
    expect(pricePerKwhEur(12.3, 0)).toBeNull()
  })
})

describe('costEurFromPricePerKwh', () => {
  it('ist die Umkehrung fuer den Preisvorschlag', () => {
    expect(costEurFromPricePerKwh(0.49, 25)).toBe(12.25)
    expect(costEurFromPricePerKwh(null, 25)).toBeNull()
    expect(costEurFromPricePerKwh(0.49, null)).toBeNull()
  })
})

describe('canApplyToLocation', () => {
  it('Batch nur mit Karte und nur wenn es am Ort etwas zu bepreisen gibt', () => {
    expect(canApplyToLocation('card', 3)).toBe(true)
    expect(canApplyToLocation(null, 3)).toBe(false)
    expect(canApplyToLocation('card', 0)).toBe(false)
  })
})

import { manualCostEur } from '../priceAmend'

describe('manualCostEur', () => {
  it('Gesamtbetrag: lokal -> EUR', () => {
    expect(manualCostEur('total', '12.30', 25, 1)).toBe(12.3)
    expect(manualCostEur('total', '123', 25, 10)).toBe(12.3)
  })

  it('kWh-Preis: mal Energie, lokal -> EUR', () => {
    expect(manualCostEur('per_kwh', '0.49', 25, 1)).toBe(12.25)
    expect(manualCostEur('per_kwh', '4.9', 25, 10)).toBe(12.25)
  })

  it('kWh-Preis ohne Energie ergibt keinen Betrag', () => {
    expect(manualCostEur('per_kwh', '0.49', null, 1)).toBeNull()
    expect(manualCostEur('per_kwh', '0.49', 0, 1)).toBeNull()
  })

  it('leer oder Unsinn ergibt null', () => {
    expect(manualCostEur('total', '', 25, 1)).toBeNull()
    expect(manualCostEur('per_kwh', 'abc', 25, 1)).toBeNull()
    expect(manualCostEur('total', '-1', 25, 1)).toBeNull()
  })
})

describe('manualCostEur mit Zahl statt String (v-model auf type=number castet)', () => {
  it('akzeptiert eine Zahl', () => {
    expect(manualCostEur('total', 111, 18, 1)).toBe(111)
    expect(manualCostEur('per_kwh', 0.49, 18, 1)).toBe(8.82)
  })
})

import { cardLocksManualPrice } from '../priceAmend'

describe('cardLocksManualPrice (Karte ODER Preis)', () => {
  it('eine Karte mit Preis fuer den Ladetyp sperrt das Preisfeld', () => {
    expect(cardLocksManualPrice(card, 'AC')).toBe(true)
  })
  it('eine Karte ohne Preis fuer den Ladetyp laesst das Feld offen - der Tarif kann nachgetragen werden', () => {
    expect(cardLocksManualPrice({ id: 'x', acPricePerKwh: null, dcPricePerKwh: 0.5 }, 'AC')).toBe(false)
  })
  it('ohne Karte ist das Feld offen', () => {
    expect(cardLocksManualPrice(null, 'AC')).toBe(false)
  })
})
