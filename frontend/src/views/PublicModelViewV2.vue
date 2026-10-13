<template>
  <div class="min-h-screen bg-gray-100 md:bg-gray-50 dark:bg-gray-950">
    <div class="model-page">
    <PublicNav />
    <main class="mx-auto max-w-5xl py-2 md:px-6 md:py-8">

      <div v-if="loading" class="flex items-center justify-center py-20">
        <div class="animate-spin rounded-full h-10 w-10 border-b-2 border-green-600"></div>
      </div>

      <div v-else-if="notFound" class="text-center py-20">
        <div class="text-5xl mb-4">🔍</div>
        <h1 class="t-value-lg text-gray-800 dark:text-gray-200 mb-2">{{ t('model.not_found_title') }}</h1>
        <p class="text-gray-500 dark:text-gray-400 mb-6">{{ t('model.not_found_desc') }}</p>
        <a href="/" class="bg-green-600 text-white px-4 py-2 rounded-sm hover:bg-green-700">{{ t('model.goto_home') }}</a>
      </div>

      <div v-else-if="apiError" class="text-center py-20">
        <h1 class="t-value-lg text-gray-800 dark:text-gray-200 mb-2">{{ t('model.error_title') }}</h1>
        <p class="text-gray-500 dark:text-gray-400 mb-6">{{ t('model.error_desc') }}</p>
        <button @click="reload" class="bg-green-600 text-white px-4 py-2 rounded-sm hover:bg-green-700">{{ t('model.reload') }}</button>
      </div>

      <div v-else-if="stats">

        <StartCodeTeaser v-if="isAuthenticated" class="mx-4 md:mx-0 mb-3" />

        <!-- Breadcrumb: als Chip geerdet, damit er nicht im Gitter-Hintergrund untergeht -->
        <nav aria-label="Breadcrumb" class="mb-4 px-4 md:px-0">
          <ol class="flex flex-nowrap items-center gap-1.5 text-sm whitespace-nowrap overflow-x-auto max-w-full [scrollbar-width:none] [&::-webkit-scrollbar]:hidden bg-white/80 dark:bg-gray-800/80 backdrop-blur-sm border border-gray-200 dark:border-gray-700 rounded-full px-3.5 py-1.5 shadow-sm">
            <li class="shrink-0"><a href="/" class="flex items-center gap-1 font-medium text-gray-600 dark:text-gray-300 hover:text-green-700 dark:hover:text-green-400 transition-colors"><HomeIcon class="h-4 w-4 shrink-0" aria-hidden="true" /><span class="sr-only md:not-sr-only">{{ t('model.breadcrumb_home') }}</span></a></li>
            <li class="flex shrink-0 items-center gap-1.5"><ChevronRightIcon class="h-3.5 w-3.5 text-gray-300 dark:text-gray-600" aria-hidden="true" /><a :href="modelsBaseUrl" class="font-medium text-gray-600 dark:text-gray-300 hover:text-green-700 dark:hover:text-green-400 transition-colors">{{ t('model.breadcrumb_models') }}</a></li>
            <li class="flex shrink-0 items-center gap-1.5"><ChevronRightIcon class="h-3.5 w-3.5 text-gray-300 dark:text-gray-600" aria-hidden="true" /><a :href="`${modelsBaseUrl}/${canonicalBrand}`" class="font-medium text-gray-600 dark:text-gray-300 hover:text-green-700 dark:hover:text-green-400 transition-colors">{{ stats.brandDisplayName }}</a></li>
            <li aria-current="page" class="flex shrink-0 items-center gap-1.5 font-bold text-gray-900 dark:text-gray-100"><ChevronRightIcon class="h-3.5 w-3.5 text-gray-300 dark:text-gray-600" aria-hidden="true" />{{ shortModelName }}</li>
          </ol>
        </nav>

        <div class="grid gap-2 md:gap-5">
          <!-- 1 Hero: its own box, the questions docked below it. On the phone the green chat closes the box and the
               change of surface to the next box is the separation, so no gap and no line on top of it. -->
          <header class="relative z-10 -mb-2 border-gray-200 bg-white md:mb-0 md:rounded-3xl md:border md:shadow-[0_1px_2px_rgba(0,0,0,0.04)] dark:border-gray-800 dark:bg-gray-900">
            <div class="grid gap-5 px-4 py-6 md:gap-6 md:px-10 md:py-8">
              <div class="grid grid-cols-[minmax(0,1fr)_auto] items-start gap-3 md:gap-8">
                <h1 class="max-w-[30ch] text-balance text-[22px] md:text-[32px] font-bold leading-[1.15] tracking-tight text-gray-900 dark:text-gray-100">{{ t('model.hero_title_v2', { model: stats.modelDisplayName }) }}</h1>
                <VariantPicker v-if="activeVariants.length > 1" v-model="selectedVariantIndex" :options="variantOptions" class="justify-self-end" />
                <p v-else-if="variantLabel" class="text-[14px] font-medium text-gray-500 dark:text-gray-400">{{ variantLabel }}</p>
              </div>

              <!-- Phone: claim, compact gauge, basis, so claim and picture share the first screen.
                   From md up: claim and basis on the left, the gauge on the right. -->
              <div class="grid gap-4 md:grid-cols-[minmax(0,1fr)_minmax(0,340px)] md:items-center md:gap-x-10 md:gap-y-3">
                <!-- One sentence per line: "Der Prospekt sagt 390 km." then "48 Fahrer sagen 280 km." -->
                <p class="text-[clamp(22px,6vw,42px)] font-bold leading-[1.1] tracking-tight text-gray-900 md:col-start-1 md:row-start-1 md:self-end dark:text-gray-100" data-testid="story-hero">
                  <span v-for="(line, i) in heroLines" :key="i" class="block" :class="i === 1 ? 'text-green-700 dark:text-green-400' : ''">{{ line }}{{ ' ' }}</span>
                </p>
                <RangeGauge v-if="storyInput.wltpRangeKm" class="md:col-start-2 md:row-span-2 md:row-start-1"
                            :wltp-km="story.realRangeKm != null && story.prospectRangeKm != null ? story.prospectRangeKm : storyInput.wltpRangeKm"
                            :real-km="story.realRangeKm" :winter-km="story.winterRangeKm"
                            :rating-label="story.realRangeKm != null ? t('model.page.range_wltp') : ratingLabel" />
                <p v-if="story.prospectRangeKm != null && story.realRangeKm != null && storyInput.wltpRangeKm" class="max-w-[46ch] text-[15px] leading-relaxed text-gray-700 md:col-start-1 md:row-start-2 md:self-start md:text-[17px] dark:text-gray-300" data-testid="hero-basis">
                  <!-- the battery window only from md up; on the phone the gauge right above says it -->
                  <span class="hidden md:inline">{{ t('model.page.hero_basis') + ' ' }}</span>
                  <template v-if="story.driverRangeKm">{{ t('model.page.hero_spread', { min: formatDistance(story.driverRangeKm.minKm, { showUnit: false }), max: formatDistance(story.driverRangeKm.maxKm) }) + ' ' }}</template>
                  {{ t('model.page.hero_depends') }}
                </p>
              </div>
            </div>

            <!-- Docked below in light grey: a buyer asks the four questions the page answers -->
            <div class="border-gray-200 bg-gray-50 px-4 py-5 md:rounded-b-3xl md:border-t md:px-10 md:py-7 dark:border-gray-800 dark:bg-gray-800/40">
              <BuyerQuestions @jump="trackJump" />
            </div>
          </header>

          <!-- 2 Data sheet -->
          <ModelFactTiles :title="stats.logCount > 0 ? t('model.page.facts_title') : t('model.page.facts_title_spec')" :sub="factsSub" :facts="story.facts">
            <p v-if="!isEurZone && stats.avgCostPerKwh" class="text-[12px] italic text-gray-500 dark:text-gray-400">{{ t('model.cost_disclaimer', { rate: EUR_EXCHANGE_RATES[currency], currency: currencySymbol, date: RATES_LAST_UPDATED }) }}</p>
          </ModelFactTiles>

          <!-- 3 Driving profile and result: one column on the phone, the result sticky on the right from md up -->
          <div class="grid gap-2 md:grid-cols-[minmax(0,1fr)_380px] md:items-start md:gap-5">
            <ModelBox id="profil" aria-labelledby="profile-title" class="scroll-mt-28" data-testid="driving-profile">
              <div class="grid gap-1.5">
                <h2 id="profile-title" class="text-[21px] font-bold tracking-tight text-gray-900 md:text-[24px] dark:text-gray-100">{{ t('model.page.profile_title') }}</h2>
                <p class="max-w-[52ch] text-[14.5px] leading-snug text-gray-600 dark:text-gray-400">{{ t('model.page.profile_sub', { model: shortModelName }) }}</p>
              </div>
              <div class="grid gap-5">
                <ProfileSlider v-for="q in profileSliders" :key="q.key" :id="`story-${q.key}`" :test-key="q.key" :label="q.label"
                               :min="q.min" :max="q.max" :step="q.step" :start="q.start" :value="q.value" :format="q.format" :detail="q.detail"
                               :highlight="highlightKey === q.key" @commit="v => answer(q.key, v)" />
              </div>
            </ModelBox>
            <div class="md:sticky md:top-24">
              <PersonalRangeCalculator
                :brand="brand"
                :model="model"
                :model-name="shortModelName"
                :log-count="stats.logCount"
                :car-count="stats.uniqueCars"
                :inputs="storyInputs"
                :open-count="openQuestions.length"
                :petrol="petrolComparison"
                @profile="applyProfile"
                @incomplete="showFirstOpen"
                @home="answerHome"
                @price="v => answers.pricePerKwh = v" />
            </div>
          </div>

          <!-- 4 Evidence behind the data sheet -->
          <ModelEvidence v-if="story.evidence.range || story.evidence.routes" :title="t('model.page.evidence_title', { model: shortModelName })" :evidence="story.evidence" />

          <!-- 5 Ratings and more details; side by side from md up -->
          <div class="grid gap-2 md:gap-5" :class="ratings ? 'md:grid-cols-2 md:items-start' : ''">
            <ModelRatingsBlock
              v-if="ratings"
              :ratings="ratings"
              :model="shortModelName"
              :cta-car-id="ratingCtaCarId"
              :register-path="isAuthenticated ? null : registerPath"
            />
            <ModelBox aria-labelledby="details-title" class="!gap-0" data-testid="model-details">
              <h2 id="details-title" class="mb-2 text-[21px] font-bold tracking-tight text-gray-900 md:text-[24px] dark:text-gray-100">{{ t('model.page.details_title') }}</h2>
              <details v-if="showYears" class="group border-t border-gray-100 first-of-type:border-t-0 dark:border-gray-800">
                <summary class="flex min-h-12 cursor-pointer list-none items-center justify-between text-[15px] font-semibold text-gray-900 dark:text-gray-100 [&::-webkit-details-marker]:hidden">
                  {{ t('model.page.details_years') }}<ChevronDownIcon class="h-5 w-5 text-gray-400 transition-transform group-open:rotate-180" aria-hidden="true" />
                </summary>
                <div class="pb-4">
                  <p class="mb-3 text-[13px] text-gray-500 dark:text-gray-400">{{ t('model.year_distribution_title', { count: stats.uniqueCars }) }}</p>
                  <div class="flex items-end gap-1.5 sm:gap-2">
                    <div v-for="entry in stats.yearDistribution" :key="entry.year" class="flex flex-1 flex-col items-center">
                      <span class="mb-1 text-xs font-bold tabular-nums text-gray-700 dark:text-gray-300">{{ entry.carCount }}</span>
                      <div class="w-full rounded-t bg-green-500" :style="{ height: yearBarHeightPx(entry.carCount) }"></div>
                    </div>
                  </div>
                  <div class="mt-1.5 flex gap-1.5 sm:gap-2">
                    <div v-for="entry in stats.yearDistribution" :key="entry.year" class="flex-1 text-center text-xs tabular-nums text-gray-500 dark:text-gray-400">{{ entry.year }}</div>
                  </div>
                  <p v-if="medianModelYear" class="mt-3 text-[13px] text-gray-600 dark:text-gray-300">{{ t('model.year_distribution_summary', { year: medianModelYear }) }}</p>
                </div>
              </details>
              <details v-if="activeVariants.length > 0" class="group border-t border-gray-100 first-of-type:border-t-0 dark:border-gray-800">
                <summary class="flex min-h-12 cursor-pointer list-none items-center justify-between text-[15px] font-semibold text-gray-900 dark:text-gray-100 [&::-webkit-details-marker]:hidden">
                  {{ t('model.page.details_variants') }}<ChevronDownIcon class="h-5 w-5 text-gray-400 transition-transform group-open:rotate-180" aria-hidden="true" />
                </summary>
                <div class="grid gap-3 pb-4">
                  <p v-if="isUS && ratingLabel !== 'EPA'" class="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-700 dark:border-amber-800 dark:bg-amber-900/20 dark:text-amber-400">{{ t('model.epa_not_available') }}</p>
                  <ModelVariantsList :variants="activeVariants" :rating-label="ratingLabel" />
                  <p class="text-[12.5px] leading-snug text-gray-500 dark:text-gray-400">{{ ratingLabel === 'EPA' ? t('model.epa_note') : t('model.wltp_note') }} {{ t('model.community_methodology_note') }}</p>
                </div>
              </details>
              <details v-if="faqItems.length > 0" class="group border-t border-gray-100 first-of-type:border-t-0 dark:border-gray-800">
                <summary class="flex min-h-12 cursor-pointer list-none items-center justify-between text-[15px] font-semibold text-gray-900 dark:text-gray-100 [&::-webkit-details-marker]:hidden">
                  {{ t('model.page.details_faq') }}<ChevronDownIcon class="h-5 w-5 text-gray-400 transition-transform group-open:rotate-180" aria-hidden="true" />
                </summary>
                <dl class="grid gap-4 pb-4">
                  <div v-for="(faq, i) in faqItems" :key="i" class="grid gap-1">
                    <dt class="text-[14px] font-semibold text-gray-900 dark:text-gray-100">{{ faq.question }}</dt>
                    <dd class="text-[14px] leading-relaxed text-gray-600 dark:text-gray-400">{{ faq.answer }}</dd>
                  </div>
                </dl>
              </details>
              <details class="group border-t border-gray-100 first-of-type:border-t-0 dark:border-gray-800">
                <summary class="flex min-h-12 cursor-pointer list-none items-center justify-between text-[15px] font-semibold text-gray-900 dark:text-gray-100 [&::-webkit-details-marker]:hidden">
                  {{ t('model.page.details_related') }}<ChevronDownIcon class="h-5 w-5 text-gray-400 transition-transform group-open:rotate-180" aria-hidden="true" />
                </summary>
                <ul class="flex flex-wrap gap-2 pb-4">
                  <li v-for="r in relatedModels" :key="r.path">
                    <a :href="`${modelsBaseUrl}/${r.path}`" class="inline-flex min-h-11 items-center rounded-full px-3.5 text-[14px] font-medium text-gray-800 ring-1 ring-inset ring-gray-200 hover:bg-gray-100 dark:text-gray-200 dark:ring-gray-700 dark:hover:bg-gray-800">{{ r.name }}</a>
                  </li>
                </ul>
              </details>
            </ModelBox>
          </div>

          <!-- 6 Owners: the call to join -->
          <ModelBox v-if="!isAuthenticated" class="md:grid-cols-[minmax(0,1fr)_auto] md:items-center md:gap-x-8" data-testid="owner-cta">
            <div class="grid gap-1">
              <h2 class="text-balance text-[19px] font-bold text-gray-900 dark:text-gray-100">{{ t('model.story.owner_title', { model: shortModelName }) }}</h2>
              <p class="text-[14.5px] leading-snug text-gray-600 dark:text-gray-400">{{ t('model.story.owner_text', { n: formatNumber(stats.uniqueCars + 1) }) }}</p>
            </div>
            <a :href="registerPath" class="inline-flex min-h-12 items-center justify-center whitespace-nowrap rounded-xl bg-gray-900 px-6 font-semibold text-white hover:bg-gray-800 dark:bg-gray-100 dark:text-gray-900 dark:hover:bg-white">{{ t('model.story.owner_cta') }}</a>
          </ModelBox>

          <!-- SEO text: the long-tail answers in prose, last on the page -->
          <ModelBox aria-labelledby="seo-title" class="!gap-4">
          <h2 id="seo-title" class="text-[19px] font-bold tracking-tight text-gray-900 dark:text-gray-100">
            {{ t('model.seo_section_title', { model: stats.modelDisplayName }) }}
          </h2>
          <div class="space-y-4 text-[14px] text-gray-600 dark:text-gray-400 leading-relaxed">
            <p>
              {{ t('model.seo_intro', { model: stats.modelDisplayName }) }}
              <template v-if="bestOfficialRange"> {{ t('model.seo_wltp_range', { range: formatDistance(bestOfficialRange), ratingLabel }) }}</template>.
              <template v-if="stats.avgConsumptionKwhPer100km">
                <template v-if="consumptionDataQuality === 'good'">
                  {{ t('model.seo_consumption_good', { consumption: formatConsumption(stats.avgConsumptionKwhPer100km), sessions: formatNumber(stats.logCount), count: formatNumber(Math.min(consumptionDataCount, stats.logCount)) }) }}
                </template>
                <template v-else-if="consumptionDataQuality === 'low'">
                  {{ t('model.seo_consumption_low', { consumption: formatConsumption(stats.avgConsumptionKwhPer100km), count: formatNumber(Math.min(consumptionDataCount, stats.logCount)) }) }}
                </template>
                <template v-else>
                  {{ t('model.seo_consumption_sparse', { consumption: formatConsumption(stats.avgConsumptionKwhPer100km), count: formatNumber(Math.min(consumptionDataCount, stats.logCount)) }) }}
                </template>
              </template>
              <template v-else>
                {{ t('model.seo_no_data_cta', { model: stats.modelDisplayName }) }}
              </template>
            </p>
            <div v-if="stats.avgCostPerKwh">
              <h3 class="font-semibold text-gray-800 dark:text-gray-200 mb-1">{{ t('model.seo_costs_title') }}</h3>
              <p>
                {{ t('model.seo_costs_intro', { model: stats.modelDisplayName, price: formatCostPerKwh(stats.avgCostPerKwh) }) }}
                <template v-if="stopCost">
                  {{ t('model.seo_costs_session', { kwh: formatNumber(Math.round(stopCost.kwh)), cost: formatCurrency(stopCost.eur, { decimals: 0 }) }) }}
                </template>
              </p>
            </div>
            <div v-if="activeVariants.length > 0 && consumptionDataCount >= 25">
              <h3 class="font-semibold text-gray-800 dark:text-gray-200 mb-1">{{ t('model.seo_wltp_title', { ratingLabel }) }}</h3>
              <p>
                {{ t('model.seo_wltp_intro', { ratingLabel }) }}
                <template v-if="stats.avgConsumptionKwhPer100km && worstOfficialConsumption">
                  {{ t('model.seo_wltp_delta', { delta: wltpDeltaPercent, ratingLabel }) }}
                </template>
              </p>
            </div>
            <div>
              <h3 class="font-semibold text-gray-800 dark:text-gray-200 mb-1">{{ t('model.seo_seasonal_title') }}</h3>
              <p v-if="storyInput.seasonal">
                {{ t('model.seo_seasonal_data', {
                  model: stats.modelDisplayName,
                  summer: formatConsumption(storyInput.seasonal.summer),
                  winter: formatConsumption(storyInput.seasonal.winter),
                  pct: Math.round((storyInput.seasonal.winter / storyInput.seasonal.summer - 1) * 100)
                }) }}
              </p>
              <p v-else>
                {{ t('model.seo_seasonal_no_data', { model: stats.modelDisplayName }) }}
              </p>
            </div>
          </div>
          </ModelBox>
        </div>

      </div><!-- end stats -->

    </main>

    <ModelCooperationNote v-for="partner in PARTNER_BANNERS" :key="partner.id" :partner="partner" />

    <footer class="max-w-5xl mx-auto px-4 py-8 mt-6 border-t border-gray-200 dark:border-gray-700 text-sm text-gray-500 dark:text-gray-400 text-center">
      <div v-if="!isAuthenticated" class="flex justify-center mb-4">
        <RegionChip />
      </div>
      © {{ currentYear }} EV Monitor ·
      <a href="/" class="hover:text-gray-700 dark:hover:text-gray-200">{{ isAuthenticated ? t('nav.dashboard') : t('model.goto_home') }}</a>
      <template v-if="!isAuthenticated">
        ·
        <a :href="registerPath" class="hover:text-gray-700 dark:hover:text-gray-200">{{ t('common.free_start') }}</a> ·
        <a :href="loginPath" class="hover:text-gray-700 dark:hover:text-gray-200">{{ t('common.login') }}</a>
      </template>
    </footer>

    <!-- Floating back pill: only shown when navigating from LP v2 -->
    <Teleport to="body">
      <div v-if="showBackPill" class="fixed bottom-6 left-4 z-50">
        <button
          @click="goBackToLpV2"
          class="back-pill btn-3d-delay inline-flex items-center gap-1.5 text-sm font-semibold text-white bg-green-600 hover:bg-green-700 rounded-full px-4 py-2 shadow-[4px_4px_0_rgba(0,0,0,0.30)] dark:shadow-[4px_4px_0_rgba(255,255,255,0.30)]"
        >
          <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2.5" stroke="currentColor" class="h-4 w-4">
            <path stroke-linecap="round" stroke-linejoin="round" d="M10.5 19.5 3 12m0 0 7.5-7.5M3 12h18" />
          </svg>
          Zurück
        </button>
      </div>
    </Teleport>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useHead } from '@unhead/vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '../stores/auth'
