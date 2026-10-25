<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  FireIcon,
  SunIcon,
  SparklesIcon,
  Battery100Icon,
} from '@heroicons/vue/24/outline'
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
}>()

const { t } = useI18n()

// App-CI: Symbole grau, die Last steht im Wort daneben.
const LOAD_META: Record<ClimateLoadKey, { icon: unknown; labelKey: string }> = {
  comfortHeat: { icon: FireIcon, labelKey: 'dashboard.trip_climate_load_comfort_heat' },
  hvacHeating: { icon: SunIcon, labelKey: 'dashboard.trip_climate_load_hvac_heating' },
  hvacCooling: { icon: SparklesIcon, labelKey: 'dashboard.trip_climate_load_hvac_cooling' },
  batteryHeater: { icon: Battery100Icon, labelKey: 'dashboard.trip_climate_load_battery_heater' },
}

const loads = computed(() =>
  activeClimateLoads(props.climate).map((l) => ({
    key: l.key,
    icon: LOAD_META[l.key].icon,
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
    class="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-gray-500 dark:text-gray-400"
    :class="align === 'start' ? 'justify-start' : 'justify-center'"
  >
    <li v-for="(load, i) in loads" :key="load.key" class="flex items-center gap-1 whitespace-nowrap">
      <span v-if="i > 0" class="mr-2 text-gray-300 dark:text-gray-600" aria-hidden="true">&middot;</span>
      <component :is="load.icon" class="w-3 h-3 flex-shrink-0 text-gray-400 dark:text-gray-500" aria-hidden="true" />
      <span class="text-gray-700 dark:text-gray-300">{{ load.label }}</span>
      <span>{{ load.duration }}</span>
    </li>
  </ul>
</template>
