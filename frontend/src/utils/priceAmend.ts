/**
 * Reine Logik fuer den Preis-Nachtrag (PriceAmendModal): Kosten aus einer Ladekarte bzw. aus
 * einem manuell getippten Betrag, und der minimale PATCH-Payload.
 *
 * Bewusst nur die 4 Nachtrag-Felder: updateLog im Backend ist ein Partial-Patch (null = behalten),
 * daher darf hier NICHTS ueber kWh/Odometer/SoC/Zeit gesendet werden - das kWh-Paar ist gekoppelt
 * und wuerde bei einseitigem Senden das jeweils andere loeschen. Weglassen = erhalten.
 */

export type ChargingTypeInput = 'AC' | 'DC' | 'UNKNOWN' | null

export interface AmendCard {
  id: string
  acPricePerKwh: number | null
  dcPricePerKwh: number | null
}

/**
 * Gesamtkosten in EUR aus dem kWh-Preis der gewaehlten Karte. DC nutzt den DC-Preis, sonst AC.
 * Ohne Energie oder ohne passenden Kartenpreis gibt es keine Kosten (null) - dann tippt der User
 * seinen Betrag von Hand.
 */
export function costEurFromCard(card: AmendCard, chargingType: ChargingTypeInput, kwh: number | null): number | null {
  const price = chargingType === 'DC' ? card.dcPricePerKwh : card.acPricePerKwh
  return costEurFromPricePerKwh(price, kwh)
}

/** Gesamtkosten in EUR aus einem kWh-Preis (Karte oder Ortsvorschlag). */
export function costEurFromPricePerKwh(pricePerKwh: number | null, kwh: number | null): number | null {
  if (pricePerKwh == null || kwh == null || kwh <= 0) return null
  return Math.round(pricePerKwh * kwh * 100) / 100
}

/**
 * Energie, auf die sich der Preis bezieht: Brutto (kwhCharged), sonst Netto (kwhAtVehicle) -
 * AutoSync-Logs kennen oft nur den Netto-Wert, und der Feed rechnet genauso.
 */
export function amendKwh(log: { kwhCharged: number | null; kwhAtVehicle: number | null }): number | null {
  return log.kwhCharged ?? log.kwhAtVehicle ?? null
}

/** Gesamtbetrag zurueck auf EUR/kWh (4 Stellen) - der Wert, der als Kartentarif merkbar ist. */
export function pricePerKwhEur(costEur: number | null, kwh: number | null): number | null {
  if (costEur == null || kwh == null || kwh <= 0) return null
  return Math.round((costEur / kwh) * 10000) / 10000
}

/** Batch am Ort geht nur ueber eine Karte (der Endpoint bepreist mit ihrem Tarif). */
export function canApplyToLocation(chargingProviderId: string | null, pricelessCount: number): boolean {
  return chargingProviderId != null && pricelessCount > 0
}

/**
 * Manuell getippter Gesamtbetrag in Landeswaehrung -> EUR. `rate` ist EUR->lokal (aus
 * EUR_EXCHANGE_RATES), daher lokal / rate. EUR-Zone: rate = 1, also identisch.
 */
export function localTotalToEur(localTotal: number | null, rate: number): number | null {
  if (localTotal == null || Number.isNaN(localTotal) || localTotal < 0) return null
  if (!(rate > 0)) return null
  return Math.round((localTotal / rate) * 100) / 100
}

export interface AmendInput {
  costEur: number | null
  chargingProviderId: string | null
  cpoName: string | null
  isPublicCharging: boolean | null
  latitude: number | null
  longitude: number | null
  costCurrency: string | null
  costExchangeRate: number | null
}

/**
 * Minimaler PATCH-Body: nur gesetzte Felder. Alles Weggelassene behaelt das Backend bei.
 * lat/lon nur gemeinsam (das Backend leitet daraus den Geohash ab).
 */
export function buildAmendPayload(i: AmendInput): Record<string, unknown> {
  const p: Record<string, unknown> = {}
  if (i.costEur != null) p.costEur = i.costEur
  if (i.chargingProviderId != null) p.chargingProviderId = i.chargingProviderId
  if (i.isPublicCharging != null) p.isPublicCharging = i.isPublicCharging
  if (i.cpoName != null && i.cpoName !== '') p.cpoName = i.cpoName
  if (i.latitude != null && i.longitude != null) {
    p.latitude = i.latitude
    p.longitude = i.longitude
  }
  if (i.costCurrency != null) p.costCurrency = i.costCurrency
  if (i.costExchangeRate != null) p.costExchangeRate = i.costExchangeRate
  return p
}

/** Nachtrag ist absendbar, sobald entweder ein Preis oder eine Ladekarte vorliegt. */
export function isAmendValid(costEur: number | null, chargingProviderId: string | null): boolean {
  return costEur != null || chargingProviderId != null
}

export type PriceInputMode = 'total' | 'per_kwh'

/**
 * Getippter Wert -> Gesamtkosten in EUR. Im kWh-Modus zaehlt der Preis pro kWh in
 * Landeswaehrung mal Energie; ohne Energie gibt es keinen Betrag.
 */
export function manualCostEur(mode: PriceInputMode, typed: string | number, kwh: number | null, rate: number): number | null {
  // v-model auf type=number liefert eine Zahl, sonst einen String - beides normalisieren.
  const v = String(typed ?? '').trim()
  if (v === '') return null
  const n = Number(v)
  if (Number.isNaN(n) || n < 0) return null
  if (mode === 'total') return localTotalToEur(n, rate)
  if (kwh == null || kwh <= 0) return null
  return localTotalToEur(Math.round(n * kwh * 100) / 100, rate)
}

/**
 * Karte ODER Preis: hat die gewaehlte Karte fuer diesen Ladetyp einen Tarif, ist er der Preis
 * dieser Ladung und das manuelle Feld gesperrt - sonst wuerde ein Batch mit dem Kartentarif
 * etwas anderes bepreisen als der User gerade getippt hat. Eine Karte ohne Tarif laesst das
 * Feld offen, damit der getippte Preis als ihr Tarif hinterlegt werden kann.
 */
export function cardLocksManualPrice(card: AmendCard | null, chargingType: 'AC' | 'DC'): boolean {
  if (!card) return false
  return (chargingType === 'DC' ? card.dcPricePerKwh : card.acPricePerKwh) != null
}
