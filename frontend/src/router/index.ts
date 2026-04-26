import { createRouter, createWebHistory } from 'vue-router';
import { useAuthStore } from '../stores/auth';
import { useCountryStore } from '../stores/country';
import { setLocale, isValidLocale } from '../i18n';
import type { CountryCode } from '../config/unitSystems';
import { isValidCountryCode } from '../config/unitSystems';

declare module 'vue-router' {
    interface RouteMeta {
        requiresAuth?: boolean
        requiresAdmin?: boolean
        guestOnly?: boolean
        locale?: string
        country?: CountryCode
    }
}
import LandingPageView from '../views/LandingPageView.vue';
import LandingPageV2View from '../views/LandingPageV2View.vue';
import LogFormView from '../views/LogFormView.vue';
import LoginView from '../views/LoginView.vue';
import RegisterView from '../views/RegisterView.vue';
import VerifyEmailView from '../views/VerifyEmailView.vue';
import OAuth2RedirectHandler from '../views/OAuth2RedirectHandler.vue';
import CarManagementView from '../views/CarManagementView.vue';
import DashboardView from '../views/DashboardView.vue';
import SettingsView from '../views/SettingsView.vue';
import PublicModelsListView from '../views/PublicModelsListView.vue';
import PublicBrandView from '../views/PublicBrandView.vue';
import PublicModelView from '../views/PublicModelViewV2.vue';
import PublicModelsCompareView from '../views/PublicModelsCompareView.vue';
import DatenschutzView from '../views/DatenschutzView.vue';
import ImpressumView from '../views/ImpressumView.vue';
import AGBView from '../views/AGBView.vue';
import TermsView from '../views/TermsView.vue';
import NotFoundView from '../views/NotFoundView.vue';
import CoinHistoryView from '../views/CoinHistoryView.vue';
import WallboxSetupView from '../views/WallboxSetupView.vue';
import ImportsView from '../views/ImportsView.vue';
import ForgotPasswordView from '../views/ForgotPasswordView.vue';
import ResetPasswordView from '../views/ResetPasswordView.vue';
import AdminView from '../views/AdminView.vue';
import LeaderboardView from '../views/LeaderboardView.vue';
import TaxExportView from '../views/TaxExportView.vue';
import SurveyView from '../views/SurveyView.vue';
import UpgradeView from '../views/UpgradeView.vue';
import UpgradeSuccessView from '../views/UpgradeSuccessView.vue';
import UpgradeCancelView from '../views/UpgradeCancelView.vue';

