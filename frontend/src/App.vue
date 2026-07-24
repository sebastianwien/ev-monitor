<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted, computed } from 'vue'
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
import BoltLogo from './components/shared/BoltLogo.vue'
import WorkspaceNav from './components/shared/WorkspaceNav.vue'
import { WORKSPACE_TABS } from './config/tabs'
import { HomeIcon, ArrowDownTrayIcon, UserIcon, BoltIcon, ChatBubbleLeftEllipsisIcon, ArrowsRightLeftIcon, ArrowPathIcon } from '@heroicons/vue/24/outline'
// Note: showImportOverlay kept for backward compat but SpritMonitor moved to /imports
import { captureUtmParams, captureReferrer } from './utils/reddit-pixel'
import { detectCountry } from './composables/useCountryDetection'
import { useHaptic } from './composables/useHaptic'
import { useThemeStore } from './stores/theme'
import ThemeToggle from './components/shared/ThemeToggle.vue'
import LocaleSwitcher from './components/shared/LocaleSwitcher.vue'
import { useTickerState } from './composables/useTickerState'
import { usePullToRefresh } from './composables/usePullToRefresh'
import { useSwipeBack } from './composables/useSwipeBack'
import { useStatusBarTheme } from './composables/useStatusBarTheme'
import { useCountryStore } from './stores/country'
import { subscriptionService } from './api/subscriptionService'
import BottomNav from './components/shared/BottomNav.vue'
import MoreSheet from './components/shared/MoreSheet.vue'
import { Capacitor } from '@capacitor/core'

// Native App (iOS/Android). Platform-Konstante (aendert sich zur Laufzeit nicht).
const isNative = Capacitor.isNativePlatform()

const { haptic } = useHaptic()
const { t } = useI18n()
const countryStore = useCountryStore()
const themeStore = useThemeStore()
const { isDark } = storeToRefs(themeStore)
// Native Statusbar-Icons ans Theme koppeln (sonst weisse Icons auf hellem Grund unsichtbar)
useStatusBarTheme(isDark)
const { tickerHasItems, tickerCollapsed } = useTickerState()

// Statusbar-Filler faerbt sich lila, sobald der Ticker vorhanden ist - auch eingeklappt,
// damit der Header nicht zwischen weiss/lila flippt (nahtloser Header bis in die Notch).
// Ohne Ticker: neutraler blickdichter Streifen mit dezenter Trennkante.
const statusbarFillerClass = computed(() =>
  tickerHasItems.value
    ? 'bg-indigo-800'
    : 'bg-white dark:bg-gray-950 border-b border-gray-200/60 dark:border-gray-800/60'
)

// Pull-to-Refresh (nur nativ): am Seitenanfang nach unten ziehen => Reload.
const { pull: ptrPull, refreshing: ptrRefreshing, armed: ptrArmed } = usePullToRefresh()

const mainPaddingTop = computed(() => {
  if (!authStore.isAuthenticated()) return '0px'
  // --top-nav-h ist 0 auf Mobile (keine Top-Nav) und 64px auf Desktop.
  // + env(safe-area-inset-top): nativ waechst der obere Bereich um die Notch-Hoehe,
  // der Content-Offset muss mitwachsen (auf Web/PWA ohne Notch ist env() = 0).
  const safe = 'env(safe-area-inset-top)'
  const nav = 'var(--top-nav-h)'
  // Die Ticker-Aufklapp-Lasche haengt unter das Band; der Content muss sie freihalten,
  // sonst ragt sie in die erste Card (siehe LeaderboardTicker.vue).
  const lasche = '20px'
  // Demo-Banner liegt unter der Nav (Banner-Hoehe 56px) - siehe DemoBanner.vue.
  if (authStore.isDemoAccount) return `calc(${nav} + 56px + ${safe})`
  if (tickerHasItems.value && !tickerCollapsed.value) return `calc(${nav} + 32px + ${lasche} + ${safe})` // Ticker 32px + Lasche
  if (tickerHasItems.value) return `calc(${nav} + ${lasche} + ${safe})` // eingeklappt: nur Lasche
  return `calc(${nav} + ${safe})`
})

const router = useRouter()
const route = useRoute()

// Edge-Swipe-Back (nur nativ): vom linken Rand nach rechts wischen => zurueck.
useSwipeBack(() => router.back())
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
// Bottom-Nav "Mehr"-Sheet (nur Mobile)
const moreOpen = ref(false)
const balanceBumping = ref(false)
const balanceInitialized = ref(false)