import { useCountryStore } from '../stores/country'
import { getChargingReferencePrices, getModelStats, getStoryContext, type ModelStoryContext, type PublicModelStats } from '../api/publicModelService'
import { COMBUSTION_LITERS_BY_CLASS, combustionLiters } from '../utils/costMix'
import {
  ChevronDownIcon, ChevronRightIcon, HomeIcon
} from '@heroicons/vue/24/outline'
import PublicNav from '../components/shared/PublicNav.vue'
import StartCodeTeaser from '../components/codes/StartCodeTeaser.vue'
import ModelCooperationNote from '../components/models/ModelCooperationNote.vue'
import { PARTNER_BANNERS } from '../config/cooperations'
import PersonalRangeCalculator from '../components/models/PersonalRangeCalculator.vue'
import RangeGauge from '../components/models/RangeGauge.vue'
import ModelBox from '../components/models/ModelBox.vue'
import VariantPicker, { type VariantOption } from '../components/models/VariantPicker.vue'
import BuyerQuestions from '../components/models/BuyerQuestions.vue'
import { scrollToElement } from '../utils/smoothScroll'
import ModelFactTiles from '../components/models/ModelFactTiles.vue'
import ModelEvidence from '../components/models/ModelEvidence.vue'
import ModelVariantsList, { type ActiveVariant } from '../components/models/ModelVariantsList.vue'
import ProfileSlider from '../components/models/ProfileSlider.vue'
import { buildModelStory, fastStop, keepUnits, prospectKm, rangeKm, type StoryInput } from '../utils/modelStory'
import type { PersonalRangeInputs } from '../api/personalRangeService'
import { analytics } from '../services/analytics'
import { useDrivingProfile, PROFILE_KEYS, PROFILE_SLIDERS, type ProfileKey } from '../composables/useDrivingProfile'
import RegionChip from '../components/shared/RegionChip.vue'
import ModelRatingsBlock from '../components/rating/ModelRatingsBlock.vue'
import { ratingService, type PublicModelRatings } from '../api/ratingService'
import { useRatingStore } from '../stores/rating'
import { buildProductJsonLd } from '../utils/ratingJsonLd'
import { useLocaleFormat } from '../composables/useLocaleFormat'
import { useMarketRoute, getMarketBasePath, OG_LOCALE, MARKET_HTML_LANG } from '../composables/useMarketRoute'
import { EUR_EXCHANGE_RATES, RATES_LAST_UPDATED } from '../config/exchangeRates'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const { formatConsumption, formatNumber, consumptionUnitLabel, convertConsumption, formatDistance, distanceUnitLabel, formatCurrency, formatCostPerKwh, formatCostPerDistance, isEurZone, currency, currencySymbol, unitSystem } = useLocaleFormat()
const { currentMarket, isDE, isEN, isGB, marketUrl, hreflangLinks } = useMarketRoute()
const modelsBaseUrl = computed(() => getMarketBasePath(currentMarket.value))
const authStore = useAuthStore()
const countryStore = useCountryStore()
const loading = ref(true)
const notFound = ref(false)
const apiError = ref(false)
const showBackPill = ref(false)
const stats = ref<PublicModelStats | null>(null)
/** Fahrerbewertungen, null bis geladen oder bei Fehler - die Seite funktioniert auch ohne. */
const ratings = ref<PublicModelRatings | null>(null)
/** Eigenes, bewertbares und unbewertetes Auto dieses Modells (nur eingeloggt). */
const ratingCtaCarId = ref<string | null>(null)
const selectedVariantIndex = ref(0)
const isAuthenticated = computed(() => authStore.isAuthenticated())

