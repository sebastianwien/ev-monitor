<template>
  <div :class="isAuthenticated ? '' : 'min-h-screen bg-gray-50 dark:bg-gray-950'">
    <PublicNav />
    <main class="max-w-3xl mx-auto md:px-4 py-6 md:py-10">
      <div class="bg-white dark:bg-gray-900 border-y md:border border-gray-200 dark:border-gray-800 md:rounded-xl md:shadow-sm px-4 md:px-6 py-6 md:py-8">
        <div v-if="loading" class="text-center py-16 text-gray-500 dark:text-gray-400">
          {{ t('common.loading') }}
        </div>

        <div v-else-if="!curve" class="text-center py-16">
          <p class="text-lg font-semibold text-gray-900 dark:text-gray-100 mb-2">
            {{ t('share_curve.not_found_title') }}
          </p>
          <p class="text-sm text-gray-500 dark:text-gray-400 mb-4">{{ t('share_curve.not_found_body') }}</p>
          <RouterLink to="/" class="text-emerald-600 dark:text-emerald-400 hover:underline">
            {{ t('share_curve.to_start') }}
          </RouterLink>
        </div>

        <article v-else>
          <div class="flex items-start gap-3 mb-1">
            <BoltIcon class="w-6 h-6 text-emerald-600 dark:text-emerald-400 flex-shrink-0 mt-1" aria-hidden="true" />
            <h1 class="text-2xl md:text-4xl font-bold text-gray-900 dark:text-gray-100">
              {{ curve.carModel || t('share_curve.fallback_title') }}
            </h1>
          </div>
          <p v-if="subtitle" class="text-sm text-gray-500 dark:text-gray-400 mb-6 ml-9">{{ subtitle }}</p>

          <!-- Kennzahlen zuerst: auf Mobile beantwortet die Zeile die Frage
               "wie schnell war die Ladung" ohne Interaktion mit der Kurve. -->
          <div v-if="tiles.length" class="grid grid-cols-2 sm:grid-cols-4 gap-2 mb-5">
            <div
              v-for="tile in tiles"
              :key="tile.key"
              class="rounded-lg border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800/60 px-3 py-2"
            >
              <div class="text-[10px] uppercase tracking-wide text-gray-500 dark:text-gray-400 truncate">{{ tile.label }}</div>
              <div class="text-base font-semibold text-gray-900 dark:text-gray-100 tabular-nums whitespace-nowrap">{{ tile.value }}</div>
            </div>
          </div>

          <PowerCurveChart
            :points="curve.points"
            :height="260"
            :height-desktop="340"
            x-axis-mode="duration"
            :aria-label="t('dashboard.power_curve_title')"
            :soc-before-percent="curve.socBefore ?? null"
            :soc-after-percent="curve.socAfter ?? null"
            :soc-axis-label="t('dashboard.power_curve_soc_axis')"
          />

          <div class="mt-3 flex flex-wrap items-center gap-x-4 gap-y-1.5 text-[11px] text-gray-500 dark:text-gray-400">
            <span class="inline-flex items-center gap-1.5">
              <span class="w-3 h-0.5 rounded-full bg-emerald-500 flex-shrink-0" />
              {{ t('dashboard.power_curve_legend_kw') }}
            </span>
            <span v-if="socSeries" class="inline-flex items-center gap-1.5">
              <span class="w-3 border-b border-dashed border-gray-400 dark:border-gray-500 flex-shrink-0" />
              {{ t('dashboard.power_curve_legend_soc') }}
            </span>
          </div>
          <p v-if="socSeries?.derived" class="mt-1 text-[11px] text-gray-400 dark:text-gray-500">
            {{ t('dashboard.power_curve_soc_derived_hint') }}
          </p>

          <div class="mt-10 rounded-xl border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800 p-4 text-sm text-gray-600 dark:text-gray-300">
            {{ t('share_curve.cta_text') }}
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
import { BoltIcon } from '@heroicons/vue/24/outline'
import PublicNav from '../components/shared/PublicNav.vue'
import PowerCurveChart from '../components/charging/PowerCurveChart.vue'
import { buildSocSeries } from '../components/charging/powerCurveSeries'
import { curveShareService, type PublicCurve } from '../api/curveShareService'
import { formatDuration } from '../components/charging/powerCurveScrub'
import { useAuthStore } from '../stores/auth'

