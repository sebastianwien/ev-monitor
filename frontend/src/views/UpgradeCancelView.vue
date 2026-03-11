<template>
    <div class="min-h-screen bg-gray-50 flex items-center justify-center px-4">
        <div class="max-w-md w-full bg-white rounded-2xl shadow-sm border border-gray-200 p-8 text-center">
            <div class="w-16 h-16 bg-gray-100 rounded-full flex items-center justify-center mx-auto mb-4">
                <svg class="w-8 h-8 text-gray-400" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor">
                    <path stroke-linecap="round" stroke-linejoin="round" d="M6 18 18 6M6 6l12 12" />
                </svg>
            </div>
            <h1 class="text-2xl font-bold text-gray-900 mb-2">Kein Problem.</h1>
            <p class="text-gray-600 mb-8">Der Upgrade-Vorgang wurde abgebrochen. Du kannst jederzeit upgraden.</p>
            <router-link
                to="/upgrade"
                class="inline-flex items-center gap-2 bg-green-600 hover:bg-green-700 text-white font-medium px-6 py-3 rounded-xl transition-colors"
            >
                Zurück zu Premium
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
</script>
