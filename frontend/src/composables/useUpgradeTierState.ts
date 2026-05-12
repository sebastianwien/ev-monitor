import { computed, type Ref } from 'vue'

export type SubscriptionTier = 'NONE' | 'AUTOSYNC' | 'AUTOSYNC_LIVE'

/**
 * Tier-abhaengige UI-States fuer die /upgrade-Seite.
 * Ausgelagert aus UpgradeView damit die Logik isoliert testbar ist.
 */
export function useUpgradeTierState(tier: Ref<SubscriptionTier>) {
    const isAutoSyncActive = computed(() => tier.value === 'AUTOSYNC')
    const isLiveActive = computed(() => tier.value === 'AUTOSYNC_LIVE')
    /** AutoSync-Subscriber kann auf Live upgraden (in-place via Stripe-Proration). */
    const isLiveUpgrade = computed(() => tier.value === 'AUTOSYNC')
    const showPlanToggle = computed(() => tier.value === 'NONE')
    const activeBannerKey = computed(() =>
        tier.value === 'AUTOSYNC_LIVE' ? 'upgrade.tier_active_banner_live'
        : tier.value === 'AUTOSYNC' ? 'upgrade.tier_active_banner_autosync'
        : ''
    )

    return {
        isAutoSyncActive,
        isLiveActive,
        isLiveUpgrade,
        showPlanToggle,
        activeBannerKey,
    }
}
