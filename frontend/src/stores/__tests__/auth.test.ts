import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore, type JwtClaims } from '../auth'

/**
 * Marken-aware entitlement gates - frontend mirror of the backend User gates.
 *   - canViewLiveCharging(brand): free for Tesla, else AUTOSYNC_LIVE/ADMIN
 *   - canViewLiveAnalytics: paid analytics layer (power curves, phantom drain,
 *     energy split), no Tesla-free path
 */
describe('auth store entitlement gates', () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        localStorage.clear()
    })

    function setUser(role: string, subscriptionTier: string, premium: boolean) {
        const store = useAuthStore()
        store.user = { role, subscriptionTier } as JwtClaims
        store.setPremium(premium)
        return store
    }

    describe('canViewLiveCharging', () => {
        it('is free for a Tesla car regardless of tier/role', () => {
            const store = setUser('USER', 'NONE', false)
            expect(store.canViewLiveCharging('tesla')).toBe(true)
            expect(store.canViewLiveCharging('Tesla')).toBe(true)
            expect(store.canViewLiveCharging('TESLA')).toBe(true)
        })

        it('requires AUTOSYNC_LIVE or ADMIN for non-Tesla brands', () => {
            expect(setUser('USER', 'AUTOSYNC_LIVE', true).canViewLiveCharging('vw')).toBe(true)
            expect(setUser('ADMIN', 'NONE', false).canViewLiveCharging('vw')).toBe(true)
            expect(setUser('USER', 'AUTOSYNC', true).canViewLiveCharging('vw')).toBe(false)
            expect(setUser('USER', 'NONE', false).canViewLiveCharging('vw')).toBe(false)
            // BETA_TESTER excluded for non-Tesla so the card stays a paid preview
            expect(setUser('BETA_TESTER', 'NONE', false).canViewLiveCharging('vw')).toBe(false)
        })

        it('treats null/empty brand as non-Tesla (paid gate)', () => {
            expect(setUser('USER', 'NONE', false).canViewLiveCharging(null)).toBe(false)
            expect(setUser('USER', 'AUTOSYNC_LIVE', true).canViewLiveCharging(null)).toBe(true)
        })
    })

    describe('canViewLiveAnalytics', () => {
        it('is the paid gate with no Tesla-free path (every paid tier incl. AUTOSYNC)', () => {
            expect(setUser('USER', 'AUTOSYNC', true).canViewLiveAnalytics).toBe(true)
            expect(setUser('USER', 'AUTOSYNC_LIVE', true).canViewLiveAnalytics).toBe(true)
            expect(setUser('USER', 'SUPPORTER', true).canViewLiveAnalytics).toBe(true)
            expect(setUser('ADMIN', 'NONE', false).canViewLiveAnalytics).toBe(true)
            expect(setUser('BETA_TESTER', 'NONE', false).canViewLiveAnalytics).toBe(true)
            expect(setUser('USER', 'NONE', false).canViewLiveAnalytics).toBe(false)
        })
    })
})
