<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { XMarkIcon, ExclamationTriangleIcon, CheckCircleIcon, InformationCircleIcon, PencilSquareIcon, TrashIcon, ChevronDownIcon } from '@heroicons/vue/24/outline'
import api from '@/api/axios'
import { useLogUndo } from '../../composables/useLogUndo'
import EditLogModal from './EditLogModal.vue'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import { formatSocRange } from '../../utils/socRange'
import ToggleSwitch from '../shared/ToggleSwitch.vue'
import Collapse from '../shared/Collapse.vue'

const { t } = useI18n()
const { formatConsumption, formatDistance, formatDecimal } = useLocaleFormat()
const ICON_BTN = 'grid h-10 w-10 place-items-center rounded-full text-gray-500 transition hover:bg-gray-100 hover:text-gray-900 dark:text-gray-400 dark:hover:bg-white/10 dark:hover:text-gray-100'
const props = defineProps<{ carId: string | null; open: boolean }>()
const emit = defineEmits<{ close: []; updated: [] }>()

interface ImplausibleLog {
  id: string
  loggedAt: string
  kwhCharged: number
  consumptionKwhPer100km: number | null
  distanceSinceLastChargeKm: number | null
  odometerKm: number | null
  includeInStatistics: boolean
  socBeforeChargePercent: number | null
  socAfterChargePercent: number | null
}

const logs = ref<ImplausibleLog[]>([])
const loading = ref(false)
const saving = ref<Set<string>>(new Set())
const editingLog = ref<any | null>(null)
const showExcluded = ref(false)

const openLogs = computed(() => logs.value.filter(l => l.includeInStatistics))
const excludedLogs = computed(() => logs.value.filter(l => !l.includeInStatistics))

async function loadLogs() {
  if (!props.carId) return
  loading.value = true
  try {
    const res = await api.get(`/logs/implausible?carId=${props.carId}`)
    logs.value = res.data
  } finally {
    loading.value = false
  }
}

watch(() => props.open, (open) => {
  if (open) { loadLogs(); showExcluded.value = false }
})

async function toggle(log: ImplausibleLog) {
  saving.value = new Set([...saving.value, log.id])
  try {
    const res = await api.patch(`/logs/${log.id}/statistics-inclusion`, {
      includeInStatistics: !log.includeInStatistics
    })
    const idx = logs.value.findIndex(l => l.id === log.id)
    if (idx !== -1) logs.value[idx] = { ...logs.value[idx], includeInStatistics: res.data.includeInStatistics }
    emit('updated')
  } finally {
    saving.value = new Set([...saving.value].filter(id => id !== log.id))
  }
}

const { deleteWithUndo } = useLogUndo()
async function deleteLog(log: ImplausibleLog) {
  try {
    await deleteWithUndo(log.id, () => { loadLogs(); emit('updated') })
    logs.value = logs.value.filter(l => l.id !== log.id)
    emit('updated')
  } catch {
    // ignore
  }
}

function onLogSaved() {
  editingLog.value = null
  loadLogs()
  emit('updated')
}

function onLogDeleted(logId: string) {
  editingLog.value = null
  logs.value = logs.value.filter(l => l.id !== logId)
  emit('updated')
}

