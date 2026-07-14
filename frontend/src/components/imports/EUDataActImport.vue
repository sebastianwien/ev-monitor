<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowUpTrayIcon, CheckCircleIcon, ExclamationCircleIcon, BoltIcon } from '@heroicons/vue/24/outline'
import { euDataActService, type EUDataActPreviewResult, type EUDataActImportResult } from '../../api/euDataActService'
import CarSelectDropdown from '../car/CarSelectDropdown.vue'
import type { Car } from '../../api/carService'

const { t, d, n } = useI18n()

const props = defineProps<{ cars: Car[] }>()
const emit = defineEmits<{ (e: 'close'): void }>()

type Step = 'upload' | 'preview' | 'done'
const step = ref<Step>('upload')
const file = ref<File | null>(null)
const carId = ref<string | null>(null)
const loading = ref(false)
const error = ref('')
const preview = ref<EUDataActPreviewResult | null>(null)
const result = ref<EUDataActImportResult | null>(null)

if (props.cars.length === 1) carId.value = props.cars[0].id

function onFileChange(ev: Event) {
  const input = ev.target as HTMLInputElement
  file.value = input.files?.[0] ?? null
  error.value = ''
}

async function loadPreview() {
  if (!file.value || !carId.value) return
  error.value = ''
  loading.value = true
  try {
    preview.value = await euDataActService.preview(file.value, carId.value)
    if (preview.value.sessions.length === 0) {
      error.value = t('eu_data_act.err_no_sessions')
      return
    }
    step.value = 'preview'
  } catch (e: unknown) {
    const err = e as { response?: { data?: { error?: string } } }
    error.value = err.response?.data?.error ?? t('eu_data_act.err_generic')
  } finally {
    loading.value = false
  }
}

async function confirmImport() {
  if (!file.value || !carId.value) return
  error.value = ''
  loading.value = true
  try {
    result.value = await euDataActService.importData(file.value, carId.value)
    step.value = 'done'
  } catch (e: unknown) {
    const err = e as { response?: { data?: { error?: string } } }
    error.value = err.response?.data?.error ?? t('eu_data_act.err_generic')
  } finally {
    loading.value = false
  }
}

function formatDate(iso: string) {
  return d(new Date(iso), 'short')
}

function formatKwh(v: number | null) {
  return v != null ? n(v, { minimumFractionDigits: 1, maximumFractionDigits: 1 }) + ' kWh' : '-'
}
</script>

