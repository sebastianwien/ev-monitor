<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ArrowLeftIcon, ArrowPathIcon } from '@heroicons/vue/24/outline'
import api from '../api/axios'
import LogsPaginationBar from '../components/dashboard/LogsPaginationBar.vue'
import type { PageSize } from '../composables/useLogList'
import { changedCells, runSegment, type RunSegment, type WebhookRow, type SignalKey } from '../utils/webhookDiff'

interface AdminConnection {
  username: string | null
  carId: string | null
  smartcarVehicleId: string
  make: string | null
  model: string | null
  year: number | null
  vin: string | null
}

const connections = ref<AdminConnection[]>([])
const selectedVehicleId = ref('')
const rows = ref<WebhookRow[]>([])
const page = ref(0)
const pageSize = ref<PageSize>(50)
const hasMore = ref(false)
const loading = ref(false)
const error = ref('')

const marks = computed(() => changedCells(rows.value))

const connectionLabel = (c: AdminConnection) =>
  `${c.username ?? 'unbekannt'} - ${c.make ?? '?'} ${c.model ?? ''}${c.year ? ` (${c.year})` : ''}`

const loadConnections = async () => {
  try {
    const res = await api.get('/admin/webhooks/connections')
    connections.value = res.data
    if (!selectedVehicleId.value && connections.value.length > 0) {
      selectedVehicleId.value = connections.value[0].smartcarVehicleId
    }
  } catch (e: any) {
    error.value = e.response?.data?.message || 'Verbindungen konnten nicht geladen werden.'
  }
}

const loadPage = async () => {
  if (!selectedVehicleId.value) return
  loading.value = true
  error.value = ''
  try {
    const res = await api.get('/admin/webhooks', {
      params: { vehicleId: selectedVehicleId.value, page: page.value, size: pageSize.value },
    })
    rows.value = res.data.items
    hasMore.value = res.data.hasMore
  } catch (e: any) {
    error.value = e.response?.data?.message || 'Webhooks konnten nicht geladen werden.'
    rows.value = []
    hasMore.value = false
  } finally {
    loading.value = false
  }
}

watch(selectedVehicleId, () => {
  page.value = 0
  loadPage()
})

const prevPage = () => {
  if (page.value > 0) {
    page.value--
    loadPage()
  }
}
const nextPage = () => {
  if (hasMore.value) {
    page.value++
    loadPage()
  }
}
const changePageSize = (size: PageSize) => {
  pageSize.value = size
  page.value = 0
  loadPage()
}

onMounted(loadConnections)

