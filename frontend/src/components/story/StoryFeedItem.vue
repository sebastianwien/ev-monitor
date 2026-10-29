<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ChevronRightIcon } from '@heroicons/vue/24/outline'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import type { StoryFeedItem, TileId } from '../../composables/storyModel'
import { feedTarget } from '../../composables/storyFeedTarget'
import InsightBars, { type InsightBar } from './InsightBars.vue'

/**
 * Eine Insight-Zeile als Karte: Satz mit fetter Zahl, die Zahl rechts, darunter die Vergleichsbasis
 * und bei Prognose und Preisvergleich zwei Balken. Tippbar, wenn hinter dem Satz eine Kachel mit
 * Sheet steht. Schätzwerte ("ca.", ganze Euro) stehen getrennt von echten Ladekosten.
 */
const props = withDefaults(defineProps<{
  item: StoryFeedItem
  /** Fuer den Modellvergleich: Modell ohne Variante, z. B. "Model 3". */
  modelName?: string
  /** Name des Story-Monats fuer die Vergleichsbasis ("im Oktober") und den Balken. */
  monthName?: string
  /** Kacheln, die ein Sheet haben: nur dahin fuehrt ein Tipp auf die Zeile. */
  sheets?: TileId[]
}>(), { modelName: '', monthName: '', sheets: () => [] })
const emit = defineEmits<{ open: [id: TileId]; charge: [logId: string, section?: 'cost']; priceless: [] }>()

const { t, locale } = useI18n()
const { formatCurrency, formatDecimal, formatDistance } = useLocaleFormat()

// Die Zahl, um die es im Satz geht, steht fett: Marker um den Wert, danach in Stücke geteilt (kein v-html).
const B_OPEN = '\u0001'
const B_CLOSE = '\u0002'
const b = (v: string | number) => `${B_OPEN}${v}${B_CLOSE}`
const eur0 = (v: number) => formatCurrency(v, { decimals: 0 })
const monthName = (n: number) => new Date(2000, n - 1, 1).toLocaleDateString(locale.value, { month: 'long' })

const sentence = computed(() => {
  const p = props.item.params
  switch (props.item.id) {
    case 'autumn_forecast':
      return t('story.feed_autumn_title', { month: monthName(p.nextMonth), eur: b(eur0(Math.round(p.deltaEur))) })
    case 'peer_cost_eur':
      return t(p.diffEur < 0 ? 'story.feed_peer_eur_less' : 'story.feed_peer_eur_more', { eur: b(eur0(Math.round(Math.abs(p.diffEur)))), model: props.modelName })
    case 'home_savings':
      return t('story.feed_home_savings')
    case 'cost_trend':
      return t(p.diff <= 0 ? 'story.feed_cost_lower' : 'story.feed_cost_higher', { diff: b(formatCurrency(Math.abs(p.diff), { decimals: 0 })) })
    case 'distance_trend':
      return t(p.diff > 0 ? 'story.feed_distance_more' : 'story.feed_distance_less', {
        km: b(formatDistance(p.current, { round: true })), previous: formatDistance(p.previous, { round: true }),
      })
    case 'most_expensive':
      return t('story.feed_most_expensive', { eur: b(formatCurrency(p.eur)), avg: formatCurrency(p.avgEur) })
    case 'data_quality': {
      const parts = []
      if (p.estimated > 0) parts.push(t('story.feed_quality_estimated', { estimated: p.estimated, total: p.total }))
      if (p.priceless > 0) parts.push(t('story.feed_quality_priceless', { count: p.priceless }, p.priceless))
      return parts.join(' ')
    }
    case 'temperature_effect':
      return t('story.feed_temp', { pct: b(formatDecimal(Math.abs(p.deltaPercent), 0)), cold: 5, mild: 15 })
  }
})

/** Vergleichsbasis unter dem Satz; nur bei den Insight-Zeilen. */
const sub = computed<string>(() => {
  const p = props.item.params
  switch (props.item.id) {
    case 'autumn_forecast': {
      const weather = p.deltaC != null ? '_weather' : ''
      return t(`story.feed_autumn_sub${weather}${p.peerWinter ? '_peer' : ''}`, { model: props.modelName, deg: Math.abs(p.deltaC ?? 0) })
    }
    case 'peer_cost_eur': return t('story.feed_peer_eur_sub', { month: props.monthName })
    case 'home_savings': {
      const base = t('story.feed_home_savings_sub')
      return p.monthsRemaining > 0 ? `${base} ${t('story.feed_home_amortised_in', { months: p.monthsRemaining })}` : base
    }
    default: return ''
  }
})

