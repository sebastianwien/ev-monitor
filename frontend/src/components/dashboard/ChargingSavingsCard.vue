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
  HomeIcon, InformationCircleIcon, PlusIcon, ChevronRightIcon, ChevronDownIcon,
  AdjustmentsHorizontalIcon, PencilSquareIcon, ArrowUturnLeftIcon, ClockIcon, XMarkIcon,
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
  /** Der Nutzer sieht die Kachel nur ueber den Probemonat - dann der Retention-Hinweis. */
  trial?: boolean
  /** Ablauf des Probemonats (ISO YYYY-MM-DD) fuer den Hinweistext. */
  trialEndsAt?: string | null
  /** Ziel des "dauerhaft freischalten"-CTA - das Dashboard entscheidet Supporter vs. AutoSync. */
  upsellTarget?: string
}>()
const emit = defineEmits<{ (e: 'edit-investment'): void; (e: 'dismiss'): void }>()

const { t, n, locale } = useI18n()

/** "3. Oktober" statt eines ISO-Datums - der Hinweis nennt den Tag, an dem Schluss ist. */
const trialEndLabel = computed(() => {
  if (!props.trialEndsAt) return ''
  return new Date(props.trialEndsAt).toLocaleDateString(locale.value, { day: 'numeric', month: 'long' })
})

const overrides = ref<PriceOverrides>({ home: null, public: null })
const showPlayground = ref(false)
onMounted(() => { if (!props.demo) overrides.value = loadPriceOverrides() })

