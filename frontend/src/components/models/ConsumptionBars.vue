<template>
  <!-- "Hersteller 13,9 / Fahrer 11,6": two bars on the scale every row shares (0 to the axis
       maximum), so the gap between prospectus and everyday use shows without a legend.
       Screen readers get the same figures as text in the row. -->
  <span class="grid gap-[5px] text-[11.5px] leading-none" aria-hidden="true" data-testid="consumption-bars">
    <span v-for="bar in bars" :key="bar.key" class="grid grid-cols-[56px_minmax(0,1fr)_auto] items-center gap-x-1.5">
      <span class="truncate text-gray-500 dark:text-gray-400">{{ bar.label }}</span>
      <span class="h-2 overflow-hidden rounded-sm bg-gray-200 dark:bg-gray-700">
        <span
          v-if="bar.width !== null"
          class="block h-full rounded-sm transition-[width] duration-500 ease-out motion-reduce:transition-none"
          :class="bar.cls"
          :style="{ width: `${bar.width}%` }"
        ></span>
      </span>
      <span class="tabular-nums text-gray-900 dark:text-gray-100">
        {{ bar.text }}<span v-if="bar.deviation" class="ml-1.5 font-semibold" :class="bar.deviationCls">{{ bar.deviation }}</span>
      </span>
    </span>
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import { barLength, type LadderAxis } from '../../utils/ladderScale'

// Values in kWh/100km; the axis converts to the display unit.
const props = defineProps<{
  axis: LadderAxis
  real: number | null
  wltp: number | null
  realText: string
  wltpText: string
  /** Real vs. WLTP in percent (kWh space), positive = more than the manufacturer says */
  deviationPct: number | null
}>()

const { t } = useI18n()
const { formatDecimal } = useLocaleFormat()

const bars = computed(() => {
  const over = props.deviationPct != null && props.deviationPct > 0
  const deviation = props.deviationPct != null
    ? `${props.deviationPct > 0 ? '+' : '−'}${formatDecimal(Math.abs(props.deviationPct), 0)} %`
    : ''
  return [
    {
      key: 'wltp',
      label: t('models_ranking.row.bar_manufacturer'),
      width: barLength(props.wltp, props.axis),
      text: props.wltp != null ? props.wltpText : '–',
      cls: 'bg-gray-400 dark:bg-gray-500',
      deviation: '',
      deviationCls: '',
    },
    {
      key: 'real',
      label: t('models_ranking.row.bar_drivers'),
      width: barLength(props.real, props.axis),
      text: props.real != null ? props.realText : '–',
      cls: over ? 'bg-orange-500 dark:bg-orange-400' : 'bg-green-600 dark:bg-green-400',
      deviation,
      deviationCls: over ? 'text-orange-700 dark:text-orange-400' : 'text-green-700 dark:text-green-400',
    },
  ]
})
</script>
