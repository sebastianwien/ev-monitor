<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'
import { LockOpenIcon, ShareIcon, PhotoIcon } from '@heroicons/vue/24/outline'
import BottomSheet from '../shared/BottomSheet.vue'
import SheetCloseButton from '../story/SheetCloseButton.vue'
import PowerCurveChart from './PowerCurveChart.vue'
import SocCurveChart from './SocCurveChart.vue'
import { computeCurveStats } from './powerCurveStats'
import { chargeHeadline, chargeHeadlineText, addedRangeKm } from './powerCurveHeadline'
import { useCurveShare } from '../../composables/useCurveShare'

/**
 * Ladekurve im Sheet (App-CI): Kopfsatz, Kurve mit fester Ablese-Zeile, drei
 * Kennzahlen, Teilen.
 *
 * Der Kopfsatz ("In 16 Min von 8 auf 66 %") und die nachgeladene Reichweite
 * beantworten die Frage ohne kWh-Wissen. Spitze und Schnitt kommen aus den
 * Kurvenpunkten (siehe computeCurveStats), SoC und die geladene Menge aus dem
 * Log - dieselbe Zahl, die der Eintrag im Feed zeigt.
 */
const props = defineProps<{
  loading: boolean
  points: { ts: number; kw: number; soc?: number | null }[]
  /** Gemessener Ladeverlauf - kommt statt der Leistungskurve, wenn die Quelle
   *  keine Leistung liefert (Smartcar). */
  socPoints?: { ts: number; soc: number }[]
  consumptionKwhPer100km?: number | null
  /** Datum/Uhrzeit der Ladung, wird als Untertitel gezeigt. */
  subtitle?: string
  socBeforeChargePercent?: number | null
  socAfterChargePercent?: number | null
  /** Geladene Menge aus dem Log - massgeblich, nicht das Integral der Kurve. */
  kwhCharged?: number | null
  /**
   * Ohne Freischaltung liegen keine Kurvenpunkte vor (Server-Gate). Statt eines
   * Schlosses im Feed zeigt das Overlay dann, worum es ueberhaupt geht.
   */
  locked?: boolean
  /** Ladedauer aus dem Log - im Teaser die einzige Quelle, sonst kommt sie aus der Kurve. */
  chargeDurationMinutes?: number | null
  /** Ziel des Upsell-CTA (/supporter oder /upgrade). */
  upsellTarget?: string
  /** Log-ID - nur gesetzt ist die Kurve teilbar. */
  logId?: string
}>()

const emit = defineEmits<{ close: [] }>()

const { t } = useI18n()

const sheetRef = ref<InstanceType<typeof BottomSheet> | null>(null)

// ── Teilen ────────────────────────────────────────────────────────────────
const { share, busy: shareBusy, error: shareError, load: loadShare, enable, revoke, shareLink, shareImage }
  = useCurveShare()
/** Kurzlebige Rueckmeldung nach dem Teilen ("Link kopiert"). */
const shareHint = ref<string | null>(null)
let hintTimer: ReturnType<typeof setTimeout> | null = null

const canShare = computed(() => !!props.logId && !props.locked && props.points.length > 0)

/** Ladeverlauf statt Ladekurve: nur wenn keine Leistung gemessen wurde. */
const socOnly = computed(() => props.points.length === 0 && (props.socPoints?.length ?? 0) > 0)

/** Durchschnittsleistung als eine Zahl. Bewusst keine kW-Reihe: ein aus dem
 *  Zaehler abgeleiteter Verlauf waere ein Vier-Minuten-Mittel, kein Momentanwert. */
const socAvgKw = computed(() => {
  const pts = props.socPoints ?? []
  if (props.kwhCharged == null || pts.length < 2) return null
  const hours = (pts[pts.length - 1].ts - pts[0].ts) / 3_600_000
  return hours > 0 ? Number(props.kwhCharged) / hours : null
})

function flashHint(message: string) {
  shareHint.value = message
  if (hintTimer) clearTimeout(hintTimer)
  hintTimer = setTimeout(() => { shareHint.value = null }, 4000)
}

const shareErrorText = computed(() => {
  switch (shareError.value) {
    case 'forbidden': return t('share_curve.error_forbidden')
    case 'no-curve': return t('share_curve.error_no_curve')
    case 'failed': return t('share_curve.error_failed')
    default: return null
  }
})

