<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { XMarkIcon, CurrencyEuroIcon, CheckCircleIcon, ChevronRightIcon, HomeIcon } from '@heroicons/vue/24/outline'
import WattBadge from '../shared/WattBadge.vue'
import { wattPossibleForLog } from '@/utils/wattPreview'
import { amendKwh } from '@/utils/priceAmend'
import { sourceInfo } from '@/utils/logSource'
import api from '@/api/axios'
import PriceAmendModal from './PriceAmendModal.vue'
import WattToast from '../shared/WattToast.vue'
import { useCoinStore } from '@/stores/coins'
import type { EvLogResponse } from './EditLogModal.vue'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import { activeHomeTariff } from '@/utils/homeTariff'
import type { ChargingProvider } from '@/composables/useChargingProviders'

const { t } = useI18n()
const { formatDecimal } = useLocaleFormat()
const props = defineProps<{ carId: string | null; open: boolean }>()
const emit = defineEmits<{ close: []; updated: [] }>()

const logs = ref<EvLogResponse[]>([])
const loading = ref(false)
const amendingLog = ref<EvLogResponse | null>(null)

/**
 * Heimtarif-Sammelnachtrag: Import-Quellen wie XPeng liefern weder Ort noch Preis, der
 * ortsbasierte Nachtrag greift dort also nie. Hat der User genau einen gueltigen Heimtarif,
 * bepreist ein Klick alle nicht-oeffentlichen Ladungen ohne Kosten auf einmal - sonst muesste
 * er jede einzeln nachtragen (bei Meigor 36 Stueck).
 */
const homeCard = ref<ChargingProvider | null>(null)
const applyingHome = ref(false)

async function loadHomeCard() {
  try {
    const res = await api.get('/users/me/charging-providers')
    homeCard.value = activeHomeTariff<ChargingProvider>(res.data)
  } catch { homeCard.value = null }
}

const homeError = ref<string | null>(null)

async function applyHomeTariff() {
  applyingHome.value = true
  homeError.value = null
  try {
    const res = await api.patch('/logs/apply-home-tariff')
    const priced = res.data?.priced ?? 0
    const coins = res.data?.coinsAwarded ?? 0
    if (coins) coinStore.refresh()
    // Kein Toast bei 0 bepreisten Ladungen - eine leere Erfolgsmeldung waere irrefuehrend.
    if (priced > 0) wattToast.value?.show(coins, t('priceless.home_applied', priced))
    else homeError.value = t('priceless.home_none')
    await loadLogs()
    emit('updated')
  } catch {
    homeError.value = t('priceless.home_error')
  } finally {
    applyingHome.value = false
  }
}

async function loadLogs() {
  if (!props.carId) return
  loading.value = true
  try {
    const res = await api.get(`/logs/priceless?carId=${props.carId}`)
    logs.value = res.data
  } finally {
    loading.value = false
  }
}

watch(() => props.open, (open) => {
  if (open) { loadLogs(); loadHomeCard() }
})

const wattToast = ref<InstanceType<typeof WattToast> | null>(null)
const coinStore = useCoinStore()
coinStore.ensureCatalog()
function onAmended(_log: EvLogResponse, coins: number, batch: number) {
  wattToast.value?.show(coins, batch > 0 ? t('priceamend.batch_done', batch) : '')
  if (coins) coinStore.refresh()
  amendingLog.value = null
  loadLogs()
  emit('updated')
}

function formatDate(iso: string) {
  return new Date(iso).toLocaleDateString(undefined, { day: '2-digit', month: '2-digit', year: 'numeric' })
}
</script>

