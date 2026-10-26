<template>
  <!-- Guests everywhere it is placed; members only on routes marked publicNav (the model pages, the story
       dashboard), where the app chrome steps back and this nav carries the way home instead of register and login.
       Under the workspace frame only the frame's own nav (home) renders, the page's nav steps back. -->
  <div v-if="!authStore.isAuthenticated() || member" ref="header" class="sticky top-0 z-50 [view-transition-name:public-nav]">
    <div class="public-nav-accent h-1 bg-gradient-to-r from-green-400 via-emerald-500 to-green-600"></div>
    <nav class="relative pt-[env(safe-area-inset-top)] bg-green-50 dark:bg-green-950 border-b-2 border-gray-900 dark:border-gray-800 shadow-sm">
    <!-- home: dieselbe Achse wie Ticker und Inhalt des eingeloggten Bereichs (max-w-6xl) -->
    <div class="mx-auto" :class="home ? 'max-w-6xl px-4 md:px-6' : 'max-w-7xl px-4 sm:px-8'">
      <div class="public-nav-row flex justify-between items-center h-16">
        <div class="flex min-w-0 items-center gap-5">
          <a href="/" class="flex items-center gap-2 shrink-0 whitespace-nowrap">
            <BoltLogo class="h-7 w-7" />
            <span class="text-2xl font-bold text-gray-900 dark:text-gray-100">EV Monitor</span>
          </a>
          <!-- Bereiche des eingeloggten Bereichs (WorkspaceFrame, ab md): stehen nur hier, wenn sie neben die rechte
               Gruppe passen, sonst unsichtbar und der Frame zeigt die Chip-Reihe (navFits) -->
          <div v-if="$slots.nav" ref="navSlot" class="min-w-0 overflow-hidden" :class="navFits ? '' : 'invisible'">
            <slot name="nav" />
          </div>
        </div>
        <!-- Rechte Gruppe: ein Hover für alle (runde, leichte Fläche, 40 px hoch), das Zahnrad ist der grüne Knopf.
             Die Hover-Flächen tragen den Abstand selbst, daher ab sm nur 4 px dazwischen. -->
        <div class="flex items-center gap-2 sm:gap-1" :class="home ? 'shrink-0' : 'min-w-0'">
          <LocaleSwitcher class="hidden sm:flex" />
          <ThemeToggle class="text-gray-600 hover:text-gray-900 dark:text-gray-300 dark:hover:text-white" />
          <template v-if="member">
            <router-link to="/coins/history" data-testid="public-nav-watt" :title="t('nav.watt')"
                         :class="[navItem, 'hidden sm:inline-flex gap-1 px-2.5 text-sm font-semibold tabular-nums']">
              <BoltIcon class="h-4 w-4" aria-hidden="true" />{{ coinStore.balance }}
            </router-link>
            <button type="button" data-testid="public-nav-feedback"
                    data-tally-open="vGB8XA" data-tally-emoji-text="👋" data-tally-emoji-animation="wave"
                    :aria-label="t('nav.bottom.feedback')" :title="t('nav.bottom.feedback')"
                    :class="[navItem, 'hidden md:inline-flex w-10']">
              <ChatBubbleLeftEllipsisIcon class="h-5 w-5" aria-hidden="true" />
            </button>
            <!-- Mobil führt das Zahnrad direkt zu den Einstellungen (Import und Vergleich trägt das Mehr-Sheet),
                 ab md öffnet es die Konto-Zeile -->
            <router-link v-if="home" to="/settings" data-testid="public-nav-settings" :aria-label="t('nav.bottom.settings')"
                         :class="[profileButton, 'inline-flex bg-green-600 md:hidden']">
              <Cog6ToothIcon class="h-5 w-5" aria-hidden="true" />
            </router-link>
            <button v-if="home" ref="accountButton" type="button" data-testid="public-nav-account"
                    aria-controls="public-nav-account-row" :aria-expanded="accountOpen" :aria-label="t('nav.account')"
                    :class="[profileButton, 'relative hidden md:inline-flex', accountOpen ? 'bg-green-700' : 'bg-green-600']" @click="accountOpen = !accountOpen">
              <!-- Zahnrad und X überblenden: offen zeigt der Knopf, wie man die Zeile wieder schließt -->
              <Cog6ToothIcon class="h-5 w-5 transition duration-200 ease-out" :class="accountOpen ? 'rotate-90 scale-50 opacity-0' : ''" aria-hidden="true" />
              <XMarkIcon class="absolute h-5 w-5 transition duration-200 ease-out" :class="accountOpen ? '' : 'scale-50 opacity-0'" aria-hidden="true" />
            </button>
            <router-link v-else to="/dashboard" data-testid="public-nav-dashboard"
                         class="btn-3d [--btn-shadow-color:#111827] dark:[--btn-shadow-color:#000000] inline-flex items-center gap-1.5 text-sm font-medium bg-green-600 text-white border-2 border-gray-900 dark:border-gray-100 px-3 sm:px-4 py-2 rounded-sm hover:bg-green-700 transition whitespace-nowrap">
              <HomeIcon class="h-4 w-4" aria-hidden="true" />{{ t('nav.dashboard') }}
            </router-link>
          </template>
          <a
            v-if="!member"
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
          <a v-if="!member" :href="pricingPath" class="hidden sm:inline text-sm font-medium text-gray-600 dark:text-gray-300 hover:text-gray-900 dark:hover:text-white px-2 py-2">{{ t('nav.pricing') }}</a>
          <a v-if="!member" :href="loginPath" class="hidden sm:inline text-sm font-medium text-gray-600 dark:text-gray-300 hover:text-gray-900 dark:hover:text-white px-2 py-2">{{ t('nav.login') }}</a>
          <a v-if="!member" :href="registerPath" class="btn-3d [--btn-shadow-color:#111827] dark:[--btn-shadow-color:#000000] text-sm font-medium bg-green-600 text-white border-2 border-gray-900 dark:border-gray-100 px-3 sm:px-4 py-2 rounded-sm hover:bg-green-700 transition whitespace-nowrap">
            {{ t('nav.register') }}
          </a>
        </div>
      </div>
    </div>
    <!-- Konto (ab md): eine zweite Zeile im Grün der Navbar, rechtsbündig unter dem Profil, gleich hoch wie
         der Ticker und über ihm. Textlinks wie die Bereiche statt eines schwebenden Menüs. -->
    <nav v-if="member && home" id="public-nav-account-row" :aria-label="t('nav.account')" class="absolute inset-x-0 top-full z-20 hidden md:block">
      <!-- Gleitet unter der Navbar hervor, ohne Einblenden; zu als Umkehrung -->
      <div class="overflow-hidden">
        <Transition name="account-slide">
          <div v-show="accountOpen" class="border-b-2 border-gray-900 bg-green-50 dark:border-gray-800 dark:bg-green-950">
            <div class="mx-auto flex h-11 max-w-6xl items-stretch justify-end gap-1 px-4 md:px-6">
              <router-link v-if="!authStore.isDemoAccount" to="/settings" :class="accountLink"><Cog6ToothIcon class="h-4 w-4" aria-hidden="true" />{{ t('nav.bottom.settings') }}</router-link>
              <router-link v-if="!authStore.isDemoAccount" to="/imports" :class="accountLink"><ArrowDownTrayIcon class="h-4 w-4" aria-hidden="true" />{{ t('nav.bottom.import') }}</router-link>
              <router-link to="/modelle" :class="accountLink"><ArrowsRightLeftIcon class="h-4 w-4" aria-hidden="true" />{{ t('nav.models_compare') }}</router-link>
              <template v-if="!authStore.isDemoAccount">
                <span class="mx-2 my-3 w-px bg-gray-300 dark:bg-gray-700" aria-hidden="true" />
                <button type="button" data-testid="public-nav-logout" :class="[accountLink, 'text-gray-500 dark:text-gray-400']" @click="authStore.logout()">
                  <ArrowRightOnRectangleIcon class="h-4 w-4" aria-hidden="true" />{{ t('nav.bottom.logout') }}
                </button>
              </template>
            </div>
          </div>
        </Transition>
      </div>
    </nav>
    </nav>
    <!-- Band unter der Nav (Ticker der Übersicht): sitzt im selben sticky Kopf, scrollt also nicht weg -->
    <slot name="below" />
  </div>
