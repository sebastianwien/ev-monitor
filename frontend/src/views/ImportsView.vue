<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '../stores/auth'
import { ArrowDownTrayIcon, BoltIcon, ExclamationTriangleIcon, CodeBracketIcon, TrashIcon, ClipboardDocumentIcon, CheckIcon, ChevronDownIcon } from '@heroicons/vue/24/outline'
import SpritMonitorImport from '../components/SpritMonitorImport.vue'
import GoeIntegration from '../components/GoeIntegration.vue'
import TeslaFleetIntegration from '../components/TeslaFleetIntegration.vue'
import SmartcarIntegration from '../components/SmartcarIntegration.vue'
import ManualImportModal from '../components/ManualImportModal.vue'
import TronityImport from '../components/TronityImport.vue'
import CarSelectDropdown from '../components/CarSelectDropdown.vue'
import type { Car } from '../api/carService'
import { useCarStore } from '../stores/car'
import { useImportsTab } from '../composables/useImportsTab'
import { apiKeyService, type ApiKeyResponse, type ApiKeyCreatedResponse } from '../api/apiKeyService'
import { analytics } from '../services/analytics'
import DemoImportsModal from '../components/DemoImportsModal.vue'

const { t } = useI18n()
const { activeTab, toggle } = useImportsTab()
const authStore = useAuthStore()
const carStore = useCarStore()
const showSpritMonitorModal = ref(false)
const manualImportCarId = ref<string | null>(null)
const showManualImportModal = ref(false)
const cars = ref<Car[]>([])
const loading = ref(true)

