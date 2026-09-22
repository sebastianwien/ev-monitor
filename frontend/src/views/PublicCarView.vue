<template>
  <div :class="isAuthenticated ? '' : 'min-h-screen bg-gray-50 dark:bg-gray-950'">
    <PublicNav />
    <main class="max-w-3xl mx-auto md:px-4 py-6 md:py-10">
      <div class="bg-white dark:bg-gray-900 border-y md:border border-gray-200 dark:border-gray-800 md:rounded-xl md:shadow-sm px-4 md:px-6 py-6 md:py-8">
        <div v-if="loading" class="text-center py-16 text-gray-500 dark:text-gray-400">
          {{ t('common.loading') }}
        </div>

        <div v-else-if="!car" class="text-center py-16">
          <p class="text-lg font-semibold text-gray-900 dark:text-gray-100 mb-2">{{ t('share_car.not_found_title') }}</p>
          <p class="text-sm text-gray-500 dark:text-gray-400 mb-4">{{ t('share_car.not_found_body') }}</p>
          <RouterLink to="/" class="text-emerald-600 dark:text-emerald-400 hover:underline">{{ t('share_car.to_start') }}</RouterLink>
        </div>

        <article v-else>
          <img v-if="car.hasImage" :src="imageUrl" :alt="car.carModel ?? ''"
            class="w-full h-44 md:h-64 object-cover rounded-lg mb-4" loading="lazy" />

          <div class="flex items-start gap-3 mb-1">
            <TruckIcon class="w-6 h-6 text-emerald-600 dark:text-emerald-400 flex-shrink-0 mt-1" aria-hidden="true" />
            <h1 class="text-2xl md:text-4xl font-bold text-gray-900 dark:text-gray-100">
              {{ car.carModel || t('share_car.fallback_title') }}
            </h1>
          </div>
          <p class="text-sm text-gray-500 dark:text-gray-400 mb-6 ml-9">
            {{ subtitle }}
          </p>

          <div v-if="tiles.length" class="grid grid-cols-2 sm:grid-cols-4 gap-2 mb-6">
            <div v-for="tile in tiles" :key="tile.key"
              class="rounded-lg border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800/60 px-3 py-2">
              <div class="text-[10px] uppercase tracking-wide text-gray-500 dark:text-gray-400 truncate">{{ tile.label }}</div>
              <div class="text-base font-semibold text-gray-900 dark:text-gray-100 tabular-nums whitespace-nowrap">{{ tile.value }}</div>
            </div>
          </div>

          <section v-if="car.months.length > 1" class="mb-6">
            <h2 class="text-sm font-semibold text-gray-800 dark:text-gray-200 mb-2">{{ t('share_car.months_title') }}</h2>
            <div class="flex items-end gap-1 h-24" role="img" :aria-label="t('share_car.months_title')">
              <div v-for="m in car.months" :key="m.month" class="flex-1 flex flex-col items-center gap-1 min-w-0">
                <div class="w-full rounded-t bg-emerald-500/70 dark:bg-emerald-400/70"
                  :style="{ height: barHeight(m.consumptionKwhPer100km) }"
                  :title="m.consumptionKwhPer100km != null ? formatConsumption(m.consumptionKwhPer100km) : ''" />
                <span class="text-[9px] text-gray-400 dark:text-gray-500 truncate w-full text-center">{{ monthLabel(m.month) }}</span>
              </div>
            </div>
            <p class="text-[11px] text-gray-400 dark:text-gray-500 mt-1">{{ consumptionUnitLabel() }}</p>
          </section>

          <section v-if="car.recentCharges.length">
            <h2 class="text-sm font-semibold text-gray-800 dark:text-gray-200 mb-1">{{ t('share_car.charges_title') }}</h2>
            <p class="text-[11px] text-gray-400 dark:text-gray-500 mb-2">{{ t('share_car.charges_hint') }}</p>
            <ul class="divide-y divide-gray-100 dark:divide-gray-800">
              <li v-for="(c, i) in car.recentCharges" :key="i" class="py-2.5 flex items-center justify-between gap-3 text-sm">
                <div class="min-w-0">
                  <span class="text-gray-900 dark:text-gray-100 tabular-nums">{{ dateLabel(c.chargedOn) }}</span>
                  <span class="text-gray-500 dark:text-gray-400 ml-2 text-xs">
                    {{ c.chargingType && c.chargingType !== 'UNKNOWN' ? c.chargingType : '' }}
                    <template v-if="c.maxChargingPowerKw != null"> {{ formatDecimal(c.maxChargingPowerKw, 0) }} kW</template>
                    <template v-if="c.publicCharging === true"> · {{ t('share_car.public_charge') }}</template>
                  </span>
                </div>
                <div class="text-right tabular-nums whitespace-nowrap">
                  <span v-if="c.kwhCharged != null" class="text-gray-900 dark:text-gray-100">{{ formatDecimal(c.kwhCharged, 1) }} kWh</span>
                  <span v-if="c.costEur != null" class="text-gray-500 dark:text-gray-400 ml-2">{{ formatCurrency(c.costEur) }}</span>
                  <div v-if="c.consumptionKwhPer100km != null" class="text-xs text-gray-400 dark:text-gray-500">{{ formatConsumption(c.consumptionKwhPer100km) }}</div>
                </div>
              </li>
            </ul>
          </section>

          <div class="mt-10 rounded-xl border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800 p-4 text-sm text-gray-600 dark:text-gray-300">
            {{ t('share_car.cta_text') }}
            <RouterLink to="/register" class="text-emerald-600 dark:text-emerald-400 font-semibold hover:underline">
              {{ t('common.free_start') }}
            </RouterLink>
          </div>
        </article>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, RouterLink } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useHead } from '@unhead/vue'
