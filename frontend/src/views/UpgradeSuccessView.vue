<template>
    <div class="min-h-screen bg-gray-50 dark:bg-gray-900 flex items-start justify-center pt-10 px-4">
        <div class="max-w-md w-full bg-white dark:bg-gray-800 rounded-2xl shadow-sm border border-gray-200 dark:border-gray-700 p-8 text-center">

            <!-- Loading state: polling for premium activation -->
            <template v-if="isPolling">
                <div class="w-16 h-16 bg-green-100 dark:bg-green-900/30 rounded-full flex items-center justify-center mx-auto mb-4">
                    <svg class="w-8 h-8 text-green-400 dark:text-green-500 animate-spin" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                        <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="2" />
                        <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v4a4 4 0 00-4 4H4z" />
                    </svg>
                </div>
                <p class="text-gray-600 dark:text-gray-400 font-medium">{{ t('upgrade.success_activating') }}</p>
            </template>

            <!-- Timeout state: webhook too slow -->
            <template v-else-if="pollTimedOut">
                <div class="w-16 h-16 bg-yellow-100 dark:bg-yellow-900/30 rounded-full flex items-center justify-center mx-auto mb-4">
                    <svg class="w-8 h-8 text-yellow-500 dark:text-yellow-400" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor">
                        <path stroke-linecap="round" stroke-linejoin="round" d="M12 9v3.75m-9.303 3.376c-.866 1.5.217 3.374 1.948 3.374h14.71c1.73 0 2.813-1.874 1.948-3.374L13.949 3.378c-.866-1.5-3.032-1.5-3.898 0L2.697 16.126ZM12 15.75h.007v.008H12v-.008Z" />
                    </svg>
                </div>
                <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100 mb-2">{{ t('upgrade.success_title') }}</h1>
                <p class="text-sm text-gray-500 dark:text-gray-400 mb-8">{{ t('upgrade.success_slow') }}</p>
                <router-link
                    to="/imports"
                    class="inline-flex items-center gap-2 bg-green-600 hover:bg-green-700 text-white font-medium px-6 py-3 rounded-xl transition-colors"
                >
                    {{ t('upgrade.success_cta') }}
                </router-link>
                <div class="mt-4">
                    <router-link to="/dashboard" class="text-sm text-gray-400 dark:text-gray-500 hover:text-gray-600 dark:hover:text-gray-300">
                        {{ t('upgrade.back_dashboard') }}
                    </router-link>
                </div>
            </template>

            <!-- Success state: isPremium confirmed -->
            <template v-else>
                <div class="w-16 h-16 bg-green-100 dark:bg-green-900/30 rounded-full flex items-center justify-center mx-auto mb-4">
                    <svg class="w-8 h-8 text-green-600 dark:text-green-400" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor">
                        <path stroke-linecap="round" stroke-linejoin="round" d="M9 12.75 11.25 15 15 9.75M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z" />
                    </svg>
                </div>
                <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100 mb-2">{{ t('upgrade.success_title') }}</h1>
                <p class="text-gray-600 dark:text-gray-400 mb-8">{{ t('upgrade.success_desc') }}</p>
                <router-link
                    to="/imports"
                    class="inline-flex items-center gap-2 bg-green-600 hover:bg-green-700 text-white font-medium px-6 py-3 rounded-xl transition-colors"
                >
                    {{ t('upgrade.success_cta') }}
                </router-link>
                <div class="mt-4">
                    <router-link to="/dashboard" class="text-sm text-gray-400 dark:text-gray-500 hover:text-gray-600 dark:hover:text-gray-300">
                        {{ t('upgrade.back_dashboard') }}
                    </router-link>
                </div>
            </template>

        </div>
    </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useI18n } from 'vue-i18n';
import { useAuthStore } from '../stores/auth';
import { analytics } from '../services/analytics';

const { t } = useI18n();
const authStore = useAuthStore();

const isPolling = ref(true);
const pollTimedOut = ref(false);

const POLL_ATTEMPTS = 15;
const POLL_DELAY_MS = 2000;

onMounted(async () => {
    for (let i = 0; i < POLL_ATTEMPTS; i++) {
        await authStore.refreshToken();
        if (authStore.isPremium) {
            isPolling.value = false;
            analytics.trackCheckoutCompleted();
            return;
        }
        if (i < POLL_ATTEMPTS - 1) {
            await new Promise(resolve => setTimeout(resolve, POLL_DELAY_MS));
        }
    }

    // All attempts exhausted - webhook likely delayed
    isPolling.value = false;
    pollTimedOut.value = true;
    // Still fire the analytics event - payment went through on Stripe's side
    analytics.trackCheckoutCompleted();
});
</script>
