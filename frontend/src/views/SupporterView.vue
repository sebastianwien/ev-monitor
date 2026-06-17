<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ChartPieIcon, BoltIcon, ArrowTrendingUpIcon, ArrowLeftIcon } from '@heroicons/vue/24/outline'
import { HeartIcon } from '@heroicons/vue/24/solid'
import { useCountryStore } from '../stores/country'
import { getPricing } from '../config/pricingConfig'
import { subscriptionService } from '../api/subscriptionService'
import DashboardInsights from '../components/dashboard/DashboardInsights.vue'

const { t } = useI18n()
const router = useRouter()
const countryStore = useCountryStore()
const pricing = computed(() => getPricing(countryStore.country))

const plan = ref<'monthly' | 'yearly'>('yearly')
const loading = ref(false)
const error = ref('')

const price = computed(() => plan.value === 'yearly' ? pricing.value.supporterYearly : pricing.value.supporterMonthly)
const priceUnit = computed(() => plan.value === 'yearly' ? t('supporter.per_year') : t('supporter.per_month'))

async function checkout() {
  loading.value = true
  error.value = ''
  try {
    const result = await subscriptionService.createCheckoutSession(plan.value, 'supporter')
    window.location.href = result.checkoutUrl
  } catch {
    error.value = t('supporter.error')
    loading.value = false
  }
}

// The phantom-feed screenshot is an optional asset; hide the image gracefully if missing.
const phantomImgOk = ref(true)

const payments = ['Visa', 'Mastercard', 'Apple Pay', 'Google Pay', 'PayPal', 'Klarna']

// Dummy data so every widget tab renders plausibly: charges drive the energy-split donut
// (~62% driving / 17% idle drain / 21% loss), the phantom drains feed "Standverluste", and
// the trips (dated within the last 2 weeks) feed the calendar and routes tabs.
const HOUR_MS = 3_600_000
function daysAgo(days: number, hour = 10): string {
  const dt = new Date()
  dt.setDate(dt.getDate() - days)
  dt.setHours(hour, 0, 0, 0)
  return dt.toISOString()
}

const dummyCar = { effectiveBatteryCapacityKwh: 75 }
const dummyEntries = [
  // Charges -> donut: 330 kWh charged, ~10% charging loss, ~2.4% idle drain (realistic)
  { id: 'pv-c1', loggedAt: daysAgo(12), _isTrip: false, kwhCharged: 110, kwhAtVehicle: 99 },
  { id: 'pv-c2', loggedAt: daysAgo(7), _isTrip: false, kwhCharged: 110, kwhAtVehicle: 99, _phantomDrain: { kwh: 4, durationMs: 11 * HOUR_MS } },
  { id: 'pv-c3', loggedAt: daysAgo(2), _isTrip: false, kwhCharged: 110, kwhAtVehicle: 99, _phantomDrain: { kwh: 4, durationMs: 9 * HOUR_MS } },
  // Trips -> calendar / routes / distance (recent dates, varied distances)
  { id: 'pv-t1', _isTrip: true, tripStartedAt: daysAgo(11, 8), distanceKm: 22, estimatedConsumedKwh: 4.0 },
  { id: 'pv-t2', _isTrip: true, tripStartedAt: daysAgo(9, 18), distanceKm: 6, estimatedConsumedKwh: 1.1 },
  { id: 'pv-t3', _isTrip: true, tripStartedAt: daysAgo(8, 9), distanceKm: 3, estimatedConsumedKwh: 0.6 },
  { id: 'pv-t4', _isTrip: true, tripStartedAt: daysAgo(6, 17), distanceKm: 35, estimatedConsumedKwh: 6.2 },
  { id: 'pv-t5', _isTrip: true, tripStartedAt: daysAgo(4, 8), distanceKm: 8, estimatedConsumedKwh: 1.5 },
  { id: 'pv-t6', _isTrip: true, tripStartedAt: daysAgo(3, 12), distanceKm: 15, estimatedConsumedKwh: 2.7 },
  { id: 'pv-t7', _isTrip: true, tripStartedAt: daysAgo(1, 19), distanceKm: 4, estimatedConsumedKwh: 0.8 },
  { id: 'pv-t8', _isTrip: true, tripStartedAt: daysAgo(0, 8), distanceKm: 11, estimatedConsumedKwh: 2.0 },
]
</script>

