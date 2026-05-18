<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  BoltIcon,
  GlobeAltIcon,
  CalendarIcon,
  FireIcon,
  BriefcaseIcon,
  Cog6ToothIcon,
  TruckIcon,
} from '@heroicons/vue/24/outline'
import type { Car } from '../../api/carService'
import type { VehicleSpecification } from '../../api/vehicleSpecificationService'
import { useLocaleFormat } from '../../composables/useLocaleFormat'

const props = defineProps<{
  car: Car
  wltp: VehicleSpecification | null
  /** Highest odometer reading from ev_logs (current mileage). Null when user has no logged odometer. */
  currentOdometerKm?: number | null
  /** 'horizontal' for desktop inline chips, 'stacked' for mobile expandable list */
  orientation?: 'horizontal' | 'stacked'
}>()

const { t, locale } = useI18n()
const { formatDistance } = useLocaleFormat()

const orientation = computed(() => props.orientation ?? 'horizontal')

// Effective battery (SoH-adjusted). Fallback to nominal if null.
const effectiveBatteryKwh = computed<number | null>(() => {
  if (props.car.effectiveBatteryCapacityKwh != null) return props.car.effectiveBatteryCapacityKwh
  if (props.car.batteryCapacityKwh != null) return props.car.batteryCapacityKwh
  return null
})

// SoH percent (100 - degradation). Only show note when degradation actually given.
const sohPercent = computed<number | null>(() => {
  if (props.car.batteryDegradationPercent == null) return null
  const soh = 100 - props.car.batteryDegradationPercent
  return Math.round(soh * 10) / 10
})

const formatKwh = (kwh: number) => {
  // 1 decimal max, locale-aware
  return kwh.toLocaleString(locale.value === 'en' ? 'en-GB' : 'de-DE', {
    maximumFractionDigits: 1,
  })
}

const batteryLabel = computed<string | null>(() => {
  if (effectiveBatteryKwh.value == null) return null
  return `${formatKwh(effectiveBatteryKwh.value)} kWh`
})

const batteryTooltip = computed<string | null>(() => {
  if (sohPercent.value == null) return null
  return t('dashboard.car_card_battery_soh_hint', { soh: sohPercent.value })
})

const rangeLabel = computed<string | null>(() => {
  if (!props.wltp?.officialRangeKm) return null
  return formatDistance(props.wltp.officialRangeKm)
})

const powerLabel = computed<string | null>(() => {
  if (props.car.powerKw == null) return null
  const ps = Math.round(props.car.powerKw * 1.35962)
  return `${props.car.powerKw} kW (${ps} PS)`
})

const yearLabel = computed<number | null>(() => {
  return props.car.year ?? null
})

const hasHeatPump = computed(() => props.car.hasHeatPump === true)
const isBusinessCar = computed(() => props.car.isBusinessCar === true)

const odometerLabel = computed<string | null>(() => {
  if (props.currentOdometerKm == null) return null
  return formatDistance(props.currentOdometerKm)
})

// In horizontal mode we render compact pills. In stacked we render full rows.
const isHorizontal = computed(() => orientation.value === 'horizontal')
</script>

<template>
  <div
    :class="[
      isHorizontal
        ? 'flex flex-wrap items-center gap-x-3 gap-y-2'
        : 'flex flex-col gap-2'
    ]"
  >
    <!-- Battery (effective, SoH-aware) -->
    <div
      v-if="batteryLabel"
      :class="[
        'inline-flex items-center gap-1.5 text-sm',
        isHorizontal
          ? 'text-gray-700 dark:text-gray-200'
          : 'text-gray-700 dark:text-gray-200'
      ]"
      :title="batteryTooltip ?? undefined"
    >
      <BoltIcon class="w-4 h-4 text-amber-500 flex-shrink-0" />
      <span class="font-medium">{{ batteryLabel }}</span>
      <span
        v-if="sohPercent != null"
        class="text-[11px] text-gray-500 dark:text-gray-400 whitespace-nowrap"
      >
        ({{ t('dashboard.car_card_battery_soh_short', { soh: sohPercent }) }})
      </span>
      <span v-if="!isHorizontal" class="sr-only">{{ t('dashboard.car_card_battery_effective') }}</span>
    </div>

    <!-- WLTP range -->
    <div
      v-if="rangeLabel"
      class="inline-flex items-center gap-1.5 text-sm text-gray-700 dark:text-gray-200"
      :title="t('dashboard.car_card_wltp_range_hint')"
    >
      <GlobeAltIcon class="w-4 h-4 text-indigo-500 flex-shrink-0" />
      <span class="font-medium">{{ rangeLabel }}</span>
      <span class="text-[11px] text-gray-500 dark:text-gray-400">WLTP</span>
    </div>

    <!-- Odometer (highest from logs) -->
    <div
      v-if="odometerLabel"
      class="inline-flex items-center gap-1.5 text-sm text-gray-700 dark:text-gray-200"
      :title="t('dashboard.car_card_odometer')"
    >
      <TruckIcon class="w-4 h-4 text-gray-500 dark:text-gray-400 flex-shrink-0" />
      <span class="font-medium">{{ odometerLabel }}</span>
    </div>

    <!-- Power -->
    <div
      v-if="powerLabel"
      class="inline-flex items-center gap-1.5 text-sm text-gray-700 dark:text-gray-200"
      :title="t('dashboard.car_card_power')"
    >
      <Cog6ToothIcon class="w-4 h-4 text-gray-500 dark:text-gray-400 flex-shrink-0" />
      <span class="font-medium">{{ powerLabel }}</span>
    </div>

    <!-- Year -->
    <div
      v-if="yearLabel"
      class="inline-flex items-center gap-1.5 text-sm text-gray-700 dark:text-gray-200"
      :title="t('dashboard.car_card_year')"
    >
      <CalendarIcon class="w-4 h-4 text-gray-500 dark:text-gray-400 flex-shrink-0" />
      <span class="font-medium">{{ yearLabel }}</span>
    </div>

    <!-- Heat pump (only when true) -->
    <div
      v-if="hasHeatPump"
      class="inline-flex items-center gap-1.5 text-sm text-gray-700 dark:text-gray-200"
      :title="t('dashboard.car_card_heat_pump')"
    >
      <FireIcon class="w-4 h-4 text-orange-500 flex-shrink-0" />
      <span class="font-medium">{{ t('dashboard.car_card_heat_pump') }}</span>
    </div>

    <!-- Business car (only when true) -->
    <div
      v-if="isBusinessCar"
      class="inline-flex items-center gap-1.5 text-sm text-gray-700 dark:text-gray-200"
      :title="t('dashboard.car_card_business_car')"
    >
      <BriefcaseIcon class="w-4 h-4 text-blue-500 flex-shrink-0" />
      <span class="font-medium">{{ t('dashboard.car_card_business_car') }}</span>
    </div>
  </div>
</template>
