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
        <button @click="close" :aria-label="t('cars.cancel')" class="text-gray-400 dark:text-gray-500 hover:text-gray-600 dark:hover:text-gray-300 transition-colors">
          <XMarkIcon class="w-5 h-5" />
        </button>
      </div>

      <div class="flex-1 overflow-y-auto p-5 space-y-5">

        <!-- Kontext: welche Ladung ist das? -->
        <section data-testid="amend-context"
          class="rounded-sm bg-gray-50 dark:bg-gray-700/50 border border-gray-200 dark:border-gray-600 p-3 space-y-2">
          <!-- Kartenausschnitt: die Geohash-Zelle zeigt die Gegend und ihre Unschaerfe ehrlich -->
          <div v-if="mapGeohash" data-testid="amend-map"
            class="relative h-32 md:h-40 -mx-3 -mt-3 mb-3 overflow-hidden rounded-t-sm border-b border-gray-200 dark:border-gray-600">
            <ActivityLocationMap variant="panel" :label="t('priceamend.map_alt')" :start-geohash="mapGeohash" :end-geohash="null" />
          </div>
          <div class="flex items-center justify-between gap-2">
            <span class="text-sm font-semibold text-gray-900 dark:text-gray-100">{{ contextDateTime }}</span>
            <span class="flex items-center gap-2 shrink-0">
              <span v-if="source"
                :class="['inline-flex items-center gap-1 px-1.5 py-0.5 rounded-full text-[10px] font-medium', source.classes]">
                <component :is="source.icon" class="w-3 h-3" aria-hidden="true" />{{ source.label }}
              </span>
              <button type="button" data-testid="amend-toggle-location"
                @click="showLocationSearch = !showLocationSearch"
                class="text-xs font-medium text-indigo-600 dark:text-indigo-400 hover:underline">
                {{ hasStoredLocation || newLocationName ? t('priceamend.location_change') : t('priceamend.location_set') }}
              </button>
            </span>
          </div>
          <dl class="flex flex-wrap gap-x-4 gap-y-1 text-xs text-gray-600 dark:text-gray-300">
            <div><dt class="sr-only">kWh</dt><dd class="font-medium">{{ kwh != null ? formatDecimal(kwh, 1) + ' kWh' : t('priceamend.kwh_unknown') }}</dd></div>
            <div v-if="log.chargingType && log.chargingType !== 'UNKNOWN'"><dt class="sr-only">{{ t('priceamend.charging_type') }}</dt><dd>{{ log.chargingType }}</dd></div>
            <div v-if="log.chargeDurationMinutes"><dt class="sr-only">{{ t('priceamend.duration') }}</dt><dd>{{ formatDuration(log.chargeDurationMinutes) }}</dd></div>
            <div v-if="log.cpoName"><dt class="sr-only">CPO</dt><dd>{{ log.cpoName }}</dd></div>
          </dl>
          <!-- Ortszeile nur, wenn es etwas zu sagen gibt: neuer Name oder fehlender Standort -->
          <p v-if="newLocationName || !hasStoredLocation"
            :class="['flex items-center gap-1 min-w-0 text-xs', newLocationName ? 'text-gray-600 dark:text-gray-300' : 'text-amber-700 dark:text-amber-300']">
            <MapPinIcon class="w-3.5 h-3.5 shrink-0" aria-hidden="true" />
            <span class="truncate">{{ newLocationName || t('priceamend.location_missing') }}</span>
          </p>
          <div v-if="showLocationSearch" class="relative">
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
        </section>

        <!-- Preisvorschlag aus der Historie an diesem Ort -->
        <button v-if="suggestion && manualCostEur == null" type="button" data-testid="amend-suggestion"
          @click="applySuggestion"
          class="w-full flex items-center gap-3 rounded-sm border border-dashed border-green-300 bg-green-50/70 dark:border-green-700 dark:bg-green-950/30 p-3 text-left">
          <SparklesIcon class="w-5 h-5 shrink-0 text-green-600 dark:text-green-400" aria-hidden="true" />
          <span class="flex-1 min-w-0">
            <span class="block text-xs font-semibold text-gray-800 dark:text-gray-100">
              {{ t('priceamend.suggestion_title', { price: formatCostPerKwh(suggestion.costPerKwh), date: suggestionDate }) }}
            </span>
            <span v-if="suggestionCostEur != null" class="block text-[11px] text-gray-600 dark:text-gray-400">
              {{ t('priceamend.card_cost_hint', { cost: formatCurrency(suggestionCostEur) }) }}
            </span>
          </span>
          <span class="shrink-0 rounded-full bg-green-600 px-3 py-1.5 text-xs font-semibold text-white">{{ t('priceamend.suggestion_apply') }}</span>
        </button>

        <!-- Ladekarte -->
        <div v-if="userProviders.length > 0" class="space-y-2">
          <label class="block text-sm font-medium text-gray-700 dark:text-gray-300">{{ t('priceamend.card_section') }}</label>
          <div class="flex gap-2.5 overflow-x-auto pb-2 -mx-1 px-1">
            <button
              v-for="p in userProviders"
              :key="p.id"
              type="button"
              :aria-pressed="selectedProviderId === p.id"
              @click="toggleProvider(p.id)"
              :class="['btn-3d relative flex-shrink-0 w-28 h-[4.5rem] rounded-sm',
                       selectedProviderId === p.id ? 'active ring-2 ring-inset ring-indigo-500' : '']"
              :style="{ '--btn-shadow-color': cardContainerStyle(p.id)['--btn-shadow-color'] }">
              <ChargingCardTile class="w-full h-full" :id="p.id" :title="p.label || p.providerName" :subtitle="cardPriceLabel(p)" />
              <CheckCircleIcon v-if="selectedProviderId === p.id"
                class="absolute top-1 right-1 h-5 w-5 rounded-full bg-white text-indigo-600 dark:bg-gray-800" aria-hidden="true" />
              <span v-else-if="providerHasNoPrice(p)"
                class="absolute top-1 right-1 h-2.5 w-2.5 rounded-full bg-amber-400 ring-2 ring-white dark:ring-gray-800"
                :title="t('logfields.card_no_price_dot')" aria-hidden="true" />
            </button>
          </div>
          <p v-if="cardCostEur != null && manualCostEur == null" class="text-xs text-gray-500 dark:text-gray-400">
            {{ t('priceamend.card_cost_hint', { cost: formatCurrency(cardCostEur) }) }}
          </p>
        </div>

        <!-- Preis manuell -->
        <div class="space-y-1">
          <label for="amend-price" class="block text-sm font-medium text-gray-700 dark:text-gray-300">
            {{ t(priceMode === 'total' ? 'priceamend.label_total' : 'priceamend.label_per_kwh', { or: userProviders.length > 0 ? t('priceamend.label_or_prefix') : '' }) }}
          </label>
          <div class="relative">
            <input id="amend-price"
              v-model="manualPriceInput"
              :disabled="priceLocked"
              type="number" inputmode="decimal" min="0" :step="priceMode === 'total' ? '0.01' : '0.001'"
              :placeholder="t(priceMode === 'total' ? 'logfields.cost_eur_placeholder' : 'logfields.cost_per_kwh_placeholder')"
              class="w-full border border-gray-200 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-sm px-3 py-2 pr-28 text-sm focus:outline-none focus:ring-2 focus:ring-green-500 disabled:opacity-50 disabled:cursor-not-allowed" />
            <div class="absolute right-1.5 top-1/2 -translate-y-1/2 flex rounded-full border border-gray-300 dark:border-gray-500 bg-gray-200 dark:bg-gray-600 p-0.5 text-xs"
              role="group" :aria-label="t('logfields.cost_mode_toggle_aria', { mode: priceMode === 'total' ? currencySymbol : currencySymbol + '/kWh' })">
              <button type="button" data-testid="amend-mode-total" @click="togglePriceMode('total')" :aria-pressed="priceMode === 'total'"
                :class="['px-2.5 py-0.5 rounded-full font-medium transition-all duration-200 min-w-[2rem] text-center', priceMode === 'total' ? 'bg-white dark:bg-gray-500 text-indigo-700 dark:text-white shadow-sm' : 'text-gray-500 dark:text-gray-400']">
                {{ currencySymbol }}
              </button>
              <button type="button" data-testid="amend-mode-per-kwh" @click="togglePriceMode('per_kwh')" :aria-pressed="priceMode === 'per_kwh'"
                :class="['px-1.5 py-0.5 rounded-full font-medium transition-all duration-200', priceMode === 'per_kwh' ? 'bg-white dark:bg-gray-500 text-indigo-700 dark:text-white shadow-sm' : 'text-gray-500 dark:text-gray-400']">
                {{ currencySymbol + '/kWh' }}
              </button>
            </div>
          </div>

          <!-- kWh-Preis als Bruecke zur Karte -->
          <div v-if="manualPerKwhEur != null" data-testid="amend-per-kwh" class="pt-1 space-y-2">
            <p class="text-xs text-gray-600 dark:text-gray-300">
              {{ priceMode === 'total'
                ? t('priceamend.per_kwh_line', { price: formatCostPerKwh(manualPerKwhEur) })
                : t('priceamend.card_cost_hint', { cost: formatCurrency(manualCostEur ?? 0) }) }}
            </p>
            <button v-if="nudge && !inlineCard.isOpen.value" type="button" data-testid="amend-nudge"
              @click="openNudge"
              class="flex w-full items-center gap-3 rounded-sm border border-dashed border-indigo-300 bg-indigo-50/60 p-3 text-left dark:border-indigo-700 dark:bg-indigo-950/30">
              <CreditCardIcon class="h-5 w-5 shrink-0 text-indigo-600 dark:text-indigo-400" aria-hidden="true" />
              <span class="flex-1 min-w-0">
                <span class="block text-xs font-semibold text-gray-800 dark:text-gray-100">
                  {{ nudge === 'new' ? t('priceamend.nudge_new_card') : t('priceamend.nudge_card_price', { card: selectedProvider?.label || selectedProvider?.providerName }) }}
                </span>
                <span class="block text-[11px] leading-snug text-gray-600 dark:text-gray-400">{{ t('priceamend.nudge_hint') }}</span>
              </span>
            </button>
          </div>
        </div>

        <!-- Inline: Karte anlegen oder bepreisen -->
        <div v-if="inlineCard.isOpen.value" data-testid="amend-inline-card"
          class="rounded-sm border border-dashed border-indigo-300 bg-indigo-50/60 p-3 space-y-2.5 dark:border-indigo-700 dark:bg-indigo-950/30">
          <label class="block text-xs font-medium text-gray-600 dark:text-gray-300" for="amend-card-provider">
            {{ t(inlineCard.isEditing.value ? 'logfields.card_edit_title' : 'logfields.card_prompt_title') }}
          </label>
          <p v-if="inlineCard.isEditing.value" class="rounded-md bg-white px-3 py-2 text-sm font-medium text-gray-800 dark:bg-gray-700 dark:text-gray-100">
            {{ inlineCard.resolvedName.value }}
          </p>
          <select v-else id="amend-card-provider" v-model="inlineCard.draft.value.providerName" :class="INPUT_CLASS">
            <option value="">{{ t('logfields.card_select_placeholder') }}</option>
            <option v-for="emp in KNOWN_EMPS.filter(e => e !== CUSTOM_PROVIDER)" :key="emp" :value="emp">{{ emp }}</option>
            <option :value="CUSTOM_PROVIDER">{{ t('logfields.card_other_provider') }}</option>
          </select>
          <input v-if="!inlineCard.isEditing.value && inlineCard.isCustom.value"
            v-model="inlineCard.draft.value.customProviderName" type="text" maxlength="100"
            :placeholder="t('logfields.card_custom_name_placeholder')" :class="INPUT_CLASS" />
          <div class="grid grid-cols-2 gap-2">
            <label class="block">
              <span class="mb-1 block text-[11px] text-gray-500 dark:text-gray-400">{{ t('logfields.card_ac_price', { unit: currencySubunit || currencySymbol }) }}</span>
              <input v-model="inlineCard.draft.value.acPrice" type="number" inputmode="decimal" step="0.1" min="0" :class="INPUT_CLASS" />
            </label>
            <label class="block">
              <span class="mb-1 block text-[11px] text-gray-500 dark:text-gray-400">{{ t('logfields.card_dc_price', { unit: currencySubunit || currencySymbol }) }}</span>
              <input v-model="inlineCard.draft.value.dcPrice" type="number" inputmode="decimal" step="0.1" min="0" :class="INPUT_CLASS" />
            </label>
          </div>
          <p v-if="inlineCard.failed.value" class="text-xs text-red-500">{{ t('logfields.card_save_failed') }}</p>
          <div class="flex gap-2">
            <button type="button" @click="inlineCard.cancel()"
              class="flex-1 rounded-md border border-gray-300 px-3 py-2 text-xs font-medium text-gray-600 dark:border-gray-600 dark:text-gray-300">
              {{ t('common.cancel') }}
            </button>
            <button type="button" data-testid="amend-card-save" :disabled="!inlineCard.canSave.value" @click="saveInlineCard"
              class="flex-1 rounded-md bg-indigo-600 px-3 py-2 text-xs font-semibold text-white disabled:cursor-not-allowed disabled:opacity-50">
              {{ inlineCard.saving.value ? t('common.saving') : t('logfields.card_save') }}
            </button>
          </div>
        </div>

        <!-- Batch: weitere preislose Ladungen am selben Ort -->
        <label v-if="pricelessCountAtLocation > 0" data-testid="amend-batch"
          :class="['flex items-start gap-2.5 text-left', batchAllowed ? 'cursor-pointer' : 'opacity-70']">
          <input type="checkbox" v-model="applyToLocation" :disabled="!batchAllowed"
            class="mt-0.5 h-4 w-4 shrink-0 rounded border-gray-300 text-indigo-600 focus:ring-2 focus:ring-indigo-500 dark:border-gray-600 dark:bg-gray-700" />
          <span class="text-xs leading-snug text-gray-600 dark:text-gray-300">
            {{ t('priceamend.batch_label', pricelessCountAtLocation) }}
            <span v-if="!batchAllowed" class="block text-[11px] text-gray-500 dark:text-gray-400">{{ t('priceamend.batch_needs_card') }}</span>
          </span>
        </label>

        <!-- Oeffentliche Ladung + CPO -->
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
import { ref, computed, watch, onMounted, defineAsyncComponent } from 'vue'
import { XMarkIcon, CheckCircleIcon, CreditCardIcon, MapPinIcon, SparklesIcon } from '@heroicons/vue/24/outline'
import BottomSheet from '../shared/BottomSheet.vue'
import ChargingCardTile from '../shared/ChargingCardTile.vue'
import geohashLib from 'ngeohash'
import api from '../../api/axios'
import { useI18n } from 'vue-i18n'
import { storeToRefs } from 'pinia'
import { useCountryStore } from '../../stores/country'
import { useCpoOptions } from '../../composables/useCpoOptions'
import { cardContainerStyle } from '../../composables/useChargingCardDesign'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import { useInlineChargingCard, CUSTOM_PROVIDER } from '../../composables/useInlineChargingCard'
import { KNOWN_EMPS, type ChargingProvider } from '../../composables/useChargingProviders'
import { EUR_EXCHANGE_RATES } from '../../config/exchangeRates'
import { sourceInfo } from '../../utils/logSource'
import { providerHasNoPrice, providerPriceForType } from '../../utils/chargingProviderPricing'
import { tariffLocationParams } from '../../utils/tariffLocation'
import { applyTariffToLocationIfRequested } from '../../utils/applyTariffToLocation'
import {
  costEurFromCard, costEurFromPricePerKwh, buildAmendPayload, isAmendValid,
  amendKwh, pricePerKwhEur, canApplyToLocation, manualCostEur as manualCostEurFrom, cardLocksManualPrice, type PriceInputMode,
} from '../../utils/priceAmend'
import type { EvLogResponse } from './EditLogModal.vue'

