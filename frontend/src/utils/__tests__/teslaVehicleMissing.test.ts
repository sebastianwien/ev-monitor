import { describe, it, expect } from 'vitest'
import { isTeslaVehicleMissing } from '../teslaVehicleMissing'

describe('isTeslaVehicleMissing', () => {
    const base = { keyPaired: false, telemetryConfigPushed: false }

    it('is true when the connection has no VIN (car was not in the Tesla account at OAuth time)', () => {
        expect(isTeslaVehicleMissing({ ...base, vin: null })).toBe(true)
        expect(isTeslaVehicleMissing({ ...base, vin: '' })).toBe(true)
    })

    it('is false when a VIN is known', () => {
        expect(isTeslaVehicleMissing({ ...base, vin: 'LRW3E7EL8PC798571' })).toBe(false)
    })

    it('is false while no status is loaded', () => {
        expect(isTeslaVehicleMissing(null)).toBe(false)
    })
})
