<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { UserIcon, KeyIcon, TrashIcon, ArrowDownTrayIcon, CurrencyDollarIcon, AcademicCapIcon } from '@heroicons/vue/24/outline'
import api from '../api/axios'

const router = useRouter()
const authStore = useAuthStore()

// Account Data
const email = ref('')
const username = ref('')
const registeredSince = ref('')
const totalLogs = ref(0)
const totalKwh = ref(0)
const totalCostEur = ref(0)

// Coin Balance
const coinBalance = ref(0)

// Forms
const showEmailForm = ref(false)
const showUsernameForm = ref(false)
const showPasswordForm = ref(false)

const newEmail = ref('')
const newUsername = ref('')
const currentPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')

// Delete Account
const showDeleteConfirm = ref(false)
const deletePassword = ref('')

// Loading & Messages
const loading = ref(false)
const message = ref<{ type: 'success' | 'error', text: string } | null>(null)

// Fetch user data
const fetchUserData = async () => {
  try {
    // Get user from JWT token (already decoded in authStore)
    const user = authStore.user
    if (user) {
      email.value = user.email || user.sub || ''
      username.value = user.username || user.email?.split('@')[0] || ''
    }

    // Fetch stats and coins from API
    const [statsRes, coinsRes] = await Promise.all([
      api.get('/users/me/stats'),
      api.get('/coins/balance')
    ])

    const stats = statsRes.data
    registeredSince.value = new Date(stats.registeredSince).toLocaleDateString('de-DE')
    totalLogs.value = stats.totalLogs
    totalKwh.value = stats.totalKwh
    totalCostEur.value = stats.totalCostEur

    coinBalance.value = coinsRes.data.total || 0
  } catch (error: any) {
    console.error('Failed to fetch user data:', error)
  }
}

// Change Email
const changeEmail = async () => {
  if (!newEmail.value) return

  loading.value = true
  message.value = null

  try {
    await api.put('/users/me/email', { newEmail: newEmail.value })
    message.value = { type: 'success', text: 'Email erfolgreich geändert! Bitte bestätige die neue Email-Adresse.' }

    // Update local value immediately
    email.value = newEmail.value

    showEmailForm.value = false
    newEmail.value = ''
  } catch (error: any) {
    message.value = { type: 'error', text: error.response?.data?.message || 'Email-Änderung fehlgeschlagen' }
  } finally {
    loading.value = false
  }
}

// Change Username
const changeUsername = async () => {
  if (!newUsername.value) return

  loading.value = true
  message.value = null

  try {
    await api.put('/users/me/username', { newUsername: newUsername.value })
    message.value = { type: 'success', text: 'Username erfolgreich geändert!' }

    // Update local value immediately
    username.value = newUsername.value

    showUsernameForm.value = false
    newUsername.value = ''
  } catch (error: any) {
    message.value = { type: 'error', text: error.response?.data?.message || 'Username-Änderung fehlgeschlagen' }
  } finally {
    loading.value = false
  }
}

// Change Password
const changePassword = async () => {
  if (!currentPassword.value || !newPassword.value || !confirmPassword.value) {
    message.value = { type: 'error', text: 'Bitte alle Felder ausfüllen' }
    return
  }

  if (newPassword.value !== confirmPassword.value) {
    message.value = { type: 'error', text: 'Passwörter stimmen nicht überein' }
    return
  }

  if (newPassword.value.length < 8) {
    message.value = { type: 'error', text: 'Passwort muss mindestens 8 Zeichen lang sein' }
    return
  }

  loading.value = true
  message.value = null

  try {
    await api.put('/users/me/password', {
      currentPassword: currentPassword.value,
      newPassword: newPassword.value
    })
    message.value = { type: 'success', text: 'Passwort erfolgreich geändert!' }
    showPasswordForm.value = false
    currentPassword.value = ''
    newPassword.value = ''
    confirmPassword.value = ''
  } catch (error: any) {
    message.value = { type: 'error', text: error.response?.data?.message || 'Passwort-Änderung fehlgeschlagen' }
  } finally {
    loading.value = false
  }
}

