<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { XMarkIcon } from '@heroicons/vue/24/outline'
import EudaAutoSync from '../imports/EudaAutoSync.vue'
import type { Car } from '../../api/carService'

/**
 * AutoSync-Einrichtung direkt nach dem Anlegen eines VW-Group-Fahrzeugs in /cars -
 * das Gegenstueck zu TeslaTelemetryPrompt. Erklaerung, Trial-Hinweis und das
 * Verbinden-Formular kommen aus derselben Karte wie auf /imports (EudaAutoSync),
 * damit beide Wege dasselbe versprechen.
 */
defineProps<{ car: Car }>()
const emit = defineEmits<{ (e: 'close'): void }>()
const { t } = useI18n()
</script>

<template>
  <!-- Teleport nach body: die Fahrzeug-View liegt auf Mobile im SwipeTabPager, dessen Track
       ein translateX() traegt - ein transformierter Vorfahre wuerde position:fixed einfangen. -->
  <Teleport to="body">
  <div
    class="fixed inset-0 bg-black bg-opacity-50 flex items-end md:items-center justify-center z-50 md:p-4"
    role="dialog"
    aria-modal="true"
    :aria-label="t('eu_data_act_sync.prompt_title')"
    data-testid="euda-prompt"
    @click.self="emit('close')"
  >
    <div class="bg-white dark:bg-gray-800 rounded-t-sm md:rounded-sm md:shadow-[6px_6px_0_rgba(0,0,0,0.40)] md:dark:shadow-[6px_6px_0_rgba(255,255,255,0.40)] w-full max-w-2xl max-h-[92vh] md:max-h-[90vh] overflow-y-auto">
      <div class="flex items-start justify-between gap-4 px-5 md:px-6 pt-5 md:pt-6 pb-4 border-b border-gray-100 dark:border-gray-700">
        <div class="min-w-0">
          <p class="text-indigo-600 dark:text-indigo-400 text-[10px] font-bold uppercase tracking-[0.14em] mb-0.5">{{ t('eu_data_act_sync.prompt_eyebrow') }}</p>
          <h2 class="text-lg md:text-xl font-bold text-gray-900 dark:text-white tracking-tight">{{ t('eu_data_act_sync.prompt_title') }}</h2>
        </div>
        <button type="button" :aria-label="t('common.close')"
                class="shrink-0 text-gray-400 hover:text-gray-600 dark:hover:text-gray-200 transition-colors"
                @click="emit('close')">
          <XMarkIcon class="w-6 h-6" />
        </button>
      </div>

      <div class="p-5 md:p-6 space-y-5">
        <EudaAutoSync :cars="[car]" embedded />

        <div class="text-center">
          <button type="button"
                  class="text-sm text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-200 underline"
                  @click="emit('close')">
            {{ t('eu_data_act_sync.prompt_later') }}
          </button>
          <p class="text-xs text-gray-400 dark:text-gray-500 mt-1">{{ t('eu_data_act_sync.prompt_later_hint') }}</p>
        </div>
      </div>
    </div>
  </div>
  </Teleport>
</template>
