<template>
  <nav v-if="!authStore.isAuthenticated()" class="bg-white dark:bg-gray-900 border-b border-gray-200 dark:border-gray-700 px-4 py-3">
    <div class="max-w-4xl mx-auto flex items-center justify-between">
      <a href="/" class="flex items-center gap-2 font-bold text-green-600 text-lg">
        <BoltIcon class="h-6 w-6" />
        EV Monitor
      </a>
      <div class="flex items-center gap-3">
        <ThemeToggle class="text-gray-600 dark:text-gray-300" />
        <a href="/login" class="text-sm text-gray-600 dark:text-gray-300 hover:text-gray-900 dark:hover:text-white">Anmelden</a>
        <a href="/register" class="text-sm bg-green-600 text-white px-3 py-1.5 rounded-lg hover:bg-green-700 whitespace-nowrap">
          Registrieren
        </a>
      </div>
    </div>
  </nav>
</template>

<script setup lang="ts">
import { useAuthStore } from '../stores/auth'
import { BoltIcon } from '@heroicons/vue/24/outline'
import ThemeToggle from './ThemeToggle.vue'

const authStore = useAuthStore()
</script>
