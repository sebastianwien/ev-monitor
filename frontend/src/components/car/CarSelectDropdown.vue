<template>
  <select
    :value="modelValue"
    @change="$emit('update:modelValue', ($event.target as HTMLSelectElement).value)"
    class="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-green-500"
  >
    <slot name="placeholder">
      <option value="" disabled>{{ t('cars.select_placeholder') }}</option>
    </slot>
    <option v-for="car in cars" :key="car.id" :value="car.id">
      {{ carLabel(car) }}
    </option>
  </select>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
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

function carLabel(car: Car): string {
  const name = `${enumToLabel(car.brand)} ${enumToLabel(car.model)}`
  return car.licensePlate ? `${name} · ${car.licensePlate}` : name
}
</script>