// Mailto-Link mit User-ID im Body fuer den Burger-Menu Feedback-Eintrag
const feedbackMailto = computed(() => {
  const u = authStore.user
  const userInfo = u?.username ? `${u.username} (ID: ${u.sub})` : (u?.sub || 'anonymous')
  const subject = encodeURIComponent('EV-Monitor Feedback')
  const body = encodeURIComponent(`User: ${userInfo}\n\n---\n`)
  return `mailto:support@ev-monitor.net?subject=${subject}&body=${body}`
})

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

    const LS_LAST_VISIT = 'ev_last_visit'
    const now = Date.now()
    const lastVisit = localStorage.getItem(LS_LAST_VISIT)
    if (lastVisit && now - parseInt(lastVisit) > 24 * 60 * 60 * 1000) {
      analytics.trackReturnVisit(Math.floor((now - parseInt(lastVisit)) / 86400000))
    }
    localStorage.setItem(LS_LAST_VISIT, String(now))
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

// Top-Nav-Hoehe live messen und als CSS-Var spiegeln, damit Ticker + DemoBanner
// fugenlos andocken und mainPaddingTop exakt stimmt (statt hartkodierter 64px).
// Nav ist auf Mobile 'hidden md:block' -> offsetHeight 0 -> Var 0, korrekt.
const navRef = ref<HTMLElement | null>(null)
let navResizeObserver: ResizeObserver | null = null
function applyNavHeight() {
  const h = navRef.value?.offsetHeight ?? 0
  document.documentElement.style.setProperty('--top-nav-h', h + 'px')
}
watch(navRef, (el) => {
  navResizeObserver?.disconnect()
  if (el) {
    navResizeObserver = new ResizeObserver(applyNavHeight)
    navResizeObserver.observe(el)
  }
  applyNavHeight()
}, { immediate: true })
onUnmounted(() => navResizeObserver?.disconnect())

const WORKSPACE_PATHS: readonly string[] = WORKSPACE_TABS.map(tab => tab.to)
const isWorkspaceRoute = computed(() => WORKSPACE_PATHS.includes(route.path))

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
}

// Bottom-Nav: Erfassen-FAB nutzt dieselbe Logik wie der Header-Button.
const handleBottomRecord = () => {
  handleNewLog()
}

const handleBottomLogout = () => {
  moreOpen.value = false
  authStore.logout()
}

</script>

