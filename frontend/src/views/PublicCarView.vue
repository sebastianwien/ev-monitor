<template>
  <div class="pc min-h-screen">
    <PublicNav />
    <main class="max-w-3xl mx-auto px-4 pb-14 md:pt-6">
      <div v-if="loading" class="text-center py-16 text-[var(--pc-ink-3)]">
        {{ t('common.loading') }}
      </div>

      <div v-else-if="!car" class="text-center py-16">
        <p class="text-lg font-semibold mb-2">{{ t('share_car.not_found_title') }}</p>
        <p class="text-sm text-[var(--pc-ink-3)] mb-4">{{ t('share_car.not_found_body') }}</p>
        <RouterLink to="/" class="underline">{{ t('share_car.to_start') }}</RouterLink>
      </div>

      <article v-else>
        <!-- Titel liegt ueber der unteren Kante des Fotos, das Foto blendet in den Grund aus. -->
        <header class="relative -mx-4 md:mx-0 md:rounded-md overflow-hidden" :class="car.hasImage ? 'aspect-[4/3] md:aspect-[2/1]' : ''">
          <template v-if="car.hasImage">
            <img :src="imageUrl" :alt="car.carModel ?? ''" class="absolute inset-0 w-full h-full object-cover" />
            <div class="absolute inset-0 pc-fade" />
          </template>
          <div :class="car.hasImage ? 'absolute left-4 right-4 bottom-2' : 'pt-6'">
            <h1 class="pc-disp text-[clamp(56px,16vw,96px)] leading-[.92] text-balance">
              {{ car.carModel || t('share_car.fallback_title') }}
            </h1>
            <p class="flex flex-wrap gap-x-4 mt-1.5 text-[var(--pc-ink-2)] tabular-nums">
              <span v-if="car.year">{{ t('share_car.built', { year: car.year }) }}</span>
              <span v-if="car.totalCharges">{{ t('share_car.charges_count', { n: car.totalCharges }) }}</span>
              <span v-if="car.totalDistanceKm != null">{{ formatDistance(car.totalDistanceKm) }}</span>
              <span v-if="sinceLabel">{{ sinceLabel }}</span>
            </p>
          </div>
        </header>

        <!-- Urteil: eine Zahl, ein Satz, eine Skala -->
        <section v-if="car.avgConsumptionKwhPer100km != null" class="pt-6 pb-5 border-b-2 border-[var(--pc-ink)]">
          <p class="pc-eyebrow">{{ t('share_car.verdict_label') }}</p>
          <div class="flex items-baseline gap-2.5 flex-wrap">
            <span class="pc-disp text-[clamp(72px,22vw,112px)] leading-[.92] tabular-nums">{{ formatConsumption(car.avgConsumptionKwhPer100km, { showUnit: false }) }}</span>
            <span class="text-lg text-[var(--pc-ink-2)]">{{ consumptionUnitLabel() }}</span>
          </div>
          <p class="mt-2 text-[17px] max-w-[32em] text-balance">
            <template v-if="peer">
              <span :class="peer.delta <= 0 ? 'text-[var(--pc-good)] font-semibold' : 'text-[var(--pc-warn)] font-semibold'">{{ peer.headline }}.</span>
              <template v-if="car.modelPagePath"> <RouterLink :to="car.modelPagePath" class="underline decoration-[var(--pc-rule)] underline-offset-4">{{ peer.detail }}</RouterLink>.</template>
              <template v-else> {{ peer.detail }}.</template>
            </template>
            <template v-if="seasonSentence"> {{ seasonSentence }}</template>
          </p>

          <svg v-if="peer" class="w-full max-w-[520px] h-auto mt-4 overflow-visible" viewBox="0 0 400 46" role="img" :aria-label="peer.headline">
            <line x1="10" y1="22" x2="390" y2="22" stroke="var(--pc-rule)" stroke-width="2" />
            <g font-size="11" fill="var(--pc-ink-3)" class="tabular-nums">
              <text x="10" y="42">{{ formatConsumption(peer.scale.minLabel, { showUnit: false }) }} {{ t('share_car.scale_min') }}</text>
              <text x="390" y="42" text-anchor="end">{{ formatConsumption(peer.scale.maxLabel, { showUnit: false }) }} {{ t('share_car.scale_max') }}</text>
            </g>
            <line :x1="scaleX(peer.scale.avgPos)" y1="12" :x2="scaleX(peer.scale.avgPos)" y2="32" stroke="var(--pc-ink-3)" stroke-width="1.5" stroke-dasharray="3 3" />
            <text :x="scaleX(peer.scale.avgPos)" y="8" text-anchor="middle" font-size="11" fill="var(--pc-ink-2)">{{ t('share_car.scale_avg', { avg: peer.avgLabel }) }}</text>
            <circle :cx="scaleX(peer.scale.youPos)" cy="22" r="7" :fill="peer.delta <= 0 ? 'var(--pc-good)' : 'var(--pc-warn)'" />
            <circle :cx="scaleX(peer.scale.youPos)" cy="22" r="2.5" fill="var(--pc-paper)" />
          </svg>
        </section>

        <div class="md:grid md:grid-cols-2 md:gap-10">
          <!-- Bilanz als Datenblatt -->
          <section v-if="ledger.length" class="mt-7 border-b border-[var(--pc-rule)]">
            <h2 class="pc-h2">{{ t('share_car.ledger_title') }}</h2>
            <dl>
              <div v-for="row in ledger" :key="row.key" class="grid grid-cols-[1fr_auto] items-baseline py-2.5 border-t border-[var(--pc-rule)]">
                <dt class="text-[var(--pc-ink-2)]">
                  {{ row.label }}
                  <small v-if="row.hint" class="block text-xs text-[var(--pc-ink-3)]">{{ row.hint }}</small>
                </dt>
                <dd class="text-[22px] font-semibold tabular-nums">
                  {{ row.value }}<i v-if="row.unit" class="not-italic text-[13px] font-normal text-[var(--pc-ink-3)] ml-1">{{ row.unit }}</i>
                </dd>
              </div>
            </dl>
          </section>

          <!-- Verlauf als Stufenlinie mit Schnitt und Winterband -->
          <section v-if="chart.points.length" class="mt-7">
            <h2 class="pc-h2">{{ t('share_car.months_title') }} <span class="normal-case tracking-normal">{{ consumptionUnitLabel() }}</span></h2>
            <svg class="w-full h-auto overflow-visible" :viewBox="`0 0 ${chart.width} ${chart.height}`" role="img" :aria-label="t('share_car.months_title')">
              <rect v-for="(b, i) in chart.winterBands" :key="i" :x="b.x" :y="chart.plot.top" :width="b.width" :height="chart.plot.bottom - chart.plot.top" fill="var(--pc-band)" />
              <g stroke="var(--pc-rule-2)" stroke-width="1">
                <line v-for="tk in chart.ticks" :key="tk.value" :x1="chart.plot.left" :y1="tk.y" :x2="chart.plot.right" :y2="tk.y" />
              </g>
              <g font-size="11" fill="var(--pc-ink-3)" text-anchor="end">
                <text v-for="tk in chart.ticks" :key="tk.value" :x="chart.plot.left - 6" :y="tk.y + 4">{{ formatConsumption(tk.value, { showUnit: false, decimals: isImperial ? 1 : 0 }) }}</text>
              </g>
              <line v-if="chart.avgY != null" :x1="chart.plot.left" :y1="chart.avgY" :x2="chart.plot.right" :y2="chart.avgY" stroke="var(--pc-ink-3)" stroke-width="1.5" stroke-dasharray="4 4" />
              <path :d="chart.path" fill="none" stroke="var(--pc-ink)" stroke-width="2.5" />
              <g font-size="11" fill="var(--pc-ink)" text-anchor="middle" class="tabular-nums">
                <text v-for="p in chart.points" :key="p.month" :x="p.cx" :y="p.y - 6">{{ formatConsumption(p.value, { showUnit: false }) }}</text>
              </g>
              <g font-size="11" fill="var(--pc-ink-3)" text-anchor="middle">
                <text v-for="p in chart.points" :key="p.month" :x="p.cx" :y="chart.height - 4">{{ monthLabel(p.month) }}</text>
              </g>
            </svg>
            <p class="flex flex-wrap gap-4 text-xs text-[var(--pc-ink-3)] mt-1.5">
              <span class="pc-lg pc-lg-you">{{ t('share_car.legend_you') }}</span>
              <span v-if="peer" class="pc-lg pc-lg-avg">{{ t('share_car.legend_avg', { avg: peer.avgLabel }) }}</span>
              <span v-if="chart.winterBands.length" class="pc-lg pc-lg-winter">{{ t('share_car.legend_winter') }}</span>
            </p>
          </section>
        </div>

        <section v-if="car.recentCharges.length" class="mt-7">
          <h2 class="pc-h2">{{ t('share_car.charges_title') }} <span class="normal-case tracking-normal">{{ t('share_car.charges_hint') }}</span></h2>
          <ol>
            <li v-for="(c, i) in car.recentCharges" :key="i"
              class="grid grid-cols-[44px_1fr_auto] gap-2.5 items-center py-2.5 border-t"
              :class="i === 0 ? 'border-[var(--pc-rule)]' : 'border-[var(--pc-rule-2)]'">
              <span class="pc-badge" :class="{ 'pc-badge-dc': c.chargingType === 'DC' }">{{ chargeBadge(c) }}</span>
              <div class="text-[13px] text-[var(--pc-ink-2)] min-w-0">
                <b class="block font-medium text-[15px] text-[var(--pc-ink)] tabular-nums">{{ dateLabel(c.chargedOn) }}</b>
                {{ chargeFacts(c) }}
              </div>
              <div class="text-right tabular-nums whitespace-nowrap">
                <b v-if="c.kwhCharged != null" class="block text-[17px] font-semibold">{{ formatDecimal(c.kwhCharged, 1) }} kWh</b>
                <small class="text-xs text-[var(--pc-ink-3)]">
                  <template v-if="c.costEur != null">{{ formatCurrency(c.costEur) }}</template>
                  <template v-if="c.costEur != null && c.consumptionKwhPer100km != null"> · </template>
                  <template v-if="c.consumptionKwhPer100km != null">{{ formatConsumption(c.consumptionKwhPer100km) }}</template>
                </small>
              </div>
            </li>
          </ol>
        </section>

        <section class="mt-10 pt-5 border-t-2 border-[var(--pc-ink)]">
          <h2 class="pc-disp text-[clamp(34px,9vw,48px)] leading-[.92]">{{ t('share_car.cta_title') }}</h2>
          <p class="mt-2 mb-3.5 text-[var(--pc-ink-2)] max-w-[32em]">{{ t('share_car.cta_text') }}</p>
          <RouterLink :to="registerTo"
            class="inline-flex items-center min-h-[44px] px-4 py-3 rounded-sm font-semibold bg-[var(--pc-ink)] text-[var(--pc-paper)] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--pc-good)]">
            {{ t('share_car.cta_button') }}
          </RouterLink>
        </section>
        <p class="mt-7 text-xs text-[var(--pc-ink-3)]">{{ t('share_car.footnote') }}</p>
      </article>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, RouterLink } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useHead } from '@unhead/vue'
