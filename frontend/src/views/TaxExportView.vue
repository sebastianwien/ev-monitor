<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { carService, type Car } from '../api/carService'
import { taxExportService, type TaxExportPreview } from '../api/taxExportService'
import { DocumentArrowDownIcon, InformationCircleIcon } from '@heroicons/vue/24/outline'

const { t } = useI18n()

const cars = ref<Car[]>([])
const loading = ref(true)
const previewLoading = ref(false)
const error = ref<string | null>(null)

const selectedCarId = ref<string>('')
const usePauschale = ref(true)
const customTariff = ref<number | null>(null)
const preview = ref<TaxExportPreview | null>(null)

// Default to current month
const now = new Date()
const fromDate = ref(new Date(now.getFullYear(), now.getMonth(), 1).toISOString().split('T')[0])
const toDate = ref(new Date(now.getFullYear(), now.getMonth() + 1, 0).toISOString().split('T')[0])

const businessCars = computed(() => cars.value.filter(c => c.isBusinessCar))

const canExport = computed(() =>
    selectedCarId.value &&
    fromDate.value &&
    toDate.value &&
    (usePauschale.value || (customTariff.value != null && customTariff.value > 0))
)

const BMF_PAUSCHALE = 0.3436

onMounted(async () => {
    try {
        const all = await carService.getCars()
        cars.value = all
        const first = all.find(c => c.isBusinessCar)
        if (first) selectedCarId.value = first.id
    } catch {
        error.value = t('tax_export.error_load_cars')
    } finally {
        loading.value = false
    }
})

watch([selectedCarId, fromDate, toDate, usePauschale, customTariff], async () => {
    if (!canExport.value) {
        preview.value = null
        return
    }
    previewLoading.value = true
    try {
        preview.value = await taxExportService.getPreview(
            selectedCarId.value,
            fromDate.value,
            toDate.value,
            usePauschale.value,
            !usePauschale.value && customTariff.value ? customTariff.value : undefined
        )
    } catch {
        preview.value = null
    } finally {
        previewLoading.value = false
    }
})

const setQuickRange = (type: 'current-month' | 'last-month' | 'current-year') => {
    const d = new Date()
    if (type === 'current-month') {
        fromDate.value = new Date(d.getFullYear(), d.getMonth(), 1).toISOString().split('T')[0]
        toDate.value = new Date(d.getFullYear(), d.getMonth() + 1, 0).toISOString().split('T')[0]
    } else if (type === 'last-month') {
        fromDate.value = new Date(d.getFullYear(), d.getMonth() - 1, 1).toISOString().split('T')[0]
        toDate.value = new Date(d.getFullYear(), d.getMonth(), 0).toISOString().split('T')[0]
    } else if (type === 'current-year') {
        fromDate.value = new Date(d.getFullYear(), 0, 1).toISOString().split('T')[0]
        toDate.value = new Date(d.getFullYear(), 11, 31).toISOString().split('T')[0]
    }
}

const downloading = ref(false)

const downloadFile = async (type: 'csv' | 'pdf') => {
    if (!canExport.value || !preview.value) return
    const tariff = !usePauschale.value && customTariff.value ? customTariff.value : undefined
    downloading.value = true
    try {
        const blob = type === 'csv'
            ? await taxExportService.downloadCsv(selectedCarId.value, fromDate.value, toDate.value, usePauschale.value, tariff)
            : await taxExportService.downloadPdf(selectedCarId.value, fromDate.value, toDate.value, usePauschale.value, tariff)
        const car = businessCars.value.find(c => c.id === selectedCarId.value)
        const plate = car?.licensePlate?.replace(/[^A-Za-z0-9]/g, '-') ?? 'fahrzeug'
        const filename = `heimladen-nachweis-${plate}-${fromDate.value.slice(0, 7)}.${type}`
        const url = URL.createObjectURL(blob)
        const a = document.createElement('a')
        a.href = url
        a.download = filename
        document.body.appendChild(a)
        a.click()
        document.body.removeChild(a)
        URL.revokeObjectURL(url)
    } catch (err: any) {
        error.value = t('tax_export.error_download')
    } finally {
        downloading.value = false
    }
}
</script>

