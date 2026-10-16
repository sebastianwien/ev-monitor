<template>
  <li class="border-b border-gray-200 dark:border-gray-800">
    <div class="mr-grid group relative pr-4 transition-colors hover:bg-white/70 lg:pr-5 lg:hover:bg-gray-50 dark:hover:bg-gray-900/60 lg:dark:hover:bg-gray-800/60">
      <!-- Whole row toggles the details; content sits below it, only the compare box is clickable on top. -->
      <button
        type="button"
        class="absolute inset-0 h-full w-full cursor-pointer focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-green-600"
        :aria-expanded="expanded"
        :aria-controls="detailId"
        @click="emit('toggle')"
      >
        <span class="sr-only">{{ t(expanded ? 'models_ranking.row.details_close' : 'models_ranking.row.details_open', { name: m.modelDisplayName }) }}</span>
      </button>

      <!-- Photo flush with the row's left, top and bottom edge; the rank sits on it -->
      <span class="mr-th pointer-events-none relative self-stretch overflow-hidden bg-gray-200 dark:bg-gray-800" :class="inCompare ? 'ring-2 ring-inset ring-green-600 dark:ring-green-400' : ''">
        <img v-if="imageUrl" :src="imageUrl" alt="" loading="lazy" class="absolute inset-0 h-full w-full object-cover transition-transform duration-300 ease-out motion-safe:group-hover:scale-105" />
        <span v-else class="absolute inset-0 grid place-items-center text-base font-bold text-gray-500 dark:text-gray-400">{{ m.brandDisplayName.slice(0, 2) }}</span>
        <span class="absolute left-1.5 top-1.5 rounded-md bg-gray-900/75 px-1.5 py-0.5 text-[11px] font-semibold tabular-nums text-white">{{ item.rank }}</span>
        <!-- Phone: compare sits on the photo, above the row toggle; the desktop has it at the end of the verdict line -->
        <button
          type="button"
          class="pointer-events-auto absolute inset-x-0 bottom-0 flex h-9 items-center justify-center gap-1 text-[12px] font-semibold text-white disabled:cursor-not-allowed disabled:opacity-60 lg:hidden"
          :class="inCompare ? 'bg-green-700/90' : 'bg-gray-900/70'"
          :aria-pressed="inCompare"
          :aria-label="t(inCompare ? 'models_ranking.row.compare_remove' : 'models_ranking.row.compare_add', { name: m.modelDisplayName })"
          :disabled="!inCompare && !canAddCompare"
          data-testid="compare-phone"
          @click.stop="emit('compare')"
        >
          <CheckIcon v-if="inCompare" class="h-3.5 w-3.5" stroke-width="2.5" aria-hidden="true" />
          <PlusIcon v-else class="h-3.5 w-3.5" stroke-width="2.5" aria-hidden="true" />
          {{ t(inCompare ? 'models_ranking.row.compare_short_remove' : 'models_ranking.row.compare_short_add') }}
        </button>
      </span>

      <span class="mr-who pointer-events-none grid min-w-0 pt-4">
        <span class="truncate font-semibold tracking-tight text-gray-900 dark:text-gray-100">
          <template v-if="nameRepeatsBrand">{{ modelName }}</template>
          <template v-else>{{ m.brandDisplayName }} <span class="font-normal text-gray-500 dark:text-gray-400">{{ modelName }}</span></template>
        </span>
      </span>

      <!-- Meta: class, cost (phone) and data basis. Own grid row so it spans the value column on the phone and wraps on the desktop. -->
      <span class="mr-meta pointer-events-none grid min-w-0 pt-0.5 text-[13px] leading-snug text-gray-500 dark:text-gray-400">
        <span class="truncate lg:hidden">{{ categoryLabel }} · <span data-testid="savings">{{ costVsCombustion }}</span></span>
        <span class="hidden truncate lg:block">{{ categoryLabel }}</span>
        <span class="truncate">
          {{ dataBasis }}<template v-if="item.singleDriver">&nbsp;</template><InformationCircleIcon v-if="item.singleDriver" class="pointer-events-auto inline h-4 w-4 align-text-bottom text-gray-400 dark:text-gray-500" role="img" :aria-label="t('models_ranking.row.single_driver')" :title="t('models_ranking.row.single_driver')" />
        </span>
      </span>

      <!-- Desktop: compare at the end of the row's second line. The reader's own figures live in the answer card and the open row. -->
      <span class="mr-hint pointer-events-none flex min-w-0 items-start gap-1.5 pb-4 pt-1.5 lg:pt-2">
        <!-- Compare is an action, not a figure: a labelled button at the end of the verdict line
             (desktop; the phone has it in the open details). It sits above the row toggle. -->
        <button
          type="button"
          class="pointer-events-auto relative ml-auto hidden h-8 flex-none items-center gap-1 self-start whitespace-nowrap rounded-lg px-2.5 text-[13px] font-semibold transition-colors disabled:cursor-not-allowed disabled:opacity-40 lg:inline-flex"
          :class="inCompare
            ? 'bg-green-600 text-white dark:bg-green-400 dark:text-gray-950'
            : 'text-gray-500 hover:bg-green-50 hover:text-green-700 dark:text-gray-400 dark:hover:bg-green-950/40 dark:hover:text-green-400'"
          :aria-pressed="inCompare"
          :aria-label="t(inCompare ? 'models_ranking.row.compare_remove' : 'models_ranking.row.compare_add', { name: m.modelDisplayName })"
          :title="!inCompare && !canAddCompare ? t('models_ranking.row.compare_full') : undefined"
          :disabled="!inCompare && !canAddCompare"
          @click.stop="emit('compare')"
        >
          <CheckIcon v-if="inCompare" class="h-3.5 w-3.5" stroke-width="2.5" aria-hidden="true" />
          <PlusIcon v-else class="h-3.5 w-3.5" stroke-width="2.5" aria-hidden="true" />
          {{ t(inCompare ? 'models_ranking.detail.compare_remove' : 'models_ranking.detail.compare_add') }}
        </button>
      </span>

      <span class="mr-val pointer-events-none grid pt-4 text-right lg:hidden">
        <b class="text-[20px] font-extrabold leading-tight tracking-tight tabular-nums" :class="mainValue.cls">{{ mainValue.value }}</b>
        <small class="text-[11px] text-gray-500 dark:text-gray-400">{{ mainValue.unit }}</small>
      </span>

      <ConsumptionBars
        class="mr-ld pointer-events-none pb-3.5 lg:pb-0 lg:pt-4"
        :axis="axis"
        :real="m.avgConsumptionKwhPer100km"
        :wltp="item.wltpKwhPer100km"
        :real-text="formatConsumption(m.avgConsumptionKwhPer100km, { showUnit: false })"
        :wltp-text="formatConsumption(item.wltpKwhPer100km, { showUnit: false })"
        :deviation-pct="item.wltpDeviationPct"
      />

      <!-- Desktop cost cell: electricity and the class's combustion car, both per 100 km -->
      <span class="mr-c3 pointer-events-none hidden content-center gap-0.5 self-stretch lg:pt-4 whitespace-nowrap text-right leading-tight lg:grid" data-testid="cost-cell">
        <span class="text-[17px] tabular-nums" :class="colClass('cost')">
          <span class="mr-1 text-[12px] font-normal text-gray-500 dark:text-gray-400">{{ t('models_ranking.row.electricity') }}</span>{{ costNumber }} <span class="text-[12px] font-normal text-gray-500 dark:text-gray-400">{{ unitSystem.currencySymbol }}</span>
        </span>
        <span class="text-[12.5px] tabular-nums text-gray-500 dark:text-gray-400">{{ combustionCaption }}</span>
        <!-- what the drivers of this model paid on average: the table itself prices every model alike -->
        <span v-if="driversPrice" class="text-[12.5px] tabular-nums text-gray-500 dark:text-gray-400">{{ driversPrice }}</span>
      </span>
      <!-- Desktop range cell (from 1280 px): the range, then the motorway ladder from a full battery,
           without a stop and with one and two fast-charge stops; "up to" when it refers to the largest battery only -->
      <span class="mr-c4 pointer-events-none hidden content-center gap-y-0.5 self-stretch xl:pt-4 whitespace-nowrap text-right leading-tight xl:grid" data-testid="range-cell">
        <span class="tabular-nums" :class="[colClass('range'), hasBatterySpan ? 'text-[14px]' : 'text-[17px]']">{{ rangeValue }} <span class="text-[12px] font-normal text-gray-500 dark:text-gray-400">{{ distanceUnitLabel() }}</span></span>
        <span v-for="step in rangeLadder" :key="step.label" class="text-[12.5px] tabular-nums text-gray-600 dark:text-gray-300"><span class="text-gray-500 dark:text-gray-400">{{ step.label }}</span> {{ step.value }}</span>
      </span>

    </div>

    <ModelRankingDetail
      v-if="expanded"
      :id="detailId"
      :item="item"
      :href="href"
      :price="price"
      :longest-trip-km="longestTripKm"
      :fuel="fuel"
      :locked="locked"
      @unlock="emit('unlock')"
      @assumptions="emit('assumptions')"
    />
  </li>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { CheckIcon, InformationCircleIcon, PlusIcon } from '@heroicons/vue/24/outline'
