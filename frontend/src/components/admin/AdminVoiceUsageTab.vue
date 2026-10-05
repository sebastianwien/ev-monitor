<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { Chart as ChartJS, CategoryScale, LinearScale, PointElement, LineElement, BarElement, Title, Tooltip, Legend, Filler } from 'chart.js'
import { Line, Bar } from 'vue-chartjs'
import { getAdminVoiceUsage, type VoiceUsageInsights } from '../../api/voiceUsageService'

ChartJS.register(CategoryScale, LinearScale, PointElement, LineElement, BarElement, Title, Tooltip, Legend, Filler)

const PRESETS = [7, 30, 90, 365] as const
const days = ref<number>(30)
const data = ref<VoiceUsageInsights | null>(null)
const loading = ref(false)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    data.value = await getAdminVoiceUsage(days.value)
  } catch {
    error.value = 'Mistral-Nutzung konnte nicht geladen werden.'
  } finally {
    loading.value = false
  }
}

watch(days, load)
onMounted(load)

const usd = (v: number, digits = 4) => `$${Number(v ?? 0).toFixed(digits)}`
const num = (v: number) => Number(v ?? 0).toLocaleString('de-DE')
const minutes = (seconds: number) => `${(Number(seconds ?? 0) / 60).toFixed(1)} min`
const shortId = (id: string) => id.slice(0, 8)

const tiles = computed(() => {
  const t = data.value?.totals
  if (!t) return []
  const failRate = t.calls ? Math.round((t.failedCalls / t.calls) * 100) : 0
  return [
    { label: 'Aufrufe', value: num(t.calls), sub: `${num(t.successCalls)} ok, ${num(t.failedCalls)} Fehler (${failRate}%)`, color: 'text-indigo-400' },
    { label: 'Kosten Zeitraum', value: usd(t.costUsd), sub: `${usd(t.costPerSuccessUsd)} je Entwurf`, color: 'text-emerald-400' },
    { label: 'Kosten gesamt', value: usd(data.value!.allTimeCostUsd, 2), sub: 'seit Start, eingefrorene Preise', color: 'text-emerald-400' },
    { label: 'Nutzer', value: num(t.distinctUsers), sub: 'mit mindestens einer Aufnahme', color: 'text-sky-400' },
    { label: 'Audio', value: minutes(t.audioSeconds), sub: `${num(t.transcribeTokens)} Transkript-Tokens`, color: 'text-amber-400' },
    { label: 'Extraktion', value: num(t.extractPromptTokens + t.extractCompletionTokens), sub: `${num(t.extractPromptTokens)} in, ${num(t.extractCompletionTokens)} out`, color: 'text-amber-400' },
    { label: 'Latenz', value: `${num(t.avgLatencyMs)} ms`, sub: 'Mittel je Aufnahme', color: 'text-gray-200' },
    { label: 'Qualität', value: t.avgFieldsFilled.toFixed(1), sub: `Felder je Entwurf, ${t.avgUncertain.toFixed(1)} unsicher`, color: 'text-gray-200' },
  ]
})

const labels = computed(() => data.value?.days.map(d => d.date.slice(5)) ?? [])

const callsChart = computed(() => ({
  labels: labels.value,
  datasets: [
    { label: 'Erfolgreich', data: data.value?.days.map(d => d.calls - d.failedCalls) ?? [], backgroundColor: 'rgba(99, 102, 241, 0.8)', stack: 'calls' },
    { label: 'Fehler', data: data.value?.days.map(d => d.failedCalls) ?? [], backgroundColor: 'rgba(248, 113, 113, 0.8)', stack: 'calls' },
  ],
}))

const costChart = computed(() => ({
  labels: labels.value,
  datasets: [{
    label: 'Kosten USD', data: data.value?.days.map(d => d.costUsd) ?? [],
    borderColor: 'rgb(52, 211, 153)', backgroundColor: 'rgba(52, 211, 153, 0.15)', fill: true, tension: 0.3, pointRadius: 2,
  }],
}))

const tokenChart = computed(() => ({
  labels: labels.value,
  datasets: [
    { label: 'Transkript', data: data.value?.days.map(d => d.transcribeTokens) ?? [], backgroundColor: 'rgba(251, 191, 36, 0.8)', stack: 't' },
    { label: 'Extraktion in', data: data.value?.days.map(d => d.extractPromptTokens) ?? [], backgroundColor: 'rgba(245, 158, 11, 0.8)', stack: 't' },
    { label: 'Extraktion out', data: data.value?.days.map(d => d.extractCompletionTokens) ?? [], backgroundColor: 'rgba(217, 119, 6, 0.8)', stack: 't' },
  ],
}))

const latencyChart = computed(() => ({
  labels: labels.value,
  datasets: [{
    label: 'Latenz ms', data: data.value?.days.map(d => d.avgLatencyMs) ?? [],
    borderColor: 'rgb(148, 163, 184)', backgroundColor: 'rgba(148, 163, 184, 0.1)', fill: false, tension: 0.3, pointRadius: 2,
  }],
}))

const baseOptions = {
  responsive: true,
  maintainAspectRatio: false,
  plugins: { legend: { labels: { color: '#9ca3af', boxWidth: 12 } } },
  scales: {
    x: { ticks: { color: '#9ca3af', maxTicksLimit: 12 }, grid: { color: 'rgba(55, 65, 81, 0.5)' } },
    y: { beginAtZero: true, ticks: { color: '#9ca3af' }, grid: { color: 'rgba(55, 65, 81, 0.5)' } },
  },
}
const stackedOptions = { ...baseOptions, scales: { x: { ...baseOptions.scales.x, stacked: true }, y: { ...baseOptions.scales.y, stacked: true } } }
</script>