<template>
  <div class="min-h-screen bg-gray-50 dark:bg-gray-900">
    <div class="max-w-2xl mx-auto px-4 py-8">

      <div class="mb-6">
        <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">{{ t('tax_export.title') }}</h1>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">{{ t('tax_export.subtitle') }}</p>
      </div>

      <!-- Kein Dienstwagen -->
      <div v-if="!loading && businessCars.length === 0"
        class="bg-blue-50 dark:bg-blue-900/30 border border-blue-200 dark:border-blue-700 rounded-xl p-5">
        <div class="flex gap-3">
          <InformationCircleIcon class="w-5 h-5 text-blue-500 mt-0.5 shrink-0" />
          <div>
            <p class="text-sm font-medium text-blue-800 dark:text-blue-200">{{ t('tax_export.no_business_car_title') }}</p>
            <p class="mt-1 text-sm text-blue-600 dark:text-blue-300">{{ t('tax_export.no_business_car_desc') }}</p>
            <router-link to="/cars"
              class="mt-2 inline-block text-sm font-medium text-blue-700 dark:text-blue-300 underline">
              {{ t('tax_export.go_to_cars') }}
            </router-link>
          </div>
        </div>
      </div>

      <div v-else-if="!loading" class="space-y-5">

        <!-- Fahrzeug -->
        <div class="bg-white dark:bg-gray-800 rounded-xl shadow-sm p-5">
          <label class="block text-sm font-semibold text-gray-700 dark:text-gray-300 mb-2">
            {{ t('tax_export.select_car') }}
          </label>
          <div class="grid gap-2 sm:grid-cols-2">
            <button v-for="car in businessCars" :key="car.id"
              @click="selectedCarId = car.id"
              :class="[
                'text-left px-4 py-3 rounded-lg border-2 transition',
                selectedCarId === car.id
                  ? 'border-blue-500 bg-blue-50 dark:bg-blue-900/30'
                  : 'border-gray-200 dark:border-gray-600 hover:border-blue-300'
              ]">
              <p class="font-medium text-sm text-gray-900 dark:text-gray-100">
                {{ car.brand }} {{ car.model.replace(/_/g, ' ') }}
              </p>
              <p v-if="car.licensePlate" class="text-xs text-gray-500 dark:text-gray-400 mt-0.5">
                {{ car.licensePlate }}
              </p>
            </button>
          </div>
        </div>

        <!-- Zeitraum -->
        <div class="bg-white dark:bg-gray-800 rounded-xl shadow-sm p-5">
          <label class="block text-sm font-semibold text-gray-700 dark:text-gray-300 mb-2">
            {{ t('tax_export.period') }}
          </label>
          <div class="flex flex-wrap gap-2 mb-3">
            <button v-for="q in ['current-month', 'last-month', 'current-year'] as const" :key="q"
              @click="setQuickRange(q)"
              class="px-3 py-1.5 text-xs rounded-full border border-gray-300 dark:border-gray-600 hover:border-blue-400 hover:bg-blue-50 dark:hover:bg-blue-900/30 transition text-gray-600 dark:text-gray-300">
              {{ t('tax_export.quick_' + q.replace(/-/g, '_')) }}
            </button>
          </div>
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-xs text-gray-500 dark:text-gray-400 mb-1">{{ t('tax_export.from') }}</label>
              <input type="date" v-model="fromDate"
                class="w-full px-3 py-2 text-sm border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100" />
            </div>
            <div>
              <label class="block text-xs text-gray-500 dark:text-gray-400 mb-1">{{ t('tax_export.to') }}</label>
              <input type="date" v-model="toDate"
                class="w-full px-3 py-2 text-sm border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100" />
            </div>
          </div>
        </div>

        <!-- Abrechnungsmodell -->
        <div class="bg-white dark:bg-gray-800 rounded-xl shadow-sm p-5">
          <label class="block text-sm font-semibold text-gray-700 dark:text-gray-300 mb-3">
            {{ t('tax_export.billing_model') }}
          </label>
          <div class="space-y-3">
            <label class="flex items-start gap-3 cursor-pointer">
              <input type="radio" v-model="usePauschale" :value="true"
                class="mt-0.5 text-blue-600" />
              <div>
                <p class="text-sm font-medium text-gray-800 dark:text-gray-200">
                  {{ t('tax_export.pauschale_label', { rate: BMF_PAUSCHALE }) }}
                </p>
                <p class="text-xs text-gray-500 dark:text-gray-400 mt-0.5">{{ t('tax_export.pauschale_hint') }}</p>
              </div>
            </label>
            <label class="flex items-start gap-3 cursor-pointer">
              <input type="radio" v-model="usePauschale" :value="false"
                class="mt-0.5 text-blue-600" />
              <div class="flex-1">
                <p class="text-sm font-medium text-gray-800 dark:text-gray-200">{{ t('tax_export.custom_tariff_label') }}</p>
                <div v-if="!usePauschale" class="mt-2 flex items-center gap-2">
                  <input type="number" v-model="customTariff" step="0.001" min="0.01" max="1"
                    placeholder="0.280"
                    class="w-28 px-3 py-1.5 text-sm border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100" />
                  <span class="text-sm text-gray-500">EUR/kWh</span>
                </div>
              </div>
            </label>
          </div>
        </div>

        <!-- Vorschau -->
        <div v-if="previewLoading" class="bg-white dark:bg-gray-800 rounded-xl shadow-sm p-5">
          <div class="animate-pulse space-y-2">
            <div class="h-4 bg-gray-200 dark:bg-gray-700 rounded w-1/3"></div>
            <div class="h-8 bg-gray-200 dark:bg-gray-700 rounded w-1/2"></div>
          </div>
        </div>

        <div v-else-if="preview" class="bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-700 rounded-xl p-5">
          <p class="text-xs font-semibold text-blue-600 dark:text-blue-400 uppercase tracking-wide mb-3">
            {{ t('tax_export.preview_title') }}
          </p>
          <div class="grid grid-cols-3 gap-4">
            <div>
              <p class="text-2xl font-bold text-gray-900 dark:text-gray-100">{{ preview.sessionCount }}</p>
              <p class="text-xs text-gray-500 dark:text-gray-400">{{ t('tax_export.sessions') }}</p>
            </div>
            <div>
              <p class="text-2xl font-bold text-gray-900 dark:text-gray-100">{{ preview.totalKwh.toFixed(1) }}</p>
              <p class="text-xs text-gray-500 dark:text-gray-400">kWh</p>
            </div>
            <div>
              <p class="text-2xl font-bold text-gray-900 dark:text-gray-100">{{ preview.totalCostEur.toFixed(2) }} €</p>
              <p class="text-xs text-gray-500 dark:text-gray-400">{{ t('tax_export.total_cost') }}</p>
            </div>
          </div>
        </div>

        <div v-else-if="canExport && !previewLoading" class="bg-amber-50 dark:bg-amber-900/20 border border-amber-200 dark:border-amber-700 rounded-xl p-4">
          <p class="text-sm text-amber-700 dark:text-amber-300">{{ t('tax_export.no_sessions') }}</p>
        </div>

        <!-- Download-Buttons -->
        <div class="flex gap-3">
          <button @click="downloadFile('csv')"
            :disabled="!preview || preview.sessionCount === 0 || downloading"
            class="flex-1 flex items-center justify-center gap-2 px-4 py-3 bg-blue-600 hover:bg-blue-700 disabled:bg-gray-300 dark:disabled:bg-gray-700 disabled:cursor-not-allowed text-white rounded-xl font-medium text-sm transition">
            <DocumentArrowDownIcon class="w-4 h-4" />
            {{ downloading ? '...' : t('tax_export.download_csv') }}
          </button>
          <button @click="downloadFile('pdf')"
            :disabled="!preview || preview.sessionCount === 0 || downloading"
            class="flex-1 flex items-center justify-center gap-2 px-4 py-3 bg-gray-800 hover:bg-gray-900 dark:bg-gray-700 dark:hover:bg-gray-600 disabled:bg-gray-300 dark:disabled:bg-gray-600 disabled:cursor-not-allowed text-white rounded-xl font-medium text-sm transition">
            <DocumentArrowDownIcon class="w-4 h-4" />
            {{ downloading ? '...' : t('tax_export.download_pdf') }}
          </button>
        </div>

        <!-- Disclaimer -->
        <p class="text-xs text-gray-400 dark:text-gray-500 leading-relaxed">
          {{ t('tax_export.disclaimer') }}
        </p>

      </div>
    </div>
  </div>
</template>
