<template>
  <div :class="isAuthenticated ? '' : 'min-h-screen bg-gray-50 dark:bg-gray-950'">
    <PublicNav />

    <main :class="isAuthenticated ? 'md:max-w-6xl md:mx-auto md:p-6' : 'max-w-6xl mx-auto px-4 py-8'">
      <div :class="isAuthenticated ? 'bg-white dark:bg-gray-800 md:rounded-xl md:shadow-lg p-4 md:p-6' : ''">
      <!-- Hero -->
      <div class="mb-6">

        <!-- Standard Hero -->
        <template v-if="!isRedditSource">
          <h1 class="text-2xl sm:text-3xl font-bold text-gray-900 dark:text-gray-100 mb-1 leading-tight text-center">
            {{ t('models_list.hero.title') }}
          </h1>
          <p class="text-gray-500 dark:text-gray-400 text-sm mb-4 text-center">
            {{ t('models_list.hero.subtitle') }}
          </p>
          <!-- Stats Badges -->
          <div class="flex flex-wrap gap-2 justify-center">
            <span class="inline-flex items-center gap-1.5 bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-300 text-xs font-medium px-3 py-1.5 rounded-full">
              <ChartBarIcon class="h-3.5 w-3.5 text-green-500" />
              {{ modelsWithData.length }} {{ t('models_list.hero.models_count') }}
            </span>
            <span v-if="platformStats" class="inline-flex items-center gap-1.5 bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-300 text-xs font-medium px-3 py-1.5 rounded-full">
              <ArrowTrendingUpIcon class="h-3.5 w-3.5 text-green-500" />
              {{ platformStats.validTripCount.toLocaleString() }} {{ t('models_list.hero.trips_count') }}
            </span>
            <span v-if="platformStats" class="inline-flex items-center gap-1.5 bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-300 text-xs font-medium px-3 py-1.5 rounded-full">
              <CheckIcon class="h-3.5 w-3.5 text-green-500" />
              {{ platformStats.userCount.toLocaleString() }} {{ t('models_list.hero.drivers_count') }}
            </span>
          </div>
        </template>

        <!-- Reddit Hero -->
        <template v-else>
          <div class="bg-gradient-to-br from-gray-900 to-green-900 px-6 py-10 sm:px-10 sm:py-12">
            <div class="inline-flex items-center gap-1.5 bg-green-500/20 text-green-300 text-xs font-semibold px-3 py-1 rounded-full mb-4 uppercase tracking-wide">
              {{ t('models_list.reddit.badge') }}
            </div>
            <h1 class="text-3xl sm:text-4xl font-bold text-white mb-3 leading-tight">
              {{ t('models_list.reddit.title') }}
            </h1>
            <p class="text-gray-300 text-lg mb-6 max-w-xl leading-relaxed">
              {{ t('models_list.reddit.subtitle') }}
            </p>
            <div class="flex flex-wrap gap-3 mb-6">
              <span class="inline-flex items-center gap-1.5 bg-white/10 text-white text-sm font-medium px-3.5 py-1.5 rounded-full">
                <ChartBarIcon class="h-4 w-4 text-green-400" />
                {{ modelsWithData.length }} {{ t('models_list.hero.models_count') }}
              </span>
              <span v-if="platformStats" class="inline-flex items-center gap-1.5 bg-white/10 text-white text-sm font-medium px-3.5 py-1.5 rounded-full">
                <ArrowTrendingUpIcon class="h-4 w-4 text-green-400" />
                {{ platformStats.validTripCount.toLocaleString() }} {{ t('models_list.reddit.trips') }}
              </span>
            </div>
            <div class="flex flex-wrap gap-3">
              <a :href="registerPath"
                 class="inline-flex items-center gap-2 bg-green-500 hover:bg-green-400 text-white font-semibold px-5 py-2.5 rounded-lg transition-colors text-sm">
                {{ t('models_list.reddit.cta') }}
                <ArrowRightIcon class="h-4 w-4" />
              </a>
              <span class="inline-flex items-center text-gray-400 text-xs self-center">{{ t('models_list.reddit.no_subscription') }}</span>
            </div>
          </div>
        </template>
      </div>

      <!-- Loading state -->
      <div v-if="loading" class="flex items-center justify-center py-20">
        <div class="animate-spin rounded-full h-10 w-10 border-b-2 border-green-600"></div>
      </div>

      <!-- Filters + Popular Models -->
      <div v-if="!loading && modelsWithData.length > 0" class="mb-6">
        <h2 class="text-lg font-semibold text-gray-800 dark:text-gray-200 mb-3 text-center">{{ t('models_list.filters.popular') }}</h2>
        <div class="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-4">
          <div class="flex flex-wrap items-center gap-3">
            <!-- Marken-Filter -->
            <div class="relative">
              <button
                @click="dropdownOpen = !dropdownOpen; categoryDropdownOpen = false"
                class="flex items-center gap-2 px-4 py-2 bg-white dark:bg-gray-700 border border-gray-300 dark:border-gray-600 rounded-lg hover:bg-gray-50 dark:hover:bg-gray-600 transition-colors"
              >
                <span class="text-sm">
                  {{ selectedBrands.length === 0 ? t('models_list.filters.all_brands') : t('models_list.filters.brand_count', { count: selectedBrands.length }) }}
                </span>
                <span class="text-gray-400">▼</span>
              </button>

              <div
                v-if="dropdownOpen"
                class="absolute top-full left-0 mt-2 w-64 bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl shadow-lg z-50 max-h-96 overflow-y-auto"
              >
                <div class="p-2">
                  <div class="flex gap-2 pb-2 mb-2 border-b border-gray-100 dark:border-gray-700">
                    <button
                      @click="selectAllBrands"
                      class="flex-1 text-xs px-2 py-1.5 bg-green-50 text-green-700 rounded hover:bg-green-100 dark:bg-green-900/40 dark:text-green-400 dark:hover:bg-green-900/60"
                    >
                      {{ t('models_list.filters.select_all') }}
                    </button>
                    <button
                      @click="clearAllBrands"
                      class="flex-1 text-xs px-2 py-1.5 bg-gray-50 dark:bg-gray-700 text-gray-700 dark:text-gray-300 rounded hover:bg-gray-100 dark:hover:bg-gray-600"
                    >
                      {{ t('models_list.filters.deselect_all') }}
                    </button>
                  </div>
                  <label
                    v-for="brand in availableBrands"
                    :key="brand"
                    class="flex items-center gap-2 px-2 py-2 hover:bg-gray-50 dark:hover:bg-gray-700 rounded cursor-pointer"
                  >
                    <input
                      type="checkbox"
                      :checked="selectedBrands.includes(brand)"
                      @change="toggleBrand(brand)"
                      class="w-4 h-4 text-green-600 border-gray-300 dark:border-gray-600 rounded focus:ring-green-500"
                    />
                    <span class="text-sm text-gray-700 dark:text-gray-300">{{ brand }}</span>
                  </label>
                </div>
              </div>
            </div>

            <!-- Kategorie-Filter -->
            <div class="relative">
              <button
                @click="categoryDropdownOpen = !categoryDropdownOpen; dropdownOpen = false"
                class="flex items-center gap-2 px-4 py-2 bg-white dark:bg-gray-700 border rounded-lg hover:bg-gray-50 dark:hover:bg-gray-600 transition-colors"
                :class="selectedCategory !== null
                  ? 'border-green-500 text-green-700 dark:text-green-400'
                  : 'border-gray-300 dark:border-gray-600'"
              >
                <span class="text-sm">
                  {{ selectedCategory === null ? t('models_list.filters.all_categories') : t(`models_list.filters.categories.${selectedCategory}`, categories.find(c => c.key === selectedCategory)?.displayName ?? '') }}
                </span>
                <span class="text-gray-400">▼</span>
              </button>

              <div
                v-if="categoryDropdownOpen"
                class="absolute top-full left-0 mt-2 w-52 bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl shadow-lg z-50 overflow-hidden"
              >
                <div class="p-1">
                  <button
                    @click="selectCategory(null)"
                    class="w-full text-left px-3 py-2 text-sm rounded-lg hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors"
                    :class="selectedCategory === null ? 'text-green-700 dark:text-green-400 font-medium bg-green-50 dark:bg-green-900/20' : 'text-gray-700 dark:text-gray-300'"
                  >
                    {{ t('models_list.filters.all_categories') }}
                  </button>
                  <button
                    v-for="cat in categories"
                    :key="cat.key"
                    @click="selectCategory(cat.key)"
                    class="w-full text-left px-3 py-2 text-sm rounded-lg hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors"
                    :class="selectedCategory === cat.key ? 'text-green-700 dark:text-green-400 font-medium bg-green-50 dark:bg-green-900/20' : 'text-gray-700 dark:text-gray-300'"
                  >
                    {{ t(`models_list.filters.categories.${cat.key}`, cat.displayName) }}
                  </button>
                </div>
              </div>
            </div>

            <!-- Active Filter Count -->
            <span v-if="selectedBrands.length > 0 || selectedCategory !== null" class="text-sm text-gray-500 dark:text-gray-400">
              {{ t('models_list.filters.results', { filtered: filteredModels.length, total: modelsWithData.length }) }}
            </span>
          </div>
        </div>
      </div>

      <!-- THG Banner -->
      <ThgBanner v-if="!isAuthenticated" />

      <!-- Models Grid -->
      <div v-if="filteredModels.length > 0" class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        <!-- Real community models -->
        <template v-for="(model, index) in filteredModels" :key="`${model.brandDisplayName}/${model.modelUrlSlug}`">
        <!-- Affiliate Banner: nach 4. Card (Mobile/2-Spalten), nach 6. Card (3-Spalten) -->
        <div v-if="(index === 4 || index === 6) && !isAuthenticated"
             :class="index === 4 ? 'col-span-full lg:hidden' : 'col-span-full hidden lg:block'">
          <AffiliateBanner />
        </div>
        <div
          class="model-card flex flex-col bg-white dark:bg-gray-800 rounded-xl border p-4 transition-all shadow-[0_10px_0_0_rgb(0_0_0/0.13)] dark:shadow-none hover:-translate-y-1 hover:shadow-[0_12px_0_0_rgb(0_0_0/0.17)] dark:hover:shadow-lg hover:z-10 active:translate-y-0 active:shadow-[0_10px_0_0_rgb(0_0_0/0.13)] relative"
          :class="isSelectedForCompare(`${model.brandDisplayName}/${model.modelUrlSlug}`)
            ? 'border-green-500 ring-2 ring-green-200 dark:ring-green-900'
            : 'border-gray-200 dark:border-gray-700 hover:border-green-500'"
        >
          <a :href="`${modelsBaseUrl}/${model.brandDisplayName}/${model.modelUrlSlug}`" class="block flex-1">
            <div class="mb-3 text-center">
              <h3 class="font-bold text-gray-900 dark:text-gray-100 text-base leading-tight">{{ model.modelDisplayName }}</h3>
              <span class="text-xs text-gray-400">{{ model.logCount }} {{ t('models_list.card.charging_sessions') }}</span>
            </div>
            <div class="grid grid-cols-[auto_1fr] items-baseline gap-x-3 gap-y-0.5 mb-3 text-sm">
              <template v-if="model.minWltpConsumptionKwhPer100km">
                <span class="text-xs text-gray-400">{{ t('models_list.card.wltp_label') }}</span>
                <span class="text-gray-500 dark:text-gray-400 font-medium">{{ formatWltpRange(model.minWltpConsumptionKwhPer100km, model.maxWltpConsumptionKwhPer100km) }} {{ consumptionUnitLabel() }}</span>
              </template>
              <template v-if="model.avgConsumptionKwhPer100km || model.minRealConsumptionKwhPer100km">
                <span class="text-xs text-gray-400">{{ t('models_list.card.real_label') }}</span>
                <span class="text-gray-700 dark:text-gray-300 font-medium">{{ formatRealConsumption(model.avgConsumptionKwhPer100km, model.minRealConsumptionKwhPer100km, model.maxRealConsumptionKwhPer100km) }} {{ consumptionUnitLabel() }}</span>
              </template>
              <template v-if="model.avgCostPerKwh && model.avgConsumptionKwhPer100km">
                <span class="text-xs text-gray-400">{{ t('models_list.card.costs_label') }}</span>
                <span class="flex flex-wrap items-center gap-x-1.5">
                  <span class="text-blue-500 font-medium">~{{ formatCostPerDistance(model.avgCostPerKwh * model.avgConsumptionKwhPer100km) }}</span>
                  <span class="relative group cursor-help inline-flex items-center gap-0.5 text-xs text-gray-400">
                    <span>{{ t('models_list.card.avg_prefix') }} {{ formatCostPerKwh(model.avgCostPerKwh) }}</span>
                    <InformationCircleIcon class="h-3 w-3 flex-shrink-0" />
                    <span class="absolute bottom-full left-0 mb-1.5 px-2.5 py-2 bg-gray-900 ring-1 ring-white/10 text-white text-xs rounded-lg w-60 hidden group-hover:block z-50 pointer-events-none leading-snug shadow-xl">
                      {{ t('models_list.card.cost_tooltip') }}
                    </span>
                  </span>
                </span>
              </template>
            </div>
            <div class="flex items-center justify-between mt-auto">
              <div class="text-green-600 font-medium flex items-center gap-1 text-sm">
                <span>{{ t('models_list.card.view_details') }}</span>
                <ArrowRightIcon class="h-4 w-4" />
              </div>
              <button
                @click.prevent="toggleCompare(`${model.brandDisplayName}/${model.modelUrlSlug}`)"
                :disabled="!isSelectedForCompare(`${model.brandDisplayName}/${model.modelUrlSlug}`) && selectedForCompare.length >= MAX_COMPARE"
                :title="isSelectedForCompare(`${model.brandDisplayName}/${model.modelUrlSlug}`) ? t('models_list.card.remove_compare') : t('models_list.card.add_compare')"
                class="p-1.5 rounded-full transition-all flex-shrink-0 shadow-[0_3px_0_0] active:translate-y-[2px] active:shadow-[0_1px_0_0]"
                :class="isSelectedForCompare(`${model.brandDisplayName}/${model.modelUrlSlug}`)
                  ? 'bg-green-500 text-white shadow-green-700 hover:bg-green-400'
                  : selectedForCompare.length >= MAX_COMPARE
                    ? 'bg-gray-100 dark:bg-gray-700 text-gray-300 cursor-not-allowed shadow-gray-300 dark:shadow-gray-900'
                    : 'bg-gray-100 dark:bg-gray-700 text-gray-400 hover:bg-green-100 hover:text-green-600 shadow-gray-300 dark:shadow-gray-900 hover:shadow-green-300'"
              >
                <CheckIcon v-if="isSelectedForCompare(`${model.brandDisplayName}/${model.modelUrlSlug}`)" class="h-4 w-4" />
                <ArrowsRightLeftIcon v-else class="h-4 w-4" />
              </button>
            </div>
          </a>
        </div>
        </template>

        <!-- Fallback filler models (mobile only, no brand filter active) — no compare toggle -->
        <a
          v-for="model in mobileFillModels"
          :key="`fallback/${model.brand}/${model.model}`"
          :href="`${modelsBaseUrl}/${model.brand}/${model.model}`"
          class="model-card bg-white dark:bg-gray-800 rounded-xl border border-gray-100 dark:border-gray-700 p-4 hover:border-green-400 hover:shadow-sm transition-all opacity-80"
        >
          <div class="flex items-start justify-between mb-2">
            <h3 class="font-semibold text-gray-700 dark:text-gray-300 text-base">{{ model.displayName }}</h3>
            <TruckIcon class="h-6 w-6 text-gray-300" />
          </div>
          <div class="flex items-center gap-2 text-xs text-gray-400">
            <ChartBarIcon class="h-3.5 w-3.5" />
            <span>{{ t('models_list.card.wltp_available') }}</span>
          </div>
        </a>
      </div>

      <!-- Empty state: No models at all -->
      <div v-else-if="!loading && modelsWithData.length === 0" class="text-center py-20">
        <TruckIcon class="h-16 w-16 text-gray-300 mb-4 mx-auto" />
        <h2 class="text-xl font-bold text-gray-800 dark:text-gray-200 mb-2">{{ t('models_list.empty.no_data_title') }}</h2>
        <p class="text-gray-500 dark:text-gray-400 mb-6">
          {{ t('models_list.empty.no_data_desc') }}
        </p>
        <a :href="registerPath" class="inline-block bg-green-600 text-white px-6 py-3 rounded-lg hover:bg-green-700">
          {{ t('models_list.empty.free_register') }}
        </a>
      </div>

      <!-- Empty state: Filtered but no results -->
      <div v-else-if="!loading && (selectedBrands.length > 0 || selectedCategory !== null) && filteredModels.length === 0" class="text-center py-20">
        <div class="text-5xl mb-4">🔍</div>
        <h2 class="text-xl font-bold text-gray-800 dark:text-gray-200 mb-2">{{ t('models_list.empty.no_results_title') }}</h2>
        <p class="text-gray-500 dark:text-gray-400 mb-6">
          {{ t('models_list.empty.no_results_desc') }}
        </p>
        <button
          @click="clearAllFilters"
          class="bg-green-600 text-white px-4 py-2 rounded-lg hover:bg-green-700"
        >
          {{ t('models_list.empty.reset_filters') }}
        </button>
      </div>

      <!-- Brand Grid -->
      <div v-if="!loading && (availableBrands.length > 0 || brandFillItems.length > 0)" class="mb-8 mt-8">
        <h2 class="text-lg font-semibold text-gray-800 dark:text-gray-200 mb-3">{{ t('models_list.brands.title') }}</h2>
        <div class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-3">
          <a
            v-for="brand in availableBrands"
            :key="brand"
            :href="`${modelsBaseUrl}/${brand}`"
            class="brand-card bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 px-4 py-3 flex items-center gap-2 hover:border-green-500 hover:shadow-sm transition-all"
          >
            <TruckIcon class="h-5 w-5 text-gray-400 flex-shrink-0" />
            <span class="text-sm font-medium text-gray-800 dark:text-gray-200 truncate">{{ brand }}</span>
          </a>
          <!-- Fallback brands without community data yet -->
          <a
            v-for="brand in brandFillItems"
            :key="`fallback/${brand}`"
            :href="`${modelsBaseUrl}/${brand}`"
            class="brand-card bg-white dark:bg-gray-800 rounded-xl border border-gray-100 dark:border-gray-700 px-4 py-3 flex items-center gap-2 hover:border-green-400 hover:shadow-sm transition-all opacity-70"
          >
            <TruckIcon class="h-5 w-5 text-gray-300 flex-shrink-0" />
            <span class="text-sm font-medium text-gray-500 dark:text-gray-400 truncate">{{ brand }}</span>
          </a>
        </div>
      </div>

      <!-- CTA Section -->
      <div v-if="!loading" class="bg-gradient-to-br from-green-600 to-green-700 rounded-2xl p-8 text-white mt-8">
        <div class="flex items-center gap-2 mb-3">
          <ArrowTrendingUpIcon class="h-7 w-7" />
          <h2 class="text-2xl font-bold">{{ t('models_list.cta.missing_vehicle') }}</h2>
        </div>
        <p class="text-green-100 mb-6">
          {{ t('models_list.cta.contribute') }}
        </p>
        <div class="flex flex-wrap gap-3">
          <a :href="registerPath"
             class="bg-white text-green-700 font-semibold px-6 py-3 rounded-lg hover:bg-green-50 transition-colors">
            {{ t('models_list.cta.free_start') }}
          </a>
          <a :href="loginPath"
             class="border-2 border-white text-white px-6 py-3 rounded-lg hover:bg-green-600 transition-colors">
            {{ t('models_list.cta.login') }}
          </a>
        </div>
      </div>

      <!-- Floating Compare Bar (outside v-if/v-else chain) -->
      <Transition name="compare-bar">
        <div
          v-if="selectedForCompare.length >= 1"
          class="fixed bottom-20 left-1/2 -translate-x-1/2 z-50 w-full max-w-lg px-4"
        >
          <div class="bg-gray-900 text-white rounded-2xl shadow-2xl px-4 py-3 flex items-center gap-3">
            <ArrowsRightLeftIcon class="h-5 w-5 text-green-400 flex-shrink-0" />
            <div class="flex-1 min-w-0">
              <div class="text-xs text-gray-400 mb-0.5">{{ t('models_list.compare.title', { current: selectedForCompare.length, max: MAX_COMPARE }) }}</div>
              <div class="text-sm font-medium truncate">
                {{ selectedForCompare.map(compareLabel).join(' · ') }}
              </div>
            </div>
            <button
              @click="startCompare"
              class="flex-shrink-0 bg-green-500 hover:bg-green-400 text-white text-sm font-semibold px-4 py-1.5 rounded-lg transition-colors">
              {{ t('models_list.compare.button') }}
            </button>
            <button @click="clearCompare" class="flex-shrink-0 p-1 text-gray-400 hover:text-white transition-colors">
              <XMarkIcon class="h-5 w-5" />
            </button>
          </div>
        </div>
      </Transition>
      </div>
    </main>

    <footer v-if="!isAuthenticated" class="max-w-6xl mx-auto px-4 py-8 mt-6 border-t border-gray-200 dark:border-gray-700 text-sm text-gray-500 dark:text-gray-400 text-center">
      © {{ currentYear }} EV Monitor ·
      <a href="/" class="hover:text-gray-700 dark:hover:text-gray-200">{{ isAuthenticated ? t('nav.dashboard') : t('nav.login') }}</a>
      <template v-if="!isAuthenticated">
        ·
        <a :href="registerPath" class="hover:text-gray-700 dark:hover:text-gray-200">{{ t('common.free_start') }}</a> ·
        <a :href="loginPath" class="hover:text-gray-700 dark:hover:text-gray-200">{{ t('common.login') }}</a>
      </template>
    </footer>
  </div>
  <DemoModelsModal />
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useHead } from '@unhead/vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '../stores/auth'
import { getTopModels, getPlatformStats, getCategories, type TopModelPreview, type PlatformStats, type VehicleCategoryItem } from '../api/publicModelService'
import { TruckIcon, ChartBarIcon, ArrowTrendingUpIcon, ArrowsRightLeftIcon, XMarkIcon, CheckIcon, ArrowRightIcon, InformationCircleIcon } from '@heroicons/vue/24/outline'
import PublicNav from '../components/shared/PublicNav.vue'
import AffiliateBanner from '../components/shared/AffiliateBanner.vue'
import ThgBanner from '../components/shared/ThgBanner.vue'
import DemoModelsModal from '../components/demo/DemoModelsModal.vue'
import { useLocaleFormat } from '../composables/useLocaleFormat'
import { useMarketRoute, getMarketBasePath, OG_LOCALE, MARKET_HTML_LANG } from '../composables/useMarketRoute'