<template>
  <Teleport to="body">
    <!-- z-40, damit das PriceAmendModal (BottomSheet, z-50) eindeutig darueber liegt statt nur per DOM-Reihenfolge -->
    <div v-if="open" class="fixed inset-0 z-40 flex items-end sm:items-center justify-center p-0 sm:p-4">
      <div class="absolute inset-0 bg-black/50" @click="emit('close')" />

      <div class="relative w-full sm:max-w-2xl bg-white dark:bg-gray-800 sm:rounded-sm shadow-[5px_5px_0_rgba(0,0,0,0.35)] dark:shadow-[5px_5px_0_rgba(255,255,255,0.35)] ring-1 ring-black/10 dark:ring-white/10 overflow-hidden flex flex-col max-h-[90dvh]">
        <!-- Header -->
        <div class="flex items-center justify-between px-5 py-4 border-b border-gray-100 dark:border-gray-700">
          <div class="flex items-center gap-2">
            <CurrencyEuroIcon class="h-5 w-5 text-amber-500" />
            <h2 class="text-base font-semibold text-gray-900 dark:text-gray-100">{{ t('priceless.title') }}</h2>
            <span v-if="logs.length > 0"
              class="text-xs bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-300 px-2 py-0.5 rounded-full font-medium tabular-nums">
              {{ logs.length }}
            </span>
          </div>
          <button @click="emit('close')" class="text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 p-1 rounded-sm">
            <XMarkIcon class="h-5 w-5" />
          </button>
        </div>

        <p class="px-5 pt-3 pb-1 text-xs text-gray-500 dark:text-gray-400">{{ t('priceless.info') }}</p>

        <!-- Ein Klick statt N Nachtraege - nur sichtbar, wenn genau ein Heimtarif gilt und
             es ueberhaupt etwas zu bepreisen gibt. -->
        <div v-if="homeCard && logs.length > 0" class="px-5 pt-2 pb-1">
          <button type="button" :disabled="applyingHome" @click="applyHomeTariff"
            class="btn-3d w-full flex items-center justify-center gap-2 px-4 py-2 rounded-sm bg-emerald-600 text-white text-sm font-medium hover:bg-emerald-700 disabled:opacity-50 transition">
            <HomeIcon class="h-4 w-4" aria-hidden="true" />
            {{ t('priceless.apply_home', { card: homeCard.label || homeCard.providerName }) }}
          </button>
          <p v-if="homeError" role="status" class="mt-1 text-[11px] text-amber-600 dark:text-amber-400">{{ homeError }}</p>
          <p v-else class="mt-1 text-[11px] text-gray-400 dark:text-gray-500">{{ t('priceless.apply_home_hint') }}</p>
        </div>

        <!-- Content -->
        <div class="overflow-y-auto flex-1">
          <div v-if="loading" class="flex items-center justify-center py-12">
            <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-gray-400" />
          </div>

          <template v-else>
            <div v-if="logs.length === 0" class="flex flex-col items-center justify-center py-12 gap-2">
              <CheckCircleIcon class="h-10 w-10 text-green-400" />
              <p class="text-sm text-gray-500 dark:text-gray-400">{{ t('priceless.all_clear') }}</p>
            </div>

            <ul v-else class="divide-y divide-gray-100 dark:divide-gray-700">
              <li v-for="log in logs" :key="log.id">
                <button type="button" @click="amendingLog = log"
                  class="w-full flex items-center gap-3 px-5 py-3 text-left hover:bg-gray-50 dark:hover:bg-gray-700/60 active:bg-gray-100 dark:active:bg-gray-700 transition-colors">
                  <div class="min-w-0 flex-1">
                    <div class="text-sm font-medium text-gray-900 dark:text-gray-100 tabular-nums">{{ formatDate(log.loggedAt) }}</div>
                    <div class="mt-0.5 flex items-center gap-2 text-xs text-gray-500 dark:text-gray-400">
                      <span class="tabular-nums">{{ amendKwh(log) != null ? formatDecimal(amendKwh(log)!, 1) + ' kWh' : t('priceamend.kwh_unknown') }}</span>
                      <span v-if="log.chargingType && log.chargingType !== 'UNKNOWN'">{{ log.chargingType }}</span>
                      <span v-if="sourceInfo(log.dataSource)">{{ sourceInfo(log.dataSource)!.label }}</span>
                    </div>
                  </div>
                  <WattBadge :amount="wattPossibleForLog(coinStore.catalog, log)" up-to size="md" />
                  <ChevronRightIcon class="h-4 w-4 shrink-0 text-gray-400" aria-hidden="true" />
                </button>
              </li>
            </ul>
          </template>
        </div>

        <!-- Footer -->
        <div class="px-5 py-3 border-t border-gray-100 dark:border-gray-700 flex justify-end">
          <button @click="emit('close')"
            class="px-4 py-2 text-sm font-medium text-gray-700 dark:text-gray-300 bg-gray-100 dark:bg-gray-700 hover:bg-gray-200 dark:hover:bg-gray-600 rounded-sm transition">
            {{ t('priceless.close') }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>

  <PriceAmendModal
    v-if="amendingLog"
    :log="amendingLog"
    @close="amendingLog = null"
    @saved="onAmended"
  />
  <WattToast ref="wattToast" />
</template>
