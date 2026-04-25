<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useHead } from '@unhead/vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '../stores/auth'
import { analytics } from '../services/analytics'
import SupportPopover from '../components/settings/SupportPopover.vue'
import LocaleSwitcher from '../components/shared/LocaleSwitcher.vue'
import { useLocaleFormat } from '../composables/useLocaleFormat'
import { getTopModels, getPlatformStats, type TopModelPreview } from '../api/publicModelService'
import {
  LockClosedIcon,
  UsersIcon,
  ArrowRightIcon,
  BoltIcon,
  ArrowDownTrayIcon,
  InformationCircleIcon
} from '@heroicons/vue/24/outline'

const { t } = useI18n()
const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const { formatConsumption, formatCostPerKwh, formatCostPerDistance } = useLocaleFormat()
const isEn = computed(() => route.path.startsWith('/en'))
const modelsUrl = computed(() => isEn.value ? '/en/models' : '/modelle')
const loginPath = computed(() => isEn.value ? '/en/login' : '/login')
const registerPath = computed(() => isEn.value ? '/en/register' : '/register')

const topModels = ref<TopModelPreview[]>([])
const nextModels = ref<TopModelPreview[]>([])
const loading = ref(true)
const displayModels = ref(0)
const displayUsers = ref(0)
const displayTrips = ref(0)

// Round trips to nearest 10 for cleaner display with "+"
const displayTripsRounded = computed(() => Math.floor(displayTrips.value / 10) * 10)

function animateCount(target: number, setter: (v: number) => void, duration = 1400) {
  const start = Date.now()
  const tick = () => {
    const progress = Math.min((Date.now() - start) / duration, 1)
    const eased = 1 - Math.pow(1 - progress, 3) // ease-out cubic
    setter(Math.round(eased * target))
    if (progress < 1) requestAnimationFrame(tick)
  }
  requestAnimationFrame(tick)
}

useHead(computed(() => ({
  title: isEn.value
    ? 'EV Monitor – Real Electric Car Consumption & Charging Costs'
    : 'EV Monitor – Echter Stromverbrauch & Ladekosten von Elektroautos',
  meta: [
    {
      name: 'description',
      content: isEn.value
        ? 'Track real EV charging costs and consumption. Compare WLTP vs. real-world data for Tesla, VW ID, Hyundai Ioniq, BMW i4 and more. Community-driven, free and private.'
        : 'Echte Ladekosten und Verbrauch von Elektroautos tracken. WLTP vs. Realität für Tesla, VW ID, Hyundai Ioniq, BMW i4 und viele mehr. Kostenlos, anonym, Open Source.'
    },
    { name: 'robots', content: 'index, follow' },
    { property: 'og:type', content: 'website' },
    { property: 'og:url', content: isEn.value ? 'https://ev-monitor.net/en' : 'https://ev-monitor.net/' },
    {
      property: 'og:title',
      content: isEn.value
        ? 'EV Monitor – Real Electric Car Consumption & Charging Costs'
        : 'EV Monitor – Echter Stromverbrauch & Ladekosten von Elektroautos'
    },
    {
      property: 'og:description',
      content: isEn.value
        ? 'Track real EV charging costs and consumption. Compare WLTP vs. real-world data for Tesla, VW ID, Hyundai Ioniq and more.'
        : 'Echte Ladekosten und Verbrauch tracken. WLTP vs. Realität für Tesla, VW ID, Hyundai Ioniq und mehr.'
    },
    { property: 'og:locale', content: isEn.value ? 'en_GB' : 'de_DE' },
  ],
  link: [
    { rel: 'canonical', href: isEn.value ? 'https://ev-monitor.net/en' : 'https://ev-monitor.net/' },
    { rel: 'alternate', hreflang: 'de', href: 'https://ev-monitor.net/' },
    { rel: 'alternate', hreflang: 'en', href: 'https://ev-monitor.net/en' },
    { rel: 'alternate', hreflang: 'x-default', href: 'https://ev-monitor.net/' },
  ]
})))

onMounted(async () => {
  analytics.track('landing_page_viewed')

  // Load platform stats and animate counters
  try {
    const stats = await getPlatformStats()
    animateCount(stats.modelCount, v => displayModels.value = v)
    animateCount(stats.userCount, v => displayUsers.value = v)
    animateCount(stats.validTripCount, v => displayTrips.value = v)
  } catch {
    // fallback: leave at 0
  }

  // Load top models with community data for SEO — single request instead of 12
  try {
    const models = await getTopModels(8)
    topModels.value = models.slice(0, 4)
    nextModels.value = models.slice(4, 8)
  } catch (error) {
    console.error('Failed to load model previews:', error)
  } finally {
    loading.value = false
  }
})

const goToRegister = (source: string = 'unknown') => {
  analytics.track('cta_register_clicked', { source })
  router.push('/register')
}

