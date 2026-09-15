<script setup lang="ts">
import { CHIP_ROW, chipClass } from './chipClass'
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { LogFormData } from '../log-form/logFormData'
import type { ChargingProvider } from '../../composables/useChargingProviders'
import type { useCostInput } from '../../composables/useCostInput'
import { providerPriceForType } from '../../utils/chargingProviderPricing'
import { useInlineChargingCard, CUSTOM_PROVIDER } from '../../composables/useInlineChargingCard'
import { KNOWN_EMPS, HOME_TARIFF_NAME } from '../../composables/useChargingProviders'
import { tariffLocationParams } from '../../utils/tariffLocation'
import { EUR_ZONE_COUNTRIES } from '../../config/unitSystems'
import { useCountryStore } from '../../stores/country'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import api from '../../api/axios'
import BigInput from './BigInput.vue'
import SegmentToggle from './SegmentToggle.vue'

const props = defineProps<{ cost: ReturnType<typeof useCostInput> }>()
const form = defineModel<LogFormData>({ required: true })
const providers = defineModel<ChargingProvider[]>('providers', { required: true })
const { t } = useI18n()
const countryStore = useCountryStore()
const { formatNumber, formatDecimal } = useLocaleFormat()

const symbol = computed(() => countryStore.unitSystem.currencySymbol)
const { costMode, costLocalTotal, costLocalPerKwh, calculatedLocalPerKwh, calculatedLocalTotal, setPerKwhEur } = props.cost

interface Suggestion { key: string; label: string; eurPerKwh: number; providerId: string | null }
const community = ref<Suggestion | null>(null)

const cardSuggestions = computed<Suggestion[]>(() => providers.value.flatMap(p => {
  const price = providerPriceForType(p, form.value.chargingType)
  return price == null ? [] : [{ key: p.id, label: p.label || p.providerName, eurPerKwh: price, providerId: p.id }]
}))
const suggestions = computed(() => [...(community.value ? [community.value] : []), ...cardSuggestions.value])

const selectedKey = ref<string | null>(null)
const pick = (s: Suggestion) => {
  selectedKey.value = s.key
  form.value.chargingProviderId = s.providerId
  setPerKwhEur(s.eurPerKwh)
}
const pickFree = () => { selectedKey.value = 'free'; form.value.chargingProviderId = null; costMode.value = 'total'; costLocalPerKwh.value = null; costLocalTotal.value = 0 }
const priceLabel = (eur: number) => `${formatNumber(Math.round(props.cost.eurToLocal(eur) * 100) / 100)} ${symbol.value}/kWh`

// ── Ladekarte inline anlegen / Tarif nachtragen ───────────────────────────────
// Ein Link in die Einstellungen wuerde den User aus dem halb ausgefuellten Log werfen.
const isEurCountry = computed(() => EUR_ZONE_COUNTRIES.includes(countryStore.country))
const subunit = computed(() => countryStore.unitSystem.currencySubunit || symbol.value)
const inlineCard = useInlineChargingCard(
  (typed: number) => isEurCountry.value ? typed / 100 : typed,
  (eur: number) => isEurCountry.value ? Math.round(eur * 1000) / 10 : eur)
const cardEmps = KNOWN_EMPS.filter(e => e !== CUSTOM_PROVIDER && e !== HOME_TARIFF_NAME)
const selectedProvider = computed(() => providers.value.find(p => p.id === form.value.chargingProviderId) ?? null)
const selectedNeedsPrice = computed(() =>
  selectedProvider.value != null && providerPriceForType(selectedProvider.value, form.value.chargingType) == null)
const typedEurPerKwh = computed<number | null>(() => {
  const local = costMode.value === 'per_kwh' ? costLocalPerKwh.value : calculatedLocalPerKwh.value
  return local == null ? null : local / (props.cost.eurToLocal(1))
})
const openNewCard = () => typedEurPerKwh.value != null
  ? inlineCard.openWithPrice(form.value.chargingType, typedEurPerKwh.value)
  : inlineCard.open()
