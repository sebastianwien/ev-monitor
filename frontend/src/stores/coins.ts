import { defineStore } from 'pinia';
import { ref } from 'vue';
import api from '../api/axios';
import type { WattCatalog } from '../utils/wattPreview';

export const useCoinStore = defineStore('coins', () => {
    const balance = ref<number>(0);
    const coinsThisMonth = ref<number>(0);
    /** Belohnungskatalog fuer "+n Watt"-Vorschauen; null bis geladen. */
    const catalog = ref<WattCatalog | null>(null);

    const fetchBalance = async () => {
        try {
            const response = await api.get('/coins/balance');
            balance.value = response.data.totalCoins || 0;
            coinsThisMonth.value = response.data.coinsThisMonth || 0;
        } catch {
            // Silently fail - balance stays at 0
        }
    };

    const fetchCatalog = async () => {
        try {
            const res = await api.get<{ event: string; amount: number; oneTime: boolean; claimed: boolean }[]>('/coins/catalog');
            catalog.value = Object.fromEntries(res.data.map(e => [e.event, { amount: e.amount, oneTime: e.oneTime, claimed: e.claimed }]));
        } catch {
            // Ohne Katalog gibt es schlicht keine Vorschau-Badges
        }
    };

    /** Fuer Vorschau-Badges: einmal laden, danach aus dem Store. */
    const ensureCatalog = async () => { if (catalog.value === null) await fetchCatalog(); };

    /** Nach einer Aktion: Guthaben UND Katalog (einmalige Boni koennten jetzt beansprucht sein). */
    const refresh = async () => { await Promise.all([fetchBalance(), fetchCatalog()]); };

    return { balance, coinsThisMonth, catalog, fetchBalance, fetchCatalog, ensureCatalog, refresh };
});
