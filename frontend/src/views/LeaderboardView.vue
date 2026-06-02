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
  ClockIcon,
  SunIcon,
  ChevronLeftIcon,
  ChevronRightIcon,
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
  hasMonthEndReward: boolean
  period: string
  entries: LeaderboardEntry[]
  ownEntry: LeaderboardEntry | null
}

const { t, locale, n } = useI18n()

const CATEGORIES = computed(() => [
  { key: 'MONTHLY_KWH', label: t('leaderboard.cat_kwh'), desc: t('leaderboard.desc_kwh'), icon: BoltIcon, color: 'text-yellow-500' },
  { key: 'MONTHLY_CHARGES', label: t('leaderboard.cat_charges'), desc: t('leaderboard.desc_charges'), icon: ArrowPathIcon, color: 'text-blue-500' },
  { key: 'MONTHLY_DISTANCE', label: t('leaderboard.cat_distance'), desc: t('leaderboard.desc_distance'), icon: FireIcon, color: 'text-orange-500' },
  { key: 'MONTHLY_CHEAPEST', label: t('leaderboard.cat_cheapest'), desc: t('leaderboard.desc_cheapest'), icon: StarIcon, color: 'text-green-500' },
  { key: 'MONTHLY_NIGHT_OWL', label: t('leaderboard.cat_night_owl'), desc: t('leaderboard.desc_night_owl'), icon: MoonIcon, color: 'text-indigo-400' },
  { key: 'MONTHLY_ICE_CHARGER', label: t('leaderboard.cat_ice_charger'), desc: t('leaderboard.desc_ice_charger'), icon: ArrowDownIcon, color: 'text-cyan-400' },
  { key: 'MONTHLY_HEAT_CHARGER', label: t('leaderboard.cat_heat_charger'), desc: t('leaderboard.desc_heat_charger'), icon: SunIcon, color: 'text-orange-400' },
  { key: 'MONTHLY_POWER_CHARGER', label: t('leaderboard.cat_power_charger'), desc: t('leaderboard.desc_power_charger'), icon: BoltIcon, color: 'text-red-500' },
])

const authStore = useAuthStore()
const activeCategory = ref('MONTHLY_KWH')
const data = ref<LeaderboardResponse | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)
const slideDirection = ref<'left' | 'right'>('left')

const activeCategoryMeta = computed(() => CATEGORIES.value.find(c => c.key === activeCategory.value))
const activeCategoryIndex = computed(() => CATEGORIES.value.findIndex(c => c.key === activeCategory.value))

function prevCategory() {
  slideDirection.value = 'right'
  const idx = activeCategoryIndex.value
  setCategory(CATEGORIES.value[(idx - 1 + CATEGORIES.value.length) % CATEGORIES.value.length].key)
}

function nextCategory() {
  slideDirection.value = 'left'
  const idx = activeCategoryIndex.value
  setCategory(CATEGORIES.value[(idx + 1) % CATEGORIES.value.length].key)
}

function jumpToCategory(key: string) {
  const from = activeCategoryIndex.value
  const to = CATEGORIES.value.findIndex(c => c.key === key)
  slideDirection.value = to > from ? 'left' : 'right'
  setCategory(key)
}

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

function podiumCardClass(rank: number): string {
  if (rank === 1) return 'ring-2 ring-amber-400 dark:ring-amber-500 bg-white dark:bg-gray-800 rounded-sm px-4 py-4'
  if (rank === 2) return 'ring-1 ring-slate-300 dark:ring-slate-500 bg-white dark:bg-gray-800 rounded-sm px-4 py-3'
  if (rank === 3) return 'ring-1 ring-amber-300 dark:ring-amber-700 bg-white dark:bg-gray-800 rounded-sm px-4 py-3'
  return 'border border-gray-100 dark:border-gray-700 bg-white dark:bg-gray-800 rounded-sm px-4 py-2.5'
}

