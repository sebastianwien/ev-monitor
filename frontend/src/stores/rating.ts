import { defineStore } from 'pinia'
import { ref } from 'vue'
import { ratingService, type RatingEligibilityResponse, type CarEligibility } from '../api/ratingService'

/**
 * Bewertbarkeit der eigenen Autos - ein Request für alle Einstiege (Announcement, Dashboard,
 * /cars, Modellseite, Lösch-Flow). Nach dem Bewerten invalidieren.
 */
export const useRatingStore = defineStore('rating', () => {
    const eligibility = ref<RatingEligibilityResponse | null>(null)
    let pending: Promise<RatingEligibilityResponse> | null = null

    async function getEligibility(forceRefresh = false): Promise<RatingEligibilityResponse> {
        if (!forceRefresh && eligibility.value) return eligibility.value
        if (!pending) {
            pending = ratingService.getEligibility().then(data => {
                eligibility.value = data
                return data
            }).finally(() => {
                pending = null
            })
        }
        return pending
    }

    function forCar(carId: string): CarEligibility | null {
        return eligibility.value?.cars.find(c => c.carId === carId) ?? null
    }

    function invalidate() {
        eligibility.value = null
    }

    return { eligibility, getEligibility, forCar, invalidate }
})
