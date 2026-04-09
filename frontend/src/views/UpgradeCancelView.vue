<template>
    <div class="min-h-screen bg-gray-50 dark:bg-gray-900 flex items-start justify-center pt-10 px-4">
        <div class="max-w-md w-full bg-white dark:bg-gray-800 rounded-2xl shadow-sm border border-gray-200 dark:border-gray-700 p-8 text-center">
            <div class="w-16 h-16 bg-gray-100 dark:bg-gray-700 rounded-full flex items-center justify-center mx-auto mb-4">
                <svg class="w-8 h-8 text-gray-400 dark:text-gray-500" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor">
                    <path stroke-linecap="round" stroke-linejoin="round" d="M6 18 18 6M6 6l12 12" />
                </svg>
            </div>
            <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100 mb-2">{{ t('upgrade.cancel_title') }}</h1>
            <p class="text-gray-600 dark:text-gray-400 mb-8">{{ t('upgrade.cancel_desc') }}</p>
            <router-link
                to="/upgrade"
                class="inline-flex items-center gap-2 bg-indigo-600 hover:bg-indigo-700 text-white font-medium px-6 py-3 rounded-xl transition-colors"
            >
                {{ t('upgrade.cancel_cta') }}
            </router-link>
            <div class="mt-4">
                <router-link to="/dashboard" class="text-sm text-gray-400 dark:text-gray-500 hover:text-gray-600 dark:hover:text-gray-300">
                    {{ t('upgrade.back_dashboard') }}
                </router-link>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n';

const { t } = useI18n();
</script>