// Einklappbar wie die anderen Dashboard-Widgets - nur mobil (auf sm+ immer offen via
// sm:!block). Zustand pro Geraet in localStorage, gleiches Muster wie DashboardInsights.
const LS_COLLAPSED = 'savings_collapsed'
const collapsed = ref(localStorage.getItem(LS_COLLAPSED) === 'true')
function toggleCollapsed() {
  collapsed.value = !collapsed.value
  localStorage.setItem(LS_COLLAPSED, String(collapsed.value))
}

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
  <!-- Ein Wurzelelement, damit die Kachel Attribute des Aufrufers erbt - mit zwei
       Wurzeln faellt ein uebergebenes class (etwa der Aussenabstand) lautlos weg. -->
  <div v-if="view"
       class="bg-white dark:bg-gray-700 rounded-sm border-2 border-gray-300 dark:border-gray-600 shadow-[2px_2px_0_0_#d1d5db] dark:shadow-[2px_2px_0_0_#4b5563] p-4 md:p-5">

    <div class="relative flex items-center justify-center gap-2 mb-4">
      <!-- Mobiler Ein-/Ausklapp-Chevron (auf sm+ ist die Kachel immer offen). Links, damit
           er nicht mit "Preise anpassen" rechts kollidiert. -->
      <button v-if="!demo" type="button"
              class="absolute left-0 sm:hidden p-1.5 -ml-1"
              :aria-expanded="!collapsed" :aria-label="t('savings.title')"
              @click="toggleCollapsed">
        <ChevronDownIcon class="h-4 w-4 text-gray-400 transition-transform duration-200" :class="{ 'rotate-180': !collapsed }" />
      </button>
      <HomeIcon class="h-5 w-5 flex-none text-emerald-600 dark:text-emerald-400" />
      <span class="text-sm font-semibold text-gray-500 dark:text-gray-400">{{ t('savings.title') }}</span>
      <!-- Sichtbarer Hinweis, dass sich hier etwas einstellen laesst. Ohne ihn ist die
           Spielerei unauffindbar - Klickbarkeit allein signalisiert nichts. Mobil ausgeblendet,
           solange die Kachel eingeklappt ist. -->
      <button v-if="!demo" type="button"
              class="absolute right-0 items-center gap-1 rounded px-2 py-1 text-xs text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200"
              :class="collapsed ? 'hidden sm:flex' : 'flex'"
              :aria-expanded="showPlayground" :aria-label="t('savings.playground_toggle')"
              @click="showPlayground = !showPlayground">
        <AdjustmentsHorizontalIcon class="h-4 w-4" />
        <span class="hidden sm:inline">{{ t('savings.playground_toggle') }}</span>
      </button>
    </div>

    <!-- Alles unterhalb des Kopfes ist der einklappbare Teil (nur mobil; sm+ immer offen). -->
    <div v-show="demo || !collapsed" class="sm:!block">

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
    <div class="flex flex-wrap items-baseline gap-x-3 gap-y-1 mb-3">
      <span class="text-3xl font-bold tracking-tight tabular-nums text-gray-900 dark:text-gray-50">
        {{ money(view.savingsEur) }}
      </span>
      <span class="text-xs text-gray-500 dark:text-gray-400">
        {{ view.firstYear ? t('savings.saved_since', { year: view.firstYear }) : t('savings.saved_total') }} ·
        {{ t('savings.home_kwh_charged', { kwh: n(view.homeKwh, { maximumFractionDigits: 0 }) }) }}
      </span>
    </div>

    <!-- Die Skala: volle Breite = oeffentliche Kosten, gefuellt = tatsaechlich gezahlt,
         heller Rest = Ersparnis. Die Zahl steht schon oben als Anker, daher hier ohne
         Label - der Balken zeigt nur noch das Verhaeltnis. -->
    <div class="relative h-6 rounded bg-gray-200 dark:bg-gray-600 overflow-hidden">
      <div class="absolute inset-y-0 left-0 bg-emerald-600 dark:bg-emerald-500 rounded"
           :style="{ width: paidWidth }"></div>
    </div>
    <div class="mt-2 flex flex-wrap justify-between gap-x-4 gap-y-1 text-xs text-gray-500 dark:text-gray-400">
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
    <div v-if="timeline" class="mt-4 pt-4 border-t border-gray-200 dark:border-gray-600">
      <!-- Start- und Endjahr sitzen jetzt in dieser Kopfzeile (links Start, rechts Ziel) -
           spiegelt die Balkenenden und spart die separate Zeile unter dem Balken. -->
      <component :is="demo ? 'div' : 'button'" :type="demo ? undefined : 'button'"
              class="w-full flex justify-between items-baseline gap-3 text-xs tabular-nums text-left mb-2"
              :aria-label="demo ? undefined : t('savings.edit_investment')"
              @click="!demo && emit('edit-investment')">
        <span class="text-gray-500 dark:text-gray-400">
          {{ t('savings.wallbox_recovered') }}
          <span class="text-gray-400 dark:text-gray-500">· {{ timeline.startYear }}</span>
        </span>
        <span class="font-semibold text-gray-900 dark:text-gray-100">
          {{ money(view.recoveredEur ?? 0) }}
          <span class="font-normal text-gray-400 dark:text-gray-500">{{ t('savings.of_investment', { total: money(view.investmentEur!) }) }}</span>
          <span v-if="view.fullyAmortised" class="font-normal text-emerald-700 dark:text-emerald-300">· {{ t('savings.fully_amortised') }}</span>
          <span v-else-if="endLabel" class="font-normal text-gray-400 dark:text-gray-500">· {{ t('savings.paid_off_by', { date: endLabel }) }}</span>
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
    </div>

    <!-- Investition hinterlegt, aber keine Prognose moeglich: ohne Ersparnis amortisiert
         nichts. Der Betrag wird trotzdem gezeigt - sonst fragt die Kachel nach etwas,
         das laengst gespeichert ist. -->
    <div v-else-if="view.investmentEur != null"
         class="mt-5 pt-4 border-t border-gray-200 dark:border-gray-600">
      <component :is="demo ? 'div' : 'button'" :type="demo ? undefined : 'button'"
                 class="w-full flex justify-between items-baseline text-xs tabular-nums text-left"
                 :aria-label="demo ? undefined : t('savings.edit_investment')"
                 @click="!demo && emit('edit-investment')">
        <span class="text-gray-500 dark:text-gray-400">{{ t('savings.wallbox_recovered') }}</span>
        <span class="font-semibold text-gray-900 dark:text-gray-100">
          {{ money(view.investmentEur) }}
          <PencilSquareIcon v-if="!demo" class="ml-1.5 inline h-4 w-4 align-text-bottom text-gray-400 dark:text-gray-500" />
        </span>
      </component>
      <p class="mt-2 text-xs leading-relaxed text-gray-500 dark:text-gray-400">
        {{ t('savings.amortisation_not_possible') }}
      </p>
    </div>

    <!-- Ohne Investition: hier sammeln wir die Eingabe ein, die die Zeitschiene braucht -->
    <button v-else type="button"
            class="mt-5 pt-4 border-t border-gray-200 dark:border-gray-600 w-full flex items-start gap-2 text-left text-xs leading-relaxed text-emerald-700 dark:text-emerald-300"
            @click="emit('edit-investment')">
      <PlusIcon class="h-4 w-4 mt-px flex-none" />
      <span class="min-w-0">{{ t('savings.add_investment') }}</span>
      <ChevronRightIcon class="h-3 w-3 mt-0.5 ml-auto flex-none text-gray-400" />
    </button>

    <!-- Fusszeile in einer Zeile: Herkunft links, Rechenweg-Aufklapper rechts. Der ganze
         Kopf ist der Toggle (natives details: tastaturbedienbar, ohne JavaScript); die
         Erklaerung faellt darunter voll breit auf. -->
    <details class="group mt-4 pt-3 border-t border-gray-200 dark:border-gray-600 text-xs leading-relaxed text-gray-500 dark:text-gray-400">
      <summary class="flex flex-wrap items-center justify-between gap-x-4 gap-y-1 cursor-pointer select-none list-none [&::-webkit-details-marker]:hidden">
        <span class="flex items-center gap-1.5 min-w-0">
          <InformationCircleIcon class="h-3.5 w-3.5 flex-none" />
          <span>{{ basisLabel }}</span>
        </span>
        <span class="flex-none inline-flex items-center gap-1 font-medium">
          {{ t('savings.how_summary') }}
          <ChevronRightIcon class="h-3 w-3 transition-transform group-open:rotate-90" />
        </span>
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

    <!-- Retention-Hinweis: nur im Probemonat und nur fuer nicht-zahlende Nutzer. Sagt,
         womit die Kachel dauerhaft bleibt - das konkrete Ziel bestimmt das Dashboard. -->
    <div v-if="trial && !demo"
         class="mt-4 pt-4 border-t border-gray-200 dark:border-gray-600 flex flex-col sm:flex-row sm:items-center gap-3">
      <p class="flex items-start gap-2 flex-1 min-w-0 text-xs leading-relaxed text-gray-600 dark:text-gray-300">
        <ClockIcon class="h-4 w-4 flex-none mt-px text-amber-500 dark:text-amber-400" />
        <span>{{ trialEndLabel ? t('savings.trial_hint', { date: trialEndLabel }) : t('savings.trial_hint_generic') }}</span>
      </p>
      <router-link v-if="upsellTarget" :to="upsellTarget"
                   class="flex-none w-full sm:w-auto inline-flex items-center justify-center bg-amber-500 hover:bg-amber-600 dark:bg-amber-500 dark:hover:bg-amber-400 text-white font-semibold px-4 py-2 rounded-sm text-xs whitespace-nowrap shadow-[0_3px_0_0_#b45309] dark:shadow-[0_3px_0_0_#92400e] active:translate-y-0.5 active:shadow-none transition">
        {{ t('savings.trial_cta') }}
      </router-link>
    </div>
    </div>

    <!-- Ausblenden: fuer Nutzer, die zwar sparen, die Kachel aber nicht dauerhaft sehen
         wollen. Ausserhalb des einklappbaren Teils, damit sie auch mobil erreichbar ist,
         ohne die Kachel erst aufzuklappen. Dauerhaft wieder einblendbar in den Einstellungen. -->
    <div v-if="!demo" class="mt-4 pt-3 border-t border-gray-200 dark:border-gray-600 flex justify-end">
      <button type="button"
              class="inline-flex items-center gap-1 rounded px-2 py-1 text-xs text-gray-400 hover:text-gray-600 dark:text-gray-500 dark:hover:text-gray-300"
              @click="emit('dismiss')">
        <XMarkIcon class="h-3.5 w-3.5" />
        {{ t('savings.dismiss') }}
      </button>
    </div>
  </div>
</template>
