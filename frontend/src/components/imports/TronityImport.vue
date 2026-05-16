<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { read, utils } from '@e965/xlsx'
import { ArrowDownTrayIcon, ArrowUpTrayIcon } from '@heroicons/vue/24/outline'
import { tronityImportService } from '../../api/tronityImportService'
import type { ManualImportResult } from '../../api/manualImportService'
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

function convertRow(row: Record<string, unknown>): object | null {
  const dateRaw = row['Start Datum']
  const kwh = row['Geladen (kWh)']

  if (typeof dateRaw !== 'string' || typeof kwh !== 'number') return null

  const date = convertDate(dateRaw)
  if (!date) return null

  const entry: Record<string, unknown> = { date, kwh }

  const rawJson = JSON.stringify(row)
  if (rawJson.length <= 2000) entry.raw_import_data = rawJson

  const odometer = row['Kilometer (km)']
  if (typeof odometer === 'number') entry.odometer_km = Math.round(odometer)

  const socBefore = row['Start Level']
  if (typeof socBefore === 'number') entry.soc_before = Math.round(socBefore)

  const socAfter = row['Ende Level']
  if (typeof socAfter === 'number') entry.soc_after = Math.round(socAfter)

  const cost = row['Kosten (EUR)']
  if (typeof cost === 'number') entry.cost_eur = cost

  const durationRaw = row['Dauer']
  if (typeof durationRaw === 'string') {
    const mins = convertDuration(durationRaw)
    if (mins !== null) entry.duration_min = mins
  }

  const lat = row['Breitengrad']
  const lon = row['Längengrad']
  if (typeof lat === 'number' && typeof lon === 'number') {
    entry.location = `${lat},${lon}`
  }

  const isAc = row['AC']
  if (typeof isAc === 'boolean') entry.charging_type = isAc ? 'AC' : 'DC'

  const maxKw = row['Max (kW)']
  if (typeof maxKw === 'number') entry.max_charging_power_kw = maxKw

  return entry
}

// "15.03.2026 20:10" → "2026-03-15 20:10"
function convertDate(raw: string): string | null {
  const m = raw.match(/^(\d{2})\.(\d{2})\.(\d{4})\s+(\d{2}:\d{2})$/)
  if (!m) return null
  return `${m[3]}-${m[2]}-${m[1]} ${m[4]}`
}

