<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { ChevronLeftIcon, XMarkIcon } from '@heroicons/vue/24/outline'
import { LAST_STEP, type WizardStep } from './wizardLogic'

defineProps<{
  step: WizardStep
  question: string
  hint?: string
  canProceed: boolean
  primaryLabel: string
  saving?: boolean
}>()
const emit = defineEmits<{ back: []; next: []; cancel: [] }>()
const { t } = useI18n()
</script>

<template>
  <div class="flex flex-col min-h-[calc(100dvh-4rem-3.5rem)] md:min-h-0">
    <header class="px-4 pt-4 pb-3 md:px-6">
      <div class="flex items-center justify-between">
        <button v-if="step > 1" type="button" :aria-label="t('common.back')" @click="emit('back')"
          class="w-8 h-8 -ml-2 flex items-center justify-center rounded-sm text-gray-500 hover:bg-gray-100 dark:hover:bg-gray-700">
          <ChevronLeftIcon class="h-5 w-5" />
        </button>
        <div v-else class="w-8" />
        <span class="text-xs font-medium tracking-wide uppercase text-gray-400 dark:text-gray-500">
          {{ t('logwizard.step_of', { step, total: LAST_STEP }) }}
        </span>
        <button type="button" :aria-label="t('common.cancel')" @click="emit('cancel')"
          class="w-8 h-8 -mr-2 flex items-center justify-center rounded-sm text-gray-400 hover:text-gray-600 hover:bg-gray-100 dark:hover:text-gray-200 dark:hover:bg-gray-700">
          <XMarkIcon class="h-5 w-5" />
        </button>
      </div>
      <div class="flex gap-1 mt-3" role="progressbar" :aria-valuenow="step" :aria-valuemin="1" :aria-valuemax="LAST_STEP">
        <i v-for="i in LAST_STEP" :key="i" :class="['flex-1 h-1 rounded-sm', i <= step ? 'bg-indigo-600' : 'bg-gray-200 dark:bg-gray-700']" />
      </div>
      <h1 class="mt-4 text-xl md:text-2xl font-bold text-gray-800 dark:text-gray-100 text-balance">{{ question }}</h1>
      <p v-if="hint" class="mt-1 text-sm text-gray-500 dark:text-gray-400">{{ hint }}</p>
    </header>

    <div class="flex-1 px-4 pb-4 md:px-6">
      <slot />
    </div>

    <footer class="sticky bottom-[calc(3.5rem+env(safe-area-inset-bottom))] md:bottom-0 bg-white dark:bg-gray-800 border-t border-gray-200 dark:border-gray-700 px-4 py-3 md:px-6 flex items-center gap-3">
      <button v-if="step > 1" type="button" @click="emit('back')"
        class="px-3 py-3 text-sm font-medium text-gray-500 dark:text-gray-400 inline-flex items-center gap-1 rounded-sm transition hover:text-gray-800 dark:hover:text-gray-100 hover:bg-gray-100 dark:hover:bg-gray-700">
        <ChevronLeftIcon class="h-4 w-4" />{{ t('common.back') }}
      </button>
      <button type="button" data-testid="wizard-next" :disabled="!canProceed || saving" @click="emit('next')"
        :class="['flex-1 bg-indigo-600 text-white p-3 rounded-sm btn-3d font-semibold transition',
                 !canProceed || saving ? 'opacity-40 cursor-not-allowed' : 'hover:bg-indigo-700']">
        {{ saving ? t('common.saving') : primaryLabel }}
      </button>
    </footer>
  </div>
</template>
