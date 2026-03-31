<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '../stores/auth'
import { analytics } from '../services/analytics'
import LocaleSwitcher from '../components/LocaleSwitcher.vue'
import { getTopModels, getPlatformStats, type TopModelPreview } from '../api/publicModelService'
import {
  BoltIcon,
  ArrowRightIcon,
  MagnifyingGlassIcon,
  ArrowLeftIcon,
  XMarkIcon,
} from '@heroicons/vue/24/outline'

const { t } = useI18n()
const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const isEn = computed(() => route.path.startsWith('/en'))
const modelsUrl = computed(() => isEn.value ? '/en/models' : '/modelle')
const loginPath = computed(() => isEn.value ? '/en/login' : '/login')
const registerPath = computed(() => isEn.value ? '/en/register' : '/register')

// Slide-in animation when returning from model detail page
const slidingBack = ref(false)
const navigatingAway = ref(false)

// Weiche state - driven by URL
const selectedPath = computed(() => {
  const s = route.params.section
  if (s === 'owner' || s === 'browser') return s
  return null
})
const transitionName = ref('slide-left')

watch(selectedPath, (val) => {
  if (val === null) searchQuery.value = ''
})

// Model data
const allModels = ref<TopModelPreview[]>([])
const searchQuery = ref('')
const loading = ref(true)
const demoLoading = ref(false)

// Platform stats
const displayTrips = ref(0)
const displayModels = ref(0)
const displayUsers = ref(0)
const displayTripsRounded = computed(() => Math.floor(displayTrips.value / 10) * 10)

const filteredModels = computed(() => {
  const q = searchQuery.value.toLowerCase().trim()
  if (!q) return allModels.value.slice(0, 6)
  return allModels.value.filter(m =>
    m.modelDisplayName.toLowerCase().includes(q) ||
    m.brandDisplayName.toLowerCase().includes(q)
  ).slice(0, 6)
})

function animateCount(target: number, setter: (v: number) => void, duration = 1400) {
  const start = Date.now()
  const tick = () => {
    const progress = Math.min((Date.now() - start) / duration, 1)
    const eased = 1 - Math.pow(1 - progress, 3)
    setter(Math.round(eased * target))
    if (progress < 1) requestAnimationFrame(tick)
  }
  requestAnimationFrame(tick)
}

function formatWltpRange(min: number, max: number | null): string {
  if (!max || Math.abs(max - min) < 0.05) return min.toFixed(1)
  return `${min.toFixed(1)} - ${max.toFixed(1)}`
}

function formatRealConsumption(avg: number | null, min: number | null, max: number | null): string {
  if (min !== null && max !== null) return formatWltpRange(min, max)
  if (avg !== null) return avg.toFixed(1)
  return '-'
}

function selectPath(path: 'owner' | 'browser') {
  analytics.track('lp_v2_path_selected', { path })
  transitionName.value = 'slide-left'
  router.push(`/start/${path}`)
}

function goBack() {
  transitionName.value = 'slide-right'
  router.push('/start')
}

const goToRegister = () => {
  analytics.track('cta_register_clicked', { source: 'lp_v2_owner' })
  navigatingAway.value = true
  router.push(registerPath.value)
}

function navigateToModel(brandDisplayName: string, modelUrlSlug: string) {
  analytics.track('lp_v2_model_clicked', { brand: brandDisplayName, model: modelUrlSlug, path: selectedPath.value ?? 'unknown' })
  sessionStorage.setItem('ev_from', 'lp_v2')
  router.push(`${modelsUrl.value}/${brandDisplayName}/${modelUrlSlug}`)
}

const demoLogin = async (source: 'lp_v2_weiche' | 'lp_v2_owner') => {
  demoLoading.value = true
  analytics.trackDemoLoginClicked(source)
  try {
    const response = await import('../api/axios').then(m => m.default.post('/auth/demo-login'))
    authStore.setToken(response.data.token)
    analytics.trackDemoSessionStarted(source)
    analytics.setDemoContext(source)
    sessionStorage.setItem('ev_demo_entry_url', window.location.pathname)
    router.push('/dashboard?utm_source=demo&utm_medium=lp_v2&utm_campaign=' + source)
  } catch {
    router.push(loginPath.value)
  } finally {
    demoLoading.value = false
  }
}

