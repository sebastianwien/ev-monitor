<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { getAllModelsWithWltpData, getModelStats, getPlatformStats, type PublicModelStats } from '../api/publicModelService'
import {
  LockClosedIcon,
  UsersIcon,
  ArrowRightIcon,
  BoltIcon,
  HeartIcon,
  ArrowDownTrayIcon
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
const displayModels = ref(0)
const displayUsers = ref(0)

function animateCount(target: number, setter: (v: number) => void, duration = 1400) {
  const start = Date.now()
  const tick = () => {
    const progress = Math.min((Date.now() - start) / duration, 1)
    const eased = 1 - Math.pow(1 - progress, 3) // ease-out cubic
    setter(Math.round(eased * target))
    if (progress < 1) requestAnimationFrame(tick)
  }
  requestAnimationFrame(tick)
}

onMounted(async () => {
  // Load platform stats and animate counters
  try {
    const stats = await getPlatformStats()
    animateCount(stats.modelCount, v => displayModels.value = v)
    animateCount(stats.userCount, v => displayUsers.value = v)
  } catch {
    // fallback: leave at 0
  }

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

const demoLoading = ref(false)
const demoLogin = async () => {
  demoLoading.value = true
  try {
    const response = await import('../api/axios').then(m => m.default.post('/auth/demo-login'))
    authStore.setToken(response.data.token)
    router.push('/dashboard')
  } catch {
    router.push('/login')
  } finally {
    demoLoading.value = false
  }
}

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
      <div class="max-w-7xl mx-auto px-6 sm:px-8 lg:px-12">
        <div class="flex justify-between items-center h-16">
          <div class="flex items-center gap-2">
            <BoltIcon class="h-6 w-6 text-green-600" />
            <span class="text-base font-bold text-gray-900 whitespace-nowrap">EV Monitor</span>
          </div>
          <div class="flex items-center gap-2 sm:gap-3">
            <a
              href="https://github.com/sebastianwien/ev-monitor"
              target="_blank"
              rel="noopener noreferrer"
              class="text-gray-600 hover:text-gray-900 p-2 inline-flex items-center"
              aria-label="View source on GitHub"
            >
              <svg class="h-5 w-5" fill="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                <path fill-rule="evenodd" d="M12 2C6.477 2 2 6.484 2 12.017c0 4.425 2.865 8.18 6.839 9.504.5.092.682-.217.682-.483 0-.237-.008-.868-.013-1.703-2.782.605-3.369-1.343-3.369-1.343-.454-1.158-1.11-1.466-1.11-1.466-.908-.62.069-.608.069-.608 1.003.07 1.531 1.032 1.531 1.032.892 1.53 2.341 1.088 2.91.832.092-.647.35-1.088.636-1.338-2.22-.253-4.555-1.113-4.555-4.951 0-1.093.39-1.988 1.029-2.688-.103-.253-.446-1.272.098-2.65 0 0 .84-.27 2.75 1.026A9.564 9.564 0 0112 6.844c.85.004 1.705.115 2.504.337 1.909-1.296 2.747-1.027 2.747-1.027.546 1.379.202 2.398.1 2.651.64.7 1.028 1.595 1.028 2.688 0 3.848-2.339 4.695-4.566 4.943.359.309.678.92.678 1.855 0 1.338-.012 2.419-.012 2.747 0 .268.18.58.688.482A10.019 10.019 0 0022 12.017C22 6.484 17.522 2 12 2z" clip-rule="evenodd" />
              </svg>
            </a>
            <template v-if="authStore.isAuthenticated()">
              <router-link
                to="/dashboard"
                class="text-gray-600 hover:text-gray-900 px-2 sm:px-3 py-2 text-sm font-medium"
              >
                Dashboard
              </router-link>
            </template>
            <template v-else>
              <router-link
                to="/login"
                class="text-gray-600 hover:text-gray-900 px-2 sm:px-3 py-2 text-sm font-medium"
              >
                Login
              </router-link>
              <router-link
                to="/register"
                class="bg-green-600 text-white px-3 sm:px-4 py-2 rounded-lg text-sm font-medium hover:bg-green-700 transition whitespace-nowrap"
              >
                Registrieren
              </router-link>
            </template>
          </div>
        </div>
      </div>
    </nav>

    <!-- Hero Section -->
    <section class="pt-12 pb-6 sm:pt-16 sm:pb-8">
      <div class="max-w-4xl mx-auto text-center px-6 sm:px-8 lg:px-12">
        <h1 class="text-4xl sm:text-5xl lg:text-6xl font-bold text-gray-900 leading-tight mb-6">
          WLTP vs. Realität:<br />
          Was dein E-Auto wirklich kann.
        </h1>
        <p class="text-xl text-gray-600 mb-8 max-w-3xl mx-auto">
          Echte Reichweiten, echte Verbräuche, echte Ladekosten – von echten Fahrern.
          Vergleiche Herstellerangaben mit Community-Daten.
        </p>
        <div class="flex flex-col sm:flex-row items-stretch sm:items-center justify-center gap-3 sm:gap-4">
          <button
            @click="goToRegister"
            class="bg-green-600 text-white px-6 py-3 sm:px-8 sm:py-4 rounded-lg text-base sm:text-lg font-semibold hover:bg-green-700 transition inline-flex items-center justify-center space-x-2"
          >
            <span>Kostenlos starten</span>
            <ArrowRightIcon class="h-5 w-5" />
          </button>
          <router-link
            to="/modelle"
            class="border border-gray-300 text-gray-700 px-6 py-3 sm:px-8 sm:py-4 rounded-lg text-base sm:text-lg font-semibold hover:border-green-500 hover:text-green-700 transition inline-flex items-center justify-center space-x-2"
          >
            <span>Modelle vergleichen</span>
            <ArrowRightIcon class="h-5 w-5" />
          </router-link>
        </div>
        <p class="mt-4 text-sm text-gray-500">
          Noch unsicher?
          <button
            @click="demoLogin"
            :disabled="demoLoading"
            class="text-indigo-600 hover:text-indigo-800 font-medium underline underline-offset-2 transition disabled:opacity-50"
          >{{ demoLoading ? 'Wird geladen…' : 'Demo-Account ausprobieren' }}</button>
          – kein Account nötig.
        </p>
        <p class="mt-4 text-sm sm:text-base font-semibold text-gray-600 tabular-nums">
          <span>{{ displayModels }}+ Modelle</span>
          <span class="mx-2">•</span>
          <span>{{ displayUsers }} Fahrer</span>
        </p>
      </div>
    </section>

    <!-- Feature Highlights -->
    <section class="pt-8 pb-12 sm:pt-12 sm:pb-16 bg-gray-50">
      <div class="max-w-7xl mx-auto px-6 sm:px-8 lg:px-12">
        <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
          <!-- Feature 1: Open Source -->
          <div class="bg-white border border-green-200 rounded-xl p-6 hover:border-green-500 transition">
            <svg class="h-10 w-10 text-green-600 mb-3" fill="currentColor" viewBox="0 0 24 24" aria-hidden="true">
              <path fill-rule="evenodd" d="M12 2C6.477 2 2 6.484 2 12.017c0 4.425 2.865 8.18 6.839 9.504.5.092.682-.217.682-.483 0-.237-.008-.868-.013-1.703-2.782.605-3.369-1.343-3.369-1.343-.454-1.158-1.11-1.466-1.11-1.466-.908-.62.069-.608.069-.608 1.003.07 1.531 1.032 1.531 1.032.892 1.53 2.341 1.088 2.91.832.092-.647.35-1.088.636-1.338-2.22-.253-4.555-1.113-4.555-4.951 0-1.093.39-1.988 1.029-2.688-.103-.253-.446-1.272.098-2.65 0 0 .84-.27 2.75 1.026A9.564 9.564 0 0112 6.844c.85.004 1.705.115 2.504.337 1.909-1.296 2.747-1.027 2.747-1.027.546 1.379.202 2.398.1 2.651.64.7 1.028 1.595 1.028 2.688 0 3.848-2.339 4.695-4.566 4.943.359.309.678.92.678 1.855 0 1.338-.012 2.419-.012 2.747 0 .268.18.58.688.482A10.019 10.019 0 0022 12.017C22 6.484 17.522 2 12 2z" clip-rule="evenodd" />
            </svg>
            <h3 class="text-lg font-semibold text-gray-900 mb-2">100% Open Source</h3>
            <p class="text-sm text-gray-600 mb-3">
              Vollständig transparent auf GitHub. Keine Vendor Lock-ins.
            </p>
            <a
              href="https://github.com/sebastianwien/ev-monitor"
              target="_blank"
              rel="noopener noreferrer"
              class="inline-flex items-center gap-1 text-green-600 hover:text-green-700 text-xs font-medium"
            >
              <span>View Source →</span>
            </a>
          </div>

          <!-- Feature 2: Auto-Import -->
          <div class="bg-white border border-gray-200 rounded-xl p-6 hover:border-green-500 transition">
            <ArrowDownTrayIcon class="h-10 w-10 text-gray-400 mb-3" />
            <h3 class="text-lg font-semibold text-gray-900 mb-2">Auto-Import</h3>
            <p class="text-sm text-gray-600 mb-2">
              Tesla Fleet API, Sprit-Monitor, go-eCharger & OCPP Wallboxen – deine Daten fließen automatisch.
            </p>
            <div class="flex gap-1.5">
              <span class="text-xs bg-blue-100 text-blue-800 font-medium px-2 py-0.5 rounded-full">go-e BETA</span>
              <span class="text-xs bg-blue-100 text-blue-800 font-medium px-2 py-0.5 rounded-full">OCPP BETA</span>
            </div>
          </div>

          <!-- Feature 3: Privacy First -->
          <div class="bg-white border border-gray-200 rounded-xl p-6 hover:border-green-500 transition">
            <LockClosedIcon class="h-10 w-10 text-gray-400 mb-3" />
            <h3 class="text-lg font-semibold text-gray-900 mb-2">Privacy First</h3>
            <p class="text-sm text-gray-600">
              Wir anonymisieren deine Daten. Geohashing statt Tracking.
              Deine Daten gehören dir.
            </p>
          </div>

          <!-- Feature 4: Community -->
          <div class="bg-white border border-gray-200 rounded-xl p-6 hover:border-green-500 transition">
            <UsersIcon class="h-10 w-10 text-gray-400 mb-3" />
            <h3 class="text-lg font-semibold text-gray-900 mb-2">Community-Daten</h3>
            <p class="text-sm text-gray-600">
              Vergleiche WLTP mit echten Fahrer-Daten.
              Trage bei, verdiene Coins.
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
              {{ preview.stats.modelDisplayName }}
            </h3>

            <div class="flex flex-wrap gap-6 text-sm text-gray-600 mb-3">
              <span>{{ preview.stats.avgKwhPerSession?.toFixed(1) || '—' }} kWh</span>
              <span>•</span>
              <span>{{ preview.stats.avgCostPerKwh?.toFixed(2) || '—' }}€/kWh</span>
              <span>•</span>
              <span>{{ preview.stats.logCount }} Fahrten</span>
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

    <!-- Import Hub Teaser -->
    <section class="py-16 px-4 sm:px-6 lg:px-8 border-t border-gray-100">
      <div class="max-w-4xl mx-auto">
        <div class="text-center mb-10">
          <div class="inline-flex items-center gap-2 mb-3">
            <ArrowDownTrayIcon class="h-6 w-6 text-green-600" />
            <h2 class="text-2xl font-semibold text-gray-900">Nie wieder manuell eintippen</h2>
          </div>
          <p class="text-gray-600">
            Verbinde deine Datenquellen — Ladevorgänge kommen automatisch rein.
          </p>
        </div>

        <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <!-- Tesla -->
          <div class="bg-white border border-gray-200 rounded-xl p-5 flex items-start gap-4">
            <div class="bg-gray-900 rounded-lg p-2 shrink-0">
              <BoltIcon class="h-5 w-5 text-white" />
            </div>
            <div>
              <div class="flex items-center gap-2 mb-1">
                <span class="font-semibold text-gray-900 text-sm">Tesla Fleet API</span>
                <span class="text-xs bg-green-100 text-green-700 font-medium px-2 py-0.5 rounded-full">Verfügbar</span>
              </div>
              <p class="text-sm text-gray-500">Ladehistorie automatisch importieren — kein Tippen, kein Kopieren.</p>
            </div>
          </div>

          <!-- Sprit-Monitor -->
          <div class="bg-white border border-gray-200 rounded-xl p-5 flex items-start gap-4">
            <div class="bg-indigo-600 rounded-lg p-2 shrink-0">
              <ArrowDownTrayIcon class="h-5 w-5 text-white" />
            </div>
            <div>
              <div class="flex items-center gap-2 mb-1">
                <span class="font-semibold text-gray-900 text-sm">Sprit-Monitor</span>
                <span class="text-xs bg-green-100 text-green-700 font-medium px-2 py-0.5 rounded-full">Verfügbar</span>
              </div>
              <p class="text-sm text-gray-500">Komplette Ladehistorie aus Sprit-Monitor einmalig importieren.</p>
            </div>
          </div>

          <!-- go-eCharger -->
          <div class="bg-white border border-gray-200 rounded-xl p-5 flex items-start gap-4">
            <div class="bg-green-600 rounded-lg p-2 shrink-0">
              <BoltIcon class="h-5 w-5 text-white" />
            </div>
            <div>
              <div class="flex items-center gap-2 mb-1">
                <span class="font-semibold text-gray-900 text-sm">go-eCharger Cloud</span>
                <span class="text-xs bg-green-100 text-green-700 font-medium px-2 py-0.5 rounded-full">Verfügbar</span>
                <span class="text-xs bg-blue-100 text-blue-800 font-medium px-2 py-0.5 rounded-full">BETA</span>
              </div>
              <p class="text-sm text-gray-500">Wallbox-Sessions automatisch nach jeder Ladung importieren.</p>
            </div>
          </div>

          <!-- OCPP -->
          <div class="bg-white border border-gray-200 rounded-xl p-5 flex items-start gap-4">
            <div class="bg-gray-700 rounded-lg p-2 shrink-0">
              <BoltIcon class="h-5 w-5 text-white" />
            </div>
            <div>
              <div class="flex items-center gap-2 mb-1">
                <span class="font-semibold text-gray-900 text-sm">OCPP Wallbox</span>
                <span class="text-xs bg-green-100 text-green-700 font-medium px-2 py-0.5 rounded-full">Verfügbar</span>
                <span class="text-xs bg-blue-100 text-blue-800 font-medium px-2 py-0.5 rounded-full">BETA</span>
              </div>
              <p class="text-sm text-gray-500">Universelles Protokoll für alle OCPP-fähigen Heimwallboxen.</p>
            </div>
          </div>
        </div>
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
            <a href="https://forms.gle/w4qkLLEv6nYGK3LWA" target="_blank" rel="noopener noreferrer" class="hover:text-gray-900">Feedback</a>
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
