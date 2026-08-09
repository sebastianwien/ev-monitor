<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  Chart as ChartJS,
  LinearScale,
  LineElement,
  PointElement,
  Tooltip,
  type ChartData,
  type ChartOptions,
} from 'chart.js'
import { Line } from 'vue-chartjs'
import { XMarkIcon, PlusIcon, InformationCircleIcon } from '@heroicons/vue/24/outline'
import BottomSheet from '../shared/BottomSheet.vue'
import { carService, type Car, type BatterySohEntry, type BatterySohStatus } from '../../api/carService'
import {
  sohAxisBounds,
  sohEmptyStateKey,
  sohTrustKey,
  nominalCapacityKwh,
} from '../../composables/sohPresentation'

ChartJS.register(LinearScale, LineElement, PointElement, Tooltip)

const props = defineProps<{ car: Car }>()
const emit = defineEmits<{ close: []; changed: [] }>()

const { t, locale } = useI18n()

const sheet = ref<InstanceType<typeof BottomSheet> | null>(null)
const history = ref<BatterySohEntry[]>([])
const status = ref<BatterySohStatus | null>(null)
const loading = ref(true)
const error = ref<string | null>(null)

// --- form state (own state rather than a shared composable: the modal is the only
// place SoH is edited, so the CRUD lives where the data is shown) ---
const showForm = ref(false)
const editingEntry = ref<BatterySohEntry | null>(null)
const formPercent = ref<number | null>(null)
const formDate = ref(new Date().toISOString().split('T')[0])
const saving = ref(false)

const load = async () => {
  loading.value = true
  error.value = null
  try {
    const [entries, detectionStatus] = await Promise.all([
      carService.getSohHistory(props.car.id),
      carService.getSohStatus(props.car.id),
    ])
    history.value = entries
    status.value = detectionStatus
  } catch {
    error.value = t('soh.load_error')
  } finally {
    loading.value = false
  }
}

onMounted(load)

const latest = computed<BatterySohEntry | null>(() => history.value[0] ?? null)

const effectiveKwh = computed<number | null>(() =>
  props.car.effectiveBatteryCapacityKwh ?? props.car.customNetCapacityKwh ?? null,
)

const nominalKwh = computed<number | null>(() =>
  nominalCapacityKwh(effectiveKwh.value, props.car.batteryDegradationPercent),
)

const numberLocale = computed(() => (locale.value === 'en' ? 'en-GB' : 'de-DE'))

const formatKwh = (kwh: number) =>
  kwh.toLocaleString(numberLocale.value, { maximumFractionDigits: 1 })

const formatDate = (iso: string) =>
  new Date(iso).toLocaleDateString(numberLocale.value, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  })

/**
 * Axis ticks: month + full year, the exact day lives in the tooltip. The year stays
 * four-digit because "Sept. 20" reads as a day of the month, not as 2020.
 */
const formatAxisDate = (ms: number) =>
  new Date(ms).toLocaleDateString(numberLocale.value, { year: 'numeric', month: 'short' })

/** Axis ticks: plain scale marks, no approximation marker. */
const formatPercent = (value: number) =>
  `${value.toLocaleString(numberLocale.value, { maximumFractionDigits: 0 })} %`

/**
 * A measured SoH value: rounded to whole percent and marked as approximate. Every source
 * - BMS reading, charge-log estimate, workshop value - is an approximation, so decimals
 * would suggest a precision none of them has.
 */
const formatSoh = (value: number) => `~${formatPercent(Math.round(value))}`

/** Estimates carry a count, so they need the plural form; the other sources are fixed strings. */
const sourceLabel = (entry: BatterySohEntry): string => {
  const key = sohTrustKey(entry.source)
  if (key !== 'trust_estimate') return t(`soh.${key}`)
  const count = entry.sampleSize ?? 0
  return t('soh.trust_estimate', { count }, count)
}

const trustLabel = computed<string | null>(() => (latest.value ? sourceLabel(latest.value) : null))

const emptyStateKey = computed(() => (status.value ? sohEmptyStateKey(status.value) : null))

const emptyStateText = computed<string | null>(() => {
  if (!status.value || !emptyStateKey.value) return null
  const s = status.value
  switch (emptyStateKey.value) {
    case 'no_capacity':
      return t('soh.empty_no_capacity')
    case 'no_charges':
      return t('soh.empty_no_charges', { required: s.requiredSocHubPercent })
    case 'hub_too_small':
      return t('soh.empty_hub_too_small', {
        required: s.requiredSocHubPercent,
        largest: Math.round(s.largestSocHubPercent ?? 0),
      })
    default:
      return t('soh.empty_pending')
  }
})

