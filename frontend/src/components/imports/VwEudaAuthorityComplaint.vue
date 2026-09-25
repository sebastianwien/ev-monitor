<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { XMarkIcon, ArrowDownTrayIcon, ClipboardDocumentIcon, CheckIcon, ArrowTopRightOnSquareIcon } from '@heroicons/vue/24/outline'
import type { VwEudaSyncActivity } from '../../api/vwEudaSyncService'
import { buildVwEudaEvidenceDocument, buildVwEudaAuthorityFormValues, AUTHORITY_FORM_URL } from '../../composables/useVwEudaAuthorityComplaint'
import { downloadEvidencePdf } from '../../composables/vwEudaEvidencePdf'

/**
 * Assistent für die Beschwerde bei der Bundesnetzagentur. Die Behörde hat nur ein Web-Formular,
 * keine API und keine Vorbelegung per URL. Drei Schritte: Beleg-PDF laden, Formularwerte kopieren,
 * Formular in neuem Tab öffnen. Der Nutzer reicht selbst ein; ev-monitor ist nicht Partei.
 */
const props = defineProps<{ activity: VwEudaSyncActivity }>()
const emit = defineEmits<{ close: [] }>()

const { t, locale } = useI18n()

const values = computed(() => buildVwEudaAuthorityFormValues(props.activity, locale.value))
const pdfBusy = ref(false)
const pdfError = ref(false)
const copiedKey = ref<string | null>(null)

async function downloadPdf() {
  pdfBusy.value = true
  pdfError.value = false
  try {
    await downloadEvidencePdf(buildVwEudaEvidenceDocument(props.activity, locale.value))
  } catch {
    pdfError.value = true
  } finally {
    pdfBusy.value = false
  }
}

async function copy(key: string, text: string) {
  try {
    await navigator.clipboard.writeText(text)
    copiedKey.value = key
    setTimeout(() => { if (copiedKey.value === key) copiedKey.value = null }, 1500)
  } catch {
    copiedKey.value = null
  }
}
</script>

<template>
  <div class="fixed inset-0 z-50 flex items-end sm:items-start justify-center sm:pt-12 sm:px-4 sm:pb-4 bg-black/50" @click.self="emit('close')">
    <div class="bg-white dark:bg-gray-800 sm:rounded-2xl shadow-xl w-full max-w-lg flex flex-col max-h-[92vh] sm:max-h-[90vh]" role="dialog" aria-modal="true" data-testid="euda-authority-dialog">
      <div class="flex items-center justify-between p-4 sm:p-5 border-b border-gray-100 dark:border-gray-700">
        <h2 class="text-base sm:text-lg font-semibold text-gray-900 dark:text-gray-100">{{ t('eu_data_act_sync.authority.title') }}</h2>
        <button type="button" class="text-gray-400 dark:text-gray-500 hover:text-gray-600 dark:hover:text-gray-300" :aria-label="t('common.close')" @click="emit('close')">
          <XMarkIcon class="w-5 h-5" aria-hidden="true" />
        </button>
      </div>

      <div class="overflow-y-auto p-4 sm:p-5 space-y-6 text-sm text-gray-700 dark:text-gray-300">
        <p>{{ t('eu_data_act_sync.authority.intro') }}</p>

        <!-- 1. Beleg -->
        <section class="space-y-2">
          <h3 class="font-bold uppercase tracking-wider text-xs text-gray-500 dark:text-gray-400">1. {{ t('eu_data_act_sync.authority.step_evidence') }}</h3>
          <p class="text-xs">{{ t('eu_data_act_sync.authority.evidence_note') }}</p>
          <button
            type="button"
            data-testid="euda-evidence-pdf"
            :disabled="pdfBusy"
            class="inline-flex items-center gap-1.5 bg-gray-950 dark:bg-white text-white dark:text-gray-950 font-bold uppercase tracking-wider text-[11px] px-4 py-2.5 rounded-sm border-2 border-gray-950 dark:border-white disabled:opacity-50"
            @click="downloadPdf"
          >
            <ArrowDownTrayIcon class="h-4 w-4" aria-hidden="true" />
            {{ t('eu_data_act_sync.authority.evidence_btn') }}
          </button>
          <p v-if="pdfError" class="text-xs text-red-700 dark:text-red-300">{{ t('eu_data_act_sync.authority.evidence_error') }}</p>
        </section>

        <!-- 2. Formularwerte -->
        <section class="space-y-2">
          <h3 class="font-bold uppercase tracking-wider text-xs text-gray-500 dark:text-gray-400">2. {{ t('eu_data_act_sync.authority.step_values') }}</h3>
          <p class="text-xs">{{ t('eu_data_act_sync.authority.values_note') }}</p>
          <ul class="divide-y divide-gray-200 dark:divide-gray-700">
            <li v-for="v in values" :key="v.key" class="py-2 space-y-1" :data-testid="`euda-authority-${v.key}`">
              <div class="flex items-start justify-between gap-2">
                <span class="text-xs text-gray-500 dark:text-gray-400">{{ v.field }}</span>
                <button type="button" class="shrink-0 inline-flex items-center gap-1 text-[11px] font-bold uppercase tracking-wider text-gray-600 dark:text-gray-300" @click="copy(v.key, v.value)">
                  <component :is="copiedKey === v.key ? CheckIcon : ClipboardDocumentIcon" class="h-3.5 w-3.5" aria-hidden="true" />
                  {{ copiedKey === v.key ? t('eu_data_act_sync.authority.copied') : t('eu_data_act_sync.authority.copy') }}
                </button>
              </div>
              <pre v-if="v.multiline" class="whitespace-pre-wrap font-sans text-xs text-gray-800 dark:text-gray-200 max-h-40 overflow-y-auto">{{ v.value }}</pre>
              <p v-else class="text-gray-800 dark:text-gray-200 break-words">{{ v.value }}</p>
            </li>
          </ul>
        </section>

        <!-- 3. Formular -->
        <section class="space-y-2">
          <h3 class="font-bold uppercase tracking-wider text-xs text-gray-500 dark:text-gray-400">3. {{ t('eu_data_act_sync.authority.step_form') }}</h3>
          <p class="text-xs">{{ t('eu_data_act_sync.authority.form_note') }}</p>
          <a
            :href="AUTHORITY_FORM_URL"
            target="_blank"
            rel="noopener noreferrer"
            data-testid="euda-authority-form-link"
            class="inline-flex items-center gap-1.5 text-[11px] font-bold uppercase tracking-wider text-gray-600 dark:text-gray-300 underline hover:no-underline"
          >
            <ArrowTopRightOnSquareIcon class="h-4 w-4" aria-hidden="true" />
            {{ t('eu_data_act_sync.authority.form_btn') }}
          </a>
        </section>

        <p class="text-xs text-gray-500 dark:text-gray-400">{{ t('eu_data_act_sync.authority.disclaimer') }}</p>
      </div>
    </div>
  </div>
</template>
