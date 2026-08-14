<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { XMarkIcon, BoltIcon } from '@heroicons/vue/24/outline'
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
  if (!s) return []
  const out: Tile[] = [
    { key: 'peak', label: t('dashboard.power_curve_peak'), value: kw(s.peakKw) },
    { key: 'avg', label: t('dashboard.power_curve_avg'), value: kw(s.avgKw) },
  ]
  if (s.durationMs > 0) {
    out.push({ key: 'duration', label: t('dashboard.power_curve_duration'), value: formatDuration(s.durationMs) })
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
          <div v-if="loading" class="text-sm text-gray-500 dark:text-gray-400 text-center py-16">
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
