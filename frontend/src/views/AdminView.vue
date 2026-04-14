<script setup lang="ts">
import { ref, computed, watch, onMounted, type Component } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import api from '../api/axios'
import axios from 'axios'
import {
  BoltIcon,
  HomeIcon,
  ArrowDownTrayIcon,
  PencilSquareIcon,
  CommandLineIcon,
} from '@heroicons/vue/24/outline'
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend,
  Filler,
} from 'chart.js'
import { Line } from 'vue-chartjs'

ChartJS.register(CategoryScale, LinearScale, PointElement, LineElement, Title, Tooltip, Legend, Filler)

const router = useRouter()
const authStore = useAuthStore()

// ── Tabs ──────────────────────────────────────────────────────────────────────
type Tab = 'impersonate' | 'users' | 'growth' | 'activity' | 'traffic'
const activeTab = ref<Tab>('users')

// ── Impersonate ───────────────────────────────────────────────────────────────
const email = ref('')
const internalToken = ref('')
const impersonateError = ref('')
const impersonateLoading = ref(false)

const impersonate = async () => {
  if (!email.value || !internalToken.value) {
    impersonateError.value = 'Email und Internal Token erforderlich.'
    return
  }
  impersonateLoading.value = true
  impersonateError.value = ''
  try {
    const baseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api'
    const response = await axios.post(
      `${baseUrl}/internal/impersonate`,
      { email: email.value },
      { headers: { 'X-Internal-Token': internalToken.value } }
    )
    authStore.setToken(response.data.token)
    sessionStorage.setItem('impersonating', email.value)
    router.push('/dashboard')
  } catch (e: any) {
    impersonateError.value = e.response?.data?.error || 'Fehler beim Impersonieren.'
  } finally {
    impersonateLoading.value = false
  }
}

// ── User Table (Query A) ──────────────────────────────────────────────────────
interface UserRow {
  email: string
  createdAt: string
  username: string
  models: string
  utmSource: string
  referrerSource: string
  evlogCount: number
  dataSources: string
}

const users = ref<UserRow[]>([])
const usersLoading = ref(false)
const usersError = ref('')
const userSearch = ref('')
const usersPage = ref(1)
const PAGE_SIZE = 20

const filteredUsers = computed(() => {
  const q = userSearch.value.toLowerCase()
  if (!q) return users.value
  return users.value.filter(u =>
    u.email?.toLowerCase().includes(q) ||
    u.username?.toLowerCase().includes(q) ||
    u.models?.toLowerCase().includes(q) ||
    u.dataSources?.toLowerCase().includes(q)
  )
})

const totalPages = computed(() => Math.ceil(filteredUsers.value.length / PAGE_SIZE))

const pagedUsers = computed(() => {
  const start = (usersPage.value - 1) * PAGE_SIZE
  return filteredUsers.value.slice(start, start + PAGE_SIZE)
})

watch(userSearch, () => { usersPage.value = 1 })

const loadUsers = async () => {
  usersLoading.value = true
  usersError.value = ''
  try {
    const res = await api.get('/admin/stats/users')
    users.value = res.data
  } catch {
    usersError.value = 'Fehler beim Laden der User-Daten.'
  } finally {
    usersLoading.value = false
  }
}

// ── User Growth Chart (Query B) ───────────────────────────────────────────────
interface GrowthRow {
  day: string
  newUsers: number
  cumulativeUsers: number
}

const growthData = ref<GrowthRow[]>([])
const growthLoading = ref(false)
const growthError = ref('')

const growthChartData = computed(() => ({
  labels: growthData.value.map(r => r.day),
  datasets: [
    {
      label: 'Neue User/Tag',
      data: growthData.value.map(r => r.newUsers),
      borderColor: '#6366f1',
      backgroundColor: 'rgba(99,102,241,0.15)',
      fill: true,
      tension: 0.1,
      yAxisID: 'y',
    },
    {
      label: 'Kumuliert',
      data: growthData.value.map(r => r.cumulativeUsers),
      borderColor: '#10b981',
      backgroundColor: 'transparent',
      fill: false,
      tension: 0.1,
      yAxisID: 'y1',
    },
  ],
}))

