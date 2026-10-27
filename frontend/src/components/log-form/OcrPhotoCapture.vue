<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { CameraIcon, XMarkIcon, ArrowPathIcon, CheckIcon, CheckCircleIcon, ChevronDownIcon, ExclamationTriangleIcon, LightBulbIcon, MagnifyingGlassIcon } from '@heroicons/vue/24/outline'
import Collapse from '../shared/Collapse.vue'
import Tesseract from 'tesseract.js'

const { t } = useI18n()

interface OcrResult {
  kwh: number | null
  cost: number | null
  durationMinutes: number | null
  maxChargingPowerKw: number | null
  confidence: number
  rawText: string
}

const emit = defineEmits<{
  dataExtracted: [OcrResult]
  cancel: []
}>()

const fileInput = ref<HTMLInputElement | null>(null)
const imagePreview = ref<string | null>(null)
const isProcessing = ref(false)
const ocrResults = ref<OcrResult | null>(null)
const error = ref<string | null>(null)
const showRaw = ref(false)

// Open camera/file picker
const openCamera = () => {
  fileInput.value?.click()
}

// Handle file selection
const handleFileSelect = (event: Event) => {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]

  if (!file) return

  // Validate file type
  if (!file.type.startsWith('image/')) {
    error.value = t('ocr.err_not_image')
    return
  }

  // Create preview
  const reader = new FileReader()
  reader.onload = (e) => {
    imagePreview.value = e.target?.result as string
    error.value = null
  }
  reader.readAsDataURL(file)
}

// Extract data from text using regex patterns
const extractChargingData = (text: string): OcrResult => {
  console.log('🔍 OCR Raw Text:', text)

  // Patterns for kWh (German & English formats)
  const kwhPatterns = [
    /(\d+[.,]\d+)\s*kWh/i,
    /(\d+[.,]\d+)\s*kwh/i,
    /Energie[:\s]+(\d+[.,]\d+)/i,
    /Geladen[:\s]+(\d+[.,]\d+)/i,
    /Charged[:\s]+(\d+[.,]\d+)/i,
    /Energy[:\s]+(\d+[.,]\d+)/i
  ]

  // Patterns for cost (€ / EUR)
  const costPatterns = [
    /€\s*(\d+[.,]\d+)/,
    /(\d+[.,]\d+)\s*€/,
    /(\d+[.,]\d+)\s*EUR/i,
    /Preis[:\s]+(\d+[.,]\d+)/i,
    /Kosten[:\s]+(\d+[.,]\d+)/i,
    /Cost[:\s]+(\d+[.,]\d+)/i,
    /Price[:\s]+(\d+[.,]\d+)/i
  ]

  // Patterns for max charging power (kW) — (?!h) verhindert Match auf kWh
  const powerPatterns = [
    /(\d+[.,]\d+)\s*kW(?!h)/i,
    /(\d+)\s*kW(?!h)/i,
    /Ladeleistung[:\s]+(\d+[.,]?\d*)/i,
    /Max\.?\s*Leistung[:\s]+(\d+[.,]?\d*)/i,
    /Power[:\s]+(\d+[.,]?\d*)/i,
    /Max\.?\s*Power[:\s]+(\d+[.,]?\d*)/i,
    /Peak[:\s]+(\d+[.,]?\d*)\s*kW(?!h)/i,
  ]

  // Patterns for duration (hours + minutes)
  const durationPatterns = [
    /(\d+)h\s*(\d+)m/i,
    /(\d+)\s*h\s*(\d+)\s*m/i,
    /(\d+)\s*Stunden?\s*(\d+)?\s*Minuten?/i,
    /(\d+)\s*hours?\s*(\d+)?\s*minutes?/i,
    /Dauer[:\s]+(\d+)[:\s]+(\d+)/i,
    /Duration[:\s]+(\d+)[:\s]+(\d+)/i,
    /Zeit[:\s]+(\d+)\s*min/i,
    /Time[:\s]+(\d+)\s*min/i,
    /(\d+)\s*Minuten/i,
    /(\d+)\s*minutes/i
  ]

  // Extract kWh
  let kwh: number | null = null
  for (const pattern of kwhPatterns) {
    const match = text.match(pattern)
    if (match) {
      kwh = parseFloat(match[1].replace(',', '.'))
      console.log('✅ kWh gefunden:', kwh)
      break
    }
  }

  // Extract cost
  let cost: number | null = null
  for (const pattern of costPatterns) {
    const match = text.match(pattern)
    if (match) {
      cost = parseFloat(match[1].replace(',', '.'))
      console.log('✅ Kosten gefunden:', cost)
      break
    }
  }

  // Extract duration
  let durationMinutes: number | null = null
  for (const pattern of durationPatterns) {
    const match = text.match(pattern)
    if (match) {
      // Check if pattern has hours and minutes (2 groups)
      if (match[2]) {
        const hours = parseInt(match[1])
        const minutes = parseInt(match[2] || '0')
        durationMinutes = hours * 60 + minutes
      } else {
        // Only minutes
        durationMinutes = parseInt(match[1])
      }
      console.log('✅ Dauer gefunden:', durationMinutes, 'min')
      break
    }
  }

  // Extract max charging power
  let maxChargingPowerKw: number | null = null
  for (const pattern of powerPatterns) {
    const match = text.match(pattern)
    if (match) {
      const val = parseFloat(match[1].replace(',', '.'))
      // Sanity check: plausible charging power range 1-350 kW
      if (val >= 1 && val <= 350) {
        maxChargingPowerKw = val
        console.log('✅ Ladeleistung gefunden:', maxChargingPowerKw, 'kW')
        break
      }
    }
  }

  // Calculate confidence (how many fields were found)
  const fieldsFound = [kwh, cost, durationMinutes, maxChargingPowerKw].filter(v => v !== null).length
  const confidence = Math.round((fieldsFound / 4) * 100)

  return {
    kwh,
    cost,
    durationMinutes,
    maxChargingPowerKw,
    confidence,
    rawText: text
  }
}

