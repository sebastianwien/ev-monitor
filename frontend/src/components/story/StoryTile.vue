<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import Spark from './Spark.vue'
import FlowBar from './FlowBar.vue'
import UseBar from './UseBar.vue'
import ShareBar from './ShareBar.vue'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import type { StoryTile } from '../../composables/storyModel'
import type { EnergyUse } from '../../composables/storyEnergy'

/**
 * Eine Fahrerfrage als Kachel in fester Höhe, Inhalt oben: Label in Versalien, eine Zahl mit
 * Einheit, ein Satz, unten eine Grafik mit Zahl und Bezug. Wenige Ladungen nennt die
 * Qualitätszeile, nicht die Kachel.
 */
/** selected: Detail steht ab lg rechts in der Spalte (aria-current, grüner Rahmen). */
const props = defineProps<{ tile: StoryTile; selected?: boolean }>()
const emit = defineEmits<{ open: [id: StoryTile['id']] }>()

const { t } = useI18n()
const { formatCurrency, formatConsumption, formatDistance, formatDecimal, consumptionUnitLabel, distanceUnitLabel, currencySymbol } = useLocaleFormat()

const label = computed(() => t(`story.q_${props.tile.id}`))

const value = computed(() => {
  const x = props.tile
  switch (x.id) {
    case 'cost': return euroParts(x.value)
    case 'flow': {
      if (!x.use) return { n: formatDecimal(x.value, 0), unit: t('story.flow_unit') }
      if (x.use.lossEur == null) return { n: formatDecimal(x.value, 0), unit: t('story.use_loss_kwh_unit') }
      // Gerechnet aus Ladepreisen und Ladeverlust: mit Tilde, wie alle gerechneten Beträge
      const e = euroParts(x.value)
      return { n: `~${e.n}`, unit: `${e.unit} ${t('story.use_loss_unit')}`.trim() }
    }
    case 'range': return { n: formatDecimal(x.value, 0), unit: distanceUnitLabel() }
    case 'wltp': return { n: formatConsumption(x.value, { showUnit: false }), unit: consumptionUnitLabel() }
    case 'trips': return { n: formatConsumption(x.value, { showUnit: false }), unit: consumptionUnitLabel() }
    case 'peer': return { n: formatConsumption(x.value, { showUnit: false }), unit: consumptionUnitLabel() }
    case 'battery': return { n: `${x.estimated ? '~' : ''}${formatDecimal(x.value, 0)}`, unit: t('story.battery_unit') }
    case 'co2': return { n: formatDecimal(Math.abs(co2Percent.value), 0), unit: t(co2Percent.value >= 0 ? 'story.co2_unit' : 'story.co2_unit_more') }
    // Ein Monat nur mit AC hiesse sonst "0 % schnell": dann der Anteil normal
    case 'charging': return x.charging && x.charging.dcKwh === 0
      ? { n: '100', unit: t('story.charging_unit_normal') }
      : { n: formatDecimal(x.value, 0), unit: t('story.charging_unit') }
  }
  return { n: '', unit: '' }
})

/** Symbol als kleine Einheit hinter der Zahl, ausser die Waehrung schreibt es davor (GBP). */
function euroParts(v: number) {
  const f = formatCurrency(v, { decimals: 0 })
  return f.startsWith(currencySymbol.value) ? { n: f, unit: '' } : { n: f.replace(currencySymbol.value, '').trim(), unit: currencySymbol.value }
}

/** Satz zur Strom-Kachel: Stand und Laden in kWh, Laden mit Tilde, sobald hochgerechnet. */
function useNote(u: EnergyUse) {
  const charge = `${u.estimatedCharges > 0 ? '~' : ''}${formatDecimal(u.chargeLossKwh, 0)}`
  const standby = u.standbyKwh == null ? null : formatDecimal(u.standbyKwh, 0)
  if (standby != null && u.chargeLossKwh > 0) return t('story.use_note', { standby, charge })
  if (standby != null) return t('story.use_note_standby', { standby })
  return u.chargeLossKwh > 0 ? t('story.use_note_charge', { charge }) : ''
}

const useOrigin = computed(() => {
  const h = props.tile.use?.homePct
  if (h == null) return ''
  if (h === 100) return t('story.use_origin_home')
  if (h === 0) return t('story.use_origin_public')
  return t('story.use_origin_mixed', { home: h, pub: 100 - h })
})

