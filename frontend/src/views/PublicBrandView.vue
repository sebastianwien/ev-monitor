<template>
  <div class="min-h-screen bg-gray-50 dark:bg-gray-950">
    <PublicNav />

    <main class="max-w-4xl mx-auto md:px-4 py-6 md:py-8">
      <!-- Loading state -->
      <div v-if="loading" class="flex items-center justify-center py-20">
        <div class="animate-spin rounded-full h-10 w-10 border-b-2 border-green-600"></div>
      </div>

      <!-- 404 state -->
      <div v-else-if="notFound" class="text-center py-20">
        <h1 class="text-2xl font-bold text-gray-800 dark:text-gray-200 mb-2">{{ t('brand.not_found_title') }}</h1>
        <p class="text-gray-500 dark:text-gray-400 mb-6">{{ t('brand.not_found_text') }}</p>
        <a :href="modelsUrl" class="bg-green-600 text-white px-4 py-2 rounded-lg hover:bg-green-700">
          {{ t('common.all_models') }}
        </a>
      </div>

      <!-- API error state (transient) — no noindex -->
      <div v-else-if="apiError" class="text-center py-20">
        <h1 class="text-2xl font-bold text-gray-800 dark:text-gray-200 mb-2">{{ t('brand.error_title') }}</h1>
        <p class="text-gray-500 dark:text-gray-400 mb-6">{{ t('brand.error_text') }}</p>
        <a :href="modelsUrl" class="bg-green-600 text-white px-4 py-2 rounded-lg hover:bg-green-700">
          {{ t('common.all_models') }}
        </a>
      </div>

      <!-- Brand page -->
      <div v-else-if="brand">
        <!-- Breadcrumb -->
        <nav class="px-4 md:px-0 text-sm text-gray-500 dark:text-gray-400 mb-4">
          <a href="/" class="hover:text-gray-700 dark:hover:text-gray-200">EV Monitor</a>
          <span class="mx-2">›</span>
          <a :href="modelsUrl" class="hover:text-gray-700 dark:hover:text-gray-200">{{ t('brand.breadcrumb_models') }}</a>
          <span class="mx-2">›</span>
          <span class="text-gray-900 dark:text-gray-100">{{ brand.brandDisplayName }}</span>
        </nav>

        <!-- Hero -->
        <div class="bg-white dark:bg-gray-800 md:rounded-2xl md:border-x border-t md:border-b border-gray-200 dark:border-gray-700 p-6 mb-6">
          <h1 class="text-3xl font-bold text-gray-900 dark:text-gray-100 mb-2">
            {{ t('brand.hero_title', { brand: brand.brandDisplayName }) }}
          </h1>
          <p class="text-gray-600 dark:text-gray-400 text-lg">
            {{ t('brand.hero_subtitle', { brand: brand.brandDisplayName }) }}
          </p>
          <div class="flex gap-4 mt-4 text-sm text-gray-500 dark:text-gray-400">
            <span>{{ t('brand.models_count', { count: brand.models.length }) }}</span>
            <span>·</span>
            <span>{{ t('brand.with_data', { count: modelsWithData.length }) }}</span>
            <span>·</span>
            <span>{{ t('brand.total_logs', { count: formatNumber(totalLogs) }) }}</span>
          </div>
        </div>

        <!-- Model Grid -->
        <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-0 sm:gap-4 px-0 sm:px-0">
          <a
            v-for="model in brand.models"
            :key="model.modelEnum"
            :href="`${modelsUrl}/${brand.brandDisplayName}/${model.modelUrlSlug}`"
            class="bg-white dark:bg-gray-800 sm:rounded-2xl border-t sm:border border-l-4 border-r-4 border-l-green-500 border-r-green-500 border-gray-200 dark:border-gray-700 p-5 hover:border-green-400 hover:shadow-md transition-all group"
          >
            <!-- Model name & log badge -->
            <div class="flex items-center justify-between mb-3">
              <h2 class="text-lg font-bold text-gray-900 dark:text-gray-100 group-hover:text-green-700 transition-colors flex items-center gap-1">
                {{ model.modelDisplayName }}
                <ChevronRightIcon class="h-4 w-4 text-gray-400 group-hover:text-green-600 transition-colors" />
              </h2>
              <span
                v-if="model.logCount > 0"
                class="text-xs px-2 py-0.5 rounded-full bg-green-50 text-green-700 border border-green-200 dark:bg-green-900/40 dark:text-green-400 dark:border-green-700 whitespace-nowrap ml-2"
              >
                {{ formatNumber(model.logCount) }} Logs
              </span>
              <span
                v-else
                class="text-xs px-2 py-0.5 rounded-full bg-gray-50 dark:bg-gray-700 text-gray-400 border border-gray-200 dark:border-gray-600 whitespace-nowrap ml-2"
              >
                {{ t('brand.be_first') }}
              </span>
            </div>

            <!-- Variant table: one row per battery size -->
            <div v-if="model.wltpVariants.length > 0">
              <!-- Header -->
              <div class="grid grid-cols-3 text-xs text-gray-400 dark:text-gray-500 px-1 pb-1">
                <span>{{ t('brand.table_battery') }}</span>
                <span class="text-center">{{ t('brand.table_wltp') }}</span>
                <span class="text-center">{{ t('brand.table_real') }}</span>
              </div>
              <!-- Rows -->
              <div class="space-y-1">
                <div
                  v-for="v in model.wltpVariants"
                  :key="v.batteryCapacityKwh"
                  class="grid grid-cols-3 text-sm bg-gray-50 dark:bg-gray-700 rounded-lg px-2 py-1.5 items-center"
                >
                  <span class="text-gray-600 dark:text-gray-400 font-medium">{{ v.batteryCapacityKwh }} kWh</span>
                  <span class="text-center text-gray-700 dark:text-gray-300">
                    {{ v.wltpRangeKm ? v.wltpRangeKm + ' km' : '–' }}
                  </span>
                  <span
                    :class="v.realConsumptionKwhPer100km ? 'text-purple-700 font-semibold' : 'text-gray-400'"
                    class="text-center"
                  >
                    {{ v.realConsumptionKwhPer100km
                      ? '~ ' + Math.round(v.batteryCapacityKwh / v.realConsumptionKwhPer100km * 10) * 10 + ' km'
                      : '–' }}
                  </span>
                </div>
              </div>
            </div>

            <!-- No WLTP data at all -->
            <div v-else class="text-xs text-gray-400 dark:text-gray-500 text-center py-3">
              {{ t('brand.no_specs') }}
            </div>

            <div class="flex justify-end mt-3">
              <span class="text-xs text-green-600 font-medium group-hover:underline">{{ t('common.details') }}</span>
            </div>
          </a>
        </div>

        <!-- SEO text section -->
        <div class="bg-white dark:bg-gray-800 md:rounded-2xl md:border-x border-t md:border-b border-gray-200 dark:border-gray-700 p-6 mt-6">
          <h2 class="text-xl font-bold text-gray-900 dark:text-gray-100 mb-3">
            {{ t('brand.seo_title', { brand: brand.brandDisplayName }) }}
          </h2>
          <p class="text-sm text-gray-600 dark:text-gray-400 leading-relaxed">
            {{ t('brand.seo_text', { brand: brand.brandDisplayName }) }}
            <template v-if="modelsWithData.length > 0">
              {{ t('brand.seo_data_count', {
                count: modelsWithData.length,
                noun: modelsWithData.length === 1 ? t('brand.seo_model_singular') : t('brand.seo_model_plural')
              }) }}
            </template>
            {{ t('brand.seo_contribute', { brand: brand.brandDisplayName }) }}
          </p>
        </div>

        <!-- CTA -->
        <div class="bg-gradient-to-br from-green-600 to-green-700 md:rounded-2xl p-6 text-white mt-6">
          <div class="flex items-center gap-2 mb-2">
            <ArrowTrendingUpIcon class="h-6 w-6" />
            <h2 class="text-xl font-bold">{{ t('brand.cta_title', { brand: brand.brandDisplayName }) }}</h2>
          </div>
          <p class="text-green-100 mb-4">{{ t('brand.cta_text') }}</p>
          <div class="flex flex-wrap gap-3">
            <a :href="registerPath"
               class="bg-white text-green-700 font-semibold px-4 py-2 rounded-lg hover:bg-green-50 transition-colors">
              {{ t('brand.free_start') }}
            </a>
            <a :href="loginPath"
               class="border border-white text-white px-4 py-2 rounded-lg hover:bg-green-600 transition-colors">
              {{ t('brand.login') }}
            </a>
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useHead } from '@unhead/vue'
import { useI18n } from 'vue-i18n'
import { getBrandModels, type PublicBrandResponse } from '../api/publicModelService'
import { ArrowTrendingUpIcon, ChevronRightIcon } from '@heroicons/vue/24/outline'
import PublicNav from '../components/shared/PublicNav.vue'
import { useLocaleFormat, useLocaleRoutes } from '../composables/useLocaleFormat'

