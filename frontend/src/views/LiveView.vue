<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
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

const activeCars = computed(() =>
  Array.isArray(cars.value) ? cars.value.filter(c => c.status === 'ACTIVE') : []
)

const canViewLive = computed(() =>
  authStore.isAutoSyncLive || authStore.isAdmin || authStore.isBetaTester
)

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

// Duration since session started
const sessionDuration = computed(() => {
  if (!data.value?.sessionStartedAt) return null
  const start = new Date(data.value.sessionStartedAt).getTime()
  const now = Date.now()
  const diffMs = Math.max(0, now - start)
  const totalSec = Math.floor(diffMs / 1000)
  const h = Math.floor(totalSec / 3600)
  const m = Math.floor((totalSec % 3600) / 60)
  if (h > 0) return `${h}h ${m}min`
  return `${m} ${t('live.minutes_short')}`
})

// Seconds since last update
const secondsSinceUpdate = computed(() => {
  if (!data.value?.lastUpdatedAt) return null
  const updated = new Date(data.value.lastUpdatedAt).getTime()
  const diff = Math.floor((Date.now() - updated) / 1000)
  return diff
})

// SoC progress bar color
const socBarColor = computed(() => {
  const soc = data.value?.socPercent ?? 0
  if (soc > 80) return 'bg-amber-400'
  return 'bg-emerald-500'
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
            <p class="text-sm text-gray-600 dark:text-gray-300 mb-4 font-medium leading-relaxed">
              {{ t('live.upgrade_desc') }}
            </p>
            <button
              @click="router.push('/upgrade')"
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

        <!-- Active charging session card -->
        <div
          v-else
          class="border-2 border-gray-900 dark:border-white rounded-sm shadow-[4px_4px_0_0_#030712] dark:shadow-[4px_4px_0_0_#ffffff] overflow-hidden"
        >
          <!-- Live badge bar -->
          <div class="flex items-center gap-2 px-4 py-2.5 bg-emerald-500 border-b-2 border-gray-900 dark:border-white">
            <span class="w-2.5 h-2.5 rounded-full bg-white animate-pulse"></span>
            <span class="text-[11px] font-bold uppercase tracking-[0.14em] text-gray-950">Live</span>
            <span class="ml-auto text-[11px] font-bold uppercase tracking-[0.12em] text-gray-950 px-2 py-0.5 bg-white/30 rounded-sm border border-gray-900/20">
              {{ data.chargingType === 'DC' ? t('live.dc') : t('live.ac') }}
            </span>
          </div>

          <!-- Power - big number -->
          <div class="px-4 py-5 border-b-2 border-gray-900 dark:border-white flex items-end gap-2">
            <span class="text-5xl font-black tracking-tight text-gray-900 dark:text-white leading-none tabular-nums">
              {{ formatNumber(data.powerKw) }}
            </span>
            <span class="text-lg font-bold text-gray-500 dark:text-gray-400 mb-1">kW</span>
            <span class="ml-auto text-[11px] font-bold uppercase tracking-[0.14em] text-gray-500 dark:text-gray-400 mb-1.5">
              {{ t('live.power') }}
            </span>
          </div>

          <!-- SoC progress bar -->
          <div class="px-4 pt-4 pb-3 border-b-2 border-gray-900 dark:border-white">
            <div class="flex items-center justify-between mb-1.5">
              <span class="text-[11px] font-bold uppercase tracking-[0.14em] text-gray-500 dark:text-gray-400">
                {{ t('live.soc') }}
              </span>
              <span class="text-sm font-black text-gray-900 dark:text-white tabular-nums">
                {{ formatNumber(data.socPercent, 1) }} %
              </span>
            </div>
            <div class="h-3 w-full bg-gray-200 dark:bg-gray-700 rounded-sm border border-gray-300 dark:border-gray-600 overflow-hidden">
              <div
                class="h-full transition-all duration-1000 ease-out rounded-sm"
                :class="socBarColor"
                :style="{ width: `${Math.min(100, data.socPercent ?? 0)}%` }"
              ></div>
            </div>
          </div>

          <!-- Stats grid -->
          <div class="divide-y-2 divide-gray-900 dark:divide-white">
            <!-- ETA -->
            <div class="flex items-center justify-between px-4 py-3">
              <span class="text-[11px] font-bold uppercase tracking-[0.14em] text-gray-500 dark:text-gray-400">
                {{ t('live.eta') }}
              </span>
              <span class="text-sm font-bold text-gray-900 dark:text-white tabular-nums">
                {{ data.timeToFullMinutes != null ? `~${data.timeToFullMinutes} ${t('live.minutes_short')}` : '-' }}
              </span>
            </div>
            <!-- Range -->
            <div class="flex items-center justify-between px-4 py-3">
              <span class="text-[11px] font-bold uppercase tracking-[0.14em] text-gray-500 dark:text-gray-400">
                {{ t('live.range') }}
              </span>
              <span class="text-sm font-bold text-gray-900 dark:text-white tabular-nums">
                {{ data.estRangeKm != null ? `~${formatNumber(data.estRangeKm, 0)} km` : '-' }}
              </span>
            </div>
            <!-- Charge current -->
            <div class="flex items-center justify-between px-4 py-3">
              <span class="text-[11px] font-bold uppercase tracking-[0.14em] text-gray-500 dark:text-gray-400">
                {{ t('live.amps') }}
              </span>
              <span class="text-sm font-bold text-gray-900 dark:text-white tabular-nums">
                {{ data.chargeAmps != null ? `${formatNumber(data.chargeAmps, 0)} A` : '-' }}
              </span>
            </div>
            <!-- Duration -->
            <div class="flex items-center justify-between px-4 py-3">
              <span class="text-[11px] font-bold uppercase tracking-[0.14em] text-gray-500 dark:text-gray-400">
                {{ t('live.duration') }}
              </span>
              <span class="text-sm font-bold text-gray-900 dark:text-white tabular-nums">
                {{ sessionDuration ?? '-' }}
              </span>
            </div>
          </div>

          <!-- Footer: last updated + manual refresh -->
          <div class="flex items-center justify-between px-4 py-2.5 bg-gray-50 dark:bg-gray-700/50 border-t-2 border-gray-900 dark:border-white">
            <span class="text-[11px] text-gray-500 dark:text-gray-400 font-medium">
              {{ t('live.last_updated') }}:
              {{ secondsSinceUpdate != null ? `${secondsSinceUpdate} ${t('live.seconds_short')}` : '-' }}
            </span>
            <button
              @click="refresh()"
              class="p-1.5 border-2 border-gray-900 dark:border-white rounded-sm shadow-[2px_2px_0_0_#030712] dark:shadow-[2px_2px_0_0_#ffffff] active:translate-x-[2px] active:translate-y-[2px] active:shadow-none transition-[transform,box-shadow] duration-75 bg-white dark:bg-gray-800 hover:bg-gray-100 dark:hover:bg-gray-700"
              :title="t('live.last_updated')"
            >
              <ArrowPathIcon class="h-4 w-4 text-gray-700 dark:text-gray-300" />
            </button>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>
