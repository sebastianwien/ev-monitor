import { createRouter, createWebHistory } from 'vue-router';
import { useAuthStore } from '../stores/auth';
import { i18n } from '../i18n';
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

const router = createRouter({
    history: createWebHistory(import.meta.env.BASE_URL),
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
                if (!localStorage.getItem('ev-locale')) {
                    const browserLang = navigator.language ?? '';
                    if (browserLang.toLowerCase().startsWith('en')) {
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
                    if (browserLang.toLowerCase().startsWith('en')) {
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
                    if (browserLang.toLowerCase().startsWith('en')) {
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
                    if (browserLang.toLowerCase().startsWith('en')) {
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

router.beforeEach((to, _from) => {
    const authStore = useAuthStore();

    // Set locale only for routes with explicit locale signal.
    // Auth-required routes (dashboard, settings, ...) inherit the current locale.
    const explicitLocale = (to.meta.locale as string | undefined)
        ?? (to.path.startsWith('/en') ? 'en' : null);
    if (explicitLocale !== null) {
        i18n.global.locale.value = explicitLocale as 'de' | 'en';
        localStorage.setItem('ev-locale', explicitLocale);
    }

    if (to.meta.requiresAuth) {
        if (!authStore.isAuthenticated()) {
            return '/login';
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
