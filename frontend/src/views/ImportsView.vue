<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowDownTrayIcon, BoltIcon, ExclamationTriangleIcon } from '@heroicons/vue/24/outline'
import SpritMonitorImport from '../components/SpritMonitorImport.vue'
import GoeIntegration from '../components/GoeIntegration.vue'
import TeslaFleetIntegration from '../components/TeslaFleetIntegration.vue'
import TeslaLoggerImportModal from '../components/TeslaLoggerImportModal.vue'
import { carService, type Car } from '../api/carService'

const route = useRoute()

type Tab = 'spritmonitor' | 'goe' | 'wallbox' | 'tesla'
const VALID_TABS: Tab[] = ['spritmonitor', 'goe', 'wallbox', 'tesla']
const activeTab = ref<Tab>(
  VALID_TABS.includes(route.query.tab as Tab) ? (route.query.tab as Tab) : 'spritmonitor'
)
const showSpritMonitorModal = ref(false)
const teslaLoggerCarId = ref<string | null>(null)
const cars = ref<Car[]>([])
const loading = ref(true)

onMounted(async () => {
  try {
    cars.value = await carService.getCars()
    // Small delay for smooth fade-in
    await new Promise(resolve => setTimeout(resolve, 100))
  } catch { /* ignore */ } finally {
    loading.value = false
  }
})

const hasActiveTesla = computed(() =>
  cars.value.some(c => c.brand?.toLowerCase() === 'tesla' && c.status === 'ACTIVE')
)

const teslaCars = computed(() =>
  cars.value.filter(c => c.brand?.toLowerCase() === 'tesla' && c.status === 'ACTIVE')
)
</script>

