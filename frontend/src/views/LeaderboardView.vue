<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
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
  carLabel: string | null
  value: number
  unit: string
  previousRank: number | null
  rankDelta: number | null
  isNew: boolean
  kwhTotal: number | null
  sessionCount: number | null
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

const { t, locale, n } = useI18n()

const CATEGORIES = computed(() => [
  { key: 'MONTHLY_KWH', label: t('leaderboard.cat_kwh'), icon: BoltIcon, color: 'text-yellow-500' },
  { key: 'MONTHLY_CHARGES', label: t('leaderboard.cat_charges'), icon: ArrowPathIcon, color: 'text-blue-500' },
  { key: 'MONTHLY_DISTANCE', label: t('leaderboard.cat_distance'), icon: FireIcon, color: 'text-orange-500' },
  { key: 'MONTHLY_CHEAPEST', label: t('leaderboard.cat_cheapest'), icon: StarIcon, color: 'text-green-500' },
  { key: 'MONTHLY_NIGHT_OWL', label: t('leaderboard.cat_night_owl'), icon: MoonIcon, color: 'text-indigo-400' },
  { key: 'MONTHLY_ICE_CHARGER', label: t('leaderboard.cat_ice_charger'), icon: ArrowDownIcon, color: 'text-cyan-400' },
  { key: 'MONTHLY_POWER_CHARGER', label: t('leaderboard.cat_power_charger'), icon: BoltIcon, color: 'text-red-500' },
])

const authStore = useAuthStore()
const activeCategory = ref('MONTHLY_KWH')
const data = ref<LeaderboardResponse | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)

const activeCategoryMeta = computed(() => CATEGORIES.value.find(c => c.key === activeCategory.value))

async function load(category: string) {
  loading.value = true
  error.value = null
  try {
    const res = await apiClient.get(`/public/leaderboard/${category}`)
    data.value = res.data
  } catch (e) {
    error.value = t('leaderboard.loading')
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
  if (isNew) return t('leaderboard.new_entry')
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
  return date.toLocaleString(locale.value === 'en' ? 'en-GB' : 'de-DE', { month: 'long', year: 'numeric' })
})
</script>