const MEDAL_COLORS: Record<number, { bail: string; face: string; shine: string; text: string }> = {
  1: { bail: '#D97706', face: '#F59E0B', shine: '#FDE68A', text: '#7C2D12' },
  2: { bail: '#64748B', face: '#94A3B8', shine: '#E2E8F0', text: '#1E293B' },
  3: { bail: '#92400E', face: '#B45309', shine: '#FDE68A', text: '#451A03' },
}

function usernameClass(rank: number): string {
  if (rank === 1) return 'text-base font-bold text-gray-900 dark:text-gray-100 truncate'
  if (rank <= 3) return 'text-sm font-semibold text-gray-900 dark:text-gray-100 truncate'
  return 'text-sm font-medium text-gray-800 dark:text-gray-200 truncate'
}

function valueClass(rank: number): string {
  if (rank === 1) return 'text-2xl font-black text-gray-900 dark:text-gray-100 tabular-nums'
  if (rank <= 3) return 'text-lg font-bold text-gray-800 dark:text-gray-200 tabular-nums'
  return 'text-sm font-semibold text-gray-700 dark:text-gray-300 tabular-nums'
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
    <div class="bg-white dark:bg-gray-800 md:rounded-sm md:shadow-[4px_4px_0_rgba(0,0,0,0.30)] dark:md:shadow-[4px_4px_0_rgba(255,255,255,0.30)] overflow-hidden">

      <!-- Header -->
      <div class="p-4 md:p-6 border-b border-gray-100 dark:border-gray-700 text-center">
        <div class="flex items-center justify-center gap-3 mb-1">
          <TrophyIcon class="h-7 w-7 text-yellow-500" />
          <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">{{ t('leaderboard.title') }}</h1>
        </div>
        <p class="text-gray-600 dark:text-gray-400 text-sm">
          {{ t('leaderboard.subtitle') }}<span v-if="data"> - {{ periodLabel }}</span>
        </p>
      </div>

      <!-- Category Gallery Header -->
      <div class="border-b border-gray-100 dark:border-gray-700 px-4 py-4">
        <div class="flex items-center gap-3">
          <button
            @click="prevCategory"
            class="flex-shrink-0 w-8 h-8 flex items-center justify-center rounded-full border border-gray-200 dark:border-gray-600 text-gray-400 dark:text-gray-500 hover:border-indigo-300 hover:text-indigo-500 dark:hover:border-indigo-600 dark:hover:text-indigo-400 transition"
            :aria-label="t('leaderboard.prev_category')">
            <ChevronLeftIcon class="h-4 w-4" />
          </button>
          <div class="flex-1 min-w-0 text-center">
            <div class="flex items-center justify-center gap-2 mb-0.5">
              <component :is="activeCategoryMeta?.icon" :class="['h-5 w-5 flex-shrink-0', activeCategoryMeta?.color]" />
              <h2 class="text-lg font-bold text-gray-900 dark:text-gray-100 truncate">{{ activeCategoryMeta?.label }}</h2>
            </div>
            <p class="text-xs text-gray-500 dark:text-gray-400">{{ activeCategoryMeta?.desc }}</p>
          </div>
          <button
            @click="nextCategory"
            class="flex-shrink-0 w-8 h-8 flex items-center justify-center rounded-full border border-gray-200 dark:border-gray-600 text-gray-400 dark:text-gray-500 hover:border-indigo-300 hover:text-indigo-500 dark:hover:border-indigo-600 dark:hover:text-indigo-400 transition"
            :aria-label="t('leaderboard.next_category')">
            <ChevronRightIcon class="h-4 w-4" />
          </button>
        </div>
        <!-- Dot indicators -->
        <div class="flex justify-center gap-1.5 mt-3">
          <button
            v-for="(cat, i) in CATEGORIES"
            :key="cat.key"
            @click="jumpToCategory(cat.key)"
            :class="[
              'rounded-full transition-all',
              i === activeCategoryIndex ? 'w-5 h-1.5 bg-indigo-500' : 'w-1.5 h-1.5 bg-gray-300 dark:bg-gray-600 hover:bg-gray-400 dark:hover:bg-gray-500'
            ]"
            :aria-label="cat.label" />
        </div>
      </div>

      <!-- Content -->
      <div class="p-4 md:p-6 overflow-hidden">
        <Transition :name="slideDirection === 'left' ? 'slide-left' : 'slide-right'" mode="out-in">
        <div :key="activeCategory">

        <!-- Loading -->
        <div v-if="loading" class="flex items-center justify-center py-12 text-gray-400">
          <ArrowPathIcon class="h-6 w-6 animate-spin mr-2" />
          {{ t('leaderboard.loading') }}
        </div>

        <!-- Error -->
        <div v-else-if="error" class="bg-red-50 dark:bg-red-900/30 border border-red-200 dark:border-red-700 text-red-700 dark:text-red-300 rounded-sm p-4 text-sm">
          {{ error }}
        </div>

        <div v-else-if="data" class="space-y-2">

          <!-- Column header -->
          <div class="flex items-center px-4 pb-1">
            <div class="w-9 flex-shrink-0" />
            <div class="flex-1 min-w-0" />
            <div class="text-xs font-medium text-gray-400 dark:text-gray-500 text-right pr-10 whitespace-nowrap">
              <span v-if="data.lowerIsBetter" class="text-blue-500 mr-1">{{ t('leaderboard.lower_is_better') }}</span>
              {{ data.unit }}
            </div>
          </div>

          <!-- Empty state -->
          <div v-if="data.entries.length === 0" class="bg-gray-50 dark:bg-gray-700/50 rounded-sm p-8 text-center text-gray-400">
            <TrophyIcon class="h-10 w-10 mx-auto mb-3 opacity-30" />
            <p class="text-sm">{{ t('leaderboard.no_data') }}</p>
            <p class="text-xs mt-1">{{ t('leaderboard.no_data_subtitle') }}</p>
          </div>

          <!-- Top 10 -->
          <div
            v-for="entry in data.entries"
            :key="entry.rank"
            :class="['flex items-center gap-3 transition', podiumCardClass(entry.rank)]">
            <!-- Medal for top 3, plain number for rest -->
            <div class="w-9 flex-shrink-0 flex justify-center">
              <svg v-if="entry.rank <= 3" width="32" height="38" viewBox="0 0 32 38" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
                <!-- ribbon left -->
                <path d="M11 18 L6 34 L13 30 L16 36 L16 18 Z" :fill="MEDAL_COLORS[entry.rank].bail" opacity="0.85"/>
                <!-- ribbon right -->
                <path d="M21 18 L26 34 L19 30 L16 36 L16 18 Z" :fill="MEDAL_COLORS[entry.rank].bail" opacity="0.7"/>
                <!-- medal circle shadow -->
                <circle cx="16" cy="14" r="13" fill="rgba(0,0,0,0.15)"/>
                <!-- medal circle -->
                <circle cx="16" cy="13" r="13" :fill="MEDAL_COLORS[entry.rank].face"/>
                <!-- shine -->
                <ellipse cx="12" cy="8" rx="4" ry="2.5" :fill="MEDAL_COLORS[entry.rank].shine" opacity="0.5" transform="rotate(-20 12 8)"/>
                <!-- rank number -->
                <text x="16" y="18" text-anchor="middle" font-size="11" font-weight="900" font-family="system-ui, sans-serif" :fill="MEDAL_COLORS[entry.rank].text">{{ entry.rank }}</text>
              </svg>
              <span v-else class="text-sm font-medium text-gray-400 dark:text-gray-500">{{ entry.rank }}</span>
            </div>
            <div class="flex-1 min-w-0">
              <div :class="usernameClass(entry.rank)">{{ entry.username }}</div>
              <div class="text-xs text-gray-400 dark:text-gray-500 truncate">{{ entry.carLabel ?? '' }}</div>
              <div v-if="entry.kwhTotal != null && entry.sessionCount != null" class="text-xs text-gray-400 dark:text-gray-500 truncate">
                {{ t('leaderboard.cheapest_subtitle', { kwh: n(Number(entry.kwhTotal), { minimumFractionDigits: 1, maximumFractionDigits: 1 }), sessions: entry.sessionCount }) }}
              </div>
            </div>
            <div :class="['text-right flex-shrink-0', valueClass(entry.rank)]">{{ entry.value }}</div>
            <div :class="['w-10 flex-shrink-0 flex flex-col items-center text-xs font-semibold', rankDeltaClass(entry.rankDelta, entry.isNew)]">
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
            <div class="ring-2 ring-indigo-400 dark:ring-indigo-500 bg-white dark:bg-gray-800 rounded-sm px-4 py-3 flex items-center gap-3">
              <div class="text-sm font-bold text-indigo-500 dark:text-indigo-400 w-9 text-center flex-shrink-0">
                {{ data.ownEntry.rank }}
              </div>
              <div class="flex-1 min-w-0">
                <div class="text-sm font-semibold text-gray-900 dark:text-gray-100 truncate">{{ data.ownEntry.username }} <span class="text-xs font-normal text-indigo-500">{{ t('leaderboard.you') }}</span></div>
                <div class="text-xs text-gray-400 dark:text-gray-500 truncate">{{ data.ownEntry.carLabel ?? '' }}</div>
                <div v-if="data.ownEntry.kwhTotal != null && data.ownEntry.sessionCount != null" class="text-xs text-gray-400 dark:text-gray-500 truncate">
                  {{ t('leaderboard.cheapest_subtitle', { kwh: n(Number(data.ownEntry.kwhTotal), { minimumFractionDigits: 1, maximumFractionDigits: 1 }), sessions: data.ownEntry.sessionCount }) }}
                </div>
              </div>
              <div class="text-sm font-semibold text-gray-900 dark:text-gray-100 tabular-nums text-right flex-shrink-0">{{ data.ownEntry.value }}</div>
              <div :class="['w-10 flex-shrink-0 flex flex-col items-center text-xs font-semibold', rankDeltaClass(data.ownEntry.rankDelta, data.ownEntry.isNew)]">
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
            class="flex items-center gap-2 bg-indigo-50 dark:bg-indigo-900/20 border border-indigo-200 dark:border-indigo-700 rounded-sm p-3 text-sm text-indigo-700 dark:text-indigo-300 hover:bg-indigo-100 dark:hover:bg-indigo-900/30 transition mt-1">
            <ClockIcon class="h-4 w-4 flex-shrink-0 text-indigo-400" />
            <span>{{ t('leaderboard.coins_history') }}</span>
          </router-link>

          <!-- Month-end reward hint -->
          <div v-if="data.hasMonthEndReward" class="mt-4 rounded-sm border border-amber-200 dark:border-amber-700 bg-amber-50 dark:bg-amber-900/20 p-4">
            <div class="flex items-start gap-3">
              <TrophyIcon class="h-5 w-5 flex-shrink-0 text-amber-500 mt-0.5" />
              <div>
                <p class="text-sm font-semibold text-amber-900 dark:text-amber-200">
                  {{ t('leaderboard.top3_reward') }} <strong>50</strong> / <strong>30</strong> / <strong>15 Watt</strong>.
                </p>
                <p v-if="!data.ownEntry && authStore.isAuthenticated()" class="text-xs text-amber-700 dark:text-amber-400 mt-1">
                  {{ t('leaderboard.not_ranked') }}
                </p>
              </div>
            </div>
          </div>

        </div>
        </div>
        </Transition>
      </div>
    </div>
    <div class="h-8" />
  </div>
</template>

<style scoped>
.slide-left-enter-active,
.slide-left-leave-active,
.slide-right-enter-active,
.slide-right-leave-active {
  transition: transform 0.28s cubic-bezier(0.4, 0, 0.2, 1), opacity 0.28s ease;
}

.slide-left-enter-from  { transform: translateX(40px);  opacity: 0; }
.slide-left-leave-to    { transform: translateX(-40px); opacity: 0; }
.slide-right-enter-from { transform: translateX(-40px); opacity: 0; }
.slide-right-leave-to   { transform: translateX(40px);  opacity: 0; }
</style>
