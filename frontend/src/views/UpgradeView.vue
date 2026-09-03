<template>
    <div v-if="loading" class="py-6 md:py-12 px-4">
        <div class="max-w-6xl mx-auto bg-gray-50/85 dark:bg-gray-900/75 backdrop-blur-md rounded-sm p-4 md:p-8 shadow-[5px_5px_0_rgba(0,0,0,0.35)] dark:shadow-[5px_5px_0_rgba(255,255,255,0.35)] shadow-black/5 dark:shadow-black/40">
            <div class="text-center py-16 text-gray-500 dark:text-gray-400">{{ t('upgrade.loading') }}</div>
        </div>
    </div>

    <!-- Ohne Abo: die AutoSync-Erzählseite (Tesla-Fahrer werden darin zum Supporter geleitet). -->
    <AutoSyncPitch v-else-if="tier === 'NONE'" />

    <!-- Mit aktivem Abo: die Verwaltungsansicht (Aktiv-Banner + Verwalten-Button). -->
    <div v-else class="py-6 md:py-12 px-4">
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
import { type SubscriptionTier } from '../composables/useUpgradeTierState';
import PricingTiers from '../components/PricingTiers.vue';
import AutoSyncPitch from '../components/AutoSyncPitch.vue';

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
