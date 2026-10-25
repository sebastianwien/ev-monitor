<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import Spark from './Spark.vue'
import FlowBar from './FlowBar.vue'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import type { StoryTile } from '../../composables/storyModel'

/**
 * Eine Fahrerfrage als Kachel in fester Höhe, Inhalt oben: Label in Versalien, eine Zahl mit
 * Einheit, ein Satz, unten eine Grafik mit Zahl und Bezug. Wenige Ladungen nennt die
 * Qualitätszeile, nicht die Kachel.
 */
const props = defineProps<{ tile: StoryTile }>()
const emit = defineEmits<{ open: [id: StoryTile['id']] }>()

const { t } = useI18n()
const { formatCurrency, formatConsumption, formatDistance, formatDecimal, consumptionUnitLabel, distanceUnitLabel, currencySymbol } = useLocaleFormat()

const label = computed(() => t(`story.q_${props.tile.id}`))

const value = computed(() => {
  const x = props.tile
  switch (x.id) {
    case 'cost': {
      // Symbol als kleine Einheit hinter der Zahl, ausser die Waehrung schreibt es davor (GBP).
      const f = formatCurrency(x.value, { decimals: 0 })
      return f.startsWith(currencySymbol.value) ? { n: f, unit: '' } : { n: f.replace(currencySymbol.value, '').trim(), unit: currencySymbol.value }
    }
    case 'flow': return { n: formatDecimal(x.value, 0), unit: t('story.flow_unit') }
    case 'range': return { n: formatDecimal(x.value, 0), unit: distanceUnitLabel() }
    case 'wltp': return { n: formatConsumption(x.value, { showUnit: false }), unit: consumptionUnitLabel() }
    case 'peer': return { n: formatConsumption(x.value, { showUnit: false }), unit: consumptionUnitLabel() }
    case 'battery': return { n: `${x.estimated ? '~' : ''}${formatDecimal(x.value, 0)}`, unit: t('story.battery_unit') }
  }
})

const note = computed(() => {
  const x = props.tile
  switch (x.id) {
    case 'cost':
      // Ohne Preise sind die Kosten zu niedrig: Zahl der Ladungen statt eines schiefen Vergleichs.
      if (x.pricelessCharges) return t('story.cost_priceless', { n: x.pricelessCharges })
      // Laufender Monat mit weniger Strom als der Vormonat: "weniger Euro" hiesse nur "weniger Tage".
      if (x.projection) {
        return t('story.cost_projection', {
          eur: formatCurrency(x.projection.eur, { decimals: 0 }),
          previous: formatCurrency(x.projection.previousEur, { decimals: 0 }),
        })
      }
      if (x.delta == null) return x.previousIncomplete ? '' : t('story.cost_no_prev')
      if (x.sentiment === 'neutral') return t('story.cost_same')
      return t('story.cost_delta', { delta: (x.delta > 0 ? '+' : '') + formatCurrency(x.delta, { decimals: 0 }) })
    case 'flow':
      return x.flow?.partial
        ? t('story.flow_partial', { covered: x.flow.coveredLogCount, total: x.flow.totalLogCount })
        : t('story.flow_loss', { kwh: formatDecimal(x.flow?.lossKwh ?? 0, 0) })
    case 'range':
      return `${t('story.range_basis_recent')}, ${t('story.range_window', { max: rangeWindow.value.max, min: rangeWindow.value.min })}`
    case 'wltp': {
      const pct = formatDecimal(Math.abs(x.deltaPercent ?? 0), 0)
      const wltp = formatConsumption(x.reference ?? 0, { showUnit: false })
      return (x.deltaPercent ?? 0) >= 0 ? t('story.wltp_delta_over', { pct, wltp }) : t('story.wltp_delta_under', { pct, wltp })
    }
    case 'peer': {
      // Wenige Ladungen: kein Prozentsatz gegen andere, nur ihr Schnitt.
      if (x.thinCharges != null) return t('story.peer_avg', { avg: formatConsumption(x.spark[0] ?? 0, { showUnit: false }), count: x.peerCount })
      const pct = formatDecimal(Math.abs(x.deltaPercent ?? 0), 0)
      if (x.sentiment === 'neutral') return t('story.peer_same', { count: x.peerCount })
      return (x.deltaPercent ?? 0) < 0 ? t('story.peer_better', { pct, count: x.peerCount }) : t('story.peer_worse', { pct, count: x.peerCount })
    }
    case 'battery': {
      const state = t(`story.battery_${x.sentiment === 'good' ? 'good' : x.sentiment === 'warn' ? 'warn' : 'bad'}`)
      return x.odometerKm ? `${state} ${t('story.battery_km', { km: formatDistance(x.odometerKm) })}` : state
    }
  }
})

