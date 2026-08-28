<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { CostMode } from '../../composables/useDashboardStats'

const props = defineProps<{ mode: CostMode }>()
defineEmits<{ toggle: [] }>()

const { t } = useI18n()

const label = computed(() => props.mode === 'full'
  ? t('dashboard.cost_mode_full')
  : t('dashboard.cost_mode_energy'))
</script>

<template>
  <!--
    Ein einzelnes Tap-Target statt zweier Chips: die Kachel ist auf Mobile nur eine halbe
    Spalte breit. Der Chip zeigt den aktiven Modus und wechselt beim Tippen - dadurch ist
    zugleich benannt, worauf sich der Wert darueber bezieht.
  -->
  <button
    type="button"
    class="flex-shrink-0 px-1.5 py-0.5 rounded-sm text-[10px] font-medium border whitespace-nowrap transition focus:outline-none focus-visible:ring-2 focus-visible:ring-pink-500"
    :class="mode === 'full'
      ? 'bg-indigo-50 dark:bg-indigo-900/40 border-indigo-300 dark:border-indigo-700 text-indigo-700 dark:text-indigo-300'
      : 'bg-gray-50 dark:bg-gray-700 border-gray-300 dark:border-gray-600 text-gray-600 dark:text-gray-300'"
    :aria-label="t('dashboard.cost_mode_toggle_aria', { mode: label })"
    @click.stop="$emit('toggle')"
  >
    {{ label }}
  </button>
</template>
