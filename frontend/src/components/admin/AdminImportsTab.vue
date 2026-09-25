<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ArrowPathIcon } from '@heroicons/vue/24/outline'
import { Bar } from 'vue-chartjs'
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  BarElement,
  Tooltip,
  Legend,
  type ChartOptions,
} from 'chart.js'
import api from '../../api/axios'
import {
  HEALTH_LABEL,
  OUTCOME_COLOR,
  OUTCOME_LABEL,
  dailySeries,
  formatAgo,
  formatRate,
  sortGroups,
  sourceLabel,
  type ImportHealth,
  type ImportStats,
} from '../../utils/importStats'

ChartJS.register(CategoryScale, LinearScale, BarElement, Tooltip, Legend)

const PERIODS = [7, 30, 90] as const

const days = ref<number>(30)
const stats = ref<ImportStats | null>(null)
const loading = ref(false)
const error = ref('')

const load = async () => {
  loading.value = true
  error.value = ''
  try {
    const res = await api.get('/admin/stats/imports', { params: { days: days.value } })
    stats.value = res.data
  } catch (e: any) {
    error.value = e.response?.data?.message || 'Import-Protokoll konnte nicht geladen werden.'
  } finally {
    loading.value = false
  }
}

const selectPeriod = (d: number) => {
  if (d === days.value) return
  days.value = d
  load()
}

const groups = computed(() => sortGroups(stats.value?.groups ?? []))

const HEALTH_DOT: Record<ImportHealth, string> = {
  OK: 'bg-green-500',
  WARN: 'bg-amber-400',
  ERROR: 'bg-red-500',
}

const gridColor = 'rgba(255,255,255,0.06)'
const tickColor = '#9ca3af'

const chartData = computed(() => {
  const series = dailySeries(stats.value?.daily ?? [], days.value)
  return {
    labels: series.labels,
    datasets: series.datasets.map((d) => ({
      label: OUTCOME_LABEL[d.outcome],
      data: d.data,
      backgroundColor: OUTCOME_COLOR[d.outcome],
      borderRadius: 1,
    })),
  }
})

const chartOptions: ChartOptions<'bar'> = {
  responsive: true,
  maintainAspectRatio: false,
  plugins: {
    legend: { position: 'bottom', labels: { color: tickColor, boxWidth: 10, font: { size: 11 } } },
  },
  scales: {
    x: { stacked: true, grid: { display: false }, ticks: { color: tickColor, maxRotation: 0, autoSkipPadding: 8 } },
    y: { stacked: true, beginAtZero: true, grid: { color: gridColor }, ticks: { color: tickColor, precision: 0 } },
  },
}

onMounted(load)
</script>

<template>
  <div>
    <div class="flex items-center justify-between gap-2 mb-4 flex-wrap">
      <h2 class="text-lg font-semibold text-white">Importe</h2>
      <div class="flex items-center gap-2">
        <div class="flex bg-gray-900 rounded-sm p-1" role="group" aria-label="Zeitraum">
          <button
            v-for="p in PERIODS"
            :key="p"
            type="button"
            :aria-pressed="days === p"
            @click="selectPeriod(p)"
            :class="[
              'min-h-[44px] px-3 text-sm rounded-sm transition',
              days === p ? 'bg-indigo-600 text-white' : 'text-gray-400 hover:text-gray-200 hover:bg-gray-800',
            ]"
          >
            {{ p }} Tage
          </button>
        </div>
        <button
          type="button"
          @click="load"
          :disabled="loading"
          aria-label="Neu laden"
          class="min-h-[44px] min-w-[44px] flex items-center justify-center rounded-sm border border-gray-700 text-gray-300 hover:bg-gray-800 disabled:opacity-40"
        >
          <ArrowPathIcon class="w-5 h-5" :class="loading ? 'animate-spin' : ''" />
        </button>
      </div>
    </div>

    <p v-if="error" class="text-sm text-red-400 mb-3">{{ error }}</p>

    <template v-if="stats">
      <p v-if="groups.length === 0" class="text-sm text-gray-400 py-8 text-center">Keine Importe im Zeitraum.</p>

      <template v-else>
        <!-- Tagesverlauf nach Ergebnis -->
        <div class="bg-gray-900 rounded-sm p-3 md:p-4 border border-gray-800 mb-4">
          <h3 class="text-sm font-medium text-gray-300 mb-2">Aufrufe je Tag</h3>
          <div class="h-44 md:h-60"><Bar :data="chartData" :options="chartOptions" /></div>
        </div>

        <!-- Karten je Provider und Kanal, gestörte zuerst -->
        <div class="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
          <article
            v-for="g in groups"
            :key="`${g.provider}-${g.channel}`"
            data-testid="import-source-card"
            class="bg-gray-900 rounded-sm p-4 border border-gray-800 min-w-0"
          >
            <header class="flex items-start justify-between gap-2 mb-3">
              <h3 class="font-medium text-white break-words">{{ sourceLabel(g.provider, g.channel) }}</h3>
              <span class="flex items-center gap-1.5 text-sm text-gray-200 shrink-0">
                <span class="w-2.5 h-2.5 rounded-full" :class="HEALTH_DOT[g.health]" aria-hidden="true" />
                {{ HEALTH_LABEL[g.health] }}
              </span>
            </header>

            <dl class="grid grid-cols-2 gap-x-3 gap-y-2 text-sm">
              <div>
                <dt class="text-xs text-gray-500">Aufrufe</dt>
                <dd class="text-gray-100 tabular-nums">{{ g.events }}</dd>
              </div>
              <div>
                <dt class="text-xs text-gray-500">Störungsquote</dt>
                <dd class="text-gray-100 tabular-nums">{{ formatRate(g.errorRate) }}</dd>
              </div>
              <div>
                <dt class="text-xs text-gray-500">Ladungen neu / übersprungen</dt>
                <dd class="text-gray-100 tabular-nums">{{ g.sessionsImported }} / {{ g.sessionsSkipped }}</dd>
              </div>
              <div>
                <dt class="text-xs text-gray-500">Fahrten neu / übersprungen</dt>
                <dd class="text-gray-100 tabular-nums">{{ g.tripsImported }} / {{ g.tripsSkipped }}</dd>
              </div>
              <div>
                <dt class="text-xs text-gray-500">Letzter Erfolg</dt>
                <dd class="text-gray-100">{{ formatAgo(g.lastSuccessAt) }}</dd>
              </div>
              <div>
                <dt class="text-xs text-gray-500">Verbindungen</dt>
                <dd class="text-gray-100" data-testid="import-connections">–</dd>
              </div>
            </dl>

            <div v-if="g.topErrors.length" class="mt-3 pt-3 border-t border-gray-800">
              <h4 class="text-xs text-gray-500 mb-1">Häufigste Fehler</h4>
              <ul class="space-y-1">
                <li v-for="e in g.topErrors" :key="e.error" class="flex gap-2 text-xs">
                  <span class="text-red-300 tabular-nums shrink-0">{{ e.count }}×</span>
                  <span class="text-gray-300 break-words min-w-0">{{ e.error }}</span>
                </li>
              </ul>
            </div>
          </article>
        </div>
      </template>
    </template>

    <p v-else-if="loading" class="text-sm text-gray-400 py-8 text-center">Lade Import-Protokoll...</p>
  </div>
</template>