<template>
  <div class="md:max-w-3xl md:mx-auto px-4 md:px-0 py-6">
    <Transition name="fade" mode="out-in">
      <div v-if="!loading">
        <!-- Header -->
        <div class="mb-6">
          <div class="flex items-center gap-3 mb-2">
            <ArrowDownTrayIcon class="h-7 w-7 text-green-600" />
            <h1 class="text-2xl font-bold text-gray-900">Ladevorgänge importieren</h1>
          </div>
          <p class="text-gray-600 text-sm">
            Importiere deine bisherigen Ladevorgänge oder verbinde eine Heimwallbox für automatischen Import.
          </p>
        </div>

        <!-- Tabs -->
        <div class="flex gap-1 bg-gray-100 rounded-xl p-1 mb-6">
          <button
            @click="activeTab = 'spritmonitor'"
            :class="[
              'flex-1 text-sm font-medium px-3 py-2 rounded-lg transition',
              activeTab === 'spritmonitor'
                ? 'bg-white shadow text-gray-900'
                : 'text-gray-600 hover:text-gray-900'
            ]"
          >
            Sprit-Monitor
          </button>
          <button
            @click="activeTab = 'goe'"
            :class="[
              'flex-1 text-sm font-medium px-3 py-2 rounded-lg transition',
              activeTab === 'goe'
                ? 'bg-white shadow text-gray-900'
                : 'text-gray-600 hover:text-gray-900'
            ]"
          >
            go-eCharger
          </button>
          <button
            @click="activeTab = 'wallbox'"
            :class="[
              'flex-1 text-sm font-medium px-3 py-2 rounded-lg transition',
              activeTab === 'wallbox'
                ? 'bg-white shadow text-gray-900'
                : 'text-gray-600 hover:text-gray-900'
            ]"
          >
            OCPP Wallbox
          </button>
          <button
            v-if="hasActiveTesla"
            @click="activeTab = 'tesla'"
            :class="[
              'flex-1 text-sm font-medium px-3 py-2 rounded-lg transition',
              activeTab === 'tesla'
                ? 'bg-white shadow text-gray-900'
                : 'text-gray-600 hover:text-gray-900'
            ]"
          >
            Tesla
          </button>
        </div>

        <!-- Tab: Sprit-Monitor -->
        <div v-if="activeTab === 'spritmonitor'" class="space-y-4">
          <div class="bg-white border border-gray-200 rounded-xl p-6 space-y-4">
            <div class="flex items-start gap-4">
              <div class="bg-indigo-600 rounded-lg p-2 shrink-0">
                <ArrowDownTrayIcon class="h-5 w-5 text-white" />
              </div>
              <div>
                <h2 class="font-semibold text-gray-900">Sprit-Monitor Import</h2>
                <p class="text-sm text-gray-600 mt-1">
                  Importiere deine komplette Ladehistorie aus Sprit-Monitor — inklusive Kosten und Verbrauch.
                  Dein API Token wird nach dem Import sofort verworfen, nichts wird gespeichert.
                </p>
              </div>
            </div>

            <ul class="text-sm text-gray-600 space-y-1 list-disc list-inside">
              <li>Komplette Ladehistorie aus Sprit-Monitor</li>
              <li>Kosten, kWh, Datum — alles inklusive</li>
              <li>Fahrzeugzuordnung wählbar</li>
              <li>Einmalig, API Token wird nicht gespeichert</li>
            </ul>

            <button
              @click="showSpritMonitorModal = true"
              class="flex items-center gap-2 bg-indigo-600 text-white px-5 py-2.5 rounded-lg font-medium text-sm hover:bg-indigo-700 transition"
            >
              <ArrowDownTrayIcon class="h-4 w-4" />
              Sprit-Monitor Import starten
            </button>
          </div>
        </div>

        <!-- Tab: go-eCharger -->
        <div v-if="activeTab === 'goe'">
          <div class="bg-white border border-gray-200 rounded-xl p-6">
            <div class="flex items-start gap-4 mb-5">
              <div class="bg-green-600 rounded-lg p-2 shrink-0">
                <BoltIcon class="h-5 w-5 text-white" />
              </div>
              <div>
                <h2 class="font-semibold text-gray-900">go-eCharger Cloud <span class="text-xs bg-blue-100 text-blue-800 px-2 py-0.5 rounded-full font-medium ml-2">BETA</span></h2>
                <p class="text-sm text-gray-600 mt-1">
                  Verbinde deinen go-eCharger über die Cloud API für vollautomatischen Import.
                  Alle 30 Sekunden wird der Ladestatus geprüft und abgeschlossene Sessions automatisch eingetragen.
                </p>
              </div>
            </div>

            <div class="flex items-start gap-2 bg-blue-50 border border-blue-200 rounded-lg p-3 mb-5">
              <ExclamationTriangleIcon class="h-4 w-4 text-blue-600 mt-0.5 shrink-0" />
              <p class="text-xs text-blue-800">
                <strong>Beta-Feature:</strong> Diese Integration befindet sich in der Beta-Phase und kann noch Fehler enthalten. Bei Problemen melde dich bitte.
              </p>
            </div>

            <div class="flex items-start gap-2 bg-amber-50 border border-amber-200 rounded-lg p-3 mb-5">
              <ExclamationTriangleIcon class="h-4 w-4 text-amber-600 mt-0.5 shrink-0" />
              <p class="text-xs text-amber-800">
                <strong>Hinweis:</strong> Die Cloud API lässt sich parallel zu allen anderen go-e Features (PV-Überschussladen,
                Lastmanagement) nutzen. Keine OCPP-Konfiguration nötig.
              </p>
            </div>

            <GoeIntegration />
          </div>
        </div>

        <!-- Tab: OCPP Wallbox -->
        <div v-if="activeTab === 'wallbox'">
          <div class="bg-white border border-gray-200 rounded-xl p-6 space-y-5">
            <div class="flex items-start gap-4">
              <div class="bg-gray-800 rounded-lg p-2 shrink-0">
                <BoltIcon class="h-5 w-5 text-white" />
              </div>
              <div>
                <h2 class="font-semibold text-gray-900">OCPP Wallbox <span class="text-xs bg-blue-100 text-blue-800 px-2 py-0.5 rounded-full font-medium ml-2">BETA</span></h2>
                <p class="text-sm text-gray-600 mt-1">
                  Richte deine Wallbox über das OCPP-Protokoll ein. Ladevorgänge werden dann automatisch
                  nach jeder Session importiert.
                </p>
              </div>
            </div>

            <div class="flex items-start gap-2 bg-blue-50 border border-blue-200 rounded-lg p-3">
              <ExclamationTriangleIcon class="h-4 w-4 text-blue-600 mt-0.5 shrink-0" />
              <p class="text-xs text-blue-800">
                <strong>Beta-Feature:</strong> Diese Integration befindet sich in der Beta-Phase und kann noch Fehler enthalten. Bei Problemen melde dich bitte.
              </p>
            </div>

            <div class="flex items-start gap-2 bg-amber-50 border border-amber-200 rounded-xl p-4">
              <ExclamationTriangleIcon class="h-5 w-5 text-amber-600 mt-0.5 shrink-0" />
              <div class="text-sm text-amber-800 space-y-1">
                <p class="font-semibold">Wichtiger Hinweis vor der Einrichtung</p>
                <p>
                  Bei OCPP wird deine Wallbox auf <strong>unseren Server</strong> als Steuereinheit umgekonfiguriert.
                  Das bedeutet: Herstellerspezifische Cloud-Features wie <strong>PV-Überschussladen</strong>
                  oder <strong>Lastmanagement</strong> funktionieren danach nicht mehr.
                </p>
                <p>Empfehlung für go-eCharger: Nutze stattdessen den <button @click="activeTab = 'goe'" class="underline font-medium cursor-pointer">Cloud API Import</button> — parallel zu allen Wallbox-Features.</p>
              </div>
            </div>

            <router-link
              to="/wallbox"
              class="inline-flex items-center gap-2 bg-gray-800 text-white px-5 py-2.5 rounded-lg font-medium text-sm hover:bg-gray-700 transition"
            >
              <BoltIcon class="h-4 w-4" />
              OCPP Wallbox konfigurieren
            </router-link>
          </div>
        </div>

        <!-- Tab: Tesla -->
        <div v-if="activeTab === 'tesla' && hasActiveTesla" class="space-y-4">
          <TeslaFleetIntegration />

          <!-- TeslaLogger Manual Import -->
          <div class="bg-white border border-gray-200 rounded-xl p-6 space-y-4">
            <div class="flex items-start gap-4">
              <div class="bg-gray-700 rounded-lg p-2 shrink-0">
                <ArrowDownTrayIcon class="h-5 w-5 text-white" />
              </div>
              <div>
                <h2 class="font-semibold text-gray-900">Manueller Datenlogger-Import</h2>
                <p class="text-sm text-gray-600 mt-1">
                  Importiere vergangene Ladevorgänge aus <strong>TeslaMate</strong>, <strong>TeslaLogger</strong>,
                  <strong>TeslaFi</strong> oder einer anderen Quelle — als CSV oder JSON in unserem Format.
                </p>
              </div>
            </div>

            <ul class="text-sm text-gray-600 space-y-1 list-disc list-inside">
              <li>Vergangene Sessions inklusive Tachostand</li>
              <li>CSV oder JSON — Format wird vorgegeben</li>
              <li>Datum-Erkennung: ISO 8601, deutsch, US, Unix-Timestamp</li>
              <li>Standort: Koordinaten (Lat/Lon) oder Ortsname</li>
            </ul>

            <div v-if="teslaCars.length > 1" class="space-y-1.5">
              <label class="block text-sm font-medium text-gray-700">Fahrzeug auswählen</label>
              <select
                v-model="teslaLoggerCarId"
                class="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-green-500"
              >
                <option value="" disabled>Fahrzeug auswählen…</option>
                <option v-for="car in teslaCars" :key="car.id" :value="car.id">
                  {{ car.brand }} {{ car.model }}
                </option>
              </select>
            </div>

            <button
              @click="teslaLoggerCarId = teslaCars.length === 1 ? teslaCars[0].id : (teslaLoggerCarId || null)"
              :disabled="teslaCars.length > 1 && !teslaLoggerCarId"
              class="flex items-center gap-2 bg-gray-800 text-white px-5 py-2.5 rounded-lg font-medium text-sm hover:bg-gray-700 disabled:opacity-40 disabled:cursor-not-allowed transition"
            >
              <ArrowDownTrayIcon class="h-4 w-4" />
              Import starten
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </div>

  <!-- Sprit-Monitor Import Modal -->
  <SpritMonitorImport v-if="showSpritMonitorModal" @close="showSpritMonitorModal = false" />

  <!-- TeslaLogger Import Modal -->
  <TeslaLoggerImportModal
    v-if="teslaLoggerCarId"
    :car-id="teslaLoggerCarId"
    @close="teslaLoggerCarId = null"
    @imported="teslaLoggerCarId = null"
  />
</template>

<style scoped>
.fade-enter-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from {
  opacity: 0;
}

.fade-enter-to {
  opacity: 1;
}
</style>
