<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useI18n } from 'vue-i18n';
import { useRoute, useRouter } from 'vue-router';
import api from '../api/axios';

const { t } = useI18n();
const route = useRoute();
const router = useRouter();

const token = ref('');
const newPassword = ref('');
const confirmPassword = ref('');
const loading = ref(false);
const success = ref(false);
const error = ref('');
const errorType = ref<'expired' | 'invalid' | ''>('');

onMounted(() => {
  token.value = (route.query.token as string) || '';
  if (!token.value) {
    error.value = t('auth.reset_password.error_invalid_link');
    errorType.value = 'invalid';
  }
});

const handleSubmit = async () => {
  error.value = '';
  errorType.value = '';

  if (newPassword.value.length < 8) {
    error.value = t('auth.reset_password.error_too_short');
    return;
  }
  if (newPassword.value !== confirmPassword.value) {
    error.value = t('auth.reset_password.error_mismatch');
    return;
  }

  loading.value = true;
  try {
    await api.post('/auth/reset-password', { token: token.value, newPassword: newPassword.value });
    success.value = true;
    setTimeout(() => router.push('/login'), 3000);
  } catch (err: any) {
    const code = err.response?.data?.code;
    if (code === 'TOKEN_EXPIRED') {
      error.value = t('auth.reset_password.error_expired');
      errorType.value = 'expired';
    } else if (code === 'INVALID_TOKEN') {
      error.value = t('auth.reset_password.error_invalid');
      errorType.value = 'invalid';
    } else {
      error.value = t('auth.reset_password.error_generic');
    }
  } finally {
    loading.value = false;
  }
};
</script>

<template>
  <div class="flex items-center justify-center min-h-[80vh] bg-gray-100 dark:bg-gray-950">
    <div class="w-full max-w-md p-8 bg-white dark:bg-gray-800 rounded-sm shadow-[4px_4px_0_rgba(0,0,0,0.30)] dark:shadow-[4px_4px_0_rgba(255,255,255,0.30)]">
      <h2 class="text-3xl font-bold text-center text-gray-800 dark:text-gray-200 mb-8">{{ t('auth.reset_password.title') }}</h2>

      <!-- Success State -->
      <div v-if="success" class="text-center">
        <div class="w-16 h-16 bg-green-100 dark:bg-green-900/40 rounded-full flex items-center justify-center mx-auto mb-4">
          <svg class="w-8 h-8 text-green-600 dark:text-green-400" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" d="M9 12.75 11.25 15 15 9.75M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z" />
          </svg>
        </div>
        <p class="text-gray-700 dark:text-gray-200 font-medium mb-2">{{ t('auth.reset_password.success_title') }}</p>
        <p class="text-gray-500 dark:text-gray-400 text-sm">{{ t('auth.reset_password.success_redirect') }}</p>
      </div>

      <!-- Form State -->
      <form v-else @submit.prevent="handleSubmit" class="space-y-6">
        <div>
          <label class="block text-sm font-medium text-gray-700 dark:text-gray-300">{{ t('auth.reset_password.new_password') }}</label>
          <input
            v-model="newPassword"
            type="password"
            required
            autocomplete="new-password"
            class="block w-full px-4 py-3 mt-1 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-sm shadow-sm focus:ring-2 focus:ring-green-500 focus:border-green-600"
            :placeholder="t('auth.reset_password.new_password_placeholder')"
          />
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 dark:text-gray-300">{{ t('auth.reset_password.confirm_password') }}</label>
          <input
            v-model="confirmPassword"
            type="password"
            required
            autocomplete="new-password"
            class="block w-full px-4 py-3 mt-1 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-sm shadow-sm focus:ring-2 focus:ring-green-500 focus:border-green-600"
            :placeholder="t('auth.reset_password.confirm_password_placeholder')"
          />
        </div>
        <div v-if="error" class="text-sm text-red-600 dark:text-red-300 bg-red-50 dark:bg-red-900/30 p-3 rounded-sm">
          {{ error }}
          <div v-if="errorType === 'expired' || errorType === 'invalid'" class="mt-2">
            <router-link to="/forgot-password" class="font-semibold underline hover:no-underline">
              {{ t('auth.reset_password.request_new') }}
            </router-link>
          </div>
        </div>
        <button
          type="submit"
          v-haptic
          :disabled="loading || !token"
          class="btn-3d [--btn-shadow-color:#111827] dark:[--btn-shadow-color:#000000] w-full px-4 py-3 font-semibold text-white bg-green-600 border-2 border-gray-900 dark:border-gray-100 rounded-sm hover:bg-green-700 focus:outline-none focus:ring-2 focus:ring-green-500 focus:ring-offset-2 disabled:bg-gray-300 dark:disabled:bg-gray-600 disabled:border-gray-400 dark:disabled:border-gray-500 disabled:cursor-not-allowed transition"
        >
          {{ loading ? t('auth.reset_password.saving') : t('auth.reset_password.submit') }}
        </button>
      </form>
    </div>
  </div>
</template>
