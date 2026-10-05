<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ChevronLeftIcon, XMarkIcon } from '@heroicons/vue/24/outline'
import { INPUT_STEPS, type WizardStep } from './wizardLogic'
import { useIsMobile } from '../../composables/useIsMobile'
import { useVisualViewportBox } from '../../composables/useVisualViewportBox'

const props = defineProps<{
  step: WizardStep
  question: string
  hint?: string
  canProceed: boolean
  primaryLabel: string
  saving?: boolean
}>()
const emit = defineEmits<{ back: []; next: []; blocked: []; cancel: [] }>()
/** Unvollständig bleibt der Button fokussierbar (aria-disabled statt disabled): ein Druck sagt, was fehlt. */
const onPrimary = () => { if (props.saving) return; if (props.canProceed) emit('next'); else emit('blocked') }
const { t } = useI18n()
const isInputStep = computed(() => props.step <= INPUT_STEPS)
// Mobile: Rahmen an den sichtbaren Ausschnitt binden. iOS Safari verschiebt bei offener
// Tastatur nur diesen Ausschnitt - ohne Bindung wandert der Kopf nach oben aus dem Bild.
const isMobile = useIsMobile()
const viewport = useVisualViewportBox()
const frameStyle = computed(() => isMobile.value && viewport.value
  ? { top: `${viewport.value.top}px`, height: `${viewport.value.height}px` } : undefined)
// Mobil scrollt nur dieser Container, nicht die Seite - jeder Schritt beginnt oben
const scroller = ref<HTMLElement | null>(null)
watch(() => props.step, () => scroller.value?.scrollTo({ top: 0 }))
</script>

<template>
  <!-- Mobile: fest auf den Viewport gespannt - Kopf und Footer bleiben stehen, nur der Inhalt
       dazwischen scrollt. Die Seite selbst kann nicht mehr scrollen, der Balken laeuft nie raus. -->
  <!-- Deutlicher Fokusring für alles im Wizard: der Browser-Standard (1 px blau) verschwindet auf Indigo und grauen Kacheln -->
  <div :style="frameStyle" class="[&_:focus-visible]:outline-2 [&_:focus-visible]:outline-offset-2 [&_:focus-visible]:outline-indigo-500 fixed inset-0 z-50 flex flex-col bg-white dark:bg-gray-800 pt-[env(safe-area-inset-top)] md:static md:pt-0 md:min-h-0">
    <header class="px-4 pt-4 pb-3 md:px-6">
      <div class="flex items-center justify-between">
        <button v-if="step > 1" type="button" :aria-label="t('common.back')" @click="emit('back')"
          class="w-8 h-8 -ml-2 flex items-center justify-center rounded-sm text-gray-500 hover:bg-gray-100 dark:hover:bg-gray-700">
          <ChevronLeftIcon class="h-5 w-5" />
        </button>
        <div v-else class="w-8" />
        <!-- Die Frage steht in der Kopfzeile statt eines Zaehlers "Schritt x von y": der Balken zaehlt schon. -->
        <h1 class="flex-1 min-w-0 px-2 text-base md:text-xl font-bold text-gray-800 dark:text-gray-100 text-center truncate">{{ question }}</h1>
        <button type="button" :aria-label="t('common.cancel')" @click="emit('cancel')"
          class="w-8 h-8 -mr-2 flex items-center justify-center rounded-sm text-gray-400 hover:text-gray-600 hover:bg-gray-100 dark:hover:text-gray-200 dark:hover:bg-gray-700">
          <XMarkIcon class="h-5 w-5" />
        </button>
      </div>
      <!-- Zwei Eingabeschritte zaehlen; die Pruefseite danach ist eine Bestaetigung ohne Zaehler und Balken -->
      <div v-if="isInputStep" class="flex gap-1 mt-3" role="progressbar" :aria-valuenow="step" :aria-valuemin="1" :aria-valuemax="INPUT_STEPS">
        <i v-for="i in INPUT_STEPS" :key="i" :class="['flex-1 h-1 rounded-sm', i <= step ? 'bg-indigo-600' : 'bg-gray-200 dark:bg-gray-700']" />
      </div>
      <!-- Immer im DOM, damit Screenreader den Hinweis vorlesen, sobald er erscheint -->
      <p role="status" aria-live="polite" :class="['text-sm text-center text-amber-700 dark:text-amber-400', hint && 'mt-2']">{{ hint }}</p>
    </header>

    <!-- Inhalt am unteren Rand: Tap-Ziele liegen so ueber dem Footer in Daumenreichweite.
         min-h-full statt fester Hoehe, damit justify-end bei langem Inhalt nichts oben abschneidet. -->
    <div ref="scroller" data-wizard-scroller class="flex-1 min-h-0 overflow-y-auto">
      <div class="min-h-full flex flex-col justify-end md:justify-start px-4 pb-4 md:px-6">
        <slot />
      </div>
    </div>

    <!-- Ziele für Schritt-eigene Werkzeuge in Daumenreichweite (Schritt 1: Spracheingabe):
         note als Streifen über dem Footer, lead links neben der Hauptaktion. Bleiben leer, wenn niemand sie füllt. -->
    <div id="wizard-footer-note" />
    <footer class="bg-white dark:bg-gray-800 border-t border-gray-200 dark:border-gray-700 px-4 pt-3 pb-[calc(0.75rem+env(safe-area-inset-bottom))] md:px-6 md:pb-3 flex items-center gap-3">
      <!-- Slot primary: ein Schritt kann die Hauptaktion ersetzen, z. B. "Vorschlag übernehmen" in Daumenreichweite -->
      <div id="wizard-footer-lead" class="contents" />
      <slot v-if="$slots.primary" name="primary" />
      <template v-else>
      <button v-if="step > 1" type="button" @click="emit('back')"
        class="px-3 py-3 text-sm font-medium text-gray-500 dark:text-gray-400 inline-flex items-center gap-1 rounded-sm transition hover:text-gray-800 dark:hover:text-gray-100 hover:bg-gray-100 dark:hover:bg-gray-700">
        <ChevronLeftIcon class="h-4 w-4" />{{ t('common.back') }}
      </button>
      <button type="button" data-testid="wizard-next" :disabled="saving" :aria-disabled="!canProceed" @click="onPrimary"
        :class="['flex-1 bg-indigo-600 text-white p-3 rounded-sm btn-3d font-semibold transition',
                 !canProceed || saving ? 'opacity-40 cursor-not-allowed' : 'hover:bg-indigo-700']">
        {{ saving ? t('common.saving') : primaryLabel }}
      </button>
      </template>
    </footer>
  </div>
</template>
