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
import ChargingCardTile from '../shared/ChargingCardTile.vue'
import { cardContainerStyle } from '../../composables/useChargingCardDesign'
import { CheckCircleIcon, PlusIcon, ClockIcon, GiftIcon } from '@heroicons/vue/24/outline'
import SegmentToggle from './SegmentToggle.vue'

const props = defineProps<{ cost: ReturnType<typeof useCostInput>; compact?: boolean }>()
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
// ── Kompakt (Schritt 2): eine Zeile "Bezahlt", Chips darunter, kein Gesamt/Je-kWh-Umschalter.
// Ein Chip setzt den Preis je kWh, die Zeile zeigt dann den errechneten Gesamtbetrag; tippt
// der Nutzer in die Zeile, gilt wieder sein Gesamtbetrag.
const compactTotal = computed(() => costMode.value === 'total' ? costLocalTotal.value : calculatedLocalTotal.value)
const onCompactInput = (e: Event) => {
  const v = (e.target as HTMLInputElement).value
  costMode.value = 'total'
  costLocalTotal.value = v === '' ? null : Number(v)
}
const compactSub = computed(() => {
  if (costMode.value === 'per_kwh' && costLocalPerKwh.value != null) {
    const kwh = form.value.kwhCharged ?? form.value.kwhAtVehicle
    return `${formatDecimal(costLocalPerKwh.value, 2)} ${symbol.value}/kWh${kwh ? ` × ${formatNumber(kwh)} kWh` : ''}`
  }
  return calculatedLocalPerKwh.value != null ? `= ${formatDecimal(calculatedLocalPerKwh.value, 2)} ${symbol.value}/kWh` : null
})
/** Kartenstreifen: öffentlich die Ladekarten, zuhause nur der Heimtarif - auch Karten ohne Tarif, die holen ihn sich beim Tap. */
const stripProviders = computed(() => providers.value.filter(p => form.value.isPublicCharging ? !p.isPrivate : p.isPrivate))
const stripPrice = (p: ChargingProvider) => providerPriceForType(p, form.value.chargingType)
const pickProvider = (p: ChargingProvider) => {
  const price = stripPrice(p)
  if (price != null) { pick({ key: p.id, label: p.label || p.providerName, eurPerKwh: price, providerId: p.id }); return }
  selectedKey.value = p.id
  form.value.chargingProviderId = p.id
  openPriceForSelected()
}
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

/** Noch nichts eingegeben: dann darf ein Vorschlag vorbelegen, sonst nie. */
const untouched = () => selectedKey.value == null && costLocalTotal.value == null && costLocalPerKwh.value == null

/**
 * Die Ladekarte, die zum Betreiber passt: "EnBW mobility+" an einer EnBW-Säule, die Karte
 * "Kaufland" bei Kaufland. Nur bei genau einem Treffer, sonst rät der Wizard nicht.
 */
const cardMatchingCpo = (): Suggestion | null => {
  const cpo = form.value.cpoName?.trim().toLowerCase()
  if (!cpo || !form.value.isPublicCharging) return null
  const hits = cardSuggestions.value.filter(c => {
    const name = c.label.toLowerCase()
    return name.includes(cpo) || cpo.includes(name)
  })
  return hits.length === 1 ? hits[0] : null
}

onMounted(async () => {
  // Vorbelegung in dieser Reihenfolge, nur solange der Nutzer nichts eingegeben hat:
  // 1. Ladekarte schon gewählt (Trefferkarte: "wie beim letzten Mal") - ihr Tarif,
  // 2. letzter Preis an genau diesem Ort (mit seiner Karte),
  // 3. die Karte, deren Name zum Betreiber passt.
  // Ohne Treffer bleibt der Preis offen - ein falscher Preis ist schlimmer als ein leerer.
  if (form.value.chargingProviderId && untouched()) {
    const preset = cardSuggestions.value.find(c => c.providerId === form.value.chargingProviderId)
    if (preset) pick(preset)
  }
  if (form.value.latitude != null && form.value.longitude != null) {
    try {
      const res = await api.get('/logs/price-suggestion', {
        params: { lat: form.value.latitude, lon: form.value.longitude, isPublic: form.value.isPublicCharging, chargingType: form.value.chargingType },
      })
      if (res.data?.costPerKwh != null) {
        community.value = { key: 'community', label: t('logwizard.price_community'), eurPerKwh: Number(res.data.costPerKwh), providerId: res.data.chargingProviderId ?? null }
      }
    } catch { /* kein Vorschlag - kein Problem */ }
  }
  if (!untouched()) return
  const auto = community.value ?? cardMatchingCpo()
  if (auto) pick(auto)
})
</script>

