<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { BoltIcon, CreditCardIcon, PencilSquareIcon, SunIcon } from '@heroicons/vue/24/outline'
import ChargeTypeBadge from './ChargeTypeBadge.vue'
import ComparisonChip from './ComparisonChip.vue'
import MetricCell from './MetricCell.vue'
import { FEED_GRID_COLS } from './feedGridCols'
import { useCommunityComparison } from '../../composables/useCommunityComparison'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import { formatSocRange } from '../../utils/socRange'
import { formatPauseDuration } from '../../utils/tripTimeFormat'
import { tempBadgeClass } from '../../utils/temperatureColor'

/**
 * Eine Ladung im Zeitraum-Feed, anatomisch identisch zur Fahrtzeile - in beiden Layouts:
 * `card` (Mobile) spiegelt die Fahrtkarte mit Kopfzeile und gelabeltem Metrics-Grid,
 * `row` (Desktop) die einzeilige Fahrt-Row im geteilten Spaltenraster {@link FEED_GRID_COLS}.
 * So lesen sich Fahrten und Ladungen im Tag fast wie eine Tabelle.
 *
 * In der Ladung-Ansicht ist die Ladung die Grenze und traegt eine ganze Karte; zum Aufklappen
 * von Ladekurve und Zusammenfuehren fuehrt weiterhin die Ladung-Ansicht.
 */
const props = withDefaults(
  defineProps<{
    entry: any
    layout?: 'card' | 'row'
    /** Eigener ct/kWh-Schnitt des Nutzers, fuer Faerbung und Tooltip des ct/kWh-Chips. */
    ownAvgCostPerKwh?: number | null
    /** Name der verwendeten Ladekarte, vom Aufrufer aus der chargingProviderId aufgeloest. */
    cardName?: string | null
  }>(),
  { layout: 'card' },
)
const emit = defineEmits<{ (e: 'edit', entry: any): void }>()

const { t, locale } = useI18n()
const { formatCurrency, formatCostPerKwh } = useLocaleFormat()

const LOCALE_MAP: Record<string, string> = { en: 'en-GB', nb: 'nb-NO', sv: 'sv-SE' }

const time = computed(() =>
  props.entry.loggedAt
    ? new Date(props.entry.loggedAt).toLocaleTimeString(LOCALE_MAP[locale.value] ?? 'de-DE', {
        hour: '2-digit',
        minute: '2-digit',
      })
    : '',
)

/** Am Fahrzeug angekommene Energie, sonst die abgerechnete - in dieser Reihenfolge. */
const kwh = computed<number | null>(() => props.entry.kwhAtVehicle ?? props.entry.kwhCharged ?? null)

const costPerKwh = computed<number | null>(() => {
  const charged = props.entry.kwhCharged ?? props.entry.kwhAtVehicle
  if (!charged || props.entry.costEur == null) return null
  return props.entry.costEur / charged
})

const socRange = computed(() =>
  formatSocRange(props.entry.socBeforeChargePercent, props.entry.socAfterChargePercent),
)

const duration = computed(() => formatPauseDuration(props.entry.chargeDurationMinutes))

const { comparisonLevel, comparisonDeltaPercent, comparisonTooltip } = useCommunityComparison()

// Verglichen wird gegen den eigenen Schnitt, nicht die Community: der eigene Schnitt
// enthaelt das eigene Ladeprofil und macht eine Supercharger-Woche nicht pauschal rot.
const costLevel = computed(() => comparisonLevel(costPerKwh.value, props.ownAvgCostPerKwh))
const costDelta = computed(() => comparisonDeltaPercent(costPerKwh.value, props.ownAvgCostPerKwh))
const costTooltip = computed(() =>
  props.ownAvgCostPerKwh == null
    ? null
    : comparisonTooltip(costPerKwh.value, props.ownAvgCostPerKwh, formatCostPerKwh(props.ownAvgCostPerKwh), 'self'))

