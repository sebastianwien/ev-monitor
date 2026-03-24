<script setup lang="ts">
import { ref, watch, onMounted, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useImportsTab } from './composables/useImportsTab'
import { useOnboardingState } from './composables/useOnboardingState'
import { useAuthStore } from './stores/auth'
import { analytics } from './services/analytics'
import { useCoinStore } from './stores/coins'
import { storeToRefs } from 'pinia'
import { useWallboxStore } from './stores/wallbox'
import SpritMonitorImport from './components/SpritMonitorImport.vue'
import SupportPopover from './components/SupportPopover.vue'
import LeaderboardTicker from './components/LeaderboardTicker.vue'
import LogFormModal from './components/LogFormModal.vue'
import FloatingActionButton from './components/FloatingActionButton.vue'
import OnboardingWelcome from './components/OnboardingWelcome.vue'
import DemoBanner from './components/DemoBanner.vue'
import RedditConsentBanner from './components/RedditConsentBanner.vue'
import FeedbackToast from './components/FeedbackToast.vue'
import { Bars3Icon, XMarkIcon, HomeIcon, ArrowDownTrayIcon, UserIcon, BoltIcon, ChatBubbleLeftEllipsisIcon, ArrowsRightLeftIcon } from '@heroicons/vue/24/outline'
// Note: showImportOverlay kept for backward compat but SpritMonitor moved to /imports
import { captureUtmParams, captureReferrer } from './utils/reddit-pixel'
import { useHaptic } from './composables/useHaptic'
import { useThemeStore } from './stores/theme'
import ThemeToggle from './components/ThemeToggle.vue'
import LocaleSwitcher from './components/LocaleSwitcher.vue'
import { useTickerState } from './composables/useTickerState'

const { haptic } = useHaptic()
const { t, locale } = useI18n()
const themeStore = useThemeStore()
const { tickerHasItems, tickerCollapsed } = useTickerState()

const mainPaddingTop = computed(() => {
  if (!authStore.isAuthenticated()) return '0px'
  if (tickerHasItems.value && !tickerCollapsed.value) return '90px' // 58 + 32
  return '64px'
})

const router = useRouter()
const route = useRoute()
const { activeTab: importsActiveTab } = useImportsTab()
const { isOnboardingVisible } = useOnboardingState()

function goToGoeTab() {
  importsActiveTab.value = 'goe'
  if (route.path !== '/imports') router.push('/imports')
}
const authStore = useAuthStore()
const coinStore = useCoinStore()
const wallboxStore = useWallboxStore()
const { activeConnection: wallboxConn, hasConnections: wallboxHasConnections } = storeToRefs(wallboxStore)
const showImportOverlay = ref(false)
const showLogFormModal = ref(false)
const mobileMenuOpen = ref(false)
const balanceBumping = ref(false)
const balanceInitialized = ref(false)

// Fetch balance + init wallbox store on load and whenever token changes (login/logout)
watch(() => route.path, (path) => {
  if (path === '/dashboard') analytics.track('dashboard_viewed')
})

watch(() => authStore.token, (newToken) => {
  if (newToken) {
    balanceInitialized.value = false
    coinStore.fetchBalance()
    wallboxStore.init(!!(authStore.user as any)?.demoAccount)
  } else {
    wallboxStore.reset()
  }
}, { immediate: true })

// Animate badge when balance increases — skip the initial fetch (0 → actual value)
watch(() => coinStore.balance, (newVal, oldVal) => {
  if (!balanceInitialized.value) {
    balanceInitialized.value = true
    return
  }
  if (newVal > oldVal) {
    balanceBumping.value = true
    setTimeout(() => { balanceBumping.value = false }, 750)
  }
})

// Capture UTM parameters and referrer on first page load for campaign tracking
onMounted(() => {
  themeStore.init()
  captureUtmParams()
  captureReferrer()
})

// Impersonation
const impersonatingAs = computed(() => sessionStorage.getItem('impersonating'))