// Höchste Fahrzeugzahl eines Baujahrs - Basis für die Säulenhöhe im Histogramm
const maxYearCount = computed(() =>
  Math.max(1, ...(stats.value?.yearDistribution ?? []).map(e => e.carCount))
)
const yearBarHeightPx = (count: number) =>
  `${Math.max(4, Math.round(count / maxYearCount.value * 88))}px`
// Gewichteter Median des Baujahrs - das "typische" Baujahr der Community-Flotte
const medianModelYear = computed(() => {
  const dist = stats.value?.yearDistribution
  if (!dist || dist.length === 0) return null
  const sorted = [...dist].sort((a, b) => a.year - b.year)
  const total = sorted.reduce((s, e) => s + e.carCount, 0)
  let cum = 0
  for (const e of sorted) {
    cum += e.carCount
    if (cum >= total / 2) return e.year
  }
  return sorted[sorted.length - 1].year
})

const brand = route.params.brand as string
const model = route.params.model as string

const canonicalBrand = computed(() => stats.value?.brandDisplayName ?? brand)
// canonicalModelSlug unused in V2 (no redirect logic)

// US wenn: /us/models Route (previewCountry) ODER User hat dauerhaft US gesetzt
const isUS = computed(() => currentMarket.value === 'us' || (countryStore.previewCountry ?? countryStore.country) === 'US')
const loginPath = computed(() => (isEN.value || isGB.value || isUS.value) ? '/en/login' : '/login')
const registerPath = computed(() => (isEN.value || isGB.value || isUS.value) ? '/en/register' : '/register')

