import { describe, expect, it } from 'vitest'
import { chargeConsumption, consumptionFromTrips, measuredConsumption, shownConsumption } from '../modelConsumption'

const charges = { avgConsumptionKwhPer100km: 19.1, rangeConsumptionKwhPer100km: 19.1, rangeConsumptionSource: 'PEER_CHARGES' as const }
const trips = { ...charges, rangeConsumptionKwhPer100km: 16.4, rangeConsumptionSource: 'PEER_DRIVE' as const }
const estimate = { avgConsumptionKwhPer100km: null, rangeConsumptionKwhPer100km: 18.7, rangeConsumptionSource: 'WLTP_ESTIMATE' as const }
const oldBackend = { avgConsumptionKwhPer100km: 19.1 }

describe('modelConsumption', () => {
  it('shows the range basis: trips before charges, the charge figure on an older backend', () => {
    expect(shownConsumption(trips)).toBe(16.4)
    expect(shownConsumption(charges)).toBe(19.1)
    expect(shownConsumption(estimate)).toBe(18.7)
    expect(shownConsumption(oldBackend)).toBe(19.1)
  })

  it('ranks only measurements, an estimate sorts last', () => {
    expect(measuredConsumption(trips)).toBe(16.4)
    expect(measuredConsumption(estimate)).toBeNull()
    expect(measuredConsumption(oldBackend)).toBe(19.1)
  })

  it('costs rest on the charge figure, never on trips or an estimate', () => {
    expect(chargeConsumption(trips)).toBe(19.1)
    expect(chargeConsumption(estimate)).toBeNull()
    expect(chargeConsumption(oldBackend)).toBe(19.1)
  })

  it('names trips as the source only for PEER_DRIVE', () => {
    expect(consumptionFromTrips(trips)).toBe(true)
    expect(consumptionFromTrips(charges)).toBe(false)
    expect(consumptionFromTrips(oldBackend)).toBe(false)
  })
})
