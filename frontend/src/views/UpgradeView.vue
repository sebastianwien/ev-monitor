<template>
    <div class="min-h-screen bg-gray-50 dark:bg-gray-900 py-10 px-4">
        <div class="max-w-lg mx-auto">
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
                    <p class="text-xs font-semibold text-gray-400 dark:text-gray-500 uppercase tracking-wide mb-4 text-center">{{ t('upgrade.how_title') }}</p>
                    <div class="flex items-start gap-1">
                        <div class="flex-1 text-center">
                            <div class="w-10 h-10 rounded-full bg-indigo-100 dark:bg-indigo-900/30 flex items-center justify-center mx-auto mb-2">
                                <svg class="w-5 h-5 text-indigo-600 dark:text-indigo-400" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor">
                                    <path stroke-linecap="round" stroke-linejoin="round" d="M13.19 8.688a4.5 4.5 0 0 1 1.242 7.244l-4.5 4.5a4.5 4.5 0 0 1-6.364-6.364l1.757-1.757m13.35-.622 1.757-1.757a4.5 4.5 0 0 0-6.364-6.364l-4.5 4.5a4.5 4.5 0 0 0 1.242 7.244" />
                                </svg>
                            </div>
                            <p class="text-xs font-medium text-gray-700 dark:text-gray-300">{{ t('upgrade.how_step1_title') }}</p>
                            <p class="text-xs text-gray-400 dark:text-gray-500 mt-0.5">{{ t('upgrade.how_step1_desc') }}</p>
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
                            <p class="text-xs font-medium text-gray-700 dark:text-gray-300">{{ t('upgrade.how_step2_title') }}</p>
                            <p class="text-xs text-gray-400 dark:text-gray-500 mt-0.5">{{ t('upgrade.how_step2_desc') }}</p>
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
                            <p class="text-xs font-medium text-gray-700 dark:text-gray-300">{{ t('upgrade.how_step3_title') }}</p>
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

                <!-- Pricing -->
                <div class="bg-white dark:bg-gray-800 rounded-2xl shadow-sm border border-gray-200 dark:border-gray-700 p-5">
                    <!-- Trial Hero + CTA -->
                    <div class="text-center mb-5">
                        <p class="text-2xl font-bold text-gray-900 dark:text-gray-100">{{ t('upgrade.trial_days') }}</p>
                    </div>

                    <button
                        @click="handleCheckout"
                        :disabled="checkoutLoading || !premiumEnabled"
                        class="w-full bg-indigo-600 hover:bg-indigo-700 disabled:bg-gray-300 dark:disabled:bg-gray-600 text-white font-semibold py-3.5 rounded-xl transition-colors mb-2"
                    >
                        <span v-if="checkoutLoading">{{ t('upgrade.cta_loading') }}</span>
                        <span v-else-if="!premiumEnabled">{{ t('upgrade.cta_coming_soon') }}</span>
                        <span v-else>{{ t('upgrade.cta') }}</span>
                    </button>

                    <p class="text-xs text-gray-400 dark:text-gray-500 text-center mb-5">
                        {{ selectedPlan === 'monthly' ? t('upgrade.trial_hint_monthly', { priceMonthly: t('upgrade.price_monthly') }) : t('upgrade.trial_hint_yearly', { priceYearly: t('upgrade.price_yearly') }) }}
                    </p>

                    <p v-if="checkoutError" class="mb-4 text-sm text-red-600 dark:text-red-400 text-center">{{ checkoutError }}</p>

                    <!-- Plan Toggle -->
                    <div class="border-t border-gray-100 dark:border-gray-700 pt-4">
                        <p class="text-xs text-gray-400 dark:text-gray-500 text-center mb-3">{{ t('upgrade.plan_after_trial') }}</p>
                        <div class="flex justify-center gap-2">
                            <button
                                @click="selectedPlan = 'monthly'"
                                :class="selectedPlan === 'monthly' ? 'bg-indigo-600 text-white' : 'bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-300'"
                                class="flex flex-col items-center px-4 py-2 rounded-lg font-medium text-sm transition-colors"
                            >
                                <span>{{ t('upgrade.plan_monthly') }}</span>
                                <span>{{ t('upgrade.price_monthly') }}</span>
                            </button>
                            <button
                                @click="selectedPlan = 'yearly'"
                                :class="selectedPlan === 'yearly' ? 'bg-indigo-600 text-white' : 'bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-300'"
                                class="flex flex-col items-center px-4 py-2 rounded-lg font-medium text-sm transition-colors"
                            >
                                <span>{{ t('upgrade.plan_yearly') }} · {{ t('upgrade.price_yearly') }}</span>
                                <span :class="selectedPlan === 'yearly' ? 'bg-white/20 text-white' : 'bg-orange-100 dark:bg-orange-900/40 text-orange-700 dark:text-orange-400'" class="text-xs px-1.5 py-0.5 rounded mt-1">{{ t('upgrade.plan_yearly_badge') }}</span>
                            </button>
                        </div>
                    </div>

                    <!-- Trust -->
                    <div class="mt-4 flex items-start gap-2 text-xs text-gray-400 dark:text-gray-500">
                        <svg class="w-4 h-4 shrink-0 mt-0.5 text-gray-300 dark:text-gray-600" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor">
                            <path stroke-linecap="round" stroke-linejoin="round" d="M16.5 10.5V6.75a4.5 4.5 0 1 0-9 0v3.75m-.75 11.25h10.5a2.25 2.25 0 0 0 2.25-2.25v-6.75a2.25 2.25 0 0 0-2.25-2.25H6.75a2.25 2.25 0 0 0-2.25 2.25v6.75a2.25 2.25 0 0 0 2.25 2.25Z" />
                        </svg>
                        <span>{{ t('upgrade.trust') }}</span>
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useI18n } from 'vue-i18n';
import { subscriptionService } from '../api/subscriptionService';
import { analytics } from '../services/analytics';

const { t } = useI18n();

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
        const status = await subscriptionService.getStatus();
        premiumEnabled.value = status.premiumEnabled;
        isPremium.value = status.isPremium;
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
</script>
