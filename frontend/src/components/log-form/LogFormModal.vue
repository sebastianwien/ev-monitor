<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { BoltIcon, ChevronUpIcon, XMarkIcon } from '@heroicons/vue/24/outline'
import BottomSheet from '../shared/BottomSheet.vue'
import LogWizard from '../log-wizard/LogWizard.vue'
import { useIsMobile } from '../../composables/useIsMobile'

/**
 * Erfassen als Sheet: mobil fast bildschirmhoch von unten, nach unten ziehen schließt wie bei
 * den anderen Sheets. Mit `minimizable` und schon gemachten Eingaben minimiert der Zug stattdessen
 * zur Leiste über der Bottom-Nav (v-model:minimized); Tipp darauf macht weiter, das X verwirft.
 * Ab sm ein zentrierter Dialog.
 */
const props = defineProps<{ carId?: string | null; initialLoggedAt?: string | null; minimizable?: boolean; minimized?: boolean }>()
const emit = defineEmits<{
  close: []
  saved: []
  'update:minimized': [value: boolean]
}>()
const { t } = useI18n()

const sheet = ref<InstanceType<typeof BottomSheet> | null>(null)
const wizard = ref<InstanceType<typeof LogWizard> | null>(null)
// Gleiche Grenze wie das Ziehen im BottomSheet: nur wo das Panel unten anliegt
const phone = useIsMobile('(max-width: 639px)')
const canMinimize = computed(() => !!props.minimizable && phone.value && !!wizard.value?.hasInput)

const close = () => sheet.value?.requestClose()
const expand = () => emit('update:minimized', false)

const handleEscape = (e: KeyboardEvent) => {
  if (e.key === 'Escape' && !props.minimized) close()
}
onMounted(() => document.addEventListener('keydown', handleEscape))
onUnmounted(() => document.removeEventListener('keydown', handleEscape))

// Die Leiste lässt sich auch hochwischen, nicht nur antippen
let swipeStart: number | null = null
const onBarTouchStart = (e: TouchEvent) => { swipeStart = e.touches[0]?.clientY ?? null }
const onBarTouchEnd = (e: TouchEvent) => {
  const end = e.changedTouches[0]?.clientY
  if (swipeStart != null && end != null && swipeStart - end > 24) expand()
  swipeStart = null
}
</script>

<template>
  <BottomSheet ref="sheet" edge :label="t('logform.title')" testid="log-form-sheet"
    panel-class="h-[90dvh] sm:h-auto sm:max-w-xl"
    :minimizable="canMinimize" :minimized="minimized"
    @minimize="emit('update:minimized', true)" @close="emit('close')">
    <div class="mx-auto mt-2 h-1.5 w-10 shrink-0 rounded-full bg-gray-200 sm:hidden dark:bg-gray-700" aria-hidden="true" />
    <LogWizard ref="wizard" embedded :car-id="carId" :initial-logged-at="initialLoggedAt"
      @success="emit('saved'); close()" @cancel="close" />

    <!-- Minimiert: die Leiste deckt den Wizard-Kopf ab, die Eingaben darunter bleiben erhalten -->
    <div v-if="minimized" data-testid="log-form-bar" class="absolute inset-x-0 top-0 z-10 h-16 bg-white dark:bg-gray-800"
      @touchstart.passive="onBarTouchStart" @touchend="onBarTouchEnd">
      <div class="mx-auto mt-2 h-1.5 w-10 rounded-full bg-gray-200 dark:bg-gray-700" aria-hidden="true" />
      <div class="flex items-center gap-1 px-2">
        <button type="button" class="flex min-h-11 min-w-0 flex-1 items-center gap-2 rounded-xl px-2 text-left" @click="expand">
          <BoltIcon class="h-5 w-5 shrink-0 text-green-600" aria-hidden="true" />
          <span class="truncate text-[15px] font-semibold text-gray-900 dark:text-gray-50">{{ t('logform.title') }}</span>
          <ChevronUpIcon class="ml-auto h-5 w-5 shrink-0 text-gray-500 dark:text-gray-400" aria-hidden="true" />
        </button>
        <button type="button" :aria-label="t('common.cancel')" class="grid h-11 w-11 shrink-0 place-items-center rounded-full text-gray-600 hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-white/10" @click="close">
          <XMarkIcon class="h-5 w-5" />
        </button>
      </div>
    </div>
  </BottomSheet>
</template>