import PublicNav from '../components/shared/PublicNav.vue'
import { carShareService, type PublicCar, type PublicCarCharge } from '../api/carShareService'
import { useLocaleFormat } from '../composables/useLocaleFormat'
import { buildStepChart, peerScale } from '../composables/publicCarChart'

/**
 * Oeffentliche Ansicht eines geteilten Fahrzeugs - der Link, den ein Nutzer
 * einem Kollegen schickt. Gestaltet wie ein Dauertest-Protokoll, nicht wie
 * das Dashboard: eine Zahl als Urteil, Bilanz als Datenblatt, Verlauf mit
 * Referenzlinie. Eigene Farbwelt, weil das Publikum ein anderes ist.
 *
 * Bewusst noindex: viele, einander aehnliche Seiten mit fremden Nutzerdaten
 * wuerden die Modell-Seiten kannibalisieren, die den SEO-Wert tragen.
 */
const BASE_URL = 'https://ev-monitor.net'

const route = useRoute()
const { t, locale } = useI18n()
const { formatDecimal, formatConsumption, consumptionUnitLabel, formatDistance, formatCurrency, formatCostPerKwh, isImperial } = useLocaleFormat()

const car = ref<PublicCar | null>(null)
const loading = ref(true)
const token = computed(() => String(route.params.token ?? ''))
const imageUrl = computed(() => `/api/public/car/${token.value}/image`)