/** Position des eigenen Werts auf der Skala sparsam ... hoch: Schnitt in der Mitte, ±100 % an den Rändern. */
const peerPos = computed(() => Math.min(1, Math.max(0, 0.5 + (props.tile.deltaPercent ?? 0) / 200)))
const rangeWindow = computed(() => {
  const w = props.tile.range?.windows.find(x => x.recommended) ?? props.tile.range?.windows[0]
  return w ? { min: w.socMin, max: w.socMax } : { min: 10, max: 90 }
})
const endLabel = computed(() => props.tile.id === 'cost' ? formatCurrency(props.tile.value, { decimals: 0 }) : formatConsumption(props.tile.value, { showUnit: false }))
</script>

<template>
  <button
    type="button"
    :data-testid="`story-tile-${tile.id}`"
    class="story-tile flex h-[150px] w-[85%] min-w-0 shrink-0 snap-start flex-col gap-1 rounded-2xl bg-white p-3.5 text-left edge-card md:h-[160px] md:w-auto
           dark:bg-gray-800/60 md:hover:bg-gray-50 md:dark:hover:bg-gray-800 transition-[transform,box-shadow,background-color] active:translate-x-0.5 active:translate-y-0.5 active:shadow-none focus:outline-none focus-visible:ring-2 focus-visible:ring-story-accent"
    :style="{ viewTransitionName: `story-tile-${tile.id}` }"
    @click="emit('open', tile.id)">
    <!-- "geschätzt" wie in den Fakt-Kacheln der Modellseiten: als Zusatz am Label, nicht als Plakette an der Zahl -->
    <span class="line-clamp-2 text-[11.5px] font-semibold uppercase leading-tight tracking-[0.06em] text-gray-500 dark:text-gray-400">{{ label }}<span v-if="tile.id === 'battery' && tile.estimated" class="ml-1.5 normal-case tracking-normal text-amber-700 dark:text-amber-400">{{ t('story.estimated_badge') }}</span></span>
    <span class="flex items-baseline gap-1 leading-none">
      <b class="text-[26px] font-extrabold tracking-tight text-gray-900 tabular-nums dark:text-gray-50">{{ value.n }}</b>
      <span class="text-[13px] font-semibold text-gray-500 dark:text-gray-400">{{ value.unit }}</span>
    </span>
    <span v-if="note" class="truncate text-[13.5px] text-gray-600 dark:text-gray-400">{{ note }}</span>
    <span data-role="graphic" class="mt-auto block">
      <FlowBar v-if="tile.id === 'flow' && tile.flow" :flow="tile.flow" />
      <span v-else-if="tile.id === 'range'" class="grid gap-1">
        <span class="relative block h-2.5 rounded-[3px] bg-gray-200 dark:bg-gray-700">
          <i class="absolute inset-y-0 rounded-[3px] bg-green-700 dark:bg-green-400" :style="{ left: `${rangeWindow.min}%`, width: `${rangeWindow.max - rangeWindow.min}%` }" />
        </span>
        <span class="flex justify-between text-[11px] text-gray-500 tabular-nums dark:text-gray-400"><span>{{ rangeWindow.min }} %</span><span>{{ rangeWindow.max }} %</span></span>
      </span>
      <span v-else-if="tile.id === 'peer'" class="grid gap-1">
        <span class="relative block h-2.5 rounded-[3px] bg-gray-200 dark:bg-gray-700">
          <i class="absolute inset-y-0 left-1/2 w-0.5 bg-gray-500" />
          <i class="absolute -top-0.5 h-3.5 w-3.5 rounded-full border-2 border-gray-900 bg-green-500 dark:border-gray-100" :style="{ left: `calc(${peerPos * 100}% - 7px)` }" />
        </span>
        <span class="flex justify-between text-[11px] text-gray-500 dark:text-gray-400"><span>{{ t('story.peer_scale_low') }}</span><span>{{ t('story.peer_scale_avg') }}</span><span>{{ t('story.peer_scale_high') }}</span></span>
      </span>
      <span v-else-if="tile.id === 'battery'" class="grid gap-1">
        <span class="relative block h-2.5 rounded-[3px] bg-gray-200 dark:bg-gray-700">
          <i class="absolute inset-y-0 left-0 rounded-[3px] bg-green-700 dark:bg-green-400" :style="{ width: `${Math.min(100, Math.max(0, tile.value))}%` }" />
        </span>
        <span class="flex justify-between text-[11px] text-gray-500 tabular-nums dark:text-gray-400"><span>0 %</span><span>{{ t('story.battery_scale_new') }}</span></span>
      </span>
      <Spark v-else :values="tile.spark" :variant="tile.sparkVariant" :reference="tile.reference" tone="good" :end-label="endLabel" />
    </span>
  </button>
</template>