/** "ID.3" statt "Volkswagen ID.3" - für "ID.3-Fahrer". */
const shortModelName = computed(() => {
  if (!stats.value) return ''
  const full = stats.value.modelDisplayName
  const brandPrefix = `${stats.value.brandDisplayName} `
  return full.startsWith(brandPrefix) ? full.slice(brandPrefix.length) : full
})

async function loadRatingCta() {
  if (!authStore.isAuthenticated() || !stats.value) return
  try {
    const eligibility = await useRatingStore().getEligibility()
    ratingCtaCarId.value = eligibility.cars.find(c => c.eligible && !c.rated
      && c.brandName === stats.value!.brandDisplayName && c.modelName === shortModelName.value)?.carId ?? null
  } catch {
    // ohne Hinweis weiter
  }
}
const ratingLabel = computed(() =>
  isUS.value && (stats.value?.epaVariants?.length ?? 0) > 0 ? 'EPA' : 'WLTP'
)

// Reset variant selection when switching between EPA and WLTP (e.g. user changes Country)
watch(ratingLabel, () => { selectedVariantIndex.value = 0 })

const activeVariants = computed<ActiveVariant[]>(() => {
  if (!stats.value) return []
  if (isUS.value && stats.value.epaVariants?.length) {
    return stats.value.epaVariants.map(v => ({
      batteryCapacityKwh: v.batteryCapacityKwh,
      variantName: v.variantName,
      displayLabel: v.displayLabel,
      officialRangeKm: v.epaRangeKm,
      officialRangeMinKm: null,
      officialConsumptionKwhPer100km: v.epaConsumptionKwhPer100km,
      officialConsumptionMinKwhPer100km: v.epaConsumptionMinKwhPer100km,
      officialConsumptionMaxKwhPer100km: v.epaConsumptionMaxKwhPer100km,
      realConsumptionKwhPer100km: v.realConsumptionKwhPer100km,
      realConsumptionMinKwhPer100km: v.realConsumptionMinKwhPer100km,
      realConsumptionMaxKwhPer100km: v.realConsumptionMaxKwhPer100km,
      realConsumptionTripCount: v.realConsumptionTripCount,
      estimatedConsumptionCount: v.estimatedConsumptionCount,
      realConsumptionRangeSource: v.realConsumptionRangeSource,
      seasonalDistribution: v.seasonalDistribution,
    }))
  }
  return stats.value.wltpVariants.map(v => ({
    batteryCapacityKwh: v.batteryCapacityKwh,
    variantName: v.variantName,
    displayLabel: v.displayLabel,
    officialRangeKm: v.wltpRangeKm,
    officialRangeMinKm: v.wltpRangeMinKm,
    officialConsumptionKwhPer100km: v.wltpConsumptionKwhPer100km,
    officialConsumptionMinKwhPer100km: v.wltpConsumptionMinKwhPer100km,
    officialConsumptionMaxKwhPer100km: v.wltpConsumptionMaxKwhPer100km,
    realConsumptionKwhPer100km: v.realConsumptionKwhPer100km,
    realConsumptionMinKwhPer100km: v.realConsumptionMinKwhPer100km,
    realConsumptionMaxKwhPer100km: v.realConsumptionMaxKwhPer100km,
    realConsumptionTripCount: v.realConsumptionTripCount,
    estimatedConsumptionCount: v.estimatedConsumptionCount,
    realConsumptionRangeSource: v.realConsumptionRangeSource,
    seasonalDistribution: v.seasonalDistribution,
  }))
})

