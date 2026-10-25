<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { DataQuality } from '../../composables/storyInsights'

/**
 * Duenne Qualitaetszeile unter der Tagesleiste: wie viele Ladungen gemessen, wie viele
 * geschaetzt, wie viele ohne Preis. Meta-Information, keine Kachel.
 */
const props = defineProps<{ quality: DataQuality }>()
const { t } = useI18n()

const measured = computed(() => Math.max(0, props.quality.total - props.quality.estimated))
const measuredPct = computed(() => props.quality.total > 0 ? (measured.value / props.quality.total) * 100 : 100)
const tone = computed(() => ({
  ok: 'text-gray-500 dark:text-gray-400',
  warn: 'text-amber-700 dark:text-amber-400',
  bad: 'text-rose-700 dark:text-rose-300',
}[props.quality.severity]))
</script>

<template>
  <div v-if="quality.total > 0" class="mt-2" data-testid="story-quality">
    <div class="h-1 rounded-full bg-amber-400/70 overflow-hidden">
      <div class="h-full bg-story-accent dark:bg-story-accent-dark rounded-full story-quality-fill" :style="{ width: `${measuredPct}%` }" />
    </div>
    <p class="mt-1 text-[11px] tabular-nums" :class="tone">
      {{ t('story.quality_line', { measured, total: quality.total }) }}<template v-if="quality.estimated > 0">, {{ t('story.quality_estimated', { n: quality.estimated }) }}</template><template v-if="quality.priceless > 0">, {{ t('story.quality_priceless', { n: quality.priceless }) }}
        <router-link v-if="quality.pricelessOpen > 0" to="/logs" class="underline underline-offset-2 font-semibold">{{ t('story.quality_fix') }}</router-link></template>
    </p>
  </div>
</template>

<style scoped>
.story-quality-fill { transition: width 600ms cubic-bezier(0.2, 0.7, 0.2, 1); }
</style>
