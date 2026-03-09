<script setup lang="ts">
import { ref } from 'vue';
import { useAuthStore } from '../stores/auth';
import { useRouter } from 'vue-router';
import { analytics } from '../services/analytics';
import { BoltIcon, ShieldCheckIcon, CurrencyEuroIcon } from '@heroicons/vue/24/outline';

const email = ref('');
const password = ref('');
const authStore = useAuthStore();
const router = useRouter();
const error = ref('');
const errorCode = ref('');
const resendSent = ref(false);

const handleLogin = async () => {
  try {
    error.value = '';
    errorCode.value = '';
    await authStore.login({ email: email.value, password: password.value });
    analytics.trackLogin();
    router.push('/statistics');
  } catch (err: any) {
    const code = err.response?.data?.code;
    errorCode.value = code || '';
    if (code === 'EMAIL_NOT_VERIFIED') {
      error.value = 'Bitte bestätige zuerst deine E-Mail-Adresse.';
    } else {
      error.value = 'Ungültige E-Mail/Username oder Passwort';
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
  <div class="min-h-[80vh] flex items-center justify-center bg-gradient-to-br from-indigo-50 via-white to-violet-50 px-4 py-12">
    <div class="w-full max-w-md">

      <!-- Card -->
      <div class="bg-white rounded-2xl shadow-xl overflow-hidden">

        <!-- Card header: branded strip -->
        <div class="bg-gradient-to-r from-indigo-600 to-violet-600 px-8 py-6 text-white text-center">
          <div class="flex items-center justify-center gap-2 mb-2">
            <BoltIcon class="h-7 w-7" />
            <span class="text-2xl font-bold tracking-tight">EV Monitor</span>
          </div>
          <p class="text-indigo-200 text-sm">Dein Ladetagebuch für Elektroautos</p>
        </div>

        <!-- Form -->
        <div class="px-8 py-8">
          <h2 class="text-xl font-bold text-gray-800 mb-6 text-center">Willkommen zurück</h2>

          <form @submit.prevent="handleLogin" class="space-y-5">
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">E-Mail oder Username</label>
              <input v-model="email" type="text" required
                class="block w-full px-4 py-3 border border-gray-300 rounded-lg shadow-sm focus:ring-indigo-500 focus:border-indigo-500"
                placeholder="deine@email.de" />
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Passwort</label>
              <input v-model="password" type="password" required
                class="block w-full px-4 py-3 border border-gray-300 rounded-lg shadow-sm focus:ring-indigo-500 focus:border-indigo-500" />
            </div>

            <div v-if="error" class="text-sm font-medium bg-red-50 p-3 rounded-lg border border-red-100"
              :class="errorCode === 'EMAIL_NOT_VERIFIED' ? 'text-orange-700' : 'text-red-600'">
              {{ error }}
              <div v-if="errorCode === 'EMAIL_NOT_VERIFIED'" class="mt-2">
                <span v-if="!resendSent">
                  <button @click="handleResendFromLogin" class="font-semibold underline hover:no-underline">
                    Bestätigungs-E-Mail erneut senden
                  </button>
                </span>
                <span v-else class="text-green-700 font-medium">E-Mail verschickt!</span>
              </div>
            </div>

            <button type="submit"
              class="w-full px-4 py-3 font-semibold text-white bg-indigo-600 rounded-lg shadow hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2 transition">
              Anmelden
            </button>
          </form>

          <div class="mt-4 text-center">
            <router-link to="/forgot-password" class="text-sm text-gray-400 hover:text-indigo-600 transition">
              Passwort vergessen?
            </router-link>
          </div>

          <div class="mt-3 text-center text-sm text-gray-500">
            Noch kein Konto?
            <router-link to="/register" class="font-semibold text-indigo-600 hover:text-indigo-500">
              Hier registrieren
            </router-link>
          </div>
        </div>

        <!-- Trust badges -->
        <div class="px-8 py-4 bg-gray-50 border-t border-gray-100 flex items-center justify-center gap-6 text-xs text-gray-400">
          <span class="flex items-center gap-1">
            <CurrencyEuroIcon class="h-3.5 w-3.5" />
            Kostenlos
          </span>
          <span class="text-gray-200">|</span>
          <span class="flex items-center gap-1">
            <ShieldCheckIcon class="h-3.5 w-3.5" />
            DSGVO-konform
          </span>
          <span class="text-gray-200">|</span>
          <span>Kein Tracking</span>
        </div>
      </div>

    </div>
  </div>
</template>
