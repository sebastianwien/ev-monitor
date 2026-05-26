<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { MapPinIcon } from '@heroicons/vue/24/outline'
import type { ChargingTypeSplit, LocationSplit } from '../../composables/useDashboardStats'

const props = defineProps<{
  chargingTypeSplit: ChargingTypeSplit
  locationSplit: LocationSplit
}>()

const { t } = useI18n()

interface Segment {
  label: string
  kwh: number
  pct: number
  textColorClass: string
  bgClass: string
}

const acDcSegments = computed<Segment[]>(() => {
  const total = props.chargingTypeSplit.acKwh + props.chargingTypeSplit.dcKwh + props.chargingTypeSplit.unknownKwh
  if (total <= 0) return []
  const segments: Segment[] = [
    {
      label: t('dashboard.charging_type_ac'),
      kwh: props.chargingTypeSplit.acKwh,
      pct: Math.round((props.chargingTypeSplit.acKwh / total) * 100),
      textColorClass: 'text-blue-500 dark:text-blue-400',
      bgClass: 'bg-blue-500',
    },
    {
      label: t('dashboard.charging_type_dc'),
      kwh: props.chargingTypeSplit.dcKwh,
      pct: Math.round((props.chargingTypeSplit.dcKwh / total) * 100),
      textColorClass: 'text-orange-500 dark:text-orange-400',
      bgClass: 'bg-orange-500',
    },
  ]
  if (props.chargingTypeSplit.unknownKwh > 0) {
    segments.push({
      label: t('dashboard.charging_type_unknown'),
      kwh: props.chargingTypeSplit.unknownKwh,
      pct: Math.round((props.chargingTypeSplit.unknownKwh / total) * 100),
      textColorClass: 'text-gray-400 dark:text-gray-500',
      bgClass: 'bg-gray-400 dark:bg-gray-500',
    })
  }
  return segments
})

const locationSegments = computed<Segment[]>(() => {
  const total = props.locationSplit.publicKwh + props.locationSplit.privateKwh
  if (total <= 0) return []
  return [
    {
      label: t('dashboard.charging_location_public'),
      kwh: props.locationSplit.publicKwh,
      pct: Math.round((props.locationSplit.publicKwh / total) * 100),
      textColorClass: 'text-purple-500 dark:text-purple-400',
      bgClass: 'bg-purple-500',
    },
    {
      label: t('dashboard.charging_location_private'),
      kwh: props.locationSplit.privateKwh,
      pct: Math.round((props.locationSplit.privateKwh / total) * 100),
      textColorClass: 'text-green-500 dark:text-green-400',
      bgClass: 'bg-green-500',
    },
  ]
})

function fmtKwh(kwh: number): string {
  return kwh.toFixed(1)
}
</script>

<template>
  <div class="bg-white dark:bg-gray-700 rounded-sm border-2 border-gray-300 dark:border-gray-600 overflow-hidden shadow-[2px_2px_0_0_#d1d5db] dark:shadow-[2px_2px_0_0_#4b5563] flex flex-col">

    <!-- Header -->
    <div class="px-4 py-3 border-b border-gray-100 dark:border-gray-600 text-sm font-semibold text-gray-800 dark:text-gray-200 text-center">
      {{ t('dashboard.charging_type_split_title') }}
    </div>

    <div class="flex-1 flex flex-col divide-y divide-gray-100 dark:divide-gray-600">

      <!-- AC / DC -->
      <div class="p-4">
        <template v-if="acDcSegments.length > 0">
          <!-- Legend: first segment left, rest right-aligned -->
          <div class="flex justify-between mb-2.5">
            <div class="flex flex-col">
              <span :class="['text-xs font-semibold leading-tight', acDcSegments[0].textColorClass]">{{ acDcSegments[0].label }}</span>
              <span class="text-xs text-gray-700 dark:text-gray-300 font-semibold tabular-nums leading-snug">
                {{ fmtKwh(acDcSegments[0].kwh) }} kWh
                <span class="text-gray-400 dark:text-gray-500 font-normal">{{ acDcSegments[0].pct }}%</span>
              </span>
            </div>
            <div v-for="seg in acDcSegments.slice(1)" :key="seg.label" class="flex flex-col items-end">
              <span :class="['text-xs font-semibold leading-tight', seg.textColorClass]">{{ seg.label }}</span>
              <span class="text-xs text-gray-700 dark:text-gray-300 font-semibold tabular-nums leading-snug">
                {{ fmtKwh(seg.kwh) }} kWh
                <span class="text-gray-400 dark:text-gray-500 font-normal">{{ seg.pct }}%</span>
              </span>
            </div>
          </div>
          <!-- Stacked bar -->
          <div class="h-2.5 w-full flex rounded-full overflow-hidden">
            <div
              v-for="seg in acDcSegments"
              :key="seg.label"
              :class="['h-full transition-all duration-500', seg.bgClass]"
              :style="{ width: seg.pct + '%' }"
            />
          </div>
        </template>
        <div v-else class="text-xs text-gray-400 dark:text-gray-500 text-center py-3">-</div>
      </div>

      <!-- Ladeort -->
      <div class="p-4">
        <div class="flex items-center justify-center gap-1.5 mb-3">
          <MapPinIcon class="w-3.5 h-3.5 text-gray-400 dark:text-gray-500 flex-shrink-0" />
          <span class="text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wide">Ladeort</span>
        </div>

        <template v-if="locationSegments.length > 0">
          <!-- Legend: first segment left, rest right-aligned -->
          <div class="flex justify-between mb-2.5">
            <div class="flex flex-col">
              <span :class="['text-xs font-semibold leading-tight', locationSegments[0].textColorClass]">{{ locationSegments[0].label }}</span>
              <span class="text-xs text-gray-700 dark:text-gray-300 font-semibold tabular-nums leading-snug">
                {{ fmtKwh(locationSegments[0].kwh) }} kWh
                <span class="text-gray-400 dark:text-gray-500 font-normal">{{ locationSegments[0].pct }}%</span>
              </span>
            </div>
            <div v-for="seg in locationSegments.slice(1)" :key="seg.label" class="flex flex-col items-end">
              <span :class="['text-xs font-semibold leading-tight', seg.textColorClass]">{{ seg.label }}</span>
              <span class="text-xs text-gray-700 dark:text-gray-300 font-semibold tabular-nums leading-snug">
                {{ fmtKwh(seg.kwh) }} kWh
                <span class="text-gray-400 dark:text-gray-500 font-normal">{{ seg.pct }}%</span>
              </span>
            </div>
          </div>
          <!-- Stacked bar -->
          <div class="h-2.5 w-full flex rounded-full overflow-hidden">
            <div
              v-for="seg in locationSegments"
              :key="seg.label"
              :class="['h-full transition-all duration-500', seg.bgClass]"
              :style="{ width: seg.pct + '%' }"
            />
          </div>
        </template>
        <div v-else class="text-xs text-gray-400 dark:text-gray-500 text-center py-3">-</div>
      </div>

    </div>
  </div>
</template>
