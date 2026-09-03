<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ListBulletIcon, HomeIcon, ChartPieIcon, BoltIcon, ArrowTrendingUpIcon } from '@heroicons/vue/24/outline'
import ChargingSavingsCard from './dashboard/ChargingSavingsCard.vue'
import DashboardInsights from './dashboard/DashboardInsights.vue'
import { useCountryStore } from '../stores/country'
import { useCarStore } from '../stores/car'
import { hasFreeDataSource } from '../composables/useCarAutoSyncProvider'
import { getPricing } from '../config/pricingConfig'
import { subscriptionService } from '../api/subscriptionService'

const { t } = useI18n()
const countryStore = useCountryStore()
const pricing = computed(() => getPricing(countryStore.country))

// Tesla-Fahrer kommen mit einer laufenden, kostenlosen Datenquelle hierher (Fleet
// Telemetry) - fuer sie ist AutoSync ueberfluessig, ihnen fehlt nur die Auswertung
// (Supporter). Der Hinweis-Kasten weist sie dorthin; alle anderen sehen ihn nicht.
const carStore = useCarStore()
const hasFreeSource = computed(() => carStore.cars.some(hasFreeDataSource))
onMounted(() => { carStore.getCars().catch(() => { /* Kasten bleibt aus */ }) })

const plan = ref<'monthly' | 'yearly'>('yearly')
const loading = ref(false)
const error = ref('')

const price = computed(() => plan.value === 'yearly' ? pricing.value.yearly : pricing.value.monthly)
const priceUnit = computed(() => plan.value === 'yearly' ? t('upgrade.tier_per_year') : t('upgrade.tier_autosync_price_unit'))

async function checkout() {
  loading.value = true
  error.value = ''
  try {
    const result = await subscriptionService.createCheckoutSession(plan.value)
    window.location.href = result.checkoutUrl
  } catch {
    error.value = t('autosync_page.error')
    loading.value = false
  }
}

const phantomImgOk = ref(true)
const curvesImgOk = ref(true)
const payments = ['Visa', 'Mastercard', 'Apple Pay', 'Google Pay', 'PayPal', 'Klarna']

const HOUR_MS = 3_600_000
function daysAgo(days: number, hour = 10): string {
  const dt = new Date()
  dt.setDate(dt.getDate() - days)
  dt.setHours(hour, 0, 0, 0)
  return dt.toISOString()
}
const dummyCar = { effectiveBatteryCapacityKwh: 75 }
const savingsPreview = {
  homeKwh: 839,
  homePricePerKwh: 0.289,
  homePriceBasis: 'OWN_LOGS' as const,
  publicPricePerKwh: 0.417,
  publicPriceBasis: 'OWN_PUBLIC' as const,
  publicPriceSampleSize: 18,
  actuallyPaidEur: 242.34,
  wouldHaveCostEur: 349.55,
  savingsEur: 107.21,
  investmentEur: 1000,
  firstYear: 2025,
  monthsOfUsage: 14,
  yearlySavings: [
    { year: 2025, homeKwh: 210, paidEur: 60.9, wouldHaveCostEur: 84.5, savingsEur: 23.6, cumulativeEur: 23.6 },
    { year: 2026, homeKwh: 839, paidEur: 242.34, wouldHaveCostEur: 349.55, savingsEur: 60.95, cumulativeEur: 84.55 },
  ],
  recoveredEur: 84.55,
  amortisationYearsRemaining: 8.5,
  fullyAmortised: false,
}
const dummyEntries = [
  { id: 'pv-c1', loggedAt: daysAgo(12), _isTrip: false, kwhCharged: 110, kwhAtVehicle: 99 },
  { id: 'pv-c2', loggedAt: daysAgo(7), _isTrip: false, kwhCharged: 110, kwhAtVehicle: 99, _phantomDrain: { kwh: 4, durationMs: 11 * HOUR_MS } },
  { id: 'pv-c3', loggedAt: daysAgo(2), _isTrip: false, kwhCharged: 110, kwhAtVehicle: 99, _phantomDrain: { kwh: 4, durationMs: 9 * HOUR_MS } },
]
</script>

