<template>
  <li class="border-b border-gray-200 dark:border-gray-800" :class="expanded ? 'bg-gray-50 dark:bg-gray-800/40' : ''">
    <div class="mr-row group relative px-4 py-3.5 transition-colors md:px-7" :class="expanded ? '' : 'hover:bg-gray-50 dark:hover:bg-gray-800/40'">
      <!-- Whole row toggles the details; content sits below it, only link, photo and compare are clickable on top. -->
      <button
        type="button"
        class="absolute inset-0 h-full w-full cursor-pointer focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-green-600"
        :aria-expanded="expanded"
        :aria-controls="detailId"
        @click="emit('toggle')"
      >
        <span class="sr-only">{{ t(expanded ? 'models_ranking.row.details_close' : 'models_ranking.row.details_open', { name: displayName }) }}</span>
      </button>

      <span class="mr-rank pointer-events-none text-[13px] font-semibold tabular-nums text-gray-500 dark:text-gray-400">{{ item.rank }}</span>

      <!-- Small photo; the magnifier opens it large with its credit. Without a photo: the brand's initials. -->
      <button
        v-if="thumbUrl"
        type="button"
        class="mr-thumb relative h-12 w-12 overflow-hidden rounded-[10px] bg-gray-200 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-green-600 dark:bg-gray-800"
        :class="inCompare ? 'ring-2 ring-green-600 dark:ring-green-400' : ''"
        :aria-label="t('models_ranking.row.zoom', { name: displayName })"
        data-testid="row-zoom"
        @click.stop="zoom"
      >
        <img :src="thumbUrl" alt="" loading="lazy" class="h-full w-full object-cover" />
        <span class="absolute bottom-0.5 right-0.5 grid h-5 w-5 place-items-center rounded-full bg-gray-900/75 text-white">
          <MagnifyingGlassPlusIcon class="h-3.5 w-3.5" stroke-width="2.25" aria-hidden="true" />
        </span>
      </button>
      <span
        v-else
        class="mr-thumb pointer-events-none grid h-12 w-12 place-items-center rounded-[10px] bg-gray-100 text-[13px] font-bold text-gray-600 dark:bg-gray-800 dark:text-gray-300"
        :class="inCompare ? 'ring-2 ring-green-600 dark:ring-green-400' : ''"
        aria-hidden="true"
        data-testid="row-initials"
      >{{ initials }}</span>

      <!-- Name as a link for search engines and the desktop; on the phone a tap opens the row -->
      <span class="mr-name pointer-events-none grid min-w-0 gap-0.5">
        <a :href="href" class="line-clamp-2 text-[16px] font-semibold leading-snug lg:truncate tracking-tight text-gray-900 underline-offset-2 hover:underline lg:pointer-events-auto lg:relative dark:text-gray-100"
           data-testid="row-name" @click.stop>{{ displayName }}</a>
        <span class="truncate text-[12.5px] leading-snug text-gray-500 dark:text-gray-400" data-testid="row-sub">
          {{ subLine }}<template v-if="item.singleDriver">&nbsp;</template><InformationCircleIcon v-if="item.singleDriver" class="pointer-events-auto inline h-4 w-4 align-text-bottom text-gray-400 dark:text-gray-500" role="img" :aria-label="t('models_ranking.row.single_driver')" :title="t('models_ranking.row.single_driver')" />
        </span>
      </span>

      <!-- Consumption: one bar on the scale all rows share, WLTP as a tick; the figures below it -->
      <span class="mr-cons pointer-events-none grid min-w-0 content-start gap-1.5" data-testid="consumption-cell">
        <span class="relative block h-2 rounded bg-gray-200 dark:bg-gray-700" role="img" :aria-label="barLabel">
          <span v-if="realWidth != null" class="absolute inset-y-0 left-0 rounded transition-[width] duration-500 ease-out motion-reduce:transition-none" :class="barTone" :style="{ width: `${realWidth}%` }"></span>
          <span v-if="wltpWidth != null" class="absolute -inset-y-1 w-0.5 rounded bg-gray-900 dark:bg-gray-100" :style="{ left: `calc(${wltpWidth}% - 1px)` }"></span>
        </span>
        <span class="text-[12.5px] leading-snug text-gray-500 dark:text-gray-400">
          {{ t('models_ranking.row.bar_drivers') }} <b class="text-[15px] tabular-nums" :class="colClass('consumption')">{{ realText }}</b>
          · {{ t('models_ranking.columns.wltp') }} <span class="tabular-nums">{{ wltpText }}</span> {{ consumptionUnitLabel() }}<template v-if="winterNote"> · <span class="text-orange-700 dark:text-orange-400">{{ winterNote }}</span></template>
        </span>
      </span>

      <!-- Cost per 100 km: electricity at the list price, the class's combustion car, what the drivers paid -->
      <span class="mr-cost pointer-events-none grid content-start gap-0.5 text-right leading-tight lg:text-left" data-testid="cost-cell">
        <span class="tabular-nums"><span class="text-[12.5px] text-gray-500 dark:text-gray-400">{{ t('models_ranking.row.electricity') }}</span> <b class="text-[16px]" :class="colClass('cost')">{{ electricityCost }}</b></span>
        <span v-if="combustionCaption" class="text-[12.5px] tabular-nums text-gray-500 dark:text-gray-400">{{ combustionCaption }}</span>
        <span v-if="driversPrice" class="text-[12.5px] tabular-nums text-gray-500 dark:text-gray-400">{{ driversPrice }}</span>
      </span>

      <!-- Range, then the motorway ladder of the largest battery (desktop) -->
      <span class="mr-range pointer-events-none grid content-start gap-0.5 text-right leading-tight lg:text-left" data-testid="range-cell">
        <b class="text-[16px] tabular-nums" :class="colClass('range')">{{ rangeValue }} <span class="text-[12.5px] font-normal text-gray-500 dark:text-gray-400">{{ distanceUnitLabel() }}</span></b>
        <span v-if="ladderLine" class="hidden text-[12.5px] leading-snug text-gray-500 lg:block dark:text-gray-400">{{ ladderLine }}</span>
      </span>

      <!-- Desktop: compare and the open state at the end; the phone has compare in the open row -->
      <span class="mr-act pointer-events-none hidden items-center gap-2 lg:flex">
        <button
          type="button"
          class="pointer-events-auto relative inline-flex h-9 items-center gap-1 whitespace-nowrap rounded-lg border px-2.5 text-[13px] font-semibold transition-colors disabled:cursor-not-allowed disabled:opacity-40"
          :class="inCompare
            ? 'border-green-600 bg-green-600 text-white dark:border-green-400 dark:bg-green-400 dark:text-gray-950'
            : 'border-gray-300 bg-white text-gray-800 hover:border-green-600 hover:text-green-700 dark:border-gray-700 dark:bg-gray-900 dark:text-gray-200'"
          :aria-pressed="inCompare"
          :aria-label="t(inCompare ? 'models_ranking.row.compare_remove' : 'models_ranking.row.compare_add', { name: displayName })"
          :title="!inCompare && !canAddCompare ? t('models_ranking.row.compare_full') : undefined"
          :disabled="!inCompare && !canAddCompare"
          data-testid="compare-row"
          @click.stop="emit('compare')"
        >
          <CheckIcon v-if="inCompare" class="h-3.5 w-3.5" stroke-width="2.5" aria-hidden="true" />
          <PlusIcon v-else class="h-3.5 w-3.5" stroke-width="2.5" aria-hidden="true" />
          {{ t(inCompare ? 'models_ranking.row.compare_short_remove' : 'models_ranking.row.compare_short_add') }}
        </button>
        <span class="grid h-9 w-9 place-items-center rounded-lg border border-gray-300 text-gray-600 dark:border-gray-700 dark:text-gray-300" aria-hidden="true">
          <ChevronDownIcon class="h-4 w-4 transition-transform" :class="expanded ? 'rotate-180' : ''" />
        </span>
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
      :in-compare="inCompare"
      :can-add-compare="canAddCompare"
      @unlock="emit('unlock')"
      @assumptions="emit('assumptions')"
      @compare="emit('compare')"
    />
  </li>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { CheckIcon, ChevronDownIcon, InformationCircleIcon, MagnifyingGlassPlusIcon, PlusIcon } from '@heroicons/vue/24/outline'
