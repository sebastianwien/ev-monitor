<script setup lang="ts">
import { ref, onMounted } from 'vue'
import api from '../api/axios'
import { useCoinStore } from '../stores/coins'
import { BoltIcon, TrophyIcon, ArrowUpIcon, ArrowDownIcon, MinusIcon, SparklesIcon } from '@heroicons/vue/24/outline'

const coinStore = useCoinStore()

interface Standing {
  category: string
  displayName: string
  unit: string
  lowerIsBetter: boolean
  rank: number | null
  value: number | null
  rankDelta: number | null
  isNew: boolean
}

const standings = ref<Standing[]>([])
const standingsLoading = ref(false)
const standingsOpen = ref(localStorage.getItem('standings-open') !== 'false')

async function fetchStandings() {
  standingsLoading.value = true
  try {
    const res = await api.get('/public/leaderboard/standings/me')
    standings.value = res.data
  } catch {
    // silent - not critical
  } finally {
    standingsLoading.value = false
  }
}

function toggleStandings() {
  standingsOpen.value = !standingsOpen.value
  localStorage.setItem('standings-open', String(standingsOpen.value))
}

function deltaClass(delta: number | null, isNew: boolean): string {
  if (isNew) return 'text-blue-500'
  if (delta === null) return 'text-gray-400'
  if (delta > 0) return 'text-green-600'
  if (delta < 0) return 'text-red-500'
  return 'text-gray-400'
}

function deltaLabel(delta: number | null, isNew: boolean): string {
  if (isNew) return 'NEU'
  if (delta === null || delta === 0) return '-'
  return delta > 0 ? `+${delta}` : `${delta}`
}

interface CoinLog {
  id: string
  coinType: string
  amount: number
  actionDescription: string
  createdAt: string
}

const logs = ref<CoinLog[]>([])
const loading = ref(false)
const error = ref<string | null>(null)

const coinTypeLabel: Record<string, string> = {
  ACHIEVEMENT_COIN: 'Achievement',
  SOCIAL_COIN: 'Community',
  GREEN_COIN: 'Eco',
  DISTANCE_COIN: 'Strecke',
  STREAK_COIN: 'Streak',
  EFFICIENCY_COIN: 'Effizienz'
}

const formatDate = (iso: string): string => {
  return new Date(iso).toLocaleString('de-DE', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  })
}

