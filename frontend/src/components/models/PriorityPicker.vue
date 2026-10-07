<template>
  <div class="grid gap-1.5">
    <span :id="labelId" class="text-[12.5px] text-gray-500 dark:text-gray-400">{{ t('models_ranking.priority.question') }}</span>
    <div class="flex flex-wrap gap-2" role="group" :aria-labelledby="labelId">
      <button
        v-for="p in PRIORITIES"
        :key="p"
        type="button"
        class="inline-flex min-h-10 items-center rounded-full px-3.5 text-sm font-medium"
        :class="modelValue === p
          ? 'bg-gray-900 text-white dark:bg-gray-100 dark:text-gray-900'
          : 'bg-gray-200/70 text-gray-800 hover:bg-gray-200 dark:bg-gray-800 dark:text-gray-200 dark:hover:bg-gray-700'"
        :aria-pressed="modelValue === p"
        :data-testid="`priority-${p}`"
        @click="emit('update:modelValue', modelValue === p ? null : p)"
      >{{ t(`models_ranking.priority.${p}`) }}</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useId } from 'vue'
import { useI18n } from 'vue-i18n'
import { PRIORITIES, type Priority } from '../../composables/useModelRanking'

defineProps<{ modelValue: Priority | null }>()
const emit = defineEmits<{ 'update:modelValue': [value: Priority | null] }>()
const { t } = useI18n()
const labelId = useId()
</script>
