<template>
    <div class="py-6 md:py-12 px-4">
        <div v-if="loading" class="max-w-6xl mx-auto bg-gray-50/85 dark:bg-gray-900/75 backdrop-blur-md rounded-sm p-4 md:p-8 shadow-[5px_5px_0_rgba(0,0,0,0.35)] dark:shadow-[5px_5px_0_rgba(255,255,255,0.35)] shadow-black/5 dark:shadow-black/40">
            <div class="text-center py-16 text-gray-500 dark:text-gray-400">{{ t('upgrade.loading') }}</div>
        </div>
        <PricingTiers
            v-else
            mode="account"
            :tier="tier"
            :premium-enabled="premiumEnabled"
            :pricing="pricing"
            v-model:selected-plan="selectedPlan"
            :show-tesla-only-features="showTeslaOnlyFeatures"
            :show-smartcar-faq="showSmartcarFaq"
            :checkout-loading="checkoutLoading"
            :checkout-error="checkoutError"
            :live-loading="liveLoading"
            :live-error="liveError"
            :portal-loading="portalLoading"
            :portal-error="portalError"
            @checkout="handleCheckout"
            @live-action="handleLiveAction"
            @manage="handleManageSubscription"
        />
    </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useI18n } from 'vue-i18n';
import { subscriptionService } from '../api/subscriptionService';
import { analytics } from '../services/analytics';
import { useCountryStore } from '../stores/country';
import { useCarStore } from '../stores/car';
import { getPricing } from '../config/pricingConfig';
import { useUpgradeTierState, type SubscriptionTier } from '../composables/useUpgradeTierState';
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
// Nur isLiveUpgrade wird hier gebraucht (Handler-Logik); die uebrigen Tier-States
// leben in PricingTiers.
const { isLiveUpgrade } = useUpgradeTierState(tier);

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

const liveLoading = ref(false);
const liveError = ref('');

async function handleLiveAction() {
    liveLoading.value = true;
    liveError.value = '';
    try {
        if (isLiveUpgrade.value) {
            // Existing AutoSync subscriber → in-place upgrade via Stripe API.
            await subscriptionService.upgradeToLive();
            // Status will flip via webhook; reload status so the UI reflects it.
            const status = await subscriptionService.getStatus();
            tier.value = status.tier ?? 'NONE';
        } else {
            // No subscription yet → take user through Stripe Checkout for Live.
            const result = await subscriptionService.createCheckoutSession(selectedPlan.value, 'autosync_live');
            window.location.href = result.checkoutUrl;
            return; // navigation will tear down the page
        }
    } catch (e: unknown) {
        const err = e as { response?: { data?: { errorCode?: string } } };
        if (err.response?.data?.errorCode === 'tesla_required') {
            liveError.value = t('upgrade.live_tesla_required_error');
        } else {
            liveError.value = t('upgrade.error');
        }
    } finally {
        liveLoading.value = false;
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
