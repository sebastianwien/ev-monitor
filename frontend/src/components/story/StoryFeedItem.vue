<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { LockClosedIcon } from '@heroicons/vue/24/outline'
import Spark from './Spark.vue'
import RankBadge from './RankBadge.vue'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import { CAT_LABEL } from '../../composables/useTickerItems'
import type { StoryFeedItem } from '../../composables/storyModel'
import type { WhatIfModel } from '../../composables/storyInsights'
import { useMomentView } from '../../composables/useMomentView'
import type { Moment } from '../../composables/storyMoments'
import { keepUnits } from '../../utils/modelStory'

/** Ein Satz aus Zahlen, der Fahrerfrage zugeordnet, mit Mini-Grafik. Gesperrt: verwischt plus CTA. */
const props = withDefaults(defineProps<{
  item: StoryFeedItem
  upsellTo: string
  /** Fuer den Was-waere-wenn-Satz: Modellnamen in der Reihenfolge der Liste. */
  whatIf?: WhatIfModel[]
  /** Fuer Moment-Eintraege: Ziel von "Preis nachtragen" und Name im AutoSync-Satz. */
  carId?: string | null
  carName?: string
}>(), { whatIf: () => [], carId: null, carName: '' })

const { t } = useI18n()
const { formatCurrency, formatConsumption, formatDecimal, formatDistance, convertConsumption, consumptionUnitLabel } = useLocaleFormat()

// Nur fuer id 'moment' gelesen; der computed wertet sonst nie aus.
const momentView = useMomentView({
  get moment() { return props.item.moment as Moment },
  get carId() { return props.carId },
  get carName() { return props.carName },
})
const momentLink = computed(() => props.item.id === 'moment' && momentView.value.action?.to ? momentView.value.action : null)

const sentence = computed(() => {
  const p = props.item.params
  switch (props.item.id) {
    case 'moment': {
      const v = momentView.value
      return keepUnits([`${v.eyebrow}${/[.?!]$/.test(v.eyebrow) ? '' : '.'}`, v.sentence, v.sub].filter(Boolean).join(' '))
    }
    case 'consumption_trend':
      // Das Delta in Anzeige-Einheiten bilden: mi/kWh ist nicht linear in kWh/100 km.
      return t(p.diff <= 0 ? 'story.feed_consumption_better' : 'story.feed_consumption_worse', {
        diff: `${formatDecimal(Math.abs(convertConsumption(p.current) - convertConsumption(p.previous)), 1)} ${consumptionUnitLabel()}`,
        current: formatConsumption(p.current, { showUnit: false }),
        previous: formatConsumption(p.previous),
      })
    case 'cost_trend':
      return t(p.diff <= 0 ? 'story.feed_cost_lower' : 'story.feed_cost_higher', { diff: formatCurrency(Math.abs(p.diff), { decimals: 0 }) })
    case 'distance_trend':
      return t(p.diff > 0 ? 'story.feed_distance_more' : 'story.feed_distance_less', {
        km: formatDistance(p.current, { round: true }), previous: formatDistance(p.previous, { round: true }),
      })
    case 'most_expensive':
      return t('story.feed_most_expensive', { eur: formatCurrency(p.eur), avg: formatCurrency(p.avgEur) })
    case 'home_savings': {
      const base = t('story.feed_home_savings', { eur: formatCurrency(p.eur, { decimals: 0 }) })
      return p.monthsRemaining > 0 ? `${base} ${t('story.feed_home_amortised_in', { months: p.monthsRemaining })}` : base
    }
    case 'peer_position':
      return t(p.diffCt <= 0 ? 'story.feed_peer_cheaper' : 'story.feed_peer_pricier', { ct: formatDecimal(Math.abs(p.diffCt), 1), peers: p.peers })
    case 'what_if_model':
      return p.modelIndex >= 0
        ? t('story.feed_what_if_cheaper', { model: props.whatIf[p.modelIndex]?.name ?? '', pct: formatDecimal(Math.abs(p.deltaPercent), 0), cars: p.cars })
        : t('story.feed_what_if_best', { cars: p.cars })
    case 'community_rank':
      return t('story.feed_rank', { rank: p.rank, category: t(`leaderboard.cat_${CAT_LABEL[props.item.label ?? ''] ?? 'kwh'}`) })
    case 'data_quality': {
      const parts = []
      if (p.estimated > 0) parts.push(t('story.feed_quality_estimated', { estimated: p.estimated, total: p.total }))
      if (p.priceless > 0) parts.push(t('story.feed_quality_priceless', { count: p.priceless }))
      return parts.join(' ')
    }
    case 'temperature_effect':
      return t('story.feed_temp', { pct: formatDecimal(Math.abs(p.deltaPercent), 0), cold: 5, mild: 15 })
    case 'temperature_locked':
      return t('story.feed_temp_locked')
    case 'phantom_locked':
      return t('story.feed_phantom_locked')
  }
})

