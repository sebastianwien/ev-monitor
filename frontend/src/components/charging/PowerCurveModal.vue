<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'
import { XMarkIcon, BoltIcon, LockOpenIcon } from '@heroicons/vue/24/outline'
import BottomSheet from '../shared/BottomSheet.vue'
import PowerCurveChart from './PowerCurveChart.vue'
import { computeCurveStats } from './powerCurveStats'
import { formatDuration } from './powerCurveScrub'
import { formatSocRange } from '../../utils/socRange'

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
  points: { ts: number; kw: number }[]
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
}>()

const emit = defineEmits<{ close: [] }>()

const { t } = useI18n()

const sheetRef = ref<InstanceType<typeof BottomSheet> | null>(null)

const stats = computed(() => computeCurveStats(props.points))
const socRange = computed(() => formatSocRange(props.socBeforeChargePercent, props.socAfterChargePercent))

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
 * Beispielkurve fuer den Teaser - eine typische DC-Ladung mit Taper.
 *
 * Bewusst erkennbar als Beispiel beschriftet und nicht verwischt: die echten
 * Punkte liegen hinter dem Server-Gate, und eine unscharfe Fremdkurve wuerde
 * so wirken, als sei es die eigene.
 */
const DEMO_POINTS = [
  [0, 45], [0.5, 180], [1, 250], [2, 247], [4, 225], [6, 198], [8, 172],
  [10, 150], [13, 128], [16, 108], [19, 92], [22, 78], [25, 66], [28, 55],
].map(([min, kw]) => ({ ts: min * 60_000, kw }))

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

onMounted(() => document.addEventListener('keydown', onEscape))
onBeforeUnmount(() => document.removeEventListener('keydown', onEscape))
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
                />
              </div>
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
            </div>
            <p class="text-[11px] text-gray-400 dark:text-gray-500">{{ t('dashboard.power_curve_scrub_hint') }}</p>
          </template>
        </div>
      </div>
    </template>
  </BottomSheet>
</template>
