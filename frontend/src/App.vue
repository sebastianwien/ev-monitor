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
import { useCarStore } from './stores/car'
import SpritMonitorImport from './components/imports/SpritMonitorImport.vue'
import SupportPopover from './components/settings/SupportPopover.vue'
import LeaderboardTicker from './components/shared/LeaderboardTicker.vue'
import LogFormModal from './components/log-form/LogFormModal.vue'
import FloatingActionButton from './components/shared/FloatingActionButton.vue'
import OnboardingWelcome from './components/shared/OnboardingWelcome.vue'
import FeatureAnnouncementModal from './components/shared/FeatureAnnouncementModal.vue'
import DemoBanner from './components/demo/DemoBanner.vue'
import DemoWelcomeModal from './components/demo/DemoWelcomeModal.vue'
import RedditConsentBanner from './components/shared/RedditConsentBanner.vue'
import FeedbackToast from './components/shared/FeedbackToast.vue'
import { Bars3Icon, XMarkIcon, HomeIcon, ArrowDownTrayIcon, UserIcon, BoltIcon, ChatBubbleLeftEllipsisIcon, ArrowsRightLeftIcon } from '@heroicons/vue/24/outline'
// Note: showImportOverlay kept for backward compat but SpritMonitor moved to /imports
import { captureUtmParams, captureReferrer } from './utils/reddit-pixel'
import { detectCountry } from './composables/useCountryDetection'
import { useHaptic } from './composables/useHaptic'
import { useThemeStore } from './stores/theme'
import ThemeToggle from './components/shared/ThemeToggle.vue'
import LocaleSwitcher from './components/shared/LocaleSwitcher.vue'
import { useTickerState } from './composables/useTickerState'
import { useCountryStore } from './stores/country'
import { subscriptionService } from './api/subscriptionService'

