<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useI18n } from 'vue-i18n';
import { useRoute, useRouter } from 'vue-router';
import api from '../api/axios';
import { BoltIcon } from '@heroicons/vue/24/outline';
import GridRippleBackground from '../components/shared/GridRippleBackground.vue';
import GoogleButton from '../components/auth/GoogleButton.vue';
import { analytics } from '../services/analytics';
import { currentUtmParams, clearStoredUtmParams, trackRedditSignup, getStoredReferrer, clearStoredReferrer } from '../utils/reddit-pixel';
import { isDetectionAmbiguous } from '../composables/useCountryDetection';
import { useCountryStore } from '../stores/country';
import type { CountryCode } from '../config/unitSystems';
import { COUNTRY_OPTIONS } from '../config/countries';
import { Capacitor } from '@capacitor/core';

const { t, locale } = useI18n();
const route = useRoute();
const router = useRouter();
const countryStore = useCountryStore();

// Drittanbieter-Login (Google) nur im Web: native App wuerde sonst Sign in with
// Apple erzwingen (Guideline 4.8). E-Mail/Passwort-Registrierung bleibt.
const googleOauthEnabled = import.meta.env.VITE_GOOGLE_OAUTH_ENABLED === 'true' && !Capacitor.isNativePlatform()
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

// Country picker - shown only when detection is ambiguous (static: navigator.languages doesn't change at runtime)
const showCountryPicker = isDetectionAmbiguous()
const selectedCountry = ref<CountryCode>(countryStore.country)

