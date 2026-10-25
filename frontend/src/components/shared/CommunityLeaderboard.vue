<script setup lang="ts">
import { ref, computed, onMounted, watch, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '../../stores/auth'
import apiClient from '../../api/axios'
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
  SunIcon,
  ExclamationCircleIcon,
} from '@heroicons/vue/24/outline'
import ChoiceChip from './ChoiceChip.vue'

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

const props = withDefaults(defineProps<{
  /** Internen Karten-Header (Titel + Periode) zeigen. Auf der Landing Page false, da dort eine Marketing-Ueberschrift einleitet. */
  showHeader?: boolean
  /** 'current' (Standard) = laufender Monat; 'previous' = Endstand des Vormonats. */
  month?: 'current' | 'previous'
  /** Maximale Anzahl angezeigter Plaetze. Standard: alle gelieferten (Top 10). */
  maxEntries?: number
  /** Handy: Kategorie-Chips fest über der Bottom-Nav (Daumenreichweite). Nur in der App, die Landing hat keine Bottom-Nav. */
  dockChips?: boolean
}>(), { showHeader: true, month: 'current', dockChips: false })

/** Meldet nach jedem Laden, ob verwertbare Eintraege vorliegen. Aufrufer entscheiden selbst (z.B. Sektion ausblenden). */
const emit = defineEmits<{ available: [hasData: boolean] }>()

const { t, locale, n } = useI18n()

// Kategoriefarbe nur am Icon: bewusste Ausnahme von der Grau-Regel der App-CI (siehe docs/design/ci.md).
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

// Die Chip-Leiste scrollt auf dem Handy seitlich: der gewaehlte Chip rueckt nach dem Wechsel ins Bild.
const chipBar = ref<HTMLElement | null>(null)
watch(activeCategory, async () => {
  await nextTick()
  chipBar.value?.querySelector<HTMLElement>('[aria-pressed="true"]')?.scrollIntoView?.({ inline: 'nearest', block: 'nearest', behavior: 'smooth' })
})

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
    const params = props.month === 'previous' ? { monthsBack: 1 } : undefined
    const res = await apiClient.get(`/public/leaderboard/${category}`, { params })
    data.value = res.data
  } catch (e) {
    error.value = t('common.error_load')
  } finally {
    loading.value = false
    emit('available', !error.value && (data.value?.entries.length ?? 0) > 0)
  }
}

watch(activeCategory, (cat) => load(cat))
onMounted(() => load(activeCategory.value))

function setCategory(key: string) {
  activeCategory.value = key
}

function rankDeltaLabel(delta: number | null, isNew: boolean): string {
  if (isNew) return t('leaderboard.new_entry')
  if (delta === null || delta === 0) return '-'
  return delta > 0 ? `+${delta}` : `${delta}`
}

// Neutrale Plakette statt Medaille (App-CI: keine Farbcodes); die Rangfolge traegt Groesse und Flaeche.
function plaqueClass(rank: number): string {
  if (rank === 1) return 'grid h-10 w-10 place-items-center rounded-full bg-gray-900 text-base font-extrabold text-white dark:bg-gray-100 dark:text-gray-900'
  if (rank <= 3) return 'grid h-9 w-9 place-items-center rounded-full border-2 border-gray-900 text-sm font-bold text-gray-900 dark:border-gray-300 dark:text-gray-100'
  return 'w-9 text-center text-sm font-semibold text-gray-500 dark:text-gray-400'
}

function usernameClass(rank: number): string {
  if (rank === 1) return 'truncate text-lg font-bold text-gray-900 dark:text-gray-50'
  if (rank <= 3) return 'truncate text-base font-semibold text-gray-900 dark:text-gray-100'
  return 'truncate text-base font-medium text-gray-800 dark:text-gray-200'
}

function valueClass(rank: number): string {
  if (rank === 1) return 'text-[26px] font-extrabold tracking-tight text-gray-900 tabular-nums dark:text-gray-50'
  if (rank <= 3) return 'text-xl font-bold text-gray-900 tabular-nums dark:text-gray-100'
  return 'text-base font-semibold text-gray-700 tabular-nums dark:text-gray-300'
}

const periodLabel = computed(() => {
  if (!data.value) return ''
  const [year, month] = data.value.period.split('-')
  const date = new Date(Number(year), Number(month) - 1, 1)
  return date.toLocaleString(locale.value === 'en' ? 'en-GB' : 'de-DE', { month: 'long', year: 'numeric' })
})

