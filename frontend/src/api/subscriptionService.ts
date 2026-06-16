import api from './axios';

export type SubscriptionTier = 'NONE' | 'AUTOSYNC' | 'AUTOSYNC_LIVE' | 'SUPPORTER';

export interface SubscriptionStatus {
    isPremium: boolean;
    tier: SubscriptionTier;
    premiumEnabled: boolean;
    subscriptionPeriodEnd: string | null;
}

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
    ): Promise<CheckoutResponse> {
        const response = await api.post('/subscription/checkout', { plan, tier });
        return response.data;
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