const sinceLabel = computed(() => {
  const first = car.value?.months?.[0]?.month
  if (!first) return ''
  const label = new Date(`${first}T00:00:00`).toLocaleDateString(locale.value, { month: 'long', year: 'numeric' })
  return t('share_car.since', { month: label })
})

/**
 * Vergleich zum Community-Schnitt desselben Modells. Prozent-Abweichung, weil
 * ein Aussenstehender mit "18,8 kWh/100km" allein nichts anfangen kann.
 */
const peer = computed(() => {
  const c = car.value
  const p = c?.peerComparison
  if (!c || !p || c.avgConsumptionKwhPer100km == null || p.peerAvgConsumptionKwhPer100km <= 0) return null
  const you = c.avgConsumptionKwhPer100km
  const avg = p.peerAvgConsumptionKwhPer100km
  const deltaPct = Math.round(((you - avg) / avg) * 100)
  const scope = p.matchType === 'SPEC' ? t('share_car.peer_scope_spec') : t('share_car.peer_scope_model')
  const headline = deltaPct === 0
    ? t('share_car.peer_equal', { scope })
    : deltaPct < 0
      ? t('share_car.peer_below', { pct: Math.abs(deltaPct), scope })
      : t('share_car.peer_above', { pct: deltaPct, scope })
  const avgLabel = formatConsumption(avg, { showUnit: false })
  return {
    delta: deltaPct,
    avg,
    avgLabel,
    headline,
    detail: t('share_car.peer_detail', { avg: avgLabel, n: p.peerUsers }, p.peerUsers),
    scale: peerScale(you, avg),
  }
})

