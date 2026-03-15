<template>
  <div class="min-h-screen bg-gray-50">
    <PublicNav />

    <main class="max-w-4xl mx-auto md:px-4 py-6 md:py-8">
      <!-- Loading state -->
      <div v-if="loading" class="flex items-center justify-center py-20">
        <div class="animate-spin rounded-full h-10 w-10 border-b-2 border-green-600"></div>
      </div>

      <!-- 404 state -->
      <div v-else-if="notFound" class="text-center py-20">
        <h1 class="text-2xl font-bold text-gray-800 mb-2">Marke nicht gefunden</h1>
        <p class="text-gray-500 mb-6">Diese Marke kennen wir leider nicht.</p>
        <a href="/modelle" class="bg-green-600 text-white px-4 py-2 rounded-lg hover:bg-green-700">
          Alle Modelle ansehen
        </a>
      </div>

      <!-- API error state (transient) — no noindex -->
      <div v-else-if="apiError" class="text-center py-20">
        <h1 class="text-2xl font-bold text-gray-800 mb-2">Daten konnten nicht geladen werden</h1>
        <p class="text-gray-500 mb-6">Bitte versuche es in ein paar Sekunden erneut.</p>
        <a href="/modelle" class="bg-green-600 text-white px-4 py-2 rounded-lg hover:bg-green-700">
          Alle Modelle ansehen
        </a>
      </div>

      <!-- Brand page -->
      <div v-else-if="brand">
        <!-- Breadcrumb -->
        <nav class="px-4 md:px-0 text-sm text-gray-500 mb-4">
          <a href="/" class="hover:text-gray-700">EV Monitor</a>
          <span class="mx-2">›</span>
          <a href="/modelle" class="hover:text-gray-700">Modelle</a>
          <span class="mx-2">›</span>
          <span class="text-gray-900">{{ brand.brandDisplayName }}</span>
        </nav>

        <!-- Hero -->
        <div class="bg-white md:rounded-2xl md:border-x border-t md:border-b border-gray-200 p-6 mb-6">
          <h1 class="text-3xl font-bold text-gray-900 mb-2">
            {{ brand.brandDisplayName }} Elektroautos – Verbrauch & WLTP Vergleich
          </h1>
          <p class="text-gray-600 text-lg">
            Alle {{ brand.brandDisplayName }} E-Modelle im Überblick: WLTP-Reichweite und reale Verbrauchsdaten der Community.
          </p>
          <div class="flex gap-4 mt-4 text-sm text-gray-500">
            <span>{{ brand.models.length }} Modelle</span>
            <span>·</span>
            <span>{{ modelsWithData.length }} mit Community-Daten</span>
            <span>·</span>
            <span>{{ totalLogs.toLocaleString('de-DE') }} Ladevorgänge</span>
          </div>
        </div>

        <!-- Model Grid -->
        <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-0 sm:gap-4 px-0 sm:px-0">
          <a
            v-for="model in brand.models"
            :key="model.modelEnum"
            :href="`/modelle/${brand.brandDisplayName}/${model.modelUrlSlug}`"
            class="bg-white sm:rounded-2xl border-t sm:border border-l-4 border-r-4 border-l-green-500 border-r-green-500 border-gray-200 p-5 hover:border-green-400 hover:shadow-md transition-all group"
          >
            <!-- Model name & log badge -->
            <div class="flex items-center justify-between mb-3">
              <h2 class="text-lg font-bold text-gray-900 group-hover:text-green-700 transition-colors flex items-center gap-1">
                {{ model.modelDisplayName }}
                <ChevronRightIcon class="h-4 w-4 text-gray-400 group-hover:text-green-600 transition-colors" />
              </h2>
              <span
                v-if="model.logCount > 0"
                class="text-xs px-2 py-0.5 rounded-full bg-green-50 text-green-700 border border-green-200 whitespace-nowrap ml-2"
              >
                {{ model.logCount.toLocaleString('de-DE') }} Logs
              </span>
              <span
                v-else
                class="text-xs px-2 py-0.5 rounded-full bg-gray-50 text-gray-400 border border-gray-200 whitespace-nowrap ml-2"
              >
                Sei der Erste
              </span>
            </div>

            <!-- Variant table: one row per battery size -->
            <div v-if="model.wltpVariants.length > 0">
              <!-- Header -->
              <div class="grid grid-cols-3 text-xs text-gray-400 px-1 pb-1">
                <span>Akku</span>
                <span class="text-center">WLTP</span>
                <span class="text-center">Ø Real</span>
              </div>
              <!-- Rows -->
              <div class="space-y-1">
                <div
                  v-for="v in model.wltpVariants"
                  :key="v.batteryCapacityKwh"
                  class="grid grid-cols-3 text-sm bg-gray-50 rounded-lg px-2 py-1.5 items-center"
                >
                  <span class="text-gray-600 font-medium">{{ v.batteryCapacityKwh }} kWh</span>
                  <span class="text-center text-gray-700">
                    {{ v.wltpRangeKm ? v.wltpRangeKm + ' km' : '–' }}
                  </span>
                  <span
                    :class="v.realConsumptionKwhPer100km ? 'text-purple-700 font-semibold' : 'text-gray-400'"
                    class="text-center"
                  >
                    {{ v.realConsumptionKwhPer100km
                      ? Math.round(v.batteryCapacityKwh / v.realConsumptionKwhPer100km * 100) + ' km'
                      : '–' }}
                  </span>
                </div>
              </div>
            </div>

            <!-- No WLTP data at all -->
            <div v-else class="text-xs text-gray-400 text-center py-3">
              Keine Spezifikationsdaten verfügbar
            </div>

            <div class="flex justify-end mt-3">
              <span class="text-xs text-green-600 font-medium group-hover:underline">Details →</span>
            </div>
          </a>
        </div>

        <!-- SEO text section -->
        <div class="bg-white md:rounded-2xl md:border-x border-t md:border-b border-gray-200 p-6 mt-6">
          <h2 class="text-xl font-bold text-gray-900 mb-3">
            {{ brand.brandDisplayName }} Elektroautos – Realer Verbrauch vs. WLTP
          </h2>
          <p class="text-sm text-gray-600 leading-relaxed">
            Auf dieser Seite findest du alle {{ brand.brandDisplayName }} Elektromodelle im Überblick.
            EV Monitor sammelt reale Verbrauchsdaten von {{ brand.brandDisplayName }}-Fahrern und vergleicht sie mit den offiziellen WLTP-Werten.
            <template v-if="modelsWithData.length > 0">
              Für {{ modelsWithData.length }} {{ modelsWithData.length === 1 ? 'Modell' : 'Modelle' }} liegen bereits Community-Daten vor.
            </template>
            Trage deine Ladevorgänge ein und hilf anderen {{ brand.brandDisplayName }}-Fahrern mit realistischen Verbrauchswerten.
          </p>
        </div>

        <!-- CTA -->
        <div class="bg-gradient-to-br from-green-600 to-green-700 md:rounded-2xl p-6 text-white mt-6">
          <div class="flex items-center gap-2 mb-2">
            <ArrowTrendingUpIcon class="h-6 w-6" />
            <h2 class="text-xl font-bold">{{ brand.brandDisplayName }}-Fahrer? Trage deine Daten bei!</h2>
          </div>
          <p class="text-green-100 mb-4">
            Tracke deine Ladevorgänge, vergleiche deinen realen Verbrauch mit WLTP und hilf der Community mit echten Daten.
          </p>
          <div class="flex flex-wrap gap-3">
            <a href="/register"
               class="bg-white text-green-700 font-semibold px-4 py-2 rounded-lg hover:bg-green-50 transition-colors">
              Kostenlos starten
            </a>
            <a href="/login"
               class="border border-white text-white px-4 py-2 rounded-lg hover:bg-green-600 transition-colors">
              Anmelden
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
import { getBrandModels, type PublicBrandResponse } from '../api/publicModelService'
import { ArrowTrendingUpIcon, ChevronRightIcon } from '@heroicons/vue/24/outline'
import PublicNav from '../components/PublicNav.vue'