// --- chart ---
// Ascending for the chart, while the list below stays newest-first.
const chronological = computed(() => [...history.value].reverse())

/** Point shape encodes the source, so it stays distinguishable without relying on colour. */
const POINT_STYLES: Record<BatterySohEntry['source'], string> = {
  VEHICLE_BMS: 'circle',
  CHARGE_LOG: 'triangle',
  MANUAL: 'rect',
  UNKNOWN: 'crossRot',
}

const chartData = computed((): ChartData<'line'> => ({
  datasets: [
    {
      // {x,y} on a linear time axis instead of category labels: measurements are years
      // apart at the start and weeks apart later. Equidistant categories would compress
      // six years into the same width as one month and invent a trend that is not there.
      data: chronological.value.map((e) => ({
        x: new Date(e.recordedAt).getTime(),
        y: e.sohPercent,
      })),
      borderColor: 'rgba(99, 102, 241, 0.45)',
      backgroundColor: '#6366f1',
      pointBackgroundColor: '#6366f1',
      pointStyle: chronological.value.map((e) => POINT_STYLES[e.source]),
      pointRadius: 6,
      pointHoverRadius: 8,
      borderWidth: 1.5,
      tension: 0,
    },
  ],
}))

const chartOptions = computed((): ChartOptions<'line'> => {
  const bounds = sohAxisBounds(chronological.value.map((e) => e.sohPercent))
  return {
    responsive: true,
    maintainAspectRatio: false,
    // Without the padding the newest point sits on the frame and gets clipped.
    // With bounds: 'data' the outermost markers sit exactly on the axis ends, so they need
    // room on both sides to not be cut in half.
    layout: { padding: { left: 8, right: 10, top: 4 } },
    plugins: {
      legend: { display: false },
      // chartjs-plugin-datalabels is registered globally by CostHistoryCard, and Chart.js
      // registration is process-wide - so it would print a number onto every point here
      // too. With measurements this dense the labels overlap and drown the line.
      datalabels: { display: false },
      tooltip: {
        callbacks: {
          title: (items) => formatDate(chronological.value[items[0].dataIndex].recordedAt),
          label: (ctx) => {
            const entry = chronological.value[ctx.dataIndex]
            return `${formatSoh(entry.sohPercent)} - ${sourceLabel(entry)}`
          },
        },
      },
    },
    scales: {
      y: {
        min: bounds.min,
        max: bounds.max,
        ticks: { callback: (v) => formatPercent(Number(v)) },
      },
      x: {
        type: 'linear',
        // Chart.js widens a linear axis to the next round tick by default, which invents
        // years before the first and after the last measurement. The axis has to end where
        // the data ends.
        bounds: 'data',
        grid: { display: false },
        ticks: {
          maxTicksLimit: 5,
          autoSkip: true,
          maxRotation: 0,
          callback: (v) => formatAxisDate(Number(v)),
        },
      },
    },
  }
})

/** A trend from two points would be extrapolation, not observation. */
const showsTrend = computed(() => history.value.length >= 4)

// --- CRUD ---
const openAddForm = () => {
  editingEntry.value = null
  formPercent.value = null
  formDate.value = new Date().toISOString().split('T')[0]
  showForm.value = true
}

const openEditForm = (entry: BatterySohEntry) => {
  editingEntry.value = entry
  formPercent.value = entry.sohPercent
  formDate.value = entry.recordedAt
  showForm.value = true
}

const cancelForm = () => {
  showForm.value = false
  editingEntry.value = null
}

const submitForm = async () => {
  if (formPercent.value == null || !formDate.value) return
  saving.value = true
  error.value = null
  try {
    const payload = { sohPercent: formPercent.value, recordedAt: formDate.value }
    if (editingEntry.value) {
      await carService.updateSohMeasurement(props.car.id, editingEntry.value.id, payload)
    } else {
      await carService.addSohMeasurement(props.car.id, payload)
    }
    showForm.value = false
    editingEntry.value = null
    await load()
    emit('changed')
  } catch {
    error.value = t('soh.save_error')
  } finally {
    saving.value = false
  }
}

