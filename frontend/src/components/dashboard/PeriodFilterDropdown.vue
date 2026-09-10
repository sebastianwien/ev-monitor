<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { CalendarIcon, ChevronDownIcon, ListBulletIcon } from '@heroicons/vue/24/outline'
import type { TimeRangeOption } from '../../composables/useTimeRangeOptions'

/**
 * Zeitraum-Picker fuer Dashboard und Log-Feed: Trigger mit aktueller Auswahl, darunter die
 * Chips (plus optional eine Gruppierung). Das Menue haengt zentriert unter dem Trigger und
 * bleibt auf schmalen Screens innerhalb des Viewports.
 */
const props = withDefaults(defineProps<{
  options: TimeRangeOption[]
  groupByOptions?: { value: string; label: string }[]
  /** Prefix fuer data-testid ("<prefix>-toggle" / "<prefix>-dropdown"). */
  testId?: string
}>(), { groupByOptions: undefined, testId: undefined })

const timeRange = defineModel<string>({ required: true })
const customStart = defineModel<string>('customStart', { default: '' })
const customEnd = defineModel<string>('customEnd', { default: '' })
const groupBy = defineModel<string>('groupBy', { default: '' })

const { t } = useI18n()
const open = ref(false)
const root = ref<HTMLElement | null>(null)

const currentLabel = computed(() =>
  props.options.find(o => o.value === timeRange.value)?.shortLabel ?? timeRange.value)
const currentGroupLabel = computed(() =>
  props.groupByOptions?.find(o => o.value === groupBy.value)?.label)

// CUSTOM-Toggle: merkt sich den vorherigen Zeitraum, damit Klick auf den aktiven
// CUSTOM-Chip zur letzten Auswahl zurueckspringt statt nur aufzuklappen.
const previousTimeRange = ref(timeRange.value !== 'CUSTOM' ? timeRange.value : 'LAST_3_MONTHS')
function select(value: string) {
  if (value === 'CUSTOM' && timeRange.value === 'CUSTOM') {
    timeRange.value = previousTimeRange.value
    open.value = false
    return
  }
  if (value !== 'CUSTOM') {
    previousTimeRange.value = value
    open.value = false
  }
  timeRange.value = value
}
function selectGroupBy(value: string) {
  groupBy.value = value
  open.value = false
}

function onClickOutside(e: MouseEvent) {
  if (!root.value?.contains(e.target as Node)) open.value = false
}
onMounted(() => document.addEventListener('click', onClickOutside))
onUnmounted(() => document.removeEventListener('click', onClickOutside))

const chipClass = (active: boolean) => [
  'px-2.5 py-1 text-xs font-medium rounded-sm transition',
  active ? 'bg-indigo-600 text-white' : 'bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-300 hover:bg-gray-200 dark:hover:bg-gray-600',
]
const dateInputClass = 'block w-full px-2 pr-7 py-1.5 text-xs border border-gray-300 dark:border-gray-600 rounded-sm bg-white dark:bg-gray-700 text-gray-800 dark:text-gray-200 focus:outline-none focus:ring-1 focus:ring-indigo-500 [&::-webkit-calendar-picker-indicator]:opacity-0 [&::-webkit-calendar-picker-indicator]:absolute [&::-webkit-calendar-picker-indicator]:right-0 [&::-webkit-calendar-picker-indicator]:w-8 [&::-webkit-calendar-picker-indicator]:h-full [&::-webkit-calendar-picker-indicator]:cursor-pointer'
</script>

<template>
  <div ref="root" class="relative">
    <button
      type="button"
      :data-testid="testId ? `${testId}-toggle` : undefined"
      :aria-expanded="open"
      @click.stop="open = !open"
      class="flex items-center gap-1.5 md:gap-2 px-2.5 md:px-4 py-1 md:py-1.5 rounded-sm border-2 border-gray-300 dark:border-gray-600 shadow-[2px_2px_0_0_#d1d5db] dark:shadow-[2px_2px_0_0_#374151] hover:shadow-[3px_3px_0_0_#9ca3af] dark:hover:shadow-[3px_3px_0_0_#4b5563] bg-white dark:bg-gray-700 hover:bg-gray-50 dark:hover:bg-gray-600 text-gray-600 md:text-gray-700 dark:text-gray-300 text-xs md:text-sm font-medium cursor-pointer transition">
      <CalendarIcon class="w-3 h-3 md:w-4 md:h-4 opacity-60" />
      <span>{{ currentLabel }}</span>
      <template v-if="currentGroupLabel">
        <span class="text-gray-300 dark:text-gray-500">·</span>
        <span>{{ currentGroupLabel }}</span>
      </template>
      <ChevronDownIcon class="w-3 h-3 md:w-3.5 md:h-3.5 opacity-50 transition-transform" :class="{ 'rotate-180': open }" />
    </button>
    <Transition name="dropdown">
      <div v-if="open"
        :data-testid="testId ? `${testId}-dropdown` : undefined"
        class="absolute left-1/2 -translate-x-1/2 top-full mt-1.5 z-40 w-72 max-w-[calc(100vw-2rem)] bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-sm shadow-[2px_2px_0_0_#d1d5db] dark:shadow-[2px_2px_0_0_#374151] p-3"
        @click.stop>
        <p class="text-[10px] font-semibold uppercase tracking-wide text-gray-400 dark:text-gray-500 mb-2">{{ t('dashboard.time_range_label') }}</p>
        <div class="flex flex-wrap gap-1.5" :class="timeRange === 'CUSTOM' || groupByOptions ? 'mb-3' : ''">
          <button v-for="option in options" :key="option.value" type="button" @click="select(option.value)"
            :class="['inline-flex items-center gap-1', ...chipClass(timeRange === option.value)]">
            <CalendarIcon v-if="option.value === 'CUSTOM'" class="w-3 h-3" />
            {{ option.shortLabel }}
          </button>
        </div>
        <div v-if="timeRange === 'CUSTOM'" class="flex items-center gap-2" :class="groupByOptions ? 'mb-3' : ''">
          <div class="flex-1 relative">
            <input type="date" v-model="customStart" :max="customEnd || undefined" :aria-label="t('dashboard.time_custom_from')" :class="dateInputClass" />
            <CalendarIcon class="pointer-events-none absolute right-2 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-gray-400" />
          </div>
          <span class="text-gray-400 text-xs shrink-0">→</span>
          <div class="flex-1 relative">
            <input type="date" v-model="customEnd" :min="customStart || undefined" :aria-label="t('dashboard.time_custom_to')" :class="dateInputClass" />
            <CalendarIcon class="pointer-events-none absolute right-2 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-gray-400" />
          </div>
        </div>
        <div v-if="groupByOptions" class="pt-2 border-t border-gray-100 dark:border-gray-700">
          <div class="flex items-center gap-1.5 mb-1.5">
            <ListBulletIcon class="h-3.5 w-3.5 text-gray-400 shrink-0" />
            <span class="text-xs text-gray-500 dark:text-gray-400">{{ t('dashboard.group_by_label') }}</span>
          </div>
          <div class="flex flex-wrap gap-1.5">
            <button v-for="opt in groupByOptions" :key="opt.value" type="button" @click="selectGroupBy(opt.value)"
              :class="chipClass(groupBy === opt.value)">
              {{ opt.label }}
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.dropdown-enter-active,
.dropdown-leave-active {
  transition: opacity 0.15s ease, transform 0.15s ease;
}
.dropdown-enter-from,
.dropdown-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
</style>
