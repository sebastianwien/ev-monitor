<script setup lang="ts">
import { useFeatureAnnouncements } from '../composables/useFeatureAnnouncements'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { SparklesIcon } from '@heroicons/vue/24/outline'

const { announcement, dismiss, total, currentNumber } = useFeatureAnnouncements()
const router = useRouter()
const { t } = useI18n()

const handleCta = () => {
  dismiss()
  if (announcement.value?.ctaRoute) {
    router.push(announcement.value.ctaRoute)
  }
}
</script>

<template>
  <div
    v-if="announcement"
    class="fixed inset-0 z-50 flex items-end sm:items-center justify-center p-4 bg-black/50 backdrop-blur-sm"
    @click.self="dismiss">
    <div class="bg-white dark:bg-gray-800 rounded-2xl shadow-2xl w-full max-w-md p-6">
      <div class="flex items-center gap-3 mb-3">
        <div class="flex-shrink-0 w-10 h-10 rounded-full bg-indigo-100 dark:bg-indigo-900/50 flex items-center justify-center">
          <SparklesIcon class="h-5 w-5 text-indigo-600 dark:text-indigo-400" />
        </div>
        <div class="flex-1 min-w-0">
          <h2 class="text-lg font-bold text-gray-800 dark:text-gray-100">{{ t(announcement.titleKey) }}</h2>
        </div>
        <span v-if="total > 1" class="text-xs text-gray-400 dark:text-gray-500 shrink-0">{{ currentNumber }} / {{ total }}</span>
      </div>

      <p class="text-sm text-gray-600 dark:text-gray-300 leading-relaxed mb-5">
        {{ t(announcement.bodyKey) }}
      </p>

      <div class="flex gap-3">
        <button
          v-if="announcement.ctaLabelKey"
          @click="handleCta"
          class="btn-3d flex-1 px-4 py-2.5 bg-indigo-600 text-white text-sm font-medium rounded-xl hover:bg-indigo-700 transition">
          {{ t(announcement.ctaLabelKey) }}
        </button>
        <button
          @click="dismiss"
          :class="announcement.ctaLabelKey ? 'flex-1' : 'w-full'"
          class="btn-3d px-4 py-2.5 bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-200 text-sm font-medium rounded-xl hover:bg-gray-200 dark:hover:bg-gray-600 transition">
          {{ currentNumber < total ? t('common.next') : (announcement.ctaLabelKey ? t('common.close') : t('common.ok_understood')) }}
        </button>
      </div>
    </div>
  </div>
</template>
