<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { MicrophoneIcon } from '@heroicons/vue/24/outline'
import type { VoiceFlag, VoiceUsage } from './wizardLogic'

defineProps<{ transcript: string; flags: VoiceFlag[]; usage: VoiceUsage }>()
const { t } = useI18n()
</script>

<template>
  <!-- Was das Modell gehört hat, über den Kacheln: so lässt sich jeder Wert gegen das Gesagte prüfen -->
  <div class="p-3 rounded-sm bg-indigo-50 dark:bg-indigo-900/30 space-y-2" data-testid="voice-transcript">
    <p class="flex gap-2 text-sm text-gray-700 dark:text-gray-200">
      <MicrophoneIcon class="h-4 w-4 mt-0.5 flex-shrink-0 text-indigo-600 dark:text-indigo-300" aria-hidden="true" />
      <span><b class="font-semibold">{{ t('voicelog.understood') }}:</b> <q class="italic">{{ transcript }}</q></span>
    </p>
    <p v-if="flags.length" class="flex flex-wrap items-center gap-1.5 text-xs" data-testid="voice-flags">
      <span class="text-gray-500 dark:text-gray-400">{{ t('voicelog.check') }}:</span>
      <span v-for="f in flags" :key="f" class="px-2 py-0.5 rounded-full bg-amber-100 dark:bg-amber-900/40 text-amber-800 dark:text-amber-200">{{ t(`voicelog.flag_${f}`) }}</span>
    </p>
    <p v-if="usage.limit != null && usage.remaining != null" class="text-xs text-gray-500 dark:text-gray-400" data-testid="voice-usage">
      {{ t('voicelog.usage', { remaining: usage.remaining, limit: usage.limit }) }}
    </p>
  </div>
</template>
