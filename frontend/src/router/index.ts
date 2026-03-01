import { createRouter, createWebHistory } from 'vue-router';
import { useAuthStore } from '../stores/auth';
import DashboardView from '../views/DashboardView.vue';
import LoginView from '../views/LoginView.vue';
import RegisterView from '../views/RegisterView.vue';
import VerifyEmailView from '../views/VerifyEmailView.vue';
import OAuth2RedirectHandler from '../views/OAuth2RedirectHandler.vue';
import CarManagementView from '../views/CarManagementView.vue';
import StatisticsView from '../views/StatisticsView.vue';
import PublicModelView from '../views/PublicModelView.vue';
import TermsView from '../views/TermsView.vue';

const router = createRouter({
    history: createWebHistory(import.meta.env.BASE_URL),
    routes: [
        {
            path: '/',
            name: 'dashboard',
            component: DashboardView,
            meta: { requiresAuth: true }
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
            path: '/modelle/:brand/:model',
            name: 'public-model',
            component: PublicModelView
            // no auth guard - public page for SEO
        },
        {
            path: '/terms',
            name: 'terms',
            component: TermsView
            // no auth guard - public page for legal info
        }
    ]
});

router.beforeEach((to, _from, next) => {
    const authStore = useAuthStore();
    const isAuthenticated = authStore.isAuthenticated();

    if (to.meta.requiresAuth && !isAuthenticated) {
        next('/login');
    } else if (to.meta.guestOnly && isAuthenticated) {
        next('/');
    } else {
        next();
    }
});

export default router;