import ModelRankingDetail from './ModelRankingDetail.vue'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import { useRankedModelText } from '../../composables/useRankedModelText'
import { useLightbox } from '../../composables/useLightbox'
import { officialModelImageAttribution, officialModelImageUrl, officialModelThumbUrl } from '../../config/modelImages'
import { convertCostPerDistance } from '../../utils/unitConversions'
import { barLength, type LadderAxis } from '../../utils/ladderScale'
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
  /** "Sparsamste" ranks by consumption or by cost per 100 km; the column it acts on is bold */
  mainValue: MainValue
  /** Longest trip in km, for the open row's answers */
  longestTripKm: number | null
  fuel: FuelKind
  /** Guest: the open row's answers wait behind the gate in chapter 1 */
  locked?: boolean
}>()

const emit = defineEmits<{ toggle: []; compare: []; unlock: []; assumptions: [] }>()

const { t, te } = useI18n()
const { formatNumber, formatConsumption, consumptionUnitLabel, distanceUnitLabel, formatCurrency, formatCostPerKwh, unitSystem } = useLocaleFormat()
const { m, fmtKm, fuelLabel, combustionCostEur, rangeValue, driversLabel, signedPct } =
  useRankedModelText(() => props.item, () => props.longestTripKm, () => props.fuel)
