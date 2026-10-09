import type { CarEligibility, RatingEligibilityResponse } from '../api/ratingService'

/** Auto, auf das die Ankündigung verlinkt. {@code count} > 1 heißt: Text ohne Modellname. */
export interface RatingTarget {
    carId: string
    modelName: string
    count: number
}

/** Bewertbar und noch nicht bewertet. Bei mehreren das Hauptfahrzeug, sonst das erste. */
export function pickRatingTarget(
    eligibility: RatingEligibilityResponse | null,
    cars: { id: string; isPrimary: boolean }[],
): RatingTarget | null {
    const candidates = eligibility?.cars.filter(c => c.eligible && !c.rated) ?? []
    if (candidates.length === 0) return null
    const primaryId = cars.find(c => c.isPrimary)?.id
    const target = candidates.find(c => c.carId === primaryId) ?? candidates[0]
    return { carId: target.carId, modelName: target.modelName, count: candidates.length }
}

export function isRatingTeaserVisible(car: CarEligibility | null, dismissed: boolean): boolean {
    return !!car && car.eligible && !car.rated && !dismissed
}

const teaserKey = (carId: string) => `rating-teaser-dismissed:${carId}`

/** Weggeklickter Dashboard-Teaser je Auto. Ohne Speicher erscheint er eben wieder. */
export const ratingTeaserStorage = {
    isDismissed(carId: string): boolean {
        try {
            return localStorage.getItem(teaserKey(carId)) === '1'
        } catch {
            return false
        }
    },
    dismiss(carId: string): void {
        try {
            localStorage.setItem(teaserKey(carId), '1')
        } catch {
            // egal
        }
    },
}