import ConsumptionBars from './ConsumptionBars.vue'
import ModelRankingDetail from './ModelRankingDetail.vue'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import { useRankedModelText } from '../../composables/useRankedModelText'
import { officialModelThumbUrl } from '../../config/modelImages'
import { convertCostPerDistance } from '../../utils/unitConversions'
import type { LadderAxis } from '../../utils/ladderScale'
import type { RankedModel, RankingSort } from '../../composables/useModelRanking'
import type { FuelKind, MainValue } from '../../utils/costMix'

const props = defineProps<{
  item: RankedModel
  axis: LadderAxis
  sort: RankingSort
  expanded: boolean
  inCompare: boolean
  canAddCompare: boolean
  /** Link to the model detail page */
  href: string
  /** Electricity price in EUR/kWh the costs are based on */
  price: number
  /** Big number under "Sparsamste": consumption or cost per 100 km */
  mainValue: MainValue
  /** Longest trip in km, named in the hint so the reader sees their own number */
  longestTripKm: number | null
  fuel: FuelKind
  /** Guest: the verdict dot and a figure-free label stay, the numbers wait behind the mail gate */
  locked?: boolean
}>()

const emit = defineEmits<{ toggle: []; compare: []; unlock: []; assumptions: [] }>()

const { t, te } = useI18n()
const { formatNumber, formatDecimal, formatConsumption, consumptionUnitLabel, distanceUnitLabel, formatCurrency, formatCostPerKwh, unitSystem } = useLocaleFormat()
const {
  m, fmtKm, fuelLabel, costLabel, combustionCostEur, typicalSpan, hasBatterySpan, rangeValue,
  driverCount, driversLabel, signedPct, displayWltpDeviation,
} = useRankedModelText(() => props.item, () => props.longestTripKm, () => props.fuel)

