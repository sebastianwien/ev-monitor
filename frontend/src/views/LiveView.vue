<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { useHead } from '@unhead/vue'
import { BoltIcon, ArrowPathIcon } from '@heroicons/vue/24/outline'
import { useAuthStore } from '../stores/auth'
import { useCarStore } from '../stores/car'
import { useChargingLive } from '../composables/useChargingLive'
import CarSelectDropdown from '../components/car/CarSelectDropdown.vue'
import type { Car } from '../api/carService'

const { t } = useI18n()
const router = useRouter()
const authStore = useAuthStore()
const carStore = useCarStore()

const cars = ref<Car[]>([])
const loading = ref(true)
const selectedCarId = ref<string | null>(null)

// Reactive clock for computed values that depend on current time
const now = ref(Date.now())
let clockInterval: ReturnType<typeof setInterval>
onMounted(() => { clockInterval = setInterval(() => { now.value = Date.now() }, 1000) })
onUnmounted(() => clearInterval(clockInterval))

useHead(computed(() => ({
  title: `${t('live.title')} | EV Monitor`,
})))

const activeCars = computed(() =>
  Array.isArray(cars.value) ? cars.value.filter(c => c.status === 'ACTIVE') : []
)

const canViewLive = computed(() => authStore.canViewLiveTrips)

onMounted(async () => {
  try {
    cars.value = await carStore.getCars()
    if (activeCars.value.length > 0) {
      selectedCarId.value = activeCars.value[0].id
    }
  } finally {
    loading.value = false
  }
})

const { data, loading: liveLoading, refresh } = useChargingLive(
  computed(() => canViewLive.value ? selectedCarId.value : null)
)

// Duration since session started - reactive via now.value
const sessionDuration = computed(() => {
  if (!data.value?.sessionStartedAt) return null
  const start = new Date(data.value.sessionStartedAt).getTime()
  const diffMs = Math.max(0, now.value - start)
  const totalSec = Math.floor(diffMs / 1000)
  const h = Math.floor(totalSec / 3600)
  const m = Math.floor((totalSec % 3600) / 60)
  if (h > 0) return `${h}h ${m}min`
  return `${m} ${t('live.minutes_short')}`
})

// Seconds since last update - reactive via now.value
const secondsSinceUpdate = computed(() => {
  if (!data.value?.lastUpdatedAt) return null
  const updated = new Date(data.value.lastUpdatedAt).getTime()
  return Math.floor((now.value - updated) / 1000)
})

// Stale data indicators
const dataIsStale = computed(() => (secondsSinceUpdate.value ?? 0) > 60)
const dataIsVeryStale = computed(() => (secondsSinceUpdate.value ?? 0) > 120)

// Effective SoC start: socAtSessionStart if available, else 0
const socStart = computed(() => data.value?.socAtSessionStart ?? 0)
// Effective target: chargeLimitSoc if available, else 80
const socTarget = computed(() => data.value?.chargeLimitSoc ?? 80)
// Session start time formatted as HH:MM
const sessionStartTime = computed(() => {
  if (!data.value?.sessionStartedAt) return null
  return new Date(data.value.sessionStartedAt).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' })
})

function formatNumber(val: number | null, decimals = 1): string {
  if (val === null || val === undefined) return '-'
  return val.toLocaleString(undefined, { minimumFractionDigits: decimals, maximumFractionDigits: decimals })
}
</script>

