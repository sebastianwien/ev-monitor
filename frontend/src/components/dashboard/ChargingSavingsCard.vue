<script setup lang="ts">
/**
 * Kostenersparnis durch Heimladen - volle Breite.
 *
 * Die Skala ist die Aussage: ihre gesamte Breite ist, was oeffentliches Laden gekostet
 * haette. Gruen gefuellt, was tatsaechlich gezahlt wurde - der helle Rest ist die
 * Ersparnis als Flaeche. Die Zahl ist kontrafaktisch ("haettest du oeffentlich geladen"),
 * und als Flaeche muss man sie nicht glauben, man sieht sie.
 *
 * Darunter die Amortisation als Zeitschiene mit Jahresstrichen. Das Enddatum ist eine
 * Fortschreibung des heutigen Ladeverhaltens und deshalb bewusst grob gehalten.
 */
import { computed, ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  HomeIcon, InformationCircleIcon, PlusIcon, ChevronRightIcon,
  AdjustmentsHorizontalIcon, PencilSquareIcon, ArrowUturnLeftIcon,
} from '@heroicons/vue/24/outline'
import {
  applyPriceOverrides,
  loadPriceOverrides,
  savePriceOverride,
  clearPriceOverrides,
  type ChargingSavings,
  type PriceOverrides,
} from './chargingSavings'
import { amortisationTimeline } from './amortisationTimeline'

const props = defineProps<{
  savings: ChargingSavings | null
  /** Schaufenster-Modus fuer die Upsell-Seite: kein lokaler Vergleichspreis, keine
   *  Bedienelemente, die ins Leere fuehren. */
  demo?: boolean
}>()
const emit = defineEmits<{ (e: 'edit-investment'): void }>()

const { t, n } = useI18n()

const overrides = ref<PriceOverrides>({ home: null, public: null })
const showPlayground = ref(false)
onMounted(() => { if (!props.demo) overrides.value = loadPriceOverrides() })

const view = computed(() =>
  props.savings ? applyPriceOverrides(props.savings, overrides.value) : null)

/** Anteil der Skala, den die tatsaechlich gezahlten Kosten einnehmen. */
const paidWidth = computed(() => {
  const v = view.value
  if (!v || v.wouldHaveCostEur <= 0) return '0%'
  return `${Math.max(0, Math.min(100, (v.actuallyPaidEur / v.wouldHaveCostEur) * 100))}%`
})

const timeline = computed(() => {
  const v = view.value
  if (!v || v.investmentEur == null || v.firstYear == null) return null
  return amortisationTimeline({
    startYear: v.firstYear,
    yearsRemaining: v.fullyAmortised ? 0 : v.amortisationYearsRemaining,
    now: new Date(),
  })
})

/** "Anfang 2031" statt eines Datums auf den Tag - mehr Genauigkeit waere bei einer
 *  Fortschreibung nicht ehrlich. */
const endLabel = computed(() => {
  const tl = timeline.value
  if (!tl?.endYear || !tl.endPart) return null
  return t(`savings.year_part_${tl.endPart}`, { year: tl.endYear })
})

/** Benennt die verwendete Stufe der Preiskette. Wer "Median im Land" liest statt "deine
 *  eigenen Ladungen", sieht sofort, dass eigenes Loggen die Zahl schaerfer macht. */
const basisLabel = computed(() => {
  const v = view.value
  if (!v) return ''
  if (v.isOverridden) return t('savings.basis_override', { price: centsPerKwh(v.publicPricePerKwh) })
  switch (v.publicPriceBasis) {
    case 'OWN_PUBLIC': return t('savings.basis_own_public', { count: v.publicPriceSampleSize })
    case 'REGION': return t('savings.basis_region', { count: v.publicPriceSampleSize })
    default: return t('savings.basis_country', { count: v.publicPriceSampleSize })
  }
})

function money(value: number): string {
  return n(value, { style: 'currency', currency: 'EUR', maximumFractionDigits: 2 })
}

function centsPerKwh(eurPerKwh: number): string {
  return t('savings.cents_per_kwh', { value: n(eurPerKwh * 100, { maximumFractionDigits: 1 }) })
}

/** Eingabe in ct/kWh, gespeichert in EUR - Ladepreise werden in Cent verglichen. */
function setOverride(kind: 'home' | 'public', cents: string | number) {
  const text = String(cents ?? '').trim()
  const value = text === '' ? null : Number(text) / 100
  if (text !== '' && !Number.isFinite(value!)) return
  if (savePriceOverride(kind, value)) {
    overrides.value = { ...overrides.value, [kind]: value }
  }
}

function resetOverrides() {
  clearPriceOverrides()
  overrides.value = { home: null, public: null }
}

