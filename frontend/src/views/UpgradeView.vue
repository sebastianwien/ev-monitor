<template>
    <div class="py-6 md:py-12 px-4">
        <div class="max-w-6xl mx-auto bg-gray-50/85 dark:bg-gray-900/75 backdrop-blur-md rounded-3xl p-4 md:p-8 shadow-xl shadow-black/5 dark:shadow-black/40">
            <div v-if="loading" class="text-center py-16 text-gray-500 dark:text-gray-400">{{ t('upgrade.loading') }}</div>

            <!-- 3-Tier Upgrade View -->
            <div v-else>
                <!-- Headline -->
                <div class="text-center mb-8">
                    <span class="inline-block text-[11px] font-bold bg-indigo-600 text-white px-3 py-1 rounded-full tracking-wide mb-3">EV MONITOR</span>
                    <h1 class="text-2xl md:text-4xl font-bold text-gray-900 dark:text-gray-100 mb-2">{{ t('upgrade.tier_headline') }}</h1>
                    <p class="text-gray-500 dark:text-gray-400 text-sm md:text-base">{{ t('upgrade.tier_subtitle') }}</p>
                </div>

                <!-- Active-Plan Banner -->
                <div v-if="tier !== 'NONE'" class="max-w-2xl mx-auto mb-6 flex flex-col sm:flex-row items-center justify-center gap-3 bg-green-50 dark:bg-green-900/20 border border-green-200 dark:border-green-800 rounded-xl px-4 py-3">
                    <div class="flex items-center gap-2">
                        <CheckCircleIcon class="w-5 h-5 text-green-600 dark:text-green-400 shrink-0" />
                        <p class="text-sm text-gray-700 dark:text-gray-200">{{ t('upgrade.tier_active_banner', { plan: activePlanName }) }}</p>
                    </div>
                    <button
                        @click="handleManageSubscription"
                        :disabled="portalLoading"
                        class="text-sm font-medium text-green-700 dark:text-green-400 hover:text-green-900 dark:hover:text-green-200 underline underline-offset-2 disabled:opacity-50"
                    >
                        {{ portalLoading ? '...' : t('upgrade.tier_active_manage') }}
                    </button>
                </div>

                <!-- Plan Toggle -->
                <div v-if="showPlanToggle" class="flex justify-center mb-6">
                    <div class="inline-flex bg-gray-100 dark:bg-gray-800 p-1 rounded-xl">
                        <button
                            @click="selectedPlan = 'monthly'"
                            :class="selectedPlan === 'monthly'
                                ? 'bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 shadow-sm'
                                : 'text-gray-600 dark:text-gray-400'"
                            class="px-4 py-2 rounded-lg text-sm font-medium transition-colors"
                        >{{ t('upgrade.tier_toggle_monthly') }}</button>
                        <button
                            @click="selectedPlan = 'yearly'"
                            :class="selectedPlan === 'yearly'
                                ? 'bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 shadow-sm'
                                : 'text-gray-600 dark:text-gray-400'"
                            class="px-4 py-2 rounded-lg text-sm font-medium transition-colors flex items-center gap-1.5"
                        >
                            {{ t('upgrade.tier_toggle_yearly') }}
                            <span class="text-[10px] bg-green-100 dark:bg-green-900/40 text-green-700 dark:text-green-400 px-1.5 py-0.5 rounded font-bold">{{ t('upgrade.tier_toggle_yearly_savings') }}</span>
                        </button>
                    </div>
                </div>

                <!-- 3 Tier Cards -->
                <div class="grid grid-cols-1 md:grid-cols-3 gap-4 md:gap-5">

                    <!-- FREE -->
                    <div class="bg-white dark:bg-gray-900 rounded-2xl border border-gray-200 dark:border-gray-700 shadow-sm dark:shadow-none p-6 flex flex-col">
                        <div class="mb-4">
                            <p class="text-xs font-bold text-gray-400 dark:text-gray-500 uppercase tracking-wider mb-1">{{ t('upgrade.tier_free_label') }}</p>
                            <h2 class="text-2xl font-bold text-gray-900 dark:text-gray-100">{{ t('upgrade.tier_free_title') }}</h2>
                            <p class="text-sm text-gray-500 dark:text-gray-400 mt-1">{{ t('upgrade.tier_free_subtitle') }}</p>
                        </div>
                        <div class="mb-5">
                            <p class="text-3xl font-bold text-gray-900 dark:text-gray-100">
                                {{ t('upgrade.tier_free_price') }}<span class="text-base font-normal text-gray-400 dark:text-gray-500"> {{ t('upgrade.tier_free_price_unit') }}</span>
                            </p>
                        </div>
                        <ul class="space-y-2.5 text-sm text-gray-700 dark:text-gray-300 mb-6 flex-1">
                            <li class="flex items-start gap-2"><span class="text-gray-400 mt-0.5">✓</span><span>{{ t('upgrade.tier_free_feat_manual') }}</span></li>
                            <li class="flex items-start gap-2"><span class="text-gray-400 mt-0.5">✓</span><span>{{ t('upgrade.tier_free_feat_xpeng') }}</span></li>
                            <li class="flex items-start gap-2"><span class="text-gray-400 mt-0.5">✓</span><span>{{ t('upgrade.tier_free_feat_imports') }}</span></li>
                            <li class="flex items-start gap-2"><span class="text-gray-400 mt-0.5">✓</span><span>{{ t('upgrade.tier_free_feat_api') }}</span></li>
                            <li class="flex items-start gap-2"><span class="text-gray-400 mt-0.5">✓</span><span>{{ t('upgrade.tier_free_feat_stats') }}</span></li>
                            <li class="flex items-start gap-2"><span class="text-gray-400 mt-0.5">✓</span><span>{{ t('upgrade.tier_free_feat_soh') }}</span></li>
                            <li class="flex items-start gap-2"><span class="text-gray-400 mt-0.5">✓</span><span>{{ t('upgrade.tier_free_feat_cars') }}</span></li>
                        </ul>
                        <button
                            disabled
                            class="w-full bg-gray-100 dark:bg-gray-800 text-gray-700 dark:text-gray-300 font-semibold py-3 rounded-xl text-sm cursor-default"
                        >
                            {{ tier === 'NONE' ? t('upgrade.tier_free_cta_current') : t('upgrade.tier_free_cta_included') }}
                        </button>
                    </div>

                    <!-- AUTOSYNC -->
                    <div
                        :class="isAutoSyncActive
                            ? 'border-green-600 dark:border-green-500 shadow-xl shadow-green-500/10'
                            : tier === 'NONE'
                                ? 'border-green-500 dark:border-green-400 shadow-xl shadow-green-500/10 md:-mt-4'
                                : 'border-gray-200 dark:border-gray-700'"
                        class="bg-white dark:bg-gray-900 rounded-2xl border-2 p-6 flex flex-col relative"
                    >
                        <span v-if="tier === 'NONE'" class="absolute -top-3 left-1/2 -translate-x-1/2 text-[10px] font-bold bg-green-600 dark:bg-green-500 text-white px-3 py-1 rounded-full tracking-wider whitespace-nowrap">{{ t('upgrade.tier_badge_recommended') }}</span>
                        <div class="mb-4">
                            <p class="text-xs font-bold text-green-600 dark:text-green-400 uppercase tracking-wider mb-1">{{ t('upgrade.tier_autosync_label') }}</p>
                            <h2 class="text-2xl font-bold text-gray-900 dark:text-gray-100">{{ t('upgrade.tier_autosync_title') }}</h2>
                            <p class="text-sm text-gray-500 dark:text-gray-400 mt-1">{{ t('upgrade.tier_autosync_subtitle') }}</p>
                        </div>
                        <div class="mb-5">
                            <p class="text-3xl font-bold text-gray-900 dark:text-gray-100">
                                <template v-if="selectedPlan === 'yearly' && tier === 'NONE'">{{ pricing.yearly }}<span class="text-base font-normal text-gray-400 dark:text-gray-500"> / Jahr</span></template>
                                <template v-else>{{ pricing.monthly }}<span class="text-base font-normal text-gray-400 dark:text-gray-500"> {{ t('upgrade.tier_autosync_price_unit') }}</span></template>
                            </p>
                            <p class="text-xs text-green-600 dark:text-green-400 font-medium mt-0.5">{{ t('upgrade.tier_autosync_yearly_hint', { yearly: pricing.yearly }) }}</p>
                        </div>
                        <ul class="space-y-2.5 text-sm text-gray-700 dark:text-gray-300 mb-6 flex-1">
                            <li class="flex items-start gap-2"><span class="text-green-600 dark:text-green-400 mt-0.5">✓</span><span>{{ t('upgrade.tier_autosync_feat_tesla') }}</span></li>
                            <li class="flex items-start gap-2"><span class="text-green-600 dark:text-green-400 mt-0.5">✓</span><span>{{ t('upgrade.tier_autosync_feat_smartcar') }}</span></li>
                            <li class="flex items-start gap-2"><span class="text-green-600 dark:text-green-400 mt-0.5">✓</span><span>{{ t('upgrade.tier_autosync_feat_connection') }}</span></li>
                            <li class="flex items-start gap-2 text-gray-500 dark:text-gray-400"><span class="mt-0.5">+</span><span><em>{{ t('upgrade.tier_autosync_feat_inherits') }}</em></span></li>
                        </ul>
                        <template v-if="tier === 'NONE'">
                            <button
                                @click="handleCheckout"
                                :disabled="checkoutLoading || !premiumEnabled"
                                class="w-full bg-green-600 hover:bg-green-700 dark:bg-green-500 dark:hover:bg-green-400 disabled:bg-gray-300 dark:disabled:bg-gray-600 text-white dark:text-gray-900 font-semibold py-3 rounded-xl text-sm shadow-[0_4px_0_0_#166534] dark:shadow-[0_4px_0_0_#064e3b] active:translate-y-1 active:shadow-none transition"
                            >
                                <span v-if="checkoutLoading">{{ t('upgrade.cta_loading') }}</span>
                                <span v-else-if="!premiumEnabled">{{ t('upgrade.cta_coming_soon') }}</span>
                                <span v-else>{{ t('upgrade.tier_autosync_cta_trial') }}</span>
                            </button>
                            <p class="text-[11px] text-gray-400 dark:text-gray-500 text-center mt-2">{{ t('upgrade.tier_autosync_disclaimer') }}</p>
                            <p v-if="checkoutError" class="text-xs text-red-600 dark:text-red-400 text-center mt-2">{{ checkoutError }}</p>
                        </template>
                        <button
                            v-else
                            disabled
                            class="w-full bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-400 font-semibold py-3 rounded-xl text-sm cursor-default flex items-center justify-center gap-1.5"
                        >
                            <CheckCircleIcon class="w-4 h-4" />
                            {{ isAutoSyncActive ? t('upgrade.tier_autosync_cta_active') : t('upgrade.tier_autosync_cta_included') }}
                        </button>
                    </div>

                    <!-- LIVE -->
                    <div
                        :class="isLiveActive
                            ? 'border-indigo-600 dark:border-indigo-400 shadow-xl shadow-indigo-500/10'
                            : 'border-indigo-300 dark:border-indigo-800'"
                        class="bg-white dark:bg-gray-900 rounded-2xl border-2 p-6 flex flex-col relative"
                    >
                        <span v-if="!isLiveActive" class="absolute -top-3 left-1/2 -translate-x-1/2 text-[10px] font-bold bg-indigo-600 dark:bg-indigo-500 text-white px-3 py-1 rounded-full tracking-wider whitespace-nowrap">{{ t('upgrade.tier_badge_live') }}</span>
                        <div class="mb-4">
                            <p class="text-xs font-bold text-indigo-600 dark:text-indigo-400 uppercase tracking-wider mb-1">{{ t('upgrade.tier_live_label') }}</p>
                            <h2 class="text-2xl font-bold text-gray-900 dark:text-gray-100">{{ t('upgrade.tier_live_card_title') }}</h2>
                            <p class="text-sm text-gray-500 dark:text-gray-400 mt-1">{{ t('upgrade.tier_live_card_subtitle') }}</p>
                        </div>
                        <div class="mb-5">
                            <p class="text-3xl font-bold text-gray-900 dark:text-gray-100">
                                <template v-if="selectedPlan === 'yearly' && tier === 'NONE'">{{ t('upgrade.live_price_yearly') }}<span class="text-base font-normal text-gray-400 dark:text-gray-500"> / Jahr</span></template>
                                <template v-else>{{ t('upgrade.live_price_monthly') }}<span class="text-base font-normal text-gray-400 dark:text-gray-500"> {{ t('upgrade.tier_live_price_unit') }}</span></template>
                            </p>
                            <p class="text-xs text-indigo-600 dark:text-indigo-400 font-medium mt-0.5">{{ t('upgrade.tier_live_yearly_hint') }}</p>
                        </div>
                        <ul class="space-y-2.5 text-sm text-gray-700 dark:text-gray-300 mb-6 flex-1">
                            <li class="flex items-start gap-2"><span class="text-indigo-600 dark:text-indigo-400 mt-0.5">✓</span><span>{{ t('upgrade.tier_live_feat_trip') }}</span></li>
                            <li class="flex items-start gap-2"><span class="text-indigo-600 dark:text-indigo-400 mt-0.5">✓</span><span>{{ t('upgrade.tier_live_feat_drain') }}</span></li>
                            <li class="flex items-start gap-2"><span class="text-indigo-600 dark:text-indigo-400 mt-0.5">✓</span><span>{{ t('upgrade.tier_live_feat_brands') }}</span></li>
                            <li class="flex items-start gap-2 text-gray-500 dark:text-gray-400"><span class="mt-0.5">+</span><span><em>{{ t('upgrade.tier_live_feat_inherits') }}</em></span></li>
                        </ul>
                        <template v-if="isLiveActive">
                            <button
                                disabled
                                class="w-full bg-indigo-100 dark:bg-indigo-900/30 text-indigo-700 dark:text-indigo-400 font-semibold py-3 rounded-xl text-sm cursor-default flex items-center justify-center gap-1.5"
                            >
                                <CheckCircleIcon class="w-4 h-4" />
                                {{ t('upgrade.tier_live_cta_active') }}
                            </button>
                        </template>
                        <template v-else>
                            <button
                                @click="handleLiveAction"
                                :disabled="liveLoading"
                                class="w-full bg-indigo-600 hover:bg-indigo-700 dark:bg-indigo-500 dark:hover:bg-indigo-400 text-white font-semibold py-3 rounded-xl text-sm shadow-[0_4px_0_0_#3730a3] dark:shadow-[0_4px_0_0_#312e81] active:translate-y-1 active:shadow-none transition disabled:opacity-60"
                            >
                                <span v-if="liveLoading">…</span>
                                <span v-else>{{ isLiveUpgrade ? t('upgrade.tier_live_cta_upgrade') : t('upgrade.tier_live_cta_activate') }}</span>
                            </button>
                            <p class="text-[11px] text-gray-400 dark:text-gray-500 text-center mt-2">{{ t('upgrade.tier_live_disclaimer') }}</p>
                            <p v-if="liveError" class="text-xs text-amber-600 dark:text-amber-400 text-center mt-2">{{ liveError }}</p>
                        </template>
                    </div>

                </div>

                <!-- Trust + Payments -->
                <div v-if="tier === 'NONE'" class="mt-8 text-center">
                    <p class="text-xs text-gray-400 dark:text-gray-500 mb-3">{{ t('upgrade.tier_trust_hint') }}</p>
                    <div class="flex flex-wrap justify-center gap-1.5">
                        <span class="text-[11px] text-gray-600 dark:text-gray-400 border border-gray-300 dark:border-gray-600 rounded px-2 py-0.5">Visa</span>
                        <span class="text-[11px] text-gray-600 dark:text-gray-400 border border-gray-300 dark:border-gray-600 rounded px-2 py-0.5">Mastercard</span>
                        <span class="text-[11px] text-gray-600 dark:text-gray-400 border border-gray-300 dark:border-gray-600 rounded px-2 py-0.5">Apple Pay</span>
                        <span class="text-[11px] text-gray-600 dark:text-gray-400 border border-gray-300 dark:border-gray-600 rounded px-2 py-0.5">Google Pay</span>
                        <span class="text-[11px] text-gray-600 dark:text-gray-400 border border-gray-300 dark:border-gray-600 rounded px-2 py-0.5">Amazon Pay</span>
                        <span class="text-[11px] text-gray-600 dark:text-gray-400 border border-gray-300 dark:border-gray-600 rounded px-2 py-0.5">Klarna</span>
                        <span class="text-[11px] text-gray-600 dark:text-gray-400 border border-gray-300 dark:border-gray-600 rounded px-2 py-0.5">PayPal</span>
                    </div>
                    <p class="text-xs text-gray-400 dark:text-gray-500 mt-3">
                        {{ t('upgrade.support_hint') }}
                        <a href="mailto:support@ev-monitor.net" class="underline hover:no-underline">support@ev-monitor.net</a>
                    </p>
                </div>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useI18n } from 'vue-i18n';
