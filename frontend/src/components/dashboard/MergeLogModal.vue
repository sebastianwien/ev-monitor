<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { LinkIcon, XMarkIcon, InformationCircleIcon, CheckIcon } from '@heroicons/vue/24/outline'

const props = defineProps<{
  entry: any
  candidates: any[]
  saving: boolean
  error: string | null
}>()

const emit = defineEmits<{
  merge: [payload: { sourceLogId: string; preferSource: boolean }]
  close: []
}>()

const { t, locale } = useI18n()

const selectedSourceId = ref<string | null>(null)
const preferSource = ref(false)

const selectedSource = computed(() =>
  props.candidates.find((c) => c.id === selectedSourceId.value) ?? null
)

function formatDate(isoDate: string) {
  return new Date(isoDate).toLocaleString(locale.value, {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function formatKwh(val: number | null | undefined) {
  if (val == null) return null
  return val.toFixed(2) + ' kWh'
}

function sourceLabel(log: any): string {
  const ds = log.dataSource ?? ''
  if (ds === 'WALLBOX_GOE') return 'Wallbox (go-e)'
  if (ds === 'WALLBOX_OCPP') return 'Wallbox (OCPP)'
  if (ds === 'SMARTCAR_LIVE') return 'AutoSync (Smartcar)'
  if (ds === 'TESLA_LIVE' || ds === 'TESLA_FLEET_IMPORT') return 'Tesla'
  if (ds === 'USER_LOGGED') return 'Manuell'
  return ds
}

const mergedPreview = computed(() => {
  if (!selectedSource.value) return null
  const primary = preferSource.value ? selectedSource.value : props.entry
  const fallback = preferSource.value ? props.entry : selectedSource.value
  return {
    kwhCharged: primary.kwhCharged ?? fallback.kwhCharged,
    kwhAtVehicle: primary.kwhAtVehicle ?? fallback.kwhAtVehicle,
    socStart: primary.socBeforeChargePercent ?? fallback.socBeforeChargePercent,
    socEnd: primary.socAfterChargePercent ?? fallback.socAfterChargePercent,
    odometerKm: primary.odometerKm ?? fallback.odometerKm,
    loggedAt: primary.loggedAt ?? fallback.loggedAt,
  }
})
</script>

<template>
  <Teleport to="body">
    <div class="fixed inset-0 z-50 flex items-end sm:items-center justify-center p-0 sm:p-4">
      <!-- Backdrop -->
      <div class="absolute inset-0 bg-black/50" @click="emit('close')" />

      <!-- Modal -->
      <div
        class="relative w-full sm:max-w-lg bg-white dark:bg-gray-800 sm:rounded-sm shadow-[5px_5px_0_rgba(0,0,0,0.35)] dark:shadow-[5px_5px_0_rgba(255,255,255,0.35)] ring-1 ring-black/10 dark:ring-white/10 overflow-hidden flex flex-col max-h-[90dvh]"
        role="dialog"
        aria-modal="true"
        :aria-labelledby="'merge-modal-title'"
      >
        <!-- Header -->
        <div class="flex items-center justify-between px-5 py-4 border-b border-gray-100 dark:border-gray-700 flex-shrink-0">
          <div class="flex items-center gap-2">
            <LinkIcon class="h-5 w-5 text-blue-500" aria-hidden="true" />
            <h2 id="merge-modal-title" class="text-base font-semibold text-gray-900 dark:text-gray-100">
              {{ t('dashboard.merge_title') }}
            </h2>
          </div>
          <button
            @click="emit('close')"
            class="text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 p-1 rounded-sm focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-400"
            :aria-label="t('dashboard.trip_cancel')"
          >
            <XMarkIcon class="h-5 w-5" />
          </button>
        </div>

        <!-- Info banner -->
        <div class="px-5 py-3 bg-blue-50 dark:bg-blue-900/20 border-b border-blue-100 dark:border-blue-800/40 flex items-start gap-2 flex-shrink-0">
          <InformationCircleIcon class="h-4 w-4 text-blue-600 dark:text-blue-400 mt-0.5 shrink-0" aria-hidden="true" />
          <p class="text-xs text-blue-700 dark:text-blue-300 leading-relaxed">{{ t('dashboard.merge_info') }}</p>
        </div>

        <!-- Target log info -->
        <div class="px-5 py-3 border-b border-gray-100 dark:border-gray-700 flex-shrink-0">
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400 mb-1.5">{{ t('dashboard.edit_title') }}</p>
          <div class="flex items-center justify-between gap-2 text-sm text-gray-900 dark:text-gray-100">
            <span class="font-medium">{{ formatDate(entry.loggedAt) }}</span>
            <div class="flex items-center gap-2 text-xs text-gray-500 dark:text-gray-400">
              <span v-if="entry.kwhCharged != null" class="bg-orange-100 dark:bg-orange-900/30 text-orange-700 dark:text-orange-300 px-1.5 py-0.5 rounded">
                {{ formatKwh(entry.kwhCharged) }}
              </span>
              <span v-if="entry.kwhAtVehicle != null" class="bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-300 px-1.5 py-0.5 rounded">
                {{ formatKwh(entry.kwhAtVehicle) }}
              </span>
              <span class="text-gray-400 dark:text-gray-500">{{ sourceLabel(entry) }}</span>
            </div>
          </div>
        </div>

        <!-- Candidate list -->
        <div class="overflow-y-auto flex-1">
          <div class="px-5 pt-3 pb-1">
            <p class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ t('dashboard.merge_select_hint') }}</p>
          </div>

          <div v-if="candidates.length === 0" class="px-5 py-6 text-center">
            <p class="text-sm text-gray-500 dark:text-gray-400">{{ t('dashboard.merge_no_candidates') }}</p>
          </div>

          <ul v-else class="divide-y divide-gray-100 dark:divide-gray-700">
            <li
              v-for="candidate in candidates"
              :key="candidate.id"
              @click="selectedSourceId = candidate.id"
              :class="[
                'flex items-center gap-3 px-5 py-3.5 cursor-pointer transition',
                selectedSourceId === candidate.id
                  ? 'bg-blue-50 dark:bg-blue-900/20'
                  : 'hover:bg-gray-50 dark:hover:bg-gray-700/50'
              ]"
              role="radio"
              :aria-checked="selectedSourceId === candidate.id"
              tabindex="0"
              @keydown.enter.space.prevent="selectedSourceId = candidate.id"
            >
              <!-- Radio indicator -->
              <div :class="[
                'w-4 h-4 rounded-full border-2 flex-shrink-0 flex items-center justify-center',
                selectedSourceId === candidate.id
                  ? 'border-blue-500 bg-blue-500'
                  : 'border-gray-300 dark:border-gray-500'
              ]">
                <div v-if="selectedSourceId === candidate.id" class="w-1.5 h-1.5 rounded-full bg-white" />
              </div>

              <!-- Candidate info -->
              <div class="min-w-0 flex-1">
                <div class="flex items-center justify-between gap-2 flex-wrap">
                  <span class="text-sm font-medium text-gray-900 dark:text-gray-100">
                    {{ formatDate(candidate.loggedAt) }}
                  </span>
                  <div class="flex items-center gap-1.5 text-xs">
                    <span v-if="candidate.kwhCharged != null" class="bg-orange-100 dark:bg-orange-900/30 text-orange-700 dark:text-orange-300 px-1.5 py-0.5 rounded">
                      {{ formatKwh(candidate.kwhCharged) }}
                    </span>
                    <span v-if="candidate.kwhAtVehicle != null" class="bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-300 px-1.5 py-0.5 rounded">
                      {{ formatKwh(candidate.kwhAtVehicle) }}
                    </span>
                    <span class="text-gray-400 dark:text-gray-500">{{ sourceLabel(candidate) }}</span>
                  </div>
                </div>
              </div>
            </li>
          </ul>

          <!-- Preview -->
          <div v-if="mergedPreview" class="mx-5 my-3 p-3 bg-gray-50 dark:bg-gray-700/50 rounded-sm border border-gray-200 dark:border-gray-600">
            <p class="text-xs font-medium text-gray-500 dark:text-gray-400 mb-2">{{ t('dashboard.merge_preview_label') }}</p>
            <div class="grid grid-cols-2 gap-x-4 gap-y-1 text-xs">
              <template v-if="mergedPreview.kwhCharged != null">
                <span class="text-gray-500 dark:text-gray-400">{{ t('dashboard.merge_kwh_charged') }}</span>
                <span class="font-medium text-orange-700 dark:text-orange-300">{{ formatKwh(mergedPreview.kwhCharged) }}</span>
              </template>
              <template v-if="mergedPreview.kwhAtVehicle != null">
                <span class="text-gray-500 dark:text-gray-400">{{ t('dashboard.merge_kwh_vehicle') }}</span>
                <span class="font-medium text-green-700 dark:text-green-300">{{ formatKwh(mergedPreview.kwhAtVehicle) }}</span>
              </template>
              <template v-if="mergedPreview.socStart != null">
                <span class="text-gray-500 dark:text-gray-400">SoC Start</span>
                <span class="font-medium text-gray-900 dark:text-gray-100">{{ mergedPreview.socStart }}%</span>
              </template>
              <template v-if="mergedPreview.socEnd != null">
                <span class="text-gray-500 dark:text-gray-400">SoC Ende</span>
                <span class="font-medium text-gray-900 dark:text-gray-100">{{ mergedPreview.socEnd }}%</span>
              </template>
            </div>
          </div>
        </div>

        <!-- preferSource toggle -->
        <div v-if="selectedSourceId" class="px-5 py-3 border-t border-gray-100 dark:border-gray-700 flex-shrink-0">
          <label class="flex items-start gap-3 cursor-pointer select-none">
            <input
              type="checkbox"
              v-model="preferSource"
              class="mt-0.5 h-4 w-4 rounded border-gray-300 dark:border-gray-600 text-blue-600 focus:ring-blue-500 focus:ring-2 cursor-pointer"
            />
            <span class="text-sm text-gray-700 dark:text-gray-300">
              {{ t('dashboard.merge_prefer_source') }}
              <span class="block text-xs text-gray-500 dark:text-gray-400 mt-0.5">{{ t('dashboard.merge_prefer_source_hint') }}</span>
            </span>
          </label>
        </div>

        <!-- Error -->
        <div v-if="error" class="mx-5 mb-3 px-3 py-2 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-700 rounded-sm text-xs text-red-700 dark:text-red-300 flex-shrink-0">
          {{ error }}
        </div>

        <!-- Footer -->
        <div class="flex items-center justify-end gap-3 px-5 py-4 border-t border-gray-100 dark:border-gray-700 flex-shrink-0">
          <button
            type="button"
            @click="emit('close')"
            class="px-4 py-2 text-sm text-gray-600 dark:text-gray-400 hover:text-gray-900 dark:hover:text-gray-100 transition focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-400 rounded-sm"
          >
            {{ t('dashboard.trip_cancel') }}
          </button>
          <button
            type="button"
            :disabled="!selectedSourceId || saving"
            @click="selectedSourceId && emit('merge', { sourceLogId: selectedSourceId, preferSource: preferSource })"
            :class="[
              'flex items-center gap-2 px-4 py-2 text-sm font-medium rounded-sm transition focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-400',
              selectedSourceId && !saving
                ? 'bg-blue-600 hover:bg-blue-700 text-white'
                : 'bg-gray-200 dark:bg-gray-700 text-gray-400 dark:text-gray-500 cursor-not-allowed'
            ]"
          >
            <span v-if="saving" class="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
            <CheckIcon v-else class="w-4 h-4" />
            {{ t('dashboard.merge_confirm') }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>
