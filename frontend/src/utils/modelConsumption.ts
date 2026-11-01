import type { TopModelPreview } from '../api/publicModelService'

/**
 * Which consumption figure of a ranked model goes where. The backend owns the rule
 * (RangeConsumptionPolicy): other drivers' trips, else their charges, else WLTP × fleet factor.
 * This file only picks the field, with the charge figure as fallback for an older backend.
 */
type Figures = Pick<TopModelPreview, 'avgConsumptionKwhPer100km' | 'rangeConsumptionKwhPer100km' | 'rangeConsumptionSource'>

/** The figure the list shows as "Verbrauch", the same one the range rests on. */
export function shownConsumption(m: Figures): number | null {
  return m.rangeConsumptionKwhPer100km ?? m.avgConsumptionKwhPer100km ?? null
}

/** Only a measurement may rank ("Sparsamste", "Nah an WLTP"): a WLTP estimate sorts last. */
export function measuredConsumption(m: Figures): number | null {
  if (m.rangeConsumptionSource === 'WLTP_ESTIMATE') return null
  return shownConsumption(m)
}

/** Battery kWh per 100 km from the drivers' charges, the basis of every cost figure (costMix.plugKwhPer100km adds the losses). */
export function chargeConsumption(m: Figures): number | null {
  return m.avgConsumptionKwhPer100km ?? null
}

/** True when the shown figure comes from other drivers' trips, so texts speak of trips, not charges. */
export function consumptionFromTrips(m: Figures): boolean {
  return m.rangeConsumptionSource === 'PEER_DRIVE'
}
