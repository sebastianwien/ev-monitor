<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ChevronRightIcon } from '@heroicons/vue/24/outline'

/**
 * Entry point to the battery-health detail sheet.
 *
 * Styled as a filled, bordered pill rather than tinted text: an enclosed surface is what
 * makes a control read as pressable. Plain coloured text next to other spec values gets
 * skipped over, however well-chosen the colour is.
 *
 * Used on the dashboard card and in the vehicle list, so both stay in sync.
 */
const props = defineProps<{
  /** Current SoH in percent, or null when nothing has been determined yet. */
  sohPercent: number | null
  /** Tighter padding for the dense mobile spec strip. */
  compact?: boolean
}>()

defineEmits<{ click: [] }>()

const { t } = useI18n()

const hasValue = computed(() => props.sohPercent != null)

/**
 * Rounded to whole percent. The estimate is not precise to a decimal, and printing one
 * claims an accuracy the method cannot back - the label carries a leading "~" to match.
 */
const rounded = computed(() => (props.sohPercent == null ? null : Math.round(props.sohPercent)))

const label = computed(() =>
  hasValue.value ? t('soh.pill_label', { pct: rounded.value }) : t('soh.badge_none'),
)

const ariaLabel = computed(() =>
  hasValue.value ? t('soh.open_aria', { soh: rounded.value }) : t('soh.open_aria_unknown'),
)
</script>

<template>
  <button
    type="button"
    :aria-label="ariaLabel"
    class="inline-flex items-center gap-1 whitespace-nowrap rounded-full border font-semibold transition-colors focus:outline-hidden focus-visible:ring-2 focus-visible:ring-offset-1 focus-visible:ring-amber-500 dark:focus-visible:ring-offset-gray-800"
    :class="[
      compact ? 'px-2 py-0.5 text-[11px]' : 'px-2.5 py-1 text-xs',
      hasValue
        ? 'border-amber-300 bg-amber-50 text-amber-800 hover:border-amber-400 hover:bg-amber-100 dark:border-amber-700/70 dark:bg-amber-900/30 dark:text-amber-300 dark:hover:bg-amber-900/50'
        : 'border-gray-300 bg-gray-50 text-gray-600 hover:border-gray-400 hover:bg-gray-100 dark:border-gray-600 dark:bg-gray-700/50 dark:text-gray-300 dark:hover:bg-gray-700',
    ]"
    @click.stop="$emit('click')"
  >
    {{ label }}
    <ChevronRightIcon class="h-3.5 w-3.5 flex-none opacity-70" aria-hidden="true" />
  </button>
</template>
