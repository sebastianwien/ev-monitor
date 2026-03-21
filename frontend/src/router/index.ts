import { createRouter, createWebHistory } from 'vue-router';
import { useAuthStore } from '../stores/auth';
import LandingPageView from '../views/LandingPageView.vue';
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
import PublicModelView from '../views/PublicModelView.vue';
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
import AdminImpersonateView from '../views/AdminImpersonateView.vue';
import LeaderboardView from '../views/LeaderboardView.vue';

const router = createRouter({
    history: createWebHistory(import.meta.env.BASE_URL),
    routes: [
        {
            path: '/landing',
            name: 'landing-preview',
            component: LandingPageView,
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
            component: PublicModelsListView
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
            component: PublicBrandView
        },
        {
            path: '/modelle/:brand/:model',
            name: 'public-model',
            component: PublicModelView
            // no auth guard - public page for SEO
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
            path: '/admin',
            name: 'admin-impersonate',
            component: AdminImpersonateView
            // no auth guard - secured by internal token in the form
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
    const isAuthenticated = authStore.isAuthenticated();

    if (to.meta.requiresAuth && !isAuthenticated) {
        return '/login';
    } else if (to.meta.guestOnly && isAuthenticated) {
        return '/dashboard';
    }
});

export default router;