const { t } = useI18n()
const { consumptionUnitLabel, formatCostPerDistance, formatCostPerKwh, formatDecimal, convertConsumption } = useLocaleFormat()
const router = useRouter()
const { currentMarket, isDE, isEN, isGB, isUS, marketUrl, hreflangLinks } = useMarketRoute()
const modelsBaseUrl = computed(() => getMarketBasePath(currentMarket.value))
const loginPath = computed(() => (isEN.value || isGB.value || isUS.value) ? '/en/login' : '/login')
const registerPath = computed(() => (isEN.value || isGB.value || isUS.value) ? '/en/register' : '/register')

const isRedditSource = new URLSearchParams(window.location.search).get('utm_source') === 'reddit'

const authStore = useAuthStore()
const loading = ref(true)
const modelsList = ref<TopModelPreview[]>([])
const platformStats = ref<PlatformStats | null>(null)
const selectedBrands = ref<string[]>([])
const dropdownOpen = ref(false)
const categoryDropdownOpen = ref(false)
const selectedCategory = ref<string | null>(null)
const categories = ref<VehicleCategoryItem[]>([])

// ── Comparison selection ───────────────────────────────────────────────────
const MAX_COMPARE = 3
const selectedForCompare = ref<string[]>([])

function isSelectedForCompare(key: string) {
  return selectedForCompare.value.includes(key)
}