import { TruckIcon } from '@heroicons/vue/24/outline'
import PublicNav from '../components/shared/PublicNav.vue'
import { carShareService, type PublicCar } from '../api/carShareService'
import { useAuthStore } from '../stores/auth'
import { useLocaleFormat } from '../composables/useLocaleFormat'

/**
 * Oeffentliche Ansicht eines geteilten Fahrzeugs - der Link, den ein Nutzer
 * einem Kollegen schickt.
 *
 * Bewusst noindex: viele, einander aehnliche Seiten mit fremden Nutzerdaten
 * wuerden die Modell-Seiten kannibalisieren, die den SEO-Wert tragen.
 */
const BASE_URL = 'https://ev-monitor.net'

const route = useRoute()
const { t, locale } = useI18n()
const authStore = useAuthStore()
const { formatDecimal, formatConsumption, consumptionUnitLabel, formatDistance, formatCurrency, formatCostPerKwh } = useLocaleFormat()

const car = ref<PublicCar | null>(null)
const loading = ref(true)
const token = computed(() => String(route.params.token ?? ''))
const imageUrl = computed(() => `/api/public/car/${token.value}/image`)
const isAuthenticated = computed(() => !!authStore.token)

const subtitle = computed(() => {
  const c = car.value
  if (!c) return ''
  const parts: string[] = []
  if (c.year) parts.push(String(c.year))
  if (c.totalCharges) parts.push(t('share_car.charges_count', { n: c.totalCharges }))
  return parts.join(' · ')
})

interface Tile { key: string; label: string; value: string }
const tiles = computed<Tile[]>(() => {
  const c = car.value
  if (!c) return []
  const out: Tile[] = []
  if (c.avgConsumptionKwhPer100km != null) out.push({ key: 'cons', label: t('share_car.tile_consumption'), value: formatConsumption(c.avgConsumptionKwhPer100km) })
  if (c.costPer100km != null) out.push({ key: 'cost100', label: t('share_car.tile_cost_per_100'), value: formatCurrency(c.costPer100km) })
  else if (c.avgCostPerKwh != null) out.push({ key: 'costkwh', label: t('share_car.tile_cost_per_kwh'), value: formatCostPerKwh(c.avgCostPerKwh) })
  if (c.totalDistanceKm != null) out.push({ key: 'dist', label: t('share_car.tile_distance'), value: formatDistance(c.totalDistanceKm) })
  if (c.publicChargingSharePercent != null) out.push({ key: 'pub', label: t('share_car.tile_public_share'), value: `${formatDecimal(c.publicChargingSharePercent, 0)} %` })
  else if (c.totalKwhCharged != null) out.push({ key: 'kwh', label: t('share_car.tile_kwh'), value: `${formatDecimal(c.totalKwhCharged, 0)} kWh` })
  return out
})

const maxMonthly = computed(() =>
  Math.max(0, ...(car.value?.months.map(m => m.consumptionKwhPer100km ?? 0) ?? [])))

function barHeight(v: number | null | undefined): string {
  if (v == null || maxMonthly.value <= 0) return '2px'
  return `${Math.max(4, Math.round((v / maxMonthly.value) * 100))}%`
}

function monthLabel(iso: string): string {
  return new Date(`${iso}T00:00:00`).toLocaleDateString(locale.value, { month: 'short' })
}

function dateLabel(iso: string): string {
  return new Date(`${iso}T00:00:00`).toLocaleDateString(locale.value, { day: '2-digit', month: '2-digit', year: 'numeric' })
}

const shareTitle = computed(() => car.value?.carModel || t('share_car.fallback_title'))
const shareDescription = computed(() => {
  const c = car.value
  if (!c || c.avgConsumptionKwhPer100km == null) return t('share_car.og_description_plain')
  return t('share_car.og_description', { consumption: formatConsumption(c.avgConsumptionKwhPer100km) })
})

useHead(computed(() => ({
  title: `${shareTitle.value} - EV Monitor`,
  meta: [
    { name: 'description', content: shareDescription.value },
    { name: 'robots', content: 'noindex, follow' },
    { property: 'og:type', content: 'website' },
    { property: 'og:title', content: shareTitle.value },
    { property: 'og:description', content: shareDescription.value },
    { property: 'og:url', content: `${BASE_URL}/fahrzeug/${token.value}` },
    ...(car.value?.hasImage ? [{ property: 'og:image', content: `${BASE_URL}/api/public/car/${token.value}/image` }] : []),
  ],
})))

onMounted(async () => {
  try {
    car.value = await carShareService.getPublic(token.value)
  } catch {
    car.value = null
  } finally {
    loading.value = false
  }
})
</script>
