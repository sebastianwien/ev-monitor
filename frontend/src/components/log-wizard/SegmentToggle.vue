<script setup lang="ts" generic="T extends string">
import { computed } from 'vue'
const props = defineProps<{ options: { value: T; label: string; testid?: string }[] }>()
const model = defineModel<T>({ required: true })
const activeIndex = computed(() => props.options.findIndex(o => o.value === model.value))
</script>

<template>
  <!-- CI-Segment-Schalter: eine Kapsel mit Kante, die aktive Fläche gleitet unter das gewählte Segment -->
  <div class="relative grid rounded-full border-2 border-edge bg-white p-0.5 dark:bg-gray-900" :style="{ gridTemplateColumns: `repeat(${options.length}, minmax(0, 1fr))` }" role="radiogroup">
    <span v-if="activeIndex >= 0" aria-hidden="true" class="absolute inset-y-0.5 left-0.5 rounded-full bg-gray-900 transition-transform duration-300 ease-out motion-reduce:transition-none dark:bg-gray-100"
      :style="{ width: `calc((100% - 0.25rem) / ${options.length})`, transform: `translateX(${activeIndex * 100}%)` }" />
    <button v-for="o in options" :key="o.value" type="button" role="radio" :aria-checked="model === o.value" :data-testid="o.testid"
      @click="model = o.value"
      :class="['relative z-10 min-h-10 rounded-full text-[14px] font-semibold transition-colors', model === o.value ? 'text-white dark:text-gray-900' : 'text-gray-600 hover:text-gray-900 dark:text-gray-300 dark:hover:text-white']">
      {{ o.label }}
    </button>
  </div>
</template>