const lightbox = useLightbox()

const detailId = computed(() => `mr-detail-${props.item.key.replace(/[^A-Za-z0-9_-]/g, '-')}`)
const modelName = computed(() => m.value.modelUrlSlug.replace(/_/g, ' '))
// "Renault 5", "Polestar 2": the model name already carries the brand, show it once
const displayName = computed(() => modelName.value.toLowerCase().startsWith(m.value.brandDisplayName.toLowerCase())
  ? modelName.value
  : `${m.value.brandDisplayName} ${modelName.value}`)
const initials = computed(() => m.value.brandDisplayName.slice(0, 2).toLocaleUpperCase())
const categoryLabel = computed(() => {
  const key = `models_list.filters.categories.${m.value.category}`
  return te(key) ? t(key) : m.value.categoryDisplayName
})
/** "Mittelklasse · 42 Fahrer · 6.627 Ladevorgänge"; drivers missing on an older backend */
const subLine = computed(() => [
  categoryLabel.value,
  driversLabel.value,
  t('models_ranking.row.logs_plural', { count: formatNumber(m.value.logCount) }, m.value.logCount),
].filter(Boolean).join(' · '))

// ── Photo ──────────────────────────────────────────────────────────────────
const thumbUrl = computed(() => officialModelThumbUrl(m.value.model))
function zoom() {
  const src = officialModelImageUrl(m.value.model)
  if (!src) return
  const credit = officialModelImageAttribution(m.value.model)
  lightbox.open([{
    src, alt: displayName.value,
    credit: credit ? { text: t('models_ranking.detail.photo_credit', { author: credit.author, license: credit.license }), href: credit.sourceUrl } : undefined,
  }])
}