// Wallbox navbar chip
const wallboxChipColor = computed(() => {
  const s = wallboxConn.value?.carState
  if (s === 2) return 'bg-green-500'
  if (s === 5 || wallboxConn.value?.lastPollError) return 'bg-red-400'
  if (s === 4) return 'bg-blue-400'
  return 'bg-gray-400'
})

const stopImpersonation = () => {
  sessionStorage.removeItem('impersonating')
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
  mobileMenuOpen.value = false
}

const closeMobileMenu = () => {
  mobileMenuOpen.value = false
}

</script>

<template>
  <div class="min-h-screen bg-gray-100 dark:bg-gray-950 ev-bg-pattern flex flex-col">
    <!-- Navigation -->
    <nav class="bg-indigo-600 shadow-md text-white fixed top-0 left-0 right-0 z-40" v-if="authStore.isAuthenticated()">
      <div class="px-4 py-3">
        <div class="flex justify-between items-center">
          <!-- Left: Logo + Nav Buttons (Desktop) -->
          <div class="flex items-center space-x-4">
            <router-link to="/dashboard" class="flex items-center gap-1.5 text-2xl font-bold tracking-wide hover:opacity-80 transition whitespace-nowrap">
              <BoltIcon class="h-7 w-7" />
              <span class="hidden sm:inline">EV Monitor</span>
            </router-link>

            <!-- Compact Icon Nav (640px - 1024px) -->
            <div class="hidden sm:flex lg:hidden items-center space-x-2">
              <button
                @click="handleNewLog(); haptic()"
                class="nav-3d p-2 rounded-md bg-green-600 hover:bg-green-700 transition"
                title="Ladevorgang erfassen">
                <BoltIcon class="h-5 w-5" />
              </button>
              <router-link
                to="/imports"
                class="nav-3d p-2 rounded-md border border-indigo-500 hover:bg-indigo-500 transition"
                @click="haptic()"
                :class="{ 'bg-indigo-500': $route.path === '/imports' }"
                title="Import">
                <ArrowDownTrayIcon class="h-5 w-5" />
              </router-link>
              <router-link
                to="/modelle"
                class="nav-3d p-2 rounded-md border border-indigo-500 hover:bg-indigo-500 transition"
                @click="haptic()"
                :class="{ 'bg-indigo-500': $route.path.startsWith('/modelle') }"
                :title="t('nav.models_compare')">
                <ArrowsRightLeftIcon class="h-5 w-5" />
              </router-link>
              <router-link
                to="/dashboard"
                class="nav-3d p-2 rounded-md border border-indigo-500 hover:bg-indigo-500 transition"
                @click="haptic()"
                :class="{ 'bg-indigo-500': $route.path === '/dashboard' }"
                title="Dashboard">
                <HomeIcon class="h-5 w-5" />
              </router-link>
            </div>

            <!-- Full Nav (1024px+) -->
            <div class="hidden lg:flex items-center space-x-4">
              <router-link
                to="/dashboard"
                class="nav-3d p-2 rounded-md border border-indigo-500 hover:bg-indigo-500 transition"
                @click="haptic()"
                :class="{ 'bg-indigo-500': $route.path === '/dashboard' }"
                title="Dashboard">
                <HomeIcon class="h-5 w-5" />
              </router-link>
              <router-link
                to="/imports"
                class="nav-3d flex items-center gap-2 px-3 py-2 rounded-md border border-indigo-500 text-sm font-medium hover:bg-indigo-500 transition"
                @click="haptic()"
                :class="{ 'bg-indigo-500': $route.path === '/imports' }">
                <ArrowDownTrayIcon class="h-5 w-5" />
                Import
              </router-link>
              <router-link
                to="/modelle"
                class="nav-3d flex items-center gap-2 px-3 py-2 rounded-md border border-indigo-500 text-sm font-medium hover:bg-indigo-500 transition"
                @click="haptic()"
                :class="{ 'bg-indigo-500': $route.path.startsWith('/modelle') }">
                <ArrowsRightLeftIcon class="h-5 w-5" />
                {{ t('nav.models_compare') }}
              </router-link>
              <button
                @click="handleNewLog(); haptic()"
                class="nav-3d p-2 rounded-md bg-green-600 hover:bg-green-700 transition"
                title="Ladevorgang erfassen">
                <BoltIcon class="h-5 w-5" />
              </button>
            </div>
          </div>

          <!-- Right: Coin Balance + User Info + Logout (Desktop) / Hamburger (Mobile) -->

          <!-- Compact Right Nav (768px - 1280px) -->
          <div class="hidden md:flex xl:hidden items-center space-x-2">
            <LocaleSwitcher variant="nav" />
            <ThemeToggle class="text-white" />
            <!-- Wallbox dot -->
            <button
              v-if="wallboxHasConnections"
              @click="goToGoeTab"
              :title="`${wallboxConn?.displayName || 'Wallbox'} · ${wallboxConn?.carStateLabel}`"
              class="p-2 rounded-md hover:bg-indigo-500 transition flex items-center justify-center"
            >
              <span
                :class="['w-2.5 h-2.5 rounded-full', wallboxChipColor,
                         wallboxConn?.carState === 2 ? 'animate-pulse' : '']"
              />
            </button>
            <router-link
              to="/coins/history"
              class="nav-3d flex items-center gap-1 px-2 h-9 text-sm border border-indigo-500 rounded-md hover:bg-indigo-500 transition font-medium"
              @click="haptic()"
              :class="{ 'watt-bump': balanceBumping }"
              title="Watt-Guthaben">
              <BoltIcon class="h-4 w-4" />
              <span>{{ coinStore.balance }}</span>
            </router-link>
            <router-link
              v-if="authStore.user"
              to="/settings"
              class="nav-3d flex items-center justify-center h-9 w-9 border border-indigo-500 rounded-md hover:bg-indigo-500 transition"
              :class="{ 'bg-indigo-500': $route.path === '/settings' }"
              @click="haptic()"
              title="Einstellungen">
              <UserIcon class="h-5 w-5" />
            </router-link>
            <button
              data-tally-open="vGB8XA" data-tally-emoji-text="👋" data-tally-emoji-animation="wave"
              class="p-2 text-indigo-300 hover:text-white transition"
              title="Feedback geben">
              <ChatBubbleLeftEllipsisIcon class="h-5 w-5" />
            </button>
            <SupportPopover variant="nav" />
          </div>

          <!-- Full Right Nav (1280px+) -->
          <div class="hidden xl:flex items-center space-x-4">
            <LocaleSwitcher variant="nav" />
            <ThemeToggle class="text-white" />
            <!-- Wallbox chip -->
            <button
              v-if="wallboxHasConnections"
              @click="goToGoeTab"
              class="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg border text-xs font-medium transition hover:opacity-80"
              :class="wallboxConn?.carState === 2
                ? 'bg-green-500 bg-opacity-30 border-green-400 text-white'
                : wallboxConn?.carState === 5 || wallboxConn?.lastPollError
                  ? 'bg-red-500 bg-opacity-30 border-red-400 text-white'
                  : 'bg-indigo-500 bg-opacity-30 border-indigo-500 text-white'"
            >
              <span
                :class="['w-2 h-2 rounded-full flex-shrink-0', wallboxChipColor,
                         wallboxConn?.carState === 2 ? 'animate-pulse' : '']"
              />
              <span class="truncate max-w-[120px]">{{ wallboxConn?.displayName || 'Wallbox' }}</span>
              <span class="opacity-90">· {{ wallboxConn?.carStateLabel }}</span>
            </button>
            <div class="relative group">
              <router-link
                to="/coins/history"
                class="nav-3d flex items-center gap-1.5 px-3 py-1.5 text-sm border border-indigo-500 rounded-md hover:bg-indigo-500 transition font-medium"
                @click="haptic()"
                :class="{ 'watt-bump': balanceBumping }">
                <BoltIcon class="h-4 w-4" />
                <span>{{ coinStore.balance }}</span>
              </router-link>
              <!-- Tooltip -->
              <div class="absolute right-0 top-full mt-2 w-48 bg-gray-900 text-white text-xs rounded-lg shadow-xl p-3 opacity-0 group-hover:opacity-100 pointer-events-none transition-opacity duration-150 z-50">
                <div class="flex justify-between mb-1.5">
                  <span class="text-gray-400">{{ t('dashboard.coins_total') }}</span>
                  <span class="font-semibold">{{ coinStore.balance }} Watt</span>
                </div>
                <div class="flex justify-between border-t border-gray-700 pt-1.5">
                  <span class="text-gray-400">{{ t('dashboard.coins_this_month') }}</span>
                  <span class="font-semibold text-yellow-400">+{{ coinStore.coinsThisMonth }} Watt</span>
                </div>
                <!-- Arrow -->
                <div class="absolute -top-1.5 right-4 w-3 h-3 bg-gray-900 rotate-45"></div>
              </div>
            </div>
            <router-link
              v-if="authStore.user"
              to="/settings"
              class="nav-3d flex items-center justify-center h-9 w-9 border border-indigo-500 rounded-md hover:bg-indigo-500 transition"
              :class="{ 'bg-indigo-500': $route.path === '/settings' }"
              @click="haptic()">
              <UserIcon class="h-5 w-5" />
            </router-link>
            <button
              data-tally-open="vGB8XA" data-tally-emoji-text="👋" data-tally-emoji-animation="wave"
              class="text-indigo-300 hover:text-white transition"
              title="Feedback geben"
            >
              <ChatBubbleLeftEllipsisIcon class="h-5 w-5" />
            </button>
            <SupportPopover variant="nav" />
          </div>

          <!-- Mobile: Icons + Hamburger Button -->
          <div class="md:hidden flex items-center gap-3">
            <LocaleSwitcher variant="nav" />
            <ThemeToggle class="text-white" />
            <!-- Wallbox dot -->
            <button
              v-if="wallboxHasConnections"
              @click="goToGoeTab"
              :title="`${wallboxConn?.displayName || 'Wallbox'} · ${wallboxConn?.carStateLabel}`"
              class="flex items-center justify-center"
            >
              <span
                :class="['w-2.5 h-2.5 rounded-full', wallboxChipColor,
                         wallboxConn?.carState === 2 ? 'animate-pulse' : '']"
              />
            </button>
            <router-link
              to="/coins/history"
              class="flex items-center gap-1 px-2 py-1 text-sm bg-indigo-500 bg-opacity-30 border border-indigo-500 rounded-md hover:bg-opacity-50 transition font-medium"
              :class="{ 'watt-bump': balanceBumping }"
              title="Watt-Guthaben">
              <BoltIcon class="h-4 w-4" />
              <span>{{ coinStore.balance }}</span>
            </router-link>
            <button
              data-tally-open="vGB8XA" data-tally-emoji-text="👋" data-tally-emoji-animation="wave"
              class="text-indigo-300 hover:text-white transition"
              title="Feedback geben">
              <ChatBubbleLeftEllipsisIcon class="h-5 w-5" />
            </button>
            <button
              @click="mobileMenuOpen = !mobileMenuOpen"
              class="p-2 rounded-md hover:bg-indigo-700 transition"
              aria-label="Menu">
              <Bars3Icon v-if="!mobileMenuOpen" class="h-6 w-6" />
              <XMarkIcon v-else class="h-6 w-6" />
            </button>
          </div>
        </div>
      </div>

    </nav>

    <!-- Leaderboard Ticker (below nav, only when authenticated) -->
    <LeaderboardTicker v-if="authStore.isAuthenticated() && !authStore.isDemoAccount && locale === 'de'" />

    <!-- Mobile Menu Overlay -->
    <Transition name="mobile-menu">
      <div
        v-if="mobileMenuOpen"
        class="fixed inset-0 z-30 md:hidden"
        @click.self="closeMobileMenu">
        <!-- Backdrop -->
        <div class="absolute inset-0 bg-black bg-opacity-50" @click="closeMobileMenu"></div>

        <!-- Menu Panel (slides in from top) -->
        <div class="absolute top-0 left-0 right-0 bg-indigo-700 shadow-2xl overflow-y-auto max-h-[70vh]">
          <div class="px-4 py-4 space-y-2">
            <!-- Header -->
            <div class="flex items-center justify-between px-3 py-2">
              <div class="flex items-center gap-2">
                <BoltIcon class="h-5 w-5 text-yellow-400" />
                <span class="text-base font-bold tracking-wide text-white">EV-Monitor</span>
              </div>
              <button
                @click="closeMobileMenu"
                class="p-1.5 rounded-full bg-indigo-500 hover:bg-indigo-400 transition"
                aria-label="Menü schließen">
                <XMarkIcon class="h-5 w-5 text-white" />
              </button>
            </div>
            <div class="border-t border-indigo-500 mb-2"></div>

            <router-link
              to="/dashboard"
              @click="closeMobileMenu"
              class="flex items-center gap-2 px-3 py-2 rounded-md text-sm font-medium text-indigo-100 hover:bg-indigo-600 transition"
              :class="{ 'bg-indigo-800': $route.path === '/dashboard' }">
              <HomeIcon class="h-5 w-5" />
              <span>Dashboard</span>
            </router-link>
            <router-link
              to="/imports"
              @click="closeMobileMenu"
              class="flex items-center gap-2 px-3 py-2 rounded-md text-sm font-medium text-indigo-100 hover:bg-indigo-600 transition"
              :class="{ 'bg-indigo-800': $route.path === '/imports' }">
              <ArrowDownTrayIcon class="h-5 w-5" />
              <span>Import</span>
            </router-link>
            <router-link
              to="/modelle"
              @click="closeMobileMenu"
              class="flex items-center gap-2 px-3 py-2 rounded-md text-sm font-medium text-indigo-100 hover:bg-indigo-600 transition"
              :class="{ 'bg-indigo-800': $route.path.startsWith('/modelle') }">
              <ArrowsRightLeftIcon class="h-5 w-5" />
              <span>{{ t('nav.models_compare') }}</span>
            </router-link>
            <router-link
              v-if="authStore.user"
              to="/settings"
              @click="closeMobileMenu"
              class="flex items-center gap-2 px-3 py-2 rounded-md text-sm font-medium text-indigo-100 hover:bg-indigo-600 transition"
              :class="{ 'bg-indigo-800': $route.path === '/settings' }">
              <UserIcon class="h-5 w-5" />
              <span>{{ authStore.user.username || authStore.user.sub }}</span>
            </router-link>
            <SupportPopover variant="nav" />
          </div>
        </div>
      </div>
    </Transition>
    <!-- Impersonation Banner -->
    <div
      v-if="impersonatingAs"
      class="sticky top-0 z-50 flex items-center justify-between px-4 py-2 bg-amber-400 text-amber-900 text-sm font-medium">
      <span>Impersonation aktiv: <strong>{{ impersonatingAs }}</strong> · Token läuft in 1h ab</span>
      <button
        @click="stopImpersonation"
        class="px-3 py-1 bg-amber-900 text-amber-100 rounded-md hover:bg-amber-800 transition text-xs font-semibold">
        Beenden
      </button>
    </div>

    <!-- Demo Banner (shown for seed/demo accounts) -->
    <DemoBanner v-if="authStore.isDemoAccount" />
    <main :class="{ 'md:pb-10 md:px-4': authStore.isAuthenticated() }" :style="{ paddingTop: mainPaddingTop, transition: 'padding-top 0.3s ease' }">
      <router-view></router-view>
    </main>

    <!-- Footer (only for authenticated users) -->
    <footer v-if="authStore.isAuthenticated()" class="bg-gray-50 dark:bg-gray-900 border-t border-gray-200 dark:border-gray-700 py-6 mt-auto">
      <div class="container mx-auto px-4">
        <div class="text-center text-sm text-gray-600 dark:text-gray-400 space-y-3">
          <p>
            © 2026 EV Monitor ·
            <router-link to="/datenschutz" class="hover:text-green-600 underline">{{ t('footer.privacy') }}</router-link> ·
            <router-link to="/impressum" class="hover:text-green-600 underline">{{ t('footer.imprint') }}</router-link> ·
            <router-link to="/agb" class="hover:text-green-600 underline">{{ t('footer.terms') }}</router-link> ·
            <a href="https://github.com/sebastianwien/ev-monitor" target="_blank" rel="noopener noreferrer" class="hover:text-green-600 underline">{{ t('footer.github') }}</a>
          </p>
          <p>
            <SupportPopover variant="footer" />
          </p>
        </div>
      </div>
    </footer>

    <!-- Sprit-Monitor Import Overlay -->
    <SpritMonitorImport v-if="showImportOverlay" @close="showImportOverlay = false" />

    <!-- Floating Action Button (only when authenticated) -->
    <FloatingActionButton v-if="authStore.isAuthenticated() && !isOnboardingVisible && $route.path !== '/erfassen'" @click="handleNewLog" />

    <!-- Log Form Modal (Desktop only) -->
    <LogFormModal v-if="showLogFormModal && authStore.isAuthenticated()" @close="showLogFormModal = false" />

    <!-- Onboarding Welcome (First-time users) -->
    <OnboardingWelcome v-if="authStore.isAuthenticated()" />

    <!-- Reddit Consent Banner (only for paid Reddit traffic) -->
    <RedditConsentBanner />

    <!-- Feedback Toast (delayed, dismissible) -->
    <FeedbackToast />
  </div>
