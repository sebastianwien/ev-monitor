<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { storeToRefs } from 'pinia'
import { CheckIcon, ChevronLeftIcon, ChevronRightIcon } from '@heroicons/vue/24/outline'
import type { FixedCostCategory } from '../../api/fixedCostService'
import { ENERGY, useYearCosts } from '../../composables/useYearCosts'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import { useThemeStore } from '../../stores/theme'
import { barScale, fitSegments, legendKeys, type MonthlyCostPoint } from '../../utils/carCostSummary'
import { costColor } from '../../utils/costColors'
import ToggleSwitch from '../shared/ToggleSwitch.vue'
import YearSlide from '../shared/YearSlide.vue'

/**
 * Kostenverlauf im Dashboard: ein Monat pro Zeile im Kalenderjahr, Strom in Indigo, Fixkosten
 * in Amber-Abstufungen (groesste Kategorie am dunkelsten). Zeile antippen klappt die Betraege
 * je Kategorie auf. Unter den Balken stehen die Kategorien als Filter-Chips (zugleich Legende).
 * Mit der Maus hebt Hover auf Balkenteil oder Legende eine Kategorie hervor
 * und schreibt ihren Betrag in jeden Balken, damit das Auge nicht zur Summenspalte wandert. Ein Tipp auf die Legende rechnet die
 * Kategorie aus und ein, gemeinsam mit der Kostenkarte der Fahrzeugseite.
 */
const props = defineProps<{ carId: string | null; registrationDate: string | null }>()
const { t, locale } = useI18n()
const { formatCurrency } = useLocaleFormat()
const { isDark } = storeToRefs(useThemeStore())

const { ranked, thisYear, year, dataYear, minYear, items, loading, error, spread, hidden, setShown, series, allSeries } =
  useYearCosts(() => props.carId, () => props.registrationDate)

const colorOf = (key: string) => costColor(key, ranked.value, isDark.value, ENERGY)
const labelOf = (key: string) => (key === ENERGY ? t('cars.costs.energy') : t(`fixed_costs.category_${key}`))
const hasEnergy = computed(() => allSeries.value.some(p => p.energyEur !== 0))
const legend = computed(() => legendKeys({ hasEnergy: hasEnergy.value, ranked: ranked.value, hidden: hidden.value, carCategories: items.value.map(fc => fc.category) }))

/** Betraege eines Monats je Kategorie in Stapelreihenfolge (Strom zuerst), Einnahmen am Ende. */
function parts(p: MonthlyCostPoint): { key: string; eur: number }[] {
  const list = [{ key: ENERGY, eur: p.energyEur }, ...ranked.value.map(c => ({ key: c as string, eur: p.fixedByCategory[c] ?? 0 }))]
  for (const [c, v] of Object.entries(p.fixedByCategory)) if (!ranked.value.includes(c as FixedCostCategory)) list.push({ key: c, eur: v })
  return list.filter(x => Math.round(x.eur) !== 0)
}

const open = ref<number | null>(null)
const focus = ref<string | null>(null)
watch([year, () => props.carId], () => { open.value = null })

// Balken: positive Teile gestapelt. Ausreisser werden im groessten Teilstueck mit Achsenbruch
// gekuerzt, der gekuerzte Teil belegt dabei mindestens 60 Prozent des Balkens.
const rows = computed(() => {
  const cap = barScale(series.value.map(p => parts(p).filter(x => x.eur > 0).map(x => x.eur)))
  return series.value.map(p => {
    const positive = parts(p).filter(x => x.eur > 0)
    const { widths, cut } = fitSegments(positive.map(x => x.eur), cap)
    let used = 0
    const segs = positive.map((x, i) => {
      const left = (used / cap) * 100
      used += widths[i]
      return { key: x.key, eur: x.eur, left, pct: (widths[i] / cap) * 100, cut: i === cut }
    })
    return { p, parts: parts(p), segs, total: p.energyEur + p.fixedEur }
  })
})
const monthFmt = (p: MonthlyCostPoint, o: Intl.DateTimeFormatOptions) =>
  new Intl.DateTimeFormat(locale.value, o).format(new Date(p.year, p.month - 1, 1))