// "14:00" → 840, "00:54" → 54
function convertDuration(raw: string): number | null {
  const m = raw.match(/^(\d+):(\d{2})$/)
  if (!m) return null
  return parseInt(m[1]) * 60 + parseInt(m[2])
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
  <div class="p-4 md:p-5 space-y-4">
    <!-- Header -->
    <div>
      <p class="text-orange-600 dark:text-orange-400 text-[11px] font-bold uppercase tracking-[0.14em] mb-2 flex items-center gap-2">
        <span class="inline-flex w-5 h-5 bg-orange-500 text-gray-950 rounded-sm items-center justify-center text-[10px] font-extrabold">TR</span>
        Tronity Import
      </p>
      <h2 class="text-lg md:text-xl font-bold text-gray-900 dark:text-white tracking-tight mb-1">{{ t('tronity.title') }}</h2>
      <p class="text-sm text-gray-600 dark:text-gray-300 font-medium" v-html="t('tronity.desc')" />
    </div>

    <ul class="space-y-2">
      <li v-for="i in 3" :key="i" class="flex items-start gap-2.5 text-sm text-gray-700 dark:text-gray-300">
        <span class="shrink-0 w-5 h-5 bg-orange-500 text-gray-950 rounded-sm flex items-center justify-center text-[10px] font-extrabold mt-0.5">→</span>
        <span class="font-medium">{{ t(`tronity.feature_${i}`) }}</span>
      </li>
    </ul>

    <!-- Car selector -->
    <div v-if="activeCars.length > 1" class="space-y-1.5">
      <label class="block text-[11px] font-bold uppercase tracking-wider text-gray-500 dark:text-gray-400">{{ t('tronity.car_label') }}</label>
      <CarSelectDropdown :cars="activeCars" v-model="selectedCarId" />
    </div>
    <p v-if="activeCars.length === 0" class="text-sm font-medium border-l-2 border-amber-500 bg-amber-50 dark:bg-amber-950/30 text-gray-700 dark:text-gray-200 px-4 py-3 rounded-r-sm">
      {{ t('tronity.no_car') }}
      <router-link to="/cars" class="font-bold underline hover:no-underline ml-1">{{ t('tronity.add_car') }}</router-link>
    </p>

    <!-- File upload -->
    <div class="space-y-1.5">
      <label class="block text-[11px] font-bold uppercase tracking-wider text-gray-500 dark:text-gray-400">{{ t('tronity.file_label') }}</label>
      <div
        class="border-2 border-dashed border-gray-400 dark:border-gray-600 bg-gray-50 dark:bg-gray-900 rounded-sm p-6 text-center cursor-pointer hover:border-orange-500 dark:hover:border-orange-500 transition-colors"
        @click="fileInput?.click()"
        @dragover.prevent
        @drop.prevent="onDrop"
      >
        <ArrowUpTrayIcon class="w-8 h-8 text-gray-400 mx-auto mb-2" />
        <p v-if="!fileName" class="text-sm text-gray-600 dark:text-gray-400 font-medium">
          {{ t('tronity.file_drop') }} <span class="text-orange-600 dark:text-orange-400 font-bold underline underline-offset-2">{{ t('tronity.file_select') }}</span>
        </p>
        <p v-else class="text-sm font-bold text-gray-900 dark:text-gray-100 font-mono">{{ fileName }}</p>
        <input
          ref="fileInput"
          type="file"
          accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
          class="hidden"
          @change="onFileChange"
        />
      </div>
      <p v-if="parseError" class="text-xs text-red-600 dark:text-red-400 font-medium">{{ parseError }}</p>
      <p v-if="previewCount !== null && !parseError" class="text-[11px] font-bold uppercase tracking-wider text-emerald-700 dark:text-emerald-400">
        ✓ {{ t('tronity.entries_found', { n: previewCount }) }}
      </p>
    </div>

    <!-- Result -->
    <div v-if="result"
         :class="['border-l-2 px-4 py-3 rounded-r-sm',
                  result.errors > 0 && result.imported === 0
                    ? 'border-red-500 bg-red-50 dark:bg-red-950/40'
                    : 'border-emerald-500 bg-emerald-50 dark:bg-emerald-950/40']">
      <p class="text-sm font-medium"
         :class="result.errors > 0 && result.imported === 0 ? 'text-red-900 dark:text-red-200' : 'text-emerald-900 dark:text-emerald-200'">
        {{ t('tronity.result', { imported: result.imported, skipped: result.skipped }) }}
        <template v-if="result.errors > 0">{{ t('tronity.result_errors', { errors: result.errors }) }}</template>
      </p>
    </div>

    <p v-if="errorMsg" class="text-sm font-medium border-l-2 border-red-500 bg-red-50 dark:bg-red-950/40 text-red-900 dark:text-red-200 px-4 py-3 rounded-r-sm">{{ errorMsg }}</p>

    <!-- Button -->
    <button
      @click="runImport"
      :disabled="!parsedEntries.length || loading || !effectiveCarId"
      class="w-full inline-flex items-center justify-center gap-2 bg-orange-500 hover:bg-orange-400 disabled:bg-gray-300 dark:disabled:bg-gray-700 disabled:text-gray-500 disabled:cursor-not-allowed text-gray-950 font-bold uppercase tracking-wider text-xs px-5 py-3 rounded-sm border-2 border-orange-500 disabled:border-gray-300 dark:disabled:border-gray-700 shadow-[2px_2px_0_0_#030712] disabled:shadow-none active:translate-x-[3px] active:translate-y-[3px] active:shadow-none transition-[transform,box-shadow] duration-75"
    >
      <span v-if="loading" class="w-4 h-4 border-2 border-gray-950/30 border-t-gray-950 rounded-full animate-spin" />
      <ArrowDownTrayIcon v-else class="h-4 w-4" />
      {{ t('tronity.import_btn') }}
    </button>
  </div>
</template>