</template>

<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import {
  HomeIcon, BoltIcon, XMarkIcon, ChatBubbleLeftEllipsisIcon, Cog6ToothIcon, ArrowDownTrayIcon, ArrowsRightLeftIcon, ArrowRightOnRectangleIcon,
} from '@heroicons/vue/24/outline'
import { useAuthStore } from '../../stores/auth'
import { useCoinStore } from '../../stores/coins'
import BoltLogo from './BoltLogo.vue'
import ThemeToggle from './ThemeToggle.vue'
import LocaleSwitcher from './LocaleSwitcher.vue'

/** home: inside the members' area (story, logs) - home is in the chips and the bottom nav, the profile instead */
defineProps<{ home?: boolean }>()
const emit = defineEmits<{ navFits: [fits: boolean] }>()

const { t, locale } = useI18n()
const authStore = useAuthStore()
const coinStore = useCoinStore()
const route = useRoute()
/** signed in on a route that keeps the public nav (router meta publicNav): home and balance instead of register */
const member = computed(() => authStore.isAuthenticated() && !!route.meta.publicNav)
const isEn = computed(() => locale.value === 'en')
const registerPath = computed(() => isEn.value ? '/en/register' : '/register')
const loginPath = computed(() => isEn.value ? '/en/login' : '/login')
const pricingPath = computed(() => isEn.value ? '/pricing' : '/preise')
// Ohne display: mobil Link, ab md Knopf der Konto-Zeile; ein inline-flex hier schlüge das hidden des jeweils anderen
const profileButton = 'h-10 w-10 items-center justify-center rounded-full border-2 border-edge text-white transition-colors hover:bg-green-700'
// Hover der rechten Gruppe (Watt, Feedback; ThemeToggle und LocaleSwitcher tragen denselben selbst)
const navItem = 'h-10 items-center justify-center rounded-full text-gray-600 transition-colors hover:bg-black/10 hover:text-gray-900 dark:text-gray-300 dark:hover:bg-white/10 dark:hover:text-white'
// Wie die Bereiche: Text statt Kachel, beim Hover wächst ein Unterstrich von links herein
const accountLink = 'inline-flex items-center gap-2 px-3 text-[14px] font-semibold whitespace-nowrap text-gray-700 hover:text-gray-900 dark:text-gray-300 dark:hover:text-white bg-[linear-gradient(currentColor,currentColor)] bg-no-repeat bg-bottom-left bg-[length:0%_2px] hover:bg-[length:100%_2px] focus-visible:bg-[length:100%_2px] focus-visible:outline-none transition-[background-size,color] duration-200 ease-out'

