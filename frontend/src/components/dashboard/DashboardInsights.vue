<script setup lang="ts">
import { ref, computed, watch, nextTick, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ChevronDownIcon } from '@heroicons/vue/24/outline'
import { phantomEur } from '../../utils/phantomDrain'
import { useSlideTransition } from '../../composables/useSlideTransition'

const { onEnter, onAfterEnter, onLeave, onAfterLeave } = useSlideTransition()
const LS_KEY = 'dashboard_insights_collapsed'
const LS_TAB_KEY = 'dashboard_insights_tab'
const collapsed = ref(localStorage.getItem(LS_KEY) === 'true')
function toggleCollapsed() {
  collapsed.value = !collapsed.value
  localStorage.setItem(LS_KEY, String(collapsed.value))
}

function selectTab(tab: Tab, e: MouseEvent) {
  activeTab.value = tab
  const el = e.currentTarget as HTMLElement | null
  if (el) el.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'nearest' })
}

const props = defineProps<{
  entries: any[]
  selectedCar: any
  selectedTimeRange: string
  customStartDate: string | null
  customEndDate: string | null
}>()

const { t } = useI18n()

type Tab = 'donut' | 'nights' | 'calendar' | 'routes'
const VALID_TABS: Tab[] = ['donut', 'nights', 'calendar', 'routes']
const storedTab = localStorage.getItem(LS_TAB_KEY) as Tab | null
const activeTab = ref<Tab>(storedTab && VALID_TABS.includes(storedTab) ? storedTab : 'donut')

// ── Animation ────────────────────────────────────────────────────────────────

const animateDonut = ref(false)
const animateBars = ref(false)
const animateRoutes = ref(false)

// nextTick ensures Vue has flushed the DOM with the "off" state,
// rAF ensures the browser has actually painted it before we flip to "on".
function scheduleAnim(fn: () => void) {
  nextTick(() => requestAnimationFrame(() => fn()))
}

watch(activeTab, (tab) => {
  localStorage.setItem(LS_TAB_KEY, tab)
  animateDonut.value = false
  animateBars.value = false
  animateRoutes.value = false
  scheduleAnim(() => {
    if (tab === 'donut') animateDonut.value = true
    if (tab === 'nights') animateBars.value = true
    if (tab === 'routes') animateRoutes.value = true
  })
})

onMounted(() => scheduleAnim(() => {
  if (activeTab.value === 'donut') animateDonut.value = true
  else if (activeTab.value === 'nights') animateBars.value = true
  else if (activeTab.value === 'routes') animateRoutes.value = true
}))

// ── Date range ───────────────────────────────────────────────────────────────

const dateRange = computed<{ from: Date | null; to: Date | null }>(() => {
  const now = new Date()
  const endOfDay = (d: Date) => { const r = new Date(d); r.setHours(23, 59, 59, 999); return r }
  switch (props.selectedTimeRange) {
    case 'THIS_MONTH':
      return { from: new Date(now.getFullYear(), now.getMonth(), 1), to: null }
    case 'LAST_MONTH': {
      const from = new Date(now.getFullYear(), now.getMonth() - 1, 1)
      return { from, to: endOfDay(new Date(now.getFullYear(), now.getMonth(), 0)) }
    }
    case 'LAST_3_MONTHS': { const f = new Date(now); f.setMonth(f.getMonth() - 3); return { from: f, to: null } }
    case 'LAST_6_MONTHS': { const f = new Date(now); f.setMonth(f.getMonth() - 6); return { from: f, to: null } }
    case 'LAST_12_MONTHS': { const f = new Date(now); f.setFullYear(f.getFullYear() - 1); return { from: f, to: null } }
    case 'THIS_YEAR':
      return { from: new Date(now.getFullYear(), 0, 1), to: null }
    case 'CUSTOM': {
      const from = props.customStartDate ? new Date(props.customStartDate) : null
      const to = props.customEndDate ? endOfDay(new Date(props.customEndDate)) : null
      return { from, to }
    }
    default: return { from: null, to: null }
  }
})

