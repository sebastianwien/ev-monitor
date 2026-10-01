<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { BoltIcon, HomeIcon, SparklesIcon } from '@heroicons/vue/24/outline'
import type { ChargingSuggestion } from './wizardLogic'
import { stationSub } from './stationSub'

/**
 * Die Trefferkarte: der Nutzer steht an einem Ort, an dem er schon geladen hat. Ein Tap
 * übernimmt Ort, Ladeart und die Ladekarte vom letzten Mal. Keine Ja/Nein-Frage - die
 * Karte ist der Button, der Ausweg darunter öffnet die Liste.
 */
const props = defineProps<{ suggestion: ChargingSuggestion; providerLabel: string | null }>()
const emit = defineEmits<{ accept: []; dismiss: [] }>()
const { t, locale } = useI18n()

const site = computed(() => props.suggestion.kind === 'SITE' ? props.suggestion.site : null)
const lastUsed = computed(() => site.value?.lastUsedAt
  ? new Date(site.value.lastUsedAt).toLocaleDateString(locale.value === 'en' ? 'en-GB' : locale.value, { day: 'numeric', month: 'short' })
  : null)
/** "24. Sept. · EnBW mobility+ · 7×" - nur das, was der Fahrer zum Wiedererkennen braucht */
const line = computed(() => [lastUsed.value, props.providerLabel, site.value ? `${site.value.usageCount}×` : null]
  .filter(Boolean).join(' · '))
</script>

<template>
  <div data-testid="suggestion-card" class="rounded-sm border-2 border-indigo-600 bg-indigo-50 dark:bg-indigo-900/30 p-4 space-y-3">
    <p class="flex items-center gap-1.5 text-[11px] font-semibold uppercase tracking-wide text-indigo-700 dark:text-indigo-300">
      <SparklesIcon class="h-4 w-4" />{{ t(site ? 'logwizard.suggestion_site' : 'logwizard.suggestion_private') }}
    </p>
    <div class="flex items-start gap-3">
      <span class="w-9 h-9 rounded-sm bg-white dark:bg-gray-800 grid place-items-center flex-shrink-0 text-gray-600 dark:text-gray-300">
        <BoltIcon v-if="site" class="h-5 w-5" /><HomeIcon v-else class="h-5 w-5" />
      </span>
      <div class="min-w-0 flex-1">
        <b class="block text-lg leading-tight font-semibold text-gray-800 dark:text-gray-100 text-balance">{{ site ? site.name : t('logwizard.suggestion_private_title') }}</b>
        <small v-if="site" class="block text-sm text-gray-600 dark:text-gray-300 tabular-nums">{{ line }}</small>
        <small v-if="site" class="block text-xs text-gray-500 dark:text-gray-400">{{ stationSub(site, t) }}</small>
        <small v-else class="block text-sm text-gray-600 dark:text-gray-300">{{ t('logwizard.suggestion_private_sub') }}</small>
      </div>
    </div>
    <button type="button" data-testid="suggestion-accept" @click="emit('accept')"
      class="btn-3d w-full min-h-12 rounded-sm bg-indigo-600 text-white font-semibold hover:bg-indigo-700 transition">
      {{ t('logwizard.suggestion_accept') }}
    </button>
    <button type="button" data-testid="suggestion-dismiss" @click="emit('dismiss')"
      class="block mx-auto min-h-9 px-3 text-sm text-gray-500 dark:text-gray-400 underline underline-offset-2 hover:text-gray-800 dark:hover:text-gray-100">
      {{ t('logwizard.nearby_other_place') }}
    </button>
  </div>
</template>
