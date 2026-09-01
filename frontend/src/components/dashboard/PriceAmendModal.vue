<template>
  <BottomSheet
    ref="sheet"
    :label="t('priceamend.title')"
    testid="price-amend-modal"
    panel-class="sm:max-w-lg"
    @close="onClosed">
    <template #default="{ close }">
      <!-- Header -->
      <div class="flex items-center justify-between p-5 border-b border-gray-100 dark:border-gray-700 shrink-0">
        <h2 class="text-lg font-semibold text-gray-900 dark:text-gray-100">{{ t('priceamend.title') }}</h2>
        <button @click="close" class="text-gray-400 dark:text-gray-500 hover:text-gray-600 dark:hover:text-gray-300 transition-colors">
          <XMarkIcon class="w-5 h-5" />
        </button>
      </div>

      <div class="flex-1 overflow-y-auto p-5 space-y-5">
        <p class="text-sm text-gray-500 dark:text-gray-400">
          {{ t('priceamend.intro', { kwh: log.kwhCharged != null ? formatDecimal(log.kwhCharged, 1) : '?' }) }}
        </p>

        <!-- Ladekarte -->
        <div v-if="userProviders.length > 0" class="space-y-2">
          <label class="block text-sm font-medium text-gray-700 dark:text-gray-300">{{ t('priceamend.card_section') }}</label>
          <div class="flex gap-2.5 overflow-x-auto pb-2 -mx-1 px-1">
            <button
              v-for="p in userProviders"
              :key="p.id"
              type="button"
              @click="toggleProvider(p.id)"
              :class="[
                'btn-3d flex-shrink-0 w-28 h-[4.5rem] rounded-sm',
                selectedProviderId === p.id ? 'active opacity-100' : 'opacity-65 hover:opacity-85',
              ]"
              :style="{ '--btn-shadow-color': cardContainerStyle(p.id)['--btn-shadow-color'] }">
              <ChargingCardTile class="w-full h-full" :id="p.id" :title="p.label || p.providerName" :subtitle="cardPriceLabel(p)" />
            </button>
          </div>
          <p v-if="cardCostEur != null" class="text-xs text-gray-500 dark:text-gray-400">
            {{ t('priceamend.card_cost_hint', { cost: formatCurrency(cardCostEur) }) }}
          </p>
        </div>

        <!-- Preis manuell -->
        <div class="space-y-1">
          <label class="block text-sm font-medium text-gray-700 dark:text-gray-300">
            {{ userProviders.length > 0 ? t('priceamend.manual_label_or') : t('priceamend.manual_label') }}
          </label>
          <div class="relative">
            <input
              v-model="manualPriceInput"
              type="number" inputmode="decimal" min="0" step="0.01"
              :placeholder="t('priceamend.manual_placeholder')"
              class="w-full border border-gray-200 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-sm px-3 py-2 pr-10 text-sm focus:outline-none focus:ring-2 focus:ring-green-500" />
            <span class="absolute right-3 top-1/2 -translate-y-1/2 text-sm font-medium text-gray-400 pointer-events-none">{{ currencySymbol }}</span>
          </div>
        </div>

        <!-- Optional: Standort -->
        <div class="space-y-1">
          <label class="block text-sm font-medium text-gray-700 dark:text-gray-300">{{ t('priceamend.location_label') }}</label>
          <div class="relative">
            <input
              v-model="locationSearchQuery"
              type="text"
              :placeholder="t('logfields.location_search_placeholder')"
              class="w-full border border-gray-200 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-sm px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-green-500"
              @focus="showSuggestions = suggestions.length > 0" />
            <ul v-if="showSuggestions && suggestions.length > 0"
              class="absolute z-10 mt-1 w-full bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-sm shadow-[4px_4px_0_rgba(0,0,0,0.30)] max-h-48 overflow-y-auto">
              <li v-for="s in suggestions" :key="s.place_id"
                class="px-3 py-2 text-sm hover:bg-gray-50 dark:hover:bg-gray-700 cursor-pointer"
                @mousedown.prevent="selectLocation(s)">
                {{ s.display_name }}
              </li>
            </ul>
          </div>
          <p v-if="newLocationName" class="text-xs text-green-600 mt-1 flex items-center gap-1">
            <CheckIcon class="h-3.5 w-3.5 flex-shrink-0" /> {{ newLocationName }}
          </p>
        </div>

        <!-- Optional: oeffentliche Ladung + CPO -->
        <div class="space-y-2">
          <label class="flex items-center gap-2.5 cursor-pointer text-sm text-gray-700 dark:text-gray-300">
            <input type="checkbox" v-model="isPublic"
              class="h-4 w-4 rounded border-gray-300 text-indigo-600 focus:ring-2 focus:ring-indigo-500 dark:border-gray-600 dark:bg-gray-700" />
            {{ t('priceamend.public_label') }}
          </label>
          <select v-if="isPublic && cpo.hasOptions.value" v-model="cpoName" data-testid="amend-cpo-select"
            class="w-full border border-gray-200 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-sm px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-green-500">
            <option :value="null">{{ t('logfields.cpo_select_placeholder') }}</option>
            <optgroup v-if="cpo.nearbyCpos.value.length" :label="t('logfields.cpo_nearby_group')">
              <option v-for="c in cpo.nearbyCpos.value" :key="'n' + c" :value="c">{{ c }}</option>
            </optgroup>
            <optgroup v-if="cpo.otherCpos.value.length" :label="t('logfields.cpo_all_group')">
              <option v-for="c in cpo.otherCpos.value" :key="'o' + c" :value="c">{{ c }}</option>
            </optgroup>
          </select>
        </div>

        <p v-if="errorMsg" class="text-sm text-red-600 bg-red-50 rounded-sm p-3">{{ errorMsg }}</p>
      </div>

      <!-- Footer -->
      <div class="flex justify-end gap-3 p-5 border-t border-gray-100 dark:border-gray-700 shrink-0">
        <button @click="close" v-haptic
          class="btn-3d px-4 py-2 text-sm font-medium text-gray-700 dark:text-gray-300 bg-white dark:bg-gray-700 border border-gray-200 dark:border-gray-600 rounded-sm hover:bg-gray-50 dark:hover:bg-gray-600 transition-colors">
          {{ t('cars.cancel') }}
        </button>
        <button @click="save" v-haptic
          :disabled="loading || !canSave"
          class="btn-3d px-5 py-2 text-sm font-medium text-white bg-green-600 rounded-sm hover:bg-green-700 disabled:opacity-40 disabled:cursor-not-allowed transition-colors flex items-center gap-2">
          <span v-if="loading" class="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
          {{ t('logfields.save') }}
        </button>
      </div>
    </template>
  </BottomSheet>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { XMarkIcon, CheckIcon } from '@heroicons/vue/24/outline'