const growthChartOptions = {
  responsive: true,
  maintainAspectRatio: false,
  interaction: { mode: 'index' as const, intersect: false },
  plugins: { legend: { position: 'top' as const } },
  scales: {
    y: { type: 'linear' as const, position: 'left' as const, title: { display: true, text: 'Neue User' } },
    y1: { type: 'linear' as const, position: 'right' as const, grid: { drawOnChartArea: false }, title: { display: true, text: 'Kumuliert' } },
  },
}

const loadGrowth = async () => {
  growthLoading.value = true
  growthError.value = ''
  try {
    const res = await api.get('/admin/stats/user-growth')
    growthData.value = res.data
  } catch {
    growthError.value = 'Fehler beim Laden der Wachstumsdaten.'
  } finally {
    growthLoading.value = false
  }
}

// ── Charging Activity Chart (Query C) ────────────────────────────────────────
interface ActivityRow {
  day: string
  chargeCount: number
  kwhTotal: number
  costTotal: number
  dataSources: string
  durationMinutesTotal: number
  kwhAvg: number
  costAvg: number
  durationMinutesAvg: number
}

const activityData = ref<ActivityRow[]>([])
const activityLoading = ref(false)
const activityError = ref('')

const activityFrom = ref('')
const activityTo = ref('')

const setActivityPreset = (days: number | null) => {
  if (days === null) {
    activityFrom.value = ''
    activityTo.value = ''
    return
  }
  const to = new Date()
  const from = new Date()
  from.setDate(from.getDate() - days)
  activityFrom.value = from.toISOString().slice(0, 10)
  activityTo.value = to.toISOString().slice(0, 10)
}

const filteredActivityData = computed(() => {
  return activityData.value.filter(r => {
    if (activityFrom.value && r.day < activityFrom.value) return false
    if (activityTo.value && r.day > activityTo.value) return false
    return true
  })
})

const activityChartData = computed(() => ({
  labels: filteredActivityData.value.map(r => r.day),
  datasets: [
    {
      label: 'Ladevorgange/Tag',
      data: filteredActivityData.value.map(r => r.chargeCount),
      borderColor: '#f59e0b',
      backgroundColor: 'rgba(245,158,11,0.15)',
      fill: true,
      tension: 0.1,
      yAxisID: 'y',
    },
    {
      label: 'kWh gesamt',
      data: filteredActivityData.value.map(r => r.kwhTotal),
      borderColor: '#3b82f6',
      backgroundColor: 'transparent',
      fill: false,
      tension: 0.1,
      yAxisID: 'y1',
    },
  ],
}))

const activityChartOptions = {
  responsive: true,
  maintainAspectRatio: false,
  interaction: { mode: 'index' as const, intersect: false },
  plugins: { legend: { position: 'top' as const } },
  scales: {
    y: { type: 'linear' as const, position: 'left' as const, title: { display: true, text: 'Ladevorgange' } },
    y1: { type: 'linear' as const, position: 'right' as const, grid: { drawOnChartArea: false }, title: { display: true, text: 'kWh' } },
  },
}

const loadActivity = async () => {
  activityLoading.value = true
  activityError.value = ''
  try {
    const res = await api.get('/admin/stats/charging-activity')
    activityData.value = res.data
  } catch {
    activityError.value = 'Fehler beim Laden der Ladeaktivitaten.'
  } finally {
    activityLoading.value = false
  }
}

// ── Traffic & Conversions Chart (Plausible + User Growth) ────────────────────
interface TrafficRow {
  date: string
  visitors: number
  pageviews: number
}

const trafficData = ref<TrafficRow[]>([])
const trafficLoading = ref(false)
const trafficError = ref('')
const trafficPeriod = ref('30d')