<template>
  <div class="py-6 md:py-12 px-4">
    <div class="max-w-2xl mx-auto bg-gray-50/85 dark:bg-gray-900/75 backdrop-blur-md rounded-sm p-4 md:p-8 shadow-[5px_5px_0_rgba(0,0,0,0.35)] dark:shadow-[5px_5px_0_rgba(255,255,255,0.35)] shadow-black/5 dark:shadow-black/40">

      <!-- Hero -->
      <div class="text-center mb-8">
        <p class="text-[13px] font-semibold uppercase tracking-wider text-green-600 dark:text-green-400 mb-3">{{ t('autosync_page.kicker') }}</p>
        <h1 class="text-2xl md:text-4xl font-bold text-gray-900 dark:text-gray-100 mb-3" style="text-wrap:balance">{{ t('autosync_page.title') }}</h1>
        <p class="text-gray-600 dark:text-gray-300 text-lg md:text-xl leading-relaxed max-w-xl mx-auto">{{ t('autosync_page.lead') }}</p>
      </div>

      <!-- Story -->
      <div class="max-w-xl mx-auto space-y-4 text-base md:text-[17px] leading-relaxed text-gray-700 dark:text-gray-300">
        <p>{{ t('autosync_page.story_p1') }}</p>
        <p>{{ t('autosync_page.story_p2') }}</p>
        <p>{{ t('autosync_page.story_p3') }}</p>
      </div>

      <!-- Tesla -> Supporter -->
      <div
        v-if="hasFreeSource"
        class="max-w-xl mx-auto mt-6 rounded-sm border border-amber-200 dark:border-amber-700/40 bg-amber-50/70 dark:bg-amber-900/15 p-4 md:p-5 text-center"
      >
        <p class="text-[15px] leading-relaxed text-gray-700 dark:text-gray-300">{{ t('autosync_page.tesla_note_body') }}</p>
        <router-link to="/supporter" class="inline-block mt-2 text-[15px] font-semibold text-amber-600 dark:text-amber-400 hover:underline">{{ t('autosync_page.tesla_note_cta') }} &rarr;</router-link>
      </div>

      <!-- What AutoSync makes visible -->
      <div class="max-w-xl mx-auto mt-8">
        <p class="text-center text-base md:text-lg font-semibold text-gray-900 dark:text-gray-100 mb-5">{{ t('autosync_page.unlock_title') }}</p>

        <div class="space-y-8">
          <!-- Auto-capture -->
          <div>
            <div class="flex items-start gap-3 mb-3">
              <ListBulletIcon class="w-5 h-5 text-green-600 dark:text-green-400 flex-shrink-0 mt-0.5" />
              <div>
                <p class="text-base md:text-[17px] font-semibold text-gray-900 dark:text-gray-100">{{ t('autosync_page.b_auto_t') }}</p>
                <p class="text-[15px] leading-relaxed text-gray-600 dark:text-gray-300 mt-0.5">{{ t('autosync_page.b_auto_d') }}</p>
              </div>
            </div>
            <div class="rounded-sm border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-3.5">
              <div class="flex items-baseline justify-between gap-3">
                <span class="text-sm font-semibold text-gray-900 dark:text-gray-100">{{ t('autosync_page.demo_row_when') }}</span>
                <span class="text-sm font-bold text-green-600 dark:text-green-400 tabular-nums">+ 41,2 kWh</span>
              </div>
              <p class="text-[12px] text-gray-500 dark:text-gray-400 mt-1">{{ t('autosync_page.demo_row_meta') }}</p>
            </div>
          </div>

          <!-- Savings (Euro answer first) -->
          <div>
            <div class="flex items-start gap-3 mb-3">
              <HomeIcon class="w-5 h-5 text-green-600 dark:text-green-400 flex-shrink-0 mt-0.5" />
              <div>
                <p class="text-base md:text-[17px] font-semibold text-gray-900 dark:text-gray-100">{{ t('autosync_page.b_savings_t') }}</p>
                <p class="text-[15px] leading-relaxed text-gray-600 dark:text-gray-300 mt-0.5">{{ t('autosync_page.b_savings_d') }}</p>
              </div>
            </div>
            <ChargingSavingsCard :savings="savingsPreview" demo />
          </div>

          <!-- Energy split -->
          <div>
            <div class="flex items-start gap-3 mb-3">
              <ChartPieIcon class="w-5 h-5 text-green-600 dark:text-green-400 flex-shrink-0 mt-0.5" />
              <div>
                <p class="text-base md:text-[17px] font-semibold text-gray-900 dark:text-gray-100">{{ t('autosync_page.b_split_t') }}</p>
                <p class="text-[15px] leading-relaxed text-gray-600 dark:text-gray-300 mt-0.5">{{ t('autosync_page.b_split_d') }}</p>
              </div>
            </div>
            <DashboardInsights
              :entries="dummyEntries"
              :selected-car="dummyCar"
              selected-time-range="ALL"
              :custom-start-date="null"
              :custom-end-date="null"
              :avg-cost-per-kwh="0.30"
              preview-mode
            />
          </div>

          <!-- Idle drain -->
          <div>
            <div class="flex items-start gap-3 mb-3">
              <BoltIcon class="w-5 h-5 text-green-600 dark:text-green-400 flex-shrink-0 mt-0.5" />
              <div>
                <p class="text-base md:text-[17px] font-semibold text-gray-900 dark:text-gray-100">{{ t('autosync_page.b_idle_t') }}</p>
                <p class="text-[15px] leading-relaxed text-gray-600 dark:text-gray-300 mt-0.5">{{ t('autosync_page.b_idle_d') }}</p>
              </div>
            </div>
            <img
              v-show="phantomImgOk"
              :src="'/upgrade-previews/phantom-feed.png'"
              :alt="t('autosync_page.b_idle_t')"
              loading="lazy"
              class="w-full rounded-sm border border-gray-200 dark:border-gray-700"
              @error="phantomImgOk = false"
            />
          </div>

          <!-- Charging curves -->
          <div>
            <div class="flex items-start gap-3 mb-3">
              <ArrowTrendingUpIcon class="w-5 h-5 text-green-600 dark:text-green-400 flex-shrink-0 mt-0.5" />
              <div>
                <p class="text-base md:text-[17px] font-semibold text-gray-900 dark:text-gray-100">{{ t('autosync_page.b_curve_t') }}</p>
                <p class="text-[15px] leading-relaxed text-gray-600 dark:text-gray-300 mt-0.5">{{ t('autosync_page.b_curve_d') }}</p>
              </div>
            </div>
            <img
              v-show="curvesImgOk"
              :src="'/upgrade-previews/curves.png'"
              :alt="t('autosync_page.b_curve_t')"
              loading="lazy"
              class="w-full rounded-sm border border-gray-200 dark:border-gray-700"
              @error="curvesImgOk = false"
            />
          </div>
        </div>
      </div>

      <!-- Price + CTA -->
      <div class="max-w-md mx-auto mt-10 bg-white dark:bg-gray-900 rounded-sm border border-gray-200 dark:border-gray-700 shadow-sm dark:shadow-none p-6">
        <div class="flex justify-center mb-6">
          <div class="inline-flex bg-gray-100 dark:bg-gray-800 p-1 rounded-sm">
            <button
              @click="plan = 'monthly'"
              :class="plan === 'monthly' ? 'bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 shadow-sm' : 'text-gray-600 dark:text-gray-400'"
              class="px-4 py-2.5 rounded-sm text-[15px] font-medium transition-colors"
            >{{ t('upgrade.tier_toggle_monthly') }}</button>
            <button
              @click="plan = 'yearly'"
              :class="plan === 'yearly' ? 'bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 shadow-sm' : 'text-gray-600 dark:text-gray-400'"
              class="px-4 py-2.5 rounded-sm text-[15px] font-medium transition-colors flex items-center gap-1.5"
            >
              {{ t('upgrade.tier_toggle_yearly') }}
              <span class="text-[10px] bg-green-100 dark:bg-green-900/40 text-green-700 dark:text-green-400 px-1.5 py-0.5 rounded font-bold">{{ t('upgrade.tier_toggle_yearly_savings') }}</span>
            </button>
          </div>
        </div>

        <div class="text-center mb-5">
          <p class="text-4xl font-bold text-gray-900 dark:text-gray-100">{{ price }}<span class="text-lg font-normal text-gray-400 dark:text-gray-500"> {{ priceUnit }}</span></p>
          <p class="text-sm text-gray-400 dark:text-gray-500 mt-1.5">{{ t('autosync_page.cancel_hint') }}</p>
        </div>

        <button
          @click="checkout" :disabled="loading"
          class="w-full inline-flex items-center justify-center gap-2 bg-green-600 hover:bg-green-700 dark:bg-green-500 dark:hover:bg-green-400 disabled:bg-gray-300 dark:disabled:bg-gray-600 text-white dark:text-gray-900 font-semibold py-3.5 rounded-sm text-base shadow-[0_4px_0_0_#166534] dark:shadow-[0_4px_0_0_#064e3b] active:translate-y-1 active:shadow-none transition"
        >
          <template v-if="loading">…</template>
          <template v-else>{{ t('autosync_page.cta') }}</template>
        </button>
        <p v-if="error" class="text-sm text-red-600 dark:text-red-400 text-center mt-2">{{ error }}</p>
      </div>

      <!-- Trust + Payments -->
      <div class="mt-8 text-center">
        <p class="text-sm text-gray-400 dark:text-gray-500 mb-3">{{ t('autosync_page.footer_note') }}</p>
        <div class="flex flex-wrap justify-center gap-1.5">
          <span v-for="m in payments" :key="m" class="text-xs text-gray-600 dark:text-gray-400 border border-gray-300 dark:border-gray-600 rounded px-2.5 py-1">{{ m }}</span>
        </div>
      </div>
    </div>
  </div>
</template>
