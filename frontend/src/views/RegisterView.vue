<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useI18n } from 'vue-i18n';
import { useRoute, useRouter } from 'vue-router';
import api from '../api/axios';
import { BoltIcon } from '@heroicons/vue/24/outline';
import { analytics } from '../services/analytics';
import { getStoredUtmParams, clearStoredUtmParams, trackRedditSignup, getStoredReferrer, clearStoredReferrer } from '../utils/reddit-pixel';

const { t, locale } = useI18n();
const route = useRoute();
const router = useRouter();

const googleOauthEnabled = import.meta.env.VITE_GOOGLE_OAUTH_ENABLED === 'true'
const fromDemo = window.history.state?.fromDemo === true
const loginPath = computed(() => locale.value === 'en' ? '/en/login' : '/login')

const email = ref('');
const password = ref('');
const referralCode = ref('');
const utmSource = ref('');
const utmMedium = ref('');
const utmCampaign = ref('');
const referrerSource = ref('');
const error = ref('');

onMounted(() => {
  const demoSource = analytics.getDemoContext()
  analytics.track('register_page_viewed', demoSource ? { demo_source: demoSource } : undefined)
  if (route.query.ref) {
    referralCode.value = String(route.query.ref);
  }

  // Capture UTM parameters from URL (direct link) or localStorage (landing page navigation)
  const urlParams = {
    source: route.query.utm_source ? String(route.query.utm_source) : '',
    medium: route.query.utm_medium ? String(route.query.utm_medium) : '',
    campaign: route.query.utm_campaign ? String(route.query.utm_campaign) : ''
  }

  // Prefer URL params over stored params (URL is more recent)
  const storedParams = getStoredUtmParams()
  const finalParams = (urlParams.source || urlParams.medium || urlParams.campaign) ? urlParams : storedParams

  if (finalParams) {
    utmSource.value = finalParams.source
    utmMedium.value = finalParams.medium
    utmCampaign.value = finalParams.campaign
  }

  referrerSource.value = getStoredReferrer() || ''
});
const pendingEmail = ref('');
const resendSent = ref(false);
const resendError = ref('');

const EMAIL_REGEX = /^[a-zA-Z0-9._%+\-]+@[a-zA-Z0-9.\-]+\.[a-zA-Z]{2,}$/

const handleRegister = async () => {
  if (!EMAIL_REGEX.test(email.value)) {
    error.value = t('auth.register.error_email_invalid');
    return;
  }
  if (password.value.length < 8) {
    error.value = t('auth.register.error_password_short');
    return;
  }
  try {
    error.value = '';
    const response = await api.post('/auth/register', {
      email: email.value,
      password: password.value,
      referralCode: referralCode.value || undefined,
      utmSource: utmSource.value || undefined,
      utmMedium: utmMedium.value || undefined,
      utmCampaign: utmCampaign.value || undefined,
      referrerSource: referrerSource.value || undefined,
    });
    if (response.data.status === 'PENDING_VERIFICATION') {
      pendingEmail.value = response.data.email;

      // Track successful registration
      analytics.trackRegistrationCompleted();

      // Track if user converted from a demo session
      const demoSource = analytics.getDemoContext()
      if (demoSource) {
        analytics.track('demo_converted_to_register', { demo_source: demoSource })
        analytics.clearDemoContext()
      }

      // Track Reddit conversion if user came from Reddit ad
      trackRedditSignup();

      // Clear stored UTM params and referrer after successful registration
      clearStoredUtmParams();
      clearStoredReferrer();
    }
  } catch (err: any) {
    error.value = err.response?.data?.message || t('auth.register.error_generic');
    analytics.trackRegistrationFailed(err.response?.data?.code || 'unknown');
  }
};

const handleResend = async () => {
  resendError.value = '';
  resendSent.value = false;
  try {
    await api.post('/auth/resend-verification', { email: pendingEmail.value });
    resendSent.value = true;
  } catch (err: any) {
    const code = err.response?.data?.code;
    resendError.value = code === 'RATE_LIMITED'
      ? t('auth.register.resend_rate_limited')
      : t('auth.register.resend_error');
  }
};
</script>

