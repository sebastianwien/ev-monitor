<template>
  <div :class="isAuthenticated ? '' : 'min-h-screen bg-white dark:bg-gray-900'">
    <PublicNav />
    <!-- Mobile edge-to-edge, ab 768 px Karte mit Kante (App-CI) -->
    <main class="max-w-3xl mx-auto md:px-4 py-6 md:py-10">
      <div class="bg-white dark:bg-gray-900 md:edge-card md:rounded-2xl px-4 md:px-8 py-2 md:py-8">
        <p v-if="loading" class="text-center py-16 text-gray-500 dark:text-gray-400">
          {{ t('common.loading') }}
        </p>

        <div v-else-if="!curve" class="py-16 text-center">
          <p class="text-[22px] font-bold text-gray-900 dark:text-gray-100">
            {{ t('share_curve.not_found_title') }}
          </p>
          <p class="mt-2 text-[15px] text-gray-500 dark:text-gray-400">{{ t('share_curve.not_found_body') }}</p>
          <RouterLink
            to="/"
            class="btn-3d cta-shadow-sm mt-6 inline-flex min-h-11 items-center justify-center rounded-full border-2 border-edge bg-white px-6 text-sm font-semibold text-gray-800 hover:bg-gray-50 dark:bg-white/10 dark:text-gray-200 dark:hover:bg-white/15"
          >
            {{ t('share_curve.to_start') }}
          </RouterLink>
        </div>

        <article v-else>
          <!-- Kopf wie im Ladekurven-Sheet: Label, Modell als Titel, Satz statt Kacheln -->
          <p class="text-[12px] font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400">
            {{ t('dashboard.power_curve_title') }}<template v-if="subtitle"> · {{ subtitle }}</template>
          </p>
          <h1 class="mt-2 text-[28px] md:text-[36px] font-bold leading-[1.1] text-gray-900 dark:text-gray-100">
            {{ curve.carModel || t('share_curve.fallback_title') }}
          </h1>
          <p v-if="headline" class="mt-2 text-lg font-semibold tabular-nums text-gray-700 dark:text-gray-300">
            {{ headline }}
          </p>

          <div class="mt-6 edge-box rounded-xl bg-white dark:bg-gray-700/50 p-3 md:p-4">
            <PowerCurveChart
              :points="curve.points"
              :height="240"
              :height-desktop="320"
              x-axis-mode="duration"
              :aria-label="t('dashboard.power_curve_title')"
              :soc-before-percent="curve.socBefore ?? null"
              :soc-after-percent="curve.socAfter ?? null"
              :soc-axis-label="t('dashboard.power_curve_soc_axis')"
              :readout="t('dashboard.power_curve_readout_peak')"
            />
          </div>

          <div v-if="statsRow.length" class="mt-6 grid grid-cols-3 gap-3">
            <div v-for="stat in statsRow" :key="stat.key" class="min-w-0">
              <p class="text-[12px] font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400 truncate">{{ stat.label }}</p>
              <p class="mt-0.5 whitespace-nowrap tabular-nums text-gray-900 dark:text-gray-100">
                <span class="text-[26px] font-bold">{{ stat.value }}</span>
                <span class="ml-1 text-[13px] text-gray-500 dark:text-gray-400">{{ stat.unit }}</span>
              </p>
            </div>
          </div>

          <!-- Einladung: einzige grüne Fläche der Seite -->
          <div class="mt-10 edge-box rounded-xl bg-white dark:bg-gray-700/50 p-4 md:p-5">
            <p class="text-[15px] leading-relaxed text-gray-700 dark:text-gray-300">{{ t('share_curve.cta_text') }}</p>
            <RouterLink
              to="/register"
              class="btn-3d cta-shadow-sm mt-4 flex min-h-11 w-full items-center justify-center rounded-full border-2 border-edge bg-green-600 px-4 text-sm font-semibold text-white md:w-auto md:inline-flex md:px-8 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-green-600"
            >
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
import PublicNav from '../components/shared/PublicNav.vue'
import PowerCurveChart from '../components/charging/PowerCurveChart.vue'
import { curveShareService, type PublicCurve } from '../api/curveShareService'
import { computeCurveStats } from '../components/charging/powerCurveStats'
import { chargeHeadline, chargeHeadlineText } from '../components/charging/powerCurveHeadline'
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

/** Kopfsatz wie im Sheet: "In 16 Min von 8 auf 66 %". Dauer aus der Freigabe, wie in der og-Beschreibung. */
const headline = computed(() => {
  const c = curve.value
  if (!c) return null
  const h = chargeHeadline({
    durationMs: c.durationMinutes != null ? c.durationMinutes * 60_000 : null,
    socBefore: c.socBefore,
    socAfter: c.socAfter,
    kwh: c.kwhCharged,
  })
  return chargeHeadlineText(h, t, (v: number) => round(v, 1))
})

interface Stat { key: string; label: string; value: string; unit: string }

/** Spitze und Schnitt aus den Kurvenpunkten, geladene Menge aus der Freigabe. Dauer und Ladestand stehen im Kopfsatz. */
const statsRow = computed<Stat[]>(() => {
  const c = curve.value
  if (!c) return []
  const fromCurve = computeCurveStats(c.points)
  const out: Stat[] = []
  const peak = c.peakKw ?? fromCurve?.peakKw
  if (peak != null) out.push({ key: 'peak', label: t('dashboard.power_curve_peak'), value: round(peak, 0), unit: 'kW' })
  if (fromCurve && fromCurve.durationMs > 0) {
    out.push({ key: 'avg', label: t('dashboard.power_curve_avg'), value: round(fromCurve.avgKw, 0), unit: 'kW' })
  }
  if (c.kwhCharged != null) out.push({ key: 'energy', label: t('dashboard.power_curve_energy'), value: round(c.kwhCharged, 1), unit: 'kWh' })
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
