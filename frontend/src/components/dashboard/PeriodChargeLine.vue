<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { BoltIcon, ChartBarSquareIcon, ChevronDownIcon, ChevronUpIcon, ClockIcon, CreditCardIcon, LockClosedIcon, PencilSquareIcon, SunIcon } from '@heroicons/vue/24/outline'
import { normalizeCharge } from '../../utils/recentActivity'
import ChargeTypeBadge from './ChargeTypeBadge.vue'
import ComparisonChip from './ComparisonChip.vue'
import { FEED_GRID_COLS } from './feedGridCols'
import { useAuthStore } from '../../stores/auth'
import { useCommunityComparison } from '../../composables/useCommunityComparison'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import { formatSocRange } from '../../utils/socRange'
import FeedEventRow from './FeedEventRow.vue'
import Collapse from '../shared/Collapse.vue'
import { chargeGroupTimeRange, chargeTimeColumn, chargeTimeRange, formatPauseDuration } from '../../utils/tripTimeFormat'
import { tempBadgeClass } from '../../utils/temperatureColor'
import { purchasesAvailable } from '../../utils/iapPolicy'

/**
 * Eine Ladung im Zeitraum-Feed, anatomisch identisch zur Fahrtzeile - in beiden Layouts:
 * `card` (Mobile) spiegelt die Fahrtkarte mit Kopfzeile und gelabeltem Metrics-Grid,
 * `row` (Desktop) die einzeilige Fahrt-Row im geteilten Spaltenraster {@link FEED_GRID_COLS}.
 * So lesen sich Fahrten und Ladungen im Tag fast wie eine Tabelle.
 *
 * Die Ladekurve oeffnet wie in der Ladung-Ansicht als Modal ('power-curve'-Event, das der
 * Aufrufer an sein PowerCurveModal bindet); nur das Zusammenfuehren bleibt der Ladung-Ansicht.
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
const emit = defineEmits<{ (e: 'edit', entry: any): void; (e: 'power-curve', entry: any): void }>()

const { t, locale } = useI18n()
const { formatCurrency, formatCostPerKwh, formatDecimal } = useLocaleFormat()
const authStore = useAuthStore()

// Dieselbe Kurven-Sichtbarkeit wie in der Ladung-Ansicht: freigeschaltet nach Analytics-
// bzw. SoC-Berechtigung, sonst als gesperrtes Symbol, wo Kaeufe moeglich sind.
const canOpenCurve = computed(() =>
  (props.entry.hasPowerCurve && authStore.canViewLiveAnalytics)
    || (props.entry.hasSocCurve && authStore.canViewSocCurve))
const curveLocked = computed(() =>
  !canOpenCurve.value
    && (props.entry.hasPowerCurve || props.entry.hasSocCurve)
    && purchasesAvailable())
// Desktop: die Ladekurve (freigeschaltet oder als Teaser) lebt ausformuliert in der
// Kontextzeile neben Ort und Ladekarte - nicht mehr als nacktes Icon in der Aktionsspalte.
const hasCurve = computed(() => canOpenCurve.value || curveLocked.value)

/**
 * Anzeigewerte - bei einer Ladegruppe die Aggregate (Summe, hoechster SoC, Gruppen-Kosten),
 * sonst die Eintragswerte. Dieselbe Abflachung wie Header und Kachel nutzen, damit die Zeile
 * zeigt, was der Header zaehlt - und nicht nur den aeltesten Teilvorgang.
 */
const charge = computed(() => normalizeCharge(props.entry))
const kwh = computed<number | null>(() => charge.value?.kwh ?? null)
// Eine nur teilweise bepreiste Ladegruppe bekommt KEINEN Gruppenpreis: Betrag und ct/kWh
// staemmen dann aus einem Bruchteil der Energie. Die Teilvorgaenge unten zeigen weiterhin
// ihren eigenen Preis.
const costEur = computed<number | null>(() => (charge.value?.isFullyPriced ? charge.value.costEur : null))
const costPerKwh = computed<number | null>(() => (charge.value?.isFullyPriced ? charge.value.costPerKwh : null))
const maxPower = computed<number | null>(() => charge.value?.maxPowerKw ?? null)
const isGroup = computed(() => charge.value?.isGroup ?? false)
const count = computed(() => props.entry?._topUps?.length ?? 0)
const topUps = computed(() => props.entry?._topUps ?? [])

