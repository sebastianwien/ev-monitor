import type { ChargingProvider } from '../composables/useChargingProviders'

type PricedCard = Pick<ChargingProvider, 'acPricePerKwh' | 'dcPricePerKwh'>

/** Der fuer diesen Ladetyp geltende Kartenpreis (EUR/kWh) - null, wenn die Karte ihn nicht kennt. */
export function providerPriceForType(card: PricedCard, chargingType: 'AC' | 'DC'): number | null {
  return chargingType === 'DC' ? card.dcPricePerKwh : card.acPricePerKwh
}

/** Karte ohne jeden Tarif - kann keine Ladung bepreisen (amber Punkt auf der Kachel). */
export function providerHasNoPrice(card: PricedCard): boolean {
  return card.acPricePerKwh == null && card.dcPricePerKwh == null
}