const isHourly = computed(() => trafficPeriod.value === 'day')

const trafficLabels = computed(() =>
  trafficData.value.map(r => {
    if (isHourly.value) {
      // "2026-03-27 14:00:00" → "14:00"
      const timePart = r.date.includes(' ') ? r.date.split(' ')[1] : r.date
      return timePart.slice(0, 5)
    }
    return r.date
  })
)

const trafficChartData = computed(() => {
  const growthMap = new Map(growthData.value.map(r => [r.day, r.newUsers]))

  const datasets: any[] = [
    {
      label: isHourly.value ? 'Visitors/Stunde' : 'Visitors/Tag',
      data: trafficData.value.map(r => r.visitors),
      borderColor: '#6366f1',
      backgroundColor: 'rgba(99,102,241,0.12)',
      fill: true,
      tension: 0,
      yAxisID: 'y',
    },
    {
      label: isHourly.value ? 'Pageviews/Stunde' : 'Pageviews/Tag',
      data: trafficData.value.map(r => r.pageviews),
      borderColor: '#a5b4fc',
      backgroundColor: 'transparent',
      fill: false,
      tension: 0,
      borderDash: [4, 4],
      yAxisID: 'y',
    },
  ]

  if (!isHourly.value) {
    datasets.push({
      label: 'Neue Registrierungen',
      data: trafficData.value.map(r => growthMap.get(r.date) ?? 0),
      borderColor: '#10b981',
      backgroundColor: 'transparent',
      fill: false,
      tension: 0,
      yAxisID: 'y1',
    })
  }

  return { labels: trafficLabels.value, datasets }
})

const trafficChartOptions = {
  responsive: true,
  maintainAspectRatio: false,
  interaction: { mode: 'index' as const, intersect: false },
  plugins: { legend: { position: 'top' as const } },
  scales: {
    y: {
      type: 'linear' as const,
      position: 'left' as const,
      title: { display: true, text: 'Visitors / Pageviews' },
    },
    y1: {
      type: 'linear' as const,
      position: 'right' as const,
      grid: { drawOnChartArea: false },
      title: { display: true, text: 'Registrierungen' },
      ticks: { precision: 0 },
    },
  },
}

const loadTraffic = async () => {
  trafficLoading.value = true
  trafficError.value = ''
  try {
    const res = await api.get(`/admin/stats/traffic?period=${trafficPeriod.value}`)
    trafficData.value = res.data
  } catch {
    trafficError.value = 'Fehler beim Laden der Traffic-Daten.'
  } finally {
    trafficLoading.value = false
  }
}

const setTrafficPreset = async (period: string) => {
  trafficPeriod.value = period
  await loadTraffic()
}

// ── Init ──────────────────────────────────────────────────────────────────────
onMounted(() => {
  loadUsers()
  loadGrowth()
  loadActivity()
  loadTraffic()
})

const setTab = (tab: Tab) => {
  activeTab.value = tab
}

function sourceInfo(ds: string): { label: string; icon: Component; classes: string } | null {
  switch (ds.trim()) {
    case 'USER_LOGGED':         return { label: 'Manuell',      icon: PencilSquareIcon,  classes: 'bg-green-900/40 text-green-300 border border-green-700' }
    case 'TESLA_FLEET_IMPORT':  return { label: 'Supercharger', icon: BoltIcon,          classes: 'bg-red-900/40 text-red-300 border border-red-700' }
    case 'TESLA_LIVE':          return { label: 'Tesla Live',   icon: BoltIcon,          classes: 'bg-red-900/40 text-red-300 border border-red-700' }
    case 'TESLA_IMPORT':        return { label: 'Tesla',        icon: ArrowDownTrayIcon, classes: 'bg-red-900/40 text-red-300 border border-red-700' }
    case 'TESLA_MANUAL_IMPORT': return { label: 'Tesla',        icon: ArrowDownTrayIcon, classes: 'bg-red-900/40 text-red-300 border border-red-700' }
    case 'SPRITMONITOR_IMPORT': return { label: 'SpritMonitor', icon: ArrowDownTrayIcon, classes: 'bg-purple-900/40 text-purple-300 border border-purple-700' }
    case 'WALLBOX_OCPP':
    case 'WALLBOX_GOE':         return { label: 'Wallbox',      icon: HomeIcon,          classes: 'bg-blue-900/40 text-blue-300 border border-blue-700' }
    case 'SMARTCAR_LIVE':       return { label: 'AutoSync',     icon: BoltIcon,          classes: 'bg-indigo-900/40 text-indigo-300 border border-indigo-700' }
    case 'PUBLIC_API':          return { label: 'API',          icon: CommandLineIcon,   classes: 'bg-amber-900/40 text-amber-300 border border-amber-700' }
    default:                    return null
  }
}

