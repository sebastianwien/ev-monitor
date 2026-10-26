<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { CalendarIcon, ChevronDownIcon, ListBulletIcon } from '@heroicons/vue/24/outline'
import type { TimeRangeOption } from '../../composables/useTimeRangeOptions'
import ChoiceChip from '../shared/ChoiceChip.vue'

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

const dateInputClass = 'block w-full min-h-10 px-2.5 pr-7 py-1.5 text-sm border-2 border-gray-300 dark:border-gray-600 rounded-xl bg-white dark:bg-gray-900 text-gray-900 dark:text-gray-100 focus:outline-none focus:border-gray-900 dark:focus:border-gray-300 [&::-webkit-calendar-picker-indicator]:opacity-0 [&::-webkit-calendar-picker-indicator]:absolute [&::-webkit-calendar-picker-indicator]:right-0 [&::-webkit-calendar-picker-indicator]:w-8 [&::-webkit-calendar-picker-indicator]:h-full [&::-webkit-calendar-picker-indicator]:cursor-pointer'
</script>

<template>
  <div ref="root" class="relative">
    <button
      type="button"
      :data-testid="testId ? `${testId}-toggle` : undefined"
      :aria-expanded="open"
      @click.stop="open = !open"
      class="btn-3d cta-shadow-sm inline-flex min-h-10 items-center gap-1.5 whitespace-nowrap rounded-full border-2 border-edge bg-white px-3.5 text-sm font-semibold text-gray-800 transition-colors hover:bg-gray-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-green-600 dark:bg-white/10 dark:text-gray-200 dark:hover:bg-white/15">
      <CalendarIcon class="h-4 w-4 text-gray-500 dark:text-gray-400" aria-hidden="true" />
      <span>{{ currentLabel }}</span>
      <template v-if="currentGroupLabel">
        <span class="text-gray-300 dark:text-gray-500">·</span>
        <span>{{ currentGroupLabel }}</span>
      </template>
      <ChevronDownIcon class="h-3.5 w-3.5 text-gray-500 transition-transform duration-300 dark:text-gray-400" :class="open ? 'rotate-180 ease-out' : 'ease-in'" aria-hidden="true" />
    </button>
    <Transition name="dropdown">
      <div v-if="open"
        :data-testid="testId ? `${testId}-dropdown` : undefined"
        class="absolute left-1/2 -translate-x-1/2 top-full mt-2 z-40 w-80 max-w-[calc(100vw-2rem)] rounded-2xl bg-white edge-card p-3 dark:bg-gray-800"
        @click.stop>
        <p class="mb-2 text-[11.5px] font-semibold uppercase tracking-[0.06em] text-gray-500 dark:text-gray-400">{{ t('dashboard.time_range_label') }}</p>
        <div class="flex flex-wrap gap-1.5" :class="timeRange === 'CUSTOM' || groupByOptions ? 'mb-3' : ''">
          <ChoiceChip v-for="option in options" :key="option.value" size="sm" :active="timeRange === option.value" @click="select(option.value)">
            <CalendarIcon v-if="option.value === 'CUSTOM'" class="h-3.5 w-3.5" aria-hidden="true" />
            {{ option.shortLabel }}
          </ChoiceChip>
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
        <div v-if="groupByOptions" class="pt-3 border-t border-gray-200 dark:border-gray-700">
          <div class="flex items-center gap-1.5 mb-2">
            <ListBulletIcon class="h-3.5 w-3.5 text-gray-400 shrink-0" aria-hidden="true" />
            <span class="text-[11.5px] font-semibold uppercase tracking-[0.06em] text-gray-500 dark:text-gray-400">{{ t('dashboard.group_by_label') }}</span>
          </div>
          <div class="flex flex-wrap gap-1.5">
            <ChoiceChip v-for="opt in groupByOptions" :key="opt.value" size="sm" :active="groupBy === opt.value" @click="selectGroupBy(opt.value)">
              {{ opt.label }}
            </ChoiceChip>
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
