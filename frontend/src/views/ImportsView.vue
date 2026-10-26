<script setup lang="ts">
import { carDisplayName } from '@/utils/enumLabel'
import { ref, computed, onMounted, defineAsyncComponent } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '../stores/auth'
import { purchasesAvailable } from '../utils/iapPolicy'
import {
  ArrowDownTrayIcon, ArrowPathIcon, ArrowRightIcon, BoltIcon, CodeBracketIcon, TrashIcon, ClipboardDocumentIcon, CheckIcon, CheckCircleIcon, ExclamationCircleIcon,
} from '@heroicons/vue/24/outline'
import ImportSection from '../components/imports/ImportSection.vue'
import { CI_BTN_PRIMARY, CI_BTN_SECONDARY, CI_ICON_BTN, CI_INPUT, CI_LABEL, CI_CARD, CI_NOTE } from '../utils/ciClasses'
import SpritMonitorImport from '../components/imports/SpritMonitorImport.vue'
import GoeIntegration from '../components/imports/GoeIntegration.vue'
const PolestarIntegration = defineAsyncComponent(() => import('../components/imports/PolestarIntegration.vue'))
import TeslaFleetIntegration from '../components/imports/TeslaFleetIntegration.vue'
import AutoSyncCarPicker from '../components/imports/AutoSyncCarPicker.vue'
import ManualImportModal from '../components/imports/ManualImportModal.vue'
const TronityImport = defineAsyncComponent(() => import('../components/imports/TronityImport.vue'))
import TessieImport from '../components/imports/TessieImport.vue'
const XpengImport = defineAsyncComponent(() => import('../components/imports/XpengImport.vue'))
const VwEudaImport = defineAsyncComponent(() => import('../components/imports/VwEudaImport.vue'))
const VwEudaAutoSync = defineAsyncComponent(() => import('../components/imports/VwEudaAutoSync.vue'))
import CarSelectDropdown from '../components/car/CarSelectDropdown.vue'
import type { Car } from '../api/carService'
import vwEudaSyncService, { isVwEudaBrand } from '../api/vwEudaSyncService'
import { useCarStore } from '../stores/car'
import { useImportsTab, featuredImportSection, importSectionOrder, type Tab } from '../composables/useImportsTab'
import { useImportGating } from '../composables/useImportGating'
import { apiKeyService, type ApiKeyResponse, type ApiKeyCreatedResponse } from '../api/apiKeyService'
import { analytics } from '../services/analytics'
import DemoImportsModal from '../components/demo/DemoImportsModal.vue'
import ImportAccordion from '../components/imports/ImportAccordion.vue'
import { subscriptionService, type SubscriptionTier } from '../api/subscriptionService'

const { t } = useI18n()
const { activeTab, toggle } = useImportsTab()
function onToggle(tab: Tab) {
  toggle(tab)
  analytics.trackImportTabClicked(tab)
}
// Feldnamen im API-Block und Merkmal-Listen der Sektionen
const CODE = 'rounded-md bg-gray-100 px-1.5 py-0.5 font-mono text-[12px] text-gray-800 dark:bg-gray-800 dark:text-gray-200'
const FEATURE = 'flex items-start gap-2.5 text-[15px] text-gray-700 dark:text-gray-300'
// Die relevanteste Sektion steht oben und startet aufgeklappt - beides aus derselben Regel,
// damit Position und Aufklapp-Zustand nicht auseinanderlaufen.
const featuredSection = ref<Tab | null>(null)
const sectionOrder = computed(() => importSectionOrder(featuredSection.value))
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
const subscriptionTier = ref<SubscriptionTier>('NONE')
const liveUpgradeLoading = ref(false)
const liveUpgradeError = ref('')
// Waehrend des EU-Data-Act-Trials ist AutoSync fuer diesen Nutzer schon freigeschaltet;
// ein Kauf-Teaser darueber widerspraeche dem, was die Karte darunter sagt.
const eudaTrialActive = ref(false)

