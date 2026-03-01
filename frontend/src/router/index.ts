import { createRouter, createWebHistory } from 'vue-router';
import { useAuthStore } from '../stores/auth';
import LandingPageView from '../views/LandingPageView.vue';
import LogFormView from '../views/LogFormView.vue';
import LoginView from '../views/LoginView.vue';
import RegisterView from '../views/RegisterView.vue';
import VerifyEmailView from '../views/VerifyEmailView.vue';
import OAuth2RedirectHandler from '../views/OAuth2RedirectHandler.vue';
import CarManagementView from '../views/CarManagementView.vue';
import StatisticsView from '../views/StatisticsView.vue';
import PublicModelsListView from '../views/PublicModelsListView.vue';
import PublicModelView from '../views/PublicModelView.vue';
import DatenschutzView from '../views/DatenschutzView.vue';
import ImpressumView from '../views/ImpressumView.vue';
import AGBView from '../views/AGBView.vue';
import TermsView from '../views/TermsView.vue';
import NotFoundView from '../views/NotFoundView.vue';

const router = createRouter({
    history: createWebHistory(import.meta.env.BASE_URL),
    routes: [
        {
            path: '/',
            name: 'landing',
            component: LandingPageView,
            beforeEnter: (_to, _from, next) => {
                const authStore = useAuthStore();
                if (authStore.isAuthenticated()) {
                    next('/statistics');
                } else {
                    next();
                }
            }
            // public landing page, but redirects to /statistics if authenticated
        },
        {
            path: '/erfassen',
            name: 'log-form',
            component: LogFormView,
            meta: { requiresAuth: true }
        },
        {
            path: '/dashboard',
            redirect: '/statistics'
            // Legacy redirect: /dashboard → /statistics
        },
        {
            path: '/cars',
            name: 'cars',
            component: CarManagementView,
            meta: { requiresAuth: true }
        },
        {
            path: '/statistics',
            name: 'statistics',
            component: StatisticsView,
            meta: { requiresAuth: true }
        },
        {
            path: '/login',
            name: 'login',
            component: LoginView,
            meta: { guestOnly: true }
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
            path: '/oauth2/redirect',
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
            path: '/:pathMatch(.*)*',
            name: 'not-found',
            component: NotFoundView
            // Catch-all route - must be last!
        }
    ]
});

router.beforeEach((to, _from, next) => {
    const authStore = useAuthStore();
    const isAuthenticated = authStore.isAuthenticated();

    if (to.meta.requiresAuth && !isAuthenticated) {
        next('/login');
    } else if (to.meta.guestOnly && isAuthenticated) {
        next('/statistics');
    } else {
        next();
    }
});

export default router;
