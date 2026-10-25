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
  if (ds === 'EU_DATA_ACT_SYNC') return 'AutoSync (VW Data Act)'
  if (ds === 'EU_DATA_ACT_IMPORT') return 'VW Data Act (Upload)'
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
        class="relative w-full sm:max-w-lg bg-white dark:bg-gray-800 rounded-t-2xl sm:rounded-2xl edge-box overflow-hidden flex flex-col max-h-[90dvh] pb-[env(safe-area-inset-bottom)] sm:pb-0"
        role="dialog"
        aria-modal="true"
        :aria-labelledby="'merge-modal-title'"
      >
        <!-- Header -->
        <div class="flex items-center justify-between gap-3 px-5 py-4 border-b border-gray-200 dark:border-gray-700 flex-shrink-0">
          <div class="flex items-center gap-2.5">
            <span class="grid h-8 w-8 place-items-center rounded-full bg-gray-100 text-gray-700 dark:bg-white/10 dark:text-gray-200">
              <LinkIcon class="h-[18px] w-[18px]" aria-hidden="true" />
            </span>
            <h2 id="merge-modal-title" class="text-[17px] font-bold text-gray-900 dark:text-gray-50">
              {{ t('dashboard.merge_title') }}
            </h2>
          </div>
          <button
            type="button"
            @click="emit('close')"
            class="btn-3d cta-shadow-sm grid h-10 w-10 shrink-0 place-items-center rounded-full border-2 border-edge bg-white text-gray-800 dark:bg-white/10 dark:text-gray-200"
            :aria-label="t('dashboard.trip_cancel')"
          >
            <XMarkIcon class="h-5 w-5" aria-hidden="true" />
          </button>
        </div>

        <!-- Info banner -->
        <div class="mx-5 mt-3 flex items-start gap-2 rounded-xl bg-gray-50 px-3 py-2.5 flex-shrink-0 dark:bg-white/5">
          <InformationCircleIcon class="mt-0.5 h-4 w-4 shrink-0 text-gray-500 dark:text-gray-400" aria-hidden="true" />
          <p class="text-[13px] leading-relaxed text-gray-700 dark:text-gray-300">{{ t('dashboard.merge_info') }}</p>
        </div>

        <!-- Target log info -->
        <div class="px-5 py-3 border-b border-gray-200 dark:border-gray-700 flex-shrink-0">
          <p class="mb-1.5 text-[11.5px] font-semibold uppercase tracking-[0.06em] text-gray-500 dark:text-gray-400">{{ t('dashboard.edit_title') }}</p>
          <div class="flex items-center justify-between gap-2 text-[15px] text-gray-900 tabular-nums dark:text-gray-100">
            <span class="font-semibold">{{ formatDate(entry.loggedAt) }}</span>
            <div class="flex items-center gap-2 text-xs text-gray-500 dark:text-gray-400">
              <span v-if="entry.kwhCharged != null" class="rounded-full bg-gray-100 px-1.5 py-0.5 font-semibold text-gray-700 dark:bg-white/10 dark:text-gray-200">
                {{ formatKwh(entry.kwhCharged) }}
              </span>
              <span v-if="entry.kwhAtVehicle != null" class="rounded-full bg-gray-100 px-1.5 py-0.5 font-semibold text-gray-700 dark:bg-white/10 dark:text-gray-200">
                {{ formatKwh(entry.kwhAtVehicle) }}
              </span>
              <span class="text-gray-400 dark:text-gray-500">{{ sourceLabel(entry) }}</span>
            </div>
          </div>
        </div>

        <!-- Candidate list -->
        <div class="overflow-y-auto flex-1">
          <div class="px-5 pt-3 pb-1">
            <p class="text-[11.5px] font-semibold uppercase tracking-[0.06em] text-gray-500 dark:text-gray-400">{{ t('dashboard.merge_select_hint') }}</p>
          </div>

          <div v-if="candidates.length === 0" class="px-5 py-6 text-center">
            <p class="text-sm text-gray-500 dark:text-gray-400">{{ t('dashboard.merge_no_candidates') }}</p>
          </div>

          <ul v-else class="divide-y divide-gray-200 dark:divide-gray-700">
            <li
              v-for="candidate in candidates"
              :key="candidate.id"
              @click="selectedSourceId = candidate.id"
              :class="[
                'flex items-center gap-3 px-5 py-3.5 cursor-pointer transition',
                selectedSourceId === candidate.id
                  ? 'bg-gray-50 dark:bg-white/10'
                  : 'hover:bg-gray-50 dark:hover:bg-white/5'
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
                  ? 'border-gray-900 bg-gray-900 dark:border-gray-100 dark:bg-gray-100'
                  : 'border-gray-300 dark:border-gray-500'
              ]">
                <div v-if="selectedSourceId === candidate.id" class="w-1.5 h-1.5 rounded-full bg-white dark:bg-gray-900" />
              </div>

              <!-- Candidate info -->
              <div class="min-w-0 flex-1">
                <div class="flex items-center justify-between gap-2 flex-wrap">
                  <span class="text-[15px] font-semibold text-gray-900 tabular-nums dark:text-gray-100">
                    {{ formatDate(candidate.loggedAt) }}
                  </span>
                  <div class="flex items-center gap-1.5 text-xs">
                    <span v-if="candidate.kwhCharged != null" class="rounded-full bg-gray-100 px-1.5 py-0.5 font-semibold text-gray-700 dark:bg-white/10 dark:text-gray-200">
                      {{ formatKwh(candidate.kwhCharged) }}
                    </span>
                    <span v-if="candidate.kwhAtVehicle != null" class="rounded-full bg-gray-100 px-1.5 py-0.5 font-semibold text-gray-700 dark:bg-white/10 dark:text-gray-200">
                      {{ formatKwh(candidate.kwhAtVehicle) }}
                    </span>
                    <span class="text-gray-400 dark:text-gray-500">{{ sourceLabel(candidate) }}</span>
                  </div>
                </div>
              </div>
            </li>
          </ul>

          <!-- Preview -->
          <div v-if="mergedPreview" class="mx-5 my-3 rounded-xl border-2 border-gray-900 p-3 dark:border-gray-700">
            <p class="mb-2 text-[11.5px] font-semibold uppercase tracking-[0.06em] text-gray-500 dark:text-gray-400">{{ t('dashboard.merge_preview_label') }}</p>
            <div class="grid grid-cols-2 gap-x-4 gap-y-1 text-[13px] tabular-nums">
              <template v-if="mergedPreview.kwhCharged != null">
                <span class="text-gray-500 dark:text-gray-400">{{ t('dashboard.merge_kwh_charged') }}</span>
                <span class="font-semibold text-gray-900 dark:text-gray-100">{{ formatKwh(mergedPreview.kwhCharged) }}</span>
              </template>
              <template v-if="mergedPreview.kwhAtVehicle != null">
                <span class="text-gray-500 dark:text-gray-400">{{ t('dashboard.merge_kwh_vehicle') }}</span>
                <span class="font-semibold text-gray-900 dark:text-gray-100">{{ formatKwh(mergedPreview.kwhAtVehicle) }}</span>
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
        <div v-if="selectedSourceId" class="px-5 py-3 border-t border-gray-200 dark:border-gray-700 flex-shrink-0">
          <label class="flex items-start gap-3 cursor-pointer select-none">
            <input
              type="checkbox"
              v-model="preferSource"
              class="mt-0.5 h-5 w-5 cursor-pointer rounded border-2 border-gray-900 accent-gray-900 dark:border-gray-300 dark:accent-gray-100"
            />
            <span class="text-sm text-gray-700 dark:text-gray-300">
              {{ t('dashboard.merge_prefer_source') }}
              <span class="block text-xs text-gray-500 dark:text-gray-400 mt-0.5">{{ t('dashboard.merge_prefer_source_hint') }}</span>
            </span>
          </label>
        </div>

        <!-- Error -->
        <div v-if="error" role="alert" class="mx-5 mb-3 flex-shrink-0 rounded-xl bg-amber-50 px-3 py-2 text-[13px] text-amber-900 dark:bg-amber-400/10 dark:text-amber-200">
          {{ error }}
        </div>

        <!-- Footer -->
        <div class="flex items-center gap-2 px-5 py-4 border-t border-gray-200 dark:border-gray-700 flex-shrink-0">
          <button
            type="button"
            @click="emit('close')"
            class="min-h-12 flex-1 rounded-xl px-3 text-[15px] font-semibold text-gray-600 hover:text-gray-900 dark:text-gray-300 dark:hover:text-gray-100"
          >
            {{ t('dashboard.trip_cancel') }}
          </button>
          <button
            type="button"
            :disabled="!selectedSourceId || saving"
            @click="selectedSourceId && emit('merge', { sourceLogId: selectedSourceId, preferSource: preferSource })"
            class="btn-3d flex min-h-12 flex-1 items-center justify-center gap-2 rounded-xl border-2 border-edge bg-green-600 px-3 text-[15px] font-semibold text-white transition hover:bg-green-700 disabled:cursor-not-allowed disabled:opacity-40"
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