/**
 * Oeffentliche Ansicht einer geteilten Ladekurve - die Seite, die in Foren und
 * sozialen Netzen landet.
 *
 * Bewusst noindex: es handelt sich um viele, einander sehr aehnliche Seiten mit
 * fremden Nutzerdaten. Indexiert wuerden sie die Modell-Seiten kannibalisieren,
 * die den SEO-Wert tragen. Fuer die Link-Vorschau reicht noindex vollkommen -
 * Social-Crawler lesen die og-Tags unabhaengig davon.
 */
const BASE_URL = 'https://ev-monitor.net'

const route = useRoute()
const { t, locale } = useI18n()
const authStore = useAuthStore()

const curve = ref<PublicCurve | null>(null)
const loading = ref(true)

const isAuthenticated = computed(() => !!authStore.token)
const socSeries = computed(() =>
  curve.value ? buildSocSeries(curve.value.points, curve.value.socBefore, curve.value.socAfter) : null)

const chargedOnLabel = computed(() => {
  const iso = curve.value?.chargedOn
  if (!iso) return ''
  return new Date(`${iso}T00:00:00`).toLocaleDateString(locale.value, {
    day: '2-digit', month: '2-digit', year: 'numeric',
  })
})

const subtitle = computed(() => {
  const c = curve.value
  if (!c) return ''
  return [c.cpoName, chargedOnLabel.value].filter(Boolean).join(' · ')
})

interface Tile { key: string; label: string; value: string }

const tiles = computed<Tile[]>(() => {
  const c = curve.value
  if (!c) return []
  const out: Tile[] = []
  if (c.peakKw != null) {
    out.push({ key: 'peak', label: t('dashboard.power_curve_peak'), value: `${round(c.peakKw, 0)} kW` })
  }
  if (c.kwhCharged != null) {
    out.push({ key: 'energy', label: t('dashboard.power_curve_energy'), value: `${round(c.kwhCharged, 1)} kWh` })
  }
  if (c.durationMinutes != null) {
    out.push({ key: 'duration', label: t('dashboard.power_curve_duration'), value: formatDuration(c.durationMinutes * 60_000) })
  }
  if (c.socBefore != null && c.socAfter != null) {
    out.push({
      key: 'soc',
      label: t('dashboard.power_curve_soc'),
      value: `${round(c.socBefore, 0)} → ${round(c.socAfter, 0)} %`,
    })
  }
  return out
})

function round(value: number, decimals: number): string {
  return Number(value).toLocaleString(locale.value, {
    minimumFractionDigits: decimals, maximumFractionDigits: decimals,
  })
}

const shareTitle = computed(() => {
  const c = curve.value
  if (!c) return t('share_curve.fallback_title')
  const model = c.carModel || t('share_curve.fallback_title')
  return c.peakKw != null
    ? t('share_curve.og_title', { model, peak: round(c.peakKw, 0) })
    : model
})

const shareDescription = computed(() => {
  const c = curve.value
  if (!c) return ''
  return t('share_curve.og_description', {
    soc: c.socBefore != null && c.socAfter != null ? `${round(c.socBefore, 0)} → ${round(c.socAfter, 0)} %` : '-',
    kwh: c.kwhCharged != null ? round(c.kwhCharged, 1) : '-',
    minutes: c.durationMinutes ?? '-',
  })
})

useHead(computed(() => {
  const token = String(route.params.token ?? '')
  const url = `${BASE_URL}/ladekurve/${token}`
  const image = `${BASE_URL}/api/public/curve/${token}/og.png`
  return {
    title: `${shareTitle.value} - EV Monitor`,
    meta: [
      { name: 'description', content: shareDescription.value },
      // Einzelne geteilte Kurven gehoeren nicht in den Index, siehe Kopfkommentar.
      { name: 'robots', content: 'noindex, follow' },
      { property: 'og:type', content: 'website' },
      { property: 'og:title', content: shareTitle.value },
      { property: 'og:description', content: shareDescription.value },
      { property: 'og:url', content: url },
      { property: 'og:image', content: image },
      { property: 'og:image:width', content: '1200' },
      { property: 'og:image:height', content: '630' },
      { name: 'twitter:card', content: 'summary_large_image' },
      { name: 'twitter:title', content: shareTitle.value },
      { name: 'twitter:description', content: shareDescription.value },
      { name: 'twitter:image', content: image },
    ],
  }
}))

onMounted(async () => {
  try {
    curve.value = await curveShareService.getPublic(String(route.params.token))
  } catch {
    // Unbekannt, widerrufen oder kaputt - der Besucher soll die Faelle nicht
    // unterscheiden koennen, also in allen dieselbe Seite.
    curve.value = null
  } finally {
    loading.value = false
  }
})
</script>
