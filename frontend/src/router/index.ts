import { createRouter, createWebHistory } from 'vue-router';
import { useAuthStore } from '../stores/auth';
import { useCountryStore } from '../stores/country';
import { setLocale, isValidLocale } from '../i18n';
import { slideDirection } from '../composables/useWorkspaceTransition';
import { installPageTransitions, isModelsRoute, modelsScrollPosition } from './pageTransitions';
import type { CountryCode } from '../config/unitSystems';
import { isValidCountryCode } from '../config/unitSystems';
import { purchasesAvailable } from '../utils/iapPolicy';

declare module 'vue-router' {
    interface RouteMeta {
        requiresAuth?: boolean
        requiresAdmin?: boolean
        guestOnly?: boolean
        /** In der nativen App gesperrt (Kauf-/Upgrade-Seiten, Apple Guideline 3.1.1). */
        hideOnNative?: boolean
        locale?: string
        country?: CountryCode
        /** Handy: ruhige CI-Fläche statt Wallpaper (Seiten im App-CI mit Boxen, docs/design/ci.md). */
        plainBackground?: boolean
        /** Eigener öffentlicher Kopf statt App-Chrome (Modellseiten fest, Bereiche der App je nach Story-Opt-in). */
        publicNav?: boolean
        /** Seite des eingeloggten Bereichs, die im neuen Design im Gerüst WorkspaceFrame steht (siehe designSwitch). */
        workspaceFrame?: boolean
    }
}
import LandingPageView from '../views/LandingPageView.vue';
import LandingPageV2View from '../views/LandingPageV2View.vue';
import LogFormView from '../views/LogFormView.vue';
import LoginView from '../views/LoginView.vue';
import RegisterView from '../views/RegisterView.vue';
import VerifyEmailView from '../views/VerifyEmailView.vue';
import OAuth2RedirectHandler from '../views/OAuth2RedirectHandler.vue';
import CarContextLayout from '../layouts/CarContextLayout.vue';
import { isStoryOptIn } from '../composables/useStoryOptIn';
import { applyDesignSwitch } from './designSwitch';
import CarsLayout from '../layouts/CarsLayout.vue';
import SettingsView from '../views/SettingsView.vue';
import PublicModelsListEntry from '../views/PublicModelsListEntry.vue';
import PublicBrandView from '../views/PublicBrandView.vue';
import PublicModelView from '../views/PublicModelViewV2.vue';
import PublicModelsCompareView from '../views/PublicModelsCompareView.vue';
import DatenschutzView from '../views/DatenschutzView.vue';
import ImpressumView from '../views/ImpressumView.vue';
import AGBView from '../views/AGBView.vue';
import NotFoundView from '../views/NotFoundView.vue';
import CoinHistoryView from '../views/CoinHistoryView.vue';
import WallboxSetupView from '../views/WallboxSetupView.vue';
import ImportsView from '../views/ImportsView.vue';
import ForgotPasswordView from '../views/ForgotPasswordView.vue';
import ResetPasswordView from '../views/ResetPasswordView.vue';
import LeaderboardView from '../views/LeaderboardView.vue';
import TaxExportView from '../views/TaxExportView.vue';
import SurveyView from '../views/SurveyView.vue';
import UpgradeView from '../views/UpgradeView.vue';
import SupporterView from '../views/SupporterView.vue';
import UpgradeSuccessView from '../views/UpgradeSuccessView.vue';
import UpgradeCancelView from '../views/UpgradeCancelView.vue';
import { onUpsellNavigation } from '../services/upsellSource'

