<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowsRightLeftIcon } from '@heroicons/vue/24/outline'
import type { CostMode } from '../../composables/useDashboardStats'

const props = defineProps<{ mode: CostMode }>()
defineEmits<{ toggle: [] }>()

const { t } = useI18n()

const label = computed(() => {
  if (props.mode === 'fixed') return t('dashboard.metric_avg_cost_fixed')
  if (props.mode === 'total') return t('dashboard.metric_avg_cost_total')
  return t('dashboard.metric_avg_cost')
})
</script>

<template>
  <!--
    Icon-only: in der Titelzeile stehen nur 135px (Desktop) bzw. 145px (Mobile) zur Verfuegung,
    ein beschrifteter Chip laeuft dort ueber. Welcher der drei Modi aktiv ist, sagt stattdessen
    der Kachel-Titel ("Ø Kosten" / "Ø Fixkosten" / "Ø Gesamt") - er wechselt sichtbar beim
    Tippen. Pfeil-Icon und harter Bodenschatten (visuelle Sprache von btn-3d, nur kleiner)
    machen den Schalter als solchen kenntlich.
  -->
  <button
    type="button"
    class="relative flex items-center flex-shrink-0 p-0.5 rounded-sm border
           before:absolute before:-inset-2 before:content-['']
           shadow-[2px_2px_0_0_rgba(0,0,0,0.15)] dark:shadow-[2px_2px_0_0_rgba(0,0,0,0.4)]
           active:shadow-none active:translate-x-[2px] active:translate-y-[2px]
           transition-all duration-75 focus:outline-none focus-visible:ring-2 focus-visible:ring-pink-500"
    :class="mode === 'energy'
      ? 'bg-white dark:bg-gray-700 border-gray-400 dark:border-gray-500 text-gray-700 dark:text-gray-200'
      : 'bg-indigo-100 dark:bg-indigo-800 border-indigo-400 dark:border-indigo-600 text-indigo-800 dark:text-indigo-100'"
    :aria-label="t('dashboard.cost_mode_toggle_aria', { mode: label })"
    @click.stop="$emit('toggle')"
  >
    <ArrowsRightLeftIcon class="w-3 h-3" />
  </button>
</template>
