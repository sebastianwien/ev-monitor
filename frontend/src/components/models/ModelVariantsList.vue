<template>
  <!-- One row per battery: prospectus against measurement, summer and winter range where measured -->
  <ul class="grid gap-2" data-testid="variants-list">
    <li v-for="v in variants" :key="v.displayLabel ?? v.batteryCapacityKwh" class="grid gap-2 rounded-xl border border-gray-200 p-3 dark:border-gray-800">
      <p class="flex items-baseline justify-between gap-3">
        <b class="text-[14.5px] font-semibold text-gray-900 dark:text-gray-100">{{ v.displayLabel || v.variantName || `${formatNumber(v.batteryCapacityKwh)} kWh` }}</b>
        <span class="whitespace-nowrap text-[12.5px] text-gray-500 dark:text-gray-400">{{ formatNumber(v.batteryCapacityKwh) }} kWh</span>
      </p>
      <dl class="grid grid-cols-3 gap-2 text-[13px]">
        <div class="grid gap-0.5">
          <dt class="text-[11.5px] font-semibold uppercase tracking-[0.05em] text-gray-500 dark:text-gray-400">{{ ratingLabel }}</dt>
          <dd class="tabular-nums text-gray-800 dark:text-gray-200">{{ officialRange(v) }}</dd>
          <dd v-if="officialConsumption(v)" class="tabular-nums text-gray-500 dark:text-gray-400">{{ officialConsumption(v) }}</dd>
        </div>
        <div class="grid gap-0.5">
          <dt class="text-[11.5px] font-semibold uppercase tracking-[0.05em] text-gray-500 dark:text-gray-400">{{ t('model.page.variants_measured') }}</dt>
          <dd class="font-semibold tabular-nums text-gray-900 dark:text-gray-100">{{ v.realConsumptionKwhPer100km ? formatConsumption(v.realConsumptionKwhPer100km) : t('model.wltp_no_data') }}</dd>
          <dd v-if="v.realConsumptionTripCount" class="text-gray-500 dark:text-gray-400">{{ t('model.page.variants_trips', { n: formatNumber(v.realConsumptionTripCount) }) }}</dd>
        </div>
        <div class="grid gap-0.5">
          <dt class="text-[11.5px] font-semibold uppercase tracking-[0.05em] text-gray-500 dark:text-gray-400">{{ t('model.page.variants_summer_winter') }}</dt>
          <dd class="tabular-nums text-gray-800 dark:text-gray-200">{{ seasonRange(v) ?? '-' }}</dd>
        </div>
      </dl>
    </li>
  </ul>
</template>

<script lang="ts">
import type { SeasonalDistribution } from '../../api/publicModelService'

/** Normalized variant - same shape regardless of WLTP or EPA source */
export interface ActiveVariant {
  batteryCapacityKwh: number
  variantName: string | null
  displayLabel: string | null
  officialRangeKm: number
  officialRangeMinKm: number | null
  officialConsumptionKwhPer100km: number | null
  officialConsumptionMinKwhPer100km: number | null
  officialConsumptionMaxKwhPer100km: number | null
  realConsumptionKwhPer100km: number | null
  realConsumptionMinKwhPer100km: number | null
  realConsumptionMaxKwhPer100km: number | null
  realConsumptionTripCount: number | null
  estimatedConsumptionCount: number | null
  realConsumptionRangeSource: 'PER_DRIVER' | 'PER_TRIP' | null
  seasonalDistribution: SeasonalDistribution | null
}
</script>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import { rangeKm } from '../../utils/modelStory'

defineProps<{ variants: ActiveVariant[]; ratingLabel: string }>()
const { t } = useI18n()
const { formatNumber, formatDistance, formatConsumption } = useLocaleFormat()

function officialRange(v: ActiveVariant): string {
  return v.officialRangeMinKm && v.officialRangeMinKm !== v.officialRangeKm
    ? `${formatDistance(v.officialRangeMinKm, { showUnit: false })}-${formatDistance(v.officialRangeKm)}`
    : formatDistance(v.officialRangeKm)
}

function officialConsumption(v: ActiveVariant): string | null {
  if (v.officialConsumptionMinKwhPer100km && v.officialConsumptionMaxKwhPer100km && v.officialConsumptionMinKwhPer100km !== v.officialConsumptionMaxKwhPer100km) {
    return `${formatConsumption(v.officialConsumptionMinKwhPer100km, { showUnit: false })}-${formatConsumption(v.officialConsumptionMaxKwhPer100km)}`
  }
  return v.officialConsumptionKwhPer100km ? formatConsumption(v.officialConsumptionKwhPer100km) : null
}

/** Same rule as the data sheet: 90 % of the net battery, rounded to 10 km; both seasons or nothing */
function seasonRange(v: ActiveVariant): string | null {
  const s = v.seasonalDistribution
  const summer = rangeKm(v.batteryCapacityKwh, s?.summerConsumptionKwhPer100km ?? null)
  const winter = rangeKm(v.batteryCapacityKwh, s?.winterConsumptionKwhPer100km ?? null)
  if (summer == null || winter == null) return null
  return `${formatDistance(summer, { showUnit: false })} / ${formatDistance(winter)}`
}
</script>
