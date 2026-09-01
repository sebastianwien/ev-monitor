<script setup lang="ts">
/**
 * Heimlade-Ersparnis.
 *
 * Aufbau von oben nach unten: der Jahresbetrag als Anker, darunter zwei Balken, die ihn
 * belegen, am Fuss die Amortisation. Die Balken stehen bewusst ueber dem Tap - die Zahl
 * ist eine kontrafaktische Aussage ("haettest du oeffentlich geladen"), und die erste
 * Rueckfrage jedes skeptischen Nutzers ("wie kommt ihr darauf?") wird ohne Umweg
 * beantwortet.
 */
import { computed, ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { HomeIcon, InformationCircleIcon, PlusIcon, ChevronRightIcon } from '@heroicons/vue/24/outline'
import {
  applyPublicPriceOverride,
  loadPublicPriceOverride,
  savePublicPriceOverride,
  clearPublicPriceOverride,
  type ChargingSavings,
} from './chargingSavings'

const props = defineProps<{ savings: ChargingSavings | null }>()
const emit = defineEmits<{ (e: 'edit-investment'): void; (e: 'open-details'): void }>()

const { t, n } = useI18n()

/** Ueber acht Jahren wird die Restlaufzeit gedaempft und als Hebel formuliert statt als
 *  Urteil - beim typischen Heimlader steht dort real eine zweistellige Zahl. */
const SLOW_AMORTISATION_YEARS = 8

const override = ref<number | null>(null)
onMounted(() => { override.value = loadPublicPriceOverride() })

const view = computed(() =>
  props.savings ? applyPublicPriceOverride(props.savings, override.value) : null)

const homeBarWidth = computed(() => {
  if (!view.value || view.value.wouldHaveCostEur <= 0) return '0%'
  const pct = (view.value.actuallyPaidEur / view.value.wouldHaveCostEur) * 100
  return `${Math.max(0, Math.min(100, pct))}%`
})

const amortisationPct = computed(() => {
  const v = view.value
  if (!v?.investmentEur || v.recoveredEur == null) return 0
  return Math.max(0, Math.min(100, (v.recoveredEur / v.investmentEur) * 100))
})

const isSlow = computed(() =>
  (view.value?.amortisationYearsRemaining ?? 0) > SLOW_AMORTISATION_YEARS)

/** Benennt die verwendete Stufe. Wer "Median in Deutschland" liest statt "deine eigenen
 *  Ladungen", sieht sofort, dass eigenes Loggen die Zahl schaerfer macht. */
const basisLabel = computed(() => {
  const v = view.value
  if (!v) return ''
  if (v.isOverridden) return t('savings.basis_override', { price: money(v.publicPricePerKwh, 3) })
  switch (v.publicPriceBasis) {
    case 'OWN_PUBLIC': return t('savings.basis_own_public', { count: v.publicPriceSampleSize })
    case 'REGION': return t('savings.basis_region', { count: v.publicPriceSampleSize })
    default: return t('savings.basis_country', { count: v.publicPriceSampleSize })
  }
})

function money(value: number, digits = 2): string {
  return n(value, { style: 'currency', currency: 'EUR', maximumFractionDigits: digits })
}

function applyOverride(value: number | null) {
  if (value == null) {
    clearPublicPriceOverride()
    override.value = null
  } else if (savePublicPriceOverride(value)) {
    override.value = value
  }
}

defineExpose({ applyOverride })
</script>

<template>
  <div v-if="view"
       class="bg-white dark:bg-gray-700 rounded-sm border-2 border-gray-300 dark:border-gray-600 overflow-hidden shadow-[2px_2px_0_0_#d1d5db] dark:shadow-[2px_2px_0_0_#4b5563] p-4">

    <div class="flex items-center gap-2 mb-4">
      <HomeIcon class="h-5 w-5 flex-none text-emerald-600 dark:text-emerald-400" />
      <span class="text-xs font-semibold text-gray-500 dark:text-gray-400">{{ t('savings.title') }}</span>
    </div>

    <!-- Anker -->
    <p class="text-4xl font-bold tracking-tight tabular-nums text-gray-900 dark:text-gray-50">
      {{ money(view.savingsEur) }}
    </p>
    <p class="mt-1.5 text-xs text-gray-500 dark:text-gray-400">{{ t('savings.saved_last_12_months') }}</p>

    <!-- Beleg -->
    <div class="mt-4 space-y-2.5">
      <div>
        <div class="flex justify-between text-xs mb-1">
          <span class="text-gray-500 dark:text-gray-400">{{ t('savings.would_have_cost') }}</span>
          <span class="font-semibold tabular-nums text-gray-900 dark:text-gray-100">{{ money(view.wouldHaveCostEur) }}</span>
        </div>
        <div class="h-4 rounded bg-gray-200 dark:bg-gray-600"></div>
      </div>
      <div>
        <div class="flex justify-between text-xs mb-1">
          <span class="text-gray-500 dark:text-gray-400">
            {{ t('savings.paid_at_home', { kwh: n(view.homeKwh, { maximumFractionDigits: 0 }) }) }}
          </span>
          <span class="font-semibold tabular-nums text-gray-900 dark:text-gray-100">{{ money(view.actuallyPaidEur) }}</span>
        </div>
        <div class="h-4 rounded bg-emerald-600 dark:bg-emerald-500" :style="{ width: homeBarWidth }"></div>
      </div>
    </div>

    <!-- Amortisation, nur mit hinterlegter Investition -->
    <div v-if="view.investmentEur != null" class="mt-4 pt-4 border-t border-gray-200 dark:border-gray-600">
      <div class="flex justify-between items-baseline text-xs mb-2 tabular-nums">
        <span class="text-gray-500 dark:text-gray-400">{{ t('savings.wallbox_recovered') }}</span>
        <span class="font-semibold text-gray-900 dark:text-gray-100">
          {{ money(view.recoveredEur ?? 0) }}
          <span class="font-normal text-gray-400 dark:text-gray-500">
            {{ t('savings.of_investment', { total: money(view.investmentEur) }) }}
          </span>
        </span>
      </div>
      <div class="h-1.5 rounded-full bg-gray-200 dark:bg-gray-600 overflow-hidden">
        <div class="h-full rounded-full bg-emerald-600 dark:bg-emerald-500 transition-all"
             :style="{ width: `${amortisationPct}%` }"></div>
      </div>
      <p v-if="view.fullyAmortised" class="mt-2 text-xs text-emerald-700 dark:text-emerald-300">
        {{ t('savings.fully_amortised') }}
      </p>
      <p v-else-if="view.amortisationYearsRemaining != null"
         class="mt-2 text-xs leading-relaxed"
         :class="isSlow
           ? 'bg-amber-50 dark:bg-amber-900/30 text-amber-800 dark:text-amber-200 rounded px-2.5 py-2'
           : 'text-gray-500 dark:text-gray-400'">
        {{ isSlow
          ? t('savings.amortisation_slow', { years: n(view.amortisationYearsRemaining, { maximumFractionDigits: 0 }) })
          : t('savings.amortisation_remaining', { years: n(view.amortisationYearsRemaining, { maximumFractionDigits: 1 }) }) }}
      </p>
    </div>

    <!-- Ohne Investition: genau hier sammeln wir die Eingabe ein, die das Feature braucht -->
    <button v-else type="button"
            class="mt-4 pt-4 border-t border-gray-200 dark:border-gray-600 w-full flex items-center gap-2 text-xs text-emerald-700 dark:text-emerald-300"
            @click="emit('edit-investment')">
      <PlusIcon class="h-4 w-4 flex-none" />
      <span>{{ t('savings.add_investment') }}</span>
      <ChevronRightIcon class="h-3 w-3 ml-auto flex-none text-gray-400" />
    </button>

    <button type="button"
            class="mt-4 pt-3 border-t border-gray-200 dark:border-gray-600 w-full flex items-start gap-1.5 text-left text-[11px] leading-snug text-gray-400 dark:text-gray-500"
            @click="emit('open-details')">
      <InformationCircleIcon class="h-3.5 w-3.5 mt-px flex-none" />
      <span>{{ basisLabel }}</span>
    </button>
  </div>
</template>
