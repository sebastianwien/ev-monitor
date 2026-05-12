<template>
  <div class="relative">
    <select
      :value="modelValue"
      @change="$emit('update:modelValue', ($event.target as HTMLSelectElement).value)"
      class="appearance-none w-full px-3 py-2.5 pr-10 border-2 border-gray-300 dark:border-gray-600 rounded-sm bg-white dark:bg-gray-900 text-gray-900 dark:text-white text-sm font-medium focus:outline-none focus:border-amber-500 dark:focus:border-amber-500 transition-colors"
    >
      <slot name="placeholder">
        <option value="" disabled>{{ t('cars.select_placeholder') }}</option>
      </slot>
      <option v-for="car in cars" :key="car.id" :value="car.id">
        {{ carLabel(car) }}
      </option>
    </select>
    <ChevronDownIcon class="w-4 h-4 absolute right-3 top-1/2 -translate-y-1/2 pointer-events-none text-gray-400 dark:text-gray-500" />
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { ChevronDownIcon } from '@heroicons/vue/24/outline'
import type { Car } from '../../api/carService'

const { t } = useI18n()

defineProps<{
  cars: Car[]
  modelValue: string | null
}>()

defineEmits<{ 'update:modelValue': [value: string] }>()

function enumToLabel(value: string | null | undefined): string {
  if (!value) return ''
  return value.replace(/_/g, ' ').toLowerCase()
    .split(' ').map(w => w.charAt(0).toUpperCase() + w.slice(1)).join(' ')
}

function carDisplayName(brand: string | null | undefined, model: string | null | undefined): string {
  const b = enumToLabel(brand); const m = enumToLabel(model)
  return m.toLowerCase().startsWith(b.toLowerCase()) ? m : `${b} ${m}`.trim()
}

function carLabel(car: Car): string {
  const name = carDisplayName(car.brand, car.model)
  return car.licensePlate ? `${name} · ${car.licensePlate}` : name
}
</script>