/** Zwei Balken: der gemessene Wert hervorgehoben, Vergleich oder Schätzung grau mit "ca.". */
const bars = computed<InsightBar[] | null>(() => {
  const p = props.item.params
  switch (props.item.id) {
    case 'autumn_forecast':
      return [
        { label: props.monthName, value: p.currentEur, text: eur0(p.currentEur), accent: true },
        { label: monthName(p.nextMonth), value: p.nextEur, text: t('story.feed_approx', { value: eur0(Math.round(p.nextEur)) }) },
      ]
    case 'peer_cost_eur':
      return [
        { label: t('story.feed_bar_you'), value: p.mineEur, text: eur0(p.mineEur), accent: true },
        { label: t('story.feed_bar_others'), value: p.theirsEur, text: t('story.feed_approx', { value: eur0(Math.round(p.theirsEur)) }) },
      ]
    default: return null
  }
})

/** Der Satz in Stücken, fett markierte Werte als eigene Stücke. */
const parts = computed(() => (sentence.value ?? '').split(B_OPEN).flatMap((chunk, i) => {
  if (i === 0) return [{ text: chunk, bold: false }]
  const [bold, rest = ''] = chunk.split(B_CLOSE)
  return [{ text: bold, bold: true }, { text: rest, bold: false }]
}).filter(x => x.text))

/** Die eine Zahl rechts: der Wert, um den es im Satz geht. */
const number = computed<string>(() => {
  const p = props.item.params
  switch (props.item.id) {
    case 'autumn_forecast': return `+${eur0(Math.round(p.deltaEur))}`
    case 'peer_cost_eur': return `${p.diffEur < 0 ? '-' : '+'}${eur0(Math.round(Math.abs(p.diffEur)))}`
    case 'cost_trend': return formatCurrency(p.current, { decimals: 0 })
    case 'distance_trend': return formatDistance(p.current, { round: true })
    case 'most_expensive': return formatCurrency(p.eur)
    case 'home_savings': return formatCurrency(p.eur, { decimals: 0 })
    case 'data_quality': return p.priceless > 0 ? `${p.priceless}` : `${p.estimated}/${p.total}`
    case 'temperature_effect': return `+${formatDecimal(Math.abs(p.deltaPercent), 0)} %`
    default: return ''
  }
})
// Gruen fuer gute Nachrichten. Amber nur, wenn der Verbrauch schlechter wird; hoehere Kosten oder
// eine teure Ladung sind Information, kein Fehler. Nie Rot.
const AMBER_WHEN_WORSE = new Set<StoryFeedItem['id']>(['temperature_effect'])
const numberClass = computed(() => {
  if (props.item.sentiment === 'good') return 'text-green-700 dark:text-green-400'
  if ((props.item.sentiment === 'warn' || props.item.sentiment === 'bad') && AMBER_WHEN_WORSE.has(props.item.id)) return 'text-amber-700 dark:text-amber-400'
  return 'text-gray-900 dark:text-gray-100'
})

/** Ziel der Zeile: eine Ladung, die Liste ohne Preis oder das Sheet der Kachel dahinter */
const target = computed(() => feedTarget(props.item, props.sheets))
const tag = computed(() => target.value ? 'button' : 'div')
function go() {
  const to = target.value
  if (to?.kind === 'sheet') emit('open', to.id)
  else if (to?.kind === 'charge') emit('charge', to.logId, to.section)
  else if (to?.kind === 'priceless') emit('priceless')
}
</script>

<template>
  <component
    :is="tag"
    :type="target ? 'button' : undefined"
    :data-testid="`story-feed-${item.id}`"
    class="story-feed-item grid w-full gap-1.5 rounded-xl bg-white px-3.5 py-3 text-left edge-card dark:bg-gray-700/50"
    :class="target ? 'group transition-[transform,box-shadow,background-color] md:hover:bg-gray-50 active:translate-x-0.5 active:translate-y-0.5 active:shadow-none focus:outline-none focus-visible:ring-2 focus-visible:ring-story-accent md:dark:hover:bg-gray-700/70' : ''"
    @click="go">
    <span class="flex items-start justify-between gap-3">
      <span class="min-w-0 flex-1">
        <span class="block text-[15px] leading-snug text-pretty text-gray-900 dark:text-gray-100" :class="sub ? 'font-bold' : ''"><template v-for="(x, i) in parts" :key="i"><b v-if="x.bold" class="font-bold">{{ x.text }}</b><template v-else>{{ x.text }}</template></template></span>
        <span v-if="sub" class="mt-0.5 block text-[13px] leading-snug text-gray-600 dark:text-gray-300">{{ sub }}</span>
      </span>
      <span v-if="number" data-testid="story-feed-number" class="shrink-0 text-[17px] font-bold tabular-nums" :class="numberClass">{{ number }}</span>
      <span v-if="target" data-testid="story-feed-chevron" class="-ml-1 mt-0.5 shrink-0 text-gray-400 transition-transform group-hover:translate-x-0.5" aria-hidden="true"><ChevronRightIcon class="h-4 w-4" /></span>
    </span>
    <InsightBars v-if="bars" :bars="bars" class="mt-1" />
  </component>
</template>