const selectedVariant = computed(() => activeVariants.value[selectedVariantIndex.value] ?? null)

// ── Story "Prospekt gegen Realität" ─────────────────────────────────────────────────────
// Model-level figures (the same the ranking and the backend calculator use) on the battery of
// the variant the reader picked. That battery also goes to the server, so the band and the
// page never disagree.

/** Ranking values for the story (fast-charge power, driver spread, fleet winter factor); null on failure. */
const storyContext = ref<ModelStoryContext | null>(null)
/** Same threshold as the backend: a season counts from 10 logs. */
const MIN_LOGS_PER_SEASON = 10
/** German daily petrol price (Tankerkönig), loaded with the page; null on failure */
const petrolPricePerLiter = ref<number | null>(null)
/** A comparable petrol car for the cost answer: litres by vehicle class, only where the fuel price is German */
const petrolComparison = computed(() => currentMarket.value === 'de' && petrolPricePerLiter.value
  ? {
      pricePerLiter: petrolPricePerLiter.value,
      // same vehicle class as the model (COMPACT, SUV, ...); unknown class: the fallback litres, no class name
      litersPer100km: combustionLiters({ litersPer100km: null, fuel: 'petrol' }, stats.value?.category),
      category: stats.value?.category && COMBUSTION_LITERS_BY_CLASS[stats.value.category] ? stats.value.categoryDisplayName ?? null : null,
    }
  : null)

/** One card per battery in the picker: the same range rule as the hero (net battery × 0,9 ÷ model mean) */
const variantOptions = computed((): VariantOption[] => activeVariants.value.map((v, i) => {
  const real = rangeKm(v.batteryCapacityKwh, stats.value?.avgConsumptionKwhPer100km ?? null)
  const wltp = real != null ? prospectKm(v.officialRangeKm)! : v.officialRangeKm
  return {
    key: `${i}-${v.displayLabel ?? v.batteryCapacityKwh}`,
    label: v.displayLabel || v.variantName || `${formatNumber(v.batteryCapacityKwh)} kWh`,
    battery: `${formatNumber(v.batteryCapacityKwh)} kWh`,
    range: real != null ? formatDistance(real) : null,
    prospect: t('model.page.variant_prospect', { range: formatDistance(wltp) }),
  }
}))

const variantLabel = computed(() => {
  const v = selectedVariant.value
  return v ? (v.displayLabel || v.variantName || `${v.batteryCapacityKwh} kWh`) : ''
})

/** Fleet winter factor from the backend, for models without winter data */
const fleetWinterFactor = computed(() => {
  const c = storyContext.value
  return c?.fleetWinterFactor != null ? { factor: c.fleetWinterFactor, models: c.fleetWinterModels } : null
})

const storyInput = computed((): StoryInput => {
  const st = stats.value!
  const v = selectedVariant.value
  const season = st.seasonalDistribution
  const seasonOk = season?.summerConsumptionKwhPer100km && season?.winterConsumptionKwhPer100km
    && season.summerLogCount >= MIN_LOGS_PER_SEASON && season.winterLogCount >= MIN_LOGS_PER_SEASON
  const rt = st.routeTypeConsumption
  return {
    modelName: st.modelDisplayName, brandName: st.brandDisplayName,
    logCount: st.logCount, carCount: st.uniqueCars, driverCount: st.uniqueContributors,
    netBatteryKwh: v?.batteryCapacityKwh ?? null,
    wltpKwhPer100km: v?.officialConsumptionKwhPer100km ?? worstOfficialConsumption.value,
    wltpRangeKm: v?.officialRangeKm ?? bestOfficialRange.value,
    avgKwhPer100km: st.avgConsumptionKwhPer100km,
    minKwhPer100km: storyContext.value?.minRealKwhPer100km ?? v?.realConsumptionMinKwhPer100km ?? null,
    maxKwhPer100km: storyContext.value?.maxRealKwhPer100km ?? v?.realConsumptionMaxKwhPer100km ?? null,
    routeType: rt ? { highway: rt.highwayKwhPer100km, city: rt.cityKwhPer100km, combined: rt.combinedKwhPer100km,
      highwayCars: rt.highwayCarCount, highwayLogs: rt.highwayLogCount, cityLogs: rt.cityLogCount, combinedLogs: rt.combinedLogCount, typedMean: rt.typedMeanKwhPer100km ?? null } : null,
    seasonal: seasonOk ? { summer: season.summerConsumptionKwhPer100km!, winter: season.winterConsumptionKwhPer100km!,
      summerLogs: season.summerLogCount, winterLogs: season.winterLogCount } : null,
    fastChargePowerKw: storyContext.value?.fastChargePowerKw ?? null,
    acPricePerKwh: st.acAvgCostPerKwh, dcPricePerKwh: st.dcAvgCostPerKwh, avgPricePerKwh: st.avgCostPerKwh,
    fleetWinterFactor: fleetWinterFactor.value,
  }
})

