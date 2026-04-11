<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { CameraIcon, XMarkIcon, ArrowPathIcon } from '@heroicons/vue/24/outline'
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
    <div class="flex justify-between items-center mb-6">
      <h2 class="text-xl font-bold text-gray-800 flex items-center gap-2">
        <CameraIcon class="h-6 w-6 text-indigo-600" />
        {{ t('ocr.title') }}
      </h2>
      <button @click="cancel" class="p-2 hover:bg-gray-100 rounded-lg transition">
        <XMarkIcon class="h-6 w-6 text-gray-600" />
      </button>
    </div>

    <!-- Error Message -->
    <div v-if="error" class="mb-4 p-4 bg-red-50 dark:bg-red-900/30 border border-red-200 dark:border-red-700 text-red-700 dark:text-red-300 rounded-lg text-sm">
      {{ error }}
    </div>

    <!-- Step 1: Camera Button (No Image) -->
    <div v-if="!imagePreview" class="text-center py-8">
      <CameraIcon class="h-16 w-16 mx-auto mb-4 text-gray-400" />
      <p class="text-gray-600 mb-6">{{ t('ocr.step1_prompt') }}</p>

      <button
        @click="openCamera"
        class="px-6 py-3 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition font-medium">
        <CameraIcon class="h-5 w-5 inline mr-2" />
        {{ t('ocr.take_photo_btn') }}
      </button>

      <p class="text-xs text-gray-500 mt-4">
        💡 {{ t('ocr.tip') }}
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
        <img :src="imagePreview" alt="Preview" class="w-full rounded-lg border-2 border-gray-300" />
      </div>

      <div class="flex gap-3">
        <button
          @click="startOcr"
          :disabled="isProcessing"
          class="flex-1 px-6 py-3 bg-green-600 text-white rounded-lg hover:bg-green-700 disabled:opacity-50 disabled:cursor-not-allowed transition font-medium">
          <span v-if="!isProcessing">🔍 {{ t('ocr.start_ocr_btn') }}</span>
          <span v-else class="flex items-center justify-center">
            <ArrowPathIcon class="h-5 w-5 animate-spin mr-2" />
            {{ t('ocr.scanning') }}
          </span>
        </button>
        <button
          @click="reset"
          :disabled="isProcessing"
          class="px-6 py-3 bg-gray-300 text-gray-700 rounded-lg hover:bg-gray-400 disabled:opacity-50 transition font-medium">
          {{ t('ocr.retry_btn') }}
        </button>
      </div>

      <p v-if="isProcessing" class="text-sm text-gray-600 mt-3 text-center">
        ⏳ {{ t('ocr.wait_hint') }}
      </p>
    </div>

    <!-- Step 3: OCR Results -->
    <div v-if="ocrResults" class="space-y-4">
      <!-- Confidence Indicator -->
      <div class="p-3 rounded-lg" :class="{
        'bg-green-50 dark:bg-green-900/30 border border-green-200 dark:border-green-700': ocrResults.confidence >= 66,
        'bg-yellow-50 dark:bg-yellow-900/30 border border-yellow-200 dark:border-yellow-700': ocrResults.confidence >= 33 && ocrResults.confidence < 66,
        'bg-red-50 dark:bg-red-900/30 border border-red-200 dark:border-red-700': ocrResults.confidence < 33
      }">
        <p class="text-sm font-medium" :class="{
          'text-green-700 dark:text-green-300': ocrResults.confidence >= 66,
          'text-yellow-700 dark:text-yellow-300': ocrResults.confidence >= 33 && ocrResults.confidence < 66,
          'text-red-700 dark:text-red-300': ocrResults.confidence < 33
        }">
          <span v-if="ocrResults.confidence >= 66">✅ {{ t('ocr.confidence_high', { pct: ocrResults.confidence }) }}</span>
          <span v-else-if="ocrResults.confidence >= 33">⚠️ {{ t('ocr.confidence_medium', { pct: ocrResults.confidence }) }}</span>
          <span v-else>❌ {{ t('ocr.confidence_low', { pct: ocrResults.confidence }) }}</span>
        </p>
      </div>

      <h3 class="font-semibold text-gray-800">{{ t('ocr.detected_data') }}</h3>

      <!-- kWh -->
      <div class="result-item">
        <label class="text-sm text-gray-600 mb-1 block">{{ t('ocr.label_kwh') }}</label>
        <div class="flex items-center gap-2">
          <input
            v-model.number="ocrResults.kwh"
            type="number"
            step="0.1"
            :placeholder="t('ocr.kwh_placeholder')"
            class="flex-1 px-3 py-2 border rounded-md focus:ring-indigo-500 focus:border-indigo-500"
            :class="ocrResults.kwh ? 'border-green-500 bg-green-50' : 'border-gray-300'"
          />
          <span v-if="ocrResults.kwh" class="text-green-600 font-medium">✓</span>
          <span v-else class="text-gray-400">—</span>
        </div>
      </div>

      <!-- Cost -->
      <div class="result-item">
        <label class="text-sm text-gray-600 mb-1 block">{{ t('ocr.label_cost') }}</label>
        <div class="flex items-center gap-2">
          <input
            v-model.number="ocrResults.cost"
            type="number"
            step="0.01"
            :placeholder="t('ocr.cost_placeholder')"
            class="flex-1 px-3 py-2 border rounded-md focus:ring-indigo-500 focus:border-indigo-500"
            :class="ocrResults.cost ? 'border-green-500 bg-green-50' : 'border-gray-300'"
          />
          <span v-if="ocrResults.cost" class="text-green-600 font-medium">✓</span>
          <span v-else class="text-gray-400">—</span>
        </div>
      </div>

      <!-- Duration -->
      <div class="result-item">
        <label class="text-sm text-gray-600 mb-1 block">{{ t('ocr.label_duration') }}</label>
        <div class="flex items-center gap-2">
          <input
            v-model.number="ocrResults.durationMinutes"
            type="number"
            :placeholder="t('ocr.duration_placeholder')"
            class="flex-1 px-3 py-2 border rounded-md focus:ring-indigo-500 focus:border-indigo-500"
            :class="ocrResults.durationMinutes ? 'border-green-500 bg-green-50' : 'border-gray-300'"
          />
          <span v-if="ocrResults.durationMinutes" class="text-green-600 font-medium">✓</span>
          <span v-else class="text-gray-400">—</span>
        </div>
      </div>

      <!-- Max Charging Power -->
      <div class="result-item">
        <label class="text-sm text-gray-600 mb-1 block">{{ t('ocr.label_power') }}</label>
        <div class="flex items-center gap-2">
          <input
            v-model.number="ocrResults.maxChargingPowerKw"
            type="number"
            step="0.1"
            :placeholder="t('ocr.power_placeholder')"
            class="flex-1 px-3 py-2 border rounded-md focus:ring-indigo-500 focus:border-indigo-500"
            :class="ocrResults.maxChargingPowerKw ? 'border-green-500 bg-green-50' : 'border-gray-300'"
          />
          <span v-if="ocrResults.maxChargingPowerKw" class="text-green-600 font-medium">✓</span>
          <span v-else class="text-gray-400">—</span>
        </div>
      </div>

      <!-- Action Buttons -->
      <div class="flex gap-3 pt-4">
        <button
          @click="applyData"
          class="flex-1 px-6 py-3 bg-green-600 text-white rounded-lg hover:bg-green-700 transition font-medium">
          ✓ {{ t('ocr.apply_btn') }}
        </button>
        <button
          @click="reset"
          class="px-6 py-3 bg-gray-300 text-gray-700 rounded-lg hover:bg-gray-400 transition font-medium">
          {{ t('ocr.rescan_btn') }}
        </button>
      </div>

      <!-- Debug: Raw Text (collapsible) -->
      <details class="text-xs text-gray-500 mt-4">
        <summary class="cursor-pointer hover:text-gray-700">🔍 {{ t('ocr.debug_toggle') }}</summary>
        <pre class="mt-2 p-2 bg-gray-100 rounded overflow-x-auto whitespace-pre-wrap">{{ ocrResults.rawText }}</pre>
      </details>
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
