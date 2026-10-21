<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ChevronDownIcon, PlusIcon, TrashIcon, XMarkIcon } from '@heroicons/vue/24/outline'
import api from '../../api/axios'
import { fixedCostService, type FixedCost } from '../../api/fixedCostService'
import type { StatisticsData } from '../../composables/useDashboardStats'
import type { FixedCostMode } from '../../composables/useFixedCostCalc'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import { buildMonthlyCostSeries, centLabel, fixedNetFromSeries, groupFixedCostsByCategory, monthsCovered, summarizeCosts } from '../../utils/carCostSummary'
import { readLocal, writeLocal } from '../../utils/safeStorage'
import { isIncomeCategory } from '../../utils/fixedCostAmount'
import BottomSheet from '../shared/BottomSheet.vue'
import FixedCostForm from './FixedCostForm.vue'

/**
 * "Was es kostet": Vollkosten des Autos der letzten 12 Monate. Energiekosten liefert
 * `/logs/statistics`, Fixkosten kommen aus der Fixkostenliste und werden mit derselben
 * Monatsregel wie im Dashboard-Kostenverlauf verteilt (Schalter `cost_history_mode`).
 * Fixkosten werden hier gepflegt: Zeile tippen oeffnet Bearbeiten im Sheet, "Kosten
 * erfassen" legt an. Keine Stift- und Muelleimer-Icons je Zeile.
 */
const props = defineProps<{ carId: string; registrationDate: string | null }>()
const { t, n } = useI18n()
const { formatCurrency, currency } = useLocaleFormat()

const stats = ref<StatisticsData | null>(null)
const items = ref<FixedCost[]>([])
const loading = ref(true)
const error = ref(false)
// Umlagemodus, derselbe Schalter wie im Kostenverlauf des Dashboards (gemeinsamer Schluessel).
const LS_MODE = 'cost_history_mode'
const stored = readLocal(LS_MODE)
const mode = ref<FixedCostMode>(stored === 'due_month' ? 'due_month' : 'pro_rata')
function setMode(m: FixedCostMode) {
  mode.value = m
  writeLocal(LS_MODE, m)
}
const year = ref(new Date().getFullYear())
const openGroups = ref<Set<string>>(new Set())
const sheet = ref<{ edit: FixedCost | null } | null>(null)
const deleting = ref(false)

async function load() {
  loading.value = true
  error.value = false
  try {
    const [statsRes, list] = await Promise.all([
      api.get<StatisticsData>('/logs/statistics', { params: { carId: props.carId, timeRange: 'LAST_12_MONTHS', groupBy: 'MONTH' } }),
      fixedCostService.list(props.carId),
    ])
    stats.value = statsRes.data
    items.value = list
  } catch {
    error.value = true
  } finally {
    loading.value = false
  }
}
onMounted(load)
watch(() => props.carId, load)

const series = computed(() => buildMonthlyCostSeries(stats.value?.chargesOverTime ?? [], items.value, mode.value))
const months = computed(() => monthsCovered(series.value, props.registrationDate))
const summary = computed(() => stats.value ? summarizeCosts(stats.value, fixedNetFromSeries(series.value), months.value) : null)
const maxMonth = computed(() => Math.max(1, ...series.value.map(p => p.energyEur + Math.max(0, p.fixedEur))))
const groups = computed(() => groupFixedCostsByCategory(items.value, year.value))
const previousYearHasCosts = computed(() => groupFixedCostsByCategory(items.value, year.value - 1).length > 0)
const yearTotal = computed(() => groups.value.reduce((s, g) => s + g.yearTotalEur, 0))
const hasAnyCost = computed(() => (summary.value?.perMonthEur ?? 0) !== 0 || items.value.length > 0)

// Kopfzahl je km: in Euro-Laendern als Cent ("8 ct"), sonst in der Nutzerwaehrung.
const perKmLabel = computed(() => {
  const ct = summary.value?.ctPerKm
  if (ct == null) return null
  return currency.value === 'EUR' ? centLabel(ct, n) : formatCurrency(ct / 100)
})
const monthLabel = (p: { year: number; month: number }) =>
  new Intl.DateTimeFormat(undefined, { month: 'short', year: '2-digit' }).format(new Date(p.year, p.month - 1, 1))

