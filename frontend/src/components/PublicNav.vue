<template>
  <nav v-if="!authStore.isAuthenticated()" class="bg-white dark:bg-gray-900 border-b border-gray-200 dark:border-gray-700 px-4 py-3">
    <div class="max-w-4xl mx-auto flex items-center justify-between gap-2">
      <a href="/" class="flex items-center gap-2 font-bold text-green-600 text-lg shrink-0 whitespace-nowrap">
        <BoltIcon class="h-6 w-6 shrink-0" />
        EV Monitor
      </a>
      <div class="flex items-center gap-2 min-w-0">
        <LocaleSwitcher class="hidden sm:flex" />
        <ThemeToggle class="text-gray-600 dark:text-gray-300" />
        <a :href="loginPath" class="hidden sm:inline text-sm text-gray-600 dark:text-gray-300 hover:text-gray-900 dark:hover:text-white">{{ t('nav.login') }}</a>
        <a :href="registerPath" class="text-sm bg-green-600 text-white px-3 py-1.5 rounded-lg hover:bg-green-700 whitespace-nowrap">
          {{ t('nav.register') }}
        </a>
      </div>
    </div>
  </nav>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '../stores/auth'
import { BoltIcon } from '@heroicons/vue/24/outline'
import ThemeToggle from './ThemeToggle.vue'
import LocaleSwitcher from './LocaleSwitcher.vue'

const { t, locale } = useI18n()
const authStore = useAuthStore()
const isEn = computed(() => locale.value === 'en')
const registerPath = computed(() => isEn.value ? '/en/register' : '/register')
const loginPath = computed(() => isEn.value ? '/en/login' : '/login')
</script>