import BottomSheet from '../shared/BottomSheet.vue'
import ChargingCardTile from '../shared/ChargingCardTile.vue'
import api from '../../api/axios'
import { useI18n } from 'vue-i18n'
import { storeToRefs } from 'pinia'
import { useCountryStore } from '../../stores/country'
import { useCpoOptions } from '../../composables/useCpoOptions'
import { cardContainerStyle } from '../../composables/useChargingCardDesign'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import { EUR_EXCHANGE_RATES } from '../../config/exchangeRates'
import { costEurFromCard, localTotalToEur, buildAmendPayload, isAmendValid } from '../../utils/priceAmend'
import type { EvLogResponse } from './EditLogModal.vue'

interface UserProvider {
  id: string
  providerName: string
  label: string | null
  acPricePerKwh: number | null
  dcPricePerKwh: number | null
}

const props = defineProps<{ log: EvLogResponse }>()
const emit = defineEmits<{ close: []; saved: [log: EvLogResponse] }>()
const { t } = useI18n()
const { formatDecimal, formatCurrency } = useLocaleFormat()

const countryStore = useCountryStore()
const { country, unitSystem } = storeToRefs(countryStore)
const currencySymbol = computed(() => unitSystem.value.currencySymbol)
const currencySubunit = computed(() => unitSystem.value.currencySubunit)
const isEurZone = computed(() => countryStore.isEurZone)
const rate = computed(() => EUR_EXCHANGE_RATES[unitSystem.value.currency])

const cpo = useCpoOptions(country)

const userProviders = ref<UserProvider[]>([])
const selectedProviderId = ref<string | null>(props.log.chargingProviderId ?? null)
const manualPriceInput = ref('')
const isPublic = ref<boolean>(props.log.isPublicCharging ?? false)
const cpoName = ref<string | null>(props.log.cpoName ?? null)

const loading = ref(false)
const errorMsg = ref('')

const selectedProvider = computed(() => userProviders.value.find(p => p.id === selectedProviderId.value) ?? null)