function formatDate(iso: string) {
  return new Date(iso).toLocaleDateString(undefined, { day: '2-digit', month: '2-digit', year: 'numeric' })
}
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="fixed inset-0 z-50 flex items-end sm:items-center justify-center p-0 sm:p-4">
      <!-- Backdrop -->
      <div class="absolute inset-0 bg-black/50" @click="emit('close')" />

      <!-- Modal -->
      <div class="relative w-full sm:max-w-2xl bg-white dark:bg-gray-800 rounded-t-2xl sm:rounded-2xl edge-box overflow-hidden flex flex-col max-h-[90dvh] pb-[env(safe-area-inset-bottom)] sm:pb-0">
        <!-- Header -->
        <div class="flex items-center justify-between gap-3 px-5 py-4 border-b border-gray-200 dark:border-gray-700">
          <div class="flex items-center gap-2.5">
            <span class="grid h-8 w-8 place-items-center rounded-full bg-amber-100 text-amber-800 dark:bg-amber-400/15 dark:text-amber-300">
              <ExclamationTriangleIcon class="h-[18px] w-[18px]" aria-hidden="true" />
            </span>
            <h2 class="text-[17px] font-bold text-gray-900 dark:text-gray-50">{{ t('implausible.title') }}</h2>
            <span v-if="openLogs.length > 0"
              class="rounded-full bg-amber-100 px-2 py-0.5 text-xs font-semibold tabular-nums text-amber-800 dark:bg-amber-400/15 dark:text-amber-300">
              {{ openLogs.length }}
            </span>
          </div>
          <button type="button" @click="emit('close')" :aria-label="t('implausible.close')"
            class="btn-3d cta-shadow-sm grid h-10 w-10 shrink-0 place-items-center rounded-full border-2 border-edge bg-white text-gray-800 dark:bg-white/10 dark:text-gray-200">
            <XMarkIcon class="h-5 w-5" aria-hidden="true" />
          </button>
        </div>

        <!-- Info -->
        <div class="mx-5 mt-3 flex items-start gap-2 rounded-xl bg-gray-50 px-3 py-2.5 dark:bg-white/5">
          <InformationCircleIcon class="mt-0.5 h-4 w-4 shrink-0 text-gray-500 dark:text-gray-400" aria-hidden="true" />
          <p class="text-[13px] leading-relaxed text-gray-700 dark:text-gray-300">
            {{ t('implausible.info') }}
          </p>
        </div>

        <!-- Content -->
        <div class="overflow-y-auto flex-1">
          <!-- Loading -->
          <div v-if="loading" class="flex items-center justify-center py-12">
            <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-gray-400" />
          </div>

          <template v-else>
            <!-- All clear -->
            <div v-if="openLogs.length === 0 && excludedLogs.length === 0"
              class="flex flex-col items-center justify-center py-12 gap-2">
              <CheckCircleIcon class="h-10 w-10 text-green-600 dark:text-green-400" aria-hidden="true" />
              <p class="text-[15px] font-semibold text-gray-900 dark:text-gray-100">{{ t('implausible.all_clear') }}</p>
            </div>

            <!-- All excluded -->
            <div v-else-if="openLogs.length === 0"
              class="flex flex-col items-center justify-center py-8 gap-2">
              <CheckCircleIcon class="h-10 w-10 text-green-600 dark:text-green-400" aria-hidden="true" />
              <p class="text-[15px] font-semibold text-gray-900 dark:text-gray-100">{{ t('implausible.all_excluded') }}</p>
            </div>

            <!-- Open entries -->
            <ul v-if="openLogs.length > 0" class="divide-y divide-gray-100 dark:divide-gray-700">
              <li v-for="log in openLogs" :key="log.id"
                class="flex items-center gap-3 px-5 py-3.5">
                <div class="min-w-0 flex-1">
                  <div class="flex items-center gap-2 flex-wrap">
                    <span class="text-[15px] font-semibold text-gray-900 tabular-nums dark:text-gray-100">
                      {{ formatDate(log.loggedAt) }}
                    </span>
                    <span v-if="log.kwhCharged != null" class="text-[13px] text-gray-600 tabular-nums dark:text-gray-300">
                      {{ formatDecimal(Number(log.kwhCharged), 1) }} kWh
                    </span>
                    <span v-if="formatSocRange(log.socBeforeChargePercent, log.socAfterChargePercent)"
                      class="text-[13px] text-gray-500 tabular-nums dark:text-gray-400">
                      {{ formatSocRange(log.socBeforeChargePercent, log.socAfterChargePercent) }}
                    </span>
                  </div>
                  <div class="flex items-center gap-3 mt-1 flex-wrap">
                    <span v-if="log.consumptionKwhPer100km != null"
                      class="text-[13px] font-semibold tabular-nums text-amber-700 dark:text-amber-400">
                      {{ formatConsumption(log.consumptionKwhPer100km) }}
                    </span>
                    <span v-if="log.distanceSinceLastChargeKm != null"
                      class="text-[13px] tabular-nums text-gray-600 dark:text-gray-300">
                      +{{ formatDistance(log.distanceSinceLastChargeKm) }}
                    </span>
                    <span v-if="log.odometerKm != null"
                      class="text-[13px] tabular-nums text-gray-500 dark:text-gray-400">
                      {{ formatDistance(log.odometerKm) }}
                    </span>
                  </div>
                </div>
                <div class="flex items-center gap-1 shrink-0">
                  <ToggleSwitch :model-value="true" :label="t('implausible.exclude_title')" class="mr-1"
                    :class="saving.has(log.id) ? 'pointer-events-none opacity-50' : ''" @update:model-value="toggle(log)" />
                  <button type="button" @click="editingLog = log" :class="ICON_BTN"
                    :title="t('implausible.edit_title')" :aria-label="t('implausible.edit_title')">
                    <PencilSquareIcon class="h-4 w-4" aria-hidden="true" />
                  </button>
                  <button type="button" @click="deleteLog(log)" :class="ICON_BTN"
                    :title="t('implausible.delete_title')" :aria-label="t('implausible.delete_title')">
                    <TrashIcon class="h-4 w-4" aria-hidden="true" />
                  </button>
                </div>
              </li>
            </ul>

            <!-- Excluded section -->
            <div v-if="excludedLogs.length > 0" class="border-t border-gray-200 dark:border-gray-700">
              <button type="button"
                @click="showExcluded = !showExcluded" :aria-expanded="showExcluded"
                class="w-full flex min-h-12 items-center justify-between px-5 text-[14px] font-semibold text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-white/5 transition">
                <span>{{ t('implausible.excluded_count', excludedLogs.length) }}</span>
                <ChevronDownIcon :class="['h-4 w-4 text-gray-400 transition-transform duration-300 ease-out', showExcluded ? 'rotate-180' : '']" aria-hidden="true" />
              </button>
              <Collapse :open="showExcluded">
              <ul class="divide-y divide-gray-200 dark:divide-gray-700 bg-gray-50 dark:bg-white/5">
                <li v-for="log in excludedLogs" :key="log.id"
                  class="flex items-center gap-3 px-5 py-3.5 opacity-60">
                  <div class="min-w-0 flex-1">
                    <div class="flex items-center gap-2 flex-wrap">
                      <span class="text-[14px] font-semibold text-gray-700 tabular-nums dark:text-gray-300">
                        {{ formatDate(log.loggedAt) }}
                      </span>
                      <span v-if="log.kwhCharged != null" class="text-[13px] text-gray-500 tabular-nums dark:text-gray-400">
                        {{ formatDecimal(Number(log.kwhCharged), 1) }} kWh
                      </span>
                    </div>
                    <div class="flex items-center gap-3 mt-1 flex-wrap">
                      <span v-if="log.consumptionKwhPer100km != null"
                        class="text-[13px] font-semibold tabular-nums text-gray-600 dark:text-gray-300">
                        {{ formatConsumption(log.consumptionKwhPer100km) }}
                      </span>
                      <span v-if="log.distanceSinceLastChargeKm != null"
                        class="text-[13px] tabular-nums text-gray-500 dark:text-gray-400">
                        {{ formatDistance(log.distanceSinceLastChargeKm) }}
                      </span>
                    </div>
                  </div>
                  <div class="flex items-center gap-1 shrink-0">
                    <ToggleSwitch :model-value="false" :label="t('implausible.include_title')" class="mr-1"
                      :class="saving.has(log.id) ? 'pointer-events-none opacity-50' : ''" @update:model-value="toggle(log)" />
                    <button type="button" @click="deleteLog(log)" :class="ICON_BTN"
                      :title="t('implausible.delete_title')" :aria-label="t('implausible.delete_title')">
                      <TrashIcon class="h-4 w-4" aria-hidden="true" />
                    </button>
                  </div>
                </li>
              </ul>
              </Collapse>
            </div>
          </template>
        </div>

        <!-- Footer -->
        <div class="px-5 py-3 border-t border-gray-200 dark:border-gray-700">
          <button type="button" @click="emit('close')"
            class="btn-3d cta-shadow-sm flex min-h-11 w-full items-center justify-center rounded-full border-2 border-edge bg-white text-sm font-semibold text-gray-800 transition-colors hover:bg-gray-50 dark:bg-white/10 dark:text-gray-200 dark:hover:bg-white/15">
            {{ t('implausible.close') }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>

  <Teleport to="body">
    <EditLogModal
      v-if="editingLog"
      :log="editingLog"
      @close="editingLog = null"
      @saved="onLogSaved"
      @deleted="onLogDeleted"
    />
  </Teleport>
</template>
