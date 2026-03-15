<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ArrowDownTrayIcon, BoltIcon, ExclamationTriangleIcon, CodeBracketIcon, TrashIcon, ClipboardDocumentIcon, CheckIcon } from '@heroicons/vue/24/outline'
import SpritMonitorImport from '../components/SpritMonitorImport.vue'
import GoeIntegration from '../components/GoeIntegration.vue'
import TeslaFleetIntegration from '../components/TeslaFleetIntegration.vue'
import TeslaLoggerImportModal from '../components/TeslaLoggerImportModal.vue'
import CarSelectDropdown from '../components/CarSelectDropdown.vue'
import { carService, type Car } from '../api/carService'
import { useImportsTab } from '../composables/useImportsTab'
import { apiKeyService, type ApiKeyResponse, type ApiKeyCreatedResponse } from '../api/apiKeyService'

const { activeTab } = useImportsTab()
const showSpritMonitorModal = ref(false)
const teslaLoggerCarId = ref<string | null>(null)
const cars = ref<Car[]>([])
const loading = ref(true)

onMounted(async () => {
  try {
    cars.value = await carService.getCars() ?? []
    await new Promise(resolve => setTimeout(resolve, 100))
  } catch { /* ignore */ } finally {
    loading.value = false
  }
  fetchApiKeys()
})

// ── API Keys ──────────────────────────────────────────────────────────────────
const apiKeys = ref<ApiKeyResponse[]>([])
const newKeyName = ref('')
const createdKey = ref<ApiKeyCreatedResponse | null>(null)
const keyCopied = ref(false)
const apiKeyLoading = ref(false)
const deletingKeyId = ref<string | null>(null)
const apiKeyMessage = ref<{ type: 'success' | 'error', text: string } | null>(null)

const fetchApiKeys = async () => {
  try { apiKeys.value = await apiKeyService.listKeys() } catch { /* ignore */ }
}

const createApiKey = async () => {
  if (!newKeyName.value.trim()) return
  apiKeyLoading.value = true
  apiKeyMessage.value = null
  try {
    createdKey.value = await apiKeyService.createKey(newKeyName.value.trim())
    newKeyName.value = ''
    await fetchApiKeys()
  } catch (error: any) {
    apiKeyMessage.value = { type: 'error', text: error.response?.data?.error || 'API Key konnte nicht erstellt werden' }
  } finally {
    apiKeyLoading.value = false
  }
}

const deleteApiKey = async (id: string, name: string) => {
  if (!window.confirm(`API Key "${name || 'ohne Name'}" wirklich widerrufen? Alle Integrationen die diesen Key nutzen hören sofort auf zu funktionieren.`)) return
  deletingKeyId.value = id
  try {
    await apiKeyService.deleteKey(id)
    apiKeys.value = apiKeys.value.filter(k => k.id !== id)
    if (createdKey.value?.id === id) createdKey.value = null
    apiKeyMessage.value = { type: 'success', text: 'API Key widerrufen.' }
    setTimeout(() => { apiKeyMessage.value = null }, 3000)
  } catch {
    apiKeyMessage.value = { type: 'error', text: 'Key konnte nicht gelöscht werden' }
  } finally {
    deletingKeyId.value = null
  }
}

const copyApiKey = async () => {
  if (!createdKey.value) return
  try {
    await navigator.clipboard.writeText(createdKey.value.plaintextKey)
    keyCopied.value = true
    setTimeout(() => { keyCopied.value = false }, 2000)
  } catch { /* ignore */ }
}