import { subscriptionService } from '../api/subscriptionService';
import { analytics } from '../services/analytics';
import { CheckCircleIcon } from '@heroicons/vue/24/solid';
import { useCountryStore } from '../stores/country';
import { useCarStore } from '../stores/car';
import { getPricing } from '../config/pricingConfig';

const { t } = useI18n();
const countryStore = useCountryStore();
const carStore = useCarStore();
const pricing = computed(() => getPricing(countryStore.country));

const userCarBrands = ref<string[]>([]);
const tier = ref<'NONE' | 'AUTOSYNC' | 'AUTOSYNC_LIVE'>('NONE');
const isLiveUpgrade = computed(() => tier.value === 'AUTOSYNC');

// Card CTA-States basierend auf aktuellem Tier
const isAutoSyncActive = computed(() => tier.value === 'AUTOSYNC');
const isLiveActive = computed(() => tier.value === 'AUTOSYNC_LIVE');
const showPlanToggle = computed(() => tier.value === 'NONE');
const activePlanName = computed(() =>
    tier.value === 'AUTOSYNC_LIVE' ? t('upgrade.tier_live_label')
    : tier.value === 'AUTOSYNC' ? t('upgrade.tier_autosync_label')
    : '');

const loading = ref(true);
const premiumEnabled = ref(false);
const selectedPlan = ref<'monthly' | 'yearly'>('yearly');
const checkoutLoading = ref(false);
const checkoutError = ref('');
const portalLoading = ref(false);

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
    try {
        const result = await subscriptionService.createPortalSession();
        window.location.href = result.portalUrl;
    } catch {
        portalLoading.value = false;
    }
}
</script>
