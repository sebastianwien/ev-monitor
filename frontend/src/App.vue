<script setup lang="ts">
import { ref } from 'vue';
import { useAuthStore } from './stores/auth';
import SpritMonitorImport from './components/SpritMonitorImport.vue';

const authStore = useAuthStore();
const showImportOverlay = ref(false);

const handleLogout = () => {
  authStore.logout();
};
</script>

<template>
  <div class="min-h-screen bg-gray-100 flex flex-col">
    <nav class="bg-indigo-600 shadow-md p-4 flex justify-between items-center text-white" v-if="authStore.isAuthenticated()">
      <div class="flex items-center space-x-6">
        <div class="text-xl font-bold tracking-wide">EV Monitor</div>
        <div class="flex space-x-4">
          <router-link
            to="/dashboard"
            class="px-3 py-2 rounded-md text-sm font-medium hover:bg-indigo-500 transition"
            :class="{ 'bg-indigo-700': $route.path === '/dashboard' }">
            Dashboard
          </router-link>
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

    <!-- Footer -->
    <footer class="bg-gray-50 border-t border-gray-200 py-6 mt-auto">
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
  </div>
</template>