const { haptic } = useHaptic()
const { t } = useI18n()
const countryStore = useCountryStore()
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
const carStore = useCarStore()
const { cars: carList } = storeToRefs(carStore)
const showWallboxChip = computed(() => wallboxHasConnections.value && carList.value.length !== 1)
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
    wallboxStore.init(!!authStore.user?.demoAccount)
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
  detectCountry()

  if (authStore.isAuthenticated() && !authStore.isDemoAccount) {
    subscriptionService.getStatus().then(s => authStore.setPremium(s.isPremium)).catch(() => {})
  }

  // Auto-haptic for all btn-3d elements
  const { haptic: triggerHaptic } = useHaptic()
  document.addEventListener('pointerdown', (e) => {
    if ((e.target as Element)?.closest('.btn-3d')) triggerHaptic()
  })

  // Auto-delay clicks on btn-3d-delay so the press animation is visible before navigation
  document.addEventListener('click', (e) => {
    const btn = (e.target as Element)?.closest('.btn-3d-delay')
    if (!btn || (e as any).__delayed) return
    e.stopImmediatePropagation()
    e.preventDefault()
    setTimeout(() => {
      const evt = new MouseEvent('click', { bubbles: true, cancelable: true })
      ;(evt as any).__delayed = true
      btn.dispatchEvent(evt)
    }, 150)
  }, { capture: true })
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
  <div :class="['min-h-screen flex flex-col', authStore.isAuthenticated() ? 'app-wallpaper' : 'bg-gray-100 dark:bg-gray-950']">
    <!-- Navigation -->
    <nav class="bg-white dark:bg-gray-900 border-b-2 border-gray-900 dark:border-white text-gray-900 dark:text-gray-100 fixed top-0 left-0 right-0 z-40" v-if="authStore.isAuthenticated()">
      <div class="px-4 py-2.5">
        <div class="flex justify-between items-center gap-3">
          <!-- Left: Logo + Nav Buttons (Desktop) -->
          <div class="flex items-center gap-3">
            <router-link to="/dashboard" class="flex items-center gap-2 hover:opacity-80 transition whitespace-nowrap">
              <span class="shrink-0 rounded-sm border-2 border-gray-900 dark:border-white bg-amber-500 p-1.5 w-9 h-9 flex items-center justify-center shadow-[2px_2px_0_0_#030712] dark:shadow-[2px_2px_0_0_#ffffff]">
                <BoltIcon class="h-5 w-5 text-gray-950" />
              </span>
              <span class="hidden sm:inline text-lg font-extrabold tracking-tight text-gray-900 dark:text-white">EV Monitor</span>
            </router-link>

            <!-- Compact Icon Nav (640px - 1024px) -->
            <div class="hidden sm:flex lg:hidden items-center gap-1.5">
              <button
                @click="handleNewLog(); haptic()"
                class="p-2 rounded-sm border-2 border-gray-900 dark:border-white bg-emerald-500 hover:bg-emerald-400 text-gray-950 shadow-[2px_2px_0_0_#030712] dark:shadow-[2px_2px_0_0_#ffffff] active:translate-x-[2px] active:translate-y-[2px] active:shadow-none transition-[transform,box-shadow] duration-75"
                title="Ladevorgang erfassen">
                <BoltIcon class="h-5 w-5" />
              </button>
              <router-link
                to="/imports"
                class="p-2 rounded-sm border-2 border-gray-900 dark:border-white bg-white dark:bg-gray-800 hover:bg-gray-50 dark:hover:bg-gray-700/50 shadow-[2px_2px_0_0_#030712] dark:shadow-[2px_2px_0_0_#ffffff] active:translate-x-[2px] active:translate-y-[2px] active:shadow-none transition-[transform,box-shadow] duration-75"
                @click="haptic()"
                :class="{ 'bg-sky-700 text-white dark:bg-sky-700': $route.path === '/imports' }"
                title="Import">
                <ArrowDownTrayIcon class="h-5 w-5" />
              </router-link>
              <router-link
                v-if="authStore.isAutoSyncLive || authStore.isBetaTester || authStore.isAdmin"
                to="/live"
                class="p-2 rounded-sm border-2 border-gray-900 dark:border-white bg-white dark:bg-gray-800 hover:bg-gray-50 dark:hover:bg-gray-700/50 shadow-[2px_2px_0_0_#030712] dark:shadow-[2px_2px_0_0_#ffffff] active:translate-x-[2px] active:translate-y-[2px] active:shadow-none transition-[transform,box-shadow] duration-75"
                @click="haptic()"
                :class="{ 'bg-emerald-500 text-gray-950 dark:bg-emerald-500': $route.path === '/live' }"
                :title="t('live.title')">
                <BoltIcon class="h-5 w-5" />
              </router-link>
              <router-link
                to="/modelle"
                class="p-2 rounded-sm border-2 border-gray-900 dark:border-white bg-white dark:bg-gray-800 hover:bg-gray-50 dark:hover:bg-gray-700/50 shadow-[2px_2px_0_0_#030712] dark:shadow-[2px_2px_0_0_#ffffff] active:translate-x-[2px] active:translate-y-[2px] active:shadow-none transition-[transform,box-shadow] duration-75"
                @click="haptic()"
                :class="{ 'bg-indigo-600 text-white dark:bg-indigo-600': $route.path.startsWith('/modelle') }"
                :title="t('nav.models_compare')">
                <ArrowsRightLeftIcon class="h-5 w-5" />
              </router-link>
              <router-link
                to="/dashboard"
                class="p-2 rounded-sm border-2 border-gray-900 dark:border-white bg-white dark:bg-gray-800 hover:bg-gray-50 dark:hover:bg-gray-700/50 shadow-[2px_2px_0_0_#030712] dark:shadow-[2px_2px_0_0_#ffffff] active:translate-x-[2px] active:translate-y-[2px] active:shadow-none transition-[transform,box-shadow] duration-75"
                @click="haptic()"
                :class="{ 'bg-amber-500 text-gray-950 dark:bg-amber-500': $route.path === '/dashboard' }"
                title="Dashboard">
                <HomeIcon class="h-5 w-5" />
              </router-link>
            </div>

            <!-- Full Nav (1024px+) -->
            <div class="hidden lg:flex items-center gap-1.5">
              <router-link
                to="/dashboard"
                class="flex items-center gap-2 px-3 py-2 rounded-sm border-2 border-gray-900 dark:border-white bg-white dark:bg-gray-800 hover:bg-gray-50 dark:hover:bg-gray-700/50 text-sm font-bold uppercase tracking-wider shadow-[2px_2px_0_0_#030712] dark:shadow-[2px_2px_0_0_#ffffff] active:translate-x-[2px] active:translate-y-[2px] active:shadow-none transition-[transform,box-shadow] duration-75"
                @click="haptic()"
                :class="{ 'bg-amber-500 text-gray-950 dark:bg-amber-500 dark:text-gray-950': $route.path === '/dashboard' }"
                title="Dashboard">
                <HomeIcon class="h-5 w-5" />
              </router-link>
              <router-link
                to="/imports"
                class="flex items-center gap-2 px-3 py-2 rounded-sm border-2 border-gray-900 dark:border-white bg-white dark:bg-gray-800 hover:bg-gray-50 dark:hover:bg-gray-700/50 text-[11px] font-bold uppercase tracking-wider shadow-[2px_2px_0_0_#030712] dark:shadow-[2px_2px_0_0_#ffffff] active:translate-x-[2px] active:translate-y-[2px] active:shadow-none transition-[transform,box-shadow] duration-75"
                @click="haptic()"
                :class="{ 'bg-sky-700 text-white dark:bg-sky-700 dark:text-white': $route.path === '/imports' }">
                <ArrowDownTrayIcon class="h-5 w-5" />
                Import
              </router-link>
              <router-link
                v-if="authStore.isAutoSyncLive || authStore.isBetaTester || authStore.isAdmin"
                to="/live"
                class="flex items-center gap-2 px-3 py-2 rounded-sm border-2 border-gray-900 dark:border-white bg-white dark:bg-gray-800 hover:bg-gray-50 dark:hover:bg-gray-700/50 text-[11px] font-bold uppercase tracking-wider shadow-[2px_2px_0_0_#030712] dark:shadow-[2px_2px_0_0_#ffffff] active:translate-x-[2px] active:translate-y-[2px] active:shadow-none transition-[transform,box-shadow] duration-75"
                @click="haptic()"
                :class="{ 'bg-emerald-500 text-gray-950 dark:bg-emerald-500 dark:text-gray-950': $route.path === '/live' }">
                <BoltIcon class="h-5 w-5" />
                {{ t('live.title') }}
              </router-link>
              <router-link
                to="/modelle"
                class="flex items-center gap-2 px-3 py-2 rounded-sm border-2 border-gray-900 dark:border-white bg-white dark:bg-gray-800 hover:bg-gray-50 dark:hover:bg-gray-700/50 text-[11px] font-bold uppercase tracking-wider shadow-[2px_2px_0_0_#030712] dark:shadow-[2px_2px_0_0_#ffffff] active:translate-x-[2px] active:translate-y-[2px] active:shadow-none transition-[transform,box-shadow] duration-75"
                @click="haptic()"
                :class="{ 'bg-indigo-600 text-white dark:bg-indigo-600 dark:text-white': $route.path.startsWith('/modelle') }">
                <ArrowsRightLeftIcon class="h-5 w-5" />
                {{ t('nav.models_compare') }}
              </router-link>
              <button
                @click="handleNewLog(); haptic()"
                class="p-2 rounded-sm border-2 border-gray-900 dark:border-white bg-emerald-500 hover:bg-emerald-400 text-gray-950 shadow-[2px_2px_0_0_#030712] dark:shadow-[2px_2px_0_0_#ffffff] active:translate-x-[2px] active:translate-y-[2px] active:shadow-none transition-[transform,box-shadow] duration-75"
                title="Ladevorgang erfassen">
                <BoltIcon class="h-5 w-5" />
              </button>
            </div>
          </div>

          <!-- Right: Coin Balance + User Info + Logout (Desktop) / Hamburger (Mobile) -->

          <!-- Compact Right Nav (768px - 1280px) -->
          <div class="hidden md:flex xl:hidden items-center gap-1.5">
            <LocaleSwitcher variant="nav" />
            <ThemeToggle v-if="!authStore.isDemoAccount" />
            <!-- Wallbox dot -->
            <button
              v-if="showWallboxChip"
              @click="goToGoeTab"
              :title="`${wallboxConn?.displayName || 'Wallbox'} · ${wallboxConn?.carStateLabel}`"
              class="p-2 rounded-sm border-2 border-gray-900 dark:border-white bg-white dark:bg-gray-800 hover:bg-gray-50 dark:hover:bg-gray-700/50 shadow-[2px_2px_0_0_#030712] dark:shadow-[2px_2px_0_0_#ffffff] active:translate-x-[2px] active:translate-y-[2px] active:shadow-none transition-[transform,box-shadow] duration-75 flex items-center justify-center"
            >
              <span
                :class="['w-2.5 h-2.5 rounded-full', wallboxChipColor,
                         wallboxConn?.carState === 2 ? 'animate-pulse' : '']"
              />
            </button>
            <router-link
              to="/coins/history"
              class="flex items-center gap-1 px-2 h-9 text-[11px] font-bold uppercase tracking-wider border-2 border-gray-900 dark:border-white bg-amber-500 text-gray-950 hover:bg-amber-400 rounded-sm shadow-[2px_2px_0_0_#030712] dark:shadow-[2px_2px_0_0_#ffffff] active:translate-x-[2px] active:translate-y-[2px] active:shadow-none transition-[transform,box-shadow] duration-75"
              @click="haptic()"
              :class="{ 'watt-bump': balanceBumping }"
              title="Watt-Guthaben">
              <BoltIcon class="h-4 w-4" />
              <span>{{ coinStore.balance }}</span>
            </router-link>
            <router-link
              v-if="authStore.user"
              to="/settings"
              class="flex items-center justify-center h-9 w-9 border-2 border-gray-900 dark:border-white bg-white dark:bg-gray-800 hover:bg-gray-50 dark:hover:bg-gray-700/50 rounded-sm shadow-[2px_2px_0_0_#030712] dark:shadow-[2px_2px_0_0_#ffffff] active:translate-x-[2px] active:translate-y-[2px] active:shadow-none transition-[transform,box-shadow] duration-75"
              :class="{ 'bg-indigo-600 text-white dark:bg-indigo-600 dark:text-white': $route.path === '/settings' }"
              @click="haptic()"
              title="Einstellungen">
              <UserIcon class="h-5 w-5" />
            </router-link>
            <button
              data-tally-open="vGB8XA" data-tally-emoji-text="👋" data-tally-emoji-animation="wave"
              class="p-2 rounded-sm border-2 border-gray-900 dark:border-white bg-white dark:bg-gray-800 hover:bg-gray-50 dark:hover:bg-gray-700/50 text-gray-700 dark:text-gray-200 shadow-[2px_2px_0_0_#030712] dark:shadow-[2px_2px_0_0_#ffffff] active:translate-x-[2px] active:translate-y-[2px] active:shadow-none transition-[transform,box-shadow] duration-75"
              title="Feedback geben">
              <ChatBubbleLeftEllipsisIcon class="h-5 w-5" />
            </button>
            <SupportPopover variant="nav" />
          </div>

          <!-- Full Right Nav (1280px+) -->
          <div class="hidden xl:flex items-center gap-2">
            <LocaleSwitcher variant="nav" />
            <ThemeToggle v-if="!authStore.isDemoAccount" />
            <!-- Wallbox chip -->
            <button
              v-if="showWallboxChip"
              @click="goToGoeTab"
              class="flex items-center gap-1.5 px-2.5 py-1.5 rounded-sm border-2 text-[11px] font-bold uppercase tracking-wider shadow-[2px_2px_0_0_#030712] dark:shadow-[2px_2px_0_0_#ffffff] active:translate-x-[2px] active:translate-y-[2px] active:shadow-none transition-[transform,box-shadow] duration-75"
              :class="wallboxConn?.carState === 2
                ? 'bg-emerald-500 border-gray-900 dark:border-white text-gray-950'
                : wallboxConn?.carState === 5 || wallboxConn?.lastPollError
                  ? 'bg-red-500 border-gray-900 dark:border-white text-white'
                  : 'bg-white dark:bg-gray-800 border-gray-900 dark:border-white text-gray-900 dark:text-gray-100'"
            >
              <span
                :class="['w-2 h-2 rounded-full flex-shrink-0', wallboxChipColor,
                         wallboxConn?.carState === 2 ? 'animate-pulse' : '']"
              />
              <span class="truncate max-w-[120px] normal-case tracking-normal">{{ wallboxConn?.displayName || 'Wallbox' }}</span>
              <span class="opacity-90 normal-case tracking-normal">· {{ wallboxConn?.carStateLabel }}</span>
            </button>
            <div class="relative group">
              <router-link
                to="/coins/history"
                class="flex items-center gap-1.5 px-3 py-1.5 text-[11px] font-bold uppercase tracking-wider border-2 border-gray-900 dark:border-white bg-amber-500 text-gray-950 hover:bg-amber-400 rounded-sm shadow-[2px_2px_0_0_#030712] dark:shadow-[2px_2px_0_0_#ffffff] active:translate-x-[2px] active:translate-y-[2px] active:shadow-none transition-[transform,box-shadow] duration-75"
                @click="haptic()"
                :class="{ 'watt-bump': balanceBumping }">
                <BoltIcon class="h-4 w-4" />
                <span>{{ coinStore.balance }}</span>
              </router-link>
              <!-- Tooltip -->
              <div class="absolute right-0 top-full mt-2 w-48 bg-white dark:bg-gray-900 border-2 border-gray-900 dark:border-white text-gray-900 dark:text-gray-100 text-xs rounded-sm shadow-[4px_4px_0_0_#030712] dark:shadow-[4px_4px_0_0_#ffffff] p-3 opacity-0 group-hover:opacity-100 pointer-events-none transition-opacity duration-150 z-50">
                <div class="flex justify-between mb-1.5">
                  <span class="text-gray-500 dark:text-gray-400 font-bold uppercase tracking-wider text-[10px]">{{ t('dashboard.coins_total') }}</span>
                  <span class="font-bold">{{ coinStore.balance }} Watt</span>
                </div>
                <div class="flex justify-between border-t-2 border-gray-300 dark:border-gray-700 pt-1.5">
                  <span class="text-gray-500 dark:text-gray-400 font-bold uppercase tracking-wider text-[10px]">{{ t('dashboard.coins_this_month') }}</span>
                  <span class="font-bold text-amber-600 dark:text-amber-500">+{{ coinStore.coinsThisMonth }} Watt</span>
                </div>
              </div>
            </div>
            <router-link
              v-if="authStore.user"
              to="/settings"
              class="flex items-center justify-center h-9 w-9 border-2 border-gray-900 dark:border-white bg-white dark:bg-gray-800 hover:bg-gray-50 dark:hover:bg-gray-700/50 rounded-sm shadow-[2px_2px_0_0_#030712] dark:shadow-[2px_2px_0_0_#ffffff] active:translate-x-[2px] active:translate-y-[2px] active:shadow-none transition-[transform,box-shadow] duration-75"
              :class="{ 'bg-indigo-600 text-white dark:bg-indigo-600 dark:text-white': $route.path === '/settings' }"
              @click="haptic()">
              <UserIcon class="h-5 w-5" />
            </router-link>
            <button
              data-tally-open="vGB8XA" data-tally-emoji-text="👋" data-tally-emoji-animation="wave"
              class="p-2 rounded-sm border-2 border-gray-900 dark:border-white bg-white dark:bg-gray-800 hover:bg-gray-50 dark:hover:bg-gray-700/50 text-gray-700 dark:text-gray-200 shadow-[2px_2px_0_0_#030712] dark:shadow-[2px_2px_0_0_#ffffff] active:translate-x-[2px] active:translate-y-[2px] active:shadow-none transition-[transform,box-shadow] duration-75"
              title="Feedback geben"
            >
              <ChatBubbleLeftEllipsisIcon class="h-5 w-5" />
            </button>
            <SupportPopover variant="nav" />
          </div>

          <!-- Mobile: Icons + Hamburger Button -->
          <div class="md:hidden flex items-center gap-1.5">
            <LocaleSwitcher variant="nav" />
            <ThemeToggle v-if="!authStore.isDemoAccount" />
            <!-- Wallbox dot -->
            <button
              v-if="showWallboxChip"
              @click="goToGoeTab"
              :title="`${wallboxConn?.displayName || 'Wallbox'} · ${wallboxConn?.carStateLabel}`"
              class="flex items-center justify-center p-1.5"
            >
              <span
                :class="['w-2.5 h-2.5 rounded-full', wallboxChipColor,
                         wallboxConn?.carState === 2 ? 'animate-pulse' : '']"
              />
            </button>
            <router-link
              to="/coins/history"
              class="flex items-center gap-1 px-2 py-1.5 text-[11px] font-bold uppercase tracking-wider border-2 border-gray-900 dark:border-white bg-amber-500 text-gray-950 hover:bg-amber-400 rounded-sm shadow-[2px_2px_0_0_#030712] dark:shadow-[2px_2px_0_0_#ffffff] active:translate-x-[2px] active:translate-y-[2px] active:shadow-none transition-[transform,box-shadow] duration-75"
              :class="{ 'watt-bump': balanceBumping }"
              title="Watt-Guthaben">
              <BoltIcon class="h-4 w-4" />
              <span>{{ coinStore.balance }}</span>
            </router-link>
            <button
              @click="mobileMenuOpen = !mobileMenuOpen"
              class="p-2 rounded-sm border-2 border-gray-900 dark:border-white bg-white dark:bg-gray-800 hover:bg-gray-50 dark:hover:bg-gray-700/50 text-gray-900 dark:text-gray-100 shadow-[2px_2px_0_0_#030712] dark:shadow-[2px_2px_0_0_#ffffff] active:translate-x-[2px] active:translate-y-[2px] active:shadow-none transition-[transform,box-shadow] duration-75"
              aria-label="Menu">
              <Bars3Icon v-if="!mobileMenuOpen" class="h-6 w-6" />
              <XMarkIcon v-else class="h-6 w-6" />
            </button>
          </div>
        </div>
      </div>

    </nav>

    <!-- Leaderboard Ticker (below nav, only when authenticated) -->
    <LeaderboardTicker v-if="authStore.isAuthenticated() && !authStore.isDemoAccount && ['DE', 'AT', 'CH'].includes(countryStore.country)" />

    <!-- Mobile Menu Overlay -->
    <Transition name="mobile-menu">
      <div
        v-if="mobileMenuOpen"
        class="fixed inset-0 z-50 md:hidden"
        @click.self="closeMobileMenu">
        <!-- Backdrop -->
        <div class="absolute inset-0 bg-black bg-opacity-50" @click="closeMobileMenu"></div>

        <!-- Menu Panel (slides in from top) -->
        <div class="absolute top-0 left-0 right-0 bg-white dark:bg-gray-900 border-b-2 border-gray-900 dark:border-white shadow-[0_4px_0_0_#030712] dark:shadow-[0_4px_0_0_#ffffff] overflow-y-auto max-h-[80vh]">
          <!-- Header -->
          <div class="flex items-center justify-between px-4 py-3 border-b-2 border-gray-300 dark:border-gray-700">
            <div class="flex items-center gap-2">
              <span class="shrink-0 rounded-sm border-2 border-gray-900 dark:border-white bg-amber-500 p-1.5 w-9 h-9 flex items-center justify-center shadow-[2px_2px_0_0_#030712] dark:shadow-[2px_2px_0_0_#ffffff]">
                <BoltIcon class="h-5 w-5 text-gray-950" />
              </span>
              <span class="text-base font-extrabold tracking-tight text-gray-900 dark:text-white">EV Monitor</span>
            </div>
            <button
              @click="closeMobileMenu"
              class="p-2 rounded-sm border-2 border-gray-900 dark:border-white bg-white dark:bg-gray-800 hover:bg-gray-50 dark:hover:bg-gray-700/50 shadow-[2px_2px_0_0_#030712] dark:shadow-[2px_2px_0_0_#ffffff] active:translate-x-[2px] active:translate-y-[2px] active:shadow-none transition-[transform,box-shadow] duration-75"
              aria-label="Menü schließen">
              <XMarkIcon class="h-5 w-5 text-gray-900 dark:text-gray-100" />
            </button>
          </div>

          <!-- Nav rows (flat accordion-style) -->
          <div class="divide-y-2 divide-gray-300 dark:divide-gray-700">
            <router-link
              to="/dashboard"
              @click="closeMobileMenu"
              class="w-full flex items-center gap-3 px-4 py-3.5 text-left hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors border-l-2"
              :class="$route.path === '/dashboard' ? 'border-l-amber-500 bg-amber-50/40 dark:bg-amber-950/20' : 'border-l-transparent'">
              <div class="shrink-0 rounded-sm border-2 border-gray-900 dark:border-white p-2 w-10 h-10 flex items-center justify-center bg-amber-500">
                <HomeIcon class="h-5 w-5 text-gray-950" />
              </div>
              <span class="font-medium text-gray-900 dark:text-gray-100 text-sm">Dashboard</span>
            </router-link>
            <router-link
              to="/imports"
              @click="closeMobileMenu"
              class="w-full flex items-center gap-3 px-4 py-3.5 text-left hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors border-l-2"
              :class="$route.path === '/imports' ? 'border-l-sky-700 bg-sky-50/40 dark:bg-sky-950/20' : 'border-l-transparent'">
              <div class="shrink-0 rounded-sm border-2 border-gray-900 dark:border-white p-2 w-10 h-10 flex items-center justify-center bg-sky-700">
                <ArrowDownTrayIcon class="h-5 w-5 text-white" />
              </div>
              <span class="font-medium text-gray-900 dark:text-gray-100 text-sm">Import</span>
            </router-link>
            <router-link
              v-if="authStore.isAutoSyncLive || authStore.isBetaTester || authStore.isAdmin"
              to="/live"
              @click="closeMobileMenu"
              class="w-full flex items-center gap-3 px-4 py-3.5 text-left hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors border-l-2"
              :class="$route.path === '/live' ? 'border-l-emerald-500 bg-emerald-50/40 dark:bg-emerald-950/20' : 'border-l-transparent'">
              <div class="shrink-0 rounded-sm border-2 border-gray-900 dark:border-white p-2 w-10 h-10 flex items-center justify-center bg-emerald-500">
                <BoltIcon class="h-5 w-5 text-gray-950" />
              </div>
              <span class="font-medium text-gray-900 dark:text-gray-100 text-sm">{{ t('live.title') }}</span>
            </router-link>
            <router-link
              to="/modelle"
              @click="closeMobileMenu"
              class="w-full flex items-center gap-3 px-4 py-3.5 text-left hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors border-l-2"
              :class="$route.path.startsWith('/modelle') ? 'border-l-indigo-600 bg-indigo-50/40 dark:bg-indigo-950/20' : 'border-l-transparent'">
              <div class="shrink-0 rounded-sm border-2 border-gray-900 dark:border-white p-2 w-10 h-10 flex items-center justify-center bg-indigo-600">
                <ArrowsRightLeftIcon class="h-5 w-5 text-white" />
              </div>
              <span class="font-medium text-gray-900 dark:text-gray-100 text-sm">{{ t('nav.models_compare') }}</span>
            </router-link>
            <router-link
              v-if="authStore.user"
              to="/settings"
              @click="closeMobileMenu"
              class="w-full flex items-center gap-3 px-4 py-3.5 text-left hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors border-l-2"
              :class="$route.path === '/settings' ? 'border-l-indigo-600 bg-indigo-50/40 dark:bg-indigo-950/20' : 'border-l-transparent'">
              <div class="shrink-0 rounded-sm border-2 border-gray-900 dark:border-white p-2 w-10 h-10 flex items-center justify-center bg-gray-900 dark:bg-gray-700">
                <UserIcon class="h-5 w-5 text-white" />
              </div>
              <span class="font-medium text-gray-900 dark:text-gray-100 text-sm truncate">{{ authStore.user.username || authStore.user.sub }}</span>
            </router-link>
            <button
              data-tally-open="vGB8XA" data-tally-emoji-text="👋" data-tally-emoji-animation="wave"
              @click="closeMobileMenu"
              class="w-full flex items-center gap-3 px-4 py-3.5 text-left hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors border-l-2 border-l-transparent">
              <div class="shrink-0 rounded-sm border-2 border-gray-900 dark:border-white p-2 w-10 h-10 flex items-center justify-center bg-white dark:bg-gray-800">
                <ChatBubbleLeftEllipsisIcon class="h-5 w-5 text-gray-900 dark:text-gray-100" />
              </div>
              <span class="font-medium text-gray-900 dark:text-gray-100 text-sm">Feedback</span>
            </button>
          </div>
          <div class="px-4 py-3 border-t-2 border-gray-300 dark:border-gray-700">
            <SupportPopover variant="nav" />
          </div>
        </div>
      </div>
    </Transition>
    <!-- Impersonation Banner -->
    <div
      v-if="impersonatingAs"
      class="sticky top-0 z-50 flex items-center justify-between px-4 py-2 bg-amber-400 border-b-2 border-gray-900 text-gray-950 text-[11px] font-bold uppercase tracking-wider">
      <span class="normal-case tracking-normal text-sm">Impersonation aktiv: <strong>{{ impersonatingAs }}</strong> · Token läuft in 1h ab</span>
      <button
        @click="stopImpersonation"
        class="px-3 py-1.5 bg-gray-900 text-amber-100 rounded-sm border-2 border-gray-900 hover:bg-gray-800 shadow-[2px_2px_0_0_#030712] active:translate-x-[2px] active:translate-y-[2px] active:shadow-none transition-[transform,box-shadow] duration-75 text-[11px] font-bold uppercase tracking-wider">
        Beenden
      </button>
    </div>

    <!-- Demo Banner (shown for seed/demo accounts) -->
    <DemoBanner v-if="authStore.isDemoAccount" />
    <main
      :class="[
        authStore.isAuthenticated() ? 'md:px-4' : '',
        authStore.isDemoAccount ? 'pb-14' : authStore.isAuthenticated() ? 'md:pb-10' : ''
      ]"
      :style="{ paddingTop: mainPaddingTop, transition: 'padding-top 0.3s ease' }">
      <router-view v-slot="{ Component, route }">
        <Transition :name="(route.meta.transition as string) || ''" mode="out-in">
          <KeepAlive :include="['DashboardView', 'LogsView']">
            <component :is="Component" />
          </KeepAlive>
        </Transition>
      </router-view>
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
            <router-link to="/consumption-methodology" class="hover:text-green-600 underline">{{ t('footer.consumption_methodology') }}</router-link> ·
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
    <FloatingActionButton v-if="authStore.isAuthenticated() && !authStore.isDemoAccount && !isOnboardingVisible && !showLogFormModal && $route.path !== '/erfassen'" @click="handleNewLog" />

    <!-- Log Form Modal (Desktop only) -->
    <LogFormModal v-if="showLogFormModal && authStore.isAuthenticated()" @close="showLogFormModal = false" />

    <!-- Onboarding Welcome (First-time users) -->
    <OnboardingWelcome v-if="authStore.isAuthenticated()" />
    <FeatureAnnouncementModal v-if="authStore.isAuthenticated() && !authStore.isDemoAccount" />

    <!-- Reddit Consent Banner (only for paid Reddit traffic) -->
    <RedditConsentBanner />

    <!-- Feedback Toast (delayed, dismissible) -->
    <FeedbackToast />
    <DemoWelcomeModal v-if="authStore.isDemoAccount" />
  </div>
</template>

<style scoped>
@keyframes watt-bump {
  0%   { transform: scale(1); }
  25%  { transform: scale(1.35); box-shadow: 0 0 0 4px rgba(250, 204, 21, 0.5), 0 0 16px rgba(250, 204, 21, 0.7); }
  60%  { transform: scale(0.95); }
  100% { transform: scale(1); }
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

<!-- Global (un-scoped) so they reach the routed view components -->
<style>
.slide-left-enter-active,
.slide-left-leave-active,
.slide-right-enter-active,
.slide-right-leave-active {
  transition: transform 0.28s cubic-bezier(0.4, 0, 0.2, 1), opacity 0.22s ease;
  will-change: transform;
}

/* Forward: dashboard -> logs (new view slides in from the right) */
.slide-left-enter-from { transform: translateX(100%); opacity: 0; }
.slide-left-leave-to   { transform: translateX(-25%); opacity: 0; }

/* Back: logs -> dashboard (new view slides in from the left) */
.slide-right-enter-from { transform: translateX(-25%); opacity: 0; }
.slide-right-leave-to   { transform: translateX(100%); opacity: 0; }
</style>
