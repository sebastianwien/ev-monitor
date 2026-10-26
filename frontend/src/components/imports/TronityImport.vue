<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { read, utils } from '@e965/xlsx'
import { ArrowDownTrayIcon, ArrowUpTrayIcon, ArrowPathIcon, CheckIcon, CheckCircleIcon, ExclamationCircleIcon } from '@heroicons/vue/24/outline'
import { CI_BTN_PRIMARY, CI_LABEL, CI_NOTE } from '../../utils/ciClasses'
import { tronityImportService } from '../../api/tronityImportService'
import type { ManualImportResult } from '../../api/manualImportService'
import { convertRow } from './tronityConverter'
import { useCarStore } from '../../stores/car'
import CarSelectDropdown from '../car/CarSelectDropdown.vue'
import type { Car } from '../../api/carService'

const MAX_FILE_BYTES = 5 * 1024 * 1024 // 5 MB

const { t } = useI18n()
const carStore = useCarStore()
const cars = ref<Car[]>([])
const selectedCarId = ref<string | null>(null)

const fileInput = ref<HTMLInputElement>()
const fileName = ref('')
const parsedEntries = ref<object[]>([])
const previewCount = ref<number | null>(null)
const parseError = ref('')
const loading = ref(false)
const result = ref<ManualImportResult | null>(null)
const errorMsg = ref('')

onMounted(async () => {
  cars.value = await carStore.getCars() ?? []
  if (activeCars.value.length === 1) selectedCarId.value = activeCars.value[0].id
})

const activeCars = computed(() => cars.value.filter(c => c.status === 'ACTIVE'))
const effectiveCarId = computed(() =>
  activeCars.value.length === 1 ? activeCars.value[0].id : selectedCarId.value
)

// ── XLSX parsing ──────────────────────────────────────────────────────────────

function parseFile(file: File) {
  parseError.value = ''
  parsedEntries.value = []
  previewCount.value = null
  result.value = null
  errorMsg.value = ''

  if (file.size > MAX_FILE_BYTES) {
    parseError.value = t('tronity.err_too_large')
    return
  }

  const reader = new FileReader()
  reader.onload = (e) => {
    try {
      const data = new Uint8Array(e.target!.result as ArrayBuffer)
      const workbook = read(data, { type: 'array' })
      const sheet = workbook.Sheets[workbook.SheetNames[0]]
      const rows = utils.sheet_to_json<Record<string, unknown>>(sheet, { raw: true, defval: null })

      if (!isTronityFormat(rows[0])) {
        parseError.value = t('tronity.err_format')
        return
      }

      const entries = rows.map(convertRow).filter(Boolean) as object[]
      parsedEntries.value = entries
      previewCount.value = entries.length
    } catch {
      parseError.value = t('tronity.err_read')
    }
  }
  reader.readAsArrayBuffer(file)
}

function isTronityFormat(row: Record<string, unknown> | undefined): boolean {
  if (!row) return false
  return 'Start Datum' in row && 'Geladen (kWh)' in row
}

// ── File input handlers ───────────────────────────────────────────────────────

function onFileChange(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  fileName.value = file.name
  parseFile(file)
}

function onDrop(event: DragEvent) {
  const file = event.dataTransfer?.files?.[0]
  if (!file) return
  fileName.value = file.name
  parseFile(file)
}

// ── Import ────────────────────────────────────────────────────────────────────

