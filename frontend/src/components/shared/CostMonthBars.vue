<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { storeToRefs } from 'pinia'
import type { FixedCostCategory } from '../../api/fixedCostService'
import { ENERGY } from '../../composables/useYearCosts'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import { useThemeStore } from '../../stores/theme'
import { barScale, fitSegments, type MonthlyCostPoint } from '../../utils/carCostSummary'
import { costColor } from '../../utils/costColors'
import Collapse from './Collapse.vue'

/**
 * Ein Balken je Monat: Strom in Indigo, Fixkosten in Amber-Abstufungen (groesste Kategorie am
 * dunkelsten). Zeile antippen klappt die Betraege je Kategorie auf. `focus` hebt eine Kategorie
 * hervor und schreibt ihren Betrag in jeden Balken (Maus-Hover, auch aus einer Legende daneben).
 * Genutzt vom Kostenverlauf im Dashboard und von der Kostenkarte der Fahrzeugseite.
 */
const props = defineProps<{ series: MonthlyCostPoint[]; ranked: FixedCostCategory[]; year: number }>()
const focus = defineModel<string | null>('focus', { default: null })
const { t, locale } = useI18n()
const { formatCurrency } = useLocaleFormat()
const { isDark } = storeToRefs(useThemeStore())

const colorOf = (key: string) => costColor(key, props.ranked, isDark.value, ENERGY)
const labelOf = (key: string) => (key === ENERGY ? t('cars.costs.energy') : t(`fixed_costs.category_${key}`))

/** Betraege eines Monats je Kategorie in Stapelreihenfolge (Strom zuerst), Einnahmen am Ende. */
function parts(p: MonthlyCostPoint): { key: string; eur: number }[] {
  const list = [{ key: ENERGY, eur: p.energyEur }, ...props.ranked.map(c => ({ key: c as string, eur: p.fixedByCategory[c] ?? 0 }))]
  for (const [c, v] of Object.entries(p.fixedByCategory)) if (!props.ranked.includes(c as FixedCostCategory)) list.push({ key: c, eur: v })
  return list.filter(x => Math.round(x.eur) !== 0)
}

const open = ref<number | null>(null)
watch(() => props.year, () => { open.value = null })

// Balken: positive Teile gestapelt. Ausreisser werden im groessten Teilstueck mit Achsenbruch
// gekuerzt, der gekuerzte Teil belegt dabei mindestens 60 Prozent des Balkens.
const rows = computed(() => {
  const cap = barScale(props.series.map(p => parts(p).filter(x => x.eur > 0).map(x => x.eur)))
  return props.series.map(p => {
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
</script>

<template>
  <ul :aria-label="t('cars.costs.chart_aria', { year })" @pointerleave="focus = null">
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
      <Collapse :open="open === i">
      <dl class="max-w-[17rem] space-y-1 pb-2 pl-[2.75rem] text-sm box-content">
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
      </Collapse>
    </li>
  </ul>
</template>
