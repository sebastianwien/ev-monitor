<template>
  <BottomSheet :label="t('share_car.sheet_title')" testid="car-share-sheet" @close="emit('close')">
    <template #default="{ close }">
      <div class="flex items-center justify-between px-4 pt-4 pb-2">
        <div class="min-w-0">
          <h2 class="text-base font-semibold text-gray-900 dark:text-gray-100">{{ t('share_car.sheet_title') }}</h2>
          <p class="text-sm text-gray-500 dark:text-gray-400 truncate">{{ title }}</p>
        </div>
        <button type="button" @click="close" :aria-label="t('common.close')"
          class="-mr-2 p-2.5 min-h-[44px] min-w-[44px] flex items-center justify-center text-gray-500 hover:text-gray-800 dark:hover:text-gray-200">
          <XMarkIcon class="w-5 h-5" />
        </button>
      </div>
      <div class="px-4 pb-4 overflow-y-auto">
        <CarShareBox :car-id="carId" :title="title" :source="source" autofocus-toggle />
      </div>
    </template>
  </BottomSheet>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { XMarkIcon } from '@heroicons/vue/24/outline'
import BottomSheet from '../shared/BottomSheet.vue'
import CarShareBox from './CarShareBox.vue'
import type { CarShareSource } from '../../composables/useCarShareSheet'

/**
 * Teilen-Dialog fuer die Einstiege ausserhalb der Fahrzeugverwaltung (Auto-Karte im
 * Header, Vergleichskarte). Inhalt ist dieselbe CarShareBox, es gibt nur einen Weg.
 */
defineProps<{ carId: string; title: string; source: CarShareSource }>()
const emit = defineEmits<{ close: [] }>()
const { t } = useI18n()
</script>
