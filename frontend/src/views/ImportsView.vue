<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowDownTrayIcon, BoltIcon, ExclamationTriangleIcon, CodeBracketIcon, TrashIcon, ClipboardDocumentIcon, CheckIcon } from '@heroicons/vue/24/outline'
import SpritMonitorImport from '../components/SpritMonitorImport.vue'
import GoeIntegration from '../components/GoeIntegration.vue'
import TeslaFleetIntegration from '../components/TeslaFleetIntegration.vue'
import ManualImportModal from '../components/ManualImportModal.vue'
import TronityImport from '../components/TronityImport.vue'
import CarSelectDropdown from '../components/CarSelectDropdown.vue'
import type { Car } from '../api/carService'
import { useCarStore } from '../stores/car'
import { useImportsTab } from '../composables/useImportsTab'
import { apiKeyService, type ApiKeyResponse, type ApiKeyCreatedResponse } from '../api/apiKeyService'
import { analytics } from '../services/analytics'

const { t } = useI18n()
const { activeTab } = useImportsTab()
const carStore = useCarStore()
const showSpritMonitorModal = ref(false)
const manualImportCarId = ref<string | null>(null)
const showManualImportModal = ref(false)
const cars = ref<Car[]>([])
const loading = ref(true)

onMounted(async () => {
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
  <div class="md:max-w-3xl md:mx-auto md:p-6">
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

        <!-- Tabs -->
        <div class="flex gap-1">
          <button
            v-for="tab in ([
              { id: 'spritmonitor' as const, label: t('imports.tab_spritmonitor') },
              { id: 'goe' as const, label: t('imports.tab_goe') },
              { id: 'wallbox' as const, label: t('imports.tab_wallbox') },
              { id: 'tesla' as const, label: t('imports.tab_tesla') },
              { id: 'tronity' as const, label: t('imports.tab_tronity') },
              { id: 'manuell' as const, label: t('imports.tab_manuell') },
              { id: 'api' as const, label: t('imports.tab_api') },
            ])"
            :key="tab.id"
            @click="activeTab = tab.id; analytics.trackImportTabClicked(tab.id)"
            :class="[
              'shrink-0 px-4 py-2.5 text-sm font-medium border-t border-l border-r rounded-t-lg transition-colors relative z-10',
              activeTab === tab.id
                ? 'bg-white dark:bg-gray-800 border-gray-200 dark:border-gray-700 text-gray-900 dark:text-gray-100 -mb-px pb-[calc(0.625rem+1px)]'
                : 'bg-gray-50 dark:bg-gray-700 border-gray-200 dark:border-gray-700 text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-200 hover:bg-gray-100 dark:hover:bg-gray-600'
            ]"
          >{{ tab.label }}</button>
        </div>
        <!-- Tab panel -->
        <div class="border border-gray-200 dark:border-gray-700 rounded-b-xl rounded-tr-xl bg-white dark:bg-gray-800">
        <!-- Tab: Sprit-Monitor -->
        <div v-if="activeTab === 'spritmonitor'" class="p-6 space-y-4">
          <div class="flex items-start gap-4">
            <div class="bg-indigo-600 rounded-lg p-2 shrink-0">
              <ArrowDownTrayIcon class="h-5 w-5 text-white" />
            </div>
            <div>
              <h2 class="font-semibold text-gray-900 dark:text-gray-100">{{ t('imports.sprit_title') }}</h2>
              <p class="text-sm text-gray-600 dark:text-gray-400 mt-1">{{ t('imports.sprit_desc') }}</p>
            </div>
          </div>
          <ul class="text-sm text-gray-600 dark:text-gray-400 space-y-1 list-disc list-inside">
            <li>{{ t('imports.sprit_feat1') }}</li>
            <li>{{ t('imports.sprit_feat2') }}</li>
            <li>{{ t('imports.sprit_feat3') }}</li>
            <li>{{ t('imports.sprit_feat4') }}</li>
          </ul>
          <button
            @click="showSpritMonitorModal = true"
            class="btn-3d flex items-center gap-2 bg-indigo-600 text-white px-5 py-2.5 rounded-lg font-medium text-sm hover:bg-indigo-700 transition"
          >
            <ArrowDownTrayIcon class="h-4 w-4" />
            {{ t('imports.sprit_btn') }}
          </button>
        </div>

        <!-- Tab: go-eCharger -->
        <div v-if="activeTab === 'goe'" class="p-6 space-y-5">
          <div class="flex items-start gap-4">
            <div class="bg-green-600 rounded-lg p-2 shrink-0">
              <BoltIcon class="h-5 w-5 text-white" />
            </div>
            <div>
              <h2 class="font-semibold text-gray-900 dark:text-gray-100">{{ t('imports.goe_title') }} <span class="text-xs bg-blue-100 text-blue-800 px-2 py-0.5 rounded-full font-medium ml-2">BETA</span></h2>
              <p class="text-sm text-gray-600 dark:text-gray-400 mt-1">{{ t('imports.goe_desc') }}</p>
            </div>
          </div>
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

        <!-- Tab: OCPP Wallbox -->
        <div v-if="activeTab === 'wallbox'" class="p-6 space-y-5">
          <div class="flex items-start gap-4">
            <div class="bg-gray-800 rounded-lg p-2 shrink-0">
              <BoltIcon class="h-5 w-5 text-white" />
            </div>
            <div>
              <h2 class="font-semibold text-gray-900 dark:text-gray-100">{{ t('imports.wallbox_title') }} <span class="text-xs bg-blue-100 text-blue-800 px-2 py-0.5 rounded-full font-medium ml-2">BETA</span></h2>
              <p class="text-sm text-gray-600 dark:text-gray-400 mt-1">{{ t('imports.wallbox_desc') }}</p>
            </div>
          </div>
          <div class="flex items-start gap-2 bg-blue-50 dark:bg-blue-900/30 border border-blue-200 dark:border-blue-700 rounded-lg p-3">
            <ExclamationTriangleIcon class="h-4 w-4 text-blue-600 dark:text-blue-400 mt-0.5 shrink-0" />
            <p class="text-xs text-blue-800 dark:text-blue-200">{{ t('imports.wallbox_beta_hint') }}</p>
          </div>
          <div class="flex items-start gap-2 bg-amber-50 dark:bg-amber-900/30 border border-amber-200 dark:border-amber-700 rounded-xl p-4">
            <ExclamationTriangleIcon class="h-5 w-5 text-amber-600 dark:text-amber-400 mt-0.5 shrink-0" />
            <div class="text-sm text-amber-800 dark:text-amber-200 space-y-1">
              <p class="font-semibold">{{ t('imports.wallbox_ocpp_warning_title') }}</p>
              <p v-html="t('imports.wallbox_ocpp_warning_desc')" />
              <p>{{ t('imports.wallbox_ocpp_goe_hint_pre') }} <button @click="activeTab = 'goe'" class="underline font-medium cursor-pointer">{{ t('imports.wallbox_ocpp_goe_link') }}</button> {{ t('imports.wallbox_ocpp_goe_hint_post') }}</p>
            </div>
          </div>
          <router-link
            to="/wallbox"
            class="btn-3d inline-flex items-center gap-2 bg-gray-800 dark:bg-gray-600 text-white px-5 py-2.5 rounded-lg font-medium text-sm hover:bg-gray-700 dark:hover:bg-gray-500 transition"
          >
            <BoltIcon class="h-4 w-4" />
            {{ t('imports.wallbox_btn') }}
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
                <h2 class="font-semibold text-gray-900 dark:text-gray-100">{{ t('imports.tesla_no_car_title') }}</h2>
                <p class="text-sm text-gray-600 dark:text-gray-400 mt-2">
                  {{ t('imports.tesla_no_car_desc', { link: '' }) }}
                  <router-link to="/cars" class="text-indigo-600 hover:underline font-medium">{{ t('imports.tesla_no_car_link') }}</router-link>
                </p>
              </div>
            </div>
          </div>
          <template v-else>
          <div class="p-6"><TeslaFleetIntegration /></div>
          </template>
        </div>
        <!-- Tab: Tronity -->
        <div v-if="activeTab === 'tronity'">
          <TronityImport />
        </div>

        <!-- Tab: Manuell -->
        <div v-if="activeTab === 'manuell'" class="p-6 space-y-4">
          <div class="flex items-start gap-4">
            <div class="bg-green-700 rounded-lg p-2 shrink-0">
              <ArrowDownTrayIcon class="h-5 w-5 text-white" />
            </div>
            <div>
              <h2 class="font-semibold text-gray-900 dark:text-gray-100">{{ t('imports.manuell_title') }}</h2>
              <p class="text-sm text-gray-600 dark:text-gray-400 mt-1">{{ t('imports.manuell_desc') }}</p>
            </div>
          </div>
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

        <!-- Tab: API -->
        <div v-if="activeTab === 'api'" class="p-6 space-y-5">
          <div class="flex items-start gap-4">
            <div class="bg-indigo-600 rounded-lg p-2 shrink-0">
              <CodeBracketIcon class="h-5 w-5 text-white" />
            </div>
            <div>
              <h2 class="font-semibold text-gray-900 dark:text-gray-100">{{ t('imports.api_title') }}</h2>
              <p class="text-sm text-gray-600 dark:text-gray-400 mt-1">{{ t('imports.api_desc') }}</p>
            </div>
          </div>

          <!-- Endpoint Info -->
          <div class="p-4 bg-indigo-50 dark:bg-indigo-900/20 border border-indigo-200 dark:border-indigo-700 rounded-lg text-sm text-indigo-800 dark:text-indigo-300">
            <p class="font-mono text-xs bg-white dark:bg-gray-700 border border-indigo-200 dark:border-indigo-700 rounded px-2 py-1.5 mb-2 break-all">
              POST https://ev-monitor.net/api/v1/sessions<br>
              Authorization: Bearer evm_&lt;dein-key&gt;
            </p>
            <p class="text-xs mb-1">
              {{ t('imports.api_required') }} <code class="bg-white dark:bg-gray-700 px-1 rounded">date</code>, <code class="bg-white dark:bg-gray-700 px-1 rounded">kwh</code>
            </p>
            <p class="text-xs">
              {{ t('imports.api_optional') }} <code class="bg-white dark:bg-gray-700 px-1 rounded">odometer_km</code>, <code class="bg-white dark:bg-gray-700 px-1 rounded">soc_after</code>, <code class="bg-white dark:bg-gray-700 px-1 rounded">cost_eur</code>, <code class="bg-white dark:bg-gray-700 px-1 rounded">duration_min</code>, <code class="bg-white dark:bg-gray-700 px-1 rounded">location</code>, <code class="bg-white dark:bg-gray-700 px-1 rounded">charging_type</code> (AC/DC)
            </p>
            <p class="text-xs mt-1">{{ t('imports.api_dedup') }}</p>
            <a href="/swagger-ui/index.html" target="_blank" class="inline-block mt-2 text-indigo-700 hover:underline font-medium text-xs">
              {{ t('imports.api_docs') }}
            </a>
          </div>

          <!-- Message -->
          <div v-if="apiKeyMessage" :class="[
            'p-3 rounded-lg text-sm',
            apiKeyMessage.type === 'success' ? 'bg-green-50 dark:bg-green-900/30 border border-green-200 dark:border-green-700 text-green-700 dark:text-green-300' : 'bg-red-50 dark:bg-red-900/30 border border-red-200 dark:border-red-700 text-red-700 dark:text-red-300'
          ]">{{ apiKeyMessage.text }}</div>

          <!-- Newly created key -->
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

          <!-- Create key -->
          <div class="flex gap-2">
            <input
              v-model="newKeyName"
              type="text"
              :placeholder="t('imports.api_key_placeholder')"
              maxlength="100"
              class="flex-1 px-3 py-2 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-lg text-sm focus:ring-indigo-500 focus:border-indigo-500"
              @keyup.enter="createApiKey" />
            <button
              @click="createApiKey"
              :disabled="apiKeyLoading || !newKeyName.trim()"
              class="btn-3d px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 disabled:opacity-50 transition text-sm font-medium whitespace-nowrap">
              {{ t('imports.api_create_btn') }}
            </button>
          </div>

          <!-- Key list -->
          <div v-if="apiKeys.length === 0" class="text-sm text-gray-500 dark:text-gray-400 italic">{{ t('imports.api_no_keys') }}</div>
          <div v-else class="space-y-2">
            <div
              v-for="key in apiKeys"
              :key="key.id"
              class="flex items-center justify-between p-3 bg-gray-50 dark:bg-gray-700 border border-gray-200 dark:border-gray-600 rounded-lg gap-2">
              <div class="min-w-0 flex-1">
                <p class="font-medium text-gray-800 dark:text-gray-200 text-sm truncate">{{ key.name || t('imports.api_no_name') }}</p>
                <p class="text-xs text-gray-500 dark:text-gray-400">
                  <code class="font-mono">{{ key.keyPrefix }}…</code>
                  · {{ t('imports.api_last_used') }} {{ formatDate(key.lastUsedAt) }}
                  · {{ t('imports.api_created') }} {{ formatDate(key.createdAt) }}
                </p>
                <!-- Merge toggle -->
                <div class="flex items-center gap-2 mt-1">
                  <button
                    @click="toggleMergeSessions(key)"
                    :class="['relative inline-flex h-5 w-9 items-center rounded-full transition-colors',
                             key.mergeSessions ? 'bg-indigo-600' : 'bg-gray-200 dark:bg-gray-600']"
                    :title="key.mergeSessions ? t('imports.api_merge_active') : t('imports.api_merge_inactive')">
                    <span :class="['inline-block h-3 w-3 transform rounded-full bg-white transition-transform',
                                   key.mergeSessions ? 'translate-x-5' : 'translate-x-1']" />
                  </button>
                  <span class="text-xs text-gray-500 dark:text-gray-400">{{ t('imports.api_merge_sessions') }}</span>
                </div>
              </div>
              <button
                @click="deleteApiKey(key.id, key.name)"
                :disabled="deletingKeyId === key.id"
                class="flex-shrink-0 p-2 text-red-500 hover:text-red-700 hover:bg-red-50 rounded-lg transition disabled:opacity-50"
                :title="t('imports.api_revoke_title')">
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

  <!-- Manual Import Modal -->
  <ManualImportModal
    v-if="showManualImportModal && manualImportCarId"
    :car-id="manualImportCarId"
    @close="showManualImportModal = false"
    @imported="showManualImportModal = false"
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
