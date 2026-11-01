<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ChevronDownIcon } from '@heroicons/vue/24/outline'
import { useSlideTransition } from '../../composables/useSlideTransition'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import type { Co2Statistics } from '../../composables/useDashboardStats'

const { onEnter, onAfterEnter, onLeave, onAfterLeave } = useSlideTransition()
const LS_KEY = 'co2_card_collapsed'
const collapsed = ref(localStorage.getItem(LS_KEY) === 'true')
function toggleCollapsed() {
  collapsed.value = !collapsed.value
  localStorage.setItem(LS_KEY, String(collapsed.value))
}

/** CO₂-Bilanz aus der API (Benziner der Fahrzeugklasse gegen den geladenen Netzstrom), nur mit Strecke. */
const props = defineProps<{
  co2: Co2Statistics
}>()

const { t } = useI18n()
const { formatDecimal } = useLocaleFormat()

const petrolGPerKm = computed(() => (props.co2.petrolKg / props.co2.distanceKm) * 1000)
const evGPerKm = computed(() => (props.co2.electricityKg / props.co2.distanceKm) * 1000)
const savedKg = computed(() => Math.round(props.co2.savedKg))

// Balkenbreite relativ zum Benziner als 100 %
const evPct = computed(() => Math.min(100, Math.round((evGPerKm.value / petrolGPerKm.value) * 100)))

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
    <button @click="toggleCollapsed" class="sm:hidden w-full px-4 py-3 flex items-center justify-between border-b border-gray-100 dark:border-gray-600">
      <div class="w-4 shrink-0"></div>
      <span class="flex-1 text-center text-sm font-semibold text-gray-800 dark:text-gray-200">{{ t('dashboard.co2_title') }}</span>
      <ChevronDownIcon class="w-4 h-4 text-gray-400 shrink-0 transition-transform duration-200" :class="{ 'rotate-180': !collapsed }" />
    </button>
    <div class="hidden sm:block px-4 py-3 border-b border-gray-100 dark:border-gray-600 text-sm font-semibold text-gray-800 dark:text-gray-200 text-center">
      {{ t('dashboard.co2_title') }}
    </div>

    <Transition @enter="onEnter" @after-enter="onAfterEnter" @leave="onLeave" @after-leave="onAfterLeave">
    <div v-show="!collapsed" class="sm:!block">
    <div class="flex-1 p-4 flex flex-col gap-3">

      <!-- Savings highlight -->
      <div class="flex items-baseline justify-center gap-1.5 flex-wrap">
        <span class="text-sm text-gray-500 dark:text-gray-400">{{ t('dashboard.co2_saved_prefix', { km: Math.round(co2.distanceKm).toLocaleString() }) }}</span>
        <span class="text-2xl font-bold text-indigo-600 dark:text-indigo-400 tabular-nums">{{ fmtKg(savedKg) }} CO2</span>
        <span class="text-sm text-gray-500 dark:text-gray-400">{{ t('dashboard.co2_saved_vs_verbrenner') }}</span>
      </div>

      <!-- Comparison bars -->
      <div class="flex flex-col gap-2.5">

        <!-- Verbrenner der Fahrzeugklasse -->
        <div>
          <div class="flex justify-between mb-1">
            <span class="text-sm text-gray-500 dark:text-gray-400">{{ t('dashboard.co2_combustion') }} <span class="text-xs text-gray-400 dark:text-gray-500">{{ t('dashboard.co2_combustion_detail', { liters: formatDecimal(co2.petrolLitersPer100km, 1) }) }}</span></span>
            <span class="text-sm font-semibold text-red-500 dark:text-red-400 tabular-nums">{{ fmtGPerKm(petrolGPerKm) }}</span>
          </div>
          <div class="h-2 w-full rounded-full bg-red-400" />
        </div>

        <!-- EV mit dem deutschen Strommix -->
        <div>
          <div class="flex justify-between mb-1">
            <span class="text-sm text-gray-500 dark:text-gray-400">{{ t('dashboard.co2_strommix') }} <span class="text-xs text-gray-400 dark:text-gray-500">{{ t('dashboard.co2_strommix_detail') }}</span></span>
            <span class="text-sm font-semibold text-amber-500 dark:text-amber-400 tabular-nums whitespace-nowrap">{{ fmtGPerKm(evGPerKm) }}</span>
          </div>
          <div class="h-2 w-full rounded-full bg-gray-200 dark:bg-gray-600">
            <div
              class="h-full rounded-full bg-amber-400 transition-all duration-500"
              :style="{ width: evPct + '%' }"
            />
          </div>
        </div>

      </div>

    </div>
    </div>
    </Transition>
  </div>
</template>