// Start OCR processing
const startOcr = async () => {
  if (!imagePreview.value) return

  isProcessing.value = true
  error.value = null

  try {
    // Initialize Tesseract worker with German & English
    const worker = await Tesseract.createWorker('deu+eng', 1, {
      logger: m => {
        if (m.status === 'recognizing text') {
          console.log(`OCR Progress: ${Math.round(m.progress * 100)}%`)
        }
      }
    })

    // Recognize text
    const { data } = await worker.recognize(imagePreview.value)

    console.log('📄 OCR Complete! Text length:', data.text.length)

    // Extract charging data
    const result = extractChargingData(data.text)
    ocrResults.value = result

    // Terminate worker to free memory
    await worker.terminate()

    // Auto-apply if confidence is high enough
    if (result.confidence >= 33) {
      // At least 1 field found
      emit('dataExtracted', result)
    }
  } catch (err: any) {
    console.error('OCR Error:', err)
    error.value = t('ocr.err_ocr_failed')
  } finally {
    isProcessing.value = false
  }
}

// Apply extracted data
const applyData = () => {
  if (ocrResults.value) {
    emit('dataExtracted', ocrResults.value)
  }
}

// Reset/retry
const reset = () => {
  imagePreview.value = null
  ocrResults.value = null
  error.value = null
  if (fileInput.value) {
    fileInput.value.value = ''
  }
}

// Cancel
const cancel = () => {
  reset()
  emit('cancel')
}
</script>