function toggleCompare(key: string) {
  const idx = selectedForCompare.value.indexOf(key)
  if (idx > -1) {
    selectedForCompare.value.splice(idx, 1)
  } else if (selectedForCompare.value.length < MAX_COMPARE) {
    selectedForCompare.value.push(key)
  }
}

function clearCompare() {
  selectedForCompare.value = []
}

function startCompare() {
  const comparePath = isDE.value ? '/modelle/vergleich' : '/en/models/compare'
  router.push(`${comparePath}?models=${selectedForCompare.value.join(',')}`)
}

function compareLabel(key: string): string {
  return key.replace(/_/g, ' ')
}

function formatWltpRange(min: number, max: number | null): string {
  if (!max || Math.abs(max - min) < 0.05) return formatDecimal(convertConsumption(min))
  return `${formatDecimal(convertConsumption(min))} - ${formatDecimal(convertConsumption(max))}`
}

function formatRealConsumption(avg: number | null, min: number | null, max: number | null): string {
  if (min !== null && max !== null) return formatWltpRange(min, max)
  if (avg !== null) return formatDecimal(convertConsumption(avg))
  return '—'
}

const isAuthenticated = computed(() => authStore.isAuthenticated())
const currentYear = new Date().getFullYear()

type ModelInfo = TopModelPreview

