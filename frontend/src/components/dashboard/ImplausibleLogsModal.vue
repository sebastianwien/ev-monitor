<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { XMarkIcon, ExclamationTriangleIcon, CheckCircleIcon, InformationCircleIcon, PencilSquareIcon, TrashIcon, ChevronDownIcon } from '@heroicons/vue/24/outline'
import api from '@/api/axios'
import EditLogModal from './EditLogModal.vue'
import { useLocaleFormat } from '../../composables/useLocaleFormat'

const { t } = useI18n()
const { formatConsumption, formatDistance } = useLocaleFormat()
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

async function deleteLog(log: ImplausibleLog) {
  if (!confirm(t('implausible.confirm_delete'))) return
  try {
    await api.delete(`/logs/${log.id}`)
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
      <div class="relative w-full sm:max-w-2xl bg-white dark:bg-gray-800 sm:border-2 sm:border-gray-900 sm:dark:border-white sm:rounded-sm sm:shadow-[4px_4px_0_0_#030712] sm:dark:shadow-[4px_4px_0_0_#ffffff] overflow-hidden flex flex-col max-h-[90dvh]">
        <!-- Header -->
        <div class="flex items-center justify-between px-5 py-4 border-b-2 border-gray-300 dark:border-gray-700">
          <div class="flex items-center gap-2">
            <div class="shrink-0 rounded-sm border-2 border-gray-900 dark:border-white bg-amber-500 p-1.5 w-8 h-8 flex items-center justify-center">
              <ExclamationTriangleIcon class="h-4 w-4 text-gray-950" />
            </div>
            <h2 class="text-base font-bold tracking-tight text-gray-900 dark:text-gray-100">{{ t('implausible.title') }}</h2>
            <span v-if="openLogs.length > 0"
              class="text-[10px] font-bold uppercase tracking-wider bg-amber-500 text-gray-950 border-2 border-amber-500 px-2 py-0.5 rounded-sm">
              {{ openLogs.length }}
            </span>
          </div>
          <button @click="emit('close')"
            class="p-2 border-2 border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-800 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-sm shadow-[2px_2px_0_0_#9ca3af] dark:shadow-[2px_2px_0_0_#4b5563] active:translate-x-[2px] active:translate-y-[2px] active:shadow-none transition-[transform,box-shadow] duration-75">
            <XMarkIcon class="h-4 w-4 text-gray-600 dark:text-gray-300" />
          </button>
        </div>

        <!-- Info -->
        <div class="px-5 py-3 border-l-2 border-amber-500 bg-amber-50 dark:bg-amber-950/30 flex items-start gap-2">
          <InformationCircleIcon class="h-4 w-4 text-amber-600 dark:text-amber-400 mt-0.5 shrink-0" />
          <p class="text-xs font-medium text-amber-700 dark:text-amber-300 leading-relaxed">
            {{ t('implausible.info') }}
          </p>
        </div>

        <!-- Content -->
        <div class="overflow-y-auto flex-1">
          <!-- Loading -->
          <div v-if="loading" class="flex items-center justify-center py-12">
            <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-amber-500" />
          </div>

          <template v-else>
            <!-- All clear -->
            <div v-if="openLogs.length === 0 && excludedLogs.length === 0"
              class="flex flex-col items-center justify-center py-12 gap-2">
              <CheckCircleIcon class="h-10 w-10 text-green-400" />
              <p class="text-sm text-gray-500 dark:text-gray-400">{{ t('implausible.all_clear') }}</p>
            </div>

            <!-- All excluded -->
            <div v-else-if="openLogs.length === 0"
              class="flex flex-col items-center justify-center py-8 gap-2">
              <CheckCircleIcon class="h-10 w-10 text-green-400" />
              <p class="text-sm text-gray-500 dark:text-gray-400">{{ t('implausible.all_excluded') }}</p>
            </div>

            <!-- Open entries -->
            <ul v-if="openLogs.length > 0" class="divide-y-2 divide-gray-200 dark:divide-gray-700">
              <li v-for="log in openLogs" :key="log.id"
                class="flex items-center gap-3 px-5 py-3.5">
                <div class="min-w-0 flex-1">
                  <div class="flex items-center gap-2 flex-wrap">
                    <span class="text-sm font-medium text-gray-900 dark:text-gray-100">
                      {{ formatDate(log.loggedAt) }}
                    </span>
                    <span class="text-xs text-gray-500 dark:text-gray-400">
                      {{ log.kwhCharged?.toFixed(2) }} kWh
                    </span>
                    <span v-if="log.socBeforeChargePercent != null && log.socAfterChargePercent != null"
                      class="text-xs text-gray-400 dark:text-gray-500">
                      SoC {{ log.socBeforeChargePercent }}% - {{ log.socAfterChargePercent }}%
                    </span>
                  </div>
                  <div class="flex items-center gap-3 mt-1 flex-wrap">
                    <span v-if="log.consumptionKwhPer100km != null"
                      class="text-xs font-semibold text-red-600 dark:text-red-400">
                      {{ formatConsumption(log.consumptionKwhPer100km) }}
                    </span>
                    <span v-if="log.distanceSinceLastChargeKm != null"
                      class="text-xs text-gray-500 dark:text-gray-400">
                      {{ formatDistance(log.distanceSinceLastChargeKm) }}
                    </span>
                    <span v-if="log.odometerKm != null"
                      class="text-xs text-gray-400 dark:text-gray-500">
                      Odo: {{ formatDistance(log.odometerKm) }}
                    </span>
                  </div>
                </div>
                <div class="flex items-center gap-1 shrink-0">
                  <button
                    @click="toggle(log)"
                    :disabled="saving.has(log.id)"
                    :title="t('implausible.exclude_title')"
                    :class="['relative inline-flex h-5 w-9 items-center rounded-full border-2 border-gray-900 dark:border-white transition-colors disabled:opacity-50 mr-1 bg-emerald-500']">
                    <span class="inline-block h-2.5 w-2.5 transform rounded-full bg-white transition-transform translate-x-[18px]" />
                  </button>
                  <button @click="editingLog = log"
                    class="p-1.5 border-2 border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-800 hover:bg-blue-50 dark:hover:bg-blue-950/30 rounded-sm shadow-[2px_2px_0_0_#9ca3af] dark:shadow-[2px_2px_0_0_#4b5563] active:translate-x-[2px] active:translate-y-[2px] active:shadow-none transition-[transform,box-shadow] duration-75"
                    :title="t('implausible.edit_title')">
                    <PencilSquareIcon class="h-4 w-4 text-blue-600 dark:text-blue-400" />
                  </button>
                  <button @click="deleteLog(log)"
                    class="p-1.5 border-2 border-red-500 bg-white dark:bg-gray-900 text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-950/30 rounded-sm shadow-[2px_2px_0_0_#dc2626] active:translate-x-[2px] active:translate-y-[2px] active:shadow-none transition-[transform,box-shadow] duration-75"
                    :title="t('implausible.delete_title')">
                    <TrashIcon class="h-4 w-4" />
                  </button>
                </div>
              </li>
            </ul>

            <!-- Excluded section -->
            <div v-if="excludedLogs.length > 0" class="border-t-2 border-gray-300 dark:border-gray-700">
              <button
                @click="showExcluded = !showExcluded"
                class="w-full flex items-center justify-between px-5 py-3 text-[11px] font-bold uppercase tracking-wider text-gray-600 dark:text-gray-400 hover:bg-gray-50 dark:hover:bg-gray-700/50 transition">
                <span>{{ t('implausible.excluded_count', excludedLogs.length) }}</span>
                <ChevronDownIcon :class="['h-4 w-4 transition-transform', showExcluded ? 'rotate-180' : '']" />
              </button>
              <ul v-if="showExcluded" class="divide-y-2 divide-gray-200 dark:divide-gray-700 bg-gray-50 dark:bg-gray-900/40">
                <li v-for="log in excludedLogs" :key="log.id"
                  class="flex items-center gap-3 px-5 py-3.5 opacity-60">
                  <div class="min-w-0 flex-1">
                    <div class="flex items-center gap-2 flex-wrap">
                      <span class="text-sm font-medium text-gray-700 dark:text-gray-300">
                        {{ formatDate(log.loggedAt) }}
                      </span>
                      <span class="text-xs text-gray-400 dark:text-gray-500">
                        {{ log.kwhCharged?.toFixed(2) }} kWh
                      </span>
                    </div>
                    <div class="flex items-center gap-3 mt-1 flex-wrap">
                      <span v-if="log.consumptionKwhPer100km != null"
                        class="text-xs font-semibold text-red-400 dark:text-red-500">
                        {{ formatConsumption(log.consumptionKwhPer100km) }}
                      </span>
                      <span v-if="log.distanceSinceLastChargeKm != null"
                        class="text-xs text-gray-400 dark:text-gray-500">
                        {{ formatDistance(log.distanceSinceLastChargeKm) }}
                      </span>
                    </div>
                  </div>
                  <div class="flex items-center gap-1 shrink-0">
                    <button
                      @click="toggle(log)"
                      :disabled="saving.has(log.id)"
                      :title="t('implausible.include_title')"
                      :class="['relative inline-flex h-5 w-9 items-center rounded-full border-2 border-gray-900 dark:border-white transition-colors disabled:opacity-50 mr-1 bg-gray-300 dark:bg-gray-600']">
                      <span class="inline-block h-2.5 w-2.5 transform rounded-full bg-white transition-transform translate-x-[2px]" />
                    </button>
                    <button @click="deleteLog(log)"
                      class="p-1.5 border-2 border-red-500 bg-white dark:bg-gray-900 text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-950/30 rounded-sm shadow-[2px_2px_0_0_#dc2626] active:translate-x-[2px] active:translate-y-[2px] active:shadow-none transition-[transform,box-shadow] duration-75"
                      :title="t('implausible.delete_title')">
                      <TrashIcon class="h-4 w-4" />
                    </button>
                  </div>
                </li>
              </ul>
            </div>
          </template>
        </div>

        <!-- Footer -->
        <div class="px-5 py-3 border-t-2 border-gray-300 dark:border-gray-700 flex justify-end">
          <button @click="emit('close')"
            class="inline-flex items-center justify-center px-4 py-2.5 bg-white dark:bg-gray-800 hover:bg-gray-50 dark:hover:bg-gray-700 text-gray-700 dark:text-gray-300 font-bold uppercase tracking-wider text-[11px] rounded-sm border-2 border-gray-300 dark:border-gray-700 shadow-[2px_2px_0_0_#9ca3af] dark:shadow-[2px_2px_0_0_#4b5563] active:translate-x-[2px] active:translate-y-[2px] active:shadow-none transition-[transform,box-shadow] duration-75">
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
    />
  </Teleport>
</template>