const router = createRouter({
    history: createWebHistory(import.meta.env.BASE_URL),
    scrollBehavior: () => ({ top: 0 }),
    routes: [
        {
            path: '/landing',
            name: 'landing-preview',
            component: LandingPageView,
        },
        {
            path: '/start/:section?',
            name: 'landing-v2',
            component: LandingPageV2View,
        },
        {
            path: '/',
            name: 'landing',
            component: LandingPageView,
            beforeEnter: () => {
                const authStore = useAuthStore();
                if (authStore.isAuthenticated()) {
                    return '/dashboard';
                }
                // First visit without saved locale preference → detect browser language
                // Default to English for all non-German browsers (NO, SE, DK, NL, BE, GB, etc.)
                if (!localStorage.getItem('ev-locale')) {
                    const browserLang = navigator.language ?? '';
                    if (!browserLang.toLowerCase().startsWith('de')) {
                        return '/en';
                    }
                }
            }
            // public landing page, but redirects to /dashboard if authenticated
        },
        {
            path: '/erfassen',
            name: 'log-form',
            component: LogFormView,
            meta: { requiresAuth: true }
        },
        {
            path: '/dashboard',
            name: 'statistics',
            component: DashboardView,
            meta: { requiresAuth: true }
        },
        {
            path: '/statistics',
            redirect: '/dashboard'
            // Legacy redirect: /statistics → /dashboard
        },
        {
            path: '/cars',
            name: 'cars',
            component: CarManagementView,
            meta: { requiresAuth: true }
        },
        {
            path: '/settings',
            name: 'settings',
            component: SettingsView,
            meta: { requiresAuth: true }
        },
        {
            path: '/coins/history',
            name: 'coin-history',
            component: CoinHistoryView,
            meta: { requiresAuth: true }
        },
        {
            path: '/wallbox',
            name: 'wallbox-setup',
            component: WallboxSetupView,
            meta: { requiresAuth: true }
        },
        {
            path: '/imports',
            name: 'imports',
            component: ImportsView,
            meta: { requiresAuth: true }
        },
        {
            path: '/upgrade',
            name: 'upgrade',
            component: UpgradeView,
            meta: { requiresAuth: true }
        },
        {
            path: '/upgrade/success',
            name: 'upgrade-success',
            component: UpgradeSuccessView,
            meta: { requiresAuth: true }
        },
        {
            path: '/upgrade/cancel',
            name: 'upgrade-cancel',
            component: UpgradeCancelView,
            meta: { requiresAuth: true }
        },
        {
            path: '/tax-export',
            name: 'tax-export',
            component: TaxExportView,
            meta: { requiresAuth: true }
        },
        {
            path: '/umfrage/:slug',
            name: 'survey',
            component: SurveyView,
            meta: { requiresAuth: true }
        },
        {
            path: '/login',
            name: 'login',
            component: LoginView,
            meta: { guestOnly: true }
        },
        {
            path: '/forgot-password',
            name: 'forgot-password',
            component: ForgotPasswordView,
            meta: { guestOnly: true }
        },
        {
            path: '/reset-password',
            name: 'reset-password',
            component: ResetPasswordView
            // no auth guard — user is logged out when they click the email link
        },
        {
            path: '/register',
            name: 'register',
            component: RegisterView,
            meta: { guestOnly: true }
        },
        {
            path: '/verify-email',
            name: 'verify-email',
            component: VerifyEmailView
            // no auth guard - accessible to everyone
        },
        {
            path: '/auth/callback',
            name: 'oauth2-redirect',
            component: OAuth2RedirectHandler
        },
        {
            path: '/modelle',
            name: 'public-models-list',
            component: PublicModelsListView,
            beforeEnter: () => {
                if (!localStorage.getItem('ev-locale')) {
                    const browserLang = navigator.language ?? '';
                    if (!browserLang.toLowerCase().startsWith('de')) {
                        return '/en/models';
                    }
                }
            }
            // no auth guard - public page for SEO
        },
        {
            path: '/modelle/vergleich',
            name: 'public-models-compare',
            component: PublicModelsCompareView
            // must be before :brand to avoid "vergleich" matching as a brand name
        },
        {
            path: '/modelle/:brand',
            name: 'public-brand',
            component: PublicBrandView,
            beforeEnter: (to) => {
                if (!localStorage.getItem('ev-locale')) {
                    const browserLang = navigator.language ?? '';
                    if (!browserLang.toLowerCase().startsWith('de')) {
                        return `/en/models/${to.params.brand}`;
                    }
                }
            }
        },
        {
            path: '/modelle/:brand/:model',
            name: 'public-model',
            component: PublicModelView,
            beforeEnter: (to) => {
                if (!localStorage.getItem('ev-locale')) {
                    const browserLang = navigator.language ?? '';
                    if (!browserLang.toLowerCase().startsWith('de')) {
                        return `/en/models/${to.params.brand}/${to.params.model}`;
                    }
                }
            }
            // no auth guard - public page for SEO
        },
        // EN auth routes
        {
            path: '/en/login',
            name: 'login-en',
            component: LoginView,
            meta: { locale: 'en', guestOnly: true }
        },
        {
            path: '/en/register',
            name: 'register-en',
            component: RegisterView,
            meta: { locale: 'en', guestOnly: true }
        },
        {
            path: '/en/forgot-password',
            name: 'forgot-password-en',
            component: ForgotPasswordView,
            meta: { locale: 'en', guestOnly: true }
        },
        // EN public routes
        {
            path: '/en',
            name: 'landing-en',
            component: LandingPageView,
            meta: { locale: 'en' },
            beforeEnter: () => {
                const authStore = useAuthStore();
                if (authStore.isAuthenticated()) {
                    return '/dashboard';
                }
            }
        },
        {
            path: '/en/models',
            name: 'public-models-list-en',
            component: PublicModelsListView,
            meta: { locale: 'en' }
        },
        {
            path: '/en/models/compare',
            name: 'public-models-compare-en',
            component: PublicModelsCompareView,
            meta: { locale: 'en' }
        },
        {
            path: '/en/models/:brand',
            name: 'public-brand-en',
            component: PublicBrandView,
            meta: { locale: 'en' }
        },
        {
            path: '/en/models/:brand/:model',
            name: 'public-model-en',
            component: PublicModelView,
            meta: { locale: 'en' }
        },
        // GB routes (en locale, GB country → miles, GBP)
        {
            path: '/gb/models',
            name: 'public-models-list-gb',
            component: PublicModelsListView,
            meta: { locale: 'en', country: 'GB' }
        },
        {
            path: '/gb/models/:brand',
            name: 'public-brand-gb',
            component: PublicBrandView,
            meta: { locale: 'en', country: 'GB' }
        },
        {
            path: '/gb/models/:brand/:model',
            name: 'public-model-gb',
            component: PublicModelView,
            meta: { locale: 'en', country: 'GB' }
        },
        // US routes (en locale, US country → EPA ratings, miles, USD)
        {
            path: '/us/models',
            name: 'public-models-list-us',
            component: PublicModelsListView,
            meta: { locale: 'en', country: 'US' }
        },
        {
            path: '/us/models/:brand',
            name: 'public-brand-us',
            component: PublicBrandView,
            meta: { locale: 'en', country: 'US' }
        },
        {
            path: '/us/models/:brand/:model',
            name: 'public-model-us',
            component: PublicModelView,
            meta: { locale: 'en', country: 'US' }
        },
        // NO routes (nb locale, NO country → kWh/mil, NOK)
        {
            path: '/no/modeller',
            name: 'public-models-list-no',
            component: PublicModelsListView,
            meta: { locale: 'nb', country: 'NO' }
        },
        {
            path: '/no/modeller/:brand',
            name: 'public-brand-no',
            component: PublicBrandView,
            meta: { locale: 'nb', country: 'NO' }
        },
        {
            path: '/no/modeller/:brand/:model',
            name: 'public-model-no',
            component: PublicModelView,
            meta: { locale: 'nb', country: 'NO' }
        },
        // SE routes (sv locale, SE country → kWh/mil, SEK)
        {
            path: '/se/modeller',
            name: 'public-models-list-se',
            component: PublicModelsListView,
            meta: { locale: 'sv', country: 'SE' }
        },
        {
            path: '/se/modeller/:brand',
            name: 'public-brand-se',
            component: PublicBrandView,
            meta: { locale: 'sv', country: 'SE' }
        },
        {
            path: '/se/modeller/:brand/:model',
            name: 'public-model-se',
            component: PublicModelView,
            meta: { locale: 'sv', country: 'SE' }
        },
        {
            path: '/datenschutz',
            name: 'datenschutz',
            component: DatenschutzView
            // no auth guard - public page for legal info
        },
        {
            path: '/impressum',
            name: 'impressum',
            component: ImpressumView
            // no auth guard - public page for legal info
        },
        {
            path: '/agb',
            name: 'agb',
            component: AGBView
            // no auth guard - public page for legal info
        },
        {
            path: '/consumption-methodology',
            name: 'consumption-methodology',
            component: () => import('../views/ConsumptionMethodologyView.vue')
        },
        {
            path: '/terms',
            name: 'terms',
            component: TermsView
            // no auth guard - public page for legal info (legacy)
        },
        {
            path: '/leaderboard',
            name: 'leaderboard',
            component: LeaderboardView,
            meta: { requiresAuth: true }
        },
        {
            path: '/rankings',
            name: 'rankings',
            component: () => import('../views/PublicRankingsView.vue'),
            meta: { requiresAuth: false }
            // Dark page - kein Nav-Link, nur per direktem URL erreichbar
        },
        {
            path: '/admin',
            name: 'admin',
            component: AdminView,
            meta: { requiresAuth: true, requiresAdmin: true }
        },
        {
            path: '/:pathMatch(.*)*',
            name: 'not-found',
            component: NotFoundView
            // Catch-all route - must be last!
        }
    ]
});

