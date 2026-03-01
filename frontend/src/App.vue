<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from './stores/auth'
import SpritMonitorImport from './components/SpritMonitorImport.vue'
import LogFormModal from './components/LogFormModal.vue'
import FloatingActionButton from './components/FloatingActionButton.vue'

const router = useRouter()
const authStore = useAuthStore()
const showImportOverlay = ref(false)
const showLogFormModal = ref(false)

const handleLogout = () => {
  authStore.logout()
}

const handleNewLog = () => {
  // Check if desktop (≥768px) or mobile
  const isDesktop = window.innerWidth >= 768

  if (isDesktop) {
    showLogFormModal.value = true
  } else {
    router.push('/erfassen')
  }
}
</script>

<template>
  <div class="min-h-screen bg-gray-100 flex flex-col">
    <nav class="bg-indigo-600 shadow-md p-4 flex justify-between items-center text-white" v-if="authStore.isAuthenticated()">
      <div class="flex items-center space-x-6">
        <div class="text-xl font-bold tracking-wide">EV Monitor</div>
        <div class="flex space-x-4">
          <button
            @click="handleNewLog"
            class="px-3 py-2 rounded-md text-sm font-medium bg-green-600 hover:bg-green-700 transition">
            Ladevorgang erfassen
          </button>
          <router-link
            to="/statistics"
            class="px-3 py-2 rounded-md text-sm font-medium hover:bg-indigo-500 transition"
            :class="{ 'bg-indigo-700': $route.path === '/statistics' }">
            Statistiken
          </router-link>
          <router-link
            to="/cars"
            class="px-3 py-2 rounded-md text-sm font-medium hover:bg-indigo-500 transition"
            :class="{ 'bg-indigo-700': $route.path === '/cars' }">
            Fahrzeuge
          </router-link>
        </div>
      </div>
      <div class="flex items-center space-x-4">
        <button
          @click="showImportOverlay = true"
          class="px-4 py-2 bg-indigo-700 hover:bg-indigo-800 rounded-lg text-sm font-medium transition shadow-sm">
          📥 Import
        </button>
        <span v-if="authStore.user" class="text-sm border border-indigo-400 px-3 py-1 rounded-md bg-indigo-500 bg-opacity-30">
          {{ authStore.user.sub }}
        </span>
        <button @click="handleLogout" class="px-4 py-2 bg-indigo-700 hover:bg-indigo-800 rounded-lg text-sm font-medium transition shadow-sm">
          Abmelden
        </button>
      </div>
    </nav>
    <main :class="{ 'py-10 px-4': authStore.isAuthenticated() }">
      <router-view></router-view>
    </main>

    <!-- Footer (only for authenticated users) -->
    <footer v-if="authStore.isAuthenticated()" class="bg-gray-50 border-t border-gray-200 py-6 mt-auto">
      <div class="container mx-auto px-4">
        <div class="text-center text-sm text-gray-600 space-y-3">
          <p>
            © 2026 EV Monitor ·
            <router-link to="/datenschutz" class="hover:text-green-600 underline">Datenschutz</router-link> ·
            <router-link to="/impressum" class="hover:text-green-600 underline">Impressum</router-link> ·
            <router-link to="/agb" class="hover:text-green-600 underline">AGB</router-link>
          </p>
          <p class="text-xs text-gray-500 flex items-center justify-center gap-2">
            <span>🔌 Datenimport powered by</span>
            <a
              href="https://www.spritmonitor.de"
              target="_blank"
              rel="noopener noreferrer"
              class="text-green-600 hover:text-green-700 hover:underline font-medium">
              Sprit-Monitor API
            </a>
          </p>
        </div>
      </div>
    </footer>

    <!-- Sprit-Monitor Import Overlay -->
    <SpritMonitorImport v-if="showImportOverlay" @close="showImportOverlay = false" />

    <!-- Floating Action Button (only when authenticated) -->
    <FloatingActionButton v-if="authStore.isAuthenticated()" @click="handleNewLog" />

    <!-- Log Form Modal (Desktop only) -->
    <LogFormModal v-if="showLogFormModal && authStore.isAuthenticated()" @close="showLogFormModal = false" />
  </div>
</template>
