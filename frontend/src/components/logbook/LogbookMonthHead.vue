<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { monthName } from '../../utils/logbook'

/** Kopf eines Monatskapitels im Logbuch: Name, rechts ein Kennwert, darunter die Summen und ein optionaler Satz. */
const props = defineProps<{ monthKey: string; line: string; right?: string | null }>()
const { locale } = useI18n()
const name = computed(() => monthName(props.monthKey, locale.value))
</script>

<template>
  <header class="border-b border-gray-200 px-4 py-2.5 dark:border-gray-700" data-testid="logbook-month-head">
    <div class="flex items-baseline justify-between gap-3">
      <h2 class="text-[19px] font-bold tracking-tight">{{ name }}</h2>
      <span v-if="right" class="text-[13px] font-semibold tabular-nums">{{ right }}</span>
    </div>
    <p v-if="line" class="text-[13px] tabular-nums text-gray-600 dark:text-gray-300">{{ line }}</p>
    <slot />
  </header>
</template>
