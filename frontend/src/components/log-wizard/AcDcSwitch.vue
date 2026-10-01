<script setup lang="ts">
import { useI18n } from 'vue-i18n'
/** Ladeart als Kippschalter: ein Knopf gleitet unter die aktive Hälfte, beide Hälften bleiben tippbar (44 px hoch). */
const model = defineModel<'AC' | 'DC'>({ required: true })
const { t } = useI18n()
</script>

<template>
  <div role="radiogroup" :aria-label="t('logwizard.charging_type')"
    class="relative grid grid-cols-2 h-11 w-[5.5rem] flex-shrink-0 rounded-full bg-gray-200 dark:bg-gray-700 p-1 select-none">
    <span aria-hidden="true" class="absolute top-1 bottom-1 left-1 w-[calc(50%-0.25rem)] rounded-full bg-white dark:bg-gray-500 shadow transition-transform duration-200 ease-out"
      :class="model === 'DC' ? 'translate-x-full' : ''" />
    <button v-for="ct in (['AC', 'DC'] as const)" :key="ct" type="button" role="radio" :aria-checked="model === ct"
      :data-testid="`charging-type-${ct.toLowerCase()}`" @click="model = ct"
      :class="['relative z-10 rounded-full text-xs font-bold tracking-wide transition-colors', model === ct ? 'text-indigo-700 dark:text-white' : 'text-gray-500 dark:text-gray-300']">
      {{ ct }}
    </button>
  </div>
</template>
