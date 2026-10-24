<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import ActionChip from '../shared/ActionChip.vue'

/**
 * Entry point to the battery-health detail sheet.
 *
 * Rendered as an ActionChip, so it looks like every other chip that opens something.
 * Used on the dashboard card and in the vehicle hero, so both stay in sync.
 */
const props = defineProps<{
  /** Current SoH in percent, or null when nothing has been determined yet. */
  sohPercent: number | null
  /** sm for the dense mobile spec strip, lg for the vehicle hero. */
  size?: 'sm' | 'md' | 'lg'
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
  <ActionChip :size="size ?? 'md'" :aria-label="ariaLabel" @click="$emit('click')">
    {{ label }}
  </ActionChip>
</template>
