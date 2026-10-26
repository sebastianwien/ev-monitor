<script setup lang="ts">
import UploadSampleNotice from './UploadSampleNotice.vue'
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowUpTrayIcon, ArrowPathIcon, ExclamationCircleIcon, ChevronDownIcon, ArrowTopRightOnSquareIcon } from '@heroicons/vue/24/outline'
import { CI_BTN_PRIMARY, CI_INPUT, CI_LABEL, CI_CARD, CI_NOTE } from '../../utils/ciClasses'
import xpengService, { type XpengJobDto } from '../../api/xpengService'
import type { Car } from '../../api/carService'
import { carLabel } from '../../composables/useCarLabel'


const { t } = useI18n()

const props = defineProps<{
  cars: Car[]
  jobRunning?: boolean
  errorMessage?: string
}>()

const emit = defineEmits<{
  (e: 'uploaded', job: XpengJobDto): void
  (e: 'error', msg: string): void
}>()

const carId = defineModel<string | null>('carId', { default: null })
const file = ref<File | null>(null)
const uploading = ref(false)

watch(() => props.cars, (cars) => {
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
    const job = await xpengService.uploadZip(carId.value, file.value)
    file.value = null
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
  <section :class="CI_CARD">
    <p :class="[CI_LABEL, 'mb-2']">
      {{ t('xpeng.eyebrow_step2') }}
    </p>

    <h3 class="mb-2 text-[18px] font-bold tracking-tight text-gray-900 dark:text-gray-100">
      {{ t('xpeng.step_upload_title') }}
    </h3>

    <p class="text-gray-600 dark:text-gray-400 text-sm leading-relaxed mb-3">
      {{ t('xpeng.upload_howto') }}
    </p>

    <a
      href="https://www.xpeng.com/data-act"
      target="_blank"
      rel="noopener noreferrer"
      class="mb-4 inline-flex min-h-11 items-center gap-1.5 text-sm font-semibold text-gray-900 underline underline-offset-2 hover:no-underline dark:text-gray-100">
      {{ t('xpeng.manual_prompt_portal_link') }}
      <ArrowTopRightOnSquareIcon class="h-4 w-4" aria-hidden="true" />
    </a>

    <div class="grid gap-4">
      <div>
        <label for="xpeng-car" :class="[CI_LABEL, 'mb-1.5 block']">
          {{ t('xpeng.car_label') }}
        </label>
        <div class="relative">
          <select id="xpeng-car" v-model="carId" :class="[CI_INPUT, 'appearance-none pr-10']">
            <option value="" disabled>{{ t('cars.select_placeholder') }}</option>
            <option v-for="car in cars" :key="car.id" :value="car.id">
              {{ carLabel(car) }}
            </option>
          </select>
          <ChevronDownIcon class="pointer-events-none absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400 dark:text-gray-500" aria-hidden="true" />
        </div>
      </div>

      <div>
        <label for="xpeng-file" :class="[CI_LABEL, 'mb-1.5 block']">
          {{ t('xpeng.file_label') }}
        </label>
        <input id="xpeng-file" type="file" accept=".zip" @change="onFileChange"
               class="block w-full text-sm text-gray-700 file:mr-3 file:min-h-11 file:cursor-pointer file:rounded-xl file:border-2 file:border-edge file:bg-white file:px-4 file:text-sm file:font-semibold file:text-gray-900 hover:file:bg-gray-50 dark:text-gray-300 dark:file:bg-white/10 dark:file:text-gray-100" />
      </div>
    </div>

    <div v-if="errorMessage" role="alert"
         :class="[CI_NOTE, 'mt-4 flex gap-2 py-3 font-semibold text-gray-900 dark:text-gray-100']">
      <ExclamationCircleIcon class="h-5 w-5 shrink-0 text-gray-600 dark:text-gray-300" aria-hidden="true" />
      <span>{{ errorMessage }}</span>
    </div>

    <button @click="submit" :disabled="!canSubmit"
            type="button" :class="[CI_BTN_PRIMARY, 'mt-5 w-full']">
      <ArrowPathIcon v-if="busy" class="h-5 w-5 animate-spin" aria-hidden="true" />
      <ArrowUpTrayIcon v-else class="h-5 w-5" aria-hidden="true" />
      <span>{{ busy ? t('xpeng.uploading') : t('xpeng.btn_upload') }}</span>
    </button>
    <UploadSampleNotice class="mt-3" />
  </section>
</template>