// Vormonats-Endstand: ehrlich kennzeichnen, auch wenn der interne Header ausgeblendet ist.
const isFinalStandings = computed(() => props.month === 'previous' && !!data.value)

// ownEntry kommt nur, wenn der Nutzer ausserhalb der Top-Plaetze steht; in den Top-Plaetzen erkennt man ihn am Namen.
const isOwn = (entry: LeaderboardEntry) => !!authStore.user?.username && entry.username === authStore.user.username
const inTopEntries = computed(() => (data.value?.entries ?? []).some(isOwn))

// Optional auf Top-N begrenzen (Landing zeigt nur Top 5, in-app weiter Top 10).
const displayedEntries = computed(() => {
  const entries = data.value?.entries ?? []
  return props.maxEntries ? entries.slice(0, props.maxEntries) : entries
})
</script>

<template>
  <!-- Inhalt der Rangliste; Kante und Rundung setzt die einbettende Seite (App: ab md, Landing: immer). -->
  <!-- Angedockte Chips liegen auf dem Handy fest unten: Platz am Ende freihalten, damit die letzte Zeile nicht darunter verschwindet. -->
  <div class="grid min-w-0 gap-5 bg-white px-4 py-6 md:p-8 dark:bg-gray-900" :class="{ 'max-md:pb-24': dockChips }">
    <p v-if="isFinalStandings" class="text-[12px] font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400">
      {{ t('leaderboard.final_standings', { month: periodLabel }) }}
    </p>

    <!-- Kopf (auf der Landing aus, dort führt die Marketing-Überschrift ein) -->
    <header v-if="showHeader" class="grid gap-1">
      <h1 class="text-[28px] font-bold leading-[1.1] text-gray-900 md:text-[36px] dark:text-gray-50">{{ t('leaderboard.title') }}</h1>
      <p class="text-sm text-gray-600 dark:text-gray-400">
        {{ t('leaderboard.subtitle') }}<span v-if="data"> · {{ periodLabel }}</span>
      </p>
    </header>

    <!-- Kategorien: Handy seitlich scrollbar bis an den Rand (in der App fest über der Bottom-Nav), ab md umbrechend und zentriert -->
    <div class="grid gap-2">
      <!-- Angedockt stehen die Chips unten; oben nennt der Titel die gewählte Kategorie -->
      <h2 v-if="dockChips" class="flex items-center gap-2 text-[18px] font-bold tracking-tight text-gray-900 md:hidden dark:text-gray-100">
        <component :is="activeCategoryMeta?.icon" class="h-5 w-5 shrink-0" :class="activeCategoryMeta?.color" aria-hidden="true" />
        {{ activeCategoryMeta?.label }}
      </h2>
      <div :class="dockChips
        ? 'fixed inset-x-0 bottom-[calc(3.5rem+env(safe-area-inset-bottom))] z-30 border-t border-gray-200 bg-white/95 pt-2.5 pb-6 backdrop-blur md:static md:z-auto md:border-0 md:bg-transparent md:p-0 md:backdrop-blur-none dark:border-gray-800 dark:bg-gray-900/95 md:dark:bg-transparent'
        : ''">
        <div ref="chipBar" role="group" :aria-label="t('leaderboard.title')"
          class="scrollbar-hide flex snap-x scroll-px-4 gap-2 overflow-x-auto px-4 pt-0.5 pb-1.5 md:mx-0 md:flex-wrap md:justify-center md:px-0"
          :class="dockChips ? 'mx-0' : '-mx-4'">
          <ChoiceChip v-for="cat in CATEGORIES" :key="cat.key" size="sm" :active="cat.key === activeCategory"
            class="relative snap-start before:absolute before:inset-x-0 before:-inset-y-1 before:content-['']"
            @click="jumpToCategory(cat.key)">
            <component :is="cat.icon" class="h-4 w-4" :class="cat.color" aria-hidden="true" />{{ cat.label }}
          </ChoiceChip>
        </div>
      </div>
      <p class="text-sm text-gray-600 md:text-center dark:text-gray-400">{{ activeCategoryMeta?.desc }}</p>
    </div>

    <div class="overflow-hidden">
      <Transition :name="slideDirection === 'left' ? 'slide-left' : 'slide-right'" mode="out-in">
      <div :key="activeCategory">

        <div v-if="loading" class="grid animate-pulse gap-3" aria-busy="true" :aria-label="t('leaderboard.loading')">
          <div v-for="i in 5" :key="i" class="h-11 rounded bg-gray-200 dark:bg-gray-700" />
        </div>

        <div v-else-if="error" role="alert" class="flex items-start gap-2 rounded-xl bg-gray-100 px-3 py-2.5 text-sm font-semibold text-gray-900 dark:bg-gray-800/60 dark:text-gray-100">
          <ExclamationCircleIcon class="mt-0.5 h-5 w-5 shrink-0 text-gray-700 dark:text-gray-300" aria-hidden="true" />
          <span>{{ error }}</span>
        </div>

        <div v-else-if="data" class="grid gap-3">
          <div v-if="data.entries.length === 0" class="rounded-xl bg-gray-100 px-4 py-6 text-center dark:bg-gray-800/60">
            <p class="font-bold text-gray-900 dark:text-gray-100">{{ t('leaderboard.no_data') }}</p>
            <p class="mt-1 text-sm text-gray-600 dark:text-gray-300">{{ t('leaderboard.no_data_subtitle') }}</p>
          </div>

          <template v-else>
            <!-- Spaltenkopf: Einheit einmal statt in jeder Zeile -->
            <p class="flex items-baseline justify-end gap-2 pr-11 text-[12px] font-semibold text-gray-500 dark:text-gray-400">
              <span v-if="data.lowerIsBetter" class="uppercase tracking-wide">{{ t('leaderboard.lower_is_better') }}</span>
              <span class="text-[13px]">{{ data.unit }}</span>
            </p>

            <ol class="divide-y divide-gray-200 dark:divide-gray-700">
              <li v-for="entry in displayedEntries" :key="entry.rank"
                :data-testid="isOwn(entry) ? 'leaderboard-own' : undefined"
                class="flex items-center gap-3"
                :class="[entry.rank === 1 ? 'py-3.5' : 'py-2.5', isOwn(entry) ? '-mx-2 rounded-xl bg-gray-100 px-2 dark:bg-gray-800/60' : 'first:pt-0 last:pb-0']">
                <div class="flex w-10 shrink-0 justify-center">
                  <span :class="plaqueClass(entry.rank)">{{ entry.rank }}</span>
                </div>
                <div class="min-w-0 flex-1">
                  <div :class="usernameClass(entry.rank)">
                    {{ entry.username }}
                    <span v-if="isOwn(entry)" class="ml-1 text-[11.5px] font-bold uppercase tracking-wide text-gray-500 dark:text-gray-400">{{ t('leaderboard.you') }}</span>
                  </div>
                  <div v-if="entry.carLabel" class="truncate text-[13px] text-gray-500 dark:text-gray-400">{{ entry.carLabel }}</div>
                  <div v-if="entry.kwhTotal != null && entry.sessionCount != null" class="truncate text-[12.5px] text-gray-500 tabular-nums dark:text-gray-400">
                    {{ t('leaderboard.cheapest_subtitle', { kwh: n(Number(entry.kwhTotal), { minimumFractionDigits: 1, maximumFractionDigits: 1 }), sessions: entry.sessionCount }) }}
                  </div>
                </div>
                <div class="shrink-0 text-right" :class="valueClass(entry.rank)">{{ entry.value }}</div>
                <div class="flex w-8 shrink-0 flex-col items-center text-[11px] font-semibold text-gray-500 tabular-nums dark:text-gray-400">
                  <ArrowUpIcon v-if="entry.rankDelta && entry.rankDelta > 0" class="h-3.5 w-3.5" aria-hidden="true" />
                  <ArrowDownIcon v-else-if="entry.rankDelta && entry.rankDelta < 0" class="h-3.5 w-3.5" aria-hidden="true" />
                  <MinusIcon v-else-if="!entry.isNew && (entry.rankDelta === 0 || entry.previousRank !== null)" class="h-3.5 w-3.5" aria-hidden="true" />
                  <SparklesIcon v-else-if="entry.isNew" class="h-3.5 w-3.5" aria-hidden="true" />
                  <span v-if="rankDeltaLabel(entry.rankDelta, entry.isNew) !== '-'">{{ rankDeltaLabel(entry.rankDelta, entry.isNew) }}</span>
                </div>
              </li>
            </ol>
          </template>

          <!-- Eigener Platz, wenn nicht in den Top 10: unten angestellt, Abstand als Punkte -->
          <p v-if="data.ownEntry" class="text-center text-sm font-bold tracking-[0.3em] text-gray-400 dark:text-gray-500" aria-hidden="true">···</p>
          <div v-if="data.ownEntry" data-testid="leaderboard-own" class="flex items-center gap-3 rounded-2xl bg-white px-3 py-3 edge-card dark:bg-gray-800/60">
            <div class="flex w-10 shrink-0 justify-center">
              <span class="text-sm font-bold text-gray-900 tabular-nums dark:text-gray-100">{{ data.ownEntry.rank }}</span>
            </div>
            <div class="min-w-0 flex-1">
              <div class="truncate text-sm font-semibold text-gray-900 dark:text-gray-100">
                {{ data.ownEntry.username }}
                <span class="ml-1 text-[11.5px] font-bold uppercase tracking-wide text-gray-500 dark:text-gray-400">{{ t('leaderboard.you') }}</span>
              </div>
              <div v-if="data.ownEntry.carLabel" class="truncate text-[13px] text-gray-500 dark:text-gray-400">{{ data.ownEntry.carLabel }}</div>
              <div v-if="data.ownEntry.kwhTotal != null && data.ownEntry.sessionCount != null" class="truncate text-[12.5px] text-gray-500 tabular-nums dark:text-gray-400">
                {{ t('leaderboard.cheapest_subtitle', { kwh: n(Number(data.ownEntry.kwhTotal), { minimumFractionDigits: 1, maximumFractionDigits: 1 }), sessions: data.ownEntry.sessionCount }) }}
              </div>
            </div>
            <div class="shrink-0 text-right text-base font-bold text-gray-900 tabular-nums dark:text-gray-100">{{ data.ownEntry.value }}</div>
            <div class="flex w-8 shrink-0 flex-col items-center text-[11px] font-semibold text-gray-500 tabular-nums dark:text-gray-400">
              <ArrowUpIcon v-if="data.ownEntry.rankDelta && data.ownEntry.rankDelta > 0" class="h-3.5 w-3.5" aria-hidden="true" />
              <ArrowDownIcon v-else-if="data.ownEntry.rankDelta && data.ownEntry.rankDelta < 0" class="h-3.5 w-3.5" aria-hidden="true" />
              <MinusIcon v-else-if="!data.ownEntry.isNew && data.ownEntry.previousRank !== null" class="h-3.5 w-3.5" aria-hidden="true" />
              <SparklesIcon v-else-if="data.ownEntry.isNew" class="h-3.5 w-3.5" aria-hidden="true" />
              <span v-if="rankDeltaLabel(data.ownEntry.rankDelta, data.ownEntry.isNew) !== '-'">{{ rankDeltaLabel(data.ownEntry.rankDelta, data.ownEntry.isNew) }}</span>
            </div>
          </div>

          <!-- Footer: Standard = Bonus-Hinweis; Aufrufer (z.B. Landing) ersetzt via #footer-Slot -->
          <slot name="footer">
            <div v-if="data.hasMonthEndReward" class="flex items-start gap-3 rounded-xl bg-gray-100 p-3 dark:bg-gray-800/60">
              <TrophyIcon class="mt-0.5 h-5 w-5 shrink-0 text-gray-700 dark:text-gray-300" aria-hidden="true" />
              <div>
                <p class="text-sm text-gray-900 dark:text-gray-100">
                  {{ t('leaderboard.top3_reward') }} <b class="tabular-nums">50</b> / <b class="tabular-nums">30</b> / <b class="tabular-nums">15 Watt</b>.
                </p>
                <p v-if="!data.ownEntry && !inTopEntries && authStore.isAuthenticated()" class="mt-1 text-[13px] text-gray-600 dark:text-gray-400">
                  {{ t('leaderboard.not_ranked') }}
                </p>
              </div>
            </div>
          </slot>
        </div>
      </div>
      </Transition>
    </div>
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

@media (prefers-reduced-motion: reduce) {
  .slide-left-enter-active,
  .slide-left-leave-active,
  .slide-right-enter-active,
  .slide-right-leave-active { transition: none; }
}
</style>
