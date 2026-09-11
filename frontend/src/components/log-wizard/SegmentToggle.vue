<script setup lang="ts" generic="T extends string">
defineProps<{ options: { value: T; label: string; testid?: string }[] }>()
const model = defineModel<T>({ required: true })
</script>

<template>
  <div class="grid gap-1 bg-gray-100 dark:bg-gray-700 p-1 rounded-sm" :style="{ gridTemplateColumns: `repeat(${options.length}, minmax(0, 1fr))` }" role="radiogroup">
    <button v-for="o in options" :key="o.value" type="button" role="radio" :aria-checked="model === o.value" :data-testid="o.testid"
      @click="model = o.value"
      :class="['py-2 text-sm rounded-sm transition', model === o.value ? 'bg-white dark:bg-gray-500 text-indigo-700 dark:text-white font-semibold shadow-sm' : 'text-gray-500 dark:text-gray-300']">
      {{ o.label }}
    </button>
  </div>
</template>