function toggleGroup(category: string) {
  const next = new Set(openGroups.value)
  if (next.has(category)) next.delete(category)
  else next.add(category)
  openGroups.value = next
}

function itemDate(fc: FixedCost): string {
  const fmt = (d: string) => new Date(d).toLocaleDateString(undefined, { day: '2-digit', month: '2-digit', year: 'numeric' })
  if (fc.recurrence === 'ONE_TIME') return fc.date ? fmt(fc.date) : ''
  const since = fc.startDate ? t('cars.costs.since', { date: fmt(fc.startDate) }) : ''
  return fc.endDate ? `${since} - ${fmt(fc.endDate)}` : since
}

async function onSaved() {
  sheet.value = null
  await load()
}

async function removeCurrent() {
  const current = sheet.value?.edit
  if (!current || !confirm(t('fixed_costs.delete_confirm'))) return
  deleting.value = true
  try {
    await fixedCostService.remove(current.id)
    sheet.value = null
    await load()
  } finally {
    deleting.value = false
  }
}

const money = (v: number) => formatCurrency(v, { decimals: 0 })
/** Geldbetrag ueber dem Monatsbalken: "304 €", ab 1.000 "1,2k €", leer bei 0 (12 Spalten auf 390 px). */
const shortMoney = (v: number) => {
  if (Math.round(v) === 0) return ''
  if (Math.abs(v) >= 1000) return money(v).replace(/[\d.,]+/, n(v / 1000, { maximumFractionDigits: 1 }) + 'k')
  return money(Math.round(v))
}
</script>