// ── Anzeige-Helfer: reine Formatierung, keine Wertveraenderung ────────────────
const fmtReceived = (iso: string) => {
  const d = new Date(iso)
  return `${pad(d.getDate())}.${pad(d.getMonth() + 1)} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}
const fmtOem = (millis: number | null) => (millis === null ? '-' : fmtReceived(new Date(millis).toISOString()))
const pad = (n: number) => String(n).padStart(2, '0')

const SIGNALS: Array<{ key: SignalKey; label: string }> = [
  { key: 'odometer', label: 'Odometer' },
  { key: 'energyAdded', label: 'energyAdded' },
  { key: 'soc', label: 'SoC' },
  { key: 'isCharging', label: 'isCharging' },
]

const valueCellClass = (row: WebhookRow, key: SignalKey, i: number) => [
  'px-3 py-1 whitespace-nowrap tabular-nums',
  row[key].status === 'ERROR' ? 'bg-red-900/40 text-red-300' : '',
  marks.value[i]?.[key]?.value ? 'bg-amber-900/50 text-amber-200 font-semibold' : '',
]
const oemCellClass = (key: SignalKey, i: number) => [
  'px-3 py-1 whitespace-nowrap tabular-nums text-gray-500',
  marks.value[i]?.[key]?.oem ? 'bg-amber-900/30 text-amber-300' : '',
]

// ── Ladevorgangs-Spalte: zeigt, wo der ChargingRunDetector einen Lauf sieht ────
const END_REASON_LABELS: Record<string, string> = {
  EXPLICIT_STOP: 'isCharging=false',
  COUNTER_FROZEN: 'Zähler eingefroren',
  COUNTER_DROP: 'Zähler-Reset',
  ODOMETER_MOVED: 'Auto gefahren',
  TIMEOUT: '12h ohne Signal',
}

const SEGMENT_CLASSES: Record<RunSegment, string> = {
  none: '',
  full: 'top-0 bottom-0',
  top: 'top-0 h-1/2',
  bottom: 'top-1/2 bottom-0',
}

const segmentOf = (row: WebhookRow) => runSegment(row.detection)
const endLabel = (row: WebhookRow) =>
  (row.detection?.endReasons ?? []).map((r) => END_REASON_LABELS[r] ?? r).join(' + ')
</script>

<template>
  <div class="min-h-screen bg-gray-950 text-gray-200 px-2 py-4 md:px-6">
    <div class="max-w-full">
      <div class="flex items-center gap-3 mb-4 flex-wrap">
        <router-link to="/admin" class="flex items-center gap-1 text-sm text-gray-400 hover:text-gray-200">
          <ArrowLeftIcon class="w-4 h-4" /> Admin
        </router-link>
        <h1 class="text-xl font-bold text-white">Smartcar Webhook Inspector</h1>
      </div>

      <!-- Fahrzeug-Auswahl + Reload -->
      <div class="flex items-center gap-2 mb-4 flex-wrap">
        <select
          v-model="selectedVehicleId"
          class="bg-gray-900 border border-gray-700 rounded-sm px-3 py-2 text-sm text-gray-200 max-w-full"
        >
          <option value="" disabled>Fahrzeug wählen...</option>
          <option v-for="c in connections" :key="c.smartcarVehicleId" :value="c.smartcarVehicleId">
            {{ connectionLabel(c) }}
          </option>
        </select>
        <button
          @click="loadPage"
          :disabled="loading || !selectedVehicleId"
          class="flex items-center gap-1 px-3 py-2 text-sm rounded-sm border border-gray-700 text-gray-300 hover:bg-gray-800 disabled:opacity-40"
        >
          <ArrowPathIcon class="w-4 h-4" :class="loading ? 'animate-spin' : ''" /> Neu laden
        </button>
      </div>

      <p v-if="error" class="text-sm text-red-400 mb-3">{{ error }}</p>

      <p class="text-xs text-gray-500 mb-3">
        Der grüne Strich zeigt, wo der aktuelle <span class="text-gray-400">ChargingRunDetector</span> einen
        Ladevorgang erkennt - berechnet serverseitig auf genau diesen Rohdaten. Ein Lauf beginnt in der
        unteren Zeile und wächst nach oben.
      </p>

      <!-- Rohdaten-Tabelle: newest first, geaenderte Zellen amber, ERROR-Status rot -->
      <div class="overflow-x-auto border border-gray-800 rounded-sm">
        <table class="text-xs text-left">
          <thead class="bg-gray-900 text-gray-400 sticky top-0">
            <tr>
              <th class="px-3 py-2 whitespace-nowrap">received</th>
              <template v-for="s in SIGNALS" :key="s.key">
                <th class="px-3 py-2 whitespace-nowrap border-l border-gray-800">{{ s.label }}</th>
                <th class="px-3 py-2 whitespace-nowrap text-gray-600">oem updated</th>
              </template>
              <th class="px-3 py-2 whitespace-nowrap border-l border-gray-800">Ladevorgang</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="(row, i) in rows"
              :key="row.id"
              class="border-t border-gray-800/60 hover:bg-gray-900/50"
            >
              <td class="px-3 py-1 whitespace-nowrap tabular-nums text-gray-300">{{ fmtReceived(row.receivedAt) }}</td>
              <template v-for="s in SIGNALS" :key="s.key">
                <td :class="[valueCellClass(row, s.key, i), 'border-l border-gray-800']">
                  <span>{{ row[s.key].value ?? '-' }}</span>
                  <span
                    v-if="row[s.key].status && row[s.key].status !== 'SUCCESS'"
                    class="ml-1 px-1 rounded-sm bg-red-800 text-red-100 text-[10px] align-middle"
                    :title="row[s.key].status ?? ''"
                  >E</span>
                </td>
                <td :class="oemCellClass(s.key, i)">{{ fmtOem(row[s.key].oemUpdatedAt) }}</td>
              </template>
              <!-- Lauf-Linie: newest-first, ein Lauf beginnt unten und waechst nach oben -->
              <td class="relative px-3 py-1 border-l border-gray-800 min-w-[12rem] whitespace-nowrap">
                <span
                  v-if="segmentOf(row) !== 'none'"
                  aria-hidden="true"
                  class="absolute left-3 w-0.5 bg-green-500"
                  :class="SEGMENT_CLASSES[segmentOf(row)]"
                ></span>
                <span
                  v-if="row.detection?.runStart || row.detection?.runEnd"
                  aria-hidden="true"
                  class="absolute left-3 top-1/2 w-2 h-2 -translate-x-[3px] -translate-y-1/2 rounded-full bg-green-500"
                ></span>
                <span class="ml-4 inline-flex gap-2">
                  <span v-if="row.detection?.runStart" class="text-green-400">Start</span>
                  <span v-if="row.detection?.runEnd" class="text-green-300/70">Ende: {{ endLabel(row) }}</span>
                </span>
              </td>
            </tr>
            <tr v-if="!loading && rows.length === 0">
              <td colspan="10" class="px-3 py-6 text-center text-gray-500">Keine Webhooks für dieses Fahrzeug im Log.</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="mt-4">
        <LogsPaginationBar
          :page="page"
          :has-more="hasMore"
          :page-size="pageSize"
          @prev="prevPage"
          @next="nextPage"
          @page-size-change="changePageSize"
        />
      </div>
    </div>
  </div>
</template>
