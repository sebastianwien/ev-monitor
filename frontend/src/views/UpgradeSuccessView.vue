<template>
    <div class="min-h-screen bg-gray-50 flex items-center justify-center px-4">
        <div class="max-w-md w-full bg-white rounded-2xl shadow-sm border border-gray-200 p-8 text-center">
            <div class="w-16 h-16 bg-green-100 rounded-full flex items-center justify-center mx-auto mb-4">
                <svg class="w-8 h-8 text-green-600" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor">
                    <path stroke-linecap="round" stroke-linejoin="round" d="M9 12.75 11.25 15 15 9.75M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z" />
                </svg>
            </div>
            <h1 class="text-2xl font-bold text-gray-900 mb-2">Premium aktiviert!</h1>
            <p class="text-gray-600 mb-8">Willkommen an Bord. Alle Premium-Features sind jetzt für dich freigeschaltet.</p>
            <router-link
                to="/wallbox"
                class="inline-flex items-center gap-2 bg-green-600 hover:bg-green-700 text-white font-medium px-6 py-3 rounded-xl transition-colors"
            >
                Zur Wallbox-Integration
            </router-link>
            <div class="mt-4">
                <router-link to="/dashboard" class="text-sm text-gray-400 hover:text-gray-600">
                    Zum Dashboard
                </router-link>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue';
import { useAuthStore } from '../stores/auth';
import { analytics } from '../services/analytics';

const authStore = useAuthStore();

onMounted(async () => {
    await authStore.refreshPremiumStatus();
    analytics.trackCheckoutCompleted();
});
</script>
