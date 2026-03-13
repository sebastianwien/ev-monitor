<template>
  <div class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50">
    <div class="bg-white rounded-2xl shadow-xl w-full max-w-lg flex flex-col max-h-[90vh]">
      <!-- Header -->
      <div class="flex items-center justify-between p-5 border-b border-gray-100">
        <h2 class="text-lg font-semibold text-gray-900">Ladehistorie importieren</h2>
        <button @click="$emit('close')" class="text-gray-400 hover:text-gray-600 transition-colors">
          <XMarkIcon class="w-5 h-5" />
        </button>
      </div>

      <div class="overflow-y-auto p-5 space-y-5">
        <!-- Info -->
        <p class="text-sm text-gray-600">
          Importiere vergangene Ladevorgänge aus
          <strong>TeslaMate</strong>, <strong>TeslaLogger</strong>, <strong>TeslaFi</strong>
          oder einer anderen Quelle im vorgegebenen Format.
        </p>

        <!-- Format spec -->
        <div class="bg-gray-50 rounded-xl p-4 space-y-2">
          <p class="text-xs font-medium text-gray-500 uppercase tracking-wide">Format-Vorlage</p>

          <div class="flex gap-2">
            <button
              v-for="fmt in (['csv', 'json'] as const)"
              :key="fmt"
              @click="activeFormatTab = fmt"
              :class="['text-xs px-3 py-1 rounded-lg font-medium transition-colors',
                activeFormatTab === fmt
                  ? 'bg-green-600 text-white'
                  : 'bg-white text-gray-600 border border-gray-200 hover:bg-gray-100']"
            >{{ fmt.toUpperCase() }}</button>
          </div>

          <pre v-if="activeFormatTab === 'csv'" class="text-xs text-gray-700 overflow-x-auto whitespace-pre">{{ csvTemplate }}</pre>
          <pre v-else class="text-xs text-gray-700 overflow-x-auto whitespace-pre">{{ jsonTemplate }}</pre>

          <p class="text-xs text-gray-500">
            <strong>Pflichtfelder:</strong> date, odometer_km, kwh, soc_before oder soc_after<br>
            <strong>date:</strong> ISO 8601, DD.MM.YYYY, MM/DD/YYYY oder Unix-Timestamp<br>
            <strong>location:</strong> Lat/Lon (z.B. <code>48.2082,16.3738</code>) oder Ortsname
          </p>
        </div>

        <!-- Format selector -->
        <div class="space-y-1.5">
          <label class="block text-sm font-medium text-gray-700">Format</label>
          <div class="flex gap-3">
            <label v-for="fmt in ['csv', 'json']" :key="fmt" class="flex items-center gap-2 cursor-pointer">
              <input type="radio" :value="fmt" v-model="selectedFormat" class="text-green-600" />
              <span class="text-sm text-gray-700">{{ fmt.toUpperCase() }}</span>
            </label>
          </div>
        </div>

        <!-- File upload -->
        <div class="space-y-1.5">
          <label class="block text-sm font-medium text-gray-700">Datei auswählen</label>
          <div
            class="border-2 border-dashed border-gray-200 rounded-xl p-6 text-center cursor-pointer hover:border-green-400 transition-colors"
            @click="fileInput?.click()"
            @dragover.prevent
            @drop.prevent="onDrop"
          >
            <ArrowUpTrayIcon class="w-8 h-8 text-gray-300 mx-auto mb-2" />
            <p v-if="!fileName" class="text-sm text-gray-500">
              Datei hierher ziehen oder <span class="text-green-600 font-medium">auswählen</span>
            </p>
            <p v-else class="text-sm font-medium text-gray-700">{{ fileName }}</p>
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
          <label class="block text-sm font-medium text-gray-700">Oder direkt einfügen</label>
          <textarea
            v-model="rawData"
            rows="5"
            class="w-full text-xs font-mono border border-gray-200 rounded-xl p-3 focus:outline-none focus:ring-2 focus:ring-green-500 resize-none"
            :placeholder="selectedFormat === 'csv' ? csvTemplate : jsonTemplate"
          />
        </div>

        <!-- Result -->
        <div v-if="result" class="rounded-xl p-4 space-y-2"
          :class="result.errors.length > 0 && result.imported === 0 ? 'bg-red-50' : 'bg-green-50'">
          <p class="text-sm font-medium" :class="result.errors.length > 0 && result.imported === 0 ? 'text-red-700' : 'text-green-700'">
            {{ result.imported }} importiert, {{ result.skipped }} übersprungen
            <template v-if="result.coinsAwarded > 0"> · +{{ result.coinsAwarded }} ⚡</template>
          </p>
          <ul v-if="result.errors.length > 0" class="space-y-1">
            <li v-for="err in result.errors" :key="err" class="text-xs text-red-600">{{ err }}</li>
          </ul>
        </div>

        <!-- Error -->
        <p v-if="errorMsg" class="text-sm text-red-600 bg-red-50 rounded-xl p-3">{{ errorMsg }}</p>
      </div>

      <!-- Footer -->
      <div class="flex justify-end gap-3 p-5 border-t border-gray-100 shrink-0">
        <button
          @click="$emit('close')"
          class="px-4 py-2 text-sm font-medium text-gray-700 bg-white border border-gray-200 rounded-xl hover:bg-gray-50 transition-colors"
        >Schließen</button>
        <button
          @click="runImport"
          :disabled="!rawData.trim() || loading"
          class="px-5 py-2 text-sm font-medium text-white bg-green-600 rounded-xl hover:bg-green-700 disabled:opacity-40 disabled:cursor-not-allowed transition-colors flex items-center gap-2"
        >
          <span v-if="loading" class="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
          Importieren
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { XMarkIcon, ArrowUpTrayIcon } from '@heroicons/vue/24/outline'
import { teslaLoggerService, type TeslaLoggerImportResult } from '../api/teslaLoggerService'

const props = defineProps<{ carId: string }>()
const emit = defineEmits<{ close: []; imported: [count: number] }>()

const selectedFormat = ref<'csv' | 'json'>('csv')
const activeFormatTab = ref<'csv' | 'json'>('csv')
const rawData = ref('')
const fileName = ref('')
const loading = ref(false)
const result = ref<TeslaLoggerImportResult | null>(null)
const errorMsg = ref('')
const fileInput = ref<HTMLInputElement>()

const csvTemplate = `date,odometer_km,kwh,soc_before,soc_after,cost_eur,location,duration_min,charging_type
2025-08-20T10:56:48,12345,24.5,45,80,8.50,"48.2082,16.3738",35,DC
2025-09-01T14:22:00,13102,18.2,30,72,,IONITY Frankfurt,28,AC`

const jsonTemplate = `[
  {
    "date": "2025-08-20T10:56:48",
    "odometer_km": 12345,
    "kwh": 24.5,
    "soc_before": 45,
    "soc_after": 80,
    "cost_eur": 8.50,
    "location": "48.2082,16.3738",
    "duration_min": 35,
    "charging_type": "DC"
  }
]`

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
    result.value = await teslaLoggerService.importData(props.carId, selectedFormat.value, rawData.value)
    if (result.value.imported > 0) {
      emit('imported', result.value.imported)
    }
  } catch (e: any) {
    errorMsg.value = e?.response?.data?.error ?? 'Import fehlgeschlagen'
  } finally {
    loading.value = false
  }
}
</script>
