<script setup lang="ts">
import { onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { BellAlertIcon, CheckCircleIcon, ExclamationCircleIcon, BoltIcon } from '@heroicons/vue/24/outline'
import { useWaitlist } from '../../composables/useWaitlist'

const { t } = useI18n()
const { onWaitlist, loaded, busy, error, load, join, leave } = useWaitlist('XPENG_AUTOSYNC')

onMounted(load)
</script>

<template>
  <!-- Marketing-Teaser: der manuelle ZIP-Upload ist heute noch Handarbeit - hier sammeln
       wir Interessenten fuer den kommenden automatischen Import (Opt-in Warteliste). -->
  <section
    v-if="loaded"
    class="rounded-sm border-2 border-green-600 dark:border-green-700 bg-green-50/60 dark:bg-green-950/30 p-5 md:p-6 shadow-[2px_2px_0_0_#16a34a] dark:shadow-[2px_2px_0_0_#15803d]">

    <template v-if="!onWaitlist">
      <p class="text-green-700 dark:text-green-500 text-[11px] font-bold uppercase tracking-[0.14em] mb-2 flex items-center gap-1.5">
        <BoltIcon class="w-4 h-4" />
        {{ t('xpeng.waitlist.eyebrow') }}
      </p>
      <h3 class="text-lg md:text-xl font-bold text-gray-900 dark:text-white tracking-tight mb-2">
        {{ t('xpeng.waitlist.title') }}
      </h3>
      <p class="text-gray-600 dark:text-gray-300 text-sm leading-relaxed mb-4">
        {{ t('xpeng.waitlist.body') }}
      </p>

      <div v-if="error"
           class="mb-4 border-l-2 border-red-500 bg-red-50 dark:bg-red-950/40 px-4 py-3 rounded-r-sm text-sm text-red-900 dark:text-red-200 flex gap-2">
        <ExclamationCircleIcon class="w-5 h-5 flex-shrink-0" />
        <span>{{ error }}</span>
      </div>

      <button
        type="button"
        v-haptic
        :disabled="busy"
        class="w-full sm:w-auto inline-flex items-center justify-center gap-2 bg-green-600 hover:bg-green-500 disabled:opacity-50 disabled:cursor-not-allowed text-white font-bold uppercase tracking-wider text-xs px-5 py-3 rounded-sm border-2 border-green-600 shadow-[2px_2px_0_0_#030712] active:translate-x-[3px] active:translate-y-[3px] active:shadow-none disabled:shadow-none transition-[transform,box-shadow] duration-75"
        @click="join">
        <span v-if="busy" class="w-4 h-4 border-2 border-white/40 border-t-white rounded-full animate-spin" />
        <BellAlertIcon v-else class="w-4 h-4" />
        <span>{{ busy ? t('xpeng.waitlist.joining') : t('xpeng.waitlist.cta') }}</span>
      </button>
    </template>

    <template v-else>
      <div class="flex items-start gap-3">
        <CheckCircleIcon class="w-6 h-6 shrink-0 text-green-600 dark:text-green-400" />
        <div class="min-w-0">
          <h3 class="text-lg font-bold text-gray-900 dark:text-white tracking-tight mb-1">
            {{ t('xpeng.waitlist.joined_title') }}
          </h3>
          <p class="text-gray-600 dark:text-gray-300 text-sm leading-relaxed">
            {{ t('xpeng.waitlist.joined_body') }}
          </p>
          <div v-if="error"
               class="mt-3 border-l-2 border-red-500 bg-red-50 dark:bg-red-950/40 px-4 py-2 rounded-r-sm text-sm text-red-900 dark:text-red-200 flex gap-2">
            <ExclamationCircleIcon class="w-5 h-5 flex-shrink-0" />
            <span>{{ error }}</span>
          </div>
          <button
            type="button"
            :disabled="busy"
            class="mt-3 text-xs text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-200 underline disabled:opacity-50"
            @click="leave">
            {{ t('xpeng.waitlist.leave') }}
          </button>
        </div>
      </div>
    </template>
  </section>
</template>