interface FallbackModel {
  brand: string
  model: string
  displayName: string
}

// Popular EV brands in Germany — fallback when fewer than 20 brands have community data
const POPULAR_DE_BRANDS_FALLBACK = [
  'Tesla', 'Volkswagen', 'Hyundai', 'BMW', 'Audi', 'Kia', 'Mercedes-Benz',
  'Škoda', 'Polestar', 'MG Motor', 'Renault', 'Volvo', 'Ford', 'Porsche',
  'Opel', 'Cupra', 'Nissan', 'Fiat', 'Dacia', 'BYD',
]

const brandFillItems = computed((): string[] => {
  const needed = 20 - availableBrands.value.length
  if (needed <= 0) return []
  const existing = new Set(availableBrands.value.map(b => b.toLowerCase()))
  return POPULAR_DE_BRANDS_FALLBACK
    .filter(b => !existing.has(b.toLowerCase()))
    .slice(0, needed)
})

// Popular EV models in Germany by registration numbers (DE slugs matching DB)
const POPULAR_DE_FALLBACK: FallbackModel[] = [
  { brand: 'Volkswagen', model: 'ID.3', displayName: 'VW ID.3' },
  { brand: 'Tesla', model: 'Model_Y', displayName: 'Tesla Model Y' },
  { brand: 'Tesla', model: 'Model_3', displayName: 'Tesla Model 3' },
  { brand: 'Volkswagen', model: 'ID.4', displayName: 'VW ID.4' },
  { brand: 'Hyundai', model: 'Ioniq_5', displayName: 'Hyundai Ioniq 5' },
  { brand: 'Škoda', model: 'Enyaq', displayName: 'Škoda Enyaq' },
  { brand: 'BMW', model: 'i4', displayName: 'BMW i4' },
  { brand: 'Audi', model: 'Q4_e-tron', displayName: 'Audi Q4 e-tron' },
  { brand: 'Kia', model: 'EV6', displayName: 'Kia EV6' },
  { brand: 'MG Motor', model: 'MG4', displayName: 'MG MG4' },
  { brand: 'Hyundai', model: 'Ioniq_6', displayName: 'Hyundai Ioniq 6' },
  { brand: 'Polestar', model: 'Polestar_2', displayName: 'Polestar 2' },
]

