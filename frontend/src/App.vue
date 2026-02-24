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
  <div class="min-h-screen bg-gray-100">
    <nav class="bg-indigo-600 shadow-md p-4 flex justify-between items-center text-white" v-if="authStore.isAuthenticated()">
      <div class="flex items-center space-x-6">
        <div class="text-xl font-bold tracking-wide">EV Monitor</div>
        <div class="flex space-x-4">
          <router-link
            to="/"
            class="px-3 py-2 rounded-md text-sm font-medium hover:bg-indigo-500 transition"
            :class="{ 'bg-indigo-700': $route.path === '/' }">
            Dashboard
          </router-link>
          <router-link
            to="/statistics"
            class="px-3 py-2 rounded-md text-sm font-medium hover:bg-indigo-500 transition"
            :class="{ 'bg-indigo-700': $route.path === '/statistics' }">
            Statistics
          </router-link>
          <router-link
            to="/cars"
            class="px-3 py-2 rounded-md text-sm font-medium hover:bg-indigo-500 transition"
            :class="{ 'bg-indigo-700': $route.path === '/cars' }">
            My Vehicles
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
          Logout
        </button>
      </div>
    </nav>
    <main :class="{ 'py-10 px-4': authStore.isAuthenticated() }">
      <router-view></router-view>
    </main>

    <!-- Sprit-Monitor Import Overlay -->
    <SpritMonitorImport v-if="showImportOverlay" @close="showImportOverlay = false" />
  </div>
</template>