/**
 * Von-bis statt nur Beginn: loggedAt ist bei Ladungen der Ladebeginn, das Ende folgt aus der
 * Dauer. Eine Ladegruppe spannt von der ersten bis zur letzten Teilladung - nicht nur die
 * erste, sonst widerspricht die Zeit dem Gruppen-kWh daneben. Ueber Nacht traegt das Datumsband.
 */
const time = computed(() => {
  if (isGroup.value && topUps.value.length) {
    // Ueber Nacht traegt das Datumsband der Gruppe, sonst die reine Zeitspanne.
    if (props.entry._spansMultipleDays) return props.entry._dateRangeLabel ?? ''
    return chargeGroupTimeRange(topUps.value, locale.value)
  }
  return chargeTimeRange(props.entry.loggedAt, props.entry.chargeDurationMinutes, locale.value)
})

const socRange = computed(() => formatSocRange(charge.value?.socBefore, charge.value?.socAfter))

/** Zeitspalte der Handy-Zeile: Beginn oben, Ende darunter; eine Gruppe von der ersten bis zur letzten Teilladung. */
const timeCol = computed(() => {
  if (isGroup.value && topUps.value.length) {
    const oldest = topUps.value[topUps.value.length - 1]
    const newest = topUps.value[0]
    const startMs = oldest?.loggedAt ? new Date(oldest.loggedAt).getTime() : NaN
    const endMs = newest?.loggedAt ? new Date(newest.loggedAt).getTime() + (newest.chargeDurationMinutes ?? 0) * 60_000 : NaN
    if (!Number.isNaN(startMs) && !Number.isNaN(endMs)) return chargeTimeColumn(oldest.loggedAt, (endMs - startMs) / 60_000, locale.value)
  }
  return chargeTimeColumn(props.entry.loggedAt, props.entry.chargeDurationMinutes, locale.value)
})

/** Teilladungen einer Gruppe aufklappen - lokaler Zustand, jede Zeile fuer sich. */
const expanded = ref(false)

