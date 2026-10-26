<template>
  <BottomSheet edge :label="importType === 'trips' ? t('manual_import.title_trips') : t('manual_import.title')" @close="$emit('close')">
    <template #default="{ close }">
      <!-- Header -->
      <div class="flex shrink-0 items-center justify-between gap-3 border-b border-gray-200 px-4 pb-3 pt-4 sm:px-6 dark:border-white/10">
        <h2 class="text-xl font-extrabold text-gray-900 dark:text-gray-50">{{ importType === 'trips' ? t('manual_import.title_trips') : t('manual_import.title') }}</h2>
        <button type="button" :class="CI_ICON_BTN" :aria-label="t('common.close')" @click="close">
          <XMarkIcon class="h-5 w-5" aria-hidden="true" />
        </button>
      </div>

      <div class="grid content-start gap-5 overflow-y-auto px-4 py-4 sm:px-6">
        <!-- Import type: Segment-Schalter (filtert den Dialog, keine Navigation) -->
        <div role="tablist" :aria-label="t('manual_import.type_label')" class="grid grid-cols-2 rounded-full border-2 border-edge p-0.5">
          <button
            v-for="type in (['sessions', 'trips'] as const)"
            :key="type"
            type="button"
            role="tab"
            :aria-selected="importType === type ? 'true' : 'false'"
            :data-testid="`import-type-${type}`"
            @click="selectType(type)"
            :class="['min-h-11 rounded-full text-sm font-semibold transition-colors',
              importType === type
                ? 'bg-gray-900 text-white dark:bg-gray-100 dark:text-gray-900'
                : 'text-gray-600 hover:text-gray-900 dark:text-gray-400 dark:hover:text-gray-100']"
          >{{ type === 'trips' ? t('manual_import.type_trips') : t('manual_import.type_sessions') }}</button>
        </div>

        <!-- Info -->
        <p :class="[CI_NOTE, 'flex gap-2.5 py-3']">
          <InformationCircleIcon class="mt-0.5 h-5 w-5 shrink-0 text-gray-500 dark:text-gray-400" aria-hidden="true" />
          <span v-html="importType === 'trips' ? t('manual_import.format_info_trips') : t('manual_import.format_info')" />
        </p>

        <!-- Format toggle -->
        <div class="flex gap-2">
          <ChoiceChip v-for="fmt in (['csv', 'json'] as const)" :key="fmt" size="sm" :active="selectedFormat === fmt" @click="selectedFormat = fmt">
            {{ fmt.toUpperCase() }}
          </ChoiceChip>
        </div>

        <!-- Format spec -->
        <div :class="[CI_CARD, 'grid gap-2']">
          <div class="flex items-center justify-between gap-3">
            <p :class="CI_LABEL">{{ t('manual_import.template_label') }}</p>
            <button type="button" class="inline-flex min-h-11 items-center gap-1 text-[13px] font-semibold text-gray-700 hover:text-gray-900 dark:text-gray-300 dark:hover:text-gray-100" @click="copyTemplate">
              <CheckIcon v-if="copied" class="h-4 w-4 text-green-700 dark:text-green-400" aria-hidden="true" />
              <ClipboardDocumentIcon v-else class="h-4 w-4" aria-hidden="true" />
              {{ copied ? t('manual_import.copied') : t('manual_import.copy_btn') }}
            </button>
          </div>

          <pre v-if="selectedFormat === 'csv'" class="overflow-x-auto whitespace-pre rounded-xl bg-gray-100 p-3 text-[12px] text-gray-800 dark:bg-gray-900 dark:text-gray-200">{{ csvTemplate }}</pre>
          <pre v-else class="overflow-x-auto whitespace-pre rounded-xl bg-gray-100 p-3 text-[12px] text-gray-800 dark:bg-gray-900 dark:text-gray-200">{{ jsonTemplate }}</pre>

          <p v-if="importType === 'trips'" class="text-[13px] text-gray-500 dark:text-gray-400">
            <span v-html="t('manual_import.required_fields_trips')" /><br>
            <span v-html="t('manual_import.trips_fields_hint')" /><br>
            <span v-html="t('manual_import.trips_date_hint')" />
          </p>
          <p v-else class="text-[13px] text-gray-500 dark:text-gray-400">
            <span v-html="t('manual_import.required_fields')" /><br>
            <span v-html="t('manual_import.kwh_hint')" /><br>
            <span v-html="t('manual_import.date_formats')" /><br>
            <span v-html="t('manual_import.location_hint')" /><br>
            <span v-html="t('manual_import.cpo_hint')" />
          </p>
        </div>

        <!-- File upload -->
        <div class="grid gap-1.5">
          <label :class="CI_LABEL">{{ t('manual_import.upload_label') }}</label>
          <div
            class="cursor-pointer rounded-2xl border-2 border-dashed border-gray-300 p-6 text-center transition-colors hover:border-gray-500 dark:border-gray-600 dark:hover:border-gray-400"
            @click="fileInput?.click()"
            @dragover.prevent
            @drop.prevent="onDrop"
          >
            <ArrowUpTrayIcon class="mx-auto mb-2 h-8 w-8 text-gray-400" aria-hidden="true" />
            <p v-if="!fileName" class="text-sm text-gray-500 dark:text-gray-400" v-html="t('manual_import.drop_hint')" />
            <p v-else class="text-sm font-semibold text-gray-900 dark:text-gray-100">{{ fileName }}</p>
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
        <div class="grid gap-1.5">
          <label for="manual-import-paste" :class="CI_LABEL">{{ t('manual_import.paste_label') }}</label>
          <textarea
            id="manual-import-paste"
            v-model="rawData"
            rows="5"
            class="w-full resize-none rounded-xl border border-gray-200 bg-white p-3 font-mono text-[13px] text-gray-900 focus:border-green-600 focus:outline-none focus:ring-2 focus:ring-green-600/30 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
            :placeholder="selectedFormat === 'csv' ? csvTemplate : jsonTemplate"
          />
        </div>

        <!-- Result -->
        <div v-if="result" role="status" :class="[CI_NOTE, 'grid gap-1 py-3']">
          <p class="flex items-start gap-2 font-semibold tabular-nums text-gray-900 dark:text-gray-100">
            <ExclamationCircleIcon v-if="result.errors > 0 && result.imported === 0" class="h-5 w-5 shrink-0 text-gray-600 dark:text-gray-300" aria-hidden="true" />
            <CheckCircleIcon v-else class="h-5 w-5 shrink-0 text-green-700 dark:text-green-400" aria-hidden="true" />
            <span>
              {{ t('manual_import.result_summary', { imported: result.imported, skipped: result.skipped }) }}
              <template v-if="result.errors > 0">{{ t('manual_import.result_errors', { errors: result.errors }) }}</template>
              <template v-if="(result.warnings ?? 0) > 0">{{ t('manual_import.result_warnings', { warnings: result.warnings }) }}</template>
            </span>
          </p>
          <p v-if="result.errors > 0 && result.imported === 0" class="pl-7 text-[13px] text-gray-700 dark:text-gray-300">
            {{ importType === 'trips' ? t('manual_import.hint_all_errors_trips') : t('manual_import.hint_all_errors') }}
          </p>
        </div>

        <!-- Column mismatch warning: Spalten fehlen, Amber -->
        <p v-if="(result?.warnings ?? 0) > 0" class="flex gap-2.5 rounded-2xl bg-amber-50 p-4 text-sm text-gray-800 dark:bg-amber-900/20 dark:text-gray-100">
          <ExclamationTriangleIcon class="mt-0.5 h-5 w-5 shrink-0 text-amber-600 dark:text-amber-400" aria-hidden="true" />{{ t('manual_import.hint_column_mismatch') }}
        </p>

        <!-- Error -->
        <p v-if="errorMsg" role="alert" :class="[CI_NOTE, 'py-3 font-semibold text-gray-900 dark:text-gray-100']">{{ errorMsg }}</p>
      </div>

      <!-- Footer -->
      <div class="flex shrink-0 justify-end gap-2 border-t border-gray-200 px-4 py-3 sm:px-6 dark:border-white/10">
        <button type="button" :class="CI_BTN_SECONDARY" @click="close">{{ t('manual_import.close_btn') }}</button>
        <button type="button" data-testid="import-submit" :class="CI_BTN_PRIMARY" :disabled="!rawData.trim() || loading" @click="runImport">
          <ArrowPathIcon v-if="loading" class="h-5 w-5 animate-spin" aria-hidden="true" />
          {{ t('manual_import.import_btn') }}
        </button>
      </div>
    </template>
  </BottomSheet>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  XMarkIcon, ArrowUpTrayIcon, ArrowPathIcon, ExclamationTriangleIcon, ExclamationCircleIcon, ClipboardDocumentIcon, CheckIcon, CheckCircleIcon, InformationCircleIcon,
} from '@heroicons/vue/24/outline'
import BottomSheet from '../shared/BottomSheet.vue'
import ChoiceChip from '../shared/ChoiceChip.vue'
import { CI_BTN_PRIMARY, CI_BTN_SECONDARY, CI_ICON_BTN, CI_LABEL, CI_CARD, CI_NOTE } from '../../utils/ciClasses'
import { manualImportService, type ManualImportResult } from '../../api/manualImportService'

