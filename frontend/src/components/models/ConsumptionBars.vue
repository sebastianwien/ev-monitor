<template>
  <!-- "Hersteller 13,9 / Fahrer 11,6": two bars on the scale every row shares (0 to the axis
       maximum), so the gap between prospectus and everyday use shows without a legend.
       Screen readers get the same figures as text in the row. -->
  <span class="grid gap-[5px] text-[11.5px] leading-none" aria-hidden="true" data-testid="consumption-bars">
    <!-- The number sits inside the fill (after it when the fill is too short); the deviation
         column is fixed, so both tracks are the same length -->
    <span v-for="bar in bars" :key="bar.key" class="grid grid-cols-[56px_minmax(0,1fr)_40px] items-center gap-x-1.5">
      <span class="truncate text-gray-500 dark:text-gray-400">{{ bar.label }}</span>
      <span class="relative h-[17px] overflow-hidden rounded bg-gray-200 dark:bg-gray-700">
        <span
          v-if="bar.width !== null"
          class="absolute inset-y-0 left-0 rounded transition-[width] duration-500 ease-out motion-reduce:transition-none"
          :class="bar.cls"
          :style="{ width: `${bar.width}%` }"
        ></span>
        <span
          v-if="bar.width !== null"
          class="absolute inset-y-0 flex items-center px-1.5 text-[11px] font-semibold tabular-nums"
          :class="bar.inside ? 'text-white dark:text-gray-950' : 'text-gray-900 dark:text-gray-100'"
          :style="bar.inside ? { right: `${100 - bar.width}%` } : { left: `${bar.width}%` }"
        >{{ bar.text }}</span>
        <span v-else class="absolute inset-y-0 left-0 flex items-center px-1.5 text-[11px] text-gray-500 dark:text-gray-400">{{ bar.text }}</span>
      </span>
      <span class="whitespace-nowrap font-semibold tabular-nums" :class="bar.deviationCls">{{ bar.deviation }}</span>
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
  // Below ~40 % of the track the fill is too short for the number, it then follows the fill
  const inside = (width: number | null) => width !== null && width >= 40
  const wltpWidth = barLength(props.wltp, props.axis)
  const realWidth = barLength(props.real, props.axis)
  return [
    {
      key: 'wltp',
      label: t('models_ranking.row.bar_manufacturer'),
      width: wltpWidth,
      inside: inside(wltpWidth),
      text: props.wltp != null ? props.wltpText : '–',
      cls: 'bg-gray-400 dark:bg-gray-500',
      deviation: '',
      deviationCls: '',
    },
    {
      key: 'real',
      label: t('models_ranking.row.bar_drivers'),
      width: realWidth,
      inside: inside(realWidth),
      text: props.real != null ? props.realText : '–',
      cls: over ? 'bg-orange-500 dark:bg-orange-400' : 'bg-green-600 dark:bg-green-400',
      deviation,
      deviationCls: over ? 'text-orange-700 dark:text-orange-400' : 'text-green-700 dark:text-green-400',
    },
  ]
})
</script>