/** Prozentposition auf der Skala in SVG-Koordinaten (10 bis 390). */
function scaleX(pos: number): number {
  return 10 + (pos / 100) * 380
}

const seasonSentence = computed(() => {
  const c = car.value
  if (!c || c.summerConsumptionKwhPer100km == null || c.winterConsumptionKwhPer100km == null) return ''
  return t('share_car.seasons', {
    summer: formatConsumption(c.summerConsumptionKwhPer100km, { showUnit: false }),
    winter: formatConsumption(c.winterConsumptionKwhPer100km, { showUnit: false }),
  })
})

interface LedgerRow { key: string; label: string; hint?: string; value: string; unit?: string }
const ledger = computed<LedgerRow[]>(() => {
  const c = car.value
  if (!c) return []
  const out: LedgerRow[] = []
  if (c.costPer100km != null) out.push({ key: 'cost100', label: t('share_car.tile_cost_per_100'), value: formatCurrency(c.costPer100km) })
  if (c.avgCostPerKwh != null) out.push({ key: 'costkwh', label: t('share_car.tile_price_kwh'), value: formatCostPerKwh(c.avgCostPerKwh) })
  if (c.publicChargingSharePercent != null) out.push({ key: 'pub', label: t('share_car.tile_public_share'), hint: t('share_car.tile_public_share_hint'), value: formatDecimal(c.publicChargingSharePercent, 0), unit: '%' })
  if (c.totalKwhCharged != null) out.push({ key: 'kwh', label: t('share_car.tile_kwh'), value: formatDecimal(c.totalKwhCharged, 0), unit: 'kWh' })
  if (c.totalDistanceKm != null) out.push({ key: 'dist', label: t('share_car.tile_distance'), value: formatDistance(c.totalDistanceKm) })
  return out
})

const chart = computed(() => buildStepChart(car.value?.months ?? [], peer.value?.avg ?? null))

/** Monat kurz, bei Jahreswechsel innerhalb der Reihe mit Jahr. */
function monthLabel(iso: string): string {
  const d = new Date(`${iso}T00:00:00`)
  const years = new Set(chart.value.points.map(p => p.month.slice(0, 4)))
  return d.toLocaleDateString(locale.value, years.size > 1 ? { month: 'short', year: '2-digit' } : { month: 'short' })
}

function dateLabel(iso: string): string {
  return new Date(`${iso}T00:00:00`).toLocaleDateString(locale.value, { day: 'numeric', month: 'short', year: 'numeric' })
}

function chargeBadge(c: PublicCarCharge): string {
  return c.chargingType && c.chargingType !== 'UNKNOWN' ? c.chargingType : '·'
}

