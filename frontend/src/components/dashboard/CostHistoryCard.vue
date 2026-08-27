<script setup lang="ts">
import { computed, watch, ref } from 'vue'
import { Chart as ChartJS, BarElement, CategoryScale, LinearScale, LineElement, PointElement, Tooltip, type ChartData, type ChartOptions } from 'chart.js'
import { Chart } from 'vue-chartjs'
import ChartDataLabels from 'chartjs-plugin-datalabels'
import { useFixedCostCalc, type FixedCostMode } from '../../composables/useFixedCostCalc'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import { useThemeStore } from '../../stores/theme'
import { storeToRefs } from 'pinia'
import { useI18n } from 'vue-i18n'
import api from '../../api/axios'

ChartJS.register(BarElement, CategoryScale, LinearScale, LineElement, PointElement, Tooltip, ChartDataLabels)

const props = defineProps<{ carId: string | null }>()

const { t, locale } = useI18n()
const { formatCurrency } = useLocaleFormat()
const { isDark } = storeToRefs(useThemeStore())

const LS_MODE = 'cost_history_mode'
const fixedCostMode = ref<FixedCostMode>((localStorage.getItem(LS_MODE) as FixedCostMode) ?? 'pro_rata')

function setMode(m: FixedCostMode) {
  fixedCostMode.value = m
  localStorage.setItem(LS_MODE, m)
}

const { load, last12Months, last12MonthsPerCategory } = useFixedCostCalc(() => props.carId, () => fixedCostMode.value)

const FIXED_PALETTE = ['#f59e0b', '#f97316', '#a78bfa', '#34d399', '#fb7185', '#06b6d4', '#84cc16', '#e879f9']
const monthlyEnergyCosts = ref<Record<string, number>>({})

async function loadAll() {
  const id = props.carId
  if (!id) return
  await Promise.all([
    load(),
    api.get(`/logs/statistics?carId=${id}&timeRange=LAST_12_MONTHS&groupBy=MONTH`)
      .then(r => {
        const map: Record<string, number> = {}
        for (const pt of r.data.chargesOverTime ?? []) {
          const d = new Date(pt.timestamp)
          const key = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
          map[key] = (map[key] ?? 0) + (pt.costEur ?? 0)
        }
        monthlyEnergyCosts.value = map
      })
      .catch(() => {}),
  ])
}

watch(() => props.carId, (id) => { if (id) loadAll() }, { immediate: true })

const energyData = computed(() =>
  last12Months.value.map(m => {
    const key = `${m.year}-${String(m.month).padStart(2, '0')}`
    return Math.round((monthlyEnergyCosts.value[key] ?? 0) * 100) / 100
  })
)

const totalData = computed(() =>
  last12Months.value.map((m, i) =>
    Math.round((energyData.value[i] + m.amount) * 100) / 100
  )
)



/** Lowest point of the stack: sum of all negative (income) contributions per month. */
const negativeTotals = computed(() =>
  last12Months.value.map((_, i) =>
    last12MonthsPerCategory.value.reduce(
      (sum, { data }) => sum + Math.min(0, data[i] ?? 0),
      Math.min(0, energyData.value[i] ?? 0),
    )
  )
)

const labels = computed(() =>
  last12Months.value.map(m => {
    const d = new Date(m.year, m.month - 1, 1)
    return d.toLocaleDateString(locale.value, { month: 'short' }).replace('.', '').slice(0, 1).toUpperCase()
  })
)

const chartData = computed((): ChartData<'bar' | 'line'> => ({
  labels: labels.value,
  datasets: [
    {
      type: 'bar' as const,
      label: t('cost_history.energy'),
      data: energyData.value,
      backgroundColor: isDark.value ? '#818cf8' : '#6366f1',
      borderColor: 'transparent',
      borderRadius: 0,
      stack: 'costs',
      order: 1,
    },
    ...last12MonthsPerCategory.value.map(({ category, data }, idx) => ({
      type: 'bar' as const,
      label: t(`fixed_costs.category_${category}`),
      data,
      backgroundColor: FIXED_PALETTE[idx % FIXED_PALETTE.length],
      borderColor: 'transparent',
      borderRadius: 0,
      stack: 'costs',
      order: 1,
    })),
    {
      type: 'line' as const,
      label: '_total',
      data: totalData.value,
      borderColor: 'transparent',
      backgroundColor: 'transparent',
      pointRadius: 0,
      order: 2,
    },
  ],
}))

