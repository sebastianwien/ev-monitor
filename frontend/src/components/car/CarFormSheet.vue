<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { XMarkIcon } from '@heroicons/vue/24/outline'
import BottomSheet from '../shared/BottomSheet.vue'
import CarFormFields, { type CarFormState } from './CarFormFields.vue'

/**
 * Fahrzeugformular als Sheet: Bearbeiten immer, Anlegen sobald schon ein Auto da ist.
 * Mobil faehrt es von unten herein, ab sm steht es mittig. Schliessen ueber X, Abbrechen
 * oder den Hintergrund meldet `cancel`; die View setzt dann das Formular zurueck.
 */
defineProps<{
  form: CarFormState
  submitting: boolean
  title: string
}>()
const emit = defineEmits<{ submit: []; cancel: []; missing: [] }>()
const { t } = useI18n()
</script>

<template>
  <BottomSheet :label="title" testid="car-form-sheet" panel-class="sm:max-w-2xl" @close="emit('cancel')">
    <template #default="{ close }">
      <div class="flex items-center justify-between px-4 sm:px-6 pt-4 pb-3 border-b border-gray-100 dark:border-gray-700">
        <h2 class="text-lg sm:text-xl font-semibold text-gray-800 dark:text-gray-200">{{ title }}</h2>
        <button type="button" @click="close" :aria-label="t('common.close')"
          class="-mr-2 p-2.5 min-h-[44px] min-w-[44px] flex items-center justify-center text-gray-500 hover:text-gray-800 dark:hover:text-gray-200">
          <XMarkIcon class="w-5 h-5" aria-hidden="true" />
        </button>
      </div>
      <div class="flex-1 overflow-y-auto px-4 sm:px-6 py-4">
        <CarFormFields :form="form" :submitting="submitting" @submit="emit('submit')" @cancel="close" @missing="emit('missing')" />
      </div>
    </template>
  </BottomSheet>
</template>
