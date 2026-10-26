<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { XMarkIcon, ArrowDownTrayIcon, ClipboardDocumentIcon, CheckIcon, ArrowTopRightOnSquareIcon } from '@heroicons/vue/24/outline'
import BottomSheet from '../shared/BottomSheet.vue'
import { CI_BTN_PRIMARY, CI_BTN_SECONDARY, CI_ICON_BTN, CI_LABEL } from '../../utils/ciClasses'
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
  <BottomSheet edge :label="t('eu_data_act_sync.authority.title')" @close="emit('close')">
    <template #default="{ close }">
      <div class="flex max-h-[92vh] flex-col sm:max-h-[90vh]" data-testid="euda-authority-dialog">
        <div class="flex items-center justify-between gap-3 border-b border-gray-200 px-4 pb-3 pt-3 sm:px-6 sm:pt-6 dark:border-white/10">
          <h2 class="text-xl font-extrabold text-gray-900 dark:text-gray-50">{{ t('eu_data_act_sync.authority.title') }}</h2>
          <button type="button" :class="CI_ICON_BTN" :aria-label="t('common.close')" @click="close">
            <XMarkIcon class="h-5 w-5" aria-hidden="true" />
          </button>
        </div>

        <div class="grid gap-6 overflow-y-auto px-4 py-4 text-sm text-gray-700 sm:px-6 dark:text-gray-300">
          <p>{{ t('eu_data_act_sync.authority.intro') }}</p>

          <!-- 1. Beleg -->
          <section class="grid gap-2">
            <h3 :class="CI_LABEL">1. {{ t('eu_data_act_sync.authority.step_evidence') }}</h3>
            <p class="text-[13px]">{{ t('eu_data_act_sync.authority.evidence_note') }}</p>
            <button type="button" data-testid="euda-evidence-pdf" :disabled="pdfBusy" :class="[CI_BTN_PRIMARY, 'justify-self-start']" @click="downloadPdf">
              <ArrowDownTrayIcon class="h-5 w-5" aria-hidden="true" />
              {{ t('eu_data_act_sync.authority.evidence_btn') }}
            </button>
            <p v-if="pdfError" role="alert" class="text-[13px] font-semibold text-gray-900 dark:text-gray-100">{{ t('eu_data_act_sync.authority.evidence_error') }}</p>
          </section>

          <!-- 2. Formularwerte -->
          <section class="grid gap-2">
            <h3 :class="CI_LABEL">2. {{ t('eu_data_act_sync.authority.step_values') }}</h3>
            <p class="text-[13px]">{{ t('eu_data_act_sync.authority.values_note') }}</p>
            <ul class="divide-y divide-gray-200 dark:divide-white/10">
              <li v-for="v in values" :key="v.key" class="grid gap-1 py-2" :data-testid="`euda-authority-${v.key}`">
                <div class="flex items-center justify-between gap-2">
                  <span class="text-[13px] text-gray-500 dark:text-gray-400">{{ v.field }}</span>
                  <button type="button" class="inline-flex min-h-11 shrink-0 items-center gap-1 text-[13px] font-semibold text-gray-700 dark:text-gray-300" @click="copy(v.key, v.value)">
                    <component :is="copiedKey === v.key ? CheckIcon : ClipboardDocumentIcon" class="h-4 w-4" aria-hidden="true" />
                    {{ copiedKey === v.key ? t('eu_data_act_sync.authority.copied') : t('eu_data_act_sync.authority.copy') }}
                  </button>
                </div>
                <pre v-if="v.multiline" class="max-h-40 overflow-y-auto whitespace-pre-wrap font-sans text-[13px] text-gray-800 dark:text-gray-200">{{ v.value }}</pre>
                <p v-else class="break-words text-gray-800 dark:text-gray-200">{{ v.value }}</p>
              </li>
            </ul>
          </section>

          <!-- 3. Formular -->
          <section class="grid gap-2">
            <h3 :class="CI_LABEL">3. {{ t('eu_data_act_sync.authority.step_form') }}</h3>
            <p class="text-[13px]">{{ t('eu_data_act_sync.authority.form_note') }}</p>
            <a :href="AUTHORITY_FORM_URL" target="_blank" rel="noopener noreferrer" data-testid="euda-authority-form-link"
              :class="[CI_BTN_SECONDARY, 'justify-self-start']">
              <ArrowTopRightOnSquareIcon class="h-5 w-5" aria-hidden="true" />
              {{ t('eu_data_act_sync.authority.form_btn') }}
            </a>
          </section>

          <p class="text-[13px] text-gray-500 dark:text-gray-400">{{ t('eu_data_act_sync.authority.disclaimer') }}</p>
        </div>
      </div>
    </template>
  </BottomSheet>
</template>
