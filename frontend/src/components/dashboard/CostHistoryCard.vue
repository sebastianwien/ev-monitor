<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { storeToRefs } from 'pinia'
import { CheckIcon, ChevronLeftIcon, ChevronRightIcon } from '@heroicons/vue/24/outline'
import { ENERGY, useYearCosts } from '../../composables/useYearCosts'
import { useThemeStore } from '../../stores/theme'
import { legendKeys } from '../../utils/carCostSummary'
import { costColor } from '../../utils/costColors'
import ToggleSwitch from '../shared/ToggleSwitch.vue'
import YearSlide from '../shared/YearSlide.vue'
import CostMonthBars from '../shared/CostMonthBars.vue'

/**
 * Kostenverlauf im Dashboard: ein Monat pro Zeile im Kalenderjahr, Strom in Indigo, Fixkosten
 * in Amber-Abstufungen (groesste Kategorie am dunkelsten). Zeile antippen klappt die Betraege
 * je Kategorie auf. Unter den Balken stehen die Kategorien als Filter-Chips (zugleich Legende).
 * Mit der Maus hebt Hover auf Balkenteil oder Legende eine Kategorie hervor
 * und schreibt ihren Betrag in jeden Balken, damit das Auge nicht zur Summenspalte wandert. Ein Tipp auf die Legende rechnet die
 * Kategorie aus und ein, gemeinsam mit der Kostenkarte der Fahrzeugseite.
 */
const props = defineProps<{ carId: string | null; registrationDate: string | null }>()
const { t } = useI18n()
const { isDark } = storeToRefs(useThemeStore())

const { ranked, thisYear, year, dataYear, minYear, items, loading, error, spread, hidden, setShown, series, allSeries } =
  useYearCosts(() => props.carId, () => props.registrationDate)

const colorOf = (key: string) => costColor(key, ranked.value, isDark.value, ENERGY)
const labelOf = (key: string) => (key === ENERGY ? t('cars.costs.energy') : t(`fixed_costs.category_${key}`))
const hasEnergy = computed(() => allSeries.value.some(p => p.energyEur !== 0))
const legend = computed(() => legendKeys({ hasEnergy: hasEnergy.value, ranked: ranked.value, hidden: hidden.value, carCategories: items.value.map(fc => fc.category) }))

const focus = ref<string | null>(null)
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
      <CostMonthBars :key="carId ?? ''" v-model:focus="focus" class="mt-2" :series="series" :ranked="ranked" :year="year" />

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
