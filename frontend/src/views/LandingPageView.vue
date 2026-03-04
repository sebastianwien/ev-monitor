<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { getAllModelsWithWltpData, getModelStats, type PublicModelStats } from '../api/publicModelService'
import {
  ChartBarIcon,
  LockClosedIcon,
  UsersIcon,
  ArrowRightIcon,
  BoltIcon,
  HeartIcon
} from '@heroicons/vue/24/outline'

const router = useRouter()
const authStore = useAuthStore()

interface ModelPreview {
  brand: string
  model: string
  stats: PublicModelStats
}

const topModels = ref<ModelPreview[]>([])
const loading = ref(true)

onMounted(async () => {
  // Load top models with community data for SEO
  try {
    const allModels = await getAllModelsWithWltpData()

    // Fetch stats for first 3 models
    const previews = await Promise.all(
      allModels.slice(0, 3).map(async (modelPath) => {
        const [brand, model] = modelPath.split('/')
        const stats = await getModelStats(brand, model)
        return stats ? { brand, model, stats } : null
      })
    )

    topModels.value = previews.filter(p => p !== null) as ModelPreview[]
  } catch (error) {
    console.error('Failed to load model previews:', error)
  } finally {
    loading.value = false
  }
})

const goToRegister = () => router.push('/register')
const goToModelDetail = (brand: string, model: string) => {
  // Replace spaces with underscores for URL (e.g. "Polestar 2" -> "Polestar_2")
  const urlModel = model.replace(/ /g, '_')
  router.push(`/modelle/${brand}/${urlModel}`)
}

const formatDelta = (real: number | null, wltp: number): string => {
  if (!real || wltp === 0) return '—'
  const percentDelta = ((real - wltp) / wltp) * 100
  const sign = percentDelta > 0 ? '+' : ''
  return `${sign}${percentDelta.toFixed(0)}%`
}
</script>