/** Kosten aus der Karte (kWh-Preis x geladene Energie). */
const cardCostEur = computed(() =>
  selectedProvider.value ? costEurFromCard(selectedProvider.value, props.log.chargingType, props.log.kwhCharged) : null)

/** Manuell getippter Betrag in Landeswaehrung -> EUR. */
const manualCostEur = computed(() => {
  const v = manualPriceInput.value.trim()
  if (v === '') return null
  const n = Number(v)
  return localTotalToEur(Number.isNaN(n) ? null : n, rate.value)
})

/** Manuell getippter Preis hat Vorrang vor dem Kartenpreis. */
const effectiveCostEur = computed(() => manualCostEur.value ?? cardCostEur.value)
const canSave = computed(() => isAmendValid(effectiveCostEur.value, selectedProviderId.value))

function cardPriceLabel(p: UserProvider): string | null {
  if (p.acPricePerKwh == null) return null
  const value = isEurZone.value ? p.acPricePerKwh * 100 : p.acPricePerKwh
  return `${value.toFixed(1)} ${currencySubunit.value || currencySymbol.value}/kWh`
}

function toggleProvider(id: string) {
  selectedProviderId.value = selectedProviderId.value === id ? null : id
}

onMounted(async () => {
  try {
    const res = await api.get<UserProvider[]>('/users/me/charging-providers')
    userProviders.value = res.data
  } catch { /* Karten sind optional */ }
  await cpo.loadAll()
  cpo.keepSelected(cpoName.value)
})

// ── Standortsuche (Nominatim) ────────────────────────────────────────────────
const locationSearchQuery = ref('')
const suggestions = ref<any[]>([])
const showSuggestions = ref(false)
const newLocationName = ref('')
const latitude = ref<number | null>(null)
const longitude = ref<number | null>(null)

let searchTimer: any = null
watch(locationSearchQuery, (q) => {
  clearTimeout(searchTimer)
  if (!q || q.length < 3) { suggestions.value = []; return }
  searchTimer = setTimeout(async () => {
    try {
      const res = await fetch(`https://nominatim.openstreetmap.org/search?q=${encodeURIComponent(q)}&format=json&limit=5`)
      suggestions.value = await res.json()
      showSuggestions.value = suggestions.value.length > 0
    } catch { /* ignore */ }
  }, 300)
})

function selectLocation(s: any) {
  latitude.value = parseFloat(s.lat)
  longitude.value = parseFloat(s.lon)
  newLocationName.value = s.display_name
  locationSearchQuery.value = s.display_name
  showSuggestions.value = false
}

// ── Speichern ────────────────────────────────────────────────────────────────
const sheet = ref<InstanceType<typeof BottomSheet> | null>(null)
const savedLog = ref<EvLogResponse | null>(null)

function onClosed() {
  if (savedLog.value) emit('saved', savedLog.value)
  else emit('close')
}

async function save() {
  errorMsg.value = ''
  if (!canSave.value) return
  loading.value = true
  try {
    const cost = effectiveCostEur.value
    // isPublicCharging nur senden, wenn der User es geaendert hat - sonst wuerde ein erneutes
    // Setzen auf privat serverseitig den Geohash kuerzen. cpoName nur bei oeffentlicher Ladung.
    const publicChanged = isPublic.value !== (props.log.isPublicCharging ?? false)
    const usedManual = manualCostEur.value != null
    const payload = buildAmendPayload({
      costEur: cost,
      chargingProviderId: selectedProviderId.value,
      cpoName: isPublic.value ? cpoName.value : null,
      isPublicCharging: publicChanged ? isPublic.value : null,
      latitude: latitude.value,
      longitude: longitude.value,
      // Nur beim manuellen Betrag die Waehrung mitschreiben; der Kartenpreis ist bereits EUR.
      costCurrency: usedManual ? unitSystem.value.currency : null,
      costExchangeRate: usedManual ? rate.value : null,
    })
    const res = await api.patch(`/logs/${props.log.id}`, payload)
    savedLog.value = res.data
    sheet.value?.requestClose()
  } catch (e: any) {
    errorMsg.value = e?.response?.data?.message ?? 'Speichern fehlgeschlagen'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.btn-3d {
  box-shadow: 0 4px 0 0 rgba(0,0,0,0.2);
  transform: translateY(0);
  transition: transform 0.08s ease, box-shadow 0.08s ease;
}
.btn-3d:active {
  box-shadow: 0 1px 0 0 rgba(0,0,0,0.2);
  transform: translateY(3px);
}
</style>
