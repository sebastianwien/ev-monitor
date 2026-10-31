<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  FireIcon,
  SparklesIcon,
} from '@heroicons/vue/24/outline'
import HeatWavesIcon from './icons/HeatWavesIcon.vue'
import BatteryHeatIcon from './icons/BatteryHeatIcon.vue'
import {
  activeClimateLoads,
  climateDurationMinutes,
  type ClimateSummary,
  type ClimateLoadKey,
} from '../utils/tripClimate'

const props = defineProps<{
  climate: ClimateSummary | null | undefined
  /** start: linksbuendig unter dem Text einer Feed-Zeile; Standard mittig unter einer Tabellenzeile */
  align?: 'start' | 'center'
  /** Logbuch-Zeile: auf dem Handy nur die Symbole (Last und Dauer für Screenreader und als Tooltip), ab 768 px mit Wort. */
  compact?: boolean
}>()

const { t } = useI18n()

// App-CI: Symbole grau, die Last steht im Wort daneben. Kompakt fehlt das Wort, dann sagt die Farbe den
// Typ (Ausnahme "farbige Typ-Icons"): Wärme orange, Kühlen cyan.
const HEAT = 'text-orange-600 dark:text-orange-400'
const COOL = 'text-cyan-600 dark:text-cyan-400'
const LOAD_META: Record<ClimateLoadKey, { icon: unknown; labelKey: string; tone: string }> = {
  comfortHeat: { icon: FireIcon, labelKey: 'dashboard.trip_climate_load_comfort_heat', tone: HEAT },
  hvacHeating: { icon: HeatWavesIcon, labelKey: 'dashboard.trip_climate_load_hvac_heating', tone: HEAT },
  hvacCooling: { icon: SparklesIcon, labelKey: 'dashboard.trip_climate_load_hvac_cooling', tone: COOL },
  batteryHeater: { icon: BatteryHeatIcon, labelKey: 'dashboard.trip_climate_load_battery_heater', tone: HEAT },
}

const loads = computed(() =>
  activeClimateLoads(props.climate).map((l) => ({
    key: l.key,
    icon: LOAD_META[l.key].icon,
    tone: LOAD_META[l.key].tone,
    label: t(LOAD_META[l.key].labelKey),
    duration: durationText(l.seconds),
  })),
)

function durationText(seconds: number): string {
  if (seconds < 60) return t('dashboard.trip_climate_minutes_lt1')
  return t('dashboard.trip_climate_minutes', { n: climateDurationMinutes(seconds) })
}
</script>

<template>
  <!-- Second, self-explanatory line under a trip: icon + label + duration per active load.
       Renders nothing for trips without climate data (Smartcar/imports). -->
  <ul
    v-if="loads.length"
    class="flex-wrap items-center text-xs text-gray-500 dark:text-gray-400"
    :class="[align === 'start' ? 'justify-start' : 'justify-center', compact ? 'inline-flex gap-x-1 md:gap-x-3' : 'flex gap-x-3 gap-y-1']"
    data-testid="trip-climate"
  >
    <li v-for="(load, i) in loads" :key="load.key" class="flex items-center gap-1 whitespace-nowrap"
      :title="compact ? `${load.label} ${load.duration}` : undefined">
      <span v-if="i > 0 && !compact" class="mr-2 text-gray-300 dark:text-gray-600" aria-hidden="true">&middot;</span>
      <component :is="load.icon" class="flex-shrink-0" :class="compact ? ['h-4 w-4', load.tone] : 'h-3 w-3 text-gray-400 dark:text-gray-500'" aria-hidden="true" />
      <span :class="compact ? 'sr-only md:not-sr-only' : ''" class="text-gray-700 dark:text-gray-300">{{ load.label }}</span>
      <span :class="compact ? 'sr-only md:not-sr-only' : ''">{{ load.duration }}</span>
    </li>
  </ul>
</template>
