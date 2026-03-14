<template>
  <div class="min-h-screen bg-gray-50">
    <!-- Navigation (only show if not authenticated) -->
    <nav v-if="!authStore.isAuthenticated" class="bg-white border-b border-gray-200 px-4 py-3">
      <div class="max-w-6xl mx-auto flex items-center justify-between">
        <a href="/" class="flex items-center gap-2 font-bold text-green-600 text-lg">
          <BoltIcon class="h-6 w-6" />
          EV Monitor
        </a>
        <div class="flex items-center gap-3">
          <a href="/login" class="text-sm text-gray-600 hover:text-gray-900">Anmelden</a>
          <a href="/register" class="text-sm bg-green-600 text-white px-3 py-1.5 rounded-lg hover:bg-green-700">
            Kostenlos registrieren
          </a>
        </div>
      </div>
    </nav>

    <main class="max-w-6xl mx-auto px-4 py-8">
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

      <!-- Brand page -->
      <div v-else-if="brand">
        <!-- Breadcrumb -->
        <nav class="text-sm text-gray-500 mb-4">
          <a href="/" class="hover:text-gray-700">EV Monitor</a>
          <span class="mx-2">›</span>
          <a href="/modelle" class="hover:text-gray-700">Modelle</a>
          <span class="mx-2">›</span>
          <span class="text-gray-900">{{ brand.brandDisplayName }}</span>
        </nav>

        <!-- Hero -->
        <div class="bg-white rounded-2xl border border-gray-200 p-6 mb-6">
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
        <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          <a
            v-for="model in brand.models"
            :key="model.modelEnum"
            :href="`/modelle/${brand.brandDisplayName}/${model.modelUrlSlug}`"
            class="bg-white rounded-2xl border border-gray-200 p-5 hover:border-green-400 hover:shadow-md transition-all group"
          >
            <!-- Model name & log badge -->
            <div class="flex items-start justify-between mb-3">
              <h2 class="text-lg font-bold text-gray-900 group-hover:text-green-700 transition-colors">
                {{ model.modelDisplayName }}
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
          </a>
        </div>

        <!-- SEO text section -->
        <div class="bg-white rounded-2xl border border-gray-200 p-6 mt-6">
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
        <div class="bg-gradient-to-br from-green-600 to-green-700 rounded-2xl p-6 text-white mt-6">
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
import { useAuthStore } from '../stores/auth'
import { getBrandModels, type PublicBrandResponse } from '../api/publicModelService'
import { BoltIcon, ArrowTrendingUpIcon } from '@heroicons/vue/24/outline'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const loading = ref(true)
const notFound = ref(false)
const brand = ref<PublicBrandResponse | null>(null)

const modelsWithData = computed(() => brand.value?.models.filter(m => m.logCount > 0) ?? [])
const totalLogs = computed(() => brand.value?.models.reduce((sum, m) => sum + m.logCount, 0) ?? 0)

onMounted(async () => {
  const brandParam = route.params.brand as string
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
  loading.value = false
})

useHead(computed(() => {
  if (!brand.value) return { title: 'Marke nicht gefunden – EV Monitor' }
  const name = brand.value.brandDisplayName
  return {
    title: `${name} Elektroautos – Realer Verbrauch & WLTP | EV Monitor`,
    meta: [
      {
        name: 'description',
        content: `Alle ${name} E-Modelle mit WLTP-Vergleich und realen Verbrauchsdaten der Community. Finde heraus wie viel ein ${name} wirklich verbraucht.`
      }
    ]
  }
}))
</script>
