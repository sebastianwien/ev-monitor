<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { Car } from '../../api/carService'
import { useCarStore } from '../../stores/car'
import { useRouter } from 'vue-router'
import { carLabel } from '@/composables/useCarLabel'

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

    <div v-else-if="error" role="alert" class="rounded-xl bg-amber-50 px-3 py-2 text-[14px] text-amber-900 dark:bg-amber-400/10 dark:text-amber-200">
      {{ error }}
    </div>

    <div v-else-if="cars.length === 0" class="space-y-2">
      <p class="rounded-xl bg-amber-50 px-3 py-2 text-[14px] text-amber-900 dark:bg-amber-400/10 dark:text-amber-200">
        {{ t('carselector.no_cars') }}
      </p>
      <button
        @click="goToCarManagement"
        type="button"
        class="btn-3d min-h-11 w-full rounded-xl border-2 border-edge bg-white px-4 text-[14px] font-semibold text-gray-800 hover:bg-gray-50 dark:bg-white/10 dark:text-gray-100 dark:hover:bg-white/15">
        {{ t('carselector.go_to_cars') }}
      </button>
    </div>

    <select
      v-else
      v-model="selectedCarId"
      required
      class="min-h-12 w-full min-w-0 rounded-xl border-2 border-edge bg-white px-3 text-[15px] font-semibold outline-none focus:border-green-600 focus:ring-0 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-100">
      <option :value="null" disabled>{{ t('carselector.select') }}</option>
      <option v-for="car in cars" :key="car.id" :value="car.id">
        {{ carLabel(car) }}
      </option>
    </select>
  </div>
</template>
