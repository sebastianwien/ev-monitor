<script setup lang="ts">
import UploadSampleNotice from './UploadSampleNotice.vue'
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowUpTrayIcon, ArrowPathIcon, CheckCircleIcon, ExclamationCircleIcon, BoltIcon } from '@heroicons/vue/24/outline'
import { CI_BTN_PRIMARY, CI_BTN_SECONDARY, CI_LABEL, CI_CARD } from '../../utils/ciClasses'
import { vwEudaImportService, type VwEudaPreviewResult, type VwEudaImportResult } from '../../api/vwEudaImportService'
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
const preview = ref<VwEudaPreviewResult | null>(null)
const result = ref<VwEudaImportResult | null>(null)

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
    preview.value = await vwEudaImportService.preview(file.value, carId.value)
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
    result.value = await vwEudaImportService.importData(file.value, carId.value)
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
  <div class="grid gap-4">

    <!-- Step: Upload -->
    <template v-if="step === 'upload'">
      <p class="text-sm text-gray-600 dark:text-gray-400">{{ t('eu_data_act.desc') }}</p>

      <div class="grid gap-3">
        <!-- File picker -->
        <label class="block">
          <span :class="[CI_LABEL, 'mb-1.5 block']">
            {{ t('eu_data_act.label_file') }}
          </span>
          <div
            class="flex min-h-14 cursor-pointer items-center gap-3 rounded-2xl border-2 border-dashed border-gray-300 p-4 transition-colors hover:border-gray-500 dark:border-gray-600 dark:hover:border-gray-400"
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
          <span :class="[CI_LABEL, 'mb-1.5 block']">
            {{ t('eu_data_act.label_car') }}
          </span>
          <CarSelectDropdown :cars="cars" v-model="carId" />
        </div>

        <p v-if="error" role="alert" class="flex items-center gap-2 text-sm font-semibold text-gray-900 dark:text-gray-100">
          <ExclamationCircleIcon class="h-5 w-5 shrink-0 text-gray-600 dark:text-gray-300" aria-hidden="true" />
          {{ error }}
        </p>

        <UploadSampleNotice />

        <button
          type="button"
          :class="[CI_BTN_PRIMARY, 'w-full']"
          :disabled="!file || !carId || loading"
          @click="loadPreview"
        >
          <BoltIcon v-if="!loading" class="h-5 w-5" aria-hidden="true" />
          <ArrowPathIcon v-else class="h-5 w-5 animate-spin" aria-hidden="true" />
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
        <span class="rounded-full bg-gray-100 px-2 py-0.5 text-[11.5px] font-bold uppercase tracking-wide text-gray-600 dark:bg-gray-800 dark:text-gray-300">
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
            <tr class="border-b border-gray-200 text-left text-[12px] font-semibold uppercase tracking-wide text-gray-500 dark:border-white/10 dark:text-gray-400">
              <th class="pb-2 pr-4">{{ t('eu_data_act.col_date') }}</th>
              <th class="pb-2 pr-4">{{ t('eu_data_act.col_duration') }}</th>
              <th class="pb-2 pr-4">SoC</th>
              <th class="pb-2 pr-4">{{ t('eu_data_act.col_type') }}</th>
              <th class="pb-2">kWh</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-gray-200 tabular-nums dark:divide-white/10">
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
                  class="rounded-full bg-gray-100 px-2 py-0.5 text-[12px] font-semibold text-gray-700 dark:bg-gray-800 dark:text-gray-300"
                >
                  {{ s.chargeType }}
                </span>
                <span v-else class="text-gray-400">-</span>
              </td>
              <td class="py-2 font-semibold">{{ formatKwh(s.calculatedKwh) }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Mobile cards -->
      <div class="grid gap-2 md:hidden">
        <div
          v-for="s in preview.sessions"
          :key="s.startedAt"
          :class="[CI_CARD, 'py-3 text-sm']"
        >
          <div class="flex items-center justify-between mb-1">
            <span class="font-medium text-gray-800 dark:text-gray-200">{{ formatDate(s.startedAt) }}</span>
            <span
              v-if="s.chargeType"
              class="rounded-full bg-gray-100 px-2 py-0.5 text-[12px] font-semibold text-gray-700 dark:bg-gray-800 dark:text-gray-300"
            >
              {{ s.chargeType }}
            </span>
          </div>
          <div class="flex gap-4 text-[13px] tabular-nums text-gray-600 dark:text-gray-400">
            <span>{{ s.durationMin }} min</span>
            <span v-if="s.socBefore != null && s.socAfter != null">{{ s.socBefore }}% → {{ s.socAfter }}%</span>
            <span class="font-semibold text-gray-900 dark:text-gray-100">{{ formatKwh(s.calculatedKwh) }}</span>
          </div>
        </div>
      </div>

      <p v-if="error" role="alert" class="flex items-center gap-2 text-sm font-semibold text-gray-900 dark:text-gray-100">
        <ExclamationCircleIcon class="h-5 w-5 shrink-0 text-gray-600 dark:text-gray-300" aria-hidden="true" />
        {{ error }}
      </p>

      <div class="flex gap-3">
        <button
          type="button"
          :class="[CI_BTN_SECONDARY, 'flex-1']"
          @click="step = 'upload'"
        >
          {{ t('eu_data_act.btn_back') }}
        </button>
        <button
          type="button"
          :class="[CI_BTN_PRIMARY, 'flex-1']"
          :disabled="loading"
          @click="confirmImport"
        >
          <ArrowPathIcon v-if="loading" class="h-5 w-5 animate-spin" aria-hidden="true" />
          {{ t('eu_data_act.btn_import') }}
        </button>
      </div>
    </template>

    <!-- Step: Done -->
    <template v-if="step === 'done' && result">
      <div class="grid justify-items-center gap-3 py-4 text-center">
        <CheckCircleIcon class="h-12 w-12 text-green-700 dark:text-green-400" aria-hidden="true" />
        <p class="text-lg font-bold text-gray-900 dark:text-gray-100">
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
          type="button"
          :class="CI_BTN_SECONDARY"
          @click="emit('close')"
        >
          {{ t('eu_data_act.btn_close') }}
        </button>
      </div>
    </template>

  </div>
</template>