const place = computed(() =>
  props.entry.cpoName
    || t(props.entry.isPublicCharging ? 'logs.period.charge_public' : 'logs.period.charge_home'),
)
</script>

<template>
  <!-- Desktop: einzeilige Row im geteilten Spaltenraster, Spalte fuer Spalte wie die Fahrt.
       Indigo ist die Ladungs-Identitaet des Feeds (+kWh, DC) - der Akzentbalken traegt die
       Unterscheidung, damit die Flaeche auch im Dark Mode dezent bleiben darf. -->
  <!-- -ml-1 zieht die Zeile ueber den 4px-Emerald-Rand des Gruppencontainers, sodass der
       indigo Balken ihn unterbricht statt daneben zu stehen. -->
  <div v-if="layout === 'row'"
    class="tabular-nums -ml-1 bg-indigo-100/80 dark:bg-indigo-900/40 border-t border-indigo-300/70 dark:border-indigo-700/50
           border-l-4 border-l-indigo-500 dark:border-l-indigo-400
           hover:bg-indigo-200/70 dark:hover:bg-indigo-900/60 transition">
    <div :class="[FEED_GRID_COLS, 'items-center px-3 py-1.5']">
      <div class="flex items-center gap-1.5 pl-4">
        <BoltIcon class="w-4 h-4 text-indigo-500 dark:text-indigo-400 flex-shrink-0" aria-hidden="true" />
      </div>
      <div class="text-[13px] text-gray-900 dark:text-gray-100 whitespace-nowrap truncate">{{ time }}</div>
      <div class="flex items-center gap-1.5 whitespace-nowrap">
        <span v-if="kwh != null" class="text-sm font-medium text-indigo-700 dark:text-indigo-300">+{{ Number(kwh).toFixed(1) }} kWh</span>
        <span v-else class="text-gray-400 dark:text-gray-600 text-sm">-</span>
        <ChargeTypeBadge :type="entry.chargingType" />
      </div>
      <div class="text-xs whitespace-nowrap">
        <span v-if="entry.costEur != null" class="text-slate-700 dark:text-gray-200">{{ formatCurrency(entry.costEur) }}</span>
        <span v-else class="text-gray-400 dark:text-gray-600">-</span>
      </div>
      <div class="text-xs text-slate-700 dark:text-gray-200 whitespace-nowrap">
        <template v-if="socRange">{{ socRange }}</template>
        <span v-else class="text-gray-400 dark:text-gray-600">-</span>
      </div>
      <div class="text-xs text-slate-700 dark:text-gray-200 whitespace-nowrap text-center">
        <template v-if="entry.maxChargingPowerKw">max {{ entry.maxChargingPowerKw }} kW</template>
        <span v-else class="text-gray-400 dark:text-gray-600">-</span>
      </div>
      <div class="text-xs text-slate-700 dark:text-gray-200 whitespace-nowrap">
        <template v-if="duration">{{ duration }}</template>
        <span v-else class="text-gray-400 dark:text-gray-600">-</span>
      </div>
      <div>
        <span v-if="entry.temperatureCelsius != null"
          :class="['inline-flex items-center gap-0.5 px-2 py-0.5 border rounded text-xs whitespace-nowrap', tempBadgeClass(entry.temperatureCelsius)]">
          <SunIcon class="w-3 h-3" />{{ entry.temperatureCelsius }}°C
        </span>
        <span v-else class="text-gray-400 dark:text-gray-600 text-xs">-</span>
      </div>
      <div class="flex justify-end whitespace-nowrap">
        <ComparisonChip v-if="costPerKwh != null" :level="costLevel" :tooltip="costTooltip" :delta-percent="costDelta">
          {{ formatCostPerKwh(costPerKwh) }}
        </ComparisonChip>
        <span v-else class="text-gray-400 dark:text-gray-600 text-xs">-</span>
      </div>
      <div class="flex justify-end">
        <button type="button" @click.stop="emit('edit', entry)"
          class="p-1 rounded text-gray-400 hover:text-gray-600 dark:hover:text-gray-200 hover:bg-gray-100 dark:hover:bg-gray-600 transition focus:outline-none focus-visible:ring-2 focus-visible:ring-indigo-400"
          :aria-label="t('dashboard.action_edit')">
          <PencilSquareIcon class="w-4 h-4" aria-hidden="true" />
        </button>
      </div>
    </div>
    <!-- Ort und Ladekarte mittig unter der Zeile - das Pendant zur Klimazeile der Fahrten. -->
    <div v-if="entry.cpoName || cardName"
      class="px-3 pb-1.5 flex items-center justify-center gap-1.5 text-xs text-gray-500 dark:text-gray-400">
      <span v-if="entry.cpoName" class="truncate">{{ entry.cpoName }}</span>
      <span v-if="entry.cpoName && cardName" aria-hidden="true">&middot;</span>
      <span v-if="cardName" class="inline-flex items-center gap-1 whitespace-nowrap">
        <CreditCardIcon class="w-3.5 h-3.5 flex-shrink-0" aria-hidden="true" />{{ cardName }}
      </span>
    </div>
  </div>

  <!-- Mobile: Karte mit Kopfzeile und gelabeltem Metrics-Grid, wie die Fahrtkarte. -->
  <div v-else class="px-3 py-3 space-y-2 tabular-nums -ml-1
              bg-indigo-100/80 dark:bg-indigo-900/40 border-t border-indigo-300/70 dark:border-indigo-700/50
              border-l-4 border-l-indigo-500 dark:border-l-indigo-400">
    <!-- Kopfzeile: gleiche Struktur wie die Fahrtzeile - wann fuehrt, dann der Hauptwert. -->
    <div class="flex items-center justify-between gap-2">
      <span class="inline-flex items-center gap-2 min-w-0 flex-wrap">
        <span class="inline-flex items-center gap-1.5 min-w-0">
          <BoltIcon class="w-4 h-4 flex-shrink-0 self-center text-indigo-500 dark:text-indigo-400" aria-hidden="true" />
          <span class="text-[15px] text-gray-900 dark:text-gray-100 whitespace-nowrap">{{ time }}</span>
        </span>
        <span v-if="kwh != null" class="font-semibold text-indigo-700 dark:text-indigo-300 whitespace-nowrap">
          +{{ Number(kwh).toFixed(1) }} kWh
        </span>
        <ChargeTypeBadge :type="entry.chargingType" />
      </span>
      <div class="flex items-center gap-1.5 flex-shrink-0">
        <button type="button" @click.stop="emit('edit', entry)"
          class="p-1 rounded text-gray-400 hover:text-gray-600 dark:hover:text-gray-200 hover:bg-gray-100 dark:hover:bg-gray-600 transition focus:outline-none focus-visible:ring-2 focus-visible:ring-indigo-400"
          :aria-label="t('dashboard.action_edit')">
          <PencilSquareIcon class="w-5 h-5" />
        </button>
      </div>
    </div>
    <!-- Metrics: gleiches Grid wie die Fahrtzeile, damit die Werte spaltenweise fluchten. -->
    <div class="grid grid-cols-2 gap-x-4 gap-y-1 text-[13px]">
      <MetricCell v-if="entry.costEur != null" emphasized>
        {{ formatCurrency(entry.costEur) }}<template v-if="costPerKwh != null"> · {{ formatCostPerKwh(costPerKwh) }}</template>
      </MetricCell>
      <MetricCell v-if="entry.maxChargingPowerKw">
        max {{ entry.maxChargingPowerKw }} kW
      </MetricCell>
      <MetricCell v-if="socRange">{{ socRange }}</MetricCell>
      <MetricCell v-if="duration">{{ duration }}</MetricCell>
      <MetricCell class="min-w-0">
        <span class="truncate">{{ place }}</span>
      </MetricCell>
      <MetricCell v-if="cardName" class="min-w-0">
        <span class="truncate">{{ cardName }}</span>
      </MetricCell>
    </div>
  </div>
</template>
