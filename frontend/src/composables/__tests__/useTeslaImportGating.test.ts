import { describe, it, expect } from 'vitest'
import { ref } from 'vue'
import { useTeslaImportGating, type TeslaImportRoles } from '../useTeslaImportGating'
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

const ROLES = {
    none: { isAdmin: false, isBetaTester: false, isTeslaFounder: false } as TeslaImportRoles,
    admin: { isAdmin: true, isBetaTester: false, isTeslaFounder: false } as TeslaImportRoles,
    beta: { isAdmin: false, isBetaTester: true, isTeslaFounder: false } as TeslaImportRoles,
    founder: { isAdmin: false, isBetaTester: false, isTeslaFounder: true } as TeslaImportRoles,
}

describe('useTeslaImportGating - hasAnyTesla', () => {
    it('is true when user has an ACTIVE Tesla', () => {
        const cars = ref<Car[]>([buildCar({ status: 'ACTIVE' })])
        const { hasAnyTesla } = useTeslaImportGating(cars, ref(ROLES.none))
        expect(hasAnyTesla.value).toBe(true)
    })

    it('is true when user has a non-ACTIVE Tesla (e.g. INACTIVE/SOLD)', () => {
        // The whole point of switching from hasActiveTesla → hasAnyTesla is letting
        // users with not-yet-active Teslas still see the AutoSync sub-section.
        const cars = ref<Car[]>([buildCar({ status: 'INACTIVE' as Car['status'] })])
        const { hasAnyTesla } = useTeslaImportGating(cars, ref(ROLES.none))
        expect(hasAnyTesla.value).toBe(true)
    })

    it('is true regardless of brand casing', () => {
        const cars = ref<Car[]>([buildCar({ brand: 'TESLA' })])
        const { hasAnyTesla } = useTeslaImportGating(cars, ref(ROLES.none))
        expect(hasAnyTesla.value).toBe(true)
    })

    it('is false when user has only non-Tesla brands', () => {
        const cars = ref<Car[]>([buildCar({ brand: 'BMW' }), buildCar({ brand: 'VW' })])
        const { hasAnyTesla } = useTeslaImportGating(cars, ref(ROLES.none))
        expect(hasAnyTesla.value).toBe(false)
    })

    it('is false on empty car list', () => {
        const cars = ref<Car[]>([])
        const { hasAnyTesla } = useTeslaImportGating(cars, ref(ROLES.none))
        expect(hasAnyTesla.value).toBe(false)
    })
})

describe('useTeslaImportGating - showLegacyTeslaTab', () => {
    it('is FALSE for Premium-only users with Tesla (the whole point of this change)', () => {
        // Premium-only Tesla owners now see the AutoSync sub-section instead.
        const cars = ref<Car[]>([buildCar({})])
        const { showLegacyTeslaTab } = useTeslaImportGating(cars, ref(ROLES.none))
        expect(showLegacyTeslaTab.value).toBe(false)
    })

    it('is TRUE for ADMIN with Tesla', () => {
        const cars = ref<Car[]>([buildCar({})])
        const { showLegacyTeslaTab } = useTeslaImportGating(cars, ref(ROLES.admin))
        expect(showLegacyTeslaTab.value).toBe(true)
    })

    it('is TRUE for BETA_TESTER with Tesla', () => {
        const cars = ref<Car[]>([buildCar({})])
        const { showLegacyTeslaTab } = useTeslaImportGating(cars, ref(ROLES.beta))
        expect(showLegacyTeslaTab.value).toBe(true)
    })

    it('is TRUE for TESLA_FOUNDER with Tesla', () => {
        const cars = ref<Car[]>([buildCar({})])
        const { showLegacyTeslaTab } = useTeslaImportGating(cars, ref(ROLES.founder))
        expect(showLegacyTeslaTab.value).toBe(true)
    })

    it('is FALSE for ADMIN without any Tesla', () => {
        const cars = ref<Car[]>([buildCar({ brand: 'BMW' })])
        const { showLegacyTeslaTab } = useTeslaImportGating(cars, ref(ROLES.admin))
        expect(showLegacyTeslaTab.value).toBe(false)
    })

    it('accepts a plain (non-ref) roles object too', () => {
        const cars = ref<Car[]>([buildCar({})])
        const { showLegacyTeslaTab } = useTeslaImportGating(cars, ROLES.founder)
        expect(showLegacyTeslaTab.value).toBe(true)
    })
})
