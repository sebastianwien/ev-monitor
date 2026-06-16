import { describe, it, expect } from 'vitest'
import { ref } from 'vue'
import { useUpgradeTierState, type SubscriptionTier } from '../useUpgradeTierState'

describe('useUpgradeTierState', () => {
    describe('Tier NONE (Free-User)', () => {
        const tier = ref<SubscriptionTier>('NONE')
        const s = useUpgradeTierState(tier)

        it('weder AutoSync noch Live aktiv', () => {
            expect(s.isAutoSyncActive.value).toBe(false)
            expect(s.isLiveActive.value).toBe(false)
        })
        it('zeigt den Monthly/Yearly-Toggle', () => {
            expect(s.showPlanToggle.value).toBe(true)
        })
        it('kein Live-Upgrade-Pfad (User hat noch kein Abo)', () => {
            expect(s.isLiveUpgrade.value).toBe(false)
        })
        it('hat keinen Active-Banner-Key', () => {
            expect(s.activeBannerKey.value).toBe('')
        })
    })

    describe('Tier AUTOSYNC (Tier 1)', () => {
        const tier = ref<SubscriptionTier>('AUTOSYNC')
        const s = useUpgradeTierState(tier)

        it('AutoSync aktiv, Live nicht', () => {
            expect(s.isAutoSyncActive.value).toBe(true)
            expect(s.isLiveActive.value).toBe(false)
        })
        it('versteckt den Plan-Toggle (bestehende Stripe-Periode)', () => {
            expect(s.showPlanToggle.value).toBe(false)
        })
        it('Live-Button wird zum in-place Upgrade', () => {
            expect(s.isLiveUpgrade.value).toBe(true)
        })
        it('zeigt AutoSync-Banner (nicht "alles freigeschaltet")', () => {
            expect(s.activeBannerKey.value).toBe('upgrade.tier_active_banner_autosync')
        })
    })

    describe('Tier AUTOSYNC_LIVE (Tier 2)', () => {
        const tier = ref<SubscriptionTier>('AUTOSYNC_LIVE')
        const s = useUpgradeTierState(tier)

        it('Live aktiv, AutoSync (Tier-1-only-State) nicht', () => {
            expect(s.isLiveActive.value).toBe(true)
            expect(s.isAutoSyncActive.value).toBe(false)
        })
        it('versteckt den Plan-Toggle', () => {
            expect(s.showPlanToggle.value).toBe(false)
        })
        it('kein Upgrade-Pfad mehr (Top-Tier)', () => {
            expect(s.isLiveUpgrade.value).toBe(false)
        })
        it('zeigt Live-Banner', () => {
            expect(s.activeBannerKey.value).toBe('upgrade.tier_active_banner_live')
        })
    })

    describe('Tier SUPPORTER (Analytics-Upsell, orthogonal)', () => {
        const tier = ref<SubscriptionTier>('SUPPORTER')
        const s = useUpgradeTierState(tier)

        it('weder AutoSync- noch Live-State aktiv', () => {
            expect(s.isAutoSyncActive.value).toBe(false)
            expect(s.isLiveActive.value).toBe(false)
        })
        it('versteckt den Plan-Toggle (bestehendes Abo)', () => {
            expect(s.showPlanToggle.value).toBe(false)
        })
        it('kein in-place Live-Upgrade', () => {
            expect(s.isLiveUpgrade.value).toBe(false)
        })
        it('zeigt den Supporter-Banner', () => {
            expect(s.activeBannerKey.value).toBe('upgrade.tier_active_banner_supporter')
        })
    })

    it('reagiert reaktiv auf Tier-Wechsel', () => {
        const tier = ref<SubscriptionTier>('NONE')
        const s = useUpgradeTierState(tier)
        expect(s.showPlanToggle.value).toBe(true)

        tier.value = 'AUTOSYNC'
        expect(s.showPlanToggle.value).toBe(false)
        expect(s.isLiveUpgrade.value).toBe(true)

        tier.value = 'AUTOSYNC_LIVE'
        expect(s.isLiveActive.value).toBe(true)
        expect(s.isLiveUpgrade.value).toBe(false)
    })
})