const modelsWithData = computed((): ModelInfo[] => modelsList.value)

const availableBrands = computed(() => {
  const brands = new Set(modelsWithData.value.map(m => m.brandDisplayName))
  return Array.from(brands).sort()
})

const filteredModels = computed(() => {
  let models = modelsWithData.value
  if (selectedBrands.value.length > 0) {
    models = models.filter(m => selectedBrands.value.includes(m.brandDisplayName))
  }
  if (selectedCategory.value !== null) {
    models = models.filter(m => m.category === selectedCategory.value)
  }
  return models
})

// Fill up to 12 models on mobile with popular fallbacks (only when no filters active)
const mobileFillModels = computed((): FallbackModel[] => {
  if (selectedBrands.value.length > 0 || selectedCategory.value !== null) return []
  const needed = 12 - filteredModels.value.length
  if (needed <= 0) return []
  const normalize = (s: string) => s.replace(/ /g, '_').toLowerCase()
  const existingSlugs = new Set(filteredModels.value.map(m => normalize(`${m.brandDisplayName}/${m.modelUrlSlug}`)))
  return POPULAR_DE_FALLBACK
    .filter(f => !existingSlugs.has(normalize(`${f.brand}/${f.model}`)))
    .slice(0, needed)
})

// Reactive JSON-LD: brands first (from live data), then popular model fallbacks
const itemListJsonLd = computed(() => {
  const BASE = 'https://ev-monitor.net'
  const brandItems = availableBrands.value.map((brand, i) => ({
    '@type': 'ListItem',
    position: i + 1,
    name: `${brand} Elektroautos – Realer Verbrauch`,
    url: `${BASE}/modelle/${brand}`
  }))

  // Append popular model entries after brands (for long-tail keywords)
  const POPULAR_MODELS = [
    { name: 'Tesla Model 3', url: `${BASE}/modelle/Tesla/Model_3` },
    { name: 'Tesla Model Y', url: `${BASE}/modelle/Tesla/Model_Y` },
    { name: 'VW ID.3', url: `${BASE}/modelle/Volkswagen/ID.3` },
    { name: 'VW ID.4', url: `${BASE}/modelle/Volkswagen/ID.4` },
    { name: 'Hyundai Ioniq 5', url: `${BASE}/modelle/Hyundai/Ioniq_5` },
    { name: 'Hyundai Ioniq 6', url: `${BASE}/modelle/Hyundai/Ioniq_6` },
    { name: 'BMW i4', url: `${BASE}/modelle/BMW/i4` },
    { name: 'Audi Q4 e-tron', url: `${BASE}/modelle/Audi/Q4_e-tron` },
    { name: 'Kia EV6', url: `${BASE}/modelle/Kia/EV6` },
    { name: 'Polestar 2', url: `${BASE}/modelle/Polestar/Polestar_2` },
  ]
  const offset = brandItems.length
  const modelItems = POPULAR_MODELS.map((m, i) => ({
    '@type': 'ListItem',
    position: offset + i + 1,
    name: m.name,
    url: m.url
  }))

  return {
    '@context': 'https://schema.org',
    '@type': 'ItemList',
    name: 'Elektroauto Marken & Modelle – Realer Verbrauch & WLTP Vergleich',
    description: 'Community-Verbrauchsdaten für alle Elektroautos im Vergleich zum WLTP-Wert.',
    itemListElement: brandItems.length > 0 ? [...brandItems, ...modelItems] : modelItems
  }
})

