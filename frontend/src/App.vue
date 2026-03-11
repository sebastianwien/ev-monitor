<script setup lang="ts">
import { ref, watch, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from './stores/auth'
import { useCoinStore } from './stores/coins'
import { storeToRefs } from 'pinia'
import { useWallboxStore } from './stores/wallbox'
import SpritMonitorImport from './components/SpritMonitorImport.vue'
import LogFormModal from './components/LogFormModal.vue'
import FloatingActionButton from './components/FloatingActionButton.vue'
import OnboardingWelcome from './components/OnboardingWelcome.vue'
import DemoBanner from './components/DemoBanner.vue'
import RedditConsentBanner from './components/RedditConsentBanner.vue'
import FeedbackToast from './components/FeedbackToast.vue'
import { Bars3Icon, XMarkIcon, HomeIcon, TruckIcon, ArrowDownTrayIcon, UserIcon, ArrowRightOnRectangleIcon, BoltIcon, ChatBubbleLeftEllipsisIcon, ArrowsRightLeftIcon, Battery0Icon } from '@heroicons/vue/24/outline'
// Note: showImportOverlay kept for backward compat but SpritMonitor moved to /imports
import { HeartIcon } from '@heroicons/vue/24/solid'
import { captureUtmParams } from './utils/reddit-pixel'

const router = useRouter()
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

// Capture UTM parameters on first page load for campaign tracking
onMounted(() => {
  captureUtmParams()
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

const handleLogout = () => {
  sessionStorage.removeItem('impersonating')
  authStore.logout()
  mobileMenuOpen.value = false
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
  <div class="min-h-screen bg-gray-100 flex flex-col">
    <!-- Navigation -->
    <nav class="bg-indigo-600 shadow-md text-white sticky top-0 z-40" v-if="authStore.isAuthenticated()">
      <div class="px-4 py-3">
        <div class="flex justify-between items-center">
          <!-- Left: Logo + Nav Buttons (Desktop) -->
          <div class="flex items-center space-x-4">
            <router-link to="/dashboard" class="flex items-center gap-1.5 text-xl font-bold tracking-wide hover:opacity-80 transition">
              <Battery0Icon class="h-6 w-6" />
              EV Monitor
            </router-link>

            <!-- Compact Icon Nav (768px - 1024px) -->
            <div class="hidden md:flex lg:hidden items-center space-x-2">
              <button
                @click="handleNewLog"
                class="p-2 rounded-md bg-green-600 hover:bg-green-700 transition"
                title="Ladevorgang erfassen">
                <BoltIcon class="h-5 w-5" />
              </button>
              <router-link
                to="/cars"
                class="p-2 rounded-md hover:bg-indigo-500 transition"
                :class="{ 'bg-indigo-700': $route.path === '/cars' }"
                title="Fahrzeuge">
                <TruckIcon class="h-5 w-5" />
              </router-link>
              <router-link
                to="/imports"
                class="p-2 rounded-md bg-indigo-700 hover:bg-indigo-800 transition"
                :class="{ 'bg-indigo-900': $route.path === '/imports' }"
                title="Import">
                <ArrowDownTrayIcon class="h-5 w-5" />
              </router-link>
            </div>

            <!-- Full Nav (1024px+) -->
            <div class="hidden lg:flex items-center space-x-4">
              <button
                @click="handleNewLog"
                class="px-3 py-2 rounded-md text-sm font-medium bg-green-600 hover:bg-green-700 transition">
                Ladevorgang erfassen
              </button>
              <router-link
                to="/cars"
                class="px-3 py-2 rounded-md text-sm font-medium hover:bg-indigo-500 transition"
                :class="{ 'bg-indigo-700': $route.path === '/cars' }">
                Fahrzeuge
              </router-link>
              <router-link
                to="/imports"
                class="flex items-center gap-2 px-4 py-2 bg-indigo-700 hover:bg-indigo-800 rounded-lg text-sm font-medium transition shadow-sm"
                :class="{ 'bg-indigo-900': $route.path === '/imports' }">
                <ArrowDownTrayIcon class="h-5 w-5" />
                Import
              </router-link>
            </div>
          </div>

          <!-- Right: Coin Balance + User Info + Logout (Desktop) / Hamburger (Mobile) -->

          <!-- Compact Right Nav (768px - 1024px) -->
          <div class="hidden md:flex lg:hidden items-center space-x-2">
            <!-- Wallbox dot -->
            <router-link
              v-if="wallboxHasConnections"
              to="/imports?tab=goe"
              :title="`${wallboxConn?.displayName || 'Wallbox'} · ${wallboxConn?.carStateLabel}`"
              class="p-2 rounded-md hover:bg-indigo-500 transition flex items-center justify-center"
            >
              <span
                :class="['w-2.5 h-2.5 rounded-full', wallboxChipColor,
                         wallboxConn?.carState === 2 ? 'animate-pulse' : '']"
              />
            </router-link>
            <router-link
              to="/coins/history"
              class="flex items-center gap-1 px-2 py-1 text-sm bg-indigo-500 bg-opacity-30 border border-indigo-400 rounded-md hover:bg-opacity-50 transition font-medium"
              :class="{ 'watt-bump': balanceBumping }"
              title="Watt-Guthaben">
              <BoltIcon class="h-4 w-4" />
              <span>{{ coinStore.balance }}</span>
            </router-link>
            <router-link
              v-if="authStore.user"
              to="/settings"
              class="p-2 border border-indigo-400 rounded-md bg-indigo-500 bg-opacity-30 hover:bg-opacity-50 transition"
              title="Einstellungen">
              <UserIcon class="h-5 w-5" />
            </router-link>
            <button
              @click="handleLogout"
              class="p-2 bg-indigo-700 hover:bg-indigo-800 rounded-md transition"
              title="Abmelden">
              <ArrowRightOnRectangleIcon class="h-5 w-5" />
            </button>
            <a
              href="https://forms.gle/w4qkLLEv6nYGK3LWA"
              target="_blank"
              rel="noopener noreferrer"
              class="p-2 text-indigo-300 hover:text-white transition"
              title="Feedback geben">
              <ChatBubbleLeftEllipsisIcon class="h-5 w-5" />
            </a>
            <a
              href="https://ko-fi.com/ev_monitor"
              target="_blank"
              rel="noopener noreferrer"
              class="p-2 text-red-400 hover:text-red-300 transition"
              title="Unterstützen">
              <HeartIcon class="h-5 w-5" />
            </a>
          </div>

          <!-- Full Right Nav (1024px+) -->
          <div class="hidden lg:flex items-center space-x-4">
            <!-- Wallbox chip -->
            <router-link
              v-if="wallboxHasConnections"
              to="/imports?tab=goe"
              class="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg border text-xs font-medium transition hover:opacity-80"
              :class="wallboxConn?.carState === 2
                ? 'bg-green-500 bg-opacity-20 border-green-400 text-green-200'
                : wallboxConn?.carState === 5 || wallboxConn?.lastPollError
                  ? 'bg-red-500 bg-opacity-20 border-red-400 text-red-200'
                  : 'bg-indigo-500 bg-opacity-20 border-indigo-400 text-indigo-200'"
            >
              <span
                :class="['w-2 h-2 rounded-full flex-shrink-0', wallboxChipColor,
                         wallboxConn?.carState === 2 ? 'animate-pulse' : '']"
              />
              <span class="truncate max-w-[120px]">{{ wallboxConn?.displayName || 'Wallbox' }}</span>
              <span class="opacity-70">· {{ wallboxConn?.carStateLabel }}</span>
            </router-link>
            <div class="relative group">
              <router-link
                to="/coins/history"
                class="flex items-center gap-1.5 px-3 py-1.5 text-sm bg-indigo-500 bg-opacity-30 border border-indigo-400 rounded-md hover:bg-opacity-50 transition font-medium"
                :class="{ 'watt-bump': balanceBumping }">
                <BoltIcon class="h-4 w-4" />
                <span>{{ coinStore.balance }}</span>
              </router-link>
              <!-- Tooltip -->
              <div class="absolute right-0 top-full mt-2 w-48 bg-gray-900 text-white text-xs rounded-lg shadow-xl p-3 opacity-0 group-hover:opacity-100 pointer-events-none transition-opacity duration-150 z-50">
                <div class="flex justify-between mb-1.5">
                  <span class="text-gray-400">Gesamt</span>
                  <span class="font-semibold">{{ coinStore.balance }} Watt</span>
                </div>
                <div class="flex justify-between border-t border-gray-700 pt-1.5">
                  <span class="text-gray-400">Diesen Monat</span>
                  <span class="font-semibold text-yellow-400">+{{ coinStore.coinsThisMonth }} Watt</span>
                </div>
                <!-- Arrow -->
                <div class="absolute -top-1.5 right-4 w-3 h-3 bg-gray-900 rotate-45"></div>
              </div>
            </div>
            <router-link
              v-if="authStore.user"
              to="/settings"
              class="flex items-center gap-2 px-3 py-1.5 text-sm border border-indigo-400 rounded-md bg-indigo-500 bg-opacity-30 hover:bg-opacity-50 transition">
              <UserIcon class="h-4 w-4" />
              <span>{{ authStore.user.username || authStore.user.sub }}</span>
            </router-link>
            <button @click="handleLogout" class="px-4 py-2 bg-indigo-700 hover:bg-indigo-800 rounded-lg text-sm font-medium transition shadow-sm">
              Abmelden
            </button>
            <a
              href="https://forms.gle/w4qkLLEv6nYGK3LWA"
              target="_blank"
              rel="noopener noreferrer"
              class="text-indigo-300 hover:text-white transition"
              title="Feedback geben"
            >
              <ChatBubbleLeftEllipsisIcon class="h-5 w-5" />
            </a>
            <a
              href="https://ko-fi.com/ev_monitor"
              target="_blank"
              rel="noopener noreferrer"
              class="text-red-400 hover:text-red-300 transition"
              title="EV Monitor unterstützen"
            >
              <HeartIcon class="h-5 w-5" />
            </a>
          </div>

          <!-- Mobile: Icons + Hamburger Button -->
          <div class="md:hidden flex items-center gap-3">
            <!-- Wallbox dot -->
            <router-link
              v-if="wallboxHasConnections"
              to="/imports?tab=goe"
              :title="`${wallboxConn?.displayName || 'Wallbox'} · ${wallboxConn?.carStateLabel}`"
              class="flex items-center justify-center"
            >
              <span
                :class="['w-2.5 h-2.5 rounded-full', wallboxChipColor,
                         wallboxConn?.carState === 2 ? 'animate-pulse' : '']"
              />
            </router-link>
            <router-link
              to="/coins/history"
              class="flex items-center gap-1 px-2 py-1 text-sm bg-indigo-500 bg-opacity-30 border border-indigo-400 rounded-md hover:bg-opacity-50 transition font-medium"
              :class="{ 'watt-bump': balanceBumping }"
              title="Watt-Guthaben">
              <BoltIcon class="h-4 w-4" />
              <span>{{ coinStore.balance }}</span>
            </router-link>
            <a
              href="https://forms.gle/w4qkLLEv6nYGK3LWA"
              target="_blank"
              rel="noopener noreferrer"
              class="text-indigo-300 hover:text-white transition"
              title="Feedback geben">
              <ChatBubbleLeftEllipsisIcon class="h-5 w-5" />
            </a>
            <a
              href="https://ko-fi.com/ev_monitor"
              target="_blank"
              rel="noopener noreferrer"
              class="text-red-400 hover:text-red-300 transition"
              title="EV Monitor unterstützen">
              <HeartIcon class="h-5 w-5" />
            </a>
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
              class="flex items-center gap-2 px-3 py-2 rounded-md text-sm font-medium hover:bg-indigo-600 transition"
              :class="{ 'bg-indigo-800': $route.path === '/dashboard' }">
              <HomeIcon class="h-5 w-5" />
              <span>Dashboard</span>
            </router-link>
            <router-link
              to="/cars"
              @click="closeMobileMenu"
              class="flex items-center gap-2 px-3 py-2 rounded-md text-sm font-medium hover:bg-indigo-600 transition"
              :class="{ 'bg-indigo-800': $route.path === '/cars' }">
              <TruckIcon class="h-5 w-5" />
              <span>Fahrzeuge</span>
            </router-link>
            <router-link
              to="/imports"
              @click="closeMobileMenu"
              class="flex items-center gap-2 px-3 py-2 rounded-md text-sm font-medium hover:bg-indigo-600 transition"
              :class="{ 'bg-indigo-800': $route.path === '/imports' }">
              <ArrowDownTrayIcon class="h-5 w-5" />
              <span>Import</span>
            </router-link>
            <router-link
              to="/modelle"
              @click="closeMobileMenu"
              class="flex items-center gap-2 px-3 py-2 rounded-md text-sm font-medium hover:bg-indigo-600 transition"
              :class="{ 'bg-indigo-800': $route.path.startsWith('/modelle') }">
              <ArrowsRightLeftIcon class="h-5 w-5" />
              <span>Modelle vergleichen</span>
            </router-link>
            <router-link
              v-if="authStore.user"
              to="/settings"
              @click="closeMobileMenu"
              class="flex items-center gap-2 px-3 py-2 rounded-md text-sm font-medium hover:bg-indigo-600 transition"
              :class="{ 'bg-indigo-800': $route.path === '/settings' }">
              <UserIcon class="h-5 w-5" />
              <span>{{ authStore.user.username || authStore.user.sub }}</span>
            </router-link>
            <button
              @click="handleLogout"
              class="w-full flex items-center gap-2 px-3 py-2 rounded-md text-sm font-medium hover:bg-indigo-600 transition text-red-300">
              <ArrowRightOnRectangleIcon class="h-5 w-5" />
              <span>Abmelden</span>
            </button>
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
    <main :class="{ 'md:py-10 md:px-4': authStore.isAuthenticated() }">
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
            <router-link to="/agb" class="hover:text-green-600 underline">AGB</router-link> ·
            <a href="https://github.com/sebastianwien/ev-monitor" target="_blank" rel="noopener noreferrer" class="hover:text-green-600 underline">GitHub</a>
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