const detailId = computed(() => `mr-detail-${props.item.key.replace(/[^A-Za-z0-9_-]/g, '-')}`)
const imageUrl = computed(() => officialModelThumbUrl(m.value.model))
const modelName = computed(() => m.value.modelUrlSlug.replace(/_/g, ' '))
// "Renault 5", "Polestar 2": the model name already carries the brand, show it once
const nameRepeatsBrand = computed(() => modelName.value.toLowerCase().startsWith(m.value.brandDisplayName.toLowerCase()))
const categoryLabel = computed(() => {
  const key = `models_list.filters.categories.${m.value.category}`
  return te(key) ? t(key) : m.value.categoryDisplayName
})

// Data basis: "from 412 charging sessions by 9 drivers". Counts missing on an older backend → sessions only.
const dataBasis = computed(() => {
  const logs = t('models_ranking.row.logs_plural', { count: formatNumber(m.value.logCount) }, m.value.logCount)
  return driverCount.value != null
    ? t('models_ranking.row.data_basis', { logs, drivers: driversLabel.value })
    : t('models_ranking.row.logs', { count: formatNumber(m.value.logCount) })
})

const costNumber = computed(() => props.item.costPer100kmEur != null
  ? formatDecimal(convertCostPerDistance(props.item.costPer100kmEur, unitSystem.value), 2)
  : '–')

// Phone meta line: "3,22 €/100km (Benziner 15,05)"
const costVsCombustion = computed(() => combustionCostEur.value != null
  ? t('models_ranking.row.cost_vs_combustion', {
      cost: costLabel.value,
      fuel: fuelLabel.value,
      combustion: formatDecimal(convertCostPerDistance(combustionCostEur.value, unitSystem.value), 2),
    })
  : costLabel.value)
// Desktop cost cell: "Fahrer zahlen Ø 29,6 ct/kWh", the drivers' own average next to the list price
const driversPrice = computed(() => m.value.avgCostPerKwh != null
  ? t('models_ranking.row.drivers_price', { price: formatCostPerKwh(m.value.avgCostPerKwh) })
  : null)
// Desktop cost cell caption: "Benziner 14,62 €" (the litres per class sit in the detail and the sheet)
const combustionCaption = computed(() => combustionCostEur.value != null
  ? t('models_ranking.row.combustion_short', {
      fuel: fuelLabel.value,
      amount: formatCurrency(convertCostPerDistance(combustionCostEur.value, unitSystem.value)),
    })
  : '')

