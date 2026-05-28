<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

const props = defineProps<{
  avgConsumptionKwhPer100km: number
  totalDistanceKm: number
}>()

const { t } = useI18n()

// CO2 constants (g/km)
const VERBRENNER_G_PER_KM = 178       // 7.5 L/100km × 2.37 kg CO2/L Benzin
const STROMMIX_G_PER_KWH = 380        // dt. Strommix 2024 (Umweltbundesamt)
const OEKO_G_PER_KWH = 30             // Lifecycle Solar/Wind

const evStrommixGPerKm = computed(() =>
  (props.avgConsumptionKwhPer100km / 100) * STROMMIX_G_PER_KWH
)

const evOekoGPerKm = computed(() =>
  (props.avgConsumptionKwhPer100km / 100) * OEKO_G_PER_KWH
)

// CO2 gespart vs. Verbrenner (dt. Strommix), in kg
const savedKg = computed(() =>
  Math.round(((VERBRENNER_G_PER_KM - evStrommixGPerKm.value) * props.totalDistanceKm) / 1000)
)

// Bar widths — relative to Verbrenner as 100%
const strommixPct = computed(() =>
  Math.round((evStrommixGPerKm.value / VERBRENNER_G_PER_KM) * 100)
)
const oekoPct = computed(() =>
  Math.round((evOekoGPerKm.value / VERBRENNER_G_PER_KM) * 100)
)

function fmtGPerKm(g: number): string {
  return Math.round(g) + ' g/km'
}

function fmtKg(kg: number): string {
  if (Math.abs(kg) >= 1000) return (kg / 1000).toFixed(1) + ' t'
  return kg + ' kg'
}
</script>

<template>
  <div class="bg-white dark:bg-gray-700 rounded-sm border-2 border-gray-300 dark:border-gray-600 overflow-hidden shadow-[2px_2px_0_0_#d1d5db] dark:shadow-[2px_2px_0_0_#4b5563] flex flex-col">

    <!-- Header -->
    <div class="px-4 py-3 border-b border-gray-100 dark:border-gray-600 text-sm font-semibold text-gray-800 dark:text-gray-200 text-center">
      {{ t('dashboard.co2_title') }}
    </div>

    <div class="flex-1 p-4 flex flex-col gap-3">

      <!-- Savings highlight -->
      <div class="text-center">
        <span class="text-xs text-gray-500 dark:text-gray-400">{{ t('dashboard.co2_saved_vs_verbrenner') }}</span>
        <div class="text-2xl font-bold text-green-600 dark:text-green-400 tabular-nums">{{ fmtKg(savedKg) }}</div>
        <span class="text-xs text-gray-400 dark:text-gray-500">{{ t('dashboard.co2_saved_basis', { km: Math.round(totalDistanceKm).toLocaleString() }) }}</span>
      </div>

      <!-- Comparison bars -->
      <div class="flex flex-col gap-2.5">

        <!-- Verbrenner -->
        <div>
          <div class="flex justify-between mb-1">
            <span class="text-xs text-gray-500 dark:text-gray-400">{{ t('dashboard.co2_combustion') }}</span>
            <span class="text-xs font-semibold text-red-500 dark:text-red-400 tabular-nums">{{ fmtGPerKm(VERBRENNER_G_PER_KM) }}</span>
          </div>
          <div class="h-2 w-full rounded-full bg-red-400" />
        </div>

        <!-- EV dt. Strommix -->
        <div>
          <div class="flex justify-between mb-1">
            <span class="text-xs text-gray-500 dark:text-gray-400">{{ t('dashboard.co2_strommix') }}</span>
            <span class="text-xs font-semibold text-yellow-600 dark:text-yellow-400 tabular-nums">{{ fmtGPerKm(evStrommixGPerKm) }}</span>
          </div>
          <div class="h-2 w-full rounded-full bg-gray-200 dark:bg-gray-600">
            <div
              class="h-full rounded-full bg-yellow-400 transition-all duration-500"
              :style="{ width: strommixPct + '%' }"
            />
          </div>
        </div>

        <!-- EV 100% Oeko -->
        <div>
          <div class="flex justify-between mb-1">
            <span class="text-xs text-gray-500 dark:text-gray-400">{{ t('dashboard.co2_oeko') }}</span>
            <span class="text-xs font-semibold text-green-600 dark:text-green-400 tabular-nums">{{ fmtGPerKm(evOekoGPerKm) }}</span>
          </div>
          <div class="h-2 w-full rounded-full bg-gray-200 dark:bg-gray-600">
            <div
              class="h-full rounded-full bg-green-500 transition-all duration-500"
              :style="{ width: oekoPct + '%' }"
            />
          </div>
        </div>

      </div>

      <!-- Footnote -->
      <p class="text-xs text-gray-400 dark:text-gray-500 leading-snug">
        {{ t('dashboard.co2_footnote') }}
      </p>

    </div>
  </div>
</template>