const openPriceForSelected = () => {
  if (!selectedProvider.value) return
  if (typedEurPerKwh.value != null) inlineCard.openEditWithPrice(selectedProvider.value, form.value.chargingType, typedEurPerKwh.value)
  else inlineCard.openEdit(selectedProvider.value)
}
const saveCard = async () => {
  const wasEditing = inlineCard.isEditing.value
  const saved = await inlineCard.save()
  if (!saved) return
  const idx = providers.value.findIndex(p => p.id === saved.id)
  providers.value = idx === -1 ? [...providers.value, saved] : providers.value.map(p => p.id === saved.id ? saved : p)
  form.value.chargingProviderId = saved.id
  selectedKey.value = saved.id
  const price = providerPriceForType(saved, form.value.chargingType)
  if (price != null && (wasEditing || typedEurPerKwh.value == null)) setPerKwhEur(price)
}

// ── Tarif auf alle preislosen Ladungen an diesem Ort ──────────────────────────
const pricelessCount = ref(0)
const fetchPricelessCount = async () => {
  const location = tariffLocationParams(form.value)
  if (!location || !form.value.chargingProviderId) { pricelessCount.value = 0; return }
  try {
    const res = await api.get('/logs/priceless-count', { params: location })
    pricelessCount.value = res.data.count ?? 0
  } catch { pricelessCount.value = 0 }
}
watch(() => form.value.chargingProviderId, (id) => {
  if (!id) form.value.applyTariffToLocation = false
  fetchPricelessCount()
}, { immediate: true })

onMounted(async () => {
  if (form.value.latitude == null || form.value.longitude == null) return
  try {
    const res = await api.get('/logs/price-suggestion', {
      params: { lat: form.value.latitude, lon: form.value.longitude, isPublic: form.value.isPublicCharging, chargingType: form.value.chargingType },
    })
    if (res.data?.costPerKwh != null) {
      community.value = { key: 'community', label: t('logwizard.price_community'), eurPerKwh: Number(res.data.costPerKwh), providerId: res.data.chargingProviderId ?? null }
    }
  } catch { /* kein Vorschlag - kein Problem */ }
})
</script>