// Motorway ladder of the largest battery, from the server: from 100 % without a stop, then each stop
// 10 to 80 %, the same rule as the stops of the longest trip
const rangeLadder = computed(() => {
  const l = props.item.ladder
  if (!l) return []
  const upto = (km: number) => hasBatterySpan.value ? t('models_ranking.row.ladder_upto', { value: fmtKm(km) }) : fmtKm(km)
  return [
    { label: t('models_ranking.row.ladder_motorway'), value: upto(l.zeroStopKm) },
    ...[1, 2].map(n => ({ label: t('models_ranking.row.ladder_stops', { count: formatNumber(n) }, n), value: upto(n === 1 ? l.oneStopKm : l.twoStopsKm) })),
  ]
})
// Colour always in kWh space: above WLTP is worse (orange), below is better (green)
const deviationClass = computed(() => {
  const d = props.item.wltpDeviationPct
  if (d == null) return ''
  return d > 0 ? 'text-orange-700 dark:text-orange-400' : 'text-green-700 dark:text-green-400'
})

const mainValue = computed(() => {
  const na = { value: '–', unit: t('models_ranking.row.no_value'), cls: '' }
  switch (props.sort) {
    case 'range':
      if (typicalSpan.value) {
        return { value: fmtKm(typicalSpan.value.hi), unit: `${distanceUnitLabel()}, ${t('models_ranking.row.typical_range')}`, cls: '' }
      }
      return m.value.realRangeKm != null
        ? { value: fmtKm(m.value.realRangeKm), unit: `${distanceUnitLabel()} ${t('models_ranking.row.real_range')}`, cls: '' }
        : na
    case 'wltp':
      return displayWltpDeviation.value != null
        ? { value: signedPct(displayWltpDeviation.value), unit: t('models_ranking.row.to_wltp'), cls: deviationClass.value }
        : na
    case 'winter':
      return props.item.winterSurchargePct != null
        ? { value: signedPct(props.item.winterSurchargePct), unit: t('models_ranking.row.in_winter'), cls: 'text-orange-700 dark:text-orange-400' }
        : { ...na, unit: t('models_ranking.row.no_season') }
    case 'data':
      return { value: formatNumber(m.value.logCount), unit: t('models_list.card.charging_sessions'), cls: '' }
    default:
      if (props.mainValue === 'cost') {
        return props.item.costPer100kmEur != null
          ? { value: costNumber.value, unit: `${unitSystem.value.currencySymbol} ${t('models_ranking.row.per_100', { unit: distanceUnitLabel() })}`, cls: '' }
          : na
      }
      return { value: formatConsumption(m.value.avgConsumptionKwhPer100km, { showUnit: false }), unit: consumptionUnitLabel(), cls: '' }
  }
})

function colClass(column: RankingSort | 'cost' | null): string {
  const active = column === props.sort || (column === 'cost' && props.sort === 'efficient' && props.mainValue === 'cost')
  return active
    ? 'font-bold text-gray-900 dark:text-gray-100'
    : 'font-medium text-gray-700 dark:text-gray-300'
}

</script>

<style>
/* Shared row grid, also used by the axis ruler in PublicModelsListViewV2 (not scoped on purpose).
   Mobile: rank, thumb, name, value on top, the ladder below across the full width.
   Desktop: one line per model with ladder and value columns. */
.mr-grid {
  display: grid;
  grid-template-columns: 96px minmax(0, 1fr) auto;
  grid-template-areas: "th who val" "th meta meta" "th hint hint" ". ld ld";
  column-gap: 10px;
  row-gap: 4px;
  align-items: center;
}
.mr-grid.mr-ruler { grid-template-areas: ". ld ld"; row-gap: 0; }
.mr-th { grid-area: th; }
.mr-meta { grid-area: meta; }
.mr-who { grid-area: who; }
.mr-val { grid-area: val; }
.mr-hint { grid-area: hint; }
.mr-ld { grid-area: ld; }
.mr-c3 { grid-area: c3; }
.mr-c4 { grid-area: c4; }
/* Desktop: name block wide enough for the meta line, the hint runs across the whole row.
   The bar pair (manufacturer vs. drivers) carries the consumption, then the cost cell and
   from 1280 px the range cell; below that the board next to the filter column is too
   narrow for it. 626 px of content at 1024 px. */
@media (min-width: 1024px) {
  .mr-grid {
    grid-template-columns: 112px minmax(160px, 1fr) minmax(210px, 1fr) 120px;
    grid-template-areas: "th who ld c3" "th meta ld c3" "th hint hint hint";
    column-gap: 8px;
    row-gap: 2px;
    align-items: start;
  }
  .mr-grid.mr-ruler {
    grid-template-areas: ". who ld c3";
  }
}
@media (min-width: 1280px) {
  .mr-grid {
    grid-template-columns: 128px minmax(226px, 1fr) minmax(236px, 1fr) 124px 124px;
    column-gap: 10px;
    grid-template-areas: "th who ld c3 c4" "th meta ld c3 c4" "th hint hint hint hint";
  }
  .mr-grid.mr-ruler {
    grid-template-areas: ". who ld c3 c4";
  }
}
</style>