// Export Data
const exportData = async () => {
  loading.value = true
  message.value = null

  try {
    const response = await api.get('/users/me/export', { responseType: 'blob' })

    // Create download link
    const url = window.URL.createObjectURL(new Blob([response.data]))
    const link = document.createElement('a')
    link.href = url
    link.setAttribute('download', `ev-monitor-export-${Date.now()}.json`)
    document.body.appendChild(link)
    link.click()
    link.remove()

    message.value = { type: 'success', text: 'Daten erfolgreich exportiert!' }
  } catch (error: any) {
    message.value = { type: 'error', text: error.response?.data?.message || 'Daten-Export fehlgeschlagen' }
  } finally {
    loading.value = false
  }
}

// Delete Account
const deleteAccount = async () => {
  if (!deletePassword.value) {
    message.value = { type: 'error', text: 'Bitte Passwort eingeben' }
    return
  }

  loading.value = true
  message.value = null

  try {
    await api.delete('/users/me', {
      data: { password: deletePassword.value }
    })

    // Logout and redirect
    authStore.logout()
    router.push('/login')
  } catch (error: any) {
    message.value = { type: 'error', text: error.response?.data?.message || 'Account-Löschung fehlgeschlagen' }
    loading.value = false
  }
}

// Restart Onboarding Tutorial
const restartOnboarding = () => {
  localStorage.removeItem('onboarding-completed')
  localStorage.setItem('onboarding-force', 'true')
  message.value = { type: 'success', text: 'Tutorial wird neu gestartet...' }
  setTimeout(() => {
    window.location.reload()
  }, 1000)
}

onMounted(() => {
  fetchUserData()
})
</script>