const { t } = useI18n()
const { formatNumber } = useLocaleFormat()
const { getCanonicalBase } = useLocaleRoutes()

const route = useRoute()
const router = useRouter()
const loading = ref(true)
const notFound = ref(false)
const apiError = ref(false)
const brand = ref<PublicBrandResponse | null>(null)

const isEn = computed(() => route.path.startsWith('/en/'))
const modelsUrl = computed(() => isEn.value ? '/en/models' : '/modelle')
const loginPath = computed(() => isEn.value ? '/en/login' : '/login')
const registerPath = computed(() => isEn.value ? '/en/register' : '/register')

const modelsWithData = computed(() => brand.value?.models.filter(m => m.logCount > 0) ?? [])
const totalLogs = computed(() => brand.value?.models.reduce((sum, m) => sum + m.logCount, 0) ?? 0)

onMounted(async () => {
  const brandParam = route.params.brand as string
  try {
    const result = await getBrandModels(brandParam)
    if (!result) {
      notFound.value = true
    } else {
      brand.value = result
      const canonicalPath = isEn.value
        ? `/en/models/${result.brandDisplayName}`
        : `/modelle/${result.brandDisplayName}`
      if (route.path !== canonicalPath) {
        router.replace(canonicalPath)
      }
    }
  } catch {
    apiError.value = true
  } finally {
    loading.value = false
  }
})

