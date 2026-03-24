<script setup lang="ts">
import { ref, computed } from 'vue';
import { useI18n } from 'vue-i18n';
import { useAuthStore } from '../stores/auth';
import { useRouter, useRoute } from 'vue-router';
import { analytics } from '../services/analytics';
import { BoltIcon, ShieldCheckIcon, CurrencyEuroIcon } from '@heroicons/vue/24/outline';

const { t, locale } = useI18n();
const googleOauthEnabled = import.meta.env.VITE_GOOGLE_OAUTH_ENABLED === 'true'

const email = ref('');
const password = ref('');
const authStore = useAuthStore();
const router = useRouter();
const route = useRoute();
const error = ref('');
const errorCode = ref('');
const resendSent = ref(false);
const infoMessage = computed(() => route.query.reason === 'email-changed' ? t('auth.login.email_changed') : '');
const sessionExpiredMessage = computed(() => route.query.reason === 'session-expired' ? t('auth.login.session_expired') : '');

const registerPath = computed(() => locale.value === 'en' ? '/en/register' : '/register')
const forgotPath = computed(() => locale.value === 'en' ? '/en/forgot-password' : '/forgot-password')

const handleLogin = async () => {
  try {
    error.value = '';
    errorCode.value = '';
    await authStore.login({ email: email.value, password: password.value });
    analytics.trackLogin();
    router.push('/dashboard');
  } catch (err: any) {
    const code = err.response?.data?.code;
    errorCode.value = code || '';
    if (code === 'EMAIL_NOT_VERIFIED') {
      error.value = t('auth.login.error_not_verified');
      analytics.trackLoginFailed('email_not_verified');
    } else {
      error.value = t('auth.login.error_invalid');
      analytics.trackLoginFailed('invalid_credentials');
    }
  }
};

const handleResendFromLogin = async () => {
  try {
    const { default: api } = await import('../api/axios');
    await api.post('/auth/resend-verification', { email: email.value });
    resendSent.value = true;
  } catch {
    // silently ignore
  }
};
</script>

<template>
  <div class="min-h-[80vh] flex items-center justify-center bg-gradient-to-br from-indigo-50 via-white to-violet-50 dark:from-gray-900 dark:via-gray-900 dark:to-gray-900 px-4 py-12">
    <div class="w-full max-w-md">

      <!-- Card -->
      <div class="bg-white dark:bg-gray-800 rounded-2xl shadow-xl overflow-hidden">

        <!-- Card header: branded strip -->
        <div class="bg-gradient-to-r from-indigo-600 to-violet-600 px-8 py-6 text-white text-center">
          <div class="flex items-center justify-center gap-2 mb-2">
            <BoltIcon class="h-7 w-7" />
            <span class="text-2xl font-bold tracking-tight">EV Monitor</span>
          </div>
          <p class="text-indigo-200 text-sm">{{ t('auth.login.tagline') }}</p>
        </div>

        <!-- Form -->
        <div class="px-8 py-8">
          <h2 class="text-xl font-bold text-gray-800 dark:text-gray-200 mb-6 text-center">{{ t('auth.login.welcome') }}</h2>

          <div v-if="infoMessage" class="mb-4 text-sm font-medium bg-green-50 p-3 rounded-lg border border-green-200 text-green-700">
            {{ infoMessage }}
          </div>
          <div v-if="sessionExpiredMessage" class="mb-4 text-sm font-medium bg-amber-50 p-3 rounded-lg border border-amber-200 text-amber-700">
            {{ sessionExpiredMessage }}
          </div>

          <form @submit.prevent="handleLogin" class="space-y-5">
            <div>
              <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">{{ t('auth.login.email_or_username') }}</label>
              <input v-model="email" type="text" required
                class="block w-full px-4 py-3 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-lg shadow-sm focus:ring-indigo-500 focus:border-indigo-500"
                :placeholder="t('auth.login.email_placeholder')" />
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">{{ t('auth.login.password') }}</label>
              <input v-model="password" type="password" required
                class="block w-full px-4 py-3 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-lg shadow-sm focus:ring-indigo-500 focus:border-indigo-500" />
            </div>

            <div v-if="error" class="text-sm font-medium bg-red-50 p-3 rounded-lg border border-red-100"
              :class="errorCode === 'EMAIL_NOT_VERIFIED' ? 'text-orange-700' : 'text-red-600'">
              {{ error }}
              <div v-if="errorCode === 'EMAIL_NOT_VERIFIED'" class="mt-2">
                <span v-if="!resendSent">
                  <button @click="handleResendFromLogin" class="font-semibold underline hover:no-underline">
                    {{ t('auth.login.resend_email') }}
                  </button>
                </span>
                <span v-else class="text-green-700 font-medium">{{ t('auth.login.resend_sent') }}</span>
              </div>
            </div>

            <button type="submit"
              v-haptic
              class="btn-3d w-full px-4 py-3 font-semibold text-white bg-indigo-600 rounded-lg hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2 transition">
              {{ t('auth.login.submit') }}
            </button>
          </form>

          <!-- Google SSO -->
          <template v-if="googleOauthEnabled">
            <div class="relative my-5">
              <div class="absolute inset-0 flex items-center">
                <div class="w-full border-t border-gray-200 dark:border-gray-600"></div>
              </div>
              <div class="relative flex justify-center text-sm">
                <span class="px-3 bg-white dark:bg-gray-800 text-gray-400">{{ t('auth.login.or') }}</span>
              </div>
            </div>
            <a
              href="/oauth2/authorization/google"
              class="w-full flex items-center justify-center gap-3 px-4 py-3 border border-gray-300 dark:border-gray-600 rounded-lg text-sm font-medium text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 transition">
              <svg class="h-5 w-5" viewBox="0 0 24 24">
                <path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" fill="#4285F4"/>
                <path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" fill="#34A853"/>
                <path d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l3.66-2.84z" fill="#FBBC05"/>
                <path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" fill="#EA4335"/>
              </svg>
              {{ t('auth.login.google') }}
            </a>
          </template>

          <div class="mt-4 text-center">
            <router-link :to="forgotPath" class="text-sm text-gray-400 hover:text-indigo-600 transition">
              {{ t('auth.login.forgot_password') }}
            </router-link>
          </div>

          <div class="mt-3 text-center text-sm text-gray-500 dark:text-gray-400">
            {{ t('auth.login.no_account') }}
            <router-link :to="registerPath" class="font-semibold text-indigo-600 hover:text-indigo-500">
              {{ t('auth.login.register_link') }}
            </router-link>
          </div>
        </div>

        <!-- Trust badges -->
        <div class="px-8 py-4 bg-gray-50 dark:bg-gray-900 border-t border-gray-100 dark:border-gray-700 flex items-center justify-center gap-6 text-xs text-gray-400">
          <span class="flex items-center gap-1">
            <CurrencyEuroIcon class="h-3.5 w-3.5" />
            {{ t('auth.login.free') }}
          </span>
          <span class="text-gray-200">|</span>
          <span class="flex items-center gap-1">
            <ShieldCheckIcon class="h-3.5 w-3.5" />
            {{ t('auth.login.gdpr') }}
          </span>
          <span class="text-gray-200">|</span>
          <span>{{ t('auth.login.no_tracking') }}</span>
        </div>
      </div>

    </div>
  </div>
</template>