function entryDate(e: any): Date | null {
  const s = e._isTrip ? e.tripStartedAt : e.loggedAt
  return s ? new Date(s) : null
}

const filteredEntries = computed(() => {
  const { from, to } = dateRange.value
  if (!from && !to) return props.entries
  return props.entries.filter(e => {
    const d = entryDate(e)
    if (!d) return false
    if (from && d < from) return false
    if (to && d > to) return false
    return true
  })
})

// ── DONUT: always all-time ───────────────────────────────────────────────────

const allChargedKwh = computed(() =>
  filteredEntries.value
    .filter(e => !e._isTrip)
    .reduce((s, e) => s + (e._isLadegruppe ? (e._totalKwh ?? 0) : (e.kwhCharged ?? e.kwhAtVehicle ?? 0)), 0)
)
const allPhantomKwh = computed(() =>
  filteredEntries.value.reduce((s, e) => s + (e._phantomDrain?.kwh ?? 0), 0)
)
const allLossKwh = computed(() =>
  filteredEntries.value
    .filter(e => !e._isTrip && !e._isLadegruppe && e.kwhCharged != null && e.kwhAtVehicle != null)
    .reduce((s, e) => s + Math.max(0, (e.kwhCharged ?? 0) - (e.kwhAtVehicle ?? 0)), 0)
)
const phantomPct = computed(() => allChargedKwh.value > 0 ? allPhantomKwh.value / allChargedKwh.value * 100 : 0)
const lossPct = computed(() => allChargedKwh.value > 0 ? allLossKwh.value / allChargedKwh.value * 100 : 0)
const drivenPct = computed(() => Math.max(0, 100 - phantomPct.value - lossPct.value))

const C = 326.7
const drivenArcLen = computed(() => drivenPct.value / 100 * C)
const phantomArcLen = computed(() => phantomPct.value / 100 * C)
const lossArcLen = computed(() => lossPct.value / 100 * C)

// Animated arc values: 0 → final (driven by animateDonut ref).
// Set via :style so they are CSS properties - required for transition to fire.
const animDrivenArc = computed(() => animateDonut.value ? drivenArcLen.value : 0)
const animPhantomArc = computed(() => animateDonut.value ? phantomArcLen.value : 0)
const animLossArc = computed(() => animateDonut.value ? lossArcLen.value : 0)

// Phantom + loss positional offsets are always static (no animation needed)
const phantomDashoffset = computed(() => -drivenArcLen.value)
const lossDashoffset = computed(() => -(drivenArcLen.value + phantomArcLen.value))

const phantomCostEur = computed(() => phantomEur(allPhantomKwh.value))

// Bar widths (0% -> actual %) driven by animateDonut ref
const drivenBarWidth = computed(() => animateDonut.value ? `${drivenPct.value}%` : '0%')
const phantomBarWidth = computed(() => animateDonut.value ? `${phantomPct.value}%` : '0%')
const lossBarWidth = computed(() => animateDonut.value ? `${lossPct.value}%` : '0%')

// ── STANDVERLUSTE: filtered ──────────────────────────────────────────────────

const drainEvents = computed(() =>
  filteredEntries.value
    .filter(e => e._phantomDrain != null)
    .map(e => ({
      kwh: e._phantomDrain!.kwh as number,
      hours: Math.round(e._phantomDrain!.durationMs / 360000) / 10,
      date: entryDate(e),
    }))
    .sort((a, b) => b.kwh - a.kwh)
    .slice(0, 6)
)
const maxDrainKwh = computed(() => drainEvents.value[0]?.kwh || 1)
const totalDrainFiltered = computed(() =>
  filteredEntries.value.reduce((s, e) => s + (e._phantomDrain?.kwh ?? 0), 0)
)

// ── CALENDAR: trips grouped by ISO week + weekday ────────────────────────────

type DayData = { count: number; km: number }
type WeekRow = { label: string; monday: Date; days: (DayData | null)[] }

