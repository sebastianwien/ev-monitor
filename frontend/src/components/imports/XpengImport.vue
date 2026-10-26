<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ExclamationCircleIcon, TrashIcon } from '@heroicons/vue/24/outline'
import { CI_BTN_SECONDARY, CI_LABEL, CI_CARD, CI_NOTE } from '../../utils/ciClasses'
import XpengUploadStep from './XpengUploadStep.vue'
import XpengJobStatus from './XpengJobStatus.vue'
import XpengAutoSyncWaitlist from './XpengAutoSyncWaitlist.vue'
import { useXpengJobs } from '../../composables/useXpengJobs'
import xpengService, { type XpengJobDto } from '../../api/xpengService'
import type { Car } from '../../api/carService'

const { t } = useI18n()
const props = defineProps<{ cars: Car[] }>()

const {
  jobs, activeJob, loading, error,
  refresh, startPolling, tryResumeFromStorage,
} = useXpengJobs()

const localError = ref('')

// Manueller ZIP-Upload: jedes XPeng-Auto ist direkt uploadbar - die Verbindung
// (VIN <-> Auto) entsteht serverseitig beim ersten Upload aus der Datei.
const xpengCars = computed(() => props.cars.filter(c => c.brand === 'XPENG'))

const uploadCarId = ref<string | null>(null)

// Top banner only for composable errors (connections/jobs fetch). Upload-specific errors are
// shown inline in XpengUploadStep so they stay visible next to the action.
const topError = computed(() => error.value)
const jobRunning = computed(() =>
  activeJob.value?.status === 'QUEUED' || activeJob.value?.status === 'PROCESSING',
)

onMounted(async () => {
  await refresh()
  await tryResumeFromStorage()
  // Default selection - if there's exactly one XPeng car, preselect for upload
  if (xpengCars.value.length === 1) {
    uploadCarId.value = xpengCars.value[0].id
  }
})

function onError(msg: string) {
  localError.value = msg
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
  <div class="grid gap-5">
    <!-- Page header (Editorial style) -->
    <div class="pb-2">
      <p :class="[CI_LABEL, 'mb-2']">
        {{ t('xpeng.eyebrow_brand') }}
      </p>
      <h2 class="mb-2 text-[22px] font-bold leading-tight tracking-tight text-gray-900 md:text-2xl dark:text-gray-100">
        {{ t('xpeng.intro_title') }}
      </h2>
      <p class="text-gray-600 dark:text-gray-400 text-sm md:text-[15px] max-w-xl leading-relaxed">
        {{ t('xpeng.intro_body') }}
      </p>
    </div>

    <div v-if="topError" role="alert"
         :class="[CI_NOTE, 'flex gap-2 py-3 font-semibold text-gray-900 dark:text-gray-100']">
      <ExclamationCircleIcon class="h-5 w-5 shrink-0 text-gray-600 dark:text-gray-300" aria-hidden="true" />
      <span>{{ topError }}</span>
    </div>

    <div v-if="loading" class="text-center text-gray-500 dark:text-gray-400 py-8">{{ t('common.loading') }}</div>

    <template v-else>
      <div v-if="xpengCars.length === 0" class="text-center py-8 text-gray-500 dark:text-gray-400">
        {{ t('xpeng.no_xpeng_cars') }}
      </div>

      <XpengUploadStep
        v-if="xpengCars.length > 0"
        v-model:car-id="uploadCarId"
        :cars="xpengCars"
        :job-running="jobRunning"
        :error-message="localError"
        @uploaded="onUploaded"
        @error="onError"
      />

      <XpengJobStatus v-if="activeJob" :job="activeJob" />

      <!-- Opt-in Warteliste, falls XPeng jemals eine offizielle Schnittstelle bereitstellt -->
      <XpengAutoSyncWaitlist v-if="xpengCars.length > 0" />

      <!-- Destruktive Aktion: alle XPENG_IMPORT-Daten loeschen -->
      <div v-if="hasImportedData" :class="CI_CARD">
        <h3 class="mb-3 flex items-center gap-2 text-[18px] font-bold tracking-tight text-gray-900 dark:text-gray-100">
          <TrashIcon class="h-5 w-5 text-gray-500 dark:text-gray-400" aria-hidden="true" />
          {{ t('xpeng.delete_all_title') }}
        </h3>
        <p class="text-gray-600 dark:text-gray-400 text-sm leading-relaxed mb-4">
          {{ t('xpeng.delete_all_body') }}
        </p>
        <p v-if="deleteResultMsg"
           role="status" class="mb-3 text-sm font-semibold text-gray-900 dark:text-gray-100">
          {{ deleteResultMsg }}
        </p>
        <button
          type="button"
          :class="CI_BTN_SECONDARY"
          :disabled="deleting"
          @click="onDeleteAllData">
          <TrashIcon class="h-5 w-5" aria-hidden="true" />
          {{ deleting ? t('common.loading') : t('xpeng.delete_all_button') }}
        </button>
      </div>
    </template>
  </div>
</template>
