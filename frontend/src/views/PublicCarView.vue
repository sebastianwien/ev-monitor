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
            class="w-full h-44 md:h-56 object-cover rounded-lg mb-4" loading="lazy" />

          <h1 class="text-2xl md:text-4xl font-bold text-gray-900 dark:text-gray-100 mb-1">
            {{ car.carModel || t('share_car.fallback_title') }}
          </h1>
          <p class="text-sm text-gray-500 dark:text-gray-400 mb-6">
            {{ subtitle }}
          </p>

          <div v-if="tiles.length" class="grid grid-cols-2 sm:grid-cols-4 gap-2 mb-6">
            <div v-for="tile in tiles" :key="tile.key"
              class="rounded-lg border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800/60 px-3 py-2">
              <div class="text-[10px] uppercase tracking-wide text-gray-500 dark:text-gray-400 truncate">{{ tile.label }}</div>
              <div class="text-base font-semibold text-gray-900 dark:text-gray-100 tabular-nums whitespace-nowrap">{{ tile.value }}</div>
            </div>
          </div>

          <div v-if="peer" class="mb-6 rounded-lg border px-3 py-2.5 flex items-start gap-3"
            :class="peer.delta <= 0
              ? 'border-emerald-200 dark:border-emerald-800 bg-emerald-50 dark:bg-emerald-900/20'
              : 'border-amber-200 dark:border-amber-800 bg-amber-50 dark:bg-amber-900/20'">
            <ArrowTrendingDownIcon v-if="peer.delta <= 0" class="w-5 h-5 shrink-0 mt-0.5 text-emerald-600 dark:text-emerald-400" aria-hidden="true" />
            <ArrowTrendingUpIcon v-else class="w-5 h-5 shrink-0 mt-0.5 text-amber-600 dark:text-amber-400" aria-hidden="true" />
            <div class="min-w-0">
              <p class="text-sm font-semibold text-gray-900 dark:text-gray-100">{{ peer.headline }}</p>
              <p class="text-xs text-gray-500 dark:text-gray-400 mt-0.5">{{ peer.detail }}</p>
            </div>
          </div>

          <section v-if="chartMonths.length > 1" class="mb-6">
            <h2 class="text-sm font-semibold text-gray-800 dark:text-gray-200 mb-2">
              {{ t('share_car.months_title') }} <span class="font-normal text-gray-400 dark:text-gray-500">({{ consumptionUnitLabel() }})</span>
            </h2>
            <div class="flex items-end gap-1" role="img" :aria-label="t('share_car.months_title')">
              <div v-for="m in chartMonths" :key="m.month" class="flex-1 min-w-0 text-center">
                <div class="text-[9px] text-gray-500 dark:text-gray-400 tabular-nums mb-0.5">{{ formatConsumption(m.value, { showUnit: false }) }}</div>
                <div class="w-full rounded-t bg-emerald-500/70 dark:bg-emerald-400/70" :style="{ height: m.px + 'px' }" />
                <div class="text-[9px] text-gray-400 dark:text-gray-500 truncate mt-1">{{ m.label }}</div>
              </div>
            </div>
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
import { ArrowTrendingDownIcon, ArrowTrendingUpIcon } from '@heroicons/vue/24/outline'
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

/**
 * Vergleich zum Community-Schnitt desselben Modells. Prozent-Abweichung, weil
 * ein Aussenstehender mit "18,8 kWh/100km" allein nichts anfangen kann.
 */
const peer = computed(() => {
  const c = car.value
  const p = c?.peerComparison
  if (!c || !p || c.avgConsumptionKwhPer100km == null || p.peerAvgConsumptionKwhPer100km <= 0) return null
  const deltaPct = Math.round(((c.avgConsumptionKwhPer100km - p.peerAvgConsumptionKwhPer100km) / p.peerAvgConsumptionKwhPer100km) * 100)
  const scope = p.matchType === 'SPEC' ? t('share_car.peer_scope_spec') : t('share_car.peer_scope_model')
  const headline = deltaPct === 0
    ? t('share_car.peer_equal', { scope })
    : deltaPct < 0
      ? t('share_car.peer_below', { pct: Math.abs(deltaPct), scope })
      : t('share_car.peer_above', { pct: deltaPct, scope })
  return {
    delta: deltaPct,
    headline,
    detail: t('share_car.peer_detail', { avg: formatConsumption(p.peerAvgConsumptionKwhPer100km), n: p.peerUsers }),
  }
})

/**
 * Nur Monate mit Verbrauchswert, chronologisch. Die Achse ist nicht fortlaufend,
 * deshalb traegt jedes Label den Monat und bei Jahreswechsel das Jahr.
 */
const CHART_MAX_PX = 72
const chartMonths = computed(() => {
  const withValue = (car.value?.months ?? []).filter(m => m.consumptionKwhPer100km != null && m.consumptionKwhPer100km > 0)
  const max = Math.max(0, ...withValue.map(m => m.consumptionKwhPer100km as number))
  let lastYear = ''
  return withValue.map(m => {
    const d = new Date(`${m.month}T00:00:00`)
    const year = String(d.getFullYear())
    const label = d.toLocaleDateString(locale.value, year !== lastYear ? { month: 'short', year: '2-digit' } : { month: 'short' })
    lastYear = year
    const value = m.consumptionKwhPer100km as number
    return { month: m.month, value, label, px: max > 0 ? Math.max(4, Math.round((value / max) * CHART_MAX_PX)) : 4 }
  })
})

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
