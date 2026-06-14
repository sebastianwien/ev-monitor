import { describe, it, expect } from 'vitest'
import { ref } from 'vue'
import { useTeslaImportGating } from '../useTeslaImportGating'
import type { Car } from '../../api/carService'

function buildCar(overrides: Partial<Car>): Car {
    return {
        id: 'car-1',
        brand: 'Tesla',
        model: 'Model 3',
        year: 2022,
        status: 'ACTIVE',
        ...overrides,
    } as Car
}

describe('useTeslaImportGating - hasAnyTesla', () => {
    it('is true when user has an ACTIVE Tesla', () => {
        const cars = ref<Car[]>([buildCar({ status: 'ACTIVE' })])
        expect(useTeslaImportGating(cars).hasAnyTesla.value).toBe(true)
    })

    it('is true when user has a non-ACTIVE Tesla (e.g. INACTIVE/SOLD)', () => {
        const cars = ref<Car[]>([buildCar({ status: 'INACTIVE' as Car['status'] })])
        expect(useTeslaImportGating(cars).hasAnyTesla.value).toBe(true)
    })

    it('is true regardless of brand casing', () => {
        const cars = ref<Car[]>([buildCar({ brand: 'TESLA' })])
        expect(useTeslaImportGating(cars).hasAnyTesla.value).toBe(true)
    })

    it('is false when user has only non-Tesla brands', () => {
        const cars = ref<Car[]>([buildCar({ brand: 'BMW' }), buildCar({ brand: 'VW' })])
        expect(useTeslaImportGating(cars).hasAnyTesla.value).toBe(false)
    })

    it('is false on empty car list', () => {
        const cars = ref<Car[]>([])
        expect(useTeslaImportGating(cars).hasAnyTesla.value).toBe(false)
    })
})

describe('useTeslaImportGating - showTeslaTab', () => {
    it('is TRUE for any Tesla owner, regardless of subscription/role (Tesla telemetry is free)', () => {
        const cars = ref<Car[]>([buildCar({})])
        expect(useTeslaImportGating(cars).showTeslaTab.value).toBe(true)
    })

    it('is TRUE even for a not-yet-active Tesla', () => {
        const cars = ref<Car[]>([buildCar({ status: 'INACTIVE' as Car['status'] })])
        expect(useTeslaImportGating(cars).showTeslaTab.value).toBe(true)
    })

    it('is FALSE when the user owns no Tesla', () => {
        const cars = ref<Car[]>([buildCar({ brand: 'BMW' })])
        expect(useTeslaImportGating(cars).showTeslaTab.value).toBe(false)
    })
})