async function onShare() {
  if (!props.logId) return
  const target = share.value ?? await enable(props.logId)
  if (!target) return
  const outcome = await shareLink(target.url, t('dashboard.power_curve_title'))
  if (outcome === 'copied') flashHint(t('share_curve.link_copied'))
  else if (outcome === 'failed') flashHint(t('share_curve.error_failed'))
}

async function onShareImage() {
  if (!props.logId) return
  const target = share.value ?? await enable(props.logId)
  if (!target) return
  const outcome = await shareImage(target.token, `ladekurve-${target.token}.png`, t('dashboard.power_curve_title'))
  if (outcome === 'copied') flashHint(t('share_curve.image_saved'))
  else if (outcome === 'failed') flashHint(t('share_curve.error_failed'))
}

async function onRevoke() {
  if (props.logId) await revoke(props.logId)
}

const stats = computed(() => computeCurveStats(props.points))

/** Dauer: aus der Kurve, beim Ladeverlauf aus dessen Spanne, sonst (Teaser) aus dem Log. */
const durationMs = computed<number | null>(() => {
  if (stats.value && stats.value.durationMs > 0) return stats.value.durationMs
  const soc = props.socPoints ?? []
  if (socOnly.value && soc.length > 1) return soc[soc.length - 1].ts - soc[0].ts
  return props.chargeDurationMinutes ? props.chargeDurationMinutes * 60_000 : null
})

/** Geladene Menge: der Log-Wert, nur ohne ihn das Kurven-Integral. */
const energyKwh = computed<number | null>(() =>
  props.kwhCharged != null ? Number(props.kwhCharged) : stats.value?.energyKwh ?? null)

const headline = computed(() => {
  const h = chargeHeadline({
    durationMs: durationMs.value,
    socBefore: props.socBeforeChargePercent,
    socAfter: props.socAfterChargePercent,
    kwh: energyKwh.value,
  })
  return chargeHeadlineText(h, t, kwhText)
})

/** Im Teaser bleibt die Reichweite weg - sie ist Teil dessen, was freigeschaltet wird. */
const rangeKm = computed(() =>
  props.locked ? null : addedRangeKm(energyKwh.value, props.consumptionKwhPer100km))

function kwhText(value: number): string {
  return value.toLocaleString(undefined, { maximumFractionDigits: 1 })
}

interface Stat { key: string; label: string; value: string; unit: string }

/** Ohne Dauer und Ladestand, die stehen im Kopfsatz. */
const statsRow = computed<Stat[]>(() => {
  const out: Stat[] = []
  const round = (v: number) => v.toLocaleString(undefined, { maximumFractionDigits: v < 10 ? 1 : 0 })
  if (stats.value) {
    out.push({ key: 'peak', label: t('dashboard.power_curve_peak'), value: round(stats.value.peakKw), unit: 'kW' })
    out.push({ key: 'avg', label: t('dashboard.power_curve_avg'), value: round(stats.value.avgKw), unit: 'kW' })
  } else if (socOnly.value && socAvgKw.value != null) {
    // Ladeverlauf: die Spitze fehlt naturgemaess, der Schnitt ist die einzige
    // Leistungsaussage, die die Daten hergeben.
    out.push({ key: 'avg', label: t('dashboard.power_curve_avg'), value: round(socAvgKw.value), unit: 'kW' })
  }
  if (energyKwh.value != null) {
    out.push({ key: 'energy', label: t('dashboard.power_curve_energy'), value: kwhText(energyKwh.value), unit: 'kWh' })
  }
  return out
})

/**
 * Beispielkurve fuer den Teaser: eine echte, aufgezeichnete DC-Ladung
 * (8 -> 66 %, 253 kW Spitze, 16 Minuten), Zeitstempel auf den Session-Start
 * normiert.
 *
 * Vorher stand hier eine von Hand gezeichnete Kurve. Die war als Vorschau
 * unbrauchbar, weil sie den Verlauf nicht abbildete, den das Feature liefert -
 * keine Anfahrrampe, kein Taper in Stufen, ein linearer Abfall den es so nicht
 * gibt. Eine echte Kurve zeigt, was man bekommt.
 *
 * Grau und klar als fremde Ladung beschriftet: die eigenen Punkte liegen hinter
 * dem Server-Gate, und die Beispielkurve darf nicht wie die eigene wirken.
 */
