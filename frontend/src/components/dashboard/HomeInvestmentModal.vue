<script setup lang="ts">
/**
 * Eingabe der Wallbox-Investition.
 *
 * Sitzt bewusst an der Kachel und nicht in den Einstellungen: dort faellt dem Nutzer auf,
 * dass der Wert fehlt, und genau dort ist er bereit, ihn einzutragen.
 */
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { XMarkIcon } from '@heroicons/vue/24/outline'

const props = defineProps<{ open: boolean; current: number | null }>()
const emit = defineEmits<{ (e: 'close'): void; (e: 'save', value: number | null): void }>()

const { t } = useI18n()
const input = ref<string>('')

/** Serverseitige Obergrenze spiegeln, damit der Nutzer die Ablehnung nicht erst nach
 *  dem Absenden sieht. */
const MAX_INVESTMENT = 100000

watch(() => props.open, (open) => {
  if (open) input.value = props.current != null ? String(props.current) : ''
})

const isValid = () => {
  if (input.value.trim() === '') return true // leer loescht den Wert
  const value = Number(input.value)
  return Number.isFinite(value) && value >= 0 && value <= MAX_INVESTMENT
}

function save() {
  if (!isValid()) return
  emit('save', input.value.trim() === '' ? null : Number(input.value))
}
</script>

<template>
  <div v-if="open" class="fixed inset-0 z-50 flex items-end sm:items-center justify-center bg-black/50 p-0 sm:p-4"
       role="dialog" aria-modal="true" @click.self="emit('close')">
    <div class="w-full sm:max-w-md bg-white dark:bg-gray-800 rounded-t-lg sm:rounded-sm p-5 sm:shadow-[4px_4px_0_rgba(0,0,0,0.30)]">
      <div class="flex items-start gap-3 mb-4">
        <h2 class="flex-1 text-lg font-bold text-gray-900 dark:text-gray-100">{{ t('savings.investment_title') }}</h2>
        <button type="button" class="p-1 -m-1 text-gray-400 hover:text-gray-600 dark:hover:text-gray-200"
                :aria-label="t('savings.investment_close')" @click="emit('close')">
          <XMarkIcon class="h-5 w-5" />
        </button>
      </div>

      <p class="mb-4 text-sm leading-relaxed text-gray-600 dark:text-gray-400">{{ t('savings.investment_hint') }}</p>

      <label class="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1" for="home-investment">
        {{ t('savings.investment_label') }}
      </label>
      <input id="home-investment" v-model="input" type="number" inputmode="decimal"
             min="0" :max="MAX_INVESTMENT" step="10" placeholder="1400"
             class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-sm text-sm focus:ring-green-500 focus:border-green-500" />
      <p class="mt-1.5 text-xs text-gray-400 dark:text-gray-500">{{ t('savings.investment_clear_hint') }}</p>

      <div class="mt-5 flex gap-2">
        <button type="button" :disabled="!isValid()"
                class="btn-3d flex-1 px-4 py-2 bg-green-600 text-white rounded-sm hover:bg-green-700 disabled:opacity-50 transition text-sm"
                @click="save">
          {{ t('savings.investment_save') }}
        </button>
        <button type="button"
                class="px-4 py-2 border border-gray-300 dark:border-gray-600 dark:text-gray-200 rounded-sm text-sm"
                @click="emit('close')">
          {{ t('savings.investment_cancel') }}
        </button>
      </div>
    </div>
  </div>
</template>
