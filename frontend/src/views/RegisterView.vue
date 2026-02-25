<script setup lang="ts">
import { ref } from 'vue';
import { useAuthStore } from '../stores/auth';
import { useRouter } from 'vue-router';

const email = ref('');
const username = ref('');
const password = ref('');
const confirmPassword = ref('');
const authStore = useAuthStore();
const router = useRouter();
const error = ref('');

const handleRegister = async () => {
  // Validate username format
  if (!/^[a-zA-Z0-9_]{3,20}$/.test(username.value)) {
    error.value = 'Username muss 3-20 Zeichen lang sein und darf nur Buchstaben, Zahlen und Unterstriche enthalten';
    return;
  }

  if (password.value.length < 8) {
    error.value = 'Passwort muss mindestens 8 Zeichen lang sein';
    return;
  }

  if (password.value !== confirmPassword.value) {
    error.value = 'Passwörter stimmen nicht überein';
    return;
  }

  try {
    error.value = '';
    await authStore.register({ email: email.value, username: username.value, password: password.value });
    router.push('/');
  } catch (err: any) {
    error.value = err.response?.data?.message || 'Registrierung fehlgeschlagen';
  }
};
</script>

<template>
  <div class="flex items-center justify-center min-h-[80vh] bg-gray-100">
    <div class="w-full max-w-md p-8 bg-white rounded-xl shadow-lg">
      <h2 class="text-3xl font-bold text-center text-gray-800 mb-8">Registrieren</h2>
      <form @submit.prevent="handleRegister" class="space-y-6">
        <div>
          <label class="block text-sm font-medium text-gray-700">E-Mail</label>
          <input v-model="email" type="email" required class="block w-full px-4 py-3 mt-1 border border-gray-300 rounded-lg shadow-sm focus:ring-indigo-500 focus:border-indigo-500" />
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700">Username</label>
          <input v-model="username" type="text" required pattern="[a-zA-Z0-9_]{3,20}" minlength="3" maxlength="20" class="block w-full px-4 py-3 mt-1 border border-gray-300 rounded-lg shadow-sm focus:ring-indigo-500 focus:border-indigo-500" placeholder="z.B. max_mustermann" />
          <p class="text-xs text-gray-500 mt-1">3-20 Zeichen, nur Buchstaben, Zahlen und Unterstriche</p>
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700">Passwort</label>
          <input v-model="password" type="password" required class="block w-full px-4 py-3 mt-1 border border-gray-300 rounded-lg shadow-sm focus:ring-indigo-500 focus:border-indigo-500" />
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700">Passwort bestätigen</label>
          <input v-model="confirmPassword" type="password" required class="block w-full px-4 py-3 mt-1 border border-gray-300 rounded-lg shadow-sm focus:ring-indigo-500 focus:border-indigo-500" />
        </div>
        <div v-if="error" class="text-sm font-medium text-red-600 bg-red-50 p-3 rounded-lg">{{ error }}</div>
        <button type="submit" class="w-full px-4 py-3 font-semibold text-white bg-indigo-600 rounded-lg shadow hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2 transition">Konto erstellen</button>
      </form>

      <div class="mt-6 text-center text-sm text-gray-500">
        Bereits ein Konto? <router-link to="/login" class="font-semibold text-indigo-600 hover:text-indigo-500">Hier anmelden</router-link>
      </div>
    </div>
  </div>
</template>