onMounted(async () => {
  const params = new URLSearchParams(window.location.search)
  if (params.get('smartcar-connected') || params.get('smartcar-error')) {
    activeTab.value = 'smartcar'
    // URL-Bereinigung überlässt SmartcarIntegration.vue (liest den Param selbst aus)
  }
  try {
    cars.value = await carStore.getCars() ?? []
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
    analytics.trackApiKeyCreated()
    newKeyName.value = ''
    await fetchApiKeys()
  } catch (error: any) {
    apiKeyMessage.value = { type: 'error', text: error.response?.data?.error || t('imports.api_err_create') }
  } finally {
    apiKeyLoading.value = false
  }
}

const deleteApiKey = async (id: string, name: string) => {
  if (!window.confirm(t('imports.api_confirm_revoke', { name: name || t('imports.api_confirm_name_fallback') }))) return
  deletingKeyId.value = id
  try {
    await apiKeyService.deleteKey(id)
    analytics.trackApiKeyDeleted()
    apiKeys.value = apiKeys.value.filter(k => k.id !== id)
    if (createdKey.value?.id === id) createdKey.value = null
    apiKeyMessage.value = { type: 'success', text: t('imports.api_revoked') }
    setTimeout(() => { apiKeyMessage.value = null }, 3000)
  } catch {
    apiKeyMessage.value = { type: 'error', text: t('imports.api_err_delete') }
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
  if (!dateStr) return t('imports.api_never')
  return new Date(dateStr).toLocaleDateString(undefined, { day: '2-digit', month: '2-digit', year: 'numeric' })
}

async function toggleMergeSessions(key: ApiKeyResponse) {
  try {
    const updated = await apiKeyService.updateMergeSessions(key.id, !key.mergeSessions)
    const idx = apiKeys.value.findIndex(k => k.id === key.id)
    if (idx !== -1) apiKeys.value[idx] = updated
  } catch (e) {
    console.error('Failed to toggle merge sessions', e)
  }
}

const hasActiveTesla = computed(() =>
  Array.isArray(cars.value) && cars.value.some(c => c.brand?.toLowerCase() === 'tesla' && c.status === 'ACTIVE')
)

const activeCars = computed(() =>
  Array.isArray(cars.value) ? cars.value.filter(c => c.status === 'ACTIVE') : []
)
</script>

<template>
  <div class="md:max-w-5xl md:mx-auto md:p-6">
    <Transition name="fade" mode="out-in">
      <div v-if="!loading" class="bg-white dark:bg-gray-800 md:rounded-xl md:shadow-lg p-4 md:p-6">
        <!-- Header -->
        <div class="mb-6">
          <div class="flex items-center gap-3 mb-2">
            <ArrowDownTrayIcon class="h-7 w-7 text-green-600" />
            <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">{{ t('imports.title') }}</h1>
          </div>
          <p class="text-gray-600 dark:text-gray-400 text-sm">
            {{ t('imports.subtitle') }}
          </p>
        </div>

        <!-- Accordion -->
        <div class="border border-gray-200 dark:border-gray-600 rounded-xl divide-y divide-gray-200 dark:divide-gray-700 overflow-hidden shadow-lg dark:shadow-[0_8px_32px_rgba(0,0,0,0.5)]">

        <!-- 1. SMARTCAR -->
        <div>
          <button
            @click="toggle('smartcar'); analytics.trackImportTabClicked('smartcar')"
            class="w-full flex items-center gap-3 px-4 py-4 text-left hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors"
          >
            <div class="shrink-0 bg-white dark:bg-gray-700 border border-gray-200 dark:border-gray-600 rounded-lg p-2 w-10 h-10 flex items-center justify-center">
              <img src="/logos/smartcar.svg" alt="Smartcar" class="h-5 w-auto dark:invert" />
            </div>
            <div class="flex-1 min-w-0">
              <div class="flex items-center gap-2 flex-wrap">
                <span class="font-medium text-gray-900 dark:text-gray-100 text-sm">{{ t('imports.tab_smartcar') }}</span>
                <span v-if="!authStore.isAdmin && !authStore.isPremium" class="text-[10px] bg-amber-100 text-amber-800 dark:bg-amber-900/40 dark:text-amber-300 font-semibold px-1.5 py-0.5 rounded-full leading-none">Premium</span>
              </div>
            </div>
            <ChevronDownIcon :class="['h-5 w-5 text-gray-400 shrink-0 transition-transform duration-200', activeTab === 'smartcar' ? 'rotate-180' : '']" />
          </button>
          <Transition name="accordion">
            <div v-if="activeTab === 'smartcar'" class="border-t border-gray-100 dark:border-gray-700">
              <SmartcarIntegration />
            </div>
          </Transition>
        </div>

        <!-- 2. API -->
        <div>
          <button
            @click="toggle('api'); analytics.trackImportTabClicked('api')"
            class="w-full flex items-center gap-3 px-4 py-4 text-left hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors"
          >
            <div class="shrink-0 bg-indigo-600 rounded-lg p-2">
              <CodeBracketIcon class="h-5 w-5 text-white" />
            </div>
            <div class="flex-1 min-w-0">
              <span class="font-medium text-gray-900 dark:text-gray-100 text-sm">{{ t('imports.tab_api') }}</span>
            </div>
            <ChevronDownIcon :class="['h-5 w-5 text-gray-400 shrink-0 transition-transform duration-200', activeTab === 'api' ? 'rotate-180' : '']" />
          </button>
          <Transition name="accordion">
            <div v-if="activeTab === 'api'" class="border-t border-gray-100 dark:border-gray-700 p-4 space-y-5">
              <p class="text-sm text-gray-600 dark:text-gray-400">{{ t('imports.api_desc') }}</p>
              <div class="p-4 bg-indigo-50 dark:bg-indigo-900/20 border border-indigo-200 dark:border-indigo-700 rounded-lg text-sm text-indigo-800 dark:text-indigo-300">
                <p class="font-mono text-xs bg-white dark:bg-gray-700 border border-indigo-200 dark:border-indigo-700 rounded px-2 py-1.5 mb-2 break-all">
                  POST https://ev-monitor.net/api/v1/sessions<br>
                  Authorization: Bearer evm_&lt;dein-key&gt;
                </p>
                <p class="text-xs mb-1">{{ t('imports.api_required') }} <code class="bg-white dark:bg-gray-700 px-1 rounded">date</code>, <code class="bg-white dark:bg-gray-700 px-1 rounded">kwh</code></p>
                <p class="text-xs">{{ t('imports.api_optional') }} <code class="bg-white dark:bg-gray-700 px-1 rounded">odometer_km</code>, <code class="bg-white dark:bg-gray-700 px-1 rounded">soc_after</code>, <code class="bg-white dark:bg-gray-700 px-1 rounded">cost_eur</code>, <code class="bg-white dark:bg-gray-700 px-1 rounded">duration_min</code>, <code class="bg-white dark:bg-gray-700 px-1 rounded">location</code>, <code class="bg-white dark:bg-gray-700 px-1 rounded">charging_type</code> (AC/DC)</p>
                <p class="text-xs mt-1">{{ t('imports.api_dedup') }}</p>
                <a href="/swagger-ui/index.html" target="_blank" class="inline-block mt-2 text-indigo-700 hover:underline font-medium text-xs">{{ t('imports.api_docs') }}</a>
              </div>
              <div v-if="apiKeyMessage" :class="['p-3 rounded-lg text-sm', apiKeyMessage.type === 'success' ? 'bg-green-50 dark:bg-green-900/30 border border-green-200 dark:border-green-700 text-green-700 dark:text-green-300' : 'bg-red-50 dark:bg-red-900/30 border border-red-200 dark:border-red-700 text-red-700 dark:text-red-300']">{{ apiKeyMessage.text }}</div>
              <div v-if="createdKey" class="p-4 bg-green-50 dark:bg-green-900/30 border border-green-300 dark:border-green-700 rounded-lg">
                <p class="font-semibold text-green-800 dark:text-green-200 mb-1 text-sm">{{ t('imports.api_new_key_title') }}</p>
                <div class="flex items-center gap-2">
                  <code class="flex-1 bg-white dark:bg-gray-700 border border-green-300 dark:border-green-700 rounded px-3 py-2 text-sm font-mono break-all">{{ createdKey.plaintextKey }}</code>
                  <button @click="copyApiKey" class="flex-shrink-0 p-2 rounded-lg bg-green-600 text-white hover:bg-green-700 transition" :title="t('imports.api_copy_title')">
                    <CheckIcon v-if="keyCopied" class="h-5 w-5" />
                    <ClipboardDocumentIcon v-else class="h-5 w-5" />
                  </button>
                </div>
                <p class="text-xs text-green-700 dark:text-green-300 mt-2">{{ t('imports.api_save_key_hint') }}</p>
              </div>
              <div class="flex gap-2">
                <input v-model="newKeyName" type="text" :placeholder="t('imports.api_key_placeholder')" maxlength="100" class="flex-1 px-3 py-2 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-lg text-sm focus:ring-indigo-500 focus:border-indigo-500" @keyup.enter="createApiKey" />
                <button @click="createApiKey" :disabled="apiKeyLoading || !newKeyName.trim()" class="btn-3d px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 disabled:opacity-50 transition text-sm font-medium whitespace-nowrap">{{ t('imports.api_create_btn') }}</button>
              </div>
              <div v-if="apiKeys.length === 0" class="text-sm text-gray-500 dark:text-gray-400 italic">{{ t('imports.api_no_keys') }}</div>
              <div v-else class="space-y-2">
                <div v-for="key in apiKeys" :key="key.id" class="flex items-center justify-between p-3 bg-gray-50 dark:bg-gray-700 border border-gray-200 dark:border-gray-600 rounded-lg gap-2">
                  <div class="min-w-0 flex-1">
                    <p class="font-medium text-gray-800 dark:text-gray-200 text-sm truncate">{{ key.name || t('imports.api_no_name') }}</p>
                    <p class="text-xs text-gray-500 dark:text-gray-400">
                      <code class="font-mono">{{ key.keyPrefix }}…</code>
                      · {{ t('imports.api_last_used') }} {{ formatDate(key.lastUsedAt) }}
                      · {{ t('imports.api_created') }} {{ formatDate(key.createdAt) }}
                    </p>
                    <div class="flex items-center gap-2 mt-1">
                      <button @click="toggleMergeSessions(key)" :class="['relative inline-flex h-5 w-9 items-center rounded-full transition-colors', key.mergeSessions ? 'bg-indigo-600' : 'bg-gray-200 dark:bg-gray-600']" :title="key.mergeSessions ? t('imports.api_merge_active') : t('imports.api_merge_inactive')">
                        <span :class="['inline-block h-3 w-3 transform rounded-full bg-white transition-transform', key.mergeSessions ? 'translate-x-5' : 'translate-x-1']" />
                      </button>
                      <span class="text-xs text-gray-500 dark:text-gray-400">{{ t('imports.api_merge_sessions') }}</span>
                    </div>
                  </div>
                  <button @click="deleteApiKey(key.id, key.name)" :disabled="deletingKeyId === key.id" class="flex-shrink-0 p-2 text-red-500 hover:text-red-700 hover:bg-red-50 rounded-lg transition disabled:opacity-50" :title="t('imports.api_revoke_title')">
                    <TrashIcon class="h-5 w-5" />
                  </button>
                </div>
              </div>
            </div>
          </Transition>
        </div>

        <!-- 3. SPRIT-MONITOR -->
        <div>
          <button
            @click="toggle('spritmonitor'); analytics.trackImportTabClicked('spritmonitor')"
            class="w-full flex items-center gap-3 px-4 py-4 text-left hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors"
          >
            <div class="shrink-0 bg-white dark:bg-gray-700 border border-gray-200 dark:border-gray-600 rounded-lg p-2 w-10 h-10 flex items-center justify-center">
              <img src="/logos/spritmonitor.png" alt="Sprit-Monitor" class="h-5 w-auto" />
            </div>
            <div class="flex-1 min-w-0">
              <span class="font-medium text-gray-900 dark:text-gray-100 text-sm">{{ t('imports.tab_spritmonitor') }}</span>
            </div>
            <ChevronDownIcon :class="['h-5 w-5 text-gray-400 shrink-0 transition-transform duration-200', activeTab === 'spritmonitor' ? 'rotate-180' : '']" />
          </button>
          <Transition name="accordion">
            <div v-if="activeTab === 'spritmonitor'" class="border-t border-gray-100 dark:border-gray-700 p-4 space-y-4">
              <p class="text-sm text-gray-600 dark:text-gray-400">{{ t('imports.sprit_desc') }}</p>
              <ul class="text-sm text-gray-600 dark:text-gray-400 space-y-1 list-disc list-inside">
                <li>{{ t('imports.sprit_feat1') }}</li>
                <li>{{ t('imports.sprit_feat2') }}</li>
                <li>{{ t('imports.sprit_feat3') }}</li>
                <li>{{ t('imports.sprit_feat4') }}</li>
              </ul>
              <button @click="showSpritMonitorModal = true" class="btn-3d flex items-center gap-2 bg-indigo-600 text-white px-5 py-2.5 rounded-lg font-medium text-sm hover:bg-indigo-700 transition">
                <ArrowDownTrayIcon class="h-4 w-4" />
                {{ t('imports.sprit_btn') }}
              </button>
            </div>
          </Transition>
        </div>

        <!-- 4. TRONITY -->
        <div>
          <button
            @click="toggle('tronity'); analytics.trackImportTabClicked('tronity')"
            class="w-full flex items-center gap-3 px-4 py-4 text-left hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors"
          >
            <div class="shrink-0 bg-white dark:bg-gray-700 border border-gray-200 dark:border-gray-600 rounded-lg p-2 w-10 h-10 flex items-center justify-center">
              <img src="/logos/tronity.svg" alt="Tronity" class="h-4 w-auto" />
            </div>
            <div class="flex-1 min-w-0">
              <span class="font-medium text-gray-900 dark:text-gray-100 text-sm">{{ t('imports.tab_tronity') }}</span>
            </div>
            <ChevronDownIcon :class="['h-5 w-5 text-gray-400 shrink-0 transition-transform duration-200', activeTab === 'tronity' ? 'rotate-180' : '']" />
          </button>
          <Transition name="accordion">
            <div v-if="activeTab === 'tronity'" class="border-t border-gray-100 dark:border-gray-700">
              <TronityImport />
            </div>
          </Transition>
        </div>

        <!-- 5. MANUELL -->
        <div>
          <button
            @click="toggle('manuell'); analytics.trackImportTabClicked('manuell')"
            class="w-full flex items-center gap-3 px-4 py-4 text-left hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors"
          >
            <div class="shrink-0 bg-green-700 rounded-lg p-2">
              <ArrowDownTrayIcon class="h-5 w-5 text-white" />
            </div>
            <div class="flex-1 min-w-0">
              <span class="font-medium text-gray-900 dark:text-gray-100 text-sm">{{ t('imports.tab_manuell') }}</span>
            </div>
            <ChevronDownIcon :class="['h-5 w-5 text-gray-400 shrink-0 transition-transform duration-200', activeTab === 'manuell' ? 'rotate-180' : '']" />
          </button>
          <Transition name="accordion">
            <div v-if="activeTab === 'manuell'" class="border-t border-gray-100 dark:border-gray-700 p-4 space-y-4">
              <p class="text-sm text-gray-600 dark:text-gray-400">{{ t('imports.manuell_desc') }}</p>
              <ul class="text-sm text-gray-600 dark:text-gray-400 space-y-1 list-disc list-inside">
                <li>{{ t('imports.manuell_feat1') }}</li>
                <li>{{ t('imports.manuell_feat2') }}</li>
                <li>{{ t('imports.manuell_feat3') }}</li>
                <li>{{ t('imports.manuell_feat4') }}</li>
                <li>{{ t('imports.manuell_feat5') }}</li>
              </ul>
              <div v-if="activeCars.length > 1" class="space-y-1.5">
                <label class="block text-sm font-medium text-gray-700 dark:text-gray-300">{{ t('imports.manuell_select_car') }}</label>
                <CarSelectDropdown :cars="activeCars" v-model="manualImportCarId" />
              </div>
              <button
                @click="manualImportCarId = activeCars.length === 1 ? activeCars[0].id : manualImportCarId; showManualImportModal = true"
                :disabled="activeCars.length === 0 || (activeCars.length > 1 && !manualImportCarId)"
                class="btn-3d w-full flex items-center justify-center gap-2 bg-green-700 text-white px-5 py-2.5 rounded-lg font-medium text-sm hover:bg-green-800 disabled:opacity-40 disabled:cursor-not-allowed transition"
              >
                <ArrowDownTrayIcon class="h-4 w-4" />
                {{ t('imports.manuell_btn') }}
              </button>
              <p v-if="activeCars.length === 0" class="text-sm text-gray-500 dark:text-gray-400 italic">
                {{ t('imports.manuell_no_car') }}
                <router-link to="/cars" class="text-indigo-600 hover:underline font-medium">{{ t('imports.manuell_no_car_link') }}</router-link>
              </p>
            </div>
          </Transition>
        </div>

        <!-- 6. GO-ECHARGER -->
        <div>
          <button
            @click="toggle('goe'); analytics.trackImportTabClicked('goe')"
            class="w-full flex items-center gap-3 px-4 py-4 text-left hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors"
          >
            <div class="shrink-0 bg-white dark:bg-gray-700 border border-gray-200 dark:border-gray-600 rounded-lg p-2 w-10 h-10 flex items-center justify-center">
              <img src="/logos/go-e.svg" alt="go-e" class="h-5 w-auto" />
            </div>
            <div class="flex-1 min-w-0">
              <div class="flex items-center gap-2 flex-wrap">
                <span class="font-medium text-gray-900 dark:text-gray-100 text-sm">{{ t('imports.tab_goe') }}</span>
                <span class="text-[10px] bg-blue-100 text-blue-800 dark:bg-blue-900/40 dark:text-blue-300 font-semibold px-1.5 py-0.5 rounded-full leading-none">BETA</span>
              </div>
            </div>
            <ChevronDownIcon :class="['h-5 w-5 text-gray-400 shrink-0 transition-transform duration-200', activeTab === 'goe' ? 'rotate-180' : '']" />
          </button>
          <Transition name="accordion">
            <div v-if="activeTab === 'goe'" class="border-t border-gray-100 dark:border-gray-700 p-4 space-y-4">
              <p class="text-sm text-gray-600 dark:text-gray-400">{{ t('imports.goe_desc') }}</p>
              <div class="flex items-start gap-2 bg-blue-50 dark:bg-blue-900/30 border border-blue-200 dark:border-blue-700 rounded-lg p-3">
                <ExclamationTriangleIcon class="h-4 w-4 text-blue-600 dark:text-blue-400 mt-0.5 shrink-0" />
                <p class="text-xs text-blue-800 dark:text-blue-200">{{ t('imports.goe_beta_hint') }}</p>
              </div>
              <div class="flex items-start gap-2 bg-amber-50 dark:bg-amber-900/30 border border-amber-200 dark:border-amber-700 rounded-lg p-3">
                <ExclamationTriangleIcon class="h-4 w-4 text-amber-600 dark:text-amber-400 mt-0.5 shrink-0" />
                <p class="text-xs text-amber-800 dark:text-amber-200">{{ t('imports.goe_parallel_hint') }}</p>
              </div>
              <GoeIntegration />
            </div>
          </Transition>
        </div>

        <!-- 7. OCPP WALLBOX -->
        <div>
          <button
            @click="toggle('wallbox'); analytics.trackImportTabClicked('wallbox')"
            class="w-full flex items-center gap-3 px-4 py-4 text-left hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors"
          >
            <div class="shrink-0 bg-gray-800 dark:bg-gray-600 rounded-lg p-2">
              <BoltIcon class="h-5 w-5 text-white" />
            </div>
            <div class="flex-1 min-w-0">
              <div class="flex items-center gap-2 flex-wrap">
                <span class="font-medium text-gray-900 dark:text-gray-100 text-sm">{{ t('imports.tab_wallbox') }}</span>
                <span class="text-[10px] bg-blue-100 text-blue-800 dark:bg-blue-900/40 dark:text-blue-300 font-semibold px-1.5 py-0.5 rounded-full leading-none">BETA</span>
              </div>
            </div>
            <ChevronDownIcon :class="['h-5 w-5 text-gray-400 shrink-0 transition-transform duration-200', activeTab === 'wallbox' ? 'rotate-180' : '']" />
          </button>
          <Transition name="accordion">
            <div v-if="activeTab === 'wallbox'" class="border-t border-gray-100 dark:border-gray-700 p-4 space-y-4">
              <p class="text-sm text-gray-600 dark:text-gray-400">{{ t('imports.wallbox_desc') }}</p>
              <div class="flex items-start gap-2 bg-blue-50 dark:bg-blue-900/30 border border-blue-200 dark:border-blue-700 rounded-lg p-3">
                <ExclamationTriangleIcon class="h-4 w-4 text-blue-600 dark:text-blue-400 mt-0.5 shrink-0" />
                <p class="text-xs text-blue-800 dark:text-blue-200">{{ t('imports.wallbox_beta_hint') }}</p>
              </div>
              <div class="flex items-start gap-2 bg-amber-50 dark:bg-amber-900/30 border border-amber-200 dark:border-amber-700 rounded-xl p-4">
                <ExclamationTriangleIcon class="h-5 w-5 text-amber-600 dark:text-amber-400 mt-0.5 shrink-0" />
                <div class="text-sm text-amber-800 dark:text-amber-200 space-y-1">
                  <p class="font-semibold">{{ t('imports.wallbox_ocpp_warning_title') }}</p>
                  <p v-html="t('imports.wallbox_ocpp_warning_desc')" />
                  <p>{{ t('imports.wallbox_ocpp_goe_hint_pre') }} <button @click="toggle('goe')" class="underline font-medium cursor-pointer">{{ t('imports.wallbox_ocpp_goe_link') }}</button> {{ t('imports.wallbox_ocpp_goe_hint_post') }}</p>
                </div>
              </div>
              <router-link to="/wallbox" class="btn-3d inline-flex items-center gap-2 bg-gray-800 dark:bg-gray-600 text-white px-5 py-2.5 rounded-lg font-medium text-sm hover:bg-gray-700 transition">
                <BoltIcon class="h-4 w-4" />
                {{ t('imports.wallbox_btn') }}
              </router-link>
            </div>
          </Transition>
        </div>

        <!-- 8. TESLA -->
        <div>
          <button
            @click="toggle('tesla'); analytics.trackImportTabClicked('tesla')"
            class="w-full flex items-center gap-3 px-4 py-4 text-left hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors"
          >
            <div class="shrink-0 bg-gray-700 rounded-lg p-2">
              <BoltIcon class="h-5 w-5 text-white" />
            </div>
            <div class="flex-1 min-w-0">
              <span class="font-medium text-gray-900 dark:text-gray-100 text-sm">{{ t('imports.tab_tesla') }}</span>
            </div>
            <ChevronDownIcon :class="['h-5 w-5 text-gray-400 shrink-0 transition-transform duration-200', activeTab === 'tesla' ? 'rotate-180' : '']" />
          </button>
          <Transition name="accordion">
            <div v-if="activeTab === 'tesla'" class="border-t border-gray-100 dark:border-gray-700">
              <div v-if="!hasActiveTesla" class="p-4">
                <p class="text-sm text-gray-600 dark:text-gray-400">
                  {{ t('imports.tesla_no_car_desc', { link: '' }) }}
                  <router-link to="/cars" class="text-indigo-600 hover:underline font-medium">{{ t('imports.tesla_no_car_link') }}</router-link>
                </p>
              </div>
              <div v-else class="p-4"><TeslaFleetIntegration /></div>
            </div>
          </Transition>
        </div>

        </div><!-- end accordion -->
      </div>
    </Transition>
  </div>

  <!-- Sprit-Monitor Import Modal -->
  <SpritMonitorImport v-if="showSpritMonitorModal" @close="showSpritMonitorModal = false" />

  <!-- Manual Import Modal -->
  <ManualImportModal
    v-if="showManualImportModal && manualImportCarId"
    :car-id="manualImportCarId"
    @close="showManualImportModal = false"
    @imported="showManualImportModal = false"
  />
  <DemoImportsModal v-if="authStore.isDemoAccount" />
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

.accordion-enter-active,
.accordion-leave-active {
  transition: opacity 0.2s ease, max-height 0.25s ease;
  max-height: 800px;
  overflow: hidden;
}
.accordion-enter-from,
.accordion-leave-to {
  opacity: 0;
  max-height: 0;
}
</style>
