<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { StarIcon } from '@heroicons/vue/24/outline'

/**
 * Vor dem Löschen eines Autos: unbewertet und bewertbar - kurz bewerten oder nur löschen.
 * Bewertet mit Text - Schalter "Text mit dem Auto löschen" sichtbar machen.
 */
defineProps<{
    model: string
    mode: 'rate' | 'text'
    deleteWithCar: boolean
    busy: boolean
}>()
const emit = defineEmits<{ rate: []; delete: []; cancel: []; 'update:deleteWithCar': [value: boolean] }>()
const { t } = useI18n()
</script>

<template>
    <Teleport to="body">
        <div class="fixed inset-0 z-50 flex items-end sm:items-center justify-center bg-black/50 backdrop-blur-sm sm:p-4" @click.self="emit('cancel')">
            <div role="dialog" aria-modal="true" aria-labelledby="rating-delete-title"
                 class="w-full sm:max-w-md bg-white dark:bg-gray-800 rounded-t-xl sm:rounded-sm p-5 pb-[calc(1.25rem+env(safe-area-inset-bottom))] space-y-4"
                 data-testid="rating-delete-sheet">
                <template v-if="mode === 'rate'">
                    <div class="flex items-start gap-3">
                        <StarIcon class="w-6 h-6 shrink-0 text-amber-500 dark:text-amber-400" aria-hidden="true" />
                        <div class="space-y-1">
                            <h2 id="rating-delete-title" class="text-lg font-bold text-gray-900 dark:text-gray-100">{{ t('rating.delete_sheet.rate_title', { model }) }}</h2>
                            <p class="text-sm text-gray-600 dark:text-gray-300">{{ t('rating.delete_sheet.rate_body') }}</p>
                        </div>
                    </div>
                    <div class="flex flex-col gap-2">
                        <button type="button" class="min-h-12 rounded-sm bg-green-600 hover:bg-green-700 text-white font-semibold" :disabled="busy" @click="emit('rate')">
                            {{ t('rating.delete_sheet.rate_cta') }}
                        </button>
                        <button type="button" class="min-h-12 rounded-sm text-red-600 dark:text-red-400 font-medium hover:bg-red-50 dark:hover:bg-red-900/20" :disabled="busy" @click="emit('delete')">
                            {{ t('rating.delete_sheet.delete_only') }}
                        </button>
                    </div>
                </template>
                <template v-else>
                    <h2 id="rating-delete-title" class="text-lg font-bold text-gray-900 dark:text-gray-100">{{ t('rating.delete_sheet.text_title', { model }) }}</h2>
                    <p class="text-sm text-gray-600 dark:text-gray-300">{{ t('rating.delete_sheet.text_body') }}</p>
                    <label class="flex items-start gap-3 min-h-11 text-sm text-gray-800 dark:text-gray-200">
                        <input type="checkbox" class="mt-0.5 w-5 h-5 accent-green-600" :checked="deleteWithCar" :disabled="busy"
                               @change="emit('update:deleteWithCar', ($event.target as HTMLInputElement).checked)" />
                        <span>{{ t('rating.deep.delete_with_car') }}</span>
                    </label>
                    <div class="flex gap-2">
                        <button type="button" class="flex-1 min-h-12 rounded-sm text-gray-600 dark:text-gray-300 font-medium" :disabled="busy" @click="emit('cancel')">
                            {{ t('rating.delete_sheet.cancel') }}
                        </button>
                        <button type="button" class="flex-1 min-h-12 rounded-sm bg-red-600 hover:bg-red-700 text-white font-semibold" :disabled="busy" @click="emit('delete')">
                            {{ t('rating.delete_sheet.delete') }}
                        </button>
                    </div>
                </template>
            </div>
        </div>
    </Teleport>
</template>
