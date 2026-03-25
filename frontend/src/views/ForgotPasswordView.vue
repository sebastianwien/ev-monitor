<script setup lang="ts">
import { ref, computed } from 'vue';
import { useI18n } from 'vue-i18n';
import api from '../api/axios';
import { analytics } from '../services/analytics';

const { t, locale } = useI18n();
const email = ref('');
const loading = ref(false);
const submitted = ref(false);
const error = ref('');

const loginPath = computed(() => locale.value === 'en' ? '/en/login' : '/login')

const handleSubmit = async () => {
  loading.value = true;
  error.value = '';
  try {
    await api.post('/auth/forgot-password', { email: email.value });
    submitted.value = true;
    analytics.trackPasswordResetRequested();
  } catch (err: any) {
    if (err.response?.status === 429) {
      error.value = t('auth.forgot_password.error_rate_limited');
    } else {
      error.value = t('auth.forgot_password.error_generic');
    }
  } finally {
    loading.value = false;
  }
};
</script>

<template>
  <div class="flex items-center justify-center min-h-[80vh] bg-gray-100">
    <div class="w-full max-w-md p-8 bg-white rounded-xl shadow-lg">
      <h2 class="text-3xl font-bold text-center text-gray-800 mb-2">{{ t('auth.forgot_password.title') }}</h2>

      <!-- Success State -->
      <div v-if="submitted" class="text-center">
        <div class="w-16 h-16 bg-green-100 rounded-full flex items-center justify-center mx-auto my-6">
          <svg class="w-8 h-8 text-green-600" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" d="M21.75 6.75v10.5a2.25 2.25 0 0 1-2.25 2.25h-15a2.25 2.25 0 0 1-2.25-2.25V6.75m19.5 0A2.25 2.25 0 0 0 19.5 4.5h-15a2.25 2.25 0 0 0-2.25 2.25m19.5 0v.243a2.25 2.25 0 0 1-1.07 1.916l-7.5 4.615a2.25 2.25 0 0 1-2.36 0L3.32 8.91a2.25 2.25 0 0 1-1.07-1.916V6.75" />
          </svg>
        </div>
        <p class="text-gray-700 mb-2 font-medium">{{ t('auth.forgot_password.success_title') }}</p>
        <p class="text-gray-500 text-sm mb-6">
          {{ t('auth.forgot_password.success_text') }}<br>
          {{ t('auth.forgot_password.success_expires') }}
        </p>
        <router-link :to="loginPath" class="text-sm text-indigo-600 hover:text-indigo-500 font-medium">
          {{ t('auth.forgot_password.back_login') }}
        </router-link>
      </div>

      <!-- Form State -->
      <form v-else @submit.prevent="handleSubmit" class="space-y-6 mt-6">
        <p class="text-gray-500 text-sm text-center -mt-4">
          {{ t('auth.forgot_password.intro') }}
        </p>
        <div>
          <label class="block text-sm font-medium text-gray-700">{{ t('auth.forgot_password.email') }}</label>
          <input
            v-model="email"
            type="email"
            required
            autocomplete="email"
            class="block w-full px-4 py-3 mt-1 border border-gray-300 rounded-lg shadow-sm focus:ring-indigo-500 focus:border-indigo-500"
            :placeholder="t('auth.forgot_password.email_placeholder')"
          />
        </div>
        <div v-if="error" class="text-sm text-red-600 bg-red-50 p-3 rounded-lg">{{ error }}</div>
        <button
          type="submit"
          :disabled="loading"
          class="w-full px-4 py-3 font-semibold text-white bg-indigo-600 rounded-lg shadow hover:bg-indigo-700 disabled:bg-gray-300 transition"
        >
          {{ loading ? t('auth.forgot_password.sending') : t('auth.forgot_password.submit') }}
        </button>
        <div class="text-center text-sm text-gray-500">
          <router-link :to="loginPath" class="text-indigo-600 hover:text-indigo-500">{{ t('auth.forgot_password.back_login') }}</router-link>
        </div>
      </form>
    </div>
  </div>
</template>
