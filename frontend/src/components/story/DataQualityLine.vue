<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { DayCoverage } from '../../composables/storyModel'
import type { DataQuality } from '../../composables/storyInsights'

/**
 * Die Qualitätszeile unter dem Hero: ein Feld je Tag des Monats (Daten grün, die genannte
 * Lücke amber, sonst grau) und ein Satz dazu. Eine Lücke nennt der Satz nur, wenn der Monat vorbei
 * ist oder sie 7 Tage überschreitet. Tage nach heute sind keine Lücke.
 */
const props = defineProps<{ days: DayCoverage[]; quality: DataQuality | null }>()
const { t } = useI18n()

const MIN_GAP_DAYS = 3
const LONG_GAP_DAYS = 7

const withData = computed(() => props.days.filter(d => d.hasData).length)
/** Im laufenden Monat nur die Tage bis heute. */
const elapsed = computed(() => props.days.filter(d => !d.future).length)
const monthOver = computed(() => props.days.length > 0 && !props.days.some(d => d.future))
const gap = computed(() => {
  const g = props.days.filter(d => d.inLongestGap && !d.future)
  if (g.length < MIN_GAP_DAYS) return null
  if (!monthOver.value && g.length <= LONG_GAP_DAYS) return null
  return { from: g[0].day, to: g[g.length - 1].day }
})
const measured = computed(() => props.quality ? Math.max(0, props.quality.total - props.quality.estimated) : 0)
const qualityText = computed(() => {
  const q = props.quality
  if (!q) return ''
  const parts = [t('story.quality_line', { measured: measured.value, total: q.total })]
  if (q.estimated > 0) parts.push(t('story.quality_estimated', { n: q.estimated }))
  return parts.join(', ')
})
const caption = computed(() => {
  const base = t('story.days_caption', { total: elapsed.value, withData: withData.value })
  return props.quality && props.quality.total > 0 ? `${base} ${qualityText.value}` : base
})
/** Amber nur für die Tage der Lücke, die der Satz nennt; andere leere Tage grau, Tage nach heute heller. */
const cellClass = (d: DayCoverage) => {
  if (d.hasData) return 'bg-green-700 dark:bg-green-400'
  if (d.future) return 'bg-gray-100 dark:bg-gray-800'
  if (gap.value && d.day >= gap.value.from && d.day <= gap.value.to) return 'bg-amber-400'
  return 'bg-gray-200 dark:bg-gray-700'
}
</script>

<template>
  <div v-if="days.length" class="grid gap-1.5" data-testid="story-quality">
    <div class="flex gap-[3px]" role="img" :aria-label="t('story.days_aria')">
      <i v-for="d in days" :key="d.day" data-role="day" class="h-2.5 flex-1 rounded-[2px]" :class="cellClass(d)" />
    </div>
    <p class="text-[12.5px] text-gray-500 tabular-nums dark:text-gray-400">{{ caption }}<template v-if="quality && quality.total > 0"><template v-if="quality.priceless > 0">, <span class="text-amber-700 dark:text-amber-400">{{ t('story.quality_priceless', { n: quality.priceless }) }}<template v-if="quality.pricelessOpen > 0">{{ ' ' }}<router-link to="/logs" class="font-semibold underline underline-offset-2">{{ t('story.quality_fix') }}</router-link></template></span></template>.</template><span v-if="gap" class="text-amber-700 dark:text-amber-400">{{ ' ' + t('story.days_gap', gap) }}</span></p>
  </div>
</template>
