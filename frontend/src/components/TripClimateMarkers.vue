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
}>()

const { t } = useI18n()

const LOAD_META: Record<ClimateLoadKey, { icon: unknown; color: string; labelKey: string }> = {
  comfortHeat: { icon: FireIcon, color: 'text-orange-500 dark:text-orange-400', labelKey: 'dashboard.trip_climate_load_comfort_heat' },
  hvacHeating: { icon: SunIcon, color: 'text-orange-500 dark:text-orange-400', labelKey: 'dashboard.trip_climate_load_hvac_heating' },
  hvacCooling: { icon: SparklesIcon, color: 'text-sky-500 dark:text-sky-400', labelKey: 'dashboard.trip_climate_load_hvac_cooling' },
  batteryHeater: { icon: Battery100Icon, color: 'text-orange-500 dark:text-orange-400', labelKey: 'dashboard.trip_climate_load_battery_heater' },
}

const loads = computed(() =>
  activeClimateLoads(props.climate).map((l) => ({
    key: l.key,
    icon: LOAD_META[l.key].icon,
    color: LOAD_META[l.key].color,
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
    class="flex flex-wrap items-center justify-center gap-x-3 gap-y-1 text-xs text-gray-500 dark:text-gray-400"
  >
    <li v-for="(load, i) in loads" :key="load.key" class="flex items-center gap-1">
      <span v-if="i > 0" class="mr-2 text-gray-300 dark:text-gray-600" aria-hidden="true">&middot;</span>
      <component :is="load.icon" :class="['w-3 h-3 flex-shrink-0', load.color]" aria-hidden="true" />
      <span class="text-gray-700 dark:text-gray-300">{{ load.label }}</span>
      <span>{{ load.duration }}</span>
    </li>
  </ul>
</template>
