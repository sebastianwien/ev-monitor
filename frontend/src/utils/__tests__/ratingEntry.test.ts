import { describe, it, expect, beforeEach, vi } from 'vitest'
import { pickRatingTarget, isRatingTeaserVisible, ratingTeaserStorage } from '../ratingEntry'
import type { CarEligibility, RatingEligibilityResponse } from '../../api/ratingService'

function car(carId: string, over: Partial<CarEligibility> = {}): CarEligibility {
    return { carId, brandName: 'Volkswagen', modelName: 'ID.3', eligible: true, rated: false, kmDriven: 5000, logCount: 20, firstLogDate: null, ...over }
}
const elig = (cars: CarEligibility[]): RatingEligibilityResponse => ({ minLogs: 5, minKm: 300, cars })

describe('Zielauto der Ankündigung', () => {
    it('gibt es nur bei einem bewertbaren, unbewerteten Auto', () => {
        expect(pickRatingTarget(null, [])).toBeNull()
        expect(pickRatingTarget(elig([car('a', { eligible: false }), car('b', { rated: true })]), [])).toBeNull()
    })

    it('nennt das Modell bei genau einem Kandidaten', () => {
        expect(pickRatingTarget(elig([car('a', { modelName: 'Enyaq' }), car('b', { eligible: false })]), []))
            .toEqual({ carId: 'a', modelName: 'Enyaq', count: 1 })
    })

    it('verlinkt bei mehreren Kandidaten das Hauptfahrzeug', () => {
        const target = pickRatingTarget(elig([car('a'), car('b')]), [{ id: 'a', isPrimary: false }, { id: 'b', isPrimary: true }])
        expect(target).toEqual({ carId: 'b', modelName: 'ID.3', count: 2 })
    })

    it('nimmt den ersten Kandidaten, wenn das Hauptfahrzeug schon bewertet ist', () => {
        const target = pickRatingTarget(elig([car('a', { rated: true }), car('b'), car('c')]), [{ id: 'a', isPrimary: true }])
        expect(target?.carId).toBe('b')
    })
})

describe('Dashboard-Teaser', () => {
    it('erscheint nur für ein bewertbares, unbewertetes Auto, das nicht weggeklickt wurde', () => {
        expect(isRatingTeaserVisible(null, false)).toBe(false)
        expect(isRatingTeaserVisible(car('a', { eligible: false }), false)).toBe(false)
        expect(isRatingTeaserVisible(car('a', { rated: true }), false)).toBe(false)
        expect(isRatingTeaserVisible(car('a'), true)).toBe(false)
        expect(isRatingTeaserVisible(car('a'), false)).toBe(true)
    })

    describe('Wegklicken je Auto', () => {
        beforeEach(() => {
            localStorage.clear()
            vi.restoreAllMocks()
        })

        it('merkt sich das Wegklicken pro Auto', () => {
            ratingTeaserStorage.dismiss('a')
            expect(ratingTeaserStorage.isDismissed('a')).toBe(true)
            expect(ratingTeaserStorage.isDismissed('b')).toBe(false)
        })

        it('übersteht gesperrten Speicher', () => {
            vi.spyOn(localStorage, 'getItem').mockImplementation(() => { throw new Error('blocked') })
            vi.spyOn(localStorage, 'setItem').mockImplementation(() => { throw new Error('blocked') })
            expect(() => ratingTeaserStorage.dismiss('a')).not.toThrow()
            expect(ratingTeaserStorage.isDismissed('a')).toBe(false)
        })
    })
})
