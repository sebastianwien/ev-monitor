<script setup lang="ts">
import { ref, computed, onMounted, defineAsyncComponent } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '../stores/auth'
import { ArrowDownTrayIcon, ArrowPathIcon, BoltIcon, CodeBracketIcon, TrashIcon, ClipboardDocumentIcon, CheckIcon, ChevronDownIcon } from '@heroicons/vue/24/outline'
import SpritMonitorImport from '../components/imports/SpritMonitorImport.vue'
import GoeIntegration from '../components/imports/GoeIntegration.vue'
import TeslaFleetIntegration from '../components/imports/TeslaFleetIntegration.vue'
import AutoSyncCarPicker from '../components/imports/AutoSyncCarPicker.vue'
import ManualImportModal from '../components/imports/ManualImportModal.vue'
const TronityImport = defineAsyncComponent(() => import('../components/imports/TronityImport.vue'))
import TessieImport from '../components/imports/TessieImport.vue'
const XpengImport = defineAsyncComponent(() => import('../components/imports/XpengImport.vue'))
const XpengAutoSyncImport = defineAsyncComponent(() => import('../components/imports/XpengAutoSyncImport.vue'))
const EUDataActImport = defineAsyncComponent(() => import('../components/imports/EUDataActImport.vue'))
import CarSelectDropdown from '../components/car/CarSelectDropdown.vue'
import type { Car } from '../api/carService'
import { useCarStore } from '../stores/car'
import { useImportsTab } from '../composables/useImportsTab'
import { useTeslaImportGating } from '../composables/useTeslaImportGating'
import { apiKeyService, type ApiKeyResponse, type ApiKeyCreatedResponse } from '../api/apiKeyService'
import { analytics } from '../services/analytics'
import DemoImportsModal from '../components/demo/DemoImportsModal.vue'
import { subscriptionService } from '../api/subscriptionService'

const { t } = useI18n()
const { activeTab, toggle } = useImportsTab()
const authStore = useAuthStore()
const carStore = useCarStore()
const showSpritMonitorModal = ref(false)
const showTessieModal = ref(false)
const manualImportCarId = ref<string | null>(null)
const showManualImportModal = ref(false)
const cars = ref<Car[]>([])
const loading = ref(true)
const premiumEnabled = ref(false)
const subscriptionIsPremium = ref(authStore.isPremium)
const subscriptionTier = ref<'NONE' | 'AUTOSYNC' | 'AUTOSYNC_LIVE'>('NONE')
const liveUpgradeLoading = ref(false)
const liveUpgradeError = ref('')

// Live-Promo: shown only when the user is on the AutoSync tier AND has at
// least one Tesla in their garage. Once on Live, no further upsell needed.
const hasTesla = computed(() => cars.value.some(c => c.brand === 'TESLA'))
const hasXpeng = computed(() => cars.value.some(c => c.brand === 'XPENG'))
const showLivePromo = computed(() => subscriptionTier.value === 'AUTOSYNC' && hasTesla.value)

async function handleLiveUpgrade() {
  liveUpgradeLoading.value = true
  liveUpgradeError.value = ''
  let navigating = false
  try {
    await subscriptionService.upgradeToLive()
    const status = await subscriptionService.getStatus()
    subscriptionTier.value = status.tier ?? 'NONE'
    subscriptionIsPremium.value = status.isPremium
  } catch (e: unknown) {
    const err = e as { response?: { data?: { errorCode?: string } } }
    if (err.response?.data?.errorCode === 'tesla_required') {
      liveUpgradeError.value = t('upgrade.live_tesla_required_error')
    } else if (err.response?.data?.errorCode === 'no_active_subscription') {
      // Kein echtes Stripe-Abo hinter dem Account → normaler Checkout für Live
      try {
        const result = await subscriptionService.createCheckoutSession('monthly', 'autosync_live')
        navigating = true
        window.location.href = result.checkoutUrl
      } catch {
        liveUpgradeError.value = t('upgrade.error')
      }
    } else {
      liveUpgradeError.value = t('upgrade.error')
    }
  } finally {
    if (!navigating) liveUpgradeLoading.value = false
  }
}