/** First sentence on its own line: "Der Prospekt sagt 434 km." then "48 Fahrer sagen 280 km." */
const heroLines = computed(() => {
  const text = keepUnits(story.value.heroSentence)
  const i = text.indexOf('. ')
  return i < 0 ? [text] : [text.slice(0, i + 1), text.slice(i + 2)]
})

/** Fast-charging stop 10 to 80 % at the DC price; the cost tile, SEO text and FAQ show the same figure */
const stopCost = computed(() => fastStop(selectedVariant.value?.batteryCapacityKwh ?? null,
  stats.value?.dcAvgCostPerKwh ?? stats.value?.avgCostPerKwh ?? null))

const story = computed(() => buildModelStory(storyInput.value, {
  t: (key, params) => t(key, params ?? {}),
  formatConsumption: v => formatConsumption(v),
  formatConsumptionValue: v => formatConsumption(v, { showUnit: false }),
  consumptionUnit: consumptionUnitLabel(),
  formatDistance: km => formatDistance(km),
  formatDistanceValue: km => formatDistance(km, { showUnit: false }),
  distanceUnit: distanceUnitLabel(),
  formatCostPerKwh,
  formatCostPerDistance,
  formatCurrency: v => formatCurrency(v, { decimals: 0 }),
  formatNumber,
}))

/** The reader's driving profile; the battery follows the variant switcher */
const { answers, answered, openQuestions, valueOf, answer: answerProfile, answerHome, applyProfile } =
  useDrivingProfile(route.query, () => stats.value?.modelDisplayName ?? model)
const storyInputs = computed((): PersonalRangeInputs => ({
  commuteKmPerDay: answers.commuteKmPerDay,
  commuteDaysPerWeek: answers.commuteDays,
  highwaySharePercent: answers.highwayShare,
  homeCharging: answers.homeCharging,
  longTripKm: answers.longTripKm,
  pricePerKwh: answers.pricePerKwh,
  annualKm: answers.annualKm,
  netBatteryKwh: selectedVariant.value?.batteryCapacityKwh ?? null,
}))

/** "30 %" on the slider, "4.500 km im Jahr" below it: the share in kilometres at the yearly mileage */
const highwayKm = (share: number) => formatDistance(Math.round(answers.annualKm * share / 100 / 100) * 100)
const FORMAT: Record<ProfileKey, (v: number) => string> = {
  annualKm: km => formatDistance(km),
  highwayShare: share => `${formatNumber(share)} %`,
  commute: km => formatDistance(km),
  commuteDays: n => t('model.page.days_value', { n }, n),
  longTrip: km => formatDistance(km),
}

const profileSliders = computed(() => PROFILE_KEYS.map(key => ({
  key,
  ...PROFILE_SLIDERS[key],
  label: t(`model.page.q_${key}`),
  value: answered.has(key) ? valueOf(key) : null,
  format: FORMAT[key],
  detail: key === 'highwayShare' ? (share: number) => t('model.story.slider_highway_km', { km: highwayKm(share) }) : undefined,
})))

function answer(key: ProfileKey, value: number) {
  answerProfile(key, value)
  if (highlightKey.value === key) highlightKey.value = null
}

/** The slider the reader is sent back to; it is marked until answered */
const highlightKey = ref<ProfileKey | null>(null)

/** Scrolls to the first open slider and puts the focus on it */
function showFirstOpen() {
  const key = openQuestions.value[0]
  if (!key) return
  highlightKey.value = key
  const block = document.getElementById(`story-${key}`)
  if (block) scrollToElement(block, { block: 'center' })
  block?.querySelector<HTMLElement>('input')?.focus({ preventScroll: true })
}

/** The question card in the hero, as a Plausible goal */
function trackJump() {
  analytics.track('model_profile_jump', { model: stats.value?.modelDisplayName ?? model })
}

const factsSub = computed(() => stats.value && stats.value.logCount > 0
  ? t('model.page.facts_sub', { logs: formatNumber(stats.value.logCount), cars: formatNumber(stats.value.uniqueCars) })
  : t('model.page.facts_sub_none'))

/** The year histogram says something from 3 years and 10 cars on */
const showYears = computed(() => {
  const dist = stats.value?.yearDistribution ?? []
  return dist.length >= 3 && dist.reduce((sum, e) => sum + e.carCount, 0) >= 10
})

/** Crawlable links to other model pages; the page itself is left out */
const RELATED = [
  ['Tesla/Model_3', 'Tesla Model 3'], ['Tesla/Model_Y', 'Tesla Model Y'], ['Volkswagen/ID.3', 'VW ID.3'],
  ['Volkswagen/ID.4', 'VW ID.4'], ['Hyundai/Ioniq_5', 'Hyundai Ioniq 5'], ['Hyundai/Ioniq_6', 'Hyundai Ioniq 6'],
  ['Kia/EV6', 'Kia EV6'], ['BMW/i4', 'BMW i4'], ['Audi/Q4_e-tron', 'Audi Q4 e-tron'],
] as const
const relatedModels = computed(() => RELATED
  .filter(([path]) => path.toLowerCase() !== `${canonicalBrand.value}/${model}`.toLowerCase())
  .map(([path, name]) => ({ path, name })))

const bestOfficialRange = computed(() => {
  if (!activeVariants.value.length) return null
  return Math.max(...activeVariants.value.map(v => v.officialRangeKm))
})

const wltpDeltaPercent = computed(() => {
  if (!stats.value?.avgConsumptionKwhPer100km || !worstOfficialConsumption.value) return null
  const real = stats.value.avgConsumptionKwhPer100km
  const official = worstOfficialConsumption.value
  // For mi/kWh: positive = more efficient (more miles per kWh = better)
  // For kWh/100km: negative = more efficient (less energy = better)
  const pct = unitSystem.value.consumptionInverse
    ? (official - real) / real * 100
    : (real - official) / official * 100
  const sign = pct > 0 ? '+' : ''
  return `${sign}${pct.toFixed(0)}%`
})

const worstOfficialConsumption = computed(() => {
  if (!activeVariants.value.length) return null
  const values = activeVariants.value.map(v => v.officialConsumptionKwhPer100km).filter((v): v is number => v !== null)
  return values.length ? Math.max(...values) : null
})