const calendarWeeks = computed((): WeekRow[] => {
  const trips = filteredEntries.value.filter(e => e._isTrip)
  if (trips.length === 0) return []

  // Kalender immer auf aktuelle Woche verankert - zeigt die letzten 2 KWs (heutige + eine davor)
  const NUM_WEEKS = 2
  const currentMonday = mondayOf(new Date())
  const weeks: WeekRow[] = []
  for (let i = NUM_WEEKS - 1; i >= 0; i--) {
    const monday = new Date(currentMonday)
    monday.setDate(currentMonday.getDate() - i * 7)
    weeks.push({
      label: isoWeekInfo(monday).weekLabel,
      monday,
      days: Array(7).fill(null) as (DayData | null)[],
    })
  }

  // Trip-Daten in passende Kalenderzellen einfuellen
  for (const trip of trips) {
    const d = entryDate(trip)
    if (!d) continue
    const tripMondayTs = mondayOf(d).getTime()
    const week = weeks.find(w => w.monday.getTime() === tripMondayTs)
    if (!week) continue
    const dayIdx = (d.getDay() + 6) % 7
    const prev = week.days[dayIdx] ?? { count: 0, km: 0 }
    week.days[dayIdx] = { count: prev.count + 1, km: prev.km + (trip.distanceKm ?? 0) }
  }

  return weeks
})

const maxCellKm = computed(() => {
  let max = 0
  for (const w of calendarWeeks.value)
    for (const d of w.days) if (d && d.km > max) max = d.km
  return max
})
const tripCount = computed(() => filteredEntries.value.filter(e => e._isTrip).length)
const totalTripKm = computed(() =>
  filteredEntries.value.filter(e => e._isTrip).reduce((s, e) => s + (e.distanceKm ?? 0), 0)
)
const avgTripKm = computed(() => tripCount.value > 0 ? totalTripKm.value / tripCount.value : 0)

// ── ROUTES: trip distribution by distance category ───────────────────────────

const ROUTE_COLORS: Record<string, string> = { short: '#f59e0b', mid: '#3b82f6', long: '#6366f1' }
function routeBarBg(key: string): string { return ROUTE_COLORS[key] ?? '#6366f1' }

const routeStats = computed(() => {
  const trips = filteredEntries.value.filter((e: any) => e._isTrip && e.distanceKm != null)
  if (trips.length === 0) return null
  const totalKm = trips.reduce((s: number, e: any) => s + Number(e.distanceKm), 0)
  const hasEnergyData = trips.some((e: any) => e.estimatedConsumedKwh != null)
  const totalEnergy = trips.reduce((s: number, e: any) =>
    s + (e.estimatedConsumedKwh != null ? Number(e.estimatedConsumedKwh) : 0), 0)
  const categories = [
    { key: 'short', min: 0, max: 5 },
    { key: 'mid',   min: 5, max: 10 },
    { key: 'long',  min: 10, max: Infinity },
  ].map((cat, i) => {
    const catTrips = trips.filter((e: any) => {
      const km = Number(e.distanceKm)
      return km >= cat.min && km < cat.max
    })
    const catKm = catTrips.reduce((s: number, e: any) => s + Number(e.distanceKm), 0)
    const catEnergy = catTrips.reduce((s: number, e: any) =>
      s + (e.estimatedConsumedKwh != null ? Number(e.estimatedConsumedKwh) : 0), 0)
    return {
      key: cat.key,
      idx: i,
      count: catTrips.length,
      km: catKm,
      kmPct: totalKm > 0 ? catKm / totalKm * 100 : 0,
      energy: catEnergy,
      energyPct: hasEnergyData && totalEnergy > 0 ? catEnergy / totalEnergy * 100 : 0,
    }
  })
  return { categories, totalKm, totalEnergy, hasEnergyData, tripCount: trips.length }
})

// ── Helpers ──────────────────────────────────────────────────────────────────

function mondayOf(d: Date): Date {
  const date = new Date(d)
  date.setHours(0, 0, 0, 0)
  const day = date.getDay() || 7  // Sun=0 → 7 (ISO Mon=1..Sun=7)
  date.setDate(date.getDate() - (day - 1))
  return date
}

function dayOfMonth(monday: Date, dayIdx: number): number {
  const d = new Date(monday)
  d.setDate(monday.getDate() + dayIdx)
  return d.getDate()
}