const router = createRouter({
    history: createWebHistory(import.meta.env.BASE_URL),
    scrollBehavior: (to, from, savedPosition) => isModelsRoute(to.name) ? modelsScrollPosition(to, from, savedPosition) : { top: 0 },
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
            }
            // public landing page, but redirects to /dashboard if authenticated
        },
        {
            path: '/erfassen',
            name: 'log-form',
            component: LogFormView,
            // Mobile: Vollbild ohne Bottom-Nav und Ticker - der Wizard ist eine geschlossene Aufgabe
            meta: { requiresAuth: true, mobileFullscreen: true }
        },
        {
            path: '/dashboard',
            name: 'statistics',
            component: CarContextLayout,
            meta: { requiresAuth: true }
        },
        {
            path: '/story',
            name: 'story',
            // Neues Design: Uebersicht im CarContextLayout (WorkspaceFrame statt App-Chrome); mobil ruhige Flaeche
            component: CarContextLayout,
            meta: { requiresAuth: true, publicNav: true, plainBackground: true, workspaceFrame: true }
        },
        {
            path: '/logs',
            name: 'logs',
            component: CarContextLayout,
            // App-CI: mobil ruhige Flaeche; publicNav (Kopf des neuen Designs) setzt der Guard je nach Story-Opt-in
            meta: { requiresAuth: true, plainBackground: true, workspaceFrame: true }
        },
        {
            path: '/statistics',
            redirect: '/dashboard'
            // Legacy redirect: /statistics → /dashboard
        },
        {
            path: '/cars',
            name: 'cars',
            component: CarsLayout,
            // App-CI: mobil ruhige Flaeche; publicNav setzt der Guard je nach Story-Opt-in
            meta: { requiresAuth: true, plainBackground: true, workspaceFrame: true }
        },
        {
            path: '/charging-providers',
            name: 'charging-providers',
            component: CarsLayout,
            // App-CI: mobil ruhige Flaeche; publicNav setzt der Guard je nach Story-Opt-in
            meta: { requiresAuth: true, plainBackground: true, workspaceFrame: true }
        },
        {
            path: '/settings',
            name: 'settings',
            component: SettingsView,
            meta: { requiresAuth: true, plainBackground: true, workspaceFrame: true }
        },
        {
            path: '/coins/history',
            name: 'coin-history',
            component: CoinHistoryView,
            meta: { requiresAuth: true, plainBackground: true, workspaceFrame: true }
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
            meta: { requiresAuth: true, plainBackground: true, workspaceFrame: true }
        },
        {
            path: '/live',
            redirect: '/dashboard'
        },
        {
            path: '/upgrade',
            name: 'upgrade',
            component: UpgradeView,
            meta: { requiresAuth: true, hideOnNative: true }
        },
        {
            path: '/supporter',
            name: 'supporter',
            component: SupporterView,
            meta: { requiresAuth: true, hideOnNative: true }
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
            path: '/cars/:carId/bewerten',
            name: 'car-rating',
            component: () => import('../views/CarRatingView.vue'),
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
            meta: { publicNav: true },
            name: 'public-models-list',
            component: PublicModelsListEntry,
            // no auth guard - public page for SEO
        },
        {
            path: '/modelle/vergleich',
            name: 'public-models-compare',
            component: PublicModelsCompareView,
            meta: { publicNav: true }
            // must be before :brand to avoid "vergleich" matching as a brand name
        },
        {
            path: '/modelle/:brand',
            name: 'public-brand',
            component: PublicBrandView,
            meta: { publicNav: true },
        },
        {
            path: '/modelle/:brand/:model',
            meta: { publicNav: true },
            name: 'public-model',
            component: PublicModelView,
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
            component: PublicModelsListEntry,
            meta: { locale: 'en', publicNav: true }
        },
        {
            path: '/en/models/compare',
            name: 'public-models-compare-en',
            component: PublicModelsCompareView,
            meta: { locale: 'en', publicNav: true }
        },
        {
            path: '/en/models/:brand',
            name: 'public-brand-en',
            component: PublicBrandView,
            meta: { locale: 'en', publicNav: true }
        },
        {
            path: '/en/models/:brand/:model',
            name: 'public-model-en',
            component: PublicModelView,
            meta: { locale: 'en', publicNav: true }
        },
        // GB routes (en locale, GB country → miles, GBP)
        {
            path: '/gb/models',
            name: 'public-models-list-gb',
            component: PublicModelsListEntry,
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
        // US market retired: nginx answers /us/models* with 301 to /en/models*.
        // Client-side fallback for in-app navigation and old bookmarks in the SPA.
        {
            path: '/us/models/:rest(.*)*',
            redirect: to => `/en/models${to.fullPath.slice('/us/models'.length)}`
        },
        // NO routes (nb locale, NO country → kWh/mil, NOK)
        {
            path: '/no/modeller',
            name: 'public-models-list-no',
            component: PublicModelsListEntry,
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
            component: PublicModelsListEntry,
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
            // Oeffentliche Preis-/Plan-Seite (Marketing). CTAs fuehren zur Registrierung,
            // kein externer Kaufpfad -> Apple 3.1.1-konform, daher kein hideOnNative.
            // Die transaktionale Seite bleibt /upgrade (requiresAuth).
            path: '/preise',
            alias: '/pricing',
            name: 'pricing',
            component: () => import('../views/PricingView.vue')
        },
        {
            path: '/stories',
            name: 'stories-list',
            component: () => import('../views/StoriesListView.vue')
            // public - published user trip stories + own story management when logged in
        },
        {
            path: '/stories/edit/:id',
            name: 'story-editor',
            component: () => import('../views/StoryEditorView.vue'),
            meta: { requiresAuth: true }
        },
        {
            path: '/stories/:slug',
            name: 'story-public',
            component: () => import('../views/PublicStoryView.vue')
            // public SEO page for a single published story
        },
        {
            path: '/fahrzeug/:token',
            name: 'public-car',
            component: () => import('../views/PublicCarView.vue')
            // public - ein vom Besitzer freigegebenes Fahrzeug, noindex (siehe View)
        },
        {
            path: '/ladekurve/:token',
            name: 'public-power-curve',
            component: () => import('../views/PublicPowerCurveView.vue')
            // public - eine vom Besitzer freigegebene Ladekurve, noindex (siehe View)
        },
        {
            path: '/blog',
            name: 'blog-list',
            component: () => import('../views/BlogListView.vue')
            // public, German-only for Phase 1
        },
        {
            path: '/blog/:slug',
            name: 'blog-post',
            component: () => import('../views/BlogPostView.vue')
            // public, German-only for Phase 1
        },
        {
            path: '/terms',
            redirect: '/agb'
            // Legacy-URL: AGB liegen kanonisch unter /agb (Footer-Link), /terms leitet dorthin um
        },
        {
            path: '/leaderboard',
            name: 'leaderboard',
            component: LeaderboardView,
            meta: { requiresAuth: true, plainBackground: true, workspaceFrame: true }
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
            component: () => import('../views/AdminView.vue'),
            meta: { requiresAuth: true, requiresAdmin: true }
        },
        { path: '/admin/webhooks', redirect: { path: '/admin', query: { tab: 'webhooks' } } },
        { path: '/admin/umfragen', redirect: { path: '/admin', query: { tab: 'surveys' } } },
        {
            path: '/:pathMatch(.*)*',
            name: 'not-found',
            component: NotFoundView
            // Catch-all route - must be last!
        }
    ]
});

