<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { LockClosedIcon, ChevronRightIcon } from '@heroicons/vue/24/outline'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import { CAT_LABEL } from '../../composables/useTickerItems'
import type { StoryFeedItem, TileId } from '../../composables/storyModel'
import type { WhatIfModel } from '../../composables/storyInsights'
import { useMomentView } from '../../composables/useMomentView'
import type { Moment } from '../../composables/storyMoments'
import { keepUnits } from '../../utils/modelStory'

/**
 * Eine Feed-Zeile: ein Satz links, eine Zahl rechts, keine Grafik, keine Metazeile. Tippbar,
 * wenn hinter dem Satz eine Kachel mit Sheet steht. Gesperrt: verwischt plus CTA.
 */
const props = withDefaults(defineProps<{
  item: StoryFeedItem
  upsellTo: string
  /** Fuer den Was-waere-wenn-Satz: Modellnamen in der Reihenfolge der Liste. */
  whatIf?: WhatIfModel[]
  /** Fuer Moment-Eintraege: Ziel von "Preis nachtragen" und Name im AutoSync-Satz. */
  carId?: string | null
  carName?: string
  /** Kacheln, die ein Sheet haben: nur dahin fuehrt ein Tipp auf die Zeile. */
  sheets?: TileId[]
}>(), { whatIf: () => [], carId: null, carName: '', sheets: () => [] })
const emit = defineEmits<{ open: [id: TileId] }>()

const { t } = useI18n()
const { formatCurrency, formatConsumption, formatDecimal, formatDistance, convertConsumption, consumptionUnitLabel } = useLocaleFormat()

// Nur fuer id 'moment' gelesen; der computed wertet sonst nie aus.
const momentView = useMomentView({
  get moment() { return props.item.moment as Moment },
  get carId() { return props.carId },
  get carName() { return props.carName },
})
const momentLink = computed(() => props.item.id === 'moment' && momentView.value.action?.to ? momentView.value.action : null)

// Die Zahl, um die es im Satz geht, steht fett: Marker um den Wert, danach in Stücke geteilt (kein v-html).
const B_OPEN = '\u0001'
const B_CLOSE = '\u0002'
const b = (v: string | number) => `${B_OPEN}${v}${B_CLOSE}`

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
        diff: b(`${formatDecimal(Math.abs(convertConsumption(p.current) - convertConsumption(p.previous)), 1)} ${consumptionUnitLabel()}`),
        current: formatConsumption(p.current, { showUnit: false }),
        previous: formatConsumption(p.previous),
      })
    case 'cost_trend':
      return t(p.diff <= 0 ? 'story.feed_cost_lower' : 'story.feed_cost_higher', { diff: b(formatCurrency(Math.abs(p.diff), { decimals: 0 })) })
    case 'distance_trend':
      return t(p.diff > 0 ? 'story.feed_distance_more' : 'story.feed_distance_less', {
        km: b(formatDistance(p.current, { round: true })), previous: formatDistance(p.previous, { round: true }),
      })
    case 'most_expensive':
      return t('story.feed_most_expensive', { eur: b(formatCurrency(p.eur)), avg: formatCurrency(p.avgEur) })
    case 'home_savings': {
      const base = t('story.feed_home_savings', { eur: b(formatCurrency(p.eur, { decimals: 0 })) })
      return p.monthsRemaining > 0 ? `${base} ${t('story.feed_home_amortised_in', { months: p.monthsRemaining })}` : base
    }
    case 'peer_position':
      return t(p.diffCt <= 0 ? 'story.feed_peer_cheaper' : 'story.feed_peer_pricier', { ct: b(formatDecimal(Math.abs(p.diffCt), 1)), peers: p.peers })
    case 'what_if_model':
      return p.modelIndex >= 0
        ? t('story.feed_what_if_cheaper', { model: props.whatIf[p.modelIndex]?.name ?? '', pct: b(formatDecimal(Math.abs(p.deltaPercent), 0)), cars: p.cars })
        : t('story.feed_what_if_best', { cars: p.cars })
    case 'community_rank':
      return t('story.feed_rank', { rank: b(p.rank), category: t(`leaderboard.cat_${CAT_LABEL[props.item.label ?? ''] ?? 'kwh'}`) })
    case 'data_quality': {
      const parts = []
      if (p.estimated > 0) parts.push(t('story.feed_quality_estimated', { estimated: p.estimated, total: p.total }))
      if (p.priceless > 0) parts.push(t('story.feed_quality_priceless', { count: p.priceless }))
      return parts.join(' ')
    }
    case 'temperature_effect':
      return t('story.feed_temp', { pct: b(formatDecimal(Math.abs(p.deltaPercent), 0)), cold: 5, mild: 15 })
    case 'drive_vs_charge':
      return t('story.feed_drive_vs_charge', { drive: b(formatConsumption(p.drive)), charged: b(formatConsumption(p.charged)) })
    case 'temperature_locked':
      return t('story.feed_temp_locked')
    case 'phantom_locked':
      return t('story.feed_phantom_locked')
  }
})

/** Der Satz in Stücken, fett markierte Werte als eigene Stücke. */
const parts = computed(() => (sentence.value ?? '').split(B_OPEN).flatMap((chunk, i) => {
  if (i === 0) return [{ text: chunk, bold: false }]
  const [bold, rest = ''] = chunk.split(B_CLOSE)
  return [{ text: bold, bold: true }, { text: rest, bold: false }]
}).filter(x => x.text))
const plain = computed(() => parts.value.map(x => x.text).join(''))