// Passen die Bereiche zwischen Logo und rechte Gruppe? Gemessen statt per Breakpoint, denn Sprache und Watt-Stand
// ändern die Breite. Die rechte Gruppe schrumpft nicht (home), also bekommt der Slot weniger Platz, als er braucht.
const navSlot = ref<HTMLElement | null>(null)
const navFits = ref(true)
function measureNav() {
  const el = navSlot.value
  if (!el) return
  const fits = el.scrollWidth <= el.clientWidth
  if (fits !== navFits.value) { navFits.value = fits; emit('navFits', fits) }
}
let navObserver: ResizeObserver | null = null
watch(navSlot, (el) => {
  navObserver?.disconnect()
  if (!el) return
  measureNav()
  if (typeof ResizeObserver === 'undefined') return
  // Slot (Fensterbreite, rechte Gruppe wächst) und Leiste (Sprache wechselt) beobachten
  navObserver = new ResizeObserver(measureNav)
  navObserver.observe(el)
  if (el.firstElementChild) navObserver.observe(el.firstElementChild)
}, { flush: 'post' })
onUnmounted(() => navObserver?.disconnect())

// Konto-Zeile: schließt per Escape (Fokus zurück aufs Profil), Klick neben den Kopf und Seitenwechsel
const accountOpen = ref(false)
const accountButton = ref<HTMLButtonElement | null>(null)
const header = ref<HTMLElement | null>(null)
function onKey(e: KeyboardEvent) {
  if (e.key !== 'Escape') return
  accountOpen.value = false
  accountButton.value?.focus()
}
function onPointer(e: PointerEvent) {
  if (!header.value?.contains(e.target as Node)) accountOpen.value = false
}
function listen(on: boolean) {
  if (on) {
    document.addEventListener('keydown', onKey)
    document.addEventListener('pointerdown', onPointer)
  } else {
    document.removeEventListener('keydown', onKey)
    document.removeEventListener('pointerdown', onPointer)
  }
}
watch(accountOpen, listen)
watch(() => route.fullPath, () => { accountOpen.value = false })
onUnmounted(() => listen(false))
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
/* Konto-Zeile: nur Schieben, kein Einblenden; auf ease-out, zu ease-in wie Collapse */
.account-slide-enter-active { transition: transform 0.3s ease-out; }
.account-slide-leave-active { transition: transform 0.3s ease-in; }
.account-slide-enter-from, .account-slide-leave-to { transform: translateY(-100%); }
@media (prefers-reduced-motion: reduce) {
  .account-slide-enter-active, .account-slide-leave-active { transition: none; }
}
/* Die Akzent-Gradientlinie liegt nativ unter der (ueberlagernden) Statusbar und ist
   damit ohnehin unsichtbar - weglassen spart Hoehe und vermeidet toten Raum. */
:global(html.is-native .public-nav-accent) {
  display: none;
}
</style>
