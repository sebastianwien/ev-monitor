<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import Spark from './Spark.vue'
import EnergyFlow from './EnergyFlow.vue'
import RangeGauge from './RangeGauge.vue'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import type { StoryTile } from '../../composables/storyModel'

/** Eine Fahrerfrage als Kachel: Frage, eine Zahl, ein Delta-Chip, eine Grafik. Nie mehr. */
const props = defineProps<{ tile: StoryTile }>()
const emit = defineEmits<{ open: [id: StoryTile['id']] }>()

const { t } = useI18n()
const { formatCurrency, formatConsumption, formatDistance, formatDecimal, consumptionUnitLabel, distanceUnitLabel } = useLocaleFormat()

const question = computed(() => t(`story.q_${props.tile.id}`))

const value = computed(() => {
  const x = props.tile
  switch (x.id) {
    case 'cost': return { n: formatCurrency(x.value, { decimals: 0 }), unit: '' }
    case 'flow': return { n: formatDecimal(x.value, 0), unit: '%' }
    case 'range': return { n: formatDecimal(x.value, 0), unit: distanceUnitLabel() }
    case 'wltp': return { n: formatConsumption(x.value, { showUnit: false }), unit: consumptionUnitLabel() }
    case 'peer': return { n: formatConsumption(x.value, { showUnit: false }), unit: consumptionUnitLabel() }
    case 'battery': return { n: formatDecimal(x.value, 0), unit: t('story.battery_unit') }
    case 'co2': return { n: formatDecimal(x.value, 0), unit: t('story.co2_unit') }
  }
})

const chip = computed(() => {
  const x = props.tile
  switch (x.id) {
    case 'cost':
      if (x.delta == null) return t('story.cost_no_prev')
      if (x.sentiment === 'neutral') return t('story.cost_same')
      return t('story.cost_delta', { delta: (x.delta > 0 ? '+' : '') + formatCurrency(x.delta, { decimals: 0 }) })
    case 'flow':
      return x.flow?.partial
        ? t('story.flow_partial', { covered: x.flow.coveredLogCount, total: x.flow.totalLogCount })
        : t('story.flow_loss', { kwh: formatDecimal(x.flow?.lossKwh ?? 0, 0) })
    case 'range':
      return t('story.range_basis_recent')
    case 'wltp': {
      const pct = formatDecimal(Math.abs(x.deltaPercent ?? 0), 0)
      const wltp = formatConsumption(x.reference ?? 0, { showUnit: false })
      return (x.deltaPercent ?? 0) >= 0 ? t('story.wltp_delta_over', { pct, wltp }) : t('story.wltp_delta_under', { pct, wltp })
    }
    case 'peer': {
      const pct = formatDecimal(Math.abs(x.deltaPercent ?? 0), 0)
      if (x.sentiment === 'neutral') return t('story.peer_same', { count: x.peerCount })
      return (x.deltaPercent ?? 0) < 0 ? t('story.peer_better', { pct, count: x.peerCount }) : t('story.peer_worse', { pct, count: x.peerCount })
    }
    case 'battery': {
      const state = t(`story.battery_${x.sentiment === 'good' ? 'good' : x.sentiment === 'warn' ? 'warn' : 'bad'}`)
      return x.odometerKm ? `${state}, ${t('story.battery_km', { km: formatDistance(x.odometerKm) })}` : state
    }
    case 'co2':
      return t('story.co2_green', { kg: formatDecimal(x.valueGreen ?? 0, 0) })
  }
})

const chipClass = computed(() => ({
  good: 'text-green-700 bg-green-50 dark:text-green-300 dark:bg-green-900/40',
  warn: 'text-amber-700 bg-amber-50 dark:text-amber-300 dark:bg-amber-900/40',
  bad: 'text-rose-700 bg-rose-50 dark:text-rose-300 dark:bg-rose-900/40',
  neutral: 'text-gray-600 bg-gray-100 dark:text-gray-300 dark:bg-gray-700',
}[props.tile.sentiment]))

const sparkLabels = computed(() => props.tile.id === 'co2'
  ? [t('story.co2_grid_label'), t('story.co2_green_label'), t('story.co2_petrol_label')]
  : [])

const flowLabels = computed(() => ({
  grid: t('story.flow_grid'), battery: t('story.flow_battery'), road: t('story.flow_road'),
  loss: t('story.flow_loss_label'), home: t('story.flow_home'), public: t('story.flow_public'),
}))
</script>

<template>
  <button
    type="button"
    :data-testid="`story-tile-${tile.id}`"
    class="story-tile @container shrink-0 w-[78%] md:w-auto snap-start text-left rounded-2xl bg-white p-3.5 max-md:edge-card md:bg-gray-100/80 md:p-5 @[420px]:px-6 @[420px]:py-5 min-w-0
           dark:bg-gray-800 md:dark:bg-gray-800/60 md:hover:bg-gray-200/70 md:dark:hover:bg-gray-800 transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-story-accent"
    :style="{ viewTransitionName: `story-tile-${tile.id}` }"
    @click="emit('open', tile.id)">
    <!-- Container Queries: die Kachel richtet sich nach ihrer eigenen Breite (Wischreihe, Raster, volle Breite), nicht nach dem Viewport. -->
    <div class="text-[13px] @[420px]:text-sm font-semibold text-gray-500 dark:text-gray-400">{{ question }}</div>
    <div class="font-extrabold text-[28px] @[420px]:text-[34px] leading-tight tracking-tight tabular-nums text-gray-900 dark:text-gray-50 mt-1">
      {{ value.n }}<span v-if="value.unit" class="text-[13px] @[420px]:text-[15px] font-semibold text-gray-500 dark:text-gray-400 ml-1">{{ value.unit }}</span>
    </div>
    <span class="inline-block mt-1 text-xs font-semibold px-2 py-0.5 rounded-full" :class="chipClass">{{ chip }}</span>
    <span v-if="tile.thinCharges != null" class="inline-block mt-1 ml-1 text-xs px-2 py-0.5 rounded-full bg-amber-100 text-amber-800 dark:bg-amber-900/40 dark:text-amber-300" data-testid="story-thin">{{ t('story.thin_charges', { n: tile.thinCharges }, tile.thinCharges) }}</span>
    <div class="mt-2 @[420px]:mt-4 @[420px]:max-w-[520px]">
      <EnergyFlow v-if="tile.id === 'flow' && tile.flow" :flow="tile.flow" :labels="flowLabels" />
      <RangeGauge v-else-if="tile.id === 'range' && tile.range" :range="tile.range" :label="t('story.range_window', { max: 90, min: 10 })" />
      <template v-else>
        <Spark :values="tile.spark" :variant="tile.sparkVariant" :reference="tile.reference" :tone="tile.sentiment" />
        <div v-if="sparkLabels.length" class="flex justify-between text-[10px] text-gray-400 mt-0.5">
          <span v-for="l in sparkLabels" :key="l">{{ l }}</span>
        </div>
      </template>
    </div>
  </button>
</template>