const DEMO_SOC_BEFORE = 8
const DEMO_SOC_AFTER = 66
const DEMO_POINTS = [
  [0, 252.1], [10, 253.5], [40, 248.3], [85, 234.7], [110, 226.2], [145.5, 223.0],
  [185.5, 215.7], [215.5, 211.5], [245.5, 202.4], [280.5, 191.2], [315.5, 181.4],
  [350.5, 172.5], [385.5, 163.9], [420.5, 156.4], [455.5, 149.4], [491, 142.8],
  [526, 135.9], [566, 130.1], [591, 125.8], [636, 115.9], [661, 113.7],
  [696, 109.9], [731.5, 105.5], [766.5, 102.5], [801.5, 99.2], [836.5, 96.1],
  [868.5, 93.1], [908.5, 90.5], [938.5, 87.8], [974.5, 84.7],
].map(([sec, kw]) => ({ ts: sec * 1_000, kw }))

/**
 * Escape schliesst das Sheet. Der Listener haengt am document, damit er auch
 * greift wenn der Fokus noch nirgends im Dialog steht.
 *
 * Die Kurve nutzt Escape ebenfalls - zum Beenden eines aktiven Scrubbings. Sie
 * stoppt die Weitergabe des Events genau dann, sodass der erste Escape den
 * Zeiger loest und erst der zweite den Dialog schliesst.
 */
function onEscape(e: KeyboardEvent) {
  if (e.key === 'Escape') sheetRef.value?.requestClose()
}

onMounted(() => {
  document.addEventListener('keydown', onEscape)
  // Status still nachladen: war die Kurve schon geteilt, soll der Link sofort
  // stehen statt beim ersten Tap eine zweite Freigabe anzulegen.
  if (canShare.value && props.logId) loadShare(props.logId)
})
onBeforeUnmount(() => {
  document.removeEventListener('keydown', onEscape)
  if (hintTimer) clearTimeout(hintTimer)
})
</script>