onMounted(() => {
  const demoSource = analytics.getDemoContext()
  analytics.track('register_page_viewed', demoSource ? { demo_source: demoSource } : undefined)
  if (route.query.ref) {
    referralCode.value = String(route.query.ref);
  }

  // UTM from the URL (direct link) or localStorage (landing page navigation)
  const finalParams = currentUtmParams()

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
    if (showCountryPicker) {
      countryStore.setCountry(selectedCountry.value)
    }
    const response = await api.post('/auth/register', {
      email: email.value,
      password: password.value,
      referralCode: referralCode.value || undefined,
      utmSource: utmSource.value || undefined,
      utmMedium: utmMedium.value || undefined,
      utmCampaign: utmCampaign.value || undefined,
      referrerSource: referrerSource.value || undefined,
      registrationLocale: locale.value || undefined,
      // Immer senden: bei sichtbarem Picker die Auswahl, sonst das Ergebnis der
      // Country-Detection (localStorage -> Browsersprache -> Default DE).
      // Vorher blieb country genau im eindeutigen Fall leer.
      country: showCountryPicker ? selectedCountry.value : countryStore.country,
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
<div class="sl-grid isolate min-h-screen bg-white dark:bg-gray-950 overflow-x-clip">
  <!-- Maus-reaktives Gitter wie auf der Startseite -->
  <GridRippleBackground />

  <div class="flex items-center justify-center min-h-screen px-4 py-12">
    <div class="w-full max-w-md p-8 bg-white dark:bg-gray-800 border-2 border-gray-900 dark:border-gray-100 rounded-xl shadow-[5px_5px_0_0_#15803d] dark:shadow-[5px_5px_0_0_#22c55e]">

      <!-- Pending Verification Screen -->
      <div v-if="pendingEmail" class="text-center">
        <div class="text-6xl mb-4">📬</div>
        <h2 class="text-2xl font-bold text-gray-800 dark:text-gray-200 mb-2">{{ t('auth.register.pending_title') }}</h2>
        <p class="text-gray-500 dark:text-gray-400 mb-2">
          {{ t('auth.register.pending_sent_to') }}
        </p>
        <p class="font-semibold text-green-600 dark:text-green-400 mb-4">{{ pendingEmail }}</p>
        <p class="text-gray-500 dark:text-gray-400 text-sm mb-6">
          {{ t('auth.register.pending_instructions') }}<br>
          {{ t('auth.register.pending_expires') }}
        </p>

        <div v-if="!resendSent">
          <div v-if="resendError" class="text-sm text-red-600 dark:text-red-300 bg-red-50 dark:bg-red-900/30 p-2 rounded mb-3">{{ resendError }}</div>
          <button
            @click="handleResend"
            class="text-sm text-green-600 dark:text-green-400 hover:underline"
          >
            {{ t('auth.register.pending_resend') }}
          </button>
        </div>
        <p v-else class="text-sm text-green-600 dark:text-green-400 font-medium">✅ {{ t('auth.register.pending_resent') }}</p>

        <div class="mt-6 text-sm text-gray-400 dark:text-gray-500">
          <router-link :to="loginPath" class="hover:text-gray-600 dark:hover:text-gray-300">{{ t('auth.register.pending_back_login') }}</router-link>
        </div>
      </div>

      <!-- Registration Form -->
      <div v-else>
        <router-link :to="locale === 'en' ? '/en' : '/'" class="flex items-center justify-center gap-2 mb-6 text-green-600 dark:text-green-400 hover:opacity-75 transition-opacity">
          <BoltIcon class="h-6 w-6" />
          <span class="text-xl font-bold tracking-tight">EV Monitor</span>
        </router-link>
        <h2 class="text-3xl font-bold text-center text-gray-800 dark:text-gray-200 mb-6">{{ t('auth.register.title') }}</h2>

        <div class="bg-green-50 dark:bg-green-900/20 rounded-sm px-4 py-3 mb-6 space-y-1.5">
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
            <input v-model="email" type="email" required autocomplete="email" class="block w-full px-4 py-3 mt-1 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-sm shadow-sm focus:ring-2 focus:ring-green-500 focus:border-green-600" />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 dark:text-gray-300">{{ t('auth.register.password') }}</label>
            <input v-model="password" type="password" required autocomplete="new-password" class="block w-full px-4 py-3 mt-1 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-sm shadow-sm focus:ring-2 focus:ring-green-500 focus:border-green-600" />
            <p class="text-xs text-gray-500 dark:text-gray-400 mt-1">{{ t('auth.register.password_hint') }}</p>
          </div>
          <!-- Country picker: only shown when browser language is ambiguous -->
          <div v-if="showCountryPicker" data-testid="country-picker">
            <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">{{ t('auth.register.country_label') }}</label>
            <p class="text-xs text-gray-500 dark:text-gray-400 mb-2">{{ t('auth.register.country_hint') }}</p>
            <div class="flex gap-1.5 flex-wrap">
              <button
                v-for="c in COUNTRY_OPTIONS"
                :key="c.code"
                type="button"
                @click="selectedCountry = c.code"
                class="flex items-center gap-1.5 px-3 py-2 rounded-sm border-2 text-sm transition-all"
                :class="selectedCountry === c.code
                  ? 'border-green-500 bg-green-50 dark:bg-green-900/20 text-green-700 dark:text-green-400 font-medium'
                  : 'border-gray-200 dark:border-gray-600 text-gray-600 dark:text-gray-300 hover:border-gray-300 dark:hover:border-gray-500'"
              >
                <span>{{ c.flag }}</span>
                <span>{{ c.name[locale] || c.name.en }}</span>
              </button>
            </div>
          </div>
          <div v-if="error" class="text-sm font-medium text-red-600 dark:text-red-300 bg-red-50 dark:bg-red-900/30 p-3 rounded-sm">{{ error }}</div>
          <button type="submit" v-haptic class="btn-3d cta-shadow w-full px-4 py-3 font-semibold text-white bg-green-600 border-2 border-gray-900 dark:border-gray-100 rounded-sm hover:bg-green-700 focus:outline-none focus:ring-2 focus:ring-green-500 focus:ring-offset-2 transition">{{ t('auth.register.submit') }}</button>
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
          <GoogleButton :label="t('auth.register.google')" />
        </template>

        <div class="mt-6 text-center text-sm text-gray-500 dark:text-gray-400">
          {{ t('auth.register.have_account') }} <router-link :to="loginPath" class="font-semibold text-green-600 dark:text-green-400 hover:text-green-500 dark:hover:text-green-300">{{ t('auth.register.login_link') }}</router-link>
        </div>
      </div>

    </div>
  </div>

  <Teleport to="body">
    <div v-if="!fromDemo" class="fixed bottom-6 left-4 z-50">
      <button @click="router.back()" class="btn-3d btn-3d-delay [--btn-shadow-color:#16a34a] inline-flex items-center gap-1.5 text-sm font-semibold text-white bg-green-600 hover:bg-green-700 rounded-full px-4 py-2 shadow-[4px_4px_0_rgba(0,0,0,0.30)] dark:shadow-[4px_4px_0_rgba(255,255,255,0.30)]">
        <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke-width="2.5" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" d="M15.75 19.5 8.25 12l7.5-7.5" /></svg>
        {{ t('common.back') }}
      </button>
    </div>
  </Teleport>
</div>
</template>
