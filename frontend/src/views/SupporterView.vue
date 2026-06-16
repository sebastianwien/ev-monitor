<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import {
  HeartIcon, ChartPieIcon, BoltIcon, ArrowTrendingUpIcon,
  SparklesIcon, CheckIcon, CodeBracketIcon, ShieldCheckIcon, ArrowLeftIcon,
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
</script>

<template>
  <div class="min-h-screen px-4 py-8 md:py-12">
    <div class="max-w-3xl mx-auto">
      <button
        type="button"
        @click="router.back()"
        class="inline-flex items-center gap-1.5 text-sm text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-200 transition-colors mb-6"
      >
        <ArrowLeftIcon class="w-4 h-4" />{{ t('supporter.back') }}
      </button>

      <!-- Hero -->
      <div class="text-center">
        <span class="inline-flex items-center justify-center w-14 h-14 rounded-2xl bg-amber-100 dark:bg-amber-500/15 mb-4">
          <HeartIcon class="w-7 h-7 text-amber-500 dark:text-amber-400" />
        </span>
        <h1 class="text-3xl md:text-4xl font-bold text-gray-900 dark:text-white tracking-tight">{{ t('supporter.title') }}</h1>
        <p class="mt-3 text-base md:text-lg text-gray-600 dark:text-gray-300 max-w-xl mx-auto">{{ t('supporter.subtitle') }}</p>
      </div>

      <!-- Pricing card -->
      <div class="mt-8 rounded-2xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 shadow-sm p-6 md:p-8">
        <div class="flex justify-center mb-6">
          <div class="inline-flex rounded-xl bg-gray-100 dark:bg-gray-700/60 p-1">
            <button
              type="button" @click="plan = 'monthly'"
              :class="plan === 'monthly' ? 'bg-white dark:bg-gray-800 text-gray-900 dark:text-white shadow-sm' : 'text-gray-500 dark:text-gray-400'"
              class="px-4 py-1.5 rounded-lg text-sm font-medium transition-colors"
            >{{ t('supporter.toggle_monthly') }}</button>
            <button
              type="button" @click="plan = 'yearly'"
              :class="plan === 'yearly' ? 'bg-white dark:bg-gray-800 text-gray-900 dark:text-white shadow-sm' : 'text-gray-500 dark:text-gray-400'"
              class="px-4 py-1.5 rounded-lg text-sm font-medium transition-colors inline-flex items-center gap-1.5"
            >
              {{ t('supporter.toggle_yearly') }}
              <span class="text-[10px] font-bold bg-amber-100 dark:bg-amber-500/20 text-amber-700 dark:text-amber-400 px-1.5 py-0.5 rounded">{{ t('supporter.toggle_savings') }}</span>
            </button>
          </div>
        </div>

        <div class="text-center">
          <p class="text-4xl font-bold text-gray-900 dark:text-white">
            {{ price }}<span class="text-lg font-normal text-gray-400 dark:text-gray-500"> {{ priceUnit }}</span>
          </p>
          <p class="text-xs text-gray-400 dark:text-gray-500 mt-1">{{ t('supporter.cancel_hint') }}</p>
        </div>

        <button
          type="button" @click="checkout" :disabled="loading"
          class="mt-6 w-full inline-flex items-center justify-center gap-2 rounded-xl bg-amber-500 hover:bg-amber-600 active:bg-amber-600 disabled:opacity-60 px-6 py-3.5 text-base font-semibold text-white shadow-sm hover:shadow transition-all"
        >
          <template v-if="loading">…</template>
          <template v-else><HeartIcon class="w-5 h-5" />{{ t('supporter.cta') }}</template>
        </button>
        <p v-if="error" class="mt-3 text-center text-sm text-red-600 dark:text-red-400">{{ error }}</p>
      </div>

      <!-- What you get -->
      <h2 class="mt-12 text-lg font-bold text-gray-900 dark:text-white">{{ t('supporter.get_title') }}</h2>
      <div class="mt-4 grid gap-3 sm:grid-cols-2">
        <div v-for="b in benefits" :key="b.title" class="flex items-start gap-3 rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 p-4">
          <component :is="b.icon" class="w-5 h-5 text-amber-500 dark:text-amber-400 flex-shrink-0 mt-0.5" />
          <div class="min-w-0">
            <p class="text-sm font-semibold text-gray-900 dark:text-white">{{ b.title }}</p>
            <p class="text-sm text-gray-500 dark:text-gray-400 mt-0.5">{{ b.desc }}</p>
          </div>
        </div>
      </div>

      <!-- Why support -->
      <h2 class="mt-12 text-lg font-bold text-gray-900 dark:text-white">{{ t('supporter.why_title') }}</h2>
      <div class="mt-4 space-y-3">
        <div v-for="r in reasons" :key="r.title" class="flex items-start gap-3">
          <component :is="r.icon" class="w-5 h-5 text-gray-400 dark:text-gray-500 flex-shrink-0 mt-0.5" />
          <div class="min-w-0">
            <p class="text-sm font-semibold text-gray-900 dark:text-white">{{ r.title }}</p>
            <p class="text-sm text-gray-500 dark:text-gray-400 mt-0.5">{{ r.desc }}</p>
          </div>
        </div>
      </div>

      <div class="mt-10 flex items-center justify-center gap-1.5 text-xs text-gray-400 dark:text-gray-500">
        <CheckIcon class="w-3.5 h-3.5" />{{ t('supporter.footer_note') }}
      </div>
    </div>
  </div>
</template>
