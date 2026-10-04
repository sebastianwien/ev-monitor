<script setup lang="ts">
import { CHIP_ROW, chipClass } from './chipClass'
import type { CommunityPrice } from './CardStrip.vue'
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
import { costMetrics } from './costMetrics'
import type { ConsumptionPreview } from '../../utils/consumptionPreview'
import { useCountryStore } from '../../stores/country'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import api from '../../api/axios'
import BigInput from './BigInput.vue'
import { CheckCircleIcon } from '@heroicons/vue/24/outline'
import SegmentToggle from './SegmentToggle.vue'

const props = defineProps<{ cost: ReturnType<typeof useCostInput>; compact?: boolean; openOnMount?: 'new' | 'price' | null
  /** Richtwert seit der letzten Ladung - steht in der Kostenzeile, weil Preis und Verbrauch zusammen gelesen werden */
  preview?: ConsumptionPreview | null
  /** Preisvorschlag, den der Wizard schon in Schritt 1 geholt hat (null = keiner). undefined = selbst holen (Bearbeiten-Dialog). */
  communityPrice?: CommunityPrice | null }>()
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
// ── Kompakt (Schritt 2): eine Zeile "Kosten". Die Einheit rechts schaltet zwischen Gesamtbetrag und
// Preis je kWh (Euroraum in ct, wie beim Anlegen einer Karte); die Zeile darunter zeigt den anderen Wert.
const compactTotal = computed(() => costMode.value === 'total' ? costLocalTotal.value : calculatedLocalTotal.value)
const compactPerKwh = computed(() => costMode.value === 'per_kwh' ? costLocalPerKwh.value : calculatedLocalPerKwh.value)
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

/** AC/DC umgeschaltet: die gewählte Karte behält ihren Platz, ihr Tarif wechselt mit (29 ct AC, 44 ct DC). */
watch(() => form.value.chargingType, type => {
  const id = form.value.chargingProviderId
  // Nur solange der Preis aus der Karte kommt (je kWh) - ein getippter Gesamtbetrag bleibt stehen
  if (!id || costMode.value !== 'per_kwh') return
  const p = providers.value.find(x => x.id === id)
  const price = p ? providerPriceForType(p, type) : null
  if (price != null) { selectedKey.value = id; setPerKwhEur(price) }
})

// ── Kompakt: ist der Preis aus Karte oder Gratis abgeleitet, reicht eine Zeile mit "Anders" ──
const manual = ref(false)
const derived = computed(() => !manual.value && (
  (costMode.value === 'per_kwh' && costLocalPerKwh.value != null)
  || (costMode.value === 'total' && costLocalTotal.value === 0 && !form.value.chargingProviderId)))
/** Unterzeile der Kostenbox: Preis je kWh, dann Kosten und Verbrauch je 100 km, sobald sie berechenbar sind */
const metricFormat = computed(() => ({ isEurCountry: isEurCountry.value, subunit: subunit.value, symbol: symbol.value, eurToLocal: props.cost.eurToLocal, formatNumber, formatDecimal }))
const derivedMetrics = computed(() => costMode.value === 'total' ? [] : costMetrics(costLocalPerKwh.value, props.preview, metricFormat.value))
// Unter dem Feld der jeweils andere Wert: bei Eingabe je kWh der Gesamtbetrag, sonst der Preis je kWh
const manualMetrics = computed(() => inputUnit.value === 'per_kwh'
  ? [...(compactTotal.value != null ? [{ value: formatDecimal(compactTotal.value, 2), unit: symbol.value }] : []), ...costMetrics(null, props.preview, metricFormat.value)]
  : costMetrics(compactPerKwh.value, props.preview, metricFormat.value))
const derivedSub = computed(() => costMode.value === 'total' ? t('logwizard.price_free') : null)

