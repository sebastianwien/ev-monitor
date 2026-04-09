import { defineStore } from 'pinia';
import api from '../api/axios';
import { ref, computed } from 'vue';
import { jwtDecode, type JwtPayload } from 'jwt-decode';
import { useCarStore } from './car';

function safeLocalStorage(op: () => void): void {
    try { op() } catch { /* localStorage blocked (Private Mode, strict tracking protection) */ }
}

function safeLocalStorageGet(key: string): string | null {
    try { return localStorage.getItem(key) } catch { return null }
}

export const useAuthStore = defineStore('auth', () => {
    const token = ref<string | null>(safeLocalStorageGet('token'));
    const user = ref<any>(null);
    const isPremium = ref<boolean>(safeLocalStorageGet('isPremium') === 'true');
    if (token.value) {
        try {
            user.value = jwtDecode(token.value);
        } catch (e) {
            token.value = null;
            safeLocalStorage(() => localStorage.removeItem('token'));
        }
    }

    const setToken = (newToken: string) => {
        token.value = newToken;
        safeLocalStorage(() => localStorage.setItem('token', newToken));
        try {
            user.value = jwtDecode(newToken);
            // Mark browser as "was real user" so feedback toast is suppressed after logout
            if (!(user.value as any)?.demoAccount) {
                safeLocalStorage(() => localStorage.setItem('wasRealUser', '1'));
            }
        } catch (e) {
            user.value = null;
        }
    };

    const setPremium = (value: boolean) => {
        isPremium.value = value;
        safeLocalStorage(() => localStorage.setItem('isPremium', String(value)));
    };

    const logout = (redirect = true) => {
        token.value = null;
        user.value = null;
        isPremium.value = false;
        safeLocalStorage(() => localStorage.removeItem('token'));
        safeLocalStorage(() => localStorage.removeItem('isPremium'));
        useCarStore().reset();
        if (redirect) {
            window.location.href = '/login';
        }
    };

    const login = async (credentials: any) => {
        const response = await api.post('/auth/login', credentials);
        if (response.data.token) {
            setToken(response.data.token);
        }
        setPremium(response.data.isPremium ?? false);
    };

    const register = async (userData: any) => {
        // Returns { status: "PENDING_VERIFICATION", email } - no JWT yet
        const response = await api.post('/auth/register', userData);
        return response.data;
    };

    const refreshToken = async () => {
        try {
            const response = await api.post('/auth/refresh');
            if (response.data.token) {
                setToken(response.data.token);
            }
            setPremium(response.data.isPremium ?? false);
        } catch {
            // Ignore errors — don't disrupt the user
        }
    };

    const refreshPremiumStatus = async () => {
        await refreshToken();
    };

    const isExpired = (): boolean => {
        if (!token.value) return true;
        try {
            const decoded = jwtDecode<JwtPayload>(token.value);
            return decoded.exp !== undefined && decoded.exp * 1000 < Date.now();
        } catch {
            return true;
        }
    };

    const isDemoAccount = computed(() => (user.value as any)?.demoAccount === true);
    const isAdmin = computed(() => (user.value as any)?.role === 'ADMIN');

    return {
        token, user, isDemoAccount, isPremium, isAdmin,
        setToken, setPremium, logout, login, register,
        refreshToken, refreshPremiumStatus,
        isAuthenticated: () => !!token.value,
        isExpired,
    };
});