const formatDateTime = (iso: string | null) => {
  if (!iso) return '-'
  // iso from Postgres: "2025-03-01 14:23:11.123456" or "2025-03-01T14:23:11"
  const normalized = iso.replace(' ', 'T').slice(0, 16)
  return normalized.replace('T', ' ')
}

// ── Resizable columns ─────────────────────────────────────────────────────────
const colWidths = ref([220, 130, 155, 110, 60, 160, 110, 140])
const tableWidth = computed(() => colWidths.value.reduce((a, b) => a + b, 0) + 'px')

let resizeState: { colIndex: number; startX: number; startWidth: number } | null = null

const startResize = (colIndex: number, e: MouseEvent) => {
  e.preventDefault()
  resizeState = { colIndex, startX: e.clientX, startWidth: colWidths.value[colIndex] }
  window.addEventListener('mousemove', onResizeMove)
  window.addEventListener('mouseup', onResizeUp)
}

const onResizeMove = (e: MouseEvent) => {
  if (!resizeState) return
  const delta = e.clientX - resizeState.startX
  colWidths.value[resizeState.colIndex] = Math.max(60, resizeState.startWidth + delta)
}

const onResizeUp = () => {
  resizeState = null
  window.removeEventListener('mousemove', onResizeMove)
  window.removeEventListener('mouseup', onResizeUp)
}
</script>