<template>
  <div class="py-6 md:py-12 px-4">
    <div class="max-w-2xl mx-auto bg-gray-50/85 dark:bg-gray-900/75 backdrop-blur-md rounded-sm p-4 md:p-8 shadow-[5px_5px_0_rgba(0,0,0,0.35)] dark:shadow-[5px_5px_0_rgba(255,255,255,0.35)] shadow-black/5 dark:shadow-black/40">
      <button
        type="button"
        @click="router.back()"
        class="inline-flex items-center gap-1.5 text-sm text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-200 transition-colors mb-6"
      >
        <ArrowLeftIcon class="w-4 h-4" />{{ t('supporter.back') }}
      </button>

      <!-- Hero: warm, not salesy -->
      <div class="text-center mb-8">
        <p class="text-xs font-semibold uppercase tracking-wider text-amber-600 dark:text-amber-400 mb-3">{{ t('supporter.hero_kicker') }}</p>
        <h1 class="text-2xl md:text-4xl font-bold text-gray-900 dark:text-gray-100 mb-3">{{ t('supporter.title') }}</h1>
        <p class="text-gray-600 dark:text-gray-300 text-base md:text-lg max-w-xl mx-auto">{{ t('supporter.lead') }}</p>
      </div>

      <!-- Story: the personal, honest narrative -->
      <div class="max-w-xl mx-auto space-y-4 text-[15px] leading-relaxed text-gray-700 dark:text-gray-300">
        <p>{{ t('supporter.story_p1') }}</p>
        <p>{{ t('supporter.story_p2') }}</p>
        <p>{{ t('supporter.story_p3') }}</p>
      </div>

      <!-- What supporting unlocks: each point above its visual -->
      <div class="max-w-xl mx-auto mt-8">
        <p class="text-sm font-semibold text-gray-900 dark:text-gray-100 mb-5">{{ t('supporter.unlock_title') }}</p>

        <div class="space-y-8">
          <!-- Energy split: the real dashboard widget, interactive -->
          <div>
            <div class="flex items-start gap-3 mb-3">
              <ChartPieIcon class="w-5 h-5 text-amber-500 dark:text-amber-400 flex-shrink-0 mt-0.5" />
              <span class="text-[15px] text-gray-700 dark:text-gray-300 leading-relaxed">{{ t('supporter.u_analytics') }}</span>
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

          <!-- Idle drain: the in-feed view -->
          <div>
            <div class="flex items-start gap-3 mb-3">
              <BoltIcon class="w-5 h-5 text-amber-500 dark:text-amber-400 flex-shrink-0 mt-0.5" />
              <span class="text-[15px] text-gray-700 dark:text-gray-300 leading-relaxed">{{ t('supporter.u_phantom') }}</span>
            </div>
            <img
              v-show="phantomImgOk"
              :src="'/upgrade-previews/phantom-feed.png'"
              :alt="t('supporter.u_phantom')"
              loading="lazy"
              class="w-full rounded-sm border border-gray-200 dark:border-gray-700"
              @error="phantomImgOk = false"
            />
          </div>

          <!-- Charging curves -->
          <div class="flex items-start gap-3">
            <ArrowTrendingUpIcon class="w-5 h-5 text-amber-500 dark:text-amber-400 flex-shrink-0 mt-0.5" />
            <span class="text-[15px] text-gray-700 dark:text-gray-300 leading-relaxed">{{ t('supporter.u_curves') }}</span>
          </div>
        </div>
      </div>

      <!-- Price + CTA: the natural conclusion, at the bottom -->
      <div class="max-w-md mx-auto mt-10 bg-white dark:bg-gray-900 rounded-sm border border-gray-200 dark:border-gray-700 shadow-sm dark:shadow-none p-6">
        <div class="flex justify-center mb-6">
          <div class="inline-flex bg-gray-100 dark:bg-gray-800 p-1 rounded-sm">
            <button
              @click="plan = 'monthly'"
              :class="plan === 'monthly' ? 'bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 shadow-sm' : 'text-gray-600 dark:text-gray-400'"
              class="px-4 py-2 rounded-sm text-sm font-medium transition-colors"
            >{{ t('supporter.toggle_monthly') }}</button>
            <button
              @click="plan = 'yearly'"
              :class="plan === 'yearly' ? 'bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 shadow-sm' : 'text-gray-600 dark:text-gray-400'"
              class="px-4 py-2 rounded-sm text-sm font-medium transition-colors flex items-center gap-1.5"
            >
              {{ t('supporter.toggle_yearly') }}
              <span class="text-[10px] bg-green-100 dark:bg-green-900/40 text-green-700 dark:text-green-400 px-1.5 py-0.5 rounded font-bold">{{ t('supporter.toggle_savings') }}</span>
            </button>
          </div>
        </div>

        <div class="text-center mb-5">
          <p class="text-4xl font-bold text-gray-900 dark:text-gray-100">{{ price }}<span class="text-lg font-normal text-gray-400 dark:text-gray-500"> {{ priceUnit }}</span></p>
          <p class="text-xs text-gray-400 dark:text-gray-500 mt-1">{{ t('supporter.cancel_hint') }}</p>
        </div>

        <button
          @click="checkout" :disabled="loading"
          class="w-full inline-flex items-center justify-center gap-2 bg-amber-500 hover:bg-amber-600 dark:bg-amber-500 dark:hover:bg-amber-400 disabled:bg-gray-300 dark:disabled:bg-gray-600 text-white font-semibold py-3 rounded-sm text-sm shadow-[0_4px_0_0_#b45309] dark:shadow-[0_4px_0_0_#92400e] active:translate-y-1 active:shadow-none transition"
        >
          <template v-if="loading">…</template>
          <template v-else><HeartIcon class="w-4 h-4" />{{ t('supporter.cta') }}</template>
        </button>
        <p v-if="error" class="text-xs text-red-600 dark:text-red-400 text-center mt-2">{{ error }}</p>
      </div>

      <!-- Trust + Payments -->
      <div class="mt-8 text-center">
        <p class="text-xs text-gray-400 dark:text-gray-500 mb-3">{{ t('supporter.footer_note') }}</p>
        <div class="flex flex-wrap justify-center gap-1.5">
          <span v-for="m in payments" :key="m" class="text-[11px] text-gray-600 dark:text-gray-400 border border-gray-300 dark:border-gray-600 rounded px-2 py-0.5">{{ m }}</span>
        </div>
      </div>
    </div>
  </div>
</template>
