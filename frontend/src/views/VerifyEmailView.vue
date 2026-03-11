<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { useAuthStore } from '../stores/auth';
import api from '../api/axios';
import { analytics } from '../services/analytics';
import { BoltIcon } from '@heroicons/vue/24/outline';

const router = useRouter();
const route = useRoute();
const authStore = useAuthStore();

type Status = 'loading' | 'success' | 'expired' | 'invalid';
const status = ref<Status>('loading');
const resendEmail = ref('');
const resendSent = ref(false);
const resendError = ref('');

onMounted(async () => {
  const token = route.query.token as string;
  if (!token) {
    status.value = 'invalid';
    return;
  }
  try {
    const response = await api.get(`/auth/verify-email?token=${token}`);
    if (response.data.token) {
      authStore.setToken(response.data.token);
      status.value = 'success';

      // Track successful email verification
      analytics.trackEmailVerified();

      // Note: Onboarding flag is NOT reset here!
      // OnboardingWelcome component will check if user has cars
      // and only show onboarding for truly new users (no cars yet)

      setTimeout(() => router.push('/dashboard'), 2000);
    }
  } catch (err: any) {
    const code = err.response?.data?.code;
    status.value = code === 'TOKEN_EXPIRED' ? 'expired' : 'invalid';
  }
});

const handleResend = async () => {
  resendError.value = '';
  if (!resendEmail.value) return;
  try {
    await api.post('/auth/resend-verification', { email: resendEmail.value });
    resendSent.value = true;
  } catch (err: any) {
    const code = err.response?.data?.code;
    resendError.value = code === 'RATE_LIMITED'
      ? 'Kurz warten – du hast gerade erst eine E-Mail angefordert.'
      : 'Fehler beim Senden. Bitte versuche es später erneut.';
  }
};
</script>

<template>
  <div class="flex items-center justify-center min-h-[80vh] bg-gray-100">
    <div class="w-full max-w-md p-8 bg-white rounded-xl shadow-lg text-center">

      <!-- Loading -->
      <div v-if="status === 'loading'">
        <BoltIcon class="h-16 w-16 text-green-600 mb-4 mx-auto" />
        <h2 class="text-xl font-semibold text-gray-700">Bestätigung wird geprüft...</h2>
      </div>

      <!-- Success -->
      <div v-else-if="status === 'success'">
        <div class="text-5xl mb-4">🎉</div>
        <h2 class="text-2xl font-bold text-green-600 mb-2">E-Mail bestätigt!</h2>
        <p class="text-gray-500">Du wirst gleich weitergeleitet...</p>
      </div>

      <!-- Expired -->
      <div v-else-if="status === 'expired'">
        <div class="text-5xl mb-4">⏰</div>
        <h2 class="text-2xl font-bold text-orange-500 mb-2">Link abgelaufen</h2>
        <p class="text-gray-500 mb-6">Der Bestätigungs-Link ist nicht mehr gültig. Fordere einen neuen an.</p>

        <div v-if="!resendSent">
          <input
            v-model="resendEmail"
            type="email"
            placeholder="deine@email.de"
            class="block w-full px-4 py-3 mb-3 border border-gray-300 rounded-lg focus:ring-indigo-500 focus:border-indigo-500"
          />
          <div v-if="resendError" class="text-sm text-red-600 bg-red-50 p-2 rounded mb-3">{{ resendError }}</div>
          <button
            @click="handleResend"
            class="w-full px-4 py-3 font-semibold text-white bg-indigo-600 rounded-lg hover:bg-indigo-700 transition"
          >
            Neuen Link senden
          </button>
        </div>
        <div v-else class="text-green-600 font-medium">
          ✅ E-Mail verschickt! Check dein Postfach.
        </div>
      </div>

      <!-- Invalid -->
      <div v-else-if="status === 'invalid'">
        <div class="text-5xl mb-4">❌</div>
        <h2 class="text-2xl font-bold text-red-500 mb-2">Ungültiger Link</h2>
        <p class="text-gray-500 mb-6">Dieser Bestätigungs-Link ist ungültig oder wurde bereits verwendet.</p>
        <router-link to="/register" class="text-indigo-600 font-semibold hover:underline">
          Zurück zur Registrierung
        </router-link>
      </div>

    </div>
  </div>
</template>