<template>
  <div class="space-y-4">

    <!-- Step: Upload -->
    <template v-if="step === 'upload'">
      <p class="text-sm text-gray-600 dark:text-gray-400">{{ t('eu_data_act.desc') }}</p>

      <div class="space-y-3">
        <!-- File picker -->
        <label class="block">
          <span class="text-sm font-medium text-gray-700 dark:text-gray-300 mb-1 block">
            {{ t('eu_data_act.label_file') }}
          </span>
          <div
            class="flex items-center gap-3 border-2 border-dashed border-gray-300 dark:border-gray-600 rounded-lg p-4 cursor-pointer hover:border-blue-400 dark:hover:border-blue-500 transition-colors"
            @click="($refs.fileInput as HTMLInputElement).click()"
          >
            <ArrowUpTrayIcon class="w-5 h-5 text-gray-400 shrink-0" />
            <span class="text-sm text-gray-600 dark:text-gray-400 truncate">
              {{ file ? file.name : t('eu_data_act.file_placeholder') }}
            </span>
          </div>
          <input ref="fileInput" type="file" accept=".json,.zip" class="hidden" @change="onFileChange" />
        </label>

        <!-- Car picker -->
        <div v-if="cars.length > 1">
          <span class="text-sm font-medium text-gray-700 dark:text-gray-300 mb-1 block">
            {{ t('eu_data_act.label_car') }}
          </span>
          <CarSelectDropdown :cars="cars" v-model="carId" />
        </div>

        <p v-if="error" class="flex items-center gap-2 text-sm text-red-600 dark:text-red-400">
          <ExclamationCircleIcon class="w-4 h-4 shrink-0" />
          {{ error }}
        </p>

        <button
          class="w-full flex items-center justify-center gap-2 bg-blue-600 hover:bg-blue-700 disabled:opacity-50 text-white font-medium text-sm py-2.5 px-4 rounded-lg transition-colors"
          :disabled="!file || !carId || loading"
          @click="loadPreview"
        >
          <BoltIcon v-if="!loading" class="w-4 h-4" />
          <span v-if="loading" class="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
          {{ t('eu_data_act.btn_preview') }}
        </button>
      </div>
    </template>

    <!-- Step: Preview -->
    <template v-if="step === 'preview' && preview">
      <div class="flex items-center justify-between">
        <p class="text-sm text-gray-500 dark:text-gray-400">
          {{ t('eu_data_act.preview_vin') }}: <span class="font-mono font-medium text-gray-800 dark:text-gray-200">{{ preview.vin }}</span>
        </p>
        <span class="text-xs bg-amber-100 dark:bg-amber-900/40 text-amber-700 dark:text-amber-400 px-2 py-0.5 rounded font-medium">
          Beta
        </span>
      </div>

      <p class="text-sm text-gray-600 dark:text-gray-400">
        {{ t('eu_data_act.preview_sessions_found', { count: preview.sessions.length }) }}
      </p>

      <!-- Sessions table (desktop) / cards (mobile) -->
      <div class="hidden md:block overflow-x-auto">
        <table class="w-full text-sm">
          <thead>
            <tr class="border-b border-gray-200 dark:border-gray-700 text-left text-xs text-gray-500 dark:text-gray-400 uppercase tracking-wide">
              <th class="pb-2 pr-4">{{ t('eu_data_act.col_date') }}</th>
              <th class="pb-2 pr-4">{{ t('eu_data_act.col_duration') }}</th>
              <th class="pb-2 pr-4">SoC</th>
              <th class="pb-2 pr-4">{{ t('eu_data_act.col_type') }}</th>
              <th class="pb-2">kWh</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-gray-100 dark:divide-gray-800">
            <tr v-for="s in preview.sessions" :key="s.startedAt" class="text-gray-800 dark:text-gray-200">
              <td class="py-2 pr-4 whitespace-nowrap">{{ formatDate(s.startedAt) }}</td>
              <td class="py-2 pr-4">{{ s.durationMin }} min</td>
              <td class="py-2 pr-4 whitespace-nowrap">
                <span v-if="s.socBefore != null && s.socAfter != null">{{ s.socBefore }}% → {{ s.socAfter }}%</span>
                <span v-else class="text-gray-400">-</span>
              </td>
              <td class="py-2 pr-4">
                <span
                  v-if="s.chargeType"
                  class="text-xs font-medium px-1.5 py-0.5 rounded"
                  :class="s.chargeType === 'DC'
                    ? 'bg-purple-100 dark:bg-purple-900/40 text-purple-700 dark:text-purple-300'
                    : 'bg-green-100 dark:bg-green-900/40 text-green-700 dark:text-green-300'"
                >
                  {{ s.chargeType }}
                </span>
                <span v-else class="text-gray-400">-</span>
              </td>
              <td class="py-2 font-medium">{{ formatKwh(s.calculatedKwh) }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Mobile cards -->
      <div class="md:hidden space-y-2">
        <div
          v-for="s in preview.sessions"
          :key="s.startedAt"
          class="bg-gray-50 dark:bg-gray-700/50 rounded-lg p-3 text-sm"
        >
          <div class="flex items-center justify-between mb-1">
            <span class="font-medium text-gray-800 dark:text-gray-200">{{ formatDate(s.startedAt) }}</span>
            <span
              v-if="s.chargeType"
              class="text-xs font-medium px-1.5 py-0.5 rounded"
              :class="s.chargeType === 'DC'
                ? 'bg-purple-100 dark:bg-purple-900/40 text-purple-700 dark:text-purple-300'
                : 'bg-green-100 dark:bg-green-900/40 text-green-700 dark:text-green-300'"
            >
              {{ s.chargeType }}
            </span>
          </div>
          <div class="flex gap-4 text-gray-600 dark:text-gray-400 text-xs">
            <span>{{ s.durationMin }} min</span>
            <span v-if="s.socBefore != null && s.socAfter != null">{{ s.socBefore }}% → {{ s.socAfter }}%</span>
            <span class="font-medium text-gray-800 dark:text-gray-200">{{ formatKwh(s.calculatedKwh) }}</span>
          </div>
        </div>
      </div>

      <p v-if="error" class="flex items-center gap-2 text-sm text-red-600 dark:text-red-400">
        <ExclamationCircleIcon class="w-4 h-4 shrink-0" />
        {{ error }}
      </p>

      <div class="flex gap-3">
        <button
          class="flex-1 text-sm font-medium py-2.5 px-4 rounded-lg border border-gray-300 dark:border-gray-600 text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors"
          @click="step = 'upload'"
        >
          {{ t('eu_data_act.btn_back') }}
        </button>
        <button
          class="flex-1 flex items-center justify-center gap-2 bg-blue-600 hover:bg-blue-700 disabled:opacity-50 text-white font-medium text-sm py-2.5 px-4 rounded-lg transition-colors"
          :disabled="loading"
          @click="confirmImport"
        >
          <span v-if="loading" class="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
          {{ t('eu_data_act.btn_import') }}
        </button>
      </div>
    </template>

    <!-- Step: Done -->
    <template v-if="step === 'done' && result">
      <div class="text-center py-4 space-y-3">
        <CheckCircleIcon class="w-12 h-12 text-green-500 mx-auto" />
        <p class="text-lg font-semibold text-gray-900 dark:text-white">
          {{ t('eu_data_act.done_title') }}
        </p>
        <div class="text-sm text-gray-600 dark:text-gray-400 space-y-1">
          <p v-if="result.imported > 0">
            {{ t('eu_data_act.done_imported', { count: result.imported }) }}
          </p>
          <p v-if="result.skipped > 0">
            {{ t('eu_data_act.done_skipped', { count: result.skipped }) }}
          </p>
          <p v-if="result.imported === 0 && result.skipped === 0">
            {{ t('eu_data_act.done_nothing_new') }}
          </p>
        </div>
        <button
          class="text-sm text-blue-600 dark:text-blue-400 hover:underline"
          @click="emit('close')"
        >
          {{ t('eu_data_act.btn_close') }}
        </button>
      </div>
    </template>

  </div>
</template>