useHead(computed(() => {
  if (notFound.value) {
    return {
      title: 'Brand not found – EV Monitor',
      meta: [{ name: 'robots', content: 'noindex, nofollow' }]
    }
  }
  if (!brand.value) {
    const brandParam = route.params.brand as string
    const canonical = isEn.value
      ? `${getCanonicalBase()}/en/models/${brandParam}`
      : `${getCanonicalBase()}/modelle/${brandParam}`
    return {
      title: 'EV Monitor',
      meta: [{ name: 'robots', content: 'index, follow' }],
      link: [{ rel: 'canonical', href: canonical }]
    }
  }

  const name = brand.value.brandDisplayName
  const modelCount = brand.value.models.length
  const base = getCanonicalBase()
  const canonicalUrl = isEn.value ? `${base}/en/models/${name}` : `${base}/modelle/${name}`
  const deUrl = `${base}/modelle/${name}`
  const enUrl = `${base}/en/models/${name}`
  const currentYear = new Date().getFullYear()

  const description = isEn.value
    ? `All ${modelCount} ${name} electric cars at a glance (${currentYear}): WLTP range, real consumption and community data. Find out how much a ${name} really consumes.`
    : `Alle ${modelCount} ${name} Elektroautos im Überblick (${currentYear}): WLTP-Reichweite, realer Verbrauch und Community-Daten. Finde heraus wie viel ein ${name} wirklich verbraucht.`

  const keywords = isEn.value
    ? [`${name} electric car`, `${name} consumption`, `${name} WLTP`, `${name} range`, `${name} EV`, `${name} kWh`, 'electric car consumption', 'WLTP vs real', 'EV range'].join(', ')
    : [`${name} Elektroauto`, `${name} Verbrauch`, `${name} WLTP`, `${name} Reichweite`, `${name} E-Auto`, `${name} kWh`, `${name} Elektromodelle`, 'Elektroauto Verbrauch', 'WLTP Unterschied real', 'Elektroauto Reichweite'].join(', ')

  const breadcrumbJsonLd = {
    '@context': 'https://schema.org',
    '@type': 'BreadcrumbList',
    itemListElement: [
      { '@type': 'ListItem', position: 1, name: 'EV Monitor', item: base },
      { '@type': 'ListItem', position: 2, name: isEn.value ? 'Models' : 'Modelle', item: isEn.value ? `${base}/en/models` : `${base}/modelle` },
      { '@type': 'ListItem', position: 3, name: `${name} ${isEn.value ? 'Electric Cars' : 'Elektroautos'}`, item: canonicalUrl },
    ]
  }

  const itemListJsonLd = {
    '@context': 'https://schema.org',
    '@type': 'ItemList',
    name: `${name} ${isEn.value ? 'Electric Cars' : 'Elektroautos'} – EV Monitor`,
    description,
    itemListElement: brand.value.models.map((model, idx) => ({
      '@type': 'ListItem',
      position: idx + 1,
      name: `${name} ${model.modelDisplayName}`,
      url: `${canonicalUrl}/${model.modelUrlSlug}`
    }))
  }

  return {
    title: isEn.value
      ? `${name} Electric Cars – Consumption & WLTP (${currentYear}) | EV Monitor`
      : `${name} Elektroautos – Verbrauch & WLTP (${currentYear}) | EV Monitor`,
    meta: [
      { name: 'description', content: description },
      { name: 'keywords', content: keywords },
      { name: 'robots', content: 'index, follow' },
      { property: 'og:title', content: isEn.value ? `${name} Electric Cars – Real Consumption & WLTP ${currentYear}` : `${name} Elektroautos – Realer Verbrauch & WLTP ${currentYear}` },
      { property: 'og:description', content: description },
      { property: 'og:type', content: 'website' },
      { property: 'og:url', content: canonicalUrl },
      { property: 'og:locale', content: isEn.value ? 'en_GB' : 'de_DE' },
    ],
    link: [
      { rel: 'canonical', href: canonicalUrl },
      { rel: 'alternate', hreflang: 'de', href: deUrl },
      { rel: 'alternate', hreflang: 'en', href: enUrl },
      { rel: 'alternate', hreflang: 'x-default', href: enUrl },
    ],
    script: [
      { type: 'application/ld+json', innerHTML: JSON.stringify(breadcrumbJsonLd) },
      { type: 'application/ld+json', innerHTML: JSON.stringify(itemListJsonLd) },
    ]
  }
}))
</script>
