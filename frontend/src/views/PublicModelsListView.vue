<template>
  <div class="min-h-screen bg-gray-50">
    <PublicNav />

    <main class="max-w-6xl mx-auto px-4 py-8">
      <!-- Hero -->
      <div class="bg-white rounded-2xl border border-gray-200 p-8 mb-8">
        <h1 class="text-3xl font-bold text-gray-900 mb-3">
          Elektroautos im Vergleich
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

      <!-- Filters + Popular Models -->
      <div v-if="!loading && modelsWithData.length > 0" class="mb-6">
        <h2 class="text-lg font-semibold text-gray-800 mb-3">Populäre Modelle</h2>
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
          :key="`${model.brandDisplayName}/${model.modelUrlSlug}`"
          class="model-card flex flex-col bg-white rounded-xl border p-4 transition-all"
          :class="isSelectedForCompare(`${model.brandDisplayName}/${model.modelUrlSlug}`)
            ? 'border-green-500 ring-2 ring-green-200'
            : 'border-gray-200 hover:border-green-500'"
        >
          <a :href="`/modelle/${model.brandDisplayName}/${model.modelUrlSlug}`" class="block flex-1">
            <div class="flex items-start justify-between gap-2 mb-2">
              <h3 class="font-bold text-gray-900 text-base">{{ model.modelDisplayName }}</h3>
              <span class="text-xs text-gray-400 whitespace-nowrap mt-0.5">{{ model.logCount }} Fahrten</span>
            </div>
            <div v-if="model.avgConsumptionKwhPer100km" class="text-sm text-gray-700 mb-0.5">
              Real: <span class="font-medium">{{ model.avgConsumptionKwhPer100km.toFixed(1) }} kWh/100km</span>
            </div>
            <div v-if="model.avgConsumptionKwhPer100km && model.bestWltpConsumptionKwhPer100km"
                 class="text-sm font-medium mb-3"
                 :class="model.avgConsumptionKwhPer100km > model.bestWltpConsumptionKwhPer100km ? 'text-red-500' : 'text-green-600'">
              ({{ formatDelta(model.avgConsumptionKwhPer100km, model.bestWltpConsumptionKwhPer100km) }} vs. WLTP)
            </div>
            <div class="text-green-600 font-medium flex items-center gap-1 text-sm mt-auto">
              <span>Details ansehen</span>
              <ArrowRightIcon class="h-4 w-4" />
            </div>
          </a>
          <div class="flex justify-end mt-2 pt-2 border-t border-gray-100">
            <button
              @click.prevent="toggleCompare(`${model.brandDisplayName}/${model.modelUrlSlug}`)"
              :disabled="!isSelectedForCompare(`${model.brandDisplayName}/${model.modelUrlSlug}`) && selectedForCompare.length >= MAX_COMPARE"
              :title="isSelectedForCompare(`${model.brandDisplayName}/${model.modelUrlSlug}`) ? 'Aus Vergleich entfernen' : 'Zum Vergleich hinzufügen'"
              class="p-1.5 rounded-full transition-colors flex-shrink-0"
              :class="isSelectedForCompare(`${model.brandDisplayName}/${model.modelUrlSlug}`)
                ? 'bg-green-500 text-white'
                : selectedForCompare.length >= MAX_COMPARE
                  ? 'bg-gray-100 text-gray-300 cursor-not-allowed'
                  : 'bg-gray-100 text-gray-400 hover:bg-green-100 hover:text-green-600'"
            >
              <CheckIcon v-if="isSelectedForCompare(`${model.brandDisplayName}/${model.modelUrlSlug}`)" class="h-4 w-4" />
              <ArrowsRightLeftIcon v-else class="h-4 w-4" />
            </button>
          </div>
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

      <!-- Brand Grid -->
      <div v-if="!loading && (availableBrands.length > 0 || brandFillItems.length > 0)" class="mb-8 mt-8">
        <h2 class="text-lg font-semibold text-gray-800 mb-3">Alle Marken</h2>
        <div class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-3">
          <a
            v-for="brand in availableBrands"
            :key="brand"
            :href="`/modelle/${brand}`"
            class="brand-card bg-white rounded-xl border border-gray-200 px-4 py-3 flex items-center gap-2 hover:border-green-500 hover:shadow-sm transition-all"
          >
            <TruckIcon class="h-5 w-5 text-gray-400 flex-shrink-0" />
            <span class="text-sm font-medium text-gray-800 truncate">{{ brand }}</span>
          </a>
          <!-- Fallback brands without community data yet -->
          <a
            v-for="brand in brandFillItems"
            :key="`fallback/${brand}`"
            :href="`/modelle/${brand}`"
            class="brand-card bg-white rounded-xl border border-gray-100 px-4 py-3 flex items-center gap-2 hover:border-green-400 hover:shadow-sm transition-all opacity-70"
          >
            <TruckIcon class="h-5 w-5 text-gray-300 flex-shrink-0" />
            <span class="text-sm font-medium text-gray-500 truncate">{{ brand }}</span>
          </a>
        </div>
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
import { getTopModels, type TopModelPreview } from '../api/publicModelService'
import { TruckIcon, ChartBarIcon, ArrowTrendingUpIcon, ArrowsRightLeftIcon, XMarkIcon, CheckIcon, ArrowRightIcon } from '@heroicons/vue/24/outline'
import PublicNav from '../components/PublicNav.vue'

const router = useRouter()

const authStore = useAuthStore()
const loading = ref(true)
const modelsList = ref<TopModelPreview[]>([])
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
  return key.replace(/_/g, ' ')
}

function formatDelta(real: number, wltp: number): string {
  const pct = ((real - wltp) / wltp) * 100
  const sign = pct > 0 ? '+' : ''
  return `${sign}${pct.toFixed(0)}%`
}