onMounted(async () => {
  analytics.track('landing_v2_viewed')

  if (sessionStorage.getItem('ev_back_slide')) {
    sessionStorage.removeItem('ev_back_slide')
    slidingBack.value = true
    setTimeout(() => slidingBack.value = false, 400)
  }

  try {
    const stats = await getPlatformStats()
    animateCount(stats.modelCount, v => displayModels.value = v)
    animateCount(stats.userCount, v => displayUsers.value = v)
    animateCount(stats.validTripCount, v => displayTrips.value = v)
  } catch { /* ignore */ }

  try {
    allModels.value = await getTopModels(40)
  } catch { /* ignore */ } finally {
    loading.value = false
  }
})
</script>

<template>
  <div :class="['min-h-screen bg-white dark:bg-gray-950 overflow-x-hidden transition-opacity duration-150', { 'slide-back': slidingBack, 'opacity-0': navigatingAway }]">

    <!-- Green accent strip -->
    <div class="h-1 bg-gradient-to-r from-green-400 via-emerald-500 to-green-600"></div>

    <!-- Navbar -->
    <nav class="sticky top-0 z-50 bg-white/85 dark:bg-gray-950/85 backdrop-blur-md border-b border-gray-200/60 dark:border-gray-700/60 shadow-sm">
      <div class="max-w-7xl mx-auto px-4 sm:px-8">
        <div class="flex justify-between items-center h-16">
          <div class="flex items-center gap-2">
            <BoltIcon class="h-7 w-7 text-green-600" />
            <span class="text-xl font-bold text-gray-900 dark:text-gray-100">EV Monitor</span>
          </div>
          <div class="flex items-center gap-2 sm:gap-3">
            <LocaleSwitcher />
            <template v-if="authStore.isAuthenticated()">
              <router-link to="/dashboard" class="text-gray-600 dark:text-gray-400 hover:text-gray-900 dark:hover:text-gray-100 px-2 sm:px-3 py-2 text-sm font-medium">
                {{ t('nav.dashboard') }}
              </router-link>
            </template>
            <template v-else>
              <router-link :to="loginPath" class="text-gray-600 dark:text-gray-400 hover:text-gray-900 dark:hover:text-gray-100 px-2 sm:px-3 py-2 text-sm font-medium">
                {{ t('nav.login') }}
              </router-link>
              <router-link :to="registerPath" class="hidden sm:inline-flex bg-green-600 text-white px-3 sm:px-4 py-2 rounded-lg text-sm font-medium hover:bg-green-700 transition">
                {{ t('nav.register') }}
              </router-link>
            </template>
          </div>
        </div>
      </div>
    </nav>

    <!-- MAIN CONTENT -->
    <main class="max-w-4xl mx-auto px-4 sm:px-8 pt-6 pb-8 sm:py-16 overflow-hidden">
      <Transition :name="transitionName" mode="out-in">

      <!-- ===== WEICHE (null state) ===== -->
      <div v-if="selectedPath === null" key="weiche">
        <div class="text-center mb-5 sm:mb-8">
          <h1 class="text-3xl sm:text-4xl font-bold text-gray-900 dark:text-gray-100 mb-2">
            {{ t('landing_v2.weiche.question') }}
          </h1>
          <p class="text-gray-500 dark:text-gray-400 text-sm sm:text-base whitespace-pre-line">
            {{ t('landing_v2.weiche.question_sub') }}
          </p>
        </div>

        <!-- Two path cards -->
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-3 sm:gap-6">

          <!-- Card A: EV-Besitzer -->
          <button
            @click="selectPath('owner')"
            class="btn-3d btn-3d-delay [--btn-shadow-color:#16a34a] group text-center bg-white dark:bg-gray-900 border-2 border-green-400 dark:border-green-600 rounded-2xl p-4 cursor-pointer"
          >
            <div class="flex items-center justify-center gap-2 mb-2">
              <BoltIcon class="h-5 w-5 text-green-600 shrink-0" />
              <h2 class="text-lg font-bold text-gray-900 dark:text-gray-100">
                {{ t('landing_v2.weiche.owner_title') }}
              </h2>
            </div>
            <p class="text-gray-500 dark:text-gray-400 text-sm leading-relaxed mb-3">
              {{ t('landing_v2.weiche.owner_desc') }}
            </p>
            <div class="flex items-center justify-center gap-1.5 text-green-600 font-semibold text-sm">
              <span>Los geht's</span>
              <ArrowRightIcon class="h-4 w-4 group-hover:translate-x-1 transition-transform" />
            </div>
          </button>

          <!-- Card B: Interessent -->
          <button
            @click="selectPath('browser')"
            class="btn-3d btn-3d-delay [--btn-shadow-color:#3b82f6] group text-center bg-white dark:bg-gray-900 border-2 border-blue-400 dark:border-blue-600 rounded-2xl p-4 cursor-pointer"
          >
            <div class="flex items-center justify-center gap-2 mb-2">
              <MagnifyingGlassIcon class="h-5 w-5 text-blue-500 shrink-0" />
              <h2 class="text-lg font-bold text-gray-900 dark:text-gray-100">
                {{ t('landing_v2.weiche.browser_title') }}
              </h2>
            </div>
            <p class="text-gray-500 dark:text-gray-400 text-sm leading-relaxed mb-3">
              {{ t('landing_v2.weiche.browser_desc') }}
            </p>
            <div class="flex items-center justify-center gap-1.5 text-blue-500 font-semibold text-sm">
              <span>Modelle ansehen</span>
              <ArrowRightIcon class="h-4 w-4 group-hover:translate-x-1 transition-transform" />
            </div>
          </button>
        </div>

        <!-- Demo: eigene Card als dritte Option -->
        <button
          @click="demoLogin('lp_v2_weiche')"
          :disabled="demoLoading"
          class="group w-full mt-3 text-left bg-green-50 dark:bg-green-900/20 border-2 border-green-200 dark:border-green-800 hover:border-green-400 dark:hover:border-green-600 rounded-2xl p-3.5 sm:p-6 transition-all duration-200 cursor-pointer disabled:opacity-50 flex items-center justify-between gap-3"
        >
          <div class="flex items-center gap-3">
            <div class="w-9 h-9 bg-green-100 dark:bg-green-900/50 rounded-xl flex items-center justify-center shrink-0 group-hover:bg-green-200 dark:group-hover:bg-green-900 transition-colors">
              <BoltIcon class="h-4 w-4 text-green-600" />
            </div>
            <p class="font-semibold text-gray-900 dark:text-gray-100 text-sm">
              {{ demoLoading ? t('landing.hero.loading_button') : t('landing_v2.weiche.demo_link') }}
            </p>
          </div>
          <ArrowRightIcon class="h-5 w-5 text-green-600 shrink-0 group-hover:translate-x-1 transition-transform" /></button>

        <!-- Social proof bar -->
        <div class="mt-5 pt-5 border-t border-gray-100 dark:border-gray-800 flex flex-wrap justify-center gap-6 sm:gap-10 text-center">
          <div>
            <p class="text-2xl sm:text-3xl font-bold text-gray-900 dark:text-gray-100 tabular-nums">{{ displayTripsRounded }}+</p>
            <p class="text-xs text-gray-400 mt-0.5">{{ t('landing_v2.stats.trips') }}</p>
          </div>
          <div>
            <p class="text-2xl sm:text-3xl font-bold text-gray-900 dark:text-gray-100 tabular-nums">{{ displayModels }}</p>
            <p class="text-xs text-gray-400 mt-0.5">{{ t('landing_v2.stats.models') }}</p>
          </div>
          <div>
            <p class="text-2xl sm:text-3xl font-bold text-gray-900 dark:text-gray-100 tabular-nums">{{ displayUsers }}</p>
            <p class="text-xs text-gray-400 mt-0.5">{{ t('landing_v2.stats.drivers') }}</p>
          </div>
        </div>
      </div>

      <!-- ===== WEG A + B: gemeinsames Template ===== -->
      <div v-else-if="selectedPath" :key="selectedPath">
        <h2 class="text-2xl sm:text-3xl font-bold text-gray-900 dark:text-gray-100 mb-6 text-center">
          {{ t(`landing_v2.${selectedPath}.title`) }}
        </h2>

        <!-- Search field -->
        <div class="relative mb-6">
          <MagnifyingGlassIcon class="absolute left-4 top-1/2 -translate-y-1/2 h-5 w-5 text-gray-400 pointer-events-none" />
          <button
            v-if="searchQuery"
            @click="searchQuery = ''"
            class="absolute right-3 top-1/2 -translate-y-1/2 p-1 text-gray-400 hover:text-gray-600 transition"
          >
            <XMarkIcon class="h-5 w-5" />
          </button>
          <input
            v-model="searchQuery"
            type="text"
            :placeholder="t(`landing_v2.${selectedPath}.search_placeholder`)"
            class="w-full pl-12 pr-4 py-4 rounded-xl border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800 text-gray-900 dark:text-gray-100 text-base placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-green-500 focus:border-transparent transition"
          />
        </div>

        <!-- Model cards -->
        <div v-if="loading" class="text-center text-gray-400 py-8">{{ t('common.loading') }}</div>

        <div v-else>
          <p v-if="searchQuery && filteredModels.length === 0" class="text-gray-500 text-sm mb-4">
            {{ t(`landing_v2.${selectedPath}.no_results`) }}
          </p>
          <div v-else-if="!searchQuery" class="flex items-center justify-between mb-4">
            <p class="text-xs font-semibold uppercase tracking-widest text-gray-400">{{ t(`landing_v2.${selectedPath}.popular`) }}</p>
            <a :href="modelsUrl" @click="analytics.track('lp_v2_all_models_clicked', { path: selectedPath })" class="text-xs font-semibold text-green-600 hover:text-green-700 transition">{{ t(`landing_v2.${selectedPath}.all_models`) }}</a>
          </div>

          <!-- Grid: 2 Spalten mobile, 3 Desktop -->
          <div class="grid grid-cols-2 sm:grid-cols-3 gap-3">
            <a
              v-for="model in filteredModels"
              :key="`${selectedPath}-${model.brand}-${model.model}`"
              :href="`${modelsUrl}/${model.brandDisplayName}/${model.modelUrlSlug}`"
              @click.prevent="navigateToModel(model.brandDisplayName, model.modelUrlSlug)"
              class="btn-3d btn-3d-delay [--btn-shadow-color:#d1d5db] bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-700 rounded-xl p-3 block"
            >
              <p class="font-semibold text-gray-900 dark:text-gray-100 text-sm truncate mb-2">{{ model.modelDisplayName }}</p>
              <div class="grid grid-cols-[auto_1fr] items-baseline gap-x-2 gap-y-0.5 text-xs">
                <template v-if="model.minWltpConsumptionKwhPer100km">
                  <span class="text-gray-400">WLTP</span>
                  <span class="text-gray-500 truncate">{{ formatWltpRange(model.minWltpConsumptionKwhPer100km, model.maxWltpConsumptionKwhPer100km) }} kWh</span>
                </template>
                <template v-if="model.avgConsumptionKwhPer100km || model.minRealConsumptionKwhPer100km">
                  <span class="text-gray-400">Real</span>
                  <span class="text-gray-800 dark:text-gray-200 font-semibold truncate">{{ formatRealConsumption(model.avgConsumptionKwhPer100km, model.minRealConsumptionKwhPer100km, model.maxRealConsumptionKwhPer100km) }} kWh</span>
                </template>
                <template v-if="model.avgCostPerKwh && model.avgConsumptionKwhPer100km">
                  <span class="text-gray-400">€</span>
                  <span class="text-blue-500 font-medium">~{{ (model.avgCostPerKwh * model.avgConsumptionKwhPer100km).toFixed(1) }}/100km</span>
                </template>
              </div>
            </a>
          </div>

          <!-- Owner-only CTAs -->
          <div v-if="selectedPath === 'owner'" class="mt-5 flex flex-col items-center gap-3">
            <button @click="goToRegister" class="btn-3d btn-3d-delay w-full bg-green-600 text-white px-6 py-3.5 rounded-xl text-base font-semibold hover:bg-green-700 active:bg-green-800 transition inline-flex items-center justify-center gap-2">
              {{ t('landing_v2.owner.cta_register') }}
            </button>
            <button @click="demoLogin('lp_v2_owner')" :disabled="demoLoading" class="btn-3d btn-3d-delay demo-shimmer w-full px-6 py-3.5 rounded-xl text-base font-medium disabled:opacity-50 inline-flex items-center justify-center gap-2 text-green-600 dark:text-green-400">
              {{ demoLoading ? t('landing.hero.loading_button') : t('landing_v2.owner.demo_link') }}
            </button>
            <button @click="goBack" class="btn-3d btn-3d-delay [--btn-shadow-color:#d1d5db] inline-flex items-center gap-1.5 text-sm font-medium text-gray-600 dark:text-gray-400 bg-gray-100 dark:bg-gray-800 hover:bg-gray-200 dark:hover:bg-gray-700 rounded-lg px-3 py-2 transition self-start mt-1">
              <ArrowLeftIcon class="h-4 w-4" />
              {{ t('landing_v2.owner.back') }}
            </button>
          </div>

          <!-- Browser back button -->
          <div v-else class="mt-5">
            <button @click="goBack" class="btn-3d btn-3d-delay [--btn-shadow-color:#d1d5db] inline-flex items-center gap-1.5 text-sm font-medium text-gray-600 dark:text-gray-400 bg-gray-100 dark:bg-gray-800 hover:bg-gray-200 dark:hover:bg-gray-700 rounded-lg px-3 py-2 transition">
              <ArrowLeftIcon class="h-4 w-4" />
              {{ t('landing_v2.browser.back') }}
            </button>
          </div>
        </div>
      </div>

            </Transition>
    </main>

    <!-- Footer -->
    <footer class="border-t border-gray-100 dark:border-gray-800 py-8 px-4">
      <div class="max-w-4xl mx-auto flex flex-col sm:flex-row items-center justify-between gap-4 text-sm text-gray-400">
        <span>{{ t('landing.footer.made_with') }}</span>
        <nav class="flex flex-wrap gap-4 justify-center">
          <a :href="modelsUrl" class="hover:text-gray-600 dark:hover:text-gray-300 transition">{{ t('landing.footer.models') }}</a>
          <router-link to="/datenschutz" class="hover:text-gray-600 dark:hover:text-gray-300 transition">{{ t('landing.footer.privacy') }}</router-link>
          <router-link to="/impressum" class="hover:text-gray-600 dark:hover:text-gray-300 transition">{{ t('landing.footer.imprint') }}</router-link>
          <a href="https://github.com/sebastianwien/ev-monitor" target="_blank" rel="noopener noreferrer" class="hover:text-gray-600 dark:hover:text-gray-300 transition">{{ t('landing.footer.github') }}</a>
        </nav>
      </div>
    </footer>

  </div>