// Async wie im Feed: Leaflet bleibt aus dem Einstiegs-Bundle.
const ActivityLocationMap = defineAsyncComponent(() => import('./ActivityLocationMap.vue'))

const props = defineProps<{ log: EvLogResponse }>()
const emit = defineEmits<{ close: []; saved: [log: EvLogResponse] }>()

const { t, locale } = useI18n()
const { formatDecimal, formatCurrency, formatCostPerKwh } = useLocaleFormat()
const countryStore = useCountryStore()
const { unitSystem, country } = storeToRefs(countryStore)
const currencySymbol = computed(() => unitSystem.value.currencySymbol)
const currencySubunit = computed(() => unitSystem.value.currencySubunit)
const isEurZone = computed(() => countryStore.isEurZone)
const rate = computed(() => EUR_EXCHANGE_RATES[unitSystem.value.currency])

const cpo = useCpoOptions(country)

// ── Kontext ──────────────────────────────────────────────────────────────────
const INPUT_CLASS = 'w-full rounded-md border border-gray-300 bg-white px-3 py-2 text-sm focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500 dark:border-gray-600 dark:bg-gray-700 dark:text-white'
const DATE_LOCALES: Record<string, string> = { de: 'de-DE', en: 'en-GB', nb: 'nb-NO', sv: 'sv-SE' }
const kwh = computed(() => amendKwh(props.log))
/** Kartenpreise kennen nur AC/DC - UNKNOWN faellt wie ueberall auf AC zurueck. */
const priceType = computed<'AC' | 'DC'>(() => props.log.chargingType === 'DC' ? 'DC' : 'AC')
const source = computed(() => sourceInfo(props.log.dataSource))
const contextDateTime = computed(() =>
  new Date(props.log.loggedAt).toLocaleString(DATE_LOCALES[locale.value] ?? 'de-DE',
    { weekday: 'short', day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' }))
const hasStoredLocation = computed(() => !!props.log.geohash)
/** Neu gesetzter Ort schlaegt den gespeicherten; Praezision wie beim Speichern (oeffentlich 7, privat 6). */
const mapGeohash = computed(() => {
  if (latitude.value != null && longitude.value != null) {
    return geohashLib.encode(latitude.value, longitude.value, isPublic.value ? 7 : 6)
  }
  return props.log.geohash ?? null
})
function formatDuration(min: number): string {
  const h = Math.floor(min / 60), m = min % 60
  return h > 0 ? `${h} h ${m} min` : `${m} min`
}

// ── Karten + Preis ───────────────────────────────────────────────────────────
const userProviders = ref<ChargingProvider[]>([])
const selectedProviderId = ref<string | null>(props.log.chargingProviderId ?? null)
const manualPriceInput = ref<string | number>('')
const isPublic = ref<boolean>(props.log.isPublicCharging ?? false)
const cpoName = ref<string | null>(props.log.cpoName ?? null)
const loading = ref(false)
const errorMsg = ref('')

const selectedProvider = computed(() => userProviders.value.find(p => p.id === selectedProviderId.value) ?? null)
const cardCostEur = computed(() =>
  selectedProvider.value ? costEurFromCard(selectedProvider.value, props.log.chargingType, kwh.value) : null)
/** Default kWh-Preis: das ist die Zahl, die auf dem Display der Saeule steht und als Tarif merkbar ist. */
const priceMode = ref<PriceInputMode>('per_kwh')
const manualCostEur = computed(() => manualCostEurFrom(priceMode.value, manualPriceInput.value, kwh.value, rate.value))

/** Umschalten rechnet den getippten Wert in die andere Basis um, statt ihn zu verwerfen. */
function togglePriceMode(mode: PriceInputMode) {
  if (priceMode.value === mode) return
  const raw = String(manualPriceInput.value ?? '').trim()
  const n = Number(raw)
  if (raw !== '' && !Number.isNaN(n) && kwh.value) {
    manualPriceInput.value = mode === 'per_kwh'
      ? String(Math.round((n / kwh.value) * 1000) / 1000)
      : String(Math.round(n * kwh.value * 100) / 100)
  }
  priceMode.value = mode
}
/** Manuell getippter Preis hat Vorrang vor dem Kartenpreis. */
const effectiveCostEur = computed(() => manualCostEur.value ?? cardCostEur.value)
const canSave = computed(() => isAmendValid(effectiveCostEur.value, selectedProviderId.value))
const manualPerKwhEur = computed(() => pricePerKwhEur(manualCostEur.value, kwh.value))

function cardPriceLabel(p: ChargingProvider): string | null {
  const price = providerPriceForType(p, priceType.value)
  if (price == null) return null
  const value = isEurZone.value ? price * 100 : price
  return `${value.toFixed(1)} ${currencySubunit.value || currencySymbol.value}/kWh`
}
/** Karte ODER Preis: eine bepreiste Karte uebernimmt, das Preisfeld wird geleert und gesperrt. */
const priceLocked = computed(() => cardLocksManualPrice(selectedProvider.value, priceType.value))
function toggleProvider(id: string) {
  selectedProviderId.value = selectedProviderId.value === id ? null : id
  if (priceLocked.value) manualPriceInput.value = ''
}
// Tippt der User einen Preis, waehrend eine bepreiste Karte gewaehlt ist (z.B. Vorschlag hat sie
// gesetzt), verliert die Karte - der getippte Preis ist die Aussage des Users.
watch(manualPriceInput, v => {
  if (String(v ?? '').trim() !== '' && priceLocked.value) selectedProviderId.value = null
})

// ── Nudge: aus dem getippten Betrag eine Karte machen ────────────────────────
const inlineCard = useInlineChargingCard(
  (typed: number) => isEurZone.value ? typed / 100 : typed,
  (eur: number) => isEurZone.value ? Math.round(eur * 1000) / 10 : eur)

/** 'new' = keine Karte gewaehlt -> anlegen; 'price' = gewaehlte Karte hat fuer diesen Typ keinen Preis. */
const nudge = computed<'new' | 'price' | null>(() => {
  if (manualPerKwhEur.value == null) return null
  const p = selectedProvider.value
  if (!p) return 'new'
  return providerPriceForType(p, priceType.value) == null ? 'price' : null
})
function openNudge() {
  if (manualPerKwhEur.value == null) return
  if (nudge.value === 'price' && selectedProvider.value) {
    inlineCard.openEdit(selectedProvider.value)
    const field = props.log.chargingType === 'DC' ? 'dcPrice' : 'acPrice'
    inlineCard.draft.value[field] = isEurZone.value ? Math.round(manualPerKwhEur.value * 1000) / 10 : manualPerKwhEur.value
  } else {
    inlineCard.openWithPrice(props.log.chargingType, manualPerKwhEur.value)
  }
}
async function saveInlineCard() {
  const wasEditing = inlineCard.isEditing.value
  const saved = await inlineCard.save()
  if (!saved) return
  const idx = userProviders.value.findIndex(p => p.id === saved.id)
  if (wasEditing && idx !== -1) userProviders.value[idx] = saved
  else userProviders.value = [...userProviders.value, saved]
  selectedProviderId.value = saved.id
}

// ── Vorschlag + Batch am Ort ─────────────────────────────────────────────────
interface Suggestion { costPerKwh: number; chargingProviderId?: string; anchorLoggedAt?: string }
const suggestion = ref<Suggestion | null>(null)
const suggestionCostEur = computed(() => costEurFromPricePerKwh(suggestion.value?.costPerKwh ?? null, kwh.value))
const suggestionDate = computed(() => suggestion.value?.anchorLoggedAt
  ? new Date(suggestion.value.anchorLoggedAt).toLocaleDateString(DATE_LOCALES[locale.value] ?? 'de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' })
  : '')
function applySuggestion() {
  if (suggestion.value == null) return
  // Der Vorschlag ist ein kWh-Preis - genau so uebernehmen, nicht als gerundeter Gesamtbetrag.
  const eur = suggestion.value.costPerKwh
  const card = userProviders.value.find(p => p.id === suggestion.value?.chargingProviderId)
  if (card && providerPriceForType(card, priceType.value) === eur) {
    // Der Anker wurde mit dieser Karte zu ihrem Tarif bezahlt - dann ist die Karte die Wahl (Batch moeglich).
    manualPriceInput.value = ''
    selectedProviderId.value = card.id
    return
  }
  selectedProviderId.value = null
  priceMode.value = 'per_kwh'
  manualPriceInput.value = String(isEurZone.value ? eur : Math.round(eur * rate.value * 1000) / 1000)
}

const pricelessCountAtLocation = ref(0)
const applyToLocation = ref(false)
const batchAllowed = computed(() => canApplyToLocation(selectedProviderId.value, pricelessCountAtLocation.value))
watch(batchAllowed, ok => { if (!ok) applyToLocation.value = false })

const locationSource = computed(() => ({
  latitude: latitude.value, longitude: longitude.value, isPublicCharging: isPublic.value, geohash: props.log.geohash,
}))
async function fetchLocationContext() {
  const location = tariffLocationParams(locationSource.value)
  suggestion.value = null
  pricelessCountAtLocation.value = 0
  if (!location) return
  const [sugg, count] = await Promise.allSettled([
    api.get('/logs/price-suggestion', { params: { ...location, isPublic: isPublic.value, chargingType: props.log.chargingType ?? undefined } }),
    api.get('/logs/priceless-count', { params: { ...location, excludeLogId: props.log.id } }),
  ])
  if (sugg.status === 'fulfilled' && sugg.value.status === 200 && sugg.value.data?.costPerKwh != null) suggestion.value = sugg.value.data
  if (count.status === 'fulfilled') pricelessCountAtLocation.value = count.value.data?.count ?? 0
}

onMounted(async () => {
  try {
    const res = await api.get<ChargingProvider[]>('/users/me/charging-providers')
    userProviders.value = res.data
  } catch { /* Karten sind optional */ }
  await cpo.loadAll()
  cpo.keepSelected(cpoName.value)
  fetchLocationContext()
})

// ── Standortsuche (Nominatim) ────────────────────────────────────────────────
const showLocationSearch = ref(false)
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
  showLocationSearch.value = false
  fetchLocationContext()
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
    // isPublicCharging nur senden, wenn der User es geaendert hat - sonst wuerde ein erneutes
    // Setzen auf privat serverseitig den Geohash kuerzen. cpoName nur bei oeffentlicher Ladung.
    const publicChanged = isPublic.value !== (props.log.isPublicCharging ?? false)
    const usedManual = manualCostEur.value != null
    const payload = buildAmendPayload({
      costEur: effectiveCostEur.value,
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
    // Nach dem Log selbst: die uebrigen preislosen Ladungen am Ort mit der Karte bepreisen.
    await applyTariffToLocationIfRequested({
      ...locationSource.value, chargingProviderId: selectedProviderId.value, applyTariffToLocation: applyToLocation.value,
    })
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