<template>
  <div :class="['min-h-screen flex flex-col', authStore.isAuthenticated() ? 'app-wallpaper' : 'bg-gray-100 dark:bg-gray-950']">
    <!-- Pull-to-Refresh-Indikator (nur nativ, erscheint beim Ziehen am Seitenanfang) -->
    <div
      v-if="ptrPull > 0 || ptrRefreshing"
      class="fixed left-1/2 -translate-x-1/2 z-[60] pointer-events-none flex items-center justify-center w-10 h-10 rounded-full bg-white/90 dark:bg-gray-800/90 shadow-md backdrop-blur-sm"
      :style="{ top: `calc(env(safe-area-inset-top) + ${Math.min(ptrPull, 80)}px - 44px)` }"
      role="status"
      aria-live="polite">
      <ArrowPathIcon
        class="w-6 h-6 text-indigo-600 dark:text-indigo-400"
        :class="ptrRefreshing ? 'animate-spin' : 'transition-transform duration-100'"
        :style="ptrRefreshing ? {} : { transform: `rotate(${ptrArmed ? 180 : Math.min(ptrPull * 2.2, 180)}deg)` }" />
    </div>

    <!-- Navigation -->
    <nav ref="navRef" class="hidden md:block bg-indigo-600 text-white fixed top-0 left-0 right-0 z-40 pt-[env(safe-area-inset-top)]" v-if="authStore.isAuthenticated()">
      <div class="px-4 py-3">
        <div class="flex justify-between items-center">
          <!-- Left: Logo + Nav Buttons (Desktop) -->
          <div class="flex items-center space-x-4">
            <router-link to="/dashboard" class="flex items-center gap-1.5 text-2xl font-bold tracking-wide hover:opacity-80 transition whitespace-nowrap">
              <BoltLogo light class="h-7 w-7" />
              <span class="hidden sm:inline">EV Monitor</span>
            </router-link>

            <!-- Compact Icon Nav (640px - 1024px) -->
            <div class="hidden sm:flex lg:hidden items-center space-x-2">
              <button
                @click="handleNewLog(); haptic()"
                class="nav-3d p-2 rounded-sm bg-green-600 hover:bg-green-700 transition"
                title="Ladevorgang erfassen">
                <BoltIcon class="h-5 w-5" />
              </button>
              <router-link
                to="/imports"
                class="nav-3d p-2 rounded-sm border border-indigo-500 hover:bg-indigo-500 transition"
                @click="haptic()"
                :class="{ 'bg-indigo-500': $route.path === '/imports' }"
                title="Import">
                <ArrowDownTrayIcon class="h-5 w-5" />
              </router-link>
              <router-link
                to="/modelle"
                class="nav-3d p-2 rounded-sm border border-indigo-500 hover:bg-indigo-500 transition"
                @click="haptic()"
                :class="{ 'bg-indigo-500': $route.path.startsWith('/modelle') }"
                :title="t('nav.models_compare')">
                <ArrowsRightLeftIcon class="h-5 w-5" />
              </router-link>
              <router-link
                to="/dashboard"
                class="nav-3d p-2 rounded-sm border border-indigo-500 hover:bg-indigo-500 transition"
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
                class="nav-3d p-2 rounded-sm border border-indigo-500 hover:bg-indigo-500 transition"
                @click="haptic()"
                :class="{ 'bg-indigo-500': $route.path === '/dashboard' }"
                title="Dashboard">
                <HomeIcon class="h-5 w-5" />
              </router-link>
              <router-link
                to="/imports"
                class="nav-3d flex items-center gap-2 px-3 py-2 rounded-sm border border-indigo-500 text-sm font-medium hover:bg-indigo-500 transition"
                @click="haptic()"
                :class="{ 'bg-indigo-500': $route.path === '/imports' }">
                <ArrowDownTrayIcon class="h-5 w-5" />
                Import
              </router-link>
              <router-link
                to="/modelle"
                class="nav-3d flex items-center gap-2 px-3 py-2 rounded-sm border border-indigo-500 text-sm font-medium hover:bg-indigo-500 transition"
                @click="haptic()"
                :class="{ 'bg-indigo-500': $route.path.startsWith('/modelle') }">
                <ArrowsRightLeftIcon class="h-5 w-5" />
                {{ t('nav.models_compare') }}
              </router-link>
              <button
                @click="handleNewLog(); haptic()"
                class="nav-3d p-2 rounded-sm bg-green-600 hover:bg-green-700 transition"
                title="Ladevorgang erfassen">
                <BoltIcon class="h-5 w-5" />
              </button>
            </div>
          </div>

          <!-- Right: Coin Balance + User Info + Logout (Desktop) / Hamburger (Mobile) -->

          <!-- Compact Right Nav (768px - 1280px) -->
          <div class="hidden md:flex xl:hidden items-center space-x-2">
            <LocaleSwitcher variant="nav" />
            <ThemeToggle v-if="!authStore.isDemoAccount" class="text-white" />
            <!-- Wallbox dot -->
            <button
              v-if="showWallboxChip"
              @click="goToGoeTab"
              :title="`${wallboxConn?.displayName || 'Wallbox'} · ${wallboxConn?.carStateLabel}`"
              class="p-2 rounded-sm hover:bg-indigo-500 transition flex items-center justify-center"
            >
              <span
                :class="['w-2.5 h-2.5 rounded-full', wallboxChipColor,
                         wallboxConn?.carState === 2 ? 'animate-pulse' : '']"
              />
            </button>
            <router-link
              to="/coins/history"
              class="nav-3d flex items-center gap-1 px-2 h-9 text-sm border border-indigo-500 rounded-sm hover:bg-indigo-500 transition font-medium"
              @click="haptic()"
              :class="{ 'watt-bump': balanceBumping }"
              title="Watt-Guthaben">
              <BoltIcon class="h-4 w-4" />
              <span>{{ coinStore.balance }}</span>
            </router-link>
            <router-link
              v-if="authStore.user && !authStore.isDemoAccount"
              to="/settings"
              class="nav-3d flex items-center justify-center h-9 w-9 border border-indigo-500 rounded-sm hover:bg-indigo-500 transition"
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
            <ThemeToggle v-if="!authStore.isDemoAccount" class="text-white" />
            <!-- Wallbox chip -->
            <button
              v-if="showWallboxChip"
              @click="goToGoeTab"
              class="flex items-center gap-1.5 px-2.5 py-1.5 rounded-sm border text-xs font-medium transition hover:opacity-80"
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
                class="nav-3d flex items-center gap-1.5 px-3 py-1.5 text-sm border border-indigo-500 rounded-sm hover:bg-indigo-500 transition font-medium"
                @click="haptic()"
                :class="{ 'watt-bump': balanceBumping }">
                <BoltIcon class="h-4 w-4" />
                <span>{{ coinStore.balance }}</span>
              </router-link>
              <!-- Tooltip -->
              <div class="absolute right-0 top-full mt-2 w-48 bg-gray-900 text-white text-xs rounded-sm shadow-[5px_5px_0_rgba(0,0,0,0.35)] dark:shadow-[5px_5px_0_rgba(255,255,255,0.35)] p-3 opacity-0 group-hover:opacity-100 pointer-events-none transition-opacity duration-150 z-50">
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
              v-if="authStore.user && !authStore.isDemoAccount"
              to="/settings"
              class="nav-3d flex items-center justify-center h-9 w-9 border border-indigo-500 rounded-sm hover:bg-indigo-500 transition"
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

        </div>
      </div>

    </nav>

    <!-- Mobile: blickdichter Statusbar-Streifen fuellt die Safe-Area (Notch/Statusbar),
         da es auf Mobile keine Top-Nav gibt - sonst scheint das Wallpaper durch. Faerbt
         sich lila, wenn der Ticker sichtbar ist, sodass der farbige Header nahtlos bis in
         die Notch laeuft (statusbarFillerClass). Nur Mobile; im Web ist env() = 0. -->
    <div
      v-if="authStore.isAuthenticated()"
      class="md:hidden fixed top-0 left-0 right-0 z-40 h-[env(safe-area-inset-top)] transition-colors"
      :class="statusbarFillerClass"
      aria-hidden="true"
    />

    <!-- Leaderboard Ticker (below nav, only when authenticated) -->
    <LeaderboardTicker v-if="authStore.isAuthenticated() && !authStore.isDemoAccount && ['DE', 'AT', 'CH'].includes(countryStore.country)" />

    <!-- Mobile: Bottom-Navigation + "Mehr"-Sheet (ersetzt das alte Hamburger-Menue) -->
    <BottomNav
      v-if="authStore.isAuthenticated()"
      :more-open="moreOpen"
      :watt-balance="coinStore.balance"
      :is-demo="authStore.isDemoAccount"
      @record="handleBottomRecord"
      @toggle-more="moreOpen = !moreOpen"
    />
    <MoreSheet
      :open="moreOpen"
      :feedback-mailto="feedbackMailto"
      :is-demo="authStore.isDemoAccount"
      :is-native="isNative"
      @close="moreOpen = false"
      @logout="handleBottomLogout"
    />
    <!-- Impersonation Banner -->
    <div
      v-if="impersonatingAs"
      class="sticky top-0 z-50 flex items-center justify-between px-4 py-2 bg-amber-400 text-amber-900 text-sm font-medium">
      <span>Impersonation aktiv: <strong>{{ impersonatingAs }}</strong> · Token läuft in 1h ab</span>
      <button
        @click="stopImpersonation"
        class="px-3 py-1 bg-amber-900 text-amber-100 rounded-sm hover:bg-amber-800 transition text-xs font-semibold">
        Beenden
      </button>
    </div>

    <!-- Demo Banner (shown for seed/demo accounts) -->
    <DemoBanner v-if="authStore.isDemoAccount" />
    <main
      :class="[
        authStore.isAuthenticated() ? 'md:px-4' : '',
        // Mobile: Platz fuer die Bottom-Nav (h-14 + Safe-Area); Desktop unveraendert.
        authStore.isAuthenticated() ? 'pb-[calc(3.5rem+env(safe-area-inset-bottom))] md:pb-10' : ''
      ]"
      style="overflow-x: clip;"
      :style="{ paddingTop: mainPaddingTop, transition: 'padding-top 0.3s ease' }">
      <!-- Die Workspace-Leiste liegt ueber der router-view, nicht in den Layouts: so
           bleibt sie beim Tab-Wechsel stehen, waehrend nur der Inhalt durchwischt. -->
      <div v-if="isWorkspaceRoute" class="hidden md:block md:max-w-6xl md:mx-auto md:px-6 md:pt-6">
        <WorkspaceNav class="mb-4" />
      </div>
      <div class="relative">
        <router-view v-slot="{ Component, route: r }">
          <Transition :name="(r.meta.transition as string) || ''" mode="out-in">
            <KeepAlive :include="['CarContextLayout']">
              <component :is="Component" />
            </KeepAlive>
          </Transition>
        </router-view>
      </div>
    </main>

    <!-- Footer nur fuer eingeloggte Nutzer auf Desktop. Mobile (und nativ) liegen die
         rechtlichen Links im Mehr-Sheet - dort sind sie mit einem Tap erreichbar, waehrend
         der Footer hier hinter der fixierten Bottom-Nav verschwaende. -->
    <footer v-if="authStore.isAuthenticated() && !isNative" class="hidden md:block bg-gray-50 dark:bg-gray-900 border-t border-gray-200 dark:border-gray-700 py-6 mt-auto">
      <div class="container mx-auto px-4">
        <div class="text-center text-sm text-gray-600 dark:text-gray-400 space-y-3">
          <p>
            © 2026 EV Monitor ·
            <router-link to="/datenschutz" class="hover:text-green-600 underline">{{ t('footer.privacy') }}</router-link> ·
            <router-link to="/impressum" class="hover:text-green-600 underline">{{ t('footer.imprint') }}</router-link> ·
            <router-link to="/agb" class="hover:text-green-600 underline">{{ t('footer.terms') }}</router-link> ·
            <router-link to="/consumption-methodology" class="hover:text-green-600 underline">{{ t('footer.consumption_methodology') }}</router-link> ·
            <router-link to="/blog" class="hover:text-green-600 underline">{{ t('footer.blog') }}</router-link> ·
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
    <!-- Floating-Action-Button nur Desktop (>=768px); auf Mobile uebernimmt der Bottom-Nav-FAB. -->
    <div class="hidden md:block">
      <FloatingActionButton v-if="authStore.isAuthenticated() && !authStore.isDemoAccount && !isOnboardingVisible && !showLogFormModal && $route.path !== '/erfassen'" @click="handleNewLog" />
    </div>

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
  0%   { transform: scale(1);    box-shadow: none; background-color: transparent; border-color: rgba(129, 140, 248, 0.5); }
  25%  { transform: scale(1.45); box-shadow: 0 0 0 4px rgba(250, 204, 21, 0.4), 0 0 16px rgba(250, 204, 21, 0.6); background-color: rgba(250, 204, 21, 0.25); border-color: rgba(250, 204, 21, 0.9); color: #fef08a; }
  60%  { transform: scale(0.95); box-shadow: 0 0 0 2px rgba(250, 204, 21, 0.2); }
  100% { transform: scale(1);    box-shadow: none; background-color: transparent; border-color: rgba(129, 140, 248, 0.5); color: inherit; }
}

