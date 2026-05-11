<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowUpTrayIcon, ExclamationCircleIcon } from '@heroicons/vue/24/outline'
import CarSelectDropdown from '../car/CarSelectDropdown.vue'
import xpengService, { type XpengJobDto } from '../../api/xpengService'
import type { Car } from '../../api/carService'

const { t } = useI18n()

const props = defineProps<{
  carsReadyForUpload: Car[]
  jobRunning?: boolean
  errorMessage?: string
}>()

const emit = defineEmits<{
  (e: 'uploaded', job: XpengJobDto): void
  (e: 'error', msg: string): void
}>()

const carId = defineModel<string | null>('carId', { default: null })
const file = ref<File | null>(null)
const password = ref('')
const uploading = ref(false)

watch(() => props.carsReadyForUpload, (cars) => {
  if (cars.length === 1 && !carId.value) carId.value = cars[0].id
}, { immediate: true })

const busy = computed(() => uploading.value || !!props.jobRunning)
const canSubmit = computed(() => !!carId.value && !!file.value && !busy.value)

function onFileChange(ev: Event) {
  const target = ev.target as HTMLInputElement
  file.value = target.files?.[0] ?? null
}

async function submit() {
  if (!carId.value || !file.value) {
    emit('error', t('xpeng.err_upload_missing'))
    return
  }
  uploading.value = true
  try {
    const job = await xpengService.upload(carId.value, file.value, password.value || undefined)
    file.value = null
    password.value = ''
    emit('uploaded', job)
  } catch (e: unknown) {
    const err = e as { response?: { data?: { error?: string } }; message?: string }
    emit('error', err.response?.data?.error ?? err.message ?? 'Unknown error')
  } finally {
    uploading.value = false
  }
}
</script>

<template>
  <section class="space-y-4 border border-gray-200 dark:border-gray-700 rounded-lg p-4">
    <h3 class="text-base font-semibold flex items-center gap-2">
      <ArrowUpTrayIcon class="w-5 h-5 text-blue-600 dark:text-blue-400" />
      {{ t('xpeng.step_upload_title') }}
    </h3>

    <div>
      <label class="block text-sm text-gray-700 dark:text-gray-300 mb-1">{{ t('xpeng.car_label') }}</label>
      <CarSelectDropdown v-model="carId" :cars="carsReadyForUpload" />
    </div>

    <div>
      <label class="block text-sm text-gray-700 dark:text-gray-300 mb-1">{{ t('xpeng.file_label') }}</label>
      <input type="file" accept=".xlsx" @change="onFileChange"
             class="block w-full text-sm text-gray-700 dark:text-gray-300 file:mr-3 file:py-2 file:px-4 file:rounded file:border-0 file:bg-blue-50 file:text-blue-700 hover:file:bg-blue-100 dark:file:bg-blue-900/40 dark:file:text-blue-300 dark:hover:file:bg-blue-900/60" />
    </div>

    <div>
      <label class="block text-sm text-gray-700 dark:text-gray-300 mb-1">
        {{ t('xpeng.password_label') }}
        <span class="text-xs text-gray-500">({{ t('xpeng.password_optional') }})</span>
      </label>
      <input v-model="password" type="password"
             class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-800 dark:text-white text-sm" />
    </div>

    <div v-if="errorMessage"
         class="rounded-md bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 p-3 text-sm text-red-800 dark:text-red-200 flex gap-2">
      <ExclamationCircleIcon class="w-5 h-5 flex-shrink-0" />
      <span>{{ errorMessage }}</span>
    </div>

    <button @click="submit" :disabled="!canSubmit"
            class="btn-3d w-full flex items-center justify-center gap-2 bg-blue-600 hover:bg-blue-700 disabled:bg-gray-400 disabled:cursor-not-allowed text-white px-5 py-2.5 rounded-lg font-medium text-sm transition-colors">
      <span v-if="busy" class="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
      <ArrowUpTrayIcon v-else class="w-4 h-4" />
      <span>{{ busy ? t('xpeng.uploading') : t('xpeng.btn_upload') }}</span>
    </button>
  </section>
</template>
