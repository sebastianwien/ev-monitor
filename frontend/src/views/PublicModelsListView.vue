<template>
  <div class="min-h-screen bg-gray-50">
    <!-- Navigation (only show if not authenticated) -->
    <nav v-if="!isAuthenticated" class="bg-white border-b border-gray-200 px-4 py-3">
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
      <!-- Hero -->
      <div class="bg-white rounded-2xl border border-gray-200 p-8 mb-8">
        <h1 class="text-3xl font-bold text-gray-900 mb-3">
          Elektroauto Verbrauch im Vergleich
        </h1>
        <p class="text-gray-600 text-lg mb-4">
          Reale Verbrauchsdaten von der Community – WLTP vs. Praxis für alle Elektroautos.
        </p>
        <p class="text-sm text-gray-500">
          {{ modelsWithData.length }} Modelle mit echten Community-Daten
        </p>
      </div>

      <!-- Loading state -->
      <div v-if="loading" class="flex items-center justify-center py-20">
        <div class="animate-spin rounded-full h-10 w-10 border-b-2 border-green-600"></div>
      </div>

      <!-- Filters -->
      <div v-else-if="modelsWithData.length > 0" class="mb-6">
        <div class="bg-white rounded-xl border border-gray-200 p-4">
          <div class="flex items-center gap-3">
            <span class="text-sm font-medium text-gray-700">Marken:</span>
            <div class="relative">
              <button
                @click="dropdownOpen = !dropdownOpen"
                class="flex items-center gap-2 px-4 py-2 bg-white border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors"
              >
                <span class="text-sm">
                  {{ selectedBrands.length === 0 ? 'Alle Marken' : `${selectedBrands.length} ausgewählt` }}
                </span>
                <span class="text-gray-400">▼</span>
              </button>

              <!-- Dropdown -->
              <div
                v-if="dropdownOpen"
                class="absolute top-full left-0 mt-2 w-64 bg-white border border-gray-200 rounded-xl shadow-lg z-50 max-h-96 overflow-y-auto"
              >
                <div class="p-2">
                  <!-- Select All / Clear All -->
                  <div class="flex gap-2 pb-2 mb-2 border-b border-gray-100">
                    <button
                      @click="selectAllBrands"
                      class="flex-1 text-xs px-2 py-1.5 bg-green-50 text-green-700 rounded hover:bg-green-100"
                    >
                      Alle auswählen
                    </button>
                    <button
                      @click="clearAllBrands"
                      class="flex-1 text-xs px-2 py-1.5 bg-gray-50 text-gray-700 rounded hover:bg-gray-100"
                    >
                      Alle abwählen
                    </button>
                  </div>

                  <!-- Brand Checkboxes -->
                  <label
                    v-for="brand in availableBrands"
                    :key="brand"
                    class="flex items-center gap-2 px-2 py-2 hover:bg-gray-50 rounded cursor-pointer"
                  >
                    <input
                      type="checkbox"
                      :checked="selectedBrands.includes(brand)"
                      @change="toggleBrand(brand)"
                      class="w-4 h-4 text-green-600 border-gray-300 rounded focus:ring-green-500"
                    />
                    <span class="text-sm text-gray-700">{{ brand }}</span>
                  </label>
                </div>
              </div>
            </div>

            <!-- Active Filter Count -->
            <span v-if="selectedBrands.length > 0" class="text-sm text-gray-500">
              ({{ filteredModels.length }} von {{ modelsWithData.length }} Modellen)
            </span>
          </div>
        </div>
      </div>

      <!-- Models Grid -->
      <div v-if="filteredModels.length > 0" class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        <a
          v-for="model in filteredModels"
          :key="`${model.brand}/${model.model}`"
          :href="`/modelle/${model.brand}/${model.model.replace(/ /g, '_')}`"
          class="bg-white rounded-xl border border-gray-200 p-5 hover:border-green-500 hover:shadow-md transition-all"
        >
          <div class="flex items-start justify-between mb-3">
            <div>
              <h3 class="font-bold text-gray-900 text-lg">
                {{ model.brand }} {{ model.model }}
              </h3>
            </div>
            <TruckIcon class="h-8 w-8 text-gray-400" />
          </div>

          <div class="space-y-2 text-sm">
            <div class="flex items-center gap-2 text-gray-600">
              <ChartBarIcon class="h-4 w-4 text-green-600" />
              <span>Echte Community-Daten</span>
            </div>
            <div class="text-xs text-gray-400">
              Realer Verbrauch, WLTP-Vergleich & mehr
            </div>
          </div>
        </a>
      </div>

      <!-- Empty state: No models at all -->
      <div v-else-if="!loading && modelsWithData.length === 0" class="text-center py-20">
        <TruckIcon class="h-16 w-16 text-gray-300 mb-4 mx-auto" />
        <h2 class="text-xl font-bold text-gray-800 mb-2">Noch keine Community-Daten</h2>
        <p class="text-gray-500 mb-6">
          Sei der Erste der echte Verbrauchsdaten beisteuert und der Community hilft!
        </p>
        <a href="/register" class="inline-block bg-green-600 text-white px-6 py-3 rounded-lg hover:bg-green-700">
          Kostenlos registrieren
        </a>
      </div>

      <!-- Empty state: Filtered but no results -->
      <div v-else-if="!loading && selectedBrands.length > 0 && filteredModels.length === 0" class="text-center py-20">
        <div class="text-5xl mb-4">🔍</div>
        <h2 class="text-xl font-bold text-gray-800 mb-2">Keine Modelle gefunden</h2>
        <p class="text-gray-500 mb-6">
          Für die ausgewählten Marken sind aktuell keine Community-Daten verfügbar.
        </p>
        <button
          @click="clearAllBrands"
          class="bg-green-600 text-white px-4 py-2 rounded-lg hover:bg-green-700"
        >
          Alle Marken anzeigen
        </button>
      </div>

      <!-- CTA Section -->
      <div v-if="!loading" class="bg-gradient-to-br from-green-600 to-green-700 rounded-2xl p-8 text-white mt-8">
        <div class="flex items-center gap-2 mb-3">
          <ArrowTrendingUpIcon class="h-7 w-7" />
          <h2 class="text-2xl font-bold">Dein Fahrzeug fehlt?</h2>
        </div>
        <p class="text-green-100 mb-6">
          Registriere dich kostenlos und trage als Erster Verbrauchsdaten für dein Elektroauto ein.
          Hilf der Community mit realen Daten!
        </p>
        <div class="flex flex-wrap gap-3">
          <a href="/register"
             class="bg-white text-green-700 font-semibold px-6 py-3 rounded-lg hover:bg-green-50 transition-colors">
            Kostenlos starten
          </a>
          <a href="/login"
             class="border-2 border-white text-white px-6 py-3 rounded-lg hover:bg-green-600 transition-colors">
            Anmelden
          </a>
        </div>
      </div>
    </main>

    <footer class="max-w-6xl mx-auto px-4 py-8 mt-6 border-t border-gray-200 text-sm text-gray-500 text-center">
      © {{ currentYear }} EV Monitor ·
      <a href="/" class="hover:text-gray-700">{{ isAuthenticated ? 'Dashboard' : 'Startseite' }}</a>
      <template v-if="!isAuthenticated">
        ·
        <a href="/register" class="hover:text-gray-700">Kostenlos registrieren</a> ·
        <a href="/login" class="hover:text-gray-700">Anmelden</a>
      </template>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useHead } from '@unhead/vue'
