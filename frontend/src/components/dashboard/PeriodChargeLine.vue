<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { BoltIcon, ChartBarSquareIcon, ChevronDownIcon, CreditCardIcon, LockClosedIcon, PencilSquareIcon } from '@heroicons/vue/24/outline'
import { normalizeCharge } from '../../utils/recentActivity'
import ComparisonChip from './ComparisonChip.vue'
import { FEED_GRID_COLS } from './feedGridCols'
import { useAuthStore } from '../../stores/auth'
import { useCommunityComparison } from '../../composables/useCommunityComparison'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import { formatSocRange } from '../../utils/socRange'
import FeedEventRow from './FeedEventRow.vue'
import Collapse from '../shared/Collapse.vue'
import { chargeGroupTimeRange, chargeTimeColumn, chargeTimeRange, formatPauseDuration } from '../../utils/tripTimeFormat'
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
  <!-- Desktop: einzeilige Rasterzeile im geteilten Spaltenraster, Spalte fuer Spalte wie die Fahrt.
       App-CI: neutral auf Weiss, der gruene Kreis traegt die Unterscheidung zur Fahrt. -->
  <div v-if="layout === 'row'" class="border-t border-gray-100 tabular-nums dark:border-gray-700/70">
    <div :class="[FEED_GRID_COLS, 'items-center px-4 py-2 text-[13px]']">
      <div class="flex items-center gap-1.5">
        <span class="grid h-7 w-7 shrink-0 place-items-center rounded-full border-2 border-edge bg-green-600 text-white dark:border-transparent dark:bg-green-500 dark:text-gray-950">
          <BoltIcon class="h-3.5 w-3.5" aria-hidden="true" />
        </span>
        <span v-if="isGroup" class="text-[12px] font-semibold text-gray-500 dark:text-gray-400">{{ count }}&times;</span>
      </div>
      <div class="truncate whitespace-nowrap font-semibold text-gray-900 dark:text-gray-100">{{ time }}</div>
      <div class="flex items-center gap-1.5 whitespace-nowrap">
        <span v-if="kwh != null" class="font-semibold text-gray-900 dark:text-gray-100">+{{ formatDecimal(Number(kwh)) }} kWh</span>
        <span v-else class="text-gray-300 dark:text-gray-600">-</span>
        <span v-if="entry.chargingType === 'AC' || entry.chargingType === 'DC'" class="text-[11.5px] font-semibold text-gray-500 dark:text-gray-400">{{ entry.chargingType }}</span>
      </div>
      <div class="whitespace-nowrap">
        <span v-if="costEur != null" class="font-semibold text-gray-900 dark:text-gray-100">{{ formatCurrency(costEur) }}</span>
        <span v-else class="text-gray-300 dark:text-gray-600">-</span>
      </div>
      <div class="whitespace-nowrap text-gray-600 dark:text-gray-300">
        <template v-if="socRange">{{ socRange }}</template>
        <span v-else class="text-gray-300 dark:text-gray-600">-</span>
      </div>
      <div class="whitespace-nowrap text-center text-gray-600 dark:text-gray-300">
        <template v-if="maxPower">max {{ formatDecimal(Number(maxPower), 0) }} kW</template>
        <span v-else class="text-gray-300 dark:text-gray-600">-</span>
      </div>
      <div class="whitespace-nowrap text-gray-600 dark:text-gray-300">
        <template v-if="duration">{{ duration }}</template>
        <span v-else class="text-gray-300 dark:text-gray-600">-</span>
      </div>
      <div class="whitespace-nowrap text-gray-600 dark:text-gray-300">
        <template v-if="entry.temperatureCelsius != null">{{ formatDecimal(Number(entry.temperatureCelsius), 0) }} °C</template>
        <span v-else class="text-gray-300 dark:text-gray-600">-</span>
      </div>
      <div class="flex justify-end whitespace-nowrap">
        <ComparisonChip v-if="costPerKwh != null" :level="costLevel" :tooltip="costTooltip" :delta-percent="costDelta">
          {{ formatCostPerKwh(costPerKwh) }}
        </ComparisonChip>
        <span v-else class="text-gray-300 dark:text-gray-600">-</span>
      </div>
      <div class="flex items-center justify-end">
        <button v-if="isGroup" type="button" @click.stop="expanded = !expanded"
          class="grid h-8 w-8 place-items-center rounded-full text-gray-500 transition hover:bg-gray-100 focus:outline-none focus-visible:ring-2 focus-visible:ring-story-accent dark:text-gray-400 dark:hover:bg-white/10"
          :aria-expanded="expanded" :aria-label="t('logs.period.charge_toggle_topups')">
          <ChevronDownIcon class="h-4 w-4 transition-transform duration-300" :class="expanded ? 'rotate-180 ease-out' : 'ease-in'" aria-hidden="true" />
        </button>
        <!-- Einzelladung: direkt editierbar. Gruppe: nur je Teilladung im Aufklapp. -->
        <button v-else type="button" @click.stop="emit('edit', entry)"
          class="grid h-8 w-8 place-items-center rounded-full text-gray-500 transition hover:bg-gray-100 hover:text-gray-900 focus:outline-none focus-visible:ring-2 focus-visible:ring-story-accent dark:text-gray-400 dark:hover:bg-white/10"
          :aria-label="t('dashboard.action_edit')">
          <PencilSquareIcon class="h-4 w-4" aria-hidden="true" />
        </button>
      </div>
    </div>

    <!-- Teilladungen der Gruppe, aufgeklappt: eine Unterzeile je Ladung mit Uhrzeit und kWh. -->
    <Collapse v-if="isGroup" :open="expanded">
      <div v-for="topUp in topUps" :key="topUp.id + '__pc'"
        :class="[FEED_GRID_COLS, 'items-center border-t border-gray-100 px-4 py-1 text-[13px] dark:border-gray-700/70']">
        <div class="pl-9 text-[12px] text-gray-400 dark:text-gray-500" aria-hidden="true">&#9492;</div>
        <div class="truncate whitespace-nowrap text-gray-600 dark:text-gray-300">{{ topUpTime(topUp) }}</div>
        <div class="whitespace-nowrap font-semibold text-gray-800 dark:text-gray-100">
          +{{ formatDecimal(Number(topUp.kwhAtVehicle ?? topUp.kwhCharged ?? 0)) }} kWh
        </div>
        <div class="whitespace-nowrap">
          <span v-if="topUp.costEur != null" class="text-gray-700 dark:text-gray-300">{{ formatCurrency(topUp.costEur) }}</span>
          <span v-else class="text-gray-300 dark:text-gray-600">-</span>
        </div>
        <div class="whitespace-nowrap text-gray-600 dark:text-gray-300">
          {{ formatSocRange(topUp.socBeforeChargePercent, topUp.socAfterChargePercent) || '-' }}
        </div>
        <div class="whitespace-nowrap text-center text-gray-600 dark:text-gray-300">
          <template v-if="topUp.maxChargingPowerKw">max {{ formatDecimal(Number(topUp.maxChargingPowerKw), 0) }} kW</template>
          <span v-else class="text-gray-300 dark:text-gray-600">-</span>
        </div>
        <div class="text-gray-300 dark:text-gray-600">-</div>
        <div></div>
        <div></div>
        <div class="flex justify-end">
          <button type="button" data-testid="period-topup-edit" @click.stop="emit('edit', topUp)"
            class="grid h-8 w-8 place-items-center rounded-full text-gray-500 transition hover:bg-gray-100 hover:text-gray-900 focus:outline-none focus-visible:ring-2 focus-visible:ring-story-accent dark:text-gray-400 dark:hover:bg-white/10"
            :aria-label="t('dashboard.action_edit')">
            <PencilSquareIcon class="h-4 w-4" aria-hidden="true" />
          </button>
        </div>
      </div>
    </Collapse>
    <!-- Ort, Ladekarte und Ladekurve unter der Zeile, ab der Zeitspalte - wie die Klimazeile der Fahrten. -->
    <div v-if="entry.cpoName || cardName || hasCurve"
      class="flex items-center gap-1.5 pb-2 pl-[72px] pr-4 text-[12px] text-gray-500 dark:text-gray-400">
      <span v-if="entry.cpoName" class="truncate">{{ entry.cpoName }}</span>
      <span v-if="entry.cpoName && cardName" aria-hidden="true">&middot;</span>
      <span v-if="cardName" class="inline-flex items-center gap-1 whitespace-nowrap">
        <CreditCardIcon class="h-3.5 w-3.5 flex-shrink-0" aria-hidden="true" />{{ cardName }}
      </span>
      <span v-if="(entry.cpoName || cardName) && hasCurve" aria-hidden="true">&middot;</span>
      <button v-if="hasCurve" type="button" @click.stop="emit('power-curve', entry)"
        :aria-label="canOpenCurve ? t('dashboard.show_power_curve') : t('dashboard.power_curve_locked')"
        :title="canOpenCurve ? undefined : t('dashboard.power_curve_locked')" aria-haspopup="dialog"
        class="inline-flex items-center gap-1 whitespace-nowrap rounded font-semibold text-gray-700 hover:text-gray-900 focus:outline-none focus-visible:ring-2 focus-visible:ring-story-accent dark:text-gray-200 dark:hover:text-gray-50">
        <ChartBarSquareIcon class="h-3.5 w-3.5 flex-shrink-0" aria-hidden="true" />{{ t('dashboard.power_curve_label') }}
        <LockClosedIcon v-if="!canOpenCurve" class="h-3 w-3 flex-shrink-0" aria-hidden="true" />
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
      <ul class="pb-2 pl-[108px] pr-3 sm:pl-[120px] sm:pr-4">
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