/** Nur die Uhrzeit einer Teilladung - das Datum traegt bereits der Tag. */
const topUpTime = (topUp: any): string =>
  topUp?.loggedAt
    ? new Date(topUp.loggedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    : ''

// Bei einer Gruppe hat "die" Ladedauer keine Bedeutung - die Einzeldauern stehen im Aufklapp.
const duration = computed(() => isGroup.value ? '' : formatPauseDuration(props.entry.chargeDurationMinutes))

const { comparisonLevel, comparisonDeltaPercent, comparisonTooltip } = useCommunityComparison()

// Verglichen wird gegen den eigenen Schnitt, nicht die Community: der eigene Schnitt
// enthaelt das eigene Ladeprofil und macht eine Supercharger-Woche nicht pauschal rot.
const costLevel = computed(() => comparisonLevel(costPerKwh.value, props.ownAvgCostPerKwh))
const costDelta = computed(() => comparisonDeltaPercent(costPerKwh.value, props.ownAvgCostPerKwh))
const costTooltip = computed(() =>
  props.ownAvgCostPerKwh == null
    ? null
    : comparisonTooltip(costPerKwh.value, props.ownAvgCostPerKwh, formatCostPerKwh(props.ownAvgCostPerKwh), 'self'))

// Der Ladeort ist dreiwertig (V166): ohne Angabe wird er neutral benannt, statt
// die Ladung faelschlich als Heimladung auszuweisen.
const place = computed(() => {
  if (props.entry.cpoName) return props.entry.cpoName
  if (props.entry.isPublicCharging === true) return t('logs.period.charge_public')
  if (props.entry.isPublicCharging === false) return t('logs.period.charge_home')
  return t('logs.period.charge_unknown')
})
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
        <span v-if="isGroup" class="text-[10px] px-1.5 py-0.5 rounded bg-indigo-100 dark:bg-indigo-900/40 text-indigo-700 dark:text-indigo-300 font-medium whitespace-nowrap">{{ count }}×</span>
      </div>
      <div class="text-[13px] text-gray-900 dark:text-gray-100 whitespace-nowrap truncate">{{ time }}</div>
      <div class="flex items-center gap-1.5 whitespace-nowrap">
        <span v-if="kwh != null" class="text-sm font-medium text-indigo-700 dark:text-indigo-300">+{{ formatDecimal(Number(kwh)) }} kWh</span>
        <span v-else class="text-gray-400 dark:text-gray-600 text-sm">-</span>
        <ChargeTypeBadge :type="entry.chargingType" />
      </div>
      <div class="text-xs whitespace-nowrap">
        <span v-if="costEur != null" class="text-slate-700 dark:text-gray-200">{{ formatCurrency(costEur) }}</span>
        <span v-else class="text-gray-400 dark:text-gray-600">-</span>
      </div>
      <div class="text-xs text-slate-700 dark:text-gray-200 whitespace-nowrap">
        <template v-if="socRange">{{ socRange }}</template>
        <span v-else class="text-gray-400 dark:text-gray-600">-</span>
      </div>
      <div class="text-xs text-slate-700 dark:text-gray-200 whitespace-nowrap text-center">
        <template v-if="maxPower">max {{ maxPower }} kW</template>
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
      <div class="flex justify-end items-center gap-0.5">
        <button v-if="isGroup" type="button" @click.stop="expanded = !expanded"
          class="p-1 rounded text-indigo-400 dark:text-indigo-500 hover:text-indigo-600 dark:hover:text-indigo-300 hover:bg-indigo-100 dark:hover:bg-indigo-900/40 transition focus:outline-none focus-visible:ring-2 focus-visible:ring-indigo-400"
          :aria-expanded="expanded" :aria-label="t('logs.period.charge_toggle_topups')">
          <ChevronUpIcon v-if="expanded" class="w-4 h-4" aria-hidden="true" />
          <ChevronDownIcon v-else class="w-4 h-4" aria-hidden="true" />
        </button>
        <!-- Einzelladung: direkt editierbar. Gruppe: nur je Teilladung im Aufklapp, denn eine
             Gruppe ist kein einzelner Log. -->
        <button v-if="!isGroup" type="button" @click.stop="emit('edit', entry)"
          class="p-1 rounded text-gray-400 hover:text-gray-600 dark:hover:text-gray-200 hover:bg-gray-100 dark:hover:bg-gray-600 transition focus:outline-none focus-visible:ring-2 focus-visible:ring-indigo-400"
          :aria-label="t('dashboard.action_edit')">
          <PencilSquareIcon class="w-4 h-4" aria-hidden="true" />
        </button>
      </div>
    </div>

    <!-- Teilladungen der Gruppe, aufgeklappt: eine Unterzeile je Ladung mit Uhrzeit und kWh. -->
    <div v-if="isGroup && expanded" class="bg-indigo-50/40 dark:bg-indigo-950/20">
      <div v-for="topUp in topUps" :key="topUp.id + '__pc'"
        :class="[FEED_GRID_COLS, 'items-center px-3 py-1 border-t border-indigo-200/40 dark:border-indigo-800/30']">
        <div class="flex items-center gap-1.5 pl-4 text-gray-400 dark:text-gray-500 text-xs">└</div>
        <div class="text-[13px] text-gray-600 dark:text-gray-300 whitespace-nowrap truncate flex items-center gap-1">
          <ClockIcon class="w-3 h-3 flex-shrink-0" aria-hidden="true" />{{ topUpTime(topUp) }}
        </div>
        <div class="text-sm font-medium text-indigo-700 dark:text-indigo-300 whitespace-nowrap">
          +{{ formatDecimal(Number(topUp.kwhAtVehicle ?? topUp.kwhCharged ?? 0)) }} kWh
        </div>
        <div class="text-xs whitespace-nowrap">
          <span v-if="topUp.costEur != null" class="text-slate-600 dark:text-gray-300">{{ formatCurrency(topUp.costEur) }}</span>
          <span v-else class="text-gray-400 dark:text-gray-600">-</span>
        </div>
        <div class="text-xs text-slate-600 dark:text-gray-300 whitespace-nowrap">
          {{ formatSocRange(topUp.socBeforeChargePercent, topUp.socAfterChargePercent) || '-' }}
        </div>
        <div class="text-xs text-slate-600 dark:text-gray-300 whitespace-nowrap text-center">
          <template v-if="topUp.maxChargingPowerKw">max {{ topUp.maxChargingPowerKw }} kW</template>
          <span v-else class="text-gray-400 dark:text-gray-600">-</span>
        </div>
        <div class="text-gray-400 dark:text-gray-600 text-xs">-</div>
        <div></div>
        <div></div>
        <div class="flex justify-end">
          <button type="button" data-testid="period-topup-edit" @click.stop="emit('edit', topUp)"
            class="p-1 rounded text-gray-400 hover:text-gray-600 dark:hover:text-gray-200 hover:bg-gray-100 dark:hover:bg-gray-600 transition focus:outline-none focus-visible:ring-2 focus-visible:ring-indigo-400"
            :aria-label="t('dashboard.action_edit')">
            <PencilSquareIcon class="w-4 h-4" aria-hidden="true" />
          </button>
        </div>
      </div>
    </div>
    <!-- Ort, Ladekarte und Ladekurve mittig unter der Zeile - das Pendant zur Klimazeile der Fahrten. -->
    <div v-if="entry.cpoName || cardName || hasCurve"
      class="px-3 pb-1.5 flex items-center justify-center gap-1.5 text-xs text-gray-500 dark:text-gray-400">
      <span v-if="entry.cpoName" class="truncate">{{ entry.cpoName }}</span>
      <span v-if="entry.cpoName && cardName" aria-hidden="true">&middot;</span>
      <span v-if="cardName" class="inline-flex items-center gap-1 whitespace-nowrap">
        <CreditCardIcon class="w-3.5 h-3.5 flex-shrink-0" aria-hidden="true" />{{ cardName }}
      </span>
      <span v-if="(entry.cpoName || cardName) && hasCurve" aria-hidden="true">&middot;</span>
      <!-- Freigeschaltet: oeffnet die Kurve; gesperrt: derselbe Button als Teaser mit Schloss. -->
      <button v-if="canOpenCurve" type="button" @click.stop="emit('power-curve', entry)"
        :aria-label="t('dashboard.show_power_curve')" aria-haspopup="dialog"
        class="inline-flex items-center gap-1 whitespace-nowrap rounded text-indigo-600 dark:text-indigo-400 hover:text-indigo-700 dark:hover:text-indigo-300 focus:outline-none focus-visible:ring-2 focus-visible:ring-indigo-400">
        <ChartBarSquareIcon class="w-3.5 h-3.5 flex-shrink-0" aria-hidden="true" />{{ t('dashboard.power_curve_label') }}
      </button>
      <button v-else-if="curveLocked" type="button" @click.stop="emit('power-curve', entry)"
        :aria-label="t('dashboard.power_curve_locked')" :title="t('dashboard.power_curve_locked')" aria-haspopup="dialog"
        class="inline-flex items-center gap-1 whitespace-nowrap rounded text-amber-600 dark:text-amber-400 hover:text-amber-700 dark:hover:text-amber-300 focus:outline-none focus-visible:ring-2 focus-visible:ring-amber-400">
        <ChartBarSquareIcon class="w-3.5 h-3.5 flex-shrink-0" aria-hidden="true" />{{ t('dashboard.power_curve_label') }}<LockClosedIcon class="w-3 h-3 flex-shrink-0" aria-hidden="true" />
      </button>
    </div>
  </div>

  <!-- Mobile: Zeile wie im Story-Tagesblatt. Einzelladung: Tippen oeffnet den Editor;
       Ladegruppe: Tippen klappt die Teilladungen auf. -->
  <div v-else class="border-t border-gray-100 dark:border-gray-700/70">
    <FeedEventRow kind="charge" :from="timeCol.from" :to="timeCol.to" interactive
      :expanded="isGroup ? expanded : undefined"
      @activate="isGroup ? (expanded = !expanded) : emit('edit', entry)">
      <template #title>
        <span v-if="isGroup" class="text-[13px] font-semibold text-gray-500 dark:text-gray-400">{{ count }}&times;</span>
        <span class="min-w-0 line-clamp-2 break-words">{{ place }}</span>
      </template>
      <span class="block">
        <template v-if="kwh != null"><b class="font-semibold text-gray-700 dark:text-gray-200">+{{ formatDecimal(Number(kwh)) }} kWh</b></template>
        <template v-if="entry.chargingType === 'AC' || entry.chargingType === 'DC'"> &middot; {{ entry.chargingType }}</template>
        <template v-if="socRange"> &middot; {{ socRange }}</template>
        <template v-if="maxPower"> &middot; max {{ formatDecimal(Number(maxPower), 0) }} kW</template>
        <template v-if="duration"> &middot; {{ duration }}</template>
      </span>
      <span v-if="cardName" class="mt-0.5 flex items-center gap-1 truncate">
        <CreditCardIcon class="h-3.5 w-3.5 shrink-0" aria-hidden="true" />{{ cardName }}
      </span>
      <button v-if="hasCurve" type="button" @click.stop="emit('power-curve', entry)" aria-haspopup="dialog"
        :aria-label="canOpenCurve ? t('dashboard.show_power_curve') : t('dashboard.power_curve_locked')"
        class="mt-0.5 inline-flex min-h-8 items-center gap-1 font-semibold text-gray-700 hover:text-gray-900 dark:text-gray-200 dark:hover:text-gray-50">
        <ChartBarSquareIcon class="h-4 w-4" aria-hidden="true" />{{ t('dashboard.power_curve_label') }}
        <LockClosedIcon v-if="!canOpenCurve" class="h-3.5 w-3.5" aria-hidden="true" />
      </button>
      <template v-if="costEur != null" #right>
        <b class="block text-[15px] font-bold text-gray-900 dark:text-gray-50">{{ formatCurrency(costEur) }}</b>
        <span v-if="costPerKwh != null" class="block text-[12px] font-semibold"
          :class="costLevel === 'worse' || costLevel === 'much_worse' ? 'text-amber-700 dark:text-amber-400' : 'text-gray-500 dark:text-gray-400'"
          :title="costTooltip ?? undefined">{{ formatCostPerKwh(costPerKwh) }}</span>
      </template>
    </FeedEventRow>

    <!-- Teilladungen der Gruppe: Uhrzeit, kWh, Kosten und Akku je Ladung, jede editierbar. -->
    <Collapse v-if="isGroup" :open="expanded">
      <ul class="pb-2 pl-[104px] pr-4">
        <li v-for="topUp in topUps" :key="topUp.id + '__pcm'"
          class="flex items-center justify-between gap-2 border-t border-gray-100 py-1.5 text-[13px] tabular-nums dark:border-gray-700/70">
          <span class="min-w-0 truncate text-gray-600 dark:text-gray-300">
            {{ topUpTime(topUp) }} &middot; <b class="font-semibold text-gray-800 dark:text-gray-100">+{{ formatDecimal(Number(topUp.kwhAtVehicle ?? topUp.kwhCharged ?? 0)) }} kWh</b>
            <template v-if="formatSocRange(topUp.socBeforeChargePercent, topUp.socAfterChargePercent)"> &middot; {{ formatSocRange(topUp.socBeforeChargePercent, topUp.socAfterChargePercent) }}</template>
          </span>
          <span class="inline-flex shrink-0 items-center gap-1 text-gray-700 dark:text-gray-200">
            <template v-if="topUp.costEur != null">{{ formatCurrency(topUp.costEur) }}</template>
            <button type="button" data-testid="period-topup-edit" @click.stop="emit('edit', topUp)"
              class="-mr-2 grid h-10 w-10 place-items-center rounded-full text-gray-500 transition-colors hover:bg-gray-100 focus:outline-none focus-visible:ring-2 focus-visible:ring-story-accent dark:text-gray-400 dark:hover:bg-white/10"
              :aria-label="t('dashboard.action_edit')">
              <PencilSquareIcon class="h-4 w-4" aria-hidden="true" />
            </button>
          </span>
        </li>
      </ul>
    </Collapse>
  </div>
</template>
