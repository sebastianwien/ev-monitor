<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { SunIcon, ChevronDownIcon } from '@heroicons/vue/24/outline'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import { useSlideTransition } from '../../composables/useSlideTransition'

const { onEnter, onAfterEnter, onLeave, onAfterLeave } = useSlideTransition()

const LS_KEY = 'range_card_collapsed'
const collapsed = ref(localStorage.getItem(LS_KEY) === 'true')
function toggleCollapsed() {
  collapsed.value = !collapsed.value
  localStorage.setItem(LS_KEY, String(collapsed.value))
}

const props = defineProps<{
  batteryCapacityKwh: number
  summerConsumption: number | null
  winterConsumption: number | null
  avgConsumption: number | null
}>()

const { t } = useI18n()
const { formatConsumption, consumptionUnitLabel, distanceUnitLabel } = useLocaleFormat()

type Season = 'summer' | 'winter'

const activeTab = ref<Season>(
  props.summerConsumption ? 'summer' : 'winter'
)

const rangeWindows = [
  { label: '100 → 0 %', socMax: 100, socMin: 0 },
  { label: '90 → 10 %', socMax: 90, socMin: 10, recommended: true },
  { label: '80 → 20 %', socMax: 80, socMin: 20 },
]

const hasSeasonal = computed(() => props.summerConsumption || props.winterConsumption)

const activeConsumption = computed<number | null>(() => {
  if (!hasSeasonal.value) return props.avgConsumption
  if (activeTab.value === 'summer') return props.summerConsumption
  return props.winterConsumption
})

function calcRange(socMax: number, socMin: number): string {
  if (!activeConsumption.value) return '–'
  const usableKwh = props.batteryCapacityKwh * (socMax - socMin) / 100
  return '~' + Math.round(usableKwh / activeConsumption.value * 100)
}

const tabColor = computed(() => {
  if (!hasSeasonal.value) return 'text-gray-700 dark:text-gray-200'
  if (activeTab.value === 'summer') return 'text-amber-600 dark:text-amber-400'
  return 'text-blue-600 dark:text-blue-300'
})
</script>

<template>
  <div class="bg-white dark:bg-gray-800 border-2 border-gray-900 dark:border-white rounded-sm shadow-[4px_4px_0_0_#030712] dark:shadow-[4px_4px_0_0_#ffffff] overflow-hidden">

    <!-- Titel -->
    <button @click="toggleCollapsed"
      class="w-full px-4 py-3 flex items-center justify-between sm:justify-center border-b-2 border-gray-300 dark:border-gray-700 sm:cursor-default">
      <span class="sm:hidden w-5"></span>
      <h3 class="text-[11px] font-bold uppercase tracking-[0.14em] text-gray-700 dark:text-gray-200">{{ t('dashboard.real_range_title') }}</h3>
      <ChevronDownIcon
        class="w-4 h-4 text-gray-400 transition-transform duration-200 sm:hidden"
        :class="{ 'rotate-180': !collapsed }" />
    </button>

    <!-- Content: auf Mobile einklappbar, auf Desktop immer sichtbar -->
    <Transition @enter="onEnter" @after-enter="onAfterEnter" @leave="onLeave" @after-leave="onAfterLeave">
    <div v-show="!collapsed" class="sm:!block">

    <!-- Tabs (nur wenn saisonale Daten) -->
    <div v-if="hasSeasonal" class="flex border-b-2 border-gray-300 dark:border-gray-700 divide-x-2 divide-gray-300 dark:divide-gray-700">
      <button v-if="summerConsumption"
        @click="activeTab = 'summer'"
        :class="['flex-1 py-2 text-[11px] font-bold uppercase tracking-wider flex items-center justify-center gap-1 transition',
          activeTab === 'summer'
            ? 'text-amber-700 dark:text-amber-400 bg-amber-50 dark:bg-amber-950/30 border-b-2 border-amber-500 -mb-0.5'
            : 'text-gray-500 dark:text-gray-500 hover:text-gray-700 dark:hover:text-gray-300']">
        <SunIcon class="w-3.5 h-3.5" />
        {{ t('dashboard.range_summer') }}
        <span class="font-medium opacity-70 normal-case tracking-normal">({{ formatConsumption(summerConsumption!, { showUnit: false }) }})</span>
      </button>
      <button v-if="winterConsumption"
        @click="activeTab = 'winter'"
        :class="['flex-1 py-2 text-[11px] font-bold uppercase tracking-wider flex items-center justify-center gap-1 transition',
          activeTab === 'winter'
            ? 'text-blue-700 dark:text-blue-300 bg-blue-50 dark:bg-blue-950/30 border-b-2 border-blue-500 -mb-0.5'
            : 'text-gray-500 dark:text-gray-500 hover:text-gray-700 dark:hover:text-gray-300']">
        <svg class="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor">
          <path stroke-linecap="round" stroke-linejoin="round" d="M2.25 15a4.5 4.5 0 004.5 4.5H18a3.75 3.75 0 001.332-7.257 3 3 0 00-3.758-3.848 5.25 5.25 0 00-10.233 2.33A4.502 4.502 0 002.25 15z" />
        </svg>
        {{ t('dashboard.range_winter') }}
        <span class="font-medium opacity-70 normal-case tracking-normal">({{ formatConsumption(winterConsumption!, { showUnit: false }) }})</span>
      </button>
    </div>

    <!-- Tabelle -->
    <div class="px-4 pt-3 pb-4">
      <div v-if="!hasSeasonal" class="text-[10px] font-bold uppercase tracking-wider text-gray-500 dark:text-gray-400 mb-2 text-right">
        Ø {{ formatConsumption(avgConsumption!, { showUnit: false }) }} {{ consumptionUnitLabel() }}
      </div>
      <table class="w-full text-sm">
        <tbody class="divide-y-2 divide-gray-200 dark:divide-gray-700">
          <tr v-for="w in rangeWindows" :key="w.label">
            <td class="py-2 font-bold text-gray-800 dark:text-gray-200 whitespace-nowrap">
              {{ w.label }}
              <span v-if="w.recommended" class="ml-1 text-[10px] uppercase tracking-wider text-gray-500 dark:text-gray-500 font-bold">(empfohlen)</span>
            </td>
            <td :class="['py-2 text-right font-bold tabular-nums whitespace-nowrap', tabColor]">
              {{ calcRange(w.socMax, w.socMin) }} {{ distanceUnitLabel() }}
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    </div>
    </Transition>
  </div>
</template>

