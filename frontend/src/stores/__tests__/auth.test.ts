import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore, type JwtClaims } from '../auth'

/**
 * Marken-aware entitlement gates - frontend mirror of the backend User gates.
 *   - canViewLiveCharging(brand): Tesla only (only Fleet Telemetry delivers live data)
 *   - canViewLiveAnalytics: paid analytics layer (power curves, phantom drain im
 *     Logfeed, Share), no Tesla-free path, kein Probemonat
 *   - canViewEnergySplit: die Energie-Split-Kachel = bezahlt ODER launch-verankerter
 *     Probemonat (reines Display-Gate, oeffnet die server-zurueckgehaltenen Daten NICHT)
 */
describe('auth store entitlement gates', () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        localStorage.clear()
    })

    afterEach(() => {
        vi.useRealTimers()
    })

    function setUser(role: string, subscriptionTier: string, premium: boolean, registeredAt?: string) {
        const store = useAuthStore()
        store.user = { role, subscriptionTier, registeredAt } as JwtClaims
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

        it('is closed for non-Tesla brands regardless of tier/role (no live source)', () => {
            expect(setUser('USER', 'AUTOSYNC_LIVE', true).canViewLiveCharging('vw')).toBe(false)
            expect(setUser('ADMIN', 'NONE', false).canViewLiveCharging('vw')).toBe(false)
            expect(setUser('USER', 'NONE', false).canViewLiveCharging('vw')).toBe(false)
            expect(setUser('USER', 'AUTOSYNC_LIVE', true).canViewLiveCharging(null)).toBe(false)
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

        it('is NOT opened by the energy-split trial (logfeed/curves stay paid)', () => {
            vi.useFakeTimers()
            vi.setSystemTime(new Date('2026-09-20T12:00:00')) // im Probemonat
            expect(setUser('USER', 'NONE', false, '2026-05-01').canViewLiveAnalytics).toBe(false)
        })
    })

    describe('canViewEnergySplit (Kachel: bezahlt ODER Probemonat)', () => {
        it('is open for every paid tier, like the analytics gate', () => {
            expect(setUser('USER', 'AUTOSYNC', true).canViewEnergySplit).toBe(true)
            expect(setUser('ADMIN', 'NONE', false).canViewEnergySplit).toBe(true)
        })

        it('is closed for a free user with no registration date (old token)', () => {
            expect(setUser('USER', 'NONE', false).canViewEnergySplit).toBe(false)
        })

        it('opens for a free user during the launch trial, without opening the paid layer', () => {
            vi.useFakeTimers()
            vi.setSystemTime(new Date('2026-09-20T12:00:00'))
            const store = setUser('USER', 'NONE', false, '2026-05-01')
            expect(store.canViewEnergySplit).toBe(true)
            expect(store.energySplitViaTrial).toBe(true)
            expect(store.canViewLiveAnalytics).toBe(false)
        })

        it('does not flag energySplitViaTrial for a paying user in the window', () => {
            vi.useFakeTimers()
            vi.setSystemTime(new Date('2026-09-20T12:00:00'))
            expect(setUser('USER', 'AUTOSYNC', true, '2026-05-01').energySplitViaTrial).toBe(false)
        })

        it('falls back to paid-only after the trial ends', () => {
            vi.useFakeTimers()
            vi.setSystemTime(new Date('2026-10-05T12:00:00'))
            expect(setUser('USER', 'NONE', false, '2026-05-01').canViewEnergySplit).toBe(false)
        })
    })
})