// Wer immer den kWh-Preis tippt, soll nicht jedes Mal umschalten: die Wahl bleibt in diesem Browser
const UNIT_KEY = 'cost-input-unit'
const readUnit = (): 'total' | 'per_kwh' => { try { return localStorage.getItem(UNIT_KEY) === 'per_kwh' ? 'per_kwh' : 'total' } catch { return 'total' } }
const inputUnit = ref(readUnit())
watch(inputUnit, u => { try { localStorage.setItem(UNIT_KEY, u) } catch { /* Speicher gesperrt: gilt nur jetzt */ } })
const perKwhFactor = computed(() => isEurCountry.value ? 100 : 1)
// Beide Einheiten stehen sichtbar nebeneinander, die aktive markiert: man sieht, dass es zwei gibt
const unitOptions = computed(() => [
  { value: 'total' as const, label: symbol.value },
  { value: 'per_kwh' as const, label: `${isEurCountry.value ? subunit.value : symbol.value}/kWh` },
])
const compactValue = computed(() => {
  if (inputUnit.value === 'total') return compactTotal.value
  const v = compactPerKwh.value
  return v == null ? null : Math.round(v * perKwhFactor.value * 1000) / 1000
})
const onCompactInput = (e: Event) => {
  const v = (e.target as HTMLInputElement).value
  const n = v === '' ? null : Number(v)
  // Getippt ist getippt: die grüne Zeile kommt nicht mitten im Tippen zurück
  manual.value = true
  if (inputUnit.value === 'total') { costMode.value = 'total'; costLocalTotal.value = n; return }
  costMode.value = 'per_kwh'
  costLocalTotal.value = null
  costLocalPerKwh.value = n == null ? null : n / perKwhFactor.value
}

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
  // Aus Schritt 1: "+ neue Karte" oder eine Karte ohne Tarif gewählt - der Editor öffnet hier
  if (props.openOnMount === 'new') openNewCard()
  else if (props.openOnMount === 'price') openPriceForSelected()
  // Vorbelegung in dieser Reihenfolge, nur solange der Nutzer nichts eingegeben hat:
  // 1. Ladekarte schon gewählt (Trefferkarte: "wie beim letzten Mal") - ihr Tarif,
  // 2. letzter Preis an genau diesem Ort (mit seiner Karte),
  // 3. die Karte, deren Name zum Betreiber passt.
  // Ohne Treffer bleibt der Preis offen - ein falscher Preis ist schlimmer als ein leerer.
  if (form.value.chargingProviderId && untouched()) {
    const preset = cardSuggestions.value.find(c => c.providerId === form.value.chargingProviderId)
    if (preset) pick(preset)
  }
  const asSuggestion = (c: CommunityPrice): Suggestion => ({ key: 'community', label: t('logwizard.price_community'), eurPerKwh: c.eurPerKwh, providerId: c.providerId })
  if (props.communityPrice !== undefined) {
    if (props.communityPrice) community.value = asSuggestion(props.communityPrice)
  } else if (form.value.latitude != null && form.value.longitude != null) {
    try {
      const res = await api.get('/logs/price-suggestion', {
        params: { lat: form.value.latitude, lon: form.value.longitude, isPublic: form.value.isPublicCharging, chargingType: form.value.chargingType },
      })
      if (res.data?.costPerKwh != null) community.value = asSuggestion({ eurPerKwh: Number(res.data.costPerKwh), providerId: res.data.chargingProviderId ?? null })
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
    <!-- Preis steht schon (Karte aus Schritt 1 oder Gratis): eine grüne Zeile, "Anders" macht sie zur Eingabe -->
    <div v-if="derived" data-testid="cost-derived"
      class="rounded-sm border-2 border-green-200 dark:border-green-900 bg-green-50 dark:bg-green-900/20 px-3 py-2">
      <div class="flex items-center gap-3 min-h-9">
        <CheckCircleIcon class="h-5 w-5 flex-shrink-0 text-green-600 dark:text-green-400" aria-hidden="true" />
        <b class="flex-1 min-w-0 text-2xl leading-tight tabular-nums text-gray-900 dark:text-gray-100">{{ compactTotal != null ? `${formatDecimal(compactTotal, 2)} ${symbol}` : '–' }}</b>
        <span v-if="derivedSub" class="text-xs text-gray-500 dark:text-gray-400">{{ derivedSub }}</span>
        <button type="button" data-testid="cost-other" @click="manual = true" class="min-h-11 px-2 text-sm font-semibold text-green-700 dark:text-green-300">{{ t('logwizard.cost_other') }}</button>
      </div>
      <!-- Kennzahlen in einer Zeile: Wert fett, Einheit grau, feine Trenner -->
      <p v-if="derivedMetrics.length" class="mt-0.5 flex items-center gap-x-2 text-xs leading-tight text-gray-500 dark:text-gray-400 tabular-nums overflow-hidden">
        <span v-for="(m, i) in derivedMetrics" :key="m.unit" :class="['inline-flex items-baseline gap-1 whitespace-nowrap', i > 0 && 'border-l border-gray-300 dark:border-gray-600 pl-2']">
          <b :class="['text-sm', m.tone === 'notice' ? 'text-amber-600 dark:text-amber-400' : 'text-gray-800 dark:text-gray-100']">{{ m.value }}</b>{{ m.unit }}
        </span>
      </p>
    </div>
    <template v-else>
    <!-- Box wie die Rädchen-Zeilen: Feldname, Zahl, Einheit; darunter die Kennzahlen. Gratis: 0 tippen. -->
    <div data-testid="cost-box" class="rounded-sm border-2 border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 px-3 py-2 focus-within:border-indigo-600">
      <!-- Schalter rechts über beide Zeilen; die Kennzahlen enden unter der Zahl -->
      <div class="grid grid-cols-[1fr_auto] items-center gap-x-2">
        <div class="flex items-baseline justify-between gap-x-2 min-h-9">
        <label for="wizard-cost" class="text-sm text-gray-500 dark:text-gray-400">{{ inputUnit === 'total' ? t('logfields.cost_eur') : t('logwizard.cost_price') }}</label>
        <input id="wizard-cost" type="number" inputmode="decimal" :step="inputUnit === 'total' ? 0.01 : 0.1" min="0"
          :placeholder="inputUnit === 'total' ? t('logfields.cost_eur_placeholder') : (isEurCountry ? '39' : '0.39')"
          :value="compactValue ?? ''" @input="onCompactInput"
          class="w-[8ch] min-w-0 bg-transparent border-0 p-0 text-right text-2xl font-medium tabular-nums text-gray-900 dark:text-gray-100 placeholder:text-gray-300 dark:placeholder:text-gray-600 focus:ring-0 focus:outline-none [appearance:textfield] [&::-webkit-inner-spin-button]:appearance-none [&::-webkit-outer-spin-button]:appearance-none" />
        </div>
        <!-- Die Einheit ist der Schalter: beide stehen übereinander, die aktive hinterlegt. Ein Tap irgendwo schaltet um
             (zwei Hälften wären je nur 22 px hoch). -->
        <button type="button" role="switch" data-testid="cost-unit" :aria-checked="inputUnit === 'per_kwh'" :aria-label="t('logwizard.cost_per_kwh')"
          @click="inputUnit = inputUnit === 'total' ? 'per_kwh' : 'total'"
          class="relative row-span-2 grid grid-rows-2 h-11 w-[4.5rem] rounded-lg bg-gray-200 dark:bg-gray-700 p-1 select-none">
          <span aria-hidden="true" class="absolute left-1 right-1 top-1 h-[calc(50%-0.25rem)] rounded-md bg-white dark:bg-gray-500 shadow transition-transform duration-200 ease-out"
            :class="inputUnit === 'per_kwh' ? 'translate-y-full' : ''" />
          <span v-for="o in unitOptions" :key="o.value" aria-hidden="true"
            :class="['relative z-10 flex items-center justify-center text-xs leading-none font-bold whitespace-nowrap transition-colors', inputUnit === o.value ? 'text-indigo-700 dark:text-white' : 'text-gray-500 dark:text-gray-300']">{{ o.label }}</span>
        </button>
        <p class="min-h-4 flex flex-wrap justify-end items-center gap-x-2 text-xs leading-tight text-gray-500 dark:text-gray-400 tabular-nums">
          <span v-for="(m, i) in manualMetrics" :key="m.unit" :class="['inline-flex items-baseline gap-1 whitespace-nowrap', i > 0 && 'border-l border-gray-300 dark:border-gray-600 pl-2']">
            <b :class="['text-sm', m.tone === 'notice' ? 'text-amber-600 dark:text-amber-400' : 'text-gray-800 dark:text-gray-100']">{{ m.value }}</b>{{ m.unit }}
          </span>
        </p>
      </div>
    </div>
    </template>
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