/** Vorbelegung der Felder: der eigene Wert, sonst der berechnete. */
function inputValue(kind: 'home' | 'public'): string {
  const own = overrides.value[kind]
  const fallback = kind === 'home' ? view.value?.homePricePerKwh : view.value?.publicPricePerKwh
  const eur = own ?? fallback
  return eur == null ? '' : String(Math.round(eur * 1000) / 10)
}
</script>

<template>
  <div v-if="view"
       class="bg-white dark:bg-gray-700 rounded-sm border-2 border-gray-300 dark:border-gray-600 shadow-[2px_2px_0_0_#d1d5db] dark:shadow-[2px_2px_0_0_#4b5563] p-4 md:p-5">

    <div class="relative flex items-center justify-center gap-2 mb-4">
      <HomeIcon class="h-5 w-5 flex-none text-emerald-600 dark:text-emerald-400" />
      <span class="text-sm font-semibold text-gray-500 dark:text-gray-400">{{ t('savings.title') }}</span>
      <!-- Sichtbarer Hinweis, dass sich hier etwas einstellen laesst. Ohne ihn ist die
           Spielerei unauffindbar - Klickbarkeit allein signalisiert nichts. -->
      <button v-if="!demo" type="button"
              class="absolute right-0 flex items-center gap-1 rounded px-2 py-1 text-xs text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200"
              :aria-expanded="showPlayground" :aria-label="t('savings.playground_toggle')"
              @click="showPlayground = !showPlayground">
        <AdjustmentsHorizontalIcon class="h-4 w-4" />
        <span class="hidden sm:inline">{{ t('savings.playground_toggle') }}</span>
      </button>
    </div>

    <!-- Eigene Annahmen. Bewusst in ct/kWh - so vergleicht man Ladepreise. -->
    <div v-if="showPlayground && !demo"
         class="mb-4 rounded-sm border border-gray-200 dark:border-gray-600 bg-gray-50 dark:bg-gray-800 p-3">
      <p class="mb-3 text-xs text-gray-500 dark:text-gray-400">{{ t('savings.playground_hint') }}</p>
      <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
        <label class="block">
          <span class="mb-1 block text-xs font-medium text-gray-600 dark:text-gray-300">{{ t('savings.playground_home') }}</span>
          <input type="number" inputmode="decimal" min="0" max="200" step="0.1"
                 :value="inputValue('home')"
                 class="w-full rounded-sm border border-gray-300 px-2 py-1.5 text-sm dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100"
                 @change="setOverride('home', ($event.target as HTMLInputElement).value)" />
        </label>
        <label class="block">
          <span class="mb-1 block text-xs font-medium text-gray-600 dark:text-gray-300">{{ t('savings.playground_public') }}</span>
          <input type="number" inputmode="decimal" min="0" max="200" step="0.1"
                 :value="inputValue('public')"
                 class="w-full rounded-sm border border-gray-300 px-2 py-1.5 text-sm dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100"
                 @change="setOverride('public', ($event.target as HTMLInputElement).value)" />
        </label>
      </div>
      <button v-if="view.isOverridden" type="button"
              class="mt-3 flex items-center gap-1.5 text-xs text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200"
              @click="resetOverrides">
        <ArrowUturnLeftIcon class="h-3.5 w-3.5" />
        {{ t('savings.playground_reset') }}
      </button>
    </div>

    <!-- Anker -->
    <div class="flex flex-wrap items-baseline gap-x-3 gap-y-1 mb-4">
      <span class="text-4xl font-bold tracking-tight tabular-nums text-gray-900 dark:text-gray-50">
        {{ money(view.savingsEur) }}
      </span>
      <span class="text-sm text-gray-500 dark:text-gray-400">
        {{ t('savings.saved_last_12_months') }} ·
        {{ t('savings.home_kwh_charged', { kwh: n(view.homeKwh, { maximumFractionDigits: 0 }) }) }}
      </span>
    </div>

    <!-- Die Skala: volle Breite = oeffentliche Kosten, gefuellt = tatsaechlich gezahlt -->
    <div class="relative h-9 rounded bg-gray-200 dark:bg-gray-600 overflow-hidden">
      <div class="absolute inset-y-0 left-0 bg-emerald-600 dark:bg-emerald-500 rounded"
           :style="{ width: paidWidth }"></div>
      <div v-if="view.savingsEur > 0"
           class="absolute inset-y-0 right-0 flex items-center justify-center px-2 text-sm font-semibold text-gray-800 dark:text-gray-100"
           :style="{ left: paidWidth }">
        + {{ money(view.savingsEur) }}
      </div>
    </div>
    <div class="mt-2 flex flex-wrap justify-between gap-x-4 gap-y-1 text-sm text-gray-500 dark:text-gray-400">
      <span>
        {{ t('savings.paid_at_home_short') }} · {{ centsPerKwh(view.homePricePerKwh) }} ·
        <span class="font-semibold text-gray-900 dark:text-gray-100">{{ money(view.actuallyPaidEur) }}</span>
      </span>
      <span>
        {{ t('savings.would_have_cost') }} · {{ centsPerKwh(view.publicPricePerKwh) }} ·
        <span class="font-semibold text-gray-900 dark:text-gray-100">{{ money(view.wouldHaveCostEur) }}</span>
      </span>
    </div>

    <!-- Amortisation als Zeitschiene -->
    <div v-if="timeline" class="mt-5 pt-4 border-t border-gray-200 dark:border-gray-600">
      <component :is="demo ? 'div' : 'button'" :type="demo ? undefined : 'button'"
              class="w-full flex justify-between items-baseline text-sm tabular-nums text-left mb-2"
              :aria-label="demo ? undefined : t('savings.edit_investment')"
              @click="!demo && emit('edit-investment')">
        <span class="text-gray-500 dark:text-gray-400">{{ t('savings.wallbox_recovered') }}</span>
        <span class="font-semibold text-gray-900 dark:text-gray-100">
          {{ money(view.recoveredEur ?? 0) }}
          <span class="font-normal text-gray-400 dark:text-gray-500">
            {{ t('savings.of_investment', { total: money(view.investmentEur!) }) }}
          </span>
          <PencilSquareIcon v-if="!demo" class="ml-1.5 inline h-4 w-4 align-text-bottom text-gray-400 dark:text-gray-500" />
        </span>
      </component>

      <div class="relative h-2.5 rounded-full bg-gray-200 dark:bg-gray-600 overflow-hidden">
        <div class="absolute inset-y-0 left-0 bg-emerald-600 dark:bg-emerald-500"
             :style="{ width: `${timeline.progressPct}%` }"></div>
        <span v-for="(pct, i) in timeline.tickPercents" :key="i"
              class="absolute inset-y-0 w-px bg-white/80 dark:bg-gray-700/80"
              :style="{ left: `${pct}%` }"></span>
      </div>

      <div class="mt-2 flex justify-between gap-3 text-xs text-gray-500 dark:text-gray-400 tabular-nums">
        <span>{{ timeline.startYear }}</span>
        <span v-if="view.fullyAmortised" class="text-emerald-700 dark:text-emerald-300">
          {{ t('savings.fully_amortised') }}
        </span>
        <span v-else-if="endLabel">{{ t('savings.paid_off_by', { date: endLabel }) }}</span>
      </div>
    </div>

    <!-- Ohne Investition: hier sammeln wir die Eingabe ein, die die Zeitschiene braucht -->
    <button v-else type="button"
            class="mt-5 pt-4 border-t border-gray-200 dark:border-gray-600 w-full flex items-start gap-2 text-left text-sm leading-relaxed text-emerald-700 dark:text-emerald-300"
            @click="emit('edit-investment')">
      <PlusIcon class="h-4 w-4 mt-px flex-none" />
      <span class="min-w-0">{{ t('savings.add_investment') }}</span>
      <ChevronRightIcon class="h-4 w-4 mt-0.5 ml-auto flex-none text-gray-400" />
    </button>

    <!-- Reine Herkunftsangabe, bewusst kein Button -->
    <p class="mt-4 pt-3 border-t border-gray-200 dark:border-gray-600 flex items-start gap-1.5 text-xs leading-relaxed text-gray-500 dark:text-gray-400">
      <InformationCircleIcon class="h-4 w-4 mt-px flex-none" />
      <span>{{ basisLabel }}</span>
    </p>

    <!-- Der Rechenweg, aufklappbar. Wer die Zahl glaubt, muss nicht lesen; wer sie
         anzweifelt, findet die Antwort ohne nachzufragen. Natives details-Element:
         tastaturbedienbar, ohne JavaScript, ohne zusaetzliche Hoehe im Normalfall. -->
    <details class="mt-3 text-sm leading-relaxed text-gray-500 dark:text-gray-400">
      <summary class="cursor-pointer select-none py-1 font-medium marker:text-gray-400">
        {{ t('savings.how_summary') }}
      </summary>
      <div class="mt-2 space-y-3 pb-1">
        <p>{{ t('savings.how_intro') }}</p>
        <p>{{ t('savings.how_paid') }}</p>
        <p>{{ t('savings.how_comparison') }}</p>
        <p>{{ t('savings.how_per_year') }}</p>
        <p v-if="view.investmentEur != null">{{ t('savings.how_amortisation') }}</p>
        <p>{{ t('savings.how_excluded') }}</p>
      </div>
    </details>
  </div>
</template>