<template>
  <div class="md:max-w-2xl md:mx-auto md:p-6">
    <div class="bg-white dark:bg-gray-800 md:rounded-xl md:shadow-lg overflow-hidden">

      <!-- Header -->
      <div class="p-4 md:p-6 border-b border-gray-100 dark:border-gray-700">
        <div class="flex items-center gap-3 mb-1">
          <TrophyIcon class="h-7 w-7 text-yellow-500" />
          <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">{{ t('leaderboard.title') }}</h1>
        </div>
        <p class="text-gray-600 dark:text-gray-400 text-sm">
          {{ t('leaderboard.subtitle') }}<span v-if="data"> - {{ periodLabel }}</span>
        </p>
      </div>

      <!-- Category Tabs -->
      <div class="overflow-x-auto md:overflow-x-visible border-b border-gray-100 dark:border-gray-700">
        <div class="flex gap-2 px-4 py-3 min-w-max md:min-w-0 md:flex-wrap">
          <button
            v-for="cat in CATEGORIES"
            :key="cat.key"
            @click="setCategory(cat.key)"
            :class="[
              'flex items-center gap-1.5 px-3 py-2 rounded-full text-sm font-medium whitespace-nowrap transition-all border',
              activeCategory === cat.key
                ? 'bg-indigo-600 text-white border-indigo-700 translate-y-0.5 shadow-[0_2px_0_0_#3730a3]'
                : 'bg-white dark:bg-gray-700 text-gray-700 dark:text-gray-300 border-gray-200 dark:border-gray-600 shadow-[0_4px_0_0_#d1d5db] dark:shadow-[0_4px_0_0_#374151] hover:shadow-[0_4px_0_0_#a5b4fc] hover:border-indigo-300 active:translate-y-0.5 active:shadow-[0_2px_0_0_#d1d5db]'
            ]">
            <component :is="cat.icon" :class="['h-4 w-4', activeCategory === cat.key ? 'text-white' : cat.color]" />
            {{ cat.label }}
          </button>
        </div>
      </div>

      <!-- Content -->
      <div class="p-4 md:p-6">

        <!-- Loading -->
        <div v-if="loading" class="flex items-center justify-center py-12 text-gray-400">
          <ArrowPathIcon class="h-6 w-6 animate-spin mr-2" />
          {{ t('leaderboard.loading') }}
        </div>

        <!-- Error -->
        <div v-else-if="error" class="bg-red-50 dark:bg-red-900/30 border border-red-200 dark:border-red-700 text-red-700 dark:text-red-300 rounded-xl p-4 text-sm">
          {{ error }}
        </div>

        <div v-else-if="data" class="space-y-2">

          <!-- Section label -->
          <div class="flex items-center gap-2 text-xs text-gray-500 dark:text-gray-400 px-1 pb-1">
            <component :is="activeCategoryMeta?.icon" :class="['h-3.5 w-3.5', activeCategoryMeta?.color]" />
            <span>{{ data.displayName }}</span>
            <span class="text-gray-300 dark:text-gray-600">·</span>
            <span v-if="data.lowerIsBetter" class="text-blue-500">{{ t('leaderboard.lower_is_better') }}</span>
          </div>

          <!-- Empty state -->
          <div v-if="data.entries.length === 0" class="bg-gray-50 dark:bg-gray-700/50 rounded-xl p-8 text-center text-gray-400">
            <TrophyIcon class="h-10 w-10 mx-auto mb-3 opacity-30" />
            <p class="text-sm">{{ t('leaderboard.no_data') }}</p>
            <p class="text-xs mt-1">{{ t('leaderboard.no_data_subtitle') }}</p>
          </div>

          <!-- Top 10 -->
          <div
            v-for="entry in data.entries"
            :key="entry.rank"
            :class="['rounded-xl border p-3 flex items-center gap-3 transition', podiumClass(entry.rank)]">
            <div :class="['w-8 h-8 rounded-full flex items-center justify-center text-sm font-bold flex-shrink-0', rankLabelClass(entry.rank)]">
              {{ rankLabel(entry.rank) }}
            </div>
            <div class="flex-1 min-w-0">
              <div class="font-semibold text-gray-900 dark:text-gray-100 truncate">{{ entry.username }}</div>
              <div class="text-xs text-gray-500 dark:text-gray-400 truncate">{{ entry.carLabel ?? t('leaderboard.rank', { n: entry.rank }) }}</div>
              <div v-if="entry.kwhTotal != null && entry.sessionCount != null" class="text-xs text-gray-400 dark:text-gray-500 truncate">
                {{ t('leaderboard.cheapest_subtitle', { kwh: n(Number(entry.kwhTotal), { minimumFractionDigits: 1, maximumFractionDigits: 1 }), sessions: entry.sessionCount }) }}
              </div>
            </div>
            <div class="text-right flex-shrink-0">
              <div class="font-bold text-gray-900 dark:text-gray-100 tabular-nums">{{ entry.value }}</div>
              <div class="text-xs text-gray-400">{{ entry.unit }}</div>
            </div>
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
                <div class="font-semibold text-indigo-900 dark:text-indigo-200 truncate">{{ data.ownEntry.username }} <span class="text-xs font-normal text-indigo-500">{{ t('leaderboard.you') }}</span></div>
                <div class="text-xs text-indigo-400 truncate">{{ data.ownEntry.carLabel ?? t('leaderboard.rank', { n: data.ownEntry.rank }) }}</div>
                <div v-if="data.ownEntry.kwhTotal != null && data.ownEntry.sessionCount != null" class="text-xs text-indigo-300 dark:text-indigo-500 truncate">
                  {{ t('leaderboard.cheapest_subtitle', { kwh: n(Number(data.ownEntry.kwhTotal), { minimumFractionDigits: 1, maximumFractionDigits: 1 }), sessions: data.ownEntry.sessionCount }) }}
                </div>
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

          <!-- Login hint -->
          <div
            v-if="!authStore.isAuthenticated() && data.entries.length > 0"
            class="text-center text-xs text-gray-500 dark:text-gray-400 pt-2 pb-1">
            <router-link to="/login" class="text-indigo-500 hover:underline">{{ t('leaderboard.login_hint') }}</router-link>
            {{ t('leaderboard.login_hint_suffix') }}
          </div>

          <!-- Watt history link -->
          <router-link
            v-if="activeCategory === 'MONTHLY_COINS'"
            to="/coins"
            class="flex items-center gap-2 bg-indigo-50 dark:bg-indigo-900/20 border border-indigo-200 dark:border-indigo-700 rounded-xl p-3 text-sm text-indigo-700 dark:text-indigo-300 hover:bg-indigo-100 dark:hover:bg-indigo-900/30 transition mt-1">
            <ClockIcon class="h-4 w-4 flex-shrink-0 text-indigo-400" />
            <span>{{ t('leaderboard.coins_history') }}</span>
          </router-link>

          <!-- Month-end reward hint -->
          <div class="bg-yellow-50 dark:bg-yellow-900/20 border border-yellow-200 dark:border-yellow-700 rounded-xl p-3 text-xs text-yellow-800 dark:text-yellow-300 flex gap-2 mt-4">
            <TrophyIcon class="h-4 w-4 flex-shrink-0 text-yellow-500 mt-0.5" />
            <span>
              {{ t('leaderboard.top3_reward') }}
              <strong>50</strong> / <strong>30</strong> / <strong>15 Watt</strong>.
              <span v-if="!data.ownEntry && authStore.isAuthenticated()"> {{ t('leaderboard.not_ranked') }}</span>
            </span>
          </div>

        </div>
      </div>
    </div>
    <div class="h-8" />
  </div>
</template>
