<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ExclamationCircleIcon, TrashIcon } from '@heroicons/vue/24/outline'
import XpengConsentStep from './XpengConsentStep.vue'
import XpengRequestMailStep from './XpengRequestMailStep.vue'
import XpengUploadStep from './XpengUploadStep.vue'
import XpengJobStatus from './XpengJobStatus.vue'
import XpengConnectionsList from './XpengConnectionsList.vue'
import { useXpengJobs } from '../../composables/useXpengJobs'
import xpengService, { type XpengJobDto } from '../../api/xpengService'
import type { Car } from '../../api/carService'

const { t } = useI18n()
const props = defineProps<{ cars: Car[] }>()

const {
  connections, jobs, activeJob, loading, error,
  refresh, startPolling, tryResumeFromStorage,
} = useXpengJobs()

const localError = ref('')

const xpengCars = computed(() => props.cars.filter(c => c.brand === 'XPENG'))
const connectedCarIds = computed(() => new Set(connections.value.map(c => c.carId)))
const carsNeedingConsent = computed(() =>
  xpengCars.value.filter(c => !connectedCarIds.value.has(c.id)),
)
const carsReadyForUpload = computed(() =>
  xpengCars.value.filter(c => connectedCarIds.value.has(c.id)),
)

const uploadCarId = ref<string | null>(null)
const selectedConnection = computed(() =>
  connections.value.find(c => c.carId === uploadCarId.value) ?? connections.value[0],
)
const selectedVin = computed(() => selectedConnection.value?.vin ?? '')

// Top banner only for composable errors (connections/jobs fetch). Upload-specific errors are
// shown inline in XpengUploadStep so they stay visible next to the action.
const topError = computed(() => error.value)
const jobRunning = computed(() =>
  activeJob.value?.status === 'QUEUED' || activeJob.value?.status === 'PROCESSING',
)

onMounted(async () => {
  await refresh()
  await tryResumeFromStorage()
  // Default selection - if there's exactly one connected car, preselect for upload
  if (carsReadyForUpload.value.length === 1) {
    uploadCarId.value = carsReadyForUpload.value[0].id
  }
})

function onError(msg: string) {
  localError.value = msg
}

async function onConsentGranted() {
  localError.value = ''
  await refresh()
  if (!uploadCarId.value && carsReadyForUpload.value.length === 1) {
    uploadCarId.value = carsReadyForUpload.value[0].id
  }
}

function onUploaded(job: XpengJobDto) {
  localError.value = ''
  activeJob.value = job
  startPolling(job.id)
}

// Es gibt importierte Daten zum Loeschen, sobald mind. ein erfolgreicher Import vorliegt.
const hasImportedData = computed(() => jobs.value.some(j => j.status === 'DONE' || j.status === 'FAILED'))
const deleting = ref(false)
const deleteResultMsg = ref('')

async function onDeleteAllData() {
  if (!confirm(t('xpeng.delete_all_confirm'))) return
  deleting.value = true
  deleteResultMsg.value = ''
  localError.value = ''
  try {
    const r = await xpengService.deleteAllImportedData()
    deleteResultMsg.value = t('xpeng.delete_all_success', {
      logs: r.chargingLogs, trips: r.trips, jobs: r.importJobs,
    })
    activeJob.value = null
    await refresh()
  } catch (e: any) {
    localError.value = e?.response?.data?.error || t('xpeng.delete_all_failed')
  } finally {
    deleting.value = false
  }
}
</script>

<template>
  <div class="space-y-5">
    <!-- Page header (Editorial style) -->
    <div class="pb-2">
      <p class="text-amber-600 dark:text-amber-500 text-[11px] font-bold uppercase tracking-[0.14em] mb-2">
        {{ t('xpeng.eyebrow_brand') }}
      </p>
      <h2 class="text-2xl md:text-3xl font-extrabold tracking-tight text-gray-900 dark:text-white leading-tight mb-2">
        {{ t('xpeng.intro_title') }}
      </h2>
      <p class="text-gray-600 dark:text-gray-400 text-sm md:text-[15px] max-w-xl leading-relaxed">
        {{ t('xpeng.intro_body') }}
      </p>
    </div>

    <div v-if="topError"
         class="rounded-sm border-2 border-red-300 dark:border-red-700 bg-red-50 dark:bg-red-950/40 p-3 text-sm text-red-900 dark:text-red-200 flex gap-2 shadow-[2px_2px_0_0_#fca5a5] dark:shadow-[2px_2px_0_0_#b91c1c]">
      <ExclamationCircleIcon class="w-5 h-5 flex-shrink-0" />
      <span>{{ topError }}</span>
    </div>

    <div v-if="loading" class="text-center text-gray-500 dark:text-gray-400 py-8">{{ t('common.loading') }}</div>

    <template v-else>
      <div v-if="xpengCars.length === 0" class="text-center py-8 text-gray-500 dark:text-gray-400">
        {{ t('xpeng.no_xpeng_cars') }}
      </div>

      <XpengConsentStep
        v-if="carsNeedingConsent.length > 0"
        :cars-needing-consent="carsNeedingConsent"
        @granted="onConsentGranted"
        @error="onError"
      />

      <template v-if="carsReadyForUpload.length > 0">
        <XpengRequestMailStep :vin="selectedVin" />
        <XpengUploadStep
          v-model:car-id="uploadCarId"
          :cars-ready-for-upload="carsReadyForUpload"
          :job-running="jobRunning"
          :error-message="localError"
          @uploaded="onUploaded"
          @error="onError"
        />
      </template>

      <XpengJobStatus v-if="activeJob" :job="activeJob" />

      <XpengConnectionsList
        :connections="connections"
        :jobs="jobs"
        @refresh="refresh"
        @error="onError"
      />

      <!-- Destruktive Aktion: alle XPENG_IMPORT-Daten loeschen -->
      <div v-if="hasImportedData"
           class="rounded-sm border-2 border-red-600 dark:border-red-700 bg-white dark:bg-gray-800 p-5 md:p-6 shadow-[2px_2px_0_0_#dc2626] dark:shadow-[2px_2px_0_0_#b91c1c]">
        <h3 class="text-lg md:text-xl font-bold text-gray-900 dark:text-white tracking-tight mb-3 flex items-center gap-2">
          <TrashIcon class="w-5 h-5 text-red-600 dark:text-red-400" />
          {{ t('xpeng.delete_all_title') }}
        </h3>
        <p class="text-gray-600 dark:text-gray-400 text-sm leading-relaxed mb-4">
          {{ t('xpeng.delete_all_body') }}
        </p>
        <p v-if="deleteResultMsg"
           class="text-sm font-medium text-green-700 dark:text-green-300 mb-3">
          {{ deleteResultMsg }}
        </p>
        <button
          type="button"
          class="inline-flex items-center gap-2 bg-red-600 hover:bg-red-500 disabled:opacity-50 disabled:cursor-not-allowed text-white font-bold uppercase tracking-wider text-xs px-5 py-3 rounded-sm border-2 border-red-600 shadow-[2px_2px_0_0_#030712] dark:shadow-[2px_2px_0_0_#030712] active:translate-x-[3px] active:translate-y-[3px] active:shadow-none transition-[transform,box-shadow] duration-75 font-family-inherit"
          :disabled="deleting"
          @click="onDeleteAllData">
          <TrashIcon class="w-4 h-4" />
          {{ deleting ? t('common.loading') : t('xpeng.delete_all_button') }}
        </button>
      </div>
    </template>
  </div>
</template>
