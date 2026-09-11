<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { LogFormData } from '../log-form/LogFormFields.vue'
import type { ChargingProvider } from '../../composables/useChargingProviders'
import type { useCostInput } from '../../composables/useCostInput'
import { providerPriceForType } from '../../utils/chargingProviderPricing'
import { useCountryStore } from '../../stores/country'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import api from '../../api/axios'
import BigInput from './BigInput.vue'
import SegmentToggle from './SegmentToggle.vue'

const props = defineProps<{ cost: ReturnType<typeof useCostInput>; providers: ChargingProvider[] }>()
const form = defineModel<LogFormData>({ required: true })
const { t } = useI18n()
const countryStore = useCountryStore()
const { formatNumber } = useLocaleFormat()

const symbol = computed(() => countryStore.unitSystem.currencySymbol)
const { costMode, costLocalTotal, costLocalPerKwh, calculatedLocalPerKwh, calculatedLocalTotal, setPerKwhEur } = props.cost

interface Suggestion { key: string; label: string; eurPerKwh: number; providerId: string | null }
const community = ref<Suggestion | null>(null)

const cardSuggestions = computed<Suggestion[]>(() => props.providers.flatMap(p => {
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
  <div class="space-y-4">
    <BigInput v-if="costMode === 'total'" id="wizard-cost" v-model="costLocalTotal" :unit="symbol" :label="t('logfields.cost_eur')" :placeholder="t('logfields.cost_eur_placeholder')" step="0.01" :min="0" autofocus />
    <BigInput v-else id="wizard-cost" v-model="costLocalPerKwh" :unit="`${symbol}/kWh`" :label="t('logfields.cost_per_kwh')" :placeholder="t('logfields.cost_per_kwh_placeholder')" step="0.001" :min="0" autofocus />
    <SegmentToggle v-model="costMode"
      :options="[{ value: 'total', label: t('logwizard.cost_total') }, { value: 'per_kwh', label: t('logwizard.cost_per_kwh') }]" />
    <p class="text-sm text-gray-500 dark:text-gray-400 tabular-nums min-h-5">
      <template v-if="costMode === 'total' && calculatedLocalPerKwh != null">= {{ formatNumber(calculatedLocalPerKwh) }} {{ symbol }}/kWh</template>
      <template v-else-if="costMode === 'per_kwh' && calculatedLocalTotal != null">= {{ formatNumber(calculatedLocalTotal) }} {{ symbol }}</template>
    </p>

    <div class="pt-2">
      <p class="text-[11px] uppercase tracking-wide text-gray-400 dark:text-gray-500 mb-2">{{ t('logwizard.price_suggestions') }}</p>
      <div class="flex flex-wrap gap-2">
        <button v-for="s in suggestions" :key="s.key" type="button" :aria-pressed="selectedKey === s.key" @click="pick(s)"
          :class="['px-3 py-1.5 rounded-full text-sm border', selectedKey === s.key ? 'bg-indigo-600 text-white border-indigo-600' : 'border-gray-300 dark:border-gray-600 text-gray-700 dark:text-gray-200']">
          {{ priceLabel(s.eurPerKwh) }} · {{ s.label }}
        </button>
        <button type="button" :aria-pressed="selectedKey === 'free'" @click="pickFree"
          :class="['px-3 py-1.5 rounded-full text-sm border', selectedKey === 'free' ? 'bg-indigo-600 text-white border-indigo-600' : 'border-gray-300 dark:border-gray-600 text-gray-700 dark:text-gray-200']">
          {{ t('logwizard.price_free') }}
        </button>
      </div>
      <p class="mt-2 text-xs text-gray-400 dark:text-gray-500">{{ t('logwizard.price_hint') }}</p>
    </div>
  </div>
</template>
