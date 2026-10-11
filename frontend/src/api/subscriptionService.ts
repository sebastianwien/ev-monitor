import api from './axios';
import { analytics } from '../services/analytics';
import { currentUpsellSource } from '../services/upsellSource';

export type SubscriptionTier = 'NONE' | 'AUTOSYNC' | 'AUTOSYNC_LIVE' | 'SUPPORTER';

export interface SubscriptionStatus {
    isPremium: boolean;
    tier: SubscriptionTier;
    premiumEnabled: boolean;
    subscriptionPeriodEnd: string | null;
    /** Erstkäufer bekommen im Stripe-Checkout 7 Tage Trial. */
    trialEligible?: boolean;
    /** AutoSync-Plätze: ein Abo deckt ein Fahrzeug. Fehlt bei älteren Backends, dann 1. */
    autoSyncSlots?: number;
}

/** A promo code the user can redeem, e.g. FIRSTEV with 28 trial days. */
export interface PromoOffer {
    code: string;
    trialDays: number;
}

export type PromoCheck =
    | { valid: true; code: string; trialDays: number }
    | { valid: false; reason: 'UNKNOWN' | 'NOT_ELIGIBLE' | 'TRIAL_USED' | 'ALREADY_REDEEMED' | 'EXHAUSTED' };

export interface CheckoutResponse {
    checkoutUrl: string;
}

export const subscriptionService = {
    async getStatus(): Promise<SubscriptionStatus> {
        const response = await api.get('/subscription/status');
        return response.data;
    },

    async createCheckoutSession(
        plan: 'monthly' | 'yearly',
        tier: 'autosync' | 'autosync_live' | 'supporter' = 'autosync',
        promoCode?: string,
    ): Promise<CheckoutResponse> {
        // Hier statt an jedem Kaufknopf: alle Wege zu Stripe laufen durch diese Methode
        analytics.trackCheckoutStarted(plan, tier, currentUpsellSource());
        const response = await api.post('/subscription/checkout', { plan, tier, promoCode });
        return response.data;
    },

    async checkPromoCode(code: string): Promise<PromoCheck> {
        const response = await api.get(`/subscription/promo/${encodeURIComponent(code.trim())}`);
        return response.data;
    },

    /** Offer for the user's sign-up source (teaser banner), null when there is none. */
    async getPromoOffer(): Promise<PromoOffer | null> {
        const response = await api.get('/subscription/promo-offer');
        return response.status === 200 ? response.data : null;
    },

    async upgradeToLive(): Promise<{ status: string }> {
        const response = await api.post('/subscription/upgrade');
        return response.data;
    },

    async downgradeToAutoSync(): Promise<{ status: string }> {
        const response = await api.post('/subscription/downgrade');
        return response.data;
    },

    async cancelSubscription(): Promise<{ status: string }> {
        const response = await api.post('/subscription/cancel');
        return response.data;
    },

    async createPortalSession(): Promise<{ portalUrl: string }> {
        const response = await api.post('/subscription/portal');
        return response.data;
    }
};