onMounted(async () => {
  if (authStore.isPremium) {
    activeTab.value = 'smartcar'
  }
  const params = new URLSearchParams(window.location.search)
  if (params.get('smartcar-connected') || params.get('smartcar-error')) {
    activeTab.value = 'smartcar'
  }
  try {
    cars.value = await carStore.getCars() ?? []
    await new Promise(resolve => setTimeout(resolve, 100))
  } catch { /* ignore */ } finally {
    loading.value = false
  }
  fetchApiKeys()
  subscriptionService.getStatus().then(s => {
    premiumEnabled.value = s.premiumEnabled
    subscriptionIsPremium.value = s.isPremium
    subscriptionTier.value = s.tier ?? 'NONE'
  }).catch(() => {})
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

const toModelLabel = (model: string) =>
  model.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, c => c.toUpperCase())

const copiedCarId = ref<string | null>(null)
const copyCarId = async (id: string) => {
  try {
    await navigator.clipboard.writeText(id)
    copiedCarId.value = id
    setTimeout(() => { copiedCarId.value = null }, 2000)
  } catch { /* ignore */ }
}

const formatDate = (dateStr: string | null) => {
  if (!dateStr) return t('imports.api_never')
  return new Date(dateStr).toLocaleDateString(undefined, { day: '2-digit', month: '2-digit', year: 'numeric' })
}

const { showTeslaTab } = useTeslaImportGating(cars)

const activeCars = computed(() =>
  Array.isArray(cars.value) ? cars.value.filter(c => c.status === 'ACTIVE') : []
)

const autoSyncActiveCarLabel = ref<string | null>(null)
const teslaConnectedLabel = ref<string | null>(null)
</script>

<template>
<div>
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

        <!-- AutoSync Pro Teaser — nur wenn Premium-Kauf möglich und User noch kein Abonnent -->
        <div v-if="premiumEnabled && !authStore.isPremium"
             class="mb-3 border border-amber-500/60 bg-amber-50 dark:bg-amber-950/20 rounded-sm shadow-[2px_2px_0_0_#030712] dark:shadow-none p-3 text-center md:text-left">
          <div class="flex flex-col items-center md:flex-row md:items-start gap-2.5">
            <div class="shrink-0 rounded-sm bg-amber-500/15 dark:bg-amber-500/10 p-1.5 w-8 h-8 flex items-center justify-center">
              <BoltIcon class="h-4 w-4 text-amber-600 dark:text-amber-400" />
            </div>
            <div class="flex-1 min-w-0">
              <p class="text-amber-600 dark:text-amber-500 text-[10px] font-bold uppercase tracking-[0.14em] mb-0.5">EV Monitor AutoSync</p>
              <p class="font-bold text-gray-900 dark:text-white text-sm md:text-base mb-1 tracking-tight">{{ t('imports.autosync_teaser_title') }}</p>
              <p class="text-xs text-gray-600 dark:text-gray-400 mb-2 leading-relaxed">{{ t('imports.autosync_teaser_desc') }}</p>
              <div class="flex flex-col items-center sm:flex-row sm:items-center gap-2">
                <router-link
                  to="/upgrade"
                  class="flex w-full sm:w-auto sm:inline-flex items-center justify-center gap-1.5 bg-amber-500 hover:bg-amber-400 text-gray-950 font-bold uppercase tracking-wider text-[10px] px-3 py-1.5 rounded-sm border border-amber-500 active:translate-y-[1px] transition-transform duration-75"
                >
                  {{ t('imports.autosync_teaser_cta') }}
                </router-link>
                <span class="text-[10px] font-bold uppercase tracking-wider text-gray-600 dark:text-gray-500">{{ t('imports.autosync_teaser_price', { priceMonthly: t('upgrade.price_monthly') }) }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- Accordion -->
        <div class="-mx-4 md:mx-0 border-y-2 md:border-2 border-gray-300 dark:border-gray-700 md:rounded-sm divide-y-2 divide-gray-300 dark:divide-gray-700 overflow-hidden md:shadow-[2px_2px_0_0_#d1d5db] dark:md:shadow-[2px_2px_0_0_#374151]">

        <!-- 1. SMARTCAR -->
        <div>
          <button
            @click="toggle('smartcar'); analytics.trackImportTabClicked('smartcar')"
            class="w-full flex items-center gap-3 px-4 py-4 text-left hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors"
          >
            <div class="shrink-0 rounded-sm border-2 border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-700 p-2 w-10 h-10 flex items-center justify-center">
              <ArrowPathIcon class="h-5 w-5 text-gray-700 dark:text-gray-200" />
            </div>
            <div class="flex-1 min-w-0">
              <div class="flex items-center gap-2 flex-wrap">
                <span class="font-medium text-gray-900 dark:text-gray-100 text-sm">{{ t('imports.tab_smartcar') }}</span>
              </div>
              <p v-if="autoSyncActiveCarLabel" class="text-xs text-green-600 dark:text-green-400 mt-0.5">
                {{ t('imports.autosync_header_active', { car: autoSyncActiveCarLabel }) }}
              </p>
            </div>
            <ChevronDownIcon :class="['h-5 w-5 text-gray-400 shrink-0 transition-transform duration-200', activeTab === 'smartcar' ? 'rotate-180' : '']" />
          </button>
          <Transition name="accordion">
            <div v-if="activeTab === 'smartcar'" class="px-1 py-3 md:p-6 space-y-3">
              <!-- Live-Promo: AutoSync subscriber with at least one Tesla in garage.
                   Neo-Brutalist Style passend zum Rest des AutoSync-Tabs. -->
              <div v-if="showLivePromo" class="border-2 border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-800 rounded-sm shadow-[2px_2px_0_0_#d1d5db] dark:shadow-[2px_2px_0_0_#374151] px-3 py-2.5 flex items-center gap-3 flex-wrap sm:flex-nowrap">
                  <div class="flex items-center gap-2 shrink-0">
                    <span class="inline-flex w-5 h-5 bg-indigo-600 text-white rounded-sm items-center justify-center text-[11px] font-extrabold shrink-0">+</span>
                    <p class="text-indigo-700 dark:text-indigo-400 text-xs font-bold uppercase tracking-[0.14em]">AutoSync Live</p>
                  </div>
                  <div class="flex items-center gap-3 flex-wrap flex-1 min-w-0">
                    <span class="flex items-center gap-1 text-xs text-gray-600 dark:text-gray-400">
                      <span class="text-indigo-500 dark:text-indigo-400 font-bold">→</span>
                      <span class="font-medium" v-html="t('imports.live_promo_feature_trip')"></span>
                    </span>
                    <span class="flex items-center gap-1 text-xs text-gray-600 dark:text-gray-400">
                      <span class="text-indigo-500 dark:text-indigo-400 font-bold">→</span>
                      <span class="font-medium" v-html="t('imports.live_promo_feature_drain')"></span>
                    </span>
                  </div>
                  <button
                    @click="handleLiveUpgrade"
                    :disabled="liveUpgradeLoading"
                    class="btn-3d shrink-0 inline-flex items-center justify-center bg-indigo-600 hover:bg-indigo-700 text-white font-bold uppercase tracking-wider text-[11px] px-3 py-1.5 rounded-sm disabled:opacity-60 disabled:cursor-not-allowed whitespace-nowrap"
                  >
                    <span v-if="liveUpgradeLoading">…</span>
                    <span v-else>{{ t('imports.live_promo_cta') }}</span>
                  </button>
                  <p v-if="liveUpgradeError" class="w-full text-xs text-red-600 dark:text-red-400 font-medium">{{ liveUpgradeError }}</p>
              </div>

              <!-- Tile-based picker per car. Smartcar brands only - Tesla is free and
                   pairs in its own "Tesla Telemetry" tab, XPeng has its own section.
                   Premium=1-active-connection limit is surfaced in tile-locking.
                   Non-premium users see the Smartcar upgrade pitch inside the picker. -->
              <AutoSyncCarPicker
                :cars="activeCars.filter(c => c.brand !== 'XPENG' && c.brand?.toLowerCase() !== 'tesla')"
                :premium-enabled="premiumEnabled"
                :is-premium="subscriptionIsPremium"
                :has-auto-sync-access="authStore.isPremium"
                @active-car-label="autoSyncActiveCarLabel = $event"
              />
              <XpengAutoSyncImport
                v-if="hasXpeng"
                :cars="activeCars"
              />
            </div>
          </Transition>
        </div>

        <!-- 2. API -->
        <div>
          <button
            @click="toggle('api'); analytics.trackImportTabClicked('api')"
            class="w-full flex items-center gap-3 px-4 py-4 text-left hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors"
          >
            <div class="shrink-0 rounded-sm border-2 border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-700 p-2 w-10 h-10 flex items-center justify-center">
              <CodeBracketIcon class="h-5 w-5 text-gray-700 dark:text-gray-200" />
            </div>
            <div class="flex-1 min-w-0">
              <span class="font-medium text-gray-900 dark:text-gray-100 text-sm">{{ t('imports.tab_api') }}</span>
            </div>
            <ChevronDownIcon :class="['h-5 w-5 text-gray-400 shrink-0 transition-transform duration-200', activeTab === 'api' ? 'rotate-180' : '']" />
          </button>
          <Transition name="accordion">
            <div v-if="activeTab === 'api'" class="border-t-2 border-gray-300 dark:border-gray-700 p-4 md:p-5 space-y-5">
              <p class="text-sm text-gray-700 dark:text-gray-300 font-medium leading-relaxed">{{ t('imports.api_desc') }}</p>

              <!-- Endpoint-Card -->
              <div class="border-2 border-gray-300 dark:border-white bg-gray-50 dark:bg-gray-950 rounded-sm shadow-[2px_2px_0_0_#4f46e5] p-4">
                <p class="text-indigo-600 dark:text-indigo-400 text-[11px] font-bold uppercase tracking-[0.14em] mb-2">Endpoint</p>
                <p class="font-mono text-xs bg-gray-100 dark:bg-gray-950 text-gray-800 dark:text-gray-100 border border-gray-300 dark:border-gray-700 rounded-sm px-3 py-2 mb-3 break-all">
                  POST https://ev-monitor.net/api/v1/sessions<br>
                  Authorization: Bearer evm_&lt;dein-key&gt;
                </p>
                <p class="text-xs text-gray-600 dark:text-gray-300 mb-1.5"><span class="text-indigo-600 dark:text-indigo-400 font-bold uppercase tracking-wider text-[10px]">{{ t('imports.api_required') }}</span> <code class="bg-gray-200 dark:bg-gray-800 text-amber-700 dark:text-amber-300 px-1.5 py-0.5 rounded-sm font-mono">date</code>, <code class="bg-gray-200 dark:bg-gray-800 text-amber-700 dark:text-amber-300 px-1.5 py-0.5 rounded-sm font-mono">kwh</code></p>
                <p class="text-xs text-gray-600 dark:text-gray-300 mb-1.5"><span class="text-indigo-600 dark:text-indigo-400 font-bold uppercase tracking-wider text-[10px]">{{ t('imports.api_optional') }}</span> <code class="bg-gray-200 dark:bg-gray-800 text-amber-700 dark:text-amber-300 px-1.5 py-0.5 rounded-sm font-mono">odometer_km</code>, <code class="bg-gray-200 dark:bg-gray-800 text-amber-700 dark:text-amber-300 px-1.5 py-0.5 rounded-sm font-mono">soc_after</code>, <code class="bg-gray-200 dark:bg-gray-800 text-amber-700 dark:text-amber-300 px-1.5 py-0.5 rounded-sm font-mono">cost_eur</code>, <code class="bg-gray-200 dark:bg-gray-800 text-amber-700 dark:text-amber-300 px-1.5 py-0.5 rounded-sm font-mono">duration_min</code>, <code class="bg-gray-200 dark:bg-gray-800 text-amber-700 dark:text-amber-300 px-1.5 py-0.5 rounded-sm font-mono">location</code>, <code class="bg-gray-200 dark:bg-gray-800 text-amber-700 dark:text-amber-300 px-1.5 py-0.5 rounded-sm font-mono">charging_type</code> (AC/DC)</p>
                <p class="text-xs text-gray-500 dark:text-gray-400 mt-2">{{ t('imports.api_dedup') }}</p>
                <p class="text-xs text-gray-500 dark:text-gray-400 mt-1" v-html="t('imports.api_date_hint')" />
                <a href="/swagger-ui/index.html" target="_blank" class="inline-block mt-3 text-indigo-600 dark:text-indigo-400 hover:text-indigo-700 dark:hover:text-indigo-300 font-bold uppercase tracking-wider text-[11px] underline underline-offset-2">{{ t('imports.api_docs') }} →</a>
              </div>

              <!-- Fahrzeug-IDs -->
              <div v-if="activeCars.length > 0" class="space-y-2">
                <p class="text-[11px] font-bold uppercase tracking-[0.14em] text-gray-500 dark:text-gray-400">{{ t('imports.api_car_ids_title') }}</p>
                <p class="text-xs text-gray-500 dark:text-gray-400 leading-relaxed" v-html="t('imports.api_car_ids_hint')" />
                <div v-for="car in activeCars" :key="car.id"
                  class="flex items-center gap-2 border-2 border-gray-300 dark:border-gray-700 bg-gray-50 dark:bg-gray-900 rounded-sm p-3">
                  <div class="flex-1 min-w-0">
                    <p class="text-xs font-bold text-gray-900 dark:text-gray-100 truncate">{{ car.year }} {{ toModelLabel(car.model) }}<span v-if="car.licensePlate" class="text-gray-500 dark:text-gray-400 font-normal"> · {{ car.licensePlate }}</span></p>
                    <p class="text-[11px] font-mono text-gray-500 dark:text-gray-400 mt-0.5 select-all">{{ car.id }}</p>
                  </div>
                  <button @click="copyCarId(car.id)" :title="t('cars.api_id_copy')"
                    class="shrink-0 p-2 border-2 border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-800 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-sm shadow-[2px_2px_0_0_#9ca3af] dark:shadow-[2px_2px_0_0_#4b5563] active:translate-x-[2px] active:translate-y-[2px] active:shadow-none transition-[transform,box-shadow] duration-75">
                    <CheckIcon v-if="copiedCarId === car.id" class="h-4 w-4 text-emerald-500" />
                    <ClipboardDocumentIcon v-else class="h-4 w-4 text-gray-600 dark:text-gray-300" />
                  </button>
                </div>
              </div>

              <div v-if="apiKeyMessage"
                   :class="['border-l-2 px-4 py-3 rounded-r-sm text-sm font-medium',
                            apiKeyMessage.type === 'success'
                              ? 'border-emerald-500 bg-emerald-50 dark:bg-emerald-950/40 text-emerald-900 dark:text-emerald-200'
                              : 'border-red-500 bg-red-50 dark:bg-red-950/40 text-red-900 dark:text-red-200']">
                {{ apiKeyMessage.text }}
              </div>

              <div v-if="createdKey" class="border-2 border-emerald-500 bg-emerald-50 dark:bg-emerald-950/40 rounded-sm shadow-[2px_2px_0_0_#10b981] p-4">
                <p class="text-[11px] font-bold uppercase tracking-[0.14em] text-emerald-700 dark:text-emerald-400 mb-2">{{ t('imports.api_new_key_title') }}</p>
                <div class="flex items-center gap-2">
                  <code class="flex-1 bg-gray-900 text-emerald-300 border-2 border-gray-900 dark:border-emerald-700 rounded-sm px-3 py-2 text-xs font-mono break-all">{{ createdKey.plaintextKey }}</code>
                  <button @click="copyApiKey"
                          class="shrink-0 p-2.5 border-2 border-gray-900 dark:border-white bg-emerald-500 hover:bg-emerald-400 text-gray-950 rounded-sm shadow-[2px_2px_0_0_#030712] active:translate-x-[3px] active:translate-y-[3px] active:shadow-none transition-[transform,box-shadow] duration-75"
                          :title="t('imports.api_copy_title')">
                    <CheckIcon v-if="keyCopied" class="h-5 w-5" />
                    <ClipboardDocumentIcon v-else class="h-5 w-5" />
                  </button>
                </div>
                <p class="text-xs text-emerald-700 dark:text-emerald-300 mt-2 font-medium">{{ t('imports.api_save_key_hint') }}</p>
              </div>

              <div class="flex flex-col sm:flex-row gap-2">
                <input v-model="newKeyName" type="text" :placeholder="t('imports.api_key_placeholder')" maxlength="100"
                       class="flex-1 px-3 py-2.5 border-2 border-gray-300 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100 rounded-sm text-sm font-medium focus:outline-none focus:border-indigo-500 transition-colors"
                       @keyup.enter="createApiKey" />
                <button @click="createApiKey" :disabled="apiKeyLoading || !newKeyName.trim()"
                        class="w-full sm:w-auto inline-flex items-center justify-center gap-2 bg-indigo-600 hover:bg-indigo-500 disabled:bg-gray-300 dark:disabled:bg-gray-700 disabled:text-gray-500 disabled:cursor-not-allowed text-white font-bold uppercase tracking-wider text-[11px] px-4 py-2.5 rounded-sm border-2 border-indigo-600 disabled:border-gray-300 dark:disabled:border-gray-700 shadow-[2px_2px_0_0_#030712] disabled:shadow-none active:translate-x-[3px] active:translate-y-[3px] active:shadow-none transition-[transform,box-shadow] duration-75 whitespace-nowrap">
                  {{ t('imports.api_create_btn') }}
                </button>
              </div>

              <div v-if="apiKeys.length === 0"
                   class="text-sm font-medium border-l-2 border-gray-300 dark:border-gray-600 bg-gray-50 dark:bg-gray-900/40 text-gray-600 dark:text-gray-400 px-4 py-3 rounded-r-sm">
                {{ t('imports.api_no_keys') }}
              </div>
              <div v-else class="space-y-2">
                <div v-for="key in apiKeys" :key="key.id"
                     class="flex items-center justify-between gap-2 border-2 border-gray-300 dark:border-gray-700 bg-gray-50 dark:bg-gray-900 rounded-sm p-3">
                  <div class="min-w-0 flex-1">
                    <p class="font-bold text-gray-900 dark:text-gray-100 text-sm truncate">{{ key.name || t('imports.api_no_name') }}</p>
                    <p class="text-xs text-gray-500 dark:text-gray-400 mt-0.5 font-mono">
                      <span class="text-amber-600 dark:text-amber-400">{{ key.keyPrefix }}…</span>
                      · {{ t('imports.api_last_used') }} {{ formatDate(key.lastUsedAt) }}
                      · {{ t('imports.api_created') }} {{ formatDate(key.createdAt) }}
                    </p>
                  </div>
                  <button @click="deleteApiKey(key.id, key.name)" :disabled="deletingKeyId === key.id"
                          class="shrink-0 p-2 border-2 border-red-500 bg-white dark:bg-gray-900 text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-950/40 rounded-sm shadow-[2px_2px_0_0_#dc2626] active:translate-x-[2px] active:translate-y-[2px] active:shadow-none transition-[transform,box-shadow] duration-75 disabled:opacity-50"
                          :title="t('imports.api_revoke_title')">
                    <TrashIcon class="h-4 w-4" />
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
            <div class="shrink-0 rounded-sm border-2 border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-700 p-2 w-10 h-10 flex items-center justify-center">
              <span class="text-gray-900 dark:text-gray-100 font-extrabold text-sm leading-none tracking-tight">SM</span>
            </div>
            <div class="flex-1 min-w-0">
              <span class="font-medium text-gray-900 dark:text-gray-100 text-sm">{{ t('imports.tab_spritmonitor') }}</span>
            </div>
            <ChevronDownIcon :class="['h-5 w-5 text-gray-400 shrink-0 transition-transform duration-200', activeTab === 'spritmonitor' ? 'rotate-180' : '']" />
          </button>
          <Transition name="accordion">
            <div v-if="activeTab === 'spritmonitor'" class="border-t-2 border-gray-300 dark:border-gray-700 p-4 md:p-5 space-y-4">
              <p class="text-sm text-gray-700 dark:text-gray-300 font-medium leading-relaxed">{{ t('imports.sprit_desc') }}</p>
              <ul class="space-y-2">
                <li v-for="i in 4" :key="i" class="flex items-start gap-2.5 text-sm text-gray-700 dark:text-gray-300">
                  <span class="shrink-0 w-5 h-5 bg-sky-700 text-white rounded-sm flex items-center justify-center text-[10px] font-extrabold mt-0.5">→</span>
                  <span class="font-medium">{{ t(`imports.sprit_feat${i}`) }}</span>
                </li>
              </ul>
              <button @click="showSpritMonitorModal = true"
                      class="flex w-full sm:w-auto sm:inline-flex items-center justify-center gap-2 bg-sky-700 hover:bg-sky-600 text-white font-bold uppercase tracking-wider text-xs px-5 py-3 rounded-sm border-2 border-sky-700 shadow-[2px_2px_0_0_#030712] active:translate-x-[3px] active:translate-y-[3px] active:shadow-none transition-[transform,box-shadow] duration-75">
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
            <div class="shrink-0 rounded-sm border-2 border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-700 p-2 w-10 h-10 flex items-center justify-center">
              <span class="text-gray-900 dark:text-gray-100 font-extrabold text-sm leading-none tracking-tight">TR</span>
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

        <!-- 5. TESSIE -->
        <div>
          <button
            @click="toggle('tessie'); analytics.trackImportTabClicked('tessie')"
            class="w-full flex items-center gap-3 px-4 py-4 text-left hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors"
          >
            <div class="shrink-0 rounded-sm border-2 border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-700 p-2 w-10 h-10 flex items-center justify-center">
              <span class="text-gray-900 dark:text-gray-100 font-extrabold text-sm leading-none tracking-tight">TS</span>
            </div>
            <div class="flex-1 min-w-0">
              <span class="font-medium text-gray-900 dark:text-gray-100 text-sm">{{ t('imports.tab_tessie') }}</span>
            </div>
            <ChevronDownIcon :class="['h-5 w-5 text-gray-400 shrink-0 transition-transform duration-200', activeTab === 'tessie' ? 'rotate-180' : '']" />
          </button>
          <Transition name="accordion">
            <div v-if="activeTab === 'tessie'" class="border-t-2 border-gray-300 dark:border-gray-700 p-4 md:p-5 space-y-4">
              <p class="text-sm text-gray-700 dark:text-gray-300 font-medium leading-relaxed">{{ t('imports.tessie_desc') }}</p>
              <ul class="space-y-2">
                <li v-for="i in 3" :key="i" class="flex items-start gap-2.5 text-sm text-gray-700 dark:text-gray-300">
                  <span class="shrink-0 w-5 h-5 bg-emerald-700 text-white rounded-sm flex items-center justify-center text-[10px] font-extrabold mt-0.5">→</span>
                  <span class="font-medium">{{ t(`imports.tessie_feat${i}`) }}</span>
                </li>
              </ul>
              <button @click="showTessieModal = true"
                      class="flex w-full sm:w-auto sm:inline-flex items-center justify-center gap-2 bg-emerald-700 hover:bg-emerald-600 text-white font-bold uppercase tracking-wider text-xs px-5 py-3 rounded-sm border-2 border-emerald-700 shadow-[2px_2px_0_0_#030712] active:translate-x-[3px] active:translate-y-[3px] active:shadow-none transition-[transform,box-shadow] duration-75">
                <ArrowDownTrayIcon class="h-4 w-4" />
                {{ t('imports.tessie_btn') }}
              </button>
            </div>
          </Transition>
        </div>

        <!-- 6. XPENG (nur sichtbar bei XPeng-Fahrzeug in Garage) -->
        <div v-if="hasXpeng">
          <button
            @click="toggle('xpeng'); analytics.trackImportTabClicked('xpeng')"
            class="w-full flex items-center gap-3 px-4 py-4 text-left hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors"
          >
            <div class="shrink-0 rounded-sm border-2 border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-700 p-2 w-10 h-10 flex items-center justify-center">
              <span class="text-gray-900 dark:text-gray-100 font-extrabold text-sm leading-none tracking-tight">XP</span>
            </div>
            <div class="flex-1 min-w-0">
              <span class="font-medium text-gray-900 dark:text-gray-100 text-sm">{{ t('imports.tab_xpeng') }}</span>
            </div>
            <ChevronDownIcon :class="['h-5 w-5 text-gray-400 shrink-0 transition-transform duration-200', activeTab === 'xpeng' ? 'rotate-180' : '']" />
          </button>
          <Transition name="accordion">
            <div v-if="activeTab === 'xpeng'" class="border-t border-gray-100 dark:border-gray-700 p-4">
              <XpengImport :cars="activeCars" />
            </div>
          </Transition>
        </div>


        <!-- 7. EU DATA ACT (VW Group) -->
        <div>
          <button
            @click="toggle('eu_data_act'); analytics.trackImportTabClicked('eu_data_act')"
            class="w-full flex items-center gap-3 px-4 py-4 text-left hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors"
          >
            <div class="shrink-0 rounded-sm border-2 border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-700 p-2 w-10 h-10 flex items-center justify-center">
              <span class="text-gray-900 dark:text-gray-100 font-extrabold text-[11px] leading-none tracking-tight">VW</span>
            </div>
            <div class="flex-1 min-w-0">
              <div class="flex items-center gap-2 flex-wrap">
                <span class="font-medium text-gray-900 dark:text-gray-100 text-sm">{{ t('imports.tab_eu_data_act') }}</span>
                <span class="text-[10px] font-bold uppercase tracking-wider bg-amber-100 dark:bg-amber-900/40 text-amber-700 dark:text-amber-400 px-1.5 py-0.5 rounded">Beta</span>
              </div>
              <p class="text-xs text-gray-500 dark:text-gray-400 mt-0.5">{{ t('imports.tab_eu_data_act_desc') }}</p>
            </div>
            <ChevronDownIcon :class="['h-5 w-5 text-gray-400 shrink-0 transition-transform duration-200', activeTab === 'eu_data_act' ? 'rotate-180' : '']" />
          </button>
          <Transition name="accordion">
            <div v-if="activeTab === 'eu_data_act'" class="border-t border-gray-100 dark:border-gray-700 p-4">
              <EUDataActImport :cars="activeCars" @close="toggle('eu_data_act')" />
            </div>
          </Transition>
        </div>

        <!-- 8. MANUELL -->
        <div>
          <button
            @click="toggle('manuell'); analytics.trackImportTabClicked('manuell')"
            class="w-full flex items-center gap-3 px-4 py-4 text-left hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors"
          >
            <div class="shrink-0 rounded-sm border-2 border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-700 p-2 w-10 h-10 flex items-center justify-center">
              <ArrowDownTrayIcon class="h-5 w-5 text-gray-700 dark:text-gray-200" />
            </div>
            <div class="flex-1 min-w-0">
              <span class="font-medium text-gray-900 dark:text-gray-100 text-sm">{{ t('imports.tab_manuell') }}</span>
            </div>
            <ChevronDownIcon :class="['h-5 w-5 text-gray-400 shrink-0 transition-transform duration-200', activeTab === 'manuell' ? 'rotate-180' : '']" />
          </button>
          <Transition name="accordion">
            <div v-if="activeTab === 'manuell'" class="border-t-2 border-gray-300 dark:border-gray-700 p-4 md:p-5 space-y-4">
              <p class="text-sm text-gray-700 dark:text-gray-300 font-medium leading-relaxed">{{ t('imports.manuell_desc') }}</p>
              <ul class="space-y-2">
                <li v-for="i in 5" :key="i" class="flex items-start gap-2.5 text-sm text-gray-700 dark:text-gray-300">
                  <span class="shrink-0 w-5 h-5 bg-green-700 text-white rounded-sm flex items-center justify-center text-[10px] font-extrabold mt-0.5">→</span>
                  <span class="font-medium">{{ t(`imports.manuell_feat${i}`) }}</span>
                </li>
              </ul>
              <div v-if="activeCars.length > 1" class="space-y-1.5">
                <label class="block text-[11px] font-bold uppercase tracking-wider text-gray-500 dark:text-gray-400">{{ t('imports.manuell_select_car') }}</label>
                <CarSelectDropdown :cars="activeCars" v-model="manualImportCarId" />
              </div>
              <button
                @click="manualImportCarId = activeCars.length === 1 ? activeCars[0].id : manualImportCarId; showManualImportModal = true"
                :disabled="activeCars.length === 0 || (activeCars.length > 1 && !manualImportCarId)"
                class="w-full inline-flex items-center justify-center gap-2 bg-green-700 hover:bg-green-600 disabled:bg-gray-300 dark:disabled:bg-gray-700 disabled:text-gray-500 disabled:cursor-not-allowed text-white font-bold uppercase tracking-wider text-xs px-5 py-3 rounded-sm border-2 border-green-700 disabled:border-gray-300 dark:disabled:border-gray-700 shadow-[2px_2px_0_0_#030712] disabled:shadow-none active:translate-x-[3px] active:translate-y-[3px] active:shadow-none transition-[transform,box-shadow] duration-75"
              >
                <ArrowDownTrayIcon class="h-4 w-4" />
                {{ t('imports.manuell_btn') }}
              </button>
              <p v-if="activeCars.length === 0" class="text-sm font-medium border-l-2 border-amber-500 bg-amber-50 dark:bg-amber-950/30 text-gray-700 dark:text-gray-200 px-4 py-3 rounded-r-sm">
                {{ t('imports.manuell_no_car') }}
                <router-link to="/cars" class="font-bold underline hover:no-underline ml-1">{{ t('imports.manuell_no_car_link') }}</router-link>
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
            <div class="shrink-0 rounded-sm border-2 border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-700 p-2 w-10 h-10 flex items-center justify-center">
              <span class="text-gray-900 dark:text-gray-100 font-extrabold text-sm leading-none tracking-tight">GE</span>
            </div>
            <div class="flex-1 min-w-0">
              <span class="font-medium text-gray-900 dark:text-gray-100 text-sm">{{ t('imports.tab_goe') }}</span>
            </div>
            <ChevronDownIcon :class="['h-5 w-5 text-gray-400 shrink-0 transition-transform duration-200', activeTab === 'goe' ? 'rotate-180' : '']" />
          </button>
          <Transition name="accordion">
            <div v-if="activeTab === 'goe'" class="border-t-2 border-gray-300 dark:border-gray-700 p-4 md:p-5 space-y-4">
              <p class="text-sm text-gray-700 dark:text-gray-300 font-medium leading-relaxed">{{ t('imports.goe_desc') }}</p>
              <div class="border-l-2 border-amber-500 bg-amber-50 dark:bg-amber-950/40 px-4 py-3 rounded-r-sm">
                <p class="text-[11px] font-bold uppercase tracking-wider text-amber-700 dark:text-amber-400 mb-1">Hinweis</p>
                <p class="text-xs text-gray-700 dark:text-gray-200 font-medium leading-relaxed">{{ t('imports.goe_parallel_hint') }}</p>
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
            <div class="shrink-0 rounded-sm border-2 border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-700 p-2 w-10 h-10 flex items-center justify-center">
              <BoltIcon class="h-5 w-5 text-gray-700 dark:text-gray-200" />
            </div>
            <div class="flex-1 min-w-0">
              <div class="flex items-center gap-2 flex-wrap">
                <span class="font-medium text-gray-900 dark:text-gray-100 text-sm">{{ t('imports.tab_wallbox') }}</span>
                <span class="text-[10px] font-bold uppercase tracking-wider bg-blue-600 text-white px-1.5 py-0.5 rounded-sm leading-none">BETA</span>
              </div>
            </div>
            <ChevronDownIcon :class="['h-5 w-5 text-gray-400 shrink-0 transition-transform duration-200', activeTab === 'wallbox' ? 'rotate-180' : '']" />
          </button>
          <Transition name="accordion">
            <div v-if="activeTab === 'wallbox'" class="border-t-2 border-gray-300 dark:border-gray-700 p-4 md:p-5 space-y-4">
              <p class="text-sm text-gray-700 dark:text-gray-300 font-medium leading-relaxed">{{ t('imports.wallbox_desc') }}</p>
              <div class="border-l-2 border-blue-500 bg-blue-50 dark:bg-blue-950/40 px-4 py-3 rounded-r-sm">
                <p class="text-[11px] font-bold uppercase tracking-wider text-blue-700 dark:text-blue-400 mb-1">Beta-Hinweis</p>
                <p class="text-xs text-gray-700 dark:text-gray-200 font-medium leading-relaxed">{{ t('imports.wallbox_beta_hint') }}</p>
              </div>
              <div class="border-l-2 border-amber-500 bg-amber-50 dark:bg-amber-950/40 px-4 py-3 rounded-r-sm space-y-1.5">
                <p class="text-[11px] font-bold uppercase tracking-wider text-amber-700 dark:text-amber-400">{{ t('imports.wallbox_ocpp_warning_title') }}</p>
                <p class="text-xs text-gray-700 dark:text-gray-200 font-medium leading-relaxed" v-html="t('imports.wallbox_ocpp_warning_desc')" />
                <p class="text-xs text-gray-700 dark:text-gray-200 font-medium leading-relaxed">{{ t('imports.wallbox_ocpp_goe_hint_pre') }} <button @click="toggle('goe')" class="underline font-bold cursor-pointer">{{ t('imports.wallbox_ocpp_goe_link') }}</button> {{ t('imports.wallbox_ocpp_goe_hint_post') }}</p>
              </div>
              <router-link to="/wallbox"
                           class="flex w-full sm:w-auto sm:inline-flex items-center justify-center gap-2 bg-slate-700 hover:bg-slate-600 text-white font-bold uppercase tracking-wider text-xs px-5 py-3 rounded-sm border-2 border-slate-700 shadow-[2px_2px_0_0_#030712] active:translate-x-[3px] active:translate-y-[3px] active:shadow-none transition-[transform,box-shadow] duration-75">
                <BoltIcon class="h-4 w-4" />
                {{ t('imports.wallbox_btn') }}
              </router-link>
            </div>
          </Transition>
        </div>

        <!-- 8. TESLA TELEMETRY - shown to every Tesla owner. Tesla Fleet-Telemetry is
             free (no AutoSync subscription needed), so pairing lives here, decoupled
             from the paid AutoSync section above (which is Smartcar brands only). -->
        <div v-if="showTeslaTab">
          <button
            @click="toggle('tesla'); analytics.trackImportTabClicked('tesla')"
            class="w-full flex items-center gap-3 px-4 py-4 text-left hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors"
          >
            <div class="shrink-0 rounded-sm border-2 border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-700 p-2 w-10 h-10 flex items-center justify-center">
              <span class="text-gray-900 dark:text-gray-100 font-extrabold text-sm leading-none tracking-tight">T</span>
            </div>
            <div class="flex-1 min-w-0">
              <span class="font-medium text-gray-900 dark:text-gray-100 text-sm">
                {{ activeTab === 'tesla' && teslaConnectedLabel ? t('tesla.connected_prefix') + ' ' + teslaConnectedLabel : t('imports.tab_tesla_telemetry') }}
              </span>
            </div>
            <ChevronDownIcon :class="['h-5 w-5 text-gray-400 shrink-0 transition-transform duration-200', activeTab === 'tesla' ? 'rotate-180' : '']" />
          </button>
          <Transition name="accordion">
            <div v-if="activeTab === 'tesla'" class="p-4 md:p-5">
              <TeslaFleetIntegration @connected-label="teslaConnectedLabel = $event" />
            </div>
          </Transition>
        </div>

        </div><!-- end accordion -->
      </div>
    </Transition>
  </div>

  <!-- Sprit-Monitor Import Modal -->
  <SpritMonitorImport v-if="showSpritMonitorModal" @close="showSpritMonitorModal = false" />

  <!-- Tessie Import Modal -->
  <TessieImport v-if="showTessieModal" :cars="activeCars" @close="showTessieModal = false" />

  <!-- Manual Import Modal -->
  <ManualImportModal
    v-if="showManualImportModal && manualImportCarId"
    :car-id="manualImportCarId"
    @close="showManualImportModal = false"
    @imported="(count) => { showManualImportModal = false; analytics.trackImportCompleted('manual', count) }"
  />
  <DemoImportsModal v-if="authStore.isDemoAccount" />
</div>
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