function isToday(monday: Date, dayIdx: number): boolean {
  const d = new Date(monday)
  d.setDate(monday.getDate() + dayIdx)
  const today = new Date()
  return d.getFullYear() === today.getFullYear()
    && d.getMonth() === today.getMonth()
    && d.getDate() === today.getDate()
}

function isoWeekInfo(d: Date): { weekKey: string; weekLabel: string } {
  const date = new Date(d)
  date.setHours(0, 0, 0, 0)
  const day = date.getDay() || 7
  date.setDate(date.getDate() + 4 - day)
  const yearStart = new Date(date.getFullYear(), 0, 1)
  const week = Math.ceil(((date.getTime() - yearStart.getTime()) / 86400000 + 1) / 7)
  return {
    weekKey: `${date.getFullYear()}-${String(week).padStart(2, '0')}`,
    weekLabel: `${t('dashboard.week_abbr')}${week}`,
  }
}

function fmt1(n: number): string {
  return n.toLocaleString('de-DE', { minimumFractionDigits: 1, maximumFractionDigits: 1 })
}

function fmtShortDate(d: Date | null): string {
  if (!d) return ''
  return d.toLocaleDateString(undefined, { day: 'numeric', month: 'numeric' })
}

function cellBgClass(d: DayData | null): string {
  if (!d || d.km === 0) return 'bg-gray-100 dark:bg-gray-800/60'
  const ratio = Math.max(0.15, d.km / maxCellKm.value)
  if (ratio < 0.35) return 'bg-emerald-500/30'
  if (ratio < 0.6) return 'bg-emerald-500/55'
  if (ratio < 0.85) return 'bg-emerald-500/80'
  return 'bg-emerald-500'
}

function drainBarWidth(ev: { kwh: number }): string {
  return animateBars.value ? `${ev.kwh / maxDrainKwh.value * 100}%` : '0%'
}
</script>