const breadcrumbJsonLd = {
  '@context': 'https://schema.org',
  '@type': 'BreadcrumbList',
  itemListElement: [
    { '@type': 'ListItem', position: 1, name: 'EV Monitor', item: 'https://ev-monitor.net' },
    { '@type': 'ListItem', position: 2, name: 'Elektroautos im Vergleich', item: 'https://ev-monitor.net/modelle' },
  ]
}

useHead(computed(() => {
  const canonical = marketUrl(currentMarket.value)
  const htmlLang = MARKET_HTML_LANG[currentMarket.value]
  return {
    title: t('models_list.meta_title'),
    htmlAttrs: { lang: htmlLang },
    meta: [
      { name: 'description', content: t('models_list.meta_description') },
      { name: 'keywords', content: t('models_list.meta_keywords') },
      { name: 'robots', content: 'index, follow' },
      { property: 'og:title', content: t('models_list.og_title') },
      { property: 'og:description', content: t('models_list.og_description') },
      { property: 'og:type', content: 'website' },
      { property: 'og:url', content: canonical },
      { property: 'og:locale', content: OG_LOCALE[currentMarket.value] ?? 'en_GB' },
    ],
    link: [
      { rel: 'canonical', href: canonical },
      ...hreflangLinks(),
    ],
    script: [
      { type: 'application/ld+json', innerHTML: () => JSON.stringify(itemListJsonLd.value) },
      { type: 'application/ld+json', innerHTML: JSON.stringify(breadcrumbJsonLd) },
    ]
  }
}))

