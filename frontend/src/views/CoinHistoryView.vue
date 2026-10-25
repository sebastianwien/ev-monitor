<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import api from '../api/axios'
import { useCoinStore } from '../stores/coins'
import { TrophyIcon, ArrowUpIcon, ArrowDownIcon, MinusIcon, SparklesIcon, ExclamationCircleIcon, ChevronDownIcon } from '@heroicons/vue/24/outline'
import ModelBox from '../components/models/ModelBox.vue'
import Collapse from '../components/shared/Collapse.vue'

const { t } = useI18n()
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

const legendOpen = ref(false)

function toggleStandings() {
  standingsOpen.value = !standingsOpen.value
  localStorage.setItem('standings-open', String(standingsOpen.value))
}

function deltaLabel(delta: number | null, isNew: boolean): string {
  if (isNew) return t('coins.delta_new')
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

const coinTypeLabel = computed<Record<string, string>>(() => ({
  ACHIEVEMENT_COIN: t('coins.type_achievement'),
  SOCIAL_COIN: t('coins.type_social'),
  GREEN_COIN: t('coins.type_green'),
  DISTANCE_COIN: t('coins.type_distance'),
  STREAK_COIN: t('coins.type_streak'),
  EFFICIENCY_COIN: t('coins.type_efficiency')
}))

const formatDate = (iso: string): string => {
  return new Date(iso).toLocaleString(undefined, {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  })
}

// Abzüge (gelöschter Ladevorgang) kommen negativ: echtes Minus statt "+-5".
const signedAmount = (amount: number): string => amount < 0 ? `−${Math.abs(amount)}` : `+${amount}`

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
    error.value = err.response?.data?.message || t('coins.error_load')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchLogs()
  fetchStandings()
})

// Legende der Watt-Regeln: Betrag pro Aktion, gruppiert wie im Belohnungssystem.
const LEGEND = [
  { title: 'coins.legend_logs', items: [['coins.legend_log_first', '+25'], ['coins.legend_log_more', '+5'], ['coins.legend_log_ocr_first', '+27'], ['coins.legend_log_ocr', '+7'], ['coins.legend_log_delete', '−']] },
  { title: 'coins.legend_imports', items: [['coins.legend_sprit_connect', '+50'], ['coins.legend_sprit_log', '+2'], ['coins.legend_tesla_connect', '+50'], ['coins.legend_tesla_logger', '+20'], ['coins.legend_tesla_logger_log', '+2'], ['coins.legend_tesla_sync_log', '+5']] },
  { title: 'coins.legend_cars', items: [['coins.legend_car_first', '+20'], ['coins.legend_car_more', '+5'], ['coins.legend_car_image_first', '+15'], ['coins.legend_car_image_public', '+10']] },
  { title: 'coins.legend_community', items: [['coins.legend_referral', '+100'], ['coins.legend_referral_bonus', '+25']] },
] as const

// Einzige gruene Flaeche der Seite: der Weg zur Bestenliste.
const BTN_PRIMARY = 'btn-3d cta-shadow-sm inline-flex min-h-11 w-full items-center justify-center gap-2 rounded-xl border-2 border-gray-900 bg-green-600 px-4 text-sm font-semibold text-white transition-colors hover:bg-green-700 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-green-600 dark:border-transparent dark:shadow-none!'
const LABEL = 'text-[12px] font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400'
</script>