<template>
  <div class="ocr-capture">
    <!-- Header -->
    <div class="mb-4 flex items-center justify-between">
      <h2 class="flex items-center gap-2 text-[17px] font-bold text-gray-900 dark:text-gray-50">
        <CameraIcon class="h-6 w-6 text-gray-900 dark:text-gray-100" aria-hidden="true" />
        {{ t('ocr.title') }}
      </h2>
      <button type="button" :aria-label="t('common.close')" @click="cancel" class="-mr-2 grid h-11 w-11 place-items-center rounded-full text-gray-600 hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-white/10">
        <XMarkIcon class="h-6 w-6" />
      </button>
    </div>

    <!-- Error Message -->
    <p v-if="error" role="alert" class="mb-4 rounded-xl bg-amber-50 px-3 py-2 text-[14px] text-amber-900 dark:bg-amber-400/10 dark:text-amber-200">
      {{ error }}
    </p>

    <!-- Step 1: Camera Button (No Image) -->
    <div v-if="!imagePreview" class="py-6 text-center">
      <CameraIcon class="mx-auto mb-4 h-16 w-16 text-gray-300 dark:text-gray-600" aria-hidden="true" />
      <p class="mb-6 text-[15px] text-gray-700 dark:text-gray-200">{{ t('ocr.step1_prompt') }}</p>

      <button type="button" @click="openCamera" class="btn-3d inline-flex min-h-12 items-center justify-center gap-2 rounded-xl border-2 border-edge bg-green-600 px-5 text-[15px] font-semibold text-white hover:bg-green-700 disabled:cursor-not-allowed disabled:opacity-40">
        <CameraIcon class="h-5 w-5" aria-hidden="true" />
        {{ t('ocr.take_photo_btn') }}
      </button>

      <p class="mt-4 inline-flex items-start gap-1.5 text-[13px] text-gray-500 dark:text-gray-400">
        <LightBulbIcon class="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />{{ t('ocr.tip') }}
      </p>

      <!-- Hidden File Input -->
      <input
        ref="fileInput"
        type="file"
        accept="image/*"
        @change="handleFileSelect"
        class="hidden"
      />
    </div>

    <!-- Step 2: Image Preview + OCR Button -->
    <div v-if="imagePreview && !ocrResults">
      <div class="mb-4">
        <img :src="imagePreview" alt="Preview" class="w-full rounded-xl border-2 border-edge" />
      </div>

      <div class="flex gap-2">
        <button type="button" @click="startOcr" :disabled="isProcessing" class="flex-1 btn-3d inline-flex min-h-12 items-center justify-center gap-2 rounded-xl border-2 border-edge bg-green-600 px-5 text-[15px] font-semibold text-white hover:bg-green-700 disabled:cursor-not-allowed disabled:opacity-40">
          <template v-if="!isProcessing"><MagnifyingGlassIcon class="h-5 w-5" aria-hidden="true" />{{ t('ocr.start_ocr_btn') }}</template>
          <template v-else>
            <ArrowPathIcon class="h-5 w-5 animate-spin" aria-hidden="true" />
            {{ t('ocr.scanning') }}
          </template>
        </button>
        <button type="button" @click="reset" :disabled="isProcessing" class="btn-3d inline-flex min-h-12 items-center justify-center rounded-xl border-2 border-edge bg-white px-5 text-[15px] font-semibold text-gray-800 hover:bg-gray-50 disabled:opacity-40 dark:bg-white/10 dark:text-gray-100 dark:hover:bg-white/15">
          {{ t('ocr.retry_btn') }}
        </button>
      </div>

      <p v-if="isProcessing" class="mt-3 text-center text-[13px] text-gray-500 dark:text-gray-400">
        {{ t('ocr.wait_hint') }}
      </p>
    </div>

    <!-- Step 3: OCR Results -->
    <div v-if="ocrResults" class="space-y-4">
      <!-- Sicherheit der Erkennung: hoch mit grünem Haken, sonst amber "prüfen" -->
      <p class="flex items-center gap-2 rounded-xl px-3 py-2 text-[14px] font-semibold"
        :class="ocrResults.confidence >= 66 ? 'bg-gray-100 text-gray-900 dark:bg-white/10 dark:text-gray-100' : 'bg-amber-50 text-amber-900 dark:bg-amber-400/10 dark:text-amber-200'">
        <CheckCircleIcon v-if="ocrResults.confidence >= 66" class="h-5 w-5 shrink-0 text-green-700 dark:text-green-400" aria-hidden="true" />
        <ExclamationTriangleIcon v-else class="h-5 w-5 shrink-0" aria-hidden="true" />
        <span v-if="ocrResults.confidence >= 66">{{ t('ocr.confidence_high', { pct: ocrResults.confidence }) }}</span>
        <span v-else-if="ocrResults.confidence >= 33">{{ t('ocr.confidence_medium', { pct: ocrResults.confidence }) }}</span>
        <span v-else>{{ t('ocr.confidence_low', { pct: ocrResults.confidence }) }}</span>
      </p>

      <h3 class="text-[15px] font-semibold text-gray-900 dark:text-gray-50">{{ t('ocr.detected_data') }}</h3>

      <!-- kWh -->
      <div class="result-item">
        <label class="mb-1 block text-[12px] font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400">{{ t('ocr.label_kwh') }}</label>
        <div class="flex items-center gap-2">
          <input
            v-model.number="ocrResults.kwh"
            type="number"
            step="0.1"
            :placeholder="t('ocr.kwh_placeholder')"
            class="min-h-11 w-full min-w-0 flex-1 rounded-xl border-2 border-edge bg-white px-3 text-[17px] font-semibold tabular-nums outline-none focus:border-green-600 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-100"
          />
          <CheckIcon v-if="ocrResults.kwh" class="h-5 w-5 shrink-0 text-green-700 dark:text-green-400" :aria-label="t('ocr.detected_data')" />
          <span v-else class="w-5 shrink-0 text-center text-gray-400" aria-hidden="true">-</span>
        </div>
      </div>

      <!-- Cost -->
      <div class="result-item">
        <label class="mb-1 block text-[12px] font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400">{{ t('ocr.label_cost') }}</label>
        <div class="flex items-center gap-2">
          <input
            v-model.number="ocrResults.cost"
            type="number"
            step="0.01"
            :placeholder="t('ocr.cost_placeholder')"
            class="min-h-11 w-full min-w-0 flex-1 rounded-xl border-2 border-edge bg-white px-3 text-[17px] font-semibold tabular-nums outline-none focus:border-green-600 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-100"
          />
          <CheckIcon v-if="ocrResults.cost" class="h-5 w-5 shrink-0 text-green-700 dark:text-green-400" :aria-label="t('ocr.detected_data')" />
          <span v-else class="w-5 shrink-0 text-center text-gray-400" aria-hidden="true">-</span>
        </div>
      </div>

      <!-- Duration -->
      <div class="result-item">
        <label class="mb-1 block text-[12px] font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400">{{ t('ocr.label_duration') }}</label>
        <div class="flex items-center gap-2">
          <input
            v-model.number="ocrResults.durationMinutes"
            type="number"
            :placeholder="t('ocr.duration_placeholder')"
            class="min-h-11 w-full min-w-0 flex-1 rounded-xl border-2 border-edge bg-white px-3 text-[17px] font-semibold tabular-nums outline-none focus:border-green-600 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-100"
          />
          <CheckIcon v-if="ocrResults.durationMinutes" class="h-5 w-5 shrink-0 text-green-700 dark:text-green-400" :aria-label="t('ocr.detected_data')" />
          <span v-else class="w-5 shrink-0 text-center text-gray-400" aria-hidden="true">-</span>
        </div>
      </div>

      <!-- Max Charging Power -->
      <div class="result-item">
        <label class="mb-1 block text-[12px] font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400">{{ t('ocr.label_power') }}</label>
        <div class="flex items-center gap-2">
          <input
            v-model.number="ocrResults.maxChargingPowerKw"
            type="number"
            step="0.1"
            :placeholder="t('ocr.power_placeholder')"
            class="min-h-11 w-full min-w-0 flex-1 rounded-xl border-2 border-edge bg-white px-3 text-[17px] font-semibold tabular-nums outline-none focus:border-green-600 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-100"
          />
          <CheckIcon v-if="ocrResults.maxChargingPowerKw" class="h-5 w-5 shrink-0 text-green-700 dark:text-green-400" :aria-label="t('ocr.detected_data')" />
          <span v-else class="w-5 shrink-0 text-center text-gray-400" aria-hidden="true">-</span>
        </div>
      </div>

      <!-- Action Buttons -->
      <div class="flex gap-2 pt-2">
        <button type="button" @click="applyData" class="flex-1 btn-3d inline-flex min-h-12 items-center justify-center gap-2 rounded-xl border-2 border-edge bg-green-600 px-5 text-[15px] font-semibold text-white hover:bg-green-700 disabled:cursor-not-allowed disabled:opacity-40">
          <CheckIcon class="h-5 w-5" aria-hidden="true" />{{ t('ocr.apply_btn') }}
        </button>
        <button type="button" @click="reset" class="btn-3d inline-flex min-h-12 items-center justify-center rounded-xl border-2 border-edge bg-white px-5 text-[15px] font-semibold text-gray-800 hover:bg-gray-50 disabled:opacity-40 dark:bg-white/10 dark:text-gray-100 dark:hover:bg-white/15">
          {{ t('ocr.rescan_btn') }}
        </button>
      </div>

      <!-- Erkannter Rohtext zum Nachprüfen, aufklappbar wie überall mit Collapse -->
      <div class="text-xs text-gray-500 dark:text-gray-400">
        <button type="button" :aria-expanded="showRaw" class="inline-flex min-h-11 items-center gap-1 font-semibold hover:text-gray-900 dark:hover:text-gray-100" @click="showRaw = !showRaw">
          <ChevronDownIcon :class="['h-4 w-4 transition-transform duration-300', showRaw ? 'rotate-180 ease-out' : 'ease-in']" aria-hidden="true" />{{ t('ocr.debug_toggle') }}
        </button>
        <Collapse :open="showRaw">
          <pre class="mt-1 overflow-x-auto whitespace-pre-wrap rounded-xl bg-gray-100 p-2 dark:bg-white/10">{{ ocrResults.rawText }}</pre>
        </Collapse>
      </div>
    </div>
  </div>
</template>

<style scoped>
.result-item input[type="number"]::-webkit-inner-spin-button,
.result-item input[type="number"]::-webkit-outer-spin-button {
  -webkit-appearance: none;
  margin: 0;
}
</style>