const note = computed(() => {
  const x = props.tile
  switch (x.id) {
    case 'cost':
      // Ohne Preise sind die Kosten zu niedrig: Zahl der Ladungen statt eines schiefen Vergleichs.
      if (x.pricelessCharges) return t('story.cost_priceless', { n: x.pricelessCharges })
      // Laufender Monat mit weniger Strom als der Vormonat: "weniger Euro" hiesse nur "weniger Tage".
      if (x.projection) {
        return t('story.cost_projection', {
          eur: formatCurrency(x.projection.eur, { decimals: 0 }),
          previous: formatCurrency(x.projection.previousEur, { decimals: 0 }),
        })
      }
      if (x.delta == null) return x.previousIncomplete ? '' : t('story.cost_no_prev')
      if (x.sentiment === 'neutral') return t('story.cost_same')
      return t('story.cost_delta', { delta: (x.delta > 0 ? '+' : '') + formatCurrency(x.delta, { decimals: 0 }) })
    case 'flow':
      if (x.use) return useNote(x.use)
      return x.flow?.partial
        ? t('story.flow_partial', { covered: x.flow.coveredLogCount, total: x.flow.totalLogCount })
        : t('story.flow_loss', { kwh: formatDecimal(x.flow?.lossKwh ?? 0, 0) })
    case 'trips': {
      const m = x.tripMonth
      if (!m) return ''
      const base = t('story.trips_note', { n: m.trips, km: formatDistance(m.avgKm, { round: true }) }, m.trips)
      return m.mainRoute ? `${base}, ${t('story.trips_main', { route: routeLabel(m.mainRoute) })}` : base
    }
    case 'range':
      return `${t('story.range_basis_recent')}, ${t('story.range_window', { max: rangeWindow.value.max, min: rangeWindow.value.min })}`
    case 'wltp': {
      const pct = formatDecimal(Math.abs(x.deltaPercent ?? 0), 0)
      const wltp = formatConsumption(x.reference ?? 0, { showUnit: false })
      return (x.deltaPercent ?? 0) >= 0 ? t('story.wltp_delta_over', { pct, wltp }) : t('story.wltp_delta_under', { pct, wltp })
    }
    case 'peer': {
      // Wenige Ladungen: kein Prozentsatz gegen andere, nur ihr Schnitt.
      if (x.thinCharges != null) return t('story.peer_avg', { avg: formatConsumption(x.spark[0] ?? 0, { showUnit: false }), count: x.peerCount })
      const pct = formatDecimal(Math.abs(x.deltaPercent ?? 0), 0)
      if (x.sentiment === 'neutral') return t('story.peer_same', { count: x.peerCount })
      return (x.deltaPercent ?? 0) < 0 ? t('story.peer_better', { pct, count: x.peerCount }) : t('story.peer_worse', { pct, count: x.peerCount })
    }
    case 'battery': {
      const state = t(`story.battery_${x.sentiment === 'good' ? 'good' : x.sentiment === 'warn' ? 'warn' : 'bad'}`)
      return x.odometerKm ? `${state} ${t('story.battery_km', { km: formatDistance(x.odometerKm) })}` : state
    }
    case 'co2': {
      const kg = formatDecimal(Math.abs(x.value), 0)
      if (x.value < 0) return t('story.co2_note_more', { kg })
      const life = x.co2?.lifetime
      return life
        ? t('story.co2_note_since', { kg, year: new Date(life.since).getFullYear(), total: formatDecimal(life.kg, 0) })
        : t('story.co2_note', { kg })
    }
    case 'charging': {
      const c = x.charging
      if (c && c.dcKwh === 0) return c.acEurPerKwh != null ? t('story.charging_only_ac', { eur: formatCurrency(c.acEurPerKwh) }) : t('story.charging_only_ac_plain')
      if (c && c.acKwh === 0) return c.dcEurPerKwh != null ? t('story.charging_only_dc', { eur: formatCurrency(c.dcEurPerKwh) }) : t('story.charging_only_dc_plain')
      if (c?.acEurPerKwh != null && c.dcEurPerKwh != null) return t('story.charging_prices', { ac: formatCurrency(c.acEurPerKwh), dc: formatCurrency(c.dcEurPerKwh) })
      return t('story.charging_kwh', { kwh: formatDecimal(c?.dcKwh ?? 0, 0) })
    }
  }
  return ''
})