const route = useRoute()
const router = useRouter()
const loading = ref(true)
const notFound = ref(false)   // true only on genuine 404 — triggers noindex
const apiError = ref(false)   // true on transient errors — keeps robots: index, follow
const brand = ref<PublicBrandResponse | null>(null)

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
      const canonicalPath = `/modelle/${result.brandDisplayName}`
      if (route.path !== canonicalPath) {
        router.replace(canonicalPath)
      }
    }
  } catch {
    // Transient error (network, 500, timeout) — do NOT set noindex
    apiError.value = true
  } finally {
    loading.value = false
  }
})

useHead(computed(() => {
  if (notFound.value) {
    return {
      title: 'Marke nicht gefunden – EV Monitor',
      meta: [{ name: 'robots', content: 'noindex, nofollow' }]
    }
  }
  if (!brand.value) {
    return { title: 'EV Monitor' }
  }

  const name = brand.value.brandDisplayName
  const modelCount = brand.value.models.length
  const canonicalUrl = `https://ev-monitor.net/modelle/${name}`
  const currentYear = new Date().getFullYear()

  const description = `Alle ${modelCount} ${name} Elektroautos im Überblick (${currentYear}): WLTP-Reichweite, realer Verbrauch und Community-Daten. Finde heraus wie viel ein ${name} wirklich verbraucht.`

  const keywords = [
    `${name} Elektroauto`,
    `${name} Verbrauch`,
    `${name} WLTP`,
    `${name} Reichweite`,
    `${name} E-Auto`,
    `${name} kWh`,
    `${name} Elektromodelle`,
    'Elektroauto Verbrauch',
    'WLTP Unterschied real',
    'Elektroauto Reichweite',
  ].join(', ')

  const breadcrumbJsonLd = {
    '@context': 'https://schema.org',
    '@type': 'BreadcrumbList',
    itemListElement: [
      { '@type': 'ListItem', position: 1, name: 'EV Monitor', item: 'https://ev-monitor.net' },
      { '@type': 'ListItem', position: 2, name: 'Modelle', item: 'https://ev-monitor.net/modelle' },
      { '@type': 'ListItem', position: 3, name: `${name} Elektroautos`, item: canonicalUrl },
    ]
  }

  const itemListJsonLd = {
    '@context': 'https://schema.org',
    '@type': 'ItemList',
    name: `${name} Elektroautos – EV Monitor`,
    description,
    itemListElement: brand.value.models.map((model, idx) => ({
      '@type': 'ListItem',
      position: idx + 1,
      name: `${name} ${model.modelDisplayName}`,
      url: `https://ev-monitor.net/modelle/${name}/${model.modelUrlSlug}`
    }))
  }

  return {
    title: `${name} Elektroautos – Verbrauch & WLTP (${currentYear}) | EV Monitor`,
    meta: [
      { name: 'description', content: description },
      { name: 'keywords', content: keywords },
      { name: 'robots', content: 'index, follow' },
      { property: 'og:title', content: `${name} Elektroautos – Realer Verbrauch & WLTP ${currentYear}` },
      { property: 'og:description', content: description },
      { property: 'og:type', content: 'website' },
      { property: 'og:url', content: canonicalUrl },
      { property: 'og:locale', content: 'de_DE' },
    ],
    link: [
      { rel: 'canonical', href: canonicalUrl }
    ],
    script: [
      { type: 'application/ld+json', innerHTML: JSON.stringify(breadcrumbJsonLd) },
      { type: 'application/ld+json', innerHTML: JSON.stringify(itemListJsonLd) },
    ]
  }
}))
</script>
