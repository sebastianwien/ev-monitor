<template>
    <div class="min-h-screen bg-gray-50 py-12 px-4">
        <div class="max-w-2xl mx-auto">
            <!-- Loading -->
            <div v-if="loading" class="text-center py-16 text-gray-500">Lädt...</div>

            <!-- Beta Banner (PREMIUM_ENABLED=false) -->
            <div v-else-if="!premiumEnabled" class="bg-white rounded-2xl shadow-sm border border-gray-200 p-8 text-center">
                <div class="w-16 h-16 bg-green-100 rounded-full flex items-center justify-center mx-auto mb-4">
                    <svg class="w-8 h-8 text-green-600" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor">
                        <path stroke-linecap="round" stroke-linejoin="round" d="M9.813 15.904 9 18.75l-.813-2.846a4.5 4.5 0 0 0-3.09-3.09L2.25 12l2.846-.813a4.5 4.5 0 0 0 3.09-3.09L9 5.25l.813 2.846a4.5 4.5 0 0 0 3.09 3.09L15.75 12l-2.846.813a4.5 4.5 0 0 0-3.09 3.09Z" />
                    </svg>
                </div>
                <h1 class="text-2xl font-bold text-gray-900 mb-3">Wallbox-Integration — Kostenlose Beta</h1>
                <p class="text-gray-600 mb-6">
                    Die Wallbox-Integration ist aktuell kostenlos für alle Beta-Nutzer verfügbar.
                    Du wirst rechtzeitig informiert, bevor wir ein Abo aktivieren.
                </p>
                <div class="bg-green-50 border border-green-200 rounded-xl p-4 text-sm text-green-800">
                    Kein Abo nötig — alle Features sind während der Beta gratis.
                </div>
                <div class="mt-8">
                    <router-link to="/wallbox" class="inline-flex items-center gap-2 bg-green-600 hover:bg-green-700 text-white font-medium px-6 py-3 rounded-xl transition-colors">
                        Zur Wallbox-Integration
                    </router-link>
                </div>
            </div>

            <!-- Already Premium -->
            <div v-else-if="isPremium" class="bg-white rounded-2xl shadow-sm border border-gray-200 p-8 text-center">
                <div class="w-16 h-16 bg-green-100 rounded-full flex items-center justify-center mx-auto mb-4">
                    <svg class="w-8 h-8 text-green-600" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor">
                        <path stroke-linecap="round" stroke-linejoin="round" d="M9 12.75 11.25 15 15 9.75M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z" />
                    </svg>
                </div>
                <h1 class="text-2xl font-bold text-gray-900 mb-3">Du bist bereits Premium</h1>
                <p class="text-gray-600 mb-6">Alle Premium-Features sind für dich freigeschaltet.</p>
                <router-link to="/wallbox" class="inline-flex items-center gap-2 bg-green-600 hover:bg-green-700 text-white font-medium px-6 py-3 rounded-xl transition-colors">
                    Zur Wallbox-Integration
                </router-link>
            </div>

            <!-- Upgrade Flow -->
            <div v-else>
                <div class="text-center mb-8">
                    <h1 class="text-3xl font-bold text-gray-900 mb-2">EV Monitor Premium</h1>
                    <p class="text-gray-500">Schalte die Wallbox-Integration und zukünftige Premium-Features frei.</p>
                </div>

                <!-- Feature List -->
                <div class="bg-white rounded-2xl shadow-sm border border-gray-200 p-6 mb-6">
                    <h2 class="font-semibold text-gray-900 mb-4">Was du bekommst</h2>
                    <ul class="space-y-3">
                        <li class="flex items-center gap-3 text-gray-700">
                            <svg class="w-5 h-5 text-green-600 shrink-0" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor">
                                <path stroke-linecap="round" stroke-linejoin="round" d="m4.5 12.75 6 6 9-13.5" />
                            </svg>
                            Wallbox-Integration (OCPP-Protokoll, automatisches Ladeprotokoll)
                        </li>
                        <li class="flex items-center gap-3 text-gray-700">
                            <svg class="w-5 h-5 text-green-600 shrink-0" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor">
                                <path stroke-linecap="round" stroke-linejoin="round" d="m4.5 12.75 6 6 9-13.5" />
                            </svg>
                            Automatische Ladeerkennung und Log-Erstellung
                        </li>
                        <li class="flex items-center gap-3 text-gray-700">
                            <svg class="w-5 h-5 text-green-600 shrink-0" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor">
                                <path stroke-linecap="round" stroke-linejoin="round" d="m4.5 12.75 6 6 9-13.5" />
                            </svg>
                            Alle zukünftigen Premium-Features inklusive
                        </li>
                    </ul>
                </div>

                <!-- Pricing Toggle -->
                <div class="bg-white rounded-2xl shadow-sm border border-gray-200 p-6 mb-6">
                    <div class="flex justify-center gap-2 mb-6">
                        <button
                            @click="selectedPlan = 'monthly'"
                            :class="selectedPlan === 'monthly' ? 'bg-green-600 text-white' : 'bg-gray-100 text-gray-700'"
                            class="px-4 py-2 rounded-lg font-medium text-sm transition-colors"
                        >
                            Monatlich
                        </button>
                        <button
                            @click="selectedPlan = 'yearly'"
                            :class="selectedPlan === 'yearly' ? 'bg-green-600 text-white' : 'bg-gray-100 text-gray-700'"
                            class="px-4 py-2 rounded-lg font-medium text-sm transition-colors"
                        >
                            Jährlich
                            <span class="ml-1 text-xs bg-orange-100 text-orange-700 px-1.5 py-0.5 rounded">~2 Monate gratis</span>
                        </button>
                    </div>

                    <div class="text-center mb-6">
                        <div v-if="selectedPlan === 'monthly'">
                            <div class="text-4xl font-bold text-gray-900">3,49 €<span class="text-lg font-normal text-gray-500">/Monat</span></div>
                        </div>
                        <div v-else>
                            <div class="text-4xl font-bold text-gray-900">29 €<span class="text-lg font-normal text-gray-500">/Jahr</span></div>
                            <div class="text-sm text-green-600 mt-1">≈ 2,42 €/Monat — 31% Ersparnis</div>
                        </div>
                    </div>

                    <button
                        @click="handleCheckout"
                        :disabled="checkoutLoading"
                        class="w-full bg-green-600 hover:bg-green-700 disabled:bg-gray-300 text-white font-semibold py-3 rounded-xl transition-colors"
                    >
                        <span v-if="checkoutLoading">Weiterleitung zu Stripe...</span>
                        <span v-else>Jetzt upgraden</span>
                    </button>
                    <p v-if="checkoutError" class="mt-3 text-sm text-red-600 text-center">{{ checkoutError }}</p>
                    <p class="mt-3 text-xs text-gray-400 text-center">Sichere Zahlung über Stripe. Jederzeit kündbar.</p>
                </div>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { subscriptionService } from '../api/subscriptionService';
import { analytics } from '../services/analytics';

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
        checkoutError.value = 'Fehler beim Starten des Checkouts. Bitte versuche es erneut.';
        checkoutLoading.value = false;
    }
}
</script>