async function runImport() {
  if (!parsedEntries.value.length || !effectiveCarId.value) return
  loading.value = true
  result.value = null
  errorMsg.value = ''

  try {
    result.value = await tronityImportService.importData(
      effectiveCarId.value,
      JSON.stringify(parsedEntries.value),
      false
    )
  } catch (e: any) {
    errorMsg.value = e?.response?.data?.error ?? t('tronity.err_import')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <!-- Abstand bringt die Sektion auf /imports (ImportSection) -->
  <div class="grid gap-4">
    <div>
      <h2 class="mb-1 text-[18px] font-bold tracking-tight text-gray-900 dark:text-gray-100">{{ t('tronity.title') }}</h2>
      <p class="text-[15px] text-gray-600 dark:text-gray-300" v-html="t('tronity.desc')" />
    </div>

    <ul class="grid gap-2">
      <li v-for="i in 3" :key="i" class="flex items-start gap-2.5 text-[15px] text-gray-700 dark:text-gray-300">
        <CheckIcon class="mt-0.5 h-5 w-5 shrink-0 text-gray-400" aria-hidden="true" />{{ t(`tronity.feature_${i}`) }}
      </li>
    </ul>

    <!-- Car selector -->
    <div v-if="activeCars.length > 1" class="grid gap-1.5">
      <label :class="CI_LABEL">{{ t('tronity.car_label') }}</label>
      <CarSelectDropdown :cars="activeCars" v-model="selectedCarId" />
    </div>
    <!-- Ohne Auto fehlt etwas: Amber mit Handlung -->
    <p v-if="activeCars.length === 0" class="rounded-2xl bg-amber-50 p-4 text-sm text-gray-800 dark:bg-amber-900/20 dark:text-gray-100">
      {{ t('tronity.no_car') }}
      <router-link to="/cars" class="ml-1 font-semibold underline hover:no-underline">{{ t('tronity.add_car') }}</router-link>
    </p>

    <!-- File upload -->
    <div class="grid gap-1.5">
      <label :class="CI_LABEL">{{ t('tronity.file_label') }}</label>
      <div
        class="cursor-pointer rounded-2xl border-2 border-dashed border-gray-300 bg-gray-50 p-6 text-center transition-colors hover:border-gray-500 dark:border-gray-600 dark:bg-gray-900/40 dark:hover:border-gray-400"
        @click="fileInput?.click()"
        @dragover.prevent
        @drop.prevent="onDrop"
      >
        <ArrowUpTrayIcon class="mx-auto mb-2 h-8 w-8 text-gray-400" aria-hidden="true" />
        <p v-if="!fileName" class="text-sm text-gray-600 dark:text-gray-400">
          {{ t('tronity.file_drop') }} <span class="font-semibold text-gray-900 underline underline-offset-2 dark:text-gray-100">{{ t('tronity.file_select') }}</span>
        </p>
        <p v-else class="font-mono text-sm font-bold text-gray-900 dark:text-gray-100">{{ fileName }}</p>
        <input
          ref="fileInput"
          type="file"
          accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
          class="hidden"
          @change="onFileChange"
        />
      </div>
      <p v-if="parseError" role="alert" class="text-[13px] font-semibold text-gray-900 dark:text-gray-100">{{ parseError }}</p>
      <p v-if="previewCount !== null && !parseError" class="flex items-center gap-1 text-[13px] font-semibold tabular-nums text-gray-900 dark:text-gray-100">
        <CheckIcon class="h-4 w-4 text-green-700 dark:text-green-400" aria-hidden="true" />{{ t('tronity.entries_found', { n: previewCount }) }}
      </p>
    </div>

    <!-- Result -->
    <p v-if="result" role="status" :class="[CI_NOTE, 'flex items-start gap-2 py-3 font-semibold tabular-nums text-gray-900 dark:text-gray-100']">
      <ExclamationCircleIcon v-if="result.errors > 0 && result.imported === 0" class="h-5 w-5 shrink-0 text-gray-600 dark:text-gray-300" aria-hidden="true" />
      <CheckCircleIcon v-else class="h-5 w-5 shrink-0 text-green-700 dark:text-green-400" aria-hidden="true" />
      <span>
        {{ t('tronity.result', { imported: result.imported, skipped: result.skipped }) }}
        <template v-if="result.errors > 0">{{ t('tronity.result_errors', { errors: result.errors }) }}</template>
      </span>
    </p>

    <p v-if="errorMsg" role="alert" :class="[CI_NOTE, 'py-3 font-semibold text-gray-900 dark:text-gray-100']">{{ errorMsg }}</p>

    <button type="button" :class="[CI_BTN_PRIMARY, 'w-full']" :disabled="!parsedEntries.length || loading || !effectiveCarId" @click="runImport">
      <ArrowPathIcon v-if="loading" class="h-5 w-5 animate-spin" aria-hidden="true" />
      <ArrowDownTrayIcon v-else class="h-5 w-5" aria-hidden="true" />
      {{ t('tronity.import_btn') }}
    </button>
  </div>
</template>