const chartOptions = computed((): ChartOptions<'bar'> => ({
  responsive: true,
  maintainAspectRatio: false,
  clip: false,
  interaction: { mode: 'index', intersect: false },
  plugins: {
    legend: { display: false },
    datalabels: {
      display: (ctx: any) => {
        const val = ctx.dataset.data[ctx.dataIndex] as number ?? 0
        if (ctx.dataset.label === '_total') return val !== 0
        return ctx.dataset.type === 'bar' && Math.abs(val) >= 10
      },
      color: (ctx: any) => ctx.dataset.label === '_total'
        ? (isDark.value ? '#d1d5db' : '#374151')
        : '#fff',
      anchor: (ctx: any) => ctx.dataset.label === '_total' ? 'end' : 'center',
      align: (ctx: any) => ctx.dataset.label === '_total' ? 'top' : 'center',
      offset: (ctx: any) => ctx.dataset.label === '_total' ? 2 : 0,
      font: (ctx: any) => ({
        size: ctx.dataset.label === '_total' ? 9 : 10,
        weight: 'bold' as const,
      }),
      formatter: (v: number, ctx: any) => ctx.dataset.label === '_total'
        ? `${Math.round(v)}€`
        : `${Math.round(v)}`,
    },
    tooltip: {
      callbacks: {
        label: (ctx: any) => {
          if (ctx.dataset.label === '_total') return
          return ` ${ctx.dataset.label}: ${formatCurrency(ctx.parsed.y)}`
        },
      },
    },
  },
  scales: {
    x: {
      stacked: true,
      grid: { color: isDark.value ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.05)', lineWidth: 1 },
      ticks: { color: isDark.value ? '#9ca3af' : '#6b7280', font: { size: 11 } },
    },
    y: {
      stacked: true,
      min: negativeTotals.value.length ? Math.floor(Math.min(0, ...negativeTotals.value) * 1.1) : 0,
      max: totalData.value.length ? Math.ceil(Math.max(0, ...totalData.value) * 1.1) : undefined,
      grid: { color: isDark.value ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.05)' },
      ticks: {
        color: isDark.value ? '#9ca3af' : '#6b7280',
        callback: (v: any) => v === 0 ? '0 €' : `${Math.round(v / 1000) > 0 ? Math.round(v / 1000) + 'k' : v} €`,
        font: { size: 11 },
        maxTicksLimit: 4,
      },
      position: 'right' as const,
    },
  },
}))

const hasAnyData = computed(() =>
  totalData.value.some(v => v !== 0)
)
</script>

<template>
  <div class="bg-white dark:bg-gray-700 rounded-sm border-2 border-gray-300 dark:border-gray-600 shadow-[2px_2px_0_0_#d1d5db] dark:shadow-[2px_2px_0_0_#374151] p-4">
    <!-- Row 1: title + mode selector -->
    <div class="flex items-center justify-between mb-2">
      <h3 class="text-sm font-semibold text-gray-700 dark:text-gray-200">{{ t('cost_history.title') }}</h3>
      <div class="flex items-center gap-2">
        <span class="text-[11px] text-gray-400 dark:text-gray-500 leading-none select-none cursor-default">{{ t('cost_history.mode_spread_label') }}</span>
        <button
          type="button"
          role="switch"
          :aria-checked="fixedCostMode === 'pro_rata'"
          :aria-label="t('cost_history.mode_spread_label')"
          @click="setMode(fixedCostMode === 'pro_rata' ? 'due_month' : 'pro_rata')"
          class="relative inline-flex h-5 w-9 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-1 focus-visible:ring-offset-white dark:focus-visible:ring-offset-gray-700"
          :class="fixedCostMode === 'pro_rata' ? 'bg-indigo-500' : 'bg-gray-300 dark:bg-gray-600'"
        >
          <span
            aria-hidden="true"
            class="pointer-events-none inline-block h-4 w-4 transform rounded-full bg-white shadow ring-0 transition duration-200 ease-in-out"
            :class="fixedCostMode === 'pro_rata' ? 'translate-x-4' : 'translate-x-0'"
          />
        </button>
      </div>
    </div>
    <!-- Row 2: legend -->
    <div class="flex items-center gap-2.5 flex-wrap mb-3">
      <span class="flex items-center gap-1 text-[11px] text-gray-500 dark:text-gray-400">
        <span class="inline-block w-2.5 h-2.5 rounded-sm bg-indigo-500 dark:bg-indigo-400 opacity-80"></span>
        {{ t('cost_history.energy') }}
      </span>
      <span
        v-for="({ category }, idx) in last12MonthsPerCategory"
        :key="category"
        class="flex items-center gap-1 text-[11px] text-gray-500 dark:text-gray-400"
      >
        <span class="inline-block w-2.5 h-2.5 rounded-sm opacity-90" :style="{ backgroundColor: FIXED_PALETTE[idx % FIXED_PALETTE.length] }"></span>
        {{ t(`fixed_costs.category_${category}`) }}
      </span>
    </div>

    <div v-if="!hasAnyData" class="py-6 text-xs text-gray-400 dark:text-gray-500 text-center">
      {{ t('cost_history.empty') }}
    </div>

    <template v-else>
      <!-- Chart -->
      <div class="h-[220px] md:h-[180px]">
        <Chart type="bar" :data="chartData" :options="chartOptions" />
      </div>

    </template>
  </div>
</template>