// Sichtbarkeit aller Import-Sektionen haengt am aktiven Auto - siehe useImportGating.
const {
  activeCarIsTesla,
  activeCarIsVwEudaBrand,
  activeCarIsXpeng,
  activeCarIsPolestar,
  showTeslaSection,
  showAutoSyncSection,
  showSmartcarPitch,
  allowLivePromo,
  smartcarCars,
} = useImportGating(cars)

const hasXpeng = computed(() => cars.value.some(c => c.brand === 'XPENG'))


// Live-Promo: AutoSync-Abonnent mit Tesla in der Garage. allowLivePromo sperrt
// sie fuer aktive Tesla (kein Upsell) und aktive XPeng (Live ist Tesla-only).
const showLivePromo = computed(() =>
  subscriptionTier.value === 'AUTOSYNC' && allowLivePromo.value && purchasesAvailable())

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
  try {
    cars.value = await carStore.getCars() ?? []
  } catch { /* ignore */ }

  // Erst nach dem Laden der Autos, das aktive Auto entscheidet: Tesla-Fahrer landen im
  // Tesla-Tab, VW-Group-Fahrer im EU-Data-Act-Tab - dort ist AutoSync fuer sie der Hauptweg.
  const params = new URLSearchParams(window.location.search)
  featuredSection.value = featuredImportSection({
    returningFromSmartcar: Boolean(params.get('smartcar-connected') || params.get('smartcar-error')),
    activeCarIsTesla: activeCarIsTesla.value,
    activeCarIsVwEudaBrand: activeCarIsVwEudaBrand.value,
    activeCarIsXpeng: activeCarIsXpeng.value,
    activeCarIsPolestar: activeCarIsPolestar.value,
    hasAutoSync: authStore.isPremium,
  })
  activeTab.value = featuredSection.value

  await new Promise(resolve => setTimeout(resolve, 100))
  loading.value = false

  fetchApiKeys()
  if (activeCarIsVwEudaBrand.value) {
    vwEudaSyncService.getEntitlement()
      .then(e => { eudaTrialActive.value = e.entitled && e.viaTrial })
      .catch(() => {})
  }
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

const activeCars = computed(() =>
  Array.isArray(cars.value) ? cars.value.filter(c => c.status === 'ACTIVE') : []
)

/** AutoSync gibt es nur fuer Marken, die das VW-EU-Data-Act-Portal bedient. */
const vwGroupCars = computed(() => activeCars.value.filter(c => isVwEudaBrand(c.brand)))

/** Polestar Data Portal Testmodus: nur fuer Polestar-Fahrzeuge in der Garage. */
const polestarCars = computed(() => activeCars.value.filter(c => (c.brand ?? '').toUpperCase() === 'POLESTAR'))

const autoSyncActiveCarLabel = ref<string | null>(null)
const teslaConnectedLabel = ref<string | null>(null)
</script>