const money = (v: number) => formatCurrency(v, { decimals: 0 })
const onHover = (e: PointerEvent, key: string | null) => { if (e.pointerType === 'mouse') focus.value = key }
const navBtn = 'inline-flex h-11 w-11 items-center justify-center rounded-sm text-gray-600 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-600 disabled:opacity-30 disabled:hover:bg-transparent'
</script>

<template>
  <section class="bg-white dark:bg-gray-700 rounded-sm border-2 border-gray-300 dark:border-gray-600 shadow-[2px_2px_0_0_#d1d5db] dark:shadow-[2px_2px_0_0_#374151] p-4" data-testid="cost-history-card" style="overflow-x: clip">
    <!-- Kopf zentriert: Jahr mit Pfeilen links und rechts der Ueberschrift -->
    <div class="-my-2 flex items-center justify-center">
      <button type="button" :class="navBtn" :disabled="year <= minYear" :aria-label="t('cars.costs.year_prev')" @click="year--">
        <ChevronLeftIcon class="h-5 w-5" aria-hidden="true" />
      </button>
      <h3 class="px-1 text-base font-semibold text-gray-800 dark:text-gray-100">
        {{ t('cost_history.title') }} <span class="tabular-nums" aria-live="polite">{{ year }}</span>
      </h3>
      <button type="button" :class="navBtn" :disabled="year >= thisYear" :aria-label="t('cars.costs.year_next')" @click="year++">
        <ChevronRightIcon class="h-5 w-5" aria-hidden="true" />
      </button>
    </div>

    <p v-if="error" class="mt-3 text-sm text-red-600 dark:text-red-400">{{ t('common.error_load') }}</p>
    <div v-else-if="loading" class="mt-3 h-40 animate-pulse rounded-sm bg-gray-100 dark:bg-gray-600" aria-hidden="true" />
    <p v-else-if="!allSeries.length" class="py-6 text-center text-xs text-gray-500 dark:text-gray-400">{{ t('cost_history.empty') }}</p>

    <!-- Jahreswechsel: Inhalt gleitet in Richtung des Pfeils, die Balken kommen fertig gezeichnet mit -->
    <YearSlide v-else :year="dataYear">
      <ul class="mt-2" :aria-label="t('cars.costs.chart_aria', { year })" @pointerleave="focus = null">
        <li v-for="(r, i) in rows" :key="r.p.month" :class="open === i ? '-mx-2 rounded-sm bg-gray-50 px-2 dark:bg-gray-600/40' : ''">
          <button type="button" class="grid w-full grid-cols-[2.25rem_1fr_4.75rem] items-center gap-2 py-1.5 min-h-9 text-left text-sm" :aria-expanded="open === i" data-testid="cost-history-month" @click="open = open === i ? null : i">
            <span :class="open === i ? 'font-semibold text-gray-900 dark:text-gray-100' : 'text-gray-500 dark:text-gray-400'">
              <span aria-hidden="true">{{ monthFmt(r.p, { month: 'short' }).replace(/\.$/, '') }}</span><span class="sr-only">{{ monthFmt(r.p, { month: 'long' }) }}</span>
            </span>
            <span class="relative flex h-3.5 md:h-5 overflow-hidden rounded-sm bg-gray-100 dark:bg-gray-600/60" aria-hidden="true">
              <span v-for="s in r.segs" :key="s.key" class="h-full border-r border-white last:border-r-0 dark:border-gray-700 transition-opacity duration-150"
                :style="{ width: `${s.pct}%`, background: colorOf(s.key), opacity: focus && focus !== s.key ? 0.2 : 1 }"
                @pointerenter="onHover($event, s.key)" />
              <!-- Achsenbruch im gekuerzten Teilstueck, die Betraege stimmen -->
              <template v-for="s in r.segs" :key="`b-${s.key}`">
                <span v-if="s.cut" class="absolute -inset-y-0.5 w-1.5 -skew-x-12 bg-white dark:bg-gray-700" :style="{ left: `calc(${s.left + s.pct / 2}% - 3px)` }" />
              </template>
              <!-- Hover: Betrag der hervorgehobenen Kategorie am Anfang ihres Balkenteils -->
              <template v-for="s in r.segs" :key="`l-${s.key}`">
                <span v-if="focus === s.key" class="pointer-events-none absolute top-1/2 -translate-y-1/2 ml-0.5 rounded-sm bg-white/90 px-1 text-[11px] font-semibold leading-4 tabular-nums text-gray-900 dark:bg-gray-900/80 dark:text-gray-100" :style="{ left: `${s.left}%` }">{{ money(s.eur) }}</span>
              </template>
            </span>
            <span class="text-right tabular-nums" :class="Math.round(r.total) === 0 ? 'text-gray-400 dark:text-gray-500' : 'font-medium text-gray-900 dark:text-gray-100'">{{ money(r.total) }}</span>
          </button>
          <dl v-if="open === i" class="max-w-[17rem] space-y-1 pb-2 pl-[2.75rem] text-sm box-content">
            <div v-for="x in r.parts" :key="x.key" class="flex items-center gap-2">
              <span class="inline-block h-2.5 w-2.5 rounded-sm" :style="{ background: x.eur > 0 ? colorOf(x.key) : 'transparent' }" aria-hidden="true" />
              <dt class="text-gray-600 dark:text-gray-300">{{ labelOf(x.key) }}</dt>
              <dd class="ml-auto tabular-nums" :class="x.eur < 0 ? 'text-green-700 dark:text-green-400' : 'text-gray-900 dark:text-gray-100'">{{ money(x.eur) }}</dd>
            </div>
            <div class="flex items-center gap-2 border-t border-gray-200 pt-1 dark:border-gray-600">
              <dt class="font-semibold text-gray-900 dark:text-gray-100">{{ monthFmt(r.p, { month: 'long' }) }}</dt>
              <dd class="ml-auto font-semibold tabular-nums text-gray-900 dark:text-gray-100">{{ money(r.total) }}</dd>
            </div>
          </dl>
        </li>
      </ul>

      <!-- Kategorien als Filter-Chips unter den Balken: zugleich Legende. Tipp rechnet aus und ein, Hover hebt hervor -->
      <div class="mt-3 flex flex-wrap items-center gap-2" @pointerleave="focus = null">
        <button v-for="key in legend" :key="key" type="button" :aria-pressed="!hidden.has(key)"
          class="relative inline-flex min-h-9 items-center gap-1.5 rounded-full border px-3 text-xs font-medium transition-colors after:absolute after:-inset-1 after:content-[''] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500"
          :class="hidden.has(key) ? 'border-gray-300 text-gray-500 dark:border-gray-500 dark:text-gray-400' : focus && focus !== key ? 'border-transparent text-gray-400 dark:text-gray-500' : 'border-transparent text-gray-800 dark:text-gray-100'"
          :style="hidden.has(key) ? undefined : { background: `${colorOf(key)}26` }"
          :aria-label="t('cars.costs.include', { category: labelOf(key) })"
          @pointerenter="onHover($event, hidden.has(key) ? null : key)" @click="setShown(key, hidden.has(key))">
          <span class="inline-block h-2.5 w-2.5 rounded-full" :style="{ background: hidden.has(key) ? 'transparent' : colorOf(key), boxShadow: `inset 0 0 0 1.5px ${colorOf(key)}` }" aria-hidden="true" />{{ labelOf(key) }}
          <CheckIcon v-if="!hidden.has(key)" class="-mr-0.5 h-3.5 w-3.5" aria-hidden="true" />
        </button>
        <label class="ml-auto flex items-center gap-2 cursor-pointer">
          <span class="text-xs text-gray-500 dark:text-gray-400 select-none whitespace-nowrap">{{ t('cars.costs.spread_short') }}</span>
          <ToggleSwitch v-model="spread" :label="t('cost_history.mode_spread_label')" />
        </label>
      </div>
    </YearSlide>
  </section>
</template>