// Kachel CO₂: dein Auto in Prozent eines Benziners; Benziner ist 100 % und die volle Breite
const co2Share = computed(() => {
  const c = props.tile.co2
  return c && c.petrolGPerKm > 0 ? Math.round((c.gridGPerKm / c.petrolGPerKm) * 100) : 100
})
const co2Percent = computed(() => 100 - co2Share.value)
const co2Rows = computed(() => [
  { key: 'petrol', label: t('story.co2_petrol'), pct: 100, cls: 'bg-gray-400 dark:bg-gray-500' },
  { key: 'grid', label: t('story.co2_yours'), pct: co2Share.value, cls: 'bg-green-700 dark:bg-green-400' },
])
// Kachel Laden: Normal grün, Schnell blau wie öffentliches Laden; ohne Art bekannte kWh zählen nicht mit
const chargingSegments = computed(() => {
  const c = props.tile.charging
  if (!c) return []
  const total = c.acKwh + c.dcKwh
  const dc = Math.round((c.dcKwh / total) * 100)
  return [
    { key: 'AC', label: t('story.charging_ac'), pct: 100 - dc, cls: 'bg-green-700 dark:bg-green-400', text: 'text-green-700 dark:text-green-400' },
    { key: 'DC', label: t('story.charging_dc'), pct: dc, cls: 'bg-sky-500', text: 'text-sky-700 dark:text-sky-400' },
  ].filter(s => s.pct > 0)
})

function routeLabel(key: string) { return t(`model.route_type_${key.toLowerCase()}`) }
// Kachel: Fahrtlängen, die gibt es immer und sie erklären den Verbrauch (kurze Fahrten kosten mehr).
const LENGTH_CLS = { SHORT: 'bg-gray-700 dark:bg-gray-300', MEDIUM: 'bg-green-700 dark:bg-green-400', LONG: 'bg-sky-500' } as const
/** Beschriftung in der Farbe ihres Abschnitts; Blau dunkler als der Balken, damit kleiner Text auf Weiß lesbar bleibt */
const LENGTH_TEXT = { SHORT: 'text-gray-700 dark:text-gray-300', MEDIUM: 'text-green-700 dark:text-green-400', LONG: 'text-sky-700 dark:text-sky-400' } as const
const lengthSegments = computed(() => (props.tile.tripMonth?.lengthShares ?? []).map(r => ({ key: r.key, label: t(`story.trips_len_${r.key.toLowerCase()}`), pct: r.pct, cls: LENGTH_CLS[r.key], text: LENGTH_TEXT[r.key] })))

/** Position des eigenen Werts auf der Skala sparsam ... hoch: Schnitt in der Mitte, ±100 % an den Rändern. */
const peerPos = computed(() => Math.min(1, Math.max(0, 0.5 + (props.tile.deltaPercent ?? 0) / 200)))
const rangeWindow = computed(() => {
  const w = props.tile.range?.windows.find(x => x.recommended) ?? props.tile.range?.windows[0]
  return w ? { min: w.socMin, max: w.socMax } : { min: 10, max: 90 }
})
const endLabel = computed(() => props.tile.id === 'cost' ? formatCurrency(props.tile.value, { decimals: 0 }) : formatConsumption(props.tile.value, { showUnit: false }))
</script>