<template>
  <div class="min-h-screen bg-white">
    <!-- Navbar -->
    <nav class="border-b border-gray-200">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="flex justify-between items-center h-16">
          <div class="flex items-center space-x-2">
            <BoltIcon class="h-8 w-8 text-green-600" />
            <span class="text-xl font-bold text-gray-900">EV Monitor</span>
          </div>
          <div class="flex items-center space-x-4">
            <router-link
              to="/modelle"
              class="text-gray-600 hover:text-gray-900 px-3 py-2 text-sm font-medium"
            >
              Modelle
            </router-link>
            <template v-if="authStore.isAuthenticated()">
              <router-link
                to="/dashboard"
                class="text-gray-600 hover:text-gray-900 px-3 py-2 text-sm font-medium"
              >
                Dashboard
              </router-link>
            </template>
            <template v-else>
              <router-link
                to="/login"
                class="text-gray-600 hover:text-gray-900 px-3 py-2 text-sm font-medium"
              >
                Login
              </router-link>
              <router-link
                to="/register"
                class="bg-green-600 text-white px-4 py-2 rounded-lg text-sm font-medium hover:bg-green-700 transition"
              >
                Registrieren
              </router-link>
            </template>
          </div>
        </div>
      </div>
    </nav>

    <!-- Hero Section -->
    <section class="py-20 px-4 sm:px-6 lg:px-8">
      <div class="max-w-4xl mx-auto text-center">
        <h1 class="text-5xl sm:text-6xl font-bold text-gray-900 leading-tight mb-6">
          WLTP vs. Realität:<br />
          Was dein E-Auto wirklich kann.
        </h1>
        <p class="text-xl text-gray-600 mb-8 max-w-3xl mx-auto">
          Echte Reichweiten, echte Verbräuche, echte Ladekosten – von echten Fahrern.
          Vergleiche Herstellerangaben mit Community-Daten.
        </p>
        <div class="flex flex-col sm:flex-row items-center justify-center gap-4">
          <button
            @click="goToRegister"
            class="bg-green-600 text-white px-8 py-4 rounded-lg text-lg font-semibold hover:bg-green-700 transition inline-flex items-center space-x-2"
          >
            <span>Kostenlos starten</span>
            <ArrowRightIcon class="h-5 w-5" />
          </button>
          <router-link
            to="/modelle"
            class="border border-gray-300 text-gray-700 px-8 py-4 rounded-lg text-lg font-semibold hover:border-green-500 hover:text-green-700 transition inline-flex items-center space-x-2"
          >
            <span>Modelle entdecken und vergleichen</span>
            <ArrowRightIcon class="h-5 w-5" />
          </router-link>
        </div>
        <p class="mt-6 text-sm text-gray-500">
          <!--300+ Ladevorgänge • -->250+ Modelle • 9 Fahrer
        </p>
      </div>
    </section>

    <!-- Feature Highlights -->
    <section class="py-16 px-4 sm:px-6 lg:px-8 bg-gray-50">
      <div class="max-w-7xl mx-auto">
        <div class="grid grid-cols-1 md:grid-cols-3 gap-8">
          <!-- Feature 1: Smart Tracking -->
          <div class="bg-white border border-gray-200 rounded-xl p-8 hover:border-green-500 transition">
            <ChartBarIcon class="h-12 w-12 text-gray-400 mb-4" />
            <h3 class="text-xl font-semibold text-gray-900 mb-3">Smart Tracking</h3>
            <p class="text-gray-600">
              Alle Ladevorgänge an einem Ort. Automatischer Tesla Import.
              Interaktive Heatmap deiner Ladestationen.
            </p>
          </div>

          <!-- Feature 2: Privacy First -->
          <div class="bg-white border border-gray-200 rounded-xl p-8 hover:border-green-500 transition">
            <LockClosedIcon class="h-12 w-12 text-gray-400 mb-4" />
            <h3 class="text-xl font-semibold text-gray-900 mb-3">Privacy First</h3>
            <p class="text-gray-600">
              Wir anonymisieren deine Daten. Geohashing statt Tracking.
              Deine Daten gehören dir.
            </p>
          </div>

          <!-- Feature 3: Community -->
          <div class="bg-white border border-gray-200 rounded-xl p-8 hover:border-green-500 transition">
            <UsersIcon class="h-12 w-12 text-gray-400 mb-4" />
            <h3 class="text-xl font-semibold text-gray-900 mb-3">Community</h3>
            <p class="text-gray-600">
              Vergleiche mit WLTP & echten Fahrer-Daten.
              Trage bei und verdiene Watt.
            </p>
          </div>
        </div>
      </div>
    </section>

    <!-- Model Preview Section -->
    <section class="py-16 px-4 sm:px-6 lg:px-8">
      <div class="max-w-7xl mx-auto">
        <div class="text-center mb-12">
          <h2 class="text-3xl font-semibold text-gray-900 mb-4">
            Was kostet das Laden wirklich?
          </h2>
          <p class="text-lg text-gray-600">
            Echte Daten von der Community
          </p>
        </div>

        <div v-if="loading" class="text-center text-gray-500">
          Lade Fahrzeugdaten...
        </div>

        <div v-else-if="topModels.length > 0" class="space-y-6">
          <!-- Model Cards -->
          <div
            v-for="preview in topModels"
            :key="`${preview.brand}-${preview.model}`"
            class="bg-white border border-gray-200 rounded-xl p-6 hover:border-green-500 transition cursor-pointer"
            @click="goToModelDetail(preview.brand, preview.model)"
          >
            <h3 class="text-2xl font-semibold text-gray-900 mb-4">
              {{ preview.stats.brand }} {{ preview.stats.model }}
            </h3>

            <div class="flex flex-wrap gap-6 text-sm text-gray-600 mb-3">
              <span>{{ preview.stats.avgKwhPerSession?.toFixed(1) || '—' }} kWh</span>
              <span>•</span>
              <span>{{ preview.stats.avgCostPerKwh?.toFixed(2) || '—' }}€/kWh</span>
              <span>•</span>
              <span>{{ preview.stats.logCount }} Logs</span>
            </div>

            <div v-if="preview.stats.avgConsumptionKwhPer100km && preview.stats.wltpVariants.length > 0" class="text-sm text-gray-700">
              Real: {{ preview.stats.avgConsumptionKwhPer100km.toFixed(1) }} kWh/100km
              <span class="text-gray-500">
                ({{ formatDelta(preview.stats.avgConsumptionKwhPer100km, preview.stats.wltpVariants[0].wltpConsumptionKwhPer100km) }} vs WLTP)
              </span>
            </div>

            <div class="mt-4 text-green-600 font-medium inline-flex items-center space-x-1">
              <span>Details ansehen</span>
              <ArrowRightIcon class="h-4 w-4" />
            </div>
          </div>

          <!-- View All Button -->
          <div class="text-center mt-8">
            <router-link
              to="/modelle"
              class="bg-green-600 text-white px-6 py-3 rounded-lg font-semibold hover:bg-green-700 transition inline-flex items-center space-x-2"
            >
              <span>Alle Modelle im Vergleich</span>
              <ArrowRightIcon class="h-5 w-5" />
            </router-link>
          </div>
        </div>

        <div v-else class="text-center text-gray-500">
          Noch keine Community-Daten verfügbar.
        </div>
      </div>
    </section>

    <!-- Gamification Teaser -->
    <section class="py-16 px-4 sm:px-6 lg:px-8 bg-gray-50">
      <div class="max-w-3xl mx-auto text-center">
        <h2 class="text-2xl font-semibold text-gray-900 mb-6">
          Belohnungen für Community-Beiträge
        </h2>
        <ul class="text-left inline-block text-gray-600 space-y-2 mb-4">
          <li>• Teile WLTP-Daten deines Fahrzeugs</li>
          <li>• Verdiene Punkte für Beiträge</li>
          <li>• Erreiche Meilensteine</li>
        </ul>
        <p class="text-sm text-gray-500 mt-6">
          (Weitere Features in Entwicklung)
        </p>
      </div>
    </section>

    <!-- Final CTA -->
    <section class="py-20 px-4 sm:px-6 lg:px-8">
      <div class="max-w-3xl mx-auto text-center">
        <h2 class="text-4xl font-bold text-gray-900 mb-4">
          Bereit für transparente Ladekosten?
        </h2>
        <p class="text-lg text-gray-600 mb-8">
          Kostenlos registrieren und loslegen.<br />
          E-Mail verifizieren – fertig.
        </p>
        <button
          @click="goToRegister"
          class="bg-green-600 text-white px-8 py-4 rounded-lg text-lg font-semibold hover:bg-green-700 transition"
        >
          Jetzt kostenlos starten
        </button>
      </div>
    </section>

    <!-- Footer -->
    <footer class="border-t border-gray-200 py-12 px-4 sm:px-6 lg:px-8 bg-gray-50">
      <div class="max-w-7xl mx-auto">
        <div class="flex flex-col md:flex-row justify-between items-center space-y-4 md:space-y-0">
          <div class="flex items-center space-x-2">
            <BoltIcon class="h-6 w-6 text-green-600" />
            <span class="font-semibold text-gray-900">EV Monitor</span>
          </div>
          <div class="flex flex-wrap justify-center gap-6 text-sm text-gray-600">
            <router-link to="/modelle" class="hover:text-gray-900 font-medium">Modelle</router-link>
            <router-link to="/datenschutz" class="hover:text-gray-900">Datenschutz</router-link>
            <router-link to="/impressum" class="hover:text-gray-900">Impressum</router-link>
            <router-link to="/agb" class="hover:text-gray-900">AGB</router-link>
            <a href="https://github.com/sebastianwien/ev-monitor" target="_blank" rel="noopener noreferrer" class="hover:text-gray-900">GitHub</a>
          </div>
        </div>
        <div class="mt-8 text-center text-sm text-gray-500">
          Made with ⚡ for the EV community
        </div>
        <div class="mt-3 text-center">
          <a
            href="https://ko-fi.com/ev_monitor"
            target="_blank"
            rel="noopener noreferrer"
            class="inline-flex items-center gap-1.5 text-xs text-gray-400 hover:text-amber-600 transition"
          >
            <HeartIcon class="h-3.5 w-3.5" />
            <span>EV Monitor unterstützen</span>
          </a>
        </div>
      </div>
    </footer>
  </div>
</template>