<template>
  <div class="p-4 sm:p-8 text-gray-100">
    <div class="max-w-7xl mx-auto bg-gray-900/80 backdrop-blur-sm rounded-2xl p-4 sm:p-6 border border-white/10">
      <!-- Header -->
      <div class="mb-6">
        <h1 class="text-2xl font-bold text-white">Admin Dashboard</h1>
        <p class="text-sm text-gray-400 mt-1">Eingeloggt als {{ authStore.user?.sub }}</p>
      </div>

      <!-- Tabs -->
      <div class="flex gap-1 mb-6 bg-gray-900 rounded-xl p-1 w-fit">
        <button
          v-for="tab in ([
            { key: 'users', label: 'User' },
            { key: 'growth', label: 'User-Wachstum' },
            { key: 'activity', label: 'Ladeaktivitat' },
            { key: 'traffic', label: 'Traffic' },
            { key: 'impersonate', label: 'Impersonieren' },
          ] as { key: Tab; label: string }[])"
          :key="tab.key"
          @click="setTab(tab.key)"
          :class="[
            'px-4 py-2 rounded-lg text-sm font-medium transition',
            activeTab === tab.key
              ? 'bg-indigo-600 text-white'
              : 'text-gray-400 hover:text-gray-200 hover:bg-gray-800'
          ]"
        >
          {{ tab.label }}
        </button>
      </div>

      <!-- Tab: User Table -->
      <div v-if="activeTab === 'users'">
        <div class="flex items-center justify-between mb-4 gap-3 flex-wrap">
          <h2 class="text-lg font-semibold text-white">
            User
            <span class="text-sm font-normal text-gray-400 ml-2">({{ filteredUsers.length }} / {{ users.length }})</span>
          </h2>
          <input
            v-model="userSearch"
            type="text"
            placeholder="Suchen..."
            class="px-3 py-1.5 rounded-lg bg-gray-800 border border-gray-700 text-sm text-gray-200 placeholder-gray-500 focus:outline-none focus:border-indigo-500 w-56"
          />
        </div>

        <div v-if="usersLoading" class="text-gray-400 text-sm py-8 text-center">Lade...</div>
        <div v-else-if="usersError" class="text-red-400 text-sm py-4">{{ usersError }}</div>
        <div v-else class="overflow-x-auto rounded-xl border border-gray-800">
          <table class="text-sm text-left" :style="{ tableLayout: 'fixed', width: tableWidth }">
            <colgroup>
              <col v-for="(w, i) in colWidths" :key="i" :style="{ width: w + 'px', minWidth: '60px' }" />
            </colgroup>
            <thead class="bg-gray-900 text-gray-400 uppercase text-xs">
              <tr>
                <th
                  v-for="(label, i) in ['Email', 'Username', 'Erstellt', 'Autos', 'Logs', 'Quellen', 'UTM', 'Referrer']"
                  :key="label"
                  class="px-4 py-3 whitespace-nowrap select-none relative"
                  :class="{ 'text-right': label === 'Logs' }"
                >
                  {{ label }}
                  <div
                    v-if="i < 7"
                    @mousedown="startResize(i, $event)"
                    class="absolute top-0 right-0 h-full w-1.5 cursor-col-resize hover:bg-indigo-500/50 transition-colors"
                  />
                </th>
              </tr>
            </thead>
            <tbody class="divide-y divide-gray-800">
              <tr
                v-for="u in pagedUsers"
                :key="u.email"
                class="hover:bg-gray-800/50 transition"
              >
                <td class="px-4 py-2.5 text-gray-200 overflow-hidden text-ellipsis whitespace-nowrap" :title="u.email">{{ u.email }}</td>
                <td class="px-4 py-2.5 text-gray-300 overflow-hidden text-ellipsis whitespace-nowrap" :title="u.username">{{ u.username || '-' }}</td>
                <td class="px-4 py-2.5 text-gray-400 overflow-hidden text-ellipsis whitespace-nowrap font-mono text-xs">{{ formatDateTime(u.createdAt) }}</td>
                <td class="px-4 py-2.5 text-gray-300 overflow-hidden text-ellipsis whitespace-nowrap" :title="u.models">{{ u.models || '-' }}</td>
                <td class="px-4 py-2.5 text-right font-mono text-gray-200">{{ u.evlogCount }}</td>
                <td class="px-4 py-2.5 overflow-hidden">
                  <div class="flex flex-wrap gap-1">
                    <template v-if="u.dataSources">
                      <span
                        v-for="ds in u.dataSources.split(', ')"
                        :key="ds"
                        :class="['inline-flex items-center gap-0.5 px-1.5 py-0.5 rounded-full text-xs whitespace-nowrap', sourceInfo(ds) ? sourceInfo(ds)!.classes : 'bg-gray-700 text-gray-300 border border-gray-600']"
                      >
                        <component v-if="sourceInfo(ds)" :is="sourceInfo(ds)!.icon" class="w-3 h-3 shrink-0" />
                        {{ sourceInfo(ds)?.label ?? ds }}
                      </span>
                    </template>
                    <span v-else class="text-gray-600">-</span>
                  </div>
                </td>
                <td class="px-4 py-2.5 text-gray-500 overflow-hidden text-ellipsis whitespace-nowrap" :title="u.utmSource">{{ u.utmSource || '-' }}</td>
                <td class="px-4 py-2.5 text-gray-500 overflow-hidden text-ellipsis whitespace-nowrap" :title="u.referrerSource">{{ u.referrerSource || '-' }}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- Pagination -->
        <div v-if="totalPages > 1" class="flex items-center justify-between mt-3">
          <span class="text-xs text-gray-500">
            Seite {{ usersPage }} / {{ totalPages }}
          </span>
          <div class="flex gap-1">
            <button
              @click="usersPage = 1"
              :disabled="usersPage === 1"
              class="px-2 py-1 rounded text-xs bg-gray-800 text-gray-400 hover:bg-gray-700 disabled:opacity-30 disabled:cursor-default"
            >«</button>
            <button
              @click="usersPage--"
              :disabled="usersPage === 1"
              class="px-2.5 py-1 rounded text-xs bg-gray-800 text-gray-400 hover:bg-gray-700 disabled:opacity-30 disabled:cursor-default"
            >‹</button>
            <button
              v-for="p in totalPages"
              :key="p"
              v-show="Math.abs(p - usersPage) <= 2"
              @click="usersPage = p"
              :class="['px-2.5 py-1 rounded text-xs transition', p === usersPage ? 'bg-indigo-600 text-white' : 'bg-gray-800 text-gray-400 hover:bg-gray-700']"
            >{{ p }}</button>
            <button
              @click="usersPage++"
              :disabled="usersPage === totalPages"
              class="px-2.5 py-1 rounded text-xs bg-gray-800 text-gray-400 hover:bg-gray-700 disabled:opacity-30 disabled:cursor-default"
            >›</button>
            <button
              @click="usersPage = totalPages"
              :disabled="usersPage === totalPages"
              class="px-2 py-1 rounded text-xs bg-gray-800 text-gray-400 hover:bg-gray-700 disabled:opacity-30 disabled:cursor-default"
            >»</button>
          </div>
        </div>
      </div>

      <!-- Tab: User Growth Chart -->
      <div v-else-if="activeTab === 'growth'">
        <h2 class="text-lg font-semibold text-white mb-4">User-Wachstum</h2>
        <div v-if="growthLoading" class="text-gray-400 text-sm py-8 text-center">Lade...</div>
        <div v-else-if="growthError" class="text-red-400 text-sm py-4">{{ growthError }}</div>
        <div v-else class="bg-gray-900 rounded-xl p-4 border border-gray-800">
          <div class="h-96">
            <Line :data="growthChartData" :options="growthChartOptions" />
          </div>
        </div>
        <!-- Summary -->
        <div class="mt-4 flex gap-4 flex-wrap">
          <div class="bg-gray-900 rounded-xl px-5 py-4 border border-gray-800 flex-1 min-w-40">
            <div class="text-2xl font-bold text-indigo-400">{{ growthData.length > 0 ? growthData[growthData.length - 1].cumulativeUsers : 0 }}</div>
            <div class="text-xs text-gray-400 mt-1">User gesamt</div>
          </div>
          <div class="bg-gray-900 rounded-xl px-5 py-4 border border-gray-800 flex-1 min-w-40">
            <div class="text-2xl font-bold text-emerald-400">{{ growthData.reduce((s, r) => s + r.newUsers, 0) }}</div>
            <div class="text-xs text-gray-400 mt-1">Aktivierungstage total</div>
          </div>
        </div>
      </div>

      <!-- Tab: Charging Activity Chart -->
      <div v-else-if="activeTab === 'activity'">
        <div class="flex items-center justify-between mb-4 gap-3 flex-wrap">
          <h2 class="text-lg font-semibold text-white">Ladeaktivitat</h2>
          <!-- Filter -->
          <div class="flex items-center gap-2 flex-wrap">
            <div class="flex gap-1">
              <button
                v-for="preset in [{ label: '7T', days: 7 }, { label: '30T', days: 30 }, { label: '90T', days: 90 }, { label: 'Alles', days: null }]"
                :key="preset.label"
                @click="setActivityPreset(preset.days)"
                class="px-2.5 py-1 rounded-lg text-xs font-medium transition bg-gray-800 text-gray-400 hover:bg-gray-700 hover:text-gray-200"
              >
                {{ preset.label }}
              </button>
            </div>
            <input
              v-model="activityFrom"
              type="date"
              class="px-2 py-1 rounded-lg bg-gray-800 border border-gray-700 text-sm text-gray-200 focus:outline-none focus:border-indigo-500"
            />
            <span class="text-gray-500 text-sm">-</span>
            <input
              v-model="activityTo"
              type="date"
              class="px-2 py-1 rounded-lg bg-gray-800 border border-gray-700 text-sm text-gray-200 focus:outline-none focus:border-indigo-500"
            />
          </div>
        </div>
        <div v-if="activityLoading" class="text-gray-400 text-sm py-8 text-center">Lade...</div>
        <div v-else-if="activityError" class="text-red-400 text-sm py-4">{{ activityError }}</div>
        <div v-else class="bg-gray-900 rounded-xl p-4 border border-gray-800">
          <div class="h-96">
            <Line :data="activityChartData" :options="activityChartOptions" />
          </div>
        </div>
        <!-- Summary -->
        <div class="mt-4 flex gap-4 flex-wrap">
          <div class="bg-gray-900 rounded-xl px-5 py-4 border border-gray-800 flex-1 min-w-40">
            <div class="text-2xl font-bold text-amber-400">{{ filteredActivityData.reduce((s, r) => s + r.chargeCount, 0) }}</div>
            <div class="text-xs text-gray-400 mt-1">Ladevorgange im Zeitraum</div>
          </div>
          <div class="bg-gray-900 rounded-xl px-5 py-4 border border-gray-800 flex-1 min-w-40">
            <div class="text-2xl font-bold text-blue-400">{{ filteredActivityData.reduce((s, r) => s + Number(r.kwhTotal), 0).toFixed(1) }}</div>
            <div class="text-xs text-gray-400 mt-1">kWh im Zeitraum</div>
          </div>
          <div class="bg-gray-900 rounded-xl px-5 py-4 border border-gray-800 flex-1 min-w-40">
            <div class="text-2xl font-bold text-emerald-400">{{ filteredActivityData.reduce((s, r) => s + Number(r.costTotal), 0).toFixed(2) }} €</div>
            <div class="text-xs text-gray-400 mt-1">Kosten im Zeitraum</div>
          </div>
        </div>
      </div>

      <!-- Tab: Traffic & Conversions -->
      <div v-else-if="activeTab === 'traffic'">
        <div class="flex items-center justify-between mb-4 gap-3 flex-wrap">
          <h2 class="text-lg font-semibold text-white">Traffic & Registrierungen</h2>
          <div class="flex gap-1">
            <button
              v-for="preset in [{ label: 'Heute', period: 'day' }, { label: '7T', period: '7d' }, { label: '30T', period: '30d' }, { label: '90T', period: '90d' }]"
              :key="preset.period"
              @click="setTrafficPreset(preset.period)"
              :class="[
                'px-2.5 py-1 rounded-lg text-xs font-medium transition',
                trafficPeriod === preset.period
                  ? 'bg-indigo-600 text-white'
                  : 'bg-gray-800 text-gray-400 hover:bg-gray-700 hover:text-gray-200'
              ]"
            >
              {{ preset.label }}
            </button>
          </div>
        </div>
        <div v-if="trafficLoading" class="text-gray-400 text-sm py-8 text-center">Lade...</div>
        <div v-else-if="trafficError" class="text-red-400 text-sm py-4">{{ trafficError }}</div>
        <div v-else-if="trafficData.length === 0" class="text-gray-500 text-sm py-8 text-center">
          Keine Daten - PLAUSIBLE_API_KEY gesetzt?
        </div>
        <div v-else class="bg-gray-900 rounded-xl p-4 border border-gray-800">
          <div class="h-96">
            <Line :data="trafficChartData" :options="trafficChartOptions" />
          </div>
        </div>
        <!-- Summary -->
        <div v-if="trafficData.length > 0" class="mt-4 flex gap-4 flex-wrap">
          <div class="bg-gray-900 rounded-xl px-5 py-4 border border-gray-800 flex-1 min-w-40">
            <div class="text-2xl font-bold text-indigo-400">{{ trafficData.reduce((s, r) => s + r.visitors, 0).toLocaleString() }}</div>
            <div class="text-xs text-gray-400 mt-1">{{ isHourly ? 'Visitors heute' : 'Unique Visitors' }}</div>
          </div>
          <div class="bg-gray-900 rounded-xl px-5 py-4 border border-gray-800 flex-1 min-w-40">
            <div class="text-2xl font-bold text-purple-400">{{ trafficData.reduce((s, r) => s + r.pageviews, 0).toLocaleString() }}</div>
            <div class="text-xs text-gray-400 mt-1">{{ isHourly ? 'Pageviews heute' : 'Pageviews' }}</div>
          </div>
          <template v-if="!isHourly">
            <div class="bg-gray-900 rounded-xl px-5 py-4 border border-gray-800 flex-1 min-w-40">
              <div class="text-2xl font-bold text-emerald-400">
                {{ trafficData.reduce((s, r) => s + (new Map(growthData.map(g => [g.day, g.newUsers])).get(r.date) ?? 0), 0) }}
              </div>
              <div class="text-xs text-gray-400 mt-1">Neue Registrierungen</div>
            </div>
            <div class="bg-gray-900 rounded-xl px-5 py-4 border border-gray-800 flex-1 min-w-40">
              <div class="text-2xl font-bold text-amber-400">
                {{
                  (() => {
                    const totalVisitors = trafficData.reduce((s, r) => s + r.visitors, 0)
                    const growthMap = new Map(growthData.map(r => [r.day, r.newUsers]))
                    const regs = trafficData.reduce((s, r) => s + (growthMap.get(r.date) ?? 0), 0)
                    if (totalVisitors === 0) return '0%'
                    return (regs / totalVisitors * 100).toFixed(1) + '%'
                  })()
                }}
              </div>
              <div class="text-xs text-gray-400 mt-1">Conversion Rate</div>
            </div>
          </template>
        </div>
      </div>

      <!-- Tab: Impersonate -->
      <div v-else-if="activeTab === 'impersonate'">
        <h2 class="text-lg font-semibold text-white mb-4">Als User einloggen</h2>
        <div class="max-w-md bg-gray-900 rounded-xl p-6 border border-gray-800">
          <p class="text-sm text-gray-400 mb-5">Generiert ein 1h-Token fur den Ziel-User.</p>

          <div v-if="impersonateError" class="mb-4 p-3 bg-red-900/40 border border-red-700 text-red-300 text-sm rounded-lg">
            {{ impersonateError }}
          </div>

          <div class="space-y-4">
            <div>
              <label class="block text-sm font-medium text-gray-300 mb-1">User-Email</label>
              <input
                v-model="email"
                type="email"
                placeholder="user@example.com"
                class="w-full px-3 py-2 bg-gray-800 border border-gray-700 rounded-lg text-gray-200 placeholder-gray-500 focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 focus:outline-none text-sm" />
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-300 mb-1">Internal Token</label>
              <input
                v-model="internalToken"
                type="password"
                placeholder="dev-internal-token-..."
                class="w-full px-3 py-2 bg-gray-800 border border-gray-700 rounded-lg text-gray-200 placeholder-gray-500 focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 focus:outline-none text-sm" />
            </div>
            <button
              @click="impersonate"
              :disabled="impersonateLoading"
              class="w-full py-2.5 bg-indigo-600 text-white font-semibold rounded-lg hover:bg-indigo-700 disabled:opacity-50 transition flex items-center justify-center gap-2 text-sm">
              <svg v-if="impersonateLoading" class="animate-spin h-4 w-4" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
              </svg>
              {{ impersonateLoading ? 'Wird eingeloggt...' : 'Als User einloggen' }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
