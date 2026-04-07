<template>
  <div class="flex items-center gap-1 text-sm font-medium">
    <a
      :href="getAlternateUrl('de')"
      @click.prevent="switchLocale('de')"
      :class="[
        'px-1.5 py-0.5 rounded transition-colors cursor-pointer',
        variant === 'nav'
          ? locale === 'de' ? 'text-white font-semibold' : 'text-indigo-300 hover:text-white'
          : locale === 'de' ? 'text-gray-900 dark:text-gray-100 font-semibold' : 'text-gray-400 dark:text-gray-500 hover:text-gray-700 dark:hover:text-gray-300'
      ]"
    >DE</a>
    <span :class="variant === 'nav' ? 'text-indigo-400' : 'text-gray-300 dark:text-gray-600'">|</span>
    <a
      :href="getAlternateUrl('en')"
      @click.prevent="switchLocale('en')"
      :class="[
        'px-1.5 py-0.5 rounded transition-colors cursor-pointer',
        variant === 'nav'
          ? locale === 'en' ? 'text-white font-semibold' : 'text-indigo-300 hover:text-white'
          : locale === 'en' ? 'text-gray-900 dark:text-gray-100 font-semibold' : 'text-gray-400 dark:text-gray-500 hover:text-gray-700 dark:hover:text-gray-300'
      ]"
    >EN</a>
  </div>
</template>

<script setup lang="ts">
import { useLocaleFormat, useLocaleRoutes } from '../composables/useLocaleFormat'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'

withDefaults(defineProps<{ variant?: 'nav' | 'public' }>(), { variant: 'public' })

const { locale } = useLocaleFormat()
const { getAlternateUrl } = useLocaleRoutes()
const { locale: i18nLocale } = useI18n()
const route = useRoute()

function switchLocale(targetLocale: 'de' | 'en') {
  i18nLocale.value = targetLocale
  localStorage.setItem('ev-locale', targetLocale)

  if (!route.meta.requiresAuth) {
    location.href = getAlternateUrl(targetLocale)
  }
}
</script>
