<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { TrashIcon, XMarkIcon } from '@heroicons/vue/24/outline'
import { useLogUndo } from '../../composables/useLogUndo'

const { t } = useI18n()
const { pending, restoreFailed, undo, dismiss } = useLogUndo()
</script>

<template>
  <!-- Mobile: volle Breite über der Bottom-Navigation (h-14 plus Safe-Area), Desktop: rechts unten -->
  <div
    v-if="pending"
    role="status"
    aria-live="polite"
    class="fixed inset-x-3 bottom-[calc(4.5rem+env(safe-area-inset-bottom))] z-50 md:inset-x-auto md:right-6 md:bottom-6 md:max-w-md animate-slide-in"
  >
    <div class="flex items-center gap-3 bg-gray-900 dark:bg-gray-100 text-white dark:text-gray-900 pl-4 pr-1 py-1 rounded-sm shadow-[6px_6px_0_rgba(0,0,0,0.40)] dark:shadow-[6px_6px_0_rgba(255,255,255,0.40)]">
      <TrashIcon class="w-5 h-5 flex-shrink-0" aria-hidden="true" />
      <p class="flex-1 min-w-0 text-sm py-2">
        {{ restoreFailed ? t('dashboard.log_restore_failed') : t('dashboard.log_deleted') }}
      </p>
      <button
        v-if="!restoreFailed"
        type="button"
        class="min-h-11 px-3 text-sm font-bold underline underline-offset-2 rounded-sm hover:bg-white/15 dark:hover:bg-black/10 focus:outline-none focus:ring-2 focus:ring-current"
        @click="undo"
      >
        {{ t('dashboard.log_undo') }}
      </button>
      <button
        type="button"
        :aria-label="t('common.close')"
        class="min-h-11 min-w-11 flex items-center justify-center rounded-sm hover:bg-white/15 dark:hover:bg-black/10 focus:outline-none focus:ring-2 focus:ring-current"
        @click="dismiss"
      >
        <XMarkIcon class="w-5 h-5" />
      </button>
    </div>
  </div>
</template>
