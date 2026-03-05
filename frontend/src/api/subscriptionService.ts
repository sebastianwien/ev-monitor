import api from './axios';

export interface SubscriptionStatus {
    isPremium: boolean;
    premiumEnabled: boolean;
}

export interface CheckoutResponse {
    checkoutUrl: string;
}

export const subscriptionService = {
    async getStatus(): Promise<SubscriptionStatus> {
        const response = await api.get('/subscription/status');
        return response.data;
    },

    async createCheckoutSession(plan: 'monthly' | 'yearly'): Promise<CheckoutResponse> {
        const response = await api.post('/subscription/checkout', { plan });
        return response.data;
    }
};
