<template>
  <div class="min-h-screen bg-gray-50">
    <PublicNav />

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
                    <a :href="`/modelle/${brand}`" class="text-sm text-gray-700 hover:text-green-600" @click.stop>{{ brand }}</a>
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
        <!-- Real community models -->
        <div
          v-for="model in filteredModels"
          :key="`${model.brand}/${model.model}`"
          class="model-card flex flex-col bg-white rounded-xl border p-5 hover:shadow-md transition-all"
          :class="isSelectedForCompare(`${model.brand}/${model.model}`)
            ? 'border-green-500 ring-2 ring-green-200'
            : 'border-gray-200 hover:border-green-500'"
        >
          <a :href="`/modelle/${model.brand}/${model.model.replace(/ /g, '_')}`" class="block">
            <div class="flex items-start justify-between mb-3">
              <h3 class="font-bold text-gray-900 text-lg">
                {{ model.brand }} {{ model.model }}
              </h3>
              <TruckIcon class="h-8 w-8 text-gray-400 flex-shrink-0" />
            </div>
            <div class="space-y-2 text-sm">
              <div class="flex items-center gap-2 text-gray-600">
                <svg class="h-4 w-4 flex-shrink-0" viewBox="0 0 16 16" fill="none">
                  <rect x="1" y="9" width="3" height="6" rx="0.75" fill="#86efac"/>
                  <rect x="6" y="5" width="3" height="10" rx="0.75" fill="#22c55e"/>
                  <rect x="11" y="2" width="3" height="13" rx="0.75" fill="#15803d"/>
                </svg>
                <span>Echte Community-Daten</span>
              </div>
              <div class="flex items-center justify-between">
                <span class="text-xs text-gray-400">Realer Verbrauch, WLTP-Vergleich & mehr</span>
                <button
                  @click.prevent="toggleCompare(`${model.brand}/${model.model}`)"
                  :disabled="!isSelectedForCompare(`${model.brand}/${model.model}`) && selectedForCompare.length >= MAX_COMPARE"
                  :title="isSelectedForCompare(`${model.brand}/${model.model}`) ? 'Aus Vergleich entfernen' : 'Zum Vergleich hinzufügen'"
                  class="p-1.5 rounded-full transition-colors flex-shrink-0"
                  :class="isSelectedForCompare(`${model.brand}/${model.model}`)
                    ? 'bg-green-500 text-white'
                    : selectedForCompare.length >= MAX_COMPARE
                      ? 'bg-gray-100 text-gray-300 cursor-not-allowed'
                      : 'bg-gray-100 text-gray-400 hover:bg-green-100 hover:text-green-600'"
                >
                  <CheckIcon v-if="isSelectedForCompare(`${model.brand}/${model.model}`)" class="h-4 w-4" />
                  <ArrowsRightLeftIcon v-else class="h-4 w-4" />
                </button>
              </div>
            </div>
          </a>
        </div>

        <!-- Fallback filler models (mobile only, no brand filter active) — no compare toggle -->
        <a
          v-for="model in mobileFillModels"
          :key="`fallback/${model.brand}/${model.model}`"
          :href="`/modelle/${model.brand}/${model.model}`"
          class="model-card bg-white rounded-xl border border-gray-100 p-4 hover:border-green-400 hover:shadow-sm transition-all opacity-80"
        >
          <div class="flex items-start justify-between mb-2">
            <h3 class="font-semibold text-gray-700 text-base">{{ model.displayName }}</h3>
            <TruckIcon class="h-6 w-6 text-gray-300" />
          </div>
          <div class="flex items-center gap-2 text-xs text-gray-400">
            <ChartBarIcon class="h-3.5 w-3.5" />
            <span>WLTP-Daten verfügbar</span>
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

      <!-- Floating Compare Bar (outside v-if/v-else chain) -->
      <Transition name="compare-bar">
        <div
          v-if="selectedForCompare.length >= 2"
          class="fixed bottom-20 left-1/2 -translate-x-1/2 z-50 w-full max-w-lg px-4"
        >
          <div class="bg-gray-900 text-white rounded-2xl shadow-2xl px-4 py-3 flex items-center gap-3">
            <ArrowsRightLeftIcon class="h-5 w-5 text-green-400 flex-shrink-0" />
            <div class="flex-1 min-w-0">
              <div class="text-xs text-gray-400 mb-0.5">Vergleich ({{ selectedForCompare.length }}/{{ MAX_COMPARE }})</div>
              <div class="text-sm font-medium truncate">
                {{ selectedForCompare.map(compareLabel).join(' · ') }}
              </div>
            </div>
            <button
              @click="startCompare"
              class="flex-shrink-0 bg-green-500 hover:bg-green-400 text-white text-sm font-semibold px-4 py-1.5 rounded-lg transition-colors">
              Vergleichen
            </button>
            <button @click="clearCompare" class="flex-shrink-0 p-1 text-gray-400 hover:text-white transition-colors">
              <XMarkIcon class="h-5 w-5" />
            </button>
          </div>
        </div>
      </Transition>
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
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { getAllModelsWithWltpData } from '../api/publicModelService'
import { TruckIcon, ChartBarIcon, ArrowTrendingUpIcon, ArrowsRightLeftIcon, XMarkIcon, CheckIcon } from '@heroicons/vue/24/outline'
import PublicNav from '../components/PublicNav.vue'

const router = useRouter()

const authStore = useAuthStore()
const loading = ref(true)
const modelsList = ref<string[]>([])
const selectedBrands = ref<string[]>([])
const dropdownOpen = ref(false)

// ── Comparison selection ───────────────────────────────────────────────────
const MAX_COMPARE = 3
const selectedForCompare = ref<string[]>([])