const deleteEntry = async (entry: BatterySohEntry) => {
  if (!window.confirm(t('soh.delete_confirm'))) return
  error.value = null
  try {
    await carService.deleteSohMeasurement(props.car.id, entry.id)
    await load()
    emit('changed')
  } catch {
    error.value = t('soh.delete_error')
  }
}

const previewKwh = computed<number | null>(() => {
  if (formPercent.value == null || nominalKwh.value == null) return null
  return (nominalKwh.value * formPercent.value) / 100
})
</script>

<template>
  <BottomSheet
    ref="sheet"
    :label="t('soh.title')"
    panel-class="sm:max-w-xl"
    testid="soh-modal"
    @close="emit('close')"
  >
    <!-- Header: stays put while the body scrolls -->
    <div class="flex items-start justify-between gap-3 px-4 pt-4 pb-3 border-b border-gray-200 dark:border-gray-700">
      <h2 class="text-base font-semibold text-gray-900 dark:text-gray-100">{{ t('soh.title') }}</h2>
      <button
        type="button"
        class="p-2 -m-2 text-gray-400 hover:text-gray-600 dark:hover:text-gray-200"
        :aria-label="t('common.close')"
        @click="sheet?.requestClose()"
      >
        <XMarkIcon class="w-5 h-5" />
      </button>
    </div>

    <div class="flex-1 overflow-y-auto px-4 py-4 space-y-6">
      <p v-if="loading" class="text-sm text-gray-500 dark:text-gray-400">{{ t('common.loading') }}</p>

      <p v-if="error" role="alert" class="text-sm text-red-600 dark:text-red-400">{{ error }}</p>

      <template v-if="!loading">
        <!-- Current value. The kWh figure is what the percentage actually means. -->
        <section v-if="latest">
          <div class="flex items-baseline gap-2">
            <span class="text-4xl font-bold text-gray-900 dark:text-gray-100">{{ formatSoh(latest.sohPercent) }}</span>
            <span class="text-sm text-gray-500 dark:text-gray-400">{{ t('soh.of_original') }}</span>
          </div>
          <p v-if="effectiveKwh && nominalKwh" class="mt-1 text-sm text-gray-600 dark:text-gray-300">
            {{ t('soh.capacity_line', { effective: formatKwh(effectiveKwh), nominal: formatKwh(nominalKwh) }) }}
          </p>
          <p class="mt-2 inline-flex items-center gap-1.5 rounded-full bg-gray-100 dark:bg-gray-700 px-2.5 py-1 text-xs text-gray-700 dark:text-gray-200">
            <InformationCircleIcon class="w-3.5 h-3.5 flex-shrink-0" />
            {{ trustLabel }}
          </p>
        </section>

        <!-- Nothing measured yet: name the missing precondition instead of showing a void. -->
        <section v-else class="rounded-sm bg-gray-50 dark:bg-gray-700/50 p-4">
          <h3 class="text-sm font-medium text-gray-800 dark:text-gray-100">{{ t('soh.empty_title') }}</h3>
          <p class="mt-1 text-sm text-gray-600 dark:text-gray-300">{{ emptyStateText }}</p>
        </section>

        <!-- History -->
        <section v-if="history.length > 0">
          <h3 class="text-sm font-medium text-gray-800 dark:text-gray-100 mb-2">{{ t('soh.chart_title') }}</h3>
          <div class="h-48">
            <Line :data="chartData" :options="chartOptions" />
          </div>
          <p v-if="!showsTrend" class="mt-2 text-xs text-gray-400 dark:text-gray-500">
            {{ t('soh.chart_too_few_points') }}
          </p>
        </section>

        <!-- Method. Collapsed by default - most users leave after the number above. -->
        <details class="group rounded-sm border border-gray-200 dark:border-gray-700">
          <summary class="cursor-pointer list-none px-3 py-2.5 text-sm font-medium text-gray-800 dark:text-gray-100 flex items-center justify-between">
            {{ t('soh.method_title') }}
            <span class="text-gray-400 group-open:rotate-180 transition-transform" aria-hidden="true">&#9662;</span>
          </summary>
          <div class="px-3 pb-3 space-y-2 text-sm text-gray-600 dark:text-gray-300">
            <p>{{ t('soh.method_bms') }}</p>
            <p>{{ t('soh.method_estimate', { required: status?.requiredSocHubPercent ?? 75 }) }}</p>
            <p>{{ t('soh.method_median') }}</p>
          </div>
        </details>

        <p class="text-xs text-gray-500 dark:text-gray-400">{{ t('soh.disclaimer') }}</p>

        <!-- Own measurement -->
        <section class="border-t border-gray-200 dark:border-gray-700 pt-4">
          <div class="flex items-center justify-between mb-1">
            <h3 class="text-sm font-medium text-gray-800 dark:text-gray-100">{{ t('soh.entries_title') }}</h3>
            <button
              v-if="!showForm"
              type="button"
              class="inline-flex items-center gap-1 text-sm text-indigo-600 dark:text-indigo-400 hover:underline py-1"
              @click="openAddForm"
            >
              <PlusIcon class="w-4 h-4" />
              {{ t('soh.add_btn') }}
            </button>
          </div>
          <p class="text-xs text-gray-500 dark:text-gray-400 mb-3">{{ t('soh.own_hint') }}</p>

          <div v-if="showForm" class="rounded-sm bg-gray-50 dark:bg-gray-700 p-3 space-y-3 mb-3">
            <div class="grid grid-cols-2 gap-3">
              <div>
                <label for="soh-percent" class="block text-xs font-medium text-gray-600 dark:text-gray-300 mb-1">
                  {{ t('soh.label_percent') }}
                </label>
                <input
                  id="soh-percent"
                  v-model.number="formPercent"
                  type="number"
                  step="0.1"
                  min="50"
                  max="100"
                  class="w-full rounded-sm border border-gray-300 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-100 px-2 py-2 text-sm"
                />
              </div>
              <div>
                <label for="soh-date" class="block text-xs font-medium text-gray-600 dark:text-gray-300 mb-1">
                  {{ t('soh.label_date') }}
                </label>
                <input
                  id="soh-date"
                  v-model="formDate"
                  type="date"
                  class="w-full rounded-sm border border-gray-300 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-100 px-2 py-2 text-sm"
                />
              </div>
            </div>
            <p v-if="previewKwh != null" class="text-xs text-amber-600 dark:text-amber-400">
              {{ t('soh.preview_capacity', { kwh: formatKwh(previewKwh) }) }}
            </p>
            <div class="flex gap-2">
              <button
                type="button"
                class="rounded-sm bg-indigo-600 px-3 py-2 text-sm font-medium text-white disabled:opacity-50"
                :disabled="saving || formPercent == null"
                @click="submitForm"
              >
                {{ t('soh.save_btn') }}
              </button>
              <button
                type="button"
                class="rounded-sm px-3 py-2 text-sm text-gray-600 dark:text-gray-300"
                @click="cancelForm"
              >
                {{ t('common.cancel') }}
              </button>
            </div>
          </div>

          <ul v-if="history.length > 0" class="divide-y divide-gray-100 dark:divide-gray-700">
            <li v-for="entry in history" :key="entry.id" class="flex items-start justify-between gap-2 py-2">
              <div class="min-w-0">
                <div class="flex items-baseline gap-2">
                  <span class="font-semibold text-gray-800 dark:text-gray-100">{{ formatSoh(entry.sohPercent) }}</span>
                  <span class="text-xs text-gray-500 dark:text-gray-400">{{ formatDate(entry.recordedAt) }}</span>
                </div>
                <!-- Herkunft und Ladehub auf eigener Zeile: zusammen zu lang fuer eine
                     Zeile auf Mobile, und sie erklaeren gemeinsam, worauf der Wert beruht. -->
                <p class="mt-0.5 text-xs text-gray-400 dark:text-gray-500">
                  {{ sourceLabel(entry) }}
                  <template v-if="entry.socHubPercent != null">
                    &middot; {{ t('soh.hub_detail', { hub: Math.round(entry.socHubPercent) }) }}
                  </template>
                </p>
              </div>
              <div class="flex flex-shrink-0 gap-3">
                <button type="button" class="text-xs text-indigo-600 dark:text-indigo-400 py-1" @click="openEditForm(entry)">
                  {{ t('soh.correct_btn') }}
                </button>
                <button type="button" class="text-xs text-red-500 py-1" @click="deleteEntry(entry)">
                  {{ t('soh.delete_btn') }}
                </button>
              </div>
            </li>
          </ul>
        </section>
      </template>
    </div>
  </BottomSheet>
</template>
