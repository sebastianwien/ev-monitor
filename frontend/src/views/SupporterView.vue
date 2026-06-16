<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import {
  HeartIcon, ChartPieIcon, BoltIcon, ArrowTrendingUpIcon,
  SparklesIcon, CodeBracketIcon, ShieldCheckIcon, ArrowLeftIcon,
} from '@heroicons/vue/24/outline'
import { useCountryStore } from '../stores/country'
import { getPricing } from '../config/pricingConfig'
import { subscriptionService } from '../api/subscriptionService'

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

const benefits = computed(() => [
  { icon: ChartPieIcon, title: t('supporter.b_analytics_title'), desc: t('supporter.b_analytics_desc') },
  { icon: BoltIcon, title: t('supporter.b_phantom_title'), desc: t('supporter.b_phantom_desc') },
  { icon: ArrowTrendingUpIcon, title: t('supporter.b_curves_title'), desc: t('supporter.b_curves_desc') },
  { icon: SparklesIcon, title: t('supporter.b_badge_title'), desc: t('supporter.b_badge_desc') },
])

const reasons = computed(() => [
  { icon: CodeBracketIcon, title: t('supporter.why_oss_title'), desc: t('supporter.why_oss_desc') },
  { icon: ShieldCheckIcon, title: t('supporter.why_independent_title'), desc: t('supporter.why_independent_desc') },
  { icon: HeartIcon, title: t('supporter.why_solo_title'), desc: t('supporter.why_solo_desc') },
])

const payments = ['Visa', 'Mastercard', 'Apple Pay', 'Google Pay', 'PayPal', 'Klarna']
</script>

<template>
  <div class="py-6 md:py-12 px-4">
    <div class="max-w-2xl mx-auto bg-gray-50/85 dark:bg-gray-900/75 backdrop-blur-md rounded-sm p-4 md:p-8 shadow-[5px_5px_0_rgba(0,0,0,0.35)] dark:shadow-[5px_5px_0_rgba(255,255,255,0.35)] shadow-black/5 dark:shadow-black/40">
      <button
        type="button"
        @click="router.back()"
        class="inline-flex items-center gap-1.5 text-sm text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-200 transition-colors mb-4"
      >
        <ArrowLeftIcon class="w-4 h-4" />{{ t('supporter.back') }}
      </button>

      <!-- Headline -->
      <div class="text-center mb-8">
        <span class="inline-flex items-center gap-1 text-[11px] font-bold bg-amber-500 text-white px-3 py-1 rounded-full tracking-wide mb-3">
          <HeartIcon class="w-3 h-3" /> SUPPORTER
        </span>
        <h1 class="text-2xl md:text-4xl font-bold text-gray-900 dark:text-gray-100 mb-2">{{ t('supporter.title') }}</h1>
        <p class="text-gray-500 dark:text-gray-400 text-sm md:text-base max-w-xl mx-auto">{{ t('supporter.subtitle') }}</p>
      </div>

      <!-- Pricing card -->
      <div class="max-w-md mx-auto bg-white dark:bg-gray-900 rounded-sm border border-gray-200 dark:border-gray-700 shadow-sm dark:shadow-none p-6">
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

      <!-- What you get -->
      <h2 class="mt-10 text-center text-lg font-bold text-gray-900 dark:text-gray-100">{{ t('supporter.get_title') }}</h2>
      <div class="mt-4 grid gap-3 sm:grid-cols-2">
        <div v-for="b in benefits" :key="b.title" class="flex items-start gap-3 bg-white dark:bg-gray-900 rounded-sm border border-gray-200 dark:border-gray-700 shadow-sm dark:shadow-none p-4">
          <component :is="b.icon" class="w-5 h-5 text-amber-500 dark:text-amber-400 flex-shrink-0 mt-0.5" />
          <div class="min-w-0">
            <p class="text-sm font-semibold text-gray-900 dark:text-gray-100">{{ b.title }}</p>
            <p class="text-sm text-gray-500 dark:text-gray-400 mt-0.5">{{ b.desc }}</p>
          </div>
        </div>
      </div>

      <!-- Why support -->
      <h2 class="mt-10 text-center text-lg font-bold text-gray-900 dark:text-gray-100">{{ t('supporter.why_title') }}</h2>
      <div class="mt-4 max-w-xl mx-auto space-y-3">
        <div v-for="r in reasons" :key="r.title" class="flex items-start gap-3">
          <component :is="r.icon" class="w-5 h-5 text-gray-400 dark:text-gray-500 flex-shrink-0 mt-0.5" />
          <div class="min-w-0">
            <p class="text-sm font-semibold text-gray-900 dark:text-gray-100">{{ r.title }}</p>
            <p class="text-sm text-gray-500 dark:text-gray-400 mt-0.5">{{ r.desc }}</p>
          </div>
        </div>
      </div>

      <!-- Trust + Payments -->
      <div class="mt-10 text-center">
        <p class="text-xs text-gray-400 dark:text-gray-500 mb-3">{{ t('supporter.footer_note') }}</p>
        <div class="flex flex-wrap justify-center gap-1.5">
          <span v-for="m in payments" :key="m" class="text-[11px] text-gray-600 dark:text-gray-400 border border-gray-300 dark:border-gray-600 rounded px-2 py-0.5">{{ m }}</span>
        </div>
      </div>
    </div>
  </div>
</template>