</template>

<style scoped>
.slide-back {
  animation: slide-from-right 0.3s cubic-bezier(0.25, 0.46, 0.45, 0.94) both;
}
@keyframes slide-from-right {
  from { opacity: 0; transform: translateX(-60px); }
  to   { opacity: 1; transform: translateX(0); }
}

/* Swipe transitions */
.slide-left-enter-active,
.slide-left-leave-active,
.slide-right-enter-active,
.slide-right-leave-active {
  transition: transform 0.2s cubic-bezier(0.25, 0.46, 0.45, 0.94), opacity 0.2s ease;
}
.slide-left-enter-from  { transform: translateX(60px); opacity: 0; }
.slide-left-leave-to    { transform: translateX(-60px); opacity: 0; }
.slide-right-enter-from { transform: translateX(-60px); opacity: 0; }
.slide-right-leave-to   { transform: translateX(60px); opacity: 0; }

.demo-shimmer {
  background: linear-gradient(120deg, white 0%, white 25%, #f0fdf4 45%, #dcfce7 50%, #f0fdf4 55%, white 75%, white 100%);
  background-size: 200% 100%;
  animation: shimmer 6s ease-in-out infinite;
  border: 1px solid #d1d5db;
  color: #6b7280;
  font-weight: 500;
}
.demo-shimmer:hover { background-position: 100% 0; }
@keyframes shimmer {
  0%   { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

</style>