import { useAuthStore } from '../stores/auth'
import { getAllModelsWithWltpData } from '../api/publicModelService'
import { BoltIcon, TruckIcon, ChartBarIcon, ArrowTrendingUpIcon } from '@heroicons/vue/24/outline'

const authStore = useAuthStore()
const loading = ref(true)
const modelsList = ref<string[]>([])
const selectedBrands = ref<string[]>([])
const dropdownOpen = ref(false)

const isAuthenticated = computed(() => authStore.isAuthenticated())
const currentYear = new Date().getFullYear()

interface ModelInfo {
  brand: string
  model: string
}

const modelsWithData = computed((): ModelInfo[] => {
  return modelsList.value.map(entry => {
    // Parse format: "BRAND/MODEL" (e.g., "TESLA/MODEL_3")
    const [brand, model] = entry.split('/')
    return {
      brand: brand || 'UNKNOWN',
      model: model || entry
    }
  })
})

const availableBrands = computed(() => {
  const brands = new Set(modelsWithData.value.map(m => m.brand))
  return Array.from(brands).sort()
})

const filteredModels = computed(() => {
  if (selectedBrands.value.length === 0) {
    return modelsWithData.value
  }
  return modelsWithData.value.filter(m => selectedBrands.value.includes(m.brand))
})

useHead({
  title: 'Elektroauto Verbrauch Vergleich – Alle Modelle | EV Monitor',
  meta: [
    {
      name: 'description',
      content: 'Vergleiche den realen Stromverbrauch aller Elektroautos. Community-Daten vs. WLTP für Tesla, VW ID, Hyundai Ioniq, BMW i4 und mehr – kein Marketing, nur echte Messwerte.'
    },
    { name: 'keywords', content: 'Elektroauto Verbrauch, EV kWh 100km, WLTP vs Real, Tesla Verbrauch, VW ID.3 Verbrauch, Elektroauto Reichweite, Ladekosten Vergleich' },
    { name: 'robots', content: 'index, follow' },
    { property: 'og:title', content: 'Elektroauto Verbrauch Vergleich – Reale Community-Daten' },
    { property: 'og:description', content: 'Echte Verbrauchsdaten von der Community für alle Elektroautos. WLTP vs. Praxis im direkten Vergleich.' },
    { property: 'og:type', content: 'website' },
    { property: 'og:url', content: 'https://ev-monitor.net/modelle' }
  ],
  link: [
    { rel: 'canonical', href: 'https://ev-monitor.net/modelle' }
  ]
})

onMounted(async () => {
  try {
    modelsList.value = await getAllModelsWithWltpData()
  } catch (err) {
    console.error('Failed to load models:', err)
  } finally {
    loading.value = false
  }

  // Close dropdown on click outside
  document.addEventListener('click', handleClickOutside)
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
})

function handleClickOutside(event: MouseEvent) {
  const target = event.target as HTMLElement
  if (!target.closest('.relative')) {
    dropdownOpen.value = false
  }
}

function toggleBrand(brand: string) {
  const index = selectedBrands.value.indexOf(brand)
  if (index > -1) {
    selectedBrands.value.splice(index, 1)
  } else {
    selectedBrands.value.push(brand)
  }
}

function selectAllBrands() {
  selectedBrands.value = [...availableBrands.value]
}

function clearAllBrands() {
  selectedBrands.value = []
  dropdownOpen.value = false
}
</script>