<template>
  <button
    type="button"
    :data-testid="`story-tile-${tile.id}`"
    class="story-tile flex h-[150px] w-[85%] min-w-0 shrink-0 snap-start flex-col gap-1 rounded-2xl bg-white p-3.5 text-left edge-card md:h-[160px] md:w-auto
           dark:bg-gray-700/50 md:hover:bg-gray-50 md:dark:hover:bg-gray-700/70 transition-[transform,box-shadow,background-color] active:translate-x-0.5 active:translate-y-0.5 active:shadow-none focus:outline-none focus-visible:ring-2 focus-visible:ring-story-accent"
    :class="selected && 'outline-2 outline-offset-2 outline-green-600 dark:outline-green-400'"
    :aria-current="selected || undefined"
    :style="{ viewTransitionName: `story-tile-${tile.id}` }"
    @click="emit('open', tile.id)">
    <span class="line-clamp-2 text-[11.5px] font-semibold uppercase leading-tight tracking-[0.06em] text-gray-500 dark:text-gray-400">{{ label }}</span>
    <span class="flex items-baseline gap-1 leading-none">
      <b class="text-[26px] font-extrabold tracking-tight text-gray-900 tabular-nums dark:text-gray-50">{{ value.n }}</b>
      <span class="text-[13px] font-semibold text-gray-500 dark:text-gray-400">{{ value.unit }}</span>
      <!-- Schätzwert: in der Zeile der Zahl, in der Schrift der Einheit -->
      <span v-if="tile.id === 'battery' && tile.estimated" class="ml-1 text-[13px] font-semibold text-amber-700 dark:text-amber-400">{{ t('story.estimated_badge') }}</span>
    </span>
    <span v-if="note" class="truncate text-[13.5px] text-gray-600 dark:text-gray-400">{{ note }}</span>
    <span data-role="graphic" class="mt-auto block">
      <span v-if="tile.id === 'flow' && tile.use" class="grid gap-1">
        <UseBar :use="tile.use" />
        <span v-if="useOrigin" class="truncate text-[11px] text-gray-500 dark:text-gray-400">{{ useOrigin }}</span>
      </span>
      <FlowBar v-else-if="tile.id === 'flow' && tile.flow" :flow="tile.flow" />
      <ShareBar v-else-if="tile.id === 'trips'" :segments="lengthSegments" />
      <span v-else-if="tile.id === 'range'" class="grid gap-1">
        <span class="relative block h-2.5 rounded-[3px] bg-gray-200 dark:bg-gray-700">
          <i class="absolute inset-y-0 rounded-[3px] bg-green-700 dark:bg-green-400" :style="{ left: `${rangeWindow.min}%`, width: `${rangeWindow.max - rangeWindow.min}%` }" />
        </span>
        <span class="flex justify-between text-[11px] text-gray-500 tabular-nums dark:text-gray-400"><span>{{ rangeWindow.min }} %</span><span>{{ rangeWindow.max }} %</span></span>
      </span>
      <span v-else-if="tile.id === 'peer'" class="grid gap-1">
        <span class="relative block h-2.5 rounded-[3px] bg-gray-200 dark:bg-gray-700">
          <i class="absolute inset-y-0 left-1/2 w-0.5 bg-gray-500" />
          <i class="absolute -top-0.5 h-3.5 w-3.5 rounded-full border-2 border-gray-900 bg-green-500 dark:border-gray-100" :style="{ left: `calc(${peerPos * 100}% - 7px)` }" />
        </span>
        <span class="flex justify-between text-[11px] text-gray-500 dark:text-gray-400"><span>{{ t('story.peer_scale_low') }}</span><span>{{ t('story.peer_scale_avg') }}</span><span>{{ t('story.peer_scale_high') }}</span></span>
      </span>
      <span v-else-if="tile.id === 'battery'" class="grid gap-1">
        <span class="relative block h-2.5 rounded-[3px] bg-gray-200 dark:bg-gray-700">
          <i class="absolute inset-y-0 left-0 rounded-[3px] bg-green-700 dark:bg-green-400" :style="{ width: `${Math.min(100, Math.max(0, tile.value))}%` }" />
        </span>
        <span class="flex justify-between text-[11px] text-gray-500 tabular-nums dark:text-gray-400"><span>0 %</span><span>{{ t('story.battery_scale_new') }}</span></span>
      </span>
      <span v-else-if="tile.id === 'co2'" class="grid gap-1" data-testid="story-co2-bars">
        <span v-for="r in co2Rows" :key="r.key" class="grid grid-cols-[5.5rem_1fr_2.75rem] items-center gap-2 text-[11px] tabular-nums text-gray-500 dark:text-gray-400">
          <span class="truncate">{{ r.label }}</span>
          <span class="block h-2 rounded-[3px] bg-gray-100 dark:bg-gray-800"><i class="block h-full rounded-[3px]" :class="r.cls" :style="{ width: `${Math.min(100, r.pct)}%` }" /></span>
          <span class="text-right">{{ r.pct }} %</span>
        </span>
      </span>
      <ShareBar v-else-if="tile.id === 'charging'" :segments="chargingSegments" />
      <Spark v-else :values="tile.spark" :variant="tile.sparkVariant" :reference="tile.reference" tone="good" :end-label="endLabel" />
    </span>
  </button>
</template>