<template>
  <!-- Lese-Zone oben, Bedien-Zone unten in Daumenreichweite (siehe StepEnergy) -->
  <div class="flex-1 flex flex-col gap-4">
    <p class="text-sm text-gray-500 dark:text-gray-400">{{ t('logwizard.price_hint') }}</p>

    <div class="mt-auto space-y-4">
    <div>
      <div :class="[CHIP_ROW, 'items-center']">
        <span class="mr-auto text-[11px] uppercase tracking-wide text-gray-400 dark:text-gray-500">{{ t('logwizard.price_suggestions') }}</span>
        <button v-for="s in suggestions" :key="s.key" type="button" :aria-pressed="selectedKey === s.key" @click="pick(s)"
          :class="chipClass(selectedKey === s.key)">
          {{ priceLabel(s.eurPerKwh) }} · {{ s.label }}
        </button>
        <button type="button" :aria-pressed="selectedKey === 'free'" @click="pickFree"
          :class="chipClass(selectedKey === 'free')">
          {{ t('logwizard.price_free') }}
        </button>
      </div>
    </div>

    <!-- Ladekarte: anlegen oder der gewaehlten Karte den fehlenden Tarif geben -->
    <div v-if="form.isPublicCharging" class="space-y-2">
      <div v-if="!inlineCard.isOpen.value" :class="CHIP_ROW">
        <button v-if="selectedNeedsPrice" type="button" data-testid="charging-card-price-missing" @click="openPriceForSelected"
          :class="chipClass(false, 'warn')">
          {{ t('logwizard.card_price_missing', { card: selectedProvider!.label || selectedProvider!.providerName }) }}
        </button>
        <button type="button" data-testid="charging-card-prompt-open" @click="openNewCard"
          :class="chipClass(false, 'dashed')">
          + {{ t('logwizard.card_add') }}
        </button>
      </div>

      <div v-else data-testid="charging-card-prompt" class="rounded-sm border border-dashed border-indigo-300 dark:border-indigo-700 bg-indigo-50/60 dark:bg-indigo-950/30 p-3 space-y-2.5">
        <label class="block text-xs font-medium text-gray-600 dark:text-gray-300" for="inline-card-provider">
          {{ t(inlineCard.isEditing.value ? 'logfields.card_edit_title' : 'logfields.card_prompt_title') }}
        </label>
        <p v-if="inlineCard.isEditing.value" class="rounded-sm bg-white dark:bg-gray-700 px-3 py-2 text-sm font-medium text-gray-800 dark:text-gray-100">{{ inlineCard.resolvedName.value }}</p>
        <select v-else id="inline-card-provider" v-model="inlineCard.draft.value.providerName"
          class="w-full rounded-sm border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-700 dark:text-white px-3 py-2 text-sm">
          <option value="">{{ t('logfields.card_select_placeholder') }}</option>
          <option v-for="emp in cardEmps" :key="emp" :value="emp">{{ emp }}</option>
          <option :value="CUSTOM_PROVIDER">{{ t('logfields.card_other_provider') }}</option>
        </select>
        <input v-if="!inlineCard.isEditing.value && inlineCard.isCustom.value" v-model="inlineCard.draft.value.customProviderName" type="text" maxlength="100"
          :placeholder="t('logfields.card_custom_name_placeholder')"
          class="w-full rounded-sm border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-700 dark:text-white px-3 py-2 text-sm" />
        <div class="grid grid-cols-2 gap-2">
          <label class="block">
            <span class="mb-1 block text-[11px] text-gray-500 dark:text-gray-400">{{ t('logfields.card_ac_price', { unit: subunit }) }}</span>
            <input v-model="inlineCard.draft.value.acPrice" type="number" inputmode="decimal" step="0.1" min="0"
              class="w-full rounded-sm border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-700 dark:text-white px-3 py-2 text-sm" />
          </label>
          <label class="block">
            <span class="mb-1 block text-[11px] text-gray-500 dark:text-gray-400">{{ t('logfields.card_dc_price', { unit: subunit }) }}</span>
            <input v-model="inlineCard.draft.value.dcPrice" type="number" inputmode="decimal" step="0.1" min="0"
              class="w-full rounded-sm border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-700 dark:text-white px-3 py-2 text-sm" />
          </label>
        </div>
        <p v-if="inlineCard.failed.value" class="text-xs text-red-500">{{ t('logfields.card_save_failed') }}</p>
        <div class="flex gap-2">
          <button type="button" @click="inlineCard.cancel()" class="btn-3d flex-1 rounded-sm border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-700 px-3 py-2 text-xs font-medium text-gray-600 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-600">{{ t('common.cancel') }}</button>
          <button type="button" data-testid="charging-card-save" :disabled="!inlineCard.canSave.value" @click="saveCard"
            class="btn-3d flex-1 rounded-sm bg-indigo-600 px-3 py-2 text-xs font-semibold text-white hover:bg-indigo-700 disabled:opacity-50 disabled:hover:bg-indigo-600">
            {{ inlineCard.saving.value ? t('common.saving') : t('logfields.card_save') }}
          </button>
        </div>
      </div>

      <!-- Tarif auch auf die preislosen Ladungen an diesem Ort -->
      <label v-if="pricelessCount > 0 && form.chargingProviderId" class="flex items-start gap-2 text-sm text-gray-700 dark:text-gray-200 pt-1">
        <input v-model="form.applyTariffToLocation" type="checkbox" class="mt-0.5 rounded-sm border-gray-300 text-indigo-600" />
        <span>{{ t('logfields.apply_tariff_to_location', pricelessCount) }}</span>
      </label>
    </div>

    <SegmentToggle v-model="costMode"
      :options="[{ value: 'total', label: t('logwizard.cost_total') }, { value: 'per_kwh', label: t('logwizard.cost_per_kwh') }]" />
    <BigInput v-if="costMode === 'total'" id="wizard-cost" v-model="costLocalTotal" :unit="symbol" :label="t('logfields.cost_eur')" :placeholder="t('logfields.cost_eur_placeholder')" step="0.01" :min="0" autofocus
      :hint="calculatedLocalPerKwh != null ? `= ${formatDecimal(calculatedLocalPerKwh, 2)} ${symbol}/kWh` : null" />
    <BigInput v-else id="wizard-cost" v-model="costLocalPerKwh" :unit="`${symbol}/kWh`" :label="t('logfields.cost_per_kwh')" :placeholder="t('logfields.cost_per_kwh_placeholder')" step="0.001" :min="0" autofocus
      :hint="calculatedLocalTotal != null ? `= ${formatDecimal(calculatedLocalTotal, 2)} ${symbol}` : null" />
    </div>
  </div>
</template>