const { t } = useI18n()

export type ManualImportType = 'sessions' | 'trips'

const props = withDefaults(defineProps<{ carId: string; initialType?: ManualImportType }>(), { initialType: 'sessions' })
const emit = defineEmits<{ close: []; imported: [count: number, type: ManualImportType] }>()

const importType = ref<ManualImportType>(props.initialType)
const selectedFormat = ref<'csv' | 'json'>('csv')
const rawData = ref('')
const fileName = ref('')
const loading = ref(false)
const result = ref<ManualImportResult | null>(null)
const errorMsg = ref('')
const fileInput = ref<HTMLInputElement>()
const copied = ref(false)

const sessionsCsvTemplate = `date,kwh,kwh_at_vehicle,odometer_km,soc_before,soc_after,cost_eur,duration_min,location,charging_type,max_charging_power_kw,route_type,tire_type,is_public_charging,cpo_name
2025-08-31T15:07:14+02:00,32.09,30.10,7893,42,80,0,26,48.2082 16.3738,DC,150.0,,,true,IONITY`

const sessionsJsonTemplate = `[
  {
    "date": "2025-08-31T15:07:14+02:00",
    "kwh": 32.09,
    "kwh_at_vehicle": 30.10,
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

const tripsCsvTemplate = `started_at,ended_at,distance_km,odometer_start_km,odometer_end_km,soc_start,soc_end,route_type
2025-08-31T15:07:14+02:00,2025-08-31T15:52:00+02:00,41.3,7852,7893,80,62,COMBINED`

const tripsJsonTemplate = `[
  {
    "started_at": "2025-08-31T15:07:14+02:00",
    "ended_at": "2025-08-31T15:52:00+02:00",
    "distance_km": 41.3,
    "odometer_start_km": 7852,
    "odometer_end_km": 7893,
    "soc_start": 80,
    "soc_end": 62,
    "route_type": "COMBINED"
  }
]`

const csvTemplate = computed(() => importType.value === 'trips' ? tripsCsvTemplate : sessionsCsvTemplate)
const jsonTemplate = computed(() => importType.value === 'trips' ? tripsJsonTemplate : sessionsJsonTemplate)

function selectType(type: ManualImportType) {
  if (importType.value === type) return
  importType.value = type
  rawData.value = ''
  fileName.value = ''
  result.value = null
  errorMsg.value = ''
}

async function copyTemplate() {
  const text = selectedFormat.value === 'csv' ? csvTemplate.value : jsonTemplate.value
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
    result.value = importType.value === 'trips'
      ? await manualImportService.importTrips(props.carId, selectedFormat.value, rawData.value)
      : await manualImportService.importData(props.carId, selectedFormat.value, rawData.value, false)
    if (result.value.imported > 0) {
      emit('imported', result.value.imported, importType.value)
    }
  } catch (e: any) {
    errorMsg.value = e?.response?.data?.error ?? t('manual_import.err_import')
  } finally {
    loading.value = false
  }
}
</script>
