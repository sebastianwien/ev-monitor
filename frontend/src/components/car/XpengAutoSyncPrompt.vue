<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { CheckCircleIcon, ExclamationCircleIcon, XMarkIcon } from '@heroicons/vue/24/outline'
import XpengAutoSyncExplainer from '../imports/XpengAutoSyncExplainer.vue'
import XpengConsentStep from '../imports/XpengConsentStep.vue'
import type { Car } from '../../api/carService'

const { t } = useI18n()

const props = defineProps<{ car: Car }>()
const emit = defineEmits<{ (e: 'close'): void }>()

const error = ref('')
const activated = ref(false)
</script>

<template>
  <div
    class="fixed inset-0 bg-black bg-opacity-50 flex items-end md:items-center justify-center z-50 md:p-4"
    role="dialog"
    aria-modal="true"
    :aria-label="t('xpeng.autosync_prompt_title')"
    @click.self="emit('close')"
  >
    <div class="bg-white dark:bg-gray-800 rounded-t-sm md:rounded-sm md:shadow-[6px_6px_0_rgba(0,0,0,0.40)] md:dark:shadow-[6px_6px_0_rgba(255,255,255,0.40)] w-full max-w-2xl max-h-[92vh] md:max-h-[90vh] overflow-y-auto">
      <div class="flex items-start justify-between gap-4 px-5 md:px-6 pt-5 md:pt-6 pb-4 border-b border-gray-100 dark:border-gray-700">
        <div class="min-w-0">
          <p class="text-green-700 dark:text-green-500 text-[10px] font-bold uppercase tracking-[0.14em] mb-0.5">{{ t('xpeng.autosync_prompt_eyebrow') }}</p>
          <h2 class="text-lg md:text-xl font-bold text-gray-900 dark:text-white tracking-tight">{{ t('xpeng.autosync_prompt_title') }}</h2>
        </div>
        <button
          type="button"
          :aria-label="t('common.close')"
          class="shrink-0 text-gray-400 hover:text-gray-600 dark:hover:text-gray-200 transition-colors"
          @click="emit('close')"
        >
          <XMarkIcon class="w-6 h-6" />
        </button>
      </div>

      <div v-if="activated" class="p-5 md:p-6 space-y-5">
        <div class="flex items-start gap-3 rounded-sm border-2 border-green-600 bg-green-50 dark:bg-green-950/30 p-4 shadow-[2px_2px_0_0_#16a34a]">
          <CheckCircleIcon class="w-6 h-6 shrink-0 text-green-700 dark:text-green-500" />
          <div>
            <p class="font-bold text-gray-900 dark:text-white text-sm mb-1">{{ t('xpeng.autosync_prompt_success_title') }}</p>
            <p class="text-sm text-gray-700 dark:text-gray-300 leading-relaxed">{{ t('xpeng.autosync_prompt_success_body') }}</p>
          </div>
        </div>
        <button
          type="button"
          v-haptic
          class="btn-3d w-full bg-gray-950 dark:bg-gray-700 text-white font-bold uppercase tracking-wide text-sm px-5 py-2.5 rounded-sm"
          @click="emit('close')"
        >
          {{ t('common.close') }}
        </button>
      </div>

      <div v-else class="p-5 md:p-6 space-y-5">
        <XpengAutoSyncExplainer :card="false" :show-manual-hint="false" />

        <div
          v-if="error"
          class="rounded-sm border-2 border-red-300 dark:border-red-700 bg-red-50 dark:bg-red-950/40 p-3 text-sm text-red-900 dark:text-red-200 flex gap-2"
        >
          <ExclamationCircleIcon class="w-5 h-5 flex-shrink-0" />
          <span>{{ error }}</span>
        </div>

        <div class="border-t-2 border-gray-200 dark:border-gray-700 pt-5">
          <XpengConsentStep
            :cars-needing-consent="[props.car]"
            mode="autosync"
            :card="false"
            @granted="activated = true; error = ''"
            @error="error = $event"
          />
        </div>

        <div class="text-center">
          <button
            type="button"
            class="text-sm text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-200 underline"
            @click="emit('close')"
          >
            {{ t('xpeng.autosync_prompt_later') }}
          </button>
          <p class="text-xs text-gray-400 dark:text-gray-500 mt-1">{{ t('xpeng.autosync_prompt_later_hint') }}</p>
        </div>
      </div>
    </div>
  </div>
</template>
