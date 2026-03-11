<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import axios from 'axios'

const router = useRouter()
const authStore = useAuthStore()

const email = ref('')
const internalToken = ref('')
const error = ref('')
const loading = ref(false)

const impersonate = async () => {
  if (!email.value || !internalToken.value) {
    error.value = 'Email und Internal Token erforderlich.'
    return
  }

  loading.value = true
  error.value = ''

  try {
    const baseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api'
    const response = await axios.post(
      `${baseUrl}/internal/impersonate`,
      { email: email.value },
      { headers: { 'X-Internal-Token': internalToken.value } }
    )

    authStore.setToken(response.data.token)
    sessionStorage.setItem('impersonating', email.value)
    router.push('/dashboard')
  } catch (e: any) {
    error.value = e.response?.data?.error || 'Fehler beim Impersonieren.'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="min-h-screen bg-gray-900 flex items-center justify-center p-4">
    <div class="bg-white rounded-xl shadow-2xl max-w-md w-full p-8">
      <div class="mb-6">
        <h1 class="text-2xl font-bold text-gray-900">Admin: Als User einloggen</h1>
        <p class="text-sm text-gray-500 mt-1">Generiert ein 1h-Token für den Ziel-User.</p>
      </div>

      <div v-if="error" class="mb-4 p-3 bg-red-50 border border-red-200 text-red-700 text-sm rounded-lg">
        {{ error }}
      </div>

      <div class="space-y-4">
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">User-Email</label>
          <input
            v-model="email"
            type="email"
            placeholder="user@example.com"
            class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500" />
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Internal Token (X-Internal-Token)</label>
          <input
            v-model="internalToken"
            type="password"
            placeholder="dev-internal-token-..."
            class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500" />
        </div>
        <button
          @click="impersonate"
          :disabled="loading"
          class="w-full py-2.5 bg-indigo-600 text-white font-semibold rounded-lg hover:bg-indigo-700 disabled:opacity-50 transition flex items-center justify-center gap-2">
          <svg v-if="loading" class="animate-spin h-4 w-4" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
            <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
            <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
          </svg>
          {{ loading ? 'Wird eingeloggt…' : 'Als User einloggen' }}
        </button>
      </div>
    </div>
  </div>
</template>
