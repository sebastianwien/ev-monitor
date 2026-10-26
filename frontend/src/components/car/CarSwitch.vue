<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { CheckIcon, ChevronDownIcon, TruckIcon } from '@heroicons/vue/24/outline'
import type { Car } from '../../api/carService'
import BottomSheet from '../shared/BottomSheet.vue'
import LicensePlate from './LicensePlate.vue'
import { carDisplayName } from '../../utils/enumLabel'

/**
 * Kopfzeile einer Box im neuen Design (Übersicht, Logs): welches Auto, und bei mehreren Autos
 * der Wechsel. Eine Zeile statt einer Auto-Kachel; die Daten des Autos stehen auf /cars.
 * Bei mehreren Autos öffnet der Name ein Sheet mit allen Autos.
 */
const props = withDefaults(defineProps<{
  cars: Pick<Car, 'id' | 'displayName' | 'model' | 'trim' | 'licensePlate'>[]
  /** Gewähltes Auto (v-model). */
  modelValue: string | null
  carImageUrls: Record<string, string>
  /** Lädt das gewählte Auto gerade? */
  charging?: boolean
}>(), { charging: false })
const emit = defineEmits<{ 'update:modelValue': [carId: string] }>()
const { t } = useI18n()

const selected = computed(() => props.cars.find(c => c.id === props.modelValue) ?? props.cars[0] ?? null)
const switchable = computed(() => props.cars.length > 1)
const open = ref(false)

function choose(carId: string, close: () => void) {
  if (carId !== props.modelValue) emit('update:modelValue', carId)
  close()
}
</script>

<template>
  <div v-if="selected" class="flex min-w-0 items-center gap-2">
    <component :is="switchable ? 'button' : 'div'"
      :type="switchable ? 'button' : undefined"
      :data-testid="switchable ? 'car-switch' : undefined"
      :aria-expanded="switchable ? open : undefined"
      :aria-haspopup="switchable ? 'dialog' : undefined"
      class="flex min-w-0 items-center gap-1.5 text-left"
      :class="switchable ? '-mx-2 min-h-11 rounded-xl px-2 hover:bg-gray-100 focus-visible:outline focus-visible:outline-2 focus-visible:outline-green-600 dark:hover:bg-white/10' : ''"
      @click="switchable && (open = true)">
      <span class="truncate text-[15px] font-bold text-gray-900 dark:text-gray-50">{{ carDisplayName(selected) }}</span>
      <span v-if="selected.trim" class="hidden shrink-0 text-[13px] text-gray-500 sm:inline dark:text-gray-400">{{ selected.trim }}</span>
      <ChevronDownIcon v-if="switchable" class="h-4 w-4 shrink-0 text-gray-500 dark:text-gray-400" aria-hidden="true" />
    </component>
    <span v-if="selected.licensePlate" class="hidden shrink-0 sm:inline-flex"><LicensePlate :plate="selected.licensePlate" size="sm" /></span>
    <span v-if="charging" data-testid="car-switch-charging" class="flex shrink-0 items-center gap-1.5 text-[13px] text-story-accent dark:text-story-accent-dark">
      <span class="h-1.5 w-1.5 rounded-full bg-current motion-safe:animate-pulse" aria-hidden="true" />{{ t('story.live_charging') }}
    </span>

    <BottomSheet v-if="open" edge :label="t('cars.switcher_label')" panel-class="sm:max-w-md" @close="open = false">
      <template #default="{ close }">
        <p class="px-5 pb-2 pt-5 text-[15px] font-bold text-gray-900 dark:text-gray-50">{{ t('cars.switcher_label') }}</p>
        <ul class="overflow-y-auto px-3 pb-4">
          <li v-for="car in cars" :key="car.id">
            <button type="button" data-testid="car-switch-option"
              :aria-current="car.id === selected.id ? 'true' : undefined"
              class="flex min-h-14 w-full items-center gap-3 rounded-xl px-2 py-2 text-left hover:bg-gray-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-green-600 dark:hover:bg-white/5"
              @click="choose(car.id, close)">
              <span class="grid h-10 w-14 shrink-0 place-items-center overflow-hidden rounded-lg bg-gray-100 dark:bg-gray-700">
                <img v-if="carImageUrls[car.id]" :src="carImageUrls[car.id]" alt="" class="h-full w-full object-cover" />
                <TruckIcon v-else class="h-5 w-5 text-gray-400" aria-hidden="true" />
              </span>
              <span class="min-w-0 flex-1">
                <span class="block truncate text-[15px] font-semibold text-gray-900 dark:text-gray-100">{{ carDisplayName(car) }}</span>
                <span v-if="car.trim" class="block truncate text-[13px] text-gray-500 dark:text-gray-400">{{ car.trim }}</span>
              </span>
              <LicensePlate v-if="car.licensePlate" :plate="car.licensePlate" size="sm" class="shrink-0" />
              <CheckIcon v-if="car.id === selected.id" class="h-5 w-5 shrink-0 text-story-accent dark:text-story-accent-dark" aria-hidden="true" />
            </button>
          </li>
        </ul>
      </template>
    </BottomSheet>
  </div>
</template>