router.beforeEach(async (to, _from) => {
    const authStore = useAuthStore();
    const countryStore = useCountryStore();

    // Set locale for routes with explicit locale signal (nb/sv need async lazy-loading).
    // Auth-required routes (dashboard, settings, ...) inherit the current locale.
    const explicitLocale = to.meta.locale as string | undefined
    if (explicitLocale !== undefined && isValidLocale(explicitLocale)) {
        await setLocale(explicitLocale);
        document.documentElement.lang = explicitLocale;
    }

    // Set preview country for market-specific public routes (in-memory only, no localStorage/API).
    // Cleared for all other routes so DE users keep their settings after visiting /us/models.
    const marketCountry = to.meta.country;
    if (marketCountry && isValidCountryCode(marketCountry)) {
        countryStore.setPreviewCountry(marketCountry);
    } else {
        countryStore.setPreviewCountry(null);
    }

    if (to.meta.requiresAuth) {
        if (!authStore.isAuthenticated()) {
            return `/login?redirect=${encodeURIComponent(to.fullPath)}`;
        }
        if (authStore.isExpired()) {
            authStore.logout(false);
            return '/login?reason=session-expired';
        }
        if (to.meta.requiresAdmin && !authStore.isAdmin) {
            return '/dashboard';
        }
    } else if (to.meta.guestOnly && authStore.isAuthenticated() && !authStore.isExpired() && !authStore.isDemoAccount) {
        return '/dashboard';
    }
});

export default router;