<template>
  <!-- Kompakt (Schritt 2): Zeile wie die Rädchen, Chips darunter, Karte inline -->
  <div v-if="props.compact" class="space-y-2">
    <div class="rounded-sm border-2 border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 px-3 py-2 focus-within:border-indigo-600">
      <div class="grid grid-cols-[1fr_auto_auto] items-baseline gap-x-2 min-h-9">
        <label for="wizard-cost" class="text-sm text-gray-500 dark:text-gray-400">{{ t('logfields.cost_eur') }}</label>
        <input id="wizard-cost" type="number" inputmode="decimal" step="0.01" min="0" :placeholder="t('logfields.cost_eur_placeholder')"
          :value="compactTotal ?? ''" :aria-label="t('logfields.cost_eur')" @input="onCompactInput"
          class="w-[8ch] min-w-0 bg-transparent border-0 p-0 text-right text-2xl font-medium tabular-nums text-gray-900 dark:text-gray-100 placeholder:text-gray-300 dark:placeholder:text-gray-600 focus:ring-0 focus:outline-none [appearance:textfield] [&::-webkit-inner-spin-button]:appearance-none [&::-webkit-outer-spin-button]:appearance-none" />
        <span class="text-base text-gray-500 dark:text-gray-400">{{ symbol }}</span>
        <span v-if="compactSub" class="col-span-3 text-right text-xs tabular-nums -mt-1 text-gray-400 dark:text-gray-500">{{ compactSub }}</span>
      </div>
    </div>
    <!-- Eine Zeile, horizontal wischbar, edge-to-edge auf Mobile: "Zuletzt hier", die Ladekarten
         im Kreditkarten-Look, Gratis, neue Karte. Die gewählte trägt Ring und Haken. -->
    <div v-if="!inlineCard.isOpen.value" data-testid="card-strip"
      class="flex gap-2.5 overflow-x-auto snap-x snap-mandatory -mx-4 px-4 pt-1 pb-2 md:mx-0 md:px-0 [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
      <button v-if="community" type="button" :aria-pressed="selectedKey === 'community'" @click="pick(community)"
        :class="['btn-3d snap-start relative flex-shrink-0 w-28 h-[4.5rem] rounded-sm p-2.5 text-left flex flex-col justify-between bg-gray-100 dark:bg-gray-700',
                 selectedKey === 'community' ? 'active ring-2 ring-inset ring-indigo-500' : '']">
        <ClockIcon class="h-4 w-4 text-gray-500 dark:text-gray-300" />
        <span><b class="block text-[11px] font-bold leading-tight text-gray-800 dark:text-gray-100">{{ community.label }}</b>
          <span class="block text-[10px] leading-tight text-gray-500 dark:text-gray-400 tabular-nums">{{ priceLabel(community.eurPerKwh) }}</span></span>
        <CheckCircleIcon v-if="selectedKey === 'community'" class="absolute top-1 right-1 h-5 w-5 rounded-full bg-white text-indigo-600 dark:bg-gray-800" aria-hidden="true" />
      </button>
      <button v-for="p in stripProviders" :key="p.id" type="button" :aria-pressed="selectedKey === p.id" @click="pickProvider(p)"
        :class="['btn-3d snap-start relative flex-shrink-0 w-28 h-[4.5rem] rounded-sm', selectedKey === p.id ? 'active ring-2 ring-inset ring-indigo-500' : '']"
        :style="{ '--btn-shadow-color': cardContainerStyle(p.id)['--btn-shadow-color'] }">
        <ChargingCardTile class="w-full h-full" :id="p.id" :title="p.label || p.providerName"
          :subtitle="stripPrice(p) != null ? priceLabel(stripPrice(p)!) : t('logfields.card_no_price_dot')" />
        <CheckCircleIcon v-if="selectedKey === p.id" class="absolute top-1 right-1 h-5 w-5 rounded-full bg-white text-indigo-600 dark:bg-gray-800" aria-hidden="true" />
        <span v-else-if="stripPrice(p) == null" class="absolute top-1 right-1 h-2.5 w-2.5 rounded-full bg-amber-400 ring-2 ring-white dark:ring-gray-800" aria-hidden="true" />
      </button>
      <button type="button" :aria-pressed="selectedKey === 'free'" @click="pickFree"
        :class="['btn-3d snap-start relative flex-shrink-0 w-20 h-[4.5rem] rounded-sm p-2.5 text-left flex flex-col justify-between bg-gray-100 dark:bg-gray-700',
                 selectedKey === 'free' ? 'active ring-2 ring-inset ring-indigo-500' : '']">
        <GiftIcon class="h-4 w-4 text-gray-500 dark:text-gray-300" />
        <b class="block text-[11px] font-bold leading-tight text-gray-800 dark:text-gray-100">{{ t('logwizard.price_free') }}</b>
        <CheckCircleIcon v-if="selectedKey === 'free'" class="absolute top-1 right-1 h-5 w-5 rounded-full bg-white text-indigo-600 dark:bg-gray-800" aria-hidden="true" />
      </button>
      <button v-if="form.isPublicCharging" type="button" data-testid="charging-card-prompt-open" @click="openNewCard"
        class="snap-start flex-shrink-0 w-20 h-[4.5rem] rounded-sm border-2 border-dashed border-gray-300 dark:border-gray-600 p-2 flex flex-col items-start justify-between text-left text-gray-500 dark:text-gray-400 hover:border-indigo-400 hover:text-indigo-600">
        <PlusIcon class="h-4 w-4" />
        <span class="text-[11px] font-semibold leading-tight">{{ t('logwizard.card_add') }}</span>
      </button>
    </div>
      <div v-if="inlineCard.isOpen.value" data-testid="charging-card-prompt" class="rounded-sm border border-dashed border-indigo-300 dark:border-indigo-700 bg-indigo-50/60 dark:bg-indigo-950/30 p-3 space-y-2.5">
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

    <label v-if="pricelessCount > 0 && form.chargingProviderId" class="flex items-start gap-2 text-sm text-gray-700 dark:text-gray-200">
      <input v-model="form.applyTariffToLocation" type="checkbox" class="mt-0.5 rounded-sm border-gray-300 text-indigo-600" />
      <span>{{ t('logfields.apply_tariff_to_location', pricelessCount) }}</span>
    </label>
  </div>

  <!-- Voll (Bearbeiten-Dialog): Lese-Zone oben, Bedien-Zone unten in Daumenreichweite (siehe StepEnergy) -->
  <div v-else class="flex-1 flex flex-col gap-4">
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

      <div v-if="inlineCard.isOpen.value" data-testid="charging-card-prompt" class="rounded-sm border border-dashed border-indigo-300 dark:border-indigo-700 bg-indigo-50/60 dark:bg-indigo-950/30 p-3 space-y-2.5">
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
