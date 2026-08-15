<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'
import { XMarkIcon, BoltIcon, LockOpenIcon, ShareIcon, PhotoIcon, LinkSlashIcon } from '@heroicons/vue/24/outline'
import BottomSheet from '../shared/BottomSheet.vue'
import PowerCurveChart from './PowerCurveChart.vue'
import { computeCurveStats } from './powerCurveStats'
import { buildSocSeries } from './powerCurveSeries'
import { formatDuration } from './powerCurveScrub'
import { formatSocRange } from '../../utils/socRange'
import { useCurveShare } from '../../composables/useCurveShare'

/**
 * Ladekurve im Overlay statt inline im Log-Feed.
 *
 * Inline stand der Kurve nur die Resthoehe einer Feed-Zeile zur Verfuegung, was
 * sie gestaucht wirken liess. Im Sheet bekommt sie die volle Breite und - auch
 * auf Mobile - eine Hoehe, in der die Ladephasen ablesbar sind.
 *
 * Die Kennzahlen im Kopf kommen aus den Kurvenpunkten (siehe computeCurveStats),
 * nur SoC und die abgerechnete kWh-Menge stammen aus dem Log.
 */
const props = defineProps<{
  loading: boolean
  points: { ts: number; kw: number; soc?: number | null }[]
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
const socRange = computed(() => formatSocRange(props.socBeforeChargePercent, props.socAfterChargePercent))

// Nur relevant fuer den Hinweis unter der Kurve: bei Bestandskurven ohne
// gemessenen SoC ist die zweite Achse rekonstruiert, und das muss dranstehen.
const socSeries = computed(() =>
  buildSocSeries(props.points, props.socBeforeChargePercent, props.socAfterChargePercent))

function kw(value: number): string {
  return `${value.toLocaleString(undefined, { maximumFractionDigits: value < 10 ? 1 : 0 })} kW`
}

interface Tile { key: string; label: string; value: string }

const tiles = computed<Tile[]>(() => {
  const s = stats.value
  if (!s && !props.locked) return []
  const out: Tile[] = []
  // Spitze und Schnitt stecken nur in der Kurve - im Teaser sind sie genau das,
  // was noch fehlt, und bleiben deshalb weg statt als Platzhalter zu erscheinen.
  if (s) {
    out.push({ key: 'peak', label: t('dashboard.power_curve_peak'), value: kw(s.peakKw) })
    out.push({ key: 'avg', label: t('dashboard.power_curve_avg'), value: kw(s.avgKw) })
  }
  if (s && s.durationMs > 0) {
    out.push({ key: 'duration', label: t('dashboard.power_curve_duration'), value: formatDuration(s.durationMs) })
  } else if (props.locked && props.chargeDurationMinutes) {
    out.push({
      key: 'duration',
      label: t('dashboard.power_curve_duration'),
      value: formatDuration(props.chargeDurationMinutes * 60_000),
    })
  }
  // Bewusst der Log-Wert und nicht das Kurven-Integral: die Kurve kann Luecken
  // haben, und der Feed-Eintrag dahinter zeigt genau diese Zahl.
  if (props.kwhCharged != null) {
    out.push({
      key: 'energy',
      label: t('dashboard.power_curve_energy'),
      value: `${Number(props.kwhCharged).toLocaleString(undefined, { maximumFractionDigits: 1 })} kWh`,
    })
  }
  if (socRange.value) {
    out.push({ key: 'soc', label: t('dashboard.power_curve_soc'), value: socRange.value })
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
 * Bewusst nicht verwischt und klar als fremde Ladung beschriftet: die eigenen
 * Punkte liegen hinter dem Server-Gate, und eine unscharfe Fremdkurve wuerde
 * so wirken, als sei es die eigene.
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

/** Zeiger sitzt im Teaser dort, wo die Kurve abknickt - genau die Stelle, um
 *  die es beim Abtasten geht. */
const DEMO_SCRUB_INDEX = 11

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
    :label="t('dashboard.power_curve_title')"
    panel-class="sm:max-w-3xl"
    testid="power-curve-modal"
    @close="emit('close')"
  >
    <template #default="{ close }">
      <div class="flex flex-col max-h-[90vh]">
        <!-- Kopf: bleibt stehen, damit die Kurve beim Scrollen nicht den Kontext verliert -->
        <div class="flex items-start gap-3 px-4 pt-4 pb-3 border-b border-gray-200 dark:border-gray-700">
          <BoltIcon class="w-5 h-5 text-emerald-600 dark:text-emerald-400 flex-shrink-0 mt-0.5" />
          <div class="min-w-0 flex-1">
            <h2 class="text-base font-semibold text-gray-900 dark:text-gray-100 leading-tight">
              {{ t('dashboard.power_curve_title') }}
            </h2>
            <p v-if="subtitle" class="text-xs text-gray-500 dark:text-gray-400 truncate mt-0.5">{{ subtitle }}</p>
          </div>
          <button
            type="button"
            :aria-label="t('common.close')"
            class="p-1.5 -mr-1 -mt-1 rounded text-gray-500 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-700 transition focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-400 flex-shrink-0"
            @click="close"
          >
            <XMarkIcon class="w-5 h-5" />
          </button>
        </div>

        <div class="flex-1 overflow-y-auto px-4 py-4 space-y-4">
          <template v-if="locked">
            <div v-if="tiles.length" class="grid grid-cols-3 gap-2">
              <div
                v-for="tile in tiles"
                :key="tile.key"
                class="rounded-sm border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-900/40 px-2 py-1.5"
              >
                <div class="text-[10px] uppercase tracking-wide text-gray-500 dark:text-gray-400 truncate">{{ tile.label }}</div>
                <div class="text-sm font-semibold text-gray-900 dark:text-gray-100 tabular-nums whitespace-nowrap">{{ tile.value }}</div>
              </div>
            </div>

            <p class="text-sm text-gray-700 dark:text-gray-200 leading-relaxed">
              {{ t('dashboard.power_curve_teaser_body') }}
            </p>

            <div class="relative rounded-sm border border-gray-200 dark:border-gray-700 p-2 pt-6">
              <span class="absolute top-1.5 left-2 rounded-sm bg-amber-100 dark:bg-amber-900/40 text-amber-800 dark:text-amber-300 px-1.5 py-0.5 text-[10px] uppercase tracking-wide font-semibold">
                {{ t('dashboard.power_curve_teaser_example') }}
              </span>
              <!-- Fremde Beispieldaten: nicht abtastbar, fuer Screenreader unsichtbar -->
              <div class="pointer-events-none opacity-70" aria-hidden="true">
                <PowerCurveChart
                  :points="DEMO_POINTS"
                  :height="180"
                  :height-desktop="230"
                  x-axis-mode="duration"
                  :consumption-kwh-per100km="18"
                  :soc-before-percent="DEMO_SOC_BEFORE"
                  :soc-after-percent="DEMO_SOC_AFTER"
                  :preview-scrub-index="DEMO_SCRUB_INDEX"
                />
              </div>
              <p class="mt-2 text-[11px] text-gray-500 dark:text-gray-400">
                {{ t('dashboard.power_curve_teaser_example_caption') }}
              </p>
            </div>

            <RouterLink
              v-if="upsellTarget"
              :to="upsellTarget"
              class="flex items-center justify-center gap-2 w-full rounded-sm bg-amber-500 hover:bg-amber-600 px-4 py-2.5 text-sm font-semibold text-white transition focus:outline-none focus-visible:ring-2 focus-visible:ring-amber-400"
              @click="close"
            >
              <LockOpenIcon class="w-4 h-4" />
              {{ t('dashboard.power_curve_locked') }}
            </RouterLink>
          </template>

          <div v-else-if="loading" class="text-sm text-gray-500 dark:text-gray-400 text-center py-16">
            {{ t('live.loading_data') }}
          </div>
          <div v-else-if="points.length === 0" class="text-sm text-gray-500 dark:text-gray-400 text-center py-16">
            {{ t('dashboard.no_power_curve') }}
          </div>
          <template v-else>
            <!-- Kennzahlen zuerst: auf Mobile beantwortet die Zeile die Frage
                 "wie schnell war die Ladung" schon ohne Interaktion mit der Kurve. -->
            <div v-if="tiles.length" class="grid grid-cols-3 sm:grid-cols-5 gap-2">
              <div
                v-for="tile in tiles"
                :key="tile.key"
                class="rounded-sm border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-900/40 px-2 py-1.5"
              >
                <div class="text-[10px] uppercase tracking-wide text-gray-500 dark:text-gray-400 truncate">{{ tile.label }}</div>
                <div class="text-sm font-semibold text-gray-900 dark:text-gray-100 tabular-nums whitespace-nowrap">{{ tile.value }}</div>
              </div>
            </div>

            <PowerCurveChart
              :points="points"
              :height="260"
              :height-desktop="340"
              x-axis-mode="duration"
              :aria-label="t('dashboard.power_curve_title')"
              :consumption-kwh-per100km="consumptionKwhPer100km ?? null"
              :soc-before-percent="socBeforeChargePercent ?? null"
              :soc-after-percent="socAfterChargePercent ?? null"
              :soc-axis-label="t('dashboard.power_curve_soc_axis')"
            />

            <div class="flex flex-wrap items-center gap-x-4 gap-y-1.5 text-[11px] text-gray-500 dark:text-gray-400">
              <span class="inline-flex items-center gap-1.5">
                <span class="w-3 h-0.5 rounded-full bg-emerald-500 flex-shrink-0" />
                {{ t('dashboard.power_curve_legend_kw') }}
              </span>
              <span v-if="consumptionKwhPer100km" class="inline-flex items-center gap-1.5">
                <span class="w-3 h-0.5 rounded-full bg-sky-500 flex-shrink-0" />
                {{ t('dashboard.power_curve_legend_km') }}
              </span>
              <span v-if="socSeries" class="inline-flex items-center gap-1.5">
                <span class="w-3 border-b border-dashed border-gray-400 dark:border-gray-500 flex-shrink-0" />
                {{ t('dashboard.power_curve_legend_soc') }}
              </span>
            </div>
            <p class="text-[11px] text-gray-400 dark:text-gray-500">{{ t('dashboard.power_curve_scrub_hint') }}</p>
            <p v-if="socSeries?.derived" class="text-[11px] text-gray-400 dark:text-gray-500">
              {{ t('dashboard.power_curve_soc_derived_hint') }}
            </p>

            <!-- Teilen. Mobile-first: zwei volle Buttons untereinander, ab sm nebeneinander. -->
            <div v-if="canShare" class="pt-3 border-t border-gray-200 dark:border-gray-700 space-y-2">
              <div class="flex flex-col sm:flex-row gap-2">
                <button
                  type="button"
                  :disabled="shareBusy"
                  class="flex-1 inline-flex items-center justify-center gap-2 rounded-sm bg-emerald-600 hover:bg-emerald-700 disabled:opacity-60 px-4 py-2.5 text-sm font-semibold text-white transition focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-400"
                  @click="onShare"
                >
                  <ShareIcon class="w-4 h-4" aria-hidden="true" />
                  {{ share ? t('share_curve.share_again') : t('share_curve.share') }}
                </button>
                <button
                  type="button"
                  :disabled="shareBusy"
                  class="flex-1 inline-flex items-center justify-center gap-2 rounded-sm border border-gray-300 dark:border-gray-600 hover:bg-gray-50 dark:hover:bg-gray-800 disabled:opacity-60 px-4 py-2.5 text-sm font-semibold text-gray-700 dark:text-gray-200 transition focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-400"
                  @click="onShareImage"
                >
                  <PhotoIcon class="w-4 h-4" aria-hidden="true" />
                  {{ t('share_curve.share_image') }}
                </button>
              </div>

              <p v-if="!share" class="text-[11px] text-gray-400 dark:text-gray-500">
                {{ t('share_curve.privacy_hint') }}
              </p>

              <div v-else class="flex items-center justify-between gap-3">
                <span class="text-[11px] text-gray-500 dark:text-gray-400 truncate" :title="share.url">{{ share.url }}</span>
                <button
                  type="button"
                  :disabled="shareBusy"
                  class="inline-flex items-center gap-1.5 flex-shrink-0 text-[11px] text-gray-500 dark:text-gray-400 hover:text-red-600 dark:hover:text-red-400 disabled:opacity-60 transition focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-400 rounded"
                  @click="onRevoke"
                >
                  <LinkSlashIcon class="w-3.5 h-3.5" aria-hidden="true" />
                  {{ t('share_curve.revoke') }}
                </button>
              </div>

              <p v-if="shareHint" class="text-[11px] text-emerald-600 dark:text-emerald-400" role="status">{{ shareHint }}</p>
              <p v-if="shareErrorText" class="text-[11px] text-red-600 dark:text-red-400" role="alert">{{ shareErrorText }}</p>
            </div>
          </template>
        </div>
      </div>
    </template>
  </BottomSheet>
</template>