/** Die eine Zahl rechts: der Wert, um den es im Satz geht. */
const number = computed<string>(() => {
  const p = props.item.params
  switch (props.item.id) {
    case 'consumption_trend': return formatConsumption(p.current, { showUnit: false })
    case 'cost_trend': return formatCurrency(p.current, { decimals: 0 })
    case 'distance_trend': return formatDistance(p.current, { round: true })
    case 'most_expensive': return formatCurrency(p.eur)
    case 'home_savings': return formatCurrency(p.eur, { decimals: 0 })
    case 'peer_position': return `${p.diffCt <= 0 ? '-' : '+'}${formatDecimal(Math.abs(p.diffCt), 1)} ct`
    case 'what_if_model': return p.modelIndex >= 0 ? `-${formatDecimal(Math.abs(p.deltaPercent), 0)} %` : ''
    case 'community_rank': return `#${p.rank}`
    case 'data_quality': return p.priceless > 0 ? `${p.priceless}` : `${p.estimated}/${p.total}`
    case 'temperature_effect': return `+${formatDecimal(Math.abs(p.deltaPercent), 0)} %`
    case 'drive_vs_charge': return formatConsumption(p.drive, { showUnit: false })
    default: return ''
  }
})
// Gruen fuer gute Nachrichten. Amber nur, wenn der Verbrauch schlechter wird; hoehere Kosten oder
// eine teure Ladung sind Information, kein Fehler. Nie Rot.
const AMBER_WHEN_WORSE = new Set<StoryFeedItem['id']>(['consumption_trend', 'temperature_effect'])
const numberClass = computed(() => {
  if (props.item.sentiment === 'good') return 'text-green-700 dark:text-green-400'
  if ((props.item.sentiment === 'warn' || props.item.sentiment === 'bad') && AMBER_WHEN_WORSE.has(props.item.id)) return 'text-amber-700 dark:text-amber-400'
  return 'text-gray-900 dark:text-gray-100'
})

const SHEET_OF: Partial<Record<StoryFeedItem['id'], TileId>> = {
  consumption_trend: 'wltp', temperature_effect: 'wltp',
  cost_trend: 'cost', most_expensive: 'cost', home_savings: 'cost', data_quality: 'cost', distance_trend: 'cost',
  peer_position: 'peer', what_if_model: 'peer', drive_vs_charge: 'trips',
}
const sheet = computed<TileId | null>(() => {
  const id = SHEET_OF[props.item.id]
  return id && props.sheets.includes(id) && !props.item.locked ? id : null
})

/** Wohin die Zeile führt: Sheet einer Kachel, eine Seite oder nirgends. Nur dann Pfeil und Knopf. */
const LINK_OF: Partial<Record<StoryFeedItem['id'], string>> = { community_rank: '/leaderboard' }
const link = computed(() => props.item.locked ? null : LINK_OF[props.item.id] ?? null)
const tag = computed(() => sheet.value ? 'button' : link.value ? 'router-link' : 'div')
const interactive = computed(() => !!sheet.value || !!link.value)

const lockedCta = computed(() => props.item.id === 'temperature_locked' ? t('story.feed_temp_cta') : t('story.feed_phantom_cta'))
</script>

<template>
  <component
    :is="tag"
    :type="sheet ? 'button' : undefined"
    :to="link ?? undefined"
    :data-testid="`story-feed-${item.id}`"
    class="story-feed-item relative flex min-h-14 w-full items-center justify-between gap-3 border-b border-gray-200 py-2.5 text-left last:border-0 dark:border-gray-800"
    :class="interactive ? 'group -mx-2 w-[calc(100%+1rem)] rounded-lg px-2 transition-colors hover:bg-gray-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-story-accent dark:hover:bg-gray-800/60' : ''"
    @click="sheet && emit('open', sheet)">
    <span class="min-w-0 flex-1" :class="item.locked ? 'blur-[5px] select-none pointer-events-none' : ''">
      <span class="block text-[15px] leading-snug text-pretty text-gray-900 dark:text-gray-100"><template v-for="(x, i) in parts" :key="i"><b v-if="x.bold" class="font-bold">{{ x.text }}</b><template v-else>{{ x.text }}</template></template></span>
      <router-link v-if="momentLink" :to="momentLink.to!" class="mt-1 inline-flex min-h-[44px] items-center text-sm font-semibold text-gray-900 underline underline-offset-2 dark:text-gray-100">{{ momentLink.label }}</router-link>
    </span>
    <span v-if="number && !item.locked" data-testid="story-feed-number" class="shrink-0 text-[15px] font-bold tabular-nums" :class="numberClass">{{ number }}</span>
    <span v-if="interactive" data-testid="story-feed-chevron" class="-ml-1 shrink-0 text-gray-400 transition-transform group-hover:translate-x-0.5" aria-hidden="true"><ChevronRightIcon class="h-4 w-4" /></span>
    <span v-if="item.locked" class="absolute inset-0 flex items-center justify-between gap-3 px-0.5">
      <span class="flex items-center gap-1.5 text-sm text-gray-800 dark:text-gray-200">
        <LockClosedIcon class="h-4 w-4 shrink-0" />{{ plain }}
      </span>
      <router-link
        :to="upsellTo"
        class="inline-flex min-h-[44px] shrink-0 items-center rounded-full bg-story-accent px-4 py-2.5 text-sm font-semibold text-white dark:bg-story-accent-dark dark:text-gray-900">
        {{ lockedCta }}
      </router-link>
    </span>
  </component>
</template>
