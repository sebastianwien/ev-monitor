<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import api from '../api/axios';

const route = useRoute();
const router = useRouter();

const token = ref('');
const newPassword = ref('');
const confirmPassword = ref('');
const loading = ref(false);
const success = ref(false);
const error = ref('');

onMounted(() => {
  token.value = (route.query.token as string) || '';
  if (!token.value) {
    error.value = 'Ungültiger Link. Bitte fordere einen neuen Reset-Link an.';
  }
});

const handleSubmit = async () => {
  error.value = '';

  if (newPassword.value.length < 8) {
    error.value = 'Das Passwort muss mindestens 8 Zeichen lang sein.';
    return;
  }
  if (newPassword.value !== confirmPassword.value) {
    error.value = 'Die Passwörter stimmen nicht überein.';
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
      error.value = 'Der Reset-Link ist abgelaufen. Bitte fordere einen neuen an.';
    } else if (code === 'INVALID_TOKEN') {
      error.value = 'Ungültiger Reset-Link. Bitte fordere einen neuen an.';
    } else {
      error.value = 'Ein Fehler ist aufgetreten. Bitte versuche es erneut.';
    }
  } finally {
    loading.value = false;
  }
};
</script>

<template>
  <div class="flex items-center justify-center min-h-[80vh] bg-gray-100">
    <div class="w-full max-w-md p-8 bg-white rounded-xl shadow-lg">
      <h2 class="text-3xl font-bold text-center text-gray-800 mb-8">Neues Passwort</h2>

      <!-- Success State -->
      <div v-if="success" class="text-center">
        <div class="w-16 h-16 bg-green-100 rounded-full flex items-center justify-center mx-auto mb-4">
          <svg class="w-8 h-8 text-green-600" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" d="M9 12.75 11.25 15 15 9.75M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z" />
          </svg>
        </div>
        <p class="text-gray-700 font-medium mb-2">Passwort erfolgreich geändert!</p>
        <p class="text-gray-500 text-sm">Du wirst in Kürze zum Login weitergeleitet...</p>
      </div>

      <!-- Form State -->
      <form v-else @submit.prevent="handleSubmit" class="space-y-6">
        <div>
          <label class="block text-sm font-medium text-gray-700">Neues Passwort</label>
          <input
            v-model="newPassword"
            type="password"
            required
            autocomplete="new-password"
            class="block w-full px-4 py-3 mt-1 border border-gray-300 rounded-lg shadow-sm focus:ring-indigo-500 focus:border-indigo-500"
            placeholder="Mindestens 8 Zeichen"
          />
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700">Passwort bestätigen</label>
          <input
            v-model="confirmPassword"
            type="password"
            required
            autocomplete="new-password"
            class="block w-full px-4 py-3 mt-1 border border-gray-300 rounded-lg shadow-sm focus:ring-indigo-500 focus:border-indigo-500"
            placeholder="Passwort wiederholen"
          />
        </div>
        <div v-if="error" class="text-sm text-red-600 bg-red-50 p-3 rounded-lg">
          {{ error }}
          <div v-if="error.includes('abgelaufen') || error.includes('Ungültiger')" class="mt-2">
            <router-link to="/forgot-password" class="font-semibold underline hover:no-underline">
              Neuen Reset-Link anfordern
            </router-link>
          </div>
        </div>
        <button
          type="submit"
          :disabled="loading || !token"
          class="w-full px-4 py-3 font-semibold text-white bg-indigo-600 rounded-lg shadow hover:bg-indigo-700 disabled:bg-gray-300 transition"
        >
          {{ loading ? 'Wird gespeichert...' : 'Passwort ändern' }}
        </button>
      </form>
    </div>
  </div>
</template>