function isSelectedForCompare(key: string) {
  return selectedForCompare.value.includes(key)
}

function toggleCompare(key: string) {
  const idx = selectedForCompare.value.indexOf(key)
  if (idx > -1) {
    selectedForCompare.value.splice(idx, 1)
  } else if (selectedForCompare.value.length < MAX_COMPARE) {
    selectedForCompare.value.push(key)
  }
}

function clearCompare() {
  selectedForCompare.value = []
}

function startCompare() {
  router.push(`/modelle/vergleich?models=${selectedForCompare.value.join(',')}`)
}

function compareLabel(key: string): string {
  const [brand, model] = key.split('/')
  return `${brand} ${model}`.replace(/_/g, ' ')
}

const isAuthenticated = computed(() => authStore.isAuthenticated())
const currentYear = new Date().getFullYear()

interface ModelInfo {
  brand: string
  model: string
}

interface FallbackModel {
  brand: string
  model: string
  displayName: string
}

// Popular EV models in Germany by registration numbers (DE slugs matching DB)
const POPULAR_DE_FALLBACK: FallbackModel[] = [
  { brand: 'Volkswagen', model: 'ID.3', displayName: 'VW ID.3' },
  { brand: 'Tesla', model: 'Model_Y', displayName: 'Tesla Model Y' },
  { brand: 'Tesla', model: 'Model_3', displayName: 'Tesla Model 3' },
  { brand: 'Volkswagen', model: 'ID.4', displayName: 'VW ID.4' },
  { brand: 'Hyundai', model: 'Ioniq_5', displayName: 'Hyundai Ioniq 5' },
  { brand: 'Škoda', model: 'Enyaq', displayName: 'Škoda Enyaq' },
  { brand: 'BMW', model: 'i4', displayName: 'BMW i4' },
  { brand: 'Audi', model: 'Q4_e-tron', displayName: 'Audi Q4 e-tron' },
  { brand: 'Kia', model: 'EV6', displayName: 'Kia EV6' },
  { brand: 'MG Motor', model: 'MG4', displayName: 'MG MG4' },
  { brand: 'Hyundai', model: 'Ioniq_6', displayName: 'Hyundai Ioniq 6' },
  { brand: 'Polestar', model: 'Polestar_2', displayName: 'Polestar 2' },
]

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

// Fill up to 12 models on mobile with popular fallbacks (only when no brand filter active)
const mobileFillModels = computed((): FallbackModel[] => {
  if (selectedBrands.value.length > 0) return []
  const needed = 12 - filteredModels.value.length
  if (needed <= 0) return []
  const normalize = (s: string) => s.replace(/ /g, '_').toLowerCase()
  const existingSlugs = new Set(filteredModels.value.map(m => normalize(`${m.brand}/${m.model}`)))
  return POPULAR_DE_FALLBACK
    .filter(f => !existingSlugs.has(normalize(`${f.brand}/${f.model}`)))
    .slice(0, needed)
})

const itemListJsonLd = {
  '@context': 'https://schema.org',
  '@type': 'ItemList',
  name: 'Elektroauto Modelle – Realer Verbrauch & WLTP Vergleich',
  description: 'Community-Verbrauchsdaten für alle Elektroautos im Vergleich zum WLTP-Wert.',
  itemListElement: [
    { '@type': 'ListItem', position: 1, name: 'Tesla Model 3', url: 'https://ev-monitor.net/modelle/Tesla/Model_3' },
    { '@type': 'ListItem', position: 2, name: 'Tesla Model Y', url: 'https://ev-monitor.net/modelle/Tesla/Model_Y' },
    { '@type': 'ListItem', position: 3, name: 'VW ID.3', url: 'https://ev-monitor.net/modelle/Volkswagen/ID.3' },
    { '@type': 'ListItem', position: 4, name: 'VW ID.4', url: 'https://ev-monitor.net/modelle/Volkswagen/ID.4' },
    { '@type': 'ListItem', position: 5, name: 'Hyundai Ioniq 5', url: 'https://ev-monitor.net/modelle/Hyundai/Ioniq_5' },
    { '@type': 'ListItem', position: 6, name: 'Hyundai Ioniq 6', url: 'https://ev-monitor.net/modelle/Hyundai/Ioniq_6' },
    { '@type': 'ListItem', position: 7, name: 'BMW i4', url: 'https://ev-monitor.net/modelle/BMW/i4' },
    { '@type': 'ListItem', position: 8, name: 'Audi Q4 e-tron', url: 'https://ev-monitor.net/modelle/Audi/Q4_e-tron' },
    { '@type': 'ListItem', position: 9, name: 'Kia EV6', url: 'https://ev-monitor.net/modelle/Kia/EV6' },
    { '@type': 'ListItem', position: 10, name: 'Polestar 2', url: 'https://ev-monitor.net/modelle/Polestar/Polestar_2' },
  ]
}

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
  ],
  script: [
    { type: 'application/ld+json', innerHTML: JSON.stringify(itemListJsonLd) }
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

<style scoped>
/* 3D press effect for model cards */
.model-card {
  box-shadow: 0 4px 0 0 rgba(0,0,0,0.10);
  transform: translateY(0);
  transition: transform 0.08s ease, box-shadow 0.08s ease, border-color 0.15s ease;
}
.model-card:active {
  box-shadow: 0 1px 0 0 rgba(0,0,0,0.10);
  transform: translateY(3px);
  transition: transform 0.05s ease, box-shadow 0.05s ease;
}

.compare-bar-enter-active,
.compare-bar-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}
.compare-bar-enter-from,
.compare-bar-leave-to {
  opacity: 0;
  transform: translateX(-50%) translateY(16px);
}
</style>
