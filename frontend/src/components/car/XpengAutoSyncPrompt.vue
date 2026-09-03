<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ArrowUpTrayIcon, ArrowTopRightOnSquareIcon, XMarkIcon } from '@heroicons/vue/24/outline'
import { useImportsTab } from '../../composables/useImportsTab'
import type { Car } from '../../api/carService'

const { t } = useI18n()
defineProps<{ car: Car }>()
const emit = defineEmits<{ (e: 'close'): void }>()
const router = useRouter()
const { activeTab } = useImportsTab()

function goToImport() {
  activeTab.value = 'xpeng'
  emit('close')
  router.push({ name: 'imports' })
}

const steps = [1, 2, 3]
</script>

<template>
  <!-- Teleport nach body: die Fahrzeug-View liegt auf Mobile im SwipeTabPager, dessen Track
       ein translateX() traegt - ein transformierter Vorfahre wird zum Containing Block fuer
       position:fixed und wuerde das Overlay aus dem Viewport schieben. -->
  <Teleport to="body">
  <div
    class="fixed inset-0 bg-black bg-opacity-50 flex items-end md:items-center justify-center z-50 md:p-4"
    role="dialog"
    aria-modal="true"
    :aria-label="t('xpeng.manual_prompt_title')"
    @click.self="emit('close')"
  >
    <div class="bg-white dark:bg-gray-800 rounded-t-sm md:rounded-sm md:shadow-[6px_6px_0_rgba(0,0,0,0.40)] md:dark:shadow-[6px_6px_0_rgba(255,255,255,0.40)] w-full max-w-2xl max-h-[92vh] md:max-h-[90vh] overflow-y-auto">
      <div class="flex items-start justify-between gap-4 px-5 md:px-6 pt-5 md:pt-6 pb-4 border-b border-gray-100 dark:border-gray-700">
        <div class="min-w-0">
          <p class="text-green-700 dark:text-green-500 text-[10px] font-bold uppercase tracking-[0.14em] mb-0.5">{{ t('xpeng.autosync_prompt_eyebrow') }}</p>
          <h2 class="text-lg md:text-xl font-bold text-gray-900 dark:text-white tracking-tight">{{ t('xpeng.manual_prompt_title') }}</h2>
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

      <div class="p-5 md:p-6 space-y-5">
        <p class="text-gray-700 dark:text-gray-300 text-sm md:text-[15px] leading-relaxed">
          {{ t('xpeng.manual_prompt_intro') }}
        </p>

        <ol class="space-y-3">
          <li v-for="n in steps" :key="n" class="flex gap-3">
            <span class="inline-flex w-6 h-6 shrink-0 bg-green-600 text-white rounded-sm items-center justify-center text-xs font-extrabold">{{ n }}</span>
            <span class="text-sm text-gray-700 dark:text-gray-300 leading-relaxed">{{ t(`xpeng.manual_prompt_step${n}`) }}</span>
          </li>
        </ol>

        <a
          href="https://www.xpeng.com/data-act"
          target="_blank"
          rel="noopener noreferrer"
          class="inline-flex items-center gap-1.5 text-sm font-bold text-green-700 dark:text-green-400 hover:underline"
        >
          {{ t('xpeng.manual_prompt_portal_link') }}
          <ArrowTopRightOnSquareIcon class="w-4 h-4" />
        </a>

        <button
          type="button"
          v-haptic
          class="btn-3d w-full inline-flex items-center justify-center gap-2 bg-green-600 hover:bg-green-500 text-white font-bold uppercase tracking-wide text-sm px-5 py-3 rounded-sm"
          @click="goToImport"
        >
          <ArrowUpTrayIcon class="w-4 h-4" />
          {{ t('xpeng.manual_prompt_cta') }}
        </button>

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
  </Teleport>
</template>