<template>
<div>
  <div class="mx-auto max-w-3xl pb-6 md:px-6 md:pb-12">
    <Transition name="fade" mode="out-in">
      <!-- Eine Box wie /settings und /cars: mobil edge-to-edge, ab md mit Kante -->
      <section v-if="!loading" class="overflow-hidden bg-white md:rounded-3xl md:edge-box dark:bg-gray-900" data-testid="imports-box">
        <header class="grid gap-2 px-4 pb-5 pt-6 md:px-8 md:pt-8">
          <h1 class="text-[28px] font-bold leading-[1.1] text-gray-900 md:text-[36px] dark:text-gray-50">{{ t('imports.title') }}</h1>
          <p class="text-[15px] text-gray-600 dark:text-gray-400">{{ t('imports.subtitle') }}</p>
        </header>

        <!-- AutoSync Pro Teaser - der Kauf-CTA. Nur wenn der User ueberhaupt ein
             Smartcar-Fahrzeug hat: Tesla-Telemetry und XPeng-AutoSync sind gratis,
             das Abo wuerde diesen Fahrzeugen nichts bringen. Sekundaerer Knopf: die gruene
             Handlung gehoert der aufgeklappten Sektion. -->
        <div v-if="showSmartcarPitch && premiumEnabled && !authStore.isPremium && !eudaTrialActive"
             :class="[CI_NOTE, 'mx-4 mb-5 flex flex-col gap-3 md:mx-8 md:flex-row md:items-center md:gap-4']">
          <BoltIcon class="hidden h-6 w-6 shrink-0 text-gray-500 md:block dark:text-gray-400" aria-hidden="true" />
          <div class="min-w-0 flex-1">
            <p :class="CI_LABEL">EV Monitor AutoSync</p>
            <p class="mt-0.5 font-semibold text-gray-900 dark:text-gray-100">{{ t('imports.autosync_teaser_title') }}</p>
            <p class="mt-1 text-[13px] leading-relaxed text-gray-600 dark:text-gray-400">{{ t('imports.autosync_teaser_desc') }}</p>
          </div>
          <div v-if="purchasesAvailable()" class="flex shrink-0 flex-col gap-1 md:items-end">
            <router-link to="/upgrade" :class="CI_BTN_SECONDARY">{{ t('imports.autosync_teaser_cta') }}</router-link>
            <span class="text-center text-[12px] tabular-nums text-gray-500 dark:text-gray-400">{{ t('imports.autosync_teaser_price', { priceMonthly: t('upgrade.price_monthly') }) }}</span>
          </div>
        </div>

        <ImportAccordion :order="sectionOrder">

        <template #tesla>
        <!-- TESLA TELEMETRY - shown to every Tesla owner, first in the list.
             Tesla Fleet-Telemetry is free (no AutoSync subscription needed), so
             pairing lives here, decoupled from the paid AutoSync section below
             (which is Smartcar brands only). -->
        <ImportSection v-if="showTeslaSection" mark="T" :open="activeTab === 'tesla'" @toggle="onToggle('tesla')"
          :title="activeTab === 'tesla' && teslaConnectedLabel ? t('tesla.connected_prefix') + ' ' + teslaConnectedLabel : t('imports.tab_tesla_telemetry')">
          <TeslaFleetIntegration @connected-label="teslaConnectedLabel = $event" />
        </ImportSection>
        </template>

        <template #smartcar>
        <!-- AUTOSYNC - fuer Tesla-Fahrer komplett ausgeblendet. Enthaelt den
             Smartcar-Weg (kostenpflichtig) und den XPeng-Weg (EU Data Act, gratis). -->
        <ImportSection v-if="showAutoSyncSection" :icon="ArrowPathIcon" :open="activeTab === 'smartcar'" @toggle="onToggle('smartcar')"
          :title="t('imports.tab_smartcar')"
          :status="autoSyncActiveCarLabel ? t('imports.autosync_header_active', { car: autoSyncActiveCarLabel }) : undefined">
          <div class="grid gap-3">
            <!-- Live-Promo: AutoSync-Abonnent mit Tesla in der Garage. Sichtbar nur,
                 wenn das aktive Auto KEIN Tesla ist - sonst ist diese Sektion weg. -->
            <div v-if="showLivePromo" :class="[CI_NOTE, 'flex flex-col gap-3 sm:flex-row sm:items-center']">
              <div class="min-w-0 flex-1">
                <p :class="CI_LABEL">AutoSync Live</p>
                <ul class="mt-1 grid gap-0.5 text-[13px] text-gray-700 dark:text-gray-300">
                  <li v-html="t('imports.live_promo_feature_trip')"></li>
                  <li v-html="t('imports.live_promo_feature_drain')"></li>
                </ul>
              </div>
              <button type="button" :class="CI_BTN_SECONDARY" :disabled="liveUpgradeLoading" @click="handleLiveUpgrade">
                <span v-if="liveUpgradeLoading">…</span>
                <span v-else>{{ t('imports.live_promo_cta') }}</span>
              </button>
              <p v-if="liveUpgradeError" role="alert" class="flex w-full items-start gap-2 text-[13px] font-semibold text-gray-900 dark:text-gray-100">
                <ExclamationCircleIcon class="h-5 w-5 shrink-0 text-gray-600 dark:text-gray-300" aria-hidden="true" />{{ liveUpgradeError }}
              </p>
            </div>

            <!-- Tile-based picker per car. Smartcar brands only - Tesla pairs in
                 seiner eigenen Sektion, XPeng laeuft ueber den EU-Data-Act-Weg darunter.
                 Premium=1-active-connection limit is surfaced in tile-locking.
                 Non-premium users see the Smartcar upgrade pitch inside the picker. -->
            <AutoSyncCarPicker
              v-if="showSmartcarPitch"
              :cars="smartcarCars"
              :premium-enabled="premiumEnabled"
              :is-premium="subscriptionIsPremium"
              :has-auto-sync-access="authStore.isPremium"
              @active-car-label="autoSyncActiveCarLabel = $event"
            />
          </div>
        </ImportSection>
        </template>

        <template #api>
        <ImportSection :icon="CodeBracketIcon" :open="activeTab === 'api'" @toggle="onToggle('api')" :title="t('imports.tab_api')">
          <div class="grid gap-5">
            <p class="text-[15px] leading-relaxed text-gray-700 dark:text-gray-300">{{ t('imports.api_desc') }}</p>

            <!-- Endpoint -->
            <div :class="[CI_CARD, 'grid gap-2']">
              <p :class="CI_LABEL">Endpoint</p>
              <p class="break-all rounded-xl bg-gray-100 px-3 py-2 font-mono text-[12.5px] text-gray-800 dark:bg-gray-900 dark:text-gray-100">
                POST https://ev-monitor.net/api/v1/sessions<br>
                Authorization: Bearer evm_&lt;dein-key&gt;
              </p>
              <p class="text-[13px] text-gray-700 dark:text-gray-300"><span :class="CI_LABEL">{{ t('imports.api_required') }}</span> <code :class="CODE">date</code>, <code :class="CODE">kwh</code></p>
              <p class="text-[13px] leading-relaxed text-gray-700 dark:text-gray-300"><span :class="CI_LABEL">{{ t('imports.api_optional') }}</span> <code :class="CODE">odometer_km</code>, <code :class="CODE">soc_after</code>, <code :class="CODE">cost_eur</code>, <code :class="CODE">duration_min</code>, <code :class="CODE">location</code>, <code :class="CODE">charging_type</code> (AC/DC)</p>
              <p class="text-[13px] text-gray-500 dark:text-gray-400">{{ t('imports.api_dedup') }}</p>
              <p class="text-[13px] text-gray-500 dark:text-gray-400" v-html="t('imports.api_date_hint')" />
              <a href="/swagger-ui/index.html" target="_blank" class="inline-flex min-h-11 items-center gap-1.5 justify-self-start text-sm font-semibold text-gray-900 underline underline-offset-2 hover:no-underline dark:text-gray-100">
                {{ t('imports.api_docs') }}<ArrowRightIcon class="h-4 w-4" aria-hidden="true" />
              </a>
            </div>

            <!-- Fahrzeug-IDs -->
            <div v-if="activeCars.length > 0" class="grid gap-2">
              <p :class="CI_LABEL">{{ t('imports.api_car_ids_title') }}</p>
              <p class="text-[13px] leading-relaxed text-gray-500 dark:text-gray-400" v-html="t('imports.api_car_ids_hint')" />
              <div v-for="car in activeCars" :key="car.id" :class="[CI_CARD, 'flex items-center gap-3 py-3']">
                <div class="min-w-0 flex-1">
                  <p class="truncate text-sm font-semibold text-gray-900 dark:text-gray-100">{{ car.year }} {{ carDisplayName(car) }}<span v-if="car.licensePlate" class="font-normal text-gray-500 dark:text-gray-400"> · {{ car.licensePlate }}</span></p>
                  <p class="mt-0.5 select-all font-mono text-[12px] text-gray-500 dark:text-gray-400">{{ car.id }}</p>
                </div>
                <button type="button" :class="CI_ICON_BTN" :title="t('cars.api_id_copy')" :aria-label="t('cars.api_id_copy')" @click="copyCarId(car.id)">
                  <CheckIcon v-if="copiedCarId === car.id" class="h-5 w-5 text-green-700 dark:text-green-400" aria-hidden="true" />
                  <ClipboardDocumentIcon v-else class="h-5 w-5" aria-hidden="true" />
                </button>
              </div>
            </div>

            <div v-if="apiKeyMessage" role="status" :class="[CI_NOTE, 'flex items-start gap-2 py-3 font-semibold text-gray-900 dark:text-gray-100']">
              <CheckCircleIcon v-if="apiKeyMessage.type === 'success'" class="h-5 w-5 shrink-0 text-green-700 dark:text-green-400" aria-hidden="true" />
              <ExclamationCircleIcon v-else class="h-5 w-5 shrink-0 text-gray-700 dark:text-gray-300" aria-hidden="true" />
              <span>{{ apiKeyMessage.text }}</span>
            </div>

            <div v-if="createdKey" :class="[CI_CARD, 'grid gap-2']">
              <p :class="CI_LABEL">{{ t('imports.api_new_key_title') }}</p>
              <div class="flex items-center gap-2">
                <code class="flex-1 break-all rounded-xl bg-gray-900 px-3 py-2 font-mono text-xs text-gray-100 dark:bg-black/40">{{ createdKey.plaintextKey }}</code>
                <button type="button" :class="CI_ICON_BTN" :title="t('imports.api_copy_title')" :aria-label="t('imports.api_copy_title')" @click="copyApiKey">
                  <CheckIcon v-if="keyCopied" class="h-5 w-5 text-green-700 dark:text-green-400" aria-hidden="true" />
                  <ClipboardDocumentIcon v-else class="h-5 w-5" aria-hidden="true" />
                </button>
              </div>
              <p class="text-[13px] font-semibold text-gray-700 dark:text-gray-300">{{ t('imports.api_save_key_hint') }}</p>
            </div>

            <div class="flex flex-col gap-2 sm:flex-row">
              <input v-model="newKeyName" type="text" :placeholder="t('imports.api_key_placeholder')" maxlength="100"
                     :class="[CI_INPUT, 'sm:flex-1']" @keyup.enter="createApiKey" />
              <button type="button" :class="[CI_BTN_PRIMARY, 'whitespace-nowrap']" :disabled="apiKeyLoading || !newKeyName.trim()" @click="createApiKey">
                {{ t('imports.api_create_btn') }}
              </button>
            </div>

            <p v-if="apiKeys.length === 0" :class="[CI_NOTE, 'py-3']">{{ t('imports.api_no_keys') }}</p>
            <div v-else class="grid gap-2">
              <div v-for="key in apiKeys" :key="key.id" :class="[CI_CARD, 'flex items-center justify-between gap-3 py-3']">
                <div class="min-w-0 flex-1">
                  <p class="truncate text-sm font-semibold text-gray-900 dark:text-gray-100">{{ key.name || t('imports.api_no_name') }}</p>
                  <p class="mt-0.5 font-mono text-[12px] text-gray-500 dark:text-gray-400">
                    <span class="text-gray-700 dark:text-gray-300">{{ key.keyPrefix }}…</span>
                    · {{ t('imports.api_last_used') }} {{ formatDate(key.lastUsedAt) }}
                    · {{ t('imports.api_created') }} {{ formatDate(key.createdAt) }}
                  </p>
                </div>
                <button type="button" :class="CI_ICON_BTN" :disabled="deletingKeyId === key.id"
                        :title="t('imports.api_revoke_title')" :aria-label="t('imports.api_revoke_title')" @click="deleteApiKey(key.id, key.name)">
                  <TrashIcon class="h-5 w-5" aria-hidden="true" />
                </button>
              </div>
            </div>
          </div>
        </ImportSection>
        </template>

        <template #spritmonitor>
        <ImportSection mark="SM" :open="activeTab === 'spritmonitor'" @toggle="onToggle('spritmonitor')" :title="t('imports.tab_spritmonitor')">
          <div class="grid gap-4">
            <p class="text-[15px] leading-relaxed text-gray-700 dark:text-gray-300">{{ t('imports.sprit_desc') }}</p>
            <ul class="grid gap-2">
              <li v-for="i in 4" :key="i" :class="FEATURE">
                <CheckIcon class="mt-0.5 h-5 w-5 shrink-0 text-gray-400" aria-hidden="true" />{{ t(`imports.sprit_feat${i}`) }}
              </li>
            </ul>
            <button type="button" :class="[CI_BTN_PRIMARY, 'w-full sm:w-auto sm:justify-self-start']" @click="showSpritMonitorModal = true">
              <ArrowDownTrayIcon class="h-5 w-5" aria-hidden="true" />{{ t('imports.sprit_btn') }}
            </button>
          </div>
        </ImportSection>
        </template>

        <template #tronity>
        <ImportSection mark="TR" :open="activeTab === 'tronity'" @toggle="onToggle('tronity')" :title="t('imports.tab_tronity')">
          <TronityImport />
        </ImportSection>
        </template>

        <template #tessie>
        <ImportSection mark="TS" :open="activeTab === 'tessie'" @toggle="onToggle('tessie')" :title="t('imports.tab_tessie')">
          <div class="grid gap-4">
            <p class="text-[15px] leading-relaxed text-gray-700 dark:text-gray-300">{{ t('imports.tessie_desc') }}</p>
            <ul class="grid gap-2">
              <li v-for="i in 3" :key="i" :class="FEATURE">
                <CheckIcon class="mt-0.5 h-5 w-5 shrink-0 text-gray-400" aria-hidden="true" />{{ t(`imports.tessie_feat${i}`) }}
              </li>
            </ul>
            <button type="button" :class="[CI_BTN_PRIMARY, 'w-full sm:w-auto sm:justify-self-start']" @click="showTessieModal = true">
              <ArrowDownTrayIcon class="h-5 w-5" aria-hidden="true" />{{ t('imports.tessie_btn') }}
            </button>
          </div>
        </ImportSection>
        </template>

        <template #xpeng>
        <!-- XPENG (nur sichtbar bei XPeng-Fahrzeug in Garage). Bewusst NUR die manuelle Stufe: User fordert
             selbst bei XPeng an und laedt die Excel hoch. Der automatische Weg (wir fragen an, wir verarbeiten)
             lebt in der AutoSync-Sektion - beides hier waere fuer den User nicht unterscheidbar. -->
        <ImportSection v-if="hasXpeng" mark="XP" :open="activeTab === 'xpeng'" @toggle="onToggle('xpeng')" :title="t('imports.tab_xpeng')">
          <XpengImport :cars="activeCars" />
        </ImportSection>
        </template>

        <template #eu_data_act>
        <!-- EU DATA ACT (VW Group): AutoSync zuerst (der bessere Weg), der manuelle Upload bleibt fuer alle darunter -->
        <ImportSection mark="VW" badge="Beta" :open="activeTab === 'eu_data_act'" @toggle="onToggle('eu_data_act')"
          :title="t('imports.tab_eu_data_act')" :subtitle="t('imports.tab_eu_data_act_desc')">
          <div class="grid gap-4">
            <VwEudaAutoSync v-if="vwGroupCars.length > 0" :cars="vwGroupCars" />
            <VwEudaImport :cars="activeCars" @close="toggle('eu_data_act')" />
          </div>
        </ImportSection>
        </template>

        <template #manuell>
        <ImportSection :icon="ArrowDownTrayIcon" :open="activeTab === 'manuell'" @toggle="onToggle('manuell')" :title="t('imports.tab_manuell')">
          <div class="grid gap-4">
            <p class="text-[15px] leading-relaxed text-gray-700 dark:text-gray-300">{{ t('imports.manuell_desc') }}</p>
            <ul class="grid gap-2">
              <li v-for="i in 6" :key="i" :class="FEATURE">
                <CheckIcon class="mt-0.5 h-5 w-5 shrink-0 text-gray-400" aria-hidden="true" />{{ t(`imports.manuell_feat${i}`) }}
              </li>
            </ul>
            <div v-if="activeCars.length > 1" class="grid gap-1.5">
              <label :class="CI_LABEL">{{ t('imports.manuell_select_car') }}</label>
              <CarSelectDropdown :cars="activeCars" v-model="manualImportCarId" />
            </div>
            <button type="button" :class="[CI_BTN_PRIMARY, 'w-full sm:w-auto sm:justify-self-start']"
              :disabled="activeCars.length === 0 || (activeCars.length > 1 && !manualImportCarId)"
              @click="manualImportCarId = activeCars.length === 1 ? activeCars[0].id : manualImportCarId; showManualImportModal = true">
              <ArrowDownTrayIcon class="h-5 w-5" aria-hidden="true" />{{ t('imports.manuell_btn') }}
            </button>
            <!-- Ohne Auto fehlt etwas: Amber mit Handlung -->
            <p v-if="activeCars.length === 0" class="rounded-2xl bg-amber-50 p-4 text-sm text-gray-800 dark:bg-amber-900/20 dark:text-gray-100">
              {{ t('imports.manuell_no_car') }}
              <router-link to="/cars" class="ml-1 font-semibold underline underline-offset-2 hover:no-underline">{{ t('imports.manuell_no_car_link') }}</router-link>
            </p>
          </div>
        </ImportSection>
        </template>

        <template #polestar>
        <!-- POLESTAR DATA PORTAL (Testmodus, nur sichtbar bei Polestar in der Garage) -->
        <ImportSection v-if="polestarCars.length > 0" data-testid="polestar-section" mark="PS" :open="activeTab === 'polestar'"
          @toggle="onToggle('polestar')" :title="t('imports.tab_polestar')" :subtitle="t('imports.tab_polestar_desc')">
          <PolestarIntegration :cars="polestarCars" />
        </ImportSection>
        </template>

        <template #goe>
        <ImportSection mark="GE" :open="activeTab === 'goe'" @toggle="onToggle('goe')" :title="t('imports.tab_goe')">
          <div class="grid gap-4">
            <p class="text-[15px] leading-relaxed text-gray-700 dark:text-gray-300">{{ t('imports.goe_desc') }}</p>
            <p :class="CI_NOTE">{{ t('imports.goe_parallel_hint') }}</p>
            <GoeIntegration />
          </div>
        </ImportSection>
        </template>

        <template #wallbox>
        <ImportSection :icon="BoltIcon" badge="Beta" :open="activeTab === 'wallbox'" @toggle="onToggle('wallbox')" :title="t('imports.tab_wallbox')">
          <div class="grid gap-4">
            <p class="text-[15px] leading-relaxed text-gray-700 dark:text-gray-300">{{ t('imports.wallbox_desc') }}</p>
            <p :class="CI_NOTE">{{ t('imports.wallbox_beta_hint') }}</p>
            <div :class="[CI_NOTE, 'grid gap-1.5']">
              <p :class="CI_LABEL">{{ t('imports.wallbox_ocpp_warning_title') }}</p>
              <p class="leading-relaxed" v-html="t('imports.wallbox_ocpp_warning_desc')" />
              <p class="leading-relaxed">{{ t('imports.wallbox_ocpp_goe_hint_pre') }} <button type="button" class="font-semibold underline underline-offset-2 hover:no-underline" @click="toggle('goe')">{{ t('imports.wallbox_ocpp_goe_link') }}</button> {{ t('imports.wallbox_ocpp_goe_hint_post') }}</p>
            </div>
            <router-link to="/wallbox" :class="[CI_BTN_PRIMARY, 'w-full sm:w-auto sm:justify-self-start']">
              <BoltIcon class="h-5 w-5" aria-hidden="true" />{{ t('imports.wallbox_btn') }}
            </router-link>
          </div>
        </ImportSection>
        </template>
        </ImportAccordion>
      </section>
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
    @imported="(count, type) => { showManualImportModal = false; analytics.trackImportCompleted(type === 'trips' ? 'manual_trips' : 'manual', count) }"
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
</style>