<template>
  <div class="md:max-w-2xl md:mx-auto md:p-6">
    <!-- Page loading skeleton -->
    <div v-if="loading" class="bg-white dark:bg-gray-800 md:rounded-xl md:shadow-lg p-4 md:p-6">
      <div class="animate-pulse space-y-4">
        <div class="h-8 bg-gray-200 dark:bg-gray-700 rounded w-1/3"></div>
        <div class="h-4 bg-gray-200 dark:bg-gray-700 rounded w-1/2"></div>
        <div class="h-40 bg-gray-200 dark:bg-gray-700 rounded"></div>
      </div>
    </div>

    <div v-else class="bg-white dark:bg-gray-800 md:rounded-xl md:shadow-lg p-4 md:p-6">
      <!-- Header -->
      <div class="mb-5">
        <div class="flex items-center gap-3 mb-1.5">
          <BoltIcon class="h-7 w-7 text-emerald-500" />
          <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">{{ t('live.title') }}</h1>
        </div>
        <p class="text-gray-600 dark:text-gray-400 text-sm">{{ t('live.subtitle') }}</p>
      </div>

      <!-- Upgrade teaser - shown for non-entitled users -->
      <div
        v-if="!canViewLive"
        class="border-2 border-gray-900 dark:border-white bg-amber-50 dark:bg-amber-950/30 rounded-sm shadow-[4px_4px_0_0_#030712] dark:shadow-[4px_4px_0_0_#ffffff] p-5"
      >
        <div class="flex flex-col items-center md:flex-row md:items-start gap-4">
          <div class="shrink-0 rounded-sm border-2 border-gray-900 dark:border-white bg-amber-500 p-2.5 w-12 h-12 flex items-center justify-center">
            <BoltIcon class="h-6 w-6 text-gray-950" />
          </div>
          <div class="flex-1 min-w-0 text-center md:text-left">
            <p class="text-amber-600 dark:text-amber-400 text-[11px] font-bold uppercase tracking-[0.14em] mb-1">
              AutoSync Live
            </p>
            <p class="font-bold text-gray-900 dark:text-white text-lg mb-1 tracking-tight">
              {{ t('live.upgrade_title') }}
            </p>
            <p id="live-upgrade-desc" class="text-sm text-gray-600 dark:text-gray-300 mb-4 font-medium leading-relaxed">
              {{ t('live.upgrade_desc') }}
            </p>
            <button
              @click="router.push('/upgrade')"
              aria-describedby="live-upgrade-desc"
              class="inline-flex items-center gap-2 bg-amber-500 hover:bg-amber-400 text-gray-950 font-bold uppercase tracking-wider text-xs px-5 py-3 rounded-sm border-2 border-gray-900 shadow-[3px_3px_0_0_#030712] active:translate-x-[3px] active:translate-y-[3px] active:shadow-none transition-[transform,box-shadow] duration-75"
            >
              <BoltIcon class="h-4 w-4" />
              {{ t('live.upgrade_cta') }}
            </button>
          </div>
        </div>
      </div>

      <!-- Content for entitled users -->
      <template v-else>
        <!-- Car selector (only if multiple cars) -->
        <div v-if="activeCars.length > 1" class="mb-4">
          <CarSelectDropdown :cars="activeCars" v-model="selectedCarId" />
        </div>

        <!-- No cars state -->
        <div v-if="activeCars.length === 0" class="text-center py-10 text-gray-500 dark:text-gray-400 text-sm">
          {{ t('cars.no_cars') }}
        </div>

        <!-- Live data spinner (first load) -->
        <div v-else-if="liveLoading && !data" class="flex items-center justify-center py-12">
          <ArrowPathIcon class="h-8 w-8 animate-spin text-gray-400" />
        </div>

        <!-- No active session -->
        <div
          v-else-if="!data?.isActive"
          class="border-2 border-gray-200 dark:border-gray-700 rounded-sm p-6 text-center"
        >
          <div class="mb-3 flex items-center justify-center">
            <span class="w-3 h-3 rounded-full bg-gray-300 dark:bg-gray-600"></span>
          </div>
          <p class="font-bold text-gray-900 dark:text-white text-base mb-1">{{ t('live.no_session') }}</p>
          <p class="text-sm text-gray-500 dark:text-gray-400">{{ t('live.no_session_hint') }}</p>
        </div>

        <!-- Active charging session card - Charging Arc design -->
        <div
          v-else
          class="border-2 border-gray-900 dark:border-white rounded-sm shadow-[4px_4px_0_0_#030712] dark:shadow-[4px_4px_0_0_#ffffff] overflow-hidden"
        >
          <!-- HEADER: car info left, LIVE badge right -->
          <div class="flex items-center justify-between px-4 pt-4 pb-3 border-b-2 border-gray-900 dark:border-white">
            <div>
              <p class="text-[11px] font-bold uppercase tracking-[0.14em] text-gray-500 dark:text-gray-400">
                {{ activeCars[0]?.brand ?? 'Tesla' }}
              </p>
            </div>
            <!-- LIVE badge -->
            <div class="relative flex items-center gap-2 border-2 border-emerald-600 dark:border-emerald-400 rounded-sm px-2.5 py-1.5 bg-emerald-50 dark:bg-emerald-950/40 shadow-[2px_2px_0_0_#030712] dark:shadow-[2px_2px_0_0_#ffffff]">
              <div class="relative w-2.5 h-2.5">
                <span class="live-ring absolute inset-0 rounded-full border-2 border-emerald-500"></span>
                <span class="live-dot absolute rounded-full bg-emerald-500" style="inset:1px;"></span>
              </div>
              <span class="text-[11px] font-bold uppercase tracking-[0.14em] text-emerald-700 dark:text-emerald-400">LIVE</span>
            </div>
          </div>

          <!-- Stale warning -->
          <div v-if="dataIsStale" class="border-l-2 border-amber-500 bg-amber-50 dark:bg-amber-950/30 px-3 py-2 text-xs font-medium text-amber-700 dark:text-amber-300">
            {{ t('live.stale_warning') }}
          </div>

          <!-- POWER SECTION -->
          <div class="px-4 pt-4 pb-3" :class="{ 'opacity-50': dataIsVeryStale }">
            <div class="flex items-end gap-3">
              <!-- kW box: dark bg, neo-brutalist border -->
              <div class="border-2 border-gray-900 dark:border-white rounded-sm px-4 py-2 bg-gray-950 shadow-[4px_4px_0_0_#030712] dark:shadow-[4px_4px_0_0_#4f4f4f]">
                <div class="flex items-baseline justify-center gap-1">
                  <span class="kw-value text-white font-extrabold leading-none" style="font-size:56px;letter-spacing:-0.03em;line-height:1;">
                    <template v-if="data.powerKw != null">{{ formatNumber(data.powerKw) }}</template>
                    <span v-else class="opacity-40">...</span>
                  </span>
                  <span class="font-extrabold text-amber-400" style="font-size:22px;line-height:1;align-self:flex-end;padding-bottom:4px;">kW</span>
                </div>
              </div>
              <!-- Type + label -->
              <div class="pb-1">
                <p class="text-[11px] font-bold uppercase tracking-[0.14em] text-emerald-600 dark:text-emerald-400">
                  {{ data.chargingType === 'DC' ? t('live.dc') : t('live.ac') }}<span v-if="data.chargingType === 'DC'"> · Supercharger</span>
                </p>
                <p class="text-xs text-gray-500 dark:text-gray-400 font-medium mt-0.5">{{ t('live.power') }}</p>
              </div>
            </div>
          </div>

          <!-- THE CHARGING ARC / PROGRESS TRACK -->
          <div class="px-4 pb-4" :class="{ 'opacity-50': dataIsVeryStale }">
            <!-- Labels row above track -->
            <div class="relative mb-1" style="height:36px;">
              <!-- START label at socStart% position -->
              <div :style="`position:absolute;left:${socStart}%;transform:translateX(-50%);bottom:0;text-align:center;`">
                <p class="text-[9px] font-bold uppercase tracking-[0.1em] text-gray-400 leading-none">START</p>
                <p class="text-xs font-bold text-gray-500 dark:text-gray-400">{{ Math.round(socStart) }}%</p>
              </div>
              <!-- CURRENT label at current SoC% -->
              <div :style="`position:absolute;left:${data.socPercent ?? 0}%;transform:translateX(-50%);bottom:0;text-align:center;`">
                <p class="text-base font-extrabold text-emerald-600 dark:text-emerald-400 leading-none">{{ Math.round(data.socPercent ?? 0) }}%</p>
                <p class="text-[9px] font-bold uppercase tracking-[0.1em] text-gray-400">jetzt</p>
              </div>
              <!-- TARGET label at socTarget% -->
              <div :style="`position:absolute;left:${Math.min(socTarget, 95)}%;transform:translateX(-50%);bottom:0;text-align:center;`">
                <p class="text-base font-extrabold text-amber-500 leading-none">{{ socTarget }}%</p>
                <p class="text-[9px] font-bold uppercase tracking-[0.1em] text-gray-400">Ziel</p>
              </div>
            </div>

            <!-- Track bar -->
            <div class="relative w-full border-2 border-gray-900 dark:border-white rounded-sm shadow-[4px_4px_0_0_#030712] dark:shadow-[4px_4px_0_0_#ffffff] overflow-visible" style="height:28px;">
              <!-- Light bg -->
              <div class="absolute inset-0 bg-gray-50 dark:bg-gray-800"></div>

              <!-- Zone 0: pre-session gray (0% to socStart) -->
              <div class="absolute top-0 left-0 h-full bg-gray-200 dark:bg-gray-700"
                   :style="`width:${socStart}%;`"></div>

              <!-- Zone 1: filled emerald (socStart to current SoC) -->
              <div class="absolute top-0 h-full bg-emerald-500"
                   :style="`left:${socStart}%;width:${Math.max(0,(data.socPercent ?? 0)-socStart)}%;`"></div>

              <!-- Zone 2: amber striped (current to target) -->
              <div class="absolute top-0 h-full"
                   :style="`left:${data.socPercent ?? 0}%;width:${Math.max(0,socTarget-(data.socPercent ?? 0))}%;background:repeating-linear-gradient(45deg,#fef3c7,#fef3c7 4px,#fde68a 4px,#fde68a 8px);border-right:2px dashed #f59e0b;`"></div>

              <!-- Zone 3: post-target gray (target to 100%) -->
              <div class="absolute top-0 right-0 h-full bg-gray-200 dark:bg-gray-700"
                   :style="`width:${100-socTarget}%;`"></div>

              <!-- Start tick line -->
              <div class="absolute top-0 h-full w-0.5 bg-gray-700 dark:bg-gray-300"
                   :style="`left:${socStart}%;`"></div>

              <!-- Target tick line -->
              <div class="absolute top-0 h-full w-0.5 bg-amber-500"
                   :style="`left:${socTarget}%;`"></div>

              <!-- Current position: pulsing circle -->
              <div class="absolute" :style="`top:50%;left:${data.socPercent ?? 0}%;transform:translate(-50%,-50%);`">
                <span class="live-ring absolute rounded-full border-2 border-emerald-500" style="width:20px;height:20px;top:0;left:0;transform:translate(-50%,-50%);"></span>
                <span class="w-5 h-5 rounded-full bg-emerald-500 border-2 border-gray-900 dark:border-white block relative z-10" style="transform:translate(-50%,-50%);"></span>
              </div>
            </div>

            <!-- Bottom tick labels -->
            <div class="relative mt-1" style="height:14px;">
              <span class="absolute left-0 text-[10px] font-bold uppercase tracking-[0.1em] text-gray-400">0%</span>
              <span class="absolute right-0 text-[10px] font-bold uppercase tracking-[0.1em] text-gray-400">100%</span>
            </div>

            <!-- ETA chip centered -->
            <div class="flex justify-center mt-3">
              <div v-if="data.timeToFullMinutes != null"
                   class="border-2 border-amber-500 rounded-sm px-3 py-1.5 flex items-center gap-2 bg-amber-50 dark:bg-amber-950/30 shadow-[2px_2px_0_0_#030712] dark:shadow-[2px_2px_0_0_#ffffff]">
                <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2.5" stroke="#f59e0b" class="w-3.5 h-3.5 shrink-0">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M12 6v6h4.5m4.5 0a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z" />
                </svg>
                <span class="font-extrabold text-amber-800 dark:text-amber-300" style="font-size:11px;letter-spacing:0.12em;text-transform:uppercase;">
                  {{ t('live.eta') }}: ~{{ data.timeToFullMinutes }} {{ t('live.minutes_short') }}
                </span>
              </div>
            </div>
          </div>

          <!-- STATS ROW: 3-column grid -->
          <div class="border-t-2 border-gray-900 dark:border-white grid grid-cols-3 divide-x-2 divide-gray-900 dark:divide-white"
               :class="{ 'opacity-50': dataIsVeryStale }">
            <!-- Reichweite -->
            <div class="px-3 py-3 text-center">
              <p class="text-[9px] font-bold uppercase tracking-[0.14em] text-gray-400 mb-1">{{ t('live.range') }}</p>
              <p class="text-xl font-extrabold text-gray-900 dark:text-white leading-none tracking-tight tabular-nums">
                {{ data.estRangeKm != null ? formatNumber(data.estRangeKm, 0) : '-' }}
              </p>
              <p class="text-[9px] font-bold uppercase tracking-[0.1em] text-gray-400 mt-1">km</p>
            </div>
            <!-- Ladestrom -->
            <div class="px-3 py-3 text-center bg-gray-50 dark:bg-gray-700/30">
              <p class="text-[9px] font-bold uppercase tracking-[0.14em] text-gray-400 mb-1">{{ t('live.amps') }}</p>
              <p class="text-xl font-extrabold text-gray-900 dark:text-white leading-none tracking-tight tabular-nums">
                {{ data.chargeAmps != null ? formatNumber(data.chargeAmps, 0) : '-' }}
              </p>
              <p class="text-[9px] font-bold uppercase tracking-[0.1em] text-gray-400 mt-1">A</p>
            </div>
            <!-- Dauer -->
            <div class="px-3 py-3 text-center">
              <p class="text-[9px] font-bold uppercase tracking-[0.14em] text-gray-400 mb-1">{{ t('live.duration') }}</p>
              <p class="text-xl font-extrabold text-gray-900 dark:text-white leading-none tracking-tight tabular-nums">
                {{ sessionDuration ?? '-' }}
              </p>
              <p v-if="sessionStartTime" class="text-[9px] font-bold uppercase tracking-[0.1em] text-gray-400 mt-1">
                seit {{ sessionStartTime }}
              </p>
            </div>
          </div>

          <!-- FOOTER -->
          <div class="border-t-2 border-gray-200 dark:border-gray-700 px-4 py-2.5 flex items-center justify-between bg-gray-50 dark:bg-gray-700/30">
            <span class="text-[11px] font-medium text-gray-400">
              {{ t('live.last_updated') }}:
              {{ secondsSinceUpdate != null ? `vor ${secondsSinceUpdate} Sek.` : '-' }}
            </span>
            <div class="flex items-center gap-2">
              <button
                @click="refresh()"
                :aria-label="t('live.refresh')"
                class="p-1.5 border-2 border-gray-900 dark:border-white rounded-sm shadow-[2px_2px_0_0_#030712] dark:shadow-[2px_2px_0_0_#ffffff] active:translate-x-[2px] active:translate-y-[2px] active:shadow-none transition-[transform,box-shadow] duration-75 bg-white dark:bg-gray-800 hover:bg-gray-100 dark:hover:bg-gray-700"
              >
                <ArrowPathIcon class="h-4 w-4 text-gray-700 dark:text-gray-300" />
              </button>
            </div>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped>
@keyframes pulse-dot {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.35; transform: scale(0.75); }
}
@keyframes pulse-ring {
  0% { transform: scale(1); opacity: 0.8; }
  100% { transform: scale(2.2); opacity: 0; }
}
@keyframes kw-tick {
  0%, 100% { opacity: 1; }
  48% { opacity: 1; }
  50% { opacity: 0.7; }
  52% { opacity: 1; }
}
.live-dot { animation: pulse-dot 1.6s ease-in-out infinite; }
.live-ring { animation: pulse-ring 1.6s ease-out infinite; }
.kw-value { animation: kw-tick 4s ease-in-out infinite; }
</style>
