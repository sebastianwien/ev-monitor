<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { Car } from '../../api/carService'
import { useCarStore } from '../../stores/car'
import { useRouter } from 'vue-router'

const props = defineProps<{
  modelValue: string | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string | null]
}>()

const { t } = useI18n()
const router = useRouter()
const carStore = useCarStore()
const cars = ref<Car[]>([])
const loading = ref(false)
const error = ref<string | null>(null)

const selectedCarId = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})

const fetchCars = async () => {
  try {
    loading.value = true
    error.value = null
    cars.value = await carStore.getCars()

    // If no car is selected and we have cars, select the primary car (or first as fallback)
    if (!selectedCarId.value && cars.value.length > 0) {
      const primaryCar = cars.value.find(c => c.isPrimary)
      selectedCarId.value = primaryCar ? primaryCar.id : cars.value[0].id
    }
  } catch (err: any) {
    error.value = err.response?.data?.message || t('carselector.error_load')
    console.error('Failed to fetch cars:', err)
  } finally {
    loading.value = false
  }
}

const enumToLabel = (value: string | null | undefined): string => {
  if (!value) return ''
  return value.replace(/_/g, ' ').toLowerCase()
    .split(' ')
    .map(word => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ')
}

const carLabel = (car: { brand: string; model: string; licensePlate: string }): string => {
  const name = `${enumToLabel(car.brand)} ${enumToLabel(car.model)}`
  return car.licensePlate ? `${name} · ${car.licensePlate}` : name
}

const goToCarManagement = () => {
  router.push('/cars')
}

onMounted(() => {
  fetchCars()
})
</script>

<template>
  <div>
    <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">{{ t('carselector.label') }}</label>

    <div v-if="loading" class="text-sm text-gray-500 dark:text-gray-400 p-2">
      {{ t('carselector.loading') }}
    </div>

    <div v-else-if="error" class="text-sm text-red-600 p-2">
      {{ error }}
    </div>

    <div v-else-if="cars.length === 0" class="space-y-2">
      <p class="text-sm text-gray-600 dark:text-gray-400 p-2 bg-yellow-50 dark:bg-yellow-900/30 border border-yellow-200 dark:border-yellow-700 rounded-md">
        {{ t('carselector.no_cars') }}
      </p>
      <button
        @click="goToCarManagement"
        type="button"
        class="w-full bg-indigo-100 text-indigo-700 px-4 py-2 rounded-md text-sm font-medium hover:bg-indigo-200 transition">
        {{ t('carselector.go_to_cars') }}
      </button>
    </div>

    <select
      v-else
      v-model="selectedCarId"
      required
      class="w-full rounded-md border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 p-2 border">
      <option :value="null" disabled>{{ t('carselector.select') }}</option>
      <option v-for="car in cars" :key="car.id" :value="car.id">
        {{ carLabel(car) }}
      </option>
    </select>
  </div>
</template>