const fetchLogs = async () => {
  loading.value = true
  error.value = null
  try {
    const response = await api.get('/coins/logs')
    // Sort newest first
    logs.value = (response.data as CoinLog[]).sort(
      (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
    )
  } catch (err: any) {
    error.value = err.response?.data?.message || 'Fehler beim Laden des Watt-Verlaufs'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchLogs()
  fetchStandings()
})
</script>

<template>
  <div class="md:max-w-2xl md:mx-auto p-4 md:p-6 md:mt-8">
    <div class="bg-white dark:bg-gray-800 md:rounded-xl md:shadow-lg p-4 md:p-6">
      <!-- Header -->
      <div class="mb-6">
        <div class="flex items-center justify-between">
          <h1 class="text-2xl font-bold text-gray-800 dark:text-gray-200">Watt-Verlauf</h1>
          <div class="flex items-center gap-2 px-4 py-2 bg-indigo-50 border border-indigo-200 rounded-lg">
            <BoltIcon class="h-5 w-5 text-indigo-600" />
            <span class="text-xl font-bold text-indigo-700">{{ coinStore.balance }}</span>
            <span class="text-sm text-indigo-500">Watt</span>
          </div>
        </div>
        <router-link to="/leaderboard" class="mt-3 flex items-center justify-center gap-2 w-full px-4 py-2.5 bg-yellow-400 hover:bg-yellow-300 active:translate-y-0.5 active:shadow-none text-yellow-900 font-semibold text-sm rounded-xl shadow-[0_4px_0_0_#b45309] hover:shadow-[0_4px_0_0_#92400e] active:shadow-[0_2px_0_0_#92400e] transition-all">
          <TrophyIcon class="h-4 w-4" />
          Zur Bestenliste
        </router-link>
      </div>

      <!-- Leaderboard Standings -->
      <div class="mb-6">
        <button @click="toggleStandings" class="w-full flex items-center justify-between mb-2 group">
          <h2 class="text-sm font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wide">Dein Ranking diesen Monat</h2>
          <span class="text-xs text-gray-400 group-hover:text-gray-600 dark:group-hover:text-gray-300 transition">{{ standingsOpen ? 'einklappen ▲' : 'ausklappen ▼' }}</span>
        </button>
        <div v-if="standingsOpen">
        <div v-if="standingsLoading" class="text-center py-4 text-gray-400 text-sm">Lade...</div>
        <div v-else class="rounded-xl border border-gray-100 dark:border-gray-700 overflow-hidden">
          <table class="w-full text-sm">
            <tbody>
              <tr
                v-for="s in standings"
                :key="s.category"
                class="border-b border-gray-50 dark:border-gray-700 last:border-0 hover:bg-gray-50 dark:hover:bg-gray-700 transition">
                <!-- Category name -->
                <td class="px-3 py-2.5 text-gray-700 dark:text-gray-300 font-medium">{{ s.displayName }}</td>
                <!-- Rank -->
                <td class="px-3 py-2.5 text-right">
                  <span v-if="s.rank !== null" class="font-bold text-gray-900 dark:text-gray-100">#{{ s.rank }}</span>
                  <span v-else class="text-gray-300 dark:text-gray-600">-</span>
                </td>
                <!-- Value -->
                <td class="px-3 py-2.5 text-right text-gray-500 dark:text-gray-400 tabular-nums">
                  <span v-if="s.value !== null">{{ s.value }} {{ s.unit }}</span>
                </td>
                <!-- Delta -->
                <td class="px-3 py-2.5 w-10">
                  <div :class="['flex flex-col items-center text-xs font-semibold', deltaClass(s.rankDelta, s.isNew)]">
                    <ArrowUpIcon v-if="s.rankDelta && s.rankDelta > 0" class="h-3 w-3" />
                    <ArrowDownIcon v-else-if="s.rankDelta && s.rankDelta < 0" class="h-3 w-3" />
                    <SparklesIcon v-else-if="s.isNew" class="h-3 w-3" />
                    <MinusIcon v-else-if="s.rank !== null" class="h-3 w-3" />
                    <span v-if="s.rank !== null">{{ deltaLabel(s.rankDelta, s.isNew) }}</span>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        </div>
      </div>

      <!-- Legende -->
      <details class="mb-6 rounded-lg border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-700 text-sm">
        <summary class="cursor-pointer px-4 py-3 font-medium text-gray-700 dark:text-gray-300 select-none">Wofür gibt es Watt?</summary>
        <div class="px-4 pb-4 pt-2 space-y-3">
          <div>
            <p class="text-xs font-semibold uppercase tracking-wide text-gray-400 mb-1">Ladevorgänge</p>
            <ul class="space-y-1 text-gray-600 dark:text-gray-400">
              <li class="flex justify-between"><span>Erster Ladevorgang erfasst</span><span class="font-semibold text-indigo-600">+25 ⚡</span></li>
              <li class="flex justify-between"><span>Weiterer Ladevorgang</span><span class="font-semibold text-indigo-600">+5 ⚡</span></li>
              <li class="flex justify-between"><span>Ladevorgang via OCR (erster)</span><span class="font-semibold text-indigo-600">+27 ⚡</span></li>
              <li class="flex justify-between"><span>Ladevorgang via OCR</span><span class="font-semibold text-indigo-600">+7 ⚡</span></li>
              <li class="flex justify-between"><span>Ladevorgang gelöscht</span><span class="font-semibold text-red-500">− ⚡</span></li>
            </ul>
          </div>
          <div>
            <p class="text-xs font-semibold uppercase tracking-wide text-gray-400 mb-1">Imports (einmalig)</p>
            <ul class="space-y-1 text-gray-600 dark:text-gray-400">
              <li class="flex justify-between"><span>Sprit-Monitor verbunden</span><span class="font-semibold text-indigo-600">+50 ⚡</span></li>
              <li class="flex justify-between"><span>Ladevorgang via Sprit-Monitor</span><span class="font-semibold text-indigo-600">+2 ⚡</span></li>
              <li class="flex justify-between"><span>Tesla verbunden</span><span class="font-semibold text-indigo-600">+50 ⚡</span></li>
              <li class="flex justify-between"><span>TeslaLogger verbunden</span><span class="font-semibold text-indigo-600">+20 ⚡</span></li>
              <li class="flex justify-between"><span>Ladevorgang via TeslaLogger (Historie)</span><span class="font-semibold text-indigo-600">+2 ⚡</span></li>
              <li class="flex justify-between"><span>Ladevorgang via Tesla Sync (täglich)</span><span class="font-semibold text-indigo-600">+5 ⚡</span></li>
            </ul>
          </div>
          <div>
            <p class="text-xs font-semibold uppercase tracking-wide text-gray-400 mb-1">Fahrzeuge</p>
            <ul class="space-y-1 text-gray-600 dark:text-gray-400">
              <li class="flex justify-between"><span>Erstes Fahrzeug hinzugefügt</span><span class="font-semibold text-indigo-600">+20 ⚡</span></li>
              <li class="flex justify-between"><span>Weiteres Fahrzeug</span><span class="font-semibold text-indigo-600">+5 ⚡</span></li>
              <li class="flex justify-between"><span>Erstes Auto-Bild hochgeladen (einmalig)</span><span class="font-semibold text-indigo-600">+15 ⚡</span></li>
              <li class="flex justify-between"><span>Bild öffentlich geteilt (einmalig)</span><span class="font-semibold text-indigo-600">+10 ⚡</span></li>
            </ul>
          </div>
          <div>
            <p class="text-xs font-semibold uppercase tracking-wide text-gray-400 mb-1">Community</p>
            <ul class="space-y-1 text-gray-600 dark:text-gray-400">
              <li class="flex justify-between"><span>Freund eingeladen</span><span class="font-semibold text-indigo-600">+100 ⚡</span></li>
              <li class="flex justify-between"><span>Willkommensbonus (eingeladen worden)</span><span class="font-semibold text-indigo-600">+25 ⚡</span></li>
            </ul>
          </div>
        </div>
      </details>

      <!-- Error -->
      <div v-if="error" class="mb-4 p-4 bg-red-50 dark:bg-red-900/30 border border-red-200 dark:border-red-700 text-red-700 dark:text-red-300 rounded-md text-sm">
        {{ error }}
      </div>

      <!-- Loading -->
      <div v-if="loading" class="text-center py-12 text-gray-500 dark:text-gray-400">
        <div class="inline-block animate-spin rounded-full h-8 w-8 border-b-2 border-indigo-600 mb-3"></div>
        <p class="text-sm">Lade Watt-Verlauf...</p>
      </div>

      <!-- Empty state -->
      <div v-else-if="logs.length === 0" class="text-center py-12">
        <BoltIcon class="h-16 w-16 mx-auto mb-4 text-gray-300" />
        <h3 class="text-lg font-semibold text-gray-700 dark:text-gray-300 mb-2">Noch kein Watt verdient</h3>
        <p class="text-gray-500 dark:text-gray-400 text-sm">
          Erfasse deinen ersten Ladevorgang oder füge ein Fahrzeug hinzu, um Coins zu verdienen!
        </p>
      </div>

      <!-- Coin log list -->
      <ul v-else class="space-y-3">
        <li
          v-for="log in logs"
          :key="log.id"
          class="flex items-center justify-between p-4 bg-gray-50 dark:bg-gray-700 border border-gray-200 dark:border-gray-600 rounded-lg">
          <div class="flex items-center gap-3">
            <BoltIcon class="h-6 w-6 text-indigo-400 flex-shrink-0" />
            <div>
              <p class="font-medium text-gray-800 dark:text-gray-200 text-sm">{{ log.actionDescription }}</p>
              <p class="text-xs text-gray-500 dark:text-gray-400 mt-0.5">
                {{ coinTypeLabel[log.coinType] || log.coinType }} · {{ formatDate(log.createdAt) }}
              </p>
            </div>
          </div>
          <span class="font-bold text-green-600 text-base">+{{ log.amount }}</span>
        </li>
      </ul>
    </div>
  </div>
</template>
