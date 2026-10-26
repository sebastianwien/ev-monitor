<script setup lang="ts">
import { computed, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { useI18n } from 'vue-i18n'
import { ArrowRightIcon, ChevronDownIcon, ChevronLeftIcon, ChevronRightIcon, PlusIcon, TrashIcon, XMarkIcon } from '@heroicons/vue/24/outline'
import { fixedCostService, type FixedCost } from '../../api/fixedCostService'
import { ENERGY, useYearCosts } from '../../composables/useYearCosts'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import { costPer100km, groupFixedCostsByCategory, summarizeCosts } from '../../utils/carCostSummary'
import { isIncomeCategory } from '../../utils/fixedCostAmount'
import { costColor } from '../../utils/costColors'
import { useThemeStore } from '../../stores/theme'
import { KM_PER_MILE } from '../../utils/unitConversions'
import BottomSheet from '../shared/BottomSheet.vue'
import ToggleSwitch from '../shared/ToggleSwitch.vue'
import FixedCostForm from './FixedCostForm.vue'

/**
 * "Was es kostet": was das Auto im Kalenderjahr kostet, mit Pfeilen durch die Jahre. Kopfzahl
 * und Satz rechnen Fixkosten immer umgelegt. Jede Kategorie (auch Strom) laesst sich aus- und
 * einrechnen, gemeinsam mit dem Kostenverlauf im Dashboard, der den Monatsverlauf zeigt.
 * Fixkosten werden hier gepflegt: Zeile tippen oeffnet Bearbeiten im Sheet, "Kosten erfassen"
 * legt an.
 */
const props = defineProps<{ carId: string; registrationDate: string | null; showHistoryLink: boolean }>()
const { t } = useI18n()
const { formatCurrency, isImperial } = useLocaleFormat()

const { ranked, thisYear, year, minYear, points, distance, items, loading, error, load, hidden, setShown, proRataSeries } =
  useYearCosts(() => props.carId, () => props.registrationDate)
const openGroups = ref<Set<string>>(new Set())
const sheet = ref<{ edit: FixedCost | null } | null>(null)
const deleting = ref(false)

const { isDark } = storeToRefs(useThemeStore())
const colorOf = (key: string) => costColor(key, ranked.value, isDark.value, ENERGY)
const summary = computed(() => summarizeCosts(proRataSeries.value))
const per100km = computed(() => (distance.value ? costPer100km(distance.value, proRataSeries.value, !hidden.value.has(ENERGY)) : null))
// Je 100 km (oder 100 mi), ab 10 in ganzen Waehrungseinheiten, darunter eine Nachkommastelle.
const per100Label = computed(() => {
  if (per100km.value === null) return null
  const v = isImperial.value ? per100km.value * KM_PER_MILE : per100km.value
  return formatCurrency(v, { decimals: Math.abs(v) < 10 ? 1 : 0 })
})
// Anteilsbalken: Strom und jede eingerechnete Kostenkategorie, Farben wie im Dashboard-Kostenverlauf.
const shares = computed(() => {
  const sums = new Map<string, number>([[ENERGY, 0]])
  for (const p of proRataSeries.value) {
    sums.set(ENERGY, sums.get(ENERGY)! + p.energyEur)
    for (const [c, v] of Object.entries(p.fixedByCategory)) sums.set(c, (sums.get(c) ?? 0) + v)
  }
  const order = [ENERGY, ...ranked.value]
  const parts = order.map(key => ({ key, eur: sums.get(key) ?? 0 })).filter(x => x.eur > 0)
  const total = parts.reduce((s, x) => s + x.eur, 0)
  return parts.map(x => ({ key: x.key, pct: (x.eur / total) * 100 }))
})
const groups = computed(() => groupFixedCostsByCategory(items.value, year.value))
const energyYear = computed(() => points.value.reduce((s, p) => s + (p.costEur ?? 0), 0))
const yearTotal = computed(() => (hidden.value.has(ENERGY) ? 0 : energyYear.value)
  + groups.value.filter(g => !hidden.value.has(g.category)).reduce((s, g) => s + g.yearTotalEur, 0))

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
const navBtn = 'inline-flex h-11 w-11 items-center justify-center rounded-xl text-gray-600 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-white/10 disabled:opacity-30 disabled:hover:bg-transparent'
</script>

<template>
  <section class="rounded-xl bg-white edge-card p-4 md:p-5 dark:bg-gray-700/50" data-testid="car-cost-card">
    <div class="flex items-center justify-between gap-3">
      <h2 class="text-lg font-bold text-gray-900 dark:text-gray-100">{{ t('cars.costs.title') }}</h2>
      <!-- Kalenderjahr waehlen -->
      <div class="-mr-2 flex items-center">
        <button type="button" :class="navBtn" :disabled="year <= minYear" :aria-label="t('cars.costs.year_prev')" data-testid="car-cost-year-prev" @click="year--">
          <ChevronLeftIcon class="h-5 w-5" aria-hidden="true" />
        </button>
        <span class="min-w-[3rem] text-center text-sm font-semibold tabular-nums text-gray-900 dark:text-gray-100" aria-live="polite" data-testid="car-cost-year">{{ year }}</span>
        <button type="button" :class="navBtn" :disabled="year >= thisYear" :aria-label="t('cars.costs.year_next')" data-testid="car-cost-year-next" @click="year++">
          <ChevronRightIcon class="h-5 w-5" aria-hidden="true" />
        </button>
      </div>
    </div>

    <p v-if="error" class="mt-3 text-sm text-red-600 dark:text-red-400">{{ t('common.error_load') }}</p>
    <div v-else-if="loading" class="mt-4 h-24 animate-pulse rounded-xl bg-gray-100 dark:bg-gray-600" aria-hidden="true" />

    <template v-else>
      <!-- Kopfzahl -->
      <div v-if="proRataSeries.length" class="mt-2">
        <p class="flex flex-wrap items-baseline gap-x-2 gap-y-1">
          <span class="text-[36px] font-extrabold leading-none tracking-tight tabular-nums text-gray-900 dark:text-gray-100" data-testid="car-cost-headline">{{ money(summary.perMonthEur) }}</span>
          <span class="text-base font-semibold text-gray-500 dark:text-gray-400">{{ t('cars.costs.per_month') }}</span>
          <span v-if="per100Label" class="inline-flex items-baseline gap-1.5 whitespace-nowrap text-base font-semibold text-gray-500 dark:text-gray-400" data-testid="car-cost-per-distance">
            {{ t('cars.costs.or') }}
            <span class="text-2xl md:text-[28px] font-extrabold tracking-tight tabular-nums text-gray-900 dark:text-gray-100">{{ per100Label }}</span>
            {{ t('cars.costs.per_100', { unit: isImperial ? 'mi' : 'km' }) }}
          </span>
        </p>
        <p class="mt-2 text-sm text-gray-700 dark:text-gray-300">
          <template v-if="summary.fixedPerMonthEur !== 0 && summary.energyPerMonthEur !== 0">
            {{ t('cars.costs.sentence', { total: money(summary.perMonthEur), energy: money(summary.energyPerMonthEur), fixed: money(summary.fixedPerMonthEur) }) }}
          </template>
          <template v-else-if="summary.fixedPerMonthEur !== 0">{{ t('cars.costs.sentence_fixed_only', { total: money(summary.perMonthEur) }) }}</template>
          <template v-else>{{ t('cars.costs.sentence_energy_only', { total: money(summary.perMonthEur) }) }}</template>
        </p>

        <!-- Anteile je Kategorie, Legende sind die Farbpunkte in der Liste darunter -->
        <div v-if="shares.length > 1" class="mt-3 flex h-2 overflow-hidden rounded-full bg-gray-200 dark:bg-gray-600" aria-hidden="true">
          <div v-for="x in shares" :key="x.key" class="h-full border-r border-white last:border-r-0 dark:border-gray-700" :style="{ width: `${x.pct}%`, background: colorOf(x.key) }" />
        </div>

        <RouterLink v-if="showHistoryLink" to="/dashboard" class="mt-3 inline-flex items-center gap-1.5 min-h-11 text-sm font-semibold text-gray-900 underline underline-offset-2 dark:text-gray-100" data-testid="car-cost-history-link">
          {{ t('cars.costs.history_link') }}<ArrowRightIcon class="h-4 w-4" aria-hidden="true" />
        </RouterLink>
      </div>
      <p v-else class="mt-3 text-sm text-gray-600 dark:text-gray-300">{{ year === thisYear ? t('cars.costs.empty_all') : t('cars.costs.empty_year', { year }) }}</p>

      <!-- Kosten nach Kategorie, jede ein- und ausrechenbar -->
      <div class="mt-5 border-t border-gray-200 dark:border-gray-600 pt-4">
        <h3 class="flex items-baseline justify-between gap-3 text-sm font-semibold text-gray-900 dark:text-gray-100">
          {{ t('cars.costs.by_category', { year }) }}<span class="tabular-nums">{{ money(yearTotal) }}</span>
        </h3>

        <ul class="mt-2 divide-y divide-gray-100 dark:divide-gray-600">
          <li v-if="energyYear !== 0" class="flex items-center gap-3 min-h-11 py-2">
            <span class="h-4 w-4 shrink-0" aria-hidden="true" />
            <span class="h-2.5 w-2.5 shrink-0 rounded-sm" :style="{ background: hidden.has(ENERGY) ? 'transparent' : colorOf(ENERGY), boxShadow: `inset 0 0 0 1.5px ${colorOf(ENERGY)}` }" aria-hidden="true" />
            <span class="font-medium" :class="hidden.has(ENERGY) ? 'text-gray-400 dark:text-gray-500' : 'text-gray-900 dark:text-gray-100'">{{ t('cars.costs.energy') }}</span>
            <span class="ml-auto tabular-nums font-semibold" :class="hidden.has(ENERGY) ? 'text-gray-400 dark:text-gray-500 line-through' : 'text-gray-900 dark:text-gray-100'">{{ money(energyYear) }}</span>
            <ToggleSwitch :model-value="!hidden.has(ENERGY)" :label="t('cars.costs.include', { category: t('cars.costs.energy') })" @update:model-value="setShown(ENERGY, $event)" />
          </li>
          <li v-for="g in groups" :key="g.category">
            <div class="flex items-center gap-3">
              <button type="button" class="flex min-w-0 flex-1 items-center gap-3 min-h-11 py-2 text-left" :aria-expanded="openGroups.has(g.category)" @click="toggleGroup(g.category)">
                <ChevronDownIcon class="h-4 w-4 shrink-0 text-gray-400 transition-transform" :class="openGroups.has(g.category) ? 'rotate-180' : ''" aria-hidden="true" />
                <span class="h-2.5 w-2.5 shrink-0 rounded-sm" :style="isIncomeCategory(g.category) ? {} : { background: hidden.has(g.category) ? 'transparent' : colorOf(g.category), boxShadow: `inset 0 0 0 1.5px ${colorOf(g.category)}` }" aria-hidden="true" />
                <span class="truncate font-medium" :class="hidden.has(g.category) ? 'text-gray-400 dark:text-gray-500' : 'text-gray-900 dark:text-gray-100'">{{ t(`fixed_costs.category_${g.category}`) }}</span>
                <span v-if="g.items.length > 1" class="shrink-0 text-xs text-gray-500 dark:text-gray-400">{{ t('cars.costs.entries', { n: g.items.length }, g.items.length) }}</span>
                <span class="ml-auto tabular-nums font-semibold"
                  :class="hidden.has(g.category) ? 'text-gray-400 dark:text-gray-500 line-through' : isIncomeCategory(g.category) ? 'text-green-700 dark:text-green-400' : 'text-gray-900 dark:text-gray-100'">{{ money(g.yearTotalEur) }}</span>
              </button>
              <ToggleSwitch :model-value="!hidden.has(g.category)" :label="t('cars.costs.include', { category: t(`fixed_costs.category_${g.category}`) })" @update:model-value="setShown(g.category, $event)" />
            </div>
            <ul v-if="openGroups.has(g.category)" class="pb-2 pl-7">
              <li v-for="fc in g.items" :key="fc.id">
                <button type="button" class="flex w-full items-center gap-2 min-h-11 py-1.5 text-left text-sm rounded-lg hover:bg-gray-50 dark:hover:bg-white/5" @click="sheet = { edit: fc }">
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
        <p v-if="!groups.length" class="mt-2 text-sm text-gray-600 dark:text-gray-300">{{ t('cars.costs.empty_fixed') }}</p>

        <button type="button" v-haptic class="btn-3d mt-3 inline-flex items-center gap-1.5 min-h-11 rounded-xl border-2 border-edge bg-white px-4 text-sm font-semibold text-gray-900 hover:bg-gray-50 dark:bg-white/10 dark:text-gray-100 dark:hover:bg-white/15" data-testid="car-cost-add" @click="sheet = { edit: null }">
          <PlusIcon class="h-4 w-4" aria-hidden="true" />{{ t('cars.costs.add') }}
        </button>
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