function chargeFacts(c: PublicCarCharge): string {
  const parts: string[] = []
  if (c.durationMinutes != null && c.durationMinutes > 0) {
    const h = Math.floor(c.durationMinutes / 60)
    const m = c.durationMinutes % 60
    parts.push(h > 0 ? t('share_car.duration_hm', { h, m }) : t('share_car.duration_min', { m }))
  }
  if (c.maxChargingPowerKw != null) parts.push(t('share_car.peak_kw', { kw: formatDecimal(c.maxChargingPowerKw, 0) }))
  if (c.publicCharging === true) parts.push(t('share_car.public_charge'))
  return parts.join(' · ')
}

const shareTitle = computed(() => {
  const c = car.value
  const model = c?.carModel || t('share_car.fallback_title')
  if (!c || c.avgConsumptionKwhPer100km == null) return model
  const parts = [formatConsumption(c.avgConsumptionKwhPer100km)]
  if (peer.value) parts.push(peer.value.headline)
  return t('share_car.og_title', { model, facts: parts.join(', ') })
})

/** Der Empfehlungscode des Teilenden wandert in die Registrierung, sonst nirgendwohin. */
const registerTo = computed(() => {
  const ref = route.query.ref
  return ref ? { path: '/register', query: { ref: String(ref) } } : '/register'
})
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
    { property: 'og:image', content: `${BASE_URL}/api/public/car/${token.value}/og.png` },
    { property: 'og:image:width', content: '1200' },
    { property: 'og:image:height', content: '630' },
    { name: 'twitter:card', content: 'summary_large_image' },
    { name: 'twitter:title', content: shareTitle.value },
    { name: 'twitter:description', content: shareDescription.value },
    { name: 'twitter:image', content: `${BASE_URL}/api/public/car/${token.value}/og.png` },
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

<style scoped>
/* Barlow Condensed (OFL), selbst gehostet wie die Nummernschild-Fonts. Nur auf dieser Seite. */
@font-face {
  font-family: 'Barlow Condensed';
  font-weight: 700;
  font-display: swap;
  src: url('/fonts/barlow-condensed-latin-700-normal.woff2') format('woff2');
}

/* Eigene Farbwelt: Papier statt Dashboard. Graphit im Dark Mode. */
.pc {
  --pc-paper: #F7F6F2; --pc-ink: #14161A; --pc-ink-2: #4B4F58; --pc-ink-3: #767B86;
  --pc-rule: #D9D7D0; --pc-rule-2: #EDECE6; --pc-band: #ECEAE2;
  --pc-good: #0F8A5F; --pc-warn: #B86E08;
  background: var(--pc-paper);
  color: var(--pc-ink);
}
.dark .pc {
  --pc-paper: #101215; --pc-ink: #F1EFE8; --pc-ink-2: #B6B3AA; --pc-ink-3: #8A8880;
  --pc-rule: #2C2F35; --pc-rule-2: #1C1F24; --pc-band: #181B20;
  --pc-good: #2FC48E; --pc-warn: #F0A030;
}
.pc-disp { font-family: 'Barlow Condensed', 'Arial Narrow', system-ui, sans-serif; font-weight: 700; letter-spacing: -0.01em; }
.pc-eyebrow { font-size: 11px; letter-spacing: .14em; text-transform: uppercase; color: var(--pc-ink-3); font-weight: 500; }
.pc-h2 { font-size: 12px; letter-spacing: .14em; text-transform: uppercase; font-weight: 500; color: var(--pc-ink-3); margin-bottom: 6px; display: flex; justify-content: space-between; align-items: baseline; gap: 12px; }
.pc-fade { background: linear-gradient(180deg, rgba(0,0,0,0) 45%, var(--pc-paper) 100%); }
.pc-badge { font-size: 11px; font-weight: 600; letter-spacing: .06em; border: 1px solid var(--pc-rule); border-radius: 3px; padding: 3px 0; text-align: center; color: var(--pc-ink-2); }
.pc-badge-dc { background: var(--pc-ink); color: var(--pc-paper); border-color: var(--pc-ink); }
.pc-lg::before { content: ""; display: inline-block; width: 18px; height: 0; border-top: 2px solid var(--pc-ink); vertical-align: middle; margin-right: 6px; }
.pc-lg-avg::before { border-top-style: dashed; border-color: var(--pc-ink-3); }
.pc-lg-winter::before { border: 0; height: 10px; background: var(--pc-band); }
</style>
