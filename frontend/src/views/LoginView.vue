<script setup lang="ts">
import { ref } from 'vue';
import { useAuthStore } from '../stores/auth';
import { useRouter } from 'vue-router';

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
  <div class="flex items-center justify-center min-h-[80vh] bg-gray-100">
    <div class="w-full max-w-md p-8 bg-white rounded-xl shadow-lg">
      <h2 class="text-3xl font-bold text-center text-gray-800 mb-8">Anmelden</h2>
      <form @submit.prevent="handleLogin" class="space-y-6">
        <div>
          <label class="block text-sm font-medium text-gray-700">E-Mail oder Username</label>
          <input v-model="email" type="text" required class="block w-full px-4 py-3 mt-1 border border-gray-300 rounded-lg shadow-sm focus:ring-indigo-500 focus:border-indigo-500" placeholder="deine@email.de oder username" />
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700">Passwort</label>
          <input v-model="password" type="password" required class="block w-full px-4 py-3 mt-1 border border-gray-300 rounded-lg shadow-sm focus:ring-indigo-500 focus:border-indigo-500" />
        </div>
        <div v-if="error" class="text-sm font-medium bg-red-50 p-3 rounded-lg" :class="errorCode === 'EMAIL_NOT_VERIFIED' ? 'text-orange-700' : 'text-red-600'">
          {{ error }}
          <div v-if="errorCode === 'EMAIL_NOT_VERIFIED'" class="mt-2">
            <span v-if="!resendSent">
              <button @click="handleResendFromLogin" class="font-semibold underline hover:no-underline">
                Bestätigungs-E-Mail erneut senden
              </button>
            </span>
            <span v-else class="text-green-700 font-medium">✅ E-Mail verschickt!</span>
          </div>
        </div>
        <button type="submit" class="w-full px-4 py-3 font-semibold text-white bg-indigo-600 rounded-lg shadow hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2 transition">Anmelden</button>
      </form>

      <div class="mt-4 text-center">
        <router-link to="/forgot-password" class="text-sm text-gray-400 hover:text-indigo-600">Passwort vergessen?</router-link>
      </div>

      <div class="mt-4 text-center text-sm text-gray-500">
        Noch kein Konto? <router-link to="/register" class="font-semibold text-indigo-600 hover:text-indigo-500">Hier registrieren</router-link>
      </div>
    </div>
  </div>
</template>
