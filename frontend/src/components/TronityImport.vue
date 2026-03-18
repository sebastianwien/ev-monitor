<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { read, utils } from '@e965/xlsx'
import { ArrowDownTrayIcon, ArrowUpTrayIcon } from '@heroicons/vue/24/outline'
import { tronityImportService } from '../api/tronityImportService'
import type { ManualImportResult } from '../api/manualImportService'
import { useCarStore } from '../stores/car'
import CarSelectDropdown from './CarSelectDropdown.vue'
import type { Car } from '../api/carService'

const MAX_FILE_BYTES = 5 * 1024 * 1024 // 5 MB

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
    parseError.value = 'Datei zu groß (max. 5 MB)'
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
        parseError.value = 'Kein Tronity-Format erkannt. Bitte einen Tronity XLSX-Export hochladen.'
        return
      }

      const entries = rows.map(convertRow).filter(Boolean) as object[]
      parsedEntries.value = entries
      previewCount.value = entries.length
    } catch {
      parseError.value = 'Datei konnte nicht gelesen werden.'
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
    errorMsg.value = e?.response?.data?.error ?? 'Import fehlgeschlagen'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="p-6 space-y-5">
    <!-- Header -->
    <div class="flex items-start gap-4">
      <div class="bg-blue-600 rounded-lg p-2 shrink-0">
        <ArrowDownTrayIcon class="h-5 w-5 text-white" />
      </div>
      <div>
        <h2 class="font-semibold text-gray-900 dark:text-gray-100">Tronity Import</h2>
        <p class="text-sm text-gray-600 dark:text-gray-400 mt-1">
          Importiere deine Ladehistorie direkt aus dem Tronity XLSX-Export.
          In der Tronity-App unter <strong>Fahrten &amp; Laden - Laden - Export</strong> eine XLSX-Datei erstellen und hier hochladen.
        </p>
      </div>
    </div>

    <ul class="text-sm text-gray-600 dark:text-gray-400 space-y-1 list-disc list-inside">
      <li>SoC vor/nach, Kosten, GPS-Koordinaten, AC/DC - alles inklusive</li>
      <li>Duplikate werden automatisch erkannt und übersprungen</li>
      <li>Format wird automatisch erkannt - kein manuelles Mapping nötig</li>
    </ul>

    <!-- Car selector -->
    <div v-if="activeCars.length > 1" class="space-y-1.5">
      <label class="block text-sm font-medium text-gray-700 dark:text-gray-300">Fahrzeug</label>
      <CarSelectDropdown :cars="activeCars" v-model="selectedCarId" />
    </div>
    <p v-if="activeCars.length === 0" class="text-sm text-gray-500 dark:text-gray-400 italic">
      Kein aktives Fahrzeug gefunden.
      <router-link to="/cars" class="text-indigo-600 hover:underline font-medium">Fahrzeug anlegen</router-link>
    </p>

    <!-- File upload -->
    <div class="space-y-1.5">
      <label class="block text-sm font-medium text-gray-700 dark:text-gray-300">XLSX-Datei</label>
      <div
        class="border-2 border-dashed border-gray-200 dark:border-gray-600 rounded-xl p-6 text-center cursor-pointer hover:border-blue-400 transition-colors"
        @click="fileInput?.click()"
        @dragover.prevent
        @drop.prevent="onDrop"
      >
        <ArrowUpTrayIcon class="w-8 h-8 text-gray-300 mx-auto mb-2" />
        <p v-if="!fileName" class="text-sm text-gray-500 dark:text-gray-400">
          Datei hierher ziehen oder <span class="text-blue-600 font-medium">auswählen</span>
        </p>
        <p v-else class="text-sm font-medium text-gray-700 dark:text-gray-300">{{ fileName }}</p>
        <input
          ref="fileInput"
          type="file"
          accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
          class="hidden"
          @change="onFileChange"
        />
      </div>
      <p v-if="parseError" class="text-sm text-red-600">{{ parseError }}</p>
      <p v-if="previewCount !== null && !parseError" class="text-sm text-gray-500 dark:text-gray-400">
        {{ previewCount }} Einträge erkannt
      </p>
    </div>

    <!-- Result -->
    <div v-if="result" class="rounded-xl p-4"
      :class="result.errors > 0 && result.imported === 0 ? 'bg-red-50 dark:bg-red-900/30' : 'bg-green-50 dark:bg-green-900/30'">
      <p class="text-sm font-medium" :class="result.errors > 0 && result.imported === 0 ? 'text-red-700 dark:text-red-300' : 'text-green-700 dark:text-green-300'">
        {{ result.imported }} importiert, {{ result.skipped }} übersprungen
        <template v-if="result.errors > 0">, {{ result.errors }} Fehler</template>
      </p>
    </div>

    <p v-if="errorMsg" class="text-sm text-red-600 dark:text-red-400 bg-red-50 dark:bg-red-900/30 rounded-xl p-3">{{ errorMsg }}</p>

    <!-- Button -->
    <button
      @click="runImport"
      :disabled="!parsedEntries.length || loading || !effectiveCarId"
      class="btn-3d w-full flex items-center justify-center gap-2 bg-blue-600 text-white px-5 py-2.5 rounded-lg font-medium text-sm hover:bg-blue-700 disabled:opacity-40 disabled:cursor-not-allowed transition"
    >
      <span v-if="loading" class="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
      <ArrowDownTrayIcon v-else class="h-4 w-4" />
      Importieren
    </button>
  </div>
</template>
