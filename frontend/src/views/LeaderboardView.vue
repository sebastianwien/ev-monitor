<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useAuthStore } from '../stores/auth'
import apiClient from '../api/axios'
import {
  TrophyIcon,
  BoltIcon,
  ArrowUpIcon,
  ArrowDownIcon,
  MinusIcon,
  SparklesIcon,
  StarIcon,
  MoonIcon,
  FireIcon,
  ArrowPathIcon,
  ClockIcon
} from '@heroicons/vue/24/outline'

interface LeaderboardEntry {
  rank: number
  username: string
  value: number
  unit: string
  previousRank: number | null
  rankDelta: number | null
  isNew: boolean
}

interface LeaderboardResponse {
  category: string
  displayName: string
  unit: string
  lowerIsBetter: boolean
  period: string
  entries: LeaderboardEntry[]
  ownEntry: LeaderboardEntry | null
}

const CATEGORIES = [
  { key: 'MONTHLY_KWH', label: 'Meiste kWh', icon: BoltIcon, color: 'text-yellow-500' },
  { key: 'MONTHLY_CHARGES', label: 'Ladevorgänge', icon: ArrowPathIcon, color: 'text-blue-500' },
  { key: 'MONTHLY_DISTANCE', label: 'Längste Strecke', icon: FireIcon, color: 'text-orange-500' },
  { key: 'MONTHLY_COINS', label: 'Meiste Watt', icon: SparklesIcon, color: 'text-purple-500' },
  { key: 'MONTHLY_CHEAPEST', label: 'Günstigster Lader', icon: StarIcon, color: 'text-green-500' },
  { key: 'MONTHLY_NIGHT_OWL', label: 'Nacht-Eule', icon: MoonIcon, color: 'text-indigo-400' },
  { key: 'MONTHLY_ICE_CHARGER', label: 'Eisbär', icon: ArrowDownIcon, color: 'text-cyan-400' },
  { key: 'MONTHLY_POWER_CHARGER', label: 'Schnellster Lader', icon: BoltIcon, color: 'text-red-500' },
]

const authStore = useAuthStore()
const activeCategory = ref('MONTHLY_KWH')
const data = ref<LeaderboardResponse | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)

const activeCategoryMeta = computed(() => CATEGORIES.find(c => c.key === activeCategory.value))

async function load(category: string) {
  loading.value = true
  error.value = null
  try {
    const res = await apiClient.get(`/public/leaderboard/${category}`)
    data.value = res.data
  } catch (e) {
    error.value = 'Leaderboard konnte nicht geladen werden.'
  } finally {
    loading.value = false
  }
}

watch(activeCategory, (cat) => load(cat))
onMounted(() => load(activeCategory.value))

function setCategory(key: string) {
  activeCategory.value = key
}

function rankDeltaClass(delta: number | null, isNew: boolean): string {
  if (isNew) return 'text-blue-500'
  if (delta === null) return 'text-gray-400'
  if (delta > 0) return 'text-green-500'
  if (delta < 0) return 'text-red-500'
  return 'text-gray-400'
}

function rankDeltaLabel(delta: number | null, isNew: boolean): string {
  if (isNew) return 'NEU'
  if (delta === null || delta === 0) return '-'
  return delta > 0 ? `+${delta}` : `${delta}`
}

function podiumClass(rank: number): string {
  if (rank === 1) return 'bg-yellow-50 dark:bg-yellow-900/20 border-yellow-300 dark:border-yellow-700'
  if (rank === 2) return 'bg-gray-50 dark:bg-gray-700 border-gray-300 dark:border-gray-600'
  if (rank === 3) return 'bg-orange-50 dark:bg-orange-900/20 border-orange-200 dark:border-orange-800'
  return 'bg-white dark:bg-gray-800 border-gray-100 dark:border-gray-700'
}

function rankLabel(rank: number): string {
  if (rank === 1) return '1'
  if (rank === 2) return '2'
  if (rank === 3) return '3'
  return String(rank)
}

function rankLabelClass(rank: number): string {
  if (rank === 1) return 'bg-yellow-400 text-white'
  if (rank === 2) return 'bg-gray-400 text-white'
  if (rank === 3) return 'bg-orange-400 text-white'
  return 'bg-gray-100 dark:bg-gray-600 text-gray-700 dark:text-gray-200'
}

const periodLabel = computed(() => {
  if (!data.value) return ''
  const [year, month] = data.value.period.split('-')
  const date = new Date(Number(year), Number(month) - 1, 1)
  return date.toLocaleString('de-DE', { month: 'long', year: 'numeric' })
})
</script>

