<template>
    <div class="py-6 md:py-10 px-4">
        <div class="max-w-lg md:max-w-2xl mx-auto bg-gray-50 dark:bg-gray-900 rounded-3xl p-3">
            <div v-if="loading" class="text-center py-16 text-gray-500 dark:text-gray-400">{{ t('upgrade.loading') }}</div>

            <!-- Bereits Pro -->
            <div v-else-if="isPremium" class="bg-white dark:bg-gray-800 rounded-2xl shadow-sm border border-gray-200 dark:border-gray-700 p-8 text-center">
                <div class="w-16 h-16 bg-green-100 dark:bg-green-900/30 rounded-full flex items-center justify-center mx-auto mb-4">
                    <svg class="w-8 h-8 text-green-600 dark:text-green-400" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor">
                        <path stroke-linecap="round" stroke-linejoin="round" d="M9 12.75 11.25 15 15 9.75M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z" />
                    </svg>
                </div>
                <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100 mb-2">{{ t('upgrade.already_pro_title') }}</h1>
                <p class="text-gray-500 dark:text-gray-400 text-sm mb-6">{{ t('upgrade.already_pro_desc') }}</p>
                <router-link to="/imports" class="inline-flex items-center gap-2 bg-green-600 hover:bg-green-700 text-white font-medium px-5 py-2.5 rounded-xl transition-colors text-sm">
                    {{ t('upgrade.already_pro_cta') }}
                </router-link>
            </div>

            <!-- Kaufseite -->
            <div v-else>
                <!-- Badge + Headline -->
                <div class="text-center mb-6">
                    <span class="inline-block text-xs font-bold bg-indigo-600 text-white px-3 py-1 rounded-full tracking-wide mb-4">{{ t('upgrade.badge') }}</span>
                    <h1 class="text-3xl font-bold text-gray-900 dark:text-gray-100 mb-2">{{ t('upgrade.headline') }}</h1>
                    <p class="text-gray-500 dark:text-gray-400 text-sm">{{ t('upgrade.subtitle') }}</p>
                </div>

                <!-- Wie es funktioniert -->
                <div class="bg-white dark:bg-gray-800 rounded-2xl shadow-sm border border-gray-200 dark:border-gray-700 p-5 mb-4">
                    <div class="flex items-start gap-1">
                        <div class="flex-1 text-center">
                            <div class="w-10 h-10 rounded-full bg-indigo-100 dark:bg-indigo-900/30 flex items-center justify-center mx-auto mb-2">
                                <svg class="w-5 h-5 text-indigo-600 dark:text-indigo-400" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor">
                                    <path stroke-linecap="round" stroke-linejoin="round" d="M13.19 8.688a4.5 4.5 0 0 1 1.242 7.244l-4.5 4.5a4.5 4.5 0 0 1-6.364-6.364l1.757-1.757m13.35-.622 1.757-1.757a4.5 4.5 0 0 0-6.364-6.364l-4.5 4.5a4.5 4.5 0 0 0 1.242 7.244" />
                                </svg>
                            </div>
                            <p class="text-sm font-medium text-gray-700 dark:text-gray-300">{{ t('upgrade.how_step1_title') }}</p>
                            <p class="hidden md:block text-xs text-gray-400 dark:text-gray-500 mt-0.5">{{ t('upgrade.how_step1_desc') }}</p>
                        </div>
                        <div class="shrink-0 pt-4">
                            <svg class="w-5 h-5 text-gray-300 dark:text-gray-600" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor">
                                <path stroke-linecap="round" stroke-linejoin="round" d="M13.5 4.5 21 12m0 0-7.5 7.5M21 12H3" />
                            </svg>
                        </div>
                        <div class="flex-1 text-center">
                            <div class="w-10 h-10 rounded-full bg-indigo-100 dark:bg-indigo-900/30 flex items-center justify-center mx-auto mb-2">
                                <svg class="w-5 h-5 text-indigo-600 dark:text-indigo-400" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor">
                                    <path stroke-linecap="round" stroke-linejoin="round" d="M3.75 13.5l10.5-11.25L12 10.5h8.25L9.75 21.75 12 13.5H3.75z" />
                                </svg>
                            </div>
                            <p class="text-sm font-medium text-gray-700 dark:text-gray-300">{{ t('upgrade.how_step2_title') }}</p>
                            <p class="hidden md:block text-xs text-gray-400 dark:text-gray-500 mt-0.5">{{ t('upgrade.how_step2_desc') }}</p>
                        </div>
                        <div class="shrink-0 pt-4">
                            <svg class="w-5 h-5 text-gray-300 dark:text-gray-600" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor">
                                <path stroke-linecap="round" stroke-linejoin="round" d="M13.5 4.5 21 12m0 0-7.5 7.5M21 12H3" />
                            </svg>
                        </div>
                        <div class="flex-1 text-center">
                            <div class="w-10 h-10 rounded-full bg-green-100 dark:bg-green-900/30 flex items-center justify-center mx-auto mb-2">
                                <svg class="w-5 h-5 text-green-600 dark:text-green-400" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor">
                                    <path stroke-linecap="round" stroke-linejoin="round" d="M9 12.75 11.25 15 15 9.75M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z" />
                                </svg>
                            </div>
                            <p class="text-sm font-medium text-gray-700 dark:text-gray-300">{{ t('upgrade.how_step3_title') }}</p>
                        </div>
                    </div>
                </div>

                <!-- Unterstützte Marken -->
                <div class="bg-white dark:bg-gray-800 rounded-2xl shadow-sm border border-gray-200 dark:border-gray-700 px-5 py-4 mb-4">
                    <p class="text-xs font-semibold text-gray-400 dark:text-gray-500 uppercase tracking-wide mb-3 text-center">{{ t('upgrade.brands_title') }}</p>
                    <div class="flex flex-wrap gap-1.5 justify-center">
                        <span v-for="brand in brands" :key="brand" class="text-xs bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-300 px-2.5 py-1 rounded-full">{{ brand }}</span>
                    </div>
                </div>

                <!-- FAQ: Wie funktioniert AutoSync (Smartcar)? -->
                <details v-if="showSmartcarFaq" class="group bg-white dark:bg-gray-800 rounded-2xl shadow-sm border border-gray-200 dark:border-gray-700 overflow-hidden mb-4">
                    <summary class="flex items-center justify-center gap-2 px-5 py-4 cursor-pointer list-none select-none hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors">
                        <p class="text-xs font-semibold text-gray-400 dark:text-gray-500 uppercase tracking-wide">{{ t('imports.smartcar_how_title') }}</p>
                        <ChevronDownIcon class="h-4 w-4 text-gray-400 shrink-0 transition-transform group-open:rotate-180" />
                    </summary>
                    <div class="border-t border-gray-100 dark:border-gray-700 px-5 pb-5 space-y-4">
                        <div v-for="i in 5" :key="i" class="pt-4">
                            <p v-if="i > 1" class="text-sm font-semibold text-gray-800 dark:text-gray-200">{{ t(`imports.smartcar_how_q${i}`) }}</p>
                            <p class="text-sm text-gray-500 dark:text-gray-400 mt-1">{{ t(`imports.smartcar_how_a${i}`) }}</p>
                        </div>
                    </div>
                </details>

                <!-- FAQ: Wie funktioniert AutoSync für Tesla? -->
                <details v-if="showTeslaFaq" class="group bg-white dark:bg-gray-800 rounded-2xl shadow-sm border border-gray-200 dark:border-gray-700 overflow-hidden mb-4">
                    <summary class="flex items-center justify-center gap-2 px-5 py-4 cursor-pointer list-none select-none hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors">
                        <p class="text-xs font-semibold text-gray-400 dark:text-gray-500 uppercase tracking-wide">{{ t('imports.tesla_how_title') }}</p>
                        <ChevronDownIcon class="h-4 w-4 text-gray-400 shrink-0 transition-transform group-open:rotate-180" />
                    </summary>
                    <div class="border-t border-gray-100 dark:border-gray-700 px-5 pb-5 space-y-4">
                        <div v-for="i in 5" :key="i" class="pt-4">
                            <p v-if="i > 1" class="text-sm font-semibold text-gray-800 dark:text-gray-200">{{ t(`imports.tesla_how_q${i}`) }}</p>
                            <p class="text-sm text-gray-500 dark:text-gray-400 mt-1" v-html="t(`imports.tesla_how_a${i}`)"></p>
                        </div>
                    </div>
                </details>

                <!-- Pricing -->
                <div class="bg-white dark:bg-gray-800 rounded-2xl shadow-sm border border-gray-200 dark:border-gray-700 p-5">
                    <!-- Trial Hero -->
                    <div class="text-center mb-4">
                        <p class="text-2xl font-bold text-gray-900 dark:text-gray-100">{{ t('upgrade.trial_days') }}</p>
                    </div>

                    <!-- Plan Toggle -->
                    <div class="mb-5">
                        <p class="text-xs text-gray-400 dark:text-gray-500 text-center mb-3">{{ t('upgrade.plan_after_trial') }}</p>
                        <div class="flex justify-center gap-2">
                            <button
                                @click="selectedPlan = 'monthly'"
                                :class="selectedPlan === 'monthly' ? 'bg-indigo-600 text-white' : 'bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-300'"
                                class="flex flex-col items-center px-4 py-2 rounded-lg font-medium text-sm transition-colors"
                            >
                                <span>{{ t('upgrade.plan_monthly') }}</span>
                                <span>{{ pricing.monthly }}</span>
                            </button>
                            <button
                                @click="selectedPlan = 'yearly'"
                                :class="selectedPlan === 'yearly' ? 'bg-indigo-600 text-white' : 'bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-300'"
                                class="flex flex-col items-center px-4 py-2 rounded-lg font-medium text-sm transition-colors"
                            >
                                <span>{{ t('upgrade.plan_yearly') }} · {{ pricing.yearly }}</span>
                                <span :class="selectedPlan === 'yearly' ? 'bg-white/20 text-white' : 'bg-green-100 dark:bg-green-900/40 text-green-700 dark:text-green-400'" class="text-xs px-1.5 py-0.5 rounded mt-1">{{ pricing.yearlySavings }}</span>
                            </button>
                        </div>
                    </div>

                    <div class="border-t border-gray-100 dark:border-gray-700 mb-5"></div>

                    <button
                        @click="handleCheckout"
                        :disabled="checkoutLoading || !premiumEnabled"
                        class="w-full bg-indigo-600 hover:bg-indigo-700 disabled:bg-gray-300 dark:disabled:bg-gray-600 text-white font-semibold py-3.5 rounded-xl transition-colors mb-4"
                    >
                        <span v-if="checkoutLoading">{{ t('upgrade.cta_loading') }}</span>
                        <span v-else-if="!premiumEnabled">{{ t('upgrade.cta_coming_soon') }}</span>
                        <span v-else>{{ t('upgrade.cta') }}</span>
                    </button>

                    <!-- Payment method trust indicators -->
                    <div class="flex items-center justify-center gap-1.5 mb-3 flex-wrap">
                        <span class="text-xs text-gray-600 dark:text-gray-300 border border-gray-300 dark:border-gray-500 rounded px-2 py-0.5">Visa</span>
                        <span class="text-xs text-gray-600 dark:text-gray-300 border border-gray-300 dark:border-gray-500 rounded px-2 py-0.5">Mastercard</span>
                        <span class="text-xs text-gray-600 dark:text-gray-300 border border-gray-300 dark:border-gray-500 rounded px-2 py-0.5">Apple Pay</span>
                        <span class="text-xs text-gray-600 dark:text-gray-300 border border-gray-300 dark:border-gray-500 rounded px-2 py-0.5">Google Pay</span>
                        <span class="text-xs text-gray-600 dark:text-gray-300 border border-gray-300 dark:border-gray-500 rounded px-2 py-0.5">Amazon Pay</span>
                        <span class="text-xs text-gray-600 dark:text-gray-300 border border-gray-300 dark:border-gray-500 rounded px-2 py-0.5">Klarna</span>
                        <span class="text-xs text-gray-600 dark:text-gray-300 border border-gray-300 dark:border-gray-500 rounded px-2 py-0.5">PayPal</span>
                    </div>

                    <p class="text-sm font-semibold text-gray-600 dark:text-gray-300 text-center mb-3">
                        {{ selectedPlan === 'monthly' ? t('upgrade.trial_hint_monthly', { priceMonthly: pricing.monthly }) : t('upgrade.trial_hint_yearly', { priceYearly: pricing.yearly }) }}
                    </p>

                    <div v-if="checkoutError" class="mb-4 text-center space-y-1">
                        <p class="text-sm text-red-600 dark:text-red-400">{{ checkoutError }}</p>
                        <p class="text-xs text-gray-400 dark:text-gray-500">
                            {{ t('upgrade.support_hint') }}
                            <a href="mailto:support@ev-monitor.net" class="underline hover:no-underline">support@ev-monitor.net</a>
                        </p>
                    </div>

                    <!-- Trust -->
                    <div class="mt-4 flex items-center justify-center gap-2 text-xs text-gray-400 dark:text-gray-500">
                        <svg class="w-4 h-4 shrink-0 text-gray-300 dark:text-gray-600" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor">
                            <path stroke-linecap="round" stroke-linejoin="round" d="M16.5 10.5V6.75a4.5 4.5 0 1 0-9 0v3.75m-.75 11.25h10.5a2.25 2.25 0 0 0 2.25-2.25v-6.75a2.25 2.25 0 0 0-2.25-2.25H6.75a2.25 2.25 0 0 0-2.25 2.25v6.75a2.25 2.25 0 0 0 2.25 2.25Z" />
                        </svg>
                        <span>{{ t('upgrade.trust') }}</span>
                    </div>
                    <p class="text-xs text-gray-400 dark:text-gray-500 text-center mt-2">
                        {{ t('upgrade.support_hint') }}
                        <a href="mailto:support@ev-monitor.net" class="underline hover:no-underline">support@ev-monitor.net</a>
                    </p>
                </div>

                <!-- Live-Card: only for users with at least one Tesla and not yet on Live tier -->
                <div v-if="showLiveBlock"
                     class="mt-4 rounded-2xl p-5 shadow-lg border border-indigo-500/40 relative bg-gradient-to-br from-indigo-600 via-indigo-700 to-purple-800">
                    <span class="absolute -top-2 right-4 text-[10px] font-bold bg-amber-400 text-amber-950 px-2 py-0.5 rounded-full">{{ t('upgrade.live_for_tesla_badge') }}</span>
                    <div class="flex items-baseline justify-between mb-1">
                        <h3 class="text-base font-bold text-white">{{ t('upgrade.live_title') }}</h3>
                        <p class="text-sm text-indigo-200">
                            <span class="text-white font-semibold">{{ t('upgrade.live_price_monthly') }}</span>
                            <span> / </span>
                            <span class="text-white font-semibold">{{ t('upgrade.live_price_yearly') }}</span>
                        </p>
                    </div>
                    <p class="text-xs text-indigo-200 mb-3">{{ t('upgrade.live_subtitle') }}</p>
                    <ul class="text-xs text-indigo-100 space-y-1 mb-4">
                        <li class="flex items-start gap-1.5"><span class="text-white">✓</span><span>{{ t('upgrade.live_features_intro') }}</span></li>
                        <li class="flex items-start gap-1.5"><span class="text-white">✓</span><strong>{{ t('upgrade.live_feature_trip') }}</strong></li>
                        <li class="flex items-start gap-1.5"><span class="text-white">✓</span><strong>{{ t('upgrade.live_feature_drain') }}</strong></li>
                    </ul>
                    <button
                        @click="handleLiveAction"
                        :disabled="liveLoading"
                        class="w-full bg-white text-indigo-700 font-semibold text-sm py-2.5 rounded-lg hover:bg-indigo-50 transition-colors disabled:opacity-60"
                    >
                        <span v-if="liveLoading">…</span>
                        <span v-else-if="isLiveUpgrade">{{ t('upgrade.live_upgrade_cta') }}</span>
                        <span v-else>{{ t('upgrade.live_buy_cta') }}</span>
                    </button>
                    <p class="text-[10px] text-indigo-200 text-center mt-2">{{ t('upgrade.live_tesla_required') }}</p>
                    <p v-if="liveError" class="text-xs text-amber-200 text-center mt-2">{{ liveError }}</p>
                </div>

                <!-- Hint for non-Tesla users so they know Live exists -->
                <p v-if="!hasTesla && tier === 'NONE'" class="text-[11px] text-gray-500 dark:text-gray-400 text-center mt-3">
                    {{ t('upgrade.live_tesla_hint_for_non_tesla') }}
                </p>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useI18n } from 'vue-i18n';
