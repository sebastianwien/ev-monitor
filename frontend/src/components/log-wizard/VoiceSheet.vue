<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { StopIcon, XMarkIcon } from '@heroicons/vue/24/outline'
import { MAX_RECORDING_MS } from '../../composables/useVoiceRecorder'
import type { VoiceEntry } from './voiceAnalytics'

/**
 * Vollbild während der Aufnahme: Spickzettel, was man sagen kann, Pegel, Restzeit und "Fertig" im
 * Daumenbereich. Die Chips sind Erinnerung, kein Live-Abhaken - das Transkript entsteht erst nach
 * dem Upload. Je mehr davon gesagt wird, desto vollständiger der Log.
 */
const props = defineProps<{ processing: boolean; elapsedMs: number; level: number; entry: VoiceEntry }>()
const emit = defineEmits<{ stop: []; cancel: [] }>()
const { t } = useI18n()

const CORE = ['place', 'energy', 'soc', 'cost', 'odometer'] as const
const MORE = ['card', 'time', 'duration', 'peak', 'type', 'vehicle_kwh', 'route', 'tires'] as const

const time = (ms: number) => { const s = Math.floor(ms / 1000); return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}` }
const progress = computed(() => Math.min(1, props.elapsedMs / MAX_RECORDING_MS))
const title = computed(() => props.processing ? t('voicelog.overlay_processing') : t('voicelog.overlay_listening'))

// Fokus in den Dialog: mit Tastatur direkt auf "Fertig", auf Touch auf den Dialog selbst (sonst doppelter Ring um den Knopf)
const root = ref<HTMLElement | null>(null)
const stopBtn = ref<HTMLButtonElement | null>(null)
onMounted(async () => {
  await nextTick()
  const touch = typeof window.matchMedia === 'function' && window.matchMedia('(pointer: coarse)').matches
  ;(touch ? root.value : stopBtn.value)?.focus({ preventScroll: true })
})
</script>

<template>
  <Teleport to="body">
    <div ref="root" tabindex="-1" role="dialog" aria-modal="true" :aria-label="title" data-testid="voice-sheet"
      class="outline-none fixed inset-0 z-[70] flex flex-col bg-white dark:bg-gray-900 pt-[env(safe-area-inset-top)] pb-[env(safe-area-inset-bottom)]"
      @keydown.escape.prevent="!processing && emit('cancel')">
      <header class="px-4 pt-4 pb-3 md:max-w-xl md:mx-auto md:w-full">
        <div class="flex items-center gap-3">
          <p data-testid="voice-status" aria-live="polite" class="flex-1 text-base font-bold text-gray-800 dark:text-gray-100">{{ title }}</p>
          <span v-if="!processing" class="text-sm tabular-nums text-gray-500 dark:text-gray-400">{{ time(elapsedMs) }} / 1:00</span>
          <button v-if="!processing" type="button" data-testid="voice-cancel" :aria-label="t('voicelog.cancel')" @click="emit('cancel')"
            class="w-11 h-11 -mr-2 grid place-items-center rounded-sm text-gray-400 hover:text-gray-600 hover:bg-gray-100 dark:hover:bg-gray-800">
            <XMarkIcon class="h-6 w-6" />
          </button>
        </div>
        <div class="mt-2 h-1 rounded-sm bg-gray-200 dark:bg-gray-700 overflow-hidden" aria-hidden="true">
          <div v-if="processing" class="h-full w-1/3 bg-indigo-600 voice-indeterminate" />
          <div v-else class="h-full bg-indigo-600 transition-[width] duration-200" :style="{ width: `${progress * 100}%` }" />
        </div>
      </header>

      <main :class="['flex-1 min-h-0 overflow-y-auto px-4 md:max-w-xl md:mx-auto md:w-full transition-opacity', processing && 'opacity-50']">
        <p class="text-sm text-gray-600 dark:text-gray-300">{{ entry === 'edit' ? t('voicelog.overlay_hint_edit') : t('voicelog.overlay_hint') }}</p>
        <h2 class="mt-4 text-[11px] uppercase tracking-wide text-gray-400 dark:text-gray-500">{{ t('voicelog.sheet_core') }}</h2>
        <ul class="mt-2 flex flex-wrap gap-2" data-testid="voice-chips-core">
          <li v-for="c in CORE" :key="c" class="px-3 py-1.5 rounded-full text-sm font-medium bg-indigo-50 text-indigo-800 dark:bg-indigo-900/40 dark:text-indigo-200">{{ t(`voicelog.chip_${c}`) }}</li>
        </ul>
        <h2 class="mt-4 text-[11px] uppercase tracking-wide text-gray-400 dark:text-gray-500">{{ t('voicelog.sheet_more') }}</h2>
        <ul class="mt-2 flex flex-wrap gap-2" data-testid="voice-chips-more">
          <li v-for="c in MORE" :key="c" class="px-3 py-1.5 rounded-full text-sm bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-300">{{ t(`voicelog.chip_${c}`) }}</li>
        </ul>
        <p class="mt-4 text-xs text-gray-500 dark:text-gray-400">{{ t('voicelog.example') }}</p>
      </main>

      <!-- Daumenbereich: ein großer Knopf beendet die Aufnahme -->
      <footer class="px-4 pt-4 pb-6 flex flex-col items-center gap-2">
        <button ref="stopBtn" type="button" data-testid="voice-done" :disabled="processing" @click="emit('stop')"
          :aria-label="processing ? title : t('voicelog.mic_stop')"
          class="relative grid place-items-center h-20 w-20 rounded-full text-white bg-red-600 hover:bg-red-700 disabled:bg-indigo-600 transition outline-none focus-visible:ring-4 focus-visible:ring-indigo-400">
          <span v-if="!processing" aria-hidden="true" class="absolute inset-0 rounded-full ring-4 ring-red-400/60 transition-transform duration-75"
            :style="{ transform: `scale(${1 + level * 0.4})` }" />
          <span v-if="processing" class="h-8 w-8 border-4 border-white/30 border-t-white rounded-full animate-spin" aria-hidden="true" />
          <StopIcon v-else class="h-8 w-8" />
        </button>
        <span class="text-sm font-semibold text-gray-700 dark:text-gray-200">{{ processing ? '' : t('voicelog.overlay_done') }}</span>
      </footer>
    </div>
  </Teleport>
</template>

<style scoped>
@keyframes voice-indeterminate { from { transform: translateX(-100%); } to { transform: translateX(300%); } }
.voice-indeterminate { animation: voice-indeterminate 1.2s ease-in-out infinite; }
@media (prefers-reduced-motion: reduce) { .voice-indeterminate { animation: none; width: 100%; opacity: .6; } }
</style>