<template>
  <div>
    <div class="flex items-center justify-between mb-4 gap-3 flex-wrap">
      <h2 class="text-lg font-semibold text-white">Mistral-Nutzung (Sprachlog)</h2>
      <div class="flex gap-1">
        <button
          v-for="p in PRESETS"
          :key="p"
          type="button"
          :class="['px-3 py-1.5 rounded-sm text-xs font-medium transition', days === p ? 'bg-indigo-600 text-white' : 'bg-gray-800 text-gray-300 hover:bg-gray-700']"
          @click="days = p"
        >
          {{ p }} Tage
        </button>
      </div>
    </div>

    <p v-if="error" class="text-red-400 text-sm mb-4">{{ error }}</p>
    <p v-else-if="loading && !data" class="text-gray-400 text-sm">Lädt...</p>

    <div v-else-if="data" class="space-y-6" :class="{ 'opacity-60': loading }">
      <div class="grid grid-cols-2 md:grid-cols-4 gap-3">
        <div v-for="t in tiles" :key="t.label" class="bg-gray-900 rounded-sm px-4 py-3 border border-gray-800">
          <div :class="['text-xl font-bold tabular-nums', t.color]">{{ t.value }}</div>
          <div class="text-xs text-gray-300 mt-1">{{ t.label }}</div>
          <div class="text-xs text-gray-500">{{ t.sub }}</div>
        </div>
      </div>

      <p v-if="!data.days.length" class="text-gray-400 text-sm">Keine Aufnahmen im Zeitraum.</p>

      <template v-else>
        <section class="bg-gray-900 rounded-sm p-4 border border-gray-800">
          <h3 class="text-sm font-semibold text-gray-200 mb-2">Aufrufe je Tag</h3>
          <div class="h-56"><Bar :data="callsChart" :options="stackedOptions" /></div>
        </section>
        <section class="bg-gray-900 rounded-sm p-4 border border-gray-800">
          <h3 class="text-sm font-semibold text-gray-200 mb-2">Kosten je Tag (USD)</h3>
          <div class="h-56"><Line :data="costChart" :options="baseOptions" /></div>
        </section>
        <section class="bg-gray-900 rounded-sm p-4 border border-gray-800">
          <h3 class="text-sm font-semibold text-gray-200 mb-2">Tokens je Tag</h3>
          <div class="h-56"><Bar :data="tokenChart" :options="stackedOptions" /></div>
        </section>
        <section class="bg-gray-900 rounded-sm p-4 border border-gray-800">
          <h3 class="text-sm font-semibold text-gray-200 mb-2">Latenz je Tag (ms, Mittel)</h3>
          <div class="h-56"><Line :data="latencyChart" :options="baseOptions" /></div>
        </section>
      </template>

      <div class="grid md:grid-cols-3 gap-4">
        <section class="bg-gray-900 rounded-sm p-4 border border-gray-800">
          <h3 class="text-sm font-semibold text-gray-200 mb-3">Modelle</h3>
          <p v-if="!data.models.length" class="text-sm text-gray-500">Keine</p>
          <ul v-else class="space-y-2 text-sm">
            <li v-for="m in data.models" :key="m.kind + m.name" class="flex justify-between gap-2">
              <span>
                <span class="font-mono text-gray-200">{{ m.name }}</span>
                <span class="text-xs text-gray-500 ml-1">{{ m.kind === 'transcribe' ? 'Transkript' : 'Extraktion' }}</span>
              </span>
              <span class="text-gray-400 tabular-nums text-right whitespace-nowrap">
                {{ num(m.calls) }} Aufrufe<br>
                <span class="text-xs">{{ m.kind === 'transcribe' ? minutes(m.audioSeconds) + ', ' : '' }}{{ num(m.tokens) }} Tokens</span>
              </span>
            </li>
          </ul>
        </section>

        <section class="bg-gray-900 rounded-sm p-4 border border-gray-800">
          <h3 class="text-sm font-semibold text-gray-200 mb-3">Fehler</h3>
          <p v-if="!data.errors.length" class="text-sm text-gray-500">Keine Fehler im Zeitraum</p>
          <ul v-else class="space-y-2 text-sm">
            <li v-for="e in data.errors" :key="e.code" class="flex justify-between">
              <span class="font-mono text-red-300">{{ e.code }}</span>
              <span class="text-gray-400 tabular-nums">{{ num(e.count) }}</span>
            </li>
          </ul>
        </section>

        <section class="bg-gray-900 rounded-sm p-4 border border-gray-800">
          <h3 class="text-sm font-semibold text-gray-200 mb-3">Top-Nutzer</h3>
          <p v-if="!data.topUsers.length" class="text-sm text-gray-500">Keine</p>
          <ul v-else class="space-y-2 text-sm">
            <li v-for="u in data.topUsers" :key="u.userId" class="flex justify-between gap-2">
              <span class="font-mono text-gray-400" :title="u.userId">{{ shortId(u.userId) }}</span>
              <span class="text-gray-300 tabular-nums whitespace-nowrap">
                {{ num(u.calls) }}<span v-if="u.failedCalls" class="text-red-400"> ({{ u.failedCalls }} Fehler)</span>
                <span class="text-gray-500 ml-2">{{ usd(u.costUsd) }}</span>
              </span>
            </li>
          </ul>
        </section>
      </div>
    </div>
  </div>
</template>