<template>
  <section class="rounded-sm bg-white dark:bg-gray-700 border-2 border-gray-300 dark:border-gray-600 shadow-[2px_2px_0_0_#d1d5db] dark:shadow-[2px_2px_0_0_#374151] p-4 md:p-5" data-testid="car-cost-card">
    <div class="flex items-baseline justify-between gap-3">
      <h2 class="text-lg font-bold text-gray-900 dark:text-gray-100">{{ t('cars.costs.title') }}</h2>
      <span class="text-xs text-gray-500 dark:text-gray-400">{{ months < 12 ? t('cars.costs.range_months', { n: months }, months) : t('cars.costs.range') }}</span>
    </div>

    <p v-if="error" class="mt-3 text-sm text-red-600 dark:text-red-400">{{ t('common.error_load') }}</p>
    <div v-else-if="loading" class="mt-4 h-24 animate-pulse rounded-sm bg-gray-100 dark:bg-gray-600" aria-hidden="true" />

    <template v-else-if="summary">
      <!-- Kopfzahl -->
      <div v-if="hasAnyCost" class="mt-3">
        <p class="flex items-baseline gap-2">
          <span class="text-[40px] md:text-[48px] font-extrabold leading-none tracking-tight tabular-nums text-gray-900 dark:text-gray-100" data-testid="car-cost-headline">
            {{ perKmLabel ?? money(summary.perMonthEur) }}
          </span>
          <span class="text-base font-semibold text-gray-500 dark:text-gray-400">{{ perKmLabel !== null ? t('cars.costs.per_km') : t('cars.costs.per_month') }}</span>
        </p>
        <p class="mt-2 text-sm text-gray-700 dark:text-gray-300">
          <template v-if="summary.fixedPerMonthEur !== 0 && summary.energyPerMonthEur !== 0">
            {{ t('cars.costs.sentence', { total: money(summary.perMonthEur), energy: money(summary.energyPerMonthEur), fixed: money(summary.fixedPerMonthEur) }) }}
          </template>
          <template v-else-if="summary.fixedPerMonthEur !== 0">{{ t('cars.costs.sentence_fixed_only', { total: money(summary.perMonthEur) }) }}</template>
          <template v-else>{{ t('cars.costs.sentence_energy_only', { total: money(summary.perMonthEur) }) }}</template>
        </p>

        <!-- Anteil Strom / Fixkosten -->
        <div v-if="summary.energyShare !== null && summary.fixedPerMonthEur > 0" class="mt-3">
          <div class="flex h-2 overflow-hidden rounded-full bg-gray-200 dark:bg-gray-600" aria-hidden="true">
            <div class="bg-indigo-500 transition-[width] duration-500 ease-out motion-reduce:transition-none" :style="{ width: `${summary.energyShare * 100}%` }" />
            <div class="bg-amber-400 flex-1" />
          </div>
          <div class="mt-1.5 flex gap-4 text-xs text-gray-500 dark:text-gray-400">
            <span><span class="mr-1.5 inline-block h-2.5 w-2.5 rounded-sm bg-indigo-500 align-middle" />{{ t('cars.costs.energy') }}</span>
            <span><span class="mr-1.5 inline-block h-2.5 w-2.5 rounded-sm bg-amber-400 align-middle" />{{ t('cars.costs.fixed') }}</span>
          </div>
        </div>

        <!-- 12 Monate -->
        <div class="mt-4 flex items-end gap-1" role="img" :aria-label="t('cars.costs.chart_aria')">
          <div v-for="p in series" :key="`${p.year}-${p.month}`" class="flex flex-1 min-w-0 flex-col justify-end overflow-visible" :title="`${monthLabel(p)}: ${money(p.energyEur + p.fixedEur)}`">
            <span class="mb-0.5 -mx-1 block text-center text-[9px] md:text-[10px] tracking-tight leading-none tabular-nums whitespace-nowrap text-gray-500 dark:text-gray-400">{{ shortMoney(p.energyEur + p.fixedEur) }}</span>
            <div class="flex h-14 flex-col justify-end gap-px">
              <div class="w-full rounded-t-sm bg-amber-400/90 transition-[height] duration-500 ease-out motion-reduce:transition-none" :style="{ height: `${(Math.max(0, p.fixedEur) / maxMonth) * 100}%` }" />
              <div class="w-full bg-indigo-500 transition-[height] duration-500 ease-out motion-reduce:transition-none" :style="{ height: `${(p.energyEur / maxMonth) * 100}%` }" />
            </div>
          </div>
        </div>
        <div class="mt-1 flex justify-between text-[11px] text-gray-500 dark:text-gray-400">
          <span>{{ monthLabel(series[0]) }}</span><span>{{ monthLabel(series[11]) }}</span>
        </div>
      </div>
      <p v-else class="mt-3 text-sm text-gray-600 dark:text-gray-300">{{ t('cars.costs.empty_all') }}</p>

      <!-- Fixkosten nach Kategorie -->
      <div class="mt-5 border-t border-gray-200 dark:border-gray-600 pt-4">
        <div class="flex items-center justify-between gap-3">
          <h3 class="text-sm font-semibold text-gray-900 dark:text-gray-100 whitespace-nowrap">
            {{ t('cars.costs.fixed_year', { year }) }}<span v-if="groups.length" class="ml-2 tabular-nums">{{ money(yearTotal) }}</span>
          </h3>
          <!-- Umlage: anteilig je Monat oder im Faelligkeitsmonat, gilt fuer Kopfzahl, Satz und Balken -->
          <label v-if="items.length" class="flex shrink-0 items-center gap-2 cursor-pointer" :title="t('cost_history.mode_spread_label')">
            <span class="text-[11px] leading-none text-gray-500 dark:text-gray-400 select-none whitespace-nowrap">{{ t('cars.costs.spread_short') }}</span>
            <button type="button" role="switch" :aria-checked="mode === 'pro_rata'" :aria-label="t('cost_history.mode_spread_label')"
              class="relative inline-flex h-5 w-9 shrink-0 rounded-full border-2 border-transparent transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-1 focus-visible:ring-offset-white dark:focus-visible:ring-offset-gray-700"
              :class="mode === 'pro_rata' ? 'bg-indigo-500' : 'bg-gray-300 dark:bg-gray-600'"
              @click="setMode(mode === 'pro_rata' ? 'due_month' : 'pro_rata')">
              <span aria-hidden="true" class="pointer-events-none inline-block h-4 w-4 transform rounded-full bg-white shadow transition" :class="mode === 'pro_rata' ? 'translate-x-4' : 'translate-x-0'" />
            </button>
          </label>
        </div>

        <ul v-if="groups.length" class="mt-2 divide-y divide-gray-100 dark:divide-gray-600">
          <li v-for="g in groups" :key="g.category">
            <button type="button" class="flex w-full items-center gap-2 min-h-11 py-2 text-left" :aria-expanded="openGroups.has(g.category)" @click="toggleGroup(g.category)">
              <ChevronDownIcon class="h-4 w-4 shrink-0 text-gray-400 transition-transform" :class="openGroups.has(g.category) ? 'rotate-180' : ''" aria-hidden="true" />
              <span class="font-medium text-gray-900 dark:text-gray-100">{{ t(`fixed_costs.category_${g.category}`) }}</span>
              <span v-if="g.items.length > 1" class="text-xs text-gray-500 dark:text-gray-400">{{ t('cars.costs.entries', { n: g.items.length }, g.items.length) }}</span>
              <span class="ml-auto tabular-nums font-semibold" :class="isIncomeCategory(g.category) ? 'text-green-700 dark:text-green-400' : 'text-gray-900 dark:text-gray-100'">{{ money(g.yearTotalEur) }}</span>
            </button>
            <ul v-if="openGroups.has(g.category)" class="pb-2 pl-6">
              <li v-for="fc in g.items" :key="fc.id">
                <button type="button" class="flex w-full items-center gap-2 min-h-11 py-1.5 text-left text-sm rounded-sm hover:bg-gray-50 dark:hover:bg-gray-600/40" @click="sheet = { edit: fc }">
                  <span class="min-w-0 flex-1">
                    <span class="block truncate text-gray-800 dark:text-gray-200">{{ fc.description }}</span>
                    <span class="block text-xs text-gray-500 dark:text-gray-400">{{ t(`fixed_costs.recurrence_${fc.recurrence}`) }}<template v-if="itemDate(fc)"> · {{ itemDate(fc) }}</template></span>
                  </span>
                  <span class="tabular-nums text-gray-700 dark:text-gray-300">{{ formatCurrency(Math.abs(fc.amount)) }}</span>
                </button>
              </li>
            </ul>
          </li>
        </ul>
        <p v-else class="mt-2 text-sm text-gray-600 dark:text-gray-300">{{ t('cars.costs.empty_fixed') }}</p>

        <div class="mt-3 flex flex-wrap items-center gap-3">
          <button type="button" v-haptic class="btn-3d inline-flex items-center gap-1.5 min-h-11 rounded-sm bg-indigo-600 px-4 text-sm font-semibold text-white hover:bg-indigo-700 transition" data-testid="car-cost-add" @click="sheet = { edit: null }">
            <PlusIcon class="h-4 w-4" aria-hidden="true" />{{ t('cars.costs.add') }}
          </button>
          <button v-if="previousYearHasCosts || year !== new Date().getFullYear()" type="button" class="min-h-11 text-sm font-medium text-indigo-600 dark:text-indigo-400"
            @click="year = year === new Date().getFullYear() ? year - 1 : new Date().getFullYear()">
            {{ t('cars.costs.show_year', { year: year === new Date().getFullYear() ? year - 1 : new Date().getFullYear() }) }}
          </button>
        </div>
      </div>
    </template>

    <!-- Erfassen und Bearbeiten im Sheet -->
    <BottomSheet v-if="sheet" :label="sheet.edit ? t('cars.costs.edit_title') : t('cars.costs.add')" testid="fixed-cost-sheet" @close="sheet = null">
      <template #default="{ close }">
        <div class="flex items-center justify-between px-4 pt-4 pb-2">
          <h2 class="text-base font-semibold text-gray-900 dark:text-gray-100">{{ sheet.edit ? t('cars.costs.edit_title') : t('cars.costs.add') }}</h2>
          <button type="button" @click="close" :aria-label="t('common.close')"
            class="-mr-2 p-2.5 min-h-[44px] min-w-[44px] flex items-center justify-center text-gray-500 hover:text-gray-800 dark:hover:text-gray-200">
            <XMarkIcon class="w-5 h-5" aria-hidden="true" />
          </button>
        </div>
        <div class="flex-1 overflow-y-auto px-4 pb-4">
          <FixedCostForm :car-id="carId" :edit="sheet.edit" @saved="onSaved" @cancel="close" />
          <button v-if="sheet.edit" type="button" :disabled="deleting"
            class="mt-4 inline-flex items-center gap-1.5 min-h-11 text-sm font-medium text-red-600 dark:text-red-400 disabled:opacity-60"
            @click="removeCurrent">
            <TrashIcon class="h-4 w-4" aria-hidden="true" />{{ t('fixed_costs.delete') }}
          </button>
        </div>
      </template>
    </BottomSheet>
  </section>
</template>