const consumptionDataCount = computed(() => {
  if (!stats.value) return 0
  const socCount = activeVariants.value.reduce((sum, v) => sum + (v.realConsumptionTripCount ?? 0), 0)
  return socCount + (stats.value.estimatedConsumptionCount ?? 0)
})

// ── SEO-Title-Werte: modell-level & deterministisch (unabhaengig vom Varianten-Switcher) ──
// Verbrauch = Modell-Schnitt (immer befuellt, sobald Community-Daten existieren). Bewusst
// NICHT displayConsumption: das ist variantenabhaengig und bei Multi-Varianten oft null.
const metaConsumption = computed(() => stats.value?.avgConsumptionKwhPer100km ?? null)

// Real-world-Reichweite je Variante (Akku x 0,9 / Real-Verbrauch, sonst Modell-Schnitt),
// als Min/Max-Span ueber alle Varianten - rundet auf 10 km wie die Hero-Anzeige.
const metaRealRangeLabel = computed(() => {
  const avg = metaConsumption.value
  if (!avg || !activeVariants.value.length) return null
  const ranges = activeVariants.value.map(v =>
    Math.round(v.batteryCapacityKwh * 0.9 / (v.realConsumptionKwhPer100km ?? avg) * 10) * 10
  )
  const unit = distanceUnitLabel()
  const minStr = formatDistance(Math.min(...ranges), { showUnit: false })
  const maxStr = formatDistance(Math.max(...ranges), { showUnit: false })
  return minStr === maxStr ? `${maxStr} ${unit}` : `${minStr}-${maxStr} ${unit}`
})

// WLTP-Range-Fallback fuer Modelle ohne Community-Verbrauch (immer aus Specs vorhanden).
const metaWltpRangeLabel = computed(() =>
  bestOfficialRange.value ? formatDistance(bestOfficialRange.value) : null
)

const consumptionDataQuality = computed((): 'good' | 'low' | 'scarce' => {
  const n = consumptionDataCount.value
  if (n >= 100) return 'good'
  if (n >= 50) return 'low'
  return 'scarce'
})

const faqItems = computed(() => {
  if (!stats.value) return []
  const name = stats.value.modelDisplayName
  const items: { question: string; answer: string }[] = []

  if (stats.value.avgConsumptionKwhPer100km) {
    const n = consumptionDataCount.value
    const quality = consumptionDataQuality.value
    const dataNote = quality === 'good'
      ? t('model.faq_consumption_data_good', { n })
      : quality === 'low'
        ? t('model.faq_consumption_data_low', { n })
        : t('model.faq_consumption_data_sparse', { n })
    items.push({
      question: t('model.faq_q_consumption', { model: name }),
      answer: t('model.faq_a_consumption', {
        model: name,
        consumption: formatConsumption(stats.value.avgConsumptionKwhPer100km),
        dataNote,
        wltp: worstOfficialConsumption.value ? formatConsumption(worstOfficialConsumption.value) : '-',
        ratingLabel: ratingLabel.value
      })
    })
  }

  if (storyInput.value.wltpRangeKm && story.value.realRangeKm != null && selectedVariant.value) {
    items.push({
      question: t('model.faq_q_range', { model: name }),
      answer: t('model.faq_a_range', {
        model: name,
        wltpRange: formatDistance(storyInput.value.wltpRangeKm),
        battery: selectedVariant.value.batteryCapacityKwh,
        consumption: formatConsumption(stats.value.avgConsumptionKwhPer100km),
        realRange: formatDistance(story.value.realRangeKm),
        ratingLabel: ratingLabel.value
      })
    })
  }

  // Same stop as the cost tile, plus the mean session for those who mostly top up at home
  if (stats.value.avgCostPerKwh && stopCost.value && stats.value.avgKwhPerSession) {
    items.push({
      question: t('model.faq_q_cost', { model: name }),
      answer: t('model.faq_a_cost', {
        price: formatCostPerKwh(stats.value.avgCostPerKwh),
        kwh: formatNumber(Math.round(stopCost.value.kwh)),
        cost: formatCurrency(stopCost.value.eur, { decimals: 0 }),
        sessionKwh: formatNumber(Math.round(stats.value.avgKwhPerSession)),
        sessionCost: formatCurrency(stats.value.avgCostPerKwh * stats.value.avgKwhPerSession, { decimals: 0 }),
      })
    })
  }

  if (worstOfficialConsumption.value && stats.value.avgConsumptionKwhPer100km && consumptionDataCount.value >= 25) {
    const real = stats.value.avgConsumptionKwhPer100km
    const official = worstOfficialConsumption.value
    // Compute diff and pct in display unit space
    const realDisplay = convertConsumption(real)
    const officialDisplay = convertConsumption(official)
    const diffVal = Math.abs(realDisplay - officialDisplay)
    const diffFormatted = `${diffVal.toFixed(1)} ${consumptionUnitLabel()}`
    const displayPct = unitSystem.value.consumptionInverse
      ? Math.round((official / real - 1) * 100)
      : Math.round((real / official - 1) * 100)
    const pctLabel = (displayPct > 0 ? '+' : '') + displayPct + '%'
    items.push({
      question: t('model.faq_q_wltp_delta', { model: name, ratingLabel: ratingLabel.value }),
      answer: t('model.faq_a_wltp_delta', {
        model: name,
        wltp: formatConsumption(official),
        real: formatConsumption(real),
        diff: diffFormatted,
        pct: pctLabel,
        ratingLabel: ratingLabel.value
      })
    })
  }

  const seasonal = selectedVariant.value?.seasonalDistribution
  const hasSeasonalData = seasonal && seasonal.winterLogCount >= 10 && seasonal.summerLogCount >= 10
  items.push({
    question: t('model.faq_q_winter', { model: name }),
    answer: hasSeasonalData
      ? t('model.faq_a_winter_data', {
          model: name,
          winter: seasonal!.winterConsumptionKwhPer100km ? formatConsumption(seasonal!.winterConsumptionKwhPer100km) : '-',
          summer: seasonal!.summerConsumptionKwhPer100km ? formatConsumption(seasonal!.summerConsumptionKwhPer100km) : '-'
        })
      : t('model.faq_a_winter_no_data', { model: name })
  })

  return items
})

const currentYear = new Date().getFullYear()

