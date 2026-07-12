<script setup lang="ts">
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

/**
 * Erklaert den XPeng-AutoSync-Weg (EU Data Act, Stufe 2). Wird an zwei Stellen
 * gezeigt: in der AutoSync-Sektion von /imports und im Modal direkt nach dem
 * Anlegen eines XPeng-Fahrzeugs.
 */
withDefaults(defineProps<{
  /** Eigene Karte mit Rahmen - im Modal aus, dort rahmt das Modal selbst. */
  card?: boolean
  /** Hinweis auf den manuellen Weg (Stufe 1). */
  showManualHint?: boolean
}>(), { card: true, showManualHint: true })

defineEmits<{ (e: 'manual'): void }>()
</script>

<template>
  <div :class="card ? 'border-2 border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-900 rounded-sm shadow-[2px_2px_0_0_#d1d5db] dark:shadow-[2px_2px_0_0_#374151] p-4 md:p-5' : ''">
    <p class="text-gray-500 dark:text-gray-400 text-[10px] font-bold uppercase tracking-[0.14em] mb-1">{{ t('xpeng.autosync_intro_eyebrow') }}</p>
    <h3 class="font-bold text-gray-900 dark:text-white text-lg mb-2 tracking-tight">{{ t('xpeng.autosync_intro_title') }}</h3>

    <p class="text-sm text-gray-600 dark:text-gray-400 leading-relaxed mb-2">{{ t('xpeng.autosync_intro_no_api') }}</p>
    <p class="text-sm text-gray-700 dark:text-gray-300 leading-relaxed border-l-2 border-green-600 pl-3 mb-4">{{ t('xpeng.autosync_intro_data_act') }}</p>

    <p class="text-[11px] font-bold uppercase tracking-[0.14em] text-gray-500 dark:text-gray-400 mb-2">{{ t('xpeng.autosync_intro_steps_title') }}</p>
    <ol class="space-y-2.5" :class="showManualHint ? 'mb-4' : ''">
      <li v-for="i in 5" :key="i" class="flex items-start gap-2.5 text-sm text-gray-700 dark:text-gray-300">
        <span class="shrink-0 w-5 h-5 bg-gray-950 dark:bg-gray-700 text-white rounded-sm flex items-center justify-center text-[10px] font-extrabold mt-0.5">{{ i }}</span>
        <span class="leading-relaxed">{{ t(`xpeng.autosync_intro_step${i}`) }}</span>
      </li>
    </ol>

    <p v-if="showManualHint" class="text-xs text-gray-500 dark:text-gray-400 leading-relaxed">
      {{ t('xpeng.autosync_intro_manual_hint') }}
      <button type="button" @click="$emit('manual')" class="underline font-bold cursor-pointer hover:no-underline">{{ t('xpeng.autosync_intro_manual_link') }}</button>
    </p>
  </div>
</template>
