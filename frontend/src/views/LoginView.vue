<script setup lang="ts">
import { ref } from 'vue';
import { useAuthStore } from '../stores/auth';
import { useRouter } from 'vue-router';

const email = ref('');
const password = ref('');
const authStore = useAuthStore();
const router = useRouter();
const error = ref('');

const handleLogin = async () => {
  try {
    error.value = '';
    await authStore.login({ email: email.value, password: password.value });
    router.push('/');
  } catch (err: any) {
    error.value = 'Ungültige E-Mail oder Passwort';
  }
};

const loginWithGoogle = () => {
  window.location.href = 'http://localhost:8080/oauth2/authorization/google';
};
const loginWithFacebook = () => {
  window.location.href = 'http://localhost:8080/oauth2/authorization/facebook';
};
const loginWithApple = () => {
  window.location.href = 'http://localhost:8080/oauth2/authorization/apple';
};
</script>

<template>
  <div class="flex items-center justify-center min-h-[80vh] bg-gray-100">
    <div class="w-full max-w-md p-8 bg-white rounded-xl shadow-lg">
      <h2 class="text-3xl font-bold text-center text-gray-800 mb-8">Anmelden</h2>
      <form @submit.prevent="handleLogin" class="space-y-6">
        <div>
          <label class="block text-sm font-medium text-gray-700">E-Mail</label>
          <input v-model="email" type="email" required class="block w-full px-4 py-3 mt-1 border border-gray-300 rounded-lg shadow-sm focus:ring-indigo-500 focus:border-indigo-500" />
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700">Passwort</label>
          <input v-model="password" type="password" required class="block w-full px-4 py-3 mt-1 border border-gray-300 rounded-lg shadow-sm focus:ring-indigo-500 focus:border-indigo-500" />
        </div>
        <div v-if="error" class="text-sm font-medium text-red-600 bg-red-50 p-3 rounded-lg">{{ error }}</div>
        <button type="submit" class="w-full px-4 py-3 font-semibold text-white bg-indigo-600 rounded-lg shadow hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2 transition">Anmelden</button>
      </form>

      <div class="mt-6 text-center text-sm text-gray-500">
        Noch kein Konto? <router-link to="/register" class="font-semibold text-indigo-600 hover:text-indigo-500">Hier registrieren</router-link>
      </div>

      <div class="mt-8">
        <div class="relative">
          <div class="absolute inset-0 flex items-center">
            <div class="w-full border-t border-gray-300"></div>
          </div>
          <div class="relative flex justify-center text-sm">
            <span class="px-2 text-gray-500 bg-white">Oder anmelden mit</span>
          </div>
        </div>

        <div class="grid grid-cols-3 gap-3 mt-6">
          <button @click="loginWithGoogle" class="flex justify-center w-full px-4 py-2 text-sm font-medium text-gray-700 bg-white border border-gray-300 rounded-md shadow-sm hover:bg-gray-50">
            <span class="sr-only">Mit Google anmelden</span>
            Google
          </button>
          <button @click="loginWithFacebook" class="flex justify-center w-full px-4 py-2 text-sm font-medium text-gray-700 bg-white border border-gray-300 rounded-md shadow-sm hover:bg-gray-50">
            <span class="sr-only">Mit Facebook anmelden</span>
            Facebook
          </button>
          <button @click="loginWithApple" class="flex justify-center w-full px-4 py-2 text-sm font-medium text-gray-700 bg-white border border-gray-300 rounded-md shadow-sm hover:bg-gray-50">
            <span class="sr-only">Mit Apple anmelden</span>
            Apple
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
