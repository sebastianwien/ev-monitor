import { defineStore } from 'pinia';
import { ref } from 'vue';
import api from '../api/axios';

export const useCoinStore = defineStore('coins', () => {
    const balance = ref<number>(0);

    const fetchBalance = async () => {
        try {
            const response = await api.get('/coins/balance');
            balance.value = response.data.totalCoins || 0;
        } catch {
            // Silently fail - balance stays at 0
        }
    };

    const refresh = fetchBalance;

    return { balance, fetchBalance, refresh };
});