const isAuthenticated = computed(() => authStore.isAuthenticated())
const currentYear = new Date().getFullYear()

type ModelInfo = TopModelPreview

interface FallbackModel {
  brand: string
  model: string
  displayName: string
}

// Popular EV brands in Germany — fallback when fewer than 20 brands have community data
const POPULAR_DE_BRANDS_FALLBACK = [
  'Tesla', 'Volkswagen', 'Hyundai', 'BMW', 'Audi', 'Kia', 'Mercedes-Benz',
  'Škoda', 'Polestar', 'MG Motor', 'Renault', 'Volvo', 'Ford', 'Porsche',
  'Opel', 'Cupra', 'Nissan', 'Fiat', 'Dacia', 'BYD',
]

const brandFillItems = computed((): string[] => {
  const needed = 20 - availableBrands.value.length
  if (needed <= 0) return []
  const existing = new Set(availableBrands.value.map(b => b.toLowerCase()))
  return POPULAR_DE_BRANDS_FALLBACK
    .filter(b => !existing.has(b.toLowerCase()))
    .slice(0, needed)
})

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

const modelsWithData = computed((): ModelInfo[] => modelsList.value)

const availableBrands = computed(() => {
  const brands = new Set(modelsWithData.value.map(m => m.brandDisplayName))
  return Array.from(brands).sort()
})

const filteredModels = computed(() => {
  if (selectedBrands.value.length === 0) {
    return modelsWithData.value
  }
  return modelsWithData.value.filter(m => selectedBrands.value.includes(m.brandDisplayName))
})

// Fill up to 12 models on mobile with popular fallbacks (only when no brand filter active)
const mobileFillModels = computed((): FallbackModel[] => {
  if (selectedBrands.value.length > 0) return []
  const needed = 12 - filteredModels.value.length
  if (needed <= 0) return []
  const normalize = (s: string) => s.replace(/ /g, '_').toLowerCase()
  const existingSlugs = new Set(filteredModels.value.map(m => normalize(`${m.brandDisplayName}/${m.modelUrlSlug}`)))
  return POPULAR_DE_FALLBACK
    .filter(f => !existingSlugs.has(normalize(`${f.brand}/${f.model}`)))
    .slice(0, needed)
})

// Reactive JSON-LD: brands first (from live data), then popular model fallbacks
const itemListJsonLd = computed(() => {
  const BASE = 'https://ev-monitor.net'
  const brandItems = availableBrands.value.map((brand, i) => ({
    '@type': 'ListItem',
    position: i + 1,
    name: `${brand} Elektroautos – Realer Verbrauch`,
    url: `${BASE}/modelle/${brand}`
  }))

  // Append popular model entries after brands (for long-tail keywords)
  const POPULAR_MODELS = [
    { name: 'Tesla Model 3', url: `${BASE}/modelle/Tesla/Model_3` },
    { name: 'Tesla Model Y', url: `${BASE}/modelle/Tesla/Model_Y` },
    { name: 'VW ID.3', url: `${BASE}/modelle/Volkswagen/ID.3` },
    { name: 'VW ID.4', url: `${BASE}/modelle/Volkswagen/ID.4` },
    { name: 'Hyundai Ioniq 5', url: `${BASE}/modelle/Hyundai/Ioniq_5` },
    { name: 'Hyundai Ioniq 6', url: `${BASE}/modelle/Hyundai/Ioniq_6` },
    { name: 'BMW i4', url: `${BASE}/modelle/BMW/i4` },
    { name: 'Audi Q4 e-tron', url: `${BASE}/modelle/Audi/Q4_e-tron` },
    { name: 'Kia EV6', url: `${BASE}/modelle/Kia/EV6` },
    { name: 'Polestar 2', url: `${BASE}/modelle/Polestar/Polestar_2` },
  ]
  const offset = brandItems.length
  const modelItems = POPULAR_MODELS.map((m, i) => ({
    '@type': 'ListItem',
    position: offset + i + 1,
    name: m.name,
    url: m.url
  }))

  return {
    '@context': 'https://schema.org',
    '@type': 'ItemList',
    name: 'Elektroauto Marken & Modelle – Realer Verbrauch & WLTP Vergleich',
    description: 'Community-Verbrauchsdaten für alle Elektroautos im Vergleich zum WLTP-Wert.',
    itemListElement: brandItems.length > 0 ? [...brandItems, ...modelItems] : modelItems
  }
})

const breadcrumbJsonLd = {
  '@context': 'https://schema.org',
  '@type': 'BreadcrumbList',
  itemListElement: [
    { '@type': 'ListItem', position: 1, name: 'EV Monitor', item: 'https://ev-monitor.net' },
    { '@type': 'ListItem', position: 2, name: 'Elektroautos im Vergleich', item: 'https://ev-monitor.net/modelle' },
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
    { type: 'application/ld+json', innerHTML: () => JSON.stringify(itemListJsonLd.value) },
    { type: 'application/ld+json', innerHTML: JSON.stringify(breadcrumbJsonLd) },
  ]
})

onMounted(async () => {
  try {
    modelsList.value = await getTopModels(50)
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
/* 3D press effect for brand cards */
.brand-card {
  box-shadow: 0 3px 0 0 rgba(0,0,0,0.08);
  transform: translateY(0);
  transition: transform 0.08s ease, box-shadow 0.08s ease, border-color 0.15s ease;
}
.brand-card:active {
  box-shadow: 0 1px 0 0 rgba(0,0,0,0.08);
  transform: translateY(2px);
  transition: transform 0.05s ease, box-shadow 0.05s ease;
}

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