// Wechsel innerhalb eines Tab-Paars (Dashboard <-> Log-Feed, Fahrzeuge <-> Ladekarten)
// bleibt in derselben Layout-Komponente - den Uebergang macht dort der SwipeTabPager.
// Ueber die Paar-Grenze hinweg (z.B. Log-Feed -> Ladekarten) wechselt die Komponente,
// und der Uebergang laeuft als Routen-Transition. Beide nutzen dieselbe Richtung und
// dieselben CSS-Klassen, damit sich jeder Tab-Klick gleich anfuehlt.
// Story-Dashboard (Experiment): wer es eingeschaltet hat, landet statt auf /dashboard dort.
router.beforeEach((to) => applyDesignSwitch(to, isStoryOptIn()));

router.beforeEach((to, from) => {
    to.meta.transition = slideDirection(from.path, to.path);
});

// Seitenwechsel zwischen den Modellseiten als View Transition
installPageTransitions(router);

// Aufrufe der Bezahlseiten mit Herkunft messen (Plausible upsell_viewed)
router.afterEach((to, from) => onUpsellNavigation(to, from));

// Kauf-/Upgrade-Seiten in der nativen App sperren (Apple Guideline 3.1.1).
// Backstop fuer den Fall, dass irgendwo ein Kauf-Link uebersehen wurde.
router.beforeEach((to) => {
    if (to.meta.hideOnNative && !purchasesAvailable()) {
        return '/dashboard';
    }
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