<template>
  <div class="grid gap-2 pb-6 max-md:-mt-[var(--ticker-lasche-h)] md:mx-auto md:max-w-2xl md:gap-4 md:p-6">
    <!-- Kopf: Kontostand und Weg zur Rangliste -->
    <ModelBox class="max-md:pt-[calc(1.5rem+var(--ticker-lasche-h))]">
      <header class="flex items-end justify-between gap-3">
        <h1 class="text-[28px] font-bold leading-[1.1] text-gray-900 md:text-[36px] dark:text-gray-50">{{ t('coins.title') }}</h1>
        <p class="flex shrink-0 items-baseline gap-1 leading-none">
          <b class="text-[26px] font-extrabold tracking-tight text-gray-900 tabular-nums dark:text-gray-50">{{ coinStore.balance }}</b>
          <span class="text-[13px] font-semibold text-gray-500 dark:text-gray-400">{{ t('coins.unit') }}</span>
        </p>
      </header>
      <router-link to="/leaderboard" :class="BTN_PRIMARY">
        <TrophyIcon class="h-4 w-4" aria-hidden="true" />
        {{ t('coins.to_leaderboard') }}
      </router-link>
    </ModelBox>

    <!-- Eigene Plätze im laufenden Monat -->
    <ModelBox>
      <button type="button" class="-my-2 flex min-h-11 w-full items-center justify-between gap-3 text-left" :aria-expanded="standingsOpen" @click="toggleStandings">
        <h2 class="text-[18px] font-bold tracking-tight text-gray-900 md:text-[20px] dark:text-gray-100">{{ t('coins.ranking_title') }}</h2>
        <span class="flex items-center gap-1 text-[13px] font-semibold text-gray-500 dark:text-gray-400">
          {{ standingsOpen ? t('coins.collapse') : t('coins.expand') }}
          <ChevronDownIcon class="h-4 w-4 transition-transform duration-300 ease-out motion-reduce:transition-none" :class="standingsOpen ? 'rotate-180' : ''" aria-hidden="true" />
        </span>
      </button>
      <Collapse :open="standingsOpen">
        <div v-if="standingsLoading" class="grid animate-pulse gap-2" aria-busy="true">
          <div v-for="i in 4" :key="i" class="h-6 rounded bg-gray-200 dark:bg-gray-700" />
        </div>
        <ul v-else class="divide-y divide-gray-200 dark:divide-gray-700">
          <li v-for="s in standings" :key="s.category" class="flex items-center gap-3 py-2.5 first:pt-0 last:pb-0">
            <span class="min-w-0 flex-1 truncate text-sm font-semibold text-gray-900 dark:text-gray-100">{{ s.displayName }}</span>
            <span v-if="s.value !== null" class="text-[13px] text-gray-500 tabular-nums dark:text-gray-400">{{ s.value }} {{ s.unit }}</span>
            <b v-if="s.rank !== null" class="w-10 text-right text-gray-900 tabular-nums dark:text-gray-100">#{{ s.rank }}</b>
            <span v-else class="w-10 text-right text-gray-400 dark:text-gray-500">-</span>
            <span class="flex w-9 flex-col items-center text-[11px] font-semibold text-gray-500 tabular-nums dark:text-gray-400">
              <template v-if="s.rank !== null">
                <ArrowUpIcon v-if="s.rankDelta && s.rankDelta > 0" class="h-3.5 w-3.5" aria-hidden="true" />
                <ArrowDownIcon v-else-if="s.rankDelta && s.rankDelta < 0" class="h-3.5 w-3.5" aria-hidden="true" />
                <SparklesIcon v-else-if="s.isNew" class="h-3.5 w-3.5" aria-hidden="true" />
                <MinusIcon v-else class="h-3.5 w-3.5" aria-hidden="true" />
                <span v-if="deltaLabel(s.rankDelta, s.isNew) !== '-'">{{ deltaLabel(s.rankDelta, s.isNew) }}</span>
              </template>
            </span>
          </li>
        </ul>
      </Collapse>
    </ModelBox>

    <!-- Legende: wofür es Watt gibt -->
    <ModelBox>
      <button type="button" class="-my-2 flex min-h-11 w-full items-center justify-between gap-3 text-left" :aria-expanded="legendOpen" @click="legendOpen = !legendOpen">
          <h2 class="text-[18px] font-bold tracking-tight text-gray-900 md:text-[20px] dark:text-gray-100">{{ t('coins.legend_title') }}</h2>
          <ChevronDownIcon class="h-4 w-4 text-gray-500 transition-transform duration-300 ease-out motion-reduce:transition-none" :class="legendOpen ? 'rotate-180' : ''" aria-hidden="true" />
      </button>
      <Collapse :open="legendOpen">
        <div class="grid gap-5 pt-4">
          <div v-for="group in LEGEND" :key="group.title" class="grid gap-1.5">
            <p :class="LABEL">{{ t(group.title) }}</p>
            <ul class="grid gap-1 text-sm text-gray-700 dark:text-gray-300">
              <li v-for="[key, amount] in group.items" :key="key" class="flex justify-between gap-3">
                <span>{{ t(key) }}</span>
                <b class="shrink-0 text-gray-900 tabular-nums dark:text-gray-100">{{ amount }} {{ t('coins.unit') }}</b>
              </li>
            </ul>
          </div>
        </div>
      </Collapse>
    </ModelBox>

    <!-- Verlauf -->
    <ModelBox>
      <div v-if="error" role="alert" class="flex items-start gap-2 rounded-xl bg-gray-100 px-3 py-2.5 text-sm font-semibold text-gray-900 dark:bg-gray-800/60 dark:text-gray-100">
        <ExclamationCircleIcon class="mt-0.5 h-5 w-5 shrink-0 text-gray-700 dark:text-gray-300" aria-hidden="true" />
        <span>{{ error }}</span>
      </div>

      <div v-if="loading" class="grid animate-pulse gap-3" aria-busy="true" :aria-label="t('coins.loading')">
        <div v-for="i in 5" :key="i" class="h-10 rounded bg-gray-200 dark:bg-gray-700" />
      </div>

      <div v-else-if="logs.length === 0 && !error" class="py-4 text-center">
        <p class="text-lg font-bold text-gray-900 dark:text-gray-50">{{ t('coins.empty_title') }}</p>
        <p class="mt-1 text-sm text-gray-600 dark:text-gray-300">{{ t('coins.empty_desc') }}</p>
      </div>

      <ul v-else class="divide-y divide-gray-200 dark:divide-gray-700">
        <li v-for="log in logs" :key="log.id" class="flex items-center justify-between gap-3 py-3 first:pt-0 last:pb-0">
          <div class="min-w-0">
            <p class="text-sm font-semibold text-gray-900 dark:text-gray-100">{{ log.actionDescription }}</p>
            <p class="mt-0.5 text-[13px] text-gray-500 tabular-nums dark:text-gray-400">
              {{ coinTypeLabel[log.coinType] || log.coinType }} · {{ formatDate(log.createdAt) }}
            </p>
          </div>
          <b class="shrink-0 text-base text-gray-900 tabular-nums dark:text-gray-100">{{ signedAmount(log.amount) }}</b>
        </li>
      </ul>
    </ModelBox>
  </div>
</template>
