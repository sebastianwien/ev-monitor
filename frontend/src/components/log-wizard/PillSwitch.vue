<script setup lang="ts" generic="T extends string">
/**
 * Kippschalter mit zwei Hälften: ein Knopf gleitet unter die aktive, beide bleiben sichtbar und tippbar (44 px hoch).
 * Breite setzt der Aufrufer (AC/DC 5.5rem, € | ct/kWh breiter).
 */
defineProps<{ options: [{ value: T; label: string; testid?: string }, { value: T; label: string; testid?: string }]; label: string }>()
const model = defineModel<T>({ required: true })
</script>

<template>
  <div role="radiogroup" :aria-label="label"
    class="relative grid grid-cols-2 h-11 flex-shrink-0 rounded-full bg-gray-200 dark:bg-gray-700 p-1 select-none">
    <span aria-hidden="true" class="absolute top-1 bottom-1 left-1 w-[calc(50%-0.25rem)] rounded-full bg-white dark:bg-gray-500 shadow transition-transform duration-200 ease-out"
      :class="model === options[1].value ? 'translate-x-full' : ''" />
    <button v-for="o in options" :key="o.value" type="button" role="radio" :aria-checked="model === o.value"
      :data-testid="o.testid" @click="model = o.value"
      :class="['relative z-10 rounded-full text-xs font-bold tracking-wide whitespace-nowrap transition-colors', model === o.value ? 'text-indigo-700 dark:text-white' : 'text-gray-500 dark:text-gray-300']">
      {{ o.label }}
    </button>
  </div>
</template>