<template>
  <div class="bg-white dark:bg-gray-700 rounded-sm border-2 border-gray-300 dark:border-gray-600 shadow-[2px_2px_0_0_#d1d5db] dark:shadow-[2px_2px_0_0_#4b5563] overflow-hidden">

    <!-- Tab bar -->
    <div class="flex items-center border-b border-gray-200 dark:border-gray-600">
      <div class="flex items-center gap-5 px-4 md:px-5 overflow-x-auto flex-1 min-w-0 [&::-webkit-scrollbar]:hidden [-ms-overflow-style:none] [scrollbar-width:none]">
        <button
          v-for="tab in (['donut', 'nights', 'calendar', 'routes'] as Tab[])"
          :key="tab"
          @click="selectTab(tab, $event)"
          :class="[
            'py-3 text-xs font-semibold border-b-2 transition-colors -mb-px whitespace-nowrap flex-shrink-0',
            activeTab === tab
              ? 'border-indigo-500 text-indigo-500 dark:text-indigo-400'
              : 'border-transparent text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300'
          ]"
        >
          {{ t(`dashboard.insights_tab_${tab}`) }}
        </button>
      </div>
      <button @click="toggleCollapsed" class="sm:hidden flex-shrink-0 p-1.5 pr-3">
        <ChevronDownIcon class="w-4 h-4 text-gray-400 transition-transform duration-200" :class="{ 'rotate-180': !collapsed }" />
      </button>
    </div>

    <!-- Panels with horizontal slide-fade -->
    <Transition @enter="onEnter" @after-enter="onAfterEnter" @leave="onLeave" @after-leave="onAfterLeave">
    <div v-show="!collapsed" class="sm:!block">
    <Transition name="tab-slide" mode="out-in">
      <div :key="activeTab">

        <!-- ── DONUT ── -->
        <div v-if="activeTab === 'donut'" class="p-4 md:p-5">
          <div class="flex items-center gap-4 md:gap-6">
          <div class="relative flex-shrink-0" style="width:128px;height:128px">
            <svg width="128" height="128" viewBox="0 0 128 128" style="transform:rotate(-90deg)">
              <circle cx="64" cy="64" r="52" fill="none" class="stroke-gray-100 dark:stroke-gray-700" stroke-width="20"/>
              <!-- Driven: strokeDasharray as CSS property so transition fires -->
              <circle cx="64" cy="64" r="52" fill="none" stroke="#6366f1" stroke-width="20"
                stroke-linecap="butt"
                :style="{
                  strokeDasharray: `${animDrivenArc} ${C - animDrivenArc}`,
                  transition: 'stroke-dasharray 0.8s cubic-bezier(0.4,0,0.2,1)'
                }"
              />
              <!-- Phantom -->
              <circle v-if="phantomArcLen > 0.5" cx="64" cy="64" r="52" fill="none" stroke="#f59e0b" stroke-width="20"
                stroke-linecap="butt"
                :stroke-dashoffset="phantomDashoffset"
                :style="{
                  strokeDasharray: `${animPhantomArc} ${C - animPhantomArc}`,
                  transition: 'stroke-dasharray 0.8s cubic-bezier(0.4,0,0.2,1) 0.05s'
                }"
              />
              <!-- Loss -->
              <circle v-if="lossArcLen > 0.5" cx="64" cy="64" r="52" fill="none" stroke="#94a3b8" stroke-width="20"
                stroke-linecap="butt"
                :stroke-dashoffset="lossDashoffset"
                :style="{
                  strokeDasharray: `${animLossArc} ${C - animLossArc}`,
                  transition: 'stroke-dasharray 0.8s cubic-bezier(0.4,0,0.2,1) 0.1s'
                }"
              />
            </svg>
            <div class="absolute inset-0 flex flex-col items-center justify-center">
              <p class="text-sm font-bold text-amber-500 dark:text-amber-400 mono">{{ fmt1(phantomPct) }}%</p>
              <p class="text-[9px] text-gray-500 dark:text-gray-400 text-center leading-tight mt-0.5">{{ t('dashboard.insights_phantom') }}</p>
            </div>
          </div>

          <div class="flex-1 min-w-0 space-y-3">
            <!-- Driven -->
            <div>
              <div class="flex items-center justify-between mb-1">
                <div class="flex items-center gap-1.5">
                  <span class="w-2 h-2 rounded-sm bg-indigo-500 flex-shrink-0"></span>
                  <span class="text-xs text-gray-600 dark:text-gray-300">{{ t('dashboard.insights_driven') }}</span>
                </div>
                <span class="text-xs font-semibold text-gray-800 dark:text-gray-200 mono tabular-nums">{{ fmt1(allChargedKwh - allPhantomKwh - allLossKwh) }} kWh</span>
              </div>
              <div class="h-1.5 bg-gray-100 dark:bg-gray-800 rounded-full overflow-hidden">
                <div class="h-full bg-indigo-500 rounded-full" :style="{ width: drivenBarWidth, transition: 'width 0.7s cubic-bezier(0.4,0,0.2,1)' }"></div>
              </div>
            </div>
            <!-- Phantom -->
            <div>
              <div class="flex items-center justify-between mb-1">
                <div class="flex items-center gap-1.5">
                  <span class="w-2 h-2 rounded-sm bg-amber-400 flex-shrink-0"></span>
                  <span class="text-xs text-amber-600 dark:text-amber-400">{{ t('dashboard.insights_phantom') }}</span>
                </div>
                <span class="text-xs font-semibold text-amber-600 dark:text-amber-400 mono tabular-nums">{{ fmt1(allPhantomKwh) }} kWh</span>
              </div>
              <div class="h-1.5 bg-gray-100 dark:bg-gray-800 rounded-full overflow-hidden">
                <div class="h-full bg-amber-400 rounded-full" :style="{ width: phantomBarWidth, transition: 'width 0.7s cubic-bezier(0.4,0,0.2,1) 0.05s' }"></div>
              </div>
            </div>
            <!-- Loss -->
            <div v-if="allLossKwh > 0.1">
              <div class="flex items-center justify-between mb-1">
                <div class="flex items-center gap-1.5">
                  <span class="w-2 h-2 rounded-sm bg-slate-400 flex-shrink-0"></span>
                  <span class="text-xs text-gray-500 dark:text-gray-400">{{ t('dashboard.insights_charging_loss') }}</span>
                </div>
                <span class="text-xs font-semibold text-gray-500 dark:text-gray-400 mono tabular-nums">{{ fmt1(allLossKwh) }} kWh</span>
              </div>
              <div class="h-1.5 bg-gray-100 dark:bg-gray-800 rounded-full overflow-hidden">
                <div class="h-full bg-slate-400 rounded-full" :style="{ width: lossBarWidth, transition: 'width 0.7s cubic-bezier(0.4,0,0.2,1) 0.1s' }"></div>
              </div>
            </div>
            <!-- Footer: centered, phantom cost as the prominent line -->
            <div class="pt-2 border-t border-gray-100 dark:border-gray-600 text-center space-y-0.5">
              <p v-if="allPhantomKwh > 0.05" class="text-sm font-semibold text-amber-600 dark:text-amber-400">{{ t('dashboard.insights_phantom_eur', { eur: fmt1(phantomCostEur) }) }}</p>
              <p class="text-[11px] text-gray-500 dark:text-gray-400">{{ fmt1(allChargedKwh) }} kWh {{ t('dashboard.insights_loaded') }}</p>
            </div>
          </div>
          </div>
        </div>

        <!-- ── STANDVERLUSTE ── -->
        <div v-else-if="activeTab === 'nights'" class="p-4 md:p-5">
          <div class="flex items-center justify-between mb-4">
            <p class="text-xs text-gray-500 dark:text-gray-400">{{ t('dashboard.insights_worst_parks') }}</p>
            <span class="text-xs font-semibold text-amber-500 dark:text-amber-400 mono tabular-nums">∑ {{ fmt1(totalDrainFiltered) }} kWh</span>
          </div>
          <div v-if="drainEvents.length === 0" class="text-center py-6 text-sm text-gray-500 dark:text-gray-400">
            {{ t('dashboard.insights_no_drain') }}
          </div>
          <div v-else class="space-y-2.5">
            <div v-for="(ev, i) in drainEvents" :key="i" class="flex items-center gap-3">
              <span class="text-[11px] text-gray-500 dark:text-gray-400 w-12 text-right flex-shrink-0 mono tabular-nums">
                {{ fmtShortDate(ev.date) }}
              </span>
              <div class="flex-1 h-5 bg-gray-100 dark:bg-gray-800/70 rounded overflow-hidden">
                <div
                  class="h-full rounded"
                  :style="{
                    width: drainBarWidth(ev),
                    background: 'linear-gradient(90deg, #d97706, #fbbf24)',
                    transition: `width 0.65s cubic-bezier(0.4,0,0.2,1) ${i * 0.07}s`
                  }"
                ></div>
              </div>
              <span class="text-xs font-semibold text-amber-500 dark:text-amber-400 mono tabular-nums w-14 text-right flex-shrink-0">
                {{ fmt1(ev.kwh) }} kWh
              </span>
              <span class="text-[10px] text-gray-500 dark:text-gray-400 w-9 flex-shrink-0">
                {{ fmt1(ev.hours) }} h
              </span>
            </div>
          </div>
          <p class="text-[11px] text-gray-500 dark:text-gray-400 mt-4 pt-3 border-t border-gray-100 dark:border-gray-600">
            {{ t('dashboard.insights_sentry_hint') }}
          </p>
        </div>

        <!-- ── CALENDAR ── -->
        <div v-else-if="activeTab === 'calendar'" class="p-4 md:p-5">
          <div class="flex items-center justify-between mb-4">
            <p class="text-xs text-gray-500 dark:text-gray-400">{{ t('dashboard.insights_trips_detected') }}</p>
            <div class="flex items-center gap-3">
              <span class="text-xs font-semibold text-gray-800 dark:text-gray-200 mono tabular-nums">{{ tripCount }}</span>
              <span class="text-xs text-gray-500 dark:text-gray-400 mono tabular-nums">{{ Math.round(totalTripKm) }} km</span>
            </div>
          </div>
          <div v-if="tripCount === 0" class="text-center py-6 text-sm text-gray-500 dark:text-gray-400">
            {{ t('dashboard.insights_no_trips') }}
          </div>
          <div v-else class="space-y-1.5">
            <div class="grid grid-cols-8 gap-1">
              <div></div>
              <div v-for="day in ['Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa', 'So']" :key="day"
                class="text-[9px] text-gray-500 dark:text-gray-400 text-center font-semibold">
                {{ day }}
              </div>
            </div>
            <div v-for="(week, wi) in calendarWeeks" :key="wi" class="grid grid-cols-8 gap-1 items-center">
              <span class="text-[9px] text-gray-500 dark:text-gray-400 font-semibold text-right pr-1">{{ week.label }}</span>
              <div v-for="(day, di) in week.days" :key="di"
                :class="[
                  'relative rounded h-12 flex flex-col items-center justify-center gap-px',
                  cellBgClass(day),
                  isToday(week.monday, di) ? 'ring-2 ring-indigo-400 dark:ring-indigo-300' : ''
                ]"
              >
                <span :class="[
                  'absolute top-0.5 left-1 text-[8px] font-medium leading-none',
                  isToday(week.monday, di)
                    ? 'text-indigo-500 dark:text-indigo-300 font-bold'
                    : (day ? 'text-white/90' : 'text-gray-500 dark:text-gray-400')
                ]">{{ dayOfMonth(week.monday, di) }}</span>
                <template v-if="day">
                  <span class="text-[11px] font-bold text-white leading-none">{{ day.count }}</span>
                  <span class="text-[9px] text-white/90 leading-none">{{ Math.round(day.km) }} km</span>
                </template>
              </div>
            </div>
          </div>
          <div class="mt-4 pt-3 border-t border-gray-100 dark:border-gray-600 grid grid-cols-3 gap-3 text-center">
            <div>
              <p class="text-sm font-bold text-gray-800 dark:text-gray-200 mono tabular-nums">{{ tripCount }}</p>
              <p class="text-[10px] text-gray-500 dark:text-gray-400">{{ t('dashboard.insights_trips_count') }}</p>
            </div>
            <div class="border-x border-gray-100 dark:border-gray-600">
              <p class="text-sm font-bold text-gray-800 dark:text-gray-200 mono tabular-nums">{{ fmt1(avgTripKm) }} km</p>
              <p class="text-[10px] text-gray-500 dark:text-gray-400">{{ t('dashboard.insights_avg_trip') }}</p>
            </div>
            <div>
              <p class="text-sm font-bold text-gray-800 dark:text-gray-200 mono tabular-nums">{{ Math.round(totalTripKm) }} km</p>
              <p class="text-[10px] text-gray-500 dark:text-gray-400">{{ t('dashboard.insights_total_km') }}</p>
            </div>
          </div>
        </div>

        <!-- ── ROUTES ── -->
        <div v-else-if="activeTab === 'routes'" class="p-4 md:p-5">
          <div v-if="!routeStats" class="text-center py-6 text-sm text-gray-500 dark:text-gray-400">
            {{ t('dashboard.insights_routes_no_data') }}
          </div>
          <div v-else class="flex flex-col md:flex-row md:items-center md:gap-0">
            <!-- legend -->
            <div class="md:flex-shrink-0 space-y-2">
              <div v-for="cat in routeStats.categories" :key="cat.key" class="flex items-center gap-2">
                <div class="w-3 h-3 rounded-sm flex-shrink-0" :style="{ background: routeBarBg(cat.key) }"></div>
                <span class="text-xs text-gray-600 dark:text-gray-400 flex-1 min-w-0 truncate">
                  {{ t(`dashboard.insights_routes_${cat.key}`) }}
                </span>
                <span class="text-xs mono tabular-nums text-gray-600 dark:text-gray-400 flex-shrink-0">
                  {{ Math.round(cat.kmPct) }}% km
                </span>
                <span v-if="routeStats.hasEnergyData" class="text-xs mono tabular-nums text-gray-500 dark:text-gray-500 flex-shrink-0 w-14 text-right">
                  {{ Math.round(cat.energyPct) }}% kWh
                </span>
              </div>
            </div>
            <!-- divider: vertical on desktop, horizontal on mobile -->
            <div class="hidden md:block w-px self-stretch bg-gray-200 dark:bg-gray-600 mx-5"></div>
            <div class="block md:hidden h-px bg-gray-100 dark:bg-gray-600 my-3"></div>
            <!-- bars -->
            <div class="flex items-center gap-3 flex-1 min-w-0">
              <div class="space-y-2 flex-1 min-w-0">
                <!-- totals header - mobile only -->
                <div class="flex md:hidden items-center justify-center gap-1.5 text-[11px] tabular-nums mono text-gray-500 dark:text-gray-400">
                  <span>{{ Math.round(routeStats.totalKm) }} km</span>
                  <template v-if="routeStats.hasEnergyData">
                    <span class="text-gray-300 dark:text-gray-600">·</span>
                    <span>{{ fmt1(routeStats.totalEnergy) }} kWh</span>
                  </template>
                </div>
                <div class="flex flex-1 h-6 overflow-hidden rounded bg-gray-100 dark:bg-gray-800/70">
                  <div
                    v-for="cat in routeStats.categories"
                    :key="cat.key"
                    class="flex items-center justify-center overflow-hidden"
                    :style="{
                      width: animateRoutes ? `${cat.kmPct}%` : '0%',
                      background: routeBarBg(cat.key),
                      transition: `width 0.65s cubic-bezier(0.4,0,0.2,1) ${cat.idx * 0.1}s`
                    }"
                  >
                    <span v-if="cat.kmPct >= 5" class="text-[11px] font-medium text-white/90 tabular-nums truncate px-1.5">
                      {{ Math.round(cat.km) }}<template v-if="cat.kmPct >= 15"> km</template>
                    </span>
                  </div>
                </div>
                <div class="flex flex-1 h-6 overflow-hidden rounded bg-gray-100 dark:bg-gray-800/70">
                  <div
                    v-for="cat in routeStats.categories"
                    :key="cat.key"
                    class="flex items-center justify-center overflow-hidden"
                    :style="{
                      width: animateRoutes ? `${cat.energyPct}%` : '0%',
                      background: routeBarBg(cat.key),
                      opacity: routeStats.hasEnergyData ? '0.7' : '0',
                      transition: `width 0.65s cubic-bezier(0.4,0,0.2,1) ${cat.idx * 0.1 + 0.05}s`
                    }"
                  >
                    <span v-if="routeStats.hasEnergyData && cat.energyPct >= 5" class="text-[11px] font-medium text-white tabular-nums truncate px-1.5">
                      {{ fmt1(cat.energy) }}<template v-if="cat.energyPct >= 15"> kWh</template>
                    </span>
                  </div>
                </div>
              </div>
              <!-- totals right - desktop only -->
              <div class="hidden md:flex flex-col items-end justify-center gap-2 flex-shrink-0 text-[11px] tabular-nums mono text-gray-500 dark:text-gray-400">
                <span>{{ Math.round(routeStats.totalKm) }} km</span>
                <span v-if="routeStats.hasEnergyData">{{ fmt1(routeStats.totalEnergy) }} kWh</span>
              </div>
            </div>
          </div>
          <p v-if="routeStats?.hasEnergyData" class="mt-3 text-[10px] text-gray-400 dark:text-gray-500 text-center">
            Es fliessen nur vollständig erfasste Fahrten mit Energiewerten in die Statistik ein.
          </p>
        </div>

      </div>
    </Transition>
    </div>
    </Transition>

  </div>
</template>

<style scoped>
/* Horizontal slide-fade on tab switch */
.tab-slide-enter-active {
  transition: opacity 0.18s ease, transform 0.18s ease;
}
.tab-slide-leave-active {
  transition: opacity 0.12s ease, transform 0.12s ease;
}
.tab-slide-enter-from {
  opacity: 0;
  transform: translateX(10px);
}
.tab-slide-leave-to {
  opacity: 0;
  transform: translateX(-6px);
}
</style>
