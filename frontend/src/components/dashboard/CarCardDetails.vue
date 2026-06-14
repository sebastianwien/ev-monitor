<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  BoltIcon,
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
  /**
   * 'horizontal' = desktop 2-col grid (inline in card)
   * 'stacked'    = vertical full-row list (dashboard expandable panel)
   * 'compact'    = mobile flowing spec strip, text-xs, wraps between items (logs card)
   */
  orientation?: 'horizontal' | 'stacked' | 'compact'
}>()

const { t, locale } = useI18n()
const { formatDistance } = useLocaleFormat()

const orientation = computed(() => props.orientation ?? 'horizontal')

// Effective battery (SoH-adjusted). Fallback to nominal if null.
const effectiveBatteryKwh = computed<number | null>(() => {
  if (props.car.effectiveBatteryCapacityKwh != null) return props.car.effectiveBatteryCapacityKwh
  if (props.car.customNetCapacityKwh != null) return props.car.customNetCapacityKwh
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

const isHorizontal = computed(() => orientation.value === 'horizontal')
const isCompact = computed(() => orientation.value === 'compact')

// Container layout per orientation.
const containerClass = computed(() => {
  switch (orientation.value) {
    case 'horizontal': return 'grid grid-cols-2 gap-x-3 gap-y-1 text-sm'
    case 'compact': return 'flex flex-wrap items-center gap-x-3 gap-y-1 text-xs'
    default: return 'flex flex-col gap-2 text-sm' // stacked
  }
})

// Smaller icons only on the compact strip.
const iconClass = computed(() => (isCompact.value ? 'w-3.5 h-3.5' : 'w-4 h-4'))

// SoH: rounded to integer on the compact strip to save width, 1 decimal elsewhere.
const sohDisplay = computed<number | null>(() => {
  if (sohPercent.value == null) return null
  return isCompact.value ? Math.round(sohPercent.value) : sohPercent.value
})
</script>

<template>
  <div :class="containerClass">
    <!-- Battery (effective, SoH-aware) -->
    <div
      v-if="batteryLabel"
      class="inline-flex items-center gap-1.5 whitespace-nowrap text-gray-700 dark:text-gray-200"
      :title="batteryTooltip ?? undefined"
    >
      <BoltIcon :class="iconClass" class="text-amber-500 flex-shrink-0" />
      <span class="font-medium">{{ batteryLabel }}</span>
      <span
        v-if="sohPercent != null"
        class="text-gray-500 dark:text-gray-400"
        :class="isHorizontal ? 'text-[11px]' : 'text-[10px]'"
      >
        ({{ t('dashboard.car_card_battery_soh_short', { soh: sohDisplay }) }})
      </span>
      <span v-if="!isHorizontal && !isCompact" class="sr-only">{{ t('dashboard.car_card_battery_effective') }}</span>
    </div>

    <!-- Odometer (highest from logs) -->
    <div
      v-if="odometerLabel"
      class="inline-flex items-center gap-1.5 whitespace-nowrap text-gray-700 dark:text-gray-200"
      :title="t('dashboard.car_card_odometer')"
    >
      <TruckIcon :class="iconClass" class="text-gray-500 dark:text-gray-400 flex-shrink-0" />
      <span class="font-medium">{{ odometerLabel }}</span>
    </div>

    <!-- Power -->
    <div
      v-if="powerLabel"
      class="inline-flex items-center gap-1.5 whitespace-nowrap text-gray-700 dark:text-gray-200"
      :title="t('dashboard.car_card_power')"
    >
      <Cog6ToothIcon :class="iconClass" class="text-gray-500 dark:text-gray-400 flex-shrink-0" />
      <span class="font-medium">{{ powerLabel }}</span>
    </div>

    <!-- Year -->
    <div
      v-if="yearLabel"
      class="inline-flex items-center gap-1.5 whitespace-nowrap text-gray-700 dark:text-gray-200"
      :title="t('dashboard.car_card_year')"
    >
      <CalendarIcon :class="iconClass" class="text-gray-500 dark:text-gray-400 flex-shrink-0" />
      <span class="font-medium">{{ yearLabel }}</span>
    </div>

    <!-- Heat pump (only when true) -->
    <div
      v-if="hasHeatPump"
      class="inline-flex items-center gap-1.5 whitespace-nowrap text-gray-700 dark:text-gray-200"
      :title="t('dashboard.car_card_heat_pump')"
    >
      <FireIcon :class="iconClass" class="text-orange-500 flex-shrink-0" />
      <span class="font-medium">{{ t('dashboard.car_card_heat_pump') }}</span>
    </div>

    <!-- Business car (only when true) -->
    <div
      v-if="isBusinessCar"
      class="inline-flex items-center gap-1.5 whitespace-nowrap text-gray-700 dark:text-gray-200"
      :title="t('dashboard.car_card_business_car')"
    >
      <BriefcaseIcon :class="iconClass" class="text-blue-500 flex-shrink-0" />
      <span class="font-medium">{{ t('dashboard.car_card_business_car') }}</span>
    </div>
  </div>
</template>