const formatDate = (dateStr: string | null) => {
  if (!dateStr) return 'Noch nie'
  return new Date(dateStr).toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' })
}

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
        <div class="flex gap-1">
          <button
            v-for="tab in ([
              { id: 'spritmonitor', label: 'Sprit-Monitor' },
              { id: 'goe', label: 'go-eCharger' },
              { id: 'wallbox', label: 'OCPP Wallbox' },
              { id: 'tesla', label: 'Tesla' },
              { id: 'api', label: 'API' },
            ] as const)"
            :key="tab.id"
            @click="activeTab = tab.id"
            :class="[
              'shrink-0 px-4 py-2.5 text-sm font-medium border-t border-l border-r rounded-t-lg transition-colors relative z-10',
              activeTab === tab.id
                ? 'bg-white border-gray-200 text-gray-900 -mb-px pb-[calc(0.625rem+1px)]'
                : 'bg-gray-50 border-gray-200 text-gray-500 hover:text-gray-700 hover:bg-gray-100'
            ]"
          >{{ tab.label }}</button>
        </div>
        <!-- Tab panel -->
        <div class="border border-gray-200 rounded-b-xl rounded-tr-xl bg-white">
        <!-- Tab: Sprit-Monitor -->
        <div v-if="activeTab === 'spritmonitor'" class="p-6 space-y-4">
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
            class="btn-3d flex items-center gap-2 bg-indigo-600 text-white px-5 py-2.5 rounded-lg font-medium text-sm hover:bg-indigo-700 transition"
          >
            <ArrowDownTrayIcon class="h-4 w-4" />
            Sprit-Monitor Import starten
          </button>
        </div>

        <!-- Tab: go-eCharger -->
        <div v-if="activeTab === 'goe'" class="p-6 space-y-5">
          <div class="flex items-start gap-4">
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
          <div class="flex items-start gap-2 bg-blue-50 border border-blue-200 rounded-lg p-3">
            <ExclamationTriangleIcon class="h-4 w-4 text-blue-600 mt-0.5 shrink-0" />
            <p class="text-xs text-blue-800">
              <strong>Beta-Feature:</strong> Diese Integration befindet sich in der Beta-Phase und kann noch Fehler enthalten. Bei Problemen melde dich bitte.
            </p>
          </div>
          <div class="flex items-start gap-2 bg-amber-50 border border-amber-200 rounded-lg p-3">
            <ExclamationTriangleIcon class="h-4 w-4 text-amber-600 mt-0.5 shrink-0" />
            <p class="text-xs text-amber-800">
              <strong>Hinweis:</strong> Die Cloud API lässt sich parallel zu allen anderen go-e Features (PV-Überschussladen,
              Lastmanagement) nutzen. Keine OCPP-Konfiguration nötig.
            </p>
          </div>
          <GoeIntegration />
        </div>

        <!-- Tab: OCPP Wallbox -->
        <div v-if="activeTab === 'wallbox'" class="p-6 space-y-5">
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
            class="btn-3d inline-flex items-center gap-2 bg-gray-800 text-white px-5 py-2.5 rounded-lg font-medium text-sm hover:bg-gray-700 transition"
          >
            <BoltIcon class="h-4 w-4" />
            OCPP Wallbox konfigurieren
          </router-link>
        </div>

        <!-- Tab: Tesla -->
        <div v-if="activeTab === 'tesla'" class="divide-y divide-gray-100">
          <div v-if="!hasActiveTesla" class="p-6">
            <div class="flex items-start gap-4">
              <div class="bg-gray-700 rounded-lg p-2 shrink-0">
                <BoltIcon class="h-5 w-5 text-white" />
              </div>
              <div>
                <h2 class="font-semibold text-gray-900">Tesla Integration</h2>
                <p class="text-sm text-gray-600 mt-2">
                  Um die Tesla-Integration zu nutzen, musst du zuerst ein Tesla-Fahrzeug in deiner
                  <router-link to="/cars" class="text-indigo-600 hover:underline font-medium">Fahrzeugverwaltung</router-link>
                  anlegen.
                </p>
              </div>
            </div>
          </div>
          <template v-else>
          <div class="p-6"><TeslaFleetIntegration /></div>
          <!-- TeslaLogger Manual Import -->
          <div class="p-6 space-y-4">
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
              <CarSelectDropdown :cars="teslaCars" v-model="teslaLoggerCarId" />
            </div>
            <button
              @click="teslaLoggerCarId = teslaCars.length === 1 ? teslaCars[0].id : (teslaLoggerCarId || null)"
              :disabled="teslaCars.length > 1 && !teslaLoggerCarId"
              class="btn-3d w-full flex items-center justify-center gap-2 bg-gray-800 text-white px-5 py-2.5 rounded-lg font-medium text-sm hover:bg-gray-700 disabled:opacity-40 disabled:cursor-not-allowed transition"
            >
              <ArrowDownTrayIcon class="h-4 w-4" />
              Import starten
            </button>
          </div>
          </template>
        </div>
        <!-- Tab: API -->
        <div v-if="activeTab === 'api'" class="p-6 space-y-5">
          <div class="flex items-start gap-4">
            <div class="bg-indigo-600 rounded-lg p-2 shrink-0">
              <CodeBracketIcon class="h-5 w-5 text-white" />
            </div>
            <div>
              <h2 class="font-semibold text-gray-900">REST API Upload</h2>
              <p class="text-sm text-gray-600 mt-1">
                Verbinde Wallboxen, Skripte oder Home-Automation direkt mit EV Monitor. Ladevorgänge werden automatisch importiert sobald dein Tool sie sendet.
              </p>
            </div>
          </div>

          <!-- Endpoint Info -->
          <div class="p-4 bg-indigo-50 border border-indigo-200 rounded-lg text-sm text-indigo-800">
            <p class="font-mono text-xs bg-white border border-indigo-200 rounded px-2 py-1.5 mb-2 break-all">
              POST https://ev-monitor.net/api/v1/sessions<br>
              Authorization: Bearer evm_&lt;dein-key&gt;
            </p>
            <p class="text-xs mb-1">
              Pflichtfelder: <code class="bg-white px-1 rounded">date</code>, <code class="bg-white px-1 rounded">kwh</code>
            </p>
            <p class="text-xs">
              Optional: <code class="bg-white px-1 rounded">odometer_km</code>, <code class="bg-white px-1 rounded">soc_after</code>, <code class="bg-white px-1 rounded">cost_eur</code>, <code class="bg-white px-1 rounded">duration_min</code>, <code class="bg-white px-1 rounded">location</code>, <code class="bg-white px-1 rounded">charging_type</code> (AC/DC)
            </p>
            <p class="text-xs mt-1">
              Duplikate werden automatisch erkannt und übersprungen — anhand Tachostand (±1h) oder Zeitpunkt (±30 Min).
            </p>
            <a href="/swagger-ui/index.html" target="_blank" class="inline-block mt-2 text-indigo-700 hover:underline font-medium text-xs">
              Vollständige API Dokumentation →
            </a>
          </div>

          <!-- Message -->
          <div v-if="apiKeyMessage" :class="[
            'p-3 rounded-lg text-sm',
            apiKeyMessage.type === 'success' ? 'bg-green-50 border border-green-200 text-green-700' : 'bg-red-50 border border-red-200 text-red-700'
          ]">{{ apiKeyMessage.text }}</div>

          <!-- Newly created key -->
          <div v-if="createdKey" class="p-4 bg-green-50 border border-green-300 rounded-lg">
            <p class="font-semibold text-green-800 mb-1 text-sm">Neuer API Key — nur jetzt sichtbar!</p>
            <div class="flex items-center gap-2">
              <code class="flex-1 bg-white border border-green-300 rounded px-3 py-2 text-sm font-mono break-all">{{ createdKey.plaintextKey }}</code>
              <button @click="copyApiKey" class="flex-shrink-0 p-2 rounded-lg bg-green-600 text-white hover:bg-green-700 transition" title="Kopieren">
                <CheckIcon v-if="keyCopied" class="h-5 w-5" />
                <ClipboardDocumentIcon v-else class="h-5 w-5" />
              </button>
            </div>
            <p class="text-xs text-green-700 mt-2">Speicher diesen Key jetzt — er wird nicht noch einmal angezeigt.</p>
          </div>

          <!-- Create key -->
          <div class="flex gap-2">
            <input
              v-model="newKeyName"
              type="text"
              placeholder="Key-Name, z.B. OpenWB Zuhause"
              maxlength="100"
              class="flex-1 px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-indigo-500 focus:border-indigo-500"
              @keyup.enter="createApiKey" />
            <button
              @click="createApiKey"
              :disabled="apiKeyLoading || !newKeyName.trim()"
              class="btn-3d px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 disabled:opacity-50 transition text-sm font-medium whitespace-nowrap">
              + Key erstellen
            </button>
          </div>

          <!-- Key list -->
          <div v-if="apiKeys.length === 0" class="text-sm text-gray-500 italic">Noch keine API Keys vorhanden.</div>
          <div v-else class="space-y-2">
            <div
              v-for="key in apiKeys"
              :key="key.id"
              class="flex items-center justify-between p-3 bg-gray-50 border border-gray-200 rounded-lg gap-2">
              <div class="min-w-0">
                <p class="font-medium text-gray-800 text-sm truncate">{{ key.name || '(kein Name)' }}</p>
                <p class="text-xs text-gray-500">
                  <code class="font-mono">{{ key.keyPrefix }}…</code>
                  · Zuletzt: {{ formatDate(key.lastUsedAt) }}
                  · Erstellt: {{ formatDate(key.createdAt) }}
                </p>
              </div>
              <button
                @click="deleteApiKey(key.id, key.name)"
                :disabled="deletingKeyId === key.id"
                class="flex-shrink-0 p-2 text-red-500 hover:text-red-700 hover:bg-red-50 rounded-lg transition disabled:opacity-50"
                title="Key widerrufen">
                <TrashIcon class="h-5 w-5" />
              </button>
            </div>
          </div>
        </div>

        </div><!-- end tab panel -->
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
