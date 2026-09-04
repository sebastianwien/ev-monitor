import { defineStore } from 'pinia';
import api from '../api/axios';
import { ref, computed } from 'vue';
import { jwtDecode, type JwtPayload } from 'jwt-decode';
import { isEnergySplitTrialActive, energySplitTrialEnd } from '../utils/featureTrial';
import { useCarStore } from './car';

export interface JwtClaims {
    sub: string           // email (JWT standard subject)
    iat: number           // issued at (JWT standard, seconds since epoch)
    exp: number           // expiration (JWT standard, seconds since epoch)
    userId: string
    username: string
    demoAccount: boolean
    authProvider: string
    role: string
    premium: boolean
    subscriptionTier?: string  // 'NONE' | 'AUTOSYNC' | 'AUTOSYNC_LIVE' - present for tokens generated after 2026-05
    country?: string
    registeredAt?: string // ISO date YYYY-MM-DD, present for tokens generated after 2026-04
}

function safeLocalStorage(op: () => void): void {
    try { op() } catch { /* localStorage blocked (Private Mode, strict tracking protection) */ }
}

function safeLocalStorageGet(key: string): string | null {
    try { return localStorage.getItem(key) } catch { return null }
}

export const useAuthStore = defineStore('auth', () => {
    const token = ref<string | null>(safeLocalStorageGet('token'));
    const user = ref<JwtClaims | null>(null);
    const isPremium = ref<boolean>(safeLocalStorageGet('isPremium') === 'true');
    if (token.value) {
        try {
            user.value = jwtDecode<JwtClaims>(token.value);
        } catch (e) {
            token.value = null;
            safeLocalStorage(() => localStorage.removeItem('token'));
        }
    }

    const setToken = (newToken: string) => {
        token.value = newToken;
        safeLocalStorage(() => localStorage.setItem('token', newToken));
        try {
            user.value = jwtDecode<JwtClaims>(newToken);
            // Mark browser as "was real user" so feedback toast is suppressed after logout
            if (!user.value?.demoAccount) {
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

    // True when token is still valid but expires within 3 days — triggers silent background refresh
    const needsRefresh = (): boolean => {
        if (!token.value || isExpired()) return false;
        try {
            const decoded = jwtDecode<JwtPayload>(token.value);
            const expiresInMs = (decoded.exp ?? 0) * 1000 - Date.now();
            return expiresInMs < 3 * 24 * 60 * 60 * 1000;
        } catch {
            return false;
        }
    };

    const isDemoAccount = computed(() => user.value?.demoAccount === true);
    const isAdmin = computed(() => user.value?.role === 'ADMIN');
    const isBetaTester = computed(() => user.value?.role === 'BETA_TESTER');
    const isTeslaFounder = computed(() => user.value?.role === 'TESLA_FOUNDER');
    const isAutoSync = computed(() => user.value?.subscriptionTier === 'AUTOSYNC');
    const isAutoSyncLive = computed(() => user.value?.subscriptionTier === 'AUTOSYNC_LIVE');
    // Analytics-only upsell: unlocks canViewLiveAnalytics but NOT telemetry (isPremium stays
    // false server-side for SUPPORTER, so canActivateTelemetry excludes it automatically).
    const isSupporter = computed(() => user.value?.subscriptionTier === 'SUPPORTER');

    // Tesla brand helper: data collection, the live-charging card and trip detection are
    // free for Tesla drivers; other brands stay on the paid AutoSync model.
    const isTeslaBrand = (brand?: string | null) => (brand ?? '').toLowerCase() === 'tesla';

    // Mirrors backend User.canActivateTelemetry(): the paid path (Smartcar). Tesla activation
    // is free and is decided per-brand in the pairing UI, not here. Server-side gate in
    // TeslaPairingService is the security boundary - this computed is purely UX.
    const canActivateTelemetry = computed(() =>
        isPremium.value || isAdmin.value || isBetaTester.value || isTeslaFounder.value);

    // Mirrors backend User.canViewLiveAnalytics(): the paid analytics layer - historical
    // power curves, phantom drain im Logfeed, Share. No Tesla-free path. Jeder bezahlte Tarif
    // (AUTOSYNC, AUTOSYNC_LIVE, SUPPORTER) plus ADMIN/BETA - der Live-Trip-Feed (Trip-Push)
    // haengt am schmaleren Backend-Gate und ist hier bewusst nicht gespiegelt. Server-side
    // gate in EvLogService (power curves) und EvLogShareService (Share). UX only. Der
    // Energie-Split-Probemonat haengt bewusst NICHT hier - er oeffnet nur die Kachel, nicht
    // die server-zurueckgehaltenen Daten.
    const canViewLiveAnalytics = computed(() =>
        isAutoSync.value || isAutoSyncLive.value || isSupporter.value || isAdmin.value || isBetaTester.value);

    // Energie-Split-Probemonat: launch-verankert aus dem JWT-registeredAt (spiegelt das
    // frontend-eigene featureTrial). Der Donut wird client-seitig aus dem eigenen Feed
    // abgeleitet - kein Server-Secret -, deshalb ist das Trial ein reines Display-Gate NUR
    // der Energie-Split-Kachel. Ladekurven/Standverluste im Logfeed/Share bleiben an
    // canViewLiveAnalytics und damit im Probemonat zu.
    const energySplitTrialActive = computed(() => isEnergySplitTrialActive(user.value?.registeredAt));
    const energySplitTrialEndsAt = computed(() => energySplitTrialEnd(user.value?.registeredAt));
    const canViewEnergySplit = computed(() => canViewLiveAnalytics.value || energySplitTrialActive.value);
    // Zugang haengt allein am Probemonat (nicht bezahlt, keine Rolle) -> dann der Retention-Hinweis.
    const energySplitViaTrial = computed(() =>
        energySplitTrialActive.value && !canViewLiveAnalytics.value);

    // Mirrors backend User.canViewSocCurve(): der Ladeverlauf ist bewusst weiter
    // gefasst als die Ladekurve - er ist das, was Quellen ohne Leistungsmessung
    // ueberhaupt hergeben, und entsteht aus den eigenen Webhooks des Nutzers.
    // Server-Gate in EvLogService. UX only.
    const canViewSocCurve = computed(() =>
        user.value?.subscriptionTier === 'AUTOSYNC' || canViewLiveAnalytics.value);

    // Mirrors backend User.canViewLiveCharging(CarBrand): the dashboard Live-Charging card
    // is free for Tesla, otherwise AUTOSYNC_LIVE or ADMIN (BETA_TESTER excluded for other
    // brands so the card stays a paid-feature preview). Server-side gate in LiveController.
    const canViewLiveCharging = (brand?: string | null) =>
        isTeslaBrand(brand) || isAutoSyncLive.value || isAdmin.value;

    return {
        token, user, isDemoAccount, isPremium, isAdmin, isBetaTester, isTeslaFounder,
        isAutoSyncLive, isSupporter,
        canActivateTelemetry, canViewLiveAnalytics, canViewSocCurve, canViewLiveCharging,
        canViewEnergySplit, energySplitViaTrial, energySplitTrialEndsAt,
        setToken, setPremium, logout, login, register,
        refreshToken, refreshPremiumStatus,
        isAuthenticated: () => !!token.value,
        isExpired,
        needsRefresh,
    };
});
