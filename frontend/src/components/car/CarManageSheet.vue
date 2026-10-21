<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import {
  PencilSquareIcon, CameraIcon, CheckCircleIcon, ClipboardDocumentIcon, CheckIcon,
  TrashIcon, XMarkIcon, PlusIcon,
} from '@heroicons/vue/24/outline'
import BottomSheet from '../shared/BottomSheet.vue'
import type { Car } from '../../api/carService'

/**
 * Mehr-Menue eines Fahrzeugs: alles, was man selten braucht, in einem Sheet statt auf der
 * Seite. Bearbeiten, Foto (mit Schalter "oeffentlich"), Aktiv setzen, Fahrzeug-ID, Loeschen,
 * weiteres Fahrzeug anlegen. Jede Zeile 48 px hoch.
 */
defineProps<{
  car: Car
  modelLabel: string
  imageUrl: string | null
  imagePublic: boolean
  uploading: boolean
  canActivate: boolean
  copied: boolean
}>()
const emit = defineEmits<{
  close: []
  edit: []
  upload: [event: Event]
  visibility: [isPublic: boolean]
  deletePhoto: []
  activate: []
  copyId: []
  delete: []
  add: []
}>()
const { t } = useI18n()

const row = 'flex w-full items-center gap-3 min-h-12 px-4 py-2 text-left text-[15px] text-gray-900 hover:bg-gray-100 dark:text-gray-100 dark:hover:bg-gray-700 rounded-sm transition'
const hint = 'ml-auto shrink-0 text-xs text-gray-500 dark:text-gray-400'
</script>

<template>
  <BottomSheet :label="modelLabel" testid="car-manage-sheet" panel-class="sm:max-w-md" @close="emit('close')">
    <template #default="{ close }">
      <div class="flex items-center justify-between px-4 pt-4 pb-2">
        <div class="min-w-0">
          <h2 class="text-base font-semibold text-gray-900 dark:text-gray-100 truncate">{{ modelLabel }}</h2>
          <p class="text-sm text-gray-500 dark:text-gray-400"><template v-if="car.trim">{{ car.trim }} · </template>{{ car.year }}</p>
        </div>
        <button type="button" @click="close" :aria-label="t('common.close')"
          class="-mr-2 p-2.5 min-h-[44px] min-w-[44px] flex items-center justify-center text-gray-500 hover:text-gray-800 dark:hover:text-gray-200">
          <XMarkIcon class="w-5 h-5" aria-hidden="true" />
        </button>
      </div>

      <div class="px-2 pb-3 overflow-y-auto">
        <button type="button" :class="row" data-testid="car-menu-edit" @click="emit('edit')">
          <PencilSquareIcon class="h-5 w-5 text-gray-500" aria-hidden="true" />{{ t('cars.edit_btn') }}
        </button>

        <!-- Foto: Upload-Label plus Schalter oeffentlich -->
        <label :class="[row, 'cursor-pointer', uploading ? 'opacity-60 cursor-not-allowed' : '']">
          <CameraIcon class="h-5 w-5 text-gray-500" aria-hidden="true" />
          <span>{{ uploading ? t('cars.uploading') : (imageUrl ? t('cars.change_photo') : t('cars.upload_photo')) }}</span>
          <input type="file" accept="image/jpeg,image/png" class="sr-only" :disabled="uploading" @change="emit('upload', $event)" />
        </label>
        <div class="flex items-center gap-3 min-h-12 pl-12 pr-4 py-2">
          <span class="text-sm text-gray-700 dark:text-gray-300">{{ t('cars.share_photo') }}</span>
          <button type="button" role="switch" :aria-checked="imagePublic" :aria-label="t('cars.share_photo')"
            class="ml-auto relative h-6 w-11 rounded-full transition-colors"
            :class="imagePublic ? 'bg-green-500' : 'bg-gray-300 dark:bg-gray-600'"
            @click="emit('visibility', !imagePublic)">
            <span class="absolute top-0.5 h-5 w-5 rounded-full bg-white shadow transition-transform" :class="imagePublic ? 'translate-x-5' : 'translate-x-0.5'" />
          </button>
        </div>
        <button v-if="imageUrl" type="button" :class="[row, 'text-sm text-gray-600 dark:text-gray-400 pl-12']" @click="emit('deletePhoto')">
          {{ t('cars.delete_photo') }}
        </button>

        <button v-if="canActivate" type="button" :class="row" data-testid="car-menu-activate" @click="emit('activate')">
          <CheckCircleIcon class="h-5 w-5 text-gray-500" aria-hidden="true" />
          <span>{{ t('cars.set_active_btn') }}</span>
          <span :class="hint">{{ t('cars.menu_activate_hint') }}</span>
        </button>

        <button type="button" :class="row" @click="emit('copyId')">
          <CheckIcon v-if="copied" class="h-5 w-5 text-green-600" aria-hidden="true" />
          <ClipboardDocumentIcon v-else class="h-5 w-5 text-gray-500" aria-hidden="true" />
          <span>{{ copied ? t('cars.api_id_copied') : t('cars.api_id_copy') }}</span>
          <span :class="[hint, 'font-mono truncate max-w-[40%]']">{{ car.id.slice(0, 8) }}…</span>
        </button>

        <button type="button" :class="row" @click="emit('add')">
          <PlusIcon class="h-5 w-5 text-gray-500" aria-hidden="true" />{{ t('cars.menu_add') }}
        </button>

        <hr class="my-2 border-gray-200 dark:border-gray-700" />

        <button type="button" :class="[row, 'text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20']" data-testid="car-menu-delete" @click="emit('delete')">
          <TrashIcon class="h-5 w-5" aria-hidden="true" />
          <span>{{ t('cars.delete_btn') }}</span>
          <span :class="hint">{{ t('cars.menu_delete_hint') }}</span>
        </button>
      </div>
    </template>
  </BottomSheet>
</template>