/* 3D press effect for navbar buttons */
.nav-3d {
  box-shadow: 3px 3px 0 0 rgba(0,0,0,0.25);
  transform: translate(0, 0);
  transition: transform 0.08s ease, box-shadow 0.08s ease;
  touch-action: manipulation;
}
.nav-3d:active,
.nav-3d.router-link-active,
.nav-3d.router-link-exact-active {
  box-shadow: 1px 1px 0 0 rgba(0,0,0,0.25);
  transform: translate(2px, 2px);
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

<!--
  Uebergang zwischen zwei Workspace-Tabs: der alte Inhalt blendet aus, dann blendet der
  neue ein und kommt dabei 14px aus der Richtung seines Tabs - genug, um die Richtung zu
  zeigen, zu wenig, um wie eine Wischgeste zu wirken, die es beim Klick ja nicht gab.

  Streng nacheinander (mode="out-in"): lagen beide Inhalte gleichzeitig da, sah man zwei
  Bewegungen auf einmal, und die Seitenhoehe sprang mitten im Uebergang. Nicht scoped: die
  Klassen landen auf dem Root der jeweiligen Layout-Komponente. Der Wechsel innerhalb
  eines Paars laeuft ueber den SwipeTabPager und nutzt dieselben Klassen.
-->
<style>
.nudge-left-enter-active,
.nudge-right-enter-active {
  transition: transform 0.22s cubic-bezier(0.22, 0.68, 0.24, 1), opacity 0.22s ease-out;
}

.nudge-left-leave-active,
.nudge-right-leave-active {
  transition: opacity 0.12s ease-in;
}

.nudge-left-enter-from { transform: translateX(14px); opacity: 0; }
.nudge-right-enter-from { transform: translateX(-14px); opacity: 0; }
.nudge-left-leave-to,
.nudge-right-leave-to { opacity: 0; }

@media (prefers-reduced-motion: reduce) {
  .nudge-left-enter-active,
  .nudge-right-enter-active,
  .nudge-left-leave-active,
  .nudge-right-leave-active {
    transition: none;
  }
}
</style>
