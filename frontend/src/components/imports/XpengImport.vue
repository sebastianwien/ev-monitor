<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ExclamationCircleIcon, ShieldCheckIcon } from '@heroicons/vue/24/outline'
import XpengConsentStep from './XpengConsentStep.vue'
import XpengRequestMailStep from './XpengRequestMailStep.vue'
import XpengUploadStep from './XpengUploadStep.vue'
import XpengJobStatus from './XpengJobStatus.vue'
import XpengConnectionsList from './XpengConnectionsList.vue'
import { useXpengJobs } from '../../composables/useXpengJobs'
import type { XpengJobDto } from '../../api/xpengService'
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

const displayedError = computed(() => localError.value || error.value)

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
</script>

<template>
  <div class="space-y-6">
    <!-- Intro -->
    <div class="rounded-lg bg-blue-50 dark:bg-blue-900/20 p-4 border border-blue-200 dark:border-blue-800">
      <div class="flex gap-3">
        <ShieldCheckIcon class="w-6 h-6 text-blue-600 dark:text-blue-400 flex-shrink-0" />
        <div class="text-sm text-gray-800 dark:text-gray-200">
          <p class="font-medium mb-1">{{ t('xpeng.intro_title') }}</p>
          <p class="text-gray-600 dark:text-gray-400">{{ t('xpeng.intro_body') }}</p>
        </div>
      </div>
    </div>

    <div v-if="displayedError"
         class="rounded-lg bg-red-50 dark:bg-red-900/20 p-3 text-sm text-red-800 dark:text-red-200 flex gap-2">
      <ExclamationCircleIcon class="w-5 h-5 flex-shrink-0" />
      <span>{{ displayedError }}</span>
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
    </template>
  </div>
</template>
