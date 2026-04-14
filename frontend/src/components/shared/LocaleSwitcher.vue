<template>
  <div class="flex items-center gap-1 text-sm font-medium">
    <template v-for="(opt, idx) in localeOptions" :key="opt.value">
      <span
        v-if="idx > 0"
        :class="variant === 'nav' ? 'text-indigo-400' : 'text-gray-300 dark:text-gray-600'"
      >|</span>
      <a
        :href="getHref(opt.value)"
        @click.prevent="switchLocale(opt.value)"
        :class="[
          'px-1.5 py-0.5 rounded transition-colors cursor-pointer',
          variant === 'nav'
            ? currentLocale === opt.value ? 'text-white font-semibold' : 'text-indigo-300 hover:text-white'
            : currentLocale === opt.value ? 'text-gray-900 dark:text-gray-100 font-semibold' : 'text-gray-400 dark:text-gray-500 hover:text-gray-700 dark:hover:text-gray-300'
        ]"
      >{{ opt.label }}</a>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useLocaleRoutes } from '../../composables/useLocaleFormat'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { setLocale, type AppLocale } from '../../i18n'

withDefaults(defineProps<{ variant?: 'nav' | 'public' }>(), { variant: 'public' })

const { locale: i18nLocale } = useI18n()
const { getAlternateUrl } = useLocaleRoutes()
const route = useRoute()

const currentLocale = computed(() => i18nLocale.value as AppLocale)
const isSwitching = ref(false)

// Always show DE and EN. Show NB/SV only when the user is currently on one of those locales
// (auto-detected), so they can see their active locale and switch away.
const localeOptions = computed(() => {
  const options: { value: AppLocale; label: string }[] = [
    { value: 'de', label: 'DE' },
    { value: 'en', label: 'EN' },
  ]
  if (currentLocale.value === 'nb') options.unshift({ value: 'nb', label: 'NB' })
  if (currentLocale.value === 'sv') options.unshift({ value: 'sv', label: 'SV' })
  return options
})

function getHref(locale: AppLocale): string {
  if (locale === 'de' || locale === 'en') return getAlternateUrl(locale)
  // NB/SV have no dedicated URL paths yet - in-place locale switch
  return '#'
}

async function switchLocale(locale: AppLocale) {
  if (isSwitching.value || locale === currentLocale.value) return
  isSwitching.value = true
  try {
    await setLocale(locale)
    if ((locale === 'de' || locale === 'en') && !route.meta.requiresAuth) {
      location.href = getAlternateUrl(locale)
    }
  } finally {
    isSwitching.value = false
  }
}
</script>
