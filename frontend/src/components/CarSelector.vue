<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { carService, type Car } from '../api/carService'
import { useRouter } from 'vue-router'

const props = defineProps<{
  modelValue: string | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string | null]
}>()

const router = useRouter()
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
    cars.value = await carService.getCars()

    // If no car is selected and we have cars, select the first one
    if (!selectedCarId.value && cars.value.length > 0) {
      selectedCarId.value = cars.value[0].id
    }
  } catch (err: any) {
    error.value = err.response?.data?.message || 'Fehler beim Laden der Fahrzeuge'
    console.error('Failed to fetch cars:', err)
  } finally {
    loading.value = false
  }
}

const getModelLabel = (modelValue: string): string => {
  // Convert enum name to readable (e.g., "MODEL_3" -> "Model 3")
  return modelValue.replace(/_/g, ' ').toLowerCase()
    .split(' ')
    .map(word => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ')
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
    <label class="block text-sm font-medium text-gray-700 mb-1">Fahrzeug</label>

    <div v-if="loading" class="text-sm text-gray-500 p-2">
      Lade Fahrzeuge...
    </div>

    <div v-else-if="error" class="text-sm text-red-600 p-2">
      {{ error }}
    </div>

    <div v-else-if="cars.length === 0" class="space-y-2">
      <p class="text-sm text-gray-600 p-2 bg-yellow-50 border border-yellow-200 rounded-md">
        Du musst zuerst ein Fahrzeug hinzufügen um Ladevorgänge zu erfassen.
      </p>
      <button
        @click="goToCarManagement"
        type="button"
        class="w-full bg-indigo-100 text-indigo-700 px-4 py-2 rounded-md text-sm font-medium hover:bg-indigo-200 transition">
        Zur Fahrzeugverwaltung
      </button>
    </div>

    <select
      v-else
      v-model="selectedCarId"
      required
      class="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 p-2 border">
      <option :value="null" disabled>Fahrzeug wählen</option>
      <option v-for="car in cars" :key="car.id" :value="car.id">
        {{ getModelLabel(car.model) }} {{ car.batteryCapacityKwh }}kWh ({{ car.licensePlate }})
      </option>
    </select>
  </div>
</template>