<template>
  <div class="min-h-screen bg-gray-100 dark:bg-gray-900">
    <!-- Header -->
    <div class="bg-indigo-600 text-white px-4 py-6 md:rounded-xl md:shadow mb-0 md:mb-6">
      <div class="max-w-3xl mx-auto">
        <div class="flex items-center gap-3 mb-1">
          <TrophyIcon class="h-7 w-7 text-yellow-300" />
          <h1 class="text-2xl font-bold">Bestenliste</h1>
        </div>
        <p class="text-indigo-200 text-sm">
          Monatliches Ranking der aktivsten EV-Monitor-Community
          <span v-if="data"> - {{ periodLabel }}</span>
        </p>
      </div>
    </div>

    <div class="max-w-3xl mx-auto px-0 md:px-0">
      <!-- Category Tabs - horizontally scrollable on mobile -->
      <div class="overflow-x-auto -mx-0">
        <div class="flex gap-2 px-4 py-3 min-w-max">
          <button
            v-for="cat in CATEGORIES"
            :key="cat.key"
            @click="setCategory(cat.key)"
            :class="[
              'flex items-center gap-1.5 px-3 py-2 rounded-full text-sm font-medium whitespace-nowrap transition-all border',
              activeCategory === cat.key
                ? 'bg-indigo-600 text-white border-indigo-700 translate-y-0.5 shadow-[0_2px_0_0_#3730a3]'
                : 'bg-white dark:bg-gray-800 text-gray-700 dark:text-gray-300 border-gray-200 dark:border-gray-600 shadow-[0_4px_0_0_#d1d5db] hover:shadow-[0_4px_0_0_#a5b4fc] hover:border-indigo-300 active:translate-y-0.5 active:shadow-[0_2px_0_0_#d1d5db]'
            ]">
            <component :is="cat.icon" :class="['h-4 w-4', activeCategory === cat.key ? 'text-white' : cat.color]" />
            {{ cat.label }}
          </button>
        </div>
      </div>

      <!-- Loading -->
      <div v-if="loading" class="flex items-center justify-center py-16 text-gray-400">
        <ArrowPathIcon class="h-6 w-6 animate-spin mr-2" />
        Lade Bestenliste...
      </div>

      <!-- Error -->
      <div v-else-if="error" class="mx-4 bg-red-50 dark:bg-red-900/30 border border-red-200 dark:border-red-700 text-red-700 dark:text-red-300 rounded-xl p-4 text-sm">
        {{ error }}
      </div>

      <!-- Leaderboard Table -->
      <div v-else-if="data" class="mx-4 md:mx-0 space-y-2">
        <!-- Section label -->
        <div class="flex items-center gap-2 text-xs text-gray-500 dark:text-gray-400 px-1 pb-1">
          <component :is="activeCategoryMeta?.icon" :class="['h-3.5 w-3.5', activeCategoryMeta?.color]" />
          <span>{{ data.displayName }}</span>
          <span class="text-gray-300">·</span>
          <span v-if="data.lowerIsBetter" class="text-blue-500">Niedriger = Besser</span>
        </div>

        <!-- Empty state -->
        <div v-if="data.entries.length === 0" class="bg-white dark:bg-gray-800 rounded-xl border border-gray-100 dark:border-gray-700 p-8 text-center text-gray-400">
          <TrophyIcon class="h-10 w-10 mx-auto mb-3 opacity-30" />
          <p class="text-sm">Noch keine Daten fuer diesen Monat.</p>
          <p class="text-xs mt-1">Lad auf und sichere dir deinen Platz!</p>
        </div>

        <!-- Top 10 -->
        <div
          v-for="entry in data.entries"
          :key="entry.rank"
          :class="['rounded-xl border p-3 flex items-center gap-3 transition', podiumClass(entry.rank)]">

          <!-- Rank badge -->
          <div :class="['w-8 h-8 rounded-full flex items-center justify-center text-sm font-bold flex-shrink-0', rankLabelClass(entry.rank)]">
            {{ rankLabel(entry.rank) }}
          </div>

          <!-- Username -->
          <div class="flex-1 min-w-0">
            <div class="font-semibold text-gray-900 dark:text-gray-100 truncate">{{ entry.username }}</div>
            <div class="text-xs text-gray-500 dark:text-gray-400">Platz {{ entry.rank }}</div>
          </div>

          <!-- Value -->
          <div class="text-right flex-shrink-0">
            <div class="font-bold text-gray-900 dark:text-gray-100 tabular-nums">{{ entry.value }}</div>
            <div class="text-xs text-gray-400">{{ entry.unit }}</div>
          </div>

          <!-- Delta indicator -->
          <div :class="['w-12 flex-shrink-0 flex flex-col items-center text-xs font-semibold', rankDeltaClass(entry.rankDelta, entry.isNew)]">
            <ArrowUpIcon v-if="entry.rankDelta && entry.rankDelta > 0" class="h-3.5 w-3.5" />
            <ArrowDownIcon v-else-if="entry.rankDelta && entry.rankDelta < 0" class="h-3.5 w-3.5" />
            <MinusIcon v-else-if="!entry.isNew && (entry.rankDelta === 0 || entry.previousRank !== null)" class="h-3.5 w-3.5" />
            <SparklesIcon v-else-if="entry.isNew" class="h-3.5 w-3.5" />
            <span>{{ rankDeltaLabel(entry.rankDelta, entry.isNew) }}</span>
          </div>
        </div>

        <!-- Own rank (if not in top 10) -->
        <template v-if="data.ownEntry">
          <div class="text-center text-xs text-gray-400 py-1">- - -</div>
          <div class="rounded-xl border-2 border-indigo-400 bg-indigo-50 dark:bg-indigo-900/20 p-3 flex items-center gap-3">
            <div class="w-8 h-8 rounded-full bg-indigo-500 text-white flex items-center justify-center text-sm font-bold flex-shrink-0">
              {{ data.ownEntry.rank }}
            </div>
            <div class="flex-1 min-w-0">
              <div class="font-semibold text-indigo-900 dark:text-indigo-200 truncate">{{ data.ownEntry.username }} <span class="text-xs font-normal text-indigo-500">(Du)</span></div>
              <div class="text-xs text-indigo-400">Platz {{ data.ownEntry.rank }}</div>
            </div>
            <div class="text-right flex-shrink-0">
              <div class="font-bold text-indigo-900 dark:text-indigo-200 tabular-nums">{{ data.ownEntry.value }}</div>
              <div class="text-xs text-indigo-400">{{ data.ownEntry.unit }}</div>
            </div>
            <div :class="['w-12 flex-shrink-0 flex flex-col items-center text-xs font-semibold', rankDeltaClass(data.ownEntry.rankDelta, data.ownEntry.isNew)]">
              <ArrowUpIcon v-if="data.ownEntry.rankDelta && data.ownEntry.rankDelta > 0" class="h-3.5 w-3.5" />
              <ArrowDownIcon v-else-if="data.ownEntry.rankDelta && data.ownEntry.rankDelta < 0" class="h-3.5 w-3.5" />
              <MinusIcon v-else-if="!data.ownEntry.isNew && data.ownEntry.previousRank !== null" class="h-3.5 w-3.5" />
              <SparklesIcon v-else-if="data.ownEntry.isNew" class="h-3.5 w-3.5" />
              <span>{{ rankDeltaLabel(data.ownEntry.rankDelta, data.ownEntry.isNew) }}</span>
            </div>
          </div>
        </template>

        <!-- Login hint if not authenticated -->
        <div
          v-if="!authStore.isAuthenticated() && data.entries.length > 0"
          class="text-center text-xs text-gray-500 dark:text-gray-400 pt-2 pb-1">
          <router-link to="/login" class="text-indigo-500 hover:underline">Einloggen</router-link>
          um deinen eigenen Platz zu sehen.
        </div>

        <!-- Watt history link (only for MONTHLY_COINS tab) -->
        <router-link
          v-if="activeCategory === 'MONTHLY_COINS'"
          to="/coins"
          class="flex items-center gap-2 bg-indigo-50 dark:bg-indigo-900/20 border border-indigo-200 dark:border-indigo-700 rounded-xl p-3 text-sm text-indigo-700 dark:text-indigo-300 hover:bg-indigo-100 dark:hover:bg-indigo-900/30 transition mt-1">
          <ClockIcon class="h-4 w-4 flex-shrink-0 text-indigo-400" />
          <span>Deinen eigenen Watt-Verlauf ansehen</span>
        </router-link>

        <!-- Month-end reward hint -->
        <div class="bg-yellow-50 dark:bg-yellow-900/20 border border-yellow-200 dark:border-yellow-700 rounded-xl p-3 text-xs text-yellow-800 dark:text-yellow-300 flex gap-2 mt-4">
          <TrophyIcon class="h-4 w-4 flex-shrink-0 text-yellow-500 mt-0.5" />
          <span>
            Die Top 3 erhalten am Monatsende Bonus-Coins:
            <strong>100</strong> / <strong>50</strong> / <strong>25 Coins</strong>.
            <span v-if="!data.ownEntry && authStore.isAuthenticated()"> Du bist noch nicht im Ranking - leg los!</span>
          </span>
        </div>
      </div>

      <div class="h-8" />
    </div>
  </div>
</template>