// ── Consumption ────────────────────────────────────────────────────────────
const realWidth = computed(() => barLength(m.value.avgConsumptionKwhPer100km, props.axis))
const wltpWidth = computed(() => barLength(props.item.wltpKwhPer100km, props.axis))
const realText = computed(() => formatConsumption(m.value.avgConsumptionKwhPer100km, { showUnit: false }))
const wltpText = computed(() => formatConsumption(props.item.wltpKwhPer100km, { showUnit: false }))
// Colour in kWh space: above WLTP is worse (orange), below is better (green)
const barTone = computed(() => (props.item.wltpDeviationPct ?? 0) > 0 ? 'bg-orange-500 dark:bg-orange-400' : 'bg-green-600 dark:bg-green-500')
const barLabel = computed(() => `${t('models_ranking.row.bar_drivers')} ${realText.value}, ${t('models_ranking.columns.wltp')} ${wltpText.value} ${consumptionUnitLabel()}`)
/** Sorted by "Winterfest": the row names its winter surcharge */
const winterNote = computed(() => props.sort === 'winter' && props.item.winterSurchargePct != null
  ? `${signedPct(props.item.winterSurchargePct)} ${t('models_ranking.row.in_winter')}`
  : '')

// ── Cost ───────────────────────────────────────────────────────────────────
/** "5,93 €": per 100 km of the market's unit, the column head names the distance */
const electricityCost = computed(() => props.item.costPer100kmEur != null
  ? formatCurrency(convertCostPerDistance(props.item.costPer100kmEur, unitSystem.value))
  : '–')
/** "Benziner 14,62 €": the class's combustion car per 100 km */
const combustionCaption = computed(() => combustionCostEur.value != null
  ? t('models_ranking.row.combustion_short', {
      fuel: fuelLabel.value,
      amount: formatCurrency(convertCostPerDistance(combustionCostEur.value, unitSystem.value)),
    })
  : '')
/** "Fahrer zahlen Ø 29,6 ct/kWh": the drivers' own average next to the list price */
const driversPrice = computed(() => m.value.avgCostPerKwh != null
  ? t('models_ranking.row.drivers_price', { price: formatCostPerKwh(m.value.avgCostPerKwh) })
  : null)

// ── Range ──────────────────────────────────────────────────────────────────
/** Motorway from a full battery, largest battery: "Autobahn ohne Stopp bis 310, 1 Stopp bis 550" */
const ladderLine = computed(() => {
  const l = props.item.ladder
  return l ? t('models_ranking.row.ladder_line', { zero: fmtKm(l.zeroStopKm), one: fmtKm(l.oneStopKm) }) : ''
})

function colClass(column: 'consumption' | 'cost' | 'range'): string {
  const s = props.sort
  const active = column === 'range' ? s === 'range'
    : column === 'cost' ? s === 'efficient' && props.mainValue === 'cost'
      : (s === 'efficient' && props.mainValue !== 'cost') || s === 'wltp' || s === 'winter'
  return active ? 'font-extrabold text-gray-900 dark:text-gray-100' : 'font-semibold text-gray-800 dark:text-gray-200'
}
</script>

<style>
/* Shared row grid, also used by the column heads in PublicModelsListViewV2 (not scoped on purpose).
   Phone: rank, photo, name and range on top; the bar and the cost below.
   Desktop (lg): one line per model, compare and the open state at the end. */
.mr-row {
  display: grid;
  grid-template-columns: 16px 48px minmax(0, 1fr) auto;
  grid-template-areas: "rank thumb name range" "cons cons cons cost";
  column-gap: 10px;
  row-gap: 12px;
  align-items: center;
}
.mr-rank { grid-area: rank; }
.mr-thumb { grid-area: thumb; }
.mr-name { grid-area: name; }
.mr-cons { grid-area: cons; align-self: start; }
.mr-cost { grid-area: cost; align-self: start; }
.mr-range { grid-area: range; }
.mr-act { grid-area: act; }
@media (min-width: 1024px) {
  .mr-row {
    grid-template-columns: 20px 48px minmax(0, 1.25fr) minmax(0, 1.1fr) 172px minmax(0, 1fr) auto;
    grid-template-areas: "rank thumb name cons cost range act";
    column-gap: 16px;
    align-items: start;
  }
  .mr-rank, .mr-thumb, .mr-name, .mr-act { align-self: center; }
  .mr-range { align-self: start; }
}
</style>
