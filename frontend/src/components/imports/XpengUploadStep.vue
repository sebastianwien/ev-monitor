<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowUpTrayIcon, ExclamationCircleIcon, ChevronDownIcon } from '@heroicons/vue/24/outline'
import xpengService, { type XpengJobDto } from '../../api/xpengService'
import type { Car } from '../../api/carService'

function carLabel(car: Car): string {
  const enumToLabel = (v: string | null | undefined) => !v ? '' :
    v.replace(/_/g, ' ').toLowerCase()
     .split(' ').map(w => w.charAt(0).toUpperCase() + w.slice(1)).join(' ')
  const b = enumToLabel(car.brand)
  const m = enumToLabel(car.model)
  const name = m.toLowerCase().startsWith(b.toLowerCase()) ? m : `${b} ${m}`.trim()
  return car.licensePlate ? `${name} · ${car.licensePlate}` : name
}

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
  <section
    class="rounded-sm border-2 border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-800 p-5 md:p-6 shadow-[4px_4px_0_0_#d1d5db] dark:shadow-[4px_4px_0_0_#374151]">
    <p class="text-amber-600 dark:text-amber-500 text-[11px] font-bold uppercase tracking-[0.14em] mb-2 flex items-center gap-2">
      <span class="inline-flex w-5 h-5 bg-amber-500 text-gray-950 rounded-sm items-center justify-center text-[11px] font-extrabold">2</span>
      Upload
    </p>

    <h3 class="text-lg md:text-xl font-bold text-gray-900 dark:text-white tracking-tight mb-4">
      {{ t('xpeng.step_upload_title') }}
    </h3>

    <div class="space-y-4">
      <div>
        <label class="block text-[11px] font-bold uppercase tracking-wider text-gray-500 dark:text-gray-400 mb-1.5">
          {{ t('xpeng.car_label') }}
        </label>
        <div class="relative">
          <select v-model="carId"
                  class="appearance-none w-full px-3 py-2.5 pr-10 border-2 border-gray-300 dark:border-gray-600 rounded-sm bg-white dark:bg-gray-900 text-gray-900 dark:text-white text-sm font-medium focus:outline-none focus:border-amber-500 dark:focus:border-amber-500 transition-colors">
            <option value="" disabled>{{ t('cars.select_placeholder') }}</option>
            <option v-for="car in carsReadyForUpload" :key="car.id" :value="car.id">
              {{ carLabel(car) }}
            </option>
          </select>
          <ChevronDownIcon class="w-4 h-4 absolute right-3 top-1/2 -translate-y-1/2 pointer-events-none text-gray-400 dark:text-gray-500" />
        </div>
      </div>

      <div>
        <label class="block text-[11px] font-bold uppercase tracking-wider text-gray-500 dark:text-gray-400 mb-1.5">
          {{ t('xpeng.file_label') }}
        </label>
        <input type="file" accept=".xlsx" @change="onFileChange"
               class="block w-full text-sm text-gray-700 dark:text-gray-300 file:mr-3 file:py-2 file:px-4 file:rounded-sm file:border-2 file:border-gray-400 dark:file:border-gray-600 file:bg-transparent file:text-gray-800 dark:file:text-gray-200 file:font-bold file:uppercase file:text-xs file:tracking-wider hover:file:bg-gray-50 dark:hover:file:bg-gray-700/40 file:cursor-pointer file:shadow-[3px_3px_0_0_#9ca3af] dark:file:shadow-[3px_3px_0_0_#4b5563] file:transition-[transform,box-shadow] file:duration-75 active:file:translate-x-[3px] active:file:translate-y-[3px] active:file:shadow-none" />
      </div>

      <div>
        <label class="block text-[11px] font-bold uppercase tracking-wider text-gray-500 dark:text-gray-400 mb-1.5">
          {{ t('xpeng.password_label') }}
          <span class="text-gray-400 dark:text-gray-500 normal-case tracking-normal font-medium">· {{ t('xpeng.password_optional') }}</span>
        </label>
        <input v-model="password" type="password"
               class="w-full px-3 py-2.5 border-2 border-gray-300 dark:border-gray-600 rounded-sm bg-white dark:bg-gray-900 dark:text-white text-sm focus:outline-none focus:border-amber-500 dark:focus:border-amber-500 transition-colors" />
      </div>
    </div>

    <div v-if="errorMessage"
         class="mt-4 border-l-2 border-red-500 bg-red-50 dark:bg-red-950/40 px-4 py-3 rounded-r-sm text-sm text-red-900 dark:text-red-200 flex gap-2">
      <ExclamationCircleIcon class="w-5 h-5 flex-shrink-0" />
      <span>{{ errorMessage }}</span>
    </div>

    <button @click="submit" :disabled="!canSubmit"
            class="mt-5 w-full inline-flex items-center justify-center gap-2 bg-amber-500 hover:bg-amber-400 disabled:bg-gray-300 dark:disabled:bg-gray-700 disabled:text-gray-500 dark:disabled:text-gray-500 disabled:cursor-not-allowed text-gray-950 font-bold uppercase tracking-wider text-xs px-5 py-3.5 rounded-sm border-2 border-amber-500 disabled:border-gray-300 dark:disabled:border-gray-700 shadow-[3px_3px_0_0_#030712] disabled:shadow-none active:translate-x-[3px] active:translate-y-[3px] active:shadow-none transition-[transform,box-shadow,background-color] duration-75">
      <span v-if="busy" class="w-4 h-4 border-2 border-gray-950/30 border-t-gray-950 rounded-full animate-spin" />
      <ArrowUpTrayIcon v-else class="w-4 h-4" />
      <span>{{ busy ? t('xpeng.uploading') : t('xpeng.btn_upload') }}</span>
    </button>
  </section>
</template>
