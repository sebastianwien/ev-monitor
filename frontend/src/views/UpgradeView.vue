<template>
    <div class="py-6 md:py-12 px-4">
        <div v-if="loading" class="max-w-6xl mx-auto bg-gray-50/85 dark:bg-gray-900/75 backdrop-blur-md rounded-sm p-4 md:p-8 shadow-[5px_5px_0_rgba(0,0,0,0.35)] dark:shadow-[5px_5px_0_rgba(255,255,255,0.35)] shadow-black/5 dark:shadow-black/40">
            <div class="text-center py-16 text-gray-500 dark:text-gray-400">{{ t('upgrade.loading') }}</div>
        </div>
        <template v-else>
            <!-- Reine-Tesla-Fahrer brauchen kein AutoSync (Daten laufen gratis) - sie
                 gehoeren aufs Supporter-Pack. Statt sie durch die AutoSync-Preistabelle
                 zu schicken, ein klarer Umweg-Hinweis oben. -->
            <div
                v-if="showSupporterRedirect"
                class="max-w-6xl mx-auto mb-4 bg-amber-50 dark:bg-amber-900/20 border border-amber-200 dark:border-amber-700/40 rounded-sm p-4 md:p-5 flex flex-col sm:flex-row sm:items-center gap-4"
            >
                <div class="flex items-start gap-3 flex-1 min-w-0">
                    <HeartIcon class="w-5 h-5 text-amber-500 dark:text-amber-400 shrink-0 mt-0.5" />
                    <div class="min-w-0">
                        <p class="text-sm font-semibold text-gray-900 dark:text-gray-100">{{ t('upgrade.tesla_no_autosync_title') }}</p>
                        <p class="text-xs text-gray-500 dark:text-gray-400 mt-0.5">{{ t('upgrade.tesla_no_autosync_body') }}</p>
                    </div>
                </div>
                <router-link
                    to="/supporter"
                    class="shrink-0 w-full sm:w-auto inline-flex items-center justify-center gap-1.5 bg-amber-500 hover:bg-amber-600 dark:bg-amber-500 dark:hover:bg-amber-400 text-white font-semibold px-5 py-2.5 rounded-sm text-sm shadow-[0_4px_0_0_#b45309] dark:shadow-[0_4px_0_0_#92400e] active:translate-y-1 active:shadow-none transition"
                >
                    <HeartIcon class="w-4 h-4" />{{ t('upgrade.tesla_no_autosync_cta') }}
                </router-link>
            </div>
            <PricingTiers
                mode="account"
                :tier="tier"
                :premium-enabled="premiumEnabled"
                :pricing="pricing"
                v-model:selected-plan="selectedPlan"
                :show-tesla-only-features="showTeslaOnlyFeatures"
                :show-smartcar-faq="showSmartcarFaq"
                :checkout-loading="checkoutLoading"
                :checkout-error="checkoutError"
                :portal-loading="portalLoading"
                :portal-error="portalError"
                @checkout="handleCheckout"
                @manage="handleManageSubscription"
            />
        </template>
    </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useI18n } from 'vue-i18n';
import { HeartIcon } from '@heroicons/vue/24/outline';
import { subscriptionService } from '../api/subscriptionService';
import { analytics } from '../services/analytics';
import { useCountryStore } from '../stores/country';
import { useCarStore } from '../stores/car';
import { getPricing } from '../config/pricingConfig';
import { type SubscriptionTier } from '../composables/useUpgradeTierState';
import { hasOnlyFreeDataSourceCars } from '../composables/useUpsellTarget';
import PricingTiers from '../components/PricingTiers.vue';

const { t } = useI18n();
const countryStore = useCountryStore();
const carStore = useCarStore();
const pricing = computed(() => getPricing(countryStore.country));

const userCarBrands = ref<string[]>([]);
// Features wie Live-Ansicht und Ladekurven sind nur fuer Tesla verfuegbar
// (Smartcar liefert keine Power-Daten). Wenn der User schon mindestens ein
// Auto angelegt hat und keines davon ein Tesla ist, blende diese Bullets aus.
// Wer noch kein Auto hat, sieht alles - das ist Akquise-Modus.
const hasOnlyNonTeslaCars = computed(() =>
    userCarBrands.value.length > 0
    && !userCarBrands.value.some(b => b?.toUpperCase() === 'TESLA'));
const showTeslaOnlyFeatures = computed(() => !hasOnlyNonTeslaCars.value);

// Smartcar-FAQ nur fuer User mit Nicht-Tesla-Auto oder noch ganz ohne Auto
// (Akquise-Modus). Reine Tesla-Fahrer nutzen kein Smartcar - fuer sie waere
// die Erklaerung irrelevant.
const showSmartcarFaq = computed(() =>
    userCarBrands.value.length === 0
    || userCarBrands.value.some(b => b?.toUpperCase() !== 'TESLA'));

const tier = ref<SubscriptionTier>('NONE');

// Reine-Tesla-Fahrer (jedes Auto liefert seine Daten gratis) brauchen kein AutoSync -
// dann oben den Supporter-Umweg zeigen statt der Kauf-Tabelle. Sobald ein Auto ohne
// Gratis-Quelle dabei ist, ist AutoSync der richtige Weg und der Hinweis verschwindet.
const showSupporterRedirect = computed(() =>
    tier.value === 'NONE'
    && hasOnlyFreeDataSourceCars(userCarBrands.value.map(brand => ({ brand }))));

const loading = ref(true);
const premiumEnabled = ref(false);
const selectedPlan = ref<'monthly' | 'yearly'>('yearly');
const checkoutLoading = ref(false);
const checkoutError = ref('');
const portalLoading = ref(false);
const portalError = ref('');

onMounted(async () => {
    try {
        const [status, cars] = await Promise.all([
            subscriptionService.getStatus(),
            carStore.getCars().catch(() => []),
        ]);
        premiumEnabled.value = status.premiumEnabled;
        tier.value = status.tier ?? 'NONE';
        userCarBrands.value = cars.map(c => c.brand);
        analytics.trackUpgradePageViewed();
    } finally {
        loading.value = false;
    }
});

async function handleCheckout() {
    checkoutLoading.value = true;
    checkoutError.value = '';
    try {
        analytics.trackCheckoutStarted(selectedPlan.value);
        const result = await subscriptionService.createCheckoutSession(selectedPlan.value);
        window.location.href = result.checkoutUrl;
    } catch {
        checkoutError.value = t('upgrade.error');
        checkoutLoading.value = false;
    }
}

async function handleManageSubscription() {
    portalLoading.value = true;
    portalError.value = '';
    try {
        const result = await subscriptionService.createPortalSession();
        window.location.href = result.portalUrl;
    } catch {
        portalError.value = t('upgrade.tier_portal_error');
        portalLoading.value = false;
    }
}
</script>