<template>
  <div class="md:max-w-4xl md:mx-auto md:p-6">
    <div class="bg-white md:rounded-xl md:shadow-lg p-4 md:p-6">
      <!-- Header -->
      <div class="flex items-center gap-3 mb-6">
        <UserIcon class="h-8 w-8 text-gray-700" />
        <h1 class="text-3xl font-bold text-gray-800">Einstellungen</h1>
      </div>

      <!-- Message Banner -->
      <div v-if="message" :class="[
        'mb-6 p-4 rounded-lg',
        message.type === 'success' ? 'bg-green-50 border border-green-200 text-green-700' : 'bg-red-50 border border-red-200 text-red-700'
      ]">
        {{ message.text }}
      </div>

      <!-- Coin Balance -->
      <div class="mb-8 bg-gradient-to-r from-amber-50 to-yellow-50 border border-amber-200 rounded-lg p-6">
        <div class="flex items-center gap-3 mb-2">
          <CurrencyDollarIcon class="h-6 w-6 text-amber-600" />
          <h2 class="text-xl font-bold text-gray-800">Deine Coins</h2>
        </div>
        <p class="text-3xl font-bold text-amber-600">{{ coinBalance }}</p>
        <router-link to="/coins/history" class="text-sm text-amber-700 hover:text-amber-800 underline mt-2 inline-block">
          Coin-Historie ansehen →
        </router-link>
      </div>

      <!-- Account Section -->
      <div class="mb-8">
        <h2 class="text-xl font-bold text-gray-800 mb-4 flex items-center gap-2">
          <UserIcon class="h-6 w-6" />
          Account
        </h2>

        <!-- Email -->
        <div class="mb-4 p-4 bg-gray-50 rounded-lg">
          <div class="flex justify-between items-center">
            <div>
              <p class="text-sm text-gray-600">Email</p>
              <p class="font-medium">{{ email }}</p>
            </div>
            <button
              @click="showEmailForm = !showEmailForm"
              class="px-3 py-1 text-sm bg-indigo-600 text-white rounded-md hover:bg-indigo-700 transition">
              Ändern
            </button>
          </div>

          <div v-if="showEmailForm" class="mt-4 pt-4 border-t border-gray-200">
            <input
              v-model="newEmail"
              type="email"
              placeholder="Neue Email-Adresse"
              class="w-full px-3 py-2 border border-gray-300 rounded-md focus:ring-indigo-500 focus:border-indigo-500 mb-3">
            <div class="flex gap-2">
              <button
                @click="changeEmail"
                :disabled="loading"
                class="px-4 py-2 bg-green-600 text-white rounded-md hover:bg-green-700 disabled:opacity-50 transition">
                Speichern
              </button>
              <button
                @click="showEmailForm = false; newEmail = ''"
                class="px-4 py-2 bg-gray-300 text-gray-700 rounded-md hover:bg-gray-400 transition">
                Abbrechen
              </button>
            </div>
          </div>
        </div>

        <!-- Username -->
        <div class="mb-4 p-4 bg-gray-50 rounded-lg">
          <div class="flex justify-between items-center">
            <div>
              <p class="text-sm text-gray-600">Username</p>
              <p class="font-medium">{{ username }}</p>
            </div>
            <button
              @click="showUsernameForm = !showUsernameForm"
              class="px-3 py-1 text-sm bg-indigo-600 text-white rounded-md hover:bg-indigo-700 transition">
              Ändern
            </button>
          </div>

          <div v-if="showUsernameForm" class="mt-4 pt-4 border-t border-gray-200">
            <input
              v-model="newUsername"
              type="text"
              placeholder="Neuer Username"
              class="w-full px-3 py-2 border border-gray-300 rounded-md focus:ring-indigo-500 focus:border-indigo-500 mb-3">
            <div class="flex gap-2">
              <button
                @click="changeUsername"
                :disabled="loading"
                class="px-4 py-2 bg-green-600 text-white rounded-md hover:bg-green-700 disabled:opacity-50 transition">
                Speichern
              </button>
              <button
                @click="showUsernameForm = false; newUsername = ''"
                class="px-4 py-2 bg-gray-300 text-gray-700 rounded-md hover:bg-gray-400 transition">
                Abbrechen
              </button>
            </div>
          </div>
        </div>

        <!-- Password -->
        <div class="mb-4 p-4 bg-gray-50 rounded-lg">
          <div class="flex justify-between items-center">
            <div>
              <p class="text-sm text-gray-600">Passwort</p>
              <p class="font-medium">••••••••••</p>
            </div>
            <button
              @click="showPasswordForm = !showPasswordForm"
              class="px-3 py-1 text-sm bg-indigo-600 text-white rounded-md hover:bg-indigo-700 transition">
              Ändern
            </button>
          </div>

          <div v-if="showPasswordForm" class="mt-4 pt-4 border-t border-gray-200 space-y-3">
            <input
              v-model="currentPassword"
              type="password"
              placeholder="Aktuelles Passwort"
              class="w-full px-3 py-2 border border-gray-300 rounded-md focus:ring-indigo-500 focus:border-indigo-500">
            <input
              v-model="newPassword"
              type="password"
              placeholder="Neues Passwort (min. 8 Zeichen)"
              class="w-full px-3 py-2 border border-gray-300 rounded-md focus:ring-indigo-500 focus:border-indigo-500">
            <input
              v-model="confirmPassword"
              type="password"
              placeholder="Neues Passwort bestätigen"
              class="w-full px-3 py-2 border border-gray-300 rounded-md focus:ring-indigo-500 focus:border-indigo-500">
            <div class="flex gap-2">
              <button
                @click="changePassword"
                :disabled="loading"
                class="px-4 py-2 bg-green-600 text-white rounded-md hover:bg-green-700 disabled:opacity-50 transition">
                Speichern
              </button>
              <button
                @click="showPasswordForm = false; currentPassword = ''; newPassword = ''; confirmPassword = ''"
                class="px-4 py-2 bg-gray-300 text-gray-700 rounded-md hover:bg-gray-400 transition">
                Abbrechen
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- Data & Privacy Section -->
      <div class="mb-8">
        <h2 class="text-xl font-bold text-gray-800 mb-4 flex items-center gap-2">
          <KeyIcon class="h-6 w-6" />
          Daten & Privacy
        </h2>

        <!-- Stats -->
        <div class="mb-4 p-4 bg-gray-50 rounded-lg">
          <p class="text-sm text-gray-600 mb-2">Registriert seit</p>
          <p class="font-medium mb-3">{{ registeredSince }}</p>

          <p class="text-sm text-gray-600">
            <strong>{{ totalLogs }}</strong> Ladevorgänge ·
            <strong>{{ Math.round(totalKwh) }}</strong> kWh ·
            <strong>€{{ totalCostEur.toFixed(2) }}</strong>
          </p>
        </div>

        <!-- Export Data -->
        <button
          @click="exportData"
          :disabled="loading"
          class="w-full mb-4 flex items-center justify-center gap-2 px-4 py-3 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 disabled:opacity-50 transition">
          <ArrowDownTrayIcon class="h-5 w-5" />
          <span>Meine Daten exportieren (JSON)</span>
        </button>

        <!-- Delete Account -->
        <button
          @click="showDeleteConfirm = true"
          class="w-full flex items-center justify-center gap-2 px-4 py-3 bg-red-600 text-white rounded-lg hover:bg-red-700 transition">
          <TrashIcon class="h-5 w-5" />
          <span>Account unwiderruflich löschen</span>
        </button>
      </div>

      <!-- Help & Support Section -->
      <div class="mb-8">
        <h2 class="text-xl font-bold text-gray-800 mb-4 flex items-center gap-2">
          <AcademicCapIcon class="h-6 w-6" />
          Hilfe & Support
        </h2>

        <div class="p-4 bg-gradient-to-r from-indigo-50 to-purple-50 border border-indigo-200 rounded-lg">
          <h3 class="font-semibold text-gray-800 mb-2 flex items-center gap-2">
            <span class="text-xl">👋</span>
            Tutorial erneut ansehen
          </h3>
          <p class="text-sm text-gray-600 mb-4">
            Möchtest du das Willkommens-Tutorial nochmal durchgehen? Perfekt wenn du eine Auffrischung brauchst oder neue Features kennenlernen willst.
          </p>
          <button
            @click="restartOnboarding"
            class="w-full flex items-center justify-center gap-2 px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition">
            <AcademicCapIcon class="h-5 w-5" />
            <span>Tutorial neu starten</span>
          </button>
        </div>
      </div>

      <!-- Delete Confirmation Modal -->
      <div
        v-if="showDeleteConfirm"
        class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-sm"
        @click.self="showDeleteConfirm = false">
        <div class="bg-white rounded-2xl shadow-2xl max-w-md w-full p-6">
          <h3 class="text-2xl font-bold text-red-600 mb-4">Account löschen?</h3>
          <p class="text-gray-700 mb-4">
            Diese Aktion kann <strong>nicht rückgängig</strong> gemacht werden.
            Alle deine Daten (Ladevorgänge, Fahrzeuge, Coins) werden permanent gelöscht.
          </p>

          <input
            v-model="deletePassword"
            type="password"
            placeholder="Passwort zur Bestätigung"
            class="w-full px-3 py-2 border border-gray-300 rounded-md focus:ring-red-500 focus:border-red-500 mb-4">

          <div class="flex gap-3">
            <button
              @click="deleteAccount"
              :disabled="loading || !deletePassword"
              class="flex-1 px-4 py-2 bg-red-600 text-white rounded-md hover:bg-red-700 disabled:opacity-50 transition">
              Endgültig löschen
            </button>
            <button
              @click="showDeleteConfirm = false; deletePassword = ''"
              class="flex-1 px-4 py-2 bg-gray-300 text-gray-700 rounded-md hover:bg-gray-400 transition">
              Abbrechen
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
