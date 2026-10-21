<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { PlusIcon } from '@heroicons/vue/24/outline'
import type { Car } from '../../api/carService'

/**
 * Fahrzeugwahl oberhalb des Heros, nur bei mehreren Autos: Chips mit Foto-Avatar,
 * wischbar auf Mobile, plus "Hinzufuegen" am Ende.
 */
defineProps<{
  cars: Car[]
  selectedId: string | null
  imageUrls: Record<string, string>
  modelLabel: (model: string) => string
}>()
const emit = defineEmits<{ select: [carId: string]; add: [] }>()
const { t } = useI18n()
</script>

<template>
  <nav :aria-label="t('cars.switcher_label')" class="flex gap-2 overflow-x-auto px-4 md:px-0 py-2 [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
    <button
      v-for="car in cars" :key="car.id" type="button"
      :aria-current="car.id === selectedId ? 'true' : undefined"
      :data-testid="`car-switch-${car.id}`"
      class="inline-flex shrink-0 items-center gap-2 min-h-11 rounded-full border pl-1.5 pr-4 text-sm font-medium transition"
      :class="car.id === selectedId
        ? 'border-indigo-500 bg-indigo-50 text-gray-900 dark:bg-indigo-500/15 dark:text-gray-100'
        : 'border-gray-300 bg-white text-gray-600 hover:bg-gray-50 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-300 dark:hover:bg-gray-700'"
      @click="emit('select', car.id)">
      <span class="h-8 w-8 overflow-hidden rounded-full bg-gradient-to-br from-indigo-900 to-indigo-500">
        <img v-if="imageUrls[car.id]" :src="imageUrls[car.id]" alt="" class="h-full w-full object-cover" />
      </span>
      <span class="whitespace-nowrap">{{ modelLabel(car.model) }}<span v-if="car.trim" class="ml-1 font-normal text-gray-500 dark:text-gray-400">{{ car.trim }}</span></span>
    </button>
    <button type="button"
      class="inline-flex shrink-0 items-center gap-1.5 min-h-11 rounded-full border border-dashed border-gray-400 px-4 text-sm font-medium text-gray-600 hover:bg-gray-50 dark:border-gray-500 dark:text-gray-300 dark:hover:bg-gray-700 transition"
      @click="emit('add')">
      <PlusIcon class="h-4 w-4" aria-hidden="true" />{{ t('cars.switcher_add') }}
    </button>
  </nav>
</template>
