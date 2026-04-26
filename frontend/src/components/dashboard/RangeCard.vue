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
  <div class="bg-white dark:bg-gray-700 rounded-lg border border-gray-200 dark:border-gray-600 shadow-sm overflow-hidden">

    <!-- Titel -->
    <button @click="toggleCollapsed"
      class="w-full px-4 py-3 flex items-center justify-between sm:justify-center border-b border-gray-100 dark:border-gray-600 sm:cursor-default">
      <span class="sm:hidden w-5"></span>
      <h3 class="text-sm font-semibold text-gray-700 dark:text-gray-300">{{ t('dashboard.real_range_title') }}</h3>
      <ChevronDownIcon
        class="w-4 h-4 text-gray-400 transition-transform duration-200 sm:hidden"
        :class="{ 'rotate-180': !collapsed }" />
    </button>

    <!-- Content: auf Mobile einklappbar, auf Desktop immer sichtbar -->
    <Transition @enter="onEnter" @after-enter="onAfterEnter" @leave="onLeave" @after-leave="onAfterLeave">
    <div v-show="!collapsed" class="sm:!block">

    <!-- Tabs (nur wenn saisonale Daten) -->
    <div v-if="hasSeasonal" class="flex border-b border-gray-100 dark:border-gray-600">
      <button v-if="summerConsumption"
        @click="activeTab = 'summer'"
        :class="['flex-1 py-2 text-xs font-medium flex items-center justify-center gap-1 transition',
          activeTab === 'summer'
            ? 'text-amber-600 dark:text-amber-400 border-b-2 border-amber-500 bg-amber-50 dark:bg-amber-900/20'
            : 'text-gray-400 dark:text-gray-500 hover:text-gray-600 dark:hover:text-gray-300']">
        <SunIcon class="w-3.5 h-3.5" />
        {{ t('dashboard.range_summer') }}
        <span class="font-normal opacity-70">({{ formatConsumption(summerConsumption!, { showUnit: false }) }})</span>
      </button>
      <button v-if="winterConsumption"
        @click="activeTab = 'winter'"
        :class="['flex-1 py-2 text-xs font-medium flex items-center justify-center gap-1 transition',
          activeTab === 'winter'
            ? 'text-blue-600 dark:text-blue-300 border-b-2 border-blue-500 bg-blue-50 dark:bg-blue-900/20'
            : 'text-gray-400 dark:text-gray-500 hover:text-gray-600 dark:hover:text-gray-300']">
        <svg class="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor">
          <path stroke-linecap="round" stroke-linejoin="round" d="M2.25 15a4.5 4.5 0 004.5 4.5H18a3.75 3.75 0 001.332-7.257 3 3 0 00-3.758-3.848 5.25 5.25 0 00-10.233 2.33A4.502 4.502 0 002.25 15z" />
        </svg>
        {{ t('dashboard.range_winter') }}
        <span class="font-normal opacity-70">({{ formatConsumption(winterConsumption!, { showUnit: false }) }})</span>
      </button>
    </div>

    <!-- Tabelle -->
    <div class="px-4 pt-3 pb-4">
      <div v-if="!hasSeasonal" class="text-xs text-gray-400 dark:text-gray-500 mb-2 text-right">
        Ø {{ formatConsumption(avgConsumption!, { showUnit: false }) }} {{ consumptionUnitLabel() }}
      </div>
      <table class="w-full text-sm">
        <tbody class="divide-y divide-gray-100 dark:divide-gray-600">
          <tr v-for="w in rangeWindows" :key="w.label">
            <td class="py-2 font-medium text-gray-800 dark:text-gray-200 whitespace-nowrap">
              {{ w.label }}
              <span v-if="w.recommended" class="ml-1 text-xs text-gray-400 dark:text-gray-500 font-normal">(empfohlen)</span>
            </td>
            <td :class="['py-2 text-right font-bold whitespace-nowrap', tabColor]">
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

