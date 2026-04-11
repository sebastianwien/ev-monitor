<template>
  <div class="fixed inset-0 z-50 flex items-start justify-center pt-12 px-4 pb-4 bg-black/50">
    <div class="bg-white dark:bg-gray-800 rounded-2xl shadow-xl w-full max-w-lg flex flex-col max-h-[90vh]">
      <!-- Header -->
      <div class="flex items-center justify-between p-5 border-b border-gray-100 dark:border-gray-700">
        <h2 class="text-lg font-semibold text-gray-900 dark:text-gray-100">{{ t('manual_import.title') }}</h2>
        <button @click="$emit('close')" class="text-gray-400 dark:text-gray-500 hover:text-gray-600 dark:hover:text-gray-300 transition-colors">
          <XMarkIcon class="w-5 h-5" />
        </button>
      </div>

      <div class="overflow-y-auto p-5 space-y-5">
        <!-- Info -->
        <div class="bg-amber-50 dark:bg-amber-900/30 border border-amber-200 dark:border-amber-700 rounded-xl p-3 flex gap-2.5">
          <ExclamationTriangleIcon class="w-4 h-4 text-amber-500 dark:text-amber-400 shrink-0 mt-0.5" />
          <p class="text-sm text-amber-800 dark:text-amber-200" v-html="t('manual_import.format_info')" />
        </div>

        <!-- Format toggle -->
        <div class="flex gap-2">
          <button
            v-for="fmt in (['csv', 'json'] as const)"
            :key="fmt"
            @click="selectedFormat = fmt"
            :class="['btn-3d text-sm px-4 py-1.5 rounded-lg font-medium transition-colors',
              selectedFormat === fmt
                ? 'active bg-green-600 text-white'
                : 'bg-white dark:bg-gray-700 text-gray-600 dark:text-gray-400 border border-gray-200 dark:border-gray-600 hover:bg-gray-100 dark:hover:bg-gray-600']"
          >{{ fmt.toUpperCase() }}</button>
        </div>

        <!-- Format spec -->
        <div class="bg-gray-50 dark:bg-gray-900 rounded-xl p-4 space-y-2">
          <div class="flex items-center justify-between">
            <p class="text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wide">{{ t('manual_import.template_label') }}</p>
            <button
              @click="copyTemplate"
              class="text-xs text-green-600 hover:text-green-700 font-medium flex items-center gap-1 transition-colors"
            >
              <ClipboardDocumentIcon class="w-3.5 h-3.5" />
              {{ copied ? t('manual_import.copied') : t('manual_import.copy_btn') }}
            </button>
          </div>

          <pre v-if="selectedFormat === 'csv'" class="text-xs text-gray-700 dark:text-gray-300 overflow-x-auto whitespace-pre">{{ csvTemplate }}</pre>
          <pre v-else class="text-xs text-gray-700 dark:text-gray-300 overflow-x-auto whitespace-pre">{{ jsonTemplate }}</pre>

          <p class="text-xs text-gray-500 dark:text-gray-400">
            <span v-html="t('manual_import.required_fields')" /><br>
            <span v-html="t('manual_import.date_formats')" /><br>
            <span v-html="t('manual_import.location_hint')" /><br>
            <span v-html="t('manual_import.cpo_hint')" />
          </p>
        </div>

        <!-- File upload -->
        <div class="space-y-1.5">
          <label class="block text-sm font-medium text-gray-700 dark:text-gray-300">{{ t('manual_import.upload_label') }}</label>
          <div
            class="border-2 border-dashed border-gray-200 dark:border-gray-600 rounded-xl p-6 text-center cursor-pointer hover:border-green-400 transition-colors"
            @click="fileInput?.click()"
            @dragover.prevent
            @drop.prevent="onDrop"
          >
            <ArrowUpTrayIcon class="w-8 h-8 text-gray-300 dark:text-gray-600 mx-auto mb-2" />
            <p v-if="!fileName" class="text-sm text-gray-500 dark:text-gray-400" v-html="t('manual_import.drop_hint')" />
            <p v-else class="text-sm font-medium text-gray-700 dark:text-gray-300">{{ fileName }}</p>
            <input
              ref="fileInput"
              type="file"
              :accept="selectedFormat === 'csv' ? '.csv,text/csv' : '.json,application/json'"
              class="hidden"
              @change="onFileChange"
            />
          </div>
        </div>

        <!-- Or paste -->
        <div class="space-y-1.5">
          <label class="block text-sm font-medium text-gray-700 dark:text-gray-300">{{ t('manual_import.paste_label') }}</label>
          <textarea
            v-model="rawData"
            rows="5"
            class="w-full text-xs font-mono border border-gray-200 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-xl p-3 focus:outline-none focus:ring-2 focus:ring-green-500 resize-none"
            :placeholder="selectedFormat === 'csv' ? csvTemplate : jsonTemplate"
          />
        </div>

        <!-- Result -->
        <div v-if="result" class="rounded-xl p-4 space-y-2"
          :class="result.errors > 0 && result.imported === 0 ? 'bg-red-50 dark:bg-red-900/30 border border-red-200 dark:border-red-700' : 'bg-green-50 dark:bg-green-900/30 border border-green-200 dark:border-green-700'">
          <p class="text-sm font-medium" :class="result.errors > 0 && result.imported === 0 ? 'text-red-700 dark:text-red-300' : 'text-green-700 dark:text-green-300'">
            {{ t('manual_import.result_summary', { imported: result.imported, skipped: result.skipped }) }}
            <template v-if="result.errors > 0">{{ t('manual_import.result_errors', { errors: result.errors }) }}</template>
            <template v-if="(result.warnings ?? 0) > 0">{{ t('manual_import.result_warnings', { warnings: result.warnings }) }}</template>
          </p>
          <p v-if="result.errors > 0 && result.imported === 0" class="text-xs text-red-600 dark:text-red-400">
            {{ t('manual_import.hint_all_errors') }}
          </p>
        </div>

        <!-- Column mismatch warning -->
        <div v-if="(result?.warnings ?? 0) > 0" class="bg-amber-50 dark:bg-amber-900/30 border border-amber-200 dark:border-amber-700 rounded-xl p-3 flex gap-2.5">
          <ExclamationTriangleIcon class="w-4 h-4 text-amber-500 dark:text-amber-400 shrink-0 mt-0.5" />
          <p class="text-sm text-amber-800 dark:text-amber-200">{{ t('manual_import.hint_column_mismatch') }}</p>
        </div>

        <!-- Error -->
        <p v-if="errorMsg" class="text-sm text-red-600 dark:text-red-400 bg-red-50 dark:bg-red-900/30 border border-red-200 dark:border-red-700 rounded-xl p-3">{{ errorMsg }}</p>
      </div>

      <!-- Footer -->
      <div class="flex justify-end gap-3 p-5 border-t border-gray-100 dark:border-gray-700 shrink-0">
        <button
          @click="$emit('close')"
          class="btn-3d px-4 py-2 text-sm font-medium text-gray-700 dark:text-gray-300 bg-white dark:bg-gray-700 border border-gray-200 dark:border-gray-600 rounded-xl hover:bg-gray-50 dark:hover:bg-gray-600 transition-colors"
        >{{ t('manual_import.close_btn') }}</button>
        <button
          @click="runImport"
          :disabled="!rawData.trim() || loading"
          class="btn-3d px-5 py-2 text-sm font-medium text-white bg-green-600 rounded-xl hover:bg-green-700 disabled:opacity-40 disabled:cursor-not-allowed transition-colors flex items-center gap-2"
        >
          <span v-if="loading" class="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
          {{ t('manual_import.import_btn') }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { XMarkIcon, ArrowUpTrayIcon, ExclamationTriangleIcon, ClipboardDocumentIcon } from '@heroicons/vue/24/outline'
import { manualImportService, type ManualImportResult } from '../../api/manualImportService'

const { t } = useI18n()

const props = defineProps<{ carId: string }>()
const emit = defineEmits<{ close: []; imported: [count: number] }>()

const selectedFormat = ref<'csv' | 'json'>('csv')
const rawData = ref('')
const fileName = ref('')
const loading = ref(false)
const result = ref<ManualImportResult | null>(null)
const errorMsg = ref('')
const fileInput = ref<HTMLInputElement>()
const copied = ref(false)

const csvTemplate = `date,kwh,odometer_km,soc_before,soc_after,cost_eur,duration_min,location,charging_type,max_charging_power_kw,route_type,tire_type,is_public_charging,cpo_name
2025-08-31T15:07:14+02:00,32.09,7893,42,80,0,26,48.2082 16.3738,DC,150.0,,,true,IONITY`

const jsonTemplate = `[
  {
    "date": "2025-08-31T15:07:14+02:00",
    "kwh": 32.09,
    "odometer_km": 7893,
    "soc_before": 42,
    "soc_after": 80,
    "cost_eur": 0,
    "duration_min": 26,
    "location": "48.2082 16.3738",
    "charging_type": "DC",
    "max_charging_power_kw": 150.0,
    "is_public_charging": true,
    "cpo_name": "IONITY"
  }
]`

async function copyTemplate() {
  const text = selectedFormat.value === 'csv' ? csvTemplate : jsonTemplate
  await navigator.clipboard.writeText(text)
  copied.value = true
  setTimeout(() => { copied.value = false }, 2000)
}

function onFileChange(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  fileName.value = file.name
  const reader = new FileReader()
  reader.onload = (e) => { rawData.value = e.target?.result as string }
  reader.readAsText(file)
}

function onDrop(event: DragEvent) {
  const file = event.dataTransfer?.files?.[0]
  if (!file) return
  fileName.value = file.name
  const reader = new FileReader()
  reader.onload = (e) => { rawData.value = e.target?.result as string }
  reader.readAsText(file)
}

async function runImport() {
  if (!rawData.value.trim()) return
  loading.value = true
  result.value = null
  errorMsg.value = ''

  try {
    result.value = await manualImportService.importData(props.carId, selectedFormat.value, rawData.value, false)
    if (result.value.imported > 0) {
      emit('imported', result.value.imported)
    }
  } catch (e: any) {
    errorMsg.value = e?.response?.data?.error ?? t('manual_import.err_import')
  } finally {
    loading.value = false
  }
}
</script>