onMounted(async () => {
  try {
    const [models, stats, cats] = await Promise.all([getTopModels(50), getPlatformStats(), getCategories()])
    modelsList.value = models
    platformStats.value = stats
    categories.value = cats
  } catch (err) {
    console.error('Failed to load models:', err)
  } finally {
    loading.value = false
  }

  // Close dropdown on click outside
  document.addEventListener('click', handleClickOutside)
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
})

function handleClickOutside(event: MouseEvent) {
  const target = event.target as HTMLElement
  if (!target.closest('.relative')) {
    dropdownOpen.value = false
    categoryDropdownOpen.value = false
  }
}

function toggleBrand(brand: string) {
  const index = selectedBrands.value.indexOf(brand)
  if (index > -1) {
    selectedBrands.value.splice(index, 1)
  } else {
    selectedBrands.value.push(brand)
  }
}

function selectAllBrands() {
  selectedBrands.value = [...availableBrands.value]
}

function clearAllBrands() {
  selectedBrands.value = []
  dropdownOpen.value = false
}

function clearAllFilters() {
  selectedBrands.value = []
  selectedCategory.value = null
  dropdownOpen.value = false
  categoryDropdownOpen.value = false
}

function selectCategory(key: string | null) {
  selectedCategory.value = key
  categoryDropdownOpen.value = false
}
</script>

<style scoped>
/* 3D press effect for brand cards */
.brand-card {
  box-shadow: 0 3px 0 0 rgba(0,0,0,0.08);
  transform: translateY(0);
  transition: transform 0.08s ease, box-shadow 0.08s ease, border-color 0.15s ease;
}
.brand-card:active {
  box-shadow: 0 1px 0 0 rgba(0,0,0,0.08);
  transform: translateY(2px);
  transition: transform 0.05s ease, box-shadow 0.05s ease;
}

/* 3D press effect for model cards */
.model-card {
  box-shadow: 0 4px 0 0 rgba(0,0,0,0.10);
  transform: translateY(0);
  transition: transform 0.08s ease, box-shadow 0.08s ease, border-color 0.15s ease;
}
.model-card:active {
  box-shadow: 0 1px 0 0 rgba(0,0,0,0.10);
  transform: translateY(3px);
  transition: transform 0.05s ease, box-shadow 0.05s ease;
}

.compare-bar-enter-active,
.compare-bar-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}
.compare-bar-enter-from,
.compare-bar-leave-to {
  opacity: 0;
  transform: translateX(-50%) translateY(16px);
}
</style>