<template>
  <div class="flex items-center justify-center min-h-screen px-4 py-12">
    <div class="w-full max-w-md p-8 bg-white dark:bg-gray-800 rounded-xl shadow-lg">

      <!-- Pending Verification Screen -->
      <div v-if="pendingEmail" class="text-center">
        <div class="text-6xl mb-4">📬</div>
        <h2 class="text-2xl font-bold text-gray-800 dark:text-gray-200 mb-2">{{ t('auth.register.pending_title') }}</h2>
        <p class="text-gray-500 dark:text-gray-400 mb-2">
          {{ t('auth.register.pending_sent_to') }}
        </p>
        <p class="font-semibold text-indigo-600 mb-4">{{ pendingEmail }}</p>
        <p class="text-gray-500 dark:text-gray-400 text-sm mb-6">
          {{ t('auth.register.pending_instructions') }}<br>
          {{ t('auth.register.pending_expires') }}
        </p>

        <div v-if="!resendSent">
          <div v-if="resendError" class="text-sm text-red-600 bg-red-50 p-2 rounded mb-3">{{ resendError }}</div>
          <button
            @click="handleResend"
            class="text-sm text-indigo-600 hover:underline"
          >
            {{ t('auth.register.pending_resend') }}
          </button>
        </div>
        <p v-else class="text-sm text-green-600 font-medium">✅ {{ t('auth.register.pending_resent') }}</p>

        <div class="mt-6 text-sm text-gray-400 dark:text-gray-500">
          <router-link :to="loginPath" class="hover:text-gray-600 dark:hover:text-gray-300">{{ t('auth.register.pending_back_login') }}</router-link>
        </div>
      </div>

      <!-- Registration Form -->
      <div v-else>
        <router-link :to="locale === 'en' ? '/en' : '/'" class="flex items-center justify-center gap-2 mb-6 text-indigo-600 dark:text-indigo-400 hover:opacity-75 transition-opacity">
          <BoltIcon class="h-6 w-6" />
          <span class="text-xl font-bold tracking-tight">EV Monitor</span>
        </router-link>
        <h2 class="text-3xl font-bold text-center text-gray-800 dark:text-gray-200 mb-6">{{ t('auth.register.title') }}</h2>

        <div class="bg-green-50 dark:bg-green-900/20 rounded-lg px-4 py-3 mb-6 space-y-1.5">
          <div class="flex items-center gap-2 text-sm text-gray-700 dark:text-gray-300">
            <span class="text-green-600 font-bold">✓</span> {{ t('auth.register.benefit_1') }}
          </div>
          <div class="flex items-center gap-2 text-sm text-gray-700 dark:text-gray-300">
            <span class="text-green-600 font-bold">✓</span> {{ t('auth.register.benefit_2') }}
          </div>
          <div class="flex items-center gap-2 text-sm text-gray-700 dark:text-gray-300">
            <span class="text-green-600 font-bold">✓</span> {{ t('auth.register.benefit_3') }}
          </div>
        </div>

        <form @submit.prevent="handleRegister" class="space-y-6">
          <div>
            <label class="block text-sm font-medium text-gray-700 dark:text-gray-300">{{ t('auth.register.email') }}</label>
            <input v-model="email" type="email" required autocomplete="email" class="block w-full px-4 py-3 mt-1 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-lg shadow-sm focus:ring-indigo-500 focus:border-indigo-500" />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 dark:text-gray-300">{{ t('auth.register.password') }}</label>
            <input v-model="password" type="password" required autocomplete="new-password" class="block w-full px-4 py-3 mt-1 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-lg shadow-sm focus:ring-indigo-500 focus:border-indigo-500" />
            <p class="text-xs text-gray-500 dark:text-gray-400 mt-1">{{ t('auth.register.password_hint') }}</p>
          </div>
          <div v-if="error" class="text-sm font-medium text-red-600 bg-red-50 p-3 rounded-lg">{{ error }}</div>
          <button type="submit" v-haptic class="btn-3d w-full px-4 py-3 font-semibold text-white bg-green-600 rounded-lg hover:bg-green-700 focus:outline-none focus:ring-2 focus:ring-green-500 focus:ring-offset-2 transition">{{ t('auth.register.submit') }}</button>
        </form>

        <!-- Google SSO -->
        <template v-if="googleOauthEnabled">
          <div class="relative my-6">
            <div class="absolute inset-0 flex items-center">
              <div class="w-full border-t border-gray-200 dark:border-gray-600"></div>
            </div>
            <div class="relative flex justify-center text-sm">
              <span class="px-3 bg-white dark:bg-gray-800 text-gray-400">{{ t('auth.register.or_faster') }}</span>
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
            {{ t('auth.register.google') }}
          </a>
        </template>

        <div class="mt-6 text-center text-sm text-gray-500 dark:text-gray-400">
          {{ t('auth.register.have_account') }} <router-link :to="loginPath" class="font-semibold text-green-600 hover:text-green-500">{{ t('auth.register.login_link') }}</router-link>
        </div>
      </div>

    </div>
  </div>

  <Teleport to="body">
    <div v-if="!fromDemo" class="fixed bottom-6 left-4 z-50">
      <button @click="router.back()" class="btn-3d btn-3d-delay [--btn-shadow-color:#16a34a] inline-flex items-center gap-1.5 text-sm font-semibold text-white bg-green-600 hover:bg-green-700 rounded-full px-4 py-2 shadow-lg">
        <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke-width="2.5" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" d="M15.75 19.5 8.25 12l7.5-7.5" /></svg>
        {{ t('common.back') }}
      </button>
    </div>
  </Teleport>
</template>
