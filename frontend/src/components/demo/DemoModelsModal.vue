<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { BoltIcon, CalculatorIcon, ArrowsRightLeftIcon, ChartBarIcon, Square3Stack3DIcon } from '@heroicons/vue/24/outline'
import { useDemoModal } from '../../composables/useDemoModal'

const { t } = useI18n()
const { visible, dismiss } = useDemoModal('ev_demo_models_shown')
</script>

<template>
  <Transition name="fade">
    <div v-if="visible" class="fixed inset-0 z-50 flex items-center justify-center px-4">
      <!-- Backdrop -->
      <div class="absolute inset-0 bg-black/60" @click="dismiss" />

      <!-- Modal -->
      <div class="relative w-full max-w-sm bg-white dark:bg-gray-900 rounded-2xl shadow-2xl p-6 flex flex-col gap-5">
        <!-- Header -->
        <div class="flex items-center gap-3">
          <div class="w-10 h-10 bg-amber-100 dark:bg-amber-900/40 rounded-xl flex items-center justify-center shrink-0">
            <BoltIcon class="h-5 w-5 text-amber-500" />
          </div>
          <div>
            <p class="text-xs font-semibold text-amber-600 uppercase tracking-wide">{{ t('demo.welcome_badge') }}</p>
            <h2 class="text-lg font-bold text-gray-900 dark:text-gray-100 leading-tight">{{ t('demo.models_title') }}</h2>
          </div>
        </div>

        <!-- Description -->
        <p class="text-sm text-gray-600 dark:text-gray-400 leading-relaxed">{{ t('demo.models_desc') }}</p>

        <!-- Feature list -->
        <ul class="space-y-3">
          <li class="flex items-start gap-3">
            <CalculatorIcon class="h-5 w-5 text-indigo-500 shrink-0 mt-0.5" />
            <span class="text-sm text-gray-700 dark:text-gray-300">{{ t('demo.models_feature_1') }}</span>
          </li>
          <li class="flex items-start gap-3">
            <ArrowsRightLeftIcon class="h-5 w-5 text-indigo-500 shrink-0 mt-0.5" />
            <span class="text-sm text-gray-700 dark:text-gray-300">{{ t('demo.models_feature_2') }}</span>
          </li>
          <li class="flex items-start gap-3">
            <ChartBarIcon class="h-5 w-5 text-indigo-500 shrink-0 mt-0.5" />
            <span class="text-sm text-gray-700 dark:text-gray-300">{{ t('demo.models_feature_3') }}</span>
          </li>
          <li class="flex items-start gap-3">
            <Square3Stack3DIcon class="h-5 w-5 text-indigo-500 shrink-0 mt-0.5" />
            <span class="text-sm text-gray-700 dark:text-gray-300">{{ t('demo.models_feature_4') }}</span>
          </li>
        </ul>

        <!-- CTA -->
        <button
          @click="dismiss"
          class="btn-3d w-full py-3 bg-indigo-600 hover:bg-indigo-700 text-white font-semibold rounded-xl text-sm transition">
          {{ t('demo.models_cta') }}
        </button>
      </div>
    </div>
  </Transition>
</template>

<style scoped>
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