const demoLoading = ref(false)
const demoLogin = async (source: 'hero' | 'models_section' = 'hero') => {
  demoLoading.value = true
  analytics.trackDemoLoginClicked(source)
  try {
    const response = await import('../api/axios').then(m => m.default.post('/auth/demo-login'))
    authStore.setToken(response.data.token)
    sessionStorage.setItem('ev_demo_entry_url', window.location.pathname)
    router.push('/dashboard')
  } catch {
    router.push('/login')
  } finally {
    demoLoading.value = false
  }
}


function formatWltpRange(min: number, max: number | null): string {
  if (!max || Math.abs(max - min) < 0.05) return formatConsumption(min)
  return `${formatConsumption(min, { showUnit: false })} - ${formatConsumption(max)}`
}

function formatRealConsumption(avg: number | null, min: number | null, max: number | null): string {
  if (min !== null && max !== null) return formatWltpRange(min, max)
  if (avg !== null) return formatConsumption(avg)
  return '-'
}
</script>

<template>
  <div class="min-h-screen bg-white dark:bg-gray-950 overflow-x-hidden">
    <!-- Navbar -->
    <nav class="border-b border-gray-200 dark:border-gray-700">
      <div class="max-w-7xl mx-auto px-6 sm:px-8 lg:px-12">
        <div class="flex justify-between items-center h-16">
          <div class="flex items-center gap-2">
            <BoltIcon class="h-7 w-7 text-green-600" />
            <span class="text-xl font-bold text-gray-900 dark:text-gray-100 whitespace-nowrap">EV Monitor</span>
          </div>
          <div class="flex items-center gap-2 sm:gap-3">
            <LocaleSwitcher />
            <a
              href="https://github.com/sebastianwien/ev-monitor"
              target="_blank"
              rel="noopener noreferrer"
              class="text-gray-600 dark:text-gray-400 hover:text-gray-900 dark:hover:text-gray-100 p-2 inline-flex items-center"
              aria-label="View source on GitHub"
            >
              <svg class="h-6 w-6" fill="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                <path fill-rule="evenodd" d="M12 2C6.477 2 2 6.484 2 12.017c0 4.425 2.865 8.18 6.839 9.504.5.092.682-.217.682-.483 0-.237-.008-.868-.013-1.703-2.782.605-3.369-1.343-3.369-1.343-.454-1.158-1.11-1.466-1.11-1.466-.908-.62.069-.608.069-.608 1.003.07 1.531 1.032 1.531 1.032.892 1.53 2.341 1.088 2.91.832.092-.647.35-1.088.636-1.338-2.22-.253-4.555-1.113-4.555-4.951 0-1.093.39-1.988 1.029-2.688-.103-.253-.446-1.272.098-2.65 0 0 .84-.27 2.75 1.026A9.564 9.564 0 0112 6.844c.85.004 1.705.115 2.504.337 1.909-1.296 2.747-1.027 2.747-1.027.546 1.379.202 2.398.1 2.651.64.7 1.028 1.595 1.028 2.688 0 3.848-2.339 4.695-4.566 4.943.359.309.678.92.678 1.855 0 1.338-.012 2.419-.012 2.747 0 .268.18.58.688.482A10.019 10.019 0 0022 12.017C22 6.484 17.522 2 12 2z" clip-rule="evenodd" />
              </svg>
            </a>
            <template v-if="authStore.isAuthenticated()">
              <router-link
                to="/dashboard"
                class="text-gray-600 dark:text-gray-400 hover:text-gray-900 dark:hover:text-gray-100 px-2 sm:px-3 py-2 text-sm font-medium"
              >
                {{ t('nav.dashboard') }}
              </router-link>
            </template>
            <template v-else>
              <router-link
                :to="loginPath"
                class="text-gray-600 dark:text-gray-400 hover:text-gray-900 dark:hover:text-gray-100 px-2 sm:px-3 py-2 text-sm font-medium"
              >
                {{ t('nav.login') }}
              </router-link>
              <router-link
                :to="registerPath"
                class="hidden sm:inline-flex bg-green-600 text-white px-3 sm:px-4 py-2 rounded-lg text-sm font-medium hover:bg-green-700 transition whitespace-nowrap"
              >
                {{ t('nav.register') }}
              </router-link>
            </template>
          </div>
        </div>
      </div>
    </nav>

    <!-- Hero Section -->
    <section class="pt-8 pb-6 sm:pt-12 sm:pb-8">
      <div class="max-w-4xl mx-auto text-center px-6 sm:px-8 lg:px-12">
        <h1 class="text-4xl sm:text-5xl lg:text-6xl font-bold text-gray-900 dark:text-gray-100 leading-tight mb-4">
          {{ t('landing.hero.title') }}
        </h1>
        <p class="text-lg text-gray-600 dark:text-gray-400 mb-6 max-w-xl mx-auto break-words">
          {{ t('landing.hero.subtitle') }}
        </p>

        <!-- Inline model preview — sofort Wert zeigen -->
        <div v-if="topModels.length > 0" class="mb-6">
          <!-- Mobile: horizontal scroll snap -->
          <div class="lg:hidden flex gap-3 overflow-x-auto snap-x snap-mandatory px-4 pb-1 -mx-4 scrollbar-hide">
            <a
              v-for="preview in topModels.slice(0, 3)"
              :key="`hero-mobile-${preview.brand}-${preview.model}`"
              :href="`${modelsUrl}/${preview.brandDisplayName}/${preview.modelUrlSlug}`"
              class="snap-start shrink-0 w-[75vw] max-w-[280px] bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl p-4 text-left hover:border-green-500 transition block"
            >
              <div class="flex items-start justify-between gap-2 mb-1">
                <span class="font-semibold text-gray-900 dark:text-gray-100">{{ preview.modelDisplayName }}</span>
                <span class="text-xs text-gray-400 whitespace-nowrap mt-0.5">{{ preview.logCount }} {{ t('landing.hero.charging_sessions') }}</span>
              </div>
              <div class="grid grid-cols-[auto_1fr] items-baseline gap-x-3 gap-y-0.5 mt-1 text-sm">
                <template v-if="preview.minWltpConsumptionKwhPer100km">
                  <span class="text-xs text-gray-400">{{ t('landing.hero.wltp_label') }}</span>
                  <span class="text-gray-500 dark:text-gray-400">{{ formatWltpRange(preview.minWltpConsumptionKwhPer100km, preview.maxWltpConsumptionKwhPer100km) }}</span>
                </template>
                <template v-if="preview.avgConsumptionKwhPer100km || preview.minRealConsumptionKwhPer100km">
                  <span class="text-xs text-gray-400">{{ t('landing.hero.real_label') }}</span>
                  <span class="text-gray-700 dark:text-gray-300 font-semibold">{{ formatRealConsumption(preview.avgConsumptionKwhPer100km, preview.minRealConsumptionKwhPer100km, preview.maxRealConsumptionKwhPer100km) }}</span>
                </template>
                <template v-if="preview.avgCostPerKwh && preview.avgConsumptionKwhPer100km">
                  <span class="text-xs text-gray-400">{{ t('landing.hero.costs_label') }}</span>
                  <span class="flex flex-wrap items-center gap-x-1.5">
                    <span class="text-blue-500 font-medium">~{{ formatCostPerDistance(preview.avgCostPerKwh * preview.avgConsumptionKwhPer100km) }}</span>
                    <span class="relative group cursor-help inline-flex items-center gap-0.5 text-xs text-gray-400">
                      <span>Ø {{ formatCostPerKwh(preview.avgCostPerKwh) }}</span>
                      <InformationCircleIcon class="h-3 w-3 flex-shrink-0" />
                      <span class="absolute bottom-full left-0 mb-1.5 px-2.5 py-2 bg-gray-800 text-white text-xs rounded-lg w-60 hidden group-hover:block z-20 pointer-events-none leading-snug shadow-lg">
                        {{ t('landing.hero.cost_tooltip') }}
                      </span>
                    </span>
                  </span>
                </template>
              </div>
              <div class="mt-2 text-green-600 text-xs font-medium flex items-center gap-1">
                <span>{{ t('landing.hero.view_details') }}</span>
                <ArrowRightIcon class="h-3.5 w-3.5" />
              </div>
            </a>
          </div>
          <!-- Desktop: 3 Modelle nebeneinander -->
          <div class="hidden lg:grid grid-cols-3 gap-4 max-w-5xl mx-auto">
            <a
              v-for="preview in topModels.slice(0, 3)"
              :key="`hero-${preview.brand}-${preview.model}`"
              :href="`${modelsUrl}/${preview.brandDisplayName}/${preview.modelUrlSlug}`"
              class="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl p-4 text-left hover:border-green-500 transition block"
            >
              <div class="flex items-start justify-between gap-2 mb-1">
                <span class="font-semibold text-gray-900 dark:text-gray-100">{{ preview.modelDisplayName }}</span>
                <span class="text-xs text-gray-400 whitespace-nowrap mt-0.5">{{ preview.logCount }} {{ t('landing.hero.charging_sessions') }}</span>
              </div>
              <div class="grid grid-cols-[auto_1fr] items-baseline gap-x-3 gap-y-0.5 mt-1 text-sm">
                <template v-if="preview.minWltpConsumptionKwhPer100km">
                  <span class="text-xs text-gray-400">{{ t('landing.hero.wltp_label') }}</span>
                  <span class="text-gray-500 dark:text-gray-400">{{ formatWltpRange(preview.minWltpConsumptionKwhPer100km, preview.maxWltpConsumptionKwhPer100km) }}</span>
                </template>
                <template v-if="preview.avgConsumptionKwhPer100km || preview.minRealConsumptionKwhPer100km">
                  <span class="text-xs text-gray-400">{{ t('landing.hero.real_label') }}</span>
                  <span class="text-gray-700 dark:text-gray-300 font-semibold">{{ formatRealConsumption(preview.avgConsumptionKwhPer100km, preview.minRealConsumptionKwhPer100km, preview.maxRealConsumptionKwhPer100km) }}</span>
                </template>
                <template v-if="preview.avgCostPerKwh && preview.avgConsumptionKwhPer100km">
                  <span class="text-xs text-gray-400">{{ t('landing.hero.costs_label') }}</span>
                  <span class="flex flex-wrap items-center gap-x-1.5">
                    <span class="text-blue-500 font-medium">~{{ formatCostPerDistance(preview.avgCostPerKwh * preview.avgConsumptionKwhPer100km) }}</span>
                    <span class="relative group cursor-help inline-flex items-center gap-0.5 text-xs text-gray-400">
                      <span>Ø {{ formatCostPerKwh(preview.avgCostPerKwh) }}</span>
                      <InformationCircleIcon class="h-3 w-3 flex-shrink-0" />
                      <span class="absolute bottom-full left-0 mb-1.5 px-2.5 py-2 bg-gray-800 text-white text-xs rounded-lg w-60 hidden group-hover:block z-20 pointer-events-none leading-snug shadow-lg">
                        {{ t('landing.hero.cost_tooltip') }}
                      </span>
                    </span>
                  </span>
                </template>
              </div>
              <div class="mt-2 text-green-600 text-xs font-medium flex items-center gap-1">
                <span>{{ t('landing.hero.view_details') }}</span>
                <ArrowRightIcon class="h-3.5 w-3.5" />
              </div>
            </a>
          </div>
        </div>

        <!-- Primary CTA: kein Account nötig -->
        <div class="flex flex-col sm:flex-row items-center justify-center gap-3 sm:gap-4">
          <button
            @click="demoLogin('hero')"
            :disabled="demoLoading"
            class="demo-shimmer w-full sm:w-auto cursor-pointer bg-green-600 text-white px-6 py-3 sm:px-8 sm:py-4 rounded-lg text-base sm:text-lg font-semibold hover:bg-green-700 disabled:opacity-50 inline-flex items-center justify-center gap-2 transition"
          >
            {{ demoLoading ? t('landing.hero.loading_button') : t('landing.hero.demo_button') }}
          </button>
          <router-link
            :to="modelsUrl"
            @click="analytics.trackCtaModelsClicked('hero')"
            class="w-full sm:w-auto border border-gray-300 dark:border-gray-600 text-gray-700 dark:text-gray-300 px-6 py-3 sm:px-8 sm:py-4 rounded-lg text-base sm:text-lg font-semibold hover:border-green-500 hover:text-green-700 transition inline-flex items-center justify-center space-x-2"
          >
            <span>{{ t('landing.hero.models_button') }}</span>
            <ArrowRightIcon class="h-5 w-5" />
          </router-link>
        </div>

        <p class="mt-5 text-sm text-gray-400">
          {{ t('landing.hero.or') }}
          <button @click="goToRegister('hero_secondary')" class="text-green-600 hover:text-green-700 font-medium underline underline-offset-2">{{ t('landing.hero.register_link') }}</button>
          {{ t('landing.hero.track_data') }}
        </p>

        <p class="mt-4 text-sm font-semibold text-gray-500 dark:text-gray-400 tabular-nums">
          <span>{{ displayTripsRounded }}+ {{ t('landing.hero.trips_label') }}</span>
          <span class="mx-2">•</span>
          <span>{{ displayModels }} {{ t('landing.hero.models_label') }}</span>
          <span class="mx-2">•</span>
          <span>{{ displayUsers }} {{ t('landing.hero.drivers_label') }}</span>
        </p>
      </div>
    </section>

    <!-- Feature Highlights -->
    <section class="pt-6 pb-8 sm:pt-12 sm:pb-16 bg-gray-50 dark:bg-gray-900">
      <div class="max-w-7xl mx-auto px-6 sm:px-8 lg:px-12">
        <div class="grid grid-cols-2 lg:grid-cols-4 gap-3 sm:gap-6">
          <!-- Feature 1: Open Source -->
          <div class="bg-white dark:bg-gray-800 border border-green-200 rounded-xl p-3 sm:p-6 hover:border-green-500 transition">
            <svg class="h-6 w-6 sm:h-10 sm:w-10 text-green-600 mb-2 sm:mb-3" fill="currentColor" viewBox="0 0 24 24" aria-hidden="true">
              <path fill-rule="evenodd" d="M12 2C6.477 2 2 6.484 2 12.017c0 4.425 2.865 8.18 6.839 9.504.5.092.682-.217.682-.483 0-.237-.008-.868-.013-1.703-2.782.605-3.369-1.343-3.369-1.343-.454-1.158-1.11-1.466-1.11-1.466-.908-.62.069-.608.069-.608 1.003.07 1.531 1.032 1.531 1.032.892 1.53 2.341 1.088 2.91.832.092-.647.35-1.088.636-1.338-2.22-.253-4.555-1.113-4.555-4.951 0-1.093.39-1.988 1.029-2.688-.103-.253-.446-1.272.098-2.65 0 0 .84-.27 2.75 1.026A9.564 9.564 0 0112 6.844c.85.004 1.705.115 2.504.337 1.909-1.296 2.747-1.027 2.747-1.027.546 1.379.202 2.398.1 2.651.64.7 1.028 1.595 1.028 2.688 0 3.848-2.339 4.695-4.566 4.943.359.309.678.92.678 1.855 0 1.338-.012 2.419-.012 2.747 0 .268.18.58.688.482A10.019 10.019 0 0022 12.017C22 6.484 17.522 2 12 2z" clip-rule="evenodd" />
            </svg>
            <h3 class="text-sm sm:text-lg font-semibold text-gray-900 dark:text-gray-100 mb-1 sm:mb-2">{{ t('landing.features.open_source_title') }}</h3>
            <p class="text-xs sm:text-sm text-gray-600 dark:text-gray-400 mb-2 sm:mb-3">{{ t('landing.features.open_source_desc') }}</p>
            <a href="https://github.com/sebastianwien/ev-monitor" target="_blank" rel="noopener noreferrer"
              class="inline-flex items-center gap-1 text-green-600 hover:text-green-700 text-xs font-medium">
              View Source →
            </a>
          </div>

          <!-- Feature 2: Auto-Import -->
          <div class="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl p-3 sm:p-6 hover:border-green-500 transition">
            <ArrowDownTrayIcon class="h-6 w-6 sm:h-10 sm:w-10 text-gray-400 mb-2 sm:mb-3" />
            <h3 class="text-sm sm:text-lg font-semibold text-gray-900 dark:text-gray-100 mb-1 sm:mb-2">{{ t('landing.features.auto_import_title') }}</h3>
            <p class="text-xs sm:text-sm text-gray-600 dark:text-gray-400 mb-2">{{ t('landing.features.auto_import_desc') }}</p>
            <div class="flex flex-wrap gap-1">
              <span class="text-xs bg-blue-100 text-blue-800 font-medium px-1.5 py-0.5 rounded-full">go-e BETA</span>
              <span class="text-xs bg-blue-100 text-blue-800 font-medium px-1.5 py-0.5 rounded-full">OCPP BETA</span>
            </div>
          </div>

          <!-- Feature 3: Privacy First -->
          <div class="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl p-3 sm:p-6 hover:border-green-500 transition">
            <LockClosedIcon class="h-6 w-6 sm:h-10 sm:w-10 text-gray-400 mb-2 sm:mb-3" />
            <h3 class="text-sm sm:text-lg font-semibold text-gray-900 dark:text-gray-100 mb-1 sm:mb-2">{{ t('landing.features.privacy_title') }}</h3>
            <p class="text-xs sm:text-sm text-gray-600 dark:text-gray-400">{{ t('landing.features.privacy_desc') }}</p>
          </div>

          <!-- Feature 4: Community -->
          <div class="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl p-3 sm:p-6 hover:border-green-500 transition">
            <UsersIcon class="h-6 w-6 sm:h-10 sm:w-10 text-gray-400 mb-2 sm:mb-3" />
            <h3 class="text-sm sm:text-lg font-semibold text-gray-900 dark:text-gray-100 mb-1 sm:mb-2">{{ t('landing.features.community_title') }}</h3>
            <p class="text-xs sm:text-sm text-gray-600 dark:text-gray-400">{{ t('landing.features.community_desc') }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- Model Preview Section -->
    <section class="py-8 sm:py-16 px-4 sm:px-6 lg:px-8">
      <div class="max-w-7xl mx-auto">
        <div class="text-center mb-12">
          <h2 class="text-3xl font-semibold text-gray-900 dark:text-gray-100 mb-4">
            {{ t('landing.models_section.title') }}
          </h2>
          <p class="text-lg text-gray-600 dark:text-gray-400">
            {{ t('landing.models_section.subtitle') }}
          </p>
        </div>

        <div v-if="loading" class="text-center text-gray-500 dark:text-gray-400">
          {{ t('landing.models_section.loading') }}
        </div>

        <div v-else-if="topModels.length > 0" class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
          <!-- Model Cards -->
          <a
            v-for="preview in topModels"
            :key="`${preview.brand}-${preview.model}`"
            :href="`/modelle/${preview.brandDisplayName}/${preview.modelUrlSlug}`"
            class="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl p-4 hover:border-green-500 transition block"
          >
            <div class="flex items-start justify-between gap-2 mb-2">
              <h3 class="text-lg font-semibold text-gray-900 dark:text-gray-100">{{ preview.modelDisplayName }}</h3>
              <span class="text-xs text-gray-400 whitespace-nowrap mt-1">{{ preview.logCount }} {{ t('landing.hero.charging_sessions') }}</span>
            </div>

            <div class="grid grid-cols-[auto_1fr] items-baseline gap-x-3 gap-y-0.5 mb-3 text-sm">
              <template v-if="preview.minWltpConsumptionKwhPer100km">
                <span class="text-xs text-gray-400">WLTP</span>
                <span class="text-gray-500 dark:text-gray-400">{{ formatWltpRange(preview.minWltpConsumptionKwhPer100km, preview.maxWltpConsumptionKwhPer100km) }}</span>
              </template>
              <template v-if="preview.avgConsumptionKwhPer100km || preview.minRealConsumptionKwhPer100km">
                <span class="text-xs text-gray-400">Real</span>
                <span class="text-gray-700 dark:text-gray-300 font-medium">{{ formatRealConsumption(preview.avgConsumptionKwhPer100km, preview.minRealConsumptionKwhPer100km, preview.maxRealConsumptionKwhPer100km) }}</span>
              </template>
              <template v-if="preview.avgCostPerKwh && preview.avgConsumptionKwhPer100km">
                <span class="text-xs text-gray-400">Kosten</span>
                <span class="flex flex-wrap items-center gap-x-1.5">
                  <span class="text-blue-500 font-medium">~{{ formatCostPerDistance(preview.avgCostPerKwh * preview.avgConsumptionKwhPer100km) }}</span>
                  <span class="relative group cursor-help inline-flex items-center gap-0.5 text-xs text-gray-400">
                    <span>Ø {{ formatCostPerKwh(preview.avgCostPerKwh) }}</span>
                    <InformationCircleIcon class="h-3 w-3 flex-shrink-0" />
                    <span class="absolute bottom-full left-0 mb-1.5 px-2.5 py-2 bg-gray-800 text-white text-xs rounded-lg w-60 hidden group-hover:block z-20 pointer-events-none leading-snug shadow-lg">
                      {{ t('landing.hero.cost_tooltip') }}
                    </span>
                  </span>
                </span>
              </template>
            </div>

            <div class="text-green-600 font-medium flex justify-center items-center gap-1 text-sm">
              <span>{{ t('landing.models_section.view_details') }}</span>
              <ArrowRightIcon class="h-4 w-4" />
            </div>
          </a>

          <!-- Next 4 models teaser + CTAs — span full grid width -->
          <div class="col-span-full mt-2 space-y-4">
            <div v-if="nextModels.length > 0">
              <!-- Mobile: pills -->
              <div class="flex flex-wrap gap-2 justify-center sm:hidden">
                <a
                  v-for="m in nextModels"
                  :key="`${m.brand}-${m.model}`"
                  :href="`${modelsUrl}/${m.brandDisplayName}/${m.modelUrlSlug}`"
                  class="px-3 py-1.5 bg-gray-100 dark:bg-gray-800 hover:bg-gray-200 dark:hover:bg-gray-700 text-gray-600 dark:text-gray-400 text-xs font-medium rounded-full transition"
                >
                  {{ m.modelDisplayName }}
                </a>
              </div>
              <!-- sm+: cards -->
              <div class="hidden sm:grid grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
                <a
                  v-for="m in nextModels"
                  :key="`${m.brand}-${m.model}`"
                  :href="`${modelsUrl}/${m.brandDisplayName}/${m.modelUrlSlug}`"
                  class="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl p-4 hover:border-green-500 transition"
                >
                  <div class="flex items-start justify-between gap-2 mb-2">
                    <h3 class="text-sm font-semibold text-gray-900 dark:text-gray-100">{{ m.modelDisplayName }}</h3>
                    <span class="text-xs text-gray-400 whitespace-nowrap mt-0.5">{{ m.logCount }} {{ t('landing.hero.charging_sessions') }}</span>
                  </div>
                  <div class="grid grid-cols-[auto_1fr] items-baseline gap-x-2 gap-y-0.5 text-xs">
                    <template v-if="m.minWltpConsumptionKwhPer100km">
                      <span class="text-gray-400">WLTP</span>
                      <span class="text-gray-500 dark:text-gray-400">{{ formatWltpRange(m.minWltpConsumptionKwhPer100km, m.maxWltpConsumptionKwhPer100km) }}</span>
                    </template>
                    <template v-if="m.avgConsumptionKwhPer100km || m.minRealConsumptionKwhPer100km">
                      <span class="text-gray-400">Real</span>
                      <span class="text-gray-700 dark:text-gray-300 font-medium">{{ formatRealConsumption(m.avgConsumptionKwhPer100km, m.minRealConsumptionKwhPer100km, m.maxRealConsumptionKwhPer100km) }}</span>
                    </template>
                    <template v-if="m.avgCostPerKwh && m.avgConsumptionKwhPer100km">
                      <span class="text-gray-400">Kosten</span>
                      <span class="flex flex-wrap items-center gap-x-1">
                        <span class="text-blue-500 font-medium">~{{ formatCostPerDistance(m.avgCostPerKwh * m.avgConsumptionKwhPer100km) }}</span>
                        <span class="relative group cursor-help inline-flex items-center gap-0.5 text-gray-400">
                          <span>Ø {{ formatCostPerKwh(m.avgCostPerKwh) }}</span>
                          <InformationCircleIcon class="h-3 w-3 flex-shrink-0" />
                          <span class="absolute bottom-full left-0 mb-1.5 px-2.5 py-2 bg-gray-800 text-white text-xs rounded-lg w-56 hidden group-hover:block z-20 pointer-events-none leading-snug shadow-lg">
                            {{ t('landing.hero.cost_tooltip') }}
                          </span>
                        </span>
                      </span>
                    </template>
                  </div>
                </a>
              </div>
            </div>
            <div class="flex flex-col sm:flex-row items-stretch sm:items-center justify-center gap-3 mt-4 sm:mt-6">
              <router-link
                :to="modelsUrl"
                class="bg-green-600 text-white px-6 py-3 rounded-lg font-semibold hover:bg-green-700 transition inline-flex items-center justify-center space-x-2"
              >
                <span>{{ t('landing.models_section.compare_button') }}</span>
                <ArrowRightIcon class="h-5 w-5" />
              </router-link>
              <button
                @click="demoLogin('models_section')"
                :disabled="demoLoading"
                class="border border-gray-300 dark:border-gray-600 text-gray-700 dark:text-gray-300 px-6 py-3 rounded-lg font-semibold hover:border-green-500 hover:text-green-700 transition disabled:opacity-50 inline-flex items-center justify-center space-x-2"
              >
                <span>{{ demoLoading ? t('landing.models_section.loading_button') : t('landing.models_section.demo_button') }}</span>
              </button>
            </div>
          </div>
        </div>

        <div v-else class="text-center text-gray-500 dark:text-gray-400">
          {{ t('landing.models_section.no_data') }}
        </div>
      </div>
    </section>

    <!-- Gamification Teaser -->
    <section class="py-8 sm:py-16 px-4 sm:px-6 lg:px-8 bg-gray-50 dark:bg-gray-900">
      <div class="max-w-3xl mx-auto text-center relative overflow-hidden">
        <BoltIcon class="absolute inset-0 m-auto h-64 w-64 text-green-600 opacity-[0.15] pointer-events-none" />
        <h2 class="text-2xl font-semibold text-gray-900 dark:text-gray-100 mb-6">
          {{ t('landing.gamification.title') }}
        </h2>
        <ul class="text-left inline-block text-gray-600 dark:text-gray-400 space-y-2 mb-4 text-lg">
          <li>• {{ t('landing.gamification.log_entry') }}</li>
          <li>• {{ t('landing.gamification.add_vehicle') }}</li>
          <li>• {{ t('landing.gamification.invite_friend') }}</li>
          <li>• {{ t('landing.gamification.import_data') }}</li>
        </ul>
        <p class="text-4xl text-gray-400 dark:text-gray-600">. . .</p>
      </div>
    </section>

    <!-- Import Hub Teaser -->
    <section class="py-8 sm:py-16 px-4 sm:px-6 lg:px-8 border-t border-gray-100 dark:border-gray-800">
      <div class="max-w-4xl mx-auto">
        <div class="text-center mb-10">
          <div class="inline-flex items-center gap-2 mb-3">
            <ArrowDownTrayIcon class="h-6 w-6 text-green-600" />
            <h2 class="text-2xl font-semibold text-gray-900 dark:text-gray-100">{{ t('landing.import.title') }}</h2>
          </div>
          <p class="text-gray-600 dark:text-gray-400">
            {{ t('landing.import.subtitle') }}
          </p>
        </div>

        <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <!-- Tesla -->
          <div class="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl p-5 flex items-start gap-4">
            <div class="bg-gray-900 rounded-lg p-2 shrink-0">
              <BoltIcon class="h-5 w-5 text-white" />
            </div>
            <div>
              <div class="flex items-center gap-2 mb-1">
                <span class="font-semibold text-gray-900 dark:text-gray-100 text-sm">Tesla Fleet API</span>
                <span class="text-xs bg-green-100 text-green-700 font-medium px-2 py-0.5 rounded-full">{{ t('landing.import.tesla_status') }}</span>
              </div>
              <p class="text-sm text-gray-500 dark:text-gray-400">{{ t('landing.import.tesla_desc') }}</p>
            </div>
          </div>

          <!-- Sprit-Monitor -->
          <div class="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl p-5 flex items-start gap-4">
            <div class="bg-indigo-600 rounded-lg p-2 shrink-0">
              <ArrowDownTrayIcon class="h-5 w-5 text-white" />
            </div>
            <div>
              <div class="flex items-center gap-2 mb-1">
                <span class="font-semibold text-gray-900 dark:text-gray-100 text-sm">Sprit-Monitor</span>
                <span class="text-xs bg-green-100 text-green-700 font-medium px-2 py-0.5 rounded-full">{{ t('landing.import.spritmonitor_status') }}</span>
              </div>
              <p class="text-sm text-gray-500 dark:text-gray-400">{{ t('landing.import.spritmonitor_desc') }}</p>
            </div>
          </div>

          <!-- go-eCharger -->
          <div class="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl p-5 flex items-start gap-4">
            <div class="bg-green-600 rounded-lg p-2 shrink-0">
              <BoltIcon class="h-5 w-5 text-white" />
            </div>
            <div>
              <div class="flex items-center gap-2 mb-1">
                <span class="font-semibold text-gray-900 dark:text-gray-100 text-sm">go-eCharger Cloud</span>
                <span class="text-xs bg-green-100 text-green-700 font-medium px-2 py-0.5 rounded-full">{{ t('landing.import.gocharger_status') }}</span>
                <span class="text-xs bg-blue-100 text-blue-800 font-medium px-2 py-0.5 rounded-full">{{ t('landing.import.gocharger_beta') }}</span>
              </div>
              <p class="text-sm text-gray-500 dark:text-gray-400">{{ t('landing.import.gocharger_desc') }}</p>
            </div>
          </div>

          <!-- OCPP -->
          <div class="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl p-5 flex items-start gap-4">
            <div class="bg-gray-700 rounded-lg p-2 shrink-0">
              <BoltIcon class="h-5 w-5 text-white" />
            </div>
            <div>
              <div class="flex items-center gap-2 mb-1">
                <span class="font-semibold text-gray-900 dark:text-gray-100 text-sm">OCPP Wallbox</span>
                <span class="text-xs bg-green-100 text-green-700 font-medium px-2 py-0.5 rounded-full">{{ t('landing.import.ocpp_status') }}</span>
                <span class="text-xs bg-blue-100 text-blue-800 font-medium px-2 py-0.5 rounded-full">{{ t('landing.import.ocpp_beta') }}</span>
              </div>
              <p class="text-sm text-gray-500 dark:text-gray-400">{{ t('landing.import.ocpp_desc') }}</p>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- Final CTA -->
    <section class="py-10 sm:py-20 px-4 sm:px-6 lg:px-8 border-t border-gray-100 dark:border-gray-800">
      <div class="max-w-3xl mx-auto text-center">
        <h2 class="text-4xl font-bold text-gray-900 dark:text-gray-100 mb-4">
          {{ t('landing.cta.title') }}
        </h2>
        <p class="text-lg text-gray-600 dark:text-gray-400 mb-8">
          {{ t('landing.cta.subtitle') }}<br />
          {{ t('landing.cta.subtitle2') }}
        </p>
        <button
          @click="goToRegister('footer_cta')"
          class="bg-green-600 text-white px-8 py-4 rounded-lg text-lg font-semibold hover:bg-green-700 transition"
        >
          {{ t('landing.cta.button') }}
        </button>
      </div>
    </section>

    <!-- Footer -->
    <footer class="border-t border-gray-200 dark:border-gray-700 py-12 px-4 sm:px-6 lg:px-8 bg-gray-50 dark:bg-gray-900">
      <div class="max-w-7xl mx-auto">
        <div class="flex flex-col md:flex-row justify-between items-center space-y-4 md:space-y-0">
          <div class="flex items-center space-x-2">
            <BoltIcon class="h-6 w-6 text-green-600" />
            <span class="font-semibold text-gray-900 dark:text-gray-100">EV Monitor</span>
          </div>
          <div class="flex flex-wrap justify-center gap-6 text-sm text-gray-600 dark:text-gray-400">
            <router-link :to="modelsUrl" class="hover:text-gray-900 dark:hover:text-gray-100 font-medium">{{ t('landing.footer.models') }}</router-link>
            <router-link to="/datenschutz" class="hover:text-gray-900 dark:hover:text-gray-100">{{ t('landing.footer.privacy') }}</router-link>
            <router-link to="/impressum" class="hover:text-gray-900 dark:hover:text-gray-100">{{ t('landing.footer.imprint') }}</router-link>
            <router-link to="/agb" class="hover:text-gray-900 dark:hover:text-gray-100">{{ t('landing.footer.terms') }}</router-link>
            <a href="https://github.com/sebastianwien/ev-monitor" target="_blank" rel="noopener noreferrer" class="hover:text-gray-900 dark:hover:text-gray-100">{{ t('landing.footer.github') }}</a>
            <a href="https://tally.so/r/vGB8XA" target="_blank" rel="noopener noreferrer" class="hover:text-gray-900 dark:hover:text-gray-100">{{ t('landing.footer.feedback') }}</a>
          </div>
        </div>
        <div class="mt-8 text-center text-sm text-gray-500 dark:text-gray-400">
          {{ t('landing.footer.made_with') }}
        </div>
        <div class="mt-3 text-center text-xs text-gray-400 dark:text-gray-500">
          <SupportPopover variant="footer" />
        </div>
      </div>
    </footer>
  </div>
</template>


<style scoped>
/* 3D press effect for buttons in sections (not nav) */
section a[class*="rounded"], section button[class*="rounded"] {
  box-shadow: 0 4px 0 0 rgba(0,0,0,0.15);
  transform: translateY(0);
  transition: transform 0.08s ease, box-shadow 0.08s ease;
}

section a[class*="rounded"]:active, section button[class*="rounded"]:active {
  box-shadow: 0 1px 0 0 rgba(0,0,0,0.15);
  transform: translateY(3px);
  transition: transform 0.05s ease, box-shadow 0.05s ease;
}

.demo-shimmer {
  background: linear-gradient(120deg, #16a34a 0%, #15803d 40%, #22c55e 50%, #15803d 60%, #16a34a 100%);
  background-size: 200% 100%;
  animation: shimmer 3s ease-in-out infinite;
  color: white;
}

.demo-shimmer:hover {
  background-position: 100% 0;
}

@keyframes shimmer {
  0%   { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}
</style>