<template>
  <BottomSheet
    ref="sheetRef"
    :label="socOnly ? t('soc_curve.title') : t('dashboard.power_curve_title')"
    panel-class="sm:max-w-2xl"
    testid="power-curve-modal"
    edge
    @close="emit('close')"
  >
    <template #default="{ close }">
      <div class="max-h-[90vh] overflow-y-auto px-4 pt-3 pb-5 sm:px-6 sm:pt-5">
        <div class="w-10 h-1 rounded-full bg-gray-300 dark:bg-gray-600 mx-auto mb-3 sm:hidden" />

        <!-- Kopf: Label mit Datum, Satz statt Kacheln, Reichweite als Ergebnis -->
        <p class="text-[12px] font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400 truncate">
          {{ socOnly ? t('soc_curve.title') : t('dashboard.power_curve_title') }}<template v-if="subtitle"> · {{ subtitle }}</template>
        </p>
        <h2 v-if="headline" class="mt-2 text-[28px] sm:text-[36px] font-bold leading-[1.1] text-gray-900 dark:text-gray-100 tabular-nums">
          {{ headline }}
        </h2>
        <p v-if="rangeKm" data-testid="power-curve-range" class="mt-1 text-lg font-semibold tabular-nums text-story-accent dark:text-story-accent-dark">
          {{ t('dashboard.power_curve_range_added', { km: rangeKm }) }}
        </p>

        <template v-if="locked">
          <p class="mt-3 text-[15px] leading-relaxed text-gray-700 dark:text-gray-300">
            {{ t('dashboard.power_curve_teaser_body') }}
          </p>
          <!-- Fremde Beispieldaten: grau, nicht abtastbar, fuer Screenreader unsichtbar -->
          <div class="mt-5 edge-box rounded-xl bg-white dark:bg-gray-700/50 p-3 sm:p-4">
            <p class="mb-3 text-[12px] font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400">
              {{ t('dashboard.power_curve_teaser_example') }}
            </p>
            <div class="pointer-events-none opacity-50" aria-hidden="true">
              <PowerCurveChart
                :points="DEMO_POINTS"
                :height="180"
                :height-desktop="230"
                x-axis-mode="duration"
                :soc-before-percent="DEMO_SOC_BEFORE"
                :soc-after-percent="DEMO_SOC_AFTER"
              />
            </div>
          </div>

          <RouterLink
            v-if="upsellTarget"
            :to="upsellTarget"
            class="btn-3d cta-shadow-sm mt-6 flex min-h-11 w-full items-center justify-center gap-2 rounded-full border-2 border-edge bg-green-600 px-4 text-sm font-semibold text-white focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-green-600"
            @click="close"
          >
            <LockOpenIcon class="w-4 h-4" aria-hidden="true" />
            {{ t('dashboard.power_curve_locked') }}
          </RouterLink>
        </template>

        <p v-else-if="loading" class="py-16 text-center text-sm text-gray-500 dark:text-gray-400">
          {{ t('live.loading_data') }}
        </p>
        <p v-else-if="points.length === 0 && !socOnly" class="py-16 text-center text-sm text-gray-500 dark:text-gray-400">
          {{ t('dashboard.no_power_curve') }}
        </p>
        <template v-else>
          <div class="mt-5 edge-box rounded-xl bg-white dark:bg-gray-700/50 p-3 sm:p-4">
            <SocCurveChart
              v-if="socOnly"
              :points="socPoints ?? []"
              :height="220"
              :height-desktop="300"
              :aria-label="t('soc_curve.title')"
              :readout="t('soc_curve.readout_end')"
            />
            <PowerCurveChart
              v-else
              :points="points"
              :height="220"
              :height-desktop="300"
              x-axis-mode="duration"
              :aria-label="t('dashboard.power_curve_title')"
              :consumption-kwh-per100km="consumptionKwhPer100km ?? null"
              :range-total-kwh="kwhCharged ?? null"
              :soc-before-percent="socBeforeChargePercent ?? null"
              :soc-after-percent="socAfterChargePercent ?? null"
              :soc-axis-label="t('dashboard.power_curve_soc_axis')"
              :readout="t('dashboard.power_curve_readout_peak')"
            />
          </div>
          <p v-if="socOnly" class="mt-2 text-[13px] text-gray-500 dark:text-gray-400">
            {{ t('soc_curve.explainer') }}
          </p>

          <div v-if="statsRow.length" class="mt-5 grid grid-cols-3 gap-3">
            <div v-for="stat in statsRow" :key="stat.key" class="min-w-0">
              <p class="text-[12px] font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400 truncate">{{ stat.label }}</p>
              <p class="mt-0.5 whitespace-nowrap tabular-nums text-gray-900 dark:text-gray-100">
                <span class="text-[26px] font-bold">{{ stat.value }}</span>
                <span class="ml-1 text-[13px] text-gray-500 dark:text-gray-400">{{ stat.unit }}</span>
              </p>
            </div>
          </div>

          <!-- Teilen: zwei Knoepfe nebeneinander, auch auf dem Handy (44 px hoch, kurze Labels) -->
          <div v-if="canShare" class="mt-6">
            <div class="grid grid-cols-2 gap-3">
              <button
                type="button"
                :disabled="shareBusy"
                class="btn-3d cta-shadow-sm inline-flex min-h-11 items-center justify-center gap-2 rounded-full border-2 border-edge bg-green-600 px-3 text-sm font-semibold text-white disabled:opacity-60 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-green-600"
                @click="onShare"
              >
                <ShareIcon class="w-4 h-4 shrink-0" aria-hidden="true" />
                {{ share ? t('share_curve.share_again') : t('share_curve.share') }}
              </button>
              <button
                type="button"
                :disabled="shareBusy"
                class="btn-3d cta-shadow-sm inline-flex min-h-11 items-center justify-center gap-2 rounded-full border-2 border-edge bg-white px-3 text-sm font-semibold text-gray-800 hover:bg-gray-50 disabled:opacity-60 dark:bg-white/10 dark:text-gray-200 dark:hover:bg-white/15 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-green-600"
                @click="onShareImage"
              >
                <PhotoIcon class="w-4 h-4 shrink-0" aria-hidden="true" />
                {{ t('share_curve.share_image') }}
              </button>
            </div>

            <p v-if="!share" class="mt-3 text-[13px] text-gray-500 dark:text-gray-400">
              {{ t('share_curve.privacy_hint') }}
            </p>
            <div v-else class="mt-3 flex items-center justify-between gap-3 text-[13px] text-gray-500 dark:text-gray-400">
              <span class="truncate" :title="share.url">{{ share.url }}</span>
              <button
                type="button"
                :disabled="shareBusy"
                class="shrink-0 min-h-11 underline underline-offset-2 hover:text-gray-900 dark:hover:text-gray-100 disabled:opacity-60"
                @click="onRevoke"
              >
                {{ t('share_curve.revoke') }}
              </button>
            </div>

            <p v-if="shareHint" class="mt-2 text-[13px] font-semibold text-gray-900 dark:text-gray-100" role="status">{{ shareHint }}</p>
            <p v-if="shareErrorText" class="mt-2 text-[13px] font-semibold text-gray-900 dark:text-gray-100" role="alert">{{ shareErrorText }}</p>
          </div>
        </template>

        <SheetCloseButton class="mt-6" @click="close" />
      </div>
    </template>
  </BottomSheet>
</template>