import { subscriptionService } from '../api/subscriptionService';
import { analytics } from '../services/analytics';
import { ChevronDownIcon } from '@heroicons/vue/24/outline';
import { useCountryStore } from '../stores/country';
import { useCarStore } from '../stores/car';
import { getPricing } from '../config/pricingConfig';

const { t } = useI18n();
const countryStore = useCountryStore();
const carStore = useCarStore();
const pricing = computed(() => getPricing(countryStore.country));

const userCarBrands = ref<string[]>([]);
const hasTesla = computed(() => userCarBrands.value.some(b => b === 'TESLA'));
const hasOnlyTesla = computed(() => userCarBrands.value.length > 0 && userCarBrands.value.every(b => b === 'TESLA'));
const hasNonTesla = computed(() => userCarBrands.value.some(b => b !== 'TESLA'));
const showTeslaFaq = computed(() => hasOnlyTesla.value || userCarBrands.value.length === 0);
const showSmartcarFaq = computed(() => hasNonTesla.value || userCarBrands.value.length === 0);
const tier = ref<'NONE' | 'AUTOSYNC' | 'AUTOSYNC_LIVE'>('NONE');
const showLiveBlock = computed(() => hasTesla.value && tier.value !== 'AUTOSYNC_LIVE');
const isLiveUpgrade = computed(() => tier.value === 'AUTOSYNC');

const brands = [
    'BMW', 'MINI', 'VW', 'Mercedes', 'Audi', 'Porsche', 'Skoda', 'SEAT', 'CUPRA', 'Opel',
    'Hyundai', 'Kia', 'Volvo', 'Polestar', 'Renault', 'Dacia', 'Nissan', 'Ford',
    'Fiat', 'Alfa Romeo', 'Peugeot', 'Citroën', 'Mazda', 'MG', 'BYD',
    'Jaguar', 'Land Rover', 'Tesla',
];

const loading = ref(true);
const premiumEnabled = ref(false);
const isPremium = ref(false);
const selectedPlan = ref<'monthly' | 'yearly'>('yearly');
const checkoutLoading = ref(false);
const checkoutError = ref('');

onMounted(async () => {
    try {
        const [status, cars] = await Promise.all([
            subscriptionService.getStatus(),
            carStore.getCars().catch(() => []),
        ]);
        premiumEnabled.value = status.premiumEnabled;
        isPremium.value = status.isPremium;
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
</script>