const lockedCta = computed(() => props.item.id === 'temperature_locked' ? t('story.feed_temp_cta') : t('story.feed_phantom_cta'))

const spark = computed<{ values: number[]; variant: 'line' | 'bars' | 'range' } | null>(() => {
  const p = props.item.params
  switch (props.item.id) {
    case 'consumption_trend': return { values: [p.previous, p.current], variant: 'bars' }
    case 'cost_trend': return { values: [p.previous, p.current], variant: 'bars' }
    case 'distance_trend': return { values: [p.previous, p.current], variant: 'bars' }
    case 'most_expensive': return { values: [p.avgEur, p.eur], variant: 'bars' }
    case 'home_savings': return { values: [p.monthsRemaining > 0 ? p.monthsRemaining : 0, 12], variant: 'range' }
    case 'peer_position': return { values: [0, p.diffCt], variant: 'range' }
    case 'what_if_model': return { values: [p.mine, p.theirs], variant: 'bars' }
    case 'data_quality': return { values: [p.total - p.estimated, p.estimated], variant: 'bars' }
    case 'temperature_effect': return { values: [p.mild, p.cold], variant: 'bars' }
    case 'community_rank': return null
    case 'moment': return null
    default: return { values: [4, 3, 5, 2, 6, 3], variant: 'line' }
  }
})
</script>

<template>
  <div
    :data-testid="`story-feed-${item.id}`"
    class="story-feed-item relative grid grid-cols-[1fr_92px] gap-3 items-center py-4 border-b border-gray-200 dark:border-gray-700">
    <div :class="item.locked ? 'blur-[5px] select-none pointer-events-none' : ''">
      <p class="text-[15px] text-pretty text-gray-900 dark:text-gray-100">{{ sentence }}</p>
      <p class="text-xs text-gray-500 dark:text-gray-400 mt-1">{{ t(`story.feed_q${item.question}`) }}</p>
      <router-link v-if="momentLink" :to="momentLink.to!" class="mt-1 inline-flex min-h-[44px] items-center text-sm font-semibold text-gray-900 underline underline-offset-2 dark:text-gray-100">{{ momentLink.label }}</router-link>
    </div>
    <div class="w-[92px] flex justify-end" :class="item.locked ? 'blur-[5px]' : ''">
      <RankBadge v-if="item.id === 'community_rank'" :rank="item.params.rank" />
      <Spark v-else-if="spark" :values="spark.values" :variant="spark.variant" :tone="item.sentiment" />
    </div>
    <div v-if="item.locked" class="absolute inset-0 flex items-center justify-between gap-3 px-0.5">
      <span class="flex items-center gap-1.5 text-sm text-gray-800 dark:text-gray-200">
        <LockClosedIcon class="w-4 h-4 shrink-0" />{{ sentence }}
      </span>
      <router-link
        :to="upsellTo"
        class="shrink-0 rounded-full bg-story-accent dark:bg-story-accent-dark text-white dark:text-gray-900 text-sm font-semibold px-4 py-2.5 min-h-[44px] inline-flex items-center">
        {{ lockedCta }}
      </router-link>
    </div>
  </div>
</template>