useHead(computed(() => {
  if (notFound.value) return { title: 'Modell nicht gefunden - EV Monitor', meta: [{ name: 'robots', content: 'noindex, nofollow' }] }
  if (!stats.value) return { title: 'EV Monitor', meta: [{ name: 'robots', content: 'noindex, follow' }] }

  const name = stats.value.modelDisplayName
  // Meta-Tags sind modell-level (nicht variantenabhaengig): sonst widerspricht die Description
  // dem Title bei Multi-Varianten, deren erste Variante keinen Realverbrauch hat (z.B. ID.4).
  const consumption = metaConsumption.value
  const wltp = worstOfficialConsumption.value

  const suffix = `/${canonicalBrand.value}/${model}`
  const canonicalUrl = marketUrl(currentMarket.value, suffix)

  const description = consumption && wltp
    ? t('model.meta_description_with_data', { model: name, consumption: formatConsumption(consumption), wltp: formatConsumption(wltp), ratingLabel: ratingLabel.value, logCount: formatNumber(stats.value.logCount) })
    : t('model.meta_description_no_data', { model: name, ratingLabel: ratingLabel.value })

  // SEO-Title: echte Zahlen (Verbrauch + Reichweite-Span) sind der CTR-Hebel im SERP -
  // kein WLTP-Konkurrent liefert gemessene Community-Werte. Modell-level, damit der Title
  // unabhaengig vom Varianten-Switcher immer befuellt ist. Fallback: WLTP-Range aus Specs.
  const title = metaConsumption.value && metaRealRangeLabel.value
    ? t('model.meta_title_with_data', { model: name, consumption: formatConsumption(metaConsumption.value), range: metaRealRangeLabel.value, year: currentYear })
    : metaWltpRangeLabel.value
      ? t('model.meta_title_wltp', { model: name, range: metaWltpRangeLabel.value, ratingLabel: ratingLabel.value, year: currentYear })
      : t('model.meta_title', { model: name, year: currentYear })

  const breadcrumbJsonLd = {
    '@context': 'https://schema.org',
    '@type': 'BreadcrumbList',
    itemListElement: [
      { '@type': 'ListItem', position: 1, name: 'EV Monitor', item: 'https://ev-monitor.net' },
      { '@type': 'ListItem', position: 2, name: isDE.value ? 'Elektroautos' : 'Electric Cars', item: marketUrl(currentMarket.value) },
      { '@type': 'ListItem', position: 3, name: stats.value.brandDisplayName, item: marketUrl(currentMarket.value, `/${canonicalBrand.value}`) },
      { '@type': 'ListItem', position: 4, name, item: canonicalUrl },
    ]
  }

  const webPageJsonLd: Record<string, unknown> = {
    '@context': 'https://schema.org',
    '@type': 'WebPage',
    name: title,
    description,
    url: canonicalUrl,
    author: { '@type': 'Organization', name: 'EV Monitor', url: 'https://ev-monitor.net' },
  }
  if (consumption) {
    webPageJsonLd['about'] = [
      { '@type': 'PropertyValue', name: isDE.value ? 'Realverbrauch' : 'Real Consumption', value: formatConsumption(consumption) },
      ...(wltp ? [{ '@type': 'PropertyValue', name: ratingLabel.value, value: formatConsumption(wltp) }] : []),
    ]
  }

  const faqJsonLd = {
    '@context': 'https://schema.org',
    '@type': 'FAQPage',
    mainEntity: faqItems.value.map(f => ({
      '@type': 'Question',
      name: f.question,
      acceptedAnswer: { '@type': 'Answer', text: f.answer }
    }))
  }

  const productJsonLd = buildProductJsonLd({
    name,
    brand: stats.value.brandDisplayName,
    url: canonicalUrl,
    driverLabel: t('rating.public.driver', { model: shortModelName.value }),
    ratings: ratings.value,
  })

  const htmlLang = MARKET_HTML_LANG[currentMarket.value]

  return {
    title,
    htmlAttrs: { lang: htmlLang },
    meta: [
      { name: 'description', content: description },
      { name: 'keywords', content: t('model.meta_keywords', { model: name }) },
      // threshold in sync with PublicModelService.SITEMAP_MIN_LOG_COUNT = 25
      { name: 'robots', content: consumptionDataCount.value >= 25 ? 'index, follow' : 'noindex, follow' },
      { property: 'og:title', content: title },
      { property: 'og:description', content: description },
      { property: 'og:type', content: 'article' },
      { property: 'og:url', content: canonicalUrl },
      { property: 'og:locale', content: OG_LOCALE[currentMarket.value] ?? 'en_GB' },
      { name: 'twitter:card', content: 'summary_large_image' },
      { name: 'twitter:title', content: title },
      { name: 'twitter:description', content: description },
    ],
    link: [
      { rel: 'canonical', href: canonicalUrl },
      ...hreflangLinks(suffix),
    ],
    script: [
      { type: 'application/ld+json', innerHTML: JSON.stringify(breadcrumbJsonLd) },
      { type: 'application/ld+json', innerHTML: JSON.stringify(webPageJsonLd) },
      ...(faqItems.value.length > 0
        ? [{ type: 'application/ld+json', innerHTML: JSON.stringify(faqJsonLd) }]
        : []),
      ...(productJsonLd ? [{ type: 'application/ld+json', innerHTML: JSON.stringify(productJsonLd) }] : []),
    ]
  }
}))

onMounted(async () => {
  if (sessionStorage.getItem('ev_from') === 'lp_v2') {
    showBackPill.value = true
    sessionStorage.removeItem('ev_from')
  }

  // Bewertungen parallel und im selben Lade-Flow, damit sie im Prerender-HTML stehen
  const ratingsRequest = ratingService.getPublicRatings(brand, model).catch(() => null)
  const contextRequest = getStoryContext(brand, model)
  // only for the petrol comparison in the result; the page works without it
  getChargingReferencePrices().then(p => { petrolPricePerLiter.value = p.petrolPricePerLiter ?? null }).catch(() => {})
  try {
    const [data, ratingData, context] = await Promise.all([getModelStats(brand, model), ratingsRequest, contextRequest])
    ratings.value = ratingData
    storyContext.value = context
    if (!data) {
      notFound.value = true
    } else {
      stats.value = data
      // Select variant with most real-world trips as default
      if (activeVariants.value.length > 1) {
        let maxTrips = -1
        activeVariants.value.forEach((v, i) => {
          if ((v.realConsumptionTripCount ?? 0) > maxTrips) {
            maxTrips = v.realConsumptionTripCount ?? 0
            selectedVariantIndex.value = i
          }
        })
      }
    }
  } catch {
    apiError.value = true
  } finally {
    loading.value = false
  }
  loadRatingCta()
})

function reload() { window.location.reload() }

function goBackToLpV2() {
  sessionStorage.setItem('ev_back_slide', '1')
  router.back()
}

</script>

<style scoped>
/* ── Typo-Skala der Modellseite ──────────────────────────────────────────
   Nur Größe/Gewicht/Tracking/Leading - Farbe bleibt per Tailwind-Utility.
   Ersetzt die zuvor 11 ad-hoc Größen durch ein konsistentes Set.        */
.t-value-lg { font-size: 1.5rem;   line-height: 1.6rem;  font-weight: 800; }

.model-page {
  animation: page-slide-in 0.28s cubic-bezier(0.25, 0.46, 0.45, 0.94) both;
}

@keyframes page-slide-in {
  from {
    opacity: 0;
    transform: translateX(28px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

.back-pill {
  transition: transform 0.1s, box-shadow 0.1s;
}
.back-pill:active {
  transform: translate(1px, 1px);
  box-shadow: none;
}
</style>
