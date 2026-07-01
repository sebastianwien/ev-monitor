<template>
  <div v-if="!authStore.isAuthenticated()" class="sticky top-0 z-50">
    <div class="public-nav-accent h-1 bg-gradient-to-r from-green-400 via-emerald-500 to-green-600"></div>
    <nav class="pt-[env(safe-area-inset-top)] bg-green-50/95 dark:bg-green-950/80 backdrop-blur-md border-b-2 border-gray-900 dark:border-gray-800 shadow-sm">
    <div class="max-w-7xl mx-auto px-4 sm:px-8">
      <div class="public-nav-row flex justify-between items-center h-16">
        <a href="/" class="flex items-center gap-2 shrink-0 whitespace-nowrap">
          <BoltLogo class="h-7 w-7" />
          <span class="text-2xl font-bold text-gray-900 dark:text-gray-100">EV Monitor</span>
        </a>
        <div class="flex items-center gap-2 sm:gap-3 min-w-0">
          <LocaleSwitcher class="hidden sm:flex" />
          <ThemeToggle class="text-gray-600 dark:text-gray-300" />
          <a
            href="https://github.com/sebastianwien/ev-monitor"
            target="_blank"
            rel="noopener noreferrer"
            class="hidden sm:inline-flex text-gray-600 dark:text-gray-400 hover:text-gray-900 dark:hover:text-gray-100 p-2 items-center"
            aria-label="View source on GitHub"
          >
            <svg class="h-6 w-6" fill="currentColor" viewBox="0 0 24 24" aria-hidden="true">
              <path fill-rule="evenodd" d="M12 2C6.477 2 2 6.484 2 12.017c0 4.425 2.865 8.18 6.839 9.504.5.092.682-.217.682-.483 0-.237-.008-.868-.013-1.703-2.782.605-3.369-1.343-3.369-1.343-.454-1.158-1.11-1.466-1.11-1.466-.908-.62.069-.608.069-.608 1.003.07 1.531 1.032 1.531 1.032.892 1.53 2.341 1.088 2.91.832.092-.647.35-1.088.636-1.338-2.22-.253-4.555-1.113-4.555-4.951 0-1.093.39-1.988 1.029-2.688-.103-.253-.446-1.272.098-2.65 0 0 .84-.27 2.75 1.026A9.564 9.564 0 0112 6.844c.85.004 1.705.115 2.504.337 1.909-1.296 2.747-1.027 2.747-1.027.546 1.379.202 2.398.1 2.651.64.7 1.028 1.595 1.028 2.688 0 3.848-2.339 4.695-4.566 4.943.359.309.678.92.678 1.855 0 1.338-.012 2.419-.012 2.747 0 .268.18.58.688.482A10.019 10.019 0 0022 12.017C22 6.484 17.522 2 12 2z" clip-rule="evenodd" />
            </svg>
          </a>
          <a :href="pricingPath" class="hidden sm:inline text-sm font-medium text-gray-600 dark:text-gray-300 hover:text-gray-900 dark:hover:text-white px-2 py-2">{{ t('nav.pricing') }}</a>
          <a :href="loginPath" class="hidden sm:inline text-sm font-medium text-gray-600 dark:text-gray-300 hover:text-gray-900 dark:hover:text-white px-2 py-2">{{ t('nav.login') }}</a>
          <a :href="registerPath" class="btn-3d [--btn-shadow-color:#111827] dark:[--btn-shadow-color:#000000] text-sm font-medium bg-green-600 text-white border-2 border-gray-900 dark:border-gray-100 px-3 sm:px-4 py-2 rounded-sm hover:bg-green-700 transition whitespace-nowrap">
            {{ t('nav.register') }}
          </a>
        </div>
      </div>
    </div>
    </nav>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '../../stores/auth'
import BoltLogo from './BoltLogo.vue'
import ThemeToggle from './ThemeToggle.vue'
import LocaleSwitcher from './LocaleSwitcher.vue'

const { t, locale } = useI18n()
const authStore = useAuthStore()
const isEn = computed(() => locale.value === 'en')
const registerPath = computed(() => isEn.value ? '/en/register' : '/register')
const loginPath = computed(() => isEn.value ? '/en/login' : '/login')
const pricingPath = computed(() => isEn.value ? '/pricing' : '/preise')
</script>

<style scoped>
/* Nur in der nativen App (iOS/Android) die Navbar straffen.
   Auf Mobile-Browser/PWA bleibt alles wie gehabt. Der Statusbar-Inset
   (env(safe-area-inset-top)) ist davon unberuehrt - der muss nativ reserviert bleiben. */
/* Gesamten Selektor in :global() - sonst kompiliert der scoped-CSS-Transform
   ":global(html.is-native) .x" fehlerhaft zu "html.is-native { ... }" (der
   Descendant geht verloren). Bei display:none wuerde das die GESAMTE App auf
   Native verstecken (0x0-Blackscreen). Die Klassen sind komponentenspezifisch,
   global ist hier also unkritisch. */
:global(html.is-native .public-nav-row) {
  height: 3rem; /* 48px statt 64px */
}
/* Die Akzent-Gradientlinie liegt nativ unter der (ueberlagernden) Statusbar und ist
   damit ohnehin unsichtbar - weglassen spart Hoehe und vermeidet toten Raum. */
:global(html.is-native .public-nav-accent) {
  display: none;
}
</style>