</template>

<style scoped>
@keyframes watt-bump {
  0%   { transform: scale(1);    box-shadow: none; background-color: transparent; border-color: rgba(129, 140, 248, 0.5); }
  25%  { transform: scale(1.45); box-shadow: 0 0 0 4px rgba(250, 204, 21, 0.4), 0 0 16px rgba(250, 204, 21, 0.6); background-color: rgba(250, 204, 21, 0.25); border-color: rgba(250, 204, 21, 0.9); color: #fef08a; }
  60%  { transform: scale(0.95); box-shadow: 0 0 0 2px rgba(250, 204, 21, 0.2); }
  100% { transform: scale(1);    box-shadow: none; background-color: transparent; border-color: rgba(129, 140, 248, 0.5); color: inherit; }
}

/* 3D press effect for navbar buttons */
.nav-3d {
  box-shadow: 0 3px 0 0 rgba(0,0,0,0.25);
  transform: translateY(0);
  transition: transform 0.08s ease, box-shadow 0.08s ease;
  touch-action: manipulation;
}
.nav-3d:active,
.nav-3d.router-link-active,
.nav-3d.router-link-exact-active {
  box-shadow: 0 1px 0 0 rgba(0,0,0,0.25);
  transform: translateY(2px);
  transition: transform 0.05s ease, box-shadow 0.05s ease;
}

.watt-bump {
  animation: watt-bump 0.75s cubic-bezier(0.36, 0.07, 0.19, 0.97);
}

/* Mobile Menu Slide-In Animation */
.mobile-menu-enter-active,
.mobile-menu-leave-active {
  transition: opacity 0.3s ease;
}

.mobile-menu-enter-active > div:last-child,
.mobile-menu-leave-active > div:last-child {
  transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.mobile-menu-enter-from,
.mobile-menu-leave-to {
  opacity: 0;
}

.mobile-menu-enter-from > div:last-child {
  transform: translateY(-100%);
}

.mobile-menu-leave-to > div:last-child {
  transform: translateY(-100%);
}
</style>
